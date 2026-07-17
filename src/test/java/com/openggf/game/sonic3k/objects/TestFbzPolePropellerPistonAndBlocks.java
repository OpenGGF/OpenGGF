package com.openggf.game.sonic3k.objects;
import com.openggf.camera.Camera;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.openggf.game.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.*;
class TestFbzPolePropellerPistonAndBlocks {
  @Test
  void everyPlacedPoleSubtypeUsesEightPixelHeightUnits() {
    for (int s : new int[] {0x0C, 0x0E, 0x14, 0x1A})
      assertEquals(
          s << 3,
          new FbzSpinningPoleObjectInstance(spawn(0x7B, s)).poleHeight());
  }
  @Test
  void propellerCyclesExactTouchFlags() {
    var o = new FbzPropellerObjectInstance(spawn(0x7C, 0));
    o.setServices(new TestObjectServices());
    assertArrayEquals(new int[] {0xB6, 0, 0xB6, 0xB7}, o.collisionCycle());
    int[] frames = {1, 2, 3, 0}, flags = {0, 0xB6, 0xB7, 0xB6};
    for (int i = 0; i < 4; i++) {
      o.update(i, null);
      assertEquals(frames[i], o.mappingFrame());
      assertEquals(flags[i], o.getCollisionFlags());
    }
  }
  @Test
  void pistonAndEveryBlockSubtypeDecodeExactMotionAndSolids() {
    var p = new FbzPistonObjectInstance(spawn(0x7D, 0x28));
    assertEquals(0x140, p.travel());
    assertEquals(0x300, p.cullWidth());
    for (int s : new int[] {0, 2, 0x14}) {
      var b = new FbzPlatformBlocksObjectInstance(spawn(0x7E, s));
      assertEquals((s >>> 4) & 7, b.mappingFrame());
      assertEquals(new int[] {0x10, 0x20, 0x30, 0x40}[(s >>> 4) & 7],
                   b.nativeWidth());
      assertEquals((s & 0xF) << 4, b.travel());
    }
  }
  @Test
  void
  pistonMovesTwoPixelsPerTickToExactEndpointAndBlocksFollowOnlyMainPlayerY() {
    var p = new FbzPistonObjectInstance(spawn(0x7D, 0x28));
    p.setServices(new TestObjectServices());
    for (int i = 0; i < 160; i++)
      p.update(i, null);
    assertEquals(0x1000 - 0x140, p.getX());
    TestSprite main = new TestSprite();
    main.setCentreY((short)0x840);
    var b = new FbzPlatformBlocksObjectInstance(spawn(0x7E, 0x14));
    b.setServices(new TestObjectServices());
    for (int i = 0; i < 8; i++)
      b.update(i, main);
    assertEquals(0x1040, b.getX());
    main.setCentreY((short)0x700);
    for (int i = 0; i < 8; i++)
      b.update(i, main);
    assertEquals(0x1000, b.getX());
  }
  @Test
  void poleParticipantStateScalesPastSixteenAndCapturesAllEligiblePlayers() {
    ArrayList<TestSprite> ps = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      TestSprite p = new TestSprite();
      p.setCentreX((short)0x1000);
      p.setCentreY((short)0x790);
      ps.add(p);
    }
    var pole = new FbzSpinningPoleObjectInstance(spawn(0x7B, 0x14));
    pole.setServices(
        new PlayersServices(ps.getFirst(), ps.subList(1, ps.size())));
    pole.update(0, null);
    assertTrue(
        ps.stream().allMatch(AbstractPlayableSprite::isObjectControlled));
    assertTrue(ps.stream().allMatch(
        AbstractPlayableSprite::isObjectMappingFrameControl));
  }
  @Test
  void onlyHeldLeaderForcesPoleCameraAndSidekickCannotOwnIt() {
    TestSprite main = new TestSprite(), sidekick = new TestSprite();
    main.setCentreX((short)0x1000);
    main.setCentreY((short)0x790);
    sidekick.setCentreX((short)0x1000);
    sidekick.setCentreY((short)0x790);
    Camera camera = mock(Camera.class);
    var services = new PlayersServices(main, List.of(sidekick));
    services.withCamera(camera);
    var pole = new FbzSpinningPoleObjectInstance(spawn(0x7B, 0x14));
    pole.setServices(services);
    pole.update(0, null);
    verify(camera).requestForcedScroll(0x1000, main.getCentreY());
    main.setJumpInputPressed(true, true);
    main.setLogicalInputState(false, false, false, false, true, true);
    pole.update(1, null);
    main.setJumpInputPressed(false, false);
    main.setLogicalInputState(false, false, false, false, false, false);
    clearInvocations(camera);
    pole.update(2, null);
    verify(camera, never()).requestForcedScroll(anyInt(), anyInt());
    assertTrue(sidekick.isObjectControlled());
  }
  @Test
  void poleLeftAloneDoesNotReleaseAndInvalidCleanupPreservesVelocity() {
    TestSprite p = new TestSprite();
    p.setCentreX((short)0x1000);
    p.setCentreY((short)0x790);
    var pole = new FbzSpinningPoleObjectInstance(spawn(0x7B, 0x14));
    pole.setServices(new PlayersServices(p, List.of()));
    pole.update(0, null);
    p.setLogicalInputState(false, false, true, false, false);
    pole.update(1, null);
    assertTrue(p.isObjectControlled());
    p.setXSpeed((short)0x234);
    p.setYSpeed((short)-0x345);
    p.setHurt(true);
    pole.update(2, null);
    assertEquals((short)0x234, p.getXSpeed());
    assertEquals((short)-0x345, p.getYSpeed());
    assertFalse(p.isObjectControlled());
  }
  @Test
  void poleDoesNotLaunchWhenJumpWasHeldBeforeCaptureWithoutLogicalPressEdge() {
    TestSprite p = new TestSprite();
    p.setCentreX((short)0x1000);
    p.setCentreY((short)0x790);
    p.setJumpInputPressed(true, false);
    p.setLogicalInputState(false, false, false, false, true, false);
    var pole = new FbzSpinningPoleObjectInstance(spawn(0x7B, 0x14));
    pole.setServices(new PlayersServices(p, List.of()));
    pole.update(0, null);
    pole.update(1, null);
    assertTrue(p.isObjectControlled());
    p.setLogicalInputState(false, false, false, false, true, true);
    pole.update(2, null);
    assertFalse(p.isObjectControlled());
  }
  @Test
  void rollingCaptureAppliesOnlyTheNativeRadiusDeltaAndPreservesYSubpixel() {
    TestSprite p = new TestSprite();
    p.setRolling(true);
    p.applyCustomRadii(6, 10);
    p.setCentreX((short)0x1000);
    p.setCentreY((short)0x790);
    p.setSubpixelRaw(0x1234, 0x5678);
    var pole = new FbzSpinningPoleObjectInstance(spawn(0x7B, 0x14));
    pole.setServices(new PlayersServices(p, List.of()));
    pole.update(0, null);
    assertEquals(0x787, p.getCentreY());
    assertEquals(0x5678, p.getYSubpixelRaw());
    assertEquals(p.getStandYRadius(), p.getYRadius());
  }
  @Test
  void poleCaptureClearsRenderFlagsBeforeApplyingInitialAngleOffset() {
    TestSprite p = new TestSprite();
    p.setCentreX((short)0x1000);
    p.setCentreY((short)0x790);
    p.setDirection(com.openggf.physics.Direction.LEFT);
    p.setRenderFlips(true, true);
    var pole = new FbzSpinningPoleObjectInstance(spawn(0x7B, 0x14));
    pole.setServices(new PlayersServices(p, List.of()));

    pole.update(0, null);

    assertEquals(0x100A, p.getCentreX(),
        "angle $E0 uses the unmirrored +$A offset after render_flags is cleared");
    assertFalse(p.getRenderHFlip());
    assertFalse(p.getRenderVFlip());
    assertEquals(com.openggf.physics.Direction.RIGHT, p.getDirection());
  }
  @Test
  void
  poleLaunchChangesToCharacterRollingRadiiWithoutMovingNativeYOrSubpixel() {
    TestSprite p = new TestSprite();
    p.setCentreX((short)0x1000);
    p.setCentreY((short)0x790);
    p.setSubpixelRaw(0x1234, 0xA5A5);
    var pole = new FbzSpinningPoleObjectInstance(spawn(0x7B, 0x14));
    pole.setServices(new PlayersServices(p, List.of()));
    pole.update(0, null);
    int heldY = p.getCentreY();
    p.setLogicalInputState(false, false, false, false, true, true);
    pole.update(1, null);
    assertEquals(heldY, p.getCentreY());
    assertEquals(0xA5A5, p.getYSubpixelRaw());
    assertEquals(p.getRollYRadius(), p.getYRadius());
  }
  @Test
  void pistonLeavesCustomRangeEvaluationToObjectManagerAndAcceptsNullCamera() {
    TestObjectServices services = new TestObjectServices();
    var piston = new FbzPistonObjectInstance(spawn(0x7D, 0x28));
    piston.setServices(services);
    assertDoesNotThrow(() -> piston.update(0, null));
    Camera camera = mock(Camera.class);
    piston.setServices(new TestObjectServices().withCamera(camera));
    piston.update(1, null);
    verifyNoInteractions(camera);
    assertTrue(piston.usesCustomOutOfRangeCheck());
    assertTrue(piston.isCustomOutOfRange(-0x4000));
  }
  @Test
  void propellerDeleteTouchUsesCoarseXOnlyNotVerticalViewport() {
    Camera camera = mock(Camera.class);
    when(camera.getX()).thenReturn((short)0xE00);
    var propeller = new FbzPropellerObjectInstance(spawn(0x7C, 0));
    propeller.setServices(new TestObjectServices().withCamera(camera));
    AbstractObjectInstance.updateCameraBounds(0xE00, 0, 0x1200, 0x100, 0);
    propeller.update(0, null);
    assertFalse(propeller.isDestroyed());
  }
  private static final class PlayersServices extends TestObjectServices {
    final ObjectPlayerQuery q;
    PlayersServices(PlayableEntity main, List<? extends PlayableEntity> s) {
      q = new ObjectPlayerQuery(() -> main, () -> s);
    }
    public ObjectPlayerQuery playerQuery() { return q; }
  }
  private static final class TestSprite extends AbstractPlayableSprite {
    TestSprite() { super("sonic", (short)0, (short)0); }
    public void draw() {}
    public void defineSpeeds() {}
    protected void createSensorLines() {}
  }
  private static ObjectSpawn spawn(int id, int subtype) {
    return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 2);
  }
}
