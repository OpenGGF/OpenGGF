package com.openggf.net.host;

import com.openggf.net.host.jdk.JdkHostLimits;
import com.openggf.net.host.jdk.JdkRaceHostServer;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;

import java.util.function.LongSupplier;

/** The two {@link RaceRoomHost} transports, for running one host contract suite against both. */
public enum RaceHostImpl {
    NETTY {
        @Override
        public RaceRoomHost start(int port, RoomHostConfig config, PlayerIdentity identity,
                                  TrackValidationProfileSource profiles, LongSupplier clockMillis) {
            return RaceHostServer.start(port, config, identity, profiles, clockMillis);
        }

        @Override
        public RaceRoomHost startTls(int port, RoomHostConfig config, PlayerIdentity identity,
                                     TrackValidationProfileSource profiles) {
            return RaceHostServer.startAuthenticated(port, config, identity, profiles);
        }
    },
    JDK {
        @Override
        public RaceRoomHost start(int port, RoomHostConfig config, PlayerIdentity identity,
                                  TrackValidationProfileSource profiles, LongSupplier clockMillis) {
            return JdkRaceHostServer.start(port, config, identity, profiles, clockMillis, false,
                    JdkHostLimits.defaults());
        }

        @Override
        public RaceRoomHost startTls(int port, RoomHostConfig config, PlayerIdentity identity,
                                     TrackValidationProfileSource profiles) {
            return JdkRaceHostServer.startTls(port, config, identity, profiles);
        }
    };

    /** Plaintext room whose room time follows {@code clockMillis}. */
    public abstract RaceRoomHost start(int port, RoomHostConfig config, PlayerIdentity identity,
                                       TrackValidationProfileSource profiles,
                                       LongSupplier clockMillis);

    /** Plaintext room on the wall clock. */
    public RaceRoomHost start(int port, RoomHostConfig config, PlayerIdentity identity,
                              TrackValidationProfileSource profiles) {
        return start(port, config, identity, profiles, System::currentTimeMillis);
    }

    /** Broker-pinnable TLS room on the wall clock. */
    public abstract RaceRoomHost startTls(int port, RoomHostConfig config,
                                          PlayerIdentity identity,
                                          TrackValidationProfileSource profiles);
}
