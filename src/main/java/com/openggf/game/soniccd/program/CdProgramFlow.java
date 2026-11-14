package com.openggf.game.soniccd.program;

import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.CdProgramCatalogue;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.backupram.CdBackupRam;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.game.soniccd.program.CdProgramRequest.NotHosted;
import com.openggf.game.soniccd.program.CdProgramRequest.SpecialStage;
import com.openggf.game.soniccd.special.CdSpecialStageEntry;
import com.openggf.game.soniccd.special.CdSpecialStageResult;
import com.openggf.game.soniccd.program.CdProgramRequest.Route;
import com.openggf.game.soniccd.program.CdProgramRequest.Stage;
import com.openggf.game.soniccd.program.CdProgramRequest.StageSelect;
import com.openggf.game.soniccd.program.CdProgramRequest.Title;

import java.util.List;
import java.util.Objects;

/**
 * The CD Initial Program's main loop ({@code IPX.asm}) as game logic: which program runs next.
 * IPX is a chain of subroutine calls ({@code .GameLoop} calls the title, {@code NewGame} calls
 * {@code RunR11}, {@code RunR12}...); here the position in that chain is explicit state and every
 * return of a called program is one method call. It holds the IPX variables that outlive a program
 * (save data, time-attack unlock, title flags, the demo counter); it never reads stage state.
 *
 * <p>The save variables go through the backup RAM ({@link #attachBackupRam}): {@code ReadSaveData} and
 * {@code WriteSaveData} (IPX.asm:1420-1475) run where IPX calls them. Without an attached backup RAM they
 * stay in this object for the session.
 */
public final class CdProgramFlow {
    /** {@code .Scenes} title exit codes (IPX.asm:77-89). */
    public static final int EXIT_DEMO = 0, EXIT_NEW_GAME = 1, EXIT_LOAD_GAME = 2, EXIT_TIME_ATTACK = 3,
            EXIT_BURAM_MANAGER = 4, EXIT_DA_GARDEN = 5, EXIT_VISUAL_MODE = 6, EXIT_SOUND_TEST = 7,
            EXIT_STAGE_SELECT = 8, EXIT_BEST_STAFF_TIMES = 9;
    /** {@code cmpi.b #%01111111} for all seven good futures or time stones (IPX.asm:307-310). */
    private static final int ALL_SEVEN = 0x7F;
    /** {@code .Demos} (IPX.asm:948-954); {@code moveq #(.DemosEnd-.Demos)/2-1,d1} is the last id. */
    private static final CdNativeProgram[] DEMOS = {CdNativeProgram.OPENING, CdNativeProgram.R11ADEMO,
            CdNativeProgram.SPECSTAGE, CdNativeProgram.R43CDEMO, CdNativeProgram.SPECSTAGE, CdNativeProgram.R82ADEMO};
    private static final CdProgramCatalogue CATALOGUE = CdProgramCatalogue.forRelease(CdReleaseVariant.USA);

    private final List<CdStageSelection> selections;
    /** {@code R11SubCmds..R83SubCmds} (IPX.asm:651-704): [zone][act][present, past, good, bad] or [good, bad]. */
    private final CdStagePayload[][][] rounds;

    // IPX variables kept across programs (Main CPU Variables.inc).
    private int savedLevel;        // word: zone in the high byte
    private int goodFutures;
    private int titleFlags;
    private int timeAttackUnlock;
    private int curSpecStage;
    private int timeStones;
    private int demoId;
    private int unkBuRAMVar;       // unkBuRAMVar (Main CPU Variables.inc:31), kept through the save
    private CdBackupRam backupRam;
    // Time Attack (lane TA): timeAttackMode, timeAttackTime, timeAttackLevel (Main CPU Variables.inc:24-28)
    // and the lives byte the menu tests when it returns ($FF1508, TimeAttack_StartCheckRecordDataEnabled).
    private boolean timeAttackMode;
    private int timeAttackTime;
    private int timeAttackLevel;
    private int lives;
    /** {@code TimeAttack .Selections} (IPX.asm:1245-1281): menu level to {@code StageSels} row, or {@code -n} for special stage n. */
    private byte[] timeAttackSelections;
    // Per-run variables.
    private int zone;
    private int act;
    private int timeZone = CdStageResult.TIME_PRESENT;
    private boolean goodFuture;
    private Route route;
    private int lastEndingId = -1;
    /** {@code plcLoadFlags} bit 0 (Level/Main.asm:39-55): set once a run's first {@code LevelStart} reset lives and score. */
    private boolean levelLoaded;

    public CdProgramFlow(List<CdStageSelection> selections, byte[] levelSubCmds) {
        this.selections = List.copyOf(selections);
        this.rounds = rounds(Objects.requireNonNull(levelSubCmds, "levelSubCmds"));
    }

    public static CdProgramFlow read(CdProgramData data) {
        CdProgramFlow flow = new CdProgramFlow(CdStageSelection.read(data), data.bytes("levelSubCmds"));
        flow.timeAttackSelections = data.bytes("timeAttackSelections");
        return flow;
    }

    /**
     * IPX {@code Start} (IPX.asm:41-48): {@code SCMD_BURAMINIT}, which sets {@code saveDisabled} when it does
     * not succeed, then {@code ReadSaveData}. The fatal BRAMINIT messages never return natively.
     */
    public CdBackupRam.Init attachBackupRam(CdBackupRam ram) {
        backupRam = Objects.requireNonNull(ram, "ram");
        CdBackupRam.Init init = ram.initialize();
        if (init.fatal()) throw new IllegalStateException("BRAMINIT stops on its " + init + " message (Initialization/Main.asm:119-163)");
        readSaveData();
        return init;
    }

    public CdBackupRam backupRam() { return backupRam; }

    /** IPX {@code ReadSaveData} (IPX.asm:1420-1432). */
    private void readSaveData() {
        if (backupRam == null) return;
        CdSaveVariables v = backupRam.readVariables();
        savedLevel = v.savedLevel();
        goodFutures = v.goodFutures();
        titleFlags = v.titleFlags();
        timeAttackUnlock = v.timeAttackUnlock();
        unkBuRAMVar = v.unknown();
        curSpecStage = v.curSpecStage();
        timeStones = v.timeStones();
    }

    /** IPX {@code WriteSaveData} (IPX.asm:1448-1475). */
    private void writeSaveData() {
        if (backupRam == null) return;
        backupRam.writeVariables(new CdSaveVariables(savedLevel, goodFutures, titleFlags, timeAttackUnlock,
                unkBuRAMVar, curSpecStage, timeStones));
    }

    /** {@code BuRAMManager} (IPX.asm:101-105): the manager returned; {@code ReadSaveData}, then {@code .GameLoop}. */
    public void backupRamManagerReturned() { readSaveData(); }

    /**
     * {@code BuRAMManager} (IPX.asm:101-105): the manager, then {@code ReadSaveData}, which drops any variable IPX
     * changed but never wrote (GameOver's {@code savedLevel}). The manager is not hosted and changes nothing, so the
     * read runs at once; a manager host calls {@link #backupRamManagerReturned} after its own changes.
     */
    private CdProgramRequest backupRamManager() {
        readSaveData();
        return notHosted(CdNativeProgram.BURAMMGR, "BuRAMManager");
    }

    /**
     * The per-run resets of {@code .GameLoop} (IPX.asm:50-68) before the title runs.
     * Called whenever the title screen starts.
     */
    public void enterTitle() {
        timeAttackMode = false;                           // move.b d0,timeAttackMode (IPX.asm:56)
        route = null;
        goodFuture = false;
        timeZone = CdStageResult.TIME_PRESENT;
    }

    /** The title returned exit code {@code d1}; dispatch through {@code .Scenes} (IPX.asm:70-89). */
    public CdProgramRequest titleExited(int exitCode) {
        return switch (exitCode) {
            case EXIT_DEMO -> demo();
            case EXIT_NEW_GAME -> newGame();
            case EXIT_LOAD_GAME -> loadGame();
            // TimeAttack (IPX.asm:1214-1242) loops on its menu and returns to .GameLoop on exit; the
            // timeattack/ host runs the menu and reports through timeAttackSelected.
            case EXIT_TIME_ATTACK -> timeAttackMenu();
            case EXIT_BURAM_MANAGER -> backupRamManager();
            case EXIT_DA_GARDEN -> notHosted(CdNativeProgram.DAGARDEN, "DAGarden");
            // VisualMode (IPX.asm:1125-1204) loops on its menu and FMVs, then returns to .GameLoop.
            case EXIT_VISUAL_MODE -> notHosted(CdNativeProgram.VISMODE, "VisualMode");
            // SoundTest (IPX.asm:1035-1123): its exits run SS8 and the easter-egg programs, then return.
            case EXIT_SOUND_TEST -> notHosted(CdNativeProgram.SNDTEST, "SoundTest");
            case EXIT_STAGE_SELECT -> new StageSelect();
            case EXIT_BEST_STAFF_TIMES -> notHosted(CdNativeProgram.STAFFTIMES, "BestStaffTimes");
            default -> throw new IllegalArgumentException("Title exit code outside .Scenes: " + exitCode);
        };
    }

    /** STSEL_.MMD returned selection {@code d0} (IPX.asm:710-728). */
    public CdProgramRequest stageSelected(int index) {
        if (index < 0 || index >= CdStageSelection.MENU_ENTRIES) {
            throw new IllegalArgumentException("Stage-select index outside the menu: " + index);
        }
        CdStageSelection row = selections.get(index);
        zone = row.zoneAct() >> 8;
        act = row.zoneAct() & 0xFF;
        timeZone = row.timeZone();
        goodFuture = row.goodFuture();
        route = Route.STAGE_SELECT;
        // StageSelect (IPX.asm:721-722): the special-stage row branches to the stage 1 demo, then rts to .GameLoop.
        if (row.command() == CdCommand.SPECSTAGE) {
            return new SpecialStage(CdSpecialStageEntry.demo(0), "SpecStage1Demo", new Title());
        }
        var stage = row.stage();
        if (stage.isPresent()) return new Stage(stage.get(), Route.STAGE_SELECT);
        // Warp, opening FMV and Comin' Soon rows run their program, then rts to .GameLoop.
        CdNativeProgram program = row.program().orElseThrow(() -> new IllegalStateException(
                "StageSels row " + index + " names neither a stage nor a program: " + row.command()));
        return notHosted(program, "StageSelect");
    }

    /**
     * {@code TimeAttack} (IPX.asm:1214-1238): the menu file returned {@code d0}, stored as
     * {@code timeAttackLevel}. Zero returns to {@code .GameLoop}; a special-stage entry runs
     * {@code TimeAttack_SS}; any other runs the {@code StageSels} row with {@code timeAttackMode} set.
     */
    public CdProgramRequest timeAttackSelected(int level) {
        if (level < 0 || level >= timeAttackSelections.length) throw new IllegalArgumentException("Time Attack level outside .Selections: " + level);
        timeAttackLevel = level;
        if (level == 0) return new Title();               // beq.w .End
        int selection = timeAttackSelections[level];      // move.b .Selections(pc,d0.w),d0
        if (selection < 0) {
            // TimeAttack_SS (IPX.asm:1287-1298): special stage -selection in time-attack mode, then the menu.
            return new NotHosted(CdNativeProgram.SPECSTAGE, "TimeAttack_SS", timeAttackMenu());
        }
        CdStageSelection row = selections.get(selection);
        zone = row.zoneAct() >> 8;
        act = row.zoneAct() & 0xFF;
        timeZone = row.timeZone();
        goodFuture = row.goodFuture();
        timeAttackMode = true;                            // move.b #1,timeAttackMode (IPX.asm:1230)
        route = Route.TIME_ATTACK;
        return new Stage(row.stage().orElseThrow(() -> new IllegalStateException("Time Attack row " + selection + " is not a stage")),
                Route.TIME_ATTACK);
    }

    /** The menu again: {@code bra.s TimeAttack}. */
    private static NotHosted timeAttackMenu() {
        return new NotHosted(CdNativeProgram.TIMEATK, "TimeAttack", new Title());
    }

    /** The stage program returned to IPX with these shared variables. */
    public CdProgramRequest stageEnded(CdStageResult result) {
        Objects.requireNonNull(result, "result");
        if (route == null) throw new IllegalStateException("No stage is running");
        // .CheckNoLives clears plcLoadFlags (Level/Main.asm:97-99); in Time Attack ObjResults_NextLevel
        // clears bit 0 too (Results/Main.asm:287-289).
        if (result.lives() == 0 || route == Route.TIME_ATTACK) levelLoaded = false;
        if (route == Route.TIME_ATTACK) {
            // move.b #0,checkpoint; move.l time,timeAttackTime; bra.s TimeAttack (IPX.asm:1235-1238).
            timeAttackTime = result.time();
            lives = result.lives();
            route = null;
            return timeAttackMenu();
        }
        goodFuture = result.goodFuture();
        timeStones = result.timeStones();
        if (route == Route.STAGE_SELECT) {
            // StageSelect (IPX.asm:726-727) runs the file once and returns: a warp, death or clear all reach the title.
            route = null;
            return new Title();
        }
        return act < 2 ? levelReturned(result) : bossLevelReturned(result);
    }

    /** RunLevel (IPX.asm:549-602), after the stage file returns. */
    private CdProgramRequest levelReturned(CdStageResult result) {
        if (result.lives() != 0 && result.timeWarp()) {
            // SCMD_WARP, then the file of the new period (IPX.asm:555-573).
            timeZone = result.timeZone() & ~CdStageResult.WARP_FLAG;
            return new NotHosted(CdNativeProgram.WARP, "RunLevel", new Stage(periodStage(), Route.CAMPAIGN));
        }
        if (result.lives() == 0) return gameOver();
        if (result.specialStage()) {
            // .SpecialStage (IPX.asm:586-591): curSpecStage and timeStones go to the program;
            // specialStageEnded applies what it returns, then RunLevel goes on to the next act.
            return new SpecialStage(CdSpecialStageEntry.normal(curSpecStage, timeStones), "RunLevel.SpecialStage", nextAct());
        }
        return nextAct();
    }

    /**
     * The special stage program returned. Normal mode saves the next stage and the stones
     * ({@code Results}: {@code move.b specStageID,curSpecStage}, {@code move.b timeStonesSub,timeStones},
     * Special Stage/Main.asm:295-296); with all seven, {@code .SpecialStage} sets the good future
     * (IPX.asm:593-597). Temporary and Time Attack runs change neither (they skip {@code Results},
     * Main.asm:239-241). {@code SoundTest_SpecStg8} runs the SS8 credits only when the stage was
     * won ({@code tst.b specStageLost}, IPX.asm:1072-1077).
     */
    public CdProgramRequest specialStageEnded(SpecialStage step, CdSpecialStageResult result) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(result, "result");
        if (result.showsResults()) {
            curSpecStage = result.nextStageId();
            timeStones = result.timeStones();
            if (timeStones == ALL_SEVEN) goodFuture = true;
        }
        if (step.nativeCaller().equals("SoundTest_SpecStg8") && result.ufosLeft() == 0) {
            return notHosted(CdNativeProgram.SS8CREDS, "SoundTest_SpecStg8");
        }
        return step.then();
    }

    /**
     * {@code SoundTest .Exits} (IPX.asm:1043-1049) for the sound-test owner: exit 1 runs Special
     * Stage 8 in temporary secret mode ({@code SoundTest_SpecStg8}, IPX.asm:1064-1071); the others
     * are not hosted here and return to {@code .GameLoop}.
     */
    public CdProgramRequest soundTestExited(int exitCode) {
        if (exitCode == 1) return new SpecialStage(CdSpecialStageEntry.secret(), "SoundTest_SpecStg8", new Title());
        // --- lane END: the hidden pictures (exits 2-6, IPX.asm:1083-1121) run, then .GameLoop.
        return switch (exitCode) {
            case 2 -> notHosted(CdNativeProgram.FUNISINF, "SoundTest_FunIsInf");
            case 3 -> notHosted(CdNativeProgram.MCSONIC, "SoundTest_MCSonic");
            case 4 -> notHosted(CdNativeProgram.TAILS, "SoundTest_Tails");
            case 5 -> notHosted(CdNativeProgram.BATMAN, "SoundTest_Batman");
            case 6 -> notHosted(CdNativeProgram.CUTESONIC, "SoundTest_CuteSonic");
            default -> new Title();                       // SoundTest_Exit: rts
        };
        // --- end lane END
    }

    /** RunBossLevel (IPX.asm:613-629) after the stage file returns; then the rest of RunR1 (IPX.asm:174-189) and its siblings. */
    private CdProgramRequest bossLevelReturned(CdStageResult result) {
        if (result.lives() == 0) return gameOver();
        int savedZone = (savedLevel >> 8) + 1;
        if (savedZone >= 7) savedZone--;                  // cmpi.b #7,savedLevel; bcs; subq (IPX.asm:622-625)
        savedLevel = (savedZone << 8) | (savedLevel & 0xFF);
        int unlock = 3 * (zone + 1);                      // moveq #3*n,d0; UnlockTimeAttackLevel (IPX.asm:638-644)
        if (unlock > timeAttackUnlock) timeAttackUnlock = unlock;
        if (zone == 0) titleFlags |= 0x60;                // bset #6/#5,titleFlags (IPX.asm:177-178)
        if (goodFuture) goodFutures |= 1 << zone;         // bclr #0,goodFuture; bset #n,goodFutures
        goodFuture = false;
        if (zone == 6) return gameDone();
        return runZone(zone + 1);
    }

    /** {@code GameDone} (IPX.asm:301-330): the ending program; both loop while it asks to replay. */
    private CdProgramRequest gameDone() {
        route = null;
        writeSaveData();                                  // GameDone (IPX.asm:301-305) writes the save first
        boolean good = goodFutures == ALL_SEVEN || timeStones == ALL_SEVEN;
        lastEndingId = good ? 0x7F : 0;
        // --- lane END: the ending program is hosted (ending/CdEndingProvider)
        return new CdProgramRequest.Ending(good, good ? "GoodEnding" : "BadEnding", new Title());
    }

    /**
     * The ending program returned its code, which {@code RunMMD} stores (IPX.asm:1372-1373): negative runs it again (the
     * return-code test after each ending, IPX.asm:316-317 and 324-325), else {@code rts} to {@code .GameLoop}.
     */
    public CdProgramRequest endingReturned(CdProgramRequest.Ending step, int mmdReturnCode) {
        Objects.requireNonNull(step, "step");
        return (byte) mmdReturnCode < 0 ? step : step.then();
    }
    // --- end lane END

    /** {@code GameOver} (IPX.asm:332-338): save the zone with act 0, then return to {@code .GameLoop}. */
    private CdProgramRequest gameOver() {
        savedLevel = zone << 8;
        goodFuture = false;
        route = null;
        return new Title();
    }

    private CdProgramRequest nextAct() {
        act++;
        if (act == 2) {
            // RunBossLevel (IPX.asm:606-611): good future file if goodFuture, else bad.
            return new Stage(rounds[zone][2][goodFuture ? 0 : 1], Route.CAMPAIGN);
        }
        timeZone = CdStageResult.TIME_PRESENT;            // RunLevel starts on the present file (IPX.asm:543-544)
        return new Stage(rounds[zone][act][0], Route.CAMPAIGN);
    }

    /** RunLevel's post-warp choice (IPX.asm:559-573). */
    private CdStagePayload periodStage() {
        CdStagePayload[] row = rounds[zone][act];
        return switch (timeZone) {
            case CdStageResult.TIME_PAST -> row[1];
            case CdStageResult.TIME_PRESENT -> row[0];
            default -> goodFuture ? row[2] : row[3];
        };
    }

    /** {@code NewGame}/{@code RunR1} (IPX.asm:162-174): clear the progress, write it, run act 1. */
    private CdProgramRequest newGame() {
        levelLoaded = false;                              // move.b d0,plcLoadFlags (IPX.asm:164)
        savedLevel = 0;
        goodFutures = 0;
        curSpecStage = 0;
        timeStones = 0;
        return runZone(0);
    }

    /**
     * {@code LoadGame} (IPX.asm:137-160): continue at the saved zone. Zone 0 branches to
     * {@code RunR1}, which is {@code NewGame} and clears the progress; a zone above 6 falls through
     * the compare chain into {@code NewGame} as well.
     */
    private CdProgramRequest loadGame() {
        readSaveData();                                   // LoadGame (IPX.asm:137-138) reads the save first
        levelLoaded = false;                              // move.b #0,plcLoadFlags (IPX.asm:141)
        int saved = savedLevel >> 8;
        return saved == 0 || saved > 6 ? newGame() : runZone(saved);
    }

    /** {@code RunR1}..{@code RunR8}: WriteSaveData, then act 1 Present. */
    private CdProgramRequest runZone(int nextZone) {
        writeSaveData();                                  // RunR1 (IPX.asm:172), RunR3..RunR8 (IPX.asm:192, 212, 230, 248, 266, 284)
        zone = nextZone;
        act = 0;
        timeZone = CdStageResult.TIME_PRESENT;
        route = Route.CAMPAIGN;
        return new Stage(rounds[zone][0][0], Route.CAMPAIGN);
    }

    /**
     * {@code Demo} (IPX.asm:929-955): run demo {@code demoID}, then advance and wrap it. The two
     * special-stage demos run {@code SpecStage1Demo}/{@code SpecStage6Demo} (IPX.asm:111-131).
     */
    private CdProgramRequest demo() {
        int id = demoId;
        demoId = demoId + 1 > DEMOS.length - 1 ? 0 : demoId + 1;
        if (DEMOS[id] == CdNativeProgram.SPECSTAGE) {
            return new SpecialStage(CdSpecialStageEntry.demo(id == 2 ? 0 : 5), id == 2 ? "SpecStage1Demo" : "SpecStage6Demo", new Title());
        }
        return notHosted(DEMOS[id], "Demo");
    }

    private static NotHosted notHosted(CdNativeProgram program, String caller) {
        return new NotHosted(program, caller, new Title());
    }

    private static CdStagePayload[][][] rounds(byte[] table) {
        if (table.length != 7 * (4 + 4 + 2)) throw new IllegalArgumentException("SubCmds table is 70 bytes");
        CdStagePayload[][][] out = new CdStagePayload[7][3][];
        int at = 0;
        for (int z = 0; z < 7; z++) {
            for (int a = 0; a < 3; a++) {
                int count = a < 2 ? 4 : 2;
                out[z][a] = new CdStagePayload[count];
                for (int i = 0; i < count; i++) {
                    CdCommand command = CdCommand.fromId(table[at++] & 0xFF);
                    out[z][a][i] = CATALOGUE.stageForCommand(command).orElseThrow(() ->
                            new IllegalStateException("SubCmds entry is not a stage: " + command));
                }
            }
        }
        return out;
    }

    // Read-only view for the title and tests.
    public int titleFlags() { return titleFlags; }
    public int savedLevel() { return savedLevel; }
    public int goodFutures() { return goodFutures; }
    public int timeAttackUnlock() { return timeAttackUnlock; }
    public int timeZone() { return timeZone; }
    public boolean goodFuture() { return goodFuture; }
    public int zone() { return zone; }
    public int act() { return act; }
    public Route route() { return route; }
    public int demoId() { return demoId; }
    /** Time Attack variables and the running route, for gameplay rewind (lane TA). */
    public record TimeAttackState(boolean mode, int time, int level, int lives, Route route, int zone, int act,
                                  int timeZone, boolean goodFuture, int titleFlags) { }

    public TimeAttackState timeAttackState() {
        return new TimeAttackState(timeAttackMode, timeAttackTime, timeAttackLevel, lives, route, zone, act, timeZone, goodFuture, titleFlags);
    }

    public void restoreTimeAttackState(TimeAttackState s) {
        timeAttackMode = s.mode();
        timeAttackTime = s.time();
        timeAttackLevel = s.level();
        lives = s.lives();
        route = s.route();
        zone = s.zone();
        act = s.act();
        timeZone = s.timeZone();
        goodFuture = s.goodFuture();
        titleFlags = s.titleFlags();
    }

    public boolean timeAttackMode() { return timeAttackMode; }
    public int timeAttackTime() { return timeAttackTime; }
    public int timeAttackLevel() { return timeAttackLevel; }
    /** {@code lives} as the last Time Attack stage returned it. */
    public int timeAttackLives() { return lives; }
    /** The Time Attack menu's own {@code titleFlags} unlocks ({@code PrepareTimeAttackRecords}, Time Attack/Main.asm:1445-1469). */
    public void setTitleFlags(int flags) { titleFlags = flags & 0xFF; }
    public int endingId() { return lastEndingId; }
    public int currentSpecialStage() { return curSpecStage; }
    /** {@code timeStones} as IPX holds it (Main CPU Variables.inc). */
    public int timeStones() { return timeStones; }

    /**
     * A stage's {@code LevelStart} begins: true when {@code plcLoadFlags} bit 0 was clear, so it resets
     * lives (3, or 1 in Time Attack) and score (Level/Main.asm:39-55); the bit is then set.
     */
    public boolean levelStartResetsProgress() {
        boolean first = !levelLoaded;
        levelLoaded = true;
        return first;
    }
    public List<CdStageSelection> selections() { return selections; }
    public CdStagePayload roundStage(int zoneIndex, int actIndex, int slot) { return rounds[zoneIndex][actIndex][slot]; }
}
