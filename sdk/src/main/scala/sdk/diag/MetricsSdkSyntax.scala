package sdk.diag

import sdk.diag.Metrics.Field

trait MetricsWriterSdkSyntax {
  implicit def diagEffectSdkSyntax[F[_]](x: Metrics[F]): MetricsWriterSdkOps[F] =
    new MetricsWriterSdkOps[F](x)
}

final class MetricsWriterSdkOps[F[_]](private val x: Metrics[F]) extends AnyVal {

  // Report a measured value
  def gauge(name: String, value: AnyRef): F[Unit] = x.measurement(name = name, fields = List(Field("value", value)))
}
