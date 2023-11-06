package slick

import sdk.api.ExternalApi
import sdk.api.data.{Block, Deploy, Status}

class ExternalApiSlick[F[_]] extends ExternalApi[F] {
  override def getBlockByHash(hash: Array[Byte]): F[Option[Block]]                          = ???
  override def getDeployByHash(hash: Array[Byte]): F[Option[Deploy]]                        = ???
  override def getDeploysByBlockHash(hash: Array[Byte]): F[Option[Seq[Deploy]]]             = ???
  override def getBalance(state: Array[Byte], wallet: Array[Byte]): F[Option[Long]]         = ???
  override def getLatestMessages: F[List[Array[Byte]]]                                      = ???
  override def status: F[Status]                                                            = ???
  override def visualizeDag(depth: Int, showJustificationLines: Boolean): F[Vector[String]] = ???
}
