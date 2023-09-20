package io.rhonix.node.api.grpc.codecs

import com.google.protobuf.{CodedInputStream, CodedOutputStream}

import java.io.{InputStream, PipedInputStream, PipedOutputStream}

/// Protobuf marshalling/unmarshalling for RPC data types.
package object protobuf {

  /// Input stream given writer to output stream. Safe to writer failure.
  private def writeProtobufBracket(writer: CodedOutputStream => Unit): PipedInputStream = {
    val pipeInput = new PipedInputStream
    val pipeOut   = new PipedOutputStream(pipeInput)
    val outStream = CodedOutputStream.newInstance(pipeOut)
    try writer(outStream)
    finally {
      outStream.flush()
      pipeOut.flush()
      pipeOut.close()
    }
    pipeInput
  }

  def writeBalanceRequestProtobuf(obj: String): InputStream =
    writeProtobufBracket(out => out.writeStringNoTag(obj))

  def readBalanceRequestProtobuf(in: InputStream): String = {
    val protoStream = CodedInputStream.newInstance(in)
    val wallet      = protoStream.readString()
    wallet
  }

  def writeBalanceResponseProtobuf(obj: Long): InputStream =
    writeProtobufBracket(out => out.writeFixed64NoTag(obj))

  def readBalanceResponseProtobuf(in: InputStream): Long = {
    val protoStream = CodedInputStream.newInstance(in)
    val balance     = protoStream.readFixed64()
    balance
  }
}
