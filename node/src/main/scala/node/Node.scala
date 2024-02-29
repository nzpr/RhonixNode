package node

import cats.Parallel
import cats.effect.std.Console
import cats.effect.{Async, Sync}
import cats.syntax.all.*
import fs2.Stream
import sdk.crypto.ECDSA
import sdk.data.{BalancesDeploy, BalancesState}
import sdk.diag.Metrics
import sdk.primitive.ByteArray
import secp256k1.Secp256k1
import weaver.data.FinalData

import scala.concurrent.duration.DurationInt

object Node {

  def apply[F[_]: Async: Parallel: Console](
    id: ByteArray,
    isBootstrap: Boolean,
    setup: Setup[F],
    genesisPoS: FinalData[ByteArray],
    genesisBalances: BalancesState,
  ): fs2.Stream[F, Unit] = {
    val printDiag: fs2.Stream[F, List[NodeSnapshot[ByteArray, ByteArray, BalancesDeploy]]] = NodeSnapshot
      .stream(setup.stateManager, setup.dProc.finStream)
      .map(List(_))
      .metered(100.milli)
      .printlns

    val mkGenesis: Stream[F, Unit] = {
      implicit val m: Metrics[F] = Metrics.unit
      Stream.eval(
        Genesis
          .mkGenesisBlock[F](id, genesisPoS, genesisBalances, setup.balancesShard)
          .flatMap { genesisM =>
            DbApiImpl(setup.database).saveBlock(genesisM) *>
              setup.ports.sendToInput(genesisM.id) *>
              Sync[F].delay(sdk.log.Logger.console.info(s"Genesis block created with hash ${genesisM.id}"))
          },
      )
    }

    val genesisF =
      if (isBootstrap)
        Stream.eval(Console[F].println(s"Minting genesis")) ++
          mkGenesis ++
          Stream.eval(Console[F].println(s"Genesis complete"))
      else Stream.eval(Console[F].println(s"Bootstrapping"))

    val run: Stream[F, Unit] = setup.dProc.dProcStream

    (genesisF ++ run) concurrently printDiag
  }

  val SupportedECDSA: Map[String, ECDSA] = Map("secp256k1" -> Secp256k1.apply)
  val BalancesShardName: String          = "balances"

}
