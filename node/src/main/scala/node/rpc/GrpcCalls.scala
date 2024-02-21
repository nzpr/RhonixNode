package node.rpc

import cats.effect.kernel.Async
import cats.syntax.all.*
import node.comm.CommImpl.{blockHashExchangeProtocol, BlockHash, BlockHashResponse}
import node.rpc.syntax.all.*
import sdk.comm.Peer

object GrpcCalls {

  /**
   * Send block through grpc channel.
   */
  def sendBlock[F[_]: Async: GrpcChannelsManager](
    block: BlockHash,
    to: Peer,
  ): F[BlockHashResponse] = {
    // For client calls it does not matter what is the message processing function.
    val apiDef = blockHashExchangeProtocol(_ => BlockHashResponse().pure[F])

    GrpcClient[F].callHost[BlockHash, BlockHashResponse](GrpcMethod(apiDef), block, to.host, to.port)
  }
}
