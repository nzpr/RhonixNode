package coop.rchain.models

final case class DeployData(
  term: String,
  timestamp: Long,
  phloPrice: Long,
  phloLimit: Long,
  validAfterBlockNumber: Long,
  shardId: String,
) {
  def totalPhloCharge = phloLimit * phloPrice
}
