package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdActEndObjects;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The end of every full-act native route ({@code native/rehost-long}): each route is replayed from its stage start with
 * its own movie only (the fixture replay; trace rows are comparison data, AGENTS.md invariant 4), and where the player
 * and camera equal native on every row up to the end window ({@link #LEAD} rows before the signpost row to one frame
 * past the trace), the end of the act is compared on every level frame of that window against native RAM
 * ({@code native/rehost-goal/<payload>/frames.csv}, the goal probe; README there): each signpost's routine and frame
 * ($17, Level End/Main.asm:708-907), the goal posts ($16) and big rings ($14) by place and routine, the results lines'
 * frames, routines, X positions and timers ($3A, Results/Main.asm:42-345), {@code timeBonus}, {@code ringBonus}, the
 * score, {@code ctrlLocked} with {@code playerCtrlHold}, the end-of-act sound requests by ID (FM_SIGNPOST,
 * SCMD_FADEPCM, SCMD_RESULTMUS) and the row the results set {@code levelRestart} 2 (ObjResults_NextLevel).
 *
 * <p>Into the next act: at the stage start the program flow is put in IPX's campaign at the route's act through its
 * own entry points (New Game, then one cleared act per earlier act), so the results' {@code levelRestart} 2 returns
 * to IPX and the coordinator requests the next act. What ObjResults_NextLevel wrote on the native exit row
 * ({@code zoneAct}, {@code timeZone}, {@code goodFuture}, {@code goodFutureFlags}; Results/Main.asm:277-341) must
 * equal the requested act and the time owner's state; act 2 requests act 3's good or bad future file by
 * {@code goodFuture} (RunBossLevel), act 1 act 2's present file; that act then loads with the period and the score.
 *
 * <p>A route whose player or camera diverges before its window is <b>pending</b> (its middle is a route lane's): it is
 * listed with its first divergence and not asserted, and the test fails once it reaches its window, until it is moved
 * into the asserted set. A route that reaches its window but whose results land on the other side of the signpost's
 * slot than native's (FindObjSlot order, the slot lane's; they then run a frame apart) is pending on slot order, named
 * with both runs' slots, and fails once the slots agree. No alignment is applied. R81B, R81C and R82A never reach a
 * signpost (rehost-long README), so they have no end to check.
 */
class TestSonicCdActEndNative {
    /** Rows before the signpost row at which the end window starts. */
    static final int LEAD = 30;

    @TempDir
    Path tempDir;

    /** One native probe row: column values by name. */
    record NativeRow(Map<String, String> v) {
        int hex(String k) { return Integer.parseInt(v.get(k), 16); }
        int dec(String k) { return Integer.parseInt(v.get(k)); }
        /** The probe's object entries of one ID: id:slot:routine:x:yScr:y:frame:var2A:var32b:var32w. */
        List<int[]> objects(int id) {
            List<int[]> out = new ArrayList<>();
            for (String o : v.get("objs").split(" ")) {
                if (o.isBlank()) continue;
                String[] p = o.split(":");
                if (Integer.parseInt(p[0], 16) != id) continue;
                int[] a = new int[p.length];
                for (int i = 0; i < p.length; i++) a[i] = Integer.parseInt(p[i], 16);
                a[1] = Integer.parseInt(p[1]);
                out.add(a);
            }
            return out;
        }
    }

    /** The goal probe's rows by trace row ({@code probe frame = bk2_frame_offset + row + 1}). */
    static Map<Integer, NativeRow> nativeRows(CdStagePayload payload, int bk2Offset) throws Exception {
        Path csv = CdTestMedia.nativeFile("rehost-goal/" + payload + "/frames.csv");
        Assumptions.assumeTrue(Files.isRegularFile(csv), "No goal probe for " + payload);
        List<String> lines = Files.readAllLines(csv);
        String[] header = lines.getFirst().split(",", -1);
        Map<Integer, NativeRow> out = new HashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",", -1);
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) m.put(header[i], c[i]);
            out.put(Integer.parseInt(c[0]) - bk2Offset - 1, new NativeRow(m));
        }
        return out;
    }

    /** The outcome of one route's end: per channel, the first mismatch and its count. */
    static final class Report {
        final CdStagePayload payload;
        int windowStart, goal, last, compared, firstDivergence = -1, firstRingDivergence = -1, nativeExit = -1, engineExit = -1;
        String slots = "";
        boolean slotsDiffer;
        final Map<String, String> first = new LinkedHashMap<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final List<String> notes = new ArrayList<>();

        Report(CdStagePayload payload) { this.payload = payload; }

        void mismatch(String channel, int row, String detail) {
            counts.merge(channel, 1, Integer::sum);
            first.putIfAbsent(channel, row + ": " + detail);
        }

        /** Player and camera equal native on every row before the window. */
        boolean reachesWindow() { return firstDivergence < 0 || firstDivergence >= windowStart; }

        String line() {
            String player = (firstDivergence < 0 ? "player green" : "player first differs at row " + firstDivergence)
                    + (firstRingDivergence < 0 ? "" : "; rings first differ at row " + firstRingDivergence);
            if (!reachesWindow()) return String.format("| %s | %d | %d | pending | %s |", payload, goal, last, player);
            return String.format("| %s | %d | %d | %d | %s; %s%s%s%s |", payload, goal, last, compared, player,
                    nativeExit == engineExit ? "exit row " + nativeExit + " equal" : "exit native " + nativeExit + " engine " + engineExit,
                    slots.isEmpty() ? "" : "; " + slots,
                    first.isEmpty() ? "" : "; " + first.entrySet().stream()
                            .map(e -> e.getKey() + " x" + counts.get(e.getKey()) + " first " + e.getValue())
                            .reduce((a, b) -> a + "; " + b).orElse(""),
                    notes.isEmpty() ? "" : "; " + String.join("; ", notes));
        }
    }

    /**
     * Stage, signpost row (rehost-long README), status, and the row from which the player may differ (0: never).
     * {@code asserted}: every channel equals native over the window and the results set {@code levelRestart} on
     * native's row. {@code pending}: the route diverges before its window. {@code slots}: it reaches its window but its
     * results' slot is on the other side of the signpost's than native's. {@code rings}: it reaches its window with the
     * ring counter different over it.
     * R81A is asserted although its ring counter differs from row 3466 (the hurt that spills the rings) to before the window:
     * every channel equals native over the window and the exit row is equal.
     * R52A was {@code rings} (25 against native's 24 from row 2005) until the replay
     * admitted its lost V-INT: native's row-1478 VBlank produced no interrupt entry (a handler still running), and the
     * spilled rings' floor check is gated by {@code levelVIntCounter+3} plus RunObjects' {@code d7}
     * (Ring/Main.asm:413-418), so an engine one V-INT ahead bounced them on other frames. With the counter's
     * stage-entry base (the timing contract's section 4) and the 2026-11-13 lost-V-INT admission the counter and rings
     * equal native on every row ({@code TestSonicCdStageEntryVIntBase}). The first ring divergence of every route is
     * reported.
     */
    @ParameterizedTest(name = "{0}: signpost row {1}, {2}")
    @CsvSource({
            "R11A, 3502, asserted, 0", "R11B, 2452, asserted, 0", "R11C, 5071, asserted, 0", "R12A, 3525, asserted, 0",
            "R31A, 4572, asserted, 0", "R31B, 7523, asserted, 0", "R31C, 6277, asserted, 0", "R32A, 5963, asserted, 0",
            "R41A, 4731, asserted, 0", "R41B, 4003, asserted, 0", "R41C, 5286, asserted, 0", "R42A, 5693, pending, 0",
            "R51A, 4037, asserted, 0", "R51B, 4775, asserted, 0", "R51C, 6433, asserted, 0", "R52A, 4053, asserted, 0",
            "R61A, 4174, asserted, 0", "R61B, 4731, asserted, 0", "R61C, 6049, asserted, 0", "R62A, 4068, asserted, 0",
            "R71A, 3258, asserted, 0", "R71B, 4199, asserted, 0", "R71C, 5378, asserted, 0", "R72A, 3681, asserted, 0",
            "R81A, 4018, asserted, 0"})
    void actEndEqualsNative(CdStagePayload payload, int goalRow, String status, int playerMayDifferFrom) throws Exception {
        Report report = run(payload, goalRow, tempDir);
        System.out.println("[scd-act-end] " + report.line());
        switch (status) {
            case "pending" -> assertTrue(!report.reachesWindow(), payload + " now reaches its end window: move it into "
                    + "the asserted set. " + report.line());
            case "slots" -> {
                assertTrue(report.reachesWindow(), payload + " no longer reaches its end window: " + report.line());
                assertTrue(report.slotsDiffer, payload + " results and signpost slots now agree with native: move it "
                        + "into the asserted set. " + report.line());
            }
            case "rings" -> {
                assertTrue(report.reachesWindow(), payload + " no longer reaches its end window: " + report.line());
                assertTrue(report.first.containsKey("rings"),
                        payload + " rings now equal native over its end window: move it into the asserted set. " + report.line());
            }
            case "asserted" -> {
                assertTrue(report.reachesWindow(), payload + " diverges before its end window: " + report.line());
                assertTrue(!report.slotsDiffer, payload + " results land on the other side of the signpost's slot: " + report.line());
                if (report.firstDivergence >= 0) {
                    assertTrue(playerMayDifferFrom > 0 && report.firstDivergence >= playerMayDifferFrom,
                            "player differs at row " + report.firstDivergence + ": " + report.line());
                }
                assertTrue(report.compared > (report.last - report.windowStart) / 2, "too few level frames compared: " + report.line());
                assertEquals(report.nativeExit, report.engineExit, "the row ObjResults_NextLevel sets levelRestart: " + report.line());
                assertTrue(report.first.isEmpty(), "differs from native: " + report.line());
            }
            default -> throw new IllegalArgumentException(status);
        }
    }

    /** One engine level frame's end-of-act state, after that row was stepped. */
    private record EngineRow(CdActEndObjects.Snapshot objects, int score, int rings, String sounds) { }

    /**
     * The end of an act's sound requests, as the probe sees them: {@code F9D} FM_SIGNPOST (FM/_Sound IDs.inc:23) in
     * fmSndQueue1, {@code C82} SCMD_FADEPCM and {@code C6B} SCMD_RESULTMUS (_Include/System.inc:251) in GACOMCMD0.
     */
    private static final java.util.Set<String> END_SOUNDS = java.util.Set.of("F9D", "C82", "C6B");

    private static String endSounds(String tokens) {
        return java.util.Arrays.stream(tokens.split(" ")).filter(END_SOUNDS::contains).sorted()
                .reduce((a, b) -> a + " " + b).orElse("");
    }

    static Report run(CdStagePayload payload, int goalRow, Path tempDir) throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-long/README.md").getParent();
        var fixture = CdFixtureReplay.Fixture.under(root, payload);
        Assumptions.assumeTrue(fixture.present(), "Native route absent: " + payload);
        TraceData trace = TraceData.load(fixture.traceDir());
        Map<Integer, NativeRow> nat = nativeRows(payload, trace.metadata().bk2FrameOffset());
        Report report = new Report(payload);
        report.goal = goalRow;
        report.last = trace.frameCount() - 1;
        report.windowStart = goalRow - LEAD;
        for (var e : nat.entrySet()) {
            if (e.getValue().hex("levelRestart") == 2 && e.getKey() > goalRow
                    && (report.nativeExit < 0 || e.getKey() < report.nativeExit)) report.nativeExit = e.getKey();
        }
        Map<Integer, EngineRow> engine = new LinkedHashMap<>();
        StringBuilder requested = new StringBuilder();
        CdStagePayload[] stage = new CdStagePayload[1];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            Path config = Files.createDirectories(tempDir.resolve(payload.name()));
            var outcome = CdFixtureReplay.replay(disc, config, fixture, Integer.MAX_VALUE, false, -1, -1, new CdFixtureReplay.Hooks() {
                @Override
                public void row(int row, AbstractPlayableSprite sonic) {
                    if (row == 0) {
                        stage[0] = ((CdLevel) GameServices.level().getCurrentLevel()).payload();
                        primeCampaign(stage[0]);
                        GameServices.audio().setRequestObserver((kind, id) -> requested.append(
                                kind == com.openggf.audio.AudioRequestObserver.RequestClass.MUSIC
                                        || kind == com.openggf.audio.AudioRequestObserver.RequestClass.COMMAND ? " C" : " F")
                                .append(Integer.toHexString(id).toUpperCase()));
                    }
                    // The ring counter (the fixture replay leaves it out): it decides the ring bonus.
                    if (row > 0 && report.firstRingDivergence < 0) {
                        TraceFrame expected = trace.getFrame(row);
                        TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace, trace.getFrame(row - 1), expected);
                        if (TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)
                                && CdFixtureReplay.completedLevelFrame(trace, row, trace.getFrame(row - 1), expected, phase).rings()
                                != sonic.getRingCount()) report.firstRingDivergence = row;
                    }
                    if (row >= report.windowStart) capture(engine, row, requested);
                    requested.setLength(0);
                }

                @Override
                public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                    // Past the trace: the movie's next rows (the pad is not read under ctrlLocked), to native's exit row.
                    for (int row = report.last + 1; row <= Math.max(report.last, report.nativeExit) + 1; row++) {
                        if (runner.getRecordingFramesRemaining() > 0) runner.stepFrameFromRecording();
                        else runner.stepFrame(false, false, false, false, false);
                        capture(engine, row, requested);
                        requested.setLength(0);
                    }
                    GameServices.audio().setRequestObserver(com.openggf.audio.AudioRequestObserver.NONE);
                    if (report.reachesWindow()) nextStage(report, nat.get(report.nativeExit), nat.get(report.nativeExit - 1));
                }
            }) ;
            report.firstDivergence = outcome.firstErrorRow();
        }
        if (!report.reachesWindow()) return report;
        compareWindow(report, trace, nat, engine);
        return report;
    }

    private static void capture(Map<Integer, EngineRow> engine, int row, StringBuilder requested) {
        engine.put(row, new EngineRow(CdActEndObjects.capture(), GameServices.gameState().getScore(),
                GameServices.level().getLevelGamestate().getRings(), endSounds(requested.toString())));
    }

    /** Every level frame of the window against the probe row that holds the same completed pass. */
    private static void compareWindow(Report report, TraceData trace, Map<Integer, NativeRow> nat, Map<Integer, EngineRow> engine) {
        Integer nSpawn = null, eSpawn = null;
        for (int i = report.windowStart; i <= Math.max(report.last, report.nativeExit) + 1; i++) {
            EngineRow now = engine.get(i);
            if (now == null) continue;
            if (report.engineExit < 0 && now.objects().nextLevel()) report.engineExit = i;
            int natRow = i;
            if (i <= report.last && i > 0) {
                TraceFrame expected = trace.getFrame(i), prior = trace.getFrame(i - 1);
                TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace, prior, expected);
                if (!TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) continue;
                TraceFrame cmp = CdFixtureReplay.completedLevelFrame(trace, i, prior, expected, phase);
                if (i + 1 <= report.last && cmp == trace.getFrame(i + 1)) natRow = i + 1;
            }
            NativeRow n = nat.get(natRow);
            if (n == null || n.dec("levelStarted") == 0) continue;
            report.compared++;
            // Which side of the signpost's slot the results took (FindObjSlot, Level End/Main.asm:838-842), once.
            if (nSpawn == null && !n.objects(0x3A).isEmpty()) {
                nSpawn = i;
                int nSign = n.objects(0x17).stream().filter(a -> a[2] >= 8).mapToInt(a -> a[1]).findFirst().orElse(-1);
                int nRes = n.objects(0x3A).getFirst()[1];
                for (int j = Math.max(report.windowStart, i - 2); j <= i + 2 && eSpawn == null; j++) {
                    EngineRow e = engine.get(j);
                    if (e == null || e.objects().resultsSlot() < 0) continue;
                    eSpawn = j;
                    int eSign = e.objects().signs().stream().filter(a -> a.routine() >= 8)
                            .mapToInt(CdActEndObjects.Sign::slot).findFirst().orElse(-1);
                    int eRes = e.objects().resultsSlot();
                    report.slotsDiffer = (nRes > nSign) != (eRes > eSign);
                    if (report.slotsDiffer) report.slots = "pending on slot order: results " + eRes + " vs signpost " + eSign
                            + " (native " + nRes + " vs " + nSign + ")";
                }
            }
            compareEnd(report, i, n, now.objects(), now.score());
            if (n.hex("rings") != now.rings()) report.mismatch("rings", i, n.hex("rings") + "/" + now.rings());
            // The requests are logged in the frame whose code made them, even when its pass completes on the next row.
            NativeRow made = nat.get(i);
            String nSounds = made == null ? "" : endSounds(made.v().get("writes"));
            if (!nSounds.equals(now.sounds())) report.mismatch("sound", i, "[" + nSounds + "]/[" + now.sounds() + "]");
        }
    }

    /**
     * IPX's campaign at this stage, through the program flow's own entry points: New Game ({@code RunR1}), then one
     * cleared act per earlier act of the run (RunLevel/RunBossLevel), so the results' {@code levelRestart} 2 returns to
     * a campaign IPX and requests the next act. The period of the route's own file is the time owner's, set when the
     * stage loaded (stage-select entry, as the native route was entered). Typed flow calls, no native values.
     */
    private static void primeCampaign(CdStagePayload payload) {
        var scenes = com.openggf.game.session.SessionManager.requireCurrentGameModule()
                .getGameService(com.openggf.game.soniccd.program.CdSceneCoordinator.class);
        var flow = scenes.flow();
        flow.titleExited(com.openggf.game.soniccd.program.CdProgramFlow.EXIT_NEW_GAME);
        int zone = payload.stage().zone().ordinal(), act = payload.stage().actNumber() - 1;
        int acts = zone * 3 + act;
        for (int i = 0; i < acts; i++) {
            flow.stageEnded(new com.openggf.game.soniccd.program.CdStageResult(3,
                    com.openggf.game.soniccd.program.CdStageResult.TIME_PRESENT, false, false, 0));
        }
        assertEquals(zone, flow.zone(), "campaign zone");
        assertEquals(act, flow.act(), "campaign act");
    }

    /**
     * What ObjResults_NextLevel leaves for IPX (Results/Main.asm:277-341) on the native exit row: the next
     * {@code zoneAct}, {@code timeZone} (present, or the future for act 3), {@code goodFuture} and
     * {@code goodFutureFlags}; the engine's campaign must request that act and load the file of that period (act 3:
     * good future if {@code goodFuture}, RunBossLevel IPX.asm:606-611), keeping the score.
     */
    private static void nextStage(Report report, NativeRow after, NativeRow before) {
        if (after == null) {
            report.mismatch("next", report.nativeExit, "no native exit row");
            return;
        }
        var levels = GameServices.level();
        var module = com.openggf.game.session.SessionManager.requireCurrentGameModule();
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(module);
        // The request IPX's campaign made: the coordinator's last step (CdSceneCoordinator#stageEnded).
        List<String> route = module.getGameService(com.openggf.game.soniccd.program.CdSceneCoordinator.class).route();
        var m = java.util.regex.Pattern.compile("-> Stage\\[payload=(\\w+)").matcher(route.isEmpty() ? "" : route.getLast());
        CdStagePayload next = m.find() ? CdStagePayload.valueOf(m.group(1)) : null;
        String want = String.format("zone %d act %d timeZone %d goodFuture %d goodFutureFlags %d", after.dec("zone"),
                after.dec("act"), after.dec("timeZone"), after.dec("goodFuture"), after.hex("goodFutureFlags"));
        String got = String.format("zone %d act %d timeZone %d goodFuture %d goodFutureFlags %d",
                next == null ? -1 : next.stage().zone().ordinal(), next == null ? -1 : next.stage().actNumber() - 1,
                tt.timeZone().nativeValue(), tt.goodFuture() ? 1 : 0, tt.goodFutureFlags());
        if (!want.equals(got)) report.mismatch("next", report.nativeExit, want + "/" + got + " " + route);
        if (next == null) return;
        // The file of that period: present for acts 2, the good or bad future for act 3 (RunLevel, RunBossLevel).
        com.openggf.game.soniccd.CdPeriod period = after.dec("timeZone") == 1 ? com.openggf.game.soniccd.CdPeriod.PRESENT
                : after.dec("goodFuture") != 0 ? com.openggf.game.soniccd.CdPeriod.GOOD_FUTURE
                : com.openggf.game.soniccd.CdPeriod.BAD_FUTURE;
        if (next.stage().period() != period) report.mismatch("next", report.nativeExit, period + "/" + next);
        // The engine loads the requested act (GameLoop's transition does this) with the campaign's carried score.
        if (levels.getRequestedZone() != SonicCdZoneRegistry.zoneIndex(next)
                || levels.getRequestedAct() != SonicCdZoneRegistry.actIndex(next)) {
            report.mismatch("next", report.nativeExit, next + " requested as " + levels.getRequestedZone() + "/" + levels.getRequestedAct());
            return;
        }
        try {
            levels.loadZoneAndAct(levels.getRequestedZone(), levels.getRequestedAct());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        CdStagePayload loaded = ((CdLevel) levels.getCurrentLevel()).payload();
        int score = GameServices.gameState().getScore();
        String expected = next + " timeZone " + after.dec("timeZone") + " score " + before.hex("score") * 10;
        String actual = loaded + " timeZone " + tt.timeZone().nativeValue() + " score " + score;
        if (!expected.equals(actual)) report.mismatch("next", report.nativeExit, expected + "/" + actual);
        report.notes.add("next " + loaded);
    }

    /** The end-of-act objects, bonuses, score and lock against the probe row. */
    private static void compareEnd(Report report, int row, NativeRow n, CdActEndObjects.Snapshot eng, int engineScore) {
        CdActEndObjects.Snapshot res = eng;
        // Each native signpost against the engine's at the same place (a stage can have one per route).
        for (int[] sign : n.objects(0x17)) {
            String want = "rt " + sign[2] + " frame " + sign[6];
            String got = eng.signs().stream().filter(e -> e.x() == sign[3] && e.y() == sign[5])
                    .map(e -> "rt " + e.routine() + " frame " + e.frame()).findFirst().orElse("none");
            if (!want.equals(got)) report.mismatch("signpost", row, String.format("($%X,$%X) ", sign[3], sign[5]) + want + "/" + got);
        }
        // The goal post and big rings by place (both ways: one the engine kept or deleted early differs too).
        List<String> nMarks = new ArrayList<>();
        for (int id : new int[] {0x16, 0x14}) {
            for (int[] o : n.objects(id)) {
                nMarks.add(String.format("%X ($%X,$%X) rt %d", id, o[3], o[5], o[2]));
            }
        }
        List<String> eMarks = new ArrayList<>();
        for (var mk : eng.markers()) {
            eMarks.add(String.format("%X ($%X,$%X) rt %d", mk.id(), mk.x(), mk.y(), mk.routine()));
        }
        nMarks.sort(null);
        eMarks.sort(null);
        if (!nMarks.equals(eMarks)) report.mismatch("goal post/big ring", row, nMarks + "/" + eMarks);
        List<String> nLines = new ArrayList<>();
        for (int[] r : n.objects(0x3A)) {
            if (r[2] >= 4) nLines.add(String.format("f%d rt %d x %d t %d", r[6], r[2], r[3], r[9]));
        }
        nLines.sort(null);
        List<String> eLines = new ArrayList<>();
        for (var l : res.lines()) eLines.add(String.format("f%d rt %d x %d t %d", l.frame(), l.routine(), l.x(), l.timer()));
        eLines.sort(null);
        if (!nLines.equals(eLines)) report.mismatch("results", row, nLines + "/" + eLines);
        if (n.hex("timeBonus") != res.timeBonus() || n.hex("ringBonus") != res.ringBonus()) {
            report.mismatch("bonus", row, n.hex("timeBonus") + "," + n.hex("ringBonus") + "/" + res.timeBonus() + "," + res.ringBonus());
        }
        if (n.hex("score") * 10 != engineScore) report.mismatch("score", row, n.hex("score") * 10 + "/" + engineScore);
        boolean nLocked = n.hex("ctrlLocked") != 0;
        if (nLocked != eng.ctrlLocked() || (nLocked && (n.hex("ctrlHold") >> 8) != (eng.ctrlHold() >> 8))) {
            report.mismatch("lock", row, nLocked + " " + Integer.toHexString(n.hex("ctrlHold")) + "/" + eng.ctrlLocked()
                    + " " + Integer.toHexString(eng.ctrlHold()));
        }
    }

}
