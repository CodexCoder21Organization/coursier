# Attempt audit

The fork pins utest0.9.1 and CsTests selects utest.runner.Framework. These suites extend plain TestSuite; none mixes in TestSuite.Retries or overrides utestWrap. In that version the default Executor.utestWrap returns runBody once; retry execution is only added by the explicit Retries trait. The exact default Executor source is saved beside this note.

Sources: https://github.com/com-lihaoyi/utest/blob/9259cd8893d957797f669380dec776e4dfcb978c/utest/src/utest/framework/Executor.scala and https://github.com/com-lihaoyi/utest/blob/9259cd8893d957797f669380dec776e4dfcb978c/utest/src/utest/TestSuite.scala.

The saved XML and console logs are independently audited for one unique testcase and one execution-status line per case per invocation. No skipped cases or retry mixins are present. The initial withRetryCount compile error executed zero cases. No kompile runner, remote run, full suite or CI retry was used.
