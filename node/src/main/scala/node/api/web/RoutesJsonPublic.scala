package node.api.web

import cats.effect.Concurrent
import cats.syntax.all.*
import endpoints4s.http4s.server.{Endpoints, JsonEntitiesFromSchemas}
import org.http4s.HttpRoutes
import sdk.api.ExternalApi
import sdk.api.data.Balance
import sdk.codecs.Base16

import scala.Function.const

/** Public JSON API routes. */
final case class RoutesJsonPublic[F[_]: Concurrent](api: ExternalApi[F])
    extends Endpoints[F]
    with JsonEntitiesFromSchemas
    with EndpointsJsonPublic {

  private def getBalanceByStrings(state: String, wallet: String): F[Option[Balance]] =
    (Base16.decode(state), Base16.decode(wallet)).bisequence.toOption
      .map(api.getBalance.tupled)
      .getOrElse(none[Long].pure[F])
      .map(_.map(new Balance(_)))

  val routes: HttpRoutes[F] = HttpRoutes.of(
    routesFromEndpoints(
      getBlock.implementedByEffect(Base16.decode(_).toOption.flatTraverse(api.getBlockByHash)),
      getDeploy.implementedByEffect(Base16.decode(_).toOption.flatTraverse(api.getDeployByHash)),
      getBalance.implementedByEffect(getBalanceByStrings.tupled),
      getLatest.implementedByEffect(const(api.getLatestMessages)),
      getStatus.implementedByEffect(const(api.status)),
      DocsJsonRoutes().public,
    ),
  )
}
