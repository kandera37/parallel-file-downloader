# Parallel File Downloader

A Kotlin command-line file downloader that downloads files in parallel using HTTP byte-range requests.

The downloader sends a `HEAD` request to inspect file metadata, checks `Content-Length` and `Accept-Ranges: bytes`, splits the file into chunks, downloads the chunks concurrently using HTTP `Range` requests, and writes every chunk to the correct position in the output file.

## Features

- HTTP `Range` request support
- Parallel chunk downloading
- Configurable chunk size
- Configurable parallelism
- Configurable retry attempts for failed chunks
- Validation of `Content-Length` and `Accept-Ranges`
- Chunk size validation to avoid silently writing corrupted output
- Command-line interface
- Unit tests with an embedded HTTP server
- GitHub Actions CI for automatic test runs

## Requirements

- JDK 17
- Docker, optional, for manual testing
- Gradle does not need to be installed manually because the Gradle Wrapper is included

## How it works

1. The downloader sends a `HEAD` request to the target URL.
2. It checks that the server provides:
    - `Content-Length`
    - `Accept-Ranges: bytes`
3. It splits the file into byte ranges based on the configured chunk size.
4. It sends parallel `GET` requests with the `Range` header, for example:

   ```http
   Range: bytes=1024-2047
   ```

5. If a chunk request fails, the downloader retries it according to `maxRetries`.
6. Each downloaded chunk is written to its final position in the output file.
7. After all chunks complete successfully, the output file contains the full downloaded file.

## Design notes

The downloader writes each chunk using positional file writes:

```kotlin
channel.write(ByteBuffer.wrap(bytes), range.start)
```

This avoids sharing a mutable file cursor between worker threads. Each worker writes its own byte range directly to the correct offset.

The implementation also validates the size of every downloaded chunk. If a server returns fewer or more bytes than expected, the downloader fails instead of silently producing a corrupted file.

Retry logic is applied per chunk. This means a temporary failure of one range request does not immediately fail the whole download, but persistent failures still result in a `DownloadException`.

## Project structure

```text
parallel-file-downloader/
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── gradle/
│   └── wrapper/
├── .github/
│   └── workflows/
│       └── ci.yml
└── src/
    ├── main/
    │   └── kotlin/
    │       └── downloader/
    │           ├── ChunkRange.kt
    │           ├── DownloadConfig.kt
    │           ├── DownloadException.kt
    │           ├── DownloadMetadata.kt
    │           ├── Main.kt
    │           └── ParallelFileDownloader.kt
    └── test/
        └── kotlin/
            └── downloader/
                └── ParallelFileDownloaderTest.kt
```

## Running tests

```bash
./gradlew clean test
```

The tests use an embedded HTTP server and do not require Docker.

The test suite verifies:

- downloading a small file
- downloading a larger file split into multiple chunks
- handling a last partial chunk
- failing when range requests are not supported
- failing when `Content-Length` is missing
- sending multiple range requests for a large file
- retrying a temporary failed range request
- failing when a range response has an unexpected chunk size

## Continuous integration

The repository includes a GitHub Actions workflow:

```text
.github/workflows/ci.yml
```

On each push to `main` and on pull requests, GitHub Actions runs:

```bash
./gradlew clean test
```

This verifies that the project builds and passes tests in a clean environment, not only on the local machine.

## Manual testing with Docker

Create a test file:

```bash
mkdir -p ~/downloader-test
yes "Hello parallel downloader" | head -n 100000 > ~/downloader-test/big-file.txt
```

Start Apache HTTP Server:

```bash
docker run --rm -p 8080:80 -v ~/downloader-test:/usr/local/apache2/htdocs/ httpd:latest
```

In another terminal, check that the server supports byte ranges:

```bash
curl -I http://localhost:8080/big-file.txt
```

Expected headers include:

```text
Accept-Ranges: bytes
Content-Length: ...
```

You can also test a range request manually:

```bash
curl -H "Range: bytes=0-4" http://localhost:8080/big-file.txt
```

## Running the downloader

```bash
./gradlew run --args="http://localhost:8080/big-file.txt cli-downloaded-big-file.txt --chunk-size 1024 --parallelism 4 --max-retries 3"
```

Arguments:

```text
<url>           URL of the file to download
<output-path>   Path where the downloaded file will be saved
--chunk-size    Size of each byte range in bytes. Default: 1048576
--parallelism   Number of parallel worker threads. Default: 4
--max-retries   Number of retry attempts per failed chunk. Default: 3
```

## Verifying the result

Compare the original and downloaded files:

```bash
diff ~/downloader-test/big-file.txt cli-downloaded-big-file.txt
```

If `diff` prints nothing, the files are identical.

You can also compare file sizes:

```bash
wc -c ~/downloader-test/big-file.txt cli-downloaded-big-file.txt
```

## Error handling

The downloader throws `DownloadException` when:

- the `HEAD` request fails
- `Content-Length` is missing or invalid
- the server does not support `Accept-Ranges: bytes`
- a range request does not return `206 Partial Content`
- a downloaded chunk has an unexpected size
- a chunk still fails after all retry attempts
- CLI arguments are invalid

## Current limitations

- The downloader does not resume partially completed downloads.
- It does not verify file checksums.
- It assumes that the server correctly supports standard HTTP byte ranges.
- It focuses on correctness, parallel downloading, and clear behavior rather than advanced download-manager features.
