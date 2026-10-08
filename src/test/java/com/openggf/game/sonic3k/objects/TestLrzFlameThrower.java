package com.openggf.game.sonic3k.objects;

import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.TrigLookupTable;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZFlameThrower} (sonic3k.asm:89227-89448), its two variants and the flame at
 * {@code loc_44048}.
 *
 * <p>Every expectation is computed from the ROM's own immediates and the ROM's own sine table
 * through the routine's arithmetic, never read back from the class: {@code 2*60} firing,
 * {@code (subtype & $7F) * 4} idle, the {@code andi.b #3} emission gate, {@code sin(angle) asr 4}
 * into {@code $2E} with {@code addq.b #8,angle}, and the flame's {@code sin/cos($2E) asl 2}.
 */
class TestLrzFlameThrower {

    private static final int X = 0x0400;
    private static final int Y = 0x0500;
    /** {@code move.w #2*60,$30(a0)} (sonic3k.asm:89241). */
    private static final int FIRING_FRAMES = 120;

    private TestObjectServices services;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        // tst.b render_flags(a0) / bpl (sonic3k.asm:89297) skips the allocation while the
        // thrower is off screen, and isWithinSolidContactBounds() is the engine's model of that
        // bit, so the camera has to be looking at it for any flame to exist.
        com.openggf.camera.Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) (X - 160));
        camera.setY((short) (Y - 112));
        // The gate reads the static viewport snapshot ObjectManager publishes each frame, which a
        // unit fixture has to place itself.
        com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(
                X - 160, Y - 112, X + 160, Y + 112, 0);
        services = new TestObjectServices().withIsolatedObjectManager().withCamera(camera);
    }

    private LrzFlameThrowerObjectInstance thrower(int subtype, boolean mirrored) {
        LrzFlameThrowerObjectInstance object = new LrzFlameThrowerObjectInstance(
                new ObjectSpawn(X, Y, 0x29, subtype, mirrored ? 1 : 0, false, 0));
        object.setServices(services);
        return object;
    }

    /** {@code bpl.s loc_43DC4} (sonic3k.asm:89235) and the two {@code $32} seeds. */
    @Test
    void subtypeBitSevenPicksTheVariantAndTheRestIsTheIdleLength() {
        LrzFlameThrowerObjectInstance horizontal = thrower(0x13, false);
        assertEquals(LrzFlameThrowerObjectInstance.Axis.HORIZONTAL, horizontal.axis());
        assertEquals(0x13 * 4, horizontal.idleFrames(),
                "lsl.w #2,d0 on the subtype (sonic3k.asm:89248-89249)");

        LrzFlameThrowerObjectInstance vertical = thrower(0x93, false);
        assertEquals(LrzFlameThrowerObjectInstance.Axis.VERTICAL, vertical.axis());
        assertEquals(0x13 * 4, vertical.idleFrames(),
                "andi.w #$7F,d0 / lsl.w #2,d0 (sonic3k.asm:89238-89239): bit 7 is the variant, "
                        + "not part of the length");

        assertEquals(FIRING_FRAMES, horizontal.phaseTimer(), "move.w #2*60,$30(a0)");
        assertFalse(horizontal.isIdle(), "a cleared slot starts at $2F = 0, firing");
        // The solid widths differ and the heights do not (sonic3k.asm:89333-89335, :89428-89430).
        assertEquals(0x23, horizontal.getSolidParams().halfWidth());
        assertEquals(0x1B, vertical.getSolidParams().halfWidth());
        assertEquals(horizontal.getSolidParams().airHalfHeight(), vertical.getSolidParams().airHalfHeight());
    }

    /**
     * The phase machine at {@code loc_43DDC} (sonic3k.asm:89257-89274): {@code 2*60} firing, then
     * {@code $32} idle, then {@code 2*60} again. The counts are the ROM's {@code subq.w}/{@code bpl}
     * semantics, so each phase ends on the frame the counter goes negative.
     */
    @Test
    void theCycleIsOneHundredAndTwentyFiringThenTheSubtypeIdle() {
        int subtype = 0x13;
        LrzFlameThrowerObjectInstance object = thrower(subtype, false);

        for (int frame = 0; frame < FIRING_FRAMES; frame++) {
            object.update(frame, null);
            assertFalse(object.isIdle(), "still firing on frame " + frame);
        }
        // The 121st dispatch takes $30 from 0 to -1 and flips the phase.
        object.update(FIRING_FRAMES, null);
        assertTrue(object.isIdle(), "$30 going negative sets $2F = 1 (sonic3k.asm:89262-89264)");
        assertEquals(subtype * 4, object.phaseTimer(), "move.w $32(a0),$30(a0)");

        for (int frame = 0; frame < subtype * 4; frame++) {
            object.update(frame, null);
            assertTrue(object.isIdle(), "still idle on idle frame " + frame);
        }
        object.update(0, null);
        assertFalse(object.isIdle(), "the idle counter going negative returns to firing");
        assertEquals(FIRING_FRAMES, object.phaseTimer(), "move.w #2*60,$30(a0) (:89269)");
    }

    /**
     * {@code andi.b #3,d0} on {@code Level_frame_counter+1} (sonic3k.asm:89283-89285). Only every
     * fourth level frame reaches {@code loc_43E4E}, and {@code angle} steps {@code 8} there.
     */
    @Test
    void onlyEveryFourthLevelFrameStepsTheAngle() {
        LrzFlameThrowerObjectInstance object = thrower(0x13, false);
        int expectedAngle = 0;
        for (int levelFrame = 0; levelFrame < 16; levelFrame++) {
            object.update(levelFrame, null);
            if ((levelFrame & 3) == 0) {
                expectedAngle = (expectedAngle + 8) & 0xFF;
            }
            assertEquals(expectedAngle, object.angle(),
                    "addq.b #8,angle(a0) runs only on the emitting frames; level frame "
                            + levelFrame);
        }
        assertEquals(4 * 8, expectedAngle, "four emissions in sixteen level frames");
    }

    /**
     * The allocation block (sonic3k.asm:89292-89320). {@code $2E = sin(angle) asr 4} for the angle
     * <em>before</em> the step, and the flame leaves at {@code cos($2E) asl 2} horizontally with
     * {@code sin($2E) asl 2} vertically, from {@code x_pos + $10}.
     */
    @Test
    void theHorizontalFlameLeavesOnTheSineOfTheSweepAngle() {
        LrzFlameThrowerObjectInstance object = thrower(0x13, false);
        object.update(0, null);

        int expectedEmissionAngle = (TrigLookupTable.sinHex(0) >> 4) & 0xFF;
        assertEquals(expectedEmissionAngle, object.emissionAngle(),
                "move.b angle(a0),d0 / GetSineCosine / asr.w #4,d0 / move.b d0,$2E");

        List<LrzFlameObjectInstance> flames = flames();
        assertEquals(1, flames.size(), "AllocateObjectAfterCurrent makes one flame a step");
        LrzFlameObjectInstance flame = flames.get(0);
        assertEquals(TrigLookupTable.cosHex(expectedEmissionAngle) << 2, flame.xVelocity(),
                "move.w d1,x_vel(a1) after asl.w #2,d1 (sonic3k.asm:89308-89310)");
        assertEquals(TrigLookupTable.sinHex(expectedEmissionAngle) << 2, flame.yVelocity(),
                "move.w d0,y_vel(a1) after asl.w #2,d0");
        assertEquals((X + 0x10) & 0xFFFF, flame.getCentreX(),
                "addi.w #$10,x_pos(a1) (sonic3k.asm:89295)");
        assertEquals(Y, flame.getCentreY(), "the vertical coordinate is copied unchanged");
    }

    /**
     * {@code btst #0,status(a0)} (sonic3k.asm:89311-89314): the mirrored placement negates
     * {@code x_vel} and moves the flame {@code $20} back, so the jet leaves the other side.
     */
    @Test
    void theMirroredHorizontalFlameLeavesTheOtherSide() {
        LrzFlameThrowerObjectInstance object = thrower(0x13, true);
        object.update(0, null);

        LrzFlameObjectInstance flame = flames().get(0);
        int emissionAngle = object.emissionAngle();
        assertEquals(-(TrigLookupTable.cosHex(emissionAngle) << 2), flame.xVelocity(),
                "neg.w x_vel(a1)");
        assertEquals((X + 0x10 - 0x20) & 0xFFFF, flame.getCentreX(),
                "subi.w #2*$10,x_pos(a1) (sonic3k.asm:89313)");
    }

    /**
     * The vertical variant swaps which trig term drives which axis and offsets in {@code y}
     * instead (sonic3k.asm:89401-89426).
     */
    @Test
    void theVerticalFlameSwapsTheAxes() {
        LrzFlameThrowerObjectInstance object = thrower(0x93, false);
        object.update(0, null);

        LrzFlameObjectInstance flame = flames().get(0);
        int emissionAngle = object.emissionAngle();
        assertEquals(TrigLookupTable.cosHex(emissionAngle) << 2, flame.yVelocity(),
                "move.w d1,y_vel(a1) (sonic3k.asm:89417)");
        assertEquals(TrigLookupTable.sinHex(emissionAngle) << 2, flame.xVelocity(),
                "move.w d0,x_vel(a1) (:89418)");
        assertEquals((Y + 0x10) & 0xFFFF, flame.getCentreY(),
                "addi.w #$10,y_pos(a1) (:89404)");
        assertEquals(X, flame.getCentreX(), "the horizontal coordinate is copied unchanged");
    }

    /**
     * {@code loc_44048} (sonic3k.asm:89434-89448). {@code $24} seeded at 8 steps
     * {@code mapping_frame} by two, and the step that reaches {@code 6} deletes the flame: the
     * first step lands on the ninth dispatch and each later one eight after, because the step
     * reloads {@code 7}.
     */
    @Test
    void theFlameStepsTwoFramesAndDiesOnTheThirdStep() {
        LrzFlameObjectInstance flame = new LrzFlameObjectInstance(X, Y, 0x100, 0, 0, 2,
                false, false);
        flame.setServices(services);

        for (int frame = 0; frame < 9; frame++) {
            flame.update(frame, null);
        }
        assertEquals(2, flame.mappingFrame() & ~1,
                "addq.b #2,mapping_frame(a0) on the ninth dispatch (sonic3k.asm:89435-89437)");
        assertFalse(flame.isDestroyed(), "frame 2 is short of the #6 limit");

        for (int frame = 0; frame < 8; frame++) {
            flame.update(frame, null);
        }
        assertEquals(4, flame.mappingFrame() & ~1, "the second step, eight dispatches later");
        assertFalse(flame.isDestroyed());

        for (int frame = 0; frame < 8 && !flame.isDestroyed(); frame++) {
            flame.update(frame, null);
        }
        assertTrue(flame.isDestroyed(),
                "cmpi.b #6,mapping_frame(a0) / bhs.s loc_44084 (sonic3k.asm:89438-89439)");
    }

    /** {@code jsr (MoveSprite2)} with no gravity term (sonic3k.asm:89444). */
    @Test
    void theFlameCarriesItsVelocityWithNoGravity() {
        LrzFlameObjectInstance flame = new LrzFlameObjectInstance(X, Y, 0x200, -0x100, 0, 2,
                false, false);
        flame.setServices(services);

        flame.update(0, null);
        assertEquals(X + 2, flame.getCentreX(), "$200 is two pixels a frame");
        assertEquals(Y - 1, flame.getCentreY(), "-$100 is one pixel a frame, upward");

        flame.update(1, null);
        assertEquals(X + 4, flame.getCentreX(), "and it does not accelerate");
        assertEquals(Y - 2, flame.getCentreY());
    }

    private List<LrzFlameObjectInstance> flames() {
        return services.objectManager().getActiveObjects().stream()
                .filter(LrzFlameObjectInstance.class::isInstance)
                .map(LrzFlameObjectInstance.class::cast)
                .toList();
    }

    @SuppressWarnings("unused")
    private static String describe(ObjectInstance object) {
        return object.getClass().getSimpleName();
    }
}
