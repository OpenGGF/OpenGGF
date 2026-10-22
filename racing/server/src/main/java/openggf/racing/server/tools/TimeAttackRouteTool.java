package openggf.racing.server.tools;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.tools.GameplayCaptureSession;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Records cosmetic poses from fresh ROM-backed GHZ1 controller playback for the
 * October 2026 capacity video. Inputs: repository root, output route file.
 * No trace rows, positions, rings, clocks or completion flags enter the engine.
 * The no-device boot renders the real route; subsequent bots only retime poses.
 */
public final class TimeAttackRouteTool {
    private static final int MAGIC = 0x47565231;
    private static final int LIMIT = 36000;
    public record Route(List<GhostFrame> frames, int firstInput) {
        public Route {
            frames = List.copyOf(frames);
            if (frames.size() < 2 || frames.size() > LIMIT || firstInput < 0 || firstInput >= frames.size() - 1)
                throw new IllegalArgumentException("invalid recorded route");
        }
    }
    private TimeAttackRouteTool() { }

    public static Route read(Path path) throws IOException {
        long size = Files.size(path);
        if (size < 12 || size > 12L + LIMIT * GhostFrameCodec.BYTES) throw new IOException("route size");
        try (var input = new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() != MAGIC) throw new IOException("route format");
            int count = input.readInt(), first = input.readInt();
            if (count < 2 || count > LIMIT || size != 12L + count * GhostFrameCodec.BYTES)
                throw new IOException("route frame count");
            List<GhostFrame> frames = new ArrayList<>(count);
            byte[] encoded = new byte[GhostFrameCodec.BYTES];
            for (int i = 0; i < count; i++) { input.readFully(encoded); frames.add(GhostFrameCodec.decode(encoded, 0)); }
            try { return new Route(frames, first); }
            catch (IllegalArgumentException invalid) { throw new IOException("route metadata", invalid); }
        }
    }

    public static void write(Path path, Route route) throws IOException {
        try (var output = new DataOutputStream(Files.newOutputStream(path))) {
            output.writeInt(MAGIC); output.writeInt(route.frames().size()); output.writeInt(route.firstInput());
            byte[] encoded = new byte[GhostFrameCodec.BYTES];
            for (GhostFrame frame : route.frames()) { GhostFrameCodec.encode(frame, encoded, 0); output.write(encoded); }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("REPOSITORY_ROOT OUTPUT_ROUTE");
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        AudioManager.getInstance().setBackend(new NullAudioBackend());
        Path root = Path.of(args[0]), output = Path.of(args[1]);
        var movie = new Bk2MovieLoader().load(root.resolve("src/test/resources/traces/s1/ghz1_fullrun/ghz1_fullrun.bk2"));
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        List<GhostFrame> frames = new ArrayList<>();
        int first = -1;
        boolean complete = false;
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.boot(root.resolve("Sonic The Hedgehog (W) (REV01) [!].gen"), 0, 0, settings);
            for (int i = 840; i < movie.getFrameCount(); i++) {
                var input = movie.getFrame(i);
                if (first < 0 && (input.p1InputMask() != 0 || input.p1ActionMask() != 0)) first = frames.size();
                capture.step(input);
                capture.renderFrame();
                var player = capture.player();
                if (player.getDead()) throw new IllegalStateException("recorded route died at " + i);
                complete = GameServices.gameState().isActCompletionSignalActive();
                frames.add(new GhostFrame(player.getCentreX(), player.getCentreY(), player.getMappingFrame(),
                        player.getRenderHFlip(), player.getRenderVFlip(), complete,
                        player.getPriorityBucket(), player.isHighPriority()));
                if (complete) break;
            }
        }
        if (!complete) throw new IllegalStateException("route did not complete naturally");
        write(output, new Route(frames, first));
        System.out.println("Recorded natural route poses=" + frames.size() + " firstInput=" + first);
    }
}
