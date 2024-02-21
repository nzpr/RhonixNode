package node.rpc

import cats.effect.{Async, Sync}
import io.grpc.*
import sdk.log.Logger

import scala.concurrent.Promise

trait GrpcClient[F[_]] {

  /**
   * Requirements to make an rpc call:
   * - method to call,
   * - request message,
   * - channel to send the message.
   * To have another more handy API please create/use syntax.
   * */
  def call[Req, Resp](method: MethodDescriptor[Req, Resp], msg: Req, channel: Channel): F[Resp]
}

object GrpcClient {
  def apply[F[_]: Async]: GrpcClient[F] =
    new GrpcClient[F] {
      override def call[Req, Resp](method: MethodDescriptor[Req, Resp], msg: Req, channel: Channel): F[Resp] = {

        val futureF = Sync[F].delay {
          val call = channel.newCall[Req, Resp](method, CallOptions.DEFAULT)

          val promise = Promise[Resp]()

          val callListener = new ClientCall.Listener[Resp] {
            override def onHeaders(headers: Metadata): Unit =
              Logger.console.info(s"CLIENT_ON_HEADERS: $headers")

            override def onMessage(message: Resp): Unit = {
              Logger.console.info(s"CLIENT_ON_MESSAGE: $message")
              val _ = promise.success(message)
              super.onMessage(message)
            }

            override def onClose(status: Status, trailers: Metadata): Unit =
              Logger.console.info(s"CLIENT_ON_CLOSE: $status, $trailers")
          }

          call.start(callListener, new Metadata())

          call.sendMessage(msg)
          Logger.console.info(s"CLIENT_SENT_MSG: $msg")

          call.request(1)

          call.halfClose()
          Logger.console.info(s"CLIENT_HALF_CLOSED")

          promise.future
        }

        Async[F].fromFuture(futureF)
      }
    }
}
