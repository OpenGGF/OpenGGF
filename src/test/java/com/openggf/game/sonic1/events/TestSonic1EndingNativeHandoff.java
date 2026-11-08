package com.openggf.game.sonic1.events;

import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic1.scroll.Sonic1ZoneConstants;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic1EndingNativeHandoff {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void stoppedSonicKeepsTheForcedWaitPoseUntilItsNativeReplacement() {
        TestEnvironment.resetAll();
        var player = new TestPlayableSprite(); player.setCode("main");
        GameServices.sprites().addSprite(player); GameServices.camera().setFocusedSprite(player);
        var events = new Sonic1LevelEventManager(); events.initLevel(Sonic1ZoneConstants.ZONE_ENDING, 0);
        player.setCentreX((short) 0x620); events.update();
        player.setCentreX((short) 0x8f); events.update();
        player.setCentreX((short) 0xa0); events.update();
        int waiting = player.resolveAnimationId(CanonicalAnimation.WAIT);
        assertEquals(waiting, player.getAnimationId());
        assertEquals(waiting, player.getAnimationManager().captureRewindState().lastAnimationId());
        assertEquals(3, player.getMappingFrame());
        assertEquals(3, player.getAnimationTick());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertTrue(player.isObjectControlled());
        assertTrue(player.isNativeSlotPresent(), "the final Sonic_LoadGfx still belongs to this object");
        events.update();
        assertFalse(player.isNativeSlotPresent(), "EndSonic replaces the ordinary Sonic animation/DPLC owner");
        assertTrue(player.isHidden());
    }
}
