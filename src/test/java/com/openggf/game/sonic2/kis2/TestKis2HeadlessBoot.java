package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.PatchEnablement;
import com.openggf.game.session.BuiltInPatches;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.game.CanonicalAnimation;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * HeadlessGameBoot integration: Sonic 2 with Knuckles requested resolves the
 * KiS2 module, runs the KiS2 physics, spawns Knuckles alone under the
 * faithful roster, honours a configured sidekick, and loads the KiS2 EHZ1
 * layout and palette. Skips without the S2 and S3K ROMs.
 */
class TestKis2HeadlessBoot {

    @TempDir
    Path tempDir;

    private EngineContext previous;

    @BeforeEach
    void resetState() {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
    }

    @AfterEach
    void cleanup() {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previous != null) {
            EngineServices.configure(previous);
        }
        TestEnvironment.resetAll();
    }

    @Test
    void knucklesOnSonic2BootsThePatchedModuleAloneWithKis2PhysicsAndLayouts() throws Exception {
        File s2File = RomTestUtils.ensureSonic2RomAvailable();
        File s3kFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(s2File != null && s3kFile != null, "KiS2 headless boot needs the S2 and S3K ROMs");
        byte[] s3kBytes = Files.readAllBytes(s3kFile.toPath());

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(s2File.getAbsolutePath()), "Configured S2 ROM must be readable");
            SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            EngineContext injected = injectedContext(config, s3kBytes);
            EngineServices.configure(injected);
            injected.roms().setRom(rom);
            Sonic2GameModule root = new Sonic2GameModule();

            GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(injected, root);
            GameplaySessionFactory.attachManagers(gameplay, injected);
            injected.graphics().initHeadless();

            GameModule module = SessionManager.requireCurrentGameModule();
            assertInstanceOf(DelegatingGameModule.class, module);
            assertEquals("kis2", ((DelegatingGameModule) module).patchId());
            assertInstanceOf(Kis2PhysicsProvider.class, module.getPhysicsProvider());

            var team = GameplayTeamBootstrap.registerActiveTeam(module, GameServices.sprites(), config);
            assertInstanceOf(Knuckles.class, team.mainSprite());
            assertEquals("knuckles", team.mainSprite().getCode());
            assertTrue(team.sidekicks().isEmpty(), "faithful roster: Knuckles alone");

            GameServices.camera().setFocusedSprite(team.mainSprite());
            GameServices.level().loadZoneAndAct(0, 0);
            GroundSensor.setLevelManager(GameServices.level());
            // Playables bind their runtime state to the session module on their
            // first runtime access (tickStatus / resolveAnimationId), not at
            // construction; the KiS2 profile must be what that binding yields.
            team.mainSprite().resolveAnimationId(CanonicalAnimation.WAIT);
            assertEquals(Kis2Physics.KNUCKLES, team.mainSprite().getPhysicsProfile());

            Level level = GameServices.level().getCurrentLevel();
            assertEquals(157, level.getObjects().size(), "EHZ1 uses the KiS2 layout (BRANCH_DIFFS.md)");
            var skWindow = LogicalRomResolver.windowSkFromCombined(s3kBytes);
            Palette expected = new Kis2PlayerArt(skWindow,
                    com.openggf.game.BuiltInRomDetectors.forGame(com.openggf.game.GameId.S3K)
                            .createModule().getCrossGameDonorProvider().createPlayerArtProvider(skWindow))
                    .loadKnucklesPalette();
            Palette actual = level.getPalette(0);
            for (int i = 0; i < 16; i++) {
                assertEquals(expected.getColor(i).r, actual.getColor(i).r, "palette 0 index " + i + " r");
                assertEquals(expected.getColor(i).g, actual.getColor(i).g, "palette 0 index " + i + " g");
                assertEquals(expected.getColor(i).b, actual.getColor(i).b, "palette 0 index " + i + " b");
            }
        }
    }

    @Test
    void configuredSidekickIsStillHonouredAsADocumentedDivergence() throws Exception {
        File s2File = RomTestUtils.ensureSonic2RomAvailable();
        File s3kFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(s2File != null && s3kFile != null, "KiS2 headless boot needs the S2 and S3K ROMs");
        byte[] s3kBytes = Files.readAllBytes(s3kFile.toPath());

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(s2File.getAbsolutePath()), "Configured S2 ROM must be readable");
            SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            EngineContext injected = injectedContext(config, s3kBytes);
            EngineServices.configure(injected);
            injected.roms().setRom(rom);

            GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(
                    injected, new Sonic2GameModule());
            GameplaySessionFactory.attachManagers(gameplay, injected);
            injected.graphics().initHeadless();

            GameModule module = SessionManager.requireCurrentGameModule();
            assertEquals("kis2", ((DelegatingGameModule) module).patchId());
            var team = GameplayTeamBootstrap.registerActiveTeam(module, GameServices.sprites(), config);
            assertInstanceOf(Knuckles.class, team.mainSprite());
            assertEquals(1, team.sidekicks().size());
            assertEquals("tails_p2", team.sidekicks().getFirst().getCode());
        }
    }

    /**
     * Tier two: the catalogue-backed resolver serves {@code KIS2} from the
     * user-supplied dump, so Casino Night loads the chip layout, line 0 is the
     * chip's {@code Pal_BGND}, the underwater palette is the chip's
     * {@code Pal_CPZ_U} and the level art carries the chip patches.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {320, 426})
    void withTheLockOnDumpTheBootRunsTierTwoWithChipLayoutsPalettesAndArt(int width) throws Exception {
        File s2File = RomTestUtils.ensureSonic2RomAvailable();
        File s3kFile = RomTestUtils.ensureSonic3kRomAvailable();
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(s2File != null && s3kFile != null && dump != null,
                "KiS2 tier-two boot needs the S2 and S3K ROMs and the lock-on dump");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(s2File.getAbsolutePath()), "Configured S2 ROM must be readable");
            SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
            config.setConfigValue(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            EngineContext injected = catalogueBackedContext(config);
            EngineServices.configure(injected);
            injected.roms().setRom(rom);
            assumeTrue(injected.roms().resolveLogicalRom(com.openggf.data.RomIdentity.KIS2).isPresent(),
                    "the isolated ROM manager must see the lock-on dump");

            GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(
                    injected, new Sonic2GameModule());
            GameplaySessionFactory.attachManagers(gameplay, injected);
            injected.graphics().initHeadless();

            GameModule module = SessionManager.requireCurrentGameModule();
            assertEquals("kis2", ((DelegatingGameModule) module).patchId());
            assertEquals(Kis2GameModule.Fidelity.TIER_TWO, ((Kis2GameModule) module).fidelity());

            var team = GameplayTeamBootstrap.registerActiveTeam(module, GameServices.sprites(), config);
            GameServices.camera().setFocusedSprite(team.mainSprite());
            GameServices.level().loadZoneAndAct(3, 0); // Sonic2ZoneRegistry index 3 = Casino Night
            GroundSensor.setLevelManager(GameServices.level());

            Level level = GameServices.level().getCurrentLevel();
            assertEquals(292, level.getObjects().size(), "CNZ1 uses the chip layout (BRANCH_DIFFS.md)");

            var chip = new Kis2ChipArt(LockOnAddressSpace.tierTwo(
                    LogicalRomResolver.windowSkFromCombined(Files.readAllBytes(s3kFile.toPath())),
                    com.openggf.data.RomByteReader.fromBytes(Files.readAllBytes(s2File.toPath())), dump));
            assertTrue(level.getPalette(0).dataEquals(chip.backgroundPaletteLine0()), "line 0 is Pal_BGND");
            Palette[] cpz = module.getWaterDataProvider().getUnderwaterPalette(rom,
                    com.openggf.game.sonic2.scroll.Sonic2ZoneConstants.ROM_ZONE_CPZ, 1,
                    com.openggf.game.PlayerCharacter.KNUCKLES);
            assertTrue(cpz[0].dataEquals(chip.chemicalPlantUnderwaterPalette()[0]), "Pal_CPZ_U from the chip");

            var monitor = module.getObjectArtProvider().getSheet(com.openggf.level.objects.ObjectArtKeys.MONITOR);
            var patch = chip.load(Kis2ChipArt.Asset.POWERUPS_PATCH);
            for (int i = 0; i < patch.length; i++) {
                for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                    for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                        assertEquals(patch[i].getPixel(x, y),
                                monitor.getPatterns()[Kis2Constants.POWERUPS_KNUCKLES_PATCH_TILE + i].getPixel(x, y),
                                "monitor patch tile " + i);
                    }
                }
            }
            assertInstanceOf(com.openggf.game.sonic2.continuescreen.Sonic2ContinueScreenProvider.class,
                    module.createContinueScreenProvider());
            assertEquals(11, module.getObjectArtProvider().getSheet(
                    com.openggf.level.objects.ObjectArtKeys.RESULTS).getFrame(0).pieces().size(),
                    "the actual registered results sheet says KNUCKLES GOT");
            var superController = assertInstanceOf(Kis2SuperStateController.class,
                    module.createSuperStateController(team.mainSprite()));
            team.mainSprite().setSuperStateController(superController);
            team.mainSprite().setRingCount(50);
            assertTrue(!superController.activateFromAirAbility(), "emerald gate remains required");
            for (int i = 0; i < 7; i++) GameServices.gameState().markEmeraldCollected(i);
            assertTrue(superController.activateFromAirAbility());
            assertEquals(Kis2Physics.SUPER_KNUCKLES, team.mainSprite().getPhysicsProfile());
            superController.update();
            assertEquals(50, team.mainSprite().getRingCount(), "no immediate ring drain in KiS2");
            for (int i = 1; i < 20; i++) superController.update();
            var paletteRegistry = GameServices.paletteOwnershipRegistryOrNull();
            if (paletteRegistry != null) {
                Palette[] normal = new Palette[level.getPaletteCount()];
                for (int i = 0; i < normal.length; i++) normal[i] = level.getPalette(i);
                paletteRegistry.resolveInto(normal, null, GameServices.graphics(), normal[0]);
            }
            assertEquals(0x428, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                    level.getPalette(0).getColor(2)), "chip cycle reaches the host palette through its owner");
            superController.debugDeactivate();
            superController.update();

            assertInstanceOf(Kis2TitleScreen.class, module.getTitleScreenProvider());
            assertTrue(module.getTitleScreenProvider() == module.getTitleScreenProvider());
            var special = assertInstanceOf(Kis2SpecialStageProvider.class, module.getSpecialStageProvider());
            assertTrue(special == module.getSpecialStageProvider(), "provider is session stable");
            assertTrue(special.getManager() == module.getGameService(
                    com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager.class));
            assertTrue(special.getManager().getDebugSprites() == module.getGameService(
                    com.openggf.game.sonic2.debug.Sonic2SpecialStageSpriteDebug.class));
            var debug = module.getDebugModeProvider().getSpecialStageDebugController();
            debug.toggleAlignmentTestMode();
            assertTrue(special.getManager().isAlignmentTestMode(), "debug commands target the live patched manager");
            debug.toggleAlignmentTestMode();
            for (int stage = 0; stage < 7; stage++) {
                special.initializeStage(stage);
                assertInstanceOf(Kis2SpecialStageDataLoader.class, special.getManager().getDataLoader());
                assertEquals(stage, special.getManager().getCurrentStage());
                replaySpecialStageWindow(special, special.rewindAdapter().orElseThrow());
            }

        }
    }

    private static <T> void replaySpecialStageWindow(Kis2SpecialStageProvider provider,
            com.openggf.game.rewind.RewindSnapshottable<T> adapter) {
        // A short independent window in each auxiliary route exercises the
        // actual manager and registered rewind owner without a long playthrough.
        for (int i = 0; i < 160; i++) provider.update();
        T before = adapter.capture();
        for (int i = 0; i < 12; i++) provider.update();
        var after = provider.getManager().captureComparisonState();
        adapter.restore(before);
        for (int i = 0; i < 12; i++) provider.update();
        assertEquals(after, provider.getManager().captureComparisonState());
    }

    private static EngineContext injectedContext(SonicConfigurationService config, byte[] s3kBytes)
            throws Exception {
        EngineContext old = EngineServices.current();
        ModuleResolutionService resolver = new ModuleResolutionService(BuiltInPatches.registrations(),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> s3kBytes), config);
        return new EngineContext(config, old.graphics(), old.audio(), isolatedRomManager(),
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(),
                old.crossGameFeatures(), resolver);
    }

    /** Resolves logical ROMs through the isolated manager's catalogue, as the engine does. */
    private static EngineContext catalogueBackedContext(SonicConfigurationService config) throws Exception {
        EngineContext old = EngineServices.current();
        RomManager roms = isolatedRomManager();
        ModuleResolutionService resolver = new ModuleResolutionService(BuiltInPatches.registrations(),
                PatchEnablement.ALL_ENABLED, LogicalRomResolver.fromRomManager(roms), config);
        return new EngineContext(config, old.graphics(), old.audio(), roms,
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(),
                old.crossGameFeatures(), resolver);
    }

    private static RomManager isolatedRomManager() throws Exception {
        var constructor = RomManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

}
