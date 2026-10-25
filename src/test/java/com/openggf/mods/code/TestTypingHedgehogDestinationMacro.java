package com.openggf.mods.code;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayablePuppet;
import org.junit.jupiter.api.Test;

import static com.openggf.mods.code.ForwardRails.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Destination suggestions select native buttons; qualification still belongs to the rail search. */
class TestTypingHedgehogDestinationMacro {
    @Test void steersInBothDirectionsAndBrakesBeforeTheDestination() {
        var right = waypoint(200, 100, 0, 120);
        assertEquals(RIGHT, right.next(0, facts(100, 100, 0, 0, false)));
        assertEquals(LEFT, right.next(1, facts(160, 100, 0x800, 0x800, false)),
                "Native stopping distance requires braking before entering the waypoint envelope");
        var left = waypoint(100, 100, 0, 120);
        assertEquals(LEFT, left.next(0, facts(200, 100, 0, 0, false)));
        assertEquals(RIGHT, left.next(1, facts(140, 100, -0x800, -0x800, false)));
    }

    @Test void arrivalUsesBothAuthoredCoordinatesAndDoesNotRequireAStop() {
        var macro = waypoint(200, 100, 0, 120);
        assertNotEquals(-1, macro.next(0, facts(180, 150, 0x300, 0x300, false)),
                "Being at the right x on another route height does not reach the waypoint");
        assertEquals(-1, macro.next(1, facts(180, 120, 0x300, 0x300, true)),
                "The existing 32 px waypoint envelope accepts airborne travel through it");
        assertFalse(macro.settled, "A passed waypoint is not a qualified fight stop");
        var alreadyThere = waypoint(200, 100, 0, 120);
        assertEquals(0, alreadyThere.next(0, facts(200, 100, 0, 0, false)),
                "The search must observe one native row to advance its waypoint counter");
        assertEquals(-1, alreadyThere.next(1, facts(200, 100, 0, 0, false)));
        assertEquals(-1, waypoint(200, 100, 0, 120).next(1, facts(232.75, 132.75, 0, 0, false)),
                "Waypoint acceptance uses native whole-pixel centres like the search's per-row gate");
    }

    @Test void jumpVariantReleasesItsNativeButtonAndAirBrakingUsesLiveAcceleration() {
        var macro = waypoint(200, 100, 4, 120);
        var airborne = facts(100, 150, 0x600, 0, true);
        assertEquals(LEFT | JUMP, macro.next(0, airborne),
                "Air braking uses twice the live acceleration, not ground deceleration");
        assertEquals(LEFT, macro.next(4, airborne), "A bounded press releases jump for native variable height");
        var quickAirBrake = new PlayablePuppet.Observation(100, 150, 0x600, 0, 0,
                0, 0, 0, false, false, true, false, 0xC00, 9, 19, null,
                0x100, 0x80, 0xC, false, false, 0, false);
        assertEquals(RIGHT, waypoint(200, 100, 0, 120).next(0, quickAirBrake),
                "Changed native acceleration changes the policy's stopping distance");
    }

    @Test void plannedStopNeedsTwelveConsecutiveStillGroundedObservationsAtItsDestination() {
        var macro = new TypingHedgehogForwardRailBuilder.DestinationMacro(200, Double.NaN, true, 0, 240);
        assertEquals(LEFT, macro.next(0, facts(170, 100, 0x800, 0x800, false)));
        for (int i = 1; i <= 6; i++) assertEquals(0, macro.next(i, facts(200, 100, 0, 0, false)));
        assertNotEquals(-1, macro.next(7, facts(200, 100, 0, 0, true)), "Airborne zero speed resets settling");
        for (int i = 8; i <= 18; i++) assertEquals(0, macro.next(i, facts(200, 300, 0, 0, false)));
        assertEquals(-1, macro.next(19, facts(200, 300, 0, 0, false)));
        assertTrue(macro.settled, "Terrain height is discovered natively for a scheduled x stop");
    }

    @Test void aStationaryActorElsewhereOrADeadlineCannotCertifyAPlannedStop() {
        var macro = new TypingHedgehogForwardRailBuilder.DestinationMacro(200, Double.NaN, true, 0, 24);
        for (int i = 0; i < 24; i++) assertEquals(RIGHT, macro.next(i, facts(100, 100, 0, 0, false)));
        assertEquals(-1, macro.next(24, facts(200, 100, 0, 0, false)));
        assertFalse(macro.settled, "Budget exhaustion is not arrival or site qualification");
        assertEquals(-1, waypoint(200, 100, 0, 12).next(12, facts(100, 100, 0, 0, false)));
    }

    @Test void spriteAdapterOnlyObservesLiveMovementAndUsesNativeRollingDeceleration() {
        var sprite = mock(AbstractPlayableSprite.class);
        when(sprite.getCentreX()).thenReturn((short) 100);
        when(sprite.getCentreY()).thenReturn((short) 100);
        when(sprite.getXSpeed()).thenReturn((short) 0x500);
        when(sprite.getGSpeed()).thenReturn((short) 0x500);
        when(sprite.getRunAccel()).thenReturn((short) 0xC);
        when(sprite.getRunDecel()).thenReturn((short) 0x80);
        when(sprite.getRollDecel()).thenReturn((short) 0x20);
        when(sprite.getMax()).thenReturn((short) 0x600);
        when(sprite.getFriction()).thenReturn((short) 0xC);
        assertEquals(RIGHT, waypoint(200, 100, 0, 120).next(0, sprite));
        when(sprite.getRolling()).thenReturn(true);
        assertEquals(LEFT, waypoint(200, 100, 0, 120).next(0, sprite),
                "A rolling actor must brake earlier using its observed native roll deceleration");
        assertTrue(mockingDetails(sprite).getInvocations().stream()
                .noneMatch(call -> call.getMethod().getName().startsWith("set")), "Steering never writes actor state");
    }

    @Test void scheduledDestinationsUseExistingFightFractionsAndForwardSpacing() {
        var node = new TypingHedgehogForwardRailBuilder.Node();
        node.x = 100;
        assertEquals(100 + TypingHedgehogForwardRailBuilder.FIRST_FIGHT_PX / 2,
                TypingHedgehogForwardRailBuilder.plannedStopX(node, 100, 8100));
        node.fights = 2;
        node.lastFightX = 2200;
        assertEquals(3700, TypingHedgehogForwardRailBuilder.plannedStopX(node, 100, 8100));
        node.lastFightX = 3700;
        assertEquals(3956, TypingHedgehogForwardRailBuilder.plannedStopX(node, 100, 8100));
        node.x = 4100;
        assertEquals(4164, TypingHedgehogForwardRailBuilder.plannedStopX(node, 100, 8100),
                "A missed preferred position suggests a new forward site without weakening the schedule gate");
    }

    private static TypingHedgehogForwardRailBuilder.DestinationMacro waypoint(int x, int y, int jump, int limit) {
        return new TypingHedgehogForwardRailBuilder.DestinationMacro(x, y, false, jump, limit);
    }

    private static PlayablePuppet.Observation facts(double x, double y, int xs, int gs, boolean airborne) {
        return new PlayablePuppet.Observation(x, y, xs, 0, gs, 0, 0, 0, false, false,
                airborne, !airborne, 0x600, 9, 19, null, 0xC, 0x80, 0xC, false, false, 0, false);
    }
}
