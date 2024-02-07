package slick.tables

import sdk.api.data.Validator
import slick.jdbc.PostgresProfile.api.*
import slick.lifted.ProvenShape

class TableValidators(tag: Tag) extends Table[Validator](tag, "Validators") {

  // Fields
  def id: Rep[Long]               = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def publicKey: Rep[Array[Byte]] = column[Array[Byte]]("publicKey", O.Unique)

  // Projection
  def * : ProvenShape[Validator] = (id, publicKey).mapTo[Validator]

  // Indices
  def idx = index("idx", publicKey, unique = true)
}
