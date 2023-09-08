package sdk.tmp

import scodec.bits.ByteVector

import java.nio.ByteBuffer

object ByteVectorOps {

  implicit class RichByteVector(byteVector: ByteVector) {

    @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
    def toDirectByteBuffer: ByteBuffer = {
      val buffer: ByteBuffer = ByteBuffer.allocateDirect(byteVector.size.toInt)
      byteVector.copyToBuffer(buffer)
      buffer.flip()
      buffer
    }
  }
}
