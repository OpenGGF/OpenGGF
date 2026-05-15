package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the engine continues to execute objects and advance the
 * canonical level frame step while the title card is on screen.
 *
 * <p>ROM parity:
 * <ul>
 *   <li>S1 {@code Level_TtlCardLoop} (sonic.asm:2766-2794) calls
 *       {@code ExecuteObjects} every iteration of the title-card wait loop.</li>
 *   <li>S2 {@code Level_TtlCard} (s2.asm:4914-4924) calls
 *       {@code RunObjects} every iteration of the title-card wait loop.</li>
 *   <li>S3K title-card wait loop at {@code loc_62CC} (sonic3k.asm:7737-7748)
 *       calls {@code Process_Sprites} every iteration.</li>
 * </ul>
 *
 * <p>Each test loads a real level for the matching game, drives the GameLoop
 * into {@link GameMode#TITLE_CARD}, captures the {@link ObjectManager} frame
 * counter and the {@link LevelManager} frame counter, and then steps a fixed
 * number of frames. Both counters must advance by the number of frames stepped
 * — proving that object updates and the canonical level frame step
 * ({@code LevelFrameStep.execute}) both ran during the title-card phase.
 *
 * <p>Each {@code @Test} method opens its own ROM (and the test is skipped
 * via {@link Assumptions} when that ROM is not available locally). This
 * keeps the three games in one class so each can be enabled or skipped
 * independently.
 */
class TestTitleCardObjectExecution {

    private static final int FRAMES_TO_STEP = 5;

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        SessionManager.clear();
        GameModuleRegistry.reset();
        RomManager.getInstance().setRom(null);
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void titleCardAdvancesObjectAndLevelFrameCounters_s1Ghz1() {
        File romFile = RomTestUtils.ensureSonic1RomAvailable();
        Assumptions.assumeTrue(romFile != null, "Sonic 1 ROM not available — skipping test");
        runTitleCardAdvancementCheck(SonicGame.SONIC_1, romFile, 0, 0, false);
    }

    @Test
    void titleCardAdvancesObjectAndLevelFrameCounters_s2Ehz1() {
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        Assumptions.assumeTrue(romFile != null, "Sonic 2 ROM not available — skipping test");
        runTitleCardAdvancementCheck(SonicGame.SONIC_2, romFile, 0, 0, false);
    }

    @Test
    void titleCardAdvancesObjectAndLevelFrameCounters_s3kAiz1() {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        Assumptions.assumeTrue(romFile != null, "Sonic 3&K ROM not available — skipping test");
        runTitleCardAdvancementCheck(SonicGame.SONIC_3K, romFile, 0, 0, true);
    }

    /**
     * Loads the requested ROM and level, switches the GameLoop into TITLE_CARD,
     * steps N frames, and asserts both the ObjectManager and LevelManager
     * frame counters advanced during the title-card phase.
     */
    private void runTitleCardAdvancementCheck(SonicGame game, File romFile,
                                              int zone, int act,
                                              boolean skipIntros) {
        // 1. Load the requested ROM and configure the matching game module.
        Rom rom = new Rom();
        assertTrue(rom.open(romFile.getAbsolutePath()),
                "ROM file must open: " + romFile.getAbsolutePath());
        TestEnvironment.configureRomFixture(rom);

        if (skipIntros) {
            SonicConfigurationService.getInstance()
                    .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        }

        // 2. Load a real level via the headless fixture (also creates the player).
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(zone, act)
                .build();

        LevelManager levelManager = GameServices.level();
        ObjectManager objectManager = levelManager.getObjectManager();
        assertNotNull(objectManager, "level load should produce an object manager");

        // 3. Build a GameLoop bound to the active gameplay mode, then move it
        //    into TITLE_CARD mode using the production entry point.
        InputHandler inputHandler = new InputHandler();
        GameLoop loop = new GameLoop(inputHandler);
        loop.setGameplayMode(SessionManager.getCurrentGameplayMode());

        // Force the active title card provider into a fresh non-released
        // state so the title card stays locked while we step frames.
        TitleCardProvider provider = GameServices.module().getTitleCardProvider();
        assertNotNull(provider, "game module must expose a title card provider");
        provider.reset();

        loop.enterTitleCard(zone, act);
        assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode(),
                "loop should be in TITLE_CARD mode after enterTitleCard()");

        // 4. Snapshot both counters before stepping any frames.
        int objectFramesBefore = objectManager.getFrameCounter();
        int levelFramesBefore = levelManager.getFrameCounter();

        // 5. Step exactly FRAMES_TO_STEP frames while in TITLE_CARD mode.
        //    The provider's reset()/initialize() above guarantees the
        //    title-card animation starts from its first locked frame, so all
        //    of the requested steps should run inside the locked-phase branch.
        for (int i = 0; i < FRAMES_TO_STEP; i++) {
            assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode(),
                    "loop should remain in TITLE_CARD mode for the duration of the test "
                            + "(frame=" + i + ", game=" + game + ")");
            loop.step();
        }

        // 6. Both counters must advance during the title card.
        //    - ObjectManager.frameCounter: proves ExecuteObjects ran each
        //      frame, matching ROM Level_TtlCard{Loop} / loc_62CC.
        //    - LevelManager.frameCounter: proves the canonical level frame
        //      step ran (parallax, water dynamics, zone features, ring
        //      manager, level gamestate), which the ROM also exercises via
        //      the per-frame VBlank routines that run during the title
        //      card loop.
        int objectFramesAfter = objectManager.getFrameCounter();
        int levelFramesAfter = levelManager.getFrameCounter();

        assertEquals(FRAMES_TO_STEP, objectFramesAfter - objectFramesBefore,
                "ObjectManager.frameCounter must advance by " + FRAMES_TO_STEP
                        + " during the title card (ROM " + game
                        + " calls ExecuteObjects each frame)");
        assertTrue(levelFramesAfter - levelFramesBefore >= FRAMES_TO_STEP,
                "LevelManager.frameCounter must advance by at least " + FRAMES_TO_STEP
                        + " during the title card so parallax, water dynamics, and zone "
                        + "features stay in sync with the ROM (was "
                        + (levelFramesAfter - levelFramesBefore) + ")");
    }
}
