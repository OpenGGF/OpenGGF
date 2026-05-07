package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.camera.Camera;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RequiresRom(SonicGame.SONIC_3K)
class TestCnzMinibossTopPhysics {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void topAdvancesThroughInitAndWaitToMain() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x0300, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.setServices(services);

        for (int i = 0; i < 240; i++) top.update(i, fixture.sprite());
        assertTrue(top.getCurrentRoutineForTest() >= 6,
                "After 240 frames the top should reach routine 6 (TopMain)");
    }

    @Test
    void waitRoutineFollowsParentUntilBossSignalsLaunch() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);
        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x02E4, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.setServices(services);
        top.attachBossForTest(boss);

        top.update(0, fixture.sprite());
        boss.onArenaChunkDestroyed();
        top.update(1, fixture.sprite());

        assertEquals(boss.getCentreX(), top.getX());
        assertEquals(boss.getCentreY() + 0x2C, top.getY(),
                "Refresh_ChildPosition keeps the waiting top at its parent child offset");
        assertEquals(2, top.getCurrentRoutineForTest(),
                "Without parent $38 bit 1, the top must remain in Wait");
    }

    @Test
    void topExposesRomTouchCollisionFlags() {
        Object top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x0300, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));

        assertTrue(top instanceof TouchResponseProvider,
                "ObjDat3_CNZMinibossTop collision byte $AA must enter Draw_And_Touch_Sprite");
        TouchResponseProvider provider = (TouchResponseProvider) top;
        assertEquals(0xAA, provider.getCollisionFlags());
        assertEquals(0, provider.getCollisionProperty());
    }

    @Test
    void topMainBouncesVerticallyBetweenArenaBounds() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x0300, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.setServices(services);
        top.forceTopMainForTest();
        int startY = top.getY();
        for (int i = 0; i < 60; i++) top.update(i, fixture.sprite());
        assertNotEquals(startY, top.getY(), "TopMain must be moving the top vertically");
    }

    @Test
    void rollingPlayerBounceReversesTopVerticalAndOpposingHorizontalVelocity() {
        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x0300, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.forceTopMainForTest();

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x3242, (short) 0x030E);
        player.setRolling(true);
        player.setCentreX((short) 0x3242);
        player.setCentreY((short) 0x030E);
        player.setXSpeed((short) -0x100);
        player.setYSpeed((short) -0x100);

        top.update(0, player);
        int afterBounceX = top.getX();
        int afterBounceY = top.getY();
        top.update(1, player);

        assertTrue(top.getX() < afterBounceX,
                "Opposing player/top X velocities should reverse the top X velocity");
        assertTrue(top.getY() < afterBounceY,
                "A rolling upward player bounce should reverse downward top Y velocity");
    }

    @Test
    void floorTerrainHitReversesTopAndQueuesSnappedArenaBlockDestruction() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x0300, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.setServices(services);
        top.forceTopMainForTest();
        getCnzEvents().setArenaChunkDestructionQueued(false);

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkRightWallDist(anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(-2, (byte) 0, 0));

            top.update(0, fixture.sprite());
        }

        Sonic3kCNZEvents events = getCnzEvents();
        assertTrue(events.isArenaChunkDestructionQueued());
        assertEquals(0x3250, events.getPendingArenaChunkX());
        assertEquals(0x02F0, events.getPendingArenaChunkY());

        int afterBounceY = top.getY();
        top.update(1, fixture.sprite());
        assertTrue(top.getY() < afterBounceY, "Floor terrain hit must reverse y_vel");
    }

    @Test
    void lowerArenaBoundBounceDoesNotQueueBlockDestruction() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x31F0, 0x0379, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.setServices(services);
        top.forceTopMainForTest();
        getCnzEvents().setArenaChunkDestructionQueued(false);

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkRightWallDist(anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(TerrainCheckResult.noCollision());

            top.update(0, fixture.sprite());
        }

        assertFalse(getCnzEvents().isArenaChunkDestructionQueued(),
                "The $380 lower-bound bounce uses loc_6DDCC, not CNZMiniboss_BlockExplosion");
        int afterBounceY = top.getY();
        top.update(1, fixture.sprite());
        assertTrue(top.getY() < afterBounceY);
    }

    @Test
    void preservesArenaCollisionSeam() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);
        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x0300, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.setServices(services);
        top.attachBossForTest(boss);

        int originalBossY = boss.getCentreY();
        top.forceArenaCollisionForTest(0x3200, 0x0300);
        top.update(0, fixture.sprite());
        assertTrue(boss.getCentreY() > originalBossY,
                "Arena collision seam must still advance boss centre Y");
    }

    @Test
    void topHitDamagesOpenCoilWithoutPlayerHitDamageShortcut() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);
        boss.forceOpenForTest();
        int originalHits = boss.getRemainingHits();

        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x02D8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.setServices(services);
        top.attachBossForTest(boss);
        top.forceTopMainForTest();

        top.update(0, fixture.sprite());

        assertEquals(originalHits - 1, boss.getRemainingHits(),
                "Only the top piece hitting the open coil should consume CNZ miniboss HP");
    }

    @Test
    void playerAttackOpensCoilButDoesNotConsumeBossHp() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);
        int originalHits = boss.getRemainingHits();

        boss.onPlayerAttack(fixture.sprite(), null);

        assertEquals(originalHits, boss.getRemainingHits(),
                "CNZ miniboss player hits open the coil; the bouncing top piece consumes HP");

        for (int frame = 0; frame < 130; frame++) {
            boss.update(frame, fixture.sprite());
        }

        assertTrue(boss.isOpenForTopHit(),
                "Player attack should open the coil for the top-piece damage window");
    }

    @Test
    void productionTouchResponseCoilAttackOpensBossWithoutConsumingHp() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        AbstractPlayableSprite player = fixture.sprite();

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);
        int originalHits = boss.getRemainingHits();

        CnzMinibossCoilInstance coil = new CnzMinibossCoilInstance(
                new ObjectSpawn(0x3240, 0x02D4, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        coil.setServices(services);
        coil.attachBossForTest(boss);

        assertTrue(coil instanceof TouchResponseAttackable,
                "The production coil child must receive Touch_Enemy attack callbacks, not just expose flags");
        assertEquals(0x1A, coil.getCollisionFlags());
        assertEquals(0x70, coil.getCollisionProperty());

        player.setCentreX((short) coil.getX());
        player.setCentreY((short) coil.getY());
        player.setRolling(true);
        player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        player.setXSpeed((short) 0x0120);
        player.setYSpeed((short) -0x0180);
        player.setGSpeed((short) 0x0100);

        ObjectManager objectManager = createIsolatedTouchObjectManager();
        objectManager.addDynamicObject(coil);
        coil.snapshotPreUpdatePosition();
        objectManager.runTouchResponsesForPlayer(player, 1);

        assertEquals(originalHits, boss.getRemainingHits(),
                "Closed-coil player attacks through ObjectManager must open without consuming the top counter");
        assertEquals((short) -0x0120, player.getXSpeed(),
                "Touch_Enemy_Part2 boss-hit path should negate player x_vel when collision_property is non-zero");
        assertEquals((short) 0x0180, player.getYSpeed(),
                "Touch_Enemy_Part2 boss-hit path should negate player y_vel when collision_property is non-zero");
        assertEquals((short) -0x0100, player.getGSpeed(),
                "S3K boss-hit path should negate ground_vel as well");
        for (int frame = 0; frame < 130; frame++) {
            boss.update(frame, fixture.sprite());
        }

        assertTrue(boss.isOpenForTopHit(),
                "A player attack routed through the spawned coil child should open the boss");
        assertEquals(0xA9, coil.getCollisionFlags(),
                "Once the parent is open, the coil child must expose the ROM open collision byte");
        assertEquals(0, coil.getCollisionProperty());
    }

    private static Sonic3kCNZEvents getCnzEvents() {
        Sonic3kLevelEventManager events =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return events.getCnzEvents();
    }

    private static ObjectManager createIsolatedTouchObjectManager() {
        TouchResponseTable table = mock(TouchResponseTable.class);
        when(table.getWidthRadius(0x1A)).thenReturn(0x10);
        when(table.getHeightRadius(0x1A)).thenReturn(0x20);

        DebugOverlayManager debugOverlay = mock(DebugOverlayManager.class);
        when(debugOverlay.isEnabled(any())).thenReturn(false);

        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x3100);
        when(camera.getY()).thenReturn((short) 0x0100);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.isVerticalWrapEnabled()).thenReturn(false);
        AbstractObjectInstance.updateCameraBounds(0x3100, 0x0100, 0x3100 + 320, 0x0100 + 224, 0);

        TestObjectServices objectServices = new TestObjectServices()
                .withDebugOverlay(debugOverlay)
                .withCamera(camera)
                .withGameModule(GameServices.module());
        assertNotNull(objectServices.gameModule());

        return new ObjectManager(List.of(), new ObjectRegistry() {
            @Override
            public com.openggf.level.objects.ObjectInstance create(ObjectSpawn spawn) {
                return null;
            }

            @Override
            public void reportCoverage(List<ObjectSpawn> spawns) {
            }

            @Override
            public String getPrimaryName(int objectId) {
                return "Test";
            }
        }, 0, null, table, null, camera, objectServices);
    }
}
