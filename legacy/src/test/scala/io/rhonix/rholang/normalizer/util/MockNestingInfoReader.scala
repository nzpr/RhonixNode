package io.rhonix.rholang.normalizer.util

import io.rhonix.rholang.normalizer.env.NestingInfoReader

case class MockNestingInfoReader(
  insidePatternInit: Boolean,
  insideTopLevelReceivePatternInit: Boolean,
  insideBundleInit: Boolean,
) extends NestingInfoReader {
  override def insidePattern: Boolean                = insidePatternInit
  override def insideTopLevelReceivePattern: Boolean = insideTopLevelReceivePatternInit
  override def insideBundle: Boolean                 = insideBundleInit
}
