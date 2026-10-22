package com.openggf.graphics;

import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** GL-free submission seam: command count, bounded capacity and painter-state barriers. */
class TestPresentationPatternBatching {
    private GraphicsManager graphics;
    private int patternId;

    @BeforeEach void setup() throws Exception {
        TestEnvironment.resetAll();
        graphics = new GraphicsManager();
        graphics.initHeadless();
        var atlas = new PatternAtlas(8, 8);
        patternId = PatternAtlasRange.OBJECTS.base();
        atlas.cachePatternHeadless(new Pattern(), patternId);
        field("patternAtlas").set(graphics, atlas);
        field("combinedPaletteTextureId").set(graphics, 1);
        var instanced = new InstancedPatternRenderer(graphics, com.openggf.game.GameServices.configuration());
        var supported = InstancedPatternRenderer.class.getDeclaredField("supported");
        supported.setAccessible(true); supported.set(instanced, true);
        field("instancedPatternRenderer").set(graphics, instanced);
        graphics.setHeadlessMode(false);
    }

    @AfterEach void cleanup() { graphics.setHeadlessMode(true); graphics.cleanup(); }

    @Test void deferredPaletteResolversAndAtlasPagesRemainIndependentCommands() throws Exception {
        var replay = (SpritePresentation.PaletteReplay) (host, palette) -> palette;
        var subject = new SpritePresentation.Subject("scoped", SpritePresentation.Part.BODY, false, 100, null, null, replay);
        SpritePresentation.withSubject(graphics, subject, () -> { submit(0, 0, 1); submit(1, 0, 1); });
        assertEquals(2, graphics.commands.size(), "even the same resolver retains its per-tile invocation boundary");
        var atlas = (PatternAtlas) field("patternAtlas").get(graphics);
        atlas.cachePatternHeadless(new Pattern(), 0);
        assertNotEquals(atlas.getEntry(patternId).atlasIndex(), atlas.getEntry(0).atlasIndex(),
                "the fixture must allocate distinct physical pages, not merely different virtual IDs");
        submit(2, 0, 1);
        int previous = patternId;
        patternId = 0;
        submit(3, 0, 1);
        patternId = previous;
        submit(4, 0, 1);
        assertEquals(5, graphics.commands.size(), "different atlas pages cannot be regrouped across painter order");
    }

    @Test void adjacentCompatibleTilesReduceTwentyThreeSubmissionsToOne() {
        for (int i = 0; i < 23; i++) submit(i, 0, 1);
        assertEquals(1, graphics.commands.size());
        assertEquals(23, ((PatternRenderCommand) graphics.commands.getFirst()).presentationPatternCount());
    }

    @Test void batchesHaveBoundedCapacityAndPreserveNormalInstancingBetweenThem() {
        for (int i = 0; i < 513; i++) submit(i, 0, 1);
        assertEquals(List.of(512, 1), graphics.commands.stream()
                .map(c -> ((PatternRenderCommand) c).presentationPatternCount()).toList());
        graphics.beginPatternBatch();
        graphics.renderPatternWithId(patternId, new PatternDesc(), 50, 60);
        submit(514, 0, 1);
        assertEquals(List.of("PatternRenderCommand", "PatternRenderCommand", "InstancedBatchCommand", "PatternRenderCommand"),
                graphics.commands.stream().map(c -> c.getClass().getSimpleName()).toList());
    }

    @Test void hueOpacityPriorityMaskGhostAndPrimitivesAreSubmissionBarriers() {
        submit(0, 0, 1);
        submit(1, .25f, 1);
        submit(2, .25f, .7f);
        graphics.setUseSpritePriorityShader(true);
        submit(3, .25f, .7f);
        graphics.setCurrentSpriteTileOcclusionPaletteMask(3);
        submit(4, .25f, .7f);
        graphics.beginGhostRenderEffect(.4f);
        submit(5, .25f, .7f);
        graphics.registerCommand((x, y, w, h) -> { });
        submit(6, .25f, .7f);
        assertEquals(8, graphics.commands.size());
    }

    @Test void capturedQueuesOwnTheirBatchWithoutMergingIntoTheOuterQueue() {
        submit(0, 0, 1);
        // Queue construction is GL-free; inspect via the existing command capture target seam.
        var captured = new java.util.ArrayList<GLCommandable>();
        try {
            field("commandCaptureTarget").set(graphics, captured);
            for (int i = 0; i < 23; i++) submit(i, 0, 1);
            assertEquals(1, captured.size());
            assertEquals(23, ((PatternRenderCommand) captured.getFirst()).presentationPatternCount());
            assertEquals(1, ((PatternRenderCommand) graphics.commands.getFirst()).presentationPatternCount());
        } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        finally {
            try { field("commandCaptureTarget").set(graphics, null); }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
            captured.forEach(GLCommandable::discard);
        }
    }

    private void submit(int x, float hue, float opacity) {
        PresentationGraphicsAccess.render(graphics, patternId, 0, false, false, false, 2, 7,
                new float[]{x, 12, x + 8, 12, x + 8, 17, x, 17}, hue, opacity);
    }
    private static Field field(String name) {
        try {
            Field field = GraphicsManager.class.getDeclaredField(name);
            field.setAccessible(true); return field;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
