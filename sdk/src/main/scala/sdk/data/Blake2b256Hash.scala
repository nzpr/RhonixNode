package sdk.data

import blakehash.Blake2b256

import java.io.OutputStream

/**
 * Represents a Blake2b256 Hash
 *
 * The default constructor is private to prevent construction means other than [[Blake2b256Hash$.create(bytes:*]] or [[Blake2b256Hash$.create(ByteArray:*)]]
 */
class Blake2b256Hash private (val bytes: ByteArray) {

  require(
    bytes.length == Blake2b256Hash.length,
    s"Expected ${Blake2b256Hash.length} but got ${bytes.length}",
  )

  override def equals(obj: scala.Any): Boolean = obj match {
    case b: Blake2b256Hash => b.bytes == bytes
    case _                 => false
  }

  override def hashCode(): Int =
    bytes.hashCode()

  override def toString: String = s"Blake(${bytes.toHex})"
}

object Blake2b256Hash {

  val length: Int = Blake2b256.hashLength

  /**
   * Constructs a [[Blake2b256Hash]]
   *
   * @param bytes The bytes to hash
   * @return The hash
   */
  def create(bytes: Array[Byte]): Blake2b256Hash =
    new Blake2b256Hash(ByteArray(Blake2b256.hash(bytes)))

  /**
   * Constructs a [[Blake2b256Hash]]
   *
   * @param byteArrays sequence of byte vectors,
   * that will be hashed as a single concatenated
   * bytes string
   * @return The hash
   */
  def create(byteArrays: Seq[ByteArray]): Blake2b256Hash =
    new Blake2b256Hash(ByteArray(Blake2b256.hash(byteArrays*)(copyToStream)))

  def create(byteArray: ByteArray): Blake2b256Hash =
    new Blake2b256Hash(ByteArray(Blake2b256.hash(byteArray)(copyToStream)))

  def fromByteArray(bytes: ByteArray): Blake2b256Hash =
    new Blake2b256Hash(bytes)

  def fromHex(string: String): Blake2b256Hash =
    fromByteArray(ByteArray(Base16.unsafeDecode(string)))

  def fromHexEither(string: String): Either[String, Blake2b256Hash] =
    Base16
      .decode(string)
      .fold[Either[String, Blake2b256Hash]](Left(s"Invalid hex string $string"))(b =>
        Right(new Blake2b256Hash(ByteArray(b))),
      )

  def fromByteArray(bytes: Array[Byte]): Blake2b256Hash =
    fromByteArray(ByteArray(bytes))

  implicit val ordering: Ordering[Blake2b256Hash] =
    (x: Blake2b256Hash, y: Blake2b256Hash) => x.bytes.compare(y.bytes)

  private def copyToStream(bv: ByteArray, stream: OutputStream): Unit = bv.copyToStream(stream)
}
