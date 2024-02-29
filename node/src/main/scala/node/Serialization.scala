package node

import cats.syntax.all.*
import cats.{Applicative, Monad}
import dproc.data.Block
import sdk.api.data.{Balance, TokenTransferRequest}
import sdk.codecs.{PrimitiveReader, PrimitiveWriter, Serialize}
import sdk.data.{BalancesDeploy, BalancesDeployBody, BalancesState}
import sdk.primitive.ByteArray
import weaver.data.ConflictResolution

import scala.math.Numeric.LongIsIntegral

// TODO move to sdk since this is a specification
object Serialization {

  implicit def balanceSerialize[F[_]: Monad]: Serialize[F, Balance] = new Serialize[F, Balance] {
    override def write(x: Balance): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) => w.write(x.x)
    override def read: PrimitiveReader[F] => F[Balance]           = (r: PrimitiveReader[F]) => r.readLong.map(new Balance(_))
  }

  implicit def balancesStateSerialize[F[_]: Monad]: Serialize[F, BalancesState] =
    new Serialize[F, BalancesState] {
      override def write(x: BalancesState): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) =>
        x match {
          case BalancesState(diffs) =>
            w.write(diffs.size) *>
              serializeSeq[F, (ByteArray, Long)](diffs.toList.sorted, { case (k, v) => w.write(k.bytes) *> w.write(v) })
        }

      override def read: PrimitiveReader[F] => F[BalancesState] = (r: PrimitiveReader[F]) =>
        for {
          size  <- r.readInt
          diffs <- (0 until size).toList.traverse { _ =>
                     for {
                       k <- r.readBytes.map(ByteArray(_))
                       v <- r.readLong
                     } yield k -> v
                   }
        } yield BalancesState(diffs.toMap)
    }

  implicit def balancesDeployBodySerialize[F[_]: Monad]: Serialize[F, BalancesDeployBody] =
    new Serialize[F, BalancesDeployBody] {
      override def write(x: BalancesDeployBody): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) =>
        x match {
          case BalancesDeployBody(balanceState, vabn) =>
            balancesStateSerialize[F].write(balanceState)(w) *> w.write(vabn)
        }

      override def read: PrimitiveReader[F] => F[BalancesDeployBody] = (r: PrimitiveReader[F]) =>
        for {
          balanceState <- balancesStateSerialize[F].read(r)
          vabn         <- r.readLong
        } yield BalancesDeployBody(balanceState, vabn)
    }

  implicit def balancesDeploySerialize[F[_]: Monad]: Serialize[F, BalancesDeploy] =
    new Serialize[F, BalancesDeploy] {
      override def write(x: BalancesDeploy): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) =>
        x match {
          case BalancesDeploy(id, body) => w.write(id.bytes) *> balancesDeployBodySerialize[F].write(body)(w)
        }

      override def read: PrimitiveReader[F] => F[BalancesDeploy] = (r: PrimitiveReader[F]) =>
        for {
          id   <- r.readBytes
          body <- balancesDeployBodySerialize[F].read(r)
        } yield BalancesDeploy(ByteArray(id), body)
    }

  implicit def bondsMapSerialize[F[_]: Monad]: Serialize[F, Map[ByteArray, Long]] =
    new Serialize[F, Map[ByteArray, Long]] {
      override def write(bonds: Map[ByteArray, Long]): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) =>
        w.write(bonds.size) *>
          serializeSeq[F, (ByteArray, Long)](bonds.toSeq, { case (k, v) => w.write(k.bytes) *> w.write(v) })

      override def read: PrimitiveReader[F] => F[Map[ByteArray, Long]] = (r: PrimitiveReader[F]) =>
        for {
          size  <- r.readInt
          bonds <- (0 until size).toList.traverse { _ =>
                     for {
                       k <- r.readBytes.map(ByteArray(_))
                       v <- r.readLong
                     } yield k -> v
                   }
        } yield bonds.toMap
    }

  implicit def blockSerialize[F[_]: Monad]: Serialize[F, Block[ByteArray, ByteArray, BalancesDeploy]] =
    new Serialize[F, Block[ByteArray, ByteArray, BalancesDeploy]] {
      override def write(x: Block[ByteArray, ByteArray, BalancesDeploy]): PrimitiveWriter[F] => F[Unit] =
        (w: PrimitiveWriter[F]) =>
          x match {
            case Block(
                  sender,
                  minGenJs,
                  offences,
                  txs,
                  finalFringe,
                  finalized,
                  merge,
                  bonds,
                  lazTol,
                  expThresh,
                  finalStateHash,
                  postStateHash,
                ) =>
              w.write(sender.bytes) *>
                serializeSeq[F, ByteArray](minGenJs.toList, x => w.write(x.bytes)) *>
                serializeSeq[F, ByteArray](offences.toList, x => w.write(x.bytes)) *>
                serializeSeq[F, BalancesDeploy](txs, balancesDeploySerialize.write(_)(w)) *>
                serializeSeq[F, ByteArray](finalFringe.toList.sorted, x => w.write(x.bytes)) *>
                finalized
                  .map { case ConflictResolution(accepted, rejected) =>
                    serializeSeq[F, BalancesDeploy](accepted.toList.sorted, balancesDeploySerialize.write(_)(w)) *>
                      serializeSeq[F, BalancesDeploy](rejected.toList.sorted, balancesDeploySerialize.write(_)(w))
                  }
                  .getOrElse(Monad[F].unit) *>
                serializeSeq[F, BalancesDeploy](merge.toList.sorted, balancesDeploySerialize.write(_)(w)) *>
                serializeSeq[F, (ByteArray, Long)](
                  bonds.bonds.toList.sorted,
                  { case (k, v) => w.write(k.bytes) *> w.write(v) },
                ) *>
                w.write(lazTol) *>
                w.write(expThresh) *>
                w.write(finalStateHash.bytes) *>
                w.write(postStateHash.bytes)
          }

      override def read: PrimitiveReader[F] => F[Block[ByteArray, ByteArray, BalancesDeploy]] = ???
    }

  implicit def tokenTransferRequestBodySerialize[F[_]: Monad]: Serialize[F, TokenTransferRequest.Body] =
    new Serialize[F, TokenTransferRequest.Body] {
      override def write(x: TokenTransferRequest.Body): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) =>
        x match {
          case TokenTransferRequest.Body(sender, recipient, tokenId, amount, vafn) =>
            w.write(sender) *>
              w.write(recipient) *>
              w.write(tokenId) *>
              w.write(amount) *>
              w.write(vafn)
        }

      override def read: PrimitiveReader[F] => F[TokenTransferRequest.Body] = (r: PrimitiveReader[F]) =>
        for {
          sender    <- r.readBytes
          recipient <- r.readBytes
          tokenId   <- r.readLong
          amount    <- r.readLong
          vafn      <- r.readLong
        } yield TokenTransferRequest.Body(sender, recipient, tokenId, amount, vafn)
    }

  implicit def tokenTransferRequestSerialize[F[_]: Monad]: Serialize[F, TokenTransferRequest] =
    new Serialize[F, TokenTransferRequest] {
      override def write(x: TokenTransferRequest): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) =>
        x match {
          case TokenTransferRequest(pubKey, digest, signature, signatureAlg, body) =>
            w.write(pubKey) *>
              w.write(digest) *>
              w.write(signature) *>
              w.write(signatureAlg) *>
              tokenTransferRequestBodySerialize[F].write(body)(w)
        }

      override def read: PrimitiveReader[F] => F[TokenTransferRequest] = (r: PrimitiveReader[F]) =>
        for {
          pubKey       <- r.readBytes
          digest       <- r.readBytes
          signature    <- r.readBytes
          signatureAlg <- r.readString
          body         <- tokenTransferRequestBodySerialize[F].read(r)
        } yield TokenTransferRequest(pubKey, digest, signature, signatureAlg, body)
    }

  // This function is needed to work around the problem with `traverse_`
  // See more examples here: https://gist.github.com/nzpr/38f843139224d1de1f0e9d15c75e925c
  private def serializeSeq[F[_]: Applicative, A: Ordering](l: Seq[A], writeF: A => F[Unit]): F[Unit] =
    l.sorted.map(writeF).fold(().pure[F])(_ *> _)

  implicit def byteArraySerialize[F[_]: Monad]: Serialize[F, ByteArray] =
    new Serialize[F, ByteArray] {
      override def write(x: ByteArray): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) => w.write(x.bytes)

      override def read: PrimitiveReader[F] => F[ByteArray] = (r: PrimitiveReader[F]) =>
        for {
          hash <- r.readBytes
        } yield ByteArray(hash)
    }

  implicit def booleanSerialize[F[_]: Monad]: Serialize[F, Boolean] =
    new Serialize[F, Boolean] {
      override def write(x: Boolean): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) => w.write(x)

      override def read: PrimitiveReader[F] => F[Boolean] = (r: PrimitiveReader[F]) =>
        for {
          rcvd <- r.readBool
        } yield rcvd
    }

  implicit def optional[F[_]: Monad, A](implicit serializeA: Serialize[F, A]): Serialize[F, Option[A]] =
    new Serialize[F, Option[A]] {
      override def write(x: Option[A]): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) =>
        x match {
          case Some(a) => w.write(true) *> serializeA.write(a)(w)
          case None    => w.write(false)
        }

      override def read: PrimitiveReader[F] => F[Option[A]] = (r: PrimitiveReader[F]) =>
        for {
          exists <- r.readBool
          a      <- if (exists) serializeA.read(r).map(Some(_)) else None.pure[F]
        } yield a
    }
}
