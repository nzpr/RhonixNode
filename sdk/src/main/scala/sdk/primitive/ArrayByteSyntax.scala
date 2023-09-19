package sdk.primitive

import java.nio.ByteBuffer

trait ArrayByteSyntax {
  implicit def sdkSyntaxArrayByte(arr: Array[Byte]): ArrayByteOps = new ArrayByteOps(arr)
}

final class ArrayByteOps(private val arr: Array[Byte]) extends AnyVal {

  /** Creates a ByteBuffer instance from a Array[Byte]. When created, a duplicate of the data is created in memory.
   * This ensures that any changes made to the ByteBuffer won't affect the original
   * array because they are separate copies. */
  def toByteBuffer: ByteBuffer = {
    val copyArr = arr.clone()
    ByteBuffer.wrap(copyArr)
  }
}
