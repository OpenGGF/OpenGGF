package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.game.soniccd.timetravel.CdTimeZone;
import com.openggf.game.soniccd.timetravel.CdWarpRequest;
import com.openggf.game.soniccd.timetravel.CdWarpTransition;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native checks for time travel. Trace data is comparison-only: the BK2 drives the engine
 * and the native rows are only compared.
 * <ul>
 *   <li>R52A (rehost fixture, stage select): the player touches the past post at $220,$1A8
 *   and runs; the warp fires at timer 210, where {@code scrollLock} freezes the camera
 *   (Sonic/Main.asm:607-643). Player and camera rows must equal native through the
 *   trigger and the 20 locked rows after it.</li>
 *   <li>{@code rehost-timetravel/present-to-future} and {@code present-to-past}: New Game
 *   routes through Palmtree Panic act 1 Present to a future post, or a future then a past
 *   post, recorded through the trigger; the native probe follows the program change into
 *   R11D and R11B. The engine's own trigger state is carried into the destination through
 *   the ordinary level load, and the destination's first rows must equal native.</li>
 * </ul>
 */
class TestSonicCdTimeTravel {
    /** R52A: the warp fires on this row; the camera locks from it (FRU report, frontier table). */
    private static final int R52A_TRIGGER_ROW = 580;
    /** R52A: the past post at $220,$1A8 is touched on this row (native timeWarpDir, tt_probe frame 2964). */
    private static final int R52A_TOUCH_ROW = 370;
    /** Destination rows compared after the arrival, by native {@code levelFrames}. */
    private static final int DESTINATION_FRAMES = 40;

    @TempDir
    Path tempDir;

    @Test
    void r52aPastPostWarpTriggersAndLocksTheCameraAsNative() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-fixtures/README.md").getParent();
        var fixture = CdFixtureReplay.Fixture.under(root, CdStagePayload.R52A);
        Assumptions.assumeTrue(fixture.present(), "No R52A fixture");
        Map<Integer, int[]> owner = new HashMap<>();
        CdWarpRequest[] warp = new CdWarpRequest[1];
        CdWarpRequest[] readyRequest = new CdWarpRequest[1];
        CdStagePayload[] arrived = new CdStagePayload[1];
        int[][] arrival = new int[1][];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var outcome = CdFixtureReplay.replay(disc, Files.createDirectories(tempDir.resolve("r52a")), fixture,
                    Integer.MAX_VALUE, false, R52A_TOUCH_ROW - 2, Integer.MAX_VALUE, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            CdTimeTravel tt = timeTravel();
                            owner.put(row, new int[] {tt.warpDirection(), tt.warpTimer(), tt.starsActive() ? 1 : 0,
                                    tt.pendingWarp() != null ? 1 : 0});
                            if (tt.pendingWarp() != null) warp[0] = tt.pendingWarp();
                        }

                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) throws Exception {
                            CdTimeTravel tt = timeTravel();
                            // The trace ends where native re-enters LevelStart; the next BK2 row reaches 230.
                            for (int i = 0; i < 3 && tt.readyWarp() == null; i++) runner.stepFrameFromRecording();
                            readyRequest[0] = tt.readyWarp();
                            if (readyRequest[0] == null) return;
                            arrived[0] = CdWarpTransition.loadDestination(GameServices.level(), tt);
                            var camera = GameServices.camera();
                            arrival[0] = new int[] {sonic.getCentreX() & 0xFFFF, sonic.getCentreY() & 0xFFFF,
                                    sonic.getXSpeed() & 0xFFFF, sonic.getYSpeed() & 0xFFFF, sonic.getGSpeed() & 0xFFFF,
                                    GameServices.level().getLevelGamestate().getRings(), camera.getX() & 0xFFFF,
                                    camera.getY() & 0xFFFF, sonic.getInvulnerableFrames(),
                                    (int) GameServices.level().getLevelGamestate().getTimerFrames(),
                                    com.openggf.trace.TraceCharacterState.statusByteFromSprite(sonic)};
                        }
                    });
            outcome.rows().stream().filter(r -> r.row() >= R52A_TRIGGER_ROW - 2)
                    .forEach(r -> System.out.println("R52A " + r.format()));
            // Every row, every field: the lag rows after the trigger (a pass sampled in RunObjects, its completed
            // mapping frame on the next row) are mapped by CdFixtureReplay.completedRow like any other.
            assertEquals(-1, outcome.firstErrorRow(),
                    () -> "R52A diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
            assertTrue(outcome.rowsCompared() > R52A_TRIGGER_ROW + 10, "R52A must replay past the trigger");
        }
        // The owner's values against native RAM (tt_probe over the same inputs; probe frame f is
        // BK2 row f-1, trace row = BK2 row - bk2_frame_offset): timeWarpDir, timeWarpTimer,
        // timeWarp (stars) and spawnMode 2 on every row from before the touch to the end.
        Path probe = CdTestMedia.nativeFile("rehost-timetravel/r52a-native/probe-frames.csv");
        var traceData = com.openggf.trace.TraceData.load(fixture.traceDir());
        int offset = traceData.metadata().bk2FrameOffset();
        List<String> lines = Files.readAllLines(probe);
        List<String> header = List.of(lines.get(0).split(","));
        int compared = 0;
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",", -1);
            int row = Integer.parseInt(c[header.indexOf("frame")]) - 1 - offset;
            if (row < R52A_TOUCH_ROW - 10 || !owner.containsKey(row)) continue;
            int dir = (byte) Integer.parseInt(c[header.indexOf("timeWarpDir")], 16);
            int timer = Integer.parseInt(c[header.indexOf("timeWarpTimer")], 16);
            int stars = Integer.parseInt(c[header.indexOf("timeWarp")], 16);
            int spawn2 = Integer.parseInt(c[header.indexOf("spawnMode")], 16) == 2 ? 1 : 0;
            int[] eng = owner.get(row);
            assertEquals(dir, eng[0], "timeWarpDir row " + row);
            // The timer is V-INT state (RunTimeWarp in VInt_Finish, Level/Main.asm:704-709), compared on its own
            // row. On a row whose levelVIntCounter did not advance the V-INT that started in the frame (a PLC
            // decode in VInt_Level, Level/Main.asm:846) had not reached VInt_Finish at the sample, so native
            // shows the timer one V-INT short there and catches up on the next row (LAG report, class 2).
            boolean vIntFinished = row == 0
                    || traceData.getFrame(row).vblankCounter() != traceData.getFrame(row - 1).vblankCounter();
            if (vIntFinished) assertEquals(timer, eng[1], "timeWarpTimer row " + row);
            assertEquals(stars, eng[2], "timeWarp row " + row);
            assertEquals(spawn2, eng[3], "spawnMode 2 row " + row);
            compared++;
        }
        assertTrue(compared > R52A_TRIGGER_ROW - R52A_TOUCH_ROW, "owner rows compared: " + compared);

        // SaveTimeWarpData's payload against native warp* RAM on the trigger row (scddisasm/src/Level/_Objects/Sonic/Main.asm:532-567).
        String[] trigger = null;
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",", -1);
            if (Integer.parseInt(c[header.indexOf("frame")]) - 1 - offset == R52A_TRIGGER_ROW) trigger = c;
        }
        assertNotNull(trigger, "native trigger row in the probe");
        var carry = warp[0].carry();
        assertEquals(hex(trigger, header, "warpX"), carry.centreX(), "warpX");
        assertEquals(hex(trigger, header, "warpY"), carry.centreY(), "warpY");
        assertEquals(hex(trigger, header, "warpGVel"), carry.groundSpeed() & 0xFFFF, "warpGVel");
        assertEquals(hex(trigger, header, "warpXVel"), carry.xSpeed() & 0xFFFF, "warpXVel");
        assertEquals(hex(trigger, header, "warpYVel"), carry.ySpeed() & 0xFFFF, "warpYVel");
        assertEquals(hex(trigger, header, "warpPlayerFlags"), carry.playerFlags(), "warpPlayerFlags");
        assertEquals(hex(trigger, header, "warpRings"), carry.rings(), "warpRings");
        assertEquals(hex(trigger, header, "warpCamX"), carry.cameraX(), "warpCamX");
        assertEquals(hex(trigger, header, "warpCamY"), carry.cameraY(), "warpCamY");
        // warpBtmBound copies bottomBound, which LevEvents_QQZ12 moves to $310 (Level/_Events.asm:244-246).
        assertEquals(hex(trigger, header, "warpBtmBound"), carry.bottomBound(), "warpBtmBound");
        long nativeTime = Long.parseLong(trigger[header.indexOf("warpTime")], 16);
        long nativeTimeFrames = ((nativeTime >> 16) & 0xFF) * 3600L + ((nativeTime >> 8) & 0xFF) * 60L + (nativeTime & 0xFF);
        System.out.printf("R52A warpTime native=%d engine=%d (frames)%n", nativeTimeFrames, carry.timeFrames());

        // The arrival: R52B through the ordinary load, with LoadTimeWarpData's values and
        // LevelSizeLoad's camera from the restored position (no native R52B arrival exists: the
        // stage-select route cannot warp, IPX.asm:709-722).
        assertNotNull(readyRequest[0], "levelRestart at timer 230");
        assertEquals(CdStagePayload.R52B, arrived[0]);
        int[] a = arrival[0];
        assertEquals(carry.centreX(), a[0], "arrival x");
        assertEquals(carry.centreY(), a[1], "arrival y");
        assertEquals(carry.xSpeed() & 0xFFFF, a[2], "arrival x speed");
        assertEquals(carry.ySpeed() & 0xFFFF, a[3], "arrival y speed");
        assertEquals(carry.groundSpeed() & 0xFFFF, a[4], "arrival ground speed");
        assertEquals(carry.rings(), a[5], "arrival rings");
        // LoadTimeWarpData restores warpTime and the load's first main-loop iteration takes VInt_Level's clock
        // tick (no title card holds it): native rehost-flow/warp-future shows warpTime $1831 at the load and
        // $1832 on the first frame.
        assertEquals(carry.timeFrames() + 1, a[9], "arrival time, one tick on");
        assertEquals(carry.playerFlags(), a[10], "arrival flags");
        System.out.printf("R52A arrival in R52B: x=%04X y=%04X cam=%04X,%04X invuln=%d%n", a[0], a[1], a[6], a[7], a[8]);
        assertEquals(-1, owner.get(R52A_TRIGGER_ROW)[0], "past post: timeWarpDir -1");
        assertEquals(210, owner.get(R52A_TRIGGER_ROW)[1], "trigger at timer 210");
        assertEquals(0, owner.get(R52A_TRIGGER_ROW - 1)[3], "no warp before the trigger row");
        assertEquals(1, owner.get(R52A_TRIGGER_ROW)[3], "spawnMode 2 on the trigger row");
        assertNotNull(warp[0]);
        assertEquals(CdTimeZone.PRESENT, warp[0].fromZone());
        assertEquals(CdTimeZone.PAST, warp[0].toZone());
        assertEquals(CdStagePayload.R52B, warp[0].destination(false));
    }

    /**
     * The recorded Palmtree Panic warps against native RAM (tt_probe over the same inputs,
     * {@code rehost-timetravel/<route>/probe-frames.csv}, README there), from the first touch to the destination. The
     * replay covers the trace (which native's recorder ends where the program leaves the level); the engine is then
     * stepped on with the movie's own inputs, one row per probe row (a probe row flagged lag is a V-blank-only row),
     * until its warp is ready, on the rows of the probe: {@code timeWarpDir}, {@code timeWarpTimer}, {@code timeWarp}
     * and {@code spawnMode} 2 on every row, SaveTimeWarpData's payload on the trigger row (timer 210), the row native
     * sets {@code levelRestart} 2 (timer $E6, RunTimeWarp, Level/Main.asm:704-709) on which the engine's warp is
     * ready and not a row before, and the destination's first frames after the program change.
     */
    /** The name predates the continuation past the trace's end; the skip policy keys on it. */
    @Test
    void recordedPalmtreePanicWarpsMatchNativeAsFarAsTheyReplay() throws Exception {
        assertRecordedWarp("present-to-future", CdStagePayload.R11D, CdTimeZone.FUTURE);
        assertRecordedWarp("present-to-past", CdStagePayload.R11B, CdTimeZone.PAST);
    }

    private void assertRecordedWarp(String name, CdStagePayload expectedDestination, CdTimeZone expectedZone)
            throws Exception {
        Path dir = CdTestMedia.nativeFile("rehost-timetravel/" + name + "/probe-frames.csv").getParent();
        var fixture = new CdFixtureReplay.Fixture(CdStagePayload.R11A, dir.resolve("trace"), dir.resolve("movie.bk2"));
        Assumptions.assumeTrue(fixture.present(), "No recorded warp " + name);
        List<NativeRow> nativeRows = NativeRow.read(dir.resolve("probe-frames.csv"));
        List<String> probe = Files.readAllLines(dir.resolve("probe-frames.csv"));
        List<String> header = List.of(probe.get(0).split(","));
        int traceRows = Files.readAllLines(dir.resolve("trace/physics.csv")).size() - 1;
        int start = -1;
        for (int i = 1; i < probe.size() && start < 0; i++) {
            String[] c = probe.get(i).split(",", -1);
            if (dec(c, header, "levelStarted") == 1 && dec(c, header, "levelFrames") == 1) start = i;
        }
        assertTrue(start > 0, name + ": no level start in the probe");
        final int probeStart = start;

        List<String> mismatches = new ArrayList<>();
        Map<Integer, int[]> owner = new java.util.TreeMap<>();      // row -> dir, timer, stars, pending, ready
        CdWarpRequest[] pending = new CdWarpRequest[1];
        int[] pendingRow = {-1};
        int[] readyRow = {-1};
        CdStagePayload[] destination = new CdStagePayload[1];
        CdWarpRequest[] ready = new CdWarpRequest[1];
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var outcome = CdFixtureReplay.replay(disc, Files.createDirectories(tempDir.resolve(name)), fixture,
                    Integer.MAX_VALUE, false, -1, -1, new CdFixtureReplay.Hooks() {
                        private void record(int row) {
                            CdTimeTravel tt = timeTravel();
                            owner.put(row, new int[] {tt.warpDirection(), tt.warpTimer(), tt.starsActive() ? 1 : 0,
                                    tt.pendingWarp() != null ? 1 : 0, tt.readyWarp() != null ? 1 : 0});
                            if (tt.pendingWarp() != null && pending[0] == null) {
                                pending[0] = tt.pendingWarp();
                                pendingRow[0] = row;
                            }
                            if (tt.readyWarp() != null && readyRow[0] < 0) readyRow[0] = row;
                        }

                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            record(row);
                        }

                        @Override
                        public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) throws Exception {
                            // The trace ends where native's recorder stops; the engine goes on with the movie's inputs
                            // over the probe's rows (a lag row runs only the V-blank) until its warp is ready.
                            int row = traceRows - 1;
                            while (readyRow[0] < 0 && row < traceRows + 400) {
                                row++;
                                String[] c = probe.get(probeStart + row).split(",", -1);
                                var phase = dec(c, header, "lag") == 1 ? com.openggf.trace.TraceExecutionPhase.VBLANK_ONLY
                                        : com.openggf.trace.TraceExecutionPhase.FULL_LEVEL_FRAME;
                                com.openggf.tests.trace.TraceReplayFrameClosureDriver.driveGeneral(phase,
                                        runner::stepFrameFromRecording, runner::skipFrameFromRecording, () -> { });
                                record(row);
                            }
                            ready[0] = timeTravel().readyWarp();
                            if (ready[0] == null) return;
                            destination[0] = CdWarpTransition.loadDestination(GameServices.level(), timeTravel());
                            compareDestination(name, runner, sonic, nativeRows, mismatches);
                        }
                    });
            assertEquals(-1, outcome.firstErrorRow(),
                    () -> name + " diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
        }

        // The owner against native on every row from before the first touch to the row the warp is ready.
        int firstWarpRow = -1;
        int nativeTrigger = -1;
        int nativeReady = -1;
        for (int row = 0; start + row < probe.size(); row++) {
            String[] c = probe.get(probeStart + row).split(",", -1);
            if (firstWarpRow < 0 && hex(c, header, "timeWarpDir") != 0) firstWarpRow = row;
            if (nativeTrigger < 0 && hex(c, header, "timeWarpTimer") == 210 && hex(c, header, "timeWarpDir") != 0) nativeTrigger = row;
            if (nativeReady < 0 && hex(c, header, "levelRestart") != 0) nativeReady = row;
            if (nativeReady >= 0) break;
        }
        assertTrue(firstWarpRow > 0 && nativeTrigger > firstWarpRow && nativeReady > nativeTrigger,
                name + ": native touch " + firstWarpRow + ", trigger " + nativeTrigger + ", ready " + nativeReady);
        int compared = 0;
        for (int row = Math.max(0, firstWarpRow - 10); row <= nativeReady; row++) {
            int[] eng = owner.get(row);
            assertNotNull(eng, name + ": engine row " + row + " not stepped");
            String[] c = probe.get(probeStart + row).split(",", -1);
            assertEquals((byte) hex(c, header, "timeWarpDir"), (byte) eng[0], name + " timeWarpDir row " + row);
            // RunTimeWarp is V-INT state; a row the V-INT of which had not finished at the recorder's sample (a lag row)
            // shows the timer one V-INT short, so the timer is compared on the other rows (LAG report, class 2).
            if (dec(c, header, "lag") == 0) assertEquals(hex(c, header, "timeWarpTimer"), eng[1], name + " timeWarpTimer row " + row);
            assertEquals(hex(c, header, "timeWarp"), eng[2], name + " timeWarp (stars) row " + row);
            assertEquals(hex(c, header, "spawnMode") == 2 ? 1 : 0, eng[3], name + " spawnMode 2 row " + row);
            compared++;
        }
        assertTrue(compared > nativeReady - firstWarpRow, name + " owner rows compared: " + compared);

        // The trigger: SaveTimeWarpData's payload against native warp* RAM (scddisasm/src/Level/_Objects/Sonic/Main.asm:532-567).
        assertEquals(nativeTrigger, pendingRow[0], name + ": the row the engine's warp is saved");
        String[] trigger = probe.get(start + nativeTrigger).split(",", -1);
        var carry = pending[0].carry();
        assertEquals(hex(trigger, header, "warpX"), carry.centreX(), name + " warpX");
        assertEquals(hex(trigger, header, "warpY"), carry.centreY(), name + " warpY");
        assertEquals(hex(trigger, header, "warpGVel"), carry.groundSpeed() & 0xFFFF, name + " warpGVel");
        assertEquals(hex(trigger, header, "warpXVel"), carry.xSpeed() & 0xFFFF, name + " warpXVel");
        assertEquals(hex(trigger, header, "warpYVel"), carry.ySpeed() & 0xFFFF, name + " warpYVel");
        assertEquals(hex(trigger, header, "warpPlayerFlags"), carry.playerFlags(), name + " warpPlayerFlags");
        assertEquals(hex(trigger, header, "warpRings"), carry.rings(), name + " warpRings");
        assertEquals(hex(trigger, header, "warpCamX"), carry.cameraX(), name + " warpCamX");
        assertEquals(hex(trigger, header, "warpCamY"), carry.cameraY(), name + " warpCamY");
        assertEquals(hex(trigger, header, "warpBtmBound"), carry.bottomBound(), name + " warpBtmBound");

        // levelRestart: ready on native's row and not before; then the destination.
        assertEquals(nativeReady, readyRow[0], name + ": the row the engine's warp is ready (native levelRestart row)");
        assertNotNull(ready[0], name + " warp ready");
        assertEquals(expectedZone, ready[0].toZone());
        assertEquals(expectedDestination, destination[0]);
        assertTrue(mismatches.isEmpty(), () -> name + " destination differs from native:\n" + String.join("\n", mismatches));
    }

    private static int dec(String[] c, List<String> header, String column) {
        return Integer.parseInt(c[header.indexOf(column)]);
    }

    /** The destination's rows by {@code levelFrames} 1..N against the native probe after the program change. */
    private static void compareDestination(String name, HeadlessTestRunner runner, AbstractPlayableSprite sonic,
                                           List<NativeRow> nativeRows, List<String> mismatches) {
        int start = -1;
        for (int i = 1; i < nativeRows.size(); i++) {
            if (nativeRows.get(i).levelStarted == 1 && nativeRows.get(i - 1).levelStarted != 1 && i > 3000) start = i;
        }
        assertTrue(start > 0, name + ": no destination level start in the probe");
        Map<Integer, NativeRow> byFrame = new HashMap<>();
        for (int i = start; i < nativeRows.size(); i++) byFrame.putIfAbsent(nativeRows.get(i).levelFrames, nativeRows.get(i));
        var state = GameServices.level().getLevelGamestate();
        for (int lf = 1; lf <= DESTINATION_FRAMES; lf++) {
            if (lf > 1) runner.stepIdleFrames(1);
            NativeRow n = byFrame.get(lf);
            if (n == null) break;
            var camera = GameServices.camera();
            int[] eng = {sonic.getCentreX() & 0xFFFF, sonic.getCentreY() & 0xFFFF, sonic.getXSpeed() & 0xFFFF,
                    sonic.getYSpeed() & 0xFFFF, sonic.getGSpeed() & 0xFFFF, state.getRings(),
                    camera.getX() & 0xFFFF, camera.getY() & 0xFFFF};
            int[] rom = {n.x, n.y, n.xVel, n.yVel, n.gVel, n.rings, n.camX, n.camY};
            String[] names = {"x", "y", "x_vel", "y_vel", "g_vel", "rings", "cam_x", "cam_y"};
            StringBuilder diff = new StringBuilder();
            for (int f = 0; f < eng.length; f++) {
                if (eng[f] != rom[f]) diff.append(String.format(" %s eng=%04X rom=%04X", names[f], eng[f], rom[f]));
            }
            long engTime = state.getTimerFrames();
            String line = String.format("%s dest lf=%d x=%04X y=%04X xs=%04X ys=%04X g=%04X rings=%d cam=%04X,%04X time=%d"
                            + " | rom time=%d", name, lf, eng[0], eng[1], eng[2], eng[3], eng[4], eng[5], eng[6], eng[7],
                    engTime, n.timeFrames());
            System.out.println(line + (diff.length() == 0 ? "" : " DIFF" + diff));
            if (diff.length() != 0) mismatches.add("lf=" + lf + diff);
        }
    }

    private static int hex(String[] c, List<String> header, String name) {
        return Integer.parseInt(c[header.indexOf(name)], 16);
    }

    private static CdTimeTravel timeTravel() {
        return CdTimeTravel.of(SessionManager.requireCurrentGameModule());
    }

    /** One row of {@code tt_probe}'s frames.csv (rehost-timetravel/_tools/tt_probe.cs). */
    record NativeRow(int levelStarted, int levelFrames, int x, int y, int xVel, int yVel, int gVel, int rings,
                     int camX, int camY, long time) {
        long timeFrames() {
            int minutes = (int) (time >> 16) & 0xFF;
            int seconds = (int) (time >> 8) & 0xFF;
            int frames = (int) time & 0xFF;
            return minutes * 3600L + seconds * 60L + frames;
        }

        static List<NativeRow> read(Path csv) throws java.io.IOException {
            List<String> lines = Files.readAllLines(csv);
            List<String> header = List.of(lines.get(0).split(","));
            List<NativeRow> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                String[] c = line.split(",", -1);
                rows.add(new NativeRow(dec(c, header, "levelStarted"), dec(c, header, "levelFrames"),
                        dec(c, header, "p_x"), dec(c, header, "p_y"), hex(c, header, "p_xvel"), hex(c, header, "p_yvel"),
                        hex(c, header, "p_gvel"), hex(c, header, "rings"), hex(c, header, "cam_x"),
                        hex(c, header, "cam_y"), Long.parseLong(c[header.indexOf("time")], 16)));
            }
            return rows;
        }

        private static int dec(String[] c, List<String> header, String name) {
            return Integer.parseInt(c[header.indexOf(name)]);
        }

        private static int hex(String[] c, List<String> header, String name) {
            return Integer.parseInt(c[header.indexOf(name)], 16);
        }
    }
}
