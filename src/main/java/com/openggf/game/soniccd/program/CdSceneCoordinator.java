package com.openggf.game.soniccd.program;

import com.openggf.game.GameOverExit;
import com.openggf.game.TitleScreenProvider.TitleScreenAction;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.game.soniccd.backupram.CdBackupRam;
import com.openggf.game.soniccd.backupram.CdBackupRamAssets;
import com.openggf.game.soniccd.backupram.CdBackupRamHost;
import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.SpecialStageEntryRequest;
import com.openggf.game.soniccd.program.CdProgramRequest.NotHosted;
import com.openggf.game.soniccd.program.CdProgramRequest.SpecialStage;
import com.openggf.game.soniccd.special.CdSpecialStageEntry;
import com.openggf.game.soniccd.special.CdSpecialStageResult;
import com.openggf.game.soniccd.program.CdProgramRequest.Stage;
import com.openggf.game.soniccd.program.CdProgramRequest.StageSelect;
import com.openggf.game.soniccd.program.CdProgramRequest.Title;
import com.openggf.level.LevelManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Owns the flow between Sonic CD programs. Program owners (title, stage select, the stage-ending
 * owners) report how their program returned; {@link CdProgramFlow} decides the next program; this
 * class turns that into the engine request that commits it. It never writes the game mode: a
 * title exit becomes a {@link TitleScreenAction} the host routes, a stage exit becomes a
 * {@link LevelManager} transition request that {@code GameLoop} consumes. Programs this build does
 * not host are recorded as typed {@link NotHosted} steps and skipped to where native continues.
 */
public final class CdSceneCoordinator {
    private final Supplier<CdDiscDataSource> disc;
    private CdProgramData data;
    private CdProgramFlow flow;
    private final List<NotHosted> notHosted = new ArrayList<>();
    private CdProgramRequest titleRequest;
    private final List<String> route = new ArrayList<>();
    // Lane TA: the session's backup RAM and the program loops IPX runs without .GameLoop.
    private CdBackupRamHost backupRamHost = CdBackupRamHost.SESSION;
    private int ramCartridgeBytes;
    /** A title action the next title start returns at once (TimeAttack loops, IPX.asm:1214-1238). */
    private TitleScreenAction resume;
    private java.util.function.Consumer<CdSpecialStageEntry> specialStagePrimer = entry -> { };
    private SpecialStage pendingSpecialStage;
    // Lane FLOW2: the time owner, the stage IPX runs after a hosted program (the warp cutscene), and the
    // campaign values a stage start through the title host keeps.
    private java.util.function.Supplier<com.openggf.game.soniccd.timetravel.CdTimeTravel> timeTravel = () -> null;
    private Stage afterProgram;
    private CarriedProgress carried;
    private boolean passedGameLoop = true;
    private Object reportedWarp;
    private final CdScorePolicy scorePolicy = new CdScorePolicy(id -> com.openggf.game.GameServices.audio().playMusic(id));

    /** {@code lives} and {@code score} as the last stage program left them (Main CPU Variables.inc). */
    private record CarriedProgress(int lives, int score) { }

    /** The special-stage provider receives each entry before the engine enters {@code SPECIAL_STAGE}. */
    public void bindSpecialStage(java.util.function.Consumer<CdSpecialStageEntry> primer) {
        this.specialStagePrimer = Objects.requireNonNull(primer, "primer");
    }

    /** The campaign time owner: stage entries, the warp destination and the Time Stones it receives. */
    public void bindTimeTravel(java.util.function.Supplier<com.openggf.game.soniccd.timetravel.CdTimeTravel> owner) {
        this.timeTravel = Objects.requireNonNull(owner, "owner");
    }

    public CdSceneCoordinator(Supplier<CdDiscDataSource> disc) {
        this.disc = Objects.requireNonNull(disc, "disc");
    }

    /** The session's disc. */
    public CdDiscDataSource disc() {
        CdDiscDataSource source = disc.get();
        if (source == null) throw new IllegalStateException("Sonic CD programs need the disc");
        return source;
    }

    public CdProgramData data() {
        CdDiscDataSource source = disc.get();
        if (source == null) throw new IllegalStateException("Sonic CD programs need the disc");
        if (data == null) data = new CdProgramData(source);
        return data;
    }

    public CdProgramFlow flow() {
        if (flow == null) {
            flow = CdProgramFlow.read(data());
            // IPX Start (IPX.asm:41-48): BRAMINIT, then ReadSaveData.
            flow.attachBackupRam(CdBackupRam.open(CdBackupRamAssets.initialRecord(disc.get()), backupRamHost, ramCartridgeBytes));
        }
        return flow;
    }

    /**
     * Where the backup RAM persists, and the size of an attached RAM cartridge (0 for none). The default
     * keeps it for the session only; an interactive host attaches {@link CdBackupRamHost#durable} before
     * the first program runs.
     */
    public void attachBackupRamHost(CdBackupRamHost host, int cartridgeBytes) {
        if (flow != null) throw new IllegalStateException("The backup RAM is already open");
        backupRamHost = Objects.requireNonNull(host, "host");
        ramCartridgeBytes = cartridgeBytes;
    }

    /** The title program is starting: the {@code .GameLoop} resets, unless a program loop is resuming. */
    public void titleStarting() {
        if (resume != null) return;
        flow().enterTitle();
        titleRequest = null;
        afterProgram = null;
        passedGameLoop = true;                            // .GameLoop: move.l d0,score (IPX.asm:53-55)
    }

    /**
     * The action a starting title returns at once without running, or null: a program IPX loops into
     * without passing {@code .GameLoop} (the Time Attack menu after its stage, its stage after the menu).
     */
    public TitleScreenAction consumeResume() {
        TitleScreenAction action = resume;
        resume = null;
        return action;
    }

    /** The Time Attack menu returned {@code timeAttackLevel} (IPX.asm:1214-1238); its screen then exits to the title host. */
    public void timeAttackSelected(int level) {
        route.add("time attack " + level);
        int before = notHosted.size();
        CdProgramRequest request = skipNotHosted(flow().timeAttackSelected(level));
        route.add("-> " + request);
        stageStartedByIpx(request);
        titleRequest = request;
        resume = switch (request) {
            case Stage ignored -> TitleScreenAction.ONE_PLAYER;
            case Title ignored -> resumesProgram(before) ? TitleScreenAction.OPTIONS : null;
            default -> throw new IllegalStateException("Time Attack never returns " + request);
        };
    }

    /** The backup-RAM manager returned (IPX.asm:101-105). */
    public void backupRamManagerReturned() {
        route.add("backup RAM manager returned");
        flow().backupRamManagerReturned();
    }

    /** The last program stepped over since {@code before} has a host: IPX runs it next, not {@code .GameLoop}. */
    private boolean resumesProgram(int before) {
        if (notHosted.size() == before) return false;
        NotHosted step = notHosted.get(notHosted.size() - 1);
        for (CdProgramHost host : CdProgramHosts.HOSTS) if (host.programs().contains(step.program())) return true;
        return false;
    }

    /**
     * Backup-RAM device state and Time Attack state for gameplay rewind; a restore never writes the host file.
     * One instance per coordinator: the rewind registry refreshes adapters by identity.
     */
    public com.openggf.game.rewind.RewindSnapshottable<?> rewindAdapter() { return rewindAdapter; }

    private record RewindState(CdBackupRam.Snapshot backupRam, CdProgramFlow.TimeAttackState timeAttack, int nextLifeScore) { }

    private final com.openggf.game.rewind.RewindSnapshottable<RewindState> rewindAdapter = newRewindAdapter();

    private com.openggf.game.rewind.RewindSnapshottable<RewindState> newRewindAdapter() {
        return new com.openggf.game.rewind.RewindSnapshottable<RewindState>() {
            @Override public String key() { return "soniccd.programs"; }

            @Override public RewindState capture() {
                if (flow == null) return new RewindState(null, null, scorePolicy.nextLifeScore());
                return new RewindState(flow.backupRam() == null ? null : flow.backupRam().rewindAdapter().capture(), flow.timeAttackState(),
                        scorePolicy.nextLifeScore());
            }

            @Override public void restore(RewindState state) {
                scorePolicy.restore(state.nextLifeScore());
                if (flow == null || state.timeAttack() == null) return;
                if (state.backupRam() != null && flow.backupRam() != null) flow.backupRam().rewindAdapter().restore(state.backupRam());
                flow.restoreTimeAttackState(state.timeAttack());
            }
        };
    }

    /**
     * The title program returned exit code {@code d1}. Returns the host action; a stage start reads
     * its zone and act from {@link #titleStartZone()}/{@link #titleStartAct()}.
     */
    public TitleScreenAction titleExited(int exitCode) {
        route.add("title exit " + exitCode);
        CdProgramRequest request = skipNotHosted(flow().titleExited(exitCode));
        titleRequest = request;
        route.add("-> " + request);
        stageStartedByIpx(request);
        if (request instanceof SpecialStage step) {
            // The attract special-stage demos start from the title; the engine has no title-to-
            // SPECIAL_STAGE action, so they are still recorded as not hosted (see the program contract).
            request = skipNotHosted(new NotHosted(com.openggf.game.soniccd.CdNativeProgram.SPECSTAGE,
                    step.nativeCaller(), step.then()));
            titleRequest = request;
        }
        return switch (request) {
            case Stage ignored -> TitleScreenAction.ONE_PLAYER;
            case StageSelect ignored -> TitleScreenAction.LEVEL_SELECT;
            // Unhosted programs that return to .GameLoop: the host opens the not-hosted screen,
            // which hands back to the title.
            case Title ignored -> TitleScreenAction.OPTIONS;
            case NotHosted ignored -> throw new IllegalStateException("not-hosted steps are skipped");
            case SpecialStage ignored -> throw new IllegalStateException("title special stages are recorded above");
            case CdProgramRequest.Ending ignored -> throw new IllegalStateException("IPX runs the ending only from GameDone"); // lane END
        };
    }

    public int titleStartZone() { return titleRequest instanceof Stage s ? SonicCdZoneRegistry.zoneIndex(s.payload()) : 0; }

    public int titleStartAct() { return titleRequest instanceof Stage s ? SonicCdZoneRegistry.actIndex(s.payload()) : 0; }

    /** The pending title request, for tests and the not-hosted screen. */
    public CdProgramRequest titleRequest() { return titleRequest; }

    /**
     * STSEL_.MMD returned {@code index}. A stage is returned for the host's level load; anything
     * else returns empty after recording the not-hosted program (the stage select then restarts,
     * see the program contract).
     */
    public java.util.Optional<Stage> stageSelected(int index) {
        route.add("stage select " + index);
        CdProgramRequest request = skipNotHosted(flow().stageSelected(index));
        route.add("-> " + request);
        stageStartedByIpx(request);
        if (request instanceof SpecialStage step) {
            pendingSpecialStage = step;
            specialStagePrimer.accept(step.entry());
        }
        return request instanceof Stage stage ? java.util.Optional.of(stage) : java.util.Optional.empty();
    }

    /**
     * The stage-select row that runs the special stage ({@code SpecStage1Demo}): true once
     * {@link #stageSelected} primed the provider, for the stage-select screen's
     * {@code isSpecialStageSelected}.
     */
    public boolean stageSelectChoseSpecialStage() { return pendingSpecialStage != null; }

    /**
     * A stage owner (results, time warp, game over) reports that the stage program returned.
     * The next stage is requested through {@link LevelManager#requestZoneAndAct}; a return to
     * the title through {@link LevelManager#requestGameOverExit} with the title exit.
     */
    public CdProgramRequest stageEnded(CdStageResult result, LevelManager level) {
        route.add("stage ended " + result);
        int before = notHosted.size();
        CdProgramRequest request = skipNotHosted(flow().stageEnded(result));
        route.add("-> " + request);
        switch (request) {
            case Stage stage when resumesProgram(before) -> {
                // RunLevel (IPX.asm:555-573): a hosted program (the warp cutscene) runs first; the title
                // host resumes into its OPTIONS screen, which hands back through programReturned.
                carry(result.lives());
                afterProgram = stage;
                resume = TitleScreenAction.OPTIONS;
                level.requestGameOverExit(GameOverExit.TITLE_SCREEN);
            }
            // The level stops before LevelScroll on the levelRestart frame (Level/Main.asm:405-408).
            case Stage stage -> level.requestZoneAndAct(SonicCdZoneRegistry.zoneIndex(stage.payload()),
                    SonicCdZoneRegistry.actIndex(stage.payload()), true);
            case Title ignored -> {
                // A Time Attack stage returns to its menu, which the title host resumes into.
                carry(result.lives());
                if (resumesProgram(before)) resume = TitleScreenAction.OPTIONS;
                level.requestGameOverExit(GameOverExit.TITLE_SCREEN);
            }
            case StageSelect ignored -> throw new IllegalStateException("IPX never returns from a stage to STSEL");
            case NotHosted ignored -> throw new IllegalStateException("not-hosted steps are skipped");
            // --- lane END: GameDone's ending runs in ENDING_CUTSCENE through the module's EndingProvider.
            case CdProgramRequest.Ending step -> {
                pendingEnding = step;
                level.requestCreditsTransition();
            }
            // --- end lane END
            case SpecialStage step -> {
                // RunLevel .SpecialStage (IPX.asm:586-591): run SCMD_SPECSTAGE with this entry.
                pendingSpecialStage = step;
                specialStagePrimer.accept(step.entry());
                level.requestSpecialStageEntry(new SpecialStageEntryRequest(step.entry().stageId(),
                        EmeraldRewardKind.CHAOS_EMERALD));
            }
        }
        return request;
    }

    /**
     * The special stage program returned ({@code RunMMD} of {@code SCMD_SPECSTAGE} returns to its
     * IPX caller). The flow applies the result and names what runs next; a stage is requested
     * through {@link LevelManager#requestZoneAndAct}, the title through the game-over exit.
     */
    public CdProgramRequest specialStageEnded(CdSpecialStageResult result, LevelManager level) {
        if (pendingSpecialStage == null) throw new IllegalStateException("No special stage is running");
        route.add("special stage ended " + result);
        SpecialStage step = pendingSpecialStage;
        pendingSpecialStage = null;
        CdProgramRequest request = skipNotHosted(flow().specialStageEnded(step, result));
        route.add("-> " + request);
        // .SpecialStage's Results wrote timeStones (Special Stage/Main.asm:295-296): the time owner's input.
        var tt = timeTravel.get();
        if (tt != null && result.showsResults()) tt.setTimeStones(flow().timeStones());
        if (level != null) {
            switch (request) {
                case Stage stage -> level.requestZoneAndAct(SonicCdZoneRegistry.zoneIndex(stage.payload()),
                        SonicCdZoneRegistry.actIndex(stage.payload()));
                case Title ignored -> level.requestGameOverExit(GameOverExit.TITLE_SCREEN);
                default -> throw new IllegalStateException("IPX continues with a stage or the title: " + request);
            }
        }
        return request;
    }

    /**
     * A hosted program screen IPX runs between two stages returned ({@code RunMMD} of {@code SCMD_WARP}
     * returns into {@code RunLevel .LevelLoop}, IPX.asm:560-573). The title host resumes into the stage
     * the flow chose; for a warp the time owner's destination is the arrival ({@link
     * com.openggf.game.soniccd.timetravel.CdWarpTransition}: {@code prepareEntry(WARP_ARRIVAL)}) and must be
     * the same file as IPX's choice. Returns the stage, or null when IPX continues at {@code .GameLoop}.
     */
    public Stage programReturned() {
        Stage stage = afterProgram;
        afterProgram = null;
        if (stage == null) return null;
        route.add("program returned -> " + stage);
        var tt = timeTravel.get();
        var warp = tt == null ? null : tt.readyWarp();
        if (warp != null) {
            var destination = warp.destination(tt.goodFuture());
            if (destination != stage.payload()) {
                throw new IllegalStateException("IPX loads " + stage.payload() + " but the warp's destination is " + destination);
            }
            tt.prepareEntry(com.openggf.game.soniccd.timetravel.CdTimeTravel.Entry.WARP_ARRIVAL);
        }
        titleRequest = stage;
        resume = TitleScreenAction.ONE_PLAYER;
        return stage;
    }

    /** True once per ready warp: the stage is left on the frame {@code levelRestart} is first seen. */
    public boolean warpLeavesStage(Object warp) {
        if (warp == null || warp == reportedWarp) return false;
        reportedWarp = warp;
        return true;
    }

    /** {@code AddPoints}' cap and extra life ({@link CdScorePolicy}). */
    public CdScorePolicy scorePolicy() { return scorePolicy; }

    /** The stage IPX runs once the hosted program screen returns, or null. */
    public Stage afterProgram() { return afterProgram; }

    /** {@code lives} and {@code score} as the stage left them, for a stage start through the title host. */
    private void carry(int lives) {
        var game = com.openggf.game.GameServices.gameStateOrNull();
        carried = new CarriedProgress(lives, game == null ? 0 : game.getScore());
    }

    /**
     * A stage starts through the title or stage-select host, which resets the engine's lives and score
     * first. {@code LevelStart} resets them only on a run's first load ({@code bset #0,plcLoadFlags},
     * {@code move.b #3,lives} or 1 in Time Attack, {@code clr.l score}, Level/Main.asm:39-55); otherwise
     * lives carry from the last stage and score from it unless {@code .GameLoop} cleared it since
     * (IPX.asm:53-55). The module calls this from {@code onNewGameFromTitle}.
     */
    public void stageStartingFromHost(com.openggf.game.GameStateManager game) {
        boolean first = flow().levelStartResetsProgress();
        int lives;
        int score;
        if (first || carried == null) {
            lives = flow().timeAttackMode() ? 1 : 3;
            score = 0;
            scorePolicy.resetForRun();
        } else {
            lives = carried.lives();
            score = passedGameLoop ? 0 : carried.score();
        }
        passedGameLoop = false;
        carried = null;
        var s = game.capture();
        game.restore(new com.openggf.game.rewind.snapshot.GameStateSnapshot(score, lives, s.continues(),
                s.currentSpecialStageIndex(), s.emeraldCount(), s.gotEmeralds(), s.gotSuperEmeralds(),
                s.emeraldsConverted(), s.currentBossId(), s.bossDefeatedFlag(), s.screenShakeActive(),
                s.backgroundCollisionFlag(), s.bigRingCollected(), s.wfzFireToggle(), s.itemBonus(),
                s.reverseGravityActive(), s.collectedSpecialRings(), s.endOfLevelActive(), s.endOfLevelFlag(),
                s.screenLocked()));
    }

    /**
     * IPX set {@code zoneAct}, {@code timeZone} and {@code goodFuture} for a stage it starts from the title,
     * stage select or Time Attack menu, and cleared {@code projDestroyed} (IPX.asm:50-68, 713-720): the time
     * owner's {@code STAGE_SELECT} entry, named so a game over's stale payload is not read as a restart.
     */
    private void stageStartedByIpx(CdProgramRequest request) {
        var tt = timeTravel.get();
        if (tt != null && request instanceof Stage) tt.prepareEntry(com.openggf.game.soniccd.timetravel.CdTimeTravel.Entry.STAGE_SELECT);
    }

    /** Record each unhosted program and continue where native continues after it returns. */
    private CdProgramRequest skipNotHosted(CdProgramRequest request) {
        while (request instanceof NotHosted step) {
            notHosted.add(step);
            route.add("not hosted " + step.program() + " from " + step.nativeCaller());
            request = step.then();
        }
        return request;
    }

    // --- lane END: the sound test's exit
    /**
     * The sound test returned exit code {@code d0} ({@code SoundTest .Exits}, IPX.asm:1035-1049). A hidden picture
     * has a host, which the title host resumes into; Special Stage 8 has no route from the native-program host to
     * {@code SPECIAL_STAGE}, so it is recorded as not hosted (as the attract special-stage demos are).
     */
    public void soundTestExited(int exitCode) {
        route.add("sound test exit " + exitCode);
        int before = notHosted.size();
        CdProgramRequest request = flow().soundTestExited(exitCode);
        if (request instanceof SpecialStage step) {
            request = new NotHosted(com.openggf.game.soniccd.CdNativeProgram.SPECSTAGE, step.nativeCaller(), step.then());
        }
        request = skipNotHosted(request);
        route.add("-> " + request);
        titleRequest = request;
        if (resumesProgram(before)) resume = TitleScreenAction.OPTIONS;
    }

    // --- lane END: the ending's coordinator calls
    private CdProgramRequest.Ending pendingEnding;

    /** The ending {@code GameDone} asked for (good or bad), or null when none is pending. */
    public CdProgramRequest.Ending pendingEnding() { return pendingEnding; }

    /** The ending program returned {@code mmdReturnCode}: run it again, or go on as IPX does. */
    public CdProgramRequest endingReturned(int mmdReturnCode) {
        if (pendingEnding == null) throw new IllegalStateException("No ending is running");
        route.add("ending returned " + mmdReturnCode);
        CdProgramRequest next = flow().endingReturned(pendingEnding, mmdReturnCode);
        pendingEnding = next instanceof CdProgramRequest.Ending again ? again : null;
        route.add("-> " + next);
        return next;
    }
    // --- end lane END

    /** The program decisions of this session, in order: returns, requests and not-hosted steps. */
    public List<String> route() { return List.copyOf(route); }

    /**
     * The screen the native-program host ({@code OPTIONS}) opens after a title exit: the registered
     * host of the program IPX would run, else {@link CdNotHostedScreen}.
     */
    public com.openggf.game.nativemenu.NativeMenuScreen programScreen() {
        lastProgramScreen = new CdNotHostedScreen(this);
        if (!notHosted.isEmpty()) {
            NotHosted step = notHosted.get(notHosted.size() - 1);
            for (CdProgramHost host : CdProgramHosts.HOSTS) {
                if (host.programs().contains(step.program())) lastProgramScreen = host.screen(step, this);
            }
        }
        return lastProgramScreen;
    }

    private com.openggf.game.nativemenu.NativeMenuScreen lastProgramScreen;

    /** The screen {@link #programScreen} last opened, for tests that draw it. */
    public com.openggf.game.nativemenu.NativeMenuScreen lastProgramScreen() { return lastProgramScreen; }

    /** Every unhosted program the flow has stepped over this session, in order. */
    public List<NotHosted> notHosted() { return List.copyOf(notHosted); }
}
