package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.Sonic3kSSEntryFlashObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kSSEntryRingObjectInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent local MHZ1 forced-ring → sanctuary → Saved2 origin lifecycle.
 * Physical contacts are declared scene setup; this is not a cold route claim.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSanctuaryOriginReturnLifecycle {
    private final CanonicalEntryMatrixSupport origin = support("MHZ1", 7, 0);
    private final CanonicalEntryMatrixSupport hub = support("HPZ-sanctuary", 0x17, 1);
    private static final Bk2FrameInput NEUTRAL = new Bk2FrameInput(0, 0, 0, false, "");

    @BeforeEach void saveConfiguration() { origin.saveConfiguration(); hub.saveConfiguration(); }
    @AfterEach void restoreConfiguration() { origin.restoreConfiguration(); }

    private static CanonicalEntryMatrixSupport support(String label, int zone, int act) {
        return new CanonicalEntryMatrixSupport() {
            @Override TestS3kCanonicalEntryLifecycle.Act binding() {
                return new TestS3kCanonicalEntryLifecycle.Act(label, zone, act);
            }
        };
    }

    @Test
    void actualPlacedForcedRingAndExitPadRestoreSaved2OriginAndIsolateBothTimelines()
            throws Exception {
        var row = new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", "");
        var fixture = origin.boot(row);
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(0, 0, 0, 0, 0, 0, 0), false);
        var input = new InputHandler();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        int lives = GameServices.gameState().getLives();
        try {
            var sourceObjects = GameServices.level().getObjectManager();
            var ringSpawn = GameServices.level().getCurrentLevel().getObjects().stream()
                    .filter(spawn -> spawn.objectId() == 0x85 && spawn.subtype() == 0x81)
                    .findFirst().orElseThrow();
            // ROM MHZ Object Pos/1.bin offset30 is the actual forced sanctuary inlet.
            assertEquals(448, ringSpawn.x());
            assertEquals(1664, ringSpawn.y());
            tick(loop, lives, 8);
            var main = GameServices.sprites().getMainPlayable();
            var jump = new Bk2FrameInput(0, AbstractPlayableSprite.INPUT_JUMP, 1, false, "");
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(jump, NEUTRAL));
            tick(loop, lives, 1);
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, jump));
            tick(loop, lives, 1);

            // Earn a real placed ring before entry; Saved2 receives no synthetic count.
            var stageRing = GameServices.level().getCurrentLevel().getRings().stream()
                    .min(java.util.Comparator.comparingInt(spawn ->
                            Math.abs(spawn.x() - ringSpawn.x()) + Math.abs(spawn.y() - ringSpawn.y())))
                    .orElseThrow();
            int beforeRings = main.getRingCount();
            placeForLocalContact(main, stageRing.x(), stageRing.y());
            for (int frame = 0; frame < 8 && main.getRingCount() == beforeRings; frame++) {
                tick(loop, lives, 1);
            }
            assertTrue(main.getRingCount() > beforeRings, "actual placed ring pickup before Saved2");

            // Stay outside the ring's +/-24 horizontal touch box during native formation.
            placeForLocalContact(main, ringSpawn.x() - 40, ringSpawn.y() - 8);
            Sonic3kSSEntryRingObjectInstance ring = null;
            for (int frame = 0; frame < 96; frame++) {
                tick(loop, lives, 1);
                ring = sourceObjects.activeObjectsOfType(Sonic3kSSEntryRingObjectInstance.class).stream()
                        .filter(object -> object.getSpawn().equals(ringSpawn)).findFirst().orElse(null);
                if (ring != null && ringMapping(ring) >= 8) break;
            }
            assertNotNull(ring, "actual placed $85 admission");
            assertTrue(ringMapping(ring) >= 8, "native formation reached its collision frame");
            assertFalse(main.isObjectControlled(), "entry has not been seeded");
            assertFalse(GameServices.level().hasBigRingReturn());
            placeForLocalContact(main, ringSpawn.x() + 8, ringSpawn.y() - 4);
            tick(loop, lives, 1);
            assertTrue(main.isObjectControlled(), "real giant-ring touch owns control");
            assertTrue(main.isHidden(), "real capture owns hidden presentation");
            assertEquals(1, sourceObjects.activeObjectsOfType(Sonic3kSSEntryFlashObjectInstance.class).size());
            int sourceHistory = fixture.gameplayMode().getRewindController().currentFrame();
            assertTrue(sourceHistory > 10);
            for (int frame = 0; frame < 320 && GameServices.level().getObjectManager() == sourceObjects; frame++) {
                tick(loop, lives, 1);
                sourceHistory = Math.max(sourceHistory, fixture.gameplayMode().getRewindController().currentFrame());
            }
            assertNotSame(sourceObjects, GameServices.level().getObjectManager(), "actual flash destination load");
            var saved = GameServices.level().getBigRingReturn();
            assertNotNull(saved, "flash Save_Level_Data2 survives hub assembly");
            assertEquals(ringSpawn.x(), saved.playerX());
            assertEquals(ringSpawn.y(), saved.playerY(), "Save_Level_Data2 owns flash/ring position");
            assertEquals(7, saved.originZone());
            assertEquals(0, saved.originAct());
            assertTrue(saved.rings() > 0);
            waitForReleasedWorld(loop, lives, 0x17, 1);
            hub.assertOwners(row);
            assertTrue(GameServices.level().getLevelGamestate().isTimerPaused());
            assertTrue(fixture.gameplayMode().getRewindController().currentFrame() < sourceHistory,
                    "actual hub load retires outgoing origin history");
            for (int frame = 0; frame < 220 && GameServices.sprites().getMainPlayable().isObjectControlled(); frame++) {
                tick(loop, lives, 1);
            }
            assertFalse(GameServices.sprites().getMainPlayable().isObjectControlled());
            replayTwice(loop, fixture, hub, row, lives);
            assertEquals(saved, GameServices.level().getBigRingReturn(), "hub replay preserves Saved2");

            var hubObjects = GameServices.level().getObjectManager();
            main = GameServices.sprites().getMainPlayable();
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(new Bk2FrameInput(0,
                    AbstractPlayableSprite.INPUT_RIGHT, 0, false, ""), NEUTRAL));
            for (int frame = 0; frame < 64 && main.getCentreX() < 0x1640 + 24; frame++) {
                tick(loop, lives, 1);
            }
            assertTrue(main.getCentreX() >= 0x1640 + 24, "ordinary movement arms native exit band");
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(new Bk2FrameInput(0,
                    AbstractPlayableSprite.INPUT_LEFT, 0, false, ""), NEUTRAL));
            int hubHistory = fixture.gameplayMode().getRewindController().currentFrame();
            for (int frame = 0; frame < 320 && GameServices.level().getObjectManager() == hubObjects; frame++) {
                tick(loop, lives, 1);
                hubHistory = Math.max(hubHistory, fixture.gameplayMode().getRewindController().currentFrame());
                if (GameServices.level().isLevelInactiveForTransition()) {
                    // The actual exit owner has published; destination gets
                    // neutral input rather than another walking command.
                    input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
                }
            }
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
            assertNotSame(hubObjects, GameServices.level().getObjectManager(), "real pad/exit band loads origin");
            waitForReleasedWorld(loop, lives, 7, 0);
            origin.assertOwners(row);
            assertFalse(GameServices.level().hasBigRingReturn(), "Saved2 consumed only after destination restore");
            assertEquals(saved.rings(), GameServices.sprites().getMainPlayable().getRingCount());
            assertEquals(saved.playerX(), GameServices.sprites().getMainPlayable().getCentreX(),
                    "actual released Saved2 origin position");
            assertFalse(GameServices.level().getLevelGamestate().isTimerPaused(), "origin clock resumes");
            assertTrue(GameServices.level().getLevelGamestate().getTimerFrames()
                    >= Math.max(0, saved.timerFrames() / 60 * 60 - 1), "native Saved2 second/frame bank restored");
            assertTrue(GameServices.camera().isLevelStarted(), "ordinary origin HUD owner resumes");
            assertTrue(fixture.gameplayMode().getRewindController().currentFrame() < hubHistory,
                    "origin reload retires outgoing hub history");
            replayTwice(loop, fixture, origin, row, lives);
        } finally {
            loop.closePresence();
        }
    }

    private static int ringMapping(Sonic3kSSEntryRingObjectInstance ring) throws Exception {
        var method = Sonic3kSSEntryRingObjectInstance.class.getDeclaredMethod("getMappingFrame");
        method.setAccessible(true);
        return (int) method.invoke(ring);
    }

    private static void placeForLocalContact(AbstractPlayableSprite main, int x, int y) {
        NativePositionOps.writeXPosPreserveSubpixel(main, x);
        NativePositionOps.writeYPosPreserveSubpixel(main, y);
        main.setXSpeed((short) 0);
        main.setGSpeed((short) 0);
        main.setYSpeed((short) 0);
        main.setAir(true);
        main.setJumping(false);
        GameServices.camera().updatePosition(true);
    }

    private static void tick(GameLoop loop, int lives, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            loop.step();
            GameServices.graphics().runPendingRenderThreadTasks();
            assertFalse(GameServices.sprites().getMainPlayable().getDead(), "no P1 death during actual transition");
            assertEquals(lives, GameServices.gameState().getLives(), "no hidden reload");
        }
    }

    private static void waitForReleasedWorld(GameLoop loop, int lives, int zone, int act) {
        for (int frame = 0; frame < 400 && (GameServices.fade().isActive()
                || GameServices.fade().hasPendingCompletion()
                || GameServices.level().hasPendingFreshLevelTransitionBoundary()); frame++) {
            tick(loop, lives, 1);
        }
        assertEquals(zone, GameServices.level().getCurrentZone());
        assertEquals(act, GameServices.level().getCurrentAct());
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        assertFalse(GameServices.fade().isActive());
        assertFalse(GameServices.fade().hasPendingCompletion());
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
    }

    private static void replayTwice(GameLoop loop, HeadlessTestFixture fixture,
                                    CanonicalEntryMatrixSupport owner, CanonicalEntryMatrixSupport.Row row,
                                    int lives) {
        owner.assertOwners(row);
        assertFalse(GameServices.fade().hasPendingCompletion());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        tick(loop, lives, 45);
        owner.assertOwners(row);
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(), "Saved2 incoming restore", 45);
            tick(loop, lives, 45);
            owner.assertOwners(row);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(after, registry.capture(), "Saved2 incoming replay", 45);
        }
    }
}
