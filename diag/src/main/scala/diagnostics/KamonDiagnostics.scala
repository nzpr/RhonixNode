package diagnostics

import cats.effect.kernel.Outcome
import cats.effect.{Async, Resource, Sync}
import cats.syntax.all.*
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.Timer
import kamon.tag.TagSet
import kamon.trace.Span

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object KamonDiagnostics {

  /**
   * Program having access to Kamon has to be run surrounded by this resource.
   */
  def kamonResource[F[_]: Sync](configOpt: Option[Config] = None): Resource[F, Unit] = {
    val stopTimeout = 2.second
    val start       = Sync[F].delay(configOpt.map(Kamon.init).getOrElse(Kamon.init()))
    val stop        = Sync[F].delay(Await.result(Kamon.stop(), stopTimeout))
    Resource.make(start)(_ => stop)
  }

  /**
   * @param name name of a timer
   * @param tags String or Long or Boolean, as per Kamon documentation. Others will be ignored.
   */
  private def timer[F[_]: Sync](name: String, tags: Map[String, Any]): Resource[F, Timer.Started] = {
    val start                  = Sync[F].delay(Kamon.timer(name).withTags(TagSet.from(tags)).start())
    def stop(t: Timer.Started) = Sync[F].delay(t.stop())
    Resource.make(start)(stop)
  }

  /** Analog to Kamon.runWithSpan with custom KamonContextStore. */
  private def runWithSpan[F[_]: Async, T](f: => F[T])(name: String, component: String)(implicit
    ctxStore: KamonContextStore[F],
  ): F[T] = for {
    curCtx <- ctxStore.get
    newSpan = Kamon.internalSpanBuilder(name, component).context(curCtx).start()
    _      <- ctxStore.set(curCtx.withEntry(Span.Key, newSpan))
    // run effect ensuring span is closed
    r      <- Sync[F].guaranteeCase(f) {
                case Outcome.Errored(err) => Sync[F].delay(newSpan.fail(err).finish())
                case _                    => Sync[F].delay(newSpan.finish())
              }
  } yield r

  def logTime[F[_]: Sync, T](f: => F[T])(name: String, tags: Map[String, Any] = Map()): F[T] = Sync[F].defer {
    if (Kamon.enabled()) timer(name, tags).surround(f) else f
  }

  def span[F[_]: Async, T](f: => F[T])(opName: String, component: String)(implicit
    ev: KamonContextStore[F],
  ): F[T] = Sync[F].defer(if (Kamon.enabled()) runWithSpan(f)(opName, component) else f)
}
