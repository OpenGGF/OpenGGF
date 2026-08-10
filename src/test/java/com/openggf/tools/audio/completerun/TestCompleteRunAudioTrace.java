package com.openggf.tools.audio.completerun;

import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestCompleteRunAudioTrace {
    private final Fixture fixture = new Fixture();

    @Test
    void oneFrameCanContainZeroOrMultipleOrderedServices() {
        var empty = fixture.frame(860, List.of(), List.of());
        var busy = fixture.frame(861, List.of(fixture.request(1)), List.of(
                fixture.service(0), fixture.service(1)));

        assertEquals(List.of(), empty.services());
        assertEquals(List.of(0L, 1L),
                busy.services().stream().map(DriverService::ordinal).toList());
    }

    @Test
    void sameIdOwnersRemainDistinctByRequestOrdinal() {
        assertNotEquals(new OwnerRef(OwnerClass.SFX, "sfx.explosion", 0xC0, 7),
                new OwnerRef(OwnerClass.SFX, "sfx.explosion", 0xC0, 8));
    }

    @Test
    void rejectsSignedOrOutOfRangeChipBytes() {
        assertThrows(IllegalArgumentException.class, () -> new YmWrite(0, -1, 0x22, 0));
        assertThrows(IllegalArgumentException.class, () -> new YmWrite(0, 0, 0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new PsgWrite(0, 0x100));
    }

    @Test
    void profileRejectsUnorderedOrDuplicateRoles() {
        assertThrows(IllegalArgumentException.class,
                () -> fixture.profile.validateState(fixture.state(List.of(HardwareRole.PSG1, HardwareRole.FM1))));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.profile.validateState(fixture.state(List.of(HardwareRole.FM1, HardwareRole.FM1))));
    }

    @Test
    void stateRejectsDuplicateFieldNames() {
        assertThrows(IllegalArgumentException.class, () -> new NormalizedState(List.of(
                new StateField("tempo", 1), new StateField("tempo", 2)), List.of()));
    }

    @Test
    void rejectsEmptyContentKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> new OwnerRef(OwnerClass.SFX, "", 0xC0, 7));
        assertThrows(IllegalArgumentException.class,
                () -> new Request(1, OwnerClass.SFX, " ", 0xC0, "mailbox", 0));
    }

    @Test
    void registryRejectsUnknownProfile() {
        assertThrows(IllegalArgumentException.class,
                () -> CompleteRunAudioProfiles.require("unknown.complete-run.profile"));
    }

    @Test
    void profileRejectsInactiveRolesWithStaleFields() {
        var stale = new NormalizedState(List.of(new StateField("tempo", 1)), List.of(
                new RoleState(HardwareRole.FM1, false, List.of(new StateField("cursor", 4))),
                new RoleState(HardwareRole.PSG1, false, List.of())));

        assertThrows(IllegalArgumentException.class, () -> fixture.profile.validateState(stale));
    }

    @Test
    void profileAcceptsItsCompleteActiveRoleInventory() {
        assertDoesNotThrow(() -> fixture.profile.validateState(new NormalizedState(
                List.of(new StateField("tempo", 1)), List.of(
                        new RoleState(HardwareRole.FM1, true, List.of(new StateField("cursor", 4))),
                        new RoleState(HardwareRole.PSG1, false, List.of())))));
    }

    @Test
    void profileRejectsWrongActiveRoleInventory() {
        var incomplete = new NormalizedState(List.of(new StateField("tempo", 1)), List.of(
                new RoleState(HardwareRole.FM1, true, List.of(new StateField("wrong", 4))),
                new RoleState(HardwareRole.PSG1, false, List.of())));

        assertThrows(IllegalArgumentException.class, () -> fixture.profile.validateState(incomplete));
    }

    @Test
    void metadataBindsThePinnedProfileFixtureAndInventories() {
        assertDoesNotThrow(() -> fixture.metadata.validateProfile(fixture.profile));
        CompleteRunAudioProfile wrongRoles = new TestProfile("test.profile", fixture.fixture,
                List.of(HardwareRole.FM1), List.of("tempo"), List.of("cursor"));

        assertThrows(IllegalArgumentException.class, () -> fixture.metadata.validateProfile(wrongRoles));
    }

    @Test
    void metadataRejectsTerminalWithWrongFrameCountExclusiveEndOrDerivedCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> fixture.metadata.validateTerminal(fixture.terminal(1), fixture.counts(1)));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.metadata.validateTerminal(fixture.terminal(2, 863), fixture.counts(2)));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.metadata.validateTerminal(fixture.terminal(2), fixture.counts(3)));
        assertDoesNotThrow(() -> fixture.metadata.validateTerminal(
                fixture.terminal(2), fixture.counts(2)));
    }

    @Test
    void terminalRequiresCanonicalSha256DigestAndOverflowSafeCountTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> new Terminal(862, 2, 0, 0, 0, 0, 0, 0, "A".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> new Terminal(862, 2, 0, 0, 0, 0, 0, 0, "a"));
        assertThrows(ArithmeticException.class,
                () -> new CaptureCounts(Long.MAX_VALUE, 1, 0, 0, 0, 0, 0).total());
    }

    @Test
    void registrySnapshotsProfileIdentityResolutionAndInventories() {
        String id = "registry.snapshot.profile";
        var mutable = new TestProfile(id, fixture.fixture, new ArrayList<>(List.of(HardwareRole.FM1, HardwareRole.PSG1)),
                new ArrayList<>(List.of("tempo")), new ArrayList<>(List.of("cursor")));
        CompleteRunAudioProfiles.register(mutable);
        mutable.roles.clear();
        mutable.globalFields.clear();
        mutable.identities.clear();

        CompleteRunAudioProfile frozen = CompleteRunAudioProfiles.require(id);
        assertEquals(List.of(HardwareRole.FM1, HardwareRole.PSG1), frozen.hardwareRoles());
        assertEquals(List.of("tempo"), frozen.stateInventory().globalFields());
        assertEquals(new NativeSoundIdentity(OwnerClass.SFX, "sfx.explosion", 0xC0),
                frozen.resolveRequest(new RawAudioRequest(OwnerClass.SFX, 0xC0, "mailbox", 0)));
        assertThrows(IllegalArgumentException.class, () -> CompleteRunAudioProfiles.register(
                new TestProfile(id, fixture.fixture, List.of(HardwareRole.FM1, HardwareRole.PSG1),
                        List.of("tempo"), List.of("cursor"))));
    }

    @Test
    void lifecycleMapsHaveCanonicalKeyOrder() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("z", 1);
        details.put("a", 2);

        assertEquals(List.of("a", "z"), new Lifecycle(0, 860, "reset", details)
                .details().keySet().stream().toList());
    }

    private static final class Fixture {
        private final CompleteRunFixture fixture = new CompleteRunFixture(
                "0123456789abcdef0123456789abcdef01234567", "89abcdef",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 862,
                "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
                List.of(new ManifestSegment("green-hill", 860, 862)), 860, 862);
        private final TestProfile profile = new TestProfile("test.profile", fixture,
                List.of(HardwareRole.FM1, HardwareRole.PSG1), List.of("tempo"), List.of("cursor"));
        private final Metadata metadata = new Metadata("complete_run_audio.v1", "test.profile", fixture,
                ProducerKind.OPENGGF, new ObserverProof("test.observer.v1", "m68k.execute",
                        List.of(new CallbackProof("driver.service", 1))),
                new ChunkPolicy(4096, "gzip", 0), List.of(HardwareRole.FM1, HardwareRole.PSG1),
                new StateInventory(List.of("tempo"), List.of("cursor")));

        private Frame frame(int row, List<Request> requests, List<DriverService> services) {
            return new Frame(row, null, false, requests, services);
        }

        private Request request(long ordinal) {
            return new Request(ordinal, OwnerClass.SFX, "sfx.explosion", 0xC0, "mailbox", 0);
        }

        private DriverService service(long ordinal) {
            return new DriverService(ordinal, "driver", List.of(),
                    state(List.of(HardwareRole.FM1, HardwareRole.PSG1)), List.of());
        }

        private NormalizedState state(List<HardwareRole> roles) {
            return new NormalizedState(List.of(new StateField("tempo", 1)), roles.stream()
                    .map(role -> new RoleState(role, false, List.of()))
                    .toList());
        }

        private Terminal terminal(long frameCount) {
            return terminal(frameCount, 862);
        }

        private Terminal terminal(long frameCount, int exclusiveEnd) {
            return new Terminal(exclusiveEnd, frameCount, 1, 2, 3, 4, 5, 6, "a".repeat(64));
        }

        private CaptureCounts counts(long frameCount) {
            return new CaptureCounts(frameCount, 1, 2, 3, 4, 5, 6);
        }
    }

    private static final class TestProfile implements CompleteRunAudioProfile {
        private final String id;
        private final CompleteRunFixture fixture;
        private final List<HardwareRole> roles;
        private final List<String> globalFields;
        private final List<String> activeRoleFields;
        private final Map<RawAudioRequest, NativeSoundIdentity> identities = new LinkedHashMap<>();

        private TestProfile(String id, CompleteRunFixture fixture, List<HardwareRole> roles,
                List<String> globalFields, List<String> activeRoleFields) {
            this.id = id;
            this.fixture = fixture;
            this.roles = roles;
            this.globalFields = globalFields;
            this.activeRoleFields = activeRoleFields;
            identities.put(new RawAudioRequest(OwnerClass.SFX, 0xC0, "mailbox", 0),
                    new NativeSoundIdentity(OwnerClass.SFX, "sfx.explosion", 0xC0));
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public CompleteRunFixture fixture() {
            return fixture;
        }

        @Override
        public List<HardwareRole> hardwareRoles() {
            return roles;
        }

        @Override
        public StateInventory stateInventory() {
            return new StateInventory(globalFields, activeRoleFields);
        }

        @Override
        public Map<RawAudioRequest, NativeSoundIdentity> nativeSoundIdentities() {
            return identities;
        }
    }
}
