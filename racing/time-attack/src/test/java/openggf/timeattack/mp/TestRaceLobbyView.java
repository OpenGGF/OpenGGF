package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneKeys;
import openggf.racing.client.ClientRaceSession;
import openggf.racing.client.RaceClient;
import openggf.racing.protocol.ControlMessage;
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

    @Test
    void explicitHostStartAndLeaveActionsRemainReachable() {
        RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), true, null);
        input.tapEnter(lobby::update);
        assertTrue(sent.contains(new ControlMessage.RoundConfigure(ROUND)));
        input.tapDown(lobby::update); // Chat
        input.tapDown(lobby::update); // Leave
        input.tapEnter(lobby::update);
        assertEquals(1, leaves.get());
    }

    @Test
    void cancellingChatReturnsToLobbyWithoutLeavingOrStarting() {
        RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), true, null);
        input.tapDown(lobby::update);
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
        input.tapEnter(lobby::update); // guest defaults to Chat
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
    void hostInvitePageShowsTheCompleteTemplate() {
        String invite = "HOST_IP:27888#" + "Ab-_".repeat(21) + "AB";
        RaceLobbyView lobby = view(new ClientRaceSession(() -> 0), true, invite);
        input.tapUp(lobby::update); // Invite/history
        input.tapEnter(lobby::update);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        lobby.draw(canvas);
        String joined = String.join("", canvas.lines);
        assertTrue(joined.contains(invite), canvas.joined());
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
        input.tapDown(lobby::update);
        input.tapDown(lobby::update); // Start
        input.tapEnter(lobby::update);
        assertTrue(sent.contains(new ControlMessage.RoundConfigure(ROUND)));
        input.tapDown(lobby::update);
        input.tapEnter(lobby::update); // Chat editor
        input.tapEscape(lobby::update);
        assertEquals(0, leaves.get());
        input.tapEscape(lobby::update);
        assertEquals(1, leaves.get());
        assertEquals(List.of(MenuCue.NAVIGATE, MenuCue.NAVIGATE, MenuCue.CONFIRM, MenuCue.NAVIGATE,
                MenuCue.CONFIRM, MenuCue.CANCEL, MenuCue.CANCEL), cues);
    }

    @Test
    void hostStartOutsideTheLobbyIsAnError() {
        ClientRaceSession session = new ClientRaceSession(() -> 0);
        RaceLobbyView lobby = view(session, true, null);
        session.onControl(new ControlMessage.RoundStart(ROUND, 1000, 10_000));
        input.tapEnter(lobby::update);
        assertFalse(sent.stream().anyMatch(ControlMessage.RoundConfigure.class::isInstance));
        assertEquals(List.of(MenuCue.ERROR), cues);
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
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        lobby.draw(canvas);
        assertTrue(canvas.joined().contains("CHEMICAL PLANT 1"), canvas.joined());
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
            assertTrue(canvas.joined().contains("TAILS"), canvas.joined());
            assertTrue(canvas.joined().contains("#1234 NEW"), canvas.joined());
        }
    }
}
