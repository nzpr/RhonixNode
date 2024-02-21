package io.rhonix.rholang.reducer.env

import coop.rchain.rholang.interpreter.accounting.Cost

trait CostWriter[F[_]] {
  def charge(amount: Cost): F[Unit]
}

object CostWriter {
  def apply[F[_]](implicit instance: CostWriter[F]): CostWriter[F] = instance
}
