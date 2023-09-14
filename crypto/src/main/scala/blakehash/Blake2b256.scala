package blakehash

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.io.DigestOutputStream

import java.io.OutputStream

/**
 * Blake2b256 hashing algorithm
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
object Blake2b256 {
  val hashLength = 32

  def hash(input: Array[Byte]): Array[Byte] = {
    val digestFn = new Blake2bDigest(hashLength * 8)
    digestFn.update(input, 0, input.length)
    val res      = new Array[Byte](hashLength)
    digestFn.doFinal(res, 0)
    res
  }

  /**
   * Constructs a Blake2b256 from sequence of T, that will be hashed as a single concatenated bytes stream.
   * @param copyToStream function which turns T into a sequence of bytes and writes this sequence to OutputStream
   */
  def hash[T](inputs: T*)(copyToStream: (T, OutputStream) => Unit): Array[Byte] = {
    val outStream = new DigestOutputStream(new Blake2bDigest(256))
    for (input <- inputs)
      copyToStream(input, outStream)
    outStream.getDigest
    // no calls to .close() since
    // DigestOutputStream doesn't use any closeable resources
  }
}
