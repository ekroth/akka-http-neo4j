name := """akka-http-neo4j"""

version := "0.1"

scalacOptions ++= Seq("-feature", "-deprecation", "-encoding", "utf8")

scalaVersion := "2.11.7"

parallelExecution in Test := false

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= {
  val akkaStreamVersion = "2.0-M2"
  val scalaTestVersion  = "2.2.5"
  Seq(
    "com.typesafe.akka"   %%  "akka-http-spray-json-experimental"   % akkaStreamVersion,
    "com.typesafe.akka"   %%  "akka-http-testkit-experimental"      % akkaStreamVersion,
    "org.scalatest"       %%  "scalatest"                           % scalaTestVersion % "test"
  )
}
