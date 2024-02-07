package slick

import slick.migration.api.Dialect

/// Package for recording database migrations.
package object migrations {

  // This should be appended only.
  def all(implicit dialect: Dialect[?]) = Baseline()
}
