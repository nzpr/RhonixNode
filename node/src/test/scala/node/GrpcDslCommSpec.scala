package node

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import node.rpc.syntax.all.grpcClientSyntax
import node.rpc.{GrpcChannelsManager, GrpcClient, GrpcServer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sdk.api.BlockHashEndpoint
import sdk.primitive.ByteArray

class GrpcDslCommSpec extends AnyFlatSpec with Matchers {

  "Grpc server" should "correctly handle all comm protocol defined." in {
    val serverPort = 4321
    val serverHost = "localhost"

    val srcMessage       = ByteArray(Array[Byte](1))
    val expectedResponse = false // false since true is default

    val grpcServer = GrpcServer.apply[IO](serverPort, _ => expectedResponse.pure[IO])

    val grpcCall = GrpcChannelsManager[IO].use { implicit ch =>
      import Serialization.*
      GrpcClient[IO].callEndpoint[ByteArray, Boolean](BlockHashEndpoint, srcMessage, serverHost, serverPort)
    }

    grpcServer.use(_ => grpcCall.map(resp => resp shouldBe expectedResponse)).unsafeRunSync()
  }
}
