package sdk.api

import sdk.api.data.Validator

trait ValidatorDbApi[F[_]] {
  def insert(publicKey: Array[Byte]): F[Long]
  def update(validator: Validator): F[Int]

  def getById(id: Long): F[Option[Validator]]
  def getByPublicKey(publicKey: Array[Byte]): F[Option[Validator]]
}
