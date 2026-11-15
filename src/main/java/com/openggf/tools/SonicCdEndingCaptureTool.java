package com.openggf.tools;

import com.openggf.game.GameDataSource;
import com.openggf.game.GameServices;
import com.openggf.game.internal.HostedGameMedia;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.ending.CdEndingProgram;
import com.openggf.game.soniccd.ending.CdEndings;
import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.ScreenshotCapture;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Films Sonic CD's good or bad ending (GameDone's {@code GOODEND}/{@code BADEND} program, IPX.asm:301-326) as the
 * engine plays it: {@link CdEndingProgram} through the production {@link CdProgramScreenRenderer}, the pair
 * {@code CdEndingProvider} draws in {@code ENDING_CUTSCENE}. The capture tools film levels; the ending is reached
 * only through a whole game, so this tool starts the program directly, as Visual Mode does. Pictures only (the
 * program's PCM is detached); {@code frames/NNNNN.png} and {@code state.csv} (frame, credits, exiting, image).
 *
 * <pre>
 * java -Dsoniccd.media.root=... -Dopenggf.headless.gl=surfaceless-egl ... com.openggf.tools.SonicCdEndingCaptureTool \
 *   --ending good --out-dir DIR [--frames N] [--from N]
 * </pre>
 */
public final class SonicCdEndingCaptureTool {
    private SonicCdEndingCaptureTool() { }

    public static void main(String[] argv) throws Exception {
        boolean good = true;
        Path out = null;
        int frames = 30000, from = 0;
        for (int i = 0; i < argv.length; i++) {
            switch (argv[i]) {
                case "--ending" -> {
                    String which = CliArguments.requireValue(argv, ++i, "--ending");
                    if (!which.equals("good") && !which.equals("bad")) {
                        throw new IllegalArgumentException("--ending good|bad");
                    }
                    good = which.equals("good");
                }
                case "--out-dir" -> out = Path.of(CliArguments.requireValue(argv, ++i, "--out-dir"));
                case "--frames" -> frames = CliArguments.parseInt(CliArguments.requireValue(argv, ++i, "--frames"));
                case "--from" -> from = CliArguments.parseInt(CliArguments.requireValue(argv, ++i, "--from"));
                default -> throw new IllegalArgumentException("Unknown argument: " + argv[i]);
            }
        }
        if (out == null) throw new IllegalArgumentException("--out-dir is required");
        Path frameDir = Files.createDirectories(out.resolve("frames"));
        SonicCdGameModule module = new SonicCdGameModule();
        GameDataSource source = HostedGameMedia.of(module).openConfiguredMedia();
        try (CdDiscDataSource disc = (CdDiscDataSource) source;
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224);
             BufferedWriter state = Files.newBufferedWriter(out.resolve("state.csv"), StandardCharsets.UTF_8)) {
            // A stage boot owns the GL context and session the program's renderer draws in.
            boot.boot(disc, module, 0, 0);
            CdSceneCoordinator scenes = module.getGameService(CdSceneCoordinator.class);
            CdEndingProgram program = CdEndings.program(new CdFmvAssets(scenes.data()), disc.assets(), good,
                    CdSubCpuAudio.ProgramPcm.detached());
            CdProgramScreenRenderer renderer = new CdProgramScreenRenderer();
            GraphicsManager graphics = GameServices.graphics();
            state.write("frame,credits,exiting,image,display,width");
            state.newLine();
            int written = 0;
            for (int f = 0; f < frames && !program.done(); f++) {
                program.frame(0);
                var frame = program.screen().frame();
                state.write(f + "," + (program.creditsActive() ? 1 : 0) + "," + (program.exiting() ? 1 : 0) + ","
                        + (program.inImage() ? 1 : 0) + "," + (frame.display() ? 1 : 0) + "," + frame.width());
                state.newLine();
                renderer.setClearColor(frame);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                graphics.resetForFixedFunction();
                renderer.draw(frame);
                graphics.flushScreenSpace();
                glFinish();
                if (f < from) continue;
                ScreenshotCapture.savePNG(ScreenshotCapture.captureFramebuffer(320, 224),
                        frameDir.resolve(String.format("%05d.png", f)));
                written++;
            }
            System.out.println("Ending (" + (good ? "good" : "bad") + "): " + program.frames() + " program frames, "
                    + written + " pictures -> " + frameDir + (program.done() ? "" : " (frame budget reached)"));
        }
    }
}
