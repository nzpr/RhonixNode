package coop.rchain.models

import cats.effect.Sync
import cats.syntax.all.*

import scala.util.hashing.MurmurHash3

/**
 * A typeclass for computing Scala's default `hashCode` for case classes in a stacksafe manner.
 *
 * An instance of this class must be provided for every case where `hashCode` is overridden.
 * The generative tests should catch that, provided the offending case class is used transitively.
 *
 * @tparam A
 */
trait HashM[A] {
  def hash[F[_]: Sync](value: A): F[Int]
}

object HashM {

  def apply[A](implicit ev: HashM[A]): HashM[A] = ev

  /**
   * HashM instance delegating to .##.
   *
   * This MUST NOT be used for types having a recursive structure
   * nor for types whose hashCode() delegates to others.
   * Otherwise the stacksafety provided by the HashM typeclass is broken.
   *
   * @tparam A
   * @return
   */
  def opaqueHash[A]: HashM[A] = new HashM[A] {

    override def hash[F[_]: Sync](value: A): F[Int] = Sync[F].pure(value.##)

  }
}

object HashMDerivation {
  import magnolia.*

  type Typeclass[T] = HashM[T]

  def combine[T](ctx: CaseClass[HashM, T]): HashM[T] = new HashM[T] {

    def hash[F[_]: Sync](value: T): F[Int] = Sync[F].defer {
      val hashFs = ctx.parameters.map { p =>
        p.typeclass.hash(p.dereference(value))
      }
      for {
        componentHashes <- hashFs.toList.sequence
      } yield productHash(ctx.typeName.short, componentHashes)
    }

  }

  // copied and adapted from scala.util.hashing.MurmurHash3,
  // which is used in Scala's case class hash code implementation
  @SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.SeqApply"))
  private def productHash(prefix: String, elements: Seq[Int]): Int = {
    val arr = elements.size
    // Case objects have the hashCode inlined directly into the
    // synthetic hashCode method, but this method should still give
    // a correct result if passed a case object.
    if (arr == 0) {
      prefix.hashCode
    } else {
      var h = MurmurHash3.productSeed
      var i = 0
      while (i < arr) {
        h = MurmurHash3.mix(h, elements(i))
        i += 1
      }
      MurmurHash3.finalizeHash(h, arr)
    }
  }

  def dispatch[T](ctx: SealedTrait[HashM, T]): HashM[T] = new HashM[T] {

    def hash[F[_]: Sync](value: T): F[Int] = Sync[F].defer {
      ctx.dispatch(value) { sub =>
        sub.typeclass.hash(sub.cast(value))
      }
    }

  }

  implicit def hashMGen[T]: HashM[T] = macro Magnolia.gen[T]
}
