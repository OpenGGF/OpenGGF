package com.openggf.game.internal;

import com.openggf.data.RomByteReader;
import com.openggf.game.ObjectArtOverlayProvider;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic1.Sonic1ObjectArtProvider;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Independent native HUD SAT topology checks; these are pieces, not the engine's expanded label tiles. */
class TestHudSatLayoutRom {
    private static final int[] STOCK_X = {0, 32, 64, 0, 40, 0, 32, 48, 0, 16};
    private static final int[] STOCK_Y = {-128, -128, -128, -112, -112, -96, -96, -96, 64, 64};
    private static final int[] STOCK_WIDTH = {4, 4, 4, 4, 4, 4, 1, 3, 2, 4};
    private static final int[] STOCK_TILE = {0, 0x18, 0x20, 0x10, 0x28, 8, 0, 0x30, 0x10A, 0x10E};

    @Test
    @RequiresRom(SonicGame.SONIC_1)
    void sonic1ProviderLoadsTenNativePiecesAndPaletteOnlyWarningVariants() throws Exception {
        Sonic1ObjectArtProvider provider = new Sonic1ObjectArtProvider();
        provider.loadArtForZone(0);
        HudSatLayout layout = provider.getHudSatLayout();
        assertNotNull(layout);
        assertFalse(layout.supportsBonusStage());
        assertEquals(List.of(10, 10, 10, 10), pieceCounts(layout));
        assertStockFrame(layout.frame(false, false, false), true, 0, 0, 0);
        for (boolean timer : List.of(false, true)) {
            for (boolean rings : List.of(false, true)) {
                SpriteMappingFrame frame = layout.frame(timer, rings, false);
                assertEquals(timer ? 1 : 0, frame.pieces().get(3).paletteIndex());
                assertEquals(rings ? 1 : 0, frame.pieces().get(5).paletteIndex());
                assertEquals(rings ? 1 : 0, frame.pieces().get(6).paletteIndex());
                assertEquals(10, frame.pieces().size(), "warning palettes do not release native SAT slots");
                assertSame(frame, layout.frame(timer, rings, true), "no fabricated S1 bonus topology");
            }
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void sonic2ExplicitReaderProviderPreservesEightByteMappingLayoutAndFlashPalettes() throws Exception {
        Sonic2ObjectArtProvider provider = new Sonic2ObjectArtProvider(TestEnvironment.currentRom(),
                RomByteReader.fromRom(TestEnvironment.currentRom()));
        HudSatLayout layout = provider.getHudSatLayout();
        assertNotNull(layout);
        assertFalse(layout.supportsBonusStage());
        assertEquals(List.of(10, 10, 10, 10), pieceCounts(layout));
        assertStockFrame(layout.frame(false, false, false), false, 1, 0, 1);
        for (boolean timer : List.of(false, true)) {
            for (boolean rings : List.of(false, true)) {
                SpriteMappingFrame frame = layout.frame(timer, rings, false);
                assertEquals(timer ? 0 : 1, frame.pieces().get(3).paletteIndex());
                assertEquals(rings ? 0 : 1, frame.pieces().get(5).paletteIndex());
                assertEquals(rings ? 0 : 1, frame.pieces().get(6).paletteIndex());
                assertEquals(10, frame.pieces().size());
                assertSame(layout.frames().get((timer ? 2 : 0) | (rings ? 1 : 0)), frame);
            }
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kProviderLoadsRemovedWarningLabelsAndRingOnlyBonusFrames() throws Exception {
        Sonic3kObjectArtProvider provider = new Sonic3kObjectArtProvider();
        provider.loadArtForZone(0);
        HudSatLayout layout = provider.getHudSatLayout();
        assertNotNull(layout);
        assertTrue(layout.supportsBonusStage());
        assertEquals(List.of(10, 8, 9, 7, 5, 3), pieceCounts(layout));
        SpriteMappingFrame stock = layout.frame(false, false, false);
        assertStockFrame(stock, false, 1, 0, 1);
        assertEquals(selectPieces(stock, 0, 1, 2, 3, 4, 7, 8, 9),
                layout.frame(false, true, false).pieces(), "ring flash removes both RING and S entries");
        assertEquals(selectPieces(stock, 0, 1, 2, 4, 5, 6, 7, 8, 9),
                layout.frame(true, false, false).pieces(), "timer flash removes the TIME entry");
        assertEquals(selectPieces(stock, 0, 1, 2, 4, 7, 8, 9), layout.frame(true, true, false).pieces());
        SpriteMappingFrame bonus = layout.frame(false, false, true);
        assertEquals(List.of(
                new SpriteMappingPiece(0, -128, 4, 2, 8, false, false, 1),
                new SpriteMappingPiece(32, -128, 1, 2, 0, false, false, 1),
                new SpriteMappingPiece(48, -128, 3, 2, 0x30, false, false, 1),
                stock.pieces().get(8), stock.pieces().get(9)), bonus.pieces());
        assertEquals(selectPieces(bonus, 2, 3, 4), layout.frame(false, true, true).pieces());
        assertSame(bonus, layout.frame(true, false, true), "bonus stages have no timer-warning selector");
        assertSame(layout.frames().get(5), layout.frame(true, true, true));
    }

    @Test
    void layoutFreezesBothLevelsOfMappingLists() {
        ArrayList<SpriteMappingPiece> pieces = new ArrayList<>(List.of(
                new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0)));
        ArrayList<SpriteMappingFrame> frames = new ArrayList<>();
        for (int i = 0; i < 4; i++) frames.add(new SpriteMappingFrame(pieces));
        HudSatLayout layout = new HudSatLayout(frames);
        pieces.clear();
        frames.clear();
        assertEquals(List.of(1, 1, 1, 1), pieceCounts(layout));
        assertThrows(UnsupportedOperationException.class, () -> layout.frames().clear());
        assertThrows(UnsupportedOperationException.class, () -> layout.frames().get(0).pieces().clear());
        assertThrows(IllegalArgumentException.class, () -> new HudSatLayout(List.of()));
    }

    @Test
    void artOverlayForwardsOnlyAnExistingNativeLayout() {
        HudSatLayout layout = new HudSatLayout(java.util.Collections.nCopies(4, new SpriteMappingFrame(List.of())));
        ObjectArtProvider nativeProvider = mock(ObjectArtProvider.class,
                withSettings().extraInterfaces(HudSatLayoutSource.class));
        when(((HudSatLayoutSource) nativeProvider).getHudSatLayout()).thenReturn(layout);
        assertSame(layout, new ObjectArtOverlayProvider(nativeProvider, Map.of()).getHudSatLayout());
        assertNull(new ObjectArtOverlayProvider(mock(ObjectArtProvider.class), Map.of()).getHudSatLayout());
    }

    private static List<Integer> pieceCounts(HudSatLayout layout) {
        return layout.frames().stream().map(frame -> frame.pieces().size()).toList();
    }

    private static List<SpriteMappingPiece> selectPieces(SpriteMappingFrame frame, int... indexes) {
        ArrayList<SpriteMappingPiece> result = new ArrayList<>();
        for (int index : indexes) result.add(frame.pieces().get(index));
        return result;
    }

    private static void assertStockFrame(SpriteMappingFrame frame, boolean priority,
                                         int textPalette, int lifeIconPalette, int lifeNamePalette) {
        assertEquals(10, frame.pieces().size());
        for (int i = 0; i < 10; i++) {
            int palette = i == 8 ? lifeIconPalette : i == 9 ? lifeNamePalette : textPalette;
            assertEquals(new SpriteMappingPiece(STOCK_X[i], STOCK_Y[i], STOCK_WIDTH[i], 2,
                    STOCK_TILE[i], false, false, palette, priority), frame.pieces().get(i), "SAT entry " + i);
        }
    }
}
