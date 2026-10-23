package com.openggf.game.sonic2;

import com.openggf.game.session.EngineServices;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.animation.AnimatedTileCachePolicy;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.animation.ChannelContext;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.level.animation.AniPlcParser;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestScriptFramesApplyStrategyGraphicsRebind {
    private final EngineContext previousContext = EngineServices.current();

    @AfterEach
    void tearDown() {
        EngineServices.configure(previousContext);
    }

    @Test
    void factoryChannelsResolveGraphicsOncePerApplyAfterConstructionAndLaterRebinds() {
        RecordingGraphicsManager installGraphics = new RecordingGraphicsManager();
        RecordingGraphicsManager currentGraphics = new RecordingGraphicsManager();
        RecordingGraphicsManager nextGraphics = new RecordingGraphicsManager();
        configureGraphics(installGraphics);
        TestLevel level = new TestLevel();
        AniPlcScriptState script = new AniPlcScriptState(
                (byte) 0, 2, new int[] {0, 2}, null, 2, art(1, 2, 3, 4));
        AniPlcParser.ensurePatternCapacity(List.of(script), level);
        AtomicInteger resolutions = new AtomicInteger();
        AnimatedTileChannelGraph graph = new AnimatedTileChannelGraph();
        graph.install(Sonic2AnimatedTileChannels.fromScripts(List.of(script), () -> {
            resolutions.incrementAndGet();
            return GameServices.graphics();
        }));
        assertEquals(0, resolutions.get(), "Factory construction must not resolve an installation owner");
        assertTrue(installGraphics.uploads.isEmpty());
        var channel = graph.channels().getFirst();
        assertEquals("s2.script.0", channel.channelId());
        assertEquals(AnimatedTileCachePolicy.ALWAYS, channel.cachePolicy());
        assertTrue(channel.guard().allows());
        assertEquals(2, channel.destinationPlan().primaryTile());
        assertEquals(3, channel.destinationPlan().secondaryTile());
        ChannelContext context = new ChannelContext(graph, null, level, null, 0, 0, 17);
        assertEquals(17, channel.phaseSource().resolve(context));

        configureGraphics(currentGraphics);
        graph.update(context);
        assertEquals(1, resolutions.get(), "A multi-tile apply must resolve exactly one owner");
        assertTrue(installGraphics.uploads.isEmpty());
        assertUploads(currentGraphics, level, 2, 1, 2);
        assertEquals(1, script.getFrameIndex());

        configureGraphics(nextGraphics);
        graph.update(context);
        assertEquals(2, resolutions.get());
        assertEquals(2, currentGraphics.uploads.size(), "The prior owner must stop receiving uploads");
        assertUploads(nextGraphics, level, 2, 3, 4);
        assertEquals(2, script.getFrameIndex());
    }

    @Test
    void factoryChannelsPreservePrimeDurationsWrapAndRestoredProgression() {
        RecordingGraphicsManager graphics = new RecordingGraphicsManager();
        TestLevel level = new TestLevel();
        AniPlcScriptState script = new AniPlcScriptState(
                (byte) -1, 0, new int[] {0, 1}, new int[] {1, 2}, 1, art(5, 9));
        AniPlcParser.primeScripts(List.of(script), level, graphics);
        assertEquals(0, script.getTimer());
        assertEquals(0, script.getFrameIndex());
        assertUploads(graphics, level, 0, 5);
        graphics.uploads.clear();

        AtomicInteger resolutions = new AtomicInteger();
        AnimatedTileChannelGraph graph = new AnimatedTileChannelGraph();
        graph.install(Sonic2AnimatedTileChannels.fromScripts(List.of(script), () -> {
            resolutions.incrementAndGet();
            return graphics;
        }));
        ChannelContext context = new ChannelContext(graph, null, level, null, 0, 0, 0);
        int[] timers = {1, 0, 2, 1, 0, 1};
        int[] frameIndexes = {1, 1, 2, 2, 2, 1};
        int[] pixels = {5, 5, 9, 9, 9, 5};
        for (int tick = 0; tick < timers.length; tick++) {
            graph.update(context);
            assertEquals(timers[tick], script.getTimer(), "timer at tick " + tick);
            assertEquals(frameIndexes[tick], script.getFrameIndex(), "frame at tick " + tick);
            assertEquals(pixels[tick], level.getPattern(0).getPixel(0, 0));
            assertEquals(tick + 1, resolutions.get(), "Resolve once even during a countdown");
        }
        assertEquals(List.of(5, 9, 5), graphics.uploads.stream().map(Upload::pixel).toList());

        script.restoreCounters(0, 1);
        graphics.uploads.clear();
        graph.update(context);
        assertEquals(7, resolutions.get());
        assertEquals(2, script.getTimer());
        assertEquals(2, script.getFrameIndex());
        assertUploads(graphics, level, 0, 9);
    }

    private void configureGraphics(GraphicsManager graphics) {
        EngineServices.configure(new EngineContext(
                previousContext.configuration(), graphics, previousContext.audio(), previousContext.roms(),
                previousContext.profiler(), previousContext.debugOverlay(), previousContext.playbackDebug(),
                previousContext.romDetection(), previousContext.crossGameFeatures(),
                previousContext.moduleResolutionService()));
    }

    private static Pattern[] art(int... pixels) {
        Pattern[] patterns = new Pattern[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            patterns[i] = new Pattern();
            patterns[i].setPixel(0, 0, (byte) pixels[i]);
        }
        return patterns;
    }

    private static void assertUploads(RecordingGraphicsManager graphics, TestLevel level,
                                      int firstDestination, int... pixels) {
        assertEquals(pixels.length, graphics.uploads.size());
        for (int i = 0; i < pixels.length; i++) {
            Upload upload = graphics.uploads.get(i);
            assertEquals(firstDestination + i, upload.destination());
            assertSame(level.getPattern(firstDestination + i), upload.pattern());
            assertEquals(pixels[i], upload.pixel());
        }
    }

    private record Upload(Pattern pattern, int destination, int pixel) {}

    private static final class RecordingGraphicsManager extends GraphicsManager {
        private final List<Upload> uploads = new ArrayList<>();

        @Override
        public boolean isGlInitialized() {
            return true;
        }

        @Override
        public void updatePatternTexture(Pattern pattern, int patternId) {
            uploads.add(new Upload(pattern, patternId, pattern.getPixel(0, 0)));
        }
    }

    private static final class TestLevel implements Level {
        private Pattern[] patterns = {new Pattern()};

        @Override public int getPaletteCount() { return 0; }
        @Override public Palette getPalette(int index) { throw new UnsupportedOperationException(); }
        @Override public int getPatternCount() { return patterns.length; }
        @Override public Pattern getPattern(int index) { return patterns[index]; }
        @Override public void ensurePatternCapacity(int minCount) {
            if (minCount <= patterns.length) {
                return;
            }
            Pattern[] expanded = new Pattern[minCount];
            System.arraycopy(patterns, 0, expanded, 0, patterns.length);
            for (int i = patterns.length; i < expanded.length; i++) {
                expanded[i] = new Pattern();
            }
            patterns = expanded;
        }
        @Override public int getChunkCount() { return 0; }
        @Override public Chunk getChunk(int index) { throw new UnsupportedOperationException(); }
        @Override public int getBlockCount() { return 0; }
        @Override public Block getBlock(int index) { throw new UnsupportedOperationException(); }
        @Override public SolidTile getSolidTile(int index) { throw new UnsupportedOperationException(); }
        @Override public Map getMap() { return null; }
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
