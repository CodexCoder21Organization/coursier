# Local launcher setup

The recovered FCOURS5 direct Mill launcher assumes the workspace output directory exists. A fresh clone has no out/ directory. The first invocation exited before compilation or any utest events with java.io.FileNotFoundException for out/mill-lock (No such file or directory). Full log is bootstrap-failure.log. Creating the normal untracked out/ directory before invoking the unchanged Mill worker resolves the missing directory; the evidence launcher should create it itself for future fresh clones. No test attempt occurred and no tuning flag or product source changed.

A detached shell child was also ended when its tool command returned before waiting. No launcher log or test event existed. The subsequent tool shell waits on its recorded child PID and remains attached as a tool session.

The report-challenge skill normally sends a record through an automatically merged upstream change. This job expressly forbids merging and publishing; record retained here instead, without invoking that workflow.
