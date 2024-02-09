package io.rhonix.rholang.normalizer.env

import coop.rchain.rholang.interpreter.compiler.BoundContext

trait BoundVarReader[T] {

  /**
   * Find bound variable across variables of current (topmost) nesting level.
   * @param name variable name.
   * @return bound variable or None.
   */
  def getBoundVar(name: String): Option[BoundContext[T]]

  /**
   * Find bound variable across variables of all nesting levels.
   * @param name variable name.
   * @return bound variable with nesting level or None .
   */
  def findBoundVar(name: String): Option[(BoundContext[T], Int)]
}

object BoundVarReader {
  def apply[T](implicit instance: BoundVarReader[T]): BoundVarReader[T] = instance
}
