import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TestSessionCoordinatorSelfTest {
    private static final Pattern RUN_ID = Pattern.compile("\\d{8}T\\d{6}Z-p\\d+-[0-9a-f]{6}");
    private static final List<String> MANIFEST_KEYS = List.of(
            "run_id", "state", "manifest", "worktree", "lease_path", "source_digest",
            "runtime_inputs_digest", "build_root", "tmp_root", "surefire_reports", "trace_reports",
            "diagnostics_root", "artifact_root", "distribution_root", "isolation",
            "lwjgl_extraction", "lwjgl_extract_template", "reports", "artifacts");

    private TestSessionCoordinatorSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].startsWith("child-")) {
            runChild(args[0]);
            return;
        }
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: TestSessionCoordinatorSelfTest <temporary-root>");
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path outputRoot = createOwnedDirectory(root.resolve("output"));

        verifyManagedReservationIsValidated(root);
        verifyManagedHelperFailureDoesNotFallback(root);
        verifyManagedMalformedJsonDoesNotFallback(root);
        verifyUnmanagedProjectFallbackIsVisible(root);
        verifyExplicitRootRemainsFailClosed(root);
        BasicRun first = verifySuccessfulRun(root, outputRoot);
        verifyExplicitQuietRun(root, outputRoot);
        verifyVerboseRun(root, outputRoot);
        verifyForeignOwnedRootIsRejected(root, outputRoot);
        verifySpaceContainingRoot(root);
        verifyInWorktreeSymlinkLockRootIsRejected(root, outputRoot);
        verifyChildExitPropagation(root, outputRoot);
        verifyShutdownFinalizesSession(root, outputRoot);
        verifyShutdownStopsProcessTree(root, outputRoot);
        verifySourceMutationInvalidatesRun(root, outputRoot);
        verifyRuntimeInputMutationInvalidatesRun(root, outputRoot);
        verifyIgnoredFileDoesNotInvalidateRun(root, outputRoot);
        verifyLeaseDisappearanceInvalidatesRun(root, outputRoot);
        verifyArbitraryReclaimIsRejected(root, outputRoot);
        verifyMismatchedReclaimMetadataIsRejected(root, outputRoot);
        verifyOwnerPublicationAndLiveLock(root, outputRoot);
        verifyStagedPublicationIsRetained(root, outputRoot);
        verifyInterruptedInitializationRetriesExactly(root, outputRoot);
        verifyLiveInitializationCannotBeReclaimed(root, outputRoot);
        verifyLivePostLockInitializationCannotBeReclaimed(root, outputRoot);
        verifySecondReclaimCheckPreventsLaunch(root, outputRoot);
        verifyNormalContentionRetriesExactly(outputRoot, first);
        String secondRunId = verifyInterruptedReclaimCanResume(root, outputRoot, first);
        check(!first.runId.equals(secondRunId), "run IDs must be unique");

        System.out.println("TestSessionCoordinatorSelfTest: PASS");
    }

    private static void verifyManagedReservationIsValidated(Path root) throws Exception {
        Path project = createTestProject(root.resolve("managed-valid-project"));
        Path managedRoot = createOwnedDirectory(root.resolve("managed-valid-root"));
        Path allocation = createOwnedDirectory(managedRoot.resolve("codex/test-sessions/session-reserved"));
        Path fakeBin = createFakeAgentScratch(root.resolve("managed-valid-bin"),
                reservationJson(managedRoot, allocation, Instant.now().plus(Duration.ofDays(6))));
        Path lockRoot = createOwnedDirectory(root.resolve("managed-valid-locks"));

        CommandResult result = runStorageCoordinator(project, managedRoot, fakeBin, null, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));

        check(result.exitCode == 0, "validated managed reservation must run successfully:\n" + result.output);
        Path manifest = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        check(manifest.getParent().getParent().equals(allocation),
                "managed reservation must be the parent of the coordinator-created run: " + manifest);
        check(!Files.exists(project.resolve(".openggf/test-runs")),
                "validated managed allocation must not create a project-local fallback");
    }

    private static void verifyManagedHelperFailureDoesNotFallback(Path root) throws Exception {
        Path project = createTestProject(root.resolve("managed-failure-project"));
        Path managedRoot = createOwnedDirectory(root.resolve("managed-failure-root"));
        createOwnedDirectory(managedRoot.resolve("codex/test-sessions"));
        Path fakeBin = createFakeAgentScratchFailure(root.resolve("managed-failure-bin"));
        Path childMarker = root.resolve("managed-failure-child-started");

        CommandResult result = runStorageCoordinator(project, managedRoot, fakeBin, childMarker, List.of(
                "--lock-root", createOwnedDirectory(root.resolve("managed-failure-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-mark-start"));

        check(result.exitCode != 0, "configured managed helper failure must fail startup");
        check(!Files.exists(project.resolve(".openggf/test-runs")),
                "managed helper failure must not create a project-local fallback");
        check(!Files.exists(childMarker), "managed helper failure must not start the child");
        check(!result.output.contains("OPENGGF_TEST_RUN_START"),
                "managed helper failure must not publish a child-start marker");
    }

    private static void verifyManagedMalformedJsonDoesNotFallback(Path root) throws Exception {
        Path managedRoot = createOwnedDirectory(root.resolve("managed-malformed-root"));
        Path allocation = createOwnedDirectory(managedRoot.resolve("codex/test-sessions/session-reserved"));
        Path mismatchedManagedRoot = createOwnedDirectory(root.resolve("managed-mismatched-root"));
        Path outsideAllocation = createOwnedDirectory(managedRoot.resolve("codex/outside-allocation"));
        String valid = reservationJson(managedRoot, allocation, Instant.now().plus(Duration.ofDays(6)));
        Map<String, String> malformed = new LinkedHashMap<>();
        malformed.put("syntax", "{");
        malformed.put("duplicate", valid.replace("\"schema_version\":1",
                "\"schema_version\":1,\"schema_version\":1"));
        malformed.put("unknown", valid.substring(0, valid.length() - 1) + ",\"future_field\":1}");
        malformed.put("schema-type", valid.replace("\"schema_version\":1", "\"schema_version\":\"1\""));
        malformed.put("tier", valid.replace("MANAGED_CODEX_TEST_SESSIONS", "PROJECT_LOCAL_FALLBACK"));
        malformed.put("managed-root", valid.replace(
                "\"managed_root\":\"" + jsonEscape(managedRoot.toString()) + "\"",
                "\"managed_root\":\"" + jsonEscape(mismatchedManagedRoot.toString()) + "\""));
        malformed.put("allocation", valid.replace(jsonEscape(allocation.toString()),
                jsonEscape(outsideAllocation.toString())));
        malformed.put("device-type", valid.replaceFirst("\"filesystem_device\":\\d+",
                "\"filesystem_device\":\"1\""));
        malformed.put("device-mismatch", valid.replaceFirst("\"filesystem_device\":\\d+",
                "\"filesystem_device\":9223372036854775807"));
        malformed.put("usable-bytes-type", valid.replace("\"usable_bytes\":1048576",
                "\"usable_bytes\":\"1048576\""));
        malformed.put("total-bytes-type", valid.replace("\"total_bytes\":2097152",
                "\"total_bytes\":\"2097152\""));
        malformed.put("inodes-type", valid.replace("\"usable_inodes\":1024",
                "\"usable_inodes\":\"1024\""));
        malformed.put("missing-retention", valid.replaceFirst(
                ",\"retention_deadline\":\"[^\"]+\"", ""));
        malformed.put("past-retention", reservationJson(managedRoot, allocation,
                Instant.now().minus(Duration.ofHours(1))));
        malformed.put("unbounded-retention", reservationJson(managedRoot, allocation,
                Instant.now().plus(Duration.ofDays(8))));
        malformed.put("helper-version-type", valid.replace(
                "\"helper_version\":\"openggf-agent-scratch-v2\"", "\"helper_version\":2"));
        malformed.put("helper-version-value", valid.replace("openggf-agent-scratch-v2",
                "openggf-agent-scratch-v3"));
        malformed.put("trailing-object", valid + "{}");

        int index = 0;
        for (Map.Entry<String, String> entry : malformed.entrySet()) {
            Path project = createTestProject(root.resolve("managed-malformed-project-" + index));
            Path fakeBin = createFakeAgentScratch(root.resolve("managed-malformed-bin-" + index), entry.getValue());
            Path childMarker = root.resolve("managed-malformed-child-" + index);
            CommandResult result = runStorageCoordinator(project, managedRoot, fakeBin, childMarker, List.of(
                    "--lock-root", createOwnedDirectory(root.resolve("managed-malformed-locks-" + index)).toString(),
                    "--", javaCommand(), "-cp", classPath(),
                    TestSessionCoordinatorSelfTest.class.getName(), "child-mark-start"));
            check(result.exitCode != 0, "malformed managed reservation must fail (" + entry.getKey() + "):\n"
                    + result.output);
            check(!Files.exists(project.resolve(".openggf/test-runs")),
                    "malformed managed reservation must not create fallback (" + entry.getKey() + ")");
            check(!Files.exists(childMarker),
                    "malformed managed reservation must not start child (" + entry.getKey() + ")");
            check(!result.output.contains("OPENGGF_TEST_RUN_START"),
                    "malformed managed reservation must not publish start marker (" + entry.getKey() + ")");
            index++;
        }
    }

    private static void verifyUnmanagedProjectFallbackIsVisible(Path root) throws Exception {
        Path project = createTestProject(root.resolve("unmanaged-project"));
        Path lockRoot = createOwnedDirectory(root.resolve("unmanaged-locks"));
        CommandResult result = runStorageCoordinator(project, null, null, null, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));

        check(result.exitCode == 0, "unmanaged contributor fallback must remain usable:\n" + result.output);
        check(result.output.contains("PROJECT_LOCAL_FALLBACK"),
                "unmanaged project fallback must emit a visible storage-tier warning:\n" + result.output);
        Path manifest = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        check(manifest.startsWith(project.resolve(".openggf/test-runs")),
                "unmanaged fallback must allocate beneath the project-local lane: " + manifest);
    }

    private static void verifyExplicitRootRemainsFailClosed(Path root) throws Exception {
        Path project = createTestProject(root.resolve("explicit-invalid-project"));
        Path managedRoot = createOwnedDirectory(root.resolve("explicit-invalid-managed"));
        Path allocation = createOwnedDirectory(managedRoot.resolve("codex/test-sessions/session-reserved"));
        Path fakeBin = createFakeAgentScratch(root.resolve("explicit-invalid-bin"),
                reservationJson(managedRoot, allocation, Instant.now().plus(Duration.ofDays(6))));
        Path childMarker = root.resolve("explicit-invalid-child-started");
        ProcessBuilder builder = storageCoordinatorProcess(project, managedRoot, fakeBin, childMarker, List.of(
                "--lock-root", createOwnedDirectory(root.resolve("explicit-invalid-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-mark-start"));
        builder.environment().put("OPENGGF_TEST_ROOT", "relative-root");
        CommandResult result = finish(builder.start());

        check(result.exitCode != 0, "invalid explicit root must fail closed");
        check(!Files.exists(project.resolve(".openggf/test-runs")),
                "invalid explicit root must not fall through to project-local storage");
        check(!Files.exists(childMarker), "invalid explicit root must not start the child");
    }

    private static BasicRun verifySuccessfulRun(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-success"));
        Path exportFile = root.resolve("success.export");
        CommandResult result = runCoordinator(outputRoot, List.of(
                "--export-file", exportFile.toString(),
                "--lock-root", lockRoot.toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));

        check(result.exitCode == 0, "successful child must produce exit code 0:\n" + result.output);
        String startLine = findLine(result.output, "OPENGGF_TEST_RUN_START");
        String endLine = findLine(result.output, "OPENGGF_TEST_RUN_END");
        String runId = markerValue(startLine, "run_id");
        check(RUN_ID.matcher(runId).matches(), "run ID must use UTC-pid-random format: " + runId);
        check(runId.equals(markerValue(endLine, "run_id")), "start and end markers must identify the same run");
        check("0".equals(markerValue(endLine, "exit_code")), "end marker must report child exit code");
        check("worktree-session".equals(markerValue(startLine, "isolation")),
                "start marker must identify the coordinator-owned isolation policy");
        check("per-surefire-fork".equals(markerValue(startLine, "lwjgl")),
                "start marker must identify per-fork LWJGL extraction");
        check("worktree-session".equals(markerValue(endLine, "isolation")),
                "end marker must identify the coordinator-owned isolation policy");
        check("per-surefire-fork".equals(markerValue(endLine, "lwjgl")),
                "end marker must identify per-fork LWJGL extraction");

        Path manifest = Path.of(markerValue(startLine, "manifest"));
        check(manifest.isAbsolute() && Files.isRegularFile(manifest), "manifest path must be absolute and regular");
        Path mavenLog = Path.of(markerValue(startLine, "log"));
        check(mavenLog.equals(manifest.getParent().resolve("maven.log")),
                "start marker must identify the session Maven log");
        check(mavenLog.equals(Path.of(markerValue(endLine, "log"))),
                "start and end markers must identify the same Maven log");
        String json = Files.readString(manifest);
        for (String key : MANIFEST_KEYS) {
            check(json.contains("\"" + key + "\""), "manifest missing required key: " + key);
        }
        check(json.contains("\"state\": \"PASSED\""), "successful manifest must be PASSED");
        check(json.contains("\"run_id\": \"" + runId + "\""), "manifest run ID must match marker");
        check(json.matches("(?s).*\"source_digest\": \"[0-9a-f]{64}\".*"), "source digest must be SHA-256");
        check(json.matches("(?s).*\"runtime_inputs_digest\": \"[0-9a-f]{64}\".*"),
                "runtime-input digest must be SHA-256");
        check(json.contains("libopenggf-selftest.so"),
                "manifest artifact inventory must include native libraries under build/native-libs");
        check(json.contains("/build/libopenggf-selftest.so"),
                "manifest artifact inventory must include native libraries beside the build binary");

        Path lease = Path.of(jsonString(json, "lease_path"));
        check(Files.isRegularFile(lease), "owner namespace must retain a regular lease.lock");
        Path namespace = lease.getParent();
        Path owner = namespace.resolve("owner.json");
        Path initializing = namespace.resolve("initializing.json");
        check(Files.isRegularFile(owner), "owner.json must be published after the lock is acquired");
        check(Files.isRegularFile(initializing), "initialization metadata must remain available for recovery");
        String ownerJson = Files.readString(owner);
        check(ownerJson.contains("\"run_id\": \"" + runId + "\""), "owner metadata must identify the run");
        check(ownerJson.contains("\"state\": \"owner\""), "owner metadata must identify the publication state");
        check(ownerJson.contains("\"branch\""), "owner metadata must record the starting branch");
        check(ownerJson.contains("\"head\""), "owner metadata must record the starting HEAD");

        String exported = Files.readString(exportFile);
        Path session = manifest.getParent();
        String expectedExport = "manifest=" + manifest + "\n"
                + "run_id=" + runId + "\n"
                + "build_root=" + session.resolve("build") + "\n"
                + "tmp_root=" + session.resolve("tmp") + "\n"
                + "surefire_reports=" + session.resolve("surefire-reports") + "\n"
                + "trace_reports=" + session.resolve("trace-reports") + "\n"
                + "diagnostics_root=" + session.resolve("diagnostics") + "\n"
                + "artifact_root=" + session.resolve("artifacts") + "\n"
                + "distribution_root=" + session.resolve("distribution") + "\n";
        check(exported.equals(expectedExport),
                "export file must contain the manifest and session roots:\n" + exported);
        check(Files.readString(mavenLog).contains("CHILD_ENV_OK"),
                "child output must be captured in maven.log");
        check(!result.output.contains("CHILD_ENV_OK"),
                "child output must not be streamed to stdout by default");
        return new BasicRun(runId, lease, lockRoot);
    }

    private static void verifyExplicitQuietRun(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-explicit-quiet"));
        CommandResult result = runCoordinator(outputRoot, List.of(
                "--quiet", "--lock-root", lockRoot.toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        check(result.exitCode == 0, "explicit quiet mode must succeed:\n" + result.output);
        check(!result.output.contains("CHILD_ENV_OK"),
                "explicit quiet mode must not stream child output to stdout");
    }

    private static void verifyVerboseRun(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-verbose"));
        CommandResult result = runCoordinator(outputRoot, List.of(
                "--verbose", "--lock-root", lockRoot.toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        check(result.exitCode == 0, "verbose mode must succeed:\n" + result.output);
        check(result.output.contains("CHILD_ENV_OK"),
                "verbose mode must stream child output to stdout");
        Path log = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "log"));
        check(Files.readString(log).contains("CHILD_ENV_OK"),
                "verbose mode must continue capturing child output in maven.log");
    }

    private static void verifyChildExitPropagation(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-exit"));
        CommandResult result = runCoordinator(outputRoot, List.of(
                "--lock-root", lockRoot.toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-exit-7"));
        check(result.exitCode == 7, "coordinator must preserve a nonzero child exit code");
        check("7".equals(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_END"), "exit_code")),
                "end marker must preserve a nonzero child exit code");
        Path manifest = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        check(Files.readString(manifest).contains("\"state\": \"FAILED\""),
                "nonzero child exit must produce a FAILED manifest");
    }

    private static void verifySpaceContainingRoot(Path root) throws Exception {
        Path outputRoot = createOwnedDirectory(root.resolve("output with spaces"));
        Path lockRoot = createOwnedDirectory(root.resolve("locks-spaces"));
        CommandResult result = runCoordinator(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        check(result.exitCode == 0, "session roots containing spaces must preserve JVM option boundaries:\n"
                + result.output);
    }

    private static void verifyInWorktreeSymlinkLockRootIsRejected(Path root, Path outputRoot) throws Exception {
        Path link = root.resolve("lock-root-link");
        Path worktree = Path.of(System.getProperty("user.dir")).toRealPath();
        try {
            try {
                Files.createSymbolicLink(link, worktree);
            } catch (UnsupportedOperationException e) {
                return;
            }
            CommandResult result = runCoordinator(outputRoot, List.of(
                    "--lock-root", link.toString(), "--", javaCommand(), "-cp", classPath(),
                    TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
            check(result.exitCode != 0, "a lock root symlinked into the worktree must be rejected");
        } finally {
            if (Files.isSymbolicLink(link)) {
                try (var entries = Files.list(link)) {
                    for (Path entry : entries.filter(path -> path.getFileName().toString()
                            .startsWith("openggf-test-session.lock")).toList()) {
                        deleteTree(entry);
                    }
                }
            }
            Files.deleteIfExists(link);
        }
    }

    private static void verifyForeignOwnedRootIsRejected(Path root, Path outputRoot) throws Exception {
        Path systemRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        String currentOwner = systemRoot.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name")).getName();
        if (Files.getOwner(systemRoot).getName().equals(currentOwner)) {
            return;
        }
        Path lockRoot = createOwnedDirectory(root.resolve("locks-foreign-root"));
        ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        builder.environment().put("OPENGGF_TEST_ROOT", systemRoot.toString());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        check(exit != 0, "a session root owned by another principal must be rejected: " + output);
    }

    private static void verifySourceMutationInvalidatesRun(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-source-mutation"));
        Path worktree = Path.of(System.getProperty("user.dir"));
        try {
            CommandResult result = runCoordinator(outputRoot, List.of(
                    "--lock-root", lockRoot.toString(),
                    "--", javaCommand(), "-cp", classPath(),
                    TestSessionCoordinatorSelfTest.class.getName(), "child-mutate"));
            check(result.exitCode != 0, "source mutation must make the coordinator nonzero");
            Path manifest = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
            String json = Files.readString(manifest);
            check(json.contains("\"state\": \"INVALID_IDENTITY_CHANGED\""),
                    "source mutation must invalidate the session identity");
        } finally {
            try (var paths = Files.list(worktree)) {
                paths.filter(path -> path.getFileName().toString().startsWith(".session-selftest-mutation-"))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }
    }

    private static void verifyRuntimeInputMutationInvalidatesRun(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-runtime-mutation"));
        Path runtimeInput = root.resolve("runtime-input.bin");
        Files.writeString(runtimeInput, "before\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                    "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                    TestSessionCoordinatorSelfTest.class.getName(), "child-mutate-runtime"));
            builder.environment().put("OPENGGF_RUNTIME_INPUTS", runtimeInput.toString());
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            check(exit != 0, "runtime input mutation must make the coordinator nonzero: " + output);
            Path manifest = Path.of(markerValue(findLine(output, "OPENGGF_TEST_RUN_START"), "manifest"));
            check(Files.readString(manifest).contains("\"state\": \"INVALID_IDENTITY_CHANGED\""),
                    "runtime input mutation must invalidate the session identity");
        } finally {
            Files.deleteIfExists(runtimeInput);
        }
    }

    private static void verifyIgnoredFileDoesNotInvalidateRun(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-ignored-file"));
        Path ignored = Path.of(System.getProperty("user.dir"), "mods",
                ".session-selftest-ignored-" + ProcessHandle.current().pid()
                        + "-" + System.nanoTime() + ".txt");
        try {
            ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                    "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                    TestSessionCoordinatorSelfTest.class.getName(), "child-create-ignored"));
            builder.environment().put("OPENGGF_TEST_IGNORED_FILE", ignored.toString());
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            CommandResult result = new CommandResult(process.waitFor(), output);
            check(result.exitCode == 0, "ignored file creation must not invalidate the source identity:\n"
                    + result.output);
            Path manifest = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
            check(Files.readString(manifest).contains("\"state\": \"PASSED\""),
                    "ignored file creation must leave the session PASSED");
        } finally {
            Files.deleteIfExists(ignored);
        }
    }

    private static void verifyLeaseDisappearanceInvalidatesRun(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-lease-disappearance"));
        CommandResult result = runCoordinator(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-delete-lease"));
        check(result.exitCode != 0, "lease disappearance must make the coordinator nonzero");
        Path manifest = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        check(Files.readString(manifest).contains("\"state\": \"INVALID_IDENTITY_CHANGED\""),
                "lease disappearance must invalidate the session identity");
    }

    private static void verifyShutdownFinalizesSession(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-shutdown"));
        Process process = coordinatorProcess(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-sleep")).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String line;
        Path manifest = null;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("OPENGGF_TEST_RUN_START ")) {
                manifest = Path.of(markerValue(line, "manifest"));
                break;
            }
        }
        check(manifest != null, "shutdown test must observe the run start marker");
        process.destroy();
        check(process.waitFor(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS),
                "coordinator must terminate after SIGTERM");
        check(Files.readString(manifest).contains("\"state\": \"ABORTED\""),
                "shutdown must finalize the manifest as ABORTED");
    }

    private static void verifyShutdownStopsProcessTree(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-shutdown-tree"));
        Path pidFile = root.resolve("grandchild.pid");
        ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-spawn-grandchild"));
        builder.environment().put("OPENGGF_TEST_GRANDCHILD_PID_FILE", pidFile.toString());
        Process process = builder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null && line.startsWith("Picked up")) {
            // Java may report JAVA_TOOL_OPTIONS before the coordinator marker.
        }
        check(line != null && line.startsWith("OPENGGF_TEST_RUN_START "),
                "process-tree shutdown test must observe the run start marker");
        for (int attempt = 0; attempt < 100 && !Files.exists(pidFile); attempt++) {
            Thread.sleep(10);
        }
        check(Files.isRegularFile(pidFile), "child must publish its grandchild PID");
        long grandchildPid = Long.parseLong(Files.readString(pidFile).trim());
        process.destroy();
        check(process.waitFor(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS),
                "coordinator must terminate after process-tree SIGTERM");
        try {
            check(ProcessHandle.of(grandchildPid).map(ProcessHandle::isAlive).orElse(false) == false,
                    "shutdown must stop coordinator descendants before releasing the lease");
        } finally {
            ProcessHandle.of(grandchildPid).ifPresent(handle -> handle.destroyForcibly());
        }
    }

    private static void verifyArbitraryReclaimIsRejected(Path root, Path outputRoot) throws Exception {
        Path unrelated = createOwnedDirectory(root.resolve("unrelated-directory"));
        CommandResult result = runCoordinator(outputRoot, List.of(
                "--reclaim", unrelated.resolve("not-a-lease.lock").toString()));
        check(result.exitCode != 0, "reclaim must reject a non-lease path");
        check(Files.isDirectory(unrelated), "reclaim rejection must not rename an arbitrary directory");
    }

    private static void verifyMismatchedReclaimMetadataIsRejected(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-mismatched-reclaim"));
        Path namespace = Files.createDirectory(lockRoot.resolve("openggf-test-session.lock-fake"));
        Files.writeString(namespace.resolve("initializing.json"),
                "{\"pid\":999999999,\"worktree\":\"/wrong-worktree\","
                        + "\"lease_path\":\"/wrong-worktree/lease.lock\",\"state\":\"initializing\"}\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        CommandResult result = runCoordinator(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--reclaim",
                namespace.resolve("lease.lock").toString()));
        check(result.exitCode != 0, "reclaim must validate the recorded namespace identity");
        check(Files.isDirectory(namespace), "mismatched reclaim metadata must not be renamed");
    }

    private static void verifyOwnerPublicationAndLiveLock(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-owner"));
        GuardedProcess process = startGuarded(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--guard", "owner",
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"), "owner");
        Path namespace = onlyEntry(lockRoot, path -> !path.getFileName().toString().contains(".staging-"));
        check(Files.isRegularFile(namespace.resolve("owner.json")),
                "owner.json must be visible while the coordinator owns the lease");
        check(!Files.exists(namespace.resolve("owner.json.tmp")),
                "owner publication must not expose its temporary file");
        try (FileChannel channel = FileChannel.open(namespace.resolve("lease.lock"), StandardOpenOption.WRITE)) {
            FileLock competing = channel.tryLock();
            check(competing == null, "lease.lock must remain exclusively locked while the child can run");
        }
        process.release();
        CommandResult result = process.finish();
        check(result.exitCode == 0, "owner-guarded run must complete after release:\n" + result.output);
    }

    private static void verifyStagedPublicationIsRetained(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-staged"));
        GuardedProcess process = startGuarded(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--guard", "staged",
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-must-not-run"), "staged");
        Path staging = onlyEntry(lockRoot, path -> path.getFileName().toString().contains(".staging-"));
        Path metadata = staging.resolve("initializing.json");
        check(Files.isRegularFile(metadata), "staging directory must contain initializing.json before publication");
        String json = Files.readString(metadata);
        check(json.contains("\"state\": \"initializing\""), "initializing metadata must name its state");
        check(json.matches("(?s).*\"pid\": \\d+.*"), "initializing metadata must record the coordinator PID");
        check(json.contains("\"worktree\""), "initializing metadata must record the canonical worktree");
        process.kill();
        check(Files.isDirectory(staging), "interrupted staging directory must be retained");
    }

    private static void verifyInterruptedInitializationRetriesExactly(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-initializing"));
        GuardedProcess process = startGuarded(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--guard", "initialized",
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-must-not-run"), "initialized");
        Path namespace = onlyEntry(lockRoot, path -> !path.getFileName().toString().contains(".staging-"));
        Path initializing = namespace.resolve("initializing.json");
        check(Files.isRegularFile(initializing), "published namespace must expose initialization metadata");
        check(!Files.exists(namespace.resolve("lease.lock")), "lease.lock must not exist before lock creation");
        process.kill();

        CommandResult contender = runCoordinator(outputRoot, List.of(
                "--lock-root", lockRoot.toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-must-not-run"));
        check(contender.exitCode == 75, "interrupted initialization must exhaust with EX_TEMPFAIL");
        assertRetrySchedule(contender.output);
        check(Files.isRegularFile(initializing), "failed initialization metadata must be retained");
        check(!contender.output.contains("CHILD_MUST_NOT_RUN"), "contender must not launch the child");
        check(!contender.output.contains("OPENGGF_TEST_RUN_START"), "startup failure must not publish a Maven manifest");
    }

    private static void verifyLiveInitializationCannotBeReclaimed(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-live-initializer"));
        GuardedProcess process = startGuarded(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--guard", "initialized",
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-must-not-run"), "initialized");
        Path namespace = onlyEntry(lockRoot, path -> !path.getFileName().toString().contains(".staging-"));
        try {
            CommandResult reclaim = runCoordinator(outputRoot, List.of(
                    "--lock-root", lockRoot.toString(), "--reclaim",
                    namespace.resolve("lease.lock").toString()));
            check(reclaim.exitCode == 75, "live initializer reclaim must be retryable contention");
            check(Files.isDirectory(namespace), "live initializer must not be renamed");
            check(!Files.exists(namespace.resolve("reclaiming.json")),
                    "live initializer reclaim must not leave a reclaim marker");
        } finally {
            process.kill();
        }
    }

    private static void verifyLivePostLockInitializationCannotBeReclaimed(Path root, Path outputRoot)
            throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-live-post-lock"));
        GuardedProcess process = startGuarded(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--guard", "lease-created",
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-must-not-run"), "lease-created");
        Path namespace = onlyEntry(lockRoot, path -> !path.getFileName().toString().contains(".staging-"));
        try (FileChannel channel = FileChannel.open(namespace.resolve("lease.lock"), StandardOpenOption.WRITE)) {
            FileLock competing = channel.tryLock();
            check(competing != null, "post-lock initialization guard must pause before locking");
            if (competing != null) {
                competing.release();
            }
        }
        try {
            CommandResult reclaim = runCoordinator(outputRoot, List.of(
                    "--lock-root", lockRoot.toString(), "--reclaim",
                    namespace.resolve("lease.lock").toString()));
            check(reclaim.exitCode == 75, "live post-lock initializer reclaim must be contention");
            check(Files.isDirectory(namespace), "live post-lock initializer must not be renamed");
            check(!Files.exists(namespace.resolve("reclaiming.json")),
                    "live post-lock initializer must not leave a reclaim marker");
        } finally {
            process.kill();
        }
    }

    private static void verifySecondReclaimCheckPreventsLaunch(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-second-reclaim-check"));
        GuardedProcess process = startGuarded(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--guard", "locked",
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-must-not-run"), "locked");
        Path namespace = onlyEntry(lockRoot, path -> !path.getFileName().toString().contains(".staging-"));
        Path reclaiming = namespace.resolve("reclaiming.json");
        Files.writeString(reclaiming, "{\"pid\": 999999999, \"state\": \"reclaiming\"}\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        process.release();
        CommandResult result = process.finish();
        check(result.exitCode == 75, "post-lock reclaim marker must prevent child launch");
        assertRetrySchedule(result.output);
        check(!Files.exists(namespace.resolve("owner.json")), "owner must not publish after post-lock reclaim detection");
        check(Files.isRegularFile(namespace.resolve("lease.lock")), "failed namespace must retain lease.lock");
        check(Files.isRegularFile(reclaiming), "reclaim marker must survive retry exhaustion");
        check(!result.output.contains("CHILD_MUST_NOT_RUN"), "post-lock reclaim detection must not launch the child");
    }

    private static void verifyNormalContentionRetriesExactly(Path outputRoot, BasicRun first) throws Exception {
        CommandResult contender = runCoordinator(outputRoot, List.of(
                "--lock-root", first.lockRoot.toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-must-not-run"));
        check(contender.exitCode == 75, "existing owner namespace must exhaust with EX_TEMPFAIL");
        assertRetrySchedule(contender.output);
        check(!contender.output.contains("CHILD_MUST_NOT_RUN"), "normal contention must not launch the child");
        check(!contender.output.contains("OPENGGF_TEST_RUN_START"),
                "normal contention must not publish a Maven manifest");
    }

    private static String verifyInterruptedReclaimCanResume(Path root, Path outputRoot, BasicRun first)
            throws Exception {
        GuardedProcess reclaim = startGuarded(outputRoot, List.of(
                "--lock-root", first.lockRoot.toString(), "--reclaim", first.lease.toString(),
                "--guard", "reclaim-claimed"), "reclaim-claimed");
        Path reclaiming = first.lease.getParent().resolve("reclaiming.json");
        check(Files.isRegularFile(reclaiming), "explicit reclaim must atomically claim reclaiming.json");
        String reclaimJson = Files.readString(reclaiming);
        check(reclaimJson.contains("\"state\": \"reclaiming\""), "reclaim marker must record its state");
        reclaim.kill();
        check(Files.isRegularFile(reclaiming), "interrupted reclaim must retain its marker");

        CommandResult resumed = runCoordinator(outputRoot, List.of("--reclaim", first.lease.toString()));
        check(resumed.exitCode == 0, "dead recorded reclaimer must be resumable:\n" + resumed.output);
        check(!Files.exists(first.lease.getParent()), "successful reclaim must atomically rename the old namespace");
        Path recovered = onlyEntry(first.lockRoot,
                path -> path.getFileName().toString().contains(".recovered-"));
        check(Files.isRegularFile(recovered.resolve("reclaiming.json")), "renamed recovery namespace must retain marker");
        check(Files.isRegularFile(recovered.resolve("lease.lock")), "renamed recovery namespace must retain lease marker");
        check(Files.isRegularFile(recovered.resolve("initializing.json")),
                "renamed recovery namespace must retain initialization metadata");
        check(Files.isRegularFile(recovered.resolve("owner.json")), "renamed recovery namespace must retain owner metadata");

        CommandResult next = runCoordinator(outputRoot, List.of(
                "--lock-root", first.lockRoot.toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        check(next.exitCode == 0, "reclaimed namespace must allow a subsequent run:\n" + next.output);
        return markerValue(findLine(next.output, "OPENGGF_TEST_RUN_START"), "run_id");
    }

    private static void assertRetrySchedule(String output) {
        List<String> retryLines = output.lines().filter(line -> line.startsWith("OPENGGF_TEST_RETRY ")).toList();
        check(retryLines.size() == 3, "policy must perform exactly three retries after the initial attempt:\n" + output);
        check("50".equals(markerValue(retryLines.get(0), "delay_ms")), "first retry delay must be 50 ms");
        check("100".equals(markerValue(retryLines.get(1), "delay_ms")), "second retry delay must be 100 ms");
        check("200".equals(markerValue(retryLines.get(2), "delay_ms")), "third retry delay must be 200 ms");
    }

    private static CommandResult runCoordinator(Path outputRoot, List<String> arguments) throws Exception {
        Process process = coordinatorProcess(outputRoot, arguments).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        return new CommandResult(exit, output);
    }

    private static GuardedProcess startGuarded(Path outputRoot, List<String> arguments, String phase)
            throws Exception {
        Process process = coordinatorProcess(outputRoot, arguments).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        List<String> lines = new ArrayList<>();
        String expected = "OPENGGF_TEST_GUARD phase=" + phase;
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
            if (line.equals(expected)) {
                return new GuardedProcess(process, reader, lines);
            }
        }
        throw new AssertionError("coordinator exited before guard " + phase + ":\n" + String.join("\n", lines));
    }

    private static ProcessBuilder coordinatorProcess(Path outputRoot, List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add(javaCommand());
        command.add("-cp");
        command.add(classPath());
        command.add("TestSessionCoordinator");
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.environment().put("OPENGGF_TEST_ROOT", outputRoot.toString());
        builder.environment().put("MAVEN_OPTS", "-Dselftest.maven=preserved");
        builder.environment().put("JAVA_TOOL_OPTIONS", "-Dselftest.java=preserved");
        return builder;
    }

    private static CommandResult runStorageCoordinator(Path project, Path managedRoot, Path fakeBin,
                                                       Path childMarker, List<String> arguments)
            throws Exception {
        return finish(storageCoordinatorProcess(project, managedRoot, fakeBin, childMarker, arguments).start());
    }

    private static ProcessBuilder storageCoordinatorProcess(Path project, Path managedRoot, Path fakeBin,
                                                            Path childMarker, List<String> arguments) {
        ProcessBuilder builder = coordinatorProcess(project.resolve("unused-explicit-root"), arguments);
        builder.directory(project.toFile());
        builder.environment().remove("OPENGGF_TEST_ROOT");
        builder.environment().remove("AGENT_SCRATCH_ROOT");
        builder.environment().remove("OGGF_SCRATCH_ROOT");
        if (managedRoot != null) {
            builder.environment().put("AGENT_SCRATCH_ROOT", managedRoot.toString());
        }
        if (fakeBin != null) {
            builder.environment().put("PATH", fakeBin + java.io.File.pathSeparator
                    + builder.environment().getOrDefault("PATH", ""));
        }
        if (childMarker != null) {
            builder.environment().put("OPENGGF_TEST_CHILD_MARKER", childMarker.toString());
        }
        return builder;
    }

    private static CommandResult finish(Process process) throws Exception {
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new CommandResult(process.waitFor(), output);
    }

    private static Path createTestProject(Path project) throws Exception {
        Files.createDirectories(project);
        runProjectCommand(project, List.of("git", "init", "-q"));
        Files.writeString(project.resolve("tracked.txt"), "session storage policy test\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.writeString(project.resolve(".gitignore"), ".openggf/\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        runProjectCommand(project, List.of("git", "add", "tracked.txt", ".gitignore"));
        runProjectCommand(project, List.of("git", "-c", "user.name=OpenGGF Self Test",
                "-c", "user.email=self-test@openggf.invalid", "commit", "-q", "-m", "fixture"));
        return project.toAbsolutePath().normalize();
    }

    private static void runProjectCommand(Path directory, List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        check(exit == 0, "fixture command failed (" + String.join(" ", command) + "):\n" + output);
    }

    private static Path createFakeAgentScratch(Path fakeBin, String reservationJson) throws IOException {
        Files.createDirectories(fakeBin);
        Path helper = fakeBin.resolve("agent-scratch");
        String script = "#!/bin/sh\n"
                + "if [ \"$1\" = \"verify\" ]; then exit 0; fi\n"
                + "if [ \"$1\" = \"reserve-test-session\" ] && [ \"$2\" = \"--json\" ]; then\n"
                + "  printf '%s\\n' " + shellQuote(reservationJson) + "\n"
                + "  exit 0\n"
                + "fi\n"
                + "exit 64\n";
        Files.writeString(helper, script, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        helper.toFile().setExecutable(true, true);
        return fakeBin.toAbsolutePath().normalize();
    }

    private static Path createFakeAgentScratchFailure(Path fakeBin) throws IOException {
        Files.createDirectories(fakeBin);
        Path helper = fakeBin.resolve("agent-scratch");
        Files.writeString(helper, "#!/bin/sh\n"
                        + "if [ \"$1\" = \"verify\" ]; then exit 0; fi\n"
                        + "echo reservation-failed >&2\n"
                        + "exit 23\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        helper.toFile().setExecutable(true, true);
        return fakeBin.toAbsolutePath().normalize();
    }

    private static String reservationJson(Path managedRoot, Path allocation, Instant deadline) {
        return "{"
                + "\"schema_version\":1,"
                + "\"storage_tier\":\"MANAGED_CODEX_TEST_SESSIONS\","
                + "\"managed_root\":\"" + jsonEscape(managedRoot.toString()) + "\","
                + "\"allocation_path\":\"" + jsonEscape(allocation.toString()) + "\","
                + "\"filesystem_device\":" + filesystemDevice(allocation) + ","
                + "\"usable_bytes\":1048576,"
                + "\"total_bytes\":2097152,"
                + "\"usable_inodes\":1024,"
                + "\"retention_deadline\":\"" + deadline.toString().replace("Z", "+00:00") + "\","
                + "\"helper_version\":\"openggf-agent-scratch-v2\""
                + "}";
    }

    private static long filesystemDevice(Path path) {
        try {
            return ((Number) Files.getAttribute(path, "unix:dev")).longValue();
        } catch (IOException | UnsupportedOperationException e) {
            throw new AssertionError("self-test requires filesystem device identity", e);
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void runChild(String mode) {
        if (mode.equals("child-mark-start")) {
            try {
                Files.writeString(Path.of(System.getenv("OPENGGF_TEST_CHILD_MARKER")), "started\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            System.out.println("CHILD_STARTED_UNEXPECTEDLY");
            System.exit(92);
        }
        if (mode.equals("child-success")) {
            try {
                Path build = Path.of(System.getenv("OPENGGF_BUILD_DIRECTORY"));
                Files.createDirectories(build.resolve("native-libs"));
                Files.writeString(build.resolve("OpenGGF"), "native-binary\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                Files.writeString(build.resolve("native-libs/libopenggf-selftest.so"), "native-library\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                Files.writeString(build.resolve("libopenggf-selftest.so"), "native-library\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        if (mode.equals("child-mutate")) {
            try {
                Path worktree = Path.of(System.getenv("OPENGGF_TEST_WORKTREE"));
                Files.writeString(worktree.resolve(".session-selftest-mutation-"
                                + ProcessHandle.current().pid() + ".txt"), "mutation\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        if (mode.equals("child-mutate-runtime")) {
            try {
                Path runtimeInput = Path.of(System.getenv("OPENGGF_RUNTIME_INPUTS").split(
                        java.util.regex.Pattern.quote(java.io.File.pathSeparator))[0]);
                Files.writeString(runtimeInput, "after\n", StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        if (mode.equals("child-create-ignored")) {
            try {
                Path ignored = Path.of(System.getenv("OPENGGF_TEST_IGNORED_FILE"));
                Files.createDirectories(ignored.getParent());
                Files.writeString(ignored, "ignored\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        if (mode.equals("child-delete-lease")) {
            try {
                Path lease = Path.of(System.getenv("OPENGGF_TEST_LEASE"));
                Path namespace = lease.getParent();
                try (var paths = Files.walk(namespace)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        if (mode.equals("child-exit-7")) {
            System.exit(7);
        }
        if (mode.equals("child-must-not-run")) {
            System.out.println("CHILD_MUST_NOT_RUN");
            System.exit(91);
        }
        Map<String, String> environment = System.getenv();
        String tmpDir = environment.get("TMPDIR");
        check(tmpDir != null && tmpDir.equals(environment.get("TMP")) && tmpDir.equals(environment.get("TEMP")),
                "TMPDIR, TMP, and TEMP must identify one session directory");
        check(environment.getOrDefault("MAVEN_OPTS", "").contains("-Djava.io.tmpdir=")
                        && environment.getOrDefault("MAVEN_OPTS", "").contains(tmpDir),
                "MAVEN_OPTS must contain the session temp option");
        check(environment.getOrDefault("MAVEN_OPTS", "").contains("-Dselftest.maven=preserved"),
                "MAVEN_OPTS must preserve the caller's value");
        check(environment.getOrDefault("JAVA_TOOL_OPTIONS", "").contains("-Djava.io.tmpdir=")
                        && environment.getOrDefault("JAVA_TOOL_OPTIONS", "").contains(tmpDir),
                "JAVA_TOOL_OPTIONS must contain the session temp option");
        check(environment.getOrDefault("JAVA_TOOL_OPTIONS", "").contains("-Dselftest.java=preserved"),
                "JAVA_TOOL_OPTIONS must preserve the caller's value");
        check("worktree-session".equals(environment.get("OPENGGF_TEST_ISOLATION")),
                "child must receive the coordinator isolation policy");
        check(tmpDir.equals(environment.get("OPENGGF_TEST_TMP_ROOT")),
                "child must receive the session temp root");
        check((tmpDir + "/lwjgl-${surefire.forkNumber}").equals(
                        environment.get("OPENGGF_TEST_LWJGL_ROOT_TEMPLATE")),
                "child must receive the per-Surefire-fork LWJGL extraction template");
        if (mode.equals("child-sleep")) {
            try {
                Thread.sleep(Duration.ofSeconds(30).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (mode.equals("child-spawn-grandchild")) {
            try {
                Process grandchild = new ProcessBuilder(javaCommand(), "-cp", classPath(),
                        TestSessionCoordinatorSelfTest.class.getName(), "child-grandchild-sleep").start();
                Files.writeString(Path.of(System.getenv("OPENGGF_TEST_GRANDCHILD_PID_FILE")),
                        Long.toString(grandchild.pid()) + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                Thread.sleep(Duration.ofSeconds(30).toMillis());
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        if (mode.equals("child-grandchild-sleep")) {
            try {
                Thread.sleep(Duration.ofSeconds(30).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("CHILD_ENV_OK");
    }

    private static Path createOwnedDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        return path.toAbsolutePath().normalize();
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static Path onlyEntry(Path root, java.util.function.Predicate<Path> predicate) throws IOException {
        try (var paths = Files.list(root)) {
            List<Path> matches = paths.filter(predicate).sorted(Comparator.comparing(Path::toString)).toList();
            check(matches.size() == 1, "expected one matching entry under " + root + " but found " + matches);
            return matches.get(0);
        }
    }

    private static String findLine(String output, String prefix) {
        return output.lines().filter(line -> line.startsWith(prefix + " ")).findFirst()
                .orElseThrow(() -> new AssertionError("missing " + prefix + " marker:\n" + output));
    }

    private static String markerValue(String line, String key) {
        Matcher matcher = Pattern.compile("(?:^| )" + Pattern.quote(key) + "=([^ ]+)").matcher(line);
        if (!matcher.find()) {
            throw new AssertionError("marker missing " + key + ": " + line);
        }
        return matcher.group(1);
    }

    private static String jsonString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("JSON missing string key " + key + ":\n" + json);
        }
        return matcher.group(1).replace("\\\\", "\\").replace("\\\"", "\"");
    }

    private static String javaCommand() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String classPath() {
        return System.getProperty("java.class.path");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record CommandResult(int exitCode, String output) {
    }

    private record BasicRun(String runId, Path lease, Path lockRoot) {
    }

    private static final class GuardedProcess {
        private final Process process;
        private final BufferedReader reader;
        private final List<String> lines;

        private GuardedProcess(Process process, BufferedReader reader, List<String> lines) {
            this.process = process;
            this.reader = reader;
            this.lines = lines;
        }

        private void release() throws IOException {
            Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
            writer.write("continue\n");
            writer.flush();
        }

        private CommandResult finish() throws Exception {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            int exit = process.waitFor();
            return new CommandResult(exit, String.join("\n", lines) + "\n");
        }

        private void kill() throws Exception {
            process.destroyForcibly();
            if (!process.waitFor(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                throw new AssertionError("guarded coordinator did not terminate");
            }
        }
    }
}
