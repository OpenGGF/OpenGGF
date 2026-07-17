package com.openggf.game.sonic3k.objects;
import com.openggf.camera.Camera;
import com.openggf.game.rules.GameRules;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.physics.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
@ExtendWith(SingletonResetExtension.class)
@FullReset
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
  void wallMissileArtUsesEightExplicitFramesBecauseItsFirstOffsetsPointBackward() {
    var entry = Sonic3kPlcArtRegistry.getPlan(4, 0).levelArt().stream()
        .filter(candidate -> candidate.key().equals(Sonic3kObjectArtKeys.FBZ_WALL_MISSILE))
        .findFirst()
        .orElseThrow();

    assertEquals(8, entry.mappingFrameCount());
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
  void launcherArmingReadsRomVisibleLevelFrameCounterInsteadOfObjectVblankClock() {
    ObjectManager manager = mock(ObjectManager.class);
    LevelManager levelManager = mock(LevelManager.class);
    when(levelManager.getFrameCounter()).thenReturn(0xFE, 0xFF);
    var services = new RecordingServices(manager).withLevelManager(levelManager);
    var launcher = new FbzMissileLauncherObjectInstance(spawn(0x7F, 0));
    launcher.setServices(services);

    // loc_3C534 reads the low byte of (Level_frame_counter+1). The unrelated
    // ObjectManager VBla argument is zero here, but the ROM-visible byte is
    // $FF, so this update must not arm the launcher.
    launcher.update(0, null);
    verify(manager, never()).addDynamicObjectAfterCurrent(any());

    // One gameplay tick later the ROM-visible counter wraps to $00. The
    // launcher arms and its zero timer emits the first projectile immediately,
    // even though the object VBla argument is now 1.
    launcher.update(1, null);
    verify(manager).addDynamicObjectAfterCurrent(
        isA(FbzMissileLauncherProjectileObjectInstance.class));
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
  void wallLauncherCountdownUsesItsExactRenderSpriteBounds() {
    ObjectManager manager = mock(ObjectManager.class);
    var wall = new FbzWallMissileObjectInstance(
        new ObjectSpawn(0x1310, 0x800, 0xE0, 0, 0, true, 3));
    wall.setServices(new RecordingServices(manager));

    // Render_Sprites rejects the exclusive right edge at
    // cameraRight + width_pixels ($10), so the native render_flags sign is clear.
    wall.update(0, null);
    verify(manager, never()).addDynamicObjectAfterCurrent(any());

    // Moving that edge one pixel right makes the same object visible. Subtype 0
    // has a zero countdown, so the first eligible update allocates immediately.
    AbstractObjectInstance.updateCameraBounds(0xF00, 0x700, 0x1301, 0x900, 0);
    wall.update(1, null);
    verify(manager).addDynamicObjectAfterCurrent(any());
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
    for (int frame = 0; frame < 64; frame++) {
      fifth.update(frame, null);
      assertFalse(fifth.isDestroyed(),
          "target-height impact is unreachable from the rising branch");
    }
    fifth.update(64, null);
    assertFalse(fifth.isDestroyed(),
        "loc_3C716 installs loc_3C768 but still publishes/draws this callback");
    assertEquals(0, parent.liveImpacts());
    assertFalse(parent.targeting());
    assertEquals(0x9E, fifth.getCollisionFlags());
    fifth.update(65, null);
    assertTrue(fifth.isDestroyed());
    assertEquals(0, fifth.getCollisionFlags());
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
  void ordinaryLauncherMissileSkipsFloorWhileRisingThenSnapsOnFirstDescendingStep() {
    ObjectManager manager = mock(ObjectManager.class);
    var services = new RecordingServices(manager);
    var parent = new FbzMissileLauncherObjectInstance(spawn(0x7F, 0xF2));
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
      // loc_3C6CC branches directly to loc_3C740 while the pre-move y_vel is
      // negative. Starting at -$600 and adding $18 takes exactly 64 updates
      // to reach zero; none of those rising callbacks may probe the floor.
      for (int frame = 0; frame < 63; frame++) {
        missile.update(frame, null);
        assertFalse(missile.isDestroyed(),
            "rising missiles pass through the launch surface before falling");
      }
      assertEquals(0, missile.getCollisionFlags(),
          "collision stays clear while the post-gravity y_vel remains negative");
      missile.update(63, null);
      assertEquals(0x9E, missile.getCollisionFlags(),
          "the last negative-entry callback reaches y_vel=0 and enables $9E");
      terrain.verify(
          () -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), eq(0xC)),
          never());

      int descendingX = missile.getX();
      int descendingY = missile.getY();
      missile.update(64, null);
      terrain.verify(
          () -> ObjectTerrainUtils.checkFloorDist(descendingX, descendingY,
                                                   0xC));
      assertFalse(missile.isDestroyed(),
          "floor hit installs loc_3C768 for the next object callback");
      assertEquals(5, parent.liveImpacts(),
          "loc_3C700 floor impact does not decrement companion $40");
      assertEquals(descendingX + 1, missile.getX(),
          "loc_3C700 still reaches loc_3C740's thresholded horizontal add");
      assertEquals(descendingY - 3, missile.getY());
      verify(manager, never()).addDynamicObject(any());

      missile.update(65, null);
      assertTrue(missile.isDestroyed());
      assertEquals(5, parent.liveImpacts());
      ArgumentCaptor<ObjectInstance> replacement =
          ArgumentCaptor.forClass(ObjectInstance.class);
      verify(manager).addDynamicObject(replacement.capture());
      assertEquals(descendingY + 1, replacement.getValue().getY());
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
  void companionOwnsItsSolidCheckpointBecauseDetonationCullsAfterTheRomReleasePass() {
    var parent = new FbzMissileLauncherObjectInstance(
        new ObjectSpawn(0x1070, 0x800, 0x7F, 0xF2, 0, true, 3));
    var companion = new FbzMissileLauncherCompanionObjectInstance(
        new ObjectSpawn(0x10A0, 0x7D0, 0x7F, 0xF2, 0, false, 3), parent);

    assertEquals(SolidExecutionMode.MANUAL_CHECKPOINT,
                 companion.solidExecutionMode(),
        "loc_3C636 must run SolidObjectFull after relocating to $7F00 and "
            + "before Sprite_OnScreen_Test2 recycles the SST slot "
            + "(sonic3k.asm:80231-80269)");
  }
  @Test
  void detonationReleasesNativePairThenExtraSidekicksBeforeSameFrameCull() {
    final int cameraX = 0x0F00;
    Camera camera = new Camera() {
      @Override public short getX() { return (short)cameraX; }
      @Override public short getY() { return (short)0x0700; }
      @Override public short getWidth() { return 320; }
      @Override public short getHeight() { return 224; }
    };
    ObjectManager[] holder = new ObjectManager[1];
    TestObjectServices services = new TestObjectServices() {
      @Override public ObjectManager objectManager() { return holder[0]; }
    };
    services.withCamera(camera)
        .withGraphicsManager(GraphicsManager.getInstance())
        .withSolidExecutionRegistry(new DefaultSolidExecutionRegistry());
    ObjectManager manager = new ObjectManager(List.of(),
        new Sonic3kObjectRegistry(), 0, null, null,
        GraphicsManager.getInstance(), camera, services);
    holder[0] = manager;
    manager.reset(cameraX);

    var parent = new FbzMissileLauncherObjectInstance(
        new ObjectSpawn(0x1070, 0x800, 0x7F, 0xF2, 0, true, 3));
    parent.setServices(services);
    var companion = manager.createDynamicObject(
        () -> new FbzMissileLauncherCompanionObjectInstance(
            new ObjectSpawn(0x10A0, 0x7D0, 0x7F, 0xF2, 0, false, 3),
            parent));
    assertNotNull(companion);

    List<String> releaseOrder = new ArrayList<>();
    ReleaseOrderPlayer p1 = new ReleaseOrderPlayer("P1", releaseOrder);
    ReleaseOrderPlayer p2 = new ReleaseOrderPlayer("P2", releaseOrder);
    ReleaseOrderPlayer extra1 = new ReleaseOrderPlayer("extra-1", releaseOrder);
    ReleaseOrderPlayer extra2 = new ReleaseOrderPlayer("extra-2", releaseOrder);
    List<ReleaseOrderPlayer> participants = List.of(p1, p2, extra1, extra2);
    for (ReleaseOrderPlayer participant : participants) {
      participant.setCentreX((short)0x10A0);
      participant.setCentreY((short)0x7B8);
      manager.forceRidingObjectForBootstrap(participant, companion);
      assertTrue(manager.hasObjectStandingBit(participant, companion));
    }
    releaseOrder.clear();
    for (int i = 0; i < 5; i++) {
      parent.missileImpacted();
    }

    manager.update(cameraX, p1, List.of(p2, extra1, extra2),
                   0, false, true, false);

    assertEquals(List.of("P1", "P2", "extra-1", "extra-2"), releaseOrder,
        "SolidObjectFull resolves the native P1/P2 pair first, then every "
            + "engine extension participant, exactly once");
    for (ReleaseOrderPlayer participant : participants) {
      assertTrue(participant.getAir());
      assertFalse(participant.isOnObject());
      assertFalse(manager.isRidingObject(participant, companion));
      assertFalse(manager.hasObjectStandingBit(participant, companion));
    }
    assertEquals(0x7F00, companion.getX());
    assertTrue(companion.isDestroyed(),
        "Sprite_OnScreen_Test2 still recycles the companion in the detonation callback");
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
      for (int frame = 0; frame < 64; frame++) {
        inFlight.update(frame, null);
        assertFalse(inFlight.isDestroyed());
      }
      terrain.verify(
          () -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), eq(0xC)),
          never());
      int descendingX = inFlight.getX();
      int descendingY = inFlight.getY();
      inFlight.update(64, null);
      terrain.verify(
          () -> ObjectTerrainUtils.checkFloorDist(descendingX, descendingY,
                                                   0xC));
      assertFalse(inFlight.isDestroyed());
      inFlight.update(65, null);
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
  private static final class ReleaseOrderPlayer extends AbstractPlayableSprite {
    private final String participantId;
    private final List<String> releaseOrder;
    private ReleaseOrderPlayer(String participantId, List<String> releaseOrder) {
      super("FBZ_MISSILE_RELEASE_" + participantId, (short)0, (short)0);
      this.participantId = participantId;
      this.releaseOrder = releaseOrder;
      setWidth(20);
      setHeight(38);
      setGameRulesForTest(GameRules.SONIC_3K);
    }
    @Override public void setAir(boolean air) {
      boolean wasAir = getAir();
      super.setAir(air);
      if (air && !wasAir && releaseOrder != null) {
        releaseOrder.add(participantId);
      }
    }
    @Override protected void defineSpeeds() {
      runAccel=0;runDecel=0;friction=0;max=0;jump=0;angle=0;
      slopeRunning=0;slopeRollingDown=0;slopeRollingUp=0;rollDecel=0;
      minStartRollSpeed=0;minRollSpeed=0;maxRoll=0;rollHeight=28;runHeight=38;
      standXRadius=9;standYRadius=19;rollXRadius=7;rollYRadius=14;
    }
    @Override protected void createSensorLines() {
      groundSensors=new Sensor[0];ceilingSensors=new Sensor[0];pushSensors=new Sensor[0];
    }
    @Override public void draw() { }
  }
  private static ObjectSpawn spawn(int id, int subtype) {
    return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 3);
  }
}
