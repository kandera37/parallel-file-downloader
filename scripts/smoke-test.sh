#!/usr/bin/env bash
set -euo pipefail

URL="${1:-http://localhost:8080/big-file.txt}"
SOURCE_FILE="${2:-$HOME/downloader-test/big-file.txt}"
OUTPUT_FILE="${3:-smoke-downloaded-big-file.txt}"

CHUNK_SIZE="${CHUNK_SIZE:-1024}"
PARALLELISM="${PARALLELISM:-4}"
MAX_RETRIES="${MAX_RETRIES:-3}"
MAX_FILE_SIZE="${MAX_FILE_SIZE:-10000000}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-30}"

echo "Smoke test for parallel-file-downloader"
echo "URL: $URL"
echo "Source file: $SOURCE_FILE"
echo "Output file: $OUTPUT_FILE"
echo "Chunk size: $CHUNK_SIZE"
echo "Parallelism: $PARALLELISM"
echo "Max retries: $MAX_RETRIES"
echo "Max file size: $MAX_FILE_SIZE"
echo "Timeout seconds: $TIMEOUT_SECONDS"
echo

if [ ! -f "$SOURCE_FILE" ]; then
  echo "Error: source file does not exist: $SOURCE_FILE" >&2
  echo "Create it with:" >&2
  echo "  mkdir -p ~/downloader-test" >&2
  echo "  yes \"Hello parallel downloader\" | head -n 100000 > ~/downloader-test/big-file.txt" >&2
  exit 1
fi

rm -f "$OUTPUT_FILE"

echo "Checking HEAD response..."
curl -I "$URL"

echo
echo "Checking a small range request..."
curl -s -H "Range: bytes=0-4" "$URL" >/dev/null

echo
echo "Running downloader..."
./gradlew run --args="$URL $OUTPUT_FILE --chunk-size $CHUNK_SIZE --parallelism $PARALLELISM --max-retries $MAX_RETRIES --max-file-size $MAX_FILE_SIZE --timeout-seconds $TIMEOUT_SECONDS"

echo
echo "Comparing files..."
diff "$SOURCE_FILE" "$OUTPUT_FILE"

echo
echo "File sizes:"
wc -c "$SOURCE_FILE" "$OUTPUT_FILE"

echo
echo "Smoke test passed."