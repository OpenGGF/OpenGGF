package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.CnzMinibossInstance;
import com.openggf.game.sonic3k.objects.CnzMinibossScrollControlInstance;
import com.openggf.game.sonic3k.objects.CnzMinibossTopInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless coverage for Task 7's CNZ Act 1 miniboss slice.
 *
 * <p>These tests intentionally exercise the narrow ROM seams called out in the
 * plan:
 * {@code Obj_CNZMinibossTop} must publish arena-destruction writes,
 * {@code Obj_CNZMinibossScrollControl} must be the producer for the same
 * {@code Events_fg_5} path Task 2 first covered through direct hooks, and the
 * registry must stop returning placeholders for the Act 1 boss slot while
 * leaving Task 8's end-boss slot untouched.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzMinibossArenaHeadless {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        com.openggf.game.session.SessionManager.clear();
    }

    /**
     * ROM anchors:
     * {@code Obj_CNZMinibossTop} writes the impact coordinates through
     * {@code Events_bg+$00/$02} before calling {@code CNZMiniboss_BlockExplosion},
     * while {@code CNZMiniboss_CheckTopHit} increments the defeat/lowering path on
     * the base. The engine keeps those responsibilities explicit by having the top
     * piece publish the chunk-removal request through the CNZ bridge and by having
     * the base accumulate the lowering rows that later slices consume.
     */
    @Test
    void minibossTopHitQueuesArenaChunkRemovalAndLowersBossBase() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = TestEnvironment.objectServices();

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);

        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x0300, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.setServices(services);
        top.attachBossForTest(boss);

        int originalBossCentreY = boss.getCentreY();

        top.forceArenaCollisionForTest(0x3200, 0x0300);
        top.update(0, fixture.sprite());

        Sonic3kCNZEvents events = getCnzEvents();
        assertEquals(0x3200, events.getPendingArenaChunkX());
        assertEquals(0x0300, events.getPendingArenaChunkY());
        assertTrue(events.getDestroyedArenaRows() >= 0x20,
                "Each top-piece collision should publish at least one 0x20-pixel arena row removal");
        assertTrue(boss.getCentreY() > originalBossCentreY,
                "The miniboss base should start lowering once the top piece reports an arena hit");
    }

    @Test
    void minibossStartCreatesProductionTopPieceAndCoilChild() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        Sonic3kCNZEvents events = getCnzEvents();
        GameServices.camera().setX((short) 0x31E0);
        for (int frame = 0; frame < 121; frame++) {
            events.update(0, frame);
        }

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        objectManager.addDynamicObject(boss);

        boss.update(0, fixture.sprite());

        assertTrue(objectManager.getActiveObjects().stream()
                        .anyMatch(CnzMinibossTopInstance.class::isInstance),
                "Obj_CNZMiniboss init must create the production top-piece child");
        assertTrue(objectManager.getActiveObjects().stream()
                        .filter(object -> object != boss)
                        .map(object -> object.getClass().getSimpleName().toLowerCase(Locale.ROOT))
                        .anyMatch(name -> name.contains("coil")),
                "Obj_CNZMiniboss init must create a production coil child, not only parent-local state");
    }

    /**
     * ROM anchor: {@code Obj_CNZMinibossScrollControl} consumes the boss-defeat
     * signal, waits for the scroll accumulator to reach {@code $1C0}, then sets
     * {@code Events_fg_5}. Task 2 covered the consumer side directly by forcing
     * the event flag in {@code Sonic3kCNZEvents}; this test locks the producer
     * path by requiring the scroll-control helper to drive that same transition
     * through the bridge instead of through direct test-only hooks.
     */
    @Test
    void scrollControlBridgeSignalAdvancesCnzEventState() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = TestEnvironment.objectServices();
        Sonic3kCNZEvents events = getCnzEvents();
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_AFTER_BOSS);
        events.forceBossBackgroundMode(Sonic3kCNZEvents.BossBackgroundMode.ACT1_POST_BOSS);

        CnzMinibossScrollControlInstance control = new CnzMinibossScrollControlInstance(
                new ObjectSpawn(0x3200, 0x0280, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        control.setServices(services);
        control.forceBossDefeatSignalForTest();
        control.forceAccumulatedOffsetForTest(0x01C0_0000);

        control.update(0, fixture.sprite());
        events.update(0, 1);

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH, events.getBackgroundRoutine());
        assertEquals(0x01C0, events.getBossScrollOffsetY(),
                "The scroll-control object should publish the same threshold-crossing offset that gates the handoff");
    }

    @Test
    void scrollControlAcceleratesToRomCapAndPublishesOffsetState() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = TestEnvironment.objectServices();
        Sonic3kCNZEvents events = getCnzEvents();

        CnzMinibossScrollControlInstance control = new CnzMinibossScrollControlInstance(
                new ObjectSpawn(0x3200, 0x0280, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        control.setServices(services);

        for (int frame = 0; frame < 600; frame++) {
            control.update(frame, fixture.sprite());
        }

        assertEquals(0x40000, events.getBossScrollVelocityY(),
                "Obj_CNZMinibossScrollControl must accelerate Events_bg+$0C up to $40000");
        assertTrue(events.getBossScrollOffsetY() > 0,
                "Obj_CNZMinibossScrollControl must accumulate Events_bg+$08 while scrolling the tunnel");
        assertFalse(control.isDestroyed(),
                "Scroll control must stay alive while waiting for the post-boss signal");
    }

    @Test
    void scrollControlPostBossPhasesSnapEnableBackgroundCollisionAndDeleteAt1C0() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = TestEnvironment.objectServices();
        Sonic3kCNZEvents events = getCnzEvents();
        GameServices.camera().setMaxYTarget((short) 0x02B8);
        GameServices.gameState().setBackgroundCollisionFlag(false);

        CnzMinibossScrollControlInstance control = new CnzMinibossScrollControlInstance(
                new ObjectSpawn(0x3200, 0x0280, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        control.setServices(services);
        control.forceBossDefeatSignalForTest();
        control.forceAccumulatedOffsetForTest(0x01BF_0000);

        for (int frame = 0; frame < 40 && !control.isDestroyed(); frame++) {
            control.update(frame, fixture.sprite());
        }

        assertEquals(0x01C0, events.getBossScrollOffsetY(),
                "Scroll control must keep publishing the final $1C0 handoff offset");
        assertEquals(0x1000, GameServices.camera().getMaxYTarget() & 0xFFFF,
                "The snap phase must set Camera_Max_Y_pos_target to $1000");
        assertTrue(GameServices.gameState().isBackgroundCollisionFlag(),
                "The snap phase must enable background collision for the upper tunnel");
        assertTrue(control.isDestroyed(),
                "Scroll control must delete itself after the post-boss offset reaches $1C0");
    }

    @Test
    void scrollControlInitMutatesLiveLayoutCells() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        MutableLevel mutableLevel = MutableLevel.snapshot(GameServices.level().getCurrentLevel());
        GameServices.level().setLevel(mutableLevel);
        mutableLevel.consumeDirtyMapCells();
        byte[] before = mutableLevel.getMap().getData().clone();

        CnzMinibossScrollControlInstance control = new CnzMinibossScrollControlInstance(
                new ObjectSpawn(0x3200, 0x0280, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        control.setServices(TestEnvironment.objectServices());

        control.update(0, fixture.sprite());

        BitSet dirtyCells = mutableLevel.consumeDirtyMapCells();
        assertFalse(dirtyCells.isEmpty(),
                "Scroll-control init must queue/apply actual lower-tunnel FG/BG layout mutations");
        assertFalse(Arrays.equals(before, mutableLevel.getMap().getData()),
                "Scroll-control init must change live MutableLevel map data, not only event counters");
        assertFalse(dirtyCells.get(0),
                "Scroll-control init must not satisfy the test by touching the origin cell");
        assertTrue(dirtyCells.get(linearMapCell(mutableLevel, 0, 0x3180 / 0x80, 0x0280 / 0x80 - 1)),
                "Scroll-control init must mutate the ROM FG tunnel cell copied from $3180,$280");
        assertTrue(dirtyCells.get(linearMapCell(mutableLevel, 1, 4, 1)),
                "Scroll-control init must mutate the first ROM BG tunnel continuation cell");
        assertTrue(dirtyCells.get(linearMapCell(mutableLevel, 1, 4, 2)),
                "Scroll-control init must mutate the second ROM BG tunnel continuation cell");
    }

    /**
     * Task 7 permanently owns the Act 1 boss slot. Later CNZ slices may promote
     * the end-boss slot, but the miniboss registry contract should remain stable:
     * {@code Obj_CNZMiniboss} must resolve to the real Act 1 boss implementation
     * rather than falling back to a placeholder.
     */
    @Test
    void registryKeepsCnzMinibossPromotedAfterLaterCnzSlices() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance miniboss = registry.create(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));

        assertInstanceOf(CnzMinibossInstance.class, miniboss);
        assertNotEquals(PlaceholderObjectInstance.class, miniboss.getClass(),
                "Task 7 should replace the miniboss placeholder-backed slot with the real Act 1 boss object");
    }

    private Sonic3kCNZEvents getCnzEvents() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return manager.getCnzEvents();
    }

    private int linearMapCell(MutableLevel level, int layer, int x, int y) {
        return layer * level.getMap().getWidth() * level.getMap().getHeight()
                + y * level.getMap().getWidth() + x;
    }
}
