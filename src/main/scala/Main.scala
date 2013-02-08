
import ServerSideEventsDirectives._
import spray.http.HttpHeaders.RawHeader
import akka.actor.{ActorRef, Actor, Props}

object Main extends App with SimpleRoutingApp {

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

      rewriteUnmatchedPath(p => if (p == "/") "/index.html" else p) {
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

// FIXME Temporary fix to avoid using dynamic on M7, already fixed on master!!!

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import akka.actor.{ActorRefFactory, Actor, Props, ActorRef}
import akka.pattern.ask
import spray.can.server.{HttpServer, ServerSettings, SprayCanHttpServerApp}
import spray.io.{ServerSSLEngineProvider, IOExtension}
import akka.util.Timeout
import spray.routing.{Route, HttpService}

trait SimpleRoutingApp extends SprayCanHttpServerApp with HttpService {

  @volatile private[this] var _refFactory: Option[ActorRefFactory] = None

  implicit def actorRefFactory = _refFactory.getOrElse(
    sys.error("Route creation is not fully supported before `startServer` has been called, " +
      "maybe you can turn your route definition into a `def` ?")
  )

  /**
   * Starts a new spray-can HttpServer with the handler being a new HttpServiceActor for the given route and
   * binds the server to the given interface and port.
   * The method returns a Future on the Bound event returned by the HttpServer as a reply to the Bind command.
   * You can use the Future to determine when the server is actually up (or you can simply drop it, if you are not
   * interested in it).
   */
  def startServer(interface: String,
                  port: Int,
                  ioBridge: ActorRef = IOExtension(system).ioBridge(),
                  settings: ServerSettings = ServerSettings(),
                  serverActorName: String = "http-server",
                  serviceActorName: String = "simple-service-actor")
                 (route: => Route)
                 (implicit sslEngineProvider: ServerSSLEngineProvider,
                  bindingTimeout: Timeout = Duration(1, "sec")): Future[HttpServer.Bound] = {
    val service = system.actorOf(
      props = Props {
        new Actor {
          _refFactory = Some(context)
          def receive = runRoute(route)
        }
      },
      name = serviceActorName
    )
    (newHttpServer(service, ioBridge, settings, serverActorName) ? Bind(interface, port)).mapTo[HttpServer.Bound]
  }
}
