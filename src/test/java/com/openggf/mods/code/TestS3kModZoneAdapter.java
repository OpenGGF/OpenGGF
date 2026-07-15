package com.openggf.mods.code;

import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kModZoneAdapter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestS3kModZoneAdapter {

    @Test
    void s3kRegistrationAcceptsAnAnchorlessZone() {
        ModContext context = new ModContext("alpha", "s3k",
                com.openggf.io.ModAssetRoot.forTests("s3k-zone"), null);

        assertDoesNotThrow(() -> context.registerZone(new ModZoneContribution(
                "sky", new BakedLevelRef("level.json"), null, null)));
    }

    @Test
    void explicitS3kAnchorIsRejectedBecauseTheHostPublishesNoStockAnchors() {
        ModContext context = new ModContext("alpha", "s3k",
                com.openggf.io.ModAssetRoot.forTests("s3k-zone"), null);

        assertThrows(ModRegistrationException.class, () -> context.registerZone(
                new ModZoneContribution("sky", new BakedLevelRef("level.json"),
                        "aiz1", null)));
    }

    @Test
    void s3kAdapterRequiresV2EightByEightAndExplicitMetadataForStockObjects() {
        Sonic3kModZoneAdapter adapter = new Sonic3kModZoneAdapter(
                new com.openggf.game.sonic3k.Sonic3kGameModule());
        List<ModPaletteClaim> claims = backdropClaim();

        assertThrows(ModRegistrationException.class,
                () -> adapter.validate("alpha", definition(1, null, claims)));
        assertThrows(ModRegistrationException.class,
                () -> adapter.validate("alpha", definition(2, 16, null, claims, List.of())));
        assertThrows(ModRegistrationException.class,
                () -> adapter.validate("alpha", definition(2, 8, null, claims,
                        List.of(new ModLevelDefinition.StockObjectSpawn(
                                1, 10, 20, 3, 0, 0, false, 20)))));
        assertDoesNotThrow(() -> adapter.validate("alpha",
                definition(2, null, claims)));
    }

    @Test
    void s3kAdapterBuildsWithHostOwnedPaletteRingSheetAndDeclaredZoneSet() throws Exception {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        Object previous = config.getConfigValue(
                com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE);
        try (var rom = new com.openggf.data.Rom()) {
            File romFile = com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable();
            assumeTrue(romFile != null,
                    "Task 5 S3K adapter test requires a configured S3K ROM");
            assumeTrue(rom.open(romFile.getPath()),
                    "Configured S3K ROM must be readable");
            config.setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE,
                    "knuckles");
            var module = new com.openggf.game.sonic3k.Sonic3kGameModule();
            module.createGame(rom);
            var definition = definition(2,
                    new ModLevelDefinition.S3kMetadata(
                            ModLevelDefinition.S3kObjectZoneSet.SKL), backdropClaim());

            Sonic3kLevel level = assertInstanceOf(Sonic3kLevel.class,
                    module.getModZoneAdapter().load("alpha", definition));

            assertEquals(S3kZoneSet.SKL, level.getObjectZoneSet());
            assertSame(module.getAdditiveLevelRingSpriteSheet(), level.getRingSpriteSheet());
            byte[] expected = rom.readBytes(
                    com.openggf.game.sonic3k.constants.Sonic3kConstants.KNUCKLES_PALETTE_ADDR,
                    32);
            com.openggf.level.Palette palette = new com.openggf.level.Palette();
            palette.fromSegaFormat(expected);
            assertEquals(palette.getColor(1).r, level.getPalette(0).getColor(1).r);
            assertEquals(palette.getColor(1).g, level.getPalette(0).getColor(1).g);
            assertEquals(palette.getColor(1).b, level.getPalette(0).getColor(1).b);
        } finally {
            config.setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE,
                    previous == null ? "sonic" : previous);
        }
    }

    @Test
    void typedMetadataMapsToTheExactInternalZoneSet() {
        assertEquals(S3kZoneSet.S3KL, map(ModLevelDefinition.S3kObjectZoneSet.S3KL));
        assertEquals(S3kZoneSet.SKL, map(ModLevelDefinition.S3kObjectZoneSet.SKL));
    }

    @Test
    void sonic2AdapterAcceptsV1AndRejectsV2() {
        var adapter = new com.openggf.game.sonic2.Sonic2ModZoneAdapter(
                new com.openggf.game.sonic2.Sonic2GameModule());

        assertDoesNotThrow(() -> adapter.validate("alpha", definition(1, null, List.of())));
        assertThrows(ModRegistrationException.class,
                () -> adapter.validate("alpha", definition(2,
                        new ModLevelDefinition.S3kMetadata(
                                ModLevelDefinition.S3kObjectZoneSet.S3KL), List.of())));
    }

    private static S3kZoneSet map(ModLevelDefinition.S3kObjectZoneSet source) {
        return S3kZoneSet.valueOf(source.name());
    }

    static ModLevelDefinition definition(int version, ModLevelDefinition.S3kMetadata metadata,
                                         List<ModPaletteClaim> claims) {
        return definition(version, 8, metadata, claims, List.of());
    }

    static ModLevelDefinition definition(int version, int blockGridSide,
                                         ModLevelDefinition.S3kMetadata metadata,
                                         List<ModPaletteClaim> claims,
                                         List<ModLevelDefinition.ObjectEntry> objects) {
        return new ModLevelDefinition(version, "SKY", 0x40, 0x400, blockGridSide, 1, 1,
                new ModLevelDefinition.Bounds(0, 0x100, 0, 0x100),
                new ModLevelDefinition.Start(0x20, 0x20),
                new ModLevelDefinition.StockMusic(1), objects, List.of(),
                new byte[32], new byte[8], new byte[blockGridSide * blockGridSide * 2],
                new byte[1], null,
                new byte[16], new byte[16], new byte[1], new int[]{0}, new int[]{0},
                new byte[][]{new byte[32], new byte[32], new byte[32], new byte[32]},
                1, 1, 1, 1, metadata, claims);
    }

    private static List<ModPaletteClaim> backdropClaim() {
        return List.of(new ModPaletteClaim(2, 0, 0));
    }
}
