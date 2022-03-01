// Will contain the logic for parsing the command line and possibly environment variables into 
// the config for this process

//> using lib "com.github.scopt::scopt:4.0.1"

case class Config(
  filename: String = "",
  apiKey: String = "",
  payloadSize: Int = 4000000,
  desiredBatchSize: Int = Integer.MAX_VALUE,
  apiBaseUrl: String = "https://api.iterable.com",
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
        .text("Path to your input CSV file."),
      opt[String]('k', "api-key")
        .required()
        .action((apiKey, c) => c.copy(apiKey = apiKey))
        .valueName("<api-key>")
        .text("Your Iterable API key."),
      opt[Int]('b', "batch-size")
        .valueName("<num items per batch>")
        .action((batchSize, c) => c.copy(desiredBatchSize = batchSize))
        .text("Optional.  The desired number of users to send with each bulk request.  If not set, the batch will be constrained by the 4MB limit set by the API.")
    )
  }

  def apply(args: Array[String]): Option[Config] = OParser.parse(parser, args, Config())
}