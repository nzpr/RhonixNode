package sim.balances

import cats.Monad
import cats.syntax.all.*
import dproc.data.Block
import sdk.codecs.{PrimitiveReader, PrimitiveWriter, Serialize}
import sdk.primitive.ByteArray
import sim.balances.data.{BalancesDeploy, BalancesDeployBody, BalancesState}
import sim.NetworkSim.*
import weaver.data.ConflictResolution

object Serialization {

  implicit def balancesStateSerialize[F[_]: Monad]: Serialize[F, BalancesState] =
    new Serialize[F, BalancesState] {
      override def write(x: BalancesState): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) =>
        x match {
          case BalancesState(diffs) =>
            w.write(diffs.size) *>
              diffs.toList.sorted.traverse_ { case (k, v) => w.write(k.bytes) *> w.write(v) }
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
      override def write(x: T): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) =>
        x match {
          case BalancesDeploy(id, body) => w.write(id.bytes) *> balancesDeployBodySerialize[F].write(body)(w)
        }

      override def read: PrimitiveReader[F] => F[T] = (r: PrimitiveReader[F]) =>
        for {
          id   <- r.readBytes
          body <- balancesDeployBodySerialize[F].read(r)
        } yield BalancesDeploy(ByteArray(id), body)
    }

  implicit def blockSerialize[F[_]: Monad]: Serialize[F, Block[M, S, T]] =
    new Serialize[F, Block[M, S, T]] {
      override def write(x: Block[M, S, T]): PrimitiveWriter[F] => F[Unit] = (w: PrimitiveWriter[F]) =>
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
              minGenJs.toList.sorted.traverse_(x => w.write(x.bytes)) *>
              offences.toList.sorted.traverse_(x => w.write(x.bytes)) *>
              txs.sorted.traverse_(x => balancesDeploySerialize.write(x)(w)) *>
              finalFringe.toList.sorted.traverse_(x => w.write(x.bytes)) *>
              finalized.traverse_ { case ConflictResolution(accepted, rejected) =>
                accepted.toList.sorted.traverse_(x => balancesDeploySerialize.write(x)(w)) *>
                  rejected.toList.sorted.traverse_(x => balancesDeploySerialize.write(x)(w))
              } *>
              merge.toList.sorted.traverse_(x => balancesDeploySerialize.write(x)(w)) *>
              bonds.bonds.toList.sorted.traverse_ { case (k, v) => w.write(k.bytes) *> w.write(v) } *>
              w.write(lazTol) *>
              w.write(expThresh) *>
              w.write(finalStateHash) *>
              w.write(postStateHash)
        }

      override def read: PrimitiveReader[F] => F[Block[M, S, T]] = ??? // not required for now
    }
}
