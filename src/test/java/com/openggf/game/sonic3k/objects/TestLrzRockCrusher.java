package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZRockCrusher} (sonic3k.asm:196988-197400, ROM {@code $900E4}) and its timer child
 * {@code loc_90502} (:197215-197260).
 *
 * <p>Every number here is a ROM table entry or a ROM comparison: the two {@code Check_CameraInRange}
 * windows {@code word_901B8}/{@code word_901C4}, the new camera limits those tables' entries 4 and
 * 5 carry, the {@code (3*60)-1} countdown, the {@code word_902EC} drop targets, and the
 * {@code $27}-frame delay {@code loc_902BE} loads.
 */
class TestLrzRockCrusher {

    @Test
    void bodyAndUpperPiecesSuppressFurtherHitsForThirtyTwoObjectTicks() {
        var harness = harness(0, 0xE40, 0x680);
        harness.crusher.update(0, null);
        var piece = new LrzRockCrusherPieceInstance(8, 0, 0);
        piece.setServices(harness.services);
        piece.attachTo(harness.crusher);
        for (var object : java.util.List.of(harness.crusher, piece)) {
            var provider = (com.openggf.level.objects.TouchResponseProvider) object;
            int flags = provider.getCollisionFlags();
            var attack = org.junit.jupiter.api.Assertions.assertInstanceOf(
                    com.openggf.level.objects.TouchResponseAttackable.class, object);
            attack.onPlayerAttack(null, null);
            assertEquals(0, provider.getCollisionFlags(), "Touch_Enemy clears collision immediately");
            for (int tick = 1; tick <= 31; tick++) {
                object.update(tick, null);
                assertEquals(0, provider.getCollisionFlags(), "sub_905A8 lockout tick " + tick);
            }
            object.update(32, null);
            assertEquals(flags, provider.getCollisionFlags(), "restore saved collision at timer zero");
        }
    }

    /** The act 1 placement of subtype 0 is inside {@code word_901B8}'s window. */
    private static final int CRUSHER_X = 0x0E40;
    private static final int CRUSHER_Y = 0x0700;

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    /**
     * {@code Check_CameraInRange} (sonic3k.asm:180433-180446) with {@code word_901B8}:
     * {@code Camera_Y} in {@code [$5E0,$740]} and {@code Camera_X} in {@code [$DC0,$EC0]}, both
     * inclusive. Outside it the routine pops its return address and nothing in the init tail runs.
     */
    @Test
    void subtypeZeroInitialisesOnlyInsideItsCameraWindow() {
        int[][] outside = {{0xE00, 0x5DF}, {0xE00, 0x741}, {0xDBF, 0x600}, {0xEC1, 0x600}};
        for (int[] at : outside) {
            LrzRockCrusher harness = harness(0, at[0], at[1]);
            harness.crusher.update(1, null);
            assertFalse(harness.crusher.initialised(),
                    "camera (" + Integer.toHexString(at[0]) + "," + Integer.toHexString(at[1]) + ")");
        }
        int[][] inside = {{0xDC0, 0x5E0}, {0xEC0, 0x740}, {0xE40, 0x680}};
        for (int[] at : inside) {
            LrzRockCrusher harness = harness(0, at[0], at[1]);
            harness.crusher.update(1, null);
            assertTrue(harness.crusher.initialised(),
                    "camera (" + Integer.toHexString(at[0]) + "," + Integer.toHexString(at[1]) + ")");
        }
    }

    /**
     * The init tail (:197001-197009): the four live limits go to {@code Camera_stored_*}, entries
     * 4 and 5 of the chosen table become {@code Camera_max_X_pos} and
     * {@code Camera_target_max_Y_pos}, and entry 4 is also kept in {@code $1C(a0)}.
     */
    @Test
    void initStoresTheLiveBoundsAndAppliesTheTableLimits() {
        LrzRockCrusher harness = harness(0, 0xE40, 0x680);
        harness.camera.setMinX((short) 0x0100);
        harness.camera.setMaxX((short) 0x2000);
        harness.camera.setMinY((short) 0x0200);
        harness.camera.setMaxYTarget((short) 0x0900);

        harness.crusher.update(1, null);

        assertEquals(0x0100, harness.state.cameraStoredMinX());
        assertEquals(0x2000, harness.state.cameraStoredMaxX());
        assertEquals(0x0200, harness.state.cameraStoredMinY());
        assertEquals(0x0900, harness.state.cameraStoredMaxY());
        assertEquals(0x0EA0, harness.camera.getMaxX() & 0xFFFF, "word_901B8 entry 4");
        assertEquals(0x06A0, harness.camera.getMaxYTarget() & 0xFFFF, "word_901B8 entry 5");
        assertEquals(0x0EA0, harness.crusher.cameraTargetX(), "$1C(a0)");
    }

    /** {@code word_901C4} (:197052) is the other subtype's window and its limits. */
    @Test
    void theOtherSubtypeUsesTheSecondTable() {
        LrzRockCrusher outside = harness(2, 0xE40, 0x680);
        outside.crusher.update(1, null);
        assertFalse(outside.crusher.initialised(), "$DC0-$EC0 is subtype 0's X window, not this one");

        LrzRockCrusher harness = harness(2, 0x500, 0x700);
        harness.crusher.update(1, null);
        assertTrue(harness.crusher.initialised());
        assertEquals(0x04A0, harness.camera.getMaxX() & 0xFFFF, "word_901C4 entry 4");
        assertEquals(0x0790, harness.camera.getMaxYTarget() & 0xFFFF, "word_901C4 entry 5");
    }

    /**
     * {@code loc_901F4} (:197063-197093): bit 0 latches only once
     * {@code Camera_target_max_Y_pos} equals {@code Camera_max_Y_pos}, bit 1 only once the camera
     * has reached {@code $1C(a0)}, and the crusher leaves routine 0 only with both.
     */
    @Test
    void routineZeroWaitsForBothCameraLatchesBeforeReleasingThePieces() {
        LrzRockCrusher harness = harness(0, 0xE40, 0x680);
        harness.crusher.update(1, null);
        assertEquals(0, harness.crusher.routine());

        // The camera has not finished easing down and has not reached $EA0.
        harness.camera.setMaxY((short) 0x0900);
        harness.crusher.update(2, null);
        assertEquals(0, harness.crusher.routine(), "neither latch");
        assertFalse(harness.crusher.piecesReleased());

        // Y done, X still short.
        harness.camera.setMaxY((short) 0x06A0);
        harness.crusher.update(3, null);
        assertEquals(0, harness.crusher.routine(), "only the Y latch");

        // X reaches the stored value.
        harness.camera.setX((short) 0x0EA0);
        harness.crusher.update(4, null);
        assertEquals(2, harness.crusher.routine(), "both latches -> loc_9026E");
        assertTrue(harness.crusher.piecesReleased(), "bset #2,$38(a0)");
    }

    /**
     * {@code loc_9026E} (:197097-197102): {@code bchg #0,$38(a0)} makes the step {@code +1} when
     * the bit was clear, so the first rumble frame moves the crusher one pixel DOWN before the
     * floor is consulted. (The bit alternates, so there is no net descent -- a class that read
     * this as a creep would drift a pixel a frame; the alternation itself needs terrain to
     * observe, because the floor check below ends the rumble as soon as nothing is underneath.)
     */
    @Test
    void theFirstRumbleFrameStepsOnePixelDownBeforeTheFloorCheck() {
        LrzRockCrusher harness = harness(0, 0xE40, 0x680);
        toRumble(harness);
        int start = harness.crusher.getCentreY();

        harness.crusher.update(10, null);
        assertEquals(start + 1, harness.crusher.getCentreY(), "add.w d0,y_pos(a0) with d0 = +1");
    }

    /**
     * {@code tst.w d1 / bpl.s loc_902BE} (:197102-197103) and {@code loc_902BE}
     * (:197127-197135). A NEGATIVE floor distance means the crusher is still buried and it keeps
     * rumbling; anything else -- which in the level only happens once the timer child has rewritten
     * the layout out from under it -- loads routine 4, the {@code $27}-frame delay and
     * {@code word_902EC}'s drop target, and hands {@code Camera_target_max_Y_pos} back.
     */
    @Test
    void theCrusherDropsOnlyOnceNothingIsUnderneathIt() {
        LrzRockCrusher harness = harness(0, 0xE40, 0x680);
        harness.camera.setMaxYTarget((short) 0x06A0);
        toRumble(harness);
        harness.state.storeCameraBounds(0x0100, 0x2000, 0x0200, 0x0900);

        // No level is loaded, so ObjCheckFloorDist reports nothing below -- the post-edit state.
        harness.crusher.update(10, null);

        assertEquals(4, harness.crusher.routine(), "loc_902BE");
        assertEquals(0x27, harness.crusher.timer(), "move.w #$27,$2E(a0)");
        assertEquals(0x850, harness.crusher.dropTargetY(), "word_902EC entry 0");
        assertTrue(harness.crusher.piecesFinished(), "ori.b #$28,$38(a0)");
        assertEquals(0x0900, harness.camera.getMaxYTarget() & 0xFFFF,
                "Camera_stored_max_Y_pos handed back");
    }

    /** {@code word_902EC} entry 1 is the other subtype's drop target (:197138). */
    @Test
    void theOtherSubtypeDropsToItsOwnTarget() {
        LrzRockCrusher harness = harness(2, 0x500, 0x700);
        toRumble(harness);
        harness.crusher.update(10, null);
        assertEquals(0x950, harness.crusher.dropTargetY(), "word_902EC entry 1");
    }

    /**
     * {@code loc_90502}/{@code loc_90512} (:197215-197245): {@code (3*60)-1} frames, then the
     * NEGATIVE request for subtype 0 -- {@code st (Events_bg+$0C)} sets the whole word -- and the
     * screen shake cleared.
     */
    @Test
    void theTimerChildRaisesTheShakeThenFiresTheNegativeRequestAfterThreeSeconds() {
        LrzRockCrusher harness = harness(0, 0xE40, 0x680);
        LrzRockCrusherTimerChildInstance timer =
                new LrzRockCrusherTimerChildInstance(0);
        timer.setServices(harness.services);

        timer.update(1, null);
        assertEquals(LrzRockCrusherTimerChildInstance.COUNTDOWN, timer.countdown());
        assertEquals(-1, harness.state.screenShake().flag(), "st (Screen_shake_flag).w");
        assertEquals(0, harness.state.chunkEditRequest(), "nothing requested yet");

        for (int frame = 2; frame <= 1 + LrzRockCrusherTimerChildInstance.COUNTDOWN; frame++) {
            timer.update(frame, null);
            assertEquals(0, harness.state.chunkEditRequest(),
                    "still counting on frame " + frame);
        }
        timer.update(2 + LrzRockCrusherTimerChildInstance.COUNTDOWN, null);
        assertEquals(-1, (short) harness.state.chunkEditRequest(), "st (Events_bg+$0C).w");
        assertEquals(0, harness.state.screenShake().flag(), "clr.b (Screen_shake_flag).w");
        assertTrue(timer.isDestroyed(), "Delete_Current_Sprite");
    }

    /**
     * {@code loc_9056E} (:197248-197256): the other subtype writes the LOW byte only, so the same
     * word reads POSITIVE and {@code LRZ1_ScreenEvent} takes its other branch, and the shake is
     * left running.
     */
    @Test
    void theOtherSubtypeFiresThePositiveRequestAndLeavesTheShakeRunning() {
        LrzRockCrusher harness = harness(2, 0x500, 0x700);
        LrzRockCrusherTimerChildInstance timer = new LrzRockCrusherTimerChildInstance(2);
        timer.setServices(harness.services);

        for (int frame = 1; frame <= 2 + LrzRockCrusherTimerChildInstance.COUNTDOWN; frame++) {
            timer.update(frame, null);
        }
        assertEquals(0x00FF, harness.state.chunkEditRequest() & 0xFFFF, "st (Events_bg+$0D).w");
        assertTrue(harness.state.chunkEditRequest() > 0, "the word must read POSITIVE");
        assertEquals(-1, harness.state.screenShake().flag(), "this branch does not clear the shake");
    }

    /**
     * The pieces' shake, {@code loc_90436} (sonic3k.asm:197486-197508). {@code d3} is
     * {@code (subtype & 8) >> 1} and {@code d1} is {@code 0} or {@code 2}; the sum indexes
     * {@code byte_904AC} as a BYTE offset into four {@code (frames, delta)} PAIRS, so the pair
     * index is that sum halved and the upper row reads the table's second half. Reading the sum
     * as a pair index instead walks off the end -- which is what a real act-1 load did before this
     * test existed, with an {@code Index 4 out of bounds for length 4} inside the capture.
     */
    @Test
    void everyPieceWalksByteAdjustedShakePairsAndNeverLeavesTheTable() {
        int[][] table = {{1, 8}, {3, -4}, {3, -4}, {7, 2}};
        for (int index = 0; index < 8; index++) {
            int subtype = index * 2;
            LrzRockCrusherPieceInstance piece =
                    new LrzRockCrusherPieceInstance(subtype, 0, 0);
            LrzRockCrusher harness = harness(0, 0xE40, 0x680);
            piece.setServices(harness.services);
            piece.attachTo(harness.crusher);
            harness.crusher.update(1, null);
            harness.camera.setMaxY(harness.camera.getMaxYTarget());
            harness.camera.setX((short) harness.crusher.cameraTargetX());
            harness.crusher.update(2, null);

            // routine 2 -> 4 on the parent's $38 bit 2, then $2E counts down from subtype & 4.
            piece.update(3, null);
            assertEquals(4, piece.routine(), "piece " + index + " released");
            int wait = subtype & 4;
            for (int frame = 0; frame <= wait; frame++) {
                piece.update(4 + frame, null);
            }
            assertEquals(6, piece.routine(), "piece " + index + " shaking");

            // The first pair is d1 = 0, so the row's FIRST entry: pairs 0 and 2.
            int[] first = table[((subtype & 8) >> 1) / 2];
            assertEquals(first[1], piece.delta(), "piece " + index + " first delta");
            // $39(a0) allows two more pair picks and loc_90490 then returns the piece to
            // routine 4 with $2E = ((subtype & 8) >> 1) + 4 (:197509-197515), after which
            // routine 4 starts the next burst -- so the piece cycles 6 -> 4 -> 6 forever.
            boolean returnedToWait = false;
            for (int frame = 0; frame < 60 && !returnedToWait; frame++) {
                piece.update(100 + frame, null);
                if (piece.routine() == 4) {
                    returnedToWait = true;
                    assertEquals(((subtype & 8) >> 1) + 4, piece.timer(),
                            "piece " + index + " reload from loc_90490");
                }
            }
            assertTrue(returnedToWait, "piece " + index + " reaches loc_90490");
            // Sixty more frames of the 6 -> 4 -> 6 cycle must stay inside byte_904AC.
            for (int frame = 0; frame < 60; frame++) {
                piece.update(200 + frame, null);
            }
            assertTrue(piece.routine() == 4 || piece.routine() == 6,
                    "piece " + index + " stays in the shake cycle");
        }
    }

    /** {@code move.b #$40,y_radius(a0)}, {@code collision_property -1}, {@code ObjDat} (:197013-197016). */
    @Test
    void renderAndCollisionStateAreTheInitWrites() {
        LrzRockCrusher zero = harness(0, 0xE40, 0x680);
        assertEquals(0x80, zero.crusher.getOnScreenHalfWidth(), "width_pixels");
        assertEquals(0x40, zero.crusher.getOnScreenHalfHeight(), "height_pixels");
        assertEquals(0x10, zero.crusher.getCollisionFlags(), "ObjDat collision number");
        assertEquals(-1, zero.crusher.getCollisionProperty());
        assertFalse(zero.crusher.isHighPriority(), "subtype 0 leaves art_tile bit 15 clear");
        assertTrue(harness(2, 0x500, 0x700).crusher.isHighPriority(), "bset #7,art_tile");
    }

    // ----- harness ------------------------------------------------------------------------------

    private static void toRumble(LrzRockCrusher harness) {
        harness.crusher.update(1, null);
        harness.camera.setMaxY(harness.camera.getMaxYTarget());
        harness.camera.setX((short) harness.crusher.cameraTargetX());
        harness.crusher.update(2, null);
        assertEquals(2, harness.crusher.routine(), "precondition: rumbling");
    }

    private static LrzRockCrusher harness(int subtype, int cameraX, int cameraY) {
        Camera camera = new Camera();
        camera.setX((short) cameraX);
        camera.setY((short) cameraY);
        LrzZoneRuntimeState state =
                new LrzZoneRuntimeState(Sonic3kZoneIds.ZONE_LRZ, 0, PlayerCharacter.SONIC_ALONE);
        ZoneRuntimeRegistry registry = new ZoneRuntimeRegistry();
        registry.install(state);
        TestObjectServices services = new TestObjectServices()
                .withIsolatedObjectManager()
                .withCamera(camera)
                .withZoneRuntimeRegistry(registry);
        LrzRockCrusherObjectInstance crusher = new LrzRockCrusherObjectInstance(
                new ObjectSpawn(CRUSHER_X, CRUSHER_Y, Sonic3kObjectIds.SPIKER, subtype, 0, false, 0));
        crusher.setServices(services);
        return new LrzRockCrusher(crusher, camera, state, services);
    }

    private record LrzRockCrusher(LrzRockCrusherObjectInstance crusher, Camera camera,
                                  LrzZoneRuntimeState state, TestObjectServices services) {
    }
}
