package downloader

import java.nio.file.Path

data class CliArguments(
    val url: String,
    val outputPath: Path,
    val config: DownloadConfig,
)

object CliArgumentsParser {

    fun parse(args: Array<String>): CliArguments {
        if (args.isEmpty() || args.contains("--help")) {
            throw DownloadException("Help requested")
        }

        if (args.size < 2) {
            throw DownloadException("Missing required arguments: <url> and <output-path>")
        }

        val url = args[0]
        val outputPath = Path.of(args[1])

        var chunkSize = DownloadConfig.DEFAULT_CHUNK_SIZE
        var parallelism = DownloadConfig.DEFAULT_PARALLELISM
        var maxRetries = DownloadConfig.DEFAULT_MAX_RETRIES
        var maxFileSize: Long? = null
        var dryRun = false
        var timeoutSeconds = DownloadConfig.DEFAULT_TIMEOUT_SECONDS

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

                "--max-retries" -> {
                    maxRetries = readIntOption(args, index, "--max-retries")
                    index += 2
                }

                "--max-file-size" -> {
                    maxFileSize = readLongOption(args, index, "--max-file-size")
                    index += 2
                }

                "--timeout-seconds" -> {
                    timeoutSeconds = readLongOption(args, index, "--timeout-seconds")
                    index += 2
                }

                "--dry-run" -> {
                    dryRun = true
                    index += 1
                }

                else -> {
                    throw DownloadException("Unknown argument: ${args[index]}")
                }
            }
        }

        return CliArguments(
            url = url,
            outputPath = outputPath,
            config = DownloadConfig(
                chunkSize = chunkSize,
                parallelism = parallelism,
                maxRetries = maxRetries,
                maxFileSize = maxFileSize,
                dryRun = dryRun,
                timeoutSeconds = timeoutSeconds,
            ),
        )
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
}