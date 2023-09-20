package io.rhonix.node.api.http.routes

import cats.effect.Sync
import cats.implicits.toFlatMapOps
import io.rhonix.node.api.http.ApiPath
import org.http4s.{EntityEncoder, HttpRoutes}
import sdk.api.{BalancesApi, FindApi}
import sdk.syntax.all.*

object Balances {
  def apply[F[_]: Sync, T](balanceApi: FindApi[F, String, T])(implicit ei: EntityEncoder[F, T]): HttpRoutes[F] = {
    val dsl = org.http4s.dsl.Http4sDsl[F]
    import dsl.*

    HttpRoutes.of[F] { case GET -> ApiPath / BalancesApi.MethodName / id =>
      balanceApi.get(id).flatMap(x => Ok(x))
    }
  }
}
