package io.rhonix.rholang.reducer.processes

import cats.effect.Sync
import cats.syntax.all.*
import coop.rchain.rholang.interpreter.accounting.RECEIVE_EVAL_COST
import coop.rchain.rholang.interpreter.errors.*
import io.rhonix.rholang.reducer.*
import io.rhonix.rholang.reducer.env.*
import io.rhonix.rholang.types.*

object ReceiveReducer {

  def reduceReceive[F[_]: Sync: ExprReducer: CostWriter: Substituter](receive: ReceiveN): F[ReceiveN] = {

    def reduceSource(src: ParN): F[ParN] = for {
      evalSrc      <- ExprReducer[F].reduceAsExpr(src)
      substSrc     <- Substituter[F].substitute(evalSrc)
      unbundledSrc <-
        substSrc match {
          case p: BundleN =>
            if (!p.readFlag) ReduceError("Trying to receive from non-readable channel.").raiseError[F, ParN]
            else p.body.pure[F]
          case _          => substSrc.pure[F]
        }
    } yield unbundledSrc

    for {
      _            <- CostWriter[F].charge(RECEIVE_EVAL_COST)
      reducedBinds <- receive.binds.traverse(rb =>
                        for {
                          reducedSrc    <- reduceSource(rb.source)
                          substPatterns <- rb.patterns.traverse(Substituter[F].substitute) // TODO: depth = 1
                        } yield ReceiveBindN(substPatterns, reducedSrc, rb.remainder, rb.freeCount),
                      )
      substBody    <- Substituter[F].substitute(receive.body) // TODO: env.shift(receive.bindCount)
    } yield ReceiveN(reducedBinds, substBody, receive.persistent, receive.peek, receive.bindCount)
  }
}
