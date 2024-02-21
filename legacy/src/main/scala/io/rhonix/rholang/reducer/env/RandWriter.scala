package io.rhonix.rholang.reducer.env

import sdk.history.ByteArray32

trait RandWriter[F[_]] {
  def withMergingRands[R](fn: F[R], rands: Seq[ByteArray32]): F[R]
  def withNewRand[R](fn: F[R]): F[R]
  def withSplitRand[R](fn: F[R], index: Short): F[R]
}

object RandWriter {
  def apply[F[_]](implicit instance: RandWriter[F]): RandWriter[F] = instance
}
