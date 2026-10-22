package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.LevelState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.HudRenderManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real preparation callers with ROM-free HUD emission; publication and rewind remain independent. */
class TestLevelSpriteTileReuse {
    private GraphicsManager graphics;
    private LevelRenderer renderer;
    private AtomicBoolean fail;

    @BeforeEach void setup() {
        GraphicsManager.destroyForReinit();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        graphics = GraphicsManager.getInstance(); graphics.initHeadless();
        fail = new AtomicBoolean();
        renderer = renderer();
    }

    @AfterEach void cleanup() { SessionManager.clear(); GraphicsManager.destroyForReinit(); }

    private LevelRenderer renderer() {
        var level = mock(LevelManager.class);
        level.graphicsManager = graphics;
        level.camera = mock(Camera.class);
        level.profiler = mock(PerformanceProfiler.class);
        level.levelGamestate = mock(LevelState.class);
        level.hudRenderManager = mock(HudRenderManager.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            graphics.renderPatternWithId(42, new PatternDesc(), 8, 16);
            if (fail.get()) throw new IllegalStateException("failed HUD producer");
            return null;
        }).when(level.hudRenderManager).draw(any(LevelState.class), nullable(PlayableEntity.class));
        return new LevelRenderer(level);
    }

    @Test void pendingWorldCountersAndAnotherWorldReuseOnlyTheirOwnPrecedingFrames() {
        renderer.prepareSpritePresentation(null);
        var world = renderer.spriteTables.prepared();
        assertEquals(1, world.tiles().size());
        assertTrue(renderer.spriteTables.published().tiles().isEmpty());
        renderer.publishHudCounters(false);
        var counters = renderer.spriteTables.counters();
        assertEquals(world, counters, "same values intentionally test owner isolation");
        assertNotSame(world.tiles().getFirst(), counters.tiles().getFirst());
        renderer.prepareSpritePresentation(null);
        assertSame(world.tiles().getFirst(), renderer.spriteTables.prepared().tiles().getFirst(),
                "reuse pending CPU output even before V-int publishes it");
        renderer.publishHudCounters(true);
        assertSame(counters.tiles().getFirst(), renderer.spriteTables.counters().tiles().getFirst());
        var another = renderer();
        another.prepareSpritePresentation(null);
        assertEquals(world, another.spriteTables.prepared());
        assertNotSame(world.tiles().getFirst(), another.spriteTables.prepared().tiles().getFirst(),
                "even identical worlds sharing one graphics host do not share a previous-frame cache");
        renderer.spriteTables.publish();
        assertSame(renderer.spriteTables.prepared(), renderer.spriteTables.published());
    }

    @Test void failureCannotReplacePendingPublishedOrCounterFramesAndRetryCanReuse() {
        renderer.prepareSpritePresentation(null);
        renderer.spriteTables.publish();
        renderer.publishHudCounters(false);
        var before = renderer.spriteTables.capture();
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> renderer.prepareSpritePresentation(null));
        assertThrows(IllegalStateException.class, () -> renderer.publishHudCounters(false));
        assertEquals(before, renderer.spriteTables.capture());
        assertSame(before.prepared(), renderer.spriteTables.prepared());
        assertSame(before.published(), renderer.spriteTables.published());
        assertSame(before.counters(), renderer.spriteTables.counters());
        fail.set(false);
        renderer.prepareSpritePresentation(null);
        renderer.publishHudCounters(false);
        assertSame(before.prepared().tiles().getFirst(), renderer.spriteTables.prepared().tiles().getFirst());
        assertSame(before.counters().tiles().getFirst(), renderer.spriteTables.counters().tiles().getFirst());
    }

    @Test void rewindRestoresReuseCandidatesAndResetReleasesThem() {
        renderer.prepareSpritePresentation(null);
        renderer.publishHudCounters(false);
        var retained = renderer.spriteTables.capture();
        renderer.resetState();
        assertTrue(renderer.spriteTables.prepared().tiles().isEmpty());
        assertTrue(renderer.spriteTables.counters().tiles().isEmpty());
        renderer.prepareSpritePresentation(null);
        renderer.publishHudCounters(false);
        assertNotSame(retained.prepared().tiles().getFirst(), renderer.spriteTables.prepared().tiles().getFirst());
        assertNotSame(retained.counters().tiles().getFirst(), renderer.spriteTables.counters().tiles().getFirst());
        renderer.spriteTables.restore(retained);
        renderer.prepareSpritePresentation(null);
        renderer.publishHudCounters(false);
        assertSame(retained.prepared().tiles().getFirst(), renderer.spriteTables.prepared().tiles().getFirst());
        assertSame(retained.counters().tiles().getFirst(), renderer.spriteTables.counters().tiles().getFirst());
        assertTrue(renderer.spriteTables.published().tiles().isEmpty(), "rewind must not publish the pending table");
        renderer.spriteTables.resetForMissingSnapshot();
        assertTrue(renderer.spriteTables.prepared().tiles().isEmpty());
        assertTrue(renderer.spriteTables.published().tiles().isEmpty());
        assertTrue(renderer.spriteTables.counters().tiles().isEmpty());
    }
}
