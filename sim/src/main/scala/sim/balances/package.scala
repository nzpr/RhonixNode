package sim

import sdk.codecs.Codec
import sdk.hashing.Blake2b256Hash
import sdk.history.KeySegment
import sdk.primitive.ByteArray
import sdk.syntax.all.sdkSyntaxTry
import sim.balances.data.Datum

import java.nio.ByteBuffer
import scala.util.Try

package object balances {

  // types for data stored in the state
  type Channel = Long
  type Balance = Long

  private def longToArray(x: Long): Array[Byte] =
    ByteBuffer.allocate(java.lang.Long.BYTES).putLong(x).array()

  private val channelCodec: Codec[Channel, ByteArray] = new Codec[Channel, ByteArray] {
    override def encode(x: Channel): Try[ByteArray] = Try(ByteArray(longToArray(x)))
    override def decode(x: ByteArray): Try[Channel] = Try(ByteBuffer.wrap(x.bytes).getInt())
  }

  val datumCodec: Codec[Datum, ByteArray] = new Codec[Datum, ByteArray] {
    override def encode(x: Datum): Try[ByteArray] = Try(
      ByteArray(longToArray(x.balance) ++ longToArray(x.latestNonce)),
    )
    override def decode(x: ByteArray): Try[Datum] = Try {
      val bb = ByteBuffer.wrap(x.bytes)
      Datum(bb.getLong, bb.getLong)
    }
  }

  def datumToHash(datum: Datum): Blake2b256Hash       = Blake2b256Hash(datumCodec.encode(datum).get)
  def walletToKeySegment(wallet: Channel): KeySegment = KeySegment(channelCodec.encode(wallet).getUnsafe)
}
