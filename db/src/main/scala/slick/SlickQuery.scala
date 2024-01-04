package slick

import cats.syntax.all.*
import sdk.primitive.ByteArray
import slick.dbio.Effect.*
import slick.jdbc.JdbcProfile
import slick.sql.{FixedSqlAction, FixedSqlStreamingAction, SqlAction}
import slick.tables.*

import scala.concurrent.ExecutionContext

final case class SlickQuery(profile: JdbcProfile, ec: ExecutionContext) {
  import profile.api.*

  implicit val _ec: ExecutionContext = ec

  // Insert actions

  def putConfig(key: String, value: String): DBIOAction[Int, NoStream, Write] =
    qConfigs.insertOrUpdate((key, value))

  def insertBlockSet(hash: Array[Byte], blockHashes: Seq[Array[Byte]]): DBIOAction[Long, NoStream, Write] =
    (qBlockSets.map(_.hash) returning qBlockSets.map(_.id)) += hash

  def insertBlockSetBinds(blockSetId: Long, blockIds: Seq[Long]): DBIOAction[Option[Int], NoStream, Write] = {
    val binds = blockIds.map(TableBlockSetBinds.BlockSetBind(blockSetId, _))
    qBlockSetBinds ++= binds
  }

  def validatorInsert(pK: Array[Byte]): DBIOAction[Long, NoStream, Write] =
    (qValidators.map(_.pubKey) returning qValidators.map(_.id)) += pK

  def insertShard(name: String): DBIOAction[Long, NoStream, Write] =
    (qShards.map(_.name) returning qShards.map(_.id)) += name

  def insertBlock(block: TableBlocks.Block): DBIOAction[Long, NoStream, Write] =
    (qBlocks returning qBlocks.map(_.id)) += block

  def insertDeploy(deploy: TableDeploys.Deploy): DBIOAction[Long, NoStream, Write] =
    (qDeploys returning qDeploys.map(_.id)) += deploy

  def insertDeployer(pK: Array[Byte]): DBIOAction[Long, NoStream, Write] =
    (qDeployers.map(_.pubKey) returning qDeployers.map(_.id)) += pK

  // Get queries

  def getConfig(key: Rep[String]): Query[Rep[String], String, Seq] =
    qConfigs.filter(_.name === key).map(_.value)

  def getBondsMapId(hash: Rep[Array[Byte]]): Query[Rep[Long], Long, Seq] =
    qBondsMaps.filter(_.hash === hash).map(_.id)

  def validatorIdByPK(pK: Rep[Array[Byte]]): Query[Rep[Long], Long, Seq] =
    qValidators.filter(_.pubKey === pK).map(_.id)

  /** Get a list of all blocks hashes from DB */
  def blockGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] = qBlocks.map(_.hash)

  def getBlock(hash: Rep[Array[Byte]]) = qBlocks.filter(_.hash === hash)

  def getValidatorPk(validatorId: Rep[Long]) = qValidators.filter(_.id === validatorId).map(_.pubKey)

  def getShardName(shardId: Rep[Long]) = qShards.filter(_.id === shardId).map(_.name)

  def getBlockSetHash(id: Rep[Long]) = qBlockSets.filter(_.id === id).map(_.hash)

  def getDeploySetHash(id: Rep[Long]) = qDeploySets.filter(_.id === id).map(_.hash)

  def getBondsMapHash(id: Rep[Long]) = qBondsMaps.filter(_.id === id).map(_.hash)

  def deployerIdByPK(pK: Array[Byte]): Query[Rep[Long], Long, Seq] =
    qDeployers.filter(_.pubKey === pK).map(_.id)

  def getDeploySetId(hash: Array[Byte]): SqlAction[Option[Long], NoStream, Read] =
    qDeploySets.filter(_.hash === hash).map(_.id).result.headOption

  def blockIdByHash(hash: Array[Byte]): Query[Rep[Long], Long, Seq] = qBlocks.filter(_.hash === hash).map(_.id)

  def insertBondsMapHash(hash: Array[Byte]): DBIOAction[Long, NoStream, Write] =
    (qBondsMaps.map(_.hash) returning qBondsMaps.map(_.id)) += hash

  def insertBondsMap(bondsMapId: Long, bMap: Seq[(Long, Long)]): DBIOAction[Option[Int], NoStream, Write] = {
    val bonds = bMap.map { case (validatorId, stake) =>
      TableBonds.Bond(bondsMapId, validatorId, stake)
    }
    qBonds ++= bonds
  }

  def getValidatorPKById(id: Rep[Long]): Query[Rep[Array[Byte]], Array[Byte], Seq] =
    qValidators.filter(_.id === id).map(_.pubKey)

  def shardIdByName(name: String): Query[Rep[Long], Long, Seq] =
    qShards.filter(_.name === name).map(_.id)

  def insertDeploySetHash(hash: Array[Byte]): DBIOAction[Long, NoStream, Write] =
    (qDeploySets.map(_.hash) returning qDeploySets.map(_.id)) += hash

  /** Deploy */
  /** Get a list of all shard names */
  def shardGetAll: Query[Rep[String], String, Seq] = qShards.map(_.name)

  /** Get a list of all deployer public keys*/
  def deployerGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] = qDeployers.map(_.pubKey)

  /** Get a list of all deploy signatures */
  def deployGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] = qDeploys.map(_.sig)

  /** Get a list of all deploySet hashes from DB*/
  def deploySetGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] = qDeploySets.map(_.hash)

  def getBondIds(bondsMapId: Rep[Long]): Query[(Rep[Long], Rep[Long]), (Long, Long), Seq] =
    qBonds.filter(_.bondsMapId === bondsMapId).map(b => (b.validatorId, b.stake))

  def getDeployIds(deploySetId: Long): DBIOAction[Seq[Long], NoStream, Read] =
    qDeploySetBinds.filter(_.deploySetId === deploySetId).map(_.deployId).result

  /** Get a list of all blockSet hashes from DB*/
  def blockSetGetAll: Query[Rep[Array[Byte]], Array[Byte], Seq] = qBlockSets.map(_.hash)

  def getBlockSet(blockSetId: Long): Query[Rep[Array[Byte]], Array[Byte], Seq] = qBlocks
    .filter(block => block.id in qBlockSetBinds.filter(_.blockSetId === blockSetId).map(_.blockId))
    .map(_.hash)

  /** Get deploy by unique sig. Returned (TableDeploys.Deploy, shard.name, deployer.pubKey)*/
  def getDeployData(sig: Array[Byte]): DBIOAction[Option[api.data.Deploy], NoStream, Read] = {
    val query = for {
      deploy   <- qDeploys if deploy.sig === sig
      shard    <- qShards if shard.id === deploy.shardId
      deployer <- qDeployers if deployer.id === deploy.deployerId
    } yield (deploy, deployer.pubKey, shard.name)
    query.result.headOption.map(_.map { case (d, pK, sName) =>
      api.data.Deploy(
        sig = d.sig,
        deployerPk = pK,
        shardName = sName,
        program = d.program,
        phloPrice = d.phloPrice,
        phloLimit = d.phloLimit,
        nonce = d.nonce,
      )
    })
  }

  /** Insert a new record in table if there is no such entry. Returned id */
  def deployInsertIfNot(d: api.data.Deploy): DBIOAction[Long, NoStream, All] = {
    def deployerInsertIfNot(pK: Array[Byte]): DBIOAction[Long, NoStream, All] =
      insertIfNot(pK, deployerIdByPK, pK, insertDeployer)

    /** Get deploy id by unique signature */
    def deployIdBySig(sig: Array[Byte]): Query[Rep[Long], Long, Seq] =
      qDeploys.filter(_.sig === sig).map(_.id)

    val actions = for {
      deployerId <- deployerInsertIfNot(d.deployerPk)
      shardId    <- shardInsertIfNot(d.shardName)
      newDeploy   = TableDeploys.Deploy(
                      id = 0L, // Will be replaced by AutoInc
                      sig = d.sig,
                      deployerId = deployerId,
                      shardId = shardId,
                      program = d.program,
                      phloPrice = d.phloPrice,
                      phloLimit = d.phloLimit,
                      nonce = d.nonce,
                    )
      deployId   <- insertIfNot(d.sig, deployIdBySig, newDeploy, insertDeploy)
    } yield deployId

    actions.transactionally
  }

  /** Delete deploy by unique sig. And clean up dependencies in Deployers and Shards if possible.
   * Return 1 if deploy deleted, or 0 otherwise. */
  def deleteDeploy(sig: Array[Byte]): DBIOAction[Int, NoStream, All] = {
    def deployerForCleanUp(deployerId: Long): Query[TableDeployers, TableDeployers.Deployer, Seq] = {
      val relatedDeploysExist = qDeploys.filter(_.deployerId === deployerId).exists
      qDeployers.filter(d => (d.id === deployerId) && !relatedDeploysExist)
    }

    def shardForCleanUp(shardId: Long): Query[TableShards, TableShards.Shard, Seq] = {
      val relatedDeploysExist = qDeploys.filter(d => d.shardId === shardId).exists
      qShards.filter(s => (s.id === shardId) && !relatedDeploysExist)
    }

    def deployById(id: Long): Query[TableDeploys, TableDeploys.Deploy, Seq] = qDeploys.filter(_.id === id)

    def deleteAndCleanUp(deployId: Long, deployerId: Long, shardId: Long): DBIOAction[Int, NoStream, Write] = for {
      r <- deployById(deployId).delete
      _ <- deployerForCleanUp(deployerId).delete
      _ <- shardForCleanUp(shardId).delete
    } yield r

    def deployIdsBySig(sig: Array[Byte]): Query[(Rep[Long], Rep[Long], Rep[Long]), (Long, Long, Long), Seq] =
      qDeploys.filter(_.sig === sig).map(d => (d.id, d.deployerId, d.shardId))

    deployIdsBySig(sig).result.headOption.flatMap {
      case Some((deployId, deployerId, shardId)) => deleteAndCleanUp(deployId, deployerId, shardId)
      case None                                  => DBIO.successful(0)
    }.transactionally
  }

  /** DeploySet */
  /** Insert a new deploy set in table if there is no such entry. Returned id */
  def deploySetInsertIfNot(hash: Array[Byte], deploySigs: Seq[Array[Byte]]): DBIOAction[Long, NoStream, All] = {
    def deploySetIdByHash(hash: Array[Byte]): Query[Rep[Long], Long, Seq] =
      qDeploySets.filter(_.hash === hash).map(_.id)

    def getDeployIdsBySigs(sigs: Seq[Array[Byte]]): Query[Rep[Long], Long, Seq] =
      qDeploys.filter(_.sig inSet sigs).map(_.id)

    def insertBinds(deploySetId: Long, deployIds: Seq[Long]): FixedSqlAction[Option[Int], NoStream, Write] =
      qDeploySetBinds ++= deployIds.map(TableDeploySetBinds.DeploySetBind(deploySetId, _))

    def insertAllData(in: (Array[Byte], Seq[Array[Byte]])): DBIOAction[Long, NoStream, Write & Read & Transactional] =
      in match {
        case (hash, deploySigs) =>
          (for {
            deploySetId <- insertDeploySetHash(hash)
            deployIds   <- getDeployIdsBySigs(deploySigs).result
            _           <- if (deployIds.length == deploySigs.length) insertBinds(deploySetId, deployIds)
                           else
                             DBIO.failed(
                               new RuntimeException(
                                 "Signatures of deploys added to deploy set do not match deploys in deploy table",
                               ),
                             )
          } yield deploySetId).transactionally
      }

    insertIfNot(hash, deploySetIdByHash, (hash, deploySigs), insertAllData)
  }

  private def deploySetGetDataById(deploySetId: Long): DBIOAction[Seq[Array[Byte]], NoStream, Read & Transactional] = {

    def getDeploySigsByIds(ids: Seq[Long]) =
      qDeploys.filter(_.id inSet ids).map(_.sig).result

    getDeployIds(deploySetId).flatMap(getDeploySigsByIds).transactionally
  }

  /** Get a list of signatures for deploys included at this deploySet. If there isn't such set - return None*/
  def deploySetGetData(hash: Array[Byte]): DBIOAction[Option[Seq[Array[Byte]]], NoStream, Read & Transactional] =
    getDeploySetId(hash).flatMap {
      case Some(deploySetId) => deploySetGetDataById(deploySetId).map(Some(_))
      case None              => DBIO.successful(None)
    }.transactionally

  /** BlockSet */
  /** Insert a new block set in table if there is no such entry. Returned id */
  def blockSetInsertIfNot(hash: Array[Byte], blockHashes: Seq[Array[Byte]]): DBIOAction[Long, NoStream, All] = {
    def blockSetIdByHash(hash: Array[Byte]): Query[Rep[Long], Long, Seq] =
      qBlockSets.filter(_.hash === hash).map(_.id)

    def getBlockIdsByHashes(hashes: Seq[Array[Byte]]): DBIOAction[Seq[Long], NoStream, Read] =
      qBlocks.filter(_.hash inSet hashes).map(_.id).result

    def insertAllData(in: (Array[Byte], Seq[Array[Byte]])): DBIOAction[Long, NoStream, Write & Read] = in match {
      case (hash, blockHashes) =>
        for {
          blockSetId <- insertBlockSet(hash, blockHashes)
          blockIds   <- getBlockIdsByHashes(blockHashes)
          _          <- if (blockIds.length == blockHashes.length) insertBlockSetBinds(blockSetId, blockIds)
                        else
                          DBIO.failed(
                            new RuntimeException("Hashes of blocks added to block set do not match blocks in block table"),
                          )
        } yield blockSetId
    }

    insertIfNot(hash, blockSetIdByHash, (hash, blockHashes), insertAllData)
  }

  /** Get a list of hashes for blocks included at this blockSet. If there isn't such set - return None */
  def blockSetGetData(hash: Array[Byte]): DBIOAction[Option[Seq[Array[Byte]]], NoStream, Read & Transactional] = {
    def blockSetIdByHash(hash: Array[Byte]): SqlAction[Option[Long], NoStream, Read] =
      qBlockSets.filter(_.hash === hash).map(_.id).result.headOption

    blockSetIdByHash(hash).flatMap {
      case Some(blockSetId) => getBlockSet(blockSetId).map(Some(_))
      case None             => DBIO.successful(None)
    }.transactionally
  }

  /** BondsMap */
  /** Insert a new bonds map in table if there is no such entry. Returned id */
  def bondsMapInsertIfNot(hash: Array[Byte], bMap: Seq[(Array[Byte], Long)]): DBIOAction[Long, NoStream, All] = {
    println(s"bondsMapInsertIfNot: ${ByteArray(hash)}, ${bMap.map(x => (ByteArray(x._1), x._2)).toMap}")
    def bondsMapIdByHash(hash: Array[Byte]): Query[Rep[Long], Long, Seq] =
      qBondsMaps.filter(_.hash === hash).map(_.id)

    def validatorsInsertIfNot1(pKs: Seq[Array[Byte]]): DBIOAction[Seq[Long], NoStream, All] =
      DBIO
        .sequence(pKs.map(validatorInsertIfNot))
        .transactionally // TODO: Can be rewritten in 2 actions, if work with groups of records

    def insertAllData(
      in: (Array[Byte], Seq[(Array[Byte], Long)]),
    ): DBIOAction[Long, NoStream, All] =
      in match {
        case (hash, bMapSeq) =>
          for {
            bondsMapId            <- insertBondsMapHash(hash)
            validatorsIdWithStake <- DBIO.sequence(bMapSeq.map { case (validatorPk, stake) =>
                                       validatorInsertIfNot(validatorPk).map(validatorId => (validatorId, stake))
                                     })
            _                     <- insertBondsMap(bondsMapId, validatorsIdWithStake)
          } yield bondsMapId
      }

    insertIfNot(hash, bondsMapIdByHash, (hash, bMap), insertAllData)
  }

  /** Get a list of all bonds maps hashes from DB */
  def bondsMapGetAll: DBIOAction[Seq[Array[Byte]], NoStream, Read] =
    qBondsMaps.map(_.hash).result

  /** Get a bonds map data by map hash. If there isn't such set - return None */
  def bondsMapGetData(hash: Rep[Array[Byte]]): Query[(Rep[Array[Byte]], Rep[Long]), (Array[Byte], Long), Seq] =
    getBondsMapId(hash)
      .flatMap(getBondIds)
      .flatMap { case (id, stake) => getValidatorPKById(id).map(x => (x, stake)) }

  /** Block */
  /** Insert a new Block in table if there is no such entry. Returned id */
  def blockInsertIfNot(b: api.data.Block): DBIOAction[Long, NoStream, All] = {

    def insertBlockSet(setDataOpt: Option[api.data.SetData]): DBIOAction[Option[Long], NoStream, All] =
      setDataOpt match {
        case Some(setData) => blockSetInsertIfNot(setData.hash, setData.data).map(Some(_))
        case None          => DBIO.successful(None)
      }

    def insertDeploySet(setDataOpt: Option[api.data.SetData]): DBIOAction[Option[Long], NoStream, All] =
      setDataOpt match {
        case Some(setData) => deploySetInsertIfNot(setData.hash, setData.data).map(Some(_))
        case None          => DBIO.successful(None)
      }

    val actions = for {
      validatorId <- validatorInsertIfNot(b.validatorPk)
      shardId     <- shardInsertIfNot(b.shardName)

      justificationSetId <- insertBlockSet(b.justificationSet)
      offencesSet        <- insertBlockSet(b.offencesSet)
      bondsMapId         <- bondsMapInsertIfNot(b.bondsMap.hash, b.bondsMap.data)
      finalFringe        <- insertBlockSet(b.finalFringe)
      deploySetId        <- insertDeploySet(b.execDeploySet)

      mergeSetId      <- insertDeploySet(b.mergeDeploySet)
      dropSetId       <- insertDeploySet(b.dropDeploySet)
      mergeSetFinalId <- insertDeploySet(b.mergeDeploySetFinal)
      dropSetFinalId  <- insertDeploySet(b.dropDeploySetFinal)

      newBlock = TableBlocks.Block(
                   id = 0L,
                   version = b.version,
                   hash = b.hash,
                   sigAlg = b.sigAlg,
                   signature = b.signature,
                   finalStateHash = b.finalStateHash,
                   postStateHash = b.postStateHash,
                   validatorId = validatorId,
                   shardId = shardId,
                   justificationSetId = justificationSetId,
                   seqNum = b.seqNum,
                   offencesSetId = offencesSet,
                   bondsMapId = bondsMapId,
                   finalFringeId = finalFringe,
                   execDeploySetId = deploySetId,
                   mergeDeploySetId = mergeSetId,
                   dropDeploySetId = dropSetId,
                   mergeDeploySetFinalId = mergeSetFinalId,
                   dropDeploySetFinalId = dropSetFinalId,
                 )

      blockId <- insertIfNot(b.hash, blockIdByHash, newBlock, insertBlock)
    } yield blockId

    actions.transactionally
  }

  /** Get deploy by unique sig. Returned (TableDeploys.Deploy, shard.name, deployer.pubKey) */
  def blockGetData(
    hash: Array[Byte],
  ): DBIOAction[Option[api.data.Block], NoStream, Read & Transactional] = {

    def getBlockSetData(idOpt: Option[Long])  = idOpt match {
      case Some(id) =>
        for {
          hash <- getBlockSetHash(id)
          data <- getBlockSet(id)
        } yield Some(api.data.SetData(hash, data))
      case None     => DBIO.successful(None)
    }
    def getDeploySetData(idOpt: Option[Long]) = idOpt match {
      case Some(id) =>
        for {
          hash <- getDeploySetHash(id)
          data <- deploySetGetDataById(id)
        } yield Some(api.data.SetData(hash, data))
      case None     => DBIO.successful(None)
    }
    def getBondsMap(id: Long)                 = for {
      hash <- getBondsMapHash(id)
      data <- getBondsMap(id)
      _     = println(s"getBondsMapData: ${ByteArray(hash)}, ${data.map(x => (ByteArray(x._1), x._2)).toMap}")
    } yield api.data.BondsMapData(hash, data)

    def getBlockData(b: TableBlocks.Block) = for {
      validatorPK          <- getValidatorPk(b.validatorId)
      shardName            <- getShardName(b.shardId)
      justificationSetData <- getBlockSetData(b.justificationSetId)
      offencesSetData      <- getBlockSetData(b.offencesSetId)
      bondsMapData         <- getBondsMap(b.bondsMapId)
      finalFringeData      <- getBlockSetData(b.finalFringeId)
      deploySetData        <- getDeploySetData(b.execDeploySetId)
      mergeSetData         <- getDeploySetData(b.mergeDeploySetId)
      dropSetData          <- getDeploySetData(b.dropDeploySetId)
      mergeSetFinalData    <- getDeploySetData(b.mergeDeploySetFinalId)
      dropSetFinalData     <- getDeploySetData(b.dropDeploySetFinalId)
    } yield api.data.Block(
      version = b.version,
      hash = b.hash,
      sigAlg = b.sigAlg,
      signature = b.signature,
      finalStateHash = b.finalStateHash,
      postStateHash = b.postStateHash,
      validatorPk = validatorPK,
      shardName = shardName,
      justificationSet = justificationSetData,
      seqNum = b.seqNum,
      offencesSet = offencesSetData,
      bondsMap = bondsMapData,
      finalFringe = finalFringeData,
      execDeploySet = deploySetData,
      mergeDeploySet = mergeSetData,
      dropDeploySet = dropSetData,
      mergeDeploySetFinal = mergeSetFinalData,
      dropDeploySetFinal = dropSetFinalData,
    )

    getBlock(hash).flatMap {
      case Some(block) => getBlockData(block).map(_.some)
      case None        => DBIO.successful(None)
    }.transactionally
  }

  private def validatorInsertIfNot(pK: Array[Byte]): DBIOAction[Long, NoStream, All] =
    insertIfNot(pK, validatorIdByPK, pK, validatorInsert)

  private def shardInsertIfNot(name: String): DBIOAction[Long, NoStream, All] =
    insertIfNot(name, shardIdByName, name, insertShard)

  def insertIfNotExists[A <: Table[B], B](table: TableQuery[A], x: B) = {
    val exists           = table.filter(m => true).exists
    val selectExpression = data.filterNot(_ => exists)
    messages.map(m => m.sender -> m.content).forceInsertQuery(selectExpression)
  }

  /** Insert a new record in table if there is no such entry. Returned id */
  private def insertIfNot[A, B](
    unique: A,
    getIdByUnique: A => Query[Rep[Long], Long, Seq],
    insertable: B,
    insert: B => DBIOAction[Long, NoStream, All],
  ): DBIOAction[Long, NoStream, All] =
    getIdByUnique(unique).result.headOption.flatMap {
      case Some(existingId) => DBIO.successful(existingId)
      case None             => insert(insertable)
    }.transactionally
}
