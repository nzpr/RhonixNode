package io.rhonix.rholang.normalizer.util

import coop.rchain.rholang.interpreter.compiler.IdContext
import Mock.{DefFreeVarIndex, FreeVarWriterData}
import io.rhonix.rholang.normalizer.env.FreeVarWriter

import scala.collection.mutable.ListBuffer

case class MockFreeVarWriter[F[_], T](scope: MockFreeVarScope[F]) extends FreeVarWriter[T] {
  private val buffer: ListBuffer[FreeVarWriterData[T]] = ListBuffer.empty

  override def putFreeVar(binding: IdContext[T]): Int = {
    val scopeLevel = scope.getScopeLevel
    buffer.append(binding match { case (name, varType, _) => FreeVarWriterData(name, varType, scopeLevel) })
    DefFreeVarIndex
  }

  def extractData: Seq[FreeVarWriterData[T]] = buffer.toSeq
}
