package com.openggf.game.sonic3k;

import com.openggf.level.Pattern;
import com.openggf.level.animation.AniPlcScriptState;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic3kAniPlcPublicationQueue {
    @Test void countersAdvanceAtSubmissionButImmutableArtWaitsForPublication() {
        Pattern art = new Pattern(); art.setPixel(0, 0, (byte) 10);
        var script = new AniPlcScriptState((byte) 7, 0x200, new int[]{0, 0}, null, 1, new Pattern[]{art});
        var queue = new Sonic3kAniPlcPublicationQueue();
        List<byte[]> shown = new ArrayList<>();
        assertTrue(script.tickSubmission(tile -> queue.submit(0x200, script.copyFramePayload(tile))));
        assertEquals(7, script.getTimer()); assertEquals(1, script.getFrameIndex());
        assertTrue(shown.isEmpty());
        art.setPixel(0, 0, (byte) 1); // submitted payload must not alias later source/working storage
        queue.publish((tile, bytes) -> shown.add(bytes));
        assertEquals(0xA0, shown.getFirst()[0] & 255);
        queue.publish((tile, bytes) -> shown.add(bytes));
        assertEquals(1, shown.size(), "a second VBlank without submission changes nothing");
    }

    @Test void rewindRestoresPresentedArtAndPendingOrderWithoutPrematurePublication() {
        var queue = new Sonic3kAniPlcPublicationQueue();
        var presented = new HashMap<Integer, byte[]>(); presented.put(10, filled(1));
        queue.submit(10, filled(2)); queue.submit(10, filled(3));
        byte[] snapshot = queue.capture(presented::get);
        queue.publish(presented::put); assertArrayEquals(filled(3), presented.get(10));
        queue.restore(snapshot, presented::put); assertArrayEquals(filled(1), presented.get(10));
        List<Integer> order = new ArrayList<>();
        queue.publish((tile, payload) -> {order.add((int)payload[0]); presented.put(tile, payload);});
        assertEquals(List.of(2, 3), order); assertArrayEquals(filled(3), presented.get(10));
        queue.restore(snapshot, presented::put);
        assertArrayEquals(snapshot, queue.capture(presented::get), "restored branch is byte-identical");
    }

    @Test void snapshotBeforeFirstSubmissionRestoresOriginalPresentedPatterns() {
        var queue = new Sonic3kAniPlcPublicationQueue();
        queue.registerDestination(10, 2);
        var memory = new HashMap<Integer, byte[]>();
        memory.put(10, filled(1)); memory.put(11, filled(2));
        byte[] before = queue.capture(memory::get);
        byte[] next = new byte[64]; Arrays.fill(next, (byte) 7); queue.submit(10, next);
        java.util.function.BiConsumer<Integer, byte[]> publish = (tile, payload) -> {
            for (int i = 0; i < payload.length / 32; i++)
                memory.put(tile + i, Arrays.copyOfRange(payload, i * 32, (i + 1) * 32));
        };
        queue.publish(publish); assertArrayEquals(filled(7), memory.get(10));
        queue.restore(before, publish);
        assertArrayEquals(filled(1), memory.get(10)); assertArrayEquals(filled(2), memory.get(11));
        queue.publish(publish); assertArrayEquals(filled(1), memory.get(10));
        assertArrayEquals(before, queue.capture(memory::get));
    }

    @Test void captureReadsCurrentPresentedBytesIncludingOtherOwnersAndRejectsInvalidRanges() {
        var queue = new Sonic3kAniPlcPublicationQueue(); queue.submit(2, filled(7));
        byte[] snapshot = queue.capture(tile -> filled(4));
        List<byte[]> restored = new ArrayList<>(); queue.restore(snapshot, (tile, bytes) -> restored.add(bytes));
        assertArrayEquals(filled(4), restored.getFirst());
        assertThrows(IllegalArgumentException.class, () -> queue.submit(2048, filled(1)));
        assertThrows(IllegalArgumentException.class, () -> queue.submit(0, new byte[31]));
    }

    private static byte[] filled(int value) { byte[] b = new byte[32]; Arrays.fill(b, (byte)value); return b; }
}
