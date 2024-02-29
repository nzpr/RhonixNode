package sim

import cats.Parallel
import cats.effect.Sync
import cats.effect.kernel.{Async, Temporal}
import cats.effect.std.Console
import cats.syntax.all.*
import dproc.data.Block
import fs2.{Pipe, Stream}
import node.{DbApiImpl, Genesis, NodeSnapshot, Setup}
import sdk.comm.Peer
import sdk.data.{BalancesDeploy, BalancesState}
import sdk.diag.Metrics
import sdk.primitive.ByteArray
import weaver.data.FinalData

import scala.concurrent.duration.{Duration, DurationInt}

object Network {

  def apply[F[_]: Async: Parallel: Console](
    peers: Map[Peer, Setup[F]],
    genesisPosState: FinalData[ByteArray],
    genesisBalancesState: BalancesState,
    netCfg: sim.Config,
  ): fs2.Stream[F, Unit] = {

    def broadcast(peers: List[Setup[F]], delay: Duration): Pipe[F, ByteArray, Unit] =
      _.evalMap(m => Temporal[F].sleep(delay) *> peers.traverse_(_.ports.sendToInput(m).void))

    val peersWithIdx = peers.zipWithIndex

    val streams = peersWithIdx.toList.map { case (_, setup) -> idx =>
      val p2pStream = {
        val notSelf = peersWithIdx.filterNot(_._2 == idx).map(_._1._2).toList

        val selfDbApiImpl  = DbApiImpl(setup.database)
        val peersDbApiImpl = peers.toList.map { case (_, peerSetup) => DbApiImpl(peerSetup.database) }

        // TODO this is a hack to make block appear in peers databases
        def copyBlockToPeers(hash: ByteArray): F[Unit] = for {
          bOpt   <- selfDbApiImpl.readBlock(hash)
          b      <- bOpt.liftTo[F](new Exception(s"Block not found for hash $hash"))
          bWithId = Block.WithId[ByteArray, ByteArray, BalancesDeploy](hash, b)
          _      <- peersDbApiImpl.parTraverse { peerDbImpl =>
                      peerDbImpl
                        .isBlockExist(hash)
                        .ifM(
                          ifFalse = peerDbImpl.saveBlock(bWithId),
                          ifTrue = ().pure,
                        )
                    }
        } yield ()

        setup.dProc.output
          .evalTap(copyBlockToPeers)
          .through(
            broadcast(notSelf, netCfg.propDelay)
              // add execution delay
              .compose(x => Stream.eval(Async[F].sleep(netCfg.exeDelay).replicateA(netCfg.txPerBlock)) *> x),
          )
      }

      val nodeStream = setup.dProc.dProcStream concurrently setup.ports.inHash

      nodeStream concurrently p2pStream
    }

    val logDiag: Stream[F, Unit] = {
      val streams = peers.toList.map { case (_, p) =>
        NodeSnapshot
          .stream(p.stateManager, p.dProc.finStream)
          .map(List(_))
          .metered(100.milli)
      }

      streams.tail
        .foldLeft(streams.head) { case (acc, r) => acc.parZipWith(r) { case (acc, x) => x ++ acc } }
        .map(_.zipWithIndex)
        .map(NetworkSnapshot(_))
        .map(_.show)
        .printlns
    }

    val simStream: Stream[F, Unit] = Stream.emits(streams).parJoin(streams.size)

    val mkGenesis: Stream[F, Unit] = {
      val genesisCreator: Setup[F] = peers.head._2
      implicit val m: Metrics[F]   = Metrics.unit
      Stream.eval(
        Genesis
          .mkGenesisBlock[F](
            genesisPosState.bonds.bonds.head._1,
            genesisPosState,
            genesisBalancesState,
            genesisCreator.balancesShard,
          )
          .flatMap { genesisM =>
            DbApiImpl(genesisCreator.database).saveBlock(genesisM) *>
              genesisCreator.ports.sendToInput(genesisM.id) *>
              Sync[F].delay(println(s"Genesis block created"))
          },
      )
    }

    simStream concurrently logDiag concurrently mkGenesis
  }
}
