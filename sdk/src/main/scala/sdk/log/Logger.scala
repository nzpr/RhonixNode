package sdk.log

import java.time.LocalDateTime

trait Logger {
  def info(msg: Any): Unit
}

object Logger {
  def console: Logger = new Logger {
    override def info(msg: Any): Unit = {
      val date = LocalDateTime.now()
      val time = date.toLocalTime
      println(s"[$time] $msg")
    }
  }

  def empty: Logger = new Logger {
    override def info(msg: Any): Unit = ()
  }
}
