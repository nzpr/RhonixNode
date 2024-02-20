package sim

import cats.effect.kernel.Resource
import cats.effect.{Async, Sync}
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import slick.jdbc.JdbcBackend.Database
import slick.util.AsyncExecutor

// This is straight copy of slick.EmbeddedPgSqlSlickDb.scala in db package.
// Since it is defined only for tests, it is not accessible in the main code.
// TODO Maybe it makes sense to move simulation to node tests
object SlickEmbeddedPgDatabase {

  def apply[F[_]: Async]: Resource[F, Database] = {
    val open           = Sync[F].delay(
      EmbeddedPostgres
        .builder()
        .setOutputRedirector(ProcessBuilder.Redirect.to(new java.io.File("/tmp/embedPgSql.log")))
        .start(),
    )
    val maxConnections = 100
    val minThreads     = 100
    val maxThreads     = 100
    val queueSize      = 1000

    Resource
      .make(open)(db => Sync[F].delay(db.close()))
      .map(x =>
        Database.forDataSource(
          x.getPostgresDatabase,
          maxConnections = Some(maxConnections),
          executor = AsyncExecutor("EmbeddedPgExecutor", minThreads, maxThreads, queueSize, maxConnections),
        ),
      )
  }
}
