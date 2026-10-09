package openggf.racing.server.host;

import openggf.racing.host.RaceRoomHost;

import openggf.racing.server.host.RaceHostServer;
import openggf.racing.server.host.DirectRoomTls;

import openggf.racing.host.jdk.JdkHostLimits;
import openggf.racing.host.jdk.JdkRaceHostServer;
import openggf.racing.hub.RoomHostConfig;
import openggf.racing.hub.TrackValidationProfileSource;
import openggf.racing.identity.PlayerIdentity;

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
