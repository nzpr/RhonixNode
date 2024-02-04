package io.rhonix.rholang.interpreter.compiler

import io.rhonix.rholang.interpreter.SourcePosition

final case class FreeContext[T](level: Int, typ: T, sourcePosition: SourcePosition)
