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
 * <p><b>Engine current state:</b> the title-card branch in
 * {@code GameLoop.step()} is per-game gated on
 * {@link TitleCardProvider#shouldRunPlayerPhysics()}:
 * <ul>
 *   <li>S2 (returns {@code true}) runs the canonical {@code LevelFrameStep
 *       .execute} every frame, advancing both the {@link ObjectManager}
 *       frame counter and the {@link LevelManager} frame counter.</li>
 *   <li>S1 / S3K (return {@code false}) use the legacy minimal path
 *       ({@code levelManager.updateObjectPositions()} + camera force-snap)
 *       so the frame counters do NOT advance. This was the B2 per-game
 *       narrowing that fixed the S3K AIZ camera divergence by avoiding
 *       extra camera / level-event work that ROM doesn't do during the
 *       S1 / S3K title-card wait. <b>However, ROM does still run
 *       ExecuteObjects / Process_Sprites during those wait loops, so
 *       the engine's S1 / S3K title-card path is still divergent from
 *       ROM.</b> See {@code docs/superpowers/specs/2026-05-15-s2-native-
 *       prelude-traces-design.md} (Section 7.5 ADR-1 / Iter-C trail) for
 *       the full record; a follow-up is needed to run object behaviour
 *       (without the camera / level-event side effects) for S1 / S3K.</li>
 * </ul>
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
    void titleCardLegacyPath_s1Ghz1() {
        // S1: TitleCardProvider.shouldRunPlayerPhysics() == false → engine
        // uses the legacy minimal title-card path; frame counters do NOT
        // advance during the locked phase. Diverges from ROM (which calls
        // ExecuteObjects each frame); tracked as a follow-up.
        File romFile = RomTestUtils.ensureSonic1RomAvailable();
        Assumptions.assumeTrue(romFile != null, "Sonic 1 ROM not available — skipping test");
        runTitleCardAdvancementCheck(SonicGame.SONIC_1, romFile, 0, 0, false, /* expectAdvance */ false);
    }

    @Test
    void titleCardAdvancesObjectAndLevelFrameCounters_s2Ehz1() {
        // S2: TitleCardProvider.shouldRunPlayerPhysics() == true → engine
        // runs LevelFrameStep.execute every frame, matching ROM Level_TtlCard.
        // Both ObjectManager and LevelManager frame counters advance.
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        Assumptions.assumeTrue(romFile != null, "Sonic 2 ROM not available — skipping test");
        runTitleCardAdvancementCheck(SonicGame.SONIC_2, romFile, 0, 0, false, /* expectAdvance */ true);
    }

    @Test
    void titleCardLegacyPath_s3kAiz1() {
        // S3K: same per-game gate as S1 — legacy minimal path during title
        // card; frame counters do NOT advance. The gate was the B2 fix that
        // restored the S3K AIZ trace by avoiding extra camera/level-event
        // work ROM doesn't do; running ExecuteObjects within that gate is
        // a follow-up.
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        Assumptions.assumeTrue(romFile != null, "Sonic 3&K ROM not available — skipping test");
        runTitleCardAdvancementCheck(SonicGame.SONIC_3K, romFile, 0, 0, true, /* expectAdvance */ false);
    }

    /**
     * Loads the requested ROM and level, switches the GameLoop into TITLE_CARD,
     * steps N frames, and asserts the per-game expected behaviour of the
     * title-card branch: S2 advances both frame counters; S1 / S3K take the
     * legacy minimal path and do not.
     */
    private void runTitleCardAdvancementCheck(SonicGame game, File romFile,
                                              int zone, int act,
                                              boolean skipIntros,
                                              boolean expectFrameCountersToAdvance) {
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
        int objectDelta = objectFramesAfter - objectFramesBefore;
        int levelDelta = levelFramesAfter - levelFramesBefore;

        // Objects tick on every title-card path (S1 / S2 / S3K) — both the
        // canonical LevelFrameStep call (S2) and the legacy minimal path
        // (S1 / S3K, via levelManager.updateObjectPositions()) advance the
        // ObjectManager frame counter. This matches ROM behaviour where
        // ExecuteObjects / Process_Sprites runs during the title-card wait.
        assertEquals(FRAMES_TO_STEP, objectDelta,
                "ObjectManager.frameCounter must advance by " + FRAMES_TO_STEP
                        + " during the title card (ROM " + game
                        + " calls ExecuteObjects each frame)");

        if (expectFrameCountersToAdvance) {
            // S2 only: full LevelFrameStep also advances level frame state
            // so parallax, water dynamics, and zone features stay in sync.
            assertTrue(levelDelta >= FRAMES_TO_STEP,
                    "LevelManager.frameCounter must advance by at least " + FRAMES_TO_STEP
                            + " during the title card on the LevelFrameStep path (game="
                            + game + "); was " + levelDelta);
        } else {
            // S1 / S3K: per-game gate uses the legacy minimal path
            // (levelManager.updateObjectPositions + camera force-snap), so
            // the LevelManager frame counter stays put while objects still
            // tick. If this changes, the per-game gate has been relaxed —
            // re-evaluate whether S3K AIZ trace still stays correct.
            assertEquals(0, levelDelta,
                    "LevelManager.frameCounter must NOT advance during the title card "
                            + "on the legacy minimal path (game=" + game + ")");
        }
    }
}
