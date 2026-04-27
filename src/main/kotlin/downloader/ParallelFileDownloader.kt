package downloader

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class ParallelFileDownloader(
    private val config: DownloadConfig = DownloadConfig(),
) {
    private val client: HttpClient = HttpClient.newHttpClient()

    fun download(url: String, outputPath: Path) {
        val uri = URI.create(url)

        val metadata = fetchMetadata(uri)
        val ranges = createRanges(metadata.contentLength)

        prepareOutputFile(outputPath, metadata.contentLength)
        println("File size: ${metadata.contentLength} bytes")
        println("Chunk size: ${config.chunkSize} bytes")
        println("Chunks: ${ranges.size}")
        println("Parallelism: ${config.parallelism}")
        println("Max retries: ${config.maxRetries}")
        downloadRangesInParallel(uri, outputPath, ranges)

        println("Downloaded ${metadata.contentLength} bytes to $outputPath")
    }

    private fun fetchMetadata(uri: URI): DownloadMetadata {
        val headRequest = HttpRequest.newBuilder(uri)
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build()

        val headResponse = client.send(headRequest, HttpResponse.BodyHandlers.discarding())

        if (headResponse.statusCode() !in 200..299) {
            throw DownloadException("HEAD request failed with status ${headResponse.statusCode()}")
        }

        val contentLength = headResponse.headers()
            .firstValue("Content-Length")
            .orElseThrow { DownloadException("Missing Content-Length header") }
            .toLongOrNull()
            ?: throw DownloadException("Invalid Content-Length header")

        val acceptRanges = headResponse.headers()
            .firstValue("Accept-Ranges")
            .orElse("")

        if (!acceptRanges.equals("bytes", ignoreCase = true)) {
            throw DownloadException("Server does not support byte ranges")
        }

        return DownloadMetadata(
            contentLength = contentLength,
            acceptRanges = acceptRanges,
        )
    }

    private fun createRanges(contentLength: Long): List<ChunkRange> {
        if (contentLength <= 0) {
            throw DownloadException("File is empty or has invalid size")
        }

        val ranges = mutableListOf<ChunkRange>()
        var start = 0L

        while (start < contentLength) {
            val end = minOf(start + config.chunkSize - 1, contentLength - 1)
            ranges.add(ChunkRange(start, end))
            start = end + 1
        }

        return ranges
    }

    private fun prepareOutputFile(outputPath: Path, contentLength: Long) {
        Files.deleteIfExists(outputPath)

        FileChannel.open(
            outputPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.truncate(contentLength)
        }
    }

    private fun downloadRangesInParallel(
        uri: URI,
        outputPath: Path,
        ranges: List<ChunkRange>,
    ) {
        val executor = Executors.newFixedThreadPool(config.parallelism)

        try {
            val futures = ranges.map { range ->
                executor.submit {
                    downloadRangeWithRetries(uri, outputPath, range)
                }
            }

            futures.forEach { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    val cause = e.cause ?: e
                    throw DownloadException("Chunk download failed: ${cause.message}")
                }
            }
        } finally {
            executor.shutdown()
        }
    }

    private fun downloadRangeWithRetries(
        uri: URI,
        outputPath: Path,
        range: ChunkRange,
    ) {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt <= config.maxRetries) {
            try {
                downloadRange(uri, outputPath, range)
                return
            } catch (e: Exception) {
                lastError = e
                attempt++

                if (attempt <= config.maxRetries) {
                    println(
                        "Retrying range ${range.start}-${range.end} " +
                        "after failure: ${e.message} " +
                        "(attempt $attempt/${config.maxRetries})"
                    )
                }
            }
        }

        throw DownloadException(
            "Failed to download range ${range.start}-${range.end} " +
            "after ${config.maxRetries + 1} attempt(s): ${lastError?.message}"
        )
    }

    private fun downloadRange(
        uri: URI,
        outputPath: Path,
        range: ChunkRange,
    ) {
        val request = HttpRequest.newBuilder(uri)
            .GET()
            .header("Range", "bytes=${range.start}-${range.end}")
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())

        if (response.statusCode() != 206) {
            throw DownloadException(
                "Range request ${range.start}-${range.end} failed with status ${response.statusCode()}"
            )
        }

        validateContentRange(response, range)

        val bytes = response.body()
        val expectedSize = range.size

        if (bytes.size.toLong() != expectedSize) {
            throw DownloadException(
                "Invalid chunk size for range ${range.start}-${range.end}: " +
                "expected $expectedSize bytes, got ${bytes.size}"
            )
        }

        FileChannel.open(outputPath, StandardOpenOption.WRITE).use { channel ->
            channel.write(ByteBuffer.wrap(bytes), range.start)
        }
    }
    private fun validateContentRange(
        response: HttpResponse<ByteArray>,
        range: ChunkRange,
    ) {
        val contentRange = response.headers()
            .firstValue("Content-Range")
            .orElseThrow {
                DownloadException("Missing Content-Range header for range ${range.start}-${range.end}")
            }

        val expectedPrefix = "bytes ${range.start}-${range.end}/"

        if (!contentRange.startsWith(expectedPrefix)) {
            throw DownloadException(
                "Unexpected Content-Range for range ${range.start}-${range.end}: " +
                    "expected prefix '$expectedPrefix', got '$contentRange'"
            )
        }
    }
}