package openggf.racing.server.tools;

import com.openggf.version.AppVersion;
import com.openggf.audio.AudioManager;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.data.Rom;
import com.openggf.debug.playback.*;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.mods.run.DeterminismFingerprint;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.tools.TimeAttackVideoCaptureTool;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Records the ordinary first-boot Time Attack flow beside 255 local protocol peers.
 * Inputs: OUTPUT, repository/ROM root, player count (2..256), optional window seconds.
 * Uses the maintained GHZ1 controller movie; reads no trace state and never forces
 * gameplay completion. Origin: October 2026 full-capacity video request.
 */
public final class TimeAttackCapacityVideoTool {
    private final Path out;
    private final TimeAttackVideoCaptureTool capture;
    private final TimeAttackVideoPeers peers;
    private final Bk2Movie movie;
    private final Process encoder;
    private final OutputStream video;
    private final OutputStream pcm;
    private final BufferedWriter events;
    private final BufferedWriter states;
    private final LiveCaptureAudioHandle audio;
    private final short[] samples;
    private final byte[] sampleBytes;
    private final long started;
    private String stage = "First boot";
    private int movieFrame;
    private Bk2FrameInput previous;
    private boolean driving;
    private boolean stepped;
    private boolean naturalFinish;
    private Object session;
    private int finishFrame = -1;
    private Set<String> previousGhosts = Set.of(), previousVisible = Set.of();
    private final Set<String> seenGhosts = new HashSet<>(), seenVisible = new HashSet<>();
    private int ghostEntries, ghostExits, ghostReturns, visibleEntries, visibleExits, visibleReturns, peakGhosts;

    @jdk.jfr.Name("openggf.MultiplayerCaptureWindow")
    @jdk.jfr.Label("Multiplayer rendered route")
    @jdk.jfr.StackTrace(false)
    static final class CaptureWindow extends jdk.jfr.Event { public int players; }
    private final CaptureWindow captureWindow = new CaptureWindow();

    private TimeAttackCapacityVideoTool(Path out, Path root, int count, Path routeFile) throws Exception {
        this.out = out.toAbsolutePath();
        Files.createDirectories(this.out);
        try {
        String fp;
        try (Rom rom = new Rom()) {
            if (!rom.open(root.resolve("Sonic The Hedgehog (W) (REV01) [!].gen").toString()))
                throw new IOException("Sonic 1 REV01 ROM not available");
            fp = new DeterminismFingerprint(AppVersion.get(), rom.calculateChecksum()).asString();
        }
        if (routeFile == null) {
            routeFile = this.out.resolve("route.ghost");
            Process routeProcess = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-Xmx1g", "-Dopenggf.headless.gl=surfaceless-egl",
                    "-cp", System.getProperty("java.class.path"), TimeAttackRouteTool.class.getName(),
                    root.toString(), routeFile.toString()).redirectErrorStream(true)
                    .redirectOutput(this.out.resolve("route-build.log").toFile()).start();
            try {
                if (!routeProcess.waitFor(120, TimeUnit.SECONDS)) {
                    routeProcess.destroyForcibly(); routeProcess.waitFor(5, TimeUnit.SECONDS);
                    throw new IOException("route preparation timeout");
                }
                if (routeProcess.exitValue() != 0) throw new IOException("route preparation failed");
            } finally { if (routeProcess.isAlive()) { routeProcess.destroyForcibly(); routeProcess.waitFor(5, TimeUnit.SECONDS); } }
        }
        peers = new TimeAttackVideoPeers(this.out.resolve("server"), Path.of("identity"), fp, count,
                TimeAttackRouteTool.read(routeFile));
        peers.fill();
        Path settings = this.out.resolve("profile/saves/mods/time-attack/settings.txt");
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, "displayName=Capture Player\nmasterUrl=" + peers.endpoint()
                + "\nmasterTrustInsecure=false\nminimap=true\n");
        movie = new Bk2MovieLoader().load(root.resolve("src/test/resources/traces/s1/ghz1_fullrun/ghz1_fullrun.bk2"));
        capture = new TimeAttackVideoCaptureTool(this.out, root, 320, 224);
        encoder = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "rawvideo", "-pixel_format", "rgba", "-video_size", "320x224", "-framerate", "30",
                "-i", "pipe:0", "-vf", "vflip,scale=960:672:flags=neighbor", "-c:v", "libx264", "-preset", "veryfast",
                "-crf", "18", "-pix_fmt", "yuv420p", this.out.resolve("gameplay-silent.mp4").toString())
                .redirectError(this.out.resolve("encoder.log").toFile()).start();
        video = new BufferedOutputStream(encoder.getOutputStream(), 4 * 1024 * 1024);
        pcm = new BufferedOutputStream(Files.newOutputStream(this.out.resolve("audio.pcm")));
        audio = AudioManager.getInstance().beginLiveCaptureAudio(60);
        samples = new short[audio.maxStereoFramesPerPacket() * 2];
        sampleBytes = new byte[samples.length * 2];
        events = Files.newBufferedWriter(this.out.resolve("chapters.csv"));
        states = Files.newBufferedWriter(this.out.resolve("state.csv"));
        events.write("frame,video_seconds,stage\n");
        states.write("frame,movie_frame,mode,x,y,dead,natural_finish,connected,bot_finishes,received_ghost_entries,active_ghosts,viewport_ghosts,ghost_entries,ghost_exits,viewport_entries,viewport_exits,viewport_returns\n");
        started = System.nanoTime();
        capture.beforeFrame(ignored -> before());
        capture.afterFrame(ignored -> after());
        chapter("First boot");
        } catch (Throwable failure) {
            try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new RuntimeException(failure);
        }
    }

    private void before() {
        try {
            peers.tick();
            if (driving && peers.connectedCount() != peers.botCount() + 1)
                throw new IllegalStateException("room lost a participant during the race: " + peers.connectedCount());
            if (driving && capture.engine().getCurrentGameMode() == GameMode.MASTER_TITLE_SCREEN)
                throw new IllegalStateException("race returned to master title before completion");
            stepped = false;
            if (driving && !naturalFinish && capture.engine().getCurrentGameMode() == GameMode.LEVEL) {
                Object coordinator = call(session, "coordinator");
                if (coordinator != null && !(boolean) call(coordinator, "holdGameplay")) {
                    Bk2FrameInput current = movieFrame + 840 < movie.getFrameCount()
                            ? movie.getFrame(movieFrame + 840)
                            : new Bk2FrameInput(0, 0, 0, false, 0, 0, false, "neutral");
                    capture.input().setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
                    previous = current;
                    movieFrame++;
                    stepped = true;
                }
            }
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private void after() {
        try {
            int frame = (int) capture.frameNumber();
            var camera = GameServices.cameraOrNull();
            var player = camera == null ? null : camera.getFocusedSprite();
            if (stepped && player != null) {
                peers.onFrame(new GhostFrame(player.getCentreX(), player.getCentreY(), player.getMappingFrame(),
                        player.getRenderHFlip(), player.getRenderVFlip(), false, player.getPriorityBucket(), player.isHighPriority()));
                if (player.getDead()) throw new IllegalStateException("recorded player died at input " + movieFrame);
                Object runtime = field(session, "runtime");
                if (runtime != null && (boolean) call(runtime, "isAttemptFinished")) {
                    naturalFinish = true;
                    finishFrame = frame;
                    peers.finishBots();
                    capture.input().clearLogicalOverride();
                    chapter("Natural level completion");
                    capture.png("07-level-complete.png");
                }
            }
            Set<String> active = new HashSet<>(), visible = new HashSet<>();
            if (session != null && player != null) {
                Object runtime = field(session, "runtime");
                if (runtime != null) {
                    @SuppressWarnings("unchecked")
                    var ghosts = (java.util.List<com.openggf.mods.run.GhostPose>) call(runtime, "ghosts");
                    for (var ghost : ghosts) {
                        if (!ghost.slotId().startsWith("net:")) continue;
                        active.add(ghost.slotId());
                        int x = ghost.pose().centreX(), y = ghost.pose().centreY();
                        if (x >= camera.getX() - 16 && x < camera.getX() + 336
                                && y >= camera.getY() - 24 && y < camera.getY() + 248) visible.add(ghost.slotId());
                    }
                }
            }
            // Count only stepped gameplay presentations, including natural completion.
            // Delayed bot/results-tail movement cannot satisfy the race visibility gate.
            if (stepped) {
                for (String id : active) if (!previousGhosts.contains(id)) { ghostEntries++; if (!seenGhosts.add(id)) ghostReturns++; }
                for (String id : previousGhosts) if (!active.contains(id)) ghostExits++;
                for (String id : visible) if (!previousVisible.contains(id)) { visibleEntries++; if (!seenVisible.add(id)) visibleReturns++; }
                for (String id : previousVisible) if (!visible.contains(id)) visibleExits++;
                previousGhosts = active; previousVisible = visible;
                peakGhosts = Math.max(peakGhosts, active.size());
            }
            if (peakGhosts > 8) throw new IllegalStateException("renderer ghost cap exceeded");
            int count = audio.drainPresentationFrame(samples) * 2;
            for (int i = 0; i < count; i++) {
                sampleBytes[2 * i] = (byte) samples[i];
                sampleBytes[2 * i + 1] = (byte) (samples[i] >>> 8);
            }
            pcm.write(sampleBytes, 0, count * 2);
            if (frame % 2 == 0) video.write(capture.pixels());
            states.write(frame + "," + movieFrame + "," + capture.engine().getCurrentGameMode()
                    + "," + (player == null ? -1 : player.getCentreX())
                    + "," + (player == null ? -1 : player.getCentreY())
                    + "," + (player != null && player.getDead()) + "," + naturalFinish
                    + "," + peers.connectedCount() + "," + peers.sentFinishes()
                    + "," + peers.receivedGhostEntries() + "," + active.size() + "," + visible.size()
                    + "," + ghostEntries + "," + ghostExits + "," + visibleEntries + "," + visibleExits
                    + "," + visibleReturns + "\n");
            if (driving && movieFrame > 0 && frame % 600 == 0) capture.png("route-" + frame + ".png");
            // Pace the real server against real engine presentations. This is a
            // demonstration, with encoding/rendering cost, never a benchmark.
            long deadline = started + frame * 1_000_000_000L / 60;
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining);
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private void chapter(String name) throws IOException {
        stage = name;
        events.write(capture.frameNumber() + "," + capture.frameNumber() / 60.0 + "," + name + "\n");
        events.flush();
        System.out.println("stage=" + name + " frame=" + capture.frameNumber()
                + " connected=" + peers.connectedCount() + " finishes=" + peers.finishedCount());
    }

    private void await(BooleanSupplier ready, int frames, String what) throws Exception {
        for (int i = 0; !ready.getAsBoolean() && i < frames; i++) capture.frame();
        if (!ready.getAsBoolean()) throw new IllegalStateException("timed out: " + what + " at " + stage);
    }

    private void run(int window) throws Exception {
        capture.openTimeAttack();
        Object ownedScene = field(field(capture.engine().getGameLoop(), "modSceneHost"), "scene");
        Object scene = com.openggf.mods.code.OwnedSceneFactory.unwrap((com.openggf.mods.scene.ModScene) ownedScene);
        session = field(scene, "session");
        chapter("Time Attack setup");
        capture.frames(90);
        for (int i = 0; i < 3; i++) capture.tap(GLFW_KEY_DOWN);
        for (int i = 0; i < 3; i++) capture.tap(GLFW_KEY_RIGHT);
        for (int i = 0; i < 3; i++) capture.tap(GLFW_KEY_DOWN);
        capture.tap(GLFW_KEY_ENTER);
        await(() -> state().equals("BROWSING"), 1200, "server browser");
        chapter("Server browser");
        capture.frames(150);
        capture.png("05-server-browser.png");
        capture.tap(GLFW_KEY_ENTER);
        await(() -> state().equals("LOBBY"), 1200, "lobby");
        await(() -> peers.connectedCount() == peers.botCount() + 1, 600, "full roster");
        chapter("Maximum capacity lobby");
        capture.frames(120);
        capture.png("06-full-lobby.png");
        capture.tap(GLFW_KEY_UP);
        capture.tap(GLFW_KEY_UP);
        // Last player page proves that the rendered client is in the 256-slot roster.
        for (int i = 0; i < (peers.botCount() / 2); i++) capture.tap(GLFW_KEY_RIGHT);
        capture.frames(90);
        capture.png("06b-last-lobby-page.png");
        peers.startRound(window);
        chapter("Round countdown");
        driving = true;
        await(() -> capture.engine().getCurrentGameMode() == GameMode.LEVEL, 1200, "ROM level");
        chapter("Green Hill Act 1");
        captureWindow.players = peers.botCount() + 1;
        captureWindow.begin();
        await(() -> naturalFinish, 6000, "natural level completion");
        captureWindow.end(); captureWindow.commit();
        await(() -> peers.sentFinishes() == peers.botCount(), 1500, "bot finishes");
        capture.frames(60);
        peers.requestAllStandings();
        await(() -> peers.allStandingsReceived(), 1200, "all 256 accepted standings");
        if (!peers.livePlayerFinished()) throw new IllegalStateException("visible player's finish absent");
        if (peers.botCount() >= 15 && (peers.liveRank() < (peers.botCount() + 1) * 3 / 8
                || peers.liveRank() > (peers.botCount() + 1) * 5 / 8))
            throw new IllegalStateException("visible player did not finish mid-pack: " + peers.liveRank());
        if (peers.botCount() >= 15 && (ghostEntries < 20 || ghostExits < 20 || visibleReturns < 5))
            throw new IllegalStateException("insufficient real visibility churn: " + ghostEntries + "/" + ghostExits + "/" + visibleReturns);
        chapter("All finishes accepted");
        capture.frames(300);
        capture.png("08-all-finished.png");
        Files.writeString(out.resolve("result.txt"), "success=true\nplayers=" + peers.connectedCount()
                + "\npeak=" + peers.peakCount() + "\nacceptedFinishes=" + peers.finishedCount()
                + "\nnaturalPlayerFinish=true\nfinishPresentation=" + finishFrame
                + "\nmovieFrames=" + movieFrame + "\nreceivedGhostEntries=" + peers.receivedGhostEntries()
                + "\ntcpUpstreamBytes=" + peers.tcpUpstreamBytes()
                + "\ntcpDownstreamBytes=" + peers.tcpDownstreamBytes()
                + "\nrenderedClients=1\nprotocolBots=" + peers.botCount()
                + "\nliveRank=" + peers.liveRank() + "\nuniqueRenderedGhosts=" + seenGhosts.size()
                + "\nghostEntries=" + ghostEntries + "\nghostExits=" + ghostExits + "\nghostReturns=" + ghostReturns
                + "\nviewportEntries=" + visibleEntries + "\nviewportExits=" + visibleExits
                + "\nviewportReturns=" + visibleReturns + "\npeakRenderedGhosts=" + peakGhosts
                + "\naudioRate=" + audio.sampleRate() + "\nframes=" + capture.frameNumber() + "\n");
    }

    private String state() {
        try { return call(session, "state").toString(); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object call(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    private void close() throws Exception {
        if (capture != null) {
            capture.beforeFrame(ignored -> {});
            capture.afterFrame(ignored -> {});
        }
        java.util.List<Exception> failures = new java.util.ArrayList<>();
        closeOne(events, failures);
        closeOne(states, failures);
        closeOne(audio, failures);
        closeOne(pcm, failures);
        closeOne(video, failures);
        if (encoder != null) {
            try {
                if (!encoder.waitFor(30, TimeUnit.SECONDS)) {
                    encoder.destroyForcibly();
                    encoder.waitFor(5, TimeUnit.SECONDS);
                    failures.add(new IOException("encoder timeout"));
                } else if (encoder.exitValue() != 0) {
                    failures.add(new IOException("encoder failed: " + encoder.exitValue()));
                }
            } catch (Exception failure) { encoder.destroyForcibly(); failures.add(failure); }
        }
        closeOne(capture, failures);
        closeOne(peers, failures);
        if (!failures.isEmpty()) {
            Exception first = failures.removeFirst();
            failures.forEach(first::addSuppressed);
            throw first;
        }
    }

    private static void closeOne(AutoCloseable resource, java.util.List<Exception> failures) {
        if (resource == null) return;
        try { resource.close(); } catch (Exception failure) { failures.add(failure); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 5)
            throw new IllegalArgumentException("OUTPUT_DIR REPOSITORY_ROOT PLAYERS [WINDOW_SECONDS=180] [RECORDED_ROUTE]");
        TimeAttackCapacityVideoTool tool = new TimeAttackCapacityVideoTool(Path.of(args[0]),
                Path.of(args[1]).toAbsolutePath(), Integer.parseInt(args[2]), args.length == 5 ? Path.of(args[4]) : null);
        try { tool.run(args.length >= 4 ? Integer.parseInt(args[3]) : 180); }
        finally { tool.close(); }
        Files.writeString(tool.out.resolve("result.txt"), "cleanup=clean\n", StandardOpenOption.APPEND);
    }
}
