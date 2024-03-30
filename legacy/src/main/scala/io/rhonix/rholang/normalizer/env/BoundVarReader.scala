package io.rhonix.rholang.normalizer.env

trait BoundVarReader[T] {

  /**
   * Find bound variable across variables of current (topmost) nesting level.
   *
   * @param name variable name.
   * @return bound variable or None.
   */
  def getBoundVar(name: String): Option[VarContext[T]]

  /**
   * Find bound variable across variables of all nesting levels.
   * @param name variable name.
   * @return bound variable or None .
   */
  def findBoundVar(name: String): Option[VarContext[T]]

  /**
   * Get next de Bruijn index.
   * @return index that will be used for the creation of the next bound variable within the current scope.
   */
  def getNextIndex: Int
}

object BoundVarReader {
  def apply[T](implicit instance: BoundVarReader[T]): instance.type = instance
}
