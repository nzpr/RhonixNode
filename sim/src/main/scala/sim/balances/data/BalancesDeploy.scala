package sim.balances.data

/**
 * Deploy is a state diff with id.
 * */
final case class BalancesDeploy(id: String, state: BalancesState) {
  override def equals(obj: Any): Boolean = obj match {
    case BalancesDeploy(id, _) => id == this.id
    case _                     => false
  }

  override def hashCode(): Int = id.hashCode
}

object BalancesDeploy {
  implicit val ordDeploy: Ordering[BalancesDeploy] = Ordering.by(_.id)
}
