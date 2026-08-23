import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standalone coordinator for isolated OpenGGF test/build sessions. */
public final class TestSessionCoordinator {
    private static final int EX_TEMPFAIL = 75;
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {50, 100, 200};
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private TestSessionCoordinator() {
    }

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.guard != null && isLifecycleGuard(options.guard)) {
                System.exit(guard(options.guard));
            }
            if (options.debugGuard != null) {
                System.exit(debugGuard(options));
            }
            if (options.reclaim != null) {
                System.exit(reclaim(options));
            }
            System.exit(run(options));
        } catch (StartupFailure e) {
            System.err.println("OPENGGF_TEST_SESSION_ERROR " + e.getMessage());
            System.exit(e.exitCode);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static int run(Options options) throws Exception {
        Path worktree = worktree();
        Path outputRoot = resolveOutputRoot(worktree, options.allowSystemTmp);
        Files.createDirectories(outputRoot);
        Path lockParent = resolveLockParent(worktree, options.lockRoot);
        String runId = createRunId();
        Path namespace = namespace(lockParent, worktree, externalLockRequested(options));
        Lease lease = acquireLease(lockParent, namespace, worktree, runId, options.command,
                options.debugGuard == null);
        if (lease == null) {
            System.err.println("OPENGGF_TEST_SESSION_CONTENTION run_id=" + runId + " reason=lease");
            return EX_TEMPFAIL;
        }

        Path session = Files.createDirectory(outputRoot.resolve(runId));
        Paths paths = Paths.create(session);
        Path leasePath = lease.namespace.resolve("lease.lock");
        String commandHash = sha256(String.join("\0", options.command));
        String allowedPhases = allowedPhases(options.command);
        String capability = writeCapability(session, runId, commandHash, worktree, leasePath,
                allowedPhases);
        String sourceBefore = sourceDigest(worktree);
        String runtimeBefore = runtimeDigest(options.command);
        writeOwner(lease.namespace.resolve("owner.json"), runId, worktree, leasePath,
                options.command, commandHash, "owner");
        writeManifest(paths.manifest, manifest(paths, runId, "RUNNING", worktree, leasePath,
                commandHash, capability, allowedPhases, sourceBefore, runtimeBefore,
                List.of(), List.of()));
        System.out.println("OPENGGF_TEST_RUN_START run_id=" + runId + " manifest="
                + paths.manifest + " lease=" + leasePath);

        ShutdownState shutdown = new ShutdownState(paths, runId, worktree, leasePath,
                sourceBefore, runtimeBefore, options.command, commandHash, capability,
                allowedPhases, options.exportFile, lease);
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown::abort, "openggf-test-session-shutdown"));
        Process child = null;
        int exitCode = 1;
        boolean interrupted = false;
        boolean identityChanged = false;
        try {
            Map<String, String> environment = new java.util.HashMap<>(System.getenv());
            Map<String, String> sessionProperties = sessionProperties(paths, runId, capability,
                    commandHash, worktree, options.command, leasePath);
            configureEnvironment(environment, paths.tmp, sessionProperties,
                    !options.command.isEmpty() && isMavenExecutable(options.command.get(0)));
            ProcessBuilder builder = new ProcessBuilder(sessionCommand(options.command, sessionProperties))
                    .directory(worktree.toFile())
                    .redirectErrorStream(true);
            builder.environment().putAll(environment);
            child = builder.start();
            shutdown.child = child;
            try (Reader reader = new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8);
                 Writer log = Files.newBufferedWriter(paths.mavenLog, StandardCharsets.UTF_8,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    if (count == 0) {
                        continue;
                    }
                    String text = new String(buffer, 0, count);
                    log.write(text);
                    log.flush();
                    System.out.print(text);
                }
            }
            exitCode = child.waitFor();
        } catch (InterruptedException e) {
            interrupted = true;
            if (child != null) {
                child.destroyForcibly();
            }
            Thread.currentThread().interrupt();
        } finally {
            String sourceAfter = sourceDigest(worktree);
            String runtimeAfter = runtimeDigest(options.command);
            boolean sourceChanged = !sourceBefore.equals(sourceAfter);
            boolean runtimeChanged = !runtimeBefore.equals(runtimeAfter);
            boolean leaseChanged = !leaseStillOwned(lease.namespace, leasePath, runId);
            identityChanged = sourceChanged || runtimeChanged || leaseChanged;
            boolean valid = !identityChanged && !interrupted;
            String state = interrupted ? "ABORTED"
                    : (identityChanged ? "INVALID_IDENTITY_CHANGED"
                    : (exitCode == 0 ? "PASSED" : "FAILED"));
            writeManifest(paths.manifest, manifest(paths, runId, state, worktree, leasePath,
                    commandHash, capability, allowedPhases, sourceAfter, runtimeAfter,
                    List.of(), List.of()));
            if (options.exportFile != null) {
                writeExport(options.exportFile, paths.manifest, runId);
            }
            System.out.println("OPENGGF_TEST_RUN_END run_id=" + runId + " exit_code=" + exitCode
                    + " state=" + state + " valid=" + valid + " manifest=" + paths.manifest);
            shutdown.completed = true;
            lease.close();
        }
        return interrupted || identityChanged
                ? (exitCode == 0 ? 1 : exitCode) : exitCode;
    }

    private static int debugGuard(Options options) throws Exception {
        Path worktree = worktree();
        Path outputRoot = resolveOutputRoot(worktree, options.allowSystemTmp);
        Path lockParent = resolveLockParent(worktree, options.lockRoot);
        String runId = createRunId();
        Path namespace = namespace(lockParent, worktree, externalLockRequested(options));
        if (options.debugGuard.equals("staged")) {
            Path staging = createStaging(lockParent, runId, worktree, namespace.resolve("lease.lock"), options.command);
            System.out.println("OPENGGF_TEST_GUARD phase=staged");
            waitForContinue();
            return 0;
        }
        if (options.debugGuard.equals("initialized")) {
            Lease lease = publishInitialization(lockParent, namespace, runId, worktree, options.command);
            System.out.println("OPENGGF_TEST_GUARD phase=initialized");
            waitForContinue();
            return 0;
        }
        if (options.reclaim != null || options.debugGuard.equals("reclaim-claimed")) {
            Path target = options.reclaim == null
                    ? namespace(lockParent, worktree, externalLockRequested(options))
                    : options.reclaim.getParent();
            Files.writeString(target.resolve("reclaiming.json"), reclaimJson(runId, target),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            System.out.println("OPENGGF_TEST_GUARD phase=reclaim-claimed");
            waitForContinue();
            return 0;
        }
        Lease target = publishInitialization(lockParent, namespace, runId, worktree, options.command);
        Path lockPath = target.namespace.resolve("lease.lock");
        Files.createFile(lockPath);
        if (options.debugGuard.equals("lease-created")) {
            System.out.println("OPENGGF_TEST_GUARD phase=lease-created");
            waitForContinue();
            return 0;
        }
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
        FileLock fileLock = channel.tryLock();
        if (fileLock == null) {
            channel.close();
            return contention(runId, "lease");
        }
        if (options.debugGuard.equals("owner")) {
            writeOwner(target.namespace.resolve("owner.json"), runId, worktree,
                    target.namespace.resolve("lease.lock"),
                    options.command, sha256(String.join("\0", options.command)), "owner");
        }
        System.out.println("OPENGGF_TEST_GUARD phase=" + options.debugGuard);
        waitForContinue();
        if (Files.exists(target.namespace.resolve("reclaiming.json"))) {
            fileLock.release();
            channel.close();
            return contention(runId, "reclaiming");
        }
        if (options.debugGuard.equals("owner")) {
            fileLock.release();
            channel.close();
            return 0;
        }
        fileLock.release();
        channel.close();
        return 0;
    }

    private static int reclaim(Options options) throws Exception {
        Path leasePath = options.reclaim.toAbsolutePath().normalize();
        Path namespace = leasePath.getParent();
        if (!isLeaseNamespace(leasePath, namespace, worktree())) {
            throw new StartupFailure("reclaim target is not an OpenGGF lease namespace: " + leasePath, 1);
        }
        String runId = createRunId();
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                retryNotice(runId, RETRY_DELAYS_MS[attempt - 1]);
            }
            Path marker = namespace.resolve("reclaiming.json");
            Path owner = namespace.resolve("owner.json");
            Path initializing = namespace.resolve("initializing.json");
            Path processTree = namespace.resolve("process-tree-active.json");
            if (Files.exists(processTree) && recordedProcessAlive(processTree)) {
                continue;
            }
            FileChannel channel = null;
            FileLock lock = null;
            try {
                boolean hasLease = Files.exists(leasePath);
                if (hasLease) {
                    channel = FileChannel.open(leasePath, StandardOpenOption.WRITE);
                    lock = channel.tryLock();
                    if (lock == null) {
                        continue;
                    }
                    if (Files.exists(owner) && recordedProcessAlive(owner)) {
                        continue;
                    }
                    if (!Files.exists(owner) && Files.exists(initializing)
                            && recordedProcessAlive(initializing)) {
                        continue;
                    }
                }
                if (!Files.exists(marker)) {
                    if (!hasLease && Files.exists(initializing) && recordedProcessAlive(initializing)) {
                        continue;
                    }
                    if (!hasLease && Files.exists(owner) && recordedProcessAlive(owner)) {
                        continue;
                    }
                    try {
                        // With a lease file present, the recovery claim is made while
                        // holding the same lock that protects the owner namespace.
                        Files.writeString(marker, reclaimJson(runId, namespace), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    } catch (FileAlreadyExistsException ignored) {
                        continue;
                    }
                } else if (!markerBelongsTo(marker, runId) && recordedProcessAlive(marker)) {
                    continue;
                }
                Path recovered = namespace.resolveSibling(
                        namespace.getFileName() + ".recovered-" + runId);
                if (lock != null) {
                    lock.release();
                    lock = null;
                }
                if (channel != null) {
                    channel.close();
                    channel = null;
                }
                moveAtomic(namespace, recovered);
                return 0;
            } catch (OverlappingFileLockException | IOException e) {
                if (attempt == MAX_RETRIES) {
                    break;
                }
            } finally {
                if (lock != null) {
                    lock.release();
                }
                if (channel != null) {
                    channel.close();
                }
            }
        }
        return EX_TEMPFAIL;
    }

    private static int guard(String phase) throws Exception {
        try {
            String manifest = System.getProperty("openggf.session.manifest");
            String capability = System.getProperty("openggf.session.capability");
            String runId = System.getProperty("openggf.session.run-id");
            String commandHash = System.getProperty("openggf.session.command-hash");
            String worktree = System.getProperty("openggf.session.worktree");
            String lease = System.getProperty("openggf.session.lease-path");
            String allowed = System.getProperty("openggf.session.allowed-phases");
            if (List.of(manifest, capability, runId, commandHash, worktree, lease, allowed)
                    .stream().anyMatch(v -> v == null || v.isBlank())) {
                return guardReject("missing session identity properties");
            }
            if (!isLifecycleGuard(phase) || !Arrays.asList(allowed.split(",")).contains(phase)) {
                return guardReject("phase " + phase + " is not allowed");
            }
            if (!commandHash.matches("[0-9a-fA-F]{64}")) {
                return guardReject("command hash is not SHA-256");
            }
            Path manifestPath = Path.of(manifest).toAbsolutePath().normalize();
            Path capabilityPath = Path.of(capability).toAbsolutePath().normalize();
            Path leasePath = Path.of(lease).toAbsolutePath().normalize();
            Path declaredWorktree = Path.of(worktree).toRealPath();
            if (!Files.isRegularFile(manifestPath) || !Files.isRegularFile(capabilityPath)
                    || !Files.isRegularFile(leasePath)) {
                return guardReject("session files are not regular files");
            }
            if (!declaredWorktree.equals(worktree().toRealPath())) {
                return guardReject("worktree identity mismatch");
            }
            String manifestJson = Files.readString(manifestPath, StandardCharsets.UTF_8);
            String capabilityText = Files.readString(capabilityPath, StandardCharsets.UTF_8);
            if (!hasJson(manifestJson, "run_id", runId)
                    || !hasJson(manifestJson, "state", "RUNNING")
                    || !hasJson(manifestJson, "worktree", declaredWorktree.toString())
                    || !hasJson(manifestJson, "lease_path", leasePath.toString())
                    || !hasJson(manifestJson, "command_hash", commandHash)
                    || !hasJson(manifestJson, "capability", capabilityPath.toString())
                    || !hasJson(manifestJson, "allowed_phases", allowed)) {
                return guardReject("manifest identity mismatch");
            }
            if (!capabilityText.contains("run_id=" + runId + "\n")
                    || !capabilityText.contains("command_hash=" + commandHash + "\n")
                    || !capabilityText.contains("worktree=" + declaredWorktree + "\n")
                    || !capabilityText.contains("lease_path=" + leasePath + "\n")
                    || !capabilityText.contains("allowed_phases=" + allowed + "\n")) {
                return guardReject("capability identity mismatch");
            }
            return 0;
        } catch (Exception e) {
            return guardReject(e.getMessage() == null ? "invalid session identity" : e.getMessage());
        }
    }

    private static int guardReject(String reason) {
        System.err.println("session guard rejected: " + reason);
        return 1;
    }

    private static boolean hasJson(String json, String key, String value) {
        return json.contains("\"" + key + "\": \"" + escape(value) + "\"");
    }

    private static Lease acquireLease(Path lockParent, Path namespace, Path worktree,
                                      String runId, List<String> command, boolean publish)
            throws Exception {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                retryNotice(runId, RETRY_DELAYS_MS[attempt - 1]);
            }
            Lease lease = tryAcquireLease(lockParent, namespace, worktree, runId, command, publish);
            if (lease != null) {
                return lease;
            }
        }
        return null;
    }

    private static Lease tryAcquireLease(Path lockParent, Path namespace, Path worktree,
                                         String runId, List<String> command, boolean publish)
            throws Exception {
        if (Files.exists(namespace)) {
            if (Files.exists(namespace.resolve("reclaiming.json"))) {
                return null;
            }
            if (Files.exists(namespace.resolve("initializing.json"))
                    && !Files.exists(namespace.resolve("owner.json"))) {
                return null;
            }
            // A published namespace is retained after a run. It is only reused after
            // explicit --reclaim, so a later coordinator cannot silently take it over.
            return null;
        }
        if (!publish) {
            return null;
        }
        Path staging;
        try {
            staging = createStaging(lockParent, runId, worktree, namespace.resolve("lease.lock"), command);
            moveAtomic(staging, namespace);
        } catch (FileAlreadyExistsException e) {
            return null;
        }
        Path lockPath = namespace.resolve("lease.lock");
        try {
            Files.createFile(lockPath);
        } catch (FileAlreadyExistsException e) {
            return null;
        }
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            channel.close();
            return null;
        }
        if (lock == null) {
            channel.close();
            return null;
        }
        if (Files.exists(namespace.resolve("reclaiming.json"))) {
            lock.release();
            channel.close();
            return null;
        }
        return new Lease(namespace, channel, lock);
    }

    private static Lease publishInitialization(Path lockParent, Path namespace, String runId,
                                               Path worktree, List<String> command) throws Exception {
        if (Files.exists(namespace)) {
            return new Lease(namespace, null, null);
        }
        Path staging = createStaging(lockParent, runId, worktree, namespace.resolve("lease.lock"), command);
        moveAtomic(staging, namespace);
        return new Lease(namespace, null, null);
    }

    private static Path createStaging(Path parent, String runId, Path worktree, Path leasePath,
                                      List<String> command) throws IOException {
        Files.createDirectories(parent);
        Path staging = Files.createDirectory(parent.resolve(".staging-" + runId));
        writeOwner(staging.resolve("initializing.json"), runId, worktree, leasePath,
                command, sha256(String.join("\0", command)), "initializing");
        return staging;
    }

    private static boolean isLeaseNamespace(Path leasePath, Path namespace, Path worktree) throws IOException {
        if (namespace == null || !leasePath.getFileName().toString().equals("lease.lock")
                || !Files.isDirectory(namespace) || Files.isSymbolicLink(namespace)) {
            return false;
        }
        String name = namespace.getFileName().toString();
        if (!name.matches("openggf-test-session\\.lock(-[0-9a-f]{12})?")) {
            return false;
        }
        for (String metadataName : List.of("owner.json", "initializing.json")) {
            Path metadata = namespace.resolve(metadataName);
            if (!Files.isRegularFile(metadata)) {
                continue;
            }
            String json = Files.readString(metadata, StandardCharsets.UTF_8);
            return json.contains("\"worktree\": \"" + escape(worktree.toString()) + "\"")
                    && json.contains("\"lease_path\": \"" + escape(leasePath.toString()) + "\"");
        }
        return false;
    }

    private static boolean recordedProcessAlive(Path metadata) throws IOException {
        String json = Files.readString(metadata, StandardCharsets.UTF_8);
        Matcher pid = Pattern.compile("\\\"pid\\\"\\s*:\\s*(\\d+)").matcher(json);
        List<Long> processIds = new ArrayList<>();
        while (pid.find()) {
            processIds.add(Long.parseLong(pid.group(1)));
        }
        if (processIds.isEmpty()) {
            return false;
        }
        for (long processId : processIds) {
            Optional<ProcessHandle> process = ProcessHandle.of(processId);
            if (process.isEmpty() || !process.get().isAlive()) {
                continue;
            }
            if (processIds.size() > 1) {
                return true;
            }
            Matcher start = Pattern.compile("\\\"process_start_epoch_ms\\\"\\s*:\\s*(-?\\d+)").matcher(json);
            if (!start.find() || Long.parseLong(start.group(1)) < 0) {
                return true;
            }
            return process.get().info().startInstant()
                    .map(value -> value.toEpochMilli() == Long.parseLong(start.group(1)))
                    .orElse(true);
        }
        return false;
    }

    private static boolean leaseStillOwned(Path namespace, Path leasePath, String runId) throws IOException {
        if (!Files.isDirectory(namespace) || !Files.isRegularFile(leasePath)) {
            return false;
        }
        Path owner = namespace.resolve("owner.json");
        if (!Files.isRegularFile(owner)) {
            return false;
        }
        String json = Files.readString(owner, StandardCharsets.UTF_8);
        return json.contains("\"run_id\": \"" + escape(runId) + "\"")
                && json.contains("\"lease_path\": \"" + escape(leasePath.toString()) + "\"");
    }

    private static boolean markerBelongsTo(Path marker, String runId) throws IOException {
        String json = Files.readString(marker, StandardCharsets.UTF_8);
        Matcher value = Pattern.compile("\\\"run_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        return value.find() && value.group(1).equals(runId);
    }

    private static int contention(String runId, String reason) throws InterruptedException {
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            retryNotice(runId, RETRY_DELAYS_MS[retry]);
        }
        System.err.println("OPENGGF_TEST_SESSION_CONTENTION run_id=" + runId + " reason=" + reason);
        return EX_TEMPFAIL;
    }

    private static void retryNotice(String runId, long delay) throws InterruptedException {
        System.out.println("OPENGGF_TEST_RETRY run_id=" + runId + " delay_ms=" + delay);
        Thread.sleep(delay);
    }

    private static void waitForContinue() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        reader.readLine();
    }

    private static void configureEnvironment(Map<String, String> environment, Path tmp,
                                             Map<String, String> properties,
                                             boolean mavenChild) throws IOException {
        Files.createDirectories(tmp);
        appendSessionJvmOption(environment, "java.io.tmpdir", tmp.toString(), mavenChild);
        environment.put("TMPDIR", tmp.toString());
        environment.put("TMP", tmp.toString());
        environment.put("TEMP", tmp.toString());
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (property.getKey().startsWith("OPENGGF_")) {
                environment.put(property.getKey(), property.getValue());
            } else {
                appendSessionJvmOption(environment, property.getKey(), property.getValue(), mavenChild);
            }
        }
    }

    private static Map<String, String> sessionProperties(Paths paths, String runId,
                                                         String capability, String commandHash,
                                                         Path worktree, List<String> command,
                                                         Path lease) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("openggf.build.directory", paths.build.toString());
        properties.put("openggf.test.tmpdir", paths.tmp.toString());
        properties.put("openggf.surefire.reports", paths.surefire.toString());
        properties.put("openggf.trace.reports", paths.trace.toString());
        properties.put("openggf.test.diagnostics", paths.diagnostics.toString());
        properties.put("openggf.artifact.root", paths.artifacts.toString());
        properties.put("openggf.distribution.root", paths.distribution.toString());
        properties.put("openggf.session.manifest", paths.manifest.toString());
        properties.put("openggf.session.capability", capability);
        properties.put("openggf.session.run-id", runId);
        properties.put("openggf.session.command-hash", commandHash);
        properties.put("openggf.session.worktree", worktree.toString());
        properties.put("openggf.session.lease-path", lease.toString());
        properties.put("openggf.session.allowed-phases", allowedPhases(command));
        properties.put("OPENGGF_TEST_RUN_ID", runId);
        properties.put("OPENGGF_TEST_MANIFEST", paths.manifest.toString());
        properties.put("OPENGGF_TEST_CAPABILITY", capability);
        properties.put("OPENGGF_TEST_WORKTREE", worktree.toString());
        properties.put("OPENGGF_TEST_LEASE", lease.toString());
        properties.put("OPENGGF_TEST_COMMAND_HASH", commandHash);
        properties.put("OPENGGF_TEST_ALLOWED_PHASES", allowedPhases(command));
        return properties;
    }

    private static List<String> sessionCommand(List<String> command,
                                               Map<String, String> properties) {
        if (command.isEmpty() || !isMavenExecutable(command.get(0))) {
            return command;
        }
        List<String> result = new ArrayList<>(command.size() + properties.size());
        result.add(command.get(0));
        properties.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("openggf."))
                .map(entry -> "-D" + entry.getKey() + "=" + entry.getValue())
                .forEach(result::add);
        result.addAll(command.subList(1, command.size()));
        return List.copyOf(result);
    }

    private static boolean isMavenExecutable(String executable) {
        String name = Path.of(executable).getFileName().toString().toLowerCase();
        return name.equals("mvn") || name.equals("mvn.cmd") || name.equals("mvn.bat")
                || name.equals("mvnw") || name.equals("mvnw.cmd");
    }

    private static void appendSessionJvmOption(Map<String, String> environment,
                                               String key, String value, boolean mavenChild) {
        String javaToolOption = "-D" + key + "=" + quoteJvmValue(value);
        environment.put("JAVA_TOOL_OPTIONS",
                append(environment.get("JAVA_TOOL_OPTIONS"), javaToolOption));
        // Maven's POSIX launcher expands MAVEN_OPTS unquoted. A value containing
        // whitespace would therefore become a new JVM argument (and a path with
        // spaces would fail before Maven starts. JAVA_TOOL_OPTIONS is parsed by
        // the JVM and remains the authoritative transport for such values when
        // the direct child is Maven. Other children retain the complete option
        // for nested Maven callers and for the coordinator environment contract.
        if (!mavenChild || mavenShellSafe(value)) {
            String mavenValue = mavenShellSafe(value) ? value : quoteJvmValue(value);
            environment.put("MAVEN_OPTS", append(environment.get("MAVEN_OPTS"),
                    "-D" + key + "=" + mavenValue));
        }
    }

    private static boolean mavenShellSafe(String value) {
        return value.matches("[A-Za-z0-9_./:+,=-]+");
    }

    private static String quoteJvmValue(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void writeExport(Path export, Path manifest, String runId) throws IOException {
        Path absolute = export.toAbsolutePath().normalize();
        if (absolute.getParent() != null) {
            Files.createDirectories(absolute.getParent());
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.writeString(temp, "manifest=" + manifest + "\nrun_id=" + runId + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        moveAtomic(temp, absolute);
    }

    private static String append(String old, String value) {
        return old == null || old.isBlank() ? value : old + " " + value;
    }

    private static String allowedPhases(List<String> command) {
        String joined = String.join(" ", command);
        List<String> phases = new ArrayList<>();
        for (String phase : List.of("pre-clean", "validate")) {
            if (joined.contains("clean") && phase.equals("pre-clean")
                    || (!joined.contains("clean") && phase.equals("validate"))) {
                phases.add(phase);
            }
        }
        if (!phases.contains("validate")) {
            phases.add("validate");
        }
        return String.join(",", phases);
    }

    private static String writeCapability(Path session, String runId, String hash, Path worktree,
                                          Path lease, String allowedPhases) throws IOException {
        Path capability = session.resolve("capability");
        Files.writeString(capability, "run_id=" + runId + "\ncommand_hash=" + hash
                + "\nworktree=" + worktree + "\nlease_path=" + lease
                + "\nallowed_phases=" + allowedPhases + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Files.setPosixFilePermissions(capability, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
        }
        return capability.toString();
    }

    private static String manifest(Paths paths, String runId, String state, Path worktree,
                                   Path lease, String commandHash, String capability,
                                   String allowedPhases, String source, String runtime,
                                   List<String> reports, List<String> artifacts) {
        return "{\n"
                + "  \"run_id\": \"" + escape(runId) + "\",\n"
                + "  \"state\": \"" + state + "\",\n"
                + "  \"manifest\": \"" + escape(paths.manifest.toString()) + "\",\n"
                + "  \"capability\": \"" + escape(capability) + "\",\n"
                + "  \"worktree\": \"" + escape(worktree.toString()) + "\",\n"
                + "  \"lease_path\": \"" + escape(lease.toString()) + "\",\n"
                + "  \"command_hash\": \"" + commandHash + "\",\n"
                + "  \"allowed_phases\": \"" + escape(allowedPhases) + "\",\n"
                + "  \"source_digest\": \"" + source + "\",\n"
                + "  \"runtime_inputs_digest\": \"" + runtime + "\",\n"
                + "  \"build_root\": \"" + escape(paths.build.toString()) + "\",\n"
                + "  \"surefire_reports\": \"" + escape(paths.surefire.toString()) + "\",\n"
                + "  \"trace_reports\": \"" + escape(paths.trace.toString()) + "\",\n"
                + "  \"artifact_root\": \"" + escape(paths.artifacts.toString()) + "\",\n"
                + "  \"distribution_root\": \"" + escape(paths.distribution.toString()) + "\",\n"
                + "  \"reports\": [],\n  \"artifacts\": []\n}\n";
    }

    private static void writeManifest(Path path, String json) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        moveAtomic(tmp, path);
    }

    private static void writeOwner(Path path, String runId, Path worktree, Path lease,
                                   List<String> command, String hash, String state) throws IOException {
        String branch = runGit(worktree, List.of("symbolic-ref", "--short", "HEAD")).trim();
        String head = runGit(worktree, List.of("rev-parse", "HEAD")).trim();
        String json = "{\n  \"run_id\": \"" + escape(runId) + "\",\n"
                + "  \"pid\": " + ProcessHandle.current().pid() + ",\n"
                + "  \"host\": \"" + escape(host()) + "\",\n"
                + "  \"worktree\": \"" + escape(worktree.toString()) + "\",\n"
                + "  \"lease_path\": \"" + escape(lease.toString()) + "\",\n"
                + "  \"branch\": \"" + escape(branch) + "\",\n"
                + "  \"head\": \"" + escape(head) + "\",\n"
                + "  \"command_hash\": \"" + hash + "\",\n"
                + "  \"command\": \"" + escape(String.join(" ", command)) + "\",\n"
                + "  \"state\": \"" + state + "\",\n"
                + "  \"started_at\": \"" + escape(Instant.now().toString()) + "\",\n"
                + "  \"process_start_epoch_ms\": " + processStartEpochMs() + "\n}\n";
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        moveAtomic(tmp, path);
    }

    private static String reclaimJson(String runId, Path namespace) {
        return "{\n  \"run_id\": \"" + escape(runId) + "\",\n  \"pid\": "
                + ProcessHandle.current().pid() + ",\n  \"host\": \"" + escape(host())
                + "\",\n  \"namespace\": \"" + escape(namespace.toString())
                + "\",\n  \"state\": \"reclaiming\",\n"
                + "  \"process_start_epoch_ms\": " + processStartEpochMs() + "\n}\n";
    }

    private static long processStartEpochMs() {
        return ProcessHandle.current().info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
    }

    private static Path resolveOutputRoot(Path worktree, boolean allowSystemTmp) throws IOException {
        String override = System.getenv("OPENGGF_TEST_ROOT");
        if (override != null && !override.isBlank()) {
            return validateRoot(Path.of(override), "OPENGGF_TEST_ROOT");
        }
        Optional<Path> managed = agentScratchRoot();
        if (managed.isPresent()) {
            return validateRoot(managed.get(), "agent-scratch session root");
        }
        Path project = worktree.resolve(".openggf/test-runs");
        if (Files.exists(project) || writableParent(project)) {
            return validateRoot(project, "project session root");
        }
        if (allowSystemTmp) {
            return validateRoot(Path.of(System.getProperty("java.io.tmpdir"), "openggf-test-runs"), "system temp");
        }
        throw new StartupFailure("no writable session root; set OPENGGF_TEST_ROOT or --allow-system-tmp", 1);
    }

    private static Optional<Path> agentScratchRoot() {
        try {
            Process process = new ProcessBuilder("agent-scratch", "new", "openggf-test-session")
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0 || output.isBlank()) {
                return Optional.empty();
            }
            String last = output.lines().reduce((a, b) -> b).orElse("").trim();
            Path path = Path.of(last);
            return path.isAbsolute() ? Optional.of(path) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Path validateRoot(Path root, String name) throws IOException {
        if (!root.isAbsolute() || root.toString().contains("\n") || root.toString().contains("\r")) {
            throw new StartupFailure(name + " must be an absolute path without newlines", 1);
        }
        Files.createDirectories(root);
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new StartupFailure(name + " is not a plain directory", 1);
        }
        try {
            String current = root.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name")).getName();
            String owner = Files.getOwner(root).getName();
            if (!owner.equals(current)) {
                throw new StartupFailure(name + " is not owned by the current user", 1);
            }
        } catch (UnsupportedOperationException e) {
            throw new StartupFailure(name + " ownership cannot be verified", 1);
        }
        Path probe = Files.createTempFile(root, ".probe-", ".tmp");
        Files.deleteIfExists(probe);
        return root.toAbsolutePath().normalize();
    }

    private static boolean writableParent(Path path) {
        try {
            Path parent = path.toAbsolutePath().normalize();
            while (parent != null && !Files.exists(parent)) {
                parent = parent.getParent();
            }
            return parent != null && Files.isWritable(parent);
        } catch (Exception e) {
            return false;
        }
    }

    private static Path resolveLockParent(Path worktree, Path explicit) throws IOException {
        Path parent;
        boolean external = explicit != null;
        if (explicit != null) {
            parent = explicit.toAbsolutePath().normalize();
        } else {
            String env = System.getenv("OPENGGF_TEST_LOCK_ROOT");
            if (env != null && !env.isBlank()) {
                external = true;
                parent = Path.of(env).toAbsolutePath().normalize();
            } else {
                Path git = gitPath("--git-dir");
                parent = (git.isAbsolute() ? git : worktree.resolve(git)).toAbsolutePath().normalize();
            }
        }
        if (external && Files.exists(parent) && Files.isSymbolicLink(parent)) {
            throw new StartupFailure("external lock root must not be a symlink", 1);
        }
        Files.createDirectories(parent);
        if (!Files.isDirectory(parent) || !Files.isWritable(parent)) {
            throw new StartupFailure("lock root is not writable: " + parent, 1);
        }
        Path realParent = parent.toRealPath();
        if (external && realParent.startsWith(worktree.toRealPath())) {
            throw new StartupFailure("external lock root must be outside the worktree", 1);
        }
        return realParent;
    }

    private static boolean externalLockRequested(Options options) {
        return options.lockRoot != null || (System.getenv("OPENGGF_TEST_LOCK_ROOT") != null
                && !System.getenv("OPENGGF_TEST_LOCK_ROOT").isBlank());
    }

    private static Path namespace(Path lockParent, Path worktree, boolean external) {
        String suffix = external ? "-" + sha256(worktree.toString()).substring(0, 12) : "";
        return lockParent.resolve("openggf-test-session.lock" + suffix);
    }

    private static Path worktree() throws IOException {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--show-toplevel")
                    .redirectErrorStream(true).start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            process.waitFor(5, TimeUnit.SECONDS);
            if (value.isBlank()) {
                throw new IOException("git returned no worktree");
            }
            return Path.of(value).toRealPath();
        } catch (Exception e) {
            return Path.of(System.getProperty("user.dir")).toRealPath();
        }
    }

    private static Path gitPath(String arg) throws IOException {
        Process process = new ProcessBuilder("git", "rev-parse", arg).redirectErrorStream(true).start();
        String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (value.isBlank()) {
            throw new StartupFailure("cannot resolve Git metadata", 1);
        }
        return Path.of(value);
    }

    private static String createRunId() {
        byte[] random = new byte[3];
        RANDOM.nextBytes(random);
        return RUN_TIME.format(Instant.now()) + "-p" + ProcessHandle.current().pid() + "-" + HEX.formatHex(random);
    }

    private static String sourceDigest(Path root) throws IOException {
        MessageDigest digest = sha();
        digest.update(gitIdentity(root).getBytes(StandardCharsets.UTF_8));
        String inventory = runGit(root, List.of("ls-files", "--cached", "--others",
                "--exclude-standard", "-z"));
        if (inventory.startsWith("<exit=") || inventory.startsWith("<timeout>")
                || inventory.startsWith("<interrupted>")) {
            throw new IOException("cannot enumerate Git source inventory: " + inventory);
        }
        for (String relative : inventory.split("\\u0000")) {
            if (relative.isBlank()) {
                continue;
            }
            Path path = root.resolve(relative).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                continue;
            }
                digest.update(root.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(path));
        }
        return HEX.formatHex(digest.digest());
    }

    private static String gitIdentity(Path root) throws IOException {
        StringBuilder identity = new StringBuilder();
        for (List<String> arguments : List.of(
                List.of("rev-parse", "HEAD"),
                List.of("symbolic-ref", "--short", "HEAD"),
                List.of("status", "--porcelain=v2", "--untracked-files=all"),
                List.of("ls-files", "-v"),
                List.of("diff", "--cached", "--binary"))) {
            identity.append(String.join("\0", arguments)).append('\n');
            identity.append(runGit(root, arguments)).append('\n');
        }
        return identity.toString();
    }

    private static String runGit(Path root, List<String> arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "<timeout>";
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return "<interrupted>";
        }
        return process.exitValue() == 0 ? output : "<exit=" + process.exitValue() + ">" + output;
    }

    private static String runtimeDigest(List<String> command) {
        MessageDigest digest = sha();
        digest.update(String.join("\0", command).getBytes(StandardCharsets.UTF_8));
        for (Path input : runtimeInputs(command)) {
            Path canonical = input.toAbsolutePath().normalize();
            digest.update(canonical.toString().getBytes(StandardCharsets.UTF_8));
            if (!Files.exists(canonical)) {
                digest.update("<missing>".getBytes(StandardCharsets.UTF_8));
                continue;
            }
            try {
                if (Files.isRegularFile(canonical)) {
                    digest.update(Files.readAllBytes(canonical));
                } else if (Files.isDirectory(canonical)) {
                    try (var paths = Files.walk(canonical)) {
                        for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                            digest.update(canonical.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
                            digest.update(Files.readAllBytes(path));
                        }
                    }
                }
            } catch (IOException e) {
                digest.update(("<unreadable:" + e.getClass().getName() + ">").getBytes(StandardCharsets.UTF_8));
            }
        }
        return HEX.formatHex(digest.digest());
    }

    private static Set<Path> runtimeInputs(List<String> command) {
        Set<Path> inputs = new LinkedHashSet<>();
        String declared = System.getenv("OPENGGF_RUNTIME_INPUTS");
        if (declared != null && !declared.isBlank()) {
            for (String value : declared.split(Pattern.quote(java.io.File.pathSeparator))) {
                if (!value.isBlank()) {
                    inputs.add(Path.of(value));
                }
            }
        }
        for (String argument : command) {
            if (!argument.startsWith("-D")) {
                continue;
            }
            int equals = argument.indexOf('=');
            if (equals <= 2) {
                continue;
            }
            String key = argument.substring(2, equals).toLowerCase();
            if (key.contains("rom") || key.contains("config") || key.contains("mods")
                    || key.endsWith(".path")) {
                inputs.add(Path.of(argument.substring(equals + 1)));
            }
        }
        return inputs;
    }

    private static String sha256(String value) {
        return HEX.formatHex(sha().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void moveAtomic(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            throw new StartupFailure("atomic lease publication is unsupported", 1);
        }
    }

    private static String host() {
        return System.getenv().getOrDefault("HOSTNAME", "unknown");
    }

    private static boolean isLifecycleGuard(String phase) {
        return phase.equals("pre-clean") || phase.equals("validate");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static final class Lease implements AutoCloseable {
        private final Path namespace;
        private final FileChannel channel;
        private final FileLock lock;

        private Lease(Path namespace, FileChannel channel, FileLock lock) {
            this.namespace = namespace;
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() throws IOException {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
    }

    private static final class ShutdownState {
        private final Paths paths;
        private final String runId;
        private final Path worktree;
        private final Path leasePath;
        private final String sourceBefore;
        private final String runtimeBefore;
        private final List<String> command;
        private final String commandHash;
        private final String capability;
        private final String allowedPhases;
        private final Path exportFile;
        private final Lease lease;
        private volatile Process child;
        private volatile boolean completed;

        private ShutdownState(Paths paths, String runId, Path worktree, Path leasePath,
                              String sourceBefore, String runtimeBefore, List<String> command,
                              String commandHash, String capability, String allowedPhases,
                              Path exportFile, Lease lease) {
            this.paths = paths;
            this.runId = runId;
            this.worktree = worktree;
            this.leasePath = leasePath;
            this.sourceBefore = sourceBefore;
            this.runtimeBefore = runtimeBefore;
            this.command = command;
            this.commandHash = commandHash;
            this.capability = capability;
            this.allowedPhases = allowedPhases;
            this.exportFile = exportFile;
            this.lease = lease;
        }

        private synchronized void abort() {
            if (completed) {
                return;
            }
            Process process = child;
            ProcessTreeResult treeResult = process == null
                    ? new ProcessTreeResult(true, List.of()) : terminateProcessTree(process);
            boolean treeStopped = treeResult.stopped();
            try {
                if (!treeStopped) {
                    writeProcessTreeMarker(leasePath.getParent(), runId, treeResult.survivors());
                }
                String sourceAfter = sourceDigest(worktree);
                String runtimeAfter = runtimeDigest(command);
                String state = sourceBefore.equals(sourceAfter) && runtimeBefore.equals(runtimeAfter)
                        && treeStopped && leaseStillOwned(leasePath.getParent(), leasePath, runId)
                        ? "ABORTED" : "INVALID_IDENTITY_CHANGED";
                writeManifest(paths.manifest, manifest(paths, runId, state, worktree, leasePath,
                        commandHash, capability, allowedPhases, sourceAfter, runtimeAfter,
                        List.of(), List.of()));
                if (exportFile != null) {
                    writeExport(exportFile, paths.manifest, runId);
                }
                System.out.println("OPENGGF_TEST_RUN_END run_id=" + runId
                        + " exit_code=143 state=" + state + " valid=false process_tree_stopped="
                        + treeStopped + " manifest=" + paths.manifest);
                System.out.flush();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            } finally {
                completed = true;
                if (treeStopped) {
                    try {
                        lease.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        private static void writeProcessTreeMarker(Path namespace, String runId,
                                                   List<ProcessHandle> survivors)
                throws IOException {
            if (namespace == null || !Files.isDirectory(namespace)) {
                return;
            }
            String json = "{\n  \"run_id\": \"" + escape(runId)
                    + "\",\n  \"state\": \"process-tree-active\",\n  \"pid\": "
                    + survivors.get(0).pid() + ",\n  \"pids\": [" + survivors.get(0).pid();
            for (ProcessHandle survivor : survivors.stream().skip(1).toList()) {
                json += ", " + survivor.pid();
            }
            json += "]\n}\n";
            Files.writeString(namespace.resolve("process-tree-active.json"), json,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }

        private static ProcessTreeResult terminateProcessTree(Process process) {
            ProcessHandle root = process.toHandle();
            Set<ProcessHandle> known = new LinkedHashSet<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                known.add(root);
                root.descendants().forEach(known::add);
                for (ProcessHandle handle : known) {
                    if (handle.isAlive()) {
                        handle.destroyForcibly();
                    }
                }
                if (known.stream().noneMatch(ProcessHandle::isAlive)) {
                    return new ProcessTreeResult(true, List.of());
                }
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new ProcessTreeResult(false, known.stream().filter(ProcessHandle::isAlive).toList());
                }
            }
            List<ProcessHandle> survivors = known.stream().filter(ProcessHandle::isAlive).toList();
            return new ProcessTreeResult(survivors.isEmpty(), survivors);
        }

        private record ProcessTreeResult(boolean stopped, List<ProcessHandle> survivors) {
        }
    }

    private record Paths(Path session, Path build, Path tmp, Path surefire, Path trace,
                         Path diagnostics, Path artifacts, Path distribution,
                         Path manifest, Path mavenLog) {
        static Paths create(Path session) throws IOException {
            Path build = Files.createDirectories(session.resolve("build"));
            Path tmp = Files.createDirectories(session.resolve("tmp"));
            Path surefire = Files.createDirectories(session.resolve("surefire-reports"));
            Path trace = Files.createDirectories(session.resolve("trace-reports"));
            Path diagnostics = Files.createDirectories(session.resolve("diagnostics"));
            Path artifacts = Files.createDirectories(session.resolve("artifacts"));
            Path distribution = Files.createDirectories(session.resolve("distribution"));
            return new Paths(session, build, tmp, surefire, trace, diagnostics, artifacts,
                    distribution, session.resolve("manifest.json"), session.resolve("maven.log"));
        }
    }

    private static final class Options {
        Path exportFile;
        Path lockRoot;
        Path reclaim;
        boolean allowSystemTmp;
        String guard;
        String debugGuard;
        List<String> command = List.of();
        Map<String, String> environment;

        static Options parse(String[] args) {
            Options options = new Options();
            List<String> command = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("--")) {
                    command.addAll(Arrays.asList(args).subList(i + 1, args.length));
                    break;
                }
                switch (arg) {
                    case "--export-file" -> options.exportFile = Path.of(require(args, ++i, arg));
                    case "--lock-root" -> options.lockRoot = Path.of(require(args, ++i, arg));
                    case "--allow-system-tmp" -> options.allowSystemTmp = true;
                    case "--reclaim" -> options.reclaim = Path.of(require(args, ++i, arg));
                    case "--guard" -> {
                        String phase = require(args, ++i, arg);
                        if (isLifecycleGuard(phase)) {
                            options.guard = phase;
                        } else {
                            options.debugGuard = phase;
                        }
                    }
                    default -> throw new StartupFailure("unknown option: " + arg, 2);
                }
            }
            options.command = List.copyOf(command);
            rejectReservedProperties(options.command);
            if (options.reclaim == null && options.guard == null && options.debugGuard == null
                    && options.command.isEmpty()) {
                throw new StartupFailure("child command required after --", 2);
            }
            return options;
        }

        private static void rejectReservedProperties(List<String> command) {
            for (String argument : command) {
                if (argument.startsWith("-Dopenggf.")) {
                    throw new StartupFailure("reserved session property cannot be supplied to the child command: "
                            + argument, 2);
                }
            }
        }

        private static String require(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new StartupFailure(option + " requires a value", 2);
            }
            return args[index];
        }
    }

    private static final class StartupFailure extends RuntimeException {
        private final int exitCode;

        private StartupFailure(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }
    }

}
