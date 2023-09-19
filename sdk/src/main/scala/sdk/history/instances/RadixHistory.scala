package sdk.history.instances

import cats.Parallel
import cats.effect.Sync
import cats.syntax.all.*
import sdk.codecs.Codec
import sdk.hashing.Blake2b256Hash
import sdk.history.RadixTree.*
import sdk.history.{History, HistoryAction, KeySegment, RadixTree}
import sdk.primitive.ByteArray
import sdk.store.{KeyValueStore, KeyValueTypedStore}
import sdk.syntax.all.*

/**
  * History implementation with radix tree
  */
object RadixHistory {
  val EmptyRootHash: Blake2b256Hash = RadixTree.EmptyRootHash

  def kCodec: Codec[Blake2b256Hash, ByteArray] = Blake2b256Hash.codec
  def vCodec: Codec[ByteArray, ByteArray]      = Codec.Identity[ByteArray]

  def apply[F[_]: Sync: Parallel](
    root: Blake2b256Hash,
    store: KeyValueTypedStore[F, Blake2b256Hash, ByteArray],
  ): F[RadixHistory[F]] =
    for {
      impl <- Sync[F].delay(new RadixTreeImpl[F](store))
      node <- impl.loadNode(root, noAssert = true)
    } yield RadixHistory(root, node, impl, store)

  def createStore[F[_]: Sync](
    store: KeyValueStore[F],
  ): KeyValueTypedStore[F, Blake2b256Hash, ByteArray] =
    store.toByteArrayTypedStore(kCodec, vCodec)
}

final case class RadixHistory[F[_]: Sync: Parallel](
  rootHash: Blake2b256Hash,
  rootNode: Node,
  impl: RadixTreeImpl[F],
  store: KeyValueTypedStore[F, Blake2b256Hash, ByteArray],
) extends History[F] {
  override def root: Blake2b256Hash = rootHash

  override def reset(root: Blake2b256Hash): F[History[F]] =
    for {
      impl <- Sync[F].delay(new RadixTreeImpl[F](store))
      node <- impl.loadNode(root, noAssert = true)
    } yield this.copy(root, node, impl, store)

  override def read(key: KeySegment): F[Option[Blake2b256Hash]] =
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
