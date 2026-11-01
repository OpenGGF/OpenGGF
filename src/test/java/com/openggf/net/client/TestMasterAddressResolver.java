package com.openggf.net.client;

import com.openggf.net.client.MasterAddressResolver.Explicit;
import com.openggf.net.client.MasterAddressResolver.Named;
import com.openggf.net.client.MasterAddressResolver.Srv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure master-address policy: grammar, RFC 2782 ordering and the same-host port-only profile. */
class TestMasterAddressResolver {
    private static final Named DEV = new Named("multi-dev.openggf.com");
    private static final RandomGenerator FIXED = new SplittableRandom(7);

    @Test
    void bareNamesAreNormalizedAndQueryTheServiceLabel() {
        Named named = assertInstanceOf(Named.class, MasterAddressResolver.parse("  Multi-Dev.OpenGGF.com.  "));
        assertEquals("multi-dev.openggf.com", named.asciiHost());
        assertEquals("_openggf._tcp.multi-dev.openggf.com", named.srvOwner());
        // IDNA names become their ASCII form before any lookup.
        assertEquals("xn--bcher-kva.example",
                assertInstanceOf(Named.class, MasterAddressResolver.parse("bücher.example")).asciiHost());
    }

    @Test
    void explicitUrlsBypassDiscoveryAndDefaultTheMasterPath() {
        assertEquals(URI.create("wss://master.example:8443/master"), assertInstanceOf(Explicit.class,
                MasterAddressResolver.parse("wss://master.example:8443")).endpoint());
        assertEquals(URI.create("wss://master.example/master"), assertInstanceOf(Explicit.class,
                MasterAddressResolver.parse("WSS://master.example/")).endpoint());
        assertEquals(URI.create("ws://127.0.0.1:9000/master"), assertInstanceOf(Explicit.class,
                MasterAddressResolver.parse("ws://127.0.0.1:9000/master")).endpoint());
        assertEquals(URI.create("ws://[::1]:9000/master"), assertInstanceOf(Explicit.class,
                MasterAddressResolver.parse("ws://[::1]:9000/master")).endpoint());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "localhost", "master", "db.localhost", "127.0.0.1", "::1", "[::1]",
            "multi-dev.openggf.com:27910", "multi-dev.openggf.com/master", "-bad.example", "bad-.example",
            "a..example", "ws://example.com/master", "ws://localhost:9/master", "ws://127.1:9/master",
            "wss://user@example.com/master", "wss://example.com/master#x", "wss://example.com/master?token=1",
            "wss://example.com/race", "https://example.com/master", "wss://example.com:0/master",
            "wss://example.com:70000/master"})
    void invalidAddressesAreRejectedWithoutDns(String text) {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> MasterAddressResolver.parse(text), text);
        assertEquals(MasterAddressResolver.INVALID_ADDRESS, rejected.getMessage());
    }

    @Test
    void sameHostRecordsBecomeTypedNameEndpointsIncludingCnameTargets() throws Exception {
        // The published records name the service host itself (a CNAME); only the port changes.
        assertEquals(List.of(URI.create("wss://multi-dev.openggf.com:27910/master")),
                MasterAddressResolver.candidates(DEV, List.of(new Srv(0, 0, 27910, "multi-dev.openggf.com.")), FIXED));
        assertEquals(List.of(URI.create("wss://multi-dev.openggf.com:27910/master")),
                MasterAddressResolver.candidates(DEV, List.of(new Srv(0, 0, 27910, "MULTI-DEV.openggf.com")), FIXED));
    }

    @Test
    void aDifferentTargetIsRejectedNotFollowed() {
        // Broken case: a record steering to another host (e.g. an attacker's certificate) never dials.
        MasterDiscoveryException foreign = assertThrows(MasterDiscoveryException.class,
                () -> MasterAddressResolver.candidates(DEV, List.of(new Srv(0, 0, 443, "evil.example.")), FIXED));
        assertEquals(MasterDiscoveryException.Reason.FOREIGN_TARGET, foreign.reason());
        assertEquals("multi-dev.openggf.com's DNS record points to another host", foreign.getMessage());
        // One foreign record poisons the whole answer rather than being silently skipped.
        assertEquals(MasterDiscoveryException.Reason.FOREIGN_TARGET, assertThrows(MasterDiscoveryException.class,
                () -> MasterAddressResolver.candidates(DEV, List.of(new Srv(0, 0, 27910, "multi-dev.openggf.com."),
                        new Srv(1, 0, 27920, "vidya.jamesj999.co.uk.")), FIXED)).reason());
    }

    @Test
    void absentAndNotOfferedServicesFailClosedWithoutAFallbackPort() {
        MasterDiscoveryException absent = assertThrows(MasterDiscoveryException.class,
                () -> MasterAddressResolver.candidates(DEV, List.of(), FIXED));
        assertEquals(MasterDiscoveryException.Reason.NOT_PUBLISHED, absent.reason());
        assertEquals("No master server is published at multi-dev.openggf.com", absent.getMessage());
        assertEquals(MasterDiscoveryException.Reason.NOT_PUBLISHED, assertThrows(MasterDiscoveryException.class,
                () -> MasterAddressResolver.candidates(DEV, List.of(new Srv(0, 0, 0, ".")), FIXED)).reason());
    }

    @Test
    void malformedAnswersAreLookupFailures() {
        List<List<Srv>> malformed = List.of(
                List.of(new Srv(0, 0, 0, "multi-dev.openggf.com.")),
                List.of(new Srv(0, 0, 65_536, "multi-dev.openggf.com.")),
                List.of(new Srv(-1, 0, 27910, "multi-dev.openggf.com.")),
                List.of(new Srv(0, 0, 0, "."), new Srv(0, 0, 27910, "multi-dev.openggf.com.")),
                nineRecords());
        for (List<Srv> answer : malformed) {
            MasterDiscoveryException failed = assertThrows(MasterDiscoveryException.class,
                    () -> MasterAddressResolver.candidates(DEV, answer, FIXED), answer.toString());
            assertEquals(MasterDiscoveryException.Reason.LOOKUP_FAILED, failed.reason(), answer.toString());
            assertEquals("Could not look up multi-dev.openggf.com", failed.getMessage());
        }
    }

    @Test
    void priorityOrdersBeforeWeightAndDuplicatePortsCollapse() throws Exception {
        List<Srv> answer = List.of(new Srv(20, 0, 3, "multi-dev.openggf.com"),
                new Srv(10, 0, 2, "multi-dev.openggf.com"), new Srv(0, 0, 1, "multi-dev.openggf.com"),
                new Srv(10, 0, 2, "multi-dev.openggf.com"));
        assertEquals(List.of(1, 2, 3), ports(MasterAddressResolver.candidates(DEV, answer, FIXED)));
    }

    @Test
    void weightsFollowRfc2782Proportions() throws Exception {
        List<Srv> answer = List.of(new Srv(0, 1, 1001, "multi-dev.openggf.com"),
                new Srv(0, 3, 1003, "multi-dev.openggf.com"), new Srv(0, 0, 1000, "multi-dev.openggf.com"));
        Map<Integer, Integer> first = new HashMap<>();
        RandomGenerator random = new SplittableRandom(2026);
        int draws = 8000;
        for (int i = 0; i < draws; i++) {
            List<URI> order = MasterAddressResolver.candidates(DEV, answer, random);
            assertEquals(3, order.size());
            first.merge(order.getFirst().getPort(), 1, Integer::sum);
        }
        // Running sums over [0, total]: weight 0 wins only on a zero draw (1 in 5), weight 3 is
        // three times weight 1. Broken control: input order (always 1001 first) fails here.
        double heavy = first.getOrDefault(1003, 0) / (double) draws;
        double light = first.getOrDefault(1001, 0) / (double) draws;
        assertTrue(Math.abs(heavy - 0.6) < 0.03, "weight 3 share " + heavy);
        assertTrue(Math.abs(light - 0.2) < 0.03, "weight 1 share " + light);
    }

    @Test
    void reachabilityClassificationOnlyRetriesPreUpgradeConnectFailures() {
        assertTrue(MasterClient.unreachableBeforeUpgrade(new java.util.concurrent.CompletionException(
                new java.net.ConnectException("refused"))));
        assertTrue(MasterClient.unreachableBeforeUpgrade(new java.net.http.HttpConnectTimeoutException("slow")));
        // Broken cases: certificate, protocol and admission failures never move on.
        for (Throwable terminal : List.of(new javax.net.ssl.SSLHandshakeException("bad certificate"),
                new java.io.IOException("wrapped", new javax.net.ssl.SSLHandshakeException("PKIX")),
                new RaceClient.JoinRejectedException("banned"),
                new java.util.concurrent.TimeoutException("admission"),
                new java.io.IOException("connection reset"))) {
            assertEquals(false, MasterClient.unreachableBeforeUpgrade(terminal), terminal.toString());
        }
    }

    private static List<Srv> nineRecords() {
        List<Srv> records = new ArrayList<>();
        for (int i = 0; i < 9; i++) records.add(new Srv(0, 0, 27_900 + i, "multi-dev.openggf.com"));
        return records;
    }

    private static List<Integer> ports(List<URI> endpoints) {
        return endpoints.stream().map(URI::getPort).toList();
    }
}
