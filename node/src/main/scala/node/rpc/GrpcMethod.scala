package node.rpc

import io.grpc.MethodDescriptor
import sdk.comm.CommProtocol

import java.io.InputStream

/**
 * Constructor for Grpc method descriptor.
 * Used both for server and client.
 * */
object GrpcMethod {
  def apply[F[_], A, B](definition: CommProtocol[F, A, B]): MethodDescriptor[A, B] =
    MethodDescriptor
      .newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(s"${definition.endpointName}")
      .setRequestMarshaller(
        new MethodDescriptor.Marshaller[A] {
          override def stream(obj: A): InputStream       = definition.streamA(obj)
          override def parse(byteStream: InputStream): A = definition.parseA(byteStream)
        },
      )
      .setResponseMarshaller(new MethodDescriptor.Marshaller[B] {
        override def stream(obj: B): InputStream       = definition.streamB(obj)
        override def parse(byteStream: InputStream): B = definition.parseB(byteStream)
      })
      .build()
}
