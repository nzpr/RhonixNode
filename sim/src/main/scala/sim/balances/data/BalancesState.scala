package sim.balances.data

import sim.balances.{Balance, Wallet}

/**
 * Minimalistic state that support only storing balances.
 * Analogous to the full tuple space it can be represent with a map.
 * */
final class BalancesState(val diffs: Map[Wallet, Balance]) extends AnyVal {
  // Replace or add records with data from the input balances
  def ++(that: BalancesState) = new BalancesState(this.diffs ++ that.diffs)

  override def toString: String = diffs.toString()
}

object BalancesState {
  val Default = new BalancesState(Map.empty[Wallet, Balance])
}
