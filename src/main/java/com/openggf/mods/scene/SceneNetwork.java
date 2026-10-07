package com.openggf.mods.scene;

/**
 * Explicit direct-connect text messaging, scoped to one scene visit. Merely obtaining
 * this facade opens no socket. All connection work and I/O run off the scene thread.
 * One listening, connecting or connected endpoint is allowed at a time; close it before
 * starting another. The host closes the facade permanently on scene exit or fault.
 */
@com.openggf.game.ModApi
public interface SceneNetwork {
    /**
     * Starts listening on all local interfaces for exactly one peer, asynchronously.
     * Ports must be 1..65535. The listener closes after accepting its first peer and
     * fails after 60 seconds without one. Bind errors appear on the returned peer.
     *
     * @throws IllegalArgumentException if the port is invalid
     * @throws IllegalStateException if an endpoint is already active or the scene has closed
     */
    ScenePeer host(int port);

    /**
     * Starts a connection asynchronously, with a five-second timeout. Accepts an IPv4
     * or IPv6 literal (without brackets or a scope id), or {@code localhost}; DNS names
     * are unsupported so name resolution cannot retain unbounded worker resources.
     * Ports must be 1..65535. Connection errors appear on the returned peer.
     *
     * @throws IllegalArgumentException if the address or port is invalid
     * @throws IllegalStateException if an endpoint is already active or the scene has closed
     */
    ScenePeer connect(String host, int port);
}
