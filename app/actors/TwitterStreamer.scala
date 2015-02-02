package actors

import akka.actor._
import play.api._
import play.api.libs.json._

class TwitterStreamer(out: ActorRef) extends Actor {

  def receive = {
    case "subscribe" => Logger.info("Received subscription from client")
    out ! Json.obj("text" -> "Hello World!")
  }
}

object TwitterStreamer {
  def props(out: ActorRef) = Props(new TwitterStreamer(out))
}