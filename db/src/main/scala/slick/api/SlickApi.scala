package slick.api

import cats.effect.Async
import cats.syntax.all.*
import sdk.primitive.ByteArray
import slick.syntax.all.*
import slick.{Actions, SlickDb}

import scala.concurrent.ExecutionContext

object SlickApi {
  def apply[F[_]: Async](db: SlickDb): F[SlickApi[F]] =
    Async[F].executionContext.map(ec => new SlickApi(db, ec))
}

class SlickApi[F[_]: Async](db: SlickDb, ec: ExecutionContext) {
  implicit val slickDb: SlickDb = db

  val actions: Actions = Actions(db.profile, ec)

  def deployInsert(d: sdk.data.Deploy): F[Unit] =
    actions
      .deployInsertIfNot(
        data.Deploy(d.sig.bytes, d.deployerPk.bytes, d.shardName, d.program, d.phloPrice, d.phloLimit, d.nonce),
      )
      .run
      .void

  def deployGet(sig: ByteArray): F[Option[sdk.data.Deploy]] = actions
    .deployGetData(sig.bytes)
    .run
    .map(
      _.map(d =>
        sdk.data.Deploy(
          sig = ByteArray(d.sig),
          deployerPk = ByteArray(d.deployerPk),
          shardName = d.shardName,
          program = d.program,
          phloPrice = d.phloPrice,
          phloLimit = d.phloLimit,
          nonce = d.nonce,
        ),
      ),
    )

  /** DeploySet */
  def deploySetInsert(deploySetHash: ByteArray, deploySigs: Set[ByteArray]): F[Unit] =
    actions.deploySetInsertIfNot(deploySetHash.bytes, deploySigs.toSeq.map(_.bytes)).run.void

  /** BlockSet */
  def blockSetInsert(blockSetHash: ByteArray, blockHashes: Set[ByteArray]): F[Unit] =
    actions.blockSetInsertIfNot(blockSetHash.bytes, blockHashes.toSeq.map(_.bytes)).run.void

  /** BondsMap */
  def bondsMapInsert(bondsMapHash: ByteArray, bMap: Map[ByteArray, Long]): F[Unit] =
    actions.bondsMapInsertIfNot(bondsMapHash.bytes, bMap.toSeq.map(x => (x._1.bytes, x._2))).run.void

  def blockInsert(b: sdk.data.Block)(
    justificationSetHash: Option[ByteArray],
    offencesSetHash: Option[ByteArray],
    bondsMapHash: ByteArray,
    finalFringeHash: Option[ByteArray],
    execDeploySetHash: Option[ByteArray],
    mergeDeploySetHash: Option[ByteArray],
    dropDeploySetHash: Option[ByteArray],
    mergeDeploySetFinalHash: Option[ByteArray],
    dropDeploySetFinalHash: Option[ByteArray],
  ): F[Unit] = actions
    .blockInsertIfNot(
      data.Block(
        version = b.version,
        hash = b.hash.bytes,
        sigAlg = b.sigAlg,
        signature = b.signature.bytes,
        finalStateHash = b.finalStateHash.bytes,
        postStateHash = b.postStateHash.bytes,
        validatorPk = b.validatorPk.bytes,
        shardName = b.shardName,
        justificationSet = justificationSetHash.map(h => data.SetData(h.bytes, b.justificationSet.toSeq.map(_.bytes))),
        seqNum = b.seqNum,
        offencesSet = offencesSetHash.map(h => data.SetData(h.bytes, b.offencesSet.toSeq.map(_.bytes))),
        bondsMap = data.BondsMapData(bondsMapHash.bytes, b.bondsMap.toSeq.map(x => (x._1.bytes, x._2))),
        finalFringe = finalFringeHash.map(h => data.SetData(h.bytes, b.finalFringe.toSeq.map(_.bytes))),
        execDeploySet = execDeploySetHash.map(h => data.SetData(h.bytes, b.execDeploySet.toSeq.map(_.bytes))),
        mergeDeploySet = mergeDeploySetHash.map(h => data.SetData(h.bytes, b.mergeDeploySet.toSeq.map(_.bytes))),
        dropDeploySet = dropDeploySetHash.map(h => data.SetData(h.bytes, b.dropDeploySet.toSeq.map(_.bytes))),
        mergeDeploySetFinal =
          mergeDeploySetFinalHash.map(h => data.SetData(h.bytes, b.mergeDeploySetFinal.toSeq.map(_.bytes))),
        dropDeploySetFinal =
          dropDeploySetFinalHash.map(h => data.SetData(h.bytes, b.dropDeploySetFinal.toSeq.map(_.bytes))),
      ),
    )
    .run
    .void

  def blockGet(hash: ByteArray): F[Option[sdk.data.Block]] = actions
    .blockGetData(hash.bytes)
    .run
    .map(
      _.map(b =>
        sdk.data.Block(
          version = b.version,
          hash = ByteArray(b.hash),
          sigAlg = b.sigAlg,
          signature = ByteArray(b.signature),
          finalStateHash = ByteArray(b.finalStateHash),
          postStateHash = ByteArray(b.postStateHash),
          validatorPk = ByteArray(b.validatorPk),
          shardName = b.shardName,
          justificationSet = b.justificationSet.map(_.data.map(ByteArray(_)).toSet).getOrElse(Set()),
          seqNum = b.seqNum,
          offencesSet = b.offencesSet.map(_.data.map(ByteArray(_)).toSet).getOrElse(Set()),
          bondsMap = b.bondsMap.data.map(x => (ByteArray(x._1), x._2)).toMap,
          finalFringe = b.finalFringe.map(_.data.map(ByteArray(_)).toSet).getOrElse(Set()),
          execDeploySet = b.execDeploySet.map(_.data.map(ByteArray(_)).toSet).getOrElse(Set()),
          mergeDeploySet = b.mergeDeploySet.map(_.data.map(ByteArray(_)).toSet).getOrElse(Set()),
          dropDeploySet = b.dropDeploySet.map(_.data.map(ByteArray(_)).toSet).getOrElse(Set()),
          mergeDeploySetFinal = b.mergeDeploySetFinal.map(_.data.map(ByteArray(_)).toSet).getOrElse(Set()),
          dropDeploySetFinal = b.dropDeploySetFinal.map(_.data.map(ByteArray(_)).toSet).getOrElse(Set()),
        ),
      ),
    )
}
