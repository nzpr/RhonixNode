package io.rhonix.rholang.normalizer

import io.rhonix.rholang.*
import io.rhonix.rholang.ast.rholang.Absyn.*

object SimpleTypeNormalizer {
  def normalizeSimpleType(p: PSimpleType): ConnectiveSTypeN = p.simpletype_ match {
    case _: SimpleTypeBool      => ConnBoolN
    case _: SimpleTypeInt       => ConnIntN
    case _: SimpleTypeBigInt    => ConnBigIntN
    case _: SimpleTypeString    => ConnStringN
    case _: SimpleTypeUri       => ConnUriN
    case _: SimpleTypeByteArray => ConnByteArrayN
  }
}
