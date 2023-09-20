package sim

import scala.concurrent.duration.Duration

final case class Config(
  size: Int,                  // size of the network
  processingConcurrency: Int, // number of messages node can process in parallel
  exeDelay: Duration,         // time to replay the message
  hashDelay: Duration,        // time to hash and sign the message
  propDelay: Duration,        // time to propagate the message id to other nodes
  rcvDelay: Duration,         // time to fetch full message given message id
  stateReadTime: Duration,    // time to read from the blockchain state the data necessary for consensus
  //    lazinessTolerance: Int, // TODO Number of fringes node has to keep to be able to process late message (Partially sync consensus).
)
