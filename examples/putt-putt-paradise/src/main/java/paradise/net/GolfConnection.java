package paradise.net;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Owns one TCP socket and its virtual workers. Socket workers only decode/encode immutable packets.
 * send/drain/close never wait for socket IO or worker joins; callers drain on their presentation thread.
 * Connect is asynchronous, including DNS; connection failure is a Disconnected event.
 */
public final class GolfConnection implements AutoCloseable {
    public static final int MAX_INCOMING_COMMANDS = 256;
    public static final int WRITE_DEADLINE_SECONDS = 5;
    private static final long FRAME_INTERVAL_NANOS = 1_000_000_000L / 60 + 1;
    public sealed interface Event { }
    public record Received(GolfPacket packet) implements Event { }
    /** Every fault/leave delivers one event so the room owner can HOLD before another gameplay step. */
    public record Disconnected(String reason) implements Event { }
    public record Stats(int incomingCommands, int reliableMessages, int reliableBytes,
                        int pendingFrames, int inFlightFrames, long coalescedFrames) { }
    private final Socket socket;
    private final Runnable released;
    private final OutboundPackets outbound = new OutboundPackets();
    private final ArrayDeque<Received> incoming = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean finishing = new AtomicBoolean();
    private final List<Thread> workers = new CopyOnWriteArrayList<>();
    private final Object deadlineLock = new Object();
    private long writeDeadline;
    private volatile int inFlightFrame;
    private Disconnected disconnected;

    private GolfConnection(Socket socket, Runnable released) { this.socket = socket; this.released = released; }

    public static GolfConnection connect(String hostname, int port) {
        Objects.requireNonNull(hostname);
        if (hostname.isBlank() || hostname.length() > 253 || port < 1 || port > 65535)
            throw new IllegalArgumentException("hostname/port");
        var connection = new GolfConnection(new Socket(), () -> { });
        connection.worker("connect", () -> {
            try {
                connection.socket.connect(new InetSocketAddress(hostname, port), 3000);
                connection.startIo();
            } catch (IOException | RuntimeException failure) { connection.disconnect("connect failed"); }
        });
        return connection;
    }

    static GolfConnection accepted(Socket socket, Runnable released) {
        return new GolfConnection(socket, released);
    }
    void startIo() {
        if (closed.get()) return;
        try { socket.setTcpNoDelay(true); socket.setSendBufferSize(64 * 1024); }
        catch (SocketException failure) { disconnect("socket setup failed"); return; }
        worker("read", this::readLoop); worker("write", this::writeLoop); worker("deadline", this::deadlineLoop);
    }
    private synchronized void worker(String name, Runnable task) {
        if (closed.get()) return;
        Thread worker = Thread.ofVirtual().name("paradise-tcp-" + name + "-" + Integer.toHexString(hashCode())).unstarted(task);
        workers.add(worker); worker.start();
    }

    /** Publishing is bounded and does no network IO. A false return also schedules a disconnect event. */
    public boolean send(GolfPacket packet) {
        Objects.requireNonNull(packet);
        if (closed.get() || finishing.get()) return false;
        if (!outbound.offer(packet)) { disconnect("outbound queue overflow"); return false; }
        return true;
    }
    /** Snapshot FIFO commands followed by the terminal disconnect event. No gameplay callbacks occur here. */
    public List<Event> drain() {
        synchronized (incoming) {
            var result = new ArrayList<Event>(incoming.size() + 1);
            result.addAll(incoming); incoming.clear();
            if (disconnected != null) { result.add(disconnected); disconnected = null; }
            return List.copyOf(result);
        }
    }
    public boolean isClosed() { return closed.get(); }
    /** Bounded asynchronous terminal control delivery; the writer closes after sending it or on its deadline. */
    public void finish(GolfPacket.Leave leave) {
        Objects.requireNonNull(leave);
        if (closed.get() || !finishing.compareAndSet(false, true)) return;
        if (!outbound.offer(leave)) disconnect("outbound queue overflow");
    }
    /** Nonblocking lifecycle observation; includes virtual workers omitted by getAllStackTraces. */
    public int activeWorkers() { return (int) workers.stream().filter(Thread::isAlive).count(); }
    public Stats stats() {
        OutboundPackets.Stats out = outbound.stats();
        synchronized (incoming) {
            return new Stats(incoming.size(), out.reliableMessages(), out.reliableBytes(), out.pendingFrames(),
                    inFlightFrame, out.coalescedFrames());
        }
    }

    private void readLoop() {
        try {
            while (!closed.get()) {
                GolfPacket packet = GolfCodec.read(socket.getInputStream());
                if (packet == null) { disconnect("remote closed"); return; }
                boolean overflow;
                synchronized (incoming) {
                    if (closed.get()) return;
                    overflow = incoming.size() >= MAX_INCOMING_COMMANDS;
                    if (!overflow) incoming.addLast(new Received(packet));
                }
                if (overflow) { disconnect("incoming queue overflow"); return; }
            }
        } catch (IOException failure) { disconnect("read failed"); }
    }
    private void writeLoop() {
        long lastViewWrite = 0;
        try {
            while (!closed.get()) {
                byte[] next = outbound.take();
                if (next == null) return;
                boolean view = next[5] == 6;
                inFlightFrame = view ? 1 : 0;
                if (view) {
                    long remaining;
                    while (!closed.get() && (remaining = lastViewWrite + FRAME_INTERVAL_NANOS - System.nanoTime()) > 0)
                        LockSupport.parkNanos(remaining);
                }
                if (closed.get()) return;
                synchronized (deadlineLock) { writeDeadline = System.nanoTime() + WRITE_DEADLINE_SECONDS * 1_000_000_000L; deadlineLock.notifyAll(); }
                socket.getOutputStream().write(next);
                synchronized (deadlineLock) { writeDeadline = 0; deadlineLock.notifyAll(); }
                if (view) lastViewWrite = System.nanoTime();
                inFlightFrame = 0;
                if (finishing.get() && next[5] == 10) { disconnect("local leave"); return; }
            }
        } catch (IOException failure) { disconnect("write failed"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        finally { inFlightFrame = 0; }
    }
    private void deadlineLoop() {
        try {
            while (!closed.get()) {
                boolean expired;
                synchronized (deadlineLock) {
                    expired = writeDeadline != 0 && System.nanoTime() - writeDeadline >= 0;
                    if (!expired) deadlineLock.wait(25);
                }
                if (expired) { disconnect("write deadline exceeded"); return; }
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    private synchronized void disconnect(String reason) {
        if (!closed.compareAndSet(false, true)) return;
        outbound.close();
        synchronized (incoming) {
            // A terminal Leave must survive the immediately following EOF, so rooms distinguish exit from a broken link.
            Received terminal = incoming.stream().filter(e -> e.packet() instanceof GolfPacket.Leave).reduce((a, b) -> b).orElse(null);
            incoming.clear(); if (terminal != null) incoming.add(terminal); disconnected = new Disconnected(reason);
        }
        try { socket.close(); } catch (IOException ignored) { }
        synchronized (deadlineLock) { writeDeadline = 0; deadlineLock.notifyAll(); }
        for (Thread worker : workers) if (worker != Thread.currentThread()) worker.interrupt();
        released.run();
    }
    /** Local teardown discards queued packets and releases the port without waiting for workers. */
    @Override public void close() { disconnect("local closed"); }
}
