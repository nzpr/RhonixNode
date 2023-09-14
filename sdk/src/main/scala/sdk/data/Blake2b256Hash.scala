package sdk.data

import blakehash.Blake2b256
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{bytes, fixedSizeBytes}

/**
 * Represents a Blake2b256 Hash
 *
 * The default constructor is private to prevent construction means other than [[Blake2b256Hash$.create(bytes:*]] or [[Blake2b256Hash$.create(byteVectors:*]]
 */
class Blake2b256Hash private (val bytes: ByteVector) {

  require(
    bytes.length == Blake2b256Hash.length,
    s"Expected ${Blake2b256Hash.length} but got ${bytes.length}",
  )

  override def equals(obj: scala.Any): Boolean = obj match {
    case b: Blake2b256Hash => b.bytes === bytes
    case _                 => false
  }

  override def hashCode(): Int =
    bytes.hashCode

  override def toString: String = s"Blake(${bytes.toHex})"
}

object Blake2b256Hash {

  val length: Int = 32

  /**
   * Constructs a [[Blake2b256Hash]]
   *
   * @param bytes The bytes to hash
   * @return The hash
   */
  def create(bytes: Array[Byte]): Blake2b256Hash =
    new Blake2b256Hash(ByteVector(Blake2b256.hash(bytes)))

  /**
   * Constructs a [[Blake2b256Hash]]
   *
   * @param byteVectors sequence of byte vectors,
   * that will be hashed as a single concatenated
   * bytes string
   * @return The hash
   */
  def create(byteVectors: Seq[ByteVector]): Blake2b256Hash =
    new Blake2b256Hash(ByteVector(Blake2b256.hash(byteVectors: _*)))

  def create(byteVector: ByteVector): Blake2b256Hash =
    new Blake2b256Hash(ByteVector(Blake2b256.hash(byteVector)))

  def fromByteVector(bytes: ByteVector): Blake2b256Hash =
    new Blake2b256Hash(bytes)

  def fromHex(string: String): Blake2b256Hash =
    fromByteVector(ByteVector(Base16.unsafeDecode(string)))

  def fromHexEither(string: String): Either[String, Blake2b256Hash] =
    Base16
      .decode(string)
      .fold[Either[String, Blake2b256Hash]](Left(s"Invalid hex string $string"))(b =>
        Right(new Blake2b256Hash(ByteVector(b))),
      )

  def fromByteArray(bytes: Array[Byte]): Blake2b256Hash =
    fromByteVector(ByteVector(bytes))

  implicit val ordering: Ordering[Blake2b256Hash] =
    (x: Blake2b256Hash, y: Blake2b256Hash) => x.bytes.compare(y.bytes)
}
