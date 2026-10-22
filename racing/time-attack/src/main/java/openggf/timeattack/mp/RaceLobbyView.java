package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.NameFont;
import com.openggf.mods.ui.TextLayout;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import openggf.timeattack.ProfileView;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.TextEntry;
import openggf.timeattack.ui.ViewInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The race lobby between rounds, drawn as a Time Attack scene view: the room's players (names in
 * each player's colour, in any script), chat history (with the LAN invite for a LAN host), the
 * host's START ROUND, chat entry, PROFILE (rename and change look, announced to the room while
 * it is in the lobby), track votes (keys 1-3) and LEAVE ROOM. The network pump and the round launch belong to
 * {@link RaceSession#poll()}, which runs every tick before this view, so an open chat editor or
 * details page can never delay a round start.
 *
 * <p>Scenes have no clipboard, so the LAN invite is shown in full on its details page (and saved
 * to the mod's storage by the session) for the host to read out or copy.
 */
public final class RaceLobbyView {
    private static final int BACKGROUND = 0x101830;
    private static final int TITLE = 0xFFFFE070;
    private static final int LABEL = 0xFFB0C8E8;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int DIM = 0xFF8C8C8C;
    private static final int VERIFIED = 0xFF99CCFF;
    private static final int UNVERIFIED = 0xFFFFCC4C;
    private static final int FOCUS = 0x803060C0;
    private static final int HINT = 0xFF8090B0;
    private static final int ACTION_HEIGHT = 13;

    private enum Focus { PLAYERS, HISTORY, START, CHAT, PROFILE, LEAVE }

    private final MultiplayerRaceCoordinator coordinator;
    private final boolean host;
    private final ControlMessage.RoundConfig configuredRound;
    private final String shareCode;
    private final Runnable leaveHandler;
    private final Consumer<MenuCue> cues;
    private final Supplier<ProfileView> profiles;
    private ProfileView profile;
    private Focus focus;
    private int playerPage;
    private int chatPage;
    private TextEntry editor;
    private boolean details;
    private String notice;

    /**
     * @param shareCode the LAN invite template a LAN host shares, or null
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
        this.shareCode = shareCode;
        this.leaveHandler = Objects.requireNonNull(leaveHandler, "leaveHandler");
        this.cues = Objects.requireNonNull(cues, "cues");
        this.focus = host ? Focus.START : Focus.CHAT;
    }

    public void update(ViewInput input) {
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
                Math.max(0, (coordinator.session().players().size() - 1) / 2));
        if (focus == Focus.HISTORY) chatPage = Math.clamp(chatPage + horizontal, 0,
                Math.max(0, (coordinator.session().chatLines().size() - 1) / 2));
        if (focus != beforeFocus || playerPage != beforePlayers || chatPage != beforeChat) {
            cues.accept(MenuCue.NAVIGATE);
        }
        if (input.accept()) {
            switch (focus) {
                case CHAT -> {
                    editor = TextEntry.text("ROOM CHAT", "", Protocol.MAX_CHAT_CHARS);
                    cues.accept(MenuCue.CONFIRM);
                }
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
                    if (canStart()) {
                        coordinator.sendRoundConfigure(configuredRound);
                        cues.accept(MenuCue.CONFIRM);
                    } else {
                        cues.accept(MenuCue.ERROR);
                    }
                }
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

    private List<Focus> choices() {
        List<Focus> choices = new ArrayList<>(List.of(Focus.PLAYERS, Focus.HISTORY));
        if (host) choices.add(Focus.START);
        choices.add(Focus.CHAT);
        if (profiles != null) choices.add(Focus.PROFILE);
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

    /** True while the invite and history page is open (tests). */
    boolean isShowingDetails() {
        return details;
    }

    public void draw(SceneCanvas canvas) {
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
        int width = canvas.width();
        canvas.clear(BACKGROUND);
        ControlMessage.RoomDescriptor room = coordinator.session().room();
        canvas.text("RACE LOBBY", 10, 10, TITLE);
        if (room == null) {
            canvas.text("Connecting...", 10, 24, HINT);
        } else {
            NameFont.draw(canvas, NameFont.fit(room.name(), width - 20), 10, 22, HINT);
        }
        List<ControlMessage.PlayerInfo> players = coordinator.session().players();
        playerPage = Math.min(playerPage, Math.max(0, (players.size() - 1) / 2));
        if (focus == Focus.PLAYERS) canvas.fill(8, 37, width - 16, 59, FOCUS);
        canvas.text("PLAYERS  < " + (playerPage + 1) + "/" + Math.max(1, (players.size() + 1) / 2) + " >",
                12, 40, LABEL);
        for (int i = playerPage * 2; i < Math.min(players.size(), playerPage * 2 + 2); i++) {
            ControlMessage.PlayerInfo player = players.get(i);
            int y = 51 + (i % 2) * 22;
            String character = player.character() == null ? "?" : player.character().toUpperCase();
            int characterX = width - 12 - canvas.textWidth(character);
            NameFont.draw(canvas, NameFont.fit(player.displayName(), characterX - 20), 12, y,
                    nameColour(player.slot()));
            canvas.text(character, characterX, y + 1, VALUE);
            String fingerprint = player.fingerprint() == null ? "????"
                    : player.fingerprint().substring(0, Math.min(4, player.fingerprint().length()));
            String badge = i == 0 ? " HOST" : player.newPlayer() ? " NEW" : "";
            // Below the name's descenders (row 12 of its line box), so marks like the dot in ị stay visible.
            canvas.text("#" + fingerprint + badge, 20, y + 12, DIM);
        }
        drawHistoryOrVote(canvas, width);
        boolean verified = room != null && room.verified();
        if (notice != null) {
            canvas.text(fit(canvas, notice, width - 20), 10, 140, TITLE);
        } else {
            canvas.text(fit(canvas, (verified ? "VERIFIED" : "UNVERIFIED TIMES") + " / "
                    + coordinator.session().phase(), width - 20), 10, 140, verified ? VERIFIED : UNVERIFIED);
        }
        int y = 153;
        for (Focus item : choices()) {
            switch (item) {
                case START -> action(canvas, item, canStart() ? "START ROUND" : "WAITING FOR ROUND", y, canStart());
                case CHAT -> action(canvas, item, "WRITE CHAT MESSAGE", y, true);
                case PROFILE -> action(canvas, item, "PROFILE: NAME AND LOOK", y, canEditProfile());
                case LEAVE -> action(canvas, item, "LEAVE ROOM", y, true);
                case PLAYERS, HISTORY -> { continue; }
            }
            y += ACTION_HEIGHT;
        }
        canvas.text(fit(canvas, "Up/Down select  Left/Right page  B leave", width - 20), 10, 210, HINT);
    }

    /** The colour a player chose for their name ({@link com.openggf.net.identity.PlayerAppearance#nameArgb}). */
    private int nameColour(int slot) {
        return coordinator.session().appearance(slot).nameArgb();
    }

    private void drawHistoryOrVote(SceneCanvas canvas, int width) {
        if (coordinator.session().phase() == ClientRaceSession.Phase.VOTE) {
            List<String> lines = HudTextLayout.voteLines(coordinator.session().voteOptions(),
                    coordinator.session().voteCounts(), coordinator.session().voteRemainingMillis(),
                    MultiplayerHudRenderer::trackLabel);
            int y = 98;
            for (String line : lines.subList(0, Math.min(4, lines.size()))) {
                canvas.text(fit(canvas, line, width - 24), 12, y, TITLE);
                y += 10;
            }
            return;
        }
        List<String> history = coordinator.session().chatLines();
        chatPage = Math.min(chatPage, Math.max(0, (history.size() - 1) / 2));
        if (focus == Focus.HISTORY) canvas.fill(8, 97, width - 16, 40, FOCUS);
        canvas.text((shareCode == null ? "CHAT HISTORY" : "LAN INVITE / CHAT") + "  < " + (chatPage + 1) + " >",
                12, 100, LABEL);
        int last = Math.max(0, history.size() - chatPage * 2);
        int first = Math.max(0, last - 2);
        for (int i = first; i < last; i++) {
            chatLine(canvas, history.get(i), 12, 111 + (i - first) * NameFont.LINE_HEIGHT, width - 24);
        }
        if (history.isEmpty()) canvas.text("No messages yet", 12, 112, DIM);
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

    private void drawDetails(SceneCanvas canvas) {
        int width = canvas.width();
        canvas.clear(BACKGROUND);
        int perLine = Math.max(1, (width - 24) / 10);
        int y = 10;
        List<String> lines = new ArrayList<>();
        if (shareCode != null) {
            canvas.text("LAN INVITE", 10, y, TITLE);
            y += 16;
            lines.add("Replace HOST_IP with this");
            lines.add("computer's LAN address:");
            lines.addAll(TextEntry.wrap(shareCode, perLine));
            lines.add("(also in lan-invite.txt in the");
            lines.add("Time Attack mod's storage)");
        } else {
            canvas.text("CHAT HISTORY", 10, y, TITLE);
            y += 16;
        }
        List<String> history = coordinator.session().chatLines();
        if (history.isEmpty() && shareCode == null) {
            lines.add("No messages yet");
        }
        for (String line : lines) {
            if (y > 190) break;
            canvas.text(line, 12, y, VALUE);
            y += 11;
        }
        if (!history.isEmpty()) y += 4;
        for (String message : history) {
            for (String line : TextEntry.wrapPixels(message, width - 24)) {
                if (y > 192) break;
                chatLine(canvas, line, 12, y, width - 24);
                y += NameFont.LINE_HEIGHT;
            }
        }
        canvas.text("Enter or B to return", 10, 208, HINT);
    }

    private void action(SceneCanvas canvas, Focus item, String label, int y, boolean enabled) {
        if (focus == item) canvas.fill(8, y - 2, canvas.width() - 16, ACTION_HEIGHT, FOCUS);
        canvas.text(label, 12, y, enabled ? VALUE : DIM);
    }

    private static String fit(SceneCanvas canvas, String text, int maxWidth) {
        return TextLayout.ellipsis(text == null ? "" : text, Math.max(0, maxWidth), canvas::textWidth);
    }
}
