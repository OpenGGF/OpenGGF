package openggf.racing.server.host;

import openggf.racing.host.RaceRoomHost;

import openggf.racing.hub.RoomHostConfig;
import openggf.racing.hub.TrackValidationProfileSource;
import openggf.racing.identity.PlayerIdentity;

/** Internal engine adapter for broker-pinned direct-room TLS. */
public final class DirectRoomTls {
    private DirectRoomTls() {
    }

    public static RaceHostServer start(int port, RoomHostConfig config,
                                       PlayerIdentity identity,
                                       TrackValidationProfileSource profiles) {
        return RaceHostServer.startAuthenticated(port, config, identity, profiles);
    }

    public static String certificateSha256(RaceHostServer server) {
        return server.tlsCertificateSha256();
    }
}
