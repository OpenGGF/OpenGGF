package com.openggf.game.soniccd.flow;

import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.timetravel.CdTimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sonic CD's main flow through the engine against native runs of the USA disc
 * ({@code native/rehost-flow/<run>/frames.csv}, the FLOW2 probe; README there). Each native run is a New
 * Game; its setup pokes (player and camera position, lives) take effect before BK2 row 2560, twelve frames
 * after Palmtree Panic act 1's title card released control. The engine run starts a New Game through the
 * title, waits for the same stage time, applies the same setup and then plays each native logic frame
 * (rows where the game polled the pad; a lag row runs no game logic) with that row's pad. Values are
 * compared at native events: the signpost, results, the next level, deaths, restarts and the title.
 *
 * <p>Not compared: frames spent loading a stage file from the disc and in {@code LevelStart}'s blocking
 * fades (the engine does not model disc or lag time); the engine's figure is reported beside native's.
 */
@EnabledOnOs(OS.LINUX)
class TestCdGameFlowNative {
    /** The setup pokes' row (probe frame 2561). */
    private static final int SETUP = 2560;

    private static void kill(CdFlowHarness h) {
        // Setup poke: oY = 2040, below the bottom boundary, so the level-bound check kills the player.
        h.place(h.player().getCentreX(), 2040, GameServices.camera().getX(), GameServices.camera().getY());
    }

    /** First engine frame index (harness frame) at which {@code test} holds, scanning the replayed rows. */
    private static int engineFrame(Map<Integer, CdFlowHarness.Row> rows, java.util.function.Predicate<CdFlowHarness.Row> test) {
        for (var r : rows.values()) if (test.test(r)) return r.frame();
        throw new AssertionError("engine never reached the event");
    }

    @Test
    void actOneClearTalliesAndEntersActTwoAsNative() throws Exception {
        CdNativeRun nat = CdNativeRun.load("act-clear");
        int signpost = nat.first(SETUP, r -> nat.dec(r, "updateHUDTime") == 0);
        int results = nat.first(signpost, r -> nat.dec(r, "ctrlLocked") == 1);
        int oneUp = nat.first(results, r -> nat.dec(r, "lives") == 4);
        int nextLevel = nat.first(results, r -> nat.dec(r, "act") == 1);
        int entry = nat.first(nextLevel, r -> nat.dec(r, "levelStarted") == 1);
        int unlock = nat.first(entry, r -> nat.dec(r, "ctrlLocked") == 0);
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            int[] routeSize = {h.scenes.route().size()};
            int[] stageEnded = {-1};
            var rows = h.replay(nat, SETUP, nextLevel + 1, r -> {
                if (r == SETUP) h.place(9728, 489, 9568, 393);
            }, (r, row) -> {
                if (stageEnded[0] < 0 && h.scenes.route().size() > routeSize[0]) stageEnded[0] = row.frame();
                return false;
            });
            int start = rows.values().iterator().next().frame() - 1;
            var engineRows = rows.values().stream().toList();
            int engineSignpost = engineFrame(rows, CdFlowHarness.Row::timerPaused);
            int engineResults = engineFrame(rows, CdFlowHarness.Row::ctrlLocked);
            int engineOneUp = engineFrame(rows, row -> row.lives() == 4);
            // Logic frames from the setup to each event.
            assertEquals(nat.logicFrames(SETUP, signpost + 1), engineSignpost - start, "frames to the signpost");
            assertEquals(nat.dec(signpost, "p_x"), rows.get(signpost).x(), "player X when the signpost stops the clock");
            // Native's V-INT ticked once more on the signpost frame; the engine's one-frame lead since the title
            // card makes the stopped clocks equal.
            assertEquals(nat.timeFrames(signpost), rows.get(signpost).time(), "the stopped clock");
            assertEquals(nat.logicFrames(signpost + 1, results + 1), engineResults - engineSignpost,
                    "signpost to results (120 spin + 60, Level End/Main.asm:758-800)");
            assertEquals(nat.logicFrames(results + 1, oneUp + 1), engineOneUp - engineResults,
                    "results to the last tally step's extra life");
            // Tally: the bonuses are points; each step takes 100 and AddPoints 10 units of ten (Results/Main.asm:193-276).
            assertEquals(nat.hex(results, "timeBonus") + nat.hex(results, "ringBonus"), rows.get(oneUp).score(),
                    "time bonus (TimeBonuses at 0:03) plus ring bonus, in points");
            assertEquals(nat.hex(nextLevel, "score") * 10, engineRows.get(engineRows.size() - 1).score(), "score after the tally");
            assertEquals(nat.logicFrames(results + 1, nextLevel + 1), stageEnded[0] - engineResults,
                    "results to ObjResults_NextLevel (levelRestart 2)");

            // The next stage: R12A, present, from its start, with the carried lives and score.
            h.until(() -> h.row().payload() == CdStagePayload.R12A, 900, 0);
            int engineEntry = h.frame;
            h.until(() -> h.row().payload() == CdStagePayload.R12A && !h.row().ctrlLocked(), 900, 0);
            var atUnlock = h.row();
            assertEquals(0, nat.dec(entry, "zone"));
            assertEquals(1, nat.dec(entry, "act"));
            assertEquals(CdStageResultPeriod.PRESENT, CdStageResultPeriod.of(nat.dec(entry, "timeZone")));
            assertEquals(CdStagePayload.R12A, atUnlock.payload(), "IPX RunR12 present file (IPX.asm:543-546)");
            assertEquals(CdTimeZone.PRESENT, h.timeTravel().timeZone());
            assertEquals(nat.dec(unlock, "p_x"), atUnlock.x(), "entry X");
            assertEquals(nat.dec(unlock, "p_y"), atUnlock.y(), "entry Y");
            assertEquals(nat.dec(unlock, "rings"), atUnlock.rings(), "entry rings");
            assertEquals(nat.dec(unlock, "lives"), atUnlock.lives(), "entry lives");
            assertEquals(nat.hex(unlock, "score") * 10, atUnlock.score(), "entry score");
            assertEquals(nat.timeFrames(unlock) + 1, atUnlock.time(),
                    "entry clock at the title card's release: the engine ticks this frame, native at the next V-INT");
            assertEquals(1, nat.dec(entry, "ctrlLocked"), "native loads the title card");
            assertEquals(CdProgramRequest.Route.CAMPAIGN, h.scenes.flow().route());
            System.out.printf("CD act clear: signpost->next stage start native %d rows (%d logic), engine %d frames%n",
                    entry - signpost, nat.logicFrames(signpost, entry), engineEntry - engineSignpost);
        }
    }

    /** The period enum by native {@code timeZone} value, for readable assertions. */
    private enum CdStageResultPeriod {
        PAST, PRESENT, FUTURE;
        static CdStageResultPeriod of(int timeZone) { return values()[timeZone & 0x7F]; }
    }

    @Test
    void deathWithoutCheckpointRestartsFromTheStartWithItsTitleCard() throws Exception {
        CdNativeRun nat = CdNativeRun.load("death-start");
        int gone = nat.first(SETUP, r -> nat.dec(r, "lives") == 2);
        int restart = nat.first(gone, r -> nat.hex(r, "levelRestart") != 0);
        int entry = nat.first(restart, r -> nat.dec(r, "levelStarted") == 1);
        int unlock = nat.first(entry, r -> nat.dec(r, "ctrlLocked") == 0);
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            var rows = h.replay(nat, SETUP, SETUP + 1, r -> kill(h), null);
            assertTrue(rows.get(SETUP).dead(), "the setup kills the player");
            h.until(() -> h.row().lives() == 2, 600, 0);
            int engineGone = h.frame;
            h.until(() -> h.row().fading(), 600, 0);
            // The fade starts on the frame after the restart request.
            assertEquals(nat.logicFrames(gone + 1, restart + 1), h.frame - 1 - engineGone,
                    "ObjSonic_Restart's 60-frame timer (Sonic/Main.asm:2443-2452)");
            h.until(() -> !h.row().dead() && h.row().ctrlLocked(), 900, 0);
            assertEquals(1, nat.dec(entry, "ctrlLocked"), "native reloads the title card (bclr #1,plcLoadFlags)");
            assertTrue(h.row().ctrlLocked(), "the engine reloads the title card");
            assertEquals(nat.timeFrames(entry), h.row().time(), "the clock restarts at 0");
            h.until(() -> !h.row().ctrlLocked(), 900, 0);
            var at = h.row();
            assertEquals(nat.dec(unlock, "p_x"), at.x());
            assertEquals(nat.dec(unlock, "p_y"), at.y());
            assertEquals(nat.dec(unlock, "lives"), at.lives());
            assertEquals(nat.dec(unlock, "rings"), at.rings());
            assertEquals(nat.timeFrames(unlock) + 1, at.time(), "the engine's clock ticks on the release frame");
            assertEquals(CdStagePayload.R11A, at.payload());
        }
    }

    @Test
    void deathAfterACheckpointRestartsThereWithItsSavedClock() throws Exception {
        CdNativeRun nat = CdNativeRun.load("death-checkpoint");
        int activated = nat.first(SETUP, r -> nat.hex(r, "checkpoint") == 2);
        int killRow = 2700;
        int gone = nat.first(killRow, r -> nat.dec(r, "lives") == 2);
        int restart = nat.first(gone, r -> nat.hex(r, "levelRestart") != 0);
        int entry = nat.first(restart, r -> nat.dec(r, "levelStarted") == 1);
        // The engine's load runs the first main-loop iteration in the frame that loads (CdLoadFrame: the loop runs
        // no second one there), so the engine's state after that frame is native's first logic frame.
        int first = nat.first(entry + 1, r -> !nat.lag(r));
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            var rows = h.replay(nat, SETUP, killRow + 1, r -> {
                if (r == SETUP) h.place(2704, 640, 2544, 544);
                if (r == killRow) kill(h);
            }, null);
            var cp = GameServices.level().getCheckpointState();
            assertEquals(nat.dec(activated, "p_x"), rows.get(activated).x(), "the post is touched at the native position");
            assertTrue(cp.getLastCheckpointIndex() == 2, "checkpoint 2 recorded");
            h.until(() -> h.row().lives() == 2, 600, 0);
            int engineGone = h.frame;
            h.until(() -> h.row().fading(), 600, 0);
            assertEquals(nat.logicFrames(gone + 1, restart + 1), h.frame - 1 - engineGone, "restart timer");
            h.until(() -> !h.row().dead(), 900, 0);
            var at = h.row();
            assertEquals(nat.dec(first, "p_x"), at.x(), "savedX");
            assertEquals(nat.dec(first, "p_y"), at.y(), "savedY");
            assertEquals(nat.hex(first, "cam_x"), GameServices.camera().getX() & 0xFFFF, "camera X from the restored position");
            assertEquals(nat.hex(first, "cam_y"), GameServices.camera().getY() & 0xFFFF, "camera Y");
            assertEquals(nat.dec(first, "lives"), at.lives());
            assertEquals(nat.dec(first, "rings"), at.rings(), "LoadCheckpointData clears rings");
            assertEquals(nat.timeFrames(first), at.time(), "savedTime with frames 59 and one second less, then two ticks");
            assertEquals(nat.hex(first, "p_hurt"), h.player().getInvulnerableFrames(), "LoadPlayer's $78 invulnerability, counting");
            assertEquals(0, nat.dec(entry, "ctrlLocked"), "native keeps the title card unloaded");
            assertFalse(at.ctrlLocked(), "no title card at a checkpoint restart");
        }
    }

    /**
     * The present-to-future warp of the time-travel lane's route ({@code rehost-flow/warp-future}, the same BK2
     * schedule). The engine cannot yet replay that route to the warp (terrain frontier at R11A row 753), so it
     * is put in the native state after BK2 row 4114: player position, subpixels, speeds and air flag, camera,
     * rings, clock, score, and the time owner's warp direction, timer ($D0) and stars. From there each native
     * row runs with its pad: the trigger at 210, the restart at 230, the stage exit, the WARP program and the
     * destination load are the engine's.
     */
    @Test
    void timeWarpRunsTheCutsceneAndLoadsTheDestinationAsNative() throws Exception {
        CdNativeRun nat = CdNativeRun.load("warp-future");
        int setup = 4114;
        int trigger = nat.first(setup, r -> nat.dec(r, "spawnMode") == 2);
        int restart = nat.first(trigger, r -> nat.hex(r, "levelRestart") != 0);
        int arm = nat.first(restart, r -> nat.dec(r, "warpTimer") == 90);
        int returned = nat.first(arm, r -> nat.dec(r, "warpTimer") == 0 && nat.hex(r, "warpVint") != 0
                && nat.hex(r + 1, "warpVint") == nat.hex(r, "warpVint") && nat.hex(r, "hdr8") != 0
                && r > arm + 100);
        int destination = nat.first(returned, r -> nat.dec(r, "levelStarted") == 1);
        // The engine's load runs Level_MainLoop's first iteration (ObjSonic_Init) in the frame that loads
        // (CdLoadFrame), so the engine's state after that frame is native's first logic frame.
        int first = nat.first(destination + 1, r -> !nat.lag(r));
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            h.until(() -> !h.row().ctrlLocked(), 900, 0);
            var p = h.player();
            p.setRolling((nat.hex(setup, "p_flags") & 4) != 0);       // oFlags bit 2: the jump's roll, radii first
            p.setCentreX((short) nat.dec(setup, "p_x"));
            p.setCentreY((short) nat.dec(setup, "p_y"));
            p.setSubpixelRaw(nat.hex(setup, "p_xsub"), nat.hex(setup, "p_ysub"));
            p.setXSpeed((short) nat.hex(setup, "p_xvel"));
            p.setYSpeed((short) nat.hex(setup, "p_yvel"));
            p.setGSpeed((short) nat.hex(setup, "p_gvel"));
            p.setAir((nat.hex(setup, "p_flags") & 2) != 0);
            p.setDirection((nat.hex(setup, "p_flags") & 1) != 0 ? com.openggf.physics.Direction.LEFT : com.openggf.physics.Direction.RIGHT);
            p.setRingCount(nat.dec(setup, "rings"));
            GameServices.camera().setX((short) nat.hex(setup, "cam_x"));
            GameServices.camera().setY((short) nat.hex(setup, "cam_y"));
            // ObjSonic_ExtCamera's focus column (camXCenter): native's camera follows 96 pixels behind the
            // player here, so the pan sits at its 64-pixel limit; pan the engine's focus there too.
            int pan = (nat.dec(setup, "p_x") - nat.hex(setup, "cam_x")) - 160;
            while (GameServices.camera().getHorizontalFocusOffset() != pan) {
                GameServices.camera().updateHorizontalFocusPan(p, false);
            }
            GameServices.gameState().addScore(nat.hex(setup, "score") * 10);
            // The engine's clock leads native's by one after the title card (see the harness).
            GameServices.level().getLevelGamestate().setTimerFrames(nat.timeFrames(setup) + 1);
            var tt = h.timeTravel();
            tt.restore(new com.openggf.game.soniccd.timetravel.CdTimeTravel.Snapshot(CdTimeZone.PRESENT, false,
                    (byte) nat.hex(setup, "timeWarpDir"), nat.hex(setup, "timeWarpTimer"), nat.hex(setup, "timeWarp") != 0,
                    null, false, false, 0, 0, false, 0, CdStagePayload.R11A, null));
            CdFlowHarness.Row atTrigger = null;
            for (int r = setup + 1; r <= restart; r++) {
                int[] pad = nat.pad(r);
                h.step(pad[0], pad[1], pad[2] != 0);
                if (r == trigger) atTrigger = h.row();
                if (r == trigger) {
                    var warp = tt.pendingWarp();
                    assertNotNull(warp, "the trigger at timeWarpTimer 210 (Sonic/Main.asm:607-644) on the native row");
                    var carry = warp.carry();
                    assertEquals(nat.hex(r, "warpX"), carry.centreX(), "warpX");
                    assertEquals(nat.hex(r, "warpY"), carry.centreY(), "warpY");
                    assertEquals((short) nat.hex(r, "warpGVel"), carry.groundSpeed(), "warpGVel");
                    assertEquals((short) nat.hex(r, "warpXVel"), carry.xSpeed(), "warpXVel");
                    assertEquals((short) nat.hex(r, "warpYVel"), carry.ySpeed(), "warpYVel");
                    assertEquals(nat.hex(r, "warpRings"), carry.rings(), "warpRings");
                    int t = nat.hex(r, "warpTime");
                    assertEquals(((t >> 16) & 0xFF) * 3600L + ((t >> 8) & 0xFF) * 60L + (t & 0xFF), carry.timeFrames(), "warpTime");
                    assertEquals(nat.hex(r, "warpCamX"), carry.cameraX(), "warpCamX");
                    assertEquals(nat.hex(r, "warpCamY"), carry.cameraY(), "warpCamY");
                    assertEquals(nat.hex(r, "warpPlayerFlags"), carry.playerFlags(), "warpPlayerFlags");
                }
                if (r < restart) assertNull(tt.readyWarp(), "not ready before 230 (row " + r + ")");
            }
            assertNotNull(atTrigger);
            int engineRestart = h.frame;
            assertTrue(h.scenes.route().stream().anyMatch(e -> e.startsWith("stage ended CdStageResult[lives=3, timeZone=130")),
                    "the stage leaves on the levelRestart row with timeZone $82: " + h.scenes.route());
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.OPTIONS, 600, 0);
            var screen = assertInstanceOf(com.openggf.game.soniccd.warp.CdWarpCutsceneScreen.class, h.scenes.lastProgramScreen());
            var program = screen.program();
            h.until(() -> program.done(), 900, 0);
            assertEquals(nat.hex(returned, "warpVint") - nat.hex(arm, "warpVint"), program.frames() - 0,
                    "WARP's V-INTs from the 90-frame arm to its return (Time Warp Cutscene/Main.asm:157-199)");
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.LEVEL && h.level() != null, 900, 0);
            var at = h.row();
            assertEquals(CdStagePayload.R11D, at.payload(), "RunLevel's bad future file (IPX.asm:563-578)");
            assertEquals(2, nat.dec(first, "timeZone"));
            assertEquals(nat.dec(first, "p_x"), at.x(), "LoadTimeWarpData X");
            assertEquals(nat.dec(first, "p_y"), at.y(), "LoadTimeWarpData Y");
            assertEquals((short) nat.hex(first, "p_xvel"), h.player().getXSpeed(), "carried X speed");
            assertEquals((short) nat.hex(first, "p_yvel"), h.player().getYSpeed(), "carried Y speed");
            assertEquals((short) nat.hex(first, "p_gvel"), h.player().getGSpeed(), "carried ground speed");
            assertEquals(nat.dec(first, "rings"), at.rings(), "warpRings");
            assertEquals(nat.dec(first, "lives"), at.lives(), "lives carried through the title host");
            assertEquals(nat.hex(first, "score") * 10, at.score(), "score carried through the title host");
            assertEquals(nat.hex(first, "cam_x"), GameServices.camera().getX() & 0xFFFF, "camera X");
            assertEquals(nat.hex(first, "cam_y"), GameServices.camera().getY() & 0xFFFF, "camera Y");
            assertEquals(nat.timeFrames(first), at.time(), "warpTime, two ticks on");
            assertFalse(at.ctrlLocked(), "no title card on arrival");
            assertEquals(CdTimeZone.FUTURE, tt.timeZone());
            System.out.printf("CD warp: restart->destination second frame native %d rows, engine %d frames (disc loads and lag unmodelled)%n",
                    first - restart, h.frame - engineRestart);
        }
    }

    /**
     * The giant ring after Palmtree Panic act 1's signpost ({@code rehost-flow/special-stage}: the act-clear
     * setup with {@code rings} poked to 50, and a jump into the ring). The results tally adds the 50-ring
     * bonus, {@code RunLevel .SpecialStage} runs Special Stage 1 in normal mode (no input: it times out), and
     * IPX goes on to act 2 with {@code curSpecStage} advanced and no Time Stone.
     */
    @Test
    void giantRingRunsTheSpecialStageAndReturnsToActTwoAsNative() throws Exception {
        CdNativeRun nat = CdNativeRun.load("special-stage");
        int touch = nat.first(SETUP, r -> nat.dec(r, "specialStage") == 1);
        int nextLevel = nat.first(touch, r -> nat.dec(r, "act") == 1);
        int entry = nat.first(nextLevel, r -> nat.dec(r, "levelStarted") == 1);
        int unlock = nat.first(entry, r -> nat.dec(r, "ctrlLocked") == 0);
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            int[] touched = {-1};
            h.replay(nat, SETUP, nextLevel + 1, r -> {
                if (r == SETUP) {
                    h.place(9728, 489, 9568, 393);
                    h.player().setRingCount(nat.hex(SETUP, "rings"));          // setup poke: rings = 50
                }
            }, (r, row) -> {
                if (touched[0] < 0 && h.level() != null && h.level().flags().specialStage) touched[0] = r;
                return false;
            });
            assertEquals(touch, touched[0], "the big ring sets specialStage (Level End/Main.asm:497) on the native row");
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE, 1200, 0);
            assertEquals(nat.hex(nextLevel, "score") * 10, GameServices.gameState().getScore(),
                    "time bonus plus the 50-ring bonus (rings * 100)");
            assertEquals(nat.dec(nextLevel, "lives"), GameServices.gameState().getLives());
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.LEVEL && h.level() != null
                    && h.level().payload() == CdStagePayload.R12A && !h.row().ctrlLocked(), 12000, 0);
            var at = h.row();
            assertEquals(nat.dec(unlock, "curSpecStage"), h.scenes.flow().currentSpecialStage(), "curSpecStage after the stage");
            assertEquals(nat.hex(unlock, "timeStones"), h.scenes.flow().timeStones(), "no Time Stone");
            assertEquals(nat.hex(unlock, "timeStones"), h.timeTravel().timeStones(), "the time owner's stones");
            assertEquals(nat.dec(unlock, "p_x"), at.x());
            assertEquals(nat.dec(unlock, "p_y"), at.y());
            assertEquals(nat.dec(unlock, "rings"), at.rings());
            assertEquals(nat.dec(unlock, "lives"), at.lives());
            assertEquals(nat.hex(unlock, "score") * 10, at.score());
            assertEquals(1, nat.dec(entry, "ctrlLocked"), "native loads act 2's title card");
            System.out.println("CD special stage route: " + h.scenes.route());
        }
    }

    @Test
    void gameOverReturnsToTheTitleAfterEightSeconds() throws Exception {
        CdNativeRun nat = CdNativeRun.load("game-over");
        int gone = nat.first(SETUP, r -> nat.dec(r, "lives") == 0);
        int restart = nat.first(gone, r -> nat.hex(r, "levelRestart") != 0);
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            h.replay(nat, SETUP, SETUP + 1, r -> {
                // Setup poke: lives = 1, then the same kill.
                GameServices.gameState().loseLife();
                GameServices.gameState().loseLife();
                kill(h);
            }, null);
            h.until(() -> h.row().lives() == 0, 600, 0);
            int engineGone = h.frame;
            assertEquals(0x1E0, nat.hex(gone, "p_reset"), "native sets the 8-second timer");
            h.until(() -> h.row().fading(), 900, 0);
            assertEquals(nat.logicFrames(gone + 1, restart + 1), h.frame - 1 - engineGone,
                    "ObjSonic_Restart's game-over timer (Sonic/Main.asm:2439)");
            h.until(() -> h.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
            assertEquals(0, nat.hex(nat.size() - 1, "plcLoadFlags"), ".CheckNoLives clears plcLoadFlags");
            assertEquals(nat.hex(restart, "savedLevel"), h.scenes.flow().savedLevel(), "GameOver: savedLevel = zone, act 0");
            assertNull(h.scenes.flow().route(), "the run is over");
            assertFalse(h.timeTravel().goodFuture(), "GameOver clears goodFuture (IPX.asm:337)");
            assertTrue(h.scenes.route().contains("-> Title[]"), "IPX returns to .GameLoop");
        }
    }
}
