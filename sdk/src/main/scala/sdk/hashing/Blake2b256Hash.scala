package sdk.hashing

import sdk.codecs.Codec
import sdk.primitive.ByteArray
import sdk.syntax.all.sdkSyntaxByteArray

import scala.util.Try

/**
 * Represents a Blake2b 256 bit hash
 *
 * The default constructor is private to prevent construction of invalid hash, although constructions from
 * raw bytes are provided, see [[Blake2b256Hash.deserialize]] functions.
 */
final case class Blake2b256Hash private (bytes: ByteArray) {

  require(
    bytes.length == Blake2b256Hash.Length,
    s"Expected ${Blake2b256Hash.Length} but got ${bytes.length}",
  )

  override def toString: String = s"Blake(${bytes.toHex})"
}

object Blake2b256Hash {

  // Ordering for hash uses underlying ordering of ByteArray
  implicit val ordering: Ordering[Blake2b256Hash] = Ordering.by(_.bytes)

  val Length: Int = Blake2b.Hash256LengthBytes

  /**
   * Calculates Blake2b hash from bytes and creates [[Blake2b256Hash]] instance.
   *
   * To create [[Blake2b256Hash]] instance from raw bytes see [[Blake2b256Hash.deserialize]] functions.
   *
   * @param bytes The bytes to hash
   */
  def apply(bytes: Array[Byte]): Blake2b256Hash =
    new Blake2b256Hash(ByteArray(Blake2b.hash256(bytes)))

  // Serialization

  /** Deserialization from hash as raw bytes */
  def deserialize(bytes: ByteArray): Try[Blake2b256Hash] = Try(new Blake2b256Hash(bytes))

  /** Deserialization from hash as raw bytes */
  def deserialize(bytes: Array[Byte]): Try[Blake2b256Hash] = deserialize(ByteArray(bytes))

  def codec: Codec[Blake2b256Hash, ByteArray] = new Codec[Blake2b256Hash, ByteArray] {
    override def encode(x: Blake2b256Hash): Try[ByteArray] = Try(x.bytes)

    override def decode(x: ByteArray): Try[Blake2b256Hash] = Blake2b256Hash.deserialize(x)
  }
}
