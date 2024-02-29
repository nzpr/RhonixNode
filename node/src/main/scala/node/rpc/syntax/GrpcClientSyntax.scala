package node.rpc.syntax

import cats.syntax.all.*
import cats.{Eval, Monad}
import io.grpc.MethodDescriptor
import node.rpc.{GrpcChannelsManager, GrpcClient, GrpcMethod}
import sdk.codecs.Serialize

import java.net.{InetSocketAddress, SocketAddress}

trait GrpcClientSyntax {
  implicit def grpcClientSyntax[F[_]](client: GrpcClient[F]): GrpcClientOps[F] = new GrpcClientOps[F](client)
}

final class GrpcClientOps[F[_]](private val client: GrpcClient[F]) extends AnyVal {

  /** Call specifying endpoint with host and port. */
  def callMethod[Req, Resp](method: MethodDescriptor[Req, Resp], msg: Req, socketAddress: SocketAddress)(implicit
    channelsManager: GrpcChannelsManager[F],
    monadF: Monad[F],
  ): F[Resp] = channelsManager.get(socketAddress).flatMap(client.call(method, msg, _))

  def callEndpoint[Req, Resp](endpoint: String, msg: Req, socketAddress: SocketAddress)(implicit
    F: Monad[F],
    gcm: GrpcChannelsManager[F],
    sA: Serialize[Eval, Req],
    sB: Serialize[Eval, Resp],
  ): F[Resp] = callMethod[Req, Resp](GrpcMethod[Req, Resp](endpoint), msg, socketAddress)
}
