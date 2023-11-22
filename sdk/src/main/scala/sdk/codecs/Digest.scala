package sdk.codecs

import sdk.primitive.ByteArray

// Type class to provide a digest of a value. Usually it is the hash of a serialized value.
trait Digest[A] {
  def digest(x: A): ByteArray
}

object Digest {
  def apply[A](implicit ev: Digest[A]): Digest[A] = ev
}
