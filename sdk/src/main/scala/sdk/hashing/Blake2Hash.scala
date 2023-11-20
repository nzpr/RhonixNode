package sdk.hashing

import org.bouncycastle.crypto.digests.Blake2bDigest

object Blake2Hash {
  final private val HashSize = 32

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  def hash(input: Array[Byte]): Array[Byte] = {
    val digestFn = new Blake2bDigest(HashSize * 8)
    digestFn.update(input, 0, input.length)
    val res      = new Array[Byte](HashSize)
    digestFn.doFinal(res, 0)
    res
  }
}
