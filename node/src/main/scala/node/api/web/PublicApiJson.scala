package node.api.web

import cats.effect.Concurrent
import endpoints4s.http4s.server.Endpoints
import node.api.web.endpoints.PublicApiEndpoints
import node.api.web.json.JsonEntitiesCirceFromSchema
import org.http4s.{EntityEncoder, HttpRoutes}

/** Public JSON API endpoints. */
final case class PublicApiJson[F[_]: Concurrent, B, D](
  blockF: String => F[B],
  deployF: String => F[D],
  balanceF: (String, String) => F[Long],
  latestF: F[Set[String]],
  statusF: F[String],
)(implicit
  blockEncoder: EntityEncoder[F, B],
  deployEncoder: EntityEncoder[F, D],
  balanceEncoder: EntityEncoder[F, Long],
) extends Endpoints[F]
    with JsonEntitiesCirceFromSchema
    with PublicApiEndpoints {

  val routes: HttpRoutes[F] = HttpRoutes.of(
    routesFromEndpoints(
      block[B].implementedByEffect(blockF),
      deploy[D].implementedByEffect(deployF),
      balance[Long].implementedByEffect(balanceF.tupled),
      latest[Set[String]].implementedByEffect(_ => latestF),
      status[String].implementedByEffect(_ => statusF),
    ),
  )
}
