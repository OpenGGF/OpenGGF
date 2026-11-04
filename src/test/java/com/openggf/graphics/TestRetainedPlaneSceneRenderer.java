package com.openggf.graphics;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30.*;

/**
 * Independent literal component vectors, authored before production exists.
 * Source J57ba058177b0ac1ce52a2c9dc04d27e546926324; accepted design SHA256
 * d41f76fe9139f0cc70be7de44cb22cf5fd1c635928e11c0fca41381a11cfffb1.
 * Pure shape/value cases need no GL. EGL cases require explicitly selected
 * surfaceless EGL and absent
 * DISPLAY/WAYLAND_DISPLAY. There is no media, World, CpuSat construction,
 * source routine replay, production expected-image helper or image output.
 * These invented component scenes are never described as live Past images.
 */
@Isolated
class TestRetainedPlaneSceneRenderer {
    private static final int WIDTH = 320, HEIGHT = 224, CELLS = 64 * 32;
    private static final int BASE = PatternAtlasRange.OBJECTS.base();
    private static final String BACKEND = "openggf.headless.gl";
    private static final int BLACK = 0x000000, RED = 0xFF0000, GREEN = 0x00FF00;
    private static final int BLUE = 0x0000FF, WHITE = 0xFFFFFF, CYAN = 0x00FFFF;
    private static final int MAGENTA = 0xFF00FF, YELLOW = 0xFFFF00;

    @Test void planeImageCopiesEveryCellAndWrapsNegativeAndPositiveCoordinates() {
        int[] words = new int[CELLS];
        for (int i = 0; i < words.length; i++) words[i] = (i * 37) & 0xFFFF;
        words[0] = 0; words[63] = 0xC807; words[31 * 64] = 0x6803; words[CELLS - 1] = 0xFFFF;
        int[] expected = words.clone();
        var plane = new RetainedPlaneSceneRenderer.PlaneImage(words);
        var same = new RetainedPlaneSceneRenderer.PlaneImage(expected);
        words[0] = 0xFFFF;
        int[] exported = plane.words64x32(); exported[63] = 0;
        assertArrayEquals(expected, plane.words64x32());
        assertEquals(same, plane); assertEquals(same.hashCode(), plane.hashCode());
        assertEquals(64, plane.widthTiles()); assertEquals(32, plane.heightTiles());
        assertNotEquals(0L, plane.revision());
        assertEquals(0, plane.descriptorAt(0, 0), "Word zero remains a native descriptor");
        assertEquals(0, plane.descriptorAt(512, 256));
        assertEquals(0xFFFF, plane.descriptorAt(-1, -1));
        assertEquals(0xC807, plane.descriptorAt(-8, 0));
        assertEquals(0x6803, plane.descriptorAt(0, -8));
        assertEquals(0xFFFF, plane.descriptorAt(511, 255));
    }

    @Test void planeImageRejectsIncompleteOrOversizedPhysicalImages() {
        assertThrows(IllegalArgumentException.class, () -> new RetainedPlaneSceneRenderer.PlaneImage(new int[CELLS - 1]));
        assertThrows(IllegalArgumentException.class, () -> new RetainedPlaneSceneRenderer.PlaneImage(new int[CELLS + 1]));
        assertThrows(NullPointerException.class, () -> new RetainedPlaneSceneRenderer.PlaneImage(null));
    }

    @Test void sceneCopiesSelectedArraysAndMapAndHasContentEquality() {
        int[] fg = filled(1), bg = filled(0), horizontal = new int[256];
        horizontal[0] = 0x80008000; horizontal[255] = 0xFEDC1234;
        byte[] palette = palette();
        var versions = new HashMap<Integer, SpritePresentation.PatternVersion>();
        versions.put(0, solid(1)); versions.put(1, solid(0));
        var scene = scene(fg, bg, horizontal, 0xFFFE8000, palette, versions, List.of());
        var same = scene(fg.clone(), bg.clone(), horizontal.clone(), 0xFFFE8000,
                palette.clone(), new HashMap<>(versions), List.of());
        int[] expectedH = horizontal.clone(); byte[] expectedPalette = palette.clone();
        fg[0] = 99; bg[0] = 99; horizontal[0] = 0; palette[0] = -1; versions.clear();
        scene.packedHscroll256()[0] = 0; scene.selectedPalette128()[0] = -1;
        assertArrayEquals(expectedH, scene.packedHscroll256());
        assertArrayEquals(expectedPalette, scene.selectedPalette128());
        assertEquals(1, scene.foreground().descriptorAt(0, 0));
        assertEquals(0, scene.background().descriptorAt(0, 0));
        assertEquals(Map.of(0, solid(1), 1, solid(0)), scene.nativePatterns());
        assertThrows(UnsupportedOperationException.class, () -> scene.nativePatterns().put(2, solid(2)));
        assertEquals(same, scene); assertEquals(same.hashCode(), scene.hashCode());
    }

    @Test void scenePreservesBothRaw8000WordsAndEveryUntransferredTailLong() {
        int[] horizontal = new int[256];
        for (int i = 0; i < horizontal.length; i++) horizontal[i] = 0x80000000 | (i << 16) | (0x8000 + i);
        horizontal[0] = 0x80008000; horizontal[223] = 0xFFFF8000;
        horizontal[224] = 0x12345678; horizontal[255] = 0x89ABCDEF;
        var s = scene(filled(1), filled(0), horizontal, 0x8000FFFF,
                palette(), Map.of(0, solid(1), 1, solid(0)), List.of());
        assertArrayEquals(horizontal, s.packedHscroll256());
        assertEquals(0x80008000, s.packedHscroll256()[0]);
        assertEquals(0x12345678, s.packedHscroll256()[224]);
        assertEquals(0x89ABCDEF, s.packedHscroll256()[255]);
        assertEquals(0x8000FFFF, s.vscrollLongBits());
    }

    @Test void sceneRejectsBadShapesMissingEnabledPlanesAndInvalidNativeIds() {
        var plane = new RetainedPlaneSceneRenderer.PlaneImage(new int[CELLS]);
        assertThrows(IllegalArgumentException.class, () -> new RetainedPlaneSceneRenderer.Scene(
                plane, plane, new int[255], 0, palette(), true, SpritePresentation.Frame.empty(), Map.of(0, solid(1))));
        assertThrows(IllegalArgumentException.class, () -> new RetainedPlaneSceneRenderer.Scene(
                plane, plane, new int[256], 0, new byte[127], true, SpritePresentation.Frame.empty(), Map.of(0, solid(1))));
        assertThrows(NullPointerException.class, () -> new RetainedPlaneSceneRenderer.Scene(
                null, plane, new int[256], 0, palette(), true, SpritePresentation.Frame.empty(), Map.of(0, solid(1))));
        assertThrows(IllegalArgumentException.class, () -> new RetainedPlaneSceneRenderer.Scene(
                plane, plane, new int[256], 0, palette(), true, SpritePresentation.Frame.empty(), Map.of(2048, solid(1))));
        assertThrows(IllegalArgumentException.class, () -> new RetainedPlaneSceneRenderer.Scene(
                plane, plane, new int[256], 0, palette(), true, SpritePresentation.Frame.empty(), Map.of(-1, solid(1))));
    }

    @Test void disabledSceneRetainsSelectionWithoutRequiringUnknownPlanesOrArt() {
        int[] h = new int[256]; h[0] = 0x80008000; h[255] = 0x13579BDF;
        var scene = new RetainedPlaneSceneRenderer.Scene(null, null, h, 0x12345678,
                palette(), false, SpritePresentation.Frame.empty(), Map.of());
        assertFalse(scene.displayEnabled()); assertNull(scene.foreground()); assertNull(scene.background());
        assertEquals(0x13579BDF, scene.packedHscroll256()[255]);
        assertEquals(0x12345678, scene.vscrollLongBits()); assertTrue(scene.nativePatterns().isEmpty());
    }

    @Test void constructorRejectsLogicOnlyGraphicsBeforeAnyNativeContextIsRequired() {
        var graphics = new GraphicsManager(); graphics.initHeadless();
        try {
            assertThrows(IllegalStateException.class, () -> new RetainedPlaneSceneRenderer(graphics, PatternAtlasRange.OBJECTS));
            assertFalse(graphics.isGlInitialized()); assertTrue(graphics.isHeadlessMode());
            assertTrue(graphics.getPatternAtlas().registeredRangesForTesting().isEmpty());
        } finally { graphics.getPatternAtlas().cleanupHeadless(); }
    }

    @Test void eglWordZeroReallyDrawsAndCloseRetiresOnlyTouchedEntriesAndSamples() throws Exception {
        try (var f = Fixture.open()) {
            var atlas = f.graphics.getPatternAtlas(); atlas.registerRange(PatternAtlasRange.HUD);
            int unrelatedId = PatternAtlasRange.HUD.base() + 19;
            f.graphics.cachePatternTexture(pattern(2), unrelatedId);
            var unrelatedEntry = atlas.getEntry(unrelatedId);
            var unrelatedSample = SpritePresentation.patternSample(f.graphics, unrelatedId);
            var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS);
            try {
                var scene = basic(); var captured = scene;
                ByteBuffer first = f.draw(renderer, scene);
                pixel(first, 0, 0, RED); pixel(first, 319, 223, RED);
                assertArrayEquals(bytes(first), bytes(f.draw(renderer, scene)));
                assertEquals(captured, scene); indexed(f.graphics, 0, 1);
                assertNotNull(SpritePresentation.patternSample(f.graphics, BASE));
                renderer.close(); renderer.close();
                assertNull(atlas.getEntry(BASE)); assertNull(atlas.getEntry(BASE + 1));
                assertNull(SpritePresentation.patternSample(f.graphics, BASE));
                assertNull(SpritePresentation.patternSample(f.graphics, BASE + 1));
                assertSame(unrelatedEntry, atlas.getEntry(unrelatedId));
                assertEquals(unrelatedSample, SpritePresentation.patternSample(f.graphics, unrelatedId));
                assertThrows(IllegalStateException.class, () -> renderer.draw(scene, 0, 0, () -> { }));
            } finally { renderer.close(); }
        }
    }

    @Test void eglBothActualPlanePassesPreserveFlipsPaletteAndPriority() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int[] fg = filled(1), bg = filled(0);
            fg[cell(1, 1)] = 2; fg[cell(2, 1)] = 2 | 0x0800; fg[cell(3, 1)] = 2 | 0x1000;
            fg[cell(4, 1)] = 3 | 0x2000;
            bg[cell(1, 3)] = 4 | 0x8000; fg[cell(1, 3)] = 5;
            bg[cell(2, 3)] = 4 | 0x8000; fg[cell(2, 3)] = 5 | 0x8000;
            bg[cell(3, 3)] = 4; fg[cell(3, 3)] = 5;
            bg[cell(4, 3)] = 4 | 0x8000; fg[cell(4, 3)] = 1 | 0x8000;
            var versions = Map.of(0, solid(1), 1, solid(0), 2, corners(), 3, solid(1), 4, solid(2), 5, solid(3));
            var pixels = f.draw(renderer, scene(fg, bg, new int[256], 0, palette(), versions, List.of()));
            pixel(pixels, 8, 8, GREEN); pixel(pixels, 15, 8, BLUE);
            pixel(pixels, 8, 15, WHITE); pixel(pixels, 15, 15, RED); pixel(pixels, 10, 10, RED);
            pixel(pixels, 16, 8, BLUE); pixel(pixels, 23, 8, GREEN);
            pixel(pixels, 16, 15, RED); pixel(pixels, 23, 15, WHITE);
            pixel(pixels, 24, 8, WHITE); pixel(pixels, 31, 8, RED);
            pixel(pixels, 24, 15, GREEN); pixel(pixels, 31, 15, BLUE);
            pixel(pixels, 32, 8, YELLOW);
            pixel(pixels, 8, 24, GREEN); pixel(pixels, 16, 24, BLUE);
            pixel(pixels, 24, 24, BLUE); pixel(pixels, 32, 24, GREEN);
            palettePixels(f.graphics);
        }
    }

    @Test void eglNativeFirstOpaqueSatWinnerIsChosenBeforePlaneOcclusion() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int[] fg = filled(1), bg = filled(0); fg[cell(2, 2)] = 6 | 0x8000;
            var versions = Map.of(0, solid(1), 1, solid(0), 6, solid(2), 7, solid(3), 8, leftHalfWhite());
            var rearHigh = tile(7, 16, 16, true);
            var frontLow = tile(8, 16, 16, false);
            // Frame is painter order; drawNative consumes the front opaque low entry first.
            var selected = scene(fg, bg, new int[256], 0, palette(), versions, List.of(rearHigh, frontLow));
            var actual = f.draw(renderer, selected);
            for (int y = 16; y < 24; y++) for (int x = 16; x < 24; x++)
                pixel(actual, x, y, x < 20 ? GREEN : BLUE);
            var reversed = scene(fg, bg, new int[256], 0, palette(), versions, List.of(frontLow, rearHigh));
            var control = f.draw(renderer, reversed);
            pixel(control, 16, 16, BLUE); assertFalse(Arrays.equals(bytes(actual), bytes(control)));
        }
    }

    @Test void eglEitherHighPlaneOccludesLowSatWhileHighSatBypassesBothMasks() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int[] fg = filled(1), bg = filled(0);
            bg[cell(1, 6)] = 2 | 0x8000; fg[cell(2, 6)] = 2 | 0x8000;
            bg[cell(3, 6)] = 2 | 0x8000; fg[cell(4, 6)] = 2 | 0x8000;
            var versions = Map.of(0, solid(1), 1, solid(0), 2, solid(2), 3, solid(3));
            var sprites = List.of(tile(3, 8, 48, false), tile(3, 16, 48, false),
                    tile(3, 24, 48, true), tile(3, 32, 48, true));
            var p = f.draw(renderer, scene(fg, bg, new int[256], 0, palette(), versions, sprites));
            pixel(p, 8, 48, GREEN); pixel(p, 16, 48, GREEN);
            pixel(p, 24, 48, BLUE); pixel(p, 32, 48, BLUE);
        }
    }

    @Test void eglNativeZeroMaskInputStillOccludesBeforeLaterHighPrioritySatAndRemainsUnchanged() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int[] fg = filled(1), bg = filled(0);
            bg[cell(1, 6)] = 2 | 0x8000; fg[cell(2, 6)] = 2 | 0x8000;
            bg[cell(3, 6)] = 2 | 0x8000; fg[cell(4, 6)] = 2 | 0x8000;
            fg[cell(8, 2)] = 2 | 0x8000;
            var versions = Map.of(0, solid(1), 1, solid(0), 2, solid(2), 3, solid(3), 4, leftHalfWhite());
            // Actual CD converter shape: native priority attributes with mask0.
            // The generic selected-native renderer owns normal plane occlusion;
            // the original immutable Scene/Frame must retain these zero masks.
            var lowB = new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, BASE + 3, 0,
                    false, false, false, 8, 48, 8, 8, true, 0, false, 1);
            var lowA = new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, BASE + 3, 0,
                    false, false, false, 16, 48, 8, 8, true, 0, false, 1);
            var highB = new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, BASE + 3, 0,
                    false, false, true, 24, 48, 8, 8, true, 0, false, 1);
            var highA = new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, BASE + 3, 0,
                    false, false, true, 32, 48, 8, 8, true, 0, false, 1);
            var rearHigh = new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, BASE + 3, 0,
                    false, false, true, 64, 16, 8, 8, true, 0, false, 1);
            var frontLow = new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, BASE + 4, 0,
                    false, false, false, 64, 16, 8, 8, true, 0, false, 1);
            var sprites = List.of(lowB, lowA, highB, highA, rearHigh, frontLow);
            var selected = scene(fg, bg, new int[256], 0, palette(), versions, sprites);
            var actual = f.draw(renderer, selected);
            pixel(actual, 8, 48, GREEN); pixel(actual, 16, 48, GREEN);
            pixel(actual, 24, 48, BLUE); pixel(actual, 32, 48, BLUE);
            for (int y = 16; y < 24; y++) for (int x = 64; x < 72; x++)
                pixel(actual, x, y, x < 68 ? GREEN : BLUE);
            assertEquals(sprites, selected.sprites().tiles());
            for (var tile : selected.sprites().tiles()) assertEquals(0, tile.occlusionMask());
            var contrast = f.draw(renderer, scene(fg, bg, new int[256], 0, palette(), versions,
                    List.of(lowB, lowA, highB, highA, rearHigh)));
            pixel(contrast, 64, 16, BLUE);
            assertFalse(Arrays.equals(bytes(actual), bytes(contrast)),
                    "Front low native opaque winner survives terrain rejection and excludes the rear high sprite");
        }
    }

    @Test void eglRaw8000AndBothPhysicalRingSeamsUseSelectedScrollWithoutInputMutation() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int[] fg = filled(4), bg = filled(0), h = new int[256];
            bg[cell(63, 31)] = 1; bg[cell(0, 31)] = 2; bg[cell(1, 31)] = 3;
            bg[cell(63, 0)] = 1; bg[cell(0, 0)] = 2; bg[cell(1, 0)] = 3;
            fg[cell(63, 31)] = 5 | 0x4000; fg[cell(0, 0)] = 6 | 0x6000;
            h[0] = 0x00018000; h[1] = 0x80000001; h[224] = 0xF00DABCD; h[255] = 0x12348000;
            byte[] palette = palette(); int[] oldFg = fg.clone(), oldBg = bg.clone(), oldH = h.clone();
            byte[] oldPalette = palette.clone();
            var versions = Map.of(0, solid(1), 1, solid(2), 2, solid(3), 3, solid(4),
                    4, solid(0), 5, solid(1), 6, solid(1));
            var selected = scene(fg, bg, h, 0x00FF00FF, palette, versions, List.of());
            var p = f.draw(renderer, selected);
            pixel(p, 0, 0, CYAN); pixel(p, 1, 0, BLUE); pixel(p, 7, 0, BLUE); pixel(p, 8, 0, WHITE);
            pixel(p, 0, 1, MAGENTA); pixel(p, 7, 1, MAGENTA); pixel(p, 8, 1, BLUE); pixel(p, 9, 1, WHITE);
            assertArrayEquals(bytes(p), bytes(f.draw(renderer, selected)));
            assertArrayEquals(oldFg, fg); assertArrayEquals(oldBg, bg); assertArrayEquals(oldH, h);
            assertArrayEquals(oldPalette, palette); assertArrayEquals(oldH, selected.packedHscroll256());
            assertEquals(0x00FF00FF, selected.vscrollLongBits());
        }
    }

    @Test void eglOldFrozenSceneReuploadsActualIndexedTexelsAfterLaterVersion() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            var red = basic();
            var green = scene(filled(1), filled(0), new int[256], 0, palette(), Map.of(0, solid(2), 1, solid(0)), List.of());
            var before = f.draw(renderer, red); pixel(before, 123, 111, RED); indexed(f.graphics, 0, 1);
            pixel(f.draw(renderer, green), 123, 111, GREEN); indexed(f.graphics, 0, 2);
            assertArrayEquals(bytes(before), bytes(f.draw(renderer, red))); indexed(f.graphics, 0, 1);
            assertEquals(Map.of(0, solid(1), 1, solid(0)), red.nativePatterns());
        }
    }

    @Test void eglDisabledRasterClearsTheActualPreviousPictureWithoutSamplingPlanes() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            pixel(f.draw(renderer, basic()), 0, 0, RED);
            var oldEntry = f.graphics.getPatternAtlas().getEntry(BASE);
            var disabled = new RetainedPlaneSceneRenderer.Scene(null, null, new int[256], 0x80008000,
                    palette(), false, SpritePresentation.Frame.empty(), Map.of());
            var black = f.draw(renderer, disabled);
            pixel(black, 0, 0, BLACK); pixel(black, 319, 223, BLACK);
            assertSame(oldEntry, f.graphics.getPatternAtlas().getEntry(BASE));
        }
    }

    @Test void eglOwnerRejectionBeforeBarrierCreatesNoPatternResidency() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            var sentinel = new IllegalStateException("source owner rejected before draw");
            var thrown = assertThrows(IllegalStateException.class, () -> renderer.draw(basic(), 0, 0, () -> { throw sentinel; }));
            assertSame(sentinel, thrown); noOwnerEntries(f.graphics);
        }
    }

    @Test void eglOwnerRecheckAfterOldFifoDrainsStillPrecedesPatternUpload() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            var oldExecuted = new AtomicInteger(); var validations = new AtomicInteger();
            f.graphics.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (x, y, w, h) -> oldExecuted.incrementAndGet()));
            var sentinel = new IllegalStateException("owner changed after real old-image barrier");
            var thrown = assertThrows(IllegalStateException.class, () -> renderer.draw(basic(), 0, 0, () -> {
                if (validations.incrementAndGet() == 2) {
                    assertEquals(1, oldExecuted.get(), "Second owner check follows actual old command execution");
                    throw sentinel;
                }
            }));
            assertSame(sentinel, thrown); assertEquals(1, oldExecuted.get()); noOwnerEntries(f.graphics);
        }
    }

    @Test void eglReentryRejectsBeforeAnySelectedPatternUpload() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            var scene = basic();
            assertThrows(IllegalStateException.class, () -> renderer.draw(scene, 0, 0,
                    () -> renderer.draw(scene, 0, 0, () -> { })));
            noOwnerEntries(f.graphics);
        }
    }

    @Test void eglExistingGraphicsCommandCaptureCannotSupplyOrdinaryFifoAuthority() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            f.graphics.executeCapturedCommands(() -> assertThrows(IllegalStateException.class,
                    () -> renderer.draw(basic(), 0, 0, () -> { })), 0, 0, WIDTH, HEIGHT);
            noOwnerEntries(f.graphics);
        }
    }

    @Test void eglStaleAtlasCannotDrawOrRetireSameNumericIdsInReplacementOwner() throws Exception {
        try (var f = Fixture.open()) {
            var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS);
            try {
                f.draw(renderer, basic()); var old = f.graphics.getPatternAtlas();
                f.graphics.resetPatternAndPaletteState(); var replacement = f.graphics.getPatternAtlas();
                assertNotSame(old, replacement); replacement.registerRange(PatternAtlasRange.OBJECTS);
                f.graphics.cachePatternTexture(pattern(3), BASE);
                var foreignEntry = replacement.getEntry(BASE); var foreignSample = SpritePresentation.patternSample(f.graphics, BASE);
                assertThrows(IllegalStateException.class, () -> renderer.draw(basic(), 0, 0, () -> { }));
                assertDoesNotThrow(renderer::close);
                assertSame(foreignEntry, replacement.getEntry(BASE));
                assertEquals(foreignSample, SpritePresentation.patternSample(f.graphics, BASE));
            } finally { renderer.close(); }
        }
    }

    @Test void eglConstructorRejectsCompetingOwnedRangeWithoutChangingForeignEntry() throws Exception {
        try (var f = Fixture.open()) {
            var atlas = f.graphics.getPatternAtlas(); atlas.registerRange(PatternAtlasRange.OBJECTS);
            f.graphics.cachePatternTexture(pattern(2), BASE); var entry = atlas.getEntry(BASE);
            assertThrows(IllegalArgumentException.class, () -> new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS));
            assertSame(entry, atlas.getEntry(BASE)); assertEquals(solid(2), SpritePresentation.patternSample(f.graphics, BASE));
        }
    }

    @Test void eglForeignSpriteLogicalRangeRejectsBeforeAnyCacheOrForeignUpload() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int foreign = PatternAtlasRange.HUD.base() + 7;
            var t = new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, foreign, 0, false, false,
                    false, 20, 20, 8, 8, true, 0xF, false, 1);
            var frame = new SpritePresentation.Frame(List.of(t), List.of(), Map.of(foreign, solid(3)));
            var scene = new RetainedPlaneSceneRenderer.Scene(new RetainedPlaneSceneRenderer.PlaneImage(filled(1)),
                    new RetainedPlaneSceneRenderer.PlaneImage(filled(0)), new int[256], 0, palette(), true,
                    frame, Map.of(0, solid(1), 1, solid(0), 7, solid(3)));
            assertThrows(IllegalArgumentException.class, () -> renderer.draw(scene, 0, 0, () -> { }));
            noOwnerEntries(f.graphics); assertNull(f.graphics.getPatternAtlas().getEntry(foreign));
        }
    }

    @Test void eglConstructorRejectsWrongPhysicalViewportBeforeClaimingRange() throws Exception {
        try (var f = Fixture.open()) {
            glViewport(0, 0, WIDTH - 1, HEIGHT);
            try {
                assertThrows(IllegalStateException.class, () -> new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS));
                assertTrue(f.graphics.getPatternAtlas().registeredRangesForTesting().stream()
                        .noneMatch(r -> r.base() == BASE));
            } finally { glViewport(0, 0, WIDTH, HEIGHT); }
        }
    }

    @Test void eglChangedViewportOrProjectionRejectsBeforeSelectedWrites() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            float[] projection = f.graphics.getProjectionMatrixBuffer().clone();
            try {
                glViewport(0, 0, WIDTH - 1, HEIGHT);
                assertThrows(IllegalStateException.class, () -> renderer.draw(basic(), 0, 0, () -> { }));
                glViewport(0, 0, WIDTH, HEIGHT);
                f.graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 640, 0, HEIGHT, -1, 1).get(new float[16]));
                assertThrows(IllegalStateException.class, () -> renderer.draw(basic(), 0, 0, () -> { }));
                noOwnerEntries(f.graphics);
            } finally { glViewport(0, 0, WIDTH, HEIGHT); f.graphics.setProjectionMatrixBuffer(projection); }
        }
    }

    @Test void eglChangedBackendPropertyAndLogicModeCannotReuseTheBoundGpuOwner() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            String incoming = System.getProperty(BACKEND);
            try {
                System.setProperty(BACKEND, "");
                assertThrows(IllegalStateException.class, () -> renderer.draw(basic(), 0, 0, () -> { }));
                restoreProperty(BACKEND, incoming); f.graphics.setHeadlessMode(true);
                assertThrows(IllegalStateException.class, () -> renderer.draw(basic(), 0, 0, () -> { }));
                noOwnerEntries(f.graphics);
            } finally { restoreProperty(BACKEND, incoming); f.graphics.setHeadlessMode(false); }
        }
    }

    @Test void eglMissingPatternDependencyRejectsRatherThanSamplingStaticOrTransparentFallback() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            var incomplete = scene(filled(1), filled(0), new int[256], 0, palette(), Map.of(1, solid(0)), List.of());
            assertThrows(IllegalArgumentException.class, () -> renderer.draw(incomplete, 0, 0, () -> { }));
            noOwnerEntries(f.graphics);
        }
    }


    @Test void eglThrowingCloseBarrierStillRetiresOwnedPixelsAndUnwindsTailOnce() throws Exception {
        try (var f = Fixture.open()) {
            var atlas = f.graphics.getPatternAtlas(); atlas.registerRange(PatternAtlasRange.HUD);
            int other = PatternAtlasRange.HUD.base() + 19;
            f.graphics.cachePatternTexture(pattern(2), other);
            var unrelated = atlas.getEntry(other); var unrelatedSample = SpritePresentation.patternSample(f.graphics, other);
            var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS);
            try {
                f.draw(renderer, basic());
                var sharedPriority = f.graphics.getTilePriorityFBO(); int priorityTexture = sharedPriority.getTextureId();
                var sentinel = new IllegalStateException("old FIFO fails during retirement");
                var runs = new AtomicInteger(); var unwinds = new AtomicInteger();
                f.graphics.registerCommand((x, y, w, h) -> { throw sentinel; });
                f.graphics.registerCommand(new GLCommandable() {
                    @Override public void execute(int x, int y, int w, int h) { runs.incrementAndGet(); }
                    @Override public void discard() { unwinds.incrementAndGet(); }
                });
                assertSame(sentinel, assertThrows(IllegalStateException.class, renderer::close));
                noOwnerEntries(f.graphics);
                assertSame(unrelated, atlas.getEntry(other));
                assertEquals(unrelatedSample, SpritePresentation.patternSample(f.graphics, other));
                assertSame(sharedPriority, f.graphics.getTilePriorityFBO()); assertTrue(glIsTexture(priorityTexture));
                assertEquals(0, runs.get()); assertEquals(1, unwinds.get());
                renderer.close(); f.graphics.flush();
                assertEquals(0, runs.get()); assertEquals(1, unwinds.get());
            } finally { renderer.close(); }
        }
    }

    @Test void eglFirstDrawRestoresIncomingTextureUnitSevenBindings() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int[] textures = unitSevenTextures();
            try {
                f.draw(renderer, basic()); assertUnitSeven(textures);
            } finally { glActiveTexture(GL_TEXTURE7); glBindTexture(GL_TEXTURE_1D, 0); glBindTexture(GL_TEXTURE_2D, 0);
                glDeleteTextures(textures); glActiveTexture(GL_TEXTURE0); }
        }
    }

    @Test void eglLazySetupFailureRestoresIncomingTextureUnitSevenBindings() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int[] textures = unitSevenTextures(); var checks = new AtomicInteger();
            var sentinel = new IllegalStateException("fail after actual lazy setup and uploads");
            try {
                assertSame(sentinel, assertThrows(IllegalStateException.class, () -> renderer.draw(basic(), 0, 0, () -> {
                    if (checks.incrementAndGet() == 3) { indexed(f.graphics, 0, 1); throw sentinel; }
                })));
                assertEquals(3, checks.get()); assertUnitSeven(textures);
            } finally { glActiveTexture(GL_TEXTURE7); glBindTexture(GL_TEXTURE_1D, 0); glBindTexture(GL_TEXTURE_2D, 0);
                glDeleteTextures(textures); glActiveTexture(GL_TEXTURE0); }
        }
    }

    @Test void eglSelectedSpritePaletteIgnoresAmbientWaterSplitAndPreservesItsBank() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int waterTexture = waterBank(f.graphics); byte[] water = textureBytes(waterTexture);
            f.graphics.setWaterEnabled(true); f.graphics.setWaterlineScreenY(-1);
            f.graphics.setUseUnderwaterPaletteForBackground(false);
            pixel(f.draw(renderer, waterContrast()), 40, 40, YELLOW);
            assertTrue(f.graphics.isWaterEnabled()); assertFalse(f.graphics.isUseUnderwaterPaletteForBackground());
            assertEquals(waterTexture, f.graphics.getUnderwaterPaletteTextureId());
            assertArrayEquals(water, textureBytes(waterTexture));
        }
    }

    @Test void eglSelectedSpritePaletteIgnoresAmbientUnderwaterBackgroundSelection() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int waterTexture = waterBank(f.graphics); byte[] water = textureBytes(waterTexture);
            f.graphics.setWaterEnabled(false); f.graphics.setUseUnderwaterPaletteForBackground(true);
            pixel(f.draw(renderer, waterContrast()), 40, 40, YELLOW);
            assertFalse(f.graphics.isWaterEnabled()); assertTrue(f.graphics.isUseUnderwaterPaletteForBackground());
            assertEquals(waterTexture, f.graphics.getUnderwaterPaletteTextureId());
            assertArrayEquals(water, textureBytes(waterTexture));
        }
    }

    @Test void eglSelectedWaterFlagsRestoreAfterPostUploadFailureWithoutRewritingWaterBank() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int waterTexture = waterBank(f.graphics); byte[] water = textureBytes(waterTexture);
            f.graphics.setWaterEnabled(true); f.graphics.setUseUnderwaterPaletteForBackground(true);
            var checks = new AtomicInteger(); var sentinel = new IllegalStateException("selected post-upload failure");
            assertSame(sentinel, assertThrows(IllegalStateException.class, () -> renderer.draw(waterContrast(), 0, 0, () -> {
                if (checks.incrementAndGet() == 3) {
                    assertFalse(f.graphics.isWaterEnabled()); assertFalse(f.graphics.isUseUnderwaterPaletteForBackground());
                    throw sentinel;
                }
            })));
            assertEquals(3, checks.get()); assertTrue(f.graphics.isWaterEnabled());
            assertTrue(f.graphics.isUseUnderwaterPaletteForBackground());
            assertEquals(waterTexture, f.graphics.getUnderwaterPaletteTextureId());
            assertArrayEquals(water, textureBytes(waterTexture));
        }
    }

    @Test void eglCachedViewportMismatchRejectsBeforeClaimingAnOtherwiseValidActualViewport() throws Exception {
        try (var f = Fixture.open()) {
            f.graphics.setViewport(3, 4, 640, 448);
            try {
                assertThrows(IllegalStateException.class, () -> new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS));
                assertTrue(f.graphics.getPatternAtlas().registeredRangesForTesting().stream().noneMatch(r -> r.base() == BASE));
                noOwnerEntries(f.graphics);
            } finally { f.graphics.setViewport(0, 0, WIDTH, HEIGHT); }
        }
    }

    @Test void eglCachedViewportChangeRejectsBeforeReplacingResidentPixelsOrMaskSampling() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            int[] fg = filled(1); fg[cell(8, 2)] = 2 | 0x8000;
            var selected = scene(fg, filled(0), new int[256], 0, palette(),
                    Map.of(0, solid(1), 1, solid(0), 2, solid(2), 3, solid(3)), List.of(tile(3, 64, 16, false)));
            byte[] before = bytes(f.draw(renderer, selected));
            var entry = f.graphics.getPatternAtlas().getEntry(BASE); var sample = SpritePresentation.patternSample(f.graphics, BASE);
            f.graphics.setViewport(0, 0, 640, 448);
            try {
                assertThrows(IllegalStateException.class, () -> renderer.draw(waterContrast(), 0, 0, () -> { }));
                assertSame(entry, f.graphics.getPatternAtlas().getEntry(BASE));
                assertEquals(sample, SpritePresentation.patternSample(f.graphics, BASE)); indexed(f.graphics, 0, 1);
            } finally { f.graphics.setViewport(0, 0, WIDTH, HEIGHT); }
            assertArrayEquals(before, bytes(f.draw(renderer, selected)));
        }
    }

    @Test void eglExternalAtlasBatchRejectsConstructorBeforeRangeClaim() throws Exception {
        try (var f = Fixture.open()) {
            var atlas = f.graphics.getPatternAtlas(); atlas.beginBatch();
            try {
                assertThrows(IllegalStateException.class, () -> new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS));
                assertTrue(atlas.registeredRangesForTesting().stream().noneMatch(r -> r.base() == BASE)); noOwnerEntries(f.graphics);
            } finally { atlas.endBatch(); }
        }
    }

    @Test void eglExternalAtlasBatchRejectsNewSceneBeforeCpuAndGpuVersionsCanDiverge() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            f.draw(renderer, basic()); indexed(f.graphics, 0, 1);
            var atlas = f.graphics.getPatternAtlas(); var entry = atlas.getEntry(BASE);
            var changed = scene(filled(1), filled(0), new int[256], 0, palette(), Map.of(0, solid(2), 1, solid(0)), List.of());
            atlas.beginBatch();
            try {
                assertThrows(IllegalStateException.class, () -> renderer.draw(changed, 0, 0, () -> { }));
                assertSame(entry, atlas.getEntry(BASE)); assertEquals(solid(1), SpritePresentation.patternSample(f.graphics, BASE));
                indexed(f.graphics, 0, 1);
            } finally { atlas.endBatch(); }
            pixel(f.draw(renderer, basic()), 100, 100, RED); indexed(f.graphics, 0, 1);
        }
    }

    @Test void eglOldFifoOpeningAnAtlasBatchRejectsBeforeSelectedUploads() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            var atlas = f.graphics.getPatternAtlas(); var calls = new AtomicInteger();
            f.graphics.registerCommand((x, y, w, h) -> { calls.incrementAndGet(); atlas.beginBatch(); });
            try {
                assertThrows(IllegalStateException.class, () -> renderer.draw(basic(), 0, 0, () -> { }));
                assertEquals(1, calls.get()); noOwnerEntries(f.graphics);
            } finally { atlas.endBatch(); }
            pixel(f.draw(renderer, basic()), 10, 10, RED); assertEquals(1, calls.get());
        }
    }

    @Test void eglExternalAtlasBatchRejectsCloseWithoutDroppingItsRetirementLedger() throws Exception {
        try (var f = Fixture.open()) {
            var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS);
            try {
                f.draw(renderer, basic()); var atlas = f.graphics.getPatternAtlas(); var entry = atlas.getEntry(BASE);
                atlas.beginBatch();
                try {
                    assertThrows(IllegalStateException.class, renderer::close);
                    assertSame(entry, atlas.getEntry(BASE)); assertEquals(solid(1), SpritePresentation.patternSample(f.graphics, BASE));
                } finally { atlas.endBatch(); }
                renderer.close(); noOwnerEntries(f.graphics);
            } finally { renderer.close(); }
        }
    }

    @Test void eglSelectedSatIgnoresAmbientVerticalWrapAndRestoresItsSettings() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            f.graphics.enableVerticalWrapAdjust(2048, 2048);
            var before = verticalWrapState(f.graphics);
            pixel(f.draw(renderer, waterContrast()), 40, 40, YELLOW);
            assertEquals(before, verticalWrapState(f.graphics));
        }
    }

    @Test void eglDisabledVerticalWrapRetainsItsUnsampledRangeAndCamera() throws Exception {
        try (var f = Fixture.open(); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            f.graphics.enableVerticalWrapAdjust(4096, -173); f.graphics.disableVerticalWrapAdjust();
            var before = verticalWrapState(f.graphics);
            pixel(f.draw(renderer, waterContrast()), 40, 40, YELLOW);
            assertEquals(before, verticalWrapState(f.graphics));
        }
    }

    @Test void eglVerticalWrapStateRestoresWhenNativeReplayThrowsAfterChangingItsSettings() throws Exception {
        var sentinel = new IllegalStateException("actual sprite command producer fails");
        var observedEnabled = new java.util.concurrent.atomic.AtomicBoolean();
        var calls = new AtomicInteger();
        try (var f = Fixture.open(() -> new GraphicsManager() {
            @Override public void renderPatternWithId(int id, com.openggf.level.PatternDesc desc, int x, int y) {
                calls.incrementAndGet(); observedEnabled.set((Boolean) verticalWrapState(this).getFirst());
                enableVerticalWrapAdjust(1024, -999); throw sentinel;
            }
        }); var renderer = new RetainedPlaneSceneRenderer(f.graphics, PatternAtlasRange.OBJECTS)) {
            f.graphics.enableVerticalWrapAdjust(2048, 2048); var before = verticalWrapState(f.graphics);
            assertSame(sentinel, assertThrows(IllegalStateException.class,
                    () -> renderer.draw(waterContrast(), 0, 0, () -> { })));
            assertEquals(1, calls.get()); assertFalse(observedEnabled.get());
            assertEquals(before, verticalWrapState(f.graphics));
        }
    }

    private static List<Object> verticalWrapState(GraphicsManager graphics) {
        try {
            var values = new ArrayList<Object>();
            for (String name : List.of("verticalWrapAdjustEnabled", "verticalWrapRange", "verticalWrapCameraY")) {
                var field = GraphicsManager.class.getDeclaredField(name); field.setAccessible(true);
                values.add(field.get(graphics));
            }
            return List.copyOf(values);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static int[] unitSevenTextures() {
        int one = glGenTextures(), two = glGenTextures(); glActiveTexture(GL_TEXTURE7);
        glBindTexture(GL_TEXTURE_1D, one); glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA8, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D, two); glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return new int[]{one, two};
    }
    private static void assertUnitSeven(int[] textures) {
        assertEquals(GL_TEXTURE7, glGetInteger(GL_ACTIVE_TEXTURE));
        assertEquals(textures[0], glGetInteger(GL_TEXTURE_BINDING_1D));
        assertEquals(textures[1], glGetInteger(GL_TEXTURE_BINDING_2D));
    }
    private static int waterBank(GraphicsManager graphics) {
        var rows = new com.openggf.level.Palette[4];
        for (int i = 0; i < rows.length; i++) { rows[i] = new com.openggf.level.Palette(); rows[i].colors[1].fromSegaFormat(0xE00); }
        Integer texture = graphics.cacheUnderwaterPaletteTexture(rows, rows[0]);
        assertNotNull(texture); assertTrue(glIsTexture(texture));
        return texture;
    }
    private static RetainedPlaneSceneRenderer.Scene waterContrast() {
        var sprite = new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, BASE + 2, 1,
                false, false, true, 40, 40, 8, 8, true, 0, false, 1);
        return scene(filled(1), filled(0), new int[256], 0, palette(),
                Map.of(0, solid(1), 1, solid(0), 2, solid(1)), List.of(sprite));
    }
    private static byte[] textureBytes(int texture) {
        int active = glGetInteger(GL_ACTIVE_TEXTURE); glActiveTexture(GL_TEXTURE0);
        int binding = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glBindTexture(GL_TEXTURE_2D, texture);
            int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
            int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
            var pixels = BufferUtils.createByteBuffer(width * height * 4);
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            return bytes(pixels);
        } finally { glBindTexture(GL_TEXTURE_2D, binding); glActiveTexture(active); }
    }

    private static RetainedPlaneSceneRenderer.Scene basic() {
        return scene(filled(1), filled(0), new int[256], 0, palette(), Map.of(0, solid(1), 1, solid(0)), List.of());
    }

    private static RetainedPlaneSceneRenderer.Scene scene(int[] fg, int[] bg, int[] h, int v, byte[] palette,
            Map<Integer, SpritePresentation.PatternVersion> patterns, List<SpritePresentation.Tile> tiles) {
        var versions = new HashMap<Integer, SpritePresentation.PatternVersion>();
        for (var t : tiles) versions.put(t.patternId(), patterns.get(t.patternId() - BASE));
        var sprites = new SpritePresentation.Frame(tiles, List.of(), versions);
        return new RetainedPlaneSceneRenderer.Scene(new RetainedPlaneSceneRenderer.PlaneImage(fg),
                new RetainedPlaneSceneRenderer.PlaneImage(bg), h, v, palette, true, sprites, patterns);
    }

    private static SpritePresentation.Tile tile(int nativeId, int x, int y, boolean high) {
        return new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, BASE + nativeId, 0, false, false,
                high, x, y, 8, 8, true, 0xF, false, 1);
    }

    private static SpritePresentation.PatternVersion solid(int index) {
        long word = index * 0x1111111111111111L;
        return new SpritePresentation.PatternVersion(word, word, word, word);
    }

    private static SpritePresentation.PatternVersion corners() {
        // Four literal nontransparent corners: row0=(green,...,blue), row7=(white,...,red).
        return new SpritePresentation.PatternVersion(0x0000000030000002L, 0, 0, 0x1000000400000000L);
    }

    private static SpritePresentation.PatternVersion leftHalfWhite() {
        long twoRows = 0x0000444400004444L;
        return new SpritePresentation.PatternVersion(twoRows, twoRows, twoRows, twoRows);
    }

    private static int[] filled(int word) { int[] values = new int[CELLS]; Arrays.fill(values, word); return values; }
    private static int cell(int x, int y) { return y * 64 + x; }

    private static byte[] palette() {
        byte[] bytes = new byte[128];
        word(bytes, 1, 0x00E); word(bytes, 2, 0x0E0); word(bytes, 3, 0xE00); word(bytes, 4, 0xEEE);
        word(bytes, 17, 0x0EE); word(bytes, 33, 0xEE0); word(bytes, 49, 0xE0E);
        return bytes;
    }
    private static void word(byte[] bytes, int index, int value) { bytes[index * 2] = (byte) (value >>> 8); bytes[index * 2 + 1] = (byte) value; }
    private static Pattern pattern(int index) {
        var pattern = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) index);
        return pattern;
    }

    private static void noOwnerEntries(GraphicsManager graphics) {
        assertNull(graphics.getPatternAtlas().getEntry(BASE)); assertNull(graphics.getPatternAtlas().getEntry(BASE + 1));
        assertNull(SpritePresentation.patternSample(graphics, BASE)); assertNull(SpritePresentation.patternSample(graphics, BASE + 1));
    }

    private static byte[] bytes(ByteBuffer buffer) { byte[] b = new byte[buffer.capacity()]; buffer.duplicate().clear().get(b); return b; }
    private static void pixel(ByteBuffer pixels, int x, int y, int rgb) {
        int at = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
        assertEquals((rgb >>> 16) & 255, pixels.get(at) & 255, "R at " + x + "," + y);
        assertEquals((rgb >>> 8) & 255, pixels.get(at + 1) & 255, "G at " + x + "," + y);
        assertEquals(rgb & 255, pixels.get(at + 2) & 255, "B at " + x + "," + y);
        assertEquals(255, pixels.get(at + 3) & 255, "Opaque selected target at " + x + "," + y);
    }

    private static void indexed(GraphicsManager graphics, int nativeId, int expectedIndex) {
        var atlas = graphics.getPatternAtlas(); var entry = atlas.getEntry(BASE + nativeId);
        assertNotNull(entry); assertEquals(0, entry.atlasIndex());
        assertEquals(solid(expectedIndex), SpritePresentation.patternSample(graphics, BASE + nativeId));
        int texture = atlas.getTextureId(entry.atlasIndex()); assertTrue(glIsTexture(texture));
        int oldRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), oldPack = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int fbo = glGenFramebuffers();
        try {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0); glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_READ_FRAMEBUFFER));
            glReadBuffer(GL_COLOR_ATTACHMENT0); var pixels = BufferUtils.createByteBuffer(64);
            glReadPixels(entry.tileX() * 8, entry.tileY() * 8, 8, 8, GL_RED, GL_UNSIGNED_BYTE, pixels);
            for (int i = 0; i < 64; i++) assertEquals(expectedIndex, pixels.get(i) & 255, "Actual indexed GPU texel " + i);
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, oldRead); glBindBuffer(GL_PIXEL_PACK_BUFFER, oldPack); glDeleteFramebuffers(fbo);
        }
    }

    private static void palettePixels(GraphicsManager graphics) {
        Integer texture = graphics.getCombinedPaletteTextureId(); assertNotNull(texture); assertTrue(glIsTexture(texture));
        int active = glGetInteger(GL_ACTIVE_TEXTURE); glActiveTexture(GL_TEXTURE0); int binding = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glBindTexture(GL_TEXTURE_2D, texture); assertEquals(16, glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH));
            int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT); assertTrue(height >= 4);
            var rgba = BufferUtils.createByteBuffer(16 * height * 4); glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            int[] expected = new int[64]; expected[1] = RED; expected[2] = GREEN; expected[3] = BLUE; expected[4] = WHITE;
            expected[17] = YELLOW; expected[33] = CYAN; expected[49] = MAGENTA;
            for (int colour = 0; colour < 64; colour++) {
                int at = colour * 4, rgb = ((rgba.get(at) & 255) << 16) | ((rgba.get(at + 1) & 255) << 8) | (rgba.get(at + 2) & 255);
                assertEquals(expected[colour], rgb, "Actual selected GPU palette colour " + colour);
                assertEquals(colour % 16 == 0 ? 0 : 255, rgba.get(at + 3) & 255);
            }
        } finally { glBindTexture(GL_TEXTURE_2D, binding); glActiveTexture(active); }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) System.clearProperty(name); else System.setProperty(name, value);
    }

    private static final class Fixture implements AutoCloseable {
        private final PresentationOffscreenTestContext context;
        private final GraphicsManager graphics;
        private Fixture(PresentationOffscreenTestContext context, GraphicsManager graphics) { this.context = context; this.graphics = graphics; }
        static Fixture open() throws Exception { return open(GraphicsManager::new); }
        static Fixture open(java.util.function.Supplier<GraphicsManager> factory) throws Exception {
            assumeTrue("surfaceless-egl".equals(System.getProperty(BACKEND)), "Explicit retained-plane EGL backend");
            assertEquals("surfaceless-egl", System.getProperty(BACKEND));
            assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
            TestEnvironment.resetAll(); RenderContext.reset();
            var c = GameServices.configuration(); c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
            c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, WIDTH); c.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, HEIGHT);
            c.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false); c.resolveDisplayAspect();
            GameServices.audio().setBackend(new NullAudioBackend());
            assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
            var context = PresentationOffscreenTestContext.openConfigured(WIDTH, HEIGHT, PresentationOffscreenTestContext.LegacyProfile.CORE_41);
            var graphics = factory.get();
            try {
                graphics.setHeadlessMode(false); graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, WIDTH, HEIGHT); graphics.setProjectionWidth(WIDTH);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, WIDTH, 0, HEIGHT, -1, 1).get(new float[16]));
                graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB); graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
                glViewport(0, 0, WIDTH, HEIGHT); glDisable(GL_DITHER); glDisable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST);
                return new Fixture(context, graphics);
            } catch (Exception | Error failure) {
                try { graphics.cleanup(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
                try { GraphicsManager.destroyForReinit(); TestEnvironment.resetAll(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
                try { context.close(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        ByteBuffer draw(RetainedPlaneSceneRenderer renderer, RetainedPlaneSceneRenderer.Scene scene) {
            glViewport(0, 0, WIDTH, HEIGHT); glDisable(GL_SCISSOR_TEST);
            glClearColor(1, 0, 1, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.draw(scene, 0, 0, () -> { });
            graphics.flush(); graphics.flushScreenSpace(); glFinish();
            var pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
            glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            assertEquals(GL_NO_ERROR, glGetError()); return pixels;
        }
        @Override public void close() {
            try { graphics.cleanup(); }
            finally {
                try { GraphicsManager.destroyForReinit(); RenderContext.reset(); TestEnvironment.resetAll(); }
                finally { context.close(); }
            }
        }
    }
}
