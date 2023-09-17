package sdk

import java.nio.ByteBuffer

object ByteBufferOps {

  /** Creates a Array[Byte] instance from a [[ByteBuffer]]. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  def fromByteBuffer(buffer: ByteBuffer): Array[Byte] = {
    val c   = buffer.duplicate()
    val arr = new Array[Byte](c.remaining)
    c.get(arr)
    arr
  }

  /** Creates a ByteBuffer instance from a Array[Byte]. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  def toByteBuffer(array: Array[Byte]): ByteBuffer = {
    val copyArr = array.clone() // Create a copy of the original array
    ByteBuffer.wrap(copyArr) // Wrap the copy in a ByteBuffer
  }
}
