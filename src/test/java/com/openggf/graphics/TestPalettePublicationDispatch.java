package com.openggf.graphics;

import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Palette;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Characterizes the pre-extraction host calls, including deliberately hostile overrides. */
class TestPalettePublicationDispatch {
    private final Host graphics = new Host();
    private final Uploads uploads = new Uploads();

    TestPalettePublicationDispatch() {
        RenderContext.reset();
        graphics.initHeadless();
        graphics.paletteTextures().setUnderwaterPaletteUploadOps(uploads);
    }

    @AfterEach void close() {
        graphics.cleanup();
        RenderContext.reset();
    }

    @Test void privateUploadsBypassPublicOverridesButPendingWaterReentersTheHost() {
        Palette[] water = rows();
        graphics.cachePaletteTexture(water[0], 0);
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_BLACK, 7, 1);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.LIMITED);
        graphics.paletteTextures().scopedPaletteBank(new Object(), water);
        assertEquals(1, graphics.normalCalls, "private fade, preference and scoped uploads bypass public cache");
        graphics.cacheUnderwaterPaletteTexture(water, null);
        graphics.paletteTextures().setPaletteUploadLatched(true);
        graphics.cacheUnderwaterPaletteTexture(water, null);
        assertEquals(1, uploads.count);
        Palette.Color alias = water[0].getColor(1);
        alias.r = 100;
        graphics.replay = () -> {
            assertFalse(graphics.paletteTextures().isPaletteUploadLatched());
            assertEquals(100, alias.r & 255, "pending sources remain aliased until replay");
            alias.r = (byte) 200;
            graphics.cachePaletteTexture(water[1], 1);
            graphics.paletteTextures().setPaletteUploadLatched(false); // reentrancy must not replace the outer saved latch
        };
        graphics.paletteTextures().flushPendingPaletteUploads();
        assertEquals(3, graphics.waterCalls);
        assertEquals(2, graphics.normalCalls);
        assertEquals(2, uploads.count);
        assertTrue(graphics.paletteTextures().isPaletteUploadLatched());
        assertSame(water[0], graphics.paletteTextures().cachedPaletteLine(0));
    }

    @Test void throwingReplayRestoresTheLatchDiscardsWaterAndLeavesNormalPublicationPending() throws Exception {
        Palette[] water = rows();
        graphics.cacheUnderwaterPaletteTexture(water, null);
        graphics.paletteTextures().setPaletteUploadLatched(true);
        graphics.cacheUnderwaterPaletteTexture(water, null);
        PaletteUploadPresentation.cacheLevelPalette(graphics, water[2], 2);
        RuntimeException failure = new RuntimeException("replay failed");
        graphics.replay = () -> { throw failure; };
        assertSame(failure, assertThrows(RuntimeException.class, graphics.paletteTextures()::flushPendingPaletteUploads));
        assertTrue(graphics.paletteTextures().isPaletteUploadLatched());
        assertEquals(1, uploads.count);
        graphics.paletteTextures().flushPendingPaletteUploads();
        assertEquals(3, graphics.waterCalls, "failed pending water is consumed, never retried");
        assertEquals(1, uploads.count);
        assertSame(water[2], graphics.paletteTextures().cachedPaletteLine(2));
        assertTrue(((java.util.Map<?, ?>) PaletteTextureTestAccess.get(graphics, "paletteTextureMap"))
                .containsKey("palette_2"), "the later publication still uploads the pending normal row");
    }

    @Test void disposalRetainsPendingAliasesLatchPreferencesAndUploadPortUntilTheirNextPublication() {
        Palette[] water = rows();
        graphics.setDisplayColorExpansion(DisplayColorExpansion.LIMITED);
        graphics.cacheUnderwaterPaletteTexture(water, null);
        graphics.paletteTextures().setPaletteUploadLatched(true);
        graphics.cacheUnderwaterPaletteTexture(water, null);
        graphics.clearPaletteTextures();
        assertTrue(graphics.paletteTextures().isPaletteUploadLatched());
        assertNull(graphics.getUnderwaterPaletteTextureId());
        assertNull(graphics.paletteTextures().cachedPaletteLine(0));
        graphics.replay = () -> assertFalse(graphics.paletteTextures().isPaletteUploadLatched());
        graphics.paletteTextures().flushPendingPaletteUploads();
        assertEquals(2, uploads.count, "pending water and the injected port survive texture disposal");
        assertEquals(0, uploads.bytes[4] & 255); // RAW_RGB keeps black at zero even with LIMITED expansion
        assertEquals(DisplayColorExpansion.LIMITED, graphics.actualExpansion());
        assertEquals(1, graphics.clearCalls);
        graphics.resetPatternAndPaletteState();
        assertEquals(1, graphics.clearCalls, "headless reset returns before the virtual clear boundary");
        graphics.resetState();
        assertEquals(2, graphics.clearCalls, "resource reset still dispatches through the host clear");
    }

    @Test void sourceRowsAreSelectedBeforeRgbSamplingAndChannelsKeepTheirOriginalOrder() {
        Palette[] water = rows();
        Palette selected = water[1];
        List<String> reads = new ArrayList<>();
        water[0] = new Palette() {
            @Override public byte red(int i) {
                reads.add("r" + i);
                if (i == 0) { water[1] = new Palette(); selected.getColor(1).r = 91; }
                return super.red(i);
            }
            @Override public byte green(int i) { reads.add("g" + i); return super.green(i); }
            @Override public byte blue(int i) { reads.add("b" + i); return super.blue(i); }
        };
        graphics.cacheUnderwaterPaletteTexture(water, null);
        assertEquals(List.of("r0", "g0", "b0", "r1", "g1", "b1"), reads.subList(0, 6));
        assertEquals(91, uploads.bytes[(16 + 1) * 4] & 255,
                "the already selected row is sampled after the callback, not its array replacement");
        assertNotSame(selected, water[1]);
    }

    @Test void headlessNullNormalRowsRemainAcceptedAndRequestedFadeDoesNotReadVirtualAccessors() {
        graphics.cachePaletteTexture(null, 0);
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.TO_WHITE, 2, 1);
        graphics.clearPaletteFadePresentation();
        assertNull(graphics.paletteTextures().cachedPaletteLine(0));
        assertEquals(1, graphics.normalCalls);
        graphics.cacheUnderwaterPaletteTexture(null, null);
        assertEquals(1, uploads.count);
    }

    private static Palette[] rows() { return new Palette[]{new Palette(), new Palette(), new Palette(), new Palette()}; }

    private static final class Host extends GraphicsManager {
        int normalCalls, waterCalls, clearCalls;
        Runnable replay;
        @Override public void cachePaletteTexture(Palette p, int line) { normalCalls++; super.cachePaletteTexture(p, line); }
        @Override public Integer cacheUnderwaterPaletteTexture(Palette[] rows, Palette normal) {
            waterCalls++;
            Runnable action = replay;
            replay = null;
            if (action != null) action.run();
            return super.cacheUnderwaterPaletteTexture(rows, normal);
        }
        @Override public void clearPaletteTextures() { clearCalls++; super.clearPaletteTextures(); }
        @Override public boolean isHeadlessMode() { throw new AssertionError("private mode read became virtual"); }
        @Override public DisplayColorProfile getDisplayColorProfile() { throw new AssertionError("private profile read became virtual"); }
        @Override public DisplayColorExpansion getDisplayColorExpansion() { throw new AssertionError("private expansion read became virtual"); }
        @Override public PaletteFadePresentation getPaletteFadePresentation() { throw new AssertionError("private fade read became virtual"); }
        DisplayColorExpansion actualExpansion() { return super.getDisplayColorExpansion(); }
    }

    private static final class Uploads implements UnderwaterPaletteUploadOps {
        int count;
        byte[] bytes;
        public int createTexture() { return 73; }
        public void configureTexture(int id) { }
        public void uploadTexture(int id, int lines, ByteBuffer source) {
            count++;
            bytes = new byte[source.remaining()];
            source.get(bytes);
        }
    }
}
