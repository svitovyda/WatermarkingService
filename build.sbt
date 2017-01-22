name := "watermarking"

version := "1.0"

scalaVersion := "2.11.8"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.12.1" % "test",
  "org.mockito" % "mockito-all" % "1.9.5",
  "com.typesafe.akka" %% "akka-actor" % "2.4.16"
)

