package com.openggf.stageplay.route;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

import com.openggf.sprites.playable.route.NativeRouteSteering;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ROM-free compatibility checks for the Stageplay adapters and their command switch. */
class TestStageplayNativeRouteSteering {
    @Test
    void legacyConstantsAndMovementAndReturnWrappersRemainCompatible() {
        assertEquals(0x180, StageplayRouteSteering.STROLL_CAP);
        assertEquals(24, StageplayRouteSteering.CHARGE_FRAMES);
        assertEquals(PuppetCommand.SPINDASH_CHARGE_FRAMES, StageplayRouteSteering.CHARGE_FRAMES);
        assertEquals(24.0, StageplayRouteSteering.OVERSHOOT_PIXELS);
        assertEquals(StageplayRouteSteering.STROLL_CAP, NativeRouteSteering.STROLL_CAP);
        assertEquals(StageplayRouteSteering.CHARGE_FRAMES, NativeRouteSteering.CHARGE_FRAMES);
        assertEquals(StageplayRouteSteering.OVERSHOOT_PIXELS, NativeRouteSteering.OVERSHOOT_PIXELS);
        assertMovement(-1, 0x300, false, 400, 0);
        assertMovement(0, 0x40, false, 400, 0);
        // Air countersteer needs to begin sooner than ground braking at the same speed.
        assertMovement(1, 0x100, false, 110, 1);
        assertMovement(-1, 0x100, true, 110, 1);
        assertFalse(StageplayRouteSteering.returning(117.99, 100, 1, false));
        assertTrue(StageplayRouteSteering.returning(118, 100, 1, false));
        assertEquals(NativeRouteSteering.returning(82, 100, -1, false),
                StageplayRouteSteering.returning(82, 100, -1, false));
        assertTrue(StageplayRouteSteering.returning(70, 100, 1, true));
    }

    @Test
    void directionAdapterMapsEveryTravelAndEndingSettingToTheNeutralPolicy() {
        var cases = List.of(
                new DirectionCase("Walk gentle coasts", motion(395, 0x100, 0x100, 0, false),
                        TravelSpeed.WALK, EndingAction.GENTLE_STOP, false, 0),
                new DirectionCase("Walk hard brakes above its cap", motion(100, 0x200, 0x200, 0, false),
                        TravelSpeed.WALK, EndingAction.HARD_STOP, false, -1),
                new DirectionCase("Walk overshoot continues past the goal", motion(411, 0, 0, 0, false),
                        TravelSpeed.WALK, EndingAction.OVERSHOOT, false, 1),
                new DirectionCase("Run gentle reserves air headroom", motion(100, 0x5F0, 0, 0, true),
                        TravelSpeed.RUN, EndingAction.GENTLE_STOP, false, 0),
                new DirectionCase("Run hard accelerates above the Walk cap", motion(100, 0x200, 0x200, 0, false),
                        TravelSpeed.RUN, EndingAction.HARD_STOP, false, 1),
                new DirectionCase("Run overshoot returns to the goal", motion(411, 0, 0, 0, false),
                        TravelSpeed.RUN, EndingAction.OVERSHOOT, true, -1));
        for (var sample : cases) {
            var actor = sample.actor();
            // Starting action and ending facing remain Stageplay-only settings.
            var route = new RouteMotion(StartAction.JUMP, sample.travel(), sample.ending(), EndingFacing.LEFT);
            var settings = new NativeRouteSteering.Settings(
                    NativeRouteSteering.TravelSpeed.valueOf(sample.travel().name()),
                    NativeRouteSteering.EndingAction.valueOf(sample.ending().name()));
            int actual = StageplayRouteSteering.direction(actor.x(), actor.xSpeed(), actor.groundSpeed(),
                    actor.runAccel(), actor.runDecel(), actor.friction(), actor.maxSpeed(), actor.slopeImpulse(),
                    actor.airborne(), 400, 1, route, sample.returning(), 1);
            assertEquals(sample.expected(), actual, sample.name());
            assertEquals(NativeRouteSteering.direction(actor.x(), actor.xSpeed(), actor.groundSpeed(),
                    actor.runAccel(), actor.runDecel(), actor.friction(), actor.maxSpeed(), actor.slopeImpulse(),
                    actor.airborne(), 400, 1, settings, sample.returning(), 1), actual, sample.name());
        }
    }

    @Test
    void movementCommandsKeepFlooringResolvedTargetsUrgencyAndTheReturnLatch() {
        var overshoot = new RouteMotion(StartAction.WALK, TravelSpeed.RUN,
                EndingAction.OVERSHOOT, EndingFacing.INHERIT);
        for (var kind : List.of(PuppetCommandKind.MOVE_TO, PuppetCommandKind.FLY_TO, PuppetCommandKind.GLIDE_TO)) {
            // Authored x=500 resolves to 104.5. Flooring leaves 4.5 pixels to travel;
            // steering from the fractional actor position would instead stop.
            assertEquals(new StageplayRouteSteering.Buttons(1, false, true, false, false),
                    StageplayRouteSteering.input(command(kind, RouteMotion.NATIVE, 1), 7,
                            motion(100.75, 0, 0, 0, false), true, false, 1, 104.5), kind.toString());
            assertEquals(new StageplayRouteSteering.Buttons(0, false, true, false, false),
                    StageplayRouteSteering.input(command(kind, RouteMotion.NATIVE, .5), 7,
                            motion(100, 0x300, 0x300, 0, false), true, false, 1, 500), kind + ": urgency");
            var command = command(kind, overshoot, 1);
            assertEquals(new StageplayRouteSteering.Buttons(1, false, false, false, false),
                    StageplayRouteSteering.input(command, 1, motion(117.99, 0, 0, 0, false),
                            false, false, 1, 100), kind + ": before threshold");
            assertEquals(new StageplayRouteSteering.Buttons(-1, false, false, true, false),
                    StageplayRouteSteering.input(command, 2, motion(118.01, 0, 0, 0, false),
                            false, false, 1, 100), kind + ": right threshold");
            assertEquals(new StageplayRouteSteering.Buttons(1, false, false, true, false),
                    StageplayRouteSteering.input(command, 2, motion(82.9, 0, 0, 0, false),
                            false, false, -1, 100), kind + ": floored left threshold");
            assertEquals(new StageplayRouteSteering.Buttons(1, false, false, true, false),
                    StageplayRouteSteering.input(command, 3, motion(95.75, 0, 0, 0, false),
                            false, true, 1, 100), kind + ": return remains latched");
        }
    }

    @Test
    void stopReleasesForGentleEndingAndCountersteersForHardEnding() {
        var gentle = command(PuppetCommandKind.STOP, RouteMotion.DEFAULT, 0);
        var hard = command(PuppetCommandKind.STOP, RouteMotion.NATIVE, 0);
        for (int speed : List.of(0x300, -0x300)) {
            var actor = motion(100.75, speed, speed, 0, false);
            assertEquals(new StageplayRouteSteering.Buttons(0, false, true, true, false),
                    StageplayRouteSteering.input(gentle, 0, actor, true, true, 1, 500));
            assertEquals(new StageplayRouteSteering.Buttons(speed > 0 ? -1 : 1, false, true, true, false),
                    StageplayRouteSteering.input(hard, 0, actor, true, true, 1, 500));
        }
    }

    @Test
    void chargeRecordAdapterAndSpindashDispatchPreserveNativeGatesAndTheChargeWindow() {
        var cases = List.of(
                new ChargeCase("facing tap", 0, false, false, false,
                        new StageplayRouteSteering.Buttons(1, false, false, false, false)),
                new ChargeCase("wait for Duck", 1, false, false, false,
                        new StageplayRouteSteering.Buttons(0, true, false, false, false)),
                new ChargeCase("airborne gate", 2, true, true, false,
                        new StageplayRouteSteering.Buttons(0, true, false, false, false)),
                new ChargeCase("grounded Duck admits jump", 3, false, true, false,
                        new StageplayRouteSteering.Buttons(0, true, true, false, false)),
                new ChargeCase("existing charge releases jump", 4, false, true, true,
                        new StageplayRouteSteering.Buttons(0, true, false, false, false)),
                new ChargeCase("twenty ticks is still charging", 20, false, true, false,
                        new StageplayRouteSteering.Buttons(0, true, true, false, false)),
                new ChargeCase("last charge tick", 24, false, true, false,
                        new StageplayRouteSteering.Buttons(0, true, true, false, false)),
                new ChargeCase("release", 25, false, true, false,
                        new StageplayRouteSteering.Buttons(0, false, false, false, true)));
        var command = command(PuppetCommandKind.SPINDASH, RouteMotion.NATIVE, 1);
        for (var sample : cases) {
            var actual = StageplayRouteSteering.charge(100, 200, sample.elapsed(), 0,
                    sample.airborne(), sample.crouching(), sample.spindash(), 12, 0);
            var neutral = NativeRouteSteering.charge(100, 200, sample.elapsed(), 0,
                    sample.airborne(), sample.crouching(), sample.spindash(), 12, 0);
            assertEquals(sample.expected(), actual, sample.name());
            assertEquals(new StageplayRouteSteering.Buttons(neutral.direction(), neutral.down(), neutral.jump(),
                    neutral.returning(), neutral.finished()), actual, sample.name() + ": record conversion");
            var actor = new StageplayRouteSteering.Motion(100, 0, 0, 12, 128, 12, 0x600, 0,
                    sample.airborne(), sample.crouching(), sample.spindash());
            // Charge owns jump even when the scheduled jump disagrees; the route latch belongs to the caller.
            var expected = sample.expected();
            assertEquals(new StageplayRouteSteering.Buttons(expected.direction(), expected.down(),
                            expected.jump(), true, expected.finished()),
                    StageplayRouteSteering.input(command, sample.elapsed(), actor, !expected.jump(),
                            true, 1, 200), sample.name() + ": command dispatch");
        }
    }

    @Test
    void faceAndPassiveCommandsKeepTheirExistingButtonAndSchedulingSemantics() {
        var actor = motion(100.75, 0x300, 0x300, 0, false);
        assertEquals(new StageplayRouteSteering.Buttons(-1, false, true, true, true),
                StageplayRouteSteering.input(command(PuppetCommandKind.FACE_LEFT, RouteMotion.DEFAULT, 0),
                        0, actor, true, true, 1, 500));
        assertEquals(new StageplayRouteSteering.Buttons(1, false, true, true, true),
                StageplayRouteSteering.input(command(PuppetCommandKind.FACE_RIGHT, RouteMotion.DEFAULT, 0),
                        0, actor, true, true, 1, 500));
        for (var kind : List.of(PuppetCommandKind.HOVER, PuppetCommandKind.CROUCH,
                PuppetCommandKind.LOOK_UP, PuppetCommandKind.ROLL, PuppetCommandKind.WAIT)) {
            assertEquals(new StageplayRouteSteering.Buttons(-1, false, true, true, false),
                    StageplayRouteSteering.input(command(kind, RouteMotion.DEFAULT, 0),
                            12, actor, true, true, 1, 500), kind.toString());
        }
        for (var kind : List.of(PuppetCommandKind.JUMP, PuppetCommandKind.RELEASE)) {
            for (boolean jump : List.of(false, true)) {
                assertEquals(new StageplayRouteSteering.Buttons(0, false, jump, true, false),
                        StageplayRouteSteering.input(command(kind, RouteMotion.NATIVE, 0),
                                0, actor, jump, true, 1, 500), kind + ": scheduled jump=" + jump);
            }
        }
    }

    private static void assertMovement(int expected, int xSpeed, boolean airborne, double targetX, double urgency) {
        int actual = StageplayMovementIntent.direction(100, xSpeed, 12, 128, 0x600,
                airborne, targetX, urgency);
        assertEquals(expected, actual);
        assertEquals(NativeRouteSteering.movementDirection(100, xSpeed, 12, 128, 0x600,
                airborne, targetX, urgency), actual, "movement wrapper equivalence");
    }

    private static StageplayRouteSteering.Motion motion(double x, int xSpeed, int groundSpeed,
            int slopeImpulse, boolean airborne) {
        return new StageplayRouteSteering.Motion(x, xSpeed, groundSpeed, 12, 128, 12, 0x600,
                slopeImpulse, airborne, false, false);
    }

    private static PuppetCommand command(PuppetCommandKind kind, RouteMotion motion, double urgency) {
        boolean movement = kind == PuppetCommandKind.MOVE_TO || kind == PuppetCommandKind.FLY_TO
                || kind == PuppetCommandKind.GLIDE_TO || kind == PuppetCommandKind.SPINDASH;
        int hold = switch (kind) {
            case SPINDASH -> PuppetCommand.SPINDASH_CHARGE_FRAMES;
            case HOVER, CROUCH, LOOK_UP, JUMP -> 12;
            default -> 0;
        };
        return new PuppetCommand("steering-test", 0, kind, movement ? 500 : 0, 0, urgency, hold,
                ObjectTarget.none(), kind == PuppetCommandKind.WAIT ? WaitCondition.GROUNDED : WaitCondition.NONE,
                kind == PuppetCommandKind.WAIT ? 60 : 0, motion);
    }

    private record DirectionCase(String name, StageplayRouteSteering.Motion actor, TravelSpeed travel,
            EndingAction ending, boolean returning, int expected) { }

    private record ChargeCase(String name, int elapsed, boolean airborne, boolean crouching,
            boolean spindash, StageplayRouteSteering.Buttons expected) { }
}
