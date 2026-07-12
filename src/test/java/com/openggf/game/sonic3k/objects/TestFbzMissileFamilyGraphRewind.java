package com.openggf.game.sonic3k.objects;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import java.util.*;
import org.junit.jupiter.api.*;

class TestFbzMissileFamilyGraphRewind {
  @BeforeEach
  void init() {
    GraphicsManager.getInstance().initHeadless();
    AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x1000, 0);
  }
  @AfterEach
  void reset() {
    AbstractObjectInstance.resetCameraBoundsForTests();
    GraphicsManager.getInstance().resetState();
  }
  @TestFactory
  Collection<DynamicTest>
  launcherFamilyRestoresExactRolesConfigurationLinksAndSlotsInBothRestoreModes() {
    return List.of(false, true)
        .stream()
        .map(inPlace
             -> DynamicTest.dynamicTest(
                 inPlace ? "in-place restore" : "forced reconstruction",
                 () -> assertLauncherFamilyRestore(inPlace)))
        .toList();
  }
  private void assertLauncherFamilyRestore(boolean inPlace) {
    ObjectManager[] holder = new ObjectManager[1];
    Camera camera = new Camera() {
      public short getX() { return 0x0E00; }
      public short getY() { return 0; }
      public short getWidth() { return 0x4000; }
      public short getHeight() { return 0x1000; }
      public boolean isVerticalWrapEnabled() { return false; }
    };
    ObjectServices services = new StubObjectServices() {
      public ObjectManager objectManager() { return holder[0]; }
      public Camera camera() { return camera; }
      public GraphicsManager graphicsManager() {
        return GraphicsManager.getInstance();
      }
    };
    ObjectManager manager =
        new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                          GraphicsManager.getInstance(), camera, services);
    holder[0] = manager;
    manager.reset(0);
    if (!inPlace)
      manager.setRewindInPlaceRestoreEnabledForTest(false);
    ObjectSpawn spawn = new ObjectSpawn(
        0x1000, 0x800, Sonic3kObjectIds.FBZ_MISSILE_LAUNCHER, 0xF2, 0, true, 3);
    FbzMissileLauncherObjectInstance parent = manager.createDynamicObject(
        () -> new FbzMissileLauncherObjectInstance(spawn));
    parent.update(0x90, null);
    assertFamily(manager, parent, 1, 1, 5, 0x100);
    int parentSlot = parent.getSlotIndex(),
        companionSlot =
            live(manager, FbzMissileLauncherCompanionObjectInstance.class)
                .getFirst()
                .getSlotIndex(),
        missileSlot =
            live(manager, FbzMissileLauncherProjectileObjectInstance.class)
                .getFirst()
                .getSlotIndex();
    RewindRegistry registry = new RewindRegistry();
    registry.register(manager.rewindSnapshottable());
    CompositeSnapshot snapshot = registry.capture();
    for (ObjectInstance object : new ArrayList<>(manager.getActiveObjects()))
      if (object != parent)
        manager.removeDynamicObject(object);
    parent.update(0x91, null);
    registry.restore(snapshot);
    FbzMissileLauncherObjectInstance restored =
        live(manager, FbzMissileLauncherObjectInstance.class).getFirst();
    assertFamily(manager, restored, 1, 1, 5, 0x100);
    assertEquals(parentSlot, restored.getSlotIndex());
    assertEquals(companionSlot,
                 live(manager, FbzMissileLauncherCompanionObjectInstance.class)
                     .getFirst()
                     .getSlotIndex());
    assertEquals(missileSlot,
                 live(manager, FbzMissileLauncherProjectileObjectInstance.class)
                     .getFirst()
                     .getSlotIndex());
    restored.update(0x91, null);
    assertEquals(3, liveAll(manager).size());
    FbzMissileLauncherProjectileObjectInstance missile =
        live(manager, FbzMissileLauncherProjectileObjectInstance.class)
            .getFirst();
    int slot = missile.getSlotIndex();
    missile.impact();
    ExplosionObjectInstance explosion =
        live(manager, ExplosionObjectInstance.class).getFirst();
    assertEquals(slot, explosion.getSlotIndex());
    assertTrue(missile.isDestroyed());
  }
  private static void assertFamily(ObjectManager manager,
                                   FbzMissileLauncherObjectInstance parent,
                                   int companions, int missiles, int impacts,
                                   int trajectory) {
    List<FbzMissileLauncherCompanionObjectInstance> cs =
        live(manager, FbzMissileLauncherCompanionObjectInstance.class);
    List<FbzMissileLauncherProjectileObjectInstance> ps =
        live(manager, FbzMissileLauncherProjectileObjectInstance.class);
    assertEquals(companions, cs.size());
    assertEquals(missiles, ps.size());
    assertEquals(impacts, parent.liveImpacts());
    assertEquals(trajectory, ps.getFirst().horizontalDelta());
    assertSame(parent, cs.getFirst().parentMember());
    assertSame(parent, ps.getFirst().parentMember());
    assertEquals(parent.getSlotIndex(), cs.getFirst().familySlot());
    assertEquals(parent.getSlotIndex(), ps.getFirst().familySlot());
  }
  private static List<ObjectInstance> liveAll(ObjectManager manager) {
    return manager.getActiveObjects()
        .stream()
        .filter(o -> !o.isDestroyed())
        .toList();
  }
  private static <T extends ObjectInstance> List<T> live(ObjectManager manager,
                                                         Class<T> type) {
    return manager.getActiveObjects()
        .stream()
        .filter(o -> type.isInstance(o) && !o.isDestroyed())
        .map(type::cast)
        .toList();
  }
}
