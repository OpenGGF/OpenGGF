package openggf.timeattack;

import com.openggf.game.run.RunEndReason;
import com.openggf.game.run.RunHandle;
import com.openggf.mods.ModStorage;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.TextLayout;
import openggf.racing.protocol.ControlMessage;
import openggf.timeattack.mp.RaceLobbyView;
import openggf.timeattack.mp.RaceSession;
import openggf.timeattack.mp.ServerBrowserView;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The Time Attack menu, opened from the master title. Choosing a route and pressing GO
 * launches a stock run through {@link SceneContext#gameplay()} with a fresh
 * {@link TimeAttackRuntime} as its host; the scene is resumed when the run ends.
 *
 * <p>The multiplayer modes hand the choice to the scene's {@link RaceSession}: host or join a
 * LAN room, or browse the master server. While the session is connecting, browsing or in a
 * room, the scene shows its views (connecting page, {@link ServerBrowserView},
 * {@link RaceLobbyView}); each round plays as a run launched by the session, and the lobby
 * returns when it ends. The session's sockets and threads close when the player leaves and
 * when this scene exits for any reason.
 */
public final class TimeAttackScene implements ModScene {
    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int LABEL = 0xFFB0C8E8;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int FOCUS = 0x803060C0;
    private static final int HINT = 0xFF8090B0;
    private static final int ROW_HEIGHT = 16;
    private static final int FIRST_ROW_Y = 44;
    /** Characters accepted in a LAN invite besides letters and digits (IPv6 brackets included). */
    private static final String INVITE_CHARACTERS = ".:/#-_[]";

    private final GhostStore ghostStore;
    private final Path identityDir;
    private TimeAttackMenuState state;
    private TimeAttackSettings settings;
    private ModStorage storage;
    private RaceSession session;
    private TimeAttackRuntime runtime;
    private int focus;
    private String status = "";
    private String joinInvite = "";
    private TextEntry inviteEditor;
    private SettingsView settingsView;
    private ServerBrowserView browser;
    private RaceLobbyView lobby;
    private Object lobbyCoordinator;

    /** The scene as the title entry opens it: ghosts and identity in the working directory. */
    public TimeAttackScene() {
        this(Path.of("ghosts"), Path.of("identity"));
    }

    TimeAttackScene(Path ghostsRoot, Path identityDir) {
        this.ghostStore = new GhostStore(ghostsRoot);
        this.identityDir = identityDir;
    }

    @Override
    public void enter(SceneContext ctx) {
        storage = ctx.storage();
        settings = TimeAttackSettings.load(storage);
        joinInvite = settings.lastJoinAddress();
        session = new RaceSession(identityDir, ghostStore, settings, storage,
                (spec, host) -> ctx.gameplay().launch(spec, host), this::newRuntime, System::currentTimeMillis);
        List<String> games = ctx.gameplay().availableGames();
        if (!games.isEmpty()) {
            state = new TimeAttackMenuState(games, games.get(0), ghostStore);
        }
    }

    private TimeAttackRuntime newRuntime() {
        TimeAttackRuntime run = new TimeAttackRuntime(ghostStore, identityDir, () -> false);
        run.setRetryKey(() -> SceneKeys.R);
        return run;
    }

    @Override
    public void update(SceneContext ctx) {
        session.poll();
        syncViews();
        ViewInput input = ViewInput.of(ctx);
        switch (session.state()) {
            case CONNECTING -> {
                if (input.back()) {
                    session.cancel();
                }
                return;
            }
            case BROWSING -> {
                if (browser != null) browser.update(input);
                return;
            }
            case LOBBY -> {
                if (lobby != null) lobby.update(input);
                return;
            }
            case RACING -> {
                return;
            }
            case IDLE -> { }
        }
        if (state == null) {
            if (input.back() || input.accept()) {
                ctx.exitToMasterTitle();
            }
            return;
        }
        if (settingsView != null) {
            settingsView.update(input);
            if (settingsView.consumeCloseRequested()) {
                settingsView = null;
            }
            return;
        }
        if (inviteEditor != null) {
            switch (inviteEditor.update(input)) {
                case ACCEPTED -> {
                    joinInvite = inviteEditor.text().strip();
                    inviteEditor = null;
                }
                case CANCELLED -> inviteEditor = null;
                case NONE -> { }
            }
            return;
        }
        updateMenu(ctx, input);
    }

    private void updateMenu(SceneContext ctx, ViewInput input) {
        if (input.back()) {
            ctx.exitToMasterTitle();
            return;
        }
        int count = menuRowCount();
        if (input.up()) focus = Math.floorMod(focus - 1, count);
        if (input.down()) focus = (focus + 1) % count;
        if (focus < state.visibleRows().size()) {
            while (state.focusedRow() != state.visibleRows().get(focus)) state.moveFocus(1);
            if (input.left()) state.adjust(-1);
            if (input.right()) state.adjust(1);
        }
        // The mode row can hide or show rows below it; keep the focus on a real row.
        focus = Math.min(focus, menuRowCount() - 1);
        int rows = state.visibleRows().size();
        int inviteRow = joining() ? rows : -1;
        int go = rows + (joining() ? 1 : 0);
        int settingsRow = go + 1;
        if (input.accept()) {
            if (focus == go) {
                state.pressGo();
            } else if (focus == inviteRow) {
                inviteEditor = new TextEntry("LAN INVITE", joinInvite, TimeAttackSettings.MAX_TEXT_LENGTH,
                        INVITE_CHARACTERS);
            } else if (focus == settingsRow) {
                settingsView = new SettingsView(settings, this::settingsChanged, this::cue);
            } else {
                focus = Math.min(focus + 1, go);
            }
        }
        TimeAttackLaunchRequest request = state.consumeLaunchRequest();
        if (request != null) {
            start(ctx, request);
        }
    }

    private void start(SceneContext ctx, TimeAttackLaunchRequest request) {
        TimeAttackMenuState.Mode mode = state.mode();
        if (mode == TimeAttackMenuState.Mode.SOLO) {
            launch(ctx, request);
            return;
        }
        if (mode == TimeAttackMenuState.Mode.JOIN_LAN && joinInvite.isBlank()) {
            status = "Enter the host's LAN invite first";
            return;
        }
        Optional<String> fingerprint = ctx.gameplay().determinismFingerprint(request.gameId());
        if (fingerprint.isEmpty()) {
            status = "The " + request.gameId().toUpperCase() + " ROM is not available for racing";
            return;
        }
        status = "";
        RaceSession.RoundSetup setup = new RaceSession.RoundSetup(request.gameId(), request.zone(),
                request.act(), request.character(), state.characterPolicy(), state.lockedCharacter(),
                state.windowSeconds());
        switch (mode) {
            case HOST_LAN -> session.hostLan(setup, fingerprint.get());
            case JOIN_LAN -> session.joinLan(joinInvite, request.character(), fingerprint.get());
            case BROWSE -> session.browse(setup, fingerprint.get());
            case SOLO -> { }
        }
    }

    private void launch(SceneContext ctx, TimeAttackLaunchRequest request) {
        TimeAttackRuntime run = newRuntime();
        run.armForLaunch(request);
        RunHandle handle = ctx.gameplay().launch(run.runSpec(), run);
        run.attachHandle(handle);
        runtime = run;
        status = "";
    }

    /** Creates, keeps or drops the browser and lobby views to match the session. */
    private void syncViews() {
        String notice = session.consumeNotice();
        RaceSession.State current = session.state();
        boolean browsing = current == RaceSession.State.BROWSING || session.connectingFromBrowser();
        if (browsing && browser == null && session.roomDirectory() != null) {
            browser = new ServerBrowserView(session.roomDirectory(), session.browseGameId(),
                    new ServerBrowserView.Actions() {
                        @Override public void join(ControlMessage.RoomSummary room) { session.joinMasterRoom(room); }
                        @Override public void create(String routing) { session.createMasterRoom(routing); }
                        @Override public void back() { session.leave(); }
                    }, this::cue, System::currentTimeMillis);
        } else if (!browsing && browser != null) {
            browser.deactivate();
            browser = null;
        }
        boolean inRoom = current == RaceSession.State.LOBBY || current == RaceSession.State.RACING;
        if (inRoom && (lobby == null || lobbyCoordinator != session.coordinator())) {
            lobby = new RaceLobbyView(session.coordinator(), session.isHosting(), session.roundConfig(),
                    session.lanInvite(), session::leave, this::cue);
            lobbyCoordinator = session.coordinator();
        } else if (!inRoom) {
            lobby = null;
            lobbyCoordinator = null;
        }
        if (notice != null) {
            if (browser != null && current == RaceSession.State.BROWSING) {
                browser.showStatus(notice);
            } else if (lobby != null && inRoom) {
                lobby.showStatus(notice);
            } else {
                status = notice;
            }
        }
    }

    private void settingsChanged() {
        if (!settings.save(storage)) {
            status = "Settings could not be saved";
        }
        session.settingsChanged();
    }

    /**
     * True while a text field is focused (LAN invite, chat, display name, port, master URL), so
     * the engine holds back its global keyboard shortcuts while the player types.
     */
    @Override
    public boolean capturesTextInput() {
        if (session == null) {
            return false;
        }
        return switch (session.state()) {
            case LOBBY -> lobby != null && lobby.isEditingChat();
            case IDLE -> inviteEditor != null || (settingsView != null && settingsView.isEditingText());
            case CONNECTING, BROWSING, RACING -> false;
        };
    }

    /**
     * Menu feedback. Title-entry scenes open without a loaded game, so there is no sound driver
     * whose effect IDs the cues could play; views still report them so their actions are testable.
     */
    private void cue(MenuCue cue) {
    }

    @Override
    public void resumed(SceneContext ctx, RunEndReason reason) {
        if (session.state() == RaceSession.State.RACING) {
            session.runEnded();
            if (reason == RunEndReason.LOAD_FAILED && lobby != null) {
                lobby.showStatus("The round could not be loaded");
            }
            return;
        }
        runtime = null;
        status = switch (reason) {
            case ACT_COMPLETED -> "Run complete";
            case LEFT, ABORTED -> "";
            case LOAD_FAILED -> "The route could not be loaded";
        };
        if (state != null) {
            state.refreshGhostSummary();
        }
    }

    @Override
    public void exit(SceneContext ctx) {
        if (browser != null) {
            browser.deactivate();
        }
        if (session != null) {
            session.close();
        }
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        switch (session.state()) {
            case CONNECTING -> {
                drawConnecting(canvas);
                return;
            }
            case BROWSING -> {
                if (browser != null) browser.draw(canvas);
                else drawConnecting(canvas);
                return;
            }
            case LOBBY, RACING -> {
                if (lobby != null) lobby.draw(canvas);
                else drawConnecting(canvas);
                return;
            }
            case IDLE -> { }
        }
        if (settingsView != null) {
            settingsView.draw(canvas);
            return;
        }
        if (inviteEditor != null) {
            inviteEditor.draw(canvas);
            return;
        }
        drawMenu(canvas);
    }

    private void drawConnecting(SceneCanvas canvas) {
        canvas.clear(BACKGROUND);
        canvas.text("TIME ATTACK", 10, 10, TITLE);
        canvas.text(fit(canvas, session.status(), canvas.width() - 20), 10, 60, VALUE);
        canvas.text("B cancel", 10, 208, HINT);
    }

    private void drawMenu(SceneCanvas canvas) {
        canvas.clear(BACKGROUND);
        canvas.text("TIME ATTACK", 10, 10, TITLE);
        if (state == null) {
            canvas.text("No game ROM is available.", 10, 40, VALUE);
            canvas.text("Configure ROMs in Settings, then come back.", 10, 56, HINT);
            return;
        }
        canvas.text("Choose a route and race mode", 10, 24, HINT);
        int index = 0;
        for (TimeAttackMenuState.Row row : state.visibleRows()) {
            String label;
            String value;
            switch (row) {
                case GAME -> { label = "Game"; value = state.currentGameId().toUpperCase(); }
                case TRACK -> { label = "Track"; value = state.currentTrack() == null ? "No tracks" : state.currentTrack().label(); }
                case CHARACTER -> { label = "Character"; value = state.currentCharacter() == null ? "None" : state.currentCharacter().toUpperCase(); }
                case MODE -> { label = "Mode"; value = state.mode().name().replace('_', ' '); }
                case POLICY -> { label = "Policy"; value = state.characterPolicy().equals("OPEN") ? "OPEN" : "LOCKED " + state.currentCharacter().toUpperCase(); }
                case WINDOW -> { label = "Window"; value = (state.windowSeconds() / 60) + " minutes"; }
                default -> throw new IllegalStateException("Unexpected row " + row);
            }
            drawRow(canvas, label, "< " + value + " >", index++);
        }
        if (joining()) {
            drawRow(canvas, "Invite", joinInvite.isBlank() ? "Select to enter" : joinInvite, index++);
        }
        String action = switch (state.mode()) {
            case SOLO -> "START RUN";
            case HOST_LAN -> "CREATE LAN ROOM";
            case JOIN_LAN -> "JOIN LAN ROOM";
            case BROWSE -> "BROWSE ROOMS";
        };
        drawAction(canvas, action, index++);
        drawAction(canvas, "SETTINGS", index);
        canvas.text(fit(canvas, (state.bestExists() ? "Best: saved" : "Best: none")
                + " / Imported ghosts: " + state.importCount(), canvas.width() - 20), 10, 181, LABEL);
        if (!status.isEmpty()) {
            canvas.text(fit(canvas, status, canvas.width() - 20), 10, 194, TITLE);
        }
        canvas.text(fit(canvas, "Up/Down select  Left/Right change  Start go  B back", canvas.width() - 20),
                10, 210, HINT);
    }

    private void drawRow(SceneCanvas canvas, String label, String value, int index) {
        int y = FIRST_ROW_Y + index * ROW_HEIGHT;
        if (focus == index) canvas.fill(8, y - 3, canvas.width() - 16, 15, FOCUS);
        canvas.text(label, 14, y, LABEL);
        canvas.text(fit(canvas, value, canvas.width() - 124), 112, y, VALUE);
    }

    private void drawAction(SceneCanvas canvas, String action, int index) {
        int y = FIRST_ROW_Y + index * ROW_HEIGHT;
        if (focus == index) canvas.fill(8, y - 3, canvas.width() - 16, 15, FOCUS);
        canvas.text(action, 14, y, VALUE);
    }

    private static String fit(SceneCanvas canvas, String text, int maxWidth) {
        return TextLayout.ellipsis(text == null ? "" : text, Math.max(0, maxWidth), canvas::textWidth);
    }

    /** Field rows, the invite row while joining, the action row and the settings row. */
    private int menuRowCount() {
        return state.visibleRows().size() + (joining() ? 1 : 0) + 2;
    }

    private boolean joining() {
        return state != null && state.mode() == TimeAttackMenuState.Mode.JOIN_LAN;
    }

    /** The run being played, or null while the menu is shown (tests). */
    TimeAttackRuntime activeRuntime() {
        return runtime;
    }

    /** The scene's multiplayer session (tests). */
    RaceSession raceSession() {
        return session;
    }

    /** The menu's status line (tests). */
    String status() {
        return status;
    }

    /** The scene's settings (tests). */
    TimeAttackSettings settings() {
        return settings;
    }
}
