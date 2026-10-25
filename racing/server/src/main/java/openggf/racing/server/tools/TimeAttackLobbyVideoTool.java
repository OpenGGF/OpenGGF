package openggf.racing.server.tools;

import com.openggf.data.Rom;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.mods.run.CharacterLooks;
import com.openggf.mods.run.DeterminismFingerprint;
import com.openggf.mods.run.RunArea;
import com.openggf.net.client.DirectJoinAddress;
import com.openggf.net.client.LobbyPosePublisher;
import com.openggf.net.client.RaceClient;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.tools.TimeAttackVideoCaptureTool;
import com.openggf.version.AppVersion;
import openggf.timeattack.mp.LobbyAreas;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Records the Time Attack lobby level through the ordinary first-boot UI: the rendered player
 * hosts a LAN room for GAME and lands in that game's lobby level behind the lobby menu; three
 * protocol peers join with international names and looks, ready up and walk the lobby as lobby
 * presence (replaying the rendered player's own recent poses at an offset, so their animation is
 * the game's own); the player readies, hides the menu with Enter and runs, brings the menu back,
 * and starts the round, which hands over to the race level.
 *
 * <p>Inputs: OUTPUT_DIR, REPOSITORY_ROOT (holding the ROM .gen files and the mod), GAME
 * ({@code s1}, {@code s2} or {@code s3k}). Writes PNG stills, {@code lobby.mp4} (when ffmpeg is
 * installed) and {@code state.csv}. Display-free (surfaceless EGL); never touches a desktop.
 * Origin: 2026-10-22 Time Attack lobby-level lane.
 */
public final class TimeAttackLobbyVideoTool {
    private static final String[] NAMES = {"Nguyễn Thị Hoa", "ブラック・ノヴァ", "Zoë Ångström"};
    private static final int[] COLOURS = {0x006E, 0x0E62, 0x04C2};
    private static final int[] DELAYS = {24, 48, 72};
    private static final int[] OFFSETS = {-0x30, 0x38, 0x70};

    private final Path out;
    private final String gameId;
    private final RunArea area;
    private final TimeAttackVideoCaptureTool capture;
    private final List<Bot> bots = new ArrayList<>();
    private final ArrayDeque<GhostFrame> history = new ArrayDeque<>();
    private final BufferedWriter states;
    private Process encoder;
    private OutputStream video;
    private Object session;
    private boolean streaming;
    private long started;

    private static final class Bot {
        final RaceClient client;
        final LobbyPosePublisher publisher;
        final int index;
        boolean ready;

        Bot(RaceClient client, int index) {
            this.client = client;
            this.publisher = new LobbyPosePublisher(client::sendBinary);
            this.index = index;
        }
    }

    private TimeAttackLobbyVideoTool(Path out, Path root, String gameId) throws Exception {
        this.out = out.toAbsolutePath();
        this.gameId = gameId;
        this.area = LobbyAreas.forGame(gameId).area();
        Files.createDirectories(this.out);
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path settings = this.out.resolve("profile/saves/mods/time-attack/settings.txt");
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, "displayName=Capture Player\nhostPort=" + port + "\nlookBase=22E\nminimap=true\n");
        capture = new TimeAttackVideoCaptureTool(this.out, root, 320, 224);
        states = Files.newBufferedWriter(this.out.resolve("state.csv"));
        states.write("frame,mode,x,y,menu_shown,lobby_ghosts,stage,ghost_poses\n");
        try {
            encoder = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "rawvideo", "-pixel_format", "rgba", "-video_size", "320x224", "-framerate", "30",
                    "-i", "pipe:0", "-vf", "vflip,scale=960:672:flags=neighbor", "-c:v", "libx264",
                    "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p",
                    this.out.resolve("lobby.mp4").toString())
                    .redirectError(this.out.resolve("encoder.log").toFile()).start();
            video = new BufferedOutputStream(encoder.getOutputStream(), 4 * 1024 * 1024);
        } catch (IOException noFfmpeg) {
            encoder = null;
            video = null;
        }
        capture.afterFrame(ignored -> after());
    }

    private static String romFile(String gameId) {
        return switch (gameId) {
            case "s1" -> "Sonic The Hedgehog (W) (REV01) [!].gen";
            case "s2" -> "Sonic The Hedgehog 2 (W) (REV01) [!].gen";
            case "s3k" -> "Sonic and Knuckles & Sonic 3 (W) [!].gen";
            default -> throw new IllegalArgumentException("game " + gameId);
        };
    }

    private String stage = "boot";

    private void after() {
        try {
            for (Bot bot : bots) bot.client.drainInbound();
            var camera = GameServices.cameraOrNull();
            var player = camera == null || capture.engine().getCurrentGameMode() != GameMode.LEVEL
                    ? null : camera.getFocusedSprite();
            boolean lobbyLevel = session != null && (boolean) call(session, "lobbyRunActive");
            if (player != null && lobbyLevel) {
                history.addLast(new GhostFrame(player.getCentreX(), player.getCentreY(), player.getMappingFrame(),
                        player.getRenderHFlip(), player.getRenderVFlip(), false, player.getPriorityBucket(),
                        player.isHighPriority()));
                while (history.size() > 120) history.removeFirst();
                if (streaming) publishBots();
            }
            int ghosts = lobbyGhosts();
            int frame = (int) capture.frameNumber();
            states.write(frame + "," + capture.engine().getCurrentGameMode() + ","
                    + (player == null ? -1 : player.getCentreX()) + "," + (player == null ? -1 : player.getCentreY())
                    + "," + (session != null && (boolean) call(session, "lobbyMenuShown")) + "," + ghosts
                    + "," + stage + "," + ghostSummary() + "\n");
            if (video != null && frame % 2 == 0) video.write(capture.pixels());
            // Pace presentations at 60 Hz: the room's presence rate limits are wall-clock.
            if (started == 0) started = System.nanoTime() - frame * 1_000_000_000L / 60;
            long remaining = started + frame * 1_000_000_000L / 60 - System.nanoTime();
            if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining);
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    /** Each peer replays the rendered player's pose from a few frames ago, beside it in the fence. */
    private void publishBots() {
        List<GhostFrame> recent = List.copyOf(history);
        for (Bot bot : bots) {
            GhostFrame source = recent.get(Math.max(0, recent.size() - 1 - DELAYS[bot.index]));
            int x = Math.clamp(source.x() + OFFSETS[bot.index], area.minX() + 0x18,
                    area.maxX() + 0x120);
            bot.publisher.onFrame(new GhostFrame(x, source.y(), source.mappingFrame(),
                    bot.index == 1 ? !source.hFlip() : source.hFlip(), source.vFlip(), false,
                    source.priorityBucket(), source.highPriority()));
        }
    }

    private int lobbyGhosts() throws Exception {
        return lobbyGhostList().size();
    }

    @SuppressWarnings("unchecked")
    private List<com.openggf.mods.run.GhostPose> lobbyGhostList() throws Exception {
        if (session == null) return List.of();
        Object coordinator = call(session, "coordinator");
        if (coordinator == null) return List.of();
        return (List<com.openggf.mods.run.GhostPose>) call(coordinator, "lobbyGhosts");
    }

    /** The lobby ghosts as {@code name@x:y:opacity} entries for the state log. */
    private String ghostSummary() throws Exception {
        StringBuilder out = new StringBuilder();
        for (var ghost : lobbyGhostList()) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Integer.toHexString(ghost.pose().centreX())).append(':')
                    .append(Integer.toHexString(ghost.pose().centreY())).append(':')
                    .append(String.format(java.util.Locale.ROOT, "%.2f", ghost.opacity()));
        }
        return out.toString();
    }

    private void chapter(String name) {
        stage = name;
        System.out.println("stage=" + name + " frame=" + capture.frameNumber());
    }

    private void await(BooleanSupplier ready, int frames, String what) throws Exception {
        for (int i = 0; !ready.getAsBoolean() && i < frames; i++) capture.frame();
        if (!ready.getAsBoolean()) throw new IllegalStateException("timed out: " + what + " at " + stage);
    }

    private void run(Path root) throws Exception {
        String fingerprint;
        try (Rom rom = new Rom()) {
            if (!rom.open(root.resolve(romFile(gameId)).toString())) throw new IOException(gameId + " ROM not available");
            fingerprint = new DeterminismFingerprint(AppVersion.get(), rom.calculateChecksum()).asString();
        }
        capture.openTimeAttack();
        Object ownedScene = field(field(capture.engine().getGameLoop(), "modSceneHost"), "scene");
        Object scene = com.openggf.mods.code.OwnedSceneFactory.unwrap((com.openggf.mods.scene.ModScene) ownedScene);
        session = field(scene, "session");
        Object menu = field(scene, "state");
        chapter("Time Attack menu");
        capture.frames(30);
        for (int i = 0; i < 4 && !gameId.equals(call(menu, "currentGameId")); i++) capture.tap(GLFW_KEY_RIGHT);
        if (!gameId.equals(call(menu, "currentGameId"))) throw new IllegalStateException("game " + gameId + " not offered");
        for (int i = 0; i < 3; i++) capture.tap(GLFW_KEY_DOWN); // MODE
        capture.tap(GLFW_KEY_RIGHT);                             // HOST LAN
        for (int i = 0; i < 4; i++) capture.tap(GLFW_KEY_DOWN); // past policy, window and vote pool
        capture.tap(GLFW_KEY_ENTER);                             // CREATE LAN ROOM
        await(() -> safe(() -> field(scene, "accessView") != null), 120, "room access");
        capture.tap(GLFW_KEY_DOWN);
        capture.tap(GLFW_KEY_DOWN);
        capture.tap(GLFW_KEY_ENTER);
        await(() -> state().equals("LOBBY"), 1200, "the lobby");
        chapter("Lobby level loading");
        await(() -> capture.engine().getCurrentGameMode() == GameMode.LEVEL, 1200, "the lobby level");

        String invite = ((String) call(session, "lanInvite")).replace("HOST_IP", "127.0.0.1");
        DirectJoinAddress address = DirectJoinAddress.parse(invite, 0);
        for (int i = 0; i < NAMES.length; i++) {
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(out.resolve("bots/bot-" + i));
            String look = CharacterLooks.withBaseColour(gameId, "sonic", COLOURS[i]).encode();
            RaceClient client = RaceClient.connect(address.uri(), identity, NAMES[i], look, fingerprint,
                    address.certificateSha256(), address.hostFingerprint()).get(20, TimeUnit.SECONDS);
            Bot bot = new Bot(client, i);
            bots.add(bot);
            bot.publisher.beginStream();
        }
        streaming = true;
        await(() -> safe(() -> lobbyGhosts() == NAMES.length), 600, "three lobby ghosts");
        capture.frames(240); // the title card clears and the peers settle beside the player
        chapter("Menu over the lobby level");
        capture.png("lobby-01-menu-over-level.png");

        for (Bot bot : bots) bot.client.sendControl(new ControlMessage.SetReady(true));
        capture.frames(30);
        capture.tap(GLFW_KEY_ENTER); // READY UP
        await(() -> safe(this::localReady), 300, "ready");
        capture.frames(30);
        chapter("Everyone ready");
        capture.png("lobby-02-everyone-ready.png");

        capture.tap(GLFW_KEY_ENTER); // ready: Enter hides the menu and hands over the controller
        if ((boolean) call(session, "lobbyMenuShown")) throw new IllegalStateException("the menu did not hide");
        chapter("Menu hidden, running");
        capture.key(GLFW_KEY_RIGHT, true);
        for (int i = 0; i < 150; i++) {
            if (i % 45 == 10) capture.key(GLFW_KEY_SPACE, true);
            if (i % 45 == 20) capture.key(GLFW_KEY_SPACE, false);
            capture.frame();
            if (i == 70) capture.png("lobby-03-menu-hidden-running.png");
        }
        capture.key(GLFW_KEY_SPACE, false);
        capture.key(GLFW_KEY_RIGHT, false);
        capture.key(GLFW_KEY_LEFT, true);
        for (int i = 0; i < 120; i++) {
            capture.frame();
            if (i == 60) capture.png("lobby-04-running-back-with-ghosts.png");
        }
        capture.key(GLFW_KEY_LEFT, false);
        capture.frames(60);
        capture.png("lobby-05-ghosts-catch-up.png");

        capture.tap(GLFW_KEY_ENTER); // the menu comes back
        if (!(boolean) call(session, "lobbyMenuShown")) throw new IllegalStateException("the menu did not return");
        chapter("Menu back");
        capture.frames(40);
        capture.png("lobby-06-menu-back.png");

        capture.tap(GLFW_KEY_DOWN);  // START ROUND
        capture.frames(10);
        capture.png("lobby-06b-start-round-focused.png");
        capture.tap(GLFW_KEY_SPACE); // once ready, A confirms (Start/Enter is the toggle)
        capture.frames(10);
        capture.png("lobby-06c-start-pressed.png");
        chapter("Round start hand-over");
        await(() -> safe(() -> state().equals("RACING")), 1200, "the race run");
        await(() -> capture.engine().getCurrentGameMode() == GameMode.LEVEL, 1200, "the race level");
        capture.frames(200);
        chapter("Race level");
        capture.png("lobby-07-race-level.png");
        Files.writeString(out.resolve("result.txt"), "success=true\ngame=" + gameId + "\nframes="
                + capture.frameNumber() + "\nbots=" + bots.size() + "\n");
    }

    private boolean localReady() throws Exception {
        Object coordinator = call(session, "coordinator");
        Object race = call(coordinator, "session");
        return (boolean) race.getClass().getMethod("isLocalReady").invoke(race);
    }

    private String state() {
        try { return call(session, "state").toString(); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private interface Check { boolean get() throws Exception; }

    private static boolean safe(Check check) {
        try { return check.get(); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object call(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private void close() throws Exception {
        capture.afterFrame(ignored -> { });
        Exception failure = null;
        for (Bot bot : bots) {
            try { bot.client.close(); } catch (Exception closeFailure) { failure = closeFailure; }
        }
        states.close();
        if (video != null) video.close();
        if (encoder != null && !encoder.waitFor(30, TimeUnit.SECONDS)) encoder.destroyForcibly();
        capture.close();
        if (failure != null) throw failure;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("OUTPUT_DIR REPOSITORY_ROOT GAME");
        Path root = Path.of(args[1]).toAbsolutePath();
        TimeAttackLobbyVideoTool tool = new TimeAttackLobbyVideoTool(Path.of(args[0]), root, args[2]);
        try {
            tool.run(root);
        } finally {
            tool.close();
        }
    }
}
