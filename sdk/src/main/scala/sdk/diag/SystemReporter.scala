package sdk.diag

import cats.effect.kernel.{Async, Sync}
import cats.syntax.all.*
import com.sun.management.OperatingSystemMXBean
import fs2.Stream
import sdk.diag.Metrics.Field

import java.lang.management.ManagementFactory
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object SystemReporter {
  final case class SystemData(cpuUsage: Double, heapUsage: Double, nonHeapUsage: Double)

  private def heapMB: Double = {
    val memoryMXBean    = ManagementFactory.getMemoryMXBean
    val heapMemoryUsage = memoryMXBean.getHeapMemoryUsage
    heapMemoryUsage.getUsed / (1024.0 * 1024.0) // Return heap memory used in MB as a double
  }

  private def nonHeapMB: Double = {
    val memoryMXBean       = ManagementFactory.getMemoryMXBean
    val nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage
    nonHeapMemoryUsage.getUsed / (1024.0 * 1024.0) // Return non-heap memory used in MB as a double
  }

  private def cpu: Double = {
    val osBean = ManagementFactory.getOperatingSystemMXBean
    osBean match {
      case sunOsBean: OperatingSystemMXBean => sunOsBean.getCpuLoad * 100.0
      // Handling for non-Sun implementations or unsupported platforms
      case _                                => -1.0
    }
  }

  def getData[F[_]: Sync]: F[SystemData] = Sync[F].delay(SystemData(cpu, nonHeapMB, heapMB))

  def apply[F[_]: Async: Metrics](period: FiniteDuration = 1.second): Stream[F, Unit] =
    Stream
      .repeatEval(getData.flatMap { case SystemData(cpu, heapMB, nonHeapMB) =>
        val fields =
          List(Field("cpu", cpu.toString), Field("heapMB", heapMB.toString), Field("nonHeapMB", nonHeapMB.toString))
        Metrics[F].measurement("system", fields)
      })
      .metered(period)
      .void
}
