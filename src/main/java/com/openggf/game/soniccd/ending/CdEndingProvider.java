package com.openggf.game.soniccd.ending;

import com.openggf.control.InputHandler;
import com.openggf.game.EndingPhase;
import com.openggf.game.EndingProvider;
import com.openggf.game.GameServices;
import com.openggf.game.internal.EndingEntryPresentation;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.audio.SonicCdAudioProfile;
import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.title.CdTitleScreen;

import java.util.Objects;

/**
 * The campaign's ending in {@code ENDING_CUTSCENE}: after the final act IPX's {@code GameDone} picks the good or
 * bad ending (IPX.asm:301-326) and the coordinator asks for the credits transition; this provider runs
 * {@link CdEndingProgram} for {@code flow.endingId()}, again while its return code is negative, and is complete
 * when IPX returns to {@code .GameLoop}. The video and credits run as {@link EndingPhase#CUTSCENE}; the final image,
 * which reads Start, as {@link EndingPhase#POST_CREDITS}. The program owns its own fades, so the engine's entry
 * fade is skipped.
 */
public final class CdEndingProvider implements EndingProvider, EndingEntryPresentation {
    private final CdSceneCoordinator coordinator;
    private final CdProgramScreenRenderer renderer = new CdProgramScreenRenderer();
    private CdEndingProgram program;
    private CdSubCpuAudio audio;
    private boolean complete;
    private int pad;

    public CdEndingProvider(CdSceneCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override public boolean ownsEntryPresentation() { return true; }

    @Override
    public void initialize() {
        CdProgramRequest.Ending step = coordinator.pendingEnding();
        boolean good = step != null ? step.good() : coordinator.flow().endingId() == 0x7F;
        var manager = GameServices.audio();
        audio = manager != null && manager.getAudioProfile() instanceof SonicCdAudioProfile cd ? cd.subCpuAudio() : null;
        start(good);
    }

    private void start(boolean good) {
        CdSubCpuAudio.ProgramPcm pcm = audio != null ? audio.beginProgramPcm() : CdSubCpuAudio.ProgramPcm.detached();
        program = CdEndings.program(new CdFmvAssets(coordinator.data()), coordinator.disc().assets(), good, pcm,
                com.openggf.game.soniccd.CdLoadTiming.programTiming());
        renderer.reset();
        complete = false;
    }

    @Override
    public void update() {
        if (program == null || complete) return;
        program.frame(pad);
        if (!program.done()) return;
        if (audio != null) audio.endProgramPcm();
        if (coordinator.pendingEnding() == null) { complete = true; return; }
        CdProgramRequest next = coordinator.endingReturned(program.returnCode());
        if (next instanceof CdProgramRequest.Ending again) start(again.good());
        else complete = true;
    }

    @Override
    public void updatePostCredits(InputHandler input) {
        pad = CdTitleScreen.pad(input.logical().player1());
        update();
    }

    @Override public void draw() { if (program != null) renderer.draw(program.screen().frame()); }

    @Override public void setClearColor() { if (program != null) renderer.setClearColor(program.screen().frame()); }

    @Override
    public EndingPhase getCurrentPhase() {
        if (complete) return EndingPhase.FINISHED;
        return program != null && program.inImage() ? EndingPhase.POST_CREDITS : EndingPhase.CUTSCENE;
    }

    @Override public boolean isComplete() { return complete; }

    /** The running program, for tests. */
    public CdEndingProgram program() { return program; }
}
