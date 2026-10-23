# GitHub CLI PR description update reads a deprecated Projects field

While finishing the FCOURS5 A1 correction on https://github.com/CodexCoder21Organization/coursier/pull/7, `gh pr edit <URL> --body-file <file>` failed before updating the description.

Exact error: `GraphQL: Projects (classic) is being deprecated in favor of the new Projects experience, see: https://github.blog/changelog/2024-05-23-sunset-notice-projects-classic/. (repository.pullRequest.projectCards)`.

The product push had already succeeded; only the description update was affected. The fallback uses `gh api --method PATCH repos/CodexCoder21Organization/coursier/pulls/7 --input <JSON file>` with the same body, then verifies the returned complete body and head SHA. A durable fix is to update the installed GitHub CLI or make this edit path use the REST endpoint without querying classic project cards.

Read `/home/leslie/.codex/skills/report-challenge/SKILL.md`. Its normal command automatically enqueues and merges a PlanRepository change. This lane explicitly prohibits both actions, so that command was not run; this record is retained in the user-authorized evidence branch instead.
