package openggf.timeattack;

import com.openggf.control.InputHandler;
import com.openggf.graphics.MenuGlyphs;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.mods.run.TrackKind;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneGameplay;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.mods.scene.host.SceneServices;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.client.RaceClient;
import com.openggf.net.protocol.ControlMessage;
import openggf.timeattack.CompetitionHudState.MassStartPhase;
import openggf.timeattack.CompetitionHudState.SwapNotice;
import openggf.timeattack.mp.HudTextLayout;
import openggf.timeattack.mp.MultiplayerHudRenderer;
import openggf.timeattack.mp.MultiplayerHudState;
import openggf.timeattack.mp.MultiplayerRaceCoordinator;
import openggf.timeattack.mp.RaceLobbyScreen;
import openggf.timeattack.mp.RaceLobbyView;
import openggf.timeattack.mp.RaceTransport;
import openggf.timeattack.mp.RoomDirectory;
import openggf.timeattack.mp.ServerBrowserView;
import openggf.timeattack.ui.CompetitionSamples;
import openggf.timeattack.ui.FakeViewInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Design pictures of the Competition GUI surfaces at 320 and 400 pixels wide, for review.
 *
 * <p>Purpose: lane D's picture checkpoint (room settings, the ARENAS group, the mass-start plate, the
 * arena HUD, the cutoff and swap plates, the results table, the race menu and the room browser).
 * Inputs: the {@code openggf.capture.out} directory and, for the over-level pictures, the S3K frame
 * {@code docs/s3k-ref/aiz-intro-sonic-airborne-ref.png} (an AIZ level frame; the arena view is not
 * merged yet). The native arena HUD's screen boxes (CompetitionHudObjectInstance) are outlined in
 * magenta as stand-ins. Every mod surface draws only fills (NameFont and CompactFont are pixel runs);
 * page titles, which the engine draws in its bold menu face, are rastered here from
 * {@link MenuGlyphs}. No GL context, window or audio is opened.
 * Usage: {@code python3 tools/testing/maven_queue.py --normal -Dmse=off
 * -Dopenggf.capture.out=$PWD/target/lane-d-captures -Dtest=CompetitionDesignCaptureTool test}.
 * The class name matches no surefire include, so only an explicit selection runs it.
 * Origin: 2026-11-07 Competition GUI lane D.
 */
class CompetitionDesignCaptureTool {
    private static final int[][] NATIVE_ARENA_HUD = {
            {0xC8 - 0x80 - 0x40, 0x90 - 0x80 - 0x10, 0x80, 0x20},
            {0x190 - 0x80 - 8, 0x98 - 0x80 - 0x0C, 16, 0x18},
            {0x120 - 0x80 - 0x10, 0xE0 - 0x80 - 0x10, 0x20, 0x20}};

    @TempDir
    Path temp;

    @Test
    void capture() throws Exception {
        String target = System.getProperty("openggf.capture.out");
        if (target == null) return;
        Path out = Path.of(target);
        Files.createDirectories(out);
        BufferedImage frame = ImageIO.read(Path.of("docs/s3k-ref/aiz-intro-sonic-airborne-ref.png").toFile());
        for (int width : new int[] {320, 400}) {
            menus(out, width);
            hud(out, width, frame);
            lobby(out, width);
            browser(out, width);
        }
    }

    // ── Pages through the real scene host ────────────────────────────────

    private void menus(Path out, int width) throws Exception {
        ModSceneHost host = new ModSceneHost();
        InputHandler input = new InputHandler();
        TimeAttackScene[] scene = new TimeAttackScene[1];
        SceneGameplay gameplay = new SceneGameplay() {
            @Override public List<String> availableGames() { return List.of("s2", "s3k"); }
            @Override public Optional<String> determinismFingerprint(String gameId) { return Optional.empty(); }
            @Override public RunHandle launch(RunSpec spec, RunHost runHost) {
                return new RunHandle() {
                    @Override public void retry() { }
                    @Override public void leave() { }
                    @Override public void spectate(boolean active, int dx, int dy) { }
                    @Override public boolean isActive() { return true; }
                };
            }
        };
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        Path root = temp.resolve("w" + width);
        host.open(ModContextTestAccess.ownedScene("time-attack", () -> {
            scene[0] = new TimeAttackScene(root.resolve("ghosts"), root.resolve("identity"));
            return scene[0];
        }, boundary), new SceneServices(null, null, root, null, () -> { }, () -> { }, null, gameplay), width, 224);
        try {
            Consumer<Integer> tap = key -> {
                input.handleKeyEvent(key, GLFW_PRESS);
                input.refreshLogicalSnapshot();
                host.update(input);
                input.update();
                input.handleKeyEvent(key, GLFW_RELEASE);
                input.refreshLogicalSnapshot();
                host.update(input);
                input.update();
            };
            if (scene[0].isPromptingForName()) {
                for (char c : "Tester".toCharArray()) {
                    com.openggf.control.MenuInput.handleCharEvent(input, c);
                    input.refreshLogicalSnapshot();
                    host.update(input);
                    input.update();
                }
                tap.accept(GLFW_KEY_ENTER);
            }
            tap.accept(GLFW_KEY_RIGHT); // s2 -> s3k
            for (int i = 0; i < 3; i++) tap.accept(GLFW_KEY_DOWN); // Mode
            tap.accept(GLFW_KEY_RIGHT); // Host a LAN room
            tap.accept(GLFW_KEY_DOWN); // Race mode
            tap.accept(GLFW_KEY_RIGHT); // Competition
            // Rows: Game 0, Track 1, Character 2, Mode 3, Race mode 4, Characters 5, Monitors 6, Cutoff 7,
            // Start in 8, Vote pool 9, then the button.
            for (int i = 0; i < 2; i++) tap.accept(GLFW_KEY_DOWN); // Monitors
            shot(out, "01-room-settings-competition", width, canvas -> scene[0].draw(null, canvas), null);
            for (int i = 0; i < 3; i++) tap.accept(GLFW_KEY_DOWN); // Vote pool: the rows scroll
            shot(out, "02-room-settings-scrolled", width, canvas -> scene[0].draw(null, canvas), null);
            for (int i = 0; i < 8; i++) tap.accept(GLFW_KEY_UP); // Track
            tap.accept(GLFW_KEY_LEFT); // the last arena, Endless Mine
            tap.accept(GLFW_KEY_LEFT); // Desert Palace
            shot(out, "03-track-arena-badges", width, canvas -> scene[0].draw(null, canvas), null);
            for (int i = 0; i < 8; i++) tap.accept(GLFW_KEY_DOWN); // Vote pool
            tap.accept(GLFW_KEY_ENTER); // the pool editor
            tap.accept(GLFW_KEY_ENTER); // Default: a new pool
            int acts = TimeAttackTrackCatalog.eligibleFor("s3k").size();
            for (int i = 0; i < acts + 2; i++) tap.accept(GLFW_KEY_DOWN); // Balloon Park
            tap.accept(GLFW_KEY_ENTER);
            shot(out, "04-pool-editor-arenas", width, canvas -> scene[0].draw(null, canvas), null);
        } finally {
            host.close();
        }
    }

    // ── Over-level HUD plates ────────────────────────────────────────────

    private static void hud(Path out, int width, BufferedImage frame) throws Exception {
        record Picture(String name, String phase, CompetitionHudState state, boolean finished) { }
        List<Picture> pictures = List.of(
                new Picture("05-mass-start-loading", "COUNTDOWN", CompetitionSamples.start(TrackKind.ARENA,
                        MassStartPhase.LOADING), false),
                new Picture("06-mass-start-held", "COUNTDOWN", CompetitionSamples.start(TrackKind.ARENA,
                        MassStartPhase.HELD), false),
                new Picture("07-arena-hud", "RUNNING", CompetitionSamples.arena(false, SwapNotice.none(), false), false),
                new Picture("08-arena-cutoff-unverified", "RUNNING", CompetitionSamples.arena(true, SwapNotice.none(), true),
                        false),
                new Picture("09-arena-swap", "RUNNING", CompetitionSamples.arena(true,
                        SwapNotice.applied("Knuckles Fan"), false), false),
                new Picture("10-arena-swap-none", "RUNNING", CompetitionSamples.arena(false, SwapNotice.fallback(), false),
                        false),
                new Picture("11-act-hud-cutoff", "RUNNING", CompetitionSamples.act(true), false),
                new Picture("12-finished-live-order", "RUNNING", CompetitionSamples.results(true), true));
        for (Picture picture : pictures) {
            MultiplayerHudState state = CompetitionSamples.room(picture.phase(), picture.state());
            shot(out, picture.name(), width, canvas -> {
                MultiplayerHudRenderer.render(canvas, state, 4096, 100, true, picture.finished());
                TimeAttackHudState clock = new TimeAttackHudState(true, 6000, -1, Integer.MIN_VALUE, picture.finished(),
                        false, "", picture.state());
                HudTextLayout.Panel panel = TimeAttackHud.layout(width, 224, clock);
                if (panel != null) HudTextLayout.drawPanel(canvas, panel);
            }, frame);
        }
        for (boolean confirm : new boolean[] {false, true}) {
            RaceLobbyScreen.RaceMenu menu = new RaceLobbyScreen.RaceMenu();
            if (confirm) {
                menu.update(false, true, false, false);
                menu.update(false, false, true, false);
            }
            MultiplayerHudState state = CompetitionSamples.room("RUNNING",
                    CompetitionSamples.arena(true, SwapNotice.none(), false));
            shot(out, confirm ? "14-race-menu-confirm" : "13-race-menu", width, canvas -> {
                MultiplayerHudRenderer.render(canvas, state, 4096, 100, true, false);
                menu.draw(canvas);
            }, frame);
        }
    }

    // ── Lobby and results ────────────────────────────────────────────────

    private static void lobby(Path out, int width) throws Exception {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        session.applyJoin(new ControlMessage.JoinAccepted("t", 0,
                new ControlMessage.RoomDescriptor("Arena night", "s3k", 2, 0, "OPEN", null, 64, false,
                        com.openggf.net.protocol.RoomAccessPolicy.PUBLIC, "FULL_CHAT",
                        "ARENA", "COMPETITION"),
                new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of())));
        RaceTransport transport = new RaceTransport() {
            @Override public List<RaceClient.InboundEvent> drainInbound() { return List.of(); }
            @Override public void sendControl(ControlMessage message) { }
            @Override public void sendBinary(byte[] bytes) { }
            @Override public int playerSlot() { return 0; }
            @Override public boolean isOpen() { return true; }
            @Override public void close() { }
        };
        ControlMessage.RoundConfig round = new ControlMessage.RoundConfig("s3k", 2, 0, 300, "OPEN", null, "ARENA",
                "COMPETITION", "COMPETITION_ITEMS", 60);
        RaceLobbyView view = new RaceLobbyView(new MultiplayerRaceCoordinator(transport, session), false, round, null,
                () -> { }, cue -> { });
        FakeViewInput input = new FakeViewInput();
        view.setCompetitionSource(() -> CompetitionSamples.start(TrackKind.ARENA, MassStartPhase.LOADING));
        shot(out, "15-lobby-mass-start", width, view::draw, null);
        view.setCompetitionSource(CompetitionSamples::lateJoin);
        shot(out, "16-lobby-late-join", width, view::draw, null);
        view.setCompetitionSource(() -> CompetitionSamples.results(true));
        shot(out, "17-lobby-finish-order", width, view::draw, null);
        input.tapDown(view::update);
        input.tapEnter(view::update);
        shot(out, "18-results-table-unverified", width, view::draw, null);
        input.tapEnter(view::update);
        shot(out, "19-results-lap-splits", width, view::draw, null);
    }

    private static void browser(Path out, int width) throws Exception {
        List<ControlMessage.RoomSummary> rooms = List.of(
                new ControlMessage.RoomSummary("a", "Arena night", "s3k", 2, 0, "OPEN", 12, 64, "RELAY", false,
                        "FULL_CHAT", "ARENA", "COMPETITION"),
                new ControlMessage.RoomSummary("b", "Hydrocity practice", "s3k", 1, 0, "OPEN", 3, 8, "DIRECT", true,
                        "QUICK_CHAT"),
                new ControlMessage.RoomSummary("c", "Marble Garden race", "s3k", 2, 1, "OPEN", 6, 16, "RELAY", false,
                        "FULL_CHAT", "ACT", "COMPETITION"));
        RoomDirectory directory = new RoomDirectory() {
            @Override public boolean isOpen() { return true; }
            @Override public CompletableFuture<ControlMessage.RoomListResult> listRooms(String gameId, int page) {
                return CompletableFuture.completedFuture(new ControlMessage.RoomListResult(rooms, 0, 1));
            }
        };
        ServerBrowserView view = new ServerBrowserView(directory, "s3k", new ServerBrowserView.Actions() {
            @Override public void join(ControlMessage.RoomSummary room) { }
            @Override public void create(String routing) { }
            @Override public void back() { }
        }, cue -> { }, () -> 0L);
        FakeViewInput input = new FakeViewInput();
        input.idle(view::update);
        input.idle(view::update);
        shot(out, "20-server-browser", width, view::draw, null);
    }

    // ── Raster ───────────────────────────────────────────────────────────

    private interface Drawing {
        void draw(RasterCanvas canvas) throws Exception;
    }

    private static void shot(Path out, String name, int width, Drawing drawing, BufferedImage frame) throws Exception {
        RasterCanvas canvas = new RasterCanvas(width);
        if (frame != null) {
            canvas.backdrop(frame);
            canvas.drawing = true;
            for (int[] box : NATIVE_ARENA_HUD) canvas.outline(box[0] + (width - 320) / 2, box[1], box[2], box[3]);
        }
        drawing.draw(canvas);
        ImageIO.write(canvas.image(), "png", out.resolve(name + "-" + width + ".png").toFile());
    }

    /** A software {@link SceneCanvas}: source-over fills, and the bold title face from {@link MenuGlyphs}. */
    static final class RasterCanvas implements SceneCanvas {
        private final int width;
        private final int[] pixels;
        boolean drawing;

        RasterCanvas(int width) {
            this.width = width;
            this.pixels = new int[width * 224];
        }

        void backdrop(BufferedImage frame) {
            // The reference is two captures side by side: stretch its left one over the canvas (nearest).
            double scaleX = frame.getWidth() / 2.0 / width;
            double scaleY = frame.getHeight() / 224.0;
            for (int y = 0; y < 224; y++) {
                for (int x = 0; x < width; x++) {
                    int sx = Math.min(frame.getWidth() / 2 - 1, (int) (x * scaleX));
                    int sy = Math.min(frame.getHeight() - 1, (int) (y * scaleY));
                    pixels[y * width + x] = 0xFF000000 | frame.getRGB(sx, sy);
                }
            }
        }

        void outline(int x, int y, int w, int h) {
            int stand = 0xC0FF40FF;
            fill(x, y, w, 1, stand);
            fill(x, y + h - 1, w, 1, stand);
            fill(x, y, 1, h, stand);
            fill(x + w - 1, y, 1, h, stand);
        }

        BufferedImage image() {
            BufferedImage image = new BufferedImage(width, 224, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, width, 224, pixels, 0, width);
            return image;
        }

        @Override public int width() { return width; }
        @Override public int height() { return 224; }

        @Override public void clear(int rgb) {
            // Over a level the scene's clear is the level showing through; pages clear opaquely.
            if (!drawing) java.util.Arrays.fill(pixels, 0xFF000000 | rgb);
        }

        @Override public void fill(int x, int y, int w, int h, int argb) {
            int alpha = argb >>> 24;
            if (alpha == 0) return;
            for (int row = Math.max(0, y); row < Math.min(224, y + h); row++) {
                for (int column = Math.max(0, x); column < Math.min(width, x + w); column++) {
                    int below = pixels[row * width + column];
                    int r = blend(argb >> 16 & 0xFF, below >> 16 & 0xFF, alpha);
                    int g = blend(argb >> 8 & 0xFF, below >> 8 & 0xFF, alpha);
                    int b = blend(argb & 0xFF, below & 0xFF, alpha);
                    pixels[row * width + column] = 0xFF000000 | r << 16 | g << 8 | b;
                }
            }
        }

        private static int blend(int top, int below, int alpha) {
            return (top * alpha + below * (255 - alpha)) / 255;
        }

        @Override public void draw(SceneImage image, float x, float y) { }
        @Override public void draw(SceneImage image, float x, float y, SceneDraw style) { }
        @Override public void draw(SceneSprite sprite, float x, float y, SceneDraw style) { }
        @Override public void drawRegion(SceneImage image, int sx, int sy, int sw, int sh, float dx, float dy,
                                         float dw, float dh, SceneDraw style) { }

        @Override public void text(String text, int x, int y, int argb) {
            int pen = x;
            for (int i = 0; i < text.length(); ) {
                int codePoint = text.codePointAt(i);
                i += Character.charCount(codePoint);
                int index = MenuGlyphs.index(codePoint);
                if (index < 0) index = MenuGlyphs.index('?');
                byte[] rows = MenuGlyphs.proportionalRows(index);
                int left = MenuGlyphs.inkLeftIndex(MenuGlyphs.Face.BOLD, index);
                for (int row = 0; row < MenuGlyphs.FULL_HEIGHT; row++) {
                    int bits = MenuGlyphs.fullRow(rows, row);
                    for (int column = 0; column < MenuGlyphs.FULL_ADVANCE; column++) {
                        if ((bits >> (MenuGlyphs.FULL_ADVANCE - 1 - column) & 1) != 0) {
                            fill(pen + column - left, y + row, 1, 1, argb);
                        }
                    }
                }
                pen += MenuGlyphs.advance(MenuGlyphs.Face.BOLD, codePoint);
            }
        }

        @Override public int textWidth(String text) {
            int width = 0;
            for (int i = 0; i < text.length(); ) {
                int codePoint = text.codePointAt(i);
                i += Character.charCount(codePoint);
                width += MenuGlyphs.advance(MenuGlyphs.Face.BOLD, codePoint);
            }
            return width;
        }

        @Override public void clip(int x, int y, int w, int h) { }
        @Override public void unclip() { }
    }
}
