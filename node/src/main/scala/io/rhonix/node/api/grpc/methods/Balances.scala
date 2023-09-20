package io.rhonix.node.api.grpc.methods

import io.grpc.*
import io.rhonix.node.api.grpc
import io.rhonix.node.api.grpc.codecs.protobuf.*

import java.io.InputStream

object Balances {

  private val protobufRequestMarshaller = new MethodDescriptor.Marshaller[String] {
    override def stream(obj: String): InputStream       = writeBalanceRequestProtobuf(obj)
    override def parse(byteStream: InputStream): String = readBalanceRequestProtobuf(byteStream)
  }

  private val protobufResponseMarshaller = new MethodDescriptor.Marshaller[Long] {
    override def stream(obj: Long): InputStream       = writeBalanceResponseProtobuf(obj)
    override def parse(byteStream: InputStream): Long = readBalanceResponseProtobuf(byteStream)
  }

  def balancesMethodDescriptor: MethodDescriptor[String, Long] = MethodDescriptor
    .newBuilder()
    .setType(MethodDescriptor.MethodType.UNARY)
    // Method name with the namespace prefix
    .setFullMethodName(s"${grpc.ServiceName}/${sdk.api.BalancesApi.MethodName}")
    // Encoder/decoder for input and output types
    .setRequestMarshaller(protobufRequestMarshaller)
    .setResponseMarshaller(protobufResponseMarshaller)
    .build()

  def balancesServerCallHandler(getBalance: String => Long): ServerCallHandler[String, Long] =
    new ServerCallHandler[String, Long] {
      override def startCall(
        call: ServerCall[String, Long],
        headers: Metadata,
      ): ServerCall.Listener[String] = {
        // Number of messages to read next from the response (default is no read at all)
        call.request(1)

        // Server listener of clients requests
        new ServerCall.Listener[String] {
          // Handle client request message and optionally respond
          override def onMessage(wallet: String): Unit = {
            val balance = getBalance(wallet)
            // Sends headers
            call.sendHeaders(headers)
            // Sends the message
            call.sendMessage(balance)
            // Close must be called, but only once to prevent exception
            call.close(Status.OK, headers)
          }

          override def onHalfClose(): Unit = ()

          override def onCancel(): Unit = ()

          override def onComplete(): Unit = ()
        }
      }
    }
}
