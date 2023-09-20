package sim.execution.balances

import cats.Parallel
import cats.effect.kernel.Async
import cats.syntax.all.*
import sdk.hashing.Blake2b256Hash
import sdk.history.History.EmptyRootHash
import sdk.history.{InsertAction, KeySegment}
import sdk.primitive.ByteArray
import sdk.store.KeyValueStore

/**
 * Radix history maintaining wallet balances.
 * Simplistic wrapper for main History API.
 * */
trait BalancesHistory[F[_]] {
  def commit(root: Blake2b256Hash, actions: List[(Wallet, Balance)]): F[Blake2b256Hash]
  def get(root: Blake2b256Hash, k: Wallet): F[Balance]
}

object BalancesHistory {

  private def toHistoryActions(usr: Wallet, newVal: Balance): InsertAction =
    InsertAction(walletToKeySegment(usr), balanceToHash(newVal))

  def apply[F[_]: Async: Parallel](kvStore: KeyValueStore[F]): F[BalancesHistory[F]] =
    sdk.history.History.create(EmptyRootHash, kvStore).map { h =>
      new BalancesHistory[F] {
        override def commit(root: Blake2b256Hash, actions: List[(Wallet, Balance)]): F[Blake2b256Hash] =
          h.reset(root).flatMap(_.process(actions.map { case (k, v) => toHistoryActions(k, v) }).map(_.root))

        override def get(root: Blake2b256Hash, k: Wallet): F[Balance] =
          h.reset(root)
            .flatMap(_.read(KeySegment(ByteArray(k.toByte))))
            .flatMap(_.liftTo[F](new Exception(s"Unknown state or wallet")))
            .map(hashToBalance)
      }
    }
}
