package io.rhonix.rholang.normalizer

import cats.effect.Sync
import cats.syntax.all.*
import io.rhonix.rholang.*
import io.rhonix.rholang.ast.rholang.Absyn.*
import io.rhonix.rholang.interpreter.errors.NormalizerError

object GroundNormalizer {
  def normalizeGround[F[_]: Sync](p: PGround): F[ExprN] = Sync[F].defer {
    p.ground_ match {
      case gb: GroundBool    =>
        gb.boolliteral_ match {
          case _: BoolFalse => Sync[F].pure(GBoolN(false))
          case _: BoolTrue  => Sync[F].pure(GBoolN(true))
        }
      case gi: GroundInt     =>
        Sync[F]
          .delay(gi.longliteral_.toLong)
          .adaptError { case e: NumberFormatException => NormalizerError(e.getMessage) }
          .map(GIntN.apply)
      case gbi: GroundBigInt =>
        Sync[F]
          .delay(BigInt(gbi.longliteral_))
          .adaptError { case e: NumberFormatException => NormalizerError(e.getMessage) }
          .map(GBigIntN.apply)
      case gs: GroundString  => Sync[F].delay(GStringN(stripString(gs.stringliteral_)))
      case gu: GroundUri     => Sync[F].delay(GUriN(stripUri(gu.uriliteral_)))
    }
  }

  // This is necessary to remove the backticks. We don't use a regular
  // expression because they're always there.
  def stripUri(raw: String): String    = {
    require(raw.length >= 2)
    raw.substring(1, raw.length - 1)
  }
  // Similarly, we need to remove quotes from strings, since we are using
  // a custom string token
  def stripString(raw: String): String = {
    require(raw.length >= 2)
    raw.substring(1, raw.length - 1)
  }
}
