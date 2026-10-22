# GitHub CLI PR description update

During I419i the installed `gh pr edit https://github.com/CodexCoder21Organization/coursier/pull/6 --body-file pr-description.md` failed with `GraphQL: Projects (classic) is being deprecated in favor of the new Projects experience` at `repository.pullRequest.projectCards`. The PR update did not occur through that command. The REST fallback `gh api repos/CodexCoder21Organization/coursier/pulls/6 -X PATCH -F body=@pr-description.md --jq .html_url` updates the exact reviewed body without accessing project cards. A current gh binary should be used. No report-challenge CLI was run because its automatic enqueue/merge conflicts with the lane's explicit rules.

# Shared test-slot ordering

The mandated mkdir/sleep60 shared-slot acquisition provides no queue position. While I419i waited after its corrected consumer passed, owners changed FKGEN3 -> F175e-175 -> F177i clock-full before I419i obtained the slot. The unchanged three-hour deadline is retained and no other lane's lock is modified. A queued slot allocator would provide predictable admission while preserving the machine-wide memory budget. This scheduling observation is kept in lane evidence; the report CLI is not run because it automatically enqueues/merges.
