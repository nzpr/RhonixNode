package io.rhonix.rholang.reducer.processes

import cats.effect.Sync
import cats.syntax.all.*
import coop.rchain.rholang.interpreter.accounting.SEND_EVAL_COST
import coop.rchain.rholang.interpreter.errors.*
import io.rhonix.rholang.reducer.*
import io.rhonix.rholang.reducer.env.*
import io.rhonix.rholang.types.*

object SendReducer {

  def reduceSend[F[_]: Sync: ExprReducer: CostWriter: Substituter](send: SendN): F[SendN] = for {
    _             <- CostWriter[F].charge(SEND_EVAL_COST)
    evalChan      <- ExprReducer[F].reduceAsExpr(send.chan)
    substChan     <- Substituter[F].substitute(evalChan)
    unbundledChan <- substChan match {
                       case p: BundleN =>
                         if (!p.writeFlag) ReduceError("Trying to send on non-writeable channel.").raiseError[F, ParN]
                         else p.body.pure[F]
                       case _          => substChan.pure[F]
                     }
    evalArgs      <- send.args.traverse(ExprReducer[F].reduceAsExpr)
    substArgs     <- evalArgs.traverse(Substituter[F].substitute)
  } yield SendN(unbundledChan, substArgs, send.persistent)
}
