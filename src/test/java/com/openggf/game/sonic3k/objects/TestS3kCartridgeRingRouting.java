package com.openggf.game.sonic3k.objects;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageType;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kCartridgePolicy;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static com.openggf.game.BonusStageType.*;
import static org.junit.jupiter.api.Assertions.*;

/** Declared local contacts with the real MHZ and LRZ2 worlds, object slots and transition owner. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCartridgeRingRouting {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void checkpointStarsAndProviderSelectTheCartridgeBonusCycle(boolean standalone) throws Exception {
        try (var boot = new HeadlessGameBoot(320, 224)) {
            boot(boot, standalone, 7, 0);
            BonusStageType[] expectedCycle = standalone
                    ? new BonusStageType[]{SLOT_MACHINE, GLOWING_SPHERE}
                    : new BonusStageType[]{SLOT_MACHINE, GLOWING_SPHERE, GUMBALL};
            assertAll(
                    () -> assertProviderCycle(GameServices.module().getBonusStageProvider(), expectedCycle),
                    () -> assertPortalCycle(GameServices.level(), expectedCycle));
        }
    }

    private static void assertProviderCycle(BonusStageProvider provider, BonusStageType[] expectedCycle) {
        assertEquals(NONE, provider.selectBonusStage(19));
        for (int band = 0; band < 6; band++) {
            BonusStageType expected = expectedCycle[band % expectedCycle.length];
            int firstRing = 20 + 15 * band;
            for (int rings = firstRing; rings < firstRing + 15; rings++) {
                assertEquals(expected, provider.selectBonusStage(rings), "provider at " + rings + " rings");
            }
        }
    }

    private static void assertPortalCycle(LevelManager level, BonusStageType[] expectedCycle) throws Exception {
        var objects = level.getObjectManager();
        var player = GameServices.sprites().getMainPlayable();
        int x = player.getCentreX();
        int y = player.getCentreY();
        var starX = Sonic3kStarPostBonusStarChild.class.getDeclaredField("currentX");
        var starY = Sonic3kStarPostBonusStarChild.class.getDeclaredField("currentY");
        starX.setAccessible(true);
        starY.setAccessible(true);
        for (int band = 0; band < 6; band++) {
            ((CheckpointState) level.getCheckpointState()).clear();
            int rings = 20 + 15 * band;
            player.setRingCount(rings);
            int checkpoint = 100 + band;
            var post = objects.createDynamicObject(() -> new Sonic3kStarPostObjectInstance(
                    new ObjectSpawn(x, y, 0x34, checkpoint, 0, false, 9999)));
            int clock = objects.getVblaCounter();
            post.update(clock, player);
            var stars = objects.activeObjectsOfType(Sonic3kStarPostBonusStarChild.class);
            assertEquals(4, stars.size(), "bonus stars at " + rings + " rings");
            var star = stars.getFirst();
            for (int frame = 0; frame < 128; frame++) {
                star.update(clock + frame, null);
            }
            NativePositionOps.writeXPosPreserveSubpixel(player, starX.getInt(star));
            NativePositionOps.writeYPosPreserveSubpixel(player, starY.getInt(star));
            star.update(clock + 128, player);
            assertEquals(expectedCycle[band % expectedCycle.length], level.consumeBonusStageRequest(),
                    "checkpoint portal at " + rings + " rings");
            stars.forEach(objects::removeDynamicObject);
            objects.activeObjectsOfType(Sonic3kStarPostStarChild.class).forEach(objects::removeDynamicObject);
            objects.removeDynamicObject(post);
            NativePositionOps.writeXPosPreserveSubpixel(player, x);
            NativePositionOps.writeYPosPreserveSubpixel(player, y);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryGiantRingWithSevenChaosEmeralds(boolean standalone) throws Exception {
        try (var boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot(boot, standalone, 9, 1);
            var level = GameServices.level();
            level.skipPendingInitialTitleCardPresentation();
            var state = GameServices.gameState();
            state.restoreS3kEmeraldProgress(Collections.nCopies(7, 1), false);
            for (int frame = 0; frame < 12; frame++) step(loop);
            var player = GameServices.sprites().getMainPlayable();
            var objects = level.getObjectManager();
            var spawn = level.getCurrentLevel().getObjects().stream()
                    .filter(s -> s.objectId() == 0x85 && s.subtype() == 9)
                    .findFirst().orElseThrow();
            int approachX = spawn.x() - 40;
            int approachY = spawn.y() - 8;
            int y = player.getCentreY();
            place(player, approachX, y);
            step(loop);
            while (y != approachY) {
                y += Math.clamp(approachY - y, -0x80, 0x80);
                place(player, approachX, y);
                step(loop);
            }
            for (int frame = 0; frame < 63; frame++) {
                place(player, approachX, approachY);
                step(loop);
            }
            var ring = objects.activeObjectsOfType(Sonic3kSSEntryRingObjectInstance.class).stream()
                    .filter(o -> o.getSpawn().equals(spawn))
                    .findFirst().orElseThrow();
            assertTrue(ring.getMappingFrame() >= 8, "ring must finish forming before contact");
            assertFalse(player.isObjectControlled(), "player is outside the ring's touch range");
            int ringsBefore = player.getRingCount();
            place(player, spawn.x() + 8, spawn.y() - 4);
            step(loop);
            assertAll(
                    () -> assertEquals(ringsBefore + (standalone ? 50 : 0), player.getRingCount(), "ring payout"),
                    () -> assertEquals(standalone, state.isSpecialRingCollected(spawn.subtype()), "collection bit"),
                    () -> assertEquals(!standalone, ring.isSuperEmeraldRing(), "Super Emerald ring palette"),
                    () -> assertEquals(!standalone, player.isObjectControlled(), "capture locks player"),
                    () -> assertEquals(standalone, objects.activeObjectsOfType(
                            Sonic3kSSEntryFlashObjectInstance.class).isEmpty(), "capture flash"));
        }
    }

    private static void place(AbstractPlayableSprite player, int x, int y) {
        NativePositionOps.writeXPosPreserveSubpixel(player, x);
        NativePositionOps.writeYPosPreserveSubpixel(player, y);
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setAir(true);
        player.setJumping(false);
        GameServices.camera().updatePosition(true);
    }

    private static void step(GameLoop loop) {
        loop.step();
        GameServices.graphics().runPendingRenderThreadTasks();
    }

    private static GameLoop boot(HeadlessGameBoot boot, boolean standalone, int zone, int act) throws Exception {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "none");
        var path = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        try (var image = new Rom()) {
            assertTrue(image.open(path.toString()));
            var reader = RomByteReader.fromRom(image);
            if (standalone) reader = reader.window(0, 0x200000);
            var loop = boot.boot(reader, standalone ? "original SK window" : "locked-on S3K", zone, act);
            assertEquals(standalone, Sonic3kCartridgePolicy.isStandaloneSk());
            assertEquals(zone, GameServices.level().getCurrentZone());
            assertEquals(act, GameServices.level().getCurrentAct());
            return loop;
        }
    }
}
