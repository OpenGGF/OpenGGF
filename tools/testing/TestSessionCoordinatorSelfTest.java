import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
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
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public final class TestSessionCoordinatorSelfTest {
    private static final Pattern RUN_ID = Pattern.compile("\\d{8}T\\d{6}Z-p\\d+-[0-9a-f]{6}");
    private static final List<String> MANIFEST_KEYS = List.of(
            "run_id", "state", "manifest", "worktree", "lease_path", "source_digest",
            "runtime_inputs_digest", "build_root", "tmp_root", "surefire_reports", "trace_reports",
            "diagnostics_root", "artifact_root", "distribution_root", "isolation",
            "lwjgl_extraction", "lwjgl_extract_template", "command_file", "log", "reports", "artifacts",
            "storage_tier", "allocation_path", "managed_root", "allocation_schema",
            "helper_version", "filesystem_device", "allocation_usable_bytes",
            "allocation_total_bytes", "allocation_inode_count_status",
            "allocation_usable_inodes", "allocation_usable_inodes_reason", "retention_deadline",
            "allocation_not_applicable_reason", "storage_warning", "allocation_verified",
            "session_real_path", "session_file_key", "session_file_store",
            "capacity_floor_bytes", "launch_usable_bytes", "launch_total_bytes",
            "launch_usable_inodes", "launch_usable_inodes_reason", "completion_usable_bytes",
            "completion_total_bytes", "completion_usable_inodes",
            "completion_usable_inodes_reason", "compaction_status", "compaction_removed_relative_paths",
            "compaction_partially_modified_relative_paths", "compaction_retained_relative_paths",
            "compaction_reclaimed_bytes",
            "compaction_error", "retain_ephemeral",
            "storage_finalization_error", "numeric_inode_unavailable_reason",
            "gzip_directory_sync_status", "manifest_directory_sync_status",
            "source_delete_directory_sync_status",
            "launch_capacity_error", "launch_inode_probe_status", "launch_inode_probe_error",
            "launch_directory_flush_status", "completion_capacity_error",
            "completion_inode_probe_status", "completion_inode_probe_error",
            "completion_directory_flush_status");

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

        verifyTerminalCompactionAllowlist(root);
        verifyTerminalCompactionRefusals(root);
        verifySecureCompactionRejectsPathSwap(root);
        verifyCompactionInspectionFailureIsNotAbsence(root);
        verifyPartialCompactionEvidenceIsTruthful(root);
        verifyStableKeyTombstoneCompactionWithoutSecureStreams(root);
        verifyStableKeyTombstoneSwapAndReparseSafety(root);
        verifyJdk21NativeWindowsUnsupportedContract(root);
        verifyRequiredFreeBytesFormula();
        verifyManagedReservationIsValidated(root);
        verifyManagedLeaseRootDefaultsOutsideReadOnlyGitCommon(root);
        verifyManagedDynamicInodesUseLiveProbe(root);
        verifyManagedHelperFailureDoesNotFallback(root);
        verifyManagedMalformedJsonDoesNotFallback(root);
        verifyUnmanagedProjectFallbackIsVisible(root);
        verifyExplicitRootRemainsFailClosed(root);
        verifyLowCapacityPreventsLaunch(root);
        verifyInvalidAndLowerCapacityOverridesFail(root);
        verifyZeroUsableInodesPreventLaunch(root);
        verifyLiveProbeFailurePreventsLaunchAcrossTiers(root);
        verifyCapacityProbeFailurePublishesStartupEvidence(root);
        verifyCompletionProbeFailuresPreserveTerminalState(root);
        verifyUnsupportedDirectoryFlushIsObservable(root);
        verifyMarkerFieldsCannotForgeLines(root, outputRoot);
        verifyCompactionAcrossStorageTiers(root, outputRoot);
        BasicRun first = verifySuccessfulRun(root, outputRoot);
        verifyExplicitQuietRun(root, outputRoot);
        verifyVerboseRun(root, outputRoot);
        verifyForeignOwnedRootIsRejected(root, outputRoot);
        verifySpaceContainingRoot(root);
        verifyInWorktreeSymlinkLockRootIsRejected(root, outputRoot);
        verifyChildExitPropagation(root, outputRoot);
        verifyLogCompressionFailureVerdictPrecedence(root, outputRoot);
        verifyPublishedLogSurvivesSourceRemovalFailure(root, outputRoot);
        verifyManifestBarrierFailureRetainsSource(root, outputRoot);
        verifyUnsupportedDirectorySyncRemainsCertifying(root, outputRoot);
        verifyRealDirectorySyncFailureIsNonCertifying(root, outputRoot);
        verifyEveryTerminalManifestBarrierFailureIsPropagated(root, outputRoot);
        verifyEveryStartupManifestBarrierFailureIsPropagated(root, outputRoot);
        verifyCompactionFailureVerdictPrecedence(root, outputRoot);
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

    private static void verifyTerminalCompactionAllowlist(Path root) throws Exception {
        Set<String> expectedRemoved = Set.of("tmp", "build/test-classes/traces");
        for (String state : List.of("PASSED", "FAILED", "INVALID_IDENTITY_CHANGED", "ABORTED",
                "STARTUP_FAILED", "STORAGE_FINALIZATION_FAILED")) {
            Path session = root.resolve("compact-" + state.toLowerCase());
            Object paths = createCompactionFixture(session);
            Object identity = captureSessionIdentity(session);
            Object result = compact(paths, state, false, List.of(), List.of(), identity);

            check(recordValue(result, "status").toString().equals("COMPACTED"),
                    state + " must be compacted");
            @SuppressWarnings("unchecked")
            List<String> removed = (List<String>) recordValue(result, "removedRelativePaths");
            check(Set.copyOf(removed).equals(expectedRemoved),
                    state + " removed unexpected paths: " + removed);
            check((long) recordValue(result, "reclaimedBytes") == 18L,
                    state + " must report exact reclaimed file bytes");
            assertOnlyCompactablePathsRemoved(session);
        }

        Path retainedSession = root.resolve("compact-retained");
        Object retainedPaths = createCompactionFixture(retainedSession);
        Object retainedIdentity = captureSessionIdentity(retainedSession);
        Object retained = compact(retainedPaths, "PASSED", true, List.of(), List.of(), retainedIdentity);
        check(recordValue(retained, "status").toString().equals("RETAINED_BY_REQUEST"),
                "retain opt-out must be recorded");
        check(Files.isDirectory(retainedSession.resolve("tmp"))
                        && Files.isDirectory(retainedSession.resolve("build/test-classes/traces")),
                "retain opt-out must preserve both compactable paths");

        Path runningSession = root.resolve("compact-running");
        Object runningPaths = createCompactionFixture(runningSession);
        Object runningIdentity = captureSessionIdentity(runningSession);
        Object running = compact(runningPaths, "RUNNING", false, List.of(), List.of(), runningIdentity);
        check(recordValue(running, "status").toString().equals("REFUSED"),
                "an active session must never be compacted");
        check(Files.isDirectory(runningSession.resolve("tmp")),
                "refused active compaction must not mutate the session");
    }

    private static void verifyTerminalCompactionRefusals(Path root) throws Exception {
        Path symlinkSession = root.resolve("compact-symlink");
        Object symlinkPaths = createCompactionFixture(symlinkSession);
        Object symlinkIdentity = captureSessionIdentity(symlinkSession);
        Path external = createOwnedDirectory(root.resolve("compact-symlink-external"));
        deleteTree(symlinkSession.resolve("tmp"));
        try {
            Files.createSymbolicLink(symlinkSession.resolve("tmp"), external);
            Object result = compact(symlinkPaths, "PASSED", false, List.of(), List.of(), symlinkIdentity);
            check(recordValue(result, "status").toString().equals("REFUSED"),
                    "a symlinked compactable path must be refused");
            check(Files.isDirectory(symlinkSession.resolve("build/test-classes/traces")),
                    "symlink refusal must not partially compact other candidates");
        } catch (UnsupportedOperationException e) {
            // Replacement and injected verifier cases still prove fail-closed identity checks.
        }

        Path replacedSession = root.resolve("compact-replaced");
        Object replacedPaths = createCompactionFixture(replacedSession);
        Object replacedIdentity = captureSessionIdentity(replacedSession);
        Path moved = root.resolve("compact-replaced-original");
        Files.move(replacedSession, moved);
        createCompactionFixture(replacedSession);
        Object replaced = compact(replacedPaths, "PASSED", false, List.of(), List.of(), replacedIdentity);
        check(recordValue(replaced, "status").toString().equals("REFUSED"),
                "a replaced session root must be refused");
        check(Files.isDirectory(replacedSession.resolve("tmp")),
                "replacement refusal must not mutate the replacement");

        Path mismatchedSession = root.resolve("compact-store-mismatch");
        Object mismatchedPaths = createCompactionFixture(mismatchedSession);
        Object mismatchedIdentity = captureSessionIdentity(mismatchedSession);
        Object mismatch = compactWithStoreVerifier(mismatchedPaths, "PASSED", false,
                List.of(), List.of(), mismatchedIdentity, (expected, candidate) -> false);
        check(recordValue(mismatch, "status").toString().equals("REFUSED"),
                "a file-store mismatch must be refused");
        check(Files.isDirectory(mismatchedSession.resolve("tmp")),
                "file-store refusal must not mutate the session");

        Path protectedSession = root.resolve("compact-protected-inventory");
        Object protectedPaths = createCompactionFixture(protectedSession);
        Object protectedIdentity = captureSessionIdentity(protectedSession);
        Path protectedReport = protectedSession.resolve("tmp/ephemeral.bin").toAbsolutePath().normalize();
        Object protectedResult = compact(protectedPaths, "PASSED", false,
                List.of(protectedReport.toString()), List.of(), protectedIdentity);
        check(recordValue(protectedResult, "status").toString().equals("REFUSED"),
                "an inventoried report below a candidate must block compaction");
        check(Files.isRegularFile(protectedReport), "an inventoried report must survive refusal");
        check(Files.isDirectory(protectedSession.resolve("build/test-classes/traces")),
                "inventory refusal must not partially compact another candidate");
    }

    private static void verifySecureCompactionRejectsPathSwap(Path root) throws Exception {
        Path session = root.resolve("compact-path-swap");
        Object paths = createCompactionFixture(session);
        Object identity = captureSessionIdentity(session);
        Path outside = createOwnedDirectory(root.resolve("compact-path-swap-outside"));
        Path outsideFile = outside.resolve("ephemeral.bin");
        Files.writeString(outsideFile, "outside-must-survive", StandardCharsets.UTF_8);
        Path capturedTmp = root.resolve("compact-path-swap-captured-tmp");
        boolean[] swapped = {false};

        Object result = compactWithDeletionHook(paths, "PASSED", false, List.of(), List.of(),
                identity, (expected, candidate) -> true, relative -> {
                    if (!swapped[0] && relative.equals("tmp/ephemeral.bin")) {
                        try {
                            Files.move(session.resolve("tmp"), capturedTmp);
                            Files.createSymbolicLink(session.resolve("tmp"), outside);
                            swapped[0] = true;
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });

        check(swapped[0], "swap hook must run after secure candidate binding");
        check(recordValue(result, "status").toString().equals("REFUSED"),
                "a replaced candidate binding must be refused");
        check(Files.readString(outsideFile).equals("outside-must-survive"),
                "descriptor-relative refusal must not touch the symlink target");
        @SuppressWarnings("unchecked")
        List<String> partial = (List<String>) recordValue(
                result, "partiallyModifiedRelativePaths");
        check(partial.equals(List.of("tmp")),
                "the captured tmp tree was partially modified before replacement refusal: " + partial);
        check((long) recordValue(result, "reclaimedBytes") == 8L,
                "the securely deleted captured file must be counted before replacement refusal");
    }

    private static void verifyCompactionInspectionFailureIsNotAbsence(Path root) throws Exception {
        Path session = root.resolve("compact-wrong-type-ancestor");
        Object paths = createCompactionFixture(session);
        Object identity = captureSessionIdentity(session);
        deleteTree(session.resolve("build/test-classes"));
        Files.writeString(session.resolve("build/test-classes"), "wrong-type",
                StandardCharsets.UTF_8);

        Object result = compact(paths, "PASSED", false, List.of(), List.of(), identity);

        check(recordValue(result, "status").toString().equals("REFUSED"),
                "a wrong-type candidate ancestor must not be treated as an absent candidate");
        check(Files.isRegularFile(session.resolve("tmp/ephemeral.bin")),
                "inspection uncertainty must refuse before deleting another candidate");
    }

    private static void verifyPartialCompactionEvidenceIsTruthful(Path root) throws Exception {
        Path session = root.resolve("compact-partial-failure");
        Object paths = createCompactionFixture(session);
        Object identity = captureSessionIdentity(session);
        Files.delete(session.resolve("tmp/ephemeral.bin"));
        Files.writeString(session.resolve("tmp/a.bin"), "aaa", StandardCharsets.UTF_8);
        Files.writeString(session.resolve("tmp/b.bin"), "bbbbb", StandardCharsets.UTF_8);

        Object result = compactWithDeletionHook(paths, "PASSED", false, List.of(), List.of(),
                identity, (expected, candidate) -> true, relative -> {
                    if (relative.equals("tmp/b.bin")) {
                        throw new UncheckedIOException(new IOException("injected mid-tree deletion failure"));
                    }
                });

        check(recordValue(result, "status").toString().equals("FAILED"),
                "an injected mid-tree deletion error must fail compaction");
        @SuppressWarnings("unchecked")
        List<String> removed = (List<String>) recordValue(result, "removedRelativePaths");
        @SuppressWarnings("unchecked")
        List<String> partial = (List<String>) recordValue(
                result, "partiallyModifiedRelativePaths");
        check(removed.isEmpty(), "a partially deleted candidate must not be reported as removed");
        check(partial.equals(List.of("tmp")),
                "partial deletion must name the modified candidate: " + partial);
        check((long) recordValue(result, "reclaimedBytes") == 3L,
                "only the successfully deleted first file must count as reclaimed");
        check(!Files.exists(session.resolve("tmp/a.bin"))
                        && Files.isRegularFile(session.resolve("tmp/b.bin")),
                "injected deletion failure must occur after exactly one file deletion");
        check(Files.isRegularFile(session.resolve("build/test-classes/traces/copied.bin")),
                "mid-tree failure must not continue to another candidate");
    }

    private static void verifyStableKeyTombstoneCompactionWithoutSecureStreams(Path root)
            throws Exception {
        Path session = root.resolve("compact-stable-key-tombstone-success");
        Object paths = createCompactionFixture(session);
        Object identity = captureSessionIdentity(session);
        Object control = tombstoneControl(true, true, "fixture-stable-key-provider",
                ignored -> { }, (expected, actual) -> expected.equals(actual), ignored -> false);

        Object result = compactWithTombstoneControl(paths, "PASSED", false, List.of(), List.of(),
                identity, (expected, candidate) -> true, ignored -> { }, control);

        check(recordValue(result, "status").toString().equals("COMPACTED"),
                "stable-key tombstone strategy must compact without secure streams: status="
                        + recordValue(result, "status") + " error=" + recordValue(result, "error"));
        @SuppressWarnings("unchecked")
        List<String> removed = (List<String>) recordValue(result, "removedRelativePaths");
        check(Set.copyOf(removed).equals(Set.of("tmp", "build/test-classes/traces")),
                "stable-key tombstone strategy removed unexpected candidates: " + removed);
        check((long) recordValue(result, "reclaimedBytes") == 18L,
                "stable-key tombstone strategy must report exact reclaimed bytes");
        assertOnlyCompactablePathsRemoved(session);
        try (var entries = Files.list(session)) {
            check(entries.noneMatch(path -> path.getFileName().toString()
                            .startsWith(".compaction-staging-")),
                    "successful stable-key tombstone compaction must remove its staging lane");
        }

        Path partialSession = root.resolve("compact-stable-key-tombstone-partial");
        Object partialPaths = createCompactionFixture(partialSession);
        Object partialIdentity = captureSessionIdentity(partialSession);
        Files.delete(partialSession.resolve("tmp/ephemeral.bin"));
        Files.writeString(partialSession.resolve("tmp/a.bin"), "aaa", StandardCharsets.UTF_8);
        Files.writeString(partialSession.resolve("tmp/b.bin"), "bbbbb", StandardCharsets.UTF_8);
        Object partialControl = tombstoneControl(true, true, "fixture-stable-key-provider",
                ignored -> { }, (expected, actual) -> expected.equals(actual), ignored -> false);
        Object partial = compactWithTombstoneControl(partialPaths, "PASSED", false,
                List.of(), List.of(), partialIdentity, (expected, candidate) -> true,
                relative -> {
                    if (relative.equals("tmp/b.bin")) {
                        throw new UncheckedIOException(
                                new IOException("injected tombstone mid-tree deletion failure"));
                    }
                }, partialControl);
        check(recordValue(partial, "status").toString().equals("FAILED"),
                "tombstone mid-tree deletion error must fail compaction");
        @SuppressWarnings("unchecked")
        List<String> partiallyModified = (List<String>) recordValue(
                partial, "partiallyModifiedRelativePaths");
        check(partiallyModified.equals(List.of("tmp")),
                "tombstone partial failure must name tmp: " + partiallyModified);
        check((long) recordValue(partial, "reclaimedBytes") == 3L,
                "tombstone partial failure must count only the deleted first file");
    }

    private static void verifyStableKeyTombstoneSwapAndReparseSafety(Path root) throws Exception {
        Path swapSession = root.resolve("compact-stable-key-tombstone-swap");
        Object swapPaths = createCompactionFixture(swapSession);
        Object swapIdentity = captureSessionIdentity(swapSession);
        Path outsideCaptured = root.resolve("compact-stable-key-tombstone-swap-captured");
        Object swapControl = tombstoneControl(true, true, "fixture-stable-key-provider",
                relative -> {
                    if (relative.equals("tmp")) {
                        try {
                            Files.move(swapSession.resolve("tmp"), outsideCaptured);
                            Files.createDirectory(swapSession.resolve("tmp"));
                            Files.writeString(swapSession.resolve("tmp/replacement.bin"),
                                    "replacement", StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                }, (expected, actual) -> expected.equals(actual), ignored -> false);

        Object swapped = compactWithTombstoneControl(swapPaths, "PASSED", false,
                List.of(), List.of(), swapIdentity, (expected, candidate) -> true,
                ignored -> { }, swapControl);

        check(recordValue(swapped, "status").toString().equals("REFUSED"),
                "stable-key candidate replacement must be refused after atomic tombstoning");
        check(Files.readString(outsideCaptured.resolve("ephemeral.bin")).equals("tmp-data"),
                "tombstone swap must not touch the outside captured-tree sentinel");
        check(!Files.exists(swapSession.resolve("tmp")),
                "tombstone swap must move the replacement itself into the staging lane");
        @SuppressWarnings("unchecked")
        List<String> swappedPartial = (List<String>) recordValue(
                swapped, "partiallyModifiedRelativePaths");
        check(swappedPartial.equals(List.of("tmp")),
                "moved replacement must be reported as a partial tmp mutation");

        Path identitySession = root.resolve("compact-stable-key-identity-mismatch");
        Object identityPaths = createCompactionFixture(identitySession);
        Object identity = captureSessionIdentity(identitySession);
        Object identityControl = tombstoneControl(true, true, "fixture-stable-key-provider",
                ignored -> { }, (expected, actual) -> false, ignored -> false);
        Object mismatch = compactWithTombstoneControl(identityPaths, "PASSED", false,
                List.of(), List.of(), identity, (expected, candidate) -> true,
                ignored -> { }, identityControl);
        check(recordValue(mismatch, "status").toString().equals("REFUSED"),
                "moved tombstone identity mismatch must be refused");
        check(findInStaging(identitySession, "ephemeral.bin") != null,
                "identity-mismatched tombstone contents must be retained without deletion");

        Path reparseSession = root.resolve("compact-stable-key-reparse");
        Object reparsePaths = createCompactionFixture(reparseSession);
        Object reparseIdentity = captureSessionIdentity(reparseSession);
        Object reparseControl = tombstoneControl(true, true, "fixture-stable-key-provider",
                ignored -> { }, (expected, actual) -> expected.equals(actual),
                path -> path.getFileName().toString().startsWith("candidate-"));
        Object reparse = compactWithTombstoneControl(reparsePaths, "PASSED", false,
                List.of(), List.of(), reparseIdentity, (expected, candidate) -> true,
                ignored -> { }, reparseControl);
        check(recordValue(reparse, "status").toString().equals("REFUSED"),
                "injected tombstone reparse point must be refused");
        check(findInStaging(reparseSession, "ephemeral.bin") != null,
                "reparse-refused tombstone contents must survive without traversal");
    }

    private static void verifyJdk21NativeWindowsUnsupportedContract(Path root) throws Exception {
        Path session = root.resolve("compact-jdk21-native-windows-unsupported");
        Object paths = createCompactionFixture(session);
        // Portable injection of the JDK 21 Windows public contract, not native-host evidence.
        Object identity = identityWithFileKey(captureSessionIdentity(session), null);
        String reason = "provider=sun.nio.fs.WindowsFileSystemProvider "
                + "file_store=fixture-ntfs secure_directory_stream=unavailable "
                + "stable_file_key=unavailable";
        Object control = tombstoneControl(true, false, reason,
                ignored -> { throw new AssertionError("JDK 21 Windows contract must not mutate"); },
                (expected, actual) -> false, ignored -> false);

        Object result = compactWithTombstoneControl(paths, "PASSED", false, List.of(), List.of(),
                identity, (expected, candidate) -> true, ignored -> { }, control);

        check(recordValue(result, "status").toString().equals("RETAINED_PLATFORM_UNSUPPORTED"),
                "JDK 21 Windows null public file key must retain certifying evidence");
        check(reason.equals(recordValue(result, "error")),
                "JDK 21 Windows retention must record the exact provider/file-store reason");
        check(compactionFailure(result) == null,
                "platform-unsupported retention must remain certifying");
        check(Files.isRegularFile(session.resolve("tmp/ephemeral.bin"))
                        && Files.isRegularFile(
                        session.resolve("build/test-classes/traces/copied.bin")),
                "JDK 21 Windows unsupported retention must perform no mutation");
        check(((List<?>) recordValue(result, "removedRelativePaths")).isEmpty()
                        && ((List<?>) recordValue(
                        result, "partiallyModifiedRelativePaths")).isEmpty()
                        && (long) recordValue(result, "reclaimedBytes") == 0L,
                "JDK 21 Windows unsupported retention must report zero mutation");
    }

    private static Path findInStaging(Path session, String fileName) throws IOException {
        try (var tree = Files.walk(session)) {
            return tree.filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst().orElse(null);
        }
    }

    private static Object createCompactionFixture(Path session) throws Exception {
        Class<?> pathsType = Class.forName("TestSessionCoordinator$Paths");
        var create = pathsType.getDeclaredMethod("create", Path.class);
        create.setAccessible(true);
        Object paths = create.invoke(null, Files.createDirectory(session));
        Files.writeString(session.resolve("tmp/ephemeral.bin"), "tmp-data", StandardCharsets.UTF_8);
        Files.createDirectories(session.resolve("build/test-classes/traces"));
        Files.writeString(session.resolve("build/test-classes/traces/copied.bin"),
                "trace-data", StandardCharsets.UTF_8);
        for (String preserved : List.of(
                "manifest.json", "command.txt", "maven.log",
                "surefire-reports/result.xml", "trace-reports/frontier.txt",
                "diagnostics/diagnostic.txt", "build/classes/Main.class",
                "build/test-classes/ordinary-resource.bin", "build/OpenGGF.jar",
                "build/native-libs/libopenggf.so", "artifacts/promoted.bin",
                "distribution/OpenGGF.zip")) {
            Path file = session.resolve(preserved);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "preserved", StandardCharsets.UTF_8);
        }
        return paths;
    }

    private static void assertOnlyCompactablePathsRemoved(Path session) {
        check(!Files.exists(session.resolve("tmp")), "tmp must be removed");
        check(!Files.exists(session.resolve("build/test-classes/traces")),
                "copied trace resources must be removed");
        for (String preserved : List.of(
                "manifest.json", "command.txt", "maven.log",
                "surefire-reports/result.xml", "trace-reports/frontier.txt",
                "diagnostics/diagnostic.txt", "build/classes/Main.class",
                "build/test-classes/ordinary-resource.bin", "build/OpenGGF.jar",
                "build/native-libs/libopenggf.so", "artifacts/promoted.bin",
                "distribution/OpenGGF.zip")) {
            check(Files.isRegularFile(session.resolve(preserved)),
                    "compaction removed preserved evidence: " + preserved);
        }
    }

    private static Object captureSessionIdentity(Path session) throws Exception {
        var method = TestSessionCoordinator.class.getDeclaredMethod(
                "captureSessionDirectoryIdentity", Path.class);
        method.setAccessible(true);
        return method.invoke(null, session);
    }

    private static Object compact(Object paths, String state, boolean retainEphemeral,
                                  List<String> reports, List<String> artifacts, Object identity)
            throws Exception {
        Class<?> pathsType = Class.forName("TestSessionCoordinator$Paths");
        Class<?> identityType = Class.forName("TestSessionCoordinator$SessionDirectoryIdentity");
        var method = TestSessionCoordinator.class.getDeclaredMethod("compactTerminalSession",
                pathsType, String.class, boolean.class, List.class, List.class, identityType);
        method.setAccessible(true);
        return method.invoke(null, paths, state, retainEphemeral, reports, artifacts, identity);
    }

    private static Object compactWithStoreVerifier(
            Object paths, String state, boolean retainEphemeral, List<String> reports,
            List<String> artifacts, Object identity, BiPredicate<FileStore, Path> verifier)
            throws Exception {
        Class<?> pathsType = Class.forName("TestSessionCoordinator$Paths");
        Class<?> identityType = Class.forName("TestSessionCoordinator$SessionDirectoryIdentity");
        var method = TestSessionCoordinator.class.getDeclaredMethod("compactTerminalSession",
                pathsType, String.class, boolean.class, List.class, List.class, identityType,
                BiPredicate.class);
        method.setAccessible(true);
        return method.invoke(null, paths, state, retainEphemeral, reports, artifacts,
                identity, verifier);
    }

    private static Object compactWithDeletionHook(
            Object paths, String state, boolean retainEphemeral, List<String> reports,
            List<String> artifacts, Object identity, BiPredicate<FileStore, Path> verifier,
            Consumer<String> deletionHook) throws Exception {
        Class<?> pathsType = Class.forName("TestSessionCoordinator$Paths");
        Class<?> identityType = Class.forName("TestSessionCoordinator$SessionDirectoryIdentity");
        var method = TestSessionCoordinator.class.getDeclaredMethod("compactTerminalSession",
                pathsType, String.class, boolean.class, List.class, List.class, identityType,
                BiPredicate.class, Consumer.class);
        method.setAccessible(true);
        return method.invoke(null, paths, state, retainEphemeral, reports, artifacts,
                identity, verifier, deletionHook);
    }

    private static Object tombstoneControl(
            boolean forceNoSecureStream, boolean stableFileKeys, String providerReason,
            Consumer<String> beforeCandidateMove, BiPredicate<Object, Object> movedIdentityMatches,
            Predicate<Path> reparsePoint) throws Exception {
        Class<?> controlType = Class.forName("TestSessionCoordinator$NativeCompactionControl");
        var constructor = controlType.getDeclaredConstructor(boolean.class, boolean.class,
                String.class, Consumer.class, BiPredicate.class, Predicate.class);
        constructor.setAccessible(true);
        return constructor.newInstance(forceNoSecureStream, stableFileKeys, providerReason,
                beforeCandidateMove, movedIdentityMatches, reparsePoint);
    }

    private static Object identityWithFileKey(Object identity, Object fileKey) throws Exception {
        Class<?> identityType = Class.forName("TestSessionCoordinator$SessionDirectoryIdentity");
        var constructor = identityType.getDeclaredConstructor(
                Path.class, Object.class, FileStore.class);
        constructor.setAccessible(true);
        return constructor.newInstance(recordValue(identity, "realPath"), fileKey,
                recordValue(identity, "fileStore"));
    }

    private static Object compactWithTombstoneControl(
            Object paths, String state, boolean retainEphemeral, List<String> reports,
            List<String> artifacts, Object identity, BiPredicate<FileStore, Path> verifier,
            Consumer<String> deletionHook, Object control) throws Exception {
        Class<?> pathsType = Class.forName("TestSessionCoordinator$Paths");
        Class<?> identityType = Class.forName("TestSessionCoordinator$SessionDirectoryIdentity");
        Class<?> controlType = Class.forName("TestSessionCoordinator$NativeCompactionControl");
        var method = TestSessionCoordinator.class.getDeclaredMethod("compactTerminalSession",
                pathsType, String.class, boolean.class, List.class, List.class, identityType,
                BiPredicate.class, Consumer.class, controlType);
        method.setAccessible(true);
        return method.invoke(null, paths, state, retainEphemeral, reports, artifacts,
                identity, verifier, deletionHook, control);
    }

    private static Object recordValue(Object record, String accessor) throws Exception {
        var method = record.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private static String compactionFailure(Object result) throws Exception {
        Class<?> resultType = Class.forName("TestSessionCoordinator$CompactionResult");
        var method = TestSessionCoordinator.class.getDeclaredMethod(
                "compactionFailure", resultType);
        method.setAccessible(true);
        return (String) method.invoke(null, result);
    }

    private static void verifyRequiredFreeBytesFormula() throws Exception {
        Class<?> capacityType = Class.forName("TestSessionCoordinator$CapacitySnapshot");
        var constructor = capacityType.getDeclaredConstructor(long.class, long.class, long.class);
        constructor.setAccessible(true);
        var method = TestSessionCoordinator.class.getDeclaredMethod("requiredFreeBytes", capacityType);
        method.setAccessible(true);
        long fivePercent = (long) method.invoke(null, constructor.newInstance(0L, 1_000L << 30, 1L));
        long minimum = (long) method.invoke(null, constructor.newInstance(0L, 100L << 30, 1L));
        check(fivePercent == 50L << 30, "five percent should exceed 20 GiB");
        check(minimum == 20L << 30, "20 GiB should be the minimum floor");
    }

    private static void verifyCompactionAcrossStorageTiers(Path root, Path explicitOutput)
            throws Exception {
        int index = 0;
        CommandResult explicit = runCoordinator(explicitOutput, List.of(
                "--lock-root", createOwnedDirectory(root.resolve("compact-tier-explicit-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        assertCompactedTier(explicit, "EXPLICIT_OVERRIDE", index++);

        Path project = createTestProject(root.resolve("compact-tier-project"));
        CommandResult fallback = runStorageCoordinator(project, null, null, null, List.of(
                "--lock-root", createOwnedDirectory(root.resolve("compact-tier-project-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        assertCompactedTier(fallback, "PROJECT_LOCAL_FALLBACK", index++);

        Path systemProject = createTestProject(root.resolve("compact-tier-system-project"));
        ProcessBuilder system = storageCoordinatorProcess(systemProject, null, null, null, List.of(
                "--allow-system-tmp",
                "--lock-root", createOwnedDirectory(root.resolve("compact-tier-system-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        Path systemTmp = createOwnedDirectory(root.resolve("compact-tier-system-tmp"));
        system.environment().put("JAVA_TOOL_OPTIONS", "-Dselftest.java=preserved -Djava.io.tmpdir="
                + systemTmp);
        assertCompactedTier(finish(system.start()), "SYSTEM_TMP_EXPLICIT", index++);

        Path managedProject = createTestProject(root.resolve("compact-tier-managed-project"));
        Path managedRoot = createOwnedDirectory(root.resolve("compact-tier-managed-root"));
        Path allocation = createOwnedDirectory(
                managedRoot.resolve("codex/test-sessions/session-reserved"));
        Path fakeBin = createFakeAgentScratch(root.resolve("compact-tier-managed-bin"),
                reservationJson(managedRoot, allocation, Instant.now().plus(Duration.ofDays(6))));
        CommandResult managed = runStorageCoordinator(managedProject, managedRoot, fakeBin, null, List.of(
                "--lock-root", createOwnedDirectory(root.resolve("compact-tier-managed-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        assertCompactedTier(managed, "MANAGED_CODEX_TEST_SESSIONS", index);
    }

    private static void assertCompactedTier(CommandResult result, String tier, int index)
            throws IOException {
        check(result.exitCode == 0, "tier compaction run " + index + " failed:\n" + result.output);
        Path manifest = Path.of(markerValue(
                findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String json = Files.readString(manifest);
        check(json.contains("\"storage_tier\": \"" + tier + "\""),
                "compaction run did not use tier " + tier);
        check(json.contains("\"compaction_status\": \"COMPACTED\""),
                "terminal session was not compacted for " + tier);
        check(!Files.exists(manifest.getParent().resolve("tmp")),
                "terminal tmp survived for " + tier);
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
        Path lease = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "lease"));
        check(manifest.getParent().getParent().equals(allocation),
                "managed reservation must be the parent of the coordinator-created run: " + manifest);
        check(lease.getParent().getParent().equals(lockRoot),
                "explicit lock-root must take priority over the managed lease root: " + lease);
        try (var entries = Files.list(managedLeaseRoot(managedRoot))) {
            check(entries.findAny().isEmpty(),
                    "explicit lock-root run must not publish metadata in the managed lease root");
        }
        check(!Files.exists(project.resolve(".openggf/test-runs")),
                "validated managed allocation must not create a project-local fallback");
        String json = Files.readString(manifest);
        check(json.contains("\"storage_tier\": \"MANAGED_CODEX_TEST_SESSIONS\""),
                "managed manifest must identify its storage tier");
        check(json.contains("\"allocation_schema\": 1"),
                "managed manifest must preserve the allocation schema");
        check(json.contains("\"helper_version\": \"openggf-agent-scratch-v2\""),
                "managed manifest must preserve the helper version");
        check(json.contains("\"allocation_not_applicable_reason\": null"),
                "managed manifest must not invent a not-applicable reason");
        check(json.contains("\"allocation_usable_inodes\": 1024"),
                "managed manifest must retain the helper allocation-time inode snapshot");
        check(json.contains("\"allocation_inode_count_status\": \"MEASURED\""),
                "managed manifest must preserve measured inode provenance");
        check(json.contains("\"allocation_usable_inodes_reason\": null"),
                "measured managed inodes must not carry an unavailable reason");
        check(json.contains("\"launch_usable_inodes\": null"),
                "managed launch must not publish an allocation snapshot as a live numeric measurement");
        check(json.contains("\"launch_usable_inodes_reason\": "
                        + "\"live numeric inode count unavailable; probe status authoritative\""),
                "managed launch must explain phase-current numeric inode nullability");
        check(json.contains("\"completion_usable_inodes\": null"),
                "managed completion must not publish an allocation snapshot as a live numeric measurement");
        check(json.contains("\"completion_usable_inodes_reason\": "
                        + "\"live numeric inode count unavailable; probe status authoritative\""),
                "managed completion must explain phase-current numeric inode nullability");
        check(json.contains("\"launch_inode_probe_status\": \"AVAILABLE\""),
                "managed launch must record a successful live inode probe");
        check(json.contains("\"completion_inode_probe_status\": \"AVAILABLE\""),
                "managed completion must record a successful live inode probe");
    }

    private static void verifyManagedLeaseRootDefaultsOutsideReadOnlyGitCommon(Path root)
            throws Exception {
        Path project = createTestProject(root.resolve("managed-default-lease-project"));
        Path managedRoot = createOwnedDirectory(root.resolve("managed-default-lease-root"));
        Path allocation = createOwnedDirectory(
                managedRoot.resolve("codex/test-sessions/session-reserved"));
        Path leaseRoot = managedLeaseRoot(managedRoot);
        Path fakeBin = createFakeAgentScratch(root.resolve("managed-default-lease-bin"),
                reservationJson(managedRoot, allocation, Instant.now().plus(Duration.ofDays(6))));
        Path gitCommon = project.resolve(".git");
        Set<java.nio.file.attribute.PosixFilePermission> originalPermissions =
                Files.getPosixFilePermissions(gitCommon);
        CommandResult result;
        try {
            Files.setPosixFilePermissions(gitCommon, Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
            result = runStorageCoordinator(project, managedRoot, fakeBin, null, List.of(
                    "--", javaCommand(), "-cp", classPath(),
                    TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        } finally {
            Files.setPosixFilePermissions(gitCommon, originalPermissions);
        }

        check(result.exitCode == 0,
                "managed default lease root must permit launch when Git common is read-only:\n"
                        + result.output);
        String start = findLine(result.output, "OPENGGF_TEST_RUN_START");
        Path lease = Path.of(markerValue(start, "lease"));
        check(lease.getParent().getParent().equals(leaseRoot),
                "managed default lease must be published under the verified lease lane: " + lease);
        check(Files.isRegularFile(lease.getParent().resolve("owner.json")),
                "managed default lease namespace must retain owner metadata");
        check(!Files.exists(gitCommon.resolve("openggf-test-session.lock")),
                "managed default must not attempt to publish lock metadata in Git common");
    }

    private static void verifyManagedDynamicInodesUseLiveProbe(Path root) throws Exception {
        Path project = createTestProject(root.resolve("managed-dynamic-inodes-project"));
        Path managedRoot = createOwnedDirectory(root.resolve("managed-dynamic-inodes-root"));
        Path allocation = createOwnedDirectory(
                managedRoot.resolve("codex/test-sessions/session-reserved"));
        var store = Files.getFileStore(allocation);
        Path fakeBin = createFakeAgentScratch(root.resolve("managed-dynamic-inodes-bin"),
                reservationJson(managedRoot, allocation, Instant.now().plus(Duration.ofDays(6)),
                        store.getUsableSpace(), store.getTotalSpace(), "UNAVAILABLE_DYNAMIC", null));

        CommandResult result = runStorageCoordinator(project, managedRoot, fakeBin, null, List.of(
                "--lock-root", createOwnedDirectory(
                        root.resolve("managed-dynamic-inodes-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));

        check(result.exitCode == 0,
                "dynamic-inode managed allocation must launch after a successful live probe:\n"
                        + result.output);
        Path manifest = Path.of(markerValue(
                findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String json = Files.readString(manifest);
        check(json.contains("\"allocation_inode_count_status\": \"UNAVAILABLE_DYNAMIC\""),
                "dynamic-inode manifest must preserve helper status");
        check(json.contains("\"allocation_usable_inodes\": null"),
                "dynamic-inode manifest must not fabricate a numeric allocation count");
        check(json.contains("\"allocation_usable_inodes_reason\": "
                        + "\"filesystem uses dynamic inode allocation; numeric count unavailable\""),
                "dynamic-inode manifest must explain numeric nullability");
        check(json.contains("\"launch_inode_probe_status\": \"AVAILABLE\""),
                "dynamic-inode launch must use the phase-current live probe");
        check(json.contains("\"completion_inode_probe_status\": \"AVAILABLE\""),
                "dynamic-inode completion must use its independent live probe");
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
        Path expectedLeaseRoot = managedLeaseRoot(managedRoot);
        Path outsideLeaseRoot = createOwnedDirectory(root.resolve("managed-outside-lease-root"));
        Path symlinkLeaseRoot = managedRoot.resolve("codex/symlink-lease-root");
        Files.createSymbolicLink(symlinkLeaseRoot, outsideLeaseRoot);
        Path fileLeaseRoot = managedRoot.resolve("codex/file-lease-root");
        Files.writeString(fileLeaseRoot, "not a directory\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
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
        malformed.put("missing-lease-root", valid.replace(
                ",\"lease_root\":\"" + jsonEscape(expectedLeaseRoot.toString()) + "\"", ""));
        malformed.put("lease-root-type", valid.replace(
                "\"lease_root\":\"" + jsonEscape(expectedLeaseRoot.toString()) + "\"",
                "\"lease_root\":1"));
        malformed.put("lease-root-outside-lane", valid.replace(
                jsonEscape(expectedLeaseRoot.toString()), jsonEscape(outsideLeaseRoot.toString())));
        malformed.put("lease-root-symlink", valid.replace(
                jsonEscape(expectedLeaseRoot.toString()), jsonEscape(symlinkLeaseRoot.toString())));
        malformed.put("lease-root-wrong-type", valid.replace(
                jsonEscape(expectedLeaseRoot.toString()), jsonEscape(fileLeaseRoot.toString())));
        malformed.put("device-type", valid.replaceFirst("\"filesystem_device\":\\d+",
                "\"filesystem_device\":\"1\""));
        malformed.put("device-mismatch", valid.replaceFirst("\"filesystem_device\":\\d+",
                "\"filesystem_device\":9223372036854775807"));
        malformed.put("usable-bytes-type", valid.replaceFirst("\"usable_bytes\":\\d+",
                "\"usable_bytes\":\"1048576\""));
        malformed.put("total-bytes-type", valid.replaceFirst("\"total_bytes\":\\d+",
                "\"total_bytes\":\"2097152\""));
        malformed.put("inodes-type", valid.replaceFirst("\"usable_inodes\":\\d+",
                "\"usable_inodes\":\"1024\""));
        malformed.put("inode-status-unknown", valid.replace(
                "\"inode_count_status\":\"MEASURED\"",
                "\"inode_count_status\":\"UNKNOWN\""));
        malformed.put("inode-status-type", valid.replace(
                "\"inode_count_status\":\"MEASURED\"", "\"inode_count_status\":1"));
        malformed.put("measured-null", valid.replace("\"usable_inodes\":1024",
                "\"usable_inodes\":null"));
        malformed.put("dynamic-numeric", valid.replace(
                "\"inode_count_status\":\"MEASURED\"",
                "\"inode_count_status\":\"UNAVAILABLE_DYNAMIC\""));
        malformed.put("dynamic-negative", valid.replace(
                "\"inode_count_status\":\"MEASURED\",\"usable_inodes\":1024",
                "\"inode_count_status\":\"UNAVAILABLE_DYNAMIC\",\"usable_inodes\":-1"));
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
        malformed.put("helper-version-marker-injection", valid.replace("openggf-agent-scratch-v2",
                "unsupported\\nOPENGGF_TEST_RUN_START run_id=counterfeit"));
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
            check(result.output.lines().noneMatch(line -> line.startsWith("OPENGGF_TEST_RUN_START")),
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
        String json = Files.readString(manifest);
        check(json.contains("\"storage_tier\": \"PROJECT_LOCAL_FALLBACK\""),
                "unmanaged manifest must preserve the fallback tier");
        check(json.contains("\"allocation_schema\": null"),
                "unmanaged allocation schema must be explicitly null");
        check(json.contains("\"helper_version\": null"),
                "unmanaged helper version must be explicitly null");
        check(json.contains("\"allocation_not_applicable_reason\": "
                        + "\"managed reservation fields do not apply to PROJECT_LOCAL_FALLBACK\""),
                "unmanaged manifest must explain helper-field nullability");
        check(json.contains("\"storage_warning\": \"OPENGGF_TEST_SESSION_WARNING "
                        + "storage_tier=PROJECT_LOCAL_FALLBACK reason=managed-scratch-not-configured "
                        + "action=install-agent-scratch\""),
                "unmanaged manifest must preserve the actionable storage warning");
        check(json.contains("\"allocation_usable_inodes\": null"),
                "unmanaged numeric inode count must be unavailable rather than fabricated");
        check(json.contains("\"allocation_inode_count_status\": null"),
                "unmanaged allocation must not claim a helper inode-count status");
        check(json.contains("\"allocation_usable_inodes_reason\": "
                        + "\"numeric inode count is unavailable for PROJECT_LOCAL_FALLBACK; "
                        + "live availability probe is authoritative\""),
                "unmanaged allocation must explain numeric inode nullability");
        check(json.contains("\"launch_usable_inodes\": null"),
                "unmanaged launch inode count must be explicitly null");
        check(json.contains("\"completion_usable_inodes\": null"),
                "unmanaged completion inode count must be explicitly null");
        check(json.contains("\"launch_usable_inodes_reason\": "
                        + "\"live numeric inode count unavailable; probe status authoritative\""),
                "unmanaged launch must identify the live probe as phase-current authority");
        check(json.contains("\"completion_usable_inodes_reason\": "
                        + "\"live numeric inode count unavailable; probe status authoritative\""),
                "unmanaged completion must identify the live probe as phase-current authority");
        check(json.contains("\"numeric_inode_unavailable_reason\": "
                        + "\"numeric inode count is unavailable for PROJECT_LOCAL_FALLBACK; "
                        + "live availability probe is authoritative\""),
                "unmanaged manifest must explain numeric inode nullability");
        check(json.contains("\"launch_inode_probe_status\": \"AVAILABLE\""),
                "unmanaged launch must record live inode availability");
    }

    private static void verifyLowCapacityPreventsLaunch(Path root) throws Exception {
        Path project = createTestProject(root.resolve("capacity-low-project"));
        Path outputRoot = createOwnedDirectory(root.resolve("capacity-low-output"));
        Path childMarker = root.resolve("capacity-low-child-started");
        ProcessBuilder builder = storageCoordinatorProcess(project, null, null, childMarker, List.of(
                "--lock-root", createOwnedDirectory(root.resolve("capacity-low-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-mark-start"));
        builder.environment().put("OPENGGF_TEST_ROOT", outputRoot.toString());
        builder.environment().put("OPENGGF_TEST_MIN_FREE_BYTES", Long.toString(Long.MAX_VALUE));

        CommandResult result = finish(builder.start());

        Path manifest = assertStartupFailedWithoutChild(
                result, outputRoot, childMarker, "low-capacity override");
        String start = findLine(result.output, "OPENGGF_TEST_RUN_START");
        String end = findLine(result.output, "OPENGGF_TEST_RUN_END");
        check("EXPLICIT_OVERRIDE".equals(markerValue(start, "storage_tier")),
                "capacity-refusal start marker must preserve the storage tier");
        check(Long.toString(Long.MAX_VALUE).equals(markerValue(start, "capacity_floor_bytes")),
                "capacity-refusal start marker must publish the raised floor");
        check(markerValue(start, "launch_usable_bytes").matches("\\d+"),
                "capacity-refusal start marker must publish launch bytes");
        check("STARTUP_FAILED".equals(markerValue(end, "state")),
                "capacity-refusal end marker must publish STARTUP_FAILED");
        String json = Files.readString(manifest);
        check(json.contains("\"capacity_floor_bytes\": " + Long.MAX_VALUE),
                "capacity-refusal manifest must publish the raised floor");
        check(json.matches("(?s).*\"launch_usable_bytes\": \\d+.*"),
                "capacity-refusal manifest must publish launch bytes");
        check(json.contains("\"allocation_schema\": null"),
                "explicit allocation schema must be explicitly null");
        check(json.contains("\"helper_version\": null"),
                "explicit helper version must be explicitly null");
        check(json.contains("\"storage_warning\": null"),
                "explicit allocation warning must be explicitly null");
    }

    private static void verifyInvalidAndLowerCapacityOverridesFail(Path root) throws Exception {
        int index = 0;
        for (String override : List.of("not-a-number", "1", "", "   \t")) {
            Path project = createTestProject(root.resolve("capacity-invalid-project-" + index));
            Path outputRoot = createOwnedDirectory(root.resolve("capacity-invalid-output-" + index));
            Path childMarker = root.resolve("capacity-invalid-child-" + index);
            ProcessBuilder builder = storageCoordinatorProcess(project, null, null, childMarker, List.of(
                    "--lock-root", createOwnedDirectory(root.resolve("capacity-invalid-locks-" + index)).toString(),
                    "--", javaCommand(), "-cp", classPath(),
                    TestSessionCoordinatorSelfTest.class.getName(), "child-mark-start"));
            builder.environment().put("OPENGGF_TEST_ROOT", outputRoot.toString());
            builder.environment().put("OPENGGF_TEST_MIN_FREE_BYTES", override);

            CommandResult result = finish(builder.start());

            assertStartupFailedWithoutChild(result, outputRoot, childMarker,
                    "invalid or lower capacity override " + override);
            index++;
        }
    }

    private static void verifyZeroUsableInodesPreventLaunch(Path root) throws Exception {
        Path project = createTestProject(root.resolve("capacity-zero-inodes-project"));
        Path managedRoot = createOwnedDirectory(root.resolve("capacity-zero-inodes-managed"));
        Path allocation = createOwnedDirectory(managedRoot.resolve("codex/test-sessions/session-reserved"));
        var store = Files.getFileStore(allocation);
        Path fakeBin = createFakeAgentScratch(root.resolve("capacity-zero-inodes-bin"),
                reservationJson(managedRoot, allocation, Instant.now().plus(Duration.ofDays(6)),
                        store.getUsableSpace(), store.getTotalSpace(), "MEASURED", 0L));
        Path childMarker = root.resolve("capacity-zero-inodes-child-started");
        ProcessBuilder builder = storageCoordinatorProcess(
                project, managedRoot, fakeBin, childMarker, List.of(
                "--lock-root", createOwnedDirectory(root.resolve("capacity-zero-inodes-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-mark-start"));

        CommandResult result = finish(builder.start());

        Path manifest = assertStartupFailedWithoutChild(result, allocation, childMarker,
                "zero usable inodes");
        String json = Files.readString(manifest);
        check(json.contains("\"allocation_usable_inodes\": 0"),
                "zero-inode refusal must preserve the allocation-time inode snapshot");
        check(json.contains("\"launch_usable_inodes\": null"),
                "zero-inode refusal must not relabel the allocation snapshot as launch-time data");
        check(json.contains("\"launch_usable_inodes_reason\": "
                        + "\"live numeric inode count unavailable; probe status authoritative\""),
                "zero-inode refusal must explain launch numeric inode nullability");
    }

    private static void verifyLiveProbeFailurePreventsLaunchAcrossTiers(Path root) throws Exception {
        int index = 0;

        Path explicitProject = createTestProject(root.resolve("live-probe-explicit-project"));
        Path explicitOutput = createOwnedDirectory(root.resolve("live-probe-explicit-output"));
        Path explicitChild = root.resolve("live-probe-explicit-child");
        ProcessBuilder explicit = storageCoordinatorProcess(
                explicitProject, null, null, explicitChild, childMarkCommand(
                        createOwnedDirectory(root.resolve("live-probe-explicit-locks"))));
        explicit.environment().put("OPENGGF_TEST_ROOT", explicitOutput.toString());
        assertInjectedLaunchProbeFailure(explicit, explicitOutput, explicitChild,
                "EXPLICIT_OVERRIDE", index++);

        Path fallbackProject = createTestProject(root.resolve("live-probe-fallback-project"));
        Path fallbackChild = root.resolve("live-probe-fallback-child");
        ProcessBuilder fallback = storageCoordinatorProcess(
                fallbackProject, null, null, fallbackChild, childMarkCommand(
                        createOwnedDirectory(root.resolve("live-probe-fallback-locks"))));
        assertInjectedLaunchProbeFailure(fallback, fallbackProject.resolve(".openggf/test-runs"),
                fallbackChild, "PROJECT_LOCAL_FALLBACK", index++);

        Path systemProject = createTestProject(root.resolve("live-probe-system-project"));
        Path systemChild = root.resolve("live-probe-system-child");
        ProcessBuilder system = storageCoordinatorProcess(
                systemProject, null, null, systemChild, childMarkCommand(
                        createOwnedDirectory(root.resolve("live-probe-system-locks")),
                        "--allow-system-tmp"));
        Path systemTmp = createOwnedDirectory(root.resolve("live-probe-system-tmp"));
        system.environment().put("JAVA_TOOL_OPTIONS", "-Dselftest.java=preserved -Djava.io.tmpdir="
                + systemTmp);
        assertInjectedLaunchProbeFailure(system, null, systemChild,
                "SYSTEM_TMP_EXPLICIT", index++);

        Path managedProject = createTestProject(root.resolve("live-probe-managed-project"));
        Path managedRoot = createOwnedDirectory(root.resolve("live-probe-managed-root"));
        Path allocation = createOwnedDirectory(managedRoot.resolve("codex/test-sessions/session-reserved"));
        Path fakeBin = createFakeAgentScratch(root.resolve("live-probe-managed-bin"),
                reservationJson(managedRoot, allocation, Instant.now().plus(Duration.ofDays(6))));
        Path managedChild = root.resolve("live-probe-managed-child");
        ProcessBuilder managed = storageCoordinatorProcess(
                managedProject, managedRoot, fakeBin, managedChild, childMarkCommand(
                        createOwnedDirectory(root.resolve("live-probe-managed-locks"))));
        assertInjectedLaunchProbeFailure(managed, allocation, managedChild,
                "MANAGED_CODEX_TEST_SESSIONS", index);
    }

    private static void assertInjectedLaunchProbeFailure(ProcessBuilder builder, Path outputRoot,
                                                         Path childMarker, String tier, int index)
            throws Exception {
        builder.environment().put("OPENGGF_TEST_LIVE_PROBE_FAILURE_PHASE", "launch");
        CommandResult result = finish(builder.start());
        Path manifest = assertStartupFailedWithoutChild(result, outputRoot, childMarker,
                "injected launch live-probe failure " + index);
        String json = Files.readString(manifest);
        check(json.contains("\"storage_tier\": \"" + tier + "\""),
                "live-probe refusal must preserve tier " + tier);
        check(json.contains("\"launch_inode_probe_status\": \"FAILED\""),
                "live-probe refusal must record FAILED for " + tier);
    }

    private static void verifyCapacityProbeFailurePublishesStartupEvidence(Path root) throws Exception {
        Path project = createTestProject(root.resolve("capacity-io-project"));
        Path outputRoot = createOwnedDirectory(root.resolve("capacity-io-output"));
        Path childMarker = root.resolve("capacity-io-child");
        ProcessBuilder builder = storageCoordinatorProcess(project, null, null, childMarker,
                childMarkCommand(createOwnedDirectory(root.resolve("capacity-io-locks"))));
        builder.environment().put("OPENGGF_TEST_ROOT", outputRoot.toString());
        builder.environment().put("OPENGGF_TEST_CAPACITY_PROBE_FAILURE_PHASE", "launch");

        CommandResult result = finish(builder.start());

        Path manifest = assertStartupFailedWithoutChild(
                result, outputRoot, childMarker, "launch capacity-probe IOException");
        String json = Files.readString(manifest);
        check(json.contains("\"launch_capacity_error\": \"injected launch capacity probe failure\""),
                "launch capacity IOException must be recorded in terminal evidence");
    }

    private static void verifyCompletionProbeFailuresPreserveTerminalState(Path root) throws Exception {
        for (String probe : List.of("capacity", "live")) {
            Path project = createTestProject(root.resolve("completion-" + probe + "-project"));
            Path outputRoot = createOwnedDirectory(root.resolve("completion-" + probe + "-output"));
            ProcessBuilder builder = storageCoordinatorProcess(project, null, null, null, List.of(
                    "--lock-root", createOwnedDirectory(root.resolve("completion-" + probe + "-locks")).toString(),
                    "--", javaCommand(), "-cp", classPath(),
                    TestSessionCoordinatorSelfTest.class.getName(), "child-exit-7"));
            builder.environment().put("OPENGGF_TEST_ROOT", outputRoot.toString());
            builder.environment().put(probe.equals("capacity")
                    ? "OPENGGF_TEST_CAPACITY_PROBE_FAILURE_PHASE"
                    : "OPENGGF_TEST_LIVE_PROBE_FAILURE_PHASE", "completion");

            CommandResult result = finish(builder.start());

            check(result.exitCode == 7, "completion " + probe
                    + " probe failure must preserve the primary child exit code");
            Path manifest = Path.of(markerValue(
                    findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
            String json = Files.readString(manifest);
            check(json.contains("\"state\": \"FAILED\""),
                    "completion " + probe + " failure must preserve FAILED state");
            check(!json.contains("\"state\": \"RUNNING\""),
                    "completion " + probe + " failure must not strand RUNNING");
            check(json.contains("\"storage_finalization_error\":"),
                    "completion " + probe + " failure must record an additional storage error");
        }
    }

    private static void verifyUnsupportedDirectoryFlushIsObservable(Path root) throws Exception {
        Path outputRoot = createOwnedDirectory(root.resolve("directory-flush-output"));
        ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                "--lock-root", createOwnedDirectory(root.resolve("directory-flush-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        builder.environment().put("OPENGGF_TEST_DIRECTORY_FLUSH_UNSUPPORTED", "1");

        CommandResult result = finish(builder.start());

        check(result.exitCode == 0,
                "unsupported directory flush must not reject a successful portable probe:\n" + result.output);
        Path manifest = Path.of(markerValue(
                findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String json = Files.readString(manifest);
        check(json.contains("\"launch_directory_flush_status\": \"DIRECTORY_FLUSH_UNSUPPORTED\""),
                "launch must record unsupported directory flush capability");
        check(json.contains("\"completion_directory_flush_status\": \"DIRECTORY_FLUSH_UNSUPPORTED\""),
                "completion must record unsupported directory flush capability");
        check(json.contains("\"completion_inode_probe_status\": \"AVAILABLE\""),
                "portable file probe must remain authoritative when directory flush is unsupported");
    }

    private static void verifyMarkerFieldsCannotForgeLines(Path root, Path outputRoot) throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve(
                "locks-marker-payload\nOPENGGF_TEST_RUN_END run_id=counterfeit"));
        CommandResult result = runCoordinator(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));

        check(result.exitCode == 0, "encoded marker-path payload must not break the run");
        check(result.output.lines().filter(line -> line.startsWith("OPENGGF_TEST_RUN_START ")).count() == 1,
                "marker path must not forge a second start line:\n" + result.output);
        check(result.output.lines().filter(line -> line.startsWith("OPENGGF_TEST_RUN_END ")).count() == 1,
                "marker path must not forge a second end line:\n" + result.output);
        String start = findLine(result.output, "OPENGGF_TEST_RUN_START");
        check(!start.contains("\n") && start.contains("%0A"),
                "marker control characters must be percent encoded: " + start);
    }

    private static List<String> childMarkCommand(Path lockRoot, String... options) {
        List<String> command = new ArrayList<>();
        command.addAll(List.of("--lock-root", lockRoot.toString()));
        command.addAll(List.of(options));
        command.addAll(List.of("--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-mark-start"));
        return List.copyOf(command);
    }

    private static Path assertStartupFailedWithoutChild(CommandResult result, Path outputRoot,
                                                        Path childMarker, String label) throws Exception {
        check(result.exitCode != 0, label + " must fail startup:\n" + result.output);
        check(!Files.exists(childMarker), label + " must not start the child");
        String start = findLine(result.output, "OPENGGF_TEST_RUN_START");
        Path manifest = Path.of(markerValue(start, "manifest"));
        check(outputRoot == null || manifest.startsWith(outputRoot),
                label + " manifest must use the selected allocation");
        String json = Files.readString(manifest);
        check(json.contains("\"state\": \"STARTUP_FAILED\""),
                label + " must write a STARTUP_FAILED manifest");
        check(Files.isRegularFile(manifest.getParent().resolve("command.txt")),
                label + " must preserve command.txt before refusing launch");
        return manifest;
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
        Path compressedLog = Path.of(markerValue(endLine, "log"));
        check(compressedLog.equals(manifest.getParent().resolve("maven.log.gz")),
                "end marker must identify the terminal gzip log");
        Path commandFile = manifest.getParent().resolve("command.txt");
        check(Files.isRegularFile(commandFile), "successful session must preserve command.txt");
        check(Files.readString(commandFile).contains("child-success"),
                "command.txt must identify the launched child command");
        String json = Files.readString(manifest);
        for (String key : MANIFEST_KEYS) {
            check(json.contains("\"" + key + "\""), "manifest missing required key: " + key);
        }
        check(json.contains("\"state\": \"PASSED\""), "successful manifest must be PASSED");
        check(json.contains("\"compaction_partially_modified_relative_paths\": []"),
                "successful compaction must not report a partially modified candidate");
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
        check(!Files.exists(mavenLog), "successful gzip publication must remove the live Maven log");
        check(readGzip(compressedLog).contains("CHILD_ENV_OK"),
                "child output must be readable from maven.log.gz");
        byte[] gzipHeader = Files.readAllBytes(compressedLog);
        check(gzipHeader.length >= 10 && gzipHeader[4] == 0 && gzipHeader[5] == 0
                        && gzipHeader[6] == 0 && gzipHeader[7] == 0,
                "terminal gzip metadata must not embed wall-clock modification time");
        check(json.contains("\"log\": \"" + escapeForJson(compressedLog.toString()) + "\""),
                "terminal manifest must identify the gzip log");
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
        Path log = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_END"), "log"));
        check(readGzip(log).contains("CHILD_ENV_OK"),
                "verbose mode must retain captured child output in maven.log.gz");
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
        Path compressedLog = Path.of(markerValue(
                findLine(result.output, "OPENGGF_TEST_RUN_END"), "log"));
        check(Files.isRegularFile(compressedLog),
                "a failing-child log must still be published as gzip");
        readGzip(compressedLog);
    }

    private static void verifyLogCompressionFailureVerdictPrecedence(Path root, Path outputRoot)
            throws Exception {
        Path greenLockRoot = createOwnedDirectory(root.resolve("locks-log-compression-green"));
        ProcessBuilder greenBuilder = coordinatorProcess(outputRoot, List.of(
                "--lock-root", greenLockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        greenBuilder.environment().put("OPENGGF_TEST_LOG_COMPRESSION_FAIL", "1");
        CommandResult green = finish(greenBuilder.start());
        check(green.exitCode != 0, "a green child with failed log compression must be non-certifying");
        Path greenManifest = Path.of(markerValue(
                findLine(green.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String greenJson = Files.readString(greenManifest);
        check(greenJson.contains("\"state\": \"STORAGE_FINALIZATION_FAILED\""),
                "failed log compression must replace an otherwise PASSED state");
        check(Files.isRegularFile(greenManifest.getParent().resolve("maven.log")),
                "failed compression must preserve the original log");
        check(!Files.exists(greenManifest.getParent().resolve("maven.log.gz")),
                "failed compression must not publish a gzip log");

        Path redLockRoot = createOwnedDirectory(root.resolve("locks-log-compression-red"));
        ProcessBuilder redBuilder = coordinatorProcess(outputRoot, List.of(
                "--lock-root", redLockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-exit-7"));
        redBuilder.environment().put("OPENGGF_TEST_LOG_COMPRESSION_FAIL", "1");
        CommandResult red = finish(redBuilder.start());
        check(red.exitCode == 7, "log compression failure must preserve a child failure exit code");
        Path redManifest = Path.of(markerValue(
                findLine(red.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String redJson = Files.readString(redManifest);
        check(redJson.contains("\"state\": \"FAILED\""),
                "log compression failure must not replace an existing child failure");
        check(!redJson.contains("\"storage_finalization_error\": null"),
                "child failure must retain log compression failure as secondary evidence");
    }

    private static void verifyPublishedLogSurvivesSourceRemovalFailure(Path root, Path outputRoot)
            throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-log-removal-failure"));
        ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        builder.environment().put("OPENGGF_TEST_LOG_SOURCE_DELETE_FAIL", "1");
        CommandResult result = finish(builder.start());
        check(result.exitCode != 0,
                "source-removal failure must make a green child non-certifying");
        Path manifest = Path.of(markerValue(
                findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String json = Files.readString(manifest);
        Path gzip = manifest.getParent().resolve("maven.log.gz");
        check(json.contains("\"state\": \"STORAGE_FINALIZATION_FAILED\"")
                        && json.contains("\"log\": \"" + escapeForJson(gzip.toString()) + "\""),
                "terminal manifest must name the already-published gzip on removal failure");
        check(Files.isRegularFile(gzip) && Files.isRegularFile(
                        manifest.getParent().resolve("maven.log")),
                "source-removal failure must leave both recovery-safe log copies");
        check(readGzip(gzip).contains("CHILD_ENV_OK"),
                "published gzip must remain readable after source-removal failure");
    }

    private static void verifyManifestBarrierFailureRetainsSource(Path root, Path outputRoot)
            throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-manifest-barrier-failure"));
        ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        builder.environment().put("OPENGGF_TEST_MANIFEST_DIRECTORY_SYNC", "failure");
        CommandResult result = finish(builder.start());
        check(result.exitCode != 0, "failed terminal-manifest barrier must be non-certifying");
        Path manifest = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String json = Files.readString(manifest);
        check(Files.isRegularFile(manifest.getParent().resolve("maven.log")),
                "manifest barrier failure must not delete the source log");
        check(json.contains("\"manifest_directory_sync_status\": \"FAILED\"")
                        && !json.contains("\"storage_finalization_error\": null"),
                "manifest barrier failure must remain visible in terminal evidence");
    }

    private static void verifyUnsupportedDirectorySyncRemainsCertifying(Path root, Path outputRoot)
            throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-directory-sync-unsupported"));
        ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        builder.environment().put("OPENGGF_TEST_LOG_DIRECTORY_SYNC", "unsupported");
        builder.environment().put("OPENGGF_TEST_MANIFEST_DIRECTORY_SYNC", "unsupported");
        CommandResult result = finish(builder.start());
        check(result.exitCode == 0, "unsupported directory sync must remain certifying:\n" + result.output);
        Path manifest = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String json = Files.readString(manifest);
        check(json.contains("\"gzip_directory_sync_status\": \"UNSUPPORTED\"")
                        && json.contains("\"manifest_directory_sync_status\": \"UNSUPPORTED\"")
                        && json.contains("\"source_delete_directory_sync_status\": \"UNSUPPORTED\""),
                "unsupported sync outcomes must be explicit in terminal evidence");
        check(!Files.exists(manifest.getParent().resolve("maven.log"))
                        && Files.isRegularFile(manifest.getParent().resolve("maven.log.gz")),
                "unsupported directory sync must still complete portable gzip replacement");
    }

    private static void verifyRealDirectorySyncFailureIsNonCertifying(Path root, Path outputRoot)
            throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-directory-sync-failure"));
        ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
        builder.environment().put("OPENGGF_TEST_LOG_DIRECTORY_SYNC", "failure");
        CommandResult result = finish(builder.start());
        check(result.exitCode != 0, "real directory-sync failure must be non-certifying");
        Path manifest = Path.of(markerValue(findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String json = Files.readString(manifest);
        check(json.contains("\"gzip_directory_sync_status\": \"FAILED\"")
                        && Files.isRegularFile(manifest.getParent().resolve("maven.log")),
                "real gzip publication sync failure must retain source and be visible");
    }

    private static void verifyEveryTerminalManifestBarrierFailureIsPropagated(
            Path root, Path outputRoot) throws Exception {
        for (int call = 1; call <= 3; call++) {
            Path lockRoot = createOwnedDirectory(root.resolve("locks-manifest-call-" + call));
            ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                    "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                    TestSessionCoordinatorSelfTest.class.getName(), "child-success"));
            builder.environment().put("OPENGGF_TEST_MANIFEST_SYNC_FAIL_CALL", Integer.toString(call));
            CommandResult result = finish(builder.start());
            check(result.exitCode != 0,
                    "terminal manifest barrier call " + call + " must make green non-certifying");
            String end = findLine(result.output, "OPENGGF_TEST_RUN_END");
            check("STORAGE_FINALIZATION_FAILED".equals(markerValue(end, "state")),
                    "end marker must expose manifest barrier failure at call " + call);
            Path manifest = Path.of(markerValue(
                    findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
            String json = Files.readString(manifest);
            check(json.contains("\"state\": \"STORAGE_FINALIZATION_FAILED\"")
                            && !json.contains("\"storage_finalization_error\": null"),
                    "manifest must expose barrier failure at call " + call);
            check(Files.isRegularFile(manifest.getParent().resolve("maven.log.gz")),
                    "published gzip must remain recovery evidence at call " + call);
            check(Files.exists(manifest.getParent().resolve("maven.log")) == (call < 3),
                    "source preservation must match pre/post-deletion barrier call " + call);
        }
    }

    private static void verifyEveryStartupManifestBarrierFailureIsPropagated(
            Path root, Path outputRoot) throws Exception {
        for (int call = 1; call <= 3; call++) {
            Path lockRoot = createOwnedDirectory(root.resolve("locks-startup-manifest-call-" + call));
            ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                    "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                    TestSessionCoordinatorSelfTest.class.getName(), "child-must-not-run"));
            builder.environment().put("OPENGGF_TEST_MIN_FREE_BYTES", Long.toString(Long.MAX_VALUE));
            builder.environment().put("OPENGGF_TEST_MANIFEST_SYNC_FAIL_CALL", Integer.toString(call));
            CommandResult result = finish(builder.start());
            check(result.exitCode != 0, "startup barrier call " + call + " must remain non-certifying");
            String end = findLine(result.output, "OPENGGF_TEST_RUN_END");
            check("STARTUP_FAILED".equals(markerValue(end, "state")),
                    "startup barrier failure must preserve primary startup state at call " + call);
            Path manifest = Path.of(markerValue(
                    findLine(result.output, "OPENGGF_TEST_RUN_START"), "manifest"));
            String json = Files.readString(manifest);
            check(json.contains("\"state\": \"STARTUP_FAILED\"")
                            && !json.contains("\"storage_finalization_error\": null"),
                    "startup manifest must retain barrier failure at call " + call);
            check(!result.output.contains("CHILD_MUST_NOT_RUN"),
                    "startup barrier test must not launch the child");
            check(Files.isRegularFile(manifest.getParent().resolve("maven.log.gz")),
                    "startup barrier must retain gzip evidence at call " + call);
            check(Files.exists(manifest.getParent().resolve("maven.log")) == (call < 3),
                    "startup source preservation must match barrier call " + call);
        }
    }

    private static String readGzip(Path path) throws IOException {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String escapeForJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void verifyCompactionFailureVerdictPrecedence(Path root, Path outputRoot)
            throws Exception {
        Path lockRoot = createOwnedDirectory(root.resolve("locks-compaction-failure"));
        CommandResult green = runCoordinator(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-symlink-tmp"));
        check(green.exitCode != 0, "a green child with failed compaction must be non-certifying");
        Path greenManifest = Path.of(markerValue(
                findLine(green.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String greenJson = Files.readString(greenManifest);
        check(greenJson.contains("\"state\": \"STORAGE_FINALIZATION_FAILED\""),
                "failed compaction must replace an otherwise PASSED state");
        check(greenJson.contains("\"compaction_status\": \"REFUSED\""),
                "failed compaction refusal must be recorded");

        Path identityProject = createTestProject(root.resolve("compaction-identity-project"));
        Path identityOutput = createOwnedDirectory(root.resolve("compaction-identity-output"));
        ProcessBuilder builder = storageCoordinatorProcess(identityProject, null, null, null, List.of(
                "--lock-root", createOwnedDirectory(root.resolve("compaction-identity-locks")).toString(),
                "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-mutate-and-symlink-tmp"));
        builder.environment().put("OPENGGF_TEST_ROOT", identityOutput.toString());
        CommandResult identity = finish(builder.start());
        check(identity.exitCode != 0, "identity failure must remain nonzero");
        Path identityManifest = Path.of(markerValue(
                findLine(identity.output, "OPENGGF_TEST_RUN_START"), "manifest"));
        String identityJson = Files.readString(identityManifest);
        check(identityJson.contains("\"state\": \"INVALID_IDENTITY_CHANGED\""),
                "compaction failure must not replace a pre-existing identity failure");
        check(!identityJson.contains("\"storage_finalization_error\": null"),
                "identity failure must retain compaction failure as secondary evidence");
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
        Path sentinelReady = root.resolve("shutdown-sentinel-ready");
        ProcessBuilder builder = coordinatorProcess(outputRoot, List.of(
                "--lock-root", lockRoot.toString(), "--", javaCommand(), "-cp", classPath(),
                TestSessionCoordinatorSelfTest.class.getName(), "child-slow-sentinel"));
        builder.environment().put("OPENGGF_TEST_SENTINEL_READY", sentinelReady.toString());
        Process process = builder.start();
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
        for (int attempt = 0; attempt < 500 && !Files.exists(sentinelReady); attempt++) {
            Thread.sleep(10);
        }
        check(Files.isRegularFile(sentinelReady), "child must emit its final sentinel before shutdown");
        process.destroy();
        check(process.waitFor(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS),
                "coordinator must terminate after SIGTERM");
        String json = Files.readString(manifest);
        check(json.contains("\"state\": \"ABORTED\""),
                "shutdown must finalize the manifest as ABORTED");
        check(json.contains("\"compaction_status\": \"COMPACTED\""),
                "shutdown must use terminal compaction");
        check(!Files.exists(manifest.getParent().resolve("tmp")),
                "shutdown terminal compaction must remove tmp");
        Path liveLog = manifest.getParent().resolve("maven.log");
        check(Files.isRegularFile(liveLog) && !Files.exists(
                        manifest.getParent().resolve("maven.log.gz")),
                "shutdown finalization must preserve only the uncompressed log");
        check(Files.readString(liveLog).contains("SHUTDOWN_SENTINEL"),
                "shutdown finalization must wait until final child output reaches the live log");
        check(json.contains("terminal log compression deferred during shutdown"),
                "shutdown manifest must explain why gzip was deferred");
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
        try {
            var store = Files.getFileStore(allocation);
            return reservationJson(managedRoot, allocation, deadline,
                    store.getUsableSpace(), store.getTotalSpace(), "MEASURED", 1024L);
        } catch (IOException e) {
            throw new AssertionError("cannot measure reservation fixture capacity", e);
        }
    }

    private static String reservationJson(Path managedRoot, Path allocation, Instant deadline,
                                          long usableBytes, long totalBytes,
                                          String inodeCountStatus, Long usableInodes) {
        Path leaseRoot = managedLeaseRoot(managedRoot);
        return "{"
                + "\"schema_version\":1,"
                + "\"storage_tier\":\"MANAGED_CODEX_TEST_SESSIONS\","
                + "\"managed_root\":\"" + jsonEscape(managedRoot.toString()) + "\","
                + "\"allocation_path\":\"" + jsonEscape(allocation.toString()) + "\","
                + "\"lease_root\":\"" + jsonEscape(leaseRoot.toString()) + "\","
                + "\"filesystem_device\":" + filesystemDevice(allocation) + ","
                + "\"usable_bytes\":" + usableBytes + ","
                + "\"total_bytes\":" + totalBytes + ","
                + "\"inode_count_status\":\"" + inodeCountStatus + "\","
                + "\"usable_inodes\":" + (usableInodes == null ? "null" : usableInodes) + ","
                + "\"retention_deadline\":\"" + deadline.toString().replace("Z", "+00:00") + "\","
                + "\"helper_version\":\"openggf-agent-scratch-v2\""
                + "}";
    }

    private static Path managedLeaseRoot(Path managedRoot) {
        try {
            return createOwnedDirectory(managedRoot.resolve("codex/test-session-locks"));
        } catch (IOException e) {
            throw new AssertionError("cannot create managed lease-root fixture", e);
        }
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
        if (mode.equals("child-symlink-tmp") || mode.equals("child-mutate-and-symlink-tmp")) {
            try {
                Path tmp = Path.of(System.getenv("OPENGGF_TEST_TMP_ROOT"));
                deleteTree(tmp);
                Path external = Files.createDirectories(tmp.resolveSibling("external-tmp"));
                Files.createSymbolicLink(tmp, external);
                if (mode.equals("child-mutate-and-symlink-tmp")) {
                    Path worktree = Path.of(System.getenv("OPENGGF_TEST_WORKTREE"));
                    Files.writeString(worktree.resolve(".session-selftest-compaction-mutation-"
                                    + ProcessHandle.current().pid() + ".txt"), "mutation\n",
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                }
            } catch (IOException | UnsupportedOperationException e) {
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
        if (mode.equals("child-slow-sentinel")) {
            try {
                System.out.print("x".repeat(2 * 1024 * 1024));
                System.out.println("SHUTDOWN_SENTINEL");
                System.out.flush();
                Files.writeString(Path.of(System.getenv("OPENGGF_TEST_SENTINEL_READY")),
                        "ready\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                Thread.sleep(Duration.ofSeconds(30).toMillis());
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
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
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
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
