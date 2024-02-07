package diagnostics

import cats.effect.{IO, IOLocal}
import kamon.Kamon
import kamon.context.Context

/**
 * Storage for Kamon context.
 * This exists because context storages available in Kamon library are incompatible with the fiber based
 * model of execution of cats.effect, so spans are not attributed to correct traces.
 *
 * At the moment there is no other way to provide fiber aware context propagation except using IOLocal.
 * So we are forced to pull concrete effect type here. But still, to not spread IO type across the codebase,
 * this type class is provided.
 */
trait KamonContextStore[F[_]] {
  def get: F[Context]
  def set(context: Context): F[Unit]
}

object KamonContextStore {
  def apply[F[_]](implicit ev: KamonContextStore[F]): KamonContextStore[F] = ev

  def forCatsEffectIOLocal = {
    import cats.effect.unsafe.implicits.global
    val ioLocalKamonContext = IOLocal(Kamon.currentContext()).unsafeRunSync()

    new KamonContextStore[IO] {
      override def get: IO[Context]             = ioLocalKamonContext.get
      override def set(span: Context): IO[Unit] = ioLocalKamonContext.set(span)
    }
  }
}
