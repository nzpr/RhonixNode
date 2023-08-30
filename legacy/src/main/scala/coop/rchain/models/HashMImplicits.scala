package coop.rchain.models

import cats.Contravariant
import cats.effect.Sync
import cats.syntax.all.*
import com.google.protobuf.ByteString
import coop.rchain.crypto.hash.Blake2b512Random
import coop.rchain.crypto.signatures.Signed
import coop.rchain.models.Expr.ExprInstance.{GBigInt, GInt}
import coop.rchain.models.HashM.*
import coop.rchain.models.HashMDerivation.*

import java.util.Objects
import scala.collection.immutable.BitSet

object HashMImplicits {

  implicit val HashMContravariant = new Contravariant[HashM] {
    override def contramap[A, B](fa: HashM[A])(f: B => A): HashM[B] = new HashM[B] {

      override def hash[F[_]: Sync](value: B): F[Int] = fa.hash(f(value))

    }
  }

  implicit val BooleanHash: HashM[Boolean]                   = opaqueHash
  implicit val IntHash: HashM[Int]                           = opaqueHash
  implicit val BigIntHash: HashM[BigInt]                     = opaqueHash
  implicit val FloatHash: HashM[Float]                       = opaqueHash
  implicit val DoubleHash: HashM[Double]                     = opaqueHash
  implicit val StringHash: HashM[String]                     = opaqueHash
  implicit val BitSetHash: HashM[BitSet]                     = opaqueHash
  implicit val ByteHash: HashM[Byte]                         = opaqueHash
  implicit val ByteStringHash: HashM[ByteString]             = opaqueHash
  implicit val Blake2b512RandomHash: HashM[Blake2b512Random] = opaqueHash
  implicit def alwaysEqualHash[A]: HashM[AlwaysEqual[A]]     = opaqueHash

  /** The instance for Long is private, because - for the whole derivation to be consistent with default hashCode
    * for case classes - the instance for Long is made inconsistent with  Long#hashCode. This is because,
    * the default hashCode for case classes uses the .## operator, which is inconsistent with hashCode for Long.
    *
    * The extensive battery of generative tests comparing HashM.hash results with default hashCode
    * seems to agree this is fine though. See HashMSpec.
    */
  implicit private val LongHash: HashM[Long] = new HashM[Long] {

    override def hash[F[_]: Sync](value: Long): F[Int] = Sync[F].pure(value.##)

  }

  implicit def seqHash[A: HashM]: HashM[Seq[A]] = new HashM[Seq[A]] {

    override def hash[F[_]: Sync](value: Seq[A]): F[Int] =
      for {
        hashes <- value.toList.traverse(HashM[A].hash[F])
      } yield hashes.hashCode()

  }

  implicit def arrayHash[A: HashM]: HashM[Array[A]] = new HashM[Array[A]] {

    override def hash[F[_]: Sync](value: Array[A]): F[Int] =
      for {
        hashes <- value.toList.traverse(HashM[A].hash[F])
      } yield hashes.hashCode()

  }

  implicit val Env: HashM[Map[String, Par]] = opaqueHash
  implicit val ParHash: HashM[Par]          = gen[Par]
  implicit val ExprHash                     = gen[Expr]
  implicit val VarHash                      = gen[Var]
  implicit val SendHash                     = gen[Send]
  implicit val ReceiveHash                  = gen[Receive]
  implicit val ReceiveBindHash              = gen[ReceiveBind]
  implicit val NewHash                      = gen[New]
  implicit val MatchHash                    = gen[Match]
  implicit def SignedHash[A: HashM]         = new HashM[Signed[A]] {
    override def hash[F[_]: Sync](value: Signed[A]): F[Int] =
      HashM[A]
        .hash(value.data)
        .map(dataHash => Objects.hash(value.sig, value.sigAlgorithm, Int.box(dataHash)))
  }

  implicit val GIntHash    = gen[GInt] // This is only possible to derive here, b/c LongHashM is private
  implicit val GBigIntHash = gen[GBigInt]

  implicit val ConnectiveHash = gen[Connective]

  implicit val ESetHash = gen[ESet]
  implicit val EMapHash = gen[EMap]

  implicit val SortedParHashSetHash: HashM[SortedParHashSet] = seqHash[Par].contramap(_.sortedPars)
  implicit val SortedParMapHash: HashM[SortedParMap]         = seqHash[(Par, Par)].contramap(_.sortedList)

  implicit val parSetHash: HashM[ParSet] = new HashM[ParSet] {

    override def hash[F[_]: Sync](parSet: ParSet): F[Int] =
      for {
        psHash             <- HashM[SortedParHashSet].hash(parSet.ps)
        remainderHash      <- HashM[Option[Var]].hash(parSet.remainder)
        connectiveUsedHash <- HashM[Boolean].hash(parSet.connectiveUsed)
      } yield Objects.hash(Int.box(psHash), Int.box(remainderHash), Int.box(connectiveUsedHash))

  }

  implicit val parMapHash: HashM[ParMap] = new HashM[ParMap] {

    override def hash[F[_]: Sync](parMap: ParMap): F[Int] =
      for {
        psHash             <- HashM[SortedParMap].hash(parMap.ps)
        remainderHash      <- HashM[Option[Var]].hash(parMap.remainder)
        connectiveUsedHash <- HashM[Boolean].hash(parMap.connectiveUsed)
      } yield Objects.hash(Int.box(psHash), Int.box(remainderHash), Int.box(connectiveUsedHash))

  }

//  implicit val BlockInfoHash                  = gen[BlockInfo]
//  implicit val LightBlockInfoHash             = gen[LightBlockInfo]
//  implicit val BondInfo                       = gen[BondInfo]
//  implicit val DeployInfo                     = gen[DeployInfo]
//  implicit val ContinuationsWithBlockInfoHash = gen[ContinuationsWithBlockInfo]
//  implicit val DataWithBlockInfoHash          = gen[DataWithBlockInfo]
//  implicit val WaitingContinuationInfoHash    = gen[WaitingContinuationInfo]
//  implicit val BlockQueryByHeightHash         = gen[BlocksQueryByHeight]
//  implicit val Status                         = gen[Status]
//
//  implicit val FinalizedFringeHash       = gen[FinalizedFringeProto]
//  implicit val BlockMessageHash          = gen[BlockMessageProto]
//  implicit val BlockMetadataInternalHash = gen[BlockMetadataProto]
//  implicit val BodyHash                  = gen[RholangStateProto]
//  implicit val BondHash                  = gen[BondProto]
//  implicit val DeployDataHash            = gen[DeployDataProto]
//  implicit val ProcessedDeployHash       = gen[ProcessedDeployProto]
//  implicit val ProcessedSystemDeployHash = gen[ProcessedSystemDeployProto]
//  implicit val ReportConsumeProto        = gen[ReportConsumeProto]
  implicit val bindPattern   = gen[BindPattern]
  implicit val parWithRandom = gen[ParWithRandom]

  implicit val PCostHash              = gen[PCost]
  implicit val TaggedContinuationHash = gen[TaggedContinuation]

  // deploy service V1
//  implicit val ContinuationAtNamePayloadV2Hash  = gen[v1.ContinuationAtNamePayload]
//  implicit val BlockResponseV2Hash              = gen[v1.BlockResponse]
//  implicit val BlockInfoResponseV2Hash          = gen[v1.BlockInfoResponse]
//  implicit val ContinuationAtNameResponseV2Hash = gen[v1.ContinuationAtNameResponse]
//  implicit val FindDeployResponseV2Hash         = gen[v1.FindDeployResponse]
//  implicit val LastFinalizedBlockResponseV2Hash = gen[v1.LastFinalizedBlockResponse]
}
