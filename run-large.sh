#!/usr/bin/env bash
set -euo pipefail
phase=$1
repo=$2
here=$(cd "$(dirname "$0")" && pwd)
python3 "$here/run-command.py" "$repo" "$phase" python3 "$here/mill-direct.py" --no-server -j1 'cache.jvm[2.13.16].test.testOnly' coursier.cache.DownloadEncodingIntegrityTests -- \
  'coursier.cache.DownloadEncodingIntegrityTests.{large gzip body streams into the cache before the remainder arrives,large deflate body streams into the cache before the remainder arrives,large multi-member gzip streams inside a later member before the remainder arrives}'
