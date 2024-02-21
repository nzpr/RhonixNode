package node.rpc

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import io.grpc.*
import io.grpc.netty.NettyServerBuilder
import node.comm.CommImpl.{BlockHash, BlockHashResponse}
import sdk.comm.CommProtocol
import sdk.log.Logger

object GrpcServer {
  def apply[F[_]: Async](
    port: Int,
    blockExchangeProtocol: CommProtocol[F, BlockHash, BlockHashResponse],
  ): Resource[F, Server] =
    Dispatcher.sequential[F].flatMap { dispatcher =>
      val serviceDefinition: ServerServiceDefinition = ServerServiceDefinition
        .builder(sdk.api.RootPathString)
        .addMethod(
          GrpcMethod(blockExchangeProtocol),
          mkMethodHandler(blockExchangeProtocol, dispatcher),
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
    definition: CommProtocol[F, Req, Resp],
    dispatcher: Dispatcher[F],
  ): ServerCallHandler[Req, Resp] = new ServerCallHandler[Req, Resp] {
    override def startCall(
      call: ServerCall[Req, Resp],
      headers: Metadata,
    ): ServerCall.Listener[Req] = {
      Logger.console.info(s"SERVER_START_CALL: ${call.getMethodDescriptor}")
      Logger.console.info(s"SERVER_START_CALL: $headers")

      // Number of messages to read next from the response (default is no read at all)
      call.request(1)

      new ServerCall.Listener[Req] {
        override def onMessage(message: Req): Unit = {
          Logger.console.info(s"SERVER_ON_MESSAGE: $message")
          call.sendHeaders(headers)
          val result = dispatcher.unsafeRunSync(definition.callback(message))
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
