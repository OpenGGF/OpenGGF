package com.openggf.game.sonic3k.objects;

import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
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
            "43:8,433:0,103:8,7:0,8:2,4:12,4:10,20:0,9:2,6:12,2:2,124:0,18:4,45:0,19:4,1:8,33:18,11:8,239:0,52:8,8:18,133:8,14:0,66:2,6:12,49:2,2:a,53:8,10:0,4:10,9:14,5:4,73:0,14:4,7:14,6:4,6:0,24:8,4:0,16:4,8:14,25:4,205:0,14:4,7:0,6:4,30:0,9:8,18:18,28:0,8:4,4:0,3:8,11:0,8:2,25:12,5:2,42:0,11:4,11:14,32:4,29:8,4:0,21:4,6:0,5:2,6:12,1:2,29:0,166:4,9:14,18:4,14:8,51:0,12:8,19:0,11:8,11:0,15:4,262:0,42:8,13:0,29:8,13:18,6:0,19:4,5:0,232:8,8:4,1:8,210:0,41:4,4:8,1:4,10:0,481:4,5:8,1:4,313:0,92:8,12:4,21:0,4:8,27:0,4:4,45:0,1:8,119:0,299:8,31:4,3:8,1:4,235:0,333:8,45:4,26:0,1373:8,31:18,12:8,13:4,2:8,1:4,45:0,39:4,1200:8,25:18,25:8,16:4,2:8,1:4,110:0,1:8,20:18,63:8,1:18,12:8,31:4,16:8,3:4,1:8,1:4,359:0,33:4,42:8,20:18,24:8,4:4,1:8,9:18,49:8,1:18,81:8,50:4,4:8,1:4,215:0,26:8,29:18,63:8,102:0,1:18,1:8,15:18,37:8,12:18,49:8,66:0,1:14,38:4,45:8,4:4,1:8,1:4,206:0,117:4,5:8,1:4,633:0,343:4,5:8,1:4,218:0,84:8,12:0,94:8,8:4,17:18,14:14,20:4,22:14,196:4,6:8,1:4,1:8,1:4,234:0,31:18,24:8,9:4,2:8,1:4,192:0,35:8,1:18,42:8,4:18,12:8,17:4,117:8,11:18,19:8";
    private static final List<InputRun> PREBOSS_INPUT_RUNS = parseInputProgram();
    private static final int NATIVE_ROUTE_FRONTIER_FRAMES = 11_900;
    private static final List<InputRun> LATE_STARPOST_INPUT_RUNS = List.of(new InputRun(120, 0x4));

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
        Set<ObjectInstance> previousFrame = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean platformObserved = false;
        boolean platformExecuted = false;
        boolean chainObserved = false;
        boolean chainExecuted = false;
        int frames = 0;
        for (InputRun run : LATE_STARPOST_INPUT_RUNS) {
            for (int i = 0; i < run.frames(); i++) {
                Set<ObjectInstance> active = Collections.newSetFromMap(new IdentityHashMap<>());
                active.addAll(GameServices.level().getObjectManager().getActiveObjects().stream()
                        .filter(object -> !object.isDestroyed()).toList());
                for (ObjectInstance object : active) {
                    boolean lowerPlatform = object instanceof FbzMagneticPlatformObjectInstance platform
                            && platform.getX() == 0x2840 && platform.getY() >= 0x0A00;
                    boolean lowerChain = object instanceof FbzMagneticPlatformChainObjectInstance chain
                            && chain.parentMember() != null
                            && chain.parentMember().getX() == 0x2840
                            && chain.parentMember().getY() >= 0x0A00;
                    if (lowerPlatform) {
                        platformObserved = true;
                        platformExecuted |= previousFrame.contains(object);
                    }
                    if (lowerChain) {
                        chainObserved = true;
                        chainExecuted |= previousFrame.contains(object);
                    }
                }
                stepMask(fixture, run.mask());
                frames++;
                assertFalse(fixture.sprite().isHurt() || fixture.sprite().getDead(),
                        "fixed checkpoint section took damage at frame " + frames);
                previousFrame = active;
            }
        }
        assertTrue(platformObserved, "late starpost did not materialize the lower $2840 platform");
        assertTrue(platformExecuted, "lower $2840 platform did not survive into its next execution pass");
        assertTrue(chainObserved, "lower $2840 platform did not allocate its real chain child");
        assertTrue(chainExecuted, "lower magnetic chain did not survive into its next execution pass");
    }

    /**
     * Continuous native-start completion to $2B30 is intentionally deferred to
     * Task20's final BK2 trace validation. {@code TestFbzEventsAct2} owns the
     * exact $2B30 foreground/background stage semantics in the focused suite.
     */
    public static void assertNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1)
                .build();
        ObjectManager objects = GameServices.level().getObjectManager();
        Set<Class<?>> encounteredFamilies = new LinkedHashSet<>();
        Set<Class<?>> executedFamilies = new LinkedHashSet<>();
        Set<ObjectInstance> previousFrame = Collections.newSetFromMap(new IdentityHashMap<>());
        RouteMilestones milestones = new RouteMilestones();
        int frames = 0;

        route:
        for (InputRun run : PREBOSS_INPUT_RUNS) {
            for (int i = 0; i < run.frames(); i++) {
                AbstractPlayableSprite player = fixture.sprite();
                Set<ObjectInstance> active = Collections.newSetFromMap(new IdentityHashMap<>());
                active.addAll(objects.getActiveObjects().stream()
                        .filter(object -> !object.isDestroyed()).toList());
                observeActiveObjects(active, previousFrame, encounteredFamilies,
                        executedFamilies, milestones, player, objects);
                stepMask(fixture, run.mask());
                frames++;
                assertFalse(player.getDead(), routeEvidence(fixture, frames, milestones));
                previousFrame = active;
                if (frames == NATIVE_ROUTE_FRONTIER_FRAMES) break route;
            }
        }

        assertEquals(NATIVE_ROUTE_FRONTIER_FRAMES, frames, "fixed frontier length changed");
        assertTrue(executedFamilies.containsAll(encounteredFamilies),
                () -> "encountered family missed its next execution pass: "
                        + difference(encounteredFamilies, executedFamilies));
        assertTrue(milestones.cageCapture, "opening wire cage never captured P1");
        assertTrue(milestones.elevatorRide, "no real $E2 car carried P1");
        assertTrue(milestones.launcherRide, "floor launcher never received a standing P1 contact");
        assertTrue(milestones.magneticPlatformRide, "magnetic platform route never carried P1");
        assertTrue(milestones.chainControl, "chain-link transport never controlled P1");
        assertTrue(milestones.spiderControl, "spider-crane transport never controlled P1");
        assertTrue(executedFamilies.contains(FbzScrewDoorObjectInstance.class),
                "placed screw-door family never executed on the route");
    }

    private static void observeActiveObjects(
            Set<ObjectInstance> active, Set<ObjectInstance> previousFrame,
            Set<Class<?>> encounteredFamilies, Set<Class<?>> executedFamilies,
            RouteMilestones milestones, AbstractPlayableSprite player, ObjectManager objects) {
        for (ObjectInstance object : active) {
            boolean excluded = object.getSpawn() != null
                    && (object.getSpawn().x() >= 0x2B30
                    || object.getSpawn().objectId() == 0
                    || object.getSpawn().objectId() == 0xCF);
            assertTrue(!(object instanceof PlaceholderObjectInstance) || excluded,
                    () -> "mechanical placeholder entered fixed FBZ2 route: "
                            + object.getClass().getName() + " spawn=" + object.getSpawn());
            encounteredFamilies.add(object.getClass());
            if (previousFrame.contains(object)) executedFamilies.add(object.getClass());
            if (object instanceof FbzWireCageObjectInstance cage && cage.heldByParticipant(0)) {
                milestones.cageCapture = true;
            }
        }

        ObjectInstance riding = objects.getRidingObject(player);
        milestones.elevatorRide |= riding instanceof FbzElevatorObjectInstance.Car;
        milestones.launcherRide |= riding instanceof FbzDezPlayerLauncherObjectInstance;
        milestones.magneticPlatformRide |= riding instanceof FbzMagneticPlatformObjectInstance;
        if (!player.isObjectControlled()) return;
        int playerX = player.getCentreX() & 0xFFFF;
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
                + " milestones=" + milestones;
    }

    private record InputRun(int frames, int mask) { }

    private static final class RouteMilestones {
        private boolean cageCapture;
        private boolean elevatorRide;
        private boolean launcherRide;
        private boolean magneticPlatformRide;
        private boolean chainControl;
        private boolean spiderControl;

        @Override public String toString() {
            return "{cage=" + cageCapture + ",elevator=" + elevatorRide
                    + ",launcher=" + launcherRide + ",magnetic=" + magneticPlatformRide
                    + ",chain=" + chainControl + ",spider=" + spiderControl + '}';
        }
    }
}
