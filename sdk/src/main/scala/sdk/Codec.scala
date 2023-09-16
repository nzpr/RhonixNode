package sdk
import cats.syntax.all.*

import scala.util.{Success, Try}

/// Codec that can fail
trait Codec[A, B] {
  def encode(x: A): Try[B]
  def decode(x: B): Try[A]
}

object Codec {
  def Identity[A]: Codec[A, A] = new Codec[A, A] {
    override def encode(x: A): Try[A] = Success(x)
    override def decode(x: A): Try[A] = Success(x)
  }
}
