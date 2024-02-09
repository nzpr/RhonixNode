package io.rhonix.rholang.normalizer.env

import coop.rchain.rholang.interpreter.compiler.IdContext

trait FreeVarWriter[T] {

  /** 
   * Add free variable.
   * @return De Bruijn index of the variable added.
   * */
  def putFreeVar(binding: IdContext[T]): Int
}

object FreeVarWriter {
  def apply[T](implicit instance: FreeVarWriter[T]): FreeVarWriter[T] = instance
}
