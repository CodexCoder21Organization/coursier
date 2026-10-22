# I419i plan
- [done] Fresh clone, required reading, rebase and main overlap check; compile Range reproducer.
- [done] Run unchanged reproducer ten single attempts; checkpoint results remotely.
- [done] Complete boundary cases, implement response-size buffer cap, run each thirty times.
- [done] Run existing cache JVM tests exactly once; review tests and implementation independently.
- [done] Build and bind the unpublished local snapshot; complete the unchanged 450-case gate at446/4,450first attempts,zero retries; inspect remaining mechanisms with independent reviews.
- [evidence limit] Original JoinedWorkload Future exceptions were not retained and cannot be recovered after those processes exited. A matching receiver-expiry ordering is concretely reproduced; attribution to the natural gate failures remains an inference, not a claimed verified cause.
- [done] Fork PR open/unmerged; all local gates and evidence complete, remote heads verified, PR description prepared with measured counts and limitations, owned process cleanup empty. Final evidence push and body update are the remaining administrative checkpoint.

# Invariants
| Resource | Creator / owner / closer | Allowed observations / terminal state | Public API check |
|---|---|---|---|
| Response stream | URL handler / Downloader / Downloader finally | exact downloaded bytes; closed before return, also with resume | instrumented real loopback HTTP stream via FileCache custom handler |
| Buffer | Downloader / one download / GC after stream closes | known uncompressed response uses min(default, max(1, remaining length)); unknown or encoded uses existing size | read array size and requested length at URL handler boundary |
| Partial file | prior download / FileCache / successful atomic move | prefix retained once, Range follows prefix, logger reports full length | create persisted prefix and call FileCache.file |
| Server, executors, cache directory | test / test / finally | no open streams or workers after scenario | close counts, executor termination, directory cleanup |
