package sim

import cats.Parallel
import cats.effect.*
import cats.effect.kernel.{Async, Temporal}
import cats.effect.std.{Console, Random}
import cats.syntax.all.*
import diagnostics.KamonContextStore
import diagnostics.metrics.{Config as InfluxDbConfig, InfluxDbBatchedMetrics}
import dproc.data.Block
import fs2.concurrent.SignallingRef
import fs2.{Pipe, Stream}
import io.circe.Encoder
import node.api.web
import node.api.web.PublicApiJson
import node.api.web.https4s.RouterFix
import node.hashing.Blake2b
import node.lmdb.LmdbStoreManager
import node.{Config as NodeConfig, Node}
import org.http4s.EntityEncoder
import pureconfig.generic.ProductHint
import sdk.codecs.Base16
import sdk.diag.{Metrics, SystemReporter}
import sdk.history.ByteArray32
import sdk.history.History.EmptyRootHash
import sdk.history.instances.RadixHistory
import sdk.reflect.ClassesAsConfig
import sdk.store.*
import sdk.syntax.all.*
import sim.Config as SimConfig
import sim.NetworkSnapshot.{reportSnapshot, NodeSnapshot}
import sim.balances.*
import sim.balances.MergeLogicForPayments.mergeRejectNegativeOverflow
import sim.balances.data.BalancesState.Default
import sim.balances.data.{BalancesDeploy, BalancesState}
import weaver.WeaverState
import weaver.data.*

import java.nio.file.Files
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try

object NetworkSim extends IOApp {

  implicit def blake2b256Hash(x: Array[Byte]): ByteArray32 = ByteArray32.convert(Blake2b.hash256(x)).getUnsafe

  // Dummy types for message id, sender id and transaction
  type M = String
  type S = String
  type T = BalancesDeploy
  implicit val ordS = new Ordering[String] {
    override def compare(x: S, y: S): Int = x compareTo y
  }

  final private case class Config(
    sim: SimConfig,
    node: NodeConfig,
    influxDb: InfluxDbConfig,
  )

  final case class NetNode[F[_]](
    id: S,
    node: Node[F, M, S, T],
    balanceApi: (ByteArray32, Wallet) => F[Option[Long]],
    getData: F[NodeSnapshot[M, S, T]],
  )

  def genesisBlock[F[_]: Async: Parallel: Metrics](
    sender: S,
    genesisExec: FinalData[S],
    users: Set[Int],
  ): F[Block.WithId[M, S, T]] = {
    val mkHistory     = sdk.history.History.create(EmptyRootHash, new InMemoryKeyValueStore[F])
    val mkValuesStore = Sync[F].delay {
      new ByteArrayKeyValueTypedStore[F, ByteArray32, Balance](
        new InMemoryKeyValueStore[F],
        ByteArray32.codec,
        balanceCodec,
      )
    }

    (mkHistory, mkValuesStore).flatMapN { case history -> valueStore =>
      val genesisState  = new BalancesState(users.map(_ -> Long.MaxValue / 2).toMap)
      val genesisDeploy = BalancesDeploy("genesis", genesisState)
      BalancesStateBuilderWithReader(history, valueStore)
        .buildState(
          baseState = EmptyRootHash,
          toFinalize = Default,
          toMerge = genesisState,
        )
        .map { case _ -> postState =>
          Block.WithId(
            s"genesis",
            Block[M, S, T](
              sender,
              Set(),
              Set(),
              txs = List(genesisDeploy),
              Set(),
              None,
              Set(),
              genesisExec.bonds,
              genesisExec.lazinessTolerance,
              genesisExec.expirationThreshold,
              finalStateHash = EmptyRootHash.bytes.bytes,
              postStateHash = postState.bytes.bytes,
            ),
          )
        }
    }
  }

  def sim[F[_]: Async: Parallel: Random: Console: KamonContextStore](
    netCfg: SimConfig,
    nodeCfg: NodeConfig,
    ifxDbCfg: InfluxDbConfig,
  ): Stream[F, Unit] = {

    /// Users (wallets) making transactions
    val users: Set[Wallet] = (1 to netCfg.usersNum).toSet

    /// Genesis data
    val lazinessTolerance = 1 // c.lazinessTolerance
    val senders           = Iterator.range(0, netCfg.size).map(n => s"s$n").toList
    // Create lfs message, it has no parents, sees no offences and final fringe is empty set
    val genesisBonds      = Bonds(senders.map(_ -> 100L).toMap)
    val genesisExec       = FinalData(genesisBonds, lazinessTolerance, 10000)
    val lfs               = MessageData[M, S]("s0", Set(), Set(), FringeData(Set()), genesisExec)

    /// Shared block store across simulation
    // TODO replace with pgSql
    val blockStore: Ref[F, Map[M, Block[M, S, T]]] = Ref.unsafe(Map.empty[M, Block[M, S, T]])

    def saveBlock(b: Block.WithId[M, S, T]): F[Unit] = blockStore.update(_.updated(b.id, b.m))

    def readBlock(id: M): F[Block[M, S, T]] = blockStore.get.map(_.getUnsafe(id))

    // Shared transactions store
    val txStore: Ref[F, Map[String, BalancesState]]  = Ref.unsafe(Map.empty[String, BalancesState])
    def saveTx(tx: BalancesDeploy): F[Unit]          = txStore.update(_.updated(tx.id, tx.state))
    def readTx(id: String): F[Option[BalancesState]] = txStore.get.map(_.get(id))

    def broadcast(
      peers: List[Node[F, M, S, T]],
      time: Duration,
    ): Pipe[F, M, Unit] = _.evalMap(m => Temporal[F].sleep(time) *> peers.traverse(_.dProc.acceptMsg(m)).void)

    def random(users: Set[Wallet]): F[BalancesState] = for {
      txVal <- Random[F].nextLongBounded(100)
      from  <- Random[F].elementOf(users)
      to    <- Random[F].elementOf(users - from)
    } yield new BalancesState(Map(from -> -txVal, to -> txVal))

    /** Storage resource for on chain storage (history and values) */
    def onChainStoreResource(
      kvStoreManager: KeyValueStoreManager[F],
    ): Resource[F, (RadixHistory[F], KeyValueTypedStore[F, ByteArray32, Balance])] = kvStoreManager.asResource
      .flatMap { kvStoreManager =>
        Resource.eval {
          for {
            historyStore <- kvStoreManager.store("history")
            valuesStore  <- kvStoreManager.store("data")
            history      <- sdk.history.History.create(EmptyRootHash, historyStore)
            values        = valuesStore.toByteArrayTypedStore[ByteArray32, Balance](ByteArray32.codec, balanceCodec)
          } yield history -> values
        }
      }

    def mkNode(vId: S): Resource[F, NetNode[F]] = {
      val dataPath     = Files.createTempDirectory(s"gorki-sim-node-$vId")
      val storeManager =
        if (nodeCfg.persistOnChainState) LmdbStoreManager(dataPath)
        else Sync[F].delay(InMemoryKeyValueStoreManager[F]())
      val metrics      =
        if (nodeCfg.enableInfluxDb) InfluxDbBatchedMetrics[F](ifxDbCfg, vId)
        else Resource.eval(Metrics.unit.pure[F])

      (Resource.eval(storeManager).flatMap(onChainStoreResource), metrics).flatMapN {
        case ((history, valueStore), metrics) =>
          implicit val x: Metrics[F] = metrics

          val blockSeqNumRef = Ref.unsafe(0)
          val assignBlockId  = (_: Any) => blockSeqNumRef.updateAndGet(_ + 1).map(idx => s"$vId-$idx")

          val txSeqNumRef = Ref.unsafe(0)
          val nextTxs     = txSeqNumRef
            .updateAndGet(_ + 1)
            .flatMap(idx => random(users).map(st => balances.data.BalancesDeploy(s"$vId-tx-$idx", st)))
            .replicateA(netCfg.txPerBlock)
            .flatTap(_.traverse(saveTx))
            .map(_.toSet)

          val balancesEngine   = BalancesStateBuilderWithReader(history, valueStore)
          val fringeMappingRef = Ref.unsafe(Map(Set.empty[M] -> EmptyRootHash))

          def buildState(
            baseFringe: Set[M],
            finalFringe: Set[M],
            toFinalize: Set[T],
            toMerge: Set[T],
            toExecute: Set[T],
          ): F[((Array[Byte], Seq[T]), (Array[Byte], Seq[T]))] =
            for {
              baseState <- fringeMappingRef.get.map(_(baseFringe))
              r         <- mergeRejectNegativeOverflow(balancesEngine, baseState, toFinalize, toMerge ++ toExecute)
              _         <- Async[F].sleep(netCfg.exeDelay).replicateA(toExecute.size)

              ((newFinState, finRj), (newMergeState, provRj)) = r

              r <- balancesEngine.buildState(baseState, newFinState, newMergeState)

              (finalHash, postHash) = r

              _ <- fringeMappingRef.update(_ + (finalFringe -> finalHash))
            } yield ((finalHash.bytes.bytes, finRj), (postHash.bytes.bytes, provRj))

          val netNode = Node[F, M, S, T](
            vId,
            WeaverState.empty[M, S, T](lfs.state),
            assignBlockId,
            nextTxs,
            buildState,
            saveBlock,
            readBlock,
          ).map { node =>
            val tpsRef    = Ref.unsafe[F, Double](0f)
            val tpsUpdate = node.dProc.finStream
              .map(_.accepted.toList)
              .flatMap(Stream.emits(_))
              .throughput(1.second)
              // finality is computed by each sender eventually so / c.size
              .map(_.toDouble / netCfg.size)
              .evalTap(tpsRef.set)

            val getData =
              (
                vId.pure,
                tpsRef.get,
                node.weaverStRef.get,
                node.propStRef.get,
                node.procStRef.get,
                node.bufferStRef.get,
              ).flatMapN { case (id, tps, w, p, pe, b) =>
                val lfsHashF = fringeMappingRef.get.map(
                  _.getUnsafe(
                    w.lazo.fringes.minByOption { case (i, _) => i }.map { case (_, fringe) => fringe }.getOrElse(Set()),
                  ),
                )
                lfsHashF.map(
                  NetworkSnapshot.NodeSnapshot(id, tps.toFloat, tps.toFloat / netCfg.txPerBlock, w, p, pe, b, _),
                )
              }

            val animateDiag = Stream
              .repeatEval(getData)
              .metered(1.second)
              .evalTap { x =>
                implicit val m: Metrics[F] = metrics
                reportSnapshot(x)
              }
              .map(v => s"\u001b[2J${v.show}")
              .printlns

            NetNode(
              vId,
              node.copy(dProc =
                node.dProc
                  .copy(dProcStream =
                    node.dProc.dProcStream concurrently tpsUpdate concurrently
                      SystemReporter[F]() concurrently animateDiag,
                  ),
              ),
              balancesEngine.readBalance(_: ByteArray32, _: Wallet),
              getData,
            )
          }
          Resource.liftK(netNode)
      }
    }

    /** Make the computer, init all peers with lfs. */
    def mkNet(lfs: MessageData[M, S]): Resource[F, List[NetNode[F]]] =
      lfs.state.bonds.activeSet.toList.traverse(mkNode)

    Stream
      .resource(mkNet(lfs))
      .map(_.zipWithIndex)
      .map { net =>
        net.map {
          case NetNode(
                self,
                Node(weaverStRef, _, _, _, dProc),
                getBalance,
                getData,
              ) -> idx =>
            val bootstrap = {
              implicit val m: Metrics[F] = Metrics.unit
              Stream.eval(genesisBlock[F](senders.head, genesisExec, users).flatMap { genesisM =>
                val genesis = genesisM.m.txs.head
                saveBlock(genesisM) *> saveTx(genesis) *> dProc.acceptMsg(genesisM.id) *>
                  Console[F].println(s"Bootstrap done for ${self}")
              })
            }
            val notSelf   = net.collect { case NetNode(id, node, _, _) -> _ if id != self => node }

            val run = dProc.dProcStream concurrently {
              dProc.output.through(broadcast(notSelf, netCfg.propDelay))
            }

            val apiServerStream: Stream[F, ExitCode] = {
              import io.circe.generic.auto.*
              import org.http4s.circe.*

              implicit val c: String => Try[Int]         = (x: String) => Try(x.toInt)
              implicit val d: String => Try[String]      = (x: String) => Try(x)
              implicit val e: String => Try[ByteArray32] =
                (x: String) => Base16.decode(x).flatMap(ByteArray32.convert)
              implicit val encoder: Encoder[Array[Byte]] =
                Encoder[String].imap(s => Base16.decode(s).getUnsafe)(x => Base16.encode(x))

              implicit val a: EntityEncoder[F, Long]         = jsonEncoderOf[F, Long]
              implicit val x: EntityEncoder[F, Int]          = jsonEncoderOf[F, Int]
              implicit val f: EntityEncoder[F, String]       = jsonEncoderOf[F, String]
              implicit val g: EntityEncoder[F, Array[Byte]]  = jsonEncoderOf[F, Array[Byte]]
              implicit val h1: EntityEncoder[F, Set[String]] = jsonEncoderOf[F, Set[String]]

              implicit val h: EntityEncoder[F, Block[String, String, String]] =
                jsonEncoderOf[F, Block[String, String, String]]

              implicit val bs: EntityEncoder[F, BalancesState] = jsonEncoderOf[F, BalancesState]

              def blockByHash(x: M): F[Option[Block[M, S, String]]] =
                blockStore.get
                  .map(_.get(x))
                  .map(
                    _.map { x =>
                      x.copy(
                        merge = x.merge.map(_.id),
                        txs = x.txs.map(_.id),
                        finalized = x.finalized.map { case ConflictResolution(accepted, rejected) =>
                          ConflictResolution(accepted.map(_.id), rejected.map(_.id))
                        },
                      )
                    },
                  )

              def latestBlocks: F[Set[M]] = weaverStRef.get.map(_.lazo.latestMessages)

              val routes = PublicApiJson[F, Block[M, S, String], BalancesState](
                blockByHash(_).flatMap(_.liftTo(new Exception(s"Not Found"))),
                readTx(_).flatMap(_.liftTo(new Exception(s"Not Found"))),
                (h: String, w: String) => {
                  val blakeH = Base16.decode(h).flatMap(ByteArray32.convert)
                  val longW  = Try(w.toInt)
                  (blakeH, longW)
                    .traverseN { case (hash, wallet) =>
                      getBalance(hash, wallet).flatMap(_.liftTo(new Exception(s"Not Found")))
                    }
                    .flatMap(_.liftTo[F])
                },
                latestBlocks,
                getData.map(_.show),
              ).routes

              val allRoutes = RouterFix(s"/${sdk.api.RootPath.mkString("/")}" -> routes)

              web.server(allRoutes, 8080 + idx, "localhost")
            }

            (run concurrently bootstrap concurrently apiServerStream) -> getData
        }
      }
      .map(_.unzip)
      .flatMap { case (streams, diags) =>
        val simStream = Stream.emits(streams).parJoin(streams.size)

        val logDiag = {
          val getNetworkState = diags.sequence
          import NetworkSnapshot.*
          getNetworkState.showAnimated(samplingTime = 150.milli)
        }

        simStream concurrently logDiag
      }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val prompt = """
    This application simulates the network of nodes with the following features:
      1. Speculative execution (block merge).
      2. Garbage collection of the consensus state.
      3. Synchronous flavour of a consensus protocol.
      4. The state that the network agrees on is a map of wallet balances.
      5. A transaction is a move of some random amount from one wallet to another.

    Blocks are created by all nodes as fast as possible. Number of transactions per block can be adjusted
      with the argument. Transactions are generated randomly.

    Usage:
      Run simulation:           java -jar sim.jar -Dconfig.file <path to config file> run
      Dump default config file: java -jar sim.jar --print-default-config <path>

    Output: console animation of the diagnostics data read from nodes. One line per node, sorted by the node index.

      BPS | Consensus size | Proposer status | Processor size | History size | LFS hash
    110.0         23         Creating            0 / 0(10)           3456          a71bbe62ee03c16498b9d975501f4063e8ca344f9f5b1efb95aedc13e432393e
    110.0         23             Idle            1 / 0(10)           3456          a71bbe62ee03c16498b9d975501f4063e8ca344f9f5b1efb95aedc13e432393e
    110.0         23             Idle            1 / 0(10)           3456          a71bbe62ee03c16498b9d975501f4063e8ca344f9f5b1efb95aedc13e432393e
    110.0         23             Idle            1 / 0(10)           3456          a71bbe62ee03c16498b9d975501f4063e8ca344f9f5b1efb95aedc13e432393e

      BPS             - blocks finalized per second (measured on the node with index 0).
      Consensus size  - number of blocks in the consensus state.
      Proposer status - status of the block proposer.
      Processor size  - number of blocks currently in processing / waiting for processing.
      History size    - blockchain state size. Number of records in key value store underlying the radix tree.
                      Keys are integers and values are longs.
      LFS hash        - hash of the oldest onchain state required for the node to operate (hash of last finalized state).

      In addition to console animation each node exposes its API via http on the port 808<i> where i is the index
        of the node.

    Available API endpoints:
      - latest blocks node observes from each peer
      http://127.0.0.1:8080/api/v1/latest

      - status
      http://127.0.0.1:8080/api/v1/status

      - block given id
      http://127.0.0.1:8080/api/v1/block/<block_id>
      Example: http://127.0.0.1:8080/api/v1/block/genesis

      - balance of a wallet given its id for historical state identified by hash
      http://127.0.0.1:8080/api/v1/balance/<hash>/<wallet_id>
      Example: http://127.0.0.1:8080/api/v1/balances/7da2990385661697cf7017a206084625720439429c26a580783ab0768a80251d/1

      - deploy given id
      http://127.0.0.1:8080/api/v1/deploy/<deploy_id>
      Example: http://127.0.0.1:8080/api/v1/deploy/genesis

    """.stripMargin

    import pureconfig.*
    import pureconfig.generic.auto.*

    implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

    args match {
      case List("--help")                 => IO.println(prompt).as(ExitCode.Success)
      case List("--print-default-config") =>
        val referenceConf = ClassesAsConfig(
          "gorki",
          InfluxDbConfig.Default,
          NodeConfig.Default,
          SimConfig.Default,
        )
        IO.println(referenceConf).as(ExitCode.Success)

      case List("run") =>
        final case class GorkiConfig(gorki: Config)
        ConfigSource.default
          .load[GorkiConfig]
          .map(_.gorki)
          .leftTraverse[IO, Config] { err =>
            new Exception("Invalid configuration file", new Exception(err.toList.map(_.description).mkString("\n")))
              .raiseError[IO, Config]
          }
          .map(_.merge)
          .flatMap { case Config(network, node, influxDb) =>
            implicit val kts: KamonContextStore[IO] = KamonContextStore.forCatsEffectIOLocal
            Random.scalaUtilRandom[IO].flatMap { implicit rndIO =>
              if (node.persistOnChainState)
                NetworkSim.sim[IO](network, node, influxDb).compile.drain.as(ExitCode.Success)
              else {
                // in memory cannot run forever so restart each minute
                Stream
                  .eval(SignallingRef.of[IO, Boolean](false))
                  .flatMap { sRef =>
                    val resetStream = Stream.sleep[IO](1.minutes) ++ Stream.eval(sRef.set(true))
                    NetworkSim.sim[IO](network, node, influxDb).interruptWhen(sRef) concurrently resetStream
                  }
                  .repeat
                  .compile
                  .drain
                  .as(ExitCode.Success)
              }
            }
          }

      case x => IO.println(s"Illegal option '${x.mkString(" ")}': see --help").as(ExitCode.Error)
    }
  }
}
