package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAizIntroEventsHeadless {
    private static final int ZONE_AIZ = 0;
    private static final int ACT_1 = 0;
    private static final short AIZ1_INTRO_CENTRE_X = 0x40;
    private static final short AIZ1_INTRO_CENTRE_Y = 0x420;
    private static final int MAX_FRAMES = 4000;

    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;
    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private Sonic sonic;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ, ACT_1);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(
                SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(
                SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(
                SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        sonic = (Sonic) fixture.sprite();
        sonic.setCentreX(AIZ1_INTRO_CENTRE_X);
        sonic.setCentreY(AIZ1_INTRO_CENTRE_Y);
        fixture.camera().updatePosition(true);

        LevelEventProvider levelEventProvider = GameModuleRegistry.getCurrent().getLevelEventProvider();
        if (levelEventProvider != null) {
            levelEventProvider.initLevel(ZONE_AIZ, ACT_1);
        }
        GameServices.level().getObjectManager().reset(0);
    }

    @Test
    void aizIntroBootstrapParksTailsBeforeFirstVisualReplayTick() {
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        assertEquals(SidekickCpuController.State.DORMANT_MARKER, controller.getState(),
                "Visual trace replay renders skipped pre-LevelLoop frames before the first CPU tick");
        assertEquals(0x7F00, tails.getCentreX() & 0xFFFF,
                "AIZ intro Tails must already be parked at the ROM dormant marker");
        assertEquals(0, tails.getCentreY() & 0xFFFF,
                "AIZ intro Tails must not render from normal level-start placement during visual replay");
        assertTrue(tails.isObjectControlled(),
                "ROM object_control=$83 keeps dormant intro Tails under object control");
        assertTrue(tails.isControlLocked(),
                "Dormant intro Tails should be locked before the first replay frame renders");
    }

    @Test
    void aizIntroSidekickBootstrapParksTailsAtDormantMarkerUntilResizeRelease() {
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        controller.setInitialState(SidekickCpuController.State.INIT);
        fixture.stepFrame(false, false, false, false, false);

        assertEquals(0x7F00, tails.getCentreX() & 0xFFFF,
                "ROM loc_13A10/sub_13ECA parks AIZ intro Tails at x_pos=$7F00");
        assertEquals(0, tails.getCentreY() & 0xFFFF,
                "ROM loc_13A10/sub_13ECA parks AIZ intro Tails at y_pos=0");
        assertEquals(0, tails.getYSubpixelRaw() & 0xFFFF,
                "AIZ intro marker is reached before Tails accumulates native subpixel drift");
        assertTrue(tails.isObjectControlled(),
                "ROM writes object_control=$83 while AIZ intro Tails is dormant");
        assertTrue(tails.isControlLocked(),
                "Engine object-control model should keep dormant intro Tails out of normal control");
        assertTrue(tails.getAir(), "ROM sub_13ECA sets Status_InAir");

        Sonic3kLevelEventManager levelEvents =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kAIZEvents aizEvents = levelEvents.getAizEvents();
        fixture.camera().setX((short) 0x1308);
        aizEvents.updatePrePhysics(ACT_1);

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "AIZ1_Resize's prior-frame Tails_CPU_routine=2 write is visible before the next sidekick CPU slot");
    }

    @Test
    void prePhysicsRetryReleasesDormantSidekickAfterLatePaletteSwapNoop() {
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        Sonic3kLevelEventManager levelEvents =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kAIZEvents aizEvents = levelEvents.getAizEvents();
        fixture.camera().setX((short) 0x1308);
        aizEvents.setPaletteSwapped(true);

        controller.setInitialState(SidekickCpuController.State.INIT);
        aizEvents.updatePrePhysics(ACT_1);
        assertEquals(SidekickCpuController.State.INIT, controller.getState(),
                "A late dynamic-event release before Tails reaches routine $0A must be a no-op");

        tails.setCentreX((short) 0x7F00);
        tails.setCentreY((short) 0);
        tails.setAir(true);
        tails.setControlLocked(true);
        tails.setObjectControlled(true);
        controller.setInitialState(SidekickCpuController.State.DORMANT_MARKER);
        aizEvents.updatePrePhysics(ACT_1);

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "AIZ pre-physics must retry until the dormant marker is actually released, independent of palette state");
    }

    @Test
    void prePhysicsBridgeWaitsForCommittedAizResizeCameraPosition() {
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        Sonic3kLevelEventManager levelEvents =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Sonic3kAIZEvents aizEvents = levelEvents.getAizEvents();

        tails.setCentreX((short) 0x7F00);
        tails.setCentreY((short) 0);
        tails.setAir(true);
        tails.setControlLocked(true);
        tails.setObjectControlled(true);
        controller.setInitialState(SidekickCpuController.State.DORMANT_MARKER);

        fixture.camera().setX((short) 0x1307);
        sonic.setCentreX((short) 0x13A8);
        assertEquals(0x1308, fixture.camera().previewNextX() & 0xFFFF,
                "test setup should make the next camera step cross the AIZ palette threshold");

        aizEvents.updatePrePhysics(ACT_1);

        assertEquals(SidekickCpuController.State.DORMANT_MARKER, controller.getState(),
                "AIZ1_Resize's Tails_CPU_routine=2 write happens after the current Process_Sprites slot; "
                        + "a preview-only threshold crossing must not release Tails yet");

        fixture.camera().setX((short) 0x1308);
        aizEvents.updatePrePhysics(ACT_1);

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "the prior-frame AIZ1_Resize write is visible once the committed camera X has reached $1308");
    }

    @Test
    void releasedAizIntroSidekickWaitsForRomLevelFrameCounterBeforeCatchUpWarp() throws Exception {
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        sonic.setCentreX((short) 0x13CE);
        sonic.setCentreY((short) 0x0402);
        sonic.setObjectControlled(false);
        tails.setCentreX((short) 0x7F00);
        tails.setCentreY((short) 0);
        tails.setAir(true);
        tails.setControlLocked(true);
        tails.setObjectControlled(true);
        controller.setInitialState(SidekickCpuController.State.DORMANT_MARKER);
        setLevelFrameCounter(0x02FF);
        controller.releaseDormantMarkerForLevelEvent();

        controller.update(0x0300);

        assertEquals(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, controller.getState(),
                "ROM Tails_Catch_Up_Flying sees the post-increment Level_frame_counter=$0300 on the release tick");
        assertEquals(0x13CE, tails.getCentreX() & 0xFFFF,
                "Catch-up warp copies Sonic x_pos on the ROM cadence frame");
        assertEquals(0x0342, tails.getCentreY() & 0xFFFF,
                "Catch-up warp copies Sonic y_pos-$C0 on the ROM cadence frame");
    }

    @Test
    void releasedAizIntroSidekickUsesRomVisibleCounterAfterWaitingForCatchUpGate() throws Exception {
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        sonic.setCentreX((short) 0x13CC);
        sonic.setCentreY((short) 0x0400);
        sonic.setObjectControlled(false);
        tails.setCentreX((short) 0x7F00);
        tails.setCentreY((short) 0);
        tails.setAir(true);
        tails.setControlLocked(true);
        tails.setObjectControlled(true);
        controller.setInitialState(SidekickCpuController.State.DORMANT_MARKER);

        setLevelFrameCounter(0x02F3);
        controller.releaseDormantMarkerForLevelEvent();
        for (int storedCounter = 0x02F4; storedCounter < 0x02FF; storedCounter++) {
            setLevelFrameCounter(storedCounter);
            controller.update(storedCounter + 1);
            assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                    "Tails must remain parked before the ROM-visible $0300 gate");
            assertEquals(0x7F00, tails.getCentreX() & 0xFFFF,
                    "Tails must remain at the AIZ dormant marker before the catch-up gate");
        }

        setLevelFrameCounter(0x02FF);
        controller.update(0x0300);

        assertEquals(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, controller.getState(),
                "Routine $02 must see Level_frame_counter=$0300 before the stored counter is committed");
        assertEquals(0x13CC, tails.getCentreX() & 0xFFFF,
                "Catch-up warp copies Sonic x_pos on the same frame as ROM");
        assertEquals(0x0340, tails.getCentreY() & 0xFFFF,
                "Catch-up warp copies Sonic y_pos-$C0 on the same frame as ROM");
    }

    private static void setLevelFrameCounter(int value) throws Exception {
        Field frameCounter = GameServices.level().getClass().getDeclaredField("frameCounter");
        frameCounter.setAccessible(true);
        frameCounter.setInt(GameServices.level(), value);
    }

    @Test
    void introMainLevelHandoffPulsesEventsFg5BeforeNormalPhase() {
        Sonic3kLevelEventManager levelEvents =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        assertNotNull(levelEvents, "Sonic3kLevelEventManager should exist");
        Sonic3kAIZEvents aizEvents = levelEvents.getAizEvents();
        assertNotNull(aizEvents, "AIZ events should be initialized");
        assertFalse(aizEvents.isEventsFg5(), "AIZ Events_fg_5 should start clear");

        boolean sawGameplayStart = false;
        boolean sawEventsPulse = false;
        boolean sawEventsClearAfterPulse = false;
        int pulseCameraX = -1;
        int pulseFrame = -1;

        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            boolean holdRight = fixture.camera().isLevelStarted();
            fixture.stepFrame(false, false, false, holdRight, false);

            if (fixture.camera().isLevelStarted()) {
                sawGameplayStart = true;
            }

            if (!sawEventsPulse && aizEvents.isEventsFg5()) {
                sawEventsPulse = true;
                pulseFrame = frame;
                pulseCameraX = fixture.camera().getX();
            }

            if (sawEventsPulse && AizPlaneIntroInstance.isMainLevelPhaseActive() && !aizEvents.isEventsFg5()) {
                sawEventsClearAfterPulse = true;
                break;
            }
        }

        String diagnostics = "gameplayStart=" + sawGameplayStart
                + " pulse=" + sawEventsPulse
                + " pulseFrame=" + pulseFrame
                + " pulseCameraX=0x" + Integer.toHexString(pulseCameraX)
                + " cameraX=0x" + Integer.toHexString(fixture.camera().getX())
                + " levelStarted=" + fixture.camera().isLevelStarted()
                + " mainLevelPhaseActive=" + AizPlaneIntroInstance.isMainLevelPhaseActive()
                + " eventsFg5=" + aizEvents.isEventsFg5()
                + " sonicX=0x" + Integer.toHexString(sonic.getCentreX());

        assertTrue(sawGameplayStart, "AIZ intro never reached gameplay start. " + diagnostics);
        assertTrue(sawEventsPulse, "AIZ intro never raised Events_fg_5 at the main-level handoff. " + diagnostics);
        assertTrue(pulseCameraX >= 0x1400,
                "AIZ intro raised Events_fg_5 before the $1400 handoff seam. " + diagnostics);
        assertTrue(sawEventsClearAfterPulse,
                "AIZ intro never cleared Events_fg_5 after entering the normal main-level phase. " + diagnostics);
    }

    @Test
    void introMainLevelHandoffDoesNotPersistentlySkipGlobalFrameCounters() {
        Sonic3kLevelEventManager levelEvents =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        assertNotNull(levelEvents, "Sonic3kLevelEventManager should exist");
        Sonic3kAIZEvents aizEvents = levelEvents.getAizEvents();
        assertNotNull(aizEvents, "AIZ events should be initialized");

        boolean sawEventsPulse = false;

        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            int levelFrameBefore = GameServices.level().getFrameCounter();
            int spriteFrameBefore = GameServices.sprites().getFrameCounter();
            boolean holdRight = fixture.camera().isLevelStarted();

            fixture.stepFrame(false, false, false, holdRight, false);

            assertEquals(levelFrameBefore + 1, GameServices.level().getFrameCounter(),
                    "AIZ intro normal refresh must not add a second LevelManager tick at frame " + frame);
            assertEquals(spriteFrameBefore + 1, GameServices.sprites().getFrameCounter(),
                    "AIZ intro normal refresh must not add a second SpriteManager tick at frame " + frame);

            if (aizEvents.isEventsFg5()) {
                sawEventsPulse = true;
                break;
            }
        }

        assertTrue(sawEventsPulse, "AIZ intro never raised Events_fg_5 at the main-level handoff");
    }

    @Test
    void introNormalRefreshDoesNotPublishHiddenCadenceAfterNonNormalCpuSlot() throws Exception {
        Sonic3kLevelEventManager levelEvents =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        assertNotNull(levelEvents, "Sonic3kLevelEventManager should exist");
        Sonic3kAIZEvents aizEvents = levelEvents.getAizEvents();
        assertNotNull(aizEvents, "AIZ events should be initialized");
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        sonic.setObjectControlled(true);
        sonic.setCentreX((short) 0x1960);
        sonic.setCentreY((short) 0x03C0);
        tails.setAir(false);
        tails.setCentreX((short) 0x1964);
        tails.setCentreY((short) 0x041E);
        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1956);
        Arrays.fill(yHistory, (short) 0x03ED);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_JUMP);
        Arrays.fill(statusHistory, (byte) 0x06);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        fixture.camera().setX((short) 0x1400);
        AizPlaneIntroInstance.setMainLevelPhaseActive(true);
        AizPlaneIntroInstance.adoptActiveIntroInstance(null);
        aizEvents.setIntroNormalRefreshPending(false);

        setLevelFrameCounter(0x06BE);
        aizEvents.updatePrePhysics(ACT_1);
        controller.setInitialState(SidekickCpuController.State.NORMAL);
        controller.update(0x06BE);
        assertNotNull(controller.getLatestNormalStepDiagnostics(),
                "NORMAL sidekick tick should record diagnostics");
        assertEquals(0x06BE, controller.getLatestNormalStepDiagnostics().frameCounter(),
                "NORMAL sidekick cadence should use the caller-provided ROM-visible frame counter");
        assertFalse(controller.getInputJumpPress(),
                "NORMAL sidekick cadence must not publish one frame early through a hidden +1 lookahead");

        setLevelFrameCounter(0x06BF);
        aizEvents.updatePrePhysics(ACT_1);
        controller.setInitialState(SidekickCpuController.State.CATCH_UP_FLIGHT);
        controller.update(0x06BF);
        assertEquals(0x06BF, GameServices.level().getFrameCounter(),
                "AIZ intro refresh must not persistently advance Level_frame_counter");

        controller.setInitialState(SidekickCpuController.State.NORMAL);
        controller.update(0x06BF);

        assertNotNull(controller.getLatestNormalStepDiagnostics(),
                "NORMAL sidekick tick should record diagnostics");
        assertEquals(0x06BF, controller.getLatestNormalStepDiagnostics().frameCounter(),
                "Earlier non-NORMAL sidekick slots must not arm a hidden NORMAL cadence");
        assertFalse(controller.getInputJumpPress(),
                "NORMAL sidekick auto-jump should wait for an actual $40 caller cadence");
        assertEquals(0x06BF, GameServices.level().getFrameCounter(),
                "AIZ intro refresh must leave the stored Level_frame_counter unchanged after NORMAL runs");
    }

    @Test
    void introNormalRefreshDoesNotSpendEarlierCadencesOrAddHiddenTick() throws Exception {
        Sonic3kLevelEventManager levelEvents =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        assertNotNull(levelEvents, "Sonic3kLevelEventManager should exist");
        Sonic3kAIZEvents aizEvents = levelEvents.getAizEvents();
        assertNotNull(aizEvents, "AIZ events should be initialized");
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        sonic.setObjectControlled(true);
        sonic.setCentreX((short) 0x1960);
        sonic.setCentreY((short) 0x03C0);
        tails.setAir(false);
        tails.setCentreX((short) 0x1964);
        tails.setCentreY((short) 0x041E);
        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1956);
        Arrays.fill(yHistory, (short) 0x03ED);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_JUMP);
        Arrays.fill(statusHistory, (byte) 0x06);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        fixture.camera().setX((short) 0x1400);
        AizPlaneIntroInstance.setMainLevelPhaseActive(true);
        AizPlaneIntroInstance.adoptActiveIntroInstance(null);
        aizEvents.setIntroNormalRefreshPending(false);

        Arrays.fill(yHistory, (short) 0x041E);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        controller.setInitialState(SidekickCpuController.State.NORMAL);
        setLevelFrameCounter(0x04FF);
        aizEvents.updatePrePhysics(ACT_1);
        controller.update(0x04FF);

        Arrays.fill(yHistory, (short) 0x03ED);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        for (int levelFrameCounter : new int[] { 0x053F, 0x057F }) {
            controller.setInitialState(SidekickCpuController.State.CATCH_UP_FLIGHT);
            setLevelFrameCounter(levelFrameCounter);
            aizEvents.updatePrePhysics(ACT_1);
            controller.update(levelFrameCounter);
        }

        controller.setInitialState(SidekickCpuController.State.NORMAL);
        setLevelFrameCounter(0x06BF);
        aizEvents.updatePrePhysics(ACT_1);
        controller.update(0x06BF);

        assertNotNull(controller.getLatestNormalStepDiagnostics(),
                "NORMAL sidekick tick should record diagnostics");
        assertEquals(0x06BF, controller.getLatestNormalStepDiagnostics().frameCounter(),
                "Earlier AIZ intro refresh cadences must not arm a later hidden +1 NORMAL tick");
        assertFalse(controller.getInputJumpPress(),
                "NORMAL sidekick auto-jump should wait for an actual $40 caller cadence");
        assertEquals(0x06BF, GameServices.level().getFrameCounter(),
                "AIZ intro refresh must leave the stored Level_frame_counter unchanged after NORMAL runs");
    }

    @Test
    void introRefreshBeginPublishesRomVisibleCounterBeforeEndOfFrameArm() throws Exception {
        Sonic3kLevelEventManager levelEvents =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        assertNotNull(levelEvents, "Sonic3kLevelEventManager should exist");
        Sonic3kAIZEvents aizEvents = levelEvents.getAizEvents();
        assertNotNull(aizEvents, "AIZ events should be initialized");
        assertFalse(GameServices.sprites().getSidekicks().isEmpty(),
                "Sonic+Tails AIZ intro should register Player_2");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().get(0);
        SidekickCpuController controller = tails.getCpuController();
        assertNotNull(controller, "CPU Tails should have a controller");

        sonic.setObjectControlled(false);
        sonic.setCentreX((short) 0x194A);
        sonic.setCentreY((short) 0x0354);
        tails.setAir(false);
        tails.setRolling(false);
        tails.setCentreX((short) 0x1939);
        tails.setCentreY((short) 0x041D);
        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1934);
        Arrays.fill(yHistory, (short) 0x033C);
        Arrays.fill(statusHistory, (byte) 0x03);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        fixture.camera().setX((short) 0x1400);
        AizPlaneIntroInstance.setMainLevelPhaseActive(false);
        AizPlaneIntroInstance.adoptActiveIntroInstance(null);
        aizEvents.setIntroNormalRefreshPending(false);
        controller.setInitialState(SidekickCpuController.State.NORMAL);

        setLevelFrameCounter(0x06FF);
        aizEvents.updatePrePhysics(ACT_1);
        controller.update(0x06FF);

        assertNotNull(controller.getLatestNormalStepDiagnostics(),
                "NORMAL sidekick tick should record diagnostics");
        assertEquals(0x06FF, controller.getLatestNormalStepDiagnostics().frameCounter(),
                "AIZ intro refresh-begin frame must use the caller-provided ROM-visible frame counter");
        assertFalse(controller.getInputJumpPress(),
                "Frame 2081 shape must not synthesize the next $40 cadence before the caller provides it");
        assertEquals(0x06FF, GameServices.level().getFrameCounter(),
                "AIZ intro refresh must not persistently advance Level_frame_counter");
    }
}
