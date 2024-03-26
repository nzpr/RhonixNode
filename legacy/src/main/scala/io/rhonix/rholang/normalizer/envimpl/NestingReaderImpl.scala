package io.rhonix.rholang.normalizer.envimpl

import io.rhonix.rholang.normalizer.env.NestingReader

final case class NestingReaderImpl(
  patternInfo: HistoryChain[(Int, Boolean)],
  bundleInfo: HistoryChain[Boolean],
) extends NestingReader {
  override def patternDepth: Int                     = patternInfo.current().map(_._1).getOrElse(0)
  override def insideTopLevelReceivePattern: Boolean = patternInfo.current().exists(_._2)
  override def insideBundle: Boolean                 = bundleInfo.current().getOrElse(false)
}
