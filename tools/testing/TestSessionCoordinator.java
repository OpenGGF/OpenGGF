import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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
import java.util.stream.Stream;

/** Standalone coordinator for isolated OpenGGF test/build sessions. */
public final class TestSessionCoordinator {
    private static final int EX_TEMPFAIL = 75;
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {50, 100, 200};
    private static final String SESSION_ISOLATION = "worktree-session";
    private static final String LWJGL_EXTRACTION_ISOLATION = "per-surefire-fork";
    private static final String LWJGL_EXTRACTION_TEMPLATE = "lwjgl-${surefire.forkNumber}";
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final int STORAGE_ALLOCATION_SCHEMA = 1;
    private static final String SUPPORTED_AGENT_SCRATCH_HELPER_VERSION = "openggf-agent-scratch-v2";
    private static final int MAX_MANAGED_DIAGNOSTIC_LENGTH = 240;
    private static final Duration MAX_MANAGED_RETENTION = Duration.ofDays(7);
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final long DEFAULT_MIN_FREE_BYTES = 20L * GIB;
    private static final Set<String> RESERVATION_FIELDS = Set.of(
            "schema_version", "storage_tier", "managed_root", "allocation_path",
            "filesystem_device", "usable_bytes", "total_bytes", "usable_inodes",
            "retention_deadline", "helper_version");

    private enum StorageTier {
        EXPLICIT_OVERRIDE,
        MANAGED_CODEX_TEST_SESSIONS,
        PROJECT_LOCAL_FALLBACK,
        SYSTEM_TMP_EXPLICIT
    }

    private record CapacitySnapshot(long usableBytes, long totalBytes, long usableInodes) {
    }

    private record StorageAllocation(
            Path outputRoot, StorageTier tier, Path managedRoot,
            int allocationSchema, String helperVersion, String filesystemDevice,
            CapacitySnapshot allocationCapacity, Instant retentionDeadline,
            String notApplicableReason, String warning) {
    }

    private enum CompactionStatus {
        COMPACTED,
        NOTHING_TO_REMOVE,
        RETAINED_BY_REQUEST,
        FAILED,
        REFUSED
    }

    private record CompactionResult(
            CompactionStatus status,
            List<String> removedRelativePaths,
            long reclaimedBytes,
            String error) {
    }

    private record ManifestContext(
            StorageAllocation allocation,
            CapacitySnapshot launchCapacity,
            CapacitySnapshot completionCapacity,
            CompactionResult compaction,
            boolean retainEphemeral,
            String storageFinalizationError,
            String launchCapacityError,
            LiveProbeResult launchProbe,
            String completionCapacityError,
            LiveProbeResult completionProbe) {
    }

    private enum ProbePhase {
        LAUNCH,
        COMPLETION
    }

    private enum InodeProbeStatus {
        AVAILABLE,
        FAILED,
        NOT_RUN
    }

    private enum DirectoryFlushStatus {
        FLUSHED,
        DIRECTORY_FLUSH_UNSUPPORTED,
        NOT_RUN
    }

    private record LiveProbeResult(
            InodeProbeStatus status,
            DirectoryFlushStatus directoryFlushStatus,
            String error) {
        private static LiveProbeResult notRun() {
            return new LiveProbeResult(InodeProbeStatus.NOT_RUN,
                    DirectoryFlushStatus.NOT_RUN, null);
        }
    }

    private record StorageObservation(
            CapacitySnapshot capacity,
            String capacityError,
            LiveProbeResult liveProbe) {
        private String error() {
            if (capacityError == null) {
                return liveProbe.error;
            }
            if (liveProbe.error == null) {
                return capacityError;
            }
            return capacityError + "; " + liveProbe.error;
        }
    }

    @FunctionalInterface
    private interface CapacityProbe {
        CapacitySnapshot measure(StorageAllocation allocation, ProbePhase phase) throws IOException;
    }

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
        StorageAllocation allocation = resolveStorageAllocation(worktree, options.allowSystemTmp);
        Path outputRoot = allocation.outputRoot;
        if (allocation.warning != null) {
            System.err.println(allocation.warning);
        }
        Files.createDirectories(outputRoot);
        Path lockParent = resolveLockParent(worktree, options.lockRoot);
        String runId = createRunId();
        Path namespace = namespace(lockParent, worktree, externalLockRequested(options));
        if (options.reuseStale && Files.isDirectory(namespace)) {
            int reclaimResult = reclaimStaleLease(namespace.resolve("lease.lock"));
            if (reclaimResult != 0 && reclaimResult != EX_TEMPFAIL) {
                return reclaimResult;
            }
        }
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
        writeCommand(paths.command, options.command);
        String capability = writeCapability(session, runId, commandHash, worktree, leasePath,
                allowedPhases);
        String sourceBefore = sourceDigest(worktree);
        String runtimeBefore = runtimeDigest(options.command);
        writeOwner(lease.namespace.resolve("owner.json"), runId, worktree, leasePath,
                options.command, commandHash, "owner");
        CapacityProbe capacityProbe = capacityProbe();
        StorageObservation launchObservation = observeCapacity(
                allocation, capacityProbe, ProbePhase.LAUNCH);
        CapacitySnapshot launchCapacity = launchObservation.capacity;
        long defaultCapacityFloor = requiredFreeBytes(launchCapacity);
        long capacityFloor;
        try {
            capacityFloor = configuredRequiredFreeBytes(launchCapacity);
        } catch (StartupFailure failure) {
            return startupFailed(paths, runId, worktree, leasePath, commandHash, capability,
                    allowedPhases, sourceBefore, runtimeBefore, allocation, launchObservation,
                    defaultCapacityFloor, failure.getMessage(), lease, capacityProbe);
        }
        if (launchObservation.capacityError != null) {
            return startupFailed(paths, runId, worktree, leasePath, commandHash, capability,
                    allowedPhases, sourceBefore, runtimeBefore, allocation, launchObservation,
                    capacityFloor, launchObservation.capacityError, lease, capacityProbe);
        }
        if (launchCapacity.usableBytes < capacityFloor
                || allocation.tier == StorageTier.MANAGED_CODEX_TEST_SESSIONS
                && launchCapacity.usableInodes == 0) {
            String reason = launchCapacity.usableInodes == 0
                    ? "allocation filesystem reports zero usable inodes"
                    : "allocation filesystem is below the required free-byte floor";
            return startupFailed(paths, runId, worktree, leasePath, commandHash, capability,
                    allowedPhases, sourceBefore, runtimeBefore, allocation, launchObservation,
                    capacityFloor, reason, lease, capacityProbe);
        }
        launchObservation = new StorageObservation(launchCapacity, null,
                liveStorageProbe(paths.session, ProbePhase.LAUNCH));
        if (launchObservation.liveProbe.status == InodeProbeStatus.FAILED) {
            return startupFailed(paths, runId, worktree, leasePath, commandHash, capability,
                    allowedPhases, sourceBefore, runtimeBefore, allocation, launchObservation,
                    capacityFloor, launchObservation.liveProbe.error, lease, capacityProbe);
        }
        ManifestContext runningContext = new ManifestContext(allocation, launchCapacity,
                null, null, false, null, null, launchObservation.liveProbe,
                null, null);
        writeManifest(paths.manifest, manifest(paths, runId, "RUNNING", worktree, leasePath,
                commandHash, capability, allowedPhases, sourceBefore, runtimeBefore,
                List.of(), List.of(), runningContext, capacityFloor));
        printStartMarker(paths, runId, leasePath, runningContext, capacityFloor, "RUNNING");

        ShutdownState shutdown = new ShutdownState(paths, runId, worktree, leasePath,
                sourceBefore, runtimeBefore, options.command, commandHash, capability,
                allowedPhases, options.exportFile, lease, allocation, launchCapacity,
                capacityFloor, capacityProbe, launchObservation.liveProbe);
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
                    if (options.verbose) {
                        System.out.print(text);
                    }
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
            StorageObservation completionObservation = observeStorage(
                    paths, allocation, capacityProbe, ProbePhase.COMPLETION);
            String storageFinalizationError = completionObservation.error();
            if (storageFinalizationError != null && state.equals("PASSED")) {
                state = "STORAGE_FINALIZATION_FAILED";
                exitCode = 1;
                valid = false;
            }
            ManifestContext terminalContext = new ManifestContext(allocation, launchCapacity,
                    completionObservation.capacity, null, false, storageFinalizationError,
                    null, launchObservation.liveProbe, completionObservation.capacityError,
                    completionObservation.liveProbe);
            writeManifest(paths.manifest, manifest(paths, runId, state, worktree, leasePath,
                    commandHash, capability, allowedPhases, sourceAfter, runtimeAfter,
                    reportInventory(paths), artifactInventory(paths), terminalContext,
                    capacityFloor));
            if (options.exportFile != null) {
                writeExport(options.exportFile, paths.manifest, runId);
            }
            printEndMarker(paths, runId, exitCode, state, valid, terminalContext);
            shutdown.completed = true;
            lease.close();
        }
        return interrupted || identityChanged
                ? (exitCode == 0 ? 1 : exitCode) : exitCode;
    }

    private static int startupFailed(Paths paths, String runId, Path worktree, Path leasePath,
                                     String commandHash, String capability, String allowedPhases,
                                     String source, String runtime, StorageAllocation allocation,
                                     StorageObservation launchObservation, long capacityFloor,
                                     String reason, Lease lease,
                                     CapacityProbe capacityProbe) throws Exception {
        try {
            if (!Files.exists(paths.mavenLog)) {
                Files.createFile(paths.mavenLog);
            }
            StorageObservation completionObservation = observeStorage(
                    paths, allocation, capacityProbe, ProbePhase.COMPLETION);
            ManifestContext context = new ManifestContext(allocation, launchObservation.capacity,
                    completionObservation.capacity, null, false, completionObservation.error(),
                    launchObservation.capacityError, launchObservation.liveProbe,
                    completionObservation.capacityError, completionObservation.liveProbe);
            writeManifest(paths.manifest, manifest(paths, runId, "STARTUP_FAILED", worktree,
                    leasePath, commandHash, capability, allowedPhases, source, runtime,
                    List.of(), List.of(), context, capacityFloor));
            printStartMarker(paths, runId, leasePath, context, capacityFloor, "STARTUP_FAILED");
            printEndMarker(paths, runId, 1, "STARTUP_FAILED", false, context);
            System.err.println("OPENGGF_TEST_SESSION_ERROR " + boundedSingleLine(reason)
                    + " allocation_path=" + boundedSingleLine(allocation.outputRoot.toString())
                    + " storage_tier=" + allocation.tier
                    + " usable_bytes=" + launchObservation.capacity.usableBytes
                    + " required_free_bytes=" + capacityFloor
                    + " usable_inodes=" + launchObservation.capacity.usableInodes
                    + " inspect_command='agent-scratch status'"
                    + " prune_preview_command='agent-scratch prune --dry-run'");
            return 1;
        } finally {
            lease.close();
        }
    }

    private static void writeCommand(Path path, List<String> command) throws IOException {
        Files.writeString(path, String.join("\n", command) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static long requiredFreeBytes(CapacitySnapshot capacity) {
        return Math.max(DEFAULT_MIN_FREE_BYTES, capacity.totalBytes / 20L);
    }

    private static long configuredRequiredFreeBytes(CapacitySnapshot capacity) {
        long required = requiredFreeBytes(capacity);
        String configured = System.getenv("OPENGGF_TEST_MIN_FREE_BYTES");
        if (configured == null) {
            return required;
        }
        if (configured.isBlank()) {
            throw new StartupFailure("OPENGGF_TEST_MIN_FREE_BYTES must not be blank", 1);
        }
        if (!configured.matches("[0-9]+")) {
            throw new StartupFailure("OPENGGF_TEST_MIN_FREE_BYTES must be an unsigned decimal integer", 1);
        }
        long override;
        try {
            override = Long.parseLong(configured);
        } catch (NumberFormatException e) {
            throw new StartupFailure("OPENGGF_TEST_MIN_FREE_BYTES is outside the supported integer range", 1);
        }
        if (override < required) {
            throw new StartupFailure("OPENGGF_TEST_MIN_FREE_BYTES may raise but not lower the default floor", 1);
        }
        return override;
    }

    private static CapacityProbe capacityProbe() {
        String inodeLimit = System.getenv("OPENGGF_TEST_CAPACITY_INODE_LIMIT");
        if (inodeLimit != null && !inodeLimit.equals("0")) {
            throw new StartupFailure("OPENGGF_TEST_CAPACITY_INODE_LIMIT may only force zero", 1);
        }
        String failurePhase = System.getenv("OPENGGF_TEST_CAPACITY_PROBE_FAILURE_PHASE");
        return (allocation, phase) -> {
            if (phaseName(phase).equals(failurePhase)) {
                throw new IOException("injected " + phaseName(phase) + " capacity probe failure");
            }
            CapacitySnapshot measured = measureCapacity(allocation);
            return inodeLimit == null ? measured
                    : new CapacitySnapshot(measured.usableBytes, measured.totalBytes, 0);
        };
    }

    private static CapacitySnapshot measureCapacity(StorageAllocation allocation) throws IOException {
        FileStore store = Files.getFileStore(allocation.outputRoot);
        long usableInodes = allocation.allocationCapacity.usableInodes;
        return new CapacitySnapshot(store.getUsableSpace(), store.getTotalSpace(), usableInodes);
    }

    private static StorageObservation observeCapacity(StorageAllocation allocation,
                                                      CapacityProbe capacityProbe,
                                                      ProbePhase phase) {
        try {
            return new StorageObservation(capacityProbe.measure(allocation, phase),
                    null, LiveProbeResult.notRun());
        } catch (IOException | RuntimeException e) {
            return new StorageObservation(allocation.allocationCapacity,
                    boundedSingleLine(message(e)), LiveProbeResult.notRun());
        }
    }

    private static StorageObservation observeStorage(Paths paths, StorageAllocation allocation,
                                                     CapacityProbe capacityProbe,
                                                     ProbePhase phase) {
        StorageObservation capacity = observeCapacity(allocation, capacityProbe, phase);
        return new StorageObservation(capacity.capacity, capacity.capacityError,
                liveStorageProbe(paths.session, phase));
    }

    private static LiveProbeResult liveStorageProbe(Path session, ProbePhase phase) {
        String phaseName = phaseName(phase);
        if (phaseName.equals(System.getenv("OPENGGF_TEST_LIVE_PROBE_FAILURE_PHASE"))) {
            return new LiveProbeResult(InodeProbeStatus.FAILED,
                    DirectoryFlushStatus.NOT_RUN,
                    "injected " + phaseName + " live probe failure");
        }
        Path probe = session.resolve(".storage-probe-" + createProbeSuffix());
        byte[] expected = "OpenGGF storage probe\n".getBytes(StandardCharsets.UTF_8);
        try {
            try (FileChannel channel = FileChannel.open(probe, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(expected);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            byte[] actual = Files.readAllBytes(probe);
            if (!Arrays.equals(expected, actual)) {
                throw new IOException("private storage probe readback mismatch");
            }
            Files.delete(probe);
            return new LiveProbeResult(InodeProbeStatus.AVAILABLE,
                    flushDirectory(session), null);
        } catch (IOException | RuntimeException e) {
            String error = boundedSingleLine(message(e));
            try {
                Files.deleteIfExists(probe);
            } catch (IOException cleanup) {
                error += "; probe cleanup failed: " + boundedSingleLine(message(cleanup));
            }
            return new LiveProbeResult(InodeProbeStatus.FAILED,
                    DirectoryFlushStatus.NOT_RUN, error);
        }
    }

    private static DirectoryFlushStatus flushDirectory(Path session) {
        if ("1".equals(System.getenv("OPENGGF_TEST_DIRECTORY_FLUSH_UNSUPPORTED"))) {
            return DirectoryFlushStatus.DIRECTORY_FLUSH_UNSUPPORTED;
        }
        try (FileChannel directory = FileChannel.open(session, StandardOpenOption.READ)) {
            directory.force(true);
            return DirectoryFlushStatus.FLUSHED;
        } catch (IOException | RuntimeException e) {
            return DirectoryFlushStatus.DIRECTORY_FLUSH_UNSUPPORTED;
        }
    }

    private static String createProbeSuffix() {
        byte[] random = new byte[6];
        RANDOM.nextBytes(random);
        return HEX.formatHex(random);
    }

    private static String phaseName(ProbePhase phase) {
        return phase.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message;
    }

    private static void printStartMarker(Paths paths, String runId, Path leasePath,
                                         ManifestContext context, long capacityFloor,
                                         String state) {
        System.out.println("OPENGGF_TEST_RUN_START run_id=" + markerToken(runId) + " isolation="
                + markerToken(SESSION_ISOLATION) + " lwjgl=" + markerToken(LWJGL_EXTRACTION_ISOLATION)
                + " manifest=" + markerToken(paths.manifest.toString())
                + " lease=" + markerToken(leasePath.toString())
                + " log=" + markerToken(paths.mavenLog.toString())
                + " state=" + markerToken(state)
                + " storage_tier=" + markerToken(context.allocation.tier.name())
                + " launch_usable_bytes=" + context.launchCapacity.usableBytes
                + " capacity_floor_bytes=" + capacityFloor);
    }

    private static void printEndMarker(Paths paths, String runId, int exitCode, String state,
                                       boolean valid, ManifestContext context) {
        printEndMarker(paths, runId, exitCode, state, valid, context, null);
    }

    private static void printEndMarker(Paths paths, String runId, int exitCode, String state,
                                       boolean valid, ManifestContext context,
                                       Boolean processTreeStopped) {
        String compactionStatus = context.compaction == null
                ? "NOT_RUN" : context.compaction.status.name();
        long reclaimedBytes = context.compaction == null ? 0 : context.compaction.reclaimedBytes;
        long completionBytes = context.completionCapacity == null
                ? -1 : context.completionCapacity.usableBytes;
        String processTreeField = processTreeStopped == null
                ? "" : " process_tree_stopped=" + processTreeStopped;
        System.out.println("OPENGGF_TEST_RUN_END run_id=" + markerToken(runId) + " isolation="
                + markerToken(SESSION_ISOLATION) + " lwjgl=" + markerToken(LWJGL_EXTRACTION_ISOLATION)
                + " exit_code=" + exitCode + " state=" + markerToken(state)
                + " valid=" + valid + " manifest=" + markerToken(paths.manifest.toString())
                + " log=" + markerToken(paths.mavenLog.toString())
                + " compaction_status=" + markerToken(compactionStatus)
                + " reclaimed_bytes=" + reclaimedBytes
                + " completion_usable_bytes=" + completionBytes + processTreeField);
    }

    private static String markerToken(String value) {
        StringBuilder encoded = new StringBuilder(value.length());
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int character = raw & 0xff;
            if (character >= 'a' && character <= 'z'
                    || character >= 'A' && character <= 'Z'
                    || character >= '0' && character <= '9'
                    || character == '-' || character == '_' || character == '.'
                    || character == '~' || character == '/' || character == ':'
                    || character == '@') {
                encoded.append((char) character);
            } else {
                encoded.append('%');
                encoded.append("0123456789ABCDEF".charAt(character >>> 4));
                encoded.append("0123456789ABCDEF".charAt(character & 0xf));
            }
        }
        return encoded.toString();
    }

    /** Reuses a completed wrapper lease only after the normal reclaim checks pass. */
    private static int reclaimStaleLease(Path leasePath) throws Exception {
        if (!Files.isDirectory(leasePath.getParent())) {
            return 0;
        }
        Options reclaim = new Options();
        reclaim.reclaim = leasePath;
        return reclaim(reclaim);
    }

    private static int debugGuard(Options options) throws Exception {
        Path worktree = worktree();
        StorageAllocation allocation = resolveStorageAllocation(worktree, options.allowSystemTmp);
        Path outputRoot = allocation.outputRoot;
        if (allocation.warning != null) {
            System.err.println(allocation.warning);
        }
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
        properties.put("openggf.session.isolation", SESSION_ISOLATION);
        properties.put("openggf.session.lwjgl-extraction", LWJGL_EXTRACTION_ISOLATION);
        properties.put("openggf.session.lwjgl-extraction-template", paths.tmp.resolve(
                LWJGL_EXTRACTION_TEMPLATE).toString());
        properties.put("openggf.session.allowed-phases", allowedPhases(command));
        properties.put("OPENGGF_TEST_RUN_ID", runId);
        properties.put("OPENGGF_TEST_MANIFEST", paths.manifest.toString());
        properties.put("OPENGGF_TEST_CAPABILITY", capability);
        properties.put("OPENGGF_TEST_WORKTREE", worktree.toString());
        properties.put("OPENGGF_TEST_LEASE", lease.toString());
        properties.put("OPENGGF_TEST_COMMAND_HASH", commandHash);
        properties.put("OPENGGF_TEST_ISOLATION", SESSION_ISOLATION);
        properties.put("OPENGGF_TEST_TMP_ROOT", paths.tmp.toString());
        properties.put("OPENGGF_TEST_LWJGL_ROOT_TEMPLATE", paths.tmp.resolve(
                LWJGL_EXTRACTION_TEMPLATE).toString());
        properties.put("OPENGGF_TEST_ALLOWED_PHASES", allowedPhases(command));
        properties.put("OPENGGF_TEST_DIAGNOSTICS", paths.diagnostics.toString());
        properties.put("OPENGGF_ARTIFACT_ROOT", paths.artifacts.toString());
        properties.put("OPENGGF_DISTRIBUTION_ROOT", paths.distribution.toString());
        properties.put("OPENGGF_BUILD_DIRECTORY", paths.build.toString());
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
        Path session = manifest.toAbsolutePath().normalize().getParent();
        String exported = "manifest=" + manifest + "\n"
                + "run_id=" + runId + "\n"
                + "build_root=" + session.resolve("build") + "\n"
                + "tmp_root=" + session.resolve("tmp") + "\n"
                + "surefire_reports=" + session.resolve("surefire-reports") + "\n"
                + "trace_reports=" + session.resolve("trace-reports") + "\n"
                + "diagnostics_root=" + session.resolve("diagnostics") + "\n"
                + "artifact_root=" + session.resolve("artifacts") + "\n"
                + "distribution_root=" + session.resolve("distribution") + "\n";
        Files.writeString(temp, exported,
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
                                   List<String> reports, List<String> artifacts,
                                   ManifestContext context, long capacityFloor) {
        StorageAllocation allocation = context.allocation;
        CapacitySnapshot launch = context.launchCapacity;
        CapacitySnapshot completion = context.completionCapacity;
        CompactionResult compaction = context.compaction;
        boolean managedNumericInodes = allocation.tier == StorageTier.MANAGED_CODEX_TEST_SESSIONS;
        String numericInodeUnavailableReason = managedNumericInodes ? null
                : "numeric inode count is unavailable for " + allocation.tier
                + "; live availability probe is authoritative";
        return "{\n"
                + "  \"run_id\": \"" + escape(runId) + "\",\n"
                + "  \"state\": \"" + state + "\",\n"
                + "  \"manifest\": \"" + escape(paths.manifest.toString()) + "\",\n"
                + "  \"capability\": \"" + escape(capability) + "\",\n"
                + "  \"worktree\": \"" + escape(worktree.toString()) + "\",\n"
                + "  \"lease_path\": \"" + escape(lease.toString()) + "\",\n"
                + "  \"command_hash\": \"" + commandHash + "\",\n"
                + "  \"allowed_phases\": \"" + escape(allowedPhases) + "\",\n"
                + "  \"isolation\": \"" + SESSION_ISOLATION + "\",\n"
                + "  \"lwjgl_extraction\": \"" + LWJGL_EXTRACTION_ISOLATION + "\",\n"
                + "  \"lwjgl_extract_template\": \""
                + escape(paths.tmp.resolve(LWJGL_EXTRACTION_TEMPLATE).toString()) + "\",\n"
                + "  \"source_digest\": \"" + source + "\",\n"
                + "  \"runtime_inputs_digest\": \"" + runtime + "\",\n"
                + "  \"build_root\": \"" + escape(paths.build.toString()) + "\",\n"
                + "  \"tmp_root\": \"" + escape(paths.tmp.toString()) + "\",\n"
                + "  \"surefire_reports\": \"" + escape(paths.surefire.toString()) + "\",\n"
                + "  \"trace_reports\": \"" + escape(paths.trace.toString()) + "\",\n"
                + "  \"diagnostics_root\": \"" + escape(paths.diagnostics.toString()) + "\",\n"
                + "  \"artifact_root\": \"" + escape(paths.artifacts.toString()) + "\",\n"
                + "  \"distribution_root\": \"" + escape(paths.distribution.toString()) + "\",\n"
                + "  \"command_file\": \"" + escape(paths.command.toString()) + "\",\n"
                + "  \"storage_tier\": \"" + allocation.tier + "\",\n"
                + "  \"allocation_path\": \"" + escape(allocation.outputRoot.toString()) + "\",\n"
                + "  \"managed_root\": " + jsonNullablePath(allocation.managedRoot) + ",\n"
                + "  \"allocation_schema\": "
                + (allocation.allocationSchema == 0 ? "null" : allocation.allocationSchema) + ",\n"
                + "  \"helper_version\": " + jsonNullable(allocation.helperVersion) + ",\n"
                + "  \"filesystem_device\": \"" + escape(allocation.filesystemDevice) + "\",\n"
                + "  \"allocation_usable_bytes\": " + allocation.allocationCapacity.usableBytes + ",\n"
                + "  \"allocation_total_bytes\": " + allocation.allocationCapacity.totalBytes + ",\n"
                + "  \"allocation_usable_inodes\": "
                + jsonNullableLong(managedNumericInodes
                ? allocation.allocationCapacity.usableInodes : null) + ",\n"
                + "  \"numeric_inode_unavailable_reason\": "
                + jsonNullable(numericInodeUnavailableReason) + ",\n"
                + "  \"retention_deadline\": "
                + jsonNullable(allocation.retentionDeadline == null
                ? null : allocation.retentionDeadline.toString()) + ",\n"
                + "  \"allocation_not_applicable_reason\": "
                + jsonNullable(allocation.notApplicableReason) + ",\n"
                + "  \"storage_warning\": " + jsonNullable(allocation.warning) + ",\n"
                + "  \"allocation_verified\": true,\n"
                + "  \"capacity_floor_bytes\": " + capacityFloor + ",\n"
                + "  \"launch_usable_bytes\": " + launch.usableBytes + ",\n"
                + "  \"launch_total_bytes\": " + launch.totalBytes + ",\n"
                + "  \"launch_usable_inodes\": "
                + jsonNullableLong(managedNumericInodes ? launch.usableInodes : null) + ",\n"
                + "  \"launch_capacity_error\": "
                + jsonNullable(context.launchCapacityError) + ",\n"
                + "  \"launch_inode_probe_status\": "
                + jsonNullable(context.launchProbe == null
                ? null : context.launchProbe.status.name()) + ",\n"
                + "  \"launch_inode_probe_error\": "
                + jsonNullable(context.launchProbe == null ? null : context.launchProbe.error) + ",\n"
                + "  \"launch_directory_flush_status\": "
                + jsonNullable(context.launchProbe == null
                ? null : context.launchProbe.directoryFlushStatus.name()) + ",\n"
                + "  \"completion_usable_bytes\": "
                + jsonNullableLong(completion == null ? null : completion.usableBytes) + ",\n"
                + "  \"completion_total_bytes\": "
                + jsonNullableLong(completion == null ? null : completion.totalBytes) + ",\n"
                + "  \"completion_usable_inodes\": "
                + jsonNullableLong(completion == null || !managedNumericInodes
                ? null : completion.usableInodes) + ",\n"
                + "  \"completion_capacity_error\": "
                + jsonNullable(context.completionCapacityError) + ",\n"
                + "  \"completion_inode_probe_status\": "
                + jsonNullable(context.completionProbe == null
                ? null : context.completionProbe.status.name()) + ",\n"
                + "  \"completion_inode_probe_error\": "
                + jsonNullable(context.completionProbe == null
                ? null : context.completionProbe.error) + ",\n"
                + "  \"completion_directory_flush_status\": "
                + jsonNullable(context.completionProbe == null
                ? null : context.completionProbe.directoryFlushStatus.name()) + ",\n"
                + "  \"compaction_status\": "
                + jsonNullable(compaction == null ? null : compaction.status.name()) + ",\n"
                + "  \"compaction_removed_relative_paths\": "
                + (compaction == null ? "[]" : jsonArray(compaction.removedRelativePaths)) + ",\n"
                + "  \"compaction_reclaimed_bytes\": "
                + jsonNullableLong(compaction == null ? null : compaction.reclaimedBytes) + ",\n"
                + "  \"compaction_error\": "
                + jsonNullable(compaction == null ? null : compaction.error) + ",\n"
                + "  \"retain_ephemeral\": " + context.retainEphemeral + ",\n"
                + "  \"storage_finalization_error\": "
                + jsonNullable(context.storageFinalizationError) + ",\n"
                + "  \"reports\": " + jsonArray(reports) + ",\n"
                + "  \"artifacts\": " + jsonArray(artifacts) + "\n}\n";
    }

    private static List<String> reportInventory(Paths paths) throws IOException {
        return inventory(paths.surefire, paths.trace, paths.diagnostics);
    }

    private static List<String> artifactInventory(Paths paths) throws IOException {
        List<String> files = new ArrayList<>(inventory(paths.artifacts, paths.distribution));
        Path nativeRoot = paths.build.resolve("native-libs");
        try (Stream<Path> tree = Files.walk(paths.build)) {
            tree.filter(path -> (path.getParent() != null && path.getParent().equals(paths.build))
                    || path.startsWith(nativeRoot))
                    .filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".jar")
                            || path.getFileName().toString().equals("OpenGGF")
                            || path.getFileName().toString().endsWith(".dylib")
                            || path.getFileName().toString().endsWith(".dll")
                            || path.getFileName().toString().endsWith(".so"))
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .forEach(files::add);
        }
        return files.stream().distinct().sorted().toList();
    }

    private static List<String> inventory(Path... roots) throws IOException {
        List<String> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                        .map(path -> path.toAbsolutePath().normalize().toString())
                        .forEach(files::add);
            }
        }
        return files.stream().distinct().sorted().toList();
    }

    private static String jsonArray(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            json.append('\"').append(escape(values.get(index))).append('\"');
        }
        return json.append(']').toString();
    }

    private static String jsonNullable(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    private static String jsonNullablePath(Path value) {
        return value == null ? "null" : jsonNullable(value.toString());
    }

    private static String jsonNullableLong(Long value) {
        return value == null ? "null" : Long.toString(value);
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

    private static StorageAllocation resolveStorageAllocation(Path worktree, boolean allowSystemTmp)
            throws IOException {
        String override = System.getenv("OPENGGF_TEST_ROOT");
        if (override != null && !override.isBlank()) {
            Path root = validateRoot(path(override, "OPENGGF_TEST_ROOT"), "OPENGGF_TEST_ROOT");
            return localAllocation(root, StorageTier.EXPLICIT_OVERRIDE, null);
        }

        String configuredManagedRoot = configuredManagedRoot();
        if (configuredManagedRoot != null) {
            return managedAllocation(path(configuredManagedRoot, "managed scratch root"));
        }
        if (allowSystemTmp) {
            Path system = validateRoot(Path.of(System.getProperty("java.io.tmpdir"), "openggf-test-runs"),
                    "system temp");
            return localAllocation(system, StorageTier.SYSTEM_TMP_EXPLICIT, null);
        }
        Path project = worktree.resolve(".openggf/test-runs");
        if (Files.exists(project) || writableParent(project)) {
            Path root = validateRoot(project, "project session root");
            String warning = "OPENGGF_TEST_SESSION_WARNING storage_tier=PROJECT_LOCAL_FALLBACK "
                    + "reason=managed-scratch-not-configured action=install-agent-scratch";
            return localAllocation(root, StorageTier.PROJECT_LOCAL_FALLBACK, warning);
        }
        throw new StartupFailure("no writable session root; set OPENGGF_TEST_ROOT or --allow-system-tmp", 1);
    }

    private static StorageAllocation managedAllocation(Path configuredRoot) {
        try {
            if (!configuredRoot.isAbsolute() || Files.isSymbolicLink(configuredRoot)) {
                throw managedFailure("configured managed scratch root must be an absolute plain directory");
            }
        } catch (StartupFailure e) {
            throw e;
        } catch (RuntimeException e) {
            throw managedFailure("configured managed scratch root type cannot be verified");
        }
        Path canonicalConfiguredRoot;
        try {
            canonicalConfiguredRoot = configuredRoot.toRealPath();
        } catch (IOException | SecurityException e) {
            throw managedFailure("configured managed scratch root cannot be canonicalised: " + configuredRoot);
        }
        validateExistingDirectory(canonicalConfiguredRoot, "configured managed scratch root");

        runAgentScratch(List.of("verify"), "verification");
        String output = runAgentScratch(List.of("reserve-test-session", "--json"), "reservation");
        Map<String, JsonValue> record = parseReservation(output);
        int schema = requiredInt(record, "schema_version");
        if (schema != STORAGE_ALLOCATION_SCHEMA) {
            throw managedFailure("unsupported reservation schema: " + schema);
        }
        String tier = requiredString(record, "storage_tier");
        if (!StorageTier.MANAGED_CODEX_TEST_SESSIONS.name().equals(tier)) {
            throw managedFailure("unexpected storage tier: " + tier);
        }

        Path reportedRoot = canonicalReservationPath(requiredString(record, "managed_root"),
                "managed_root");
        if (!reportedRoot.equals(canonicalConfiguredRoot)) {
            throw managedFailure("reservation managed root does not match configured root");
        }
        Path codex = canonicalConfiguredRoot.resolve("codex");
        Path lane = codex.resolve("test-sessions");
        validateExistingDirectory(codex, "managed Codex lane");
        validateExistingDirectory(lane, "managed test-session lane");
        Path canonicalLane;
        try {
            canonicalLane = lane.toRealPath();
        } catch (IOException | SecurityException e) {
            throw managedFailure("managed test-session lane cannot be canonicalised");
        }
        if (!canonicalLane.equals(lane.toAbsolutePath().normalize())) {
            throw managedFailure("managed test-session lane traverses a symbolic link");
        }

        Path allocation = canonicalReservationPath(requiredString(record, "allocation_path"),
                "allocation_path");
        validateExistingDirectory(allocation, "managed session allocation");
        if (!canonicalLane.equals(allocation.getParent())) {
            throw managedFailure("reservation allocation is outside the managed test-session lane");
        }

        long device = requiredLong(record, "filesystem_device");
        long actualDevice = filesystemDevice(allocation);
        if (device < 0 || device != actualDevice
                || actualDevice != filesystemDevice(canonicalConfiguredRoot)
                || actualDevice != filesystemDevice(canonicalLane)) {
            throw managedFailure("reservation filesystem device does not match the allocation");
        }
        long usableBytes = requiredLong(record, "usable_bytes");
        long totalBytes = requiredLong(record, "total_bytes");
        long usableInodes = requiredLong(record, "usable_inodes");
        if (usableBytes < 0 || totalBytes <= 0 || usableBytes > totalBytes || usableInodes < 0) {
            throw managedFailure("reservation capacity values are outside their valid ranges");
        }

        Instant now = Instant.now();
        Instant retentionDeadline;
        try {
            retentionDeadline = Instant.parse(requiredString(record, "retention_deadline"));
        } catch (DateTimeParseException e) {
            throw managedFailure("reservation retention_deadline is not an ISO-8601 instant");
        }
        if (!retentionDeadline.isAfter(now)
                || retentionDeadline.isAfter(now.plus(MAX_MANAGED_RETENTION))) {
            throw managedFailure("reservation retention_deadline is outside the bounded seven-day policy");
        }
        String helperVersion = requiredString(record, "helper_version");
        if (!SUPPORTED_AGENT_SCRATCH_HELPER_VERSION.equals(helperVersion)) {
            throw managedFailure("unsupported reservation helper_version: " + helperVersion);
        }

        return new StorageAllocation(allocation, StorageTier.MANAGED_CODEX_TEST_SESSIONS,
                reportedRoot, schema, helperVersion, Long.toString(device),
                new CapacitySnapshot(usableBytes, totalBytes, usableInodes), retentionDeadline,
                null, null);
    }

    private static StorageAllocation localAllocation(Path root, StorageTier tier, String warning)
            throws IOException {
        FileStore store = Files.getFileStore(root);
        return new StorageAllocation(root, tier, null, 0, null,
                store.name().isBlank() ? store.type() : store.name(),
                new CapacitySnapshot(store.getUsableSpace(), store.getTotalSpace(), -1), null,
                "managed reservation fields do not apply to " + tier, warning);
    }

    private static String configuredManagedRoot() {
        String generic = nonBlank(System.getenv("AGENT_SCRATCH_ROOT"));
        String legacy = nonBlank(System.getenv("OGGF_SCRATCH_ROOT"));
        if (generic != null && legacy != null && !generic.equals(legacy)) {
            throw managedFailure("AGENT_SCRATCH_ROOT conflicts with OGGF_SCRATCH_ROOT");
        }
        return generic != null ? generic : legacy;
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Path path(String value, String name) {
        try {
            return Path.of(value);
        } catch (RuntimeException e) {
            throw new StartupFailure(name + " is not a valid path", 1);
        }
    }

    private static String runAgentScratch(List<String> arguments, String operation) {
        List<String> command = new ArrayList<>();
        command.add("agent-scratch");
        command.addAll(arguments);
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException | SecurityException e) {
            throw managedFailure("agent-scratch " + operation + " could not start");
        }
        boolean finished;
        try {
            finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                throw managedFailure("agent-scratch " + operation + " timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw managedFailure("agent-scratch " + operation + " failed with exit "
                        + process.exitValue() + helperDetail(output));
            }
            if (operation.equals("reservation") && output.isBlank()) {
                throw managedFailure("agent-scratch reservation returned no JSON");
            }
            return output;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw managedFailure("agent-scratch " + operation + " was interrupted");
        } catch (IOException e) {
            throw managedFailure("agent-scratch " + operation + " output could not be read");
        }
    }

    private static String helperDetail(String output) {
        if (output.isBlank()) {
            return "";
        }
        String oneLine = output.replace('\n', ' ').replace('\r', ' ');
        return ": " + oneLine.substring(0, Math.min(oneLine.length(), 240));
    }

    private static Path canonicalReservationPath(String value, String field) {
        Path reported = path(value, "reservation " + field);
        if (!reported.isAbsolute() || value.contains("\n") || value.contains("\r")) {
            throw managedFailure("reservation " + field + " must be an absolute path without newlines");
        }
        try {
            Path canonical = reported.toRealPath();
            if (!canonical.toString().equals(value)) {
                throw managedFailure("reservation " + field + " is not canonical");
            }
            return canonical;
        } catch (IOException | SecurityException e) {
            throw managedFailure("reservation " + field + " cannot be canonicalised");
        }
    }

    private static void validateExistingDirectory(Path root, String name) {
        try {
            if (!Files.isDirectory(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(root)) {
                throw managedFailure(name + " is not a plain directory");
            }
            String current = root.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name")).getName();
            String owner = Files.getOwner(root).getName();
            if (!owner.equals(current)) {
                throw managedFailure(name + " is not owned by the current user");
            }
        } catch (StartupFailure e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw managedFailure(name + " ownership or directory type cannot be verified");
        }
    }

    private static long filesystemDevice(Path path) {
        try {
            return ((Number) Files.getAttribute(path, "unix:dev",
                    java.nio.file.LinkOption.NOFOLLOW_LINKS)).longValue();
        } catch (IOException | RuntimeException e) {
            throw managedFailure("filesystem device identity cannot be verified for " + path);
        }
    }

    private static StartupFailure managedFailure(String detail) {
        return new StartupFailure("managed scratch is configured but unavailable: "
                + boundedSingleLine(detail), 1);
    }

    private static String boundedSingleLine(String value) {
        StringBuilder safe = new StringBuilder(Math.min(value.length(), MAX_MANAGED_DIAGNOSTIC_LENGTH));
        for (int index = 0; index < value.length() && safe.length() < MAX_MANAGED_DIAGNOSTIC_LENGTH; index++) {
            char character = value.charAt(index);
            int type = Character.getType(character);
            if (Character.isISOControl(character) || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                safe.append(' ');
            } else {
                safe.append(character);
            }
        }
        return safe.toString();
    }

    private static Map<String, JsonValue> parseReservation(String json) {
        Map<String, JsonValue> values = new FlatJsonParser(json).parse();
        for (String field : RESERVATION_FIELDS) {
            if (!values.containsKey(field)) {
                throw managedFailure("reservation JSON is missing required field " + field);
            }
        }
        return values;
    }

    private static String requiredString(Map<String, JsonValue> values, String field) {
        JsonValue value = values.get(field);
        if (value == null || value.type != JsonType.STRING) {
            throw managedFailure("reservation field " + field + " must be a string");
        }
        return value.value;
    }

    private static long requiredLong(Map<String, JsonValue> values, String field) {
        JsonValue value = values.get(field);
        if (value == null || value.type != JsonType.INTEGER) {
            throw managedFailure("reservation field " + field + " must be an integer");
        }
        try {
            return Long.parseLong(value.value);
        } catch (NumberFormatException e) {
            throw managedFailure("reservation field " + field + " is outside the integer range");
        }
    }

    private static int requiredInt(Map<String, JsonValue> values, String field) {
        long value = requiredLong(values, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw managedFailure("reservation field " + field + " is outside the integer range");
        }
        return (int) value;
    }

    private enum JsonType {
        STRING,
        INTEGER
    }

    private record JsonValue(JsonType type, String value) {
    }

    private static final class FlatJsonParser {
        private final String source;
        private int index;

        private FlatJsonParser(String source) {
            this.source = source;
        }

        private Map<String, JsonValue> parse() {
            Map<String, JsonValue> values = new LinkedHashMap<>();
            whitespace();
            expect('{');
            whitespace();
            if (take('}')) {
                finish();
                return values;
            }
            while (true) {
                String key = string();
                if (!RESERVATION_FIELDS.contains(key)) {
                    throw managedFailure("reservation JSON contains unknown field " + key);
                }
                whitespace();
                expect(':');
                whitespace();
                JsonValue value = value();
                if (values.putIfAbsent(key, value) != null) {
                    throw managedFailure("reservation JSON contains duplicate field " + key);
                }
                whitespace();
                if (take('}')) {
                    finish();
                    return values;
                }
                expect(',');
                whitespace();
            }
        }

        private JsonValue value() {
            if (peek() == '"') {
                return new JsonValue(JsonType.STRING, string());
            }
            int start = index;
            take('-');
            if (index >= source.length() || !Character.isDigit(source.charAt(index))) {
                throw malformed("expected a string or integer value");
            }
            if (source.charAt(index) == '0') {
                index++;
                if (index < source.length() && Character.isDigit(source.charAt(index))) {
                    throw malformed("integer has a leading zero");
                }
            } else {
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            }
            return new JsonValue(JsonType.INTEGER, source.substring(start, index));
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < source.length()) {
                char character = source.charAt(index++);
                if (character == '"') {
                    return value.toString();
                }
                if (character < 0x20) {
                    throw malformed("unescaped control character in string");
                }
                if (character != '\\') {
                    value.append(character);
                    continue;
                }
                if (index >= source.length()) {
                    throw malformed("unfinished string escape");
                }
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(unicode());
                    default -> throw malformed("invalid string escape");
                }
            }
            throw malformed("unterminated string");
        }

        private char unicode() {
            if (index + 4 > source.length()) {
                throw malformed("unfinished Unicode escape");
            }
            int value = 0;
            for (int count = 0; count < 4; count++) {
                int digit = Character.digit(source.charAt(index++), 16);
                if (digit < 0) {
                    throw malformed("invalid Unicode escape");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private void finish() {
            whitespace();
            if (index != source.length()) {
                throw malformed("trailing content after reservation object");
            }
        }

        private void whitespace() {
            while (index < source.length()) {
                char character = source.charAt(index);
                if (character != ' ' && character != '\t' && character != '\r' && character != '\n') {
                    return;
                }
                index++;
            }
        }

        private char peek() {
            return index < source.length() ? source.charAt(index) : '\0';
        }

        private boolean take(char expected) {
            if (peek() != expected) {
                return false;
            }
            index++;
            return true;
        }

        private void expect(char expected) {
            if (!take(expected)) {
                throw malformed("expected '" + expected + "'");
            }
        }

        private StartupFailure malformed(String detail) {
            return managedFailure("malformed reservation JSON at offset " + index + ": " + detail);
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
        private final StorageAllocation allocation;
        private final CapacitySnapshot launchCapacity;
        private final long capacityFloor;
        private final CapacityProbe capacityProbe;
        private final LiveProbeResult launchProbe;
        private volatile Process child;
        private volatile boolean completed;

        private ShutdownState(Paths paths, String runId, Path worktree, Path leasePath,
                              String sourceBefore, String runtimeBefore, List<String> command,
                              String commandHash, String capability, String allowedPhases,
                              Path exportFile, Lease lease, StorageAllocation allocation,
                              CapacitySnapshot launchCapacity, long capacityFloor,
                              CapacityProbe capacityProbe, LiveProbeResult launchProbe) {
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
            this.allocation = allocation;
            this.launchCapacity = launchCapacity;
            this.capacityFloor = capacityFloor;
            this.capacityProbe = capacityProbe;
            this.launchProbe = launchProbe;
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
                StorageObservation completionObservation = observeStorage(
                        paths, allocation, capacityProbe, ProbePhase.COMPLETION);
                ManifestContext terminalContext = new ManifestContext(allocation, launchCapacity,
                        completionObservation.capacity, null, false, completionObservation.error(),
                        null, launchProbe, completionObservation.capacityError,
                        completionObservation.liveProbe);
                writeManifest(paths.manifest, manifest(paths, runId, state, worktree, leasePath,
                        commandHash, capability, allowedPhases, sourceAfter, runtimeAfter,
                        reportInventory(paths), artifactInventory(paths), terminalContext,
                        capacityFloor));
                if (exportFile != null) {
                    writeExport(exportFile, paths.manifest, runId);
                }
                printEndMarker(paths, runId, 143, state, false, terminalContext,
                        treeStopped);
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
                         Path manifest, Path mavenLog, Path command) {
        static Paths create(Path session) throws IOException {
            Path build = Files.createDirectories(session.resolve("build"));
            Path tmp = Files.createDirectories(session.resolve("tmp"));
            Path surefire = Files.createDirectories(session.resolve("surefire-reports"));
            Path trace = Files.createDirectories(session.resolve("trace-reports"));
            Path diagnostics = Files.createDirectories(session.resolve("diagnostics"));
            Path artifacts = Files.createDirectories(session.resolve("artifacts"));
            Path distribution = Files.createDirectories(session.resolve("distribution"));
            return new Paths(session, build, tmp, surefire, trace, diagnostics, artifacts,
                    distribution, session.resolve("manifest.json"), session.resolve("maven.log"),
                    session.resolve("command.txt"));
        }
    }

    private static final class Options {
        Path exportFile;
        Path lockRoot;
        Path reclaim;
        boolean reuseStale;
        boolean allowSystemTmp;
        boolean verbose;
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
                    case "--quiet" -> options.verbose = false;
                    case "--verbose" -> options.verbose = true;
                    case "--reclaim" -> options.reclaim = Path.of(require(args, ++i, arg));
                    case "--reuse-stale" -> options.reuseStale = true;
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
