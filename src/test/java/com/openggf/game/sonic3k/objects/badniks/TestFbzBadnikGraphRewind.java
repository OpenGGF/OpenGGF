package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzBadnikGraphRewind {
    @BeforeEach void init() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x1000, 0);
    }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @TestFactory Collection<DynamicTest> bothFamiliesRestoreExactRolesLinksSlotsAndNoDuplicates() {
        List<DynamicTest> tests = new ArrayList<>();
        for (boolean inPlace : List.of(false, true)) {
            tests.add(DynamicTest.dynamicTest("Blaster " + mode(inPlace), () -> blaster(inPlace)));
            tests.add(DynamicTest.dynamicTest("TechnoSqueek " + mode(inPlace), () -> techno(inPlace)));
        }
        return tests;
    }

    private void blaster(boolean inPlace) {
        Harness h = new Harness(inPlace);
        BlasterBadnikInstance parent = h.manager.createDynamicObject(
                () -> new BlasterBadnikInstance(spawn(0xA8, 0x20)));
        for (int frame = 0; frame <= 19; frame++) parent.update(frame, h.main);
        var effect = live(h.manager, BlasterAttackEffectObjectInstance.class).getFirst();
        var projectile = live(h.manager, BlasterProjectileObjectInstance.class).getFirst();
        effect.update(19, null);
        projectile.update(19, null);
        parent.update(20, h.main);
        parent.update(21, h.main);
        for (int frame = 22; frame <= 28; frame++) parent.update(frame, h.main);
        List<BlasterProjectileObjectInstance> beforeProjectiles = live(h.manager, BlasterProjectileObjectInstance.class);
        assertEquals(2, beforeProjectiles.size());
        BlasterProjectileObjectInstance secondary = beforeProjectiles.stream()
                .filter(p -> !p.primaryKind()).findFirst().orElseThrow();
        int parentSlot = parent.getSlotIndex(), effectSlot = effect.getSlotIndex();
        int projectileSlot = projectile.getSlotIndex(), secondarySlot = secondary.getSlotIndex();
        CompositeSnapshot snapshot = snapshot(h.manager);
        h.manager.removeDynamicObject(effect);
        h.manager.removeDynamicObject(projectile);
        restore(h.manager, snapshot);
        BlasterBadnikInstance restored = live(h.manager, BlasterBadnikInstance.class).getFirst();
        effect = live(h.manager, BlasterAttackEffectObjectInstance.class).getFirst();
        List<BlasterProjectileObjectInstance> restoredProjectiles = live(h.manager, BlasterProjectileObjectInstance.class);
        assertFalse(restoredProjectiles.isEmpty(), () -> "active after restore: "
                + h.manager.getActiveObjects().stream().map(o -> o.getClass().getSimpleName()
                + "[destroyed=" + o.isDestroyed() + ",slot="
                + (o instanceof AbstractObjectInstance a ? a.getSlotIndex() : -1) + "]").toList());
        assertEquals(2, restoredProjectiles.size());
        projectile = restoredProjectiles.stream().filter(BlasterProjectileObjectInstance::primaryKind)
                .findFirst().orElseThrow();
        secondary = restoredProjectiles.stream().filter(p -> !p.primaryKind()).findFirst().orElseThrow();
        assertEquals(parentSlot, restored.getSlotIndex());
        assertEquals(effectSlot, effect.getSlotIndex());
        assertEquals(projectileSlot, projectile.getSlotIndex());
        assertEquals(secondarySlot, secondary.getSlotIndex());
        assertSame(restored, effect.parentMember());
        assertSame(restored, projectile.parentMember());
        assertEquals(restored.getSlotIndex(), effect.familySlot());
        assertEquals(restored.getSlotIndex(), projectile.familySlot());
        restored.update(20, h.main);
        assertEquals(1, live(h.manager, BlasterAttackEffectObjectInstance.class).size());
        assertEquals(2, live(h.manager, BlasterProjectileObjectInstance.class).size());
        restored.setDestroyed(true);
        effect.update(21, null);
        projectile.update(21, null);
        secondary.update(21, null);
        assertFalse(effect.isDestroyed(), "89726 terminates only through its own F4 callback");
        assertFalse(projectile.isDestroyed(), "Blaster projectiles are independent siblings");
        assertFalse(secondary.isDestroyed(), "89746 is independent of both parent and primary");
    }

    @Test void exhaustedAfterCurrentSlotsAreOneShotForBothFamilies() {
        Harness blasterHarness = new Harness(true);
        BlasterBadnikInstance blaster = new BlasterBadnikInstance(spawn(0xA8, 0x20));
        blasterHarness.manager.addDynamicObjectAtSlot(blaster, 127);
        for (int frame = 0; frame <= 30; frame++) {
            blasterHarness.manager.update(0, blasterHarness.main, List.of(), frame);
        }
        assertTrue(blaster.childrenAttempted());
        assertTrue(live(blasterHarness.manager, BlasterAttackEffectObjectInstance.class).isEmpty());
        assertTrue(live(blasterHarness.manager, BlasterProjectileObjectInstance.class).isEmpty());

        Harness technoHarness = new Harness(true);
        TechnoSqueekBadnikInstance techno = new TechnoSqueekBadnikInstance(spawn(0xA9, 0));
        technoHarness.manager.addDynamicObjectAtSlot(techno, 127);
        for (int frame = 0; frame <= 5; frame++) {
            technoHarness.manager.update(0, technoHarness.main, List.of(), frame);
        }
        assertNotNull(techno.attachment(), "factory construction still occurs on failed allocation");
        assertTrue(techno.attachment().isDestroyed());
        assertTrue(live(technoHarness.manager, TechnoSqueekAttachmentObjectInstance.class).isEmpty());
    }

    private void techno(boolean inPlace) {
        Harness h = new Harness(inPlace);
        TechnoSqueekBadnikInstance parent = h.manager.createDynamicObject(
                () -> new TechnoSqueekBadnikInstance(spawn(0xA9, 0)));
        parent.update(0, h.main);
        parent.update(1, h.main);
        TechnoSqueekAttachmentObjectInstance child = live(h.manager, TechnoSqueekAttachmentObjectInstance.class).getFirst();
        child.update(1, null);
        int parentSlot = parent.getSlotIndex(), childSlot = child.getSlotIndex();
        CompositeSnapshot snapshot = snapshot(h.manager);
        h.manager.removeDynamicObject(child);
        restore(h.manager, snapshot);
        TechnoSqueekBadnikInstance restored = live(h.manager, TechnoSqueekBadnikInstance.class).getFirst();
        child = live(h.manager, TechnoSqueekAttachmentObjectInstance.class).getFirst();
        assertEquals(parentSlot, restored.getSlotIndex());
        assertEquals(childSlot, child.getSlotIndex());
        assertSame(restored, child.parentMember());
        assertEquals(restored.getSlotIndex(), child.familySlot());
        assertSame(child, restored.attachment(),
                "the restored parent must recover its exact owned 89B24 slot");
        restored.update(2, h.main);
        assertEquals(1, live(h.manager, TechnoSqueekAttachmentObjectInstance.class).size());
        restored.setDestroyed(true);
        child.update(3, null);
        assertTrue(child.isDestroyed(), "89B24 is parent-owned and status-checks its owner");
    }

    private static CompositeSnapshot snapshot(ObjectManager manager) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        return registry.capture();
    }

    private static void restore(ObjectManager manager, CompositeSnapshot snapshot) {
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        registry.restore(snapshot);
    }

    private static String mode(boolean inPlace) { return inPlace ? "in-place" : "forced reconstruction"; }

    private static <T extends ObjectInstance> List<T> live(ObjectManager manager, Class<T> type) {
        return manager.getActiveObjects().stream().filter(o -> type.isInstance(o) && !o.isDestroyed())
                .map(type::cast).toList();
    }

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 3);
    }

    private static final class Harness {
        final PlayableEntity main = player();
        final ObjectManager manager;
        Harness(boolean inPlace) {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = new Camera() {
                @Override public short getX() { return 0x0F00; }
                @Override public short getY() { return 0x0700; }
                @Override public short getWidth() { return 0x4000; }
                @Override public short getHeight() { return 0x1000; }
                @Override public boolean isVerticalWrapEnabled() { return false; }
            };
            ObjectServices services = new StubObjectServices() {
                private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, List::of);
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public ObjectPlayerQuery playerQuery() { return query; }
            };
            manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.reset(0);
            if (!inPlace) manager.setRewindInPlaceRestoreEnabledForTest(false);
        }
        private static PlayableEntity player() {
            PlayableEntity p = mock(PlayableEntity.class);
            when(p.getCentreX()).thenReturn((short) 0x1040);
            when(p.getCentreY()).thenReturn((short) 0x800);
            return p;
        }
    }
}
