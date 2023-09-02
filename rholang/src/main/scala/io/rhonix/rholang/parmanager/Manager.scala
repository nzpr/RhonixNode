package io.rhonix.rholang.parmanager

import cats.Eval
import com.google.protobuf.CodedOutputStream
import io.rhonix.rholang._
import io.rhonix.rholang.parmanager.Constants.hashSize
import io.rhonix.rholang.parmanager.blake2.Blake2Hash
import io.rhonix.rholang.parmanager.protobuf.{ProtoCodec, ProtoPrimitiveReader, ProtoPrimitiveWriter}

import java.io.InputStream

object Manager {

  def equals(self: RhoTypeN, other: Any): Boolean = other match {
    case x: RhoTypeN => x.rhoHash.value sameElements self.rhoHash.value
    case _           => false
  }

  def sortPars(ps: Seq[ParN]): Seq[ParN]                                    = Sorting.sortPars(ps)
  def sortBinds(bs: Seq[ReceiveBindN]): Seq[ReceiveBindN]                   = Sorting.sortBinds(bs)
  def sortBindsWithT[T](bs: Seq[(ReceiveBindN, T)]): Seq[(ReceiveBindN, T)] =
    Sorting.sortBindsWithT(bs)
  def sortUris(uris: Seq[String]): Seq[String]                              = Sorting.sortUris(uris)
  def sortInjections(injections: Map[String, ParN]): Seq[(String, ParN)]    =
    Sorting.sortInjections(injections)
  def comparePars(p1: ParN, p2: ParN): Int                                  = Sorting.comparePars(p1, p2)

  private def flatPs(ps: Seq[ParN]): Seq[ParN] =
    ps.flatMap {
      case _: NilN.type => Seq()
      case x: ParProcN  => flatPs(x.ps)
      case p            => Seq(p)
    }

  private def makePProc(ps: Seq[ParN]): ParN = ps match {
    case Nil      => NilN
    case p :: Nil => p
    case _        => ParProcN(ps)
  }

  /**
    * Create a flatten parallel Par (ParProc) from par sequence
    * Flatting is the process of transforming ParProc(P, Q, ...):
    * - empty data:  ParProc()  -> Nil
    * - single data: ParProc(P) -> P
    * - nil data:    ParProc(P, Q, Nil) -> ParProc(P, Q)
    * - nested data  ParProc(ParProc(P,Q), ParProc(L,K)) -> ParProc(P, Q, L, K)
    * @param ps initial par sequence to be executed in parallel
    * @return
    */
  def flattedPProc(ps: Seq[ParN]): ParN = makePProc(flatPs(ps))

  /**
    * Create a flatten parallel Par (ParProc) from two Pars.
    * See [[flattedPProc]] for more information.
    */
  def combinePars(p1: ParN, p2: ParN): ParN = flattedPProc(Seq(p1, p2))

  /** MetaData */
  def rhoHashFn(p: RhoTypeN): Eval[Array[Byte]]                              = {
    val write                                                                                   = (out: CodedOutputStream) => RhoHash.serializeForHash(p, ProtoPrimitiveWriter(out))
    // Return data padded with zero from left if its size less then the limit ot hash of the data
    def padOrHash(data: Array[Byte], limit: Int, hash: Array[Byte] => Array[Byte]): Array[Byte] = {
      val pad = limit - data.length
      if (pad <= 0) hash(data) else Array.concat(data, Array.fill(pad)(0.toByte))
    }
    // The size of the byte stream here (payloadSize) will be the serialized primitive types or collection of
    // hashes from nested objects. The size is growing by doubling so as to minimize resizing overhead 512 bytes
    // set here.
    ProtoCodec.encode(512, write).map(padOrHash(_, hashSize, Blake2Hash.hash))
  }
  def serializedSizeFn(p: RhoTypeN): Eval[Int]                               = SerializedSize.calcSerSize(p)
  def serializedFn(p: RhoTypeN, memoizeChildren: Boolean): Eval[Array[Byte]] = {
    val write = (out: CodedOutputStream) => Serialization.serialize(p, ProtoPrimitiveWriter(out), memoizeChildren)
    p.serializedSize.flatMap(size => ProtoCodec.encode(size, write))
  }
  def connectiveUsedFn(p: RhoTypeN): Eval[Boolean]                           = ConnectiveUsed.connectiveUsedFn(p)
  def evalRequiredFn(p: RhoTypeN): Boolean                                   = EvalRequired.evalRequiredFn(p)
  def substituteRequiredFn(p: RhoTypeN): Boolean                             = SubstituteRequired.substituteRequiredFn(p)

  // Deserialize with protobuf
  def protoDeserialize(bytes: Array[Byte]): ParN = {
    val decode = (in: InputStream) => Serialization.deserialize(ProtoPrimitiveReader(in))
    ProtoCodec.decode(bytes, decode).value
  }
}
