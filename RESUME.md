# Final measurement state

The buffer fix is on fix/I419i-response-download-buffers at2bf531d14bbd6fff9a1fa3d4f069f0aa504525d8, in the open/unmerged fork PR https://github.com/CodexCoder21Organization/coursier/pull/6. Do not merge, publish, enqueue or use handoff-cli.

The final13-case source runs ten first attempts against unchanged main: eight size cases fail10/10 and five controls pass10/10. All13 final cases pass30/30 with the fix. Restoring source after the final backout also passes13/13 once, and all five compiled classes match the measured snapshot. The existing cache module suite passed104/104 once before the narrow mixed-case identity correction.

The original downstream gate is complete:446passed/4failed/450,450first attempts,zero retries. All30 iterations and source/artifact bindings are audited. RepeatedPublicBuildCopies failed in2and4; JoinedWorkload failed in2and16. Generated fixture archives and resolved buffer3 jars contain the exact unpublished snapshot. See gate-results and the independent reviews.

Separate JoinedWorkload diagnostics are not part of450: the ordinary single-worker copy passes; the forced late-connection copy reproduces receiver expiry, child reply-read wait and parent deadline. This does not conclusively recover the original gate's hidden Future exceptions. See remaining-mechanisms.md for that limit.

All tracked process ownership checks currently find no live owned processes, no owned zombies and no unrecorded lane processes. No acceptance or diagnostic process remains active. Build outputs are deliberately absent from Git. The complete core measurement patch is archived here; its local branch is not a downstream product proposal.
