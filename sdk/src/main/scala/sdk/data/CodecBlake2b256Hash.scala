package sdk.data

import cats.Applicative
import cats.syntax.all.*
import scodec.bits.BitVector

class CodecBlake2b256Hash[F[_]: Applicative] extends Codec[F, Blake2b256Hash] {
  def encode(x: Blake2b256Hash): F[BitVector] = x.bytes.toBitVector.pure
  def decode(x: BitVector): F[Blake2b256Hash] = Blake2b256Hash.fromByteVector(x.toByteVector).pure
}
object CodecBlake2b256Hash {
  def apply[F[_]: Applicative]: CodecBlake2b256Hash[F] = new CodecBlake2b256Hash()
}
