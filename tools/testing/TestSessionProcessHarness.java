import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** External-process acceptance harness for the isolated test-session coordinator. */
public final class TestSessionProcessHarness {
    private static final Duration START_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration FINISH_TIMEOUT = Duration.ofSeconds(45);
    private static final Pattern MANIFEST_MARKER = Pattern.compile("manifest=(.+?) lease=");

    private TestSessionProcessHarness() {
    }

    public static void main(String[] args) throws Exception {
        Path sourceRoot = args.length > 0
                ? Path.of(args[0]).toRealPath()
                : Path.of(System.getProperty("user.dir")).toRealPath();
        Path root = args.length > 1
                ? Path.of(args[1]).toAbsolutePath().normalize()
                : Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")),
                        "openggf-session-harness-");
        Files.createDirectories(root);
        Harness harness = new Harness(sourceRoot, root);
        try {
            harness.prepare();
            harness.runAll();
            System.out.println("TestSessionProcessHarness: PASS root=" + root);
        } catch (Throwable failure) {
            System.err.println("TestSessionProcessHarness: FAIL root=" + root);
            failure.printStackTrace(System.err);
            throw failure;
        }
    }

    private static final class Harness {
        private final Path sourceRoot;
        private final Path root;
        private final Path baseRepo;
        private final Path linkedRepo;
        private final Path fakeBin;
        private final Path outputRoot;
        private final Path externalLockRoot;
        private final Path sessionScript;
        private final boolean windows;

        private Harness(Path sourceRoot, Path root) {
            this.sourceRoot = sourceRoot;
            this.root = root;
            this.baseRepo = root.resolve("base-repo");
            this.linkedRepo = root.resolve("linked-worktree");
            this.fakeBin = root.resolve("fake-bin");
            this.outputRoot = root.resolve("sessions");
            this.externalLockRoot = root.resolve("external-lock-root");
            this.windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            this.sessionScript = baseRepo.resolve("tools/testing/")
                    .resolve(windows ? "test-session.ps1" : "test-session.sh");
        }

        private void prepare() throws Exception {
            Files.createDirectories(fakeBin);
            Files.createDirectories(outputRoot);
            Files.createDirectories(externalLockRoot);
            runGit(root, "init", "-q", baseRepo.toString());
            runGit(baseRepo, "config", "user.email", "session-harness@example.invalid");
            runGit(baseRepo, "config", "user.name", "OpenGGF session harness");
            copyTool("TestSessionCoordinator.java");
            copyTool("test-session.sh");
            copyTool("test-session.ps1");
            Path fixture = sourceRoot.resolve("tools/testing/fixtures/session-guard/pom.xml");
            if (Files.isRegularFile(fixture)) {
                Path destination = baseRepo.resolve("tools/testing/fixtures/session-guard/pom.xml");
                Files.createDirectories(destination.getParent());
                Files.copy(fixture, destination);
            }
            Files.writeString(baseRepo.resolve("tracked.txt"), "baseline\n", StandardCharsets.UTF_8);
            Files.writeString(baseRepo.resolve(".gitignore"), "*.gen\ntarget/\n", StandardCharsets.UTF_8);
            writeFakeMaven();
            runGit(baseRepo, "add", ".");
            runGit(baseRepo, "commit", "-qm", "session harness baseline");
            runGit(baseRepo, "worktree", "add", "--detach", linkedRepo.toString());
        }

        private void copyTool(String name) throws IOException {
            Path source = sourceRoot.resolve("tools/testing").resolve(name);
            Path destination = baseRepo.resolve("tools/testing").resolve(name);
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination);
            if (name.endsWith(".sh")) {
                destination.toFile().setExecutable(true);
            }
        }

        private void writeFakeMaven() throws IOException {
            Path script = fakeBin.resolve(windows ? "mvn.cmd" : "mvn");
            if (windows) {
                Files.writeString(script, """
                        @echo off
                        echo FAKE_MAVEN_STARTED>>%FAKE_MAVEN_MARKER%
                        if not "%FAKE_MAVEN_TEMP_MARKER%"=="" echo %TMPDIR%>%FAKE_MAVEN_TEMP_MARKER%
                        if not "%FAKE_MAVEN_WAIT%"=="" goto wait
                        :after_wait
                        if "%FAKE_MAVEN_MUTATION%"=="branch" git checkout -qb harness-branch-%RANDOM%
                        if "%FAKE_MAVEN_MUTATION%"=="head" git commit --allow-empty -qm harness-head
                        if "%FAKE_MAVEN_MUTATION%"=="tracked" echo changed>tracked.txt
                        if "%FAKE_MAVEN_MUTATION%"=="untracked" echo changed>existing-untracked.txt
                        if "%FAKE_MAVEN_MUTATION%"=="staged" (echo staged>tracked.txt & git add tracked.txt)
                        if "%FAKE_MAVEN_MUTATION%"=="runtime" echo runtime-mutated>runtime-input.gen
                        if not exist "%OPENGGF_TEST_DIAGNOSTICS%" mkdir "%OPENGGF_TEST_DIAGNOSTICS%"
                        echo %FAKE_MAVEN_MARKER%>"%OPENGGF_TEST_DIAGNOSTICS%\\collision-report.txt"
                        echo BUILD SUCCESS
                        exit /b 0
                        :wait
                        if exist "%FAKE_MAVEN_WAIT%" (ping -n 2 127.0.0.1>nul & goto wait)
                        goto after_wait
                        """, StandardCharsets.UTF_8);
                return;
            }
            Files.writeString(script, """
                    #!/usr/bin/env bash
                    set -eu
                    printf 'FAKE_MAVEN_STARTED\\n' >> "${FAKE_MAVEN_MARKER}"
                    if [ -n "${FAKE_MAVEN_TEMP_MARKER:-}" ]; then
                        printf '%s\\n' "${TMPDIR}" > "${FAKE_MAVEN_TEMP_MARKER}"
                    fi
                    if [ -n "${FAKE_MAVEN_WAIT:-}" ]; then
                        while [ -e "${FAKE_MAVEN_WAIT}" ]; do sleep 0.05; done
                    fi
                    case "${FAKE_MAVEN_MUTATION:-}" in
                      branch) git checkout -qb "harness-branch-${PPID}" ;;
                      head) git commit --allow-empty -qm harness-head ;;
                      tracked) printf 'changed\\n' > tracked.txt ;;
                      untracked) printf 'changed\\n' > existing-untracked.txt ;;
                      staged) printf 'staged\\n' > tracked.txt; git add tracked.txt ;;
                      runtime) printf 'runtime-mutated\\n' > runtime-input.gen ;;
                    esac
                    mkdir -p "${OPENGGF_TEST_DIAGNOSTICS}"
                    printf '%s\\n' "${FAKE_MAVEN_MARKER}" > "${OPENGGF_TEST_DIAGNOSTICS}/collision-report.txt"
                    printf 'BUILD SUCCESS\\n'
                    exit "${FAKE_MAVEN_EXIT:-0}"
                    """, StandardCharsets.UTF_8);
            script.toFile().setExecutable(true);
        }

        private void runAll() throws Exception {
            capacityRefusalPreventsLaunch();
            liveProbeFailurePreventsLaunch();
            completionProbeFailurePreservesPrimaryFailure();
            markerFieldsAreEncoded();
            sameWorktreeContention();
            linkedWorktreesRunIndependently();
            systemTempIsNotUsed();
            inWorktreeLockRootIsRejected();
            reportRootsAreIsolated();
            mutationIsInvalid("branch", "branch");
            mutationIsInvalid("head", "head");
            mutationIsInvalid("tracked", "tracked");
            mutationIsInvalid("untracked", "untracked");
            mutationIsInvalid("staged", "staged");
            runtimeInputMutationIsInvalid();
            interruptionAndReclaim();
            rawLifecycleIsRejected();
            activeSessionSurvivesRawCleanAttempt();
        }

        private void capacityRefusalPreventsLaunch() throws Exception {
            SessionProcess process = start(baseRepo, "capacity-refusal", externalLockRoot,
                    null, null, List.of(), null,
                    Map.of("OPENGGF_TEST_MIN_FREE_BYTES", Long.toString(Long.MAX_VALUE)));
            int exit = process.finish();
            check(exit != 0, "low-capacity session must fail startup");
            check(!Files.exists(root.resolve("markers/capacity-refusal.txt")),
                    "low-capacity session must not start fake Maven");
            String output = process.output();
            Matcher matcher = MANIFEST_MARKER.matcher(output.lines()
                    .filter(line -> line.startsWith("OPENGGF_TEST_RUN_START "))
                    .findFirst().orElseThrow(() -> new AssertionError(
                            "capacity refusal did not publish a start marker:\n" + output)));
            check(matcher.find(), "capacity-refusal marker lacks manifest path");
            Path manifest = Path.of(matcher.group(1));
            check(jsonString(manifest, "state").equals("STARTUP_FAILED"),
                    "capacity refusal must persist STARTUP_FAILED");
            check(jsonString(manifest, "storage_tier").equals("EXPLICIT_OVERRIDE"),
                    "capacity refusal must persist its storage tier");
            check(Files.isRegularFile(manifest.getParent().resolve("command.txt")),
                    "capacity refusal must preserve command.txt");
        }

        private void liveProbeFailurePreventsLaunch() throws Exception {
            SessionProcess process = start(baseRepo, "live-probe-refusal", externalLockRoot,
                    null, null, List.of(), null,
                    Map.of("OPENGGF_TEST_LIVE_PROBE_FAILURE_PHASE", "launch"));
            Path manifest = process.awaitManifest();
            check(process.finish() != 0, "failed launch live probe must fail startup");
            check(!Files.exists(root.resolve("markers/live-probe-refusal.txt")),
                    "failed launch live probe must not start fake Maven");
            check(jsonString(manifest, "state").equals("STARTUP_FAILED"),
                    "failed launch live probe must persist STARTUP_FAILED");
            check(jsonString(manifest, "launch_inode_probe_status").equals("FAILED"),
                    "failed launch live probe must be observable");
        }

        private void completionProbeFailurePreservesPrimaryFailure() throws Exception {
            SessionProcess process = start(baseRepo, "completion-probe-failure", externalLockRoot,
                    null, null, List.of(), null, Map.of(
                            "OPENGGF_TEST_LIVE_PROBE_FAILURE_PHASE", "completion",
                            "FAKE_MAVEN_EXIT", "7"));
            Path manifest = process.awaitManifest();
            check(process.finish() == 7,
                    "completion probe failure must preserve the primary fake-Maven exit");
            check(jsonString(manifest, "state").equals("FAILED"),
                    "completion probe failure must preserve FAILED state");
            check(!Files.readString(manifest).contains("\"state\": \"RUNNING\""),
                    "completion probe failure must not strand the manifest in RUNNING");
        }

        private void markerFieldsAreEncoded() throws Exception {
            Path lockRoot = root.resolve(
                    "marker-lock\nOPENGGF_TEST_RUN_START run_id=counterfeit");
            Files.createDirectories(lockRoot);
            SessionProcess process = start(baseRepo, "marker-encoding", lockRoot,
                    null, null, List.of());
            process.awaitManifest();
            check(process.finish() == 0, "encoded marker field run must succeed");
            String output = process.output();
            check(output.lines().filter(line -> line.startsWith("OPENGGF_TEST_RUN_START ")).count() == 1,
                    "lock path must not forge a start marker:\n" + output);
            check(output.lines().filter(line -> line.startsWith("OPENGGF_TEST_RUN_END ")).count() == 1,
                    "lock path must not forge an end marker:\n" + output);
            check(output.lines().filter(line -> line.startsWith("OPENGGF_TEST_RUN_START "))
                            .findFirst().orElseThrow().contains("%0A"),
                    "lock-path newline must be encoded in the marker");
        }

        private void sameWorktreeContention() throws Exception {
            Path wait = root.resolve("wait-contention");
            Files.createFile(wait);
            SessionProcess owner = start(baseRepo, "contention-owner", externalLockRoot,
                    wait, null, List.of());
            Path ownerManifest = owner.awaitManifest();
            SessionProcess loser = start(baseRepo, "contention-loser", externalLockRoot,
                    null, null, List.of());
            int loserExit = loser.finish();
            check(loserExit != 0, "contending session must not start or pass");
            check(!loser.output().contains("FAKE_MAVEN_STARTED"),
                    "contending session must exit before fake Maven starts");
            Files.deleteIfExists(wait);
            check(owner.finish() == 0, "owner session must complete after contention test");
            check(Files.isRegularFile(ownerManifest), "owner manifest must survive contention");
        }

        private void linkedWorktreesRunIndependently() throws Exception {
            Path waitOne = root.resolve("wait-linked-one");
            Path waitTwo = root.resolve("wait-linked-two");
            Files.createFile(waitOne);
            Files.createFile(waitTwo);
            SessionProcess first = start(baseRepo, "linked-one", externalLockRoot,
                    waitOne, null, List.of());
            SessionProcess second = start(linkedRepo, "linked-two", externalLockRoot,
                    waitTwo, null, List.of());
            Path firstManifest = first.awaitManifest();
            Path secondManifest = second.awaitManifest();
            check(!firstManifest.getParent().equals(secondManifest.getParent()),
                    "linked worktrees must receive different session roots");
            Files.deleteIfExists(waitOne);
            Files.deleteIfExists(waitTwo);
            check(first.finish() == 0 && second.finish() == 0,
                    "linked worktree sessions must both complete");
        }

        private void systemTempIsNotUsed() throws Exception {
            Path marker = root.resolve("temp-marker");
            SessionProcess process = start(baseRepo, "system-temp", externalLockRoot,
                    null, marker, List.of());
            Path manifest = process.awaitManifest();
            check(process.finish() == 0, "session with unusable inherited temp must complete");
            String sessionTemp = jsonString(manifest, "tmp_root");
            check(Files.readString(marker).trim().equals(sessionTemp),
                    "child temp directory must be coordinator-owned: " + Files.readString(marker));
        }

        private void inWorktreeLockRootIsRejected() throws Exception {
            Path inside = baseRepo.resolve(".session-lock-inside");
            Files.createDirectories(inside);
            SessionProcess process = start(baseRepo, "inside-lock", inside,
                    null, null, List.of());
            int exit = process.finish();
            check(exit != 0, "an external lock root inside the worktree must be rejected");
            check(!process.output().contains("FAKE_MAVEN_STARTED"),
                    "rejected lock root must not start fake Maven");
        }

        private void reportRootsAreIsolated() throws Exception {
            Path first = runSimple("reports-one", List.of());
            Path second = runSimple("reports-two", List.of());
            String firstReport = jsonArrayContaining(first, "collision-report.txt");
            String secondReport = jsonArrayContaining(second, "collision-report.txt");
            check(!firstReport.equals(secondReport), "reports from separate sessions must not collide");
            check(firstReport.startsWith(jsonString(first, "diagnostics_root")),
                    "first report must be below its diagnostics root");
            check(secondReport.startsWith(jsonString(second, "diagnostics_root")),
                    "second report must be below its diagnostics root");
        }

        private void mutationIsInvalid(String label, String mutation) throws Exception {
            Path repo = cloneRepo("mutation-" + label);
            if (mutation.equals("untracked")) {
                Files.writeString(repo.resolve("existing-untracked.txt"), "before\n", StandardCharsets.UTF_8);
            }
            Path manifest = runSimple(repo, "mutation-" + label, List.of(), mutation);
            check(jsonString(manifest, "state").equals("INVALID_IDENTITY_CHANGED"),
                    mutation + " mutation must invalidate the session");
        }

        private void runtimeInputMutationIsInvalid() throws Exception {
            Path repo = cloneRepo("mutation-runtime");
            Path input = repo.resolve("runtime-input.gen");
            Files.writeString(input, "runtime-before\n", StandardCharsets.UTF_8);
            Files.writeString(repo.resolve(".git/info/exclude"), "runtime-input.gen\n",
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            Path manifest = runSimple(repo, "mutation-runtime", List.of(
                    "-Dtest.runtime.path=" + input), "runtime");
            check(jsonString(manifest, "state").equals("INVALID_IDENTITY_CHANGED"),
                    "declared ignored runtime-input mutation must invalidate the session");
        }

        private void interruptionAndReclaim() throws Exception {
            Path wait = root.resolve("wait-interrupt");
            Files.createFile(wait);
            SessionProcess interrupted = start(baseRepo, "interrupted", externalLockRoot,
                    wait, null, List.of());
            Path manifest = interrupted.awaitManifest();
            interrupted.process().destroy();
            interrupted.finish();
            Files.deleteIfExists(wait);
            String state = jsonString(manifest, "state");
            check(state.equals("ABORTED") || state.equals("RUNNING"),
                    "interrupted session must retain a terminal or recoverable manifest: " + state);
            Path reclaimed = runSimple(baseRepo, "reclaimed", List.of("--reuse-stale"));
            check(jsonString(reclaimed, "state").equals("PASSED"),
                    "a stale session lease must be reclaimable");
        }

        private void rawLifecycleIsRejected() throws Exception {
            Path fixture = baseRepo.resolve("tools/testing/fixtures/session-guard/pom.xml");
            check(Files.isRegularFile(fixture), "session-guard fixture must be installed in harness repo");
            for (String phase : List.of("clean", "compile", "test-compile", "test", "verify", "package")) {
                ProcessBuilder builder = new ProcessBuilder("mvn", "-f", fixture.toString(), phase)
                        .directory(baseRepo.toFile())
                        .redirectErrorStream(true);
                builder.environment().put("PATH", System.getenv().getOrDefault("PATH", ""));
                Process process = builder.start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exit = process.waitFor();
                check(exit != 0, "raw Maven " + phase + " must be rejected: " + output);
                check(!output.contains("SESSION_GUARD_MARKER"),
                        "raw Maven " + phase + " must fail before the fixture marker changes");
            }
        }

        private void activeSessionSurvivesRawCleanAttempt() throws Exception {
            Path marker = baseRepo.resolve("target/SESSION_GUARD_MARKER");
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "keep\n", StandardCharsets.UTF_8);
            Path wait = root.resolve("wait-active-clean");
            Files.createFile(wait);
            SessionProcess active = start(baseRepo, "active-clean", externalLockRoot,
                    wait, null, List.of());
            active.awaitManifest();
            ProcessBuilder builder = new ProcessBuilder("mvn", "-f",
                    baseRepo.resolve("tools/testing/fixtures/session-guard/pom.xml").toString(), "clean")
                    .directory(baseRepo.toFile()).redirectErrorStream(true);
            builder.environment().put("PATH", System.getenv().getOrDefault("PATH", ""));
            Process clean = builder.start();
            int exit = clean.waitFor();
            check(exit != 0, "raw clean must not run while a supported session is active");
            check(Files.isRegularFile(marker), "active session target marker must survive raw clean");
            Files.deleteIfExists(wait);
            check(active.finish() == 0, "active session must complete after raw clean rejection");
        }

        private Path runSimple(String label, List<String> options) throws Exception {
            return runSimple(baseRepo, label, options, null);
        }

        private Path runSimple(Path repo, String label, List<String> options) throws Exception {
            return runSimple(repo, label, options, null);
        }

        private Path runSimple(Path repo, String label, List<String> options, String mutation)
                throws Exception {
            SessionProcess process = start(repo, label, externalLockRoot, null, null,
                    options, mutation);
            Path manifest = process.awaitManifest();
            check(process.finish() == 0 || jsonString(manifest, "state").equals("INVALID_IDENTITY_CHANGED"),
                    "session did not complete as expected: " + process.output());
            return manifest;
        }

        private SessionProcess start(Path repo, String label, Path lockRoot, Path wait,
                                     Path tempMarker, List<String> options) throws IOException {
            return start(repo, label, lockRoot, wait, tempMarker, options, null);
        }

        private SessionProcess start(Path repo, String label, Path lockRoot, Path wait,
                                     Path tempMarker, List<String> options, String mutation)
                throws IOException {
            return start(repo, label, lockRoot, wait, tempMarker, options, mutation, Map.of());
        }

        private SessionProcess start(Path repo, String label, Path lockRoot, Path wait,
                                     Path tempMarker, List<String> options, String mutation,
                                     Map<String, String> extraEnvironment)
                throws IOException {
            Path marker = root.resolve("markers").resolve(label + ".txt");
            Files.createDirectories(marker.getParent());
            Files.deleteIfExists(marker);
            List<String> command = new ArrayList<>();
            if (windows) {
                command.add("powershell");
                command.add("-NoProfile");
                command.add("-ExecutionPolicy");
                command.add("Bypass");
                command.add("-File");
            }
            command.add(sessionScript.toString());
            command.addAll(List.of("--lock-root", lockRoot.toString(), "--", "mvn"));
            command.addAll(options);
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(repo.toFile()).redirectErrorStream(true);
            Map<String, String> environment = builder.environment();
            environment.put("PATH", fakeBin + java.io.File.pathSeparator
                    + System.getenv().getOrDefault("PATH", ""));
            environment.put("OPENGGF_TEST_ROOT", outputRoot.resolve(label).toString());
            environment.put("FAKE_MAVEN_MARKER", marker.toString());
            environment.put("FAKE_MAVEN_WAIT", wait == null ? "" : wait.toString());
            environment.put("FAKE_MAVEN_TEMP_MARKER", tempMarker == null ? "" : tempMarker.toString());
            environment.put("FAKE_MAVEN_MUTATION", mutation == null ? "" : mutation);
            environment.putAll(extraEnvironment);
            if (label.equals("system-temp")) {
                environment.put("TMPDIR", "/dev/null");
                environment.put("TMP", "/dev/null");
                environment.put("TEMP", "/dev/null");
            }
            return new SessionProcess(builder.start());
        }

        private Path cloneRepo(String label) throws Exception {
            Path clone = root.resolve(label);
            runGit(root, "clone", "-q", baseRepo.toString(), clone.toString());
            runGit(clone, "config", "user.email", "session-harness@example.invalid");
            runGit(clone, "config", "user.name", "OpenGGF session harness");
            return clone;
        }
    }

    private static final class SessionProcess {
        private final Process process;
        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final StringBuilder output = new StringBuilder();

        private SessionProcess(Process process) {
            this.process = process;
            Thread reader = new Thread(() -> readOutput(), "session-harness-output");
            reader.setDaemon(true);
            reader.start();
        }

        private void readOutput() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (output) {
                        output.append(line).append('\n');
                    }
                    lines.offer(line);
                }
            } catch (IOException e) {
                synchronized (output) {
                    output.append("HARNESS_READER_ERROR ").append(e).append('\n');
                }
            }
        }

        private Path awaitManifest() throws Exception {
            long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                long remaining = deadline - System.nanoTime();
                String line = lines.poll(Math.max(1, remaining), TimeUnit.NANOSECONDS);
                if (line == null) {
                    break;
                }
                if (line.startsWith("OPENGGF_TEST_RUN_START ")) {
                    Matcher matcher = MANIFEST_MARKER.matcher(line);
                    check(matcher.find(), "start marker lacks manifest path: " + line);
                    return Path.of(URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8));
                }
            }
            throw new AssertionError("session did not start:\n" + output());
        }

        private int finish() throws Exception {
            if (!process.waitFor(FINISH_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("session did not finish:\n" + output());
            }
            return process.exitValue();
        }

        private Process process() {
            return process;
        }

        private String output() {
            synchronized (output) {
                return output.toString();
            }
        }
    }

    private static String jsonString(Path manifest, String key) throws IOException {
        String json = Files.readString(manifest, StandardCharsets.UTF_8);
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("manifest missing string key " + key + ": " + manifest);
        }
        return matcher.group(1).replace("\\\\", "\\").replace("\\\"", "\"");
    }

    private static String jsonArrayContaining(Path manifest, String fragment) throws IOException {
        String json = Files.readString(manifest, StandardCharsets.UTF_8);
        int start = json.indexOf("\"reports\":");
        int end = json.indexOf(']', start);
        check(start >= 0 && end > start, "manifest has no reports array: " + manifest);
        String array = json.substring(start, end + 1);
        int fragmentIndex = array.indexOf(fragment);
        check(fragmentIndex >= 0, "manifest reports do not contain " + fragment + ": " + manifest);
        int quoteStart = array.lastIndexOf('"', fragmentIndex);
        int quoteEnd = array.indexOf('"', fragmentIndex + fragment.length());
        return array.substring(quoteStart + 1, quoteEnd).replace("\\\\", "\\");
    }

    private static String runGit(Path directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new AssertionError("git command failed: " + command + "\n" + output);
        }
        return output;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
