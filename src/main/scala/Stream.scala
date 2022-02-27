//> using lib "com.typesafe.akka::akka-stream:2.6.18"
//> using lib "com.typesafe.akka::akka-actor-typed:2.6.18"
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.NotUsed
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json
import scala.concurrent.Future
import play.api.libs.json.JsObject

case class BulkUserUpdatePayload(users: Seq[JsObject] = Seq.empty)
object BulkUserUpdatePayload { implicit val fmt = Json.format[BulkUserUpdatePayload] }

object Stream {
  implicit lazy val system: ActorSystem = ActorSystem("test")

  def main(args: Array[String]): Unit =  {
    Config(args).map { config =>
      for { 
        (parser, lineSource) <- sourceFromFile(config.filename)
      } yield 
        payloadSource(config, parser, lineSource)
          .runWith(Sink.foreach(EchoPayloadProcessor.apply(config, system)))
          .andThen(_ => system.terminate())
    }
  }

  def payloadSource(config: Config, parser: FileIngestionParser, lineSource: Source[String, NotUsed]): Source[BulkUserUpdatePayload, NotUsed] = 
    lineSource
      .map(parser.parseUserLine)
      .zipWithIndex
      .divertTo(Sink.foreach(s => System.err.println(s"LINE ${s._2 + 1 + 1}: ${s._1}")), _._1.isLeft)
      .collect { case (Right(rqst), _) => Json.toJsObject(rqst) }
      .statefulMapConcat { () => 
        var bufferSize = 0

        {
          case rqst =>
            bufferSize += rqst.toString.length
            if(bufferSize > config.payloadSize)
              bufferSize = 0
            List((rqst, bufferSize == 0))
        }
      }
      .splitWhen { case (_, newBuffer) => newBuffer }
      .fold(BulkUserUpdatePayload()) { case (result, (rqst, _)) => result.copy(users = result.users :+ rqst) }
      .concatSubstreams

  def sourceFromFile(filename: String): Future[(FileIngestionParser, Source[String, NotUsed])] = {
    Source.fromIterator(scala.io.Source.fromFile(filename).getLines)
      .prefixAndTail(1)
      .runWith(Sink.head)
      .map {
        case (Seq(headerLine), contentSource) =>
          (new FileIngestionDelimitedParser(CsvParser, headerLine), contentSource)
      }
  }
}