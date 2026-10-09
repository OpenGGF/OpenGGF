package openggf.timeattack;

import com.openggf.control.InputHandler;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.scene.SceneGameplay;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.mods.scene.host.SceneServices;
import openggf.racing.protocol.ControlMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Time Attack title-entry scene, driven through the engine's scene host. */
class TestTimeAttackScene {
    @TempDir
    Path temp;

    private final ModSceneHost host = new ModSceneHost();
    private final InputHandler input = new InputHandler();
    private final List<String> exits = new ArrayList<>();
    private final List<RunSpec> launched = new ArrayList<>();
    private final List<RunHost> hosts = new ArrayList<>();

    @AfterEach
    void close() {
        host.close();
    }

    private Optional<String> fingerprint = Optional.empty();
    private TimeAttackScene scene;

    private void open(List<String> games) {
        SceneGameplay gameplay = new SceneGameplay() {
            @Override public List<String> availableGames() { return games; }
            @Override public Optional<String> determinismFingerprint(String gameId) { return fingerprint; }
            @Override public RunHandle launch(RunSpec spec, RunHost runHost) {
                launched.add(spec);
                hosts.add(runHost);
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
        host.open(ModContextTestAccess.ownedScene("time-attack", () -> {
                    scene = new TimeAttackScene(temp.resolve("ghosts"), temp.resolve("identity"));
                    return scene;
                }, boundary),
                new SceneServices(null, null, temp, null, () -> exits.add("game"), () -> exits.add("master"),
                        null, gameplay),
                320, 224);
    }

    private void press(int glfwKey) {
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        host.update(input);
        input.update();
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        host.update(input);
        input.update();
    }

    @Test
    void fieldSelectionDoesNotLaunchButTheStartRowLaunchesAnIsolatedAct() {
        open(List.of("s2"));
        for (int i = 0; i < 4; i++) press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertTrue(launched.isEmpty(), "field selection must not launch a run");
        press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals(1, launched.size());
        RunSpec spec = launched.get(0);
        assertEquals("s2", spec.gameId());
        assertEquals(GameplayRunPolicy.isolatedAct(), spec.policy());
        assertInstanceOf(TimeAttackRuntime.class, hosts.get(0));
    }

    @Test
    void backLeavesForTheMasterTitle() {
        open(List.of("s1"));
        press(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        assertEquals(List.of("master"), exits);
        assertTrue(launched.isEmpty());
    }

    @Test
    void withoutGameRomsTheSceneOnlyOffersTheWayBack() {
        open(List.of());
        press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals(List.of("master"), exits);
        assertTrue(launched.isEmpty());
    }

    @Test
    void resumingAfterARunKeepsTheMenuUsable() {
        open(List.of("s2"));
        for (int i = 0; i < 5; i++) press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals(1, launched.size());
        host.resume(RunEndReason.ACT_COMPLETED);
        press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals(2, launched.size(), "the menu returns focused on START RUN");
    }

    // ── Multiplayer through the scene ────────────────────────────────────

    private static final String FP = "0.8:cafe1234";

    /** One key tap with the logical snapshot refreshed, so mapped pad buttons reach the scene too. */
    private void tap(int glfwKey, boolean shift) {
        if (shift) input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        input.refreshLogicalSnapshot();
        host.update(input);
        input.update();
        input.handleKeyEvent(glfwKey, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        if (shift) input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        idle();
    }

    private void tap(int glfwKey) {
        tap(glfwKey, false);
    }

    private void idle() {
        input.refreshLogicalSnapshot();
        host.update(input);
        input.update();
    }

    private void type(String text) {
        for (char c : text.toCharArray()) {
            int[] key = openggf.timeattack.ui.FakeViewInput.keyFor(c);
            tap(key[0], key[1] != 0);
        }
    }

    private void idleUntil(String what, java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                org.junit.jupiter.api.Assertions.fail("timed out waiting for " + what);
            }
            idle();
            Thread.sleep(10);
        }
    }

    /** From the GAME row, moves to MODE and steps the mode right {@code steps} times. */
    private void chooseMode(int steps) {
        for (int i = 0; i < 3; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
        for (int i = 0; i < steps; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
    }

    @Test
    void multiplayerWithoutAFingerprintExplainsTheMissingRom() {
        open(List.of("s2"));
        chooseMode(1); // HOST LAN
        for (int i = 0; i < 3; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // past policy and window
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals("The S2 ROM is not available for racing", scene.status());
        assertEquals(openggf.timeattack.mp.RaceSession.State.IDLE, scene.raceSession().state());
        assertTrue(launched.isEmpty());
    }

    @Test
    void joiningNeedsAnInviteFirst() {
        fingerprint = Optional.of(FP);
        open(List.of("s2"));
        chooseMode(2); // JOIN LAN
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // invite row
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // JOIN LAN ROOM
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals("Enter the host's LAN invite first", scene.status());
        assertEquals(openggf.timeattack.mp.RaceSession.State.IDLE, scene.raceSession().state());
    }

    @Test
    void textInputIsClaimedOnlyWhileAFieldIsFocused() {
        fingerprint = Optional.of(FP);
        open(List.of("s2"));
        assertFalse(host.capturesTextInput(), "menu");
        chooseMode(2); // JOIN LAN
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // invite row
        assertFalse(host.capturesTextInput(), "invite row focused but not editing");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertTrue(host.capturesTextInput(), "invite field");
        type("vv[]");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        assertFalse(host.capturesTextInput(), "invite field cancelled");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // JOIN LAN ROOM
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // SETTINGS
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertFalse(host.capturesTextInput(), "settings page");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER); // display name
        assertTrue(host.capturesTextInput(), "display name field");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertFalse(host.capturesTextInput(), "display name accepted");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // master URL
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertTrue(host.capturesTextInput(), "master URL field");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        assertFalse(host.capturesTextInput(), "master URL cancelled");
    }

    @Test
    void settingsPageSavesToTheModStorage() throws Exception {
        open(List.of("s2"));
        for (int i = 0; i < 5; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // SETTINGS
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER); // display name editor
        type("Ray");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals("Ray", scene.settings().displayName());
        Path saved;
        try (var files = java.nio.file.Files.walk(temp)) {
            saved = files.filter(p -> p.getFileName().toString().equals(TimeAttackSettings.FILE_NAME))
                    .findFirst().orElseThrow();
        }
        assertTrue(java.nio.file.Files.readString(saved).contains("displayName=Ray"));
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE); // settings -> menu
        assertTrue(exits.isEmpty());
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE); // menu -> master title
        assertEquals(List.of("master"), exits);
    }

    @Test
    void lanJoinThroughTheSceneRacesARoundAndLeavingClosesTheConnection() throws Exception {
        openggf.racing.identity.PlayerIdentity roomIdentity =
                openggf.racing.identity.PlayerIdentity.loadOrCreate(temp.resolve("room-host"));
        try (openggf.racing.host.jdk.JdkRaceHostServer room = openggf.racing.host.jdk.JdkRaceHostServer.startTls(0,
                new openggf.racing.hub.RoomHostConfig("LAN", "s2", 0, 0, "OPEN", null, 8, FP),
                roomIdentity, openggf.racing.hub.TrackValidationProfileSource.none())) {
            String invite = "127.0.0.1:" + room.port() + "#" + openggf.racing.client.DirectJoinAddress.shareCode(
                    room.tlsCertificateSha256(), roomIdentity.fingerprint());
            fingerprint = Optional.of(FP);
            open(List.of("s2"));
            chooseMode(2); // JOIN LAN
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // invite row
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            type(invite);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // JOIN LAN ROOM
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            idleUntil("the lobby", () -> scene.raceSession().state() == openggf.timeattack.mp.RaceSession.State.LOBBY);
            assertEquals(invite, scene.settings().lastJoinAddress());
            assertEquals(1, onRoomThread(room, () -> room.room().playerCount()));
            assertFalse(host.capturesTextInput(), "lobby");
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER); // a guest's lobby opens on WRITE CHAT MESSAGE
            assertTrue(host.capturesTextInput(), "room chat field");
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
            assertFalse(host.capturesTextInput(), "room chat cancelled");
            assertEquals(openggf.timeattack.mp.RaceSession.State.LOBBY, scene.raceSession().state());

            ControlMessage.RoundConfig round = new ControlMessage.RoundConfig("s2", 0, 0, 60, "OPEN", null);
            assertTrue(onRoomThread(room, () -> room.room().requestStartRound(round)));
            idleUntil("the round run", () -> scene.raceSession().state() == openggf.timeattack.mp.RaceSession.State.RACING);
            assertEquals(List.of(new RunSpec("s2", 0, 0, "sonic", GameplayRunPolicy.isolatedAct())), launched);
            host.resume(RunEndReason.ACT_COMPLETED);
            assertEquals(openggf.timeattack.mp.RaceSession.State.LOBBY, scene.raceSession().state());

            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE); // leave the room
            assertEquals(openggf.timeattack.mp.RaceSession.State.IDLE, scene.raceSession().state());
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            while (onRoomThread(room, () -> room.room().playerCount()) != 0) {
                assertTrue(System.nanoTime() < deadline, "the room still counts the player");
                Thread.sleep(20);
            }
        }
    }

    private static <T> T onRoomThread(openggf.racing.host.jdk.JdkRaceHostServer room,
                                      java.util.function.Supplier<T> read) throws Exception {
        java.util.concurrent.CompletableFuture<T> result = new java.util.concurrent.CompletableFuture<>();
        room.execute(() -> result.complete(read.get()));
        return result.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
}
