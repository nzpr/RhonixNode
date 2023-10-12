package sdk.crypto

/** Splittable pseudorandom number generator. */
trait SplittablePrng {
  def fromSeed[A](seed: A): SplittablePrng
  def splitByte(index: Byte): SplittablePrng
  def splitShort(index: Short): SplittablePrng
  def next: Array[Byte]
  def copy: SplittablePrng
}
