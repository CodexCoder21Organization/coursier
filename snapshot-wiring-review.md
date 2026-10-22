# Local fixed coursier-cache wiring review

## Scope and state

Reviewed the clean kompile-core checkout at `dc3a851aa` in `/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/I419i-kompile-core-20261020`. The only write for this task is this report. I did not edit project files or run a build or test.

The repo README was read in full. I also traced the root build dependency list, the test preparation and child-launch path, workspace Maven-coordinate substitution, and the test Maven-repository fixture. I inspected the pinned `/home/leslie/.cache/coursier/v1/https/kotlin.directory/kompile/build-kotlin-jvm/0.0.33/build-kotlin-jvm-0.0.33.jar` API with `javap`.

## Finding

An `@MavenArtifactCoordinates` rule in the root `build.kts` is a sound seam for unchanged test scripts that declare `@file:WithArtifact("io.get-coursier:coursier-cache_2.13:2.1.31-buffer3")`. `LocalBuildWorkspace.loadMavenCoordinateMapping()` records parameterless build-rule annotations from all workspace `build.kts` files (`src/Workspace.kt:1348-1403`). The Maven-coordinate path then invokes that local rule, records its Maven-layout directory, and resolves its POM closure through `bldbinary://repository` (`src/Workspace.kt:3750-3815, 3906-3918, 3959-3987`). During test classpath assembly, that directory is entered in `localDirs` by group and artifact, and the unified Coursier graph uses it as the workspace artifact (`src/Workspace.kt:6173-6203, 4146-4204`). It is therefore not expected to add both the remote and local `coursier-cache` jar to that one mediated graph. The forked test JVM receives this resolved classpath directly (`src/Workspace.kt:7031-7044`, passed to `launcher.executeSync` at `5095-5097`). A proper Maven-layout output with the original POM is required; a jar-only output falls back to the jar and omits POM transitives (`src/Workspace.kt:3928-3957`).

That annotation alone does **not** wire every context:

1. The root build's `dependencies` is created by `resolveDependencies2(...)` at `build.kts:631-647`; this build-kotlin resolver does not consult `LocalBuildWorkspace`'s Maven-coordinate mapping. `buildMaven136DefinitionSiteScopeTests()` compiles and publishes its manager using `dependencies` (`build.kts:1234-1246`), and `buildFatJar()` merges the same dependency jars (`1387-1389`). The outer manager/compiler classpath needs its own local-file selection.
2. `buildTestMavenRepositoryRawLockFixtures()` directly runs `coursierapi.Fetch` against only kotlin.directory and Maven Central for the graph containing this exact cache coordinate (`build.kts:1113-1145`). It then reads from `coursierapi.Cache.create().location`, copies cached repository bodies into a new local-only HTTP Maven fixture, and packages those bytes (`1147-1208`). Root workspace substitution does not affect that direct fetch or its fixture bytes. Any nested workspace that consumes this generated fixture can therefore still resolve the upstream jar.
3. The separate `test-support/input-fixture-build/build.kts` has two more direct-fetch paths: `resolveLegacyCompilerRuntime()` hard-codes the cache coordinate and public repositories (`:15-95`), while `currentDependencies` reads the manager POM and refetches its listed artifacts from those repositories without transitives (`:97-117`). These paths also bypass root workspace substitution. They matter for any of the selected tests that use prepared input fixtures.

## Minimal wiring recommendation

Stage the unpublished fixed jar and the original coordinate POM in an isolated scratch Maven-layout directory, for example `.../io/get-coursier/coursier-cache_2.13/2.1.31-buffer3/`. Keep the ordinary dependencies and their repository bytes untouched. Do not copy over a shared Coursier cache.

Use that staged Maven artifact in three places:

- Add one parameterless root build rule annotated `@MavenArtifactCoordinates("io.get-coursier:coursier-cache_2.13:2.1.31-buffer3")` that returns the staged Maven-layout artifact directory. That wires the unchanged root test-script `@WithArtifact` declarations and the `testClasspath` passed to all launched test JVMs. Ensure the annotation has no duplicate coordinate elsewhere in the workspace and the tests do not request `doNotUpgrade=true`; either condition would prevent or reject the substitution.
- For the root build's `resolveDependencies2` list, use the API's per-coordinate repository argument for the cache dependency: `MavenPrebuilt2("io.get-coursier:coursier-cache_2.13:2.1.31-buffer3", repositories = listOf(stagedRepo.toURI().toURL(), ...the existing repositories...))`. `javap` confirms `MavenPrebuilt2(String, boolean, List<URL>)`, defaults, and `resolveDependencies2(List<MavenPrebuilt>, List<URL>)` exist in pinned build-kotlin-jvm 0.0.33. Alternatively, map-replace just the resolved cache jar in `dependencies`, but this needs extra care at every place that reconstructs the classpath or POM.
- Make the root test Maven-repository builder serve the staged jar and original POM at the exact Maven paths before packaging its `kompile-test-maven/` entries. Give the fixture builder the staged path through an existing build input or an explicit build-rule dependency; its current explicit remote fetch cannot discover the root annotation mapping. If prepared-fixture scripts are in the selected gate, thread this same staged repository into both direct-fetch paths in `test-support/input-fixture-build/build.kts` as well.

This preserves all 15 test source hashes: the wiring belongs in build rules and fixture production, not test scripts. A local Maven repository override by itself is insufficient for root `@WithArtifact` resolution because repository URLs come from each script's `@WithRepository` declarations or the default URL list (`src/Workspace.kt:5888-5897`), while the root `build.kts` direct fetches hard-code their own repository lists.

## Byte verification for a later run

Record SHA-256 of the staged fixed jar and of its `coursier/cache/Downloader.class` entry before the gate. After resolving `dependencies`, assert there is one classpath entry for the cache artifact and its jar hash matches the staged jar. In each launched test JVM, the classpath is assembled from the resolved `cpFiles` (`src/Workspace.kt:7031-7044`); use a read-only child class-load trace such as `-Xlog:class+load=info` supplied through `JAVA_TOOL_OPTIONS`, then check that the loaded `coursier.cache.Downloader` source is the local fixed jar and compare its class-entry bytes. For fixture consumers, extract the generated `kompile-test-maven/io/get-coursier/coursier-cache_2.13/2.1.31-buffer3/` jar and POM and compare them byte-for-byte with the staged pair. Check the child trace and fixture copy separately: the local root rule does not flow into isolated nested workspaces on its own.

## Evidence read

- `README.md` (entire file; repository build, dependency and child-process conventions).
- `build.kts:631-647, 1111-1208, 1234-1246, 1387-1389, 1404-1434, 1458-1480` (dependency list, local fixture fetch and packaging, manager/fat-jar builds, runtime fixture assembly).
- `src/Workspace.kt:1348-1430, 3750-3815, 3906-3918, 3928-3987, 4112-4204, 5888-5897, 6173-6203, 6250-6263, 7031-7044` (`@MavenArtifactCoordinates` mapping, substitution, unified test graph and forked classpath).
- `src/WorkspaceCoursierCache.kt:24-105` and `src/Workspace.kt:1094` (workspace-specific cache directory; distinct from `Cache.create().location` in fixture builders).
- `test-support/input-fixture-build/build.kts:15-117` (the prepared-fixture direct Coursier fetch paths).
- Pinned `build-kotlin-jvm-0.0.33.jar` signatures above. The API supports supplying a `List<URL>` repository list per `MavenPrebuilt2` coordinate; no build was run to validate a proposed edit.

## Progress closeout

- Read README and confirm gate state: done.
- Trace outer dependencies and test child paths: done.
- Inspect pinned build-kotlin API signatures without building: done.
- Recommend local artifact wiring and byte checks: done.

## Follow-up review of the measurement wiring (2026-10-22)

Reviewed the current uncommitted core diff in the same checkout, still based at `dc3a851aa`. It changes only `build.kts`, `src/Workspace.kt`, `src/WorkspaceCoordinateResolver.kt`, and `test-support/input-fixture-build/build.kts`. I did not build, run tests, or edit source.

This follow-up supersedes the earlier “Minimal wiring recommendation”: the current measurement diff uses the explicit scratch repository across the selected resolver and fixture paths, plus an outer `dependencies` replacement/assertion. It does not add an `@MavenArtifactCoordinates` rule.

### Context coverage

The wiring covers the selected 15 test contexts identified in `gate-selector-binding.json`:

- Each selected source hash matches the manifest exactly. The current core diff contains no test source path.
- All 15 top-level test scripts use default repositories. Ten selected tests consume `buildTestMavenRepository()`, the selected prepared-input test path runs through the input-fixture builder, and `testAttemptTimelineOlderHookLibrariesRemainUsable` directly declares the cache artifact with no repository override. The added default Maven repo and the fixture-builder wiring therefore cover the selected direct and fixture-backed lookups.
- The outer `dependencies` graph now gives the cache coordinate the local repository explicitly and maps the resolved artifact directory to `/tmp/I419i-runtime/snapshot-artifact`. The follow-up check requires exactly one matching cache jar and byte-compares it to `cache-snapshot.jar` before the manager/fat-jar rules consume it.
- The four root fixture builders that perform direct Coursier fetches now put the local repo first and put its root directory first in the subsequent source search. `buildTestMavenRepositoryRawLockFixtures` includes the cache coordinate explicitly; the attributed compiler, publication, and recorder fixture builders also resolve graphs that include it.
- `Workspace.kt` has four updated default-repository fallbacks and `WorkspaceCoordinateResolver.kt` has the fifth. The two direct-fetch paths in `test-support/input-fixture-build/build.kts` also put the local repo first.

The explicit root test-Maven fixture is the route for selected consumers that declare a fixture repository. The test sources themselves remain byte-for-byte unchanged; the selected-hash check reports 15/15 matches. The relevant difference from the baseline is only dependency sourcing in the measurement harness: selected resolution can now use the unpublished fixed artifact.

### Artifact byte evidence and limits

The isolated Maven tree contains only the cache coordinate's jar, original POM, and MD5/SHA-1 sidecars. The jar hash is `f685b29c350dcc1ab07cc3b0b0062fe176c76c4b1ebf18bdcfb30260ac49f3a2`; the POM hash is `8b8bb6bbf811b99fdc35bf91d4f66359d864475063bc1e3c9ae068f29fd538ab`. Both match the flattened `snapshot-artifact` files, and all four checksum sidecars match their payloads.

The existing `/tmp/I419i-runtime/manager.jar` contains `coursier/cache/internal/Downloader.class` with SHA-256 `844d69344b48e818d3d2e81b57ed6e8318b914b48114730f5929935955b3766e`, matching that class entry in the staged fixed jar. The staged, flattened, and `cache-snapshot.jar` files also have identical whole-jar hashes. This verifies the class bytes in the existing manager container; it is not a runtime class-load trace from the next gate run. No emitted `kompile-test-maven/.../coursier-cache...jar` fixture exists in the checkout yet, so its packaged bytes still need to be checked from the next run's output.

### Remaining handling

Every `/tmp/I419i-runtime/...` reference is a measurement-only absolute path. It is coherent for this isolated run, but must be removed from any product or PR diff. After the next build, verify the emitted test-Maven fixture's jar and POM against the staged files and retain the one-match classpath assertion's result. I did not find another uncovered selected context.

The separate Coursier source follow-up also confirms the earlier P3 point is fixed at commit `2bf531d14`: the implementation uses `equalsIgnoreCase("identity")`, and the test includes mixed-case `IdEnTiTy`. I did not rerun that branch; the final test results remain parent-supplied evidence.
