package com.openggf.net.client;

import java.io.IOException;
import java.util.Objects;

/**
 * Why {@link MasterClient#connect(String, com.openggf.net.identity.PlayerIdentity, String, String,
 * javax.net.ssl.SSLContext)} found no master endpoint for a bare host name. Discovery fails closed:
 * there is no fallback port, so each reason is a distinct, player-facing message naming the host.
 * Invalid address text is an {@link IllegalArgumentException}, as for other endpoint policy.
 */
@com.openggf.game.ModApi
public final class MasterDiscoveryException extends IOException {
    private static final long serialVersionUID = 1L;

    /** The discovery outcome; the message is already phrased for players. */
    @com.openggf.game.ModApi
    public enum Reason {
        /** NXDOMAIN, no SRV record, or the RFC 2782 "service not offered" target ({@code .}). */
        NOT_PUBLISHED,
        /** DNS failure, timeout, or a malformed answer. */
        LOOKUP_FAILED,
        /** A record names a different target host; DNS may only change the port. */
        FOREIGN_TARGET
    }

    private final Reason reason;

    MasterDiscoveryException(Reason reason, String host, Throwable cause) {
        super(message(Objects.requireNonNull(reason, "reason"), Objects.requireNonNull(host, "host")), cause);
        this.reason = reason;
    }

    public Reason reason() { return reason; }

    private static String message(Reason reason, String host) {
        return switch (reason) {
            case NOT_PUBLISHED -> "No master server is published at " + host;
            case LOOKUP_FAILED -> "Could not look up " + host;
            case FOREIGN_TARGET -> host + "'s DNS record points to another host";
        };
    }
}
