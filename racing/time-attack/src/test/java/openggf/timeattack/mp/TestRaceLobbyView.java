package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneKeys;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.client.RaceClient;
import com.openggf.net.protocol.ControlMessage;
import openggf.timeattack.ui.FakeViewInput;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The race lobby scene view: host start, chat, private invite actions, votes and leave. */
class TestRaceLobbyView {
    private static final ControlMessage.RoundConfig ROUND =
            new ControlMessage.RoundConfig("s2", 0, 0, 300, "OPEN", null);

    private final List<ControlMessage> sent = new ArrayList<>();
    private final AtomicInteger leaves = new AtomicInteger();
    private final List<MenuCue> cues = new ArrayList<>();
    private final FakeViewInput input = new FakeViewInput();

    /** Admit before any roster/messages are injected: applyJoin clears the previous room's history. */
    private static ClientRaceSession joinedSession() {
        return joinedSession(0);
    }

    private static ClientRaceSession joinedSession(int slot) {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        session.applyJoin(new ControlMessage.JoinAccepted("t", slot,
                new ControlMessage.RoomDescriptor("Room", "s2", 0, 0, "OPEN", null, 8, false, "FULL_CHAT"),
                new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of())));
        return session;
    }

    private RaceLobbyView view(ClientRaceSession session, boolean host, String shareCode) {
        RaceTransport transport = new RaceTransport() {
            @Override public List<RaceClient.InboundEvent> drainInbound() { return List.of(); }
            @Override public void sendControl(ControlMessage message) { sent.add(message); }
            @Override public void sendBinary(byte[] bytes) { }
            @Override public int playerSlot() { return 0; }
            @Override public boolean isOpen() { return true; }
            @Override public void close() { }
        };
        return new RaceLobbyView(new MultiplayerRaceCoordinator(transport, session), host, ROUND, shareCode,
                leaves::incrementAndGet, cues::add);
    }

    private RaceLobbyView viewWithProfile(ClientRaceSession session, openggf.timeattack.TimeAttackSettings settings) {
        return viewWithProfile(session, settings, false);
    }

    private RaceLobbyView viewWithProfile(ClientRaceSession session, openggf.timeattack.TimeAttackSettings settings,
                                          boolean host) {
        RaceTransport transport = new RaceTransport() {
            @Override public List<RaceClient.InboundEvent> drainInbound() { return List.of(); }
            @Override public void sendControl(ControlMessage message) { sent.add(message); }
            @Override public void sendBinary(byte[] bytes) { }
            @Override public int playerSlot() { return 0; }
            @Override public boolean isOpen() { return true; }
            @Override public void close() { }
        };
        return new RaceLobbyView(new MultiplayerRaceCoordinator(transport, session), host, ROUND, null,
                leaves::incrementAndGet, cues::add,
                () -> new openggf.timeattack.ProfileView(settings, "s2", "tails", () -> { }, cues::add));
    }

    @Test
    void controllerActivationUsesTheRadialChooserAndAimedTriggerSends() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.ChatModeState("QUICK_CHAT"));
        RaceLobbyView lobby = view(session, false, null);
        input.tapDown(lobby::update);
        input.clear();
        input.accept = true;
        input.physical = controllerFrame(true, 0, 0, -1);
        lobby.update(input);
        assertTrue(lobby.isShowingRadialQuickChat());
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(256);
        lobby.draw(canvas);
        canvas.assertFillsOnScreen();
        input.accept = false;
        input.physical = controllerFrame(false, 1, 0, -1);
        lobby.update(input);
        input.physical = controllerFrame(false, 1, 0, 1);
        lobby.update(input);
        assertFalse(lobby.isShowingQuickChat());
        assertEquals(List.of(new ControlMessage.QuickChat("KNOW_THE_WAY")), sent);
    }

    @Test
    void keyboardActivationKeepsTheListEvenWhenAPadIsConnected() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.ChatModeState("QUICK_CHAT"));
        RaceLobbyView lobby = view(session, false, null);
        input.tapDown(lobby::update);
        input.clear();
        input.accept = true;
        input.pressed.add(SceneKeys.ENTER);
        input.physical = controllerFrame(false, 0, 0, -1);
        lobby.update(input);
        assertTrue(lobby.isShowingQuickChat());
        assertFalse(lobby.isShowingRadialQuickChat());
        input.tapKey(lobby::update, SceneKeys.DIGIT_8);
        assertEquals(List.of(new ControlMessage.QuickChat("WELL_PLAYED")), sent);
    }

    private static com.openggf.control.PhysicalInput controllerFrame(boolean confirm, float x, float y, float rt) {
        boolean[] buttons = new boolean[15];
        buttons[com.openggf.control.PhysicalGamepad.BUTTON_X] = confirm;
        var pad = new com.openggf.control.PhysicalGamepad(0, "Xbox", buttons, new float[] {0, 0, x, y, -1, rt});
        var events = confirm ? List.of(new com.openggf.control.PhysicalInputEvent(1, 0,
                com.openggf.control.PhysicalInputEvent.Kind.BUTTON, 0,
                com.openggf.control.PhysicalGamepad.BUTTON_X, 1)) : List.<com.openggf.control.PhysicalInputEvent>of();
        return new com.openggf.control.PhysicalInput(0, List.of(), List.of(pad), events, 0);
    }

    @Test
    void profileRenamesAndRecoloursThroughTheRoomWhileInTheLobby() {
        openggf.timeattack.TimeAttackSettings settings = new openggf.timeattack.TimeAttackSettings();
        settings.setDisplayName("Ray");
        ClientRaceSession session = joinedSession();
        RaceLobbyView lobby = viewWithProfile(session, settings);
        input.tapDown(lobby::update); // guest: ready -> chat
        input.tapDown(lobby::update); // chat -> Quick Chat
        input.tapDown(lobby::update); // Quick Chat -> profile
        input.tapEnter(lobby::update);
        assertTrue(lobby.isShowingProfile());
        input.tapRight(lobby::update); // Classic -> Crimson
        for (int i = 0; i < 5; i++) input.tapDown(lobby::update); // SAVE
        input.tapEnter(lobby::update);
        assertFalse(lobby.isShowingProfile());
        var look = settings.appearanceFor("s2", "tails");
        assertEquals(0x022E, look.baseColour());
        assertEquals(List.of(new ControlMessage.UpdateProfile("Ray", look.encode())), sent);
    }

    @Test
    void profileWaitsForTheLobbyPhase() {
        ClientRaceSession session = joinedSession();
        RaceLobbyView lobby = viewWithProfile(session, new openggf.timeattack.TimeAttackSettings());
        session.onControl(new ControlMessage.RoundStart(ROUND, 1000, 10_000));
        input.tapDown(lobby::update);
        input.tapDown(lobby::update); // ready -> chat -> Quick Chat
        input.tapDown(lobby::update); // profile
        cues.clear();
        input.tapEnter(lobby::update);
        assertFalse(lobby.isShowingProfile());
        assertEquals(List.of(MenuCue.ERROR), cues);
        assertTrue(sent.isEmpty());
    }

    @Test
    void internationalNamesAreDrawnInEachPlayersColour() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "abcdef", "ブラック・ノヴァ", "sonic"),
                new ControlMessage.PlayerInfo(1, "123456", "Nguyễn Thị Hoa", "tails", true))));
        session.onControl(new ControlMessage.PlayerLook(1, "0E6.20E6.30C4"));
        session.onControl(new ControlMessage.ChatBroadcast(1, "Nguyễn Thị Hoa", "xin chào"));
        int guest = session.appearance(1).nameArgb();
        for (int width : new int[] {320, 400}) {
            RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
            view(session, true, null).draw(canvas);
            canvas.assertFillsOnScreen();
            assertTrue(canvas.drewName("ブラック・ノヴァ", 19, com.openggf.net.identity.PlayerAppearance.STOCK.nameArgb()));
            assertTrue(canvas.drewName("Nguyễn Thị Hoa", 19, guest), "the guest's name in its colour");
            int[] runs = {0};
            com.openggf.mods.ui.NameFont.glyphs("Nguyễn Thị Hoa", 0, 0, (x, y, w, h) -> runs[0]++);
            // The chat sender, fitted to a third of the row, is drawn in the same colour as the player row.
            assertTrue(canvas.fillsIn(guest).size() > runs[0], "the chat sender is coloured too");
            String page = view(session, true, null).screen(width, 224).plain();
            assertTrue(page.contains("[ ] Nguyễn Thị Hoa NEW TAILS"), page);
            assertTrue(page.contains("Nguyễn Thị Hoa: xin chào"), page);
        }
    }

    @Test
    void theDetailsPageListsEachPlayersIdentityTag() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "abcdef", "Sid", "sonic"),
                new ControlMessage.PlayerInfo(1, "123456", "Sid", "tails", true))));
        RaceLobbyView lobby = view(session, true, null);
        input.tapUp(lobby::update); // Invite/history
        input.tapEnter(lobby::update);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        lobby.draw(canvas);
        assertTrue(canvas.shows("PLAYERS"), canvas.joined());
        int stock = com.openggf.net.identity.PlayerAppearance.STOCK.nameArgb();
        assertTrue(canvas.drewName("Sid", 14, stock), "two players with one name, told apart by their tags");
        canvas.assertFillsOnScreen();
    }

    @Test
    void chatTakesAnyScript() {
        RaceLobbyView lobby = view(joinedSession(), false, null);
        input.tapDown(lobby::update); // ready -> chat
        input.tapEnter(lobby::update);
        input.commit(lobby::update, "Привет, 你好!");
        input.tapKey(lobby::update, SceneKeys.ENTER);
        assertTrue(sent.contains(new ControlMessage.Chat("Привет, 你好!")), sent.toString());
    }

    @Test
    void explicitHostStartAndLeaveActionsRemainReachable() {
        RaceLobbyView lobby = view(joinedSession(), true, null);
        input.tapDown(lobby::update); // Ready -> Start
        input.tapEnter(lobby::update);
        assertTrue(sent.contains(new ControlMessage.RoundConfigure(ROUND)));
        input.tapDown(lobby::update); // Ready rule
        input.tapDown(lobby::update); // Chat
        input.tapDown(lobby::update); // Quick Chat
        input.tapDown(lobby::update); // Room mode
        input.tapDown(lobby::update); // Leave
        input.tapEnter(lobby::update);
        assertEquals(1, leaves.get());
    }

    @Test
    void cancellingChatReturnsToLobbyWithoutLeavingOrStarting() {
        RaceLobbyView lobby = view(joinedSession(), true, null);
        for (int i = 0; i < 3; i++) input.tapDown(lobby::update); // ready -> start -> rule -> chat
        input.tapEnter(lobby::update);
        assertTrue(lobby.isEditingChat());
        input.tapEscape(lobby::update);
        assertEquals(0, leaves.get());
        assertFalse(sent.stream().anyMatch(ControlMessage.RoundConfigure.class::isInstance));
        input.tapEscape(lobby::update);
        assertEquals(1, leaves.get());
    }

    @Test
    void chatIsTypedAndSentOnEnter() {
        RaceLobbyView lobby = view(joinedSession(), false, null);
        input.tapDown(lobby::update); // guest: Ready -> Chat
        input.tapEnter(lobby::update);
        input.type(lobby::update, "Hi all!");
        input.tapKey(lobby::update, SceneKeys.ENTER);
        assertTrue(sent.contains(new ControlMessage.Chat("Hi all!")), sent.toString());
        assertFalse(lobby.isEditingChat());
    }

    @Test
    void historyDetailsReturnWithoutLeaving() {
        RaceLobbyView lobby = view(joinedSession(), true, null);
        input.tapUp(lobby::update); // History
        input.tapEnter(lobby::update);
        assertTrue(lobby.isShowingDetails());
        input.tapEscape(lobby::update);
        assertFalse(lobby.isShowingDetails());
        assertEquals(0, leaves.get());
        input.tapEscape(lobby::update);
        assertEquals(1, leaves.get());
    }

    @Test
    void chatCardNamesATabShortcutThatWorksBeforeAndAfterReadying() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "abcdef", "Owner", "sonic"))));
        RaceLobbyView lobby = view(session, true, null);
        lobby.setLevelBehind(true);
        input.tapUp(lobby::update); // History
        for (boolean ready : new boolean[] {false, true}) {
            session.onControl(new ControlMessage.PlayerReady(0, ready));
            for (int width : new int[] {320, 400}) {
                assertTrue(lobby.screen(width, 224).plain().contains("TAB: DETAILS"),
                        "keyboard hint must name the direct shortcut at " + width);
            }
            input.tapKey(lobby::update, SceneKeys.TAB);
            assertTrue(lobby.isShowingDetails());
            assertTrue(session.isLocalReady() == ready, "reading never changes ready state");
            input.tapEscape(lobby::update);
            assertFalse(lobby.isShowingDetails());
        }
        assertEquals(0, leaves.get());
    }

    /**
     * A crowded room's details page scrolls: every player and every message is reachable and whole
     * (wrapped at spaces, continuations indented), it opens at the newest message's first line, and
     * Up reaches the players at the top.
     */
    @Test
    void crowdedRoomDetailsScrollToEveryPlayerAndEveryMessageWhole() {
        ClientRaceSession session = joinedSession();
        String[] names = {"Tails", "Knuckles the Echidna of Angel Island", "Cream the Rabbit and Cheese Chao",
                "Silver from two hundred years ahead", "Blaze the Cat of the Sol Dimension",
                "Espio the Chameleon of Team Chaotix", "Amy", "Sid"};
        List<ControlMessage.PlayerInfo> players = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) players.add(new ControlMessage.PlayerInfo(i, "abcd" + i + "f", names[i], "sonic"));
        session.onControl(new ControlMessage.RoomState(players));
        List<String> messages = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            String text = "Hi from " + names[i] + "! Ready in a moment, just picking a character.";
            session.onControl(new ControlMessage.ChatBroadcast(i, names[i], text));
            messages.add(names[i] + ": " + text);
        }
        RaceLobbyView lobby = view(session, true, null);
        input.tapUp(lobby::update); // History
        for (int width : new int[] {320, 400}) {
            input.tapEnter(lobby::update);
            assertTrue(lobby.isShowingDetails());
            var all = lobby.detailLines(width);
            // Every name and message is whole: its first line and continuations rejoin it exactly.
            List<String> rejoined = new java.util.ArrayList<>();
            for (var line : all) {
                switch (line.kind()) {
                    case PLAYER, CHAT -> rejoined.add(line.text());
                    case NAME_CONTINUATION, CHAT_CONTINUATION -> rejoined.set(rejoined.size() - 1, rejoined.getLast() + " " + line.text());
                    default -> { }
                }
            }
            for (String name : names) assertTrue(rejoined.contains(name), name + " whole at " + width + ": " + rejoined);
            for (String message : messages) assertTrue(rejoined.contains(message), message + " whole at " + width);
            var opened = lobby.shownDetailLines(width);
            assertTrue(opened.size() < all.size(), "the crowded fixture needs scrolling at " + width);
            assertEquals(RaceLobbyView.DetailLine.Kind.CHAT, opened.getFirst().kind(), "opens on a message's first line");
            assertEquals(all.getLast(), opened.getLast(), "the newest message is in view, to its last line");
            for (int i = 0; i < all.size(); i++) input.tapUp(lobby::update);
            assertEquals("Room", lobby.shownDetailLines(width).getFirst().text(), "Up reaches the room's facts above the players");
            for (int i = 0; i < all.size(); i++) input.tapDown(lobby::update);
            assertEquals(all.getLast(), lobby.shownDetailLines(width).getLast(), "Down reaches the newest line");
            RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
            lobby.draw(canvas);
            canvas.assertFillsOnScreen();
            input.tapEscape(lobby::update);
            assertFalse(lobby.isShowingDetails());
        }
        assertEquals(0, leaves.get(), "reading the details never leaves the room");
    }

    @Test
    void historyDoesNotDiscloseTheLegacyManualInvite() {
        String invite = "HOST_IP:27888#" + "Ab-_".repeat(21) + "AB";
        RaceLobbyView lobby = view(joinedSession(), true, invite);
        input.tapUp(lobby::update); // Invite/history
        input.tapEnter(lobby::update);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        lobby.draw(canvas);
        String joined = String.join("", canvas.lines);
        assertFalse(joined.contains(invite), canvas.joined());
        assertFalse(joined.contains("HOST_IP"), canvas.joined());
        assertFalse(joined.contains("lan-invite.txt"), canvas.joined());
    }

    @Test
    void lobbyNoOpPagesStaySilentWhileStartChatAndNestedBackReportActions() {
        RaceLobbyView lobby = view(joinedSession(), true, null);
        input.tapUp(lobby::update);
        input.tapUp(lobby::update); // Players
        cues.clear();
        input.tapLeft(lobby::update);
        input.tapRight(lobby::update);
        input.tapEnter(lobby::update);
        assertTrue(cues.isEmpty(), cues.toString());
        for (int i = 0; i < 3; i++) input.tapDown(lobby::update); // History, Ready, Start
        input.tapEnter(lobby::update);
        assertTrue(sent.contains(new ControlMessage.RoundConfigure(ROUND)));
        input.tapDown(lobby::update); // Ready rule
        input.tapDown(lobby::update);
        input.tapEnter(lobby::update); // Chat editor
        input.tapEscape(lobby::update);
        assertEquals(0, leaves.get());
        input.tapEscape(lobby::update);
        assertEquals(1, leaves.get());
        assertEquals(List.of(MenuCue.NAVIGATE, MenuCue.NAVIGATE, MenuCue.NAVIGATE, MenuCue.CONFIRM,
                MenuCue.NAVIGATE, MenuCue.NAVIGATE, MenuCue.CONFIRM, MenuCue.CANCEL, MenuCue.CANCEL), cues);
    }

    @Test
    void hostStartOutsideTheLobbyIsAnError() {
        ClientRaceSession session = joinedSession();
        RaceLobbyView lobby = view(session, true, null);
        session.onControl(new ControlMessage.RoundStart(ROUND, 1000, 10_000));
        input.tapDown(lobby::update); // Ready -> Start
        input.tapEnter(lobby::update);
        assertFalse(sent.stream().anyMatch(ControlMessage.RoundConfigure.class::isInstance));
        assertEquals(List.of(MenuCue.NAVIGATE, MenuCue.ERROR), cues);
    }

    @Test
    void voteKeysCastTheOfferedTrackOnlyDuringAVote() {
        ClientRaceSession session = joinedSession();
        RaceLobbyView lobby = view(session, false, null);
        input.tapKey(lobby::update, SceneKeys.DIGIT_2);
        assertTrue(sent.isEmpty());
        session.onControl(new ControlMessage.TrackVoteOffer(List.of("s2:0:0", "s2:1:0"), 60_000));
        input.tapKey(lobby::update, SceneKeys.DIGIT_2);
        assertEquals(List.of(new ControlMessage.TrackVote("s2:1:0")), sent);
        input.tapKey(lobby::update, SceneKeys.DIGIT_3); // no third option
        assertEquals(1, sent.size());
        String page = lobby.screen(320, 224).plain();
        assertTrue(page.contains("2 CHEMICAL PLANT 1"), page);
        assertTrue(page.contains("NEXT TRACK"), page);
    }

    @Test
    void everyOfferedTrackIsShownWithItsVotes() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.TrackVoteOffer(List.of("s2:0:0", "s2:1:0", "s2:2:0"), 60_000));
        session.onControl(new ControlMessage.TrackVoteTally(List.of(new ControlMessage.VoteCount("s2:1:0", 3))));
        String page = view(session, false, null).screen(320, 224).plain();
        assertTrue(page.contains("| 1 "), page);
        assertTrue(page.contains("2 CHEMICAL PLANT 1 3"), page);
        assertTrue(page.contains("| 3 "), "all three options, not just two: " + page);
    }

    @Test
    void playersAndLongNamesStayOnScreenAtEveryWidth() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "abcdef", "Host" + "x".repeat(40), "sonic"),
                new ControlMessage.PlayerInfo(1, "123456", "Guest", "tails", true))));
        session.onControl(new ControlMessage.ChatBroadcast(1, "Guest", "hello " + "y".repeat(80)));
        for (int width : new int[] {320, 400}) {
            RaceLobbyView lobby = view(session, true, null);
            RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
            lobby.draw(canvas);
            canvas.assertFillsOnScreen();
            String page = lobby.screen(width, 224).plain();
            assertTrue(page.contains("Guest NEW TAILS"), page);
        }
    }

    @Test
    void readyUpIsTheFirstActionAndTogglesThroughTheRoom() {
        ClientRaceSession session = joinedSession(1);
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(1, "123456", "Guest", "tails"))));
        RaceLobbyView lobby = view(session, false, null);
        input.tapEnter(lobby::update);
        assertEquals(List.of(new ControlMessage.SetReady(true)), sent);
        session.onControl(new ControlMessage.PlayerReady(1, true));
        input.tapEnter(lobby::update);
        assertEquals(new ControlMessage.SetReady(false), sent.getLast());
        assertTrue(lobby.isOnMainPage());
    }

    @Test
    void theOwnerStepsTheReadyRuleAndItsStartWaitsForEveryone() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "abcdef", "Owner", "sonic"),
                new ControlMessage.PlayerInfo(1, "123456", "Sid", "tails"))));
        session.onControl(new ControlMessage.PlayerReady(0, true));
        RaceLobbyView lobby = view(session, true, null);
        List<com.openggf.net.protocol.ReadyRules> chosen = new ArrayList<>();
        lobby.setReadyRuleListener(chosen::add);
        input.tapDown(lobby::update); // Start
        input.tapEnter(lobby::update);
        assertFalse(sent.stream().anyMatch(ControlMessage.RoundConfigure.class::isInstance),
                "the default rule waits for Sid");
        String page = lobby.screen(320, 224).plain();
        assertTrue(page.contains("| Waiting for Sid |"), page);
        assertFalse(page.contains("WAITING FOR"), "the ready boxes already name who is ready: " + page);
        assertTrue(page.contains("[x] Owner"), "a ready box for the owner: " + page);
        assertTrue(page.contains("[ ] Sid"), page);

        input.tapDown(lobby::update); // Ready rule
        input.tapRight(lobby::update);
        assertEquals(new ControlMessage.SetReadyRule("REQUIRED"), sent.getLast());
        session.onControl(new ControlMessage.ReadyRule("REQUIRED", true)); // the host's answer
        input.tapEnter(lobby::update);
        assertEquals(List.of(com.openggf.net.protocol.ReadyRules.REQUIRED,
                com.openggf.net.protocol.ReadyRules.AUTO), chosen,
                "the menu steps from the room's rule; the host answers with ReadyRule");
        session.onControl(new ControlMessage.ReadyRule("AUTO", false));
        input.tapUp(lobby::update); // Start
        input.tapEnter(lobby::update);
        assertEquals(new ControlMessage.RoundConfigure(ROUND), sent.getLast(), "nobody waits under AUTO");
    }

    @Test
    void hostNoticesAndActionsReadWholeInTheRowFaceAt320And400() {
        String notice = "No short code: share the LAN invite.";
        for (int width : new int[] {320, 400}) {
            ClientRaceSession session = joinedSession();
            session.onControl(new ControlMessage.RoomState(List.of(
                    new ControlMessage.PlayerInfo(0, "abcdef", "Owner", "sonic"),
                    new ControlMessage.PlayerInfo(1, "123456", "Sid", "tails"),
                    new ControlMessage.PlayerInfo(2, "654321", "Amy", "sonic"))));
            RaceLobbyView lobby = view(session, true, null);
            lobby.showStatus(notice);
            RaceLobbyScreen.Screen screen = lobby.screen(width, 224);
            RaceLobbyScreen.Card menu = screen.cards().getLast();
            List<RaceLobbyScreen.Note> notes = menu.rows().stream().filter(RaceLobbyScreen.Note.class::isInstance)
                    .map(RaceLobbyScreen.Note.class::cast).toList();
            assertEquals("Waiting: you + 2", notes.getFirst().text(), "include the local player explicitly");
            assertTrue(notes.stream().allMatch(RaceLobbyScreen.Note::names), "status sentences use the row face");
            String joined = String.join(" ", notes.subList(1, notes.size()).stream().map(RaceLobbyScreen.Note::text).toList());
            assertEquals(notice, joined, "the notice is drawn whole at " + width);
            assertTrue(notes.size() - 1 <= 2, "two name-font lines at most: " + notes);
            int labelRoom = menu.width() - 2 * RaceLobbyScreen.INSET - 8;
            int used = RaceLobbyScreen.TITLE_HEIGHT + 2 + 3;
            for (RaceLobbyScreen.Row row : menu.rows()) {
                used += row.height();
                if (row instanceof RaceLobbyScreen.Action action)
                    assertTrue(com.openggf.mods.ui.NameFont.width(action.label()) <= labelRoom,
                            action.label() + " fits without an ellipsis at " + width);
            }
            assertTrue(menu.y() + used <= RaceLobbyScreen.columnBottom(224), "rows stay inside the card at " + width);
            assertTrue(screen.plain().contains("Ready rule < By size >"), screen.plain());
            assertTrue(screen.plain().contains("Room chat < Full chat >"), screen.plain());
            RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
            lobby.draw(canvas);
            canvas.assertFillsOnScreen();
        }
    }

    @Test
    void readinessNamesYouAndPrioritisesEveryoneReadyOverOptional() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "abcdef", "Owner", "sonic"),
                new ControlMessage.PlayerInfo(1, "123456", "Sid", "tails"))));
        RaceLobbyView lobby = view(session, true, null);
        assertTrue(lobby.screen(320, 224).plain().contains("Waiting: you + 1"));
        session.onControl(new ControlMessage.PlayerReady(1, true));
        assertTrue(lobby.screen(320, 224).plain().contains("Waiting for you"));
        session.onControl(new ControlMessage.ReadyRule("AUTO", false));
        session.onControl(new ControlMessage.PlayerReady(0, true));
        assertTrue(lobby.screen(320, 224).plain().contains("Everyone is ready"));
    }

    @Test
    void guestsSeeTheRuleButCannotChangeIt() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.ReadyRule("AUTO", false));
        RaceLobbyView lobby = view(session, false, null);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        lobby.draw(canvas);
        canvas.assertFillsOnScreen();
        String page = lobby.screen(320, 224).plain();
        assertTrue(page.contains("READY AUTO"), page);
        assertTrue(page.contains("| Ready is optional |"), page);
        assertFalse(page.contains("Ready rule <"), "the rule action is the owner's: " + page);
    }

    @Test
    void overTheLobbyLevelOnlyTheCardsAreDrawnSoTheLevelStaysInView() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "abcdef", "Owner", "sonic"))));
        RaceLobbyView lobby = view(session, true, null);
        RecordingSceneCanvas level = new RecordingSceneCanvas(320);
        lobby.draw(new openggf.timeattack.ui.OverLevelCanvas(level));
        long[] covered = new long[1];
        for (int[] fill : level.fills) {
            assertFalse(fill[2] >= 320 && fill[3] >= 224, "no full-screen veil over the level");
            if ((fill[4] >>> 24) == (RaceLobbyScreen.CARD_FILL >>> 24) && fill[4] == RaceLobbyScreen.CARD_FILL) {
                covered[0] += (long) fill[2] * fill[3];
            }
        }
        assertTrue(covered[0] > 0, "the cards have their dark backing");
        assertTrue(covered[0] < 320L * 224 * 3 / 4, "the level shows around the cards: " + covered[0]);
        level.assertFillsOnScreen();

        RecordingSceneCanvas menu = new RecordingSceneCanvas(320);
        menu.fill(0, 0, 1, 1, 0xFF123456);
        lobby.draw(menu);
        assertFalse(menu.fills.stream().anyMatch(fill -> fill[4] == 0xFF123456 && fill[2] == 1),
                "without a level behind, the page clears the screen first");
    }
    @Test
    void hostsAndMembersHaveAnExplicitInviteAction() {
        for (boolean host : new boolean[] {false, true}) {
            RaceLobbyView lobby = view(joinedSession(), host, null);
            AtomicInteger opens = new AtomicInteger();
            lobby.setInviteAction(opens::incrementAndGet);
            input.tapKey(lobby::update, SceneKeys.I);
            assertEquals(1, opens.get());
            for (int width : new int[] {320, 400, 640}) {
                RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
                lobby.draw(canvas);
                canvas.assertFillsOnScreen();
                assertTrue(lobby.screen(width, 224).plain().contains("Room invite"));
            }
        }
    }

    @Test
    void fullOffersTypedAndQuickWhileQuickHasOnlyTheCannedChooser() {
        ClientRaceSession session = joinedSession();
        RaceLobbyView lobby = view(session, false, null);
        String full = lobby.screen(320, 224).plain();
        assertTrue(full.contains("  Chat"), full);
        assertTrue(full.contains("Quick chat"), full);
        session.onControl(new ControlMessage.ChatModeState("QUICK_CHAT"));
        String quick = lobby.screen(320, 224).plain();
        assertFalse(lobby.screen(320, 224).cards().stream().flatMap(card -> card.rows().stream())
                .anyMatch(row -> row instanceof RaceLobbyScreen.Action action && action.label().equals("Chat")), quick);
        assertTrue(quick.contains("Quick chat"), quick);
        input.tapDown(lobby::update); // Ready -> Quick Chat
        input.tapEnter(lobby::update);
        assertTrue(lobby.isShowingQuickChat());
        assertFalse(lobby.isEditingText());
        input.tapKey(lobby::update, SceneKeys.DIGIT_6);
        assertFalse(lobby.isShowingQuickChat());
        assertEquals(List.of(new ControlMessage.QuickChat("THIS_IS_ZONE")), sent);
    }

    @Test
    void modeChangesCloseOpenComposersAndOffKeepsRoomDetailsAndInvites() {
        ClientRaceSession session = joinedSession();
        RaceLobbyView lobby = view(session, false, "HOST_IP:27888#invite");
        AtomicInteger opens = new AtomicInteger();
        lobby.setInviteAction(opens::incrementAndGet);
        input.tapDown(lobby::update);
        input.tapEnter(lobby::update);
        assertTrue(lobby.isEditingChat());
        input.type(lobby::update, "unfinished");
        session.onControl(new ControlMessage.ChatModeState("QUICK_CHAT"));
        input.idle(lobby::update);
        assertFalse(lobby.isEditingChat());
        input.tapDown(lobby::update); // Ready -> Quick Chat after reconcile
        input.tapEnter(lobby::update);
        assertTrue(lobby.isShowingQuickChat());
        session.onControl(new ControlMessage.ChatModeState("OFF"));
        input.idle(lobby::update);
        assertFalse(lobby.isShowingQuickChat());
        assertFalse(lobby.screen(320, 224).plain().contains("Quick chat"));
        assertTrue(lobby.screen(320, 224).plain().contains("Room invite"));
        input.tapUp(lobby::update);
        input.tapEnter(lobby::update);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        lobby.draw(canvas);
        assertTrue(canvas.shows("Room chat is off"), canvas.joined());
        assertFalse(canvas.shows("HOST_IP:27888#invite"), canvas.joined());
        input.tapEscape(lobby::update);
        input.tapKey(lobby::update, SceneKeys.I);
        assertEquals(1, opens.get());
        assertTrue(sent.isEmpty());
    }

    @Test
    void personalDisableHidesBothKindsFromCardAndDetailsButStillAllowsSending() {
        ClientRaceSession session = joinedSession();
        RaceLobbyView lobby = view(session, false, "HOST_IP:27888#invite");
        AtomicInteger opens = new AtomicInteger();
        lobby.setInviteAction(opens::incrementAndGet);
        session.onControl(new ControlMessage.ChatBroadcast(1, "Player", "typed secret"));
        session.onControl(new ControlMessage.QuickChatBroadcast(1, "Player", "BRUTAL", "s2", 0, 0));
        String shown = lobby.screen(320, 224).plain();
        assertTrue(shown.contains("Player: typed secret"), shown);
        assertTrue(shown.contains("Player: Brutal!"), shown);
        input.tapUp(lobby::update);
        input.tapEnter(lobby::update);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        lobby.draw(canvas);
        assertTrue(canvas.drewName("Player: typed secret", 14, 0xFFFFFFFF), "typed chat is drawn in details");
        assertTrue(canvas.drewName("Player: Brutal!", 14, 0xFFFFFFFF), "quick chat is drawn in details");

        session.setChatPreferences(true, true);
        String hidden = lobby.screen(320, 224).plain();
        assertFalse(hidden.contains("typed secret"), hidden);
        assertFalse(hidden.contains("Brutal"), hidden);
        assertTrue(hidden.contains("Quick chat"), hidden);
        assertTrue(hidden.contains("Room invite"), hidden);
        lobby.draw(canvas);
        assertFalse(canvas.drewName("Player: typed secret", 14, 0xFFFFFFFF));
        assertFalse(canvas.drewName("Player: Brutal!", 14, 0xFFFFFFFF));
        assertTrue(canvas.shows("Chat hidden in Settings > Gameplay"), canvas.joined());
        assertFalse(canvas.shows("HOST_IP:27888#invite"), canvas.joined());
        input.tapEscape(lobby::update);
        input.tapKey(lobby::update, SceneKeys.I);
        assertEquals(1, opens.get());
        input.tapDown(lobby::update); // History -> Ready
        input.tapDown(lobby::update); // typed chat remains available
        input.tapEnter(lobby::update);
        input.commit(lobby::update, "hello");
        input.tapEnter(lobby::update);
        assertTrue(sent.contains(new ControlMessage.Chat("hello")));
    }

    @Test
    void ownerRequestsPersistButAuthoritativeModeRemainsVisibleUntilReply() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.ChatModeState("QUICK_CHAT"));
        RaceLobbyView lobby = view(session, true, null);
        List<com.openggf.net.protocol.ChatMode> saved = new ArrayList<>();
        lobby.setChatModeListener(saved::add);
        // Ready -> Start -> Rule -> Quick Chat -> room mode.
        for (int i = 0; i < 4; i++) input.tapDown(lobby::update);
        input.tapLeft(lobby::update); // request Full under a public Quick cap
        assertEquals(List.of(new ControlMessage.SetChatMode("FULL_CHAT")), sent);
        assertEquals(List.of(com.openggf.net.protocol.ChatMode.FULL_CHAT), saved);
        assertEquals(com.openggf.net.protocol.ChatMode.QUICK_CHAT, session.chatMode());
        // The host's stepper reads the authoritative mode, in the row's sentence case.
        assertTrue(lobby.screen(320, 224).plain().contains("Room chat < Quick chat >"), lobby.screen(320, 224).plain());
        session.onControl(new ControlMessage.RoundStart(ROUND, 1000, 10_000));
        input.tapRight(lobby::update);
        assertEquals(1, sent.size(), "mode actions cannot change an active round");
    }

    @Test
    void quickChooserPreviewFollowsTheLatestRoundContext() {
        ClientRaceSession session = joinedSession();
        RaceLobbyView lobby = view(session, false, null);
        input.tapDown(lobby::update); // Ready -> typed chat
        input.tapDown(lobby::update); // Quick Chat
        input.tapEnter(lobby::update);
        assertTrue(lobby.isShowingQuickChat());
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        int phraseX = 4 + HudTextLayout.TEXT_INSET + com.openggf.mods.ui.CompactFont.width("6 ", 1) + 1;
        lobby.draw(canvas);
        assertTrue(canvas.drewName("This is Emerald Hill!", phraseX, HudTextLayout.WHITE));

        ControlMessage.RoundConfig votedRound = new ControlMessage.RoundConfig("s2", 1, 0, 300, "OPEN", null);
        session.onControl(new ControlMessage.RoundStart(votedRound, 1000, 10_000));
        session.onControl(new ControlMessage.RoundEnd(List.of()));
        lobby.draw(canvas);
        String received = TimeAttackQuickChat.format(new com.openggf.net.chat.QuickChatContent(
                com.openggf.net.protocol.QuickChatPhrase.THIS_IS_ZONE, "s2", 1, 0));
        assertEquals("This is Chemical Plant!", received);
        assertTrue(canvas.drewName(received, phraseX, HudTextLayout.WHITE), "preview matches received context");
        assertFalse(canvas.drewName("This is Emerald Hill!", phraseX, HudTextLayout.WHITE));
        canvas.assertFillsOnScreen();
    }

    @Test
    void allOwnerActionsWithWaitingNamesAndNoticeStayOnScreen() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "abcdef", "Owner", "sonic"),
                new ControlMessage.PlayerInfo(1, "123456", "A waiting racer with a long name", "tails"))));
        RaceLobbyView lobby = viewWithProfile(session, new openggf.timeattack.TimeAttackSettings(), true);
        lobby.setInviteAction(() -> { });
        for (String notice : List.of("Waiting for the next round",
                "No short code: share the LAN invite.")) {
            lobby.showStatus(notice);
            for (int width : new int[] {320, 400}) {
                RaceLobbyScreen.Screen screen = lobby.screen(width, 224);
                assertEquals(9, screen.cards().stream().flatMap(card -> card.rows().stream())
                        .filter(RaceLobbyScreen.Action.class::isInstance).count());
                // Every card's rows stay inside the card, above the key plate.
                for (RaceLobbyScreen.Card card : screen.cards()) {
                    int rows = card.rows().stream().mapToInt(RaceLobbyScreen.Row::height).sum();
                    assertTrue(RaceLobbyScreen.TITLE_HEIGHT + 2 + rows <= card.height() + 3,
                            notice + " at " + width + ": " + card.title() + " rows " + rows + " in " + card.height());
                    assertTrue(card.y() + card.height() <= RaceLobbyScreen.columnBottom(224));
                }
                RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
                lobby.draw(canvas);
                canvas.assertFillsOnScreen();
            }
        }
    }

    @Test
    void fullHistoryReachesEveryLongUnicodeMessagePlayerAndNoticeWithoutActions() {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        String roomName = "部屋🚀".repeat(35) + "ROOM_TAIL";
        session.applyJoin(new ControlMessage.JoinAccepted("t", 0,
                new ControlMessage.RoomDescriptor(roomName, "s2", 0, 0, "OPEN", null, 8, false, "FULL_CHAT"),
                new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of())));
        List<ControlMessage.PlayerInfo> players = new ArrayList<>();
        for (int i = 0; i < 8; i++) players.add(new ControlMessage.PlayerInfo(i, "abcdef" + i,
                "Racer" + i + "日本語e\u0301🚀".repeat(12) + "PLAYER_TAIL_" + i, "tails"));
        session.onControl(new ControlMessage.RoomState(players));
        for (int i = 0; i < 24; i++) session.onControl(new ControlMessage.ChatBroadcast(i % 8,
                players.get(i % 8).displayName(), "Message" + i + "日本語e\u0301🚀".repeat(12) + "CHAT_TAIL_" + i));
        RaceLobbyView lobby = view(session, true, "HOST_IP:27888#SECRET_INVITE");
        lobby.showStatus("Long diagnostic " + "status ".repeat(70) + "STATUS_TAIL");
        RecordingSceneCanvas main = new RecordingSceneCanvas(320);
        lobby.draw(main);
        assertTrue(lobby.screen(320, 224).plain().contains("F1 Details"));
        input.tapKey(lobby::update, SceneKeys.F1);
        String read = openggf.timeattack.ui.FullTextReaderProbe.readAll(input, lobby::update, lobby::draw,
                lobby::detailsVisibleLines, lobby::detailsFirstLine);
        for (int i = 0; i < 8; i++) assertTrue(read.contains(openggf.timeattack.ui.FullTextReader.readable(players.get(i).displayName())), "player " + i);
        for (int i = 24 - com.openggf.net.client.ClientRaceSession.CHAT_LINES_KEPT; i < 24; i++) assertTrue(read.contains("CHAT_TAIL_" + i), "message " + i);
        assertTrue(read.contains(openggf.timeattack.ui.FullTextReader.readable(roomName)));
        assertTrue(read.contains("STATUS_TAIL"));
        assertFalse(read.contains("SECRET_INVITE"));
        assertTrue(sent.isEmpty(), sent.toString());
        input.tapEnter(lobby::update);
        assertFalse(lobby.isShowingDetails());
        assertTrue(sent.isEmpty());
        assertEquals(0, leaves.get());
    }

    @Test
    void fullReaderHidesNewlyDisabledChatWhileItStaysOpen() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.ChatBroadcast(1, "Player", "CHAT_PRIVACY_SENTINEL"));
        RaceLobbyView lobby = view(session, false, "HOST_IP:27888#SECRET_INVITE");
        input.tapKey(lobby::update, SceneKeys.F1);
        String read = openggf.timeattack.ui.FullTextReaderProbe.readAll(input, lobby::update, lobby::draw,
                lobby::detailsVisibleLines, lobby::detailsFirstLine);
        assertTrue(read.contains("CHAT_PRIVACY_SENTINEL"));
        session.setChatPreferences(true, true);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        lobby.draw(canvas);
        assertFalse(String.join("", lobby.detailsVisibleLines()).contains("CHAT_PRIVACY_SENTINEL"));
        assertFalse(canvas.shows("CHAT_PRIVACY_SENTINEL"));
        assertTrue(lobby.isShowingDetails());
        assertTrue(sent.isEmpty());
    }


    /**
     * The lobby's caps follow the live bindings: once ready over the level a keyboard selects rows with
     * its bound A key (Enter is Play), a pad names its family's buttons, the chat card names the pad's
     * confirm, and keyboard-only vote digits are not offered to a pad.
     */
    @Test
    void lobbyCapsNameTheBoundSelectKeyAndThePadsOwnButtons() {
        ClientRaceSession session = joinedSession();
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "abcdef", "Owner", "sonic"))));
        session.onControl(new ControlMessage.PlayerReady(0, true));
        RaceLobbyView lobby = view(session, true, null);
        lobby.setLevelBehind(true);
        input.xboxAndDefaultKeyboard();
        input.tapUp(lobby::update); // History, from the keyboard
        String keyboard = lobby.screen(320, 224).plain();
        assertTrue(keyboard.contains("Space Select") && keyboard.contains("ENTER Play"), keyboard);
        assertTrue(keyboard.contains("TAB: DETAILS"), keyboard);
        input.padPress(lobby::update);
        String pad = lobby.screen(400, 224).plain();
        for (String cap : List.of("X Select", "Start Play", "R3 Details", "A Leave", "X: OPEN")) {
            assertTrue(pad.contains(cap), cap + " in " + pad);
        }
        assertFalse(pad.contains("B Leave") || pad.contains("A Select"), "no Genesis names stand in for live labels: " + pad);
        session.onControl(new ControlMessage.TrackVoteOffer(List.of("s2:0:0", "s2:1:0"), 10_000));
        assertFalse(lobby.screen(400, 224).plain().contains("1-3 Vote"), "a pad cannot cast the digit votes");
        input.keyboardPress(lobby::update);
        assertTrue(lobby.screen(400, 224).plain().contains("1-3 Vote"), "a keyboard can");
        assertEquals(0, leaves.get());
    }
}
