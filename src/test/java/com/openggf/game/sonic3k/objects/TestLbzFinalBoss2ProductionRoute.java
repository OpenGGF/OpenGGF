package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kLBZEvents;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2EggCapsuleInstance;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.BigArmExplosionControllerChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.EscapeExplosionEmitterChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.EscapeFloorChild;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Production-owner acceptance for {@code loc_746D8-loc_7498E}. */
@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(SingletonResetExtension.class)
class TestLbzFinalBoss2ProductionRoute {
    /** word_72FEA clamps the Knuckles wrapper arena to Camera_X=$4380. */
    private static final int CAMERA_X = 0x4380;
    private static final int CAMERA_Y = 0x0328;
    private static final int SAFE_PLAYER_X = 0x43A0;
    private static final int SAFE_PLAYER_Y = 0x03E0;

    private HeadlessTestFixture fixture;

    @BeforeEach
    void setUp() {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .build();
        fixture.camera().setX((short) CAMERA_X);
        fixture.camera().setY((short) CAMERA_Y);
        fixture.camera().setMinX((short) CAMERA_X);
        fixture.camera().setMaxX((short) CAMERA_X);
        fixture.camera().setMinY((short) CAMERA_Y);
        fixture.camera().setMaxY((short) CAMERA_Y);
        pinPlayer(SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
    }

    @AfterEach
    void tearDown() {
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void realCapsuleResultsFloorAndCarrierCompleteTheKnucklesRoute() {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        GameServices.water().setDynamicWaterLocked(Sonic3kZoneIds.ZONE_LBZ, 1, true);
        LbzFinalBoss1Instance wrapper = spawnFinalBoss1ThroughRegistry(objectManager);
        fixture.stepIdleFrames(1);
        assertEquals(Sonic3kObjectIds.LBZ_FINAL_BOSS_1,
                GameServices.gameState().getCurrentBossId(),
                "the live $CA wrapper owns Boss_flag setup before the Big Arm handoff");
        assertFalse(GameServices.water().isDynamicWaterLocked(Sonic3kZoneIds.ZONE_LBZ, 1),
                "the Knuckles wrapper owns the stale _unkFAA2 clear");

        stepPinnedUntil(() -> wrapper.getCollisionFlags() == 0x0F, 0x40,
                SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
        for (int hit = 1; hit <= 9; hit++) {
            stepPinnedUntil(() -> wrapperTouchWindow(wrapper), 0x1000,
                    SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
            attackThroughOrdinaryTouch(wrapper, hit);
            if (hit < 9) {
                stepPinnedUntil(() -> wrapper.getCollisionFlags() == 0x0F, 0x100,
                        SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
            }
        }
        stepPinnedUntil(() -> !objectManager.activeObjectsOfType(
                        LbzFinalBoss2Instance.class).isEmpty(),
                0x1000, SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
        LbzFinalBoss2Instance boss = onlyLive(objectManager, LbzFinalBoss2Instance.class);
        assertTrue(wrapper.isDestroyed());
        assertTrue(wrapper.getSlotIndex() < boss.getSlotIndex(),
                "the $CA wrapper allocates its later-slot $CC handoff before deleting itself");
        assertEquals(Sonic3kObjectIds.LBZ_FINAL_BOSS_1,
                GameServices.gameState().getCurrentBossId(),
                "Big Arm inherits the wrapper's nonzero Boss_flag");

        stepPinnedUntil(() -> boss.getCollisionFlags() == 0x0F, 0x400,
                SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
        for (int hit = 1; hit <= 8; hit++) {
            stepPinnedUntil(() -> bossTouchWindow(boss), 0x200,
                    SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
            attackThroughOrdinaryTouch(boss, hit);
            if (hit < 8) {
                stepPinnedUntil(() -> boss.getHitFlashTimerForTest() == 0, 0x80,
                        SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
            }
        }

        stepPinnedUntil(boss::isCapsuleChildSpawnedForTest, 0x600,
                SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
        LbzFinalBoss2EggCapsuleInstance capsule = onlyLive(
                objectManager, LbzFinalBoss2EggCapsuleInstance.class);
        assertTrue(GameServices.gameState().isEndOfLevelActive(),
                "Boss_LoadEggCapsuleAndAnimals must set _unkFAA8 before allocating the later capsule slot");
        assertFalse(GameServices.water().isDynamicWaterLocked(Sonic3kZoneIds.ZONE_LBZ, 1));
        assertFalse(boss.isCapsuleReleasedForTest());
        assertTrue(capsule.getSlotIndex() > boss.getSlotIndex(),
                "the capsule writer must execute after the retained root poll");

        for (int frame = 0; frame < 8 && !capsule.traceDebugDetails().contains("o=1"); frame++) {
            AbstractPlayableSprite player = fixture.sprite();
            player.setCentreX((short) (capsule.getX() + 1));
            // Use the inclusive top of Check_PlayerInRange. This is inside the
            // native button trigger rectangle but just outside the solid
            // overlap, so the production solid checkpoint does not consume
            // the upward y_vel before loc_86770 samples it.
            player.setCentreYPreserveSubpixel((short) (capsule.getY() + 0x08));
            player.setXSpeed((short) 0);
            player.setYSpeed((short) -0x100);
            player.setGSpeed((short) 0);
            player.setAir(true);
            player.setAnimationId(Sonic3kAnimationIds.ROLL);
            player.setObjectControlled(true);
            fixture.stepIdleFrames(1);
        }
        assertTrue(capsule.traceDebugDetails().contains("o=1"),
                "a real upward Knuckles button hit must open the route-8 capsule: "
                        + capsule.traceDebugDetails()
                        + " player=" + fixture.sprite().getCode()
                        + String.format(" (%04X,%04X) yv=%04X anim=%02X air=%s",
                        fixture.sprite().getCentreX() & 0xFFFF,
                        fixture.sprite().getCentreY() & 0xFFFF,
                        fixture.sprite().getYSpeed() & 0xFFFF,
                        fixture.sprite().getAnimationId(), fixture.sprite().getAir())
                        + String.format(" capsule=(%04X,%04X)", capsule.getX(), capsule.getY()));

        boolean observedLockWhileResultsActive = false;
        AbstractObjectInstance results = null;
        for (int frame = 0; frame < 4000 && GameServices.gameState().isEndOfLevelActive(); frame++) {
            pinPlayer(CAMERA_X + 0x50, SAFE_PLAYER_Y, false);
            fixture.stepIdleFrames(1);
            if (GameServices.water().isDynamicWaterLocked(Sonic3kZoneIds.ZONE_LBZ, 1)) {
                observedLockWhileResultsActive = true;
                assertFalse(boss.isCapsuleReleasedForTest(),
                        "active _unkFAA8 must keep loc_7473A waiting after the capsule writes _unkFAA2");
            }
            if (results == null) {
                results = objectManager.getActiveObjects().stream()
                        .filter(S3kResultsScreenObjectInstance.class::isInstance)
                        .map(AbstractObjectInstance.class::cast)
                        .findFirst().orElse(null);
            }
        }
        assertTrue(observedLockWhileResultsActive,
                "the capsule must cross Camera_X-$60 before the results owner clears _unkFAA8");
        assertNotNull(results, "the capsule must allocate the production results object");
        assertTrue(results.getSlotIndex() > boss.getSlotIndex(),
                "the results writer must execute after the retained root poll");
        assertFalse(GameServices.gameState().isEndOfLevelActive());
        assertTrue(GameServices.water().isDynamicWaterLocked(Sonic3kZoneIds.ZONE_LBZ, 1));
        assertFalse(boss.isCapsuleReleasedForTest(),
                "the later results slot clear is visible only on the next root dispatch");

        pinPlayer(CAMERA_X + 0x50, SAFE_PLAYER_Y, false);
        fixture.stepIdleFrames(1);
        assertTrue(boss.isCapsuleReleasedForTest());

        stepPinnedUntil(() -> !boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR).isEmpty(),
                0x400, CAMERA_X + 0x50, SAFE_PLAYER_Y, false);
        EscapeFloorChild floor = (EscapeFloorChild) boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR).getFirst();
        assertEquals(0x16, floor.mappingFrameForTest());
        assertTrue(boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD).stream()
                .map(LbzFinalBoss2Instance.RobotnikHead4Child.class::cast)
                .anyMatch(LbzFinalBoss2Instance.RobotnikHead4Child::usesEggRoboMappingForTest));

        fixture.camera().setMaxX((short) 0x5000);
        fixture.camera().setMinY((short) 0x0200);
        fixture.camera().setMaxY((short) 0x1200);
        fixture.camera().setMaxYTarget((short) 0x1200);
        stepPinnedUntil(floor::isSettledForTest, 0x400,
                0x4400, 0x0100, false);
        Sonic3kLBZEvents lbzEvents = ((Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider()).getLbzEvents();
        assertTrue(lbzEvents.isPostTitleAct2SizeChangeActiveForTest());
        assertArrayEquals(new int[]{0x6000, 0, 0x1000},
                lbzEvents.postTitleAct2TargetsForTest());
        assertArrayEquals(new int[]{0x4000, 0x4000, 0x8000},
                lbzEvents.postTitleAct2WorkerAccumulatorsForTest(),
                "all three later-slot workers execute once in the settlement pass");
        assertEquals(0x1000, fixture.camera().getMaxYTarget() & 0xFFFF);
        assertEquals(0x5000, fixture.camera().getMaxX() & 0xFFFF,
                "loc_74DA4 stores max X; it does not snap the current bound");
        assertEquals(0x0200, fixture.camera().getMinY() & 0xFFFF,
                "loc_74DA4 stores min Y; it does not snap the current bound");
        assertEquals(0x11FE, fixture.camera().getMaxY() & 0xFFFF,
                "only the ordinary later camera phase eases current max Y toward the literal target");

        EscapeExplosionEmitterChild firstEmitter = null;
        BigArmExplosionControllerChild firstEmitterController = null;
        int qualifiedEmitterAttempts = 0;
        int previousEmitterCounter = floor.emitterCounterForTest();
        for (int frame = 0; frame < 0x300 && !floor.isDestroyed(); frame++) {
            pinPlayer(0x4400, 0x0100, false);
            fixture.stepIdleFrames(1);
            int currentEmitterCounter = floor.emitterCounterForTest();
            if (currentEmitterCounter != previousEmitterCounter) {
                if (currentEmitterCounter != 0xFF) {
                    qualifiedEmitterAttempts++;
                }
                previousEmitterCounter = currentEmitterCounter;
            }
            if (firstEmitter == null) {
                List<Object> emitters = boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ESCAPE_EXPLOSION_EMITTER);
                if (!emitters.isEmpty()) {
                    firstEmitter = (EscapeExplosionEmitterChild) emitters.getFirst();
                }
            }
            if (firstEmitter != null && firstEmitterController == null) {
                firstEmitterController = firstEmitter.explosionControllerForTest();
            }
        }
        assertEquals(127, qualifiedEmitterAttempts,
                "$7F qualifying-entry pre-decrement attempts exactly $7E..$00");
        assertTrue(boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR).isEmpty(),
                "the next qualifying decrement to $FF deletes the floor owner");
        int successfulEmitters = boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ESCAPE_EXPLOSION_EMITTER);
        assertTrue(successfulEmitters > 0 && successfulEmitters <= qualifiedEmitterAttempts,
                "successful emitter children remain bounded by SST availability");
        assertNotNull(firstEmitterController);
        assertEquals(0x80, firstEmitterController.counterForTest(),
                "subtype-4's signed-negative byte must never count down");
        assertTrue(firstEmitterController.isDestroyed(),
                "the emitter's $60 expiry/bit-5 signal is the controller teardown owner");

        pinPlayer(0x4510, 0x0100, false);
        fixture.stepIdleFrames(1);
        assertTrue(fixture.sprite().isObjectControlled());
        assertTrue(fixture.sprite().isObjectMappingFrameControl());
        int initialCarriedFrame = fixture.sprite().getMappingFrame();
        fixture.stepIdleFrames(11);
        assertEquals(initialCarriedFrame, fixture.sprite().getMappingFrame(),
                "anim_frame starts at zero, so the first 11-entry reload selects $8C again");
        fixture.stepIdleFrames(11);
        assertEquals(0x8D, fixture.sprite().getMappingFrame(),
                "the second 11-entry reload selects external frame $8D");
        fixture.stepIdleFrames(11);
        assertEquals(0x8C, fixture.sprite().getMappingFrame(),
                "later reloads alternate $8C/$8D every 11 entries");

        for (int frame = 0; frame < 0x200 && !GameServices.level().hasPendingLevelExit(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(GameServices.level().consumeZoneActRequest());
        assertEquals(Sonic3kZoneIds.ZONE_MHZ, GameServices.level().getRequestedZone());
        assertEquals(0, GameServices.level().getRequestedAct());
        assertTrue(GameServices.level().isLevelInactiveForTransition());
    }

    @Test
    void routeRequiresWrapperAndOrdinaryTouchOwnership() {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        fixture.stepIdleFrames(8);
        assertTrue(objectManager.activeObjectsOfType(LbzFinalBoss2Instance.class).isEmpty(),
                "without the $CA route wrapper, an ordinary manager run cannot invent $CC");

        LbzFinalBoss1Instance wrapper = spawnFinalBoss1ThroughRegistry(objectManager);
        fixture.stepIdleFrames(1);
        stepPinnedUntil(() -> wrapper.getCollisionFlags() == 0x0F, 0x40,
                SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
        for (int hit = 1; hit <= 9; hit++) {
            stepPinnedUntil(() -> wrapperTouchWindow(wrapper), 0x1000,
                    SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
            attackThroughOrdinaryTouch(wrapper, hit);
            if (hit < 9) {
                stepPinnedUntil(() -> wrapper.getCollisionFlags() == 0x0F, 0x100,
                        SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
            }
        }
        stepPinnedUntil(() -> !objectManager.activeObjectsOfType(
                        LbzFinalBoss2Instance.class).isEmpty(),
                0x1000, SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
        LbzFinalBoss2Instance boss = onlyLive(objectManager, LbzFinalBoss2Instance.class);
        stepPinnedUntil(() -> boss.getCollisionFlags() == 0x0F, 0x400,
                SAFE_PLAYER_X, SAFE_PLAYER_Y, false);
        assertTrue(wrapper.getSlotIndex() < boss.getSlotIndex());

        for (int pass = 0; pass < 3; pass++) {
            pinNonAttackingAt(boss.getCentreX(), boss.getCentreY());
            fixture.stepIdleFrames(1);
            assertEquals(8, boss.getCollisionProperty(),
                    "overlap without a native attack state must not decrement Big Arm HP");
        }

        attackThroughOrdinaryTouch(boss, 1);
        assertEquals(7, boss.getCollisionProperty(),
                "ObjectTouchResponseController must deliver exactly one ordinary attack");
    }

    @Test
    void nativeEscapePrioritiesVisibilityAndFloorAnimationBoundary() throws Exception {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        LbzFinalBoss2Instance boss = objectManager.createDynamicObject(
                () -> new LbzFinalBoss2Instance(new ObjectSpawn(
                        0x44A0, 0x0780, Sonic3kObjectIds.LBZ_FINAL_BOSS_2,
                        0, 0, false, 0)));
        fixture.stepIdleFrames(1);
        setBooleanField(boss, "artTileHigh", true);

        LbzFinalBoss2Instance.DefeatDebrisChild debris = createChild(
                objectManager, LbzFinalBoss2Instance.DefeatDebrisChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class, int.class},
                boss, 0, 0, 0);
        LbzFinalBoss2Instance.DefeatFollowVisualChild follow = createChild(
                objectManager, LbzFinalBoss2Instance.DefeatFollowVisualChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class},
                boss, 0, 0);
        LbzFinalBoss2Instance.RobotnikShipFlameChild flame = createChild(
                objectManager, LbzFinalBoss2Instance.RobotnikShipFlameChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class}, boss);
        EscapeFloorChild floor = createChild(
                objectManager, EscapeFloorChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class},
                boss, 0, 0);
        LbzFinalBoss2Instance.EscapeFloorExplosionChild explosion = createChild(
                objectManager, LbzFinalBoss2Instance.EscapeFloorExplosionChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class, EscapeFloorChild.class,
                        int.class, int.class, int.class},
                boss, floor, 0, 0, 0);

        assertEquals(2, debris.getPriorityBucket());
        assertEquals(4, follow.getPriorityBucket());
        assertEquals(5, flame.getPriorityBucket());
        assertEquals(6, floor.getPriorityBucket());
        assertEquals(1, explosion.getPriorityBucket());
        assertTrue(debris.isHighPriority());
        assertTrue(follow.isHighPriority());
        assertTrue(flame.isHighPriority());
        assertTrue(floor.isHighPriority());
        assertFalse(explosion.isHighPriority(),
                "ObjDat_BossExplosionHitbox retains low art priority");

        RenderOnlyServices render = new RenderOnlyServices();
        floor.setServices(render);
        floor.appendRenderCommands(new ArrayList<>());
        verify(render.floorRenderer, times(1)).drawFrameIndex(
                0x16, floor.getX(), floor.getY(), false, false, 1);

        clearInvocations(render.floorRenderer);
        setBooleanField(floor, "emitterRoutineDispatched", true);
        floor.appendRenderCommands(new ArrayList<>());
        verify(render.floorRenderer, times(1)).drawFrameIndex(
                0x16, floor.getX(), floor.getY(), false, false, 1);

        explosion.setServices(render);
        explosion.update(0, fixture.sprite());
        for (int ownEntry = 1; ownEntry <= 25; ownEntry++) {
            explosion.update(ownEntry, fixture.sprite());
        }
        assertTrue(explosion.isAnimatingForTest());
        assertEquals(0, explosion.rawCursorForTest(),
                "BossExplosionHitbox_StartAnim changes only the callback on wait expiry");
        assertEquals(0, explosion.mappingFrameForTest());
        assertEquals(0, explosion.rawTimerForTest());
        explosion.appendRenderCommands(new ArrayList<>());
        verify(render.explosionRenderer, never()).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());

        explosion.update(26, fixture.sprite());
        assertEquals(2, explosion.rawCursorForTest());
        assertEquals(0, explosion.mappingFrameForTest());
        assertEquals(1, explosion.rawTimerForTest());
        explosion.appendRenderCommands(new ArrayList<>());
        verify(render.explosionRenderer, times(1)).drawFrameIndex(
                0, explosion.getX(), explosion.getY(), false, false);

        for (int ownEntry = 27; ownEntry < 0x80 && !explosion.isDestroyed(); ownEntry++) {
            explosion.update(ownEntry, fixture.sprite());
        }
        assertTrue(explosion.isDestroyed(),
                "the ROM raw $F4 callback must independently finish the floor explosion");
    }

    @Test
    void floorExplosionTerminalEntryDrawsTouchesAndDefersRemoval() throws Exception {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        LbzFinalBoss2Instance boss = objectManager.createDynamicObject(
                () -> new LbzFinalBoss2Instance(new ObjectSpawn(
                        0x44A0, 0x0780, Sonic3kObjectIds.LBZ_FINAL_BOSS_2,
                        0, 0, false, 0)));
        fixture.stepIdleFrames(1);
        EscapeFloorChild floor = recordBossChild(boss,
                LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR,
                createChild(objectManager, EscapeFloorChild.class,
                        new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class},
                        boss, 0, 0));
        LbzFinalBoss2Instance.EscapeFloorExplosionChild explosion = recordBossChild(
                boss, LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR_EXPLOSION,
                createChild(objectManager,
                        LbzFinalBoss2Instance.EscapeFloorExplosionChild.class,
                        new Class<?>[]{LbzFinalBoss2Instance.class, EscapeFloorChild.class,
                                int.class, int.class, int.class},
                        boss, floor, 0, 0, 0));
        mutableListField(floor, "explosions").add(explosion);
        int slot = explosion.getSlotIndex();
        int oldMapping = explosion.mappingFrameForTest();
        boolean observedAnimationFrame = false;

        for (int entry = 0; entry < 0x80; entry++) {
            oldMapping = explosion.mappingFrameForTest();
            explosion.update(entry, fixture.sprite());
            observedAnimationFrame |= explosion.rawCursorForTest() != 0;
            if (observedAnimationFrame && explosion.isAnimatingForTest()
                    && explosion.rawCursorForTest() == 0) {
                break;
            }
        }

        assertFalse(explosion.isDestroyed(),
                "$F4 installs Go_Delete_Sprite but retains the floor hitbox SST");
        assertEquals(slot, explosion.getSlotIndex());
        assertEquals(oldMapping, explosion.mappingFrameForTest());
        assertEquals(0, explosion.rawTimerForTest());
        assertTrue(mutableListField(floor, "explosions").contains(explosion));
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR_EXPLOSION).contains(explosion));
        assertEquals(0, explosion.getCollisionFlags(),
                "the ordinary touch-list tail remains active with the source-cleared collision byte");

        RenderOnlyServices render = new RenderOnlyServices();
        explosion.setServices(render);
        explosion.appendRenderCommands(new ArrayList<>());
        verify(render.explosionRenderer, times(1)).drawFrameIndex(
                oldMapping, explosion.getX(), explosion.getY(), false, false);

        explosion.update(0x80, fixture.sprite());
        assertTrue(explosion.isDestroyed());
        assertTrue(mutableListField(floor, "explosions").isEmpty());
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR_EXPLOSION).isEmpty());
    }

    @Test
    void emitterAndControllersDeferGoDeleteAcrossLaterSlots() throws Exception {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        LbzFinalBoss2Instance boss = objectManager.createDynamicObject(
                () -> new LbzFinalBoss2Instance(new ObjectSpawn(
                        0x44A0, 0x0780, Sonic3kObjectIds.LBZ_FINAL_BOSS_2,
                        0, 0, false, 0)));
        fixture.stepIdleFrames(1);
        EscapeFloorChild floor = recordBossChild(boss,
                LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR,
                createChild(objectManager, EscapeFloorChild.class,
                        new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class},
                        boss, 0, 0));
        EscapeExplosionEmitterChild emitter = recordBossChild(boss,
                LbzFinalBoss2Instance.ChildKind.ESCAPE_EXPLOSION_EMITTER,
                createChild(objectManager, EscapeExplosionEmitterChild.class,
                        new Class<?>[]{LbzFinalBoss2Instance.class, EscapeFloorChild.class},
                        boss, floor));
        mutableListField(floor, "emitters").add(emitter);
        emitter.update(0, fixture.sprite());
        BigArmExplosionControllerChild controller = emitter.explosionControllerForTest();
        assertNotNull(controller);
        setIntField(emitter, "waitTimer", 0);
        int emitterSlot = emitter.getSlotIndex();
        int controllerSlot = controller.getSlotIndex();

        emitter.update(1, fixture.sprite());
        assertTrue(emitter.controllerStopSignalForTest());
        assertFalse(emitter.isDestroyed(), "loc_74E70 only installs Go_Delete_Sprite");
        assertTrue(mutableListField(floor, "emitters").contains(emitter));
        controller.update(1, fixture.sprite());
        assertFalse(controller.isDestroyed(),
                "the later controller slot independently installs its pending delete");
        assertEquals(emitterSlot, emitter.getSlotIndex());
        assertEquals(controllerSlot, controller.getSlotIndex());

        int emissions = controller.emissionCountForTest();
        emitter.update(2, fixture.sprite());
        controller.update(2, fixture.sprite());
        assertTrue(emitter.isDestroyed());
        assertTrue(controller.isDestroyed());
        assertSame(controller, emitter.explosionControllerForTest(),
                "controller deletion must not mutate its native parent shell");
        assertEquals(emissions, controller.emissionCountForTest());
        assertTrue(mutableListField(floor, "emitters").isEmpty());
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.ESCAPE_EXPLOSION_EMITTER).isEmpty());

        BigArmExplosionControllerChild rootController = recordBossChild(boss,
                LbzFinalBoss2Instance.ChildKind.DEFEAT_EXPLOSION_CONTROLLER,
                createChild(objectManager, BigArmExplosionControllerChild.class,
                        new Class<?>[]{LbzFinalBoss2Instance.class}, boss));
        setIntField(boss, "flags", readIntField(boss, "flags") | (1 << 5));
        rootController.update(3, fixture.sprite());
        assertFalse(rootController.isDestroyed(),
                "Obj_WaitForParent installs the root controller's delete callback first");
        rootController.update(4, fixture.sprite());
        assertTrue(rootController.isDestroyed());
    }

    @Test
    void rootEscapeDrawsThroughFloorWaitExpiryAndStopsAtShipCrossing() throws Exception {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        LbzFinalBoss2Instance boss = objectManager.createDynamicObject(
                () -> new LbzFinalBoss2Instance(new ObjectSpawn(
                        0x44A0, 0x0780, Sonic3kObjectIds.LBZ_FINAL_BOSS_2,
                        0, 0, false, 0)));
        fixture.stepIdleFrames(1);

        RenderOnlyServices render = new RenderOnlyServices();
        boss.setServices(render);
        setBooleanField(boss, "defeatStarted", true);
        setBooleanField(boss, "rootHidden", false);

        setEnumField(boss, "defeatStage", "SHIP_RISE");
        setIntField(boss, "y", CAMERA_Y + 0x20);
        boss.update(0, fixture.sprite());
        boss.appendRenderCommands(new ArrayList<>());
        verify(render.shipRenderer, times(1)).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(0));

        clearInvocations(render.shipRenderer);
        setEnumField(boss, "defeatStage", "FLOOR_WAIT");
        setIntField(boss, "defeatTimer", 1);
        boss.update(1, fixture.sprite());
        boss.appendRenderCommands(new ArrayList<>());
        verify(render.shipRenderer, times(1)).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(0));

        clearInvocations(render.shipRenderer);
        setIntField(boss, "defeatTimer", 0);
        boss.update(2, fixture.sprite());
        boss.appendRenderCommands(new ArrayList<>());
        verify(render.shipRenderer, times(1)).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(0));

        clearInvocations(render.shipRenderer);
        setEnumField(boss, "defeatStage", "SHIP_ESCAPE");
        setIntField(boss, "x", CAMERA_X + 0x1BF);
        setIntField(boss, "xVel", 0);
        boss.update(3, fixture.sprite());
        boss.appendRenderCommands(new ArrayList<>());
        verify(render.shipRenderer, times(1)).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(0));

        clearInvocations(render.shipRenderer);
        setIntField(boss, "x", CAMERA_X + 0x1C0);
        boss.update(4, fixture.sprite());
        boss.appendRenderCommands(new ArrayList<>());
        verify(render.shipRenderer, never()).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt());
        assertTrue(readBooleanField(boss, "rootHidden"));

        setEnumField(boss, "defeatStage", "WALK_TO_FALL");
        boss.update(5, fixture.sprite());
        boss.appendRenderCommands(new ArrayList<>());
        verify(render.shipRenderer, never()).drawFrameIndex(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void escapeMotionAndCarrierPreserveFullLowWords() throws Exception {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        LbzFinalBoss2Instance boss = objectManager.createDynamicObject(
                () -> new LbzFinalBoss2Instance(new ObjectSpawn(
                        0x44A0, 0x0780, Sonic3kObjectIds.LBZ_FINAL_BOSS_2,
                        0, 0, false, 0)));
        fixture.stepIdleFrames(1);

        setIntField(boss, "xSub", 0x1357);
        setIntField(boss, "ySub", 0x2468);
        setBooleanField(boss, "defeatStarted", true);
        setEnumField(boss, "defeatStage", "WALK_TO_FALL");
        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0x4510);
        player.setCentreYPreserveSubpixel((short) 0x0400);
        player.setSubpixelRaw(0xCAFE, 0xBEEF);

        boss.update(0, player);
        assertEquals(0x4510, boss.getCentreX());
        assertEquals(0x0400, boss.getCentreY());
        assertEquals(0x1357, readIntField(boss, "xSub"));
        assertEquals(0x2468, readIntField(boss, "ySub"));
        assertTrue(player.isObjectControlled(), "$83 retains object-owner bit 7");
        assertTrue(player.isObjectMappingFrameControl(), "$83 retains external-frame bit 1");
        assertTrue(player.isControlLocked(), "$83 retains movement-suppression bit 0");
        assertEquals(0x8C, player.getMappingFrame());
        assertEquals(0xCAFE, player.getXSubpixelRaw());
        assertEquals(0xBEEF, player.getYSubpixelRaw());

        boss.update(1, player);
        assertEquals(0x4512, boss.getCentreX(), "signed 8.8 X velocity shifts by eight");
        assertEquals(0x03FC, boss.getCentreY(), "signed 8.8 Y velocity shifts by eight");
        assertEquals(0x1357, readIntField(boss, "xSub"));
        assertEquals(0x2468, readIntField(boss, "ySub"));
        assertEquals(boss.getCentreX(), player.getCentreX());
        assertEquals(boss.getCentreY(), player.getCentreY());
        assertEquals(0xCAFE, player.getXSubpixelRaw());
        assertEquals(0xBEEF, player.getYSubpixelRaw());

        LbzFinalBoss2Instance.DefeatDebrisChild debris = createChild(
                objectManager, LbzFinalBoss2Instance.DefeatDebrisChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class, int.class},
                boss, 0, 0, 0);
        setIntField(debris, "xSub", 0x1111);
        setIntField(debris, "ySub", 0x2222);
        int debrisX = debris.getX();
        int debrisY = debris.getY();
        debris.update(2, player);
        debris.update(3, player);
        assertEquals((debrisX - 1) & 0xFFFF, debris.getX());
        assertEquals((debrisY - 1) & 0xFFFF, debris.getY());
        assertEquals(0x1111, readIntField(debris, "xSub"));
        assertEquals(0x2222, readIntField(debris, "ySub"));

        EscapeFloorChild floor = createChild(
                objectManager, EscapeFloorChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class},
                boss, 0, 0);
        setEnumField(floor, "stage", "FALL");
        setIntField(floor, "xSub", 0x3333);
        setIntField(floor, "ySub", 0x4444);
        setIntField(floor, "yVel", -0x100);
        int floorX = floor.getX();
        int floorY = floor.getY();
        floor.update(4, player);
        assertEquals(floorX, floor.getX());
        assertEquals((floorY - 1) & 0xFFFF, floor.getY());
        assertEquals(0x3333, readIntField(floor, "xSub"));
        assertEquals(0x4444, readIntField(floor, "ySub"));
    }

    private void stepPinnedUntil(BooleanSupplier condition, int limit,
                                 int playerX, int playerY, boolean air) {
        for (int frame = 0; frame < limit && !condition.getAsBoolean(); frame++) {
            pinPlayer(playerX, playerY, air);
            fixture.stepIdleFrames(1);
        }
        assertTrue(condition.getAsBoolean(), "production route did not reach the expected state within " + limit + " frames");
    }

    private LbzFinalBoss1Instance spawnFinalBoss1ThroughRegistry(ObjectManager objectManager) {
        ObjectRegistry registry = GameServices.module().createObjectRegistry();
        ObjectSpawn spawn = new ObjectSpawn(
                0x44A0, CAMERA_Y + 0x58, Sonic3kObjectIds.LBZ_FINAL_BOSS_1,
                0, 0, false, 0);
        return objectManager.createDynamicObject(() -> assertInstanceOf(
                LbzFinalBoss1Instance.class, registry.create(spawn)));
    }

    private static <T extends AbstractObjectInstance> T createChild(
            ObjectManager objectManager, Class<T> type, Class<?>[] parameterTypes,
            Object... arguments) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return objectManager.createDynamicObject(() -> {
            try {
                return constructor.newInstance(arguments);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    private static <T extends LbzFinalBoss2Instance.BossChild> T recordBossChild(
            LbzFinalBoss2Instance boss, LbzFinalBoss2Instance.ChildKind kind, T child)
            throws Exception {
        Method method = LbzFinalBoss2Instance.class.getDeclaredMethod(
                "recordChild", LbzFinalBoss2Instance.ChildKind.class,
                LbzFinalBoss2Instance.BossChild.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        T result = (T) method.invoke(boss, kind, child);
        assertNotNull(result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> mutableListField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return (List<Object>) field.get(target);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int readIntField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setBooleanField(Object target, String name, boolean value)
            throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static boolean readBooleanField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnumField(Object target, String name, String value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, Enum.valueOf((Class<? extends Enum>) field.getType(), value));
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the concrete child/base-object hierarchy.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private void attackThroughOrdinaryTouch(
            com.openggf.level.objects.TouchResponseProvider target, int hitNumber) {
        ObjectInstance instance = assertInstanceOf(ObjectInstance.class, target);
        ObjectManager manager = GameServices.level().getObjectManager();
        manager.getTouchResponseDebugState().setEnabled(true);
        int before = target.getCollisionProperty();
        pinAttackingAt(instance.getX(), instance.getY());
        fixture.stepIdleFrames(1);
        assertEquals(before - 1, target.getCollisionProperty(),
                "ordinary touch hit " + hitNumber + " must decrement exactly once; player="
                        + String.format("(%04X,%04X)", fixture.sprite().getCentreX() & 0xFFFF,
                        fixture.sprite().getCentreY() & 0xFFFF)
                        + " target=" + String.format("(%04X,%04X)", instance.getX(), instance.getY())
                        + " hits=" + manager.getTouchResponseDebugState().getHits());
    }

    private void pinAttackingAt(int x, int y) {
        AbstractPlayableSprite player = fixture.sprite();
        GameServices.level().getLevelGamestate().setRings(1);
        player.setDead(false);
        player.setObjectRoutineOverride(null);
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setHurt(false);
        player.setInvulnerableFrames(0);
        player.setInvincibleFrames(2);
        player.setCentreX((short) x);
        player.setCentreYPreserveSubpixel((short) y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAir(true);
        player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
    }

    private void pinNonAttackingAt(int x, int y) {
        AbstractPlayableSprite player = fixture.sprite();
        GameServices.level().getLevelGamestate().setRings(1);
        player.setDead(false);
        player.setObjectRoutineOverride(null);
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setHurt(false);
        player.setInvulnerableFrames(0);
        player.setInvincibleFrames(0);
        player.setCentreX((short) x);
        player.setCentreYPreserveSubpixel((short) y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAir(false);
        player.setAnimationId(Sonic3kAnimationIds.WALK.id());
    }

    private boolean wrapperTouchWindow(LbzFinalBoss1Instance wrapper) {
        int delta = (short) (wrapper.getCentreY() - CAMERA_Y);
        return delta >= -0x70 && delta <= 0xC0;
    }

    private boolean bossTouchWindow(LbzFinalBoss2Instance boss) {
        int dx = (short) (boss.getCentreX() - CAMERA_X);
        int dy = (short) (boss.getCentreY() - CAMERA_Y);
        return dx >= 0x10 && dx <= 0x130 && dy >= -0x40 && dy <= 0x100;
    }

    private void pinPlayer(int x, int y, boolean air) {
        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) x);
        player.setCentreYPreserveSubpixel((short) y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAir(air);
    }

    private static <T extends ObjectInstance> T onlyLive(ObjectManager objectManager, Class<T> type) {
        List<T> objects = objectManager.activeObjectsOfType(type).stream()
                .filter(object -> !((AbstractObjectInstance) object).isDestroyed())
                .toList();
        assertEquals(1, objects.size(), "expected one live " + type.getSimpleName());
        return objects.getFirst();
    }

    private static final class RenderOnlyServices extends StubObjectServices {
        private final Camera camera = new Camera();
        private final ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        private final PatternSpriteRenderer floorRenderer = mock(PatternSpriteRenderer.class);
        private final PatternSpriteRenderer explosionRenderer = mock(PatternSpriteRenderer.class);
        private final PatternSpriteRenderer shipRenderer = mock(PatternSpriteRenderer.class);

        private RenderOnlyServices() {
            camera.setX((short) CAMERA_X);
            camera.setY((short) CAMERA_Y);
            when(floorRenderer.isReady()).thenReturn(true);
            when(explosionRenderer.isReady()).thenReturn(true);
            when(shipRenderer.isReady()).thenReturn(true);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.LBZ_FINAL_BOSS_1))
                    .thenReturn(floorRenderer);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP))
                    .thenReturn(shipRenderer);
            when(renderManager.getBossExplosionRenderer()).thenReturn(explosionRenderer);
        }

        @Override
        public Camera camera() {
            return camera;
        }

        @Override
        public ObjectRenderManager renderManager() {
            return renderManager;
        }
    }
}
