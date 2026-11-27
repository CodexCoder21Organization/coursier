# CCLI findings

OBSERVED — Started 2026-11-27 05:10 UTC. Deadline 06:10 UTC (60-minute lane time box). No merging, publishing, deployments, handoff writes, status loops or delegated runs.
OBSERVED — Read CPUB-findings.md in full. It records clean main 4d5a7d3d0176fbadb65f86a80ab958a37c995a6f failing cli.compile, 46 release modules, cache 215/215, and an unresolved published-source commit gap.
OBSERVED — Plan:
1. IN PROGRESS: confirm authorized CPUB checkout clean, read project and reference documents, reproduce compile for all 46 released modules, checkpoint logs.
2. PENDING: trace fork/upstream customHandlerFactory history and published 2.1.30 sources; state mechanism with evidence.
3. PENDING: add public fetch-path regression coverage and smallest correct fix; show baseline failure and fixed pass.
4. PENDING: compile all released modules, run affected CLI tests and cache suite once; review full touched files against reference documents.
5. PENDING: fetch/rebase, commit/push fix, open one PR with cause and results; push final evidence and stop without waiting for checks.

OBSERVED — 05:24 UTC: authorized reuse of CPUB/workspace/coursier after `git status --porcelain=v1` was empty; fetched/rebased main remains 4d5a7d3d0176fbadb65f86a80ab958a37c995a6f. README and DEVELOP read; no root AGENTS.md exists. Mill reports 0.12.15. Fork main rules endpoint returns []. rg is unavailable; used grep/find instead.
OBSERVED — The mechanism is a reference to an out-of-scope parameter in CLI run(), and the evidence that shows it is the full diff of https://github.com/CodexCoder21Organization/coursier/commit/6628043a159dbc89a28a5d3f8604bfb3dce275bf: it introduces the parameter only to task() but appends its use to run()'s channel cache as well. No definition was removed or renamed. `git log --all -S customHandlerFactory -- modules/cli/src/main/scala/coursier/cli/fetch/Fetch.scala` identifies only that commit.
OBSERVED — The introducing commit has one parent, upstream commit https://github.com/coursier/coursier/commit/ef960b85826c681f0b698f6b865ffd9c8e27198a. Freshly fetched upstream/main's same-symbol history is empty; its corresponding file has no parameter or handler forwarding. The fork's change is its own addition, not a partial upstream pick. It entered main via https://github.com/CodexCoder21Organization/coursier/commit/b97ac6a1595b5439be28539d47cbd1f00f2e88b1.
INFERRED — Smallest correct repair is to restore run()'s channel-cache construction to upstream, retaining task()'s real optional factory and forwarding. run() overrides the command interface and has no custom factory input or CLI option; inventing a value there adds no functionality. A public task() regression will ensure the intended programmatic custom artifact handler still works.
OBSERVED — Downloaded published coursier-cli_3 2.1.30 source JAR: task() has the factory parameter but neither task() nor run() uses it. This source has no undefined symbol, unlike main. Exact release commit remains to be established; binary comparison in progress.
OBSERVED — Complete baseline compile launched with explicit 46 modules from artifactMetadata, `--no-server --disable-prompt --color false -j1 -k`, hard 1200-second cap, cs on PATH. cli.compile emits exactly E006 Fetch.scala:101:124 Not found: customHandlerFactory; keep-going continues compiling other released modules. Logs saved under CCLI/logs.
