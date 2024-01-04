package slick

import slick.jdbc.JdbcProfile
import slick.tables.*

case class Queries(profile: JdbcProfile) {
  import profile.api.*

  /** Deploy */
  /** Get a list of all shard names */
  def shardGetAll: Query[Rep[String], String, Seq] =
    qShards.map(_.name)

  /** Get a list of all deployer public keys*/
  def deployerGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] =
    qDeployers.map(_.pubKey)

  def deployerForCleanUp(deployerId: Long): Query[TableDeployers, TableDeployers.Deployer, Seq] = {
    val relatedDeploysExist = qDeploys.filter(_.deployerId === deployerId).exists
    qDeployers.filter(d => (d.id === deployerId) && !relatedDeploysExist)
  }

  def getBlockIdByHash(hash: Array[Byte]): Query[Rep[Long], Long, Seq] =
    qBlocks.filter(_.hash === hash).map(_.id)

  def blockSetIdByHash(hash: Array[Byte]): Query[Rep[Long], Long, Seq] =
    qBlockSets.filter(_.hash === hash).map(_.id)

  /** Get a list of signatures for deploys included at this deploySet. If there isn't such set - return None*/
  def deploySetGetData(hash: Array[Byte]): Query[Rep[Array[Byte]], Array[Byte], Seq] =
    getDeploySetId(hash).flatMap(deploySetGetDataById)

  /** Get a list of all deploy signatures */
  def deployGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] = qDeploys.map(_.sig)

  /** Get a list of all deploySet hashes from DB*/
  def deploySetGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] = qDeploySets.map(_.hash)

  def getBondIds(bondsMapId: Rep[Long]): Query[(Rep[Long], Rep[Long]), (Long, Long), Seq] =
    qBonds.filter(_.bondsMapId === bondsMapId).map(b => (b.validatorId, b.stake))

  def getDeployIds(deploySetId: Rep[Long]): Query[Rep[Long], Long, Seq] =
    qDeploySetBinds.filter(_.deploySetId === deploySetId).map(_.deployId)

  def blockSetIdByHash(hash: Rep[Array[Byte]]): Query[Rep[Long], Long, Seq] =
    qBlockSets.filter(_.hash === hash).map(_.id)

  /** Get a list of all bonds maps hashes from DB */
  def bondsMapGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] =
    qBondsMaps.map(_.hash)

  /** Get deploy id by unique signature */
  def deployIdBySig(sig: Rep[Array[Byte]]): Query[Rep[Long], Long, Seq] =
    qDeploys.filter(_.sig === sig).map(_.id)

  /** Get a list of all blockSet hashes from DB*/
  def blockSetGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] = qBlockSets.map(_.hash)

  def deploySetIdByHash(hash: Array[Byte]): Query[Rep[Long], Long, Seq] =
    qDeploySets.filter(_.hash === hash).map(_.id)

  def deploySetGetDataById(deploySetId: Rep[Long]): Query[Rep[Array[Byte]], Array[Byte], Seq] =
    getDeployIds(deploySetId).flatMap(x => getDeploySigsByIds(x))

  def getDeployIdsBySigs(sigs: Rep[Array[Byte]]): Query[Rep[Long], Long, Seq] =
    qDeploys.filter(_.sig === sigs).map(_.id)

  def getBlockSet(blockSetId: Rep[Long]): Query[Rep[Array[Byte]], Array[Byte], Seq] =
    qBlocks
      .filter(block => block.id in qBlockSetBinds.filter(_.blockSetId === blockSetId).map(_.blockId))
      .map(_.hash)

  def getDeploySigsByIds(ids: Rep[Long]): Query[Rep[Array[Byte]], Array[Byte], Seq] =
    qDeploys.filter(_.id === ids).map(_.sig)

  def getValidatorPKById(id: Rep[Long]): Query[Rep[Array[Byte]], Array[Byte], Seq] =
    qValidators.filter(_.id === id).map(_.pubKey)

  def deployerIdByPK(pK: Array[Byte]): Query[Rep[Long], Long, Seq] =
    qDeployers.filter(_.pubKey === pK).map(_.id)

  def getDeploySetId(hash: Array[Byte]): Query[Rep[Long], Long, Seq] =
    qDeploySets.filter(_.hash === hash).map(_.id)

  def blockIdByHash(hash: Array[Byte]): Query[Rep[Long], Long, Seq] = qBlocks.filter(_.hash === hash).map(_.id)

  def deployIdsBySig(sig: Array[Byte]): Query[(Rep[Long], Rep[Long], Rep[Long]), (Long, Long, Long), Seq] =
    qDeploys.filter(_.sig === sig).map(d => (d.id, d.deployerId, d.shardId))

  /** Get a list of hashes for blocks included at this blockSet. If there isn't such set - return None */
  def blockSetGetData(hash: Array[Byte]): Query[Rep[Array[Byte]], Array[Byte], Seq] =
    blockSetIdByHash(hash).flatMap(getBlockSet)

  def bondsMapIdByHash(hash: Array[Byte]): Query[Rep[Long], Long, Seq] =
    qBondsMaps.filter(_.hash === hash).map(_.id)

  /** Get a list of all blocks hashes from DB */
  def blockGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] =
    qBlocks.map(_.hash)

  def shardIdByName(name: Rep[String]): Query[Rep[Long], Long, Seq] =
    qShards.filter(_.name === name).map(_.id)

  def validatorIdByPK(pK: Array[Byte]): Query[Rep[Long], Long, Seq] =
    qValidators.filter(_.pubKey === pK).map(_.id)

}
