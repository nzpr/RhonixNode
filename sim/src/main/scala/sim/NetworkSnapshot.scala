package sim

import cats.{Applicative, Show}
import sdk.DagCausalQueue
import sdk.node.{Processor, Proposer}
import weaver.WeaverState
import cats.syntax.all.*
import sdk.diag.Metrics
import sdk.diag.Metrics.Field
import sdk.history.ByteArray32
import sdk.syntax.all.sdkSyntaxByteArray

/// Snapshot of the simulation state.
object NetworkSnapshot {
  final case class NodeSnapshot[M, S, T](
    // id of the node
    id: S,
    // transactions finalized per second at the moment of snapshot creation
    tps: Float,
    // blocks finalized per second at the moment of snapshot creation
    bps: Float,
    // states
    weaver: WeaverState[M, S, T],
    proposer: Proposer.ST,
    processor: Processor.ST[M],
    buffer: DagCausalQueue[M],
    lfsHash: ByteArray32,
  )

  def reportSnapshot[F[_]: Metrics: Applicative, M, S, T](s: NodeSnapshot[M, S, T]): F[Unit] =
    Metrics[F].measurement(
      "nodeSnapshot",
      List(
        Field("tps", s.tps.toDouble.asInstanceOf[AnyRef]),
        Field("bps", s.bps.toDouble.asInstanceOf[AnyRef]),
        Field("consensusSize", s.weaver.lazo.dagData.size.asInstanceOf[AnyRef]),
        Field("processorSize", s.processor.processingSet.size.asInstanceOf[AnyRef]),
        Field("latestMessages", s.weaver.lazo.latestMessages.mkString(" | ")),
        Field(
          "blockHeight",
          s.weaver.lazo.latestMessages.map(s.weaver.lazo.dagData(_).seqNum).maxOption.getOrElse(0).toString,
        ),
      ),
    )

  implicit def showNodeSnapshot[M, S, T]: Show[NodeSnapshot[M, S, T]] = new Show[NodeSnapshot[M, S, T]] {
    override def show(x: NodeSnapshot[M, S, T]): String = {
      import x.*
      val processorData = s"${processor.processingSet.size} / " +
        s"${processor.waitingList.size}(${processor.concurrency})"

      f"$tps%5s $bps%5s ${weaver.lazo.dagData.size}%10s " +
        f"${proposer.status}%16s " +
        f"$processorData%20s " +
        f"${lfsHash.bytes.toHex}%74s"
    }
  }

  implicit def showNetworkSnapshot[M, S: Ordering, T]: Show[List[NodeSnapshot[M, S, T]]] =
    new Show[List[NodeSnapshot[M, S, T]]] {
      override def show(x: List[NodeSnapshot[M, S, T]]): String =
        s"""  TPS | BPS | Consensus size | Proposer status | Processor size | LFS hash
           |${x.sortBy(_.id).map(_.show).mkString("\n")}
           |""".stripMargin
    }

}
