package io.rhonix.rholang.normalizer.envimpl

import cats.effect.Sync
import io.rhonix.rholang.normalizer.env.NestingWriter
import io.rhonix.rholang.normalizer.envimpl.*

final case class NestingWriterImpl[F[_]: Sync](
  private val patternInfo: PatternInfoChain,
  private val bundleInfo: BundleInfoChain,
) extends NestingWriter[F] {

  override def withinPattern[R](inReceive: Boolean)(scopeFn: F[R]): F[R] =
    patternInfo.runWithNewStatus(inReceive)(scopeFn)

  override def withinBundle[R](scopeFn: F[R]): F[R] = bundleInfo.runWithNewStatus(scopeFn)
}
