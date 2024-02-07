package node.api.web.endpoints

import cats.syntax.all.*
import endpoints4s.algebra
import node.api.web.json.JsonSchemasPretty

trait PublicApiEndpoints extends algebra.Endpoints with algebra.JsonEntitiesFromSchemas with JsonSchemasPretty {

  lazy val deployId: Path[String]  = segment[String](name = "id", docs = "id of a deploy".some)
  lazy val blockId: Path[String]   = segment[String](name = "id", docs = "id of a block".some)
  lazy val stateHash: Path[String] = segment[String](name = "hash", docs = "hash of a state".some)
  lazy val walletId: Path[String]  = segment[String](name = "id", docs = "wallet id".some)

  def block[A](implicit r: ResponseEntity[A]): Endpoint[String, A] = endpoint(
    get(path / "block" / blockId),
    ok(r),
    docs = EndpointDocs().withDescription("Get block my id".some),
  )

  def deploy[A](implicit r: ResponseEntity[A]): Endpoint[String, A] = endpoint(
    get(path / "deploy" / deployId),
    ok(r),
    docs = EndpointDocs().withDescription("Get deploy by id".some),
  )

  def latest[A](implicit r: ResponseEntity[A]): Endpoint[Unit, A] = endpoint(
    get(path / "latest"),
    ok(r),
    docs = EndpointDocs().withDescription("Latest messages".some),
  )

  def balance[A](implicit r: ResponseEntity[A]): Endpoint[(String, String), A] = endpoint(
    get(path / "balance" / stateHash / walletId),
    ok(r),
    docs = EndpointDocs().withDescription("Balance of a wallet at the state specified".some),
  )

  def status[A](implicit r: ResponseEntity[A]): Endpoint[Unit, A] = endpoint(
    get(path / "status"),
    ok(r),
    docs = EndpointDocs().withDescription("Status".some),
  )
}
