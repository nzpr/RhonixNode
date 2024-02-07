package sdk.history

import cats.Parallel
import cats.effect.{Async, Sync}
import sdk.history.instances.RadixHistory
import sdk.store.KeyValueStore

/** History definition represents key-value API for RSpace tuple space */
trait History[F[_]] {

  /** Read operation on the Merkle tree */
  def read(key: KeySegment): F[Option[ByteArray32]]

  /** Insert/update/delete operations on the underlying Merkle tree (key-value store) */
  def process(actions: List[HistoryAction]): F[History[F]]

  /** Get the root of the Merkle tree */
  def root: ByteArray32

  /** Returns History with specified root pointer */
  def reset(root: ByteArray32): F[History[F]]
}

object History {
  def EmptyRootHash(implicit hash32: Array[Byte] => ByteArray32): ByteArray32 = RadixHistory.EmptyRootHash

  def create[F[_]: Async: Sync: Parallel](
    root: ByteArray32,
    store: KeyValueStore[F],
  )(implicit hash32: Array[Byte] => ByteArray32): F[RadixHistory[F]] =
    RadixHistory(root, RadixHistory.createStore(store))
}
