package io.rhonix.node.api.grpc.codecs

import com.google.protobuf.{CodedInputStream, CodedOutputStream}
import io.rhonix.node.api.grpc.data.*

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

  def writeBalanceRequestProtobuf(obj: BalanceRequest): InputStream =
    writeProtobufBracket(out => out.writeStringNoTag(obj.wallet))

  def readBalanceRequestProtobuf(in: InputStream): BalanceRequest = {
    val protoStream = CodedInputStream.newInstance(in)
    val balance     = protoStream.readString()
    BalanceRequest(balance)
  }

  def writeBalanceResponseProtobuf(obj: BalanceResponse): InputStream =
    writeProtobufBracket(out => out.writeFixed64NoTag(obj.balance))

  def readBalanceResponseProtobuf(in: InputStream): BalanceResponse = {
    val protoStream = CodedInputStream.newInstance(in)
    val balance     = protoStream.readFixed64()
    BalanceResponse(balance)
  }
}
