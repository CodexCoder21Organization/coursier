# Retry-setting control

Source: https://github.com/CodexCoder21Organization/coursier/commit/5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3

Retained command:

```sh
LC_ALL=C.UTF-8 ./mill -i 'cache.jvm[2.13.16].test.testOnly' coursier.cache.FileCacheLocationOwnershipTests
```

Only the forked test JVM receives `-Dcoursier.exception-retry-backoff-initial-delay=1m`, using the same temporary CacheJvmBaseTests.forkArgs addition as the reviewer. The patch is recorded separately and restored before committing evidence. Product and test bytes remain exactly the reviewed source. A checkout-local cs link selects the available coursier launcher. The test runs under one acquired local build slot, with a 15-minute command deadline and immediate slot cleanup.

All six test FileCache constructors already provide `retry = 5`, `retryBackoffInitialDelay = 10.milliseconds` and `retryBackoffMultiplier = 2.0`, in addition to `credentials = Nil`.

`FileCache.download` is private and constructs Downloader positionally only through `clock`. Its `retry` argument occupies Downloader's deprecated `sslRetry` slot. Downloader's active `retryCount`, `retryBackoffInitialDelay` and `retryBackoffMultiplier` are omitted and select CacheDefaults. CacheDefaults reads the supported process retry properties. Downloader builds its private Retry from these active arguments. The same-file waiter then uses that Retry when it checks for its owner's publication.

Changing the six constructor inputs to the values they already have cannot alter this call. The required correction is in product FileCache.download: pass its retry and both backoff settings to the active Downloader arguments. The canonical-destination ownership change in CacheLocks and Downloader needs no alteration. That production forwarding change exceeds the assigned test-only edit scope and has not been made.

The fixture's ordinary reviewed 3/3 and the review's prior property result are already recorded at https://github.com/CodexCoder21Organization/coursier/tree/wip/revcoursier2-evidence/.review-evidence . This lane repeats only the assigned negative control; it does not run positive gates for an unmade change.
