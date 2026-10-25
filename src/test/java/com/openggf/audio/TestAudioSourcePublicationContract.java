package com.openggf.audio;

import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.presentation.AudioPresentationCommandResolver;
import com.openggf.audio.presentation.AudioPresentationFrameView;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.SmpsAssetKey;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.debug.PerformanceProfiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.openggf.audio.AudioTestFixtures.EMPTY_DAC;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Before-move behavior of the original manager-owned logical source namespace.
 * No replacement owner, residency policy, production hooks or global manager swaps.
 * Reflection is confined to current publication/guard diagnostics and generation limits.
 */
class TestAudioSourcePublicationContract {
    private AudioManager audio;
    private ObservingBackend backend;
    private final List<String> effects = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Construct the spy itself: copying another manager would leave its final
        // nested owners bound to the original receiver.
        audio = spy(AudioManager.class);
        backend = new ObservingBackend();
        audio.setBackend(backend);
        effects.clear();
    }

    @AfterEach
    void tearDown() {
        backend.sink.onClose = () -> { };
        backend.onStreamReset = () -> { };
        backend.onStop = () -> { };
        if (audio.getBackend() instanceof ObservingBackend current) {
            current.sink.onClose = () -> { };
            current.onStreamReset = () -> { };
            current.onStop = () -> { };
        }
        audio.setStreamedMusicSessionInvalidator(() -> { });
        if (audio.mutatorBinding != null) audio.mutatorBinding.close();
        audio.destroy();
    }

    @Test
    void sameIdentityReentryBypassesAdmissionButChangedIdentityIsRejected() {
        Profile installed = new Profile("installed");
        Rom rom = install(installed);
        Object prior = base();
        Profile donor = new Profile("donor");
        donor.onConfig = () -> {
            assertTrue(Thread.holdsLock(audio), "the public manager monitor remains held");
            assertTrue(guard());
            effects.add("callback");
            audio.setRom(rom);
            audio.setAudioProfile(installed);
            assertSame(prior, base(), "the bypass must publish nothing");
            IllegalStateException changedRom = assertThrows(IllegalStateException.class,
                    () -> audio.setRom(new Rom()));
            IllegalStateException changedProfile = assertThrows(IllegalStateException.class,
                    () -> audio.setAudioProfile(new Profile("changed")));
            assertEquals("Audio source mutation must not be re-entered", changedRom.getMessage());
            assertEquals(changedRom.getMessage(), changedProfile.getMessage());
            assertTrue(guard(), "rejected nested admissions must not release the outer guard");
            effects.add("returned");
        };

        audio.registerDonorLoader("donor", donor.loader, EMPTY_DAC, null, donor);

        assertEquals(List.of("callback", "returned"), effects);
        assertSame(prior, base());
        assertEquals(1L, generations().get("donor"));
        assertFalse(guard());
        verify(audio, times(2)).setRom(rom);
        verify(audio, times(2)).setAudioProfile(installed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameIdentityWithAbsentLoaderRejectsReentryButRetriesPreparationOutsideAdmission(boolean changeRom) {
        Rom rom = new Rom();
        int[] loaderPreparations = {0};
        int[] configCallbacks = {0};
        GameAudioProfile installed = new AudioTestFixtures.StubAudioProfile(null) {
            @Override public SmpsLoader createSmpsLoader(Rom sourceRom) {
                assertSame(rom, sourceRom);
                assertTrue(Thread.holdsLock(audio));
                assertTrue(guard());
                loaderPreparations[0]++;
                return super.createSmpsLoader(sourceRom);
            }

            @Override public SmpsSequencerConfig getSequencerConfig() {
                assertTrue(Thread.holdsLock(audio));
                assertTrue(guard());
                configCallbacks[0]++;
                return Profile.CONFIG;
            }
        };
        audio.setAudioProfile(installed);
        audio.setRom(rom);
        Object prior = base();
        assertSame(installed, component(prior, "profile"));
        assertSame(rom, component(prior, "rom"));
        assertNull(component(prior, "loader"));
        assertEquals(1, loaderPreparations[0]);
        assertEquals(1, configCallbacks[0]);
        Runnable sameIdentityRetry = () -> {
            if (changeRom) audio.setRom(rom);
            else audio.setAudioProfile(installed);
        };
        int[] donorCallbacks = {0};
        Profile donor = new Profile("donor");
        donor.onConfig = () -> {
            donorCallbacks[0]++;
            assertTrue(Thread.holdsLock(audio));
            assertTrue(guard());
            assertSame(prior, base());
            IllegalStateException failure = assertThrows(IllegalStateException.class, sameIdentityRetry::run);
            assertEquals("Audio source mutation must not be re-entered", failure.getMessage());
            assertSame(prior, base(), "identical identities with no installed loader must not bypass admission");
            assertEquals(1, loaderPreparations[0], "nested rejection precedes loader preparation");
            assertEquals(1, configCallbacks[0], "nested rejection precedes profile configuration");
            assertTrue(guard(), "nested rejection must preserve the outer admission");
        };

        audio.registerDonorLoader("donor", donor.loader, EMPTY_DAC, null, donor);

        assertEquals(1, donorCallbacks[0], "the nested attempt must actually execute");
        assertSame(prior, base());
        assertSame(installed, audio.getAudioProfile());
        assertSame(rom, component(base(), "rom"));
        assertNull(component(base(), "loader"), "the outside retry still starts without an installed loader");
        assertEquals(1, loaderPreparations[0]);
        assertEquals(1, configCallbacks[0]);
        assertEquals(1L, generations().get("donor"));
        assertFalse(guard());

        sameIdentityRetry.run();

        assertNotSame(prior, base(), "outside admission the same identities must prepare a fresh tuple");
        assertEquals(generation(prior) + 1, generation(base()));
        assertSame(installed, component(base(), "profile"));
        assertSame(rom, component(base(), "rom"));
        assertNull(component(base(), "loader"), "preparation uses the same null-loader seam again");
        assertEquals(2, loaderPreparations[0]);
        assertEquals(changeRom ? 1 : 2, configCallbacks[0]);
        assertEquals(1, donorCallbacks[0]);
        assertFalse(guard());
    }

    @Test
    void compatibilityOverloadsRetainVirtualDispatchToTheFiveArgumentEntry() {
        SmpsLoader loader = new AudioTestFixtures.StubSmpsLoader();
        audio.registerDonorLoader("three", loader, EMPTY_DAC);
        audio.registerDonorLoader("four", loader, EMPTY_DAC, Profile.CONFIG);
        verify(audio).registerDonorLoader("three", loader, EMPTY_DAC, null, null);
        verify(audio).registerDonorLoader("four", loader, EMPTY_DAC, Profile.CONFIG, null);
        assertEquals(Map.of("three", 1L, "four", 1L), generations());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void baseOverflowPrecedesEveryPreparationCallback(boolean changeRom) {
        Profile installed = new Profile("base");
        install(installed);
        replaceGeneration("baseAudioSource", null, Long.MAX_VALUE);
        Object prior = base();
        Profile candidate = new Profile("candidate");
        installed.onCreate = () -> fail("ROM preparation ran before overflow");
        candidate.onCreate = () -> fail("profile loader preparation ran before overflow");
        candidate.onConfig = () -> fail("profile config ran before overflow");
        int attempts = backend.profiles.size();

        assertThrows(ArithmeticException.class, () -> {
            if (changeRom) audio.setRom(new Rom());
            else audio.setAudioProfile(candidate);
        });

        assertSame(prior, base());
        assertEquals(attempts, backend.profiles.size());
        assertFalse(guard(), "overflow must release the admitted mutation");
    }

    @Test
    void donorConfigCallbackPrecedesOverflowAndBindingSettersRemainUnguarded() {
        Profile prior = new Profile("prior");
        audio.registerDonorLoader("donor", prior.loader, EMPTY_DAC, Profile.CONFIG, prior);
        Object route = donors().get("donor");
        write(audio, "donorGenerationCounters", Map.of("donor", Long.MAX_VALUE));
        Profile candidate = new Profile("candidate");
        candidate.onConfig = () -> {
            assertTrue(Thread.holdsLock(audio));
            assertTrue(guard());
            effects.add("config-before-overflow");
            audio.registerDonorSound(GameSound.JUMP, "donor", 0xA1);
            audio.registerDonorMusicMap("donor", Map.of(GameMusic.SUPER, 0x82));
        };
        int attempts = backend.donors.size();

        assertThrows(ArithmeticException.class, () -> audio.registerDonorLoader(
                "donor", candidate.loader, EMPTY_DAC, null, candidate));

        assertEquals(List.of("config-before-overflow"), effects);
        assertSame(route, donors().get("donor"));
        assertEquals(Long.MAX_VALUE, generations().get("donor"));
        assertEquals(attempts, backend.donors.size());
        assertEquals(Map.of(GameMusic.SUPER, 0x82), music().get("donor"));
        assertEquals(0xA1, (int) component(sounds().get(GameSound.JUMP), "sfxId"));
        assertFalse(guard());
    }

    @Test
    void donorConfigFailureWinsOverOverflowAndLeavesTheOldRoute() {
        Profile prior = new Profile("prior");
        audio.registerDonorLoader("donor", prior.loader, EMPTY_DAC, Profile.CONFIG, prior);
        Object route = donors().get("donor");
        write(audio, "donorGenerationCounters", Map.of("donor", Long.MAX_VALUE));
        AssertionError failure = new AssertionError("config callback identity");
        Profile candidate = new Profile("candidate");
        candidate.onConfig = () -> { throw failure; };
        int attempts = backend.donors.size();

        assertSame(failure, assertThrows(AssertionError.class, () -> audio.registerDonorLoader(
                "donor", candidate.loader, EMPTY_DAC, null, candidate)));
        assertSame(route, donors().get("donor"));
        assertEquals(Long.MAX_VALUE, generations().get("donor"));
        assertEquals(attempts, backend.donors.size());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(guard());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void creatorFailureAfterBasePublicationKeepsThePublishedTuple(boolean changeRom) {
        Profile prior = new Profile("prior");
        install(prior);
        Object before = base();
        long generation = generation(before);
        Rom nextRom = new Rom();
        Profile nextProfile = new Profile("next");
        AssertionError failure = new AssertionError("postpublication creator");
        AudioMutatorAccess.Binding binding = AudioMutatorAccess.bind(audio, intent -> {
            assertEquals(AudioMutatorAccess.Kind.SOURCE, intent.kind());
            assertTrue(Thread.holdsLock(audio));
            assertTrue(guard());
            assertNotSame(before, base());
            assertEquals(generation + 1, generation(base()));
            throw failure;
        });

        assertSame(failure, assertThrows(AssertionError.class, () -> {
            if (changeRom) audio.setRom(nextRom);
            else audio.setAudioProfile(nextProfile);
        }));

        Object published = base();
        assertSame(changeRom ? nextRom : component(before, "rom"), component(published, "rom"));
        assertSame(changeRom ? prior : nextProfile, audio.getAudioProfile());
        assertEquals(generation + 1, generation(published));
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(guard());
        binding.close();
        if (changeRom) audio.setRom(nextRom);
        else audio.setAudioProfile(nextProfile);
        assertSame(published, base(), "same-identity retry is a bypass, not a repair");
    }

    @Test
    void basePreparationFailureKeepsTupleAndDoesNotNotifyCreator() {
        Profile prior = new Profile("prior");
        install(prior);
        Object before = base();
        Profile candidate = new Profile("candidate");
        IllegalArgumentException failure = new IllegalArgumentException("loader preparation");
        candidate.onCreate = () -> { throw failure; };
        AudioMutatorAccess.bind(audio, intent -> fail("unpublished source notified creator"));
        int attempts = backend.profiles.size();

        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> audio.setAudioProfile(candidate)));

        assertSame(before, base());
        assertEquals(attempts, backend.profiles.size());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(guard());
    }

    @Test
    void baseInstallationReadsTheBackendAfterPreparationCallbacks() {
        Profile prior = new Profile("prior");
        install(prior);
        Profile candidate = new Profile("candidate");
        ObservingBackend replacement = new ObservingBackend();
        candidate.onConfig = () -> audio.setBackend(replacement);
        backend.profiles.clear();

        audio.setAudioProfile(candidate);

        assertTrue(backend.profiles.isEmpty(), "the entry-time backend must not receive candidate configuration");
        assertEquals(List.of(prior, candidate), replacement.profiles);
        assertSame(replacement, audio.getBackend());
        assertSame(candidate, audio.getAudioProfile());
        assertFalse(guard());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void baseRollbackReadsTheThenCurrentBackendAndSuppressesItsRestorationFailure(boolean rollbackFails) {
        Profile prior = new Profile("prior");
        install(prior);
        Object route = base();
        Profile candidate = new Profile("candidate");
        ObservingBackend replacement = new ObservingBackend();
        AssertionError original = new AssertionError("base candidate identity");
        IllegalArgumentException restoration = new IllegalArgumentException("base restore identity");
        backend.onProfile = profile -> {
            assertSame(candidate, profile);
            audio.setBackend(replacement);
            replacement.onProfile = restored -> {
                assertSame(prior, restored);
                assertTrue(guard());
                if (rollbackFails) throw restoration;
            };
            throw original;
        };

        assertSame(original, assertThrows(AssertionError.class, () -> audio.setAudioProfile(candidate)));

        assertSame(route, base());
        assertSame(replacement, audio.getBackend());
        assertEquals(List.of(prior, prior), replacement.profiles);
        assertArrayEquals(rollbackFails ? new Throwable[] {restoration} : new Throwable[0],
                original.getSuppressed());
        assertFalse(guard());
    }

    @Test
    void postpublicationCallbackReplacementUsesTheThenCurrentPresentationFactory() {
        Profile prior = new Profile("prior");
        install(prior);
        audio.captureLogicalSnapshot();
        AudioPresentationProducer retired = read(audio, "shadowProducer");
        Object retiredFactory = audio.shadowFactoryForTesting();
        Object retiredAdmissionPolicy = read(retiredFactory, "sfxAdmissionPolicy");
        ObservingBackend replacement = new ObservingBackend();
        Profile candidate = new Profile("candidate");
        AudioMutatorAccess.Binding binding = AudioMutatorAccess.bind(audio, intent -> {
            assertSame(candidate, audio.getAudioProfile());
            audio.setBackend(replacement);
            assertNull(read(audio, "shadowFactory"));
        });

        audio.setAudioProfile(candidate);

        assertTrue(retired.isClosed());
        assertSame(retiredAdmissionPolicy, read(retiredFactory, "sfxAdmissionPolicy"),
                "publication must not write a factory retired by the callback");
        assertSame(replacement, audio.getBackend());
        assertNull(read(audio, "shadowProducer"), "the callback's close stays effective after publication");
        binding.close();
        audio.captureLogicalSnapshot();
        assertNotSame(retiredFactory, audio.shadowFactoryForTesting());
        assertEquals(List.of(candidate), replacement.profiles);
        assertFalse(guard());
    }

    @Test
    void acceptedPresentationCloseFailureAfterBasePublicationDoesNotRollBackTheTuple() {
        Profile prior = new Profile("prior");
        install(prior);
        audio.captureLogicalSnapshot();
        Object before = base();
        AudioPresentationProducer producer = read(audio, "shadowProducer");
        IllegalStateException failure = new IllegalStateException("accepted old sink close");
        backend.sink.onClose = () -> {
            assertTrue(guard());
            assertNotSame(before, base());
            effects.add("published-before-close");
            throw failure;
        };
        Rom replacement = new Rom();

        assertSame(failure, assertThrows(IllegalStateException.class, () -> audio.setRom(replacement)));

        assertEquals(List.of("published-before-close"), effects);
        assertSame(replacement, component(base(), "rom"));
        assertEquals(generation(before) + 1, generation(base()));
        assertTrue(producer.isClosed());
        assertSame(producer, read(audio, "shadowProducer"), "root reference clearing was never reached");
        assertFalse(guard());
        Object partial = base();
        audio.setRom(replacement);
        assertSame(partial, base());
        assertEquals(1, backend.sink.closes, "same identity must not retry partial teardown");
    }

    @Test
    void rejectedNestedResetStillNotifiesCreatorAndInvalidatesStreamedSessionFirst() {
        Profile installed = new Profile("installed");
        install(installed);
        Object prior = base();
        audio.registerDonorLoader("retained", installed.loader, EMPTY_DAC);
        StreamedMusicPort port = mock(StreamedMusicPort.class);
        int rate = audio.outputSampleRate();
        when(port.outputRate()).thenReturn(rate);
        doAnswer(invocation -> { effects.add("port-close"); return null; }).when(port).close();
        audio.installStreamedMusicPort(port);
        audio.setStreamedMusicSessionInvalidator(() -> {
            assertTrue(guard());
            assertNull(audio.mutatorBinding, "creator binding is consumed before invalidation");
            effects.add("invalidate");
        });
        backend.onStreamReset = () -> { assertTrue(guard()); effects.add("stream-reset"); };
        backend.onStop = () -> fail("rejected reset must not reach backend stop");
        AudioMutatorAccess.Binding binding = AudioMutatorAccess.bind(audio, intent -> {
            assertEquals(AudioMutatorAccess.Kind.STOP, intent.kind());
            assertSame(prior, base());
            assertTrue(Thread.holdsLock(audio));
            assertTrue(guard());
            effects.add("creator-stop");
        });
        Profile candidate = new Profile("candidate");
        candidate.onConfig = () -> {
            assertThrows(IllegalStateException.class, audio::resetState);
            assertSame(prior, base());
            assertTrue(donors().containsKey("retained"));
            assertTrue(guard(), "nested failure cannot release outer admission");
        };

        audio.setAudioProfile(candidate);

        assertEquals(List.of("creator-stop", "invalidate", "stream-reset", "port-close"), effects);
        assertFalse(binding.active());
        verify(port).close();
        assertSame(StreamedMusicPort.EMPTY, read(audio, "streamedMusicPort"));
        assertFalse(guard());
        backend.onStop = () -> { };
        backend.onStreamReset = () -> { };
    }

    @Test
    void resetCloseFailureLeavesBaseEmptyAndDonorsRetainedAfterNonSourceEffects() {
        Profile profile = new Profile("base");
        install(profile);
        audio.registerDonorLoader("retained", profile.loader, EMPTY_DAC);
        audio.registerDonorSound(GameSound.JUMP, "retained", 0xA1);
        audio.registerDonorMusicMap("retained", Map.of(GameMusic.SUPER, 0x82));
        audio.beginCommandTimelineFrame(17);
        audio.playSfx(GameSound.RING);
        audio.presentFrame(PresentationMode.SILENT);
        Object priorBase = base();
        Map<String, Object> routes = donors();
        Map<String, Long> counters = generations();
        AudioPresentationProducer producer = read(audio, "shadowProducer");
        IllegalStateException failure = new IllegalStateException("reset sink close");
        backend.onStop = () -> {
            assertSame(priorBase, base());
            assertTrue(guard());
            effects.add("stop-before-empty-base");
        };
        backend.sink.onClose = () -> {
            assertNull(audio.getAudioProfile());
            assertEquals(0, generation(base()));
            assertNull(read(audio, "soundMap"));
            assertEquals(Boolean.TRUE, read(audio, "ringLeft"));
            assertEquals(0, audio.commandTimeline().entryCount());
            assertSame(routes, donors());
            assertSame(counters, generations());
            assertTrue(guard());
            effects.add("close-after-root-reset-before-donors");
            throw failure;
        };

        assertSame(failure, assertThrows(IllegalStateException.class, audio::resetState));

        assertEquals(List.of("stop-before-empty-base", "close-after-root-reset-before-donors"), effects);
        assertEquals(0, generation(base()));
        assertSame(routes, donors());
        assertSame(counters, generations());
        assertTrue(sounds().containsKey(GameSound.JUMP));
        assertTrue(music().containsKey("retained"));
        assertTrue(producer.isClosed());
        assertSame(producer, read(audio, "shadowProducer"));
        assertFalse(guard());
        backend.onStop = () -> { };
        backend.sink.onClose = () -> { };
        audio.resetState();
        assertTrue(donors().isEmpty());
        assertTrue(generations().isEmpty());
        assertTrue(sounds().isEmpty());
        assertTrue(music().isEmpty());
        assertEquals(1, backend.sink.closes, "accepted producer close is not retried");
    }

    @Test
    void resetDonorOverflowHappensAfterPresentationCloseAndPreventsCounterDisposal() {
        Profile profile = new Profile("base");
        install(profile);
        audio.registerDonorLoader("overflow", profile.loader, EMPTY_DAC);
        audio.registerDonorSound(GameSound.JUMP, "overflow", 0xA1);
        replaceGeneration("donorAudioSources", "overflow", Long.MAX_VALUE);
        write(audio, "donorGenerationCounters", Map.of("overflow", Long.MAX_VALUE, "tombstone", 41L));
        Map<String, Object> routes = donors();
        Map<String, Long> counters = generations();
        audio.captureLogicalSnapshot();
        backend.sink.onClose = () -> {
            assertEquals(0, generation(base()));
            assertSame(routes, donors());
            assertSame(counters, generations());
            effects.add("close-before-overflow");
        };

        assertThrows(ArithmeticException.class, audio::resetState);

        assertEquals(List.of("close-before-overflow"), effects);
        assertEquals(0, generation(base()));
        assertNull(read(audio, "shadowProducer"));
        assertSame(routes, donors(), "overflow occurs before any donor-table publication");
        assertSame(counters, generations(), "the final reset counter discard was never reached");
        assertTrue(sounds().containsKey(GameSound.JUMP));
        assertFalse(guard());
        // Only after observing the partial state, make fixture cleanup finite.
        replaceGeneration("donorAudioSources", "overflow", 1);
        backend.sink.onClose = () -> { };
        audio.resetState();
        assertTrue(generations().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void donorRollbackUsesTheBackendInstalledDuringTheFailedCallback(boolean rollbackFails) {
        Profile prior = new Profile("prior");
        audio.registerDonorLoader("donor", prior.loader, EMPTY_DAC, Profile.CONFIG, prior);
        Object route = donors().get("donor");
        Profile candidate = new Profile("candidate");
        ObservingBackend replacement = new ObservingBackend();
        IllegalStateException original = new IllegalStateException("candidate backend identity");
        AssertionError restoration = new AssertionError("replacement rollback identity");
        replacement.onDonor = profile -> {
            assertSame(prior, profile);
            assertTrue(guard());
            if (rollbackFails) throw restoration;
        };
        backend.onDonor = profile -> {
            assertSame(candidate, profile);
            audio.setBackend(replacement);
            throw original;
        };

        assertSame(original, assertThrows(IllegalStateException.class, () -> audio.registerDonorLoader(
                "donor", candidate.loader, EMPTY_DAC, Profile.CONFIG, candidate)));

        assertSame(replacement, audio.getBackend());
        assertEquals(List.of(prior), replacement.donors);
        if (rollbackFails) {
            assertArrayEquals(new Throwable[] {restoration}, original.getSuppressed());
            assertFalse(donors().containsKey("donor"));
            assertEquals(2L, generations().get("donor"));
        } else {
            assertEquals(0, original.getSuppressed().length);
            assertSame(route, donors().get("donor"));
            assertEquals(1L, generations().get("donor"));
        }
        assertFalse(guard());
    }

    @Test
    void deferredCompatibilityCompletionIsOutsideAdmissionAndShadowReadsDoNotComplete() {
        SmpsLoader legacy = new AudioTestFixtures.StubSmpsLoader();
        audio.registerDonorLoader("legacy", legacy, EMPTY_DAC);
        Object observed = donors().get("legacy");
        install(new Profile("base"));
        var sources = shadowSources();
        var projection = sources.sourceFor(SmpsAssetKey.Route.DONOR_ID, "legacy");
        assertNull(projection.config());
        assertSame(observed, donors().get("legacy"));
        Profile trigger = new Profile("trigger");
        trigger.onConfig = () -> {
            assertTrue(Thread.holdsLock(audio));
            assertTrue(guard());
            Object completed = completeLegacy("legacy", observed);
            assertNotSame(observed, completed);
            assertSame(legacy, component(completed, "loader"));
            assertSame(Profile.CONFIG, component(completed, "config"));
            assertEquals(generation(observed), generation(completed));
            assertTrue(guard());
        };

        audio.registerDonorLoader("trigger", trigger.loader, EMPTY_DAC, null, trigger);

        assertSame(Profile.CONFIG, component(donors().get("legacy"), "config"));
        assertEquals(1L, generations().get("legacy"));
        assertFalse(guard());
    }

    @Test
    void compatibilityCompletionUsesCurrentIdentityButReturnsObservedAfterRemoval() {
        SmpsLoader first = new AudioTestFixtures.StubSmpsLoader();
        audio.registerDonorLoader("legacy", first, EMPTY_DAC);
        Object observed = donors().get("legacy");
        install(new Profile("base"));
        audio.registerDonorLoader("legacy", new AudioTestFixtures.StubSmpsLoader(), EMPTY_DAC, Profile.CONFIG);
        Object current = donors().get("legacy");
        assertSame(current, completeLegacy("legacy", observed));
        audio.clearDonorAudio();
        Map<String, Long> tombstones = generations();

        assertSame(observed, completeLegacy("legacy", observed));

        assertTrue(donors().isEmpty());
        assertSame(tombstones, generations());
        assertNull(component(observed, "config"));
        assertEquals(3L, shadowSources().sourceFor(SmpsAssetKey.Route.DONOR_ID, "legacy").dependencyGeneration());
        assertNull(shadowSources().sourceFor(SmpsAssetKey.Route.DONOR_ID, "legacy").loader());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logicalRestorePublishesOnlySoundBindingsWithoutRewindingSourceOrMusic(boolean held) {
        install(new Profile("base"));
        audio.registerDonorLoader("donor", new AudioTestFixtures.StubSmpsLoader(), EMPTY_DAC);
        audio.registerDonorSound(GameSound.JUMP, "donor", 0xA1);
        audio.registerDonorMusicMap("donor", Map.of(GameMusic.SUPER, 0x81));
        audio.beginCommandTimelineFrame(12);
        audio.playSfx(GameSound.RING);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        audio.registerDonorLoader("donor", new AudioTestFixtures.StubSmpsLoader(), EMPTY_DAC);
        audio.registerDonorLoader("new-route", new AudioTestFixtures.StubSmpsLoader(), EMPTY_DAC);
        audio.registerDonorSound(GameSound.JUMP, "new-route", 0xA2);
        audio.registerDonorSound(GameSound.ROLLING, "new-route", 0xA3);
        audio.registerDonorMusicMap("donor", Map.of(GameMusic.SUPER, 0x82));
        audio.beginCommandTimelineFrame(31);
        audio.resetRingSound();
        audio.presentFrame(PresentationMode.SILENT);
        Object currentBase = base();
        Map<String, Object> currentRoutes = donors();
        Map<String, Long> currentGenerations = generations();
        Map<String, Object> currentMusic = Map.copyOf(music());
        Set<AudioLogicalSnapshot.DonorSfxBindingSnapshot> liveBindings = audio.captureLogicalSnapshot().donorBindings();
        int publications = audio.logicalRestorePublicationsForTesting();
        if (held) audio.beginReverseAudioPresentation();

        audio.restoreLogicalSnapshot(selected);
        if (held) {
            assertEquals(liveBindings, audio.captureLogicalSnapshot().donorBindings(), "held selection has no binding publication");
            assertEquals(publications, audio.logicalRestorePublicationsForTesting());
            assertTrue(audio.endReverseAudioPresentation());
        }

        AudioLogicalSnapshot after = audio.captureLogicalSnapshot();
        assertEquals(selected.donorBindings(), after.donorBindings());
        assertEquals(selected.ringLeft(), after.ringLeft());
        assertEquals(selected.commandTimelineFrame(), after.commandTimelineFrame());
        assertEquals(selected.commandTimelineNextOrder(), after.commandTimelineNextOrder());
        assertEquals(publications + 1, audio.logicalRestorePublicationsForTesting());
        assertSame(currentBase, base());
        assertSame(currentRoutes, donors());
        assertSame(currentGenerations, generations());
        assertEquals(currentMusic, music());
        assertTrue(after.donorGameIds().contains("new-route"), "snapshot route IDs do not restore installed tuples");
    }

    @Test
    void eagerCursorFailureRollsBackBindingsAndLedgerWithoutRollingBackSources() {
        install(new Profile("base"));
        audio.registerDonorSound(GameSound.JUMP, "old", 0xA1);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        audio.registerDonorLoader("current", new AudioTestFixtures.StubSmpsLoader(), EMPTY_DAC);
        audio.registerDonorMusicMap("current", Map.of(GameMusic.SUPER, 0x82));
        audio.registerDonorSound(GameSound.JUMP, "current", 0xA2);
        audio.beginCommandTimelineFrame(19);
        audio.resetRingSound();
        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        Map<String, Object> routes = donors();
        Map<String, Long> counters = generations();
        AudioLogicalSnapshot malformed = new AudioLogicalSnapshot(false, 99, -1, 0,
                selected.presentation(), selected.donorGameIds(), selected.donorBindings());

        audio.restoreLogicalSnapshot(malformed);

        AudioLogicalSnapshot after = audio.captureLogicalSnapshot();
        assertEquals(before.ringLeft(), after.ringLeft());
        assertEquals(before.commandTimelineFrame(), after.commandTimelineFrame());
        assertEquals(before.commandTimelineNextOrder(), after.commandTimelineNextOrder());
        assertEquals(before.donorBindings(), after.donorBindings());
        assertEquals(before.presentation(), after.presentation());
        assertSame(routes, donors());
        assertSame(counters, generations());
        assertEquals(Map.of(GameMusic.SUPER, 0x82), music().get("current"));
    }

    @Test
    void backendReplacementAndDestroyRetainNamespaceButSuccessfulResetDiscardsTombstones() {
        Profile profile = new Profile("base");
        install(profile);
        audio.registerDonorLoader("removed", profile.loader, EMPTY_DAC);
        audio.clearDonorAudio();
        audio.registerDonorLoader("live", profile.loader, EMPTY_DAC);
        audio.registerDonorSound(GameSound.JUMP, "live", 0xA1);
        audio.registerDonorMusicMap("live", Map.of(GameMusic.SUPER, 0x81));
        Object base = base();
        Map<String, Object> routes = donors();
        Map<String, Long> counters = generations();
        Map<GameSound, Object> bindings = Map.copyOf(sounds());
        Map<String, Object> music = Map.copyOf(music());
        audio.captureLogicalSnapshot();
        audio.setBackend(new NullAudioBackend());
        assertSame(base, base());
        assertSame(routes, donors());
        assertSame(counters, generations());
        audio.destroy();
        assertSame(base, base());
        assertSame(routes, donors());
        assertSame(counters, generations());
        assertEquals(bindings, sounds());
        assertEquals(music, music());
        assertEquals(2L, generations().get("removed"));

        audio.resetState();

        assertEquals(0, generation(base()));
        assertTrue(donors().isEmpty());
        assertTrue(generations().isEmpty());
        assertTrue(sounds().isEmpty());
        assertTrue(music().isEmpty());
        audio.registerDonorLoader("removed", profile.loader, EMPTY_DAC);
        assertEquals(1L, generations().get("removed"));
        assertFalse(guard());
    }

    @Test
    void standaloneConstructionPublishesGenerationOneWithoutRomOrLoader() {
        Profile profile = new Profile("standalone");
        AudioManager standalone = AudioManager.createStandalonePresentation("standalone", profile,
                SonicConfigurationService.getInstance(), PerformanceProfiler.getInstance(),
                new NoDeviceAudioSink(48_000), new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
        try {
            Object source = read(standalone, "baseAudioSource");
            assertEquals(1, generation(source));
            assertSame(profile, component(source, "profile"));
            assertSame(Profile.CONFIG, component(source, "config"));
            assertNull(component(source, "rom"));
            assertNull(component(source, "loader"));
            assertNull(component(source, "dac"));
            assertSame(profile, standalone.getAudioProfile());
        } finally {
            standalone.destroy();
        }
    }

    private Rom install(Profile profile) {
        Rom rom = new Rom();
        audio.setAudioProfile(profile);
        audio.setRom(rom);
        return rom;
    }

    private Object base() { return read(audio, "baseAudioSource"); }
    private boolean guard() { return read(audio, "sourceMutationInProgress"); }
    private Map<String, Object> donors() { return read(audio, "donorAudioSources"); }
    private Map<String, Long> generations() { return read(audio, "donorGenerationCounters"); }
    private Map<GameSound, Object> sounds() { return read(audio, "donorSoundBindings"); }
    private Map<String, Object> music() { return read(audio, "donorMusicBindings"); }
    private static long generation(Object source) { return (long) component(source, "generation"); }

    private AudioPresentationCommandResolver.Sources shadowSources() {
        audio.captureLogicalSnapshot();
        Object resolver = read(audio, "shadowResolver");
        return read(resolver, "sources");
    }

    private Object completeLegacy(String key, Object observed) {
        try {
            var method = AudioManager.class.getDeclaredMethod("completeLegacyDonorSource", String.class, observed.getClass());
            method.setAccessible(true);
            return method.invoke(audio, key, observed);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Object receiver, String name) {
        try {
            if (receiver instanceof AudioManager manager && Set.of(
                    "baseAudioSource", "donorAudioSources", "donorGenerationCounters",
                    "sourceMutationInProgress", "donorSoundBindings", "donorMusicBindings").contains(name)) {
                receiver = AudioManagerTestDiagnostics.sourcePublication(manager);
            }
            Field field = receiver.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(receiver);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void write(AudioManager receiver, String name, Object value) {
        try {
            Object publication = AudioManagerTestDiagnostics.sourcePublication(receiver);
            Field field = publication.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(publication, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Object component(Object record, String name) {
        try {
            var method = record.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(record);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private void replaceGeneration(String field, String key, long generation) {
        Object previous = key == null ? base() : donors().get(key);
        RecordComponent[] components = previous.getClass().getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values = Arrays.stream(components).map(c -> c.getName().equals("generation")
                ? generation : component(previous, c.getName())).toArray();
        try {
            Constructor<?> constructor = previous.getClass().getDeclaredConstructor(types);
            constructor.setAccessible(true);
            Object replaced = constructor.newInstance(values);
            if (key == null) write(audio, field, replaced);
            else write(audio, field, Map.of(key, replaced));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class Profile extends AudioTestFixtures.StubAudioProfile {
        static final SmpsSequencerConfig CONFIG = new SmpsSequencerConfig.Builder().build();
        final SmpsLoader loader = new AudioTestFixtures.StubSmpsLoader();
        final String game;
        Runnable onCreate = () -> { };
        Runnable onConfig = () -> { };
        Profile(String game) { super(null); this.game = game; }
        @Override public String presentationGameId() { return game; }
        @Override public SmpsLoader createSmpsLoader(Rom rom) { onCreate.run(); return loader; }
        @Override public SmpsSequencerConfig getSequencerConfig() { onConfig.run(); return CONFIG; }
    }

    private final class ObservingBackend extends NullAudioBackend {
        final List<GameAudioProfile> profiles = new ArrayList<>();
        final List<GameAudioProfile> donors = new ArrayList<>();
        final ObservingSink sink = new ObservingSink();
        Consumer<GameAudioProfile> onProfile = profile -> { };
        Consumer<GameAudioProfile> onDonor = profile -> { };
        Runnable onStop = () -> { };
        Runnable onStreamReset = () -> { };
        @Override public void setAudioProfile(GameAudioProfile profile) { profiles.add(profile); onProfile.accept(profile); }
        @Override public void registerAudioProfileCoordHandlers(GameAudioProfile profile) { donors.add(profile); onDonor.accept(profile); }
        @Override public void stopPlayback() { onStop.run(); }
        @Override public void resetStreamedMusicPort() { onStreamReset.run(); }
        @Override public AudioPresentationSink createPresentationSink(Consumer<Throwable> failureHandler, Consumer<String> warningHandler) { return sink; }
    }

    private static final class ObservingSink implements AudioPresentationSink {
        Runnable onClose = () -> { };
        int closes;
        @Override public int sampleRate() { return 48_000; }
        @Override public void accept(AudioPresentationFrameView frame) { }
        @Override public void onReverseBoundary() { }
        @Override public void close() { closes++; onClose.run(); }
    }
}
