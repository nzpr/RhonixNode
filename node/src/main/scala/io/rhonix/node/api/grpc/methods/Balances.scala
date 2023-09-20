package io.rhonix.node.api.grpc.methods

import io.grpc.*
import io.rhonix.node.api.grpc.data.*
import io.rhonix.node.api.grpc.codecs.protobuf.*
import rhonix.api.prefix

import java.io.InputStream

object Balances {

  private val methodName = "Balances"

  private val protobufRequestMarshaller = new MethodDescriptor.Marshaller[BalanceRequest] {
    override def stream(obj: BalanceRequest): InputStream       = writeBalanceRequestProtobuf(obj)
    override def parse(byteStream: InputStream): BalanceRequest = readBalanceRequestProtobuf(byteStream)
  }

  private val protobufResponseMarshaller = new MethodDescriptor.Marshaller[BalanceResponse] {
    override def stream(obj: BalanceResponse): InputStream       = writeBalanceResponseProtobuf(obj)
    override def parse(byteStream: InputStream): BalanceResponse = readBalanceResponseProtobuf(byteStream)
  }

  def balancesMethodDescriptor: MethodDescriptor[BalanceRequest, BalanceResponse] = MethodDescriptor
    .newBuilder()
    .setType(MethodDescriptor.MethodType.UNARY)
    // Method name with the namespace prefix
    .setFullMethodName(s"$prefix/$methodName")
    // Encoder/decoder for input and output types
    .setRequestMarshaller(protobufRequestMarshaller)
    .setResponseMarshaller(protobufResponseMarshaller)
    .build()

  def balancesServerCallHandler(getBalance: String => Long): ServerCallHandler[BalanceRequest, BalanceResponse] =
    new ServerCallHandler[BalanceRequest, BalanceResponse] {
      override def startCall(
        call: ServerCall[BalanceRequest, BalanceResponse],
        headers: Metadata,
      ): ServerCall.Listener[BalanceRequest] = {
        // Number of messages to read next from the response (default is no read at all)
        call.request(1)

        // Server listener of clients requests
        new ServerCall.Listener[BalanceRequest] {
          // Handle client request message and optionally respond
          override def onMessage(message: BalanceRequest): Unit = {
            val balance = getBalance(message.wallet)
            // Sends headers
            call.sendHeaders(headers)
            // Sends the message
            call.sendMessage(BalanceResponse(balance))
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
