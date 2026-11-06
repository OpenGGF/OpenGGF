package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneClipboard;
import com.openggf.mods.scene.SceneMouse;
import com.openggf.net.client.ClientRaceSession;
import com.openggf.net.client.RaceClient;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.RoomAccessPolicy;
import openggf.timeattack.ui.FakeViewInput;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TestLobbyInviteController {
    private final AtomicLong now = new AtomicLong(1_000);
    private final ClientRaceSession session = new ClientRaceSession(now::get);
    private final List<ControlMessage> sent = new ArrayList<>();
    private final List<RaceClient.InboundEvent> inbound = new ArrayList<>();
    private final List<String> copied = new ArrayList<>();
    private SceneClipboard.Result copyResult = SceneClipboard.Result.SUCCESS;
    private final SceneClipboard clipboard = value -> { copied.add(value); return () -> copyResult; };
    private final FakeViewInput input = new FakeViewInput();
    private final MultiplayerRaceCoordinator coordinator = new MultiplayerRaceCoordinator(new RaceTransport() {
        @Override public List<RaceClient.InboundEvent> drainInbound() {
            List<RaceClient.InboundEvent> events = List.copyOf(inbound);
            inbound.clear();
            return events;
        }
        @Override public void sendControl(ControlMessage message) { sent.add(message); }
        @Override public void sendBinary(byte[] bytes) { }
        @Override public int playerSlot() { return session.localSlot(); }
        @Override public boolean isOpen() { return true; }
        @Override public void close() { }
    }, session);
    private LobbyInviteController panel;

    private void open(boolean owner, boolean members, String manual) {
        RoomAccessPolicy policy = new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, members);
        session.applyJoin(new ControlMessage.JoinAccepted("token", owner ? 0 : 1, new ControlMessage.RoomDescriptor("Private room", "s2", 0, 0,
                "OPEN", null, 8, false, policy), null));
        session.onControl(new ControlMessage.RoomAccessState("instance", policy, 1, 1));
        panel = new LobbyInviteController(coordinator, owner, () -> manual, cue -> { }, now::get);
        panel.open();
    }

    private void update(openggf.timeattack.ui.ViewInput input) { panel.update(input, SceneMouse.none(), clipboard); }
    private void pump(ControlMessage message) {
        inbound.add(new RaceClient.Control(message));
        coordinator.pump();
        panel.poll(clipboard);
    }
    private ControlMessage.RoomInviteRequest request() {
        return sent.stream().filter(ControlMessage.RoomInviteRequest.class::isInstance)
                .map(ControlMessage.RoomInviteRequest.class::cast).reduce((a, b) -> b).orElseThrow();
    }
    private void disclose(long disclosure, long epoch) {
        pump(new ControlMessage.RoomInvite("7K3M9X", "instance", 1, epoch, now.get() + 60_000, disclosure));
    }
    private RecordingSceneCanvas drawn() {
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(400);
        panel.draw(canvas);
        canvas.assertFillsOnScreen();
        return canvas;
    }

    private List<String> codePixels() {
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(400);
        panel.draw(canvas);
        return canvas.fills.stream().filter(fill -> fill[0] >= 24 && fill[0] < 300
                && fill[1] >= 38 && fill[1] < 70).map(java.util.Arrays::toString).toList();
    }

    @Test
    void membersExplicitlyFetchBeforeCopyWithoutRevealing() {
        open(false, true, null);
        assertTrue(sent.isEmpty());
        List<String> masked = codePixels();
        input.tapDown(this::update);
        input.tapEnter(this::update);
        assertTrue(request().disclosureId() > 0);
        assertTrue(copied.isEmpty());
        disclose(request().disclosureId(), 1);
        panel.poll(clipboard);
        assertEquals(List.of("7K3 M9X"), copied);
        input.idle(this::update);
        assertTrue(drawn().shows("Copied"));
        assertEquals(masked, codePixels());
    }

    @Test
    void staleFetchAndPendingCopyCannotSurviveFocusLoss() {
        open(true, true, null);
        input.tapDown(this::update);
        input.tapEnter(this::update);
        long stale = request().disclosureId();
        panel.focusLost();
        disclose(stale, 1);
        assertNull(session.invite());
        assertTrue(copied.isEmpty());
        input.tapEnter(this::update);
        assertNotEquals(stale, request().disclosureId());
        copyResult = SceneClipboard.Result.PENDING;
        disclose(request().disclosureId(), 1);
        assertEquals(1, copied.size());
        panel.focusLost();
        copyResult = SceneClipboard.Result.SUCCESS;
        input.idle(this::update);
        assertFalse(drawn().shows("Copied"));
    }

    @Test
    void copyIntentExpiresAndAReplacementRoomCannotReceiveIt() {
        open(false, true, null);
        input.tapDown(this::update);
        input.tapEnter(this::update);
        long stale = request().disclosureId();
        now.addAndGet(4_501);
        disclose(stale, 1);
        assertTrue(copied.isEmpty());
        assertNull(session.invite());
        input.tapEnter(this::update);
        long next = request().disclosureId();
        pump(new ControlMessage.RoomAccessState("instance", session.accessPolicy(), 2, 2));
        pump(new ControlMessage.RoomInvite("7K3M9X", "instance", 1, 1, now.get() + 60_000, next));
        assertTrue(copied.isEmpty());
    }

    @Test
    void disabledMembersCannotFetchOrCopyAndAnExpiryDropsTheOldDisclosure() {
        open(false, false, null);
        input.tapEnter(this::update);
        assertTrue(sent.isEmpty());
        assertTrue(copied.isEmpty());
        assertFalse(panel.isOpen());
        panel.open();
        assertTrue(drawn().shows("Only the host can invite"));
        panel.close();
        open(false, true, null);
        input.tapEnter(this::update);
        disclose(request().disclosureId(), 1);
        now.addAndGet(60_001);
        input.idle(this::update);
        assertNull(session.invite());
        assertFalse(drawn().shows("7K3 M9X"));
    }

    @Test
    void ownerSharingIsPendingUntilAuthoritativeAcknowledgmentAndFailureIsGeneric() {
        open(true, true, null);
        for (int i = 0; i < 3; i++) input.tapDown(this::update);
        input.tapEnter(this::update);
        assertInstanceOf(ControlMessage.RoomInviteSharing.class, sent.getLast());
        input.idle(this::update);
        assertTrue(drawn().shows("Updating invite"));
        assertTrue(session.accessPolicy().membersCanInvite());
        pump(new ControlMessage.RoomAccessState("instance", new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, false), 1, 2));
        input.idle(this::update);
        assertTrue(drawn().shows("Updating invite"));
        pump(new ControlMessage.RoomInvite("7K3M9X", "instance", 1, 2, now.get() + 60_000, 0));
        input.idle(this::update);
        assertTrue(drawn().shows("Invite updated"));
        input.tapEnter(this::update);
        pump(new ControlMessage.RoomInviteRejected("7K3M9X secret endpoint password", 0));
        input.idle(this::update);
        assertTrue(drawn().shows("Invite unavailable"));
        assertFalse(drawn().shows("secret endpoint"));
    }

    @Test
    void ownerPasswordChangeUsesAnExactCredentialAndWaitsForAck() {
        open(true, true, null);
        for (int i = 0; i < 4; i++) input.tapDown(this::update);
        input.tapEnter(this::update);
        assertTrue(panel.isEditingText());
        input.commit(this::update, "  abcdefghijklmnop🙂  ");
        input.tapEnter(this::update);
        assertFalse(panel.isEditingText());
        assertInstanceOf(ControlMessage.RoomPasswordChange.class, sent.getLast());
        assertTrue(drawn().shows("Changing password"));
        pump(new ControlMessage.RoomAccessState("instance", session.accessPolicy(), 2, 2));
        pump(new ControlMessage.RoomPasswordChanged(2));
        input.idle(this::update);
        assertTrue(drawn().shows("Password changed"));
    }

    @Test
    void manualFallbackCopiesTheWholePinnedAddressAndStaysMasked() {
        String address = "HOST_IP:27900#" + "abcDEF_123-".repeat(9);
        open(true, true, address);
        assertFalse(drawn().shows("HOST_IP"));
        input.tapDown(this::update);
        input.tapEnter(this::update);
        assertEquals(List.of(address), copied);
        assertFalse(drawn().shows("HOST_IP"));
        panel.focusLost();
        assertFalse(panel.isEditingText());
    }
    @Test
    void acceptedMutationInvalidationWithdrawsPendingCopyBeforeAnEpochAck() {
        open(false, true, null);
        input.tapDown(this::update);
        input.tapEnter(this::update);
        long disclosure = request().disclosureId();
        pump(new ControlMessage.RoomInviteInvalidated("instance"));
        disclose(disclosure, 1);
        assertNull(session.invite());
        assertTrue(copied.isEmpty());
        input.idle(this::update);
        assertFalse(drawn().shows("7K3 M9X"));
    }

    @Test
    void inviteExpiryUsesTheHubClockWhenItDiffersFromLocalWallTime() {
        open(false, true, null);
        pump(new ControlMessage.Pong(now.get(), now.get() + 100_000));
        assertEquals(101_000, session.hubNowEstimateMillis());
        List<String> masked = codePixels();
        input.tapEnter(this::update);
        pump(new ControlMessage.RoomInvite("7K3M9X", "instance", 1, 1, 102_000, request().disclosureId()));
        input.idle(this::update);
        assertNotNull(session.invite());
        assertNotEquals(masked, codePixels());
        now.addAndGet(1_001);
        input.idle(this::update);
        assertNull(session.invite());
        assertEquals(masked, codePixels());
    }

    @Test
    void acceptedMutationMasksAnAlreadyRevealedCodeBeforeAnotherUpdate() {
        open(true, true, null);
        List<String> masked = codePixels();
        input.tapEnter(this::update);
        disclose(request().disclosureId(), 1);
        input.idle(this::update);
        assertNotEquals(masked, codePixels());
        pump(new ControlMessage.RoomInviteInvalidated("instance"));
        assertEquals(masked, codePixels());
    }

}
