package sdk.api.data

import cats.kernel.Eq

final case class Block(
  hash: Array[Byte],
  sender: Array[Byte],
  version: Int,
  shardId: String,
  seqNum: Long,
  number: Long,
  justifications: Set[Justification],
  bonds: Set[Bond],

  // Rholang (tuple space) state change
  finStateHash: Array[Byte],  // hash(VM state)
  preStateHash: Array[Byte],  // hash(VM state)
  postStateHash: Array[Byte], // hash(VM state)

  deploys: Set[Deploy],

  // Block signature
  signatureAlg: String,
  signature: Array[Byte],

  // Status
  status: Int,
) {
  override def equals(obj: Any): Boolean = obj match {
    case that: Block => this.hash sameElements that.hash
    case _           => false
  }

  override def hashCode(): Int = hash.hashCode()
}

object Block {
  implicit val blockEq: Eq[Block] = Eq.fromUniversalEquals
}
