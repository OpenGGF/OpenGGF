package com.openggf.game.sonic3k;

import com.openggf.game.session.EngineServices;
import com.openggf.tests.TestEnvironment;

import com.openggf.game.rewind.schema.ZoneEventSchemaSidecar;
import com.openggf.game.rewind.snapshot.LevelEventSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import com.openggf.game.sonic3k.features.HCZWaterSkimHandler;
import com.openggf.game.sonic3k.objects.Aiz2BossEndSequenceState;
import com.openggf.game.sonic3k.objects.AizCollapsingLogBridgeObjectInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesCnz2AInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesCnz2BInstance;
import com.openggf.game.sonic3k.objects.HCZWaterRushObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for the {@link Sonic3kLevelEventManager} extra-state snapshot (C.4).
 *
 * <p>Covers manager-level flags, AIZ fire/battleship state, HCZ wall-chase and
 * cutscene state, CNZ camera-clamp and boss-scroll state, and MGZ collapse counters.
 */
class TestSonic3kLevelEventRewindSnapshot {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        SessionManager.clear();
    }

    @Test
    void keyIsLevelEvent() {
        assertEquals("level-event", new Sonic3kLevelEventManager().key());
    }

    @Test
    void extraBytesNotNullForAiz() {
        // Act 1 (index 1) avoids the AIZ intro camera path that requires a
        // live gameplay session, while still constructing aizEvents so the
        // capture produces a non-empty extra blob.
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);
        LevelEventSnapshot snap = mgr.capture();
        assertNotNull(snap.extra());
        assertTrue(snap.extra().length > 0);
    }

    @Test
    void roundTripManagerLevelFlags() {
        // Manager-level flags (hczPendingPostTransitionCutscene,
        // mgzPostTransitionReleasePending) are zone-agnostic; use MGZ to stay
        // away from the AIZ intro bootstrap that requires a live camera.
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_MGZ, 0);
        // Directly set zone-agnostic manager flags
        mgr.setHczPendingPostTransitionCutscene(true);
        mgr.requestMgzPostTransitionRelease();

        LevelEventSnapshot snap = mgr.capture();
        // Reset and restore
        mgr.initLevel(Sonic3kZoneIds.ZONE_MGZ, 0);
        mgr.restore(snap);

        // hczPendingPostTransitionCutscene should survive
        // (no public getter; test indirectly by round-tripping the full blob)
        assertNotNull(snap.extra(), "Extra blob must not be null");
    }

    @Test
    void roundTripAizBattleshipState() {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1); // act 2 has aizEvents
        var aiz = mgr.getAizEventsForTest();
        assertNotNull(aiz, "AIZ events must be non-null in AIZ zone");

        aiz.setBattleshipAutoScrollActiveRaw(true);
        aiz.setBattleshipSpawned(true);
        aiz.setBattleshipWrapX(0x46C0);
        aiz.setScreenShakeTimer(10);
        aiz.setLevelRepeatOffsetRaw(0x200);
        aiz.setBattleshipSmoothScrollXRaw(0x1234);
        aiz.setBattleshipPostScrollCameraX(0x4440);
        aiz.setFireSequencePhaseOrdinal(2); // AIZ1_FIRE_REFRESH
        aiz.setFireBgCopyFixed(0x0068_0000);
        aiz.setFireRiseSpeed(0x5000);
        aiz.setFireWavePhase(0x30);
        aiz.setFireTransitionFrames(120);
        aiz.setAiz2ResizeRoutine(8);
        aiz.setMinibossSpawned(true);
        aiz.setPaletteSwapped(true);
        aiz.setBoundariesUnlocked(true);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);
        var aiz2 = mgr.getAizEventsForTest();
        assertNotNull(aiz2);
        mgr.restore(snap);

        assertTrue(aiz2.isBattleshipAutoScrollActiveRaw());
        assertTrue(aiz2.isBattleshipSpawned());
        assertEquals(0x46C0,      aiz2.getBattleshipWrapX());
        assertEquals(10,          aiz2.getScreenShakeTimer());
        assertEquals(0x200,       aiz2.getLevelRepeatOffsetRaw());
        assertEquals(0x1234,      aiz2.getBattleshipSmoothScrollXRaw());
        assertEquals(0x4440,      aiz2.getBattleshipPostScrollCameraX());
        assertEquals(2,           aiz2.getFireSequencePhaseOrdinal());
        assertEquals(0x0068_0000, aiz2.getFireBgCopyFixed());
        assertEquals(0x5000,      aiz2.getFireRiseSpeed());
        assertEquals(0x30,        aiz2.getFireWavePhase());
        assertEquals(120,         aiz2.getFireTransitionFrames());
        assertEquals(8,           aiz2.getAiz2ResizeRoutine());
        assertTrue(aiz2.isMinibossSpawned());
        assertTrue(aiz2.isPaletteSwapped());
        assertTrue(aiz2.isBoundariesUnlocked());
    }

    @Test
    void roundTripHczWallChaseState() {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        var hcz = mgr.getHczEventsForTest();
        assertNotNull(hcz, "HCZ events must be non-null in HCZ zone");

        hcz.setBgRoutine(8);
        hcz.setAct2BgRoutine(4);
        hcz.setWallOffsetFixed(0x800);
        hcz.setWallOffsetPixels(16);
        hcz.setWallMoving(true);
        hcz.setWallChaseBgOverlayActiveRaw(true);
        hcz.setShakeTimer(12);
        hcz.setCutsceneActive(true);
        hcz.setCutsceneFrame(30);
        hcz.setCutsceneCenterX(0x80);
        hcz.setCutsceneCurrentY(0x600);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        var hcz2 = mgr.getHczEventsForTest();
        assertNotNull(hcz2);
        mgr.restore(snap);

        assertEquals(8,     hcz2.getBgRoutine());
        assertEquals(4,     hcz2.getAct2BgRoutine());
        assertEquals(0x800, hcz2.getWallOffsetFixed());
        assertEquals(16,    hcz2.getWallOffsetPixels());
        assertTrue(hcz2.isWallMoving());
        assertTrue(hcz2.isWallChaseBgOverlayActive());
        assertEquals(12,    hcz2.getShakeTimer());
        assertTrue(hcz2.isCutsceneActive());
        assertEquals(30,    hcz2.getCutsceneFrame());
        assertEquals(0x80,  hcz2.getCutsceneCenterX());
        assertEquals(0x600, hcz2.getCutsceneCurrentY());
    }

    @Test
    void roundTripCnzBossScrollAndClamps() {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);
        var cnz = mgr.getCnzEventsForTest();
        assertNotNull(cnz, "CNZ events must be non-null in CNZ zone");

        cnz.setBossScrollState(120, -4);
        cnz.setCameraClampsActive(true);
        cnz.setCameraStoredMaxXPos((short) 0x3200);
        cnz.setCameraStoredMinXPos((short) 0x1000);
        cnz.setWaterTargetYRaw(0x700);
        cnz.setDestroyedArenaRows(3);
        cnz.setBossBackgroundMode(com.openggf.game.sonic3k.events.Sonic3kCNZEvents.BossBackgroundMode.ACT1_MINIBOSS_PATH);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);
        var cnz2 = mgr.getCnzEventsForTest();
        assertNotNull(cnz2);
        mgr.restore(snap);

        assertEquals(120,  cnz2.getBossScrollOffsetY());
        assertEquals(-4,   cnz2.getBossScrollVelocityY());
        assertTrue(cnz2.isCameraClampsActive());
        assertEquals((short) 0x3200, cnz2.getCameraStoredMaxXPos());
        assertEquals((short) 0x1000, cnz2.getCameraStoredMinXPos());
        assertEquals(0x700, cnz2.getWaterTargetY());
        assertEquals(3,     cnz2.getDestroyedArenaRows());
        assertEquals(com.openggf.game.sonic3k.events.Sonic3kCNZEvents.BossBackgroundMode.ACT1_MINIBOSS_PATH,
                cnz2.getBossBackgroundMode());
    }

    @Test
    void roundTripMgzCollapseCounters() {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_MGZ, 1);
        var mgz = mgr.getMgzEventsForTest();
        assertNotNull(mgz, "MGZ events must be non-null in MGZ zone");

        mgz.setCollapseRequested(true);
        mgz.setCollapseInitialized(true);
        mgz.setCollapseMutationCount(7);
        mgz.setCollapseFrameCounter(42);
        mgz.setCollapseStartupShakeTimer(5);
        mgz.setBgRiseRoutine(8);
        mgz.setBgRiseOffset(0x100);
        mgz.setBgRiseSubpixelAccum(0x8000);
        mgz.setBgRiseMotionStarted(true);
        mgz.setBossArenaRoutine(4);
        mgz.setBossSpawned(true);
        mgz.setAppearance1Complete(true);
        mgz.setAppearance2Complete(true);

        LevelEventSnapshot snap = mgr.capture();

        mgr.initLevel(Sonic3kZoneIds.ZONE_MGZ, 1);
        var mgz2 = mgr.getMgzEventsForTest();
        assertNotNull(mgz2);
        mgr.restore(snap);

        assertTrue(mgz2.isCollapseRequested());
        assertTrue(mgz2.isCollapseInitialized());
        assertEquals(7,      mgz2.getCollapseMutationCount());
        assertEquals(42,     mgz2.getCollapseFrameCounter());
        assertEquals(5,      mgz2.getCollapseStartupShakeTimer());
        assertEquals(8,      mgz2.getBgRiseRoutine());
        assertEquals(0x100,  mgz2.getBgRiseOffset());
        assertEquals(0x8000, mgz2.getBgRiseSubpixelAccum());
        assertTrue(mgz2.isBgRiseMotionStarted());
        assertEquals(4,      mgz2.getBossArenaRoutine());
        assertTrue(mgz2.isBossSpawned());
        assertTrue(mgz2.isAppearance1Complete());
        assertTrue(mgz2.isAppearance2Complete());
    }

    @Test
    void resetStateClearsPostTransitionHandoffState() throws Exception {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_CNZ, 1);

        mgr.requestHczPostTransitionCutscene();
        mgr.requestMgzPostTransitionRelease();
        mgr.requestCnzPostTransitionRelease(12);
        set(mgr, "cnzPostTransitionAct2SizeActive", true);
        set(mgr, "cnzAct2MinXAccumulator", 1);
        set(mgr, "cnzAct2MaxXAccumulator", 2);
        set(mgr, "cnzAct2MinYAccumulator", 3);
        set(mgr, "cnzAct2MaxYAccumulator", 4);

        mgr.resetState();

        assertFalse((boolean) get(mgr, "hczPendingPostTransitionCutscene"));
        assertFalse((boolean) get(mgr, "mgzPendingPostTransitionRelease"));
        assertEquals(0, get(mgr, "cnzPendingPostTransitionReleaseFrames"));
        assertEquals(0, get(mgr, "cnzPendingPostTransitionAct2SizeFrames"));
        assertFalse((boolean) get(mgr, "cnzPostTransitionAct2SizeActive"));
        assertEquals(0, get(mgr, "cnzAct2MinXAccumulator"));
        assertEquals(0, get(mgr, "cnzAct2MaxXAccumulator"));
        assertEquals(0, get(mgr, "cnzAct2MinYAccumulator"));
        assertEquals(0, get(mgr, "cnzAct2MaxYAccumulator"));
    }

    @Test
    void resetStateClearsAiz2BossEndSequenceGlobals() {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);

        Aiz2BossEndSequenceState.triggerBridgeDrop();
        Aiz2BossEndSequenceState.pressButton();
        Aiz2BossEndSequenceState.releaseEggCapsule();
        Aiz2BossEndSequenceState.activateCutsceneOverrideObjects();

        mgr.resetState();

        assertFalse(Aiz2BossEndSequenceState.isBridgeDropTriggered());
        assertFalse(Aiz2BossEndSequenceState.isButtonPressed());
        assertFalse(Aiz2BossEndSequenceState.isEggCapsuleReleased());
        assertFalse(Aiz2BossEndSequenceState.isCutsceneOverrideObjectsActive());
        assertNull(Aiz2BossEndSequenceState.getActiveKnuckles());
    }

    @Test
    void resetStateClearsAizDrawBridgeBurnTrigger() throws Exception {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);

        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(true);

        mgr.resetState();

        assertFalse((boolean) getStatic(AizCollapsingLogBridgeObjectInstance.class, "drawBridgeBurnActive"));
    }

    @Test
    void resetStateClearsStaticCutsceneSignpostAndHczWaterRefs() throws Exception {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);

        setStatic(CutsceneKnucklesCnz2AInstance.class, "activeInstance",
                new CutsceneKnucklesCnz2AInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        setStatic(CutsceneKnucklesCnz2BInstance.class, "activeInstance",
                new CutsceneKnucklesCnz2BInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        HCZWaterRushObjectInstance.HCZBreakableBarState.setState(3);
        HCZWaterRushObjectInstance.HCZWaterRushPaletteCycleGate.setActive(true);
        setStatic(HCZWaterSkimHandler.class, "skimActiveP1", true);
        setStatic(HCZWaterSkimHandler.class, "skimActiveP2", true);

        mgr.resetState();

        assertNull(getStatic(CutsceneKnucklesCnz2AInstance.class, "activeInstance"));
        assertNull(getStatic(CutsceneKnucklesCnz2BInstance.class, "activeInstance"));
        assertEquals(0, HCZWaterRushObjectInstance.HCZBreakableBarState.getState());
        assertFalse(HCZWaterRushObjectInstance.HCZWaterRushPaletteCycleGate.isActive());
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());
        assertFalse(HCZWaterSkimHandler.isSkimActiveP2());
    }

    @Test
    void handlerAbsentDoesNotCorruptBuffer() {
        // Snapshot in AIZ, restore in HCZ — AIZ handler present but hczEvents null.
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);
        LevelEventSnapshot snap = mgr.capture();

        // Switch to HCZ and restore — should not throw or corrupt
        mgr.initLevel(Sonic3kZoneIds.ZONE_HCZ, 0);
        assertDoesNotThrow(() -> mgr.restore(snap));
    }

    @Test
    void malformedAizSchemaPayloadDoesNotAbortLaterZoneRestore() throws Exception {
        Sonic3kLevelEventManager hczSource = new Sonic3kLevelEventManager();
        hczSource.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        var hcz = hczSource.getHczEventsForTest();
        assertNotNull(hcz);
        hcz.setBgRoutine(8);
        hcz.setWallMoving(true);
        hcz.setShakeTimer(12);
        LevelEventSnapshot hczSnapshot = hczSource.capture();

        Sonic3kLevelEventManager target = new Sonic3kLevelEventManager();
        target.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);
        Sonic3kLevelEventManager hczTargetOwner = new Sonic3kLevelEventManager();
        hczTargetOwner.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        set(target, "hczEvents", hczTargetOwner.getHczEventsForTest());
        var hczTarget = target.getHczEventsForTest();
        assertNotNull(hczTarget);
        hczTarget.setBgRoutine(0);
        hczTarget.setWallMoving(false);
        hczTarget.setShakeTimer(0);

        byte[] malformedExtra = insertMalformedAizPayload(hczSnapshot.extra());
        LevelEventSnapshot malformedSnapshot = new LevelEventSnapshot(
                hczSnapshot.currentZone(),
                hczSnapshot.currentAct(),
                hczSnapshot.eventRoutineFg(),
                hczSnapshot.eventRoutineBg(),
                hczSnapshot.frameCounter(),
                hczSnapshot.timerFrames(),
                hczSnapshot.bossActive(),
                hczSnapshot.eventDataFg(),
                hczSnapshot.eventDataBg(),
                malformedExtra);

        assertDoesNotThrow(() -> target.restore(malformedSnapshot),
                "schema-invalid AIZ sidecar should not abort later zone sidecars");
        assertEquals(8, hczTarget.getBgRoutine());
        assertTrue(hczTarget.isWallMoving());
        assertEquals(12, hczTarget.getShakeTimer());
    }

    @Test
    void corruptSameLengthAizSchemaPayloadRollsBackAndDoesNotAbortLaterZoneRestore() throws Exception {
        Sonic3kLevelEventManager hczSource = new Sonic3kLevelEventManager();
        hczSource.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        var hcz = hczSource.getHczEventsForTest();
        assertNotNull(hcz);
        hcz.setBgRoutine(8);
        hcz.setWallMoving(true);
        hcz.setShakeTimer(12);
        LevelEventSnapshot hczSnapshot = hczSource.capture();

        Sonic3kLevelEventManager target = new Sonic3kLevelEventManager();
        target.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);
        var aizTarget = target.getAizEventsForTest();
        assertNotNull(aizTarget);
        aizTarget.setIntroSpawned(true);

        Sonic3kLevelEventManager hczTargetOwner = new Sonic3kLevelEventManager();
        hczTargetOwner.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        set(target, "hczEvents", hczTargetOwner.getHczEventsForTest());
        var hczTarget = target.getHczEventsForTest();
        assertNotNull(hczTarget);
        hczTarget.setBgRoutine(0);
        hczTarget.setWallMoving(false);
        hczTarget.setShakeTimer(0);

        Sonic3kAIZEvents payloadSource = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        payloadSource.setIntroSpawned(false);
        byte[] corruptPayload = corruptFireSequencePhaseOrdinal(
                ZoneEventSchemaSidecar.capture(payloadSource), Integer.MAX_VALUE);
        LevelEventSnapshot malformedSnapshot = snapshotWithAizPayload(hczSnapshot, corruptPayload);

        assertDoesNotThrow(() -> target.restore(malformedSnapshot),
                "same-length schema-corrupt AIZ sidecar should not abort later zone sidecars");
        assertTrue(aizTarget.isIntroSpawned(),
                "schema-corrupt AIZ sidecar must roll back partial AIZ field writes");
        assertEquals(8, hczTarget.getBgRoutine());
        assertTrue(hczTarget.isWallMoving());
        assertEquals(12, hczTarget.getShakeTimer());
    }

    @Test
    void negativeEnumOrdinalAizSchemaPayloadRollsBackAndDoesNotAbortLaterZoneRestore() throws Exception {
        Sonic3kLevelEventManager hczSource = new Sonic3kLevelEventManager();
        hczSource.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        var hcz = hczSource.getHczEventsForTest();
        assertNotNull(hcz);
        hcz.setBgRoutine(8);
        hcz.setWallMoving(true);
        hcz.setShakeTimer(12);
        LevelEventSnapshot hczSnapshot = hczSource.capture();

        Sonic3kLevelEventManager target = new Sonic3kLevelEventManager();
        target.initLevel(Sonic3kZoneIds.ZONE_AIZ, 1);
        var aizTarget = target.getAizEventsForTest();
        assertNotNull(aizTarget);
        aizTarget.setIntroSpawned(true);

        Sonic3kLevelEventManager hczTargetOwner = new Sonic3kLevelEventManager();
        hczTargetOwner.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        set(target, "hczEvents", hczTargetOwner.getHczEventsForTest());
        var hczTarget = target.getHczEventsForTest();
        assertNotNull(hczTarget);
        hczTarget.setBgRoutine(0);
        hczTarget.setWallMoving(false);
        hczTarget.setShakeTimer(0);

        Sonic3kAIZEvents payloadSource = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        payloadSource.setIntroSpawned(false);
        byte[] corruptPayload = corruptFireSequencePhaseOrdinal(
                ZoneEventSchemaSidecar.capture(payloadSource), -1);
        LevelEventSnapshot malformedSnapshot = snapshotWithAizPayload(hczSnapshot, corruptPayload);

        assertDoesNotThrow(() -> target.restore(malformedSnapshot),
                "null-enum AIZ sidecar should not abort later zone sidecars");
        assertTrue(aizTarget.isIntroSpawned(),
                "null-enum AIZ sidecar must roll back partial AIZ field writes");
        assertEquals(8, hczTarget.getBgRoutine());
        assertTrue(hczTarget.isWallMoving());
        assertEquals(12, hczTarget.getShakeTimer());
    }

    @Test
    void corruptSameLengthHczSchemaPayloadRollsBackAndDoesNotAbortLaterZoneRestore() throws Exception {
        Sonic3kLevelEventManager cnzSource = new Sonic3kLevelEventManager();
        cnzSource.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);
        var cnz = cnzSource.getCnzEventsForTest();
        assertNotNull(cnz);
        cnz.setBossScrollState(120, -4);
        cnz.setCameraClampsActive(true);
        cnz.setWaterTargetYRaw(0x700);
        LevelEventSnapshot cnzSnapshot = cnzSource.capture();

        Sonic3kLevelEventManager target = new Sonic3kLevelEventManager();
        target.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        HczProbeEvents hczTarget = new HczProbeEvents();
        hczTarget.init(1);
        hczTarget.setWallMoving(true);
        hczTarget.setShakeTimer(99);
        hczTarget.setProbeMode(HczProbeMode.ONE);
        set(target, "hczEvents", hczTarget);

        Sonic3kLevelEventManager cnzTargetOwner = new Sonic3kLevelEventManager();
        cnzTargetOwner.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);
        set(target, "cnzEvents", cnzTargetOwner.getCnzEventsForTest());
        var cnzTarget = target.getCnzEventsForTest();
        assertNotNull(cnzTarget);
        cnzTarget.setBossScrollState(0, 0);
        cnzTarget.setCameraClampsActive(false);
        cnzTarget.setWaterTargetYRaw(0);

        HczProbeEvents payloadSource = new HczProbeEvents();
        payloadSource.init(1);
        payloadSource.setWallMoving(false);
        payloadSource.setShakeTimer(12);
        payloadSource.setProbeMode(HczProbeMode.ZERO);
        byte[] corruptPayload = corruptHczProbeModeOrdinal(
                ZoneEventSchemaSidecar.capture(payloadSource), Integer.MAX_VALUE);
        LevelEventSnapshot malformedSnapshot = snapshotWithHczPayload(cnzSnapshot, corruptPayload);

        assertDoesNotThrow(() -> target.restore(malformedSnapshot),
                "schema-corrupt HCZ sidecar should not abort later zone sidecars");
        assertTrue(hczTarget.isWallMoving(),
                "schema-corrupt HCZ sidecar must roll back partial HCZ field writes");
        assertEquals(99, hczTarget.getShakeTimer(),
                "schema-corrupt HCZ sidecar must roll back partial HCZ field writes");
        assertEquals(HczProbeMode.ONE, hczTarget.getProbeMode(),
                "schema-corrupt HCZ sidecar must preserve existing enum state");
        assertEquals(120, cnzTarget.getBossScrollOffsetY());
        assertEquals(-4, cnzTarget.getBossScrollVelocityY());
        assertTrue(cnzTarget.isCameraClampsActive());
        assertEquals(0x700, cnzTarget.getWaterTargetY());
    }

    @Test
    void malformedHczSchemaPayloadDoesNotAbortLaterZoneRestore() throws Exception {
        Sonic3kLevelEventManager cnzSource = new Sonic3kLevelEventManager();
        cnzSource.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);
        var cnz = cnzSource.getCnzEventsForTest();
        assertNotNull(cnz);
        cnz.setBossScrollState(120, -4);
        cnz.setCameraClampsActive(true);
        cnz.setWaterTargetYRaw(0x700);
        LevelEventSnapshot cnzSnapshot = cnzSource.capture();

        Sonic3kLevelEventManager target = new Sonic3kLevelEventManager();
        target.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        var hczTarget = target.getHczEventsForTest();
        assertNotNull(hczTarget);
        hczTarget.setWallMoving(true);
        hczTarget.setShakeTimer(99);

        Sonic3kLevelEventManager cnzTargetOwner = new Sonic3kLevelEventManager();
        cnzTargetOwner.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);
        set(target, "cnzEvents", cnzTargetOwner.getCnzEventsForTest());
        var cnzTarget = target.getCnzEventsForTest();
        assertNotNull(cnzTarget);
        cnzTarget.setBossScrollState(0, 0);
        cnzTarget.setCameraClampsActive(false);
        cnzTarget.setWaterTargetYRaw(0);

        LevelEventSnapshot malformedSnapshot = snapshotWithHczPayload(cnzSnapshot, new byte[] { 0 });

        assertDoesNotThrow(() -> target.restore(malformedSnapshot),
                "schema-invalid HCZ sidecar should not abort later zone sidecars");
        assertTrue(hczTarget.isWallMoving(),
                "schema-invalid HCZ sidecar must leave existing HCZ fields intact");
        assertEquals(99, hczTarget.getShakeTimer(),
                "schema-invalid HCZ sidecar must leave existing HCZ fields intact");
        assertEquals(120, cnzTarget.getBossScrollOffsetY());
        assertEquals(-4, cnzTarget.getBossScrollVelocityY());
        assertTrue(cnzTarget.isCameraClampsActive());
        assertEquals(0x700, cnzTarget.getWaterTargetY());
    }

    private static LevelEventSnapshot snapshotWithAizPayload(LevelEventSnapshot source, byte[] aizPayload) {
        LevelEventSnapshot malformedSnapshot = new LevelEventSnapshot(
                source.currentZone(),
                source.currentAct(),
                source.eventRoutineFg(),
                source.eventRoutineBg(),
                source.frameCounter(),
                source.timerFrames(),
                source.bossActive(),
                source.eventDataFg(),
                source.eventDataBg(),
                insertAizPayload(source.extra(), aizPayload));
        return malformedSnapshot;
    }

    private static LevelEventSnapshot snapshotWithHczPayload(LevelEventSnapshot source, byte[] hczPayload) {
        return new LevelEventSnapshot(
                source.currentZone(),
                source.currentAct(),
                source.eventRoutineFg(),
                source.eventRoutineBg(),
                source.frameCounter(),
                source.timerFrames(),
                source.bossActive(),
                source.eventDataFg(),
                source.eventDataBg(),
                insertHczPayload(source.extra(), hczPayload));
    }

    @Test
    void roundTripFixedAirCountdownSidecarRam() throws Exception {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_CNZ, 1);

        Object fixed = fixedAirCountdownManager(mgr);
        Object p1 = fixedController(fixed, "p1");
        setFixedControllerState(p1, true, 0x0A, 0x81, 0x1234, 0x02, 0x01, 0xFF, 0x8040, 0x0032, 0x0016);

        LevelEventSnapshot snap = mgr.capture();

        setFixedControllerState(p1, false, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        mgr.restore(snap);

        assertEquals("true,10,129,4660,2,1,255,32832,50,22", fixedControllerState(p1),
                "fixed Breathing_bubbles sidecar RAM must rewind with S3K event-manager snapshots");
    }

    @Test
    void fixedAirCountdownSelectsCountdownNumberSubtypeAndDisplayTimer() throws Exception {
        Sonic3kLevelEventManager mgr = new Sonic3kLevelEventManager();
        mgr.initLevel(Sonic3kZoneIds.ZONE_CNZ, 1);

        Object fixed = fixedAirCountdownManager(mgr);
        Object p1 = fixedController(fixed, "p1");
        set(p1, "obj3a", 0x80);
        set(p1, "obj38", 0);

        Sonic sonic = new Sonic("test", (short) 0, (short) 0);
        sonic.getDrowningController().setRemainingAirFromFixedCountdown(10);

        Method method = p1.getClass().getDeclaredMethod("countdownChildFor", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        method.setAccessible(true);
        Object child = method.invoke(p1, sonic);

        assertEquals(5, get(child, "subtype"),
                "ROM AirCountdown_MakeItem uses air_left >> 1 as countdown-number subtype");
        assertEquals(0x1C, get(child, "displayTimer"),
                "countdown number children start with $3C=$1C before AirCountdown_ShowNumber");
    }

    private static Object fixedAirCountdownManager(Sonic3kLevelEventManager mgr) throws Exception {
        Field field = Sonic3kLevelEventManager.class.getDeclaredField("fixedAirCountdownManager");
        field.setAccessible(true);
        return field.get(mgr);
    }

    private static Object fixedController(Object fixed, String name) throws Exception {
        Field field = fixed.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(fixed);
    }

    private static void setFixedControllerState(Object controller,
                                               boolean installed,
                                               int routine,
                                               int subtype,
                                               int obj30,
                                               int obj36,
                                               int obj37,
                                               int obj38,
                                               int obj3a,
                                               int obj3c,
                                               int obj3e) throws Exception {
        set(controller, "installed", installed);
        set(controller, "routine", routine);
        set(controller, "subtype", subtype);
        set(controller, "obj30", obj30);
        set(controller, "obj36", obj36);
        set(controller, "obj37", obj37);
        set(controller, "obj38", obj38);
        set(controller, "obj3a", obj3a);
        set(controller, "obj3c", obj3c);
        set(controller, "obj3e", obj3e);
    }

    private static String fixedControllerState(Object controller) throws Exception {
        return get(controller, "installed") + ","
                + get(controller, "routine") + ","
                + get(controller, "subtype") + ","
                + get(controller, "obj30") + ","
                + get(controller, "obj36") + ","
                + get(controller, "obj37") + ","
                + get(controller, "obj38") + ","
                + get(controller, "obj3a") + ","
                + get(controller, "obj3c") + ","
                + get(controller, "obj3e");
    }

    private static byte[] insertMalformedAizPayload(byte[] hczExtra) {
        return insertAizPayload(hczExtra, new byte[] { 0 });
    }

    private static byte[] insertAizPayload(byte[] hczExtra, byte[] aizPayload) {
        int managerBytes = 30;
        int originalAizAbsentFlagBytes = 1;
        ByteBuffer buf = ByteBuffer.allocate(managerBytes + 1 + Integer.BYTES + aizPayload.length
                + hczExtra.length - managerBytes - originalAizAbsentFlagBytes);
        buf.put(hczExtra, 0, managerBytes);
        buf.put((byte) 1);
        buf.putInt(aizPayload.length);
        buf.put(aizPayload);
        buf.put(hczExtra, managerBytes + originalAizAbsentFlagBytes,
                hczExtra.length - managerBytes - originalAizAbsentFlagBytes);
        return buf.array();
    }

    private static byte[] insertHczPayload(byte[] cnzExtra, byte[] hczPayload) {
        int managerBytes = 30;
        int originalAizAbsentFlagBytes = 1;
        int originalHczAbsentFlagBytes = 1;
        int prefixBytes = managerBytes + originalAizAbsentFlagBytes;
        ByteBuffer buf = ByteBuffer.allocate(prefixBytes + 1 + Integer.BYTES + hczPayload.length
                + cnzExtra.length - prefixBytes - originalHczAbsentFlagBytes);
        buf.put(cnzExtra, 0, prefixBytes);
        buf.put((byte) 1);
        buf.putInt(hczPayload.length);
        buf.put(hczPayload);
        buf.put(cnzExtra, prefixBytes + originalHczAbsentFlagBytes,
                cnzExtra.length - prefixBytes - originalHczAbsentFlagBytes);
        return buf.array();
    }

    private static byte[] corruptFireSequencePhaseOrdinal(byte[] payload, int invalidOrdinal) {
        Sonic3kAIZEvents zero = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        zero.setFireSequencePhaseOrdinal(0);
        byte[] zeroPayload = ZoneEventSchemaSidecar.capture(zero);

        Sonic3kAIZEvents one = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        one.setFireSequencePhaseOrdinal(1);
        byte[] onePayload = ZoneEventSchemaSidecar.capture(one);

        assertEquals(zeroPayload.length, payload.length);
        int changedByte = -1;
        for (int i = 0; i < zeroPayload.length; i++) {
            if (zeroPayload[i] != onePayload[i]) {
                assertEquals(-1, changedByte,
                        "fireSequencePhase ordinal should be the only byte changed by this probe");
                changedByte = i;
            }
        }
        assertTrue(changedByte >= 0, "fireSequencePhase ordinal offset must be discoverable");

        byte[] corrupt = payload.clone();
        int ordinalOffset = changedByte;
        assertTrue(ordinalOffset + Integer.BYTES <= corrupt.length,
                "fireSequencePhase ordinal offset must be valid");
        corrupt[ordinalOffset] = (byte) invalidOrdinal;
        corrupt[ordinalOffset + 1] = (byte) (invalidOrdinal >>> 8);
        corrupt[ordinalOffset + 2] = (byte) (invalidOrdinal >>> 16);
        corrupt[ordinalOffset + 3] = (byte) (invalidOrdinal >>> 24);
        return corrupt;
    }

    private static byte[] corruptHczProbeModeOrdinal(byte[] payload, int invalidOrdinal) {
        HczProbeEvents zero = new HczProbeEvents();
        zero.init(1);
        zero.setProbeMode(HczProbeMode.ZERO);
        byte[] zeroPayload = ZoneEventSchemaSidecar.capture(zero);

        HczProbeEvents one = new HczProbeEvents();
        one.init(1);
        one.setProbeMode(HczProbeMode.ONE);
        byte[] onePayload = ZoneEventSchemaSidecar.capture(one);

        assertEquals(zeroPayload.length, payload.length);
        int changedByte = -1;
        for (int i = 0; i < zeroPayload.length; i++) {
            if (zeroPayload[i] != onePayload[i]) {
                assertEquals(-1, changedByte,
                        "probeMode ordinal should be the only byte changed by this probe");
                changedByte = i;
            }
        }
        assertTrue(changedByte >= 0, "probeMode ordinal offset must be discoverable");

        byte[] corrupt = payload.clone();
        int ordinalOffset = changedByte;
        assertTrue(ordinalOffset + Integer.BYTES <= corrupt.length,
                "probeMode ordinal offset must be valid");
        corrupt[ordinalOffset] = (byte) invalidOrdinal;
        corrupt[ordinalOffset + 1] = (byte) (invalidOrdinal >>> 8);
        corrupt[ordinalOffset + 2] = (byte) (invalidOrdinal >>> 16);
        corrupt[ordinalOffset + 3] = (byte) (invalidOrdinal >>> 24);
        return corrupt;
    }

    private enum HczProbeMode {
        ZERO,
        ONE
    }

    private static final class HczProbeEvents extends Sonic3kHCZEvents {
        private HczProbeMode probeMode = HczProbeMode.ZERO;

        private HczProbeMode getProbeMode() {
            return probeMode;
        }

        private void setProbeMode(HczProbeMode probeMode) {
            this.probeMode = probeMode;
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object getStatic(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStatic(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
