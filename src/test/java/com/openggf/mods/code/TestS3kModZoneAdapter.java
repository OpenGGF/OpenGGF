package com.openggf.mods.code;

import com.openggf.game.modzone.ModObjectZoneSet;
import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.modzone.ModZoneHostMetadata;
import com.openggf.game.modzone.ModZoneLevelData;
import com.openggf.game.modzone.ModZoneRegistrationException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        assertThrows(ModZoneRegistrationException.class,
                () -> adapter.validate("alpha", hostData(definition(1, null, claims))));
        assertThrows(ModZoneRegistrationException.class,
                () -> adapter.validate("alpha", hostData(definition(2, 16, null, claims, List.of()))));
        assertThrows(ModZoneRegistrationException.class,
                () -> adapter.validate("alpha", hostData(definition(2, 8, null, claims,
                        List.of(new ModLevelDefinition.StockObjectSpawn(
                                1, 10, 20, 3, 0, 0, false, 20))))));
        assertDoesNotThrow(() -> adapter.validate("alpha",
                hostData(definition(2, null, claims))));
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
                    new ModZoneHostMetadata(ModObjectZoneSet.SKL), backdropClaim());

            Sonic3kLevel level = assertInstanceOf(Sonic3kLevel.class,
                    module.getModZoneAdapter().load("alpha", hostData(definition)));

            assertEquals(S3kZoneSet.SKL, level.getObjectZoneSet());
            assertFalse(level.hasStockRomZoneIdentity());
            assertSame(module.getAdditiveLevelRingSpriteSheet(), level.getRingSpriteSheet());
            byte[] expected = rom.readBytes(
                    com.openggf.game.sonic3k.constants.Sonic3kConstants.KNUCKLES_PALETTE_ADDR,
                    32);
            com.openggf.level.Palette palette = new com.openggf.level.Palette();
            palette.fromSegaFormat(expected);
            assertEquals(palette.getColor(1).r, level.getPalette(0).getColor(1).r);
            assertEquals(palette.getColor(1).g, level.getPalette(0).getColor(1).g);
            assertEquals(palette.getColor(1).b, level.getPalette(0).getColor(1).b);

            com.openggf.level.MutableLevel nestedSnapshot =
                    com.openggf.level.MutableLevel.snapshot(
                            com.openggf.level.MutableLevel.snapshot(level));
            assertSame(level, com.openggf.level.LevelOrigin.original(nestedSnapshot));
            var snapshotRegistry = new LevelBackedRegistry(nestedSnapshot);
            assertInstanceOf(com.openggf.level.objects.PlaceholderObjectInstance.class,
                    snapshotRegistry.create(new com.openggf.level.objects.ObjectSpawn(
                            10, 20,
                            com.openggf.game.sonic3k.constants.Sonic3kObjectIds.LBZ_PIPE_PLUG,
                            0, 0, false, 1)));

            Sonic3kLevel s3kl = assertInstanceOf(Sonic3kLevel.class,
                    module.getModZoneAdapter().load("alpha", hostData(definition(2,
                            new ModZoneHostMetadata(ModObjectZoneSet.S3KL),
                            backdropClaim()))));
            assertEquals(S3kZoneSet.S3KL, s3kl.getObjectZoneSet());
            assertFalse(s3kl.hasStockRomZoneIdentity());
        } finally {
            config.setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE,
                    previous == null ? "sonic" : previous);
        }
    }

    @Test
    void typedMetadataMapsToTheExactInternalZoneSet() {
        assertEquals(S3kZoneSet.S3KL, map(ModObjectZoneSet.S3KL));
        assertEquals(S3kZoneSet.SKL, map(ModObjectZoneSet.SKL));
    }

    @Test
    void zoneIdGatedStockFactoryIsRejectedBeforePublication() {
        Sonic3kModZoneAdapter adapter = new Sonic3kModZoneAdapter(
                new com.openggf.game.sonic3k.Sonic3kGameModule());
        var stockPipePlug = new ModLevelDefinition.StockObjectSpawn(
                1, 10, 20,
                com.openggf.game.sonic3k.constants.Sonic3kObjectIds.LBZ_PIPE_PLUG,
                0, 0, false, 20);
        ModZoneRegistrationException failure = assertThrows(ModZoneRegistrationException.class,
                () -> adapter.validate("alpha", hostData(definition(2, 8,
                        new ModZoneHostMetadata(ModObjectZoneSet.S3KL),
                        backdropClaim(), List.of(stockPipePlug)))));

        assertEquals("MOD_S3K_STOCK_OBJECT_INCOMPATIBLE", failure.findingCode());
    }

    @Test
    void namespacedObjectBypassesStockFactoryCompatibility() {
        Sonic3kModZoneAdapter adapter = new Sonic3kModZoneAdapter(
                new com.openggf.game.sonic3k.Sonic3kGameModule());
        var controller = new ModLevelDefinition.KeyedObjectSpawn(
                1, 10, 20, "alpha:controller", 0, 0, false, 20);

        assertDoesNotThrow(() -> adapter.validate("alpha", hostData(definition(2, 8, null,
                backdropClaim(), List.of(controller)))));
    }

    @Test
    void s3kDataSelectDecorationUsesEffectiveRegistryAndPreservesNativePresentation() {
        Sonic3kModZoneAdapter adapter = new Sonic3kModZoneAdapter(
                new com.openggf.game.sonic3k.Sonic3kGameModule());
        com.openggf.game.ZoneRegistry effective =
                org.mockito.Mockito.mock(com.openggf.game.ZoneRegistry.class);
        com.openggf.game.dataselect.DataSelectHostProfile inherited =
                new com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile();
        com.openggf.game.dataselect.DataSelectPresentationProvider presentation =
                org.mockito.Mockito.mock(
                        com.openggf.game.dataselect.DataSelectPresentationProvider.class);

        var decorated = adapter.decorateHostProfile(
                inherited, () -> effective, (owner, finding) -> { });

        assertInstanceOf(com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile.class,
                decorated);
        var decoratedPresentation =
                adapter.decoratePresentationProvider(presentation, decorated);
        org.junit.jupiter.api.Assertions.assertNotSame(presentation, decoratedPresentation);
        assertSame(decorated, decoratedPresentation.controller().hostProfile());
    }

    @Test
    void sonic2AdapterAcceptsV1AndRejectsV2() {
        var adapter = new com.openggf.game.sonic2.Sonic2ModZoneAdapter(
                new com.openggf.game.sonic2.Sonic2GameModule());

        assertDoesNotThrow(() -> adapter.validate("alpha", hostData(definition(1, null, List.of()))));
        assertThrows(ModZoneRegistrationException.class,
                () -> adapter.validate("alpha", hostData(definition(2,
                        new ModZoneHostMetadata(ModObjectZoneSet.S3KL), List.of()))));
    }

    private static S3kZoneSet map(ModObjectZoneSet source) {
        return S3kZoneSet.valueOf(source.name());
    }

    static ModLevelDefinition definition(int version, ModZoneHostMetadata metadata,
                                         List<ModPaletteClaim> claims) {
        return definition(version, 8, metadata, claims, List.of());
    }

    static ModLevelDefinition definition(int version, int blockGridSide,
                                         ModZoneHostMetadata metadata,
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

    static ModZoneLevelData hostData(ModLevelDefinition definition) {
        try {
            return ModZoneLoader.prepareHostData(definition);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static final class LevelBackedRegistry
            extends com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry {
        private final com.openggf.level.Level level;

        private LevelBackedRegistry(com.openggf.level.Level level) {
            this.level = level;
        }

        @Override
        protected com.openggf.level.Level currentLevel() {
            return level;
        }
    }
}
