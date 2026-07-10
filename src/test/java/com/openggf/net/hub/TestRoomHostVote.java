package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestRoomHostVote {
    private static final String FP = "0.6:cafe";

    @Test
    void memberVoteSelectsStartableTrackAndRefreshesDescriptor(@TempDir Path dir)
            throws Exception {
        long[] now = {1_000_000};
        PlayerIdentity hostIdentity = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        RoomHost room = new RoomHost(new RoomHostConfig("Vote", "s3k", 0, 0,
                "OPEN", null, 8, FP,
                List.of("s3k:0:0", "s3k:0:1", "s3k:1:0", "s3k:1:1")),
                hostIdentity, () -> now[0], TrackValidationProfileSource.none());
        FakeHubConnection host = new FakeHubConnection();
        FakeHubConnection member = new FakeHubConnection();
        String hostToken = admit(room, host, hostIdentity, "HOST");
        String memberToken = admit(room, member,
                PlayerIdentity.loadOrCreate(dir.resolve("member")), "MEMBER");
        ControlMessage.RoundConfig first = new ControlMessage.RoundConfig(
                "s3k", 0, 0, 2, "OPEN", null);
        room.onText(host, ControlCodec.encode(hostToken,
                new ControlMessage.RoundConfigure(first)));
        now[0] += HostRoundEngine.COUNTDOWN_MILLIS + 2001;
        room.tick();
        now[0] += HostRoundEngine.ROUND_END_LINGER_MILLIS;
        room.tick();
        List<String> options = room.round().voteOptions();
        String winner = options.get(1);
        room.onText(member, ControlCodec.encode(memberToken,
                new ControlMessage.TrackVote(winner)));
        now[0] += HostRoundEngine.VOTE_WINDOW_MILLIS;
        room.tick();

        room.onText(host, ControlCodec.encode(hostToken,
                new ControlMessage.RoundConfigure(
                        new ControlMessage.RoundConfig("s3k", 99, 99, 2, "OPEN", null))));
        assertEquals(HostRoundEngine.Phase.LOBBY, room.round().phase());
        ControlMessage.RoundConfig next = room.round().votedNextConfig();
        room.onText(host, ControlCodec.encode(hostToken,
                new ControlMessage.RoundConfigure(next)));
        assertEquals(HostRoundEngine.Phase.COUNTDOWN, room.round().phase());
        assertEquals(next.zone(), room.descriptor().zone());
        assertEquals(next.act(), room.descriptor().act());
    }

    private static String admit(RoomHost room, FakeHubConnection connection,
                                PlayerIdentity identity, String name) throws Exception {
        ClientHandshake handshake = new ClientHandshake(identity, name, FP);
        room.onConnected(connection);
        room.onText(connection, ControlCodec.encode(null, handshake.hello()));
        ControlMessage.Welcome welcome = (ControlMessage.Welcome) last(connection);
        room.onText(connection, ControlCodec.encode(null, handshake.onWelcome(welcome)));
        return connection.text.stream().map(value -> ControlCodec.decode(value).message())
                .filter(ControlMessage.JoinAccepted.class::isInstance)
                .map(ControlMessage.JoinAccepted.class::cast)
                .findFirst().orElseThrow().sessionToken();
    }

    private static ControlMessage last(FakeHubConnection connection) {
        return ControlCodec.decode(connection.text.getLast()).message();
    }
}
