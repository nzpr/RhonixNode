package sdk

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class ProcessorSpec extends AnyFlatSpec {
  val p = Processor.default[Int]

  it should "implement structural equivalence" in {
    val p1      = Processor.default[Int]
    val p2      = Processor.default[Int]
    val (p3, _) = p2.start(0)
    p1 == p2 shouldBe true
    p1 == p3 shouldBe false
  }

  "init state" should "be empty" in {
    p.processingSet shouldBe Set()
  }

  it should "allow processing" in {
    val (_, r) = p.start(1)
    r shouldBe true
  }

  it should "not allow starting another processing for the same item until it's done" in {
    val (p1, _) = p.start(1)
    val (_, r)  = p1.start(1)
    r shouldBe false
  }

  // Items that are done are supposed to be stored in some upstream state which should be responsible for
  // detecting duplicates if required
  it should "allow starting another processing for the same item after it's done" in {
    val (p1, _) = p.start(1)
    val (_, r)  = p1.done(1).start(1)
    r shouldBe true
  }

  it should "allow concurrent processing for different items" in {
    val (p1, r1) = p.start(1)
    val (_, r2)  = p1.start(2)
    r1 shouldBe true
    r2 shouldBe true
  }
}
