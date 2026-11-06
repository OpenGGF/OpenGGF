package com.openggf.net.client;

import com.openggf.net.protocol.NetworkEndpointPolicy;

import java.io.IOException;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

/**
 * Pure master-address policy behind {@link MasterClient#connect(String,
 * com.openggf.net.identity.PlayerIdentity, String, String, javax.net.ssl.SSLContext)}: no DNS here.
 *
 * <p>An address is either an explicit {@code ws(s)://} URL, used as given, or a bare multi-label
 * DNS name whose port comes from {@code _openggf._tcp.<name>} SRV records. Records use the
 * same-host, port-only profile: every target must equal the typed name, so the WebSocket
 * authority, SNI and certificate check all keep the typed name and DNS can change only the port.
 * The published targets are CNAMEs, which RFC 2782 forbids; accepting them is deliberate (the
 * address lookup of the target is the ordinary A/AAAA lookup of the typed name). Different targets
 * are rejected, never followed. Discovery fails closed: no record, a sole {@code .} target and
 * malformed answers are errors, with no fallback port.
 *
 * <p>Origin: hostname-only multiplayer endpoints (2026-11-01).
 */
final class MasterAddressResolver {
    static final String SERVICE_PREFIX = "_openggf._tcp.";
    static final String MASTER_PATH = "/master";
    /** The largest answer considered; larger answers are malformed rather than truncated. */
    static final int MAX_RECORDS = 8;
    static final int MAX_ADDRESS_CHARS = 2048;
    static final String INVALID_ADDRESS = "Invalid master server address";

    private MasterAddressResolver() { }

    sealed interface Address permits Explicit, Named { }

    /** A ws(s) URL, admitted by {@link NetworkEndpointPolicy#requireWebSocket}. */
    record Explicit(URI endpoint) implements Address {
        Explicit { Objects.requireNonNull(endpoint, "endpoint"); }
    }

    /** IDNA ASCII, lower-case, no trailing dot, at least two labels. */
    record Named(String asciiHost) implements Address {
        Named { Objects.requireNonNull(asciiHost, "asciiHost"); }
        String srvOwner() { return SERVICE_PREFIX + asciiHost; }
    }

    record Srv(int priority, int weight, int port, String target) { }

    /** DNS seam. An empty list means NXDOMAIN or no SRV data; failures and timeouts throw. */
    interface SrvLookup {
        List<Srv> query(String owner, Duration budget) throws IOException;
    }

    /** @throws IllegalArgumentException with {@link #INVALID_ADDRESS} for unusable text */
    static Address parse(String address) {
        if (address == null) throw invalid(null);
        String text = address.strip();
        if (text.isEmpty() || text.length() > MAX_ADDRESS_CHARS) throw invalid(null);
        if (text.contains("://")) return new Explicit(explicit(text));
        return new Named(hostName(text));
    }

    private static URI explicit(String text) {
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException malformed) {
            throw invalid(malformed);
        }
        try {
            NetworkEndpointPolicy.requireWebSocket(uri);
        } catch (IllegalArgumentException rejected) {
            throw invalid(rejected);
        }
        if (uri.getRawQuery() != null) throw invalid(null);
        String path = uri.getRawPath();
        if (path == null || path.isEmpty() || path.equals("/")) path = MASTER_PATH;
        // The master's WebSocket handler matches its path exactly.
        if (!path.equals(MASTER_PATH)) throw invalid(null);
        try {
            return new URI(uri.getScheme().toLowerCase(Locale.ROOT), null, uri.getHost(), uri.getPort(),
                    MASTER_PATH, null, null);
        } catch (URISyntaxException impossible) {
            throw invalid(impossible);
        }
    }

    private static String hostName(String text) {
        String ascii;
        try {
            ascii = IDN.toASCII(text, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException malformed) {
            throw invalid(malformed);
        }
        if (ascii.endsWith(".")) ascii = ascii.substring(0, ascii.length() - 1);
        if (ascii.isEmpty() || ascii.length() > 253) throw invalid(null);
        String[] labels = ascii.split("\\.", -1);
        // Single-label names and localhost are local; IP literals need an explicit URL.
        if (labels.length < 2 || ascii.equals("localhost") || ascii.endsWith(".localhost")) throw invalid(null);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 || !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) {
                throw invalid(null);
            }
        }
        if (labels[labels.length - 1].matches("[0-9]+")) throw invalid(null);
        return ascii;
    }

    private static IllegalArgumentException invalid(Throwable cause) {
        return new IllegalArgumentException(INVALID_ADDRESS, cause);
    }

    /**
     * The validated answer as dial order: ascending priority, then RFC 2782 weighted selection
     * within a priority. Duplicate ports collapse to their first position.
     */
    static List<URI> candidates(Named host, List<Srv> answer, RandomGenerator random)
            throws MasterDiscoveryException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(random, "random");
        String name = host.asciiHost();
        if (answer == null || answer.isEmpty()) {
            throw new MasterDiscoveryException(MasterDiscoveryException.Reason.NOT_PUBLISHED, name, null);
        }
        if (answer.size() > MAX_RECORDS) throw malformed(name, "more than " + MAX_RECORDS + " SRV records");
        boolean notOffered = false;
        boolean foreign = false;
        for (Srv record : answer) {
            if (record == null || record.target() == null) throw malformed(name, "missing SRV target");
            String target = normalizeTarget(record.target());
            if (target.isEmpty()) {
                notOffered = true;
                continue;
            }
            if (record.priority() < 0 || record.priority() > 65_535
                    || record.weight() < 0 || record.weight() > 65_535
                    || record.port() < 1 || record.port() > 65_535) {
                throw malformed(name, "SRV field out of range");
            }
            if (!target.equals(name)) foreign = true;
        }
        if (notOffered) {
            // RFC 2782: a sole "." target means the service is decidedly not offered.
            if (answer.size() == 1) {
                throw new MasterDiscoveryException(MasterDiscoveryException.Reason.NOT_PUBLISHED, name, null);
            }
            throw malformed(name, "\".\" target mixed with live records");
        }
        if (foreign) {
            throw new MasterDiscoveryException(MasterDiscoveryException.Reason.FOREIGN_TARGET, name, null);
        }
        TreeMap<Integer, List<Srv>> byPriority = new TreeMap<>();
        for (Srv record : answer) {
            byPriority.computeIfAbsent(record.priority(), ignored -> new ArrayList<>()).add(record);
        }
        LinkedHashSet<Integer> ports = new LinkedHashSet<>();
        for (List<Srv> group : byPriority.values()) {
            for (Srv chosen : weightedOrder(group, random)) ports.add(chosen.port());
        }
        List<URI> endpoints = new ArrayList<>(ports.size());
        for (int port : ports) {
            try {
                endpoints.add(new URI("wss", null, name, port, MASTER_PATH, null, null));
            } catch (URISyntaxException impossible) {
                throw malformed(name, "unusable endpoint");
            }
        }
        return List.copyOf(endpoints);
    }

    /** RFC 2782 selection without replacement: zero weights first, then a running-sum draw. */
    static List<Srv> weightedOrder(List<Srv> group, RandomGenerator random) {
        List<Srv> remaining = new ArrayList<>();
        for (Srv record : group) if (record.weight() == 0) remaining.add(record);
        for (Srv record : group) if (record.weight() != 0) remaining.add(record);
        List<Srv> ordered = new ArrayList<>(remaining.size());
        while (!remaining.isEmpty()) {
            long total = 0;
            for (Srv record : remaining) total += record.weight();
            long draw = random.nextLong(total + 1);
            long running = 0;
            int pick = remaining.size() - 1;
            for (int i = 0; i < remaining.size(); i++) {
                running += remaining.get(i).weight();
                if (running >= draw) {
                    pick = i;
                    break;
                }
            }
            ordered.add(remaining.remove(pick));
        }
        return ordered;
    }

    static String normalizeTarget(String target) {
        String value = target.strip().toLowerCase(Locale.ROOT);
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

    private static MasterDiscoveryException malformed(String host, String detail) {
        return new MasterDiscoveryException(MasterDiscoveryException.Reason.LOOKUP_FAILED, host,
                new IOException("malformed SRV answer: " + detail));
    }
}
