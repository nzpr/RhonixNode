package coop.rchain.rholang.interpreter

import cats.effect.Sync
import cats.mtl.implicits.*
import cats.syntax.all.*
import coop.rchain.metrics.Span
import coop.rchain.models.*
import coop.rchain.models.Var.VarInstance.FreeVar
import coop.rchain.models.rholang.implicits.*
import coop.rchain.models.serialization.implicits.mkProtobufInstance
import coop.rchain.rholang.interpreter.matcher.*
import coop.rchain.rspace.Match as StorageMatch
import coop.rchain.shared.Serialize

package object storage {

  /* Match instance */
  def matchListPar[F[_]: Sync: Span]: StorageMatch[F, BindPattern, ListParWithRandom, MatchedParsWithRandom] =
    new StorageMatch[F, BindPattern, ListParWithRandom, MatchedParsWithRandom] {
      def get(
        pattern: BindPattern,
        data: ListParWithRandom,
      ): F[Option[MatchedParsWithRandom]] = {
        type R[A] = MatcherMonadT[F, A]
        implicit val matcherMonadError = implicitly[Sync[R]]
        for {
          matchResult <- runFirst[F, Seq[Par]](
                           SpatialMatcher
                             .foldMatch[R, Par, Par](
                               data.pars,
                               pattern.patterns,
                               pattern.remainder,
                             ),
                         )
        } yield matchResult.map { case (freeMap, caughtRem) =>
          val remainderMap = pattern.remainder match {
            case Some(Var(FreeVar(level))) =>
              freeMap + (level -> VectorPar().addExprs(EList(caughtRem.toVector)))
            case _                         => freeMap
          }
          MatchedParsWithRandom(
            remainderMap,
            data.randomState,
          )
        }
      }
    }

  /* Serialize instances */

  implicit val serializeBindPattern: Serialize[BindPattern] =
    mkProtobufInstance(BindPattern)

  implicit val serializePar: Serialize[Par] =
    mkProtobufInstance(Par)

  implicit val serializePars: Serialize[ListParWithRandom] =
    mkProtobufInstance(ListParWithRandom)

  implicit val serializeTaggedContinuation: Serialize[TaggedContinuation] =
    mkProtobufInstance(TaggedContinuation)
}
