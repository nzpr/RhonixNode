package db

import org.apache.commons.dbcp2.BasicDataSource

object PgDataSource {
  def apply(config: Config): BasicDataSource = {
    val dataSource = new BasicDataSource()

    dataSource.setDriverClassName("org.postgresql.Driver")
    dataSource.setUrl(config.dbUrl)
    dataSource.setUsername(config.dbUser)
    dataSource.setPassword(config.dbPassword)
    dataSource.setInitialSize(config.initialConnections)
    dataSource.setMaxIdle(config.maxIdleConnections)
    dataSource.setMaxTotal(config.maxTotalConnections)

    dataSource
  }
}
