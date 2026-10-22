# Downstream snapshot measurement plan

The gate source remains the fifteen exact test files from core dc3a851aa (SHA-256 inventory checked against prior preflight). Preserve 30 iterations, 4 workers, CPU 0,1 and maxTestAttempts=1. Keep all original per-test budgets and assertions. CI is orchestrator-owned.

The only measurement source edits prepend file:///tmp/I419i-runtime/maven/ to default Maven repository lists and the direct fixture fetches, and use that directory first when packaging isolated HTTP Maven fixtures. The directory contains only the fixed coursier-cache_2.13:2.1.31-buffer3 jar and its original POM/checksums. The root build dependency list also substitutes the exact local snapshot directory and asserts its bytes. No shared Coursier cache or Maven installation is edited. This is explicit measurement configuration, not a proposed core behavior change.

1. Finish the reviewed case-insensitive identity correction and 13-case 30/30 register; rebuild the final cache jar.
2. Acquire the machine-wide slot before the core build and keep it through the 4-worker gate; the host reports ~9 GiB current usage in the shared10GiB cgroup while another lane owns the slot.
3. Rebuild core outer manager locally from the snapshot-configured source. Run consumer buffer test (with loaded-class SHA output) once to verify the exact fixed classes arrive in a test child, and inspect the fixture Maven jars for the fixed cache bytes.
4. Compile the same ControlAttempts.java launcher, run its 30-iteration gate with taskset0,1 and maxTestAttempts=1, audit every XML/attempt history and completion marker.
5. For any gate failure, preserve and classify the precise stack/evidence. Do not count fewer failures as proof of a historical cause; the local artifact route and cache starting state are recorded differences.

Never add Java agents, reflection, or JVM tuning flags. Do not run two build/test commands at once. No publish, merge, enqueue, re-request, remote run or handoff command.
