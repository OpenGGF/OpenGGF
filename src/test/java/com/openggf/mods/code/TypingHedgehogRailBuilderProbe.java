package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.mode.CourseCheckpoint;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.CourseControlProbeAccess;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typing of the Hedgehog rail builder and repairer: turns a source pad program into a validated
 * {@code .rail} (see {@code examples/typing-of-the-hedgehog/.../typinghog/rails/Rail.java}).
 *
 * <p><b>Purpose.</b> Rails are open-loop pad programs. This tool replays a source program with the
 * mod's exact rail semantics (one program row per advanced controlled row from the first row after
 * level load, title-card rows included; jump = ACTION_A; press edges from the previous row; a
 * {@link GameplayFrameController} feeding {@code nativeInput} through
 * {@link ControlledFrameRuntime#step}) under the mod configuration (320 px, forced FAST load time,
 * donation off, the game's team, stock badniks present). At each failure (native hurt, death,
 * drift off the reference path, stuck) it backtracks to a course checkpoint and searches small local
 * input edits (jump shifts and length changes, removed or added jumps, direction holds, brief stops
 * and skips), accepting the cheapest edit that avoids the failure and re-joins the reference path
 * (a recorded state within 3 px and a similar speed, from which the source program is spliced back
 * in) and stays on it for a look-ahead. Every accepted program is re-validated by a fresh straight
 * run, because restores can re-seat dynamic objects in different slots (S1/S2). It then chooses a
 * boss row and 4-7 stand rows that pass a held 1800-row soak (no hurt, death or time-over) and an
 * exact restore-and-resume check of the whole stand chain, and writes the rail.
 *
 * <p><b>Inputs.</b> Committed BK2 movies sliced per act (lag rows dropped; the recorded P1 states are
 * read offline as the reference path only, never fed to gameplay) or authored cold routes (their own
 * first straight run is the reference). Targets: {@code -Dtypinghog.railbuild=s1:ghz3,s1:syz3}
 * (or a game code for all its targets); output directory {@code -Dtypinghog.railbuild.out}
 * (default {@code target/typinghog-railbuild}); one {@code .rail} plus a repair log per target.
 *
 * <pre>
 * python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TypingHedgehogRailBuilderProbe#s1" \
 *     -Dtypinghog.railbuild=s1 -Dtypinghog.railbuild.out=examples/typing-of-the-hedgehog/src/main/resources/rails \
 *     -Dsonic1.rom.path=... test
 * </pre>
 *
 * <p><b>Origin.</b> Typing of the Hedgehog rails lane, branch {@code feature/ai-typing-hedgehog-rails},
 * 2026-10-09, after the kill tests in {@code TypingHedgehogRailProbe}.
 */
class TypingHedgehogRailBuilderProbe {
    private static final String SPEC = System.getProperty("typinghog.railbuild");
    private static final Path OUT = Path.of(System.getProperty("typinghog.railbuild.out", "target/typinghog-railbuild"));
    private static final Path LOG_DIR = Path.of(System.getProperty("typinghog.railbuild.log", "target/typinghog-railbuild"));
    private static final Path TRACES = Path.of("src/test/resources/traces");
    private static final Path ROUTES = Path.of("src/test/resources/routes/s3k");

    static final int WIDTH = 320;
    static final int CHECKPOINT_SPACING = 30;
    static final int CHECKPOINT_RING = 40;
    static final int DRIFT_FAIL_PX = 16;
    static final int SYNC_PX = 4;
    static final int STUCK_FRAMES = 600;
    static final int RESYNC_HORIZON = 300;
    static final int LOOKAHEAD = 300;
    static final int SOAK_ROWS = 1800;
    static final int STAND_CLEAR_ROWS = 50;
    static final int STAND_SPACING = 600;
    static final int MAX_REPAIRS = 30;
    static final int CANDIDATE_BUDGET = 1200;
    static final long FAILURE_TIME_BUDGET_MS = 420_000;
    static final int TARGET_ROWS = 4500;
    static final int LEVEL_MODE = 0x0C;
    static final int PAD_BITS = 0x1F;
    static final int DIRS = 0x0F;
    static final int JUMP = AbstractPlayableSprite.INPUT_JUMP;

    @AfterEach
    void reset() {
        GameServices.configuration().clearSessionOverrides();
        SessionManager.clear();
    }

    // ------------------------------------------------------------------ targets

    /** One act to rail. {@code source} is a trace fixture dir or a cold-route .bk2; {@code firstFrame} 0/1 for routes. */
    record Target(String name, SonicGame game, String code, Path source, boolean cold, int firstFrame,
                  int zone, int act, String sidekick, boolean emeralds, boolean captureAuthored) {
        String team() { return sidekick.isEmpty() ? "sonic" : "sonic," + sidekick; }
        String file() { return code + "-" + name + ".rail"; }
    }

    static List<Target> targets(SonicGame game) {
        return switch (game) {
            case SONIC_1 -> List.of(
                    trace("ghz3", game, "s1", "s1/ghz3_completerun", 0, 2, ""),
                    trace("mz3", game, "s1", "s1/mz3_completerun", 1, 2, ""),
                    trace("syz3", game, "s1", "s1/syz3_completerun", 2, 2, ""),
                    trace("lz3", game, "s1", "s1/lz3_completerun", 3, 2, ""),
                    trace("slz3", game, "s1", "s1/slz3_completerun", 4, 2, ""),
                    trace("sbz2", game, "s1", "s1/sbz2_completerun", 5, 1, ""),
                    trace("fz", game, "s1", "s1/fz_completerun", 6, 0, ""));
            case SONIC_2 -> List.of(
                    trace("ehz2", game, "s2", "s2/runs/s2-sonic-tails-complete-emeralds/seg5_ehz2", 0, 1, "tails"),
                    trace("cpz2", game, "s2", "s2/cpz2", 1, 1, "tails"),
                    trace("arz2", game, "s2", "s2/arz2", 2, 1, "tails"),
                    trace("cnz2", game, "s2", "s2/cnz2", 3, 1, "tails"),
                    trace("htz2", game, "s2", "s2/htz2", 4, 1, "tails"),
                    trace("mcz2", game, "s2", "s2/mcz2", 5, 1, "tails"),
                    trace("ooz2", game, "s2", "s2/ooz2", 6, 1, "tails"),
                    trace("mtz3", game, "s2", "s2/mtz3", 7, 2, "tails"),
                    trace("scz", game, "s2", "s2/scz", 8, 0, "tails"),
                    trace("wfz", game, "s2", "s2/wfz", 9, 0, "tails"),
                    trace("dez", game, "s2", "s2/dez_ending", 10, 0, "tails"));
            default -> List.of(
                    // Complete-run act 1 slices (the boss acts' cold act-2 slices drift at row 0).
                    trace("aiz1", game, "s3k", "s3k/aiz_completerun", 0, 0, "tails"),
                    trace("hcz1", game, "s3k", "s3k/hcz_completerun", 1, 0, "tails"),
                    trace("mgz1", game, "s3k", "s3k/mgz_completerun", 2, 0, "tails"),
                    trace("cnz1", game, "s3k", "s3k/cnz_completerun", 3, 0, "tails"),
                    trace("fbz1", game, "s3k", "s3k/fbz_completerun", 4, 0, "tails"),
                    trace("icz1", game, "s3k", "s3k/icz_completerun", 5, 0, "tails"),
                    trace("lbz1", game, "s3k", "s3k/lbz_completerun", 6, 0, "tails"),
                    // Engine-authored cold routes.
                    cold("ddz", "ddz-super-fresh-320", 1, 12, 0, "", true),
                    cold("mhz1", "mhz1-team-cold-complete-320", 1, 7, 0, "tails", false),
                    cold("dez1", "dez1-sonic-tails-cold-complete-320", 1, 11, 0, "tails", false),
                    cold("lrz1", "lrz-boss-sonic-tails-cold-hpz-320", 1, 9, 0, "tails", false),
                    cold("soz2", "soz2-cold-sonic-tails", 0, 8, 1, "tails", false),
                    cold("soz1", "soz1-cold-sonic-tails", 0, 8, 0, "tails", false),
                    cold("ssz1", "ssz1-sonic-tails-cold-complete-320", 1, 10, 0, "tails", false));
        };
    }

    private static Target trace(String name, SonicGame game, String code, String dir, int zone, int act, String sidekick) {
        return new Target(name, game, code, TRACES.resolve(dir), false, 0, zone, act, sidekick, false, false);
    }

    private static Target cold(String name, String route, int firstFrame, int zone, int act, String sidekick,
                               boolean emeralds) {
        // MHZ/DDZ routes were authored on HeadlessTestFixture; the others on GameplayCaptureSession,
        // which omits the initial title card and skips intros (its SOZ tests keep them).
        boolean capture = !route.startsWith("mhz") && !route.startsWith("ddz");
        return new Target(name, SonicGame.SONIC_3K, "s3k", ROUTES.resolve(route + ".bk2"), true, firstFrame,
                zone, act, sidekick, emeralds, capture);
    }

    // ------------------------------------------------------------------ source and program

    /** Source rows (lag rows dropped) with the recorded P1 state after each, when one exists. */
    static final class Source {
        int[] masks;
        int[] traceRow;
        int[] refX, refY, refXs, refYs;
        boolean[] refAir;
        boolean hasRef;
        int recordedFirstHurt = -1;

        int length() { return masks.length; }
    }

    static Source load(Target target) throws IOException {
        Source source = new Source();
        if (target.cold()) {
            Bk2Movie movie = new Bk2MovieLoader().loadMovieOrInputLog(target.source());
            int n = movie.getFrameCount() - target.firstFrame();
            source.masks = new int[n];
            source.traceRow = new int[n];
            for (int i = 0; i < n; i++) {
                source.masks[i] = movie.getFrame(i + target.firstFrame()).p1InputMask() & PAD_BITS;
                source.traceRow[i] = i + target.firstFrame();
            }
            authoredReference(target, movie, source);
            return source;
        }
        TraceData trace = TraceData.load(target.source());
        TraceMetadata meta = trace.metadata();
        Bk2Movie movie = new Bk2MovieLoader().load(resolveBk2(target.source(), meta));
        int offset = meta.bk2FrameOffset();
        int from = TraceReplayBootstrap.preLevelFrameCountForTraceReplay(trace);
        int to = trace.frameCount();
        for (int[] slice : levelSlices(trace)) {
            if (slice[2] <= from && from < slice[3]) { to = slice[3]; break; }
            if (slice[2] > from) { to = Math.min(to, slice[2]); break; }
        }
        List<Integer> rows = new ArrayList<>();
        for (int row = from; row < to; row++) {
            boolean lag = row > 0 && trace.getFrame(row).gameplayFrameCounter()
                    == trace.getFrame(row - 1).gameplayFrameCounter();
            if (!lag) rows.add(row);
        }
        int n = rows.size();
        source.masks = new int[n];
        source.traceRow = new int[n];
        source.refX = new int[n];
        source.refY = new int[n];
        source.refXs = new int[n];
        source.refYs = new int[n];
        source.refAir = new boolean[n];
        source.hasRef = true;
        for (int i = 0; i < n; i++) {
            int row = rows.get(i);
            var frame = trace.getFrame(row);
            int movieFrame = offset + row;
            source.masks[i] = movieFrame < movie.getFrameCount() ? movie.getFrame(movieFrame).p1InputMask() & PAD_BITS : 0;
            source.traceRow[i] = row;
            source.refX[i] = frame.x() & 0xFFFF;
            source.refY[i] = frame.y() & 0xFFFF;
            source.refXs[i] = frame.xSpeed();
            source.refYs[i] = frame.ySpeed();
            source.refAir[i] = frame.air();
            if (source.recordedFirstHurt < 0 && frame.routine() == 4) source.recordedFirstHurt = i;
        }
        return source;
    }

    /**
     * An authored route's reference path: the route replayed through its authoring harness (the
     * non-controlled fixture, as its route test does), recording P1 after each frame. This is an
     * engine trajectory, not trace data; it lets the controlled replay be aligned and repaired.
     */
    static void authoredReference(Target target, Bk2Movie movie, Source source) throws IOException {
        resetModule();
        var config = GameServices.configuration();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, WIDTH);
        config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "FAST");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, target.sidekick());
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,
                target.captureAuthored() && !target.source().getFileName().toString().startsWith("soz"));
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(target.zone(), target.act())
                .withFreshLevelStartLifecycle().build();
        if (target.emeralds()) GameServices.gameState().restoreS3kEmeraldProgress(Collections.nCopies(7, 1), false);
        if (target.captureAuthored()) GameServices.level().skipPendingInitialTitleCardPresentation();
        GameServices.level().consumePendingInitialProcessSpritesPass();
        fixture.runner().primeInputState(target.firstFrame() > 0 ? movie.getFrame(0)
                : new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        int n = source.length();
        source.refX = new int[n];
        source.refY = new int[n];
        source.refXs = new int[n];
        source.refYs = new int[n];
        source.refAir = new boolean[n];
        int startZone = target.zone(), startAct = target.act();
        int last = n;
        for (int i = 0; i < n; i++) {
            if (target.captureAuthored()) GameServices.level().skipPendingInitialTitleCardPresentation();
            int mask = source.masks[i];
            fixture.runner().stepFrame((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0,
                    (mask & JUMP) != 0);
            var p = (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
            source.refX[i] = p.getCentreX() & 0xFFFF;
            source.refY[i] = p.getCentreY() & 0xFFFF;
            source.refXs[i] = p.getXSpeed();
            source.refYs[i] = p.getYSpeed();
            source.refAir[i] = p.getAir();
            if (source.recordedFirstHurt < 0 && p.isHurt()) source.recordedFirstHurt = i;
            if (p.getDead() || GameServices.level().getCurrentZone() != startZone
                    || GameServices.level().getCurrentAct() != startAct) {
                last = i + 1;
                break;
            }
        }
        if (last < n) {
            // The authored run left the act (or died) here; the rail ends at the act anyway.
            source.masks = java.util.Arrays.copyOf(source.masks, last);
            source.traceRow = java.util.Arrays.copyOf(source.traceRow, last);
            source.refX = java.util.Arrays.copyOf(source.refX, last);
            source.refY = java.util.Arrays.copyOf(source.refY, last);
            source.refXs = java.util.Arrays.copyOf(source.refXs, last);
            source.refYs = java.util.Arrays.copyOf(source.refYs, last);
            source.refAir = java.util.Arrays.copyOf(source.refAir, last);
        }
        source.hasRef = true;
        config.clearSessionOverrides();
        SessionManager.clear();
    }

    /** The ROM's stock module again (a previous session left the patched one current). */
    static void resetModule() throws IOException {
        GameModuleRegistry.detectAndSetModule(com.openggf.data.RomManager.getInstance().getRom());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private static List<int[]> levelSlices(TraceData trace) {
        List<int[]> slices = new ArrayList<>();
        Integer zone = null, act = null, mode = null;
        int[] open = null;
        for (int row = 0; row < trace.frameCount(); row++) {
            for (TraceEvent event : trace.getEventsForFrame(row)) {
                if (event instanceof TraceEvent.ZoneActState state) {
                    if (state.actualZoneId() != null) zone = state.actualZoneId();
                    if (state.actualAct() != null) act = state.actualAct();
                    if (state.gameMode() != null) mode = state.gameMode();
                }
            }
            boolean level = mode != null && mode == LEVEL_MODE && zone != null && act != null;
            if (open != null && (!level || open[0] != zone || open[1] != act)) {
                open[3] = row;
                slices.add(open);
                open = null;
            }
            if (open == null && level) open = new int[] {zone, act, row, -1};
        }
        if (open != null) {
            open[3] = trace.frameCount();
            slices.add(open);
        }
        return slices;
    }

    private static Path resolveBk2(Path dir, TraceMetadata meta) throws IOException {
        if (meta.sourceBk2() != null && !meta.sourceBk2().isBlank()) {
            Path shared = dir.getParent().resolve("_movies").resolve(meta.sourceBk2());
            if (Files.exists(shared)) return shared;
            Path runRoot = dir.getParent().resolve(meta.sourceBk2());
            if (Files.exists(runRoot)) return runRoot;
        }
        try (var files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".bk2")).findFirst()
                    .orElseThrow(() -> new IOException("no .bk2 for " + dir));
        }
    }

    /** A working program: a mask per row and the source row it plays (-1 for inserted rows). */
    record Program(int[] masks, int[] src) {
        int length() { return masks.length; }

        static Program fromSource(Source source, int pad, int sourceFrom) {
            int n = pad + source.length() - sourceFrom;
            int[] masks = new int[n];
            int[] src = new int[n];
            for (int i = 0; i < pad; i++) src[i] = -1;
            for (int i = sourceFrom; i < source.length(); i++) {
                masks[pad + i - sourceFrom] = source.masks[i];
                src[pad + i - sourceFrom] = i;
            }
            return new Program(masks, src);
        }

        /** The first {@code rows} rows, then the source from {@code sourceRow}. */
        Program splice(int rows, Source source, int sourceRow) {
            int tail = Math.max(0, source.length() - sourceRow);
            int[] masks = new int[rows + tail];
            int[] src = new int[rows + tail];
            System.arraycopy(this.masks, 0, masks, 0, rows);
            System.arraycopy(this.src, 0, src, 0, rows);
            for (int i = 0; i < tail; i++) {
                masks[rows + i] = source.masks[sourceRow + i];
                src[rows + i] = sourceRow + i;
            }
            return new Program(masks, src);
        }

        Program copy() { return new Program(masks.clone(), src.clone()); }

        int mask(int row) { return row >= 0 && row < masks.length ? masks[row] : 0; }
    }

    // ------------------------------------------------------------------ the rail controller

    /** Course's rail semantics without the typing layer: RUN rows feed the program, held rows are neutral. */
    static final class Driver implements GameplayFrameController, RewindSnapshottable<Integer> {
        int[] program = new int[0];
        int row;
        int previousMask;
        boolean holding;

        @Override public String key() { return "typing-rail-builder"; }
        @Override public Integer capture() { return 0; }
        @Override public void restore(Integer value) { }

        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
            if (holding) holdPlayer();
            return true;
        }

        @Override public void afterTick(CourseControl course, boolean advanced) { }

        @Override public LogicalInputSnapshot nativeInput(CourseControl course) {
            int mask = 0;
            if (!holding) {
                mask = row < program.length ? program[row] & PAD_BITS : 0;
                row++;
            }
            int action = (mask & JUMP) != 0 ? InputActionMasks.ACTION_A : 0;
            int previousAction = (previousMask & JUMP) != 0 ? InputActionMasks.ACTION_A : 0;
            var p1 = PlayerInputState.of(mask, mask & ~previousMask, action, action & ~previousAction, false, false);
            previousMask = mask;
            return LogicalInputSnapshot.ofPlayers(p1, PlayerInputState.neutral());
        }

        @Override public boolean allowsDebugRewind() { return false; }

        /** Course.holdPlayer: Sonic held while the world lives on. */
        static void holdPlayer() {
            if (!(GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite player)) return;
            player.setObjectControlled(true);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
        }
    }

    // ------------------------------------------------------------------ session

    /** P1 state after a row. */
    record State(int x, int y, int xs, int ys, int gs, int angle, boolean air, boolean onObject, boolean hurt,
                 boolean dead, boolean controlled, boolean boss, boolean actEnd, int zone, int act, int rings) {
        static State observe() {
            var p = (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
            var state = GameServices.gameState();
            var level = GameServices.level();
            return new State(p.getCentreX() & 0xFFFF, p.getCentreY() & 0xFFFF, p.getXSpeed(), p.getYSpeed(),
                    p.getGSpeed(), p.getAngle() & 0xFF, p.getAir(), p.isOnObject(), p.isHurt(), p.getDead(),
                    p.isObjectControlled() || p.isControlLocked(), state.isBossFightActive(),
                    state.isActCompletionSignalActive() || state.isEndOfLevelActive(),
                    level.getCurrentZone(), level.getCurrentAct(), p.getRingCount());
        }

        boolean samePlace(State o) { return x == o.x && y == o.y && xs == o.xs && ys == o.ys; }
    }

    record Mark(int row, int previousMask, CourseCheckpoint checkpoint) { }

    final class Session {
        final Target target;
        final Driver driver = new Driver();
        final InputHandler input = new InputHandler();
        SharedLevel bootstrap;
        HeadlessTestFixture fixture;
        CourseControl course;

        Session(Target target, Program program) throws IOException {
            this.target = target;
            driver.program = program.masks();
            resetModule();
            var config = GameServices.configuration();
            config.clearSessionOverrides();
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
            config.resolveDisplayAspect();
            config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, WIDTH);
            config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "FAST");
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, target.sidekick());
            config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
            GameServices.graphics().setProjectionWidth(WIDTH);
            var context = new ModContext("typing-rail-builder", target.code(), ModAssetRoot.forTests("rail-builder"));
            context.registerServiceBundle("rails", () -> GameServiceBundle.builder().frameController("rails", driver).build());
            var boundary = new ModFaultBoundary(Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                    owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> { });
            GameModule effective = new ModBackedGamePatch(context.freeze(), boundary).apply(GameServices.module(), null);
            SessionManager.clear();
            GameModuleRegistry.setCurrent(effective);
            TestEnvironment.activeGameplayMode();
            fixture = HeadlessTestFixture.builder().withZoneAndAct(target.zone(), target.act()).build();
            if (fixture.camera().getWidth() != WIDTH) throw new IllegalStateException("viewport " + fixture.camera().getWidth());
            if (target.emeralds()) GameServices.gameState().restoreS3kEmeraldProgress(Collections.nCopies(7, 1), false);
            course = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
            // The initial Process_Sprites pass is SETUP_ONLY: not a controlled row.
            for (int i = 0; i < 8 && driver.row == 0 && ControlledFrameRuntime.prepareSetup(fixture.runtime()); i++) { }
        }

        /** One controlled row; returns true when the driver consumed a program row (or held one). */
        void step() {
            ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
            input.update();
        }

        Mark mark() { return new Mark(driver.row, driver.previousMask, course.capture()); }

        void restore(Mark mark, int[] program) {
            course.restore(mark.checkpoint());
            driver.program = program;
            driver.row = mark.row();
            driver.previousMask = mark.previousMask();
            driver.holding = false;
        }

        void close() {
            if (bootstrap != null) bootstrap.dispose();
            bootstrap = null;
        }
    }

    // ------------------------------------------------------------------ straight runs

    /** How a run ended; {@code row} is the program row on which it ended (rows before it are clean). */
    record Outcome(String kind, int row, int divergeRow, int bossRow, int actEndRow, State[] states) {
        boolean success() { return kind.equals("boss") || kind.equals("act-end") || kind.equals("level-change"); }
    }

    /**
     * Steps the session's program from its current row until an end or a failure. Records the state
     * after every row into {@code states}; keeps a ring of checkpoints when {@code ring} is non-null.
     */
    Outcome run(Session s, Program program, Source source, int limitRow, ArrayDeque<Mark> ring, State[] states,
                boolean stopAtBoss) {
        return run(s, program, source, limitRow, ring, states, stopAtBoss, 0);
    }

    /** As above; drift against the reference is only checked on rows at or after {@code driftFrom}. */
    Outcome run(Session s, Program program, Source source, int limitRow, ArrayDeque<Mark> ring, State[] states,
                boolean stopAtBoss, int driftFrom) {
        int startZone = s.target.zone(), startAct = s.target.act();
        int divergeRow = -1, bossRow = -1, actEndRow = -1;
        boolean wasHurt = false;
        java.util.Set<Long> cells = new HashSet<>();
        int lastNewCell = s.driver.row;
        while (s.driver.row < Math.min(limitRow, program.length())) {
            int before = s.driver.row;
            if (ring != null && before % CHECKPOINT_SPACING == 0) {
                if (ring.isEmpty() || ring.peekLast().row() != before) {
                    ring.addLast(s.mark());
                    while (ring.size() > CHECKPOINT_RING) ring.removeFirst();
                }
            }
            try {
                s.step();
            } catch (Throwable t) {
                return new Outcome("exception:" + brief(t), before, divergeRow, bossRow, actEndRow, states);
            }
            if (s.driver.row == before) continue;
            int row = before;
            State st = State.observe();
            if (states != null && row < states.length) states[row] = st;
            if (st.zone() != startZone || st.act() != startAct) {
                return new Outcome("level-change", row, divergeRow, bossRow, actEndRow < 0 ? row : actEndRow, states);
            }
            if (st.dead()) return new Outcome("death", row, divergeRow, bossRow, actEndRow, states);
            if (st.hurt() && !wasHurt) return new Outcome("hurt", row, divergeRow, bossRow, actEndRow, states);
            wasHurt = st.hurt();
            if (st.boss() && bossRow < 0) {
                bossRow = row;
                if (stopAtBoss) return new Outcome("boss", row, divergeRow, bossRow, actEndRow, states);
            }
            if (st.actEnd() && actEndRow < 0) {
                actEndRow = row;
                return new Outcome("act-end", row, divergeRow, bossRow, actEndRow, states);
            }
            int src = program.src()[row];
            if (source.hasRef && src >= 0 && row >= driftFrom) {
                int d = Math.max(Math.abs(st.x() - source.refX[src]), Math.abs(st.y() - source.refY[src]));
                if (d > 2 && divergeRow < 0) divergeRow = row;
                if (d > DRIFT_FAIL_PX) return new Outcome("drift", row, divergeRow, bossRow, actEndRow, states);
            }
            if (cells.add(((long) (st.x() >> 5) << 32) | (st.y() >> 5)) || st.controlled()) {
                lastNewCell = row;
            } else if (divergeRow >= 0 && row - lastNewCell >= STUCK_FRAMES) {
                return new Outcome("stuck", lastNewCell, divergeRow, bossRow, actEndRow, states);
            }
        }
        return new Outcome("exhausted", s.driver.row, divergeRow, bossRow, actEndRow, states);
    }

    // ------------------------------------------------------------------ repair search

    record Edit(String description, int cost, int firstRow, Program program) { }

    static List<Edit> edits(Program p, int from, int to) {
        List<Edit> edits = new ArrayList<>();
        int n = p.length();
        from = Math.max(1, from);
        to = Math.min(n - 1, to);
        // Jump presses overlapping the window.
        List<int[]> presses = new ArrayList<>();
        for (int i = Math.max(0, from - 40); i <= Math.min(n - 1, to + 10); i++) {
            if ((p.masks()[i] & JUMP) != 0 && (i == 0 || (p.masks()[i - 1] & JUMP) == 0)) {
                int end = i;
                while (end < n && (p.masks()[end] & JUMP) != 0) end++;
                presses.add(new int[] {i, end});
            }
        }
        for (int[] press : presses) {
            int a = press[0], b = press[1];
            for (int d = -16; d <= 16; d++) {
                if (d == 0 || a + d < 1 || b + d >= n) continue;
                int[] m = p.masks().clone();
                for (int i = a; i < b; i++) m[i] &= ~JUMP;
                for (int i = a + d; i < b + d; i++) m[i] |= JUMP;
                edits.add(new Edit("shift jump @" + a + " by " + d, Math.abs(d), Math.min(a, a + d), new Program(m, p.src())));
            }
            for (int d : new int[] {-16, -8, -4, 4, 8, 16}) {
                int nb = b + d;
                if (nb <= a || nb >= n) continue;
                int[] m = p.masks().clone();
                for (int i = Math.min(b, nb); i < Math.max(b, nb); i++) {
                    if (d > 0) m[i] |= JUMP; else m[i] &= ~JUMP;
                }
                edits.add(new Edit("jump @" + a + " length " + (b - a) + "->" + (nb - a), 3, Math.min(b, nb),
                        new Program(m, p.src())));
            }
            int[] m = p.masks().clone();
            for (int i = a; i < b; i++) m[i] &= ~JUMP;
            edits.add(new Edit("remove jump @" + a, 8, a, new Program(m, p.src())));
        }
        for (int t = from; t <= to; t += 3) {
            for (int len : new int[] {6, 12, 24}) {
                if (t + len >= n) continue;
                int[] m = p.masks().clone();
                for (int i = t; i < t + len; i++) m[i] |= JUMP;
                if (t > 0) m[t - 1] &= ~JUMP;
                edits.add(new Edit("add jump @" + t + " x" + len, 6 + len / 12, Math.max(0, t - 1), new Program(m, p.src())));
            }
        }
        for (int t = from; t <= to; t += 5) {
            for (int len : new int[] {10, 20, 40}) {
                if (t + len >= n) continue;
                for (int dir : new int[] {AbstractPlayableSprite.INPUT_RIGHT, AbstractPlayableSprite.INPUT_LEFT, 0}) {
                    int[] m = p.masks().clone();
                    for (int i = t; i < t + len; i++) m[i] = (m[i] & ~DIRS) | dir;
                    String name = dir == 0 ? "release" : dir == AbstractPlayableSprite.INPUT_RIGHT ? "right" : "left";
                    edits.add(new Edit(name + " @" + t + " x" + len, 7 + len / 10, t, new Program(m, p.src())));
                }
            }
            for (int len : new int[] {8, 16, 32, 60}) {
                int[] m = new int[n + len];
                int[] src = new int[n + len];
                System.arraycopy(p.masks(), 0, m, 0, t);
                System.arraycopy(p.src(), 0, src, 0, t);
                for (int i = t; i < t + len; i++) src[i] = -1;
                System.arraycopy(p.masks(), t, m, t + len, n - t);
                System.arraycopy(p.src(), t, src, t + len, n - t);
                edits.add(new Edit("stop @" + t + " x" + len, 4 + len / 8, t, new Program(m, src)));
            }
            for (int len : new int[] {8, 16, 32}) {
                if (t + len >= n) continue;
                int[] m = new int[n - len];
                int[] src = new int[n - len];
                System.arraycopy(p.masks(), 0, m, 0, t);
                System.arraycopy(p.src(), 0, src, 0, t);
                System.arraycopy(p.masks(), t + len, m, t, n - t - len);
                System.arraycopy(p.src(), t + len, src, t, n - t - len);
                edits.add(new Edit("skip @" + t + " x" + len, 6 + len / 8, t, new Program(m, src)));
            }
        }
        edits.sort(Comparator.comparingInt(Edit::cost));
        return edits;
    }

    /**
     * Searches edits near a failure; returns the repaired program (spliced back onto the source) or
     * null. The session is left in an arbitrary state.
     */
    Program repair(Session s, Program p, Source source, Outcome failure, ArrayDeque<Mark> ring, StringBuilder log,
                   Set<String> tried) {
        int f = failure.row();
        int d = failure.divergeRow() >= 0 ? Math.min(failure.divergeRow(), f) : f;
        int from = Math.max(1, d - 150);
        List<Edit> edits = edits(p, from, f + 5);
        long deadline = System.currentTimeMillis() + FAILURE_TIME_BUDGET_MS;
        int evaluated = 0;
        for (Edit edit : edits) {
            if (evaluated >= CANDIDATE_BUDGET || System.currentTimeMillis() > deadline) break;
            if (!tried.add(edit.description() + "|" + f)) continue;
            Mark mark = null;
            for (Mark m : ring) if (m.row() <= edit.firstRow() && (mark == null || m.row() > mark.row())) mark = m;
            if (mark == null) continue;
            evaluated++;
            Program cand = edit.program();
            int horizon = Math.min(cand.length(), f + RESYNC_HORIZON + 60);
            State[] states = new State[cand.length()];
            s.restore(mark, cand.masks());
            Outcome o = run(s, cand, source, horizon, null, states, false, Integer.MAX_VALUE);
            if (!o.kind().equals("exhausted") && !o.kind().equals("drift") && !o.kind().equals("stuck")) {
                if (!o.success()) continue;
            }
            int resyncLimit = o.kind().equals("exhausted") ? horizon : o.row();
            Program spliced = null;
            int spliceRow = -1, spliceSrc = -1;
            int startCheck = Math.max(f, edit.firstRow()) + 10;
            int floor = Math.max(0, expectedSource(p, d));
            for (int m = startCheck; m < resyncLimit && spliced == null; m += 5) {
                State st = states[m];
                if (st == null) continue;
                if (!source.hasRef) break;
                int expected = expectedSource(cand, m);
                for (int sr = Math.max(floor, expected - 300); sr < Math.min(source.length() - 1, expected + 300); sr++) {
                    if (Math.abs(st.x() - source.refX[sr]) <= 3 && Math.abs(st.y() - source.refY[sr]) <= 3
                            && Math.abs(st.xs() - source.refXs[sr]) <= 0x60 && Math.abs(st.ys() - source.refYs[sr]) <= 0x60
                            && st.air() == source.refAir[sr]) {
                        spliced = cand.splice(m + 1, source, sr + 1);
                        // The edited stretch no longer follows its old source rows: no reference there.
                        for (int r = edit.firstRow(); r <= m; r++) spliced.src()[r] = -1;
                        spliceRow = m;
                        spliceSrc = sr;
                        break;
                    }
                }
            }
            if (spliced == null && !source.hasRef && o.kind().equals("exhausted")) {
                // No reference path (authored route): survive the horizon and keep the edited tail.
                spliced = cand;
                spliceRow = horizon;
            }
            if (spliced == null) continue;
            // Look-ahead on the spliced program: no failure and back on the reference.
            State[] look = new State[spliced.length()];
            s.restore(mark, spliced.masks());
            int lookEnd = Math.min(spliced.length(), spliceRow + 1 + LOOKAHEAD);
            Outcome la = run(s, spliced, source, lookEnd, null, look, false, spliceRow + 1);
            if (!la.kind().equals("exhausted") && !la.success()) continue;
            if (source.hasRef && la.divergeRow() >= 0 && la.divergeRow() > spliceRow) {
                boolean ok = true;
                for (int r = spliceRow + 1; r < lookEnd && ok; r++) {
                    int src = spliced.src()[r];
                    if (look[r] == null || src < 0) continue;
                    ok = Math.abs(look[r].x() - source.refX[src]) <= SYNC_PX && Math.abs(look[r].y() - source.refY[src]) <= SYNC_PX;
                }
                if (!ok) continue;
            }
            log.append(String.format("  repair %s at row %d (diverged %d): %s; resync row %d -> source %d (trace row %d), %d candidates%n",
                    failure.kind(), f, failure.divergeRow(), edit.description(), spliceRow, spliceSrc,
                    spliceSrc >= 0 ? source.traceRow[spliceSrc] : -1, evaluated));
            return spliced;
        }
        log.append(String.format("  no repair for %s at row %d (diverged %d) after %d candidates%n",
                failure.kind(), f, failure.divergeRow(), evaluated));
        return null;
    }

    private static int expectedSource(Program p, int row) {
        for (int r = row; r >= 0; r--) if (p.src()[r] >= 0) return p.src()[r] + (row - r);
        for (int r = row; r < p.length(); r++) if (p.src()[r] >= 0) return p.src()[r] - (r - row);
        return row;
    }

    // ------------------------------------------------------------------ alignment

    /** Leading neutral rows (title card) that best align the recording with the controlled run. */
    int alignPad(Target target, Source source, StringBuilder log) throws IOException {
        if (!source.hasRef) return 0;
        Session s = new Session(target, Program.fromSource(source, 0, 0));
        try {
            Mark origin = s.mark();
            int bestPad = 0, bestRow = -1;
            for (int pass = 0; pass < 2; pass++) {
                int lo = pass == 0 ? 0 : Math.max(0, bestPad - 4), hi = pass == 0 ? 300 : bestPad + 4;
                int step = pass == 0 ? 3 : 1;
                for (int pad = lo; pad <= hi; pad += step) {
                    Program p = Program.fromSource(source, pad, 0);
                    s.restore(origin, p.masks());
                    Outcome o = run(s, p, source, Math.min(p.length(), pad + (pass == 0 ? 900 : 4000)), null,
                            new State[p.length()], true);
                    int good = o.divergeRow() >= 0 ? o.divergeRow() : o.row();
                    if (good > bestRow) { bestRow = good; bestPad = pad; }
                }
            }
            log.append(String.format("  title pad %d rows (in sync to row %d)%n", bestPad, bestRow));
            return bestPad;
        } finally {
            s.close();
        }
    }

    // ------------------------------------------------------------------ build

    record Built(Program program, int validated, int bossRow, List<Integer> stands, String endKind, int repairs) { }

    Built build(Target target, StringBuilder log) throws IOException {
        Source source = load(target);
        log.append(String.format("%s %s: %d source rows, recorded first hurt at source row %d%n",
                target.code(), target.name(), source.length(), source.recordedFirstHurt));
        int pad = alignPad(target, source, log);
        Program program = Program.fromSource(source, pad, 0);
        Set<String> tried = new HashSet<>();
        int repairs = 0;
        Outcome last;
        State[] states;
        while (true) {
            Session s = new Session(target, program);
            ArrayDeque<Mark> ring = new ArrayDeque<>();
            states = new State[program.length()];
            try {
                last = run(s, program, source, program.length(), ring, states, true);
                log.append(String.format("  straight run: %s at row %d (diverged %d)%n", last.kind(), last.row(), last.divergeRow()));
                if (last.success() || repairs >= MAX_REPAIRS) break;
                if (last.kind().equals("exhausted")) break;
                Program repaired = repair(s, program, source, last, ring, log, tried);
                if (repaired == null) break;
                program = repaired;
                repairs++;
            } finally {
                s.close();
            }
        }
        // Validated end: the boss arena / act end, or the last clean row before the failure.
        int end = last.success() ? last.row() : Math.max(0, last.row() - 1);
        Choice choice = chooseStands(target, program, source, states, end, last, log);
        return new Built(program, choice.bossRow(), choice.bossRow(), choice.stands(), last.kind(), repairs);
    }

    record Choice(int bossRow, List<Integer> stands) { }

    /** A row Sonic can be held on: grounded, flat or slow, not riding, not hurt, controllable. */
    static boolean standable(State st) {
        return st != null && !st.air() && !st.onObject() && !st.hurt() && !st.dead() && !st.controlled()
                && (st.angle() == 0 || Math.abs(st.gs()) <= 0x100) && Math.abs(st.gs()) <= 0x600;
    }

    Choice chooseStands(Target target, Program program, Source source, State[] states, int end, Outcome last,
                        StringBuilder log) throws IOException {
        // Boss stand: the latest standable row at or before the end that survives a held soak.
        int bossRow = -1;
        for (int r = end; r > Math.max(STAND_SPACING, end - 1200) && bossRow < 0; r -= 10) {
            int c = nearestStandable(states, r, 10);
            if (c < 0) continue;
            if (chain(target, program, source, states, List.of(), c, log, false)) bossRow = c;
        }
        if (bossRow < 0) {
            bossRow = end;
            log.append("  boss stand: no soak-safe row found; boss row at the validated end " + end + "\n");
        } else {
            log.append(String.format("  boss stand at row %d (end %s at %d)%n", bossRow, last.kind(), end));
        }
        int count = Math.max(4, Math.min(7, bossRow / 1100));
        List<Integer> stands = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            int ideal = (int) ((long) bossRow * i / (count + 1));
            int lowest = stands.isEmpty() ? 300 : stands.getLast() + STAND_SPACING;
            int chosen = -1;
            for (int delta = 0; delta <= 400 && chosen < 0; delta += 10) {
                for (int sign : new int[] {1, -1}) {
                    int r = ideal + sign * delta;
                    if (r < lowest || r > bossRow - STAND_SPACING / 2) continue;
                    if (!standable(states[r])) continue;
                    List<Integer> trial = new ArrayList<>(stands);
                    trial.add(r);
                    if (chain(target, program, source, states, trial, bossRow, log, false)) {
                        chosen = r;
                        break;
                    }
                }
            }
            if (chosen >= 0) stands.add(chosen);
        }
        boolean ok = chain(target, program, source, states, stands, bossRow, log, true);
        log.append(String.format("  stands %s; full chain %s%n", stands, ok ? "EXACT" : "FAILED"));
        return new Choice(bossRow, stands);
    }

    private static int nearestStandable(State[] states, int row, int radius) {
        for (int d = 0; d <= radius; d++) {
            if (row - d >= 0 && row - d < states.length && standable(states[row - d])) return row - d;
        }
        return -1;
    }

    /**
     * Course's stand chain: run from a fresh load; at each stand capture, hold for the soak plus the
     * clear rows, restore and resume; then hold at the boss row for the soak. The run must reproduce
     * the straight run's P1 state on every row and take no hurt, death or time-over.
     */
    boolean chain(Target target, Program program, Source source, State[] straight, List<Integer> stands, int bossRow,
                  StringBuilder log, boolean verbose) throws IOException {
        Session s = new Session(target, program);
        try {
            int next = 0;
            while (s.driver.row < bossRow) {
                if (next < stands.size() && s.driver.row == stands.get(next)) {
                    int at = s.driver.row;
                    Mark mark = s.mark();
                    if (!soak(s, SOAK_ROWS + STAND_CLEAR_ROWS)) {
                        if (verbose) log.append("    stand " + at + ": soak failed\n");
                        return false;
                    }
                    s.restore(mark, program.masks());
                    next++;
                    continue;
                }
                int before = s.driver.row;
                s.step();
                if (s.driver.row == before) continue;
                State st = State.observe();
                State ref = straight[before];
                if (ref == null || !st.samePlace(ref) || st.hurt() || st.dead()) {
                    if (verbose) log.append("    chain diverged at row " + before + " (" + st + " vs " + ref + ")\n");
                    return false;
                }
            }
            boolean safe = soak(s, SOAK_ROWS);
            if (!safe && verbose) log.append("    boss soak failed at row " + bossRow + "\n");
            return safe;
        } finally {
            s.close();
        }
    }

    /** Holds Sonic for {@code rows} rows as a stand does; false on hurt, death or time-over. */
    private static boolean soak(Session s, int rows) {
        s.driver.holding = true;
        try {
            for (int i = 0; i < rows; i++) {
                s.step();
                var p = (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
                if (p.isHurt() || p.getDead() || GameServices.level().getLevelGamestate().isTimeOver()) return false;
            }
            return true;
        } finally {
            s.driver.holding = false;
        }
    }

    // ------------------------------------------------------------------ output

    static String rle(int[] masks, int rows) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < rows) {
            int j = i;
            while (j < rows && masks[j] == masks[i]) j++;
            if (b.length() > 0) b.append(',');
            b.append(j - i).append(':').append(Integer.toHexString(masks[i]).toUpperCase());
            i = j;
        }
        return b.toString();
    }

    static String romCrc() throws IOException {
        var rom = GameServices.rom().getRom();
        var crc = new java.util.zip.CRC32();
        for (long at = 0, size = rom.getSize(); at < size; at += 65536) {
            crc.update(rom.readBytes(at, (int) Math.min(65536, size - at)));
        }
        return String.format("%08X", crc.getValue());
    }

    void write(Target target, Built built, StringBuilder log) throws IOException {
        Files.createDirectories(OUT);
        String text = String.join("\n",
                "# " + target.code() + " " + target.name() + ": built by TypingHedgehogRailBuilderProbe from "
                        + target.source() + " (" + built.repairs() + " repairs, ended " + built.endKind() + ")",
                "game " + target.code(),
                "zone " + target.zone(),
                "act " + target.act(),
                "team " + target.team(),
                "validated " + built.validated(),
                "engine " + com.openggf.version.AppVersion.get(),
                "rom " + romCrc(),
                "program " + rle(built.program().masks(), built.validated()));
        StringBuilder rail = new StringBuilder(text).append('\n');
        for (int stand : built.stands()) rail.append("stand ").append(stand).append('\n');
        rail.append("boss ").append(built.bossRow()).append('\n');
        Files.writeString(OUT.resolve(target.file()), rail.toString(), StandardCharsets.UTF_8);
        // The mod reads rails/index.txt: every rail in the directory, in name order.
        try (var files = Files.list(OUT)) {
            List<String> names = files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".rail"))
                    .sorted().toList();
            Files.writeString(OUT.resolve("index.txt"), String.join("\n", names) + "\n", StandardCharsets.UTF_8);
        }
        log.append(String.format("RESULT %s %s validated=%d (%.1fs) boss=%d stands=%s repairs=%d end=%s%n",
                target.code(), target.name(), built.validated(), built.validated() / 60.0, built.bossRow(),
                built.stands(), built.repairs(), built.endKind()));
    }

    // ------------------------------------------------------------------ drivers

    @Test @RequiresRom(SonicGame.SONIC_1) void s1() throws IOException { drive(SonicGame.SONIC_1); }
    @Test @RequiresRom(SonicGame.SONIC_2) void s2() throws IOException { drive(SonicGame.SONIC_2); }
    @Test @RequiresRom(SonicGame.SONIC_3K) void s3k() throws IOException { drive(SonicGame.SONIC_3K); }

    private void drive(SonicGame game) throws IOException {
        Assumptions.assumeTrue(SPEC != null, "opt-in: -Dtypinghog.railbuild=<targets>");
        List<String> wanted = new ArrayList<>();
        for (String part : SPEC.split(",")) if (!part.isBlank()) wanted.add(part.strip());
        Files.createDirectories(LOG_DIR);
        for (Target target : targets(game)) {
            String key = target.code() + ":" + target.name();
            if (!wanted.contains(target.code()) && !wanted.contains(key)) continue;
            StringBuilder log = new StringBuilder();
            long started = System.currentTimeMillis();
            try {
                Built built = build(target, log);
                write(target, built, log);
            } catch (Throwable t) {
                log.append("FAILED ").append(key).append(": ").append(brief(t)).append('\n');
            }
            log.append(String.format("  (%.0f s)%n", (System.currentTimeMillis() - started) / 1000.0));
            System.out.print(log.toString().lines().map(l -> "RAILBUILD|" + l + "\n").reduce("", String::concat));
            Files.writeString(LOG_DIR.resolve(target.code() + ".log"), log.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            reset();
        }
    }

    private static String brief(Throwable t) {
        String message = t.getClass().getSimpleName() + ": " + t.getMessage();
        StackTraceElement[] stack = t.getStackTrace();
        for (int i = 0; i < Math.min(3, stack.length); i++) {
            message += " @" + stack[i].getClassName().replaceAll(".*\\.", "") + ":" + stack[i].getLineNumber();
        }
        return message.replace('\n', ' ');
    }
}
