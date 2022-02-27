// Error code information that has been brought in from the Iterable code base.

//> using lib "com.beachape::enumeratum:1.7.0"
import enumeratum._
import play.api.libs.json.Writes
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Format

case class ErrorRecord(
  message: String,
  code: FileIngestionErrorCode,
  lineNumber: Long,
)

case class Error(
  message: String,
  code: FileIngestionErrorCode,
) {
  def toRecord(lineNumber: Long): ErrorRecord = ErrorRecord(message, code, lineNumber)
}

sealed trait FileIngestionErrorCode extends EnumEntry with Product with Serializable

object FileIngestionErrorCode extends Enum[FileIngestionErrorCode] {
  case object ParseError extends FileIngestionErrorCode
  case object NoUserKey extends FileIngestionErrorCode
  case object InvalidEmail extends FileIngestionErrorCode
  case object InvalidUserId extends FileIngestionErrorCode
  case object UserDoesNotExist extends FileIngestionErrorCode
  case object UserForgotten extends FileIngestionErrorCode
  case object UniqueFieldLimitExceeded extends FileIngestionErrorCode
  case object FieldTypeMismatch extends FileIngestionErrorCode
  case object UnprocessableData extends FileIngestionErrorCode
  case object ForbiddenFieldModification extends FileIngestionErrorCode
  case object TrackingDisallowed extends FileIngestionErrorCode
  case object InternalError extends FileIngestionErrorCode
  case object ContentTruncated extends FileIngestionErrorCode

  override val values: IndexedSeq[FileIngestionErrorCode] = findValues
}