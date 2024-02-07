package db.slick

import cats.effect.kernel.Async
import cats.effect.{Resource, Sync}
import slick.SlickDb
import slick.jdbc.H2Profile
import slick.jdbc.JdbcBackend.Database
import slick.migration.api.H2Dialect

// Slick interface for in memory H2. Only for testing.
object EmbeddedH2SlickDb {
  def apply[F[_]: Async]: Resource[F, SlickDb[F]] = {
    // db should be named (:test), and keepAliveConnection set to true,
    // otherwise something weird happens with the db life cycle and db is cleared before closed.
    val open       = Sync[F].delay(Database.forURL("jdbc:h2:mem:test", keepAliveConnection = true))
    val dbResource = Resource.make(open)(db => Sync[F].delay(db.close()))

    SlickDb.resource(dbResource, H2Profile, new H2Dialect)
  }
}
