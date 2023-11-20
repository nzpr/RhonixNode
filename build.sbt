import Dependencies.*
import BNFC.*

val scala3Version       = "3.3.0"
val scala2Version       = "2.13.10"
lazy val commonSettings = Seq(
  version           := "0.1.0-SNAPSHOT",
  scalafmtOnCompile := !sys.env.contains("CI"), // Format on compile, disable in CI environments

  // Java 17+ has more restrictive policy for access to JDK internals.
  // Without the following java options liquibase library does not work properly
  // and tests fail with "java.base does not "opens java.lang" to unnamed module @4b8f7a19"
  // Please see https://dev.java/learn/modules/add-exports-opens/
  // NOTE: this does not help with running tests in IntelliJ, so
  // to run such tests in IntelliJ this argument has to be added explicitly.
  javaOptions ++= Seq(
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
  ),
  Test / fork               := true,
  Test / parallelExecution  := false,
  Test / testForkedParallel := false,
)

lazy val settingsScala3 = commonSettings ++ Seq(
  scalaVersion := scala3Version,
  scalacOptions ++= CompilerOptions.DEFAULTS,
  Compile / compile / wartremoverErrors ++= WartsSettings.DEFAULTS,
)

lazy val settingsScala2 = commonSettings ++ Seq(
  scalaVersion := scala2Version,
  // Enables Scala2 project to depend on Scala3 projects
  scalacOptions += "-Ytasty-reader",
  scalacOptions ++= CompilerOptions.DEFAULTS_SCALA_2,
  Compile / compile / wartremoverErrors ++= WartsSettings.DEFAULTS_SCALA_2,
)

lazy val all = (project in file("."))
  .settings(commonSettings*)
  .aggregate(sdk, weaver, dproc, db, node, rholang, legacy, sim, diag, macros)

lazy val sdk = (project in file("sdk"))
//  .settings(settingsScala3*) // Not supported in IntelliJ Scala plugin
  .settings(settingsScala2*)
  .settings(libraryDependencies ++= common ++ dbLibs ++ tests :+ protobuf :+ bouncyProvCastle)

// Database interfaces implementation
lazy val db = (project in file("db"))
  .settings(settingsScala2*)
  .settings(libraryDependencies ++= Seq(catsCore, catsEffect) ++ dbLibs ++ tests ++ log)
  .dependsOn(sdk)

// Consensus
lazy val weaver = (project in file("weaver"))
//  .settings(settingsScala3*) // Not supported in IntelliJ Scala plugin
  .settings(settingsScala2*)
  .settings(
    libraryDependencies ++= common ++ tests,
  )
  .dependsOn(sdk)

// Node logic
lazy val dproc = (project in file("dproc"))
//  .settings(settingsScala3*) // Not supported in IntelliJ Scala plugin
  .settings(settingsScala2*)
  .settings(
    libraryDependencies ++= common ++ tests,
  )
  .dependsOn(sdk, weaver, diag)

// Node implementation
lazy val node = (project in file("node"))
//  .settings(settingsScala3*) // Not supported in IntelliJ Scala plugin
  .settings(settingsScala2*)
  .settings(
    libraryDependencies ++= common ++ Seq(
      protobuf,
      grpc,
      grpcNetty,
    ) ++ tests ++ log ++ http4s ++ endpoints4s ++ cryptoLibs :+ lmdbjava :+ enumeratum :+ pureConfig,
    resolvers ++=
      // for embedded InfluxDB
      Resolver.sonatypeOssRepos("releases") ++
        Resolver.sonatypeOssRepos("snapshots"),
  )
  .dependsOn(sdk % "compile->compile;test->test", weaver, dproc, diag, db)

// Diagnostics
lazy val diag = (project in file("diag"))
  //  .settings(settingsScala3*) // Not supported in IntelliJ Scala plugin
  .settings(settingsScala2*)
  .settings(
    libraryDependencies ++= common ++ tests ++ diagnostics,
    // for embedded InfluxDB
    resolvers ++= Resolver.sonatypeOssRepos("releases"),
  )
  .dependsOn(sdk % "compile->compile;test->test")

// TODO this is commented out since JmhPlugin messes up with compile paths and IDEA doesn't like it
// lazy val bench = (project in file("bench"))
//  //  .settings(settingsScala3*) // Not supported in IntelliJ Scala plugin
//  .settings(settingsScala2*)
//  .enablePlugins(JmhPlugin)
//  .settings(
//    libraryDependencies ++= common ++ Seq(protobuf, grpc, grpcNetty) ++ tests,
//  )
//  .dependsOn(sdk, weaver)

lazy val sim = (project in file("sim"))
  //  .settings(settingsScala3*) // Not supported in IntelliJ Scala plugin
  .settings(settingsScala2*)
  .settings(
    libraryDependencies ++= common,
    version                          := "0.1.0-SNSHOT",
    assembly / mainClass             := Some("sim.NetworkSim"),
    assembly / assemblyJarName       := "sim.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("reference.conf")    => MergeStrategy.concat
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case x                             => MergeStrategy.first
    },
  )
  .dependsOn(node, db, diag)

// Rholang implementation
lazy val rholang = (project in file("rholang"))
  .settings(settingsScala2*)
  .settings(bnfcSettings*)
  .settings(libraryDependencies ++= common ++ tests :+ protobuf :+ bouncyProvCastle)
  // TODO Matching the rholang object should be always exhaustive. Remove when done.
  .settings(scalacOptions ++= Seq("-Xlint:-strict-unsealed-patmat", "-Xnon-strict-patmat-analysis"))
  .dependsOn(sdk % "compile->compile;test->test")

// Legacy implementation (rholang + rspace)
lazy val legacy = (project in file("legacy"))
  .settings(settingsScala2*)
  .settings(
    scalacOptions ~= { options =>
      options.filterNot(Set("-Xfatal-warnings", "-Ywarn-unused:imports")) ++ Seq(
        "-Xlint:-strict-unsealed-patmat",
        "-Xnon-strict-patmat-analysis",
        "-Wconf:cat=deprecation:ws",   // suppress deprecation warnings
        "-Xlint:-missing-interpolator",// Disable false positive strings containing ${...}
      )
    },
    Compile / compile / wartremoverErrors ~= {
      _.filterNot(Seq(Wart.SeqApply, Wart.Throw, Wart.Var, Wart.SeqUpdated).contains)
    },
    libraryDependencies ++= common ++ tests ++ legacyLibs,
    resolvers += ("jitpack" at "https://jitpack.io"),
  )
  .dependsOn(sdk, rholang, macros) // depends on new rholang implementation

// Macro implementation should be compiled before macro application
// https://stackoverflow.com/questions/75847326/macro-implementation-not-found-scala-2-13-3
lazy val macros = (project in file("macros"))
  .settings(settingsScala2*)
  .settings(
    libraryDependencies ++= common ++ legacyLibs,
    resolvers ++=
      // for kalium
      Resolver.sonatypeOssRepos("releases") ++
        Resolver.sonatypeOssRepos("snapshots") :+ ("jitpack" at "https://jitpack.io"),
  )
