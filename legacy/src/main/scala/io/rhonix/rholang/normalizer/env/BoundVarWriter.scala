package io.rhonix.rholang.normalizer.env

import coop.rchain.rholang.interpreter.compiler.IdContext

trait BoundVarWriter[T] {

  /**
   * Add bound variables.
   *
   * @param bindings sequence of [[IdContext]].
   * @return indices of bound variables in bounds map that result in application of bindings.
   *         NOTE: if binding tries to bound a variable that is already present in the bounds map (shadows it),
   *         it's index is not included in the output.
   */
  def putBoundVars(bindings: Seq[IdContext[T]]): Seq[Int]
}

object BoundVarWriter {
  def apply[T](implicit instance: BoundVarWriter[T]): BoundVarWriter[T] = instance
}
