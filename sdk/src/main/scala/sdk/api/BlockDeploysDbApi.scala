package sdk.api

import sdk.api.data.BlockDeploys

trait BlockDeploysDbApi[F[_]] {
  def insert(blockDeploys: BlockDeploys): F[Unit]
  def getByBlock(blockId: Long): F[Seq[BlockDeploys]]
}

object BlockDeploysDbApi {
  val MethodName: String = "deploys"
}
