package node

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import io.grpc.netty.NettyChannelBuilder
import node.comm.CommImpl
import node.comm.CommImpl.{BlockHash, BlockHashResponse}
import node.rpc.{GrpcClient, GrpcMethod, GrpcServer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sdk.primitive.ByteArray

class GrpcDslCommSpec extends AnyFlatSpec with Matchers {

  "Grpc server" should "correctly handle all comm protocol defined." in {
    val serverPort = 4321

    val srcMessage       = BlockHash(ByteArray(Array[Byte](1)))
    val expectedResponse = BlockHashResponse(false) // false since true is default
    val protocol         = CommImpl.blockHashExchangeProtocol(_ => expectedResponse.pure[IO])

    val grpcServer    = GrpcServer.apply[IO](serverPort, protocol)
    val clientChannel = NettyChannelBuilder.forAddress("localhost", serverPort).usePlaintext().build
    val grpcCall      = GrpcClient[IO].call(GrpcMethod(protocol), srcMessage, clientChannel)

    grpcServer.use(_ => grpcCall.map(resp => resp shouldBe expectedResponse)).unsafeRunSync()
  }
}
