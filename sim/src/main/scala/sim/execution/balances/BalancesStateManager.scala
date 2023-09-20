package sim.execution.balances

import cats.Parallel
import cats.effect.kernel.{Async, Sync}
import cats.syntax.all.*
import sdk.hashing.Blake2b256Hash
import sdk.store.InMemoryKeyValueStore

/**
 * Builds blockchain state and provides reading the data.
 * */
trait BalancesStateManager[F[_]] {
  def buildState(
    baseFringe: Blake2b256Hash,
    toFinalize: Set[BalancesDeploy],
    toMerge: Set[BalancesDeploy],
    toExecute: Set[BalancesDeploy],
  ): F[(Blake2b256Hash, Blake2b256Hash)]

  def readBalance(state: Blake2b256Hash, wallet: Wallet): F[Balance]
}

object BalancesStateManager {
  def apply[F[_]: Async: Parallel, M]: F[BalancesStateManager[F]] = Sync[F].defer {
    BalancesHistory(new InMemoryKeyValueStore[F]).map { history =>
      new BalancesStateManager[F] {
        override def buildState(
          baseHash: Blake2b256Hash,
          toFinalize: Set[BalancesDeploy],
          toMerge: Set[BalancesDeploy],
          toExecute: Set[BalancesDeploy],
        ): F[(Blake2b256Hash, Blake2b256Hash)] =
          for {
            // merge final state
            finalHash <- history.commit(baseHash, toFinalize.map(_.state).toList.combineAll.diffs.toList)
            // merge pre state, apply tx on top top get post state
            postState  = toMerge.map(_.state).toList.combineAll |+| toExecute.map(_.state).toList.combineAll
            // merge post state
            postHash  <- history.commit(finalHash, postState.diffs.toList)
          } yield finalHash -> postHash

        override def readBalance(state: Blake2b256Hash, wallet: Wallet): F[Balance] = history.get(state, wallet)
      }
    }
  }
}
