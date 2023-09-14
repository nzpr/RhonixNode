package sdk.data

import cats.Applicative
import cats.syntax.all.*
import scodec.bits.{BitVector, ByteVector}

class CodecByteVector[F[_]: Applicative] extends Codec[F, ByteVector] {
  def encode(x: ByteVector): F[BitVector] = x.toBitVector.pure
  def decode(x: BitVector): F[ByteVector] = x.toByteVector.pure
}
object CodecByteVector {
  def apply[F[_]: Applicative]: CodecByteVector[F] = new CodecByteVector()
}
