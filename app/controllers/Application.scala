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
import actors.TwitterStreamer

object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index("Tweets"))
  }

  val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]
  
  val jsonStream: Enumerator[JsObject] = enumerator &> Encoding.decode() &> Enumeratee.grouped(JsonIteratees.jsSimpleObject)
  
  val loggingIteratee = Iteratee.foreach[JsObject]( { value =>
    Logger.info(value.toString())
  })
  
  jsonStream run loggingIteratee
  
  def tweets = WebSocket.acceptWithActor[String, JsValue] {
    request => out => TwitterStreamer.props(out)
  }
  
  def replicateFeed = Action { implicit request =>
    Ok.feed(TwitterStreamer.subscribeNode)
  }
}