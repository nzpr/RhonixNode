package sdk.api

import sdk.api.data.*

trait ExternalApi[F[_]] {
  def getBlockByHash(hash: Array[Byte]): F[Option[Block]]
  def getDeployByHash(hash: Array[Byte]): F[Option[Deploy]]
  def getDeploysByBlockHash(hash: Array[Byte]): F[Option[Seq[Deploy]]]
  def getBalance(state: Array[Byte], wallet: Array[Byte]): F[Option[Long]]
  def getLatestMessages: F[List[Array[Byte]]]
  def status: F[Status]
  def visualizeDag(depth: Int, showJustificationLines: Boolean): F[Vector[String]]
}
