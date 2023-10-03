package sim.balances

import cats.Parallel
import cats.effect.kernel.{Async, Sync}
import cats.syntax.all.*
import sdk.hashing.Blake2b256Hash
import sdk.history.{History, InsertAction}
import sdk.store.KeyValueTypedStore
import sdk.syntax.all.*
import sim.balances.data.{Datum, State}

/**
 * Builds blockchain state storing balances and provides reading the data.
 * */
trait BalancesStateBuilderWithReader[F[_]] {
  def buildState(
    baseState: Blake2b256Hash,
    toFinalize: State,
    toMerge: State,
  ): F[(Blake2b256Hash, Blake2b256Hash)]

  def readState(state: Blake2b256Hash, wallet: Channel): F[Option[(Balance, Long)]]
}

object BalancesStateBuilderWithReader {

  // Balances state cannot bear store balances
  private def negativeBalanceException(w: Channel, b: Balance): Exception =
    new Exception(s"Attempt to commit negative balance $b for wallet $w.")

  def apply[F[_]: Async: Parallel](
    history: History[F],
    valueStore: KeyValueTypedStore[F, Blake2b256Hash, Datum],
  ): BalancesStateBuilderWithReader[F] = {

    /**
     * Create action for history and persist hash -> value relation.
     *
     * Thought the second is not necessary, value type for RadixHistory is hardcoded with Blake hash,
     * so cannot just place Balance as a value there.
     */
    def createHistoryActionAndStoreData(wallet: Channel, datum: Datum): F[InsertAction] =
      for {
        _    <- Sync[F].raiseError(negativeBalanceException(wallet, datum.balance)).whenA(datum.balance < 0)
        vHash = datumToHash(datum)
        _    <- valueStore.put(vHash, datum)
      } yield InsertAction(walletToKeySegment(wallet), vHash)

    def applyActions(
      root: Blake2b256Hash,
      setBalanceActions: List[(Channel, Datum)],
    ): F[Blake2b256Hash] =
      for {
        h       <- history.reset(root)
        actions <- setBalanceActions.traverse { case (w, datum) => createHistoryActionAndStoreData(w, datum) }
        root    <- h.process(actions).map(_.root)
      } yield root

    new BalancesStateBuilderWithReader[F] {
      override def buildState(
        baseState: Blake2b256Hash,
        toFinalize: State,
        toMerge: State,
      ): F[(Blake2b256Hash, Blake2b256Hash)] = for {
        // merge final state
        finalHash <- applyActions(baseState, toFinalize.diffs.toList)
        // merge pre state, apply tx on top top get post state
        postState  = toFinalize ++ toMerge
        // merge post state
        preState  <- applyActions(finalHash, postState.diffs.toList)
      } yield (finalHash, preState)

      override def readState(state: Blake2b256Hash, wallet: Channel): F[Option[(Balance, Long)]] = for {
        h    <- history.reset(state)
        bOpt <- h.read(walletToKeySegment(wallet))
        r    <- bOpt.flatTraverse(hash => valueStore.get1(hash))
      } yield r
    }
  }
}
