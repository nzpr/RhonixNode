package io.rhonix.rholang.normalizer

import cats.effect.Sync
import coop.rchain.rholang.interpreter.compiler.VarSort
import io.rhonix.rholang.ast.rholang.Absyn.*
import io.rhonix.rholang.normalizer.syntax.all.*
import io.rhonix.rholang.types.*

object Normalizer {

  /** Normalizes parser AST types to core Rholang AST types. Entry point of the normalizer.
   *
   * @param proc input parser AST object.
   * @return core Rholang AST object [[ParN]].
   */
  def normalize[F[_]: Sync](proc: Proc): F[ParN] = {
    val norm = new NormalizerRecImpl[F, VarSort]
    import norm.*
    norm.normalize(proc).withNewVarScope
  }
}
