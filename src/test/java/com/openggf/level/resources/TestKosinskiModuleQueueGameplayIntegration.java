package com.openggf.level.resources;

import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestKosinskiModuleQueueGameplayIntegration {

    @Test
    void gameplayFrameProcessesSessionQueueOnceAndRewindRestoresItsPhase() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(4, 0)
                .build();
        KosinskiModuleQueue queue = fixture.gameplayMode().getKosinskiModuleQueue();
        assertSame(queue, TestEnvironment.objectServices().kosinskiModuleQueue());
        assertTrue(queue.enqueue(GameServices.rom().getRom(), 0x0D6A62, 0x520 * 0x20));

        var before = fixture.gameplayMode().getRewindRegistry().capture();
        assertTrue(before.containsKey(queue.key()),
                "gameplay-scoped KosM state must be part of atomic rewind capture");

        fixture.stepFrame(false, false, false, false, false);
        assertEquals(KosinskiModuleQueue.Phase.DECOMPRESSION_IN_PROGRESS, queue.phase());
        assertEquals(0x81, queue.modulesLeftRaw(),
                "LevelFrameStep must invoke exactly the native start phase once");

        fixture.gameplayMode().getRewindRegistry().restore(before);
        assertEquals(KosinskiModuleQueue.Phase.READY_TO_START, queue.phase());
        assertEquals(1, queue.modulesLeftRaw());
    }
}
