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
