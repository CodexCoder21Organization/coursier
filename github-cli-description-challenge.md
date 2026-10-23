# GitHub CLI PR edit stops on a retired Projects field

Attempted to update the reviewed description of https://github.com/CodexCoder21Organization/coursier/pull/7 with `gh pr edit --body-file`. The command exited 1 with `GraphQL: Projects (classic) is being deprecated in favor of the new Projects experience, see: https://github.blog/changelog/2024-05-23-sunset-notice-projects-classic/. (repository.pullRequest.projectCards)`.

The description was still from the previous round despite the earlier completion note. Used the REST pull-request PATCH endpoint with a JSON body containing the reviewed draft, then checked the returned body for byte-for-byte equality and confirmed the PR remained open at ac74fa03ab018e66a67f293dbae7a4fe3a7bd367. That update succeeded.

Suggested lasting fix: update the installed GitHub CLI to a version that no longer queries the retired field; scripts should check their exit codes and verify the returned description. The report-challenge skill was read, but its normal CLI enqueues and automatically merges a new PR, which the lane brief expressly prohibits. This evidence record keeps the finding without that action.
