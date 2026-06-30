package com.openggf.tests.trace;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.objects.ARZPlatformObjectInstance;
import com.openggf.game.sonic2.objects.ArrowProjectileInstance;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.BreathingBubbleInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Comparison-only measurement and assertion of the engine's dynamic-slot
 * occupancy against the ROM trace timeline using {@link ObjectOccupancyOracle}.
 *
 * <p><strong>Self-deleting transient assertion (Task 1.7, piece a).</strong>
 * The green S2 traces (EHZ1, SCZ, WFZ) assert frame-for-frame parity of the
 * live <em>count</em> of the badnik-death explosion (Obj27), whose destroy
 * frame is a fixed {@code anim_frame_duration} countdown: it deletes 35 game
 * frames after spawn in S2/S3K (init 3 / reload 7 / delete at mapping_frame 5 —
 * docs/s2disasm/s2.asm:46672-46684). Piece (a) aligns the engine explosion to
 * that exact frame (previously it lingered 4 frames via a uniform 8-frame
 * delay).
 *
 * <p>The assertion is deliberately scoped two ways. First, by id (see
 * {@link #TRANSIENT_SELF_DELETE_IDS}): the Animal (Obj28) despawns by walk/fly
 * physics and the off-screen {@code MarkObjGone} window (docs/s2disasm/s2.asm
 * Obj28_Walk/Obj28_Fly), and the points popup (Obj29) — already ROM-correct on
 * lifespan — diverges only by a one-frame spawn-windowing offset; both are
 * object-lifetime categories outside piece (a). Second, by <em>count</em>
 * rather than by slot: it compares the number of live Obj27 instances the
 * engine holds against the ROM timeline. A delete that is a frame late leaves
 * engineCount &gt; romCount; a frame early leaves engineCount &lt; romCount.
 * Counting by id ignores spawn-slot-allocation / windowing drift (the engine
 * spawning the same transient into a different slot than the ROM — piece b),
 * which would otherwise mask or fake a transient-timing regression. See
 * {@link ObjectOccupancyOracle#firstTransientCountDivergence}.
 *
 * <p>MTZ1 stays a non-asserting MEASUREMENT: it is a trace frontier (not a green
 * trace), so its occupancy diverges for windowing reasons unrelated to transient
 * timing.
 *
 * <p><strong>Comparison-only invariant:</strong> the oracle and this test read
 * trace data and engine state and report; they never write engine state from
 * the trace.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestS2ObjectOccupancyOracle {

    private static final int FIRST_DYNAMIC_SLOT = ObjectSlotLayout.SONIC_2.firstDynamicSlot();

    /**
     * Self-deleting transient object id whose destroy frame this assertion
     * guards: Obj27, the badnik-death explosion. Its ROM lifespan is a fixed
     * {@code anim_frame_duration} countdown (init 3 / reload 7 / delete at
     * mapping_frame 5 in S2/S3K) = 35 game frames from spawn; piece (a) aligns
     * the engine to that exact frame.
     *
     * <p>Obj29 (the floating points popup) is deliberately NOT in scope even
     * though its self-delete logic is also fixed-countdown and the engine
     * already matches the ROM lifespan exactly (32 frames: delete when
     * {@code y_vel >= 0}, docs/s2disasm/s2.asm Obj29_Main). Its per-frame count
     * still diverges by one frame in some green traces (e.g. EHZ1 f1308: ROM
     * spawns the points at f1309, the engine at f1308) because the engine spawns
     * it one frame off the ROM {@code AllocateObject} ordering — a
     * spawn-slot-windowing offset (piece b), not a delete-frame error. Including
     * Obj29 would make this assertion red for a reason outside piece (a).
     */
    private static final Set<Integer> TRANSIENT_SELF_DELETE_IDS = Set.of(0x27);

    @Test
    public void measureHtz1OccupancyDivergence() throws Exception {
        ObjectOccupancyOracle.Divergence first =
                measureFirstDivergence("htz", Sonic2ZoneConstants.ZONE_HTZ, 0, null);
        if (first == null) {
            System.out.println(
                    "[occupancy-oracle] HTZ1: no dynamic-slot occupancy divergence "
                            + "across the trace.");
        } else {
            System.out.printf(
                    "[occupancy-oracle] HTZ1 first divergence: frame=%d slot=%d "
                            + "expectedId=0x%02X actualId=0x%02X%n",
                    first.frame(), first.slot(),
                    first.expectedId() & 0xFF, first.actualId() & 0xFF);
        }
        // Measurement only: HTZ1 is a trace frontier, not a green trace.
    }

    @Test
    public void measureMtz1OccupancyDivergence() throws Exception {
        ObjectOccupancyOracle.Divergence first =
                measureFirstDivergence("mtz", Sonic2ZoneConstants.ZONE_MTZ, 0, null);
        if (first == null) {
            System.out.println(
                    "[occupancy-oracle] MTZ1: no dynamic-slot occupancy divergence "
                            + "across the trace.");
        } else {
            System.out.printf(
                    "[occupancy-oracle] MTZ1 first divergence: frame=%d slot=%d "
                            + "expectedId=0x%02X actualId=0x%02X%n",
                    first.frame(), first.slot(),
                    first.expectedId() & 0xFF, first.actualId() & 0xFF);
        }
        // Measurement only: MTZ1 is a trace frontier, not a green trace.
    }

    @Test
    public void mtz1RespawnTrackedBadnikKillDoesNotReloadThroughPlacementWindow() throws Exception {
        Integer slot21Id = driveTrace("mtz", Sonic2ZoneConstants.ZONE_MTZ, 0,
                (trace, om, frame) -> {
                    if (frame != 1168) {
                        return null;
                    }
                    Map<Integer, Integer> expected =
                            ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT);
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    Assertions.assertEquals(0x74, expected.get(21),
                            "ROM fixture should load the invisible block into slot 21 at MTZ1 f1168");
                    return actual.get(21);
                });
        Assertions.assertNotNull(slot21Id);
        Assertions.assertEquals(0x74, slot21Id,
                "S2 ChkLoadObj must skip the killed respawn-tracked Asteron at x=$0720 "
                        + "so the next streamed object takes slot 21");
    }

    @Test
    public void htz2Obj18StandingBitClearsWhenLaterRideWinsAtRomFrame4011() throws Exception {
        Boolean obj18Standing = driveTrace("htz2", Sonic2ZoneConstants.ZONE_HTZ, 1,
                (trace, om, frame) -> {
                    if (frame != 4011) {
                        return null;
                    }
                    TraceEvent.ObjectNear expectedObj18 = trace.getEventsForFrame(frame).stream()
                            .filter(TraceEvent.ObjectNear.class::isInstance)
                            .map(TraceEvent.ObjectNear.class::cast)
                            .filter(near -> near.slot() == 34)
                            .filter(near -> parseObjectType(near.objectType()) == 0x18)
                            .findFirst()
                            .orElse(null);
                    Assertions.assertNotNull(expectedObj18,
                            "HTZ2 ROM fixture should report Obj18 slot 34 at f4011");
                    Assertions.assertEquals(0, parseObjectType(expectedObj18.status()) & 0x18,
                            "ROM Obj18 standing bits are clear before slot 22 becomes the active ride");
                    ARZPlatformObjectInstance actualObj18 =
                            om.activeObjectsOfType(ARZPlatformObjectInstance.class).stream()
                                    .filter(platform -> platform.getX() == 0x1860)
                                    .findFirst()
                                    .orElse(null);
                    Assertions.assertNotNull(actualObj18,
                            "Engine should have the HTZ2 Obj18 platform at x=$1860 by f4011");
                    AbstractPlayableSprite sonic =
                            (AbstractPlayableSprite) GameServices.sprites().getSprite("sonic");
                    return om.hasObjectStandingBit(sonic, actualObj18);
                });
        Assertions.assertNotNull(obj18Standing);
        Assertions.assertFalse(obj18Standing,
                "RideObject_SetRide must clear the previous object's standing bit when a later object "
                        + "wins the ride (docs/s2disasm/s2.asm:35999-36006)");
    }

    @Test
    public void mtz3RotatingPlatformLoadKeepsRomSlot22Identity() throws Exception {
        SlotCheck slotCheck = driveTrace("mtz3", Sonic2ZoneConstants.ZONE_MTZ, 2,
                (trace, om, frame) -> {
                    if (frame != 1556) {
                        return null;
                    }
                    Map<Integer, Integer> expected =
                            ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT);
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    Assertions.assertEquals(0x6E, expected.get(22),
                            "ROM fixture should load the MTZ large rotating platform into slot 22 at MTZ3 f1556");
                    return new SlotCheck(actual.get(22), describeSlots(actual, 16, 35));
                });
        Assertions.assertNotNull(slotCheck);
        Assertions.assertEquals(0x6E, slotCheck.actualId(),
                "MTZ3 slot 22 must remain the ROM Obj6E platform slot because "
                        + "TailsCPU_UpdateObjInteract dereferences interact(a0)=0x16 live; actual slots "
                        + slotCheck.summary());
    }

    @Test
    public void mtz3MovingPlatformUnloadReleasesRomSlot17() throws Exception {
        SlotCheck slotCheck = driveTrace("mtz3", Sonic2ZoneConstants.ZONE_MTZ, 2,
                (trace, om, frame) -> {
                    if (frame != 555) {
                        return null;
                    }
                    Map<Integer, Integer> expected =
                            ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT);
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    Assertions.assertNull(expected.get(17),
                            "ROM fixture should unload MTZ Obj6A from slot 17 at MTZ3 f555");
                    return new SlotCheck(actual.get(17), describeSlots(actual, 16, 24));
                });
        Assertions.assertNotNull(slotCheck);
        Assertions.assertNull(slotCheck.actualId(),
                "MTZ Obj6A must unload from its ROM slot when objoff_32 leaves "
                        + "the MarkObjGone2 window; actual slots " + slotCheck.summary());
    }

    @Test
    public void mtz3TwinStomperNoContactClearsTailsPushAtRomFrame1743() throws Exception {
        PushCheck pushCheck = driveTrace("mtz3", Sonic2ZoneConstants.ZONE_MTZ, 2,
                (trace, om, frame) -> {
                    if (frame != 1743) {
                        return null;
                    }
                    TraceFrame expected = trace.getFrame(frame);
                    Assertions.assertNotNull(expected.sidekick(),
                            "MTZ3 trace row f1743 must include Tails state");
                    Assertions.assertEquals(0, expected.sidekick().statusByte() & 0x20,
                            "ROM fixture should have cleared Tails Status_Push at MTZ3 f1743");
                    Assertions.assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                            "Engine fixture must have a CPU Tails sidekick at MTZ3 f1743");
                    AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
                    return new PushCheck(tails.getPushing(), tails.getCentreX(), tails.getCentreY(),
                            describeSlots(om.occupiedDynamicSlotIds(), 24, 28));
                });
        Assertions.assertNotNull(pushCheck);
        Assertions.assertFalse(pushCheck.pushing(),
                "S2 SolidObject_TestClearPush must clear Tails Status_Push when Obj64 "
                        + "is no longer contacting Tails at MTZ3 f1743; tails=("
                        + String.format("%04X,%04X", pushCheck.tailsX(), pushCheck.tailsY())
                        + ") nearby slots " + pushCheck.summary());
    }

    @Test
    public void mcz2Obj75SpikeBallParentAndDisplayChildSurviveUntilTailsHit() throws Exception {
        SlotCheck slotCheck = driveTrace("mcz2", Sonic2ZoneConstants.ZONE_MCZ, 1,
                (trace, om, frame) -> {
                    if (frame != 6429) {
                        return null;
                    }
                    ObjectSpawnState state = obj75Mcz2SpikeBallState(om);
                    boolean matches = state != null
                            && state.active()
                            && !state.dormant()
                            && state.liveCount() >= 2;
                    return matches ? null : new SlotCheck(-1,
                            describeSlots(om.occupiedDynamicSlotIds(), 24, 36) + " | "
                                    + describeObj75Mcz2SpawnState(om));
                });
        Assertions.assertNull(slotCheck,
                () -> "MCZ2 Obj75 spike-ball parent/display child was not live at f6429; actual slots "
                        + slotCheck.summary());
    }

    @Test
    public void cnz2VerticalFlipperRightEdgeKeepsTailsPushBeforeCpuFollowAtRomFrame7983() throws Exception {
        PushCheck pushCheck = driveTrace("cnz2", Sonic2ZoneConstants.ZONE_CNZ, 1,
                (trace, om, frame) -> {
                    if (frame != 7983) {
                        return null;
                    }
                    TraceFrame expected = trace.getFrame(frame);
                    Assertions.assertNotNull(expected.sidekick(),
                            "CNZ2 trace row f7983 must include Tails state");
                    Assertions.assertEquals(0x20, expected.sidekick().statusByte() & 0x20,
                            "ROM fixture should have Tails Status_Push set at CNZ2 f7983");
                    Assertions.assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                            "Engine fixture must have a CPU Tails sidekick at CNZ2 f7983");
                    AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
                    return new PushCheck(tails.getPushing(), tails.getCentreX(), tails.getCentreY(),
                            String.format("tails=(x=%04X y=%04X angle=%02X gs=%04X air=%s onObj=%s) slots %s",
                                    tails.getCentreX() & 0xFFFF,
                                    tails.getCentreY() & 0xFFFF,
                                    tails.getAngle() & 0xFF,
                                    tails.getGSpeed() & 0xFFFF,
                                    tails.getAir(),
                                    tails.isOnObject(),
                                    describeSlots(om.occupiedDynamicSlotIds(), 18, 25)));
                });
        Assertions.assertNotNull(pushCheck);
        Assertions.assertTrue(pushCheck.pushing(),
                "S2 Obj86 vertical flipper right-edge contact must leave Tails Status_Push set before "
                        + "the next TailsCPU_Normal follow pass; " + pushCheck.summary());
    }

    private record SlotCheck(Integer actualId, String summary) {
    }

    private record PushCheck(boolean pushing, int tailsX, int tailsY, String summary) {
    }

    private record ObjectSpawnState(boolean active, boolean dormant, int liveCount) {
    }

    private record RideCheck(int expectedY, int actualY, boolean actualAir, boolean actualOnObject) {
    }

    private record DeadRideReleaseCheck(
            boolean onObject,
            boolean air,
            boolean riding,
            boolean objectStandingBit,
            String summary) {
    }

    @Test
    public void mtz1TwinStomperRetractionKeepsRiderOnPreMoveSurfaceAtRomFrame1267() throws Exception {
        RideCheck rideCheck = driveTrace("mtz", Sonic2ZoneConstants.ZONE_MTZ, 0,
                (trace, om, frame) -> {
                    if (frame != 1267) {
                        return null;
                    }
                    TraceFrame expected = trace.getFrame(frame);
                    AbstractPlayableSprite sonic = Assertions.assertInstanceOf(
                            AbstractPlayableSprite.class,
                            GameServices.sprites().getSprite("sonic"),
                            "Engine fixture must have Sonic at MTZ1 f1267");
                    Assertions.assertEquals(0x1E, expected.standOnObj(),
                            "ROM fixture should have Sonic riding Obj64 in slot 30 at MTZ1 f1267");
                    return new RideCheck(expected.y() & 0xFFFF,
                            sonic.getCentreY() & 0xFFFF,
                            sonic.getAir(),
                            sonic.isOnObject());
                });
        Assertions.assertNotNull(rideCheck);
        Assertions.assertEquals(rideCheck.expectedY(), rideCheck.actualY(),
                "S2 Obj64 retraction should keep a continued top rider seated on the "
                        + "pre-update surface for the transition frame");
        Assertions.assertFalse(rideCheck.actualAir());
        Assertions.assertTrue(rideCheck.actualOnObject());
    }

    @Test
    public void mtz3DeadTailsObj6eStaleStandingBitClearsAtRomFrame3618() throws Exception {
        DeadRideReleaseCheck check = driveTrace("mtz3", Sonic2ZoneConstants.ZONE_MTZ, 2,
                (trace, om, frame) -> {
                    if (frame != 3618) {
                        return null;
                    }
                    TraceFrame expected = trace.getFrame(frame);
                    Assertions.assertNotNull(expected.sidekick(),
                            "MTZ3 trace row f3618 must include Tails state");
                    Assertions.assertEquals(0, expected.sidekick().statusByte() & 0x08,
                            "ROM fixture should have cleared Tails Status_OnObj at MTZ3 f3618");
                    Assertions.assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                            "Engine fixture must have a CPU Tails sidekick at MTZ3 f3618");
                    AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
                    ObjectInstance obj6e = om.getActiveObjects().stream()
                            .filter(AbstractObjectInstance.class::isInstance)
                            .map(AbstractObjectInstance.class::cast)
                            .filter(instance -> instance.getSlotIndex() == 16)
                            .filter(instance -> instance.getSpawn() != null
                                    && (instance.getSpawn().objectId() & 0xFF) == 0x6E)
                            .findFirst()
                            .orElse(null);
                    Assertions.assertNotNull(obj6e,
                            "Engine fixture must still have the ridden Obj6E in slot 16 at MTZ3 f3618");
                    return new DeadRideReleaseCheck(
                            tails.isOnObject(),
                            tails.getAir(),
                            om.isRidingObject(tails),
                            om.hasObjectStandingBit(tails, obj6e),
                            describeSlots(om.occupiedDynamicSlotIds(), 16, 18));
                });
        Assertions.assertNotNull(check);
        Assertions.assertFalse(check.onObject(),
                "S2 SolidObject must clear dead Tails' stale Status_OnObj on Obj6E; slots "
                        + check.summary());
        Assertions.assertTrue(check.air());
        Assertions.assertFalse(check.riding(),
                "Dead Tails should not retain an engine riding record after the Obj6E stale clear; slots "
                        + check.summary());
        Assertions.assertFalse(check.objectStandingBit(),
                "Obj6E's sidekick standing bit must clear with Status_OnObj (docs/s2disasm/s2.asm:35022-35044)");
    }

    @Test
    public void arz2ChopChopLoadsIntoRomSlot19AfterBubbleBurstClears() throws Exception {
        SlotCheck slotCheck = driveTrace("arz2", Sonic2ZoneConstants.ZONE_ARZ, 1,
                (trace, om, frame) -> {
                    if (frame != 458) {
                        return null;
                    }
                    Map<Integer, Integer> expected =
                            ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT);
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    Assertions.assertEquals(0x91, expected.get(19),
                            "ROM fixture should load the ARZ2 ChopChop into slot 19 at f458");
                    return new SlotCheck(actual.get(19), describeSlots(actual, 16, 36));
                });
        Assertions.assertNotNull(slotCheck);
        Assertions.assertEquals(0x91, slotCheck.actualId(),
                "ARZ2 Obj91 must take ROM slot 19; lower slots must not be held by "
                        + "stale Obj24 bubble children. Actual slots " + slotCheck.summary());
    }

    @Test
    public void arz2ChopChopAnimalDoesNotMoveOnCreationFrame() throws Exception {
        AnimalPositionCheck check = animalPositionAtArz2Frame(549);
        Assertions.assertNotNull(check);
        Assertions.assertEquals(check.expectedX(), check.actualX(),
                "ARZ2 Obj28 should be born at the Obj27 position on its first DisplaySprite frame; slots "
                        + check.summary());
        Assertions.assertEquals(check.expectedY(), check.actualY(),
                "S2 Obj28_InitRandom branches to DisplaySprite and must not run ObjectMoveAndFall "
                        + "until routine 2 on the next object pass; slots " + check.summary());
    }

    @Test
    public void arz2ChopChopAnimalKeepsObjectMoveAndFallSubpixelCarry() throws Exception {
        AnimalPositionCheck check = animalPositionAtArz2Frame(553);
        Assertions.assertNotNull(check);
        Assertions.assertEquals(check.expectedX(), check.actualX(),
                "S2 Obj28 ObjectMoveAndFall should keep the animal at the Obj27 x-position while popping; slots "
                        + check.summary());
        Assertions.assertEquals(check.expectedY(), check.actualY(),
                "S2 Obj28 ObjectMoveAndFall must apply old y_vel through the ROM subpixel accumulator "
                        + "before gravity; slots " + check.summary());
    }

    @Test
    public void arz2ChopChopAnimalDoesNotWalkOnLandingTransitionFrame() throws Exception {
        AnimalPositionCheck check = animalPositionAtArz2Frame(593);
        Assertions.assertNotNull(check);
        Assertions.assertEquals(check.expectedX(), check.actualX(),
                "S2 Obj28_Main must branch to DisplaySprite after landing and must not run Obj28_Walk/Fly "
                        + "until the next ExecuteObjects pass; slots " + check.summary());
        Assertions.assertEquals(check.expectedY(), check.actualY(),
                "S2 Obj28_Main accepts only negative ObjCheckFloorDist (tst.w d1 / bpl.s DisplaySprite) "
                        + "before snapping and changing routine; slots " + check.summary());
    }

    @Test
    public void arz2ChopChopAnimalCarriesXSubpixelAfterLanding() throws Exception {
        AnimalPositionCheck check = animalPositionAtArz2Frame(595);
        Assertions.assertNotNull(check);
        Assertions.assertEquals(check.expectedX(), check.actualX(),
                "S2 Obj28_Walk calls ObjectMoveAndFall, whose longword x_pos update preserves x_sub "
                        + "(docs/s2disasm/s2.asm:24670-24673,30164-30174); slots " + check.summary());
        Assertions.assertEquals(check.expectedY(), check.actualY(),
                "S2 Obj28_Walk must keep sharing ObjectMoveAndFall vertical carry after landing; slots "
                        + check.summary());
    }

    @Test
    public void arz2ChopChopAnimalFreesSlotWhenRenderFlagClears() throws Exception {
        SlotCheck slotCheck = driveTrace("arz2", Sonic2ZoneConstants.ZONE_ARZ, 1,
                (trace, om, frame) -> {
                    if (frame != 626) {
                        return null;
                    }
                    Map<Integer, Integer> expected =
                            ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT);
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    Assertions.assertEquals(0x0A, expected.get(24),
                            "ROM fixture should reuse slot 24 for the mouth bubble after Obj28 deletes at f626");
                    return new SlotCheck(actual.get(24), describeSlots(actual, 19, 25));
                });
        Assertions.assertNotNull(slotCheck);
        Assertions.assertEquals(0x0A, slotCheck.actualId(),
                "S2 Obj28_Walk deletes when render_flags.on_screen is clear; the animal must free slot 24 "
                        + "for the next AllocateObject bubble (docs/s2disasm/s2.asm:24570-24594,24670-24688). "
                        + "Actual slots " + slotCheck.summary());
    }

    @Test
    public void arz2DynamicSlotOccupancyMatchesThroughArrowShooterStream() throws Exception {
        SlotWindowCheck check = driveTrace("arz2", Sonic2ZoneConstants.ZONE_ARZ, 1,
                (trace, om, frame) -> {
                    if (frame != 687) {
                        return null;
                    }
                    Map<Integer, Integer> expected =
                            ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT);
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    Assertions.assertEquals(0x8F, expected.get(61),
                            "ROM fixture should allocate Obj8F Grounder wall child into slot 61 at f687");
                    Assertions.assertEquals(0x8F, expected.get(62),
                            "ROM fixture should allocate Obj8F Grounder wall child into slot 62 at f687");
                    Assertions.assertEquals(0x8F, expected.get(63),
                            "ROM fixture should allocate Obj8F Grounder wall child into slot 63 at f687");
                    Assertions.assertEquals(0x0A, expected.get(64),
                            "ROM fixture should allocate the next Obj0A mouth bubble into slot 64 at f687");
                    return new SlotWindowCheck(actual, describeSlots(actual, 57, 64));
                });
        Assertions.assertNotNull(check);
        Assertions.assertEquals(0x8F, check.idAt(61),
                "S2 Obj8D loc_36C64 uses AllocateObject for Obj8F wall pieces before the "
                        + "same-frame Obj0A mouth bubble; actual slots " + check.summary());
        Assertions.assertEquals(0x8F, check.idAt(62),
                "S2 Obj8D loc_36C64 uses AllocateObject for Obj8F wall pieces before the "
                        + "same-frame Obj0A mouth bubble; actual slots " + check.summary());
        Assertions.assertEquals(0x8F, check.idAt(63),
                "S2 Obj8D loc_36C64 uses AllocateObject for Obj8F wall pieces before the "
                        + "same-frame Obj0A mouth bubble; actual slots " + check.summary());
        Assertions.assertEquals(0x0A, check.idAt(64),
                "Obj0A should take the next lowest slot after the Obj8F wall pieces; actual slots "
                        + check.summary());
    }

    @Test
    public void arz2ArrowProjectileAllocatesInRomSlot65OnRomFrame696() throws Exception {
        SlotProjectileCheck slotCheck = driveTrace("arz2", Sonic2ZoneConstants.ZONE_ARZ, 1,
                (trace, om, frame) -> {
                    if (frame != 694 && frame != 695 && frame != 696) {
                        return null;
                    }
                    Map<Integer, Integer> expected =
                            ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT);
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    if (frame == 694 || frame == 695) {
                        Assertions.assertNull(expected.get(65),
                                "ROM fixture should not allocate the Obj22 arrow into slot 0x41 until f696");
                        Assertions.assertNull(actual.get(65),
                                "S2 Obj22 should not reserve the arrow projectile SST slot before "
                                        + "Obj22_ShootArrow runs (docs/s2disasm/s2.asm:51570-51587); "
                                        + "actual slots " + describeSlots(actual, 60, 65));
                        return null;
                    }

                    TraceEvent.ObjectNear expectedArrow = trace.getEventsForFrame(frame).stream()
                            .filter(TraceEvent.ObjectNear.class::isInstance)
                            .map(TraceEvent.ObjectNear.class::cast)
                            .filter(near -> near.slot() == 65)
                            .filter(near -> parseObjectType(near.objectType()) == 0x22)
                            .findFirst()
                            .orElse(null);
                    Assertions.assertNotNull(expectedArrow,
                            "ARZ2 ROM fixture should report the first Obj22 arrow in slot 0x41 at f696");
                    ArrowProjectileInstance actualArrow = om.activeObjectsOfType(ArrowProjectileInstance.class)
                            .stream()
                            .filter(arrow -> arrow.getSlotIndex() == 65)
                            .findFirst()
                            .orElse(null);
                    return new SlotProjectileCheck(actual.get(65),
                            actualArrow == null ? -1 : actualArrow.getX(),
                            expectedArrow.x() & 0xFFFF,
                            describeSlots(actual, 60, 65));
                });
        Assertions.assertNotNull(slotCheck);
        Assertions.assertEquals(0x22, slotCheck.actualId(),
                "ARZ2 slot 0x41 should first contain Obj22 when ROM Obj22_ShootArrow allocates "
                        + "the arrow at f696; actual slots " + slotCheck.summary());
        Assertions.assertEquals(slotCheck.expectedX(), slotCheck.actualX(),
                "S2 Obj22_Arrow_Init falls through into Obj22_Arrow/ObjectMove on the allocation "
                        + "frame, so the first visible arrow X is already advanced by $400 "
                        + "(docs/s2disasm/s2.asm:51590-51607); actual slots " + slotCheck.summary());
    }

    @Test
    public void arz2SecondArrowProjectileAllocatesInRomLowSlotOnRomFrame796() throws Exception {
        SlotProjectileCheck slotCheck = driveTrace("arz2", Sonic2ZoneConstants.ZONE_ARZ, 1,
                (trace, om, frame) -> {
                    if (frame != 796) {
                        return null;
                    }
                    Map<Integer, Integer> expected =
                            ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT);
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    TraceEvent.ObjectNear expectedArrow = trace.getEventsForFrame(frame).stream()
                            .filter(TraceEvent.ObjectNear.class::isInstance)
                            .map(TraceEvent.ObjectNear.class::cast)
                            .filter(near -> near.slot() == 18)
                            .filter(near -> parseObjectType(near.objectType()) == 0x22)
                            .findFirst()
                            .orElse(null);
                    Assertions.assertNotNull(expectedArrow,
                            "ARZ2 ROM fixture should allocate the second Obj22 arrow in low slot 0x12 at f796");
                    ArrowProjectileInstance actualArrow = om.activeObjectsOfType(ArrowProjectileInstance.class)
                            .stream()
                            .filter(arrow -> arrow.getSlotIndex() == 18)
                            .findFirst()
                            .orElse(null);
                    return new SlotProjectileCheck(actual.get(18),
                            actualArrow == null ? -1 : actualArrow.getX(),
                            expectedArrow.x() & 0xFFFF,
                            "expected " + describeSlots(expected, 18, 42)
                                    + " actual " + describeSlots(actual, 18, 42));
                });
        Assertions.assertNotNull(slotCheck);
        Assertions.assertEquals(0x22, slotCheck.actualId(),
                "S2 Obj22_ShootArrow must use AllocateObject/lowest-free semantics; at f796 the "
                        + "free ROM slot is below the shooter, so the projectile belongs in slot 0x12. "
                        + "Actual slots " + slotCheck.summary());
        Assertions.assertEquals(slotCheck.expectedX(), slotCheck.actualX(),
                "A lower-slot Obj22 child has already been passed by ExecuteObjects on its allocation "
                        + "frame, so it must remain at the shooter x_pos until the next frame "
                        + "(docs/s2disasm/s2.asm:51570-51607); actual slots " + slotCheck.summary());
    }

    @Test
    public void arz2ArrowProjectileUsesRomWallProbeAtRomFrame844() throws Exception {
        SlotProjectileCheck slotCheck = driveTrace("arz2", Sonic2ZoneConstants.ZONE_ARZ, 1,
                (trace, om, frame) -> {
                    if (frame != 844) {
                        return null;
                    }
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    TraceEvent.ObjectNear expectedArrow = trace.getEventsForFrame(frame).stream()
                            .filter(TraceEvent.ObjectNear.class::isInstance)
                            .map(TraceEvent.ObjectNear.class::cast)
                            .filter(near -> near.slot() == 65)
                            .filter(near -> parseObjectType(near.objectType()) == 0x22)
                            .findFirst()
                            .orElse(null);
                    Assertions.assertNotNull(expectedArrow,
                            "ARZ2 ROM fixture should still report the first Obj22 arrow in slot 0x41 at f844");
                    ArrowProjectileInstance actualArrow = om.activeObjectsOfType(ArrowProjectileInstance.class)
                            .stream()
                            .filter(arrow -> arrow.getSlotIndex() == 65)
                            .findFirst()
                            .orElse(null);
                    return new SlotProjectileCheck(actual.get(65),
                            actualArrow == null ? -1 : actualArrow.getX(),
                            expectedArrow.x() & 0xFFFF,
                            describeSlots(actual, 60, 65));
                });
        Assertions.assertNotNull(slotCheck);
        Assertions.assertEquals(0x22, slotCheck.actualId(),
                "S2 Obj22 arrow must survive through the ROM opposite-side wall probe at f844; "
                        + "actual slots " + slotCheck.summary());
        Assertions.assertEquals(slotCheck.expectedX(), slotCheck.actualX(),
                "S2 Obj22_Arrow checks ObjCheckLeftWallDist at x_pos-8 for a right-moving arrow, "
                        + "not the right wall at x_pos+8 (docs/s2disasm/s2.asm:51607-51623); "
                        + "actual slots " + slotCheck.summary());
    }

    @Test
    public void arz2PlatformBobUsesRomStandingLatchOnJumpFrame888() throws Exception {
        PlatformPositionCheck check = driveTrace("arz2", Sonic2ZoneConstants.ZONE_ARZ, 1,
                (trace, om, frame) -> {
                    if (frame != 888) {
                        return null;
                    }
                    TraceEvent.ObjectNear expectedPlatform = trace.getEventsForFrame(frame).stream()
                            .filter(TraceEvent.ObjectNear.class::isInstance)
                            .map(TraceEvent.ObjectNear.class::cast)
                            .filter(near -> near.slot() == 0x1F)
                            .filter(near -> parseObjectType(near.objectType()) == 0x18)
                            .findFirst()
                            .orElse(null);
                    Assertions.assertNotNull(expectedPlatform,
                            "ARZ2 ROM fixture should report the ridden Obj18 platform in slot 0x1F at f888");
                    ARZPlatformObjectInstance actualPlatform = om.activeObjectsOfType(ARZPlatformObjectInstance.class)
                            .stream()
                            .filter(platform -> platform.getSlotIndex() == 0x1F)
                            .findFirst()
                            .orElse(null);
                    return new PlatformPositionCheck(
                            expectedPlatform.x() & 0xFFFF,
                            expectedPlatform.y() & 0xFFFF,
                            actualPlatform == null ? -1 : actualPlatform.getX(),
                            actualPlatform == null ? -1 : actualPlatform.getY(),
                            describeSlots(om.occupiedDynamicSlotIds(), 0x1B, 0x24));
                });
        Assertions.assertNotNull(check);
        Assertions.assertEquals(check.expectedX(), check.actualX(),
                "ARZ2 Obj18 slot 0x1F should keep ROM X on Sonic's jump-off frame; slots "
                        + check.summary());
        Assertions.assertEquals(check.expectedY(), check.actualY(),
                "S2 Obj18_TopSolid reads status(a0)&standing_mask before PlatformObject clears "
                        + "the jump-off ride, so Obj18_Nudge must use the prior standing latch "
                        + "at f888 (docs/s2disasm/s2.asm:23219-23243,23311-23320); slots "
                        + check.summary());
    }

    @Test
    public void arz2LeafParticlesDoNotDisplaceMouthBubbleSlotOnRomFrame723() throws Exception {
        SlotWindowCheck slotCheck = driveTrace("arz2", Sonic2ZoneConstants.ZONE_ARZ, 1,
                (trace, om, frame) -> {
                    if (frame != 723) {
                        return null;
                    }
                    Map<Integer, Integer> expected =
                            ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT);
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    Assertions.assertEquals(0x0A, expected.get(17),
                            "ARZ2 ROM fixture should allocate the f723 mouth bubble into slot 0x11 "
                                    + "after Obj2C leaves have observed render_flags.on_screen and deleted");
                    ObjectOccupancyOracle.Divergence divergence =
                            ObjectOccupancyOracle.firstDivergence(trace, om, frame, FIRST_DYNAMIC_SLOT);
                    Assertions.assertNull(divergence,
                            "ARZ2 dynamic slots should still match at f723 after Obj2C_Leaf deletes "
                                    + "through render_flags.on_screen "
                                    + "(docs/s2disasm/s2.asm:52232-52237); expected "
                                    + describeSlots(expected, 16, 22) + " actual "
                                    + describeSlots(actual, 16, 22));
                    return new SlotWindowCheck(actual, describeSlots(actual, 16, 22));
                });
        Assertions.assertNotNull(slotCheck);
        Assertions.assertEquals(0x0A, slotCheck.idAt(17),
                "Obj0A mouth bubble should take the ROM slot 0x11 once off-screen Obj2C leaves "
                        + "delete through the render-flag path; actual slots " + slotCheck.summary());
    }

    @Test
    public void arz2ChopChopEmitsPatrolBubbleIntoRomSlot19AtFrame598() throws Exception {
        SlotBubbleCheck slotCheck = driveTrace("arz2", Sonic2ZoneConstants.ZONE_ARZ, 1,
                (trace, om, frame) -> {
                    if (frame != 598) {
                        return null;
                    }
                    Map<Integer, Integer> expected =
                            ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT);
                    Map<Integer, Integer> actual = om.occupiedDynamicSlotIds();
                    TraceEvent.ObjectAppeared expectedBubble = trace.getEventsForFrame(frame).stream()
                            .filter(TraceEvent.ObjectAppeared.class::isInstance)
                            .map(TraceEvent.ObjectAppeared.class::cast)
                            .filter(appeared -> appeared.slot() == 19)
                            .filter(appeared -> parseObjectType(appeared.objectType()) == 0x0A)
                            .findFirst()
                            .orElse(null);
                    Assertions.assertNotNull(expectedBubble,
                            "ARZ2 ROM fixture should allocate the f598 Obj91 patrol bubble into slot 0x13");
                    BreathingBubbleInstance actualBubble = om.activeObjectsOfType(BreathingBubbleInstance.class)
                            .stream()
                            .filter(bubble -> bubble.getSlotIndex() == 19)
                            .findFirst()
                            .orElse(null);
                    return new SlotBubbleCheck(actual.get(19),
                            actualBubble == null ? -1 : actualBubble.getX(),
                            actualBubble == null ? -1 : actualBubble.getY(),
                            expectedBubble.x() & 0xFFFF,
                            expectedBubble.y() & 0xFFFF,
                            describeSlots(expected, 16, 26),
                            describeSlots(actual, 16, 26));
                });
        Assertions.assertNotNull(slotCheck);
        Assertions.assertEquals(0x0A, slotCheck.actualId(),
                "Obj91 must spawn its Obj0A patrol bubble through the normal free-slot path at f598; "
                        + "expected slots " + slotCheck.expectedSummary()
                        + " actual slots " + slotCheck.actualSummary());
        Assertions.assertEquals(slotCheck.expectedX(), slotCheck.actualX(),
                "Obj91_MakeBubble offsets x_pos by 0x14 from the ChopChop mouth "
                        + "(docs/s2disasm/s2.asm:73751-73764)");
        Assertions.assertEquals(slotCheck.expectedY(), slotCheck.actualY(),
                "Obj91_MakeBubble offsets y_pos by +6 from the ChopChop mouth "
                        + "(docs/s2disasm/s2.asm:73765-73769)");
    }

    private record SlotWindowCheck(Map<Integer, Integer> slots, String summary) {
        int idAt(int slot) {
            return slots.getOrDefault(slot, -1);
        }
    }

    private record SlotProjectileCheck(Integer actualId, int actualX, int expectedX, String summary) {
    }

    private record SlotBubbleCheck(
            Integer actualId,
            int actualX,
            int actualY,
            int expectedX,
            int expectedY,
            String expectedSummary,
            String actualSummary) {
    }

    private record PlatformPositionCheck(int expectedX, int expectedY, int actualX, int actualY, String summary) {
    }

    private AnimalPositionCheck animalPositionAtArz2Frame(int targetFrame) throws Exception {
        return driveTrace("arz2", Sonic2ZoneConstants.ZONE_ARZ, 1,
                (trace, om, frame) -> {
                    if (frame != targetFrame) {
                        return null;
                    }
                    TraceEvent.ObjectNear expectedAnimal = trace.getEventsForFrame(frame).stream()
                            .filter(TraceEvent.ObjectNear.class::isInstance)
                            .map(TraceEvent.ObjectNear.class::cast)
                            .filter(near -> near.slot() == 24)
                            .filter(near -> parseObjectType(near.objectType()) == 0x28)
                            .findFirst()
                            .orElse(null);
                    Assertions.assertNotNull(expectedAnimal,
                            "ARZ2 ROM fixture should report the first ChopChop animal in slot 24 at f"
                                    + targetFrame);

                    AnimalObjectInstance actualAnimal = om.activeObjectsOfType(AnimalObjectInstance.class).stream()
                            .filter(animal -> animal.getSlotIndex() == 24)
                            .findFirst()
                            .orElse(null);
                    return new AnimalPositionCheck(expectedAnimal.x() & 0xFFFF, expectedAnimal.y() & 0xFFFF,
                            actualAnimal == null ? -1 : actualAnimal.getX(),
                            actualAnimal == null ? -1 : actualAnimal.getY(),
                            describeSlots(om.occupiedDynamicSlotIds(), 19, 25));
                });
    }

    private record AnimalPositionCheck(int expectedX, int expectedY, int actualX, int actualY, String summary) {
    }

    private static int parseObjectType(String objectType) {
        if (objectType == null || objectType.isBlank()) {
            return -1;
        }
        return Integer.parseInt(objectType.replace("0x", "").replace("0X", "").trim(), 16) & 0xFF;
    }

    private static String describeSlots(Map<Integer, Integer> occupancy, int firstSlot, int lastSlot) {
        StringBuilder sb = new StringBuilder();
        for (int slot = firstSlot; slot <= lastSlot; slot++) {
            Integer id = occupancy.get(slot);
            if (id == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(slot).append(':').append(String.format("%02X", id & 0xFF));
        }
        return sb.toString();
    }

    private static String describeObj75Mcz2SpawnState(ObjectManager objectManager) {
        StringBuilder sb = new StringBuilder("obj75-spawns");
        for (var spawn : objectManager.getAllSpawns()) {
            if (spawn.objectId() == 0x75
                    && spawn.x() >= 0x1700
                    && spawn.x() <= 0x1800) {
                if (sb.length() > "obj75-spawns".length()) {
                    sb.append(' ');
                } else {
                    sb.append(' ');
                }
                int liveSlot = objectManager.getActiveObjects().stream()
                        .filter(AbstractObjectInstance.class::isInstance)
                        .map(AbstractObjectInstance.class::cast)
                        .filter(instance -> instance.getSpawn() != null)
                        .filter(instance -> instance.getSpawn().layoutIndex() == spawn.layoutIndex())
                        .mapToInt(AbstractObjectInstance::getSlotIndex)
                        .findFirst()
                        .orElse(-1);
                sb.append(String.format("i%d %02X@%04X,%04X sub=%02X active=%s dorm=%s rem=%s live=s%d",
                        spawn.layoutIndex(),
                        spawn.objectId(),
                        spawn.x(),
                        spawn.y(),
                        spawn.subtype(),
                        objectManager.getActiveSpawns().contains(spawn),
                        objectManager.isDormant(spawn),
                        objectManager.isRemembered(spawn),
                        liveSlot));
            }
        }
        return sb.toString();
    }

    private static ObjectSpawnState obj75Mcz2SpikeBallState(ObjectManager objectManager) {
        for (var spawn : objectManager.getAllSpawns()) {
            if (spawn.objectId() == 0x75
                    && spawn.x() == 0x1740
                    && spawn.y() == 0x0690
                    && spawn.subtype() == 0x17) {
                int liveCount = (int) objectManager.getActiveObjects().stream()
                        .filter(AbstractObjectInstance.class::isInstance)
                        .map(AbstractObjectInstance.class::cast)
                        .filter(instance -> instance.getSpawn() != null)
                        .filter(instance -> instance.getSpawn().layoutIndex() == spawn.layoutIndex())
                        .count();
                return new ObjectSpawnState(
                        objectManager.getActiveSpawns().contains(spawn),
                        objectManager.isDormant(spawn),
                        liveCount);
            }
        }
        return null;
    }

    @Test
    public void scz1TransientOccupancyMatchesRom() throws Exception {
        assertTransientOccupancy("scz", Sonic2ZoneConstants.ZONE_SCZ, 0);
    }

    @Test
    public void wfz1TransientOccupancyMatchesRom() throws Exception {
        assertTransientOccupancy("wfz", Sonic2ZoneConstants.ZONE_WFZ, 0);
    }

    /**
     * Asserts that for every replayed frame of the named green S2 trace, the
     * engine never holds MORE live instances of a self-deleting transient
     * ({@link #TRANSIENT_SELF_DELETE_IDS}) than the ROM timeline — i.e. the
     * transient never self-deletes LATER than the ROM {@code DeleteObject}. This
     * is exactly the piece-(a) regression the explosion fix closes (the old
     * uniform 8-frame delay made the explosion linger 4 frames). Counting by id
     * ignores slot reshuffle; restricting to {@code engineCount > romCount}
     * ignores the {@code engineCount < romCount} spawn-frame windowing offset
     * that belongs to piece (b).
     */
    private void assertTransientOccupancy(String route, int zone, int act) throws Exception {
        ObjectOccupancyOracle.CountDivergence first = driveTrace(route, zone, act,
                (trace, om, frame) -> ObjectOccupancyOracle.firstTransientCountDivergence(
                        trace, om, frame, FIRST_DYNAMIC_SLOT, TRANSIENT_SELF_DELETE_IDS, true));
        Assertions.assertNull(first, () -> first == null ? "" : String.format(
                "[occupancy-oracle] %s transient lingers past its ROM DeleteObject: "
                        + "frame=%d id=0x%02X romCount=%d engineCount=%d "
                        + "(scope=Obj27; engineCount>romCount = late self-delete)",
                route.toUpperCase(), first.frame(), first.id(),
                first.romCount(), first.engineCount()));
    }

    /** Per-frame comparison-only probe over the driven engine + ROM timeline. */
    @FunctionalInterface
    private interface FrameProbe<T> {
        T check(TraceData trace, ObjectManager om, int frame);
    }

    @Test
    public void ehz1TransientOccupancyMatchesRom() throws Exception {
        // EHZ1 is a green S2 trace (Sonic+Tails). Assert transient self-delete
        // occupancy (Obj27/Obj29) frame-for-frame against the ROM timeline.
        Path traceDir = Path.of("src/test/resources/traces/s2").resolve("ehz1_fullrun");
        Assumptions.assumeTrue(Files.isDirectory(traceDir),
                "EHZ1 trace directory not found: " + traceDir);
        assertTransientOccupancy("ehz1_fullrun", Sonic2ZoneConstants.ZONE_EHZ, 0);
    }

    /**
     * Unscoped slot-occupancy measurement (every slot divergence) used by the
     * non-asserting MTZ1 frontier probe.
     */
    private ObjectOccupancyOracle.Divergence measureFirstDivergence(
            String route, int zone, int act, Set<Integer> unused) throws Exception {
        return driveTrace(route, zone, act,
                (trace, om, frame) -> ObjectOccupancyOracle.firstDivergence(
                        trace, om, frame, FIRST_DYNAMIC_SLOT));
    }

    /**
     * Drives the named S2 level-select trace through the engine (mirroring the
     * S2 branch of {@code AbstractTraceReplayTest.replayMatchesTrace}) and
     * returns the first non-null result from {@code probe}, or {@code null} when
     * the probe reported no divergence for any replayed frame.
     */
    private <T> T driveTrace(String route, int zone, int act, FrameProbe<T> probe)
            throws Exception {
        Path traceDir = Path.of("src/test/resources/traces/s2").resolve(route);
        Assumptions.assumeTrue(Files.isDirectory(traceDir),
                "Trace directory not found: " + traceDir);
        Assumptions.assumeTrue(Files.exists(traceDir.resolve("metadata.json")),
                "metadata.json not found in " + traceDir);

        Path bk2Path = findBk2File(traceDir);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        TraceMetadata meta = trace.metadata();
        Assumptions.assumeTrue("s2".equals(meta.game()),
                "Expected an S2 trace but metadata.game=" + meta.game());

        boolean requiresFreshLevelLoad =
                TraceReplayBootstrap.requiresFreshLevelLoadForTraceReplay(trace);
        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);

        SharedLevel sharedLevel = requiresFreshLevelLoad
                ? null
                : SharedLevel.load(SonicGame.SONIC_2, zone, act);
        try {
            HeadlessTestFixture.Builder fixtureBuilder = HeadlessTestFixture.builder()
                    .withRecording(bk2Path)
                    .withRecordingStartFrame(
                            TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace));
            if (sharedLevel != null) {
                fixtureBuilder.withSharedLevel(sharedLevel);
            } else {
                fixtureBuilder.withZoneAndAct(zone, act);
            }
            if (TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace)) {
                fixtureBuilder
                        .startPosition(meta.startX(), meta.startY())
                        .startPositionIsCentre();
            }
            HeadlessTestFixture fixture = fixtureBuilder.build();

            TraceReplaySessionBootstrap.BootstrapResult boot =
                    TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);

            ObjectManager om = GameServices.level() != null
                    ? GameServices.level().getObjectManager() : null;
            Assumptions.assumeTrue(om != null, "ObjectManager unavailable after bootstrap");

            int startTraceIndex = boot.replayStart().startingTraceIndex();
            for (int i = startTraceIndex; i < trace.frameCount(); i++) {
                TraceFrame expected = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceReplayBootstrap.phaseForReplay(trace, previous, expected);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
                if (!TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) {
                    continue;
                }
                T divergence = probe.check(trace, om, i);
                if (divergence != null) {
                    return divergence;
                }
            }
            return null;
        } finally {
            if (sharedLevel != null) {
                sharedLevel.dispose();
            } else {
                TestEnvironment.resetAll();
            }
        }
    }

    private static Path findBk2File(Path dir) throws Exception {
        try (var files = Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
