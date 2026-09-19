package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_56EA0} (sonic3k.asm:115568-115631): the dome region's lava surface.
 *
 * <p>Every expectation is a ROM write or a ROM branch: the fixed {@code x_pos $1E80}, the
 * {@code $988} base the negated phase is subtracted from, the {@code ±$C000} velocity reload and
 * {@code $100} step, the {@code SolidObjectTop} parameters {@code $280/$80/$6C}, and the fact
 * that the phase restarts at zero because {@code clr.w (_unkEE9C).w} is in the init.
 */
class TestLrzDomeLavaPlatform {

    private TestObjectServices services;
    private LrzZoneRuntimeState state;

    @BeforeEach
    void setUp() {
        AbstractObjectInstance.updateCameraBounds(0x1C00, 0x800, 0x2200, 0xA00, 0);
        state = new LrzZoneRuntimeState(Sonic3kZoneIds.ZONE_LRZ, 0, PlayerCharacter.SONIC_ALONE);
        state.setDomeRegionLocked(true);
        services = new TestObjectServices().withIsolatedObjectManager();
        services.zoneRuntimeRegistry().install(state);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    /** The init writes, and the fact that the surface starts on its base row. */
    @Test
    void theInitWritesAreTheRomsOwn() {
        LrzDomeLavaPlatformObjectInstance lava = platform();
        assertEquals(0x1E80, lava.getCentreX(), "move.w #$1E80,x_pos(a0)");
        assertEquals(0x988, lava.getCentreY(), "$988 with a zero phase");
        assertEquals(0, state.domePlatformPhase(), "clr.w (_unkEE9C).w");
        assertEquals(0x280, lava.getSolidParams().halfWidth(), "move.w #$280,d1");
        assertEquals(0x80, lava.getSolidParams().airHalfHeight(), "move.w #$80,d2");
        assertEquals(0x6C, lava.getSolidParams().groundHalfHeight(), "move.w #$6C,d3");
        assertEquals(-0x4000, LrzDomeLavaPlatformObjectInstance.unusedField34(),
                "move.l #-$4000,$34(a0), written and never read");
    }

    /**
     * {@code loc_56EF6}: the very first dispatch adds a zero velocity to a zero position, takes
     * the {@code beq} into {@code loc_56EFC}, and turns the surface round with {@code -$C000}.
     */
    @Test
    void theFirstDispatchTurnsTheSurfaceUpward() {
        LrzDomeLavaPlatformObjectInstance lava = platform();
        lava.update(1, null);
        assertTrue(lava.rising(), "st $36(a0)");
        assertEquals(-0xC000, lava.velocity(), "move.l #-$C000,d1");
        assertEquals(0, state.domePlatformPhase(), "the position was parked at zero first");
    }

    /**
     * {@code loc_56F0A}: while rising the position accumulates the negative velocity and the
     * velocity is eased by {@code $100} a frame, and the published phase is the position's HIGH
     * word, so {@code y_pos} climbs a pixel at a time.
     */
    @Test
    void theSurfaceRisesOnTheAccumulatedHighWord() {
        LrzDomeLavaPlatformObjectInstance lava = platform();
        lava.update(1, null);
        int lastY = lava.getCentreY();
        boolean sawTheSurfaceMove = false;
        for (int frame = 0; frame < 120; frame++) {
            lava.update(frame + 2, null);
            assertEquals((0x988 - (short) state.domePlatformPhase()) & 0xFFFF, lava.getCentreY(),
                    "y_pos = $988 - _unkEE9C at frame " + frame);
            if (lava.getCentreY() != lastY) {
                sawTheSurfaceMove = true;
            }
            lastY = lava.getCentreY();
        }
        assertTrue(sawTheSurfaceMove, "120 frames must move the surface off its base row");
        assertNotEquals(0, state.domePlatformPhase(), "_unkEE9C must have been published");
    }

    /** {@code loc_56EC2}: the surface's whole life is {@code Events_bg+$00}. */
    @Test
    void clearingTheRegionLockRetiresTheSurface() {
        LrzDomeLavaPlatformObjectInstance lava = platform();
        lava.update(1, null);
        assertFalse(lava.isDestroyed(), "still locked");
        state.setDomeRegionLocked(false);
        lava.update(2, null);
        assertTrue(lava.isDestroyed(), "jmp (Delete_Current_Sprite).l");
    }

    private LrzDomeLavaPlatformObjectInstance platform() {
        LrzDomeLavaPlatformObjectInstance lava = new LrzDomeLavaPlatformObjectInstance(
                new ObjectSpawn(LrzDomeLavaPlatformObjectInstance.FIXED_X,
                        LrzDomeLavaPlatformObjectInstance.SURFACE_BASE_Y, 0, 0, 0, false, 0));
        lava.setServices(services);
        return lava;
    }
}
