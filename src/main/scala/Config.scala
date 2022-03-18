// Will contain the logic for parsing the command line and possibly environment variables into 
// the config for this process

//> using lib "com.github.scopt::scopt:4.0.1"

case class Config(
  filename: String = "",
  apiKey: String = "",
  payloadSize: Int = 4000000,
  desiredBatchSize: Int = Integer.MAX_VALUE,
  apiBaseUrl: String = "https://api.iterable.com",
  mergeNestedObjects: Boolean = Config.mergeNestedObjectsDefault,
  preferUserId: Boolean = Config.preferUserIdDefault,
  echoOnly: Boolean = false,
)

object Config {
  val preferUserIdDefault = false
  val mergeNestedObjectsDefault = false

  val execName = "bulkUserUpdate"
  import scopt.OParser
  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName(execName),
      head(execName, "0.1"),
      opt[String]('i', "input-file")
        .required()
        .action((infile, c) => c.copy(filename = infile))
        .valueName("<file>")
        .validate { 
          case name if name.endsWith(".tsv") => success
          case name if name.endsWith(".csv") => success
          case name if name.endsWith(".json") => success
          case _ => failure("Filename must end with .csv, .tsv, or .json")
        }
        .text(addBlankLine("Path to your input file.  Must end with .csv, .tsv, or .json")),
      opt[String]('k', "api-key")
        .required()
        .action((apiKey, c) => c.copy(apiKey = apiKey))
        .valueName("<api-key>")
        .text(addBlankLine("Your Iterable API key.")),
      opt[Int]('b', "batch-size")
        .valueName("<n>")
        .action((batchSize, c) => c.copy(desiredBatchSize = batchSize))
        .text(addBlankLine(
          """Optional.  The desired number of users to send with each bulk request.  If not set,
            |the batch size will be constrained by the 4MB payload size limit set by the API."""
            .stripMargin)),
      opt[Unit]("merge-nested-objects")
        .action((_, c) => c.copy(mergeNestedObjects = true))
        .text(addBlankLine(
          """Merge top level objects instead of overwriting. For example, if user profile has data:
            |{ mySettings: { mobile: true }} and the user being updated has data: 
            |{ mySettings: { email: true }}, the resulting profile will be: 
            |{ mySettings: { mobile: true, email: true }}"""
            .stripMargin)),
      opt[Unit]("prefer-user-id")
        .action((_, c) => c.copy(preferUserId = true))
        .text(addBlankLine("Create a new users with the specified userId if one does not yet exist.")),
      opt[Unit]("echo-only")
        .action((_, c) => c.copy(echoOnly = true))
        .text("Dumps the API payloads but does not send them."),
    )
  }

  def addBlankLine(s: String) = s"${s}\n "

  def apply(args: Array[String]): Option[Config] = OParser.parse(parser, args, Config())
}