package io.rhonix.node.api

import cats.effect.{Async, ExitCode}
import org.http4s.{HttpApp, HttpRoutes, Uri}
import org.http4s.Uri.Path.{Root, Segment}
import org.http4s.blaze.server.BlazeServerBuilder
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import sdk.api.Path

package object http {
  val ApiPath: Uri.Path = Root.addSegments(Path.map(Segment))

  def server[F[_]: Async](
    routes: HttpRoutes[F],
    httpPort: Int = 8080,
    host: String = "localhost",
  ): fs2.Stream[F, ExitCode] = BlazeServerBuilder[F]
    .bindHttp(httpPort, host)
    .withHttpApp(HttpApp[F](routes.orNotFound.run))
    .withIdleTimeout(1.seconds)
    .serve
}
