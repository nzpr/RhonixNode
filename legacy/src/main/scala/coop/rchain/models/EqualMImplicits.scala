package coop.rchain.models

import cats.effect.Sync
import cats.instances.stream.*
import cats.syntax.all.*
import com.google.protobuf.ByteString
import coop.rchain.catscontrib.Catscontrib.*
import coop.rchain.crypto.hash.Blake2b512Random
import coop.rchain.crypto.signatures.Signed
import cats.Eval
import coop.rchain.models.EqualM.*
import magnolia.Magnolia.gen

import scala.Function.tupled
import scala.collection.immutable.BitSet

object EqualMImplicits {

  implicit val IntEqual: EqualM[Int]                           = opaqueEqual
  implicit val BigIntEqual: EqualM[BigInt]                     = opaqueEqual
  implicit val FloatEqual: EqualM[Float]                       = opaqueEqual
  implicit val LongEqual: EqualM[Long]                         = opaqueEqual
  implicit val DoubleEqual: EqualM[Double]                     = opaqueEqual
  implicit val StringEqual: EqualM[String]                     = opaqueEqual
  implicit val BooleanEqual: EqualM[Boolean]                   = opaqueEqual
  implicit val BitSetEqual: EqualM[BitSet]                     = opaqueEqual
  implicit val ByteEqual: EqualM[Byte]                         = opaqueEqual
  implicit val ByteStringEqual: EqualM[ByteString]             = opaqueEqual
  implicit val Blake2b512RandomEqual: EqualM[Blake2b512Random] = opaqueEqual
  implicit def alwaysEqualEqual[A]: EqualM[AlwaysEqual[A]]     = opaqueEqual

  implicit def seqEqual[A: EqualM]: EqualM[Seq[A]] = new EqualM[Seq[A]] {

    override def equal[F[_]: Sync](self: Seq[A], other: Seq[A]): F[Boolean] = {
      val pairs = self.to(LazyList).zip(other)
      Sync[F].delay(self.lengthIs == other.length) &&^
        pairs.forallM(tupled(EqualM[A].equal[F]))
    }

  }

  implicit def arrayEqual[A: EqualM]: EqualM[Array[A]] = new EqualM[Array[A]] {

    override def equal[F[_]: Sync](self: Array[A], other: Array[A]): F[Boolean] = {
      val pairs = self.to(LazyList).zip(other)
      Sync[F].delay(self.length == other.length) &&^
        pairs.forallM(tupled(EqualM[A].equal[F]))
    }

  }

  implicit def mapEqual[A: EqualM, B: EqualM]: EqualM[Map[A, B]] = new EqualM[Map[A, B]] {

    override def equal[F[_]: Sync](self: Map[A, B], other: Map[A, B]): F[Boolean] = {
      val pairsA = self.keys.to(LazyList).zip(other.keys)
      val pairsB = self.values.to(LazyList).zip(other.values)
      Sync[F].delay(self.sizeIs == other.size) &&^ pairsA
        .forallM(tupled(EqualM[A].equal[F])) &&^ pairsB.forallM(tupled(EqualM[B].equal[F]))
    }

  }

  implicit def EvalEqual[A: EqualM]: EqualM[Eval[A]] = by(_.value)

  implicit val ParEqual: EqualM[Par] = gen[Par]
  implicit val ExprEqual             = gen[Expr]
  implicit val VarEqual              = gen[Var]
  implicit val SendEqual             = gen[Send]
  implicit val ReceiveEqual          = gen[Receive]
  implicit val ReceiveBindEqual      = gen[ReceiveBind]
  implicit val NewEqual              = gen[New]
  implicit val MatchEqual            = gen[Match]

  implicit val ConnectiveEqual = gen[Connective]
//  implicit def SignedEqual[A: EqualM] = new EqualM[Signed[A]] {
//    override def equal[F[_]: Sync](self: Signed[A], other: Signed[A]): F[Boolean] =
//      if (self.sigAlgorithm == other.sigAlgorithm && self.sig == other.sig)
//        EqualM[A].equal(self.data, other.data)
//      else Sync[F].pure(false)
//  }

  implicit val ESetEqual = gen[ESet]
  implicit val EMapEqual = gen[EMap]

  implicit val SortedParHashSetEqual: EqualM[SortedParHashSet] = by(_.sortedPars)
  implicit val SortedParMapEqual: EqualM[SortedParMap]         = by(_.sortedList)

  implicit val ParSetEqual: EqualM[ParSet] = by(x => (x.ps, x.remainder, x.connectiveUsed))
  implicit val ParMapEqual: EqualM[ParMap] = by(x => (x.ps, x.remainder, x.connectiveUsed))

//  implicit val BlockInfoHash                  = gen[BlockInfo]
//  implicit val LightBlockInfoHash             = gen[LightBlockInfo]
//  implicit val BondInfo                       = gen[BondInfo]
//  implicit val DeployInfo                     = gen[DeployInfo]
//  implicit val ContinuationsWithBlockInfoHash = gen[ContinuationsWithBlockInfo]
//  implicit val DataWithBlockInfoHash          = gen[DataWithBlockInfo]
//  implicit val WaitingContinuationInfoHash    = gen[WaitingContinuationInfo]
//  implicit val BlockQueryByHeightHash         = gen[BlocksQueryByHeight]
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
}
