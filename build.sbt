name := "Spray SSE"

version := "0.1"

scalaVersion := "2.11.7"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "typesafe releases repo" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.9"

libraryDependencies += "io.spray" %% "spray-io" % "1.3.3"

libraryDependencies += "io.spray" %% "spray-can" % "1.3.3"

libraryDependencies += "io.spray" %% "spray-routing" % "1.3.3"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"

libraryDependencies += "io.spray" %% "spray-testkit" % "1.3.3" % "test"
