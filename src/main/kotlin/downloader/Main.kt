package downloader

import java.nio.file.Path

fun main(args: Array<String>) {
    if (args.isEmpty() || args.contains("--help")) {
        printUsage()
        return
    }

    if (args.size < 2) {
        printUsage()
        throw DownloadException("Missing required arguments: <url> and <output-path>")
    }

    val url = args[0]
    val outputPath = Path.of(args[1])

    var chunkSize = 1024L * 1024L
    var parallelism = 4

    var index = 2
    while (index < args.size) {
        when (args[index]) {
            "--chunk-size" -> {
                chunkSize = readLongOption(args, index, "--chunk-size")
                index += 2
            }

            "--parallelism" -> {
                parallelism = readIntOption(args, index, "--parallelism")
                index += 2
            }

            else -> {
                throw DownloadException("Unknown argument: ${args[index]}")
            }
        }
    }

    val downloader = ParallelFileDownloader(
        DownloadConfig(
            chunkSize = chunkSize,
            parallelism = parallelism,
        )
    )

    downloader.download(url, outputPath)
}

private fun readLongOption(args: Array<String>, index: Int, optionName: String): Long {
    if (index + 1 >= args.size) {
        throw DownloadException("Missing value for $optionName")
    }

    return args[index + 1].toLongOrNull()
        ?: throw DownloadException("Invalid number for $optionName: ${args[index + 1]}")
}

private fun readIntOption(args: Array<String>, index: Int, optionName: String): Int {
    if (index + 1 >= args.size) {
        throw DownloadException("Missing value for $optionName")
    }

    return args[index + 1].toIntOrNull()
        ?: throw DownloadException("Invalid number for $optionName: ${args[index + 1]}")
}

private fun printUsage() {
    println(
        """
        Usage:
          ./gradlew run --args="<url> <output-path> [--chunk-size bytes] [--parallelism n]"

        Example:
          ./gradlew run --args="http://localhost:8080/big-file.txt downloaded-big-file.txt --chunk-size 1024 --parallelism 4"

        Options:
          --chunk-size    Size of each downloaded byte range. Default: 1048576
          --parallelism   Number of parallel download workers. Default: 4
        """.trimIndent()
    )
}