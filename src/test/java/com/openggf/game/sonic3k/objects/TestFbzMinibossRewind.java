package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestFbzMinibossRewind {
    private static final Class<?>[] INITIAL_PREFIX = {
            FbzMinibossCoverChild.class,
            FbzMinibossCoverChild.class,
            FbzMinibossCoverChild.class,
            FbzMinibossPlungerChild.class,
            FbzMinibossAimerChild.class,
            FbzMinibossArmChild.class,
            FbzMinibossArmChild.class
    };
    @BeforeEach void headless() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() { GraphicsManager.getInstance().resetState(); }
    @Test
    void stableRolesRelinkBothCyclesIndependentOfRecreationOrder() {
        FbzMinibossInstance boss = new FbzMinibossInstance(new ObjectSpawn(0x2F00, 0x5E0, 0xAA, 0, 0, true, 3));
        FbzMinibossArmChild left = FbzMinibossArmChild.forTest(boss, 0);
        FbzMinibossArmChild right = FbzMinibossArmChild.forTest(boss, 1);
        FbzMinibossChainLink[] leftLinks = left.createLinksForTest();
        FbzMinibossChainLink[] rightLinks = right.createLinksForTest();

        FbzMinibossRewindLinks.settleForTest(boss, new Object[] {
                rightLinks[4], leftLinks[2], right, leftLinks[0], rightLinks[1], left,
                rightLinks[3], leftLinks[4], rightLinks[0], leftLinks[1], leftLinks[3], rightLinks[2]
        });
        assertSame(left, leftLinks[4].next());
        assertSame(right, rightLinks[4].next());
        assertSame(leftLinks[0], left.next());
        assertSame(rightLinks[0], right.next());
        assertNotSame(leftLinks[0], rightLinks[0]);
    }

    @Test
    void partialPrefixNeverHealsIntoACycle() {
        FbzMinibossInstance boss = new FbzMinibossInstance(new ObjectSpawn(0x2F00, 0x5E0, 0xAA, 0, 0, true, 3));
        FbzMinibossArmChild arm = FbzMinibossArmChild.forTest(boss, 0);
        FbzMinibossChainLink[] links = arm.createLinksForTest();
        FbzMinibossRewindLinks.settleForTest(boss, new Object[] {arm, links[0], links[1]});
        assertSame(links[0], arm.next());
        assertSame(links[1], links[0].next());
        assertNull(links[1].next(), "a failed prefix must not be healed into a cycle");
    }

    @Test
    void everySimpleChildRecreatesAsAParentFreePhaseOneShell() {
        FbzMinibossInstance boss = new FbzMinibossInstance(
                new ObjectSpawn(0x2F00, 0x5E0, 0xAA, 0, 0, true, 3));
        List<AbstractObjectInstance> children = List.of(
                new FbzMinibossAimerChild(boss),
                new FbzMinibossCoverChild(boss, 1, 0x10, -8),
                new FbzMinibossPlungerChild(boss),
                new FbzMinibossPaletteChild(boss),
                new FbzMinibossExplosionController(boss),
                new FbzMinibossPrisonChild(boss),
                new FbzMinibossAnimalChild(boss, 2),
                new FbzMinibossFragmentChild(boss, 4));
        ObjectServices services = new StubObjectServices();

        for (AbstractObjectInstance child : children) {
            RewindRecreatable recreatable = assertInstanceOf(RewindRecreatable.class, child);
            RewindRecreateContext context = new RewindRecreateContext(
                    child.getSpawn(), null, services, null, null);
            assertNotNull(recreatable.recreateForRewind(context),
                    child.getClass().getSimpleName() + " must not require a live root during phase 1");
        }
    }

    @Test
    void forcedReconstructionRelinksAllSimpleChildrenAfterAdverseChildFirstOrder() {
        ManagerHarness h = managerHarness();
        FbzMinibossInstance boss = new FbzMinibossInstance(
                new ObjectSpawn(0x2F00, 0x5E0, 0xAA, 0, 0, true, 3));
        boss.setSlotIndex(92);
        List<AbstractObjectInstance> children = List.of(
                new FbzMinibossFragmentChild(boss, 4),
                new FbzMinibossAnimalChild(boss, 2),
                new FbzMinibossPrisonChild(boss),
                new FbzMinibossExplosionController(boss),
                new FbzMinibossPaletteChild(boss),
                new FbzMinibossPlungerChild(boss),
                new FbzMinibossCoverChild(boss, 1, 0x10, -8),
                new FbzMinibossAimerChild(boss));
        for (int index = 0; index < children.size(); index++) {
            h.manager.addDynamicObjectAtSlot(children.get(index), 4 + index);
        }
        h.manager.addDynamicObjectAtSlot(boss, 92);
        CompositeSnapshot snapshot = capture(h.manager);

        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        new ArrayList<>(h.manager.getActiveObjects()).forEach(h.manager::removeDynamicObject);
        restore(h.manager, snapshot);

        List<ObjectInstance> restored = live(h.manager);
        assertEquals(9, restored.size(), "phase-1 shells must not create duplicate family members");
        FbzMinibossInstance restoredBoss = restored.stream()
                .filter(FbzMinibossInstance.class::isInstance)
                .map(FbzMinibossInstance.class::cast).findFirst().orElseThrow();
        for (ObjectInstance object : restored) {
            if (object == restoredBoss) continue;
            assertSame(restoredBoss, objectField(object, "boss"),
                    object.getClass().getSimpleName() + " must relink to the exact restored root");
        }
        for (Class<?> type : List.of(FbzMinibossAimerChild.class, FbzMinibossCoverChild.class,
                FbzMinibossPlungerChild.class, FbzMinibossPaletteChild.class,
                FbzMinibossExplosionController.class, FbzMinibossPrisonChild.class,
                FbzMinibossAnimalChild.class, FbzMinibossFragmentChild.class)) {
            assertEquals(1, count(restored, type), "duplicate " + type.getSimpleName());
        }
    }

    @TestFactory
    Collection<DynamicTest> realObjectManagerGraphRestoresExactSlotsCyclesAndNoDuplicates() {
        return List.of(false, true).stream().map(inPlace -> DynamicTest.dynamicTest(
                inPlace ? "in-place" : "forced reconstruction", () -> assertRealRestore(inPlace))).toList();
    }

    @TestFactory
    Collection<DynamicTest> everyInitialAllocationFailureRestoresExactPrefixInBothModes() {
        List<DynamicTest> tests = new ArrayList<>();
        for (boolean inPlace : List.of(true, false)) {
            for (int surviving = 0; surviving <= INITIAL_PREFIX.length; surviving++) {
                int prefix = surviving;
                tests.add(DynamicTest.dynamicTest(
                        (inPlace ? "in-place" : "forced") + " initial prefix " + prefix,
                        () -> assertInitialPrefixRestore(prefix, inPlace)));
            }
        }
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> everyArmTableAllocationFailureRestoresExactPrefixInBothModes() {
        List<DynamicTest> tests = new ArrayList<>();
        for (boolean inPlace : List.of(true, false)) {
            for (int side = 0; side < 2; side++) {
                for (int surviving = 0; surviving <= FbzMinibossArmChild.LINK_COUNT; surviving++) {
                    int armSide = side;
                    int prefix = surviving;
                    tests.add(DynamicTest.dynamicTest(
                            (inPlace ? "in-place" : "forced") + " arm " + armSide + " prefix " + prefix,
                            () -> assertArmPrefixRestore(armSide, prefix, inPlace)));
                }
            }
        }
        return tests;
    }

    private void assertInitialPrefixRestore(int prefix, boolean inPlace) {
        ManagerHarness h = managerHarness();
        FbzMinibossInstance boss = h.manager.createDynamicObject(() -> new FbzMinibossInstance(
                new ObjectSpawn(0x2F00, 0x5E0, 0xAA, 0, 0, true, 3)));
        h.manager.reserveAllButNFreeSlots(prefix);
        h.step(0);

        List<ObjectInstance> captured = live(h.manager);
        assertInitialPrefix(captured, boss, prefix);
        int[] slots = slots(captured);
        CompositeSnapshot snapshot = capture(h.manager);

        if (inPlace) {
            captured.forEach(object -> ((AbstractObjectInstance) object).setDestroyed(true));
        } else {
            h.manager.setRewindInPlaceRestoreEnabledForTest(false);
            new ArrayList<>(captured).forEach(h.manager::removeDynamicObject);
        }
        restore(h.manager, snapshot);

        List<ObjectInstance> restored = live(h.manager);
        FbzMinibossInstance restoredBoss = restored.stream().filter(FbzMinibossInstance.class::isInstance)
                .map(FbzMinibossInstance.class::cast).findFirst().orElseThrow();
        assertInitialPrefix(restored, restoredBoss, prefix);
        assertArrayEquals(slots, slots(restored), "stable SST slots must survive prefix restore");

        releaseUnusedSlots(h.manager);
        h.step(100);
        assertInitialPrefix(live(h.manager), restoredBoss, prefix);
    }

    private void assertArmPrefixRestore(int side, int prefix, boolean inPlace) {
        ManagerHarness h = managerHarness();
        FbzMinibossInstance boss = new FbzMinibossInstance(
                new ObjectSpawn(0x2F00, 0x5E0, 0xAA, 0, 0, true, 3));
        h.manager.addDynamicObjectAtSlot(boss, 92);
        FbzMinibossArmChild arm = new FbzMinibossArmChild(boss, side);
        h.manager.addDynamicObjectAtSlot(arm, 4);
        boss.setRootBit(FbzMinibossInstance.ROOT_FIGHT_STARTED);

        h.manager.reserveAllButNFreeSlots(0);
        h.step(0); // boss consumes its own seven-entry table failure before capacity is opened
        releaseHighestUnusedSlots(h.manager, prefix);
        int lastFrame = side == 0 ? 3 : 66;
        for (int frame = 1; frame <= lastFrame; frame++) h.step(frame);

        assertArmPrefix(h.manager, boss, side, prefix);
        List<ObjectInstance> capturedFamily = family(h.manager, boss.getSlotIndex());
        int[] slots = slots(capturedFamily);
        CompositeSnapshot snapshot = capture(h.manager);

        if (inPlace) {
            arm.setNext(null);
            arm.setTerminal(null);
            for (FbzMinibossChainLink link : links(h.manager, boss.getSlotIndex(), side)) {
                link.setBoss(null);
                link.setArm(null);
                link.setPrevious(null);
                link.setNext(null);
            }
        } else {
            h.manager.setRewindInPlaceRestoreEnabledForTest(false);
            for (ObjectInstance object : new ArrayList<>(capturedFamily)) {
                if (!(object instanceof FbzMinibossInstance)) h.manager.removeDynamicObject(object);
            }
        }
        restore(h.manager, snapshot);

        FbzMinibossInstance restoredBoss = live(h.manager).stream()
                .filter(FbzMinibossInstance.class::isInstance)
                .map(FbzMinibossInstance.class::cast).findFirst().orElseThrow();
        assertArmPrefix(h.manager, restoredBoss, side, prefix);
        assertArrayEquals(slots, slots(family(h.manager, restoredBoss.getSlotIndex())),
                "partial arm family must return to its exact SST slots");

        releaseUnusedSlots(h.manager);
        for (int frame = 100; frame < 110; frame++) h.step(frame);
        assertArmPrefix(h.manager, restoredBoss, side, prefix);
    }

    private static void assertInitialPrefix(List<ObjectInstance> objects, FbzMinibossInstance boss, int prefix) {
        List<ObjectInstance> family = objects.stream()
                .filter(object -> object instanceof FbzMinibossInstance
                        || object instanceof FbzMinibossCoverChild
                        || object instanceof FbzMinibossPlungerChild
                        || object instanceof FbzMinibossAimerChild
                        || object instanceof FbzMinibossArmChild)
                .sorted(java.util.Comparator.comparingInt(object -> ((AbstractObjectInstance) object).getSlotIndex()))
                .toList();
        assertEquals(1 + prefix, family.size(), "failed AllocateObject must preserve only its prefix");
        assertSame(boss, family.getFirst());
        for (int index = 0; index < prefix; index++) {
            ObjectInstance child = family.get(index + 1);
            assertInstanceOf(INITIAL_PREFIX[index], child, "wrong role at initial table ordinal " + index);
            if (index >= 5) assertEquals(index - 5, ((FbzMinibossArmChild) child).side());
        }
    }

    private static void assertArmPrefix(ObjectManager manager, FbzMinibossInstance boss,
                                        int side, int prefix) {
        FbzMinibossArmChild arm = family(manager, boss.getSlotIndex()).stream()
                .filter(FbzMinibossArmChild.class::isInstance)
                .map(FbzMinibossArmChild.class::cast)
                .filter(candidate -> candidate.side() == side)
                .findFirst().orElseThrow();
        List<FbzMinibossChainLink> links = links(manager, boss.getSlotIndex(), side);
        assertEquals(prefix, links.size(), "allocation failure must stop at the first missing ordinal");
        assertSame(prefix == 0 ? null : links.getFirst(), arm.next());
        assertSame(prefix == 0 ? null : links.getLast(), arm.terminal());
        Object previous = arm;
        for (int index = 0; index < prefix; index++) {
            FbzMinibossChainLink link = links.get(index);
            assertEquals(index, link.linkIndex());
            assertSame(boss, link.boss());
            assertSame(arm, link.arm());
            assertSame(previous, link.previous());
            if (index + 1 < prefix) assertSame(links.get(index + 1), link.next());
            else assertSame(prefix == FbzMinibossArmChild.LINK_COUNT ? arm : null, link.next(),
                    "only a real terminal ordinal may close the cycle");
            previous = link;
        }
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

    private static List<ObjectInstance> family(ObjectManager manager, int familySlot) {
        return live(manager).stream().filter(object -> object instanceof FbzMinibossInstance boss
                        ? boss.getSlotIndex() == familySlot
                        : object instanceof FbzMinibossArmChild arm
                        ? arm.familySlot() == familySlot
                        : object instanceof FbzMinibossChainLink link && link.familySlot() == familySlot)
                .toList();
    }

    private static List<FbzMinibossChainLink> links(ObjectManager manager, int familySlot, int side) {
        return live(manager).stream().filter(FbzMinibossChainLink.class::isInstance)
                .map(FbzMinibossChainLink.class::cast)
                .filter(link -> link.familySlot() == familySlot && link.side() == side)
                .sorted(java.util.Comparator.comparingInt(FbzMinibossChainLink::linkIndex))
                .toList();
    }

    private static int[] slots(List<ObjectInstance> objects) {
        return objects.stream().mapToInt(object -> ((AbstractObjectInstance) object).getSlotIndex())
                .sorted().toArray();
    }

    private static void releaseHighestUnusedSlots(ObjectManager manager, int count) {
        java.util.Set<Integer> occupied = live(manager).stream()
                .map(object -> ((AbstractObjectInstance) object).getSlotIndex())
                .collect(java.util.stream.Collectors.toSet());
        int released = 0;
        for (int slot = 92; slot >= 4 && released < count; slot--) {
            if (!occupied.contains(slot)) {
                manager.releaseDynamicSlot(slot);
                released++;
            }
        }
        assertEquals(count, released);
    }

    private static void releaseUnusedSlots(ObjectManager manager) {
        java.util.Set<Integer> occupied = live(manager).stream()
                .map(object -> ((AbstractObjectInstance) object).getSlotIndex())
                .collect(java.util.stream.Collectors.toSet());
        for (int slot = 4; slot <= 92; slot++) {
            if (!occupied.contains(slot)) manager.releaseDynamicSlot(slot);
        }
    }

    private ManagerHarness managerHarness() {
        com.openggf.game.PlayableEntity p1 = mock(com.openggf.game.PlayableEntity.class);
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0x2E20; }
            @Override public short getY() { return 0x540; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> p1, List::of); }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        return new ManagerHarness(manager, p1);
    }

    private record ManagerHarness(ObjectManager manager, com.openggf.game.PlayableEntity player) {
        void step(int frame) { manager.update(0x2E20, player, List.of(), frame, false); }
    }

    private void assertRealRestore(boolean inPlace) {
        com.openggf.game.PlayableEntity p1 = mock(com.openggf.game.PlayableEntity.class);
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0x2E20; }
            @Override public short getY() { return 0x540; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> p1, List::of); }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        if (!inPlace) manager.setRewindInPlaceRestoreEnabledForTest(false);
        FbzMinibossInstance boss = manager.createDynamicObject(() -> new FbzMinibossInstance(
                new ObjectSpawn(0x2F00, 0x5E0, 0xAA, 0, 0, true, 3)));
        manager.update(0x2E20, p1, List.of(), 0, false);
        assertEquals(2, count(live(manager), FbzMinibossArmChild.class),
                "both arm entries from ChildObjDat_6FA76 must survive their setup update");
        FbzMinibossPlungerChild plunger = manager.getActiveObjects().stream()
                .filter(FbzMinibossPlungerChild.class::isInstance)
                .map(FbzMinibossPlungerChild.class::cast).findFirst().orElseThrow();
        plunger.onStandingContact(p1, true);
        for (int frame = 1; frame < 170; frame++) {
            plunger.onStandingContact(p1, true);
            manager.update(0x2E20, p1, List.of(), frame, false);
            assertEquals(2, count(live(manager), FbzMinibossArmChild.class),
                    "arm family disappeared at frame " + frame);
        }

        assertExactGraph(manager);
        int[] slots = live(manager).stream().mapToInt(o -> ((AbstractObjectInstance) o).getSlotIndex()).sorted().toArray();
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        CompositeSnapshot snapshot = registry.capture();
        for (ObjectInstance object : new ArrayList<>(manager.getActiveObjects())) {
            if (object instanceof FbzMinibossArmChild || object instanceof FbzMinibossChainLink) {
                manager.removeDynamicObject(object);
            }
        }
        registry.restore(snapshot);
        assertExactGraph(manager);
        assertArrayEquals(slots, live(manager).stream().mapToInt(o -> ((AbstractObjectInstance) o).getSlotIndex()).sorted().toArray());
    }

    private static void assertExactGraph(ObjectManager manager) {
        List<ObjectInstance> live = live(manager);
        assertEquals(18, live.size(), () -> "live graph: " + live.stream()
                .map(o -> o.getClass().getSimpleName() + "@" + ((AbstractObjectInstance) o).getSlotIndex())
                .toList());
        assertEquals(1, count(live, FbzMinibossInstance.class));
        assertEquals(3, count(live, FbzMinibossCoverChild.class));
        assertEquals(1, count(live, FbzMinibossPlungerChild.class));
        assertEquals(1, count(live, FbzMinibossAimerChild.class));
        assertEquals(2, count(live, FbzMinibossArmChild.class));
        assertEquals(10, count(live, FbzMinibossChainLink.class));
        for (int side = 0; side < 2; side++) {
            final int s = side;
            FbzMinibossArmChild arm = live.stream().filter(FbzMinibossArmChild.class::isInstance)
                    .map(FbzMinibossArmChild.class::cast).filter(a -> a.side() == s).findFirst().orElseThrow();
            Object cursor = arm;
            for (int index = 0; index < 5; index++) {
                cursor = cursor instanceof FbzMinibossArmChild a ? a.next() : ((FbzMinibossChainLink) cursor).next();
                FbzMinibossChainLink link = assertInstanceOf(FbzMinibossChainLink.class, cursor);
                assertEquals(index, link.linkIndex());
                assertEquals(side, link.side());
            }
            assertSame(arm, ((FbzMinibossChainLink) cursor).next());
        }
    }

    private static List<ObjectInstance> live(ObjectManager manager) {
        return manager.getActiveObjects().stream().filter(o -> !o.isDestroyed()).toList();
    }
    private static long count(List<ObjectInstance> objects, Class<?> type) {
        return objects.stream().filter(type::isInstance).count();
    }

    private static Object objectField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("missing structural field " + fieldName, e);
        }
    }
}
