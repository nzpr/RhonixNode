package slick

import cats.syntax.all.*
import sdk.primitive.ByteArray
import slick.dbio.Effect.*
import slick.jdbc.JdbcProfile
import slick.tables.*

import scala.concurrent.ExecutionContext

final case class SlickQuery1(profile: JdbcProfile, ec: ExecutionContext) {
  import profile.api.*
  private val a: Queries = Queries(profile)
  import a.*

  implicit val _ec: ExecutionContext = ec

  def putConfig(key: String, value: String): DBIOAction[Int, NoStream, Write] =
    qConfigs.insertOrUpdate((key, value))

  def getConfig(key: String): DBIOAction[Option[String], NoStream, Read] =
    qConfigs.filter(_.name === key).map(_.value).result.headOption

  def insertDeploySetHash(hash: Array[Byte]): DBIOAction[Long, NoStream, Write] =
    (qDeploySets.map(_.hash) returning qDeploySets.map(_.id)) += hash

  def getBondsMapId(hash: Array[Byte]): DBIOAction[Option[Long], NoStream, Read] =
    qBondsMaps.filter(_.hash === hash).map(_.id).result.headOption

  def insertBlockSet(hash: Array[Byte], blockHashes: Seq[Array[Byte]]): DBIOAction[Long, NoStream, Write] =
    (qBlockSets.map(_.hash) returning qBlockSets.map(_.id)) += hash

  def insertBlockSetBinds(blockSetId: Long, blockIds: Seq[Long]): DBIOAction[Option[Int], NoStream, Write] = {
    val binds = blockIds.map(TableBlockSetBinds.BlockSetBind(blockSetId, _))
    qBlockSetBinds ++= binds
  }

  def deployerInsertIfNot(pK: Array[Byte]): DBIOAction[Long, NoStream, All] =
    insertIfNot(pK, deployerIdByPK, pK, insertDeployer)

  def insertBinds(deploySetId: Long, deployIds: Seq[Long]): DBIOAction[Option[Int], NoStream, Write] =
    qDeploySetBinds ++= deployIds.map(TableDeploySetBinds.DeploySetBind(deploySetId, _))

  def insertBlock(block: TableBlocks.Block): DBIOAction[Long, NoStream, Write] =
    (qBlocks returning qBlocks.map(_.id)) += block

  def insertDeploy(deploy: TableDeploys.Deploy): DBIOAction[Long, NoStream, Write] =
    (qDeploys returning qDeploys.map(_.id)) += deploy

  def insertDeployer(pK: Array[Byte]): DBIOAction[Long, NoStream, Write] =
    (qDeployers.map(_.pubKey) returning qDeployers.map(_.id)) += pK

  def insertBondsMapHash(hash: Array[Byte]): DBIOAction[Long, NoStream, Write] =
    (qBondsMaps.map(_.hash) returning qBondsMaps.map(_.id)) += hash

  def insertBondsMap(bondsMapId: Long, bMap: Seq[(Long, Long)]): DBIOAction[Option[Int], NoStream, Write] = {
    val bonds = bMap.map { case (validatorId, stake) => TableBonds.Bond(bondsMapId, validatorId, stake) }
    qBonds ++= bonds
  }

  /** Get deploy by unique sig. Returned (TableDeploys.Deploy, shard.name, deployer.pubKey)*/
  def getDeployData(sig: Rep[Array[Byte]]) = {
    val query = for {
      deploy   <- qDeploys.filter(x => x.sig === sig)
      shard    <- qShards.filter(x => x.id === deploy.shardId)
      deployer <- qDeployers.filter(x => x.id === deploy.deployerId)
    } yield api.data.Deploy(
      sig = deploy.sig,
      deployerPk = deployer.pubKey,
      shardName = shard.name,
      program = deploy.program,
      phloPrice = deploy.phloPrice,
      phloLimit = deploy.phloLimit,
      nonce = deploy.nonce,
    )
  }

  /** Insert a new record in table if there is no such entry. Returned id */
  def deployInsertIfNot(d: api.data.Deploy): DBIOAction[Long, NoStream, All] = {

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
    def deleteAndCleanUp(deployId: Long, deployerId: Long, shardId: Long): DBIOAction[Int, NoStream, Write] = for {
      r <- deployById(deployId).delete
      _ <- deployerForCleanUp(deployerId).delete
      _ <- shardForCleanUp(shardId).delete
    } yield r

    deployIdsBySig(sig).result.headOption.flatMap {
      case Some((deployId, deployerId, shardId)) => deleteAndCleanUp(deployId, deployerId, shardId)
      case None                                  => DBIO.successful(0)
    }.transactionally
  }

  /** DeploySet */
  /** Insert a new deploy set in table if there is no such entry. Returned id */
  def deploySetInsertIfNot(hash: Array[Byte], deploySigs: Seq[Array[Byte]]): DBIOAction[Long, NoStream, All] = {

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

  /** BlockSet */
  /** Insert a new block set in table if there is no such entry. Returned id */
  def blockSetInsertIfNot(hash: Array[Byte], blockHashes: Seq[Array[Byte]]): DBIOAction[Long, NoStream, All] = {
    def insertAllData(in: (Array[Byte], Seq[Array[Byte]])): DBIOAction[Long, NoStream, Write & Read] = in match {
      case (hash, blockHashes) =>
        for {
          blockSetId <- insertBlockSet(hash, blockHashes)
          blockIds   <- getBlockIdByHash(blockHashes)
          _          <- if (blockIds.length == blockHashes.length) insertBlockSetBinds(blockSetId, blockIds)
                        else
                          DBIO.failed(
                            new RuntimeException("Hashes of blocks added to block set do not match blocks in block table"),
                          )
        } yield blockSetId
    }

    insertIfNot(hash, blockSetIdByHash, (hash, blockHashes), insertAllData)
  }

  /** BondsMap */
  /** Insert a new bonds map in table if there is no such entry. Returned id */
  def bondsMapInsertIfNot(hash: Array[Byte], bMap: Seq[(Array[Byte], Long)]): DBIOAction[Long, NoStream, All] = {
    println(s"bondsMapInsertIfNot: ${ByteArray(hash)}, ${bMap.map(x => (ByteArray(x._1), x._2)).toMap}")

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

  private def getBondsMapDataById(bondsMapId: Long): DBIOAction[Seq[(Array[Byte], Long)], NoStream, Read] =
    for {
      bondMapIds            <- getBondIds(bondsMapId)
      validatorsPkWithStake <- DBIO.sequence(bondMapIds.map { case (validatorId, stake) =>
                                 getValidatorPKById(validatorId).map(validatorPk => (validatorPk, stake))
                               })
    } yield validatorsPkWithStake.collect { case (Some(pk), stake) => (pk, stake) }

  /** Get a bonds map data by map hash. If there isn't such set - return None */
  def bondsMapGetData(hash: Array[Byte]): DBIOAction[Option[Seq[(Array[Byte], Long)]], NoStream, All] =
    getBondsMapId(hash).flatMap {
      case Some(bondsMapId) =>
        getBondsMapDataById(bondsMapId).map { x =>
          Some(x)
        }
      case None             => DBIO.successful(None)
    }.transactionally

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
    def getBlock(hash: Array[Byte])       = qBlocks.filter(_.hash === hash).result.headOption
    def getValidatorPk(validatorId: Long) = qValidators.filter(_.id === validatorId).map(_.pubKey).result.head
    def getShardName(shardId: Long)       = qShards.filter(_.id === shardId).map(_.name).result.head
    def getBlockSetHash(id: Long)         = qBlockSets.filter(_.id === id).map(_.hash).result.head
    def getDeploySetHash(id: Long)        = qDeploySets.filter(_.id === id).map(_.hash).result.head
    def getBondsMapHash(id: Long)         = qBondsMaps.filter(_.id === id).map(_.hash).result.head

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
    def getBondsMapData(id: Long)             = for {
      hash <- getBondsMapHash(id)
      data <- getBondsMapDataById(id)
    } yield api.data.BondsMapData(hash, data)

    def getBlockData(b: TableBlocks.Block) = for {
      validatorPK          <- getValidatorPk(b.validatorId)
      shardName            <- getShardName(b.shardId)
      justificationSetData <- getBlockSetData(b.justificationSetId)
      offencesSetData      <- getBlockSetData(b.offencesSetId)
      bondsMapData         <- getBondsMapData(b.bondsMapId)
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
    insertIfNot(pK, validatorIdByPK, pK, (qValidators.map(_.pubKey) returning qValidators.map(_.id)) += pK)

  private def shardInsertIfNot(name: String): DBIOAction[Long, NoStream, All] =
    insertIfNot(name, shardIdByName, name, (qShards.map(_.name) returning qShards.map(_.id)) += name)

  /** Insert a new record in table if there is no such entry. Returned id */
  private def insertIfNot[A, B](
    unique: Rep[A],
    getIdByUnique: Rep[A] => Query[Rep[Long], Long, Seq],
    insertable: B,
    insert: B => DBIOAction[Long, NoStream, All],
  ): DBIOAction[Long, NoStream, All] =
    getIdByUnique(unique).result.headOption.flatMap {
      case Some(existingId) => DBIO.successful(existingId)
      case None             => insert(insertable)
    }.transactionally
}
