package downloader

data class ChunkRange(
    val start: Long,
    val end: Long,
) {
    val size: Long
        get() = end - start + 1
}