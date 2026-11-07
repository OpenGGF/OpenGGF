package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestFrozenLaunchPolicy {
    @TempDir Path root;
    record Joined(FakeHubConnection connection,int slot,String participant) { }
    Joined join(RoomHost room,String name) throws Exception {
        var identity=PlayerIdentity.loadOrCreate(root.resolve(name));
        var handshake=new ClientHandshake(identity,name,"fp");
        var connection=new FakeHubConnection();room.onConnected(connection);
        var codec=new ControlJsonCodec();
        room.onText(connection,codec.encode(null,handshake.hello()));
        var welcome=connection.text.stream().map(t->codec.decode(t).message())
                .filter(ControlMessage.Welcome.class::isInstance).map(ControlMessage.Welcome.class::cast).findFirst().orElseThrow();
        room.onText(connection,codec.encode(null,handshake.onWelcome(welcome)));
        var joined=connection.text.stream().map(t->codec.decode(t).message())
                .filter(ControlMessage.JoinAccepted.class::isInstance).map(ControlMessage.JoinAccepted.class::cast).findFirst().orElseThrow();
        return new Joined(connection,joined.playerSlot(),RoomRoundAccess.participantIdForSlot(room,joined.playerSlot(),identity.fingerprint()));
    }
    @Test void savedPostCentreSurvivesMaximumHeightActivationThroughTheRoomWire() throws Exception {
        long[] now={0}; var codec=new ControlJsonCodec();
        var room=new RoomHost(new RoomHostConfig("test","s2",0,0,"OPEN",null,8,"fp"),
                PlayerIdentity.loadOrCreate(root.resolve("host")),()->now[0],
                (game,zone,act)->Optional.of(new TrackValidationProfile(4096,1024,32,60)));
        room.configureTrackIdentity("ACT","COMPETITION"); var a=join(room,"A");
        var token=a.connection().text.stream().map(t->codec.decode(t).message())
                .filter(ControlMessage.JoinAccepted.class::isInstance).map(ControlMessage.JoinAccepted.class::cast)
                .findFirst().orElseThrow().sessionToken();
        assertTrue(room.requestStartRound(new ControlMessage.RoundConfig("s2",0,0,600,"OPEN",null,
                "ACT","COMPETITION","NORMAL",30)));
        now[0]=10000; room.tick();
        room.onText(a.connection(),codec.encode(token,new ControlMessage.AttemptStart(1)));
        room.onText(a.connection(),codec.encode(token,new ControlMessage.CheckpointReached(1,0,2,100,164)));
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        for(int i=0;i<=120;i++) {
            now[0]=10000+i*1000L/60; byte[] data=new byte[7];
            com.openggf.net.ghost.GhostFrameCodec.encode(new com.openggf.net.ghost.GhostFrame(
                    100,i==120?164:100,0,false,false,false,0,false,false,2,false,0,i==120),data,0);
            digest.update(data); room.onBinary(a.connection(),GhostPackets.encodeFrames(1,i,data));
        }
        room.onText(a.connection(),codec.encode(token,new ControlMessage.AttemptFinish(1,120,0,120,
                List.of(),List.of(),"ab".repeat(32),HexFormat.of().formatHex(digest.digest()),null)));
        var rows=a.connection().text.stream().map(t->codec.decode(t).message())
                .filter(ControlMessage.StandingsDelta.class::isInstance).map(ControlMessage.StandingsDelta.class::cast)
                .reduce((first,last)->last).orElseThrow().rows();
        assertEquals("FINISHED",rows.getFirst().outcome()); assertEquals(120,rows.getFirst().bestTimeFrames());
        assertNull(a.connection().closedReason); room.close("test complete");
    }
    @Test void lateJoinCannotEnableSwapsInASoloFrozenRoom() throws Exception {
        var room=new RoomHost(new RoomHostConfig("test","s3k",0,0,"OPEN",null,8,"fp"),
                PlayerIdentity.loadOrCreate(root.resolve("host")),()->0L,TrackValidationProfileSource.none());
        room.configureTrackIdentity("ARENA","COMPETITION");
        var a=join(room,"A");
        assertTrue(room.requestStartRound(new ControlMessage.RoundConfig("s3k",0,0,600,"OPEN",null,
                "ARENA","COMPETITION","ITEMS",30)));
        var b=join(room,"B");
        assertFalse(RoomRoundAccess.frozenLaunchForParticipant(room,a.slot(),a.participant()).swapEnabled());
        assertFalse(RoomRoundAccess.frozenLaunchForParticipant(room,b.slot(),b.participant()).swapEnabled());
        room.close("test complete");
    }
    @Test void readerRetainsFrozenEligibilityAfterPeerLeavesAndRefusesAnotherParticipant() throws Exception {
        var room=new RoomHost(new RoomHostConfig("test","s3k",0,0,"OPEN",null,8,"fp"),
                PlayerIdentity.loadOrCreate(root.resolve("host")),()->0L,
                (KeyedTrackValidationProfileSource) key->Optional.of(new TrackValidationProfile(4096,1024,32,60,1024,0,5,12)));
        room.configureTrackIdentity("ARENA","COMPETITION");
        var a=join(room,"A");var b=join(room,"B");
        assertTrue(room.requestStartRound(new ControlMessage.RoundConfig("s3k",0,0,600,"OPEN",null,
                "ARENA","COMPETITION","ITEMS",30)));
        var launch=RoomRoundAccess.frozenLaunchForParticipant(room,a.slot(),a.participant());
        assertNotNull(launch);assertTrue(launch.swapEnabled());
        assertEquals("COMPETITION",launch.config().mode());assertEquals("ITEMS",launch.config().monitors());
        room.onDisconnected(b.connection());
        assertEquals(launch,RoomRoundAccess.frozenLaunchForParticipant(room,a.slot(),a.participant()));
        assertNull(RoomRoundAccess.frozenLaunchForParticipant(room,a.slot(),"another-participant"));
        room.onDisconnected(a.connection());
        assertNull(RoomRoundAccess.frozenLaunchForParticipant(room,a.slot(),a.participant()));
        room.close("test complete");
    }
}
