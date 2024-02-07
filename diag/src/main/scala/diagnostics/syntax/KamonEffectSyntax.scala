package diagnostics.syntax

import cats.effect.Async
import cats.effect.kernel.Sync
import diagnostics.{KamonContextStore, KamonDiagnostics}

/** Syntax provided by Kamon for any effectful computation of a value. */
final class KamonEffectOps[F[_], T](private val f: F[T]) extends AnyVal {

  /** Measure time. */
  def kamonTimer(name: String, tags: Map[String, Any] = Map())(implicit syncF: Sync[F]): F[T] =
    KamonDiagnostics.logTime(f)(name, tags)

  /** Build a span. */
  def span(opName: String, component: String)(implicit asyncF: Async[F], ev: KamonContextStore[F]): F[T] =
    KamonDiagnostics.span(f)(opName, component)

}

trait KamonEffectSyntax {
  implicit def kamonSyntax[F[_]: Sync, T](f: F[T]): KamonEffectOps[F, T] = new KamonEffectOps[F, T](f)
}
