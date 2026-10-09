package openggf.racing.host;

import openggf.racing.hub.RoomHost;

/**
 * Transport-neutral contract for an in-process race room host.
 *
 * <p>Two implementations satisfy it: the Netty {@link RaceHostServer} used by the
 * dedicated server, and the JDK-only {@code net.host.jdk.JdkRaceHostServer} that
 * can run inside a validated code mod. Callers that only start, advertise and
 * drive a room should depend on this interface so either transport can be used.
 *
 * <p>Every {@link RoomHost} callback, the periodic room tick and every task passed
 * to {@link #execute(Runnable)} run serially on one room thread. {@link #room()}
 * must only be mutated from such a task.
 */
public interface RaceRoomHost extends AutoCloseable {
    /** Bound TCP port of the {@code /race} WebSocket endpoint. */
    int port();

    /**
     * Lower-case hex SHA-256 of the room's DER-encoded TLS certificate, as pinned by
     * direct-join invites and broker room listings; {@code null} for plaintext rooms.
     */
    String tlsCertificateSha256();

    /**
     * Runs {@code task} on the serialized room thread.
     *
     * @throws IllegalStateException after {@link #close()}
     */
    void execute(Runnable task);

    /** The room protocol driver owned by this host. */
    RoomHost room();

    /** Stops accepting, closes every peer connection and releases the port and threads. Idempotent. */
    @Override
    void close();
}
