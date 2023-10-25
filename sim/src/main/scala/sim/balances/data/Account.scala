package sim.balances.data

final case class Account(balance: Long, nonce: Long)

object Account {
  val Default: Account = Account(0L, -1L)
}
