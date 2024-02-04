package io.rhonix.rholang.interpreter

final case class SourcePosition(row: Int, column: Int) {
  override def toString: String = s"$row:$column"
}
