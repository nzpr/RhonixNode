package io.rhonix.rholang.normalizer.envimpl

import io.rhonix.rholang.normalizer.env.*
import io.rhonix.rholang.normalizer.syntax.all.*

final case class BoundVarReaderImpl[T](chain: HistoryChain[VarMap[T]]) extends BoundVarReader[T] {
  override def getBoundVar(name: String): Option[VarContext[T]]  = chain.current().flatMap(_.get(name))
  override def findBoundVar(name: String): Option[VarContext[T]] = chain.getFirstVarInChain(name)
  override def getNextIndex: Int                                 = chain.current().map(_.nextIndex).getOrElse(0)
}
