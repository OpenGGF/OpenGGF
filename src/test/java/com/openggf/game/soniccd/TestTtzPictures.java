package com.openggf.game.soniccd;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Native screenshot checks of the ten Tidal Tempest stages ({@link TtzPicture}), one test method per stage
 * so each verdict is reported separately. A headless GL boot cannot be repeated in a JVM (GL 0x502), so
 * the class boots once and loads each stage into the running session. Skips without the user media.
 */
@EnabledOnOs(OS.LINUX)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestTtzPictures {
    private TtzPicture.Session session;

    @BeforeAll
    void boot() throws Exception {
        CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        try {
            session = new TtzPicture.Session(disc, CdStagePayload.R41A);
        } catch (Exception | Error e) {
            disc.close();
            throw e;
        }
    }

    @AfterAll
    void close() throws Exception {
        if (session != null) {
            CdDiscDataSource disc = session.disc;
            session.close();
            disc.close();
        }
    }

    @Test
    void r41aMatchesItsNativeScreenshots() throws Exception {
        TtzPicture.run(session, CdStagePayload.R41A);
    }

    @Test
    void r41bMatchesItsNativeScreenshots() throws Exception {
        TtzPicture.run(session, CdStagePayload.R41B);
    }

    @Test
    void r41cMatchesItsNativeScreenshots() throws Exception {
        TtzPicture.run(session, CdStagePayload.R41C);
    }

    @Test
    void r41dMatchesItsNativeScreenshots() throws Exception {
        TtzPicture.run(session, CdStagePayload.R41D);
    }

    @Test
    void r42aMatchesItsNativeScreenshots() throws Exception {
        TtzPicture.run(session, CdStagePayload.R42A);
    }

    @Test
    void r42bMatchesItsNativeScreenshots() throws Exception {
        TtzPicture.run(session, CdStagePayload.R42B);
    }

    @Test
    void r42cMatchesItsNativeScreenshots() throws Exception {
        TtzPicture.run(session, CdStagePayload.R42C);
    }

    @Test
    void r42dMatchesItsNativeScreenshots() throws Exception {
        TtzPicture.run(session, CdStagePayload.R42D);
    }

    @Test
    void r43cMatchesItsNativeScreenshots() throws Exception {
        TtzPicture.run(session, CdStagePayload.R43C);
    }

    @Test
    void r43dMatchesItsNativeScreenshots() throws Exception {
        TtzPicture.run(session, CdStagePayload.R43D);
    }

    /** Loads all ten stages into the same session and runs each for 300 frames (see {@link TtzStagesRun}). */
    @Test
    void everyStageScrollsCyclesAndHasWaterForThreeHundredFrames() throws Exception {
        TtzStagesRun.run(session);
    }

    /**
     * The comparison must be able to fail: with the camera one pixel off from level frame 100 on, the
     * Act 2 Past end shot (100% when right) must drop below the bar.
     */
    @Test
    void comparisonFailsWhenTheCameraIsOnePixelOff() throws Exception {
        var results = TtzPicture.check(session, CdStagePayload.R42B, 1);
        var end = results.stream().filter(r -> r.shot().equals("end")).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(end.passes(), "a skewed camera was not detected: " + end);
    }
}
