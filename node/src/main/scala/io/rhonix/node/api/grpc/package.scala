package io.rhonix.node.api

import cats.effect.Resource
import cats.effect.kernel.{Async, Sync}
import cats.effect.std.Dispatcher
import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import sdk.api.FindApi
import sdk.syntax.all.apiFindSyntax

package object grpc {
  val ServiceName: String = sdk.api.Path.mkString(".")

  def server[F[_]: Async](port: Int, balanceApi: FindApi[F, String, Long]): Resource[F, Server] =
    Dispatcher.sequential[F].flatMap { dispatcher =>
      Resource.make {
        // Bind listener to the network adapter accepting API schema (service definition)
        Sync[F].delay {
          NettyServerBuilder
            .forPort(port)
            .addService(methods.All(ServiceName, (x: String) => dispatcher.unsafeRunSync(balanceApi.get(x))))
            .build
            .start()
        }
      }(server => Sync[F].delay { val _ = server.shutdown() })
    }
}
