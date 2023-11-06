package sdk.api.data

final case class Justification(sender: Array[Byte], blockHash: Array[Byte]) {
  override def equals(obj: Any): Boolean = obj match {
    case that: Justification => blockHash sameElements that.blockHash
    case _                   => false
  }

  override def hashCode(): Int = blockHash.hashCode()
}
