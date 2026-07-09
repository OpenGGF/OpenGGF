package com.openggf.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/** Test-only byte-transparent TCP forwarder with fixed one-way delay. */
public final class LatencyProxy implements AutoCloseable {
    private final ServerSocket server;
    private final List<Socket> sockets = new ArrayList<>();
    private final Thread acceptor;
    private volatile boolean closed;

    public LatencyProxy(String targetHost, int targetPort, long delayMillis) throws IOException {
        server = new ServerSocket(0);
        acceptor = new Thread(() -> accept(targetHost, targetPort, delayMillis),
                "latency-proxy-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void accept(String targetHost, int targetPort, long delayMillis) {
        try {
            while (!closed) {
                Socket client = server.accept();
                Socket target = new Socket(targetHost, targetPort);
                synchronized (sockets) {
                    sockets.add(client);
                    sockets.add(target);
                }
                pump(client.getInputStream(), target.getOutputStream(), delayMillis);
                pump(target.getInputStream(), client.getOutputStream(), delayMillis);
            }
        } catch (IOException ignored) {
            // Expected when the proxy is closed.
        }
    }

    private static void pump(InputStream input, OutputStream output, long delayMillis) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (delayMillis > 0) {
                        Thread.sleep(delayMillis);
                    }
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } catch (Exception ignored) {
                // Expected when either endpoint closes.
            }
        }, "latency-proxy-pump");
        thread.setDaemon(true);
        thread.start();
    }

    public int port() {
        return server.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        server.close();
        synchronized (sockets) {
            for (Socket socket : sockets) {
                socket.close();
            }
            sockets.clear();
        }
    }
}
