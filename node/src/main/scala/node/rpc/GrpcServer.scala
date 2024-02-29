package node.rpc

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import dproc.data.Block
import io.grpc.*
import io.grpc.netty.NettyServerBuilder
import node.Serialization.*
import sdk.api.{BlockEndpoint, BlockHashEndpoint}
import sdk.data.BalancesDeploy
import sdk.log.Logger
import sdk.primitive.ByteArray

import java.net.SocketAddress

object GrpcServer {
  def apply[F[_]: Async](
    port: Int,
    hashRcvF: (ByteArray, SocketAddress) => F[Boolean],
    blockResolve: (ByteArray, SocketAddress) => F[Option[Block[ByteArray, ByteArray, BalancesDeploy]]],
  ): Resource[F, Server] =
    Dispatcher.sequential[F].flatMap { dispatcher =>
      val serviceDefinition: ServerServiceDefinition = ServerServiceDefinition
        .builder(sdk.api.RootPathString)
        .addMethod(
          GrpcMethod[ByteArray, Boolean](BlockHashEndpoint),
          mkMethodHandler(hashRcvF, dispatcher),
        )
        .addMethod(
          GrpcMethod[ByteArray, Option[Block[ByteArray, ByteArray, BalancesDeploy]]](BlockEndpoint),
          mkMethodHandler(blockResolve, dispatcher),
        )
        .build()

      Resource.make(
        Sync[F].delay {
          NettyServerBuilder
            .forPort(port)
            .addService(serviceDefinition)
            .build
            .start()
        },
      )(server => Sync[F].delay(server.shutdown()).void)
    }

  private def mkMethodHandler[F[_], Req, Resp](
    callback: (Req, SocketAddress) => F[Resp], // request and remote address
    dispatcher: Dispatcher[F],
  ): ServerCallHandler[Req, Resp] = new ServerCallHandler[Req, Resp] {
    override def startCall(
      call: ServerCall[Req, Resp],
      headers: Metadata,
    ): ServerCall.Listener[Req] = {
      Logger.console.info(s"SERVER_START_CALL: ${call.getMethodDescriptor}")
      val remoteAddr = call.getAttributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR)
      Logger.console.info(s"CALLER ADDR: $remoteAddr")

      Logger.console.info(s"SERVER_START_CALL: $headers")

      // Number of messages to read next from the response (default is no read at all)
      call.request(1)

      new ServerCall.Listener[Req] {
        override def onMessage(message: Req): Unit = {
          Logger.console.info(s"SERVER_ON_MESSAGE: $message")
          call.sendHeaders(headers)
          val result = dispatcher.unsafeRunSync(callback(message, remoteAddr))
          call.sendMessage(result)
          Logger.console.info(s"SERVER_SENT_RESPOND_MSG")
          call.close(Status.OK, headers)
        }

        override def onHalfClose(): Unit = Logger.console.info(s"SERVER_ON_HALF_CLOSE")

        override def onCancel(): Unit = Logger.console.info(s"SERVER_ON_CANCEL")

        override def onComplete(): Unit = Logger.console.info(s"SERVER_ON_COMPLETE")
      }
    }
  }
}
