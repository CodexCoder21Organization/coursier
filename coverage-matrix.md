# FCOURS4 coverage matrix

All names below refer to DownloadResponseFramingTests and use real loopback HTTP with isolated FileCache directories and retry count zero. Before XML is against original 7ab7bfff production; after results refer to the final-head gates.

| RCOURS2 group | Regression scenarios / boundary | Proof |
|---|---|---|
| 1. Range-unit case | valid mixed case range unit completes the artifact | A1 fails original code; final 30-round gate. |
| 2. Range and chunk ending | chunked range must consume the closing chunk before publishing; chunked range rejects data beyond its stated range; range data stays temporary until the closing chunk arrives | A2/A3 fail original code; final 30-round gate. |
| 3. No-body and HEAD | unsolicited 304 cannot publish an empty artifact; no body status with a saved prefix is rejected and cleaned; HEAD metadata length does not require response bytes | A4 fails original code; HEAD is a positive contract already satisfied there; final 30-round gate. |
| 4. Encodings and names | short other encoded response cannot publish partial bytes; correct mixed case gzip decodes the body; mixed case deflate; unknown/empty encodings; stacked codings; encoded stream endings and checksums | A6/A7 fail original code; valid compatibility cases can already pass original code; final 30-round gate. |
| 5. Real stream-error cleanup | cut off chunked response removes temporary bytes; cut off gzip response removes temporary bytes; invalid header/trailer and checksum cleanup | Both A5 paths fail original code; final 30-round gate. |
| 6. Same-file ownership | concurrent callers never publish the same short artifact; concurrent callers share one correct artifact download | Public watching-owner callback orders the genuine callers. These positive inherited length-integrity contracts pass original PR head; final 30-round gate. |

Further compatibility protections cover body-bearing successful and unfollowed redirect statuses, complete existing HTTP errors, progress between unknown-length body fragments, and generic file-output errors under a decoder. These found issues in the first implementation and have saved failing and passing targeted runs. The complete cache module also exercises unchanged redirection, ownership, file-lock and URL behavior.
