package sdk.data

trait Codec[F[_], A] {
  def encode(x: A): F[ByteArray]
  def decode(x: ByteArray): F[A]
}
