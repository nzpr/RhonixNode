package sim.execution.balances

import cats.Monoid
import cats.effect.Sync
import cats.effect.std.Random
import cats.syntax.all.*

/**
 * Minimalistic state that support only storing balances.
 * Analogous to the full tuple space it can be represent with a map.
 * Can be treated as a state but also as a state diff, since it is a monoid.
 * */
final class BalancesState(val diffs: Map[Wallet, Balance]) extends AnyVal

object BalancesState {
  implicit def monoidForBalances: Monoid[BalancesState] =
    new Monoid[BalancesState] {
      override def empty: BalancesState = new BalancesState(Map.empty[Wallet, Balance])

      override def combine(x: BalancesState, y: BalancesState): BalancesState =
        new BalancesState(x.diffs |+| y.diffs)
    }

  def random[F[_]: Random: Sync](users: Set[Wallet]): F[BalancesState] = for {
    txVal <- Random[F].nextLongBounded(100)
    from  <- Random[F].elementOf(users)
    to    <- Random[F].elementOf(users - from)
  } yield new BalancesState(Map(from -> -txVal, to -> txVal))
}
