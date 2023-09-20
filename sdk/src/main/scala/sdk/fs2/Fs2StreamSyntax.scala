package sdk.fs2

import cats.effect.Ref
import cats.effect.kernel.Ref.Make
import cats.effect.kernel.Temporal
import fs2.Stream

import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait Fs2StreamSyntax {
  implicit def fs2StreamSyntax[F[_], A](x: Stream[F, A]): Fs2StreamOps[F, A] = new Fs2StreamOps[F, A](x)
}

final class Fs2StreamOps[F[_], A](private val x: Stream[F, A]) extends AnyVal {

  /// Convert stream into a stream of numbers of elements pulled from initial stream per a period specified.
  def throughput(period: FiniteDuration = 1.second)(implicit makeF: Make[F], tempF: Temporal[F]): Stream[F, Int] =
    Stream.eval(Ref.of[F, Int](0)).flatMap { acc =>
      val out = Stream.repeatEval(acc.getAndUpdate(_ => 0)).metered(period)
      out mergeHaltR x.evalTap(_ => acc.update(_ + 1)).drain
    }
}
