package node.rpc.syntax

import cats.Monad
import cats.syntax.all.*
import io.grpc.MethodDescriptor
import node.rpc.{GrpcChannelsManager, GrpcClient}

trait GrpcClientSyntax {
  implicit def grpcClientSyntax[F[_]](client: GrpcClient[F]): GrpcClientOps[F] = new GrpcClientOps[F](client)
}

final class GrpcClientOps[F[_]](private val client: GrpcClient[F]) extends AnyVal {

  /** Call specifying endpoint with host and port. */
  def callHost[Req, Resp](method: MethodDescriptor[Req, Resp], msg: Req, host: String, port: Int)(implicit
    channelsManager: GrpcChannelsManager[F],
    monadF: Monad[F],
  ): F[Resp] = channelsManager.get(host, port).flatMap(client.call(method, msg, _))
}
