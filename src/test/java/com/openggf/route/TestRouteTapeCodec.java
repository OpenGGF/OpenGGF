package com.openggf.route;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Route rows survive every conversion losslessly: engine input, script text and BizHawk movies. */
class TestRouteTapeCodec {
    /** Every 8-bit combination on each lane, the other lane complemented, plus neutral and release rows. */
    private static InputTape everyCombination() {
        var rows = new ArrayList<PadRow>();
        for (int mask = 0; mask < 256; mask++) {
            rows.add(new PadRow(mask, 255 - mask));
            rows.add(PadRow.NEUTRAL);
        }
        rows.add(PadRow.of(PlayerSlot.P1, PhysicalButton.LEFT, PhysicalButton.RIGHT, PhysicalButton.START));
        rows.add(PadRow.of(PlayerSlot.P2, PhysicalButton.UP, PhysicalButton.DOWN, PhysicalButton.C));
        return InputTape.of(rows);
    }

    @Test
    void everyPhysicalCombinationSurvivesTheEngineConversion() {
        InputTape tape = everyCombination();
        for (int i = 0; i < tape.size(); i++) {
            PadRow row = tape.row(i);
            assertEquals(row, PadRow.fromEngine(row.toEngine(i)), "row " + i);
        }
    }

    @Test
    void engineFormMatchesWhatTheMovieLoaderProducesForTheSameRow(@TempDir Path dir) throws Exception {
        InputTape tape = everyCombination();
        var route = RouteArtifact.of(launch(), tape, new Provenance("test", "", "", List.of()));
        Path movie = RouteFiles.exportBk2(route, dir.resolve("all.bk2"));
        var frames = new Bk2MovieLoader().load(movie).getFrames();
        for (int i = 0; i < tape.size(); i++) {
            Bk2FrameInput direct = tape.row(i).toEngine(i);
            Bk2FrameInput loaded = frames.get(i);
            // Gameplay reads the input/action masks and Start; the raw line is diagnostics only.
            assertEquals(loaded.p1InputMask(), direct.p1InputMask(), "P1 input row " + i);
            assertEquals(loaded.p1ActionMask(), direct.p1ActionMask(), "P1 action row " + i);
            assertEquals(loaded.p1StartPressed(), direct.p1StartPressed(), "P1 start row " + i);
            assertEquals(loaded.p2InputMask(), direct.p2InputMask(), "P2 input row " + i);
            assertEquals(loaded.p2ActionMask(), direct.p2ActionMask(), "P2 action row " + i);
            assertEquals(loaded.p2StartPressed(), direct.p2StartPressed(), "P2 start row " + i);
        }
    }

    @Test
    void scriptTextRoundTripsEveryRowWithExplicitLetters() {
        InputTape tape = everyCombination();
        String script = RouteFiles.scriptText(tape);
        assertEquals(tape, RouteFiles.tapeFromScript(script));
        assertFalse(script.toLowerCase().contains("jump"), "the writer names physical buttons, never the jump alias");
        assertTrue(script.contains("\n1 U+D+L+R+A+B+C+S\n"), "P2 neutral is omitted: " + script.substring(0, 200));
        assertTrue(script.startsWith("1 - / U+D+L+R+A+B+C+S\n"), script.substring(0, 200));
    }

    @Test
    void exportedMovieUsesTheBizHawkKeyHeaderAndSyncSettings(@TempDir Path dir) throws Exception {
        var tape = InputTape.of(List.of(PadRow.of(PlayerSlot.P1, PhysicalButton.A, PhysicalButton.START),
                PadRow.NEUTRAL, PadRow.of(PlayerSlot.P2, PhysicalButton.B)));
        var route = RouteArtifact.of(launch(), tape, new Provenance("test", "", "", List.of()));
        Path movie = RouteFiles.exportBk2(route, dir.resolve("route.bk2"));
        try (var zip = new ZipFile(movie.toFile())) {
            String log = new String(zip.getInputStream(zip.getEntry("Input Log.txt")).readAllBytes());
            assertTrue(log.contains("LogKey:#Power|Reset|#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"), log);
            assertTrue(log.contains("|..|....A..S|........|"), log);
            assertTrue(log.contains("|..|........|.....B..|"), log);
            assertNotNull(zip.getEntry("SyncSettings.json"));
            String header = new String(zip.getInputStream(zip.getEntry("Header.txt")).readAllBytes());
            assertTrue(header.contains("Core Genplus-gx\n") && header.contains("Platform GEN\n"), header);
        }
        var metadata = new Bk2MovieLoader().load(movie).getHeaderMetadata();
        assertEquals(RomIdentity.standard("s3k").sha1(), metadata.get("SHA1"), "BizHawk's space-delimited header parses");
        assertEquals("Genplus-gx", metadata.get("Core"));
        assertEquals("BizHawk v2.0.0", metadata.get("MovieVersion"));
        byte[] first = Files.readAllBytes(movie);
        RouteFiles.exportBk2(route, dir.resolve("again.bk2"));
        assertArrayEquals(first, Files.readAllBytes(dir.resolve("again.bk2")), "byte-deterministic export");
    }

    @Test
    void pressEdgesComeFromThePreviousHeldRow() {
        PadRow a = PadRow.of(PlayerSlot.P1, PhysicalButton.A);
        LogicalInputSnapshot first = RecordedInputSnapshots.fromBk2(a.toEngine(1), PadRow.NEUTRAL.toEngine(0));
        LogicalInputSnapshot held = RecordedInputSnapshots.fromBk2(a.toEngine(2), a.toEngine(1));
        assertEquals(InputActionMasks.ACTION_A, first.player1().actionPressedMask());
        assertEquals(0, held.player1().actionPressedMask(), "a held A is not a fresh press");
        assertEquals(AbstractPlayableSprite.INPUT_JUMP, first.player1().pressedMask() & AbstractPlayableSprite.INPUT_JUMP);
    }

    @Test
    void tapesAreBoundsCheckedAndRejectNonControllerSignals(@TempDir Path dir) throws Exception {
        var tape = InputTape.of(List.of(PadRow.NEUTRAL, PadRow.NEUTRAL));
        assertThrows(IndexOutOfBoundsException.class, () -> tape.row(2));
        assertThrows(IndexOutOfBoundsException.class, () -> tape.row(-1));
        assertThrows(IllegalArgumentException.class, () -> new PadRow(0x100, 0));
        var debug = new Bk2FrameInput(0, 0, 0, false, 0, 0, false, true, false, false, false, false, "");
        assertThrows(IllegalArgumentException.class, () -> PadRow.fromEngine(debug));
        // A jump bit with no action button is A, as the input-log writer emits it.
        assertEquals(PadRow.of(PlayerSlot.P1, PhysicalButton.A),
                PadRow.fromEngine(new Bk2FrameInput(0, AbstractPlayableSprite.INPUT_JUMP, 0, false, "")));
        Path movie = RouteFiles.exportBk2(RouteArtifact.of(launch(), tape, new Provenance("t", "", "", List.of())),
                dir.resolve("two.bk2"));
        assertEquals(2, RouteFiles.tapeFromMovie(movie, 2).size());
        assertThrows(java.io.IOException.class, () -> RouteFiles.tapeFromMovie(movie, 3), "no clamping past the end");
    }

    static LaunchContract launch() {
        return LaunchContract.coldLevel(RomIdentity.standard("s3k"), new LevelId("s3k", 10, 0),
                new Roster("sonic", "tails"), 320, 0);
    }
}
