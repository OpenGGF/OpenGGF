package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The atlas's primitive pattern-sample table must behave like the map it replaced. */
class TestPatternSampleTable {

    private static byte[] pixels(long seed) {
        byte[] pixels = new byte[64];
        Random random = new Random(seed);
        for (int i = 0; i < pixels.length; i++) {
            // Includes high-nibble bytes: samples keep only the 4-bit colour index.
            pixels[i] = (byte) random.nextInt(256);
        }
        return pixels;
    }

    @Test
    void sampleEqualsThePresentationPacking() {
        PatternSampleTable table = new PatternSampleTable();
        byte[] art = pixels(7);
        table.put(0x1234, art);
        assertEquals(SpritePresentation.patternVersion(art), table.get(0x1234));
        assertNull(table.get(0x1235));
    }

    @Test
    void readersShareOneInstanceUntilTheSampleChanges() {
        PatternSampleTable table = new PatternSampleTable();
        byte[] art = pixels(1);
        table.put(5, art);
        var first = table.get(5);
        table.put(5, art.clone());
        assertSame(first, table.get(5), "an equal rewrite keeps the materialized version");
        byte[] changed = art.clone();
        changed[63] = (byte) ((changed[63] + 1) & 15);
        table.put(5, changed);
        var second = table.get(5);
        assertNotEquals(first, second);
        assertEquals(SpritePresentation.patternVersion(changed), second);
    }

    @Test
    void frozenVersionIsReturnedAsGiven() {
        PatternSampleTable table = new PatternSampleTable();
        var frozen = new SpritePresentation.PatternVersion(1, 2, 3, 4);
        table.put(9, frozen);
        assertSame(frozen, table.get(9));
        table.put(9, pixels(3));
        assertEquals(SpritePresentation.patternVersion(pixels(3)), table.get(9));
    }

    @Test
    void matchesAHashMapAcrossGrowthCollisionsAndRemoval() {
        PatternSampleTable table = new PatternSampleTable();
        Map<Integer, SpritePresentation.PatternVersion> model = new HashMap<>();
        Random random = new Random(42);
        for (int step = 0; step < 50_000; step++) {
            // Clustered ids (multiples of 1024 collide under small masks) and a few negatives.
            int id = switch (random.nextInt(3)) {
                case 0 -> random.nextInt(600);
                case 1 -> random.nextInt(64) * 1024;
                default -> -random.nextInt(50);
            };
            int action = random.nextInt(10);
            if (action < 6) {
                byte[] art = pixels(random.nextLong());
                table.put(id, art);
                model.put(id, SpritePresentation.patternVersion(art));
            } else if (action < 9) {
                table.remove(id);
                model.remove(id);
            } else {
                assertEquals(model.get(id), table.get(id), "step " + step);
            }
            assertEquals(model.size(), table.size(), "step " + step);
        }
        for (var entry : model.entrySet()) {
            assertEquals(entry.getValue(), table.get(entry.getKey()));
        }
        table.clear();
        assertEquals(0, table.size());
        for (int id : model.keySet()) {
            assertNull(table.get(id));
        }
    }

    @Test
    void rewritingResidentSamplesAllocatesNothing() {
        var threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        PatternSampleTable table = new PatternSampleTable();
        byte[][] art = {pixels(1), pixels(2), pixels(3)};
        for (int id = 0; id < 200; id++) {
            table.put(id, art[id % 3]);
        }
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int round = 0; round < 1000; round++) {
            for (int id = 0; id < 200; id++) {
                table.put(id, art[(id + round) % 3]);
            }
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        // 200,000 writes; the boxed-key map allocated at least an Integer and a record per write.
        assertTrue(allocated < 200_000, "allocated " + allocated + " bytes");
    }
}
