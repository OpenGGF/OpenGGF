package openggf.racing.server.master;

import com.openggf.net.hub.HubConnection;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestInviteDirectory {
    private long now = 1_000_000;
    private final AtomicInteger bits = new AtomicInteger();
    private final InviteDirectory directory = new InviteDirectory(() -> now, bits::getAndIncrement, 8);
    private final SessionRegistry.RegistrationLease lease = lease(1);

    private static SessionRegistry.RegistrationLease lease(long generation) {
        return new SessionRegistry.RegistrationLease(new HubConnection() {
            @Override public void sendText(String text) { }
            @Override public void sendBinary(byte[] bytes) { }
            @Override public void close(String reason) { }
            @Override public String remoteHost() { return "192.0.2.1"; }
        }, generation);
    }

    private InviteDirectory.Invitation register() {
        directory.register("r-1", lease, "instance-1");
        return directory.reveal("r-1", lease);
    }

    private InviteDirectory.Resolution resolve(String code) {
        return assertInstanceOf(InviteDirectory.Lookup.Found.class,
                directory.resolve(code, "identity", "192.0.2.1")).resolution();
    }

    @Test
    void encodesAllThirtyRandomBitsAsExactlySixBase32Symbols() {
        InviteDirectory.Invitation zero = register();
        assertEquals("000000", zero.code());
        bits.set(0x3fff_ffff);
        assertEquals("ZZZZZZ", directory.rotate("r-1", lease).code());
        bits.set(1 << 29);
        assertEquals("G00000", directory.rotate("r-1", lease).code());
        bits.set(1);
        assertEquals("000001", directory.rotate("r-1", lease).code());
    }

    @Test
    void lookupNormalizesOnlyThroughSharedCodeParser() {
        register();
        bits.set(0x21);
        InviteDirectory.Invitation invitation = directory.rotate("r-1", lease);
        assertEquals("000011", invitation.code());
        assertEquals("r-1", resolve("ooo oIl").roomId());
        assertInstanceOf(InviteDirectory.Lookup.Unavailable.class,
                directory.resolve("００００１１", "identity", "192.0.2.1"));
    }

    @Test
    void activeCollisionRetriesWithoutReplacingTheOtherRoom() {
        ArrayDeque<Integer> samples = new ArrayDeque<>(List.of(7, 7, 8));
        InviteDirectory collisions = new InviteDirectory(() -> now, samples::removeFirst, 8);
        SessionRegistry.RegistrationLease other = lease(2);
        collisions.register("a", lease, "instance-a");
        collisions.register("b", other, "instance-b");
        assertEquals("000007", collisions.reveal("a", lease).code());
        assertEquals("000008", collisions.reveal("b", other).code());
        assertEquals("a", assertInstanceOf(InviteDirectory.Lookup.Found.class,
                collisions.resolve("000007", "id", "ip")).resolution().roomId());
    }

    @Test
    void exhaustedCollisionRetryPreservesActiveInvitation() {
        InviteDirectory.Invitation original = register();
        bits.set(99);
        InviteDirectory stuck = new InviteDirectory(() -> now, () -> 99, 8);
        stuck.register("r-1", lease, "instance");
        InviteDirectory.Invitation first = stuck.reveal("r-1", lease);
        assertThrows(InviteDirectory.UnavailableException.class, () -> stuck.rotate("r-1", lease));
        assertEquals(first, stuck.reveal("r-1", lease));
        assertEquals(original, directory.reveal("r-1", lease));
    }

    @Test
    void revealAndLookupNeverExtendTheAbsoluteLifetime() {
        InviteDirectory.Invitation invitation = register();
        long deadline = now + 600_000;
        now += 599_999;
        assertEquals(invitation, directory.reveal("r-1", lease));
        InviteDirectory.Resolution resolution = resolve(invitation.code());
        assertEquals(deadline, resolution.expiresAtMillis());
        now++;
        assertFalse(directory.isCurrent(resolution));
        assertInstanceOf(InviteDirectory.Lookup.Unavailable.class,
                directory.resolve(invitation.code(), "id", "ip"));
    }

    @Test
    void pruningDoesNotAutomaticallyRenewAnExpiredCode() {
        InviteDirectory.Invitation invitation = register();
        now += 600_000;
        directory.prune();
        assertEquals(1, bits.get());
        assertInstanceOf(InviteDirectory.Lookup.Unavailable.class,
                directory.resolve(invitation.code(), "id", "ip"));
        assertEquals(1, bits.get());
        assertEquals(invitation.epoch(), directory.epoch("r-1", lease));
    }

    @Test
    void explicitRenewalAfterExpiryChangesCodeEpochAndDeadline() {
        InviteDirectory.Invitation previous = register();
        now += 600_000;
        bits.set(0);
        InviteDirectory.Invitation renewed = directory.reveal("r-1", lease);
        assertNotEquals(previous.code(), renewed.code());
        assertEquals(previous.epoch() + 1, renewed.epoch());
        assertEquals(now + 600_000, renewed.expiresAtMillis());
    }

    @Test
    void rotationInvalidatesOutstandingResolutionImmediately() {
        InviteDirectory.Invitation invitation = register();
        InviteDirectory.Resolution resolution = resolve(invitation.code());
        directory.rotate("r-1", lease);
        assertFalse(directory.isCurrent(resolution));
        assertInstanceOf(InviteDirectory.Lookup.Unavailable.class,
                directory.resolve(invitation.code(), "id", "ip"));
    }

    @Test
    void fifthAttemptSucceedsAndSixthIsLimitedRegardlessOfValidity() {
        InviteDirectory.Invitation invitation = register();
        for (int i = 0; i < 4; i++) {
            assertInstanceOf(InviteDirectory.Lookup.Unavailable.class,
                    directory.resolve("?", "id", "ip"));
        }
        assertInstanceOf(InviteDirectory.Lookup.Found.class,
                directory.resolve(invitation.code(), "id", "ip"));
        InviteDirectory.Lookup.RateLimited valid = assertInstanceOf(
                InviteDirectory.Lookup.RateLimited.class,
                directory.resolve(invitation.code(), "id", "ip"));
        InviteDirectory.Lookup.RateLimited invalid = assertInstanceOf(
                InviteDirectory.Lookup.RateLimited.class, directory.resolve("?", "id", "ip"));
        assertEquals(valid, invalid);
        assertEquals(60_000, valid.retryAfterMillis());
    }

    @Test
    void rollingWindowDoesNotResetAtAMinuteBoundary() {
        for (int i = 0; i < 5; i++) {
            directory.resolve("?", "id", "ip");
            now += 10_000;
        }
        now += 9_999;
        assertEquals(1, assertInstanceOf(InviteDirectory.Lookup.RateLimited.class,
                directory.resolve("?", "id", "ip")).retryAfterMillis());
        now++;
        assertInstanceOf(InviteDirectory.Lookup.Unavailable.class, directory.resolve("?", "id", "ip"));
        assertInstanceOf(InviteDirectory.Lookup.RateLimited.class, directory.resolve("?", "id", "ip"));
    }

    @Test
    void identityBudgetSpansAddressesRoomsAndReconnects() {
        register();
        for (int i = 0; i < 5; i++) directory.resolve("?", "id", "ip-" + i);
        assertInstanceOf(InviteDirectory.Lookup.RateLimited.class,
                directory.resolve("000000", "id", "fresh-ip"));
        now += 60_000;
        assertInstanceOf(InviteDirectory.Lookup.Found.class,
                directory.resolve("000000", "id", "fresh-ip"));
    }

    @Test
    void natAndRotatingIdentitiesShareTheObservedSourceBudget() {
        for (int i = 0; i < 5; i++) directory.resolve("?", "id-" + i, "shared-ip");
        assertInstanceOf(InviteDirectory.Lookup.RateLimited.class,
                directory.resolve("?", "new-id", "shared-ip"));
    }

    @Test
    void saturatedTablesShedNewKeysWithoutResettingLiveLimits() {
        InviteDirectory bounded = new InviteDirectory(() -> now, bits::getAndIncrement, 1);
        for (int i = 0; i < 5; i++) bounded.resolve("?", "id", "ip");
        assertInstanceOf(InviteDirectory.Lookup.RateLimited.class,
                bounded.resolve("?", "new-id", "new-ip"));
        assertInstanceOf(InviteDirectory.Lookup.RateLimited.class,
                bounded.resolve("?", "id", "ip"));
        now += 60_000;
        assertInstanceOf(InviteDirectory.Lookup.Unavailable.class,
                bounded.resolve("?", "new-id", "new-ip"));
    }

    @Test
    void staleGenerationCannotDeleteOrRotateAReplacement() {
        InviteDirectory.Invitation original = register();
        InviteDirectory.Resolution old = resolve(original.code());
        SessionRegistry.RegistrationLease replacement = lease(2);
        directory.register("r-1", replacement, "instance-2");
        InviteDirectory.Invitation current = directory.reveal("r-1", replacement);
        assertFalse(directory.remove("r-1", lease));
        assertThrows(InviteDirectory.UnavailableException.class, () -> directory.rotate("r-1", lease));
        assertThrows(InviteDirectory.UnavailableException.class,
                () -> directory.register("r-1", lease, "instance-1"));
        assertFalse(directory.isCurrent(old));
        assertEquals(current, directory.reveal("r-1", replacement));
    }

    @Test
    void duplicateRegistrationIsIdempotentAndCloseRetiresTheAlias() {
        InviteDirectory.Invitation invitation = register();
        directory.register("r-1", lease, "instance-1");
        assertEquals(invitation, directory.reveal("r-1", lease));
        assertTrue(directory.remove("r-1", lease));
        assertInstanceOf(InviteDirectory.Lookup.Unavailable.class,
                directory.resolve(invitation.code(), "id", "ip"));
    }

    @Test
    void liveRegistrationCapacityIsBoundedAndReleasedOnClose() {
        InviteDirectory bounded = new InviteDirectory(() -> now, bits::getAndIncrement, 1);
        bounded.register("a", lease, "instance-a");
        assertThrows(InviteDirectory.UnavailableException.class,
                () -> bounded.register("b", lease(2), "instance-b"));
        bounded.remove("a", lease);
        assertDoesNotThrow(() -> bounded.register("b", lease(2), "instance-b"));
    }

    @Test
    void issuanceIsBoundedAndFailedRotationDoesNotClaimRevocation() {
        InviteDirectory.Invitation original = register();
        InviteDirectory.Invitation current = original;
        for (int i = 0; i < 4; i++) current = directory.rotate("r-1", lease);
        InviteDirectory.Invitation last = current;
        assertEquals(60_000, assertThrows(InviteDirectory.UnavailableException.class,
                () -> directory.rotate("r-1", lease)).retryAfterMillis());
        assertEquals(last, directory.reveal("r-1", lease));
        now += 60_000;
        assertNotEquals(last, directory.rotate("r-1", lease));
    }

    @Test
    void retiredCodesCanBeReusedLaterWithoutAPermanentArchive() {
        register();
        InviteDirectory.Invitation second = directory.rotate("r-1", lease);
        bits.set(0);
        assertEquals("000000", directory.rotate("r-1", lease).code());
        assertEquals("000001", second.code());
    }

    @Test
    void epochFloorIsMonotonicAndCannotBeSeededAfterIssuanceOrRetirement() {
        directory.register("r-1", lease, "instance");
        assertEquals(8, directory.seedEpochFloor("r-1", lease, 8));
        assertEquals(8, directory.seedEpochFloor("r-1", lease, 3));
        assertEquals(9, directory.reveal("r-1", lease).epoch());
        assertThrows(InviteDirectory.UnavailableException.class,
                () -> directory.seedEpochFloor("r-1", lease, 100));
        now += 600_000;
        directory.prune();
        assertThrows(InviteDirectory.UnavailableException.class,
                () -> directory.seedEpochFloor("r-1", lease, 100));
    }

    @Test
    void exhaustedEpochCannotWrapOrReplaceTheExistingCode() {
        directory.register("r-1", lease, "instance");
        assertThrows(InviteDirectory.UnavailableException.class,
                () -> directory.seedEpochFloor("r-1", lease, Long.MAX_VALUE));
        directory.seedEpochFloor("r-1", lease, Long.MAX_VALUE - 1);
        InviteDirectory.Invitation last = directory.reveal("r-1", lease);
        assertEquals(Long.MAX_VALUE, last.epoch());
        assertThrows(InviteDirectory.UnavailableException.class, () -> directory.rotate("r-1", lease));
        assertEquals(last, directory.current("r-1", lease));
    }
}
