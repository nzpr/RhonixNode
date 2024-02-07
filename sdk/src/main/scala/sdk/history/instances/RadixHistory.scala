package sdk.history.instances

import cats.Parallel
import cats.effect.Sync
import cats.syntax.all.*
import sdk.codecs.Codec
import sdk.history.RadixTree.*
import sdk.history.{ByteArray32, History, HistoryAction, KeySegment, RadixTree}
import sdk.primitive.ByteArray
import sdk.store.{KeyValueStore, KeyValueTypedStore}
import sdk.syntax.all.*

/**
  * History implementation with radix tree
  */
object RadixHistory {
  def EmptyRootHash(implicit hash32: Array[Byte] => ByteArray32): ByteArray32 = RadixTree.EmptyRootHash

  def kCodec: Codec[ByteArray32, ByteArray] = ByteArray32.codec
  def vCodec: Codec[ByteArray, ByteArray]   = Codec.Identity[ByteArray]

  def apply[F[_]: Sync: Parallel](
    root: ByteArray32,
    store: KeyValueTypedStore[F, ByteArray32, ByteArray],
  )(implicit hash32: Array[Byte] => ByteArray32): F[RadixHistory[F]] =
    for {
      impl <- Sync[F].delay(new RadixTreeImpl[F](store))
      node <- impl.loadNode(root, noAssert = true)
    } yield RadixHistory(root, node, impl, store)

  def createStore[F[_]: Sync](
    store: KeyValueStore[F],
  ): KeyValueTypedStore[F, ByteArray32, ByteArray] =
    store.toByteArrayTypedStore(kCodec, vCodec)
}

final case class RadixHistory[F[_]: Sync: Parallel](
  rootHash: ByteArray32,
  rootNode: Node,
  impl: RadixTreeImpl[F],
  store: KeyValueTypedStore[F, ByteArray32, ByteArray],
)(implicit hash32: Array[Byte] => ByteArray32)
    extends History[F] {
  override def root: ByteArray32 = rootHash

  override def reset(root: ByteArray32): F[History[F]] =
    for {
      impl <- Sync[F].delay(new RadixTreeImpl[F](store))
      node <- impl.loadNode(root, noAssert = true)
    } yield this.copy(root, node, impl, store)

  override def read(key: KeySegment): F[Option[ByteArray32]] =
    impl.read(rootNode, key)

  override def process(actions: List[HistoryAction]): F[History[F]] =
    for {

      /** TODO: To improve time, it is possible to implement this check into the [[RadixTreeImpl.saveAndCommit()]]. */
      _ <- new RuntimeException("Cannot process duplicate actions on one key.").raiseError
             .unlessA(hasNoDuplicates(actions))

      newRootDataOpt <- impl.saveAndCommit(rootNode, actions)
      newHistoryOpt   = newRootDataOpt.map { newRootData =>
                          val (newRootNode, newRootHash) = newRootData
                          this.copy(newRootHash, newRootNode, impl, store)
                        }
      _               = impl.clearReadCache()
    } yield newHistoryOpt.getOrElse(this)

  private def hasNoDuplicates(actions: List[HistoryAction]) =
    actions.map(_.key).distinct.sizeIs == actions.size
}
