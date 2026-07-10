package com.openggf.net.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestPhase3Protocol {
    @Test
    void roundTripsEveryNewControlMessage() {
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "Big", "s3k", 0, 0, "OPEN", null, 256, false);
        List<ControlMessage> messages = List.of(
                new ControlMessage.RoomCreate(descriptor, "RELAY", 0, "0.6:cafe1234"),
                new ControlMessage.RoomCreated("room-1"),
                new ControlMessage.RoomCreateRejected("denied"),
                new ControlMessage.RoomListRequest("s3k", 0),
                new ControlMessage.RoomListResult(List.of(new ControlMessage.RoomSummary(
                        "room-1", "Big", "s3k", 0, 0, "OPEN", 12, 256,
                        "RELAY", false)), 0, 1),
                new ControlMessage.RoomJoinRequest("room-1"),
                new ControlMessage.RoomJoinResult("room-1", "DIRECT", "192.168.1.5",
                        27888, "hostfingerprint", "0.6:cafe1234"),
                new ControlMessage.RoomJoinRejected("fingerprint mismatch"),
                new ControlMessage.RoomLeave("room-1"),
                new ControlMessage.Heartbeat("room-1", 5),
                new ControlMessage.PowChallenge("JOIN", "cHJlZml4", 22),
                new ControlMessage.PowSolution("JOIN", 123456789L),
                new ControlMessage.RelayAttach("room-1"),
                new ControlMessage.RelayGuestOpen(7),
                new ControlMessage.RelayGuestClose(7, "gone"),
                new ControlMessage.RelayGuestText(7, "{\"v\":1}"),
                new ControlMessage.StandingsPageRequest(2),
                new ControlMessage.StandingsPage(List.of(new ControlMessage.StandingsRow(
                        0, "A", "sonic", 3600, 41, "NONE")), 2, 16),
                new ControlMessage.RankUpdate(41, 3600));
        for (ControlMessage message : messages) {
            assertEquals(message,
                    ControlCodec.decode(ControlCodec.encode("t", message)).message());
        }
    }

    @Test
    void rosterRoundTripsFullRoom() {
        List<GhostPackets.RosterEntry> entries = new ArrayList<>();
        for (int slot = 0; slot < 256; slot++) {
            entries.add(new GhostPackets.RosterEntry(slot, slot * 64, 4,
                    GhostPackets.ROSTER_STATUS_RUNNING));
        }
        byte[] packet = GhostPackets.encodeRoster(entries);
        assertEquals(GhostPackets.TYPE_ROSTER, packet[0] & 0xFF);
        assertEquals(3 + 256 * 5, packet.length);
        assertEquals(entries, GhostPackets.decodeRoster(packet));
    }

    @Test
    void rosterRejectsMalformedAndOutOfRangeData() {
        byte[] good = GhostPackets.encodeRoster(List.of(
                new GhostPackets.RosterEntry(0, 1, 2, 0)));
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.decodeRoster(Arrays.copyOf(good, good.length - 1)));
        byte[] badStatus = good.clone();
        badStatus[badStatus.length - 1] = (byte) 255;
        assertThrows(ProtocolViolationException.class,
                () -> GhostPackets.decodeRoster(badStatus));
        assertThrows(ProtocolViolationException.class, () -> GhostPackets.encodeRoster(
                List.of(new GhostPackets.RosterEntry(0, -1, 0, 0))));
        assertThrows(ProtocolViolationException.class, () -> GhostPackets.encodeRoster(
                List.of(new GhostPackets.RosterEntry(256, 0, 0, 0))));
        assertThrows(ProtocolViolationException.class, () -> GhostPackets.encodeRoster(
                List.of(new GhostPackets.RosterEntry(0, 0, 0, 3))));
    }

    @Test
    void relayGuestBinaryWrapsAndEnforcesCap() {
        byte[] inner = GhostPackets.encodeFrames(1, 0,
                new byte[com.openggf.ghost.GhostFrameCodec.BYTES]);
        GhostPackets.RelayGuestBinary decoded = GhostPackets.decodeRelayGuestBinary(
                GhostPackets.encodeRelayGuestBinary(300, inner));
        assertEquals(300, decoded.guestId());
        assertArrayEquals(inner, decoded.payload());
        assertThrows(ProtocolViolationException.class, () ->
                GhostPackets.encodeRelayGuestBinary(1,
                        new byte[Protocol.MAX_BINARY_BYTES - 2]));
        assertThrows(ProtocolViolationException.class, () ->
                GhostPackets.decodeRelayGuestBinary(new byte[] {0x04, 0, 1}));
    }

    @Test
    void tunneledTextUsesOnlyExpandedMasterDecodeCap() {
        String inner = "\"".repeat(Protocol.MAX_CONTROL_BYTES - 200);
        ControlMessage wrapped = new ControlMessage.RelayGuestText(7, inner);
        String wire = ControlCodec.encode(null, wrapped);
        int bytes = wire.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(bytes > Protocol.MAX_CONTROL_BYTES);
        assertTrue(bytes <= Protocol.MAX_MASTER_FRAME_BYTES);
        assertEquals(wrapped,
                ControlCodec.decode(wire, Protocol.MAX_MASTER_FRAME_BYTES).message());
        assertThrows(ProtocolViolationException.class, () -> ControlCodec.decode(wire));
    }
}
