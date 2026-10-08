package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Reproduces the widescreen seam through actual DDZ render passes, including retained GL state. */
@RequiresRom(SonicGame.SONIC_3K)
class TestDdzBackgroundWrapCapture {
    @Test void chaseCloudsRepeatWithoutAnUnrenderedFboColumn() throws Exception {
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "", "off", null, null, null,
                "1111111", false, false, null, null);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/ddz-super-fresh-800.bk2"));
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 12, 0, settings);
            for (int frame = 0; frame <= 8400; frame++) session.step(movie.getFrame(frame));
            session.render();
            // No sprites intersect this cloud strip; repeat rendering must preserve its pixels.
            for (int pass = 0; pass < 3; pass++) {
                var image = session.render(false);
                for (int y = 160; y < 200; y++) for (int x = 128; x < 160; x++)
                    assertEquals(image.argb(x, y), image.argb(x + 512, y),
                            "512px plane period at x=" + x + " y=" + y + " pass=" + pass);
            }
        }
    }
}
