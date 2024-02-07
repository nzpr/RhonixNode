package sim

import sdk.reflect.Description
import scala.concurrent.duration.{Duration, DurationInt}

@Description("sim")
final case class Config(
  @Description("Number of nodes in the network.")
  size: Int = 4,
  @Description("Number of users transacting in the network.")
  usersNum: Int = 100,
  @Description("Number of transactions in a block.")
  txPerBlock: Int = 1,
  @Description("Artificial delay to execute transaction.")
  exeDelay: Duration = 0.seconds,
  @Description("Artificial delay to propagate the block.")
  propDelay: Duration = 0.seconds,
)

object Config {
  val Default: Config = Config()
}
