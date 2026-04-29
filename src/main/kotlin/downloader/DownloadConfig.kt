package downloader

data class DownloadConfig(
    val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    val parallelism: Int = DEFAULT_PARALLELISM,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val maxFileSize: Long? = null,
    val dryRun: Boolean = false,
    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) {
    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
        require(parallelism > 0) { "parallelism must be positive" }
        require(maxRetries >= 0) { "maxRetries must be zero or positive" }
        require(maxFileSize == null || maxFileSize > 0) {
            "maxFileSize must be positive when provided"
        }
        require(timeoutSeconds > 0) { "timeoutSeconds must be positive" }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 1024L * 1024L
        const val DEFAULT_PARALLELISM = 4
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_TIMEOUT_SECONDS = 30L
    }
}