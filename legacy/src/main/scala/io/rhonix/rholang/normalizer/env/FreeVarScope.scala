package io.rhonix.rholang.normalizer.env

trait FreeVarScope[F[_]] {

  /** Run function within an empty free variables scope (preserving history).
   * @tparam R The type of the result produced by the function `scopeFn`.
   * @param scopeFn The function to be executed within the new scope of free variables.
   * @param startIndex The de Bruijn index to be used for the first free variable created within the new scope. Defaults to 0.
   * @return The result of executing the function `scopeFn` within the new scope of free variables.
   */
  def withNewFreeVarScope[R](scopeFn: F[R], startIndex: Int = 0): F[R]
}

object FreeVarScope {
  def apply[F[_]](implicit instance: FreeVarScope[F]): FreeVarScope[F] = instance
}
