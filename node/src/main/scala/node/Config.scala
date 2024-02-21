package node

import sdk.reflect.Description

import java.nio.file.Path

@Description("node")
final case class Config(
  @Description("Persist on chain state on disk.")
  persistOnChainState: Boolean = false,
  @Description("Path to key value stores folder.")
  kvStoresPath: Path = Path.of("~/.gorki/kv-store"),
  @Description("Limit number of blocks to be processed concurrently")
  processingConcurrency: Int = 4,
  @Description("Enable streaming of metrics to InfluxDb")
  enableInfluxDb: Boolean = false,
  @Description("Enable dev mode. WARNING: This mode is not secure and should not be used in production.")
  devMode: Boolean = false,
  @Description("WebApi configuration")
  webApi: node.api.web.Config = node.api.web.Config("localhost", 8080),
  @Description("Node gRPC port")
  gRpcPort: Int = 5555,
)

object Config {
  val Default: Config = Config()
}
