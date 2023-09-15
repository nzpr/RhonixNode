package sdk.data

import cats.Applicative
import cats.syntax.all.*
import sdk.Codec

class CodecBlake2b256Hash[F[_]: Applicative] extends Codec[F, Blake2b256Hash] {
  def encode(x: Blake2b256Hash): F[ByteArray] = x.bytes.pure
  def decode(x: ByteArray): F[Blake2b256Hash] = Blake2b256Hash.fromByteArray(x).pure
}
object CodecBlake2b256Hash {
  def apply[F[_]: Applicative]: CodecBlake2b256Hash[F] = new CodecBlake2b256Hash()
}
