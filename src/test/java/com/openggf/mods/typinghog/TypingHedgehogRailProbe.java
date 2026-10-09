package com.openggf.mods.typinghog;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.mode.CourseCheckpoint;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.CourseControlProbeAccess;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Typing of the Hedgehog rail feasibility probe: kill tests 1 and 2 of the "Rails" section of
 * {@code docs/architecture/designs/2026-10-09-typing-of-the-hedgehog.md}.
 *
 * <p><b>Purpose.</b> Measures how far committed pad programs carry Sonic through stock acts
 * under the mod's configuration (16:9 / 400 px viewport, forced FAST load-time simulation,
 * cross-game donation off, Sonic solo or Sonic+Tails), with and without stock badniks, and
 * whether a course checkpoint captured at a "stand" restores exactly after a neutral soak.
 * It reports numbers; it asserts nothing about them.
 *
 * <p><b>Inputs.</b> Pad programs only: the P1 masks of committed BK2 movies, sliced per act with
 * the fixture's {@code zone_act_state} events and pre-level prefix
 * ({@link TraceReplayBootstrap#preLevelFrameCountForTraceReplay}), and the engine-authored cold
 * routes under {@code src/test/resources/routes/s3k/}. Trace rows are read offline only to slice
 * and align input and to mark lag rows (a row whose {@code gameplayFrameCounter} equals the
 * previous row's was not polled by the ROM); no physics or aux row reaches gameplay. Program row
 * {@code r} plays on engine frame {@code r - preLevel}; in S3K the first player-driven row is
 * held until {@code Camera.isLevelStarted()}. Cold routes prime movie frame 0 and step from frame
 * 1 after the initial Process_Sprites pass, as their own route tests do.
 *
 * <p><b>Badnik identification</b> (generic, no per-game id tables): every decoded layout spawn
 * of the act is constructed once in a throwaway session through the act's own object registry
 * under probe construction; a spawn is a badnik when the instance is an
 * {@link AbstractBadnikInstance} or its class lives in a game's {@code objects.badniks} package
 * (the S1 Bomb/Ball Hog and S3K Fireworm/Iwamodoki/Toxomister/Tunnelbot do not extend the base).
 * The measured session then drops those spawns with {@link ObjectManager#resyncSpawnList} before
 * its first row. Monitors and everything else stay.
 *
 * <p><b>Run.</b> Opt-in and excluded from the ordinary suite ({@code *Probe*}):
 * <pre>
 * python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TypingHedgehogRailProbe#s1*" \
 *     -Dtypinghog.rails=true -Dsonic1.rom.path=... test
 * </pre>
 * Optional: {@code -Dtypinghog.rails.only=ghz3,mz3} (source id prefixes),
 * {@code -Dtypinghog.rails.phases=runs,controls,badniks,stands},
 * {@code -Dtypinghog.rails.out=target/typinghog-rails}. Results are TSV lines in that directory
 * (one file per game) and on stdout with the {@code RAIL|} prefix.
 *
 * <p><b>Origin.</b> Typing of the Hedgehog rails lane, branch
 * {@code feature/ai-typing-hedgehog-rails}, 2026-10-09.
 */
class TypingHedgehogRailProbe {

    private static final boolean ENABLED = Boolean.getBoolean("typinghog.rails");
    private static final List<String> ONLY = csv(System.getProperty("typinghog.rails.only", ""));
    private static final List<String> PHASES =
            csv(System.getProperty("typinghog.rails.phases", "runs,controls,badniks,stands"));
    private static final Path OUT = Path.of(System.getProperty("typinghog.rails.out", "target/typinghog-rails"));
    private static final Path TRACES = Path.of("src/test/resources/traces");
    private static final Path ROUTES = Path.of("src/test/resources/routes/s3k");

    private static final int MOD_WIDTH = 400;
    private static final int NATIVE_WIDTH = 320;
    /** "Stuck": no new 32 px cell visited for this many frames outside a boss/locked arena. */
    private static final int STUCK_FRAMES = 600;
    /** Neutral frames allowed after a program ends for a pending act end to arrive. */
    private static final int SLACK_FRAMES = 600;
    private static final int INTRO_HOLD_LIMIT = 6000;
    private static final int SOAK_FRAMES = 600;
    private static final int LEVEL_MODE = 0x0C;

    @AfterEach
    void reset() {
        GameServices.configuration().clearSessionOverrides();
        SessionManager.clear();
    }

    // ------------------------------------------------------------------ sources

    enum Kind { TRACE, COLD }

    /**
     * One input source. TRACE: a fixture directory, sliced per act; {@code zone/act} are the
     * engine level indexes of the first sliced act (S1/S2) or -1 to take them from the
     * fixture's {@code zone_act_state} events (S3K, both acts). COLD: an authored S3K route
     * starting at {@code zone/act} with {@code sidekick} as authored.
     */
    record Source(String id, SonicGame game, Kind kind, Path path, int zone, int act,
                  String sidekick, boolean emeralds) { }

    private static List<Source> s1Sources() {
        return List.of(
                trace("ghz3", SonicGame.SONIC_1, "s1/ghz3_completerun", 0, 2),
                trace("mz3", SonicGame.SONIC_1, "s1/mz3_completerun", 1, 2),
                trace("syz3", SonicGame.SONIC_1, "s1/syz3_completerun", 2, 2),
                trace("lz3", SonicGame.SONIC_1, "s1/lz3_completerun", 3, 2),
                trace("slz3", SonicGame.SONIC_1, "s1/slz3_completerun", 4, 2),
                trace("sbz2", SonicGame.SONIC_1, "s1/sbz2_completerun", 5, 1),
                trace("fz", SonicGame.SONIC_1, "s1/fz_completerun", 6, 0));
    }

    private static List<Source> s2Sources() {
        return List.of(
                trace("ehz2", SonicGame.SONIC_2, "s2/runs/s2-sonic-tails-complete-emeralds/seg5_ehz2", 0, 1),
                trace("cpz2", SonicGame.SONIC_2, "s2/cpz2", 1, 1),
                trace("arz2", SonicGame.SONIC_2, "s2/arz2", 2, 1),
                trace("cnz2", SonicGame.SONIC_2, "s2/cnz2", 3, 1),
                trace("htz2", SonicGame.SONIC_2, "s2/htz2", 4, 1),
                trace("mcz2", SonicGame.SONIC_2, "s2/mcz2", 5, 1),
                trace("ooz2", SonicGame.SONIC_2, "s2/ooz2", 6, 1),
                trace("mtz3", SonicGame.SONIC_2, "s2/mtz3", 7, 2),
                trace("scz", SonicGame.SONIC_2, "s2/scz", 8, 0),
                trace("wfz", SonicGame.SONIC_2, "s2/wfz", 9, 0),
                trace("dez", SonicGame.SONIC_2, "s2/dez_ending", 10, 0),
                trace("dez-seg28", SonicGame.SONIC_2, "s2/runs/s2-sonic-tails-complete-emeralds/seg28_dez1", 10, 0));
    }

    private static List<Source> s3kSources() {
        List<Source> sources = new ArrayList<>();
        for (String[] t : new String[][] {
                {"aiz", "s3k/aiz_completerun"}, {"hcz", "s3k/hcz_completerun"},
                {"mgz", "s3k/mgz_completerun"}, {"cnz", "s3k/cnz_completerun"},
                {"fbz", "s3k/fbz_completerun"}, {"icz", "s3k/icz_completerun"},
                {"lbz", "s3k/lbz_completerun"}, {"mhz", "s3k/mhz_completerun"},
                {"soz", "s3k/soz_completerun"}, {"lrz", "s3k/lrz_completerun"},
                // Fixture directory names are shifted against their ROM zone ids:
                // hpz22 = HPZ ($16), "hpz" = SSZ ($0A), "ssz" = DEZ ($0B), "dez" = DEZ boss arena ($17).
                {"hpz", "s3k/hpz22_completerun"}, {"ssz", "s3k/hpz_completerun"},
                {"dez", "s3k/ssz_completerun"}, {"dezboss", "s3k/dez_completerun"},
                {"ddz", "s3k/runs/s3k-sonic-tails-complete-emeralds/zone0c"}}) {
            sources.add(new Source(t[0], SonicGame.SONIC_3K, Kind.TRACE, TRACES.resolve(t[1]), -1, -1,
                    "tails", t[0].equals("ddz")));
        }
        for (Object[] c : new Object[][] {
                {"mhz1-sonic-fresh-320", 7, 0, ""}, {"mhz1-team-cold-complete-320", 7, 0, "tails"},
                {"mhz2-sonic-incoming-320", 7, 0, ""}, {"mhz2-team-incoming-320", 7, 0, "tails"},
                {"ddz-super-fresh-320", 12, 0, ""},
                {"dez1-sonic-solo-cold-complete-320", 11, 0, ""},
                {"dez1-sonic-tails-cold-complete-320", 11, 0, "tails"},
                {"dez-sonic-solo-cold-ending-320", 11, 0, ""},
                {"dez-sonic-tails-cold-ending-320", 11, 0, "tails"},
                {"dez2-sonic-solo-incoming-clear-320", 11, 0, ""},
                {"lrz-boss-sonic-tails-cold-hpz-320", 9, 0, "tails"},
                {"lrz2-sonic-tails-cold-boss-act-arrival-320", 9, 0, "tails"},
                {"soz1-cold-sonic", 8, 0, ""}, {"soz1-cold-sonic-tails", 8, 0, "tails"},
                {"soz2-cold-sonic", 8, 1, ""}, {"soz2-cold-sonic-tails", 8, 1, "tails"},
                {"ssz1-sonic-solo-cold-complete-320", 10, 0, ""},
                {"ssz1-sonic-tails-cold-complete-320", 10, 0, "tails"}}) {
            String name = (String) c[0];
            sources.add(new Source("cold:" + name, SonicGame.SONIC_3K, Kind.COLD,
                    ROUTES.resolve(name + ".bk2"), (int) c[1], (int) c[2], (String) c[3],
                    name.startsWith("ddz")));
        }
        return sources;
    }

    private static Source trace(String id, SonicGame game, String dir, int zone, int act) {
        return new Source(id, game, Kind.TRACE, TRACES.resolve(dir), zone, act,
                game == SonicGame.SONIC_1 ? "" : "tails", false);
    }

    // ------------------------------------------------------------------ programs

    /**
     * A pad program for one cold act start. {@code masks[i]} is the P1 mask of source row
     * {@code firstRow + i}; {@code lag[i]} marks rows the ROM did not poll.
     */
    record Program(String id, Source source, int zone, int act, int firstRow, int[] masks,
                   boolean[] lag, int lagRows, int[] recX, int[] recY, int recFirstHurt, String note) {
        int length() { return masks.length; }
    }

    private static List<Program> programs(Source source) throws IOException {
        if (source.kind() == Kind.COLD) {
            Bk2Movie movie = new Bk2MovieLoader().loadMovieOrInputLog(source.path());
            int n = movie.getFrameCount() - 1;
            int[] masks = new int[n];
            for (int i = 0; i < n; i++) masks[i] = movie.getFrame(i + 1).p1InputMask();
            return List.of(new Program(source.id(), source, source.zone(), source.act(), 1, masks,
                    new boolean[n], 0, null, null, -1, "authored cold route, frames 1.." + n));
        }
        TraceData trace = TraceData.load(source.path());
        TraceMetadata meta = trace.metadata();
        Bk2Movie movie = new Bk2MovieLoader().load(resolveBk2(source.path(), meta));
        int offset = meta.bk2FrameOffset();
        int preLevel = TraceReplayBootstrap.preLevelFrameCountForTraceReplay(trace);
        List<int[]> slices = levelSlices(trace); // {zone, act, startRow, endRowExclusive}
        List<Program> programs = new ArrayList<>();
        if (source.game() != SonicGame.SONIC_3K) {
            int end = trace.frameCount();
            for (int[] s : slices) {
                if (s[2] <= preLevel && preLevel < s[3]) { end = s[3]; break; }
                if (s[2] > preLevel) { end = Math.min(end, s[2]); break; }
            }
            programs.add(program(source.id(), source, source.zone(), source.act(), trace, movie, offset,
                    preLevel, end, "rows " + preLevel + ".." + end + " slices=" + describe(slices)));
            return programs;
        }
        // S3K: the first level slice is act 1 from the pre-level prefix; the act-1 program runs
        // on through the recorded act 2 (warm continuation is reported separately); each later
        // slice of the same zone is also measured as a cold start of its own act.
        int[] first = null;
        for (int[] s : slices) if (s[3] > preLevel) { first = s; break; }
        if (first == null) throw new IllegalStateException(source.id() + ": no level slice");
        int zoneEnd = first[3];
        List<int[]> sameZone = new ArrayList<>();
        for (int[] s : slices) {
            if (s[2] < first[2]) continue;
            if (s[0] != first[0]) break;
            sameZone.add(s);
            zoneEnd = s[3];
        }
        programs.add(program(source.id() + "-act" + (first[1] + 1), source, first[0], first[1], trace, movie,
                offset, preLevel, zoneEnd, "rows " + preLevel + ".." + zoneEnd + " slices=" + describe(slices)));
        for (int[] s : sameZone) {
            if (s == first || s[1] == first[1]) continue;
            programs.add(program(source.id() + "-act" + (s[1] + 1), source, s[0], s[1], trace, movie,
                    offset, s[2], s[3], "cold act " + (s[1] + 1) + " from recorded act change row " + s[2]));
            break;
        }
        return programs;
    }

    private static Program program(String id, Source source, int zone, int act, TraceData trace, Bk2Movie movie,
                                   int offset, int from, int to, String note) {
        int n = Math.max(0, Math.min(to, trace.frameCount()) - from);
        int[] masks = new int[n];
        boolean[] lag = new boolean[n];
        // Comparison-only: the recorded P1 centre per row (to date the first divergence) and the
        // first recorded hurt routine (to tell a recorded hit from an engine-only one).
        int[] recX = new int[n];
        int[] recY = new int[n];
        int recFirstHurt = -1;
        int lagRows = 0;
        for (int i = 0; i < n; i++) {
            int row = from + i;
            var frame = trace.getFrame(row);
            recX[i] = frame.x() & 0xFFFF;
            recY[i] = frame.y() & 0xFFFF;
            if (recFirstHurt < 0 && frame.routine() == 4) recFirstHurt = i;
            int movieFrame = offset + row;
            masks[i] = movieFrame < movie.getFrameCount() ? movie.getFrame(movieFrame).p1InputMask() : 0;
            if (row > 0 && trace.getFrame(row).gameplayFrameCounter()
                    == trace.getFrame(row - 1).gameplayFrameCounter()) {
                lag[i] = true;
                lagRows++;
            }
        }
        return new Program(id, source, zone, act, from, masks, lag, lagRows, recX, recY, recFirstHurt,
                note + " recFirstHurt=" + recFirstHurt);
    }

    /** Contiguous Level-mode ($0C) row ranges per recorded (zone, act), from zone_act_state events. */
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

    private static String describe(List<int[]> slices) {
        StringBuilder b = new StringBuilder();
        for (int[] s : slices) {
            if (b.length() > 0) b.append(';');
            b.append(String.format("z%d/a%d:%d-%d", s[0], s[1], s[2], s[3]));
        }
        return b.toString();
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

    // ------------------------------------------------------------------ sessions

    /** A configured headless session stepping one program; owns the cursor and intro hold. */
    private static final class Rail {
        final Program program;
        final boolean dropLag;
        final HeadlessTestFixture fixture;
        final boolean s3k;
        int cursor;
        int engineFrames;
        int heldFrames;
        int droppedLagRows;
        int lastMask;
        boolean inputSeen;

        Rail(Program program, String sidekick, boolean dropLag, int width, Set<Integer> removeLayout) {
            this.program = program;
            this.dropLag = dropLag;
            this.s3k = program.source().game() == SonicGame.SONIC_3K;
            configure(sidekick, width);
            fixture = HeadlessTestFixture.builder().withZoneAndAct(program.zone(), program.act())
                    .withFreshLevelStartLifecycle().build();
            if (fixture.camera().getWidth() != width) {
                throw new IllegalStateException("viewport " + fixture.camera().getWidth() + " != " + width);
            }
            var mode = SessionManager.getCurrentGameplayMode().getWorldSession().loadTimeSimulationMode();
            if (mode != com.openggf.game.timing.LoadTimeSimulationMode.FAST) {
                throw new IllegalStateException("load-time simulation " + mode);
            }
            if (program.source().emeralds()) {
                GameServices.gameState().restoreS3kEmeraldProgress(Collections.nCopies(7, 1), false);
            }
            if (removeLayout != null && !removeLayout.isEmpty()) {
                List<ObjectSpawn> kept = new ArrayList<>();
                for (ObjectSpawn spawn : GameServices.level().getCurrentLevel().getObjects()) {
                    if (!removeLayout.contains(spawn.layoutIndex())) kept.add(spawn);
                }
                GameServices.level().getObjectManager().resyncSpawnList(kept);
            }
            if (program.source().kind() == Kind.COLD) {
                GameServices.level().consumePendingInitialProcessSpritesPass();
                prime(0);
            }
        }

        AbstractPlayableSprite player() { return fixture.sprite(); }

        boolean exhausted() {
            skipLag();
            return cursor >= program.length();
        }

        private void skipLag() {
            while (dropLag && cursor < program.length() && program.lag()[cursor]) {
                cursor++;
                droppedLagRows++;
            }
        }

        /** Steps one engine frame of the program (or an intro hold); false if exhausted. */
        boolean step() {
            skipLag();
            if (cursor >= program.length()) return false;
            int mask = program.masks()[cursor];
            if (s3k && !inputSeen && mask != 0 && !GameServices.camera().isLevelStarted()) {
                stepMask(0);
                heldFrames++;
                if (heldFrames > INTRO_HOLD_LIMIT) throw new IllegalStateException("level never started");
                return true;
            }
            if (mask != 0) inputSeen = true;
            stepMask(mask);
            cursor++;
            return true;
        }

        void stepMask(int mask) {
            fixture.runner().stepFrame(
                    (mask & AbstractPlayableSprite.INPUT_UP) != 0,
                    (mask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                    (mask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                    (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                    (mask & AbstractPlayableSprite.INPUT_JUMP) != 0);
            lastMask = mask;
            engineFrames++;
        }

        /** Re-seeds the driver's held-button history (outside the course partition). */
        void prime(int mask) {
            fixture.runner().primeInputState(new Bk2FrameInput(0, mask & 0x0F,
                    (mask & AbstractPlayableSprite.INPUT_JUMP) != 0 ? InputActionMasks.ACTION_A : 0, false, ""));
            lastMask = mask;
        }
    }

    private static void configure(String sidekick, int width) {
        var config = GameServices.configuration();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == NATIVE_WIDTH ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.WIDE_16_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        // The mod forces the default explicitly rather than inheriting a user setting.
        config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "FAST");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick);
        // Recorded runs and authored routes both start with the native intros (AIZ1, LBZ1, SOZ1).
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    // ------------------------------------------------------------------ kill test 1

    record RunResult(String program, String variant, String team, int width, boolean badniksRemoved,
                     int programRows, int lagRows, int validatedRows, int validatedFrames, String reason,
                     int terminalRow, int firstHurtRow, int bossRow, int camLockRow, int actEndRow,
                     int actChangeRow, int warmRows, String warmReason, int maxX, int endX, int endY,
                     int engineFrames, int heldFrames, int removedBadniks, String note) {
        static String header() {
            return "program\tvariant\tteam\twidth\tbadniks\tprogramRows\tlagRows\tvalidatedRows\tvalidatedFrames\tseconds"
                    + "\treason\tterminalRow\tfirstHurtRow\tbossRow\tcamLockRow\tactEndRow\tactChangeRow\twarmRows"
                    + "\twarmReason\tmaxX\tendX\tendY\tengineFrames\theldFrames\tremovedBadniks\tnote";
        }

        String tsv() {
            return String.join("\t", program, variant, team.isEmpty() ? "sonic" : "sonic+" + team,
                    Integer.toString(width), badniksRemoved ? "removed" : "stock",
                    Integer.toString(programRows), Integer.toString(lagRows), Integer.toString(validatedRows),
                    Integer.toString(validatedFrames), String.format("%.1f", validatedFrames / 60.0), reason,
                    Integer.toString(terminalRow), Integer.toString(firstHurtRow), Integer.toString(bossRow),
                    Integer.toString(camLockRow), Integer.toString(actEndRow), Integer.toString(actChangeRow),
                    Integer.toString(warmRows), warmReason, hex(maxX), hex(endX), hex(endY),
                    Integer.toString(engineFrames), Integer.toString(heldFrames), Integer.toString(removedBadniks),
                    note);
        }
    }

    /**
     * Steps a program until P1 dies, stops making progress, the program (plus slack) runs out, the
     * zone changes, the act ends, or an exception escapes. Hurt is recorded and ends validation but
     * not the run. A same-zone act change (S3K seamless reload) ends the validated cold act and the
     * run continues as a warm measurement of the next act.
     */
    private static RunResult measure(Program program, String team, boolean dropLag, int width,
                                     Set<Integer> removeLayout) {
        String variant = program.source().kind() == Kind.COLD ? "authored" : (dropLag ? "drop-lag" : "keep-lag");
        Rail rail;
        try {
            rail = new Rail(program, team, dropLag, width, removeLayout);
        } catch (Throwable t) {
            return new RunResult(program.id(), variant, team, width, removeLayout != null, program.length(),
                    program.lagRows(), 0, 0, "setup-exception:" + brief(t), 0, -1, -1, -1, -1, -1, 0, "", 0, 0, 0,
                    0, 0, removeLayout == null ? 0 : removeLayout.size(), program.note());
        }
        int startZone = program.zone();
        int act = program.act();
        Set<Long> cells = new HashSet<>();
        int lastNewCell = 0;
        int lastNewCellRow = 0;
        // Rows are program indexes (0 = the program's first row); frames are engine frames.
        int firstHurt = -1, boss = -1, camLock = -1, actEnd = -1, actChange = -1, warmHurt = -1;
        int firstHurtFrame = -1, actEndFrame = -1, actChangeFrame = -1;
        int maxX = 0;
        int slack = 0;
        int firstDiverge = -1;
        int stuckRow = -1, stuckFrame = -1;
        String reason = null;
        int terminalRow = -1;
        int terminalFrame = -1;
        boolean wasHurt = false;
        while (reason == null) {
            try {
                int before = rail.cursor;
                if (!rail.step()) {
                    if (slack >= SLACK_FRAMES) { reason = "exhausted"; break; }
                    rail.stepMask(0);
                    slack++;
                }
                // Index of the row just stepped: rows before it are clean when an event fires on it.
                int row = Math.max(0, rail.cursor - 1);
                AbstractPlayableSprite p = rail.player();
                if (firstDiverge < 0 && program.recX() != null && rail.cursor > before && row < program.length()) {
                    int dx = Math.abs((p.getCentreX() & 0xFFFF) - program.recX()[row]);
                    int dy = Math.abs((p.getCentreY() & 0xFFFF) - program.recY()[row]);
                    if (dx > 2 || dy > 2) firstDiverge = row;
                }
                var level = GameServices.level();
                var state = GameServices.gameState();
                var camera = GameServices.camera();
                int x = p.getCentreX() & 0xFFFF, y = p.getCentreY() & 0xFFFF;
                maxX = Math.max(maxX, x);
                if (level.getCurrentZone() != startZone) {
                    // A zone load ends the act; for S1/S2 it is the act end when no signal was seen.
                    if (actEnd < 0 && actChange < 0) { actEnd = row; actEndFrame = rail.engineFrames; }
                    reason = "zone-change"; break;
                }
                if (level.getCurrentAct() != act) {
                    if (actChange < 0 && program.source().game() == SonicGame.SONIC_3K) {
                        actChange = row;
                        actChangeFrame = rail.engineFrames;
                        act = level.getCurrentAct();
                        cells.clear();
                        lastNewCell = rail.engineFrames;
                        lastNewCellRow = row;
                    } else {
                        if (actEnd < 0 && actChange < 0) { actEnd = row; actEndFrame = rail.engineFrames; }
                        reason = "act-change"; break;
                    }
                }
                if (p.getDead()) { reason = "death"; break; }
                boolean hurt = p.isHurt();
                if (hurt && !wasHurt) {
                    if (actChange < 0 && actEnd < 0 && firstHurt < 0) { firstHurt = row; firstHurtFrame = rail.engineFrames; }
                    if (actChange >= 0 && warmHurt < 0) warmHurt = row;
                }
                wasHurt = hurt;
                boolean bossActive = state.isBossFightActive();
                if (bossActive && boss < 0 && actChange < 0) boss = row;
                boolean locked = camera.getMinX() == camera.getMaxX();
                if (locked && camLock < 0 && actChange < 0 && rail.engineFrames > 60) camLock = row;
                if ((state.isActCompletionSignalActive() || state.isEndOfLevelActive()) && actChange < 0 && actEnd < 0) {
                    actEnd = row;
                    actEndFrame = rail.engineFrames;
                    if (program.source().game() != SonicGame.SONIC_3K) { reason = "act-end"; break; }
                }
                if (cells.add(((long) (x >> 5) << 32) | (y >> 5)) || !rail.inputSeen) {
                    lastNewCell = rail.engineFrames;
                    lastNewCellRow = row;
                } else if (!bossActive && !locked && rail.engineFrames - lastNewCell >= STUCK_FRAMES) {
                    reason = "stuck";
                    stuckRow = lastNewCellRow;
                    stuckFrame = lastNewCell;
                    break;
                }
            } catch (Throwable t) {
                reason = "exception:" + brief(t);
                break;
            }
        }
        // A stuck run is dated from its last progress; any other terminal from the last stepped row.
        terminalRow = stuckRow >= 0 ? stuckRow : "exhausted".equals(reason) ? program.length()
                : Math.max(0, rail.cursor - 1);
        terminalFrame = stuckRow >= 0 ? stuckFrame : rail.engineFrames;
        // The cold act ends at its act change (S3K seamless reload) or its act-end signal; validation
        // also ends at the first hurt before that.
        int actRow = actChange >= 0 ? actChange : actEnd >= 0 ? actEnd : terminalRow;
        int actFrame = actChange >= 0 ? actChangeFrame : actEnd >= 0 ? actEndFrame : terminalFrame;
        boolean hurtFirst = firstHurt >= 0 && firstHurt <= actRow;
        int validatedRows = hurtFirst ? firstHurt : actRow;
        int validatedFrames = hurtFirst ? firstHurtFrame : actFrame;
        String outcome = hurtFirst ? "hurt" : actChange >= 0 ? "act-change" : actEnd >= 0 ? "act-end" : reason;
        int warmRows = 0;
        String warmReason = "";
        if (actChange >= 0) {
            boolean warmHurtFirst = warmHurt >= 0 && warmHurt <= terminalRow;
            warmRows = Math.max(0, (warmHurtFirst ? warmHurt : terminalRow) - actChange);
            warmReason = warmHurtFirst ? "hurt" : reason;
        }
        AbstractPlayableSprite p = rail.player();
        return new RunResult(program.id(), variant, team, width, removeLayout != null, program.length(),
                program.lagRows(), validatedRows, validatedFrames, outcome + "(" + reason + ")", terminalRow,
                firstHurt, boss, camLock, actEnd, actChange, warmRows, warmReason, maxX,
                p.getCentreX() & 0xFFFF, p.getCentreY() & 0xFFFF, rail.engineFrames, rail.heldFrames,
                removeLayout == null ? 0 : removeLayout.size(), "diverge@" + firstDiverge + " " + program.note());
    }

    // ------------------------------------------------------------------ badnik classification

    private static final Map<String, Set<Integer>> BADNIK_CACHE = new LinkedHashMap<>();

    /** Layout indexes of the act's badnik spawns, classified in a throwaway session. */
    private static Set<Integer> badnikLayout(Program program, StringBuilder report) {
        String key = program.source().game() + ":" + program.zone() + ":" + program.act();
        Set<Integer> cached = BADNIK_CACHE.get(key);
        if (cached != null) return cached;
        Set<Integer> badniks = new HashSet<>();
        Map<String, Integer> histogram = new TreeMap<>();
        int failures = 0;
        configure("", MOD_WIDTH);
        HeadlessTestFixture.builder().withZoneAndAct(program.zone(), program.act()).build();
        ObjectManager manager = GameServices.level().getObjectManager();
        ObjectRegistry registry = registryOf(manager);
        var services = manager.getObjectServices();
        for (ObjectSpawn spawn : new ArrayList<>(GameServices.level().getCurrentLevel().getObjects())) {
            try {
                ObjectInstance instance = ObjectConstructionContext.withProbeConstruction(() ->
                        ObjectConstructionContext.construct(services, () -> registry.create(spawn)));
                if (instance != null && isBadnik(instance)) {
                    badniks.add(spawn.layoutIndex());
                    histogram.merge(instance.getClass().getSimpleName(), 1, Integer::sum);
                }
            } catch (Throwable t) {
                failures++;
            }
        }
        report.append(key).append(" badniks=").append(badniks.size()).append(" ctorFailures=").append(failures)
                .append(' ').append(histogram);
        BADNIK_CACHE.put(key, badniks);
        SessionManager.clear();
        return badniks;
    }

    static boolean isBadnik(ObjectInstance instance) {
        return instance instanceof AbstractBadnikInstance
                || instance.getClass().getPackageName().endsWith(".objects.badniks");
    }

    private static ObjectRegistry registryOf(ObjectManager manager) {
        try {
            var field = ObjectManager.class.getDeclaredField("registry");
            field.setAccessible(true);
            return (ObjectRegistry) field.get(manager);
        } catch (ReflectiveOperationException e) {
            return GameServices.module().createObjectRegistry();
        }
    }

    // ------------------------------------------------------------------ kill test 2

    record Final(int x, int y, int xs, int ys, int gs, int rings, long rng, int levelFrame, int vint,
                 long timerFrames) {
        static Final of(Rail rail) {
            var p = rail.player();
            var level = GameServices.level();
            return new Final(p.getCentreX() & 0xFFFF, p.getCentreY() & 0xFFFF, p.getXSpeed(), p.getYSpeed(),
                    p.getGSpeed(), p.getRingCount(), GameServices.rng().getSeed(), level.getFrameCounter(),
                    level.getObjectManager().getFrameCounter(), level.getLevelGamestate().getTimerFrames());
        }
    }

    /**
     * Kill test 2 on one program/team: capture a course checkpoint at a stand row, play the
     * program to its validated end (reference), restore (the end-of-rail restore must not be
     * rejected), soak {@link #SOAK_FRAMES} neutral frames, restore again, replay to the same end
     * and compare.
     */
    private static String stand(Program program, String team, boolean dropLag, Set<Integer> removeLayout,
                                int validatedRows) {
        Rail rail;
        try {
            rail = new Rail(program, team, dropLag, MOD_WIDTH, removeLayout);
        } catch (Throwable t) {
            return "setup-exception:" + brief(t);
        }
        CourseControl course = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
        int endIndex = Math.min(program.length(), validatedRows);
        int earliest = Math.max(300, endIndex / 4);
        int standIndex = -1;
        try {
            while (rail.cursor < endIndex - 300) {
                if (!rail.step()) break;
                if (rail.cursor < earliest || !rail.inputSeen) continue;
                var p = rail.player();
                var ps = course.playerState();
                if (ps.floorSupport() && !p.getAir() && !p.isOnObject() && !p.isHurt() && !p.getDead()
                        && !p.isObjectControlled() && !p.isControlLocked() && (p.getAngle() & 0xFF) == 0
                        && Math.abs(p.getGSpeed()) <= 0x100 && course.presentationReady()
                        && !GameServices.gameState().isBossFightActive()) {
                    standIndex = rail.cursor;
                    break;
                }
            }
        } catch (Throwable t) {
            return "exception-before-stand:" + brief(t);
        }
        if (standIndex < 0) return "no-stand-row(validated=" + validatedRows + ")";
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        int standMask = rail.lastMask;
        int standEngine = rail.engineFrames;
        long layoutAtStand = registry.courseLayoutVersion();
        CourseCheckpoint checkpoint = course.capture();
        String standPos = hex(rail.player().getCentreX() & 0xFFFF) + "," + hex(rail.player().getCentreY() & 0xFFFF)
                + " gs=" + rail.player().getGSpeed() + " sidekicks=" + GameServices.sprites().getSidekicks().size();
        StringBuilder out = new StringBuilder();
        out.append("standRow=").append(standIndex).append(" endRow=").append(endIndex).append(" at=").append(standPos);
        // Reference: uninterrupted play to the validated end.
        try {
            while (rail.cursor < endIndex && rail.step()) { }
        } catch (Throwable t) {
            return out.append(" reference-exception:").append(brief(t)).toString();
        }
        Final reference = Final.of(rail);
        CompositeSnapshot referenceCourse = registry.captureCourse();
        long layoutAtEnd = registry.courseLayoutVersion();
        out.append(" layout(stand=").append(layoutAtStand).append(",end=").append(layoutAtEnd).append(')');
        // Restore from the validated end back to the stand.
        try {
            course.restore(checkpoint);
            out.append(" restoreFromEnd=ok");
        } catch (Throwable t) {
            return out.append(" restoreFromEnd=REJECTED:").append(brief(t)).toString();
        }
        // Stand soak: neutral native steps, as a held stand would run them.
        rail.prime(standMask);
        boolean soakHurt = false, soakDead = false, soakTimeOver = false;
        try {
            for (int i = 0; i < SOAK_FRAMES; i++) {
                rail.stepMask(0);
                var p = rail.player();
                soakHurt |= p.isHurt();
                soakDead |= p.getDead();
                soakTimeOver |= GameServices.level().getLevelGamestate().isTimeOver();
            }
        } catch (Throwable t) {
            out.append(" soak-exception:").append(brief(t));
        }
        out.append(" soak(hurt=").append(soakHurt).append(",dead=").append(soakDead)
                .append(",timeOver=").append(soakTimeOver).append(')');
        try {
            course.restore(checkpoint);
            out.append(" restoreAfterSoak=ok");
        } catch (Throwable t) {
            return out.append(" restoreAfterSoak=REJECTED:").append(brief(t)).toString();
        }
        rail.cursor = standIndex;
        rail.inputSeen = true;
        rail.engineFrames = standEngine;
        rail.prime(standMask);
        try {
            while (rail.cursor < endIndex && rail.step()) { }
        } catch (Throwable t) {
            return out.append(" replay-exception:").append(brief(t)).toString();
        }
        Final replay = Final.of(rail);
        CompositeSnapshot replayCourse = registry.captureCourse();
        out.append(" final=").append(reference.equals(replay) ? "EQUAL" : "DIFF ref=" + reference + " got=" + replay);
        List<String> differing = new ArrayList<>();
        String firstDiff = "";
        for (String key : referenceCourse.entries().keySet()) {
            List<String> d = RewindSnapshotDiff.diffKey(key, referenceCourse.get(key), replayCourse.get(key));
            if (!d.isEmpty()) {
                differing.add(key + "#" + d.size());
                StringBuilder detail = new StringBuilder();
                for (int i = 0; i < Math.min(3, d.size()); i++) detail.append(d.get(i)).append(" | ");
                firstDiff += key + ": " + (detail.length() > 400 ? detail.substring(0, 400) : detail) + " ;; ";
            }
        }
        out.append(" courseKeysDiffering=").append(differing.size()).append('/').append(referenceCourse.entries().size());
        if (!differing.isEmpty()) {
            out.append(' ').append(differing.subList(0, Math.min(6, differing.size())))
                    .append(" first={").append(firstDiff.length() > 2000 ? firstDiff.substring(0, 2000) : firstDiff)
                    .append('}');
        }
        out.append(" layoutAfterReplay=").append(registry.courseLayoutVersion());
        return out.toString();
    }

    // ------------------------------------------------------------------ drivers

    @Test
    @RequiresRom(SonicGame.SONIC_1)
    void s1Rails() throws IOException {
        drive("s1", s1Sources(), List.of(""));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void s2Rails() throws IOException {
        drive("s2", s2Sources(), List.of("tails", ""));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void s3kRails() throws IOException {
        drive("s3k", s3kSources(), List.of("tails", ""));
    }

    private void drive(String game, List<Source> sources, List<String> teams) throws IOException {
        Assumptions.assumeTrue(ENABLED, "opt-in: -Dtypinghog.rails=true");
        Files.createDirectories(OUT);
        Path file = OUT.resolve(game + ".tsv");
        Files.writeString(file, "");
        emit(file, "#runs\t" + RunResult.header());
        Map<String, RunResult> best = new LinkedHashMap<>();
        Map<String, Program> programById = new LinkedHashMap<>();
        Map<String, RunResult> badnikRuns = new LinkedHashMap<>();
        for (Source source : sources) {
            if (!selected(source.id())) continue;
            List<Program> programs;
            try {
                programs = programs(source);
            } catch (Throwable t) {
                emit(file, "#source-error\t" + source.id() + "\t" + brief(t));
                continue;
            }
            for (Program program : programs) {
                programById.put(program.id(), program);
                emit(file, "#program\t" + program.id() + "\tzone=" + program.zone() + "\tact=" + program.act()
                        + "\trows=" + program.length() + "\tlagRows=" + program.lagRows() + "\t" + program.note());
                if (source.kind() == Kind.COLD) {
                    if (phase("controls")) keepRun(file, best, program, measure(program, source.sidekick(), false,
                            NATIVE_WIDTH, null), false);
                    if (phase("runs")) keepRun(file, best, program, measure(program, source.sidekick(), false,
                            MOD_WIDTH, null), true);
                    continue;
                }
                if (phase("controls")) {
                    keepRun(file, best, program, measure(program, source.sidekick(), false, NATIVE_WIDTH, null), false);
                    keepRun(file, best, program, measure(program, source.sidekick(), true, NATIVE_WIDTH, null), false);
                }
                if (phase("runs")) {
                    for (String team : teams) {
                        for (boolean drop : new boolean[] {false, true}) {
                            keepRun(file, best, program, measure(program, team, drop, MOD_WIDTH, null), true);
                        }
                    }
                }
            }
        }
        if (phase("badniks")) {
            for (var entry : best.entrySet()) {
                Program program = programById.get(entry.getKey());
                RunResult b = entry.getValue();
                StringBuilder report = new StringBuilder();
                Set<Integer> remove;
                try {
                    remove = badnikLayout(program, report);
                } catch (Throwable t) {
                    emit(file, "#badnik-classify-error\t" + program.id() + "\t" + brief(t));
                    continue;
                }
                emit(file, "#badniks\t" + program.id() + "\t" + report);
                RunResult r = measure(program, b.team(), b.variant().equals("drop-lag"), MOD_WIDTH, remove);
                badnikRuns.put(program.id(), r);
                emit(file, "badnik-run\t" + r.tsv());
            }
        }
        if (phase("stands")) {
            // Two acts per game (distinct zones) with the longest validated mod-config rails; every
            // team the game offers (a cold route keeps its authored team); stock badniks removed as
            // the mod runs them. Each team's stand window ends at its own validated end.
            List<Map.Entry<String, RunResult>> ranked = new ArrayList<>(best.entrySet());
            ranked.sort((a, b) -> Integer.compare(b.getValue().validatedRows(), a.getValue().validatedRows()));
            Set<Integer> zonesDone = new HashSet<>();
            for (var entry : ranked) {
                if (zonesDone.size() >= 2) break;
                Program program = programById.get(entry.getKey());
                RunResult b = entry.getValue();
                if (b.validatedRows() < 900 || zonesDone.contains(program.zone())) continue;
                zonesDone.add(program.zone());
                boolean drop = b.variant().equals("drop-lag");
                Set<Integer> remove;
                try {
                    remove = badnikLayout(program, new StringBuilder());
                } catch (Throwable t) {
                    remove = null;
                }
                List<String> standTeams = program.source().kind() == Kind.COLD
                        ? List.of(program.source().sidekick()) : teams;
                for (String team : standTeams) {
                    RunResult window = measure(program, team, drop, MOD_WIDTH, remove);
                    emit(file, "stand-window\t" + window.tsv());
                    String result = window.validatedRows() < 900
                            ? "skipped(validated=" + window.validatedRows() + ")"
                            : stand(program, team, drop, remove, window.validatedRows());
                    emit(file, "stand\t" + program.id() + "\t" + (team.isEmpty() ? "sonic" : "sonic+" + team)
                            + "\t" + b.variant() + "\tbadniks=" + (remove == null ? "stock" : "removed")
                            + "\t" + result);
                }
            }
        }
    }

    /** Tracks, per program, the best 400-wide run (the mod's configuration). */
    private static void keepRun(Path file, Map<String, RunResult> best, Program program, RunResult r, boolean mod)
            throws IOException {
        emit(file, (mod ? "run\t" : "control\t") + r.tsv());
        if (!mod) return;
        RunResult current = best.get(program.id());
        if (current == null || r.validatedRows() > current.validatedRows()) best.put(program.id(), r);
    }

    private static boolean selected(String id) {
        if (ONLY.isEmpty()) return true;
        for (String prefix : ONLY) if (id.startsWith(prefix)) return true;
        return false;
    }

    private static boolean phase(String name) { return PHASES.contains(name); }

    private static void emit(Path file, String line) throws IOException {
        System.out.println("RAIL|" + line);
        Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static List<String> csv(String value) {
        List<String> list = new ArrayList<>();
        for (String part : value.split(",")) if (!part.isBlank()) list.add(part.trim());
        return list;
    }

    private static String brief(Throwable t) {
        String message = t.getClass().getSimpleName() + ": " + t.getMessage();
        StackTraceElement[] stack = t.getStackTrace();
        if (stack.length > 0) message += " @" + stack[0].getClassName().replaceAll(".*\\.", "") + ":" + stack[0].getLineNumber();
        message = message.replace('\t', ' ').replace('\n', ' ');
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private static String hex(int value) { return "$" + Integer.toHexString(value).toUpperCase(); }
}
