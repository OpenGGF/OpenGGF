package com.openggf.game.soniccd.fmv;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.audio.SonicCdAudioProfile;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.title.CdTitleScreen;

import java.util.Objects;

/**
 * Hosts the opening video ({@code OPENING}) in the {@code OPTIONS} native-program mode: IPX's attract
 * {@code Demo} runs it from the title ({@code .Demos}, IPX.asm:948-954) and returns to {@code .GameLoop}. Its
 * PCM goes through the sub-CPU audio owner's chip ({@link CdSubCpuAudio#beginProgramPcm}).
 */
public final class CdFmvScreen implements NativeMenuScreen {
    private final CdSceneCoordinator coordinator;
    private final CdProgramScreenRenderer renderer = new CdProgramScreenRenderer();
    private CdOpeningProgram program;
    private CdSubCpuAudio audio;
    private boolean exiting;

    public CdFmvScreen(CdSceneCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override public GameMode mode() { return GameMode.OPTIONS; }

    @Override
    public void initialize() {
        audio = subCpuAudio();
        CdSubCpuAudio.ProgramPcm pcm = audio != null ? audio.beginProgramPcm() : CdSubCpuAudio.ProgramPcm.detached();
        program = opening(new CdFmvAssets(coordinator.data()), coordinator.disc().assets(), pcm,
                com.openggf.game.soniccd.CdLoadTiming.programTiming());
        renderer.reset();
        exiting = false;
    }

    /** {@code SPCmd_LoadOpening} (SPX.asm:1393-1412): OPEN_M.MMD with OPEN_S.BIN streaming OPN.STM. */
    public static CdOpeningProgram opening(CdFmvAssets assets, com.openggf.game.soniccd.media.CdProgramAssets disc,
                                           CdSubCpuAudio.ProgramPcm pcm) {
        return opening(assets, disc, pcm, null);
    }

    /** As above, the stream's read paced by {@code timing}'s load-time profile ({@link CdFmvStreamer#seekFor}). */
    public static CdOpeningProgram opening(CdFmvAssets assets, com.openggf.game.soniccd.media.CdProgramAssets disc,
                                           CdSubCpuAudio.ProgramPcm pcm,
                                           com.openggf.game.timing.HardwareTimingService timing) {
        CdFmvStreamer streamer = new CdFmvStreamer(new CdFmvStream(disc, CdNativeFile.OPENSTM),
                CdFmvStream.Layout.SOUND, pcm, assets.from("pcmRegValues", 14),
                CdFmvStreamer.seekFor(timing, entry(disc, CdNativeFile.OPENSTM)));
        return new CdOpeningProgram(assets, streamer);
    }

    /** The stream file's directory entry (its position and size key its read). */
    public static com.openggf.data.cd.Iso9660FileSystem.Entry entry(com.openggf.game.soniccd.media.CdProgramAssets disc,
                                                                    CdNativeFile file) {
        try {
            return disc.entry(file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("directory entry for " + file, e);
        }
    }

    static CdSubCpuAudio subCpuAudio() {
        var manager = GameServices.audio();
        return manager != null && manager.getAudioProfile() instanceof SonicCdAudioProfile cd ? cd.subCpuAudio() : null;
    }

    @Override
    public void update(InputHandler input) {
        if (program == null || exiting) return;
        program.frame(CdTitleScreen.pad(input.logical().player1()));
        if (program.done()) {
            if (audio != null) audio.endProgramPcm();
            exiting = true;
        }
    }

    @Override public void draw() { if (program != null) renderer.draw(program.screen().frame()); }

    @Override public void setClearColor() { if (program != null) renderer.setClearColor(program.screen().frame()); }

    @Override
    public void reset() {
        if (audio != null) audio.endProgramPcm();
        program = null;
        exiting = false;
    }

    @Override
    public Exit consumeExit() {
        if (!exiting) return Exit.NONE;
        exiting = false;
        program = null;
        return new Exit(Destination.TITLE_SCREEN, null, false);
    }

    /** The running program, for tests. */
    public CdOpeningProgram program() { return program; }
}
