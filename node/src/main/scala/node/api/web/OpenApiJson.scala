package node.api.web

import cats.effect.kernel.Concurrent
import endpoints4s.http4s.server
import endpoints4s.http4s.server.Endpoints
import endpoints4s.openapi
import endpoints4s.openapi.model.{Info, OpenApi}

import scala.Function.const

/**
  * OpenApi schema definition for RNode Web API v1.
  */
private object OpenApiJsonPublic
    extends EndpointsJsonPublic
    with openapi.Endpoints
    with openapi.JsonEntitiesFromSchemas {

  private val endpoints: Seq[DocumentedEndpoint] = Seq(getBlock, getDeploy, getStatus, getBlock, getLatest)

  val openApi: OpenApi = openApi(Info(title = "RNode API", version = "1.0"))(endpoints*)
}

/**
  * OpenApi endpoint definition (GET /openapi.json).
  */
final case class DocsJsonRoutes[F[_]: Concurrent]()
    extends Endpoints[F]
    with server.JsonEntitiesFromEncodersAndDecoders {
  implicit val jCodec: endpoints4s.Encoder[OpenApi, String] = OpenApi.stringEncoder

  val public =
    endpoint(get(path / "openapi.json"), ok(jsonResponse[OpenApi])).implementedBy(const(OpenApiJsonPublic.openApi))
}
