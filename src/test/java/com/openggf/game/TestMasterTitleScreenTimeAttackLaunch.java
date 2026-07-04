package com.openggf.game;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.timeattack.TimeAttackLaunchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: launching a time attack tears down the master title screen
 * (Engine.launchTimeAttack -> MasterTitleScreen.cleanup()) synchronously from
 * inside TimeAttackMenu.update. MasterTitleScreen.update must survive its
 * timeAttackMenu field being nulled mid-update (NPE at update():364 pre-fix).
 */
class TestMasterTitleScreenTimeAttackLaunch {
    @TempDir
    Path tempDir;

    @Test
    void synchronousTeardownDuringMenuUpdateDoesNotThrow() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        MasterTitleScreen screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_1, true);
        // Simulate Engine.launchTimeAttack's synchronous master-title teardown.
        screen.setTimeAttackLaunchStarter(request -> screen.cleanup());

        assertTrue(screen.handleTimeAttackMenuRequest(true), "time attack menu should open");
        screen.timeAttackMenuStateForTest().forceLaunchRequestForTest(
                new TimeAttackLaunchRequest("s1", 0, 0, "sonic", List.of()));

        InputHandler input = new InputHandler();
        assertDoesNotThrow(() -> screen.update(input));
        assertFalse(screen.isTimeAttackMenuOpenForTest(), "menu must be gone after teardown");
    }
}
