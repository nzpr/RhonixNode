addSbtPlugin("org.wartremover"    % "sbt-wartremover"    % "3.1.3")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"       % "2.4.0")
addSbtPlugin("ch.epfl.scala"      % "sbt-scala3-migrate" % "0.5.1") // Temp for migration to Scala 3
addSbtPlugin("pl.project13.scala" % "sbt-jmh"            % "0.4.5")
addSbtPlugin("com.eed3si9n"       % "sbt-assembly"       % "0.14.10")

// TODO remove 3 items below along with StackSafeScalapbGenerator when legacy rholang is removed
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")
// Yes it's weird to do the following, but it's what is mandated by the scalapb documentation
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.3"
addSbtPlugin("org.typelevel"          % "sbt-fs2-grpc"         % "2.5.11")