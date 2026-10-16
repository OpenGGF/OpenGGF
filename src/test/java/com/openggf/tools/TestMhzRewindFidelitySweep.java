package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dense capture/restore/replay over the authored solo Sonic MHZ1 routes where restores used to
 * drift: the mushroom cap bounce (320px 3903), the dropping platform and rider launches (320px
 * 5550, 5790), and the Mushmeanie shell and curled vine (800px 6827-6880). Every second input is a
 * checkpoint with a thirty-input replay compared row by row and key by key.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestMhzRewindFidelitySweep {
    @ParameterizedTest
    @CsvSource({
            "mhz1-sonic-fresh-320,320,3860,3960",
            "mhz1-sonic-fresh-320,320,5520,5820",
            "mhz1-sonic-cold-complete-800,800,6800,6900"
    })
    void restoresReplayTheLiveRouteExactly(String name, int width, int from, int to) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(
                Path.of("src/test/resources/routes/s3k/" + name + ".bk2"));
        var inputs = new ArrayList<Bk2FrameInput>();
        for (int i = 0; i < movie.getFrameCount(); i++) inputs.add(movie.getFrame(i));
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0, settings);
            var report = RewindFidelitySweep.sweep(session, inputs, from, to, 2, 30, 4);
            assertTrue(report.checks() >= (to - from) / 4, () -> "too few checkpoints: " + report);
            assertTrue(report.clean(), report::toString);
        }
    }
}
