package io.rhonix.rholang.normalizer.envimpl

import coop.rchain.rholang.interpreter.compiler.FreeContext
import io.rhonix.rholang.normalizer.env.*

final case class FreeVarReaderImpl[T](chain: HistoryChain[VarMap[T]]) extends FreeVarReader[T] {
  override def getFreeVars: Seq[(String, FreeContext[T])] =
    chain.current().getOrElse(VarMap.default()).data.toSeq.map { case (name, VarContext(index, typ, sourcePosition)) =>
      (name, FreeContext(index, typ, sourcePosition))
    }

  override def getFreeVar(name: String): Option[FreeContext[T]] = chain
    .current()
    .flatMap(_.get(name).map { case VarContext(index, typ, sourcePosition) =>
      FreeContext(index, typ, sourcePosition)
    })
}
