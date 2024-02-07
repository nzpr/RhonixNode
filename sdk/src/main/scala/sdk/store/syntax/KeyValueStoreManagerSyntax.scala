package sdk.store.syntax

import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import sdk.codecs.Codec
import sdk.primitive.ByteArray
import sdk.store.{KeyValueStoreManager, KeyValueTypedStore}
import sdk.syntax.all.*

trait KeyValueStoreManagerSyntax {
  implicit final def sdkSyntaxKeyValueStoreManager[F[_]: Sync](
    manager: KeyValueStoreManager[F],
  ): KeyValueStoreManagerOps[F] = new KeyValueStoreManagerOps[F](manager)
}

final class KeyValueStoreManagerOps[F[_]: Sync](
  // KeyValueStoreManager extensions / syntax
  private val manager: KeyValueStoreManager[F],
) {

  /**
    * Returns typed key-value store (DB).
    *
    * @param name database name
    * @param kCodec codec for the key
    * @param vCodec codec for the value
    */
  def database[K, V](
    name: String,
    kCodec: Codec[K, ByteArray],
    vCodec: Codec[V, ByteArray],
  ): F[KeyValueTypedStore[F, K, V]] =
    manager.store(name).map(_.toByteArrayTypedStore(kCodec, vCodec))

  /**
    * Wraps manager with Resource calling shutdown on exit.
    */
  def asResource: Resource[F, KeyValueStoreManager[F]] =
    Resource.make(manager.pure[F])(_.shutdown)
}
