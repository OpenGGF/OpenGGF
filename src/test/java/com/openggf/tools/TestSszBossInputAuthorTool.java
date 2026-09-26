package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestSszBossInputAuthorTool {
    @Test
    void missingBossAndDeathCannotOutscoreObservedDamage() {
        double hit = SszBossInputAuthorTool.score(false, 8, 7, 1, false, 20);
        assertTrue(hit > SszBossInputAuthorTool.score(false, 8, null, 99, false, 0),
                "losing the target is not proof of defeating it");
        assertTrue(hit > SszBossInputAuthorTool.score(true, 8, 0, 99, false, 0));
        assertTrue(SszBossInputAuthorTool.score(false, 1, 0, 0, false, 0) > hit);
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void authoredFatalHitReplaysFromColdWithIdenticalObservedState(@TempDir Path output) throws Exception {
        Path input = Path.of("src/test/resources/routes/s3k/ssz1-tails-solo-cold-complete-320.bk2");
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null);
        var result = SszBossInputAuthorTool.run(rom, settings, input, 5090,
                SszBossInputAuthorTool.Encounter.GHZ, output, 240);
        assertEquals(SszBossInputAuthorTool.Stop.DEFEATED, result.stop());
        assertTrue(result.frames() > 5090 && result.frames() <= 5330);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(output.resolve("input.bk2"));
        var rows = Files.readAllLines(output.resolve("state.csv"));
        assertEquals(result.frames(), movie.getFrameCount());
        assertEquals(result.frames() + 1, rows.size());
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 10, 0, settings);
            for (int f = 0; f < movie.getFrameCount(); f++) {
                var pad = movie.getFrame(f);
                session.step(pad);
                session.render();
                String actual = session.stateLine(f, pad);
                // The exploratory pad has no raw BK2 text; compare every state field.
                assertEquals(withoutInput(rows.get(f + 1)), withoutInput(actual), "frame " + f);
                assertFalse(session.player().getDead());
            }
            assertEquals(0, SszBossInputAuthorTool.Encounter.GHZ.observe().health());
        }
        assertThrows(IllegalArgumentException.class, () -> SszBossInputAuthorTool.run(
                rom, settings, input, 5090, SszBossInputAuthorTool.Encounter.GHZ, output, 240));
        assertEquals(rows, Files.readAllLines(output.resolve("state.csv")), "existing evidence is preserved");
    }

    private static String withoutInput(String row) {
        return row.substring(0, row.lastIndexOf(','));
    }
}
