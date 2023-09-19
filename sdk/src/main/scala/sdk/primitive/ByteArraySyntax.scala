package sdk.primitive

import sdk.codecs.Base16
import sdk.syntax.all.sdkSyntaxArrayByte

import java.nio.ByteBuffer

trait ByteArraySyntax {
  implicit def sdkSyntaxByteArray(ba: ByteArray): ByteArrayOps = new ByteArrayOps(ba)
}

final class ByteArrayOps(private val ba: ByteArray) extends AnyVal {
  private def arr: Array[Byte] = ba.toArray

  def size: Int = length

  def length: Int = arr.length

  def nonEmpty: Boolean = arr.nonEmpty

  def isEmpty: Boolean = arr.isEmpty

  def head: Byte = arr.head

  def headOption: Option[Byte] = arr.headOption

  def tail: ByteArray = ByteArray(arr.tail)

  /** The element at given index. */
  def apply(i: Int): Byte = arr(i)

  /** A copy of this array with one single replaced element. */
  def updated(idx: Int, b: Byte): ByteArray = ByteArray(arr.updated(idx: Int, b: Byte))

  /** A copy of this array with all elements of an array appended. */
  def ++(other: ByteArray): ByteArray =
    ByteArray(arr ++ other.toArray)

  /** Returns a new vector with the specified byte prepended. */
  def +:(byte: Byte): ByteArray = ByteArray(byte +: arr)

  /** Returns a new vector with the specified byte appended. */
  def :+(byte: Byte): ByteArray = ByteArray(arr :+ byte)

  /** Converts the contents of this byte vector to a hexadecimal string of size * 2 nibbles. */
  def toHex: String = Base16.encode(arr)

  /** Creates a ByteBuffer instance from a ByteArray. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  def toByteBuffer: ByteBuffer = arr.toByteBuffer

}
