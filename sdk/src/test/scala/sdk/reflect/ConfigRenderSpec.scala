package sdk.reflect

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigRenderSpec extends AnyFlatSpec with Matchers {

  case class Config(
    @Description("anno1")
    value1: Boolean = true,
    @Description("anno2")
    value2: Int = 0,
  )

  "it" should "generate valid list of fields" in {
    val s = ClassAsTuple(Config())
    s shouldBe List(("value1", true, "anno1"), ("value2", 0, "anno2"))
  }
}
