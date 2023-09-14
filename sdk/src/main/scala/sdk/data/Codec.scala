package sdk.data

import scodec.bits.BitVector

trait Codec[F[_], A] {
  def encode(x: A): F[BitVector]
  def decode(x: BitVector): F[A]
}
