package com.openggf.audio.session;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsSessionTransitionMatrix {
    private enum SourcePolicy {
        S1(true), S2(false), S3K(false);

        private final boolean preservesSfx;

        SourcePolicy(boolean preservesSfx) {
            this.preservesSfx = preservesSfx;
        }
    }

    static Stream<Arguments> baseDonorPairings() {
        return Arrays.stream(SourcePolicy.values()).flatMap(base ->
                Arrays.stream(SourcePolicy.values()).map(donor ->
                        Arguments.of(base, donor)));
    }

    @ParameterizedTest(name = "host={0}, donor={1}")
    @MethodSource("baseDonorPairings")
    void hostPolicyWinsForEveryBaseDonorPairing(
            SourcePolicy host, SourcePolicy donor) {
        SmpsPhysicalPolicy physicalPolicy =
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings =
                SmpsSessionTestFixtures.settings();
        SmpsSessionProfileFingerprint fingerprint =
                new SmpsSessionProfileFingerprint(
                        host.name(), 19, physicalPolicy.identity(), settings);
        SmpsDriverSession session = new SmpsDriverSession(
                settings, physicalPolicy,
                new SmpsSessionTestFixtures.RecordingObserver(), fingerprint,
                SmpsDriverSessionConfiguration.DEFAULT);
        session.install();
        Object device = session.physicalIdentityForTesting();
        SmpsDriver driver = session.logicalDriverForTesting();

        session.queueActivation(activation(
                0x80 + donor.ordinal(), donor, true));
        session.serviceForward();

        assertSame(device, session.physicalIdentityForTesting());
        assertSame(driver, session.logicalDriverForTesting());
        assertSame(fingerprint, session.captureSnapshot().profile());
        assertEquals(host.name(), session.captureSnapshot()
                .profile().baseGameId());
    }

    @ParameterizedTest(name = "base={0}, donor={1}")
    @MethodSource("baseDonorPairings")
    void baseMusicAfterDonorSfxAndDonorMusicAfterBaseSfx(
            SourcePolicy base, SourcePolicy donor) {
        assertMusicTransition(base, donor, false);
        assertMusicTransition(base, donor, true);
    }

    @Test
    void overridePopEmitsSourceOwnedFirstServiceAndExactNextPcm() {
        SmpsSessionTestFixtures.RecordingObserver expectedWrites =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsSessionTestFixtures.RecordingObserver actualWrites =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession expected =
                SmpsSessionTestFixtures.session(expectedWrites);
        SmpsDriverSession actual =
                SmpsSessionTestFixtures.session(actualWrites);
        expected.install();
        actual.install();
        Object physical = actual.physicalIdentityForTesting();
        SmpsDriver logical = actual.logicalDriverForTesting();
        PreparedSmpsMusicActivation base = activation(
                0x81, SourcePolicy.S1, false);
        expected.queueActivation(base);
        actual.queueActivation(base);
        expected.serviceForward();
        actual.serviceForward();

        actual.applyCommand(new SmpsSessionCommand.PushOverride(
                activation(0x82, SourcePolicy.S2, true)));
        actual.serviceForward();
        actualWrites.clear();
        actual.applyCommand(new SmpsSessionCommand.RestoreOverride());
        assertTrue(actualWrites.events().isEmpty(),
                "override pop itself is write-free");
        expectedWrites.clear();

        expected.serviceForward();
        actual.serviceForward();
        short[] expectedPcm = new short[256];
        short[] actualPcm = new short[256];
        expected.renderFrames(expectedPcm, 0, 128);
        actual.renderFrames(actualPcm, 0, 128);

        assertSame(physical, actual.physicalIdentityForTesting());
        assertSame(logical, actual.logicalDriverForTesting());
        assertEquals(expectedWrites.events(), actualWrites.events());
        assertArrayEquals(expectedPcm, actualPcm);
    }

    private static void assertMusicTransition(
            SourcePolicy base,
            SourcePolicy donor,
            boolean donorMusicAfterBaseSfx) {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        session.install();
        Object device = session.physicalIdentityForTesting();
        SmpsDriver driver = session.logicalDriverForTesting();
        SourcePolicy sfxPolicy = donorMusicAfterBaseSfx ? base : donor;
        SourcePolicy musicPolicy = donorMusicAfterBaseSfx ? donor : base;
        boolean musicIsDonor = donorMusicAfterBaseSfx;
        SmpsDriverSnapshot current = snapshotWith(
                entry(0xA0 + sfxPolicy.ordinal(), sfxPolicy,
                        !donorMusicAfterBaseSfx, true));
        SmpsDriverSession.PreparedRestore prepared = session.prepareRestore(
                session.captureSnapshot(), current, ignored -> null);
        session.commitRestore(prepared);

        session.queueActivation(activation(
                0x81 + musicPolicy.ordinal(), musicPolicy, musicIsDonor));

        List<SmpsDriverSnapshot.SequencerEntry> sequencers =
                session.captureLogicalSnapshot().sequencers();
        assertSame(device, session.physicalIdentityForTesting());
        assertSame(driver, session.logicalDriverForTesting());
        assertFalse(sequencers.getFirst().sfx());
        assertEquals(musicPolicy.preservesSfx ? 2 : 1,
                sequencers.size());
        assertEquals(musicPolicy.preservesSfx,
                sequencers.stream().anyMatch(
                        SmpsDriverSnapshot.SequencerEntry::sfx));
    }

    private static PreparedSmpsMusicActivation activation(
            int id, SourcePolicy policy, boolean donor) {
        SmpsDriverSnapshot.SequencerEntry incoming = entry(
                id, policy, donor, false);
        return new PreparedSmpsMusicActivation(
                new SmpsMusicActivation(incoming.source(), 0),
                incoming,
                SmpsLogicalTransitionPolicies.forConfig(incoming.config()),
                new SmpsDacSelection(
                        incoming.source(), incoming.dacData()));
    }

    private static SmpsDriverSnapshot snapshotWith(
            SmpsDriverSnapshot.SequencerEntry entry) {
        return new SmpsDriverSnapshot(
                SmpsSequencer.Region.NTSC,
                SmpsDriver.ReadMode.SAMPLE_ACCURATE,
                entry.sfx() ? entry.source().id() : 0,
                false, 0, 5, List.of(entry),
                new int[] {-1, -1, -1, -1, -1, -1},
                new int[] {-1, -1, -1, -1});
    }

    private static SmpsDriverSnapshot.SequencerEntry entry(
            int id,
            SourcePolicy policy,
            boolean donor,
            boolean sfx) {
        AudioTestFixtures.StubSmpsData data =
                new AudioTestFixtures.StubSmpsData(
                        (donor ? "donor-" : "base-") + id);
        data.setId(id);
        SmpsSourceDescriptor source = new SmpsSourceDescriptor(
                sfx
                        ? donor ? SmpsSourceDescriptor.Kind.DONOR_SFX_ID
                                : SmpsSourceDescriptor.Kind.BASE_SFX_ID
                        : donor ? SmpsSourceDescriptor.Kind.DONOR_MUSIC
                                : SmpsSourceDescriptor.Kind.BASE_MUSIC,
                id, null, donor ? "donor" : null,
                data.getZ80StartAddress(), data.getData().length,
                Arrays.hashCode(data.getData()), false, 7);
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder()
                .direct68kDriver(policy.preservesSfx)
                .build();
        SmpsDriver detached = new SmpsDriver();
        SmpsSequencer sequencer = new SmpsSequencer(
                data, SmpsSessionTestFixtures.dac(), detached, detached,
                AudioManager.getInstance(), config, source,
                SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE);
        sequencer.setIsSfx(sfx);
        return new SmpsDriverSnapshot.SequencerEntry(
                sfx, source,
                SmpsSequencer.SourceDescriptorTrust.PRECOMPUTED_IMMUTABLE,
                null, data, SmpsSessionTestFixtures.dac(),
                AudioManager.getInstance(), config,
                sequencer.captureSnapshot());
    }
}
