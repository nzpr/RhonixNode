package rhonix.api.http.routes

import cats.effect.Sync
import cats.syntax.all.*
import org.http4s.{EntityEncoder, HttpRoutes}
import rhonix.api
import sdk.api.*
import sdk.syntax.all.*

object All {
  def apply[F[_]: Sync, T](blockApi: BlockDbApi[F], deployApi: BlockDeploysDbApi[F], balanceApi: FindApi[F, String, T])(
    implicit ei: EntityEncoder[F, T],
  ): HttpRoutes[F] = {
    val dsl = org.http4s.dsl.Http4sDsl[F]
    import dsl.*

    // This derives encoders for response types
    import io.circe.generic.auto.*
    import org.http4s.circe.CirceEntityCodec.circeEntityEncoder

    HttpRoutes.of[F] {
      case GET -> Root / api.prefix / "balance" / id => balanceApi.get(id).flatMap(x => Ok(x))
      case GET -> Root / api.prefix / "block" / id   =>
        val hash = java.util.Base64.getDecoder.decode(id)
        blockApi.getByHash(hash).flatMap(_.map(Ok(_)).getOrElse(NotFound()))
      case GET -> Root / api.prefix / "deploys" / id =>
        deployApi.getByBlock(id.toLong).flatMap(Ok(_))
    }
  }
}
