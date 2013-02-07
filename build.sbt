name := "Spray SSE"

version := "0.1"

scalaVersion := "2.10.0"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "typesafe releases repo" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.1.0"

libraryDependencies += "io.spray" % "spray-io" % "1.1-M7"

libraryDependencies += "io.spray" % "spray-can" % "1.1-M7"

libraryDependencies += "io.spray" % "spray-routing" % "1.1-M7"

libraryDependencies += "org.scalatest" % "scalatest_2.10.0" % "2.0.M5" % "test"

libraryDependencies += "io.spray" % "spray-testkit" % "1.1-M7" % "test"
