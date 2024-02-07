package diagnostics

import cats.effect.unsafe.implicits.global
import cats.effect.{Async, IO}
import cats.syntax.all.*
import com.typesafe.config.ConfigValueFactory
import diagnostics.syntax.all.kamonSyntax
import kamon.Kamon
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

class KamonTest extends AnyFlatSpec with Matchers {

  "Kamon" should "report all spans and arrange in traces properly" in {

    /**
     * Recurse n times, each time with a new span
     *
     * @param n     Recursion depth
     * @param recId id of recursion (to easily identify the span in Jaeger/Zipkin)
     * @return
     */
    def recWithSpan[F[_]: Async](n: Int, recId: Int)(implicit t: KamonContextStore[F]): F[Unit] = Async[F].defer {
      val rec = Async[F].defer {
        val work    = Async[F].sleep(10.millis)              // simulate some work
        val recurse = recWithSpan(n - 1, recId).whenA(n > 0) // recurse if n > 0
        work >> recurse
      }
      rec.span(s"recId $recId step $n ", "recursion")
    }

    // To understand all these configurations look into reference.conf files in Kamon packages
    val cfg = Kamon
      .config()
      // No need for influxdb reporter
      .withValue("kamon.modules.influxdb.enabled", ConfigValueFactory.fromAnyRef(false))
      // okHttp instrumentation fills Jaeger with unwanted spans, so disable
      .withValue("kamon.instrumentation.okhttp.http-client.tracing.enabled", ConfigValueFactory.fromAnyRef(false))
      // by default Kamon apply sampling for traces and do not report each and every trace. Switch to always mode.
      .withValue("kamon.trace.sampler", ConfigValueFactory.fromAnyRef("always"))

    // number of tasks. Kamon should report this number of traces.
    val numTasks   = 5
    // Each trace should contain this number of nested spans.
    val nestingLvl = 9

    implicit val traceContext: KamonContextStore[IO] = KamonContextStore.forCatsEffectIOLocal

    KamonDiagnostics
      .kamonResource[IO](cfg.some)
      .surround {
        // list of tasks to run
        val tasks = (1 to numTasks).map { x =>
          recWithSpan[IO](nestingLvl, x)
        }.toList
        // Run tasks concurrently to check that context is propagated correctly.
        tasks.parSequence.void
      }
      .unsafeRunSync() shouldBe ()
  }
}
