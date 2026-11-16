package com.openggf.game.rewind;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMgzScreenPublication {
    @AfterEach void close() { TestEnvironment.resetAll(); }

    @Test void repeatedProjectionCannotConsumeObjectShakeRequest() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_MGZ, 0).build();
        var state = S3kRuntimeStates.currentMgz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var handler = GameServices.parallax().getHandler(Sonic3kZoneIds.ZONE_MGZ);
        state.requestContinuousScreenShake();
        int[] first = new int[224];
        handler.update(first, fixture.camera().getX(), fixture.camera().getY(), 1, 0);
        int shake = handler.getShakeOffsetY();
        int[] second = new int[224];
        handler.update(second, fixture.camera().getX(), fixture.camera().getY(), 1, 0);
        assertEquals(shake, handler.getShakeOffsetY(), "drawing twice must not publish another shake generation");
        assertArrayEquals(first, second);
    }
    @Test void registeredRestorePreservesShakeAndForwardPublication() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_MGZ, 0)
                .withSkippedZoneIntro().build();
        var manager = (com.openggf.game.sonic3k.Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider();
        var events = manager.getMgzEvents();
        events.requestContinuousScreenShake();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        byte[] saved = events.captureMgzState();
        fixture.stepFrame(false, false, false, false, false);
        byte[] after = events.captureMgzState();
        for (int branch = 0; branch < 2; branch++) {
            registry.restore(before);
            assertArrayEquals(saved, events.captureMgzState(), "rebuild cannot consume setup or cloud phase");
            fixture.stepFrame(false, false, false, false, false);
            assertArrayEquals(after, events.captureMgzState(), "ordinary suffix after restore");
        }
    }

}
