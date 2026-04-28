package downloader

fun main(args: Array<String>) {
    if (args.isEmpty() || args.contains("--help")) {
        printUsage()
        return
    }

    val cliArguments = CliArgumentsParser.parse(args)

    val downloader = ParallelFileDownloader(cliArguments.config)

    downloader.download(
        url = cliArguments.url,
        outputPath = cliArguments.outputPath,
    )
}

private fun printUsage() {
    println(
        """
        Usage:
          ./gradlew run --args="<url> <output-path> [--chunk-size bytes] [--parallelism n] [--max-retries n] [--max-file-size bytes]"

        Example:
          ./gradlew run --args="http://localhost:8080/big-file.txt cli-downloaded-big-file.txt --chunk-size 1024 --parallelism 4 --max-retries 3 --max-file-size 10000000"

        Options:
          --chunk-size      Size of each downloaded byte range. Default: 1048576
          --parallelism     Number of parallel download workers. Default: 4
          --max-retries     Number of retry attempts per failed chunk. Default: 3
          --max-file-size   Optional maximum allowed file size in bytes.
        """.trimIndent()
    )
}