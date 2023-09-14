package sdk.store

import cats.effect.Sync
import cats.syntax.all.*
import scodec.bits.BitVector
import sdk.data.ByteVectorOps.*
import sdk.data.Codec

class KeyValueTypedStoreCodec[F[_]: Sync, K, V](
  store: KeyValueStore[F],
  kCodec: Codec[F, K],
  vCodec: Codec[F, V],
) extends KeyValueTypedStore[F, K, V] {

  def encodeKey(key: K): F[BitVector]     = kCodec.encode(key)
  def decodeKey(bytes: BitVector): F[K]   = kCodec.decode(bytes)
  def encodeValue(value: V): F[BitVector] = vCodec.encode(value)
  def decodeValue(bytes: BitVector): F[V] = vCodec.decode(bytes)

  import cats.instances.option.*
  import cats.instances.vector.*

  override def get(keys: Seq[K]): F[Seq[Option[V]]] =
    for {
      keysBitVector <- keys.toVector.traverse(encodeKey)
      keysBuf        = keysBitVector.map(_.toByteVector.toDirectByteBuffer)
      valuesBytes   <- store.get(keysBuf, BitVector(_))
      values        <- valuesBytes.toVector.traverse(_.traverse(decodeValue))
    } yield values

  override def put(kvPairs: Seq[(K, V)]): F[Unit] =
    for {
      pairsBitVector <- kvPairs.toVector.traverse { case (k, v) =>
                          encodeKey(k).map2(encodeValue(v))((x, y) => (x, y))
                        }
      pairs           = pairsBitVector.map { case (k, v) => (k.toByteVector.toDirectByteBuffer, v) }
      _              <- store.put[BitVector](pairs, _.toByteVector.toDirectByteBuffer)
    } yield ()

  override def delete(keys: Seq[K]): F[Int] =
    for {
      keysBitVector <- keys.toVector.traverse(encodeKey)
      keysBuf        = keysBitVector.map(_.toByteVector.toDirectByteBuffer)
      deletedCount  <- store.delete(keysBuf)
    } yield deletedCount

  override def contains(keys: Seq[K]): F[Seq[Boolean]] =
    for {
      keysBitVector <- keys.toVector.traverse(encodeKey)
      keysBuf        = keysBitVector.map(_.toByteVector.toDirectByteBuffer)
      results       <- store.get(keysBuf, _ => ())
    } yield results.map(_.nonEmpty)

  override def collect[T](pf: PartialFunction[(K, () => V), T]): F[Seq[T]] = ??? // TODO: implement in the future
//    for {
//      values <- store.iterate(
//                  _.map { case (kBuff, vBuff) =>
//                    val kBytes = BitVector(kBuff)
//                    // Inside LMDB iterator can only be synchronous operation / unsafe decode
//                    val k      = kCodec.decodeValue(kBytes).require
//                    // Lazy evaluate/decode value
//                    val fv     = () => {
//                      val vBytes = BitVector(vBuff)
//                      vCodec.decodeValue(vBytes).require
//                    }
//                    (k, fv)
//                  }.collect(pf).toVector,
//                )
//    } yield values

  override def toMap: F[Map[K, V]] =
    for {
      valuesBytes <- store.iterate(
                       _.map { case (k, v) => (BitVector(k), BitVector(v)) }.toVector,
                     )
      values      <- valuesBytes.traverse { case (k, v) =>
                       for {
                         key   <- decodeKey(k)
                         value <- decodeValue(v)
                       } yield (key, value)

                     }
    } yield values.toMap
}
