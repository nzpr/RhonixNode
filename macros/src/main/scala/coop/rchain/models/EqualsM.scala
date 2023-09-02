package coop.rchain.models

import cats.effect.Sync
import cats.syntax.all.*

/**
  * A typeclass for computing Scala's default `equals` for case classes in a stacksafe manner.
  *
  * An instance of this class must be provided for every case where `equals` is overridden.
  * The generative tests should catch that, provided the offending case class is used transitively.
  *
  * @tparam A
  */
trait EqualM[A] {

  def equal[F[_]: Sync](self: A, other: A): F[Boolean]

  def equals[F[_]: Sync](a: A, b: Any): F[Boolean] =
    if (a.getClass.isInstance(b)) {
      equal[F](a, a.getClass.cast(b))
    } else false.pure[F]

}

object EqualM {

  def apply[A](implicit ev: EqualM[A]): EqualM[A] = ev

  def by[A, B: EqualM](f: A => B): EqualM[A] = new EqualM[A] {

    override def equal[F[_]: Sync](self: A, other: A): F[Boolean] =
      EqualM[B].equal(f(self), f(other))

  }

  /**
    * EqualM instance delegating to Any#equa(). Hence the type bound.
    * This MUST NOT be used for types having a recursive structure
    * nor for types whose EqualCode() delegates to others.
    * Otherwise the stacksafety provided by the EqualM typeclass is broken.
    *
    * @tparam A
    * @return
    */
  def opaqueEqual[A]: EqualM[A] = new EqualM[A] {
    override def equal[F[_]: Sync](self: A, other: A): F[Boolean] = Sync[F].pure(self == other)
  }

}

object EqualMDerivation {
  import magnolia.*

  type Typeclass[T] = EqualM[T]

  def combine[T](ctx: CaseClass[EqualM, T]): EqualM[T] = new EqualM[T] {

    def equal[F[_]: Sync](self: T, other: T): F[Boolean] = Sync[F].defer {
      ctx.parameters.to(LazyList).forallM { p =>
        p.typeclass.equal(p.dereference(self), p.dereference(other))
      }
    }

  }

  def dispatch[T](ctx: SealedTrait[EqualM, T]): EqualM[T] = new EqualM[T] {

    def equal[F[_]: Sync](self: T, other: T): F[Boolean] = Sync[F].defer {
      ctx.dispatch(self) { sub =>
        if (sub.cast.isDefinedAt(other)) {
          sub.typeclass.equal(sub.cast(self), sub.cast(other))
        } else {
          Sync[F].pure(false)
        }
      }
    }

  }

  implicit def eqMGen[T]: EqualM[T] = macro Magnolia.gen[T]
}
