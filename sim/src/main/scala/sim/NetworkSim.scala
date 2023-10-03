package sim

import cats.Parallel
import cats.effect.*
import cats.effect.kernel.{Async, Temporal}
import cats.effect.std.{Console, Random}
import cats.syntax.all.*
import diagnostics.KamonContextStore
import dproc.data.Block
import fs2.{Pipe, Stream}
import io.circe.Encoder
import node.Node
import node.api.web
import node.api.web.PublicApiJson
import node.api.web.https4s.RouterFix
import node.lmdb.LmdbStoreManager
import org.http4s.EntityEncoder
import sdk.codecs.Base16
import sdk.hashing.Blake2b256Hash
import sdk.history.History.EmptyRootHash
import sdk.history.instances.RadixHistory
import sdk.store.*
import sdk.syntax.all.*
import sim.balances.*
import sim.balances.MergeLogicForPayments.{attemptAdd, mergeRejectNegativeOverflow}
import sim.balances.data.{Datum, Deploy}
import weaver.WeaverState
import weaver.data.*

import java.nio.file.Paths
import scala.concurrent.duration.{Duration, DurationInt, MICROSECONDS}
import scala.util.Try

object NetworkSim extends IOApp {

  // Dummy types for message id, sender id and transaction
  type M = String
  type S = String
  type T = Deploy
  implicit val ordS = new Ordering[String] {
    override def compare(x: S, y: S): Int = x compareTo y
  }

  final case class Config(
    size: Int,
    usersNum: Int,
    processingConcurrency: Int,
    txPerBlock: Int,
    exeDelay: Duration,
    propDelay: Duration,
  )

  final case class NetNode[F[_]](
    id: S,
    node: Node[F, M, S, T],
    balanceApi: (Blake2b256Hash, Channel) => F[Option[Long]],
    fringeMapping: Set[M] => F[Blake2b256Hash],
  )

  def genesisBlock[F[_]: Async: Parallel](
    sender: S,
    genesisExec: FinalData[S],
    users: Set[Int],
  ): F[Block.WithId[M, S, T]] = {
    val mkHistory     = sdk.history.History.create(EmptyRootHash, new InMemoryKeyValueStore[F])
    val mkValuesStore = Sync[F].delay {
      new ByteArrayKeyValueTypedStore[F, Blake2b256Hash, Datum](
        new InMemoryKeyValueStore[F],
        Blake2b256Hash.codec,
        datumCodec,
      )
    }

    (mkHistory, mkValuesStore).flatMapN { case history -> valueStore =>
      val genesisState  = new State(users.map(_ -> (Long.MaxValue / 2 -> 0L)).toMap)
      val genesisDeploy = BalancesDeploy("genesis", genesisState, 0L)
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

  def sim[F[_]: Async: Parallel: Random: Console: KamonContextStore](c: Config): Stream[F, Unit] = {

    /// Users (wallets) making transactions
    val users: Set[Channel] = (1 to c.usersNum).toSet

    /// Genesis data
    val lazinessTolerance = 1 // c.lazinessTolerance
    val senders           = Iterator.range(0, c.size).map(n => s"s$n").toList
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
    val txStore: Ref[F, Map[String, State]]  = Ref.unsafe(Map.empty[String, State])
    def saveTx(tx: Deploy): F[Unit]          = txStore.update(_.updated(tx.id, tx.state))
    def readTx(id: String): F[Option[State]] = txStore.get.map(_.get(id))

    def broadcast(
      peers: List[Node[F, M, S, T]],
      time: Duration,
    ): Pipe[F, M, Unit] = _.evalMap(m => Temporal[F].sleep(time) *> peers.traverse(_.dProc.acceptMsg(m)).void)

    def random(users: Set[Channel]): F[Map[Int, Long]] = for {
      txVal <- Random[F].nextLongBounded(100)
      from  <- Random[F].elementOf(users)
      to    <- Random[F].elementOf(users - from)
    } yield Map(from -> -txVal, to -> txVal)

    /** Storage resource for on chain storage (history and values) */
    def onChainStoreResource(
      kvStoreManager: KeyValueStoreManager[F],
    ): Resource[F, (RadixHistory[F], KeyValueTypedStore[F, Blake2b256Hash, (Balance, Long)])] =
      kvStoreManager.asResource
        .flatMap { kvStoreManager =>
          Resource.eval {
            for {
              historyStore <- kvStoreManager.store("history")
              valuesStore  <- kvStoreManager.store("data")
              history      <- sdk.history.History.create(EmptyRootHash, historyStore)
              values        =
                valuesStore.toByteArrayTypedStore[Blake2b256Hash, (Balance, Long)](Blake2b256Hash.codec, datumCodec)
            } yield history -> values
          }
        }

    def mkNode(vId: S): Resource[F, NetNode[F]] = {
      val dataPath     = Paths.get(s".lmdb/node-$vId")
//      val storeManager = Sync[F].delay(InMemoryKeyValueStoreManager[F])
      val storeManager = LmdbStoreManager(dataPath)
      Resource.eval(storeManager).flatMap(onChainStoreResource).flatMap { case history -> valueStore =>
        val blockSeqNumRef = Ref.unsafe(0)
        val assignBlockId  = (_: Any) => blockSeqNumRef.updateAndGet(_ + 1).map(idx => s"$vId-$idx")

        val txSeqNumRef = Ref.unsafe(0)
        val nextTxs     = txSeqNumRef
          .updateAndGet(_ + 1)
          .flatMap(idx => random(users).map(st => balances.data.BalancesDeploy(s"$vId-tx-$idx", new State(st))))
          .replicateA(c.txPerBlock)
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
        ): F[((Array[Byte], Seq[T]), (Array[Byte], Seq[T]), Seq[T])] =
          for {
            baseState <- fringeMappingRef.get.map(_(baseFringe))
            r         <- mergeRejectNegativeOverflow(balancesEngine, baseState, toFinalize, toMerge)
            _         <- Async[F].sleep(c.exeDelay).replicateA(toExecute.size)

            ((newFinState, finRj), (newMergeState, provRj)) = r

            r <- balancesEngine.buildState(baseState, newFinState, newMergeState)

            (finalHash, preState) = r

            preBalances             <- readState(balancesEngine, preState, toExecute.flatMap(_.state.diffs.keys).toList)
            (executed, doubleSpends) =
              foldCollectFailures[State, T](new State(preBalances), toExecute.toList, attemptAdd)

            r1           <- balancesEngine.buildState(preState, State.Default, executed)
            (_, postHash) = r1
            _            <- fringeMappingRef.update(_ + (finalFringe -> finalHash))
          } yield ((finalHash.bytes.bytes, finRj), (postHash.bytes.bytes, provRj), doubleSpends)

        val netNode = Node[F, M, S, T](
          vId,
          WeaverState.empty[M, S, T](lfs.state),
          assignBlockId,
          nextTxs,
          buildState,
          saveBlock,
          readBlock,
        ).map(
          NetNode(
            vId,
            _,
            balancesEngine.readState(_: Blake2b256Hash, _: Channel),
            (x: Set[M]) => fringeMappingRef.get.map(_.getUnsafe(x)),
          ),
        )
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
                Node(weaverStRef, processorStRef, proposerStRef, bufferStRef, dProc),
                getBalance,
                fringeToHash,
              ) -> idx =>
            val bootstrap =
              Stream.eval(genesisBlock[F](senders.head, genesisExec, users).flatMap { genesisM =>
                val genesis = genesisM.m.txs.head
                saveBlock(genesisM) *> saveTx(genesis) *> dProc.acceptMsg(genesisM.id) *>
                  Console[F].println(s"Bootstrap done for ${self}")
              })
            val notSelf   = net.collect { case NetNode(id, node, _, _) -> _ if id != self => node }

            val run = dProc.dProcStream concurrently {
              dProc.output.through(broadcast(notSelf, c.propDelay))
            }

            val tpsRef    = Ref.unsafe[F, Float](0f)
            val tpsUpdate = dProc.finStream
              .map(_.accepted.toList)
              .flatMap(Stream.emits(_))
              .throughput(1.second)
              // finality is computed by each sender eventually so / c.size
              .evalTap(x => tpsRef.set(x.toFloat / c.size))
            val getData   =
              (
                idx.pure,
                tpsRef.get,
                weaverStRef.get,
                proposerStRef.get,
                processorStRef.get,
                bufferStRef.get,
              ).flatMapN { case (id, tps, w, p, pe, b) =>
                val lfsHashF =
                  fringeToHash(
                    w.lazo.fringes.minByOption { case (i, _) => i }.map { case (_, fringe) => fringe }.getOrElse(Set()),
                  )
                lfsHashF.map(NetworkSnapshot.NodeSnapshot(id, tps, w, p, pe, b, _))
              }

            val apiServerStream: Stream[F, ExitCode] = {
              import io.circe.generic.auto.*
              import org.http4s.circe.*

              implicit val c: String => Try[Int]            = (x: String) => Try(x.toInt)
              implicit val d: String => Try[String]         = (x: String) => Try(x)
              implicit val e: String => Try[Blake2b256Hash] =
                (x: String) => Base16.decode(x).flatMap(Blake2b256Hash.deserialize)
              implicit val encoder: Encoder[Array[Byte]]    =
                Encoder[String].imap(s => Base16.decode(s).getUnsafe)(x => Base16.encode(x))

              implicit val a: EntityEncoder[F, Long]         = jsonEncoderOf[F, Long]
              implicit val x: EntityEncoder[F, Int]          = jsonEncoderOf[F, Int]
              implicit val f: EntityEncoder[F, String]       = jsonEncoderOf[F, String]
              implicit val g: EntityEncoder[F, Array[Byte]]  = jsonEncoderOf[F, Array[Byte]]
              implicit val h1: EntityEncoder[F, Set[String]] = jsonEncoderOf[F, Set[String]]

              implicit val h: EntityEncoder[F, Block[String, String, String]] =
                jsonEncoderOf[F, Block[String, String, String]]

              implicit val bs: EntityEncoder[F, State] = jsonEncoderOf[F, State]

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

              val routes = PublicApiJson[F, Block[M, S, String], State](
                blockByHash(_).flatMap(_.liftTo(new Exception(s"Not Found"))),
                readTx(_).flatMap(_.liftTo(new Exception(s"Not Found"))),
                (h: String, w: String) => {
                  val blakeH = Base16.decode(h).flatMap(Blake2b256Hash.deserialize)
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

            (run concurrently bootstrap concurrently tpsUpdate concurrently apiServerStream) -> getData
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

    Usage: specify 6 input arguments
      1. Number of nodes in the network
      2. Number of blocks that node is allowed to process concurrently
      3. Number of users of the network
      4. Number of transactions in a block
      5. Delay to add to transaction execution time (microseconds)
      6. Network propagation delay (microseconds)

      eg java -jar *.jar 4 4 100 1 0 0

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

    args match {
      case List("--help") => IO.println(prompt).as(ExitCode.Success)
      case List(
            size,
            processingConcurrency,
            usersNum,
            txsNum,
            exeDelay,
            propDelay,
          ) =>
        val config = Config(
          size = size.toInt,
          usersNum = usersNum.toInt,
          processingConcurrency = processingConcurrency.toInt,
          txPerBlock = txsNum.toInt,
          Duration(exeDelay.toLong, MICROSECONDS),
          Duration(propDelay.toLong, MICROSECONDS),
//          lazinessTolerance.toInt,
        )

        implicit val kts: KamonContextStore[IO] = KamonContextStore.forCatsEffectIOLocal
        Random.scalaUtilRandom[IO].flatMap { implicit rndIO =>
          NetworkSim.sim[IO](config).compile.drain.as(ExitCode.Success)
        }

      case x => IO.println(s"Illegal option '${x.mkString(" ")}': see --help").as(ExitCode.Error)
    }
  }
}
