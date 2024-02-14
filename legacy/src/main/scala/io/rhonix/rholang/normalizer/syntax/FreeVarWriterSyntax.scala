package io.rhonix.rholang.normalizer.syntax

import coop.rchain.rholang.interpreter.compiler.*
import io.rhonix.rholang.normalizer.env.FreeVarWriter

import java.util.UUID

trait FreeVarWriterSyntax {
  implicit def normalizerSyntaxFreeVarWriter[T](x: FreeVarWriter[T]): FreeVarWriterOps[T] =
    new FreeVarWriterOps(x)
}

final class FreeVarWriterOps[T](private val x: FreeVarWriter[T]) extends AnyVal {

  /**
   * Creates a new free variable.
   *
   * This method generates a unique identifier for a new variable of type `T`,
   * adds it to the list of free variables in the current scope, and returns
   * the De Bruijn index of the newly created variable.
   *
   * @param typeVar the type of the new variable
   * @return the De Bruijn index of the newly created variable
   */
  def createFreeVar(typeVar: T): Int = x.putFreeVar(createUniqueVarData(typeVar))
}
