package sdk.primitive

import java.nio.ByteBuffer

trait ByteBufferSyntax {
  implicit def sdkSyntaxByteBuffer(bb: ByteBuffer): ByteBufferOps = new ByteBufferOps(bb)
}

final class ByteBufferOps(private val bb: ByteBuffer) extends AnyVal {

  /** Creates a Array[Byte] instance from a [[ByteBuffer]]. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  def toArray: Array[Byte] = {
    val c   = bb.duplicate()
    val arr = new Array[Byte](c.remaining)
    c.get(arr)
    arr
  }
}
