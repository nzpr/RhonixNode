package io.rhonix.rholang.normalizer.util

import coop.rchain.rholang.interpreter.compiler.BoundContext
import Mock.DefPosition
import io.rhonix.rholang.normalizer.env.BoundVarReader

case class MockBoundVarReader[T](boundVars: Map[String, (Int, T)]) extends BoundVarReader[T] {
  private val boundVarMap: Map[String, BoundContext[T]] =
    boundVars.map { case (name, (index, varType)) => name -> BoundContext(index, varType, DefPosition) }

  override def getBoundVar(name: String): Option[BoundContext[T]] = boundVarMap.get(name)

  override def findBoundVar(name: String): Option[(BoundContext[T], Int)] =
    boundVarMap.get(name).map(context => (context, 0)) // Example with level 0
}
