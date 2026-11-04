package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.RecordingFrameDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A fresh S3K Level: entry ends its terrain wait inside LoadLevelLoadBlock's
 * loop. Its last iteration waits for VSync before the module queue retires
 * the final module (loc_7870, sonic3k.asm:9772-9782),
 * so the iteration that observes the release owns one counted V-int. Both
 * production drivers must count it exactly once.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFreshLevelReleaseVint {
    private static final int STEP_CAP = 800;

    @AfterEach
    void reset() {
        GameServices.configuration().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terrainWaitReleaseIterationCountsItsVint(boolean recording) throws Exception {
        Runnable step = start(recording);
        GameServices.level().requestZoneAndAct(1, 0);
        boolean released = false;
        for (int i = 0; i < STEP_CAP && !released; i++) {
            var before = title().capture();
            boolean terrainWait = before.freshLevelTerrainStarted() && before.freshLevelTransitionMode();
            int clock = GameServices.level().getObjectManager().getVblaCounter();
            step.run();
            if (terrainWait && !title().capture().freshLevelTransitionMode()) {
                released = true;
                assertEquals(clock + 1, GameServices.level().getObjectManager().getVblaCounter(),
                        "the loc_7870 iteration that retires the last module owns one V-int");
            }
        }
        assertTrue(released, "fresh title loop never released");
    }

    private static Runnable start(boolean recording) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        if (recording) {
            var driver = new RecordingFrameDriver(GameServices.sprites().getMainPlayable());
            return () -> driver.stepFrame(false, false, false, false, false);
        }
        var loop = new GameLoop(new InputHandler());
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        return loop::step;
    }

    private static Sonic3kTitleCardManager title() {
        return (Sonic3kTitleCardManager) GameServices.module().getTitleCardProvider();
    }
}
