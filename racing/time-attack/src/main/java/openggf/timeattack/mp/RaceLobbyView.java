package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.TextLayout;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ReadyRules;
import com.openggf.net.protocol.ChatMode;
import openggf.timeattack.ProfileView;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.MenuPage;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The race lobby between rounds, drawn as a Time Attack scene view: the room's players (names in
 * each player's colour, in any script), chat history and private invite actions, the
 * host's START ROUND, chat entry, PROFILE (rename and change look, announced to the room while
 * it is in the lobby), track votes (keys 1-3) and LEAVE ROOM. The network pump and the round launch belong to
 * {@link RaceSession#poll()}, which runs every tick before this view, so an open chat editor or
 * details page can never delay a round start.
 */
public final class RaceLobbyView {
    private static final int BACKGROUND = 0x101830;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int DIM = 0xFF8C8C8C;

    private enum Focus { PLAYERS, HISTORY, READY, START, RULE, CHAT, QUICK_CHAT, CHAT_MODE, PROFILE, INVITE, LEAVE }

    private final MultiplayerRaceCoordinator coordinator;
    private final boolean host;
    private final ControlMessage.RoundConfig configuredRound;
    private Runnable inviteAction;
    private final Runnable leaveHandler;
    private final Consumer<MenuCue> cues;
    private final Supplier<ProfileView> profiles;
    private ProfileView profile;
    private Focus focus;
    private int playerPage;
    private int chatPage;
    private TextEntry editor;
    private final QuickChatChooser quickChat = new QuickChatChooser();
    private boolean details;
    /** Hints name controller buttons after a pad press. */
    private boolean pad;
    private String notice;
    private Consumer<ReadyRules> readyRuleChosen = rule -> { };
    private Consumer<ChatMode> chatModeChosen = mode -> { };
    private boolean levelBehind;
    /** Players on one page of the roster card, from the last drawn screen height. */
    private int playersPerPage = RaceLobbyScreen.playersPerPage(224, true);

    /**
     * @param shareCode legacy invite argument; sharing uses {@link #setInviteAction(Runnable)}
     */
    public RaceLobbyView(MultiplayerRaceCoordinator coordinator, boolean host,
                         ControlMessage.RoundConfig configuredRound, String shareCode,
                         Runnable leaveHandler, Consumer<MenuCue> cues) {
        this(coordinator, host, configuredRound, shareCode, leaveHandler, cues, null);
    }

    /**
     * @param profiles opens the profile page for the PROFILE action (its SAVE writes the settings);
     *                 null hides the action
     */
    public RaceLobbyView(MultiplayerRaceCoordinator coordinator, boolean host,
                         ControlMessage.RoundConfig configuredRound, String shareCode,
                         Runnable leaveHandler, Consumer<MenuCue> cues, Supplier<ProfileView> profiles) {
        this.profiles = profiles;
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.host = host;
        this.configuredRound = Objects.requireNonNull(configuredRound, "configuredRound");
        this.leaveHandler = Objects.requireNonNull(leaveHandler, "leaveHandler");
        this.cues = Objects.requireNonNull(cues, "cues");
        this.focus = Focus.READY;
    }

    public void setInviteAction(Runnable open) {
        inviteAction = Objects.requireNonNull(open, "open");
    }

    /** Called with the owner's newly chosen ready rule (the scene saves it in the settings). */
    public void setReadyRuleListener(Consumer<ReadyRules> listener) {
        readyRuleChosen = Objects.requireNonNull(listener, "listener");
    }

    /** Saves the owner's requested mode; the display always uses the authoritative reply. */
    public void setChatModeListener(Consumer<ChatMode> listener) {
        chatModeChosen = Objects.requireNonNull(listener, "listener");
    }

    /** The view is drawn over the lobby level: its hint says how to play once ready. */
    public void setLevelBehind(boolean behind) {
        levelBehind = behind;
    }

    /**
     * True on the lobby's own page (no chat editor, profile or details page open): only there do
     * Start and Enter toggle between the menu and the character.
     */
    public boolean isOnMainPage() {
        return profile == null && editor == null && !details && !quickChat.isOpen();
    }

    private void reconcileChatMode() {
        if (!coordinator.session().chatMode().allowsText()) editor = null;
        if (!coordinator.session().chatMode().allowsQuickChat()) quickChat.close();
        if (!choices().contains(focus)) focus = Focus.READY;
    }

    public void update(ViewInput input) {
        pad = MenuPage.controller(input, pad);
        reconcileChatMode();
        if (quickChat.isOpen()) {
            quickChat.update(input, phrase -> {
                coordinator.sendQuickChat(phrase);
                cues.accept(MenuCue.CONFIRM);
            });
            return;
        }
        if (profile != null) {
            profile.update(input);
            if (profile.consumeCloseRequested()) {
                if (profile.wasSaved()) announceProfile(profile);
                profile = null;
            }
            return;
        }
        if (details) {
            if (input.back() || input.accept()) {
                details = false;
                cues.accept(input.back() ? MenuCue.CANCEL : MenuCue.CONFIRM);
            }
            return;
        }
        if (editor != null) {
            switch (editor.update(input)) {
                case ACCEPTED -> {
                    String text = editor.text().strip();
                    if (!text.isEmpty()) coordinator.sendChat(text);
                    editor = null;
                    cues.accept(MenuCue.CONFIRM);
                }
                case CANCELLED -> {
                    editor = null;
                    cues.accept(MenuCue.CANCEL);
                }
                case NONE -> { }
            }
            return;
        }
        if (inviteAction != null && input.keyPressed(SceneKeys.I)) {
            inviteAction.run();
            cues.accept(MenuCue.CONFIRM);
            return;
        }
        if (input.back()) {
            cues.accept(MenuCue.CANCEL);
            leaveHandler.run();
            return;
        }
        voteKeys(input);
        if (input.up() || input.down() || input.accept()) {
            notice = null;
        }
        Focus beforeFocus = focus;
        int beforePlayers = playerPage;
        int beforeChat = chatPage;
        List<Focus> choices = choices();
        int delta = input.up() ? -1 : input.down() ? 1 : 0;
        if (delta != 0) focus = choices.get(Math.floorMod(choices.indexOf(focus) + delta, choices.size()));
        int horizontal = input.left() ? -1 : input.right() ? 1 : 0;
        if (focus == Focus.PLAYERS) playerPage = Math.clamp(playerPage + horizontal, 0,
                Math.max(0, (coordinator.session().players().size() - 1) / playersPerPage));
        if (focus == Focus.HISTORY) chatPage = Math.clamp(chatPage + horizontal, 0,
                Math.max(0, coordinator.session().chatLines().size() - 1));
        if (focus == Focus.RULE && horizontal != 0) changeReadyRule(horizontal);
        if (focus == Focus.CHAT_MODE && horizontal != 0) changeChatMode(horizontal);
        if (focus != beforeFocus || playerPage != beforePlayers || chatPage != beforeChat) {
            cues.accept(MenuCue.NAVIGATE);
        }
        if (input.accept()) {
            switch (focus) {
                case CHAT -> {
                    editor = TextEntry.text("ROOM CHAT", "", Protocol.MAX_CHAT_CHARS);
                    cues.accept(MenuCue.CONFIRM);
                }
                case QUICK_CHAT -> {
                    if (input.controllerAccept()) quickChat.openController(input.physicalInput());
                    else quickChat.open();
                    cues.accept(MenuCue.CONFIRM);
                }
                case CHAT_MODE -> changeChatMode(1);
                case PROFILE -> {
                    if (canEditProfile()) {
                        profile = profiles.get();
                        cues.accept(MenuCue.CONFIRM);
                    } else {
                        notice = "Profile changes wait for the lobby";
                        cues.accept(MenuCue.ERROR);
                    }
                }
                case LEAVE -> {
                    cues.accept(MenuCue.CANCEL);
                    leaveHandler.run();
                }
                case START -> {
                    if (canStart() && everyoneReadyEnough()) {
                        ControlMessage.RoundConfig committed = coordinator.session().nextRoundConfig();
                        coordinator.sendRoundConfigure(committed == null ? configuredRound : committed);
                        cues.accept(MenuCue.CONFIRM);
                    } else {
                        if (canStart()) notice = "Waiting for every player to be ready";
                        cues.accept(MenuCue.ERROR);
                    }
                }
                case READY -> {
                    if (betweenRounds()) {
                        coordinator.setReady(!coordinator.session().isLocalReady());
                        cues.accept(MenuCue.CONFIRM);
                    } else {
                        cues.accept(MenuCue.ERROR);
                    }
                }
                case RULE -> changeReadyRule(1);
                case INVITE -> { inviteAction.run(); cues.accept(MenuCue.CONFIRM); }
                case HISTORY -> {
                    details = true;
                    cues.accept(MenuCue.CONFIRM);
                }
                case PLAYERS -> { }
            }
        }
    }

    private void voteKeys(ViewInput input) {
        if (coordinator.session().phase() != ClientRaceSession.Phase.VOTE) {
            return;
        }
        List<String> options = coordinator.session().voteOptions();
        for (int option = 0; option < Math.min(3, options.size()); option++) {
            if (input.keyPressed(SceneKeys.DIGIT_1 + option)) {
                coordinator.castVote(option);
                cues.accept(MenuCue.CONFIRM);
                return;
            }
        }
    }

    /** The owner steps through the ready rules; the host announces the change to everyone. */
    private void changeReadyRule(int step) {
        if (!host || !betweenRounds()) {
            cues.accept(MenuCue.ERROR);
            return;
        }
        ReadyRules[] rules = ReadyRules.values();
        ReadyRules current = ReadyRules.parse(coordinator.session().readyRule());
        int index = current == null ? 0 : current.ordinal();
        ReadyRules next = rules[Math.floorMod(index + step, rules.length)];
        coordinator.setReadyRule(next);
        readyRuleChosen.accept(next);
        cues.accept(MenuCue.NAVIGATE);
    }

    private void changeChatMode(int step) {
        if (!host || !betweenRounds()) { cues.accept(MenuCue.ERROR); return; }
        ChatMode[] modes = ChatMode.values();
        ChatMode next = modes[Math.floorMod(coordinator.session().chatMode().ordinal() + step, modes.length)];
        coordinator.setChatMode(next);
        chatModeChosen.accept(next);
        cues.accept(MenuCue.NAVIGATE);
    }

    private boolean betweenRounds() {
        ClientRaceSession.Phase phase = coordinator.session().phase();
        return phase == ClientRaceSession.Phase.LOBBY || phase == ClientRaceSession.Phase.ROUND_END
                || phase == ClientRaceSession.Phase.VOTE;
    }

    /** The room's rule does not wait, or every present player is ready. */
    private boolean everyoneReadyEnough() {
        return !coordinator.session().readyRequired() || coordinator.session().playersNotReady().isEmpty();
    }

    private List<Focus> choices() {
        List<Focus> choices = new ArrayList<>(List.of(Focus.PLAYERS, Focus.HISTORY, Focus.READY));
        if (host) {
            choices.add(Focus.START);
            choices.add(Focus.RULE);
        }
        if (coordinator.session().chatMode().allowsText()) choices.add(Focus.CHAT);
        if (coordinator.session().chatMode().allowsQuickChat()) choices.add(Focus.QUICK_CHAT);
        if (host) choices.add(Focus.CHAT_MODE);
        if (profiles != null) choices.add(Focus.PROFILE);
        if (inviteAction != null) choices.add(Focus.INVITE);
        choices.add(Focus.LEAVE);
        return choices;
    }

    /** The host accepts renames and looks only while the room is in its lobby phase. */
    private boolean canEditProfile() {
        return profiles != null && coordinator.session().phase() == ClientRaceSession.Phase.LOBBY;
    }

    /** Tells the room the saved name and look; the host answers with PlayerUpsert and PlayerLook. */
    private void announceProfile(ProfileView saved) {
        if (coordinator.session().phase() != ClientRaceSession.Phase.LOBBY) {
            notice = "Saved; the room sees it next time you join";
            return;
        }
        String name = saved.name();
        if (name.isEmpty()) {
            // A profile without a chosen name keeps the identity-prefix name the room already has.
            int slot = coordinator.session().localSlot();
            name = coordinator.session().players().stream().filter(player -> player.slot() == slot)
                    .map(ControlMessage.PlayerInfo::displayName).findFirst().orElse("");
        }
        if (name.isEmpty()) return;
        coordinator.updateProfile(name, saved.appearance());
    }

    private boolean canStart() {
        ClientRaceSession.Phase phase = coordinator.session().phase();
        return host && (phase == ClientRaceSession.Phase.LOBBY || phase == ClientRaceSession.Phase.ROUND_END);
    }

    /** A one-line message from the session (a round that could not start), shown until the next action. */
    public void showStatus(String message) {
        notice = message == null || message.isBlank() ? null : message;
    }

    /** True while the chat editor takes keyboard text. */
    public boolean isEditingChat() {
        return editor != null;
    }

    /** True while any field (chat, or the profile's name) takes keyboard text. */
    public boolean isEditingText() {
        return editor != null || (profile != null && profile.isEditingText());
    }

    /** True while the profile page is open (tests). */
    boolean isShowingProfile() {
        return profile != null;
    }

    /** True while the history page is open (tests). */
    boolean isShowingDetails() {
        return details;
    }

    boolean isShowingQuickChat() { return quickChat.isOpen(); }
    boolean isShowingRadialQuickChat() { return quickChat.isRadial(); }

    public void draw(SceneCanvas canvas) {
        reconcileChatMode();
        if (quickChat.isOpen()) {
            canvas.clear(BACKGROUND);
            ControlMessage.RoundConfig current = coordinator.session().roundConfig();
            if (current == null) current = configuredRound;
            quickChat.draw(canvas, TimeAttackQuickChat.zoneName(current.gameId(),
                    current.zone(), current.act()), MenuPage.back(pad));
            return;
        }
        if (profile != null) {
            profile.draw(canvas);
            return;
        }
        if (editor != null) {
            editor.draw(canvas);
            return;
        }
        if (details) {
            drawDetails(canvas);
            return;
        }
        // Over the lobby level only the cards are drawn, so the level stays in view around them.
        if (!(canvas instanceof openggf.timeattack.ui.OverLevelCanvas)) canvas.clear(BACKGROUND);
        RaceLobbyScreen.draw(canvas, screen(canvas.width(), canvas.height()));
    }

    /** The main page as measured cards (tests read its text). */
    RaceLobbyScreen.Screen screen(int width, int height) {
        ClientRaceSession race = coordinator.session();
        ControlMessage.RoomDescriptor room = race.room();
        List<ControlMessage.PlayerInfo> players = race.players();
        List<RaceLobbyScreen.Player> rows = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            ControlMessage.PlayerInfo player = players.get(i);
            String character = player.character() == null ? "?"
                    : player.character().toUpperCase(java.util.Locale.ROOT);
            rows.add(new RaceLobbyScreen.Player(player.displayName(), nameColour(player.slot()),
                    race.isReady(player.slot()), player.slot() == race.localSlot(),
                    i == 0 ? "HOST" : player.newPlayer() ? "NEW" : "", character));
        }
        boolean secondaryCard = true;
        playersPerPage = RaceLobbyScreen.playersPerPage(height, secondaryCard);
        playerPage = Math.min(playerPage, Math.max(0, (players.size() - 1) / playersPerPage));

        List<RaceLobbyScreen.Note> status = new ArrayList<>();
        // A host's menu already shows the mode on its CHAT row; members see it here.
        if (!choices().contains(Focus.CHAT_MODE)) {
            status.add(new RaceLobbyScreen.Note(race.chatMode().label(), HudTextLayout.ACCENT, false));
        }
        if (!race.readyRequired()) {
            status.add(new RaceLobbyScreen.Note("NOBODY WAITS", HudTextLayout.MUTED, false));
        } else if (race.playersNotReady().isEmpty()) {
            status.add(new RaceLobbyScreen.Note("EVERYONE IS READY", RaceLobbyScreen.READY, false));
        } else {
            StringBuilder names = new StringBuilder();
            for (ControlMessage.PlayerInfo player : race.playersNotReady()) {
                if (!names.isEmpty()) names.append(", ");
                names.append(player.displayName());
            }
            status.add(new RaceLobbyScreen.Note("WAITING FOR", HudTextLayout.GOLD, false));
            status.add(new RaceLobbyScreen.Note(names.toString(), HudTextLayout.WHITE, true));
        }
        if (notice != null) status.add(new RaceLobbyScreen.Note(notice, HudTextLayout.GOLD, false));

        boolean ready = race.isLocalReady();
        List<RaceLobbyScreen.Action> actions = new ArrayList<>();
        for (Focus item : choices()) {
            switch (item) {
                case READY -> actions.add(action(item, ready ? "Cancel ready" : "Ready up", betweenRounds()));
                case START -> {
                    boolean startable = canStart() && everyoneReadyEnough();
                    actions.add(action(item, startable ? "Start round" : canStart() ? "Start round (waiting)"
                            : "Waiting for the round", startable));
                }
                case RULE -> actions.add(action(item, "Rule < " + MenuPage.sentence(ruleLabel(race.readyRule())) + " >",
                        betweenRounds()));
                case CHAT -> actions.add(action(item, "Chat", true));
                case QUICK_CHAT -> actions.add(action(item, "Quick chat", true));
                case CHAT_MODE -> actions.add(action(item, "Chat < " + MenuPage.sentence(race.chatMode().label()) + " >",
                        betweenRounds()));
                case PROFILE -> actions.add(action(item, "Name and look", canEditProfile()));
                case INVITE -> actions.add(action(item, "Room invite", true));
                case LEAVE -> actions.add(action(item, "Leave room", true));
                case PLAYERS, HISTORY -> { }
            }
        }
        long readyCount = players.stream().filter(player -> race.isReady(player.slot())).count();

        // Once ready over the lobby level, Start (Enter on a keyboard) plays instead of selecting.
        List<MenuPage.Hint> hints = new ArrayList<>();
        if (race.phase() == ClientRaceSession.Phase.VOTE) hints.add(MenuPage.hint("1-3", "Vote"));
        hints.add(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"));
        boolean plays = levelBehind && ready;
        if (pad || !plays) hints.add(MenuPage.hint(MenuPage.confirm(pad), "Select"));
        if (plays) hints.add(MenuPage.hint(pad ? "START" : "ENTER", "Play"));
        hints.add(MenuPage.hint(MenuPage.back(pad), "Leave"));
        RaceLobbyScreen.Model model = new RaceLobbyScreen.Model(
                room == null ? "Connecting..." : room.name(), nextTrack(room), phaseLabel(),
                phaseColour(), room != null && room.verified(), rows, room == null ? 0 : room.maxPlayers(),
                playerPage, focus == Focus.PLAYERS, secondary(), "READY " + ruleLabel(race.readyRule()),
                readyCount + "/" + players.size(), status, actions, hints);
        return RaceLobbyScreen.layout(model, width, height);
    }

    private RaceLobbyScreen.Action action(Focus item, String label, boolean enabled) {
        return new RaceLobbyScreen.Action(label, focus == item, enabled);
    }

    /** The card under the players: the vote while one runs, the last round's podium, else the chat. */
    private RaceLobbyScreen.Secondary secondary() {
        ClientRaceSession race = coordinator.session();
        boolean focused = focus == Focus.HISTORY;
        if (race.phase() == ClientRaceSession.Phase.VOTE) {
            List<String> options = race.voteOptions();
            List<ControlMessage.VoteCount> counts = race.voteCounts();
            int total = counts.stream().mapToInt(ControlMessage.VoteCount::votes).sum();
            List<RaceLobbyScreen.Row> rows = new ArrayList<>();
            for (int index = 0; index < Math.min(3, options.size()); index++) {
                String key = options.get(index);
                int votes = counts.stream().filter(count -> count.trackKey().equals(key))
                        .mapToInt(ControlMessage.VoteCount::votes).findFirst().orElse(0);
                rows.add(new RaceLobbyScreen.Choice(index + 1, MultiplayerHudRenderer.trackLabel(key), votes, total));
            }
            return new RaceLobbyScreen.Secondary("NEXT TRACK", Math.max(0, race.voteRemainingMillis() / 1000) + "S",
                    rows, focused, -1);
        }
        if (race.phase() == ClientRaceSession.Phase.ROUND_END && !race.podiumTop(3).isEmpty()) {
            List<RaceLobbyScreen.Row> rows = new ArrayList<>();
            ControlMessage.RoundConfig config = race.roundConfig();
            String policy = config == null ? null : config.characterPolicy();
            for (ControlMessage.StandingsRow row : race.podiumTop(3)) {
                rows.add(new RaceLobbyScreen.Note(HudTextLayout.standingsLine(row, policy),
                        HudTextLayout.WHITE, true));
            }
            return new RaceLobbyScreen.Secondary("RESULTS", "", rows, focused, -1);
        }
        List<String> history = visibleChatLines();
        chatPage = Math.min(chatPage, Math.max(0, history.size() - 1));
        List<RaceLobbyScreen.Row> rows = new ArrayList<>();
        for (String line : history) rows.add(chatRow(line));
        if (rows.isEmpty()) {
            // An empty history reads as a quiet sentence in the row face, like the chat it stands for.
            rows.add(new RaceLobbyScreen.Note(race.chatDisabled() ? "Chat hidden in Engine Settings"
                    : race.chatMode() == ChatMode.OFF ? "Room chat is off"
                    : "No messages yet",
                    RaceLobbyScreen.DISABLED, true));
        }
        String right = focused ? "A: OPEN" : chatPage > 0 ? "OLDER" : "";
        return new RaceLobbyScreen.Secondary("CHAT", right, rows,
                focused, history.isEmpty() ? -1 : chatPage);
    }

    /** A {@code name: text} chat line, with the sender coloured when exactly one player has that name. */
    private RaceLobbyScreen.Row chatRow(String line) {
        int colon = line.indexOf(": ");
        if (colon > 0) {
            String sender = line.substring(0, colon);
            List<ControlMessage.PlayerInfo> matches = coordinator.session().players().stream()
                    .filter(player -> sender.equals(player.displayName())).toList();
            int colour = matches.size() == 1 ? nameColour(matches.getFirst().slot()) : VALUE;
            return new RaceLobbyScreen.Chat(sender, colour, line.substring(colon + 2));
        }
        return new RaceLobbyScreen.Chat("", VALUE, line);
    }

    /** What happens next: the voted track, else the room's own. */
    private static String nextTrackKey(ControlMessage.RoomDescriptor room, String voted) {
        if (voted != null) return voted;
        return room == null ? null : room.gameId() + ":" + room.zone() + ":" + room.act();
    }

    private String nextTrack(ControlMessage.RoomDescriptor room) {
        if (coordinator.session().phase() == ClientRaceSession.Phase.VOTE) return "NEXT  TRACK BY VOTE";
        String key = nextTrackKey(room, coordinator.session().lastVoteResultTrackKey());
        if (key == null) return "";
        String game = key.substring(0, Math.max(0, key.indexOf(':'))).toUpperCase(java.util.Locale.ROOT);
        return "NEXT  " + game + "  " + MultiplayerHudRenderer.trackLabel(key);
    }

    private String phaseLabel() {
        ClientRaceSession race = coordinator.session();
        return switch (race.phase()) {
            case LOBBY -> "LOBBY";
            case VOTE -> "VOTE";
            case ROUND_END -> "ROUND OVER";
            case COUNTDOWN -> "STARTING";
            case RUNNING -> "ROUND RUNNING";
        };
    }

    private int phaseColour() {
        return coordinator.session().phase() == ClientRaceSession.Phase.LOBBY ? HudTextLayout.ACCENT
                : HudTextLayout.GOLD;
    }

    private static String ruleLabel(String rule) {
        ReadyRules parsed = ReadyRules.parse(rule);
        if (parsed == null) return "?";
        return switch (parsed) {
            case REQUIRED -> "REQUIRED";
            case AUTO -> "AUTO";
            case DEFAULT_BY_SIZE -> "BY SIZE";
        };
    }

    /** The colour a player chose for their name ({@link com.openggf.net.identity.PlayerAppearance#nameArgb}). */
    private int nameColour(int slot) {
        return coordinator.session().appearance(slot).nameArgb();
    }

    /**
     * One {@code name: text} chat line in the name font; the sender's name takes their colour when
     * exactly one player in the room has that name (lines carry the name, not the slot).
     */
    private void chatLine(SceneCanvas canvas, String line, int x, int y, int maxWidth) {
        String shown = NameFont.fit(line, maxWidth);
        int colon = line.indexOf(": ");
        int colour = VALUE;
        if (colon > 0) {
            String sender = line.substring(0, colon);
            List<ControlMessage.PlayerInfo> matches = coordinator.session().players().stream()
                    .filter(player -> sender.equals(player.displayName())).toList();
            if (matches.size() == 1) colour = nameColour(matches.getFirst().slot());
        }
        if (colon <= 0 || colour == VALUE) {
            NameFont.draw(canvas, shown, x, y, VALUE);
            return;
        }
        int split = Math.min(colon, shown.length());
        String name = shown.substring(0, split);
        NameFont.draw(canvas, name, x, y, colour);
        NameFont.draw(canvas, shown.substring(split), x + NameFont.width(name), y, VALUE);
    }

    private List<String> visibleChatLines() {
        ClientRaceSession race = coordinator.session();
        return race.chatDisabled() || race.chatMode() == ChatMode.OFF ? List.of() : race.chatLines();
    }

    private void drawDetails(SceneCanvas canvas) {
        int width = canvas.width();
        MenuPage.page(canvas, "ROOM DETAILS", coordinator.session().chatMode().label(), "Players and the room's chat");
        int x = MenuPage.MARGIN + 6;
        int y = MenuPage.BODY_TOP + 1;
        MenuPage.section(canvas, "Players", x, y, width - 2 * x);
        y += 10;
        // Each player's identity tag, which tells two players with the same name apart.
        for (ControlMessage.PlayerInfo player : coordinator.session().players()) {
            if (y > 120) break;
            String fingerprint = player.fingerprint() == null ? "????"
                    : player.fingerprint().substring(0, Math.min(4, player.fingerprint().length()));
            String tag = "#" + fingerprint + (player.newPlayer() ? " NEW" : "");
            int tagWidth = com.openggf.mods.ui.CompactFont.width(tag, 1);
            NameFont.draw(canvas, NameFont.fit(player.displayName(), width - 2 * x - 8 - tagWidth), x, y,
                    nameColour(player.slot()));
            com.openggf.mods.ui.CompactFont.draw(canvas, tag, width - x - tagWidth, y + 4, 1, MenuPage.DIM);
            y += NameFont.LINE_HEIGHT;
        }
        y += 4;
        MenuPage.section(canvas, "Chat", x, y, width - 2 * x);
        y += 10;
        List<String> history = visibleChatLines();
        if (history.isEmpty()) {
            MenuPage.notice(canvas, MenuPage.Tone.INFO, coordinator.session().chatDisabled()
                    ? "Chat hidden in Engine Settings"
                    : coordinator.session().chatMode() == ChatMode.OFF ? "Room chat is off" : "No messages yet",
                    x, y, width - 2 * x);
        } else {
            // The newest messages that fit, oldest at the top.
            List<String> lines = new ArrayList<>();
            for (String message : history) lines.addAll(TextEntry.wrapPixels(message, width - 2 * x));
            int fit = Math.max(1, (MenuPage.FOOTER_Y - 2 - y) / NameFont.LINE_HEIGHT);
            for (String line : lines.subList(Math.max(0, lines.size() - fit), lines.size())) {
                chatLine(canvas, line, x, y, width - 2 * x);
                y += NameFont.LINE_HEIGHT;
            }
        }
        MenuPage.footer(canvas, "", MenuPage.TEXT, List.of(MenuPage.hint(MenuPage.back(pad), "Back")));
    }

}
