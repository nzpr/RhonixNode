package slick.api

import cats.effect.Async
import sdk.api.ValidatorDbApi
import sdk.api.data.Validator
import slick.jdbc.JdbcProfile
import slick.{SlickDb, SlickQuery}
import slick.syntax.all.*

class ValidatorDbApiImplSlick[F[_]: Async: SlickDb] extends ValidatorDbApi[F] {
  implicit val p: JdbcProfile = SlickDb[F].profile
  val queries: SlickQuery     = SlickQuery()
  import queries.*

  override def insert(publicKey: Array[Byte]): F[Long] = insertValidator(publicKey).run

  override def update(validator: Validator): F[Int] = updateValidator(validator).run

  override def getById(id: Long): F[Option[Validator]] = getValidatorById(id).run

  override def getByPublicKey(publicKey: Array[Byte]): F[Option[Validator]] = getValidatorByPublicKey(publicKey).run
}
