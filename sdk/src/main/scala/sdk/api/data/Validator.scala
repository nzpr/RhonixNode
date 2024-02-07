package sdk.api.data

final case class Validator(
  id: Long,              // PK key
  publicKey: Array[Byte],// Unique index
) {
  override def equals(obj: Any): Boolean = obj match {
    case that: Validator =>
      this.publicKey.sameElements(that.publicKey)
    case _               => false
  }
}
