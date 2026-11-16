package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TestAizComposedRewindState {
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
