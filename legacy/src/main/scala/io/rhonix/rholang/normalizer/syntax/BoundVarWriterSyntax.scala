package io.rhonix.rholang.normalizer.syntax

import coop.rchain.rholang.interpreter.compiler.*
import io.rhonix.rholang.normalizer.env.BoundVarWriter

trait BoundVarWriterSyntax {
  implicit def normalizerSyntaxBoundVarWriter[T](x: BoundVarWriter[T]): BoundVarWriterOps[T] =
    new BoundVarWriterOps(x)
}

final class BoundVarWriterOps[T](private val x: BoundVarWriter[T]) extends AnyVal {

  /**
   * Creates a new bound variable.
   *
   * This method generates a unique identifier for a new variable of type `T`,
   * adds it to the list of bound variables in the current scope, and returns
   * the index of the newly created variable in the list.
   *
   * @param typeVar the type of the new variable
   * @return the index of the newly created variable in the list of bound variables
   */
  def createBoundVar(typeVar: T): Int = x.putBoundVars(Seq(createUniqueVarData(typeVar))).head
}
