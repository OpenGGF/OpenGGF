package com.openggf.graphics;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpritePieceRenderer.PreparedPiece;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Characterizes the admission/read/append boundary, including observable virtual getter callbacks. */
class TestPatternRendererIngress {
    private static final PatternAtlas.Entry ENTRY = new PatternAtlas.Entry(1, 0, 1, 0, 0, .125f, .25f, .625f, .75f);
    private static final List<String> ADMISSION = List.of("priority", "shader", "underwater", "ghost", "colour", "alpha");
    private SamplingManager manager;
    private SonicConfigurationService config;
    private BatchedPatternRenderer batched;
    private InstancedPatternRenderer instanced;

    @BeforeEach void setUp() throws Exception {
        manager = new SamplingManager();
        manager.initHeadless();
        config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        batched = new BatchedPatternRenderer(manager, config);
        instanced = new InstancedPatternRenderer(manager, config);
        set(instanced, "supported", true);
        set(manager, "batchedRenderer", batched);
        set(manager, "instancedPatternRenderer", instanced);
        PatternAtlas atlas = new PatternAtlas(8, 8);
        assertEquals(0, atlas.cachePatternHeadless(new Pattern(), 0).atlasIndex());
        assertEquals(1, atlas.cachePatternHeadless(new Pattern(), PatternAtlasRange.OBJECTS.base()).atlasIndex());
        set(manager, "patternAtlas", atlas);
        set(manager, "combinedPaletteTextureId", 1);
        manager.setHeadlessMode(false);
        manager.events.clear();
    }

    @AfterEach void tearDown() {
        manager.callback = ignored -> {};
        manager.setHeadlessMode(true);
        manager.cleanup();
    }

    @Test void admissionCallbacksPrecedeCachedReadsAndAppendDoesNotRevalidate() throws Exception {
        for (boolean strip : new boolean[]{false, true}) {
            PatternDesc desc = cachedDesc();
            instanced.beginBatch(); manager.events.clear();
            manager.callback = name -> {
                if (name.equals("alpha")) {
                    desc.setHFlip(true); desc.setVFlip(true); desc.setPriority(true); desc.setPaletteIndex(2);
                    set(instanced, "batchGhostAlpha", .25f); // Changing admission state now must not trigger a second check.
                }
            };
            assertTrue(addInstanced(ENTRY, 7, desc, -3, 11, strip, 1));
            assertEquals(ADMISSION, manager.events, "priority=true bypasses the mask getter");
            float[] data = (float[]) get(instanced, "instanceData");
            assertEquals(-3f, data[0]); assertEquals(strip ? 211f : 205f, data[1]);
            assertEquals(8f, data[2]); assertEquals(strip ? 2f : 8f, data[3]);
            assertEquals(.625f, data[4]); assertEquals(.125f, data[6]);
            assertEquals(strip ? .59375f : .25f, data[5]);
            assertEquals(strip ? .53125f : .75f, data[7]);
            assertEquals(7f, data[8], "palette argument was sampled before the callback");
            assertEquals(0f, data[9]); assertEquals(0x6000, desc.get());
            assertEquals(1, get(instanced, "instanceCount")); assertTrue(manager.commands.isEmpty());
            manager.callback = ignored -> {}; instanced.cancelBatch();
        }
    }

    @Test void admissionKeepsTheOriginalReceiverWhenManagerCallbackReplacesItsField() throws Exception {
        for (boolean strip : new boolean[]{false, true}) {
            manager.beginPatternBatch();
            manager.callback = name -> { if (name.equals("alpha")) set(manager, "instancedPatternRenderer", null); };
            try {
                submit(cachedDesc(), 10, strip);
                assertEquals(1, get(instanced, "instanceCount"));
                assertTrue(manager.commands.isEmpty(), "the original receiver accepted the tile without fallback");
            } finally {
                manager.callback = ignored -> {};
                set(manager, "instancedPatternRenderer", instanced);
                manager.flushPatternBatch(); manager.discardQueuedCommands();
            }
        }
    }

    @Test void lowPriorityAloneSamplesTheAlreadyMappedGlobalMaskAfterAdmission() throws Exception {
        for (boolean strip : new boolean[]{false, true}) {
            instanced.beginBatch(); manager.events.clear();
            PatternDesc desc = cachedDesc();
            manager.setCurrentSpriteTileOcclusionPaletteMask(5);
            assertTrue(addInstanced(ENTRY, -13, desc, 0, 0, strip, -2));
            assertEquals(List.of("priority", "shader", "underwater", "ghost", "colour", "alpha", "mask"), manager.events);
            assertEquals(5f, ((float[]) get(instanced, "instanceData"))[9]);
            assertEquals(-13f, ((float[]) get(instanced, "instanceData"))[8]);
            instanced.cancelBatch();
        }
    }

    @Test void everyInstancedRejectionShortCircuitsNullDescriptorAndLaterGetters() throws Exception {
        for (boolean strip : new boolean[]{false, true}) for (int stop = 0; stop < 9; stop++) {
            instanced.beginBatch();
            switch (stop) {
                case 0 -> set(instanced, "batchActive", false);
                case 1 -> set(instanced, "instanceCount", 4096);
                case 2 -> set(instanced, "batchAtlasIndex", 1);
                case 3 -> set(instanced, "batchUsePriorityShader", true);
                case 4 -> set(instanced, "batchUseWaterShader", true);
                case 5 -> set(instanced, "batchUnderwaterPalette", true);
                case 6 -> set(instanced, "batchGhostEffectActive", true);
                case 7 -> set(instanced, "batchGhostColour", true);
                case 8 -> set(instanced, "batchGhostAlpha", .5f);
            }
            int count = (int) get(instanced, "instanceCount");
            float[] before = ((float[]) get(instanced, "instanceData")).clone();
            manager.events.clear();
            assertFalse(addInstanced(ENTRY, 7, null, 0, 0, strip, 0), "rejection " + stop);
            assertEquals(stop < 3 ? List.of() : ADMISSION.subList(0, stop - 2), manager.events);
            assertEquals(count, get(instanced, "instanceCount"));
            assertArrayEquals(before, (float[]) get(instanced, "instanceData"));
            assertTrue(manager.commands.isEmpty());
        }
    }

    @Test void throwingAdmissionAndConditionalMaskLeaveArraysCountsAndQueuesUntouched() throws Exception {
        for (boolean strip : new boolean[]{false, true}) for (String throwing : List.of("priority", "shader", "underwater", "ghost", "colour", "alpha", "mask")) {
            instanced.beginBatch(); manager.events.clear();
            IllegalStateException expected = new IllegalStateException(throwing);
            manager.callback = name -> { if (name.equals(throwing)) throw expected; };
            float[] before = ((float[]) get(instanced, "instanceData")).clone();
            assertSame(expected, assertThrows(IllegalStateException.class,
                    () -> addInstanced(ENTRY, 7, cachedDesc(), 0, 0, strip, 0)));
            assertEquals(0, get(instanced, "instanceCount"));
            assertArrayEquals(before, (float[]) get(instanced, "instanceData")); assertTrue(manager.commands.isEmpty());
            manager.callback = ignored -> {};
        }
    }

    @Test void entryNullWinsOverDescriptorNullOnlyAfterAdmissionPrefix() throws Exception {
        for (boolean strip : new boolean[]{false, true}) {
            instanced.cancelBatch();
            assertFalse(addInstanced(null, 7, null, 0, 0, strip, 0));
            instanced.beginBatch(); manager.events.clear();
            assertTrue(assertThrows(NullPointerException.class,
                    () -> addInstanced(null, 7, null, 0, 0, strip, 0)).getMessage().contains("entry"));
            assertTrue(manager.events.isEmpty()); assertEquals(0, get(instanced, "instanceCount"));
            assertThrows(NullPointerException.class, () -> addInstanced(ENTRY, 7, null, 0, 0, strip, 0));
            batched.beginBatch();
            assertTrue(assertThrows(NullPointerException.class,
                    () -> addBatched(null, 7, null, 0, 0, strip, 0)).getMessage().contains("entry"));
            assertEquals(0, batched.getPatternCount());
            set(batched, "patternCount", 4096);
            assertFalse(addBatched(null, 7, null, 0, 0, strip, 0));
            batched.cleanupHeadless();
        }
        batched.beginShadowBatch();
        assertTrue(assertThrows(NullPointerException.class,
                () -> addShadow(null, null, 0, 0)).getMessage().contains("entry"));
        assertFalse(addShadow(new PatternAtlas.Entry(1, 1, 1, 0, 0, 0, 0, 1, 1), null, 0, 0));
        assertEquals(0, batched.getPatternCount()); assertTrue(manager.commands.isEmpty());
    }

    @Test void batchedAttributesUseCachesAndManagerStateIsCapturedOnlyAtEnd() throws Exception {
        manager.callback = ignored -> fail("batched admission/append must not sample manager state");
        PatternDesc desc = cachedDesc(); desc.setHFlip(true); desc.setVFlip(true); desc.setPriority(true);
        batched.beginBatch();
        assertTrue(addBatched(ENTRY, 7, desc, Integer.MAX_VALUE, Integer.MIN_VALUE, false, 0));
        assertTrue(addBatched(ENTRY, -13, desc, -3, 11, true, -1));
        assertEquals((float) (224 - Integer.MIN_VALUE - 8), ((float[]) get(batched, "vertexData"))[1]);
        assertEquals((float) (Integer.MAX_VALUE + 8), ((float[]) get(batched, "vertexData"))[2]);
        assertEquals(7f, ((float[]) get(batched, "paletteCoordData"))[0]);
        assertEquals(-13f, ((float[]) get(batched, "paletteCoordData"))[6]);
        assertEquals(.84375f, ((float[]) get(batched, "texCoordData"))[13], "unclamped strip -1");
        manager.callback = ignored -> {}; manager.events.clear();
        GLCommandable command = batched.endBatch();
        assertEquals(List.of("priority", "mask", "ghost", "colour", "alpha"), manager.events);
        command.discard();
        batched.beginShadowBatch();
        manager.callback = ignored -> fail("shadow never samples palette/priority or manager state");
        assertTrue(addShadow(ENTRY, desc, 1, 2));
        batched.endShadowBatch().discard();
    }

    @Test void capacityRetriesRetainBatchedPaletteButResampleInstancedPalette() throws Exception {
        for (boolean useInstanced : new boolean[]{false, true}) for (boolean strip : new boolean[]{false, true}) {
            manager.setInstancedBatchingEnabled(useInstanced);
            PatternDesc desc = cachedDesc();
            manager.beginPatternBatch();
            for (int i = 0; i < 4096; i++) submit(desc, i, strip);
            assertTrue(manager.commands.isEmpty());
            manager.callback = name -> { if (name.equals("alpha")) {
                desc.setPaletteIndex(2); desc.setHFlip(true); desc.setVFlip(true);
            }};
            submit(desc, 4096, strip);
            manager.callback = ignored -> {};
            if (!useInstanced && !strip) assertTrue(batched.isBatchActive(), "whole fallback restarts");
            manager.flushPatternBatch();
            assertEquals(2, manager.commands.size());
            Object first = manager.commands.getFirst(), last = manager.commands.getLast();
            assertEquals(4096, get(first, useInstanced ? "instanceCount" : "patternCount"));
            if (useInstanced) {
                assertEquals(1, get(last, "instanceCount"));
                assertEquals(2f, ((FloatBuffer) get(last, "instanceBuffer")).get(8));
            } else if (strip) {
                assertEquals(1, get(last, "patternCount"));
                assertEquals(7f, ((FloatBuffer) get(last, "paletteCoordBuffer")).get(0));
            } else {
                assertInstanceOf(PatternRenderCommand.class, last);
                assertEquals(2, get(last, "paletteIndex"));
            }
            manager.discardQueuedCommands();
        }
    }

    @Test void rejectedFreshInstancedBatchKeepsDistinctWholeAndStripRestartTails() throws Exception {
        RejectingInstanced rejecting = new RejectingInstanced(manager, config);
        set(rejecting, "supported", true); set(manager, "instancedPatternRenderer", rejecting);
        for (boolean strip : new boolean[]{false, true}) {
            rejecting.begins = 0;
            manager.beginPatternBatch(); submit(cachedDesc(), 10, strip);
            assertEquals(strip ? 3 : 2, rejecting.begins);
            assertEquals(1, manager.commands.size()); assertInstanceOf(PatternRenderCommand.class, manager.commands.getFirst());
            assertTrue(rejecting.isBatchActive()); assertEquals(0, get(rejecting, "instanceCount"));
            manager.discardQueuedCommands(); manager.flushPatternBatch();
        }
    }

    @Test void shadowFreshRejectionFlushesPrefixThenThrowsWithoutAppending() throws Exception {
        RejectingShadow rejecting = new RejectingShadow(manager, config);
        set(manager, "batchedRenderer", rejecting);
        manager.beginShadowBatch(); manager.addShadowPattern(0, cachedDesc(), 1, 2);
        rejecting.reject = true;
        assertEquals("Unable to add shadow pattern to a fresh batch", assertThrows(IllegalStateException.class,
                () -> manager.addShadowPattern(PatternAtlasRange.OBJECTS.base(), cachedDesc(), 3, 4)).getMessage());
        assertEquals(1, manager.commands.size()); assertEquals(1, get(manager.commands.getFirst(), "patternCount"));
        assertEquals(0, rejecting.getPatternCount()); assertTrue(rejecting.isShadowBatchActive());
    }

    @Test void fullSatTileReadsReusableDescriptorAfterAdmissionCallbacks() throws Exception {
        manager.callback = name -> { if (name.equals("alpha")) {
            PatternDesc scratch = (PatternDesc) get(manager, "reusableReplayDesc");
            scratch.setHFlip(true); scratch.setVFlip(true); scratch.setPriority(true); scratch.setPaletteIndex(7);
        }};
        manager.beginSpriteSatCollection();
        manager.submitSpriteSatPiece(piece());
        manager.endSpriteSatCollectionAndReplay();
        assertEquals(1, manager.commands.size());
        FloatBuffer data = (FloatBuffer) get(manager.commands.getFirst(), "instanceBuffer");
        assertEquals(0f, data.get(9)); assertEquals(1f, data.get(8), "SAT keeps the decoder's explicit palette argument");
        assertEquals(.99875f, data.get(4)); assertEquals(.00125f, data.get(6));
        assertEquals(.00125f, data.get(5)); assertEquals(.99875f, data.get(7));
    }

    @Test void satFreshRejectionFallsBackAndThrowingAdmissionCancelsAndRestoresCallerState() throws Exception {
        RejectingInstanced rejecting = new RejectingInstanced(manager, config);
        set(rejecting, "supported", true); set(manager, "instancedPatternRenderer", rejecting);
        manager.setUseSpritePriorityShader(true); manager.setCurrentSpriteTileOcclusionPaletteMask(5);
        manager.beginSpriteSatCollection(); manager.submitSpriteSatPiece(piece()); manager.endSpriteSatCollectionAndReplay();
        assertEquals(2, rejecting.begins); assertFalse(rejecting.isBatchActive());
        assertInstanceOf(PatternRenderCommand.class, manager.commands.getFirst()); manager.discardQueuedCommands();
        set(manager, "instancedPatternRenderer", instanced);
        IllegalStateException expected = new IllegalStateException("SAT admission");
        int[] alphaReads = {0};
        manager.callback = name -> { if (name.equals("alpha") && ++alphaReads[0] == 2) throw expected; };
        manager.beginSpriteSatCollection(); manager.submitSpriteSatPiece(piece());
        assertSame(expected, assertThrows(IllegalStateException.class, manager::endSpriteSatCollectionAndReplay));
        manager.callback = ignored -> {};
        assertFalse(instanced.isBatchActive()); assertEquals(0, get(instanced, "instanceCount"));
        assertTrue(manager.commands.isEmpty()); assertTrue(manager.isUseSpritePriorityShader());
        assertEquals(15, manager.getCurrentSpriteTileOcclusionPaletteMask(), "SAT entry resets the global override before replay");
    }

    @Test void explicitConstructorsFailManagerBeforeConfigurationAndHeightBeforeDebug() {
        assertEquals("graphicsManager", assertThrows(NullPointerException.class,
                () -> new BatchedPatternRenderer(null, null)).getMessage());
        assertEquals("graphicsManager", assertThrows(NullPointerException.class,
                () -> new InstancedPatternRenderer(null, null)).getMessage());
        assertEquals("configService", assertThrows(NullPointerException.class,
                () -> new BatchedPatternRenderer(manager, null)).getMessage());
        assertEquals("configService", assertThrows(NullPointerException.class,
                () -> new InstancedPatternRenderer(manager, null)).getMessage());
        SonicConfigurationService throwing = mock(SonicConfigurationService.class);
        IllegalStateException expected = new IllegalStateException("height");
        when(throwing.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS)).thenThrow(expected);
        assertSame(expected, assertThrows(IllegalStateException.class, () -> new BatchedPatternRenderer(manager, throwing)));
        assertSame(expected, assertThrows(IllegalStateException.class, () -> new InstancedPatternRenderer(manager, throwing)));
        verify(throwing, never()).getBoolean(any());
    }

    private boolean addInstanced(PatternAtlas.Entry entry, int palette, PatternDesc desc, int x, int y, boolean strip, int index) {
        return invokeAdapter(strip ? "tryAddInstancedStrip" : "tryAddInstancedPattern", entry, palette, desc, x, y, strip, index);
    }
    private boolean addBatched(PatternAtlas.Entry entry, int palette, PatternDesc desc, int x, int y, boolean strip, int index) {
        return invokeAdapter(strip ? "tryAddBatchedStrip" : "tryAddBatchedPattern", entry, palette, desc, x, y, strip, index);
    }
    private boolean addShadow(PatternAtlas.Entry entry, PatternDesc desc, int x, int y) {
        try {
            var method = GraphicsManager.class.getDeclaredMethod("tryAddBatchedShadow", PatternAtlas.Entry.class, PatternDesc.class, int.class, int.class);
            method.setAccessible(true);
            return (boolean) method.invoke(manager, entry, desc, x, y);
        } catch (java.lang.reflect.InvocationTargetException failure) { throwCause(failure.getCause()); return false; }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    // The characterized operation moved to private GM ingress; only this invocation seam changes.
    private boolean invokeAdapter(String name, PatternAtlas.Entry entry, int palette, PatternDesc desc, int x, int y, boolean strip, int index) {
        try {
            Class<?>[] types = strip
                    ? new Class<?>[]{PatternAtlas.Entry.class, int.class, PatternDesc.class, int.class, int.class, int.class}
                    : new Class<?>[]{PatternAtlas.Entry.class, int.class, PatternDesc.class, int.class, int.class};
            var method = GraphicsManager.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return (boolean) method.invoke(manager, strip ? new Object[]{entry, palette, desc, x, y, index}
                    : new Object[]{entry, palette, desc, x, y});
        } catch (java.lang.reflect.InvocationTargetException failure) { throwCause(failure.getCause()); return false; }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void throwCause(Throwable cause) {
        if (cause instanceof RuntimeException failure) throw failure;
        if (cause instanceof Error failure) throw failure;
        throw new AssertionError(cause);
    }
    private void submit(PatternDesc desc, int x, boolean strip) {
        if (strip) manager.renderStripPatternWithId(0, desc, x, 10, 1);
        else manager.renderPatternWithId(0, desc, x, 10);
    }
    private static PatternDesc cachedDesc() {
        PatternDesc desc = new PatternDesc(0x6000);
        desc.setPatternIndex(99); desc.setPaletteIndex(7); desc.setHFlip(false); desc.setVFlip(false); desc.setPriority(false);
        return desc;
    }
    private static PreparedPiece piece() {
        return new PreparedPiece(10, 20, 1, 1, 0, 0, 1, false, false, false, false,
                SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, "ingress");
    }
    private static Object get(Object target, String name) {
        try { Field field = find(target, name); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void set(Object target, String name, Object value) {
        try { find(target, name).set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Field find(Object target, String name) throws NoSuchFieldException {
        Class<?> type = target.getClass();
        while (type != null) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static class SamplingManager extends GraphicsManager {
        final List<String> events = new ArrayList<>();
        Consumer<String> callback = ignored -> {};
        private void sample(String name) { events.add(name); callback.accept(name); }
        @Override public boolean isUseSpritePriorityShader() { sample("priority"); return super.isUseSpritePriorityShader(); }
        @Override public ShaderProgram getShaderProgram() { sample("shader"); return super.getShaderProgram(); }
        @Override public boolean isUseUnderwaterPaletteForBackground() { sample("underwater"); return super.isUseUnderwaterPaletteForBackground(); }
        @Override public boolean isGhostRenderEffectActive() { sample("ghost"); return super.isGhostRenderEffectActive(); }
        @Override public boolean isGhostRenderColourPreserved() { sample("colour"); return super.isGhostRenderColourPreserved(); }
        @Override public float getGhostRenderAlpha() { sample("alpha"); return super.getGhostRenderAlpha(); }
        @Override public int getCurrentSpriteTileOcclusionPaletteMask() { sample("mask"); return super.getCurrentSpriteTileOcclusionPaletteMask(); }
    }
    private static final class RejectingInstanced extends InstancedPatternRenderer {
        int begins;
        RejectingInstanced(GraphicsManager manager, SonicConfigurationService config) { super(manager, config); }
        @Override public void beginBatch(int page) { super.beginBatch(page); begins++; set(this, "batchAtlasIndex", page + 1); }
    }
    private static final class RejectingShadow extends BatchedPatternRenderer {
        boolean reject;
        RejectingShadow(GraphicsManager manager, SonicConfigurationService config) { super(manager, config); }
        @Override public void beginShadowBatch(int page) { super.beginShadowBatch(reject ? page + 1 : page); }
    }
}
