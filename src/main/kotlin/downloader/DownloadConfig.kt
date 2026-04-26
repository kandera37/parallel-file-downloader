package downloader

data class DownloadConfig(
    val chunkSize: Long = 1024 * 1024,
    val parallelism: Int = 4,
) {
    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
        require(parallelism > 0) { "parallelism must be positive" }
    }
}