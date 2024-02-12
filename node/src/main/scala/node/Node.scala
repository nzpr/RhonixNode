package node

import cats.effect.{Async, Ref}
import cats.syntax.all.*
import dproc.DProc
import dproc.DProc.ExeEngine
import dproc.data.Block
import node.comm.*
import node.comm.CommProtocol.CommMessage
import sdk.DagCausalQueue
import sdk.comm.Comm
import sdk.crypto.ECDSA
import sdk.diag.Metrics
import sdk.merging.Relation
import sdk.node.{Processor, Proposer}
import sdk.primitive.ByteArray
import secp256k1.Secp256k1
import slick.SlickDb
import weaver.WeaverState
import weaver.data.FinalData

final case class Node[F[_], M, S, T](
  // state
  weaverStRef: Ref[F, WeaverState[M, S, T]],
  procStRef: Ref[F, Processor.ST[M]],
  propStRef: Ref[F, Proposer.ST],
  bufferStRef: Ref[F, DagCausalQueue[M]],
  // inputs and outputs
  dProc: DProc[F, M, T],
  comm: Comm[F, M, CommMessage],
  output: fs2.Stream[F, M],
)

object Node {
  val SupportedECDSA: Map[String, ECDSA] = Map("secp256k1" -> Secp256k1.apply)

  /** Make instance of a process - peer or the network.
   * Init with last finalized state (lfs as the simplest). */
  def apply[F[_]: Async: Metrics, M, S, T: Ordering](
    id: S,
    lfs: WeaverState[M, S, T],
    hash: Block[M, S, T] => F[M],
    loadTx: F[Set[T]],
    computePreStateWithEffects: (
      Set[M],
      Set[M],
      Set[T],
      Set[T],
      Set[T],
    ) => F[((Array[Byte], Seq[T]), (Array[Byte], Seq[T]))],
    saveBlock: Block.WithId[M, S, T] => F[Unit],
    readBlock: M => F[Block[M, S, T]],
    gRpcPort: Int,
    commCfg: comm.Config,
  )(implicit db: SlickDb, msgEncoder: M => ByteArray): F[Node[F, M, S, T]] =
    for {
      weaverStRef    <- Ref.of(lfs)                       // weaver
      proposerStRef  <- Ref.of(Proposer.default)          // proposer
      processorStRef <- Ref.of(Processor.default[M]())    // processor
      bufferStRef    <- Ref.of(DagCausalQueue.default[M]) // buffer

      exeEngine = new ExeEngine[F, M, S, T] {
                    def execute(
                      base: Set[M],
                      fringe: Set[M],
                      toFinalize: Set[T],
                      toMerge: Set[T],
                      txs: Set[T],
                    ): F[((Array[Byte], Seq[T]), (Array[Byte], Seq[T]))] =
                      computePreStateWithEffects(base, fringe, toFinalize, toMerge, txs)

                    // data read from the final state associated with the final fringe
                    def consensusData(fringe: Set[M]): F[FinalData[S]] = lfs.lazo.trustAssumption.pure[F] // TODO
                  }

      dproc     <- DProc.apply[F, M, S, T](
                     weaverStRef,
                     proposerStRef,
                     processorStRef,
                     bufferStRef,
                     loadTx,
                     id.some,
                     exeEngine,
                     Relation.notRelated[F, T],
                     hash,
                     saveBlock,
                     readBlock,
                   )
      peerTable <- PeerTable(commCfg)
      comm      <- {
        implicit val logger: Logger = Logger.empty
        CommImpl[F, M](gRpcPort, peerTable)
      }
      output     = dproc.output.evalMap(msg => comm.broadcast(msg).as(msg))
    } yield new Node(
      weaverStRef,
      processorStRef,
      proposerStRef,
      bufferStRef,
      dproc,
      comm,
      output,
    )
}
