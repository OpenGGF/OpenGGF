package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SSZHPZTeleporterObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicTrailChild;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 7: the {@code $79} pad at {@code ($1A40,$670)} and the entry of {@code Obj_SSZEndBoss}.
 *
 * <p>Every expectation is a literal from {@code loc_45A84}, {@code loc_45AB0}, {@code loc_7B2DC}
 * or {@code loc_7B308}, written out rather than read back from the production constant it is
 * checking, so a change to the constant fails here instead of agreeing with itself.
 *
 * <p><b>Declared setup.</b> The final arena's own lock wants {@code Camera_X >= $19A0} and the
 * leader below {@code $680}, which a checkpoint restart at the pad's own placement reaches; no
 * cold route gets here yet.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszMechaSpawnHeadless {

    /** The {@code $79:$00} placement slice 7 owns. */
    private static final int PAD_X = 0x1A40;
    private static final int PAD_Y = 0x0670;

    /** {@code sub_575EA}: {@code move.w #$19A0,(Camera_min_X_pos).w}. */
    private static final int FINAL_ARENA_MIN_X = 0x19A0;
    /** {@code move.w #$5C0,(Camera_min_Y_pos).w} and the same into {@code Camera_target_max_Y}. */
    private static final int FINAL_ARENA_Y = 0x05C0;
    /** {@code Events_bg+$06}. */
    private static final int EV_FINAL_ARENA = 0x06;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    /**
     * {@code sub_575EA}'s final-arena branch and {@code loc_45A84}'s gate. The pad does not test
     * the player at all: it waits for {@code Camera_Y_pos == Camera_max_Y_pos}, which only
     * becomes true once the lock's {@code $5C0} has finished easing the camera down.
     */
    @Test
    void theFinalArenaLocksAndThenThePadAllocatesMechaSonic() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszZoneRuntimeState state = requireState();

        boolean locked = false;
        for (int frame = 0; frame < 0x200 && !locked; frame++) {
            fixture.stepIdleFrames(1);
            locked = state.eventsBgByte(EV_FINAL_ARENA) != 0;
        }
        assertTrue(locked, "bset the Events_bg+$06 byte");

        var camera = GameServices.camera();
        assertEquals(FINAL_ARENA_MIN_X, camera.getMinX() & 0xFFFF,
                "move.w #$19A0,(Camera_min_X_pos).w");
        assertEquals(FINAL_ARENA_Y, camera.getMinY() & 0xFFFF,
                "move.w #$5C0,(Camera_min_Y_pos).w");

        SszMechaSonicObjectInstance boss = runToSpawn(fixture);
        assertEquals(camera.getY() & 0xFFFF, camera.getMaxY() & 0xFFFF,
                "loc_45A84 only allocates on the frame Camera_Y_pos has reached Camera_max_Y_pos");
        assertEquals(boss.getSlotIndex() & 0xFFFF, state.carriedObjectSlot(),
                "move.w a1,(_unkFAA4).w");
    }

    /**
     * {@code loc_7B2DC} and {@code loc_7B308}. Each number here is the immediate operand of one
     * instruction, and the two box words and the two positions are all derived from the camera
     * at the moment of the allocation, not from the pad.
     */
    @Test
    void theInitTakesItsBoxAndItsEntryFromTheCameraAtTheAllocation() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToSpawn(fixture);
        var camera = GameServices.camera();
        int cameraX = camera.getX() & 0xFFFF;
        int cameraY = camera.getY() & 0xFFFF;

        // The init runs on the boss's first update, which a plain AllocateObject may defer to
        // the next frame; step until it has.
        for (int frame = 0; frame < 4 && !boss.initExecutedForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(boss.initExecutedForTest(), "loc_7B2DC ran");

        assertEquals(8, boss.getCollisionProperty(), "move.b #8,collision_property(a0)");
        assertEquals(4, boss.routineForTest(), "move.b #4,routine(a0) -- the act-1 branch");
        assertEquals(2, boss.mappingFrameForTest(), "move.b #2,mapping_frame(a0)");
        assertEquals(-0x800, boss.xVelForTest(), "move.w #-$800,x_vel(a0)");

        SszZoneRuntimeState state = requireState();
        assertEquals((cameraX + 0x20) & 0xFFFF, state.bossLeftX(),
                "addi.w #$20,d0 / move.w d0,(_unkFAB4).w");
        assertEquals((cameraX + 0x120) & 0xFFFF, state.bossRightX(),
                "and another addi.w #$100,d0 into (_unkFAB6).w");
        assertEquals((cameraY + 0x30) & 0xFFFF, state.bossCeilingY(),
                "addi.w #$30,d0 / move.w d0,(_unkFAB0).w");
        assertEquals((cameraX + 0x160) & 0xFFFF, boss.getX(),
                "a further addi.w #$40,d0 into x_pos(a0)");
        assertEquals((cameraY + 0xA0) & 0xFFFF, boss.getY(),
                "addi.w #$70,d0 into y_pos(a0)");

        // CreateChild6_Simple ChildObjDat_7D47A: dc.w 1 is two children, not one.
        assertEquals(2, boss.trailForTest().size(),
                "ChildObjDat_7D47A's dc.w 1 is a count of two");
    }

    /**
     * {@code loc_7B3E6}. The entry is eight pixels a frame to the left and the test that ends it
     * is {@code Camera_X_pos - $20} against {@code x_pos}, so it runs off the left of the screen
     * before it turns round. {@code $2E} is then {@code $3F} and the pause is what
     * {@code loc_7B416}'s {@code Obj_Wait} spends.
     */
    @Test
    void theEntryRunsOffTheLeftOfTheScreenAndThenWaitsSixtyThreeFrames() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        int startX = boss.getX();

        fixture.stepIdleFrames(1);
        assertEquals((startX - 8) & 0xFFFF, boss.getX(),
                "MoveSprite2 with x_vel -$800 is eight pixels a frame");

        for (int frame = 0; frame < 0x100 && boss.routineForTest() == 4; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(6, boss.routineForTest(), "move.b #6,routine(a0)");
        assertEquals(0x3F, boss.timerForTest(), "move.w #$3F,$2E(a0)");
        int cameraX = GameServices.camera().getX() & 0xFFFF;
        assertTrue(boss.getX() <= ((cameraX - 0x20) & 0xFFFF),
                "the turn is the frame Camera_X_pos - $20 caught x_pos");

        // loc_7B41C, after Obj_Wait spends $3F: the return is at Camera_Y + $40, four pixels a
        // frame to the right, and it is flipped because the ROM bsets render_flags bit 0.
        for (int frame = 0; frame < 0x50 && boss.routineForTest() == 6; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(8, boss.routineForTest(), "move.b #8,routine(a0)");
        assertEquals(0x400, boss.xVelForTest(), "move.w #$400,x_vel(a0)");
        assertTrue(boss.renderFlippedForTest(), "bset #0,render_flags(a0)");
        assertEquals(((GameServices.camera().getY() & 0xFFFF) + 0x40) & 0xFFFF, boss.getY(),
                "addi.w #$40,d0 / move.w d0,y_pos(a0)");
    }

    /**
     * {@code loc_45AB0}. The pad reads the boss's {@code x_pos} back out of {@code $30(a0)} every
     * frame and arms its own {@code $20}-frame deletion the moment the boss is no longer to the
     * right of it, which known bug #42 recorded as the missing half of this pad.
     */
    @Test
    void thePadExplodesAndDeletesItselfOnceMechaSonicHasPassedIt() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        SSZHPZTeleporterObjectInstance pad = spawnerPad();
        assertNotNull(pad, "the $79 placement at ($1A40,$670) is live");

        boolean passed = false;
        for (int frame = 0; frame < 0x100 && !passed; frame++) {
            fixture.stepIdleFrames(1);
            passed = boss.getX() <= PAD_X;
        }
        assertTrue(passed, "routine 4 carried Mecha Sonic past the pad");

        // move.w #$20,$32(a0), then subq.w #1 a frame until Delete_Current_Sprite.
        for (int frame = 0; frame < 0x30 && !pad.isDestroyed(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(pad.isDestroyed(), "the pad deleted itself after its $20 frames");
    }

    /**
     * {@code byte_7D2FC}, written out as the twenty-two literals the ROM holds. This is the whole
     * of {@code sub_7D2D8}: which frames of Mecha Sonic hurt, which can be attacked and which are
     * harmless is a property of the animation frame and of nothing else.
     */
    @Test
    void theCollisionByteIsTheMappingFrameTableAndNotAState() {
        int[] shipped = {
                0x23, 0x23, 0x09, 0x86, 0x86, 0x86, 0x86, 0x1A,
                0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x09, 0x00,
                0x09, 0x23, 0x06, 0x23, 0x23, 0x23,
        };
        assertEquals(shipped.length, SszMechaSonicObjectInstance.COLLISION_BY_FRAME.length,
                "byte_7D2FC is twenty-two bytes");
        for (int frame = 0; frame < shipped.length; frame++) {
            assertEquals(shipped[frame], SszMechaSonicObjectInstance.COLLISION_BY_FRAME[frame],
                    "byte_7D2FC entry " + frame);
        }

        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        fixture.stepIdleFrames(1);
        assertEquals(shipped[boss.mappingFrameForTest()], boss.getCollisionFlags(),
                "sub_7D2D8 wrote the row for the frame the boss is actually drawing");
    }

    /**
     * {@code byte_7B62E} and {@code byte_7B636}. The three attacks are a fixed eight-entry cycle
     * on {@code $3B(a0)}, not a random draw — only {@code loc_7B484}'s landing reads the RNG —
     * so the sequence a recorded run sees is a function of how many jumps have happened.
     */
    @Test
    void theAttackChoiceIsAFixedEightEntryCycleAndNotARandomDraw() {
        int[] cycle = {0, 1, 2, 0, 1, 2, 0, 1};
        int[] routines = {0x16, 0x1A, 0x1E};
        assertEquals(cycle.length, SszMechaSonicObjectInstance.ATTACK_CYCLE.length,
                "byte_7B62E is eight bytes");
        for (int index = 0; index < cycle.length; index++) {
            assertEquals(cycle[index], SszMechaSonicObjectInstance.ATTACK_CYCLE[index],
                    "byte_7B62E entry " + index);
        }
        for (int index = 0; index < routines.length; index++) {
            assertEquals(routines[index], SszMechaSonicObjectInstance.ATTACK_ROUTINES[index],
                    "byte_7B636 entry " + index);
        }
    }

    /** Rewind across the entry: before the spawn, with the boss live, and after. */
    @Test
    void theEntrySurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        fixture.stepIdleFrames(4);

        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = registry.capture();

        registry.restore(before);
        sameSnapshot(before, registry.capture(), "restore during the entry");
        SszMechaSonicObjectInstance restored = active(SszMechaSonicObjectInstance.class);
        assertNotNull(restored, "Mecha Sonic is back");
        assertEquals(boss.routineForTest(), restored.routineForTest(), "routine(a0) came back");
        assertFalse(restored.trailForTest().isEmpty(), "the trail children relinked");

        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, registry.capture(), "forward replay during the entry");
    }

    /**
     * {@code sub_7D236}'s {@code add.w d0,d0} against four-byte rows only addresses whole rows
     * because {@code CreateChild6_Simple} steps its subtypes with {@code addq.w #2,d2}. Read as
     * one-per-child instead, subtype 1 would take {@code $81C} as a priority word — not a
     * sprite-table offset at all — which is how the first draft of this was caught.
     */
    @Test
    void theTrailChildrenStepTheirSubtypesByTwoSoTheRowsLineUp() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        List<SszMechaSonicTrailChild> children = boss.trailForTest();
        assertEquals(2, children.size(), "ChildObjDat_7D47A's dc.w 1 loops dbf twice");

        SszMechaSonicTrailChild first = children.get(0);
        SszMechaSonicTrailChild second = children.get(1);
        assertEquals(0, first.subtypeForTest(), "moveq #0,d2");
        assertEquals(2, second.subtypeForTest(), "addq.w #2,d2, not addq.w #1");

        assertEquals(-4, first.childDxForTest(), "dc.b -4 of byte_7D24C's first row");
        assertEquals(0x1C, first.childDyForTest(), "dc.b $1C");
        assertEquals(0x300, first.priorityWordForTest(), "dc.w $300");

        assertEquals(8, second.childDxForTest(), "dc.b 8 of the second row");
        assertEquals(0x1C, second.childDyForTest(), "dc.b $1C");
        assertEquals(0x200, second.priorityWordForTest(), "dc.w $200");
    }

    // --- helpers ------------------------------------------------------------------------------

    private static void sameSnapshot(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }

    private static SszZoneRuntimeState requireState() {
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElse(null);
        assertNotNull(state, "SSZ act 1 has its runtime state");
        return state;
    }

    private static SszMechaSonicObjectInstance runToSpawn(HeadlessTestFixture fixture) {
        SszMechaSonicObjectInstance boss = null;
        for (int frame = 0; frame < 0x400 && boss == null; frame++) {
            fixture.stepIdleFrames(1);
            boss = active(SszMechaSonicObjectInstance.class);
        }
        assertNotNull(boss, "loc_45A84 allocated Obj_SSZEndBoss");
        return boss;
    }

    private static SszMechaSonicObjectInstance runToInit(HeadlessTestFixture fixture) {
        SszMechaSonicObjectInstance boss = runToSpawn(fixture);
        for (int frame = 0; frame < 4 && !boss.initExecutedForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(boss.initExecutedForTest(), "loc_7B2DC ran");
        return boss;
    }

    private static SSZHPZTeleporterObjectInstance spawnerPad() {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return null;
        }
        for (var instance : manager.getActiveObjects()) {
            if (instance instanceof SSZHPZTeleporterObjectInstance pad
                    && (pad.getX() & 0xFFFF) == PAD_X) {
                return pad;
            }
        }
        return null;
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

    private static List<Object> allActive(Class<?> type) {
        List<Object> out = new ArrayList<>();
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return out;
        }
        for (var instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                out.add(instance);
            }
        }
        return out;
    }

    private static HeadlessTestFixture bootAtCheckpoint(int width, int x, int y) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                width == 320 ? WidescreenAspect.NATIVE_4_3.name()
                        : WidescreenAspect.WIDE_16_9.name());
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
