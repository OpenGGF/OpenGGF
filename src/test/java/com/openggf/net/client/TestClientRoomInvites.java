package com.openggf.net.client;

import com.openggf.net.protocol.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestClientRoomInvites {
    long now = 1000;
    ClientRaceSession session() {
        var session = new ClientRaceSession(() -> now);
        session.applyJoin(new ControlMessage.JoinAccepted("token", 0, new ControlMessage.RoomDescriptor(
                "Room", "s3k", 0, 0, "OPEN", null, 8, false),
                new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of())));
        session.onControl(new ControlMessage.RoomAccessState("instance", RoomAccessPolicy.PUBLIC, 0, 1));
        return session;
    }
    ControlMessage.RoomInvite reply(long disclosure, long inviteEpoch, long expiry) {
        return new ControlMessage.RoomInvite("7K3M9X", "instance", 0, inviteEpoch, expiry, disclosure);
    }

    @Test
    void onlyExplicitMatchingDisclosureIsCachedAndHideInvalidatesLateReplies() {
        var session = session();
        session.onControl(reply(0, 1, 10_000));
        assertNull(session.invite());
        var first = session.beginInviteRequest();
        session.invalidateInvite();
        var second = session.beginInviteRequest();
        session.onControl(reply(first.disclosureId(), 1, 10_000));
        assertNull(session.invite());
        session.onControl(reply(second.disclosureId(), 1, 10_000));
        assertEquals("7K3M9X", session.invite().code());
        session.invalidateInvite();
        session.onControl(reply(second.disclosureId(), 1, 10_000));
        assertNull(session.invite());
    }

    @Test
    void expiryIsAbsoluteAndStalePolicyEpochOrInstanceCannotRestoreCode() {
        var session = session();
        var request = session.beginInviteRequest();
        session.onControl(reply(request.disclosureId(), 1, 2000));
        assertNotNull(session.invite());
        now = 2000;
        assertNull(session.invite());
        request = session.beginInviteRequest();
        session.onControl(reply(request.disclosureId(), 1, 2000));
        assertNull(session.invite());
        now = 1500;
        request = session.beginInviteRequest();
        session.onControl(new ControlMessage.RoomAccessState("instance", new RoomAccessPolicy(RoomAccessPolicy.Mode.PUBLIC, false), 0, 2));
        session.onControl(reply(request.disclosureId(), 1, 10_000));
        assertNull(session.invite());
        session.onControl(new ControlMessage.RoomInvite("7K3M9X", "replacement", 0, 2, 10_000, request.disclosureId()));
        assertNull(session.invite());
        assertFalse(session.accessPolicy().membersCanInvite());
    }

    @Test
    void canonicalRenewalStateCanArriveBeforeMatchingExplicitReply() {
        var session = session();
        var request = session.beginInviteRequest();
        session.onControl(new ControlMessage.RoomAccessState("instance", RoomAccessPolicy.PUBLIC, 0, 2));
        session.onControl(reply(request.disclosureId(), 2, 10_000));
        assertNotNull(session.invite());
        session.onControl(new ControlMessage.Kick("left"));
        assertNull(session.invite());
        assertThrows(IllegalStateException.class, session::beginInviteRequest);
    }
    @Test
    void pendingAuthorityChangeHidesCodeWithoutPretendingTheEpochChanged() {
        var session = session();
        var request = session.beginInviteRequest();
        session.onControl(reply(request.disclosureId(), 1, 10_000));
        assertNotNull(session.invite());
        session.onControl(new ControlMessage.RoomInviteInvalidated("instance"));
        assertNull(session.invite());
        assertEquals(1, session.inviteEpoch());
        session.onControl(reply(request.disclosureId(), 1, 10_000));
        assertNull(session.invite());
        var fresh = session.beginInviteRequest();
        session.onControl(reply(fresh.disclosureId(), 1, 10_000));
        assertNotNull(session.invite());
    }

}
