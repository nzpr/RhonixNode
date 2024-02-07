package slick.migrations

import slick.migration.api.{Dialect, ReversibleMigrationSeq, TableMigration}

// Initial version of the database
object Baseline {
  def apply()(implicit dialect: Dialect[?]): ReversibleMigrationSeq = {
    val validatorTable = TableMigration(slick.validators).create
      .addColumns(_.id, _.publicKey)
      .addIndexes(_.idx)

    val bondTable = TableMigration(slick.bonds).create
      .addColumns(_.id, _.validatorId, _.stake)

    val configTable = TableMigration(slick.config).create
      .addColumns(_.name, _.value)

    validatorTable & bondTable & configTable
  }
}
