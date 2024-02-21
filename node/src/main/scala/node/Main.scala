package node

import cats.Monad
import cats.effect.std.Env
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import org.apache.commons.dbcp2.BasicDataSource
import sdk.codecs.Base16
import sdk.primitive.ByteArray
import sdk.reflect.ClassesAsConfig
import sdk.syntax.all.*
import slick.SlickPgDatabase
import slick.jdbc.JdbcBackend.Database
import weaver.WeaverState

object Main extends IOApp {
  private val DbUrlKey = "gorki.db.url"
  private val DbUser   = "gorki.db.user"
  private val DbPasswd = "gorki.db.passwd"
  private val NodeId   = "gorki.node.id"

  private val DefaultNodeId = "defaultNodeId"

  private def configWithEnv[F[_]: Env: Monad]: F[(db.Config, String)] = for {
    dbUrlOpt      <- Env[F].get(DbUrlKey)
    dbUserOpt     <- Env[F].get(DbUser)
    dbPasswordOpt <- Env[F].get(DbPasswd)
    nodeIdOpt     <- Env[F].get(NodeId)
  } yield {
    val newCfg = for {
      dbUrl      <- dbUrlOpt
      dbUser     <- dbUserOpt
      dbPassword <- dbPasswordOpt
      nodeId     <- nodeIdOpt
    } yield db.Config.Default.copy(dbUrl = dbUrl, dbUser = dbUser, dbPassword = dbPassword) -> nodeId

    newCfg.getOrElse(db.Config.Default -> DefaultNodeId)
  }

  override def run(args: List[String]): IO[ExitCode] =
    args match {
      case List("--print-default-config") =>
        val referenceConf = ClassesAsConfig(
          "gorki",
          db.Config.Default,
          node.Config.Default,
        )
        IO.println(referenceConf).as(ExitCode.Success)
      case _                              =>
        configWithEnv[IO].flatMap { case (dbCfg, nodeId) =>
          // Todo read bonds file
          val genesisExec = DummyGenesis(100)
          val idBa        = Base16.decode(nodeId).map(ByteArray(_)).getUnsafe
          val db          = SlickPgDatabase[IO](dbCfg)

          fs2.Stream
            .resource(Setup.all[IO](db, idBa, genesisExec).map(Node.stream))
            .flatten
            .compile
            .drain
            .map(_ => ExitCode.Success)
            .handleError(_ => ExitCode.Error)
        }
    }
}
