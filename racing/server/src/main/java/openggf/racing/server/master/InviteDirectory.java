package openggf.racing.server.master;

import com.openggf.net.protocol.RoomInviteCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** Broker-loop-confined invitations and rolling identity/address lookup budgets. */
public final class InviteDirectory {
    public static final long LIFETIME_MILLIS = 10 * 60_000L;
    public static final long WINDOW_MILLIS = 60_000;
    public static final int RESOLVE_LIMIT = 5;
    private static final int GENERATION_ATTEMPTS = 32;
    private static final int ISSUANCE_LIMIT = 5;
    private static final int DEFAULT_CAPACITY = 10_000;
    private static final byte[] DIGEST_DOMAIN =
            "OpenGGF room invite\0".getBytes(StandardCharsets.US_ASCII);

    public record Invitation(String code, String instanceId, long epoch, long expiresAtMillis) {
        @Override public String toString() { return "Invitation[redacted]"; }
    }

    public record Resolution(String roomId, SessionRegistry.RegistrationLease lease,
                             String instanceId, long epoch, long expiresAtMillis) { }

    public sealed interface Lookup {
        record Found(Resolution resolution) implements Lookup { }
        record Unavailable() implements Lookup { }
        record RateLimited(long retryAfterMillis) implements Lookup { }
    }

    public static final class UnavailableException extends RuntimeException {
        private final long retryAfterMillis;

        UnavailableException(long retryAfterMillis) {
            super("invite unavailable");
            this.retryAfterMillis = retryAfterMillis;
        }

        public long retryAfterMillis() { return retryAfterMillis; }
    }

    private static final class Registration {
        final String roomId;
        final SessionRegistry.RegistrationLease lease;
        final String instanceId;
        final ArrayDeque<Long> issuances = new ArrayDeque<>();
        long epoch;
        Invitation invitation;
        String previousDigest;

        Registration(String roomId, SessionRegistry.RegistrationLease lease, String instanceId) {
            this.roomId = roomId;
            this.lease = lease;
            this.instanceId = instanceId;
        }
    }

    private static final class RollingBudgets {
        private final int capacity;
        private final Map<String, ArrayDeque<Long>> buckets = new HashMap<>();

        RollingBudgets(int capacity) { this.capacity = capacity; }

        long attempt(String key, long now) {
            ArrayDeque<Long> attempts = buckets.get(key);
            if (attempts == null) {
                prune(now);
                if (buckets.size() >= capacity) {
                    return buckets.values().stream().mapToLong(bucket ->
                            Math.max(1, WINDOW_MILLIS - (now - bucket.peekFirst())))
                            .min().orElse(WINDOW_MILLIS);
                }
                attempts = new ArrayDeque<>();
                buckets.put(key, attempts);
            }
            expire(attempts, now);
            if (attempts.size() >= RESOLVE_LIMIT) {
                return Math.max(1, WINDOW_MILLIS - (now - attempts.peekFirst()));
            }
            attempts.addLast(now);
            return 0;
        }

        void prune(long now) {
            buckets.values().removeIf(attempts -> {
                expire(attempts, now);
                return attempts.isEmpty();
            });
        }
    }

    private final LongSupplier clock;
    private final IntSupplier randomBits;
    private final int capacity;
    private final Map<String, Registration> registrations = new HashMap<>();
    private final Map<String, Registration> index = new HashMap<>();
    private final RollingBudgets identities;
    private final RollingBudgets addresses;

    public InviteDirectory(LongSupplier clock) {
        this(clock, new SecureRandom()::nextInt, DEFAULT_CAPACITY);
    }

    InviteDirectory(LongSupplier clock, IntSupplier randomBits, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("invalid invite capacity");
        this.clock = Objects.requireNonNull(clock);
        this.randomBits = Objects.requireNonNull(randomBits);
        this.capacity = capacity;
        identities = new RollingBudgets(capacity);
        addresses = new RollingBudgets(capacity);
    }

    public void register(String roomId, SessionRegistry.RegistrationLease lease, String instanceId) {
        Objects.requireNonNull(roomId);
        Objects.requireNonNull(lease);
        Objects.requireNonNull(instanceId);
        Registration previous = registrations.get(roomId);
        if (previous != null && previous.lease.equals(lease)
                && previous.instanceId.equals(instanceId)) return;
        if (previous != null && previous.lease.generation() >= lease.generation()) {
            throw new UnavailableException(0);
        }
        if (previous == null && registrations.size() >= capacity) {
            throw new UnavailableException(WINDOW_MILLIS);
        }
        if (previous != null) retire(previous);
        Registration registration = new Registration(roomId, lease, instanceId);
        if (previous != null) registration.previousDigest = previous.previousDigest;
        registrations.put(roomId, registration);
    }

    public Invitation reveal(String roomId, SessionRegistry.RegistrationLease lease) {
        Registration registration = require(roomId, lease);
        long now = clock.getAsLong();
        if (registration.invitation != null
                && now < registration.invitation.expiresAtMillis()) {
            return registration.invitation;
        }
        return issue(registration, now);
    }

    public Invitation rotate(String roomId, SessionRegistry.RegistrationLease lease) {
        return issue(require(roomId, lease), clock.getAsLong());
    }

    public long epoch(String roomId, SessionRegistry.RegistrationLease lease) {
        return require(roomId, lease).epoch;
    }

    public Invitation current(String roomId, SessionRegistry.RegistrationLease lease) {
        Invitation invitation = require(roomId, lease).invitation;
        if (invitation == null || clock.getAsLong() >= invitation.expiresAtMillis()) {
            throw new UnavailableException(0);
        }
        return invitation;
    }

    public long seedEpochFloor(String roomId, SessionRegistry.RegistrationLease lease, long floor) {
        Registration registration = require(roomId, lease);
        if (floor < 0 || floor == Long.MAX_VALUE || registration.invitation != null
                || registration.previousDigest != null) throw new UnavailableException(0);
        registration.epoch = Math.max(registration.epoch, floor);
        return registration.epoch;
    }

    public boolean remove(String roomId, SessionRegistry.RegistrationLease lease) {
        Registration registration = registrations.get(roomId);
        if (registration == null || !registration.lease.equals(lease)) return false;
        registrations.remove(roomId);
        retire(registration);
        return true;
    }

    public Lookup resolve(String rawCode, String identity, String observedAddress) {
        long now = clock.getAsLong();
        long identityRetry = identities.attempt(Objects.requireNonNull(identity), now);
        long addressRetry = addresses.attempt(Objects.requireNonNull(observedAddress), now);
        if (identityRetry > 0 || addressRetry > 0) {
            return new Lookup.RateLimited(Math.max(identityRetry, addressRetry));
        }
        final String canonical;
        try {
            canonical = RoomInviteCode.parse(rawCode).canonical();
        } catch (IllegalArgumentException malformed) {
            return new Lookup.Unavailable();
        }
        Registration registration = index.get(digest(canonical));
        if (registration == null) return new Lookup.Unavailable();
        Invitation invitation = registration.invitation;
        if (invitation == null || now >= invitation.expiresAtMillis()) {
            retire(registration);
            return new Lookup.Unavailable();
        }
        return new Lookup.Found(new Resolution(registration.roomId, registration.lease,
                registration.instanceId, invitation.epoch(), invitation.expiresAtMillis()));
    }

    public boolean isCurrent(Resolution resolution) {
        Registration registration = registrations.get(resolution.roomId());
        return registration != null && registration.lease.equals(resolution.lease())
                && registration.instanceId.equals(resolution.instanceId())
                && registration.epoch == resolution.epoch()
                && registration.invitation != null
                && registration.invitation.expiresAtMillis() == resolution.expiresAtMillis()
                && clock.getAsLong() < resolution.expiresAtMillis();
    }

    public void prune() {
        long now = clock.getAsLong();
        for (Registration registration : registrations.values()) {
            if (registration.invitation != null
                    && now >= registration.invitation.expiresAtMillis()) retire(registration);
        }
        identities.prune(now);
        addresses.prune(now);
    }

    private Registration require(String roomId, SessionRegistry.RegistrationLease lease) {
        Registration registration = registrations.get(roomId);
        if (registration == null || !registration.lease.equals(lease)) {
            throw new UnavailableException(0);
        }
        return registration;
    }

    private Invitation issue(Registration registration, long now) {
        if (registration.epoch == Long.MAX_VALUE) throw new UnavailableException(0);
        expire(registration.issuances, now);
        if (registration.issuances.size() >= ISSUANCE_LIMIT) {
            throw new UnavailableException(Math.max(1,
                    WINDOW_MILLIS - (now - registration.issuances.peekFirst())));
        }
        registration.issuances.addLast(now);
        String currentDigest = registration.invitation == null
                ? registration.previousDigest : digest(registration.invitation.code());
        for (int attempt = 0; attempt < GENERATION_ATTEMPTS; attempt++) {
            String code = encode(randomBits.getAsInt());
            String key = digest(code);
            Registration collision = index.get(key);
            if (collision != null && collision.invitation != null
                    && now >= collision.invitation.expiresAtMillis()) {
                retire(collision);
                collision = null;
            }
            if (collision != null || key.equals(currentDigest)) continue;
            Invitation invitation = new Invitation(code, registration.instanceId,
                    registration.epoch + 1, now + LIFETIME_MILLIS);
            retire(registration);
            registration.invitation = invitation;
            registration.epoch = invitation.epoch();
            index.put(key, registration);
            return invitation;
        }
        throw new UnavailableException(WINDOW_MILLIS);
    }

    private void retire(Registration registration) {
        if (registration.invitation != null) {
            String key = digest(registration.invitation.code());
            index.remove(key, registration);
            registration.previousDigest = key;
            registration.invitation = null;
        }
    }

    private static void expire(ArrayDeque<Long> attempts, long now) {
        while (!attempts.isEmpty() && now - attempts.peekFirst() >= WINDOW_MILLIS) {
            attempts.removeFirst();
        }
    }

    private static String encode(int bits) {
        char[] symbols = new char[RoomInviteCode.LENGTH];
        for (int i = symbols.length - 1; i >= 0; i--) {
            symbols[i] = RoomInviteCode.ALPHABET.charAt(bits & 31);
            bits >>>= 5;
        }
        return new String(symbols);
    }

    private static String digest(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DIGEST_DOMAIN);
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
