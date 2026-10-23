# Published coursier buffer6 release

The requested release makes the content-encoding checks merged in https://github.com/CodexCoder21Organization/coursier/pull/8 available to consumers. It builds unchanged source https://github.com/CodexCoder21Organization/coursier/commit/119e39d993a0e5f96a489c9e9eddcd0e0acf9a4a and changes no consumer.

All seven coordinates use `2.1.31-buffer6`. All 21 binary JAR, source JAR and POM files were downloaded and match their local SHA-256 values. `release-verification.json` has every file URL and both hashes; `local-artifacts.json` identifies the exact Mill packaging outputs.

| Coordinate | Binary JAR SHA-256 |
| --- | --- |
| [`io.get-coursier:cache-util:2.1.31-buffer6`](https://kotlin.directory/io/get-coursier/cache-util/2.1.31-buffer6/) | `e11621d4b9fa341c0c9b90ae8b8b4dbbc338420e0bb5452d263429f9c945737a` |
| [`io.get-coursier:coursier-paths:2.1.31-buffer6`](https://kotlin.directory/io/get-coursier/coursier-paths/2.1.31-buffer6/) | `efbbbf1e5b783378ae57646cf7399ecc9302ba16c135730892a84ec8420dd359` |
| [`io.get-coursier:coursier-util_2.13:2.1.31-buffer6`](https://kotlin.directory/io/get-coursier/coursier-util_2.13/2.1.31-buffer6/) | `df9798ba6975d4ba9fda7f788c9bad4e53c3ee4c68cd1e77a88571bcc8107d7f` |
| [`io.get-coursier:coursier-core_2.13:2.1.31-buffer6`](https://kotlin.directory/io/get-coursier/coursier-core_2.13/2.1.31-buffer6/) | `8c65eccc3bde694c69bcccfb2f301b783145b7c12463d7757f1a0c35c43065fd` |
| [`io.get-coursier:coursier-cache_2.13:2.1.31-buffer6`](https://kotlin.directory/io/get-coursier/coursier-cache_2.13/2.1.31-buffer6/) | `e581abf2e27f34f8b53c4da6ab7a54b95664d78844173368c44f475e6b2ba1e8` |
| [`io.get-coursier:coursier-proxy-setup:2.1.31-buffer6`](https://kotlin.directory/io/get-coursier/coursier-proxy-setup/2.1.31-buffer6/) | `cbbd875b6e5bd83060696d28883279153729d75ce104e68c74e719ecd16ec65c` |
| [`io.get-coursier:coursier_2.13:2.1.31-buffer6`](https://kotlin.directory/io/get-coursier/coursier_2.13/2.1.31-buffer6/) | `f4d41713029dc500dabe9da5b1ac0417464d49be86e0a52ac7431c5625cde5ca` |

`class-proof.json` compares all 181 published cache class files to their staged JAR and raw compiler output. Eight existing classes differ from buffer5 and two are new: `GzipInputStream` and `Downloader$DeflateInputStream`. The generated `Downloader$Blocking$` caller references both new decoder implementations. Header/trailer checksum, member boundary and deflate end checks appear in the saved bytecode listing; packaged Downloader.scala and GzipInputStream.scala exactly match the requested git source. The gzip reader returns decoded fragments before reading a trailer or waiting for another compressed fragment, as shown by source identity and the bytecode read method; no full decoded response buffer is introduced.

`independent-audit.json` confirms seven sole publisher invocations, 28 immediate HTTP 404 checks, seven metadata absence checks, all 21 file hash matches, and 181 compiled/staged/downloaded cache class matches. Each upload returned exit 0 on its first invocation, with no retries. One initial class-proof script invocation failed because its expected generated caller class name was incorrect; the corrected proof passed before publication and passed again against downloaded bytes. This correction changed evidence code only. No tests were run or edited because this release brief authorizes no targeted test gate. No new PR, merge, check request, deployment, consumer edit or other publication was performed.
