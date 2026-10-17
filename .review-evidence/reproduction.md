# Review reproduction notes

Reviewed source: 5470aee6fd7b4dca3838ca92687f3ed2dd5a3da3.

Ordinary exact-head command:

```sh
LC_ALL=C.UTF-8 ./mill -i 'cache.jvm[2.13.16].test.testOnly' coursier.cache.FileCacheLocationOwnershipTests
```

The checkout-local cs link points to /usr/local/bin/coursier, which supplies the executable name expected by the build. The ordinary command's native XML is in local/test-report.xml.

The supplemental process-setting experiment uses the same product and test source, with only CacheJvmBaseTests.forkArgs temporarily extended to pass this JVM property:

```text
-Dcoursier.exception-retry-backoff-initial-delay=1m
```

That temporary invocation configuration is recorded in ambient-fork-config.patch and restored after the run. It changes no test assertion or operation bound. Native XML and complete output are retained separately from the ordinary run.

FileCache.download passes arguments through clock into Downloader but omits its active retryCount and backoff arguments; the FileCache retry value instead occupies deprecated sslRetry. The omitted backoff default reads CacheDefaults.retryBackoffInitialDelay, which reads the process property. The explicit fixture value therefore does not control the actual waiter delay.

The gate artifacts come from https://github.com/CodexCoder21Organization/coursier/actions/runs/38996343860 and the unrelated-config baseline from https://github.com/CodexCoder21Organization/coursier/actions/runs/38995746243. native-audit.json records native counts, first attempts, source proofs and file-hash comparisons. gate-workflow.yml is the actual exact-source workflow used for the retained gate.
