package coop.rchain.rholang.interpreter

import java.util.UUID

package object compiler {

  /** A tuple of a variable name, its type and source position. */
  type IdContext[T] = (String, T, SourcePosition)

  /** Generate unique data for new variable creation. */
  def createUniqueVarData[T](typeVars: T): IdContext[T] =
    (UUID.randomUUID().toString, typeVars, SourcePosition(0, 0))
}
