package sim.balances

import cats.Eval
import dproc.data.Block
import node.hashing.Blake2b
import sdk.codecs.Digest
import sdk.codecs.protobuf.ProtoPrimitiveWriter
import sdk.primitive.ByteArray
import sim.NetworkSim.*
import data.BalancesDeployBody

object Hashing {
  implicit val balancesDeployBodyDigest: Digest[BalancesDeployBody] =
    new sdk.codecs.Digest[BalancesDeployBody] {
      override def digest(x: BalancesDeployBody): ByteArray = {
        val bytes = ProtoPrimitiveWriter.encodeWith(Serialization.balancesDeployBodySerialize[Eval].write(x))
        ByteArray(Blake2b.hash256(bytes.value))
      }
    }

  implicit val blockBodyDigest: Digest[Block[M, S, T]] =
    new sdk.codecs.Digest[Block[M, S, T]] {
      override def digest(x: Block[M, S, T]): ByteArray = {
        val bytes = ProtoPrimitiveWriter.encodeWith(Serialization.blockSerialize[Eval].write(x))
        ByteArray(Blake2b.hash256(bytes.value))
      }
    }
}
