package sdk.diag

import brave.Tracing
import brave.propagation.TraceContext
import cats.effect.{Resource, Sync}
import zipkin2.reporter.brave.AsyncZipkinSpanHandler
import zipkin2.reporter.okhttp3.OkHttpSender

trait Span[F[_]] {
  def traceF[A](source: String, parentContext: Option[TraceContext] = None)(block: brave.Span => F[A]): F[A]
  def trace[A](source: String, parentContext: Option[TraceContext] = None)(block: brave.Span => A): A
}

object Span {
  def apply[F[_]](implicit ev: Span[F]): Span[F] = ev
}

final class ZipkinSpan[F[_]: Sync](implicit tracing: Tracing) extends Span[F] {
  override def traceF[A](source: String, parentContext: Option[TraceContext] = None)(block: brave.Span => F[A]): F[A] =
    Zipkin.traceF(source, parentContext)(block)

  override def trace[A](source: String, parentContext: Option[TraceContext])(block: brave.Span => A): A =
    Zipkin.trace(source, parentContext)(block)
}

object Zipkin {
  // Run `docker run -d -p 9411:9411 openzipkin/zipkin` from Terminal to start Zipkin UI
  def apply[F[_]: Sync]: Resource[F, ZipkinSpan[F]] = Resource
    .make {
      Sync[F].delay {
        val sender            = OkHttpSender.create("http://zipkin:9411/api/v2/spans")
        val zipkinSpanHandler = AsyncZipkinSpanHandler.create(sender)
        val tracing           = Tracing
          .newBuilder()
          .localServiceName("Node")
          .addSpanHandler(zipkinSpanHandler)
          .build()
        (zipkinSpanHandler, tracing)
      }
    } { case (_, tracing) => Sync[F].delay(tracing.close()) }
    .map { case (_, tracing) =>
      implicit val t = tracing
      new ZipkinSpan[F]()
    }

  def traceF[F[_]: Sync, A](operationName: String, parentContext: Option[TraceContext] = None)(
    io: brave.Span => F[A],
  )(implicit tracing: Tracing): F[A] = Resource
    .make {
      Sync[F].delay {
        val span = parentContext match {
          case Some(ctx) => tracing.tracer().newChild(ctx).name(operationName).start()
          case None      => tracing.tracer().nextSpan().name(operationName).start()
        }
        span
      }
    }(span => Sync[F].delay(span.finish()))
    .use(span => io(span))

  def trace[A](operationName: String, parentContext: Option[TraceContext] = None)(
    io: brave.Span => A,
  )(implicit tracing: Tracing): A = {
    val span   = parentContext match {
      case Some(ctx) => tracing.tracer().newChild(ctx).name(operationName).start()
      case None      => tracing.tracer().nextSpan().name(operationName).start()
    }
    val result = io(span)
    span.finish()
    result
  }
}
