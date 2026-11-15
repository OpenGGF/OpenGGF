package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.player.CdPlayerStage;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.trace.TraceReplayFrameClosureDriver;
import com.openggf.tools.HeadlessGameBoot;
import com.openggf.trace.EngineDiagnostics;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceBinder;
import com.openggf.trace.TraceCharacterState;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceVIntServiceCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays one Sonic CD v5 fixture (any stage payload) through the hosted engine and
 * reports the first diverging row. Trace data is comparison-only: the BK2 supplies the
 * inputs and nothing read from {@code physics.csv} or {@code aux_state.jsonl} reaches
 * the engine.
 *
 * <p>Sampling (K2 report item 13): row N is taken after BK2 row
 * {@code bk2_frame_offset + N} completes. Native iteration 1 (levelFrames=1, row 0)
 * runs only ObjSonic_Init (Sonic/Main.asm:135-157), so row 0 is the loaded state and
 * its BK2 row is consumed without a gameplay step. After that the shared counter model
 * ({@code scd}) chooses a full step or a V-INT-only row, exactly as for Sonic 1.
 */
final class CdFixtureReplay {
    /** One engine row beside its native row. */
    record Row(int row, TraceExecutionPhase phase, int bk2Input, TraceFrame expected, int x, int y, int xSpeed,
               int ySpeed, int gSpeed, int angle, int status, int anim, int mapFrame, int cameraX, int cameraY,
               boolean ramp3d) {
        String format() {
            return String.format("%d %s in=%04X | ENG x=%04X y=%04X xs=%04X ys=%04X g=%04X ang=%02X st=%02X "
                            + "anim=%02X map=%02X cam=%04X,%04X 3d=%d | ROM x=%04X y=%04X xs=%04X ys=%04X g=%04X "
                            + "ang=%02X st=%02X anim=%02X map=%02X cam=%04X,%04X rt=%02X obj=%d",
                    row, phase, bk2Input, x, y, xSpeed, ySpeed, gSpeed, angle, status, anim, mapFrame, cameraX,
                    cameraY, ramp3d ? 1 : 0, expected.x() & 0xFFFF, expected.y() & 0xFFFF,
                    expected.xSpeed() & 0xFFFF, expected.ySpeed() & 0xFFFF, expected.gSpeed() & 0xFFFF,
                    expected.angle() & 0xFF, expected.statusByte() & 0xFF, expected.animationId() & 0xFF,
                    expected.mappingFrame() & 0xFF, expected.cameraX() & 0xFFFF, expected.cameraY() & 0xFFFF,
                    expected.routine() & 0xFF, expected.standOnObj());
        }
    }

    /**
     * The replay's result: {@code rowsCompared} rows replayed from row 0;
     * {@code firstErrorRow} is -1 when every compared row matched, and {@code fields}
     * are the fields in error on that row. A replay that does not stop at the first error
     * also reports the second-divergence view: {@code errorRows} compared rows in error,
     * {@code reconvergeRow} the first matching row after the first error (-1 if none) and
     * {@code secondErrorRow} the first error after that (-1 if none).
     */
    record Outcome(CdStagePayload payload, TraceData trace, int rowsCompared, int firstErrorRow,
                   List<FieldComparison> fields, List<Row> rows, CdLevel level, int errorRows,
                   int reconvergeRow, int secondErrorRow, List<FieldComparison> secondFields) {
        Row row(int index) {
            for (Row r : rows) if (r.row() == index) return r;
            return null;
        }
    }

    /** A fixture: the v5 trace directory and its BK2 movie. */
    record Fixture(CdStagePayload payload, Path traceDir, Path movie) {
        /** {@code <root>/<payload>/trace} and {@code <root>/<payload>/movie.bk2} (rehost-fixtures README). */
        static Fixture under(Path root, CdStagePayload payload) {
            return new Fixture(payload, root.resolve(payload.name()).resolve("trace"),
                    root.resolve(payload.name()).resolve("movie.bk2"));
        }

        boolean present() {
            return Files.isRegularFile(traceDir.resolve("metadata.json")) && Files.isRegularFile(movie);
        }
    }

    private CdFixtureReplay() { }

    /** Optional observers: each replayed row, and the live session after the last row. */
    interface Hooks {
        Hooks NONE = new Hooks() { };

        default void row(int row, AbstractPlayableSprite sonic) { }

        default void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) throws Exception { }

        /** The section 4 base the load is seeded with; a negative control may move it. */
        default int stageEntryBase(int recorded) { return recorded; }

        /**
         * {@code gameplay.loadTimeSimulation} for the replay's session, and whether the load is seeded. A trace
         * replay ignores the setting (the seed wins), so the default runs {@code NONE} with the seed; a check of
         * normal play's own load (no seed, {@code CdLevelVIntCounter} paced by the profile) names a mode instead.
         */
        default String productionLoadTime() { return null; }
    }

    /**
     * Boots {@code fixture.payload()} from {@code disc}, replays up to {@code lastRow}
     * (inclusive, capped by the trace) and returns the comparison. With
     * {@code stopAtFirstError} the replay ends on the first row in error. Rows in
     * {@code [keepFrom, keepTo]} and the four rows before the first error are kept.
     */
    static Outcome replay(CdDiscDataSource disc, Path configDir, Fixture fixture, int lastRow,
                          boolean stopAtFirstError, int keepFrom, int keepTo) throws Exception {
        return replay(disc, configDir, fixture, lastRow, stopAtFirstError, keepFrom, keepTo, Hooks.NONE);
    }

    /**
     * Replays the {@code rehost-fixtures/<payload>} movie (skipped when the optional native fixture or
     * the disc is absent) through every row up to {@code lastRow}; {@code afterRow} runs after each
     * row is stepped, while the level is still loaded (object-lane observations).
     */
    static Outcome replay(Path configDir, CdStagePayload payload, int lastRow, java.util.function.IntConsumer afterRow)
            throws Exception {
        Path traceDir = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/metadata.json").getParent();
        Path movie = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/movie.bk2");
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            return replay(disc, configDir, new Fixture(payload, traceDir, movie), lastRow, false, -1, -1, afterRow);
        }
    }

    static Outcome replay(CdDiscDataSource disc, Path configDir, Fixture fixture, int lastRow,
                          boolean stopAtFirstError, int keepFrom, int keepTo,
                          java.util.function.IntConsumer afterRow) throws Exception {
        return replay(disc, configDir, fixture, lastRow, stopAtFirstError, keepFrom, keepTo, new Hooks() {
            @Override public void row(int row, AbstractPlayableSprite sonic) { afterRow.accept(row); }
        });
    }

    /** As above, with {@code hooks} seeing every row and the session after the last one. */
    static Outcome replay(CdDiscDataSource disc, Path configDir, Fixture fixture, int lastRow,
                          boolean stopAtFirstError, int keepFrom, int keepTo, Hooks hooks) throws Exception {
        return replaySequence(disc, configDir, List.of(new Leg(fixture, lastRow)), stopAtFirstError, keepFrom, keepTo,
                hooks).getLast();
    }

    /** One stage of a {@link #replaySequence}: its fixture and the last row to replay. */
    record Leg(Fixture fixture, int lastRow) { }

    /**
     * Replays {@code legs} one after another in one gameplay session, as a player goes from stage to stage in one
     * process: the first stage is booted, each later one is loaded over the stage before it through the level owner's
     * stage load ({@code loadZoneAndAct}), with no reset in between. Each leg's movie starts at its own row 0 and is
     * compared with its own trace. A stage whose outcome here differs from {@link #replay} alone carries state across
     * the stage change.
     */
    static List<Outcome> replaySequence(CdDiscDataSource disc, Path configDir, List<Leg> legs,
                                        boolean stopAtFirstError, int keepFrom, int keepTo, Hooks hooks)
            throws Exception {
        EngineContext previous = EngineServices.current();
        TestEnvironment.resetAll();
        try {
            List<Outcome> outcomes = new ArrayList<>();
            for (int n = 0; n < legs.size(); n++) {
                Leg leg = legs.get(n);
                // Section 4's base for this leg's load, from its own trace's row 0 (see stageEntryBase).
                int base = hooks.stageEntryBase(stageEntryBase(TraceData.load(leg.fixture().traceDir())));
                String production = hooks.productionLoadTime();
                if (n == 0) {
                    boot(disc, configDir, leg.fixture().payload(), production == null ? base : null,
                            production == null ? "NONE" : production);
                } else {
                    if (production == null) {
                        SessionManager.requireCurrentGameModule().getGameService(CdLevelVIntCounter.class)
                                .seedNextStageForReplay(base);
                    }
                    loadStage(leg.fixture().payload());
                }
                outcomes.add(playLoaded(leg.fixture(), leg.lastRow(), stopAtFirstError, keepFrom, keepTo, hooks));
            }
            return outcomes;
        } finally {
            GroundSensor.setLevelManager(null);
            SessionManager.clear();
            GameModuleRegistry.reset();
            if (previous != null) EngineServices.configure(previous);
            TestEnvironment.resetAll();
        }
    }

    /** Loads {@code payload} over the loaded stage through the level owner's stage load. */
    static AbstractPlayableSprite loadStage(CdStagePayload payload) throws Exception {
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload),
                SonicCdZoneRegistry.actIndex(payload));
        if (!(GameServices.level().getCurrentLevel() instanceof CdLevel level) || level.payload() != payload) {
            throw new IllegalStateException("Loaded level is not " + payload);
        }
        return (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
    }

    /** Replays {@code fixture}'s movie over the stage just loaded (its row 0 is the loaded state). */
    private static Outcome playLoaded(Fixture fixture, int lastRow, boolean stopAtFirstError, int keepFrom,
                                      int keepTo, Hooks hooks) throws Exception {
        TraceData trace = TraceData.load(fixture.traceDir());
        if (!"scd".equals(trace.metadata().game())) {
            throw new IllegalStateException(fixture + " is not an scd trace");
        }
        Bk2Movie movie = new Bk2MovieLoader().load(fixture.movie());
        AbstractPlayableSprite sonic = (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
        CdLevel level = (CdLevel) GameServices.level().getCurrentLevel();
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
        runner.setBk2Movie(movie, trace.metadata().bk2FrameOffset());
        // Player and camera rows only: rings are not hosted yet (the common-object
        // lane owns them), so the ring counter is not a player-lane comparison.
        TraceBinder binder = binder();
        // The timing contract's lost-V-INT admission (2026-11-13): rows whose VBlank natively produced no
        // V-INT entry, from the trace's vint_service_timing stream; a trace without it services every row.
        TraceVIntServiceCoordinator vint = TraceVIntServiceCoordinator.open(fixture.traceDir(), trace,
                SessionManager.requireCurrentGameModule());
        int last = Math.min(trace.frameCount() - 1, lastRow);
        int firstError = -1;
        List<FieldComparison> fields = List.of();
        List<Row> kept = new ArrayList<>();
        java.util.ArrayDeque<Row> recent = new java.util.ArrayDeque<>();
        int compared = 0;
        int errorRows = 0;
        int reconverge = -1;
        int secondError = -1;
        List<FieldComparison> secondFields = List.of();
        for (int i = 0; i <= last; i++) {
            TraceFrame expected = trace.getFrame(i);
            TraceFrame prior = i > 0 ? trace.getFrame(i - 1) : null;
            int bk2Input;
            TraceExecutionPhase phase;
            boolean serviced = vint.vBlankServiced(i);
            if (i == 0) {
                // Iteration 1 is ObjSonic_Init only; the load already produced it.
                phase = TraceExecutionPhase.FULL_LEVEL_FRAME;
                bk2Input = runner.consumeRecordingFrameInputOnly();
            } else if (!serviced) {
                // Native VInterrupt (Level/Main.asm:681) was not entered at this VBlank: the previous handler was
                // still running. No V-INT work and no main loop: only the BK2 row is consumed. The coordinator
                // admitted this only on a VBlank-only row.
                phase = TraceReplayBootstrap.phaseForReplay(trace, prior, expected);
                bk2Input = runner.consumeRecordingFrameInputOnly();
            } else {
                phase = TraceReplayBootstrap.phaseForReplay(trace, prior, expected);
                bk2Input = TraceReplayFrameClosureDriver.driveGeneral(phase,
                        runner::stepFrameFromRecording, runner::skipFrameFromRecording, () -> { });
            }
            if (!binder.validateInput(expected, bk2Input)) {
                throw new IllegalStateException(String.format(
                        "Input alignment error at trace row %d: BK2=0x%04X trace=0x%04X",
                        i, bk2Input, expected.input()));
            }
            Row row = capture(i, phase, bk2Input, expected, sonic);
            hooks.row(i, sonic);
            recent.addLast(row);
            if (recent.size() > 5) recent.removeFirst();
            if (i >= keepFrom && i <= keepTo) kept.add(row);
            if (TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) {
                // A row whose levelFrames the next rows repeat is compared with the last of
                // them, where its level frame is complete (completedRow).
                TraceFrame comparisonExpected = completedLevelFrame(trace, i, prior, expected, phase);
                FrameComparison comparison = compare(binder, comparisonExpected, sonic);
                if (comparison.hasError()) errorRows++;
                if (firstError >= 0 && reconverge < 0 && !comparison.hasError()) {
                    reconverge = i;
                } else if (reconverge >= 0 && secondError < 0 && comparison.hasError()) {
                    secondError = i;
                    secondFields = comparison.divergentFields().stream()
                            .filter(f -> f.severity() == com.openggf.trace.Severity.ERROR).toList();
                }
                if (firstError < 0 && comparison.hasError()) {
                    firstError = i;
                    fields = comparison.divergentFields().stream()
                            .filter(f -> f.severity() == com.openggf.trace.Severity.ERROR).toList();
                    for (Row r : recent) if (!kept.contains(r)) kept.add(r);
                    if (stopAtFirstError) {
                        compared = i + 1;
                        break;
                    }
                }
            }
            compared = i + 1;
        }
        kept.sort(java.util.Comparator.comparingInt(Row::row));
        hooks.after(runner, sonic);
        return new Outcome(fixture.payload(), trace, compared, firstError, fields, kept, level, errorRows,
                reconverge, secondError, secondFields);
    }

    /**
     * The native state this engine row is compared with: {@link #completedRow}'s row, for every field.
     */
    static TraceFrame completedLevelFrame(TraceData trace, int i, TraceFrame prior, TraceFrame expected,
                                          TraceExecutionPhase phase) {
        return phase == TraceExecutionPhase.FULL_LEVEL_FRAME ? trace.getFrame(completedRow(trace, i)) : expected;
    }

    /**
     * The one sampling rule of the Sonic CD replay comparisons: the native row that holds row {@code row}'s level
     * frame completed is the last row with its {@code levelFrames} -- {@code row} itself unless the rows after it
     * repeat the counter (V-blank-only rows). The engine steps whole level frames, so every compared object and
     * field of an engine row is taken from that row. Fields the V-INT itself advances (its counter, the time-warp
     * timer, Tidal Tempest's water) are not level-frame state and are compared on their own row.
     *
     * <p>Why (LAG lane census, {@code tools/soniccd/lag_census.py} over 70 fixtures, 28 full-act routes and 40 boss
     * movies, joined with {@code tools/soniccd/lag_native_probe.cs}, which reads the 68K PC/SR and
     * {@code Level_MainLoop}'s stack slot at every recorder sample): the recorder reads RAM just before each V-INT,
     * so an ordinary row is a finished pass waiting in {@code VSync}. A repeated counter has three causes. (1) The
     * pass overran: VInterrupt (Level/Main.asm:681-686) found {@code vintRoutine} 0 and took VInt_Lag
     * (Level/Main.asm:738-766), which reads no pads, so the recorder flags the frame as lag. All 5,939 such rows follow a sample taken inside
     * a main-loop call (DrawObjects 3,366, LevelScroll 1,008, RunObjects 875, ...), and no pass ran on past a second
     * frame. (2) Pattern load cues decoded in V-INT ({@code DecompPLCSlow}, Level/Main.asm:846) ran past the sample:
     * 1,980 rows, every one with the PLC queue busy and the pass already finished. (3) An object's own blocking
     * {@code VSync} loop (Wacky Workbench Eggman's palette fades, objects/r6/boss.asm:545-559, vintRoutine $12):
     * 4 x 22 rows; the pass resumes after it. In (1) and (3) the sampled row holds a half-finished pass; in (2) it is
     * already finished, so the later row is the same state. A sample inside a pass with no repeat after it happens
     * only in the pass's last call ({@code UpdateGlobalAnims}) or before its {@code levelFrames} increment (70 rows).
     * A row sampled in {@code VSync} is never moved, so a pass the game itself cut short (e.g. {@code PlaceBlock}
     * leaving {@code d7} = $00800000 for {@code RunObjects}' {@code dbf}) is compared as it is.
     */
    static int completedRow(TraceData trace, int row) {
        int counter = trace.getFrame(row).gameplayFrameCounter();
        int end = row;
        while (end + 1 < trace.frameCount()) {
            TraceFrame next = trace.getFrame(end + 1);
            if (next.gameplayFrameCounter() != counter
                    || TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(end), next)
                    != TraceExecutionPhase.VBLANK_ONLY) {
                break;
            }
            end++;
        }
        return end;
    }

    /**
     * The first row of row {@code row}'s level frame: the row whose pass the following same-counter rows repeat
     * ({@link #completedRow}'s other end). A native event first seen on a plateau row (an object a later slot of
     * that pass created after the sample) belongs to the engine row that ran the pass.
     */
    static int levelFrameStart(TraceData trace, int row) {
        int start = row;
        while (start > 0
                && trace.getFrame(start).gameplayFrameCounter() == trace.getFrame(start - 1).gameplayFrameCounter()
                && TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(start - 1), trace.getFrame(start))
                == TraceExecutionPhase.VBLANK_ONLY) {
            start--;
        }
        return start;
    }

    /** The binder every Sonic CD replay comparison uses (rings are not compared here). */
    static TraceBinder binder() {
        return new TraceBinder(ToleranceConfig.DEFAULT.withRingCountMode(ToleranceConfig.RingCountMode.DISABLED));
    }

    /** {@link #compare} with a native row standing in for the engine (the rule's own checks). */
    static FrameComparison compareNative(TraceBinder binder, TraceFrame expected, TraceFrame actual) {
        EngineDiagnostics diag = new EngineDiagnostics(actual.routine(), -1, -1, actual.rings(), actual.statusByte(),
                actual.cameraX(), actual.cameraY(), -1, -1, -1, -1, "", actual.xSub(), actual.ySub(), -1, -1,
                actual.animationId(), actual.mappingFrame());
        return binder.compareFrame(expected, actual.x(), actual.y(), actual.xSpeed(), actual.ySpeed(), actual.gSpeed(),
                actual.angle(), actual.air(), actual.rolling(), actual.groundMode(), diag);
    }

    private static Row capture(int i, TraceExecutionPhase phase, int bk2Input, TraceFrame expected,
                               AbstractPlayableSprite sonic) {
        return new Row(i, phase, bk2Input, expected, sonic.getCentreX() & 0xFFFF, sonic.getCentreY() & 0xFFFF,
                sonic.getXSpeed() & 0xFFFF, sonic.getYSpeed() & 0xFFFF, sonic.getGSpeed() & 0xFFFF,
                sonic.getAngle() & 0xFF, statusByte(sonic) & 0xFF,
                sonic.getAnimationId() & 0xFF, sonic.getMappingFrame() & 0xFF,
                GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                CdPlayerStage.onPseudo3dRamp(sonic));
    }

    /**
     * The player's {@code oFlags} as the trace records it: the shared status booleans plus the CD player's own bits
     * ({@link com.openggf.game.soniccd.player.CdPlayerObjectFlags}, bit 7).
     */
    static int statusByte(AbstractPlayableSprite sonic) {
        return TraceCharacterState.statusByteFromSprite(sonic)
                | com.openggf.game.soniccd.player.CdPlayerObjectFlags.statusBits(sonic);
    }

    static FrameComparison compare(TraceBinder binder, TraceFrame expected, AbstractPlayableSprite sonic) {
        int camX = GameServices.camera().getX() & 0xFFFF;
        int camY = GameServices.camera().getY() & 0xFFFF;
        EngineDiagnostics diag = new EngineDiagnostics(
                TraceCharacterState.routineFromSprite(sonic), -1, -1, sonic.getRingCount(),
                statusByte(sonic), camX, camY, -1, -1, -1, -1,
                String.format("anim=%02X frame=%02X g=%04X", sonic.getAnimationId() & 0xFF,
                        sonic.getMappingFrame() & 0xFF, sonic.getGSpeed() & 0xFFFF),
                sonic.getXSubpixelRaw(), sonic.getYSubpixelRaw(), -1, -1,
                sonic.getAnimationId() & 0xFF, sonic.getMappingFrame() & 0xFF);
        return binder.compareFrame(expected, sonic.getCentreX(), sonic.getCentreY(),
                sonic.getXSpeed(), sonic.getYSpeed(), sonic.getGSpeed(), sonic.getAngle(),
                sonic.getAir(), sonic.getRolling(), sonic.getGroundMode().ordinal(), diag);
    }

    /** Boots {@code payload} from {@code disc} for a focused object test; the caller resets the engine. */
    /** No title card stays out longer than this many frames (the stock fixtures release at about 180). */
    private static final int TITLE_CARD_FRAME_CAP = 600;

    /**
     * Steps idle frames until the stage's title card lets go of the controls, as the game does after a load:
     * the stage load sets {@code ctrlLocked} with the title card (Level/Main.asm:272-274) and
     * {@code ObjTitleCard_WaitPLC} clears it (Level/_Objects/Title Card/Main.asm:181-186); until then
     * {@code ObjSonic_Main} keeps {@code playerCtrlHold} instead of the pad (Level/_Objects/Sonic/Main.asm:296-298).
     * Returns the frames stepped; fails when the load did not lock the controls or the card never released them.
     */
    static int releaseTitleCard(HeadlessTestRunner runner) {
        CdLevel level = (CdLevel) GameServices.level().getCurrentLevel();
        if (!level.flags().ctrlLocked) throw new IllegalStateException("the stage load did not lock the controls");
        int frames = 0;
        while (level.flags().ctrlLocked) {
            if (++frames > TITLE_CARD_FRAME_CAP) throw new IllegalStateException("the title card never released the controls");
            runner.stepIdleFrames(1);
        }
        return frames;
    }

    static AbstractPlayableSprite boot(Path configDir, CdDiscDataSource disc, CdStagePayload payload) throws Exception {
        return boot(disc, configDir, payload, null);
    }

    /**
     * The contract's section 4 initial base for this trace: {@code levelVIntCounter} before the V-INT that produced
     * row 0, the seed the other games take in {@code TraceReplaySessionBootstrap}. Row 0 must be the stage's first
     * main-loop iteration (levelFrames 1, {@code LoadedLevelFrameCounter}), the structural boundary the load stands
     * for; any other start has no load to seed.
     */
    static int stageEntryBase(TraceData trace) {
        int levelFrames = trace.getFrame(0).gameplayFrameCounter();
        if (levelFrames != 1) {
            throw new IllegalStateException("row 0 is levelFrames " + levelFrames
                    + ", not the stage's first iteration; no stage-start V-INT base applies");
        }
        return com.openggf.trace.replay.TraceReplaySessionBootstrap.vblankCounterBeforeRowZero(trace);
    }

    /** {@code stageEntryBase}: section 4's seed for the load, or {@code null} for production's (no trace). */
    private static AbstractPlayableSprite boot(CdDiscDataSource disc, Path configDir, CdStagePayload payload,
                                               Integer stageEntryBase) throws Exception {
        return boot(disc, configDir, payload, stageEntryBase, "NONE");
    }

    private static AbstractPlayableSprite boot(CdDiscDataSource disc, Path configDir, CdStagePayload payload,
                                               Integer stageEntryBase, String loadTimeSimulation) throws Exception {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(configDir);
        config.setConfigValue(SonicConfiguration.LOAD_TIME_SIMULATION, loadTimeSimulation);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        EngineContext old = EngineServices.current();
        EngineContext injected = new EngineContext(config, old.graphics(), old.audio(), old.roms(),
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(),
                old.crossGameFeatures());
        EngineServices.configure(injected);

        GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(
                injected, new SonicCdGameModule(), ModuleResolutionService.LaunchPolicy.DETERMINISTIC, disc);
        GameplaySessionFactory.attachManagers(gameplay, injected);
        injected.graphics().initHeadless();
        GameModuleRegistry.setCurrent(SessionManager.requireCurrentGameModule());

        var team = GameplayTeamBootstrap.registerActiveTeam(
                SessionManager.requireCurrentGameModule(), GameServices.sprites(), config);
        AbstractPlayableSprite sonic = team.mainSprite();
        GameServices.camera().setFocusedSprite(sonic);
        if (stageEntryBase != null) {
            SessionManager.requireCurrentGameModule().getGameService(CdLevelVIntCounter.class)
                    .seedNextStageForReplay(stageEntryBase);
        }
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload),
                SonicCdZoneRegistry.actIndex(payload));
        GroundSensor.setLevelManager(GameServices.level());
        if (!(GameServices.level().getCurrentLevel() instanceof CdLevel level) || level.payload() != payload) {
            throw new IllegalStateException("Loaded level is not " + payload);
        }
        return sonic;
    }
}
