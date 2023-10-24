package sim.balances

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sim.balances.MergeLogicForPayments.*
import sim.balances.data.BalancesState

import scala.concurrent.duration.DurationInt

class MergeLogicForPaymentsSpec extends AnyFlatSpec with Matchers {

  behavior of "attemptCombine"

  it should "compute valid output" in {
    val initBalances = Map(1 -> 4L, 2 -> 1L)
    val change       = Map(1 -> -1L)
    val reference    = Map(1 -> 3L, 2 -> 1L)
    attemptCombineNonNegative(initBalances, change) shouldBe new BalancesState(reference).diffs.some
  }

  it should "handle edge case" in {
    val initBalances = Map(1 -> 1L)
    val zero         = Map(1 -> -1L)
    attemptCombineNonNegative(initBalances, zero).isDefined shouldBe true
  }

  it should "reject deploy if leads negative" in {
    val initBalances = Map(1 -> 1L)
    val changeNeg    = Map(1 -> -2L)
    attemptCombineNonNegative(initBalances, changeNeg) shouldBe None
  }

  it should "throw exception on Long overflow" in {
    val initBalances = Map(1 -> Long.MaxValue)
    val changeNeg    = Map(1 -> 1L)
    intercept[Exception](attemptCombineNonNegative(initBalances, changeNeg))
  }

  behavior of "foldCollectFailures"

  it should "output correct combination result and failures" in {
    val state = 4
    val items = List(1, 2, 3)

    def attemptCombine(state: Int, item: Int): Option[Int] = item match {
      case 3 => none[Int]
      case x => (state + x).some
    }

    foldCollectFailures(state, items, attemptCombine) shouldBe (7, Seq(3))
  }
}
