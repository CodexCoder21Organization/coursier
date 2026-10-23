# PUBCOURS2 published coordinates

Source: https://github.com/CodexCoder21Organization/coursier/commit/3788263afb4b8977cfd5ebc2a557de566e0c801c

Merged checks: https://github.com/CodexCoder21Organization/coursier/pull/7

All seven modules use `2.1.31-buffer5`. Each binary JAR, POM and source JAR was downloaded and SHA-256 verified against the exact local build (21/21 matches). See [release-verification.json](release-verification.json) for all file hashes and immediate absence checks.

| Coordinate | Binary JAR SHA-256 |
| --- | --- |
| `io.get-coursier:cache-util:2.1.31-buffer5` | `1092b1b287a0077fb505dad41555c47a1016a4c8eb0f0a50c601a719f51b931b` |
| `io.get-coursier:coursier-paths:2.1.31-buffer5` | `6ca3ed0756e19ad72e14dcdebe6b5a7706d20cf524115ea535b59e844061e776` |
| `io.get-coursier:coursier-util_2.13:2.1.31-buffer5` | `9bc8e42c08a5a313dfbb8a676996927840cce7645b88a781518080a4686567c9` |
| `io.get-coursier:coursier-core_2.13:2.1.31-buffer5` | `c0a2d1e2a4a37f38477856ea683c046de3357b203e876bcf2824f3f90547c5b6` |
| `io.get-coursier:coursier-cache_2.13:2.1.31-buffer5` | `cdad657fecc7bea36dd1303a4ba8167cef28b8873758802a65a3c2772b20ed5c` |
| `io.get-coursier:coursier-proxy-setup:2.1.31-buffer5` | `9fd14c4ba99b2a3a2fdb6adc58e6e6c29212c9dd79a06bbbd5f9255f47b9ff62` |
| `io.get-coursier:coursier_2.13:2.1.31-buffer5` | `77192fb41b8c9db1118bf1baa9aba64384a676bcd4620843caaae048464ffcee` |

The downloaded cache JAR matches all 179 raw compiled class files. Four existing classes (Downloader, its companion, CacheUrl, its companion) have different hashes from buffer4. ResponseInputStream and DecodingInputStream are present in buffer5 and absent from buffer4. Both packaged changed Scala sources equal the git bytes from the requested source commit; the new response class contains the incomplete, overlong and invalid-framing messages. See [class-proof.json](class-proof.json).

Only this seven-module release was published. No consumer, test, PR, release tag on GitHub, main branch, deployment or merge queue was changed. No additional publication is needed.
