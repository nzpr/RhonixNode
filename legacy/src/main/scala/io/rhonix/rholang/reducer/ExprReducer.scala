package io.rhonix.rholang.reducer

import io.rhonix.rholang.types.ParN

trait ExprReducer[F[_]] {
  def reduceAsExpr(proc: ParN): F[ParN]
}

object ExprReducer {
  def apply[F[_]](implicit instance: ExprReducer[F]): instance.type = instance
}
