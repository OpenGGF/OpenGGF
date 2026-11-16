package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.events.Sonic3kMGZEvents;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TestMgzComposedRewindState {
    @BeforeEach void reset() { TestEnvironment.resetAll(); }

    @Test void flagAndPublicationRoundTripWithoutRuntimeAdapterState() {
        var events = new Sonic3kMGZEvents();
        events.init(1);
        events.requestContinuousScreenShake();
        byte[] saved = events.captureMgzState();
        assertTrue(Sonic3kMGZEvents.hasValidMgzState(saved));
        events.clearScreenShakeFlag();
        assertFalse(Arrays.equals(saved, events.captureMgzState()));
        events.restoreMgzState(saved);
        assertArrayEquals(saved, events.captureMgzState());
    }

    @Test void backgroundInitAndAct1TransitionDoNotConsumeTimedShake() {
        TestEnvironment.configureGameModuleFixture(com.openggf.tests.rules.SonicGame.SONIC_3K);
        com.openggf.game.GameServices.camera().setFocusedSprite(new com.openggf.tests.TestablePlayableSprite(
                "sonic", (short) 0x1000, (short) 0x500));
        var events = new Sonic3kMGZEvents();
        events.init(0);
        events.writeScreenShakeFlag(14);
        events.initializeBackgroundAtLoad(0);
        assertEquals(14, flag(events));
        events.setBgRoutine(4);
        events.updateAfterDynamicObjects(0, 1);
        assertEquals(14, flag(events), "MGZ1BGE_Transition omits ShakeScreen_Setup");
        events.setBgRoutine(0);
        events.updateAfterDynamicObjects(0, 2);
        assertEquals(13, flag(events), "ordinary background dispatch does advance the countdown");
    }

    private static int flag(Sonic3kMGZEvents events) {
        byte[] bytes = events.captureMgzState();
        return ByteBuffer.wrap(bytes).getInt(bytes.length - 12);
    }

    @Test void malformedFramingAndWordsAreRejectedBeforeMutation() {
        var events = new Sonic3kMGZEvents();
        events.init(1);
        events.requestContinuousScreenShake();
        byte[] saved = events.captureMgzState();
        for (int index : new int[] {0, 4, 8, saved.length - 12, saved.length - 8, saved.length - 4}) {
            byte[] bad = saved.clone();
            ByteBuffer.wrap(bad).putInt(index, 0x10000);
            assertFalse(Sonic3kMGZEvents.hasValidMgzState(bad), "offset " + index);
            assertThrows(IllegalArgumentException.class, () -> events.restoreMgzState(bad));
            assertArrayEquals(saved, events.captureMgzState());
        }
        for (int length : new int[] {0, 11, saved.length - 1, saved.length + 1}) {
            byte[] bad = Arrays.copyOf(saved, length);
            assertFalse(Sonic3kMGZEvents.hasValidMgzState(bad));
            assertThrows(IllegalArgumentException.class, () -> events.restoreMgzState(bad));
            assertArrayEquals(saved, events.captureMgzState());
        }
    }
}
