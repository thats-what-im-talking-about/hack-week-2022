// Defines the PayloadProcessor trait, and provides implentations.  The whole point of the project 
// here is to read in CSV files and transform the contents into valid Iterable API payloads.
//> using lib "com.typesafe.akka::akka-http:10.2.8"

import play.api.libs.json.Json
import akka.http.scaladsl.Http
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import scala.concurrent.Future
import akka.http.scaladsl.model.headers.ModeledCustomHeader
import akka.http.scaladsl.model.headers.ModeledCustomHeaderCompanion
import scala.util.Try
import scala.util.Random

trait PayloadProcessor {
  def apply(config: Config, system: ActorSystem)(payload: BulkUserUpdatePayload): Future[Int]
}

object EchoPayloadProcessor extends PayloadProcessor {
override def apply(config: Config, system: ActorSystem)(payload: BulkUserUpdatePayload): Future[Int] = {
    println(Json.prettyPrint(Json.toJson(payload)))
    Future.successful(200)
  }
}

object TestPayloadProcessor extends PayloadProcessor {
  override def apply(config: Config, system: ActorSystem)(payload: BulkUserUpdatePayload): Future[Int] = {
    val rv = Random.nextInt(100) match {
      case i if i > 90 => 429
      case i if i > 70 => 400
      case _ => 200
    }
    Future.successful(rv)
  }
}

object ApiPayloadProcessor extends PayloadProcessor {
  import akka.http.scaladsl.client.RequestBuilding.Post

  final class ApiKeyHeader(apiKey: String) extends ModeledCustomHeader[ApiKeyHeader] {
    override def renderInRequests = true
    override def renderInResponses = true
    override val companion = ApiKeyHeader
    override def value: String = apiKey
  }
  object ApiKeyHeader extends ModeledCustomHeaderCompanion[ApiKeyHeader] {
    override val name = "Api-Key"
    override def parse(value: String) = Try(new ApiKeyHeader(value))
  }

  override def apply(config: Config, system: ActorSystem)(payload: BulkUserUpdatePayload): Future[Int] = {
    val post = Post(s"${config.apiBaseUrl}/api/users/bulkUpdate", Json.toJson(payload).toString)
                 .withHeaders(Seq(ApiKeyHeader(config.apiKey)))
    val result = for {
      resp <- Http(system).singleRequest(post)
    } yield {
      resp.status.intValue()
    }

    result.recover {
      case t => 
        t.printStackTrace() 
        -1
    }
  }
}