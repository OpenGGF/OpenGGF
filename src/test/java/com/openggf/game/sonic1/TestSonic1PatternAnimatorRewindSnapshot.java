package com.openggf.game.sonic1;

import com.openggf.data.RomByteReader;
import com.openggf.game.OscillationManager;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip rewind snapshot tests for {@link Sonic1PatternAnimator} (Track E.2).
 *
 * <p>ROM-dependent tests verify that a full capture → tick → restore cycle
 * returns the animator to its original counter state. ROM-free tests verify
 * that the key and empty snapshot shape are stable.
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestSonic1PatternAnimatorRewindSnapshot {

    @BeforeEach
    void setUp() {
        OscillationManager.reset();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void keyIsPatternAnimator() throws IOException {
        Sonic1PatternAnimator anim = buildAnimator(Sonic1Constants.ZONE_GHZ);
        assertEquals("pattern-animator", anim.key());
    }

    @Test
    void captureProducesNoScriptCounters() throws IOException {
        Sonic1PatternAnimator anim = buildAnimator(Sonic1Constants.ZONE_GHZ);
        PatternAnimatorSnapshot snap = anim.capture();
        assertEquals(0, snap.scriptCounters().length,
                "S1 uses inner handlers, not AniPLC scripts");
        assertNotNull(snap.extra(), "S1 retains the tiles its handlers last published");
    }

    @Test
    void captureHandlerCountMatchesHandlerCount() throws IOException {
        // GHZ has 3 handlers: waterfall, big flower, small flower
        Sonic1PatternAnimator anim = buildAnimator(Sonic1Constants.ZONE_GHZ);
        PatternAnimatorSnapshot snap = anim.capture();
        assertEquals(3, snap.handlerCounters().length);
    }

    @Test
    void roundTripGhzHandlerCounters() throws IOException {
        Sonic1PatternAnimator anim = buildAnimator(Sonic1Constants.ZONE_GHZ);
        PatternAnimatorSnapshot before = anim.capture();

        // Tick a few frames to advance all counters
        for (int i = 0; i < 20; i++) {
            anim.update();
        }

        PatternAnimatorSnapshot after20 = anim.capture();
        // Counters should have changed
        boolean changed = false;
        for (int i = 0; i < before.handlerCounters().length; i++) {
            if (before.handlerCounters()[i].slot0() != after20.handlerCounters()[i].slot0()
                    || before.handlerCounters()[i].slot1() != after20.handlerCounters()[i].slot1()) {
                changed = true;
                break;
            }
        }
        assertTrue(changed, "Handler counters must advance after ticking");

        // Restore to before-snapshot and verify counters match
        anim.restore(before);
        PatternAnimatorSnapshot restored = anim.capture();

        for (int i = 0; i < before.handlerCounters().length; i++) {
            assertEquals(before.handlerCounters()[i].slot0(),
                    restored.handlerCounters()[i].slot0(),
                    "slot0 (timer) mismatch at handler " + i);
            assertEquals(before.handlerCounters()[i].slot1(),
                    restored.handlerCounters()[i].slot1(),
                    "slot1 (frameCounter) mismatch at handler " + i);
        }
    }

    @Test
    void roundTripMzHandlerCounters() throws IOException {
        // MZ has 3 handlers: lava surface, magma body, torch
        Sonic1PatternAnimator anim = buildAnimator(Sonic1Constants.ZONE_MZ);
        PatternAnimatorSnapshot before = anim.capture();

        for (int frame = 1; frame <= 25; frame++) {
            OscillationManager.update(frame);
            anim.update();
        }

        anim.restore(before);
        PatternAnimatorSnapshot restored = anim.capture();

        assertEquals(before.handlerCounters().length, restored.handlerCounters().length);
        for (int i = 0; i < before.handlerCounters().length; i++) {
            PatternAnimatorSnapshot.HandlerCounter exp = before.handlerCounters()[i];
            PatternAnimatorSnapshot.HandlerCounter got = restored.handlerCounters()[i];
            assertEquals(exp.slot0(), got.slot0(), "slot0 mismatch at handler " + i);
            assertEquals(exp.slot1(), got.slot1(), "slot1 mismatch at handler " + i);
            assertEquals(exp.slot2(), got.slot2(), "slot2 (currentFrame) mismatch at handler " + i);
        }
    }

    @Test
    void roundTripSbzHandlerCounters() throws IOException {
        // SBZ has 2 handlers: smoke puff 1, smoke puff 2
        Sonic1PatternAnimator anim = buildAnimator(Sonic1Constants.ZONE_SBZ);
        PatternAnimatorSnapshot before = anim.capture();

        for (int i = 0; i < 30; i++) {
            anim.update();
        }

        anim.restore(before);
        PatternAnimatorSnapshot restored = anim.capture();

        assertEquals(2, restored.handlerCounters().length);
        for (int i = 0; i < 2; i++) {
            PatternAnimatorSnapshot.HandlerCounter exp = before.handlerCounters()[i];
            PatternAnimatorSnapshot.HandlerCounter got = restored.handlerCounters()[i];
            // SmokePuffAnim packs: slot0=frameTimer, slot1=intervalTimer, slot2=frameCounter
            assertEquals(exp.slot0(), got.slot0(), "frameTimer mismatch at smoke " + i);
            assertEquals(exp.slot1(), got.slot1(), "intervalTimer mismatch at smoke " + i);
            assertEquals(exp.slot2(), got.slot2(), "frameCounter mismatch at smoke " + i);
        }
    }

    @Test
    void publishedPatternsRestoreImmediatelyWithoutWaitingForAnotherAnimationTick() throws IOException {
        int[] zones = {Sonic1Constants.ZONE_GHZ, Sonic1Constants.ZONE_MZ,
                Sonic1Constants.ZONE_SBZ, Sonic1Constants.ZONE_ENDZ};
        for (int caseIndex = 0; caseIndex < zones.length; caseIndex++) {
            TestLevel level = new TestLevel(1024);
            Sonic1PatternAnimator animator = buildAnimator(level, zones[caseIndex]);
            for (int frame = 1; frame <= 5; frame++) {
                OscillationManager.update(frame);
                animator.update();
            }
            var checkpoint = animator.capture();
            byte[] published = patternPixels(level);
            // Observe a real publication change, rather than choosing an interval
            // that can finish on the same animation frame (both SBZ smoke cycles did).
            for (int frame = 6; frame <= 505 && Arrays.equals(published, patternPixels(level)); frame++) {
                OscillationManager.update(frame);
                animator.update();
            }
            assertFalse(Arrays.equals(published, patternPixels(level)),
                    "The forward interval must change actual published art for zone " + zones[caseIndex]);
            animator.restore(checkpoint);
            assertArrayEquals(published, patternPixels(level),
                    "Restore must republish checkpoint art before the next tick for zone " + zones[caseIndex]);
            assertArrayEquals(checkpoint.handlerCounters(), animator.capture().handlerCounters());
            assertArrayEquals(checkpoint.extra(), animator.capture().extra());
        }
    }

    @Test
    void malformedPublishedPatternShapeIsRejectedBeforeCountersOrPixelsChange() throws IOException {
        TestLevel level = new TestLevel(1024);
        Sonic1PatternAnimator animator = buildAnimator(level, Sonic1Constants.ZONE_GHZ);
        var before = animator.capture();
        byte[] pixels = patternPixels(level);
        byte[] invalid = before.extra().clone();
        java.nio.ByteBuffer.wrap(invalid).putInt(0);
        var changedCounters = before.handlerCounters().clone();
        changedCounters[0] = new PatternAnimatorSnapshot.HandlerCounter(111, 222, 0);
        assertThrows(IllegalArgumentException.class, () -> animator.restore(new PatternAnimatorSnapshot(
                before.scriptCounters(), changedCounters, invalid)));
        assertArrayEquals(before.handlerCounters(), animator.capture().handlerCounters());
        assertArrayEquals(pixels, patternPixels(level));
        assertThrows(IllegalArgumentException.class, () -> animator.restore(new PatternAnimatorSnapshot(
                before.scriptCounters(), changedCounters, Arrays.copyOf(before.extra(), before.extra().length - 1))));
        assertArrayEquals(pixels, patternPixels(level));
        byte[] wrongDestination = before.extra().clone();
        java.nio.ByteBuffer.wrap(wrongDestination).putInt(Integer.BYTES, -1);
        assertThrows(IllegalArgumentException.class, () -> animator.restore(new PatternAnimatorSnapshot(
                before.scriptCounters(), changedCounters, wrongDestination)));
        assertArrayEquals(before.handlerCounters(), animator.capture().handlerCounters());
        assertArrayEquals(pixels, patternPixels(level));
    }

    private static byte[] patternPixels(Level level) {
        byte[] result = new byte[level.getPatternCount() * Pattern.PATTERN_SIZE_IN_MEM];
        for (int i = 0; i < level.getPatternCount(); i++)
            level.getPattern(i).copyInto(result, i * Pattern.PATTERN_SIZE_IN_MEM);
        return result;
    }

    // ===== Helpers =====

    private static Sonic1PatternAnimator buildAnimator(int zone) throws IOException {
        int capacity = 1024;
        TestLevel level = new TestLevel(capacity);
        return buildAnimator(level, zone);
    }

    private static Sonic1PatternAnimator buildAnimator(Level level, int zone) throws IOException {
        RomByteReader reader = RomByteReader.fromRom(
                com.openggf.tests.TestEnvironment.currentRom());
        return new Sonic1PatternAnimator(reader, level, zone);
    }

    /** Minimal Level stub — patterns only, no ROM or GL. */
    private static final class TestLevel implements Level {
        private Pattern[] patterns;

        private TestLevel(int size) {
            patterns = new Pattern[size];
            for (int i = 0; i < size; i++) patterns[i] = new Pattern();
        }

        @Override public int getPaletteCount() { return 0; }
        @Override public Palette getPalette(int i) { throw new UnsupportedOperationException(); }
        @Override public int getPatternCount() { return patterns.length; }
        @Override public Pattern getPattern(int i) { return patterns[i]; }
        @Override public void ensurePatternCapacity(int n) {
            if (n <= patterns.length) return;
            Pattern[] e = Arrays.copyOf(patterns, n);
            for (int i = patterns.length; i < n; i++) e[i] = new Pattern();
            patterns = e;
        }
        @Override public int getChunkCount() { return 0; }
        @Override public Chunk getChunk(int i) { throw new UnsupportedOperationException(); }
        @Override public int getBlockCount() { return 0; }
        @Override public Block getBlock(int i) { throw new UnsupportedOperationException(); }
        @Override public SolidTile getSolidTile(int i) { throw new UnsupportedOperationException(); }
        @Override public Map getMap() { throw new UnsupportedOperationException(); }
        @Override public List<ObjectSpawn> getObjects() { return List.of(); }
        @Override public List<RingSpawn> getRings() { return List.of(); }
        @Override public RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return 0; }
        @Override public int getMaxX() { return 0; }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 0; }
        @Override public int getZoneIndex() { return 0; }
    }
}
