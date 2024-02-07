package diagnostics.metrics

import cats.effect.kernel.{Async, Clock}
import cats.effect.{Ref, Resource, Sync}
import cats.syntax.all.*
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import com.influxdb.client.{InfluxDBClient, InfluxDBClientFactory, InfluxDBClientOptions}
import okhttp3.OkHttpClient
import sdk.diag.Metrics
import sdk.diag.Metrics.{Field, Tag}
import scala.jdk.CollectionConverters.*

import scala.concurrent.duration.Duration
import scala.util.Try

object InfluxDbBatchedMetrics {

  // key for tag identifying the process reporting metrics
  private val SOURCE_TAG_KEY: String = "sourceTag"

  private def mkInfluxClient[F[_]: Sync](config: Config): F[InfluxDBClient] = Sync[F].delay {
    import config.*
    // try create buckets in case is absent
    val b = Try(InfluxDBClientFactory.create(url, token.toCharArray).getBucketsApi.createBucket(bucket, org))

    // Set http client
    val httpClient: OkHttpClient.Builder = new OkHttpClient.Builder()
      .readTimeout(readTimeout._1, readTimeout._2)
      .writeTimeout(writeTimeout._1, writeTimeout._2)
      .connectTimeout(connectTimeout._1, connectTimeout._2)

    // Set client options
    val options: InfluxDBClientOptions = InfluxDBClientOptions
      .builder()
      .url(url)
      .authenticateToken(token.toCharArray)
      .org(org)
      .bucket(b.map(_.getId).getOrElse(bucket))
      .okHttpClient(httpClient)
      .build();

    // Build, enable compression
    InfluxDBClientFactory.create(options).enableGzip()
  }

  private def mkAsyncWriter[F[_]: Sync](client: InfluxDBClient) = Sync[F].delay(client.makeWriteApi())

  private def reportingFiberResource[F[_]: Async](
    report: F[Unit],
    batchInterval: Duration,
  ) = Async[F].background((report *> Async[F].sleep(batchInterval)).foreverM)

  /**
   * Report metrics to InfluxDB with batching.
   * @param config configuration object
   * @param sourceTag tag of a source. Useful to show many instantiations of the same metric on the same chart
   */
  def apply[F[_]: Async](config: Config, sourceTag: String): Resource[F, Metrics[F]] = for {
    client <- Resource.make(mkInfluxClient(config))(c => Sync[F].delay(c.close()))
    writer <- Resource.make(mkAsyncWriter(client))(w => Sync[F].delay { w.flush(); w.close() })
    // accumulator
    acc    <- Resource.eval(Ref.of(List.empty[Point]))
    times  <- Resource.eval(Ref.of(Map.empty[String, Long]))
    // fiber doing infinitely repeating reports
    f       = acc.getAndSet(List()).map(points => writer.writePoints(points.asJava))
    _      <- reportingFiberResource(f, config.batchInterval)
  } yield new Metrics[F] {
    override def measurement(name: String, fields: List[Metrics.Field], tags: List[Metrics.Tag]): F[Unit] =
      Clock[F].realTimeInstant.flatMap { now =>
        lazy val p = Point
          .measurement(name)
          .addTag(SOURCE_TAG_KEY, sourceTag)
          .time(now, WritePrecision.MS)
          .addTags(tags.map { case Tag(k, v) => k -> v }.toMap.asJava)
          .addFields(fields.map { case Field(k, v) => k -> v }.toMap.asJava)

        times
          .modify { s =>
            val nowMillis    = now.toEpochMilli
            val shouldUpdate = s.get(name).forall(x => (nowMillis - x) >= config.meter.toMillis)
            val newS         = if (shouldUpdate) s + (name -> nowMillis) else s
            newS -> shouldUpdate
          }
          .flatMap(acc.update(_ :+ p).whenA(_))
      }
  }
}
