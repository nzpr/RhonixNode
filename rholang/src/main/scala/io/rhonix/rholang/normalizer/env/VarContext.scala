package io.rhonix.rholang.normalizer.env

import io.rhonix.rholang.interpreter.SourcePosition

final case class VarContext[T](index: Int, typ: T, sourcePosition: SourcePosition)
