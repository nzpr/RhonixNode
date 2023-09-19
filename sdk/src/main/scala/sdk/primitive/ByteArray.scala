package sdk.primitive

import sdk.syntax.all.sdkSyntaxByteBuffer

import java.nio.ByteBuffer
import java.util

/** An immutable array of bytes */
sealed class ByteArray private (underlying: Array[Byte]) {

  /** Return copy of underlying byte array.
    * The operation does not clone the output data, changing output array will change internal array instance. */
  def toArray: Array[Byte] = underlying

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
  def apply(buffer: ByteBuffer): ByteArray = ByteArray(buffer.toArray)
}
