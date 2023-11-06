package sdk.api.data

import cats.kernel.Eq

final case class Deploy(
  sig: Array[Byte],
  publicKey: Array[Byte],
  shardId: String,
  program: String,
  phloPrice: Long,
  phloLimit: Long,
  timestamp: Long,
  validAfterBlockNumber: Long,
  status: Long,
) {
  override def equals(obj: Any): Boolean = obj match {
    case that: Deploy => this.sig sameElements that.sig
    case _            => false
  }

  override def hashCode(): Int = sig.hashCode()
}

object Deploy {
  implicit val deployEq: Eq[Deploy] = Eq.fromUniversalEquals
}
