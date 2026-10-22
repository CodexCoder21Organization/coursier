"""Audit a fifteen-owner local gate without accepting partial or mismatched evidence."""
from pathlib import Path
import argparse
import collections
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

EVIDENCE = Path(__file__).resolve().parent
CORE = Path("/tmp/claude-501/-code/eff1f329-f18f-4c39-ad01-4fe1302ac178/scratchpad/workspace/I419i-kompile-core-20261020")
RUNTIME = Path("/tmp/I419i-runtime")
OWNERS = [
    "testPreparedAdmissionSingletonWorkersRetireBeforeReturn",
    "testPreparedAdmissionParallelWorkersRetireBeforeReturn",
    "testPreparedAdmissionInterruptedWorkerRetiresBeforeReturn",
    "testAttemptTimelineAttributedCompiler",
    "testPreparedBatchDoesNotRescanBuildScriptsOnExecute",
    "testPreparedBatchUsesPreparedWorkspaceRuleBody",
    "testR419aDecodedInputRemainsReusable",
    "testRepeatedPublicBuildCopiesDoNotRetainPayloads",
    "testReviewAdmissionFailureDrainsConcurrentSibling",
    "testAttemptTimelineAttributedPublication",
    "testAttemptTimelineUpstreamLockOwners",
    "testAttemptTimelineOlderHookLibrariesRemainUsable",
    "testAttemptTimelineJoinedWorkloadEvents",
    "testAttemptTimelinePreservesPartialOutputAcrossChildEvents",
    "testAttemptTimelineUpstreamEvents",
]
EXPECTED_ITERATIONS = 30
EXPECTED_WORKERS = 4
EXPECTED_CPU_AFFINITY = [0, 1]
EXPECTED_MAX_TEST_ATTEMPTS = 1
EXPECTED_CASES = len(OWNERS) * EXPECTED_ITERATIONS
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
MARKER_RE = re.compile(r"^iteration-(\d+)\.complete\.properties$")


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def file_binding(path):
    path = Path(path).resolve()
    return {"path": str(path), "size": path.stat().st_size, "sha256": sha256(path)}


def launcher_class_bindings(directory):
    directory = Path(directory).resolve()
    if not directory.is_dir():
        raise FileNotFoundError("launcher directory is missing")
    classes = sorted(directory.rglob("*.class"))
    if not classes:
        raise FileNotFoundError("launcher contains no class files")
    return {
        path.relative_to(directory).as_posix(): {
            "size": path.stat().st_size,
            "sha256": sha256(path),
        }
        for path in classes
    }


def artifact_bindings():
    launcher = (RUNTIME / "launcher").resolve()
    return {
        "managerJar": file_binding(RUNTIME / "manager.jar"),
        "controlAttemptsSource": file_binding(EVIDENCE / "ControlAttempts.java"),
        "launcherDirectory": str(launcher),
        "launcherClasses": launcher_class_bindings(launcher),
    }


def selector_paths():
    return {owner: str((CORE / "tests" / (owner + ".kts")).resolve()) for owner in OWNERS}


def selector_hashes(paths):
    return {owner: sha256(path) for owner, path in paths.items()}


def git_value(name):
    try:
        return subprocess.check_output(
            ["git", "rev-parse", name], cwd=CORE, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def clean_worktree():
    try:
        return not subprocess.check_output(
            ["git", "status", "--porcelain"], cwd=CORE, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return False


def read_json(path, label, errors):
    try:
        return json.loads(Path(path).read_text())
    except (OSError, ValueError, UnicodeError):
        errors.append(label + " is absent or invalid")
        return None


def validate_bindings(pre, post, terminal, errors, limitations):
    checks = {}
    if not isinstance(pre, dict):
        errors.append("preflight record is unavailable")
        return {"valid": False, "checks": checks}

    expected_paths = selector_paths()
    selectors = pre.get("selectors")
    paths = pre.get("selectorPaths")
    selector_ok = isinstance(selectors, dict) and set(selectors) == set(OWNERS)
    selector_ok = selector_ok and isinstance(paths, dict) and paths == expected_paths
    if selector_ok:
        try:
            selector_ok = all(SHA256_RE.fullmatch(selectors[name] or "") for name in OWNERS)
            selector_ok = selector_ok and selector_hashes(expected_paths) == selectors
        except (OSError, TypeError):
            selector_ok = False
    checks["selectorInventoryAndHashes"] = bool(selector_ok)
    if not selector_ok:
        errors.append("preflight selector names, paths, or source hashes do not match the reviewed inventory")

    parameter_ok = (
        pre.get("schemaVersion") == 2
        and pre.get("iterations") == EXPECTED_ITERATIONS
        and pre.get("workers") == EXPECTED_WORKERS
        and pre.get("cpuAffinity") == EXPECTED_CPU_AFFINITY
        and pre.get("maxTestAttempts") == EXPECTED_MAX_TEST_ATTEMPTS
        and pre.get("expectedCases") == EXPECTED_CASES
    )
    checks["gateParameters"] = bool(parameter_ok)
    if not parameter_ok:
        errors.append("preflight schema or gate parameters do not match the reviewed gate")

    head = pre.get("head")
    main = pre.get("main")
    git_pre_ok = bool(COMMIT_RE.fullmatch(head or "") and COMMIT_RE.fullmatch(main or ""))
    if git_pre_ok and terminal:
        git_pre_ok = isinstance(post, dict) and post.get("head") == head and post.get("main") == main
    if git_pre_ok and terminal:
        git_pre_ok = git_value("HEAD") == head and git_value("origin/main") == main
    checks["headAndMain"] = bool(git_pre_ok)
    if not git_pre_ok:
        errors.append("preflight and terminal HEAD/main bindings are missing or differ")

    expected_artifacts = None
    try:
        expected_artifacts = artifact_bindings()
    except (OSError, ValueError):
        errors.append("current manager, source, or launcher class bytes are unavailable")
    pre_artifacts = pre.get("artifacts")
    artifacts_pre_ok = isinstance(pre_artifacts, dict) and pre_artifacts == expected_artifacts
    if artifacts_pre_ok:
        artifacts_pre_ok = (
            pre.get("managerSha256") == pre_artifacts["managerJar"]["sha256"]
            and pre.get("launcherSourceSha256") == pre_artifacts["controlAttemptsSource"]["sha256"]
        )
    checks["preflightArtifactBytes"] = bool(artifacts_pre_ok)
    if not artifacts_pre_ok:
        limitations.append("preflight does not bind the exact manager JAR, controller source, and launcher class files")
        errors.append("preflight executable artifact bindings are missing or differ from current bytes")

    artifacts_terminal_ok = False
    if terminal:
        post_artifacts = post.get("artifacts") if isinstance(post, dict) else None
        artifacts_terminal_ok = (
            isinstance(post_artifacts, dict)
            and post_artifacts == pre_artifacts
            and post_artifacts == expected_artifacts
        )
        if not artifacts_terminal_ok:
            errors.append("preflight and postflight executable artifact bindings are missing or differ")
        if isinstance(post, dict):
            post_selectors = post.get("selectors")
            selectors_terminal_ok = post_selectors == selectors == selector_hashes(expected_paths)
            checks["postflightSelectorHashes"] = bool(selectors_terminal_ok)
            if not selectors_terminal_ok:
                errors.append("preflight and postflight selector source hashes differ")
            if post.get("sourceUnchanged") is not True:
                errors.append("postflight did not confirm unchanged selector sources")
            if post.get("worktreeClean") is not True or not clean_worktree():
                errors.append("terminal worktree is not confirmed clean")
            if post.get("returncode") != 0:
                errors.append("controller did not exit successfully")
    else:
        limitations.append("terminal postflight bindings are not available yet")
    checks["postflightArtifactBytes"] = bool(artifacts_terminal_ok)

    return {
        "valid": bool(selector_ok and parameter_ok and git_pre_ok and artifacts_pre_ok and
                       (artifacts_terminal_ok if terminal else False)),
        "checks": checks,
    }


def parse_properties(path, iteration, errors):
    values = {}
    try:
        lines = path.read_text().splitlines()
    except (OSError, UnicodeError):
        errors.append("completion marker is unreadable")
        return values
    for line in lines:
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key in values:
            errors.append("completion marker contains a duplicate property")
        values[key] = value
    if values.get("iteration") != str(iteration):
        errors.append("completion marker iteration property does not match its filename")
    if values.get("wholePublicCallReturned") != "true":
        errors.append("completion marker does not confirm return from the public call")
    return values


def qualified_name(package_name, function_name):
    return (package_name + "." if package_name else "") + function_name


def inspect_xml(xml_path, iteration, errors):
    expected_paths = selector_paths()
    try:
        root = ET.fromstring(xml_path.read_bytes())
    except (OSError, ET.ParseError):
        errors.append("XML result file is absent or invalid")
        return {}, collections.Counter(), 0

    if "incomplete" in root.attrib or "remote-failed" in root.attrib:
        errors.append("XML result file is marked incomplete or failed")
    for tag in ("result-read-error", "remote-run-error"):
        if list(root.iter(tag)):
            errors.append("XML result file contains " + tag)
    histories = list(root.iter("attempt-history"))
    attempts = list(root.iter("attempt"))
    if histories or attempts:
        errors.append("XML contains retry-attempt history despite maxTestAttempts=1")

    rows = list(root.iter("test"))
    found = {}
    counts = collections.Counter()
    for row in rows:
        name = row.attrib.get("name")
        file_path = row.attrib.get("file")
        if not name:
            errors.append("XML test row has no exact name")
            continue
        owner = name.rsplit(".", 1)[-1]
        package_name = name.rsplit(".", 1)[0] if "." in name else ""
        if owner not in OWNERS:
            errors.append("XML contains an owner outside the reviewed inventory")
            continue
        if file_path != expected_paths[owner]:
            errors.append("XML owner file path does not match its exact selected source")
        if "disabled" in row.attrib or "filtered-out" in row.attrib:
            errors.append("XML contains a disabled or filtered-out test row")
        success = row.attrib.get("success")
        if success not in ("true", "false"):
            errors.append("XML test row has no valid success outcome")
            continue
        status = "pass" if success == "true" else "fail"
        counts[status] += 1
        if owner in found:
            errors.append("XML contains more than one row for an expected owner")
        found[owner] = {
            "owner": owner,
            "packageName": package_name,
            "functionName": owner,
            "filePath": file_path,
            "name": name,
            "status": status,
            "duration": row.attrib.get("time"),
            "detailsSha256": hashlib.sha256("".join(row.itertext()).encode()).hexdigest(),
        }

    if set(found) != set(OWNERS):
        errors.append("XML owner inventory is incomplete or contains duplicates")
    for rule in root.iter("buildrule"):
        if rule.attrib.get("success") != "true":
            errors.append("XML contains a failed or malformed build rule")
    return found, counts, len(rows)


def inspect_timeline(timeline_path, expected_xml, iteration, errors, partial_lines):
    expected_paths = selector_paths()
    resources_path = timeline_path.with_name(timeline_path.name + ".resources.jsonl")
    streams = {}
    for stream_name, path in (("attempt", timeline_path), ("resources", resources_path)):
        stream_events = []
        try:
            lines = path.read_text().splitlines()
        except (OSError, UnicodeError):
            errors.append(stream_name + " timeline stream is absent or unreadable")
            streams[stream_name] = stream_events
            continue
        for line_number, line in enumerate(lines, start=1):
            try:
                value = json.loads(line)
                if not isinstance(value, dict):
                    raise ValueError("record is not an object")
                stream_events.append(value)
            except ValueError:
                partial_lines.append({"iteration": iteration, "stream": stream_name, "line": line_number})
                errors.append(stream_name + " timeline stream contains a malformed JSON record")
        streams[stream_name] = stream_events

    attempt_stream = streams["attempt"]
    resource_stream = streams["resources"]
    events = attempt_stream + resource_stream
    if not attempt_stream or attempt_stream[0].get("event") != "suite-start":
        errors.append("attempt timeline stream has no initial suite-start record")
    if any(event.get("event") in ("suite-end", "recorder-summary") for event in attempt_stream):
        errors.append("attempt timeline stream contains resource-only terminal records")
    if any(event.get("event") == "suite-start" for event in attempt_stream[1:]) or any(
        event.get("event") == "suite-start" for event in resource_stream
    ):
        errors.append("timeline has duplicate or misplaced suite-start records")

    suite_values = [event.get("suiteId") for event in events]
    suite_id = suite_values[0] if suite_values and isinstance(suite_values[0], str) else None
    if not suite_id or any(not isinstance(value, str) or value != suite_id for value in suite_values):
        errors.append("timeline suite identity is missing or inconsistent")
        suite_id = None

    summaries = [event for event in resource_stream if event.get("event") == "recorder-summary"]
    suite_ends = [event for event in resource_stream if event.get("event") == "suite-end"]
    if len(summaries) != 1 or not resource_stream or resource_stream[-1].get("event") != "recorder-summary":
        errors.append("resource timeline stream has no unique terminal recorder summary")
    if len(suite_ends) != 1:
        errors.append("resource timeline stream has no unique suite-end record")

    if summaries:
        summary = summaries[-1]
        ordinary_line_count = len(attempt_stream) + len(resource_stream) - 1
        if summary.get("writtenRecords") != ordinary_line_count:
            errors.append("recorder summary written-record count does not match both timeline streams")
        if summary.get("acceptedRecords") != summary.get("writtenRecords"):
            errors.append("recorder accepted and written record counts differ")
        if summary.get("lostRecords") != 0 or summary.get("unresolvedRecords") != 0:
            errors.append("recorder reports lost or unresolved timeline records")
        if summary.get("writeFailure") is not None or summary.get("writerInterruption") is not None:
            errors.append("recorder reports a write failure or writer interruption")
        if summary.get("bufferCapacity") != 8192:
            errors.append("timeline recorder capacity differs from the recorded gate")

    attempt_event_names = {"queued", "launched", "ready", "body-finished", "result"}
    attempt_events = [event for event in events if event.get("attemptId") is not None]
    retries = [event for event in events if event.get("event") == "retried"]
    if retries:
        errors.append("timeline contains retry events")
    if any(event.get("event") in attempt_event_names for event in resource_stream):
        errors.append("resource timeline stream contains attempt lifecycle records")
    for event in attempt_events:
        if event.get("suiteId") != suite_id or not isinstance(event.get("attemptId"), str) or not event.get("attemptId"):
            errors.append("attempt event has a missing or inconsistent suite/attempt identity")
        if event.get("attemptNumber") != EXPECTED_MAX_TEST_ATTEMPTS:
            errors.append("timeline contains an attempt number outside the max-one gate")
        owner = event.get("functionName")
        if (not isinstance(owner, str) or owner not in OWNERS
                or not isinstance(event.get("packageName"), str)
                or event.get("filePath") != expected_paths.get(owner)):
            errors.append("timeline attempt is attached to the wrong owner or source file")

    for event in attempt_stream:
        if event.get("event") in attempt_event_names and not event.get("attemptId"):
            errors.append("attempt lifecycle record is missing attemptId")

    launches = [event for event in attempt_stream if event.get("event") == "launched"]
    results = [event for event in attempt_stream if event.get("event") == "result"]
    def event_key(event):
        suite = event.get("suiteId")
        attempt = event.get("attemptId")
        if not isinstance(suite, str) or not suite or not isinstance(attempt, str) or not attempt:
            return None
        return suite, attempt

    launch_counts = collections.Counter(key for event in launches if (key := event_key(event)) is not None)
    result_counts = collections.Counter(key for event in results if (key := event_key(event)) is not None)
    launch_keys = set(launch_counts)
    result_keys = set(result_counts)
    if any(event_key(event) is None for event in launches + results):
        errors.append("launch or result record is missing suiteId/attemptId")
    if any(count != 1 for count in launch_counts.values()):
        errors.append("timeline does not have exactly one launch record per attempt identity")
    if any(count != 1 for count in result_counts.values()):
        errors.append("timeline does not have exactly one result record per attempt identity")
    if launch_keys != result_keys:
        errors.append("launched and completed attempt identity sets differ")
    if len(launches) != len(OWNERS) or len(results) != len(OWNERS):
        errors.append("timeline launch/result cardinality differs from the fifteen owners")

    launch_by_key = {key: event for event in launches if (key := event_key(event)) is not None}
    result_by_key = {key: event for event in results if (key := event_key(event)) is not None}
    result_owners = collections.Counter()
    for key in launch_keys | result_keys:
        launch = launch_by_key.get(key)
        result = result_by_key.get(key)
        if launch is None or result is None:
            continue
        launch_identity = tuple(launch.get(field) for field in ("filePath", "packageName", "functionName", "attemptNumber"))
        result_identity = tuple(result.get(field) for field in ("filePath", "packageName", "functionName", "attemptNumber"))
        if launch_identity != result_identity:
            errors.append("launch and result records disagree on owner/file identity")
        owner = result.get("functionName")
        if isinstance(owner, str) and owner in OWNERS:
            result_owners[owner] += 1
        if result.get("outcome") not in ("SUCCESS", "FAILURE"):
            errors.append("timeline result has an unsupported outcome")
        xml_row = expected_xml.get(owner) if isinstance(owner, str) else None
        if xml_row is None:
            errors.append("timeline result has no matching exact XML owner")
        else:
            package_name = result.get("packageName")
            timeline_name = qualified_name(package_name, owner) if isinstance(package_name, str) and isinstance(owner, str) else None
            if timeline_name != xml_row.get("name") or result.get("filePath") != xml_row.get("filePath"):
                errors.append("timeline result and XML row differ on exact owner/file identity")
            expected_outcome = "SUCCESS" if xml_row.get("status") == "pass" else "FAILURE"
            if result.get("outcome") != expected_outcome:
                errors.append("timeline outcome differs from its XML result row")

    if set(result_owners) != set(OWNERS) or any(count != 1 for count in result_owners.values()):
        errors.append("timeline results do not contain exactly one result per reviewed owner")
    if suite_ends:
        end = suite_ends[0]
        if end.get("resultCount") != len(OWNERS):
            errors.append("suite-end result count differs from the reviewed owner inventory")
        if end.get("failureCount") != sum(1 for row in expected_xml.values() if row.get("status") == "fail"):
            errors.append("suite-end failure count differs from XML outcomes")
        expected_reason = "completed" if all(row.get("status") == "pass" for row in expected_xml.values()) else "test-failures"
        if end.get("reason") != expected_reason:
            errors.append("suite-end reason differs from the observed XML outcomes")

    return {
        "suiteId": suite_id,
        "firstAttempts": len(launch_keys),
        "launchRecords": len(launches),
        "resultRecords": len(results),
        "retries": len(retries),
        "events": len(events),
    }


def inspect_iteration(raw, marker, iteration, partial_lines):
    errors = []
    properties = parse_properties(marker, iteration, errors)
    xml_path = raw / f"iteration-{iteration}.xml"
    timeline_path = raw / f"iteration-{iteration}.jsonl"
    xml_rows, xml_counts, xml_row_count = inspect_xml(xml_path, iteration, errors)
    timeline = inspect_timeline(timeline_path, xml_rows, iteration, errors, partial_lines)

    try:
        tests = int(properties.get("tests", "-1"))
        failures = int(properties.get("failures", "-1"))
        setup_failures = int(properties.get("setupFailures", "-1"))
    except ValueError:
        tests = failures = setup_failures = -1
        errors.append("completion marker counters are not integers")
    if tests != xml_row_count or tests != len(OWNERS):
        errors.append("completion marker test count differs from the exact XML inventory")
    if failures != xml_counts["fail"]:
        errors.append("completion marker failure count differs from XML outcomes")
    if setup_failures != 0:
        errors.append("completion marker reports setup failures or lacks the zero count")

    return {
        "iteration": iteration,
        "tests": xml_row_count,
        "passes": xml_counts["pass"],
        "failures": xml_counts["fail"],
        "firstAttempts": timeline["firstAttempts"],
        "launchRecords": timeline["launchRecords"],
        "resultRecords": timeline["resultRecords"],
        "retries": timeline["retries"],
        "suiteId": timeline["suiteId"],
        "xmlSha256": sha256(xml_path) if xml_path.is_file() else None,
        "timelineSha256": sha256(timeline_path) if timeline_path.is_file() else None,
        "valid": not errors,
        "errors": errors,
    }, xml_rows, xml_counts


def audit(run):
    global_errors = []
    limitations = []
    partial_lines = []
    pre = read_json(run / "preflight.json", "preflight", global_errors)
    post_path = run / "postflight.json"
    terminal = post_path.is_file()
    post = read_json(post_path, "postflight", global_errors) if terminal else None
    binding = validate_bindings(pre, post, terminal, global_errors, limitations)

    raw = run / "raw"
    if not raw.is_dir():
        global_errors.append("raw result directory is absent")
        marker_paths = []
    else:
        marker_paths = sorted(raw.glob("iteration-*.complete.properties"))
    selected_markers = {}
    alias_names = []
    for marker in marker_paths:
        match = MARKER_RE.fullmatch(marker.name)
        if not match:
            global_errors.append("completion marker filename is not canonical: " + marker.name)
            continue
        try:
            iteration = int(match.group(1))
        except ValueError:
            global_errors.append("completion marker iteration number is invalid")
            continue
        canonical = f"iteration-{iteration}.complete.properties"
        if marker.name != canonical:
            alias_names.append(marker.name)
            global_errors.append("completion marker uses a noncanonical numeric iteration alias")
        previous = selected_markers.get(iteration)
        if previous is None or (previous.name != f"iteration-{iteration}.complete.properties" and marker.name == canonical):
            selected_markers[iteration] = marker
        else:
            global_errors.append("more than one completion marker names the same numeric iteration")

    expected_ids = set(range(1, EXPECTED_ITERATIONS + 1))
    present_ids = set(selected_markers)
    if present_ids != expected_ids:
        missing = sorted(expected_ids - present_ids)
        extra = sorted(present_ids - expected_ids)
        global_errors.append("distinct iteration IDs do not equal 1 through 30; missing=" + str(missing) + ", extra=" + str(extra))

    raw_xml_ids = set()
    raw_timeline_ids = set()
    raw_resource_ids = set()
    if raw.is_dir():
        for path in raw.iterdir():
            resource_match = re.fullmatch(r"iteration-(\d+)\.jsonl\.resources\.jsonl", path.name)
            if resource_match:
                iteration = int(resource_match.group(1))
                raw_resource_ids.add(iteration)
                if path.name != f"iteration-{iteration}.jsonl.resources.jsonl":
                    global_errors.append("resource timeline filename uses a noncanonical numeric iteration alias")
                continue
            match = re.fullmatch(r"iteration-(\d+)\.(xml|jsonl)", path.name)
            if not match:
                continue
            iteration = int(match.group(1))
            if path.suffix == ".xml":
                raw_xml_ids.add(iteration)
            else:
                raw_timeline_ids.add(iteration)
            if path.name != f"iteration-{iteration}{path.suffix}":
                global_errors.append("raw XML/timeline filename uses a noncanonical numeric iteration alias")
    if raw_xml_ids != present_ids or raw_timeline_ids != present_ids or raw_resource_ids != present_ids:
        global_errors.append("raw XML/timeline/resource files do not correspond exactly to unique completion markers")

    iterations = []
    per_owner = {owner: collections.Counter() for owner in OWNERS}
    totals = collections.Counter()
    suite_ids = set()
    for iteration in sorted(selected_markers):
        summary, xml_rows, xml_counts = inspect_iteration(raw, selected_markers[iteration], iteration, partial_lines)
        iterations.append(summary)
        for owner, row in xml_rows.items():
            per_owner[owner][row["status"]] += 1
        totals.update(xml_counts)
        totals["firstAttempts"] += summary["firstAttempts"]
        totals["launchRecords"] += summary["launchRecords"]
        totals["resultRecords"] += summary["resultRecords"]
        totals["retries"] += summary["retries"]
        if summary["suiteId"]:
            if summary["suiteId"] in suite_ids:
                summary["valid"] = False
                summary["errors"].append("suiteId is reused across iterations")
            suite_ids.add(summary["suiteId"])

    if any(not item["valid"] for item in iterations):
        global_errors.append("one or more iteration records failed their exact accounting checks")
    if partial_lines:
        global_errors.append("one or more timeline JSON lines are malformed")
    if len(iterations) != EXPECTED_ITERATIONS or totals["firstAttempts"] != EXPECTED_CASES:
        global_errors.append("complete gate totals do not contain exactly 450 first attempts")
    if totals["fail"] != 0:
        global_errors.append("one or more expected tests failed")
    if not terminal:
        limitations.append("postflight does not exist; this run is still active or ended without a terminal record")

    iteration_set_ok = present_ids == expected_ids and not alias_names
    row_accounting_ok = (
        len(iterations) == EXPECTED_ITERATIONS
        and all(item["valid"] and item["tests"] == len(OWNERS)
                and item["firstAttempts"] == len(OWNERS)
                and item["launchRecords"] == len(OWNERS)
                and item["resultRecords"] == len(OWNERS)
                and item["retries"] == 0 for item in iterations)
    )
    report = {
        "state": "INCOMPLETE" if not terminal else "REJECTED",
        "accepted": False,
        "terminal": terminal,
        "completeIterations": len(iterations),
        "expectedIterations": EXPECTED_ITERATIONS,
        "distinctIterationIds": sorted(present_ids),
        "allExpectedCasesPresent": bool(iteration_set_ok and row_accounting_ok and totals["firstAttempts"] == EXPECTED_CASES),
        "totals": dict(totals),
        "perOwner": {owner: dict(counts) for owner, counts in per_owner.items()},
        "iterations": iterations,
        "partialJsonLines": partial_lines,
        "binding": binding,
        "errors": global_errors,
        "limitations": limitations,
    }
    no_iteration_errors = not global_errors and all(item["valid"] for item in iterations)
    report["accepted"] = bool(
        terminal and report["allExpectedCasesPresent"] and totals["fail"] == 0
        and binding["valid"] and no_iteration_errors
    )
    report["state"] = "ACCEPTED" if report["accepted"] else ("INCOMPLETE" if not terminal else "REJECTED")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run_directory", type=Path)
    parser.add_argument("--require-accepted", action="store_true")
    args = parser.parse_args()
    report = audit(args.run_directory)
    (args.run_directory / "counts.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: value for key, value in report.items() if key != "iterations"}, indent=2))
    return 0 if report["accepted"] or not args.require_accepted else 1


if __name__ == "__main__":
    raise SystemExit(main())
