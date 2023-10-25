package sim.balances

import cats.syntax.all.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sim.balances.MergeLogicForPayments.*
import sim.balances.data.{Account, BalancesDeploy, BalancesState}

class MergeLogicForPaymentsSpec extends AnyFlatSpec with Matchers {

  behavior of "attemptCombine"

  it should "compute valid output" in {
    val initBalances = Map(1 -> Account(4L, 0), 2 -> Account(1L, 0))
    val change       = Map(1 -> -1L)
    // nonce for account 1 become 1, balance is s sum
    val reference    = Map(1 -> Account(3L, 1), 2 -> Account(1L, 0))

    val neg = BalancesDeploy("0", 1, new BalancesState(change), 1)
    attemptCombine(initBalances, neg) shouldBe reference.some
  }

  it should "handle edge case" in {
    val initBalances = Map(1 -> Account(1L, 0))
    val zero         = Map(1 -> -1L)

    val zeroCase = BalancesDeploy("0", 1, new BalancesState(zero), 1)
    attemptCombine(initBalances, zeroCase).isDefined shouldBe true
  }

  it should "reject deploy if leads negative" in {
    val initBalances = Map(1 -> Account(1L, 0))
    val changeNeg    = Map(1 -> -2L)

    val neg = BalancesDeploy("0", 1, new BalancesState(changeNeg), 1)
    attemptCombine(initBalances, neg) shouldBe None
  }

  it should "reject deploy if nonce is not greater then latest" in {
    val initBalances = Map(1 -> Account(1L, 2))
    val change       = Map(1 -> 2L)

    val neg = BalancesDeploy("0", 1, new BalancesState(change), 1)
    attemptCombine(initBalances, neg) shouldBe None
  }

  it should "throw exception on Long overflow" in {
    val initBalances = Map(1 -> Account(Long.MaxValue, 0))
    val changeNeg    = Map(1 -> 1L)

    val neg = BalancesDeploy("0", 1, new BalancesState(changeNeg), 1)
    intercept[Exception](attemptCombine(initBalances, neg))
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
