package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kLBZEvents;
import com.openggf.game.sonic3k.objects.SongFadeTransitionInstance;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2EggCapsuleInstance;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.ArmControllerChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.ArmAttachmentChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.ArmKinematicJointChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.ArmOuterCollisionChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.ArmSegmentChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.ArmVisualJointChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.BigArmExplosionControllerChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.BossChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.EscapeExplosionEmitterChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.EscapeFloorExplosionChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.EscapeFloorChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.GrabOwnerChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.LandingCollisionChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.DefeatDebrisChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.DefeatFollowVisualChild;
import com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance.RobotnikShipFlameChild;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact-ID graph and two-signal rewind coverage for S3KL object {@code $CC}. */
@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(SingletonResetExtension.class)
class TestS3kLbzFinalBoss2GraphRewind {
    private static final int CAMERA_X = 0x4300;
    private static final int CAMERA_Y = 0x0328;

    private HeadlessTestFixture fixture;
    private ObjectManager objectManager;
    private RewindRegistry registry;

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
        pinPlayer(0x42A0, 0x03E0, false);
        objectManager = GameServices.level().getObjectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        registry = fixture.gameplayMode().getRewindRegistry();
        assertNotNull(registry);
    }

    @AfterEach
    void tearDown() {
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void articulatedGrabGraphRestoresFreshWithExactIdsSlotsAndLinks() throws Exception {
        LbzFinalBoss2Instance sourceBoss = spawnBoss();
        stepUntil(() -> sourceBoss.getCollisionFlags() == 0x0F, 0x400);
        stepUntil(() -> sourceBoss.getRoutineForTest() == 0x0A, 0x200);

        ArmControllerChild sourceController = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_GRAPH, ArmControllerChild.class);
        List<ArmSegmentChild> sourceSegments = children(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT, ArmSegmentChild.class);
        ArmKinematicJointChild sourceJoint = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_JOINT, ArmKinematicJointChild.class);
        GrabOwnerChild sourceGrab = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.GRAB, GrabOwnerChild.class);
        fixture.sprite().setCentreX((short) sourceGrab.getX());
        fixture.sprite().setCentreYPreserveSubpixel((short) sourceGrab.getY());
        fixture.sprite().setXSpeed((short) 0);
        fixture.sprite().setYSpeed((short) 0);
        fixture.sprite().setGSpeed((short) 0);
        fixture.sprite().setAir(true);
        sourceGrab.update(0, fixture.sprite());
        assertTrue(sourceBoss.isGrabActiveForTest());

        List<ObjectInstance> sourceGraph = new ArrayList<>();
        for (Object object : readListField(sourceBoss, "graphChildren")) {
            sourceGraph.add((ObjectInstance) object);
        }
        assertEquals(10, sourceGraph.size(),
                "head, controller, attachment, visual, outer, two segments, joint, grab, landing");
        Map<ObjectInstance, ObjectRefId> ids = idsFor(sourceGraph);
        ObjectRefId bossId = objectId(sourceBoss);
        Map<ObjectRefId, Integer> slots = slotsFor(sourceBoss, ids);
        int sourceRoutine = sourceBoss.getRoutineForTest();
        int sourceControllerAngle = readIntField(sourceController, "angle");

        CompositeSnapshot snapshot = registry.capture();
        registry.restore(snapshot);

        LbzFinalBoss2Instance restoredBoss = objectById(LbzFinalBoss2Instance.class, bossId);
        assertNotSame(sourceBoss, restoredBoss);
        assertSlots(slots);
        assertEquals(sourceRoutine, restoredBoss.getRoutineForTest());
        assertTrue(restoredBoss.isGrabActiveForTest());
        assertEquals(sourceGraph.size(), readListField(restoredBoss, "graphChildren").size());

        for (ObjectInstance source : sourceGraph) {
            ObjectInstance restored = objectById(ids.get(source));
            assertNotSame(source, restored, source.getClass().getSimpleName() + " must restore fresh");
            assertEquals(source.getClass(), restored.getClass());
            if (restored instanceof BossChild) {
                assertSame(restoredBoss, readObjectField(restored, "boss"));
            }
        }

        ArmControllerChild restoredController = onlyChild(
                restoredBoss, LbzFinalBoss2Instance.ChildKind.ARM_GRAPH, ArmControllerChild.class);
        List<ArmSegmentChild> restoredSegments = children(
                restoredBoss, LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT, ArmSegmentChild.class);
        ArmKinematicJointChild restoredJoint = onlyChild(
                restoredBoss, LbzFinalBoss2Instance.ChildKind.ARM_JOINT, ArmKinematicJointChild.class);
        GrabOwnerChild restoredGrab = onlyChild(
                restoredBoss, LbzFinalBoss2Instance.ChildKind.GRAB, GrabOwnerChild.class);
        assertSame(restoredController, readObjectField(restoredBoss, "armController"));
        assertEquals(sourceControllerAngle, readIntField(restoredController, "angle"));
        assertEquals(List.of(0, 1), restoredSegments.stream()
                .map(segment -> readIntUnchecked(segment, "subtype")).toList());
        for (ArmSegmentChild segment : restoredSegments) {
            assertSame(restoredController, readObjectField(segment, "controller"));
        }
        assertSame(restoredController, readObjectField(restoredJoint, "controller"));
        assertSame(restoredController, readObjectField(restoredGrab, "controller"));
        assertSame(fixture.sprite(), readObjectField(restoredGrab, "grabbedPlayer"));
    }

    @Test
    void segmentRawRestartReadsFcAndRoundTripsBeforeOwnEntry() throws Exception {
        LbzFinalBoss2Instance initialBoss = spawnBoss();
        stepUntil(() -> initialBoss.getCollisionFlags() == 0x0F, 0x400);
        LbzFinalBoss2Instance boss = initialBoss;
        ObjectRefId bossId = objectId(boss);

        for (int index = 0; index < 2; index++) {
            boss = objectById(LbzFinalBoss2Instance.class, bossId);
            setBooleanField(boss, "grabActive", false);
            ArmSegmentChild segment = children(
                    boss, LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT,
                    ArmSegmentChild.class).get(index);
            ObjectRefId segmentId = objectId(segment);
            setIntField(segment, "animationIndex", 5);
            setIntField(segment, "animationTimer", 0);
            CompositeSnapshot boundary = registry.capture();

            segment.update(0, fixture.sprite());
            assertEquals(0, readIntField(segment, "animationIndex"));
            assertEquals(9, readIntField(segment, "animationTimer"));
            assertEquals(index == 0 ? 7 : 0x0B, segment.mappingFrameForTest());

            registry.restore(boundary);
            ArmSegmentChild restored = objectById(ArmSegmentChild.class, segmentId);
            restored.update(0, fixture.sprite());
            assertEquals(0, readIntField(restored, "animationIndex"));
            assertEquals(9, readIntField(restored, "animationTimer"));
            assertEquals(index == 0 ? 7 : 0x0B, restored.mappingFrameForTest());
        }
    }

    @Test
    void finalHitPreservesMappingUntilFadeExpiryAndRunsNativeFadeCounters()
            throws Exception {
        SstFiller lowerSlotFiller = objectManager.createDynamicObject(() ->
                new SstFiller(new ObjectSpawn(0, 0, 0x7F,
                        0x70, 0, false, 0)));
        LbzFinalBoss2Instance boss = spawnBoss();
        assertTrue(slotOf(lowerSlotFiller) < slotOf(boss));
        stepUntil(() -> boss.getCollisionFlags() == 0x0F, 0x400);
        for (int hit = 1; hit < 8; hit++) {
            boss.onPlayerAttack(fixture.sprite(), null);
            fixture.stepIdleFrames(1);
            stepUntil(() -> boss.getHitFlashTimerForTest() == 0, 0x80);
        }
        setIntField(boss, "mappingFrame", 8);
        boss.onPlayerAttack(fixture.sprite(), null);
        boss.update(0, fixture.sprite());
        assertEquals(8, boss.getMappingFrameForTest(),
                "the final-hit entry preserves the interrupted fight mapping");

        setIntField(boss, "defeatTimer", 1);
        boss.update(0, fixture.sprite());
        assertEquals(8, boss.getMappingFrameForTest(),
                "a nonexpired Wait_FadeToLevelMusic entry preserves mapping");
        int lowerSlot = slotOf(lowerSlotFiller);
        objectManager.removeDynamicObject(lowerSlotFiller);
        setIntField(boss, "defeatTimer", 0);
        boss.update(0, fixture.sprite());
        assertEquals(5, boss.getMappingFrameForTest());
        assertEquals(119, readIntField(boss, "defeatTimer"));

        SongFadeTransitionInstance fade = onlyLive(SongFadeTransitionInstance.class);
        ObjectRefId fadeId = objectId(fade);
        assertEquals(lowerSlot, slotOf(fade));
        assertTrue(slotOf(fade) < slotOf(boss),
                "AllocateObject may select a free SST slot below the root");
        assertEquals(120, readIntField(fade, "nativeRemaining"));
        CompositeSnapshot allocationBoundary = registry.capture();

        fixture.stepIdleFrames(1);
        fade = objectById(SongFadeTransitionInstance.class, fadeId);
        assertEquals(119, readIntField(fade, "nativeRemaining"));
        registry.restore(allocationBoundary);
        SongFadeTransitionInstance restored = objectById(
                SongFadeTransitionInstance.class, fadeId);
        fixture.stepIdleFrames(1);
        restored = objectById(SongFadeTransitionInstance.class, fadeId);
        assertEquals(119, readIntField(restored, "nativeRemaining"));

        for (int ownEntry = 2; ownEntry <= 120; ownEntry++) {
            restored.update(ownEntry, fixture.sprite());
        }
        assertFalse(restored.isDestroyed());
        assertEquals(0, readIntField(restored, "nativeRemaining"));
        CompositeSnapshot terminalBoundary = registry.capture();
        restored.update(121, fixture.sprite());
        assertTrue(restored.isDestroyed());

        registry.restore(terminalBoundary);
        restored = objectById(SongFadeTransitionInstance.class, fadeId);
        restored.update(121, fixture.sprite());
        assertTrue(restored.isDestroyed());

        objectManager.removeDynamicObject(restored);
        LbzFinalBoss2Instance higherLayoutBoss = spawnBoss();
        setBooleanField(higherLayoutBoss, "initialized", true);
        setBooleanField(higherLayoutBoss, "defeatStarted", true);
        setEnumField(higherLayoutBoss, "defeatStage", "DELAY");
        setIntField(higherLayoutBoss, "defeatTimer", 0);
        setIntField(higherLayoutBoss, "mappingFrame", 8);
        int higherRootSlot = slotOf(higherLayoutBoss);
        fillAllSstSlots();
        SstFiller higherSlotFiller = objectManager.activeObjectsOfType(SstFiller.class)
                .stream()
                .filter(filler -> slotOf(filler) > higherRootSlot)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "test setup needs a filled slot above the second root"));
        int higherSlot = slotOf(higherSlotFiller);
        objectManager.removeDynamicObject(higherSlotFiller);

        higherLayoutBoss.update(0, fixture.sprite());
        SongFadeTransitionInstance higherFade = onlyLive(SongFadeTransitionInstance.class);
        assertEquals(higherSlot, slotOf(higherFade));
        assertTrue(slotOf(higherFade) > higherRootSlot,
                "the native fade contract must not assume a lower SST allocation");
        assertEquals(120, readIntField(higherFade, "nativeRemaining"));
        fixture.stepIdleFrames(1);
        assertEquals(119, readIntField(higherFade, "nativeRemaining"),
                "the higher-slot owner performs its first own dispatch in slot order");
    }

    @Test
    void defeatDebrisControllerAndVisibleExplosionsRestoreByExactId() throws Exception {
        LbzFinalBoss2Instance sourceBoss = spawnBoss();
        defeatThroughProductionHits(sourceBoss);
        BigArmExplosionControllerChild sourceController = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.DEFEAT_EXPLOSION_CONTROLLER,
                BigArmExplosionControllerChild.class);
        DefeatFollowVisualChild sourceFollow = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.DEFEAT_FOLLOW_VISUAL,
                DefeatFollowVisualChild.class);
        List<VisibleExplosionOracle> sourceExplosions = visibleExplosionOracles();
        assertFalse(sourceExplosions.isEmpty());

        ObjectRefId controllerId = objectId(sourceController);
        ObjectRefId followId = objectId(sourceFollow);
        int controllerInterval = sourceController.intervalCounterForTest();
        int controllerEmissions = sourceController.emissionCountForTest();
        GraphRoundTrip restored = roundTripGraph(sourceBoss);

        BigArmExplosionControllerChild restoredController = restored.object(
                controllerId, BigArmExplosionControllerChild.class);
        assertSame(restoredController, readObjectField(restored.boss(), "defeatExplosionController"));
        assertEquals(0x80, restoredController.counterForTest());
        assertEquals(controllerInterval, restoredController.intervalCounterForTest());
        assertEquals(controllerEmissions, restoredController.emissionCountForTest());
        assertVisibleExplosionOraclesRestored(sourceExplosions);
        DefeatFollowVisualChild restoredFollow = restored.object(
                followId, DefeatFollowVisualChild.class);
        assertSame(restored.boss(), readObjectField(restoredFollow, "boss"));

        LbzFinalBoss2Instance debrisBoss = restored.boss();
        stepUntil(() -> debrisBoss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_DEBRIS).size() == 5, 0x100);
        assertEquals(1, debrisBoss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.DEFEAT_FOLLOW_VISUAL).size(),
                "the bit-4 signal entry retains the follow visual's SST");
        assertEquals("GO_DELETE_2", readObjectField(restoredFollow, "pendingDelete").toString());
        fixture.stepIdleFrames(1);
        assertTrue(debrisBoss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.DEFEAT_FOLLOW_VISUAL).isEmpty(),
                "the following own entry removes the pending follow visual");
        List<DefeatDebrisChild> sourceDebris = children(
                debrisBoss, LbzFinalBoss2Instance.ChildKind.DEFEAT_DEBRIS,
                DefeatDebrisChild.class);
        assertEquals(5, sourceDebris.size());
        List<ObjectRefId> debrisIds = idsForList(sourceDebris);
        GraphRoundTrip debrisRoundTrip = roundTripGraph(debrisBoss);

        for (int i = 0; i < debrisIds.size(); i++) {
            DefeatDebrisChild debris = debrisRoundTrip.object(
                    debrisIds.get(i), DefeatDebrisChild.class);
            assertEquals(i * 2, debris.subtypeForTest());
            assertSame(debrisRoundTrip.boss(), readObjectField(debris, "boss"));
        }
    }

    @Test
    void visibleExplosionsRestoreAndOutliveControllerWithoutOwnerEdges() throws Exception {
        LbzFinalBoss2Instance sourceBoss = spawnBoss();
        stepUntil(() -> sourceBoss.getCollisionFlags() == 0x0F, 0x400);
        for (int hit = 1; hit < 8; hit++) {
            sourceBoss.onPlayerAttack(fixture.sprite(), null);
            fixture.stepIdleFrames(1);
            stepUntil(() -> sourceBoss.getHitFlashTimerForTest() == 0, 0x80);
        }

        List<ObjectRefId> originalArticulatedOrder = idsForList(
                articulatedRootInventory(sourceBoss));
        ArmControllerChild articulatedController = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_GRAPH,
                ArmControllerChild.class);
        assertThrows(NoSuchFieldException.class,
                () -> field(articulatedController, "segments"));
        assertThrows(NoSuchFieldException.class,
                () -> field(articulatedController, "joint"));
        assertThrows(NoSuchFieldException.class,
                () -> field(articulatedController, "grabOwner"));
        assertNoArticulatedDeletionCallback(articulatedController);
        ObjectRefId bossId = objectId(sourceBoss);
        sourceBoss.onPlayerAttack(fixture.sprite(), null);
        registry.restore(registry.capture());
        LbzFinalBoss2Instance restoredBoundaryBoss = objectById(
                LbzFinalBoss2Instance.class, bossId);
        assertEquals(originalArticulatedOrder,
                idsForList(articulatedRootInventory(restoredBoundaryBoss)));

        pinPlayer(0x42A0, 0x03E0, false);
        fixture.stepIdleFrames(1);
        assertTrue(restoredBoundaryBoss.isDefeatStartedForTest());
        stepUntil(() -> restoredBoundaryBoss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_DEBRIS).size() == 5, 0x100);

        BigArmExplosionControllerChild sourceController = onlyChild(
                restoredBoundaryBoss, LbzFinalBoss2Instance.ChildKind.DEFEAT_EXPLOSION_CONTROLLER,
                BigArmExplosionControllerChild.class);
        ObjectRefId controllerId = objectId(sourceController);

        // Execute only the root's next native slot. The fight ended above
        // Camera_Y-$40 on this deterministic route, so loc_746F4 publishes bit
        // 5/capsule immediately; the later controller slot has not yet polled it.
        restoredBoundaryBoss.update(0, fixture.sprite());
        assertTrue(restoredBoundaryBoss.isCapsuleChildSpawnedForTest());
        assertFalse(sourceController.isDestroyed());
        List<VisibleExplosionOracle> sourceExplosions = visibleExplosionOracles();
        assertFalse(sourceExplosions.isEmpty());
        assertTrue(readListField(restoredBoundaryBoss, "graphChildren").stream()
                .noneMatch(S3kBossExplosionChild.class::isInstance));
        assertThrows(NoSuchFieldException.class,
                () -> field(sourceController, "liveExplosions"));
        VisibleExplosionOracle survivor = sourceExplosions.stream()
                .min((left, right) -> Integer.compare(left.rawCursor(), right.rawCursor()))
                .orElseThrow();
        GraphRoundTrip restored = roundTripGraph(restoredBoundaryBoss);
        BigArmExplosionControllerChild restoredController = restored.object(
                controllerId, BigArmExplosionControllerChild.class);
        assertVisibleExplosionOraclesRestored(sourceExplosions);

        S3kBossExplosionChild beforeTeardown = objectById(
                S3kBossExplosionChild.class, survivor.id());
        int cursorBeforeTeardown = beforeTeardown.rawCursorForTest();
        int timerBeforeTeardown = beforeTeardown.rawTimerForTest();
        restoredController.update(0, fixture.sprite());
        assertFalse(restoredController.isDestroyed(),
                "the controller's later-slot poll installs Go_Delete_Sprite first");
        assertEquals("GO_DELETE",
                readObjectField(restoredController, "pendingDelete").toString());
        registry.restore(registry.capture());
        restoredController = objectById(
                BigArmExplosionControllerChild.class, controllerId);
        restoredController.update(1, fixture.sprite());
        assertTrue(restoredController.isDestroyed(),
                "Delete_Current_Sprite owns teardown on the following entry");
        pinPlayer(0x42A0, 0x03E0, false);
        fixture.stepIdleFrames(1);

        assertTrue(findObjectById(controllerId).isEmpty(),
                "root bit 5 must delete the later-slot subtype-4 controller");
        S3kBossExplosionChild afterTeardown = objectById(
                S3kBossExplosionChild.class, survivor.id());
        assertEquals(survivor.slot(), slotOf(afterTeardown));
        assertEquals(survivor.x(), afterTeardown.getX());
        assertEquals(survivor.y(), afterTeardown.getY());
        assertFalse(cursorBeforeTeardown == afterTeardown.rawCursorForTest()
                        && timerBeforeTeardown == afterTeardown.rawTimerForTest(),
                "the independently scheduled visible explosion must advance after controller deletion");

        List<VisibleExplosionOracle> afterTeardownExplosions = visibleExplosionOracles();
        registry.restore(registry.capture());
        assertVisibleExplosionOraclesRestored(afterTeardownExplosions);
        S3kBossExplosionChild terminal = objectById(
                S3kBossExplosionChild.class, survivor.id());
        for (int entry = 0; entry < 0x40 && !terminal.pendingDeleteForTest(); entry++) {
            terminal.update(entry, fixture.sprite());
        }
        assertTrue(terminal.pendingDeleteForTest());
        assertFalse(terminal.isDestroyed());
        int terminalSlot = slotOf(terminal);
        int terminalMapping = terminal.mappingFrameForTest();
        assertEquals(0, terminal.rawCursorForTest());
        assertEquals(0, terminal.rawTimerForTest());

        registry.restore(registry.capture());
        terminal = objectById(S3kBossExplosionChild.class, survivor.id());
        assertTrue(terminal.pendingDeleteForTest());
        assertFalse(terminal.isDestroyed());
        assertEquals(terminalSlot, slotOf(terminal));
        assertEquals(terminalMapping, terminal.mappingFrameForTest());
        terminal.update(0, fixture.sprite());
        assertTrue(terminal.isDestroyed());
        fixture.stepIdleFrames(1);
        assertTrue(findObjectById(survivor.id()).isEmpty(),
                "ObjectManager removes the independently owned explosion only after its delete callback");
    }

    @Test
    void sourceFixedArticulatedDefeatDispositionRestoresAndReexecutes() throws Exception {
        LbzFinalBoss2Instance sourceBoss = spawnBoss();
        stepUntil(() -> sourceBoss.getCollisionFlags() == 0x0F, 0x400);
        for (int hit = 1; hit < 8; hit++) {
            sourceBoss.onPlayerAttack(fixture.sprite(), null);
            fixture.stepIdleFrames(1);
            stepUntil(() -> sourceBoss.getHitFlashTimerForTest() == 0, 0x80);
        }

        ArmControllerChild sourceController = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_GRAPH,
                ArmControllerChild.class);
        ArmAttachmentChild sourceAttachment = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_ATTACHMENT,
                ArmAttachmentChild.class);
        ArmVisualJointChild sourceVisual = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_VISUAL,
                ArmVisualJointChild.class);
        ArmOuterCollisionChild sourceOuter = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION,
                ArmOuterCollisionChild.class);
        List<ArmSegmentChild> sourceSegments = children(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT,
                ArmSegmentChild.class);
        ArmKinematicJointChild sourceJoint = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_JOINT,
                ArmKinematicJointChild.class);
        GrabOwnerChild sourceGrab = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.GRAB,
                GrabOwnerChild.class);
        LandingCollisionChild sourceLanding = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_UPPER_COLLISION,
                LandingCollisionChild.class);

        List<ObjectInstance> literalPreHitOrder = List.of(
                sourceController, sourceAttachment, sourceVisual, sourceOuter,
                sourceSegments.get(0), sourceSegments.get(1), sourceJoint,
                sourceGrab, sourceLanding);
        List<Class<?>> literalPreHitTypes = List.of(
                ArmControllerChild.class, ArmAttachmentChild.class,
                ArmVisualJointChild.class, ArmOuterCollisionChild.class,
                ArmSegmentChild.class, ArmSegmentChild.class,
                ArmKinematicJointChild.class, GrabOwnerChild.class,
                LandingCollisionChild.class);
        assertEquals(literalPreHitTypes, literalPreHitOrder.stream()
                .map(Object::getClass).toList());
        List<ObjectRefId> preHitIds = idsForList(literalPreHitOrder);
        Map<ObjectRefId, Integer> originalSlots = new LinkedHashMap<>();
        for (ObjectRefId id : preHitIds) {
            originalSlots.put(id, slotOf(objectById(id)));
        }
        ObjectRefId bossId = objectId(sourceBoss);

        sourceBoss.onPlayerAttack(fixture.sprite(), null);
        sourceBoss.update(0, fixture.sprite());
        assertTrue(sourceBoss.isDefeatStartedForTest());
        assertExactArticulatedInventory(sourceBoss, preHitIds, originalSlots);
        assertFalse(sourceController.isFlickerMoveForTest());
        assertFalse(sourceSegments.get(0).isFlickerMoveForTest());
        assertFalse(sourceSegments.get(1).isFlickerMoveForTest());
        assertFalse(sourceJoint.isFlickerMoveForTest(),
                "the root final-hit slot cannot retroactively run later child callbacks");

        registry.restore(registry.capture());
        LbzFinalBoss2Instance restoredRootBoundary = objectById(
                LbzFinalBoss2Instance.class, bossId);
        assertExactArticulatedInventory(restoredRootBoundary, preHitIds, originalSlots);
        fixture.stepIdleFrames(1);

        List<ObjectRefId> literalPostHitIds = List.of(
                preHitIds.get(0), preHitIds.get(1), preHitIds.get(2),
                preHitIds.get(4), preHitIds.get(5), preHitIds.get(6));
        assertExactArticulatedInventory(restoredRootBoundary, literalPostHitIds, originalSlots);
        assertTrue(findObjectById(preHitIds.get(3)).isEmpty(), "outer deletes on root status 7");
        assertTrue(findObjectById(preHitIds.get(7)).isEmpty(), "grab owner deletes on root status 7");
        assertTrue(findObjectById(preHitIds.get(8)).isEmpty(), "landing owner deletes on root status 7");

        ArmControllerChild controller = objectById(
                ArmControllerChild.class, preHitIds.get(0));
        ArmSegmentChild segment0 = objectById(ArmSegmentChild.class, preHitIds.get(4));
        ArmSegmentChild segment1 = objectById(ArmSegmentChild.class, preHitIds.get(5));
        ArmKinematicJointChild joint = objectById(
                ArmKinematicJointChild.class, preHitIds.get(6));
        assertFlickerState(controller,
                readBooleanField(controller, "hFlip") ? -0x200 : 0x200, -0x200);
        assertFlickerState(segment0,
                readBooleanField(segment0, "hFlip") ? -0x200 : 0x200, -0x200);
        assertFlickerState(segment1,
                readBooleanField(segment1, "hFlip") ? 0x300 : -0x300, -0x200);
        assertFlickerState(joint,
                readBooleanField(joint, "hFlip") ? -0x300 : 0x300, -0x200);
        assertNoArticulatedDeletionCallback(controller);
        DefeatFollowVisualChild follow = onlyChild(
                restoredRootBoundary, LbzFinalBoss2Instance.ChildKind.DEFEAT_FOLLOW_VISUAL,
                DefeatFollowVisualChild.class);
        ObjectRefId followId = objectId(follow);

        List<ObjectRefId> flickerIds = List.of(
                preHitIds.get(0), preHitIds.get(4), preHitIds.get(5), preHitIds.get(6));
        List<ObjectRefId> firstMoveSurvivors = List.copyOf(flickerIds);
        List<ObjectRefId> literalPostMoveIds = List.copyOf(literalPostHitIds);
        CompositeSnapshot flickerBoundary = registry.capture();
        fixture.stepIdleFrames(1);
        Map<ObjectRefId, ChildMotionOracle> firstExecution = childMotionOracles(
                firstMoveSurvivors);
        assertExactArticulatedInventory(
                restoredRootBoundary, literalPostMoveIds, originalSlots);

        registry.restore(flickerBoundary);
        LbzFinalBoss2Instance reexecutedBoss = objectById(
                LbzFinalBoss2Instance.class, bossId);
        fixture.stepIdleFrames(1);
        assertEquals(firstExecution, childMotionOracles(
                firstMoveSurvivors),
                "fresh restore must reproduce the first Obj_FlickerMove entry exactly");
        assertExactArticulatedInventory(reexecutedBoss, literalPostMoveIds, originalSlots);

        setIntField(reexecutedBoss, "defeatTimer", 0);
        reexecutedBoss.update(0, fixture.sprite());
        assertFalse(objectById(ArmAttachmentChild.class, preHitIds.get(1)).isDestroyed());
        assertFalse(objectById(ArmVisualJointChild.class, preHitIds.get(2)).isDestroyed());
        assertFalse(objectById(DefeatFollowVisualChild.class, followId).isDestroyed(),
                "root bit 4 becomes observable only when each later child executes");
        objectById(ArmAttachmentChild.class, preHitIds.get(1)).update(0, fixture.sprite());
        objectById(ArmVisualJointChild.class, preHitIds.get(2)).update(0, fixture.sprite());
        objectById(DefeatFollowVisualChild.class, followId).update(0, fixture.sprite());
        List<ObjectRefId> secondMoveSurvivors = firstMoveSurvivors.stream()
                .filter(id -> sourceFlickerMoveSurvives(
                        assertInstanceOf(BossChild.class, objectById(id))))
                .toList();
        fixture.stepIdleFrames(1);

        List<ObjectRefId> literalPostBit4Ids = secondMoveSurvivors;
        assertExactArticulatedInventory(reexecutedBoss, literalPostBit4Ids, originalSlots);
        assertTrue(findObjectById(preHitIds.get(1)).isEmpty());
        assertTrue(findObjectById(preHitIds.get(2)).isEmpty());
        assertTrue(findObjectById(followId).isEmpty());

        GraphRoundTrip postBit4 = roundTripGraph(reexecutedBoss);
        assertExactArticulatedInventory(postBit4.boss(), literalPostBit4Ids, originalSlots);
    }

    @Test
    void bit4ChildrenRefreshThenDeferRemovalAcrossRestore() throws Exception {
        LbzFinalBoss2Instance sourceBoss = spawnBoss();
        stepUntil(() -> sourceBoss.getCollisionFlags() == 0x0F, 0x400);
        ArmAttachmentChild attachment = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_ATTACHMENT,
                ArmAttachmentChild.class);
        ArmVisualJointChild visual = onlyChild(
                sourceBoss, LbzFinalBoss2Instance.ChildKind.ARM_VISUAL,
                ArmVisualJointChild.class);
        DefeatFollowVisualChild follow = createChild(
                DefeatFollowVisualChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class},
                sourceBoss, 0x14, -0x18);
        follow = recordBossChild(sourceBoss,
                LbzFinalBoss2Instance.ChildKind.DEFEAT_FOLLOW_VISUAL, follow);
        follow.update(0, fixture.sprite());

        sourceBoss.setCentreX(0x4500);
        sourceBoss.setCentreY(0x0700);
        setBooleanField(sourceBoss, "renderXFlip", true);
        setIntField(attachment, "currentX", 0x1111);
        setIntField(attachment, "currentY", 0x2222);
        setIntField(visual, "currentX", 0x3333);
        setIntField(visual, "currentY", 0x4444);
        setIntField(follow, "currentX", 0x5555);
        setIntField(follow, "currentY", 0x6666);

        ObjectRefId bossId = objectId(sourceBoss);
        ObjectRefId attachmentId = objectId(attachment);
        ObjectRefId visualId = objectId(visual);
        ObjectRefId followId = objectId(follow);
        List<ObjectRefId> signalIds = List.of(attachmentId, visualId, followId);
        Map<ObjectRefId, Integer> slots = new LinkedHashMap<>();
        for (ObjectRefId id : signalIds) {
            slots.put(id, slotOf(objectById(id)));
        }
        List<ObjectRefId> preSignalRootOrder = idsForList(
                readListField(sourceBoss, "graphChildren"));

        setIntField(sourceBoss, "flags", readIntField(sourceBoss, "flags") | 0x10);
        attachment.update(1, fixture.sprite());
        visual.update(1, fixture.sprite());
        follow.update(1, fixture.sprite());

        assertFalse(attachment.isDestroyed());
        assertFalse(visual.isDestroyed());
        assertFalse(follow.isDestroyed());
        assertEquals((sourceBoss.getCentreX() - readIntField(attachment, "dx")) & 0xFFFF,
                attachment.getX(), "loc_749BE performs adjusted refresh before the signal");
        assertEquals((sourceBoss.getCentreX() - readIntField(visual, "dx")) & 0xFFFF,
                visual.getX(), "loc_74BAE performs adjusted refresh before the signal");
        assertEquals((sourceBoss.getCentreX() + readIntField(follow, "dx")) & 0xFFFF,
                follow.getX(), "loc_74E24 performs unadjusted refresh before the signal");
        for (ObjectRefId id : signalIds) {
            BossChild child = assertInstanceOf(BossChild.class, objectById(id));
            assertEquals("GO_DELETE_2", readObjectField(child, "pendingDelete").toString());
            assertEquals(slots.get(id).intValue(), slotOf(child));
        }
        assertEquals(preSignalRootOrder, idsForList(readListField(sourceBoss, "graphChildren")),
                "the signal entry retains exact root allocation order");

        registry.restore(registry.capture());
        LbzFinalBoss2Instance restoredBoss = objectById(
                LbzFinalBoss2Instance.class, bossId);
        assertNotSame(sourceBoss, restoredBoss);
        assertEquals(preSignalRootOrder,
                idsForList(readListField(restoredBoss, "graphChildren")));

        for (ObjectRefId id : signalIds) {
            BossChild restored = assertInstanceOf(BossChild.class, objectById(id));
            int x = restored.getX();
            int y = restored.getY();
            restored.update(2, fixture.sprite());
            assertTrue(restored.isDestroyed());
            assertEquals(x, restored.getX(), "delete callback performs no second refresh");
            assertEquals(y, restored.getY(), "delete callback performs no second refresh");
        }
        List<ObjectRefId> expectedSurvivors = preSignalRootOrder.stream()
                .filter(id -> !signalIds.contains(id))
                .toList();
        assertEquals(expectedSurvivors,
                idsForList(readListField(restoredBoss, "graphChildren")),
                "next-entry pruning preserves every surviving root edge in order");
        fixture.stepIdleFrames(1);
        for (ObjectRefId id : signalIds) {
            assertTrue(findObjectById(id).isEmpty());
        }
    }

    @Test
    void fixedPointLowWordsAndTimedGlobalsRoundTripAtNativeBoundaries() throws Exception {
        LbzFinalBoss2Instance sourceBoss = spawnBoss();
        fixture.stepIdleFrames(1);
        ObjectRefId bossId = objectId(sourceBoss);

        DefeatDebrisChild sourceDebris = createChild(
                DefeatDebrisChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class, int.class},
                sourceBoss, 0, 0, 0);
        sourceDebris.update(0, fixture.sprite());
        ObjectRefId debrisId = objectId(sourceDebris);
        EscapeFloorChild sourceFloor = createChild(
                EscapeFloorChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class},
                sourceBoss, 0, 0);
        ObjectRefId floorId = objectId(sourceFloor);
        LbzFinalBoss2EggCapsuleInstance sourceCapsule = objectManager.createDynamicObject(
                () -> LbzFinalBoss2EggCapsuleInstance.createForCamera(CAMERA_X, CAMERA_Y));
        sourceCapsule.update(0, fixture.sprite());
        ObjectRefId capsuleId = objectId(sourceCapsule);
        S3kBossExplosionChild sourceExplosion = objectManager.createDynamicObject(
                () -> S3kBossExplosionChild.createWithNativeInitSfx(0x4400, 0x03E0));
        ObjectRefId explosionId = objectId(sourceExplosion);

        setBooleanField(sourceBoss, "defeatStarted", true);
        setEnumField(sourceBoss, "defeatStage", "PLAYER_FALL");
        setIntField(sourceBoss, "x", 0x4510);
        setIntField(sourceBoss, "y", 0x0100);
        setIntField(sourceBoss, "xSub", 0x1357);
        setIntField(sourceBoss, "ySub", 0x2468);
        setIntField(sourceBoss, "xVel", 0x0200);
        setIntField(sourceBoss, "yVel", -0x0400);
        setBooleanField(sourceBoss, "artTileHigh", true);
        setIntField(sourceBoss, "statusBits", 1 << 6);
        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0x4510);
        player.setCentreYPreserveSubpixel((short) 0x0100);
        player.setSubpixelRaw(0xCAFE, 0xBEEF);
        player.setObjectControlled(true);
        player.setControlLocked(true);
        player.setObjectMappingFrameControl(true);
        player.setMappingFrame(0x8C);
        setIntField(sourceDebris, "xSub", 0x1111);
        setIntField(sourceDebris, "ySub", 0x2222);
        setEnumField(sourceFloor, "stage", "FALL");
        setIntField(sourceFloor, "xSub", 0x3333);
        setIntField(sourceFloor, "ySub", 0x4444);
        setIntField(sourceFloor, "yVel", -0x0100);
        setIntField(sourceCapsule, "ySubpixel", 0x5555);

        CompositeSnapshot positionBoundary = registry.capture();
        registry.restore(positionBoundary);
        LbzFinalBoss2Instance restoredBoss = objectById(
                LbzFinalBoss2Instance.class, bossId);
        DefeatDebrisChild restoredDebris = objectById(DefeatDebrisChild.class, debrisId);
        EscapeFloorChild restoredFloor = objectById(EscapeFloorChild.class, floorId);
        LbzFinalBoss2EggCapsuleInstance restoredCapsule = objectById(
                LbzFinalBoss2EggCapsuleInstance.class, capsuleId);
        S3kBossExplosionChild restoredExplosion = objectById(
                S3kBossExplosionChild.class, explosionId);
        assertEquals(0x1357, readIntField(restoredBoss, "xSub"));
        assertEquals(0x2468, readIntField(restoredBoss, "ySub"));
        assertTrue(restoredBoss.isHighPriority());
        assertEquals(1 << 6, readIntField(restoredBoss, "statusBits") & (1 << 6));
        assertTrue(restoredDebris.isHighPriority(),
                "captured high ObjDat art priority must survive shell recreation");
        assertEquals(0x1111, readIntField(restoredDebris, "xSub"));
        assertEquals(0x2222, readIntField(restoredDebris, "ySub"));
        assertEquals(0x3333, readIntField(restoredFloor, "xSub"));
        assertEquals(0x4444, readIntField(restoredFloor, "ySub"));
        assertEquals(0x5555, readIntField(restoredCapsule, "ySubpixel"));
        assertEquals(0xCAFE, fixture.sprite().getXSubpixelRaw());
        assertEquals(0xBEEF, fixture.sprite().getYSubpixelRaw());

        updateFixedPointBoundary(
                restoredBoss, restoredDebris, restoredFloor, restoredCapsule,
                restoredExplosion);
        FixedPointOracle expectedMotion = fixedPointOracle(
                restoredBoss, restoredDebris, restoredFloor, restoredCapsule,
                restoredExplosion);

        registry.restore(positionBoundary);
        restoredBoss = objectById(LbzFinalBoss2Instance.class, bossId);
        restoredDebris = objectById(DefeatDebrisChild.class, debrisId);
        restoredFloor = objectById(EscapeFloorChild.class, floorId);
        restoredCapsule = objectById(LbzFinalBoss2EggCapsuleInstance.class, capsuleId);
        restoredExplosion = objectById(S3kBossExplosionChild.class, explosionId);
        updateFixedPointBoundary(
                restoredBoss, restoredDebris, restoredFloor, restoredCapsule,
                restoredExplosion);
        assertEquals(expectedMotion, fixedPointOracle(
                restoredBoss, restoredDebris, restoredFloor, restoredCapsule,
                restoredExplosion));
        assertEquals(0x1357, readIntField(restoredBoss, "xSub"));
        assertEquals(0x2468, readIntField(restoredBoss, "ySub"));
        assertEquals(0xCAFE, fixture.sprite().getXSubpixelRaw());
        assertEquals(0xBEEF, fixture.sprite().getYSubpixelRaw(),
                "word carrier copies preserve all player low-position bits");

        LbzZoneRuntimeState shake = currentLbzState();
        shake.startTimedScreenShake(1);
        shake.prepareTimedScreenShakeBackground(true, 1);
        shake.startTimedScreenShake(20);
        shake.applyTimedScreenShakeForeground();
        assertEquals(new ShakeOracle(20, 1, 1), shakeOracle(shake));
        CompositeSnapshot foregroundBoundary = registry.capture();

        shake.prepareTimedScreenShakeBackground(true, -5);
        ShakeOracle prepared = new ShakeOracle(19, -5, 1);
        assertEquals(prepared, shakeOracle(shake));
        registry.restore(foregroundBoundary);
        shake = currentLbzState();
        assertEquals(new ShakeOracle(20, 1, 1), shakeOracle(shake));
        shake.prepareTimedScreenShakeBackground(true, -5);
        assertEquals(prepared, shakeOracle(shake));
        shake.applyTimedScreenShakeForeground();
        assertEquals(new ShakeOracle(19, -5, -5), shakeOracle(shake));

        CompositeSnapshot deadPauseBoundary = registry.capture();
        fixture.sprite().setObjectRoutineOverride(6);
        shake.prepareTimedScreenShakeBackground(false, 0);
        assertEquals(new ShakeOracle(19, 0, -5), shakeOracle(shake));
        registry.restore(deadPauseBoundary);
        shake = currentLbzState();
        fixture.sprite().setObjectRoutineOverride(6);
        shake.prepareTimedScreenShakeBackground(false, 0);
        assertEquals(new ShakeOracle(19, 0, -5), shakeOracle(shake),
                "routine >= 6 pauses the countdown and publishes zero");
        fixture.sprite().setObjectRoutineOverride(2);

        restoredBoss = objectById(LbzFinalBoss2Instance.class, bossId);
        setBooleanField(restoredBoss, "defeatStarted", true);
        setEnumField(restoredBoss, "defeatStage", "SHIP_ESCAPE");
        setIntField(restoredBoss, "x", CAMERA_X + 0x1BE);
        setIntField(restoredBoss, "xSub", 0x7777);
        setIntField(restoredBoss, "xVel", 0x0200);
        setIntField(restoredBoss, "yVel", 0);
        setIntField(restoredBoss, "flags", 0);
        GameServices.gameState().setCurrentBossId(Sonic3kObjectIds.LBZ_FINAL_BOSS_1);
        CompositeSnapshot bossFlagBoundary = registry.capture();
        restoredBoss.update(0, fixture.sprite());
        GlobalBoundaryOracle shipCrossing = globalBoundaryOracle(restoredBoss);
        assertEquals(0, shipCrossing.bossId());
        assertEquals("WAIT_FLOOR_SIGNAL", shipCrossing.stage());
        assertTrue(shipCrossing.hidden());
        assertEquals(1 << 6, shipCrossing.statusBits() & (1 << 6));
        assertTrue(shipCrossing.artTileHigh());
        assertEquals(0x7777, readIntField(restoredBoss, "xSub"));

        registry.restore(bossFlagBoundary);
        restoredBoss = objectById(LbzFinalBoss2Instance.class, bossId);
        restoredBoss.update(0, fixture.sprite());
        assertEquals(shipCrossing, globalBoundaryOracle(restoredBoss),
                "Boss_flag clears on the same restored ship-crossing entry");

        setEnumField(restoredBoss, "defeatStage", "AUTOWALK");
        setIntField(restoredBoss, "flags", 0x20);
        fixture.sprite().setCentreX((short) (CAMERA_X + 0x50));
        fixture.sprite().setCentreYPreserveSubpixel((short) 0x0400);
        int headsBefore = restoredBoss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD);
        CompositeSnapshot autoWalkBoundary = registry.capture();
        restoredBoss.update(0, fixture.sprite());
        assertEquals(0, readIntField(restoredBoss, "flags") & 0x20);
        assertEquals("SHIP_RISE", readObjectField(restoredBoss, "defeatStage").toString());
        assertEquals(headsBefore + 1, restoredBoss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD));
        ObjectRefId firstHeadId = objectId(assertInstanceOf(ObjectInstance.class,
                restoredBoss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD).getLast()));

        registry.restore(autoWalkBoundary);
        restoredBoss = objectById(LbzFinalBoss2Instance.class, bossId);
        restoredBoss.update(0, fixture.sprite());
        assertEquals(0, readIntField(restoredBoss, "flags") & 0x20);
        assertEquals(firstHeadId, objectId(assertInstanceOf(ObjectInstance.class,
                restoredBoss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD).getLast())),
                "PLC/head submission and later-slot allocation replay deterministically");

        setEnumField(restoredBoss, "defeatStage", "DELAY");
        setIntField(restoredBoss, "defeatTimer", 0);
        setIntField(restoredBoss, "flags", 0);
        restoredBoss.update(0, fixture.sprite());
        SongFadeTransitionInstance fade = onlyLive(SongFadeTransitionInstance.class);
        ObjectRefId fadeId = objectId(fade);
        assertEquals(0, readIntField(fade, "timer"));
        assertEquals(119, readIntField(restoredBoss, "defeatTimer"));
        assertEquals(120, readIntField(fade, "nativeRemaining"));
        CompositeSnapshot fadeBoundary = registry.capture();
        registry.restore(fadeBoundary);
        SongFadeTransitionInstance restoredFade = objectById(
                SongFadeTransitionInstance.class, fadeId);
        restoredFade.update(0, fixture.sprite());
        assertEquals(119, readIntField(restoredFade, "nativeRemaining"));
        registry.restore(fadeBoundary);
        restoredFade = objectById(SongFadeTransitionInstance.class, fadeId);
        restoredFade.update(0, fixture.sprite());
        assertEquals(119, readIntField(restoredFade, "nativeRemaining"),
                "the independent Obj_Song_Fade_ToLevelMusic owner replays from $2E=119");
    }

    @Test
    void capsuleSignalsShipAndFloorGraphsRoundTripThroughProductionRoute() throws Exception {
        LbzFinalBoss2Instance capsuleWaitBoss = spawnBoss();
        defeatThroughProductionHits(capsuleWaitBoss);
        stepUntil(capsuleWaitBoss::isCapsuleChildSpawnedForTest, 0x600);
        LbzFinalBoss2EggCapsuleInstance sourceCapsule = onlyLive(
                LbzFinalBoss2EggCapsuleInstance.class);
        ObjectRefId capsuleId = objectId(sourceCapsule);
        String capsuleBeforeThreshold = sourceCapsule.traceDebugDetails();
        assertTrue(GameServices.gameState().isEndOfLevelActive());
        assertFalse(dynamicWaterLocked());

        GraphRoundTrip beforeThreshold = roundTripGraph(capsuleWaitBoss);
        LbzFinalBoss2EggCapsuleInstance beforeThresholdCapsule = beforeThreshold.object(
                capsuleId, LbzFinalBoss2EggCapsuleInstance.class);
        assertSame(beforeThresholdCapsule, readObjectField(beforeThreshold.boss(), "capsuleChild"));
        assertEquals(capsuleBeforeThreshold, beforeThresholdCapsule.traceDebugDetails());
        assertTrue(GameServices.gameState().isEndOfLevelActive());
        assertFalse(dynamicWaterLocked());
        assertFalse(beforeThreshold.boss().isCapsuleReleasedForTest());

        openCapsule(beforeThresholdCapsule);
        stepUntil(() -> dynamicWaterLocked() && GameServices.gameState().isEndOfLevelActive(),
                0x1000, CAMERA_X + 0x50, 0x03E0, false);
        LbzFinalBoss2Instance lockWaitBoss = beforeThreshold.boss();
        LbzFinalBoss2EggCapsuleInstance lockWaitCapsule = objectById(
                LbzFinalBoss2EggCapsuleInstance.class, capsuleId);
        String capsuleAfterThreshold = lockWaitCapsule.traceDebugDetails();
        ObjectRefId resultsId = objectId(onlyLive(
                com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance.class));

        GraphRoundTrip afterThreshold = roundTripGraph(lockWaitBoss);
        LbzFinalBoss2EggCapsuleInstance afterThresholdCapsule = afterThreshold.object(
                capsuleId, LbzFinalBoss2EggCapsuleInstance.class);
        assertSame(afterThresholdCapsule, readObjectField(afterThreshold.boss(), "capsuleChild"));
        assertEquals(capsuleAfterThreshold, afterThresholdCapsule.traceDebugDetails());
        assertNotNull(objectById(
                com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance.class, resultsId));
        assertTrue(GameServices.gameState().isEndOfLevelActive());
        assertTrue(dynamicWaterLocked());
        assertFalse(afterThreshold.boss().isCapsuleReleasedForTest());

        stepUntil(() -> !GameServices.gameState().isEndOfLevelActive(),
                0x1000, CAMERA_X + 0x50, 0x03E0, false);
        LbzFinalBoss2Instance resultsClearedBoss = afterThreshold.boss();
        assertTrue(dynamicWaterLocked());
        assertFalse(resultsClearedBoss.isCapsuleReleasedForTest(),
                "the later results slot clears active after the root's poll");

        GraphRoundTrip cleared = roundTripGraph(resultsClearedBoss);
        assertFalse(GameServices.gameState().isEndOfLevelActive());
        assertTrue(dynamicWaterLocked());
        assertFalse(cleared.boss().isCapsuleReleasedForTest());
        pinPlayer(CAMERA_X + 0x50, 0x03E0, false);
        fixture.stepIdleFrames(1);
        assertTrue(cleared.boss().isCapsuleReleasedForTest());

        LbzFinalBoss2Instance shipBoss = cleared.boss();
        stepUntil(() -> !shipBoss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ESCAPE_FLAME).isEmpty(),
                0x400, CAMERA_X + 0x50, 0x03E0, false);
        RobotnikShipFlameChild sourceFlame = onlyChild(
                shipBoss, LbzFinalBoss2Instance.ChildKind.ESCAPE_FLAME,
                RobotnikShipFlameChild.class);
        ObjectRefId flameId = objectId(sourceFlame);
        GraphRoundTrip ship = roundTripGraph(shipBoss);
        RobotnikShipFlameChild restoredFlame = ship.object(
                flameId, RobotnikShipFlameChild.class);
        assertSame(restoredFlame, readObjectField(ship.boss(), "escapeFlame"));
        assertSame(ship.boss(), readObjectField(restoredFlame, "boss"));
        assertTrue(ship.boss().childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ROBOTNIK_HEAD).stream()
                .map(LbzFinalBoss2Instance.RobotnikHead4Child.class::cast)
                .anyMatch(LbzFinalBoss2Instance.RobotnikHead4Child::usesEggRoboMappingForTest));

        LbzFinalBoss2Instance floorBoss = ship.boss();
        stepUntil(() -> !floorBoss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR).isEmpty(),
                0x400, CAMERA_X + 0x50, 0x03E0, false);
        EscapeFloorChild sourceFloor = onlyChild(
                floorBoss, LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR,
                EscapeFloorChild.class);
        stepUntil(sourceFloor::isSettledForTest, 0x400, 0x4400, 0x0100, false);
        ObjectRefId floorBossId = objectId(floorBoss);
        ObjectRefId floorId = objectId(sourceFloor);
        Sonic3kLBZEvents sourceLbzEvents = ((Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider()).getLbzEvents();
        assertTrue(sourceLbzEvents.isPostTitleAct2SizeChangeActiveForTest());
        int[] eventTargets = sourceLbzEvents.postTitleAct2TargetsForTest();
        int floorBoundaryMaxY = fixture.camera().getMaxY() & 0xFFFF;
        GraphRoundTrip workerBoundary = roundTripGraph(floorBoss);
        assertNotNull(workerBoundary.object(floorId, EscapeFloorChild.class));
        Sonic3kLBZEvents restoredLbzEvents = ((Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider()).getLbzEvents();
        assertTrue(restoredLbzEvents.isPostTitleAct2SizeChangeActiveForTest());
        assertArrayEquals(eventTargets, restoredLbzEvents.postTitleAct2TargetsForTest());
        assertEquals(floorBoundaryMaxY, fixture.camera().getMaxY() & 0xFFFF);
        assertArrayEquals(new int[]{0x6000, 0, 0x1000}, eventTargets);
        assertArrayEquals(new int[]{0x4000, 0x4000, 0x8000},
                restoredLbzEvents.postTitleAct2WorkerAccumulatorsForTest());

        int[] beforeSecondEntryBounds = cameraBounds();
        CompositeSnapshot beforeSecondEntry = registry.capture();
        restoredLbzEvents.updatePostTitleAct2SizeWorkers();
        assertArrayEquals(new int[]{0x8000, 0x8000, 0x10000},
                restoredLbzEvents.postTitleAct2WorkerAccumulatorsForTest());
        assertEquals(beforeSecondEntryBounds[0], cameraBounds()[0]);
        assertEquals(beforeSecondEntryBounds[1], cameraBounds()[1]);
        assertEquals((beforeSecondEntryBounds[2] + 1) & 0xFFFF, cameraBounds()[2],
                "the next event-owner update is each worker's second own entry");
        int[] secondEntryBounds = cameraBounds();
        boolean[] secondEntryPhases = restoredLbzEvents.postTitleAct2WorkerPhasesForTest();

        registry.restore(beforeSecondEntry);
        restoredLbzEvents = currentLbzEvents();
        restoredLbzEvents.updatePostTitleAct2SizeWorkers();
        assertArrayEquals(new int[]{0x8000, 0x8000, 0x10000},
                restoredLbzEvents.postTitleAct2WorkerAccumulatorsForTest());
        assertArrayEquals(secondEntryBounds, cameraBounds());
        assertArrayEquals(secondEntryPhases,
                restoredLbzEvents.postTitleAct2WorkerPhasesForTest());

        CompositeSnapshot beforeNonzeroStep = registry.capture();
        restoredLbzEvents.updatePostTitleAct2SizeWorkers();
        int[] nonzeroBounds = cameraBounds();
        int[] nonzeroAccumulators =
                restoredLbzEvents.postTitleAct2WorkerAccumulatorsForTest();
        boolean[] nonzeroPhases = restoredLbzEvents.postTitleAct2WorkerPhasesForTest();
        registry.restore(beforeNonzeroStep);
        restoredLbzEvents = currentLbzEvents();
        restoredLbzEvents.updatePostTitleAct2SizeWorkers();
        assertArrayEquals(nonzeroBounds, cameraBounds());
        assertArrayEquals(nonzeroAccumulators,
                restoredLbzEvents.postTitleAct2WorkerAccumulatorsForTest());
        assertArrayEquals(nonzeroPhases,
                restoredLbzEvents.postTitleAct2WorkerPhasesForTest());

        LbzFinalBoss2Instance resumedFloorBoss = objectById(
                LbzFinalBoss2Instance.class, floorBossId);
        EscapeFloorChild resumedFloor = objectById(EscapeFloorChild.class, floorId);

        stepUntil(() -> firstEmitterWithController(resumedFloorBoss) != null,
                0x20, 0x4400, 0x0100, false);

        EscapeExplosionEmitterChild sourceEmitter = firstEmitterWithController(resumedFloorBoss);
        assertNotNull(sourceEmitter);
        BigArmExplosionControllerChild sourceEmitterController =
                sourceEmitter.explosionControllerForTest();
        assertNotNull(sourceEmitterController);
        List<VisibleExplosionOracle> sourceVisibleExplosions = visibleExplosionOracles();
        assertFalse(sourceVisibleExplosions.isEmpty());
        List<EscapeFloorExplosionChild> sourceFloorExplosions = children(
                resumedFloorBoss, LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR_EXPLOSION,
                EscapeFloorExplosionChild.class);
        assertEquals(7, sourceFloorExplosions.size());

        ObjectRefId emitterId = objectId(sourceEmitter);
        ObjectRefId emitterControllerId = objectId(sourceEmitterController);
        List<ObjectRefId> floorExplosionIds = idsForList(sourceFloorExplosions);
        List<ObjectRefId> emitterIds = idsForList(readListField(resumedFloor, "emitters"));
        int floorCounter = resumedFloor.emitterCounterForTest();
        int emitterPosition = sourceEmitter.positionIndexForTest();
        int emitterWait = readIntField(sourceEmitter, "waitTimer");
        int controllerInterval = sourceEmitterController.intervalCounterForTest();
        int controllerEmissions = sourceEmitterController.emissionCountForTest();
        int allocationCount = resumedFloorBoss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ESCAPE_EXPLOSION_EMITTER);

        GraphRoundTrip floor = roundTripGraph(resumedFloorBoss);
        EscapeFloorChild restoredFloor = floor.object(floorId, EscapeFloorChild.class);
        EscapeExplosionEmitterChild restoredEmitter = floor.object(
                emitterId, EscapeExplosionEmitterChild.class);
        BigArmExplosionControllerChild restoredEmitterController = floor.object(
                emitterControllerId, BigArmExplosionControllerChild.class);
        assertSame(restoredFloor, readObjectField(floor.boss(), "escapeFloor"));
        assertEquals(floorCounter, restoredFloor.emitterCounterForTest());
        assertEquals(floorExplosionIds, idsForList(readListField(restoredFloor, "explosions")));
        assertEquals(emitterIds, idsForList(readListField(restoredFloor, "emitters")));
        for (Object explosion : readListField(restoredFloor, "explosions")) {
            assertSame(restoredFloor, readObjectField(explosion, "floor"));
        }
        assertSame(restoredFloor, readObjectField(restoredEmitter, "floor"));
        assertSame(restoredEmitterController,
                readObjectField(restoredEmitter, "explosionController"));
        assertEquals(emitterPosition, restoredEmitter.positionIndexForTest());
        assertEquals(emitterWait, readIntField(restoredEmitter, "waitTimer"));
        assertSame(restoredEmitter, readObjectField(restoredEmitterController, "emitterParent"));
        assertEquals(0x80, restoredEmitterController.counterForTest());
        assertEquals(controllerInterval, restoredEmitterController.intervalCounterForTest());
        assertEquals(controllerEmissions, restoredEmitterController.emissionCountForTest());
        assertVisibleExplosionOraclesRestored(sourceVisibleExplosions);
        assertEquals(allocationCount, floor.boss().getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ESCAPE_EXPLOSION_EMITTER));

        setIntField(restoredEmitter, "waitTimer", 0);
        restoredEmitter.update(0, fixture.sprite());
        assertTrue(restoredEmitter.controllerStopSignalForTest());
        assertTrue(restoredEmitter.isPendingDeleteForTest());
        assertFalse(restoredEmitter.isDestroyed());
        assertFalse(restoredEmitterController.isPendingDeleteForTest(),
                "the later controller slot has not observed the emitter signal yet");
        CompositeSnapshot beforeLaterControllerPoll = registry.capture();

        restoredEmitterController.update(0, fixture.sprite());
        assertTrue(restoredEmitterController.isPendingDeleteForTest());
        assertFalse(restoredEmitterController.isDestroyed());
        CompositeSnapshot afterLaterControllerPoll = registry.capture();

        registry.restore(beforeLaterControllerPoll);
        EscapeExplosionEmitterChild reexecutedEmitter = objectById(
                EscapeExplosionEmitterChild.class, emitterId);
        BigArmExplosionControllerChild reexecutedController = objectById(
                BigArmExplosionControllerChild.class, emitterControllerId);
        assertTrue(reexecutedEmitter.isPendingDeleteForTest());
        assertFalse(reexecutedController.isPendingDeleteForTest());
        reexecutedController.update(0, fixture.sprite());
        assertTrue(reexecutedController.isPendingDeleteForTest());

        registry.restore(afterLaterControllerPoll);
        LbzFinalBoss2Instance pendingFloorBoss = objectById(
                LbzFinalBoss2Instance.class, floorBossId);
        EscapeFloorChild pendingFloor = objectById(EscapeFloorChild.class, floorId);
        reexecutedEmitter = objectById(EscapeExplosionEmitterChild.class, emitterId);
        reexecutedController = objectById(
                BigArmExplosionControllerChild.class, emitterControllerId);
        assertTrue(idsForList(readListField(pendingFloor, "emitters")).contains(emitterId));
        assertTrue(idsForList(readListField(pendingFloorBoss, "graphChildren"))
                .containsAll(List.of(emitterId, emitterControllerId)));
        int pendingEmissions = reexecutedController.emissionCountForTest();
        reexecutedEmitter.update(1, fixture.sprite());
        reexecutedController.update(1, fixture.sprite());
        assertTrue(reexecutedEmitter.isDestroyed());
        assertTrue(reexecutedController.isDestroyed());
        assertEquals(pendingEmissions, reexecutedController.emissionCountForTest());
        assertFalse(idsForList(readListField(pendingFloor, "emitters")).contains(emitterId));
        assertFalse(idsForList(readListField(pendingFloorBoss, "graphChildren"))
                .contains(emitterId));
        assertFalse(idsForList(readListField(pendingFloorBoss, "graphChildren"))
                .contains(emitterControllerId));

        EscapeFloorExplosionChild terminalFloorExplosion = objectById(
                EscapeFloorExplosionChild.class, floorExplosionIds.getFirst());
        for (int entry = 0; entry < 0x80
                && !terminalFloorExplosion.isPendingDeleteForTest(); entry++) {
            terminalFloorExplosion.update(entry, fixture.sprite());
        }
        assertTrue(terminalFloorExplosion.isPendingDeleteForTest());
        assertFalse(terminalFloorExplosion.isDestroyed());
        ObjectRefId terminalFloorExplosionId = objectId(terminalFloorExplosion);
        int terminalFloorSlot = slotOf(terminalFloorExplosion);
        int terminalFloorMapping = terminalFloorExplosion.mappingFrameForTest();
        assertTrue(idsForList(readListField(pendingFloor, "explosions"))
                .contains(terminalFloorExplosionId));

        registry.restore(registry.capture());
        LbzFinalBoss2Instance terminalFloorBoss = objectById(
                LbzFinalBoss2Instance.class, floorBossId);
        EscapeFloorChild terminalFloor = objectById(EscapeFloorChild.class, floorId);
        terminalFloorExplosion = objectById(
                EscapeFloorExplosionChild.class, terminalFloorExplosionId);
        assertTrue(terminalFloorExplosion.isPendingDeleteForTest());
        assertEquals(terminalFloorSlot, slotOf(terminalFloorExplosion));
        assertEquals(terminalFloorMapping, terminalFloorExplosion.mappingFrameForTest());
        assertTrue(idsForList(readListField(terminalFloor, "explosions"))
                .contains(terminalFloorExplosionId));
        assertTrue(idsForList(readListField(terminalFloorBoss, "graphChildren"))
                .contains(terminalFloorExplosionId));
        terminalFloorExplosion.update(0, fixture.sprite());
        assertTrue(terminalFloorExplosion.isDestroyed());
        assertFalse(idsForList(readListField(terminalFloor, "explosions"))
                .contains(terminalFloorExplosionId));
        assertFalse(idsForList(readListField(terminalFloorBoss, "graphChildren"))
                .contains(terminalFloorExplosionId));

        Sonic3kLBZEvents lbzEvents = ((Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider()).getLbzEvents();
        assertTrue(lbzEvents.isPostTitleAct2SizeChangeActiveForTest(),
                "literal stored targets keep the three gradual workers alive beyond their zero-step entries");
        assertArrayEquals(new int[]{0x6000, 0, 0x1000},
                lbzEvents.postTitleAct2TargetsForTest());
    }

    @Test
    void failedSstAllocationsNeverEnterCapturedGraphAndRetryOnlyAtNativeBoundaries()
            throws Exception {
        LbzFinalBoss2Instance boss = spawnBoss();
        ObjectRefId bossId = objectId(boss);
        setBooleanField(boss, "initialized", true);

        fillAllSstSlots();
        freeLowestFillers(2);
        invokeNoArg(boss, "spawnArmGraph");
        assertEquals(List.of(
                        LbzFinalBoss2Instance.ChildKind.ARM_GRAPH,
                        LbzFinalBoss2Instance.ChildKind.ARM_ATTACHMENT),
                boss.getChildOrderForTest(),
                "CreateChild1_Normal stops the initial table at the first failure");
        assertEquals(0, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_VISUAL));
        assertEquals(0, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_OUTER_COLLISION));
        assertEveryRootEdgeHasLiveIdentity(boss);
        boss = strictRestoreBoss(bossId);

        freeLowestFillers(2);
        invokeNoArg(boss, "spawnNestedArmGraph");
        assertEquals(2, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_SEGMENT));
        assertEquals(0, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ARM_JOINT));
        assertEquals(0, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.GRAB));
        assertEveryRootEdgeHasLiveIdentity(boss);
        boss = strictRestoreBoss(bossId);

        invokePrivate(boss, "startDefeat", new Class<?>[]{
                com.openggf.game.PlayableEntity.class}, fixture.sprite());
        assertNull(readObjectField(boss, "defeatExplosionController"));
        assertEquals(0, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_EXPLOSION_CONTROLLER));
        assertEquals(0, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_FOLLOW_VISUAL));
        boss = strictRestoreBoss(bossId);
        freeLowestFillers(1);
        boss.update(0, fixture.sprite());
        assertNull(readObjectField(boss, "defeatExplosionController"),
                "the root's failed subtype-4 controller is a one-shot allocation");

        BigArmExplosionControllerChild controller = createChild(
                BigArmExplosionControllerChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class}, boss);
        controller = recordBossChild(boss,
                LbzFinalBoss2Instance.ChildKind.DEFEAT_EXPLOSION_CONTROLLER, controller);
        setObjectField(boss, "defeatExplosionController", controller);
        ObjectRefId controllerId = objectId(controller);
        fillAllSstSlots();
        controller.update(0, fixture.sprite());
        assertEquals(0, controller.emissionCountForTest());
        assertTrue(objectManager.activeObjectsOfType(S3kBossExplosionChild.class).isEmpty());
        boss = strictRestoreBoss(bossId);
        controller = objectById(BigArmExplosionControllerChild.class, controllerId);
        freeLowestFillers(1);
        controller.update(1, fixture.sprite());
        controller.update(2, fixture.sprite());
        assertEquals(0, controller.emissionCountForTest(),
                "a live controller must not retry before its third own entry");
        controller.update(3, fixture.sprite());
        assertEquals(1, controller.emissionCountForTest());
        assertEquals(1, objectManager.activeObjectsOfType(S3kBossExplosionChild.class).size());
        boss = strictRestoreBoss(bossId);

        freeLowestFillers(1);
        EscapeFloorChild floor = createChild(EscapeFloorChild.class,
                new Class<?>[]{LbzFinalBoss2Instance.class, int.class, int.class},
                boss, 0, 0);
        floor = recordBossChild(boss,
                LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR, floor);
        setObjectField(boss, "escapeFloor", floor);
        ObjectRefId floorId = objectId(floor);
        fillAllSstSlots();
        freeLowestFillers(3);
        int maxXBeforeSettle = fixture.camera().getMaxX() & 0xFFFF;
        int minYBeforeSettle = fixture.camera().getMinY() & 0xFFFF;
        int maxYBeforeSettle = fixture.camera().getMaxY() & 0xFFFF;
        invokeNoArg(floor, "settle");
        assertEquals(3, readListField(floor, "explosions").size(),
                "the seven-entry hitbox table stops at its first failed allocation");
        assertEquals(3, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ESCAPE_FLOOR_EXPLOSION));
        assertEquals(maxXBeforeSettle, fixture.camera().getMaxX() & 0xFFFF,
                "loc_74DA4 stores max X after a failed child-table entry");
        assertEquals(minYBeforeSettle, fixture.camera().getMinY() & 0xFFFF);
        assertEquals(maxYBeforeSettle, fixture.camera().getMaxY() & 0xFFFF);
        assertEquals(0x1000, fixture.camera().getMaxYTarget() & 0xFFFF);
        assertArrayEquals(new int[]{0x6000, 0, 0x1000},
                currentLbzEvents().postTitleAct2TargetsForTest());
        assertTrue(currentLbzEvents().isPostTitleAct2SizeChangeActiveForTest(),
                "the later level-size owner must still be allocated after table failure");
        assertEveryRootEdgeHasLiveIdentity(boss);
        assertEveryListEdgeHasLiveIdentity(floor, "explosions");
        boss = strictRestoreBoss(bossId);
        floor = objectById(EscapeFloorChild.class, floorId);

        floor.update(0, fixture.sprite());
        assertEquals(0x7E, floor.emitterCounterForTest());
        assertTrue(readListField(floor, "emitters").isEmpty());
        assertEquals(0, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ESCAPE_EXPLOSION_EMITTER),
                "a failed qualified attempt consumes no successful ordinal");
        boss = strictRestoreBoss(bossId);
        floor = objectById(EscapeFloorChild.class, floorId);
        freeLowestFillers(1);
        floor.update(1, fixture.sprite());
        floor.update(2, fixture.sprite());
        floor.update(3, fixture.sprite());
        assertTrue(readListField(floor, "emitters").isEmpty(),
                "loc_74DEA retries only on the next four-V-int boundary");
        floor.update(4, fixture.sprite());
        assertEquals(0x7D, floor.emitterCounterForTest());
        assertEquals(1, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.ESCAPE_EXPLOSION_EMITTER));
        EscapeExplosionEmitterChild emitter = onlyChild(boss,
                LbzFinalBoss2Instance.ChildKind.ESCAPE_EXPLOSION_EMITTER,
                EscapeExplosionEmitterChild.class);
        assertEquals(0, emitter.getSpawn().subtype(),
                "the success after a failure keeps the next successful ordinal");
        assertEveryListEdgeHasLiveIdentity(floor, "emitters");
        ObjectRefId emitterId = objectId(emitter);
        boss = strictRestoreBoss(bossId);
        emitter = objectById(EscapeExplosionEmitterChild.class, emitterId);

        emitter.update(4, fixture.sprite());
        assertNull(emitter.explosionControllerForTest(),
                "an emitter must not retain its destroyed/slotless controller attempt");
        boss = strictRestoreBoss(bossId);
        emitter = objectById(EscapeExplosionEmitterChild.class, emitterId);
        freeLowestFillers(1);
        emitter.update(5, fixture.sprite());
        assertNull(emitter.explosionControllerForTest(),
                "the emitter's subtype-4 controller allocation is one-shot");

        fillAllSstSlots();
        invokeNoArg(boss, "beginCapsuleHandoff");
        assertTrue(GameServices.gameState().isEndOfLevelActive(),
                "Boss_LoadEggCapsuleAndAnimals writes _unkFAA8 before allocation");
        assertNull(readObjectField(boss, "capsuleChild"),
                "a destroyed/slotless route-8 capsule must not enter the dedicated edge");
        assertFalse(boss.isCapsuleChildSpawnedForTest());
        assertTrue(boss.childrenOfKindForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_CAPSULE).isEmpty());
        assertEquals(0, boss.getChildAllocationCountForTest(
                LbzFinalBoss2Instance.ChildKind.DEFEAT_CAPSULE),
                "failed construction consumes no successful child ordinal");
        boss = strictRestoreBoss(bossId);
        freeLowestFillers(1);
        fixture.stepIdleFrames(1);
        boss = objectById(LbzFinalBoss2Instance.class, bossId);
        assertNull(readObjectField(boss, "capsuleChild"));
        assertFalse(boss.isCapsuleChildSpawnedForTest(),
                "the one-shot route-8 callback does not retry after a slot is freed");
        strictRestoreBoss(bossId);
    }

    private void defeatThroughProductionHits(LbzFinalBoss2Instance boss) {
        stepUntil(() -> boss.getCollisionFlags() == 0x0F, 0x400);
        for (int hit = 1; hit <= 8; hit++) {
            boss.onPlayerAttack(fixture.sprite(), null);
            fixture.stepIdleFrames(1);
            if (hit < 8) {
                stepUntil(() -> boss.getHitFlashTimerForTest() == 0, 0x80);
            }
        }
        assertTrue(boss.isDefeatStartedForTest());
    }

    private void openCapsule(LbzFinalBoss2EggCapsuleInstance capsule) {
        for (int frame = 0; frame < 8 && !capsule.traceDebugDetails().contains("o=1"); frame++) {
            AbstractPlayableSprite player = fixture.sprite();
            player.setCentreX((short) (capsule.getX() + 1));
            player.setCentreYPreserveSubpixel((short) (capsule.getY() + 0x08));
            player.setXSpeed((short) 0);
            player.setYSpeed((short) -0x100);
            player.setGSpeed((short) 0);
            player.setAir(true);
            player.setAnimationId(Sonic3kAnimationIds.ROLL);
            player.setObjectControlled(true);
            fixture.stepIdleFrames(1);
        }
        assertTrue(capsule.traceDebugDetails().contains("o=1"));
    }

    private boolean dynamicWaterLocked() {
        return GameServices.water().isDynamicWaterLocked(Sonic3kZoneIds.ZONE_LBZ, 1);
    }

    private int[] cameraBounds() {
        return new int[]{
                fixture.camera().getMaxX() & 0xFFFF,
                fixture.camera().getMinY() & 0xFFFF,
                fixture.camera().getMaxY() & 0xFFFF
        };
    }

    private EscapeExplosionEmitterChild firstEmitterWithController(LbzFinalBoss2Instance boss) {
        return boss.childrenOfKindForTest(
                        LbzFinalBoss2Instance.ChildKind.ESCAPE_EXPLOSION_EMITTER).stream()
                .map(EscapeExplosionEmitterChild.class::cast)
                .filter(emitter -> emitter.explosionControllerForTest() != null)
                .filter(emitter -> !emitter.explosionControllerForTest().isDestroyed())
                .findFirst().orElse(null);
    }

    private GraphRoundTrip roundTripGraph(LbzFinalBoss2Instance sourceBoss) throws Exception {
        ObjectRefId bossId = objectId(sourceBoss);
        int bossSlot = slotOf(sourceBoss);
        List<ObjectInstance> sourceGraph = readListField(sourceBoss, "graphChildren").stream()
                .map(ObjectInstance.class::cast)
                .toList();
        List<ObjectRefId> orderedIds = idsForList(sourceGraph);
        Map<ObjectRefId, ObjectInstance> sourceById = new LinkedHashMap<>();
        Map<ObjectRefId, Integer> slots = new LinkedHashMap<>();
        Map<ObjectRefId, Class<?>> types = new LinkedHashMap<>();
        for (int i = 0; i < sourceGraph.size(); i++) {
            ObjectRefId id = orderedIds.get(i);
            ObjectInstance object = sourceGraph.get(i);
            sourceById.put(id, object);
            slots.put(id, slotOf(object));
            types.put(id, object.getClass());
        }

        registry.restore(registry.capture());

        LbzFinalBoss2Instance restoredBoss = objectById(LbzFinalBoss2Instance.class, bossId);
        assertNotSame(sourceBoss, restoredBoss);
        assertEquals(bossSlot, slotOf(restoredBoss));
        assertEquals(orderedIds, idsForList(readListField(restoredBoss, "graphChildren")));
        Map<ObjectRefId, ObjectInstance> restoredById = new LinkedHashMap<>();
        for (ObjectRefId id : orderedIds) {
            ObjectInstance restored = objectById(id);
            restoredById.put(id, restored);
            assertNotSame(sourceById.get(id), restored);
            assertEquals(types.get(id), restored.getClass());
            assertEquals(slots.get(id).intValue(), slotOf(restored));
            if (restored instanceof BossChild) {
                assertSame(restoredBoss, readObjectField(restored, "boss"));
            }
        }
        return new GraphRoundTrip(restoredBoss, restoredById);
    }

    private List<ObjectRefId> idsForList(List<?> objects) {
        return objects.stream()
                .map(ObjectInstance.class::cast)
                .map(this::objectId)
                .toList();
    }

    private List<ObjectInstance> articulatedRootInventory(
            LbzFinalBoss2Instance boss) throws Exception {
        return readListField(boss, "graphChildren").stream()
                .filter(this::isArticulatedChild)
                .map(ObjectInstance.class::cast)
                .toList();
    }

    private List<ObjectInstance> articulatedObjectManagerInventory() {
        return objectManager.getActiveObjects().stream()
                .filter(object -> !object.isDestroyed())
                .filter(this::isArticulatedChild)
                .toList();
    }

    private boolean isArticulatedChild(Object object) {
        if (!(object instanceof BossChild child)) {
            return false;
        }
        return switch (child.kindForTest()) {
            case ARM_GRAPH, ARM_ATTACHMENT, ARM_VISUAL, ARM_OUTER_COLLISION,
                    ARM_SEGMENT, ARM_JOINT, GRAB, ARM_UPPER_COLLISION -> true;
            default -> false;
        };
    }

    private void assertExactArticulatedInventory(
            LbzFinalBoss2Instance boss,
            List<ObjectRefId> expectedIds,
            Map<ObjectRefId, Integer> originalSlots) throws Exception {
        assertEquals(expectedIds, idsForList(articulatedObjectManagerInventory()),
                "ObjectManager articulated inventory must match the literal source disposition");
        assertEquals(expectedIds, idsForList(articulatedRootInventory(boss)),
                "root inventory must match ObjectManager in original allocation order");
        for (ObjectRefId id : expectedIds) {
            assertEquals(originalSlots.get(id).intValue(), slotOf(objectById(id)),
                    "surviving articulated child must retain its original SST slot");
        }
    }

    private void assertFlickerState(
            BossChild child, int expectedXVelocity, int expectedYVelocity) {
        assertTrue(child.isFlickerMoveForTest());
        assertEquals(0, child.getCollisionFlags());
        assertEquals(expectedXVelocity, child.flickerXVelocityForTest());
        assertEquals(expectedYVelocity, child.flickerYVelocityForTest());
    }

    private boolean sourceFlickerMoveSurvives(BossChild child) {
        try {
            int nextX = ((child.getX() & 0xFFFF) << 16)
                    | (readIntField(child, "currentXSub") & 0xFFFF);
            int nextY = ((child.getY() & 0xFFFF) << 16)
                    | (readIntField(child, "currentYSub") & 0xFFFF);
            nextX += ((short) child.flickerXVelocityForTest()) << 8;
            nextY += ((short) child.flickerYVelocityForTest()) << 8;
            int x = (nextX >>> 16) & 0xFFFF;
            int y = (nextY >>> 16) & 0xFFFF;
            int coarseBack = ((fixture.camera().getX() & 0xFFFF) - 0x80) & 0xFF80;
            int xDelta = ((x & 0xFF80) - coarseBack) & 0xFFFF;
            int yDelta = (y - (fixture.camera().getY() & 0xFFFF) + 0x80) & 0xFFFF;
            return Integer.compareUnsigned(xDelta, 0x280) <= 0
                    && Integer.compareUnsigned(yDelta, 0x200) <= 0;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private Map<ObjectRefId, ChildMotionOracle> childMotionOracles(
            List<ObjectRefId> ids) throws Exception {
        Map<ObjectRefId, ChildMotionOracle> oracles = new LinkedHashMap<>();
        for (ObjectRefId id : ids) {
            BossChild child = assertInstanceOf(BossChild.class, objectById(id));
            oracles.put(id, new ChildMotionOracle(
                    child.getX(), child.getY(),
                    readIntField(child, "currentXSub"),
                    readIntField(child, "currentYSub"),
                    child.flickerXVelocityForTest(),
                    child.flickerYVelocityForTest(),
                    readBooleanField(child, "flickerVisible")));
        }
        return oracles;
    }

    private <T extends AbstractObjectInstance> T createChild(
            Class<T> type, Class<?>[] parameterTypes, Object... arguments) throws Exception {
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

    private void updateFixedPointBoundary(
            LbzFinalBoss2Instance boss,
            DefeatDebrisChild debris,
            EscapeFloorChild floor,
            LbzFinalBoss2EggCapsuleInstance capsule,
            S3kBossExplosionChild explosion) {
        boss.update(0, fixture.sprite());
        debris.update(0, fixture.sprite());
        floor.update(0, fixture.sprite());
        capsule.update(0, fixture.sprite());
        explosion.update(0, fixture.sprite());
    }

    private FixedPointOracle fixedPointOracle(
            LbzFinalBoss2Instance boss,
            DefeatDebrisChild debris,
            EscapeFloorChild floor,
            LbzFinalBoss2EggCapsuleInstance capsule,
            S3kBossExplosionChild explosion) throws Exception {
        return new FixedPointOracle(
                boss.getCentreX(), boss.getCentreY(),
                readIntField(boss, "xSub"), readIntField(boss, "ySub"),
                fixture.sprite().getCentreX() & 0xFFFF,
                fixture.sprite().getCentreY() & 0xFFFF,
                fixture.sprite().getXSubpixelRaw(), fixture.sprite().getYSubpixelRaw(),
                debris.getX(), debris.getY(),
                readIntField(debris, "xSub"), readIntField(debris, "ySub"),
                floor.getX(), floor.getY(),
                readIntField(floor, "xSub"), readIntField(floor, "ySub"),
                capsule.getX(), capsule.getY(), readIntField(capsule, "ySubpixel"),
                explosion.rawCursorForTest(), explosion.mappingFrameForTest(),
                explosion.rawTimerForTest(), explosion.nativeInitSfxPlayedForTest());
    }

    private LbzZoneRuntimeState currentLbzState() {
        return S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry())
                .orElseThrow();
    }

    private Sonic3kLBZEvents currentLbzEvents() {
        return ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .getLbzEvents();
    }

    private void fillAllSstSlots() {
        int ordinal = objectManager.activeObjectsOfType(SstFiller.class).size();
        while (objectManager.hasFreeDynamicSlot()) {
            int subtype = ordinal++;
            SstFiller filler = objectManager.createDynamicObject(() ->
                    new SstFiller(new ObjectSpawn(0, 0, 0x7F,
                            subtype, 0, false, 0)));
            assertFalse(filler.isDestroyed());
        }
        assertFalse(objectManager.hasFreeDynamicSlot());
    }

    private void freeLowestFillers(int count) {
        List<SstFiller> fillers = objectManager.activeObjectsOfType(SstFiller.class).stream()
                .filter(filler -> !filler.isDestroyed())
                .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                .toList();
        assertTrue(fillers.size() >= count,
                "test setup needs " + count + " removable SST fillers");
        for (int i = 0; i < count; i++) {
            objectManager.removeDynamicObject(fillers.get(i));
        }
        assertTrue(objectManager.hasFreeDynamicSlot());
    }

    private LbzFinalBoss2Instance strictRestoreBoss(ObjectRefId bossId) {
        registry.restore(registry.capture());
        return objectById(LbzFinalBoss2Instance.class, bossId);
    }

    private void assertEveryRootEdgeHasLiveIdentity(LbzFinalBoss2Instance boss)
            throws Exception {
        for (Object edge : readListField(boss, "graphChildren")) {
            ObjectInstance object = assertInstanceOf(ObjectInstance.class, edge);
            assertFalse(object.isDestroyed());
            objectId(object);
        }
    }

    private void assertEveryListEdgeHasLiveIdentity(Object owner, String fieldName)
            throws Exception {
        for (Object edge : readListField(owner, fieldName)) {
            assertNotNull(edge, fieldName + " must not retain a failed allocation");
            ObjectInstance object = assertInstanceOf(ObjectInstance.class, edge);
            assertFalse(object.isDestroyed());
            objectId(object);
        }
    }

    private static void invokeNoArg(Object target, String methodName) throws Exception {
        invokePrivate(target, methodName, new Class<?>[0]);
    }

    private static Object invokePrivate(
            Object target, String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static <T extends BossChild> T recordBossChild(
            LbzFinalBoss2Instance boss,
            LbzFinalBoss2Instance.ChildKind kind,
            T child) throws Exception {
        Method method = LbzFinalBoss2Instance.class.getDeclaredMethod(
                "recordChild", LbzFinalBoss2Instance.ChildKind.class, BossChild.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        T recorded = (T) method.invoke(boss, kind, child);
        assertNotNull(recorded);
        return recorded;
    }

    private static ShakeOracle shakeOracle(LbzZoneRuntimeState state) {
        return new ShakeOracle(
                state.getTimedShakeCountdown(),
                state.getTimedShakePreparedOffset(),
                state.getTimedShakeAppliedOffset());
    }

    private static GlobalBoundaryOracle globalBoundaryOracle(
            LbzFinalBoss2Instance boss) throws Exception {
        return new GlobalBoundaryOracle(
                GameServices.gameState().getCurrentBossId(),
                readObjectField(boss, "defeatStage").toString(),
                readBooleanField(boss, "rootHidden"),
                boss.getCentreX(), boss.getCentreY(),
                readIntField(boss, "flags"),
                readIntField(boss, "statusBits"),
                boss.isHighPriority());
    }

    private void assertNoArticulatedDeletionCallback(ArmControllerChild controller) {
        for (java.lang.reflect.Method method : controller.getClass().getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(BossChild.class.isAssignableFrom(parameter),
                        "controller must not declare child cleanup callback " + method.getName());
            }
        }
    }

    private List<VisibleExplosionOracle> visibleExplosionOracles() {
        return objectManager.activeObjectsOfType(S3kBossExplosionChild.class).stream()
                .filter(explosion -> !explosion.isDestroyed())
                .map(explosion -> {
                    try {
                        return new VisibleExplosionOracle(
                                objectId(explosion), explosion, slotOf(explosion),
                                explosion.getX(), explosion.getY(),
                                explosion.rawCursorForTest(),
                                explosion.mappingFrameForTest(),
                                explosion.rawTimerForTest(),
                                explosion.nativeInitSfxForTest(),
                                explosion.nativeInitSfxPlayedForTest());
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                })
                .toList();
    }

    private void assertVisibleExplosionOraclesRestored(
            List<VisibleExplosionOracle> expected) throws Exception {
        for (VisibleExplosionOracle oracle : expected) {
            S3kBossExplosionChild restored = objectById(
                    S3kBossExplosionChild.class, oracle.id());
            assertNotSame(oracle.source(), restored);
            assertEquals(oracle.slot(), slotOf(restored));
            assertEquals(oracle.x(), restored.getX());
            assertEquals(oracle.y(), restored.getY());
            assertEquals(oracle.rawCursor(), restored.rawCursorForTest());
            assertEquals(oracle.mappingFrame(), restored.mappingFrameForTest());
            assertEquals(oracle.rawTimer(), restored.rawTimerForTest());
            assertEquals(oracle.nativeInitSfx(), restored.nativeInitSfxForTest());
            assertEquals(oracle.nativeInitSfxPlayed(), restored.nativeInitSfxPlayedForTest());
        }
    }

    private <T extends ObjectInstance> T onlyLive(Class<T> type) {
        List<T> live = objectManager.activeObjectsOfType(type).stream()
                .filter(object -> !object.isDestroyed())
                .toList();
        assertEquals(1, live.size(), "expected one live " + type.getSimpleName());
        return live.getFirst();
    }

    private record GraphRoundTrip(
            LbzFinalBoss2Instance boss,
            Map<ObjectRefId, ObjectInstance> objects) {

        private <T extends ObjectInstance> T object(ObjectRefId id, Class<T> type) {
            ObjectInstance object = objects.get(id);
            assertNotNull(object, "restored graph is missing " + id);
            return type.cast(object);
        }
    }

    private record VisibleExplosionOracle(
            ObjectRefId id,
            S3kBossExplosionChild source,
            int slot,
            int x,
            int y,
            int rawCursor,
            int mappingFrame,
            int rawTimer,
            boolean nativeInitSfx,
            boolean nativeInitSfxPlayed) {
    }

    private record ChildMotionOracle(
            int x,
            int y,
            int xSub,
            int ySub,
            int xVelocity,
            int yVelocity,
            boolean visible) {
    }

    private record FixedPointOracle(
            int rootX,
            int rootY,
            int rootXSub,
            int rootYSub,
            int playerX,
            int playerY,
            int playerXSub,
            int playerYSub,
            int debrisX,
            int debrisY,
            int debrisXSub,
            int debrisYSub,
            int floorX,
            int floorY,
            int floorXSub,
            int floorYSub,
            int capsuleX,
            int capsuleY,
            int capsuleYSub,
            int explosionCursor,
            int explosionFrame,
            int explosionTimer,
            boolean explosionSfxPlayed) {
    }

    private record ShakeOracle(int countdown, int prepared, int applied) {
    }

    private record GlobalBoundaryOracle(
            int bossId,
            String stage,
            boolean hidden,
            int x,
            int y,
            int flags,
            int statusBits,
            boolean artTileHigh) {
    }

    private static final class SstFiller extends AbstractObjectInstance
            implements SpawnRewindRecreatable {
        private SstFiller(ObjectSpawn spawn) {
            super(spawn, "BigArmSstFiller");
        }

        @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(
                List<com.openggf.graphics.GLCommand> commands) { }
        @Override public int getX() { return 0; }
        @Override public int getY() { return 0; }
        @Override public boolean isPersistent() { return true; }
    }

    private LbzFinalBoss2Instance spawnBoss() {
        return objectManager.createDynamicObject(() -> new LbzFinalBoss2Instance(new ObjectSpawn(
                0x44A0, 0x0780, Sonic3kObjectIds.LBZ_FINAL_BOSS_2,
                0, 0, false, 0)));
    }

    private void stepUntil(BooleanSupplier condition, int limit) {
        stepUntil(condition, limit, 0x42A0, 0x03E0, false);
    }

    private void stepUntil(BooleanSupplier condition, int limit,
                           int playerX, int playerY, boolean air) {
        for (int frame = 0; frame < limit && !condition.getAsBoolean(); frame++) {
            pinPlayer(playerX, playerY, air);
            fixture.stepIdleFrames(1);
        }
        assertTrue(condition.getAsBoolean(), "state not reached within " + limit + " frames");
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

    private ObjectRefId objectId(ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "missing ObjectManager identity for " + object.getClass().getSimpleName());
        return id;
    }

    private Map<ObjectInstance, ObjectRefId> idsFor(List<? extends ObjectInstance> objects) {
        Map<ObjectInstance, ObjectRefId> ids = new LinkedHashMap<>();
        for (ObjectInstance object : objects) {
            ids.put(object, objectId(object));
        }
        return ids;
    }

    private Map<ObjectRefId, Integer> slotsFor(
            ObjectInstance owner, Map<ObjectInstance, ObjectRefId> graphIds) {
        Map<ObjectRefId, Integer> slots = new LinkedHashMap<>();
        slots.put(objectId(owner), slotOf(owner));
        graphIds.forEach((object, id) -> slots.put(id, slotOf(object)));
        return slots;
    }

    private void assertSlots(Map<ObjectRefId, Integer> expected) {
        expected.forEach((id, slot) -> assertEquals(
                slot.intValue(), slotOf(objectById(id)),
                "restored object " + id + " must retain its SST slot"));
    }

    private static int slotOf(ObjectInstance object) {
        int slot = ((AbstractObjectInstance) object).getSlotIndex();
        assertTrue(slot >= 0);
        return slot;
    }

    private <T extends ObjectInstance> T objectById(Class<T> type, ObjectRefId id) {
        return type.cast(objectById(id));
    }

    private ObjectInstance objectById(ObjectRefId id) {
        return findObjectById(id)
                .orElseThrow(() -> new AssertionError("missing restored object " + id));
    }

    private Optional<ObjectInstance> findObjectById(ObjectRefId id) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> !object.isDestroyed())
                .filter(object -> id.equals(objectManager.captureIdentityContext()
                        .requireIdentityTable().idFor(object)))
                .findFirst();
    }

    private static <T> T onlyChild(LbzFinalBoss2Instance boss,
                                   LbzFinalBoss2Instance.ChildKind kind,
                                   Class<T> type) {
        List<T> matches = children(boss, kind, type);
        assertEquals(1, matches.size(), "expected one " + kind);
        return matches.getFirst();
    }

    private static <T> List<T> children(LbzFinalBoss2Instance boss,
                                        LbzFinalBoss2Instance.ChildKind kind,
                                        Class<T> type) {
        return boss.childrenOfKindForTest(kind).stream()
                .map(type::cast)
                .toList();
    }

    private static Object readObjectField(Object target, String name) throws Exception {
        return field(target, name).get(target);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> readListField(Object target, String name) throws Exception {
        return (List<Object>) field(target, name).get(target);
    }

    private static int readIntField(Object target, String name) throws Exception {
        return field(target, name).getInt(target);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        field(target, name).setInt(target, value);
    }

    private static void setBooleanField(Object target, String name, boolean value)
            throws Exception {
        field(target, name).setBoolean(target, value);
    }

    private static void setObjectField(Object target, String name, Object value)
            throws Exception {
        field(target, name).set(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnumField(Object target, String name, String value) throws Exception {
        Field enumField = field(target, name);
        enumField.set(target, Enum.valueOf((Class<? extends Enum>) enumField.getType(), value));
    }

    private static boolean readBooleanField(Object target, String name) throws Exception {
        return field(target, name).getBoolean(target);
    }

    private static int readIntUnchecked(Object target, String name) {
        try {
            return readIntField(target, name);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Search inherited native object state.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
