# Buffer-only release provenance

The upstream pull request changes only the allocation in Downloader.readFullyTo. A direct build of current main also includes behavior changes that are absent from the deployed 2.1.30 artifact. The first candidate, 2.1.31-buffer1, was published from main and is superseded for downstream adoption.

The release baseline is the published source archive at https://kotlin.directory/io/get-coursier/coursier-cache_2.13/2.1.30/coursier-cache_2.13-2.1.30-sources.jar (SHA-256 23c695dc66da28c1ddc6318eb33dc5a614f21dcf5ae48de1aa5d644ce9b0a5de). Compare each module source with this archive, retaining only the Array.fill -> new Array allocation change. The adjacent patch applies that baseline to this branch without changing the main-source proposal. It preserves deployed null-handler handling, NotFound behavior, and FileCache source visibility.

Reproduction from a clean branch checkout:

```sh
git apply checkpoint/LH7-buffer-only-release.patch
taskset -c 0,1 ./mill --no-server 'cache.jvm[2.13.16].test'
taskset -c 0,1 ./mill --no-server 'cache.jvm[2.13.16].jar'
```

Package the resulting thin JAR with the published 2.1.30 POM, changing only its root version to 2.1.31-buffer2. Do not change dependency versions. Restore the four overlaid source files after packaging; the patch remains the durable release recipe. Full test results and final JAR hash will be appended after verification.

Verified release build:88/88 cache-module tests pass, wall214.839/user129.459/system17.578 seconds. The public CPU comparison is540/540ms (ratio1.0). Of176 class files,175 are byte-for-byte equal to deployed2.1.30; only coursier/cache/internal/Downloader$.class differs. JAR SHA25620307f9eabb1532b8630fcb0ade84c990fd11c175afebac7b0465c471a4d2406. Published source JAR SHA256ce092ffb004c8bcba40e24c792e1ddf05649bb385785f7a4f4bc7d4c57363fa9. POM SHA256aa6e3c4eedab931e74c22868ea28ec189145914f6968bf45e3f91b0da9965d62. Source overlay restored after packaging.
