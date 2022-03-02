// Will contain the logic for parsing the command line and possibly environment variables into 
// the config for this process

//> using lib "com.github.scopt::scopt:4.0.1"

case class Config(
  filename: String = "",
  apiKey: String = "",
  payloadSize: Int = 4000000,
  desiredBatchSize: Int = Integer.MAX_VALUE,
  apiBaseUrl: String = "https://api.iterable.com",
  echoOnly: Boolean = false,
)

object Config {
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
        .text("Path to your input file.  Must end with .csv, .tsv, or .json"),
      opt[String]('k', "api-key")
        .required()
        .action((apiKey, c) => c.copy(apiKey = apiKey))
        .valueName("<api-key>")
        .text("Your Iterable API key."),
      opt[Int]('b', "batch-size")
        .valueName("<items>")
        .action((batchSize, c) => c.copy(desiredBatchSize = batchSize))
        .text("Optional.  The desired number of users to send with each bulk request.  If not set, the batch will be constrained by the 4MB limit set by the API."),
      opt[Unit]("echo-only")
        .action((_, c) => c.copy(echoOnly = true))
        .text("Dumps the API payloads but does not send them."),
    )
  }

  def apply(args: Array[String]): Option[Config] = OParser.parse(parser, args, Config())
}