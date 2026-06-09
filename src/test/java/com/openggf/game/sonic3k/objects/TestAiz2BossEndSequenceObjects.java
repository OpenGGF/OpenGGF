package com.openggf.game.sonic3k.objects;

import com.openggf.game.session.EngineServices;
import com.openggf.tests.TestEnvironment;

import com.openggf.camera.Camera;
import com.openggf.game.session.EngineContext;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameStateManager;
import com.openggf.game.save.SaveReason;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossEggCapsuleInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAiz2BossEndSequenceObjects {
    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
    }

    @AfterEach
    void tearDown() {
        Aiz2BossEndSequenceState.reset();
        SessionManager.clear();
        SessionManager.clear();
    }

    @Test
    void route8FloatingCapsulesShareNeutralBaseWithoutGroundCapsules() throws Exception {
        Class<?> floatingBase = Class.forName(
                "com.openggf.game.sonic3k.objects.AbstractS3kFloatingEndEggCapsuleInstance");

        assertEquals(floatingBase, Aiz2EndEggCapsuleInstance.class.getSuperclass());
        assertEquals(floatingBase, Mgz2EndEggCapsuleInstance.class.getSuperclass());
        assertFalse(floatingBase.isAssignableFrom(CnzEggCapsuleInstance.class));
        assertFalse(floatingBase.isAssignableFrom(HczEndBossEggCapsuleInstance.class));
    }

    @Test
    void floatingCapsuleBaseUsesPlainAnimalsWhileAizOptsIntoHighPriorityAnimals() {
        ObjectSpawn spawn = new ObjectSpawn(0x1000, 0x0200, 0x28, 0, 0, false, 0);
        NeutralFloatingCapsuleForTest neutral = new NeutralFloatingCapsuleForTest();
        AizFloatingCapsuleForTest aiz = new AizFloatingCapsuleForTest();

        AbstractObjectInstance neutralAnimal = neutral.createAnimal(spawn);
        AbstractObjectInstance aizAnimal = aiz.createAnimal(spawn);

        assertTrue(neutralAnimal instanceof EggPrisonAnimalInstance);
        assertFalse(neutralAnimal.isHighPriority(),
                "The shared floating capsule base should not encode AIZ waterfall foreground priority");
        assertTrue(aizAnimal instanceof EggPrisonAnimalInstance);
        assertTrue(aizAnimal.isHighPriority(),
                "AIZ keeps its local high-priority animal behavior for foreground waterfall tiles");
    }

    @Test
    void cutsceneButtonPressesWhenKnucklesReachesIt() throws Exception {
        S3kCutsceneButtonObjectInstance button =
                new S3kCutsceneButtonObjectInstance(new ObjectSpawn(0x4B18, 0x0189, 0x83, 0, 0, false, 0));
        button.setServices(new TestObjectServices());

        CutsceneKnucklesAiz2Instance knuckles = CutsceneKnucklesAiz2Instance.createDefault();
        setField(knuckles, "currentX", 0x4B10);
        setField(knuckles, "currentY", 0x0188);
        // Button only triggers after Knuckles' jump is finished (LAUGH_2 phase)
        setPhase(knuckles, "LAUGH_2");
        Aiz2BossEndSequenceState.setActiveKnuckles(knuckles);

        button.update(0, null);

        assertTrue(Aiz2BossEndSequenceState.isButtonPressed());
    }

    @Test
    void drawBridgeDropsPlayersIntoHurtFallAfterButtonPress() {
        AizDrawBridgeObjectInstance bridge =
                new AizDrawBridgeObjectInstance(new ObjectSpawn(0x4B48, 0x0218, 0x32, 0, 2, false, 0));
        bridge.setServices(new TestObjectServices().withGameState(new GameStateManager()));

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x4B48);
        player.setCentreY((short) 0x0210);
        player.setOnObject(true);
        bridge.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);

        Aiz2BossEndSequenceState.triggerBridgeDrop();
        for (int i = 0; i < 40; i++) {
            bridge.update(i, player);
        }
        assertTrue(bridge.isSolidFor(player));

        Aiz2BossEndSequenceState.pressButton();
        for (int i = 0; i < 14; i++) {
            bridge.update(40 + i, player);
        }

        assertTrue(bridge.isSolidFor(player),
                "Obj_AIZDrawBridge continues running SolidObjectFull2 through the $0E collapse countdown");
        assertFalse(player.getAir(),
                "Players stay ride-supported until loc_2B45E ejects them and deletes the parent object");

        bridge.update(54, player);

        assertFalse(bridge.isSolidFor(player));
        assertTrue(player.getAir());
        assertEquals(Sonic3kAnimationIds.HURT_FALL.id(), player.getForcedAnimationId());
    }

    @Test
    void drawBridgeFlatSupportStartsOnRoutineEntryAfterSettledAngleIsReached() {
        AizDrawBridgeObjectInstance bridge =
                new AizDrawBridgeObjectInstance(new ObjectSpawn(0x4B48, 0x0218, 0x32, 0, 2, false, 0));
        bridge.setServices(new TestObjectServices().withGameState(new GameStateManager()));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);

        Aiz2BossEndSequenceState.triggerBridgeDrop();
        for (int i = 0; i < 32; i++) {
            bridge.update(i, player);
        }

        assertFalse(bridge.isSolidFor(player),
                "ROM Obj_AIZDrawBridge reaches $38=$80 after the loc_2B2B0 settled check; "
                        + "flat/full bridge support starts on the next routine entry");

        bridge.update(32, player);

        assertTrue(bridge.isSolidFor(player));
    }

    @Test
    void drawBridgeUsesSolidObjectFull2ProfileForLandingWidthPixels() {
        AizDrawBridgeObjectInstance bridge =
                new AizDrawBridgeObjectInstance(new ObjectSpawn(0x4B48, 0x0218, 0x32, 0, 2, false, 0));

        SolidObjectParams params = bridge.getSolidParams();
        assertFalse(bridge.isTopSolidOnly(),
                "ROM Obj_AIZDrawBridge calls SolidObjectFull2, so new landings narrow d1=$6B "
                        + "back to width_pixels=$60 instead of using a top-only full-width landing");
        assertTrue(bridge.bypassesOffscreenSolidGate());
        assertEquals(0x6B, params.halfWidth());
        assertEquals(8, params.airHalfHeight());
        assertEquals(8, params.groundHalfHeight());
    }

    @Test
    void eggCapsuleReleasesControllerAfterResultsFinish() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) 0x4880);
        camera.setY((short) 0x0100);

        GameStateManager gameState = new GameStateManager();
        TestObjectServices services = new TestObjectServices()
                .withCamera(camera)
                .withGameState(gameState);

        Aiz2EndEggCapsuleInstance capsule = Aiz2EndEggCapsuleInstance.createForCamera(
                camera.getX() & 0xFFFF, camera.getY() & 0xFFFF);
        capsule.setServices(services);

        assertFalse(Aiz2BossEndSequenceState.isEggCapsuleReleased());
        setField(capsule, "currentY", 0x0140);
        setField(capsule, "opened", 1);
        setField(capsule, "resultsStarted", 1);
        gameState.setEndOfLevelFlag(true);
        capsule.update(0, null);

        assertTrue(Aiz2BossEndSequenceState.isEggCapsuleReleased());
    }

    @Test
    void aizCapsuleButtonTriggerDefersParentOpenUntilNextRoutineEntry() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) 0x48E0);
        camera.setY((short) 0x0122);

        GameStateManager gameState = new GameStateManager();
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setCentreX((short) 0x49EB);
        sonic.setCentreY((short) 0x0186);
        sonic.setYSpeed((short) -1);
        sonic.setAir(true);
        sonic.setAnimationId(Sonic3kAnimationIds.ROLL);

        Aiz2EndEggCapsuleInstance capsule = new Aiz2EndEggCapsuleInstance(0x49EA, 0x0162);
        capsule.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(gameState));

        capsule.update(0, sonic);

        assertTrue(getBooleanField(capsule, "buttonTriggered"),
                "ROM loc_86770 sets the parent trigger bit from the button child first");
        assertFalse(getBooleanField(capsule, "opened"),
                "Obj_EggCapsule does not run sub_865DE until the parent object's next routine entry "
                        + "(sonic3k.asm:181739-181767,181556-181570)");

        capsule.update(1, sonic);

        assertTrue(getBooleanField(capsule, "opened"),
                "The parent sees the child-set bit on the next routine entry and runs sub_865DE "
                        + "(sonic3k.asm:181556-181570)");
    }

    @Test
    void floatingCapsuleTraceDetailsExposeParentRoutineAnalogs() throws Exception {
        Aiz2EndEggCapsuleInstance capsule = new Aiz2EndEggCapsuleInstance(0x49EA, 0x0162);
        setField(capsule, "buttonTriggered", 1);
        setField(capsule, "opened", 1);
        setField(capsule, "postOpenTimer", 0x20);
        setField(capsule, "mappingFrame", 1);
        setField(capsule, "buttonRecess", 8);
        setField(capsule, "buttonTriggerSource", 2);
        setField(capsule, "buttonTriggerFrame", 0x1234);
        setField(capsule, "openFrame", 0x1235);

        assertEquals("cap=open/0020 t=1 o=1 r=0 mf=01 btn=08 src=p2 tf=1234 of=1235",
                capsule.traceDebugDetails(),
                "Trace context should expose the Obj_EggCapsule routine-8/$0A/$0C progression "
                        + "and $2E countdown without mutating replay state");

        setField(capsule, "resultsStarted", 1);

        assertEquals("cap=results/0020 t=1 o=1 r=1 mf=01 btn=08 src=p2 tf=1234 of=1235",
                capsule.traceDebugDetails());
    }

    @Test
    void floatingCapsuleRoute8MovesRealCoordinatesWithRomLongwordAndSwing() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) 0x4800);
        camera.setY((short) 0x0100);

        Aiz2EndEggCapsuleInstance capsule = Aiz2EndEggCapsuleInstance.createForCamera(
                camera.getX() & 0xFFFF, camera.getY() & 0xFFFF);
        capsule.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(new GameStateManager()));
        setField(capsule, "xDirection", 0);

        capsule.update(0, null);

        assertEquals(0x00C0, capsule.getY(),
                "A child-spawned route-8 Obj_EggCapsule first runs loc_8657A setup; "
                        + "loc_8662A motion starts on a later routine entry "
                        + "(sonic3k.asm:181496-181545,181588-181647)");

        for (int i = 1; i <= 4; i++) {
            capsule.update(i, null);
        }

        assertEquals(0x00C3, capsule.getY(),
                "Obj_EggCapsule route 8 mutates real y_pos: add.l #$4000,y_pos, "
                        + "Swing_UpAndDown, then MoveSprite2 (sonic3k.asm:181626-181647)");
        assertEquals(0x6000, getIntField(capsule, "ySubpixel"));
        assertEquals(0x80, getIntField(capsule, "yVelocity"));
        assertEquals(0x00E7, capsule.getPieceY(1),
                "The button child follows refreshed parent y_pos, not a render-only bob");
    }

    @Test
    void floatingCapsuleRoute8SetupRefreshesFromLiveCameraOnFirstRoutineEntry() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) 0x4800);
        camera.setY((short) 0x0100);

        Aiz2EndEggCapsuleInstance capsule = Aiz2EndEggCapsuleInstance.createForCamera(
                camera.getX() & 0xFFFF, camera.getY() & 0xFFFF);
        capsule.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(new GameStateManager()));

        camera.setX((short) 0x48E0);
        camera.setY((short) 0x0122);
        setField(capsule, "currentX", 0x4A08);
        setField(capsule, "currentY", 0x011A);
        setField(capsule, "xDirection", -1);
        setField(capsule, "yVelocity", 0x20);
        setField(capsule, "ySubpixel", 0x8000);
        setField(capsule, "swingDescending", 1);

        capsule.update(0, null);

        assertEquals(0x4980, capsule.getX(),
                "Route-8 init loc_86592 writes x_pos from the live camera before later movement "
                        + "(sonic3k.asm:181522-181545)");
        assertEquals(0x00E2, capsule.getY(),
                "Route-8 init loc_86592 writes y_pos=Camera_Y_pos-$40 on its own routine entry");
        assertEquals(1, getIntField(capsule, "xDirection"),
                "loc_86592 initializes parent $3A to +1 before loc_8662A starts patrolling");
        assertEquals(0xC0, getIntField(capsule, "yVelocity"),
                "Swing_Setup1 initializes y_vel=$00C0 for the route-8 capsule");
        assertEquals(0, getIntField(capsule, "ySubpixel"));
        assertFalse(getBooleanField(capsule, "swingDescending"));
    }

    @Test
    void floatingCapsuleRoute8XPatrolUsesRomCompareBeforeAdd() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) 0x4800);
        camera.setY((short) 0x0100);

        Aiz2EndEggCapsuleInstance capsule = new Aiz2EndEggCapsuleInstance(0x4910, 0x00C0);
        capsule.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(new GameStateManager()));
        setField(capsule, "xDirection", 1);

        capsule.update(0, null);

        assertEquals(0x4911, capsule.getX(),
                "ROM loc_8662A compares against Camera_X_pos+$110 before adding $3A; equality "
                        + "does not reverse or clamp the capsule this frame (sonic3k.asm:181604-181625)");
        assertEquals(1, getIntField(capsule, "xDirection"));

        capsule.update(1, null);

        assertEquals(0x4910, capsule.getX());
        assertEquals(-1, getIntField(capsule, "xDirection"));

        setField(capsule, "currentX", 0x4830);
        setField(capsule, "xDirection", -1);

        capsule.update(2, null);

        assertEquals(0x4831, capsule.getX(),
                "The negative-$3A branch reverses only once x_pos reaches Camera_X_pos+$30");
        assertEquals(1, getIntField(capsule, "xDirection"));
    }

    @Test
    void floatingCapsulePostOpenRoutineStillSwingsAndMovesBeforeResults() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();

        GameStateManager gameState = new GameStateManager();
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        TimingFloatingCapsuleForTest capsule = new TimingFloatingCapsuleForTest();
        capsule.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(gameState));
        setField(capsule, "opened", 1);
        setField(capsule, "postOpenTimer", 0x41);
        setField(capsule, "currentY", 0x0100);

        capsule.update(0, sonic);

        assertEquals(0x40, getIntField(capsule, "postOpenTimer"));
        assertEquals(0x0100, capsule.getY());
        assertEquals(0xB000, getIntField(capsule, "ySubpixel"),
                "AIZ routine $0A runs sub_868F8, then Swing_UpAndDown and MoveSprite2 "
                        + "while waiting for results (sonic3k.asm:181656-181667)");
        assertEquals(0xB0, getIntField(capsule, "yVelocity"));
    }

    @Test
    void aizCapsuleButtonRangeUsesRawChildPositionBeforeSwing() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) 0x48E0);
        camera.setY((short) 0x0114);

        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        tails.setCentreX((short) 0x4A13);
        tails.setCentreY((short) 0x01B2);
        tails.setYSpeed((short) -1);
        tails.setAir(true);

        Aiz2EndEggCapsuleInstance capsule = new Aiz2EndEggCapsuleInstance(0x4A1B, 0x0154);
        capsule.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(new GameStateManager()));
        setField(capsule, "xDirection", 0);

        capsule.update(0, tails);

        assertFalse(getBooleanField(capsule, "buttonTriggered"),
                "ROM loc_86770 checks the refreshed child y_pos from the real parent position "
                        + "(sonic3k.asm:181739-181767,181604-181647)");
        assertEquals(0x0178, capsule.getPieceY(1),
                "The button solid child uses parent y_pos + child_dy");
    }

    @Test
    void aizCapsuleInstallsSignedSidekickLockOnlyWhenParentConsumesButtonTrigger() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) 0x48E0);
        camera.setY((short) 0x0122);

        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setCentreX((short) 0x49EB);
        sonic.setCentreY((short) 0x0186);
        sonic.setYSpeed((short) -1);
        sonic.setAir(true);
        sonic.setAnimationId(Sonic3kAnimationIds.ROLL);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        SidekickCpuController tailsCpu = new SidekickCpuController(tails, sonic);
        tails.setCpuController(tailsCpu);

        Aiz2EndEggCapsuleInstance capsule = new Aiz2EndEggCapsuleInstance(0x49EA, 0x0162);
        capsule.setServices(new QueryOnlyServices(camera, sonic, List.of(tails))
                .withGameState(new GameStateManager()));
        setField(capsule, "xDirection", 0);

        capsule.update(0, sonic);

        assertTrue(getBooleanField(capsule, "buttonTriggered"));
        assertFalse(getBooleanField(capsule, "opened"));
        assertFalse(tailsCpu.isController2SignedLocked(),
                "loc_86770 only sets parent $38 bit 1; sub_865DE has not run yet");

        capsule.update(1, sonic);

        assertTrue(getBooleanField(capsule, "opened"));
        assertTrue(tailsCpu.isController2SignedLocked(),
                "sub_865DE installs Ctrl_2_locked=$FF when the parent changes to routine $0A "
                        + "(sonic3k.asm:181556-181570,181604-181647)");
    }

    @Test
    void floatingCapsuleResultsWaitUsesRomPredecrementCounter() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();

        GameStateManager gameState = new GameStateManager();
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setAir(false);
        TimingFloatingCapsuleForTest capsule = new TimingFloatingCapsuleForTest();
        capsule.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(gameState));
        setField(capsule, "opened", 1);
        setField(capsule, "postOpenTimer", 0x41);

        for (int i = 0; i < 0x40; i++) {
            capsule.update(i, sonic);
            assertFalse(getBooleanField(capsule, "resultsStarted"),
                    "ROM sub_868F8 branches while --$2E is non-negative");
        }

        capsule.update(0x40, sonic);

        assertTrue(getBooleanField(capsule, "resultsStarted"),
                "sub_865DE writes $2E=$40 and sub_868F8 starts results on the 65th routine entry "
                        + "(sonic3k.asm:181556-181570,181900-181918)");
    }

    @Test
    void floatingCapsuleResultsWaitForResidualGroundVelocityToSettle() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();

        GameStateManager gameState = new GameStateManager();
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setAir(false);
        sonic.setXSpeed((short) 0x0014);
        sonic.setGSpeed((short) 0x0014);
        TimingFloatingCapsuleForTest capsule = new TimingFloatingCapsuleForTest();
        capsule.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(gameState));
        setField(capsule, "opened", 1);
        setField(capsule, "postOpenTimer", 0);

        capsule.update(0, sonic);

        assertFalse(getBooleanField(capsule, "resultsStarted"),
                "The collapsed engine object pass must let the ROM-style ground deceleration "
                        + "settle before applying Set_PlayerEndingPose");

        sonic.setXSpeed((short) 0);
        sonic.setGSpeed((short) 0);
        capsule.update(1, sonic);

        assertTrue(getBooleanField(capsule, "resultsStarted"));
    }

    @Test
    void floatingCapsuleResultsClearStaleEndOfLevelFlagBeforeWaitingForExit() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();

        GameStateManager gameState = new GameStateManager();
        gameState.setEndOfLevelFlag(true);
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setAir(false);
        TimingFloatingCapsuleForTest capsule = new TimingFloatingCapsuleForTest();
        capsule.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(gameState));
        setField(capsule, "opened", 1);
        setField(capsule, "postOpenTimer", 0);

        capsule.update(0, sonic);

        assertTrue(getBooleanField(capsule, "resultsStarted"));
        assertFalse(gameState.isEndOfLevelFlag(),
                "AIZ2 results must not consume the in-level title-card End_of_level_flag; "
                        + "Obj_LevelResults owns the next flag write on exit");
        assertFalse(Aiz2BossEndSequenceState.isEggCapsuleReleased());
    }

    @Test
    void aizCapsuleClearsSignedSidekickLockWhenEndingPoseCheckRuns() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();

        GameStateManager gameState = new GameStateManager();
        gameState.setEndOfLevelFlag(true);
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        SidekickCpuController tailsCpu = new SidekickCpuController(tails, sonic);
        tailsCpu.setController2SignedLocked(true);
        tails.setCpuController(tailsCpu);

        Aiz2EndEggCapsuleInstance capsule = new Aiz2EndEggCapsuleInstance(0x49E9, 0x0163);
        capsule.setServices(new QueryOnlyServices(camera, sonic, List.of(tails))
                .withGameState(gameState));
        setField(capsule, "opened", 1);
        setField(capsule, "resultsStarted", 1);

        capsule.update(0, sonic);

        assertFalse(tailsCpu.isController2SignedLocked(),
                "Check_TailsEndPose clears Ctrl_2_locked before Set_PlayerEndingPose "
                        + "(sonic3k.asm:181919-181939)");
    }

    @Test
    void aizCapsuleButtonRequiresSonicRollAnimationBeforeParentTrigger() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) 0x48E0);
        camera.setY((short) 0x0122);

        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setCentreX((short) 0x49EB);
        sonic.setCentreY((short) 0x0186);
        sonic.setYSpeed((short) -1);
        sonic.setAnimationId(Sonic3kAnimationIds.WALK);

        Aiz2EndEggCapsuleInstance capsule = new Aiz2EndEggCapsuleInstance(0x49EA, 0x0162);
        capsule.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(new GameStateManager()));

        capsule.update(0, sonic);
        assertFalse(getBooleanField(capsule, "buttonTriggered"),
                "ROM loc_86770 rejects Sonic unless anim(a1) == #2 before setting parent $38 bit 1 "
                        + "(sonic3k.asm:181749-181761)");

        sonic.setAnimationId(Sonic3kAnimationIds.ROLL);
        capsule.update(1, sonic);

        assertTrue(getBooleanField(capsule, "buttonTriggered"));
    }

    @Test
    void aizCapsuleNativeP2TriggersFromUpwardVelocityWithoutRollAnimation() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) 0x48E0);
        camera.setY((short) 0x0122);

        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setCentreX((short) 0x4900);
        sonic.setCentreY((short) 0x0186);
        sonic.setYSpeed((short) 0);
        sonic.setAnimationId(Sonic3kAnimationIds.WALK);

        TestablePlayableSprite p2 = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        p2.setCentreX((short) 0x49EB);
        p2.setCentreY((short) 0x0186);
        p2.setYSpeed((short) -1);
        p2.setAnimationId(Sonic3kAnimationIds.WALK);

        Aiz2EndEggCapsuleInstance capsule = new Aiz2EndEggCapsuleInstance(0x49EA, 0x0162);
        capsule.setServices(new QueryOnlyServices(camera, sonic, List.of(p2))
                .withGameState(new GameStateManager()));

        capsule.update(0, sonic);

        assertTrue(getBooleanField(capsule, "buttonTriggered"),
                "ROM loc_86770 checks native Player 2 after native Player 1 is absent/rejected, "
                        + "then triggers immediately after upward y_vel without an anim #2 test "
                        + "(sonic3k.asm:181777-181800)");
        assertEquals(2, getIntField(capsule, "buttonTriggerSource"));
        assertFalse(getBooleanField(capsule, "opened"));
    }

    @Test
    void aizCapsuleButtonRunsSolidHelperBeforeVelocityTriggerCheck() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setX((short) 0x48E0);
        camera.setY((short) 0x0122);

        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sonic.setCentreX((short) 0x4900);
        sonic.setCentreY((short) 0x0186);
        sonic.setYSpeed((short) 0);
        sonic.setAnimationId(Sonic3kAnimationIds.WALK);

        TestablePlayableSprite p2 = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        p2.setCentreX((short) 0x49EB);
        p2.setCentreY((short) 0x0186);
        p2.setYSpeed((short) -1);
        p2.setAnimationId(Sonic3kAnimationIds.WALK);

        Aiz2EndEggCapsuleInstance capsule = new Aiz2EndEggCapsuleInstance(0x49EA, 0x0162);
        VelocityCancellingSolidRegistry solidRegistry =
                new VelocityCancellingSolidRegistry(capsule, p2);
        capsule.setServices(new QueryOnlyServices(camera, sonic, List.of(p2))
                .withGameState(new GameStateManager())
                .withSolidExecutionRegistry(solidRegistry));

        capsule.update(0, sonic);

        assertTrue(solidRegistry.resolved,
                "ROM loc_86770 refreshes the child and runs sub_86A54 before Check_PlayerInRange "
                        + "(sonic3k.asm:181739-181767,182049-182054)");
        assertFalse(getBooleanField(capsule, "buttonTriggered"),
                "The button trigger must observe y_vel after the child solid helper; if sub_86A54 "
                        + "has cancelled upward velocity, loc_86770 does not set parent $38 bit 1");
    }

    @Test
    void controllerWaitsForEggCapsuleBeforeStartingWalkAndHydrocityTransition() {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setMaxX((short) 0x4880);
        camera.setMaxY((short) 0x0200);
        camera.setX((short) 0x4880);
        camera.setY((short) 0x0100);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x4A60);
        player.setCentreY((short) 0x0210);
        player.setTestY((short) 0x0170);
        player.setSubpixelRaw(0x5900, 0xA300);

        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        Aiz2BossEndSequenceController controller = new Aiz2BossEndSequenceController(0x4880, 0x0000);
        controller.setServices(services);

        controller.update(0, player);
        assertEquals(0x4880, camera.getMaxXTarget() & 0xFFFF);
        assertFalse(player.isControlLocked());
        assertFalse(player.isForceInputRight());

        Aiz2BossEndSequenceState.releaseEggCapsule();
        controller.update(1, player);
        assertEquals(0x4880, camera.getMaxXTarget() & 0xFFFF);
        assertTrue(player.isControlLocked());
        assertTrue(player.isObjectControlled());
        assertFalse(player.isForceInputRight());

        for (int i = 0; i < 10; i++) {
            controller.update(i + 2, player);
        }

        assertEquals(0x49D8, camera.getMaxXTarget() & 0xFFFF);
        assertTrue(player.isControlLocked());
        assertFalse(player.isObjectControlled());
        assertTrue(player.isForcedInputActive(AbstractPlayableSprite.INPUT_RIGHT));
        assertEquals(0x000C, player.getXSpeed() & 0xFFFF);
        assertEquals(0x000C, player.getGSpeed() & 0xFFFF);
        assertEquals(0x6500, player.getXSubpixelRaw());

        for (int i = 0; i < 16; i++) {
            camera.updateBoundaryEasing();
            controller.update(i + 13, player);
        }
        assertTrue((camera.getMaxX() & 0xFFFF) > 0x4880);

        player.setCentreX((short) 0x4A80);
        Aiz2BossEndSequenceState.pressButton();
        player.setTestY((short) 0x01F0);
        controller.update(100, player);

        assertEquals(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL,
                services.lastSeamlessRequest.type());
        assertEquals(Sonic3kZoneIds.ZONE_HCZ, services.lastSeamlessRequest.targetZone());
        assertEquals(0, services.lastSeamlessRequest.targetAct());
        assertTrue(services.lastSeamlessRequest.deactivateLevelNow());
        assertTrue(services.lastSeamlessRequest.preserveLevelGamestate());
        assertTrue(services.lastSeamlessRequest.preserveOffsetCameraPosition());
        assertEquals(SaveReason.PROGRESSION_SAVE, services.lastSaveReason);
    }

    @Test
    void controllerLocksAllEngineSidekicksFromPlayerQueryDuringCutsceneWalk() {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setMaxX((short) 0x4880);
        camera.setX((short) 0x4880);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x4900);
        TestablePlayableSprite firstSidekick = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        TestablePlayableSprite extraSidekick = new TestablePlayableSprite("knuckles", (short) 0, (short) 0);

        Aiz2BossEndSequenceController controller = new Aiz2BossEndSequenceController(0x4880, 0x0000);
        controller.setServices(new QueryOnlyServices(camera, player, List.of(firstSidekick, extraSidekick)));
        Aiz2BossEndSequenceState.releaseEggCapsule();

        controller.update(1, player);

        assertTrue(firstSidekick.isControlLocked());
        assertTrue(extraSidekick.isControlLocked());
    }

    @Test
    void hydrocityTransitionUsesRomCentreYRatherThanSpriteTopY() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setMaxX((short) 0x49D8);
        camera.setX((short) 0x49D8);
        camera.setY((short) 0x0100);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setHeight(38);
        player.setCentreX((short) 0x4A80);
        player.setTestY((short) 0x01D4);

        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        Aiz2BossEndSequenceController controller = new Aiz2BossEndSequenceController(0x4880, 0x0000);
        controller.setServices(services);
        setField(controller, "postResultsControlRestoreDelay", 0);
        Aiz2BossEndSequenceState.releaseEggCapsule();
        Aiz2BossEndSequenceState.pressButton();

        controller.update(100, player);

        assertEquals(Sonic3kZoneIds.ZONE_HCZ, services.lastSeamlessRequest.targetZone(),
                "ROM y_pos maps to centre Y; top-left/test Y must not delay the HCZ transition");
    }

    @Test
    void controllerButtonStartsGradualLevelEndYChild() throws Exception {
        Camera camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        camera.setMaxX((short) 0x49D8);
        camera.setX((short) 0x49D8);
        camera.setMaxY((short) 0x015A);
        camera.setY((short) 0x0100);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x4A80);
        player.setCentreY((short) 0x0200);

        Aiz2BossEndSequenceController controller = new Aiz2BossEndSequenceController(0x4880, 0x0000);
        controller.setServices(new RecordingServices()
                .withCamera(camera)
                .withGameState(new GameStateManager()));
        setField(controller, "postResultsControlRestoreDelay", 0);
        Aiz2BossEndSequenceState.releaseEggCapsule();
        Aiz2BossEndSequenceState.pressButton();

        controller.update(100, player);

        assertEquals(0x015A, camera.getMaxY() & 0xFFFF,
                "Obj_IncLevEndYGradual's first $8000 accumulator tick has no integer delta");
        assertEquals(0x1000, camera.getMaxYTarget() & 0xFFFF,
                "loc_65C56 writes Camera_target_max_Y_pos before creating the gradual level-end child");

        controller.update(101, player);

        assertEquals(0x015B, camera.getMaxY() & 0xFFFF,
                "The child object adds the accumulator high word to Camera_max_Y_pos on later updates");
        assertEquals(0x1000, camera.getMaxYTarget() & 0xFFFF);
    }

    private static void setField(Object target, String fieldName, int value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        if (field.getType() == boolean.class) {
            field.setBoolean(target, value != 0);
        } else {
            field.setInt(target, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> void setPhase(Object target, String enumName) throws Exception {
        Field field = target.getClass().getDeclaredField("phase");
        field.setAccessible(true);
        Class<E> enumType = (Class<E>) field.getType();
        field.set(target, Enum.valueOf(enumType, enumName));
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static boolean getBooleanField(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static int getIntField(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static final class NeutralFloatingCapsuleForTest extends AbstractS3kFloatingEndEggCapsuleInstance {
        NeutralFloatingCapsuleForTest() {
            super(0, 0, "NeutralFloatingCapsuleForTest");
        }

        AbstractObjectInstance createAnimal(ObjectSpawn spawn) {
            return createCapsuleAnimal(spawn, 0, 0, 0);
        }
    }

    private static final class AizFloatingCapsuleForTest extends Aiz2EndEggCapsuleInstance {
        AizFloatingCapsuleForTest() {
            super(0, 0);
        }

        AbstractObjectInstance createAnimal(ObjectSpawn spawn) {
            return createCapsuleAnimal(spawn, 0, 0, 0);
        }
    }

    private static final class TimingFloatingCapsuleForTest extends AbstractS3kFloatingEndEggCapsuleInstance {
        TimingFloatingCapsuleForTest() {
            super(0, 0, "TimingFloatingCapsuleForTest");
        }

        @Override
        protected AbstractObjectInstance createResultsScreen() {
            return new EggPrisonAnimalInstance(new ObjectSpawn(0, 0, 0x28, 0, 0, false, 0), 0, 0);
        }
    }

    private static final class RecordingServices extends TestObjectServices {
        int requestedZone = -1;
        int requestedAct = -1;
        SaveReason lastSaveReason;
        SeamlessLevelTransitionRequest lastSeamlessRequest;

        @Override
        public void requestZoneAndAct(int zone, int act) {
            requestedZone = zone;
            requestedAct = act;
        }

        @Override
        public void requestSessionSave(SaveReason reason) {
            lastSaveReason = reason;
        }

        @Override
        public void requestSeamlessTransition(SeamlessLevelTransitionRequest request) {
            lastSeamlessRequest = request;
        }
    }

    private static final class QueryOnlyServices extends TestObjectServices {
        private final Camera camera;
        private final ObjectPlayerQuery playerQuery;

        QueryOnlyServices(Camera camera, TestablePlayableSprite main, List<TestablePlayableSprite> sidekicks) {
            this.camera = camera;
            this.playerQuery = new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }

        @Override
        public Camera camera() {
            return camera;
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return playerQuery;
        }

        @Override
        public List<com.openggf.game.PlayableEntity> sidekicks() {
            throw new AssertionError("AIZ2 end sequence should use ObjectPlayerQuery for cutscene sidekick control");
        }
    }

    private static final class VelocityCancellingSolidRegistry implements SolidExecutionRegistry {
        private final AbstractObjectInstance object;
        private final TestablePlayableSprite playerToStop;
        private boolean resolved;

        VelocityCancellingSolidRegistry(AbstractObjectInstance object, TestablePlayableSprite playerToStop) {
            this.object = object;
            this.playerToStop = playerToStop;
        }

        @Override
        public void beginFrame(int frameCounter, List<? extends com.openggf.game.PlayableEntity> players) {
        }

        @Override
        public void beginObject(com.openggf.level.objects.ObjectInstance object,
                ObjectSolidExecutionContext.Resolver resolver) {
        }

        @Override
        public ObjectSolidExecutionContext currentObject() {
            return new ObjectSolidExecutionContext(this, object, () -> {
                resolved = true;
                playerToStop.setYSpeed((short) 0);
                return new SolidCheckpointBatch(object, Map.of());
            });
        }

        @Override
        public PlayerStandingState previousStanding(com.openggf.level.objects.ObjectInstance object,
                com.openggf.game.PlayableEntity player) {
            return PlayerStandingState.NONE;
        }

        @Override
        public void publishCheckpoint(SolidCheckpointBatch batch) {
        }

        @Override
        public void endObject(com.openggf.level.objects.ObjectInstance object) {
        }

        @Override
        public void finishFrame() {
        }

        @Override
        public void clearTransientState() {
        }
    }
}
