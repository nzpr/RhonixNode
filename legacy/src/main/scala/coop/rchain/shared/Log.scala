package coop.rchain.shared

import cats.*
import cats.effect.Sync
import cats.syntax.all.*
import com.typesafe.scalalogging.Logger
import coop.rchain.catscontrib.effect.implicits.*

import scala.language.experimental.macros

trait Log[F[_]] {
  def isTraceEnabled(implicit ev: Logger): F[Boolean]
  def trace(msg: => String)(implicit ev: Logger): F[Unit]
  def debug(msg: => String)(implicit ev: Logger): F[Unit]
  def info(msg: => String)(implicit ev: Logger): F[Unit]
  def warn(msg: => String)(implicit ev: Logger): F[Unit]
  def warn(msg: => String, cause: Throwable)(implicit ev: Logger): F[Unit]
  def error(msg: => String)(implicit ev: Logger): F[Unit]
  def error(msg: => String, cause: Throwable)(implicit ev: Logger): F[Unit]
}

object Log extends LogInstances {
  def apply[F[_]](implicit L: Log[F]): Log[F] = L

  class NOPLog[F[_]: Applicative] extends Log[F] {
    def isTraceEnabled(implicit ev: Logger): F[Boolean]                       = false.pure[F]
    def trace(msg: => String)(implicit ev: Logger): F[Unit]                   = ().pure[F]
    def debug(msg: => String)(implicit ev: Logger): F[Unit]                   = ().pure[F]
    def info(msg: => String)(implicit ev: Logger): F[Unit]                    = ().pure[F]
    def warn(msg: => String)(implicit ev: Logger): F[Unit]                    = ().pure[F]
    def warn(msg: => String, cause: Throwable)(implicit ev: Logger): F[Unit]  = ().pure[F]
    def error(msg: => String)(implicit ev: Logger): F[Unit]                   = ().pure[F]
    def error(msg: => String, cause: Throwable)(implicit ev: Logger): F[Unit] = ().pure[F]
  }

  // FunctorK
  implicit class LogMapKOps[F[_]](val log: Log[F]) extends AnyVal {
    def mapK[G[_]](nt: F ~> G): Log[G] = new Log[G] {
      override def isTraceEnabled(implicit ev: Logger): G[Boolean]                       = nt(log.isTraceEnabled)
      override def trace(msg: => String)(implicit ev: Logger): G[Unit]                   = nt(log.trace(msg))
      override def debug(msg: => String)(implicit ev: Logger): G[Unit]                   = nt(log.debug(msg))
      override def info(msg: => String)(implicit ev: Logger): G[Unit]                    = nt(log.info(msg))
      override def warn(msg: => String)(implicit ev: Logger): G[Unit]                    = nt(log.warn(msg))
      override def warn(msg: => String, cause: Throwable)(implicit ev: Logger): G[Unit]  =
        nt(log.warn(msg, cause))
      override def error(msg: => String)(implicit ev: Logger): G[Unit]                   = nt(log.error(msg))
      override def error(msg: => String, cause: Throwable)(implicit ev: Logger): G[Unit] =
        nt(log.error(msg, cause))
    }
  }
}

sealed abstract class LogInstances {

  def log[F[_]: Sync]: Log[F] = new Log[F] {

    def isTraceEnabled(implicit ev: Logger): F[Boolean]                       =
      Sync[F].delay(ev.underlying.isTraceEnabled())
    def trace(msg: => String)(implicit ev: Logger): F[Unit]                   =
      Sync[F].delay(ev.trace(msg))
    def debug(msg: => String)(implicit ev: Logger): F[Unit]                   =
      Sync[F].delay(ev.debug(msg))
    def info(msg: => String)(implicit ev: Logger): F[Unit]                    =
      Sync[F].delay(ev.info(msg))
    def warn(msg: => String)(implicit ev: Logger): F[Unit]                    =
      Sync[F].delay(ev.warn(msg))
    def warn(msg: => String, cause: Throwable)(implicit ev: Logger): F[Unit]  =
      Sync[F].delay(ev.warn(msg, cause))
    def error(msg: => String)(implicit ev: Logger): F[Unit]                   =
      Sync[F].delay(ev.error(msg))
    def error(msg: => String, cause: Throwable)(implicit ev: Logger): F[Unit] =
      Sync[F].delay(ev.error(msg, cause))
  }

  val logId: Log[Id] = log[Id]
}
