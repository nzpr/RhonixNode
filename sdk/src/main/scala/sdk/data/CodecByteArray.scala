package sdk.data

import cats.Applicative
import cats.syntax.all.*
import sdk.Codec

class CodecByteArray[F[_]: Applicative] extends Codec[F, ByteArray] {
  def encode(x: ByteArray): F[ByteArray] = x.pure
  def decode(x: ByteArray): F[ByteArray] = x.pure
}
object CodecByteArray {
  def apply[F[_]: Applicative]: CodecByteArray[F] = new CodecByteArray()
}
