package com.openggf.game.sonic1.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic1CollapsingFloorWalkOff {

    @Test
    void staleFragmentUsesRomExitPlatformBounds() {
        assertFalse(Sonic1CollapsingFloorObjectInstance.exitsPlatform(false,
                0x1D7F, 0x1D60));
        assertTrue(Sonic1CollapsingFloorObjectInstance.exitsPlatform(false,
                0x1D80, 0x1D60), "ExitPlatform's right edge is exclusive");
        assertTrue(Sonic1CollapsingFloorObjectInstance.exitsPlatform(true,
                0x1D60, 0x1D60), "an airborne player exits even inside the X bounds");
    }

    @Test
    void airborneStandingRoutineRunsExitPlatformInsteadOfRelanding() {
        Sonic1CollapsingFloorObjectInstance floor =
                new Sonic1CollapsingFloorObjectInstance(
                        new ObjectSpawn(0x1DA0, 0x0568, 0x53, 0, 0, false, 0), 5);
        TestPlayableSprite player = new TestPlayableSprite();

        assertFalse(floor.airborneStaleStandingBitReturnsNoContact(player));
        floor.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
        player.setAir(true);

        assertTrue(floor.airborneStaleStandingBitReturnsNoContact(player),
                "routine 4 calls ExitPlatform, which returns before PlatformObject can re-land");
        floor.onSolidContactCleared(player, 1);
        assertFalse(floor.airborneStaleStandingBitReturnsNoContact(player),
                "ExitPlatform resets the object to routine 2");
    }

    @Test
    void groundedWalkOffAlsoResetsTheFloorToRoutineTwo() {
        // ExitPlatform `.exitedPlatform` sets obRoutine = 2 for an out-of-band
        // grounded Sonic too, so the next CFlo_ChkTouch fragments with the flag cleared.
        Sonic1CollapsingFloorObjectInstance floor =
                new Sonic1CollapsingFloorObjectInstance(
                        new ObjectSpawn(0x1320, 0x05E8, 0x53, 0, 0, false, 0), 4);
        TestPlayableSprite player = new TestPlayableSprite();
        floor.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
        player.setAir(false);
        assertTrue(floor.airborneStaleStandingBitReturnsNoContact(player), "routine 4");

        TestPlayableSprite sidekick = new TestPlayableSprite();
        sidekick.setCpuControlled(true);
        floor.onSolidContactCleared(sidekick, 1);
        assertTrue(floor.airborneStaleStandingBitReturnsNoContact(player),
                "ExitPlatform reads v_player only");

        floor.onSolidContactCleared(player, 1);
        assertFalse(floor.airborneStaleStandingBitReturnsNoContact(player),
                "a grounded walk-off resets the object to routine 2");
    }
}
