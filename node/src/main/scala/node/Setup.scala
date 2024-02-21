package node

import cats.Parallel
import cats.effect.Ref
import cats.effect.kernel.Ref.Make
import cats.effect.kernel.{Async, Resource, Sync}
import cats.effect.std.Queue
import cats.syntax.all.*
import diagnostics.metrics.InfluxDbBatchedMetrics
import node.Hashing.*
import node.Node.BalancesShardName
import node.Serialization.*
import node.api.web.PublicApiJson
import node.api.web.https4s.RouterFix
import node.api.{web, ExternalApiSlickImpl}
import node.comm.CommImpl.{BlockHash, BlockHashResponse}
import node.comm.{CommImpl, PeerTable}
import node.lmdb.LmdbStoreManager
import node.rpc.GrpcServer
import node.state.StateManager
import org.http4s.HttpRoutes
import org.http4s.server.Server
import sdk.api.ExternalApi
import sdk.api.data.Balance
import sdk.comm.Peer
import sdk.data.BalancesDeploy
import sdk.diag.Metrics
import sdk.history.ByteArray32
import sdk.primitive.ByteArray
import sdk.store.{HistoryWithValues, InMemoryKeyValueStoreManager, KeyValueStoreManager, KeyValueTypedStore}
import sdk.syntax.all.sdkSyntaxByteArray
import slick.SlickDb
import slick.api.SlickApi
import slick.jdbc.JdbcBackend.DatabaseDef
import slick.jdbc.PostgresProfile
import slick.migration.api.PostgresDialect
import weaver.WeaverState
import weaver.data.{ConflictResolution, FinalData}

import java.nio.file.Path

/** Node setup. */
final case class Setup[F[_]](
  // sql database
  database: SlickApi[F],
  // key value store managers
  diskKvStoreManager: KeyValueStoreManager[F],
  memKvStoreManager: KeyValueStoreManager[F],
  // web server
  webServer: org.http4s.server.Server,
  // grpc server
  grpcServer: io.grpc.Server,
  // metrics streamer
  metrics: Metrics[F],
  // peerTable
  peerManager: PeerTable[F, String, Peer],
  // shard for balances
  balancesShard: BalancesStateBuilderWithReader[F],
  // deploy pool
  deployPool: KeyValueTypedStore[F, ByteArray, BalancesDeploy],
  stateManager: StateManager[F],
  // ports to bypass API servers (e.g. to simulate the network)
  ports: Ports[F],
  node: Node[F],
)

// Not to use in production. Ports that can be used to bypass API servers to call directly in simulation.
final case class Ports[F[_]](
  inHash: fs2.Stream[F, BlockHash],
  outHash: fs2.Stream[F, BlockHash],
  finStream: fs2.Stream[F, ConflictResolution[BalancesDeploy]],
  sendToInput: BlockHash => F[Unit],
)

object Setup {
  private def database[F[_]: Async](dbDef: Resource[F, DatabaseDef]): Resource[F, SlickApi[F]] =
    dbDef.evalMap(x => SlickDb(x, PostgresProfile, new PostgresDialect).flatMap(SlickApi[F]))

  private def kvDiskStoreManager[F[_]: Async](dataDir: Path): Resource[F, KeyValueStoreManager[F]] =
    Resource.make(LmdbStoreManager[F](dataDir))(_.shutdown)

  private def kvMemStoreManager[F[_]: Async]: Resource[F, KeyValueStoreManager[F]] =
    Resource.make(Sync[F].delay(InMemoryKeyValueStoreManager[F]()))(_.shutdown)

  private def metrics[F[_]: Async](
    enableInfluxDb: Boolean,
    influxDbConfig: diagnostics.metrics.Config,
    sourceTag: String,
  ): Resource[F, Metrics[F]] =
    if (enableInfluxDb) InfluxDbBatchedMetrics[F](influxDbConfig, sourceTag)
    else Resource.pure(Metrics.unit)

  private def webServer[F[_]: Async](
    host: String,
    port: Int,
    devMode: Boolean,
    externalApi: ExternalApi[F],
  ): Resource[F, Server] = {
    val routes: HttpRoutes[F] = PublicApiJson[F](externalApi).routes
    val allRoutes             = RouterFix(s"/${sdk.api.RootPath.mkString("/")}" -> routes)
    web.server(allRoutes, port, host, devMode)
  }

  private def deployPool[F[_]: Sync: Make]: Resource[F, KeyValueTypedStore[F, ByteArray, BalancesDeploy]] =
    Resource.eval(Ref[F].of(Map.empty[ByteArray, BalancesDeploy]).map { st =>
      new KeyValueTypedStore[F, ByteArray, BalancesDeploy] {
        override def get(keys: Seq[ByteArray]): F[Seq[Option[BalancesDeploy]]] =
          st.get.map(_.view.filterKeys(keys.contains).values.toSeq.map(Option.apply))

        override def put(kvPairs: Seq[(ByteArray, BalancesDeploy)]): F[Unit] = st.update(_.++(kvPairs))

        override def delete(keys: Seq[ByteArray]): F[Int] = st.update(_.--(keys)).as(0)

        override def contains(keys: Seq[ByteArray]): F[Seq[Boolean]] =
          st.get.map(_.view.map(keys.contains).toSeq)

        override def collect[T](pf: PartialFunction[(ByteArray, () => BalancesDeploy), T]): F[Seq[T]] = ???

        override def toMap: F[Map[ByteArray, BalancesDeploy]] = st.get
      }
    })

  // TODO proper implementation, not always default is used and db is not populated
  private def loadCfg[F[_]: Async](
    db: SlickApi[F],
  ): Resource[F, (node.Config, diagnostics.metrics.Config, comm.Config)] = Resource.eval(Sync[F].defer {
    val dbEmpty: F[Boolean]                                                           = true.pure
    val loadInitial: F[(node.Config, diagnostics.metrics.Config, comm.Config)]        =
      (node.Config.Default, diagnostics.metrics.Config.Default, comm.Config.Default).pure[F]
    val loadFromDB: F[(node.Config, diagnostics.metrics.Config, comm.Config)]         = loadInitial
    val saveToDb: ((node.Config, diagnostics.metrics.Config, comm.Config)) => F[Unit] = _ => ().pure

    dbEmpty.ifM(loadFromDB, loadInitial.flatTap(saveToDb))
  })

  def all[F[_]: Async: Parallel](
    dbDef: Resource[F, DatabaseDef],
    id: ByteArray,
    // TODO for now node always starts from the same genesis state, specified either in simulator or in main function
    //   make it restored from the database
    genesisData: FinalData[ByteArray],
    idx: Int = 0,
  ): Resource[F, Setup[F]] = for {
    // connect to the database
    database          <- database[F](dbDef)
    // load node configuration
    cfg               <- loadCfg[F](database)
    (nCfg, mCfg, cCfg) = cfg
    // metrics
    metrics           <- metrics(nCfg.enableInfluxDb, mCfg, id.toHex)
    // kv store managers (for rholang execution)
    diskKvManager     <- kvDiskStoreManager(nCfg.kvStoresPath)
    memKvManager      <- kvMemStoreManager
    // shard for balances
    balanceHwV        <- Resource.eval(HistoryWithValues[F, Balance](BalancesShardName, diskKvManager))
    balancesShard      = { implicit val m: Metrics[F] = metrics; BalancesStateBuilderWithReader(balanceHwV) }
    // deploy pool
    dPool             <- deployPool
    // state
    nodeState         <- Resource.eval(StateManager[F](Bootstrap.const(genesisData)))
    // api
    latestM            = nodeState.weaverStRef.get.map(_.lazo.latestMessages.toList)
    extApiImpl         = ExternalApiSlickImpl(database, balancesShard, latestM, dPool)
    // web server
    webServer         <- webServer[F](nCfg.webApi.host, nCfg.webApi.port + idx, nCfg.devMode, extApiImpl)
    // port for input blocks
    inBlockQ          <- Resource.eval(Queue.unbounded[F, BlockHash])
    // grpc server
    grpcSrv           <- {
      val receive = inBlockQ.tryOffer(_: BlockHash).map(BlockHashResponse)
      val bep     = CommImpl.blockHashExchangeProtocol[F](receive)
      GrpcServer.apply[F](nCfg.gRpcPort + idx, bep)
    }
    // peerTable
    peerTable         <- { implicit val db: SlickApi[F] = database; Resource.eval(PeerTable(cCfg)) }
    // TODO these fringe mapping should not be here
    fringeMappingRef  <- Resource.eval(Ref.of(Map.empty[Set[ByteArray], ByteArray32]))
    node              <- {
      implicit val m: Metrics[F] = metrics;
      Resource.eval(
        Node.make[F](fringeMappingRef, nodeState, id, balancesShard, WeaverState.empty(genesisData), database),
      )
    }
  } yield {
    // This `pullIncoming` in real node should contain messages received by API server (grpc)
    val inHashes     = fs2.Stream.fromQueueUnterminated(inBlockQ)
    val pullIncoming = inHashes.evalTap(x => node.dProc.acceptMsg(x.msg))

    // Streams
    val sendOutbound = node.dProc.output.map(BlockHash)
    val finStream    = node.dProc.finStream
    val ports        = Ports(pullIncoming, sendOutbound, finStream, inBlockQ.offer)

    Setup(
      database,
      diskKvManager,
      memKvManager,
      webServer,
      grpcSrv,
      metrics,
      peerTable,
      balancesShard,
      dPool,
      nodeState,
      ports,
      node,
    )
  }
}
