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

class TestGraphicsManagerPatternAtlasPageOrdering {
    private GraphicsManager manager;
    private int pageOneId;
    private PatternAtlas atlas;

    @BeforeEach
    void setUp() throws Exception {
        GraphicsManager.destroyForReinit();
        TestEnvironment.resetAll();
        manager = GraphicsManager.getInstance();
        manager.initHeadless();

        atlas = new PatternAtlas(8, 8);
        pageOneId = PatternAtlasRange.OBJECTS.base();
        atlas.cachePatternHeadless(new Pattern(), 0);
        atlas.cachePatternHeadless(new Pattern(), pageOneId);
        setField(manager, "patternAtlas", atlas);
        setField(manager, "combinedPaletteTextureId", 1);

        InstancedPatternRenderer renderer = new InstancedPatternRenderer(
                manager, com.openggf.game.GameServices.configuration());
        setField(renderer, "supported", true);
        setField(manager, "instancedPatternRenderer", renderer);
        manager.setHeadlessMode(false); // batching construction is GL-free; commands are not executed
    }

    @AfterEach
    void tearDown() {
        manager.setHeadlessMode(true);
        GraphicsManager.destroyForReinit();
    }

    @Test
    void generalBatchingFlushesInSubmissionOrderAcrossAtlasPages() throws Exception {
        PatternDesc desc = new PatternDesc();
        manager.beginPatternBatch();
        manager.renderPatternWithId(0, desc, 10, 10);
        manager.renderPatternWithId(pageOneId, desc, 20, 10);
        manager.renderPatternWithId(0, desc, 30, 10);
        manager.flushPatternBatch();

        assertEquals(List.of(0, 1, 0), queuedAtlasPages());
    }

    @Test
    void scaledDirectDrawFlushesBeforeItAndRestartsBatchAfterIt() throws Exception {
        PatternDesc desc = new PatternDesc();
        manager.beginPatternBatch();
        manager.renderPatternWithId(0, desc, 10, 10);
        manager.renderPatternWithIdScaled(pageOneId, desc, 20, 10, 16, 16);
        manager.renderPatternWithId(0, desc, 30, 10);
        manager.flushPatternBatch();

        assertEquals(List.of("InstancedBatchCommand", "PatternRenderCommand", "InstancedBatchCommand"),
                manager.commands.stream().map(c -> c.getClass().getSimpleName()).toList());
        assertEquals(List.of(0, 1, 0), queuedAtlasPages());
    }

    @Test
    void stripPatternsBatchOnOverflowAtlasPage() throws Exception {
        PatternDesc desc = new PatternDesc();
        manager.beginPatternBatch();
        manager.renderStripPatternWithId(pageOneId, desc, 10, 10, 0);
        manager.flushPatternBatch();

        assertEquals(List.of(1), queuedAtlasPages());
    }

    @Test
    void stripFallsBackDirectlyWhenInstancingIsInactiveAndPreservesUvs() throws Exception {
        setField(manager, "instancedPatternRenderer", null);
        PatternDesc desc = new PatternDesc();
        desc.setHFlip(true);
        desc.setVFlip(false);

        manager.renderStripPatternWithId(pageOneId, desc, 10, 20, 2);

        assertEquals(1, manager.commands.size());
        Object command = manager.commands.get(0);
        PatternAtlas.Entry entry = atlas.getEntry(pageOneId);
        float rowStep = (entry.v1() - entry.v0()) / 8f;
        float stripTop = entry.v0() + rowStep * 3.5f;
        float stripBottom = entry.v0() + rowStep * 2.5f;
        assertEquals("PatternRenderCommand", command.getClass().getSimpleName());
        assertEquals(entry.u1(), (float) commandField(command, "u0"));
        assertEquals(entry.u0(), (float) commandField(command, "u1"));
        assertEquals(stripBottom, (float) commandField(command, "v0"));
        assertEquals(stripTop, (float) commandField(command, "v1"));
        assertEquals(2f, (float) commandField(command, "height"));
    }

    @Test
    void stripFallsBackDirectlyWhenBatchingIsDisabled() {
        manager.setBatchingEnabled(false);
        manager.renderStripPatternWithId(pageOneId, new PatternDesc(), 10, 20, 0);
        assertEquals(1, manager.commands.size());
        assertTrue(manager.commands.get(0) instanceof PatternRenderCommand);
    }

    @Test
    void directStripUsesEffectiveUnderwaterPalette() throws Exception {
        manager.setBatchingEnabled(false);
        setField(manager, "combinedPaletteTextureId", 11);
        setField(manager, "underwaterPaletteTextureId", 22);
        manager.setUseUnderwaterPaletteForBackground(true);

        manager.renderStripPatternWithId(pageOneId, new PatternDesc(), 10, 20, 0);

        assertEquals(1, manager.commands.size());
        assertEquals(22, commandField(manager.commands.get(0), "paletteTextureId"));
    }

    @Test
    void directStripQueuesWhenOnlyEffectiveUnderwaterPaletteExists() throws Exception {
        manager.setBatchingEnabled(false);
        setField(manager, "combinedPaletteTextureId", null);
        setField(manager, "underwaterPaletteTextureId", 22);
        manager.setUseUnderwaterPaletteForBackground(true);

        manager.renderStripPatternWithId(pageOneId, new PatternDesc(), 10, 20, 0);

        assertEquals(1, manager.commands.size());
        assertEquals(22, commandField(manager.commands.get(0), "paletteTextureId"));
    }

    @Test
    void legacyBatchFullFlushesAndRetriesStripWithoutDroppingIt() throws Exception {
        setField(manager, "instancedPatternRenderer", null);
        manager.setInstancedBatchingEnabled(false);
        PatternDesc desc = new PatternDesc();
        manager.beginPatternBatch();
        for (int i = 0; i < 4097; i++) {
            manager.renderStripPatternWithId(0, desc, i, 20, 0);
        }
        manager.flushPatternBatch();

        assertEquals(2, manager.commands.size(), "full legacy batch + retried one-strip batch");
        assertEquals(4096, commandField(manager.commands.get(0), "patternCount"));
        assertEquals(1, commandField(manager.commands.get(1), "patternCount"));
    }

    @Test
    void ghostStateChangeCreatesAnOrderedBatchBoundary() throws Exception {
        PatternDesc desc = new PatternDesc();
        manager.beginPatternBatch();
        manager.renderPatternWithId(0, desc, 10, 10);
        manager.beginGhostRenderEffect(0.5f);
        manager.renderPatternWithId(0, desc, 20, 10);
        manager.endGhostRenderEffect();
        manager.flushPatternBatch();

        assertEquals(2, manager.commands.size());
        assertEquals(false, commandField(manager.commands.get(0), "capturedGhostEffectActive"));
        assertEquals(true, commandField(manager.commands.get(1), "capturedGhostEffectActive"));
        assertEquals(0.5f, (float) commandField(manager.commands.get(1), "capturedGhostAlpha"));
    }

    @Test
    void directIngressKeepsCachedAttributesExplicitIdAndFloatArithmetic() throws Exception {
        manager.setBatchingEnabled(false);
        for (int flags = 0; flags < 8; flags++) {
            PatternDesc desc = new PatternDesc(0x123);
            desc.setPatternIndex(99); // Neither the raw word nor this cache owns the explicit atlas ID.
            desc.setPaletteIndex(7);
            desc.setHFlip((flags & 1) != 0);
            desc.setVFlip((flags & 2) != 0);
            desc.setPriority((flags & 4) != 0);
            manager.renderPatternWithId(pageOneId, desc, Integer.MIN_VALUE, Integer.MAX_VALUE);
            manager.renderPatternWithIdScaled(pageOneId, desc, -3.25f, 16.75f, 11.5f, 3.125f);
            manager.renderStripPatternWithId(pageOneId, desc, Integer.MAX_VALUE, Integer.MIN_VALUE, 1);
            assertEquals(0x123, desc.get(), "cached setters do not rewrite the raw word");
            for (int i = 0; i < 3; i++) {
                Object command = manager.commands.get(i);
                assertEquals(1, commandField(command, "atlasIndex"));
                assertEquals(7, commandField(command, "paletteIndex"));
                assertEquals(desc.getHFlip(), commandField(command, "hFlip"));
                assertEquals(desc.getVFlip(), commandField(command, "vFlip"));
                assertEquals(desc.getPriority(), commandField(command, "piecePriority"));
            }
            assertEquals((float) Integer.MIN_VALUE, commandField(manager.commands.get(0), "x"));
            assertEquals(224f - (float) Integer.MAX_VALUE - 8f, commandField(manager.commands.get(0), "y"));
            assertEquals(224f - 16.75f - 3.125f, commandField(manager.commands.get(1), "y"));
            assertEquals(11.5f, commandField(manager.commands.get(1), "width"));
            assertEquals(3.125f, commandField(manager.commands.get(1), "height"));
            assertEquals(224f - (float) Integer.MIN_VALUE - 2f, commandField(manager.commands.get(2), "y"));
            manager.discardQueuedCommands();
        }
    }

    @Test
    void underwaterSamplingSeesAcquiredPrefixAndPaletteBeforeMutatingLaterAttributes() throws Exception {
        SamplingGraphicsManager sampling = samplingManager();
        PatternDesc desc = new PatternDesc();
        desc.setPaletteIndex(7);
        PatternRenderCommand reused = seedCommand();
        SpritePresentation.PaletteReplay replay = (host, palette) -> palette;
        sampling.spritePresentationSubject = new SpritePresentation.Subject("before", SpritePresentation.Part.BODY,
                false, 100, null, null, replay);
        sampling.sample = () -> {
            assertEquals(0, PatternRenderCommand.pooledCommandCount());
            assertSame(sampling, commandField(reused, "graphicsManager"));
            assertEquals(1, commandField(reused, "atlasIndex"));
            assertEquals(1, commandField(reused, "paletteTextureId"));
            PatternAtlas.Entry entry = atlas.getEntry(pageOneId);
            assertEquals(entry.u0(), commandField(reused, "u0"));
            assertEquals(entry.v0(), commandField(reused, "v0"));
            assertEquals(entry.u1(), commandField(reused, "u1"));
            assertEquals(entry.v1(), commandField(reused, "v1"));
            assertEquals(7, commandField(reused, "paletteIndex"));
            assertSame(replay, commandField(reused, "paletteReplay"));
            assertNull(commandField(reused, "presentationQuad"));
            assertEquals(0, commandField(reused, "presentationCount"));
            assertEquals(0f, commandField(reused, "presentationHue"));
            assertEquals(1f, commandField(reused, "presentationOpacity"));
            assertEquals(false, commandField(reused, "leased"));
            assertEquals(false, commandField(reused, "hFlip"));
            assertEquals(false, commandField(reused, "paletteReplayUnderwater"));
            assertEquals(1f, commandField(reused, "x"));
            assertEquals(4f, commandField(reused, "height"));
            desc.setPaletteIndex(2);
            desc.setHFlip(true); desc.setVFlip(true); desc.setPriority(true);
            sampling.setCurrentSpriteTileOcclusionPaletteMask(5);
            sampling.beginGhostRenderEffect(.375f, true);
            sampling.spritePresentationSubject = SpritePresentation.Subject.WORLD;
        };
        sampling.renderPatternWithIdScaled(pageOneId, desc, -2.5f, 3.75f, 9.25f, 6.5f);
        assertSame(reused, sampling.commands.getFirst());
        assertEquals(7, commandField(reused, "paletteIndex"));
        assertEquals(true, commandField(reused, "paletteReplayUnderwater"));
        assertSame(replay, commandField(reused, "paletteReplay"));
        assertEquals(true, commandField(reused, "hFlip"));
        assertEquals(true, commandField(reused, "vFlip"));
        assertEquals(true, commandField(reused, "piecePriority"));
        assertEquals(5, commandField(reused, "capturedTileOcclusionPaletteMask"));
        assertEquals(true, commandField(reused, "ghostEffectActive"));
        assertEquals(true, commandField(reused, "ghostColour"));
        assertEquals(.375f, commandField(reused, "ghostAlpha"));
        assertEquals(224f - 3.75f - 6.5f, commandField(reused, "y"));
        sampling.discardQueuedCommands();
    }

    @Test
    void nullDescriptorAndUnderwaterThrowConsumePoolWithoutFinishingOrRegistering() throws Exception {
        SamplingGraphicsManager sampling = samplingManager();
        PatternRenderCommand first = seedCommand();
        sampling.sample = () -> fail("null descriptor wins before underwater sampling");
        assertThrows(NullPointerException.class,
                () -> sampling.renderPatternWithIdScaled(pageOneId, null, 0, 0, 8, 8));
        assertEquals(0, PatternRenderCommand.pooledCommandCount());
        assertSame(sampling, commandField(first, "graphicsManager"));
        assertEquals(false, commandField(first, "leased"));
        assertTrue(sampling.commands.isEmpty());
        PatternRenderCommand second = seedCommand();
        IllegalStateException expected = new IllegalStateException("underwater sample");
        sampling.sample = () -> { throw expected; };
        PatternDesc desc = new PatternDesc(); desc.setPaletteIndex(7); desc.setHFlip(true);
        assertSame(expected, assertThrows(IllegalStateException.class,
                () -> sampling.renderPatternWithIdScaled(pageOneId, desc, 10, 20, 16, 4)));
        assertEquals(7, commandField(second, "paletteIndex"));
        assertEquals(false, commandField(second, "hFlip"), "later fields retain their recycled value on failure");
        assertEquals(false, commandField(second, "leased"));
        assertEquals(0, PatternRenderCommand.pooledCommandCount());
        second.discard(); first.discard();
        assertEquals(0, PatternRenderCommand.pooledCommandCount(), "failed initialization never acquired a lease");
        assertTrue(sampling.commands.isEmpty());
        sampling.discardQueuedCommands();
    }

    private SamplingGraphicsManager samplingManager() throws Exception {
        manager.discardQueuedCommands();
        PatternRenderCommand.cleanupHeadless();
        SamplingGraphicsManager sampling = new SamplingGraphicsManager();
        sampling.initHeadless(); sampling.setHeadlessMode(false);
        setField(sampling, "patternAtlas", atlas);
        setField(sampling, "combinedPaletteTextureId", 1);
        return sampling;
    }

    private PatternRenderCommand seedCommand() throws Exception {
        manager.renderPatternWithIdScaled(0, new PatternDesc(), 1, 2, 3, 4);
        PatternRenderCommand command = (PatternRenderCommand) manager.commands.getLast();
        // Leave stale presentation values for the acquisition prefix to clear.
        setField(command, "presentationQuad", new float[8]);
        setField(command, "presentationCount", 2);
        setField(command, "presentationHue", .25f);
        setField(command, "presentationOpacity", .5f);
        manager.discardQueuedCommands();
        return command;
    }

    private static final class SamplingGraphicsManager extends GraphicsManager {
        Runnable sample;
        @Override public boolean isUseUnderwaterPaletteForBackground() {
            sample.run();
            return true;
        }
    }

    private List<Integer> queuedAtlasPages() {
        return manager.commands.stream().map(command -> {
            try {
                Field field = command.getClass().getDeclaredField("atlasIndex");
                field.setAccessible(true);
                return field.getInt(command);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(command.getClass().getSimpleName() + " has no atlas page", e);
            }
        }).toList();
    }

    private static Object commandField(Object command, String name) {
        try {
            Field field = command.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(command);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = (target instanceof GraphicsManager ? GraphicsManager.class : target.getClass()).getDeclaredField(name);
        field.setAccessible(true);
        if (value instanceof Boolean b) {
            field.setBoolean(target, b);
        } else {
            field.set(target, value);
        }
    }
}
