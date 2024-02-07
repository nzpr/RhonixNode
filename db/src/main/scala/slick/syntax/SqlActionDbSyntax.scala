package slick.syntax

import cats.effect.kernel.Async
import slick.SlickDb
import slick.dbio.{Effect, NoStream}
import slick.sql.SqlAction

trait SqlActionRunSyntax {
  implicit def sqlActionRunSyntax[F[_], R, S <: NoStream, E <: Effect](
    x: SqlAction[R, S, E],
  ): SqlSyntaxOps[F, R, S, E] =
    new SqlSyntaxOps[F, R, S, E](x)
}

final class SqlSyntaxOps[F[_], R, +S <: NoStream, -E <: Effect](x: SqlAction[R, S, E]) {
  def run(implicit F: Async[F], slickDb: SlickDb[F]): F[R] = SlickDb[F].run(x)
}
