name := "akka-persistence-sample"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence" % "2.5.19",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.19" % Test,
  "org.typelevel" %% "cats-core" % "1.5.0",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
)
