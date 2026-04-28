package downloader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DownloadConfigTest {
    @Test
    fun acceptsDefaultConfig() {
        val config = DownloadConfig()

        assertEquals(1024L * 1024L, config.chunkSize)
        assertEquals(4, config.parallelism)
        assertEquals(3, config.maxRetries)
        assertNull(config.maxFileSize)
        assertEquals(false, config.dryRun)
        assertEquals(30, config.timeoutSeconds)
    }

    @Test
    fun acceptsValidCustomConfig() {
        val config = DownloadConfig(
            chunkSize = 1024,
            parallelism = 8,
            maxRetries = 5,
            maxFileSize = 10_000_000,
            dryRun = true,
            timeoutSeconds = 15,
        )

        assertEquals(1024, config.chunkSize)
        assertEquals(8, config.parallelism)
        assertEquals(5, config.maxRetries)
        assertEquals(10_000_000, config.maxFileSize)
        assertEquals(true, config.dryRun)
        assertEquals(15, config.timeoutSeconds)
    }

    @Test
    fun failsWhenChunkSizeIsNotPositive() {
        assertFailsWith<IllegalArgumentException> {
            DownloadConfig(chunkSize = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            DownloadConfig(chunkSize = -1)
        }
    }

    @Test
    fun failsWhenParallelismIsNotPositive() {
        assertFailsWith<IllegalArgumentException> {
            DownloadConfig(parallelism = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            DownloadConfig(parallelism = -1)
        }
    }

    @Test
    fun failsWhenMaxRetriesIsNegative() {
        assertFailsWith<IllegalArgumentException> {
            DownloadConfig(maxRetries = -1)
        }
    }

    @Test
    fun failsWhenMaxFileSizeIsNotPositive() {
        assertFailsWith<IllegalArgumentException> {
            DownloadConfig(maxFileSize = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            DownloadConfig(maxFileSize = -1)
        }
    }

    @Test
    fun failsWhenTimeoutSecondsIsNotPositive() {
        assertFailsWith<IllegalArgumentException> {
            DownloadConfig(timeoutSeconds = 0)
        }

        assertFailsWith<IllegalArgumentException> {
            DownloadConfig(timeoutSeconds = -1)
        }
    }
}