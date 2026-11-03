package com.openggf.net.client;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SRV lookups through the JDK's JNDI DNS provider ({@code jdk.naming.dns}), the platform resolver
 * with no extra dependency. The factory is named explicitly (no provider fallback) and nothing is
 * cached here; the recursive resolver applies record TTLs. NXDOMAIN and a name without SRV data
 * are empty answers; every other naming failure, and exceeding the caller's budget, is an
 * {@link IOException}.
 *
 * <p>JNDI's resolver waits {@value #TIMEOUT_INITIAL_MILLIS} ms, doubling per retry, for
 * {@value #RETRIES} retries per server (about 7 s for one server). A 750 ms initial timeout failed
 * the first cold lookup through systemd-resolved in the 2026-11-01 Linux spike; 1000 ms passed.
 * The caller's budget still caps the whole query.
 *
 * <p>Native images need {@code jdk.naming.dns} in the image and a reflection entry for
 * {@link #FACTORY}; {@code tools/native-image/build.py} adds both.
 */
final class JndiSrvLookup implements MasterAddressResolver.SrvLookup {
    static final String FACTORY = "com.sun.jndi.dns.DnsContextFactory";
    static final String SYSTEM_RESOLVERS = "dns:";
    static final int TIMEOUT_INITIAL_MILLIS = 1000;
    static final int RETRIES = 2;

    private final String providerUrl;

    /** @param providerUrl {@code dns:} for the platform resolvers, or {@code dns://host:port} */
    JndiSrvLookup(String providerUrl) {
        this.providerUrl = Objects.requireNonNull(providerUrl, "providerUrl");
        if (!providerUrl.startsWith("dns:")) throw new IllegalArgumentException("provider must be a dns: URL");
    }

    static JndiSrvLookup system() { return new JndiSrvLookup(SYSTEM_RESOLVERS); }

    @Override
    public List<MasterAddressResolver.Srv> query(String owner, Duration budget) throws IOException {
        Objects.requireNonNull(owner, "owner");
        long millis = Objects.requireNonNull(budget, "budget").toMillis();
        if (millis <= 0) throw new SocketTimeoutException("DNS budget exhausted");
        CompletableFuture<List<MasterAddressResolver.Srv>> lookup = new CompletableFuture<>();
        Thread worker = Thread.ofVirtual().name("master-srv-lookup").start(() -> {
            try {
                lookup.complete(lookupNow(owner));
            } catch (Throwable failure) {
                lookup.completeExceptionally(failure);
            }
        });
        try {
            return lookup.get(millis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            worker.interrupt();
            throw new SocketTimeoutException("DNS lookup exceeded " + millis + " ms");
        } catch (InterruptedException interrupted) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("DNS lookup interrupted");
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("DNS lookup failed", cause);
        }
    }

    private List<MasterAddressResolver.Srv> lookupNow(String owner) throws IOException {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, FACTORY);
        environment.put(Context.PROVIDER_URL, providerUrl);
        environment.put("com.sun.jndi.dns.timeout.initial", Integer.toString(TIMEOUT_INITIAL_MILLIS));
        environment.put("com.sun.jndi.dns.timeout.retries", Integer.toString(RETRIES));
        InitialDirContext context;
        try {
            context = new InitialDirContext(environment);
        } catch (NamingException unavailable) {
            throw new IOException("DNS provider unavailable", unavailable);
        }
        try {
            Attribute srv = context.getAttributes(owner, new String[]{"SRV"}).get("SRV");
            if (srv == null) return List.of();
            List<MasterAddressResolver.Srv> records = new ArrayList<>();
            NamingEnumeration<?> values = srv.getAll();
            while (values.hasMore()) {
                if (records.size() > MasterAddressResolver.MAX_RECORDS) break; // resolver rejects oversize answers
                records.add(parse(String.valueOf(values.next())));
            }
            return List.copyOf(records);
        } catch (NameNotFoundException absent) {
            return List.of();
        } catch (NamingException failed) {
            throw new IOException("DNS lookup failed: " + failed.getClass().getSimpleName(), failed);
        } finally {
            try {
                context.close();
            } catch (NamingException ignored) {
                // nothing is held after the query
            }
        }
    }

    /** RDATA text form: {@code priority weight port target}. */
    static MasterAddressResolver.Srv parse(String value) throws IOException {
        String[] fields = value.strip().split("\\s+");
        if (fields.length != 4) throw new IOException("malformed SRV record");
        try {
            return new MasterAddressResolver.Srv(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2]), fields[3]);
        } catch (NumberFormatException malformed) {
            throw new IOException("malformed SRV record", malformed);
        }
    }
}
