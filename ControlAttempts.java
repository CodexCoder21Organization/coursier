import java.io.File;
import java.nio.file.Files;
import java.util.*;
import kompile.*;
import community.kotlin.clocks.simple.SystemClock;
import okio.FileSystem;
import okio.Path;
public final class ControlAttempts {
  public static void main(String[] args) throws Exception {
    if (args.length < 6) throw new IllegalArgumentException("Expected root cache output-directory repetitions parallelism test-file...");
    File root = new File(args[0]).getAbsoluteFile(), cache = new File(args[1]).getAbsoluteFile(), output = new File(args[2]).getAbsoluteFile();
    if (output.exists() || !output.mkdirs()) throw new IllegalStateException("A fresh result directory is required, but could not create " + output);
    int repetitions = Integer.parseInt(args[3]), parallelism = Integer.parseInt(args[4]);
    List<String> selectors = Arrays.asList(Arrays.copyOfRange(args, 5, args.length));
    if (repetitions < 1 || parallelism < 1) throw new IllegalArgumentException("Positive repetition and worker counts are required");
    long failures=0, tests=0;
    // Reuse dependency preparation; every execute still launches a fresh isolated test JVM.
    try (LocalBuildWorkspace workspace = new LocalBuildWorkspace(List.of(root, new File(System.getProperty("control.sourceRoot"))), cache)) {
      for (int iteration=1; iteration<=repetitions; iteration++) {
        ExecutionResults result = TestTimelineExecutionKt.executeTestsWithTimeline(workspace, selectors,
          Path.Companion.get(new File(output, "iteration-"+iteration+".jsonl").getAbsolutePath(), false), FileSystem.SYSTEM,
          new SystemClock(), parallelism, 1, 8192, new PreparedTestExecutionListener() {
            public void onChildProcessStarted(PreparedTestExecutionIdentity test,long pid) {}
            public void onTestReady(PreparedTestExecutionIdentity test) {}
          });
        org.json.JSONArray observations = new org.json.JSONArray();
        for (ExecutionResult test : result.getResults()) {
          StringBuilder console = new StringBuilder();
          test.getConsoleOutput().forEach(chunk -> console.append(chunk.getMessage()));
          org.json.JSONArray measurements = new org.json.JSONArray();
          console.toString().lines().filter(line -> line.startsWith("I419f ") ||
              line.startsWith("I419g runtime ") || line.startsWith("F361") || line.startsWith("F86d native-read-count="))
              .forEach(measurements::put);
          observations.put(new org.json.JSONObject().put("iteration", iteration)
              .put("test", test.getPackageName()+"."+test.getFunction())
              .put("status", test.getStatus().name()).put("measurements", measurements));
        }
        Files.writeString(new File(output, "iteration-"+iteration+".measurements.json").toPath(), observations.toString(2));
        QuickTestUtilsKt.writeResults(result.getResults(), FileSystem.SYSTEM,
          Path.Companion.get(new File(output, "iteration-"+iteration+".xml").getAbsolutePath(),false),
          result.getBuildRuleResults(), ExecutionResultsKt.getAttemptHistories(result));
        long failed = result.getResults().stream().filter(r -> r.getStatus()==ExecutionStatus.FAILURE).count();
        long setupFailed = result.getBuildRuleResults().stream().filter(r -> !r.getSucceeded()).count();
        Properties complete = new Properties();
        complete.setProperty("iteration", Integer.toString(iteration));
        complete.setProperty("tests", Integer.toString(result.getResults().size()));
        complete.setProperty("failures", Long.toString(failed));
        complete.setProperty("setupFailures", Long.toString(setupFailed));
        complete.setProperty("wholePublicCallReturned", "true");
        try (java.io.OutputStream marker = Files.newOutputStream(
            new File(output, "iteration-"+iteration+".complete.properties").toPath())) {
          complete.store(marker, "Raw XML was written before this completion marker");
        }
        if (result.getResults().isEmpty()) throw new IllegalStateException("No selected tests executed in iteration " + iteration);
        tests += result.getResults().size(); failures += failed + setupFailed;
        System.out.println("ITERATION " + iteration + " tests=" + result.getResults().size() + " failures=" + failed);
        // Controls measure every scheduled invocation, including failures; maxTestAttempts stays one.
      }
    }
    System.out.println("REGISTER tests="+tests+" failures="+failures+" repetitions="+repetitions+" maxTestAttempts=1");
    if (failures!=0) System.exit(1);
  }
}
