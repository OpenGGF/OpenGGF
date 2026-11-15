package com.openggf;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SavePaths;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.CdFixtureReplayAccess;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.title.CdTitleScreen;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceReplayBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sonic CD started the way a player starts it: {@code roms.default: scd} through the Engine's own
 * configured startup ({@link Engine#initializeConfiguredHeadlessSession}: {@code initializeGame}, the
 * hosted-media launch, title entry) and every frame through {@link Engine#advanceFrame()}, the window
 * loop's frame. No harness boot, no direct level load. Needs the USA disc
 * ({@code -Dsoniccd.media.root}) and, for the stage comparison, the R11A native fixture.
 */
@Isolated
class TestSonicCdPlayerLaunch {
    private static final int START = 0x80;   // pad bit for the title's Start
    @TempDir Path dir;
    private EngineContext previous;
    private String previousSaveRoot;
    private Engine engine;
    private InputHandler input;
    private Bk2FrameInput last;
    private int frame;

    @BeforeEach
    void requireDisc() throws Exception {
        CdFixtureReplayAccess.usaCue();      // skips without the disc
        previous = EngineServices.current();
        previousSaveRoot = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty(SavePaths.ROOT_PROPERTY, dir.resolve("saves").toString());
    }

    @AfterEach
    void close() {
        try {
            closeEngine();
        } finally {
            if (previousSaveRoot == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
            else System.setProperty(SavePaths.ROOT_PROPERTY, previousSaveRoot);
            if (previous != null) EngineServices.configure(previous);
            Engine.clearGlobalInstance();
            ModSubsystem.clearProcess();
            TestEnvironment.resetAll();
        }
    }

    /** {@code roms.default: scd}, no master title, title on startup: the player's configured launch. */
    private void boot() {
        TestEnvironment.resetAll();
        ModSubsystem.clearProcess();
        EngineContext bootstrap = EngineServices.current();
        SonicConfigurationService config = SonicConfigurationService.createStandalone(dir);
        GraphicsManager graphics = new GraphicsManager();
        graphics.initHeadless();
        var audio = bootstrap.audio();
        audio.setBackend(new NullAudioBackend());
        EngineContext services = new EngineContext(config, graphics, audio, bootstrap.roms(), bootstrap.profiler(),
                bootstrap.debugOverlay(), bootstrap.playbackDebug(), bootstrap.romDetection(),
                bootstrap.crossGameFeatures());
        EngineServices.configure(services);
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        config.setConfigValue(SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.TITLE_SCREEN_ON_STARTUP, true);
        config.setConfigValue(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.DEFAULT_ROM, "scd");
        // The shipped default, named so the stage load's counter base is the measured one (CdLevelVIntCounter).
        config.setConfigValue(SonicConfiguration.LOAD_TIME_SIMULATION, "FAST");
        input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        engine = new Engine(services);
        engine.initializeConfiguredHeadlessSession(input, new NullAudioBackend());
        last = null;
        frame = 0;
        assertEquals(GameMode.TITLE_SCREEN, engine.getCurrentGameMode(),
                "IPX Start runs the title straight after BRAMINIT (IPX.asm:39-65)");
        assertEquals(GameId.SCD, SessionManager.getCurrentWorldSession().getGameModule().getGameId());
    }

    private void closeEngine() {
        if (engine == null) return;
        try {
            engine.closeConfiguredHeadlessSession();
        } finally {
            engine = null;
            new SaveManager(SavePaths.root()).flushPendingWrites();
        }
    }

    /** One player frame with this pad (native bits: 0-3 directions, 4 B, 5 C, 6 A, 7 Start). */
    private void frame(Bk2FrameInput row) {
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(row, last));
        last = row;
        frame++;
        engine.advanceFrame();
    }

    private void press(int directions, boolean start) {
        frame(new Bk2FrameInput(frame, directions, 0, start, ""));
    }

    private void idle(int frames) {
        for (int i = 0; i < frames; i++) press(0, false);
    }

    private void until(BooleanSupplier done, int budget, String what) {
        for (int i = 0; i < budget && !done.getAsBoolean(); i++) idle(1);
        assertTrue(done.getAsBoolean(), what + " within " + budget + " frames");
    }

    private CdSceneCoordinator scenes() {
        return SessionManager.getCurrentWorldSession().getGameModule().getGameService(CdSceneCoordinator.class);
    }

    private CdTitleScreen title() { return (CdTitleScreen) engine.getGameLoop().getTitleScreenProvider(); }

    /** Waits for the title menu, Start to open it, then {@code rights} moves and Start on that option. */
    private void chooseTitleOption(int rights) {
        until(() -> engine.getCurrentGameMode() == GameMode.TITLE_SCREEN && title().program() != null
                && title().program().acceptsMenuInput(), 3000, "the title's PRESS START");
        press(0, true);
        idle(60);
        for (int i = 0; i < rights; i++) {
            press(0x08, false);
            idle(40);
        }
        press(0, true);
    }

    private CdLevel level() {
        var manager = GameServices.levelOrNull();
        return manager != null && manager.getCurrentLevel() instanceof CdLevel l ? l : null;
    }

    @Test
    void playerLaunchBootsTheDiscToTheTitleAndANewGamePlaysPalmtreePanicAsNative() throws Exception {
        Path traceDir = CdFixtureReplayAccess.nativeFile("rehost-fixtures/R11A/trace/metadata.json").getParent();
        Path moviePath = CdFixtureReplayAccess.nativeFile("rehost-fixtures/R11A/movie.bk2");
        TraceData trace = TraceData.load(traceDir);
        var movie = new Bk2MovieLoader().load(moviePath);
        boot();
        // New Game: the menu opens on NEW GAME with a fresh save (no CONTINUE, Menu/Main.asm:69-117).
        chooseTitleOption(0);
        until(() -> engine.getCurrentGameMode() == GameMode.LEVEL && level() != null, 900, "the stage");
        assertEquals(CdStagePayload.R11A, level().payload(), "NewGame runs Palmtree Panic act 1 present (IPX.asm:162-174)");
        assertEquals(com.openggf.game.soniccd.program.CdProgramRequest.Route.CAMPAIGN, scenes().flow().route());

        // The stage on the player's own frame loop, fed the fixture's recorded pads. A lag row (the native
        // main loop overran, VInt_Lag read no pads) runs no game frame; every other row is one player frame.
        // Row 0 is the frame that ran the load: native ends it with Level_MainLoop's first iteration
        // (levelFrames 1), which the load itself runs (CdFirstMainLoopIteration), so it is compared as it stands.
        int offset = trace.metadata().bk2FrameOffset();
        last = movie.getFrame(offset);
        List<String> errors = new ArrayList<>();
        int compared = 0, lagRows = 0, counterRows = 0;
        for (int i = 0; i < trace.frameCount(); i++) {
            if (i > 0) {
                TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(i - 1), trace.getFrame(i));
                if (phase == TraceExecutionPhase.VBLANK_ONLY) { lagRows++; continue; }
                frame(movie.getFrame(offset + i));
            }
            var sonic = GameServices.camera().getFocusedSprite();
            var expected = trace.getFrame(CdFixtureReplayAccess.completedRow(trace, i));
            var comparison = CdFixtureReplayAccess.compare(expected, sonic);
            compared++;
            if (comparison.hasError() && errors.size() < 5) errors.add("row " + i + ": " + comparison.divergentFields());
            // levelFrames (Level/Main.asm:400): the level frame the engine has run equals native's on every row.
            int levelFrames = GameServices.level().getFrameCounter();
            if (levelFrames != expected.gameplayFrameCounter() && errors.size() < 5) {
                errors.add("row " + i + ": levelFrames native " + expected.gameplayFrameCounter() + " engine " + levelFrames);
            }
            // levelVIntCounter (Level/Main.asm:711-712): FAST starts the stage from the measured load
            // (load-time-profiles/soniccd-fast-v1.json), so row 0 equals native. The player's loop then runs one V-INT
            // per level frame and none for a native lag or held frame (row 1 is already one), so on every row it is
            // native's row-0 count plus the level frames since: native's own count also holds its lag V-INTs.
            int counter = GameServices.level().getObjectManager().getVblaCounter() & 0xFFFF;
            int expectedCounter = (trace.getFrame(0).vblankCounter() + expected.gameplayFrameCounter() - 1) & 0xFFFF;
            if (counter != expectedCounter && errors.size() < 5) {
                errors.add("row " + i + ": levelVIntCounter engine " + counter + ", native row 0 + level frames "
                        + expectedCounter + " (native " + trace.getFrame(i).vblankCounter() + ")");
            }
            counterRows++;
        }
        assertEquals(trace.frameCount() - lagRows, compared);
        assertTrue(compared > 800, "the whole fixture, " + compared + " rows");
        assertTrue(counterRows > 800, "levelVIntCounter compared on " + counterRows + " rows");
        assertEquals(List.of(), errors, "the player's launch plays R11A as native on every compared row");
    }

    @Test
    void aSaveSurvivesARestartAndContinueRunsTheSavedZone() {
        boot();
        chooseTitleOption(0);
        until(() -> engine.getCurrentGameMode() == GameMode.LEVEL && level() != null, 900, "the stage");
        var ram = scenes().flow().backupRam();
        assertTrue(ram.commits() >= 1, "NewGame's WriteSaveData committed (IPX.asm:162-174)");
        // Progress a player would make by clearing Palmtree Panic and Collision Chaos, written as IPX writes it:
        // RunR1 sets titleFlags bits 6 and 5 (IPX.asm:180-181), RunR3 unlocks Time Attack to stage 6
        // (IPX.asm:200-201), and the save is written at RunR4 (IPX.asm:211-212).
        int flags = scenes().flow().titleFlags() | 0x60;
        ram.writeVariables(new CdSaveVariables(0x0200, 0, flags, 6, 0, 0, 0));
        byte[] saved = ram.internal().image();
        closeEngine();
        Path slot = dir.resolve("saves").resolve("soniccd").resolve("slot0.json");
        assertTrue(Files.isRegularFile(slot), "the backup RAM is a save beside the other games' saves: " + slot);

        boot();
        var flow = scenes().flow();
        assertArrayEquals(saved, flow.backupRam().internal().image(), "the restart read the same backup RAM");
        assertEquals(flags, flow.titleFlags());
        assertEquals(2, flow.savedLevel() >> 8);
        chooseTitleOption(0);                                   // CONTINUE is the first option now
        until(() -> engine.getCurrentGameMode() == GameMode.LEVEL && level() != null, 900, "the saved stage");
        assertEquals(2, level().payload().stage().zone().ordinal(), "LoadGame runs the saved zone (IPX.asm:137-160)");
    }

    /** One frame with these directions and actions (A 1, B 2, C 4), then one released frame: a tap. */
    private void tap(int directions, int actions) {
        frame(new Bk2FrameInput(frame, directions, actions, false, ""));
        idle(1);
    }

    /** The title menu as a finished campaign leaves it, through the game's own save write; then a restart. */
    private void bootWithTitleFlags(int titleFlags) {
        boot();
        scenes().flow().backupRam().writeVariables(new CdSaveVariables(0, 0x7F, titleFlags, 7, 0, 0, 0));
        closeEngine();
        boot();
        assertEquals(titleFlags, scenes().flow().titleFlags());
    }

    @Test
    void theStageSelectSecretAndTimeAttackReachTheirStages() {
        boot();
        until(() -> title().program() != null && title().program().acceptsMenuInput(), 3000, "the title's PRESS START");
        // ObjMenu_PressStart's stage-select cheat: Up, Down, Down, Left, Right, B (STAGE_SELECT, Menu/Main.asm:37-67).
        for (int d : new int[] {1, 2, 2, 4, 8}) tap(d, 0);
        tap(0, 2);
        until(() -> engine.getCurrentGameMode() == GameMode.LEVEL_SELECT, 600, "title exit 8: the stage select");
        idle(60);
        press(0, true);                                         // StageSels row 0: Palmtree Panic act 1 Present
        until(() -> engine.getCurrentGameMode() == GameMode.LEVEL && level() != null, 900, "the selected stage");
        assertEquals(CdStagePayload.R11A, level().payload());
        assertEquals(com.openggf.game.soniccd.program.CdProgramRequest.Route.STAGE_SELECT, scenes().flow().route());
        closeEngine();

        bootWithTitleFlags(0x7C);
        chooseTitleOption(1);                                   // from CONTINUE: TIME ATTACK
        until(() -> engine.getCurrentGameMode() == GameMode.OPTIONS, 600, "the Time Attack menu");
        assertInstanceOf(com.openggf.game.soniccd.timeattack.CdTimeAttackScreen.class, scenes().lastProgramScreen());
        idle(300);
        tap(0, 1);                                              // A: Palmtree Panic's panel
        idle(120);
        tap(8, 0);                                              // Right: GO
        idle(60);
        tap(0, 1);                                              // A: EnterSelectedStage
        until(() -> engine.getCurrentGameMode() == GameMode.LEVEL && level() != null, 900, "the Time Attack stage");
        assertTrue(scenes().flow().timeAttackMode(), "timeAttackMode (IPX.asm:1230)");
        assertEquals(CdStagePayload.R11A, level().payload());
    }

    @Test
    void programsThisBuildDoesNotHostReturnToTheTitle() {
        // Unlocks as a finished campaign leaves them: RAM DATA, D.A. Garden and Visual Mode on the menu
        // (titleFlags bits 4, 3, 2; Menu/Main.asm:69-117), CONTINUE first (bit 6).
        bootWithTitleFlags(0x7C);
        // Menu order from CONTINUE: TIME ATTACK, RAM DATA, D.A. GARDEN, VISUAL MODE.
        record Option(int rights, CdNativeProgram program) { }
        for (Option option : List.of(new Option(2, CdNativeProgram.BURAMMGR), new Option(3, CdNativeProgram.DAGARDEN),
                new Option(4, CdNativeProgram.VISMODE))) {
            int before = scenes().notHosted().size();
            chooseTitleOption(option.rights());
            until(() -> scenes().notHosted().size() > before, 600, option.program() + " requested");
            assertEquals(option.program(), scenes().notHosted().get(scenes().notHosted().size() - 1).program());
            until(() -> engine.getCurrentGameMode() == GameMode.TITLE_SCREEN && title().program() != null,
                    600, option.program() + " returns to the title");
        }
        // Attract mode: the title times out (ObjMenu_TimeOut) into IPX Demo: the opening video (hosted;
        // Start stops it, CheckFMVStop), then the Palmtree Panic demo and the special-stage demo (not hosted).
        int before = scenes().notHosted().size();
        until(() -> engine.getCurrentGameMode() == GameMode.OPTIONS, 2500, "the title's time-out into the opening video");
        assertEquals(CdNativeProgram.OPENING, scenes().notHosted().get(scenes().notHosted().size() - 1).program());
        idle(120);
        // CheckFMVStop reads the held pad only at a video-frame boundary (FrameRead_NextFrame,
        // FMV/Main (Opening).asm:342-343; CheckFMVStop, :512-516), every 7 or 8 V-ints, so Start is held for
        // 20 frames, as a player's press lasts.
        for (int i = 0; i < 20; i++) press(0, true);
        until(() -> engine.getCurrentGameMode() == GameMode.TITLE_SCREEN && title().program() != null, 600,
                "the opening video returns to the title");
        for (CdNativeProgram demo : List.of(CdNativeProgram.R11ADEMO, CdNativeProgram.SPECSTAGE)) {
            until(() -> requestedSince(before).contains(demo), 2500, demo + " requested");
            until(() -> engine.getCurrentGameMode() == GameMode.TITLE_SCREEN && title().program() != null, 600,
                    demo + " returns to the title");
        }
    }

    private List<CdNativeProgram> requestedSince(int before) {
        var steps = scenes().notHosted();
        return steps.subList(Math.min(before, steps.size()), steps.size()).stream().map(s -> s.program()).toList();
    }
}
