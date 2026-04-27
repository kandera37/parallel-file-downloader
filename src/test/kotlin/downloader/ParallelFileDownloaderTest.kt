package downloader

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class ParallelFileDownloaderTest {
    @Test
    fun downloadsSmallFileCorrectly() {
        val data = "Hello from test server!".toByteArray()
        RangeTestServer(data).use { server ->
            val outputPath = Files.createTempFile("downloaded-small", ".txt")

            val downloader = ParallelFileDownloader(
                DownloadConfig(
                    chunkSize = 5,
                    parallelism = 2,
                )
            )

            downloader.download(server.url(), outputPath)

            val downloaded = Files.readAllBytes(outputPath)
            assertContentEquals(data, downloaded)

            Files.deleteIfExists(outputPath)
        }
    }

    @Test
    fun downloadsLargeFileInChunksCorrectly() {
        val data = ByteArray(100_000) { index ->
            (index % 256).toByte()
        }

        RangeTestServer(data).use { server ->
            val outputPath = Files.createTempFile("downloaded-large", ".bin")

            val downloader = ParallelFileDownloader(
                DownloadConfig(
                    chunkSize = 1024,
                    parallelism = 4,
                )
            )

            downloader.download(server.url(), outputPath)

            val downloaded = Files.readAllBytes(outputPath)
            assertContentEquals(data, downloaded)

            Files.deleteIfExists(outputPath)
        }
    }

    @Test
    fun downloadsFileWithLastPartialChunkCorrectly() {
        val data = ByteArray(10_003) { index ->
            (index % 127).toByte()
        }

        RangeTestServer(data).use { server ->
            val outputPath = Files.createTempFile("downloaded-partial", ".bin")

            val downloader = ParallelFileDownloader(
                DownloadConfig(
                    chunkSize = 1000,
                    parallelism = 3,
                )
            )

            downloader.download(server.url(), outputPath)

            val downloaded = Files.readAllBytes(outputPath)
            assertContentEquals(data, downloaded)

            Files.deleteIfExists(outputPath)
        }
    }

    @Test
    fun failsWhenServerDoesNotSupportRanges() {
        val data = "range support disabled".toByteArray()

        RangeTestServer(
            data = data,
            supportsRanges = false,
        ).use { server ->
            val outputPath = Files.createTempFile("downloaded-no-ranges", ".txt")

            val downloader = ParallelFileDownloader(
                DownloadConfig(
                    chunkSize = 5,
                    parallelism = 2,
                )
            )

            assertFailsWith<DownloadException> {
                downloader.download(server.url(), outputPath)
            }

            Files.deleteIfExists(outputPath)
        }
    }

    @Test
    fun failsWhenContentLengthIsMissing() {
        val data = "missing content length".toByteArray()

        RangeTestServer(
            data = data,
            includeContentLength = false,
        ).use { server ->
            val outputPath = Files.createTempFile("downloaded-no-length", ".txt")

            val downloader = ParallelFileDownloader(
                DownloadConfig(
                    chunkSize = 5,
                    parallelism = 2,
                )
            )

            assertFailsWith<DownloadException> {
                downloader.download(server.url(), outputPath)
            }

            Files.deleteIfExists(outputPath)
        }
    }

    @Test
    fun sendsMultipleRangeRequestsForLargeFile() {
        val data = ByteArray(20_000) { index ->
            (index % 256).toByte()
        }

        RangeTestServer(data).use { server ->
            val outputPath = Files.createTempFile("downloaded-counted", ".bin")

            val downloader = ParallelFileDownloader(
                DownloadConfig(
                    chunkSize = 1000,
                    parallelism = 4,
                )
            )

            downloader.download(server.url(), outputPath)

            val downloaded = Files.readAllBytes(outputPath)
            assertContentEquals(data, downloaded)

            assert(server.rangeRequestCount.get() > 1) {
                "Expected multiple range requests, got ${server.rangeRequestCount.get()}"
            }

            Files.deleteIfExists(outputPath)
        }
    }

    @Test
    fun retriesFailedRangeRequestAndEventuallyDownloadsFile() {
        val data = ByteArray(20_000) { index ->
            (index % 256).toByte()
        }

        RangeTestServer(
            data = data,
            failFirstRangeRequest = true,
        ).use { server ->
            val outputPath = Files.createTempFile("downloaded-retry", ".bin")

            val downloader = ParallelFileDownloader(
                DownloadConfig(
                    chunkSize = 1000,
                    parallelism = 4,
                    maxRetries = 2,
                )
            )

            downloader.download(server.url(), outputPath)

            val downloaded = Files.readAllBytes(outputPath)
            assertContentEquals(data, downloaded)

            assert(server.failedRangeRequestCount.get() == 1) {
                "Expected exactly one simulated failed range request, got ${server.failedRangeRequestCount.get()}"
            }

            assert(server.rangeRequestCount.get() > 1) {
                "Expected multiple range requests, got ${server.rangeRequestCount.get()}"
            }

            Files.deleteIfExists(outputPath)
        }
    }

    @Test
    fun failsWhenRangeResponseHasUnexpectedSize() {
        val data = ByteArray(20_000) { index ->
            (index % 256).toByte()
        }

        RangeTestServer(
            data = data,
            corruptFirstRangeResponse = true,
        ).use { server ->
            val outputPath = Files.createTempFile("downloaded-corrupted", ".bin")

            val downloader = ParallelFileDownloader(
                DownloadConfig(
                    chunkSize = 1000,
                    parallelism = 4,
                    maxRetries = 0,
                )
            )

            assertFailsWith<DownloadException> {
                downloader.download(server.url(), outputPath)
            }

            assert(server.corruptedRangeResponseCount.get() == 1) {
                "Expected exactly one corrupted range response, got ${server.corruptedRangeResponseCount.get()}"
            }

            Files.deleteIfExists(outputPath)
        }
    }

    @Test
    fun failsWhenContentRangeDoesNotMatchRequestedRange() {
        val data = ByteArray(20_000) { index ->
            (index % 256).toByte()
        }

        RangeTestServer(
            data = data,
            invalidContentRange = true,
        ).use { server ->
            val outputPath = Files.createTempFile("downloaded-invalid-content-range", ".bin")

            val downloader = ParallelFileDownloader(
                DownloadConfig(
                    chunkSize = 1000,
                    parallelism = 4,
                    maxRetries = 0,
                )
            )

            assertFailsWith<DownloadException> {
                downloader.download(server.url(), outputPath)
            }

            Files.deleteIfExists(outputPath)
        }
    }
}

private class RangeTestServer(
    private val data: ByteArray,
    private val supportsRanges: Boolean = true,
    private val includeContentLength: Boolean = true,
    private val failFirstRangeRequest: Boolean = false,
    private val corruptFirstRangeResponse: Boolean = false,
    private val invalidContentRange: Boolean = false,
) : AutoCloseable {
    private val server: HttpServer = HttpServer.create(InetSocketAddress(0), 0)

    val rangeRequestCount = AtomicInteger(0)
    val failedRangeRequestCount = AtomicInteger(0)
    val corruptedRangeResponseCount = AtomicInteger(0)

    init {
        server.createContext("/file") { exchange ->
            handle(exchange)
        }

        server.start()
    }

    fun url(): String {
        return "http://localhost:${server.address.port}/file"
    }

    private fun handle(exchange: HttpExchange) {
        try {
            when (exchange.requestMethod) {
                "HEAD" -> handleHead(exchange)
                "GET" -> handleGet(exchange)
                else -> {
                    exchange.sendResponseHeaders(405, -1)
                }
            }
        } finally {
            exchange.close()
        }
    }

    private fun handleHead(exchange: HttpExchange) {
        if (supportsRanges) {
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
        }

        if (includeContentLength) {
            exchange.responseHeaders.add("Content-Length", data.size.toString())
        }

        exchange.sendResponseHeaders(200, -1)
    }

    private fun handleGet(exchange: HttpExchange) {
        if (supportsRanges) {
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
        }

        val rangeHeader = exchange.requestHeaders.getFirst("Range")

        if (rangeHeader == null) {
            exchange.responseHeaders.add("Content-Length", data.size.toString())
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.write(data)
            return
        }

        rangeRequestCount.incrementAndGet()

        if (failFirstRangeRequest && failedRangeRequestCount.compareAndSet(0, 1)) {
            val message = "Simulated temporary range request failure"
            val responseBody = message.toByteArray()
            exchange.sendResponseHeaders(500, responseBody.size.toLong())
            exchange.responseBody.write(responseBody)
            return
        }

        val range = parseRange(rangeHeader)
        val expectedChunk = data.copyOfRange(range.first.toInt(), range.second.toInt() + 1)

        val chunk =
            if (corruptFirstRangeResponse && corruptedRangeResponseCount.compareAndSet(0, 1)) {
                expectedChunk.dropLast(1).toByteArray()
            } else {
                expectedChunk
            }

        val contentRange =
            if (invalidContentRange) {
                "bytes 0-0/${data.size}"
            } else {
                "bytes ${range.first}-${range.second}/${data.size}"
            }

        exchange.responseHeaders.add("Content-Range", contentRange)
        exchange.responseHeaders.add("Content-Length", chunk.size.toString())

        exchange.sendResponseHeaders(206, chunk.size.toLong())
        exchange.responseBody.write(chunk)
    }

    private fun parseRange(header: String): Pair<Long, Long> {
        val prefix = "bytes="

        if (!header.startsWith(prefix)) {
            throw IllegalArgumentException("Invalid Range header: $header")
        }

        val parts = header.removePrefix(prefix).split("-")

        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid Range header: $header")
        }

        val start = parts[0].toLong()
        val end = parts[1].toLong()

        if (start < 0 || end < start || end >= data.size) {
            throw IllegalArgumentException("Invalid Range values: $header")
        }

        return start to end
    }

    override fun close() {
        server.stop(0)
    }
}