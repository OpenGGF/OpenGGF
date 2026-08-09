package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-backed production-route evidence for the AIZ2 Knuckles miniboss napalm graph.
 *
 * <p>The fixture deliberately isolates the real boss in an otherwise empty S3K
 * SST while retaining the loaded AIZ2 layout and collision data. That makes
 * native routine-entry and AllocateObjectAfterCurrent ordering observable
 * without hydrating gameplay state from the comparison trace.</p>
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestAizMinibossNapalmProductionRoute {
    private static final int CAMERA_X = 0x10C0;
    private static final int CAMERA_Y = 0x0450;
    private static final int BOSS_SLOT = 12;
    private static final int BOSS_X = 0x11D0;
    private static final int BOSS_Y_BEFORE_DESCENT = 0x0423;
    private static final int PARENT_BITS = 0x38;
    private static final int BARREL_ACTIVATE_BIT = 1 << 1;

    private static SharedLevel sharedLevel;
    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;

    @BeforeAll
    static void loadAiz2() throws Exception {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        oldSkipIntros = configuration.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = configuration.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = configuration.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        configuration.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        // AIZ2 is zone 0, zero-based act index 1.
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_AIZ, 1);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
    }

    @BeforeEach
    void restoreSharedRuntime() {
        // @RequiresRom resets singleton runtime state before each method; the
        // shared fixture reloads/rewires the production LevelManager so object
        // terrain probes below read the live AIZ2 layout.
        HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
    }

    @Test
    void liveBossUsesNativeWaitEntriesAndGatesNapalmToKnuckles() throws Exception {
        Harness knuckles = Harness.create(PlayerCharacter.KNUCKLES);
        driveToActivationCallback(knuckles, true);
        assertEquals(BARREL_ACTIVATE_BIT,
                knuckles.boss().getCustomFlag(PARENT_BITS) & BARREL_ACTIVATE_BIT,
                "SWING callback entry 21 must publish the Knuckles barrel bit");

        Harness sonic = Harness.create(PlayerCharacter.SONIC_ALONE);
        driveToActivationCallback(sonic, true);
        assertEquals(0, sonic.boss().getCustomFlag(PARENT_BITS) & BARREL_ACTIVATE_BIT,
                "the same production callback must leave the barrel bit clear for Sonic");
        sonic.step(80);
        assertEquals(0, live(sonic.manager(), AizMinibossNapalmProjectile.class).size(),
                "Sonic must not create FallingShot objects after driving through the live callback");
    }

    @Test
    void liveBarrelsSpawnNativePairsAtActivationEntriesAndAfterCurrentSlots() throws Exception {
        Harness harness = Harness.create(PlayerCharacter.KNUCKLES);
        driveToActivationCallback(harness, false);

        Set<AizMinibossNapalmProjectile> observed =
                java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Integer, PairSpawn> byBarrelSubtype = new LinkedHashMap<>();
        for (int relativeEntry = 1; relativeEntry <= 64; relativeEntry++) {
            harness.step();
            for (AizMinibossNapalmProjectile projectile :
                    live(harness.manager(), AizMinibossNapalmProjectile.class)) {
                if (!observed.add(projectile)) {
                    continue;
                }
                AizMinibossFlameBarrelChild barrel = readObjectField(
                        projectile, "barrel", AizMinibossFlameBarrelChild.class);
                AizMinibossBarrelShotFlareChild flare = live(
                        harness.manager(), AizMinibossBarrelShotFlareChild.class).stream()
                        .filter(candidate -> readObjectField(candidate, "anchor", ObjectInstance.class) == barrel)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "FallingShot must be allocated after its native flare sibling"));
                byBarrelSubtype.put(projectile.getBarrelSubtype(), new PairSpawn(
                        relativeEntry, barrel, flare, projectile));
            }
        }

        assertEquals(List.of(0, 2, 4), new ArrayList<>(byBarrelSubtype.keySet()));
        assertEquals(List.of(32, 48, 64), byBarrelSubtype.values().stream()
                        .map(PairSpawn::activationRelativeEntry).toList(),
                "barrel delays 0/16/32 plus the native opening script yield the captured 16-entry stagger");
        for (PairSpawn pair : byBarrelSubtype.values()) {
            assertTrue(pair.flare().getSlotIndex() > pair.barrel().getSlotIndex(),
                    "flare must use AllocateObjectAfterCurrent from the barrel slot");
            assertTrue(pair.projectile().getSlotIndex() > pair.flare().getSlotIndex(),
                    "FallingShot must follow the flare in the native pair");
            assertEquals(2, pair.projectile().getChildSubtype());
            assertEquals(2, pair.projectile().getSpawn().subtype());
            assertEquals(0x98, ((TouchResponseProvider) pair.projectile()).getCollisionFlags());
            assertTrue(pair.projectile().usesCurrentTouchResponseState(),
                    "loc_68C96 publishes the post-movement collision-list state");
            assertSame(harness.boss(), readObjectField(
                    pair.projectile(), "parent", AbstractBossInstance.class));
            assertSame(pair.barrel(), readObjectField(
                    pair.projectile(), "barrel", AizMinibossFlameBarrelChild.class));
            assertSame(pair.barrel(), readObjectField(
                    pair.flare(), "anchor", ObjectInstance.class));
        }
    }

    @Test
    void romTerrainImpactsSpawnNativeExplosionGraphAndCollisionWindows() throws Exception {
        Harness harness = Harness.create(PlayerCharacter.KNUCKLES);
        driveToActivationCallback(harness, false);
        assertEquals(-2, ObjectTerrainUtils.checkFloorDist(0x10E4, 0x0508, 8).distance(),
                "precondition: live AIZ2 terrain owns the captured first impact surface");

        Set<AizMinibossNapalmExplosionChild> knownExplosions =
                java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        List<ExplosionGroup> groups = new ArrayList<>();
        for (int relativeEntry = 1; relativeEntry <= 380; relativeEntry++) {
            List<AizMinibossNapalmProjectile> before =
                    live(harness.manager(), AizMinibossNapalmProjectile.class);
            for (AizMinibossNapalmProjectile projectile : before) {
                assertEquals(0x98, projectile.getCollisionFlags(),
                        "FallingShot stays harmful through its floor-test dispatch");
            }

            harness.step();
            List<AizMinibossNapalmExplosionChild> newcomers = live(
                    harness.manager(), AizMinibossNapalmExplosionChild.class).stream()
                    .filter(knownExplosions::add)
                    .sorted(Comparator.comparingInt(child -> child.getSpawn().subtype()))
                    .toList();
            if (!newcomers.isEmpty()) {
                assertEquals(7, newcomers.size(),
                        "ChildObjDat_690D8 must allocate all seven explosion hitboxes");
                AizMinibossNapalmProjectile source = before.stream()
                        .filter(AbstractObjectInstance::isDestroyed)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "explosion children must come from the FallingShot destroyed this entry"));
                ExplosionGroup group = ExplosionGroup.created(harness.entryCount(), source, newcomers);
                group.assertNativeCreationGraph();
                groups.add(group);
            }
            groups.forEach(group -> group.observe(harness.entryCount()));
        }

        assertEquals(3, groups.size(), "all three live barrels must reach real AIZ2 terrain; live shots="
                + live(harness.manager(), AizMinibossNapalmProjectile.class).stream()
                        .map(shot -> String.format("(%04X,%04X,%s)",
                                shot.getX(), shot.getY(), readStateName(shot)))
                        .toList());
        assertEquals(0x10E4, groups.get(0).impactX(),
                "first impact X is a fixed-fixture comparison oracle, not a fitted terrain constant");
        assertEquals(0x0506, groups.get(0).impactY(),
                "first impact Y must be produced by the ROM-backed AIZ2 floor probe");
        assertEquals(0x1134, groups.get(1).impactX(),
                "second impact X is a fixed-fixture comparison oracle");
        assertEquals(0x0504, groups.get(1).impactY(),
                "second impact Y must be produced by the ROM-backed AIZ2 floor probe");
        groups.forEach(ExplosionGroup::assertNativeLifetime);
    }

    @Test
    void rewindPreservesExactPerBarrelGraphAndForwardEvolution() throws Exception {
        Harness harness = Harness.create(PlayerCharacter.KNUCKLES);
        driveToActivationCallback(harness, false);
        harness.step(64);
        assertEquals(3, live(harness.manager(), AizMinibossNapalmProjectile.class).size(),
                "pre-consumption capture requires all three production FallingShots");
        assertEquals(List.of(0, 0, 0), live(harness.manager(), AizMinibossFlameBarrelChild.class)
                        .stream().map(AizMinibossFlameBarrelChild::getPositionCounter).toList(),
                "the first snapshot must precede each FallingShot's `$39` consumption");

        RewindRegistry rewindRegistry = registryFor(harness.manager());
        CapturedTopology beforeDrop = CapturedTopology.capture(harness.manager());
        CompositeSnapshot beforeDropSnapshot = rewindRegistry.capture();

        List<FrameState> toMixedPhase = new ArrayList<>();
        for (int entry = 0; entry < 220; entry++) {
            harness.step();
            toMixedPhase.add(FrameState.capture(harness.manager()));
            if (!live(harness.manager(), AizMinibossNapalmExplosionChild.class).isEmpty()
                    && live(harness.manager(), AizMinibossNapalmProjectile.class).size() == 2) {
                break;
            }
        }
        assertFalse(toMixedPhase.isEmpty());
        assertEquals(2, live(harness.manager(), AizMinibossNapalmProjectile.class).size(),
                "later snapshot must mix a real explosion with two displaced FallingShots");
        assertTrue(live(harness.manager(), AizMinibossNapalmProjectile.class).stream()
                        .allMatch(projectile -> Math.abs(projectile.getX() - BOSS_X) > 0x40),
                "the later shots must be far enough from the three barrels to defeat geometric relinking");

        CapturedTopology mixed = CapturedTopology.capture(harness.manager());
        CompositeSnapshot mixedSnapshot = rewindRegistry.capture();
        List<FrameState> mixedFuture = captureForward(harness, 80);

        rewindRegistry.restore(mixedSnapshot);
        mixed.assertRestored(harness.manager());
        assertEquals(mixedFuture, captureForward(harness, 80),
                "later mixed projectile/explosion replay must preserve slots, links, collision, and lifetime");

        rewindRegistry.restore(beforeDropSnapshot);
        beforeDrop.assertRestored(harness.manager());
        assertEquals(toMixedPhase, captureForward(harness, toMixedPhase.size()),
                "pre-consumption replay must preserve per-barrel `$39`, facing, and selected drop positions");
        mixed.assertRestored(harness.manager());
    }

    private static void driveToActivationCallback(Harness harness, boolean assertEveryBoundary) {
        harness.step(); // INIT -> WAIT_TRIGGER
        assertEquals(2, harness.boss().getState().routine);
        harness.step(); // trigger -> WAIT, literal #180
        assertEquals(4, harness.boss().getState().routine);
        assertEquals(180, readIntField(harness.boss(), "waitTimer"));

        harness.step(180);
        assertEquals(4, harness.boss().getState().routine,
                "literal #180 must still be in WAIT on Obj_Wait entry 180");
        assertEquals(0, live(harness.manager(), AizMinibossFlameBarrelChild.class).size());
        harness.step();
        assertEquals(6, harness.boss().getState().routine,
                "literal #180 callback must run on following WAIT entry 181");
        assertEquals(0xAF, readIntField(harness.boss(), "waitTimer"));
        assertEquals(3, live(harness.manager(), AizMinibossFlameBarrelChild.class).size());

        harness.step(0xAF);
        assertEquals(6, harness.boss().getState().routine,
                "literal #$AF must still be in DESCEND on MoveSprite2/Obj_Wait entry 175");
        assertEquals(BOSS_Y_BEFORE_DESCENT + 0xAF, harness.boss().getY());
        harness.step();
        assertEquals(8, harness.boss().getState().routine,
                "literal #$AF callback must run on following DESCEND entry 176");
        assertEquals(BOSS_Y_BEFORE_DESCENT + 0xB0, harness.boss().getY());
        assertEquals(20, readIntField(harness.boss(), "waitTimer"));

        harness.step(20);
        assertEquals(8, harness.boss().getState().routine);
        assertEquals(0, harness.boss().getCustomFlag(PARENT_BITS) & BARREL_ACTIVATE_BIT,
                "activation must remain absent on SWING/MoveWaitTouch entry 20");
        assertEquals(0, live(harness.manager(), AizMinibossNapalmProjectile.class).size());
        harness.step();
        assertEquals(8, harness.boss().getState().routine,
                "the callback starts the flame delay without changing the swing routine");
        if (assertEveryBoundary && harness.character() == PlayerCharacter.KNUCKLES) {
            assertEquals(BARREL_ACTIVATE_BIT,
                    harness.boss().getCustomFlag(PARENT_BITS) & BARREL_ACTIVATE_BIT,
                    "activation first becomes visible on following SWING callback entry 21");
        }
    }

    private static List<FrameState> captureForward(Harness harness, int entries) {
        List<FrameState> states = new ArrayList<>(entries);
        for (int entry = 0; entry < entries; entry++) {
            harness.step();
            states.add(FrameState.capture(harness.manager()));
        }
        return states;
    }

    private static RewindRegistry registryFor(ObjectManager manager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        return registry;
    }

    private static ObjectRefId objectId(ObjectManager manager, ObjectInstance object) {
        ObjectRefId id = manager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "live production object must have a rewind identity");
        return id;
    }

    private static <T extends ObjectInstance> T objectById(
            ObjectManager manager, Class<T> type, ObjectRefId id) {
        RewindIdentityTable table = manager.captureIdentityContext().requireIdentityTable();
        return live(manager, type).stream()
                .filter(object -> id.equals(table.idFor(object)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing restored " + type.getSimpleName() + " " + id));
    }

    private static <T extends ObjectInstance> List<T> live(ObjectManager manager, Class<T> type) {
        return manager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .sorted(Comparator.comparingInt(TestAizMinibossNapalmProductionRoute::slotOf))
                .toList();
    }

    private static int slotOf(ObjectInstance object) {
        return object instanceof AbstractObjectInstance instance ? instance.getSlotIndex() : -1;
    }

    private static int readIntField(Object target, String fieldName) {
        try {
            return findField(target.getClass(), fieldName).getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("unable to read " + fieldName, e);
        }
    }

    private static String readStateName(Object target) {
        try {
            Object value = findField(target.getClass(), "state").get(target);
            return value == null ? "null" : value.toString();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("unable to read state", e);
        }
    }

    private static <T> T readObjectField(Object target, String fieldName, Class<T> type) {
        try {
            return type.cast(findField(target.getClass(), fieldName).get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("unable to read " + fieldName, e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private record PairSpawn(
            int activationRelativeEntry,
            AizMinibossFlameBarrelChild barrel,
            AizMinibossBarrelShotFlareChild flare,
            AizMinibossNapalmProjectile projectile) {
    }

    private static final class ExplosionGroup {
        // The higher-slot child consumes INIT on the impact pass itself, so
        // these are following-pass offsets from creation (native routine-4
        // dispatch numbers are one greater: 27,23,19,15,11,7,3).
        private static final int[] EXPECTED_HARMFUL_START = {26, 22, 18, 14, 10, 6, 2};

        private final int createdAtEntry;
        private final int sourceSlot;
        private final int impactX;
        private final int impactY;
        private final List<ExplosionTracker> trackers;

        private ExplosionGroup(
                int createdAtEntry,
                int sourceSlot,
                int impactX,
                int impactY,
                List<ExplosionTracker> trackers) {
            this.createdAtEntry = createdAtEntry;
            this.sourceSlot = sourceSlot;
            this.impactX = impactX;
            this.impactY = impactY;
            this.trackers = trackers;
        }

        static ExplosionGroup created(
                int createdAtEntry,
                AizMinibossNapalmProjectile source,
                List<AizMinibossNapalmExplosionChild> children) {
            return new ExplosionGroup(
                    createdAtEntry,
                    source.getSlotIndex(),
                    source.getX(),
                    source.getY(),
                    children.stream().map(ExplosionTracker::new).toList());
        }

        void assertNativeCreationGraph() {
            assertEquals(List.of(0, 2, 4, 6, 8, 10, 12), trackers.stream()
                    .map(tracker -> tracker.child.getSpawn().subtype()).toList());
            int previousSlot = sourceSlot;
            for (int index = 0; index < trackers.size(); index++) {
                AizMinibossNapalmExplosionChild child = trackers.get(index).child;
                assertTrue(child.getSlotIndex() > previousSlot,
                        "explosion allocation must skip occupied slots after the FallingShot");
                assertEquals(impactX + AizMinibossNapalmExplosionChild.X_OFFSETS[index], child.getX());
                assertEquals(impactY + AizMinibossNapalmExplosionChild.Y_OFFSETS[index], child.getY());
                assertEquals(0, child.getCollisionFlags(),
                        "new explosion child must not publish `$97` before routine 4");
                previousSlot = child.getSlotIndex();
            }
        }

        void observe(int currentEntry) {
            int relativeDispatch = currentEntry - createdAtEntry;
            if (relativeDispatch <= 0) {
                return;
            }
            trackers.forEach(tracker -> tracker.observe(relativeDispatch));
        }

        void assertNativeLifetime() {
            for (int index = 0; index < trackers.size(); index++) {
                ExplosionTracker tracker = trackers.get(index);
                assertEquals(EXPECTED_HARMFUL_START[index], tracker.firstHarmfulDispatch,
                        "subtype delays must stagger routine-4 collision by four object entries");
                assertEquals(21, tracker.harmfulDispatches,
                        "AniRaw_BossExplosion must publish `$97` for exactly 21 dispatches");
                assertEquals(EXPECTED_HARMFUL_START[index] + 21, tracker.destroyedDispatch,
                        "the child must delete on the dispatch after its final harmful animation hold");
            }
        }

        int impactX() {
            return impactX;
        }

        int impactY() {
            return impactY;
        }
    }

    private static final class ExplosionTracker {
        private final AizMinibossNapalmExplosionChild child;
        private int firstHarmfulDispatch = -1;
        private int harmfulDispatches;
        private int destroyedDispatch = -1;

        private ExplosionTracker(AizMinibossNapalmExplosionChild child) {
            this.child = child;
        }

        private void observe(int relativeDispatch) {
            int collision = child.getCollisionFlags();
            if (collision != 0) {
                assertEquals(0x97, collision);
                if (firstHarmfulDispatch < 0) {
                    firstHarmfulDispatch = relativeDispatch;
                }
                harmfulDispatches++;
            }
            if (child.isDestroyed() && destroyedDispatch < 0) {
                destroyedDispatch = relativeDispatch;
            }
        }
    }

    private record Identified(ObjectRefId id, int slot) {
    }

    private record LinkedObject(ObjectRefId id, int slot, ObjectRefId ownerId) {
    }

    private record CapturedTopology(
            Identified boss,
            Map<Integer, Identified> barrels,
            List<LinkedObject> flares,
            List<LinkedObject> projectiles) {

        static CapturedTopology capture(ObjectManager manager) {
            AizMinibossInstance boss = live(manager, AizMinibossInstance.class).getFirst();
            Identified bossId = new Identified(objectId(manager, boss), boss.getSlotIndex());
            Map<Integer, Identified> barrels = new LinkedHashMap<>();
            for (AizMinibossFlameBarrelChild barrel :
                    live(manager, AizMinibossFlameBarrelChild.class)) {
                barrels.put(barrel.getBarrelSubtype(),
                        new Identified(objectId(manager, barrel), barrel.getSlotIndex()));
            }
            List<LinkedObject> flares = live(manager, AizMinibossBarrelShotFlareChild.class)
                    .stream().map(flare -> {
                        ObjectInstance anchor = readObjectField(flare, "anchor", ObjectInstance.class);
                        return new LinkedObject(objectId(manager, flare), flare.getSlotIndex(),
                                objectId(manager, anchor));
                    }).toList();
            List<LinkedObject> projectiles = live(manager, AizMinibossNapalmProjectile.class)
                    .stream().map(projectile -> {
                        ObjectInstance barrel = readObjectField(projectile, "barrel", ObjectInstance.class);
                        return new LinkedObject(objectId(manager, projectile), projectile.getSlotIndex(),
                                objectId(manager, barrel));
                    }).toList();
            return new CapturedTopology(bossId, barrels, flares, projectiles);
        }

        void assertRestored(ObjectManager manager) {
            AizMinibossInstance restoredBoss = objectById(
                    manager, AizMinibossInstance.class, boss.id());
            assertEquals(boss.slot(), restoredBoss.getSlotIndex());

            for (Map.Entry<Integer, Identified> entry : barrels.entrySet()) {
                AizMinibossFlameBarrelChild barrel = objectById(
                        manager, AizMinibossFlameBarrelChild.class, entry.getValue().id());
                assertEquals(entry.getKey(), barrel.getBarrelSubtype());
                assertEquals(entry.getValue().slot(), barrel.getSlotIndex());
                assertSame(restoredBoss, readObjectField(barrel, "parent", AbstractBossInstance.class),
                        "each subtype-0/2/4 barrel must retain the exact restored boss");
            }
            for (LinkedObject expected : flares) {
                AizMinibossBarrelShotFlareChild flare = objectById(
                        manager, AizMinibossBarrelShotFlareChild.class, expected.id());
                AizMinibossFlameBarrelChild owner = objectById(
                        manager, AizMinibossFlameBarrelChild.class, expected.ownerId());
                assertEquals(expected.slot(), flare.getSlotIndex());
                assertSame(owner, readObjectField(flare, "anchor", ObjectInstance.class),
                        "flare anchor must resolve by captured ObjectRefId, not nearest geometry");
            }
            for (LinkedObject expected : projectiles) {
                AizMinibossNapalmProjectile projectile = objectById(
                        manager, AizMinibossNapalmProjectile.class, expected.id());
                AizMinibossFlameBarrelChild owner = objectById(
                        manager, AizMinibossFlameBarrelChild.class, expected.ownerId());
                assertEquals(expected.slot(), projectile.getSlotIndex());
                assertSame(restoredBoss, readObjectField(
                        projectile, "parent", AbstractBossInstance.class));
                assertSame(owner, readObjectField(
                        projectile, "barrel", AizMinibossFlameBarrelChild.class),
                        "FallingShot source barrel must resolve by captured ObjectRefId");
            }
        }
    }

    private record BossFrame(ObjectRefId id, int slot, int routine, int x, int y, int parentBits) {
    }

    private record BarrelFrame(
            ObjectRefId id,
            int slot,
            int subtype,
            int positionCounter,
            boolean facingFlipped) {
    }

    private record FlareFrame(
            ObjectRefId id,
            int slot,
            ObjectRefId anchorId,
            int x,
            int y,
            int sequenceIndex,
            int frameTimer) {
    }

    private record ProjectileFrame(
            ObjectRefId id,
            int slot,
            ObjectRefId barrelId,
            int childSubtype,
            int barrelSubtype,
            int x,
            int y,
            int collision,
            int priority,
            String state) {
    }

    private record ExplosionFrame(
            ObjectRefId id,
            int slot,
            int subtype,
            int x,
            int y,
            int collision,
            String state) {
    }

    private record FrameState(
            BossFrame boss,
            List<BarrelFrame> barrels,
            List<FlareFrame> flares,
            List<ProjectileFrame> projectiles,
            List<ExplosionFrame> explosions) {

        static FrameState capture(ObjectManager manager) {
            RewindIdentityTable table = manager.captureIdentityContext().requireIdentityTable();
            AizMinibossInstance boss = live(manager, AizMinibossInstance.class).getFirst();
            BossFrame bossFrame = new BossFrame(
                    table.idFor(boss), boss.getSlotIndex(), boss.getState().routine,
                    boss.getX(), boss.getY(), boss.getCustomFlag(PARENT_BITS));
            List<BarrelFrame> barrels = live(manager, AizMinibossFlameBarrelChild.class).stream()
                    .map(barrel -> new BarrelFrame(
                            table.idFor(barrel), barrel.getSlotIndex(), barrel.getBarrelSubtype(),
                            barrel.getPositionCounter(), barrel.isFacingFlipped()))
                    .toList();
            List<FlareFrame> flares = live(manager, AizMinibossBarrelShotFlareChild.class).stream()
                    .map(flare -> {
                        ObjectInstance anchor = readObjectField(flare, "anchor", ObjectInstance.class);
                        return new FlareFrame(
                                table.idFor(flare), flare.getSlotIndex(), table.idFor(anchor),
                                flare.getX(), flare.getY(),
                                readIntField(flare, "sequenceIndex"),
                                readIntField(flare, "frameTimer"));
                    }).toList();
            List<ProjectileFrame> projectiles = live(manager, AizMinibossNapalmProjectile.class)
                    .stream().map(projectile -> {
                        ObjectInstance barrel = readObjectField(projectile, "barrel", ObjectInstance.class);
                        return new ProjectileFrame(
                                table.idFor(projectile), projectile.getSlotIndex(), table.idFor(barrel),
                                projectile.getChildSubtype(), projectile.getBarrelSubtype(),
                                projectile.getX(), projectile.getY(), projectile.getCollisionFlags(),
                                projectile.getPriorityBucket(), readStateName(projectile));
                    }).toList();
            List<ExplosionFrame> explosions = live(manager, AizMinibossNapalmExplosionChild.class)
                    .stream().map(explosion -> new ExplosionFrame(
                            table.idFor(explosion), explosion.getSlotIndex(), explosion.getSpawn().subtype(),
                            explosion.getX(), explosion.getY(), explosion.getCollisionFlags(),
                            readStateName(explosion)))
                    .toList();
            return new FrameState(bossFrame, barrels, flares, projectiles, explosions);
        }
    }

    private record Harness(
            ObjectManager manager,
            AizMinibossInstance boss,
            PlayerCharacter character,
            Camera camera,
            int[] entryCounter) {

        static Harness create(PlayerCharacter character) throws IOException {
            int routeCameraX = character == PlayerCharacter.KNUCKLES ? CAMERA_X : 0x10E0;
            Camera camera = new Camera() {
                @Override public short getX() { return (short) routeCameraX; }
                @Override public short getY() { return (short) CAMERA_Y; }
                @Override public short getWidth() { return 320; }
                @Override public short getHeight() { return 224; }
                @Override public boolean isVerticalWrapEnabled() { return false; }
            };
            ZoneRuntimeRegistry runtimeRegistry = new ZoneRuntimeRegistry();
            Sonic3kAIZEvents events = new Sonic3kAIZEvents(null);
            runtimeRegistry.install(new AizZoneRuntimeState(1, character, events));
            GameStateManager gameState = new GameStateManager();
            Rom rom = RomManager.getInstance().getRom();
            ObjectManager[] holder = new ObjectManager[1];
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public Level currentLevel() { return sharedLevel.level(); }
                @Override public LevelManager levelManager() { return GameServices.level(); }
                @Override public int romZoneId() { return Sonic3kZoneIds.ZONE_AIZ; }
                @Override public int currentAct() { return 1; }
                @Override public int featureZoneId() { return Sonic3kZoneIds.ZONE_AIZ; }
                @Override public int featureActId() { return 1; }
                @Override public ZoneRuntimeRegistry zoneRuntimeRegistry() { return runtimeRegistry; }
                @Override public GameStateManager gameState() { return gameState; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public SonicConfigurationService configuration() {
                    return SonicConfigurationService.getInstance();
                }
                @Override public RomManager romManager() { return RomManager.getInstance(); }
                @Override public Rom rom() { return rom; }
            };
            ObjectManager manager = new ObjectManager(
                    List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            AizMinibossInstance boss = new AizMinibossInstance(new ObjectSpawn(
                    BOSS_X, BOSS_Y_BEFORE_DESCENT, Sonic3kObjectIds.AIZ_MINIBOSS,
                    0, 0, false, 0));
            manager.addDynamicObjectAtSlot(boss, BOSS_SLOT);
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            return new Harness(manager, boss, character, camera, new int[1]);
        }

        void step() {
            manager.update(camera.getX(), null, List.of(), entryCounter[0], false);
            entryCounter[0]++;
        }

        void step(int entries) {
            for (int entry = 0; entry < entries; entry++) {
                step();
            }
        }

        int entryCount() {
            return entryCounter[0];
        }
    }
}
