package paradise.net;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns a bounded listener and its accepted sockets until close; no engine callbacks. */
public final class GolfListener implements AutoCloseable {
    public static final int MAX_PEERS = 2;
    private final ServerSocket server;
    private final Set<GolfConnection> peers = new HashSet<>();
    private final ArrayDeque<GolfConnection> accepted = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread worker;
    private final byte[] invite;
    private final Map<Socket, Thread> authenticating = new HashMap<>();
    private GolfListener(ServerSocket server, byte[] invite) {
        this.server = server; this.invite = invite;
        worker = Thread.ofVirtual().name("paradise-tcp-listen-" + server.getLocalPort()).start(this::acceptLoop);
    }
    /** Port 0 asks the OS for a free port; boundPort returns the concrete port. */
    public static GolfListener listen(int port) throws IOException {
        return listen(port, false);
    }
    /** Explicit network exposure: the code must be shared privately with the invited guest. */
    public static GolfListener listenLan(int port) throws IOException { return listen(port, true); }
    public String inviteCode() { return invite == null ? "" : GolfInvite.code(invite); }
    private static GolfListener listen(int port, boolean lan) throws IOException {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port");
        var server = new ServerSocket();
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(lan ? null : InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port), MAX_PEERS);
            return new GolfListener(server, lan ? GolfInvite.secret() : null);
        }
        catch (IOException failure) { server.close(); throw failure; }
    }
    public int boundPort() { return server.getLocalPort(); }
    public boolean isClosed() { return closed.get(); }
    /** Includes every accepted socket, even before drain and during terminal flushing. */
    public int activeWorkers() {
        synchronized (peers) {
            discardRetired();
            return (worker.isAlive() ? 1 : 0) + peers.stream().mapToInt(GolfConnection::activeWorkers).sum()
                    + (int) authenticating.values().stream().filter(Thread::isAlive).count();
        }
    }
    public List<GolfConnection> drain() {
        synchronized (peers) { discardRetired(); var result = List.copyOf(accepted); accepted.clear(); return result; }
    }
    // The disconnect callback runs before its worker has returned. Retain ownership until
    // all threads have actually stopped; at most MAX_PEERS connections can be retained.
    private void discardRetired() {
        authenticating.entrySet().removeIf(entry -> !entry.getValue().isAlive());
        // An undrained terminal socket still owns a disconnect event. Keep it in
        // the bounded peer set until the presentation thread receives that event.
        peers.removeIf(connection -> !accepted.contains(connection)
                && connection.isClosed() && connection.activeWorkers() == 0);
    }
    private void acceptLoop() {
        try {
            while (!closed.get()) {
                Socket socket = server.accept();
                synchronized (peers) {
                    discardRetired();
                    if (closed.get() || peers.size() + authenticating.size() >= MAX_PEERS) { socket.close(); continue; }
                    if (invite == null) publish(socket);
                    else {
                        Thread authentication = Thread.ofVirtual().name("paradise-tcp-invite-" + server.getLocalPort())
                                .unstarted(() -> authenticate(socket));
                        authenticating.put(socket, authentication); authentication.start();
                    }
                }
            }
        } catch (IOException failure) { close(); }
    }
    // Called under peers; unauthenticated sockets are never visible to GolfRoom.
    private void publish(Socket socket) {
        var connection = GolfConnection.accepted(socket);
        peers.add(connection); accepted.addLast(connection); connection.startIo();
    }
    private void authenticate(Socket socket) {
        boolean transferred = false;
        try {
            if (GolfInvite.host(socket, invite)) synchronized (peers) {
                if (!closed.get()) { publish(socket); transferred = true; }
            }
        } catch (IOException | RuntimeException rejected) { /* No room seat or message authority. */ }
        finally {
            if (!transferred) try { socket.close(); } catch (IOException ignored) { }
            // Retain the worker until it has actually returned. The next listener
            // observation/accept reaps dead entries under peers, as for connections.
        }
    }
    /** Closing the listener also closes drained peers: callers need no blocking join operation. */
    @Override public void close() {
        stop(null);
    }
    /** Stops accepting immediately; owned peers deliver one bounded terminal control before their asynchronous close. */
    public void finish(GolfPacket.Leave leave) { stop(Objects.requireNonNull(leave)); }
    private void stop(GolfPacket.Leave leave) {
        if (!closed.compareAndSet(false, true)) return;
        try { server.close(); } catch (IOException ignored) { }
        List<GolfConnection> owned;
        synchronized (peers) {
            owned = List.copyOf(peers); accepted.clear();
            for (Socket socket : authenticating.keySet()) try { socket.close(); } catch (IOException ignored) { }
        }
        for (GolfConnection connection : owned) { if (leave == null) connection.close(); else connection.finish(leave); }
        worker.interrupt();
    }
}
