# Parallel File Downloader

![CI](https://github.com/kandera37/parallel-file-downloader/actions/workflows/ci.yml/badge.svg)

A Kotlin command-line file downloader that downloads files in parallel using HTTP byte-range requests.

The project was built for a Data Ingestion test task. It sends a `HEAD` request to inspect file metadata, validates that the server supports byte ranges, splits the file into chunks, downloads those chunks concurrently, and writes every chunk into the correct position in the output file.

## Features

- Downloads files using HTTP `Range` requests
- Splits files into configurable byte chunks
- Downloads chunks in parallel using a fixed-size thread pool
- Validates `Content-Length` and `Accept-Ranges: bytes`
- Validates `Content-Range` headers for range responses
- Verifies that every downloaded chunk has the expected size
- Writes chunks directly to their target offsets in the output file
- Retries failed chunk downloads with a configurable retry limit
- Supports an optional maximum file size limit before downloading starts
- Supports configurable HTTP request timeout
- Supports dry-run mode for validating metadata and planned chunks without downloading the file
- Prints a download summary with elapsed time and average speed
- Provides a command-line interface
- Includes unit tests with an embedded HTTP test server
- Includes GitHub Actions CI for automatic test execution

## Requirements

- JDK 17
- Gradle Wrapper is included, so Gradle does not need to be installed manually
- Docker is optional and only needed for manual smoke testing with Apache HTTP Server

## How it works

1. The downloader sends a `HEAD` request to the target URL with the configured HTTP timeout.
2. It checks that the server provides:
   - `Content-Length`
   - `Accept-Ranges: bytes`
3. If a maximum file size is configured, it checks the file size before downloading.
4. It splits the file into byte ranges based on the configured chunk size.
5. If dry-run mode is enabled, it prints the planned download summary and exits without creating the output file or sending range download requests.
6. Otherwise, it sends parallel `GET` requests with the `Range` header, for example:

   ```http
   Range: bytes=1024-2047
   ```

7. For every range response, it validates:
   - HTTP status `206 Partial Content`
   - `Content-Range` header
   - downloaded chunk size
8. Each downloaded chunk is written to its final position in the output file.
9. If a chunk download fails, it is retried up to the configured retry limit.
10. After all chunks complete successfully, the output file contains the full downloaded file.

## Design notes

The downloader writes each chunk using positional file writes:

```kotlin
channel.write(ByteBuffer.wrap(bytes), range.start)
```

This avoids sharing a mutable file cursor between worker threads. Each worker writes its own byte range directly to the correct offset.

The implementation also validates the size of every downloaded chunk. If a server returns fewer or more bytes than expected, the downloader fails instead of silently producing a corrupted file.

`Content-Range` validation is used to confirm that the server returned the same byte range that was requested. Checking only the chunk size is not enough, because a server could theoretically return the right number of bytes from the wrong range.

The optional maximum file size limit is checked after the `HEAD` request and before any range requests are sent. This prevents the downloader from starting large downloads when a size limit has been configured.

Dry-run mode is intended for quickly inspecting a source file before downloading it. It still performs the `HEAD` request, validates metadata, checks the optional maximum file size, and calculates the planned chunk layout, but it does not create the output file or send range download requests.

HTTP request timeout is applied to both metadata requests and range download requests. This prevents the downloader from waiting indefinitely if a server becomes slow or unresponsive.

## Project structure

```text
src/
  main/
    kotlin/
      downloader/
        ChunkRange.kt
        CliArguments.kt
        DownloadConfig.kt
        DownloadException.kt
        DownloadMetadata.kt
        Main.kt
        ParallelFileDownloader.kt
  test/
    kotlin/
      downloader/
        CliArgumentsTest.kt
        DownloadConfigTest.kt
        ParallelFileDownloaderTest.kt
```

## Running tests

```bash
./gradlew clean test
```

The tests use an embedded HTTP server, so Docker is not required for automated testing.

The test suite verifies:

- downloading a small file
- downloading a larger file split into multiple chunks
- handling a last partial chunk
- failing when range requests are not supported
- failing when `Content-Length` is missing
- sending multiple range requests for a large file
- retrying a temporarily failed range request
- failing when a range response has an unexpected size
- failing when `Content-Range` does not match the requested range
- failing when a file exceeds the configured maximum file size
- dry-run mode not creating an output file or sending range requests
- CLI argument parsing
- download configuration validation
- HTTP timeout option parsing and validation

## GitHub Actions CI

The repository includes GitHub Actions CI.

On every push to `main` and on every pull request, GitHub Actions runs:

```bash
./gradlew clean test
```

This verifies that the project builds and that the test suite passes in a clean environment.

## Smoke test script

The repository also includes a small smoke-test script for manual end-to-end testing with a local Apache server.

After starting Docker Apache as described below, run:

```bash
./scripts/smoke-test.sh
```


The script checks the `HEAD` response, performs a small range request, runs the downloader, compares the downloaded file with the source file, and prints file sizes.

The script expects the local Apache server to be running and the source file to exist at `~/downloader-test/big-file.txt`.

## Manual test with Docker

Create a local directory with a file:

```bash
mkdir -p ~/downloader-test
yes "Hello parallel downloader" | head -n 100000 > ~/downloader-test/big-file.txt
```

Start Apache HTTP Server with Docker:

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
./gradlew run --args="http://localhost:8080/big-file.txt cli-downloaded-big-file.txt --chunk-size 1024 --parallelism 4 --max-retries 3 --max-file-size 10000000 --timeout-seconds 30"
```

Arguments:

```text
<url>             URL of the file to download
<output-path>     Path where the downloaded file will be saved
--chunk-size      Size of each byte range in bytes. Default: 1048576
--parallelism     Number of parallel worker threads. Default: 4
--max-retries     Number of retry attempts per failed chunk. Default: 3
--max-file-size   Optional maximum allowed file size in bytes.
--timeout-seconds HTTP request timeout in seconds. Default: 30
--dry-run         Validate metadata and print the planned download without creating an output file.
```

### Dry-run mode

Dry-run mode can be used to inspect metadata and the planned chunk layout without downloading the file:

```bash
./gradlew run --args="http://localhost:8080/big-file.txt dry-run-output.txt --chunk-size 1024 --parallelism 4 --max-retries 3 --max-file-size 10000000 --timeout-seconds 30 --dry-run"
```

In this mode, the downloader validates the file metadata and prints the planned download summary, but it does not create `dry-run-output.txt` and does not send range download requests.

You can also display usage information:

```bash
./gradlew run --args="--help"
```

## Verifying the downloaded file

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

The CLI exits with an error when:

- the `HEAD` request fails
- `Content-Length` is missing or invalid
- the server does not support `Accept-Ranges: bytes`
- the file exceeds the configured maximum file size
- a range request does not return `206 Partial Content`
- `Content-Range` is missing or does not match the requested range
- a downloaded chunk has an unexpected size
- a chunk still fails after all retry attempts
- a configured option value is invalid, such as non-positive `chunkSize`, `parallelism`, `maxFileSize`, or `timeoutSeconds`
- an invalid CLI argument is provided

## Current limitations

- The downloader does not resume partially completed downloads.
- It does not verify checksums against an external expected hash.
- It does not adapt chunk size automatically based on file size or network behavior.
- It assumes that the server correctly supports standard HTTP byte ranges.
- It focuses on correctness, validation, and testability rather than advanced download-manager features.
