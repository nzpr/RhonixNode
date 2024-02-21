package io.rhonix.rholang.reducer

import io.rhonix.rholang.ast.rholang.Absyn.*
import io.rhonix.rholang.types.{ParN, VarN}

trait RecReducer[F[_]] {
  def reduce(proc: ParN): F[Unit]
}

object RecReducer {
  def apply[F[_]](implicit instance: RecReducer[F]): instance.type = instance
}
