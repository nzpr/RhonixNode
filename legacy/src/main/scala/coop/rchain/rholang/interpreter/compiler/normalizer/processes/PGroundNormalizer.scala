package coop.rchain.rholang.interpreter.compiler.normalizer.processes

import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.rholang.ParN
import io.rhonix.rholang.ast.rholang.Absyn.PGround
import coop.rchain.rholang.interpreter.compiler.normalizer.GroundNormalizeMatcher
import coop.rchain.rholang.interpreter.compiler.{ProcVisitInputs, ProcVisitOutputs}

object PGroundNormalizer {
  def normalize[F[_]: Sync](p: PGround, input: ProcVisitInputs): F[ProcVisitOutputs] =
    GroundNormalizeMatcher
      .normalizeMatch[F](p.ground_)
      .map(expr =>
        ProcVisitOutputs(
          ParN.combine(input.par, expr),
          input.freeMap,
        ),
      )
}
