package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.*;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.difficulty.FlyingBatteryBossRecipe;
import com.openggf.game.sonic3k.events.FbzObjectEventBridge;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Live native graph checks; no whole-route or visual-readability certification. */
class TestFbzCampaignBossRecipe {
    @AfterEach void resetGraphics() { GraphicsManager.getInstance().resetState(); }

    static Stream<Arguments> encounters() {
        return Arrays.stream(FlyingBatteryBossRecipe.values()).flatMap(recipe ->
                Stream.of(PlayerCharacter.SONIC_ALONE, PlayerCharacter.SONIC_AND_TAILS,
                        PlayerCharacter.TAILS_ALONE, PlayerCharacter.KNUCKLES)
                        .map(character -> Arguments.of(recipe, character)));
    }

    @ParameterizedTest @MethodSource("encounters")
    void actualArmStartsKeepTheImmediateWaveAndNativeDispatchAndRotationBudgets(
            FlyingBatteryBossRecipe recipe, PlayerCharacter character) throws Exception {
        World world = new World(character);
        FbzEndBossInstance boss = world.boss(recipe);
        completeGraph(boss);
        assertEquals(16, world.manager.getActiveObjects().size());
        prepareAttack(boss);
        world.player.setCentreX((short) (boss.getX() + 0x200));
        List<List<Integer>> starts = List.of(new ArrayList<>(), new ArrayList<>());
        int executions = 0, peak = 16;
        List<String> peakRoles = List.of();
        Set<ObjectInstance> seenFlames = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Integer> volleys = new ArrayList<>();
        List<Integer> idleAndAttackable = new ArrayList<>();
        while (boss.phase() == FbzEndBossInstance.Phase.ATTACK && executions < 2000) {
            int[] before = boss.arms().stream().mapToInt(arm -> integer(arm, "motionPhase")).toArray();
            world.tick();
            int live = world.manager.getActiveObjects().size();
            if (live > peak) { peak = live; peakRoles = roles(world.manager); }
            long born = world.manager.getActiveObjects().stream().filter(FbzEndBossFlameChild.class::isInstance)
                    .filter(seenFlames::add).count();
            if (born != 0) { assertEquals(9, born); volleys.add(world.frame); }
            assertEquals(0x16, boss.getCollisionFlags(), "extra beats must not introduce root immunity");
            if (boss.arms().stream().allMatch(arm -> integer(arm, "motionPhase") == 1)) idleAndAttackable.add(executions);
            for (int arm = 0; arm < 2; arm++) {
                if (before[arm] == 1 && integer(boss.arm(arm), "motionPhase") == 2) {
                    starts.get(arm).add(executions);
                }
            }
            executions++;
        }
        int waves = recipe == FlyingBatteryBossRecipe.EXTRA_BEAT ? 11 : 10;
        assertEquals(recipe == FlyingBatteryBossRecipe.EXTRA_BEAT ? 384 : 352, executions,
                "Knuckles weapon activity must not freeze the native countdown");
        assertEquals(FbzEndBossInstance.Phase.ROTATION, boss.phase());
        for (List<Integer> armStarts : starts) {
            assertEquals(waves, armStarts.size(), "count actual phase1->2 child starts, including the immediate wave");
            assertEquals(0, armStarts.getFirst());
            assertEquals(63, armStarts.get(1), "first expiry uses all64 eligible dispatches");
            for (int i = 2; i < armStarts.size(); i++) assertEquals(32, armStarts.get(i) - armStarts.get(i - 1));
        }
        for (int i = 1; i < starts.getFirst().size(); i++) {
            int previous = starts.getFirst().get(i - 1), next = starts.getFirst().get(i);
            assertTrue(idleAndAttackable.stream().filter(frame -> frame > previous && frame < next).count() >= 15,
                    "each native inter-wave interval retains at least15 updates with both arms idle and the root attackable");
        }
        assertEquals(8, boss.getState().hitCount);
        assertEquals(9, FbzEndBossInstance.attackRounds(), "public stock oracle does not become the preset");
        int rotationExecutions = character == PlayerCharacter.KNUCKLES ? 256 : 512;
        int initialAngle = boss.angle();
        for (int tick = 1; tick < rotationExecutions; tick++) {
            world.tick();
            assertEquals(FbzEndBossInstance.Phase.ROTATION, boss.phase());
            assertEquals(0x16, boss.getCollisionFlags());
            int live = world.manager.getActiveObjects().size();
            if (live > peak) { peak = live; peakRoles = roles(world.manager); }
            long born = world.manager.getActiveObjects().stream().filter(FbzEndBossFlameChild.class::isInstance)
                    .filter(seenFlames::add).count();
            if (born != 0) { assertEquals(9, born); volleys.add(world.frame); }
        }
        // loc_70B18's nine-child volley has a raw5F cooldown. This owner dispatches
        // another volley97 updates later; waits plus raw animations leave indices0..4
        // alive for108/105/102/102/99 updates. Five old flames overlap the new nine.
        if (character == PlayerCharacter.KNUCKLES) {
            assertEquals(30, peak, "16 steady nodes plus14 overlapping flame nodes");
            for (int index = 0; index < 9; index++) {
                assertEquals(index < 5 ? 2 : 1, Collections.frequency(peakRoles, "flame:" + index));
            }
            for (int volley = 1; volley < volleys.size(); volley++) {
                assertEquals(97, volleys.get(volley) - volleys.get(volley - 1));
            }
        } else {
            assertEquals(16, peak);
            assertTrue(volleys.isEmpty(), "distant Sonic/Tails keeps the proximity gate closed");
        }
        world.tick();
        assertEquals(FbzEndBossInstance.Phase.ATTACK, boss.phase());
        assertEquals(initialAngle, boss.angle());
        assertEquals(recipe == FlyingBatteryBossRecipe.EXTRA_BEAT ? 10 : 9, integer(boss, "attackRoundsLeft"));
        assertEquals(0x3F, integer(boss, "timer"));
        assertEquals(0, world.samples, "native cycle transitions never query creator services");
    }

    @ParameterizedTest @EnumSource(value = PlayerCharacter.class, names = {"SONIC_AND_TAILS", "KNUCKLES"})
    void actualWeaponChildKeepsProximityLatchBusyFreezeAndNativeCharacterBypass(PlayerCharacter character) throws Exception {
        World world = new World(character);
        FbzEndBossInstance boss = world.boss(FlyingBatteryBossRecipe.EXTRA_BEAT);
        completeGraph(boss); prepareAttack(boss);
        world.player.setCentreX((short) (boss.getX() + 8)); world.player.setAir(false);
        world.tick();
        assertTrue(boss.isWeaponTriggerActive());
        assertTrue((boolean) value(boss.weapon(), "firing"));
        assertEquals(0x5F, integer(boss.weapon(), "waitTimer"));
        assertEquals(0x3E, integer(boss, "timer"));
        assertEquals(character != PlayerCharacter.KNUCKLES, value(boss, "attackLatch"));
        world.player.setCentreX((short) (boss.getX() + 0x200));
        for (int i = 0; i < 10; i++) world.tick();
        assertEquals(character == PlayerCharacter.KNUCKLES ? 0x34 : 0x3E, integer(boss, "timer"));
        assertEquals(0x55, integer(boss.weapon(), "waitTimer"));
        assertEquals(FbzEndBossFlameChild.nativeVolleyCount(), world.manager.activeObjectsOfType(FbzEndBossFlameChild.class).size());
    }

    @ParameterizedTest @EnumSource(value = PlayerCharacter.class, names = {"SONIC_AND_TAILS", "KNUCKLES"})
    void nativeRootRetainsEightHitsAndTheEntireInvulnerabilityAndDefeatBoundary(PlayerCharacter character) throws Exception {
        World world = new World(character);
        FbzEndBossInstance boss = world.boss(FlyingBatteryBossRecipe.EXTRA_BEAT);
        prepareAttack(boss);
        assertEquals(8, boss.getState().hitCount);
        for (int hit = 1; hit <= 8; hit++) {
            assertEquals(0x16, boss.getCollisionFlags());
            boss.onPlayerAttack(world.player, null);
            boss.onPlayerAttack(world.player, null);
            assertEquals(8 - hit, boss.getState().hitCount, "pending native hit cannot be double-counted");
            invoke(boss, "updateNativeHitHandler");
            if (hit < 8) {
                assertEquals(0x1F, boss.getState().invulnerabilityTimer);
                for (int tick = 0; tick < 31; tick++) {
                    boss.onPlayerAttack(world.player, null);
                    assertEquals(8 - hit, boss.getState().hitCount);
                    invoke(boss, "updateNativeHitHandler");
                }
                assertFalse(boss.getState().invulnerable);
            }
        }
        assertTrue(world.levelState.isTimerPaused());
        assertEquals(FbzEndBossInstance.Phase.DEFEAT_RECENTER, boss.phase());
        assertEquals(0, boss.getCollisionFlags());
        assertEquals(1000, world.gameState.getScore());
    }

    @ParameterizedTest @EnumSource(value = PlayerCharacter.class, names = {"SONIC_AND_TAILS", "KNUCKLES"})
    void destructiveGraphRestorePreservesCommittedEnumCounterAndForwardReplayWithoutLookup(PlayerCharacter character) throws Exception {
        World world = new World(character);
        world.availableRecipe = FlyingBatteryBossRecipe.EXTRA_BEAT;
        FbzEndBossEventControlInstance controller = world.controllerAtBirth();
        controller.update(0, world.player);
        FbzEndBossInstance boss = world.manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        completeGraph(boss); prepareAttack(boss);
        for (int tick = 0; tick < 80; tick++) world.tick();
        world.availableRecipe = null;
        CompositeSnapshot snapshot = capture(world.manager);
        Map<ObjectRefId, Integer> beforeIds = idSlots(world.manager);
        int remaining = integer(boss, "attackRoundsLeft");
        world.tick();
        CompositeSnapshot expected = capture(world.manager);
        world.manager.setRewindInPlaceRestoreEnabledForTest(false);
        world.manager.getActiveObjects().stream().toList().forEach(world.manager::removeDynamicObject);
        restore(world.manager, snapshot);
        FbzEndBossInstance restored = world.manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        assertNotSame(boss, restored);
        assertEquals(beforeIds, idSlots(world.manager));
        assertEquals(FlyingBatteryBossRecipe.EXTRA_BEAT, value(restored, "encounterRecipe"));
        assertEquals(true, value(restored, "encounterRecipeCommitted"));
        assertEquals(remaining, integer(restored, "attackRoundsLeft"));
        assertEquals(2, restored.arms().size()); assertEquals(8, restored.chainLinks().size());
        assertTrue(restored.arms().stream().allMatch(arm -> arm.boss() == restored));
        assertThrows(IllegalStateException.class, () -> restored.commitEncounterRecipe(FlyingBatteryBossRecipe.STOCK));
        world.tick();
        CompositeSnapshot replayed = capture(world.manager);
        assertTrue(RewindSnapshotDiff.diffKey("object-manager", expected.get("object-manager"), replayed.get("object-manager")).isEmpty());
        assertEquals(1, world.samples, "constructor/recreate/forward native lifetime must not resample a withdrawn service");
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15})
    void everyExtraBeatChildAllocationPrefixSurvivesDestructiveRestoreWithoutHealing(int prefix) throws Exception {
        World world = new World(PlayerCharacter.SONIC_ALONE);
        FbzEndBossInstance boss = world.boss(FlyingBatteryBossRecipe.EXTRA_BEAT);
        world.manager.reserveAllButNFreeSlots(prefix);
        completeGraph(boss);
        List<String> roles = roles(world.manager);
        assertEquals(prefix + 1, roles.size());
        CompositeSnapshot snapshot = capture(world.manager);
        world.manager.setRewindInPlaceRestoreEnabledForTest(false);
        world.manager.getActiveObjects().stream().toList().forEach(world.manager::removeDynamicObject);
        restore(world.manager, snapshot);
        FbzEndBossInstance restored = world.manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        assertEquals(FlyingBatteryBossRecipe.EXTRA_BEAT, value(restored, "encounterRecipe"));
        assertEquals(roles, roles(world.manager));
        Set<Integer> used = new HashSet<>();
        world.manager.getActiveObjects().forEach(o -> used.add(((AbstractObjectInstance) o).getSlotIndex()));
        for (int slot = 4; slot < 94; slot++) if (!used.contains(slot)) world.manager.releaseDynamicSlot(slot);
        completeGraph(restored);
        assertEquals(roles, roles(world.manager), "failed native child suffixes must not heal after capacity returns");
    }

    @Test void commitmentRequiresAnAcceptedFreshBossAndIsNeverRepeatable() throws Exception {
        FbzEndBossInstance detached = new FbzEndBossInstance(spawn());
        assertEquals(FlyingBatteryBossRecipe.STOCK, value(detached, "encounterRecipe"));
        assertThrows(IllegalStateException.class, () -> detached.commitEncounterRecipe(FlyingBatteryBossRecipe.EXTRA_BEAT));
        World world = new World(PlayerCharacter.SONIC_ALONE);
        FbzEndBossInstance committed = world.boss(null);
        assertEquals(FlyingBatteryBossRecipe.STOCK, value(committed, "encounterRecipe"));
        assertThrows(IllegalStateException.class, () -> committed.commitEncounterRecipe(FlyingBatteryBossRecipe.EXTRA_BEAT));
        FbzEndBossInstance started = world.manager.createDynamicObject(() -> new FbzEndBossInstance(spawn()));
        set(started, "nativeStarted", true);
        assertThrows(IllegalStateException.class, () -> started.commitEncounterRecipe(FlyingBatteryBossRecipe.EXTRA_BEAT));
    }

    static ObjectSpawn spawn() { return new ObjectSpawn(0x31C0, 0x690, 0xAC, 0, 0, false, 0); }
    static void prepareAttack(FbzEndBossInstance boss) throws Exception {
        set(boss, "nativeStarted", true);
        for (var arm : boss.arms()) set(arm, "motionPhase", 1);
        invoke(boss, "beginAttack");
    }
    static void completeGraph(FbzEndBossInstance boss) {
        boss.spawnNativeGraph();
        if (boss.ship() != null) boss.ship().update(0, null);
        boss.arms().forEach(arm -> arm.update(0, null));
        if (boss.joint(0) != null) boss.joint(0).update(0, null);
        if (boss.joint(1) != null) boss.joint(1).update(0, null);
        if (boss.weapon() != null) boss.weapon().update(0, null);
    }
    static CompositeSnapshot capture(ObjectManager manager) { var registry = new RewindRegistry(); registry.register(manager.rewindSnapshottable()); return registry.capture(); }
    static void restore(ObjectManager manager, CompositeSnapshot snapshot) { var registry = new RewindRegistry(); registry.register(manager.rewindSnapshottable()); registry.restore(snapshot); }
    static Map<ObjectRefId, Integer> idSlots(ObjectManager manager) {
        var ids = manager.captureIdentityContext().requireIdentityTable();
        Map<ObjectRefId, Integer> result = new HashMap<>();
        for (var object : manager.getActiveObjects()) result.put(ids.idFor(object), ((AbstractObjectInstance) object).getSlotIndex());
        return result;
    }
    static List<String> roles(ObjectManager manager) {
        return manager.getActiveObjects().stream().filter(FbzEndBossGraphMember.class::isInstance)
                .map(o -> ((FbzEndBossGraphMember) o).rewindRole()).sorted().toList();
    }
    static Field field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new AssertionError(name);
    }
    static Object value(Object target, String name) { try { return field(target, name).get(target); } catch (IllegalAccessException e) { throw new AssertionError(e); } }
    static int integer(Object target, String name) { return (int) value(target, name); }
    static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
    static void invoke(Object target, String name) throws Exception { Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(target); }

    static final class World {
        final SonicConfigurationService config = SonicConfigurationService.createStandalone();
        final Camera camera = new Camera(config);
        final GameStateManager gameState = new GameStateManager();
        final LevelGamestate levelState = new LevelGamestate();
        final TestPlayableSprite player = new TestPlayableSprite();
        final Bridge bridge = new Bridge();
        final List<String> log = bridge.log;
        int zone = 4, samples, creations, frame;
        FlyingBatteryBossRecipe availableRecipe;
        boolean abortNextSample;
        ObjectManager manager;
        final Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry() {
            @Override protected int currentRomZoneId() { return zone; }
            @Override public ObjectInstance create(ObjectSpawn spawn) { creations++; log.add("create:" + spawn.objectId()); return super.create(spawn); }
        };
        final GameModule module = new Sonic3kGameModule() {
            @Override public ObjectRegistry createObjectRegistry() { return registry; }
            @Override public <T> T getGameService(Class<T> type) {
                if (type == FlyingBatteryBossRecipe.class) {
                    samples++; log.add("sample");
                    FbzEndBossInstance boss = manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
                    assertTrue(boss.getSlotIndex() >= 0); assertFalse((boolean) value(boss, "nativeStarted"));
                    if (abortNextSample) { abortNextSample = false; throw new DeclinedRecipe(); }
                    return type.cast(availableRecipe);
                }
                return super.getGameService(type);
            }
        };
        final StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public Camera camera() { return camera; }
            @Override public SonicConfigurationService configuration() { return config; }
            @Override public GameStateManager gameState() { return gameState; }
            @Override public LevelGamestate levelGamestate() { return levelState; }
            @Override public GameModule gameModule() { return module; }
            @Override public LevelEventProvider levelEventProvider() { return bridge; }
            @Override public int romZoneId() { return zone; }
        };
        World(PlayerCharacter character) {
            GraphicsManager.getInstance().initHeadless();
            services.withPlayerQuery(new ObjectPlayerQuery(() -> player, List::of));
            Sonic3kFBZEvents events = new Sonic3kFBZEvents(); events.init(1);
            services.zoneRuntimeRegistry().install(new FbzZoneRuntimeState(1, character, events));
            manager = new ObjectManager(List.of(), registry, 0, null, null, GraphicsManager.getInstance(), camera, services);
            manager.reset(0);
            player.setCentreX((short) 0x3400); player.setCentreY((short) 0x690); player.setAir(false);
        }
        FbzEndBossInstance boss(FlyingBatteryBossRecipe recipe) {
            FbzEndBossInstance boss = manager.createDynamicObject(() -> new FbzEndBossInstance(spawn()));
            assertNotNull(boss); boss.commitEncounterRecipe(recipe); return boss;
        }
        FbzEndBossEventControlInstance controllerAtBirth() throws Exception {
            var controller = manager.createDynamicObject(FbzEndBossEventControlInstance::new);
            set(controller, "initialized", true);
            set(controller, "phaseOrdinal", FbzEndBossEventControlInstance.Phase.WAIT_BOSS_STAGE.ordinal());
            bridge.stage = 0x0C;
            return controller;
        }
        void tick() { manager.update(0, player, List.of(), frame++); }
    }
    static final class DeclinedRecipe extends ObjectCallbackAbortException {
        DeclinedRecipe() { super("controlled optional provider first fault", null); }
    }
    static final class Bridge implements FbzObjectEventBridge, LevelEventProvider {
        final List<String> log = new ArrayList<>();
        int stage; boolean shake = true, rebase;
        @Override public void initLevel(int zone, int act) { }
        @Override public void update() { }
        @Override public void setMagneticState(Sonic3kFBZEvents.MagneticPolarity polarity, int timerPhase) { }
        @Override public void setCloudRewindId(int index, ObjectRefId id) { }
        @Override public void setCloudCleanupTerminal(boolean value) { }
        @Override public void setBossLoadPositionAdjustmentPending(boolean value) { rebase = value; log.add("rebase:" + value); }
        @Override public int getAct2ForegroundStage() { return stage; }
        @Override public void setAct2ForegroundStage(int stage) { this.stage = stage; }
        @Override public void setBossBackgroundOffsets(int x, int y) { }
        @Override public void setPlaneAssignmentMode(Sonic3kFBZEvents.PlaneAssignmentMode mode) { }
        @Override public void setCollisionMode(Sonic3kFBZEvents.CollisionMode mode, int x, int y) { }
        @Override public void setScreenShakeState(boolean active, int offset, int phase) { shake = active; }
        @Override public void setScreenShakeActive(boolean active) { shake = active; log.add("shake:" + active); }
        @Override public boolean isScreenShakeActive() { return shake; }
    }
}
