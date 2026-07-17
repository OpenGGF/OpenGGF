package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzEndBossRewind {
    @AfterEach void resetGraphics() { GraphicsManager.getInstance().resetState(); }

    @Test
    void completeLiveGraphCaptureRemoveRestorePreservesRolesIdsAndSlots() {
        GraphicsManager.getInstance().initHeadless();
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), null, services);
        holder[0] = manager;
        manager.reset(0);
        FbzEndBossInstance boss = manager.createDynamicObject(() -> new FbzEndBossInstance(
                new ObjectSpawn(0x31C0, 0x690, Sonic3kObjectIds.FBZ_END_BOSS,
                        0, 0, false, 0)));
        assertNotNull(boss);
        boss.spawnNativeGraph();
        assertEquals(5, roleSlots(manager).size());
        boss.ship().update(0, null);
        boss.arms().forEach(arm -> arm.update(0, null));
        boss.joint(0).update(0, null);
        boss.joint(1).update(0, null);
        Map<String, Integer> beforeSlots = roleSlots(manager);
        assertEquals(16, beforeSlots.size());
        int rootSlot = beforeSlots.get("root");
        assertEquals(rootSlot + 5, beforeSlots.get("head"));
        assertEquals(rootSlot + 6, beforeSlots.get("joint:0"));
        assertEquals(rootSlot + 7, beforeSlots.get("joint:1"));
        assertEquals(rootSlot + 8, beforeSlots.get("link:0:0"));
        assertEquals(rootSlot + 12, beforeSlots.get("link:1:0"));

        RewindRegistry rewind = new RewindRegistry();
        rewind.register(manager.rewindSnapshottable());
        CompositeSnapshot snapshot = rewind.capture();
        manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
        rewind.restore(snapshot);

        assertEquals(beforeSlots, roleSlots(manager), () -> manager.getActiveObjects().stream()
                .map(o -> o.getClass().getSimpleName() + ":" + o.isDestroyed() + ":" + ((AbstractObjectInstance)o).getSlotIndex())
                .toList().toString());
        FbzEndBossInstance restored = manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        assertEquals(2, restored.arms().size());
        assertEquals(8, restored.chainLinks().size());
        assertNotNull(restored.ship());
        assertNotNull(restored.weapon());
        assertSame(restored, restored.ship().boss());
        assertTrue(restored.chainLinks().stream().allMatch(link -> link.boss() == restored));
    }

    @Test
    void capturedEscapeExplosionControllerAndShipFlameRestoreAtTheirExactSlots() {
        GraphicsManager.getInstance().initHeadless();
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), null, services);
        holder[0] = manager;
        manager.reset(0);
        FbzEndBossInstance boss = manager.createDynamicObject(() -> new FbzEndBossInstance(
                new ObjectSpawn(0x31C0, 0x690, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0)));
        boss.spawnNativeGraph();
        boss.ship().update(0, null);
        boss.arms().forEach(arm -> arm.update(0, null));
        boss.joint(0).update(0, null);
        boss.joint(1).update(0, null);
        FbzEndBossShipExplosionController explosions = manager.createDynamicObject(
                () -> new FbzEndBossShipExplosionController(boss, boss.ship()));
        FbzEndBossShipFlameChild flame = manager.createDynamicObject(
                () -> new FbzEndBossShipFlameChild(boss, boss.ship()));
        boss.attach(explosions);
        boss.attach(flame);
        Map<String, Integer> before = roleSlots(manager);
        assertEquals(18, before.size());

        RewindRegistry rewind = new RewindRegistry();
        rewind.register(manager.rewindSnapshottable());
        CompositeSnapshot snapshot = rewind.capture();
        manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
        rewind.restore(snapshot);

        assertEquals(before, roleSlots(manager));
        FbzEndBossInstance restored = manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        assertSame(restored, manager.activeObjectsOfType(FbzEndBossShipExplosionController.class).getFirst().boss());
        assertSame(restored, manager.activeObjectsOfType(FbzEndBossShipFlameChild.class).getFirst().boss());
    }

    @Test
    void attackLatchAndNativeP2LockHelperRoundTripAtExactSlots() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        TestPlayableSprite p1 = new TestPlayableSprite();
        TestPlayableSprite p2 = new TestPlayableSprite();
        TestPlayableSprite extra = new TestPlayableSprite();
        SidekickCpuController p2Cpu = new SidekickCpuController(p2, p1);
        SidekickCpuController extraCpu = new SidekickCpuController(extra, p2);
        setInt(p2Cpu, "controlCounter", 600);
        setInt(extraCpu, "controlCounter", 321);
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> p1, () -> List.of(p2, extra));
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public ObjectPlayerQuery playerQuery() { return query; }
            @Override public List<com.openggf.game.PlayableEntity> sidekicks() { return List.of(p2, extra); }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), null, services);
        holder[0] = manager;
        manager.reset(0);
        FbzEndBossInstance boss = manager.createDynamicObject(() -> new FbzEndBossInstance(
                new ObjectSpawn(0x31C0, 0x690, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0)));
        setBoolean(boss, "attackLatch", true);
        assertDoesNotThrow(() -> setBoolean(boss, "suppressRootDrawThisFrame", true),
                "one-update defeat draw suppression must be a captured root scalar");
        S3kNativeP2LockInstance helper = manager.createDynamicObject(S3kNativeP2LockInstance::new);
        helper.update(0, p1);
        assertEquals(0, p2Cpu.getDiagnosticControlCounter(), "loc_863C0 clears Tails_CPU_idle_timer");
        assertEquals(321, extraCpu.getDiagnosticControlCounter(),
                "engine-only sidekicks are safety locked but do not own native P2 authority");
        setInt(p2Cpu, "diagnosticCtrl2HeldLatch",
                com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT);
        setInt(p2Cpu, "diagnosticCtrl2PressedLatch",
                com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT);
        helper.update(1, p1);
        assertEquals(0, p2Cpu.getDiagnosticGeneratedHeldInput()
                        & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT,
                "loc_863D6 clears Ctrl_2_logical on every locked execution");
        int helperSlot = helper.getSlotIndex();

        RewindRegistry rewind = new RewindRegistry();
        rewind.register(manager.rewindSnapshottable());
        CompositeSnapshot snapshot = rewind.capture();
        setBoolean(boss, "attackLatch", false);
        manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
        rewind.restore(snapshot);

        FbzEndBossInstance restoredBoss = manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        assertTrue(getBoolean(restoredBoss, "attackLatch"));
        assertTrue(getBoolean(restoredBoss, "suppressRootDrawThisFrame"));
        S3kNativeP2LockInstance restoredHelper = manager.activeObjectsOfType(S3kNativeP2LockInstance.class).getFirst();
        assertEquals(helperSlot, restoredHelper.getSlotIndex());
        assertTrue(p2.isControlLocked(), "the native P2 helper's live safety lock remains coherent");
    }

    @Test
    void twoOverlappingDisplacedFamiliesRestoreExactObjectIdsSlotsAndNeverCrossLink() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), null, services);
        holder[0] = manager;
        manager.reset(0);
        FbzEndBossInstance first = spawnBoss(manager, 0x31C0, 0x690);
        FbzEndBossInstance second = spawnBoss(manager, 0x31C0, 0x690);
        completeGraph(first);
        completeGraph(second);
        addFlamesAndDefeatChildren(manager, first);
        addFlamesAndDefeatChildren(manager, second);
        setBossPosition(first, 0x7000, 0x200);
        setBossPosition(second, 0x100, 0x900);

        Map<ObjectRefId, Integer> idsAndSlots = idSlots(manager);
        assertEquals(54, idsAndSlots.size());
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(manager.rewindSnapshottable());
        CompositeSnapshot snapshot = rewind.capture();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
        rewind.restore(snapshot);

        assertEquals(idsAndSlots, idSlots(manager), "every global SST slot and ObjectRefId must be stable");
        List<FbzEndBossInstance> roots = manager.activeObjectsOfType(FbzEndBossInstance.class);
        assertEquals(2, roots.size());
        for (FbzEndBossInstance root : roots) {
            List<FbzEndBossGraphMember> family = manager.getActiveObjects().stream()
                    .filter(FbzEndBossGraphMember.class::isInstance)
                    .map(FbzEndBossGraphMember.class::cast)
                    .filter(member -> member.boss() == root)
                    .toList();
            assertEquals(27, family.size(), "all live combat, weapon-flame, and defeat roles stay in one family");
            assertTrue(family.stream().allMatch(member -> member == root || member.boss() == root));
        }

        CompositeSnapshot recaptured = rewind.capture();
        assertTrue(RewindSnapshotDiff.diffKey("object-manager", snapshot.get("object-manager"),
                recaptured.get("object-manager")).isEmpty(), "capture -> restore -> capture must be identical");
    }

    @Test
    void restoredGraphForwardReplayMatchesUninterruptedReplay() throws Exception {
        ObjectManager manager = managerWithSelfServices();
        FbzEndBossInstance boss = spawnBoss(manager, 0x31C0, 0x690);
        completeGraph(boss);
        addFlamesAndDefeatChildren(manager, boss);
        setBoolean(boss, "nativeStarted", true);
        setValue(boss, "phaseOrdinal", FbzEndBossInstance.Phase.DESCEND.ordinal());
        FbzEndBossShipExplosionController controller = manager
                .activeObjectsOfType(FbzEndBossShipExplosionController.class).getFirst();
        setInt(controller, "waitCounter", 100);

        CompositeSnapshot checkpoint = capture(manager);
        manager.update(0, null, List.of(), 400);
        CompositeSnapshot uninterrupted = capture(manager);
        restore(manager, checkpoint);
        manager.update(0, null, List.of(), 400);
        CompositeSnapshot replayed = capture(manager);

        assertTrue(RewindSnapshotDiff.diffKey("object-manager", uninterrupted.get("object-manager"),
                replayed.get("object-manager")).isEmpty(),
                "one-frame forward replay after restore must reproduce graph state exactly");
    }

    @TestFactory
    Collection<DynamicTest> everyNativeConstructionPrefixAndFlamePrefixRestoresWithoutHealing() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int prefix = 0; prefix <= 15; prefix++) {
            int surviving = prefix;
            tests.add(DynamicTest.dynamicTest("native graph prefix " + prefix,
                    () -> assertGraphPrefixRoundTrip(surviving)));
        }
        for (int prefix = 0; prefix <= FbzEndBossFlameChild.nativeVolleyCount(); prefix++) {
            int surviving = prefix;
            tests.add(DynamicTest.dynamicTest("weapon flame prefix " + prefix,
                    () -> assertFlamePrefixRoundTrip(surviving)));
        }
        return tests;
    }

    private static void assertGraphPrefixRoundTrip(int prefix) {
        ObjectManager manager = managerWithSelfServices();
        FbzEndBossInstance boss = spawnBoss(manager, 0x31C0, 0x690);
        manager.reserveAllButNFreeSlots(prefix);
        completeGraph(boss);
        List<String> before = familyRoles(manager, boss);
        assertEquals(1 + prefix, before.size());
        CompositeSnapshot snapshot = capture(manager);
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
        restore(manager, snapshot);
        FbzEndBossInstance restored = manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        assertEquals(before, familyRoles(manager, restored));
        releaseUnusedSlots(manager);
        completeGraph(restored);
        assertEquals(before, familyRoles(manager, restored), "failed native allocation prefixes must not heal");
    }

    private static void assertFlamePrefixRoundTrip(int prefix) throws Exception {
        ObjectManager manager = managerWithSelfServices();
        FbzEndBossInstance boss = spawnBoss(manager, 0x31C0, 0x690);
        completeGraph(boss);
        manager.reserveAllButNFreeSlots(prefix);
        setBoolean(boss.weapon(), "initialized", true);
        setBoolean(boss, "weaponTrigger", true);
        boss.weapon().update(0, null);
        assertEquals(prefix, manager.activeObjectsOfType(FbzEndBossFlameChild.class).size());
        CompositeSnapshot snapshot = capture(manager);
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
        restore(manager, snapshot);
        FbzEndBossInstance restored = manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        List<String> before = familyRoles(manager, restored);
        releaseUnusedSlots(manager);
        restored.weapon().update(1, null);
        assertEquals(prefix, manager.activeObjectsOfType(FbzEndBossFlameChild.class).size(),
                "a captured partial flame volley must never be healed");
        assertEquals(before, familyRoles(manager, restored));
    }

    private static ObjectManager managerWithSelfServices() {
        GraphicsManager.getInstance().initHeadless();
        ObjectManager[] holder = new ObjectManager[1];
        com.openggf.camera.Camera camera = new com.openggf.camera.Camera();
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public com.openggf.camera.Camera camera() { return camera; }
        };
        holder[0] = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0].reset(0);
        return holder[0];
    }

    private static FbzEndBossInstance spawnBoss(ObjectManager manager, int x, int y) {
        return manager.createDynamicObject(() -> new FbzEndBossInstance(new ObjectSpawn(
                x, y, Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0)));
    }

    private static void completeGraph(FbzEndBossInstance boss) {
        boss.spawnNativeGraph();
        if (boss.ship() != null) boss.ship().update(0, null);
        boss.arms().forEach(arm -> arm.update(0, null));
        for (int i = 0; i < 2; i++) if (boss.joint(i) != null) boss.joint(i).update(0, null);
    }

    private static void addFlamesAndDefeatChildren(ObjectManager manager, FbzEndBossInstance boss) throws Exception {
        setBoolean(boss.weapon(), "initialized", true);
        setBoolean(boss, "weaponTrigger", true);
        boss.weapon().update(0, null);
        FbzEndBossShipExplosionController controller = manager.createDynamicObject(
                () -> new FbzEndBossShipExplosionController(boss, boss.ship()));
        FbzEndBossShipFlameChild flame = manager.createDynamicObject(
                () -> new FbzEndBossShipFlameChild(boss, boss.ship()));
        boss.attach(controller);
        boss.attach(flame);
    }

    private static void setBossPosition(FbzEndBossInstance boss, int x, int y) throws Exception {
        Field field = com.openggf.level.objects.boss.AbstractBossInstance.class.getDeclaredField("state");
        field.setAccessible(true);
        var state = (com.openggf.level.objects.boss.BossStateContext) field.get(boss);
        state.x = x;
        state.y = y;
    }

    private static Map<ObjectRefId, Integer> idSlots(ObjectManager manager) {
        var ids = manager.captureIdentityContext().requireIdentityTable();
        return manager.getActiveObjects().stream()
                .filter(FbzEndBossGraphMember.class::isInstance)
                .collect(Collectors.toMap(ids::idFor,
                        object -> ((AbstractObjectInstance) object).getSlotIndex()));
    }

    private static List<String> familyRoles(ObjectManager manager, FbzEndBossInstance boss) {
        return manager.getActiveObjects().stream()
                .filter(FbzEndBossGraphMember.class::isInstance)
                .map(FbzEndBossGraphMember.class::cast)
                .filter(member -> member.boss() == boss)
                .sorted(java.util.Comparator.comparingInt(member -> ((AbstractObjectInstance) member).getSlotIndex()))
                .map(FbzEndBossGraphMember::rewindRole)
                .toList();
    }

    private static CompositeSnapshot capture(ObjectManager manager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        return registry.capture();
    }

    private static void restore(ObjectManager manager, CompositeSnapshot snapshot) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        registry.restore(snapshot);
    }

    private static void releaseUnusedSlots(ObjectManager manager) {
        java.util.Set<Integer> occupied = manager.getActiveObjects().stream()
                .map(object -> ((AbstractObjectInstance) object).getSlotIndex())
                .collect(Collectors.toSet());
        for (int slot = 4; slot < 94; slot++) if (!occupied.contains(slot)) manager.releaseDynamicSlot(slot);
    }

    private static Map<String, Integer> roleSlots(ObjectManager manager) {
        return manager.getActiveObjects().stream()
                .filter(o -> o instanceof FbzEndBossGraphMember)
                .collect(Collectors.toMap(
                        o -> ((FbzEndBossGraphMember) o).rewindRole(),
                        o -> ((AbstractObjectInstance) o).getSlotIndex(),
                        (a, b) -> { throw new AssertionError("duplicate role"); },
                        java.util.TreeMap::new));
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void setInt(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setValue(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
