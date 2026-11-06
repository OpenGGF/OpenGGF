package com.openggf.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.SourceScanExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import java.util.Arrays;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guard: every committed trace fixture agrees with its source BK2 movie about
 * <em>which movie frame each recorded row belongs to</em>.
 *
 * <p>Five harness defects found in short order were all the same shape — two
 * parts of the system disagreeing by one about the frame a thing belonged to —
 * and every one of them surfaced only as a mysterious red trace weeks later.
 * The cheapest place to catch that class of bug is where it is objectively
 * checkable without a ROM, an engine, or a replay: the fixture itself carries a
 * recorded per-frame controller byte, and the movie it was recorded from
 * carries the same presses. If a producer publishes {@code bk2_frame_offset}
 * that disagrees with the offset it actually sampled input at, the two streams
 * stop lining up and this guard fails immediately at record time rather than
 * months later inside a physics comparison.
 *
 * <p>This is precisely how the {@code S2RunCaptureRunner} {@code frameNow + 1}
 * observer skew was proven: the run's {@code ss_2} and {@code ss_5} segments
 * reproduce their movie exactly (6361/6361 and 6690/6690 rows) at the published
 * offset and lose several hundred rows at either neighbouring offset.
 *
 * <h2>What is compared</h2>
 * The fixture's {@code input} column is the ROM's P1 controller hold byte
 * (S1 {@code Ctrl_1_Held} / S2 {@code Ctrl_1_Held}, written by
 * {@code ReadJoypads} — s1disasm {@code sonic.asm} / s2disasm {@code s2.asm}),
 * whose low nibble is direction bits U/D/L/R (bit 0..3) and whose bit 7 is
 * Start. Those bits are compared exactly.
 *
 * <p>The three face-button bits (4..6) are compared only as "some face button
 * is held". BizHawk's Genesis log key names its three face columns A/B/C, but
 * no fixed permutation of those columns onto ROM bits 4/5/6 reproduces every
 * committed fixture (the best single permutation explains 782147 of 789086
 * rows, none explains all), so the column-to-ROM-bit identity is a BizHawk core
 * detail this guard deliberately does not assert. Collapsing them costs nothing
 * for the invariant under test — this guard is about frame <em>alignment</em>,
 * not button identity — and it still discriminates strongly: with the collapse,
 * every committed fixture matches its movie on 100% of rows at the published
 * offset, and loses 2-6% of rows at plus or minus one.
 *
 * <p>Consequently the guard demands an exact 100% at the published offset, and
 * separately demands that the published offset strictly beat both neighbours
 * wherever the movie window is not constant. A window with no input changes
 * (a few short S1 title-card segments) cannot discriminate at all; those are
 * exempted from the neighbour check by measurement of the movie, never by name.
 *
 * <p>Fixtures with no committed source BK2 are skipped — a movie is a large
 * binary and not every fixture ships one — so the guard also asserts a floor on
 * how many segments it actually covered, to fail loudly rather than vacuously
 * if the fixture tree is rearranged.
 *
 * <p>Comparison-only (hard rule 4): this reads fixture bytes and movie bytes and
 * compares them to each other. No engine is constructed and no engine state is
 * hydrated from either.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestTraceFixtureMovieAlignmentGuard {
    private final Map<Path, int[]> inputCache = new HashMap<>();

    @AfterAll
    void releaseInputs() {
        inputCache.clear();
        movieCache.clear();
    }

    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final Path FIXTURE_ROOT =
            PROJECT_ROOT.resolve("src/test/resources/traces");

    /**
     * 124 segments were covered when the guard landed: 22 standalone fixture
     * directories plus the segments of the five runs that ship their movie
     * (two S3K runs reference a movie that is not committed and are skipped).
     * A drop below this means fixtures or movies were removed and the guard
     * quietly stopped guarding.
     */
    private static final int MINIMUM_COVERED_SEGMENTS = 124;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // Immutable, preconfigured reader shared by aux tasks; they never configure MAPPER.
    private static final ObjectReader AUX_READER = MAPPER.reader();

    private final Map<Path, List<Bk2FrameInput>> movieCache = new HashMap<>();

    // ---------------------------------------------------------------- (a)

    @Test
    void everyFixtureRowReproducesItsMovieRowAtThePublishedOffset() throws IOException {
        List<Segment> segments = discoverSegments();
        List<String> failures = new ArrayList<>();
        int covered = 0;

        for (Segment segment : segments) {
            List<Bk2FrameInput> movie = movie(segment.bk2());
            int[] rowInputs = readInputColumn(segment.dir());
            if (rowInputs.length == 0) {
                failures.add(segment.label() + ": physics payload has no rows");
                continue;
            }
            covered++;

            int atOffset = matches(rowInputs, movie, segment.offset());
            if (atOffset != rowInputs.length) {
                failures.add(segment.label() + ": input column matches the movie on only "
                        + atOffset + "/" + rowInputs.length
                        + " rows at the published bk2_frame_offset=" + segment.offset()
                        + " (first mismatch at trace frame "
                        + firstMismatch(rowInputs, movie, segment.offset())
                        + "); minus one=" + matches(rowInputs, movie, segment.offset() - 1)
                        + ", plus one=" + matches(rowInputs, movie, segment.offset() + 1));
                continue;
            }

            int before = matches(rowInputs, movie, segment.offset() - 1);
            int after = matches(rowInputs, movie, segment.offset() + 1);
            if (before == atOffset && after == atOffset) {
                // A window in which the recorded input never changes matches at
                // every shift; it carries no alignment evidence either way.
                continue;
            }
            if (before >= atOffset || after >= atOffset) {
                failures.add(segment.label() + ": a neighbouring offset explains the input"
                        + " column at least as well as the published one"
                        + " (minus one=" + before + ", offset=" + atOffset
                        + ", plus one=" + after + " of " + rowInputs.length + " rows)");
            }
        }

        if (!failures.isEmpty()) {
            fail("Trace fixture(s) disagree with their source BK2 about which movie frame"
                    + " each row belongs to. This is a harness offset bug, not a fixture to"
                    + " relax:\n  " + String.join("\n  ", failures));
        }
        assertTrue(covered >= MINIMUM_COVERED_SEGMENTS,
                "Alignment guard covered only " + covered + " segments, below the "
                        + MINIMUM_COVERED_SEGMENTS + " it covered when it landed — fixtures or"
                        + " their movies moved and the guard has stopped guarding.");
    }

    // ---------------------------------------------------------------- (b)

    /**
     * Every {@code run_objects_end} pass states both a segment-local input
     * sample frame and the absolute BK2 row it sampled. Those are two names for
     * one row, so they must resolve through the segment's published offset.
     * {@link SpecialStageRunObjectsPassBinder#validateInputAgainstMovie} makes
     * the same check at replay time; doing it here needs no ROM and no engine,
     * so a bad recording is rejected before it is ever replayed.
     */
    @Test
    void specialStagePassInputSamplesResolveToOneMovieRow() throws Exception {
        AuxScanResult result = scanPassSamples(discoverSegments(), 2, TestTraceFixtureMovieAlignmentGuard::openText);
        List<String> failures = result.failures();
        int passes = result.passes();

        if (!failures.isEmpty()) {
            fail("run_objects_end pass(es) disagree with their segment's bk2_frame_offset"
                    + " about which movie row the pass sampled input from:\n  "
                    + String.join("\n  ", failures));
        }
        assertTrue(passes > 0,
                "No run_objects_end passes found in the fixture tree — the pass"
                        + " self-consistency guard has stopped guarding.");
    }

    private record AuxScanResult(int passes, List<String> failures) {
        private AuxScanResult {
            failures = List.copyOf(failures);
        }
    }

    @FunctionalInterface
    private interface AuxReaderOpener {
        BufferedReader open(Path path) throws IOException;
    }

    private static AuxScanResult scanPassSamples(List<Segment> segments, int workers,
            AuxReaderOpener opener) throws Exception {
        List<Callable<AuxScanResult>> tasks = new ArrayList<>(segments.size());
        for (Segment segment : segments) {
            tasks.add(() -> scanPassSamples(segment, opener));
        }
        List<String> failures = new ArrayList<>();
        int passes = 0;
        // The helper returns in discovery order, regardless of task completion order.
        for (AuxScanResult result : SourceScanExecutor.invokeOrdered(tasks, workers)) {
            passes += result.passes();
            failures.addAll(result.failures());
        }
        return new AuxScanResult(passes, failures);
    }

    private static AuxScanResult scanPassSamples(Segment segment, AuxReaderOpener opener) throws IOException {
        Path aux = segment.dir().resolve("aux_state.jsonl.gz");
        if (!Files.isRegularFile(aux)) {
            aux = segment.dir().resolve("aux_state.jsonl");
            if (!Files.isRegularFile(aux)) {
                return new AuxScanResult(0, List.of());
            }
        }
        List<String> failures = new ArrayList<>();
        int passes = 0;
        try (BufferedReader reader = opener.open(aux)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains("\"run_objects_end\"")) {
                    continue;
                }
                JsonNode node = AUX_READER.readTree(line);
                if (!"run_objects_end".equals(node.path("type").asText())) {
                    continue;
                }
                passes++;
                int sequence = node.path("pass_sequence").asInt(-1);
                checkSample(failures, segment, sequence, node,
                        "input_sample_frame", "input_sample_bk2_frame");
                checkSample(failures, segment, sequence, node,
                        "previous_input_sample_frame", "previous_input_sample_bk2_frame");
            }
        }
        return new AuxScanResult(passes, failures);
    }

    @Test
    void auxSegmentScansPreserveEveryRowFallbackAndOrderedDiagnostics(@TempDir Path root) throws Exception {
        List<Segment> segments = ownedAuxSegments(root);
        AuxScanResult expected = new AuxScanResult(5, List.of(
                "first pass 2: input_sample_bk2_frame=14 but bk2_frame_offset(10) + input_sample_frame(3) = 13",
                "first pass 2: missing previous_input_sample_frame/previous_input_sample_bk2_frame",
                "first pass -1: missing input_sample_frame/input_sample_bk2_frame",
                "second pass 7: input_sample_bk2_frame=21 but bk2_frame_offset(20) + input_sample_frame(0) = 20"));
        assertEquals(expected, historicalPassSampleScan(segments));
        assertEquals(expected, scanPassSamples(segments, 1, TestTraceFixtureMovieAlignmentGuard::openText));
        assertEquals(expected, scanPassSamples(segments, 2, TestTraceFixtureMovieAlignmentGuard::openText));
    }

    @Test
    void auxSegmentScansReallyOverlapAndCloseBeforeReturning(@TempDir Path root) throws Exception {
        List<Segment> segments = ownedAuxSegments(root);
        CountDownLatch firstReading = new CountDownLatch(1);
        CountDownLatch secondClosed = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        AuxReaderOpener opener = path -> {
            boolean first = path.getParent().equals(segments.getFirst().dir());
            if (!first) {
                await(firstReading, "first stream must start before another stream opens");
            }
            BufferedReader reader = openText(path);
            opened.incrementAndGet();
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            return new BufferedReader(reader) {
                private boolean firstRead = true;

                @Override
                public String readLine() throws IOException {
                    if (first && firstRead) {
                        firstRead = false;
                        firstReading.countDown();
                        // Forces completion order to differ from segment discovery order.
                        await(secondClosed, "second stream must finish while the first remains open");
                    }
                    return super.readLine();
                }

                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        active.decrementAndGet();
                        closed.incrementAndGet();
                        if (path.getParent().equals(segments.get(1).dir())) {
                            secondClosed.countDown();
                        }
                    }
                }
            };
        };
        assertEquals(historicalPassSampleScan(segments), scanPassSamples(segments, 2, opener));
        assertEquals(2, peak.get(), "at most two owned streams, with real overlap");
        assertEquals(3, opened.get(), "each present segment once; gzip takes precedence over raw");
        assertEquals(opened.get(), closed.get());
        assertEquals(0, active.get(), "no reader may outlive the invocation");
    }

    @Test
    void auxSegmentParseFailuresStillCloseTheirOwnedReader(@TempDir Path root) throws Exception {
        Path dir = Files.createDirectory(root.resolve("invalid"));
        Files.writeString(dir.resolve("aux_state.jsonl"), "{\"type\":\"run_objects_end\",\n");
        AtomicBoolean closed = new AtomicBoolean();
        AuxReaderOpener opener = path -> new BufferedReader(openText(path)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    closed.set(true);
                }
            }
        };
        assertThrows(IOException.class, () -> scanPassSamples(
                List.of(new Segment(dir, root.resolve("unused.bk2"), 0, "invalid")), 2, opener));
        assertTrue(closed.get());
    }

    private static void await(CountDownLatch latch, String failure) throws IOException {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while coordinating owned fixture readers", interrupted);
        }
    }

    private static List<Segment> ownedAuxSegments(Path root) throws IOException {
        Path first = Files.createDirectory(root.resolve("first"));
        Files.writeString(first.resolve("aux_state.jsonl"), """
                ignored non-JSON row without a pass marker
                {"type":"other","note":"run_objects_end"}
                {"type":"run_objects_end","pass_sequence":1,"input_sample_frame":2,"input_sample_bk2_frame":12,"previous_input_sample_frame":1,"previous_input_sample_bk2_frame":11}
                {"type":"run_objects_end","pass_sequence":2,"input_sample_frame":3,"input_sample_bk2_frame":14}
                {"type":"run_objects_end","previous_input_sample_frame":0,"previous_input_sample_bk2_frame":10}
                """);
        Path second = Files.createDirectory(root.resolve("second"));
        try (var output = new GZIPOutputStream(Files.newOutputStream(second.resolve("aux_state.jsonl.gz")))) {
            output.write(("{\"type\":\"run_objects_end\",\"pass_sequence\":7,\"input_sample_frame\":0,"
                    + "\"input_sample_bk2_frame\":21,\"previous_input_sample_frame\":-1,"
                    + "\"previous_input_sample_bk2_frame\":19}\n").getBytes(StandardCharsets.UTF_8));
        }
        // A present raw payload must never override or supplement its gzip sibling.
        Files.writeString(second.resolve("aux_state.jsonl"), "{\"type\":\"run_objects_end\"}\n");
        Path negative = Files.createDirectory(root.resolve("negative"));
        Files.writeString(negative.resolve("aux_state.jsonl"), """
                {"type":"run_objects_end","pass_sequence":9,"input_sample_frame":1,"input_sample_bk2_frame":-3,"previous_input_sample_frame":0,"previous_input_sample_bk2_frame":-4}
                """);
        Path absent = Files.createDirectory(root.resolve("absent"));
        Path unusedMovie = root.resolve("not-read.bk2");
        return List.of(new Segment(first, unusedMovie, 10, "first"),
                new Segment(second, unusedMovie, 20, "second"),
                new Segment(negative, unusedMovie, -4, "negative"),
                new Segment(absent, unusedMovie, 0, "absent"));
    }

    /** Independent pre-concurrency loop; pins filtering, field coercion and row order. */
    private static AuxScanResult historicalPassSampleScan(List<Segment> segments) throws IOException {
        List<String> failures = new ArrayList<>();
        int passes = 0;

        for (Segment segment : segments) {
            Path aux = segment.dir().resolve("aux_state.jsonl.gz");
            if (!Files.isRegularFile(aux)) {
                aux = segment.dir().resolve("aux_state.jsonl");
                if (!Files.isRegularFile(aux)) {
                    continue;
                }
            }
            try (BufferedReader reader = openText(aux)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.contains("\"run_objects_end\"")) {
                        continue;
                    }
                    JsonNode node = MAPPER.readTree(line);
                    if (!"run_objects_end".equals(node.path("type").asText())) {
                        continue;
                    }
                    passes++;
                    int sequence = node.path("pass_sequence").asInt(-1);
                    checkSample(failures, segment, sequence, node,
                            "input_sample_frame", "input_sample_bk2_frame");
                    checkSample(failures, segment, sequence, node,
                            "previous_input_sample_frame", "previous_input_sample_bk2_frame");
                }
            }
        }

        return new AuxScanResult(passes, failures);
    }

    private static void checkSample(List<String> failures, Segment segment, int sequence,
            JsonNode node, String localField, String bk2Field) {
        JsonNode local = node.get(localField);
        JsonNode bk2 = node.get(bk2Field);
        if (local == null || bk2 == null) {
            failures.add(segment.label() + " pass " + sequence + ": missing "
                    + localField + "/" + bk2Field);
            return;
        }
        int expected = segment.offset() + local.asInt();
        if (bk2.asInt() != expected) {
            failures.add(segment.label() + " pass " + sequence + ": " + bk2Field + "="
                    + bk2.asInt() + " but bk2_frame_offset(" + segment.offset() + ") + "
                    + localField + "(" + local.asInt() + ") = " + expected);
        }
    }

    // ---------------------------------------------------------------- (c)

    /**
     * Run manifests state each segment's window in movie space. Windows must be
     * ordered and non-overlapping: two segments claiming the same movie row is
     * the manifest-level form of the same "which frame does this belong to"
     * confusion, and it is what a mis-stamped gap row would look like once it
     * reached publication.
     */
    @Test
    void runManifestSegmentWindowsTileTheMovieWithoutOverlap() throws IOException {
        List<String> failures = new ArrayList<>();
        int runs = 0;

        for (Path manifest : listManifests()) {
            runs++;
            JsonNode root = MAPPER.readTree(manifest.toFile());
            int cursor = 0;
            String previous = "(start of movie)";
            for (JsonNode segment : root.path("segments")) {
                String dir = segment.path("dir").asText();
                int offset = segment.path("bk2_frame_offset").asInt(-1);
                int count = segment.path("trace_frame_count").asInt(-1);
                if (offset < 0 || count <= 0) {
                    failures.add(manifest + " [" + dir + "]: missing or invalid"
                            + " bk2_frame_offset/trace_frame_count");
                    continue;
                }
                if (offset < cursor) {
                    failures.add(manifest + " [" + dir + "]: window starts at movie frame "
                            + offset + ", inside the window already claimed by " + previous
                            + " (which ends at " + cursor + ")");
                }
                Path payload = manifest.resolveSibling(dir);
                int rows = readInputColumn(payload).length;
                if (rows > 0 && rows != count) {
                    failures.add(manifest + " [" + dir + "]: manifest states"
                            + " trace_frame_count=" + count + " but the payload has "
                            + rows + " rows");
                }
                cursor = offset + count;
                previous = dir;
            }
        }

        if (!failures.isEmpty()) {
            fail("Run manifest segment windows are inconsistent:\n  "
                    + String.join("\n  ", failures));
        }
        assertTrue(runs > 0, "No run manifests found under " + FIXTURE_ROOT);
    }

    // ---------------------------------------------------------------- helpers

    private record Segment(Path dir, Path bk2, int offset, String label) {
    }

    /**
     * Standalone fixture directories carry their own {@code metadata.json};
     * run directories carry one {@code run_manifest.json} naming a shared movie
     * and every segment's window into it. Only segments whose movie is actually
     * committed can be checked.
     */
    private List<Segment> discoverSegments() throws IOException {
        List<Segment> segments = new ArrayList<>();

        try (var tree = Files.walk(FIXTURE_ROOT)) {
            List<Path> metadata = tree.filter(Files::isRegularFile)
                    .filter(p -> "metadata.json".equals(p.getFileName().toString()))
                    .sorted()
                    .toList();
            for (Path meta : metadata) {
                JsonNode root = MAPPER.readTree(meta.toFile());
                JsonNode source = root.get("source_bk2");
                JsonNode offset = root.get("bk2_frame_offset");
                if (source == null || offset == null) {
                    continue;
                }
                Path bk2 = meta.resolveSibling(source.asText());
                if (!Files.isRegularFile(bk2)) {
                    continue;
                }
                Path dir = meta.getParent();
                segments.add(new Segment(dir, bk2, offset.asInt(), relativize(dir)));
            }
        }

        for (Path manifest : listManifests()) {
            JsonNode root = MAPPER.readTree(manifest.toFile());
            Path bk2 = manifest.resolveSibling(root.path("source_bk2").asText());
            if (!Files.isRegularFile(bk2)) {
                continue;
            }
            for (JsonNode segment : root.path("segments")) {
                Path dir = manifest.resolveSibling(segment.path("dir").asText());
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                segments.add(new Segment(dir, bk2,
                        segment.path("bk2_frame_offset").asInt(), relativize(dir)));
            }
        }
        return segments;
    }

    private List<Path> listManifests() throws IOException {
        try (var tree = Files.walk(FIXTURE_ROOT)) {
            return tree.filter(Files::isRegularFile)
                    .filter(p -> "run_manifest.json".equals(p.getFileName().toString()))
                    .sorted()
                    .toList();
        }
    }

    private List<Bk2FrameInput> movie(Path bk2) throws IOException {
        List<Bk2FrameInput> cached = movieCache.get(bk2);
        if (cached != null) {
            return cached;
        }
        Bk2Movie loaded = new Bk2MovieLoader().load(bk2);
        List<Bk2FrameInput> frames = loaded.getFrames();
        movieCache.put(bk2, frames);
        return frames;
    }

    /**
     * Reads the {@code input} column, which every physics payload carries as a
     * hex ROM controller byte in a named CSV header. Returns an empty array when
     * the directory holds no physics payload at all.
     */
    private int[] readInputColumn(Path dir) throws IOException {
        int[] cached = inputCache.get(dir);
        if (cached != null) return cached;
        int[] inputs = loadInputColumn(dir);
        inputCache.put(dir, inputs);
        return inputs;
    }

    static int[] loadInputColumn(Path dir) throws IOException {
        Path payload = dir.resolve("physics.csv.gz");
        if (!Files.isRegularFile(payload)) {
            payload = dir.resolve("physics.csv");
            if (!Files.isRegularFile(payload)) {
                return new int[0];
            }
        }
        try (BufferedReader reader = openText(payload)) {
            String header = reader.readLine();
            if (header == null) {
                return new int[0];
            }
            int column = List.of(header.split(",", -1)).indexOf("input");
            if (column < 0) {
                throw new IOException("physics payload has no 'input' column: " + payload);
            }
            int[] values = new int[1024];
            int size = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (size == values.length) values = Arrays.copyOf(values, size * 2);
                values[size++] = inputValue(line, column);
            }
            return Arrays.copyOf(values, size);
        }
    }

    static int inputValue(String line, int column) {
        int start = 0;
        for (int index = 0; index < column; index++) {
            int comma = line.indexOf(',', start);
            // The former split(..., -1)[column] rejected short rows, including
            // those with no input field. Preserve its exception category.
            if (comma < 0) throw new ArrayIndexOutOfBoundsException(column);
            start = comma + 1;
        }
        int end = line.indexOf(',', start);
        return Integer.parseInt(line.substring(start, end < 0 ? line.length() : end).trim(), 16);
    }

    private static BufferedReader openText(Path path) throws IOException {
        InputStream in = Files.newInputStream(path);
        if (path.getFileName().toString().endsWith(".gz")) {
            in = new GZIPInputStream(in, 1 << 16);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
    }

    private static int matches(int[] rowInputs, List<Bk2FrameInput> movie, int offset) {
        int matched = 0;
        for (int i = 0; i < rowInputs.length; i++) {
            int index = offset + i;
            if (index < 0 || index >= movie.size()) {
                continue;
            }
            if (alignmentKey(rowInputs[i]) == alignmentKey(movie.get(index))) {
                matched++;
            }
        }
        return matched;
    }

    private static int firstMismatch(int[] rowInputs, List<Bk2FrameInput> movie, int offset) {
        for (int i = 0; i < rowInputs.length; i++) {
            int index = offset + i;
            if (index < 0 || index >= movie.size()
                    || alignmentKey(rowInputs[i]) != alignmentKey(movie.get(index))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Direction bits and Start exactly, face buttons collapsed to one bit — see
     * the class comment for why the A/B/C column identity is deliberately not
     * asserted. Bit layout of the ROM hold byte: 0=Up, 1=Down, 2=Left, 3=Right,
     * 4..6 face, 7=Start (s2disasm {@code ReadJoypads}).
     */
    private static int alignmentKey(int romHoldByte) {
        return (romHoldByte & 0x0F)
                | ((romHoldByte & 0x70) != 0 ? 0x10 : 0)
                | ((romHoldByte & 0x80) != 0 ? 0x20 : 0);
    }

    private static int alignmentKey(Bk2FrameInput frame) {
        int mask = frame.p1InputMask();
        int directions = 0;
        if ((mask & AbstractPlayableSprite.INPUT_UP) != 0) {
            directions |= 0x01;
        }
        if ((mask & AbstractPlayableSprite.INPUT_DOWN) != 0) {
            directions |= 0x02;
        }
        if ((mask & AbstractPlayableSprite.INPUT_LEFT) != 0) {
            directions |= 0x04;
        }
        if ((mask & AbstractPlayableSprite.INPUT_RIGHT) != 0) {
            directions |= 0x08;
        }
        return directions
                | (frame.p1ActionMask() != 0 ? 0x10 : 0)
                | (frame.p1StartPressed() ? 0x20 : 0);
    }

    private static String relativize(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path root = PROJECT_ROOT.toAbsolutePath().normalize();
        return (absolute.startsWith(root) ? root.relativize(absolute) : absolute)
                .toString().replace('\\', '/');
    }

    private static Path resolveProjectRoot() {
        String basedir = System.getProperty("project.basedir");
        if (basedir != null && !basedir.isEmpty()) {
            return Paths.get(basedir);
        }
        return Paths.get(System.getProperty("user.dir", "."));
    }
}
