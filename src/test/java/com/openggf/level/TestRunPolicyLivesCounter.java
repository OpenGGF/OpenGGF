package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.HudLabel;
import com.openggf.level.objects.HudProfile;
import com.openggf.level.objects.HudProfileAccess;
import com.openggf.level.objects.HudRow;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A run whose policy draws no lives counter loads its levels with the lives row hidden. */
@RequiresRom(SonicGame.SONIC_1)
class TestRunPolicyLivesCounter {
    @AfterEach
    void tearDown() {
        var session = SessionManager.getCurrentGameplayMode();
        if (session != null) {
            session.endGameplayRun();
        }
    }

    @Test
    void fastRestartRunsHideOnlyTheLivesRow() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        LevelManager level = GameServices.level();
        assertEquals(HudProfile.stock(), installed(level), "the campaign keeps the stock HUD");

        SessionManager.getCurrentGameplayMode().beginGameplayRun(
                GameplayRunPolicy.isolatedAct().withFastRestarts(), 0, 0);
        level.loadCurrentLevel();

        HudProfile hidden = installed(level);
        assertEquals(HudProfile.stock().rows().size(), hidden.rows().size());
        for (int i = 0; i < hidden.rows().size(); i++) {
            HudRow row = hidden.rows().get(i);
            HudRow stock = HudProfile.stock().rows().get(i);
            if (row.label() == HudLabel.LIVES) {
                assertFalse(row.visible(), "the lives counter is hidden");
            } else {
                assertEquals(stock, row, "every other row is unchanged");
                assertTrue(row.visible());
            }
        }
    }

    @Test
    void aPolicyThatKeepsTheCounterKeepsTheProfile() {
        HudProfile stock = HudProfile.stock();
        assertEquals(stock, HudProfileAccess.forRunPolicy(stock, GameplayRunPolicy.isolatedAct()));
        assertEquals(stock, HudProfileAccess.forRunPolicy(stock, GameplayRunPolicy.stock()));
    }

    private static HudProfile installed(LevelManager level) {
        return HudProfileAccess.current(level.hudRenderManager);
    }
}
