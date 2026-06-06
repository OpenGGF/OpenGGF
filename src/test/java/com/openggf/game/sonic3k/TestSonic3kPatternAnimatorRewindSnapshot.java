package com.openggf.game.sonic3k;

import com.openggf.game.session.EngineServices;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GraphicsManager;
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
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip rewind snapshot tests for {@link Sonic3kPatternAnimator} (Track E.3).
 *
 * <p>Tests build a minimal animator for AIZ2 (which has AniPLC scripts) and
 * verify that a full capture → tick → restore cycle returns counters to
 * their original state.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kPatternAnimatorRewindSnapshot {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void keyIsPatternAnimator() throws IOException {
        Sonic3kPatternAnimator anim = buildAnimator(0, 1); // AIZ2
        assertEquals("pattern-animator", anim.key());
    }

    @Test
    void captureHasNoHandlerCounters() throws IOException {
        Sonic3kPatternAnimator anim = buildAnimator(0, 1); // AIZ2
        PatternAnimatorSnapshot snap = anim.capture();
        assertEquals(0, snap.handlerCounters().length,
                "S3K uses AniPLC scripts, not inner handlers");
    }

    @Test
    void captureHasScriptCountersForAiz2() throws IOException {
        Sonic3kPatternAnimator anim = buildAnimator(0, 1); // AIZ2
        PatternAnimatorSnapshot snap = anim.capture();
        assertTrue(snap.scriptCounters().length > 0,
                "AIZ2 should have at least one AniPLC script");
    }

    @Test
    void captureHasExtraBlobForAiz2() throws IOException {
        Sonic3kPatternAnimator anim = buildAnimator(0, 1); // AIZ2
        PatternAnimatorSnapshot snap = anim.capture();
        assertNotNull(snap.extra(), "S3K animator should pack scalar state into extra blob");
        assertEquals(53, snap.extra().length, "Extra blob should be 53 bytes (1 bool + 13 ints)");
    }

    @Test
    void roundTripScriptCountersForAiz2() throws IOException {
        Sonic3kPatternAnimator anim = buildAnimator(0, 1); // AIZ2
        PatternAnimatorSnapshot before = anim.capture();

        // Tick several frames to advance script counters
        for (int i = 0; i < 20; i++) {
            anim.update();
        }

        PatternAnimatorSnapshot after = anim.capture();

        // Verify at least one counter changed
        boolean changed = false;
        for (int i = 0; i < Math.min(before.scriptCounters().length, after.scriptCounters().length); i++) {
            if (before.scriptCounters()[i].timer() != after.scriptCounters()[i].timer()
                    || before.scriptCounters()[i].frameIndex() != after.scriptCounters()[i].frameIndex()) {
                changed = true;
                break;
            }
        }
        assertTrue(changed, "Script counters must advance after ticking");

        // Restore and verify counters match before-snapshot
        anim.restore(before);
        PatternAnimatorSnapshot restored = anim.capture();

        assertEquals(before.scriptCounters().length, restored.scriptCounters().length,
                "Script counter array length must match");
        for (int i = 0; i < before.scriptCounters().length; i++) {
            assertEquals(before.scriptCounters()[i].timer(),
                    restored.scriptCounters()[i].timer(),
                    "timer mismatch at script " + i);
            assertEquals(before.scriptCounters()[i].frameIndex(),
                    restored.scriptCounters()[i].frameIndex(),
                    "frameIndex mismatch at script " + i);
        }
    }

    @Test
    void roundTripExtraBlobForAiz2() throws IOException {
        Sonic3kPatternAnimator anim = buildAnimator(0, 1); // AIZ2
        PatternAnimatorSnapshot before = anim.capture();

        for (int i = 0; i < 10; i++) {
            anim.update();
        }

        anim.restore(before);
        PatternAnimatorSnapshot restored = anim.capture();

        assertArrayEquals(before.extra(), restored.extra(),
                "Extra scalar blob must survive a restore round-trip");
    }

    @Test
    void roundTripMhzBackgroundPhaseCaches() throws Exception {
        Sonic3kPatternAnimator anim = buildAnimator(Sonic3kZoneIds.ZONE_MHZ, 0);
        setIntField(anim, "lastMhzBg1Phase", 0x12);
        setIntField(anim, "lastMhzBg2Phase", 0x34);
        PatternAnimatorSnapshot before = anim.capture();

        setIntField(anim, "lastMhzBg1Phase", 0x56);
        setIntField(anim, "lastMhzBg2Phase", 0x78);

        anim.restore(before);

        assertEquals(0x12, getIntField(anim, "lastMhzBg1Phase"),
                "MHZ BG1 phase cache must rewind with the pattern animator");
        assertEquals(0x34, getIntField(anim, "lastMhzBg2Phase"),
                "MHZ BG2 phase cache must rewind with the pattern animator");
    }

    // ===== Helpers =====

    private static Sonic3kPatternAnimator buildAnimator(int zone, int act) throws IOException {
        RomByteReader reader = RomByteReader.fromRom(
                com.openggf.tests.TestEnvironment.currentRom());
        TestLevel level = new TestLevel(1024);
        return new Sonic3kPatternAnimator(reader, level, zone, act, true);
    }

    private static int getIntField(Sonic3kPatternAnimator animator, String fieldName) throws Exception {
        Field field = Sonic3kPatternAnimator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(animator);
    }

    private static void setIntField(Sonic3kPatternAnimator animator, String fieldName, int value) throws Exception {
        Field field = Sonic3kPatternAnimator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(animator, value);
    }

    /** Minimal Level stub — patterns only. */
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
