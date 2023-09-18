package sdk.history

import sdk.primitive.ByteArray
import scala.annotation.tailrec

final case class KeySegment(value: ByteArray) extends AnyVal {
  def size: Int                = value.size
  def nonEmpty: Boolean        = value.nonEmpty
  def isEmpty: Boolean         = value.isEmpty
  def head: Byte               = value.head
  def tail: KeySegment         = KeySegment(value.tail)
  def headOption: Option[Byte] = value.headOption

  def ++(other: KeySegment): KeySegment =
    KeySegment(value ++ other.value)

  def :+(byte: Byte): KeySegment = KeySegment(value :+ byte)
  def toHex: String              = value.toHex
}

object KeySegment {
  val Default: KeySegment = KeySegment(ByteArray.Default)

  def apply(bv: ByteArray): KeySegment   = {
    require(bv.size <= 127, "Size of key segment is more than 127")
    new KeySegment(bv)
  }
  def apply(ab: Array[Byte]): KeySegment = KeySegment(ByteArray(ab))
  def apply(sb: Seq[Byte]): KeySegment   = KeySegment(ByteArray(sb))

  /**
    * Find the common part of a and b.
    *
    * @return (Common part, rest of a, rest of b).
    */
  def commonPrefix(a: KeySegment, b: KeySegment): (KeySegment, KeySegment, KeySegment) = {
    @tailrec
    def go(common: KeySegment, l: KeySegment, r: KeySegment): (KeySegment, KeySegment, KeySegment) =
      if (r.isEmpty || l.isEmpty) (common, l, r)
      else {
        val lHead = l.head
        val rHead = r.head
        if (lHead == rHead) go(common :+ lHead, l.tail, r.tail)
        else (common, l, r)
      }

    go(KeySegment.Default, a, b)
  }
}
