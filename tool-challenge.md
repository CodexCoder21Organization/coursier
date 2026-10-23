# Local launcher setup

The recovered FCOURS5 direct Mill launcher assumes the workspace output directory exists. A fresh clone has no out/ directory. The first invocation exited before compilation or any utest events with java.io.FileNotFoundException for out/mill-lock (No such file or directory). Full log is bootstrap-failure.log. Creating the normal untracked out/ directory before invoking the unchanged Mill worker resolves the missing directory; the evidence launcher should create it itself for future fresh clones. No test attempt occurred and no tuning flag or product source changed.

A detached shell child was also ended when its tool command returned before waiting. No launcher log or test event existed. The subsequent tool shell waits on its recorded child PID and remains attached as a tool session.

The report-challenge skill normally sends a record through an automatically merged upstream change. This job expressly forbids merging and publishing; record retained here instead, without invoking that workflow.

The recovered process recorder also stopped traversing a parent after it was first recorded. A later test child could therefore be missed by the numeric PID audit. The first head gate reported max observed Java count 1; that number is an observation from an incomplete recorder, not the total concurrency bound. The direct launcher structurally uses one Mill JVM and one sequential test JVM. Corrected the evidence-only recorder to traverse all current descendants on every sample, retaining PID/start-time ownership checks; saved the original runner as run-command-before-tracker-fix.py. XML and unique qualified events independently establish the original test attempts; no gate is re-run because of the recorder correction.
