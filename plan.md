# I419i plan
- [in progress] Fresh clone, required reading, rebase and main overlap check; compile Range reproducer.
- [pending] Run unchanged reproducer ten single attempts; checkpoint results remotely.
- [pending] Complete boundary cases, implement response-size buffer cap, run each thirty times.
- [pending] Run existing cache JVM tests exactly once; review tests and implementation independently.
- [pending] Build local snapshot and run the same fifteen-owner, thirty-iteration single-attempt kompile gate; inspect any remaining failure mechanisms.
- [pending] Rebase, validate, open fork PR, scan and push evidence, verify remote heads and process cleanup.

# Invariants
| Resource | Creator / owner / closer | Allowed observations / terminal state | Public API check |
|---|---|---|---|
| Response stream | URL handler / Downloader / Downloader finally | exact downloaded bytes; closed before return, also with resume | instrumented real loopback HTTP stream via FileCache custom handler |
| Buffer | Downloader / one download / GC after stream closes | known uncompressed response uses min(default, max(1, remaining length)); unknown or encoded uses existing size | read array size and requested length at URL handler boundary |
| Partial file | prior download / FileCache / successful atomic move | prefix retained once, Range follows prefix, logger reports full length | create persisted prefix and call FileCache.file |
| Server, executors, cache directory | test / test / finally | no open streams or workers after scenario | close counts, executor termination, directory cleanup |
