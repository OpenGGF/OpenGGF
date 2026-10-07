package com.openggf.mods.scene;

import java.util.List;

/**
 * One ordered text connection. Calls never wait for connection progress or socket I/O.
 * This is a message transport, not synchronized gameplay, discovery, or asset transfer.
 * Connections are plaintext and unauthenticated; validate every application message.
 */
@com.openggf.game.ModApi
public interface ScenePeer {
    /** The current connection phase; CLOSED and FAILED are terminal. */
    @com.openggf.game.ModApi
    enum State { LISTENING, CONNECTING, CONNECTED, CLOSED, FAILED }

    /**
     * Text observed by the receiving host, in wire order. {@code receivedNanos} uses
     * the same local monotonic clock as physical input events; it is not the sender's
     * clock, and network or scheduling delay remains part of the observation.
     */
    @com.openggf.game.ModApi
    record Message(String text, long receivedNanos) { }

    /** Current phase, including asynchronous bind/connect/protocol failures. */
    State state();

    /** A bounded human-readable failure reason in FAILED, otherwise null. */
    String error();

    /**
     * Enqueues well-formed UTF-8 text of at most 4096 Java characters (UTF-16 code
     * units). Returns false for null/oversized/ill-formed text, a non-connected peer,
     * or a full queue. True means queued, not delivered. Empty text is allowed.
     * Incoming and outgoing messages share a 256-message budget, including a write
     * in progress. Incoming overflow fails the connection rather than dropping data.
     */
    boolean send(String text);

    /** Drains received messages into an immutable list, in order; at most 256 entries. */
    List<Message> poll();

    /**
     * Cancels listening/connecting/I/O and discards pending messages. Idempotent;
     * FAILED retains its error. A clean remote close becomes CLOSED and retains
     * already received messages until polled or explicitly closed.
     */
    void close();
}
