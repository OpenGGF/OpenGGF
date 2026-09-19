package com.openggf.game.sonic3k.objects;

import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_LRZSpikeBallLauncher} (sonic3k.asm:89848-89933), its ball at {@code loc_44916} /
 * {@code loc_44954}, and the {@code Animate_SpriteIrregularDelay} script that drives both
 * (:36238-36308).
 *
 * <p>The cycle lengths here are the ROM's own {@code subq.b}/{@code bcc} arithmetic applied to
 * {@code byte_4495E} and {@code byte_44964}, not measurements taken off the class. A delay byte
 * of {@code n} holds a frame for {@code n + 1} passes, because the script only advances on the
 * pass where the timer is already zero:
 * <ul>
 *   <li>Script 0 is one entry, {@code (3, $7F)}, so the launcher sits on frame {@code 3} for
 *       {@code $80 = 128} passes; the {@code $FC} that charges it lands on pass {@code 129}.</li>
 *   <li>Script 1's forty-two entries carry delays {@code $D,$D,$B,$B,9,9,7,7,5,5,3,3,1,1} and
 *       then twenty-eight zeros, so the flicker runs
 *       {@code 2*(14+12+10+8+6+4+2) + 28 = 140} passes.</li>
 * </ul>
 */
class TestLrzSpikeBallLauncher {

    private static final int X = 0x0B40;
    private static final int Y = 0x0700;
    /** {@code (3, $7F)}: the delay plus the pass that reads it. */
    private static final int WIND_UP_PASSES = 0x80;
    /** The forty-two entries of {@code byte_44964}, summed as {@code delay + 1}. */
    private static final int RAMP_PASSES = 2 * (14 + 12 + 10 + 8 + 6 + 4 + 2) + 28;

    private TestObjectServices services;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        com.openggf.camera.Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) (X - 160));
        camera.setY((short) (Y - 112));
        com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(
                X - 160, Y - 112, X + 160, Y + 112, 0);
        services = new TestObjectServices().withIsolatedObjectManager().withCamera(camera);
    }

    private LrzSpikeBallLauncherObjectInstance launcher(int subtype) {
        LrzSpikeBallLauncherObjectInstance object = new LrzSpikeBallLauncherObjectInstance(
                new ObjectSpawn(X, Y, 0x37, subtype, 0, false, 0));
        object.setServices(services);
        return object;
    }

    /** {@code move.b subtype(a0),d0 / lsl.w #4,d0 / neg.w d0} (sonic3k.asm:89894-89900). */
    @Test
    void theSubtypeIsTheLaunchSpeedAndNothingElse() {
        assertEquals(-0x0500, launcher(0x50).launchVelocity(), "subtype $50");
        assertEquals(-0x0600, launcher(0x60).launchVelocity(), "subtype $60");
        assertEquals(-0x0700, launcher(0x70).launchVelocity(), "subtype $70");
        // The solid box is the same for every one of them (:89904-89906).
        assertEquals(0x1B, launcher(0x60).getSolidParams().halfWidth());
        assertEquals(4, launcher(0x60).getSolidParams().airHalfHeight());
        assertEquals(5, launcher(0x60).getSolidParams().groundHalfHeight());
    }

    /** The {@code AllocateObjectAfterCurrent} block (sonic3k.asm:89855-89874). */
    @Test
    void theBallIsAllocatedEightPixelsAboveTheLauncherAndIsAHazardAtRest() {
        LrzSpikeBallLauncherObjectInstance object = launcher(0x60);
        assertEquals(0, balls().size(), "nothing is allocated before the first pass");
        object.update(0, null);

        LrzSpikeBallLauncherBallInstance ball = object.ball();
        assertNotNull(ball, "move.w a1,$3C(a0) (sonic3k.asm:89871)");
        assertEquals(X, ball.getCentreX(), "move.w x_pos(a0),x_pos(a1)");
        assertEquals(Y - 8, ball.getCentreY(), "subi.w #8,y_pos(a1)");
        assertEquals(Y - 8, ball.restY(), "move.w y_pos(a1),$46(a1)");
        assertEquals(0x9A, ball.getCollisionFlags(),
                "move.b #$9A,collision_flags(a1): the resting ball hurts too");
        assertFalse(ball.isInFlight(), "the allocated code pointer is loc_44954");

        object.update(1, null);
        assertEquals(1, balls().size(), "the launcher allocates once, on its init pass");
    }

    /**
     * {@code loc_448A8}'s two {@code $FC}s: the first with {@code anim(a0) == 0}, which only
     * plays {@code sfx_Charging}, and the second with {@code anim(a0) == 1}, which launches.
     */
    @Test
    void theWindUpChargesAndOnlyTheRampLaunches() {
        LrzSpikeBallLauncherObjectInstance object = launcher(0x60);
        object.update(0, null);
        assertEquals(3, object.mappingFrame(), "byte_4495E entry 0 is frame 3");

        // Pass 1 is the entry read; passes 2 .. $80 are the delay; the $FC lands on $80 + 1.
        for (int pass = 1; pass < WIND_UP_PASSES; pass++) {
            object.update(pass, null);
            assertEquals(0, object.anim(), "pass " + pass + " is still script 0");
            assertFalse(object.ball().isInFlight(), "nothing fires during the wind-up");
        }
        // The charging $FC.
        object.update(WIND_UP_PASSES, null);
        assertEquals(0, object.anim(), "the $FC pass still reads anim 0: the $FD is a pass later");
        assertFalse(object.ball().isInFlight(), "loc_448B6 is the sound branch, not the launch");

        // The $FD pass.
        object.update(WIND_UP_PASSES + 1, null);
        assertEquals(1, object.anim(), "move.b 1(a1,d1.w),anim(a0) at loc_1AD00");

        // Script 1 restarts the frame counter, so its first entry lands on the next pass.
        object.update(WIND_UP_PASSES + 2, null);
        assertEquals(4, object.mappingFrame(), "byte_44964 entry 0 is frame 4");

        for (int pass = 1; pass < RAMP_PASSES; pass++) {
            object.update(WIND_UP_PASSES + 2 + pass, null);
            assertFalse(object.ball().isInFlight(),
                    "the ramp only flickers; pass " + pass + " of " + RAMP_PASSES);
        }
        object.update(WIND_UP_PASSES + 2 + RAMP_PASSES, null);
        assertTrue(object.ball().isInFlight(), "loc_448D4 writes loc_44916 into the ball");
        assertEquals(-0x0600, object.ball().yVelocity(), "move.w d0,y_vel(a1)");
    }

    /**
     * {@code loc_44916} (sonic3k.asm:89917-89932). {@code MoveSprite} moves with the <em>old</em>
     * {@code y_vel} and only then adds {@code $38}, so the first frame of flight is the launch
     * velocity exactly: {@code -$600 >> 8 = -6} pixels.
     */
    @Test
    void theFirstFrameOfFlightIsTheLaunchVelocityAndGravityFollows() {
        LrzSpikeBallLauncherBallInstance ball = new LrzSpikeBallLauncherBallInstance(X, Y);
        ball.setServices(services);
        ball.launch(-0x0600);

        ball.update(0, null);
        assertEquals(Y - 6, ball.getCentreY(), "y += old y_vel >> 8");
        assertEquals(-0x0600 + 0x38, ball.yVelocity(), "addi.w #$38,y_vel(a0)");
    }

    /**
     * The flight ends on an unsigned compare of {@code $46} against {@code y_pos}
     * (:89935-89938) and snaps back to {@code $46} exactly, so a launch and its landing are
     * pixel-identical however high the ball went.
     */
    @Test
    void theBallAlwaysLandsExactlyOnItsRestHeight() {
        // Passes aloft, from -(subtype << 4) stepped by $38 a frame with MoveSprite's own
        // old-velocity-then-gravity order: the flight is a plain parabola with no terrain in it.
        int[] subtypes = { 0x50, 0x60, 0x70 };
        int[] expectedPasses = { 47, 57, 66 };
        for (int index = 0; index < subtypes.length; index++) {
            int subtype = subtypes[index];
            LrzSpikeBallLauncherBallInstance ball = new LrzSpikeBallLauncherBallInstance(X, Y);
            ball.setServices(services);
            ball.launch((short) -(subtype << 4));

            int passes = 0;
            int highest = Y;
            while (ball.isInFlight() && passes < 600) {
                ball.update(passes, null);
                highest = Math.min(highest, ball.getCentreY());
                passes++;
            }
            assertFalse(ball.isInFlight(), "subtype " + Integer.toHexString(subtype) + " lands");
            assertEquals(expectedPasses[index], passes,
                    "subtype " + Integer.toHexString(subtype) + " passes aloft");
            assertEquals(Y, ball.getCentreY(), "move.w d0,y_pos(a0) at loc_44948");
            assertEquals(0, ball.mappingFrame(), "move.b #0,mapping_frame(a0)");
            assertTrue(highest < Y - 0x20,
                    "subtype " + Integer.toHexString(subtype) + " reached " + (Y - highest));
        }
    }

    /**
     * {@code subq.b #1,anim_frame_timer(a0) / bpl} with a reload of {@code 2}
     * (:89917-89924): three frames, each held for three passes, and only while flying.
     */
    @Test
    void theSpinIsThreeFramesOnATwoFrameTimer() {
        LrzSpikeBallLauncherBallInstance ball = new LrzSpikeBallLauncherBallInstance(X, Y);
        ball.setServices(services);
        ball.launch(-0x0700);

        // The first pass finds the timer at zero and steps straight to frame 1.
        ball.update(0, null);
        assertEquals(1, ball.mappingFrame());
        ball.update(1, null);
        ball.update(2, null);
        assertEquals(1, ball.mappingFrame(), "the reload of 2 holds the frame for three passes");
        ball.update(3, null);
        assertEquals(2, ball.mappingFrame());
        for (int i = 0; i < 3; i++) {
            ball.update(4 + i, null);
        }
        assertEquals(0, ball.mappingFrame(), "cmpi.b #3,mapping_frame / blo wraps at three");
    }

    private List<LrzSpikeBallLauncherBallInstance> balls() {
        return services.objectManager().getActiveObjects().stream()
                .filter(LrzSpikeBallLauncherBallInstance.class::isInstance)
                .map(LrzSpikeBallLauncherBallInstance.class::cast)
                .toList();
    }
}
