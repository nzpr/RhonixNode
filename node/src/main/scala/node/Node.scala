package node

import cats.effect.Ref
import cats.effect.kernel.{Async, Sync}
import cats.syntax.all.*
import dproc.DProc
import dproc.DProc.ExeEngine
import dproc.data.Block
import sdk.DagCausalQueue
import sdk.merging.Relation
import sdk.node.{Processor, Proposer}
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
)

object Node {

  /** Make instance of a process - peer or the network.
   * Init with last finalized state (lfs as the simplest). */
  def apply[F[_]: Async, M, S, T: Ordering](
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
    ) => F[((Array[Byte], Seq[T]), (Array[Byte], Seq[T]), Seq[T])],
    saveBlock: Block.WithId[M, S, T] => F[Unit],
    readBlock: M => F[Block[M, S, T]],
  ): F[Node[F, M, S, T]] =
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
                      txs: List[T],
                    ): F[((Array[Byte], Seq[T]), (Array[Byte], Seq[T]), Seq[T])] =
                      computePreStateWithEffects(base, fringe, toFinalize, toMerge, txs.toSet)

                    // data read from the final state associated with the final fringe
                    def consensusData(fringe: Set[M]): F[FinalData[S]] = lfs.lazo.trustAssumption.pure[F] // TODO
                  }

      dproc <- DProc.apply[F, M, S, T](
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

    } yield new Node(
      weaverStRef,
      processorStRef,
      proposerStRef,
      bufferStRef,
      dproc,
    )
}
