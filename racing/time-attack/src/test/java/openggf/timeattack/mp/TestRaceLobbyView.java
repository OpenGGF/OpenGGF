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

/** The race lobby scene view: host start, chat, history/invite page, votes and leave. */
class TestRaceLobbyView {
    private static final ControlMessage.RoundConfig ROUND =
            new ControlMessage.RoundConfig("s2", 0, 0, 300, "OPEN", null);

    private final List<ControlMessage> sent = new ArrayList<>();
    private final AtomicInteger leaves = new AtomicInteger();
    private final List<MenuCue> cues = new ArrayList<>();
    private final FakeViewInput input = new FakeViewInput();

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
        RaceTransport transport = new RaceTransport() {
            @Override public List<RaceClient.InboundEvent> drainInbound() { return List.of(); }
            @Override public void sendControl(ControlMessage message) { sent.add(message); }
            @Override public void sendBinary(byte[] bytes) { }
            @Override public int playerSlot() { return 0; }
            @Override public boolean isOpen() { return true; }
            @Override public void close() { }
        };
        return new RaceLobbyView(new MultiplayerRaceCoordinator(transport, session), false, ROUND, null,
                leaves::incrementAndGet, cues::add,
                () -> new openggf.timeattack.ProfileView(settings, "s2", "tails", () -> { }, cues::add));
    }

    @Test
    void profileRenamesAndRecoloursThroughTheRoomWhileInTheLobby() {
        openggf.timeattack.TimeAttackSettings settings = new openggf.timeattack.TimeAttackSettings();
        settings.setDisplayName("Ray");
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        RaceLobbyView lobby = viewWithProfile(session, settings);
        input.tapDown(lobby::update); // guest: ready -> chat
        input.tapDown(lobby::update); // chat -> profile
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
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        RaceLobbyView lobby = viewWithProfile(session, new openggf.timeattack.TimeAttackSettings());
        session.onControl(new ControlMessage.RoundStart(ROUND, 1000, 10_000));
        input.tapDown(lobby::update);
        input.tapDown(lobby::update); // ready -> chat -> profile
        cues.clear();
        input.tapEnter(lobby::update);
        assertFalse(lobby.isShowingProfile());
        assertEquals(List.of(MenuCue.ERROR), cues);
        assertTrue(sent.isEmpty());
    }

    @Test
    void internationalNamesAreDrawnInEachPlayersColour() {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
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
            assertTrue(canvas.fillsIn(guest).size() >= 2 * runs[0], "the chat sender is coloured too");
            String page = view(session, true, null).screen(width, 224).plain();
            assertTrue(page.contains("[ ] Nguyễn Thị Hoa NEW TAILS"), page);
            assertTrue(page.contains("Nguyễn Thị Hoa: xin chào"), page);
        }
    }

    @Test
    void theDetailsPageListsEachPlayersIdentityTag() {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "abcdef", "Sid", "sonic"),
                new ControlMessage.PlayerInfo(1, "123456", "Sid", "tails", true))));
        RaceLobbyView lobby = view(session, true, null);
        input.tapUp(lobby::update); // Invite/history
        input.tapEnter(lobby::update);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        lobby.draw(canvas);
        assertTrue(canvas.joined().contains("PLAYERS"), canvas.joined());
        int stock = com.openggf.net.identity.PlayerAppearance.STOCK.nameArgb();
        assertTrue(canvas.drewName("Sid", 12, stock), "two players with one name, told apart by their tags");
        canvas.assertFillsOnScreen();
    }

    @Test
    void chatTakesAnyScript() {
        RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), false, null);
        input.tapDown(lobby::update); // ready -> chat
        input.tapEnter(lobby::update);
        input.commit(lobby::update, "Привет, 你好!");
        input.tapKey(lobby::update, SceneKeys.ENTER);
        assertTrue(sent.contains(new ControlMessage.Chat("Привет, 你好!")), sent.toString());
    }

    @Test
    void explicitHostStartAndLeaveActionsRemainReachable() {
        RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), true, null);
        input.tapDown(lobby::update); // Ready -> Start
        input.tapEnter(lobby::update);
        assertTrue(sent.contains(new ControlMessage.RoundConfigure(ROUND)));
        input.tapDown(lobby::update); // Ready rule
        input.tapDown(lobby::update); // Chat
        input.tapDown(lobby::update); // Leave
        input.tapEnter(lobby::update);
        assertEquals(1, leaves.get());
    }

    @Test
    void cancellingChatReturnsToLobbyWithoutLeavingOrStarting() {
        RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), true, null);
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
        RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), false, null);
        input.tapDown(lobby::update); // guest: Ready -> Chat
        input.tapEnter(lobby::update);
        input.type(lobby::update, "Hi all!");
        input.tapKey(lobby::update, SceneKeys.ENTER);
        assertTrue(sent.contains(new ControlMessage.Chat("Hi all!")), sent.toString());
        assertFalse(lobby.isEditingChat());
    }

    @Test
    void historyDetailsReturnWithoutLeaving() {
        RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), true, null);
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
    void historyDoesNotDiscloseTheLegacyManualInvite() {
        String invite = "HOST_IP:27888#" + "Ab-_".repeat(21) + "AB";
        RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), true, invite);
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
        RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), true, null);
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
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        RaceLobbyView lobby = view(session, true, null);
        session.onControl(new ControlMessage.RoundStart(ROUND, 1000, 10_000));
        input.tapDown(lobby::update); // Ready -> Start
        input.tapEnter(lobby::update);
        assertFalse(sent.stream().anyMatch(ControlMessage.RoundConfigure.class::isInstance));
        assertEquals(List.of(MenuCue.NAVIGATE, MenuCue.ERROR), cues);
    }

    @Test
    void voteKeysCastTheOfferedTrackOnlyDuringAVote() {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
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
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        session.onControl(new ControlMessage.TrackVoteOffer(List.of("s2:0:0", "s2:1:0", "s2:2:0"), 60_000));
        session.onControl(new ControlMessage.TrackVoteTally(List.of(new ControlMessage.VoteCount("s2:1:0", 3))));
        String page = view(session, false, null).screen(320, 224).plain();
        assertTrue(page.contains("| 1 "), page);
        assertTrue(page.contains("2 CHEMICAL PLANT 1 3"), page);
        assertTrue(page.contains("| 3 "), "all three options, not just two: " + page);
    }

    @Test
    void playersAndLongNamesStayOnScreenAtEveryWidth() {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
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
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        session.applyJoin(new ControlMessage.JoinAccepted("t", 1, null, null));
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
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        session.applyJoin(new ControlMessage.JoinAccepted("t", 0, null, null));
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
        assertTrue(page.contains("WAITING FOR | Sid"), page);
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
    void guestsSeeTheRuleButCannotChangeIt() {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        session.onControl(new ControlMessage.ReadyRule("AUTO", false));
        RaceLobbyView lobby = view(session, false, null);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        lobby.draw(canvas);
        canvas.assertFillsOnScreen();
        String page = lobby.screen(320, 224).plain();
        assertTrue(page.contains("READY AUTO"), page);
        assertTrue(page.contains("NOBODY WAITS"), page);
        assertFalse(page.contains("RULE <"), "the rule action is the owner's: " + page);
    }

    @Test
    void overTheLobbyLevelOnlyTheCardsAreDrawnSoTheLevelStaysInView() {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
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
        menu.fill(0, 0, 1, 1, 0xFFFFFFFF);
        lobby.draw(menu);
        assertFalse(menu.fills.stream().anyMatch(fill -> fill[4] == 0xFFFFFFFF && fill[2] == 1),
                "without a level behind, the page clears the screen first");
    }
    @Test
    void hostsAndMembersHaveAnExplicitInviteAction() {
        for (boolean host : new boolean[] {false, true}) {
            RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), host, null);
            AtomicInteger opens = new AtomicInteger();
            lobby.setInviteAction(opens::incrementAndGet);
            input.tapKey(lobby::update, SceneKeys.I);
            assertEquals(1, opens.get());
            for (int width : new int[] {320, 400, 640}) {
                RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
                lobby.draw(canvas);
                canvas.assertFillsOnScreen();
                assertTrue(lobby.screen(width, 224).plain().contains("ROOM INVITE"));
            }
        }
    }

}
