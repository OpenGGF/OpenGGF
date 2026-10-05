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
    public int activeWorkers() { return worker.isAlive() ? 1 : 0; }
    public List<GolfConnection> drain() {
        synchronized (peers) { var result = List.copyOf(accepted); accepted.clear(); return result; }
    }
    private void acceptLoop() {
        try {
            while (!closed.get()) {
                Socket socket = server.accept();
                GolfConnection connection;
                synchronized (peers) {
                    if (closed.get() || peers.size() >= MAX_PEERS) { socket.close(); continue; }
                    var holder = new GolfConnection[1];
                    connection = GolfConnection.accepted(socket, () -> {
                        synchronized (peers) { peers.remove(holder[0]); accepted.remove(holder[0]); }
                    });
                    holder[0] = connection; peers.add(connection); accepted.addLast(connection);
                }
                connection.startIo();
            }
        } catch (IOException failure) { close(); }
    }
    /** Closing the listener also closes drained peers: callers need no blocking join operation. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { server.close(); } catch (IOException ignored) { }
        List<GolfConnection> owned;
        synchronized (peers) { owned = List.copyOf(peers); peers.clear(); accepted.clear(); }
        for (GolfConnection connection : owned) connection.close();
        worker.interrupt();
    }
}
