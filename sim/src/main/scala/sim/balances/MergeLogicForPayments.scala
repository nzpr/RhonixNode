package sim.balances

import cats.effect.kernel.Sync
import cats.syntax.all.*
import sdk.diag.Metrics
import sdk.history.ByteArray32
import sdk.syntax.all.{effectSyntax, mapSyntax}
import sim.balances.data.{Account, BalancesDeploy}

import scala.math.Numeric.LongIsIntegral

object MergeLogicForPayments {

  /**
   * Compute final values for records changed by the deploy.
   *
   * @return State containing changed values.
   *         None if negative value or long overflow detected. In this case, deploy should be rejected.
   * */
  def attemptCombine(
    state: Map[Wallet, Account],
    deploy: BalancesDeploy,
  ): Option[Map[Wallet, Account]] = {
    val curNonce = state.getUnsafe(deploy.deployer).nonce
    val newNonce = Math.max(deploy.nonce, curNonce)
    // deploys with nonces not greater then already folded should be discarded
    if (newNonce == curNonce) none[Map[Wallet, Account]]
    else
      deploy.state.diffs
        .foldLeft(state.some) { case (acc1, (wallet, change)) =>
          acc1 match {
            case None      => acc1
            case Some(acc) =>
              // Input args should ensure item is present in a map
              val curV = state.getUnsafe(wallet).balance
              // Overflow should be fatal, since this is related to total supply
              val newV = Math.addExact(curV, change)
              Option
                .unless(newV < 0)(newV)
                .as(acc.updated(wallet, acc.getUnsafe(wallet).copy(balance = newV, nonce = newNonce)))
          }
        }
  }

  /**
   * Fold a sequence of items into initial state. Combination of an item with the state can fail.
   *
   * @return new state and items that failed to be combined.
   * */
  def foldCollectFailures[A, B](z: A, x: Seq[B], attemptCombine: (A, B) => Option[A]): (A, Seq[B]) =
    x.foldLeft(z, Seq.empty[B]) { case ((acc, rjAcc), x) =>
      attemptCombine(acc, x).map(_ -> rjAcc).getOrElse(acc -> (x +: rjAcc))
    }

  /**
   * Merge deploys into the base state rejecting those leading to overflow.
   * */
  def mergeRejectNegativeOverflow[F[_]: Sync: Metrics](
    reader: BalancesStateBuilderWithReader[F],
    baseState: ByteArray32,
    toFinalize: Set[BalancesDeploy],
    toMerge: Set[BalancesDeploy],
  ): F[((Map[Wallet, Account], Seq[BalancesDeploy]), (Map[Wallet, Account], Seq[BalancesDeploy]))] = Sync[F].defer {
    val adjustedInFinal: Set[Wallet] = toFinalize.flatMap(_.state.diffs.keys)
    val adjustedInMerge: Set[Wallet] = toMerge.flatMap(_.state.diffs.keys)
    val adjustedAll: Set[Wallet]     = adjustedInFinal ++ adjustedInMerge

    val readAllAccounts = adjustedAll.toList
      .traverse(k => reader.readAccount(baseState, k).map(_.getOrElse(Account.Default)).map(k -> _))
      .map(_.toMap)

    readAllAccounts
      .flatMap { initAll =>
        val initFinal = initAll.view.filterKeys(adjustedInFinal.contains).toMap

        // Deploys per each deployer should be ordered, deploys to fold should be ordered by deployer
        def orderSet(x: Set[BalancesDeploy]) = {
          implicit val deployOrdering: Ordering[BalancesDeploy] = Ordering.by(x => (x.nonce, x.id))
          x.groupBy(_.deployer).view.mapValues(_.toList.sorted).toList.sortBy(_._1).flatMap(_._2)
        }

        val toFinalizeSorted = orderSet(toFinalize)
        val toMergeSorted    = orderSet(toMerge)

        Sync[F]
          .delay(foldCollectFailures(initFinal, toFinalizeSorted, attemptCombine))
          .timedM("buildFinalState")
          .map { case (finChange, finRj) =>
            val (mergeChange, provRj) = foldCollectFailures(
              initAll ++ finChange,
              toMergeSorted,
              attemptCombine,
            )
            (finChange, finRj) -> (mergeChange, provRj)
          }
      }
  }
}
