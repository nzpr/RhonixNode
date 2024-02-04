package io.rhonix.rholang.interpreter.compiler

import io.rhonix.rholang.interpreter.SourcePosition

final case class BoundContext[T](index: Int, typ: T, sourcePosition: SourcePosition)
