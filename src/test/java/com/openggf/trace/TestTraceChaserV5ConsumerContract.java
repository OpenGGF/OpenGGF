package com.openggf.trace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageTraceData;
import com.openggf.game.sonic3k.specialstage.S3kSpecialStageTraceData;
import com.openggf.trace.SpecialStageTraceData;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceRunManifest;
import com.openggf.tests.TraceChaserTestSupport;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** Runs TraceChaser's immutable v5 pack through OpenGGF's real Java consumers. */
class TestTraceChaserV5ConsumerContract {
    private static final Path PACK = Path.of("src/test/resources/tracechaser/v5");
    private static final Path PROVENANCE = Path.of("src/test/resources/tracechaser/provenance.json");
    private static final ObjectMapper JSON = new ObjectMapper();
    // These are small, reviewed contracts, not an unbounded repository export.
    private static final int MAX_COPY_FILES = 512;
    private static final int MAX_GIT_BYTES = 1024 * 1024;
    private static final int MAX_COPY_BYTES = 4 * MAX_GIT_BYTES;
    private static final Map<String, String> EXPECTED_COPIES = Map.of(
            "contracts/v5", "src/test/resources/tracechaser/v5",
            "bizhawk-headless/fixtures/gpgx-audio-capability-v1.json",
                    "src/test/resources/tracechaser/gpgx-audio-capability-v1.json",
            "contracts/audio/normalization-contract-v1.json",
                    "src/test/resources/audio/contracts/normalization-contract-v1.json");

    @TempDir Path temporaryDirectory;

    @TestFactory
    Stream<DynamicTest> everyPublishedCaseMatchesJavaConsumerSemantics() throws IOException {
        JsonNode manifest = JSON.readTree(PACK.resolve("manifest.json").toFile());
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode testCase : manifest.path("cases")) {
            tests.add(DynamicTest.dynamicTest(testCase.path("id").asText(),
                    () -> executeCase(testCase)));
        }
        assertEquals(31, tests.size());
        return tests.stream();
    }

    @Test
    void consumerCopyRecordsExactImmutableSource() throws Exception {
        validateProvenance(PROVENANCE, Path.of("."));
    }

    @TestFactory
    Stream<DynamicTest> provenanceRejectsTamperedMissingAndExtraCopies() {
        return Stream.of(
                DynamicTest.dynamicTest("tampered declared copy", () -> {
                    Path root = materializeProvenance("tampered");
                    Files.writeString(root.resolve(
                            "src/test/resources/tracechaser/gpgx-audio-capability-v1.json"), "tampered");
                    assertThrows(AssertionError.class,
                            () -> validateProvenance(root.resolve("provenance.json"), root));
                }),
                DynamicTest.dynamicTest("missing declared copy", () -> {
                    Path root = materializeProvenance("missing");
                    Files.delete(root.resolve(
                            "src/test/resources/audio/contracts/normalization-contract-v1.json"));
                    assertThrows(AssertionError.class,
                            () -> validateProvenance(root.resolve("provenance.json"), root));
                }),
                DynamicTest.dynamicTest("extra provenance copy", () -> {
                    Path root = materializeProvenance("extra");
                    Path provenancePath = root.resolve("provenance.json");
                    var provenance = (com.fasterxml.jackson.databind.node.ObjectNode)
                            JSON.readTree(provenancePath.toFile());
                    var extra = JSON.createObjectNode();
                    extra.put("source", "unexpected/source.json");
                    extra.put("destination", "src/test/resources/tracechaser/unexpected.json");
                    extra.put("sha256", "00".repeat(32));
                    ((com.fasterxml.jackson.databind.node.ArrayNode) provenance.path("copies")).add(extra);
                    JSON.writeValue(provenancePath.toFile(), provenance);
                    assertThrows(AssertionError.class,
                            () -> validateProvenance(provenancePath, root));
                }));
    }

    @Test
    @Tag("tracechaser-integration")
    void initializedCheckoutPreservesConsumerCopiesFromDeclaredImmutableOrigin() throws Exception {
        Path checkout = TraceChaserTestSupport.requirePinnedCheckout();
        validateProvenance(PROVENANCE, Path.of("."));
        JsonNode provenance = JSON.readTree(PROVENANCE.toFile());
        assertOriginCopiesMatch(checkout, provenance, Path.of("."));
    }

    @Test
    void immutableOriginComparisonAllowsDifferentProducerHeadAndWorkingFiles() throws Exception {
        OriginFixture fixture = originFixture("different-head");
        for (String source : EXPECTED_COPIES.keySet()) {
            Path path = fixture.checkout().resolve(source);
            if (Files.isDirectory(path)) path = path.resolve("nested/member space.bin");
            tamperEqualLength(path);
        }
        Files.writeString(fixture.checkout().resolve("contracts/v5/later-file.json"), "later");
        String later = commitFixture(fixture.checkout());
        assertTrue(!later.equals(fixture.provenance().path("origin_commit").asText()));
        Files.writeString(fixture.checkout().resolve("contracts/v5/manifest.json"), "uncommitted");

        assertOriginCopiesMatch(fixture.checkout(), fixture.provenance(), fixture.consumer());
        var current = fixture.provenance().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) current).put("origin_commit", later);
        assertThrows(AssertionError.class,
                () -> assertOriginCopiesMatch(fixture.checkout(), current, fixture.consumer()));
    }

    @TestFactory
    Stream<DynamicTest> immutableOriginComparisonRejectsChangedBytesAndTreeMembership() {
        return Stream.of("source", "consumer").flatMap(side ->
                Stream.of("equal-length bytes", "missing member", "extra member", "missing standalone copy")
                        .map(change -> DynamicTest.dynamicTest(side + " " + change, () -> {
                            OriginFixture fixture = originFixture(side + "-" + change.replace(' ', '-'));
                            Path root = side.equals("source") ? fixture.checkout() : fixture.consumer();
                            Path tree = root.resolve(side.equals("source") ? "contracts/v5"
                                    : EXPECTED_COPIES.get("contracts/v5"));
                            Path member = tree.resolve("nested/member space.bin");
                            switch (change) {
                                case "equal-length bytes" -> tamperEqualLength(member);
                                case "missing member" -> Files.delete(member);
                                case "extra member" -> Files.writeString(tree.resolve("extra.json"), "extra");
                                case "missing standalone copy" -> Files.delete(root.resolve(side.equals("source")
                                        ? "contracts/audio/normalization-contract-v1.json"
                                        : EXPECTED_COPIES.get("contracts/audio/normalization-contract-v1.json")));
                                default -> throw new AssertionError(change);
                            }
                            if (side.equals("source")) {
                                ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.provenance())
                                        .put("origin_commit", commitFixture(fixture.checkout()));
                            }
                            AssertionError failure = assertThrows(AssertionError.class,
                                    () -> assertOriginCopiesMatch(fixture.checkout(), fixture.provenance(),
                                            fixture.consumer()));
                            assertTrue(failure.getMessage().contains(change.equals("missing standalone copy")
                                    ? "normalization-contract-v1.json" : "src/test/resources/tracechaser/v5"),
                                    failure::toString);
                        })));
    }

    @Test
    void immutableOriginComparisonFailsClearlyWhenOriginIsUnavailable() throws Exception {
        OriginFixture fixture = originFixture("unavailable");
        String unavailable = "00".repeat(20);
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.provenance()).put("origin_commit", unavailable);
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertOriginCopiesMatch(fixture.checkout(), fixture.provenance(), fixture.consumer()));
        assertTrue(failure.getMessage().contains("declared immutable origin " + unavailable + " is unavailable"));
    }

    @Test
    void gitEnvironmentCannotRedirectFixtureWritesIntoTheCallerRepository() throws Exception {
        OriginFixture caller = originFixture("foreign-caller");
        Path callerRoot = caller.checkout();
        Files.writeString(callerRoot.resolve("caller-only.txt"), "staged caller content");
        git(callerRoot, "add", "caller-only.txt");
        Files.writeString(callerRoot.resolve("caller-only.txt"), "unstaged caller content");
        Map<String, String> before = snapshotFiles(callerRoot);
        Path target = Files.createDirectories(temporaryDirectory.resolve("independent-target"));
        Map<String, String> poison = Map.of(
                "GIT_DIR", callerRoot.resolve(".git").toString(),
                "GIT_WORK_TREE", callerRoot.toString(),
                "GIT_INDEX_FILE", callerRoot.resolve(".git/index").toString(),
                "GIT_COMMON_DIR", callerRoot.resolve(".git").toString(),
                "GIT_CONFIG_COUNT", "1", "GIT_CONFIG_KEY_0", "core.bare", "GIT_CONFIG_VALUE_0", "true");
        try {
            git(target, poison, "init", "--quiet");
            Files.write(target.resolve("binary.dat"), new byte[] {0, -1, 1, 42});
            git(target, poison, "add", "--all");
            git(target, poison, "-c", "user.name=Contract fixture", "-c", "user.email=fixture@example.invalid",
                    "-c", "commit.gpgSign=false", "commit", "--quiet", "-m", "Independent fixture");
            assertTrue(Files.isDirectory(target.resolve(".git")), "target must own its repository");
            assertArrayEquals(new byte[] {0, -1, 1, 42},
                    git(target, poison, "cat-file", "blob", "HEAD:binary.dat"));
        } finally {
            assertEquals(before, snapshotFiles(callerRoot), "foreign HEAD, index, config and files must stay exact");
        }
    }

    @TestFactory
    Stream<DynamicTest> consumerCopiesRejectAncestorSymlinksBeforeReadingTheirTargets() {
        return Stream.of("tracechaser", "audio").map(directory -> DynamicTest.dynamicTest(directory, () -> {
            OriginFixture fixture = originFixture("ancestor-" + directory);
            Path ancestor = fixture.consumer().resolve("src/test/resources/" + directory);
            Path outside = temporaryDirectory.resolve("outside-" + directory);
            Files.move(ancestor, outside);
            try {
                Files.createSymbolicLink(ancestor, outside.toAbsolutePath());
            } catch (UnsupportedOperationException | AccessDeniedException exception) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false,
                        "temporary directory symlinks are unavailable on this platform: " + exception);
            } catch (FileSystemException exception) {
                String reason = String.valueOf(exception.getReason()).toLowerCase(Locale.ROOT);
                org.junit.jupiter.api.Assumptions.assumeTrue(!reason.contains("not supported")
                                && !reason.contains("privilege") && !reason.contains("operation not permitted"),
                        "temporary directory symlinks are unavailable on this platform: " + exception);
                throw exception;
            }
            AssertionError originFailure = assertThrows(AssertionError.class,
                    () -> assertOriginCopiesMatch(fixture.checkout(), fixture.provenance(), fixture.consumer()));
            assertTrue(originFailure.getMessage().contains("symbolic-link"), originFailure::toString);
            AssertionError provenanceFailure = assertThrows(AssertionError.class,
                    () -> validateProvenance(fixture.consumer().resolve("provenance.json"), fixture.consumer()));
            assertTrue(provenanceFailure.getMessage().contains("symbolic-link"), provenanceFailure::toString);
        }));
    }

    @TestFactory
    Stream<DynamicTest> provenanceRejectsSymlinksBeforeParsingProductionRelativeJson() {
        return Stream.of("ancestor", "leaf").map(kind -> DynamicTest.dynamicTest(kind, () -> {
            Path root = materializeProvenance("preparse-" + kind);
            Path provenance = root.resolve(PROVENANCE);
            Files.copy(PROVENANCE, provenance);
            validateProvenance(provenance, root); // The actual production-relative regular path remains valid.
            Path link;
            Path target = temporaryDirectory.resolve("malformed-outside-provenance-" + kind);
            if (kind.equals("ancestor")) {
                link = provenance.getParent();
                Files.move(link, target);
                Files.writeString(target.resolve("provenance.json"), "{ malformed outside provenance");
            } else {
                link = provenance;
                Files.writeString(target, "{ malformed outside provenance");
                Files.delete(provenance);
            }
            try {
                Files.createSymbolicLink(link, target.toAbsolutePath());
            } catch (UnsupportedOperationException | AccessDeniedException exception) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false,
                        "temporary provenance symlinks are unavailable on this platform: " + exception);
            } catch (FileSystemException exception) {
                String reason = String.valueOf(exception.getReason()).toLowerCase(Locale.ROOT);
                org.junit.jupiter.api.Assumptions.assumeTrue(!reason.contains("not supported")
                                && !reason.contains("privilege") && !reason.contains("operation not permitted"),
                        "temporary provenance symlinks are unavailable on this platform: " + exception);
                throw exception;
            }
            AssertionError failure = assertThrows(AssertionError.class, () -> validateProvenance(provenance, root));
            assertTrue(failure.getMessage().contains("symbolic-link"), failure::toString);
        }));
    }

    @Test
    void gitTimeoutTerminatesAChildHoldingItsOutputPipe() throws Exception {
        Path marker = temporaryDirectory.resolve("retained-pipe-child.pid");
        Process process = new ProcessBuilder(javaProcessCommand("parent", marker)).start();
        List<ProcessHandle> owned = new ArrayList<>();
        owned.add(process.toHandle());
        FutureTask<byte[]> request = new FutureTask<>(() -> readGitProcess(process, 1200));
        Thread invocation = Thread.ofPlatform().daemon().unstarted(request);
        Throwable primary = null;
        try {
            long readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ((!Files.exists(marker) || Files.size(marker) == 0) && System.nanoTime() < readyDeadline)
                Thread.sleep(10);
            assertTrue(Files.exists(marker) && Files.size(marker) > 0, "controlled child did not become ready");
            ProcessHandle child = ProcessHandle.of(Long.parseLong(Files.readString(marker))).orElseThrow();
            owned.add(child);
            assertTrue(child.isAlive(), "child must hold the inherited pipe before the timeout");
            invocation.start();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> request.get(4, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IOException
                    && failure.getCause().getMessage().contains("timed out"), failure::toString);
            assertEquals(0, failure.getCause().getSuppressed().length, "timeout cleanup must finish cleanly");
            assertTrue(owned.stream().noneMatch(ProcessHandle::isAlive),
                    "timeout must finish terminating both owned processes before returning");
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            // Independent bounded safety cleanup also makes the deliberately red predecessor safe.
            process.descendants().forEach(handle -> { owned.add(handle); handle.destroyForcibly(); });
            for (ProcessHandle handle : owned.reversed()) if (handle.isAlive()) handle.destroyForcibly();
            request.cancel(true);
            invocation.join(1000);
            long cleanupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (owned.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < cleanupDeadline)
                Thread.sleep(10);
            if (owned.stream().anyMatch(ProcessHandle::isAlive)) {
                AssertionError cleanup = new AssertionError("controlled process cleanup incomplete: "
                        + owned.stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList());
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    public static final class RetainedPipeProcess {
        public static void main(String[] arguments) throws Exception {
            if (arguments[0].equals("child")) {
                Thread.sleep(60_000);
            } else {
                Process child = new ProcessBuilder(javaProcessCommand("child", Path.of(arguments[1])))
                        .inheritIO().start();
                Files.writeString(Path.of(arguments[1]), Long.toString(child.pid()));
                child.waitFor();
            }
        }
    }

    private static List<String> javaProcessCommand(String mode, Path marker) {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", System.getProperty("java.class.path"), RetainedPipeProcess.class.getName(),
                mode, marker.toAbsolutePath().toString());
    }

    private static Map<String, String> snapshotFiles(Path root) throws Exception {
        Map<String, String> snapshot = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList())
                snapshot.put(root.relativize(path).toString(), sha256(path));
        }
        return snapshot;
    }

    private record OriginFixture(Path checkout, Path consumer, JsonNode provenance) { }

    private OriginFixture originFixture(String name) throws Exception {
        Path consumer = materializeProvenance(name);
        Path tree = consumer.resolve(EXPECTED_COPIES.get("contracts/v5"));
        Files.createDirectories(tree.resolve("nested"));
        Files.write(tree.resolve("nested/member space.bin"), new byte[] {0, 1, -1, 42});
        Path checkout = Files.createDirectories(temporaryDirectory.resolve("git-" + name));
        git(checkout, "init", "--quiet");
        for (var copy : EXPECTED_COPIES.entrySet()) {
            Path from = consumer.resolve(copy.getValue());
            Path to = checkout.resolve(copy.getKey());
            try (var paths = Files.walk(from)) {
                for (Path path : paths.toList()) {
                    Path target = to.resolve(from.relativize(path));
                    if (Files.isDirectory(path)) Files.createDirectories(target);
                    else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target);
                    }
                }
            }
        }
        JsonNode provenance = JSON.readTree(PROVENANCE.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) provenance).put("origin_commit", commitFixture(checkout));
        return new OriginFixture(checkout, consumer, provenance);
    }

    private static String commitFixture(Path checkout) throws Exception {
        git(checkout, "-c", "core.autocrlf=false", "add", "--all");
        git(checkout, "-c", "user.name=Contract fixture", "-c", "user.email=fixture@example.invalid",
                "-c", "commit.gpgSign=false", "commit", "--quiet", "-m", "Immutable contract fixture");
        return new String(git(checkout, "rev-parse", "HEAD"), StandardCharsets.UTF_8).strip();
    }

    private static void tamperEqualLength(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length - 1] ^= 1;
        Files.write(path, bytes);
    }

    private void validateProvenance(Path provenancePath, Path root) throws Exception {
        Path consumerRoot = root.toAbsolutePath().normalize();
        Path safeProvenance = safeConsumerDestination(consumerRoot,
                provenancePath.toAbsolutePath().normalize().toString());
        assertTrue(Files.isRegularFile(safeProvenance, LinkOption.NOFOLLOW_LINKS),
                "missing regular consumer provenance: " + safeProvenance);
        JsonNode provenance = JSON.readTree(safeProvenance.toFile());
        assertEquals("9e51ff79e7a542f3c50d96618a7e24e6fc72397e",
                provenance.path("origin_commit").asText());
        assertEquals("openggf-tracechaser-consumer-copy-v1", provenance.path("format").asText());
        Map<String, String> declared = new LinkedHashMap<>();
        for (JsonNode copy : provenance.path("copies")) {
            String source = copy.path("source").asText();
            String destination = copy.path("destination").asText();
            assertEquals(null, declared.put(source, destination), "duplicate provenance source " + source);
            Path destinationPath = safeConsumerDestination(consumerRoot, destination);
            Path hashed = copy.has("manifest_sha256")
                    ? destinationPath.resolve("manifest.json") : destinationPath;
            assertTrue(Files.isRegularFile(hashed, LinkOption.NOFOLLOW_LINKS),
                    "missing declared consumer copy: " + destination);
            String field = copy.has("manifest_sha256") ? "manifest_sha256" : "sha256";
            assertEquals(copy.path(field).asText(), sha256(hashed), destination);
        }
        assertEquals(EXPECTED_COPIES, declared, "provenance must declare exactly the reviewed copies");
    }

    private Path materializeProvenance(String name) throws IOException {
        Path root = temporaryDirectory.resolve("provenance-" + name);
        Files.createDirectories(root);
        Files.copy(PROVENANCE, root.resolve("provenance.json"));
        for (String destination : EXPECTED_COPIES.values()) {
            Path source = Path.of(destination);
            Path target = root.resolve(destination);
            if (Files.isDirectory(source)) {
                Files.createDirectories(target);
                Files.copy(source.resolve("manifest.json"), target.resolve("manifest.json"));
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        }
        return root;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }

    private static Path safeConsumerDestination(Path consumerRoot, String destination) {
        Path root = consumerRoot.toAbsolutePath().normalize();
        Path path = root.resolve(destination).normalize();
        assertTrue(path.startsWith(root), "copy destination escapes consumer root: " + destination);
        assertTrue(!Files.isSymbolicLink(root), "symbolic-link consumer root: " + root);
        assertTrue(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS), "missing consumer root: " + root);
        Path current = root;
        for (Path part : root.relativize(path)) {
            current = current.resolve(part);
            assertTrue(!Files.isSymbolicLink(current), "symbolic-link consumer component: " + current);
            if (!current.equals(path)) assertTrue(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS),
                    "unsafe consumer ancestor: " + current);
        }
        return path;
    }

    private static void assertOriginCopiesMatch(Path checkout, JsonNode provenance, Path consumerRoot)
            throws Exception {
        String origin = provenance.path("origin_commit").asText();
        assertTrue(origin.matches("[0-9a-f]{40}"), "origin must be an immutable full commit ID");
        try {
            git(checkout, "cat-file", "-e", origin + "^{commit}");
        } catch (IOException exception) {
            throw new AssertionError("declared immutable origin " + origin + " is unavailable in " + checkout
                    + "; no HEAD fallback or network fetch", exception);
        }
        for (JsonNode copy : provenance.path("copies")) {
            String source = copy.path("source").asText();
            Path destination = safeConsumerDestination(consumerRoot, copy.path("destination").asText());
            String type;
            try {
                type = new String(git(checkout, "cat-file", "-t", origin + ":" + source),
                        StandardCharsets.UTF_8).strip();
            } catch (IOException exception) {
                throw new AssertionError("missing declared origin source: " + source + " -> " + destination,
                        exception);
            }
            assertTrue(type.equals("tree") || type.equals("blob"), "unsupported origin type: " + type);
            Map<String, String> blobs = new LinkedHashMap<>();
            // NUL delimiters preserve whitespace in paths; read committed objects, never checkout files.
            String listing = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(
                    git(checkout, "ls-tree", "-r", "-z", "--full-tree", origin, "--", source))).toString();
            for (String entry : listing.split("\0")) {
                int tab = entry.indexOf('\t');
                assertTrue(tab > 0, "malformed origin tree entry");
                String[] header = entry.substring(0, tab).split(" ");
                assertTrue(header.length == 3 && header[1].equals("blob")
                        && (header[0].equals("100644") || header[0].equals("100755")),
                        "origin copy must contain only regular blobs: " + entry);
                String path = entry.substring(tab + 1);
                assertTrue(type.equals("tree") ? path.startsWith(source + "/") : path.equals(source));
                String relative = type.equals("tree") ? path.substring(source.length() + 1) : "";
                assertEquals(null, blobs.put(relative, header[2]), "duplicate origin path: " + path);
                assertTrue(blobs.size() <= MAX_COPY_FILES, "origin copy exceeds file bound");
            }
            assertTrue(!blobs.isEmpty(), "missing declared origin source: " + source);
            List<String> destinationFiles;
            if (type.equals("tree")) {
                assertTrue(Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS), destination.toString());
                try (var paths = Files.walk(destination)) {
                    List<Path> members = paths.limit(2L * MAX_COPY_FILES + 1).toList();
                    assertTrue(members.size() <= 2 * MAX_COPY_FILES, "consumer copy exceeds path bound");
                    for (Path member : members) {
                        assertTrue(Files.isDirectory(member, LinkOption.NOFOLLOW_LINKS)
                                || Files.isRegularFile(member, LinkOption.NOFOLLOW_LINKS),
                                "unsafe consumer copy: " + member);
                    }
                    destinationFiles = members.stream().filter(Files::isRegularFile)
                            .map(destination::relativize).map(path -> path.toString().replace('\\', '/'))
                            .sorted().toList();
                }
            } else {
                assertTrue(Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS), destination.toString());
                destinationFiles = List.of("");
            }
            assertEquals(blobs.keySet().stream().sorted().toList(), destinationFiles,
                    "origin/consumer file membership: " + destination);
            int totalBytes = 0;
            for (var blob : blobs.entrySet()) {
                byte[] expected = git(checkout, "cat-file", "blob", blob.getValue());
                totalBytes += expected.length;
                assertTrue(totalBytes <= MAX_COPY_BYTES, "origin copy exceeds byte bound");
                Path file = blob.getKey().isEmpty() ? destination : destination.resolve(blob.getKey());
                byte[] actual;
                try (var input = Files.newInputStream(file)) {
                    actual = input.readNBytes(MAX_GIT_BYTES + 1);
                }
                assertTrue(actual.length <= MAX_GIT_BYTES, "consumer file exceeds byte bound: " + file);
                assertArrayEquals(expected, actual, "immutable origin " + origin + ": " + file);
            }
        }
    }

    private static byte[] git(Path checkout, String... arguments) throws Exception {
        return git(checkout, Map.of(), arguments);
    }

    private static byte[] git(Path checkout, Map<String, String> environment, String... arguments) throws Exception {
        Path resolvedCheckout = checkout.toRealPath();
        // These commands belong only to test fixtures and read-only origin inspection.
        // Actual project commits use the installed hooks normally, outside this helper.
        var command = new ArrayList<>(List.of("git", "--no-pager", "--no-replace-objects", "--literal-pathspecs",
                "-c", "core.hooksPath=", "-c", "core.fsmonitor=false", "-c", "init.templateDir=",
                "-c", "gc.auto=0", "-c", "maintenance.auto=false", "-C", resolvedCheckout.toString()));
        command.addAll(List.of(arguments));
        var builder = new ProcessBuilder(command).directory(resolvedCheckout.toFile()).redirectErrorStream(true);
        builder.environment().putAll(environment);
        // -C alone does not override these selectors, including hook-exported index/config variables.
        builder.environment().keySet().removeIf(key -> key.toUpperCase(Locale.ROOT).startsWith("GIT_"));
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        builder.environment().put("GIT_CONFIG_GLOBAL",
                System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
        builder.environment().put("GIT_NO_LAZY_FETCH", "1");
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        return readGitProcess(builder.start(), 10_000);
    }

    private static byte[] readGitProcess(Process process, long timeoutMillis) throws Exception {
        ExecutorService reader = Executors.newVirtualThreadPerTaskExecutor();
        Future<byte[]> output = reader.submit(() -> process.getInputStream().readNBytes(MAX_GIT_BYTES + 1));
        Map<Long, ProcessHandle> descendants = new LinkedHashMap<>();
        Throwable primary = null;
        try {
            process.getOutputStream().close();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (true) {
                process.descendants().forEach(handle -> descendants.putIfAbsent(handle.pid(), handle));
                if (output.isDone()) {
                    byte[] bytes = output.get();
                    if (bytes.length > MAX_GIT_BYTES) throw new IOException("local Git output exceeds byte bound");
                    if (!process.isAlive()) {
                        if (process.exitValue() != 0) throw new IOException("local Git command failed: "
                                + new String(bytes, StandardCharsets.UTF_8));
                        return bytes;
                    }
                }
                if (System.nanoTime() >= deadline) throw new IOException("local Git command timed out");
                Thread.sleep(10);
            }
        } catch (Exception | Error failure) {
            primary = failure;
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw failure;
        } finally {
            try {
                finishGitProcess(process, descendants, output, reader);
            } catch (Exception cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    private static void finishGitProcess(Process process, Map<Long, ProcessHandle> descendants,
            Future<byte[]> output, ExecutorService reader) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        boolean interrupted = Thread.interrupted();
        process.descendants().forEach(handle -> descendants.putIfAbsent(handle.pid(), handle));
        for (ProcessHandle handle : new ArrayList<>(descendants.values()).reversed()) handle.destroyForcibly();
        try {
            // Give the direct parent a bounded opportunity to reap its terminated children.
            try { process.waitFor(200, TimeUnit.MILLISECONDS); }
            catch (InterruptedException exception) { interrupted = true; }
            process.destroyForcibly();
            output.cancel(true);
            reader.shutdownNow();
            AtomicReference<IOException> closeFailure = new AtomicReference<>();
            // Stream.close can itself wait on a reader's monitor; never close on the calling thread.
            Thread closer = Thread.ofVirtual().start(() -> {
                for (Closeable stream : List.of(process.getInputStream(), process.getErrorStream(),
                        process.getOutputStream())) {
                    try { stream.close(); }
                    catch (IOException exception) { closeFailure.compareAndSet(null, exception); }
                }
            });
            try {
                process.waitFor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                reader.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                closer.join(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
            } catch (InterruptedException exception) {
                interrupted = true;
            }
            if (process.isAlive() || descendants.values().stream().anyMatch(ProcessHandle::isAlive)
                    || !reader.isTerminated() || closer.isAlive())
                throw new IOException("local Git cleanup did not finish within its two-second bound");
            if (closeFailure.get() != null) throw new IOException("local Git stream cleanup failed", closeFailure.get());
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void executeCase(JsonNode testCase) throws Exception {
        Path root = materialize(testCase);
        JsonNode expectation = testCase.path("consumer_expectation");
        if ("accept".equals(expectation.path("outcome").asText())) {
            Object parsed = parse(testCase.path("consumer_entry").asText(), root);
            JsonNode semantics = expectation.has("normalized_semantics")
                    ? expectation.path("normalized_semantics") : testCase.path("normalized_semantics");
            assertSemantic(semantics, parsed, testCase.path("id").asText());
            return;
        }
        Throwable thrown = assertThrows(Throwable.class,
                () -> parse(testCase.path("consumer_entry").asText(), root));
        JsonNode diagnostic = expectation.path("diagnostic");
        assertEquals(diagnostic.path("exception_class").asText(), thrown.getClass().getName());
        String expected = diagnostic.path("message").asText();
        if ("contains".equals(diagnostic.path("message_match").asText())) {
            assertTrue(String.valueOf(thrown.getMessage()).contains(expected), thrown::toString);
        } else {
            assertEquals(expected, thrown.getMessage());
        }
    }

    private Object parse(String entry, Path root) throws IOException {
        return switch (entry) {
            case "com.openggf.trace.TraceData.load" -> TraceData.load(root);
            case "com.openggf.trace.TraceRunManifest.load+validate" -> {
                TraceRunManifest manifest = TraceRunManifest.load(root.resolve("run_manifest.json"));
                manifest.validate(root);
                yield manifest;
            }
            case "com.openggf.game.sonic1.specialstage.Sonic1SpecialStageTraceData.load" ->
                    Sonic1SpecialStageTraceData.load(root);
            case "com.openggf.trace.SpecialStageTraceData.load" -> SpecialStageTraceData.load(root);
            case "com.openggf.game.sonic3k.specialstage.S3kSpecialStageTraceData.load" ->
                    S3kSpecialStageTraceData.load(root);
            default -> throw new AssertionError("unknown Java consumer entry: " + entry);
        };
    }

    private Path materialize(JsonNode testCase) throws IOException {
        Path source = PACK.resolve(testCase.path("root").asText());
        JsonNode materialization = testCase.path("materialization");
        if (materialization.isMissingNode()) return source;
        Path targetRoot = temporaryDirectory.resolve(testCase.path("id").asText());
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = targetRoot.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target);
            }
        }
        JsonNode payload = JSON.readTree(targetRoot.resolve(materialization.path("source").asText()).toFile());
        Files.write(targetRoot.resolve(materialization.path("target").asText()),
                Base64.getDecoder().decode(payload.path("base64").asText()));
        return targetRoot;
    }

    private void assertSemantic(JsonNode expected, Object actual, String path) throws Exception {
        if (expected.isNull()) {
            assertEquals(null, actual, path);
        } else if (expected.isObject()) {
            for (var fields = expected.fields(); fields.hasNext();) {
                var field = fields.next();
                assertSemantic(field.getValue(), resolve(actual, field.getKey()), path + "." + field.getKey());
            }
        } else if (expected.isArray()) {
            assertTrue(actual instanceof List<?>, path + " must be a list");
            List<?> values = (List<?>) actual;
            assertEquals(expected.size(), values.size(), path);
            for (int index = 0; index < expected.size(); index++) {
                assertSemantic(expected.get(index), values.get(index), path + "[" + index + "]");
            }
        } else if (expected.isBoolean()) {
            assertEquals(expected.booleanValue(), actual, path);
        } else if (expected.isIntegralNumber()) {
            assertTrue(actual instanceof Number, path + " must be numeric, got " + actual);
            assertEquals(expected.longValue(), ((Number) actual).longValue(), path);
        } else {
            String value = actual instanceof Enum<?> enumeration
                    ? enumeration.name().toLowerCase(Locale.ROOT) : String.valueOf(actual);
            assertEquals(expected.asText(), value, path);
        }
    }

    private Object resolve(Object actual, String field) throws Exception {
        assertNotNull(actual, field);
        if (actual instanceof TraceData trace) {
            return switch (field) {
                case "metadata" -> trace.metadata();
                case "frames" -> java.util.stream.IntStream.range(0, trace.frameCount()).mapToObj(trace::getFrame).toList();
                case "aux_events" -> java.util.stream.IntStream.range(0, trace.frameCount())
                        .boxed().flatMap(frame -> trace.getEventsForFrame(frame).stream()).toList();
                case "hardware_work_completed" -> trace.hardwareTimingSchedule().edges();
                default -> throw new AssertionError("unknown TraceData semantic: " + field);
            };
        }
        if (actual instanceof Sonic1SpecialStageTraceData trace) {
            return traceField(field, trace.metadata(), trace.frames(), trace.eventsByFrame(), trace.frameCount());
        }
        if (actual instanceof SpecialStageTraceData trace) {
            List<?> frames = java.util.stream.IntStream.range(0, trace.frameCount()).mapToObj(trace::getFrame).toList();
            return switch (field) {
                case "metadata" -> trace.metadata();
                case "frames" -> frames;
                case "aux_events" -> java.util.stream.IntStream.range(0, trace.frameCount())
                        .boxed().flatMap(frame -> trace.getEventsForFrame(frame).stream()).toList();
                default -> throw new AssertionError("unknown special-stage semantic: " + field);
            };
        }
        if (actual instanceof S3kSpecialStageTraceData trace) {
            return traceField(field, trace.metadata(), trace.frames(), trace.eventsByFrame(), trace.frameCount());
        }
        if (actual instanceof TraceRunManifest manifest) {
            if (field.equals("trace_schema")) return TraceRunManifest.TRACE_SCHEMA;
            if (field.equals("member_order")) return manifest.segments().stream().map(TraceRunManifest.Segment::dir).toList();
        }
        if (actual instanceof TraceFrame frame && field.equals("player")) return frame;
        if (actual instanceof TraceFrame && field.equals("present")) return true;
        if (actual instanceof com.openggf.trace.TraceEvent.StateSnapshot snapshot) {
            if (field.equals("frame")) return snapshot.frame();
            return snapshot.fields().get(field);
        }
        if (actual instanceof TraceMetadata metadata) {
            return switch (field) {
                case "initial_rng_seed" -> metadata.initialRngSeed();
                case "rng_seed_hex" -> metadata.rngSeedHex();
                case "start_x" -> (int) metadata.startX();
                case "start_y" -> (int) metadata.startY();
                case "start_x_hex" -> metadata.startXHex();
                case "start_y_hex" -> metadata.startYHex();
                default -> invoke(actual, field);
            };
        }
        return invoke(actual, field);
    }

    private Object traceField(String field, TraceMetadata metadata, List<?> frames,
            java.util.Map<Integer, ?> events, int frameCount) {
        return switch (field) {
            case "metadata" -> metadata;
            case "frames" -> frames;
            case "aux_events" -> java.util.stream.IntStream.range(0, frameCount)
                    .mapToObj(events::get).filter(java.util.Objects::nonNull)
                    .flatMap(value -> ((List<?>) value).stream()).toList();
            default -> throw new AssertionError("unknown special-stage semantic: " + field);
        };
    }

    private Object invoke(Object actual, String snakeName) throws Exception {
        String camel = snakeName.replaceAll("_([a-z])", "$1");
        StringBuilder converted = new StringBuilder();
        boolean upper = false;
        for (char character : snakeName.toCharArray()) {
            if (character == '_') { upper = true; continue; }
            converted.append(upper ? Character.toUpperCase(character) : character);
            upper = false;
        }
        camel = converted.toString();
        if (camel.equals("status") && (actual instanceof TraceFrame
                || actual instanceof com.openggf.trace.TraceCharacterState)) camel = "statusByte";
        camel = camel.replace("Togo", "ToGo");
        Method method = actual.getClass().getMethod(camel);
        return method.invoke(actual);
    }
}
