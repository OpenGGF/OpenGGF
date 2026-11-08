package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.NameFont;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ReadyRules;
import com.openggf.net.protocol.ChatMode;
import com.openggf.net.protocol.TrackKey;
import openggf.timeattack.CompetitionHudState;
import openggf.timeattack.ProfileView;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.MenuPage;
import openggf.timeattack.ui.FullTextReader;
import openggf.timeattack.ui.LapTimeFormat;
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

    private enum Focus { PLAYERS, HISTORY, READY, START, RESULTS, RULE, CHAT, QUICK_CHAT, CHAT_MODE, PROFILE, INVITE, LEAVE }

    /** The results pages: none, the placement table, or every row's lap splits and notes. */
    private enum ResultsPage { NONE, TABLE, DETAILS }

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
    /** The shown Room details lines as drawn (label and value per line), for tests. */
    private List<String> detailsShown = List.of();
    /** Room details' first shown line; {@link Integer#MAX_VALUE} opens at the newest message. */
    private int detailsScroll = Integer.MAX_VALUE;
    private int detailsTotal;
    private int detailsVisible = 1;
    /** Hints name controller buttons after a pad press. */
    private MenuPage.Keys keys = MenuPage.Keys.keyboard();
    private String notice;
    private Consumer<ReadyRules> readyRuleChosen = rule -> { };
    private Consumer<ChatMode> chatModeChosen = mode -> { };
    private boolean levelBehind;
    /** The Competition round's display state (lane C fills it); Time Attack rooms read it inactive. */
    private Supplier<CompetitionHudState> competition;
    private ResultsPage resultsPage = ResultsPage.NONE;
    private int resultsScroll;
    private int resultsVisible = 1;
    private int resultsTotal;
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
        this.competition = () -> coordinator.hudState().competition();
    }

    /** Where the view reads the Competition round's state (by default the coordinator's HUD state). */
    public void setCompetitionSource(Supplier<CompetitionHudState> source) {
        competition = Objects.requireNonNull(source, "source");
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
        return profile == null && editor == null && !details && !quickChat.isOpen()
                && resultsPage == ResultsPage.NONE;
    }

    /**
     * Room details' keys on the main page: the details key (F1, or R3 on a controller) anywhere, and
     * the chat card's Tab shortcut, which works even when Enter belongs to Play.
     */
    public boolean opensDetails(ViewInput input) {
        return isOnMainPage() && (input.details() || focus == Focus.HISTORY && input.keyPressed(SceneKeys.TAB));
    }

    /** Opens Room details: from the chat card at its newest message, otherwise at the room's facts. */
    private void openDetails(boolean newest) {
        details = true;
        detailsScroll = newest ? Integer.MAX_VALUE : 0;
        cues.accept(MenuCue.CONFIRM);
    }

    private void reconcileChatMode() {
        if (!coordinator.session().chatMode().allowsText()) editor = null;
        if (!coordinator.session().chatMode().allowsQuickChat()) quickChat.close();
        if (!choices().contains(focus)) focus = Focus.READY;
    }

    public void update(ViewInput input) {
        keys = MenuPage.Keys.of(input, keys);
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
        if (resultsPage != ResultsPage.NONE) {
            updateResults(input);
            return;
        }
        if (details) {
            if (input.back() || input.accept() || input.details() || input.keyPressed(SceneKeys.TAB)) {
                details = false;
                cues.accept(input.back() ? MenuCue.CANCEL : MenuCue.CONFIRM);
                return;
            }
            int step = input.up() ? -1 : input.down() ? 1 : 0;
            if (step != 0) {
                int last = Math.max(0, detailsTotal - detailsVisible);
                int current = detailsScroll == Integer.MAX_VALUE ? last : detailsScroll;
                // Opening on a message's first line may sit past the last full page; Down stays there.
                int next = Math.clamp(current + step, 0, Math.max(last, current));
                if (next != current) cues.accept(MenuCue.NAVIGATE);
                detailsScroll = next;
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
        if (opensDetails(input)) {
            openDetails(focus == Focus.HISTORY);
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
                case HISTORY -> openDetails(true);
                case RESULTS -> {
                    resultsPage = ResultsPage.TABLE;
                    resultsScroll = 0;
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
        if (host) choices.add(Focus.START);
        if (!competition.get().placements().isEmpty()) choices.add(Focus.RESULTS);
        if (host) choices.add(Focus.RULE);
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
        if (resultsPage != ResultsPage.NONE) {
            drawResults(canvas);
            return;
        }
        if (quickChat.isOpen()) {
            canvas.clear(BACKGROUND);
            ControlMessage.RoundConfig current = coordinator.session().roundConfig();
            if (current == null) current = configuredRound;
            quickChat.draw(canvas, TimeAttackQuickChat.zoneName(current.gameId(),
                    current.zone(), current.act()), keys.back());
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

        // Status reads as sentences in the row face. Who is ready is already on the PLAYERS card's
        // ready boxes, so the line says only how many are still to come (naming one player, who
        // may be on another page of the card).
        List<RaceLobbyScreen.Note> status = new ArrayList<>();
        // A host's menu already shows the mode on its Room chat row (as Settings names it); members see it here.
        if (!choices().contains(Focus.CHAT_MODE)) {
            status.add(new RaceLobbyScreen.Note("Room chat: " + MenuPage.sentence(race.chatMode().label()),
                    HudTextLayout.ACCENT, true));
        }
        CompetitionHudState round = competition.get();
        if (round.lateJoin()) {
            status.add(new RaceLobbyScreen.Note("You joined after the start: you race the next round",
                    HudTextLayout.GOLD, true));
        }
        status.add(readiness(race));
        if (notice != null) status.add(new RaceLobbyScreen.Note(notice, HudTextLayout.GOLD, true));

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
                case RULE -> actions.add(action(item, "Ready rule < " + MenuPage.sentence(ruleLabel(race.readyRule())) + " >",
                        betweenRounds()));
                case CHAT -> actions.add(action(item, "Chat", true));
                case QUICK_CHAT -> actions.add(action(item, "Quick chat", true));
                case CHAT_MODE -> actions.add(action(item, "Room chat < " + MenuPage.sentence(race.chatMode().label()) + " >",
                        betweenRounds()));
                case PROFILE -> actions.add(action(item, "Name and look", canEditProfile()));
                case INVITE -> actions.add(action(item, "Room invite", true));
                case RESULTS -> actions.add(action(item, race.phase() == ClientRaceSession.Phase.ROUND_END
                        ? "Results" : "Finish order", true));
                case LEAVE -> actions.add(action(item, "Leave room", true));
                case PLAYERS, HISTORY -> { }
            }
        }
        long readyCount = players.stream().filter(player -> race.isReady(player.slot())).count();

        // Once ready over the lobby level, Start (Enter on a keyboard) plays instead of selecting, so
        // a keyboard selects rows with its bound A (or C) key, named from the live bindings.
        List<MenuPage.Hint> hints = new ArrayList<>();
        // Votes are the keyboard's digit keys; a controller has no vote input, so it is not offered one.
        if (race.phase() == ClientRaceSession.Phase.VOTE && !keys.pad()) hints.add(MenuPage.hint("1-3", "Vote"));
        hints.add(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"));
        boolean plays = levelBehind && ready;
        hints.add(MenuPage.hint(plays ? keys.select() : keys.confirm(), "Select"));
        if (plays) hints.add(MenuPage.hint(keys.start(), "Play"));
        hints.add(MenuPage.hint(keys.details(), "Details"));
        hints.add(MenuPage.hint(keys.back(), "Leave"));
        RaceLobbyScreen.Model model = new RaceLobbyScreen.Model(
                room == null ? "Connecting..." : room.name(), nextTrack(room), phaseLabel(),
                phaseColour(), room != null && room.verified(), rows, room == null ? 0 : room.maxPlayers(),
                playerPage, focus == Focus.PLAYERS, secondary(), "READY " + ruleLabel(race.readyRule()),
                readyCount + "/" + players.size(), status, actions, hints, timesLabel(room, round),
                massStarting(round) && round.massStartPhase() == CompetitionHudState.MassStartPhase.LOADING
                        ? "LOADING" : "");
        return RaceLobbyScreen.layout(model, width, height);
    }

    /** One sentence for who the round is waiting on; the ready boxes say which players. */
    private static RaceLobbyScreen.Note readiness(ClientRaceSession race) {
        List<ControlMessage.PlayerInfo> waiting = race.playersNotReady();
        if (waiting.isEmpty() && !race.players().isEmpty())
            return new RaceLobbyScreen.Note("Everyone is ready", RaceLobbyScreen.READY, true);
        if (!race.readyRequired()) return new RaceLobbyScreen.Note("Ready is optional", HudTextLayout.MUTED, true);
        if (waiting.isEmpty()) return new RaceLobbyScreen.Note("Waiting for players", HudTextLayout.MUTED, true);
        if (waiting.stream().anyMatch(player -> player.slot() == race.localSlot())) {
            return new RaceLobbyScreen.Note(waiting.size() == 1 ? "Waiting for you"
                    : "Waiting: you + " + (waiting.size() - 1),
                    HudTextLayout.GOLD, true);
        }
        return new RaceLobbyScreen.Note(waiting.size() == 1 ? "Waiting for " + waiting.getFirst().displayName()
                : "Waiting for " + waiting.size() + " players", HudTextLayout.GOLD, true);
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
        CompetitionHudState round = competition.get();
        if (round.isCompetition() && !round.placements().isEmpty()) {
            // While the round runs this is the live finish order; at its end, the placements.
            List<RaceLobbyScreen.Row> rows = new ArrayList<>();
            for (CompetitionHudState.PlacementRow row : MultiplayerHudRenderer.displayOrder(round.placements())) {
                if (rows.size() == 3) break;
                rows.add(new RaceLobbyScreen.Note(placementLine(round, row), HudTextLayout.WHITE, true));
            }
            boolean ended = race.phase() == ClientRaceSession.Phase.ROUND_END;
            return new RaceLobbyScreen.Secondary(ended ? "RESULTS" : "FINISH ORDER",
                    round.unverified() ? "UNVERIFIED" : "", rows, focused, -1,
                    round.unverified() ? HudTextLayout.AMBER : HudTextLayout.MUTED);
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
            rows.add(new RaceLobbyScreen.Note(race.chatDisabled() ? "Chat hidden in Settings > Gameplay"
                    : race.chatMode() == ChatMode.OFF ? "Room chat is off"
                    : "No messages yet",
                    RaceLobbyScreen.DISABLED, true));
        }
        // The card's own key: the pad's confirm button, or Tab on a keyboard (Enter may belong to Play).
        String right = focused ? keys.pad() ? (keys.confirm() + ": OPEN").toUpperCase(java.util.Locale.ROOT)
                : "TAB: DETAILS" : chatPage > 0 ? "OLDER" : "";
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
        return room == null ? null : trackKey(room.gameId(), room.trackKind(), room.zone(), room.act());
    }

    /** The protocol key of a room's or round's track (an arena's index shares the zone field). */
    static String trackKey(String gameId, String kind, int zone, int act) {
        try {
            return new TrackKey(gameId, kind == null ? "ACT" : kind, zone, act).format();
        } catch (IllegalArgumentException malformed) {
            return gameId + ":" + zone + ":" + act;
        }
    }

    /** {@code 1 Tails 0'21"50}: rank (or a dash when unfinished), name with any swap mark, and the time. */
    private static String placementLine(CompetitionHudState round, CompetitionHudState.PlacementRow row) {
        String rank = row.outcome() == CompetitionHudState.Outcome.FINISHED ? Integer.toString(row.rank()) : "-";
        return rank + " " + row.name() + (row.taintedSwap() ? " *" : "") + "  "
                + MultiplayerHudRenderer.placementTime(round, row);
    }

    /** A Competition mass start that has not released yet. */
    private boolean massStarting(CompetitionHudState round) {
        return round.isCompetition() && round.massStartPhase() != CompetitionHudState.MassStartPhase.RELEASED
                && coordinator.session().phase() != ClientRaceSession.Phase.ROUND_END;
    }

    /**
     * The header's times label: a Competition room without verification publishes its times, labelled
     * UNVERIFIED (design D17); the mode tag precedes it. Time Attack rooms keep the default wording.
     */
    private static String timesLabel(ControlMessage.RoomDescriptor room, CompetitionHudState round) {
        boolean competitionRoom = round.isCompetition() || room != null && "COMPETITION".equals(room.mode());
        if (!competitionRoom) return "";
        return "COMPETITION  " + (room != null && room.verified() ? "VERIFIED" : "UNVERIFIED");
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
        CompetitionHudState round = competition.get();
        if (round.lateJoin() && race.phase() != ClientRaceSession.Phase.ROUND_END) return "NEXT ROUND";
        if (massStarting(round)) return MultiplayerHudRenderer.startLabel(round);
        return switch (race.phase()) {
            case LOBBY -> "LOBBY";
            case VOTE -> "VOTE";
            case ROUND_END -> "ROUND OVER";
            case COUNTDOWN -> "STARTING";
            case RUNNING -> "ROUND RUNNING";
        };
    }

    private int phaseColour() {
        return coordinator.session().phase() == ClientRaceSession.Phase.LOBBY && !massStarting(competition.get())
                ? HudTextLayout.ACCENT : HudTextLayout.GOLD;
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

    /**
     * One line of Room details: a section caption, a room fact (its label on the first line), a player
     * (their name in their colour, with the identity tag on its first line), a chat message's first
     * line (sender coloured) or one of its continuations, or a quiet notice. Every value, name and
     * message is whole, wrapped at spaces; code points the fonts cannot draw read as {@code [U+XXXX]}.
     */
    record DetailLine(Kind kind, String text, int colour, String tag) {
        enum Kind { SECTION, FACT, PLAYER, NAME_CONTINUATION, CHAT, CHAT_CONTINUATION, NOTICE }

        /** The line as drawn: a fact's label, then its text (the tag drawn at the right is left out). */
        String plain() { return kind == Kind.FACT ? tag + text : text; }
    }

    /** A continuation starts this far in, so it reads as part of the line above. */
    private static final int DETAIL_INDENT = 6;

    /** The room facts' labels, which set their value column. */
    private static List<String> factLabels() {
        return List.of("Room", "Next", "Phase", "Ready rule", "Room chat", "Ready", "Notice");
    }

    List<DetailLine> detailLines(int width) {
        int room = width - 2 * (MenuPage.MARGIN + 6) - 6;
        ClientRaceSession race = coordinator.session();
        List<DetailLine> lines = new ArrayList<>();
        // The main page's header and status, whole: what its cards can only preview.
        lines.add(new DetailLine(DetailLine.Kind.SECTION, "Room", 0, ""));
        int factRoom = room - MenuPage.labelColumn(factLabels());
        fact(lines, "Room", race.room() == null ? "Connecting..." : race.room().name(), factRoom);
        fact(lines, "Next", nextTrackWords(race.room()), factRoom);
        fact(lines, "Phase", MenuPage.sentence(phaseLabel()), factRoom);
        fact(lines, "Ready rule", MenuPage.sentence(ruleLabel(race.readyRule())), factRoom);
        fact(lines, "Room chat", MenuPage.sentence(race.chatMode().label()), factRoom);
        fact(lines, "Ready", readiness(race).text(), factRoom);
        if (notice != null) fact(lines, "Notice", notice, factRoom);
        lines.add(new DetailLine(DetailLine.Kind.SECTION, "Players", 0, ""));
        // Each player's identity tag, which tells two players with the same name apart.
        for (ControlMessage.PlayerInfo player : race.players()) {
            String fingerprint = player.fingerprint() == null ? "????"
                    : player.fingerprint().substring(0, Math.min(4, player.fingerprint().length()));
            String character = player.character() == null ? "?" : player.character().toUpperCase(java.util.Locale.ROOT);
            String tag = "#" + fingerprint + (player.newPlayer() ? " NEW" : "") + " " + character
                    + (race.isReady(player.slot()) ? " READY" : "");
            int tagWidth = com.openggf.mods.ui.CompactFont.width(tag, 1);
            List<String> name = MenuPage.wrap(FullTextReader.readable(player.displayName()), room - 8 - tagWidth);
            for (int i = 0; i < name.size(); i++) lines.add(new DetailLine(i == 0 ? DetailLine.Kind.PLAYER
                    : DetailLine.Kind.NAME_CONTINUATION, name.get(i), nameColour(player.slot()), i == 0 ? tag : ""));
        }
        lines.add(new DetailLine(DetailLine.Kind.SECTION, "Chat", 0, ""));
        List<String> history = visibleChatLines();
        if (history.isEmpty()) {
            lines.add(new DetailLine(DetailLine.Kind.NOTICE, race.chatDisabled()
                    ? "Chat hidden in Settings > Gameplay"
                    : race.chatMode() == ChatMode.OFF ? "Room chat is off" : "No messages yet", 0, ""));
        }
        for (String message : history) {
            List<String> wrapped = MenuPage.wrap(FullTextReader.readable(message), room - DETAIL_INDENT);
            for (int i = 0; i < wrapped.size(); i++) lines.add(new DetailLine(i == 0 ? DetailLine.Kind.CHAT
                    : DetailLine.Kind.CHAT_CONTINUATION, wrapped.get(i), VALUE, ""));
        }
        if (race.phase() == ClientRaceSession.Phase.VOTE && !race.voteOptions().isEmpty()) {
            lines.add(new DetailLine(DetailLine.Kind.SECTION, "Vote", 0, ""));
            List<String> options = race.voteOptions();
            for (int i = 0; i < options.size(); i++) {
                fact(lines, Integer.toString(i + 1), MultiplayerHudRenderer.trackLabel(options.get(i)), factRoom);
            }
        }
        List<ControlMessage.StandingsRow> standings = race.podiumTop(race.players().size());
        if (!standings.isEmpty()) {
            lines.add(new DetailLine(DetailLine.Kind.SECTION, "Results", 0, ""));
            ControlMessage.RoundConfig config = race.roundConfig();
            for (ControlMessage.StandingsRow row : standings) {
                for (String line : MenuPage.wrap(FullTextReader.readable(HudTextLayout.standingsLine(row,
                        config == null ? null : config.characterPolicy())), room)) {
                    lines.add(new DetailLine(DetailLine.Kind.CHAT, line, VALUE, ""));
                }
            }
        }
        return lines;
    }

    /** A labelled room fact, its value wrapped under itself after the label column. */
    private static void fact(List<DetailLine> lines, String label, String value, int width) {
        if (value == null || value.isBlank()) return;
        List<String> wrapped = MenuPage.wrap(FullTextReader.readable(value), width);
        for (int i = 0; i < wrapped.size(); i++) {
            lines.add(new DetailLine(DetailLine.Kind.FACT, wrapped.get(i), 0, i == 0 ? label : ""));
        }
    }

    /** What happens next, in words ("Sonic 1, Green Hill 1"). */
    private String nextTrackWords(ControlMessage.RoomDescriptor room) {
        if (coordinator.session().phase() == ClientRaceSession.Phase.VOTE) return "Track by vote";
        String key = nextTrackKey(room, coordinator.session().lastVoteResultTrackKey());
        if (key == null) return "";
        String game = key.substring(0, Math.max(0, key.indexOf(':')));
        return MenuPage.gameName(game) + ", " + MenuPage.words(MultiplayerHudRenderer.trackLabel(key));
    }

    /** The lines Room details shows now, after its scroll position settles (tests). */
    List<DetailLine> shownDetailLines(int width) {
        List<DetailLine> lines = detailLines(width);
        int first = detailsFirst(lines);
        return lines.subList(first, Math.min(lines.size(), first + detailsVisible));
    }

    private int detailsFirst(List<DetailLine> lines) {
        detailsVisible = Math.max(1, (MenuPage.FOOTER_Y - 2 - (MenuPage.BODY_TOP + 1)) / NameFont.LINE_HEIGHT);
        detailsTotal = lines.size();
        int last = Math.max(0, lines.size() - detailsVisible);
        if (detailsScroll == Integer.MAX_VALUE) {
            // Open at the newest messages, starting on a message's first line rather than mid-message.
            int first = last;
            while (first < lines.size() - 1 && (lines.get(first).kind() == DetailLine.Kind.CHAT_CONTINUATION
                    || lines.get(first).kind() == DetailLine.Kind.NAME_CONTINUATION
                    || lines.get(first).kind() == DetailLine.Kind.FACT && lines.get(first).tag().isEmpty())) first++;
            detailsScroll = first;
        }
        return Math.clamp(detailsScroll, 0, Math.max(last, Math.min(detailsScroll, lines.size() - 1)));
    }

    private void drawDetails(SceneCanvas canvas) {
        int width = canvas.width();
        MenuPage.page(canvas, "ROOM DETAILS", coordinator.session().chatMode().label(), "The room, its players and chat");
        int x = MenuPage.MARGIN + 6;
        int y = MenuPage.BODY_TOP + 1;
        int factX = x + MenuPage.labelColumn(factLabels());
        List<DetailLine> lines = detailLines(width);
        int first = detailsFirst(lines);
        int shown = Math.min(lines.size() - first, detailsVisible);
        List<DetailLine> page = lines.subList(first, first + shown);
        detailsShown = page.stream().map(DetailLine::plain).toList();
        for (DetailLine line : page) {
            switch (line.kind()) {
                case SECTION -> MenuPage.section(canvas, line.text(), x, y + 3, width - 2 * x - 6);
                case FACT -> {
                    if (!line.tag().isEmpty()) NameFont.draw(canvas, line.tag(), x, y, MenuPage.MUTED);
                    NameFont.draw(canvas, line.text(), factX, y, VALUE);
                }
                case PLAYER -> {
                    // The identity tag and character in the quiet colour; READY in the ready boxes' green.
                    boolean ready = line.tag().endsWith(" READY");
                    String quiet = ready ? line.tag().substring(0, line.tag().length() - " READY".length()) : line.tag();
                    int tagX = width - x - 6 - com.openggf.mods.ui.CompactFont.width(line.tag(), 1);
                    NameFont.draw(canvas, line.text(), x, y, line.colour());
                    com.openggf.mods.ui.CompactFont.draw(canvas, quiet, tagX, y + 4, 1, MenuPage.DIM);
                    if (ready) com.openggf.mods.ui.CompactFont.draw(canvas, "READY",
                            tagX + com.openggf.mods.ui.CompactFont.width(quiet, 1) + 7, y + 4, 1, RaceLobbyScreen.READY);
                }
                case NAME_CONTINUATION -> NameFont.draw(canvas, line.text(), x + DETAIL_INDENT, y, line.colour());
                case CHAT -> chatLine(canvas, line.text(), x, y, width - 2 * x - 6);
                case CHAT_CONTINUATION -> NameFont.draw(canvas, line.text(), x + DETAIL_INDENT, y, VALUE);
                case NOTICE -> MenuPage.notice(canvas, MenuPage.Tone.INFO, line.text(), x, y, width - 2 * x - 6);
            }
            y += NameFont.LINE_HEIGHT;
        }
        MenuPage.scrollbar(canvas, width - MenuPage.MARGIN - 2, MenuPage.BODY_TOP + 1, detailsVisible * NameFont.LINE_HEIGHT,
                first, detailsVisible, lines.size());
        List<MenuPage.Hint> hints = new ArrayList<>();
        if (lines.size() > detailsVisible) hints.add(MenuPage.hint(MenuPage.ARROWS_UD, "Scroll"));
        hints.add(MenuPage.hint(keys.back(), "Back"));
        String position = lines.size() > detailsVisible
                ? (first + 1) + "-" + (first + shown) + " of " + lines.size() : "";
        MenuPage.footer(canvas, position, MenuPage.DIM, hints);
    }

    // ── Results: the placement table and its lap-split details page ──────────

    private void updateResults(ViewInput input) {
        if (input.back()) {
            resultsPage = resultsPage == ResultsPage.DETAILS ? ResultsPage.TABLE : ResultsPage.NONE;
            resultsScroll = 0;
            cues.accept(MenuCue.CANCEL);
            return;
        }
        if (input.accept() || input.details()) {
            resultsPage = resultsPage == ResultsPage.TABLE ? ResultsPage.DETAILS : ResultsPage.TABLE;
            resultsScroll = 0;
            cues.accept(MenuCue.CONFIRM);
            return;
        }
        int step = input.up() ? -1 : input.down() ? 1 : 0;
        int next = Math.clamp(resultsScroll + step, 0, Math.max(0, resultsTotal - resultsVisible));
        if (next != resultsScroll) cues.accept(MenuCue.NAVIGATE);
        resultsScroll = next;
    }

    /** True while a results page is open (tests). */
    boolean isShowingResults() { return resultsPage != ResultsPage.NONE; }

    /** The shown results lines as drawn, cells joined by " | " (tests). */
    List<String> resultsVisibleLines() { return resultsShown; }

    private List<String> resultsShown = List.of();

    /** Width of a time column: every ROM clock and Time Attack time is seven tabular cells. */
    private static int timeColumn() { return com.openggf.mods.ui.CompactFont.width("0'00\"00", 1); }

    /** Width of the state column: its widest value. */
    private static int stateColumn() { return com.openggf.mods.ui.CompactFont.width("UNVERIFIED", 1); }

    private static final int COLUMN_GAP = 8;
    private static final int HEADER_ROW = 11;

    private void drawResults(SceneCanvas canvas) {
        int width = canvas.width();
        CompetitionHudState round = competition.get();
        boolean arena = round.isArena();
        boolean ended = coordinator.session().phase() == ClientRaceSession.Phase.ROUND_END;
        String title = resultsPage == ResultsPage.DETAILS ? arena ? "LAP SPLITS" : "RESULT NOTES"
                : ended ? "RESULTS" : "FINISH ORDER";
        String subtitle = roundTrackWords(round);
        if (round.unverified()) MenuPage.page(canvas, title, "UNVERIFIED", MenuPage.AMBER, subtitle);
        else MenuPage.page(canvas, title, ended ? null : "LIVE", subtitle);
        List<CompetitionHudState.PlacementRow> rows = MultiplayerHudRenderer.displayOrder(round.placements());
        if (resultsPage == ResultsPage.TABLE) drawTable(canvas, round, rows);
        else drawSplits(canvas, round, rows);
        List<MenuPage.Hint> hints = new ArrayList<>();
        if (resultsTotal > resultsVisible) hints.add(MenuPage.hint(MenuPage.ARROWS_UD, "Scroll"));
        hints.add(MenuPage.hint(keys.confirm(), resultsPage == ResultsPage.TABLE ? arena ? "Splits" : "Notes" : "Table"));
        hints.add(MenuPage.hint(keys.back(), "Back"));
        String position = resultsTotal > resultsVisible ? (resultsScroll + 1) + "-"
                + Math.min(resultsTotal, resultsScroll + resultsVisible) + " of " + resultsTotal : "";
        MenuPage.footer(canvas, position, MenuPage.DIM, hints);
    }

    /** The round's track in words ("Azure Lake, 5 laps"), from the round's configuration. */
    private String roundTrackWords(CompetitionHudState round) {
        ControlMessage.RoundConfig config = coordinator.session().roundConfig();
        if (config == null) config = configuredRound;
        String key = trackKey(config.gameId(), config.trackKind(), config.zone(), config.act());
        String words = MenuPage.words(MultiplayerHudRenderer.trackLabel(key));
        return round.isArena() && round.lapCount() > 0 ? words + ", " + round.lapCount() + " laps" : words;
    }

    /**
     * Rank, name (with a dim {@code *} on a swap-tainted run), the character's initial, total, best
     * lap on arenas, and the state: the verify state for a finisher, else DNF or DNS. Unfinished rows
     * come last.
     */
    private void drawTable(SceneCanvas canvas, CompetitionHudState round, List<CompetitionHudState.PlacementRow> rows) {
        int width = canvas.width();
        int x = MenuPage.MARGIN + 6;
        int right = width - MenuPage.MARGIN - 6;
        int stateX = right - stateColumn();
        int bestX = round.isArena() ? stateX - COLUMN_GAP - timeColumn() : stateX;
        int totalX = bestX - COLUMN_GAP - timeColumn();
        int characterX = totalX - COLUMN_GAP - 5;
        int nameX = x + com.openggf.mods.ui.CompactFont.width("00", 1) + 4;
        int y = MenuPage.BODY_TOP + 1;
        com.openggf.mods.ui.CompactFont.draw(canvas, "#", x, y + 1, 1, MenuPage.DIM);
        com.openggf.mods.ui.CompactFont.draw(canvas, "NAME", nameX, y + 1, 1, MenuPage.DIM);
        com.openggf.mods.ui.CompactFont.draw(canvas, "TOTAL", totalX, y + 1, 1, MenuPage.DIM);
        if (round.isArena()) com.openggf.mods.ui.CompactFont.draw(canvas, "BEST", bestX, y + 1, 1, MenuPage.DIM);
        com.openggf.mods.ui.CompactFont.draw(canvas, "STATE", stateX, y + 1, 1, MenuPage.DIM);
        canvas.fill(x, y + HEADER_ROW - 2, right - x, 1, 0xFF24418A);
        y += HEADER_ROW;
        resultsVisible = Math.max(1, (MenuPage.FOOTER_Y - 2 - y) / NameFont.LINE_HEIGHT);
        resultsTotal = rows.size();
        resultsScroll = Math.clamp(resultsScroll, 0, Math.max(0, resultsTotal - resultsVisible));
        List<String> shown = new ArrayList<>();
        int localSlot = coordinator.session().localSlot();
        for (int i = resultsScroll; i < Math.min(rows.size(), resultsScroll + resultsVisible); i++) {
            CompetitionHudState.PlacementRow row = rows.get(i);
            boolean finished = row.outcome() == CompetitionHudState.Outcome.FINISHED;
            if (row.slot() >= 0 && row.slot() == localSlot) {
                canvas.fill(MenuPage.MARGIN + 2, y, width - 2 * MenuPage.MARGIN - 4, NameFont.LINE_HEIGHT, 0x26FFFFFF);
            }
            String rank = finished ? Integer.toString(row.rank()) : "-";
            String character = row.character().isEmpty() ? "?" : row.character().substring(0, 1)
                    .toUpperCase(java.util.Locale.ROOT);
            String total = finished ? MultiplayerHudRenderer.placementTime(round, row) : "--";
            String best = row.bestLapFrames() > 0 ? LapTimeFormat.lap(row.bestLapFrames()) : "--";
            String state = finished ? switch (row.verifyState()) {
                case VERIFIED -> "VERIFIED";
                case PENDING -> "PENDING";
                case UNVERIFIED -> "UNVERIFIED";
            } : row.outcome().name();
            int stateColour = !finished ? row.outcome() == CompetitionHudState.Outcome.DNF ? MenuPage.RED : MenuPage.DIM
                    : switch (row.verifyState()) {
                        case VERIFIED -> MenuPage.GREEN;
                        case PENDING -> MenuPage.DIM;
                        case UNVERIFIED -> MenuPage.AMBER;
                    };
            int compactY = y + NameFont.BASELINE - 7 - 1;
            com.openggf.mods.ui.CompactFont.draw(canvas, rank, x, compactY, 1, finished ? MenuPage.TEXT : MenuPage.DIM);
            int markWidth = row.taintedSwap() ? com.openggf.mods.ui.CompactFont.width("*", 1) + 3 : 0;
            String name = NameFont.fit(FullTextReader.readable(row.name()), characterX - 6 - nameX - markWidth);
            NameFont.draw(canvas, name, nameX, y - 1, row.slot() >= 0 ? nameColour(row.slot()) : VALUE);
            if (row.taintedSwap()) com.openggf.mods.ui.CompactFont.draw(canvas, "*", nameX + NameFont.width(name) + 3,
                    compactY, 1, MenuPage.DIM);
            com.openggf.mods.ui.CompactFont.draw(canvas, character, characterX, compactY, 1, MenuPage.MUTED);
            HudTextLayout.compact(canvas, total, totalX, compactY, finished ? MenuPage.TEXT : MenuPage.DIM);
            if (round.isArena()) HudTextLayout.compact(canvas, best, bestX, compactY, MenuPage.MUTED);
            com.openggf.mods.ui.CompactFont.draw(canvas, state, stateX, compactY, 1, stateColour);
            shown.add(String.join(" | ", rank, row.name() + (row.taintedSwap() ? " *" : ""), character, total,
                    round.isArena() ? best : "", state));
            y += NameFont.LINE_HEIGHT;
        }
        MenuPage.scrollbar(canvas, width - MenuPage.MARGIN - 2, MenuPage.BODY_TOP + 1 + HEADER_ROW,
                resultsVisible * NameFont.LINE_HEIGHT, resultsScroll, resultsVisible, resultsTotal);
        resultsShown = List.copyOf(shown);
    }

    /** One line of the splits page: a racer's heading, their laps, or a quiet note. */
    private record SplitLine(CompetitionHudState.PlacementRow row, String text, boolean laps) { }

    /**
     * Every row in placement order: its rank, name, character and total, then (arenas) all five lap
     * times with the best in gold, then why it did not finish and whether a teleport swap touched it.
     */
    private void drawSplits(SceneCanvas canvas, CompetitionHudState round, List<CompetitionHudState.PlacementRow> rows) {
        int width = canvas.width();
        int x = MenuPage.MARGIN + 6;
        int right = width - MenuPage.MARGIN - 6;
        List<SplitLine> lines = new ArrayList<>();
        boolean tainted = false;
        for (CompetitionHudState.PlacementRow row : rows) {
            lines.add(new SplitLine(row, "", false));
            if (round.isArena() && !row.lapFrames().isEmpty()) lines.add(new SplitLine(row, "", true));
            if (!row.reason().isBlank()) lines.add(new SplitLine(null, row.outcome().name() + ": " + row.reason(), false));
            if (row.taintedSwap()) {
                lines.add(new SplitLine(null, "* A teleport swap moved this run", false));
                tainted = true;
            }
        }
        if (tainted) lines.add(new SplitLine(null, "* The swap exchanged this racer's position with a rival's, "
                + "so the time is not the racer's alone", false));
        // Long notes wrap into extra lines, whole.
        List<SplitLine> wrapped = new ArrayList<>();
        for (SplitLine line : lines) {
            if (line.row() != null) wrapped.add(line);
            else for (String part : MenuPage.wrap(line.text(), right - x - 6)) wrapped.add(new SplitLine(null, part, false));
        }
        int y = MenuPage.BODY_TOP + 1;
        resultsVisible = Math.max(1, (MenuPage.FOOTER_Y - 2 - y) / NameFont.LINE_HEIGHT);
        resultsTotal = wrapped.size();
        resultsScroll = Math.clamp(resultsScroll, 0, Math.max(0, resultsTotal - resultsVisible));
        List<String> shown = new ArrayList<>();
        for (int i = resultsScroll; i < Math.min(wrapped.size(), resultsScroll + resultsVisible); i++) {
            SplitLine line = wrapped.get(i);
            int compactY = y + NameFont.BASELINE - 7 - 1;
            if (line.row() == null) {
                NameFont.draw(canvas, line.text(), x + DETAIL_INDENT, y - 1, MenuPage.DIM);
                shown.add(line.text());
            } else if (!line.laps()) {
                CompetitionHudState.PlacementRow row = line.row();
                boolean finished = row.outcome() == CompetitionHudState.Outcome.FINISHED;
                String rank = finished ? Integer.toString(row.rank()) : "-";
                String total = MultiplayerHudRenderer.placementTime(round, row);
                String character = MenuPage.sentence(row.character());
                int totalX = right - com.openggf.mods.ui.CompactFont.width(total, 1);
                int characterWidth = NameFont.width(character);
                com.openggf.mods.ui.CompactFont.draw(canvas, rank, x, compactY, 1, MenuPage.TEXT);
                int nameX = x + com.openggf.mods.ui.CompactFont.width("00", 1) + 4;
                String name = NameFont.fit(FullTextReader.readable(row.name()) + (row.taintedSwap() ? " *" : ""),
                        totalX - 8 - characterWidth - 6 - nameX);
                NameFont.draw(canvas, name, nameX, y - 1, row.slot() >= 0 ? nameColour(row.slot()) : VALUE);
                NameFont.draw(canvas, character, nameX + NameFont.width(name) + 6, y - 1, MenuPage.MUTED);
                HudTextLayout.compact(canvas, total, totalX, compactY, finished ? MenuPage.TEXT : MenuPage.DIM);
                shown.add(rank + " | " + row.name() + " | " + character + " | " + total);
            } else {
                CompetitionHudState.PlacementRow row = line.row();
                int lapX = x + DETAIL_INDENT + com.openggf.mods.ui.CompactFont.width("00", 1) + 4;
                int bestIndex = row.lapFrames().indexOf(row.lapFrames().stream().min(Integer::compare).orElse(-1));
                List<String> laps = new ArrayList<>();
                for (int lap = 0; lap < row.lapFrames().size(); lap++) {
                    String time = LapTimeFormat.lap(row.lapFrames().get(lap));
                    HudTextLayout.compact(canvas, time, lapX, compactY, lap == bestIndex ? MenuPage.GOLD : MenuPage.MUTED);
                    lapX += timeColumn() + 6;
                    laps.add(time);
                }
                shown.add("  " + String.join(" ", laps));
            }
            y += NameFont.LINE_HEIGHT;
        }
        MenuPage.scrollbar(canvas, width - MenuPage.MARGIN - 2, MenuPage.BODY_TOP + 1,
                resultsVisible * NameFont.LINE_HEIGHT, resultsScroll, resultsVisible, resultsTotal);
        resultsShown = List.copyOf(shown);
    }

    /** The shown Room details lines as drawn: a fact's label then its text (tests read the whole page). */
    List<String> detailsVisibleLines() { return detailsShown; }

    int detailsFirstLine() { return detailsScroll == Integer.MAX_VALUE ? 0 : detailsScroll; }

}
