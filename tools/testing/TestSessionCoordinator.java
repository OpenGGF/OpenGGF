import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

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
    private static final AtomicInteger TERMINAL_MANIFEST_SYNC_CALLS = new AtomicInteger();
    private static final Set<String> TERMINAL_SESSION_STATES = Set.of(
            "PASSED", "FAILED", "INVALID_IDENTITY_CHANGED", "ABORTED",
            "STARTUP_FAILED", "STORAGE_FINALIZATION_FAILED");
    private static final List<String> COMPACTABLE_RELATIVE_PATHS = List.of(
            "tmp", "build/test-classes/traces");
    private static final Set<String> RESERVATION_FIELDS = Set.of(
            "schema_version", "storage_tier", "managed_root", "allocation_path",
            "lease_root", "filesystem_device", "usable_bytes", "total_bytes",
            "inode_count_status", "usable_inodes",
            "retention_deadline", "helper_version");

    private enum StorageTier {
        EXPLICIT_OVERRIDE,
        MANAGED_CODEX_TEST_SESSIONS,
        PROJECT_LOCAL_FALLBACK,
        SYSTEM_TMP_EXPLICIT
    }

    private record CapacitySnapshot(long usableBytes, long totalBytes, long usableInodes) {
    }

    private enum InodeCountStatus {
        MEASURED,
        UNAVAILABLE_DYNAMIC
    }

    private record InodeSnapshot(
            InodeCountStatus status, Long usableInodes, String unavailableReason) {
        private InodeSnapshot {
            if (status == null) {
                throw new IllegalArgumentException("inode count status is required");
            }
            if (status == InodeCountStatus.MEASURED) {
                if (usableInodes == null || usableInodes < 0 || unavailableReason != null) {
                    throw new IllegalArgumentException(
                            "measured inode snapshot requires a nonnegative count and no reason");
                }
            } else if (usableInodes != null
                    || unavailableReason == null || unavailableReason.isBlank()) {
                throw new IllegalArgumentException(
                        "unavailable inode snapshot requires null count and a reason");
            }
        }
    }

    private record StorageAllocation(
            Path outputRoot, StorageTier tier, Path managedRoot,
            Path managedLeaseRoot,
            int allocationSchema, String helperVersion, String filesystemDevice,
            CapacitySnapshot allocationCapacity, InodeSnapshot allocationInodes,
            Instant retentionDeadline,
            String notApplicableReason, String warning) {
    }

    private enum CompactionStatus {
        COMPACTED,
        NOTHING_TO_REMOVE,
        RETAINED_BY_REQUEST,
        RETAINED_PLATFORM_UNSUPPORTED,
        FAILED,
        REFUSED
    }

    private record CompactionResult(
            CompactionStatus status,
            List<String> removedRelativePaths,
            List<String> partiallyModifiedRelativePaths,
            long reclaimedBytes,
            String error) {
    }

    private enum DirectorySyncStatus {
        SYNCED,
        UNSUPPORTED,
        FAILED
    }

    private record DirectorySyncResult(DirectorySyncStatus status, String error) {
    }

    private record LogCompressionResult(
            Path publishedLog, String error, boolean published,
            DirectorySyncStatus gzipDirectorySyncStatus,
            DirectorySyncStatus manifestDirectorySyncStatus,
            DirectorySyncStatus sourceDeleteDirectorySyncStatus) {
        private LogCompressionResult withManifestSync(DirectorySyncStatus status) {
            return new LogCompressionResult(publishedLog, error, published,
                    gzipDirectorySyncStatus, status, sourceDeleteDirectorySyncStatus);
        }

        private LogCompressionResult withSourceDeleteSync(DirectorySyncStatus status) {
            return new LogCompressionResult(publishedLog, error, published,
                    gzipDirectorySyncStatus, manifestDirectorySyncStatus, status);
        }

        private LogCompressionResult withError(String additionalError) {
            return new LogCompressionResult(publishedLog, combineStorageErrors(error, additionalError),
                    published, gzipDirectorySyncStatus, manifestDirectorySyncStatus,
                    sourceDeleteDirectorySyncStatus);
        }
    }

    private record BoundEntry(
            Path relativePath, Object fileKey, boolean directory, long size) {
    }

    private record CandidateBinding(
            String relativePath, Map<Path, BoundEntry> entries) {
    }

    private record NativeCompactionControl(
            boolean forceNoSecureStream,
            boolean stableFileKeys,
            String providerReason,
            Consumer<String> beforeCandidateMove,
            BiPredicate<Object, Object> movedIdentityMatches,
            Predicate<Path> reparsePoint) {
    }

    private static final class DeletionProgress {
        private long reclaimedBytes;
        private boolean candidateModified;
        private boolean candidateFullyRemoved;
        private String currentCandidate;
    }

    private record SessionDirectoryIdentity(
            Path realPath, Object fileKey, FileStore fileStore) {
    }

    private record ManifestContext(
            StorageAllocation allocation,
            CapacitySnapshot launchCapacity,
            CapacitySnapshot completionCapacity,
            CompactionResult compaction,
            boolean retainEphemeral,
            String storageFinalizationError,
            LogCompressionResult logCompression,
            SessionDirectoryIdentity sessionIdentity,
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
        Path lockParent = resolveLockParent(worktree, options.lockRoot, allocation);
        String runId = createRunId();
        Path namespace = namespace(lockParent, worktree,
                externalLockRequested(options, allocation));
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
        SessionDirectoryIdentity sessionIdentity = captureSessionDirectoryIdentity(session);
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
                    defaultCapacityFloor, failure.getMessage(), lease, capacityProbe,
                    sessionIdentity, options.retainEphemeral);
        }
        if (launchObservation.capacityError != null) {
            return startupFailed(paths, runId, worktree, leasePath, commandHash, capability,
                    allowedPhases, sourceBefore, runtimeBefore, allocation, launchObservation,
                    capacityFloor, launchObservation.capacityError, lease, capacityProbe,
                    sessionIdentity, options.retainEphemeral);
        }
        boolean measuredZeroInodes = allocation.allocationInodes != null
                && allocation.allocationInodes.status == InodeCountStatus.MEASURED
                && launchCapacity.usableInodes == 0;
        if (launchCapacity.usableBytes < capacityFloor || measuredZeroInodes) {
            String reason = measuredZeroInodes
                    ? "allocation filesystem reports zero usable inodes"
                    : "allocation filesystem is below the required free-byte floor";
            return startupFailed(paths, runId, worktree, leasePath, commandHash, capability,
                    allowedPhases, sourceBefore, runtimeBefore, allocation, launchObservation,
                    capacityFloor, reason, lease, capacityProbe, sessionIdentity,
                    options.retainEphemeral);
        }
        launchObservation = new StorageObservation(launchCapacity, null,
                liveStorageProbe(paths.session, ProbePhase.LAUNCH));
        if (launchObservation.liveProbe.status == InodeProbeStatus.FAILED) {
            return startupFailed(paths, runId, worktree, leasePath, commandHash, capability,
                    allowedPhases, sourceBefore, runtimeBefore, allocation, launchObservation,
                    capacityFloor, launchObservation.liveProbe.error, lease, capacityProbe,
                    sessionIdentity, options.retainEphemeral);
        }
        ManifestContext runningContext = new ManifestContext(allocation, launchCapacity,
                null, null, options.retainEphemeral, null, null, sessionIdentity,
                null, launchObservation.liveProbe, null, null);
        writeManifest(paths.manifest, manifest(paths, runId, "RUNNING", worktree, leasePath,
                commandHash, capability, allowedPhases, sourceBefore, runtimeBefore,
                List.of(), List.of(), runningContext, capacityFloor));
        printStartMarker(paths, runId, leasePath, runningContext, capacityFloor, "RUNNING");

        ShutdownState shutdown = new ShutdownState(paths, runId, worktree, leasePath,
                sourceBefore, runtimeBefore, options.command, commandHash, capability,
                allowedPhases, options.exportFile, lease, allocation, launchCapacity,
                capacityFloor, capacityProbe, launchObservation.liveProbe, sessionIdentity,
                options.retainEphemeral);
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
            shutdown.outputDrainComplete.countDown();
            if (shutdown.claimNormalFinalization()) {
                try {
                    String sourceAfter = sourceDigest(worktree);
                    String runtimeAfter = runtimeDigest(options.command);
                    boolean sourceChanged = !sourceBefore.equals(sourceAfter);
                    boolean runtimeChanged = !runtimeBefore.equals(runtimeAfter);
                    boolean leaseChanged = !leaseStillOwned(lease.namespace, leasePath, runId);
                    identityChanged = sourceChanged || runtimeChanged || leaseChanged;
                    boolean valid = !identityChanged && !interrupted;
                    String primaryState = interrupted ? "ABORTED"
                                                      : (identityChanged ? "INVALID_IDENTITY_CHANGED"
                                                                         : (exitCode == 0 ? "PASSED" : "FAILED"));
                    String state = primaryState;
                    StorageObservation completionObservation =
                            observeStorage(paths, allocation, capacityProbe, ProbePhase.COMPLETION);
                    String storageFinalizationError = completionObservation.error();
                    if (storageFinalizationError != null && state.equals("PASSED")) {
                        state = "STORAGE_FINALIZATION_FAILED";
                        exitCode = 1;
                        valid = false;
                    }
                    List<String> reports = reportInventory(paths);
                    List<String> artifacts = artifactInventory(paths);
                    ManifestContext preCompactionContext = new ManifestContext(
                            allocation, launchCapacity, completionObservation.capacity, null, options.retainEphemeral,
                            storageFinalizationError, null, sessionIdentity, null, launchObservation.liveProbe,
                            completionObservation.capacityError, completionObservation.liveProbe);
                    writeManifest(paths.manifest, manifest(paths, runId, state, worktree, leasePath, commandHash,
                                                           capability, allowedPhases, sourceAfter, runtimeAfter,
                                                           reports, artifacts, preCompactionContext, capacityFloor));
                    CompactionResult compaction = compactTerminalSession(paths, state, options.retainEphemeral, reports,
                                                                         artifacts, sessionIdentity);
                    storageFinalizationError =
                            combineStorageErrors(storageFinalizationError, compactionFailure(compaction));
                    LogCompressionResult logCompression = compressTerminalLog(paths);
                    storageFinalizationError = combineStorageErrors(storageFinalizationError, logCompression.error);
                    if (storageFinalizationError != null && primaryState.equals("PASSED")) {
                        state = "STORAGE_FINALIZATION_FAILED";
                        exitCode = 1;
                        valid = false;
                    }
                    ManifestContext terminalContext =
                            new ManifestContext(allocation, launchCapacity, completionObservation.capacity, compaction,
                                                options.retainEphemeral, storageFinalizationError, logCompression,
                                                sessionIdentity, null, launchObservation.liveProbe,
                                                completionObservation.capacityError, completionObservation.liveProbe);
                    DirectorySyncResult manifestBarrier = writeTerminalManifest(paths.manifest,
                            manifest(paths, runId, state, worktree, leasePath, commandHash,
                                    capability, allowedPhases, sourceAfter, runtimeAfter,
                                    reports, artifacts, terminalContext, capacityFloor));
                    logCompression = logCompression.withManifestSync(manifestBarrier.status);
                    if (manifestBarrier.status == DirectorySyncStatus.FAILED) {
                        String barrierError = "terminal manifest directory sync failed: "
                                + manifestBarrier.error;
                        logCompression = logCompression.withError(barrierError);
                        storageFinalizationError = combineStorageErrors(
                                storageFinalizationError, barrierError);
                        if (primaryState.equals("PASSED")) {
                            state = "STORAGE_FINALIZATION_FAILED";
                            exitCode = 1;
                            valid = false;
                        }
                    }
                    terminalContext = new ManifestContext(
                            allocation, launchCapacity, completionObservation.capacity, compaction,
                            options.retainEphemeral, storageFinalizationError, logCompression,
                            sessionIdentity, null, launchObservation.liveProbe,
                            completionObservation.capacityError, completionObservation.liveProbe);
                    DirectorySyncResult recordedBarrier = writeTerminalManifest(paths.manifest,
                            manifest(paths, runId, state, worktree, leasePath, commandHash,
                                    capability, allowedPhases, sourceAfter, runtimeAfter,
                                    reports, artifacts, terminalContext, capacityFloor));
                    logCompression = logCompression.withManifestSync(recordedBarrier.status);
                    if (recordedBarrier.status == DirectorySyncStatus.FAILED) {
                        String barrierError = "recorded terminal manifest directory sync failed: "
                                + recordedBarrier.error;
                        logCompression = logCompression.withError(barrierError);
                        storageFinalizationError = combineStorageErrors(
                                storageFinalizationError, barrierError);
                        if (primaryState.equals("PASSED")) {
                            state = "STORAGE_FINALIZATION_FAILED";
                            exitCode = 1;
                            valid = false;
                        }
                    }
                    if (manifestBarrier.status != DirectorySyncStatus.FAILED
                            && recordedBarrier.status != DirectorySyncStatus.FAILED) {
                        LogCompressionResult removal = removeCompressedLogSource(paths, logCompression);
                        if (!java.util.Objects.equals(removal.error, logCompression.error)) {
                            storageFinalizationError = combineStorageErrors(
                                    storageFinalizationError, removal.error);
                            if (primaryState.equals("PASSED")) {
                                state = "STORAGE_FINALIZATION_FAILED";
                                exitCode = 1;
                                valid = false;
                            }
                        }
                        logCompression = removal;
                    }
                    terminalContext = new ManifestContext(
                            allocation, launchCapacity, completionObservation.capacity, compaction,
                            options.retainEphemeral, storageFinalizationError, logCompression,
                            sessionIdentity, null, launchObservation.liveProbe,
                            completionObservation.capacityError, completionObservation.liveProbe);
                    DirectorySyncResult finalEvidenceBarrier = writeTerminalManifest(paths.manifest,
                            manifest(paths, runId, state, worktree, leasePath, commandHash,
                                    capability, allowedPhases, sourceAfter, runtimeAfter,
                                    reports, artifacts, terminalContext, capacityFloor));
                    if (finalEvidenceBarrier.status == DirectorySyncStatus.FAILED) {
                        String barrierError = "final terminal manifest directory sync failed: "
                                + finalEvidenceBarrier.error;
                        logCompression = logCompression.withManifestSync(DirectorySyncStatus.FAILED)
                                .withError(barrierError);
                        storageFinalizationError = combineStorageErrors(
                                storageFinalizationError, barrierError);
                        if (primaryState.equals("PASSED")) {
                            state = "STORAGE_FINALIZATION_FAILED";
                            exitCode = 1;
                            valid = false;
                        }
                        terminalContext = new ManifestContext(
                                allocation, launchCapacity, completionObservation.capacity, compaction,
                                options.retainEphemeral, storageFinalizationError, logCompression,
                                sessionIdentity, null, launchObservation.liveProbe,
                                completionObservation.capacityError, completionObservation.liveProbe);
                        writeTerminalManifest(paths.manifest,
                                manifest(paths, runId, state, worktree, leasePath, commandHash,
                                        capability, allowedPhases, sourceAfter, runtimeAfter,
                                        reports, artifacts, terminalContext, capacityFloor));
                    }
                    if (options.exportFile != null) {
                        writeExport(options.exportFile, paths.manifest, runId);
                    }
                    printEndMarker(paths, runId, exitCode, state, valid, terminalContext);
                    lease.close();
                } finally {
                    shutdown.completeNormalFinalization();
                }
            }
        }
        return interrupted || identityChanged ? (exitCode == 0 ? 1 : exitCode) : exitCode;
    }

    private static int startupFailed(Paths paths, String runId, Path worktree, Path leasePath, String commandHash,
                                     String capability, String allowedPhases, String source, String runtime,
                                     StorageAllocation allocation, StorageObservation launchObservation,
                                     long capacityFloor, String reason, Lease lease, CapacityProbe capacityProbe,
                                     SessionDirectoryIdentity sessionIdentity, boolean retainEphemeral)
            throws Exception {
        try {
            if (!Files.exists(paths.mavenLog)) {
                Files.createFile(paths.mavenLog);
            }
            StorageObservation completionObservation =
                    observeStorage(paths, allocation, capacityProbe, ProbePhase.COMPLETION);
            String storageFinalizationError = completionObservation.error();
            List<String> reports = reportInventory(paths);
            List<String> artifacts = artifactInventory(paths);
            ManifestContext preCompactionContext = new ManifestContext(
                    allocation, launchObservation.capacity, completionObservation.capacity,
                    null, retainEphemeral, storageFinalizationError, null, sessionIdentity,
                    launchObservation.capacityError, launchObservation.liveProbe,
                    completionObservation.capacityError, completionObservation.liveProbe);
            writeManifest(paths.manifest, manifest(paths, runId, "STARTUP_FAILED", worktree,
                    leasePath, commandHash, capability, allowedPhases, source, runtime,
                    reports, artifacts, preCompactionContext, capacityFloor));
            CompactionResult compaction = compactTerminalSession(paths, "STARTUP_FAILED",
                    retainEphemeral, reports, artifacts, sessionIdentity);
            storageFinalizationError = combineStorageErrors(storageFinalizationError,
                    compactionFailure(compaction));
            LogCompressionResult logCompression = compressTerminalLog(paths);
            storageFinalizationError = combineStorageErrors(storageFinalizationError,
                    logCompression.error);
            ManifestContext context = new ManifestContext(
                    allocation, launchObservation.capacity, completionObservation.capacity,
                    compaction, retainEphemeral, storageFinalizationError, logCompression, sessionIdentity,
                    launchObservation.capacityError, launchObservation.liveProbe,
                    completionObservation.capacityError, completionObservation.liveProbe);
            DirectorySyncResult manifestBarrier = writeTerminalManifest(paths.manifest,
                    manifest(paths, runId, "STARTUP_FAILED", worktree,
                            leasePath, commandHash, capability, allowedPhases, source, runtime,
                            reports, artifacts, context, capacityFloor));
            logCompression = logCompression.withManifestSync(manifestBarrier.status);
            if (manifestBarrier.status == DirectorySyncStatus.FAILED) {
                String barrierError = "terminal manifest directory sync failed: "
                        + manifestBarrier.error;
                logCompression = logCompression.withError(barrierError);
                storageFinalizationError = combineStorageErrors(storageFinalizationError,
                        barrierError);
            }
            context = new ManifestContext(
                    allocation, launchObservation.capacity, completionObservation.capacity,
                    compaction, retainEphemeral, storageFinalizationError, logCompression,
                    sessionIdentity, launchObservation.capacityError,
                    launchObservation.liveProbe, completionObservation.capacityError,
                    completionObservation.liveProbe);
            DirectorySyncResult recordedBarrier = writeTerminalManifest(paths.manifest,
                    manifest(paths, runId, "STARTUP_FAILED", worktree,
                    leasePath, commandHash, capability, allowedPhases, source, runtime,
                    reports, artifacts, context, capacityFloor));
            logCompression = logCompression.withManifestSync(recordedBarrier.status);
            if (recordedBarrier.status == DirectorySyncStatus.FAILED) {
                String barrierError = "recorded terminal manifest directory sync failed: "
                        + recordedBarrier.error;
                logCompression = logCompression.withError(barrierError);
                storageFinalizationError = combineStorageErrors(storageFinalizationError,
                        barrierError);
            }
            if (manifestBarrier.status != DirectorySyncStatus.FAILED
                    && recordedBarrier.status != DirectorySyncStatus.FAILED) {
                LogCompressionResult removal = removeCompressedLogSource(paths, logCompression);
                if (!java.util.Objects.equals(removal.error, logCompression.error)) {
                    storageFinalizationError = combineStorageErrors(storageFinalizationError,
                            removal.error);
                }
                logCompression = removal;
            }
            context = new ManifestContext(
                    allocation, launchObservation.capacity, completionObservation.capacity,
                    compaction, retainEphemeral, storageFinalizationError, logCompression,
                    sessionIdentity, launchObservation.capacityError,
                    launchObservation.liveProbe, completionObservation.capacityError,
                    completionObservation.liveProbe);
            DirectorySyncResult finalEvidenceBarrier = writeTerminalManifest(paths.manifest,
                    manifest(paths, runId, "STARTUP_FAILED", worktree,
                            leasePath, commandHash, capability, allowedPhases, source, runtime,
                            reports, artifacts, context, capacityFloor));
            if (finalEvidenceBarrier.status == DirectorySyncStatus.FAILED) {
                String barrierError = "final terminal manifest directory sync failed: "
                        + finalEvidenceBarrier.error;
                logCompression = logCompression.withManifestSync(DirectorySyncStatus.FAILED)
                        .withError(barrierError);
                storageFinalizationError = combineStorageErrors(storageFinalizationError,
                        barrierError);
                context = new ManifestContext(
                        allocation, launchObservation.capacity, completionObservation.capacity,
                        compaction, retainEphemeral, storageFinalizationError, logCompression,
                        sessionIdentity, launchObservation.capacityError,
                        launchObservation.liveProbe, completionObservation.capacityError,
                        completionObservation.liveProbe);
                writeTerminalManifest(paths.manifest,
                        manifest(paths, runId, "STARTUP_FAILED", worktree,
                                leasePath, commandHash, capability, allowedPhases, source, runtime,
                                reports, artifacts, context, capacityFloor));
            }
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
        Path publishedLog = context.logCompression == null
                ? paths.mavenLog : context.logCompression.publishedLog;
        System.out.println("OPENGGF_TEST_RUN_START run_id=" + markerToken(runId) + " isolation="
                + markerToken(SESSION_ISOLATION) + " lwjgl=" + markerToken(LWJGL_EXTRACTION_ISOLATION)
                + " manifest=" + markerToken(paths.manifest.toString())
                + " lease=" + markerToken(leasePath.toString())
                + " log=" + markerToken(publishedLog.toString())
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
        Path terminalLog = context.logCompression == null
                ? paths.mavenLog : context.logCompression.publishedLog;
        System.out.println("OPENGGF_TEST_RUN_END run_id=" + markerToken(runId) + " isolation="
                + markerToken(SESSION_ISOLATION) + " lwjgl=" + markerToken(LWJGL_EXTRACTION_ISOLATION)
                + " exit_code=" + exitCode + " state=" + markerToken(state)
                + " valid=" + valid + " manifest=" + markerToken(paths.manifest.toString())
                + " log=" + markerToken(terminalLog.toString())
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
        Path lockParent = resolveLockParent(worktree, options.lockRoot, allocation);
        String runId = createRunId();
        Path namespace = namespace(lockParent, worktree,
                externalLockRequested(options, allocation));
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
                    ? namespace(lockParent, worktree, externalLockRequested(options, allocation))
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
        InodeSnapshot allocationInodes = allocation.allocationInodes;
        String numericInodeUnavailableReason = allocationInodes != null
                ? allocationInodes.unavailableReason
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
                + "  \"log\": \"" + escape(context.logCompression == null
                ? paths.mavenLog.toString() : context.logCompression.publishedLog.toString()) + "\",\n"
                + "  \"storage_tier\": \"" + allocation.tier + "\",\n"
                + "  \"allocation_path\": \"" + escape(allocation.outputRoot.toString()) + "\",\n"
                + "  \"managed_root\": " + jsonNullablePath(allocation.managedRoot) + ",\n"
                + "  \"allocation_schema\": "
                + (allocation.allocationSchema == 0 ? "null" : allocation.allocationSchema) + ",\n"
                + "  \"helper_version\": " + jsonNullable(allocation.helperVersion) + ",\n"
                + "  \"filesystem_device\": \"" + escape(allocation.filesystemDevice) + "\",\n"
                + "  \"allocation_usable_bytes\": " + allocation.allocationCapacity.usableBytes + ",\n"
                + "  \"allocation_total_bytes\": " + allocation.allocationCapacity.totalBytes + ",\n"
                + "  \"allocation_inode_count_status\": "
                + jsonNullable(allocationInodes == null ? null : allocationInodes.status.name()) + ",\n"
                + "  \"allocation_usable_inodes\": "
                + jsonNullableLong(allocationInodes == null
                ? null : allocationInodes.usableInodes) + ",\n"
                + "  \"allocation_usable_inodes_reason\": "
                + jsonNullable(numericInodeUnavailableReason) + ",\n"
                + "  \"numeric_inode_unavailable_reason\": "
                + jsonNullable(numericInodeUnavailableReason) + ",\n"
                + "  \"retention_deadline\": "
                + jsonNullable(allocation.retentionDeadline == null
                ? null : allocation.retentionDeadline.toString()) + ",\n"
                + "  \"allocation_not_applicable_reason\": "
                + jsonNullable(allocation.notApplicableReason) + ",\n"
                + "  \"storage_warning\": " + jsonNullable(allocation.warning) + ",\n"
                + "  \"allocation_verified\": true,\n"
                + "  \"session_real_path\": "
                + jsonNullablePath(context.sessionIdentity == null
                ? null : context.sessionIdentity.realPath) + ",\n"
                + "  \"session_file_key\": "
                + jsonNullable(context.sessionIdentity == null
                || context.sessionIdentity.fileKey == null
                ? null : String.valueOf(context.sessionIdentity.fileKey)) + ",\n"
                + "  \"session_file_store\": "
                + jsonNullable(context.sessionIdentity == null ? null
                : context.sessionIdentity.fileStore.name() + " ("
                + context.sessionIdentity.fileStore.type() + ")") + ",\n"
                + "  \"capacity_floor_bytes\": " + capacityFloor + ",\n"
                + "  \"launch_usable_bytes\": " + launch.usableBytes + ",\n"
                + "  \"launch_total_bytes\": " + launch.totalBytes + ",\n"
                + "  \"launch_usable_inodes\": null,\n"
                + "  \"launch_usable_inodes_reason\": "
                + "\"live numeric inode count unavailable; probe status authoritative\",\n"
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
                + "  \"completion_usable_inodes\": null,\n"
                + "  \"completion_usable_inodes_reason\": "
                + "\"live numeric inode count unavailable; probe status authoritative\",\n"
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
                + "  \"compaction_partially_modified_relative_paths\": "
                + (compaction == null ? "[]"
                : jsonArray(compaction.partiallyModifiedRelativePaths)) + ",\n"
                + "  \"compaction_retained_relative_paths\": "
                + (compaction != null && (compaction.status == CompactionStatus.RETAINED_BY_REQUEST
                || compaction.status == CompactionStatus.RETAINED_PLATFORM_UNSUPPORTED)
                ? jsonArray(COMPACTABLE_RELATIVE_PATHS) : "[]") + ",\n"
                + "  \"compaction_reclaimed_bytes\": "
                + jsonNullableLong(compaction == null ? null : compaction.reclaimedBytes) + ",\n"
                + "  \"compaction_error\": "
                + jsonNullable(compaction == null ? null : compaction.error) + ",\n"
                + "  \"retain_ephemeral\": " + context.retainEphemeral + ",\n"
                + "  \"gzip_directory_sync_status\": "
                + jsonNullable(context.logCompression == null
                ? null : nullableName(context.logCompression.gzipDirectorySyncStatus)) + ",\n"
                + "  \"manifest_directory_sync_status\": "
                + jsonNullable(context.logCompression == null
                ? null : nullableName(context.logCompression.manifestDirectorySyncStatus)) + ",\n"
                + "  \"source_delete_directory_sync_status\": "
                + jsonNullable(context.logCompression == null
                ? null : nullableName(context.logCompression.sourceDeleteDirectorySyncStatus)) + ",\n"
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

    private static String nullableName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static void writeManifest(Path path, String json) throws IOException {
        DirectorySyncResult result = writeManifest(path, json, null);
        if (result.status == DirectorySyncStatus.FAILED) {
            throw new IOException(result.error);
        }
    }

    private static DirectorySyncResult writeTerminalManifest(Path path, String json)
            throws IOException {
        DirectorySyncResult result = writeManifest(
                path, json, "OPENGGF_TEST_MANIFEST_DIRECTORY_SYNC");
        String failCall = System.getenv("OPENGGF_TEST_MANIFEST_SYNC_FAIL_CALL");
        int call = TERMINAL_MANIFEST_SYNC_CALLS.incrementAndGet();
        if (Integer.toString(call).equals(failCall)) {
            return new DirectorySyncResult(DirectorySyncStatus.FAILED,
                    "injected one-shot terminal manifest sync failure at call " + call);
        }
        return result;
    }

    private static DirectorySyncResult writeManifest(Path path, String json, String injectionKey)
            throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        moveAtomic(tmp, path);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        return syncDirectory(path.getParent(), injectionKey);
    }

    private static SessionDirectoryIdentity captureSessionDirectoryIdentity(Path session)
            throws IOException {
        Path expected = session.toAbsolutePath().normalize();
        BasicFileAttributes attributes = Files.readAttributes(expected, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || Files.isSymbolicLink(expected)) {
            throw new IOException("session root is not a plain directory: " + expected);
        }
        Path realPath = expected.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realPath.equals(expected)) {
            throw new IOException("session root is not canonical: " + expected);
        }
        return new SessionDirectoryIdentity(realPath, attributes.fileKey(),
                Files.getFileStore(expected));
    }

    private static CompactionResult compactTerminalSession(
            Paths paths, String state, boolean retainEphemeral, List<String> reports,
            List<String> artifacts, SessionDirectoryIdentity identity) {
        return compactTerminalSession(paths, state, retainEphemeral, reports, artifacts,
                identity, (expected, candidate) -> {
                    try {
                        return expected.equals(Files.getFileStore(candidate));
                    } catch (IOException | RuntimeException e) {
                        return false;
                    }
                }, ignored -> { }, defaultNativeCompactionControl(paths, identity));
    }

    private static CompactionResult compactTerminalSession(
            Paths paths, String state, boolean retainEphemeral, List<String> reports,
            List<String> artifacts, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore) {
        return compactTerminalSession(paths, state, retainEphemeral, reports, artifacts,
                identity, sameStore, ignored -> { }, defaultNativeCompactionControl(paths, identity));
    }

    private static CompactionResult compactTerminalSession(
            Paths paths, String state, boolean retainEphemeral, List<String> reports,
            List<String> artifacts, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore, Consumer<String> deletionHook) {
        return compactTerminalSession(paths, state, retainEphemeral, reports, artifacts,
                identity, sameStore, deletionHook, defaultNativeCompactionControl(paths, identity));
    }

    private static CompactionResult compactTerminalSession(
            Paths paths, String state, boolean retainEphemeral, List<String> reports,
            List<String> artifacts, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore, Consumer<String> deletionHook,
            NativeCompactionControl nativeControl) {
        List<String> removed = new ArrayList<>();
        List<String> partiallyModified = new ArrayList<>();
        DeletionProgress progress = new DeletionProgress();
        try {
            if (!TERMINAL_SESSION_STATES.contains(state)) {
                throw new CompactionRefusal("session state is not terminal: " + state);
            }
            if (retainEphemeral) {
                return new CompactionResult(CompactionStatus.RETAINED_BY_REQUEST,
                        List.of(), List.of(), 0L, null);
            }

            List<Path> protectedPaths = Stream.concat(reports.stream(), artifacts.stream())
                    .map(value -> inventoryPath(paths.session, value))
                    .toList();
            validateProtectedPaths(paths.session, protectedPaths);
            boolean secureStrategyUsed = false;
            try (DirectoryStream<Path> opened = openSessionDirectory(paths.session)) {
                if (!nativeControl.forceNoSecureStream
                        && opened instanceof SecureDirectoryStream<?> rawSecure) {
                    if (identity.fileKey == null) {
                        throw new CompactionRefusal(
                                "secure session identity has no stable file key");
                    }
                    secureStrategyUsed = true;
                    requireSessionDirectoryIdentity(paths.session, identity, sameStore);
                    @SuppressWarnings("unchecked")
                    SecureDirectoryStream<Path> sessionStream =
                            (SecureDirectoryStream<Path>) rawSecure;
                    revalidateSessionDescriptor(sessionStream, identity);

                    List<CandidateBinding> candidates = new ArrayList<>();
                    for (String relative : COMPACTABLE_RELATIVE_PATHS) {
                        CandidateBinding candidate = bindCandidate(sessionStream, paths.session,
                                relative, identity, sameStore);
                        if (candidate != null) {
                            candidates.add(candidate);
                        }
                    }

                    for (CandidateBinding candidate : candidates) {
                        beginCandidate(progress, candidate.relativePath);
                        requireSessionDirectoryIdentity(paths.session, identity, sameStore);
                        revalidateSessionDescriptor(sessionStream, identity);
                        deleteCandidate(sessionStream, candidate, deletionHook, progress);
                        removed.add(candidate.relativePath);
                        finishCandidate(progress);
                    }
                }
            }
            if (!secureStrategyUsed) {
                if (!nativeControl.stableFileKeys || identity.fileKey == null) {
                    return new CompactionResult(
                            CompactionStatus.RETAINED_PLATFORM_UNSUPPORTED,
                            List.of(), List.of(), 0L,
                            boundedSingleLine(nativeControl.providerReason));
                }
                requireSessionDirectoryIdentity(paths.session, identity, sameStore);
                compactNativeCandidates(paths.session, identity, sameStore, deletionHook,
                        nativeControl, removed, progress);
            }
            return new CompactionResult(removed.isEmpty()
                    ? CompactionStatus.NOTHING_TO_REMOVE : CompactionStatus.COMPACTED,
                    List.copyOf(removed), List.of(), progress.reclaimedBytes, null);
        } catch (NativePlatformUnsupported e) {
            if (progress.candidateModified || !removed.isEmpty()) {
                recordInterruptedCandidate(removed, partiallyModified,
                        progress.currentCandidate, progress);
                return new CompactionResult(CompactionStatus.REFUSED, List.copyOf(removed),
                        List.copyOf(partiallyModified), progress.reclaimedBytes,
                        boundedSingleLine(e.getMessage()));
            }
            return new CompactionResult(CompactionStatus.RETAINED_PLATFORM_UNSUPPORTED,
                    List.of(), List.of(), 0L, boundedSingleLine(e.getMessage()));
        } catch (CompactionRefusal e) {
            recordInterruptedCandidate(removed, partiallyModified,
                    progress.currentCandidate, progress);
            return new CompactionResult(CompactionStatus.REFUSED, List.copyOf(removed),
                    List.copyOf(partiallyModified), progress.reclaimedBytes,
                    boundedSingleLine(e.getMessage()));
        } catch (IOException | RuntimeException e) {
            recordInterruptedCandidate(removed, partiallyModified,
                    progress.currentCandidate, progress);
            Throwable reported = e instanceof UncheckedIOException unchecked
                    ? unchecked.getCause() : e;
            return new CompactionResult(CompactionStatus.FAILED, List.copyOf(removed),
                    List.copyOf(partiallyModified), progress.reclaimedBytes,
                    boundedSingleLine(message(reported)));
        }
    }

    private static Path inventoryPath(Path session, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : session.resolve(path)).toAbsolutePath().normalize();
    }

    private static NativeCompactionControl defaultNativeCompactionControl(
            Paths paths, SessionDirectoryIdentity identity) {
        String provider = paths.session.getFileSystem().provider().getClass().getName();
        String store = identity.fileStore.name() + " (" + identity.fileStore.type() + ")";
        String reason = "provider=" + provider + " file_store=" + store
                + " secure_directory_stream=unavailable stable_file_key="
                + (identity.fileKey == null ? "unavailable" : "available");
        return new NativeCompactionControl(false, identity.fileKey != null, reason,
                ignored -> { }, Object::equals,
                path -> isNativeReparsePoint(path, null));
    }

    private static void beginCandidate(DeletionProgress progress, String candidate) {
        progress.currentCandidate = candidate;
        progress.candidateModified = false;
        progress.candidateFullyRemoved = false;
    }

    private static void finishCandidate(DeletionProgress progress) {
        progress.currentCandidate = null;
        progress.candidateModified = false;
        progress.candidateFullyRemoved = false;
    }

    private static void compactNativeCandidates(
            Path session, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore, Consumer<String> deletionHook,
            NativeCompactionControl control, List<String> removed,
            DeletionProgress progress)
            throws IOException, CompactionRefusal, NativePlatformUnsupported {
        List<CandidateBinding> candidates = new ArrayList<>();
        for (String relative : COMPACTABLE_RELATIVE_PATHS) {
            CandidateBinding candidate = bindNativeCandidate(
                    session, relative, identity, sameStore, control);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        Path staging = session.resolve(".compaction-staging-" + createProbeSuffix());
        Files.createDirectory(staging);
        BoundEntry stagingIdentity = inspectNativeEntry(staging, staging.getFileName(),
                true, identity, sameStore, control, false);
        for (CandidateBinding candidate : candidates) {
            beginCandidate(progress, candidate.relativePath);
            control.beforeCandidateMove.accept(candidate.relativePath);
            verifyNativeSessionAndStaging(
                    session, identity, staging, stagingIdentity, sameStore, control);
            Path source = session.resolve(candidate.relativePath);
            Path tombstone = staging.resolve("candidate-" + createProbeSuffix());
            Files.move(source, tombstone, StandardCopyOption.ATOMIC_MOVE);
            progress.candidateModified = true;
            BoundEntry candidateRoot = candidate.entries.get(Path.of(candidate.relativePath));
            verifyMovedTombstone(tombstone, candidateRoot, identity, sameStore, control);
            deleteNativeCandidate(session, identity, staging, stagingIdentity,
                    tombstone, candidate, sameStore, deletionHook, control, progress);
            removed.add(candidate.relativePath);
            finishCandidate(progress);
        }
        verifyNativeSessionAndStaging(
                session, identity, staging, stagingIdentity, sameStore, control);
        Files.delete(staging);
    }

    private static CandidateBinding bindNativeCandidate(
            Path session, String relative, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore, NativeCompactionControl control)
            throws CompactionRefusal, NativePlatformUnsupported {
        Map<Path, BoundEntry> entries = new LinkedHashMap<>();
        Path candidate = Path.of(relative);
        Path traversed = Path.of("");
        try {
            for (Path component : candidate) {
                traversed = traversed.getNameCount() == 0
                        ? component : traversed.resolve(component);
                BoundEntry bound;
                try {
                    bound = inspectNativeEntry(session.resolve(traversed), traversed,
                            true, identity, sameStore, control, true);
                } catch (NoSuchFileException e) {
                    throw new CandidateAbsent();
                }
                entries.put(traversed, bound);
            }
            bindNativeDescendants(session.resolve(candidate), session, candidate,
                    entries, identity, sameStore, control);
            return new CandidateBinding(relative, Map.copyOf(entries));
        } catch (CandidateAbsent e) {
            return null;
        } catch (NotDirectoryException e) {
            throw new CompactionRefusal(
                    "compactable path ancestor is not a directory: " + relative);
        } catch (NativePlatformUnsupported e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new CompactionRefusal(
                    "native compactable path cannot be inspected safely: "
                            + relative + ": " + message(e));
        }
    }

    private static void bindNativeDescendants(
            Path directory, Path session, Path parentRelative,
            Map<Path, BoundEntry> entries, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore, NativeCompactionControl control)
            throws IOException, CompactionRefusal, NativePlatformUnsupported {
        BoundEntry expectedDirectory = entries.get(parentRelative);
        verifyNativeBoundEntry(directory, expectedDirectory, identity, sameStore, control);
        List<Path> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                names.add(entry.getFileName());
            }
        }
        verifyNativeBoundEntry(directory, expectedDirectory, identity, sameStore, control);
        names.sort(Comparator.comparing(Path::toString));
        for (Path name : names) {
            Path relative = parentRelative.resolve(name);
            Path absolute = session.resolve(relative);
            BoundEntry bound = inspectNativeEntry(absolute, relative, false,
                    identity, sameStore, control, true);
            entries.put(relative, bound);
            if (bound.directory) {
                bindNativeDescendants(absolute, session, relative, entries,
                        identity, sameStore, control);
            }
        }
    }

    private static BoundEntry inspectNativeEntry(
            Path path, Path relative, boolean requireDirectory,
            SessionDirectoryIdentity identity, BiPredicate<FileStore, Path> sameStore,
            NativeCompactionControl control, boolean unsupportedOnNullKey)
            throws IOException, CompactionRefusal, NativePlatformUnsupported {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (isNativeReparsePoint(path, attributes) || control.reparsePoint.test(path)
                || requireDirectory && !attributes.isDirectory()) {
            throw new CompactionRefusal("unsafe native compactable entry: " + relative);
        }
        if (attributes.fileKey() == null) {
            if (unsupportedOnNullKey) {
                throw new NativePlatformUnsupported(control.providerReason
                        + " entry=" + relative + " stable_file_key=unavailable");
            }
            throw new CompactionRefusal(
                    "native compactable entry has no stable identity: " + relative);
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(identity.realPath) || normalized.equals(identity.realPath)
                || !normalized.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(normalized)
                || !sameStore.test(identity.fileStore, normalized)) {
            throw new CompactionRefusal("unsafe native compactable descendant: " + relative);
        }
        return new BoundEntry(relative, attributes.fileKey(), attributes.isDirectory(),
                attributes.isRegularFile() ? attributes.size() : 0L);
    }

    private static boolean isNativeReparsePoint(Path path, BasicFileAttributes knownAttributes) {
        try {
            BasicFileAttributes attributes = knownAttributes == null
                    ? Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS) : knownAttributes;
            return attributes.isSymbolicLink() || attributes.isOther();
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    private static void verifyNativeSessionAndStaging(
            Path session, SessionDirectoryIdentity identity,
            Path staging, BoundEntry stagingIdentity,
            BiPredicate<FileStore, Path> sameStore, NativeCompactionControl control)
            throws CompactionRefusal {
        requireSessionDirectoryIdentity(session, identity, sameStore);
        try {
            verifyNativeBoundEntry(staging, stagingIdentity, identity, sameStore, control);
        } catch (IOException | NativePlatformUnsupported e) {
            throw new CompactionRefusal(
                    "native staging identity cannot be inspected: " + message(e));
        }
    }

    private static void verifyMovedTombstone(
            Path tombstone, BoundEntry expectedRoot, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore, NativeCompactionControl control)
            throws IOException, CompactionRefusal, NativePlatformUnsupported {
        BoundEntry moved = inspectNativeEntry(tombstone, tombstone.getFileName(),
                true, identity, sameStore, control, false);
        if (!control.movedIdentityMatches.test(expectedRoot.fileKey, moved.fileKey)
                || !expectedRoot.fileKey.equals(moved.fileKey)) {
            throw new CompactionRefusal("atomically moved candidate identity does not match binding");
        }
    }

    private static void deleteNativeCandidate(
            Path session, SessionDirectoryIdentity identity,
            Path staging, BoundEntry stagingIdentity, Path tombstone,
            CandidateBinding candidate, BiPredicate<FileStore, Path> sameStore,
            Consumer<String> deletionHook, NativeCompactionControl control,
            DeletionProgress progress)
            throws IOException, CompactionRefusal, NativePlatformUnsupported {
        Path candidateRoot = Path.of(candidate.relativePath);
        List<BoundEntry> deletionOrder = candidate.entries.values().stream()
                .filter(entry -> entry.relativePath.equals(candidateRoot)
                        || entry.relativePath.startsWith(candidateRoot))
                .sorted(Comparator.comparingInt(
                        (BoundEntry entry) -> entry.relativePath.getNameCount()).reversed()
                        .thenComparing(entry -> entry.relativePath.toString()))
                .toList();
        for (BoundEntry target : deletionOrder) {
            verifyNativeSessionAndStaging(
                    session, identity, staging, stagingIdentity, sameStore, control);
            Path suffix = candidateRoot.relativize(target.relativePath);
            Path targetPath = suffix.toString().isEmpty()
                    ? tombstone : tombstone.resolve(suffix);
            verifyNativeTarget(tombstone, candidateRoot, targetPath, target,
                    candidate, identity, sameStore, control);
            deletionHook.accept(target.relativePath.toString().replace('\\', '/'));
            verifyNativeSessionAndStaging(
                    session, identity, staging, stagingIdentity, sameStore, control);
            verifyNativeTarget(tombstone, candidateRoot, targetPath, target,
                    candidate, identity, sameStore, control);
            Files.delete(targetPath);
            progress.candidateModified = true;
            if (!target.directory) {
                progress.reclaimedBytes = Math.addExact(progress.reclaimedBytes, target.size);
            }
            if (target.relativePath.equals(candidateRoot)) {
                progress.candidateFullyRemoved = true;
            }
        }
    }

    private static void verifyNativeTarget(
            Path tombstone, Path candidateRoot, Path targetPath, BoundEntry target,
            CandidateBinding candidate, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore, NativeCompactionControl control)
            throws IOException, CompactionRefusal, NativePlatformUnsupported {
        Path parent = target.relativePath.getParent();
        if (!target.relativePath.equals(candidateRoot) && parent != null) {
            Path traversed = candidateRoot;
            BoundEntry rootBound = candidate.entries.get(candidateRoot);
            verifyNativeBoundEntry(tombstone, rootBound, identity, sameStore, control);
            Path suffixParent = candidateRoot.relativize(parent);
            for (Path component : suffixParent) {
                traversed = traversed.resolve(component);
                Path absolute = tombstone.resolve(candidateRoot.relativize(traversed));
                verifyNativeBoundEntry(absolute, candidate.entries.get(traversed),
                        identity, sameStore, control);
            }
        }
        verifyNativeBoundEntry(targetPath, target, identity, sameStore, control);
    }

    private static void verifyNativeBoundEntry(
            Path path, BoundEntry expected, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore, NativeCompactionControl control)
            throws IOException, CompactionRefusal, NativePlatformUnsupported {
        if (expected == null) {
            throw new CompactionRefusal("native compactable ancestor is unbound");
        }
        BasicFileAttributes actual = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (isNativeReparsePoint(path, actual) || control.reparsePoint.test(path)
                || actual.fileKey() == null
                || actual.isDirectory() != expected.directory
                || !expected.fileKey.equals(actual.fileKey())
                || !sameStore.test(identity.fileStore, path)) {
            throw new CompactionRefusal(
                    "native compactable entry identity changed: " + expected.relativePath);
        }
    }

    private static void validateProtectedPaths(Path session, List<Path> protectedPaths)
            throws CompactionRefusal {
        Path root = session.toAbsolutePath().normalize();
        for (Path protectedPath : protectedPaths) {
            for (String relative : COMPACTABLE_RELATIVE_PATHS) {
                Path candidate = root.resolve(relative).normalize();
                if (protectedPath.equals(candidate) || protectedPath.startsWith(candidate)) {
                    throw new CompactionRefusal(
                            "compactable path contains inventoried evidence: " + candidate);
                }
            }
        }
    }

    private static void revalidateSessionDirectory(
            Path session, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore) throws IOException, CompactionRefusal {
        Path expected = session.toAbsolutePath().normalize();
        BasicFileAttributes attributes = Files.readAttributes(expected, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || Files.isSymbolicLink(expected)
                || !expected.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(identity.realPath)
                || !identity.fileKey.equals(attributes.fileKey())
                || !sameStore.test(identity.fileStore, expected)) {
            throw new CompactionRefusal("session directory identity changed: " + expected);
        }
    }

    private static void requireSessionDirectoryIdentity(
            Path session, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore) throws CompactionRefusal {
        try {
            revalidateSessionDirectory(session, identity, sameStore);
        } catch (IOException | RuntimeException e) {
            throw new CompactionRefusal(
                    "session directory identity cannot be inspected: " + message(e));
        }
    }

    private static DirectoryStream<Path> openSessionDirectory(Path session)
            throws CompactionRefusal {
        try {
            return Files.newDirectoryStream(session);
        } catch (IOException | RuntimeException e) {
            throw new CompactionRefusal(
                    "session directory cannot be opened safely: " + message(e));
        }
    }

    private static void revalidateSessionDescriptor(
            SecureDirectoryStream<Path> sessionStream, SessionDirectoryIdentity identity)
            throws CompactionRefusal {
        try {
            BasicFileAttributeView view = sessionStream.getFileAttributeView(
                    BasicFileAttributeView.class);
            if (view == null) {
                throw new CompactionRefusal("session descriptor attributes are unavailable");
            }
            BasicFileAttributes attributes = view.readAttributes();
            if (!attributes.isDirectory() || !identity.fileKey.equals(attributes.fileKey())) {
                throw new CompactionRefusal("open session descriptor identity changed");
            }
        } catch (IOException | RuntimeException e) {
            throw new CompactionRefusal(
                    "session descriptor identity cannot be inspected: " + message(e));
        }
    }

    private static CandidateBinding bindCandidate(
            SecureDirectoryStream<Path> sessionStream, Path session, String relative,
            SessionDirectoryIdentity identity, BiPredicate<FileStore, Path> sameStore)
            throws CompactionRefusal {
        Map<Path, BoundEntry> entries = new LinkedHashMap<>();
        Path candidate = Path.of(relative);
        try {
            bindCandidatePath(sessionStream, session, candidate, 0, Path.of(""), entries,
                    identity, sameStore);
            return new CandidateBinding(relative, Map.copyOf(entries));
        } catch (CandidateAbsent e) {
            return null;
        } catch (NotDirectoryException e) {
            throw new CompactionRefusal(
                    "compactable path ancestor is not a directory: " + relative);
        } catch (IOException | RuntimeException e) {
            throw new CompactionRefusal(
                    "compactable path cannot be inspected safely: " + relative + ": " + message(e));
        }
    }

    private static void bindCandidatePath(
            SecureDirectoryStream<Path> parent, Path session, Path candidate, int index,
            Path parentRelative, Map<Path, BoundEntry> entries,
            SessionDirectoryIdentity identity, BiPredicate<FileStore, Path> sameStore)
            throws IOException, CompactionRefusal, CandidateAbsent {
        Path name = candidate.getName(index);
        Path relative = parentRelative.getNameCount() == 0
                ? name : parentRelative.resolve(name);
        BoundEntry bound;
        try {
            bound = inspectEntry(parent, name, session.resolve(relative), relative,
                    true, identity, sameStore);
        } catch (NoSuchFileException e) {
            throw new CandidateAbsent();
        }
        entries.put(relative, bound);
        try (SecureDirectoryStream<Path> child = parent.newDirectoryStream(
                name, LinkOption.NOFOLLOW_LINKS)) {
            verifyDirectoryDescriptor(child, bound);
            if (index + 1 < candidate.getNameCount()) {
                bindCandidatePath(child, session, candidate, index + 1, relative,
                        entries, identity, sameStore);
            } else {
                bindDescendants(child, session, relative, entries, identity, sameStore);
            }
        }
    }

    private static void bindDescendants(
            SecureDirectoryStream<Path> directory, Path session, Path parentRelative,
            Map<Path, BoundEntry> entries, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore) throws IOException, CompactionRefusal {
        List<Path> names = new ArrayList<>();
        for (Path entry : directory) {
            names.add(entry.getFileName());
        }
        names.sort(Comparator.comparing(Path::toString));
        for (Path name : names) {
            Path relative = parentRelative.resolve(name);
            BoundEntry bound = inspectEntry(directory, name, session.resolve(relative),
                    relative, false, identity, sameStore);
            entries.put(relative, bound);
            if (bound.directory) {
                try (SecureDirectoryStream<Path> child = directory.newDirectoryStream(
                        name, LinkOption.NOFOLLOW_LINKS)) {
                    verifyDirectoryDescriptor(child, bound);
                    bindDescendants(child, session, relative, entries, identity, sameStore);
                }
            }
        }
    }

    private static BoundEntry inspectEntry(
            SecureDirectoryStream<Path> parent, Path name, Path absolute, Path relative,
            boolean requireDirectory, SessionDirectoryIdentity identity,
            BiPredicate<FileStore, Path> sameStore) throws IOException, CompactionRefusal {
        BasicFileAttributes attributes = secureAttributes(parent, name);
        if (attributes.isSymbolicLink() || requireDirectory && !attributes.isDirectory()
                || attributes.fileKey() == null) {
            throw new CompactionRefusal("unsafe compactable entry: " + relative);
        }
        Path normalized = absolute.toAbsolutePath().normalize();
        if (!normalized.startsWith(identity.realPath) || normalized.equals(identity.realPath)
                || !normalized.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(normalized)
                || !sameStore.test(identity.fileStore, normalized)) {
            throw new CompactionRefusal("unsafe compactable descendant: " + relative);
        }
        return new BoundEntry(relative, attributes.fileKey(), attributes.isDirectory(),
                attributes.isRegularFile() ? attributes.size() : 0L);
    }

    private static BasicFileAttributes secureAttributes(
            SecureDirectoryStream<Path> parent, Path name) throws IOException {
        BasicFileAttributeView view = parent.getFileAttributeView(
                name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IOException("descriptor-relative attributes are unavailable for " + name);
        }
        return view.readAttributes();
    }

    private static void verifyDirectoryDescriptor(
            SecureDirectoryStream<Path> directory, BoundEntry bound)
            throws CompactionRefusal {
        try {
            BasicFileAttributeView view = directory.getFileAttributeView(BasicFileAttributeView.class);
            if (view == null) {
                throw new CompactionRefusal(
                        "directory descriptor attributes are unavailable: " + bound.relativePath);
            }
            BasicFileAttributes attributes = view.readAttributes();
            if (!attributes.isDirectory() || !bound.fileKey.equals(attributes.fileKey())) {
                throw new CompactionRefusal(
                        "directory identity changed while opening: " + bound.relativePath);
            }
        } catch (IOException | RuntimeException e) {
            throw new CompactionRefusal(
                    "directory descriptor cannot be inspected: " + bound.relativePath
                            + ": " + message(e));
        }
    }

    private static void deleteCandidate(
            SecureDirectoryStream<Path> sessionStream, CandidateBinding candidate,
            Consumer<String> deletionHook, DeletionProgress progress)
            throws IOException, CompactionRefusal {
        Path root = Path.of(candidate.relativePath);
        List<BoundEntry> deletionOrder = candidate.entries.values().stream()
                .filter(entry -> entry.relativePath.equals(root)
                        || entry.relativePath.startsWith(root))
                .sorted(Comparator.comparingInt(
                        (BoundEntry entry) -> entry.relativePath.getNameCount()).reversed()
                        .thenComparing(entry -> entry.relativePath.toString()))
                .toList();
        for (BoundEntry entry : deletionOrder) {
            deleteBoundEntry(sessionStream, candidate, entry, deletionHook, progress);
        }
    }

    private static void deleteBoundEntry(
            SecureDirectoryStream<Path> sessionStream, CandidateBinding candidate,
            BoundEntry target, Consumer<String> deletionHook, DeletionProgress progress)
            throws IOException, CompactionRefusal {
        List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
        SecureDirectoryStream<Path> parent = sessionStream;
        try {
            Path parentRelative = target.relativePath.getParent();
            Path traversed = Path.of("");
            if (parentRelative != null) {
                for (Path component : parentRelative) {
                    traversed = traversed.getNameCount() == 0
                            ? component : traversed.resolve(component);
                    BoundEntry boundParent = candidate.entries.get(traversed);
                    if (boundParent == null || !boundParent.directory) {
                        throw new CompactionRefusal(
                                "unbound compactable ancestor: " + traversed);
                    }
                    verifyBoundEntry(parent, component, boundParent);
                    SecureDirectoryStream<Path> child = openBoundDirectory(
                            parent, component, boundParent);
                    opened.add(child);
                    parent = child;
                }
            }
            Path name = target.relativePath.getFileName();
            verifyBoundEntry(parent, name, target);
            deletionHook.accept(target.relativePath.toString().replace('\\', '/'));
            verifyBoundEntry(parent, name, target);
            if (target.directory) {
                parent.deleteDirectory(name);
                progress.candidateModified = true;
            } else {
                parent.deleteFile(name);
                progress.candidateModified = true;
                progress.reclaimedBytes = Math.addExact(progress.reclaimedBytes, target.size);
            }
            if (target.relativePath.equals(Path.of(candidate.relativePath))) {
                progress.candidateFullyRemoved = true;
            }
        } finally {
            for (int index = opened.size() - 1; index >= 0; index--) {
                opened.get(index).close();
            }
        }
    }

    private static void verifyBoundEntry(
            SecureDirectoryStream<Path> parent, Path name, BoundEntry expected)
            throws CompactionRefusal {
        try {
            BasicFileAttributes actual = secureAttributes(parent, name);
            if (actual.isSymbolicLink() || actual.isDirectory() != expected.directory
                    || !expected.fileKey.equals(actual.fileKey())) {
                throw new CompactionRefusal(
                        "compactable entry identity changed: " + expected.relativePath);
            }
        } catch (IOException | RuntimeException e) {
            throw new CompactionRefusal(
                    "compactable entry cannot be inspected: " + expected.relativePath
                            + ": " + message(e));
        }
    }

    private static SecureDirectoryStream<Path> openBoundDirectory(
            SecureDirectoryStream<Path> parent, Path name, BoundEntry expected)
            throws CompactionRefusal {
        try {
            SecureDirectoryStream<Path> child = parent.newDirectoryStream(
                    name, LinkOption.NOFOLLOW_LINKS);
            try {
                verifyDirectoryDescriptor(child, expected);
                return child;
            } catch (CompactionRefusal e) {
                child.close();
                throw e;
            }
        } catch (CompactionRefusal e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new CompactionRefusal(
                    "compactable ancestor cannot be opened: " + expected.relativePath
                            + ": " + message(e));
        }
    }

    private static void recordInterruptedCandidate(
            List<String> removed, List<String> partial, String currentCandidate,
            DeletionProgress progress) {
        if (currentCandidate == null) {
            return;
        }
        if (progress.candidateFullyRemoved && !removed.contains(currentCandidate)) {
            removed.add(currentCandidate);
        } else if (progress.candidateModified && !partial.contains(currentCandidate)) {
            partial.add(currentCandidate);
        }
    }

    private static String compactionFailure(CompactionResult compaction) {
        if (compaction.status != CompactionStatus.FAILED
                && compaction.status != CompactionStatus.REFUSED) {
            return null;
        }
        return "terminal compaction " + compaction.status.name().toLowerCase(
                java.util.Locale.ROOT) + ": " + compaction.error;
    }

    private static LogCompressionResult compressTerminalLog(Paths paths) {
        Path compressed = paths.session.resolve("maven.log.gz");
        Path temporary = paths.session.resolve(".maven.log.gz-" + createProbeSuffix() + ".tmp");
        try {
            if ("1".equals(System.getenv("OPENGGF_TEST_LOG_COMPRESSION_FAIL"))) {
                throw new IOException("injected log compression failure");
            }
            try (var input = Files.newInputStream(paths.mavenLog, StandardOpenOption.READ);
                 OutputStream file = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE);
                 var gzip = new GZIPOutputStream(file, 64 * 1024)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        gzip.write(buffer, 0, count);
                    }
                }
                gzip.finish();
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, compressed, StandardCopyOption.ATOMIC_MOVE);
            DirectorySyncResult directorySync = syncDirectory(
                    paths.session, "OPENGGF_TEST_LOG_DIRECTORY_SYNC");
            String error = directorySync.status == DirectorySyncStatus.FAILED
                    ? "terminal gzip directory sync failed: " + directorySync.error : null;
            return new LogCompressionResult(compressed, error, true, directorySync.status,
                    null, null);
        } catch (IOException | RuntimeException e) {
            String error = "terminal log compression failed: " + boundedSingleLine(message(e));
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                error += "; temporary cleanup failed: " + boundedSingleLine(message(cleanup));
            }
            boolean published = Files.isRegularFile(compressed, LinkOption.NOFOLLOW_LINKS);
            return new LogCompressionResult(published ? compressed : paths.mavenLog, error,
                    published, DirectorySyncStatus.FAILED, null, null);
        }
    }

    private static LogCompressionResult removeCompressedLogSource(
            Paths paths, LogCompressionResult compression) {
        if (!compression.published || compression.error != null) {
            return compression;
        }
        try {
            if ("1".equals(System.getenv("OPENGGF_TEST_LOG_SOURCE_DELETE_FAIL"))) {
                throw new IOException("injected log source removal failure");
            }
            Files.delete(paths.mavenLog);
            DirectorySyncResult directorySync = syncDirectory(
                    paths.session, "OPENGGF_TEST_LOG_DIRECTORY_SYNC");
            LogCompressionResult result = compression.withSourceDeleteSync(directorySync.status);
            return directorySync.status == DirectorySyncStatus.FAILED
                    ? result.withError("terminal log source deletion directory sync failed: "
                    + directorySync.error) : result;
        } catch (IOException | RuntimeException e) {
            return compression.withError(
                    "terminal log source removal failed: " + boundedSingleLine(message(e)));
        }
    }

    private static DirectorySyncResult syncDirectory(Path directory, String injectionKey) {
        String injected = injectionKey == null ? null : System.getenv(injectionKey);
        if ("unsupported".equals(injected)) {
            return new DirectorySyncResult(DirectorySyncStatus.UNSUPPORTED,
                    "injected unsupported directory sync");
        }
        if ("failure".equals(injected)) {
            return new DirectorySyncResult(DirectorySyncStatus.FAILED,
                    "injected directory sync failure");
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
            return new DirectorySyncResult(DirectorySyncStatus.SYNCED, null);
        } catch (UnsupportedOperationException e) {
            return new DirectorySyncResult(DirectorySyncStatus.UNSUPPORTED,
                    boundedSingleLine(message(e)));
        } catch (IOException e) {
            if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                    .contains("windows")) {
                return new DirectorySyncResult(DirectorySyncStatus.UNSUPPORTED,
                        boundedSingleLine(message(e)));
            }
            return new DirectorySyncResult(DirectorySyncStatus.FAILED,
                    boundedSingleLine(message(e)));
        }
    }

    private static String combineStorageErrors(String first, String second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first + "; " + second;
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

        Path expectedLeaseRoot = canonicalConfiguredRoot.resolve("codex/test-session-locks");
        Path reportedLeaseRoot = canonicalReservationPath(requiredString(record, "lease_root"),
                "lease_root");
        validateExistingDirectory(reportedLeaseRoot, "managed test-session lease root");
        if (!reportedLeaseRoot.equals(expectedLeaseRoot)) {
            throw managedFailure("reservation lease_root is outside the managed lease lane");
        }

        long device = requiredLong(record, "filesystem_device");
        long actualDevice = filesystemDevice(allocation);
        if (device < 0 || device != actualDevice
                || actualDevice != filesystemDevice(canonicalConfiguredRoot)
                || actualDevice != filesystemDevice(canonicalLane)
                || actualDevice != filesystemDevice(reportedLeaseRoot)) {
            throw managedFailure("reservation filesystem device does not match the allocation");
        }
        long usableBytes = requiredLong(record, "usable_bytes");
        long totalBytes = requiredLong(record, "total_bytes");
        InodeSnapshot inodeSnapshot = requiredInodeSnapshot(record);
        if (usableBytes < 0 || totalBytes <= 0 || usableBytes > totalBytes) {
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
                reportedRoot, reportedLeaseRoot, schema, helperVersion, Long.toString(device),
                new CapacitySnapshot(usableBytes, totalBytes,
                        inodeSnapshot.usableInodes == null ? -1 : inodeSnapshot.usableInodes),
                inodeSnapshot, retentionDeadline, null, null);
    }

    private static StorageAllocation localAllocation(Path root, StorageTier tier, String warning)
            throws IOException {
        FileStore store = Files.getFileStore(root);
        return new StorageAllocation(root, tier, null, null, 0, null,
                store.name().isBlank() ? store.type() : store.name(),
                new CapacitySnapshot(store.getUsableSpace(), store.getTotalSpace(), -1), null, null,
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

    private static InodeSnapshot requiredInodeSnapshot(Map<String, JsonValue> values) {
        String statusText = requiredString(values, "inode_count_status");
        InodeCountStatus status;
        try {
            status = InodeCountStatus.valueOf(statusText);
        } catch (IllegalArgumentException e) {
            throw managedFailure("reservation field inode_count_status has unknown value: "
                    + statusText);
        }
        JsonValue value = values.get("usable_inodes");
        if (status == InodeCountStatus.MEASURED) {
            if (value == null || value.type != JsonType.INTEGER) {
                throw managedFailure(
                        "reservation usable_inodes must be an integer when inode_count_status is MEASURED");
            }
            long count = requiredLong(values, "usable_inodes");
            if (count < 0) {
                throw managedFailure("reservation usable_inodes must be nonnegative when measured");
            }
            return new InodeSnapshot(status, count, null);
        }
        if (value == null || value.type != JsonType.NULL) {
            throw managedFailure(
                    "reservation usable_inodes must be null when inode_count_status is UNAVAILABLE_DYNAMIC");
        }
        return new InodeSnapshot(status, null,
                "filesystem uses dynamic inode allocation; numeric count unavailable");
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
        INTEGER,
        NULL
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
            if (source.startsWith("null", index)) {
                index += 4;
                return new JsonValue(JsonType.NULL, null);
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

    private static Path resolveLockParent(Path worktree, Path explicit,
                                          StorageAllocation allocation) throws IOException {
        Path parent;
        boolean external = explicit != null;
        if (explicit != null) {
            parent = explicit.toAbsolutePath().normalize();
        } else {
            String env = System.getenv("OPENGGF_TEST_LOCK_ROOT");
            if (env != null && !env.isBlank()) {
                external = true;
                parent = Path.of(env).toAbsolutePath().normalize();
            } else if (allocation.managedLeaseRoot != null) {
                external = true;
                parent = allocation.managedLeaseRoot;
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

    private static boolean externalLockRequested(Options options, StorageAllocation allocation) {
        return options.lockRoot != null || (System.getenv("OPENGGF_TEST_LOCK_ROOT") != null
                && !System.getenv("OPENGGF_TEST_LOCK_ROOT").isBlank())
                || allocation.managedLeaseRoot != null;
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
        private enum FinalizationOwner {
            NONE,
            NORMAL,
            SHUTDOWN
        }

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
        private final SessionDirectoryIdentity sessionIdentity;
        private final boolean retainEphemeral;
        private final CountDownLatch outputDrainComplete = new CountDownLatch(1);
        private final CountDownLatch finalizationComplete = new CountDownLatch(1);
        private volatile Process child;
        private volatile boolean completed;
        private FinalizationOwner owner = FinalizationOwner.NONE;

        private ShutdownState(Paths paths, String runId, Path worktree, Path leasePath,
                              String sourceBefore, String runtimeBefore, List<String> command,
                              String commandHash, String capability, String allowedPhases,
                              Path exportFile, Lease lease, StorageAllocation allocation,
                              CapacitySnapshot launchCapacity, long capacityFloor,
                              CapacityProbe capacityProbe, LiveProbeResult launchProbe,
                              SessionDirectoryIdentity sessionIdentity,
                              boolean retainEphemeral) {
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
            this.sessionIdentity = sessionIdentity;
            this.retainEphemeral = retainEphemeral;
        }

        private synchronized boolean claimNormalFinalization() {
            if (owner != FinalizationOwner.NONE) {
                return false;
            }
            owner = FinalizationOwner.NORMAL;
            return true;
        }

        private void completeNormalFinalization() {
            synchronized (this) {
                completed = true;
            }
            finalizationComplete.countDown();
        }

        private void abort() {
            boolean waitForNormal;
            synchronized (this) {
                if (completed) {
                    return;
                }
                waitForNormal = owner == FinalizationOwner.NORMAL;
                if (owner == FinalizationOwner.NONE) {
                    owner = FinalizationOwner.SHUTDOWN;
                } else if (!waitForNormal) {
                    return;
                }
            }
            if (waitForNormal) {
                awaitUninterruptibly(finalizationComplete);
                return;
            }
            Process process = child;
            ProcessTreeResult treeResult = process == null
                    ? new ProcessTreeResult(true, List.of()) : terminateProcessTree(process);
            boolean treeStopped = treeResult.stopped();
            if (!awaitOutputDrain()) {
                synchronized (this) {
                    completed = true;
                }
                finalizationComplete.countDown();
                if (treeStopped) {
                    try {
                        lease.close();
                    } catch (IOException ignored) {
                    }
                }
                return;
            }
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
                List<String> reports = reportInventory(paths);
                List<String> artifacts = artifactInventory(paths);
                String storageFinalizationError = completionObservation.error();
                ManifestContext preCompactionContext = new ManifestContext(
                        allocation, launchCapacity, completionObservation.capacity, null,
                        retainEphemeral, storageFinalizationError, null, sessionIdentity,
                        null, launchProbe, completionObservation.capacityError,
                        completionObservation.liveProbe);
                writeManifest(paths.manifest, manifest(paths, runId, state, worktree, leasePath,
                        commandHash, capability, allowedPhases, sourceAfter, runtimeAfter,
                        reports, artifacts, preCompactionContext,
                        capacityFloor));
                CompactionResult compaction = compactTerminalSession(paths, state,
                        retainEphemeral, reports, artifacts, sessionIdentity);
                storageFinalizationError = combineStorageErrors(storageFinalizationError,
                        compactionFailure(compaction));
                LogCompressionResult logCompression = new LogCompressionResult(paths.mavenLog,
                        "terminal log compression deferred during shutdown", false,
                        null, null, null);
                storageFinalizationError = combineStorageErrors(storageFinalizationError,
                        logCompression.error);
                ManifestContext terminalContext = new ManifestContext(
                        allocation, launchCapacity, completionObservation.capacity, compaction,
                        retainEphemeral, storageFinalizationError, logCompression, sessionIdentity,
                        null, launchProbe, completionObservation.capacityError,
                        completionObservation.liveProbe);
                writeManifest(paths.manifest, manifest(paths, runId, state, worktree, leasePath,
                        commandHash, capability, allowedPhases, sourceAfter, runtimeAfter,
                        reports, artifacts, terminalContext, capacityFloor));
                if (exportFile != null) {
                    writeExport(exportFile, paths.manifest, runId);
                }
                printEndMarker(paths, runId, 143, state, false, terminalContext,
                        treeStopped);
                System.out.flush();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            } finally {
                synchronized (this) {
                    completed = true;
                }
                finalizationComplete.countDown();
                if (treeStopped) {
                    try {
                        lease.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        private static void awaitUninterruptibly(CountDownLatch latch) {
            boolean interrupted = false;
            for (;;) {
                try {
                    latch.await();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private boolean awaitOutputDrain() {
            boolean interrupted = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            try {
                while (System.nanoTime() < deadline) {
                    try {
                        if (outputDrainComplete.await(100, TimeUnit.MILLISECONDS)) {
                            return true;
                        }
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                return false;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
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
        boolean retainEphemeral;
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
                    case "--retain-ephemeral" -> options.retainEphemeral = true;
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

    private static final class CompactionRefusal extends Exception {
        private CompactionRefusal(String message) {
            super(message);
        }
    }

    private static final class CandidateAbsent extends Exception {
    }

    private static final class NativePlatformUnsupported extends Exception {
        private NativePlatformUnsupported(String message) {
            super(message);
        }
    }

}
