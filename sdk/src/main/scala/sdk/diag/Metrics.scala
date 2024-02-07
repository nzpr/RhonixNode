package sdk.diag

import cats.Applicative
import sdk.diag.Metrics.{Field, Tag}

/**
 * This is plain analog to InfluxDb writer client interface.
 * Not sure whether there will be another implementation for this trait except InfluxDB
 * but it is present in sdk to not pull InfluxDB dependency only to declare metrics.
 * */
trait Metrics[F[_]] {
  def measurement(name: String, fields: List[Field] = List(), tags: List[Tag] = List()): F[Unit]
}

object Metrics {
  final case class Field(key: String, value: AnyRef)
  final case class Tag(key: String, value: String)

  def apply[F[_]](implicit m: Metrics[F]): Metrics[F] = m

  def unit[F[_]: Applicative]: Metrics[F] = new Metrics[F] {
    override def measurement(name: String, fields: List[Field], tags: List[Tag]): F[Unit] = Applicative[F].unit
  }
}
