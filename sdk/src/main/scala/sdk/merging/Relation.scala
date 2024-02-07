package sdk.merging

import cats.Applicative
import cats.syntax.all.*

trait Relation[F[_], T] {
  def conflicts(l: T, r: T): F[Boolean]
  def depends(x: T, on: T): F[Boolean]
}

object Relation {

  // All transactions are mergeable and independent
  def notRelated[F[_]: Applicative, T]: Relation[F, T] = new Relation[F, T] {
    override def conflicts(l: T, r: T): F[Boolean] = false.pure[F]

    override def depends(x: T, on: T): F[Boolean] = false.pure[F]
  }
}
