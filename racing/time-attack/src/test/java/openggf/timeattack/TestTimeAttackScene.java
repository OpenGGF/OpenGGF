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
import com.openggf.net.protocol.ControlMessage;
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
    private int retries;
    /** Runs launched behind the scene (the lobby level) when {@link #behindSupported} is set. */
    private final List<RunSpec> behind = new ArrayList<>();
    private final List<FakeHandle> behindHandles = new ArrayList<>();
    private boolean behindSupported;

    /** A run handle that records the commands a lobby level receives. */
    private static final class FakeHandle implements RunHandle {
        boolean held;
        boolean covered;
        int holdChanges;
        boolean left;
        @Override public void retry() { }
        @Override public void leave() { left = true; }
        @Override public void spectate(boolean active, int dx, int dy) { }
        @Override public void holdControls(boolean hold) { held = hold; holdChanges++; }
        @Override public void setLevelCovered(boolean cover) { covered = cover; }
        @Override public boolean isActive() { return true; }
    }

    @AfterEach
    void close() {
        host.close();
    }

    private Optional<String> fingerprint = Optional.empty();
    private TimeAttackScene scene;

    /** Opens the scene and answers the first-run name prompt, as a new player does. */
    private void open(List<String> games) {
        openFresh(games);
        if (scene.isPromptingForName()) {
            type("Tester");
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        }
    }

    private void openFresh(List<String> games) {
        SceneGameplay gameplay = new SceneGameplay() {
            @Override public List<String> availableGames() { return games; }
            @Override public Optional<String> determinismFingerprint(String gameId) { return fingerprint; }
            @Override public RunHandle launch(RunSpec spec, RunHost runHost) {
                launched.add(spec);
                hosts.add(runHost);
                return new RunHandle() {
                    @Override public void retry() { retries++; }
                    @Override public void leave() { }
                    @Override public void spectate(boolean active, int dx, int dy) { }
                    @Override public boolean isActive() { return true; }
                };
            }
            @Override public RunHandle launchBehind(RunSpec spec, RunHost runHost) {
                if (!behindSupported) return SceneGameplay.super.launchBehind(spec, runHost);
                behind.add(spec);
                hosts.add(runHost);
                FakeHandle handle = new FakeHandle();
                behindHandles.add(handle);
                return handle;
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
        assertEquals(GameplayRunPolicy.isolatedAct().withFastRestarts().withoutZoneIntros(), spec.policy());
        assertInstanceOf(TimeAttackRuntime.class, hosts.get(0));
    }

    @Test
    void importedRetryKeyDrivesTheLaunchedRunInsteadOfTheDefaultKey() throws Exception {
        java.nio.file.Files.writeString(temp.resolve("config.yaml"), "timeAttack:\n  retryKey: F6\n");
        open(List.of("s2"));
        for (int i = 0; i < 5; i++) press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        RunHost runtime = hosts.getFirst();
        runtime.admitStep(runKey(com.openggf.mods.scene.SceneKeys.R));
        assertEquals(0, retries);
        runtime.admitStep(runKey(com.openggf.mods.scene.SceneKeys.F6));
        assertEquals(1, retries);
    }

    private static com.openggf.mods.run.RunInput runKey(int pressed) {
        return new com.openggf.mods.run.RunInput() {
            @Override public com.openggf.control.LogicalInputSnapshot input() { return null; }
            @Override public boolean keyDown(int key) { return key == pressed; }
            @Override public boolean keyPressed(int key) { return key == pressed; }
        };
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

    @Test
    void aNewPlayerNamesThemselvesBeforeAnyModeAndTheNameIsSaved() throws Exception {
        openFresh(List.of("s2"));
        assertTrue(scene.isPromptingForName());
        assertTrue(host.capturesTextInput(), "the name prompt takes text");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertTrue(scene.isPromptingForName(), "an empty name cannot skip the prompt");
        assertTrue(launched.isEmpty());
        type("Nguyễn ");
        com.openggf.control.MenuInput.handleCharEvent(input, ' ');
        type("花子"); // an IME commits 花子 as two characters
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertFalse(scene.isPromptingForName());
        assertEquals("Nguyễn 花子", scene.settings().displayName(), "canonical: single spaces, NFC");
        Path saved;
        try (var files = java.nio.file.Files.walk(temp)) {
            saved = files.filter(p -> p.getFileName().toString().equals(TimeAttackSettings.FILE_NAME))
                    .findFirst().orElseThrow();
        }
        assertTrue(java.nio.file.Files.readString(saved).contains("displayName=Nguyễn 花子"));
        for (int i = 0; i < 5; i++) press(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals(1, launched.size(), "the menu works once the name is chosen");
    }

    @Test
    void escapeAtTheNamePromptLeavesInsteadOfSkippingIt() {
        openFresh(List.of("s2"));
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        assertEquals(List.of("master"), exits);
        assertTrue(scene.isPromptingForName());
    }

    @Test
    void anExistingIdentityWithoutANameIsNotPrompted() throws Exception {
        java.nio.file.Files.createDirectories(temp.resolve("identity"));
        java.nio.file.Files.writeString(temp.resolve("identity").resolve("player-identity.key"), "legacy");
        openFresh(List.of("s2"));
        assertFalse(scene.isPromptingForName(), "legacy profiles keep the identity-prefix name");
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

    /** Types {@code text} as GLFW delivers it: one character event per code point, one tick each. */
    private void type(String text) {
        text.codePoints().forEach(codePoint -> {
            com.openggf.control.MenuInput.handleCharEvent(input, codePoint);
            idle();
        });
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
        for (int i = 0; i < 4; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // past policy, window and vote pool
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals("The S2 ROM is not available for racing", scene.status());
        assertEquals(openggf.timeattack.mp.RaceSession.State.IDLE, scene.raceSession().state());
        assertTrue(launched.isEmpty());
    }

    @Test
    void everyModeFitsTheFrameAndItsKeysNameWhatTheFocusedRowDoes() {
        open(List.of("s2"));
        for (int width : new int[] {320, 400}) {
            openggf.timeattack.ui.RecordingSceneCanvas canvas = new openggf.timeattack.ui.RecordingSceneCanvas(width);
            scene.draw(null, canvas);
            canvas.assertFillsOnScreen();
            assertTrue(canvas.shows("NEXT") && canvas.shows("TITLE"), "a field row advances; Back leaves");
            assertTrue(canvas.shows("START RUN"));
        }
        for (int i = 0; i < 4; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // the action row
        openggf.timeattack.ui.RecordingSceneCanvas action = new openggf.timeattack.ui.RecordingSceneCanvas(320);
        scene.draw(null, action);
        assertTrue(action.shows("START") && !action.shows("NEXT"), "Enter on the action row starts the run");
        for (int i = 0; i < 4; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_UP);
        for (int mode = 1; mode <= 3; mode++) {
            chooseMode(1);
            for (int width : new int[] {320, 400}) {
                openggf.timeattack.ui.RecordingSceneCanvas canvas = new openggf.timeattack.ui.RecordingSceneCanvas(width);
                scene.draw(null, canvas);
                canvas.assertFillsOnScreen();
                // Rows and the status stay above the footer band.
                for (int[] fill : canvas.fills) {
                    if (fill[1] < openggf.timeattack.ui.MenuPage.FOOTER_Y && fill[3] < 30) {
                        assertTrue(fill[1] + fill[3] <= openggf.timeattack.ui.MenuPage.FOOTER_Y + 1,
                                "mode " + mode + " at " + width + " crosses the footer: " + java.util.Arrays.toString(fill));
                    }
                }
            }
            for (int i = 0; i < 3; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_UP);
        }
    }

    @Test
    void aLongStatusWrapsWholeInsteadOfBeingCut() {
        open(List.of("s2"));
        chooseMode(1); // HOST LAN
        for (int i = 0; i < 4; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        openggf.timeattack.ui.RecordingSceneCanvas canvas = new openggf.timeattack.ui.RecordingSceneCanvas(320);
        scene.draw(null, canvas);
        assertTrue(canvas.shows("The S2 ROM is not available for racing"), "the whole status is drawn");
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
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // look
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // host port
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
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER); // display name editor, holding the prompt's name
        for (int i = 0; i < "Tester".length(); i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);
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
        com.openggf.net.identity.PlayerIdentity roomIdentity =
                com.openggf.net.identity.PlayerIdentity.loadOrCreate(temp.resolve("room-host"));
        try (com.openggf.net.host.jdk.JdkRaceHostServer room = com.openggf.net.host.jdk.JdkRaceHostServer.startTls(0,
                new com.openggf.net.hub.RoomHostConfig("LAN", "s2", 0, 0, "OPEN", null, 8, FP),
                roomIdentity, com.openggf.net.hub.TrackValidationProfileSource.none())) {
            String invite = "127.0.0.1:" + room.port() + "#" + com.openggf.net.client.DirectJoinAddress.shareCode(
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
            assertFalse(scene.settings().encode().contains(invite));
            assertEquals(1, onRoomThread(room, () -> room.room().playerCount()));
            assertFalse(host.capturesTextInput(), "lobby");
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN); // a guest's lobby opens on READY UP; chat is next
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            assertTrue(host.capturesTextInput(), "room chat field");
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
            assertFalse(host.capturesTextInput(), "room chat cancelled");
            assertEquals(openggf.timeattack.mp.RaceSession.State.LOBBY, scene.raceSession().state());

            ControlMessage.RoundConfig round = new ControlMessage.RoundConfig("s2", 0, 0, 60, "OPEN", null);
            assertTrue(onRoomThread(room, () -> room.room().requestStartRound(round)));
            idleUntil("the round run", () -> scene.raceSession().state() == openggf.timeattack.mp.RaceSession.State.RACING);
            assertEquals(List.of(new RunSpec("s2", 0, 0, "sonic", GameplayRunPolicy.isolatedAct().withFastRestarts().withoutZoneIntros())), launched);
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

    @Test
    void theLobbyLevelPlaysBehindTheMenuTogglesOnceReadyAndHandsOverToTheRound() throws Exception {
        behindSupported = true;
        com.openggf.net.identity.PlayerIdentity roomIdentity =
                com.openggf.net.identity.PlayerIdentity.loadOrCreate(temp.resolve("room-host"));
        try (com.openggf.net.host.jdk.JdkRaceHostServer room = com.openggf.net.host.jdk.JdkRaceHostServer.startTls(0,
                new com.openggf.net.hub.RoomHostConfig("LAN", "s2", 0, 0, "OPEN", null, 8, FP),
                roomIdentity, com.openggf.net.hub.TrackValidationProfileSource.none())) {
            String invite = "127.0.0.1:" + room.port() + "#" + com.openggf.net.client.DirectJoinAddress.shareCode(
                    room.tlsCertificateSha256(), roomIdentity.fingerprint());
            fingerprint = Optional.of(FP);
            open(List.of("s2"));
            chooseMode(2); // JOIN LAN
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            type(invite);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            idleUntil("the lobby level", () -> !behind.isEmpty());
            var raceSession = scene.raceSession();
            assertEquals(openggf.timeattack.mp.LobbyAreas.runSpec(
                    openggf.timeattack.mp.LobbyAreas.forGame("s2"), "sonic"), behind.getFirst());
            assertEquals(GameplayRunPolicy.DeathRestart.FAST_FROM_START, behind.getFirst().policy().deathRestart(),
                    "lobby deaths reload at the lobby spawn with no lives");
            assertInstanceOf(openggf.timeattack.mp.LobbyRun.class, hosts.getLast());
            FakeHandle lobbyLevel = behindHandles.getFirst();
            assertTrue(lobbyLevel.held, "the menu owns the controller while shown");
            assertTrue(lobbyLevel.covered, "and covers the stock HUD and nameplates");
            assertTrue(raceSession.lobbyMenuShown());

            // Not ready yet: Enter is the menu's confirm (on READY UP), not the toggle.
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            idleUntil("ready", () -> raceSession.coordinator().session().isLocalReady());
            assertTrue(raceSession.lobbyMenuShown());
            assertTrue(lobbyLevel.held);

            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER); // ready: Enter hides the menu
            assertFalse(raceSession.lobbyMenuShown());
            assertFalse(lobbyLevel.held, "the character has the controller");
            assertFalse(lobbyLevel.covered, "with the stock HUD back");
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);  // the hidden menu takes no input
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
            assertEquals(openggf.timeattack.mp.RaceSession.State.LOBBY, raceSession.state());
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER); // and Enter brings it back
            assertTrue(raceSession.lobbyMenuShown());
            assertTrue(lobbyLevel.held);

            // Once ready, Start/Enter is the toggle and A (Space) confirms; a chat editor keeps
            // Enter, which sends the message instead of hiding the menu.
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
            assertTrue(host.capturesTextInput());
            type("hi");
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            assertFalse(host.capturesTextInput());
            assertTrue(raceSession.lobbyMenuShown());

            ControlMessage.RoundConfig round = new ControlMessage.RoundConfig("s2", 0, 0, 60, "OPEN", null);
            assertTrue(onRoomThread(room, () -> room.room().requestStartRound(round)));
            idleUntil("the lobby level to leave", () -> lobbyLevel.left);
            assertTrue(launched.isEmpty(), "the round waits for the lobby level to end");
            host.resume(RunEndReason.LEFT);
            assertEquals(List.of(new RunSpec("s2", 0, 0, "sonic",
                    GameplayRunPolicy.isolatedAct().withFastRestarts().withoutZoneIntros())), launched,
                    "the scene launches the round as it resumes, so the hand-over stays black");
            assertEquals(openggf.timeattack.mp.RaceSession.State.RACING, raceSession.state());

            host.resume(RunEndReason.ACT_COMPLETED);
            assertEquals(openggf.timeattack.mp.RaceSession.State.LOBBY, raceSession.state());
            assertEquals(2, behind.size(), "back in the lobby, the lobby level relaunches at once");
            assertTrue(behindHandles.get(1).held);

            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE); // leave the room from the lobby menu
            assertEquals(openggf.timeattack.mp.RaceSession.State.IDLE, raceSession.state());
            assertTrue(behindHandles.get(1).left);
            host.resume(RunEndReason.LEFT);
            assertEquals(2, behind.size(), "no lobby level outside a room");
        }
    }

    @Test
    void aLobbyLevelThatCannotLoadLeavesTheMenuOnItsOwn() throws Exception {
        behindSupported = true;
        com.openggf.net.identity.PlayerIdentity roomIdentity =
                com.openggf.net.identity.PlayerIdentity.loadOrCreate(temp.resolve("room-host"));
        try (com.openggf.net.host.jdk.JdkRaceHostServer room = com.openggf.net.host.jdk.JdkRaceHostServer.startTls(0,
                new com.openggf.net.hub.RoomHostConfig("LAN", "s2", 0, 0, "OPEN", null, 8, FP),
                roomIdentity, com.openggf.net.hub.TrackValidationProfileSource.none())) {
            String invite = "127.0.0.1:" + room.port() + "#" + com.openggf.net.client.DirectJoinAddress.shareCode(
                    room.tlsCertificateSha256(), roomIdentity.fingerprint());
            fingerprint = Optional.of(FP);
            open(List.of("s2"));
            chooseMode(2);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            type(invite);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            idleUntil("the lobby level", () -> !behind.isEmpty());
            host.resume(RunEndReason.LOAD_FAILED);
            for (int i = 0; i < 5; i++) idle();
            assertEquals(1, behind.size(), "a failed lobby level is not retried in a loop");
            assertFalse(scene.raceSession().lobbyRunActive());
            assertEquals(openggf.timeattack.mp.RaceSession.State.LOBBY, scene.raceSession().state());
        }
    }

    private static <T> T onRoomThread(com.openggf.net.host.jdk.JdkRaceHostServer room,
                                      java.util.function.Supplier<T> read) throws Exception {
        java.util.concurrent.CompletableFuture<T> result = new java.util.concurrent.CompletableFuture<>();
        room.execute(() -> result.complete(read.get()));
        return result.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
    @Test
    void creationModalCapturesInputAndFocusLossErasesThePrivatePassword() throws Exception {
        fingerprint = Optional.of(FP);
        open(List.of("s2"));
        chooseMode(1);
        for (int i = 0; i < 4; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertTrue(host.capturesTextInput(), "creation is a modal even before its password editor opens");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        type("a long private room password");
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        scene.focusLost(null);
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        assertEquals(openggf.timeattack.mp.RaceSession.State.IDLE, scene.raceSession().state());
        assertTrue(host.capturesTextInput());
        assertTrue(launched.isEmpty());
        tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        assertFalse(host.capturesTextInput());
    }

    @Test
    void privateManualJoinPromptsLocallyAndCancelReturnsToTheMenuWithoutAPlayer() throws Exception {
        var roomIdentity = com.openggf.net.identity.PlayerIdentity.loadOrCreate(temp.resolve("private-host"));
        try (var password = com.openggf.net.protocol.RoomPassword.of("a private room password");
             var room = com.openggf.net.host.jdk.JdkRaceHostServer.startTls(0,
                     new com.openggf.net.hub.RoomHostConfig("Private", "s2", 0, 0, "OPEN", null, 8, FP,
                             List.of(), false, new com.openggf.net.protocol.RoomAccessPolicy(
                             com.openggf.net.protocol.RoomAccessPolicy.Mode.PRIVATE, true), password, null),
                     roomIdentity, com.openggf.net.hub.TrackValidationProfileSource.none())) {
            room.room().ready().get(10, java.util.concurrent.TimeUnit.SECONDS);
            String invite = "127.0.0.1:" + room.port() + "#" + com.openggf.net.client.DirectJoinAddress.shareCode(
                    room.tlsCertificateSha256(), roomIdentity.fingerprint());
            fingerprint = Optional.of(FP);
            open(List.of("s2"));
            chooseMode(2);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            type(invite);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            idleUntil("private password modal", () -> scene.raceSession().state()
                    == openggf.timeattack.mp.RaceSession.State.PASSWORD_REQUIRED);
            assertTrue(host.capturesTextInput());
            assertTrue(launched.isEmpty());
            assertEquals(0, onRoomThread(room, () -> room.room().playerCount()));
            type("a private room password");
            scene.focusLost(null);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            assertEquals(openggf.timeattack.mp.RaceSession.State.PASSWORD_REQUIRED, scene.raceSession().state());
            assertEquals(0, onRoomThread(room, () -> room.room().playerCount()));
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
            assertEquals(openggf.timeattack.mp.RaceSession.State.IDLE, scene.raceSession().state());
            assertFalse(host.capturesTextInput());
        }
    }

    @Test
    void browserCodeAndCreationModalsCaptureTextAndDrawBeforeTheBrowser() throws Exception {
        try (var master = openggf.racing.server.master.MasterServer.start(
                openggf.racing.server.master.TestMasterServer.testConfig(), temp.resolve("browser-master"))) {
            fingerprint = Optional.of(FP);
            open(List.of("s2"));
            scene.settings().setMasterUrl("ws://127.0.0.1:" + master.port() + "/master");
            chooseMode(3);
            for (int i = 0; i < 4; i++) tap(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            idleUntil("browser", () -> scene.raceSession().state() == openggf.timeattack.mp.RaceSession.State.BROWSING);
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_J);
            assertTrue(host.capturesTextInput());
            type("7K3 M9X");
            var canvas = new openggf.timeattack.ui.RecordingSceneCanvas(320);
            scene.draw(null, canvas);
            assertTrue(canvas.shows("JOIN BY CODE"));
            assertFalse(canvas.shows("7K3 M9X"));
            scene.focusLost(null);
            assertFalse(host.capturesTextInput());
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_C);
            assertTrue(host.capturesTextInput());
            scene.draw(null, canvas);
            assertTrue(canvas.shows("CREATE ROOM"));
            assertFalse(canvas.shows("Rooms listed on your master server"));
            tap(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
            assertFalse(host.capturesTextInput());
            assertEquals(openggf.timeattack.mp.RaceSession.State.BROWSING, scene.raceSession().state());
        }
    }

}
