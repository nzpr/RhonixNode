package io.rhonix.rholang.interpreter.compiler

sealed trait VarSort
case object ProcSort extends VarSort
case object NameSort extends VarSort
