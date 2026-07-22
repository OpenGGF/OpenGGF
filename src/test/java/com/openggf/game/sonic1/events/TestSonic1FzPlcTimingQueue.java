package com.openggf.game.sonic1.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic1FzPlcTimingQueue {

    @Test
    void modelsQueuedSbz2AndFzBossNemesisWork() {
        Sonic1FzPlcTimingQueue queue = new Sonic1FzPlcTimingQueue();

        queue.resetForFinalZoneGameplay();
        assertEquals(107, queue.framesRemaining());

        for (int i = 0; i < 54; i++) {
            queue.tickVBlank();
        }
        queue.enqueueFzBossCue();

        assertEquals(226, queue.framesRemaining());
    }

    @Test
    void eachGameplayVblankConsumesOneLogicalPlcSlice() {
        Sonic1FzPlcTimingQueue queue = new Sonic1FzPlcTimingQueue();
        queue.resetForFinalZoneGameplay();

        queue.tickVBlank();

        assertEquals(106, queue.framesRemaining());
    }
}
