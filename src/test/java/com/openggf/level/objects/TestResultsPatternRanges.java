package com.openggf.level.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance;
import com.openggf.game.sonic2.objects.ResultsScreenObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestResultsPatternRanges {
    @Test void s2OnlyTheChangedCounterRangeIsUploaded() throws Exception {
        var results = new ResultsScreenObjectInstance(90, 7, 0, false);
        var fixture = new Fixture(results);
        fixture.update();
        assertEquals(List.of(0, 8, 16, 24), fixture.graphics.updatedStarts());
        byte[][] before = fixture.pixels();
        fixture.graphics.clear();
        fixture.update();
        assertTrue(fixture.graphics.uploads.isEmpty());
        set(results, "ringBonus", 321);
        fixture.update();
        assertEquals(List.of(16), fixture.graphics.updatedStarts());
        for (int i = 0; i < 32; i++) if (i < 16 || i >= 24)
            assertArrayEquals(before[i], fixture.pixels()[i], "unchanged/fixed art must remain untouched");
        assertEquals(0, fixture.graphics.depth);
    }

    @Test void s1ChangingScoreKeepsTheTimeAndRingArtAndUploadsItsOwnRange() throws Exception {
        var results = new Sonic1ResultsScreenObjectInstance(90, 7, 0);
        var fixture = new Fixture(results);
        fixture.update();
        assertEquals(3, fixture.graphics.updatedStarts().size());
        int scoreStart = fixture.graphics.updatedStarts().getLast();
        byte[][] before = fixture.pixels();
        fixture.graphics.clear();
        fixture.score.addScore(100);
        fixture.update();
        assertEquals(List.of(scoreStart), fixture.graphics.updatedStarts());
        assertEquals(12, fixture.graphics.uploads.size());
        for (int i = 0; i < 16; i++) assertArrayEquals(before[i], fixture.pixels()[i]);
        assertEquals(0, fixture.graphics.depth);
    }

    private static final class Fixture {
        final AbstractObjectInstance results;
        final RecordingGraphics graphics = new RecordingGraphics();
        final GameStateManager score = new GameStateManager();
        final ObjectRenderManager render = mock(ObjectRenderManager.class);
        final Pattern[] patterns = new Pattern[1024];
        final java.lang.reflect.Method update;
        Fixture(AbstractObjectInstance results) throws Exception {
            this.results = results;
            java.util.Arrays.setAll(patterns, index -> new Pattern());
            var digits = new Pattern[20];
            java.util.Arrays.setAll(digits, index -> { var p = new Pattern(); p.setPixel(0, 0, (byte)(index % 15 + 1)); return p; });
            var sheet = new ObjectSpriteSheet(patterns, List.of(), 0, 1);
            var renderer = new PatternSpriteRenderer(sheet, graphics);
            renderer.ensurePatternsCached(graphics, 0x400);
            when(render.getResultsRenderer()).thenReturn(renderer);
            when(render.getResultsSheet()).thenReturn(sheet);
            when(render.getResultsHudDigitPatterns()).thenReturn(digits);
            results.setServices(new TestObjectServices() {
                @Override public ObjectRenderManager renderManager() { return render; }
            }.withGraphicsManager(graphics).withGameState(score));
            update = results.getClass().getDeclaredMethod("updateBonusPatterns", ObjectRenderManager.class);
            update.setAccessible(true);
        }
        void update() throws Exception { update.invoke(results, render); }
        byte[][] pixels() {
            byte[][] copy = new byte[patterns.length][64];
            for (int i = 0; i < patterns.length; i++) patterns[i].copyInto(copy[i], 0);
            return copy;
        }
    }
    private static final class RecordingGraphics extends GraphicsManager {
        int depth;
        final List<Integer> uploads = new ArrayList<>();
        final List<Integer> starts = new ArrayList<>();
        boolean firstInRange;
        @Override public void beginPatternAtlasBatch() { depth++; firstInRange = true; }
        @Override public void endPatternAtlasBatch() { depth--; }
        @Override public void cachePatternTexture(Pattern pattern, int id) { }
        @Override public void updatePatternTexture(Pattern pattern, int id) {
            assertTrue(depth > 0);
            if (firstInRange) { starts.add(id - 0x400); firstInRange = false; }
            uploads.add(id - 0x400);
        }
        void clear() { uploads.clear(); starts.clear(); }
        List<Integer> updatedStarts() { return List.copyOf(starts); }
    }
    private static void set(Object object, String name, int value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.setInt(object, value);
    }
}
