package com.openggf.game.sonic3k;

import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.level.Palette;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.mods.code.ModPaletteClaim;
import com.openggf.mods.code.ModRegistrationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestS3kCustomZonePaletteBridge {

    @Test
    void customZoneComposesCharacterCreatorAndConfiguredHudClaims() {
        Palette character = paletteWithColor(2, 0x00E0);
        Palette hud = paletteWithColor(12, 0x0E00);
        S3kCustomZonePaletteBridge bridge = new S3kCustomZonePaletteBridge(
                "sample", "sample:level", character,
                List.of(new ModPaletteClaim(1, 3, 0x000E)),
                2, () -> hud);
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] palettes = blankPalettes();

        registry.beginFrame();
        bridge.submitFrameClaims(registry);
        registry.resolveInto(palettes, null, null, palettes[0]);

        assertEquals("host:s3k-character", registry.ownerAt(PaletteSurface.NORMAL, 0, 2));
        assertEquals("sample:level", registry.ownerAt(PaletteSurface.NORMAL, 1, 3));
        assertEquals("host:s3k-hud", registry.ownerAt(PaletteSurface.NORMAL, 2, 12));
        assertEquals(0x00E0, segaWord(palettes[0].getColor(2)));
        assertEquals(0x000E, segaWord(palettes[1].getColor(3)));
        assertEquals(0x0E00, segaWord(palettes[2].getColor(12)));
    }

    @Test
    void creatorCannotClaimCharacterOrConfiguredHudReservation() {
        assertThrows(IllegalArgumentException.class, () -> new ModPaletteClaim(0, 2, 0x0EEE));
        assertThrows(ModRegistrationException.class,
                () -> S3kCustomZonePaletteBridge.validateCreatorClaims(
                        "sample", List.of(new ModPaletteClaim(2, 12, 0x0EEE)),
                        2, paletteWithColor(12, 0x0E00)));
    }

    @Test
    void nullHudOverrideCreatesNoHudReservationOrWrite() {
        S3kCustomZonePaletteBridge.validateCreatorClaims(
                "sample", List.of(new ModPaletteClaim(2, 12, 0x0EEE)), 2, null);
        S3kCustomZonePaletteBridge bridge = new S3kCustomZonePaletteBridge(
                "sample", "sample:level", new Palette(),
                List.of(new ModPaletteClaim(2, 12, 0x0EEE)),
                2, () -> null);
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] palettes = blankPalettes();

        registry.beginFrame();
        bridge.submitFrameClaims(registry);
        registry.resolveInto(palettes, null, null, palettes[0]);

        assertEquals("sample:level", registry.ownerAt(PaletteSurface.NORMAL, 2, 12));
    }

    @Test
    void liveHudOverrideIsReadForEachPaletteFrame() {
        AtomicReference<Palette> current = new AtomicReference<>(paletteWithColor(5, 0x000E));
        S3kCustomZonePaletteBridge bridge = new S3kCustomZonePaletteBridge(
                "sample", "sample:level", new Palette(), List.of(), 3, current::get);
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] palettes = blankPalettes();

        registry.beginFrame();
        bridge.submitFrameClaims(registry);
        registry.resolveInto(palettes, null, null, palettes[0]);
        assertEquals(0x000E, segaWord(palettes[3].getColor(5)));

        current.set(paletteWithColor(5, 0x0E00));
        registry.beginFrame();
        bridge.submitFrameClaims(registry);
        registry.resolveInto(palettes, null, null, palettes[0]);
        assertEquals(0x0E00, segaWord(palettes[3].getColor(5)));
    }

    @Test
    void mutableSnapshotKeepsCustomS3kPaletteBridgeEligibility() {
        MutableLevel mutable = MutableLevel.snapshot(s3kLevel(false));

        assertTrue(S3kCustomZonePaletteBridge.supports(mutable));
    }

    @Test
    void mutableSnapshotOfStockS3kLevelDoesNotInstallBridge() {
        MutableLevel mutable = MutableLevel.snapshot(s3kLevel(true));

        assertFalse(S3kCustomZonePaletteBridge.supports(mutable));
    }

    @Test
    void realS3kHudWithoutLivesOverrideSubmitsOnlyCharacterAndCreatorClaims() {
        Sonic3kObjectArtProvider hudProvider = new Sonic3kObjectArtProvider();
        S3kCustomZonePaletteBridge bridge = new S3kCustomZonePaletteBridge(
                "sample", "sample:level", paletteWithColor(4, 0x00E0),
                List.of(new ModPaletteClaim(1, 3, 0x000E)),
                hudProvider.getHudFlashPaletteLine(), hudProvider::getHudLivesPaletteOverride);
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] palettes = blankPalettes();

        registry.beginFrame();
        bridge.submitFrameClaims(registry);
        registry.resolveInto(palettes, null, null, palettes[0]);

        assertEquals("host:s3k-character", registry.ownerAt(PaletteSurface.NORMAL, 0, 4));
        assertEquals("sample:level", registry.ownerAt(PaletteSurface.NORMAL, 1, 3));
        assertEquals("host:s3k-character", registry.ownerAt(PaletteSurface.NORMAL,
                hudProvider.getHudFlashPaletteLine(), 12));
    }

    private static Palette[] blankPalettes() {
        return new Palette[]{new Palette(), new Palette(), new Palette(), new Palette()};
    }

    private static Palette paletteWithColor(int color, int word) {
        Palette palette = new Palette();
        palette.getColor(color).fromSegaFormat(new byte[]{(byte) (word >>> 8), (byte) word}, 0);
        return palette;
    }

    private static int segaWord(Palette.Color color) {
        return com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(color);
    }

    private static Sonic3kLevel s3kLevel(boolean stockIdentity) {
        Sonic3kLevel level = mock(Sonic3kLevel.class);
        when(level.hasStockRomZoneIdentity()).thenReturn(stockIdentity);
        when(level.getMap()).thenReturn(new Map(1, 1, 1, new byte[]{0}));
        when(level.getPaletteCount()).thenReturn(4);
        for (int line = 0; line < 4; line++) {
            when(level.getPalette(line)).thenReturn(new Palette());
        }
        when(level.getObjects()).thenReturn(List.of());
        when(level.getRings()).thenReturn(List.of());
        when(level.getBlockPixelSize()).thenReturn(128);
        when(level.getChunksPerBlockSide()).thenReturn(8);
        return level;
    }
}
