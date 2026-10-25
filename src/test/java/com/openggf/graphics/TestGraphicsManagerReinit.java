package com.openggf.graphics;

import com.openggf.game.session.SessionManager;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestGraphicsManagerReinit {

    @Test
    void transformUniformLocationsAreLookedUpOncePerShaderProgram() {
        PatternRenderCommand.clearUniformLocationCache();
        AtomicInteger lookups = new AtomicInteger();
        PatternRenderCommand.UniformLookup lookup = (program, name) -> lookups.incrementAndGet();

        int[] first = PatternRenderCommand.transformUniformLocations(7, lookup);
        int[] sameProgram = PatternRenderCommand.transformUniformLocations(7, lookup);
        int[] otherProgram = PatternRenderCommand.transformUniformLocations(9, lookup);

        assertSame(first, sameProgram);
        assertNotSame(first, otherProgram);
        assertEquals(4, lookups.get(), "projection + camera are each queried once per shader");
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GraphicsManager.destroyForReinit();
    }

    @Test
    void repeatedHeadlessDestroyDrainsCommandsAndStaticPools() throws Exception {
        for (int cycle = 0; cycle < 3; cycle++) {
            GraphicsManager.destroyForReinit();
            TestEnvironment.resetAll();
            GraphicsManager manager = GraphicsManager.getInstance();
            manager.initHeadless();
            manager.cachePatternTexture(new Pattern(), 0);
            PatternAtlas.Entry entry = manager.getPatternAtlasEntry(0);
            PatternRenderCommand command = PatternRenderCommand.obtain(entry, -1, manager);
            command.capturePalette(7);
            command.finish(cycle == 0, cycle == 1, cycle == 2, .25f, .5f, 8.75f, 4.125f);
            manager.registerCommand(command);
            PatternRenderCommand.ensureNativeScratch();
            GLCommand.ensureNativeScratch();
            GLCommandGroup.ensureNativeScratch(12);
            assertTrue(PatternRenderCommand.hasNativeScratch());
            assertTrue(GLCommand.hasNativeScratch());
            assertTrue(GLCommandGroup.hasNativeScratch());

            GraphicsManager.destroyForReinit();

            assertEquals(0, PatternRenderCommand.pooledCommandCount(),
                    "destroy must not retain commands (or their old manager) in a static pool");
            assertFalse(PatternRenderCommand.hasNativeScratch(),
                    "process-lifetime native scratch must be releasable for reinit");
            assertFalse(GLCommand.hasNativeScratch());
            assertFalse(GLCommandGroup.hasNativeScratch());
            var owner = PatternRenderCommand.class.getDeclaredField("graphicsManager"); owner.setAccessible(true);
            assertNull(owner.get(command), "a retired queued lease cannot retain its manager after reinit");
            var leased = PatternRenderCommand.class.getDeclaredField("leased"); leased.setAccessible(true);
            assertFalse(leased.getBoolean(command));
        }
    }
}
