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
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The LRZ2 ordinary giant ring s9 ($85, subtype 9, ($2640,$640)) under legal
 * seven-Chaos initial progress, with an independent short entry. This is NOT a
 * cold route claim. Drafted read-only on 49344e7c2 (aiz5-slots lane); executed and
 * qualified by the seven-chaos-gateway lane, whose first run exposed the LRZ
 * background-initializer rewind defect that {@link TestS3kLrzBackgroundInitRewind} isolates.
 *
 * <p>Declared boundaries, labelled as such:
 * <ul>
 *   <li>Persistent emerald progress is declared before the first native dispatch:
 *       seven Chaos (state 1), not converted. Reaching LRZ with all seven S3
 *       emeralds is legal. The payout case declares seven Super (state 3).</li>
 *   <li>The approach to the ring and the later pedestal fall use the same
 *       declared local-contact stimuli as TestS3kSanctuaryOriginReturnLifecycle /
 *       TestS3kSanctuaryPedestalLifecycle: placement only, no contact, flag,
 *       timer, Saved2, selection or transition is seeded.</li>
 *   <li>The Super stage fails under ordinary NEUTRAL input. The END/DEL debug
 *       outcome stimulus is not used.</li>
 * </ul>
 *
 * <p>ROM owners (skdisasm 044fa46 sonic3k.asm):
 * <ul>
 *   <li>Obj_SSEntryRing init :128291-128309: with Chaos_emerald_count=7 on an SK
 *       level (SSEntry_CheckLevel :128482) the ring sets $38 bit6 and uses the
 *       PalSPtr_SSEntry palette scripts.</li>
 *   <li>Collision :128332-128339: 50 rings only if Super_emerald_count=7;
 *       otherwise the flash.</li>
 *   <li>SSEntryFlash_GoSS :128441-128477: Save_Level_Data2, then loc_618AC
 *       (Special_bonus_entry_flag=2, $1701, Last_star_post_hit=0,
 *       Restart_level_flag), then sets the Collected_special_ring_array bit.</li>
 *   <li>Obj_HPZSSEntryControl :197949-197995: a state-1 emerald runs the
 *       conversion (Emeralds_converted_flag). $38 bit5, the exit enable, is set
 *       only when no state-1/2 emerald exists, so loc_90C58's Saved2 exit
 *       (:198136-198154) cannot fire on this visit.</li>
 *   <li>Pedestal loc_90926 :197827-197841: Special_bonus_entry_flag=1,
 *       SK_special_stage_flag, Last_star_post_hit=Saved2_last_star_post_hit.</li>
 *   <li>The stage end (:12709-12712), the results $1701 rebuild (:63209-63233)
 *       and Level's Saved2 branch (:7571-7582) return to LRZ2 whether the stage
 *       was won or failed.</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrz2SevenChaosGiantRingGateway {
    private static final int RING_ID = 0x85, RING_SUBTYPE = 9, RING_X = 0x2640, RING_Y = 0x640;
    private static final int HUB_ZONE = 0x17, HUB_ACT = 1, LRZ_ZONE = 9, LRZ2_ACT = 1;
    private static final Bk2FrameInput NEUTRAL = new Bk2FrameInput(0, 0, 0, false, "");

    private final CanonicalEntryMatrixSupport origin = support("LRZ2", LRZ_ZONE, LRZ2_ACT);
    private final CanonicalEntryMatrixSupport hub = support("HPZ-sanctuary", HUB_ZONE, HUB_ACT);

    @BeforeEach void saveConfiguration() { origin.saveConfiguration(); hub.saveConfiguration(); }
    @AfterEach void restoreConfiguration() { origin.restoreConfiguration(); }

    private static CanonicalEntryMatrixSupport support(String label, int zone, int act) {
        return new CanonicalEntryMatrixSupport() {
            @Override TestS3kCanonicalEntryLifecycle.Act binding() {
                return new TestS3kCanonicalEntryLifecycle.Act(label, zone, act);
            }
        };
    }

    @ParameterizedTest(name = "seven-Chaos s9 gateway and Saved2 LRZ2 return {0}")
    @ValueSource(strings = {"tails", "sonic"})
    void sevenChaosRingConvertsInTheSanctuaryAndAFailedPedestalStageReturnsToLrz2(String main) throws Exception {
        var row = new CanonicalEntryMatrixSupport.Row(main, WidescreenAspect.NATIVE_4_3, "off", "");
        var fixture = origin.boot(row);
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(1, 1, 1, 1, 1, 1, 1), false);
        var input = new InputHandler();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        int lives = GameServices.gameState().getLives();
        try {
            ObjectSpawn ringSpawn = locateRing();
            tick(loop, lives, 8);
            int markBefore = GameServices.level().getCheckpointState().getStarPostActivationMark();
            var ring = approachAndFormRing(loop, lives, ringSpawn);
            assertTrue(isSuperEmeraldRing(ring), "seven Chaos on an SK level: $38 bit6 Super Emerald ring");

            var sourceObjects = GameServices.level().getObjectManager();
            int sourceHistory = touchRing(loop, lives, fixture, ringSpawn);
            for (int f = 0; f < 320 && GameServices.level().getObjectManager() == sourceObjects; f++) {
                tick(loop, lives, 1);
                sourceHistory = Math.max(sourceHistory, fixture.gameplayMode().getRewindController().currentFrame());
            }
            assertNotSame(sourceObjects, GameServices.level().getObjectManager(), "loc_618AC restarts into $1701");
            assertNotEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode(), "not the Blue Sphere branch");
            var saved = GameServices.level().getBigRingReturn();
            assertNotNull(saved, "Save_Level_Data2 precedes the destination branch");
            assertEquals(RING_X, saved.playerX());
            assertEquals(RING_Y, saved.playerY());
            assertEquals(LRZ_ZONE, saved.originZone());
            assertEquals(LRZ2_ACT, saved.originAct());
            assertTrue(GameServices.gameState().isSpecialRingCollected(RING_SUBTYPE), "loc_618D0 collected bit");
            TestS3kSanctuaryOriginReturnLifecycle.waitForReleasedWorld(loop, lives, HUB_ZONE, HUB_ACT, row);
            assertFalse(GameServices.level().getTransitions().isLastStarPostHitSet(),
                    "loc_618AC clears Last_star_post_hit for the $1701 start");
            assertTrue(fixture.gameplayMode().getRewindController().currentFrame() < sourceHistory,
                    "the $1701 load retires the LRZ2 history");

            // The conversion ceremony runs under neutral input; released control ends it.
            for (int f = 0; f < 4000 && GameServices.sprites().getMainPlayable().isObjectControlled(); f++)
                tick(loop, lives, 1);
            assertFalse(GameServices.sprites().getMainPlayable().isObjectControlled(), "ceremony releases control");
            assertEquals(List.of(2, 2, 2, 2, 2, 2, 2), GameServices.gameState().getS3kEmeraldStates(),
                    "the state-1 scan converts every Chaos Emerald to a gray Super Emerald");
            assertTrue(GameServices.gameState().isEmeraldsConverted(), "Emeralds_converted_flag");
            TestS3kSanctuaryOriginReturnLifecycle.replayTwice(loop, fixture, hub, row, lives);
            assertEquals(saved, GameServices.level().getBigRingReturn(), "hub replay preserves Saved2");

            assertExitBandStaysClosed(loop, lives, input);

            var provider = TestS3kSanctuaryPedestalLifecycle.selectAndReplayIncomingStage(
                    loop, input, fixture, hub, row, 6, lives);
            assertEquals(saved, GameServices.level().getBigRingReturn(), "pedestal entry preserves Saved2");
            assertNotNull(provider);
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
            for (int f = 0; f < 6000 && loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE; f++) step(loop);
            assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode(),
                    "ordinary neutral input fails the Super stage");
            for (int f = 0; f < 2600 && loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE_RESULTS; f++) step(loop);

            TestS3kSanctuaryOriginReturnLifecycle.waitForReleasedWorld(loop, lives, LRZ_ZONE, LRZ2_ACT, row);
            origin.assertOwners(row);
            var p1 = GameServices.sprites().getMainPlayable();
            assertEquals(saved.playerX(), p1.getCentreX(), "Level loc_6034/loc_2D2C2 Saved2 position");
            assertEquals(saved.rings(), p1.getRingCount());
            assertTrue(GameServices.gameState().isSpecialRingCollected(RING_SUBTYPE));
            assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kSSEntryRingObjectInstance.class)
                            .stream().noneMatch(o -> o.getSpawn().equals(ringSpawn) && !o.isDestroyed()),
                    "a collected s9 ring deletes itself at init");
            assertEquals(List.of(2, 2, 2, 2, 2, 2, 2), GameServices.gameState().getS3kEmeraldStates(),
                    "a failed stage keeps the pedestal gray");
            assertTrue(GameServices.gameState().isEmeraldsConverted());
            // Level masks the stage exit's ori.b #$80 off with andi.b #$7F,(Last_star_post_hit) just
            // before LevelLoop (loc_64DC, skdisasm 044fa46 sonic3k.asm:7945; native RAM $FE2A reads 0 on the first $0C frame), so
            // only the restored low-bit mark below is ROM state here. After load the engine boolean
            // plays Tails_CPU_star_post_flag (loc_137A4 copy, still $80 natively); not asserted here.
            assertEquals(markBefore, GameServices.level().getCheckpointState().getStarPostActivationMark(),
                    "loc_90926 restored Saved2_last_star_post_hit across the sanctuary detour");
            assertFalse(GameServices.level().getLevelGamestate().isTimerPaused(), "origin clock resumes");
            assertFalse(GameServices.level().hasBigRingReturn(), "Saved2 consumed after destination restore");
            TestS3kSanctuaryOriginReturnLifecycle.replayTwice(loop, fixture, origin, row, lives);
        } finally {
            loop.closePresence();
        }
    }

    @ParameterizedTest(name = "seven-Super s9 pays fifty rings and stays in LRZ2 {0}")
    @ValueSource(strings = {"tails", "sonic"})
    void sevenSuperRingPaysFiftyRingsWithoutAnyLoad(String main) throws Exception {
        var row = new CanonicalEntryMatrixSupport.Row(main, WidescreenAspect.NATIVE_4_3, "off", "");
        var fixture = origin.boot(row);
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3, 3, 3, 3, 3, 3, 3), true);
        var input = new InputHandler();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        int lives = GameServices.gameState().getLives();
        try {
            ObjectSpawn ringSpawn = locateRing();
            tick(loop, lives, 8);
            approachAndFormRing(loop, lives, ringSpawn);
            var objects = GameServices.level().getObjectManager();
            int ringsBefore = GameServices.sprites().getMainPlayable().getRingCount();
            touchRing(loop, lives, fixture, ringSpawn);
            for (int f = 0; f < 64; f++) tick(loop, lives, 1);
            assertSame(objects, GameServices.level().getObjectManager(), "loc_61794 claims rings, no load");
            assertTrue(objects.activeObjectsOfType(Sonic3kSSEntryFlashObjectInstance.class).isEmpty());
            assertEquals(ringsBefore + 50, GameServices.sprites().getMainPlayable().getRingCount(), "AddRings 50");
            assertTrue(GameServices.gameState().isSpecialRingCollected(RING_SUBTYPE), "loc_61794 sets the bit");
            assertFalse(GameServices.level().hasBigRingReturn(), "no Save_Level_Data2 on the payout path");
            TestS3kSanctuaryOriginReturnLifecycle.replayTwice(loop, fixture, origin, row, lives);
        } finally {
            loop.closePresence();
        }
    }

    private static ObjectSpawn locateRing() {
        var spawn = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(s -> s.objectId() == RING_ID && s.subtype() == RING_SUBTYPE)
                .findFirst().orElseThrow();
        assertEquals(RING_X, spawn.x(), "LRZ2 placement $85/9 X");
        assertEquals(RING_Y, spawn.y(), "LRZ2 placement $85/9 Y");
        return spawn;
    }

    /** Declared local approach: one horizontal placement, then <= $80 vertical steps for the Y load pass. */
    private static Sonic3kSSEntryRingObjectInstance approachAndFormRing(GameLoop loop, int lives, ObjectSpawn ringSpawn)
            throws Exception {
        var main = GameServices.sprites().getMainPlayable();
        int y = main.getCentreY();
        int approachY = ringSpawn.y() - 8;
        placeForLocalContact(main, ringSpawn.x() - 40, y);
        tick(loop, lives, 1);
        while (y != approachY) {
            y = y < approachY ? Math.min(y + 0x80, approachY) : Math.max(y - 0x80, approachY);
            placeForLocalContact(main, ringSpawn.x() - 40, y);
            tick(loop, lives, 1);
        }
        var objects = GameServices.level().getObjectManager();
        Sonic3kSSEntryRingObjectInstance ring = null;
        for (int f = 0; f < 96; f++) {
            placeForLocalContact(main, ringSpawn.x() - 40, approachY);
            tick(loop, lives, 1);
            ring = objects.activeObjectsOfType(Sonic3kSSEntryRingObjectInstance.class).stream()
                    .filter(o -> o.getSpawn().equals(ringSpawn)).findFirst().orElse(null);
            if (ring != null && ringMapping(ring) >= 8) break;
        }
        assertNotNull(ring, "actual placed $85/9 admission");
        assertTrue(ringMapping(ring) >= 8, "native formation reached its collision frame");
        assertFalse(main.isObjectControlled(), "outside the +/-24 touch box during formation");
        return ring;
    }

    private static int touchRing(GameLoop loop, int lives, HeadlessTestFixture fixture, ObjectSpawn ringSpawn) {
        var main = GameServices.sprites().getMainPlayable();
        placeForLocalContact(main, ringSpawn.x() + 8, ringSpawn.y() - 4);
        tick(loop, lives, 1);
        return fixture.gameplayMode().getRewindController().currentFrame();
    }

    /** Ordinary right/left inputs cross the real band and pad; $38 bit5 is clear after conversion. */
    private static void assertExitBandStaysClosed(GameLoop loop, int lives, InputHandler input) {
        var hubObjects = GameServices.level().getObjectManager();
        var main = GameServices.sprites().getMainPlayable();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(
                new Bk2FrameInput(0, AbstractPlayableSprite.INPUT_RIGHT, 0, false, ""), NEUTRAL));
        for (int f = 0; f < 64 && main.getCentreX() < 0x1640 + 24; f++) tick(loop, lives, 1);
        assertTrue(main.getCentreX() >= 0x1640 + 24, "ordinary movement arms the band ($38 bit3)");
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(
                new Bk2FrameInput(0, AbstractPlayableSprite.INPUT_LEFT, 0, false, ""), NEUTRAL));
        for (int f = 0; f < 64 && Math.abs(main.getCentreX() - 0x1640) >= 8; f++) tick(loop, lives, 1);
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
        for (int f = 0; f < 120; f++) tick(loop, lives, 1);
        assertSame(hubObjects, GameServices.level().getObjectManager(), "loc_90C58 needs $38 bit5: no Saved2 exit");
        assertFalse(GameServices.level().isLevelInactiveForTransition());
        assertEquals(HUB_ZONE, GameServices.level().getCurrentZone());
    }

    private static boolean isSuperEmeraldRing(Sonic3kSSEntryRingObjectInstance ring) throws Exception {
        var method = Sonic3kSSEntryRingObjectInstance.class.getDeclaredMethod("isSuperEmeraldRing");
        method.setAccessible(true);
        return (boolean) method.invoke(ring);
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
        for (int f = 0; f < frames; f++) {
            step(loop);
            assertFalse(GameServices.sprites().getMainPlayable().getDead(), "no P1 death");
            assertEquals(lives, GameServices.gameState().getLives(), "no hidden reload");
        }
    }

    /** Special-stage and results frames have no level P1 to inspect. */
    private static void step(GameLoop loop) {
        loop.step();
        GameServices.graphics().runPendingRenderThreadTasks();
    }
}
