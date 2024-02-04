package io.rhonix.rholang.normalizer.envimpl

import io.rhonix.rholang.interpreter.SourcePosition
import io.rhonix.rholang.interpreter.compiler.IdContext
import io.rhonix.rholang.normalizer.env.FreeVarWriter

final case class FreeVarWriterImpl[T](private val putFn: (String, T, SourcePosition) => Int) extends FreeVarWriter[T] {

  override def putFreeVar(binding: IdContext[T]): Int = putFn.tupled(binding)
}
