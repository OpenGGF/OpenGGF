package com.openggf.game.soniccd.title;

import com.openggf.control.InputHandler;
import com.openggf.game.LevelSelectProvider;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdSceneCoordinator;

import java.util.Objects;

/**
 * Hosts the stage-select secret ({@link CdStageSelectProgram}) in {@code LEVEL_SELECT}. A stage
 * row exits to the host's level load; the special-stage row runs {@code SpecStage1Demo} through the
 * host's special-stage exit (IPX.asm:721-722); a row naming another program (warp, opening FMV,
 * Comin' Soon) is recorded as not hosted by the coordinator and returns to the title, where IPX's
 * {@code rts} goes back to {@code .GameLoop} (IPX.asm:710-728).
 */
public final class CdStageSelectScreen implements LevelSelectProvider,
        com.openggf.game.internal.TitleReturningLevelSelect {
    private final CdSceneCoordinator coordinator;
    private final CdProgramScreenRenderer renderer = new CdProgramScreenRenderer();
    private CdStageSelectProgram program;
    private CdProgramRequest.Stage selected;
    private boolean specialStage;
    private boolean title;
    private State state = State.INACTIVE;

    public CdStageSelectScreen(CdSceneCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void initialize() {
        program = new CdStageSelectProgram(coordinator.data());
        renderer.reset();
        selected = null;
        specialStage = false;
        title = false;
        state = State.ACTIVE;
    }

    @Override
    public void update(InputHandler input) {
        if (state == State.INACTIVE) initialize();
        if (state != State.ACTIVE) return;
        program.frame(CdTitleScreen.pad(input.logical().player1()));
        if (!program.done()) return;
        var stage = coordinator.stageSelected(program.selection());
        selected = stage.orElse(null);
        specialStage = coordinator.stageSelectChoseSpecialStage();
        title = selected == null && !specialStage;
        state = State.EXITING;
    }

    @Override public void draw() { if (program != null) renderer.draw(program.screen()); }
    @Override public void setClearColor() { if (program != null) renderer.setClearColor(program.screen()); }

    @Override
    public void reset() {
        state = State.INACTIVE;
        program = null;
    }

    @Override public State getState() { return state; }
    @Override public boolean isExiting() { return state == State.EXITING; }
    @Override public boolean isActive() { return state != State.INACTIVE; }
    @Override public boolean isSpecialStageSelected() { return specialStage; }
    @Override public boolean returnsToTitle() { return title; }
    @Override public boolean isSoundTestSelected() { return false; }
    @Override public int getSelectedZone() { return selected == null ? 0 : SonicCdZoneRegistry.zoneIndex(selected.payload()); }
    @Override public int getSelectedAct() { return selected == null ? 0 : SonicCdZoneRegistry.actIndex(selected.payload()); }
    @Override public int getSelectedZoneAct() { return (getSelectedZone() << 8) | getSelectedAct(); }
    @Override public int getSelectedIndex() { return program == null ? 0 : program.selection(); }
    @Override public int getSoundTestValue() { return 0; }

    public CdStageSelectProgram program() { return program; }
    public CdProgramRequest.Stage selected() { return selected; }
}
