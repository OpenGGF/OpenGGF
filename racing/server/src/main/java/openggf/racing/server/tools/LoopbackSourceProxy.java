package openggf.racing.server.tools;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Byte-transparent, task-owned loopback proxy for the 2026-10 racing socket stress harness.
 * Each upstream uses a distinct 127/8 source; production per-address admission remains intact.
 * Input is a local server port and a bounded lifetime connection budget, never an external target.
 */
final class LoopbackSourceProxy implements AutoCloseable {
    private final ServerSocket listener;
    private final int targetPort;
    private final int connectionBudget;
    private final AtomicInteger accepted = new AtomicInteger();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    // Retain the bounded thread handles until close so every started pump is joined.
    private final Set<Thread> pumps = ConcurrentHashMap.newKeySet();
    private final LongAdder upstreamBytes = new LongAdder();
    private final LongAdder downstreamBytes = new LongAdder();
    private final Thread acceptor;
    private volatile boolean closed;
    private volatile IOException failure;

    LoopbackSourceProxy(int targetPort, int connectionBudget) throws IOException {
        this.targetPort = targetPort;
        this.connectionBudget = connectionBudget;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        acceptor = Thread.ofVirtual().name("racing-stress-proxy-accept").start(this::accept);
    }

    int port() { return listener.getLocalPort(); }
    long upstreamBytes() { return upstreamBytes.sum(); }
    long downstreamBytes() { return downstreamBytes.sum(); }
    int openSockets() { return sockets.size(); }
    IOException failure() { return failure; }

    private void accept() {
        while (!closed) {
            Socket client = null;
            Socket target = null;
            try {
                client = listener.accept();
                int ordinal = accepted.incrementAndGet();
                if (ordinal > connectionBudget) {
                    throw new IOException("source proxy connection budget exceeded");
                }
                target = new Socket();
                // Linux routes the entire 127/8 block locally. Unsupported hosts fail explicitly.
                byte[] address = {127, 71, (byte) (ordinal >>> 8), (byte) ordinal};
                target.bind(new InetSocketAddress(InetAddress.getByAddress(address), 0));
                target.connect(new InetSocketAddress("127.0.0.1", targetPort), 3_000);
                client.setTcpNoDelay(true);
                target.setTcpNoDelay(true);
                synchronized (this) {
                    if (closed) {
                        closeSocket(client);
                        closeSocket(target);
                        return;
                    }
                    sockets.add(client);
                    sockets.add(target);
                    pump(client, target, upstreamBytes);
                    pump(target, client, downstreamBytes);
                }
            } catch (IOException error) {
                closeSocket(client);
                closeSocket(target);
                if (!closed) failure = new IOException(
                        "distinct loopback source connection failed (127/8 support required)", error);
                return;
            }
        }
    }

    private void pump(Socket from, Socket to, LongAdder bytes) {
        Thread thread = Thread.ofVirtual().name("racing-stress-proxy-pump").unstarted(() -> {
            try {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = from.getInputStream().read(buffer)) >= 0) {
                    to.getOutputStream().write(buffer, 0, count);
                    bytes.add(count);
                }
            } catch (IOException expectedOnPeerClose) {
                // The production clients own protocol failure reporting.
            } finally {
                closeSocket(from);
                closeSocket(to);
                sockets.remove(from);
                sockets.remove(to);
            }
        });
        pumps.add(thread);
        thread.start();
    }

    private static void closeSocket(Socket socket) {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    boolean isTerminated() {
        return !acceptor.isAlive() && pumps.stream().noneMatch(Thread::isAlive) && sockets.isEmpty();
    }

    private static boolean joinUntil(Thread thread, long deadline) {
        boolean interrupted = false;
        while (thread.isAlive()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try { thread.join(java.time.Duration.ofNanos(remaining)); }
            catch (InterruptedException interruption) { interrupted = true; }
        }
        return interrupted;
    }

    @Override
    public void close() throws InterruptedException {
        synchronized (this) { closed = true; }
        try { listener.close(); } catch (IOException ignored) { }
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        try {
            interrupted |= joinUntil(acceptor, deadline);
        } finally {
            // Registration is closed even if accept/connect or joining was interrupted.
            sockets.forEach(LoopbackSourceProxy::closeSocket);
            for (Thread thread : Set.copyOf(pumps)) interrupted |= joinUntil(thread, deadline);
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (!isTerminated()) {
            throw new IllegalStateException("source proxy did not release owned threads/sockets");
        }
        if (interrupted) throw new InterruptedException("interrupted after source proxy cleanup completed");
    }
}
