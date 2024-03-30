package io.rhonix.rholang.normalizer.util

import io.rhonix.rholang.normalizer.env.{BoundVarReader, VarContext}
import io.rhonix.rholang.normalizer.util.Mock.DefPosition

case class MockBoundVarReader[T](boundVars: Map[String, (Int, T)]) extends BoundVarReader[T] {
  private val boundVarMap: Map[String, VarContext[T]] =
    boundVars.map { case (name, (index, varType)) => name -> VarContext(index, varType, DefPosition) }

  override def getBoundVar(name: String): Option[VarContext[T]] = boundVarMap.get(name)

  override def findBoundVar(name: String): Option[VarContext[T]] =
    boundVarMap.get(name)

  override def getNextIndex: Int = if (boundVarMap.isEmpty) 0 else boundVarMap.values.map(_.index).max + 1
}
