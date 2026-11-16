package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TestAizComposedRewindState {
    @Test
    void lastOffsetSurvivesIndependentlyOfCurrentOffsetAndFlag() throws Exception {
        var source = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        var sourceShake = shake(source);
        source.triggerScreenShake(14);
        sourceShake.setup(1, false);
        sourceShake.setup(2, false);
        assertEquals(12, sourceShake.flag());
        assertEquals(4, sourceShake.offset());
        assertEquals(-4, sourceShake.lastOffset());
        byte[] saved = source.captureAizState();
        var target = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        target.triggerScreenShake(3);
        shake(target).setup(9, false);
        target.restoreAizState(saved);
        assertEquals(12, shake(target).flag());
        assertEquals(4, shake(target).offset());
        assertEquals(-4, shake(target).lastOffset(), "independent previous ROM offset");
        assertArrayEquals(saved, target.captureAizState());
    }

    private static com.openggf.game.sonic3k.runtime.S3kScreenShake shake(Sonic3kAIZEvents events) throws Exception {
        var field = Sonic3kAIZEvents.class.getDeclaredField("screenShake");
        field.setAccessible(true);
        return (com.openggf.game.sonic3k.runtime.S3kScreenShake) field.get(events);
    }

    @Test
    void publicationAndAllShakeWordsRoundTripAndRejectMalformedInputAtomically() {
        var source = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        source.triggerScreenShake(-1);
        source.setScreenShakeOffsetYRaw(3);
        source.setScreenShakeAppliedOffsetYRaw(2);
        byte[] good = source.captureAizState();
        var target = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        target.restoreAizState(good);
        assertArrayEquals(good, target.captureAizState());
        for (int index : new int[] {0, 4, 8, good.length - 12, good.length - 8, good.length - 4}) {
            byte[] bad = good.clone();
            ByteBuffer.wrap(bad).putInt(index, Integer.MAX_VALUE);
            assertFalse(Sonic3kAIZEvents.hasValidAizState(bad));
            assertThrows(IllegalArgumentException.class, () -> target.restoreAizState(bad));
            assertArrayEquals(good, target.captureAizState(), "rejected payload leaves all words intact");
        }
        for (int length : new int[] {0, 11, good.length - 1, good.length + 1}) {
            byte[] bad = Arrays.copyOf(good, length);
            assertThrows(IllegalArgumentException.class, () -> target.restoreAizState(bad));
            assertArrayEquals(good, target.captureAizState());
        }
    }
}
