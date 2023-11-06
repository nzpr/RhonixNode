package node.api.web

import cats.syntax.all.*
import endpoints4s.algebra
import node.api.web.json.ExternalApiJsonSchemas
import sdk.api.data.{Balance, Block, Deploy, Status}

/** Public JSON API endpoints. */
trait EndpointsJsonPublic extends algebra.Endpoints with algebra.JsonEntitiesFromSchemas with ExternalApiJsonSchemas {

  private lazy val deployId: Path[String]  = segment[String](name = "id", docs = "id of a deploy".some)
  private lazy val blockId: Path[String]   = segment[String](name = "id", docs = "id of a block".some)
  private lazy val stateHash: Path[String] = segment[String](name = "hash", docs = "hash of a state".some)
  private lazy val walletId: Path[String]  = segment[String](name = "id", docs = "wallet id".some)

  private val notFoundDocumentation: Option[String] = "Not found".some

  def getBlock: Endpoint[String, Option[Block]] = endpoint(
    get(path / "block" / blockId),
    ok(jsonResponse[Block]).orNotFound(notFoundDocumentation),
    docs = EndpointDocs().withDescription("Get block my id".some),
  )

  def getDeploy: Endpoint[String, Option[Deploy]] = endpoint(
    get(path / "deploy" / deployId),
    ok(jsonResponse[Deploy]).orNotFound(notFoundDocumentation),
    docs = EndpointDocs().withDescription("Get deploy by id".some),
  )

  def getLatest: Endpoint[Unit, List[Array[Byte]]] = endpoint(
    get(path / "latest"),
    ok(jsonResponse[List[Array[Byte]]]),
    docs = EndpointDocs().withDescription("Latest messages".some),
  )

  def getBalance: Endpoint[(String, String), Option[Balance]] = endpoint(
    get(path / "balance" / stateHash / walletId),
    ok(jsonResponse[Balance]).orNotFound(notFoundDocumentation),
    docs = EndpointDocs().withDescription("Balance of a wallet at the state specified".some),
  )

  def getStatus: Endpoint[Unit, Status] = endpoint(
    get(path / "status"),
    ok(jsonResponse[Status]),
    docs = EndpointDocs().withDescription("Status".some),
  )
}
