package com.openggf.game.sonic3k;

import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.modzone.ModZoneRegistrationException;
import com.openggf.level.Palette;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.level.Pattern;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.HudStaticArt;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
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
                2, hudArt(2, 12), new Pattern[0], () -> hud);
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
        assertThrows(ModZoneRegistrationException.class,
                () -> new S3kCustomZonePaletteBridge(
                        "sample", "sample:level", new Palette(),
                        List.of(new ModPaletteClaim(2, 12, 0x0EEE)),
                        2, hudArt(2, 12), new Pattern[0],
                        () -> paletteWithColor(12, 0x0E00)));
    }

    @Test
    void nullHudOverrideCreatesNoHudReservationOrWrite() {
        S3kCustomZonePaletteBridge bridge = new S3kCustomZonePaletteBridge(
                "sample", "sample:level", new Palette(),
                List.of(new ModPaletteClaim(2, 12, 0x0EEE)),
                2, emptyHudArt(), new Pattern[0], () -> null);
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
                "sample", "sample:level", new Palette(), List.of(), 3,
                hudArt(3, 5), new Pattern[0], current::get);
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
    void enginePaletteWiringDoesNotExpandModApiManagerTypes() {
        assertThrows(NoSuchMethodException.class, () -> LevelManager.class.getMethod(
                "submitCustomZonePaletteClaims", PaletteOwnershipRegistry.class));
        assertThrows(NoSuchMethodException.class, () ->
                com.openggf.level.objects.HudRenderManager.class.getMethod(
                        "setRouteLivesPaletteOverrideThroughOwnership", boolean.class));
    }

    @Test
    void realS3kNullOverrideClaimsColorsUsedByLivesArtFromCharacterPalette() {
        Sonic3kObjectArtProvider hudProvider = new Sonic3kObjectArtProvider();
        Palette character = paletteWithColor(5, 0x00E0);
        character.getColor(7).fromSegaFormat(new byte[]{0x0E, 0x00}, 0);
        Pattern livesNumber = patternWithColor(7);
        S3kCustomZonePaletteBridge bridge = new S3kCustomZonePaletteBridge(
                "sample", "sample:level", character,
                List.of(new ModPaletteClaim(1, 3, 0x000E)),
                hudProvider.getHudFlashPaletteLine(), hudArt(0, 5),
                new Pattern[]{livesNumber}, hudProvider::getHudLivesPaletteOverride);
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] palettes = blankPalettes();

        registry.beginFrame();
        bridge.submitFrameClaims(registry);
        registry.resolveInto(palettes, null, null, palettes[0]);

        assertEquals("host:s3k-hud", registry.ownerAt(PaletteSurface.NORMAL, 0, 5));
        assertEquals("host:s3k-hud", registry.ownerAt(PaletteSurface.NORMAL, 0, 7));
        assertEquals(0x00E0, segaWord(palettes[0].getColor(5)));
        assertEquals(0x0E00, segaWord(palettes[0].getColor(7)));
        assertEquals("sample:level", registry.ownerAt(PaletteSurface.NORMAL, 1, 3));
    }

    @Test
    void hudOverrideWritesOnlyUsedCellsAndIntrinsicPriorityBeatsRuntimeOverrides() {
        Palette character = paletteWithColor(5, 0x000E);
        character.getColor(6).fromSegaFormat(new byte[]{0x00, (byte) 0xE0}, 0);
        Palette override = paletteWithColor(5, 0x0E00);
        override.getColor(6).fromSegaFormat(new byte[]{0x0E, 0x0E}, 0);
        S3kCustomZonePaletteBridge bridge = new S3kCustomZonePaletteBridge(
                "sample", "sample:level", character, List.of(), 0,
                hudArt(0, 5), new Pattern[0], () -> override);
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] palettes = blankPalettes();

        registry.beginFrame();
        bridge.submitFrameClaims(registry);
        registry.submit(PaletteWrite.normal("s3k.cutscene", 300, 0, 5,
                new byte[]{0x00, (byte) 0xE0}));
        registry.resolveInto(palettes, null, null, palettes[0]);

        assertEquals("host:s3k-hud", registry.ownerAt(PaletteSurface.NORMAL, 0, 5));
        assertEquals(0x0E00, segaWord(palettes[0].getColor(5)));
        assertEquals("host:s3k-character", registry.ownerAt(PaletteSurface.NORMAL, 0, 6));
        assertEquals(0x00E0, segaWord(palettes[0].getColor(6)),
                "unused override colors must not replace the character palette");
    }

    private static Palette[] blankPalettes() {
        return new Palette[]{new Palette(), new Palette(), new Palette(), new Palette()};
    }

    private static Palette paletteWithColor(int color, int word) {
        Palette palette = new Palette();
        palette.getColor(color).fromSegaFormat(new byte[]{(byte) (word >>> 8), (byte) word}, 0);
        return palette;
    }

    private static HudStaticArt hudArt(int paletteLine, int color) {
        Pattern used = patternWithColor(color);
        SpriteMappingFrame empty = new SpriteMappingFrame(List.of());
        return new HudStaticArt(new Pattern[]{used}, empty, empty, empty, empty, empty, empty,
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, paletteLine))));
    }

    private static HudStaticArt emptyHudArt() {
        SpriteMappingFrame empty = new SpriteMappingFrame(List.of());
        return new HudStaticArt(new Pattern[0], empty, empty, empty, empty, empty, empty, empty);
    }

    private static Pattern patternWithColor(int color) {
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) color);
        return pattern;
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
