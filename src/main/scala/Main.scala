
import ServerSideEventsDirectives._
import spray.http.HttpHeaders.RawHeader
import akka.actor.{ActorSystem, ActorRef, Actor, Props}
import spray.http.Uri.Path
import spray.routing.SimpleRoutingApp

object Main extends App with SimpleRoutingApp {

  implicit val system = ActorSystem()

  val sseProcessor = system.actorOf(Props { new Actor {
    def receive = {
      case (channel: ActorRef, lastEventID: Option[String]) =>
        // Print LastEventID if present
        lastEventID.foreach(lei => println(s"LastEventID: $lei"))

        // Simulate some work
        Thread.sleep(3000)

        channel ! Message("some\ndata\ntest", "customevent", "someid")
    }
  }})

  startServer(interface = "localhost", port = 8080) {

      rewriteUnmatchedPath(p => if (p == Path./) Path("/index.html") else p) {
        pathPrefix("") {
          getFromResourceDirectory("")
        }
      } ~
      path("sse") {
        respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*")) {
          sse { (channel, lastEventID) =>
          // Register a closed event handler
            channel ! RegisterClosedHandler( () => println("Connection closed !!!") )

            // Use the channel
            sseProcessor ! (channel, lastEventID)
          }
        }
      } ~
      pathPrefix("duplexsse") {
        respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*")) {
          duplexSse { channel =>
            println("Connected")
            channel ! RegisterClosedHandler( () => println("Connection closed !!!") )
            channel ! Message("A message")
          } onMessage { (channel, msg) =>
            channel ! Message(s"The message was: ${msg.data}")
          }
        }
      }
  }
}

