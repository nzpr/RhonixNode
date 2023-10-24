package sim.balances

import cats.effect.kernel.Sync
import cats.syntax.all.*
import cats.{Monad, Parallel}
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
   * Fold a List of items into initial state. Combination of an item with the state can fail.
   * @return new state and items that failed to be combined.
   * */
  def foldCollectFailures[A, B](z: A, x: List[B], attemptCombine: (A, B) => Option[A]): (A, List[B]) =
    x.foldLeft(z, List.empty[B]) { case ((acc, rjAcc), x) =>
      attemptCombine(acc, x).map(_ -> rjAcc).getOrElse(acc -> (x +: rjAcc))
    }

  def parFoldCollectFailures[F[_]: Parallel: Monad, A](
    z: A,
    x: List[A],
    attemptCombine: (A, A) => Option[A],
    deduct: (A, A) => A,
    parallelism: Int = java.lang.Runtime.getRuntime.availableProcessors(),
  ): F[(A, List[A])] = (x, List.empty[A])
    .tailRecM {
      case List(r) -> dropped => (r -> dropped).asRight[(List[A], List[A])].pure[F]
      case x -> dropped       =>
        (if (parallelism == 1 || x.size < 30) List(x) else x.grouped(x.size / parallelism).toList)
          .parTraverse[F, (A, List[A])](foldCollectFailures(z, _, attemptCombine).pure[F])
          .map(_.unzip)
          .map { case (x, newlyDropped) =>
            (x.map(deduct(_, z)) -> (newlyDropped.flatten ++ dropped)).asLeft[(A, List[A])]
          }
    }
    .map { case (x, y) => attemptCombine(z, x).get -> y }

  /**
   * Merge deploys into the base state rejecting those leading to overflow.
   * */
  def mergeRejectNegativeOverflow[F[_]: Sync: Parallel: Metrics](
    reader: BalancesStateBuilderWithReader[F],
    baseState: Blake2b256Hash,
    toFinalize: Set[BalancesDeploy],
    toMerge: Set[BalancesDeploy],
  ): F[((BalancesState, List[BalancesDeploy]), (BalancesState, List[BalancesDeploy]))] = Sync[F].defer {
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

        def combineStateWithDeploy(s: BalancesDeploy, d: BalancesDeploy) =
          attemptCombineNonNegative(s.state.diffs, d.state.diffs).map(x => new BalancesDeploy("", new BalancesState(x)))

        def deduct(s: BalancesDeploy, d: BalancesDeploy): BalancesDeploy =
          s.copy(state = new data.BalancesState(d.state.diffs.view.mapValues(_ * -1).toMap |+| s.state.diffs))

        parFoldCollectFailures[F, BalancesDeploy](
          new BalancesDeploy("", initFinal),
          toFinalizeSorted,
          combineStateWithDeploy,
          deduct,
        )
          .timedM("buildFinalState")
          .flatMap { case (finChange, finRj) =>
            parFoldCollectFailures[F, BalancesDeploy](
              new BalancesDeploy("", initAll ++ finChange.state),
              toMergeSorted,
              combineStateWithDeploy,
              deduct,
            ).map { case (mergeChange, provRj) =>
              (finChange.state, finRj) -> (mergeChange.state, provRj)
            }
          }
      }
  }
}
