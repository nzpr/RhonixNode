package coop.rchain.rspace

/**
  * Type class for matching patterns with data.
  *
  * @tparam P A type representing patterns
  * @tparam A A type representing input data
  * @tparam B A type representing matched data
  */
trait Match[F[_], P, A, B] {

  def get(p: P, a: A): F[Option[B]]
}
