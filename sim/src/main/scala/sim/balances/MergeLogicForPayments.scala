package sim.balances

import cats.effect.kernel.Sync
import cats.syntax.all.*
import sdk.diag.Metrics
import sdk.hashing.Blake2b256Hash
import sdk.syntax.all.{effectSyntax, mapSyntax}
import sim.balances.data.{BalancesDeploy, BalancesState}

object MergeLogicForPayments {

  /**
   * Attempt to combine two maps.
   * @return combined map or None if combination leads to negative value on any key.
   * */
  def attemptCombineNonNegative[K](x: Map[K, Long], y: Map[K, Long]): Option[Map[K, Long]] =
    y.foldLeft(x.some) { case (acc, (wallet, change)) =>
      acc match {
        case None      => acc
        case Some(acc) =>
          // Input args should ensure item is present in a map
          val curV = x.getUnsafe(wallet)
          // Overflow should be fatal, since this is related to total supply
          val newV = Math.addExact(curV, change)
          Option.unless(newV < 0)(newV).as(acc + (wallet -> newV))
      }
    }

  /**
   * Fold a sequence of items into initial state. Combination of an item with the state can fail.
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
    baseState: Blake2b256Hash,
    toFinalize: Set[BalancesDeploy],
    toMerge: Set[BalancesDeploy],
  ): F[((BalancesState, Seq[BalancesDeploy]), (BalancesState, Seq[BalancesDeploy]))] = Sync[F].defer {
    val adjustedInFinal: Set[Wallet] = toFinalize.flatMap(_.state.diffs.keys)
    val adjustedInMerge: Set[Wallet] = toMerge.flatMap(_.state.diffs.keys)
    val adjustedAll: Set[Wallet]     = adjustedInFinal ++ adjustedInMerge

    val readAllBalances = adjustedAll.toList
      .traverse(k => reader.readBalance(baseState, k).map(_.getOrElse(0L)).map(k -> _))
      .map(_.toMap)

    readAllBalances
      .flatMap { allInitValues =>
        val initFinal        = new BalancesState(allInitValues.view.filterKeys(adjustedInFinal.contains).toMap)
        val initAll          = new BalancesState(allInitValues)
        val toFinalizeSorted = toFinalize.toList.sorted
        val toMergeSorted    = toMerge.toList.sorted

        def combineStateWithDeploy(s: BalancesState, d: BalancesDeploy) =
          attemptCombineNonNegative(s.diffs, d.state.diffs).map(new BalancesState(_))

        Sync[F]
          .delay(foldCollectFailures(initFinal, toFinalizeSorted, combineStateWithDeploy))
          .timedM("buildFinalState")
          .map { case (finChange, finRj) =>
            val (mergeChange, provRj) = foldCollectFailures(initAll ++ finChange, toMergeSorted, combineStateWithDeploy)
            (finChange, finRj) -> (mergeChange, provRj)
          }
      }
  }
}
