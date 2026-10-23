#!/usr/bin/env bash
set -euo pipefail
repo=${1:?Expected fresh coursier path}
cd "$repo"
git fetch origin
git rebase origin/main
test "$(git rev-parse HEAD)" = 119e39d993a0e5f96a489c9e9eddcd0e0acf9a4a
test -z "$(git status --porcelain --ignore-submodules)"
test "$(git rev-parse refs/tags/v2.1.31-buffer6)" = 119e39d993a0e5f96a489c9e9eddcd0e0acf9a4a
free -m
available=$(free -m | awk '/^Mem:/ {print $7}')
deadline=$((SECONDS+600))
while test "$available" -lt 4000 && test "$SECONDS" -lt "$deadline"; do
    sleep 60
    free -m
    available=$(free -m | awk '/^Mem:/ {print $7}')
done
./mill --no-server --jobs 2 show '{cache.jvm[2.13.16],core.jvm[2.13.16],util.jvm[2.13.16],paths,cache-util,coursier.jvm[2.13.16],proxy-setup}.{jar,pom,sourceJar}'
