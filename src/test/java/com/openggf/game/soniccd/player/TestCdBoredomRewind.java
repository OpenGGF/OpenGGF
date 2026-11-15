package com.openggf.game.soniccd.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code boredTimer} is rewind state: the stage signals' snapshot ({@link CdPlayerStage#signalsRewindAdapter()}) carries
 * it, so a restore puts back the V-INT count the give-up compares (Sonic/Main.asm:24-25).
 */
class TestCdBoredomRewind {
    @Test
    void theTimerIsCapturedAndRestored() {
        CdPlayerStage stage = new CdPlayerStage(() -> null);
        stage.boredom().restore(1);
        for (int i = 0; i < 99; i++) stage.boredom().vIntTick();
        var snapshot = stage.signalsRewindAdapter().capture();
        assertEquals(100, snapshot.boredTimer());
        for (int i = 0; i < 50; i++) stage.boredom().vIntTick();
        assertEquals(150, stage.boredom().timer());
        stage.signalsRewindAdapter().restore(snapshot);
        assertEquals(100, stage.boredom().timer());
    }

    @Test
    void aStoppedTimerDoesNotCount() {
        CdBoredom boredom = new CdBoredom();
        boredom.vIntTick();                    // tst.w boredTimer.w; beq.s (Level/Main.asm:986-987)
        assertEquals(0, boredom.timer());
        boredom.restore(0xFFFF);
        boredom.vIntTick();                    // addq.w wraps the word
        assertEquals(0, boredom.timer());
    }
}
