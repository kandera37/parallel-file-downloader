package downloader

import java.nio.file.Path

fun main() {
    val downloader = ParallelFileDownloader(
        DownloadConfig(
            chunkSize = 1024,
            parallelism = 4,
        )
    )

    downloader.download(
        url = "http://localhost:8080/big-file.txt",
        outputPath = Path.of("downloaded-big-file.txt"),
    )
}