// Will contain the logic for parsing the command line and possibly environment variables into 
// the config for this process

//> using lib "com.github.scopt::scopt:4.0.1"

case class Config(
  filename: String = "",
  apiKey: String = "",
  payloadSize: Int = 300,
  apiBaseUrl: String = "http://localhost:9000",
)

object Config {
  import scopt.OParser
  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("foo"),
      head("foo", "0.1"),
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
    )
  }

  def apply(args: Array[String]): Option[Config] = OParser.parse(parser, args, Config())
}