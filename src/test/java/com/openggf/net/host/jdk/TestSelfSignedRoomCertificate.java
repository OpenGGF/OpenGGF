package com.openggf.net.host.jdk;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import static org.junit.jupiter.api.Assertions.*;

/** JDK-only direct-room certificate: structure, pin and TLS usability without Bouncy Castle. */
class TestSelfSignedRoomCertificate {
    private static final long YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000;

    @Test
    void certificateIsASelfSignedV3WithThePinnedDigest() throws Exception {
        long now = System.currentTimeMillis();
        SelfSignedRoomCertificate generated = SelfSignedRoomCertificate.generate(now);
        X509Certificate certificate = generated.certificate();

        assertEquals(3, certificate.getVersion());
        assertEquals("CN=openggf-direct-room", certificate.getSubjectX500Principal().getName());
        assertEquals(certificate.getSubjectX500Principal(), certificate.getIssuerX500Principal());
        certificate.verify(certificate.getPublicKey());
        assertEquals("SHA256withECDSA", certificate.getSigAlgName());
        assertEquals("EC", certificate.getPublicKey().getAlgorithm());
        assertTrue(certificate.getSerialNumber().signum() > 0);
        assertTrue(Math.abs(certificate.getNotBefore().getTime() - (now - YEAR_MILLIS)) < 2_000,
                "notBefore is back-dated one year like Netty's self-signed certificate");
        assertEquals(Instant.parse("9999-12-31T23:59:59Z"), certificate.getNotAfter().toInstant());
        certificate.checkValidity();
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(certificate.getEncoded())), generated.sha256Hex());
        assertTrue(generated.sha256Hex().matches("[0-9a-f]{64}"));
    }

    @Test
    void everyRoomGetsAFreshKeyAndPin() throws Exception {
        long now = System.currentTimeMillis();
        SelfSignedRoomCertificate first = SelfSignedRoomCertificate.generate(now);
        SelfSignedRoomCertificate second = SelfSignedRoomCertificate.generate(now);
        assertNotEquals(first.sha256Hex(), second.sha256Hex());
        assertNotEquals(first.certificate().getPublicKey(), second.certificate().getPublicKey());
    }

    @Test
    void serverContextHandshakesWithTls12AndTls13Clients() throws Exception {
        SelfSignedRoomCertificate generated =
                SelfSignedRoomCertificate.generate(System.currentTimeMillis());
        SSLContext server = generated.serverContext();
        for (String protocol : new String[] {"TLSv1.3", "TLSv1.2"}) {
            try (SSLServerSocket listener = (SSLServerSocket) server.getServerSocketFactory()
                    .createServerSocket()) {
                listener.bind(new InetSocketAddress("127.0.0.1", 0));
                CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                    try (Socket socket = listener.accept()) {
                        ((SSLSocket) socket).startHandshake();
                        socket.getOutputStream().write(42);
                        socket.getOutputStream().flush();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
                X509Certificate[] seen = new X509Certificate[1];
                SSLContext client = SSLContext.getInstance("TLS");
                client.init(null, new TrustManager[] {new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String a) { }
                    @Override public void checkServerTrusted(X509Certificate[] chain, String a) {
                        seen[0] = chain[0];
                    }
                    @Override public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }}, null);
                try (SSLSocket socket = (SSLSocket) client.getSocketFactory()
                        .createSocket("127.0.0.1", listener.getLocalPort())) {
                    socket.setEnabledProtocols(new String[] {protocol});
                    socket.startHandshake();
                    assertEquals(protocol, socket.getSession().getProtocol());
                    assertEquals(42, socket.getInputStream().read());
                }
                accepted.get(10, TimeUnit.SECONDS);
                assertArrayEquals(generated.certificate().getEncoded(), seen[0].getEncoded(),
                        "the presented certificate is the pinned one");
            }
        }
    }

    @Test
    void derEncodesTheShapesTheCertificateUses() {
        assertEquals("06082a8648ce3d040302", hex(Der.oid("1.2.840.10045.4.3.2")));
        assertEquals("0603550403", hex(Der.oid("2.5.4.3")));
        assertEquals("020102", hex(Der.integer(BigInteger.TWO)));
        assertEquals("02020080", hex(Der.integer(BigInteger.valueOf(128))));
        assertEquals("0c0161", hex(Der.utf8String("a")));
        assertEquals("030200ff", hex(Der.bitString(new byte[] {(byte) 0xFF})));
        assertEquals("a003020102", hex(Der.explicit(0, Der.integer(BigInteger.TWO))));
        assertEquals("30" + "7f", hex(Der.tagged(0x30, new byte[127])).substring(0, 4));
        assertEquals("308180", hex(Der.tagged(0x30, new byte[128])).substring(0, 6));
        assertEquals("30820100", hex(Der.tagged(0x30, new byte[256])).substring(0, 8));
        // RFC 5280 §4.1.2.5: UTCTime through 2049, GeneralizedTime from 2050.
        assertEquals("170d" + hex("491231235959Z".getBytes()),
                hex(Der.time(Instant.parse("2049-12-31T23:59:59Z"))));
        assertEquals("180f" + hex("20500101000000Z".getBytes()),
                hex(Der.time(Instant.parse("2050-01-01T00:00:00Z"))));
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
