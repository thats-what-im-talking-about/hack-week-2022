//> using lib "com.github.tototoshi::scala-csv:1.3.10"
import com.github.tototoshi.csv.CSVParser
import com.github.tototoshi.csv.defaultCSVFormat
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import FileIngestionErrorCode.ParseError
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.JsString
import play.api.libs.json.JsNull

trait FileIngestionParser {
  def parseUserLine(line: String): Either[Error, ApiUserUpdateRequest]
  def parseEventLine(line: String): Either[Error, TrackRequest]
}

trait DelimitedParser {
  def parseLine(input: String): Option[List[String]]
}

object FieldNames {
  val Email = "email"
  val UserId = "userId"
  val PreferUserId = "preferUserId"
  val MergeNestedObjects = "mergeNestedObjects"
  val EventName = "eventName"
  val Id = "id"
  val CreatedAt = "createdAt"
  val CampaignId = "campaignId"
  val TemplateId = "templateId"
}

object CsvParser extends CSVParser(defaultCSVFormat) with DelimitedParser
object TsvParser extends DelimitedParser {
  // Without the -1 split will ignore trailing tabs
  override def parseLine(input: String): Option[List[String]] = Some(input.split("\t", -1).toList)
}

class FileIngestionDelimitedParser(parser: DelimitedParser, header: String) extends FileIngestionParser {
  private val fields =
    parser.parseLine(header).getOrElse(throw new RuntimeException("Unable to parse as valid header"))

  override def parseUserLine(line: String): Either[Error, ApiUserUpdateRequest] =
    parseDelimitedLine(line) { fieldVals =>
      Try {
        val fieldValsMap = fieldVals.toMap
        val email = fieldValsMap.get(FieldNames.Email).flatten
        val userId = fieldValsMap.get(FieldNames.UserId).flatten
        val preferUserId = fieldValsMap.get(FieldNames.PreferUserId).flatten.map(_.toBoolean)
        val mergeNestedObjects = fieldValsMap.get(FieldNames.MergeNestedObjects).flatten.map(_.toBoolean)
        val dataFieldVals = fieldValsMap -- Set(
          FieldNames.Email,
          FieldNames.UserId,
          FieldNames.PreferUserId,
          FieldNames.MergeNestedObjects,
        )
        ApiUserUpdateRequest(email, createDataFields(dataFieldVals), userId, preferUserId, mergeNestedObjects)
      }
    }

  override def parseEventLine(line: String): Either[Error, TrackRequest] =
    parseDelimitedLine(line) { fieldVals =>
      Try {
        val fieldValsMap = fieldVals.toMap
        val email = fieldValsMap.get(FieldNames.Email).flatten
        val eventName = fieldValsMap(FieldNames.EventName).get
        val id = fieldValsMap.get(FieldNames.Id).flatten
        val createdAt = fieldValsMap.get(FieldNames.CreatedAt).flatten.map(_.toLong)
        val userId = fieldValsMap.get(FieldNames.UserId).flatten
        val campaignId = fieldValsMap.get(FieldNames.CampaignId).flatten.map(_.toLong)
        val templateId = fieldValsMap.get(FieldNames.TemplateId).flatten.map(_.toLong)

        val dataFieldVals = fieldValsMap -- Set(
          FieldNames.Email,
          FieldNames.EventName,
          FieldNames.Id,
          FieldNames.CreatedAt,
          FieldNames.UserId,
          FieldNames.CampaignId,
          FieldNames.TemplateId,
        )

        val datafields = createDataFields(dataFieldVals)
        TrackRequest(email, eventName, id, createdAt, datafields, userId, campaignId, templateId)
      }
    }

  private def parseDelimitedLine[T](
    line: String
  )(createObject: Seq[(String, Option[String])] => Try[T]): Either[Error, T] = {
    Try { parser.parseLine(line) } match {
      case Success(Some(values)) if values.length == fields.length =>
        val valueOpts = values.map { case "" => None; case v => Some(v) }
        val fieldsVals = fields.zip(valueOpts)
        createObject(fieldsVals)
          .map(Right(_))
          .getOrElse(Left(Error("Not a valid CSV line", ParseError)))
      case Success(Some(values)) =>
        Left(Error(s"Line had ${values.length} values, expected ${fields.length}", ParseError))
      case Success(None) => Left(Error("Unable to read line", ParseError))
      case Failure(e) =>
        Left(Error("Unable to read line", ParseError))
    }
  }

  private def createDataFields(dataFieldVals: Iterable[(String, Option[String])]): Option[JsObject] = {
    if (dataFieldVals.isEmpty) {
      None
    } else {
      Some(dataFieldVals.foldLeft(Json.obj()) {
        case (dataFields, (field, value)) => dataFields + (field -> value.map(JsString).getOrElse(JsNull))
      })
    }
  }
}
