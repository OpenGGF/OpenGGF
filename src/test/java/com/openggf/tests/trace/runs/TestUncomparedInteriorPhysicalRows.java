package com.openggf.tests.trace.runs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestUncomparedInteriorPhysicalRows {
    private static final Pattern MEASURED_TIMING = Pattern.compile(
            "(?<![0-9_])(?:10_?162|172)(?![0-9_])"
                    + "|s2-sonic-tails-complete-emeralds");

    @Test
    void representedAndInterstitialRowsFormOneContinuousPhysicalWalk() {
        List<AbstractRunChainTest.UncomparedInteriorPhysicalRow> rows =
                AbstractRunChainTest.uncomparedInteriorPhysicalRows(
                        40, 3, 47,
                        new boolean[]{false, true, false, true});

        assertEquals(List.of(40, 41, 42, 43, 44, 45, 46),
                rows.stream()
                        .map(AbstractRunChainTest.UncomparedInteriorPhysicalRow::movieRow)
                        .toList());
        assertTrue(rows.get(2).representedSpecialRow());
        assertFalse(rows.get(3).representedSpecialRow());
        assertEquals(43, rows.get(3).movieRow(),
                "the first gap row is the special segment's exclusive end");
        assertTrue(rows.get(4).lagGapRow(),
                "the census selects the loop for its row only");
    }

    @Test
    void synchronousLoadIsTheLastMultiRowNonAdmittedRun() {
        // admitted, load (3 lag rows), admitted, lone overrun lag row, admitted
        boolean[] gap = {false, false, true, true, true, false, false, true, false};
        assertEquals(4, AbstractRunChainTest.lastSynchronousLoadRow(gap),
                "a lone leave-loop overrun row cannot hold InitPlayers");
        assertEquals(3, AbstractRunChainTest.lastSynchronousLoadRow(
                new boolean[]{false, true, true, true}),
                "a gap ending in its load run releases on the final row");
        assertEquals(-1, AbstractRunChainTest.lastSynchronousLoadRow(
                new boolean[]{false, true, false, true, false}),
                "single-row lag runs locate no synchronous load");
        assertEquals(-1, AbstractRunChainTest.lastSynchronousLoadRow(new boolean[0]));
    }

    @Test
    void productionLevelEntryTimingContainsNoMeasuredRowOrRouteIdentity()
            throws IOException {
        for (Path source : List.of(
                Path.of("src/main/java/com/openggf/GameLoop.java"),
                Path.of("src/main/java/com/openggf/level/LevelManager.java"),
                Path.of("src/main/java/com/openggf/game/LevelInitProfile.java"),
                Path.of("src/main/java/com/openggf/game/sonic2/"
                        + "Sonic2LevelInitProfile.java"))) {
            assertFalse(MEASURED_TIMING.matcher(Files.readString(source)).find(),
                    source::toString);
        }
    }
}

