package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_3K)
class TestCnzScreenShakeDeath {
    @Test
    void deadPlayerStopsTheNextPublicationWithoutConsumingTheCountdown() throws Exception {
        var fixture = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(3, 1).build();
        var manager = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        var events = manager.getCnzEvents();
        events.triggerScreenShake(0x14);
        fixture.stepFrame(false, false, false, false, false);
        fixture.sprite().setDead(true);
        fixture.stepFrame(false, false, false, false, false);
        fixture.stepFrame(false, false, false, false, false);
        assertEquals(0, GameServices.parallax().getHandler(3).getShakeOffsetY(),
                "ShakeScreen_Setup suppresses the next displayed sample at routine >= 6");
        var timer = events.getClass().getDeclaredField("screenShakeTimer");
        timer.setAccessible(true);
        assertEquals(0x13, timer.getInt(events), "dead frames retain the flag");
    }
}
