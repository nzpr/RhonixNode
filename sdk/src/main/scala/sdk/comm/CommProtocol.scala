package sdk.comm

import java.io.InputStream

final case class CommProtocol[F[_], A, B](
  endpointName: String,
  streamA: A => InputStream,
  parseA: InputStream => A,
  callback: A => F[B],
  streamB: B => InputStream,
  parseB: InputStream => B,
)
