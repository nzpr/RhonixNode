package sim.execution

import sdk.hashing.Blake2b256Hash
import sdk.history.KeySegment
import sdk.primitive.ByteArray
import sdk.syntax.all.sdkSyntaxTry

import java.nio.ByteBuffer

package object balances {

  // types for data stored in the state
  type Wallet  = Int
  type Balance = Long

  private def longToArray(x: Long): Array[Byte] = {
    val byteBuffer: ByteBuffer = ByteBuffer.allocate(java.lang.Long.BYTES)
    byteBuffer.putLong(x)
    byteBuffer.array()
  }

  private def intToArray(x: Int): Array[Byte] = {
    val byteBuffer: ByteBuffer = ByteBuffer.allocate(java.lang.Integer.BYTES)
    byteBuffer.putInt(x)
    byteBuffer.array()
  }

  // codecs
  def hashToBalance(hash: Blake2b256Hash): Long       =
    ByteBuffer.wrap(Blake2b256Hash.codec.encode(hash).getUnsafe.bytes).getLong
  def balanceToHash(balance: Balance): Blake2b256Hash = Blake2b256Hash(longToArray(balance))
  def walletToKeySegment(wallet: Wallet): KeySegment  = KeySegment(intToArray(wallet))
}
