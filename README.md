# Parallel File Downloader

A Kotlin command-line file downloader that downloads files in parallel using HTTP byte-range requests.

The downloader sends a `HEAD` request to inspect file metadata, checks `Content-Length` and `Accept-Ranges: bytes`, splits the file into chunks, downloads the chunks concurrently using HTTP `Range` requests, and writes every chunk to the correct position in the output file.

## Features

- HTTP `Range` request support
- Parallel chunk downloading
- Configurable chunk size
- Configurable parallelism
- Validation of `Content-Length` and `Accept-Ranges`
- Chunk size validation
- Command-line interface
- Unit tests with an embedded HTTP server

## Requirements

- JDK 17
- Docker, optional, for manual testing
- Gradle does not need to be installed manually because the Gradle Wrapper is included

## Running tests

```bash
./gradlew clean test
```

The tests use an embedded HTTP server and do not require Docker.

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

Check that the server supports byte ranges:

```bash
curl -I http://localhost:8080/big-file.txt
```

Expected headers include:

```text
Accept-Ranges: bytes
Content-Length: ...
```

## Running the downloader

```bash
./gradlew run --args="http://localhost:8080/big-file.txt cli-downloaded-big-file.txt --chunk-size 1024 --parallelism 4"
```

Arguments:

```text
<url>           URL of the file to download
<output-path>   Path where the downloaded file will be saved
--chunk-size    Size of each byte range in bytes. Default: 1048576
--parallelism   Number of parallel worker threads. Default: 4
```

## Verifying the result

```bash
diff ~/downloader-test/big-file.txt cli-downloaded-big-file.txt
wc -c ~/downloader-test/big-file.txt cli-downloaded-big-file.txt
```

If `diff` prints nothing and the file sizes match, the downloaded file is correct.

## Design notes

Each chunk is written using a positional file write:

```kotlin
channel.write(ByteBuffer.wrap(bytes), range.start)
```

This avoids sharing a mutable file cursor between worker threads.

The downloader also checks that each downloaded chunk has the expected size. If a server returns an invalid chunk, the downloader fails instead of silently producing a corrupted file.

## Error handling

The downloader throws `DownloadException` when:

- the `HEAD` request fails
- `Content-Length` is missing or invalid
- the server does not support `Accept-Ranges: bytes`
- a range request does not return `206 Partial Content`
- a downloaded chunk has an unexpected size
- CLI arguments are invalid

## Current limitations

- No retry logic for failed chunks
- No resume support for partially downloaded files
- Assumes the server correctly supports standard HTTP byte ranges
