package node.rpc

import cats.effect.{Async, Ref, Resource}
import cats.syntax.all.*
import io.grpc.*
import io.grpc.netty.NettyChannelBuilder

trait GrpcChannelsManager[F[_]] {
  def get(serverHost: String, serverPort: Int): F[ManagedChannel]
}

object GrpcChannelsManager {
  def apply[F[_]: Async]: Resource[F, GrpcChannelsManager[F]] = Resource
    .make(Ref.of(Map.empty[(String, Int), ManagedChannel]))(_.get.map(_.values.foreach(_.shutdown())))
    .map { channelsRef =>
      new GrpcChannelsManager[F] {
        def get(serverHost: String, serverPort: Int): F[ManagedChannel] = channelsRef.modify { st =>
          st.get(serverHost, serverPort) match {
            case Some(channel) => (st, channel)
            case None          =>
              val newChannel = NettyChannelBuilder.forAddress(serverHost, serverPort).usePlaintext().build
              val newSt      = st + ((serverHost, serverPort) -> newChannel)
              newSt -> newChannel
          }
        }
      }
    }
}
