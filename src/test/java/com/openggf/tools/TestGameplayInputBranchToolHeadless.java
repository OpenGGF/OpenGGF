package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestGameplayInputBranchToolHeadless {
    @Test void branchesMatchEachOtherAndAFreshUninterruptedReplay(@TempDir Path dir) throws Exception {
        var rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        var input = dir.resolve("held-input.bk2");
        InputLogAuthorTool.author("240 R+A", input, "s3k");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        var output = dir.resolve("branches");
        GameplayInputBranchTool.run(rom, "s3k", 9, 0, settings, input, 200, output,
                List.of("10 R+A;30 R", "10 R+A;30 R"));
        assertEquals(Files.readString(output.resolve("variant-0.csv")),
                Files.readString(output.resolve("variant-1.csv")));
        assertArrayEquals(Files.readAllBytes(output.resolve("variant-0-230.png")),
                Files.readAllBytes(output.resolve("variant-1-230.png")));
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(output.resolve("variant-0.bk2"));
        var rows = new StringBuilder(GameplayCaptureSession.stateHeader()).append('\n');
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                var pad = movie.getFrame(frame);
                session.step(pad);
                session.render();
                rows.append(session.stateLine(frame, pad)).append('\n');
            }
        }
        assertEquals(rows.toString(), Files.readString(output.resolve("variant-0.csv")),
                "a restored branch must agree with a fresh controller-only replay");
        assertThrows(IllegalArgumentException.class, () -> GameplayInputBranchTool.run(
                rom, "s3k", 9, 0, settings, input, 200, output, List.of("1 R")),
                "existing authored products must not be overwritten");
    }
}
