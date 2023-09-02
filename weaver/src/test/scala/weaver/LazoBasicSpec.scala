package weaver

import cats.syntax.all.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import weaver.rules.Basic.*
import weaver.rules.*
import weaver.rules.Dag.computeFJS

import scala.annotation.tailrec

class LazoBasicSpec extends AnyFlatSpec with Matchers {

  "unambiguity violation" should "be detected" in {
    val eqs = Map(0 -> 10, 1 -> 10) // sender 10 equivocates by sending 0 and 1
    val ok  = Map(2 -> 11, 3 -> 12) // sender do not equivocate
    val all = eqs ++ ok
    unambiguity(eqs.keySet, all.keySet, all) shouldBe none[Offence]
    unambiguity(Set(), all.keySet, all) shouldBe InvalidUnambiguity(Map(10 -> Set(0, 1))).some
    unambiguity(Set(), ok.keySet, ok) shouldBe none[Offence]
  }

  "continuity violation" should "be detected" in {
    val selfJsMgjs = Set(0, 1, 2, 3)
    val seen       = selfJsMgjs.map(_ -> true).toMap
    continuity(seen, selfJsMgjs) shouldBe none[Offence]
    continuity(seen + (0 -> false), selfJsMgjs) shouldBe InvalidContinuity(Set(0)).some
  }

  "frugality violation" should "be detected" in {
    val prevOffences = Map(4 -> 10)
    val jssWrong     = Map(0 -> 10, 1 -> 11, 2 -> 12)
    val jssGood      = Map(1 -> 11, 2 -> 12)
    frugality(jssGood.keySet, prevOffences.keySet, jssGood ++ prevOffences) shouldBe none[Offence]
    frugality(jssWrong.keySet, prevOffences.keySet, jssWrong ++ prevOffences) shouldBe
      InvalidFrugality(Set(0)).some
  }
}
