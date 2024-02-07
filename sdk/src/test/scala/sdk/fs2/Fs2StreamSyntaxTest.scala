package sdk.fs2

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sdk.syntax.all.*

import scala.concurrent.duration.DurationInt

class Fs2StreamSyntaxTest extends AnyFlatSpec with Matchers {
  // This test cannot run on CI, since fs2.Stream has no guarantee that value is immediately pulled
  // when data is available. So output might depend on the environment where test is run.
  // E.g. if host is busy, test might fail.
  "throughput" should "output correct value" ignore {
    val stream = Stream.repeatEval[IO, Unit](IO.unit).metered(90.millis).take(100)
    val out    = stream.throughput(1.second).compile.toList.unsafeRunSync()
    out shouldBe List(11, 11, 11, 11, 11, 11, 11, 11, 11)
  }
}
