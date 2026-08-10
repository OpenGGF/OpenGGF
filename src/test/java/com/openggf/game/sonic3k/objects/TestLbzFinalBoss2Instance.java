package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.audio.AudioManager;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.S3kTransitionEventBridge;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2RomData;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RequiresRom(SonicGame.SONIC_3K)
class TestLbzFinalBoss2Instance {
    private static final int OBJECT_ID = 0xCC;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
    }

    @AfterEach
    void resetObjectCameraBounds() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @Test
    void initWaitAndFallAllocateNativeGraphWithExactCollisionOwners() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);

        boss.update(0, null);

        assertEquals(0x02, boss.getRoutineForTest());
        assertEquals(0x59, boss.getTimerForTest());
        assertEquals(8, boss.getCollisionProperty());
        assertEquals(0, boss.getCollisionFlags());
        assertEquals(5, boss.getMappingFrameForTest());
        assertEquals(services.cameraX() + 0xA0, boss.getCentreX());
        assertEquals(services.cameraY() - 0x50, boss.getCentreY());
        assertTrue(boss.hasPublishedDeathPlaneDisable());
        assertEquals(List.of(LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD),
                boss.getChildOrderForTest());

        int frame = advanceUntil(boss, null, 0,
                () -> boss.getRoutineForTest() == 0x06, 0x100);
        assertEquals(8, boss.getMappingFrameForTest());
        assertEquals(List.of(
                        LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD,
                        LbzFinalBoss2Instance.ChildKind.ARM_GRAPH,
                        LbzFinalBoss2Instance.ChildKind.ARM_ATTACHMENT,
                        LbzFinalBoss2Instance.ChildKind.ARM_VISUAL,
                        LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION,
                        LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT,
                        LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT,
                        LbzFinalBoss2Instance.ChildKind.ARM_JOINT,
                        LbzFinalBoss2Instance.ChildKind.GRAB),
                boss.getChildOrderForTest());

        LbzFinalBoss2RomData rom = new LbzFinalBoss2RomData(services.romReader());
        LbzFinalBoss2Instance.BossChild controller = firstChild(
                boss, LbzFinalBoss2Instance.ChildKind.ARM_GRAPH);
        assertArrayEquals(rom.childOffset(
                        Sonic3kConstants.LBZ_FINAL_BOSS_2_INITIAL_CHILD_TABLE_ADDR, 0),
                new int[]{controller.xOffsetForTest(), 0x24},
                "ChildObjDat_75122 supplies +$14,+$24 before loc_749EC rewrites dy=-6");
        assertEquals(-6, controller.yOffsetForTest());
        assertEquals(0, controller.getCollisionFlags());
        assertEquals(0, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION).getCollisionFlags());
        for (Object segment : boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT)) {
            assertEquals(0, assertInstanceOf(
                    LbzFinalBoss2Instance.BossChild.class, segment).getCollisionFlags());
        }
        assertEquals(0, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_JOINT).getCollisionFlags());

        frame = advanceUntil(boss, null, frame,
                () -> boss.getRoutineForTest() == 0x08, 0x200);
        assertTrue(frame > 0);
        assertTrue(boss.isArtTileHighForTest());
        assertEquals(0x0F, boss.getCollisionFlags());
        assertEquals(0xAD, boss.getArmCollisionForTest());
        assertEquals(0x9A, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION).getCollisionFlags());
        assertEquals(0x9C, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_UPPER_COLLISION).getCollisionFlags());
        assertEquals(0, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_JOINT).getCollisionFlags());
    }

    @Test
    void nativePriorityBucketsAndInitialVisibilityComeFromObjectData() {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, null);

        assertEquals(5, boss.getPriorityBucket());
        assertEquals(5, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD).getPriorityBucket());
        assertEquals(3, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_GRAPH).getPriorityBucket());
        assertEquals(4, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_ATTACHMENT).getPriorityBucket());
        assertEquals(6, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_VISUAL).getPriorityBucket());
        assertEquals(6, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION).getPriorityBucket());
        List<Object> segments = boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT);
        assertEquals(List.of(1, 3), segments.stream()
                .map(segment -> assertInstanceOf(
                        LbzFinalBoss2Instance.BossChild.class, segment).getPriorityBucket())
                .toList(), "native segment subtypes 0/2 own priority $80/$180");
        assertEquals(3, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_JOINT).getPriorityBucket());
        assertEquals(0, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.GRAB).getPriorityBucket());

        frame = advanceUntil(boss, null, frame,
                () -> !boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ARM_UPPER_COLLISION).isEmpty(),
                0x200);
        assertTrue(frame > 0);
        assertEquals(0, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_UPPER_COLLISION).getPriorityBucket(),
                "CreateChild1_Normal does not inherit SetUp_ObjAttributes priority");
    }

    @Test
    void outerUsesAdjustedFlipAndEvenVIntCadenceWhileLandingAndGrabNeverDraw()
            throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, null);
        LbzFinalBoss2Instance.ArmOuterCollisionChild outer = assertInstanceOf(
                LbzFinalBoss2Instance.ArmOuterCollisionChild.class,
                boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION).get(0));
        LbzFinalBoss2Instance.BossChild landing = firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_UPPER_COLLISION);
        LbzFinalBoss2Instance.BossChild grab = firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.GRAB);

        clearInvocations(services.bigArmRenderer);
        setBooleanField(boss, "renderXFlip", false);
        outer.update(2, null);
        outer.appendRenderCommands(new ArrayList<>());
        verify(services.bigArmRenderer, times(1)).drawFrameIndex(
                0x0C, outer.getX(), outer.getY(), false, false, 0);

        clearInvocations(services.bigArmRenderer);
        outer.update(3, null);
        outer.appendRenderCommands(new ArrayList<>());
        verify(services.bigArmRenderer, never()).drawFrameIndex(
                0x0C, outer.getX(), outer.getY(), false, false, 0);

        setBooleanField(boss, "renderXFlip", true);
        outer.update(4, null);
        outer.appendRenderCommands(new ArrayList<>());
        verify(services.bigArmRenderer, times(1)).drawFrameIndex(
                0x0C, outer.getX(), outer.getY(), true, false, 0);

        clearInvocations(services.bigArmRenderer);
        landing.appendRenderCommands(new ArrayList<>());
        grab.appendRenderCommands(new ArrayList<>());
        verify(services.bigArmRenderer, never()).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void robotnikHeadInitDefersRomRawAnimationUntilNextOwnEntry() {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        boss.update(0, null);
        LbzFinalBoss2Instance.RobotnikHead4Child head = assertInstanceOf(
                LbzFinalBoss2Instance.RobotnikHead4Child.class,
                boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD).get(0));

        assertEquals(0, head.mappingFrameForTest());
        assertEquals(0, head.rawCursorForTest());
        assertEquals(0, head.animationTimerForTest());

        boss.update(1, null);
        assertEquals(0, head.mappingFrameForTest(),
                "Obj_RobotnikHead3Init returns without Animate_Raw");
        assertEquals(0, head.rawCursorForTest());
        assertEquals(0, head.animationTimerForTest());

        boss.update(2, null);
        assertEquals(1, head.mappingFrameForTest());
        assertEquals(1, head.rawCursorForTest());
        assertEquals(0x0F, head.animationTimerForTest(),
                "Knuckles reads AniRaw_EggRoboHead from ROM");
    }

    @Test
    void floorExplosionWaitExpiryDefersRawCursorUntilFollowingOwnEntry() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        boss.update(0, null);

        Constructor<LbzFinalBoss2Instance.EscapeFloorChild> floorConstructor =
                LbzFinalBoss2Instance.EscapeFloorChild.class.getDeclaredConstructor(
                        LbzFinalBoss2Instance.class, int.class, int.class);
        floorConstructor.setAccessible(true);
        LbzFinalBoss2Instance.EscapeFloorChild floor = floorConstructor.newInstance(
                boss, 0, 0);
        floor.setServices(services);
        Constructor<LbzFinalBoss2Instance.EscapeFloorExplosionChild> explosionConstructor =
                LbzFinalBoss2Instance.EscapeFloorExplosionChild.class.getDeclaredConstructor(
                        LbzFinalBoss2Instance.class,
                        LbzFinalBoss2Instance.EscapeFloorChild.class,
                        int.class, int.class, int.class);
        explosionConstructor.setAccessible(true);
        LbzFinalBoss2Instance.EscapeFloorExplosionChild explosion =
                explosionConstructor.newInstance(boss, floor, 0, 0, 0);
        explosion.setServices(services);

        explosion.update(0, null);
        for (int ownEntry = 1; ownEntry <= 25; ownEntry++) {
            explosion.update(ownEntry, null);
        }
        assertTrue(explosion.isAnimatingForTest());
        assertEquals(0, explosion.rawCursorForTest(),
                "wait expiry changes callback only");
        assertEquals(0, explosion.mappingFrameForTest());
        assertEquals(0, explosion.rawTimerForTest());

        explosion.update(26, null);
        assertEquals(2, explosion.rawCursorForTest());
        assertEquals(0, explosion.mappingFrameForTest());
        assertEquals(1, explosion.rawTimerForTest());
    }

    @Test
    void firstFourBobCyclesChooseRoutine0AAndFifthChooses0CWithoutExtraRng() {
        HarnessServices services = new HarnessServices();
        services.rng().setSeed(0x12345678L);
        GameRng oracle = new GameRng(GameRng.Flavour.S3K, 0x12345678L);
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, null);

        for (int cycle = 1; cycle <= 4; cycle++) {
            frame = advanceUntil(boss, null, frame,
                    () -> boss.getRoutineForTest() != 0x08, 0x200);
            assertEquals(0x0A, boss.getRoutineForTest(), "cycle " + cycle);
            assertEquals(cycle, boss.getRandomCounterForTest());
            oracle.nextRaw();
            frame = advanceUntil(boss, null, frame,
                    () -> boss.getRoutineForTest() == 0x08, 0x200);
        }

        frame = advanceUntil(boss, null, frame,
                () -> boss.getRoutineForTest() != 0x08, 0x200);
        assertTrue(frame > 0);
        assertEquals(0x0C, boss.getRoutineForTest());
        assertEquals(0, boss.getRandomCounterForTest(),
                "loc_743AE clears $39 after testing the old bit 2");
        oracle.nextRaw();
        assertEquals(oracle.getSeed(), services.rng().getSeed(),
                "loc_74F82 consumes one Random_Number call, not an extra fitted draw");
    }

    @Test
    void routine0AUsesRomMotionTablesAndStrictCameraBounceFallthroughs() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, null);
        frame = advanceUntil(boss, null, frame,
                () -> boss.getRoutineForTest() == 0x0A, 0x200);

        setIntField(boss, "waitTimer", 0x40);
        setIntField(boss, "xVel", 0);
        setIntField(boss, "yVel", 0);
        setIntField(boss, "randomAction", 6);
        int yBefore = boss.getCentreY();
        boss.update(++frame, null);
        assertEquals(yBefore, boss.getCentreY(), "word-table offset 6 is a no-op");
        assertEquals(0, boss.getYVelocityForTest());

        setIntField(boss, "randomAction", 8);
        yBefore = boss.getCentreY();
        boss.update(++frame, null);
        assertEquals((yBefore - 4) & 0xFFFF, boss.getCentreY(),
                "hit override offset 8 subtracts four from y_pos");

        setIntField(boss, "randomAction", 2);
        setIntField(boss, "yVel", 0);
        boss.update(++frame, null);
        assertEquals(-4, boss.getYVelocityForTest());

        setIntField(boss, "randomAction", 4);
        setIntField(boss, "yVel", -8);
        setIntField(boss, "angle", 8);
        boss.update(++frame, null);
        assertEquals(0, boss.getYVelocityForTest());
        assertEquals(2, intField(boss, "angle"),
                "offset 4 writes $3C=2 only when its sum becomes zero");
    }

    @Test
    void dropLandAndRiseFollowNativeRoutineBoundaries() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, null);

        for (int cycle = 0; cycle < 4; cycle++) {
            frame = advanceUntil(boss, null, frame,
                    () -> boss.getRoutineForTest() == 0x0A, 0x300);
            frame = advanceUntil(boss, null, frame,
                    () -> boss.getRoutineForTest() == 0x08, 0x300);
        }
        frame = advanceUntil(boss, null, frame,
                () -> boss.getRoutineForTest() == 0x0C, 0x300);

        setIntField(boss, "waitTimer", 0);
        boss.update(++frame, null);
        assertEquals(0x0E, boss.getRoutineForTest());
        assertEquals(0x1F, boss.getTimerForTest());
        assertEquals(0, boss.getXVelocityForTest(),
                "loc_74428 clears x_vel on the bounce-expiry entry");

        int callbackX = boss.getCentreX();
        int callbackY = boss.getCentreY();
        for (int entry = 0; entry < 32; entry++) {
            boss.update(++frame, null);
        }
        assertEquals(0x10, boss.getRoutineForTest());
        assertEquals(callbackX, boss.getCentreX());
        assertEquals(callbackY, boss.getCentreY(),
                "loc_74448 changes routine/bit 3 without running light gravity");
        assertTrue((intField(boss, "flags") & (1 << 3)) != 0);

        setIntField(boss, "y", services.cameraY() + 0xC0);
        setIntField(boss, "ySub", 0);
        setIntField(boss, "yVel", 0);
        boss.update(++frame, null);
        assertEquals(0x10, boss.getRoutineForTest(),
                "loc_74456 uses strict unsigned y > Camera_Y_pos_copy+$C0");
        setIntField(boss, "y", services.cameraY() + 0xC1);
        boss.update(++frame, null);
        assertEquals(0x12, boss.getRoutineForTest());
        assertTrue((intField(boss, "flags") & (1 << 2)) != 0);

        setIntField(boss, "y", services.cameraY() + 0xE1);
        setIntField(boss, "ySub", 0);
        setIntField(boss, "yVel", 0);
        boss.update(++frame, null);
        assertEquals(0x14, boss.getRoutineForTest(),
                "loc_7447A subtracts $80 and tests the moved position on one entry");

        setIntField(boss, "y", services.cameraY() + 0xD0);
        setIntField(boss, "ySub", 0);
        setIntField(boss, "yVel", -0x40);
        boss.update(++frame, null);
        assertEquals(0x16, boss.getRoutineForTest());
        assertEquals(services.cameraY() + 0xD0, boss.getCentreY());
        assertEquals(0, boss.getYVelocityForTest());
        assertEquals(0x1F, boss.getTimerForTest());

        for (int entry = 0; entry < 32; entry++) {
            boss.update(++frame, null);
        }
        assertEquals(0x18, boss.getRoutineForTest());
        assertEquals(0x300, Math.abs(boss.getXVelocityForTest()),
                "loc_744EA falls through to the random bounded-bounce setup");

        setIntField(boss, "waitTimer", 0);
        boss.update(++frame, null);
        assertEquals(0x1A, boss.getRoutineForTest());
        assertEquals(0x1F, boss.getTimerForTest());

        for (int entry = 0; entry < 32; entry++) {
            boss.update(++frame, null);
        }
        assertEquals(0x1C, boss.getRoutineForTest());
        assertEquals(-0x400, boss.getYVelocityForTest());
        assertEquals(0, intField(boss, "flags") & (1 << 3));

        setIntField(boss, "y", services.cameraY() - 0x60);
        setIntField(boss, "ySub", 0);
        boss.update(++frame, null);
        assertEquals(0x08, boss.getRoutineForTest());
        assertEquals(0x7F, boss.getTimerForTest(),
                "loc_74558 rejoins the complete native bob wait");
    }

    @Test
    void controllerActivationDefersAngleAndBit1FreezesPosition() throws Exception {
        HarnessServices services = new HarnessServices();
        TestablePlayableSprite player = new TestablePlayableSprite(
                "knuckles", (short) 0x4400, (short) 0x0800);
        services.withPlayer(player);
        LbzFinalBoss2Instance boss = newBoss(services);
        boss.update(0, player);
        int frame = advanceUntil(boss, player, 0,
                () -> !boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ARM_GRAPH).isEmpty(), 0x100);
        LbzFinalBoss2Instance.ArmControllerChild controller = assertInstanceOf(
                LbzFinalBoss2Instance.ArmControllerChild.class,
                boss.childrenOfKindForTest(LbzFinalBoss2Instance.ChildKind.ARM_GRAPH).get(0));
        player.setCentreYPreserveSubpixel((short) (controller.getY() - 0x40));

        int angleBefore = intField(controller, "angle");
        controller.update(++frame, player);
        assertEquals(angleBefore, intField(controller, "angle"),
                "routine 2 is circular lookup only before activation");

        setBooleanField(boss, "artTileHigh", true);
        controller.update(++frame, player);
        assertEquals(0xAD, controller.getCollisionFlags());
        assertEquals(angleBefore, intField(controller, "angle"),
                "activation publishes routine 4 but still looks up the old angle");

        controller.update(++frame, player);
        assertFalse(angleBefore == intField(controller, "angle"),
                "angle adjustment begins on the following routine-4 dispatch");

        setIntField(boss, "flags", intField(boss, "flags") | 2);
        int xBefore = controller.getX();
        int yBefore = controller.getY();
        boss.setCentreX(boss.getCentreX() + 0x20);
        controller.update(++frame, player);
        assertEquals(xBefore, controller.getX());
        assertEquals(yBefore, controller.getY(),
                "root $38 bit 1 returns before angle and circular position work");
    }

    @Test
    void controllerFlipAndImmediateParentRefreshFollowNativeEntryOrder() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        LbzFinalBoss2Instance.ArmControllerChild controller =
                newArmController(boss, services, 0x14, 0x24);
        setObjectField(boss, "armController", controller);

        int spawnX = controller.getX();
        int spawnY = controller.getY();
        boss.setCentreX(boss.getCentreX() + 0x20);
        setBooleanField(boss, "renderXFlip", true);

        controller.update(1, null);
        assertEquals(spawnX, controller.getX(),
                "loc_749EC rewrites offsets and creates children without refreshing");
        assertEquals(spawnY, controller.getY());
        assertFalse((boolean) objectField(controller, "hFlip"));

        controller.update(2, null);
        assertFalse((boolean) objectField(controller, "hFlip"),
                "routine 2 never latches the root flip");
        setBooleanField(boss, "artTileHigh", true);
        controller.update(3, null);
        assertFalse((boolean) objectField(controller, "hFlip"),
                "the activation entry uses the previously latched flip");

        controller.update(4, null);
        assertTrue((boolean) objectField(controller, "hFlip"),
                "the first routine-4 entry latches Change_FlipXUseParent");

        setBooleanField(boss, "renderXFlip", false);
        LbzFinalBoss2Instance.ArmSegmentChild segment = assertInstanceOf(
                LbzFinalBoss2Instance.ArmSegmentChild.class,
                boss.childrenOfKindForTest(LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT).get(0));
        int segmentDx = intField(segment, "dx");
        segment.update(5, null);
        assertTrue((boolean) objectField(segment, "hFlip"),
                "adjusted children copy their immediate controller, not the root");
        assertEquals((controller.getX() - segmentDx) & 0xFFFF, segment.getX());
    }

    @Test
    void segmentFirstRawStepUsesCursorPlusOneWithoutGrabMask() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        LbzFinalBoss2Instance.ArmControllerChild controller =
                newArmController(boss, services, 0, 0);
        setIntField(controller, "currentX", 0x4500);
        setIntField(controller, "currentY", 0x0700);

        for (int subtype = 0; subtype < 2; subtype++) {
            LbzFinalBoss2Instance.ArmSegmentChild segment =
                    newArmSegment(boss, services, controller, subtype, 0x10, 0);
            segment.update(1, null);
            assertEquals(1, intField(segment, "animationIndex"));
            assertEquals(9, intField(segment, "animationTimer"));
            assertEquals(subtype == 0 ? 4 : 8, segment.mappingFrameForTest(),
                    "cursor 1 must select script byte 2 through 1(a1,d0.w)");
        }

        setBooleanField(boss, "grabActive", true);
        for (int subtype = 0; subtype < 2; subtype++) {
            LbzFinalBoss2Instance.ArmSegmentChild segment =
                    newArmSegment(boss, services, controller, subtype, 0x10, 0);
            segment.update(2, null);
            assertEquals(1, intField(segment, "animationIndex"));
            assertEquals(9, intField(segment, "animationTimer"));
            assertEquals(subtype == 0 ? 7 : 0x0B, segment.mappingFrameForTest(),
                    "the same-entry grab callback overrides mapping only");
        }
    }

    @Test
    void segmentsHoldOffsetAndAnimationAtNativeCallbackBoundaries() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        LbzFinalBoss2Instance.ArmControllerChild controller =
                newArmController(boss, services, 0, 0);
        setIntField(controller, "currentX", 0x4500);
        setIntField(controller, "currentY", 0x0700);
        setBooleanField(controller, "hFlip", false);
        LbzFinalBoss2Instance.ArmSegmentChild segment0 =
                newArmSegment(boss, services, controller, 0, 0x10, 0);
        LbzFinalBoss2Instance.ArmSegmentChild segment1 =
                newArmSegment(boss, services, controller, 1, 0x10, 0);

        setBooleanField(boss, "grabActive", true);
        segment0.update(1, null);
        segment1.update(1, null);

        assertEquals(1, intField(segment0, "animationIndex"));
        assertEquals(1, intField(segment1, "animationIndex"));
        assertEquals(9, intField(segment0, "animationTimer"));
        assertEquals(9, intField(segment1, "animationTimer"));
        assertEquals(7, segment0.mappingFrameForTest());
        assertEquals(0x0B, segment1.mappingFrameForTest());
        assertEquals(0x18, intField(segment1, "dx"),
                "the first held entry stores native subtype-2 child_dx + 8");
        assertEquals(0x4510, segment1.getX(),
                "the first held entry refreshes before changing stored child_dx");

        segment1.update(2, null);
        assertEquals(0x4518, segment1.getX());
        assertEquals(1, intField(segment1, "animationIndex"));
        assertEquals(9, intField(segment1, "animationTimer"),
                "loc_74B76 never runs Animate_Raw while held");

        setBooleanField(boss, "grabActive", false);
        segment1.update(3, null);
        assertEquals(0x4518, segment1.getX(),
                "first release refreshes once with the still-adjusted offset");
        assertEquals(0x10, intField(segment1, "dx"));
        assertEquals(1, intField(segment1, "animationIndex"));
        assertEquals(9, intField(segment1, "animationTimer"),
                "release only switches callbacks and restores child_dx");

        segment1.update(4, null);
        assertEquals(0x4510, segment1.getX());
        assertEquals(8, intField(segment1, "animationTimer"),
                "ordinary adjusted refresh/animation resumes one entry later");
    }

    @Test
    void grabReleaseCooldownFreezesAndReacquiresAfterSwitchOnlyExpiry() throws Exception {
        for (boolean finalFlip : List.of(false, true)) {
            HarnessServices services = new HarnessServices();
            TestablePlayableSprite player = new TestablePlayableSprite(
                    "knuckles", (short) 0, (short) 0);
            services.withPlayer(player);
            LbzFinalBoss2Instance boss = newBoss(services);
            LbzFinalBoss2Instance.ArmControllerChild controller =
                    newArmController(boss, services, 0, 0);
            setIntField(controller, "currentX", 0x4500);
            setIntField(controller, "currentY", 0x0700);
            setBooleanField(controller, "hFlip", false);
            LbzFinalBoss2Instance.GrabOwnerChild grab =
                    newGrabOwner(boss, services, controller, 0x10, 0);
            setObjectField(grab, "grabbedPlayer", player);
            setBooleanField(boss, "grabActive", true);

            grab.update(1, player);
            int frozenX = grab.getX();
            int frozenY = grab.getY();

            setIntField(controller, "currentX", 0x4600);
            setIntField(controller, "currentY", 0x0710);
            setBooleanField(controller, "hFlip", finalFlip);
            int reacquireX = 0x4600 + (finalFlip ? -0x10 : 0x10);
            player.setCentreX((short) reacquireX);
            player.setCentreYPreserveSubpixel((short) 0x0710);
            setBooleanField(boss, "grabActive", false);

            grab.update(2, player);
            assertEquals(0x3F, intField(grab, "releaseCooldown"),
                    "loc_74CF8 seeds $40 and falls through to the first decrement");
            assertEquals(frozenX, grab.getX());
            assertEquals(frozenY, grab.getY());

            for (int entry = 0; entry < 0x3F; entry++) {
                grab.update(3 + entry, player);
                assertEquals(frozenX, grab.getX());
                assertEquals(frozenY, grab.getY(),
                        "loc_74D04 freezes position for the entire cooldown");
            }
            assertEquals(0, intField(grab, "releaseCooldown"));

            grab.update(0x43, player);
            assertEquals(0xFFFF, intField(grab, "releaseCooldown"));
            assertFalse(boss.isGrabActiveForTest(),
                    "the 0->$FFFF entry switches callback without range acquisition");
            assertEquals(frozenX, grab.getX());

            grab.update(0x44, player);
            assertTrue(boss.isGrabActiveForTest());
            assertEquals(reacquireX & 0xFFFF, grab.getX(),
                    "the following normal entry refreshes with the controller's current flip");
        }
    }

    @Test
    void grabOwnerNaturallyAcquiresKnucklesInHalfOpenRange() {
        HarnessServices services = new HarnessServices();
        TestablePlayableSprite player = new TestablePlayableSprite(
                "knuckles", (short) 0, (short) 0);
        services.withPlayer(player);
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, player);
        frame = advanceUntil(boss, player, frame,
                () -> boss.getRoutineForTest() == 0x0A, 0x200);
        LbzFinalBoss2Instance.GrabOwnerChild grab = assertInstanceOf(
                LbzFinalBoss2Instance.GrabOwnerChild.class,
                boss.childrenOfKindForTest(LbzFinalBoss2Instance.ChildKind.GRAB).get(0));

        player.setCentreX((short) (grab.getX() + 0x20));
        player.setCentreYPreserveSubpixel((short) grab.getY());
        grab.update(++frame, player);
        assertFalse(boss.isGrabActiveForTest(), "upper X edge is exclusive");

        player.setCentreX((short) (grab.getX() - 0x10));
        player.setCentreYPreserveSubpixel((short) (grab.getY() - 0x10));
        player.setSubpixelRaw(0x1357, 0x2468);
        grab.update(++frame, player);
        assertTrue(boss.isGrabActiveForTest(), "lower X/Y edges are inclusive");
        assertEquals(0x1E, boss.getRoutineForTest());
        assertEquals(0x40, boss.getTimerForTest());
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(grab.getX(), player.getCentreX(),
                "loc_74C8C falls through to loc_74CCC and snaps on acquisition");
        assertEquals(grab.getY(), player.getCentreY());
        assertEquals(0x1357, player.getXSubpixelRaw());
        assertEquals(0x2468, player.getYSubpixelRaw(),
                "word x_pos/y_pos writes preserve both player low words");
    }

    @Test
    void grabSideSelectionUsesRootAgainstCameraA0() throws Exception {
        HarnessServices rightServices = new HarnessServices();
        TestablePlayableSprite rightPlayer = new TestablePlayableSprite(
                "knuckles", (short) 0, (short) 0);
        rightServices.withPlayer(rightPlayer);
        LbzFinalBoss2Instance rightBoss = newBoss(rightServices);
        int rightFrame = acquireGrab(rightBoss, rightServices, rightPlayer);
        rightFrame = enterGrabAlignWait(rightBoss, rightPlayer, rightFrame, rightServices);
        rightBoss.setCentreX(rightServices.cameraX() + 0xA1);
        setIntField(rightBoss, "waitTimer", 0);
        rightBoss.update(++rightFrame, rightPlayer);
        assertEquals(0x24, rightBoss.getRoutineForTest());
        assertFalse(rightBoss.isRenderXFlipForTest());
        assertEquals(rightServices.cameraX() + 0xE0, rightBoss.getCentreX(),
                "strict root x > Camera_X+$A0 selects the right-side target");

        HarnessServices leftServices = new HarnessServices();
        TestablePlayableSprite leftPlayer = new TestablePlayableSprite(
                "knuckles", (short) 0, (short) 0);
        leftServices.withPlayer(leftPlayer);
        LbzFinalBoss2Instance leftBoss = newBoss(leftServices);
        int leftFrame = acquireGrab(leftBoss, leftServices, leftPlayer);
        leftFrame = enterGrabAlignWait(leftBoss, leftPlayer, leftFrame, leftServices);
        leftBoss.setCentreX(leftServices.cameraX() + 0xA0);
        setIntField(leftBoss, "waitTimer", 0);
        leftBoss.update(++leftFrame, leftPlayer);
        assertEquals(0x24, leftBoss.getRoutineForTest());
        assertTrue(leftBoss.isRenderXFlipForTest());
        assertEquals(leftServices.cameraX() + 0x60, leftBoss.getCentreX(),
                "equality follows the native flipped/left-side branch");
    }

    @Test
    void throwUsesHurtForVulnerablePlayerAndReboundForInvinciblePlayer() throws Exception {
        HarnessServices hurtServices = new HarnessServices();
        TestablePlayableSprite hurtPlayer = new TestablePlayableSprite(
                "knuckles", (short) 0, (short) 0);
        hurtServices.withPlayer(hurtPlayer);
        LbzFinalBoss2Instance hurtBoss = newBoss(hurtServices);
        int hurtFrame = acquireGrab(hurtBoss, hurtServices, hurtPlayer);
        hurtFrame = advanceGrabToThrowWait(hurtBoss, hurtPlayer, hurtFrame, hurtServices);
        setIntField(hurtBoss, "waitTimer", 0);
        hurtBoss.update(++hurtFrame, hurtPlayer);

        assertEquals(0x2A, hurtBoss.getRoutineForTest());
        assertFalse(hurtBoss.isGrabActiveForTest());
        assertTrue(hurtPlayer.isHurt(),
                "non-invincible loc_74664 must enter the standard HurtCharacter path");
        assertEquals(-0x600, hurtBoss.getYVelocityForTest());

        HarnessServices reboundServices = new HarnessServices();
        TestablePlayableSprite reboundPlayer = new TestablePlayableSprite(
                "knuckles", (short) 0, (short) 0);
        reboundPlayer.setInvincibleFrames(1);
        reboundServices.withPlayer(reboundPlayer);
        LbzFinalBoss2Instance reboundBoss = newBoss(reboundServices);
        int reboundFrame = acquireGrab(reboundBoss, reboundServices, reboundPlayer);
        reboundFrame = advanceGrabToThrowWait(
                reboundBoss, reboundPlayer, reboundFrame, reboundServices);
        setIntField(reboundBoss, "waitTimer", 0);
        reboundBoss.update(++reboundFrame, reboundPlayer);

        assertEquals(0x2A, reboundBoss.getRoutineForTest());
        assertFalse(reboundPlayer.isObjectControlled());
        assertEquals(-reboundBoss.getXVelocityForTest(), reboundPlayer.getXSpeed());
        assertEquals(-0x400, reboundPlayer.getYSpeed());
        assertTrue(reboundPlayer.getAir());
        assertFalse(reboundPlayer.isJumping());
        assertFalse(reboundPlayer.getSpindash());
        assertEquals(Sonic3kAnimationIds.ROLL.id(), reboundPlayer.getAnimationId());
        assertEquals(List.of(Sonic3kSfx.SPRING.id), reboundServices.sfx.stream()
                        .filter(id -> id == Sonic3kSfx.SPRING.id).toList(),
                "the invincible release owns exactly one spring SFX");
    }

    @Test
    void fightAndHeldWritesPreserveArbitraryLowWords() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, null);
        frame = advanceUntil(boss, null, frame,
                () -> boss.getRoutineForTest() == 0x0A, 0x200);

        setIntField(boss, "waitTimer", 0x40);
        setIntField(boss, "randomAction", 0);
        setIntField(boss, "xSub", 0x1234);
        setIntField(boss, "ySub", 0xFEDC);
        setIntField(boss, "xVel", 0x0101);
        setIntField(boss, "yVel", -0x0101);
        int oldXLong = (boss.getCentreX() << 16) | 0x1234;
        int oldYLong = (boss.getCentreY() << 16) | 0xFEDC;

        boss.update(++frame, null);

        int expectedX = oldXLong + (0x0101 << 8);
        int expectedY = oldYLong + (-0x0101 << 8);
        assertEquals((expectedX >>> 16) & 0xFFFF, boss.getCentreX());
        assertEquals(expectedX & 0xFFFF, intField(boss, "xSub"));
        assertEquals((expectedY >>> 16) & 0xFFFF, boss.getCentreY());
        assertEquals(expectedY & 0xFFFF, intField(boss, "ySub"));

        boss.setCentreX(0x4567);
        boss.setCentreY(0x0678);
        assertEquals(expectedX & 0xFFFF, intField(boss, "xSub"));
        assertEquals(expectedY & 0xFFFF, intField(boss, "ySub"),
                "native word-coordinate writes must preserve root low words");
    }

    @Test
    void cameraCopyFightStatesUseAppliedOffsetRatherThanBaseCamera() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, null);
        int baseY = services.cameraY();

        publishAppliedShake(services.state, 5);
        setIntField(boss, "routine", 0x10);
        setIntField(boss, "y", baseY + 0xC1);
        setIntField(boss, "yVel", 0);
        boss.update(++frame, null);
        assertEquals(0x10, boss.getRoutineForTest(),
                "routine $10 must not use base Camera_Y_pos");
        publishAppliedShake(services.state, 0);
        setIntField(boss, "routine", 0x10);
        setIntField(boss, "y", baseY + 0xC1);
        setIntField(boss, "yVel", 0);
        boss.update(++frame, null);
        assertEquals(0x12, boss.getRoutineForTest());

        publishAppliedShake(services.state, 5);
        setIntField(boss, "routine", 0x12);
        setIntField(boss, "y", baseY + 0xE3);
        setIntField(boss, "yVel", -0x100);
        boss.update(++frame, null);
        assertEquals(0x14, boss.getRoutineForTest());
        publishAppliedShake(services.state, 0);
        setIntField(boss, "routine", 0x12);
        setIntField(boss, "y", baseY + 0xE3);
        setIntField(boss, "yVel", -0x100);
        boss.update(++frame, null);
        assertEquals(0x12, boss.getRoutineForTest());

        publishAppliedShake(services.state, 5);
        setIntField(boss, "routine", 0x14);
        setIntField(boss, "y", baseY + 0xD1);
        setIntField(boss, "yVel", -0x40);
        boss.update(++frame, null);
        assertEquals(0x14, boss.getRoutineForTest());
        publishAppliedShake(services.state, 0);
        setIntField(boss, "routine", 0x14);
        setIntField(boss, "y", baseY + 0xD1);
        setIntField(boss, "yVel", -0x40);
        boss.update(++frame, null);
        assertEquals(0x16, boss.getRoutineForTest());

        publishAppliedShake(services.state, 5);
        setIntField(boss, "routine", 0x24);
        setIntField(boss, "y", baseY + 0x88);
        boss.update(++frame, null);
        assertEquals(0x24, boss.getRoutineForTest());
        publishAppliedShake(services.state, 0);
        setIntField(boss, "routine", 0x24);
        setIntField(boss, "y", baseY + 0x88);
        boss.update(++frame, null);
        assertEquals(0x26, boss.getRoutineForTest());

        publishAppliedShake(services.state, 5);
        setIntField(boss, "routine", 0x06);
        setIntField(boss, "y", baseY + 0x121);
        setIntField(boss, "yVel", 0);
        boss.update(++frame, null);
        assertEquals(0x08, boss.getRoutineForTest(),
                "routine $06 retains explicit base Camera_Y_pos ownership");
    }

    @Test
    void routine2AUsesMoveSpriteGravity38() {
        HarnessServices services = new HarnessServices();
        TestablePlayableSprite player = new TestablePlayableSprite(
                "knuckles", (short) 0, (short) 0);
        player.setInvincibleFrames(1);
        services.withPlayer(player);
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, player);
        frame = advanceUntil(boss, player, frame,
                () -> boss.getRoutineForTest() == 0x0A, 0x200);
        LbzFinalBoss2Instance.GrabOwnerChild grab = assertInstanceOf(
                LbzFinalBoss2Instance.GrabOwnerChild.class,
                boss.childrenOfKindForTest(LbzFinalBoss2Instance.ChildKind.GRAB).get(0));
        player.setCentreX((short) grab.getX());
        player.setCentreYPreserveSubpixel((short) grab.getY());
        grab.update(++frame, player);

        frame = advanceUntil(boss, player, frame,
                () -> boss.getRoutineForTest() == 0x2A, 0x400);
        assertEquals(-0x600, boss.getYVelocityForTest());
        int beforeY = boss.getCentreY();

        boss.update(++frame, player);

        assertEquals((beforeY - 6) & 0xFFFF, boss.getCentreY());
        assertEquals(-0x5C8, boss.getYVelocityForTest());
    }

    @Test
    void nonFinalHitClearsCollisionAndPublishesExactSixColourFlash() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, null);

        boss.onPlayerAttack(null, null);
        boss.update(++frame, null);

        assertEquals(7, boss.getCollisionProperty());
        assertEquals(0, boss.getCollisionFlags());
        assertEquals(0, boss.getArmCollisionForTest());
        assertEquals(0x3B, boss.getHitFlashTimerForTest(),
                "sub_74FD2 writes $3C and decrements it on the hit entry");
        LbzFinalBoss2RomData data = new LbzFinalBoss2RomData(services.romReader());
        assertPaletteWords(services.level.getPalette(1), data.flashPaletteIndices(),
                data.flashPaletteWords(true));
    }

    @Test
    void flashExpiryRestoresOnlyRootAndControllerCollision() {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, null);
        boss.onPlayerAttack(null, null);
        boss.update(++frame, null);

        while (boss.getHitFlashTimerForTest() > 0) {
            boss.update(++frame, null);
        }

        assertEquals(0x0F, boss.getCollisionFlags());
        assertEquals(0xAD, boss.getArmCollisionForTest());
        assertEquals(0x9A, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION).getCollisionFlags());
        for (Object segment : boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT)) {
            assertEquals(0, assertInstanceOf(
                    LbzFinalBoss2Instance.BossChild.class, segment).getCollisionFlags());
        }
        assertEquals(0, firstChild(boss,
                LbzFinalBoss2Instance.ChildKind.ARM_JOINT).getCollisionFlags());
    }

    @Test
    void eighthHitRunsShippedFixBugsZeroBranch() {
        HarnessServices services = new HarnessServices();
        TestablePlayableSprite player = new TestablePlayableSprite(
                "knuckles", (short) 0x4400, (short) 0x700);
        services.withPlayer(player);
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, player);
        for (int hit = 1; hit < 8; hit++) {
            boss.onPlayerAttack(player, null);
            boss.update(++frame, player);
            while (boss.getHitFlashTimerForTest() > 0) {
                boss.update(++frame, player);
            }
        }
        player.setControlLocked(true);

        boss.onPlayerAttack(player, null);
        boss.update(++frame, player);

        assertTrue(boss.isDefeatStartedForTest());
        assertEquals(0, boss.getCollisionFlags());
        assertEquals(1000, services.gameState.getScore());
        assertFalse(player.isControlLocked(),
                "FixBugs=0 frees the player only when root $30 is zero");
        Object controller = boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_EXPLOSION_CONTROLLER).get(0);
        LbzFinalBoss2Instance.BigArmExplosionControllerChild typed = assertInstanceOf(
                LbzFinalBoss2Instance.BigArmExplosionControllerChild.class, controller);
        assertEquals(0x80, typed.counterForTest());
        assertEquals(1, typed.emissionCountForTest(),
                "zeroed $2E reaches the callback on the controller's creation dispatch");

        boss.update(++frame, player);
        boss.update(++frame, player);
        assertEquals(1, typed.emissionCountForTest());
        boss.update(++frame, player);
        assertEquals(2, typed.emissionCountForTest(),
                "Obj_Wait reload $2 fires on every third later controller entry");
        assertEquals(0x80, typed.counterForTest(),
                "signed-negative $80 takes bmi and never reaches subq.b");

        frame = advanceUntil(boss, player, frame,
                boss::isCapsuleChildSpawnedForTest, 0x500);
        assertTrue(frame > 0);
        assertFalse(typed.isDestroyed(),
                "Obj_WaitForParent installs Go_Delete_Sprite on the bit-5 observation entry");
        boss.update(++frame, player);
        assertTrue(typed.isDestroyed(),
                "Delete_Current_Sprite removes the controller on its next own entry");
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_EXPLOSION_CONTROLLER).isEmpty());
    }

    @Test
    void naturallyHeldFinalHitRetainsControlUntilCapsuleGate() throws Exception {
        HarnessServices services = new HarnessServices();
        TestablePlayableSprite player = new TestablePlayableSprite(
                "knuckles", (short) 0x4400, (short) 0x0700);
        services.withPlayer(player);
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, player);
        for (int hit = 1; hit < 8; hit++) {
            boss.onPlayerAttack(player, null);
            boss.update(++frame, player);
            while (boss.getHitFlashTimerForTest() > 0) {
                boss.update(++frame, player);
            }
        }
        LbzFinalBoss2Instance.GrabOwnerChild grab = assertInstanceOf(
                LbzFinalBoss2Instance.GrabOwnerChild.class,
                boss.childrenOfKindForTest(LbzFinalBoss2Instance.ChildKind.GRAB).get(0));
        player.setCentreX((short) grab.getX());
        player.setCentreYPreserveSubpixel((short) grab.getY());
        grab.update(++frame, player);
        assertTrue(boss.isGrabActiveForTest());
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertFalse(player.isObjectControlAllowsCpu(),
                "$81 is bit 7 ownership plus bit 0 movement suppression");

        boss.onPlayerAttack(player, null);
        boss.update(++frame, player);

        assertTrue(boss.isDefeatStartedForTest());
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.GRAB).isEmpty(),
                "the later held-owner slot deletes through loc_74BFA");
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlSuppressesMovement(),
                "FixBugs=0 retains exact $81 after later-slot held-owner deletion");

        frame = advanceUntil(boss, player, frame,
                boss::isCapsuleChildSpawnedForTest, 0x500);
        assertTrue(player.isObjectControlled(),
                "$81 persists throughout the capsule wait");
        services.gameState.setEndOfLevelActive(false);
        services.water.setDynamicWaterLocked(Sonic3kZoneIds.ZONE_LBZ, 1, true);
        boss.update(++frame, player);
        assertFalse(player.isObjectControlled(),
                "loc_7473A, not defeat or grab deletion, restores object control");
        assertTrue(player.isControlLocked(),
                "the same gate entry installs the production autowalk lock");
    }

    @Test
    void defeatDelayCreatesLevelMusicFadeBeforeDebrisCallback() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = defeatBoss(boss, null);
        ObjectManager manager = mock(ObjectManager.class);
        services.objectManager = manager;
        List<SongFadeTransitionInstance> fades = new ArrayList<>();
        int[] nextSlot = {4};
        doAnswer(invocation -> {
            SongFadeTransitionInstance fade = invocation.getArgument(0);
            fade.setServices(services);
            fade.setSlotIndex(nextSlot[0]++);
            fades.add(fade);
            return null;
        }).when(manager).addDynamicObject(any());
        doAnswer(invocation -> {
            AbstractObjectInstance child = invocation.getArgument(0);
            child.setServices(services);
            child.setSlotIndex(nextSlot[0]++);
            return null;
        }).when(manager).addDynamicObjectAfterCurrent(any());

        frame = advanceUntil(boss, null, frame,
                () -> boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.DEFEAT_DEBRIS).size() == 5,
                0x100);

        assertEquals(1, fades.size(),
                "Wait_FadeToLevelMusic allocates the independent fade owner first");
        SongFadeTransitionInstance fade = fades.get(0);
        assertEquals(120, intField(fade, "nativeWaitWord"));
        assertEquals(0, intField(fade, "elapsedUpdates"));
        assertEquals(120, intField(fade, "nativeRemaining"));
        assertEquals(119, intField(boss, "defeatTimer"),
                "Wait_FadeToLevelMusic writes root $2E=119 before AllocateObject");
        fade.update(++frame, null);
        assertEquals(119, intField(fade, "nativeRemaining"),
                "the fade owner's first own entry leaves native $2E=119");
        assertEquals(5, boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_DEBRIS).size());
    }

    @Test
    void slotExhaustionSkipsExplosionRngAndSfx() throws Exception {
        HarnessServices exhaustedServices = new HarnessServices();
        ObjectManager exhaustedManager = mock(ObjectManager.class);
        exhaustedServices.objectManager = exhaustedManager;
        doAnswer(invocation -> {
            AbstractObjectInstance child = invocation.getArgument(0);
            child.setServices(exhaustedServices);
            child.setDestroyed(true);
            return null;
        }).when(exhaustedManager).addDynamicObjectAfterCurrent(any());
        LbzFinalBoss2Instance exhaustedBoss = newBoss(exhaustedServices);
        LbzFinalBoss2Instance.BigArmExplosionControllerChild exhaustedController =
                newExplosionController(exhaustedBoss, exhaustedServices);
        exhaustedServices.rng().setSeed(0x13572468L);

        exhaustedController.update(0, null);

        assertEquals(0x13572468L, exhaustedServices.rng().getSeed(),
                "failed CreateChild6_Simple returns before Random_Number");
        assertTrue(exhaustedServices.sfx.isEmpty());
        assertEquals(0, exhaustedController.emissionCountForTest());

        HarnessServices successServices = new HarnessServices();
        ObjectManager successManager = mock(ObjectManager.class);
        successServices.objectManager = successManager;
        List<S3kBossExplosionChild> allocated = new ArrayList<>();
        doAnswer(invocation -> {
            S3kBossExplosionChild child = invocation.getArgument(0);
            assertEquals(exhaustedBoss.getCentreX(), child.getX(),
                    "allocation occurs at the unoffset parent coordinate");
            child.setServices(successServices);
            child.setSlotIndex(27);
            allocated.add(child);
            return null;
        }).when(successManager).addDynamicObjectAfterCurrent(any());
        LbzFinalBoss2Instance successBoss = newBoss(successServices);
        LbzFinalBoss2Instance.BigArmExplosionControllerChild successController =
                newExplosionController(successBoss, successServices);
        long seed = 0x13572468L;
        successServices.rng().setSeed(seed);
        GameRng oracle = new GameRng(GameRng.Flavour.S3K, seed);
        int random = oracle.nextRaw();

        successController.update(0, null);

        assertEquals(oracle.getSeed(), successServices.rng().getSeed(),
                "successful allocation advances Random_Number exactly once");
        assertEquals(1, allocated.size());
        assertEquals((successBoss.getCentreX() + (random & 0x3F) - 0x20) & 0xFFFF,
                allocated.get(0).getX());
        assertEquals((successBoss.getCentreY() + ((random >>> 16) & 0x3F) - 0x20) & 0xFFFF,
                allocated.get(0).getY());
        assertTrue(successServices.sfx.isEmpty(),
                "allocation/controller dispatch is silent");
        allocated.get(0).update(0, null);
        assertEquals(List.of(Sonic3kSfx.EXPLODE.id), successServices.sfx,
                "the visible child's first own entry plays exactly once");
    }

    @Test
    void finalHitSkipsBossHitAndOnlySuccessfulVisibleInitPlaysExplode() throws Exception {
        HarnessServices services = new HarnessServices();
        TestablePlayableSprite player = new TestablePlayableSprite(
                "knuckles", (short) 0x4400, (short) 0x0700);
        services.withPlayer(player);
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = initializeFight(boss, player);
        for (int hit = 1; hit < 8; hit++) {
            boss.onPlayerAttack(player, null);
            boss.update(++frame, player);
            while (boss.getHitFlashTimerForTest() > 0) {
                boss.update(++frame, player);
            }
        }
        services.sfx.clear();

        ObjectManager manager = mock(ObjectManager.class);
        services.objectManager = manager;
        List<AbstractObjectInstance> allocated = new ArrayList<>();
        doAnswer(invocation -> {
            AbstractObjectInstance child = invocation.getArgument(0);
            child.setServices(services);
            child.setSlotIndex(20 + allocated.size());
            allocated.add(child);
            return null;
        }).when(manager).addDynamicObjectAfterCurrent(any());
        doAnswer(invocation -> {
            AbstractObjectInstance child = invocation.getArgument(0);
            child.setServices(services);
            child.setSlotIndex(20 + allocated.size());
            allocated.add(child);
            return null;
        }).when(manager).addDynamicObjectAfterSlot(
                any(), org.mockito.ArgumentMatchers.anyInt());

        boss.onPlayerAttack(player, null);
        boss.update(++frame, player);
        assertTrue(boss.isDefeatStartedForTest());
        assertTrue(services.sfx.isEmpty(),
                "collision_property zero branches before the nonfinal BossHit call");

        LbzFinalBoss2Instance.BigArmExplosionControllerChild controller = assertInstanceOf(
                LbzFinalBoss2Instance.BigArmExplosionControllerChild.class,
                boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.DEFEAT_EXPLOSION_CONTROLLER).get(0));
        controller.update(++frame, player);
        assertTrue(services.sfx.isEmpty(),
                "controller allocation and creation dispatch stay silent");
        S3kBossExplosionChild visible = allocated.stream()
                .filter(S3kBossExplosionChild.class::isInstance)
                .map(S3kBossExplosionChild.class::cast)
                .findFirst()
                .orElseThrow();
        visible.update(++frame, player);
        assertEquals(List.of(Sonic3kSfx.EXPLODE.id), services.sfx);
    }

    @Test
    void finalHitTransitionsArticulatedChildrenBySourceSlotLifecycle() {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = defeatBoss(boss, null);

        LbzFinalBoss2Instance.ArmControllerChild controller = assertInstanceOf(
                LbzFinalBoss2Instance.ArmControllerChild.class,
                boss.childrenOfKindForTest(LbzFinalBoss2Instance.ChildKind.ARM_GRAPH).get(0));
        List<Object> segments = boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT);
        LbzFinalBoss2Instance.BossChild joint = firstChild(
                boss, LbzFinalBoss2Instance.ChildKind.ARM_JOINT);
        assertEquals(0, controller.getCollisionFlags());
        assertEquals(0, joint.getCollisionFlags());
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION).isEmpty());
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_UPPER_COLLISION).isEmpty());
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.GRAB).isEmpty());
        assertEquals(1, boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_ATTACHMENT).size());
        assertEquals(1, boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_VISUAL).size());

        int controllerX = controller.getX();
        int controllerY = controller.getY();
        int segment0X = ((LbzFinalBoss2Instance.BossChild) segments.get(0)).getX();
        int jointX = joint.getX();
        boss.update(++frame, null);

        int xSign = boss.isRenderXFlipForTest() ? -1 : 1;
        assertEquals((controllerX + xSign * 2) & 0xFFFF, controller.getX());
        assertEquals((controllerY - 2) & 0xFFFF, controller.getY());
        assertEquals((segment0X + xSign * 2) & 0xFFFF,
                ((LbzFinalBoss2Instance.BossChild) segments.get(0)).getX());
        assertEquals((jointX + xSign * 3) & 0xFFFF, joint.getX());

        frame = advanceUntil(boss, null, frame,
                () -> boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.DEFEAT_DEBRIS).size() == 5,
                0x100);
        assertEquals(1, boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_ATTACHMENT).size());
        assertEquals(1, boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_VISUAL).size());
        assertEquals(1, boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_FOLLOW_VISUAL).size(),
                "Child_Draw_Sprite2 retains all three pending callbacks on the signal entry");
        boss.update(++frame, null);
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_ATTACHMENT).isEmpty());
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_VISUAL).isEmpty());
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_FOLLOW_VISUAL).isEmpty());
    }

    @Test
    void articulatedFlickerVelocityUsesTransitioningChildOwnFlip() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);

        LbzFinalBoss2Instance.ArmControllerChild controller =
                newArmController(boss, services, 0x14, -6);
        setBooleanField(controller, "controllerInitialized", true);
        setBooleanField(controller, "hFlip", false);
        setBooleanField(boss, "renderXFlip", true);
        setIntField(boss, "statusBits", 0x80);

        controller.update(0, null);

        assertEquals(0x200, controller.flickerXVelocityForTest(),
                "Set_IndexedVelocity tests the controller's latched render bit, not the root's new flip");

        LbzFinalBoss2Instance.ArmControllerChild immediateParent =
                newArmController(boss, services, 0x14, -6);
        setBooleanField(immediateParent, "hFlip", true);
        LbzFinalBoss2Instance.ArmSegmentChild segment =
                newArmSegment(boss, services, immediateParent, 0, 0x18, 0);
        setBooleanField(boss, "renderXFlip", false);

        segment.update(1, null);

        assertEquals(-0x200, segment.flickerXVelocityForTest(),
                "an articulated child signs indexed X from its immediate-parent-derived own flip");
    }

    @Test
    void articulatedFlickerCullUsesCameraCoarseBackWindow() throws Exception {
        HarnessServices services = new HarnessServices();
        services.camera.setX((short) 0x1080);
        LbzFinalBoss2Instance boss = newBoss(services);

        LbzFinalBoss2Instance.ArmControllerChild nativeBack =
                newArmController(boss, services, 0, 0);
        setBooleanField(nativeBack, "flickerMove", true);
        setIntField(nativeBack, "flickerXVelocity", 0);
        setIntField(nativeBack, "flickerYVelocity", 0);
        setIntField(nativeBack, "currentX", 0x1000);
        setIntField(nativeBack, "currentY", services.cameraY());

        nativeBack.update(0, null);

        assertFalse(nativeBack.isDestroyed(),
                "Camera_X_pos_coarse_back is (camera-$80)&$FF80, so $1000 survives");

        LbzFinalBoss2Instance.ArmControllerChild nativeFrontCull =
                newArmController(boss, services, 0, 0);
        setBooleanField(nativeFrontCull, "flickerMove", true);
        setIntField(nativeFrontCull, "flickerXVelocity", 0);
        setIntField(nativeFrontCull, "flickerYVelocity", 0);
        setIntField(nativeFrontCull, "currentX", 0x1300);
        setIntField(nativeFrontCull, "currentY", services.cameraY());

        nativeFrontCull.update(1, null);
        assertFalse(nativeFrontCull.isDestroyed(),
                "Go_Delete_Sprite_3 installs the delete callback on the cull entry");
        nativeFrontCull.update(2, null);
        assertTrue(nativeFrontCull.isDestroyed(),
                "Delete_Current_Sprite runs on the following own entry");
    }

    @Test
    void flickerCullInstallsDeleteCallbackBeforeNextEntryRemoval() throws Exception {
        HarnessServices services = new HarnessServices();
        services.camera.setX((short) 0x1080);
        LbzFinalBoss2Instance boss = newBoss(services);

        LbzFinalBoss2Instance.ArmControllerChild articulated =
                newArmController(boss, services, 0, 0);
        setBooleanField(articulated, "flickerMove", true);
        setIntField(articulated, "flickerXVelocity", 0x100);
        setIntField(articulated, "flickerYVelocity", 0);
        setIntField(articulated, "currentX", 0x1300);
        setIntField(articulated, "currentY", services.cameraY());

        articulated.update(0, null);
        int articulatedSignalX = articulated.getX();
        assertFalse(articulated.isDestroyed());
        articulated.update(1, null);
        assertTrue(articulated.isDestroyed());
        assertEquals(articulatedSignalX, articulated.getX(),
                "the pending delete entry performs no second movement");

        LbzFinalBoss2Instance.DefeatDebrisChild debris =
                newDefeatDebris(boss, services, 0, 0, 0);
        debris.update(0, null);
        setIntField(debris, "xVel", 0x100);
        setIntField(debris, "yVel", 0);
        setIntField(debris, "currentX", 0x1300);
        setIntField(debris, "currentY", services.cameraY());

        debris.update(1, null);
        int debrisSignalX = debris.getX();
        assertFalse(debris.isDestroyed());
        debris.update(2, null);
        assertTrue(debris.isDestroyed());
        assertEquals(debrisSignalX, debris.getX(),
                "debris uses the same deferred Go_Delete_Sprite_3 callback");
    }

    @Test
    void defeatDebrisUsesAdjustedFlipIndexedVelocityAndFullFlickerMove() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        boss.setCentreX(0x4500);
        boss.setCentreY(0x0700);
        setBooleanField(boss, "renderXFlip", true);
        LbzFinalBoss2Instance.DefeatDebrisChild debris =
                newDefeatDebris(boss, services, 0x10, 0x08, 2);

        debris.update(1, null);
        assertEquals(0x44F0, debris.getX(),
                "loc_74D14 uses adjusted refresh under the latched root flip");
        assertEquals(0x0708, debris.getY());
        assertEquals(-0x100, debris.xVelocityForTest(),
                "Set_IndexedVelocity X is negated by the child flip");
        clearInvocations(services.bigArmRenderer);
        debris.appendRenderCommands(new ArrayList<>());
        verify(services.bigArmRenderer, times(1)).drawFrameIndex(
                debris.mappingFrameForTest(), debris.getX(), debris.getY(), true, false, 1);

        clearInvocations(services.bigArmRenderer);
        debris.update(2, null);
        debris.appendRenderCommands(new ArrayList<>());
        verify(services.bigArmRenderer, never()).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt());
        debris.update(3, null);
        debris.appendRenderCommands(new ArrayList<>());
        verify(services.bigArmRenderer, times(1)).drawFrameIndex(
                debris.mappingFrameForTest(), debris.getX(), debris.getY(), true, false, 1);

        setIntField(debris, "currentX", services.cameraX() + 0x300);
        setIntField(debris, "currentY", services.cameraY());
        debris.update(4, null);
        assertFalse(debris.isDestroyed(),
                "Go_Delete_Sprite_3 retains the cull-entry SST");
        debris.update(5, null);
        assertTrue(debris.isDestroyed(),
                "Delete_Current_Sprite runs on the following own entry");
    }

    @Test
    void rootAndDefeatFollowDrawOnlyOnNativeCallbacks() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = defeatBoss(boss, null);

        clearInvocations(services.shipRenderer);
        boss.appendRenderCommands(new ArrayList<>());
        verify(services.shipRenderer, times(1)).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(0));

        LbzFinalBoss2Instance.DefeatFollowVisualChild follow = assertInstanceOf(
                LbzFinalBoss2Instance.DefeatFollowVisualChild.class,
                boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.DEFEAT_FOLLOW_VISUAL).get(0));
        clearInvocations(services.finalBoss1Renderer);
        follow.appendRenderCommands(new ArrayList<>());
        verify(services.finalBoss1Renderer, never()).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt());

        setBooleanField(boss, "renderXFlip", true);
        boss.update(++frame, null);
        follow.appendRenderCommands(new ArrayList<>());
        verify(services.finalBoss1Renderer, times(1)).drawFrameIndex(
                0x15, follow.getX(), follow.getY(), false, false, 1);

        setIntField(boss, "defeatTimer", 0);
        clearInvocations(services.shipRenderer);
        boss.update(++frame, null);
        boss.appendRenderCommands(new ArrayList<>());
        verify(services.shipRenderer, never()).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt());

        setIntField(boss, "y", services.cameraY() - 0x40);
        clearInvocations(services.shipRenderer);
        boss.update(++frame, null);
        boss.appendRenderCommands(new ArrayList<>());
        verify(services.shipRenderer, never()).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt());

        setEnumField(boss, "defeatStage", "FLOOR_WAIT");
        setIntField(boss, "defeatTimer", 0);
        setBooleanField(boss, "rootHidden", false);
        clearInvocations(services.shipRenderer);
        boss.update(++frame, null);
        boss.appendRenderCommands(new ArrayList<>());
        verify(services.shipRenderer, times(1)).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(0));

        setEnumField(boss, "defeatStage", "SHIP_ESCAPE");
        setIntField(boss, "x", services.cameraX() + 0x1C0);
        setIntField(boss, "xVel", 0);
        clearInvocations(services.shipRenderer);
        boss.update(++frame, null);
        boss.appendRenderCommands(new ArrayList<>());
        verify(services.shipRenderer, never()).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void defeatCreatesFiveIndexedDebrisInNativeOrder() {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = defeatBoss(boss, null);

        frame = advanceUntil(boss, null, frame,
                () -> boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.DEFEAT_DEBRIS).size() == 5,
                0x100);
        assertTrue(frame > 0);
        int[][] expectedVelocities = {
                {-0x100, -0x100}, {0x100, -0x100},
                {-0x200, -0x200}, {0x200, -0x200}, {-0x300, -0x200}
        };
        List<Object> debris = boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_DEBRIS);
        for (int i = 0; i < debris.size(); i++) {
            LbzFinalBoss2Instance.DefeatDebrisChild child = assertInstanceOf(
                    LbzFinalBoss2Instance.DefeatDebrisChild.class, debris.get(i));
            assertEquals(i * 2, child.subtypeForTest());
            assertEquals(expectedVelocities[i][0], child.xVelocityForTest());
            assertEquals(expectedVelocities[i][1], child.yVelocityForTest());
        }
    }

    @Test
    void defeatRiseUsesStrictCameraMinus40ThresholdBeforeCapsule() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = defeatBoss(boss, null);
        frame = advanceUntil(boss, null, frame,
                () -> boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.DEFEAT_DEBRIS).size() == 5,
                0x100);

        setIntField(boss, "y", services.cameraY() - 0x3F);
        boss.update(++frame, null);
        assertEquals(services.cameraY() - 0x40, boss.getCentreY());
        assertFalse(boss.isCapsuleChildSpawnedForTest(),
                "cmp/blo does not take the boundary at equality");

        boss.update(++frame, null);

        assertEquals(services.cameraY() - 0x41, boss.getCentreY());
        assertTrue(boss.isCapsuleChildSpawnedForTest());
        assertTrue(services.gameState.isEndOfLevelActive());
    }

    @Test
    void capsuleGateRetainsBit5UntilPlcHeadTargetAndShipClearsBossFlag() throws Exception {
        HarnessServices services = new HarnessServices();
        TestablePlayableSprite player = new TestablePlayableSprite(
                "knuckles", (short) services.cameraX(), (short) 0x03E0);
        services.withPlayer(player);
        services.gameState.setCurrentBossId(0xCA);
        LbzFinalBoss2Instance boss = newBoss(services);
        int frame = defeatBoss(boss, player);
        frame = advanceUntil(boss, player, frame,
                boss::isCapsuleChildSpawnedForTest, 0x500);
        assertTrue((intField(boss, "flags") & (1 << 5)) != 0);

        services.gameState.setEndOfLevelActive(false);
        services.water.setDynamicWaterLocked(Sonic3kZoneIds.ZONE_LBZ, 1, true);
        player.setCentreX((short) services.cameraX());
        boss.update(++frame, player);

        assertTrue(boss.isCapsuleReleasedForTest());
        assertTrue((intField(boss, "flags") & (1 << 5)) != 0,
                "loc_7473A leaves root $38 bit 5 set while autowalk is active");
        assertEquals(0, services.transitionBridge.postGatePlcLoads);

        player.setCentreX((short) (services.cameraX() + 0x50));
        boss.update(++frame, player);

        assertEquals(0, intField(boss, "flags") & (1 << 5),
                "loc_74768 clears bit 5 only at the autowalk target");
        assertEquals(1, services.transitionBridge.postGatePlcLoads,
                "PLC $71 is submitted before the replacement head allocation");
        assertEquals(0xCA, services.gameState.getCurrentBossId(),
                "the gate and autowalk target do not clear Boss_flag");

        frame = advanceUntil(boss, player, frame,
                () -> services.gameState.getCurrentBossId() == 0, 0x500);
        assertTrue(frame > 0);
        assertTrue(boss.getCentreX() >= services.cameraX() + 0x1C0);
    }

    @Test
    void shipCrossingSetsStatusBit6AndRetainsArtPriority() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance boss = newBoss(services);
        setBooleanField(boss, "initialized", true);
        setBooleanField(boss, "defeatStarted", true);
        setEnumField(boss, "defeatStage", "SHIP_ESCAPE");
        setIntField(boss, "x", services.cameraX() + 0x1C0);
        setIntField(boss, "xVel", 0);
        setIntField(boss, "statusBits", 0);
        setIntField(boss, "flags", 0);
        setBooleanField(boss, "artTileHigh", true);
        services.gameState.setCurrentBossId(OBJECT_ID);

        boss.update(1, null);

        assertEquals(1 << 6, intField(boss, "statusBits") & (1 << 6));
        assertEquals((1 << 4) | (1 << 5),
                intField(boss, "flags") & ((1 << 4) | (1 << 5)));
        assertEquals(0, services.gameState.getCurrentBossId());
        assertTrue(boss.isHighPriority());
        clearInvocations(services.shipRenderer);
        boss.appendRenderCommands(new ArrayList<>());
        verify(services.shipRenderer, never()).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void artTilePriorityPropagatesIndependentlyFromPriorityBucket() throws Exception {
        HarnessServices services = new HarnessServices();
        LbzFinalBoss2Instance lowRoot = newBoss(services);
        assertFalse(lowRoot.isHighPriority());
        assertEquals(5, lowRoot.getPriorityBucket());

        setBooleanField(lowRoot, "artTileHigh", true);
        assertTrue(lowRoot.isHighPriority(),
                "art_tile bit 7 is independent from numeric sprite priority");

        Constructor<LbzFinalBoss2Instance.RobotnikShipFlameChild> constructor =
                LbzFinalBoss2Instance.RobotnikShipFlameChild.class
                        .getDeclaredConstructor(LbzFinalBoss2Instance.class);
        constructor.setAccessible(true);
        setBooleanField(lowRoot, "artTileHigh", false);
        LbzFinalBoss2Instance.RobotnikShipFlameChild lowFlame =
                constructor.newInstance(lowRoot);
        lowFlame.setServices(services);
        setBooleanField(lowRoot, "artTileHigh", true);
        assertFalse(lowFlame.isHighPriority(),
                "the abbreviated flame ObjDat keeps the copied low root art bit");

        LbzFinalBoss2Instance highRoot = newBoss(services);
        setBooleanField(highRoot, "artTileHigh", true);
        LbzFinalBoss2Instance.RobotnikShipFlameChild highFlame =
                constructor.newInstance(highRoot);
        highFlame.setServices(services);
        setBooleanField(highRoot, "artTileHigh", false);
        assertTrue(highFlame.isHighPriority(),
                "flame priority is copied once, not a constant or dynamic mirror");

        S3kBossExplosionChild genericExplosion = new S3kBossExplosionChild(0, 0);
        assertTrue(genericExplosion.isHighPriority());

        HarnessServices graphServices = new HarnessServices();
        LbzFinalBoss2Instance graphRoot = newBoss(graphServices);
        initializeFight(graphRoot, null);
        assertTrue(graphRoot.isHighPriority());

        LbzFinalBoss2Instance.RobotnikHead4Child head = assertInstanceOf(
                LbzFinalBoss2Instance.RobotnikHead4Child.class,
                graphRoot.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD).getFirst());
        LbzFinalBoss2Instance.ArmControllerChild controller = assertInstanceOf(
                LbzFinalBoss2Instance.ArmControllerChild.class,
                graphRoot.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ARM_GRAPH).getFirst());
        assertEquals(3, controller.getPriorityBucket());
        assertTrue(controller.isHighPriority());
        assertTrue(head.isHighPriority());
        assertEquals(5, head.getPriorityBucket());

        for (LbzFinalBoss2Instance.ChildKind kind : List.of(
                LbzFinalBoss2Instance.ChildKind.ARM_ATTACHMENT,
                LbzFinalBoss2Instance.ChildKind.ARM_VISUAL,
                LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION,
                LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT,
                LbzFinalBoss2Instance.ChildKind.ARM_JOINT,
                LbzFinalBoss2Instance.ChildKind.ARM_UPPER_COLLISION)) {
            for (Object object : graphRoot.childrenOfKindForTest(kind)) {
                assertTrue(assertInstanceOf(
                                LbzFinalBoss2Instance.BossChild.class, object).isHighPriority(),
                        kind + " must expose its source-owned art_tile bit independently");
            }
        }
        LbzFinalBoss2Instance.GrabOwnerChild grab = assertInstanceOf(
                LbzFinalBoss2Instance.GrabOwnerChild.class,
                graphRoot.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.GRAB).getFirst());
        assertFalse(grab.isHighPriority(),
                "the invisible grab slot retains the controller's low allocation-time copy");

        setBooleanField(graphRoot, "artTileHigh", false);
        head.update(0, null);
        assertFalse(head.isHighPriority(), "Child_GetPriority dynamically mirrors the head");
        assertTrue(controller.isHighPriority(),
                "Child_GetPriorityOnce owners retain the already-latched bit");
        LbzFinalBoss2Instance.ArmOuterCollisionChild outer = assertInstanceOf(
                LbzFinalBoss2Instance.ArmOuterCollisionChild.class,
                graphRoot.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION).getFirst());
        outer.update(1, null);
        assertTrue(outer.isHighPriority());
        assertEquals(0x9A, outer.getCollisionFlags());

        setBooleanField(graphRoot, "artTileHigh", true);
        Method capsuleHandoff = LbzFinalBoss2Instance.class
                .getDeclaredMethod("beginCapsuleHandoff");
        capsuleHandoff.setAccessible(true);
        capsuleHandoff.invoke(graphRoot);
        assertTrue(graphRoot.isHighPriority(),
                "loc_74710 clears render_flags bit 7, never art_tile bit 7");
    }

    private static LbzFinalBoss2Instance newBoss(HarnessServices services) {
        LbzFinalBoss2Instance boss = new LbzFinalBoss2Instance(new ObjectSpawn(
                0x44A0, 0x0780, OBJECT_ID, 0, 0, false, 0));
        boss.setServices(services);
        return boss;
    }

    private static int initializeFight(LbzFinalBoss2Instance boss,
                                       TestablePlayableSprite player) {
        boss.update(0, player);
        return advanceUntil(boss, player, 0,
                () -> boss.getRoutineForTest() == 0x08
                        && boss.getCollisionFlags() == 0x0F,
                0x400);
    }

    private static int acquireGrab(LbzFinalBoss2Instance boss,
                                   HarnessServices services,
                                   TestablePlayableSprite player) {
        int frame = initializeFight(boss, player);
        frame = advanceUntil(boss, player, frame,
                () -> boss.getRoutineForTest() == 0x0A, 0x300);
        LbzFinalBoss2Instance.GrabOwnerChild grab = assertInstanceOf(
                LbzFinalBoss2Instance.GrabOwnerChild.class,
                boss.childrenOfKindForTest(LbzFinalBoss2Instance.ChildKind.GRAB).get(0));
        player.setCentreX((short) grab.getX());
        player.setCentreYPreserveSubpixel((short) grab.getY());
        grab.update(++frame, player);
        assertTrue(boss.isGrabActiveForTest());
        assertEquals(0x1E, boss.getRoutineForTest());
        return frame;
    }

    private static int enterGrabAlignWait(LbzFinalBoss2Instance boss,
                                          TestablePlayableSprite player,
                                          int frame,
                                          HarnessServices services) throws Exception {
        setIntField(boss, "waitTimer", 0);
        boss.update(++frame, player);
        assertEquals(0x20, boss.getRoutineForTest());
        boss.setCentreY(services.cameraY() - 0x60);
        setIntField(boss, "yVel", 0);
        boss.update(++frame, player);
        assertEquals(0x22, boss.getRoutineForTest());
        return frame;
    }

    private static int advanceGrabToThrowWait(LbzFinalBoss2Instance boss,
                                              TestablePlayableSprite player,
                                              int frame,
                                              HarnessServices services) throws Exception {
        frame = enterGrabAlignWait(boss, player, frame, services);
        setIntField(boss, "waitTimer", 0);
        boss.update(++frame, player);
        assertEquals(0x24, boss.getRoutineForTest());
        boss.setCentreY(services.cameraY() + 0x88);
        boss.update(++frame, player);
        assertEquals(0x26, boss.getRoutineForTest());
        setIntField(boss, "waitTimer", 0);
        boss.update(++frame, player);
        assertEquals(0x28, boss.getRoutineForTest());
        return frame;
    }

    private static int defeatBoss(LbzFinalBoss2Instance boss,
                                  TestablePlayableSprite player) {
        int frame = initializeFight(boss, player);
        for (int hit = 0; hit < 8; hit++) {
            boss.onPlayerAttack(player, null);
            boss.update(++frame, player);
            while (hit < 7 && boss.getHitFlashTimerForTest() > 0) {
                boss.update(++frame, player);
            }
        }
        assertTrue(boss.isDefeatStartedForTest());
        return frame;
    }

    private static int advanceUntil(LbzFinalBoss2Instance boss,
                                    TestablePlayableSprite player,
                                    int frame,
                                    BooleanSupplier condition,
                                    int limit) {
        for (int count = 0; count < limit; count++) {
            if (condition.getAsBoolean()) {
                return frame;
            }
            boss.update(++frame, player);
        }
        throw new AssertionError("Big Arm condition not reached after " + limit
                + " entries; routine=" + boss.getRoutineForTest()
                + " x=" + Integer.toHexString(boss.getCentreX())
                + " y=" + Integer.toHexString(boss.getCentreY()));
    }

    private static LbzFinalBoss2Instance.BossChild firstChild(
            LbzFinalBoss2Instance boss,
            LbzFinalBoss2Instance.ChildKind kind) {
        return assertInstanceOf(LbzFinalBoss2Instance.BossChild.class,
                boss.childrenOfKindForTest(kind).get(0));
    }

    private static LbzFinalBoss2Instance.BigArmExplosionControllerChild newExplosionController(
            LbzFinalBoss2Instance boss, HarnessServices services) throws Exception {
        Constructor<LbzFinalBoss2Instance.BigArmExplosionControllerChild> constructor =
                LbzFinalBoss2Instance.BigArmExplosionControllerChild.class
                        .getDeclaredConstructor(LbzFinalBoss2Instance.class);
        constructor.setAccessible(true);
        LbzFinalBoss2Instance.BigArmExplosionControllerChild controller =
                constructor.newInstance(boss);
        controller.setServices(services);
        return controller;
    }

    private static LbzFinalBoss2Instance.ArmControllerChild newArmController(
            LbzFinalBoss2Instance boss, HarnessServices services, int dx, int dy) throws Exception {
        Constructor<LbzFinalBoss2Instance.ArmControllerChild> constructor =
                LbzFinalBoss2Instance.ArmControllerChild.class.getDeclaredConstructor(
                        LbzFinalBoss2Instance.class, int.class, int.class);
        constructor.setAccessible(true);
        LbzFinalBoss2Instance.ArmControllerChild child = constructor.newInstance(boss, dx, dy);
        child.setServices(services);
        return child;
    }

    private static LbzFinalBoss2Instance.ArmSegmentChild newArmSegment(
            LbzFinalBoss2Instance boss, HarnessServices services,
            LbzFinalBoss2Instance.ArmControllerChild controller,
            int subtype, int dx, int dy) throws Exception {
        Constructor<LbzFinalBoss2Instance.ArmSegmentChild> constructor =
                LbzFinalBoss2Instance.ArmSegmentChild.class.getDeclaredConstructor(
                        LbzFinalBoss2Instance.class,
                        LbzFinalBoss2Instance.ArmControllerChild.class,
                        int.class, int.class, int.class);
        constructor.setAccessible(true);
        LbzFinalBoss2Instance.ArmSegmentChild child =
                constructor.newInstance(boss, controller, subtype, dx, dy);
        child.setServices(services);
        return child;
    }

    private static LbzFinalBoss2Instance.GrabOwnerChild newGrabOwner(
            LbzFinalBoss2Instance boss, HarnessServices services,
            LbzFinalBoss2Instance.ArmControllerChild controller,
            int dx, int dy) throws Exception {
        Constructor<LbzFinalBoss2Instance.GrabOwnerChild> constructor =
                LbzFinalBoss2Instance.GrabOwnerChild.class.getDeclaredConstructor(
                        LbzFinalBoss2Instance.class,
                        LbzFinalBoss2Instance.ArmControllerChild.class,
                        int.class, int.class);
        constructor.setAccessible(true);
        LbzFinalBoss2Instance.GrabOwnerChild child =
                constructor.newInstance(boss, controller, dx, dy);
        child.setServices(services);
        return child;
    }

    private static LbzFinalBoss2Instance.DefeatDebrisChild newDefeatDebris(
            LbzFinalBoss2Instance boss, HarnessServices services,
            int dx, int dy, int subtype) throws Exception {
        Constructor<LbzFinalBoss2Instance.DefeatDebrisChild> constructor =
                LbzFinalBoss2Instance.DefeatDebrisChild.class.getDeclaredConstructor(
                        LbzFinalBoss2Instance.class,
                        int.class, int.class, int.class);
        constructor.setAccessible(true);
        LbzFinalBoss2Instance.DefeatDebrisChild child =
                constructor.newInstance(boss, dx, dy, subtype);
        child.setServices(services);
        return child;
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static Object objectField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setObjectField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setEnumField(Object target, String name, String value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object enumValue = Enum.valueOf((Class<? extends Enum>) field.getType(), value);
        field.set(target, enumValue);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the concrete child/root hierarchy.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void publishAppliedShake(LbzZoneRuntimeState state, int offset) {
        state.startTimedScreenShake(1);
        state.prepareTimedScreenShakeBackground(true, offset);
        state.applyTimedScreenShakeForeground();
    }

    private static void assertPaletteWords(Palette palette, int[] indices, int[] words) {
        for (int i = 0; i < indices.length; i++) {
            Palette.Color expected = new Palette.Color();
            expected.fromSegaFormat(new byte[]{
                    (byte) (words[i] >>> 8), (byte) words[i]}, 0);
            Palette.Color actual = palette.getColor(indices[i]);
            assertEquals(Byte.toUnsignedInt(expected.r), Byte.toUnsignedInt(actual.r),
                    "red at palette index " + indices[i]);
            assertEquals(Byte.toUnsignedInt(expected.g), Byte.toUnsignedInt(actual.g),
                    "green at palette index " + indices[i]);
            assertEquals(Byte.toUnsignedInt(expected.b), Byte.toUnsignedInt(actual.b),
                    "blue at palette index " + indices[i]);
        }
    }

    private static final class HarnessServices extends StubObjectServices {
        private final Camera camera = new Camera();
        private final LbzZoneRuntimeState state = new LbzZoneRuntimeState(
                1, PlayerCharacter.KNUCKLES);
        private final GameStateManager gameState = new GameStateManager();
        private final WaterSystem water = new WaterSystem();
        private final StubLevel level = new StubLevel();
        private final List<Integer> sfx = new ArrayList<>();
        private ObjectManager objectManager;
        private final AudioManager audioManager = mock(AudioManager.class);
        private final RecordingTransitionBridge transitionBridge =
                new RecordingTransitionBridge();
        private final ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        private final PatternSpriteRenderer bigArmRenderer = mock(PatternSpriteRenderer.class);
        private final PatternSpriteRenderer shipRenderer = mock(PatternSpriteRenderer.class);
        private final PatternSpriteRenderer finalBoss1Renderer = mock(PatternSpriteRenderer.class);
        private TestablePlayableSprite player;

        private HarnessServices() {
            camera.setX((short) 0x4300);
            camera.setY((short) 0x0600);
            water.loadForLevelFromProvider(new StaticWaterProvider(), null,
                    Sonic3kZoneIds.ZONE_LBZ, 1, PlayerCharacter.KNUCKLES);
            when(bigArmRenderer.isReady()).thenReturn(true);
            when(shipRenderer.isReady()).thenReturn(true);
            when(finalBoss1Renderer.isReady()).thenReturn(true);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.LBZ_FINAL_BOSS_2))
                    .thenReturn(bigArmRenderer);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP))
                    .thenReturn(shipRenderer);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.EGG_ROBO_HEAD))
                    .thenReturn(shipRenderer);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.LBZ_FINAL_BOSS_1))
                    .thenReturn(finalBoss1Renderer);
        }

        private void withPlayer(TestablePlayableSprite player) {
            this.player = player;
        }

        private int cameraX() {
            return Short.toUnsignedInt(camera.getX());
        }

        private int cameraY() {
            return Short.toUnsignedInt(camera.getY());
        }

        @Override public Camera camera() { return camera; }
        @Override public ZoneRuntimeState zoneRuntimeState() { return state; }
        @Override public GameStateManager gameState() { return gameState; }
        @Override public WaterSystem waterSystem() { return water; }
        @Override public Level currentLevel() { return level; }
        @Override public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> player, List::of);
        }
        @Override public ObjectManager objectManager() { return objectManager; }
        @Override public ObjectRenderManager renderManager() { return renderManager; }
        @Override public AudioManager audioManager() { return audioManager; }
        @Override public LevelEventProvider levelEventProvider() { return transitionBridge; }
        @Override public Rom rom() { return TestEnvironment.currentRom(); }
        @Override public RomByteReader romReader() {
            try {
                return RomByteReader.fromRom(rom());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        @Override public RuntimeArtCoordinator runtimeArtCoordinator() {
            return TestEnvironment.activeGameplayMode().runtimeArtCoordinator();
        }
        @Override public void playSfx(int soundId) { sfx.add(soundId); }
    }

    private static final class RecordingTransitionBridge
            implements LevelEventProvider, S3kTransitionEventBridge {
        private int postGatePlcLoads;

        @Override public void initLevel(int zone, int act) { }
        @Override public void update() { }
        @Override public void signalActTransition() { }
        @Override public void requestHczPostTransitionCutscene() { }
        @Override public void requestMgzPostTransitionRelease() { }
        @Override public void requestCnzPostTransitionRelease() { }
        @Override public void loadLbzBigArmPostGatePlc() { postGatePlcLoads++; }
    }

    private static final class StaticWaterProvider implements WaterDataProvider {
        @Override public boolean hasWater(int zoneId, int actId, PlayerCharacter character) {
            return true;
        }
        @Override public int getStartingWaterLevel(int zoneId, int actId) { return 0x0640; }
        @Override public Palette[] getUnderwaterPalette(
                Rom rom, int zoneId, int actId, PlayerCharacter character) { return null; }
        @Override public DynamicWaterHandler getDynamicHandler(
                int zoneId, int actId, PlayerCharacter character) { return null; }
    }

    private static final class StubLevel implements Level {
        private final Palette[] palettes = {
                new Palette(), new Palette(), new Palette(), new Palette()
        };

        @Override public int getPaletteCount() { return palettes.length; }
        @Override public Palette getPalette(int index) { return palettes[index]; }
        @Override public int getPatternCount() { return 0; }
        @Override public Pattern getPattern(int index) { throw new UnsupportedOperationException(); }
        @Override public int getChunkCount() { return 0; }
        @Override public Chunk getChunk(int index) { throw new UnsupportedOperationException(); }
        @Override public int getBlockCount() { return 0; }
        @Override public Block getBlock(int index) { throw new UnsupportedOperationException(); }
        @Override public SolidTile getSolidTile(int index) { throw new UnsupportedOperationException(); }
        @Override public Map getMap() { return null; }
        @Override public List<ObjectSpawn> getObjects() { return List.of(); }
        @Override public List<RingSpawn> getRings() { return List.of(); }
        @Override public RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return 0; }
        @Override public int getMaxX() { return 0; }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 0; }
        @Override public int getZoneIndex() { return Sonic3kZoneIds.ZONE_LBZ; }
    }
}
