package sim

import cats.effect.*
import cats.effect.std.Random
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import node.Setup
import sdk.comm.Peer
import sdk.data.BalancesState
import sdk.hashing.Blake2b
import sdk.primitive.ByteArray
import slick.SlickPgDatabase
import slick.jdbc.JdbcBackend.Database
import weaver.data.{Bonds, FinalData}

object NetworkSim extends IOApp {

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
      Run simulation:           java -Dconfig.file=<path to config file> -jar sim.jar run
      Dump default config file: java -jar sim.jar --print-default-config > <path>

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
      case List("run")    =>
        val netCfg: sim.Config    = sim.Config.Default.copy(size = 3, usersNum = 2)
        val rnd                   = new scala.util.Random()
        /// Users (wallets) making transactions
        val users: Set[ByteArray] =
          (1 to netCfg.usersNum).map(_ => Array(rnd.nextInt().toByte)).map(Blake2b.hash256).map(ByteArray(_)).toSet
        val genesisPoS            = {
          val rnd               = new scala.util.Random()
          /// Genesis data
          val lazinessTolerance = 1 // c.lazinessTolerance
          val senders           =
            Iterator
              .range(0, netCfg.size)
              .map(_ => Array(rnd.nextInt().toByte))
              .map(Blake2b.hash256)
              .map(ByteArray(_))
              .toSet
          // Create lfs message, it has no parents, sees no offences and final fringe is empty set
          val genesisBonds      = Bonds(senders.map(_ -> 100L).toMap)
          FinalData(genesisBonds, lazinessTolerance, 10000)
        }
        val genesisBalances       = new BalancesState(users.map(_ -> Long.MaxValue / 2).toMap)

        implicit val rndIO: Random[IO] = Random.scalaUtilRandom[IO].unsafeRunSync()

        genesisPoS.bonds.activeSet.toList.zipWithIndex
          .traverse { case id -> idx =>
            val db: Resource[IO, Database] =
              SlickPgDatabase[IO](
                _root_.db.Config.Default.copy(dbUrl = s"${_root_.db.Config.Default.dbUrl}_${idx + 1}"),
              )
            // populate database with peers
            val peers: List[Peer]          = ???
            Setup.all[IO](db, id, genesisPoS, node.Main.randomDeploys[IO](users, netCfg.txPerBlock), idx)
          }
          .map(setups => Network.apply[IO](setups, genesisPoS, genesisBalances, netCfg))
          .use(_.compile.drain.as(ExitCode.Success))

      case x => IO.println(s"Illegal option '${x.mkString(" ")}': see --help").as(ExitCode.Error)
    }
  }
}
