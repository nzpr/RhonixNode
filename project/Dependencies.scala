import sbt.*

object Dependencies {
  // Core dependencies
  val catsCore   = "org.typelevel" %% "cats-core"   % "2.9.0" // cross CrossVersion.for3Use2_13
  val mouse      = "org.typelevel" %% "mouse"       % "1.2.1" // cross CrossVersion.for3Use2_13
  val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.0" // cross CrossVersion.for3Use2_13
  val fs2Core    = "co.fs2"        %% "fs2-core"    % "3.7.0" // cross CrossVersion.for3Use2_13

  // Added to support legacy code. But useful library in general.
  val kindProjector = compilerPlugin(
    "org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full,
  )

  // Network communication
  val grpc      = "io.grpc" % "grpc-core"  % "1.53.0"
  val grpcNetty = "io.grpc" % "grpc-netty" % "1.53.0"

  // LEGACY dependencies of imported projects
  val protobuf          = "com.google.protobuf"         % "protobuf-java"   % "3.22.2"
  val scalapbRuntimeLib = "com.thesamet.scalapb"       %% "scalapb-runtime" % "0.11.13"
  val scalapbCompiler   = "com.thesamet.scalapb"       %% "compilerplugin"  % "0.11.13"
  val magnolia          = "com.propensive"             %% "magnolia"        % "0.17.0"
  val bouncyProvCastle  = "org.bouncycastle"            % "bcprov-jdk15on"  % "1.70"
  val bouncyPkixCastle  = "org.bouncycastle"            % "bcpkix-jdk15on"  % "1.70"
  val guava             = "com.google.guava"            % "guava"           % "31.1-jre"
  val secp256k1Java     = "com.github.rchain"           % "secp256k1-java"  % "0.1"
  val scodecCore        = "org.scodec"                 %% "scodec-core"     % "1.11.10"
  val scodecCats        = "org.scodec"                 %% "scodec-cats"     % "1.2.0"
  val scodecBits        = "org.scodec"                 %% "scodec-bits"     % "1.1.37"
  val shapeless         = "com.chuusai"                %% "shapeless"       % "2.3.10"
  val lz4               = "org.lz4"                     % "lz4-java"        % "1.8.0"
  val lmdbjava          = "org.lmdbjava"                % "lmdbjava"        % "0.8.3"
  val enumeratum        = "com.beachape"               %% "enumeratum"      % "1.7.2"
  val xalan             = "xalan"                       % "xalan"           % "2.7.3"
  val catsMtl           = "org.typelevel"              %% "cats-mtl-core"   % "0.7.1"
  val catsMtlLaws       = "org.typelevel"              %% "cats-mtl-laws"   % "0.7.1"
  val kalium            = "com.github.rchain"           % "kalium"          % "0.8.1"
  val lightningj        = "org.lightningj"              % "lightningj"      % "0.5.2-Beta"
  val scalacheck        = "org.scalacheck"             %% "scalacheck"      % "1.14.1"
  val scalaLogging      = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.4"
  val scallop           = "org.rogach"                 %% "scallop"         % "3.3.2"
  val catsEffectLawsTest = "org.typelevel"             %% "cats-effect-laws"% "3.5.0" % "test"
  val legacyLibs        = Seq(
    magnolia,
    guava,
    bouncyProvCastle,
    bouncyPkixCastle,
    protobuf,
    scalapbRuntimeLib,
    scalapbCompiler,
    secp256k1Java,
    scodecCore,
    scodecCats,
    scodecBits,
    lz4,
    lmdbjava,
    enumeratum,
    xalan,
    catsMtl,
    catsMtlLaws,
    kalium,
    lightningj,
    shapeless,
    scalacheck,
    scalaLogging,
    scallop,
    catsEffectLawsTest
  )

  // Testing frameworks
  val scalacheckShapeless = "com.github.alexarchambault" %% "scalacheck-shapeless_1.16" % "1.3.1" % Test
  val scalatest           = "org.scalatest"              %% "scalatest"                 % "3.2.15" // cross CrossVersion.for3Use2_13
  val scalatest_ce        =
    "org.typelevel" %% "cats-effect-testing-scalatest" % "1.4.0" % Test // cross CrossVersion.for3Use2_13
  val mockito             = "org.mockito"       %% "mockito-scala-cats" % "1.17.12"  % Test
  val scalacheck_e        = "org.typelevel"     %% "scalacheck-effect"  % "1.0.4"    % Test
  val scalatestScalacheck = "org.scalatestplus" %% "scalacheck-1-17"    % "3.2.16.0" % Test

  // Diagnostics
  val kamon                 = "io.kamon" %% "kamon-core"        % "2.6.3"
  val kamonStatus           = "io.kamon" %% "kamon-status-page" % "2.6.3"
  val kamonInfluxDbReporter = "io.kamon" %% "kamon-influxdb"    % "2.6.0"
  val kamonZipkinReporter   = "io.kamon" %% "kamon-jaeger"      % "2.6.0"

  // Logging
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.4.7"
  val slf4j          = "org.slf4j"      % "slf4j-api"       % "2.0.5"

  val http4sNetty = "org.http4s" %% "http4s-netty-server" % "0.5.9"
  val http4sBlaze = "org.http4s" %% "http4s-blaze-server" % "0.23.14"
  val http4sDSL   = "org.http4s" %% "http4s-dsl"          % "0.23.23"
  val circeCodec  = "org.http4s" %% "http4s-circe"        % "0.23.23"

  // Database
  val embeddedPostgres           = "io.zonky.test"     % "embedded-postgres" % "2.0.4"  % Test
  val junitJupiter               = "org.junit.jupiter" % "junit-jupiter-api" % "5.10.0" % Test
  val postgresql                 = "org.postgresql"    % "postgresql"        % "42.6.0"
  val squeryl                    = "org.squeryl"      %% "squeryl"           % "0.10.0"
  val liquibase4s: Seq[ModuleID] = Seq(
    "io.github.liquibase4s" %% "liquibase4s-core"        % "1.0.0",
    "io.github.liquibase4s" %% "liquibase4s-cats-effect" % "1.0.0",
  )

  val common = Seq(catsCore, catsEffect, fs2Core, kindProjector)

  val diagnostics = Seq(kamon, kamonStatus, kamonInfluxDbReporter, kamonZipkinReporter)

  val log = Seq(logbackClassic, slf4j)

  val http4s = Seq(http4sNetty, http4sDSL, circeCodec, http4sBlaze)

  val tests = Seq(scalatest, scalatest_ce, mockito, scalacheck_e, scalacheckShapeless, scalatestScalacheck)

  val dbLibs = Seq(embeddedPostgres, postgresql, squeryl, junitJupiter) ++ liquibase4s
}
