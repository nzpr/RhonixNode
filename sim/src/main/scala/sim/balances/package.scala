package sim

import sdk.codecs.Codec
import sdk.history.{ByteArray32, KeySegment}
import sdk.primitive.ByteArray
import sdk.syntax.all.sdkSyntaxTry

import java.nio.ByteBuffer
import scala.util.Try

package object balances {

  // types for data stored in the state
  type Wallet  = Int
  type Balance = Long

  private def longToArray(x: Long): Array[Byte] =
    ByteBuffer.allocate(java.lang.Long.BYTES).putLong(x).array()

  private def intToArray(x: Int): Array[Byte] =
    ByteBuffer.allocate(java.lang.Integer.BYTES).putInt(x).array()

  private val walletCodec: Codec[Wallet, ByteArray] = new Codec[Wallet, ByteArray] {
    override def encode(x: Wallet): Try[ByteArray] = Try(ByteArray(intToArray(x)))
    override def decode(x: ByteArray): Try[Wallet] = Try(ByteBuffer.wrap(x.bytes).getInt())
  }

  val balanceCodec: Codec[Balance, ByteArray] = new Codec[Balance, ByteArray] {
    override def encode(x: Balance): Try[ByteArray] = Try(ByteArray(longToArray(x)))
    override def decode(x: ByteArray): Try[Balance] = Try(ByteBuffer.wrap(x.bytes).getLong)
  }

  def balanceToHash(balance: Balance)(implicit hash32: Array[Byte] => ByteArray32): ByteArray32 =
    hash32(longToArray(balance))

  def walletToKeySegment(wallet: Wallet): KeySegment = KeySegment(walletCodec.encode(wallet).getUnsafe)
}
