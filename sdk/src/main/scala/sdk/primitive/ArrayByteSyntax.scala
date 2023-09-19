package sdk.primitive

import java.nio.ByteBuffer

trait ArrayByteSyntax {
  implicit def sdkSyntaxArrayByte(x: Array[Byte]): ArrayByteOps = new ArrayByteOps(x)
}

final class ArrayByteOps(private val x: Array[Byte]) extends AnyVal {

  /**
   * Wrap the copy of array with the ByteBuffer.
   * */
  def toByteBuffer: ByteBuffer = ByteBuffer.wrap(x.clone())
}
