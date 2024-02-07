package coop.rchain.shared

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait LogSource {
  val clazz: Class[_]
}

object LogSource {
  def apply(c: Class[_]): LogSource = new LogSource {
    val clazz: Class[_] = c
  }

  @SuppressWarnings(Array("org.wartremover.warts.Null")) // false-positive
  implicit def matLogSource: LogSource = macro LogSourceMacros.mkLogSource
}

class LogSourceMacros(val c: blackbox.Context) {
  import c.universe.*

  def mkLogSource: c.Expr[LogSource] = {
    val tree =
      q"""
          coop.rchain.shared.LogSource(${c.reifyEnclosingRuntimeClass}.asInstanceOf[Class[_]])
       """

    c.Expr[LogSource](tree)
  }
}
