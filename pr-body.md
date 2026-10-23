The user requested “fix four download-integrity defects that coursier main already has, in a new PR”. kompile uses this fork to fill its shared artifact cache, so a download accepted while incomplete or wrongly decoded is reused by every later build that reads it. This change is part of the kompile shared-cache campaign and follows the four defects found during reviews of https://github.com/CodexCoder21Organization/coursier/pull/7; those defects were deliberately left out of that PR.

The original evidence is recorded in [RCOURS3 findings](https://github.com/CodexCoder21Organization/coursier/blob/wip/RCOURS3-evidence-20261023/RCOURS3-findings.md) and [RCOURS4 findings](https://github.com/CodexCoder21Organization/coursier/blob/wip/RCOURS4-evidence-20261023/RCOURS4-findings.md). Current main includes the earlier framing changes at https://github.com/CodexCoder21Organization/coursier/commit/3788263afb4b8977cfd5ebc2a557de566e0c801c; the six new reproductions of these four groups all fail on that main commit in one attempt each.

| Defect | Change | Regression in `DownloadEncodingIntegrityTests` |
|---|---|---|
| Repeated Content-Encoding fields use only the last field | Combine indexed fields in wire order, validate the full list, then decode in reverse order | `repeated encoding fields decode every layer in field order`; `a later identity field cannot hide an unsupported encoding` |
| An incomplete later gzip member publishes earlier bytes | Stream every member and require its complete header, data, checksum and size | `a truncated later gzip member cannot publish earlier members` |
| Progress within a gzip member waits for the next fragment | Return available decoded bytes directly to the copy/progress loop | `gzip reports decoded progress before the next compressed fragment` |
| Inner EOF skips outer gzip integrity checks | Require deflate EOF to reach verified EOF in its source, completing every outer decoder | `inner deflate EOF cannot hide a corrupt outer gzip trailer`; `inner deflate EOF cannot hide an incomplete outer gzip trailer` |

The same completion check rejects extra deflate bytes and the JDK inflater's dictionary-required state, which also reports EOF before a complete body exists. A separate test was confirmed failing before adding the dictionary-state guard. The gzip reader uses bounded buffers and releases its inflater and underlying stream on close. All rejection paths retain the existing temporary-file removal, and error assertions compare complete messages including URL and expected/received details.

Additional cases cover mixed repeated fields and blanks, encoded resume rejection, every incomplete later-member prefix, empty members, optional headers and their checksum, corrupt member CRC/size, extra outer output, trailing gzip data, three-layer decoding and one-byte buffers. Tests use real loopback HTTP servers, isolated caches and a latch-controlled progress ordering. README describes the public completion and progress behavior.

Validation: PENDING FINAL GATES.

Dedicated test-comprehensiveness and code-review passes are recorded in [FINHCOURS findings and test evidence](https://github.com/CodexCoder21Organization/coursier/blob/wip/FINHCOURS-evidence-20261023/FINHCOURS-findings.md).

Existing tests on main: **no edits**. Within the new suite, the added resume fixture initially used a range length of 3 with a longer compressed Content-Length; its range was corrected to describe the compressed byte count so it reaches the encoding rejection being asserted. Its full error and cleanup assertions were retained. No timeout increases, iteration reductions, skipped tests, reflection, mocks, JVM tuning or remote runs were added. No merge, enqueue, deployment or artifact publication was performed.
