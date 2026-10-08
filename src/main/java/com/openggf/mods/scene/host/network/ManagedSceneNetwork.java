package com.openggf.mods.scene.host.network;

import com.openggf.mods.scene.SceneNetwork;
import com.openggf.mods.scene.ScenePeer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Engine-owned scene transport. One lazy selector worker per scene owns all sockets;
 * the short monitor sections below only exchange bounded values, never perform I/O.
 * A four-byte big-endian length precedes strict UTF-8 text, capped before allocation.
 * No resolver thread, executor backlog, filesystem or ROM/content pipeline is involved.
 */
public final class ManagedSceneNetwork implements SceneNetwork, AutoCloseable {
    static final int MAX_CHARS = 4096;
    static final int MAX_BYTES = MAX_CHARS * 4;
    static final int MAX_PENDING = 256;
    private static final long SECOND = 1_000_000_000L;

    /** Shorter deadlines are injectable only by engine-package tests. */
    record Deadlines(long acceptNanos, long connectNanos, long transferNanos) {
        Deadlines {
            if (acceptNanos <= 0 || connectNanos <= 0 || transferNanos <= 0)
                throw new IllegalArgumentException("Network deadlines must be positive");
        }
    }

    private final Object lock = new Object();
    private final Deadlines deadlines;
    private Peer active;
    private boolean closed;
    private Thread worker;
    private volatile Selector selector;

    public ManagedSceneNetwork() {
        this(new Deadlines(60 * SECOND, 5 * SECOND, 5 * SECOND));
    }

    ManagedSceneNetwork(Deadlines deadlines) {
        this.deadlines = deadlines;
    }

    @Override
    public ScenePeer host(int port) {
        validatePort(port);
        return start(new InetSocketAddress(port), true);
    }

    @Override
    public ScenePeer connect(String host, int port) {
        validatePort(port);
        return start(new InetSocketAddress(literalAddress(host), port), false);
    }

    private static void validatePort(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1..65535");
    }

    private static InetAddress literalAddress(String host) {
        if ("localhost".equalsIgnoreCase(host)) return InetAddress.getLoopbackAddress();
        if (host == null || host.isEmpty() || host.length() > 45)
            throw new IllegalArgumentException("Use an IP literal or localhost");
        try {
            // Restrict getByName to numeric IPv6 syntax: it never receives a DNS name.
            if (host.indexOf(':') >= 0 && host.matches("[0-9a-fA-F:.]+"))
                return InetAddress.getByName(host);
            String[] parts = host.split("\\.", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Use an IP literal or localhost");
            byte[] address = new byte[4];
            for (int i = 0; i < 4; i++) {
                if (!parts[i].matches("[0-9]{1,3}")) throw new IllegalArgumentException("Invalid IPv4 address");
                int octet = Integer.parseInt(parts[i]);
                if (octet > 255) throw new IllegalArgumentException("Invalid IPv4 address");
                address[i] = (byte) octet;
            }
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException invalid) {
            throw new IllegalArgumentException("Invalid IP literal", invalid);
        }
    }

    private ScenePeer start(InetSocketAddress address, boolean hosting) {
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Scene networking has closed");
            if (active != null && !active.terminal())
                throw new IllegalStateException("Close the active scene peer first");
            if (active != null) active.discard();
            Peer peer = new Peer(address, hosting);
            active = peer;
            if (worker == null) {
                worker = new Thread(this::run, "openggf-scene-network");
                worker.setDaemon(true);
                worker.start();
            }
            wake();
            return peer;
        }
    }

    private void wake() {
        Selector current = selector;
        if (current != null) current.wakeup();
        lock.notifyAll();
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
            if (active != null) active.close();
            wake();
        }
    }

    private void run() {
        try (Selector loop = Selector.open()) {
            selector = loop;
            while (true) {
                Peer peer;
                synchronized (lock) {
                    while (!closed && (active == null || active.terminal())) lock.wait();
                    if (closed) return;
                    peer = active;
                }
                serve(peer, loop);
                // Closing a registered channel can defer its descriptor release until
                // selector cancellation is processed. Retire it before a new endpoint
                // binds the same port, including after accept timeout or peer close.
                loop.selectNow();
                loop.selectedKeys().clear();
            }
        } catch (IOException | InterruptedException | RuntimeException failure) {
            synchronized (lock) {
                if (active != null && !active.terminal()) active.fail("Network worker unavailable");
                closed = true;
            }
        } finally {
            selector = null;
        }
    }

    private void serve(Peer peer, Selector loop) {
        try (Resources resources = new Resources()) {
            if (peer.terminal()) return;
            long started = System.nanoTime();
            if (peer.hosting) {
                resources.server = ServerSocketChannel.open();
                resources.server.configureBlocking(false);
                resources.server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                resources.server.bind(peer.address, 1);
                resources.server.register(loop, SelectionKey.OP_ACCEPT);
            } else {
                resources.socket = SocketChannel.open();
                configure(resources.socket);
                if (resources.socket.connect(peer.address)) peer.connected();
                resources.socket.register(loop, peer.state() == ScenePeer.State.CONNECTED
                        ? SelectionKey.OP_READ : SelectionKey.OP_CONNECT);
            }
            ByteBuffer header = ByteBuffer.allocate(4);
            ByteBuffer payload = null;
            ByteBuffer writing = null;
            long frameStarted = 0;
            long writeStarted = 0;
            while (!peer.terminal()) {
                long now = System.nanoTime();
                if (peer.state() == ScenePeer.State.LISTENING && now - started >= deadlines.acceptNanos())
                    throw new IOException("Timed out waiting for a peer");
                if (peer.state() == ScenePeer.State.CONNECTING && now - started >= deadlines.connectNanos())
                    throw new IOException("Connection timed out");
                if ((payload != null || header.position() > 0) && now - frameStarted >= deadlines.transferNanos())
                    throw new IOException("Message receive timed out");
                if (writing != null && now - writeStarted >= deadlines.transferNanos())
                    throw new IOException("Message send timed out");

                if (peer.state() == ScenePeer.State.CONNECTED && writing == null) {
                    writing = peer.nextWrite();
                    writeStarted = now;
                }
                if (resources.socket != null && resources.socket.isConnected()) {
                    resources.socket.keyFor(loop).interestOps(SelectionKey.OP_READ
                            | (writing == null ? 0 : SelectionKey.OP_WRITE));
                }
                loop.select(100);
                var keys = loop.selectedKeys().iterator();
                while (keys.hasNext() && !peer.terminal()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) continue;
                    if (key.isAcceptable()) {
                        SocketChannel accepted = resources.server.accept();
                        if (accepted != null) {
                            resources.socket = accepted;
                            resources.server.close();
                            resources.server = null;
                            configure(accepted);
                            accepted.register(loop, SelectionKey.OP_READ);
                            peer.connected();
                        }
                    } else if (key.isConnectable()) {
                        if (resources.socket.finishConnect()) {
                            key.interestOps(SelectionKey.OP_READ);
                            peer.connected();
                        }
                    } else {
                        if (key.isReadable()) {
                            // One bounded read per readiness iteration prevents input flooding
                            // from starving cancellation, sends or timeout checks.
                            ByteBuffer target = payload == null ? header : payload;
                            int count = resources.socket.read(target);
                            if (count < 0) {
                                if (payload != null || header.position() != 0)
                                    throw new IOException("Truncated message frame");
                                peer.remoteClosed();
                            } else if (count > 0) {
                                if (payload == null && header.position() == count) frameStarted = System.nanoTime();
                                if (!target.hasRemaining()) {
                                    if (payload == null) {
                                        header.flip();
                                        int length = header.getInt();
                                        header.clear();
                                        if (length < 0 || length > MAX_BYTES)
                                            throw new IOException("Invalid message byte length");
                                        payload = ByteBuffer.allocate(length);
                                    }
                                    if (payload != null && !payload.hasRemaining()) {
                                        payload.flip();
                                        String text = StandardCharsets.UTF_8.newDecoder()
                                                .onMalformedInput(CodingErrorAction.REPORT)
                                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                                .decode(payload).toString();
                                        if (text.length() > MAX_CHARS)
                                            throw new IOException("Message exceeds 4096 characters");
                                        peer.received(text, System.nanoTime());
                                        payload = null;
                                    }
                                }
                            }
                        }
                        if (!peer.terminal() && key.isWritable() && writing != null) {
                            resources.socket.write(writing);
                            if (!writing.hasRemaining()) {
                                peer.written();
                                writing = null;
                            }
                        }
                    }
                }
                loop.selectedKeys().clear();
            }
        } catch (CharacterCodingException invalid) {
            peer.fail("Malformed UTF-8 message");
        } catch (IOException | RuntimeException failure) {
            String reason = failure.getMessage();
            peer.fail(reason == null ? "Network I/O failed" : reason);
        }
    }

    private static void configure(SocketChannel socket) throws IOException {
        socket.configureBlocking(false);
        socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
        socket.setOption(StandardSocketOptions.SO_SNDBUF, MAX_BYTES * 2);
        socket.setOption(StandardSocketOptions.SO_RCVBUF, MAX_BYTES * 2);
    }

    private static final class Resources implements AutoCloseable {
        ServerSocketChannel server;
        SocketChannel socket;

        @Override
        public void close() throws IOException {
            try {
                if (socket != null) socket.close();
            } finally {
                if (server != null) server.close();
            }
        }
    }

    private final class Peer implements ScenePeer {
        private final InetSocketAddress address;
        private final boolean hosting;
        private final ArrayDeque<ByteBuffer> outgoing = new ArrayDeque<>();
        private final ArrayDeque<Message> incoming = new ArrayDeque<>();
        private volatile State state;
        private volatile String error;
        private int pending;

        Peer(InetSocketAddress address, boolean hosting) {
            this.address = address;
            this.hosting = hosting;
            this.state = hosting ? State.LISTENING : State.CONNECTING;
        }

        boolean terminal() { return state == State.CLOSED || state == State.FAILED; }

        @Override public State state() { return state; }
        @Override public String error() { return state == State.FAILED ? error : null; }

        @Override
        public boolean send(String text) {
            if (text == null || text.length() > MAX_CHARS || state != State.CONNECTED) return false;
            ByteBuffer bytes;
            try {
                bytes = StandardCharsets.UTF_8.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .encode(java.nio.CharBuffer.wrap(text));
            } catch (CharacterCodingException invalid) {
                return false;
            }
            synchronized (lock) {
                if (state != State.CONNECTED || pending >= MAX_PENDING) return false;
                ByteBuffer frame = ByteBuffer.allocate(4 + bytes.remaining());
                frame.putInt(bytes.remaining()).put(bytes).flip();
                outgoing.add(frame);
                pending++;
                wake();
                return true;
            }
        }

        @Override
        public List<Message> poll() {
            synchronized (lock) {
                List<Message> result = List.copyOf(incoming);
                pending -= incoming.size();
                incoming.clear();
                return result;
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (state != State.FAILED) state = State.CLOSED;
                discard();
                wake();
            }
        }

        void discard() {
            outgoing.clear();
            incoming.clear();
            pending = 0;
        }

        void connected() {
            synchronized (lock) {
                if (!terminal()) state = State.CONNECTED;
            }
        }

        ByteBuffer nextWrite() {
            synchronized (lock) { return outgoing.pollFirst(); }
        }

        void written() {
            synchronized (lock) { if (!terminal()) pending--; }
        }

        void received(String text, long nanos) throws IOException {
            synchronized (lock) {
                if (terminal()) return;
                if (pending >= MAX_PENDING) throw new IOException("Incoming message queue full");
                incoming.add(new Message(text, nanos));
                pending++;
            }
        }

        void remoteClosed() {
            synchronized (lock) {
                if (terminal()) return;
                state = State.CLOSED;
                outgoing.clear();
                pending = incoming.size();
            }
        }

        void fail(String reason) {
            synchronized (lock) {
                if (terminal()) return;
                error = reason.substring(0, Math.min(256, reason.length()));
                state = State.FAILED;
                discard();
            }
        }
    }
}
