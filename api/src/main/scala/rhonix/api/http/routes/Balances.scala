package rhonix.api.http.routes

import cats.effect.Sync
import cats.implicits.toFlatMapOps
import org.http4s.{EntityEncoder, HttpRoutes}
import rhonix.api
import sdk.api.FindApi
import sdk.syntax.all.*

object Balances {
  def apply[F[_]: Sync, T](balanceApi: FindApi[F, String, T])(implicit ei: EntityEncoder[F, T]): HttpRoutes[F] = {
    val dsl = org.http4s.dsl.Http4sDsl[F]
    import dsl.*

    HttpRoutes.of[F] { case GET -> Root / api.prefix / "balance" / id =>
      balanceApi.get(id).flatMap(x => Ok(x))
    }
  }
}
