package com.openggf.tools.audio.completerun;

import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestCompleteRunAudioTrace {
    private static final String TEST_ABI_NAME = CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_ABI_NAME;
    private static final int TEST_ABI_VERSION = CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_ABI_VERSION;
    private static final int TEST_EVENT_SIZE = CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_EVENT_SIZE;
    private static final int TEST_CAPACITY = CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_CAPACITY;
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
    void metadataPinsAProducerSpecificTypedObserverRuntimeIdentity() {
        assertDoesNotThrow(() -> fixture.metadata.validateProfile(fixture.profile));
        TestProfile wrongIdentity = new TestProfile("test.profile", fixture.fixture,
                List.of(HardwareRole.FM1, HardwareRole.PSG1), List.of("tempo"), List.of("cursor"));
        wrongIdentity.observerRuntimeIdentities.put(ProducerKind.OPENGGF,
                new CallbackObserverIdentity("different.callback.v1"));

        assertThrows(IllegalArgumentException.class,
                () -> fixture.metadata.validateProfile(wrongIdentity));
    }

    @Test
    void bufferedObserverIdentityFailsClosedOnEveryRuntimeBound() {
        String digest = "a".repeat(64);
        assertThrows(IllegalArgumentException.class, () -> new BufferedNativeObserverIdentity(
                TEST_ABI_NAME, TEST_ABI_VERSION, TEST_EVENT_SIZE, TEST_CAPACITY,
                "bizhawk-2.11-gpgx-audio-observer-v1", "gpgx-audio-observer-v1", "9f0e01c17bf47019",
                digest, digest, false, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BufferedNativeObserverIdentity(
                TEST_ABI_NAME, TEST_ABI_VERSION, TEST_EVENT_SIZE, TEST_CAPACITY,
                "bizhawk-2.11-gpgx-audio-observer-v1", "gpgx-audio-observer-v1", "9f0e01c17bf47019",
                digest, digest, true, TEST_CAPACITY + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BufferedNativeObserverIdentity(
                TEST_ABI_NAME, TEST_ABI_VERSION, TEST_EVENT_SIZE, TEST_CAPACITY,
                "bizhawk-2.11-gpgx-audio-observer-v1", "gpgx-audio-observer-v1", "9f0e01c17bf47019",
                digest, digest, true, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BufferedNativeObserverIdentity(
                TEST_ABI_NAME, TEST_ABI_VERSION, TEST_EVENT_SIZE, TEST_CAPACITY,
                "/tmp/observer", "gpgx-audio-observer-v1", "9f0e01c17bf47019",
                digest, digest, true, 1, 0));
    }

    @Test
    void managedAdapterAndObserverArtifactsMustMatchExactly() {
        String digest = "a".repeat(64);
        Map<RuntimeArtifact, String> nativeArtifacts = new LinkedHashMap<>();
        for (RuntimeArtifact artifact : List.of(RuntimeArtifact.BIZHAWK_EXECUTABLE,
                RuntimeArtifact.BIZHAWK_CORE_DLL, RuntimeArtifact.GPGX_CORE,
                RuntimeArtifact.BIZHAWK_COMMON_DLL, RuntimeArtifact.WATERBOX_HOST,
                RuntimeArtifact.GPGX_CORE_UNCOMPRESSED, RuntimeArtifact.GPGX_OBSERVER_PATCH,
                RuntimeArtifact.GPGX_OBSERVER_SOURCE_BUNDLE, RuntimeArtifact.GPGX_OBSERVER_TOOLCHAIN,
                RuntimeArtifact.GPGX_OBSERVER_BUILD_RECIPE, RuntimeArtifact.GPGX_OBSERVER_IDENTITY,
                RuntimeArtifact.GPGX_OBSERVER_ADAPTER_SOURCE, RuntimeArtifact.GPGX_HOST_SOURCE,
                RuntimeArtifact.BIZHAWK_BIZINVOKE_DLL, RuntimeArtifact.BIZHAWK_BASE_COMMON_DLL)) {
            nativeArtifacts.put(artifact, digest);
        }
        BufferedNativeObserverIdentity nativeIdentity = new BufferedNativeObserverIdentity(
                TEST_ABI_NAME, TEST_ABI_VERSION, TEST_EVENT_SIZE, TEST_CAPACITY,
                "bizhawk-2.11-gpgx-audio-observer-v1", "gpgx-audio-observer-v1", "9f0e01c17bf47019",
                digest, digest, true, 1, 0);
        ProducerRuntimeIdentity reflection = new ProducerRuntimeIdentity(
                "BizHawk", "2.11", "BizHawk", "2.11", "GPGX", "1.0",
                ManagedObserverAdapter.REFLECTION, nativeArtifacts);
        assertDoesNotThrow(() -> reflection.validateFor(ProducerKind.REFERENCE, nativeIdentity));

        nativeArtifacts.put(RuntimeArtifact.BIZHAWK_OBSERVER_MANAGED_PATCH, digest);
        assertThrows(IllegalArgumentException.class, () -> new ProducerRuntimeIdentity(
                "BizHawk", "2.11", "BizHawk", "2.11", "GPGX", "1.0",
                ManagedObserverAdapter.REFLECTION, nativeArtifacts));
        nativeArtifacts.put(RuntimeArtifact.BIZHAWK_OBSERVER_CORES_DLL, digest);
        ProducerRuntimeIdentity firstClass = new ProducerRuntimeIdentity(
                "BizHawk", "2.11", "BizHawk", "2.11", "GPGX", "1.0",
                ManagedObserverAdapter.FIRST_CLASS, nativeArtifacts);
        assertDoesNotThrow(() -> firstClass.validateFor(ProducerKind.REFERENCE, nativeIdentity));
    }

    @Test
    void callbackObserverMetadataHasIndependentCanonicalJsonAndStrictParserGates() throws Exception {
        String canonical = """
                {"schema":"complete_run_audio.v1","profileId":"test.profile","fixture":{"romSha1":"0123456789abcdef0123456789abcdef01234567","romCrc32":"89abcdef","bk2Sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","bk2RowCount":862,"runManifestSha256":"fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210","segments":[{"id":"green-hill","firstFrame":860,"exclusiveEnd":862}],"firstFrame":860,"exclusiveEnd":862},"producerKind":"OPENGGF","producerRuntimeIdentity":{"producerName":"OpenGGF","producerVersion":"0.6","emulatorName":"OpenGGF","emulatorVersion":"0.6","coreName":"SMPS","coreVersion":"1","observerAdapter":"CALLBACK_ONLY","artifactSha256":{"OPENGGF_PRODUCER":"4444444444444444444444444444444444444444444444444444444444444444"}},"observerRuntimeIdentity":{"kind":"CALLBACK","id":"openggf.callback.v1"},"observerProof":{"observerProfile":"test.observer.v1","callbackSource":"m68k.execute","callbacks":[{"callback":"driver.service","observations":1}]},"chunkPolicy":{"frameRows":4096,"compression":"gzip","gzipTimestamp":0},"hardwareRoles":["FM1","PSG1"],"stateInventory":{"globalFields":["tempo"],"activeRoleFields":["cursor"]}}""";

        assertEquals(canonical, CompleteRunAudioJson.writeMetadata(fixture.metadata));
        assertEquals(fixture.metadata, readMetadata(canonical));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"kind\":\"CALLBACK\"", "\"kind\":\"UNKNOWN\"")));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"observerRuntimeIdentity\":{\"kind\":\"CALLBACK\",\"id\":\"openggf.callback.v1\"},", "")));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"id\":\"openggf.callback.v1\"",
                        "\"id\":\"openggf.callback.v1\",\"id\":\"duplicate\"")));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"observerAdapter\":\"CALLBACK_ONLY\"",
                        "\"observerAdapter\":\"REFLECTION\"")));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"id\":\"openggf.callback.v1\"",
                        "\"id\":\"openggf.callback.v1\",\"unknown\":0")));
    }

    @Test
    void metadataReaderRejectsDuplicateRuntimeArtifactKeysWithoutParserDuplicateDetection() throws Exception {
        String canonical = CompleteRunAudioJson.writeMetadata(fixture.metadata);
        String duplicate = canonical.replace(
                "\"OPENGGF_PRODUCER\":\"4444444444444444444444444444444444444444444444444444444444444444\"",
                "\"OPENGGF_PRODUCER\":\"4444444444444444444444444444444444444444444444444444444444444444\","
                        + "\"OPENGGF_PRODUCER\":\"5555555555555555555555555555555555555555555555555555555555555555\"");

        try (var parser = new com.fasterxml.jackson.core.JsonFactory().createParser(duplicate)) {
            parser.nextToken();
            assertThrows(IllegalArgumentException.class, () -> CompleteRunAudioJson.readMetadata(parser));
        }
    }

    @Test
    void bufferedObserverMetadataHasIndependentCanonicalJsonAndStrictParserGates() throws Exception {
        String digest = "a".repeat(64);
        Map<RuntimeArtifact, String> artifacts = new EnumMap<>(RuntimeArtifact.class);
        artifacts.put(RuntimeArtifact.BIZHAWK_EXECUTABLE, digest);
        artifacts.put(RuntimeArtifact.BIZHAWK_CORE_DLL, digest);
        artifacts.put(RuntimeArtifact.BIZHAWK_COMMON_DLL, digest);
        artifacts.put(RuntimeArtifact.WATERBOX_HOST, digest);
        artifacts.put(RuntimeArtifact.GPGX_CORE, CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_CORE_SHA256);
        artifacts.put(RuntimeArtifact.GPGX_CORE_UNCOMPRESSED,
                CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_CORE_UNCOMPRESSED_SHA256);
        artifacts.put(RuntimeArtifact.GPGX_OBSERVER_PATCH,
                CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_PATCH_SHA256);
        artifacts.put(RuntimeArtifact.GPGX_OBSERVER_SOURCE_BUNDLE,
                CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_SOURCE_BUNDLE_SHA256);
        artifacts.put(RuntimeArtifact.GPGX_OBSERVER_TOOLCHAIN,
                CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_TOOLCHAIN_SHA256);
        artifacts.put(RuntimeArtifact.GPGX_OBSERVER_BUILD_RECIPE,
                CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_BUILD_RECIPE_SHA256);
        artifacts.put(RuntimeArtifact.GPGX_OBSERVER_IDENTITY,
                CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_IDENTITY_SHA256);
        artifacts.put(RuntimeArtifact.GPGX_OBSERVER_ADAPTER_SOURCE,
                CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_ADAPTER_SOURCE_SHA256);
        artifacts.put(RuntimeArtifact.GPGX_HOST_SOURCE,
                CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_HOST_SOURCE_SHA256);
        artifacts.put(RuntimeArtifact.BIZHAWK_BIZINVOKE_DLL,
                CompleteRunAudioProfiles.BIZHAWK_BIZINVOKE_SHA256);
        artifacts.put(RuntimeArtifact.BIZHAWK_BASE_COMMON_DLL,
                CompleteRunAudioProfiles.BIZHAWK_BASE_COMMON_SHA256);
        Metadata metadata = new Metadata(SCHEMA, "test.profile", fixture.fixture, ProducerKind.REFERENCE,
                new ProducerRuntimeIdentity("BizHawk", "2.11", "BizHawk", "2.11", "GPGX", "1.0",
                        ManagedObserverAdapter.REFLECTION, artifacts),
                new BufferedNativeObserverIdentity(TEST_ABI_NAME, TEST_ABI_VERSION,
                        TEST_EVENT_SIZE, TEST_CAPACITY,
                        "bizhawk-2.11-gpgx-audio-observer-v1", "gpgx-audio-observer-v1",
                        CompleteRunAudioProfiles.GPGX_AUDIO_TRACE_CORE_BUILD_ID,
                        "b".repeat(64), "c".repeat(64), true, 1, 0),
                new ObserverProof("reference.observer.v1", "native.buffer",
                        List.of(new CallbackProof("driver.service", 1))),
                new ChunkPolicy(4096, "gzip", 0), List.of(HardwareRole.FM1, HardwareRole.PSG1),
                new StateInventory(List.of("tempo"), List.of("cursor")));
        String canonical = """
{"schema":"complete_run_audio.v1","profileId":"test.profile","fixture":{"romSha1":"0123456789abcdef0123456789abcdef01234567","romCrc32":"89abcdef","bk2Sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","bk2RowCount":862,"runManifestSha256":"fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210","segments":[{"id":"green-hill","firstFrame":860,"exclusiveEnd":862}],"firstFrame":860,"exclusiveEnd":862},"producerKind":"REFERENCE","producerRuntimeIdentity":{"producerName":"BizHawk","producerVersion":"2.11","emulatorName":"BizHawk","emulatorVersion":"2.11","coreName":"GPGX","coreVersion":"1.0","observerAdapter":"REFLECTION","artifactSha256":{"BIZHAWK_EXECUTABLE":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","BIZHAWK_CORE_DLL":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","BIZHAWK_COMMON_DLL":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","WATERBOX_HOST":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","GPGX_CORE":"ba276573fc7802fb2313c051471dbdd664959c06aaafa6ef73564799886d083f","GPGX_CORE_UNCOMPRESSED":"7807b57ffdfa303465ec2a2e707a5aacc38bd56cd10e201aca2965620eb71fb2","GPGX_OBSERVER_PATCH":"45d85fc19405457c788be4f6c17d2b14281d33fbff163cd42eead76e08f7f6d2","GPGX_OBSERVER_SOURCE_BUNDLE":"abd68651d633a0a75d01cb9569cfb9dc15da4a7540eb072fc2d8eb11e548ed0e","GPGX_OBSERVER_TOOLCHAIN":"9caa5c02dcd2d9c01e5d0196956787a0f31760195c6544a2ceafcb771f469521","GPGX_OBSERVER_BUILD_RECIPE":"eb58429b3b0bb47b337c60055d849f917842b8e973083d23261bdb2e04783d99","GPGX_OBSERVER_IDENTITY":"f3721d457aa867559d6ebad16111a4a1d737b9187c8655b144788a685d869e28","GPGX_OBSERVER_ADAPTER_SOURCE":"770dfcfef0fabc2eb7211add26d7a3716e33b75ddbe7dd3d7ba1568c8cb3a102","GPGX_HOST_SOURCE":"052090e4a93c6614f3c4465526c47876779dc40ded1897d0cc4d24c3c04ed497","BIZHAWK_BIZINVOKE_DLL":"8d05389bf0e02be1244bdc7a2adcd93b4cff95acf199fc927987ca699760a1b7","BIZHAWK_BASE_COMMON_DLL":"438a49d6a45d9fcac17016240ae205d1af7a4632865f6f70468b684b82323f33"}},"observerRuntimeIdentity":{"kind":"BUFFERED_NATIVE","abiName":"gpgx.audio-trace.v1","abiVersion":1,"eventSize":32,"capacity":65536,"installationId":"bizhawk-2.11-gpgx-audio-observer-v1","coreId":"gpgx-audio-observer-v1","coreBuildId":"8e822239d27df092","watchMaskSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","serviceManifestSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","enabled":true,"maximumFrameOccupancy":1,"overflowCount":0},"observerProof":{"observerProfile":"reference.observer.v1","callbackSource":"native.buffer","callbacks":[{"callback":"driver.service","observations":1}]},"chunkPolicy":{"frameRows":4096,"compression":"gzip","gzipTimestamp":0},"hardwareRoles":["FM1","PSG1"],"stateInventory":{"globalFields":["tempo"],"activeRoleFields":["cursor"]}}""";

        assertEquals(canonical, CompleteRunAudioJson.writeMetadata(metadata));
        assertEquals(metadata, readMetadata(canonical));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"eventSize\":32,", "")));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"capacity\":65536",
                        "\"capacity\":65536,\"capacity\":65536")));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"enabled\":true", "\"enabled\":false")));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"maximumFrameOccupancy\":1",
                        "\"maximumFrameOccupancy\":2000009")));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"overflowCount\":0", "\"overflowCount\":1")));
        assertThrows(IllegalArgumentException.class,
                () -> readMetadata(canonical.replace("\"overflowCount\":0}",
                        "\"overflowCount\":0,\"unknown\":0}")));
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
    void registryRejectsProfileThatOmitsAnObserverRuntimeIdentity() {
        TestProfile missingReference = new TestProfile("missing.reference.observer", fixture.fixture,
                List.of(HardwareRole.FM1, HardwareRole.PSG1), List.of("tempo"), List.of("cursor"));
        missingReference.observerRuntimeIdentities.remove(ProducerKind.REFERENCE);

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
        mutable.observerRuntimeIdentities.clear();

        CompleteRunAudioProfile frozen = CompleteRunAudioProfiles.require(id);
        assertEquals(List.of(HardwareRole.FM1, HardwareRole.PSG1), frozen.hardwareRoles());
        assertEquals(List.of("tempo"), frozen.stateInventory().globalFields());
        assertEquals(new NativeSoundIdentity(OwnerClass.SFX, "sfx.explosion", 0xC0),
                frozen.resolveRequest(new RawAudioRequest(OwnerClass.SFX, 0xC0, "mailbox", 0)));
        assertEquals(fixture.openGgfRuntimeIdentity(),
                frozen.producerRuntimeIdentities().get(ProducerKind.OPENGGF));
        assertEquals(new CallbackObserverIdentity("openggf.callback.v1"),
                frozen.observerRuntimeIdentities().get(ProducerKind.OPENGGF));
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
                ProducerKind.OPENGGF, openGgfRuntimeIdentity(), new CallbackObserverIdentity("openggf.callback.v1"),
                new ObserverProof("test.observer.v1", "m68k.execute",
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
                    ManagedObserverAdapter.CALLBACK_ONLY,
                    Map.of(RuntimeArtifact.BIZHAWK_EXECUTABLE, "1".repeat(64),
                            RuntimeArtifact.BIZHAWK_CORE_DLL, "2".repeat(64),
                            RuntimeArtifact.GPGX_CORE, "3".repeat(64)));
        }

        private ProducerRuntimeIdentity openGgfRuntimeIdentity() {
            return new ProducerRuntimeIdentity("OpenGGF", "0.6", "OpenGGF", "0.6", "SMPS", "1",
                    ManagedObserverAdapter.CALLBACK_ONLY,
                    Map.of(RuntimeArtifact.OPENGGF_PRODUCER, "4".repeat(64)));
        }
    }

    private static Metadata readMetadata(String json) {
        try (var parser = CompleteRunAudioJson.FACTORY.createParser(json)) {
            parser.nextToken();
            Metadata metadata = CompleteRunAudioJson.readMetadata(parser);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("metadata JSON contains trailing tokens");
            }
            return metadata;
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("invalid metadata JSON", failure);
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
        private final Map<ProducerKind, ObserverRuntimeIdentity> observerRuntimeIdentities = new LinkedHashMap<>();

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
                    ManagedObserverAdapter.CALLBACK_ONLY,
                    Map.of(RuntimeArtifact.BIZHAWK_EXECUTABLE, "1".repeat(64),
                            RuntimeArtifact.BIZHAWK_CORE_DLL, "2".repeat(64),
                            RuntimeArtifact.GPGX_CORE, "3".repeat(64))));
            producerIdentities.put(ProducerKind.OPENGGF, new ProducerRuntimeIdentity(
                    "OpenGGF", "0.6", "OpenGGF", "0.6", "SMPS", "1",
                    ManagedObserverAdapter.CALLBACK_ONLY,
                    Map.of(RuntimeArtifact.OPENGGF_PRODUCER, "4".repeat(64))));
            observerRuntimeIdentities.put(ProducerKind.REFERENCE,
                    new CallbackObserverIdentity("bizhawk-s1-callback.v1"));
            observerRuntimeIdentities.put(ProducerKind.OPENGGF,
                    new CallbackObserverIdentity("openggf.callback.v1"));
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
        public Map<ProducerKind, ObserverRuntimeIdentity> observerRuntimeIdentities() {
            return observerRuntimeIdentities;
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
