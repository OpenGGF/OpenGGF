package flappytails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The port of {@code Tails_Move_FlySwim} behaves as the routine reads. */
class FlightTest {
    @Test
    void glidingAddsEightAFrameAndMovesByTheSpeed() {
        Flight flight = new Flight();
        flight.start(100);
        flight.tick(false, false, 0);
        assertEquals(8, flight.yVel(), "addi.w #8,y_vel(a0) while gliding");
        flight.tick(false, false, 0);
        assertEquals(16, flight.yVel());
        assertEquals((100 << 8) + 8 + 16, flight.yFine(), "MoveSprite2 adds the new speed each frame");
    }

    @Test
    void aFlapPushesUpUntilFasterThanMinusHundredHexThenGlides() {
        Flight flight = new Flight();
        flight.start(100);
        assertTrue(flight.tick(true, false, 0), "the press starts a flap");
        assertEquals(8, flight.yVel(), "the request frame still glides");
        int frames = 0;
        while (flight.flapping()) {
            flight.tick(false, false, 0);
            frames++;
        }
        // 8 -> -$108 takes nine pushes of $20; the push after -$100 is passed ends the flap.
        assertEquals(-0x118, flight.yVel());
        assertEquals(10, frames);
        flight.tick(false, false, 0);
        assertEquals(-0x110, flight.yVel(), "gliding again");
    }

    @Test
    void pressesDuringAFlapOrWhileRisingFastAreIgnored() {
        Flight flight = new Flight();
        flight.start(100);
        flight.tick(true, false, 0);
        flight.tick(false, false, 0);
        assertFalse(flight.tick(true, false, 0), "already flapping");
        while (flight.flapping()) flight.tick(false, false, 0);
        assertFalse(flight.tick(true, false, 0), "rising faster than -$100");
    }

    @Test
    void aFlapLastsAtMostThirtyFrames() {
        Flight flight = new Flight();
        flight.start(100);
        flight.setYVel(0x400);           // falling fast: thirty pushes do not reach -$100
        flight.tick(true, false, 0);
        int frames = 0;
        while (flight.flapping()) {
            flight.tick(false, false, 0);
            frames++;
        }
        assertEquals(Flight.FLAP_FRAMES_END - 2, frames, "flag 2 counts to $20");
    }

    @Test
    void theCeilingStopsRisingSixteenPixelsDown() {
        Flight flight = new Flight();
        flight.start(16);
        flight.setYVel(-0x100);
        flight.tick(false, false, 0);
        assertEquals(0, flight.yVel());
        assertEquals(16, flight.y());
    }

    @Test
    void theTimerRunsDownOnOddFramesAndATiredTailsCannotFlap() {
        Flight flight = new Flight();
        flight.start(100);
        flight.tick(false, false, 0);
        assertEquals(Flight.FULL_TIMER, flight.timer(), "even frames leave the timer");
        flight.tick(false, true, 0);
        assertEquals(Flight.FULL_TIMER - 1, flight.timer());
        for (int i = 0; i < 2 * Flight.FULL_TIMER; i++) flight.tick(false, (i & 1) == 0, -10000);
        assertTrue(flight.tired());
        assertEquals(TailsArt.ANIM_TIRED, flight.animation());
        assertFalse(flight.tick(true, true, -10000), "no flaps without flight time");
    }

    @Test
    void flyingSoundsEverySixteenFrames() {
        int due = 0;
        for (long frame = 0; frame < 160; frame++) if (Flight.buzzDue(frame)) due++;
        assertEquals(10, due);
        assertTrue(Flight.buzzDue(8));
    }
}
