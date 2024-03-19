package sdk.diag

import cats.effect.kernel.Sync
import com.typesafe.scalalogging.Logger

import scala.reflect.macros.blackbox

/**
 * Static functions for logging.
 *
 * {{{
 *  com.typesafe.scalalogging.Logger is used as a logging backend.
 *  This object contains also macro expansion to provide log source for typesafe Logger automatically.
 *
 *  Usage: insert the following import statement at the beginning of your file
 *
 *  import sdk.log.Logger.*
 *  
 *  to get access log functions with logger automatically derived.
 * }}}
 * */
object Log {
  def logDebug(msg: String)(implicit scalaLogger: Logger): Unit = scalaLogger.debug(msg)

  def logDebugF[F[_]: Sync](msg: String)(implicit scalaLogger: Logger): F[Unit] = Sync[F].delay(scalaLogger.debug(msg))

  def logInfo(msg: String)(implicit scalaLogger: Logger): Unit = scalaLogger.info(msg)

  def logInfoF[F[_]: Sync](msg: String)(implicit scalaLogger: Logger): F[Unit] = Sync[F].delay(scalaLogger.info(msg))

  def logError(msg: String)(implicit scalaLogger: Logger): Unit = scalaLogger.error(msg)

  def logErrorF[F[_]: Sync](msg: String)(implicit scalaLogger: Logger): F[Unit] = Sync[F].delay(scalaLogger.error(msg))

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  implicit def logger: Logger = macro LogSourceMacros.mkLogger

  private class LogSourceMacros(val c: blackbox.Context) {
    import c.universe.*

    def mkLogger: c.Expr[Logger] = {
      val tree = q"""com.typesafe.scalalogging.Logger.apply(${c.reifyEnclosingRuntimeClass}.asInstanceOf[Class[_]])"""
      c.Expr[Logger](tree)
    }
  }
}
