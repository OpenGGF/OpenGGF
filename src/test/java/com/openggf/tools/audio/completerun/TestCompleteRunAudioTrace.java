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
        assertNotEquals(new OwnerRef(OwnerClass.SFX, "sfx.explosion", 0xC0,
                        OwnerOrigin.REQUEST, 7),
                new OwnerRef(OwnerClass.SFX, "sfx.explosion", 0xC0,
                        OwnerOrigin.REQUEST, 8));
    }

    @Test
    void baselineAndRequestOriginsCannotCollideAtTheSameNumericOrdinal() {
        OwnerRef baseline = new OwnerRef(OwnerClass.MUSIC, "music.81", 0x81,
                OwnerOrigin.BASELINE, 0);
        OwnerRef request = new OwnerRef(OwnerClass.MUSIC, "music.81", 0x81,
                OwnerOrigin.REQUEST, 0);

        assertNotEquals(baseline, request);
    }

    @Test
    void ownerOriginAndIdentityShapeMustAgree() {
        assertThrows(IllegalArgumentException.class, () -> new OwnerRef(
                OwnerClass.NONE, "none", 0, OwnerOrigin.REQUEST, 0));
        assertThrows(IllegalArgumentException.class, () -> new OwnerRef(
                OwnerClass.SFX, "sfx.explosion", 0xc0, OwnerOrigin.NONE, -1));
        assertThrows(IllegalArgumentException.class, () -> new OwnerRef(
                OwnerClass.SFX, "sfx.explosion", 0xc0, OwnerOrigin.REQUEST, -1));
    }

    @Test
    void baselineCarriesAnExplicitOwnerForEveryHardwareRole() {
        OwnerRef music = new OwnerRef(OwnerClass.MUSIC, "music.81", 0x81,
                OwnerOrigin.BASELINE, 0);
        Baseline baseline = new Baseline(860,
                new NormalizedState(List.of(new StateField("tempo", 1)), List.of(
                        new RoleState(HardwareRole.FM1, true,
                                List.of(new StateField("cursor", 4))),
                        new RoleState(HardwareRole.PSG1, false, List.of()))),
                List.of(new RoleOwner(HardwareRole.FM1, music),
                        new RoleOwner(HardwareRole.PSG1,
                                new OwnerRef(OwnerClass.NONE, "none", 0,
                                        OwnerOrigin.NONE, -1))));

        assertEquals(music, baseline.roleOwners().getFirst().owner());
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
                () -> new OwnerRef(OwnerClass.SFX, "", 0xC0, OwnerOrigin.REQUEST, 7));
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
    }

    @Test
    void metadataRejectsAnOtherwiseMatchingProfileWithWrongFixture() {
        CompleteRunFixture wrongFixture = new CompleteRunFixture(
                "1123456789abcdef0123456789abcdef01234567", "89abcdef",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 862,
                "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
                List.of(new ManifestSegment("green-hill", 860, 862)), 860, 862);
        CompleteRunAudioProfile wrongFixtureProfile = new TestProfile("test.profile", wrongFixture,
                List.of(HardwareRole.FM1, HardwareRole.PSG1), List.of("tempo"), List.of("cursor"));

        assertThrows(IllegalArgumentException.class, () -> fixture.metadata.validateProfile(wrongFixtureProfile));
    }

    @Test
    void metadataRejectsAnOtherwiseMatchingProfileWithWrongStateInventory() {
        CompleteRunAudioProfile wrongInventory = new TestProfile("test.profile", fixture.fixture,
                List.of(HardwareRole.FM1, HardwareRole.PSG1), List.of("tempo_changed"), List.of("cursor"));

        assertThrows(IllegalArgumentException.class, () -> fixture.metadata.validateProfile(wrongInventory));
    }

    @Test
    void metadataRejectsAnOtherwiseMatchingProfileWithWrongHardwareRoles() {
        CompleteRunAudioProfile wrongRoles = new TestProfile("test.profile", fixture.fixture,
                List.of(HardwareRole.FM1), List.of("tempo"), List.of("cursor"));

        assertThrows(IllegalArgumentException.class, () -> fixture.metadata.validateProfile(wrongRoles));
    }

    @Test
    void metadataRejectsAnOtherwiseMatchingProfileWithWrongProducerRuntimeIdentity() {
        TestProfile wrongRuntime = new TestProfile("test.profile", fixture.fixture,
                List.of(HardwareRole.FM1, HardwareRole.PSG1), List.of("tempo"), List.of("cursor"));
        wrongRuntime.producerIdentities.put(ProducerKind.OPENGGF, new ProducerRuntimeIdentity(
                "OpenGGF", "different", "OpenGGF", "0.6", "SMPS", "1",
                Map.of(RuntimeArtifact.OPENGGF_PRODUCER, "4".repeat(64))));

        assertThrows(IllegalArgumentException.class, () -> fixture.metadata.validateProfile(wrongRuntime));
    }

    @Test
    void metadataRejectsObserverProofOutsideTheExactProducerSpecificProfileContract() {
        TestProfile wrongObserver = new TestProfile("test.profile", fixture.fixture,
                List.of(HardwareRole.FM1, HardwareRole.PSG1), List.of("tempo"), List.of("cursor"));
        wrongObserver.observerProofs.put(ProducerKind.OPENGGF,
                new ObserverProof("different.observer.v2", "java.different-domain",
                        List.of(new CallbackProof("different.site", 2))));

        assertThrows(IllegalArgumentException.class,
                () -> fixture.metadata.validateProfile(wrongObserver));
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
    void producerRuntimeIdentityRequiresKindSpecificArtifactsAndCanonicalHashes() {
        assertDoesNotThrow(() -> fixture.referenceRuntimeIdentity());
        assertDoesNotThrow(() -> fixture.openGgfRuntimeIdentity());
        ProducerRuntimeIdentity missingGpgx = new ProducerRuntimeIdentity(
                "BizHawk", "2.11", "Genesis Plus GX", "1.0", "GPGX", "1.0",
                Map.of(RuntimeArtifact.BIZHAWK_EXECUTABLE, "a".repeat(64),
                        RuntimeArtifact.BIZHAWK_CORE_DLL, "b".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> missingGpgx.validateFor(ProducerKind.REFERENCE));
        assertThrows(IllegalArgumentException.class, () -> new ProducerRuntimeIdentity(
                "OpenGGF", "0.6", "OpenGGF", "0.6", "SMPS", "1",
                Map.of(RuntimeArtifact.OPENGGF_PRODUCER, "A".repeat(64))));
    }

    @Test
    void registryRejectsProfileThatOmitsAnAllowedProducerKindIdentity() {
        TestProfile missingReference = new TestProfile("missing.reference.runtime", fixture.fixture,
                List.of(HardwareRole.FM1, HardwareRole.PSG1), List.of("tempo"), List.of("cursor"));
        missingReference.producerIdentities.remove(ProducerKind.REFERENCE);

        assertThrows(IllegalArgumentException.class, () -> CompleteRunAudioProfiles.register(missingReference));
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
        mutable.producerIdentities.clear();

        CompleteRunAudioProfile frozen = CompleteRunAudioProfiles.require(id);
        assertEquals(List.of(HardwareRole.FM1, HardwareRole.PSG1), frozen.hardwareRoles());
        assertEquals(List.of("tempo"), frozen.stateInventory().globalFields());
        assertEquals(new NativeSoundIdentity(OwnerClass.SFX, "sfx.explosion", 0xC0),
                frozen.resolveRequest(new RawAudioRequest(OwnerClass.SFX, 0xC0, "mailbox", 0)));
        assertEquals(fixture.openGgfRuntimeIdentity(),
                frozen.producerRuntimeIdentities().get(ProducerKind.OPENGGF));
        assertThrows(IllegalArgumentException.class, () -> CompleteRunAudioProfiles.register(
                new TestProfile(id, fixture.fixture, List.of(HardwareRole.FM1, HardwareRole.PSG1),
                        List.of("tempo"), List.of("cursor"))));
    }

    @Test
    void lifecycleMapsHaveCanonicalKeyOrder() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("z", 1);
        details.put("a", 2);

        assertEquals(List.of("a", "z"), new Lifecycle(0, 860, "reset", details, List.of())
                .details().keySet().stream().toList());
    }

    @Test
    void lifecycleOwnershipTransitionsRequireCanonicalUniqueRoleOrder() {
        OwnerRef none = new OwnerRef(OwnerClass.NONE, "none", 0, OwnerOrigin.NONE, -1);
        LifecycleOwnership fm1 = new LifecycleOwnership(HardwareRole.FM1, none, none);
        LifecycleOwnership psg1 = new LifecycleOwnership(HardwareRole.PSG1, none, none);

        assertThrows(IllegalArgumentException.class,
                () -> new Lifecycle(0, 860, "reset", Map.of(), List.of(psg1, fm1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Lifecycle(0, 860, "reset", Map.of(), List.of(fm1, fm1)));
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
                ProducerKind.OPENGGF, openGgfRuntimeIdentity(), new ObserverProof("test.observer.v1", "m68k.execute",
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

        private ProducerRuntimeIdentity referenceRuntimeIdentity() {
            return new ProducerRuntimeIdentity("BizHawk", "2.11", "BizHawk", "2.11", "GPGX", "1.0",
                    Map.of(RuntimeArtifact.BIZHAWK_EXECUTABLE, "1".repeat(64),
                            RuntimeArtifact.BIZHAWK_CORE_DLL, "2".repeat(64),
                            RuntimeArtifact.GPGX_CORE, "3".repeat(64)));
        }

        private ProducerRuntimeIdentity openGgfRuntimeIdentity() {
            return new ProducerRuntimeIdentity("OpenGGF", "0.6", "OpenGGF", "0.6", "SMPS", "1",
                    Map.of(RuntimeArtifact.OPENGGF_PRODUCER, "4".repeat(64)));
        }
    }

    private static final class TestProfile implements CompleteRunAudioProfile {
        private final String id;
        private final CompleteRunFixture fixture;
        private final List<HardwareRole> roles;
        private final List<String> globalFields;
        private final List<String> activeRoleFields;
        private final Map<RawAudioRequest, NativeSoundIdentity> identities = new LinkedHashMap<>();
        private final Map<ProducerKind, ProducerRuntimeIdentity> producerIdentities = new LinkedHashMap<>();
        private final Map<ProducerKind, ObserverProof> observerProofs = new LinkedHashMap<>();

        private TestProfile(String id, CompleteRunFixture fixture, List<HardwareRole> roles,
                List<String> globalFields, List<String> activeRoleFields) {
            this.id = id;
            this.fixture = fixture;
            this.roles = roles;
            this.globalFields = globalFields;
            this.activeRoleFields = activeRoleFields;
            identities.put(new RawAudioRequest(OwnerClass.SFX, 0xC0, "mailbox", 0),
                    new NativeSoundIdentity(OwnerClass.SFX, "sfx.explosion", 0xC0));
            producerIdentities.put(ProducerKind.REFERENCE, new ProducerRuntimeIdentity(
                    "BizHawk", "2.11", "BizHawk", "2.11", "GPGX", "1.0",
                    Map.of(RuntimeArtifact.BIZHAWK_EXECUTABLE, "1".repeat(64),
                            RuntimeArtifact.BIZHAWK_CORE_DLL, "2".repeat(64),
                            RuntimeArtifact.GPGX_CORE, "3".repeat(64))));
            producerIdentities.put(ProducerKind.OPENGGF, new ProducerRuntimeIdentity(
                    "OpenGGF", "0.6", "OpenGGF", "0.6", "SMPS", "1",
                    Map.of(RuntimeArtifact.OPENGGF_PRODUCER, "4".repeat(64))));
            observerProofs.put(ProducerKind.REFERENCE,
                    new ObserverProof("reference.observer.v1", "m68k.execute",
                            List.of(new CallbackProof("driver.service", 1))));
            observerProofs.put(ProducerKind.OPENGGF,
                    new ObserverProof("test.observer.v1", "m68k.execute",
                            List.of(new CallbackProof("driver.service", 1))));
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

        @Override
        public Map<ProducerKind, ProducerRuntimeIdentity> producerRuntimeIdentities() {
            return producerIdentities;
        }

        @Override
        public Map<ProducerKind, ObserverProof> observerProofs() {
            return observerProofs;
        }

        @Override
        public Map<NativeSoundIdentity, List<NativeSoundIdentity>> decisionResolutions() {
            NativeSoundIdentity identity = new NativeSoundIdentity(OwnerClass.SFX, "sfx.explosion", 0xC0);
            return Map.of(identity, List.of(identity));
        }

        @Override
        public List<RoleOwner> baselineRoleOwners() {
            OwnerRef none = new OwnerRef(OwnerClass.NONE, "none", 0, OwnerOrigin.NONE, -1);
            return roles.stream().map(role -> new RoleOwner(role, none)).toList();
        }

        @Override
        public Map<String, OwnershipTransition> ownershipTransitions() {
            return Map.of("accepted", OwnershipTransition.ACQUIRE_REQUEST,
                    "rejected", OwnershipTransition.REJECT_PRESERVE);
        }

        @Override
        public PendingRequestPolicy pendingRequestPolicy() {
            return new PendingRequestPolicy(4, 0, null);
        }

        @Override
        public RestoreStackPolicy restoreStackPolicy() {
            return new RestoreStackPolicy(0, List.of(), null);
        }

        @Override
        public Map<String, LifecycleRule> lifecycleRules() {
            return Map.of("pulse", new LifecycleRule("pulse", List.of("payload"),
                    LifecycleOwnershipAction.NONE));
        }
    }
}
