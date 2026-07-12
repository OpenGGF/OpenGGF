package com.openggf.game.sonic3k.objects;
import com.openggf.level.objects.*;
import com.openggf.physics.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
class TestFbzMissileObjects {
  @BeforeEach
  void bounds() {
    AbstractObjectInstance.updateCameraBounds(0xF00, 0x700, 0x1300, 0x900, 0);
  }
  @AfterEach
  void reset() {
    AbstractObjectInstance.resetCameraBoundsForTests();
  }
  @Test
  void everyLauncherSubtypeDecodesExactCadenceBurstPhaseAndCompanion() {
    for (int s : new int[] {2, 0x72, 0xF2}) {
      var o = new FbzMissileLauncherObjectInstance(spawn(0x7F, s));
      assertEquals((((s & 0xC) + 4) << 2), o.launchInterval());
      assertEquals((s & 3) + 1, o.burstCount());
      assertEquals(s & 0x70, o.phaseOffset());
      assertEquals((s & 0x80) != 0, o.hasCompanion());
      assertEquals(1, o.getPriorityBucket());
    }
  }
  @Test
  void wallMissileSubtypesUseSubtypeTimesFourCadence() {
    for (int s : new int[] {0x10, 0x20}) {
      var o = new FbzWallMissileObjectInstance(spawn(0xE0, s));
      assertEquals(s << 2, o.launchInterval());
      assertEquals(5, o.getPriorityBucket());
    }
  }
  @Test
  void launcherTrajectoryTableAndExplosionOffsetsAreExact() {
    assertArrayEquals(
        new int[] {0x100, 0xE0, 0x120, 0x140, 0x100, 0xE0, 0xC0, 0xE0},
        FbzMissileLauncherObjectInstance.horizontalTrajectoryTable());
    assertArrayEquals(
        new int[] {-0x18, 2, -8, -4, 8, 4, 0x18, -2},
        FbzMissileLauncherCompanionObjectInstance.explosionOffsets());
  }
  @Test
  void
  launcherAttemptsThreeShotsAtSeventeenTickEdgesAndFailureDoesNotConsumeTrajectoryOrSfx() {
    ObjectManager manager = mock(ObjectManager.class);
    int[] calls = {0};
    doAnswer(inv -> {
      if (calls[0]++ == 0)
        ((AbstractObjectInstance)inv.getArgument(0)).setDestroyed(true);
      return null;
    })
        .when(manager)
        .addDynamicObjectAfterCurrent(any());
    RecordingServices services = new RecordingServices(manager);
    var launcher = new FbzMissileLauncherObjectInstance(spawn(0x7F, 2));
    launcher.setServices(services);
    for (int f = 0; f <= 34; f++)
      launcher.update(f, null);
    ArgumentCaptor<AbstractObjectInstance> cap =
        ArgumentCaptor.forClass(AbstractObjectInstance.class);
    verify(manager, times(3)).addDynamicObjectAfterCurrent(cap.capture());
    assertEquals(List.of(0x100, 0x100, 0xE0),
                 cap.getAllValues()
                     .stream()
                     .map(v
                          -> ((FbzMissileLauncherProjectileObjectInstance)v)
                                 .horizontalDelta())
                     .toList());
    assertEquals(2, services.projectileSfx);
  }
  @Test
  void
  wallLauncherFiresAtSixtyFifthEligibleTickThenAfterExactEightyUpdateMuzzleLockout() {
    ObjectManager manager = mock(ObjectManager.class);
    RecordingServices services = new RecordingServices(manager);
    var wall = new FbzWallMissileObjectInstance(spawn(0xE0, 0x10));
    wall.setServices(services);
    for (int f = 0; f < 64; f++)
      wall.update(f, null);
    verify(manager, never()).addDynamicObjectAfterCurrent(any());
    wall.update(64, null);
    verify(manager, times(1)).addDynamicObjectAfterCurrent(any());
    for (int f = 65; f < 208; f++)
      wall.update(f, null);
    verify(manager, times(1)).addDynamicObjectAfterCurrent(any());
    wall.update(208, null);
    verify(manager, times(2)).addDynamicObjectAfterCurrent(any());
  }
  @Test
  void
  companionAllocationFailureKeepsFiveImpactsAndExplosionFailureRetriesSameOffset() {
    ObjectManager manager = mock(ObjectManager.class);
    doAnswer(inv -> {
      ((AbstractObjectInstance)inv.getArgument(0)).setDestroyed(true);
      return null;
    })
        .when(manager)
        .addDynamicObjectAfterCurrent(any());
    var parent = new FbzMissileLauncherObjectInstance(spawn(0x7F, 0xF2));
    parent.setServices(new RecordingServices(manager));
    parent.update(0x90, null);
    assertEquals(5, parent.liveImpacts());
    org.mockito.Mockito.reset(manager);
    List<AbstractObjectInstance> made = new ArrayList<>();
    doAnswer(inv -> {
      AbstractObjectInstance o = inv.getArgument(0);
      made.add(o);
      if (made.size() == 1)
        o.setDestroyed(true);
      return null;
    })
        .when(manager)
        .addDynamicObjectAfterCurrent(any());
    var companion = new FbzMissileLauncherCompanionObjectInstance(
        new ObjectSpawn(0x1030, 0x7D0, 0x7F, 0xF2, 0, false, 3), parent);
    companion.setServices(new RecordingServices(manager));
    for (int i = 0; i < 5; i++)
      parent.missileImpacted();
    companion.update(0, null);
    assertEquals(List.of(0x1018, 0x1018, 0x1028, 0x1038),
                 made.stream().map(AbstractObjectInstance::getX).toList());
    assertEquals(List.of(0x7D2, 0x7D2, 0x7CC, 0x7D4),
                 made.stream().map(AbstractObjectInstance::getY).toList());
  }
  @Test
  void
  wallMissileChildDeletesBeforeMovementAndTogglesOnlyOnFourFrameBoundary() {
    var child = new FbzWallMissileProjectileObjectInstance(spawn(0xE0, 0x10),
                                                           (short)0x400);
    child.update(0, null);
    assertEquals(0x1004, child.getX());
    assertEquals(1, child.mappingFrame());
    AbstractObjectInstance.updateCameraBounds(0, 0, 0x100, 0x100, 0);
    int x = child.getX();
    child.update(4, null);
    assertTrue(child.isDestroyed());
    assertEquals(x, child.getX());
    assertEquals(1, child.mappingFrame());
  }
  @Test
  void
  launcherProjectileUsesTwentyFourEightMotionAndFifthTargetImpactClearsParentMode() {
    ObjectManager manager = mock(ObjectManager.class);
    var services = new RecordingServices(manager);
    var parent = new FbzMissileLauncherObjectInstance(spawn(0x7F, 0xF2));
    parent.setServices(services);
    var ordinary = new FbzMissileLauncherProjectileObjectInstance(
        new ObjectSpawn(0x1000, 0x802, 0x7F, 2, 0, false, 3), parent, 0x100);
    ordinary.setServices(services);
    ordinary.update(0, null);
    assertEquals(0x1000, ordinary.getX());
    assertEquals(0x7FC, ordinary.getY());
    int before = ordinary.getX();
    for (int i = 1; i < 80 && ordinary.getX() == before; i++)
      ordinary.update(i, null);
    assertEquals(0x1001, ordinary.getX());
    for (int i = 0; i < 4; i++)
      parent.missileImpacted();
    var fifth = new FbzMissileLauncherProjectileObjectInstance(
        new ObjectSpawn(0x1000, 0x900, 0x7F, 0xF2, 0, false, 3), parent, 0x100);
    fifth.setServices(services);
    fifth.update(0, null);
    assertTrue(fifth.isDestroyed());
    assertEquals(0, parent.liveImpacts());
    assertFalse(parent.targeting());
    assertEquals(1, fifth.getPriorityBucket());
  }
  @Test
  void
  wallMissileIsIndependentAfterCurrentSiblingAndSurvivesLauncherDeletion() {
    ObjectManager manager = mock(ObjectManager.class);
    var services = new RecordingServices(manager);
    var wall = new FbzWallMissileObjectInstance(spawn(0xE0, 0x10));
    wall.setServices(services);
    for (int i = 0; i <= 64; i++)
      wall.update(i, null);
    ArgumentCaptor<AbstractObjectInstance> cap =
        ArgumentCaptor.forClass(AbstractObjectInstance.class);
    verify(manager).addDynamicObjectAfterCurrent(cap.capture());
    var missile = assertInstanceOf(FbzWallMissileProjectileObjectInstance.class,
                                   cap.getValue());
    wall.setDestroyed(true);
    missile.update(65, null);
    assertFalse(missile.isDestroyed());
  }
  @Test
  void ordinaryLauncherMissileChecksFloorEveryFrameAndSnapsBeforeImpact() {
    ObjectManager manager = mock(ObjectManager.class);
    var services = new RecordingServices(manager);
    var parent = new FbzMissileLauncherObjectInstance(spawn(0x7F, 2));
    parent.setServices(services);
    var missile = new FbzMissileLauncherProjectileObjectInstance(
        new ObjectSpawn(0x1000, 0x802, 0x7F, 2, 0, false, 3), parent, 0x100);
    missile.setServices(services);
    try (MockedStatic<ObjectTerrainUtils> terrain =
             mockStatic(ObjectTerrainUtils.class)) {
      terrain
          .when(()
                    -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(),
                                                         eq(0xC)))
          .thenReturn(new TerrainCheckResult(-3, (byte)0, 1));
      missile.update(0, null);
      terrain.verify(
          () -> ObjectTerrainUtils.checkFloorDist(0x1000, 0x7FC, 0xC));
      assertTrue(missile.isDestroyed());
      assertEquals(0x7F9, missile.getY());
      ArgumentCaptor<ObjectInstance> replacement =
          ArgumentCaptor.forClass(ObjectInstance.class);
      verify(manager).addDynamicObject(replacement.capture());
      assertEquals(0x7FD, replacement.getValue().getY());
    }
  }
  @Test
  void
  companionCullUsesParentAnchorThenDetonationRelocatesAnchorAndDeletesSameUpdate() {
    ObjectManager manager = mock(ObjectManager.class);
    com.openggf.camera.Camera camera = mock(com.openggf.camera.Camera.class);
    when(camera.getX()).thenReturn((short)0xE00);
    var services = new RecordingServices(manager) {
      public com.openggf.camera.Camera camera() { return camera; }
    };
    var parent = new FbzMissileLauncherObjectInstance(
        new ObjectSpawn(0x1070, 0x800, 0x7F, 0xF2, 0, true, 3));
    parent.setServices(services);
    var companion = new FbzMissileLauncherCompanionObjectInstance(
        new ObjectSpawn(0x10A0, 0x7D0, 0x7F, 0xF2, 0, false, 3), parent);
    companion.setServices(services);
    companion.update(0, null);
    assertFalse(companion.isDestroyed());
    for (int i = 0; i < 5; i++)
      parent.missileImpacted();
    companion.update(1, null);
    assertEquals(0x7F00, companion.getX());
    assertTrue(companion.isDestroyed());
  }
  @Test
  void
  alreadySpawnedTargetMissileFallsThroughToFloorProbeAfterFifthImpactClearsParentMode() {
    ObjectManager manager = mock(ObjectManager.class);
    var services = new RecordingServices(manager);
    var parent = new FbzMissileLauncherObjectInstance(spawn(0x7F, 0xF2));
    parent.setServices(services);
    var inFlight = new FbzMissileLauncherProjectileObjectInstance(
        new ObjectSpawn(0x1000, 0x802, 0x7F, 0xF2, 0, false, 3), parent, 0x100);
    inFlight.setServices(services);
    for (int i = 0; i < 5; i++)
      parent.missileImpacted();
    assertFalse(parent.targeting());
    try (MockedStatic<ObjectTerrainUtils> terrain =
             mockStatic(ObjectTerrainUtils.class)) {
      terrain
          .when(()
                    -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(),
                                                         eq(0xC)))
          .thenReturn(new TerrainCheckResult(-1, (byte)0, 1));
      inFlight.update(0, null);
      terrain.verify(
          () -> ObjectTerrainUtils.checkFloorDist(0x1000, 0x7FC, 0xC));
      assertTrue(inFlight.isDestroyed());
    }
  }
  private static class RecordingServices extends TestObjectServices {
    final ObjectManager manager;
    int projectileSfx;
    RecordingServices(ObjectManager m) { manager = m; }
    public ObjectManager objectManager() { return manager; }
    public void playSfx(int id) {
      if (id == com.openggf.game.sonic3k.audio.Sonic3kSfx.LEVEL_PROJECTILE.id)
        projectileSfx++;
    }
  }
  private static ObjectSpawn spawn(int id, int subtype) {
    return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 3);
  }
}
