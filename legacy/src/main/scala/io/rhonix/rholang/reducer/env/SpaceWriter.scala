package io.rhonix.rholang.reducer.env

import io.rhonix.rholang.reducer.env.SpaceWriter.*
import io.rhonix.rholang.types.{ParN, ReceiveN, SendN}
import sdk.history.ByteArray32

trait SpaceWriter[F[_]] {
  def writeProduce(produce: SendN): F[Option[SpaceWritingResult]]
  def writeConsume(consume: ReceiveN): F[Option[SpaceWritingResult]]
}

object SpaceWriter {
  def apply[F[_]](implicit instance: SpaceWriter[F]): SpaceWriter[F] = instance

  final case class SpaceWritingResult(
    continuationWithRand: (ParN, ByteArray32),
    environmentsWithRands: Seq[(Seq[ParN], ByteArray32)],
    persistent: Boolean,
    peek: Boolean,
  )
}
