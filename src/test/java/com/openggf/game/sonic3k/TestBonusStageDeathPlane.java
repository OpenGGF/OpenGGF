package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBonusStageDeathPlane {
    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void disabledDeathPlaneDoesNotReplaceTheGumballExitTrigger() {
        var provider = new Sonic3kBonusStageCoordinator();
        SessionManager.getCurrentGameplayMode().setActiveBonusStageProvider(provider);
        var events = (Sonic3kLevelEventManager) GameModuleRegistry.getCurrent().getLevelEventProvider();
        events.initLevel(Sonic3kZoneIds.ZONE_GUMBALL, 0);
        var player = new TestablePlayableSprite("knuckles", (short) 0x100, (short) 0x340);

        // Obj_GumballMachine sets Disable_death_plane; Player_Boundary_CheckBottom
        // returns without setting Restart_level_flag. Only loc_61076 owns the exit.
        assertTrue(events.interceptPitDeath(player));
        assertFalse(provider.isStageComplete(), "suppressing pit death must not request a stage exit");
        assertFalse(player.getDead());
    }
}
