package slick

import cats.effect.kernel.Async
import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile
import slick.migration.api.Dialect

// Do not expose underlying Database to prevent possibility of calling close() on it
final case class SlickDb[F[_]] private (private val db: Database, profile: JdbcProfile) {
  def run[A](a: DBIOAction[A, NoStream, Nothing])(implicit F: Async[F]): F[A] = Async[F].fromFuture(db.run(a).pure)
}

object SlickDb {
  def resource[F[_]: Async](
    dbResource: Resource[F, Database],
    profile: JdbcProfile,
    dialect: Dialect[?],
  ): Resource[F, SlickDb[F]] = {
    // Execute migrations each time resource is instantiated
    def runMigration(db: Database): F[Unit] = Async[F].fromFuture(db.run(migrations.all(dialect)()).pure)

    dbResource.evalMap(db => runMigration(db).as(new SlickDb[F](db, profile)))
  }

  def apply[F[_]: Async](implicit x: SlickDb[F]): SlickDb[F] = x
}
