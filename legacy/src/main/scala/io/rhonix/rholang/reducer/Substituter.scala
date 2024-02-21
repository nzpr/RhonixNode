package io.rhonix.rholang.reducer

import io.rhonix.rholang.types.*

trait Substituter[F[_]] {
  def substitute(proc: ParN): F[ParN]
}

object Substituter {
  def apply[F[_]](implicit instance: Substituter[F]): instance.type = instance
}
