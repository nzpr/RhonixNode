package sdk.data

import sdk.Base16

import java.io.OutputStream
import java.nio.ByteBuffer

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

  /** Return copy of underlying byte array.*/
  def toArray: Array[Byte] = underlying.clone()

  /** Return underlying byte array.
   * The operation does not clone the output data, changing output array will change instance. */
  def toArrayUnsafe: Array[Byte] = underlying

  /** A copy of this array with one single replaced element. */
  final def updated(idx: Int, b: Byte): ByteArray = ByteArray(underlying.updated(idx: Int, b: Byte))

  /** A copy of this array with all elements of an array appended. */
  def ++(other: ByteArray): ByteArray =
    ByteArray(underlying ++ other.toArrayUnsafe)

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
  def toByteBuffer: ByteBuffer = {
    val copyArr = underlying.clone() // Create a copy of the original array
    ByteBuffer.wrap(copyArr) // Wrap the copy in a ByteBuffer
  }

  /** Creates a ByteBuffer instance from a ByteArray. When created, a duplicate of the data is created in memory.
   * The operation does not clone input the data, changing the input array will change instance. */
  def toByteBufferUnsafe: ByteBuffer = ByteBuffer.wrap(underlying)

  /** Result of comparing `this` with operand `that`.
   * Implement this method to determine how instances of A will be sorted.
   * Returns `x` where:
   *   - `x < 0` when `this < that`
   *   - `x == 0` when `this == that`
   *   - `x > 0` when  `this > that`
   */
  @SuppressWarnings(Array("org.wartremover.warts.Return", "org.wartremover.warts.Var"))
  def compare(that: ByteArray): Int =
    if (this eq that)
      0
    else {
      val thisLength   = this.length
      val thatLength   = that.length
      val commonLength = thisLength.min(thatLength)
      var i            = 0
      while (i < commonLength) {
        val cmp = (this.underlying(i) & 0xff).compare(that.underlying(i) & 0xff)
        if (cmp != 0)
          return cmp
        i += 1
      }
      if (thisLength < thatLength)
        -1
      else if (thisLength > thatLength)
        1
      else
        0
    }

  override def equals(other: Any): Boolean = other match {
    case x: ByteArray => this.underlying sameElements x.underlying
    case _            => false
  }

  override def hashCode(): Int = java.util.Arrays.hashCode(underlying)
}

object ByteArray {

  /** Creates a ByteArray instance from a Array[Byte]. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  def apply(x: Array[Byte]): ByteArray = new ByteArray(x.clone())

  def apply(x: Seq[Byte]): ByteArray = ByteArray(x.toArray)

  /** Creates a ByteArray instance from a Array[Byte].
   * The operation does not clone input the data, changing the input array will change instance. */
  def applyUnsafe(x: Array[Byte]): ByteArray = new ByteArray(x)

  /** Creates a ByteArray instance from a ByteBuffer. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  def apply(buffer: ByteBuffer): ByteArray = {
    val c   = buffer.duplicate()
    val arr = new Array[Byte](c.remaining)
    c.get(arr)
    ByteArray(arr)
  }

  /** Constructs a ByteArray from a list of literal bytes.
   * Only the least significant byte is used of each integral value. */
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  def apply[A: Integral](bytes: A*): ByteArray = {
    val integral = implicitly[Integral[A]]
    val buf      = new Array[Byte](bytes.size)
    var i        = 0
    bytes.foreach { b =>
      buf(i) = integral.toInt(b).toByte
      i += 1
    }
    ByteArray(buf)
  }

  val Empty: ByteArray = ByteArray(Array[Byte]())
}
