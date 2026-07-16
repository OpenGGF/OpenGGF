package com.openggf.game.sonic3k.objects;

import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Placement-backed, deterministic route contract through FBZ2's preboss handoff. */
@RequiresRom(SonicGame.SONIC_3K)
public class TestFbzAct2TraversalPreboss {
    /**
     * Fixed ordinary P1 controller program from the native FBZ2 start.
     * Each entry is {@code frame-count:input-mask}; masks use the playable
     * sprite's up/down/left/right/jump bits. There are no state-dependent
     * decisions: object or physics cadence drift makes the route fail.
     */
    private static final String PREBOSS_INPUT_PROGRAM =
            "43:8,433:0,103:8,7:0,8:2,4:12,4:10,20:0,9:2,6:12,2:2,124:0,18:4,45:0,19:4,1:8,33:18,11:8,239:0,52:8,8:18,133:8,14:0,66:2,6:12,49:2,2:a,53:8,10:0,4:10,9:14,5:4,73:0,14:4,7:14,6:4,6:0,24:8,4:0,16:4,8:14,25:4,205:0,14:4,7:0,6:4,30:0,9:8,18:18,28:0,8:4,4:0,3:8,11:0,8:2,25:12,5:2,42:0,11:4,11:14,32:4,29:8,4:0,21:4,6:0,5:2,6:12,1:2,29:0,166:4,9:14,18:4,14:8,51:0,12:8,19:0,11:8,11:0,15:4,262:0,42:8,13:0,29:8,13:18,6:0,19:4,5:0,232:8,8:4,1:8,210:0,41:4,4:8,1:4,10:0,237:4,20:14,224:4,5:8,1:4,80:0,130:4,318:0,92:8,12:4,21:0,4:8,27:0,4:4,45:0,1:8,119:0,299:8,31:4,3:8,1:4,235:0,40:8,20:18,273:8,45:4,26:0,1373:8,31:18,12:8,13:4,2:8,1:4,45:0,39:4,740:8,25:4,105:0,80:8,60:a,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,20:18,1:8,9:8,16:4,2:8,1:4,110:0,1:8,20:18,63:8,1:18,12:8,31:4,16:8,3:4,1:8,1:4,359:0,33:4,42:8,20:18,24:8,4:4,1:8,9:18,49:8,1:18,81:8,50:4,4:8,1:4,215:0,6:8,20:18,29:14,20:4,20:8,30:18,10:8,8:2,4:12,4:2,4:12,4:2,19:8,42:0,45:8,10:4,208:0,16:8,40:18,1600:0,1:18,1:8,15:18,37:8,12:18,49:8,66:0,1:14,38:4,45:8,4:4,1:8,1:4,206:0,117:4,5:8,1:4,20:0,30:18,583:0,343:4,5:8,1:4,218:0,190:0,8:4,17:18,14:14,20:4,22:14,196:4,6:8,1:4,1:8,1:4,234:0,31:18,24:8,9:4,2:8,1:4,192:0,35:8,1:18,42:8,4:18,12:8,17:4,98:8,20:18,100:8";
    private static final List<InputRun> NATIVE_START_TO_POST_SPIKES = parseInputProgram();
    private static final int NATIVE_ROUTE_FRONTIER_FRAMES = 15_010;
    private static final List<InputRun> LATE_STARPOST_INPUT_RUNS = List.of(new InputRun(120, 0x4));
    private static final List<InputRun> CRANE_APPROACH_CYCLE = List.of(
            new InputRun(20, 0x18), new InputRun(40, 0x8));
    private static final List<InputRun> CRANE_CAPTURE_AND_RELEASE = List.of(
            new InputRun(40, 0x4), new InputRun(500, 0x0));
    private static final List<InputRun> MAGNETIC_CHAIN_TRANSFERS_AND_LOWER_DOOR = List.of(
            new InputRun(20, 0x18), new InputRun(40, 0x8),
            new InputRun(20, 0x18), new InputRun(148, 0x8),
            new InputRun(20, 0x18), new InputRun(140, 0x8),
            new InputRun(20, 0x14), new InputRun(8, 0x4),
            new InputRun(20, 0x8), new InputRun(200, 0x0));
    private static final List<InputRun> LOWER_BACKTRACK_CYCLE = List.of(
            new InputRun(20, 0x14), new InputRun(20, 0x4));
    private static final List<InputRun> LOWER_BACKTRACK_FINAL_CYCLE = List.of(
            new InputRun(30, 0x0), new InputRun(20, 0x14), new InputRun(20, 0x4));

    @Test
    void a80Subtype16DownwardPathSwitchSelectsCollisionPathCD() throws IOException {
        ObjectSpawn switcher = TestFbzObjectInventory.load("2.bin").stream()
                .filter(spawn -> spawn.x() == 0x0A80 && spawn.y() == 0x0630)
                .filter(spawn -> spawn.objectId() == Sonic3kObjectIds.PATH_SWAP)
                .filter(spawn -> spawn.subtype() == 0x16)
                .findFirst().orElseThrow();
        assertTrue(ObjectManager.isPlaneSwitcherHorizontal(switcher.subtype()));
        assertEquals(0, ObjectManager.decodePlaneSwitcherPath(switcher.subtype(), 1),
                "crossing downward selects native path 0");
        var config = new Sonic3kGameModule().getPlaneSwitcherConfig();
        assertEquals(0x0C, config.getPath0TopSolidBit() & 0xFF);
        assertEquals(0x0D, config.getPath0LrbSolidBit() & 0xFF);
    }

    @Test
    void act2ContainsExactlyTheEightNativeElevatorSubtypesAndTwelveControllers() throws IOException {
        var elevators = TestFbzObjectInventory.load("2.bin").stream()
                .filter(spawn -> spawn.objectId() == Sonic3kObjectIds.FBZ_ELEVATOR)
                .toList();
        assertEquals(12, elevators.size());
        assertEquals(Set.of(0x0F, 0x1E, 0x24, 0x25, 0x32, 0x37, 0x3B, 0x4B),
                elevators.stream().map(ObjectSpawn::subtype).collect(Collectors.toSet()));
        assertEquals(Map.of(0x0F, 1L, 0x1E, 1L, 0x24, 2L, 0x25, 1L,
                        0x32, 1L, 0x37, 3L, 0x3B, 1L, 0x4B, 2L),
                elevators.stream().collect(Collectors.groupingBy(
                        ObjectSpawn::subtype, Collectors.counting())));
    }

    @Test
    void everyElevatorPlacementResolvesToTheRealControllerFactory() throws IOException {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return Sonic3kZoneIds.ZONE_FBZ; }
        };
        TestFbzObjectInventory.load("2.bin").stream()
                .filter(spawn -> spawn.objectId() == Sonic3kObjectIds.FBZ_ELEVATOR)
                .forEach(spawn -> assertInstanceOf(
                        FbzElevatorObjectInstance.class, registry.create(spawn)));
    }

    @Test
    void ordinaryPlacementRouteTo2b30HasNoMechanicalPlaceholder() throws IOException {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return Sonic3kZoneIds.ZONE_FBZ; }
        };
        var unresolved = TestFbzObjectInventory.load("2.bin").stream()
                .filter(spawn -> spawn.x() < 0x2B30)
                .filter(spawn -> spawn.objectId() != 0xCF)
                .filter(spawn -> registry.create(spawn) instanceof PlaceholderObjectInstance)
                .map(spawn -> String.format("$%04X id=$%02X subtype=$%02X",
                        spawn.x(), spawn.objectId(), spawn.subtype()))
                .toList();
        assertTrue(unresolved.isEmpty(), () -> "ordinary preboss placeholders: " + unresolved);
    }

    public static void assertLateNativeStarpostRestartMaterializesAndExecutesLowerMagneticSection() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1)
                .build();
        ObjectSpawn checkpoint = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(spawn -> spawn.objectId() == GameServices.module().getCheckpointObjectId())
                .filter(spawn -> (spawn.subtype() & 0x7F) == 5)
                .findFirst().orElseThrow();
        CheckpointState state = (CheckpointState) GameServices.level().getCheckpointState();
        state.restoreFromSaved(checkpoint.x(), checkpoint.y(),
                checkpoint.x() - 0xA0, checkpoint.y() - 0x60, checkpoint.subtype() & 0x7F);
        GameServices.level().respawnPlayer();

        assertTrue(state.isActive());
        assertEquals(5, state.getLastCheckpointIndex());
        assertEquals(checkpoint.x(), fixture.sprite().getCentreX() & 0xFFFF);
        assertEquals(checkpoint.y(), fixture.sprite().getCentreY() & 0xFFFF);
        assertEquals(checkpoint.x() - 0xA0, state.getSavedCameraX());
        assertEquals(checkpoint.y() - 0x60, state.getSavedCameraY());
        assertEquals(checkpoint.x() - 0xA0, fixture.camera().getX() & 0xFFFF);
        assertEquals(Math.min(state.getSavedCameraY(), fixture.camera().getMaxY() & 0xFFFF),
                fixture.camera().getY() & 0xFFFF,
                "checkpoint restart camera must use the game-owned FBZ2 vertical clamp");
        LateStarpostMilestones milestones = new LateStarpostMilestones();
        FixedInputRunner runner = new FixedInputRunner(
                fixture, GameServices.level().getObjectManager(), milestones::observe);
        runner.run(LATE_STARPOST_INPUT_RUNS, (frame, player) ->
                assertFalse(player.isHurt() || player.getDead(),
                        () -> "fixed checkpoint section took damage at frame " + frame.frames()));

        assertTrue(milestones.platformObserved,
                "late starpost did not materialize the lower $2840 platform");
        assertTrue(milestones.platformExecuted,
                "lower $2840 platform did not survive into its next execution pass");
        assertTrue(milestones.chainObserved,
                "lower $2840 platform did not allocate its real chain child");
        assertTrue(milestones.chainExecuted,
                "lower magnetic chain did not survive into its next execution pass");
    }

    /**
     * Runs the fixed native-start route through every traversal stage, both bosses,
     * the capsule/results handoff, and the production request for Sandopolis Act 0.
     */
    public static void assertNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones() {
        assertNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(fixture -> { });
    }

    public static void assertNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(
            Consumer<HeadlessTestFixture> startAssertion) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1)
                .build();
        startAssertion.accept(fixture);
        ObjectManager objects = GameServices.level().getObjectManager();
        Set<Class<?>> encounteredFamilies = new LinkedHashSet<>();
        Set<Class<?>> executedFamilies = new LinkedHashSet<>();
        RouteMilestones milestones = new RouteMilestones();
        FixedInputRunner runner = new FixedInputRunner(fixture, objects,
                (active, previous, player, manager) -> observeActiveObjects(
                        active, previous, encounteredFamilies, executedFamilies,
                        milestones, player, manager));
        FrameCheck alive = (frame, player) ->
                assertFalse(player.getDead(),
                        () -> routeEvidence(fixture, frame.frames(), milestones));

        runner.run(NATIVE_START_TO_POST_SPIKES, alive);
        assertEquals(NATIVE_ROUTE_FRONTIER_FRAMES, runner.frames(), "fixed frontier length changed");
        for (int cycle = 0; cycle < 100; cycle++) {
            runner.run(CRANE_APPROACH_CYCLE, alive);
        }
        runner.run(CRANE_CAPTURE_AND_RELEASE, alive);
        runner.run(MAGNETIC_CHAIN_TRANSFERS_AND_LOWER_DOOR, alive);
        for (int cycle = 0; cycle < 9; cycle++) {
            List<InputRun> cycleRuns = cycle == 8
                    ? LOWER_BACKTRACK_FINAL_CYCLE : LOWER_BACKTRACK_CYCLE;
            runner.run(cycleRuns, (frame, player) -> {
                alive.afterFrame(frame, player);
                assertEquals(6, GameServices.level().getLevelGamestate().getRings(),
                        () -> routeEvidence(fixture, frame.frames(), milestones));
            });
        }
        List<InputRun> elevatorSubbossBossAndExit = List.of(
                new InputRun(20, 0x18), new InputRun(180, 0x0),
                new InputRun(20, 0x18), new InputRun(45, 0x8),
                new InputRun(200, 0x0), new InputRun(20, 0x18),
                new InputRun(200, 0x8), new InputRun(15, 0x4),
                new InputRun(330, 0x0), new InputRun(20, 0x18),
                new InputRun(5, 0x4), new InputRun(205, 0x0),
                new InputRun(1, 0x8), new InputRun(3, 0x2),
                new InputRun(1, 0x12), new InputRun(1, 0x2),
                new InputRun(1, 0x12), new InputRun(1, 0x2),
                new InputRun(1, 0x12), new InputRun(1, 0x2),
                new InputRun(2, 0x8), new InputRun(20, 0x18),
                new InputRun(416, 0x8), new InputRun(70, 0x4),
                new InputRun(263, 0x0), new InputRun(70, 0x8),
                new InputRun(265, 0x0), new InputRun(70, 0x4),
                new InputRun(265, 0x0), new InputRun(70, 0x8),
                new InputRun(265, 0x0), new InputRun(70, 0x4),
                new InputRun(265, 0x0), new InputRun(70, 0x8),
                new InputRun(265, 0x0), new InputRun(70, 0x4),
                new InputRun(600, 0x0), new InputRun(300, 0x8),
                new InputRun(20, 0x18), new InputRun(300, 0x8),
                new InputRun(3000, 0x0), new InputRun(100, 0x8),
                new InputRun(120, 0x0),
                new InputRun(30, 0x18), new InputRun(30, 0x8),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(40, 0x4), new InputRun(20, 0x14),
                new InputRun(30, 0x18), new InputRun(30, 0x8),
                new InputRun(20, 0x8), new InputRun(20, 0x18),
                new InputRun(40, 0x8), new InputRun(20, 0x18),
                new InputRun(30, 0x4),
                new InputRun(20, 0x8),
                new InputRun(30, 0x14), new InputRun(30, 0x4),
                new InputRun(40, 0x8),
                new InputRun(1, 0x4), new InputRun(3, 0x2),
                new InputRun(1, 0x12), new InputRun(1, 0x2),
                new InputRun(1, 0x12), new InputRun(1, 0x2),
                new InputRun(1, 0x12), new InputRun(1, 0x2),
                new InputRun(2, 0x4), new InputRun(40, 0x4),
                new InputRun(20, 0x18), new InputRun(60, 0x8),
                new InputRun(1, 0x8),
                new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8),
                new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(30, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x8), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(1, 0x4), new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(1, 0x8), new InputRun(20, 0x14), new InputRun(40, 0x4),
                new InputRun(55, 0x4),
                new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(69, 0x4),
                new InputRun(20, 0x18), new InputRun(40, 0x8),
                new InputRun(40, 0x4),
                new InputRun(20, 0x14), new InputRun(60, 0x4),
                new InputRun(400, 0x8),
                new InputRun(20, 0x18), new InputRun(200, 0x8),
                new InputRun(1000, 0x0));
        boolean forcedExitRequested = runner.runUntil(elevatorSubbossBossAndExit,
                (frame, player) -> {
                    assertFalse(player.getDead(),
                            () -> routeEvidence(fixture, frame.frames(), milestones));
                    assertTrue(GameServices.level().getLevelGamestate().getRings() >= 6,
                            () -> routeEvidence(fixture, frame.frames(), milestones)
                                    + " subboss=" + objects.activeObjectsOfType(Fbz2SubbossInstance.class)
                                    .stream().map(boss -> boss.phaseName() + "/" + boss.cyclesRemaining())
                                    .toList()
                                    + " laser=" + objects.activeObjectsOfType(Fbz2SubbossLaserChild.class)
                                    .stream().map(laser -> laser.getX() + ":" + laser.getY()).toList()
                                    + " boss=" + objects.activeObjectsOfType(FbzEndBossInstance.class)
                                    .stream().map(boss -> boss.phase() + "/" + boss.getCollisionProperty())
                                    .toList());
                }, () -> GameServices.level().getRequestedZone() == Sonic3kZoneIds.ZONE_SOZ
                        && GameServices.level().getRequestedAct() == 0);
        assertTrue(executedFamilies.containsAll(encounteredFamilies),
                () -> "encountered family missed its next execution pass: "
                        + difference(encounteredFamilies, executedFamilies));
        assertTrue(milestones.cageCapture, "opening wire cage never captured P1");
        assertTrue(milestones.prisonOpened, "placed $CF prison never opened on the route");
        assertTrue(milestones.elevatorRide, "no real $E2 car carried P1");
        assertTrue(milestones.launcherRide, "floor launcher never received a standing P1 contact");
        assertTrue(milestones.flamethrowerRide,
                "route never stood on the $1B28 flamethrower to suppress its flames");
        assertTrue(milestones.magneticPlatformRide, "magnetic platform route never carried P1");
        assertTrue(milestones.chainControl, "chain-link transport never controlled P1");
        assertTrue(milestones.spiderControl, "spider-crane transport never controlled P1");
        assertTrue(executedFamilies.contains(FbzScrewDoorObjectInstance.class),
                "placed screw-door family never executed on the route");
        assertTrue(fixture.camera().getX() >= 0x2B30,
                "route has not reached the native FBZ2 subboss event: "
                        + routeEvidence(fixture, runner.frames(), milestones));
        assertTrue(forcedExitRequested,
                "boss-owned forced exit never requested SOZ act 0: "
                        + routeEvidence(fixture, runner.frames(), milestones));
        assertEquals(Sonic3kZoneIds.ZONE_SOZ, GameServices.level().getRequestedZone());
        assertEquals(0, GameServices.level().getRequestedAct());
        for (String key : List.of(Sonic3kObjectArtKeys.FBZ_EXIT_DOOR,
                Sonic3kObjectArtKeys.FBZ_EXIT_HALL_DOOR_SCENERY,
                Sonic3kObjectArtKeys.FBZ_EXIT_HALL)) {
            var renderer=GameServices.level().getObjectRenderManager().getRenderer(key);
            assertNotNull(renderer,"post-capsule PLC did not publish "+key);
            assertTrue(renderer.isReady(),"post-capsule PLC consumer was not cached: "+key);
        }
    }

    private static void observeActiveObjects(
            Set<ObjectInstance> active, Set<ObjectInstance> previousFrame,
            Set<Class<?>> encounteredFamilies, Set<Class<?>> executedFamilies,
            RouteMilestones milestones, AbstractPlayableSprite player, ObjectManager objects) {
        for (ObjectInstance object : active) {
            boolean excluded = object.getSpawn() != null
                    && (object.getSpawn().x() >= 0x2B30
                    || object.getSpawn().objectId() == 0);
            assertTrue(!(object instanceof PlaceholderObjectInstance) || excluded,
                    () -> "mechanical placeholder entered fixed FBZ2 route: "
                            + object.getClass().getName() + " spawn=" + object.getSpawn());
            encounteredFamilies.add(object.getClass());
            if (previousFrame.contains(object)) executedFamilies.add(object.getClass());
            if (object instanceof FbzWireCageObjectInstance cage && cage.heldByParticipant(0)) {
                milestones.cageCapture = true;
            }
            if (object instanceof FbzEggPrisonFragmentInstance
                    || object instanceof FbzEggPrisonExplosionController) {
                milestones.prisonOpened = true;
            }
        }

        ObjectInstance riding = objects.getRidingObject(player);
        milestones.elevatorRide |= riding instanceof FbzElevatorObjectInstance.Car;
        milestones.launcherRide |= riding instanceof FbzDezPlayerLauncherObjectInstance;
        boolean onFlamethrower = riding instanceof FbzFlamethrowerObjectInstance flame
                && flame.mappingFrame() == 2;
        milestones.flamethrowerRide |= onFlamethrower;
        milestones.magneticPlatformRide |= riding instanceof FbzMagneticPlatformObjectInstance;
        int playerX = player.getCentreX() & 0xFFFF;
        if (!player.isObjectControlled()) return;
        milestones.chainControl |= active.stream().anyMatch(object ->
                object instanceof FbzChainLinkObjectInstance
                        && Math.abs(object.getX() - playerX) < 0x100);
        milestones.spiderControl |= active.stream().anyMatch(object ->
                object instanceof FbzSpiderCraneObjectInstance
                        && Math.abs(object.getX() - playerX) < 0x100);
    }

    private static void stepMask(HeadlessTestFixture fixture, int mask) {
        fixture.stepFrame(
                (mask & AbstractPlayableSprite.INPUT_UP) != 0,
                (mask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                (mask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                (mask & AbstractPlayableSprite.INPUT_JUMP) != 0);
    }

    private static List<InputRun> parseInputProgram() {
        return java.util.Arrays.stream(PREBOSS_INPUT_PROGRAM.split(","))
                .map(entry -> entry.split(":"))
                .map(parts -> new InputRun(
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1], 16)))
                .toList();
    }

    private static Set<Class<?>> difference(Set<Class<?>> expected, Set<Class<?>> actual) {
        Set<Class<?>> result = new LinkedHashSet<>(expected);
        result.removeAll(actual);
        return result;
    }

    private static String routeEvidence(
            HeadlessTestFixture fixture, int frames, RouteMilestones milestones) {
        AbstractPlayableSprite player = fixture.sprite();
        return "frame=" + frames
                + " camera=($" + Integer.toHexString(fixture.camera().getX() & 0xFFFF)
                + ",$" + Integer.toHexString(fixture.camera().getY() & 0xFFFF) + ')'
                + " player=($" + Integer.toHexString(player.getCentreX() & 0xFFFF)
                + ",$" + Integer.toHexString(player.getCentreY() & 0xFFFF) + ')'
                + " speed=($" + Integer.toHexString(player.getXSpeed() & 0xFFFF)
                + ",$" + Integer.toHexString(player.getYSpeed() & 0xFFFF) + ')'
                + " rings=" + GameServices.level().getLevelGamestate().getRings()
                + " hurt=" + player.isHurt() + " dead=" + player.getDead()
                + " milestones=" + milestones;
    }

    @FunctionalInterface
    private interface FrameObserver {
        void observe(Set<ObjectInstance> active, Set<ObjectInstance> previous,
                     AbstractPlayableSprite player, ObjectManager objects);
    }

    @FunctionalInterface
    private interface FrameCheck {
        void afterFrame(FixedInputRunner runner, AbstractPlayableSprite player);
    }

    @FunctionalInterface
    private interface StopCondition {
        boolean reached();
    }

    /**
     * Executes immutable fixed-input runs while reusing exactly two identity sets
     * for current/previous object-lifetime observations across every frame.
     */
    private static final class FixedInputRunner {
        private static final StopCondition NEVER_STOP = () -> false;

        private final HeadlessTestFixture fixture;
        private final ObjectManager objects;
        private final FrameObserver observer;
        private Set<ObjectInstance> activeFrame =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private Set<ObjectInstance> previousFrame =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private int frames;

        private FixedInputRunner(
                HeadlessTestFixture fixture, ObjectManager objects, FrameObserver observer) {
            this.fixture = fixture;
            this.objects = objects;
            this.observer = observer;
        }

        private void run(List<InputRun> runs, FrameCheck check) {
            assertFalse(runUntil(runs, check, NEVER_STOP), "non-stopping input run stopped");
        }

        private boolean runUntil(
                List<InputRun> runs, FrameCheck check, StopCondition stopCondition) {
            for (InputRun run : runs) {
                for (int i = 0; i < run.frames(); i++) {
                    activeFrame.clear();
                    for (ObjectInstance object : objects.getActiveObjects()) {
                        if (!object.isDestroyed()) activeFrame.add(object);
                    }
                    AbstractPlayableSprite player = fixture.sprite();
                    observer.observe(activeFrame, previousFrame, player, objects);
                    stepMask(fixture, run.mask());
                    frames++;
                    if (stopCondition.reached()) {
                        swapFrameSets();
                        return true;
                    }
                    check.afterFrame(this, player);
                    swapFrameSets();
                }
            }
            return false;
        }

        private void swapFrameSets() {
            Set<ObjectInstance> reusable = previousFrame;
            previousFrame = activeFrame;
            activeFrame = reusable;
        }

        private int frames() {
            return frames;
        }
    }

    private static final class LateStarpostMilestones {
        private boolean platformObserved;
        private boolean platformExecuted;
        private boolean chainObserved;
        private boolean chainExecuted;

        private void observe(
                Set<ObjectInstance> active, Set<ObjectInstance> previous,
                AbstractPlayableSprite player, ObjectManager objects) {
            for (ObjectInstance object : active) {
                boolean lowerPlatform = object instanceof FbzMagneticPlatformObjectInstance platform
                        && platform.getX() == 0x2840 && platform.getY() >= 0x0A00;
                boolean lowerChain = object instanceof FbzMagneticPlatformChainObjectInstance chain
                        && chain.parentMember() != null
                        && chain.parentMember().getX() == 0x2840
                        && chain.parentMember().getY() >= 0x0A00;
                if (lowerPlatform) {
                    platformObserved = true;
                    platformExecuted |= previous.contains(object);
                }
                if (lowerChain) {
                    chainObserved = true;
                    chainExecuted |= previous.contains(object);
                }
            }
        }
    }

    private record InputRun(int frames, int mask) { }

    private static final class RouteMilestones {
        private boolean cageCapture;
        private boolean prisonOpened;
        private boolean elevatorRide;
        private boolean launcherRide;
        private boolean flamethrowerRide;
        private boolean magneticPlatformRide;
        private boolean chainControl;
        private boolean spiderControl;

        @Override public String toString() {
            return "{cage=" + cageCapture + ",prison=" + prisonOpened
                    + ",elevator=" + elevatorRide
                    + ",launcher=" + launcherRide + ",flame=" + flamethrowerRide
                    + ",magnetic=" + magneticPlatformRide
                    + ",chain=" + chainControl + ",spider=" + spiderControl + '}';
        }
    }
}
