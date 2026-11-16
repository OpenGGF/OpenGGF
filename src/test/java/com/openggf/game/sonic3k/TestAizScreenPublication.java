package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.scroll.SwScrlAiz;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestAizScreenPublication {
    @Test
    void waterTimerWritesTheFlagOnlyAtItsStartAndExpiry() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).withSkippedZoneIntro().build();
        try {
            var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getAizEvents();
            var water = GameServices.water();
            water.updateDynamic(0, 1, 0x2000, 0x400);
            water.updateDynamic(0, 1, 0x2850, 0x400);
            assertEquals(-1, events.getScreenShakeTimer());
            events.triggerScreenShake(0x10);
            water.updateDynamic(0, 1, 0x2850, 0x400);
            assertEquals(0x10, events.getScreenShakeTimer(), "a live timer cannot overwrite a later bomb flag");
            for (int i = 0; i < 178; i++) water.updateDynamic(0, 1, 0x2850, 0x400);
            assertEquals(0, events.getScreenShakeTimer(), "Obj_6E6E expiry clears that same flag");
        } finally {
            com.openggf.game.session.SessionManager.clear();
        }
    }

    @Test
    void rendererRecreationAndRepeatedSamplingCannotChooseAnotherWaveOrigin() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro().build();
        try {
            var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getAizEvents();
            fixture.camera().setLevelStarted(true);
            var first = new SwScrlAiz();
            int[] original = new int[224];
            first.update(original, 0x1800, 0x80, 0, 0);
            for (int i = 0; i < 12; i++) fixture.stepFrame(false, false, false, false, false);
            byte[] captured = events.captureAizState();
            int frame = GameServices.level().getFrameCounter();
            first.update(original, 0x1800, 0x80, frame, 0);
            var rebuilt = new SwScrlAiz();
            rebuilt.init(0, 0x1800, 0x80);
            int[] afterRebuild = new int[224];
            rebuilt.update(afterRebuild, 0x1800, 0x80, frame, 0);
            assertArrayEquals(original, afterRebuild, "recreation must retain every canopy scroll word");
            rebuilt.update(afterRebuild, 0x1800, 0x80, frame, 0);
            assertArrayEquals(captured, events.captureAizState(), "projection cannot consume publication state");
            events.restoreAizState(captured);
            rebuilt.init(0, 0x1800, 0x80);
            rebuilt.update(afterRebuild, 0x1800, 0x80, frame, 0);
            assertArrayEquals(original, afterRebuild, "restore and rebuild preserve the published wave");
        } finally {
            com.openggf.game.session.SessionManager.clear();
        }
    }
}
