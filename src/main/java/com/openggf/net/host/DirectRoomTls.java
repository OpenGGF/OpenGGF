package com.openggf.net.host;

import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;

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
