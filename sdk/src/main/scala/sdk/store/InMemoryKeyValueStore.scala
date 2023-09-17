package sdk.store

import cats.effect.Sync
import sdk.primitive.ByteArray

import java.nio.ByteBuffer
import scala.collection.concurrent.TrieMap

final case class InMemoryKeyValueStore[F[_]: Sync]() extends KeyValueStore[F] {

  val State: TrieMap[ByteBuffer, ByteArray] = TrieMap[ByteBuffer, ByteArray]()

  override def get[T](keys: Seq[ByteBuffer], fromBuffer: ByteBuffer => T): F[Seq[Option[T]]] =
    Sync[F].delay(
      keys.map(State.get).map(_.map(_.toByteBuffer).map(fromBuffer)),
    )

  override def put[T](kvPairs: Seq[(ByteBuffer, T)], toBuffer: T => ByteBuffer): F[Unit] =
    Sync[F].delay(
      kvPairs
        .foreach { case (k, v) =>
          State.put(k, ByteArray(toBuffer(v)))
        },
    )

  override def delete(keys: Seq[ByteBuffer]): F[Int] =
    Sync[F].delay(keys.map(State.remove).count(_.nonEmpty))

  override def iterate[T](f: Iterator[(ByteBuffer, ByteBuffer)] => T): F[T] =
    Sync[F].delay {
      val iter = State.iterator.map { case (k, v) => (k, v.toByteBuffer) }
      f(iter)
    }

  def clear(): Unit = State.clear()

  def numRecords(): Int = State.size

  def sizeBytes(): Long =
    State.map { case (byteBuffer, byteArray) => byteBuffer.capacity + byteArray.size.toLong }.sum

}
