package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.PaletteLoader;
import com.openggf.data.RomByteReader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2ArtOverlays;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.game.sonic2.Sonic2WaterDataProvider;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.util.PatternDecompressor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * The chip-resident KiS2 assets read from the user-supplied lock-on dump
 * (BRANCH_DIFFS.md §Chip addresses) and their application through the Sonic 2
 * overlay seams. Skips without the dump; the sheet tests also need the S2 ROM.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestKis2ChipArt {

    private static RomByteReader dump;
    private static RomByteReader sk;
    private static RomByteReader s2;
    private static Kis2ChipArt chip;

    @BeforeAll
    static void loadRoms() throws Exception {
        dump = Kis2TestRoms.lockOnDumpOrNull();
        File s2File = RomTestUtils.ensureSonic2RomAvailable();
        File s3kFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(dump != null && s2File != null && s3kFile != null,
                "KiS2 chip art needs the lock-on dump plus the S2 and S3K ROMs");
        s2 = RomByteReader.fromBytes(Files.readAllBytes(s2File.toPath()));
        sk = LogicalRomResolver.windowSkFromCombined(Files.readAllBytes(s3kFile.toPath()));
        chip = new Kis2ChipArt(LockOnAddressSpace.tierTwo(sk, s2, dump));
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void everyChipAssetDecompressesToTheTileCountItsNemesisHeaderDeclares() {
        for (Kis2ChipArt.Asset asset : Kis2ChipArt.Asset.values()) {
            Pattern[] tiles = chip.load(asset);
            assertEquals(asset.tileCount(), tiles.length, asset.name());
            assertFalse(isBlank(tiles), asset + " must not decode to empty tiles");
        }
        assertEquals(Kis2Constants.SHIELD_TILE_COUNT, chip.shield().length);
        assertEquals(66 - Kis2Constants.SHIELD_TILE_COUNT, chip.invincibilityStars().length);
    }

    @Test
    void chipAssetsAreFreshCopiesAndTheTierOneConstructorRejectsAMissingChip() {
        Pattern[] first = chip.load(Kis2ChipArt.Asset.LIFE_COUNTER);
        Pattern[] second = chip.load(Kis2ChipArt.Asset.LIFE_COUNTER);
        assertTrue(first != second && first[0] == second[0], "cached tiles, cloned array");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Kis2ChipArt(LockOnAddressSpace.tierOne(sk, s2)));
    }

    /**
     * Settles the catalogue's open question: the chip's {@code Pal_BGND} line 0
     * is byte-identical to the S&amp;K {@code Pal_KnuxEndPose} line tier one draws with.
     */
    @Test
    void chipBackgroundLineZeroEqualsTheSkKnucklesLineTierOneUses() {
        Palette fromChip = chip.backgroundPaletteLine0();
        Palette fromSk = new Palette();
        fromSk.fromSegaFormat(sk.slice(Kis2Constants.PAL_KNUCKLES_S2_LAYOUT, Palette.PALETTE_SIZE_IN_ROM));
        assertTrue(fromChip.dataEquals(fromSk), "Pal_BGND line 0 == Pal_KnuxEndPose");
        Palette stockSonic = new Palette();
        stockSonic.fromSegaFormat(s2.slice(Sonic2Constants.SONIC_TAILS_PALETTE_ADDR, Palette.PALETTE_SIZE_IN_ROM));
        assertFalse(fromChip.dataEquals(stockSonic), "Knuckles' reds replace Sonic's blues");
    }

    @Test
    void underwaterPalettesRecolourLineZeroOnlyAndTheWaterSeamServesThem() throws Exception {
        Palette[] cpz = chip.chemicalPlantUnderwaterPalette();
        Palette[] arz = chip.aquaticRuinUnderwaterPalette();
        Palette[] stockCpz = PaletteLoader.fromBytes(s2.slice(0x2E62, Kis2Constants.UNDERWATER_PALETTE_SIZE));
        Palette[] stockArz = PaletteLoader.fromBytes(s2.slice(0x2FA2, Kis2Constants.UNDERWATER_PALETTE_SIZE));
        assertFalse(cpz[0].dataEquals(stockCpz[0]), "Pal_CPZ_U line 0 changed for Knuckles");
        assertFalse(arz[0].dataEquals(stockArz[0]), "Pal_ARZ_U line 0 changed for Knuckles");
        for (int line = 1; line < 4; line++) {
            assertTrue(cpz[line].dataEquals(stockCpz[line]), "Pal_CPZ_U line " + line + " is stock");
            assertTrue(arz[line].dataEquals(stockArz[line]), "Pal_ARZ_U line " + line + " is stock");
        }

        Sonic2WaterDataProvider provider = new Sonic2WaterDataProvider((zoneId, actId) ->
                zoneId == Sonic2ZoneConstants.ROM_ZONE_CPZ ? chip.chemicalPlantUnderwaterPalette() : null);
        try (com.openggf.data.Rom rom = new com.openggf.data.Rom()) {
            assumeTrue(rom.open(RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath()));
            Palette[] served = provider.getUnderwaterPalette(rom, Sonic2ZoneConstants.ROM_ZONE_CPZ, 1,
                    PlayerCharacter.SONIC_ALONE);
            assertTrue(served[0].dataEquals(cpz[0]), "override answers CPZ");
            Palette[] stock = provider.getUnderwaterPalette(rom, Sonic2ZoneConstants.ROM_ZONE_ARZ, 0,
                    PlayerCharacter.SONIC_ALONE);
            assertTrue(stock[0].dataEquals(stockArz[0]), "a null answer keeps the stock palette");
        }
    }

    @Test
    void overlaysPatchTheMonitorSignpostShieldAndStarsSheetsLikeThePatchedPlcLists() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        SessionManager.openGameplaySession(GameModuleRegistry.getCurrent(),
                SaveSessionContext.noSave("s2", new SelectedTeam("knuckles", List.of()), 0, 0));

        Sonic2ObjectArtProvider stock = new Sonic2ObjectArtProvider();
        Sonic2ObjectArtProvider patched = new Sonic2ObjectArtProvider(new Sonic2ArtOverlays(
                () -> chip.load(Kis2ChipArt.Asset.LIFE_COUNTER),
                List.of(new Sonic2ArtOverlays.SheetPatch(ObjectArtKeys.MONITOR,
                                Kis2Constants.POWERUPS_KNUCKLES_PATCH_TILE,
                                () -> chip.load(Kis2ChipArt.Asset.POWERUPS_PATCH)),
                        new Sonic2ArtOverlays.SheetPatch(ObjectArtKeys.SHIELD, 0, chip::shield),
                        new Sonic2ArtOverlays.SheetPatch(ObjectArtKeys.INVINCIBILITY_STARS, 0,
                                chip::invincibilityStars),
                        new Sonic2ArtOverlays.SheetPatch(ObjectArtKeys.SIGNPOST,
                                Kis2Constants.SIGNPOST_KNUCKLES_PATCH_TILE,
                                () -> chip.load(Kis2ChipArt.Asset.SIGNPOST_PATCH)),
                        new Sonic2ArtOverlays.SheetPatch("no-such-sheet", 0,
                                () -> chip.load(Kis2ChipArt.Asset.SIGNPOST_PATCH)))));
        try (MockedStatic<CrossGameFeatureProvider> donor = mockStatic(CrossGameFeatureProvider.class)) {
            donor.when(CrossGameFeatureProvider::isS3kDonorActive).thenReturn(false);
            stock.loadArtForZone(Sonic2ZoneConstants.ROM_ZONE_EHZ);
            patched.loadArtForZone(Sonic2ZoneConstants.ROM_ZONE_EHZ);
        }

        // PlrList_Std1: the lives counter is the chip's, and the 1-up face follows it.
        assertArrayEquals(pixels(chip.load(Kis2ChipArt.Asset.LIFE_COUNTER)[0]),
                pixels(patched.getHudLivesPatterns()[0]), "HUD life counter is the chip's");
        assertTilesEqual(chip.load(Kis2ChipArt.Asset.LIFE_COUNTER), patched.getSheet(ObjectArtKeys.MONITOR),
                com.openggf.game.sonic2.Sonic2ObjectArt.MONITOR_LIFE_ICON_TILE);
        assertTilesDiffer(stock.getSheet(ObjectArtKeys.MONITOR), patched.getSheet(ObjectArtKeys.MONITOR),
                com.openggf.game.sonic2.Sonic2ObjectArt.MONITOR_LIFE_ICON_TILE, 12);
        // PlrList_Std2: monitor icons +44, merged shield/stars at the shield base.
        assertTilesEqual(chip.load(Kis2ChipArt.Asset.POWERUPS_PATCH), patched.getSheet(ObjectArtKeys.MONITOR),
                Kis2Constants.POWERUPS_KNUCKLES_PATCH_TILE);
        assertTilesDiffer(stock.getSheet(ObjectArtKeys.MONITOR), patched.getSheet(ObjectArtKeys.MONITOR),
                Kis2Constants.POWERUPS_KNUCKLES_PATCH_TILE, 8);
        assertTilesEqual(stock.getSheet(ObjectArtKeys.MONITOR).getPatterns(),
                patched.getSheet(ObjectArtKeys.MONITOR), 0, Kis2Constants.POWERUPS_KNUCKLES_PATCH_TILE);
        assertTilesEqual(chip.shield(), patched.getSheet(ObjectArtKeys.SHIELD), 0);
        assertTilesEqual(chip.invincibilityStars(), patched.getSheet(ObjectArtKeys.INVINCIBILITY_STARS), 0);
        assertTilesDiffer(stock.getSheet(ObjectArtKeys.SHIELD), patched.getSheet(ObjectArtKeys.SHIELD), 0, 32);
        // PlrList_Signpost: Knuckles' face at +34, the plate before it untouched.
        assertTilesEqual(chip.load(Kis2ChipArt.Asset.SIGNPOST_PATCH), patched.getSheet(ObjectArtKeys.SIGNPOST),
                Kis2Constants.SIGNPOST_KNUCKLES_PATCH_TILE);
        assertTilesEqual(stock.getSheet(ObjectArtKeys.SIGNPOST).getPatterns(),
                patched.getSheet(ObjectArtKeys.SIGNPOST), 0, Kis2Constants.SIGNPOST_KNUCKLES_PATCH_TILE);
        assertTilesDiffer(stock.getSheet(ObjectArtKeys.SIGNPOST), patched.getSheet(ObjectArtKeys.SIGNPOST),
                Kis2Constants.SIGNPOST_KNUCKLES_PATCH_TILE, 24);
        // hud_a: the lives name piece moves to palette line 0 with the patch icon.
        assertEquals(0, patched.getHudStaticArt().livesFrame().pieces().get(1).paletteIndex());
        assertEquals(1, stock.getHudStaticArt().livesFrame().pieces().get(1).paletteIndex());
    }

    @Test
    void chipAssetsMatchTheStockArtGeometryTheyPatch() {
        Pattern[] stockShield = PatternDecompressor.fromBytes(nemesis(s2, Sonic2Constants.ART_NEM_SHIELD_ADDR));
        Pattern[] stockStars = PatternDecompressor.fromBytes(
                nemesis(s2, Sonic2Constants.ART_NEM_INVINCIBILITY_STARS_ADDR));
        assertEquals(stockShield.length, chip.shield().length, "shield keeps 32 tiles");
        assertEquals(stockStars.length, chip.invincibilityStars().length, "stars keep 34 tiles");
        Pattern[] stockLife = PatternDecompressor.fromBytes(nemesis(s2, Sonic2Constants.ART_NEM_SONIC_LIFE_ADDR));
        assertEquals(stockLife.length, chip.load(Kis2ChipArt.Asset.LIFE_COUNTER).length);
        Pattern[] stockMini = PatternDecompressor.fromBytes(nemesis(s2, Sonic2Constants.ART_NEM_MINI_SONIC_ADDR));
        assertEquals(stockMini.length, chip.load(Kis2ChipArt.Asset.CONTINUE_ICON).length);
    }

    private static byte[] nemesis(RomByteReader reader, int address) {
        try (var bais = new java.io.ByteArrayInputStream(reader.slice(address,
                Math.min(8192, reader.size() - address)));
             var channel = java.nio.channels.Channels.newChannel(bais)) {
            return com.openggf.data.compression.NemesisReader.decompress(channel);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertTilesEqual(Pattern[] expected, ObjectSpriteSheet sheet, int firstTile) {
        assertTilesEqual(expected, sheet, firstTile, expected.length);
    }

    private static void assertTilesEqual(Pattern[] expected, ObjectSpriteSheet sheet, int firstTile, int count) {
        assertNotNull(sheet);
        Pattern[] actual = sheet.getPatterns();
        for (int i = 0; i < count; i++) {
            assertArrayEquals(pixels(expected[i]), pixels(actual[firstTile + i]), "tile " + (firstTile + i));
        }
    }

    private static void assertTilesDiffer(ObjectSpriteSheet stock, ObjectSpriteSheet patched, int firstTile, int count) {
        int differing = 0;
        for (int i = 0; i < count; i++) {
            if (!java.util.Arrays.equals(pixels(stock.getPatterns()[firstTile + i]),
                    pixels(patched.getPatterns()[firstTile + i]))) {
                differing++;
            }
        }
        assertTrue(differing > 0, "tiles " + firstTile + ".." + (firstTile + count - 1) + " must change");
    }

    private static byte[] pixels(Pattern tile) {
        byte[] out = new byte[Pattern.PATTERN_WIDTH * Pattern.PATTERN_HEIGHT];
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                out[y * Pattern.PATTERN_WIDTH + x] = tile.getPixel(x, y);
            }
        }
        return out;
    }

    private static boolean isBlank(Pattern[] tiles) {
        for (Pattern tile : tiles) {
            for (byte pixel : pixels(tile)) {
                if (pixel != 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
