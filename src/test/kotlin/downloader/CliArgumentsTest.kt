package downloader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import java.nio.file.Path

class CliArgumentsTest {
    @Test
    fun parsesRequiredArgumentsWithDefaultConfig() {
        val arguments = CliArgumentsParser.parse(
            arrayOf(
                "http://localhost:8080/file.txt",
                "downloaded.txt",
            )
        )

        assertEquals("http://localhost:8080/file.txt", arguments.url)
        assertEquals(Path.of("downloaded.txt"), arguments.outputPath)
        assertEquals(1024L * 1024L, arguments.config.chunkSize)
        assertEquals(4, arguments.config.parallelism)
        assertEquals(3, arguments.config.maxRetries)
        assertNull(arguments.config.maxFileSize)
        assertEquals(false, arguments.config.dryRun)
        assertEquals(30, arguments.config.timeoutSeconds)
    }

    @Test
    fun parsesOptionalArguments() {
        val arguments = CliArgumentsParser.parse(
            arrayOf(
                "http://localhost:8080/file.txt",
                "downloaded.txt",
                "--chunk-size",
                "1024",
                "--parallelism",
                "8",
                "--max-retries",
                "5",
                "--max-file-size",
                "10000000",
                "--timeout-seconds",
                "15",
                "--dry-run",
            )
        )

        assertEquals(1024, arguments.config.chunkSize)
        assertEquals(8, arguments.config.parallelism)
        assertEquals(5, arguments.config.maxRetries)
        assertEquals(10_000_000, arguments.config.maxFileSize)
        assertEquals(true, arguments.config.dryRun)
        assertEquals(15, arguments.config.timeoutSeconds)
    }

    @Test
    fun parsesDryRunFlag() {
        val arguments = CliArgumentsParser.parse(
            arrayOf(
                "http://localhost:8080/file.txt",
                "downloaded.txt",
                "--dry-run",
            )
        )

        assertEquals(true, arguments.config.dryRun)
    }

    @Test
    fun parsesTimeoutSecondsOption() {
        val arguments = CliArgumentsParser.parse(
            arrayOf(
                "http://localhost:8080/file.txt",
                "downloaded.txt",
                "--timeout-seconds",
                "10",
            )
        )

        assertEquals(10, arguments.config.timeoutSeconds)
    }

    @Test
    fun failsWhenRequiredArgumentsAreMissing() {
        assertFailsWith<DownloadException> {
            CliArgumentsParser.parse(arrayOf("http://localhost:8080/file.txt"))
        }
    }

    @Test
    fun failsOnUnknownArgument() {
        assertFailsWith<DownloadException> {
            CliArgumentsParser.parse(
                arrayOf(
                    "http://localhost:8080/file.txt",
                    "downloaded.txt",
                    "--unknown",
                    "123",
                )
            )
        }
    }

    @Test
    fun failsWhenOptionValueIsMissing() {
        assertFailsWith<DownloadException> {
            CliArgumentsParser.parse(
                arrayOf(
                    "http://localhost:8080/file.txt",
                    "downloaded.txt",
                    "--chunk-size",
                )
            )
        }
    }

    @Test
    fun failsWhenNumericOptionIsInvalid() {
        assertFailsWith<DownloadException> {
            CliArgumentsParser.parse(
                arrayOf(
                    "http://localhost:8080/file.txt",
                    "downloaded.txt",
                    "--parallelism",
                    "abc",
                )
            )
        }
    }

    @Test
    fun failsWhenConfigValueIsInvalid() {
        assertFailsWith<IllegalArgumentException> {
            CliArgumentsParser.parse(
                arrayOf(
                    "http://localhost:8080/file.txt",
                    "downloaded.txt",
                    "--parallelism",
                    "0",
                )
            )
        }
    }
}