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
    private GolfListener(ServerSocket server) {
        this.server = server;
        worker = Thread.ofVirtual().name("paradise-tcp-listen-" + server.getLocalPort()).start(this::acceptLoop);
    }
    /** Port 0 asks the OS for a free port; boundPort returns the concrete port. */
    public static GolfListener listen(int port) throws IOException {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("port");
        var server = new ServerSocket();
        try { server.setReuseAddress(true); server.bind(new InetSocketAddress(port), MAX_PEERS); return new GolfListener(server); }
        catch (IOException failure) { server.close(); throw failure; }
    }
    public int boundPort() { return server.getLocalPort(); }
    public boolean isClosed() { return closed.get(); }
    /** Includes every accepted socket, even before drain and during terminal flushing. */
    public int activeWorkers() {
        synchronized (peers) {
            discardRetired();
            return (worker.isAlive() ? 1 : 0) + peers.stream().mapToInt(GolfConnection::activeWorkers).sum();
        }
    }
    public List<GolfConnection> drain() {
        synchronized (peers) { discardRetired(); var result = List.copyOf(accepted); accepted.clear(); return result; }
    }
    // The disconnect callback runs before its worker has returned. Retain ownership until
    // all threads have actually stopped; at most MAX_PEERS connections can be retained.
    private void discardRetired() { peers.removeIf(connection -> connection.isClosed() && connection.activeWorkers() == 0); }
    private void acceptLoop() {
        try {
            while (!closed.get()) {
                Socket socket = server.accept();
                GolfConnection connection;
                synchronized (peers) {
                    discardRetired();
                    if (closed.get() || peers.size() >= MAX_PEERS) { socket.close(); continue; }
                    var holder = new GolfConnection[1];
                    connection = GolfConnection.accepted(socket, () -> {
                        synchronized (peers) { accepted.remove(holder[0]); }
                    });
                    holder[0] = connection; peers.add(connection); accepted.addLast(connection);
                }
                connection.startIo();
            }
        } catch (IOException failure) { close(); }
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
        synchronized (peers) { owned = List.copyOf(peers); accepted.clear(); }
        for (GolfConnection connection : owned) { if (leave == null) connection.close(); else connection.finish(leave); }
        worker.interrupt();
    }
}
