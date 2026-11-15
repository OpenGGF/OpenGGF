package com.openggf.game.soniccd.timeattack;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.backupram.CdBackupRam;
import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.program.CdProgramFlow;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.title.CdTitleScreen;

import java.util.List;

/**
 * Hosts the Time Attack menu in the {@code OPTIONS} native-program mode. It runs
 * {@link CdTimeAttackProgram} against the session's backup RAM and IPX variables, and when the menu
 * returns its level it reports it to the {@link CdSceneCoordinator}, which decides the next program; the
 * screen then exits to the title host, which starts the stage (or the title) without running the title.
 */
public final class CdTimeAttackScreen implements NativeMenuScreen {
    private final CdSceneCoordinator coordinator;
    private final CdProgramScreenRenderer renderer = new CdProgramScreenRenderer();
    private CdTimeAttackAssets assets;
    private CdTimeAttackProgram program;
    private boolean exiting;
    private List<CdTimeAttackProgram.AudioRequest> audio = List.of();

    public CdTimeAttackScreen(CdSceneCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override public GameMode mode() { return GameMode.OPTIONS; }

    @Override
    public void initialize() {
        if (assets == null) assets = new CdTimeAttackAssets(coordinator.data());
        if (program != null) program.close();
        CdProgramFlow flow = coordinator.flow();
        CdBackupRam ram = flow.backupRam();
        CdTimeAttackProgram.Saves saves = ram == null ? sessionSaves() : new CdTimeAttackProgram.Saves() {
            @Override public CdSaveRecord read() { return ram.readSave(); }
            @Override public void write(CdSaveRecord record) { ram.writeSave(record); }
        };
        program = new CdTimeAttackProgram(assets, saves, new CdTimeAttackProgram.Host() {
            @Override public int timeAttackLevel() { return flow.timeAttackLevel(); }
            @Override public int timeAttackTime() { return flow.timeAttackTime(); }
            @Override public int lives() { return flow.timeAttackLives(); }
            @Override public int timeAttackUnlock() { return flow.timeAttackUnlock(); }
            @Override public int titleFlags() { return flow.titleFlags(); }
            @Override public void setTitleFlags(int flags) { flow.setTitleFlags(flags); }
        });
        renderer.reset();
        exiting = false;
    }

    private static CdTimeAttackProgram.Saves sessionSaves() {
        throw new IllegalStateException("The Time Attack menu needs the session's backup RAM");
    }

    @Override
    public void update(InputHandler input) {
        if (program == null || exiting) return;
        program.frame(CdTitleScreen.pad(input.logical().player1()));
        audio = program.drainAudio();
        if (program.done()) {
            coordinator.timeAttackSelected(program.exitLevel());
            exiting = true;
        }
    }

    /** The audio requests of the last frame, by ID. */
    public List<CdTimeAttackProgram.AudioRequest> audio() { return audio; }

    @Override public void draw() { if (program != null) renderer.draw(program.screen()); }

    @Override public void setClearColor() { if (program != null) renderer.setClearColor(program.screen()); }

    @Override
    public void reset() {
        if (program != null) program.close();
        program = null;
        exiting = false;
    }

    @Override
    public Exit consumeExit() {
        if (!exiting) return Exit.NONE;
        exiting = false;
        if (program != null) program.close();
        program = null;
        return new Exit(Destination.TITLE_SCREEN, null, false);
    }

    /** The running program, for tests. */
    public CdTimeAttackProgram program() { return program; }
}
