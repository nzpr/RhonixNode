package sim.balances

import cats.syntax.all.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sdk.primitive.ByteArray
import sim.balances.MergeLogicForPayments.*
import sim.balances.data.{BalancesDeploy, BalancesDeployBody, BalancesState}

class MergeLogicForPaymentsSpec extends AnyFlatSpec with Matchers {

  behavior of "attemptCombine"

  it should "compute valid output" in {
    val initBalances = Map(1 -> 4L, 2 -> 1L)
    val change       = Map(1 -> -1L)
    val reference    = Map(1 -> 3L, 2 -> 1L)

    val b   = new BalancesState(initBalances)
    val neg = BalancesDeploy(ByteArray(List()), BalancesDeployBody(new BalancesState(change), 0))
    attemptCombine(b, neg).map(_.diffs) shouldBe new BalancesState(reference).diffs.some
  }

  it should "handle edge case" in {
    val initBalances = Map(1 -> 1L)
    val zero         = Map(1 -> -1L)

    val b        = new BalancesState(initBalances)
    val zeroCase = BalancesDeploy(ByteArray(List()), BalancesDeployBody(new BalancesState(zero), 0))
    attemptCombine(b, zeroCase).map(_.diffs).isDefined shouldBe true
  }

  it should "reject deploy if leads negative" in {
    val initBalances = Map(1 -> 1L)
    val changeNeg    = Map(1 -> -2L)

    val b   = new BalancesState(initBalances)
    val neg = BalancesDeploy(ByteArray(List()), new BalancesDeployBody(BalancesState(changeNeg), 0))
    attemptCombine(b, neg) shouldBe None
  }

  it should "throw exception on Long overflow" in {
    val initBalances = Map(1 -> Long.MaxValue)
    val changeNeg    = Map(1 -> 1L)

    val b   = new BalancesState(initBalances)
    val neg = BalancesDeploy(ByteArray(List()), new BalancesDeployBody(BalancesState(changeNeg), 0))
    intercept[Exception](attemptCombine(b, neg))
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
