package com.openggf.net.client;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Qualification witness for master discovery in packaged builds (the native launcher's
 * {@code --check-master-discovery}): the same parse, JNDI SRV lookup and candidate policy that
 * {@link MasterClient#connect(String, com.openggf.net.identity.PlayerIdentity, String, String,
 * javax.net.ssl.SSLContext)} uses, without any network traffic beyond DNS. Engine tooling only; not
 * part of the Mod API.
 *
 * <p>Origin: hostname-only multiplayer endpoints (2026-11-01).
 */
public final class MasterDiscoveryCheck {
    private MasterDiscoveryCheck() { }

    /**
     * Build-time syntax check of a master address, with the same parser {@code connect} uses and
     * no network I/O.
     *
     * @return the normalized form: the ASCII host name, or the admitted URL
     * @throws IllegalArgumentException when the text is not a usable master address
     */
    public static String requireAddress(String address) {
        return switch (MasterAddressResolver.parse(address)) {
            case MasterAddressResolver.Named named -> named.asciiHost();
            case MasterAddressResolver.Explicit explicit -> explicit.endpoint().toString();
        };
    }

    /** As {@link #requireAddress}, but only a bare multi-label DNS host name is accepted. */
    public static String requireHostName(String hostName) {
        if (!(MasterAddressResolver.parse(hostName) instanceof MasterAddressResolver.Named named)) {
            throw new IllegalArgumentException("Expected a bare DNS host name, not a URL: " + hostName);
        }
        return named.asciiHost();
    }

    /**
     * Resolves {@code hostName} to its dial order.
     *
     * @param providerUrlOrNull {@code dns://host:port} for a specific resolver, or null for the
     *                          platform resolvers
     * @throws MasterDiscoveryException when discovery fails closed
     * @throws IllegalArgumentException when {@code hostName} is not a bare multi-label DNS name
     */
    public static List<URI> resolve(String hostName, String providerUrlOrNull) throws IOException {
        MasterAddressResolver.Named named = new MasterAddressResolver.Named(requireHostName(hostName));
        JndiSrvLookup lookup = providerUrlOrNull == null ? JndiSrvLookup.system() : new JndiSrvLookup(providerUrlOrNull);
        List<MasterAddressResolver.Srv> answer;
        try {
            answer = lookup.query(named.srvOwner(), Duration.ofMillis(MasterClient.DNS_STAGE_MILLIS));
        } catch (IOException failed) {
            throw new MasterDiscoveryException(MasterDiscoveryException.Reason.LOOKUP_FAILED, named.asciiHost(), failed);
        }
        return MasterAddressResolver.candidates(named, answer, RandomGenerator.getDefault());
    }
}
