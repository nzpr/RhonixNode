package slick

import cats.effect.kernel.Resource
import cats.effect.{Async, Sync}
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import slick.jdbc.H2Profile
import slick.jdbc.JdbcBackend.Database
import slick.migration.api.H2Dialect

object EmbeddedPgSqlSlickDb {
  def apply[F[_]: Async]: Resource[F, SlickDb] = {
    val open       = Sync[F].delay(EmbeddedPostgres.builder().start())
    val dbResource =
      Resource.make(open)(db => Sync[F].delay(db.close())).map(x => Database.forDataSource(x.getPostgresDatabase, None))
    SlickDb.resource(dbResource, H2Profile, new H2Dialect)
  }
}
