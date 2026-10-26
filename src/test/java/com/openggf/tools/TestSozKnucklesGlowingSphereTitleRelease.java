package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldPlcLifecycleAccess;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance;
import com.openggf.game.sonic3k.resources.S3kKosModuleDescriptor;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.runs.RunLevelLoadCause;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The maintained cold controller prefix reaches its own starpost portal, then
 * exercises the natural $1400 title handoff independently of the long boss route.
 * Native044 Obj_TitleCardInit queues RedAct, S3KZone, Num1 and Bonus; loc_62CC
 * waits for title movement and Nemesis, LoadLevelLoadBlock drains physical terrain,
 * and loc_64DC returns to LevelLoop before its first ordinary Process_Sprites.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozKnucklesGlowingSphereTitleRelease {
    @Test
    void coldPortalDrainsRealTitleWorkBeforeFirstInputDrivenBonusTick() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(
                Path.of("src/test/resources/routes/s3k/soz1-cold-knuckles.bk2"));
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        var drawing = new RouteFrameDrawing("openggf.soz.drawEveryFrame");
        try (var session = new GameplayCaptureSession(settings)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            // This public consumer EXECUTES the production initial object pass.
            assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
            var origin = GameServices.level().getCurrentLevel();
            long originGeneration = GameServices.level().getCompletedProductionLoadGeneration();
            int frame = 0;
            // A safety budget, not an expected portal/release row.
            for (; frame < 4000 && GameServices.level().getCurrentLevel() == origin; frame++) {
                session.step(movie.getFrame(frame));
                drawing.afterStep(session);
                assertFalse(session.player().getDead(), "cold portal approach at " + frame);
            }
            var level = GameServices.level();
            var interior = level.getCurrentLevel();
            assertNotSame(origin, interior, "ordinary controller input enters its real bonus portal");
            assertEquals(0x14, level.getCurrentZone());
            assertEquals(0, level.getCurrentAct());
            assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode());
            long generation = level.getCompletedProductionLoadGeneration();
            assertTrue(generation > originGeneration);
            var world = level.getWorldSession();
            var objects = level.getObjectManager();
            var player = assertInstanceOf(Knuckles.class, session.player());
            assertSame(player, GameServices.sprites().getMainPlayable());
            var bonus = GameServices.bonusStageOrNull();
            assertNotNull(bonus);
            assertEquals(BonusStageType.GLOWING_SPHERE, bonus.getActiveType());
            assertNotNull(bonus.getSavedState());
            assertEquals(0x0800, bonus.getSavedState().savedZoneAndAct());
            var receipt = SessionManager.getCurrentGameplayMode().runLevelLoads().latest().orElseThrow();
            assertEquals(RunLevelLoadCause.ORDINARY, receipt.cause());
            assertEquals(generation, receipt.identity().loadGeneration());
            assertEquals(0x14, receipt.identity().progressionZone());
            assertEquals(level.getRomZoneId(), receipt.identity().romZone());
            assertEquals(0, receipt.identity().act());
            var title = assertInstanceOf(Sonic3kTitleCardManager.class, GameServices.module().getTitleCardProvider());
            var plc = WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class);
            var art = S3kRuntimeArtCoordinator.current();
            var submitted = new LinkedHashMap<HardwareWorkHandle, S3kKosModuleDescriptor>();
            boolean sawArchives = false, sawNemesis = false, sawTerrain = false;
            int entry = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                    + (0x14 * 2) * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;
            var rom = TestEnvironment.currentRom();
            int terrain = rom.read32BitAddr(entry) & 0xFFFFFF;
            assertEquals(terrain, rom.read32BitAddr(entry + 4) & 0xFFFFFF,
                    "$1400 uses one deduplicated terrain archive");
            for (int step = 0; step < 600; step++) {
                assertSame(interior, level.getCurrentLevel());
                assertSame(world, level.getWorldSession());
                assertSame(interior, world.getCurrentLevel());
                assertSame(objects, level.getObjectManager());
                assertEquals(generation, level.getCompletedProductionLoadGeneration());
                var state = title.capture();
                sawNemesis |= plc.isBusy();
                if (state.artLoading()) {
                    sawArchives = true;
                    assertTrue(state.bonusMode());
                    assertEquals(List.of(0, 0x10, 0x3D, 0x4D), state.artDestinations());
                    assertEquals(4, state.artHandles().size());
                }
                for (var handle : GameServices.hardwareTiming().pendingHandles()) {
                    if (handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE)
                        submitted.putIfAbsent(handle, art.moduleQueue().descriptor(handle));
                }
                if (submitted.values().stream().anyMatch(d -> d.sourceAddress() == terrain)) {
                    sawTerrain = true;
                    assertFalse(state.artLoading());
                    assertTrue(state.freshLevelTitleReady());
                    assertTrue(state.freshLevelTerrainStarted());
                    assertFalse(plc.isBusy(), "Nemesis finishes before terrain submission");
                }
                if (session.loop().getCurrentGameMode() == GameMode.BONUS_STAGE) break;
                assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode());
                assertTrue(player.isControlLocked());
                assertEquals(0, level.getFrameCounter(), "locked title runs no ordinary level body");
                // Hold ordinary controller Left through the actual release, without
                // choosing a release row or writing any gameplay state.
                session.step(new Bk2FrameInput(frame++, AbstractPlayableSprite.INPUT_LEFT, 0, false, "L"));
                drawing.afterStep(session);
            }
            assertTrue(sawArchives && sawNemesis && sawTerrain, "all real release workloads observed");
            assertEquals(List.of(Sonic3kConstants.ART_KOSM_TITLE_CARD_RED_ACT_ADDR,
                            Sonic3kConstants.ART_KOSM_TITLE_CARD_S3K_ZONE_ADDR,
                            Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM1_ADDR,
                            Sonic3kConstants.ART_KOSM_BONUS_TITLE_CARD_ADDR, terrain),
                    submitted.values().stream().map(S3kKosModuleDescriptor::sourceAddress).toList());
            assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode());
            assertFalse(art.freshLevelArtWaitsForModuleQueue(), "physical terrain FIFO drains before release");
            assertFalse(plc.isBusy());
            assertFalse(player.isControlLocked());
            assertEquals(0, level.getFrameCounter(), "release iteration is setup, not ordinary bonus gameplay");
            assertEquals(0, GameServices.sprites().getFrameCounter());
            var trap = objects.activeObjectsOfType(PachinkoEnergyTrapObjectInstance.class).getFirst();
            int updates = trap.getUpdateCount();
            session.step(new Bk2FrameInput(frame, AbstractPlayableSprite.INPUT_LEFT, 0, false, "L"));
            drawing.afterStep(session);
            assertSame(interior, level.getCurrentLevel());
            assertSame(world, level.getWorldSession());
            assertSame(objects, level.getObjectManager());
            assertSame(player, session.player());
            assertSame(player, GameServices.sprites().getMainPlayable());
            assertEquals(generation, level.getCompletedProductionLoadGeneration());
            assertEquals(receipt, SessionManager.getCurrentGameplayMode().runLevelLoads().latest().orElseThrow());
            assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode());
            assertEquals(1, level.getFrameCounter(), "next ordinary tick advances Level_frame_counter once");
            assertEquals(1, GameServices.sprites().getFrameCounter());
            assertEquals(updates + 1, trap.getUpdateCount(), "real bonus object executes once");
            assertEquals(objects.getVblaCounter(), trap.getLastUpdateFrameCounter(),
                    "the trap reads V_int_run_count, independently of Level_frame_counter");
            assertEquals(AbstractPlayableSprite.INPUT_LEFT,
                    session.loop().getInputHandler().logical().player1().heldMask());
            assertTrue(player.isLeftPressed(), "ordinary input reaches the released playable owner");
            assertFalse(player.isRightPressed());
            assertTrue(player.getXSpeed() < 0, "held Left drives actual bonus player physics");
            assertFalse(player.getDead());
            System.out.println("SOZ1 Knuckles natural Glowing Sphere: first ordinary input=" + frame
                    + ", generation=" + generation + ", title/terrain parents=" + submitted.size());
        }
    }
}
