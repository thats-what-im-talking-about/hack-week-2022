// Contains all the data formats that are used for producing correct JSON payloads for the API call.

//> using lib "com.typesafe.play::play-json:2.8.2"
import play.api.libs.json.JsObject
import play.api.libs.json.Json

case class ApiUserUpdateRequest(
  email: Option[String],
  dataFields: Option[JsObject],
  userId: Option[String],
  preferUserId: Option[Boolean],
  mergeNestedObjects: Option[Boolean],
)

object ApiUserUpdateRequest {
  implicit val fmt = Json.format[ApiUserUpdateRequest]
}

case class TrackRequest(
  email: Option[String],
  eventName: String,
  id: Option[String],
  createdAt: Option[Long],
  dataFields: Option[JsObject],
  userId: Option[String],
  campaignId: Option[Long],
  templateId: Option[Long],
)

object TrackRequest {
  implicit val fmt = Json.format[TrackRequest]
}