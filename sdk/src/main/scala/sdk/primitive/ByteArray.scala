package sdk.primitive

import sdk.ByteBufferOps
import sdk.codecs.Base16

import java.nio.ByteBuffer
import java.util

/** An immutable array of bytes */
sealed class ByteArray private (underlying: Array[Byte]) {
  def size: Int                = length
  def length: Int              = underlying.length
  def nonEmpty: Boolean        = underlying.nonEmpty
  def isEmpty: Boolean         = underlying.isEmpty
  def head: Byte               = underlying.head
  def headOption: Option[Byte] = underlying.headOption
  def tail: ByteArray          = ByteArray(underlying.tail)

  /** The element at given index. */
  def apply(i: Int): Byte = underlying(i)

  /** Return copy of underlying byte array.
    * The operation does not clone the output data, changing output array will change internal array instance. */
  def toArray: Array[Byte] = underlying

  /** A copy of this array with one single replaced element. */
  final def updated(idx: Int, b: Byte): ByteArray = ByteArray(underlying.updated(idx: Int, b: Byte))

  /** A copy of this array with all elements of an array appended. */
  def ++(other: ByteArray): ByteArray =
    ByteArray(underlying ++ other.toArray)

  /** Returns a new vector with the specified byte prepended. */
  def +:(byte: Byte): ByteArray = ByteArray(byte +: underlying)

  /** Returns a new vector with the specified byte appended. */
  def :+(byte: Byte): ByteArray = ByteArray(underlying :+ byte)

  /** Converts the contents of this byte vector to a hexadecimal string of size * 2 nibbles. */
  def toHex: String = Base16.encode(underlying)

  /** Creates a ByteBuffer instance from a ByteArray. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  def toByteBuffer: ByteBuffer = ByteBufferOps.toByteBuffer(underlying)

  override def equals(other: Any): Boolean = other match {
    case other: ByteArray => util.Arrays.equals(this.underlying, other.toArray)
    case _                => false
  }

  override def hashCode(): Int = util.Arrays.hashCode(underlying)
}

object ByteArray {

  // Ordering uses Java comparison of underlying bytes
  implicit val ordering: Ordering[ByteArray] =
    (x: ByteArray, y: ByteArray) => util.Arrays.compare(x.toArray, y.toArray)

  val Default: ByteArray = ByteArray(Array[Byte]())

  /** Creates a ByteArray instance from a Array[Byte].
   * The operation does not clone input the data, changing the input array will change instance. */
  def apply(x: Array[Byte]): ByteArray = new ByteArray(x)

  def apply(x: Seq[Byte]): ByteArray = ByteArray(x.toArray)

  def apply(x: Byte): ByteArray = new ByteArray(Array(x))

  /** Creates a ByteArray instance from a ByteBuffer. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  def apply(buffer: ByteBuffer): ByteArray = ByteArray(ByteBufferOps.fromByteBuffer(buffer))
}
