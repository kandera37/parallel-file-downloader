package downloader

data class DownloadMetadata(
    val contentLength: Long,
    val acceptRanges: String,
)