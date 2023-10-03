package sim.balances

import cats.syntax.all.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sim.balances.MergeLogicForPayments.*
import sim.balances.data.{Deploy, State}

class MergeLogicForPaymentsSpec extends AnyFlatSpec with Matchers {

  behavior of "attemptCombine"

  it should "compute valid output" in {
    val initBalances = Map(1 -> 4L, 2 -> 1L)
    val change       = Map(1 -> -1L)
    val reference    = Map(1 -> 3L, 2 -> 1L)

    val b   = new State(initBalances)
    val neg = BalancesDeploy("0", new State(change))
    attemptAdd(b, neg).map(_.diffs) shouldBe new State(reference).diffs.some
  }

  it should "handle edge case" in {
    val initBalances = Map(1 -> 1L)
    val zero         = Map(1 -> -1L)

    val b        = new State(initBalances)
    val zeroCase = BalancesDeploy("0", new State(zero))
    attemptAdd(b, zeroCase).map(_.diffs).isDefined shouldBe true
  }

  it should "reject deploy if leads negative" in {
    val initBalances = Map(1 -> 1L)
    val changeNeg    = Map(1 -> -2L)

    val b   = new State(initBalances)
    val neg = BalancesDeploy("0", new State(changeNeg))
    attemptAdd(b, neg) shouldBe None
  }

  it should "throw exception on Long overflow" in {
    val initBalances = Map(1 -> Long.MaxValue)
    val changeNeg    = Map(1 -> 1L)

    val b   = new State(initBalances)
    val neg = BalancesDeploy("0", new State(changeNeg))
    intercept[Exception](attemptAdd(b, neg))
  }

  behavior of "foldCollectFailures"

  it should "output correct combination result and failures" in {
    val state = 4
    val items = Seq(1, 2, 3)

    def attemptCombine(state: Int, item: Int): Option[Int] = item match {
      case 3 => none[Int]
      case x => (state + x).some
    }

    foldCollectFailures(state, items, attemptCombine) shouldBe (7, Seq(3))
  }
}
