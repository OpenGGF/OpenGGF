package com.openggf.level.render;

import com.openggf.data.Rom;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.session.WorldSession;
import com.openggf.level.scroll.ZoneScrollHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestSceneBackgroundPreparation {
    // The storage is larger than the native repeat in both axes. Sentinel pixels
    // outside that repeat expose sampling the image dimensions instead of its period.
    private static final int STORAGE_WIDTH = 11;
    private static final int STORAGE_HEIGHT = 7;
    private static final int PERIOD_WIDTH = 7;
    private static final int PERIOD_HEIGHT = 5;

    @Test
    void nativeScanlineOffsetsAndPopulatedPeriodsFillNarrowAndWideViewports() {
        RecordingScroll scroll = new RecordingScroll();
        DetachedBackground background = background(scroll, 0, 0);
        int cameraX = -17, cameraY = 9;
        background.update(cameraX, cameraY, 42);

        for (int width : new int[] {320, 528}) {
            ZonePictureSource.Picture sampled = SceneBackgroundPreparation.sample(background, width, 224);
            assertEquals(width, sampled.width());
            assertEquals(224, sampled.height());
            assertEquals(width * 224, sampled.argb().length);
            for (int y = 0; y < 224; y++) {
                int sourceY = Math.floorMod(y + cameraY - 5 + lineYOffset(y), PERIOD_HEIGHT);
                for (int x = 0; x < width; x++) {
                    int sourceX = Math.floorMod(x + cameraX + lineXOffset(y), PERIOD_WIDTH);
                    assertEquals(colour(sourceX, sourceY, 0), sampled.argb()[y * width + x],
                            "native pixel at viewport " + width + ", " + x + "," + y);
                }
            }
        }
        assertEquals(1, scroll.updates, "CPU sampling must not advance the native handler");
    }

    @Test
    void fixedViewHorizontalExtensionKeepsTheNativeCentreAtWiderWidths() {
        int nativeWidth = 320;
        int[] pixels = new int[nativeWidth * 224];
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < nativeWidth; x++) pixels[y * nativeWidth + x] = colour(x, y, 0);
        }
        ZoneScrollHandler scroll = mock(ZoneScrollHandler.class);
        DetachedBackground background = new DetachedBackground(
                new ZonePictureSource.Picture(nativeWidth, 224, pixels), scroll, 0, nativeWidth, 224)
                .withHorizontalExtension((x, width) -> {
                    int nativeX = x - (width - nativeWidth) / 2;
                    return nativeX < 0 ? -nativeX - 1
                            : nativeX >= nativeWidth ? 2 * nativeWidth - nativeX - 1 : nativeX;
                });
        background.update(0, 0, 1);

        for (int width : new int[] {320, 528}) {
            var sampled = SceneBackgroundPreparation.sample(background, width, 224);
            int inset = (width - nativeWidth) / 2;
            for (int y : new int[] {0, 111, 223}) {
                for (int x = 0; x < width; x++) {
                    int nativeX = x - inset;
                    int expectedX = nativeX < 0 ? -nativeX - 1
                            : nativeX >= nativeWidth ? 2 * nativeWidth - nativeX - 1 : nativeX;
                    assertEquals(colour(expectedX, y, 0), sampled.argb()[y * width + x]);
                }
            }
        }
    }

    @Test
    void activeAdvancementUsesTheWorldRomAndKeepsOnePrivateScrollOwner() {
        Fixture fixture = fixture(0);
        SceneBackgroundPreparation preparation = new SceneBackgroundPreparation();
        try (var services = mockStatic(GameServices.class, invocation -> {
            throw new AssertionError("Private scenery accessed GameServices." + invocation.getMethod().getName());
        })) {
            var first = preparation.advance(fixture.world(), 6, 100, 37, 50, 320, 224);
            var duplicate = preparation.advance(fixture.world(), 6, 100, 37, 50, 320, 224);
            var wider = preparation.advance(fixture.world(), 6, 100, 37, 50, 528, 224);
            assertArrayEquals(first.argb(), duplicate.argb());
            assertEquals(first.argb()[319], wider.argb()[319]);
            assertEquals(1, fixture.scrolls().size());
            RecordingScroll scroll = fixture.scrolls().getFirst();
            assertEquals(1, scroll.inits);
            assertEquals(1, scroll.updates, "duplicate presentation and width changes do not tick native scroll");
            assertEquals(0, scroll.initialAct);
            assertEquals(100, scroll.initialX);
            assertEquals(37, scroll.initialY);

            preparation.advance(fixture.world(), 6, 103, 37, 50, 320, 224);
            assertEquals(2, scroll.updates, "changed camera is sampled even at the same presentation tick");
            preparation.advance(fixture.world(), 6, 103, 37, 51, 320, 224);
            assertEquals(3, scroll.updates);
        }
        verify(fixture.factory()).create(fixture.rom());
        verify(fixture.source()).background(6, 0);
        verify(fixture.data()).rom();
        verify(fixture.world(), never()).getCurrentLevel();
        verify(fixture.world(), never()).setCurrentLevel(any());
        verifyNoInteractions(fixture.rom());
    }

    @Test
    void sourceIndicesUseMainRomZonesThenHiddenPalaceSanctuary() {
        for (int index = 0; index < 13; index++) {
            assertEquals(index, SceneBackgroundPreparation.sourceZone(index));
            assertEquals(0, SceneBackgroundPreparation.sourceAct(index));
        }
        assertEquals(0x16, SceneBackgroundPreparation.sourceZone(13));
        assertEquals(1, SceneBackgroundPreparation.sourceAct(13));
        for (int invalid : new int[] {-1, 14, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> SceneBackgroundPreparation.sourceZone(invalid));
            assertThrows(IllegalArgumentException.class, () -> SceneBackgroundPreparation.sourceAct(invalid));
        }

        Fixture fixture = fixture(0);
        new SceneBackgroundPreparation().advance(fixture.world(), 13, 0, 0, 1, 320, 224);
        verify(fixture.source()).background(0x16, 1);
        assertEquals(1, fixture.scrolls().getFirst().initialAct);
    }

    @Test
    void sourceChangeAndExpiryResetStartFreshScrollState() {
        Fixture fixture = fixture(0);
        SceneBackgroundPreparation preparation = new SceneBackgroundPreparation();
        preparation.advance(fixture.world(), 0, 12, 34, 20, 320, 224);
        preparation.advance(fixture.world(), 1, 12, 34, 21, 320, 224);
        preparation.reset();
        preparation.advance(fixture.world(), 1, 12, 34, 21, 320, 224);

        assertEquals(3, fixture.scrolls().size());
        for (RecordingScroll scroll : fixture.scrolls()) {
            assertEquals(1, scroll.inits);
            assertEquals(1, scroll.updates);
        }
        verify(fixture.factory(), times(3)).create(fixture.rom());
        verify(fixture.source()).background(0, 0);
        verify(fixture.source(), times(2)).background(1, 0);
    }

    @Test
    void capturedRewindRestoresEveryNativeOffsetAndFrozenPictureThenReplaysExactly() {
        for (int width : new int[] {320, 528}) {
            Fixture fixture = fixture(0, true);
            SceneBackgroundPreparation preparation = new SceneBackgroundPreparation();
            preparation.advance(fixture.world(), 6, 120, 39, 20, width, 224);
            SceneBackgroundPreparation.State saved = preparation.capture();
            int[] frozenPixels = preparation.picture().argb();
            RecordingScroll original = fixture.scrolls().getFirst();
            int savedAccumulator = original.accumulator;
            List<SceneBackgroundPreparation.State> forward = new ArrayList<>();
            List<int[]> forwardPictures = new ArrayList<>();
            // ACTIVE logical presentations advance without any GPU draw.
            for (int tick = 21; tick <= 25; tick++) {
                preparation.advance(fixture.world(), 6, 100 + tick, 30 + tick, tick, width, 224);
                forward.add(preparation.capture());
                forwardPictures.add(preparation.picture().argb());
            }
            assertEquals(6, original.updates);
            assertNotEquals(savedAccumulator, original.accumulator);
            assertFalse(java.util.Arrays.equals(frozenPixels, forwardPictures.getLast()),
                    "the stateful native timeline actually moved");

            preparation.restore(fixture.world(), saved);
            RecordingScroll restored = fixture.scrolls().getLast();
            assertSame(original, restored, "matching source reuses its private native owner");
            assertEquals(1, restored.restores);
            assertEquals(1, restored.inits, "matching owner is not cold initialized again");
            assertEquals(6, restored.updates, "restoration is not a presentation update");
            assertEquals(savedAccumulator, restored.accumulator);
            assertNativeStateEquals(saved, preparation.capture());
            assertArrayEquals(frozenPixels, preparation.picture().argb(), "held rewind shows the captured picture");

            preparation.advance(fixture.world(), 6, 120, 39, 20, width, 224);
            assertEquals(6, restored.updates, "restored last-tick admission remains idempotent");
            assertNativeStateEquals(saved, preparation.capture());
            for (int tick = 21; tick <= 25; tick++) {
                preparation.advance(fixture.world(), 6, 100 + tick, 30 + tick, tick, width, 224);
                assertNativeStateEquals(forward.get(tick - 21), preparation.capture());
                assertArrayEquals(forwardPictures.get(tick - 21), preparation.picture().argb());
            }
            assertEquals(11, restored.updates);
            assertEquals(1, restored.restores);
            verify(fixture.factory()).create(fixture.rom());
        }
    }

    @Test
    void freshOwnerInitializesBeforeOpaqueRestoreAndReconstructsTheCapturedPicture() {
        Fixture fixture = fixture(0, true);
        SceneBackgroundPreparation original = new SceneBackgroundPreparation();
        original.advance(fixture.world(), 13, 36, 41, 9, 528, 224);
        original.advance(fixture.world(), 13, 38, 43, 10, 528, 224);
        var saved = original.capture();
        int[] frozen = original.picture().argb();

        SceneBackgroundPreparation fresh = new SceneBackgroundPreparation();
        fresh.restore(fixture.world(), saved);
        RecordingScroll restored = fixture.scrolls().getLast();
        assertNotSame(fixture.scrolls().getFirst(), restored);
        assertEquals(1, restored.inits, "ROM scroll tables/state are initialized before opaque restore");
        assertTrue(restored.restoreSawInitialized);
        assertEquals(1, restored.initialAct);
        assertEquals(38, restored.initialX);
        assertEquals(43, restored.initialY);
        assertEquals(1, restored.restores);
        assertEquals(0, restored.updates);
        assertNativeStateEquals(saved, fresh.capture());
        assertArrayEquals(frozen, fresh.picture().argb());
        fresh.advance(fixture.world(), 13, 38, 43, 10, 528, 224);
        assertEquals(0, restored.updates, "restored presentation is already admitted");
        original.advance(fixture.world(), 13, 43, 48, 11, 528, 224);
        fresh.advance(fixture.world(), 13, 43, 48, 11, 528, 224);
        assertNativeStateEquals(original.capture(), fresh.capture());
        assertArrayEquals(original.picture().argb(), fresh.picture().argb());
        verify(fixture.factory(), times(2)).create(fixture.rom());
    }

    @Test
    void pictureAndCaptureArraysCannotMutatePublishedOrRestoredState() {
        Fixture fixture = fixture(0, true);
        SceneBackgroundPreparation preparation = new SceneBackgroundPreparation();
        var returned = preparation.advance(fixture.world(), 0, 33, 14, 8, 528, 224);
        SceneBackgroundPreparation.State saved = preparation.capture();
        int[] expectedPixels = preparation.picture().argb();
        int[] expectedWords = saved.background().words(), expectedYs = saved.background().sourceYs();

        java.util.Arrays.fill(returned.argb(), 0);
        java.util.Arrays.fill(preparation.picture().argb(), 0);
        java.util.Arrays.fill(saved.background().words(), 0);
        java.util.Arrays.fill(saved.background().sourceYs(), 0);
        assertArrayEquals(expectedPixels, preparation.picture().argb());
        assertArrayEquals(expectedWords, saved.background().words());
        assertArrayEquals(expectedYs, saved.background().sourceYs());

        // Constructor inputs are also detached, not just arrays returned by accessors.
        int[] suppliedWords = expectedWords.clone(), suppliedYs = expectedYs.clone();
        var detached = saved.background();
        var defensive = new DetachedBackground.State(detached.scroll(), suppliedWords, suppliedYs,
                detached.initialized(), detached.lastX(), detached.lastY(), detached.lastTick());
        var copied = new SceneBackgroundPreparation.State(saved.index(), saved.width(), saved.height(), defensive);
        java.util.Arrays.fill(suppliedWords, 0);
        java.util.Arrays.fill(suppliedYs, 0);
        assertNativeStateEquals(saved, copied);
        preparation.advance(fixture.world(), 0, 71, 27, 9, 528, 224);
        preparation.restore(fixture.world(), copied);
        assertNativeStateEquals(saved, preparation.capture());
        assertArrayEquals(expectedPixels, preparation.picture().argb());
    }

    @Test
    void readsAndSamplingHoldPublishedOffsetsWithoutCallingTheNativeHandler() {
        RecordingScroll scroll = new RecordingScroll(true);
        DetachedBackground background = background(scroll, 0, 0);
        background.update(15, 27, 17);
        int[] expectedX = new int[224], expectedY = new int[224];
        for (int y = 0; y < 224; y++) {
            expectedX[y] = background.sourceX(y);
            expectedY[y] = background.sourceY(y);
        }
        int[] expectedPixels = SceneBackgroundPreparation.sample(background, 528, 224).argb();
        int updates = scroll.updates, verticalReads = scroll.verticalReads, perLineReads = scroll.perLineReads;
        // Live handler fields changing after publication must not leak into a held draw.
        scroll.verticalScroll = 900;
        java.util.Arrays.fill(scroll.verticalOffsets, (short) -700);
        for (int read = 0; read < 4; read++) {
            for (int y = 0; y < 224; y++) {
                assertEquals(expectedX[y], background.sourceX(y));
                assertEquals(expectedY[y], background.sourceY(y));
            }
            assertArrayEquals(expectedPixels, SceneBackgroundPreparation.sample(background, 528, 224).argb());
        }
        assertEquals(updates, scroll.updates);
        assertEquals(verticalReads, scroll.verticalReads);
        assertEquals(perLineReads, scroll.perLineReads);

        Fixture fixture = fixture(0, true);
        SceneBackgroundPreparation preparation = new SceneBackgroundPreparation();
        preparation.advance(fixture.world(), 0, 15, 27, 17, 320, 224);
        int[] heldPixels = preparation.picture().argb();
        RecordingScroll owner = fixture.scrolls().getFirst();
        for (int read = 0; read < 4; read++) {
            assertArrayEquals(heldPixels, preparation.picture().argb());
            var captured = preparation.capture();
            assertEquals(320, captured.width());
            assertEquals(224, captured.height());
        }
        assertEquals(1, owner.updates, "picture/capture reads never consume ACTIVE presentations");
        assertEquals(1, owner.verticalReads);
        assertEquals(1, owner.perLineReads);
        assertEquals(4, owner.captures);
    }

    @Test
    void emptyCapturedStateClearsExpiredSceneryWithoutOpeningAProvider() {
        SceneBackgroundPreparation preparation = new SceneBackgroundPreparation();
        SceneBackgroundPreparation.State empty = preparation.capture();
        assertNull(empty.background());
        assertEquals(0, empty.width());
        assertEquals(0, empty.height());
        assertNull(preparation.picture());
        Fixture fixture = fixture(0);
        preparation.advance(fixture.world(), 0, 0, 0, 1, 320, 224);
        preparation.restore(fixture.world(), empty);
        assertNull(preparation.picture());
        assertNull(preparation.capture().background());
        verify(fixture.factory()).create(fixture.rom());
        preparation.advance(fixture.world(), 0, 0, 0, 2, 320, 224);
        assertEquals(2, fixture.scrolls().size());
        preparation.reset();
        assertNull(preparation.picture());
        assertNull(preparation.capture().background());
    }

    private static void assertNativeStateEquals(SceneBackgroundPreparation.State expected,
                                               SceneBackgroundPreparation.State actual) {
        assertEquals(expected.index(), actual.index());
        assertEquals(expected.background().scroll(), actual.background().scroll(), "native logical accumulator");
        assertArrayEquals(expected.background().words(), actual.background().words(), "all 224 published X offsets");
        assertArrayEquals(expected.background().sourceYs(), actual.background().sourceYs(), "all 224 published Y offsets");
        assertEquals(expected.background().initialized(), actual.background().initialized());
        assertEquals(expected.background().lastX(), actual.background().lastX());
        assertEquals(expected.background().lastY(), actual.background().lastY());
        assertEquals(expected.background().lastTick(), actual.background().lastTick());
        assertEquals(expected.width(), actual.width());
        assertEquals(expected.height(), actual.height());
        assertEquals(expected, actual, "rewind records compare native arrays by value");
        assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void changingWorldUsesItsOwnProviderRomAndPalettePixels() {
        Fixture first = fixture(0), second = fixture(0x400000);
        SceneBackgroundPreparation preparation = new SceneBackgroundPreparation();
        var before = preparation.advance(first.world(), 0, 0, 0, 5, 320, 224);
        var after = preparation.advance(second.world(), 0, 0, 0, 5, 320, 224);
        assertNotEquals(before.argb()[0], after.argb()[0], "private RGB pixels belong to the selected world source");
        verify(first.factory()).create(first.rom());
        verify(second.factory()).create(second.rom());
        assertEquals(1, first.scrolls().size());
        assertEquals(1, second.scrolls().size());
    }

    @Test
    void missingWorldProviderOrRomFailsWithoutInventingScenery() {
        SceneBackgroundPreparation preparation = new SceneBackgroundPreparation();
        assertThrows(IllegalStateException.class, () -> preparation.advance(null, 0, 0, 0, 1, 320, 224));

        Fixture noProvider = fixture(0);
        when(noProvider.module().getGameService(ZonePictureSource.Factory.class)).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> preparation.advance(noProvider.world(), 0, 0, 0, 1, 320, 224));
        verifyNoInteractions(noProvider.factory(), noProvider.source());

        Fixture noRom = fixture(0);
        when(noRom.data().rom()).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> preparation.advance(noRom.world(), 0, 0, 0, 1, 320, 224));
        verifyNoInteractions(noRom.factory(), noRom.source());
    }

    @Test
    void unavailableBackgroundCanRecoverThroughTheSameProviderWithoutAStalePicture() {
        Fixture fixture = fixture(0);
        SceneBackgroundPreparation preparation = new SceneBackgroundPreparation();
        DetachedBackground prepared = background(new RecordingScroll(), 0, 0);
        when(fixture.source().background(3, 0)).thenReturn(null, prepared);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> preparation.advance(fixture.world(), 3, 0, 0, 1, 320, 224));
        assertTrue(failure.getMessage().contains("not prepared"));
        var recovered = preparation.advance(fixture.world(), 3, 0, 0, 1, 320, 224);
        assertEquals(320 * 224, recovered.argb().length);
        verify(fixture.factory(), times(2)).create(fixture.rom());
        verify(fixture.source(), times(2)).background(3, 0);
    }

    @Test
    void invalidViewportIsRejectedBeforeReadingTheWorldSource() {
        Fixture fixture = fixture(0);
        SceneBackgroundPreparation preparation = new SceneBackgroundPreparation();
        for (int[] dimensions : new int[][] {{0, 224}, {-1, 224}, {320, 0}, {320, -1}, {320, 225}}) {
            assertThrows(IllegalArgumentException.class, () -> preparation.advance(
                    fixture.world(), 0, 0, 0, 1, dimensions[0], dimensions[1]));
        }
        verifyNoInteractions(fixture.world(), fixture.factory(), fixture.source(), fixture.data());
    }

    private static Fixture fixture(int tint) {
        return fixture(tint, false);
    }

    private static Fixture fixture(int tint, boolean stateful) {
        WorldSession world = mock(WorldSession.class);
        GameModule module = mock(GameModule.class);
        GameDataSource data = mock(GameDataSource.class);
        Rom rom = mock(Rom.class);
        ZonePictureSource.Factory factory = mock(ZonePictureSource.Factory.class);
        ZonePictureSource source = mock(ZonePictureSource.class);
        List<RecordingScroll> scrolls = new ArrayList<>();
        when(world.getGameModule()).thenReturn(module);
        when(world.getDataSource()).thenReturn(data);
        when(data.rom()).thenReturn(Optional.of(rom));
        when(module.getGameService(ZonePictureSource.Factory.class)).thenReturn(factory);
        when(factory.create(rom)).thenReturn(source);
        when(source.background(anyInt(), anyInt())).thenAnswer(invocation -> {
            RecordingScroll scroll = new RecordingScroll(stateful);
            scrolls.add(scroll);
            return background(scroll, invocation.getArgument(1, Integer.class), tint);
        });
        return new Fixture(world, module, data, rom, factory, source, scrolls);
    }

    private static DetachedBackground background(RecordingScroll scroll, int act, int tint) {
        int[] pixels = new int[STORAGE_WIDTH * STORAGE_HEIGHT];
        for (int y = 0; y < STORAGE_HEIGHT; y++) {
            for (int x = 0; x < STORAGE_WIDTH; x++) pixels[y * STORAGE_WIDTH + x] = colour(x, y, tint);
        }
        return new DetachedBackground(new ZonePictureSource.Picture(STORAGE_WIDTH, STORAGE_HEIGHT, pixels),
                scroll, act, PERIOD_WIDTH, PERIOD_HEIGHT);
    }

    private static int colour(int x, int y, int tint) { return 0xFF000000 | tint | y << 12 | x; }
    private static int lineXOffset(int y) { return (y % 5 - 2) * 11; }
    private static int lineYOffset(int y) { return y % 4 - 2; }

    private record Fixture(WorldSession world, GameModule module, GameDataSource data, Rom rom,
                           ZonePictureSource.Factory factory, ZonePictureSource source,
                           List<RecordingScroll> scrolls) { }

    private static final class RecordingScroll implements ZoneScrollHandler {
        private final boolean stateful;
        private final short[] verticalOffsets = new short[224];
        private short verticalScroll;
        private int updates, inits, initialAct, initialX, initialY;
        private int accumulator, captures, restores, verticalReads, perLineReads;
        private boolean restoreSawInitialized;

        private RecordingScroll() { this(false); }
        private RecordingScroll(boolean stateful) { this.stateful = stateful; }

        @Override public void init(int act, int cameraX, int cameraY) {
            inits++;
            accumulator = 0;
            initialAct = act;
            initialX = cameraX;
            initialY = cameraY;
        }

        @Override public void update(int[] words, int cameraX, int cameraY, int tick, int act) {
            updates++;
            if (stateful) accumulator += 2 + Math.floorMod(tick, 4);
            verticalScroll = (short) (cameraY - 5 + accumulator * 2);
            for (int y = 0; y < words.length; y++) {
                // Deliberately unrelated FG scroll must not leak into Plane B sampling.
                words[y] = 0x5A5A0000 | (-(cameraX + lineXOffset(y) + accumulator) & 0xFFFF);
                verticalOffsets[y] = (short) (lineYOffset(y) + accumulator * (y % 3 - 1));
            }
        }

        private record ScrollState(int accumulator, short verticalScroll) { }
        @Override public Object captureRewindState() {
            captures++;
            return new ScrollState(accumulator, verticalScroll);
        }
        @Override public void restoreRewindState(Object state) {
            restores++;
            restoreSawInitialized = inits > 0;
            ScrollState saved = (ScrollState) state;
            accumulator = saved.accumulator();
            verticalScroll = saved.verticalScroll();
            for (int y = 0; y < verticalOffsets.length; y++)
                verticalOffsets[y] = (short) (lineYOffset(y) + accumulator * (y % 3 - 1));
        }
        @Override public short getVscrollFactorBG() { verticalReads++; return verticalScroll; }
        @Override public short[] getPerLineVScrollBG() { perLineReads++; return verticalOffsets; }
        @Override public int getMinScrollOffset() { return 0; }
        @Override public int getMaxScrollOffset() { return 0; }
    }
}
