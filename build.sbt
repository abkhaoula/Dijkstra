lazy val commonSettings = Seq(
  version := "1.0",
  name := "akka-Dijkstra",
  scalaVersion := "2.12.8"
)

mainClass in (Compile, packageBin) := Some("GraphSystem")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.19"
)
