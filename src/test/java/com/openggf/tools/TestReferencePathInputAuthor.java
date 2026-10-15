package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** The reference-path author follows a reference, and finds it again after a forced divergence. */
@RequiresRom(SonicGame.SONIC_3K)
class TestReferencePathInputAuthor {
    @Test
    void recoversTheReferencePathAfterADeliberateDivergenceAndReplaysUninterrupted() throws Exception {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(
                Path.of("src/test/resources/routes/s3k/ssz1-sonic-tails-cold-complete-320.bk2"));
        var reference = ReferencePathInputAuthor.referencePath(rom, settings, 10, 0, movie, 2400);
        var inputs = new ArrayList<Bk2FrameInput>();
        int endX, endY;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 10, 0, settings);
            // Up to the open bridge the run is the reference run itself.
            for (int f = 0; f <= 1850; f++) {
                session.step(movie.getFrame(f)); session.renderFrame();
                inputs.add(movie.getFrame(f));
            }
            // Forty held-Left inputs the reference never pressed here.
            for (int n = 0; n < 40; n++) {
                var pad = new Bk2FrameInput(0, 4, 0, false, "");
                session.step(pad); session.renderFrame();
                inputs.add(pad);
            }
            var author = new ReferencePathInputAuthor(session, inputs, reference, new StringBuilder());
            var result = author.seek(movie, 1850, 2050, ReferencePathInputAuthor.Params.DEFAULT, () -> false);
            assertEquals(ReferencePathInputAuthor.Stop.REACHED, result.stop(), result.toString());
            assertTrue(result.progress() >= 2050, result.toString());
            assertEquals(inputs.size(), result.inputs());
            assertFalse(session.player().getDead());
            // The reference runs right along this stretch (X 1146 at 1850, 1743 at 2050).
            assertTrue(session.player().getCentreX() >= 1600, "P1 is past the stretch: " + session.player().getCentreX());
            endX = session.player().getCentreX();
            endY = session.player().getCentreY();
        }
        // Restores never leak into the recorded inputs: a fresh uninterrupted replay lands identically.
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 10, 0, settings);
            for (var pad : inputs) {
                session.step(pad); session.renderFrame();
                assertFalse(session.player().getDead());
            }
            assertEquals(endX, session.player().getCentreX());
            assertEquals(endY, session.player().getCentreY());
        }
    }
}
