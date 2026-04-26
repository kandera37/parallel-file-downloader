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

data class ChunkRange(
    val start: Long,
    val end: Long,
)

class ParallelFileDownloader(
    private val config: DownloadConfig = DownloadConfig(),
) {
    private val client: HttpClient = HttpClient.newHttpClient()

    fun download(url: String, outputPath: Path) {
        val uri = URI.create(url)

        val contentLength = fetchContentLength(uri)
        val ranges = createRanges(contentLength)

        prepareOutputFile(outputPath, contentLength)
        downloadRangesInParallel(uri, outputPath, ranges)

        println("Downloaded $contentLength bytes to $outputPath")
    }

    private fun fetchContentLength(uri: URI): Long {
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

        return contentLength
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
                    downloadRange(uri, outputPath, range)
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

        val bytes = response.body()
        val expectedSize = range.end - range.start + 1

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
}