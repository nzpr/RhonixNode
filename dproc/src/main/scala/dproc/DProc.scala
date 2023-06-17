package dproc

import cats.data.EitherT
import cats.effect.kernel.{Async, Outcome}
import cats.effect.std.Queue
import cats.effect.{Ref, Sync}
import cats.syntax.all.*
import dproc.data.Block
import fs2.Stream
import sdk.DagCausalQueue
import weaver.*
import weaver.Weaver.ExeEngine
import weaver.data.*

/**
  * Process of an distributed computer.
  *
  * @param stateRef Ref holding the process state
  * @param gcStream stream of message that are garbage collected
  * @param finStream stream of finalized transactions
  * @param acceptMsg callback to make the process accept the block
  * @tparam F effect type
  */
final case class DProc[F[_], M, S, T](
  dProcStream: Stream[F, Unit],                // main stream that launches the process
  // state
  stateRef: Ref[F, Weaver[M, S, T]],           // state of the process - all data supporting the protocol
  ppStateRef: Option[Ref[F, sdk.Proposer]],    // state of the proposer
  pcStateRef: Ref[F, sdk.Processor[M]],        // state of the processor
  // outputs
  output: Stream[F, Block.WithId[M, S, T]],    // stream of output messages
  gcStream: Stream[F, Set[M]],                 // stream of messages garbage collected
  finStream: Stream[F, ConflictResolution[T]], // stream of finalized transactions
  // inputs
  acceptMsg: Block.WithId[M, S, T] => F[Unit], // callback to make process accept received block
  acceptTx: T => F[Unit],                      // callback to make the process accept transaction
)

object DProc {
  final private case class StateWithTxs[M, S, T](state: Weaver[M, S, T], txs: Iterable[T])

  /** Attempt to propose given proposer state. */
  def attemptPropose[F[_]: Sync, P](stRef: Ref[F, sdk.Proposer], propose: => F[P]): F[Either[Unit, P]] =
    Sync[F].bracketCase(stRef.modify(_.start)) { go =>
      go.guard[Option].toRight(()).traverse(_ => propose)
    } {
      // if started and completed - notify the state
      case true -> Outcome.Succeeded(_) => stRef.update(_.created)
      // if started but canceled - return to initial idle state
      case true -> Outcome.Canceled()   => stRef.update(_.created.done)
      // if erred - return to initial idle state, rise error
      case (_, Outcome.Errored(e))      => stRef.update(_.created.done) >> e.raiseError[F, Unit]
      // if not started - do nothing
      case false -> _                   => Sync[F].unit
    }

  /** Process given processor state. */
  def attemptProcess[F[_]: Sync, M, R](stRef: Ref[F, sdk.Processor[M]], m: M, process: => F[R]): F[Either[Unit, R]] =
    Sync[F].bracket(stRef.modify(_.start(m))) { started =>
      // suppress if already started, process otherwise
      if (started) process.map(_.asRight[Unit]) else ().asLeft[R].pure[F]
    }(_ => stRef.update(_.done(m)))

  def apply[F[_]: Async, M, S, T: Ordering](
    idOpt: Option[S],
    initPool: List[T],
    initState: Weaver[M, S, T],
    exeEngine: ExeEngine[F, M, S, T],
    idGen: Block[M, S, T] => F[M],
    // DProc assumes that blocks are received in full and stored. Retrieval is out of scope
    getBlock: M => F[Block.WithId[M, S, T]],
    // max number of blocks processed concurrently
    maxConcurrent: Int,
  ): F[DProc[F, M, S, T]] =
    for {
      // channel for incoming user signed transactions
      tQ             <- Queue.unbounded[F, T]
      // channel for outbound messages
      oQ             <- Queue.unbounded[F, Block.WithId[M, S, T]]
      // channel for garbage collect
      gcQ            <- Queue.unbounded[F, Set[M]]
      // channel for finalization results
      fQ             <- Queue.unbounded[F, ConflictResolution[T]]
      // processing queue
      pQ             <- Queue.unbounded[F, Block.WithId[M, S, T]]
      // states
      stRef          <- Ref[F].of(initState)
      plRef          <- Ref[F].of(initPool)
      proposerRef    <- Ref[F].of(sdk.Proposer.default)
      processorStRef <- Ref[F].of(sdk.Processor.default[M])
      bufferStRef    <- Ref[F].of(DagCausalQueue.default[M])
      weaverStRef    <- Ref[F].of(initState)
    } yield {

      /** Get the latest state to propose on top of. */
      def getProposeBase: F[StateWithTxs[M, S, T]] = (weaverStRef.get, plRef.get).mapN(StateWithTxs[M, S, T])

      /** Send block to processing queue. */
      def sendToProcessing(ids: Set[M]): F[Unit] = ids.toList.traverse_(getBlock(_) >>= pQ.offer)

      /** Add message to the buffer and send dependency free to processing. */
      def bufferAddWithSend(m: M, d: Set[M]): F[Unit] = bufferStRef
        .modify(_.enqueue(m, d).dequeue) // enqueue and get dependency free messages
        .flatMap(sendToProcessing)

      /** Notify buffer about completion and send dependency free to processing.  */
      def bufferCompleteWithSend(m: M): F[Unit] = bufferStRef
        .modify { s =>
          val (newS, success) = s.satisfy(m) // satisfy and get dependency free messages
          if (success) s.dequeue else newS -> Set.empty[M]
        }
        .flatMap(sendToProcessing)

      /** Create block. */
      def createBlock(s: StateWithTxs[M, S, T], sender: S): F[Block.WithId[M, S, T]] =
        for {
          // create message
          m <- MessageLogic.createMessage[F, M, S, T](s.txs.toList, sender, s.state, exeEngine)
          // assign ID (hashing / signing done here)
          b <- idGen(m).map(id => Block.WithId(id, m))
        } yield b

      /** Create block on the latest state. */
      def createBlockOnLatest(sender: S): F[Block.WithId[M, S, T]] = getProposeBase.flatMap(createBlock(_, sender))

      /** Propose pipe given sender. */
      def proposePipeBySender(sender: S): Stream[F, Unit] => Stream[F, Unit] = {
        val proposeF = attemptPropose(proposerRef, createBlockOnLatest(sender))
        // it is enough to have 2 permits here since attemptPropose returns immediately if another
        // propose is in progress. So one permit for proposing and one for pulling attempts
        (_: Stream[F, Unit]).parEvalMap(2)(_ => proposeF).evalMap(_.traverse_(pQ.offer))
      }

      /** Processing of a block might lead to new garbage and new finality. */
      final case class ProcessOutcome(garbage: Set[M], finalityOpt: Option[ConflictResolution[T]])

      /** Process block. */
      def processF(b: Block.WithId[M, S, T], w: Weaver[M, S, T]): F[ProcessOutcome] = for {
        // replay
        br    <- MessageLogic.replay(b, w, exeEngine)
        // add to the state TODO add to persistent store
        r     <- stRef.modify(_.add(b.id, br.lazoME, br.meldMOpt, br.gardMOpt, br.offenceOpt))
        // now buffer can be notified, since the block is in the state
        _     <- bufferCompleteWithSend(b.id)
        (g, _) = r
      } yield ProcessOutcome(g, b.m.finalized)

      /** Process block if state allows. */
      def processIfPossibleF(b: Block.WithId[M, S, T]): F[Either[Unit, ProcessOutcome]] = for {
        // read latest Weaver state
        w <- weaverStRef.get
        // check whether it is suitable to process the block
        ok = Weaver.shouldAdd(w.lazo, b.id, b.m.minGenJs ++ b.m.offences, b.m.sender)
        r <- Either.cond(ok, (), ()).traverse(_ => processF(b, w))
      } yield r

      /** Postprocessing after block is in the state and will not be lost by reboot. */
      def postProcessF(processOutcome: ProcessOutcome): F[Unit] = for {
        // report garbage
        _ <- gcQ.offer(processOutcome.garbage)
        // report new finality if detected
        _ <- processOutcome.finalityOpt.traverse(fQ.offer)
      } yield ()

      // main stream of the process
      val stream = {
        val bufferOutStream = Stream.fromQueueUnterminated(pQ)
        val txsStream       = Stream.fromQueueUnterminated(tQ)

        // pull blocks from buffer and process, buffer ensures that all blocks pulled can be processed concurrently
        val processBlocks = bufferOutStream.parEvalMap(maxConcurrent) { b =>
          attemptProcess(processorStRef, b.id, processIfPossibleF(b).flatMap(_.traverse(postProcessF)))
        }
        // pull transactions and add to the pool
        val pullTxs       = txsStream.evalTap(t => plRef.update(t +: _))

        // Attempt to propose after each transaction received or block added
        val proposePipe = idOpt.map(proposePipeBySender).getOrElse(identity[Stream[F, Unit]])
        processBlocks.merge(pullTxs).void.through(proposePipe)
      }

      new DProc[F, M, S, T](
        stream,
        stRef,
        idOpt.map(_ => proposerRef),
        processorStRef,
        Stream.fromQueueUnterminated(oQ),
        Stream.fromQueueUnterminated(gcQ),
        Stream.fromQueueUnterminated(fQ),
        (b: Block.WithId[M, S, T]) => bufferAddWithSend(b.id, b.m.minGenJs ++ b.m.offences),
        tQ.offer,
      )
    }
}
