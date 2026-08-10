package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.CoordFlagContext;
import com.openggf.audio.smps.CoordFlagHandler;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Rom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that AudioManager.resetState() clears all observable mutable fields:
 * audioProfile, soundMap, smpsLoader/dacData (via setRom), ringLeft, and donor state.
 *
 * No ROM or OpenGL required.
 */
public class TestAudioManagerResetState {

    private static final DacData EMPTY_DAC = new DacData(
            Collections.emptyMap(), Collections.emptyMap(), 288);

    private AudioManager am;
    private RingTrackingBackend backend;

    @BeforeEach
    public void setUp() {
        am = AudioManager.getInstance();
        am.resetState();
        backend = new RingTrackingBackend();
        am.setBackend(backend);
    }

    @AfterEach
    public void tearDown() {
        am.resetState();
    }

    @Test
    public void resetStateClearsAudioProfile() {
        am.setAudioProfile(new StubAudioProfile());
        assertNotNull(am.getAudioProfile(), "Precondition: audioProfile should be set");

        am.resetState();

        assertNull(am.getAudioProfile(), "audioProfile should be null after resetState()");
    }

    @Test
    public void resetStateResetsRingLeftToTrue() {
        // Advance ringLeft to false by playing a RING sound once (toggles trueâ†’false)
        am.setSoundMap(new EnumMap<>(GameSound.class));
        am.playSfx(GameSound.RING);
        assertEquals("RING_LEFT", lastSfxName());

        // Second ring should use RING_RIGHT (ringLeft is now false)
        am.playSfx(GameSound.RING);
        assertEquals("RING_RIGHT", lastSfxName());

        am.resetState();
        am.setBackend(backend);
        am.setSoundMap(new EnumMap<>(GameSound.class));

        // After reset, ringLeft is true again â€” first ring goes left
        am.playSfx(GameSound.RING);
        assertEquals("RING_LEFT", lastSfxName(),
                "ringLeft should be reset to true after resetState()");
    }

    @Test
    public void resetStateClearsDonorAudio() {
        // Register a donor loader and sound binding
        StubSmpsLoader donorLoader = new StubSmpsLoader();
        am.registerDonorLoader("s2", donorLoader, EMPTY_DAC);
        am.registerDonorSound(GameSound.SPINDASH_CHARGE, "s2", 0xE0);

        am.resetState();
        am.setBackend(backend);

        // With empty sound map and cleared donor state, the sound falls through to
        // backend.playSfx(name) rather than routing through the donor loader
        am.setSoundMap(new EnumMap<>(GameSound.class));
        am.playSfx(GameSound.SPINDASH_CHARGE);

        assertEquals("SPINDASH_CHARGE", lastSfxName(),
                "Donor bindings should be cleared — presentation must use the base-name route");
    }

    @Test
    public void resetStateClearsSoundMap() {
        // Set up a sound map with a base loader so playSfx(int) would succeed
        StubSmpsLoader baseLoader = new StubSmpsLoader();
        baseLoader.sfxResults.put(0x90, new StubSmpsData("jump"));
        am.setAudioProfile(new StubAudioProfile(baseLoader));
        am.setRom(new Rom());

        Map<GameSound, Integer> soundMap = new EnumMap<>(GameSound.class);
        soundMap.put(GameSound.JUMP, 0x90);
        am.setSoundMap(soundMap);

        am.resetState();
        am.setBackend(backend);

        // After reset: no soundMap, no smpsLoader â†’ falls through to fallback
        am.playSfx(GameSound.JUMP);

        assertEquals("JUMP", lastSfxName(),
                "soundMap should be cleared — presentation must use the base-name route");
    }

    @Test
    public void doubleResetDoesNotThrow() {
        am.resetState();
        // A second reset on an already-cleared instance should not throw
        am.resetState();
    }

    @Test
    void setRomAndProfilePublishNewGenerationsWithoutRetargetingSnapshots() {
        Rom firstRom = new Rom();
        Rom secondRom = new Rom();
        DacData firstDac = dac(291);
        DacData secondDac = dac(292);
        DacData profileDac = dac(293);
        StubSmpsLoader firstLoader = loader(firstDac, 0x90, (byte) 0x11);
        StubSmpsLoader secondLoader = loader(secondDac, 0x90, (byte) 0x22);
        SwitchingAudioProfile firstProfile = new SwitchingAudioProfile(firstLoader);
        firstProfile.loaders.put(firstRom, firstLoader);
        firstProfile.loaders.put(secondRom, secondLoader);
        am.setAudioProfile(firstProfile);
        am.setRom(firstRom);
        ObservedSource first = observeBaseSfx(0x90);
        AudioLogicalSnapshot oldSnapshot = am.captureLogicalSnapshot();

        am.setRom(secondRom);
        ObservedSource second = observeBaseSfx(0x90);
        SwitchingAudioProfile replacementProfile =
                new SwitchingAudioProfile(loader(
                        profileDac, 0x90, (byte) 0x33));
        am.setAudioProfile(replacementProfile);
        ObservedSource replacement = observeBaseSfx(0x90);

        assertTrue(second.descriptor().dependencyGeneration()
                > first.descriptor().dependencyGeneration());
        assertTrue(replacement.descriptor().dependencyGeneration()
                > second.descriptor().dependencyGeneration());
        assertSame(firstDac, first.dac());
        assertSame(secondDac, second.dac());
        assertSame(profileDac, replacement.dac());
        assertSame(secondRom, replacementProfile.lastRom,
                "setAudioProfile must rebuild against the currently published ROM");
        am.restoreLogicalSnapshot(oldSnapshot);
        assertEquals(first.descriptor(), currentSource().descriptor());
        assertSame(firstDac, currentSource().dac());
    }

    @Test
    void nullRomAndProfileClearIncompatibleLoaderAndDacDependencies() {
        Rom rom = new Rom();
        DacData dac = dac(299);
        SwitchingAudioProfile profile = new SwitchingAudioProfile(
                loader(dac, 0x9A, (byte) 0x2A));
        am.setAudioProfile(profile);
        am.setRom(rom);
        ObservedSource initial = observeBaseSfx(0x9A);

        am.setRom(null);
        assertSame(profile, am.getAudioProfile());
        assertFalse(am.playSfx(0x9A),
                "a null ROM must clear the loader/DAC pair");

        am.setRom(rom);
        ObservedSource restored = observeBaseSfx(0x9A);
        assertEquals(initial.descriptor().dependencyGeneration() + 2,
                restored.descriptor().dependencyGeneration());
        assertSame(dac, restored.dac());

        am.setAudioProfile(null);
        assertNull(am.getAudioProfile());
        assertFalse(am.playSfx(0x9A),
                "a null profile must clear the loader/DAC pair");
        assertNull(backend.profileAttempts.get(
                backend.profileAttempts.size() - 1));
    }

    @Test
    void failedRomLoaderConstructionOrDacLoadLeavesTheWholeTuplePublished() {
        Rom firstRom = new Rom();
        Rom throwingCreateRom = new Rom();
        Rom throwingDacRom = new Rom();
        DacData oldDac = dac(301);
        DacData createRetryDac = dac(302);
        DacData dacRetryDac = dac(303);
        StubSmpsLoader oldLoader = loader(oldDac, 0x91, (byte) 0x41);
        StubSmpsLoader createRetry = loader(
                createRetryDac, 0x91, (byte) 0x42);
        StubSmpsLoader dacRetry = loader(
                dacRetryDac, 0x91, (byte) 0x43);
        SwitchingAudioProfile profile = new SwitchingAudioProfile(oldLoader);
        profile.loaders.put(firstRom, oldLoader);
        profile.loaders.put(throwingCreateRom, createRetry);
        profile.loaders.put(throwingDacRom, dacRetry);
        am.setAudioProfile(profile);
        am.setRom(firstRom);
        ObservedSource initial = observeBaseSfx(0x91);

        profile.throwCreateFor = throwingCreateRom;
        assertThrows(IllegalStateException.class,
                () -> am.setRom(throwingCreateRom));
        assertSame(am.getAudioProfile(), profile);
        ObservedSource afterCreateFailure = observeBaseSfx(0x91);
        assertEquals(initial, afterCreateFailure);
        profile.throwCreateFor = null;
        am.setRom(throwingCreateRom);
        ObservedSource afterCreateRetry = observeBaseSfx(0x91);
        assertEquals(initial.descriptor().dependencyGeneration() + 1,
                afterCreateRetry.descriptor().dependencyGeneration());
        assertSame(createRetryDac, afterCreateRetry.dac());

        dacRetry.dacFailure = new IllegalStateException("DAC load failed");
        assertThrows(IllegalStateException.class,
                () -> am.setRom(throwingDacRom));
        assertEquals(afterCreateRetry, observeBaseSfx(0x91));
        dacRetry.dacFailure = null;
        am.setRom(throwingDacRom);
        ObservedSource afterDacRetry = observeBaseSfx(0x91);
        assertEquals(afterCreateRetry.descriptor().dependencyGeneration() + 1,
                afterDacRetry.descriptor().dependencyGeneration());
        assertSame(dacRetryDac, afterDacRetry.dac());
    }

    @Test
    void failedProfilePreparationOrBackendPublicationIsAtomicAndRetryable() {
        Rom currentRom = new Rom();
        DacData oldDac = dac(311);
        StubSmpsLoader oldLoader = loader(oldDac, 0x92, (byte) 0x51);
        SwitchingAudioProfile oldProfile = new SwitchingAudioProfile(oldLoader);
        am.setAudioProfile(oldProfile);
        am.setRom(currentRom);
        ObservedSource initial = observeBaseSfx(0x92);

        SwitchingAudioProfile throwingProfile =
                new SwitchingAudioProfile(loader(dac(312), 0x92, (byte) 0x52));
        throwingProfile.throwCreateFor = currentRom;
        assertThrows(IllegalStateException.class,
                () -> am.setAudioProfile(throwingProfile));
        assertSame(oldProfile, am.getAudioProfile());
        assertEquals(initial, observeBaseSfx(0x92));

        StubSmpsLoader throwingDacLoader =
                loader(dac(313), 0x92, (byte) 0x53);
        throwingDacLoader.dacFailure = new IllegalStateException("DAC failed");
        SwitchingAudioProfile throwingDacProfile =
                new SwitchingAudioProfile(throwingDacLoader);
        assertThrows(IllegalStateException.class,
                () -> am.setAudioProfile(throwingDacProfile));
        assertSame(oldProfile, am.getAudioProfile());
        assertEquals(initial, observeBaseSfx(0x92));

        DacData replacementDac = dac(314);
        SwitchingAudioProfile replacement = new SwitchingAudioProfile(
                loader(replacementDac, 0x92, (byte) 0x54));
        backend.failProfile = replacement;
        assertThrows(IllegalStateException.class,
                () -> am.setAudioProfile(replacement));
        assertEquals(java.util.List.of(replacement, oldProfile),
                backend.lastProfileAttempts(2));
        assertSame(oldProfile, am.getAudioProfile());
        assertEquals(initial, observeBaseSfx(0x92));

        SwitchingAudioProfile throwingConfigProfile =
                new SwitchingAudioProfile(
                        loader(dac(315), 0x92, (byte) 0x55));
        throwingConfigProfile.configFailure =
                new AssertionError("config failed");
        int backendAttempts = backend.profileAttempts.size();
        assertThrows(AssertionError.class,
                () -> am.setAudioProfile(throwingConfigProfile));
        assertEquals(backendAttempts, backend.profileAttempts.size());
        assertSame(oldProfile, am.getAudioProfile());
        assertEquals(initial, observeBaseSfx(0x92));

        backend.failProfile = null;
        am.setAudioProfile(replacement);
        ObservedSource retried = observeBaseSfx(0x92);
        assertEquals(initial.descriptor().dependencyGeneration() + 1,
                retried.descriptor().dependencyGeneration());
        assertSame(replacementDac, retried.dac());
    }

    @Test
    void backendRestoreFailureIsSuppressedWithoutPublishingTheCandidate() {
        DacData oldDac = dac(321);
        SwitchingAudioProfile oldProfile = new SwitchingAudioProfile(
                loader(oldDac, 0x93, (byte) 0x61));
        am.setAudioProfile(oldProfile);
        am.setRom(new Rom());
        ObservedSource initial = observeBaseSfx(0x93);
        SwitchingAudioProfile replacement = new SwitchingAudioProfile(
                loader(dac(322), 0x93, (byte) 0x62));
        backend.failProfile = replacement;
        backend.failRestoreProfile = oldProfile;

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> am.setAudioProfile(replacement));

        assertEquals(1, failure.getSuppressed().length);
        assertSame(oldProfile, am.getAudioProfile());
        assertEquals(initial, observeBaseSfx(0x93));

        backend.failProfile = null;
        backend.failRestoreProfile = null;
        am.setAudioProfile(replacement);
        ObservedSource retried = observeBaseSfx(0x93);
        assertEquals(initial.descriptor().dependencyGeneration() + 1,
                retried.descriptor().dependencyGeneration());
    }

    @Test
    void reentrantRomReplacementCannotMixOneBaseResolutionTuple() {
        Rom firstRom = new Rom();
        Rom secondRom = new Rom();
        DacData firstDac = dac(331);
        DacData secondDac = dac(332);
        StubSmpsLoader firstLoader = loader(
                firstDac, 0x94, (byte) 0x71);
        StubSmpsLoader secondLoader = loader(
                secondDac, 0x94, (byte) 0x72);
        firstLoader.sfxResults.put(
                0x93, new StubSmpsData("sfx", (byte) 0x71));
        secondLoader.sfxResults.put(
                0x93, new StubSmpsData("sfx", (byte) 0x72));
        SwitchingAudioProfile profile = new SwitchingAudioProfile(firstLoader);
        profile.loaders.put(firstRom, firstLoader);
        profile.loaders.put(secondRom, secondLoader);
        am.setAudioProfile(profile);
        am.setRom(firstRom);
        ObservedSource initial = observeBaseSfx(0x93);
        firstLoader.runOnSfxLoadNumber = firstLoader.sfxLoadCount + 2;
        firstLoader.onSfxLoad = () -> am.setRom(secondRom);

        assertTrue(am.playSfx(0x94));
        am.presentFrame(PresentationMode.SILENT);
        ObservedSource reentrant = currentSource();

        assertEquals(0x94, reentrant.descriptor().id(),
                "the reentrant command must be admitted");
        assertEquals(initial.descriptor().dependencyGeneration(),
                reentrant.descriptor().dependencyGeneration());
        assertEquals(initial.descriptor().dataHash(),
                reentrant.descriptor().dataHash(),
                "the old generation must retain the old program marker");
        assertSame(firstDac, reentrant.dac());
        am.update();
        ObservedSource afterReplacement = observeBaseSfx(0x94);
        assertTrue(afterReplacement.descriptor().dependencyGeneration()
                > reentrant.descriptor().dependencyGeneration());
        assertNotEquals(reentrant.descriptor().dataHash(),
                afterReplacement.descriptor().dataHash());
        assertSame(secondDac, afterReplacement.dac());
    }

    @Test
    void reentrantProfileReplacementCannotMixBaseDependenciesOrPolicy() {
        Rom rom = new Rom();
        DacData oldDac = dac(341);
        DacData newDac = dac(342);
        StubSmpsLoader oldLoader = loader(
                oldDac, 0x95, (byte) 0x73);
        StubSmpsLoader newLoader = loader(
                newDac, 0x95, (byte) 0x74);
        oldLoader.sfxResults.put(
                0x94, new StubSmpsData("sfx", (byte) 0x73));
        newLoader.sfxResults.put(
                0x94, new StubSmpsData("sfx", (byte) 0x74));
        SmpsSequencerConfig oldConfig = new SmpsSequencerConfig.Builder()
                .tempoMode(SmpsSequencerConfig.TempoMode.TIMEOUT)
                .build();
        SmpsSequencerConfig newConfig = new SmpsSequencerConfig.Builder()
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                .build();
        PolicyProfile oldProfile = new PolicyProfile(
                "base", oldLoader, oldConfig, 0x41, true, true);
        PolicyProfile newProfile = new PolicyProfile(
                "base", newLoader, newConfig, 0x52, false, false);
        am.setAudioProfile(oldProfile);
        am.setRom(rom);
        ObservedSource initial = observeBaseSfx(0x94);
        oldLoader.runOnSfxLoadNumber = oldLoader.sfxLoadCount + 2;
        oldLoader.onSfxLoad = () -> am.setAudioProfile(newProfile);

        assertTrue(am.playSfx(0x95));
        am.presentFrame(PresentationMode.SILENT);
        var snapshot = am.shadowSmpsDriverSnapshotForTesting();
        var reentrant = snapshot.sequencers().get(
                snapshot.sequencers().size() - 1);

        assertEquals(0x95, reentrant.source().id(),
                "the reentrant command must be admitted");
        assertEquals(initial.descriptor().dependencyGeneration(),
                reentrant.source().dependencyGeneration());
        assertEquals(initial.descriptor().dataHash(),
                reentrant.source().dataHash(),
                "the old loader/program must remain paired to its generation");
        assertSame(oldDac, reentrant.dacData());
        assertEquals(SmpsSequencerConfig.TempoMode.TIMEOUT,
                reentrant.config().getTempoMode());
        assertEquals(0x41, reentrant.snapshot().sfxPriority());
        assertTrue(reentrant.snapshot().specialSfx());
        am.update();
        ObservedSource replacement = observeBaseSfx(0x95);
        assertSame(newDac, replacement.dac());
        assertNotEquals(initial.descriptor().dataHash(),
                replacement.descriptor().dataHash());
    }

    @Test
    void reentrantProfileCallbackCannotRetargetTheCapturedBaseHandle() {
        Rom rom = new Rom();
        DacData oldDac = dac(343);
        DacData newDac = dac(344);
        StubSmpsLoader oldLoader = loader(
                oldDac, 0x99, (byte) 0x7A);
        StubSmpsLoader newLoader = loader(
                newDac, 0x99, (byte) 0x7B);
        oldLoader.sfxResults.put(
                0x98, new StubSmpsData("sfx", (byte) 0x7A));
        newLoader.sfxResults.put(
                0x98, new StubSmpsData("sfx", (byte) 0x7B));
        SmpsSequencerConfig oldConfig = new SmpsSequencerConfig.Builder()
                .tempoMode(SmpsSequencerConfig.TempoMode.TIMEOUT)
                .build();
        SmpsSequencerConfig newConfig = new SmpsSequencerConfig.Builder()
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                .build();
        PolicyProfile oldProfile = new PolicyProfile(
                "old", oldLoader, oldConfig, 0x43, true, true);
        PolicyProfile newProfile = new PolicyProfile(
                "new", newLoader, newConfig, 0x54, false, false);
        am.setAudioProfile(oldProfile);
        am.setRom(rom);
        ObservedSource initial = observeBaseSfx(0x98);
        oldProfile.onPresentationGameId =
                () -> am.setAudioProfile(newProfile);

        assertTrue(am.playSfx(0x99));
        am.presentFrame(PresentationMode.SILENT);
        var snapshot = am.shadowSmpsDriverSnapshotForTesting();
        var reentrant = snapshot.sequencers().get(
                snapshot.sequencers().size() - 1);

        assertEquals(0x99, reentrant.source().id());
        assertEquals(initial.descriptor().dependencyGeneration(),
                reentrant.source().dependencyGeneration());
        assertEquals(initial.descriptor().dataHash(),
                reentrant.source().dataHash());
        assertSame(oldDac, reentrant.dacData());
        assertEquals(SmpsSequencerConfig.TempoMode.TIMEOUT,
                reentrant.config().getTempoMode());
        assertEquals(0x43, reentrant.snapshot().sfxPriority());
        assertTrue(reentrant.snapshot().specialSfx());
        assertSame(newProfile, am.getAudioProfile(),
                "the callback's later publication still succeeds");
    }

    @Test
    void profilePresentationSetupIsAtomicWithoutAnExistingOwnerAndCanRetry() {
        am.setRom(new Rom());
        CoordinatedProfile candidate = new CoordinatedProfile(
                "candidate", loader(dac(351), 0x96, (byte) 0x75));
        candidate.configureFailure =
                new IllegalStateException("handler setup failed");
        int backendAttempts = backend.profileAttempts.size();

        assertThrows(IllegalStateException.class,
                () -> am.setAudioProfile(candidate));

        assertNull(am.getAudioProfile());
        assertNull(backend.activeProfile);
        assertEquals(backendAttempts, backend.profileAttempts.size(),
                "handler preparation must precede backend mutation");
        assertThrows(IllegalArgumentException.class,
                () -> am.presentationCoordFlagHandlersForTesting()
                        .handlerFor("candidate"));

        candidate.configureFailure = null;
        am.setAudioProfile(candidate);
        assertSame(candidate, backend.activeProfile);
        assertTrue(am.playSfx(0x96));
        am.presentFrame(PresentationMode.SILENT);
        var snapshot = am.shadowSmpsDriverSnapshotForTesting();
        var sequencer = snapshot.sequencers().get(
                snapshot.sequencers().size() - 1);
        assertSame(am.presentationCoordFlagHandlersForTesting()
                        .handlerFor("candidate"),
                sequencer.config().getCoordFlagHandler());
    }

    @Test
    void profilePresentationErrorRollsBackLiveOwnerBackendAndTuple() {
        am.setRom(new Rom());
        CoordinatedProfile oldProfile = new CoordinatedProfile(
                "old", loader(dac(361), 0x97, (byte) 0x76));
        am.setAudioProfile(oldProfile);
        ObservedSource initial = observeBaseSfx(0x97);
        SmpsCoordFlagHandlerOwner owner =
                am.presentationCoordFlagHandlersForTesting();
        CoordFlagHandler oldHandler = owner.handlerFor("old");
        CoordinatedProfile candidate = new CoordinatedProfile(
                "candidate", loader(dac(362), 0x97, (byte) 0x77));
        candidate.configureFailure = new AssertionError(
                "handler setup error");

        assertThrows(AssertionError.class,
                () -> am.setAudioProfile(candidate));

        assertSame(oldProfile, am.getAudioProfile());
        assertSame(oldProfile, backend.activeProfile);
        assertSame(oldHandler, owner.handlerFor("old"));
        assertThrows(IllegalArgumentException.class,
                () -> owner.handlerFor("candidate"));
        assertEquals(initial, observeBaseSfx(0x97));
    }

    @Test
    void backendErrorRestoresActualProfileAndLeavesTupleUnpublished() {
        am.setRom(new Rom());
        SwitchingAudioProfile oldProfile = new SwitchingAudioProfile(
                loader(dac(371), 0x98, (byte) 0x78));
        am.setAudioProfile(oldProfile);
        ObservedSource initial = observeBaseSfx(0x98);
        SwitchingAudioProfile candidate = new SwitchingAudioProfile(
                loader(dac(372), 0x98, (byte) 0x79));
        backend.failProfileError = candidate;

        assertThrows(AssertionError.class,
                () -> am.setAudioProfile(candidate));

        assertSame(oldProfile, backend.activeProfile);
        assertSame(oldProfile, am.getAudioProfile());
        assertEquals(initial, observeBaseSfx(0x98));
    }

    @Test
    void sourceTuplePublicationsUseVolatileImmutableReferences()
            throws Exception {
        assertTrue(Modifier.isVolatile(AudioManager.class
                .getDeclaredField("baseAudioSource").getModifiers()));
        assertTrue(Modifier.isVolatile(AudioManager.class
                .getDeclaredField("donorAudioSources").getModifiers()));
    }

    private ObservedSource observeBaseSfx(int sfxId) {
        assertTrue(am.playSfx(sfxId));
        am.presentFrame(PresentationMode.SILENT);
        ObservedSource observed = currentSource();
        am.update();
        return observed;
    }

    private ObservedSource currentSource() {
        var snapshot = am.shadowSmpsDriverSnapshotForTesting();
        assertNotNull(snapshot);
        var sequencer = snapshot.sequencers().get(
                snapshot.sequencers().size() - 1);
        return new ObservedSource(sequencer.source(), sequencer.dacData());
    }

    private static DacData dac(int cycles) {
        return new DacData(Map.of(), Map.of(), cycles);
    }

    private static StubSmpsLoader loader(
            DacData dac, int sfxId, byte marker) {
        StubSmpsLoader loader = new StubSmpsLoader(dac);
        loader.sfxResults.put(sfxId, new StubSmpsData("sfx", marker));
        return loader;
    }

    private record ObservedSource(
            SmpsSourceDescriptor descriptor, DacData dac) {
    }

    private String lastSfxName() {
        var entries = am.commandTimeline().entries();
        return ((AudioCommand.PlaySfx) entries.get(entries.size() - 1).command()).sfxName();
    }

    // --- Test doubles ---

    /**
     * Tracks which ring channel (LEFT/RIGHT) was last requested via playSfx(GameSound).
     * Also records fallback and SMPS play calls for other assertions.
     */
    private static class RingTrackingBackend extends NullAudioBackend {
        boolean lastPlayedRingLeft;
        String lastFallbackName;
        String lastSmpsName;
        final java.util.List<GameAudioProfile> profileAttempts =
                new java.util.ArrayList<>();
        GameAudioProfile failProfile;
        GameAudioProfile failRestoreProfile;
        GameAudioProfile failProfileError;
        GameAudioProfile activeProfile;

        @Override
        public void setAudioProfile(GameAudioProfile profile) {
            profileAttempts.add(profile);
            activeProfile = profile;
            if (failProfileError != null && profile == failProfileError) {
                throw new AssertionError("backend rejected profile");
            }
            if ((failProfile != null && profile == failProfile)
                    || (failRestoreProfile != null
                    && profile == failRestoreProfile)) {
                throw new IllegalStateException("backend rejected profile");
            }
        }

        java.util.List<GameAudioProfile> lastProfileAttempts(int count) {
            return java.util.List.copyOf(profileAttempts.subList(
                    profileAttempts.size() - count, profileAttempts.size()));
        }

        @Override
        public void playSfx(String sfxName, float pitch) {
            if ("RING_LEFT".equals(sfxName)) {
                lastPlayedRingLeft = true;
            } else if ("RING_RIGHT".equals(sfxName)) {
                lastPlayedRingLeft = false;
            }
            lastFallbackName = sfxName;
            lastSmpsName = null;
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch) {
            lastSmpsName = data.toString();
            lastFallbackName = null;
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch,
                                SmpsSequencerConfig config) {
            lastSmpsName = data.toString();
            lastFallbackName = null;
        }
    }

    private static class StubSmpsData extends AbstractSmpsData {
        final String name;

        StubSmpsData(String name) {
            this(name, (byte) 0);
        }

        StubSmpsData(String name, byte marker) {
            super(new byte[] {marker}, 0);
            this.name = name;
        }

        @Override protected void parseHeader() {}
        @Override public byte[] getVoice(int voiceId) { return new byte[0]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }

        @Override
        public String toString() { return name; }
    }

    private static class StubSmpsLoader implements SmpsLoader {
        final Map<Integer, AbstractSmpsData> sfxResults = new java.util.HashMap<>();
        final DacData dac;
        RuntimeException dacFailure;
        int sfxLoadCount;
        int runOnSfxLoadNumber = -1;
        Runnable onSfxLoad;

        StubSmpsLoader() {
            this(EMPTY_DAC);
        }

        StubSmpsLoader(DacData dac) {
            this.dac = dac;
        }

        @Override public AbstractSmpsData loadMusic(int musicId) { return null; }
        @Override public AbstractSmpsData loadSfx(int sfxId) {
            AbstractSmpsData result = sfxResults.get(sfxId);
            sfxLoadCount++;
            if (sfxLoadCount == runOnSfxLoadNumber) {
                Runnable callback = onSfxLoad;
                onSfxLoad = null;
                if (callback != null) {
                    callback.run();
                }
            }
            return result;
        }
        @Override public AbstractSmpsData loadSfx(String sfxName) { return null; }
        @Override public DacData loadDacData() {
            if (dacFailure != null) {
                throw dacFailure;
            }
            return dac;
        }
    }

    private static class StubAudioProfile implements GameAudioProfile {
        protected final SmpsLoader loader;
        private final SmpsSequencerConfig config =
                new SmpsSequencerConfig.Builder().build();

        StubAudioProfile() { this.loader = new StubSmpsLoader(); }
        StubAudioProfile(SmpsLoader loader) { this.loader = loader; }

        @Override public SmpsLoader createSmpsLoader(Rom rom) { return loader; }
        @Override public SmpsSequencerConfig getSequencerConfig() {
            return config;
        }
        @Override public int getSpeedShoesOnCommandId() { return -1; }
        @Override public int getSpeedShoesOffCommandId() { return -1; }
        @Override public int getInvincibilityMusicId() { return -1; }
        @Override public int getExtraLifeMusicId() { return -1; }
        @Override public int getDrowningMusicId() { return -1; }
        @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
    }

    private static final class SwitchingAudioProfile extends StubAudioProfile {
        final Map<Rom, SmpsLoader> loaders = new java.util.IdentityHashMap<>();
        Rom throwCreateFor;
        Rom lastRom;
        Throwable configFailure;

        private SwitchingAudioProfile(SmpsLoader loader) {
            super(loader);
        }

        @Override
        public SmpsLoader createSmpsLoader(Rom rom) {
            lastRom = rom;
            if (rom == throwCreateFor) {
                throw new IllegalStateException("loader construction failed");
            }
            return loaders.getOrDefault(rom, loader);
        }

        @Override
        public SmpsSequencerConfig getSequencerConfig() {
            if (configFailure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (configFailure instanceof Error error) {
                throw error;
            }
            return super.getSequencerConfig();
        }
    }

    private static final class PolicyProfile extends StubAudioProfile {
        private final String gameId;
        private final SmpsSequencerConfig config;
        private final int priority;
        private final boolean special;
        private final boolean continuous;
        Runnable onPresentationGameId;

        private PolicyProfile(
                String gameId,
                SmpsLoader loader,
                SmpsSequencerConfig config,
                int priority,
                boolean special,
                boolean continuous) {
            super(loader);
            this.gameId = gameId;
            this.config = config;
            this.priority = priority;
            this.special = special;
            this.continuous = continuous;
        }

        @Override
        public String presentationGameId() {
            Runnable callback = onPresentationGameId;
            onPresentationGameId = null;
            if (callback != null) {
                callback.run();
            }
            return gameId;
        }
        @Override public SmpsSequencerConfig getSequencerConfig() {
            return config;
        }
        @Override public int getSfxPriority(int soundId) { return priority; }
        @Override public boolean isSpecialSfx(int soundId) { return special; }
        @Override public boolean isContinuousSfx(int soundId) {
            return continuous;
        }
    }

    private static final class CoordinatedProfile extends StubAudioProfile {
        private final String gameId;
        private final SmpsSequencerConfig config;
        Throwable configureFailure;

        private CoordinatedProfile(String gameId, SmpsLoader loader) {
            super(loader);
            this.gameId = gameId;
            this.config = new SmpsSequencerConfig.Builder()
                    .coordFlagHandler(new ArbitraryHandler())
                    .build();
        }

        @Override public String presentationGameId() { return gameId; }
        @Override public SmpsSequencerConfig getSequencerConfig() {
            return config;
        }
        @Override
        public void configurePresentationCoordFlagHandlers(
                SmpsCoordFlagHandlerOwner owner) {
            owner.register(gameId, ignored -> new ArbitraryHandler());
            if (configureFailure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (configureFailure instanceof Error error) {
                throw error;
            }
        }
    }

    private static final class ArbitraryHandler implements CoordFlagHandler {
        @Override
        public boolean handleFlag(
                CoordFlagContext context,
                SmpsSequencer.Track track,
                int command) {
            return false;
        }

        @Override public int flagParamLength(int command) { return -1; }
    }
}
