package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossShieldChild;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicHeadChild;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 5: the Green Hill recreation's arena, from {@code sub_575EA}'s {@code loc_57686} band
 * through {@code loc_576E8}'s allocation of {@code Obj_SSZGHZBoss}.
 *
 * <p>Every value is from the routine. The lock needs Player 1's {@code y_pos} in
 * {@code [$440,$880)} with {@code Events_bg+$00} zero, then {@code y_pos >= $7C0},
 * {@code Camera_X_pos == $160} and the player not in the air ({@code btst #1,status}); it writes
 * {@code Camera_max_X = $160}, {@code Camera_min_Y = Camera_target_max_Y = $7C0} and
 * {@code st (Events_bg+$01).w}. The spawn is a separate gate at {@code loc_576E8}:
 * {@code Camera_Y_pos == $7C0} exactly, and only then {@code AllocateObject},
 * {@code st (Events_bg+$05).w} and {@code Events_bg+$00 = $7F00}.
 *
 * <p><b>Declared setup.</b> The arena sits behind the bridge and a long route, and
 * {@code SSZ1_ScreenInit} drags a leader with no star post back to the arrival column, so the
 * approach is written as a checkpoint restart at {@code ($200,$7C8)}. Measured in a capture, the
 * leader settles on the arena floor at {@code ($200,$86C)} with the camera at {@code ($160,$7C0)} —
 * the exact configuration the two gates ask for, reached by the act's own physics rather than by
 * writing camera values.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszGhzArenaHeadless {

    private static final int APPROACH_X = 0x0200;
    private static final int APPROACH_Y = 0x07C8;
    /** {@code loc_57686}. */
    private static final int PRE_LOCK_MIN_X = 0x0160;
    private static final int PRE_LOCK_MAX_X = 0x19A0;
    private static final int LOCK_MAX_X = 0x0160;
    private static final int ARENA_Y = 0x07C0;
    /** {@code move.w #$7F00,(Events_bg+$00).w}. */
    private static final int BOSS_FIGHTING_WORD = 0x7F00;
    private static final int EV_GHZ_BOSS = 0x00;
    private static final int EV_GHZ_LOCK = 0x01;
    private static final int EV_EVENT_OWNS_BOUNDS = 0x05;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void theArenaLocksAndThenAllocatesTheBossWhenTheCameraSettles(int width) {
        HeadlessTestFixture fixture = bootAtCheckpoint(width, APPROACH_X, APPROACH_Y);
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        assertEquals(0, state.eventsBgByte(EV_GHZ_LOCK), "the lock has not fired at the first pass");

        var camera = GameServices.camera();
        boolean locked = false;
        for (int frame = 0; frame < 400 && !locked; frame++) {
            fixture.stepIdleFrames(1);
            locked = state.eventsBgByte(EV_GHZ_LOCK) != 0;
        }
        assertTrue(locked, "st (Events_bg+$01).w: the leader reached $7C0 with Camera_X at $160");
        assertEquals(LOCK_MAX_X, camera.getMaxX() & 0xFFFF, "move.w #$160,(Camera_max_X_pos).w");
        assertEquals(ARENA_Y, camera.getMinY() & 0xFFFF, "move.w #$7C0,(Camera_min_Y_pos).w");
        assertEquals(ARENA_Y, camera.getMaxYTarget() & 0xFFFF,
                "and (Camera_target_max_Y_pos).w");

        boolean spawned = false;
        for (int frame = 0; frame < 1400 && !spawned; frame++) {
            fixture.stepIdleFrames(1);
            // tst.b, not tst.w: Events_bg+$01 is the lock byte and sits in the same word.
            spawned = state.eventsBgByte(EV_GHZ_BOSS) != 0;
        }
        assertTrue(spawned, "loc_576E8 allocated Obj_SSZGHZBoss");
        assertEquals(ARENA_Y, camera.getY() & 0xFFFF,
                "loc_576E8 gates on Camera_Y_pos == $7C0 exactly, which the $1000 -> $7C0 ease "
                        + "at 2 px a frame takes about 1050 frames to reach");
        assertEquals(BOSS_FIGHTING_WORD, state.eventsBgWord(EV_GHZ_BOSS),
                "move.w #$7F00,(Events_bg+$00).w");
        assertEquals(0, state.eventsBgByte(EV_GHZ_LOCK),
                "the same word write clears Events_bg+$01");
        assertEquals(-1, state.eventsBgByte(EV_EVENT_OWNS_BOUNDS),
                "st (Events_bg+$05).w freezes sub_575EA until a $79 launch clears it");
        // The pre-lock bounds are not restored while the fight runs.
        assertEquals(LOCK_MAX_X, camera.getMaxX() & 0xFFFF, "the arena stays closed");
        assertTrue((camera.getMinX() & 0xFFFF) == PRE_LOCK_MIN_X
                        || (camera.getMinX() & 0xFFFF) == 0,
                "Camera_min_X is whatever the last pre-lock pass left, not "
                        + PRE_LOCK_MAX_X);
    }

    /**
     * The boss the allocation produces, through its own routine table. Every expectation is from
     * {@code off_7A2B4} and the routine bodies: the ship appears at
     * {@code (Camera_X + $110, Camera_Y - $40)}, waits {@code $1F} frames before its dispatch
     * starts at all, falls for {@code $67} with the Mecha Sonic head attached, then runs in with
     * the {@code ChildObjDat_7A69E} emitter and, once {@code Camera_X + $A0} reaches it, drops the
     * six-link {@code ChildObjDat_7A684} chain.
     */
    @Test
    void theBossFallsInRunsAndDropsItsSixLinkChain() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        var camera = GameServices.camera();
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        assertEquals((camera.getX() + SszGhzBossObjectInstance.SPAWN_CAMERA_X_OFFSET) & 0xFFFF,
                boss.getX() & 0xFFFF, "move.w Camera_X_pos + $110, x_pos(a0)");
        assertEquals((camera.getY() + SszGhzBossObjectInstance.SPAWN_CAMERA_Y_OFFSET) & 0xFFFF,
                boss.getY() & 0xFFFF, "move.w Camera_Y_pos - $40, y_pos(a0)");
        assertEquals(0, boss.routineForTest(), "the dispatch has not started yet");
        assertEquals(8, boss.hitsRemainingForTest(), "move.b #8,collision_property(a0)");

        int yBeforeTheWait = boss.getY() & 0xFFFF;
        fixture.stepIdleFrames(0x1F);
        assertEquals(yBeforeTheWait, boss.getY() & 0xFFFF,
                "Obj_Wait with $2E = $1F: nothing moves for those frames");

        boolean falling = false;
        for (int frame = 0; frame < 8 && !falling; frame++) {
            fixture.stepIdleFrames(1);
            falling = boss.routineForTest() == 2;
        }
        assertTrue(falling, "loc_7A2C0 took routine 2 through SetUp_ObjAttributes");
        assertEquals(1, countActive(SszMechaSonicHeadChild.class),
                "Child1_MakeMechaHead: one head, at (0,-$20)");
        int yAtFallStart = boss.getY() & 0xFFFF;
        fixture.stepIdleFrames(0x20);
        assertTrue((boss.getY() & 0xFFFF) > yAtFallStart, "y_vel $100 with MoveSprite2");

        boolean runningIn = false;
        for (int frame = 0; frame < 0x80 && !runningIn; frame++) {
            fixture.stepIdleFrames(1);
            runningIn = boss.routineForTest() >= 4;
        }
        assertTrue(runningIn, "loc_7A2FC after the $67-frame fall");
        assertEquals(1, countActive(SszGhzBossShieldChild.class),
                "ChildObjDat_7A69E: the $1E-offset emitter");

        boolean chained = false;
        for (int frame = 0; frame < 600 && !chained; frame++) {
            fixture.stepIdleFrames(1);
            chained = !boss.chainForTest().isEmpty();
        }
        assertTrue(chained, "loc_7A32C dropped the chain when Camera_X + $A0 reached the ship");
        assertEquals(SszGhzBossObjectInstance.CHAIN_LINKS, boss.chainForTest().size(),
                "ChildObjDat_7A684: dc.w 6-1");
        var links = boss.chainForTest();
        for (int index = 0; index < links.size(); index++) {
            assertEquals(index * 2, links.get(index).subtypeForTest(),
                    "CreateChild9_TreeList numbers the list 0, 2, 4, ...");
        }
        assertTrue(boss.isChainPhaseActive(), "bset #6,$38(a0) hides the emitter while it runs");
    }

    /**
     * {@code sub_7A5A0}'s zero-hits branch and the escape that follows. The flag the rest of the
     * act reads is not written at the killing hit: {@code loc_7A5EC} only starts the run, and
     * {@code loc_7A3F8} writes {@code st (Events_bg+$00).w} — a negative byte — once the
     * {@code x_vel $400} escape has run its {@code $2E} frames out.
     */
    @Test
    void eightHitsSendTheShipAwayAndOnlyThenIsTheFlagNegative() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        // Let it reach a live routine so the defeat is not taken during the entry wait.
        for (int frame = 0; frame < 0x120 && boss.routineForTest() < 4; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(boss.routineForTest() >= 4, "the ship is flying before the first hit");
        assertEquals(0x7F, state.eventsBgByte(EV_GHZ_BOSS) & 0xFF, "still $7F00's high byte");

        var player = fixture.sprite();
        var hit = new com.openggf.level.objects.TouchResponseResult(
                0x06, 0, 0, com.openggf.level.objects.TouchCategory.ENEMY);
        for (int index = 0; index < 8; index++) {
            boss.onPlayerAttack(player, hit);
            for (int cooldown = 0; index < 7 && cooldown < 0x20; cooldown++) {
                fixture.stepIdleFrames(1);
            }
        }
        assertEquals(0, boss.hitsRemainingForTest(), "collision_property reached zero");
        assertTrue(state.eventsBgByte(EV_GHZ_BOSS) > 0,
                "loc_7A5EC writes no flag: the killing hit only starts the escape");

        boolean beaten = false;
        for (int frame = 0; frame < 400 && !beaten; frame++) {
            fixture.stepIdleFrames(1);
            beaten = state.eventsBgByte(EV_GHZ_BOSS) < 0;
        }
        assertTrue(beaten, "loc_7A3F8 st (Events_bg+$00).w after the escape");
        assertEquals(0, countActive(SszMechaSonicHeadChild.class),
                "st (_unkFA89).w deletes the head with the ship");
        assertEquals(0, countActive(SszGhzBossObjectInstance.class), "Go_Delete_Sprite");
    }

    /**
     * Rewind spot: mid-fight, with the ball and chain out. The links are the only SSZ graph so far
     * where the parent of a child is another child — {@code CreateChild9_TreeList} threads
     * {@code parent3} down the list — so a restore has to relink six objects, not one, and the
     * root link's angle has to come back with them.
     */
    @Test
    void theFightSurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        boolean chained = false;
        for (int frame = 0; frame < 900 && !chained; frame++) {
            fixture.stepIdleFrames(1);
            chained = boss.chainForTest().size() == SszGhzBossObjectInstance.CHAIN_LINKS;
        }
        assertTrue(chained, "the spot is taken with the chain out");
        // Let the links finish their word_7A642 drops so the swing is live at the capture.
        fixture.stepIdleFrames(0x50);

        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = registry.capture();

        registry.restore(before);
        sameSnapshot(before, registry.capture(), "restore mid-fight");
        SszGhzBossObjectInstance restored = active(SszGhzBossObjectInstance.class);
        assertNotNull(restored, "the ship is back");
        var links = restored.chainForTest();
        assertEquals(SszGhzBossObjectInstance.CHAIN_LINKS, links.size(),
                "the whole tree list came back");

        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, registry.capture(), "forward replay mid-fight");
    }

    private static void sameSnapshot(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }

    private static SszGhzBossObjectInstance runToSpawn(HeadlessTestFixture fixture) {
        SszGhzBossObjectInstance boss = null;
        for (int frame = 0; frame < 1600 && boss == null; frame++) {
            fixture.stepIdleFrames(1);
            boss = active(SszGhzBossObjectInstance.class);
        }
        assertNotNull(boss, "loc_576E8 allocated Obj_SSZGHZBoss");
        return boss;
    }

    private static <T> T active(Class<T> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return null;
        }
        for (var instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                return type.cast(instance);
            }
        }
        return null;
    }

    private static int countActive(Class<?> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return 0;
        }
        int count = 0;
        for (var instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                count++;
            }
        }
        return count;
    }

    private static HeadlessTestFixture bootAtCheckpoint(int width, int x, int y) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                width == 320 ? WidescreenAspect.NATIVE_4_3.name() : WidescreenAspect.WIDE_16_9.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .startPosition((short) x, (short) y)
                .startPositionIsCentre()
                .build();
        if (GameServices.level().getCheckpointState() instanceof CheckpointState checkpoint) {
            checkpoint.saveCheckpoint(2, x, y, false);
        }
        return fixture;
    }
}
