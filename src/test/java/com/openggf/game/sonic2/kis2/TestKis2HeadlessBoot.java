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
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.Level;
import com.openggf.level.Palette;
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
            Palette expected = new Kis2PlayerArt(LogicalRomResolver.windowSkFromCombined(s3kBytes))
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

    private static EngineContext injectedContext(SonicConfigurationService config, byte[] s3kBytes)
            throws Exception {
        EngineContext old = EngineServices.current();
        ModuleResolutionService resolver = new ModuleResolutionService(EngineContext.builtInPatches(),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> s3kBytes), config);
        return new EngineContext(config, old.graphics(), old.audio(), isolatedRomManager(),
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(),
                old.crossGameFeatures(), resolver);
    }

    private static RomManager isolatedRomManager() throws Exception {
        var constructor = RomManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

}
