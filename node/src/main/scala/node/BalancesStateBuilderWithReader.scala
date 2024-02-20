package node

import cats.Parallel
import cats.effect.Ref
import cats.effect.kernel.{Async, Sync}
import cats.syntax.all.*
import sdk.api.data.Balance
import sdk.codecs.Digest
import sdk.data.BalancesState
import sdk.diag.Metrics
import sdk.history.{ByteArray32, InsertAction, KeySegment}
import sdk.primitive.ByteArray
import sdk.store.HistoryWithValues
import sdk.syntax.all.*

/**
 * Builds blockchain state storing balances and provides reading the data.
 * */
trait BalancesStateBuilderWithReader[F[_]] {
  def buildState(
    baseState: ByteArray32,
    toFinalize: BalancesState,
    toMerge: BalancesState,
  ): F[(ByteArray32, ByteArray32)]

  def readBalance(state: ByteArray32, wallet: ByteArray): F[Option[Long]]
}

object BalancesStateBuilderWithReader {

  // Balances state cannot bear store balances
  private def negativeBalanceException(w: ByteArray, b: Balance): Exception =
    new Exception(s"Attempt to commit negative balance ${b.x} for wallet $w.")

  def apply[F[_]: Async: Parallel: Metrics](
    balancesHistoryWithValues: HistoryWithValues[F, Balance],
  )(implicit balanceHash: Digest[Balance]): BalancesStateBuilderWithReader[F] = {

    val HistoryWithValues(history, valueStore) = balancesHistoryWithValues

    /**
     * Create action for history and persist hash -> value relation.
     *
     * Thought the second is not necessary, value type for RadixHistory is hardcoded with Blake hash,
     * so cannot just place Balance as a value there.
     */
    def createHistoryActionAndStoreData(wallet: ByteArray, balance: Balance): F[InsertAction] =
      for {
        _     <- Sync[F].raiseError(negativeBalanceException(wallet, balance)).whenA(balance.x < 0)
        vHash <- ByteArray32.convert(balanceHash.digest(balance)).liftTo[F]
        _     <- valueStore.put(vHash, balance)
      } yield InsertAction(KeySegment(wallet), vHash)

    def applyActions(
      root: ByteArray32,
      setBalanceActions: List[(ByteArray, Balance)],
    ): F[ByteArray32] =
      for {
        h       <- history.reset(root)
        actions <- setBalanceActions.traverse { case (w, b) => createHistoryActionAndStoreData(w, b) }
        root    <- h.process(actions).map(_.root)
      } yield root

    new BalancesStateBuilderWithReader[F] {
      override def buildState(
        baseState: ByteArray32,
        toFinalize: BalancesState,
        toMerge: BalancesState,
      ): F[(ByteArray32, ByteArray32)] = for {
        // merge final state
        finalHash <-
          applyActions(baseState, toFinalize.diffs.view.mapValues(new Balance(_)).toList).timedM("commit-final-state")
        _         <- Metrics[F].gauge("final-hash", finalHash.bytes.toHex)
        // merge pre state, apply tx on top top get post state
        postState  = toFinalize ++ toMerge
        // merge post state
        postHash  <- applyActions(finalHash, postState.diffs.view.mapValues(new Balance(_)).toList)
      } yield finalHash -> postHash

      override def readBalance(state: ByteArray32, wallet: ByteArray): F[Option[Long]] = for {
        h    <- history.reset(state)
        bOpt <- h.read(KeySegment(wallet))
        r    <- bOpt.flatTraverse(hash => valueStore.get1(hash).map(_.map(_.x)))
      } yield r
    }
  }
}
