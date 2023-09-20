package sim

import cats.Show
import sdk.DagCausalQueue
import sdk.node.{Processor, Proposer}
import weaver.WeaverState
import cats.syntax.all.*

/// Snapshot of the simulation state.
object NetworkSnapshot {
  final case class NodeSnapshot[M, S, T](
    // id of the node
    id: Int,
    // transactions finalized per second at the moment of snapshot creation
    tps: Int,
    // states
    weaver: WeaverState[M, S, T],
    proposer: Proposer.ST,
    processor: Processor.ST[M],
    buffer: DagCausalQueue[M],
  )

  implicit def showNodeSnapshot[M, S, T]: Show[NodeSnapshot[M, S, T]] = new Show[NodeSnapshot[M, S, T]] {
    override def show(x: NodeSnapshot[M, S, T]): String = {
      import x.*
      val processorData = s"${processor.processingSet.size} / " +
        s"${processor.waitingList.size}(${processor.concurrency})"

      f"$tps%5s ${weaver.lazo.dagData.size}%10s " +
        f"${proposer.status}%16s " +
        f"$processorData%20s " +
        f"${buffer.dequeue._2.size}%12s"
    }
  }

  implicit def showNetworkSnapshot[M, S, T]: Show[List[NodeSnapshot[M, S, T]]] = new Show[List[NodeSnapshot[M, S, T]]] {
    override def show(x: List[NodeSnapshot[M, S, T]]): String =
      s"""  BPS | Consensus size | Proposer status | Processor size | Buffer size
         |${x.sortBy(_.id).map(_.show).mkString("\n")}
         |""".stripMargin
  }

}
