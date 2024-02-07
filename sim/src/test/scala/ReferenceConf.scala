import org.scalatest.flatspec.AnyFlatSpec
import sdk.reflect.ClassesAsConfig

class ReferenceConf extends AnyFlatSpec {
  "this" should "output reference configuration for simulation" in {
    val s = ClassesAsConfig(
      "gorki",
      sim.Config.Default,
      node.Config.Default,
      diagnostics.metrics.Config.Default,
    )
    println(s)
  }
}
