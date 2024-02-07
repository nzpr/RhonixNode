package node

import sdk.reflect.Description

@Description("node")
final case class Config(
  @Description("Persist on chain state on disk.")
  persistOnChainState: Boolean = false,
  @Description("Limit number of blocks to be processed concurrently")
  processingConcurrency: Int = 4,
  @Description("Enable streaming of metrics to InfluxDb")
  enableInfluxDb: Boolean = false,
)

object Config {
  val Default: Config = Config()
}
