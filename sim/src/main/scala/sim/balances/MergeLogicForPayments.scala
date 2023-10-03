package sim.balances

import cats.Monad
import cats.effect.kernel.Sync
import cats.syntax.all.*
import sdk.store.KeyValueTypedStore
import sdk.store.syntax.all.*
import sim.balances.data.Deploy

object MergeLogicForPayments {

  def attemptAdd[F[_]: Sync](
    balances: Map[Channel, Long],
    deploy: Deploy,
  ): F[Boolean] =
    Deploy.toDiff(deploy).toList.foldLeftM(true) {
      case (true, (wallet, change)) =>
        balances.get(wallet) match {
          case Some(curV) =>
            val newV = Math.addExact(curV, change)
            if (newV < 0) false.pure else balances.put(wallet, newV).as(true)
          case _          => balances.put(wallet, change).as(true)
        }
      case (false, _)               => false.pure
    }

  private def attemptAddMany[F[_]: Monad, A, B](z: A, list: Seq[B], attempt: (A, B) => F[Option[A]]): F[Seq[B]] =
    list.foldLeftM(Seq.empty[B]) { case (acc, x) => attempt(z, x).map(_.map(_ +: acc).getOrElse(acc)) }

  /**
   * Merge deploys into the base state rejecting those leading to overflow.
   * */
  def mergeRejectNegativeOverflow[F[_]: Sync](
    balances: KeyValueTypedStore[F, Channel, Long],
    toFinalize: Set[Deploy],
    toMerge: Set[Deploy],
  ): F[(Seq[Deploy], Seq[Deploy])] = {
    def attempt(x: KeyValueTypedStore[F, Channel, Long], d: Deploy): F[Option[Deploy]] =
      attemptAdd[F](x, d).map(_.guard[Option].as(d))

    for {
      fin <- attemptAddMany(balances, toFinalize.toList.sorted, attempt)
      pre <- attemptAddMany(balances, toMerge.toList.sorted, attempt)
    } yield fin ++ pre
  }
}
