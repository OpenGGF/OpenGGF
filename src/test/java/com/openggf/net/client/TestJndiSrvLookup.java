package com.openggf.net.client;

import com.openggf.net.client.MasterAddressResolver.Srv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real JDK JNDI DNS provider against an in-test loopback UDP DNS stub: answers, NXDOMAIN,
 * NODATA, SERVFAIL and silence, each mapped to a distinct outcome within the caller's budget.
 */
@Timeout(30)
class TestJndiSrvLookup {
    private StubDns dns;

    @BeforeEach
    void startStub() throws Exception {
        dns = new StubDns();
    }

    @AfterEach
    void stopStub() {
        dns.close();
    }

    @Test
    void returnsTheAnsweredSrvRecords() throws Exception {
        dns.answer("_openggf._tcp.multi-dev.openggf.com",
                new Srv(0, 0, 27910, "multi-dev.openggf.com"), new Srv(5, 10, 27911, "multi-dev.openggf.com"));
        List<Srv> records = lookup().query("_openggf._tcp.multi-dev.openggf.com", Duration.ofSeconds(5));
        assertEquals(2, records.size());
        assertTrue(records.contains(new Srv(0, 0, 27910, "multi-dev.openggf.com.")), records.toString());
        assertTrue(records.contains(new Srv(5, 10, 27911, "multi-dev.openggf.com.")), records.toString());
        assertEquals(List.of(URI.create("wss://multi-dev.openggf.com:27910/master"),
                        URI.create("wss://multi-dev.openggf.com:27911/master")),
                MasterAddressResolver.candidates(new MasterAddressResolver.Named("multi-dev.openggf.com"),
                        records, new SplittableRandom(1)));
    }

    @Test
    void nxdomainAndNodataAreEmptyAnswers() throws Exception {
        dns.rcode("_openggf._tcp.absent.example", 3);
        dns.rcode("_openggf._tcp.nosrv.example", 0);
        assertEquals(List.of(), lookup().query("_openggf._tcp.absent.example", Duration.ofSeconds(5)));
        assertEquals(List.of(), lookup().query("_openggf._tcp.nosrv.example", Duration.ofSeconds(5)));
    }

    @Test
    void servfailIsALookupFailureNotAnEmptyAnswer() {
        dns.rcode("_openggf._tcp.broken.example", 2);
        long start = System.nanoTime();
        assertThrows(IOException.class,
                () -> lookup().query("_openggf._tcp.broken.example", Duration.ofSeconds(9)));
        assertTrue(elapsedMillis(start) < 9_500, "SERVFAIL resolved within the budget");
    }

    @Test
    void silenceFailsWhenTheBudgetExpires() {
        dns.silent("_openggf._tcp.blackhole.example");
        long start = System.nanoTime();
        IOException failed = assertThrows(IOException.class,
                () -> lookup().query("_openggf._tcp.blackhole.example", Duration.ofMillis(1500)));
        long elapsed = elapsedMillis(start);
        assertTrue(elapsed >= 1400 && elapsed < 3000, "budget enforced: " + elapsed + " ms, " + failed);
        assertTrue(dns.queries.get() >= 1, "the stub was actually asked");
    }

    @Test
    void aForeignTargetThroughRealDnsStillFailsClosed() throws Exception {
        // Broken control for the end-to-end path: a correctly parsed answer naming another host.
        dns.answer("_openggf._tcp.multi-dev.openggf.com", new Srv(0, 0, 443, "evil.example"));
        List<Srv> records = lookup().query("_openggf._tcp.multi-dev.openggf.com", Duration.ofSeconds(5));
        assertEquals(MasterDiscoveryException.Reason.FOREIGN_TARGET, assertThrows(MasterDiscoveryException.class,
                () -> MasterAddressResolver.candidates(new MasterAddressResolver.Named("multi-dev.openggf.com"),
                        records, new SplittableRandom(1))).reason());
    }

    @Test
    void onlyDnsProviderUrlsAreAccepted() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new JndiSrvLookup("ldap://127.0.0.1"));
        assertEquals(new Srv(1, 2, 3, "x.example."), JndiSrvLookup.parse("1 2 3 x.example."));
        assertThrows(IOException.class, () -> JndiSrvLookup.parse("1 2 x.example."));
        assertThrows(IOException.class, () -> JndiSrvLookup.parse("a b c d"));
    }

    private JndiSrvLookup lookup() {
        return new JndiSrvLookup("dns://127.0.0.1:" + dns.port());
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /** Minimal authoritative UDP responder: one question, uncompressed SRV RDATA. */
    static final class StubDns implements AutoCloseable {
        private record Reply(int rcode, List<Srv> records, boolean silent) { }

        private final DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        private final Map<String, Reply> replies = new ConcurrentHashMap<>();
        final AtomicInteger queries = new AtomicInteger();
        private final Thread thread = Thread.ofPlatform().daemon().name("stub-dns").start(this::serve);

        StubDns() throws SocketException { }

        int port() { return socket.getLocalPort(); }

        void answer(String name, Srv... records) { replies.put(name, new Reply(0, List.of(records), false)); }
        void rcode(String name, int rcode) { replies.put(name, new Reply(rcode, List.of(), false)); }
        void silent(String name) { replies.put(name, new Reply(0, List.of(), true)); }

        private void serve() {
            byte[] buffer = new byte[512];
            while (!socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (IOException closed) {
                    return;
                }
                queries.incrementAndGet();
                try {
                    byte[] response = respond(ByteBuffer.wrap(packet.getData(), 0, packet.getLength()).slice());
                    if (response != null) {
                        socket.send(new DatagramPacket(response, response.length, packet.getSocketAddress()));
                    }
                } catch (IOException | RuntimeException malformed) {
                    // ignore unusable queries, as a resolver would
                }
            }
        }

        private byte[] respond(ByteBuffer query) throws IOException {
            int id = query.getShort(0) & 0xFFFF;
            int flags = query.getShort(2) & 0xFFFF;
            int position = 12;
            StringBuilder name = new StringBuilder();
            while (true) {
                int length = query.get(position++) & 0xFF;
                if (length == 0) break;
                if (!name.isEmpty()) name.append('.');
                byte[] label = new byte[length];
                query.get(position, label);
                name.append(new String(label, StandardCharsets.US_ASCII));
                position += length;
            }
            int questionEnd = position + 4;
            String owner = name.toString().toLowerCase(java.util.Locale.ROOT);
            Reply reply = replies.getOrDefault(owner, new Reply(3, List.of(), false));
            if (reply.silent()) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(id >> 8); out.write(id);
            int responseFlags = 0x8000 | 0x0400 | (flags & 0x0100) | 0x0080 | reply.rcode();
            out.write(responseFlags >> 8); out.write(responseFlags);
            out.write(0); out.write(1);
            out.write(0); out.write(reply.records().size());
            out.write(0); out.write(0);
            out.write(0); out.write(0);
            byte[] question = new byte[questionEnd - 12];
            query.get(12, question);
            out.write(question);
            for (Srv record : reply.records()) {
                out.write(0xC0); out.write(0x0C);
                out.write(0); out.write(33);
                out.write(0); out.write(1);
                out.write(0); out.write(0); out.write(1); out.write(0x2C);
                byte[] target = encodeName(record.target());
                int length = 6 + target.length;
                out.write(length >> 8); out.write(length);
                short16(out, record.priority());
                short16(out, record.weight());
                short16(out, record.port());
                out.write(target);
            }
            return out.toByteArray();
        }

        private static void short16(ByteArrayOutputStream out, int value) {
            out.write(value >> 8);
            out.write(value);
        }

        private static byte[] encodeName(String name) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String trimmed = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
            if (!trimmed.isEmpty()) {
                for (String label : trimmed.split("\\.")) {
                    byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
                    out.write(bytes.length);
                    out.write(bytes);
                }
            }
            out.write(0);
            return out.toByteArray();
        }

        @Override
        public void close() {
            socket.close();
            thread.interrupt();
        }
    }
}
