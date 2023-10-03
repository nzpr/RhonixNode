package sim.balances.data

/** Data sitting on a channel. Analogous to Datum in Rholang. */
final case class Datum(balance: Long, latestNonce: Long)
