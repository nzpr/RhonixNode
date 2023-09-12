package rhonix.api.http.codecs

import cats.effect.kernel.Concurrent
import io.circe.generic.auto.*
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}
import sdk.api.data.*

/// JSON codecs for Http4s
package object json {
  implicit def blockEncoder[F[_]: Concurrent]: EntityEncoder[F, Block] = jsonEncoderOf[F, Block]

  implicit def blockDecoder[F[_]: Concurrent]: EntityDecoder[F, Block] = jsonOf[F, Block]

  implicit def blockDeploysEncoder[F[_]: Concurrent]: EntityEncoder[F, BlockDeploys] = jsonEncoderOf[F, BlockDeploys]

  implicit def blockDeploysDecoder[F[_]: Concurrent]: EntityDecoder[F, BlockDeploys] = jsonOf[F, BlockDeploys]
}
