package sim.balances.data

import sim.balances.Wallet

/**
 * Deploy is a state diff with id and nonce.
 * */
final case class BalancesDeploy(id: String, deployer: Wallet, state: BalancesState, nonce: Long) {
  override def equals(obj: Any): Boolean = obj match {
    case BalancesDeploy(id, _, _, _) => id == this.id
    case _                           => false
  }

  override def hashCode(): Int = id.hashCode
}

object BalancesDeploy {
  implicit val ordDeploy: Ordering[BalancesDeploy] = Ordering.by(_.id)
}
