package openggf.timeattack;

import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.ModStorage;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.net.identity.PlayerAppearance;
import com.openggf.net.protocol.ControlMessage;
import openggf.timeattack.mp.RaceLobbyView;
import openggf.timeattack.mp.RaceSession;
import openggf.timeattack.mp.ServerBrowserView;
import openggf.timeattack.mp.VoteTrackPools;
import openggf.timeattack.mp.RoomAccessView;
import openggf.timeattack.mp.LobbyInviteController;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomPassword;
import com.openggf.mods.ui.NameFont;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.MenuPage;
import openggf.timeattack.ui.OverLevelCanvas;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.nio.file.Files;
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
    /** Characters accepted in a LAN invite besides letters and digits (IPv6 brackets included). */
    private static final String INVITE_CHARACTERS = ".:/#-_[]";
    /** {@code PlayerIdentity}'s key file: its absence means this player has never raced online. */
    private static final String IDENTITY_KEY_FILE = "player-identity.key";

    private final GhostStore ghostStore;
    private final Path identityDir;
    private final Path legacyConfigDirectory;
    private TimeAttackMenuState state;
    private TimeAttackSettings settings;
    private ModStorage storage;
    private RaceSession session;
    private TimeAttackRuntime runtime;
    private int focus;
    private String status = "";
    private String joinInvite = "";
    private TextEntry inviteEditor;
    private TextEntry namePrompt;
    private RoomAccessView accessView;
    private TextEntry codeEditor;
    private TextEntry passwordEditor;
    private LobbyInviteController invites;
    private SceneContext context;
    private SettingsView settingsView;
    private VotePoolEditorView poolEditor;
    private ServerBrowserView browser;
    private RaceLobbyView lobby;
    private Object lobbyCoordinator;
    /** Hints name controller buttons after a pad press, keyboard keys after a key press. */
    private boolean pad;

    /** The scene as the title entry opens it: ghosts and identity in the working directory. */
    public TimeAttackScene() {
        this(Path.of("ghosts"), Path.of("identity"));
    }

    TimeAttackScene(Path ghostsRoot, Path identityDir) {
        this.ghostStore = new GhostStore(ghostsRoot);
        this.identityDir = identityDir;
        this.legacyConfigDirectory = ghostsRoot.toAbsolutePath().getParent();
    }

    @Override
    public void enter(SceneContext ctx) {
        context = ctx;
        storage = ctx.storage();
        try {
            settings = LegacyTimeAttackSettings.loadOrImport(storage, legacyConfigDirectory);
            if (settings.pendingPublication()) status = "Previous settings active - save pending";
        } catch (java.io.IOException failure) {
            status = "Previous settings could not be imported";
            settings = TimeAttackSettings.load(storage);
        }
        joinInvite = "";
        session = new RaceSession(identityDir, ghostStore, settings, storage,
                (spec, host) -> ctx.gameplay().launch(spec, host), this::newRuntime, System::currentTimeMillis);
        // Every join (Hello) and lobby entry announces the look the settings hold for that game/character.
        session.setLookSource(settings::appearanceFor);
        session.setChatPreferenceSource(ctx::censorMultiplayerProfanity, ctx::multiplayerChatDisabled);
        // Each room's lobby is a short level of its game, playing behind the lobby menu.
        session.setLobbyLauncher((spec, host) -> ctx.gameplay().launchBehind(spec, host));
        List<String> games = ctx.gameplay().availableGames();
        if (!games.isEmpty()) {
            state = new TimeAttackMenuState(games, games.get(0), ghostStore, poolChoices());
        }
        // A new player names themselves before anything else. Profiles from before names were
        // asked for (an identity but no name) keep racing under their identity prefix.
        if (state != null && settings.displayName().isEmpty()
                && !Files.exists(identityDir.resolve(IDENTITY_KEY_FILE))) {
            namePrompt = TextEntry.name("CHOOSE YOUR NAME", "")
                    .withPrompt("Other racers see it. Any script works.");
        }
    }

    /** The menu's vote-pool row, backed by the saved custom pools and the per-game choice. */
    private TimeAttackMenuState.PoolChoices poolChoices() {
        return new TimeAttackMenuState.PoolChoices() {
            @Override public List<String> names(String gameId) {
                return settings.votePools(gameId).stream().map(VotePool::name).toList();
            }
            @Override public String chosen(String gameId) { return settings.votePoolChoice(gameId); }
            @Override public void choose(String gameId, String name) {
                settings.setVotePoolChoice(gameId, name);
                settingsChanged();
            }
        };
    }

    /** The vote pool a room hosted for {@code gameId} carries: the chosen custom pool or the default. */
    private List<String> hostedVotePool(String gameId) {
        String chosen = state.currentPoolName();
        return chosen.isEmpty() ? VoteTrackPools.forGame(gameId)
                : settings.votePool(gameId, chosen).map(pool -> VoteTrackPools.keysOf(gameId, pool))
                        .orElseGet(() -> VoteTrackPools.forGame(gameId));
    }

    private TimeAttackRuntime newRuntime() {
        TimeAttackRuntime run = new TimeAttackRuntime(ghostStore, identityDir, () -> false);
        run.setRetryKey(settings::retryKey);
        run.setLookSource(settings::appearanceFor);
        // The run plays through the launched game's own sound driver.
        run.setSfxPlayer(id -> context.audio().playSfx(id));
        return run;
    }

    @Override
    public void update(SceneContext ctx) {
        session.poll();
        syncViews();
        ViewInput input = ViewInput.of(ctx);
        pad = MenuPage.controller(input, pad);
        if (accessView != null) {
            accessView.update(input);
            return;
        }
        if (codeEditor != null) {
            switch (codeEditor.update(input)) {
                case ACCEPTED -> {
                    String code = codeEditor.text();
                    codeEditor.clear();
                    codeEditor = null;
                    session.joinCode(code);
                }
                case CANCELLED -> { codeEditor.clear(); codeEditor = null; }
                case NONE -> { }
            }
            return;
        }
        if (session.state() == RaceSession.State.PASSWORD_REQUIRED) {
            updatePassword(input);
            return;
        }
        if (passwordEditor != null) { passwordEditor.clear(); passwordEditor = null; }
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
                if (lobby != null) updateLobby(input);
                return;
            }
            case RACING -> {
                return;
            }
            case IDLE, PASSWORD_REQUIRED -> { }
        }
        if (state == null) {
            if (input.back() || input.accept()) {
                ctx.exitToMasterTitle();
            }
            return;
        }
        if (namePrompt != null) {
            switch (namePrompt.update(input)) {
                case ACCEPTED -> {
                    settings.setDisplayName(namePrompt.acceptedName());
                    namePrompt = null;
                    settingsChanged();
                }
                case CANCELLED -> ctx.exitToMasterTitle(); // no skipping: the prompt returns next time
                case NONE -> { }
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
        if (poolEditor != null) {
            poolEditor.update(input);
            if (poolEditor.consumeCloseRequested()) {
                poolEditor = null;
            }
            return;
        }
        if (inviteEditor != null) {
            switch (inviteEditor.update(input)) {
                case ACCEPTED -> {
                    joinInvite = inviteEditor.text().strip();
                    inviteEditor.clear();
                    inviteEditor = null;
                }
                case CANCELLED -> { inviteEditor.clear(); inviteEditor = null; }
                case NONE -> { }
            }
            return;
        }
        updateMenu(ctx, input);
    }

    @FunctionalInterface
    private interface CreateRoom {
        void create(RoomAccessPolicy policy, RoomPassword password);
    }

    private void openAccess(CreateRoom create) {
        accessView = new RoomAccessView(new RoomAccessView.Actions() {
            @Override public void create(boolean privateRoom, boolean membersCanInvite, String password) {
                accessView.close();
                accessView = null;
                RoomAccessPolicy policy = new RoomAccessPolicy(privateRoom ? RoomAccessPolicy.Mode.PRIVATE
                        : RoomAccessPolicy.Mode.PUBLIC, membersCanInvite);
                try (RoomPassword secret = privateRoom ? RoomPassword.of(password) : null) {
                    create.create(policy, secret);
                }
            }
            @Override public void cancelled() { accessView.close(); accessView = null; }
        }, this::cue);
    }

    private void updatePassword(ViewInput input) {
        if (passwordEditor == null) {
            RaceSession.PasswordPrompt prompt = session.passwordPrompt();
            String identity = prompt == null ? "" : prompt.hostIdentity();
            if (identity != null && identity.length() > 12) identity = identity.substring(0, 12);
            passwordEditor = TextEntry.password("ROOM PASSWORD")
                    .withPrompt("This room is private. Host ID " + (identity == null ? "unavailable" : identity));
        }
        switch (passwordEditor.update(input)) {
            case ACCEPTED -> {
                try (RoomPassword password = RoomPassword.of(passwordEditor.text())) {
                    passwordEditor.clear();
                    passwordEditor = null;
                    joinInvite = "";
                    session.submitPassword(password);
                } catch (IllegalArgumentException invalid) {
                    passwordEditor.withPrompt("Use 15 to 128 characters. Nothing is trimmed.");
                }
            }
            case CANCELLED -> {
                passwordEditor.clear();
                passwordEditor = null;
                joinInvite = "";
                session.cancel();
            }
            case NONE -> { }
        }
    }

    private void clearEditors() {
        if (accessView != null) accessView.close();
        accessView = null;
        if (codeEditor != null) codeEditor.clear();
        codeEditor = null;
        if (passwordEditor != null) passwordEditor.clear();
        passwordEditor = null;
        if (inviteEditor != null) inviteEditor.clear();
        inviteEditor = null;
    }

    @Override
    public void focusLost(SceneContext ctx) {
        if (accessView != null) accessView.focusLost();
        if (codeEditor != null) { codeEditor.clear(); codeEditor = null; }
        if (passwordEditor != null) passwordEditor.clear();
        if (inviteEditor != null) { inviteEditor.clear(); inviteEditor = null; }
        joinInvite = "";
        if (invites != null) invites.focusLost();
    }

    /**
     * The lobby menu, over the lobby level when it plays. Once ready, Start or Enter on the
     * lobby's own page hides the menu and hands the controller to the character; pressing it
     * again brings the menu back, like a pause menu. While hidden the menu takes no input.
     */
    private void updateLobby(ViewInput input) {
        if (invites != null) {
            invites.poll(context.clipboard());
            if (invites.isOpen()) {
                invites.update(input, context.mouse(), context.clipboard());
                return;
            }
        }
        boolean overLevel = session.lobbyRunActive();
        lobby.setLevelBehind(overLevel);
        if (overLevel && !session.lobbyMenuShown()) {
            if (input.start()) {
                session.setLobbyMenuShown(true);
                cue(MenuCue.CONFIRM);
            }
            return;
        }
        if (overLevel && input.start() && lobby.isOnMainPage()
                && session.coordinator().session().isLocalReady()) {
            session.setLobbyMenuShown(false);
            cue(MenuCue.CONFIRM);
            return;
        }
        lobby.update(input);
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
                        INVITE_CHARACTERS).masked()
                        .withPrompt("Type the invite the host shared, with HOST_IP replaced. It stays hidden.");
            } else if (focus == settingsRow) {
                settingsView = new SettingsView(settings, this::settingsChanged, this::cue,
                        state.currentGameId(), state.currentCharacter(),
                        stockLine(state.currentGameId(), state.currentCharacter()));
            } else if (focus < rows && state.visibleRows().get(focus) == TimeAttackMenuState.Row.POOL) {
                poolEditor = new VotePoolEditorView(settings, state.currentGameId(), this::settingsChanged, this::cue);
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
        List<String> votePool = hostedVotePool(request.gameId());
        if (mode != TimeAttackMenuState.Mode.JOIN_LAN) {
            Optional<String> rejected = VoteTrackPools.rejection(request.gameId(), votePool, state.lockedCharacter());
            if (rejected.isPresent()) {
                status = rejected.get();
                return;
            }
        }
        status = "";
        RaceSession.RoundSetup setup = new RaceSession.RoundSetup(request.gameId(), request.zone(),
                request.act(), request.character(), state.characterPolicy(), state.lockedCharacter(),
                state.windowSeconds(), votePool);
        switch (mode) {
            case HOST_LAN -> openAccess((policy, password) -> session.hostLan(setup, fingerprint.get(), policy, password));
            case JOIN_LAN -> {
                session.joinLan(joinInvite, request.character(), fingerprint.get());
                joinInvite = "";
            }
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
                        @Override public void create(String routing) {
                            openAccess((policy, password) -> session.createMasterRoom(routing, policy, password));
                        }
                        @Override public void joinCode() {
                            codeEditor = new TextEntry("JOIN BY CODE", "", 32, " -").masked()
                                    .withPrompt("Six characters. Uses your selected master.");
                        }
                        @Override public void back() { session.leave(); }
                    }, this::cue, System::currentTimeMillis, settings.chatMode());
        } else if (!browsing && browser != null) {
            browser.deactivate();
            browser = null;
        }
        boolean inRoom = current == RaceSession.State.LOBBY || current == RaceSession.State.RACING;
        if (current == RaceSession.State.RACING && invites != null) invites.dismiss();
        if (inRoom && (lobby == null || lobbyCoordinator != session.coordinator())) {
            RaceSession raceSession = session;
            lobby = new RaceLobbyView(session.coordinator(), session.isHosting(), session.roundConfig(),
                    null, session::leave, this::cue,
                    () -> {
                        String game = raceSession.roundConfig() == null ? null : raceSession.roundConfig().gameId();
                        return new ProfileView(settings, game, raceSession.character(),
                                stockLine(game, raceSession.character()), this::settingsChanged, this::cue);
                    });
            if (invites != null) invites.close();
            invites = new LobbyInviteController(session.coordinator(), session.isHosting(),
                    session::manualInviteFallback, this::cue, System::currentTimeMillis);
            lobby.setInviteAction(invites::open);
            lobby.setReadyRuleListener(rule -> {
                settings.setReadyRule(rule);
                settingsChanged();
            });
            lobby.setChatModeListener(mode -> {
                settings.setChatMode(mode);
                settingsChanged();
            });
            lobbyCoordinator = session.coordinator();
        } else if (!inRoom) {
            if (invites != null) invites.close();
            invites = null;
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

    /**
     * The character's stock palette line as Mega Drive colour words, read through the ROM pipeline,
     * or null when that game's ROM art is unavailable.
     */
    private int[] stockLine(String gameId, String character) {
        if (context == null || gameId == null || character == null) return null;
        // The scene ROM path has no KiS2 Knuckles line; it would return the Sonic/Tails one.
        if (gameId.equals("s2") && character.equalsIgnoreCase("knuckles")) return null;
        SceneRomArt rom = context.art().rom(gameId);
        int[] argb = rom == null ? null : rom.characterPalette(character);
        if (argb == null || argb.length < 16) return null;
        int[] line = new int[16];
        for (int i = 0; i < 16; i++) line[i] = PlayerAppearance.fromRgb(argb[i] & 0xFFFFFF);
        return line;
    }

    private void settingsChanged() {
        settings.save(storage);
        status = settings.publicationNotice();
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
        if (accessView != null || codeEditor != null) return true;
        return switch (session.state()) {
            case LOBBY -> invites != null && invites.isEditingText() || lobby != null && lobby.isEditingText();
            case IDLE -> namePrompt != null || inviteEditor != null
                    || (settingsView != null && settingsView.isEditingText())
                    || (poolEditor != null && poolEditor.isEditingText());
            case PASSWORD_REQUIRED -> true;
            case BROWSING -> codeEditor != null || accessView != null;
            case CONNECTING, RACING -> false;
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
        context = ctx;
        session.setChatPreferenceSource(ctx::censorMultiplayerProfanity, ctx::multiplayerChatDisabled);
        session.flushPendingPublications();
        // Polling here launches the next run (the round after the lobby level, the lobby level
        // after a round) before the screen fades back in, so the hand-over stays black.
        if (session.lobbyRunEnded(reason)) {
            session.poll();
            syncViews();
            return;
        }
        if (session.state() == RaceSession.State.RACING) {
            session.runEnded();
            if (reason == RunEndReason.LOAD_FAILED && lobby != null) {
                lobby.showStatus("The round could not be loaded");
            }
            session.poll();
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
        clearEditors();
        joinInvite = "";
        if (invites != null) invites.close();
        if (browser != null) {
            browser.deactivate();
        }
        if (session != null) {
            session.close();
        }
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        if (accessView != null) { accessView.draw(canvas); return; }
        if (codeEditor != null) { codeEditor.draw(canvas); return; }
        if (session.state() == RaceSession.State.PASSWORD_REQUIRED) {
            if (passwordEditor != null) passwordEditor.draw(canvas);
            else drawConnecting(canvas);
            return;
        }
        if (invites != null && invites.isOpen()) { invites.draw(canvas); return; }
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
                if (lobby == null) drawConnecting(canvas);
                else if (!session.lobbyRunActive()) lobby.draw(canvas);
                else if (session.lobbyMenuShown()) lobby.draw(new OverLevelCanvas(canvas));
                else drawPlayHint(canvas);
                return;
            }
            case IDLE, PASSWORD_REQUIRED -> { }
        }
        if (namePrompt != null && state != null) {
            namePrompt.draw(canvas);
            return;
        }
        if (settingsView != null) {
            settingsView.draw(canvas);
            return;
        }
        if (poolEditor != null) {
            poolEditor.draw(canvas);
            return;
        }
        if (inviteEditor != null) {
            inviteEditor.draw(canvas);
            return;
        }
        drawMenu(canvas);
    }

    /** The lobby level with the menu hidden: only a reminder of how to bring it back. */
    private void drawPlayHint(SceneCanvas canvas) {
        String key = pad ? "START" : "ENTER";
        java.util.List<MenuPage.Hint> hint = java.util.List.of(MenuPage.hint(key, "Lobby menu"));
        int width = MenuPage.hintsWidth(hint);
        int x = canvas.width() - 8 - width;
        canvas.fill(x - 4, 205, width + 8, 15, 0xC002081C);
        MenuPage.hints(canvas, x, 207, hint, MenuPage.TEXT);
    }

    private void drawConnecting(SceneCanvas canvas) {
        MenuPage.page(canvas, "TIME ATTACK", null, "Connecting");
        int width = canvas.width();
        MenuPage.notice(canvas, MenuPage.Tone.INFO, session.status(), 14, MenuPage.BODY_TOP + 8, width - 28);
        MenuPage.footer(canvas, "", MenuPage.TEXT, java.util.List.of(MenuPage.hint(MenuPage.back(pad), "Cancel")));
    }

    private static final int MENU_TOP = MenuPage.BODY_TOP - 2;

    private void drawMenu(SceneCanvas canvas) {
        int width = canvas.width();
        if (state == null) {
            MenuPage.page(canvas, "TIME ATTACK", null, "Race a route against your best");
            int y = MenuPage.notice(canvas, MenuPage.Tone.WARNING, "No game ROM is available.", 14, MENU_TOP + 8,
                    width - 28);
            MenuPage.paragraph(canvas, "Add your games from the title screen, then come back.", 24, y + 2,
                    width - 38, 2, MenuPage.MUTED);
            MenuPage.footer(canvas, "", MenuPage.TEXT, java.util.List.of(MenuPage.hint(MenuPage.back(pad), "Title")));
            return;
        }
        MenuPage.page(canvas, "TIME ATTACK", MenuPage.gameName(state.currentGameId()), switch (state.mode()) {
            case SOLO -> "Race the route against your best ghost";
            case HOST_LAN -> "Open a room for players on your network";
            case JOIN_LAN -> "Join a room with the host's LAN invite";
            case BROWSE -> "Find rooms on the master server";
        });
        java.util.List<TimeAttackMenuState.Row> rows = state.visibleRows();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (TimeAttackMenuState.Row row : rows) labels.add(label(row));
        if (joining()) labels.add("Invite");
        int valueX = MenuPage.MARGIN + 6 + MenuPage.labelColumn(labels);
        int y = MENU_TOP;
        int index = 0;
        for (TimeAttackMenuState.Row row : rows) {
            String value = switch (row) {
                case GAME -> MenuPage.gameName(state.currentGameId());
                case TRACK -> state.currentTrack() == null ? "No tracks" : MenuPage.words(state.currentTrack().label());
                case CHARACTER -> state.currentCharacter() == null ? "None" : MenuPage.words(state.currentCharacter().toUpperCase());
                case MODE -> switch (state.mode()) {
                    case SOLO -> "Solo";
                    case HOST_LAN -> "Host a LAN room";
                    case JOIN_LAN -> "Join a LAN room";
                    case BROWSE -> "Browse online rooms";
                };
                case POLICY -> state.characterPolicy().equals("OPEN") ? "Any character"
                        : "Only " + MenuPage.words(state.currentCharacter().toUpperCase());
                case WINDOW -> (state.windowSeconds() / 60) + (state.windowSeconds() == 60 ? " minute" : " minutes");
                // Pool names are display text in any script.
                case POOL -> state.currentPoolName().isEmpty() ? "Default" : state.currentPoolName();
            };
            MenuPage.field(canvas, y, valueX, label(row), value, MenuPage.TEXT, focus == index, true);
            y += MenuPage.ROW_H;
            index++;
        }
        if (joining()) {
            boolean set = !joinInvite.isBlank();
            int x = MenuPage.field(canvas, y, valueX, "Invite", set ? "   Entered" : "Not entered",
                    set ? MenuPage.TEXT : MenuPage.DIM, focus == index, false);
            if (set) MenuPage.lock(canvas, x, y + 3, MenuPage.CYAN);
            y += MenuPage.ROW_H;
            index++;
        }
        String action = switch (state.mode()) {
            case SOLO -> "START RUN";
            case HOST_LAN -> "CREATE LAN ROOM";
            case JOIN_LAN -> "JOIN LAN ROOM";
            case BROWSE -> "BROWSE ROOMS";
        };
        y += 3;
        MenuPage.button(canvas, MenuPage.MARGIN, y, width - 2 * MenuPage.MARGIN, action, focus == index, true);
        y += MenuPage.BUTTON_H + 3;
        index++;
        MenuPage.action(canvas, y, "Settings", focus == index, true);
        y += MenuPage.ROW_H + 3;
        int lines = Math.max(1, (MenuPage.FOOTER_Y - 1 - y) / NameFont.LINE_HEIGHT);
        if (!status.isEmpty()) {
            MenuPage.Tone tone = statusTone(status);
            MenuPage.icon(canvas, tone, 14, y + 3);
            MenuPage.paragraph(canvas, status, 24, y, width - 38, lines, MenuPage.colour(tone));
        } else {
            String best = state.bestExists() ? "Best ghost saved" : "No best ghost yet";
            NameFont.draw(canvas, NameFont.fit(best + "   Imported ghosts: " + state.importCount(), width - 28), 14, y,
                    MenuPage.DIM);
        }
        MenuPage.footer(canvas, "", MenuPage.TEXT, menuHints(rows.size()));
    }

    /** Saved/complete reports read as success; everything else the menu reports needs attention. */
    private static MenuPage.Tone statusTone(String status) {
        String lower = status.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("run complete") || lower.contains("saved") && !lower.contains("not")
                && !lower.contains("pending") ? MenuPage.Tone.GOOD : MenuPage.Tone.WARNING;
    }

    private static String label(TimeAttackMenuState.Row row) {
        return switch (row) {
            case GAME -> "Game";
            case TRACK -> "Track";
            case CHARACTER -> "Character";
            case MODE -> "Mode";
            case POLICY -> "Characters";
            case WINDOW -> "Round length";
            case POOL -> "Vote pool";
        };
    }

    /** What the keys do on the focused row (accept on a field row moves to the next row). */
    private java.util.List<MenuPage.Hint> menuHints(int fieldRows) {
        String confirm = MenuPage.confirm(pad);
        java.util.List<MenuPage.Hint> hints = new java.util.ArrayList<>();
        hints.add(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"));
        int go = fieldRows + (joining() ? 1 : 0);
        if (focus < fieldRows) {
            boolean pool = state.visibleRows().get(focus) == TimeAttackMenuState.Row.POOL;
            hints.add(MenuPage.hint(MenuPage.ARROWS_LR, pool ? "Pool" : "Change"));
            hints.add(MenuPage.hint(confirm, pool ? "Edit pools" : "Next"));
        } else if (focus < go) {
            hints.add(MenuPage.hint(confirm, "Enter invite"));
        } else if (focus == go) {
            hints.add(MenuPage.hint(confirm, switch (state.mode()) {
                case SOLO -> "Start";
                case HOST_LAN -> "Create";
                case JOIN_LAN -> "Join";
                case BROWSE -> "Browse";
            }));
        } else {
            hints.add(MenuPage.hint(confirm, "Open"));
        }
        hints.add(MenuPage.hint(MenuPage.back(pad), "Title"));
        return hints;
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

    /** True while the first-run name prompt is shown (tests). */
    boolean isPromptingForName() {
        return namePrompt != null;
    }

    /** The scene's settings (tests). */
    TimeAttackSettings settings() {
        return settings;
    }
}
