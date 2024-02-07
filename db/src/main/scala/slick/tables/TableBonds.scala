package slick.tables

import sdk.api.data.Bond
import slick.jdbc.PostgresProfile.api.*
import slick.lifted.ProvenShape

class TableBonds(tag: Tag) extends Table[Bond](tag, "Bonds") {

  // Fields
  def id: Rep[Long]          = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def validatorId: Rep[Long] = column[Long]("validatorId", O.Unique)
  def stake: Rep[Long]       = column[Long]("stake")

  // Projection
  def * : ProvenShape[Bond] = (validatorId, stake).mapTo[Bond]
}
