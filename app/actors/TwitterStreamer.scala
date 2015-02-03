package actors

import akka.actor._
import play.api._
import play.api.libs.json._
import play.api.libs.iteratee.Enumerator
import play.api.libs.oauth.RequestToken
import play.api.libs.oauth.ConsumerKey
import play.api.Play.current
import play.api.libs.iteratee.Concurrent
import play.extras.iteratees.Encoding
import play.api.libs.iteratee.Enumeratee
import play.extras.iteratees.JsonIteratees
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WS
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.iteratee.Iteratee

class TwitterStreamer(out: ActorRef) extends Actor {

  def receive = {
    case "subscribe" =>
      Logger.info("Received subscription from client")
      TwitterStreamer.subscribe(out)
  }
}

object TwitterStreamer {
  def props(out: ActorRef) = Props(new TwitterStreamer(out))

  private var broadcastEnumerator: Option[Enumerator[JsObject]] = None

  def subscribe(out: ActorRef): Unit = {
    if (broadcastEnumerator == None) {
      connect()
    }
    val twitterClient = Iteratee.foreach[JsObject] { t => out ! t }
    broadcastEnumerator.map { enumerator =>
      enumerator run twitterClient
    }
  }

  def connect(): Unit = {
    credentials.map {
      case (consumerKey, requestToken) =>
        val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]
        val jsonStream: Enumerator[JsObject] = enumerator &> Encoding.decode() &> Enumeratee.grouped(JsonIteratees.jsSimpleObject)

        val (be, _) = Concurrent.broadcast(jsonStream)

        broadcastEnumerator = Some(be)

        val url = "https://stream.twitter.com/1.1/statuses/filter.json"

        WS.url(url)
          .withRequestTimeout(-1)
          .sign(OAuthCalculator(consumerKey, requestToken))
          .withQueryString("track" -> "reactive")
          .get { response =>
            Logger.info("Status: " + response.status)
            iteratee
          }.map { _ =>
            Logger.info("Twitter stream closed")
          }
    } getOrElse {
      Logger.error("Twitter credentials missing")
    }
  }

  private def credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey <- Play.current.configuration.getString("twitter.apiKey")
    apiSecret <- Play.current.configuration.getString("twitter.apiSecret")
    token <- Play.current.configuration.getString("twitter.token")
    tokenSecret <- Play.current.configuration.getString("twitter.tokenSecret")
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))

}