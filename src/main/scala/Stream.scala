//> using lib "com.typesafe.akka::akka-stream:2.6.18"
//> using lib "com.typesafe.akka::akka-actor-typed:2.6.18"
// This will suppress slf4j warnings
//> using lib "org.slf4j:slf4j-nop:1.7.36"

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ClosedShape
import akka.stream.FlowShape
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Merge
import akka.stream.scaladsl.MergePreferred
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

case class BulkUserUpdatePayload(users: Seq[JsObject] = Seq.empty)
object BulkUserUpdatePayload { implicit val fmt = Json.format[BulkUserUpdatePayload] }

object Stream {
  implicit lazy val system: ActorSystem = ActorSystem("test")

  def main(args: Array[String]): Unit =  {
    Config(args).map { config =>
      for { 
        (parser, lineSource) <- sourceFromFile(config.filename)
        result <- payloadSource(config, parser, lineSource)
          .via(processFlow(config))
          .runWith(Sink.foreach { case (status, payload, batchNo) => println(s"Processed batch ${batchNo}, status ${status}, payload contained ${payload.users.length} items.")})
          .andThen(_ => system.terminate())
      } yield result
    }
  }

  def payloadSource(config: Config, parser: FileIngestionParser, lineSource: Source[String, NotUsed]): Source[(BulkUserUpdatePayload, Long), NotUsed] = 
    lineSource
      .map(parser.parseUserLine)
      .zipWithIndex
      .divertTo(Sink.foreach(s => System.err.println(s"LINE ${s._2 + 1 + 1}: ${s._1}")), _._1.isLeft)
      .collect { case (Right(rqst), _) => Json.toJsObject(rqst) }
      .statefulMapConcat { () => 
        var bufferSize, itemCount = 0

        {
          case rqst =>
            itemCount += 1
            bufferSize += rqst.toString.length
            if(bufferSize > config.payloadSize || itemCount >= config.desiredBatchSize) {
              bufferSize = 0
              itemCount = 0
            }
            List((rqst, bufferSize == 0))
        }
      }
      .splitWhen { case (_, newBuffer) => newBuffer }
      .fold(BulkUserUpdatePayload()) { case (result, (rqst, _)) => result.copy(users = result.users :+ rqst) }
      .concatSubstreams
      .zipWithIndex

  def processFlow(config: Config) = Flow.fromGraph(GraphDSL.create() { implicit b => 
    import GraphDSL.Implicits._

    val apiPayloadProcessor = if(config.echoOnly) EchoPayloadProcessor(config, system) _ else ApiPayloadProcessor(config, system) _
    val process = b.add(Flow[(BulkUserUpdatePayload, Long)].mapAsyncUnordered(5) { case (payload, batchNo) => apiPayloadProcessor(payload).map(result => (result, payload, batchNo)) })
    val throttle = b.add(Flow[(Int, BulkUserUpdatePayload, Long)].throttle(5, 1.second))
    val merge = b.add(MergePreferred[(BulkUserUpdatePayload, Long)](1, eagerComplete = true))
    val (retryQueue, retrySource) = Source.queue[(BulkUserUpdatePayload, Long)](10).preMaterialize()
    val divertRetries = b.add(Flow[(Int, BulkUserUpdatePayload, Long)].divertTo(
      Sink.foreach { case (_, payload, batchNo) => 
        println(s"Retrying batchNo ${batchNo} due to 429")
        retryQueue.offer((payload, batchNo)) 
      }, 
      { case (status, _, _) => status == 429 }
    ))
    
    merge ~> process ~> throttle ~> divertRetries  // 429's are diverted to the retrySource
    merge.preferred        <~       retrySource 

    FlowShape(merge.in(0), divertRetries.out)
  })

  def sourceFromFile(filename: String): Future[(FileIngestionParser, Source[String, NotUsed])] = {
    if(filename.toLowerCase().endsWith(".json")) {
      Future.successful((new JsonParser(), Source.fromIterator(scala.io.Source.fromFile(filename).getLines)))
    } else {
      Source.fromIterator(scala.io.Source.fromFile(filename).getLines)
        .prefixAndTail(1)
        .runWith(Sink.head)
        .map {
          case (Seq(headerLine), contentSource) if filename.toLowerCase.endsWith(".csv") =>
            (new FileIngestionDelimitedParser(CsvParser, headerLine), contentSource)
          case (Seq(headerLine), contentSource) if filename.toLowerCase.endsWith(".tsv") =>
            (new FileIngestionDelimitedParser(TsvParser, headerLine), contentSource)
        }
    }
  }
}