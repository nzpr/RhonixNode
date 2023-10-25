package sim.balances

import cats.Parallel
import cats.effect.kernel.{Async, Sync}
import cats.syntax.all.*
import sdk.diag.Metrics
import sdk.history.{ByteArray32, History, InsertAction}
import sdk.store.KeyValueTypedStore
import sdk.syntax.all.*
import sim.balances.data.Account

/**
 * Builds blockchain state storing balances and provides reading the data.
 * */
trait BalancesStateBuilderWithReader[F[_]] {
  def buildState(
    baseState: ByteArray32,
    toFinalize: Map[Wallet, Account],
    toMerge: Map[Wallet, Account],
  ): F[(ByteArray32, ByteArray32)]

  def readAccount(state: ByteArray32, wallet: Wallet): F[Option[Account]]
}

object BalancesStateBuilderWithReader {

  // Balances state cannot bear store balances
  private def negativeBalanceException(w: Wallet, b: Balance): Exception =
    new Exception(s"Attempt to commit negative balance $b for wallet $w.")

  def apply[F[_]: Async: Parallel: Metrics](
    history: History[F],
    valueStore: KeyValueTypedStore[F, ByteArray32, Account],
  ): BalancesStateBuilderWithReader[F] = {

    /**
     * Create action for history and persist hash -> value relation.
     *
     * Thought the second is not necessary, value type for RadixHistory is hardcoded with Blake hash,
     * so cannot just place Balance as a value there.
     */
    def createHistoryActionAndStoreData(wallet: Wallet, value: Account): F[InsertAction] =
      for {
        _    <- Sync[F].raiseError(negativeBalanceException(wallet, value.balance)).whenA(value.balance < 0)
        vHash = accountToHash(value)
        _    <- valueStore.put(vHash, value)
      } yield InsertAction(walletToKeySegment(wallet), vHash)

    def applyActions(
      root: ByteArray32,
      actions: Map[Wallet, Account],
    ): F[ByteArray32] =
      for {
        h       <- history.reset(root)
        actions <- actions.toList.traverse { case (w, b) => createHistoryActionAndStoreData(w, b) }
        root    <- h.process(actions).map(_.root)
      } yield root

    new BalancesStateBuilderWithReader[F] {
      override def buildState(
        baseState: ByteArray32,
        toFinalize: Map[Wallet, Account],
        toMerge: Map[Wallet, Account],
      ): F[(ByteArray32, ByteArray32)] = for {
        // merge final state
        finalHash <- applyActions(baseState, toFinalize).timedM("commit-final-state")
        _         <- Metrics[F].gauge("final-hash", finalHash.bytes.toHex)
        // merge pre state, apply tx on top top get post state
        postState  = toFinalize ++ toMerge
        // merge post state
        postHash  <- applyActions(finalHash, postState)
      } yield finalHash -> postHash

      override def readAccount(state: ByteArray32, wallet: Wallet): F[Option[Account]] = for {
        h    <- history.reset(state)
        bOpt <- h.read(walletToKeySegment(wallet))
        r    <- bOpt.flatTraverse(hash => valueStore.get1(hash))
      } yield r

    }
  }
}
