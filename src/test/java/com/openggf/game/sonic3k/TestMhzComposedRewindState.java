package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.events.Sonic3kMHZEvents;
import com.openggf.game.rewind.schema.ZoneEventSchemaSidecar;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TestMhzComposedRewindState {
    @Test void completePublicationAndThreeShakeWordsRestoreTogether() {
        var source = new Sonic3kMHZEvents();
        source.requestScreenShake(20);
        source.setScreenShakeOffsetForTest(-5);
        source.publishDeformOutputs(0x1234, 0x2345, 0x3456);
        source.publishEndBossArenaHelperXPositions(0x4300, new int[]{1,2,3,4,5,6});
        byte[] saved = source.captureMhzState();
        var target = new Sonic3kMHZEvents();
        target.restoreMhzState(saved);
        assertArrayEquals(saved, target.captureMhzState());
        assertEquals(20, target.getScreenShakeFlag());
        assertEquals(-5, target.getScreenShakeOffset());
        assertEquals(0x1234, target.publishedBgCameraX());
        assertEquals(6, target.endBossArenaSpikeX(5));
    }

    @Test void malformedPayloadsAreRejectedBeforeMutation() {
        var target = new Sonic3kMHZEvents();
        target.requestScreenShake(14);
        byte[] saved = target.captureMhzState();
        byte[] wideShake = saved.clone();
        ByteBuffer.wrap(wideShake).putInt(wideShake.length - 8, 0x10000);
        byte[] widePublication;
        var bad = new Sonic3kMHZEvents();
        try {
            var field = Sonic3kMHZEvents.class.getDeclaredField("loopAdjustedCameraX");
            field.setAccessible(true); field.setInt(bad, -1);
            widePublication = bad.captureMhzState();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        byte[] wrongVersion = saved.clone(); ByteBuffer.wrap(wrongVersion).putInt(4, 2);
        for (byte[] malformed : new byte[][] {null, new byte[0], Arrays.copyOf(saved, 11),
                Arrays.copyOf(saved, saved.length - 1), Arrays.copyOf(saved, saved.length + 1),
                wideShake, widePublication, wrongVersion, ZoneEventSchemaSidecar.capture(target)}) {
            assertFalse(Sonic3kMHZEvents.hasValidMhzState(malformed));
            assertThrows(IllegalArgumentException.class, () -> target.restoreMhzState(malformed));
            assertArrayEquals(saved, target.captureMhzState());
        }
    }
    @Test void loopHistoryRestoresAcrossCameraWrapAndActRebase() throws Exception {
        var events = new Sonic3kMHZEvents();
        var adjust = Sonic3kMHZEvents.class.getDeclaredMethod("adjustBgDuringLoop", int.class);
        adjust.setAccessible(true);
        assertEquals(0x427C, adjust.invoke(events, 0x427C));
        assertEquals(0x4280, adjust.invoke(events, 0x4080));
        byte[] saved = events.captureMhzState();
        assertEquals(0x42C0, adjust.invoke(events, 0x40C0));
        events.restoreMhzState(saved);
        assertEquals(0x42C0, adjust.invoke(events, 0x40C0));
        // MHZ1_BackgroundEvent rebases only Events_fg_0; retained history is
        // in the same scalar payload as the deformation words and shake.
        var actual = Sonic3kMHZEvents.class.getDeclaredField("loopActualCameraX");
        actual.setAccessible(true); actual.setInt(events, 0x40C0 - 0x4200 & 0xFFFF);
        assertEquals(0x42D0, adjust.invoke(events, 0x40D0 - 0x4200 & 0xFFFF));
    }
}
