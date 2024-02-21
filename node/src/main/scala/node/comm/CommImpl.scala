package node.comm

import cats.Eval
import cats.effect.Sync
import sdk.comm.CommProtocol
import sdk.primitive.ByteArray

import java.io.InputStream

object CommImpl {

  trait CommMessage

  // Send message to remote peer
  final case class BlockHash(msg: ByteArray)               extends CommMessage
  final case class BlockHashResponse(rcvd: Boolean = true) extends CommMessage

  /** Protocol for blocks exchange. TODO move to sdk */
  def blockHashExchangeProtocol[F[_]: Sync](
    f: BlockHash => F[BlockHashResponse],
  ): CommProtocol[F, BlockHash, BlockHashResponse] =
    CommProtocol[F, BlockHash, BlockHashResponse](
      s"${sdk.api.RootPathString}/CommService",
      (obj: BlockHash) => Serialize.encode[BlockHash](obj, (obj, writer) => writer.write(obj.msg.bytes)),
      (bs: InputStream) => Serialize.decode[BlockHash](bs, r => r.readBytes.map(bytes => BlockHash(ByteArray(bytes)))),
      f,
      (obj: BlockHashResponse) => Serialize.encode[BlockHashResponse](obj, (obj, writer) => writer.write(obj.rcvd)),
      (bs: InputStream) => Serialize.decode[BlockHashResponse](bs, r => r.readBool.map(BlockHashResponse)),
    )
}
