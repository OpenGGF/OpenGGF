package com.openggf.game.soniccd.title.secrets;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.audio.SonicCdAudioProfile;
import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.fmv.CdProgramScreenState;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.title.CdTitleScreen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Hosts the sound test ({@code SNDTEST}), best staff times ({@code STAFFTIMES}) and the five hidden pictures in the {@code OPTIONS} native-program mode.
 * The sound test reports its exit code to the coordinator ({@code SoundTest .Exits}, IPX.asm:1035-1123); a picture
 * returns to {@code .GameLoop}.
 */
public final class CdSecretsScreen implements NativeMenuScreen {
    private static final Map<Integer, String> PCM_DRIVERS = drivers();

    private final CdSceneCoordinator coordinator;
    private final CdNativeProgram program;
    private final CdProgramScreenRenderer renderer = new CdProgramScreenRenderer();
    private CdSoundTestProgram soundTest;
    private CdSecretImageProgram image;
    private CdBestTimesProgram bestTimes;
    private boolean exiting;

    public CdSecretsScreen(CdSceneCoordinator coordinator, CdNativeProgram program) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.program = Objects.requireNonNull(program, "program");
    }

    @Override public GameMode mode() { return GameMode.OPTIONS; }

    @Override
    public void initialize() {
        CdFmvAssets assets = new CdFmvAssets(coordinator.data());
        soundTest = null;
        image = null;
        bestTimes = null;
        switch (program) {
            case SNDTEST -> soundTest = new CdSoundTestProgram(assets);
            case STAFFTIMES -> bestTimes = new CdBestTimesProgram(assets);
            case FUNISINF -> image = new CdSecretImageProgram(assets, CdSecretImageProgram.Picture.FUN_IS_INFINITE);
            case MCSONIC -> image = new CdSecretImageProgram(assets, CdSecretImageProgram.Picture.MC_SONIC);
            case TAILS -> image = new CdSecretImageProgram(assets, CdSecretImageProgram.Picture.TAILS);
            case BATMAN -> image = new CdSecretImageProgram(assets, CdSecretImageProgram.Picture.BATMAN);
            case CUTESONIC -> image = new CdSecretImageProgram(assets, CdSecretImageProgram.Picture.CUTE_SONIC);
            default -> throw new IllegalArgumentException("Not a secret program: " + program);
        }
        renderer.reset();
        exiting = false;
    }

    @Override
    public void update(InputHandler input) {
        if (exiting) return;
        int pad = CdTitleScreen.pad(input.logical().player1());
        if (soundTest != null) {
            soundTest.frame(pad);
            for (CdSoundTestProgram.Request request : soundTest.drainRequests()) play(request);
            if (soundTest.done()) {
                coordinator.soundTestExited(soundTest.exitCode());
                exiting = true;
            }
        } else if (bestTimes != null) {
            bestTimes.frame(pad);
            if (bestTimes.done()) exiting = true;
        } else if (image != null) {
            image.frame(pad);
            if (image.done()) exiting = true;
        }
    }

    private static void play(CdSoundTestProgram.Request request) {
        var manager = GameServices.audio();
        if (manager == null) return;
        if (request.fm()) {
            manager.playSfx(request.id());                 // move.b id,FMDrvQueue2
            return;
        }
        if (manager.getAudioProfile() instanceof SonicCdAudioProfile cd) {
            CdSubCpuAudio audio = cd.subCpuAudio();
            String driver = PCM_DRIVERS.get(request.id());
            if (driver != null) audio.loadPcmDriver(driver);
            audio.submit(request.id());
        }
    }

    private CdProgramScreenState state() {
        return soundTest != null ? soundTest.screen() : bestTimes != null ? bestTimes.screen() : image != null ? image.screen() : null;
    }

    @Override public void draw() { if (state() != null) renderer.draw(state().frame()); }

    @Override public void setClearColor() { if (state() != null) renderer.setClearColor(state().frame()); }

    @Override
    public void reset() {
        soundTest = null;
        image = null;
        exiting = false;
    }

    @Override
    public Exit consumeExit() {
        if (!exiting) return Exit.NONE;
        exiting = false;
        return new Exit(Destination.TITLE_SCREEN, null, false);
    }

    /** The running sound test, for tests. */
    public CdSoundTestProgram soundTest() { return soundTest; }

    private static Map<Integer, String> drivers() {
        Map<Integer, String> out = new HashMap<>();
        try (InputStream in = CdSecretsScreen.class.getResourceAsStream("/soniccd/soundtest-pcm-drivers.tsv")) {
            if (in == null) throw new IllegalStateException("soniccd/soundtest-pcm-drivers.tsv is missing");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean header = true;
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.startsWith("#") || line.isBlank()) continue;
                if (header) { header = false; continue; }
                String[] c = line.split("\t");
                out.put(Integer.parseInt(c[0], 16), c[1]);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return Map.copyOf(out);
    }
}
