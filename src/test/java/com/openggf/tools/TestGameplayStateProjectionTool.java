package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.rewind.snapshot.RingSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Pure CLI/serialization/file ownership contracts; no ROM, Engine, GL or game services. */
class TestGameplayStateProjectionTool {
    @TempDir
    Path temporary;

    private String[] arguments() {
        return new String[]{"--profile", GameplayStateProjectionTool.PROFILE,
                "--rom", temporary.resolve("original.gen").toString(), "--input", "explicit.bk2",
                "--frames", "1", "--out-dir", temporary.resolve("new-output").toString()};
    }

    @Test
    void parsesOnlyTheExplicitColdPairedProfileWithoutFileOrEngineAccess() {
        var arguments = GameplayStateProjectionTool.Arguments.parse(arguments());
        assertEquals(temporary.resolve("original.gen"), arguments.rom());
        assertEquals(Path.of("explicit.bk2").toAbsolutePath(), arguments.input());
        assertEquals(1, arguments.frames());
        assertEquals(temporary.resolve("new-output"), arguments.outDir());
        assertFalse(Files.exists(arguments.outDir()));
    }

    @Test
    void requiresEveryOptionAndRejectsUnknownDuplicateOrMissingValues() {
        String[] valid = arguments();
        for (int option = 0; option < valid.length; option += 2) {
            var missing = new ArrayList<>(List.of(valid));
            missing.subList(option, option + 2).clear();
            var error = assertThrows(IllegalArgumentException.class,
                    () -> GameplayStateProjectionTool.Arguments.parse(missing.toArray(String[]::new)));
            assertTrue(error.getMessage().contains(valid[option]));
        }
        assertThrows(IllegalArgumentException.class,
                () -> GameplayStateProjectionTool.Arguments.parse(new String[]{"--restore", "snapshot"}));
        assertThrows(IllegalArgumentException.class,
                () -> GameplayStateProjectionTool.Arguments.parse(new String[]{"--frames"}));
        assertThrows(IllegalArgumentException.class,
                () -> GameplayStateProjectionTool.Arguments.parse(new String[]{"--input", "--frames"}));
        var duplicate = new ArrayList<>(List.of(valid));
        duplicate.addAll(List.of("--frames", "2"));
        assertThrows(IllegalArgumentException.class,
                () -> GameplayStateProjectionTool.Arguments.parse(duplicate.toArray(String[]::new)));
    }

    @Test
    void rejectsUnsupportedProfileRelativeRomAndNonpositiveOrUnboundedLimits() {
        String[] args = arguments();
        args[1] = "any-game";
        assertThrows(IllegalArgumentException.class, () -> GameplayStateProjectionTool.Arguments.parse(args));
        args[1] = GameplayStateProjectionTool.PROFILE;
        args[3] = "relative.gen";
        assertThrows(IllegalArgumentException.class, () -> GameplayStateProjectionTool.Arguments.parse(args));
        args[3] = temporary.resolve("original.gen").toString();
        for (String limit : List.of("0", "-1", "20001", "2147483648", "not-an-integer")) {
            args[7] = limit;
            assertThrows(IllegalArgumentException.class, () -> GameplayStateProjectionTool.Arguments.parse(args));
        }
        args[7] = String.valueOf(GameplayStateProjectionTool.MAX_INPUTS);
        assertEquals(GameplayStateProjectionTool.MAX_INPUTS,
                GameplayStateProjectionTool.Arguments.parse(args).frames());
    }

    @Test
    void neverClampsOrRepeatsTheLastMovieInputAndValidatesTheRequestedPrefix() {
        var neutral = new Bk2FrameInput(0, 0, 0, false, "|........|........|");
        var p2 = new Bk2FrameInput(1, 0, 0, false, 1, 0, false, "P2");
        var movie = movie(List.of(neutral, p2));
        assertDoesNotThrow(() -> GameplayStateProjectionTool.validateMovie(movie, 1));
        assertThrows(IllegalArgumentException.class, () -> GameplayStateProjectionTool.validateMovie(movie, 2));
        assertThrows(IllegalArgumentException.class, () -> GameplayStateProjectionTool.validateMovie(movie, 3));
        assertThrows(IllegalArgumentException.class, () -> GameplayStateProjectionTool.validateMovie(movie(List.of()), 1));
        var debug = new Bk2FrameInput(0, 0, 0, false, 0, 0, false,
                false, true, false, false, false, "debug");
        assertThrows(IllegalArgumentException.class,
                () -> GameplayStateProjectionTool.validateMovie(movie(List.of(debug)), 1));
    }

    private static Bk2Movie movie(List<Bk2FrameInput> frames) {
        return new Bk2Movie(Path.of("explicit.bk2"), "unused", Map.of(), frames, 0);
    }

    @Test
    void headerKeepsThe115NamedColumnsAndCsvEscapesQuotesCommasNewlinesAndUnicode() {
        var header = GameplayStateProjectionTool.HEADER;
        assertEquals(115, header.size());
        assertEquals(115, new HashSet<>(header).size());
        assertEquals(List.of("input", "phase", "pad"), header.subList(0, 3));
        assertEquals("p1_binding", header.get(14));
        assertEquals("p2_binding", header.get(60));
        assertEquals("rings_collected_words", header.get(106));
        assertEquals("rings_attracted", header.getLast());
        assertEquals("\"a,b\",\"c\"\"d\",\"é\n\",\"\"\n",
                new String(GameplayStateProjectionTool.csv(List.of("a,b", "c\"d", "é\n", "")),
                        StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> GameplayStateProjectionTool.csv(List.of("x".repeat(GameplayStateProjectionTool.MAX_ROW_BYTES))));
    }

    @Test
    void serializesCopiedRingRecordsWithSlotAndAnimationFieldsWithoutMutatingThem() {
        long[] collected = {33, Long.MIN_VALUE};
        int[] active = {3, 5};
        var snapshot = new RingSnapshot(collected,
                new RingSnapshot.SparkleEntry[]{new RingSnapshot.SparkleEntry(5, 7172)}, 9, 2048, active,
                0, 1, 2, 3, 4, new RingSnapshot.LostRingEntry[0],
                new RingSnapshot.AttractedRingEntry[]{new RingSnapshot.AttractedRingEntry(
                        true, 6, 100, 200, 1, 2, -3, 4, 7, 28, true, 99, 5, 6, 7)});
        collected[0] = 0;
        active[0] = 0;
        var row = new ArrayList<String>();
        GameplayStateProjectionTool.appendRings(row, snapshot);
        assertEquals(List.of("[33, -9223372036854775808]", "5:7172", "9", "2048", "[3, 5]", "1", "2", "3",
                "6:7:28:true:100:200:1:2:-3:4:true:99:5:6:7"), row);
        var again = new ArrayList<String>();
        GameplayStateProjectionTool.appendRings(again, snapshot);
        assertEquals(row, again);
        assertArrayEquals(new long[]{33, Long.MIN_VALUE}, snapshot.collectedWords());
    }

    @Test
    void bindingsUseActualLocalReferenceIdentityAndHaveAFiniteRetentionLimit() {
        var bindings = new GameplayStateProjectionTool.LocalBindings();
        Object first = new String("equal");
        Object second = new String("equal");
        bindings.bind(first, "p1_0");
        assertEquals("p1_0", bindings.id(first));
        assertEquals("other0", bindings.id(second));
        assertEquals("other0", bindings.id(second));
        assertEquals("none", bindings.id(null));
        assertThrows(IllegalArgumentException.class, () -> bindings.bind(new Object(), new String("p1_0")));
        for (int i = 2; i < GameplayStateProjectionTool.MAX_BINDINGS; i++) bindings.id(new Object());
        assertThrows(IllegalStateException.class, () -> bindings.id(new Object()));
        assertEquals("p1_0", bindings.id(first));
    }

    @Test
    void refusesEvenAnEmptyExistingDirectoryWithoutOverwritingAnything() throws Exception {
        Path existing = Files.createDirectory(temporary.resolve("existing"));
        assertThrows(FileAlreadyExistsException.class,
                () -> new GameplayStateProjectionTool.ProjectionOutput(existing));
        Path original = Files.writeString(existing.resolve("projection.csv"), "original");
        assertThrows(FileAlreadyExistsException.class,
                () -> new GameplayStateProjectionTool.ProjectionOutput(existing));
        assertEquals("original", Files.readString(original));
    }

    @Test
    void abortRemovesOnlyOwnedPartialProductsAndPreservesTheOriginalFailure() {
        Path output = temporary.resolve("partial");
        var original = new IllegalStateException("step/draw failure");
        assertSame(original, assertThrows(IllegalStateException.class, () -> {
            try (var ignored = new GameplayStateProjectionTool.ProjectionOutput(output)) {
                throw original;
            }
        }));
        assertFalse(Files.exists(output));
    }

    @Test
    void abortPreservesUnknownAdditionsAndReportsCleanupFailureAsSuppressed() throws Exception {
        Path output = temporary.resolve("partial-with-unknown");
        var original = new IllegalStateException("original failure");
        assertSame(original, assertThrows(IllegalStateException.class, () -> {
            try (var ignored = new GameplayStateProjectionTool.ProjectionOutput(output)) {
                Files.writeString(output.resolve("unknown"), "preserve");
                throw original;
            }
        }));
        assertEquals(1, original.getSuppressed().length);
        assertInstanceOf(IOException.class, original.getSuppressed()[0]);
        assertEquals("preserve", Files.readString(output.resolve("unknown")));
        assertFalse(Files.exists(output.resolve("projection.csv")));
    }

    @Test
    void incompleteOrWrongWidthOutputCannotBecomeACompletedObservation() throws Exception {
        Path output = temporary.resolve("incomplete");
        try (var writer = new GameplayStateProjectionTool.ProjectionOutput(output)) {
            assertThrows(IllegalArgumentException.class, () -> writer.row(List.of("too few fields")));
            assertThrows(IllegalStateException.class,
                    () -> writer.complete(GameplayStateProjectionTool.Arguments.parse(arguments()), endpoint()));
            assertFalse(Files.exists(output.resolve("projection.xml")));
        }
        assertFalse(Files.exists(output));
    }

    @Test
    void completedOutputContainsThreeRowsAndHonestEscapedZeroJunitObservation() throws Exception {
        var arguments = GameplayStateProjectionTool.Arguments.parse(arguments());
        List<String> endpoint = endpoint();
        try (var output = new GameplayStateProjectionTool.ProjectionOutput(arguments.outDir())) {
            for (int i = 0; i < 3; i++) output.row(endpoint);
            output.complete(arguments, endpoint);
            assertThrows(IllegalStateException.class, () -> output.row(endpoint));
        }
        List<String> lines = Files.readAllLines(arguments.outDir().resolve("projection.csv"));
        assertEquals(4, lines.size());
        var xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(arguments.outDir().resolve("projection.xml").toFile());
        Element root = xml.getDocumentElement();
        assertEquals("ordinary-projection", root.getTagName());
        assertEquals("1", root.getAttribute("completed_inputs"));
        assertEquals("3", root.getAttribute("rows"));
        assertEquals("115", root.getAttribute("fields"));
        assertEquals("0", root.getAttribute("junit_tests"));
        assertEquals("0", root.getAttribute("skips"));
        assertEquals(115, root.getElementsByTagName("field").getLength());
        assertEquals("<&\"value", root.getElementsByTagName("field").item(0).getTextContent());
        assertTrue(root.getAttribute("bindings").startsWith("LOCAL"));
        assertEquals(0, root.getElementsByTagName("testsuite").getLength());
    }

    private static List<String> endpoint() {
        var row = new ArrayList<>(Collections.nCopies(115, ""));
        row.set(0, "<&\"value");
        return row;
    }
}
