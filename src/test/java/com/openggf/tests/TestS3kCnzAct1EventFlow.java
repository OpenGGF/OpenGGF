package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSignpostInstance;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardState;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzAct1EventFlow {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
    }

    @AfterEach
    void tearDown() {
        GameServices.audio().setBackend(new NullAudioBackend());
        SessionManager.clear();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void firstEventsFg5FallsThroughToFgRefresh_notActReload() {
        Sonic3kCNZEvents events = initCnzEvents(0);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_AFTER_BOSS);
        events.forceBossBackgroundMode(Sonic3kCNZEvents.BossBackgroundMode.ACT1_POST_BOSS);
        events.setEventsFg5(true);

        events.update(0, 0);

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH, events.getBackgroundRoutine(),
                "CNZ1BGE_FGRefresh must keep Background_collision_flag active until "
                        + "Draw_PlaneVertSingleBottomUp exhausts Draw_delayed_rowcount "
                        + "(docs/skdisasm/sonic3k.asm:107523-107539)");
        assertFalse(events.isAct2TransitionRequested());
        assertFalse(events.isEventsFg5());

        advanceCnzPostBossRefresh(events, 1, 15);

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH_2, events.getBackgroundRoutine());
    }

    @Test
    void secondEventsFg5AtTransitionStageRequestsSeamlessActSwap() {
        Sonic3kCNZEvents events = initCnzEvents(0);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_DO_TRANSITION);
        events.setEventsFg5(true);
        GameServices.camera().setMinX((short) 0x31E0);
        GameServices.camera().setMaxX((short) 0x3260);
        GameServices.camera().setMinY((short) 0x00E0);
        GameServices.camera().setMaxY((short) 0x0300);
        GameServices.camera().setMaxYTarget((short) 0x0300);

        events.update(0, 1);

        assertTrue(events.isAct2TransitionRequested());
        assertEquals(0x0301, events.getPendingZoneActWord());
        assertEquals(-0x3000, events.getTransitionWorldOffsetX());
        assertEquals(0x0200, events.getTransitionWorldOffsetY());

        SeamlessLevelTransitionRequest request =
                GameServices.level().consumeSeamlessTransitionRequest();
        assertNotNull(request);
        assertEquals(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL, request.type());
        assertEquals(Sonic3kZoneIds.ZONE_CNZ, request.targetZone());
        assertEquals(1, request.targetAct());
        assertEquals(RuntimeArtAdmissionPolicy.TITLE_OWNER,
                request.runtimeArtAdmissionPolicy(),
                "the carried Obj_LevelResults/Obj_TitleCard SST owns CNZ2 enemy admission");
        assertEquals(-0x3000, request.playerOffsetX());
        assertEquals(0x0200, request.playerOffsetY());
        assertEquals(-0x3000, request.cameraOffsetX());
        assertEquals(0x0200, request.cameraOffsetY());
        assertTrue(request.preserveLevelGamestate(),
                "CNZ1BGE_DoTransition reloads level data but does not clear the current timer/rings "
                        + "(docs/skdisasm/sonic3k.asm:107603-107653)");
        assertTrue(request.preserveOffsetCameraPosition(),
                "CNZ1BGE_DoTransition offsets Camera_X/Y_pos directly; it does not recenter after Load_Level");
        assertEquals(0x01E0, request.postTransitionMinX(),
                "CNZ1BGE_DoTransition subtracts d0=$3000 from Camera_min_X_pos "
                        + "(docs/skdisasm/sonic3k.asm:107642)");
        assertEquals(0x0260, request.postTransitionMaxX(),
                "CNZ1BGE_DoTransition subtracts d0=$3000 from Camera_max_X_pos "
                        + "(docs/skdisasm/sonic3k.asm:107643)");
        assertEquals(0x02E0, request.postTransitionMinY(),
                "CNZ1BGE_DoTransition subtracts d1=-$200 from Camera_min_Y_pos "
                        + "(docs/skdisasm/sonic3k.asm:107644)");
        assertEquals(0x0500, request.postTransitionMaxY(),
                "CNZ1BGE_DoTransition subtracts d1=-$200 from Camera_max_Y_pos "
                        + "(docs/skdisasm/sonic3k.asm:107645)");
        assertEquals(0x0500, request.postTransitionMaxYTarget(),
                "CNZ1BGE_DoTransition copies Camera_max_Y_pos to Camera_target_max_Y_pos "
                        + "(docs/skdisasm/sonic3k.asm:107646)");
    }

    @Test
    void productionPostBossChainAdvancesToReloadGateAndManagerSeesTransitionRequest() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_CNZ, 0);

        Sonic3kCNZEvents events = manager.getCnzEvents();
        events.forceBossBackgroundMode(Sonic3kCNZEvents.BossBackgroundMode.ACT1_POST_BOSS);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_AFTER_BOSS);

        events.setEventsFg5(true);
        events.update(0, 0);
        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH, events.getBackgroundRoutine());
        assertFalse(manager.isAct2TransitionRequested());

        advanceCnzPostBossRefresh(events, 1, 15);
        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH_2, events.getBackgroundRoutine());
        assertFalse(manager.isAct2TransitionRequested());

        advanceCnzPostBossRefresh(events, 16, 16);
        assertEquals(Sonic3kCNZEvents.BG_DO_TRANSITION, events.getBackgroundRoutine());
        assertFalse(manager.isAct2TransitionRequested());

        events.setEventsFg5(true);
        events.update(0, 30);

        assertTrue(events.isAct2TransitionRequested());
        assertTrue(manager.isAct2TransitionRequested());
        assertEquals(0x0301, events.getPendingZoneActWord());
    }

    @Test
    void secondPostBossRefreshCompletionSpawnsRomEndSignAtTransitionGate() {
        CapturingCnzEvents events = new CapturingCnzEvents();
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_FG_REFRESH_2);

        advanceCnzPostBossRefresh(events, 0, 16);

        assertEquals(Sonic3kCNZEvents.BG_DO_TRANSITION, events.getBackgroundRoutine());
        assertNotNull(events.spawned,
                "CNZ1BGE_FGRefresh2 must allocate Obj_EndSign before CNZ1BGE_DoTransition "
                        + "(docs/skdisasm/sonic3k.asm:107590-107601)");
        assertTrue(events.spawned instanceof S3kSignpostInstance,
                "CNZ1BGE_FGRefresh2 allocates Obj_EndSign "
                        + "(docs/skdisasm/sonic3k.asm:107596-107597)");
        assertEquals(0x32C0, events.spawned.getX(),
                "CNZ1BGE_FGRefresh2 writes Obj_EndSign x_pos=$32C0 "
                        + "(docs/skdisasm/sonic3k.asm:107596-107598)");
    }

    @Test
    void cnzDoTransitionAppliesRomCoordinateRemapImmediately() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .startPosition((short) 0x32D0, (short) 0x04AC)
                .startPositionIsCentre()
                .build();
        GameServices.camera().setFocusedSprite(fixture.sprite());
        GameServices.camera().setX((short) 0x323B);
        GameServices.camera().setY((short) 0x044C);
        GameServices.camera().setMinX((short) 0x31E0);
        GameServices.camera().setMaxX((short) 0x3260);
        GameServices.camera().setMinY((short) 0x00E0);
        GameServices.camera().setMaxY((short) 0x0300);

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .preserveMusic(true)
                .preserveLevelGamestate(true)
                .showInLevelTitleCard(false)
                .preserveOffsetCameraPosition(true)
                .postTransitionMinX(0x01E0)
                .postTransitionMaxX(0x0260)
                .postTransitionMinY(0x02E0)
                .postTransitionMaxY(0x0500)
                .postTransitionMaxYTarget(0x0500)
                .playerOffset(-0x3000, 0x0200)
                .cameraOffset(-0x3000, 0x0200)
                .build();

        GameServices.level().executeActTransition(request);

        assertEquals(0x02D0, fixture.sprite().getCentreX() & 0xFFFF,
                "CNZ1BGE_DoTransition subtracts d0=$3000 from Player_1 x_pos "
                        + "(docs/skdisasm/sonic3k.asm:107626-107629)");
        assertEquals(0x06AC, fixture.sprite().getCentreY() & 0xFFFF,
                "CNZ1BGE_DoTransition subtracts d1=-$200 from Player_1 y_pos "
                        + "(docs/skdisasm/sonic3k.asm:107626-107629)");
        assertEquals(0x023B, GameServices.camera().getX() & 0xFFFF,
                "CNZ1BGE_DoTransition subtracts d0=$3000 from Camera_X_pos "
                        + "(docs/skdisasm/sonic3k.asm:107626-107631)");
        assertEquals(0x064C, GameServices.camera().getY() & 0xFFFF,
                "CNZ1BGE_DoTransition subtracts d1=-$200 from Camera_Y_pos "
                        + "(docs/skdisasm/sonic3k.asm:107626-107631)");
        assertEquals(0x01E0, GameServices.camera().getMinX() & 0xFFFF,
                "CNZ1BGE_DoTransition offsets Camera_min_X_pos after Load_Level "
                        + "(docs/skdisasm/sonic3k.asm:107642)");
        assertEquals(0x0260, GameServices.camera().getMaxX() & 0xFFFF,
                "CNZ1BGE_DoTransition offsets Camera_max_X_pos after Load_Level "
                        + "(docs/skdisasm/sonic3k.asm:107643)");
        assertEquals(0x02E0, GameServices.camera().getMinY() & 0xFFFF,
                "CNZ1BGE_DoTransition offsets Camera_min_Y_pos after Load_Level "
                        + "(docs/skdisasm/sonic3k.asm:107644)");
        assertEquals(0x0500, GameServices.camera().getMaxY() & 0xFFFF,
                "CNZ1BGE_DoTransition offsets Camera_max_Y_pos after Load_Level "
                        + "(docs/skdisasm/sonic3k.asm:107645)");
        assertEquals(0x0500, GameServices.camera().getMaxYTarget() & 0xFFFF,
                "CNZ1BGE_DoTransition copies Camera_max_Y_pos to Camera_target_max_Y_pos "
                        + "(docs/skdisasm/sonic3k.asm:107646)");
    }

    @Test
    void cnzDoTransitionKeepsSignpostAndResultsObjectsAlive() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .startPosition((short) 0x32D0, (short) 0x04AC)
                .startPositionIsCentre()
                .build();
        GameServices.camera().setFocusedSprite(fixture.sprite());

        S3kSignpostInstance signpost = new S3kSignpostInstance(0x32C0, 0);
        S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                TestEnvironment.objectServices(),
                () -> new S3kResultsScreenObjectInstance(
                        com.openggf.game.PlayerCharacter.SONIC_AND_TAILS, 0));
        GameServices.level().getObjectManager().addDynamicObject(signpost);
        GameServices.level().getObjectManager().addDynamicObject(results);

        GameServices.level().executeActTransition(cnzAct2TransitionRequest());

        List<ObjectInstance> active = new ArrayList<>(GameServices.level().getObjectManager().getActiveObjects());
        assertTrue(active.contains(signpost),
                "CNZ1BGE_DoTransition reloads the level behind Obj_EndSign; "
                        + "the signpost must not vanish when Obj_LevelResults starts");
        assertTrue(active.contains(results),
                "Obj_LevelResults must survive the CNZ Act 1 reload it requests so the results screen can show");
    }

    @Test
    void productionCnzReloadCarriesTheResultsTitleOwnerUntilTitleCompletion()
            throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .startPosition((short) 0x32D0, (short) 0x04AC)
                .startPositionIsCentre()
                .build();
        GameServices.camera().setFocusedSprite(fixture.sprite());

        S3kResultsScreenObjectInstance actOneResults = ObjectConstructionContext.construct(
                TestEnvironment.objectServices(),
                () -> new S3kResultsScreenObjectInstance(
                        com.openggf.game.PlayerCharacter.SONIC_AND_TAILS, 0));
        GameServices.level().getObjectManager().addDynamicObject(actOneResults);

        HardwareTimingService timing = GameServices.hardwareTiming();
        int resultsParentCount = moduleJobCount(timing);
        assertEquals(3, resultsParentCount,
                "the live Act 1 results owner begins with its three ROM KosM parents");
        drainModuleHardware(timing);
        actOneResults.update(0, fixture.sprite());

        Sonic3kCNZEvents events = initCnzEvents(0);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_DO_TRANSITION);
        events.setEventsFg5(true);
        events.update(0, 0);

        S3kResultsScreenObjectInstance carriedResults = GameServices.level()
                .getObjectManager().getActiveObjects().stream()
                .filter(S3kResultsScreenObjectInstance.class::isInstance)
                .map(S3kResultsScreenObjectInstance.class::cast)
                .findFirst()
                .orElseThrow();
        assertSame(actOneResults, carriedResults,
                "CNZ Load_Level must carry the semantic Obj_LevelResults SST, not replace it with a control-delay bridge");
        assertEquals(resultsParentCount, moduleJobCount(timing),
                "the executor must neither publish a title nor submit CNZ2 enemies");

        Sonic3kObjectArtProvider artProvider = (Sonic3kObjectArtProvider)
                GameServices.module().getObjectArtProvider();
        artProvider.processRuntimeArtQueue();
        assertEquals(resultsParentCount, moduleJobCount(timing),
                "the held TITLE_OWNER lease blocks enemy submission before title publication");

        prepareFinalResultsExit(carriedResults);
        carriedResults.update(1, fixture.sprite());
        assertEquals(resultsParentCount, moduleJobCount(timing),
                "the child-retirement completion dispatch only publishes the mutated title owner");

        carriedResults.update(2, fixture.sprite());
        assertEquals(resultsParentCount + 4, moduleJobCount(timing),
                "the following Obj_TitleCard dispatch queues exactly four title parents");
        Sonic3kTitleCardManager title = (Sonic3kTitleCardManager)
                GameServices.module().getTitleCardProvider();
        assertEquals(artProvider.capture().runtimeArtAdmissionLeaseId(),
                title.capture().runtimeArtAdmissionLeaseId(),
                "the carried title binds the exact CNZ reload lease");

        artProvider.processRuntimeArtQueue();
        assertEquals(resultsParentCount + 4, moduleJobCount(timing),
                "enemy work remains held until the title owner reaches COMPLETE");

        drainModuleHardware(timing);
        for (int dispatch = 0; dispatch < 1_000 && !title.isComplete(); dispatch++) {
            title.update();
        }
        assertTrue(title.isComplete());
        assertEquals(resultsParentCount + 4, moduleJobCount(timing),
                "title completion arms but does not submit enemy parents inside the owner dispatch");

        artProvider.processRuntimeArtQueue();
        assertEquals(resultsParentCount + 8, moduleJobCount(timing),
                "the actual COMPLETE boundary admits the four CNZ2 enemy parents exactly once");
        artProvider.processRuntimeArtQueue();
        assertEquals(resultsParentCount + 8, moduleJobCount(timing),
                "later provider pumps cannot publish a second enemy batch");
    }

    @Test
    void productionCnzCarriedTitleOwnerRewindsAtEveryOwnershipBoundary()
            throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .startPosition((short) 0x32D0, (short) 0x04AC)
                .startPositionIsCentre()
                .build();
        GameServices.camera().setFocusedSprite(fixture.sprite());
        HardwareTimingService timing = GameServices.hardwareTiming();
        RewindRegistry rewind = TestEnvironment.activeGameplayMode().getRewindRegistry();

        S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                TestEnvironment.objectServices(),
                () -> new S3kResultsScreenObjectInstance(
                        com.openggf.game.PlayerCharacter.SONIC_AND_TAILS, 0));
        GameServices.level().getObjectManager().addDynamicObject(results);
        drainModuleHardware(timing);
        results.update(0, fixture.sprite());
        CompositeSnapshot beforeReload = rewind.capture();
        prepareFinalResultsExit(results);
        rewind.restore(beforeReload);
        results = reacquireResultsOwner();
        assertFalse((boolean) getField(results, "exitPublicationComplete"),
                "rewind immediately before reload restores the live Act 1 results owner");

        GameServices.level().executeActTransition(cnzAct2TransitionRequest());
        S3kResultsScreenObjectInstance carried = reacquireResultsOwner();
        prepareFinalResultsExit(carried);
        CompositeSnapshot afterRecreation = rewind.capture();

        carried.update(1, fixture.sprite());
        CompositeSnapshot beforeTitlePublication = rewind.capture();
        carried.update(2, fixture.sprite());
        Sonic3kTitleCardManager title = (Sonic3kTitleCardManager)
                GameServices.module().getTitleCardProvider();
        drainModuleHardware(timing);
        for (int dispatch = 0; dispatch < 1_000 && !title.isComplete(); dispatch++) {
            title.update();
        }
        Sonic3kObjectArtProvider artProvider = (Sonic3kObjectArtProvider)
                GameServices.module().getObjectArtProvider();
        artProvider.processRuntimeArtQueue();
        CompositeSnapshot afterCompletion = rewind.capture();

        rewind.restore(afterCompletion);
        int completedJobCount = moduleJobCount(timing);
        ((Sonic3kObjectArtProvider) GameServices.module().getObjectArtProvider())
                .processRuntimeArtQueue();
        assertEquals(completedJobCount, moduleJobCount(timing),
                "restoring after COMPLETE cannot release a second enemy batch");

        rewind.restore(beforeTitlePublication);
        assertEquals(3, moduleJobCount(timing));
        S3kResultsScreenObjectInstance restoredBeforeTitle = reacquireResultsOwner();
        assertTrue((boolean) getField(restoredBeforeTitle, "exitPublicationComplete"));
        assertTrue((boolean) getField(restoredBeforeTitle, "titleInitializationPending"));
        restoredBeforeTitle.update(3, fixture.sprite());
        assertEquals(7, moduleJobCount(timing),
                "rewind before title publication replays the single four-parent dispatch");

        rewind.restore(afterRecreation);
        assertEquals(3, moduleJobCount(timing));
        S3kResultsScreenObjectInstance restoredAfterReload = reacquireResultsOwner();
        restoredAfterReload.update(4, fixture.sprite());
        restoredAfterReload.update(5, fixture.sprite());
        assertEquals(7, moduleJobCount(timing),
                "rewind after target recreation retains the same semantic results/title owner");

    }

    @Test
    void stalePreReloadTitleOwnerCannotClaimTheNewCnzLease() throws Exception {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        HardwareTimingService timing = GameServices.hardwareTiming();
        Sonic3kObjectArtProvider artProvider = (Sonic3kObjectArtProvider)
                GameServices.module().getObjectArtProvider();
        artProvider.prepareRuntimeArtForActTransition(
                Sonic3kZoneIds.ZONE_CNZ, RuntimeArtAdmissionPolicy.TITLE_OWNER);
        Sonic3kTitleCardManager title = new Sonic3kTitleCardManager();
        title.initialize(Sonic3kZoneIds.ZONE_CNZ, 0);
        prepareTitleCompletionDispatch(title);
        Sonic3kTitleCardManager.Snapshot staleOwner = title.capture();
        long staleLease = staleOwner.runtimeArtAdmissionLeaseId();
        assertTrue(staleLease >= 0,
                "the pre-reload production title owner must carry its exact scalar lease id");

        GameServices.level().executeActTransition(cnzAct2TransitionRequest());
        assertTrue(artProvider.capture().runtimeArtAdmissionLeaseId() != staleLease,
                "the target CNZ batch receives a new exact lease identity");

        title.restore(staleOwner);
        drainModuleHardware(timing);
        assertThrows(IllegalStateException.class, title::update,
                "the stale pre-reload title scalar cannot bind or consume the new CNZ lease");
        assertFalse(artProvider.capture().runtimeArtAdmissionConsumed());
        assertEquals(Sonic3kTitleCardState.EXIT, title.capture().state());
    }

    @Test
    void cnzPostTransitionResultsHandoffRestoresPlayerControlAfterRomDelay() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_CNZ, 1);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x02D0, (short) 0x06AC);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0x029A, (short) 0x06B0);
        tails.setCpuControlled(true);
        GameServices.sprites().addSprite(player);
        GameServices.sprites().addSprite(tails, "tails");
        GameServices.camera().setFocusedSprite(player);

        ObjectControlState.nativeBit7FullControl().applyTo(player);
        ObjectControlState.nativeBit7FullControl().applyTo(tails);
        player.setControlLocked(true);
        tails.setControlLocked(true);
        player.setAir(true);
        tails.setAir(true);

        manager.requestCnzPostTransitionRelease(2);

        manager.update();
        assertTrue(player.isObjectControlled());
        assertTrue(tails.isObjectControlled());
        assertTrue(player.getAir());
        assertTrue(tails.getAir());

        manager.update();
        assertFalse(player.isObjectControlled(),
                "Obj_EndSignControlAwaitStart calls Restore_PlayerControl after "
                        + "Obj_LevelResults loc_2DD06 clears _unkFAA8 "
                        + "(docs/skdisasm/sonic3k.asm:62708-62720,180407-180412)");
        assertFalse(tails.isObjectControlled(),
                "Restore_PlayerControl2 clears Player_2 object_control in the same handoff "
                        + "(docs/skdisasm/sonic3k.asm:180359-180367)");
        assertFalse(player.isControlLocked());
        assertFalse(tails.isControlLocked());
        assertFalse(player.getAir());
        assertFalse(tails.getAir());
    }

    @Test
    void cnzPostTransitionStartsRomAct2LevelSizeGradualAfterTitleCardHandoff() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_CNZ, 1);

        GameServices.camera().setMinX((short) 0x01E0);
        GameServices.camera().setMaxX((short) 0x0260);
        GameServices.camera().setMinY((short) 0x0580);
        GameServices.camera().setMaxY((short) 0x1000);
        GameServices.camera().setMaxYTarget((short) 0x1000);

        manager.requestCnzPostTransitionRelease(1);

        for (int i = 0; i < 20; i++) {
            manager.update();
        }

        assertEquals(0x0260, GameServices.camera().getMaxX() & 0xFFFF,
                "Change_Act2Sizes must wait on the in-level title-card End_of_level_flag, not elapsed frames");

        GameServices.gameState().setEndOfLevelFlag(true);
        for (int i = 0; i < 12; i++) {
            manager.update();
        }

        assertEquals(0x0260, GameServices.camera().getMaxX() & 0xFFFF,
                "Obj_IncLevEndXGradual accumulates $4000 and does not move Camera_max_X_pos "
                        + "until the fourth update (docs/skdisasm/sonic3k.asm:178154-178168)");

        manager.update();

        assertEquals(0x0261, GameServices.camera().getMaxX() & 0xFFFF,
                "Obj_EndSignControlDoStart calls Change_Act2Sizes after the in-level title-card "
                        + "End_of_level_flag, then Obj_IncLevEndXGradual begins expanding Act 2 bounds "
                        + "(docs/skdisasm/sonic3k.asm:180415-180419,180575-180632,178154-178168)");

        for (int i = 0; i < 4; i++) {
            manager.update();
        }

        assertEquals(0x0266, GameServices.camera().getMaxX() & 0xFFFF,
                "Obj_IncLevEndXGradual keeps its full 16.16 object accumulator in $30(a0) and "
                        + "applies the swapped high word each frame; it is not a delta-only "
                        + "fractional carry (docs/skdisasm/sonic3k.asm:178154-178168)");
    }

    @Test
    void lowerRouteMinibossEntryRemapsPlayersAndCameraIntoBossTunnel() {
        Sonic3kCNZEvents events = initCnzEvents(0);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x3000, (short) 0x0650);
        GameServices.sprites().addSprite(player);
        GameServices.camera().setFocusedSprite(player);
        GameServices.camera().setMinY((short) 0);
        GameServices.camera().setX((short) 0x3000);
        GameServices.camera().setY((short) 0x0600);

        events.update(0, 0);

        assertEquals(0x0650 - 0x0700, player.getCentreY(),
                "CNZ1 lower-route miniboss entry should mirror the ROM's -$700 player Y remap");
        assertEquals(0x0600 - 0x0700, GameServices.camera().getY(),
                "CNZ1 lower-route miniboss entry should mirror the ROM's -$700 camera Y remap");
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_Y, GameServices.camera().getMinY() & 0xFFFF,
                "CNZ1BGE_Normal should set the tunnel minimum Y before the $31E0 arena gate");
        assertTrue(events.isWallGrabSuppressed(),
                "CNZ1BGE_Normal should suppress wall-grab interactions inside the boss tunnel");
        assertFalse(events.isBossFlag(),
                "Early tunnel entry must not set Boss_flag before the $31E0 arena gate");
        assertEquals(Sonic3kCNZEvents.BossBackgroundMode.ACT1_MINIBOSS_PATH,
                events.getBossBackgroundMode());
    }

    @Test
    void lowerRouteTunnelModeAt3000DoesNotArmRomArenaGateUntil31E0() {
        GameServices.audio().commandTimeline().clear();

        Sonic3kCNZEvents events = initCnzEvents(0);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x3000, (short) 0x0650);
        GameServices.sprites().addSprite(player);
        GameServices.camera().setFocusedSprite(player);
        GameServices.camera().setMinX((short) 0x0000);
        GameServices.camera().setMaxX((short) 0x4000);
        GameServices.camera().setMinY((short) 0x0000);
        GameServices.camera().setMaxY((short) 0x1000);
        GameServices.camera().setMaxYTarget((short) 0x1000);
        GameServices.camera().setX((short) 0x3000);
        GameServices.camera().setY((short) 0x0600);

        events.update(0, 0);

        assertEquals(0x0650 - 0x0700, player.getCentreY(),
                "The early lower-route remap must still use ROM centre coordinates");
        assertEquals(0x0600 - 0x0700, GameServices.camera().getY(),
                "The early lower-route remap must move the foreground camera into the tunnel");
        assertEquals(Sonic3kCNZEvents.BossBackgroundMode.ACT1_MINIBOSS_PATH,
                events.getBossBackgroundMode(),
                "Camera X $3000 may enter the miniboss tunnel scroll mode");
        assertFalse(events.isBossFlag(), "Camera X $3000 must not set Boss_flag");
        assertTrue(events.isWallGrabSuppressed(), "Camera X $3000 should suppress wall-grab during the boss tunnel setup");
        assertEquals(0x0000, GameServices.camera().getMinX() & 0xFFFF,
                "Camera X $3000 must not clamp Camera_min_X_pos to the ROM arena gate");
        assertEquals(0x4000, GameServices.camera().getMaxX() & 0xFFFF,
                "Camera X $3000 must not clamp Camera_max_X_pos to the ROM arena gate");
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_Y, GameServices.camera().getMinY() & 0xFFFF,
                "Camera X $3000 should apply the ROM tunnel minimum Y");
        assertEquals(0x1000, GameServices.camera().getMaxYTarget() & 0xFFFF,
                "Camera X $3000 must not set the arena target max Y");
        assertEquals(0, commandsOfType(AudioCommand.FadeOutMusic.class).size(),
                "Camera X $3000 must not fade music for the arena gate");
        assertTrue(commandsOfType(AudioCommand.PlayMusic.class).isEmpty(),
                "Camera X $3000 must not start miniboss music");

        GameServices.camera().setX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);
        events.update(0, 1);
        events.enterMinibossArenaFromObjectSlot();

        assertTrue(events.isBossFlag(), "Camera X $31E0 is the ROM arena gate that sets Boss_flag");
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X, GameServices.camera().getMinX() & 0xFFFF);
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_X, GameServices.camera().getMaxX() & 0xFFFF);
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_Y, GameServices.camera().getMinY() & 0xFFFF);
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_ARENA_MAX_Y, GameServices.camera().getMaxYTarget() & 0xFFFF);
        assertEquals(1, commandsOfType(AudioCommand.FadeOutMusic.class).size(),
                "The real arena gate fades current music before the 120-frame wait");
        assertTrue(commandsOfType(AudioCommand.PlayMusic.class).isEmpty(),
                "Miniboss music must wait for the outer 120-frame release timer");
    }

    @Test
    void arenaGateReleasesMinibossMusicOnceAfter120FrameWait() {
        GameServices.audio().commandTimeline().clear();

        Sonic3kCNZEvents events = initCnzEvents(0);
        GameServices.camera().setX((short) Sonic3kConstants.CNZ_MINIBOSS_ARENA_MIN_X);

        events.enterMinibossArenaFromObjectSlot();
        events.update(0, 0);

        assertEquals(1, commandsOfType(AudioCommand.FadeOutMusic.class).size(),
                "Arena gate should issue the ROM fade-out immediately");
        assertTrue(commandsOfType(AudioCommand.PlayMusic.class).isEmpty(),
                "Miniboss music should not start on the gate frame");

        for (int frame = 1; frame <= 120; frame++) {
            events.update(0, frame);
        }

        assertEquals(List.of(Sonic3kMusic.MINIBOSS.id), commandsOfType(AudioCommand.PlayMusic.class)
                        .stream().map(AudioCommand.PlayMusic::musicId).toList(),
                "The outer gate releases mus_Miniboss after the 120-frame wait");

        for (int frame = 120; frame < 180; frame++) {
            events.update(0, frame);
        }

        assertEquals(List.of(Sonic3kMusic.MINIBOSS.id), commandsOfType(AudioCommand.PlayMusic.class)
                        .stream().map(AudioCommand.PlayMusic::musicId).toList(),
                "The release timer must not replay miniboss music after it fires once");
    }

    @Test
    void bossScrollStartAdvancesWhenTunnelBackgroundReachesRomThreshold() {
        Sonic3kCNZEvents events = initCnzEvents(0);
        events.forceBossBackgroundMode(Sonic3kCNZEvents.BossBackgroundMode.ACT1_MINIBOSS_PATH);
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_BOSS_START);
        events.setBossScrollState(0x0120, 0);
        GameServices.camera().setY((short) 0x01C0);

        events.update(0, 0);

        assertEquals(Sonic3kCNZEvents.BG_BOSS, events.getBackgroundRoutine());
    }

    @Test
    void act2KnucklesEntryStartsTeleporterRoute_notModeOnly() {
        Sonic3kCNZEvents events = initCnzEvents(1);

        events.beginKnucklesTeleporterRoute();

        assertEquals(Sonic3kCNZEvents.FG_ACT2_KNUCKLES_ROUTE, events.getForegroundRoutine());
        assertTrue(events.isKnucklesTeleporterRouteActive());
        assertEquals(0x4750, events.getCameraMinXClamp());
        assertEquals(0x48E0, events.getCameraMaxXClamp());
    }

    @Test
    void act2KnucklesRouteEndReleasesTeleporterClamp() {
        Sonic3kCNZEvents events = initCnzEvents(1);
        events.beginKnucklesTeleporterRoute();

        events.endKnucklesTeleporterRoute();

        assertFalse(events.isKnucklesTeleporterRouteActive(),
                "CutsceneKnux_CNZ2B clears Ctrl_1_locked and deletes itself after the camera has moved down; "
                        + "the engine-side CNZ route clamp must be released at the same handoff");
        assertEquals(Sonic3kCNZEvents.FG_ACT2_NORMAL, events.getForegroundRoutine());
        assertEquals(0, events.getCameraMinXClamp());
        assertEquals(0, events.getCameraMaxXClamp());
    }

    private Sonic3kCNZEvents initCnzEvents(int act) {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        manager.initLevel(Sonic3kZoneIds.ZONE_CNZ, act);
        return manager.getCnzEvents();
    }

    private SeamlessLevelTransitionRequest cnzAct2TransitionRequest() {
        return SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.TITLE_OWNER)
                .preserveMusic(true)
                .preserveLevelGamestate(true)
                .showInLevelTitleCard(false)
                .preserveOffsetCameraPosition(true)
                .postTransitionMinX(0x01E0)
                .postTransitionMaxX(0x0260)
                .postTransitionMinY(0x02E0)
                .postTransitionMaxY(0x0500)
                .postTransitionMaxYTarget(0x0500)
                .playerOffset(-0x3000, 0x0200)
                .cameraOffset(-0x3000, 0x0200)
                .build();
    }

    private static void prepareFinalResultsExit(S3kResultsScreenObjectInstance results)
            throws ReflectiveOperationException {
        setField(results, "resultsChildrenCreated", true);
        setField(results, "state", 4);
        setField(results, "childrenRemaining", 0);
        setField(results, "carriedResultsRenderRetireDispatches", 0);
        setField(results, "exitRetireDispatchesInitialized", true);
    }

    private static void prepareTitleCompletionDispatch(Sonic3kTitleCardManager title)
            throws ReflectiveOperationException {
        setField(title, "state", Sonic3kTitleCardState.EXIT);
        setField(title, "exitChildrenGone", true);
        boolean[] exited = (boolean[]) getField(title, "elemExited");
        Arrays.fill(exited, true);
    }

    private static Object getField(Object target, String name)
            throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static S3kResultsScreenObjectInstance reacquireResultsOwner() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(S3kResultsScreenObjectInstance.class::isInstance)
                .map(S3kResultsScreenObjectInstance.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static int moduleJobCount(HardwareTimingService timing) {
        return (int) timing.capture().jobs().stream()
                .filter(job -> job.kind() == HardwareWorkKind.KOS_MODULE_QUEUE)
                .count();
    }

    private static void drainModuleHardware(HardwareTimingService timing) {
        for (int frame = 0;
                frame < 100_000
                        && timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE) > 0;
                frame++) {
            HardwareBoundaryPump.service(timing, S3kRuntimeArtCoordinator.current(),
                    HardwareServiceBoundary.PRE_MAIN_LOOP);
            HardwareBoundaryPump.service(timing, S3kRuntimeArtCoordinator.current(),
                    HardwareServiceBoundary.POST_OBJECTS);
        }
        assertEquals(0, timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
    }

    private void advanceCnzPostBossRefresh(Sonic3kCNZEvents events, int firstFrame, int updates) {
        for (int i = 0; i < updates; i++) {
            events.update(0, firstFrame + i);
        }
    }

    private <T extends AudioCommand> List<T> commandsOfType(Class<T> type) {
        return GameServices.audio().commandTimeline().entries().stream()
                .map(entry -> entry.command())
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private static final class CapturingCnzEvents extends Sonic3kCNZEvents {
        private ObjectInstance spawned;

        @Override
        protected <T extends ObjectInstance> T spawnObject(Supplier<T> factory) {
            T object = factory.get();
            spawned = object;
            return object;
        }
    }
}
