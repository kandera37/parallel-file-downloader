package downloader

data class DownloadConfig(
    val chunkSize: Long = 1024 * 1024,
    val parallelism: Int = 4,
    val maxRetries: Int = 3,
    val maxFileSize: Long? = null,
) {
    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
        require(parallelism > 0) { "parallelism must be positive" }
        require(maxRetries >= 0) { "maxRetries must be zero or positive" }
        require(maxFileSize == null || maxFileSize > 0) { "maxFileSize must be positive when provided" }
    }
}