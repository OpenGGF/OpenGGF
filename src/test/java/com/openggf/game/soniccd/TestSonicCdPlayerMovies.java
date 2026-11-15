package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native rows for the player behaviour of the PLAYER3 lane. Each replay is a cold power-on movie whose trace is the
 * comparison; the BK2 drives the engine and no trace value enters it.
 */
class TestSonicCdPlayerMovies {
    @TempDir
    Path tempDir;

    /**
     * Full-act R42A ({@code rehost-long/R42A}): rows 2068-2070 hold A with a lag row (2069) between, on which the
     * pad holds Up alone. The lag V-INT ({@code VInt_Lag}, Level/Main.asm:738-766) does not read the pad, so
     * {@code playerCtrlTap} at row 2070 is taken against row 2068's held A and has no A press: native does not jump
     * (with ground speed $300 from the shooter's missile, {@code ObjSonic_CheckJump} would, scddisasm/src/Level/_Objects/Sonic/Main.asm:1779-1801).
     * Row 3164 is the next stop, not the player's: native lands on the wood block $44 (slot 79), which is not hosted.
     */
    @Test
    void aJumpPressIsTakenAgainstTheLastPolledRow() throws Exception {
        assertEqualsThrough("rehost-long", CdStagePayload.R42A, 3163);
    }

    /**
     * {@code rehost-player3/idle/R11A}: Sonic stands from the stage start with no input. The idle script starts the boredom
     * timer ({@code ObjSonic_ChkBoredom}, Sonic/Main.asm:11-25) and {@code RunBoredTimer} counts every unpaused V-INT,
     * lag V-INTs included (Level/Main.asm:704-709, 985-993); at {@code 3*60*60} the give-up writes animation $2B, -$500
     * up and $100 forward (row 10802). The ground mode stands until the script's jump frame $17 (row 10948), then only
     * bounds and falls through the floor (Main.asm:675-692); past the bottom boundary, which the level events pin to the
     * camera, it takes routine 6 without {@code KillPlayer} (row 11006, Main.asm:1704-1707), keeping its X speed; off
     * screen, the life the give-up set is lost and routine 8 waits without animating (rows 11010-11489). The whole movie
     * equals native, every row.
     */
    @Test
    void theIdlePlayerGivesUpAfterThreeMinutes() throws Exception {
        assertEqualsThrough("rehost-player3/idle", CdStagePayload.R11A, 11489);
    }

    /**
     * The timer itself against native RAM: {@code boredTimer} ($FFF780) read after every row of the idle movie by the
     * lane's probe ({@code rehost-player3/idle/R11A/probe/bored.csv}: row, timer), compared on the rows whose V-INT ran
     * to {@code VInt_Finish} (the trace's V-INT counter advanced; the LAG lane's sampling rule).
     */
    @Test
    void theBoredomTimerCountsEveryVInt() throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-player3/idle/R11A/movie.bk2");
        Path probe = movie.getParent().resolve("probe/bored.csv");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(probe), "No boredTimer probe: " + probe);
        java.util.Map<Integer, Integer> nativeTimer = new java.util.HashMap<>();
        for (String line : Files.readAllLines(probe)) {
            String[] f = line.split(",");
            if (f.length < 2 || !Character.isDigit(f[0].charAt(0))) continue;
            nativeTimer.put(Integer.parseInt(f[0]), Integer.parseInt(f[1], 16));
        }
        var fixture = CdFixtureReplay.Fixture.under(movie.getParent().getParent(), CdStagePayload.R11A);
        var trace = com.openggf.trace.TraceData.load(fixture.traceDir());
        int[] compared = {0};
        java.util.List<String> mismatches = new java.util.ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, Files.createDirectories(tempDir.resolve("bored")), fixture, 11010, false, -1, -1,
                    new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, com.openggf.sprites.playable.AbstractPlayableSprite sonic) {
                            Integer expected = nativeTimer.get(row);
                            if (expected == null || row == 0) return;
                            if (trace.getFrame(row).vblankCounter() == trace.getFrame(row - 1).vblankCounter()) return;
                            compared[0]++;
                            int actual = com.openggf.game.soniccd.player.CdPlayerStage.of(sonic).boredom().timer();
                            if (actual != expected && mismatches.size() < 5) {
                                mismatches.add("row " + row + " native " + expected + " engine " + actual);
                            }
                        }
                    });
        }
        assertTrue(mismatches.isEmpty(), () -> "boredTimer differs: " + mismatches);
        assertTrue(compared[0] > 10_000, () -> "only " + compared[0] + " rows compared");
    }

    private void assertEqualsThrough(String dir, CdStagePayload payload, int lastGreenRow) throws Exception {
        Path movie = CdTestMedia.nativeFile(dir + "/" + payload + "/movie.bk2");
        var fixture = CdFixtureReplay.Fixture.under(movie.getParent().getParent(), payload);
        org.junit.jupiter.api.Assumptions.assumeTrue(fixture.present(), "Native movie absent: " + movie);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            Path config = Files.createDirectories(tempDir.resolve(dir.replace('/', '_') + payload));
            var outcome = CdFixtureReplay.replay(disc, config, fixture, lastGreenRow, true, -1, -1);
            for (CdFixtureReplay.Row row : outcome.rows()) System.out.println("[player-movie] " + row.format());
            assertTrue(outcome.firstErrorRow() < 0,
                    () -> dir + "/" + payload + " diverges at row " + outcome.firstErrorRow() + " before row "
                            + lastGreenRow + ": " + outcome.fields());
            assertTrue(outcome.rowsCompared() > lastGreenRow, () -> "only " + outcome.rowsCompared() + " rows compared");
        }
    }
}
