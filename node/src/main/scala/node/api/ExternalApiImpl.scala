package node.api

import cats.data.ValidatedNel
import cats.effect.kernel.Async
import cats.syntax.all.*
import node.Hashing.*
import node.api.web.Validation
import sdk.api
import sdk.api.data.*
import sdk.api.{ApiErr, ExternalApi}
import sdk.codecs.Digest
import sdk.data.BalancesState
import sdk.history.ByteArray32
import sdk.primitive.ByteArray
import sdk.syntax.all.*

object ExternalApiImpl {
  def apply[F[_]: Async](
    getBlock: ByteArray => F[Option[Block]],
    readTx: ByteArray => F[Option[BalancesState]],
    readBalance: (ByteArray32, ByteArray) => F[Option[Balance]],
    latestBlocks: F[Set[ByteArray]],
  ): ExternalApi[F] = new ExternalApi[F] {
    override def getBlockByHash(hash: Array[Byte]): F[Option[api.data.Block]] = getBlock(ByteArray(hash))

    override def getDeployByHash(hash: Array[Byte]): F[Option[Deploy]] =
      readTx(ByteArray(hash)).map(_.map { x => // TODO read deploy?
        val program = s"${x.diffs.map { case k -> v => k.toHex -> v }}"
        Deploy(Array.empty[Byte], Array.empty[Byte], "root", program, 0L, 0L, 0L, 0L, 0L)
      })

    override def getDeploysByHash(hash: Array[Byte]): F[Option[Seq[Array[Byte]]]]     = ???
    override def getBalance(state: Array[Byte], wallet: Array[Byte]): F[Option[Long]] = {
      val blakeH = ByteArray32.convert(state)
      val longW  = ByteArray(wallet)
      blakeH.traverse(readBalance(_, longW).map(_.map(_.x))).flatMap(_.liftTo[F])
    }

    override def getLatestMessages: F[List[Array[Byte]]] = latestBlocks.map(_.toList.map(_.bytes))
    override def status: F[Status]                       = Status("0.1.1").pure

    override def transferToken(tx: TokenTransferRequest): F[ValidatedNel[ApiErr, Unit]] = ???
//      Validation
//        .validateTokenTransferRequest(tx)(implicitly[Digest[TokenTransferRequest.Body]])
//        .traverse { _ =>
//          deployPool.update(
//            _.updated(
//              ByteArray(tx.digest),
//              BalancesState(
//                Map(
//                  ByteArray(tx.body.from) -> -tx.body.value,
//                  ByteArray(tx.body.to)   -> tx.body.value,
//                ),
//              ),
//            ),
//          )
//        }
  }
}
