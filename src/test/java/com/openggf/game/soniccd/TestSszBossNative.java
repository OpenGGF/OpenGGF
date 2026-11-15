package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.boss.ssz.SszMetalSonic;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Stardust Speedway race against native references recorded for it
 * ({@code ~/Downloads/Sonic-CD/native/rehost-boss-ssz/<movie>/}: BK2, v5 trace, and the boss probe's post-frame RAM
 * in {@code probe/globals.csv} and {@code probe/objects.csv}; comparison only), through {@link CdBossNativeReplay}:
 * per trace row the player and camera rows, the race objects' appearances (row, ID), every race slot's ID, X, Y and
 * routine against native RAM, Metal Sonic's collision type and, while he dashes (routine $C) with his collision on,
 * his script step ({@code var_2d}), and the camera's left, right and bottom bounds. Amy's landing (routine 4) stands
 * for the capsule's break: after it only the act end remains.
 */
class TestSszBossNative {
    static final CdBossNativeReplay.Spec SPEC = new CdBossNativeReplay.Spec("rehost-boss-ssz",
            java.util.Set.of(0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x37), SszMetalSonic.ID, 0xC, 15,
            part -> ((SszMetalSonic) part).scriptStep(), 0x33);

    @TempDir
    Path tempDir;

    /** Good Future: the player wins the race, Metal Sonic is destroyed at the door, Amy is freed, results. */
    @Test
    void r73cWinReplaysAgainstNative() throws Exception {
        check("R73C-win", CdStagePayload.R73C);
    }

    /** Bad Future: the player wins the race (Amy at $3F43, $1AB), she is freed, results. */
    @Test
    void r73dWinReplaysAgainstNative() throws Exception {
        check("R73D-win", CdStagePayload.R73D);
    }

    /**
     * Good Future, the promo's finish (CAPTURE2 lane): R73C-win's inputs to U+3560, then the player held level with
     * Metal Sonic (within $1C of him) until 38 frames before the line, and first to $3E90 with Metal Sonic just short
     * of the door ($3E48), so the door closes in front of him instead of setting him back to it
     * (sub_20C072, objects/r7/boss.asm:239-248).
     */
    @Test
    void r73cNeckAndNeckFinishReplaysAgainstNative() throws Exception {
        check("R73C-neck", CdStagePayload.R73C);
    }

    /** The player stops short of the door: Metal Sonic finishes first ($82), then Eggman's laser kills the player. */
    @Test
    void r73cLossAtTheDoorReplaysAgainstNative() throws Exception {
        check("R73C-lose", CdStagePayload.R73C);
    }

    /** The player stops mid-race: the laser catches up and kills them ({@code sub_20C940}). */
    @Test
    void r73cLaserDeathReplaysAgainstNative() throws Exception {
        check("R73C-laser", CdStagePayload.R73C);
    }

    /** The player brakes in front of Metal Sonic's dash and is hit by its $BC collision. */
    @Test
    void r73cDashHitReplaysAgainstNative() throws Exception {
        check("R73C-hit", CdStagePayload.R73C);
    }

    /**
     * The score on every row of the R73C win against native's {@code score} long ($FF1518, Main CPU
     * Variables.inc:70; the probe's {@code score} column, re-probed for this movie): native counts AddPoints' units
     * of ten (HUD and Points/Main.asm:157-160), the engine points. Metal Sonic's destruction at the door adds
     * {@code moveq #$64} (objects/r7/boss.asm:720-744; native row 4238, 0 to 100 units), then the results tally.
     */
    @Test
    void r73cScoreFollowsNative() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-boss-ssz/R73C-win/manifest.json").getParent();
        Path traceDir = root.resolve("trace");
        String meta = java.nio.file.Files.readString(traceDir.resolve("metadata.json"));
        var m = java.util.regex.Pattern.compile("\"bk2_frame_offset\"\\s*:\\s*(\\d+)").matcher(meta);
        assertTrue(m.find(), "bk2_frame_offset");
        int offset = Integer.parseInt(m.group(1));
        List<String> lines = java.nio.file.Files.readAllLines(root.resolve("probe/globals.csv"));
        int column = java.util.Arrays.asList(lines.getFirst().split(",")).indexOf("score");
        assertTrue(column > 0, "globals.csv has the score column");
        java.util.Map<Integer, Integer> nativeScore = new java.util.HashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            nativeScore.put(Integer.parseInt(c[0]), Integer.parseInt(c[column]));
        }
        List<String> mismatches = new ArrayList<>();
        int[] rows = {0};
        int[] defeatRow = {-1};
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R73C, traceDir,
                    root.resolve("movie.bk2")), Integer.MAX_VALUE, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            Integer units = nativeScore.get(offset + row + 1);
                            if (units == null) return;
                            rows[0]++;
                            int engine = GameServices.gameState().getScore();
                            if (defeatRow[0] < 0 && units > 0) defeatRow[0] = row;
                            if (engine != units * 10) mismatches.add("row " + row + " native " + units * 10 + " engine " + engine);
                        }
                    });
        }
        System.out.println("BOSS-SSZ R73C-win score rows " + rows[0] + ", first native points row " + defeatRow[0]
                + ", mismatches " + mismatches.size() + (mismatches.isEmpty() ? "" : " first " + mismatches.getFirst()));
        assertEquals(4238, defeatRow[0], "native's first row with points (Metal Sonic destroyed)");
        assertTrue(rows[0] > 5000, "rows compared: " + rows[0]);
        assertTrue(mismatches.isEmpty(), mismatches.size() + " score mismatches, first "
                + (mismatches.isEmpty() ? "" : mismatches.getFirst()));
    }

    /** Bad Future: the player stops short of the door, Metal Sonic finishes first, Eggman's laser kills them. */
    @Test
    void r73dLossAtTheDoorReplaysAgainstNative() throws Exception {
        check("R73D-lose", CdStagePayload.R73D);
    }

    /** Bad Future: the player stops mid-race and the laser catches them ({@code sub_20C940}). */
    @Test
    void r73dLaserDeathReplaysAgainstNative() throws Exception {
        check("R73D-laser", CdStagePayload.R73D);
    }

    /**
     * The player turns back during Metal Sonic's attack pose, so the pose ends with the player behind him: the electric
     * shield (routine $E, the shield object $30) instead of the dash.
     */
    @Test
    void r73cElectricShieldReplaysAgainstNative() throws Exception {
        check("R73C-shield", CdStagePayload.R73C);
    }

    /** After the win the player walks past Amy facing away from her: the hug (routines $A, $C) and the results. */
    @Test
    void r73cAmysHugReplaysAgainstNative() throws Exception {
        check("R73C-hug", CdStagePayload.R73C);
    }

    /** After the win the player rolls past Amy: she jumps (routines $E, $10), twice, then hugs them. */
    @Test
    void r73cAmysJumpReplaysAgainstNative() throws Exception {
        check("R73C-amyjump", CdStagePayload.R73C);
    }

    private void check(String movie, CdStagePayload payload) throws Exception {
        dump(movie, payload);
        CdBossNativeReplay.check(tempDir, SPEC, movie, payload);
    }

    /**
     * Diagnostics: the engine's race objects, player, camera and bounds per trace row, written to
     * {@code target/soniccd-boss-ssz/<movie>-engine.csv} for comparison with the probe's {@code objects.csv} offline.
     */
    private void dump(String movie, CdStagePayload payload) throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-boss-ssz/" + movie + "/manifest.json").getParent();
        Path out = Path.of("target", "soniccd-boss-ssz");
        java.nio.file.Files.createDirectories(out);
        StringBuilder csv = new StringBuilder("row,slot,id,class,x,y,routine,col,extra\n");
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            java.nio.file.Files.createDirectories(tempDir.resolve("dump"));
            CdFixtureReplay.replay(disc, tempDir.resolve("dump"), new CdFixtureReplay.Fixture(payload, root.resolve("trace"),
                    root.resolve("movie.bk2")), Integer.MAX_VALUE, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            var cam = GameServices.camera();
                            csv.append(String.format("%d,-1,0,player,%d,%d,%d,0,cam=%d/%d L=%d R=%d B=%d T=%d%n", row,
                                    sonic.getCentreX(), sonic.getCentreY(), sonic.getDead() ? 6 : sonic.isHurt() ? 4 : 2,
                                    cam.getX(), cam.getY(), cam.getMinX(), cam.getMaxX(), cam.getMaxY(), cam.getMaxYTarget()));
                            for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                                if (!(o instanceof com.openggf.game.soniccd.objects.boss.CdBossPart part) || part.isDestroyed()) continue;
                                String extra = "";
                                if (part instanceof com.openggf.game.soniccd.objects.boss.ssz.SszBossEggman e) {
                                    extra = "flags=" + e.bossFlags() + " finish=" + e.finish();
                                } else if (part instanceof SszMetalSonic m) {
                                    extra = "step=" + m.scriptStep();
                                }
                                csv.append(String.format("%d,%d,%d,%s,%d,%d,%d,%d,%s%n", row, part.getSlotIndex(),
                                        part.getSpawn().objectId(), part.getClass().getSimpleName(), part.getX() & 0xFFFF,
                                        part.getY() & 0xFFFF, part.routine(), part.colType(), extra));
                            }
                        }
                    });
        }
        java.nio.file.Files.writeString(out.resolve(movie + "-engine.csv"), csv);
    }

    /**
     * The act end of the R73C win movie, which runs to the stage exit. Amy's {@code StartResults} spawned the results
     * object; native's reaches {@code ObjResults_NextLevel} (routine 8) on the trace's last row when its timer runs out
     * (Results/Main.asm:193-209); the next frame the stage program returns. The movie entered by the stage select, so
     * IPX's {@code StageSelect} then returns to the title (IPX.asm:726-727). The engine's results must reach routine 8
     * on the same row, and one frame later report the act's end through {@code CdStageExit.actCleared}.
     */
    @Test
    void r73cActEndReachesTheStageExitOnTheNativeRow() throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-boss-ssz/R73C-win/manifest.json").getParent();
        Path traceDir = root.resolve("trace");
        int lastRow = com.openggf.trace.TraceData.load(traceDir).frameCount() - 1;
        int[] nextLevelRow = {-1};
        List<String> route = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(CdStagePayload.R73C, traceDir,
                    root.resolve("movie.bk2")), lastRow, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            // The stage select's row 61 is R73C (IPX StageSels), as the movie's route chose
                            // (rehost-fixtures/R73C/manifest.json, selection_index 61).
                            if (row == 0) coordinator().stageSelected(61);
                            if (nextLevelRow[0] < 0 && resultsRoutine() == 8) nextLevelRow[0] = row;
                        }

                        @Override
                        public void after(com.openggf.tests.HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                            runner.stepFrame(false, false, false, true, false);
                            route.addAll(coordinator().route());
                        }
                    });
        }
        System.out.println("BOSS-SSZ R73C-win native NextLevel row " + lastRow + ", engine " + nextLevelRow[0]
                + ", route " + route);
        assertEquals(lastRow, nextLevelRow[0], "the row the results reach ObjResults_NextLevel");
        assertTrue(route.stream().anyMatch(r -> r.startsWith("stage ended")), "actCleared reported: " + route);
        assertTrue(route.getLast().contains("Title"), "stage select returns to the title: " + route);
    }

    private static com.openggf.game.soniccd.program.CdSceneCoordinator coordinator() {
        return GameServices.module().getGameService(com.openggf.game.soniccd.program.CdSceneCoordinator.class);
    }

    /** The live results object's routine (the first line's), or -1. */
    private static int resultsRoutine() {
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof com.openggf.game.soniccd.objects.common.CdResults) || o.isDestroyed()) continue;
            try {
                var field = o.getClass().getDeclaredField("routine");
                field.setAccessible(true);
                int routine = field.getInt(o);
                if (routine == 8) return 8;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return -1;
    }

}
