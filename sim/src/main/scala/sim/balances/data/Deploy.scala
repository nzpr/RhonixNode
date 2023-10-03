package sim.balances.data

final case class Deploy(id: String, nonce: Long, signer: Long, payee: Long, amt: Long) {
  override def equals(obj: Any): Boolean = obj match {
    case Deploy(id, _, _, _, _) => id == this.id
    case _                      => false
  }

  override def hashCode(): Int = id.hashCode
}

object Deploy {
  implicit val ordDeploy: Ordering[Deploy] = Ordering.by(_.id)

  def toDiff(d: Deploy): Map[Long, Long] = Map(d.signer -> -d.amt, d.payee -> d.amt)
}
