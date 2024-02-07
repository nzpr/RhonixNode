package sdk.db.data

final case class Validator(
  publicKey: Array[Byte], // Unique index
) {
  override def equals(obj: Any): Boolean = obj match {
    case that: Validator => this.publicKey.sameElements(that.publicKey)
    case _               => false
  }

  override def hashCode(): Int = publicKey.hashCode()
}
