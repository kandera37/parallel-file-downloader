package downloader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull

class DownloadConfigTest {
    @Test
    fun acceptsDefaultConfig() {
        val config = DownloadConfig()

        assertEquals(DownloadConfig.DEFAULT_CHUNK_SIZE, config.chunkSize)
        assertEquals(DownloadConfig.DEFAULT_PARALLELISM, config.parallelism)
        assertEquals(DownloadConfig.DEFAULT_MAX_RETRIES, config.maxRetries)
        assertNull(config.maxFileSize)
        assertFalse(config.dryRun)
        assertEquals(DownloadConfig.DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds)
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
        assertTrue(config.dryRun)
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