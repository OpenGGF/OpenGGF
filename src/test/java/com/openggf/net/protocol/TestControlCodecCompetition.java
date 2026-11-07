package com.openggf.net.protocol;

import com.openggf.net.ghost.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestControlCodecCompetition {
    static List<ControlMessage> shapes() {
        var t = new ControlMessage.SwapTarget(65535, 0, -4096, 4096, true, true, 7, true, 2, 75, 15);
        return List.of(new ControlMessage.RaceCutoff(1, 2000),
                new ControlMessage.TeleportRequest(1, 0), new ControlMessage.TeleportNoPartner(1, 0),
                new ControlMessage.TeleportSwap(1, 1, 0, 255, t, t, 0, 50),
                new ControlMessage.TeleportOutcome(1, 50, "APPLIED"),
                new ControlMessage.TeleportOutcome(1, 50, "FALLBACK"),
                new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig("s3k", 4, 0, 60, "OPEN", null, "ARENA", "COMPETITION", "ITEMS", 60)),
                new ControlMessage.AttemptFinish(1, 50, 0, 50, List.of(10,10,10,10,10), List.of(10,20), "a".repeat(64), "b".repeat(64), null),
                new ControlMessage.AttemptReset(1, "LEFT"),
                new ControlMessage.StandingsPageRequest(0, "BEST_LAP"),
                new ControlMessage.StandingsPage(List.of(new ControlMessage.StandingsRow(0,"Name","sonic",0,1,"UNVERIFIED","DNF",10,List.of(10))),0,1,"BEST_LAP"),
                new ControlMessage.RankUpdate(1, 10, "BEST_LAP"),
                new ControlMessage.RoomTrackUpdate("room", 4, 0, "ARENA"),
                new ControlMessage.RoomCreate(new ControlMessage.RoomDescriptor("Room", "s3k", 4, 0, "OPEN", null, 8, false, RoomAccessPolicy.PUBLIC, "FULL_CHAT", "ARENA", "COMPETITION"), "RELAY", 0, "build"),
                new ControlMessage.RoomListResult(List.of(new ControlMessage.RoomSummary("room", "Room", "s3k", 4, 0, "OPEN", 1, 8, "RELAY", false, "FULL_CHAT", "ARENA", "COMPETITION")), 0, 1),
                launch());
    }
    private static ControlMessage.RoundStart launch() {
        var cfg = new ControlMessage.RoundConfig("s3k", 4, 0, 60, "OPEN", null, "ARENA", "COMPETITION", "ITEMS", 60);
        return new ControlMessage.RoundStart(cfg, 1000, 61000,
                new ControlMessage.RaceLaunch(1,1,cfg,"participant",0,"fingerprint","sonic",1000,61000,true));
    }
    @Test void roundTripsAndDirections() {
        var c = new ControlJsonCodec();
        for (var m : shapes()) assertEquals(m, c.decode(c.encode(null,m)).message());
        var request = c.encode(null, new ControlMessage.TeleportRequest(1, 0));
        assertInstanceOf(ControlMessage.TeleportRequest.class,c.decodeRoomClient(request).message());
        assertThrows(ProtocolViolationException.class, () -> c.decodeRoomServer(request));
        var swap = c.encode(null,shapes().get(3));
        assertInstanceOf(ControlMessage.TeleportSwap.class,c.decodeRoomServer(swap).message());
        assertThrows(ProtocolViolationException.class, () -> c.decodeRoomClient(swap));
    }
    @Test void rejectsInvalidTargetsAndTokensAndMissingFields() {
        var c = new ControlJsonCodec();
        String swap = c.encode(null, shapes().get(3));
        for (String bad : List.of(swap.replace("65535","65536"), swap.replace("-4096","-4097"),
                swap.replace("\"shoeTimer\":75","\"shoeTimer\":76"), swap.replace("\"shoeKind\":2","\"shoeKind\":3"),
                swap.replace("\"progress\":15","\"progress\":16"), swap.replace("\"slotB\":255","\"slotB\":0"),
                swap.replace("\"swapId\":1,",""))) assertThrows(ProtocolViolationException.class, () -> c.decode(bad));
        assertThrows(ProtocolViolationException.class, () -> c.decode(c.encode(null,new ControlMessage.TeleportOutcome(1,0,"OTHER"))));
    }
    @Test void rosterProgressUsesExistingStatusByte() {
        for (int progress=0;progress<16;progress++) {
            var entries = List.of(new GhostPackets.RosterEntry(1,10,20,(progress << 4) | GhostPackets.ROSTER_STATUS_RUNNING));
            assertEquals(8,GhostPackets.encodeRoster(entries).length);
            assertEquals(entries,GhostPackets.decodeRoster(GhostPackets.encodeRoster(entries)));
        }
    }
    @Test void ghostBitsRoundTripWithoutGrowingFrames() {
        for (int progress=0;progress<16;progress++) for (int shoes=0;shoes<3;shoes++) {
            var f = new GhostFrame(100,200,255,true,true,true,7,true,true,progress,true,shoes);
            byte[] bytes = new byte[7]; GhostFrameCodec.encode(f,bytes,0);
            assertEquals(f,GhostFrameCodec.decode(bytes,0));
        }
    }
}
