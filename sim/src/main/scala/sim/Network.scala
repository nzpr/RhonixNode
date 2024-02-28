package sim

import cats.Parallel
import cats.effect.Sync
import cats.effect.kernel.{Async, Temporal}
import cats.effect.std.Console
import cats.syntax.all.*
import dproc.data.Block
import fs2.{Pipe, Stream}
import node.comm.CommImpl
import node.comm.CommImpl.BlockHash
import node.{DbApiImpl, Genesis, NodeSnapshot, Setup}
import sdk.data.{BalancesDeploy, BalancesState}
import sdk.diag.Metrics
import sdk.primitive.ByteArray
import weaver.data.FinalData

import scala.concurrent.duration.{Duration, DurationInt}

object Network {

  def apply[F[_]: Async: Parallel: Console](
    peers: List[Setup[F]],
    genesisPosState: FinalData[ByteArray],
    genesisBalancesState: BalancesState,
    netCfg: sim.Config,
  ): fs2.Stream[F, Unit] = {

    def broadcast(peers: List[Setup[F]], delay: Duration): Pipe[F, ByteArray, Unit] =
      _.evalMap(m => Temporal[F].sleep(delay) *> peers.traverse_(_.ports.sendToInput(BlockHash(m)).void))

    val peersWithIdx = peers.zipWithIndex

    val streams = peersWithIdx.map { case setup -> idx =>
      val p2pStream = {
        val notSelf = peersWithIdx.filterNot(_._2 == idx).map(_._1)

        val selfDbApiImpl  = DbApiImpl(setup.database)
        val peersDbApiImpl = peers.map(peerSetup => DbApiImpl(peerSetup.database))

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
      val streams = peers.map { p =>
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
      val genesisCreator: Setup[F] = peers.head
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
              genesisCreator.ports.sendToInput(CommImpl.BlockHash(genesisM.id)) *>
              Sync[F].delay(println(s"Genesis block created"))
          },
      )
    }

    simStream concurrently logDiag concurrently mkGenesis
  }
}
