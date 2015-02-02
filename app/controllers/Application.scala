package controllers

import play.api._
import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.RequestToken
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.RequestToken
import play.api.libs.ws.WS
import play.api.libs.oauth.OAuthCalculator
import play.api.Play.current
import scala.concurrent.Future
import play.api.libs.iteratee._
import play.api.libs.json.JsValue
import play.extras.iteratees._
import play.api.libs.json.JsObject

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]
  
  val jsonStream: Enumerator[JsObject] = enumerator &> Encoding.decode() &> Enumeratee.grouped(JsonIteratees.jsSimpleObject)
  
  val loggingIteratee = Iteratee.foreach[JsObject]( { value =>
    Logger.info(value.toString())
  })
  
  jsonStream run loggingIteratee
  
  def tweets = Action.async {
    credentials.map {
      case (consumerKey, requestToken) =>
        WS.url("https://stream.twitter.com/1.1/statuses/filter.json")
          .withRequestTimeout(-1)
          .sign(OAuthCalculator(consumerKey, requestToken))
          .withQueryString("track" -> "scala")
          .get { response =>
            Logger.info("Status:" + response.status)
            iteratee
          }.map(_ => Ok("Stream closed"))
    } getOrElse {
      Future {
        InternalServerError("Twitter credentials missing")
      }
    }
  }

  def credentials: Option[(ConsumerKey, RequestToken)] = for {
    apiKey <- Play.current.configuration.getString("twitter.apiKey")
    apiSecret <- Play.current.configuration.getString("twitter.apiSecret")
    token <- Play.current.configuration.getString("twitter.token")
    tokenSecret <- Play.current.configuration.getString("twitter.tokenSecret")
  } yield (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))
}