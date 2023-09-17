package sdk.primitive

import sdk.ByteBufferOps
import sdk.codecs.Base16

import java.io.OutputStream
import java.nio.ByteBuffer
import java.util

/** An immutable array of bytes */
sealed case class ByteArray private (underlying: Array[Byte]) {
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
    * The operation does not clone the output data, changing output array will change instance. */
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

  def copyToStream(s: OutputStream): Unit = s.write(underlying)

  /** Converts the contents of this byte vector to a hexadecimal string of size * 2 nibbles. */
  def toHex: String = Base16.encode(underlying)

  /** Creates a ByteBuffer instance from a ByteArray. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  def toByteBuffer: ByteBuffer = ByteBufferOps.toByteBuffer(underlying)

  /** Result of comparing `this` with operand `that`.
   * Implement this method to determine how instances of A will be sorted.
   * Returns `x` where:
   *   - `x < 0` when `this < that`
   *   - `x == 0` when `this == that`
   *   - `x > 0` when  `this > that`
   */
  def compare(that: ByteArray): Int = util.Arrays.compare(this.underlying, that.toArray)

  override def equals(other: Any): Boolean = other match {
    case x: ByteArray if this.eq(x) => true
    case x: ByteArray               => this.underlying sameElements x.underlying
    case _                          => false
  }

  override def hashCode(): Int = java.util.Arrays.hashCode(underlying)
}

object ByteArray {

  /** Creates a ByteArray instance from a Array[Byte].
   * The operation does not clone input the data, changing the input array will change instance. */
  def apply(x: Array[Byte]): ByteArray = new ByteArray(x)

  def apply(x: Seq[Byte]): ByteArray = ByteArray(x.toArray)

  def apply(x: Byte): ByteArray = new ByteArray(Array(x))

  /** Creates a ByteArray instance from a ByteBuffer. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  def apply(buffer: ByteBuffer): ByteArray = ByteArray(ByteBufferOps.fromByteBuffer(buffer))

  val Empty: ByteArray = ByteArray(Array[Byte]())
}
