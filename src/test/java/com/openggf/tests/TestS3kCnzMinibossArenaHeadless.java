package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
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
     * while the CNZ arena row scan later advances {@code Events_bg+$04} after a
     * whole row has cleared. The engine keeps those responsibilities explicit by
     * having the top piece publish the chunk-removal request through the CNZ bridge
     * without directly lowering the base.
     */
    @Test
    void minibossTopHitQueuesArenaChunkRemovalWithoutDirectBaseLowering() {
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
        assertEquals(originalBossCentreY, boss.getCentreY(),
                "CNZMiniboss_BlockExplosion does not write Events_bg+$04; the base lowering path "
                        + "must wait for the arena row-clear signal");
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
        boss.update(1, fixture.sprite());

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
    void scrollControlBridgeSignalUsesRomWaitAndSlowPathBeforeEventHandoff() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = TestEnvironment.objectServices();
        Sonic3kCNZEvents events = getCnzEvents();
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_AFTER_BOSS);
        events.forceBossBackgroundMode(Sonic3kCNZEvents.BossBackgroundMode.ACT1_POST_BOSS);
        GameServices.camera().setMaxYTarget((short) 0x02B8);
        GameServices.gameState().setBackgroundCollisionFlag(false);

        CnzMinibossScrollControlInstance control = new CnzMinibossScrollControlInstance(
                new ObjectSpawn(0x3200, 0x0280, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        control.setServices(services);
        control.forceBossDefeatSignalForTest();
        control.forceAccumulatedOffsetForTest(0x01C0_0000);

        control.update(0, fixture.sprite());
        events.update(0, 1);

        assertEquals(Sonic3kCNZEvents.BG_AFTER_BOSS, events.getBackgroundRoutine(),
                "Obj_CNZMinibossScrollControl loc_52042 only enters Wait after the defeat signal; "
                        + "it must not set Events_fg_5 or Camera_target_max_Y_pos in the same update "
                        + "(docs/skdisasm/sonic3k.asm:107770-107795)");
        assertEquals(0x02B8, GameServices.camera().getMaxYTarget() & 0xFFFF,
                "Camera_target_max_Y_pos remains the arena max until Obj_CNZMinibossScrollWait2 "
                        + "reaches loc_5209E (docs/skdisasm/sonic3k.asm:107814-107828)");
        assertFalse(control.isDestroyed(),
                "The scroll-control object must remain live while it passes through Wait/Slow/Wait2.");

        for (int frame = 1; frame < 600 && !control.isDestroyed(); frame++) {
            control.update(frame, fixture.sprite());
        }
        events.update(0, 121);

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH, events.getBackgroundRoutine(),
                "CNZ1BGE_FGRefresh must wait for Draw_delayed_rowcount before the BG->FG copy "
                        + "(docs/skdisasm/sonic3k.asm:107523-107539)");
        assertEquals(0x01C0, events.getBossScrollOffsetY(),
                "The scroll-control object should publish the same threshold-crossing offset during "
                        + "the delayed refresh window");
        advanceCnzPostBossRefresh(events, 122, 15);

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH_2, events.getBackgroundRoutine());
        assertEquals(0, events.getBossScrollOffsetY(),
                "CNZ1BGE_FGRefresh loc_51DAE clears Events_bg+$08 after the BG->FG copy "
                        + "(docs/skdisasm/sonic3k.asm:107562-107563)");
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
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_BOSS);
        GameServices.camera().setMaxYTarget((short) 0x02B8);
        GameServices.gameState().setBackgroundCollisionFlag(false);

        CnzMinibossScrollControlInstance control = new CnzMinibossScrollControlInstance(
                new ObjectSpawn(0x3200, 0x0280, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        control.setServices(services);
        control.forceBossDefeatSignalForTest();
        control.forceAccumulatedOffsetForTest(0x01BF_0000);

        for (int frame = 0; frame < 600 && !control.isDestroyed(); frame++) {
            control.update(frame, fixture.sprite());
        }

        assertEquals(0x01C0, events.getBossScrollOffsetY(),
                "Scroll control must keep publishing the final $1C0 handoff offset");
        assertEquals(0x1000, GameServices.camera().getMaxYTarget() & 0xFFFF,
                "The snap phase must set Camera_Max_Y_pos_target to $1000");
        assertTrue(GameServices.gameState().isBackgroundCollisionFlag(),
                "The snap phase must enable background collision for the upper tunnel");
        assertEquals(Sonic3kCNZEvents.BG_AFTER_BOSS, events.getBackgroundRoutine(),
                "Obj_CNZMinibossScrollWait2 loc_5209E must advance Events_routine_bg "
                        + "when it enables BG collision (docs/skdisasm/sonic3k.asm:107814-107828)");
        assertTrue(control.isDestroyed(),
                "Scroll control must delete itself after the post-boss offset reaches $1C0");
    }

    @Test
    void fgRefreshCopiesBossTunnelBgLayoutBackToForegroundCollision() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        MutableLevel mutableLevel = MutableLevel.snapshot(GameServices.level().getCurrentLevel());
        GameServices.level().setLevel(mutableLevel);
        Sonic3kCNZEvents events = getCnzEvents();

        int[][] source = new int[6][5];
        for (int row = 0; row < source.length; row++) {
            for (int column = 0; column < source[row].length; column++) {
                source[row][column] = mutableLevel.getMap()
                        .getValue(1, 0x0200 / 0x80 + column, 0x0180 / 0x80 + row) & 0xFF;
                int differentValue = source[row][column] == 0 ? 1 : 0;
                mutableLevel.setBlockInMap(0, 0x3180 / 0x80 + column,
                        0x0280 / 0x80 + row, differentValue);
            }
        }
        GameServices.gameState().setBackgroundCollisionFlag(true);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_FG_REFRESH);

        events.update(0, fixture.frameCount());

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH, events.getBackgroundRoutine(),
                "CNZ1BGE_FGRefresh must not copy foreground collision until "
                        + "Draw_PlaneVertSingleBottomUp drives Draw_delayed_rowcount below zero "
                        + "(docs/skdisasm/sonic3k.asm:103436-103452,107527-107539)");
        assertTrue(GameServices.gameState().isBackgroundCollisionFlag(),
                "Background_collision_flag stays active during the delayed post-boss refresh");

        advanceCnzPostBossRefresh(events, fixture.frameCount() + 1, 15);

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH_2, events.getBackgroundRoutine(),
                "CNZ1BGE_FGRefresh should advance to the second refresh pass after the BG->FG copy");
        assertFalse(GameServices.gameState().isBackgroundCollisionFlag(),
                "CNZ1BGE_FGRefresh clears Background_collision_flag after copying BG layout to FG");
        for (int row = 0; row < source.length; row++) {
            for (int column = 0; column < source[row].length; column++) {
                assertEquals(source[row][column],
                        mutableLevel.getMap().getValue(0, 0x3180 / 0x80 + column,
                                0x0280 / 0x80 + row) & 0xFF,
                        "CNZ1BGE_FGRefresh must copy the ROM 5x6 boss tunnel layout window");
            }
        }
    }

    @Test
    void fgRefreshRestoresTailsLandingCellFromBossBackgroundLayout() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        MutableLevel mutableLevel = MutableLevel.snapshot(GameServices.level().getCurrentLevel());
        GameServices.level().setLevel(mutableLevel);
        Sonic3kCNZEvents events = getCnzEvents();

        CnzMinibossScrollControlInstance control = new CnzMinibossScrollControlInstance(
                new ObjectSpawn(0x3200, 0x0280, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        control.setServices(TestEnvironment.objectServices());
        int beforeBg63 = mutableLevel.getMap().getValue(1, 6, 3) & 0xFF;
        int beforeFg655 = mutableLevel.getMap().getValue(0, 0x65, 5) & 0xFF;
        control.update(0, fixture.sprite());
        int afterInitBg63 = mutableLevel.getMap().getValue(1, 6, 3) & 0xFF;
        int afterInitFg655 = mutableLevel.getMap().getValue(0, 0x65, 5) & 0xFF;

        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_FG_REFRESH);
        advanceCnzPostBossRefresh(events, fixture.frameCount(), 16);
        int afterRefreshFg655 = mutableLevel.getMap().getValue(0, 0x65, 5) & 0xFF;

        assertEquals(beforeBg63, afterInitBg63,
                "Scroll-control init should not alter the BG source cell for CNZ1BGE_FGRefresh");
        assertEquals(beforeFg655, afterInitFg655,
                "Scroll-control init should not pre-fill the FG landing cell; ROM waits for CNZ1BGE_FGRefresh");
        assertNotEquals(beforeFg655, beforeBg63,
                "This guard needs a distinct BG source and FG destination to prove the handoff");
        assertEquals(beforeBg63, afterRefreshFg655,
                "CNZ1BGE_FGRefresh must copy the BG source chunk into Tails' post-boss landing FG cell");
    }

    @Test
    void fgRefreshInvalidatesForegroundBeforeEndSignSpawns() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        var tilemaps = GameServices.level().getTilemapManager();
        tilemaps.setForegroundTilemapDirty(false);
        tilemaps.setBackgroundTilemapDirty(false);
        Sonic3kCNZEvents events = getCnzEvents();
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_FG_REFRESH);

        advanceCnzPostBossRefresh(events, fixture.frameCount(), 16);

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH_2, events.getBackgroundRoutine(),
                "The first delayed CNZ1BGE_FGRefresh pass must complete before Obj_EndSign is allocated");
        assertTrue(tilemaps.isForegroundTilemapDirty(),
                "CNZ1BGE_FGRefresh copies the post-boss room BG layout back to Plane A; "
                        + "the foreground tilemap must redraw before the signpost/results wait");
    }

    @Test
    void arenaChunkClearDoesNotPopulatePostBossLandingCellBeforeFgRefresh() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        MutableLevel mutableLevel = MutableLevel.snapshot(GameServices.level().getCurrentLevel());
        GameServices.level().setLevel(mutableLevel);
        Sonic3kCNZEvents events = getCnzEvents();

        int beforeFg655 = mutableLevel.getMap().getValue(0, 0x65, 5) & 0xFF;
        int beforeBg63 = mutableLevel.getMap().getValue(1, 6, 3) & 0xFF;

        events.setPendingArenaChunkDestruction(0x32D0, 0x0300);

        assertEquals(beforeFg655, mutableLevel.getMap().getValue(0, 0x65, 5) & 0xFF,
                "CNZ1_ScreenEvent arena chunk clears only remove chunk descriptors from the current FG block; "
                        + "they do not populate the post-boss landing cell from the BG source "
                        + "(docs/skdisasm/sonic3k.asm:107340-107414)");
        assertEquals(beforeBg63, mutableLevel.getMap().getValue(1, 6, 3) & 0xFF,
                "The BG source cell used later by CNZ1BGE_FGRefresh must survive arena chunk clears");
    }

    @Test
    void arenaChunkCollisionClearRunsFromScreenEventNotTopObjectWrite() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        MutableLevel mutableLevel = MutableLevel.snapshot(GameServices.level().getCurrentLevel());
        GameServices.level().setLevel(mutableLevel);
        Sonic3kCNZEvents events = getCnzEvents();

        int before = GameServices.level().getChunkDescAt((byte) 0, 0x32D0, 0x0300, false).get();
        assertNotEquals(0, before,
                "Guard setup needs the CNZ miniboss floor chunk to be solid before the queued top impact");

        events.setPendingArenaChunkDestruction(0x32D0, 0x0310);

        assertEquals(before, GameServices.level().getChunkDescAt((byte) 0, 0x32D0, 0x0300, false).get(),
                "Obj_CNZMinibossTop only writes Events_bg+$00/$02 and creates the explosion child; "
                        + "CNZ1_ScreenEvent performs the descriptor clear later "
                        + "(docs/skdisasm/sonic3k.asm:145182-145184,107340-107365)");

        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_BOSS);
        events.update(0, 0);

        assertEquals(0, GameServices.level().getChunkDescAt((byte) 0, 0x32D0, 0x0300, false).get(),
                "The next CNZ screen-event update should consume the queued arena impact and clear "
                        + "the ROM-selected 2x2 chunk descriptors");
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

    @Test
    void scrollControlInitInvalidatesTilemapsForVisibleBossRoomWallsAndBackground() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        var tilemaps = GameServices.level().getTilemapManager();
        tilemaps.setForegroundTilemapDirty(false);
        tilemaps.setBackgroundTilemapDirty(false);

        CnzMinibossScrollControlInstance control = new CnzMinibossScrollControlInstance(
                new ObjectSpawn(0x3200, 0x0280, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        control.setServices(TestEnvironment.objectServices());

        control.update(0, fixture.sprite());

        assertTrue(tilemaps.isForegroundTilemapDirty(),
                "Obj_CNZMinibossScrollInit changes the live foreground wall/tunnel cells, so Plane A must redraw");
        assertTrue(tilemaps.isBackgroundTilemapDirty(),
                "Obj_CNZMinibossScrollInit changes the live background continuation cells, so Plane B must redraw");
    }

    @Test
    void arenaEntryInvalidatesBossRoomTilemapsBeforePlayerLands() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        fixture.sprite().setAir(true);
        GameServices.camera().setX((short) 0x31E0);
        var tilemaps = GameServices.level().getTilemapManager();
        tilemaps.setForegroundTilemapDirty(false);
        tilemaps.setBackgroundTilemapDirty(false);

        Sonic3kCNZEvents events = getCnzEvents();
        events.update(0, 0);

        assertTrue(tilemaps.isForegroundTilemapDirty(),
                "Arena entry must redraw the boss-room foreground walls before the player lands");
        assertTrue(tilemaps.isBackgroundTilemapDirty(),
                "Arena entry must redraw the boss-room background before the player lands");
    }

    @Test
    void arenaEntryDoesNotMutateScrollControlTunnelLayoutCells() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        MutableLevel mutableLevel = MutableLevel.snapshot(GameServices.level().getCurrentLevel());
        GameServices.level().setLevel(mutableLevel);
        byte[] before = mutableLevel.getMap().getData().clone();

        GameServices.camera().setX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);
        getCnzEvents().update(0, 0);

        assertTrue(Arrays.equals(before, mutableLevel.getMap().getData()),
                "Obj_CNZMiniboss arena entry loads PLC/palette and clamps camera, but "
                        + "Obj_CNZMinibossScrollControl owns the live $3180/$0280 tunnel layout copy");
    }

    @Test
    void arenaChunkClearInvalidatesVisibleForegroundTilemap() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        var tilemaps = GameServices.level().getTilemapManager();
        tilemaps.setForegroundTilemapDirty(false);
        tilemaps.setBackgroundTilemapDirty(false);
        Sonic3kCNZEvents events = getCnzEvents();
        events.setPendingArenaChunkDestruction(0x32D0, 0x0310);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_BOSS);

        events.update(0, 0);

        assertTrue(tilemaps.isForegroundTilemapDirty(),
                "CNZ1_ScreenEvent clears visible floor chunk descriptors and must refresh the foreground tilemap");
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

    private void advanceCnzPostBossRefresh(Sonic3kCNZEvents events, int firstFrame, int updates) {
        for (int i = 0; i < updates; i++) {
            events.update(0, firstFrame + i);
        }
    }
}
