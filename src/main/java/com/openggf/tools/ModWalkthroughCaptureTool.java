package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.graphics.ScreenshotCapture;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import static org.lwjgl.opengl.GL11.*;

/**
 * Captures a packaged mod's real title-to-gameplay walkthrough and final stereo
 * PCM through the existing production capture session. Inputs: ROM, mod jar,
 * game/zone/one-based act, BK2/input log, new outside-repository output directory.
 * Origin: MHZ Post Two Ambush prototype, 2026-10-07. This is presentation evidence,
 * not native parity; no comparison/fixture state is supplied to gameplay.
 */
public final class ModWalkthroughCaptureTool {
    private ModWalkthroughCaptureTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 7 && args.length != 8) throw new IllegalArgumentException(
                "Usage: ROM MOD.jar GAME ZONE ACT INPUT OUTPUT [--title]");
        boolean title = args.length == 8 && args[7].equals("--title");
        if (args.length == 8 && !title) throw new IllegalArgumentException("Unknown capture option " + args[7]);
        Path output = Path.of(args[6]).toAbsolutePath();
        if (Files.exists(output)) throw new IllegalArgumentException("Output directory already exists: " + output);
        Files.createDirectories(output.resolve("frames"));
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(args[5]));
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null,
                null, true, false);
        var pcm = new short[16384];
        int rate;
        int fps;
        long pcmBytes = 0;
        try (var session = new GameplayCaptureSession(settings);
             var state = Files.newBufferedWriter(output.resolve("state.csv"));
             var wav = FileChannel.open(output.resolve("audio.wav"), StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            session.applyMod(Path.of(args[1]));
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
            session.boot(Path.of(args[0]), GameplayCaptureTool.ZoneIds.resolve(args[2], args[3]), Integer.parseInt(args[4]) - 1, settings);
            var audio = GameServices.audio();
            if (title) session.loop().initializeTitleScreenMode();
            rate = audio.outputSampleRate(); fps = audio.presentationFrameRate();
            // The manager-owned live lease follows title/level source rebuilds.
            // The offline compatibility lease deliberately retires at a rebuild.
            try (var capture = audio.beginLiveCaptureAudio(fps)) {
                wav.write(wavHeader(rate, 0));
                state.write("frame,mode,x,y,xvel,yvel,rings,hurt,dead,cam_x,cam_y,checkpoint,controller_state,adapter_state,input\n");
                for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                    var input = movie.getFrame(frame);
                    session.step(input);
                    // One final PCM packet per outer presented row, including held menus.
                    session.loop().presentOuterFrame(false, false);
                    int stereoFrames = capture.drainPresentationFrame(pcm);
                    var bytes = ByteBuffer.allocate(stereoFrames * 4).order(ByteOrder.LITTLE_ENDIAN);
                    for (int n = 0; n < stereoFrames * 2; n++) bytes.putShort(pcm[n]);
                    bytes.flip();
                    while (bytes.hasRemaining()) wav.write(bytes);
                    pcmBytes += stereoFrames * 4L;
                    writeState(state, session, frame, input.rawLine());
                    var mode = session.loop().getCurrentGameMode();
                    if (mode == GameMode.TITLE_SCREEN) {
                        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                        var provider = session.loop().getTitleScreenProvider();
                        provider.setClearColor(); provider.draw();
                        GameServices.graphics().flushScreenSpace();
                        var ui = GameServices.graphics().getUiRenderPipeline();
                        if (ui != null) ui.renderFadePass();
                        glFinish();
                        ScreenshotCapture.savePNG(ScreenshotCapture.captureFramebuffer(320, 224),
                                output.resolve(String.format(Locale.ROOT, "frames/%05d.png", frame)));
                    } else {
                        ScreenshotCapture.savePNG(session.render(),
                                output.resolve(String.format(Locale.ROOT, "frames/%05d.png", frame)));
                    }
                }
                wav.position(0); wav.write(wavHeader(rate, pcmBytes));
            }
        }
        Files.writeString(output.resolve("capture.txt"), "sampleRate=" + rate + "\nfps=" + fps
                + "\nframes=" + movie.getFrameCount() + "\npcmBytes=" + pcmBytes + "\n");
        System.out.println("Captured " + movie.getFrameCount() + " production rows and " + pcmBytes
                + " PCM bytes to " + output);
    }

    private static void writeState(BufferedWriter state, GameplayCaptureSession session, int frame, String input)
            throws IOException {
        var player = GameServices.sprites().getMainPlayable(); // Re-read after native respawn; no stale roster pointer.
        var camera = GameServices.camera();
        var post = GameServices.level().getCheckpointState();
        var controller = GameServices.module().gameplayFrameController();
        String controllerState = controller instanceof com.openggf.game.rewind.RewindSnapshottable<?> adapter
                ? String.valueOf(adapter.capture()) : "";
        // Registered adapters also expose run state separate from the frame
        // controller. This is observation only; no CSV value enters gameplay.
        String adapterState = GameServices.module().rewindAdapters().stream()
                .map(adapter -> adapter.key() + "=" + adapter.capture())
                .collect(java.util.stream.Collectors.joining(";"));
        state.write(frame + "," + session.loop().getCurrentGameMode() + "," + (player.getCentreX() & 65535)
                + "," + (player.getCentreY() & 65535) + "," + player.getXSpeed() + "," + player.getYSpeed()
                + "," + player.getRingCount() + "," + player.isHurt() + "," + player.getDead()
                + "," + camera.getX() + "," + camera.getY() + "," +
                post.getLastCheckpointIndex() + ",\"" + controllerState.replace("\"", "\"\"")
                + "\",\"" + adapterState.replace("\"", "\"\"")
                + "\",\"" + input.replace("\"", "\"\"") + "\"\n");
    }

    private static ByteBuffer wavHeader(int rate, long bytes) {
        if (bytes > Integer.MAX_VALUE - 36L) throw new IllegalArgumentException("Walkthrough exceeds WAV size bound");
        var header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(0x46464952).putInt((int) bytes + 36).putInt(0x45564157).putInt(0x20746D66)
                .putInt(16).putShort((short) 1).putShort((short) 2).putInt(rate).putInt(rate * 4)
                .putShort((short) 4).putShort((short) 16).putInt(0x61746164).putInt((int) bytes);
        return header.flip();
    }
}
