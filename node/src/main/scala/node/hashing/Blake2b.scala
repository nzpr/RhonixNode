package node.hashing

import org.bouncycastle.crypto.digests.Blake2bDigest

/**
 * Blake2b hashing algorithm
 */
object Blake2b {
  val Hash256LengthBytes = 32

  /**
   * Calculates Blake2b 256 bit hash
   *
   * @param input Bytes to hash
   * @return Calculated hash as bytes
   */
  def hash256(input: Array[Byte]): Array[Byte] = {
    val digestFn = new Blake2bDigest(Hash256LengthBytes * 8)
    digestFn.update(input, 0, input.length)
    val res      = new Array[Byte](Hash256LengthBytes)
    val length   = digestFn.doFinal(res, 0)
    require(
      length == Hash256LengthBytes,
      s"Unexpected calculated hash length `$length`, expected `$Hash256LengthBytes`.",
    )
    res
  }
}
