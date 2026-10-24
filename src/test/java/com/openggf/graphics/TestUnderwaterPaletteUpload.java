package com.openggf.graphics;

import com.openggf.game.GameId;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

class TestUnderwaterPaletteUpload {
    private GraphicsManager graphics;
    private RecordingUnderwaterUploadOps stagingUploads;

    @AfterEach
    void resetState() {
        if (graphics != null) {
            graphics.clearPaletteTextures();
        }
        RenderContext.reset();
    }

    @Test
    void fromWhiteFadesTheDerivedWaterTargetRatherThanChangingTheTintRatio() {
        assertNativeWhiteDerivedTarget(PaletteFadePresentation.Mode.FROM_WHITE,
                new int[]{219, 219, 219, 255});
    }

    @Test
    void toWhiteFadesTheDerivedWaterTargetRatherThanChangingTheTintRatio() {
        assertNativeWhiteDerivedTarget(PaletteFadePresentation.Mode.TO_WHITE,
                new int[]{36, 0, 0, 255});
    }

    private void assertNativeWhiteDerivedTarget(PaletteFadePresentation.Mode mode, int[] expected) {
        graphics = new GraphicsManager();
        graphics.initHeadless();
        RecordingUnderwaterUploadOps uploads = new RecordingUnderwaterUploadOps();
        graphics.setUnderwaterPaletteUploadOps(uploads);
        RenderContext donor = RenderContext.getOrCreateDonor(GameId.S2);
        donor.setPalette(0, paletteWithColor(1, 255, 0, 0));
        Palette[] underwater = fourRows();
        underwater[0] = paletteWithColor(1, 0, 0, 0);
        Palette normal = paletteWithColor(1, 36, 0, 0);
        // This water transform makes the donor's red target black. The native
        // white routine steps that final target, not the inputs of the tint ratio.
        graphics.setPaletteFadePresentation(mode, 1, PaletteFadePresentation.ALL_LINES);
        assertEquals(mode, graphics.displayedPaletteFade().mode());
        assertEquals(1, graphics.displayedPaletteFade().steps());
        assertTrue(graphics.displayedPaletteFade().affects(donor.getPaletteLineBase()));
        graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        assertEquals(1, graphics.getUnderwaterDerivedRowRecomputeCount(), "the actual donor row was derived");
        assertEquals(255, donor.getPalette(0).getColor(1).r & 255, "the donor target stays raw");
        assertArrayEquals(expected, rgbaAt(ByteBuffer.wrap(uploads.lastUpload),
                donor.getPaletteLineBase() * 16 + 1));
    }

    @Test
    void wholePaletteWhiteFadeAlsoReachesScopedRowsBeyondTheMaskWidth() {
        graphics = new GraphicsManager();
        graphics.initHeadless();
        RecordingUnderwaterUploadOps uploads = new RecordingUnderwaterUploadOps();
        graphics.setUnderwaterPaletteUploadOps(uploads);
        Palette black = paletteWithColor(1, 0, 0, 0);
        int bank = 0;
        for (int i = 0; i < 9; i++) {
            bank = graphics.scopedPaletteBank(new Object(), new Palette[]{black, black, black, black});
        }
        assertTrue(bank >= Integer.SIZE, "exercise a real private row beyond the legacy bit mask");
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_WHITE, 0,
                PaletteFadePresentation.ALL_LINES);
        assertTrue(graphics.displayedPaletteFade().affects(bank));
        graphics.cacheUnderwaterPaletteTexture(null, null);
        assertArrayEquals(new int[]{255, 255, 255, 255}, rgbaAt(ByteBuffer.wrap(uploads.lastUpload), bank * 16 + 1));
    }

    @Test
    void whiteWaterPublicationKeepsRawWritersPendingAndRestoresTheDisplayedGeneration() {
        graphics = new GraphicsManager();
        graphics.initHeadless();
        RecordingUnderwaterUploadOps uploads = new RecordingUnderwaterUploadOps();
        graphics.setUnderwaterPaletteUploadOps(uploads);
        RenderContext donor = RenderContext.getOrCreateDonor(GameId.S2);
        donor.setPalette(0, paletteWithColor(1, 255, 0, 0));
        Palette normal = paletteWithColor(1, 36, 0, 0);
        Palette[] water = fourRows();
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_WHITE, 1,
                PaletteFadePresentation.ALL_LINES);
        graphics.cacheUnderwaterPaletteTexture(water, normal);
        PaletteUploadPresentation.publishAndLatch(graphics);
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_WHITE, 2,
                PaletteFadePresentation.ALL_LINES);
        var displayed = PaletteUploadPresentation.captureDisplayedFade(graphics);
        assertTrue(displayed.pending());

        // A later raw writer and a newly admitted private bank must not advance CRAM.
        water[0].setColor(1, new Palette.Color((byte) 36, (byte) 0, (byte) 0));
        Palette black = paletteWithColor(1, 0, 0, 0);
        int bank = graphics.scopedPaletteBank(new Object(), new Palette[]{black, black, black, black});
        graphics.refreshScopedUnderwaterResidency();
        assertArrayEquals(new int[]{219, 219, 219, 255}, rgbaAt(ByteBuffer.wrap(uploads.lastUpload), bank * 16 + 1));
        assertArrayEquals(new int[]{219, 219, 219, 255}, rgbaAt(ByteBuffer.wrap(uploads.lastUpload), donor.getPaletteLineBase() * 16 + 1));
        PaletteUploadPresentation.publishAndLatch(graphics);
        graphics.refreshScopedUnderwaterResidency();
        assertArrayEquals(new int[]{182, 182, 182, 255}, rgbaAt(ByteBuffer.wrap(uploads.lastUpload), bank * 16 + 1));

        graphics.cacheUnderwaterPaletteTexture(water, normal);
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.FROM_WHITE, 3,
                PaletteFadePresentation.ALL_LINES);
        PaletteUploadPresentation.restoreDisplayedFade(graphics, displayed);
        graphics.refreshScopedUnderwaterResidency();
        assertArrayEquals(new int[]{219, 219, 219, 255}, rgbaAt(ByteBuffer.wrap(uploads.lastUpload), bank * 16 + 1));
        PaletteUploadPresentation.publishAndLatch(graphics);
        graphics.refreshScopedUnderwaterResidency();
        assertArrayEquals(new int[]{146, 146, 146, 255}, rgbaAt(ByteBuffer.wrap(uploads.lastUpload), bank * 16 + 1));
        assertArrayEquals(new int[]{255, 146, 146, 255}, rgbaAt(ByteBuffer.wrap(uploads.lastUpload), donor.getPaletteLineBase() * 16 + 1),
                "only the next native publication admits the changed water target");
    }

    @Test
    void whiteWaterCacheUsesTheSaturatedColourStepRatherThanAWrappedByte() {
        graphics = new GraphicsManager();
        graphics.initHeadless();
        RecordingUnderwaterUploadOps uploads = new RecordingUnderwaterUploadOps();
        graphics.setUnderwaterPaletteUploadOps(uploads);
        RenderContext donor = RenderContext.getOrCreateDonor(GameId.S2);
        donor.setPalette(0, paletteWithColor(1, 255, 0, 0));
        Palette normal = paletteWithColor(1, 36, 0, 0);
        Palette[] water = fourRows();
        for (var mode : new PaletteFadePresentation.Mode[]{PaletteFadePresentation.Mode.FROM_WHITE,
                PaletteFadePresentation.Mode.TO_WHITE}) {
            graphics.setPaletteFadePresentation(mode, 0, PaletteFadePresentation.ALL_LINES);
            graphics.cacheUnderwaterPaletteTexture(water, normal);
            graphics.setPaletteFadePresentation(mode, 256, PaletteFadePresentation.ALL_LINES);
            graphics.cacheUnderwaterPaletteTexture(water, normal);
            int channel = mode == PaletteFadePresentation.Mode.FROM_WHITE ? 0 : 255;
            assertArrayEquals(new int[]{channel, channel, channel, 255},
                    rgbaAt(ByteBuffer.wrap(uploads.lastUpload), donor.getPaletteLineBase() * 16 + 1),
                    "public steps saturate at the native endpoint; a byte wrap must not reuse step zero");
        }
    }

    @Test
    void expansionToggleReuploadsDirectAndDerivedRowsWithoutChangingNativePaletteBytes() throws Exception {
        graphics = new GraphicsManager();
        graphics.initHeadless();
        RecordingUnderwaterUploadOps uploads = new RecordingUnderwaterUploadOps();
        graphics.setUnderwaterPaletteUploadOps(uploads);
        RenderContext donor = RenderContext.getOrCreateDonor(GameId.S2);
        Palette rawDonor = paletteWithColor(1, 181, 81, 41);
        donor.setPalette(0, rawDonor);
        Palette[] underwater = fourRows();
        underwater[0] = paletteWithColor(1, 100, 50, 25);
        Palette normal = paletteWithColor(1, 200, 100, 50);
        graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        byte[] full = uploads.lastUpload.clone();
        int derived = graphics.getUnderwaterDerivedRowRecomputeCount();
        graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.LIMITED);
        graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        assertEquals(2, uploads.uploadCount);
        assertEquals(derived + 1, graphics.getUnderwaterDerivedRowRecomputeCount());
        for (int i = 0; i < full.length; i++) {
            int expected = i % 4 == 3 ? full[i] & 255 : (int) Math.round((full[i] & 255) * 238.0 / 255);
            assertEquals(expected, uploads.lastUpload[i] & 255, "uploaded component " + i);
        }
        assertEquals(181, rawDonor.getColor(1).r & 255);
        assertEquals(100, underwater[0].getColor(1).r & 255);
        graphics.cacheUnderwaterPaletteTexture(copyRows(underwater), normal.deepCopy());
        assertEquals(2, uploads.uploadCount, "unchanged range/content reuses the uploaded texture");
        graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.FULL);
        graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        assertEquals(3, uploads.uploadCount);
        assertArrayEquals(full, uploads.lastUpload);
    }

    @Test
    void limitedExpansionFollowsFadeAndRetainsTransparentColourZero() throws Exception {
        graphics = new GraphicsManager();
        graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.LIMITED);
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.TO_BLACK, 1, 1);
        Palette[] underwater = fourRows();
        underwater[0] = paletteWithColor(1, 255, 255, 255);
        cacheUnderwaterPaletteTexture(underwater, new Palette());
        assertArrayEquals(new int[]{204, 238, 238, 255}, rgbaAt(uploadBuffer(graphics), 1));
        assertArrayEquals(new int[]{0, 0, 0, 0}, rgbaAt(uploadBuffer(graphics), 0));
    }

    @ParameterizedTest
    @EnabledOnOs(OS.LINUX)
    @CsvSource({
            "NONE,0,RAW_RGB,FULL,7565D8,6EFFA2,1E3264",
            "NONE,0,MD_ANALOG,LIMITED,4343BD,43DE76,111143",
            "TO_WHITE,2,RAW_RGB,FULL,B66DDB,B6FF92,6D246D",
            "TO_WHITE,2,MD_ANALOG,LIMITED,9743BD,97DE76,431143",
            "FROM_WHITE,3,MD_ANALOG,LIMITED,7676BD,76DE76,767676",
            "TO_BLACK,1,RAW_RGB,FULL,5E5BE5,49FF92,00246D"
    })
    void underwaterDonorTextureAndRenderedAppearanceKeepFadeAndProfileOrder(
            PaletteFadePresentation.Mode mode, int steps, DisplayColorProfile profile,
            DisplayColorExpansion expansion, String donorRgb, String appearanceRgb, String stockRgb) throws Exception {
        RenderContext.reset();
        try (var context = PresentationOffscreenTestContext.open(320, 224)) {
            graphics = new GraphicsManager();
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setBatchingEnabled(false);
                graphics.setViewport(0, 0, 320, 224);
                graphics.setProjectionWidth(320);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                graphics.setUseSpritePriorityShader(true);
                graphics.setWaterEnabled(true);
                graphics.setWaterlineScreenY(40);
                graphics.setWindowHeight(224);
                graphics.setScreenHeight(224);
                graphics.setDisplayColorProfile(profile);
                graphics.setDisplayColorExpansion(expansion);
                glDisable(GL_DITHER);

                Palette normal = paletteWithColor(1, 200, 100, 0);
                normal.colors[2] = new Palette.Color((byte) 0, (byte) 100, (byte) 200);
                Palette[] water = fourRows();
                water[0].colors[0] = new Palette.Color((byte) 255, (byte) 255, (byte) 255);
                water[0].colors[1] = new Palette.Color((byte) 100, (byte) 200, (byte) 80);
                water[0].colors[2] = new Palette.Color((byte) 30, (byte) 50, (byte) 100);
                water[0].colors[3] = new Palette.Color((byte) 255, (byte) 240, (byte) 200);
                Palette donorLine = paletteWithColor(1, 181, 81, 241);
                donorLine.colors[0] = new Palette.Color((byte) 181, (byte) 81, (byte) 241);
                RenderContext donor = RenderContext.getOrCreateDonor(GameId.S2);
                donor.setPalette(0, donorLine);
                graphics.cachePaletteTexture(normal, 0);
                graphics.cachePaletteTexture(donorLine, donor.getPaletteLineBase());
                Palette overrides = paletteWithColor(1, 221, 151, 181);
                var appearance = AppearancePalettes.acquire(graphics, null, 0, normal, true, overrides, 1 << 1);
                assertNotNull(appearance);
                int appearanceBase = appearance.context().getPaletteLineBase();

                graphics.setPaletteFadePresentation(mode, steps, PaletteFadePresentation.ALL_LINES);
                Integer texture = graphics.cacheUnderwaterPaletteTexture(water, normal);
                assertNotNull(texture);
                glBindTexture(GL_TEXTURE_2D, texture);
                int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
                ByteBuffer pixels = ByteBuffer.allocateDirect(16 * height * 4);
                glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                assertArrayEquals(rgbWithAlpha(donorRgb, 255), rgbaAt(pixels, donor.getPaletteLineBase() * 16 + 1));
                assertArrayEquals(rgbWithAlpha(appearanceRgb, 255), rgbaAt(pixels, appearanceBase * 16 + 1));
                assertArrayEquals(rgbWithAlpha(stockRgb, 255), rgbaAt(pixels, appearanceBase * 16 + 2));
                assertEquals(0, rgbaAt(pixels, donor.getPaletteLineBase() * 16)[3]);
                assertEquals(0, rgbaAt(pixels, appearanceBase * 16)[3]);

                Pattern pattern = new Pattern();
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    pattern.setPixel(x, y, (byte) (x < 4 ? 1 : x < 6 ? 0 : 2));
                }
                int patternId = PatternAtlasRange.OBJECTS.base();
                graphics.cachePatternTexture(pattern, patternId);
                glViewport(0, 0, 320, 224);
                glClearColor(3 / 255f, 5 / 255f, 7 / 255f, 1);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                graphics.executeCapturedCommands(() -> graphics.renderPatternRowsWithId(patternId, new PatternDesc(),
                        appearanceBase, false, false, true, 20, 60, 0, 8), 0, 0, 320, 224);
                glFinish();
                assertFramebufferRgb(22, 63, appearanceRgb);
                assertFramebufferRgb(26, 63, stockRgb);
                assertFramebufferRgb(24, 63, "030507");
                assertEquals(GL_NO_ERROR, glGetError());
                appearance.release();
            } finally {
                graphics.cleanup();
                graphics = null;
            }
        }
    }

    private static int[] rgbWithAlpha(String hex, int alpha) {
        int rgb = Integer.parseUnsignedInt(hex, 16);
        return new int[]{rgb >>> 16, (rgb >>> 8) & 255, rgb & 255, alpha};
    }

    private static void assertFramebufferRgb(int x, int y, String hex) {
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        glReadPixels(x, 223 - y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        assertArrayEquals(rgbWithAlpha(hex, 255), rgbaAt(pixel, 0), "framebuffer at " + x + "," + y);
    }

    @Test
    void unchangedContentReusesPreparedStaging() throws Exception {
        graphics = new GraphicsManager();
        Palette[] underwater = fourRows();
        Palette normal = paletteWithColor(1, 200, 100, 50);

        assertTrue(cacheUnderwaterPaletteTexture(underwater, normal));
        ByteBuffer firstBuffer = uploadBuffer(graphics);
        assertFalse(cacheUnderwaterPaletteTexture(copyRows(underwater), normal.deepCopy()),
                "a second shader consumer must reuse the frame's prepared upload");
        assertSame(firstBuffer, uploadBuffer(graphics));
    }

    @Test
    void realCacheBoundaryUploadsOnceForBothConsumersAndInvalidatesContributingContent() {
        graphics = new GraphicsManager();
        graphics.initHeadless();
        RecordingUnderwaterUploadOps uploads = new RecordingUnderwaterUploadOps();
        graphics.setUnderwaterPaletteUploadOps(uploads);

        RenderContext donor = RenderContext.getOrCreateDonor(GameId.S2);
        Palette donorRow = paletteWithColor(1, 180, 80, 40);
        donor.setPalette(0, donorRow);
        Palette[] underwater = fourRows();
        underwater[0] = paletteWithColor(1, 100, 50, 25);
        Palette normal = paletteWithColor(1, 200, 100, 50);

        Integer scalarShaderTexture = graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        Integer instancedShaderTexture = graphics.cacheUnderwaterPaletteTexture(underwater, normal);

        assertEquals(scalarShaderTexture, instancedShaderTexture,
                "both shader configuration paths must consume the same texture");
        assertEquals(scalarShaderTexture, uploads.lastTextureId);
        assertEquals(1, uploads.createCount);
        assertEquals(1, uploads.uploadCount, "unchanged second consumer must not upload again");
        assertEquals(16 * RenderContext.getTotalPaletteLines() * 4, uploads.lastUploadBytes);

        graphics.setDisplayColorProfile(com.openggf.graphics.color.DisplayColorProfile.MD_ANALOG);
        assertSingleNewUpload(uploads, () -> graphics.cacheUnderwaterPaletteTexture(underwater, normal));

        normal.getColor(1).r++;
        assertSingleNewUpload(uploads, () -> graphics.cacheUnderwaterPaletteTexture(underwater, normal));

        underwater[0].getColor(1).b++;
        assertSingleNewUpload(uploads, () -> graphics.cacheUnderwaterPaletteTexture(underwater, normal));

        donorRow.getColor(1).g++;
        assertSingleNewUpload(uploads, () -> graphics.cacheUnderwaterPaletteTexture(underwater, normal));
        assertEquals(1, uploads.createCount, "content changes must update the existing texture");
    }

    @Test
    void structurallyDifferentRowsCannotShareAnUploadKey() {
        graphics = new GraphicsManager();
        graphics.initHeadless();
        RecordingUnderwaterUploadOps uploads = new RecordingUnderwaterUploadOps();
        graphics.setUnderwaterPaletteUploadOps(uploads);

        RenderContext donor = RenderContext.getOrCreateDonor(GameId.S2);
        Palette x = paletteWithColor(1, 80, 40, 20);
        Palette y = paletteWithColor(1, 20, 40, 80);
        Palette normal = paletteWithColor(1, 100, 100, 100);

        // Rows 4-6: derived X, absent, direct Y.
        Palette[] firstLayout = new Palette[RenderContext.getTotalPaletteLines()];
        firstLayout[0] = normal.deepCopy();
        firstLayout[6] = y.deepCopy();
        donor.setPalette(0, x.deepCopy());
        graphics.cacheUnderwaterPaletteTexture(firstLayout, normal);
        byte[] firstUpload = uploads.lastUpload.clone();

        // Rows 4-6: absent, direct X, derived Y. The old variable-length key
        // serialized both layouts identically despite their different output rows.
        Palette[] secondLayout = new Palette[RenderContext.getTotalPaletteLines()];
        secondLayout[0] = normal.deepCopy();
        secondLayout[5] = x.deepCopy();
        donor.setPalette(0, null);
        donor.setPalette(2, y.deepCopy());
        graphics.cacheUnderwaterPaletteTexture(secondLayout, normal);

        assertEquals(2, uploads.uploadCount, "row case framing must force a new upload");
        assertFalse(java.util.Arrays.equals(firstUpload, uploads.lastUpload),
                "the two row layouts must produce different whole-texture bytes");
    }

    @Test
    void donorRowCacheRecomputesOnlyRowsWhoseContributingContentChanges() {
        graphics = new GraphicsManager();
        graphics.initHeadless();
        RecordingUnderwaterUploadOps uploads = new RecordingUnderwaterUploadOps();
        graphics.setUnderwaterPaletteUploadOps(uploads);

        RenderContext donor = RenderContext.getOrCreateDonor(GameId.S2);
        Palette donor0 = paletteWithColor(1, 180, 80, 40);
        Palette donor1 = paletteWithColor(1, 40, 80, 180);
        donor.setPalette(0, donor0);
        donor.setPalette(1, donor1);
        Palette normal = paletteWithColor(1, 200, 100, 50);
        Palette[] underwater = new Palette[RenderContext.getTotalPaletteLines()];
        underwater[0] = paletteWithColor(1, 100, 50, 25);

        graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        assertEquals(2, graphics.getUnderwaterDerivedRowRecomputeCount());

        donor.setPalette(0, donor0.deepCopy());
        donor.setPalette(1, donor1.deepCopy());
        graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        assertEquals(2, graphics.getUnderwaterDerivedRowRecomputeCount(),
                "equal replacement objects must reuse cached derived RGBA");

        donor.getPalette(0).getColor(1).r++;
        graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        assertEquals(3, graphics.getUnderwaterDerivedRowRecomputeCount(),
                "only the mutated donor row should be re-derived");

        normal.getColor(1).g++;
        graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        assertEquals(5, graphics.getUnderwaterDerivedRowRecomputeCount());

        underwater[0].getColor(1).b++;
        graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        assertEquals(7, graphics.getUnderwaterDerivedRowRecomputeCount());

        graphics.setDisplayColorProfile(com.openggf.graphics.color.DisplayColorProfile.MD_ANALOG);
        graphics.cacheUnderwaterPaletteTexture(underwater, normal);
        assertEquals(9, graphics.getUnderwaterDerivedRowRecomputeCount());
    }

    @Test
    void scopedPublishedBasisTracksNativePublicationAndExactWorldLifetime() throws Exception {
        graphics = new GraphicsManager();
        graphics.initHeadless();
        RecordingUnderwaterUploadOps uploads = new RecordingUnderwaterUploadOps();
        graphics.setUnderwaterPaletteUploadOps(uploads);
        Object world = new Object();
        graphics.bindScopedPaletteWorld(world);
        Palette normal = paletteWithColor(1, 200, 100, 50);
        Palette[] water = fourRows();
        water[0] = paletteWithColor(1, 100, 50, 25);
        graphics.cacheUnderwaterPaletteTexture(water, normal);
        Object owner = new Object();
        Palette row = paletteWithColor(1, 180, 80, 40);
        graphics.scopedPaletteBank(owner, new Palette[]{row, row, row, row});
        graphics.refreshScopedUnderwaterResidency();
        assertArrayEquals(new int[]{90, 40, 20, 255}, rgbaAt(uploadBuffer(graphics), 4 * 16 + 1));

        // Derived residency uses the fade at publication; a new native cache updates that basis.
        graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.TO_BLACK, 1, 1);
        graphics.scopedPaletteBank(owner, new Palette[]{row, row, row, row});
        graphics.refreshScopedUnderwaterResidency();
        assertArrayEquals(new int[]{90, 40, 20, 255}, rgbaAt(uploadBuffer(graphics), 4 * 16 + 1));
        graphics.cacheUnderwaterPaletteTexture(water, normal);
        ScopedUnderwaterBasis published = (ScopedUnderwaterBasis) fieldValue(graphics, "scopedUnderwaterBasis");
        assertEquals(PaletteFadePresentation.Mode.TO_BLACK, published.fadeMode());
        assertEquals(1, published.fadeSteps());

        graphics.clearScopedUnderwaterBasis(new Object());
        assertSame(published, fieldValue(graphics, "scopedUnderwaterBasis"), "another world cannot clear this publication");
        graphics.bindScopedPaletteWorld(new Object());
        assertNull(fieldValue(graphics, "scopedUnderwaterBasis"), "world rebinding drops the prior water basis");
        int count = uploads.uploadCount;
        graphics.scopedPaletteBank(owner, new Palette[]{row, row, row, row});
        graphics.refreshScopedUnderwaterResidency();
        assertEquals(count, uploads.uploadCount, "no stale water publication is replayed in the new world");
        graphics.cacheUnderwaterPaletteTexture(water, normal);
        assertNotNull(fieldValue(graphics, "scopedUnderwaterBasis"));
        graphics.clearPaletteTextures();
        assertNull(fieldValue(graphics, "scopedUnderwaterBasis"));
        assertNull(fieldValue(graphics, "scopedPaletteWorld"));
    }

    private static void assertSingleNewUpload(RecordingUnderwaterUploadOps uploads, Runnable cacheCall) {
        int before = uploads.uploadCount;
        cacheCall.run();
        cacheCall.run();
        assertEquals(before + 1, uploads.uploadCount);
    }

    @Test
    void inPlacePaletteMutationForcesAnotherUploadAndPreservesRgbaRules() throws Exception {
        graphics = new GraphicsManager();
        Palette[] underwater = fourRows();
        underwater[0].setColor(1, new Palette.Color((byte) 146, (byte) 73, (byte) 219));

        assertTrue(cacheUnderwaterPaletteTexture(underwater, new Palette()));
        ByteBuffer staged = uploadBuffer(graphics);
        assertArrayEquals(new int[] {0, 0, 0, 0}, rgbaAt(staged, 0));
        assertArrayEquals(new int[] {146, 73, 219, 255}, rgbaAt(staged, 1));

        underwater[0].getColor(1).g = (byte) 74;
        assertTrue(cacheUnderwaterPaletteTexture(underwater, new Palette()),
                "in-place palette cycling/fading must invalidate the content gate");
        assertArrayEquals(new int[] {146, 74, 219, 255}, rgbaAt(uploadBuffer(graphics), 1));
    }

    @Test
    void nativeStagingBufferIsReusedAndOnlyGrows() throws Exception {
        graphics = new GraphicsManager();
        Palette[] baseRows = fourRows();
        assertTrue(cacheUnderwaterPaletteTexture(baseRows, new Palette()));
        ByteBuffer baseBuffer = uploadBuffer(graphics);
        int baseCapacity = baseBuffer.capacity();

        baseRows[0].getColor(2).b = 1;
        assertTrue(cacheUnderwaterPaletteTexture(baseRows, new Palette()));
        assertSame(baseBuffer, uploadBuffer(graphics), "same-sized uploads must reuse native staging");

        RenderContext.getOrCreateDonor(GameId.S2);
        assertTrue(cacheUnderwaterPaletteTexture(baseRows, new Palette()));
        ByteBuffer grown = uploadBuffer(graphics);
        assertTrue(grown.capacity() > baseCapacity);

        RenderContext.reset();
        baseRows[0].getColor(2).b = 2;
        assertTrue(cacheUnderwaterPaletteTexture(baseRows, new Palette()));
        assertEquals(grown.capacity(), uploadBuffer(graphics).capacity(),
                "staging capacity must not shrink when donor rows disappear");
    }

    @Test
    void clearingPaletteTexturesReleasesStagingAndAllowsReinitialization() throws Exception {
        graphics = new GraphicsManager();
        assertTrue(cacheUnderwaterPaletteTexture(fourRows(), new Palette()));
        assertNotNull(uploadBuffer(graphics));

        graphics.clearPaletteTextures();
        assertNull(uploadBuffer(graphics));

        assertTrue(cacheUnderwaterPaletteTexture(fourRows(), new Palette()));
        assertNotNull(uploadBuffer(graphics));
        graphics.clearPaletteTextures();
    }

    @Test
    void headlessOwnerCleanupReleasesNativeStagingAndContentCache() throws Exception {
        graphics = new GraphicsManager();
        graphics.initHeadless();
        assertTrue(cacheUnderwaterPaletteTexture(fourRows(), new Palette()));
        assertNotNull(uploadBuffer(graphics));
        assertNotNull(fieldValue(graphics, "underwaterPaletteContentKey"));

        graphics.cleanup();

        assertNull(uploadBuffer(graphics), "owner cleanup must release native staging without a GL context");
        assertNull(fieldValue(graphics, "underwaterPaletteContentKey"),
                "owner cleanup must invalidate the corresponding content cache");
        assertFalse((Boolean) fieldValue(graphics, "underwaterPaletteContentKeyValid"));
    }

    private static Palette[] fourRows() {
        return new Palette[] {new Palette(), new Palette(), new Palette(), new Palette()};
    }

    private boolean cacheUnderwaterPaletteTexture(Palette[] palettes, Palette normalLine0) {
        if (stagingUploads == null) {
            graphics.initHeadless();
            stagingUploads = new RecordingUnderwaterUploadOps();
            graphics.setUnderwaterPaletteUploadOps(stagingUploads);
        }
        int uploadCount = stagingUploads.uploadCount;
        graphics.cacheUnderwaterPaletteTexture(palettes, normalLine0);
        return stagingUploads.uploadCount > uploadCount;
    }

    private static Palette[] copyRows(Palette[] source) {
        Palette[] copy = new Palette[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i].deepCopy();
        return copy;
    }

    private static Palette paletteWithColor(int index, int r, int g, int b) {
        Palette palette = new Palette();
        palette.setColor(index, new Palette.Color((byte) r, (byte) g, (byte) b));
        return palette;
    }

    private static ByteBuffer uploadBuffer(GraphicsManager graphics) throws Exception {
        return (ByteBuffer) fieldValue(graphics, "underwaterPaletteUploadBuffer");
    }

    private static Object fieldValue(GraphicsManager graphics, String name) throws Exception {
        Field field = GraphicsManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(graphics);
    }

    private static int[] rgbaAt(ByteBuffer buffer, int colorIndex) {
        int offset = colorIndex * 4;
        return new int[] {
                Byte.toUnsignedInt(buffer.get(offset)),
                Byte.toUnsignedInt(buffer.get(offset + 1)),
                Byte.toUnsignedInt(buffer.get(offset + 2)),
                Byte.toUnsignedInt(buffer.get(offset + 3))
        };
    }

    private static final class RecordingUnderwaterUploadOps implements UnderwaterPaletteUploadOps {
        private int createCount;
        private int uploadCount;
        private int lastTextureId;
        private int lastUploadBytes;
        private byte[] lastUpload;

        @Override
        public int createTexture() {
            createCount++;
            return 73;
        }

        @Override
        public void configureTexture(int textureId) {
            lastTextureId = textureId;
        }

        @Override
        public void uploadTexture(int textureId, int totalLines, ByteBuffer rgbaBytes) {
            uploadCount++;
            lastTextureId = textureId;
            lastUploadBytes = rgbaBytes.remaining();
            lastUpload = new byte[lastUploadBytes];
            rgbaBytes.duplicate().get(lastUpload);
            assertEquals(16 * totalLines * 4, lastUploadBytes);
        }
    }
}
