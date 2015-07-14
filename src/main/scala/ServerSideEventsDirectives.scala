
import spray.http._
import spray.can.Http
import spray.routing._
import Directives._
import HttpHeaders._
import akka.actor._
import akka.pattern.ask
import scala.concurrent.duration._
import util.{Success, Failure}

// Enable scala features
import scala.language.postfixOps
import scala.language.implicitConversions

trait ServerSideEventsDirectives {

  case class Message(data: String, event: Option[String], id: Option[String])
  case class RegisterClosedHandler(handler: () => Unit)
  object CloseConnection

  object Message {
    def apply(data: String): Message = Message(data, None, None)
    def apply(data: String, event: String): Message = Message(data, Some(event), None)
    def apply(data: String, event: String, id: String): Message = Message(data, Some(event), Some(id))
  }

  def sse(body: (ActorRef, Option[String]) => Unit)(implicit refFactory: ActorRefFactory): Route = {

    val responseStart = HttpResponse(
      headers = `Cache-Control`(CacheDirectives.`no-cache`) :: Nil,
      entity = ":" + (" " * 2049) + "\n" // 2k padding for IE using Yaffle
    )

    // TODO These headers should be standard headers
    val preflightHeaders = List(
      RawHeader("Access-Control-Allow-Methods", "GET"),
      RawHeader("Access-Control-Allow-Headers", "Last-Event-ID, Cache-Control"),
      RawHeader("Access-Control-Max-Age", "86400")
    )

    def lastEventId = optionalHeaderValueByName("Last-Event-ID") | parameter("lastEventId"?)

    def sseRoute(lei: Option[String]) = (ctx: RequestContext) => {

      val connectionHandler = refFactory.actorOf(
        Props {
          new Actor {

            var closedHandlers: List[() => Unit] = Nil

            ctx.responder ! ChunkedResponseStart(responseStart)

            // Keep-Alive
            context.setReceiveTimeout(15 seconds)

            def receive = {
              case Message(data, event, id) =>
                val idString = id.map(id => s"id: $id\n").getOrElse("")
                val eventString = event.map(ev => s"event: $ev\n").getOrElse("")
                val dataString = data.split("\n").map(d => s"data: $d\n").mkString
                ctx.responder ! MessageChunk(s"${idString}${eventString}${dataString}\n")
              case CloseConnection =>
                ctx.responder ! ChunkedMessageEnd
              case ReceiveTimeout =>
                ctx.responder ! MessageChunk(":\n") // Comment to keep connection alive
              case RegisterClosedHandler(handler) => closedHandlers ::= handler
              case _: Http.ConnectionClosed =>
                closedHandlers.foreach(_())
                context.stop(self)
            }
          }
        }
      )

      body(connectionHandler, lei)
    }

    get {
      respondWithMediaType(MediaType.custom("text/event-stream")) { // TODO This should be a standard media type
        lastEventId { lei =>
          sseRoute(lei)
        }
      }
    } ~
    // Answer preflight requests. Needed for Yaffle
    method(HttpMethods.OPTIONS) {  // TODO Change this with options, that it's included in Master
      respondWithHeaders(preflightHeaders: _*) {
        complete(StatusCodes.OK)
      }
    }

  }

  trait bidirectionalSseMessage {
    def onMessage(messageHandler: (ActorRef, Message) => Unit): Route
  }

  def duplexSse(openHandler: ActorRef => Unit)(implicit refFactory: ActorRefFactory): bidirectionalSseMessage  = {

    case class RegisterChannel(channel: ActorRef)
    case class UnregisterChannel(id: Int)
    case class GetChannel(id: Int, token: String)
    case class ChannelRegistered(id: Int, token: String)

    // Actor to map upstream channels and ids
    val channelMapper = refFactory.actorOf(
      Props {
        new Actor {

          private var nextId = 1

          private var channels = collection.immutable.Map.empty[Int, (String, ActorRef)]

          private val randomTokenGenerator = new scala.util.Random(new java.security.SecureRandom())

          private def getChannel(id: Int, token: String): Option[ActorRef] = {
            channels.get(id).flatMap{ case (origToken, c) =>
              if (token == origToken) {
                Some(c)
              } else {
                None
              }
            }
          }

          def receive = {
            case RegisterChannel(c) =>
              val token = randomTokenGenerator.alphanumeric.take(20).mkString
              channels += nextId -> (token, c)
              sender ! ChannelRegistered(nextId, token)
              nextId += 1
            case UnregisterChannel(id) =>
              channels -= id
            case GetChannel(id, token) =>
              lazy val error = Status.Failure(new Exception(s"Channel $id is not registered"))
              sender ! getChannel(id, token).getOrElse(error)
          }

        }
      }
    )

    // For the asks
    implicit val timeout = akka.util.Timeout(5 seconds)
    import refFactory.dispatcher

    // To handle the onMessage part
    new bidirectionalSseMessage {

      def onMessage(messageHandler: (ActorRef, Message) => Unit): Route = {
        path("receive") {
          sse { (c, _) =>

            // Register the channel
            val cr = ( channelMapper ? RegisterChannel(c) ).mapTo[ChannelRegistered]

            cr onComplete {
              case Success(ChannelRegistered(id, token)) =>
                // Unregister it on closed event
                c ! RegisterClosedHandler( () => channelMapper !  UnregisterChannel(id) )

                // Send the id and token to the client in json
                c ! Message(s"""{ "id": "$id", "token": "$token" }""", "init")

                // Call the custom open handler
                openHandler(c)
              case Failure(_) => c ! CloseConnection
            }
          }
        } ~
        path("send") {
          post {
            formFields("id", "token", "msg") { (id, token, msg) =>
              // Get the channel and pass it to the messageHandler
              ( channelMapper ? GetChannel(id.toInt, token) ) onSuccess  {
                case channel: ActorRef => messageHandler(channel, Message(msg))
              }
              complete(StatusCodes.OK) // Errors, if any, returned through the SSE channel
            }
          }
        }
      }

    }


  }

}

object ServerSideEventsDirectives extends ServerSideEventsDirectives
