package sdk
import cats.syntax.all.*

/// Codec that can fail
trait Codec[A, B, Err] {
  def encode(x: A): Either[Err, B]
  def decode(x: B): Either[Err, A]
}

object Codec {
  def Identity[A, Err]: Codec[A, A, Err] = new Codec[A, A, Err] {
    override def encode(x: A): Either[Err, A] = x.asRight[Err]
    override def decode(x: A): Either[Err, A] = x.asRight[Err]
  }
}
