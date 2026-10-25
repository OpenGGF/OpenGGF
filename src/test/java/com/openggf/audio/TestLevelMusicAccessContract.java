package com.openggf.audio;

import com.openggf.audio.presentation.AudioRequestService;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.rewind.AudioTimelineEntry;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Original f056 production behavior, before admission-owner extraction. No injected production state. */
class TestLevelMusicAccessContract {
    private final List<AudioManager> managers = new ArrayList<>();
    private final List<LevelMusicAccess.Control> controls = new ArrayList<>();
    private final List<Rom> roms = new ArrayList<>();
    private AudioManager audio;

    @BeforeEach void setUp() {
        SonicConfigurationService.getInstance().resetToDefaults();
        audio = manager();
    }

    @AfterEach void tearDown() throws Exception {
        for (var control : controls) control.close();
        for (var manager : managers) {
            if (manager.mutatorBinding != null) manager.mutatorBinding.close();
            manager.setRequestObserver(AudioRequestObserver.NONE);
            manager.destroy();
        }
        for (var rom : roms) rom.close();
    }

    private AudioManager manager() {
        // Construct the receiver itself. spy(existing) copies nested owners bound to the old receiver.
        var manager = spy(AudioManager.class);
        managers.add(manager);
        assertFalse(manager.isRewindReplaySuppressed(), "a silent manager cannot discriminate publication");
        assertEquals(NullAudioBackend.class, manager.getBackend().getClass());
        return manager;
    }

    private LevelMusicAccess.Control acquire(AudioManager manager, boolean entry) {
        var control = entry ? LevelMusicAccess.suppressAutomaticEntry(manager)
                : LevelMusicAccess.suppressAutomatic(manager);
        controls.add(control);
        return control;
    }

    private void admission(AudioManager manager, boolean automatic, boolean entry) {
        assertEquals(automatic, LevelMusicAccess.allowsAutomatic(manager));
        assertEquals(entry, LevelMusicAccess.allowsEntryMusic(manager));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void constructedManagersKeepMixedNestedScopesIndependentInEitherCloseOrder(boolean ordinaryFirst) {
        var other = manager();
        assertNotSame(audio, other);
        admission(audio, true, true);
        admission(other, true, true);
        var ordinary = acquire(audio, false);
        admission(audio, false, true);
        var entry = acquire(audio, true);
        var nestedEntry = acquire(audio, true);
        admission(audio, false, false);
        admission(other, true, true);
        var otherScope = acquire(other, false);
        (ordinaryFirst ? ordinary : entry).close();
        admission(audio, false, false);
        nestedEntry.close();
        admission(audio, false, !ordinaryFirst);
        (ordinaryFirst ? entry : ordinary).close();
        ordinary.close(); entry.close(); nestedEntry.close();
        admission(audio, true, true);
        admission(other, false, true);
        // Reacquisition detects an accidental negative count after duplicate closes.
        try (var again = acquire(audio, true)) { admission(audio, false, false); }
        otherScope.close();
        admission(other, true, true);
    }

    @Test void everyAcquireQueryAndCloseIncludingDuplicateUsesTheSuppliedManagerMonitor() throws Exception {
        var ordinary = acquire(audio, false);
        var entry = acquire(audio, true);
        var acquired = new AtomicReference<LevelMusicAccess.Control>();
        blockedBy(audio, () -> acquired.set(LevelMusicAccess.suppressAutomatic(audio)));
        controls.add(acquired.get());
        blockedBy(audio, () -> acquired.set(LevelMusicAccess.suppressAutomaticEntry(audio)));
        controls.add(acquired.get());
        blockedBy(audio, () -> assertFalse(LevelMusicAccess.allowsAutomatic(audio)));
        blockedBy(audio, () -> assertFalse(LevelMusicAccess.allowsEntryMusic(audio)));
        blockedBy(audio, () -> assertTrue(LevelMusicAccess.allowsEntryRequest(audio, null)));
        blockedBy(audio, ordinary::close);
        blockedBy(audio, ordinary::close);
        blockedBy(audio, entry::close);
        blockedBy(audio, entry::close);
        synchronized (audio) {
            // The exact monitor is reentrant; there is no token thread affinity.
            try (var scope = acquire(audio, true)) { admission(audio, false, false); }
        }
        for (var scope : controls) onWorker(scope::close);
        onWorker(() -> admission(audio, true, true));
    }

    @Test void resetProfileRomBackendDestroyAndReuseRetainOldScopesOnTheirOriginalManager() {
        var other = manager();
        var ordinary = acquire(audio, false);
        var entry = acquire(audio, true);
        List<Runnable> replacements = List.of(audio::resetState,
                () -> audio.setAudioProfile(new Profile(false)), () -> audio.setRom(new Rom()),
                () -> audio.setBackend(new NullAudioBackend()), audio::destroy,
                () -> { audio.setAudioProfile(new Profile(true)); audio.presentationFrameRate(); });
        for (var replace : replacements) {
            replace.run();
            admission(audio, false, false);
            admission(other, true, true);
        }
        entry.close();
        admission(audio, false, true);
        ordinary.close();
        admission(audio, true, true);
        try (var reused = acquire(audio, true)) { admission(audio, false, false); }
    }

    @Test void closeIsEffectFreeAndTheTokenStronglyRetainsTheExactManagerAfterClose() throws Exception {
        var backend = spy(NullAudioBackend.class);
        audio.setBackend(backend);
        var profile = install(new Profile(true));
        var service = profile.latest();
        audio.playSfx(0xA0);
        var before = service.snapshot();
        var effects = new ArrayList<String>();
        profile.classify = () -> effects.add("classify");
        audio.setRequestObserver((kind, id) -> effects.add("request"));
        try (var binding = bind(effects)) {
            var control = acquire(audio, true);
            int entries = audio.commandTimeline().entryCount();
            long fades = audio.musicFadeOutCount();
            clearInvocations(backend);
            assertTrue(retains(control, audio, 2));
            control.close(); control.close();
            assertTrue(retains(control, audio, 2), "closing retains the exact manager strongly");
            assertEquals(List.of(), effects);
            assertEquals(before, service.snapshot());
            assertEquals(entries, audio.commandTimeline().entryCount());
            assertEquals(fades, audio.musicFadeOutCount());
            verifyNoInteractions(backend);
            admission(audio, true, true);
        }
    }

    @Test void nullFactoriesAndQueriesFailWhileNullCommandsRemainAllowed() {
        assertThrows(NullPointerException.class, () -> LevelMusicAccess.suppressAutomatic(null));
        assertThrows(NullPointerException.class, () -> LevelMusicAccess.suppressAutomaticEntry(null));
        assertThrows(NullPointerException.class, () -> LevelMusicAccess.allowsAutomatic(null));
        assertThrows(NullPointerException.class, () -> LevelMusicAccess.allowsEntryMusic(null));
        assertThrows(NullPointerException.class, () -> LevelMusicAccess.allowsEntryRequest(null, null));
        admission(audio, true, true);
        assertTrue(LevelMusicAccess.allowsEntryRequest(audio, null));
        try (var scope = acquire(audio, true)) {
            assertTrue(LevelMusicAccess.allowsEntryRequest(audio, null));
        }
        admission(audio, true, true);
    }

    @Test void theEntryPredicateExcludesOnlyTheThreeMusicCommandCategories() {
        var music = new AudioCommand.PlayMusic(0x81, AudioCommand.MusicRoute.BASE_SMPS, false, null);
        List<AudioCommand> excluded = List.of(music, new AudioCommand.FadeOutMusic(2, 3), new AudioCommand.StopMusic());
        List<AudioCommand> admitted = List.of(
                new AudioCommand.PlaySfx(0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1, null),
                new AudioCommand.StopAllSfx(), new AudioCommand.StopSmpsSfx(1), new AudioCommand.SilencePsg(1),
                new AudioCommand.SetSpeedShoes(true), new AudioCommand.SetSpeedMultiplier(2),
                new AudioCommand.SetMusicTempoPercent(75), new AudioCommand.ChangeMusicTempo(2),
                new AudioCommand.RetainGlobalStop(0xFD), new AudioCommand.PlaySegaPcm(0xFA, new byte[]{1}, 8000),
                new AudioCommand.ReferenceLimitation(0xFA, "unavailable"), new AudioCommand.StopRawPcm(),
                new AudioCommand.StopSegaPcmAndRetainGlobalStop(1), new AudioCommand.EndMusicOverride(1),
                new AudioCommand.RestoreMusic(AudioCommand.RestoreCause.EXPLICIT), new AudioCommand.ResetRingAlternation(false),
                new AudioCommand.PlayNamespacedMusic(new StreamedMusicPort.TrackRef("owner", "track")),
                new AudioCommand.PlayNamespacedSfx(new StreamedMusicPort.SfxRef("owner", "sfx")));
        try (var ordinary = acquire(audio, false)) {
            for (var command : excluded) assertTrue(LevelMusicAccess.allowsEntryRequest(audio, command));
            try (var entry = acquire(audio, true)) {
                for (var command : excluded) assertFalse(LevelMusicAccess.allowsEntryRequest(audio, command), command.toString());
                for (var command : admitted) assertTrue(LevelMusicAccess.allowsEntryRequest(audio, command), command.toString());
            }
        }
    }

    @Test void directControlsGateBeforeMutatorsObservationRecordingAndFadeCount() {
        var profile = install(new Profile(true));
        var service = profile.latest();
        var before = service.snapshot();
        var effects = new ArrayList<String>();
        profile.classify = () -> effects.add("classify");
        audio.setRequestObserver((kind, id) -> effects.add("request:" + id));
        try (var binding = bind(effects); var entry = acquire(audio, true)) {
            int count = audio.commandTimeline().entryCount();
            long fades = audio.musicFadeOutCount();
            audio.playMusic(0x81); audio.stopMusic(); audio.fadeOutMusic(2, 3);
            assertEquals(List.of(), effects);
            assertEquals(before, service.snapshot());
            assertEquals(count, audio.commandTimeline().entryCount());
            assertEquals(fades, audio.musicFadeOutCount());
            entry.close();
            // Ordinary automatic ownership does not gate explicit director controls.
            try (var ordinary = acquire(audio, false)) {
                audio.playMusic(0x81); audio.stopMusic(); audio.fadeOutMusic(2, 3);
            }
            assertEquals(List.of("music:MUSIC", "request:129", "classify", "music:STOP", "music:FADE"), effects);
            assertEquals(0x81, service.snapshot().pipeline().music0().requestByte());
            assertEquals(count + 2, audio.commandTimeline().entryCount());
            assertEquals(fades + 1, audio.musicFadeOutCount());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0x81, 0xF9, 0xFE, 0xFF})
    void filteredRawRequestsAreObservedAndReturnTrueWithoutTouchingTheRealS2MailboxOrTemplates(int id) {
        var profile = install(new Profile(true));
        var effects = new ArrayList<String>();
        audio.playSfx(0xA0, .75f);
        var service = profile.latest();
        var queued = service.snapshot();
        int entries = audio.commandTimeline().entryCount();
        profile.classify = () -> effects.add("classify");
        audio.setRequestObserver((kind, request) -> effects.add("request:" + request));
        try (var binding = bind(effects); var scope = acquire(audio, true)) {
            assertTrue(audio.playSfx(id, .5f));
            assertEquals(id >= 0xE0 ? "music:COMMAND" : "sfx:" + id, effects.getFirst());
            assertEquals("request:" + id, effects.get(1));
            assertEquals(id == 0x81 ? List.of(effects.getFirst(), "request:" + id, "classify")
                    : List.of(effects.getFirst(), "request:" + id), effects);
            assertEquals(queued, service.snapshot(), "filtering must neither overwrite SFX0 nor add an event template");
            assertEquals(entries, audio.commandTimeline().entryCount());
            scope.close();
            assertTrue(audio.playSfx(id, .5f));
            assertEquals(id, service.snapshot().pipeline().sfx0().requestByte());
            assertEquals(queued.pendingSubmissionEvents().size() + 1, service.snapshot().pendingSubmissionEvents().size());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0xA0, 0xF1, 0xF8, 0xFA, 0xFB, 0xFC, 0xFD})
    void entryScopeStillSubmitsOrdinaryEffectsGapsAndNonMusicSystemCommands(int id) {
        var profile = install(new Profile(true));
        try (var scope = acquire(audio, true)) {
            assertTrue(audio.playSfx(id, .625f));
            var state = profile.latest().snapshot();
            assertEquals(id, state.pipeline().sfx0().requestByte());
            assertEquals(1, state.pendingSubmissionEvents().size());
            var command = state.pipeline().sfx0().payload().command();
            var expected = switch (id) {
                case 0xF8 -> AudioCommand.StopAllSfx.class;
                case 0xFA -> AudioCommand.ReferenceLimitation.class;
                case 0xFB, 0xFC -> AudioCommand.SetSpeedShoes.class;
                case 0xFD -> AudioCommand.RetainGlobalStop.class;
                default -> AudioCommand.PlaySfx.class;
            };
            assertInstanceOf(expected, command);
            if (command instanceof AudioCommand.PlaySfx sfx) assertEquals(.625f, sfx.pitch());
        }
    }

    @Test void rawPcmConstructionAlsoRunsBeforeTheFilterAndIsAdmitted() {
        var profile = new Profile(true) {
            @Override public SegaPcmSpec getSegaPcmSpec() { return new SegaPcmSpec(0, 1, 8000); }
            @Override public byte[] loadSegaPcm(Object rom) { return new byte[]{12}; }
        };
        install(profile);
        try (var scope = acquire(audio, true)) {
            assertTrue(audio.playSfx(0xFA));
            var pcm = assertInstanceOf(AudioCommand.PlaySegaPcm.class,
                    profile.latest().snapshot().pipeline().sfx0().payload().command());
            assertArrayEquals(new byte[]{12}, pcm.pcm());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rawObserverAndClassifierCanAcquireOrReleaseAdmissionBeforeTheLaterQuery(boolean inClassifier) {
        var profile = install(new Profile(true));
        var service = profile.latest();
        var scope = new AtomicReference<LevelMusicAccess.Control>();
        Runnable callback = () -> {
            assertFalse(Thread.holdsLock(audio), "callbacks run after the monitor query, not inside an atomic submit");
            if (scope.get() == null) scope.set(acquire(audio, true)); else scope.get().close();
        };
        if (inClassifier) profile.classify = callback;
        else audio.setRequestObserver((kind, id) -> callback.run());
        var before = service.snapshot();
        assertTrue(audio.playSfx(0x81));
        assertEquals(before, service.snapshot(), "callback acquisition precedes the raw gate");
        assertTrue(audio.playSfx(0x81));
        assertEquals(0x81, service.snapshot().pipeline().sfx0().requestByte(), "callback release precedes the raw gate");
    }

    @Test void directQueryReleasesTheMonitorAndDoesNotAtomicallyCheckAndSubmit() {
        var profile = install(new Profile(true));
        audio.setRequestObserver((kind, id) -> {
            assertFalse(Thread.holdsLock(audio));
            var scope = new AtomicReference<LevelMusicAccess.Control>();
            try { onWorker(() -> scope.set(LevelMusicAccess.suppressAutomaticEntry(audio))); }
            catch (Exception failure) { throw new AssertionError(failure); }
            controls.add(scope.get());
        });
        audio.playMusic(0x81);
        admission(audio, false, false);
        assertEquals(0x81, profile.latest().snapshot().pipeline().music0().requestByte(),
                "the original direct query is not repeated after the observer acquired a scope");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rawCallbacksUseCapturedSourceButTheThenCurrentService(boolean inClassifier) {
        var original = install(new Profile(false));
        original.override = true;
        var oldService = original.latest();
        var before = oldService.snapshot();
        var replacement = new Profile(true);
        Runnable swap = () -> audio.setAudioProfile(replacement);
        if (inClassifier) original.classify = swap;
        else audio.setRequestObserver((kind, id) -> swap.run());
        assertTrue(audio.playSfx(0x81));
        assertEquals(before, oldService.snapshot());
        assertNotSame(oldService, replacement.latest());
        var command = assertInstanceOf(AudioCommand.PlayMusic.class,
                replacement.latest().snapshot().pipeline().sfx0().payload().command());
        assertEquals(AudioCommand.MusicRoute.FALLBACK_WAV, command.route());
        assertTrue(command.override(), "classification uses the captured original profile");
    }

    @Test void directMusicCapturesSourceAfterItsObserverInstead() {
        var original = install(new Profile(false));
        original.override = true;
        var replacement = new Profile(true);
        audio.setRequestObserver((kind, id) -> audio.setAudioProfile(replacement));
        audio.playMusic(0x81);
        var command = assertInstanceOf(AudioCommand.PlayMusic.class,
                replacement.latest().snapshot().pipeline().music0().payload().command());
        assertEquals(AudioCommand.MusicRoute.BASE_SMPS, command.route());
        assertFalse(command.override());
        assertEquals(0, original.latest().snapshot().pipeline().music0().requestByte());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void clearedCurrentServiceFailsOnlyWhenTheLaterFilterAllowsSubmission(boolean deny) {
        var profile = install(new Profile(true));
        var retired = profile.latest();
        var before = retired.snapshot();
        audio.setRequestObserver((kind, id) -> {
            audio.destroy();
            if (deny) acquire(audio, true);
        });
        if (deny) assertTrue(audio.playSfx(0x81));
        else assertThrows(NullPointerException.class, () -> audio.playSfx(0x81));
        assertEquals(1, profile.services.size(), "the later access does not rebuild a cleared service");
        assertEquals(before, retired.snapshot());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void observerAndClassifierFailuresRetainPriorEffectsWithoutSubmitting(boolean classifierFailure) {
        var profile = new Profile(true);
        var effects = new ArrayList<String>();
        profile.prepare = () -> effects.add("prepare");
        audio.setAudioProfile(profile);
        var failure = new IllegalStateException("callback failure");
        audio.setRequestObserver((kind, id) -> {
            effects.add("observe");
            if (!classifierFailure) throw failure;
        });
        profile.classify = () -> { effects.add("classify"); acquire(audio, true); throw failure; };
        try (var binding = bind(effects)) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> audio.playSfx(0x81)));
            assertEquals(classifierFailure ? List.of("sfx:129", "prepare", "observe", "classify")
                    : List.of("sfx:129", "prepare", "observe"), effects);
            assertEquals(0, profile.latest().snapshot().pipeline().sfx0().requestByte());
            assertEquals(List.of(), profile.latest().snapshot().pendingSubmissionEvents());
            admission(audio, !classifierFailure, !classifierFailure);
        }
    }

    @Test void rawSuppressionStillNotifiesTheMutatorBeforeSkippingPreparationAndObservation() {
        var profile = new Profile(true);
        var effects = new ArrayList<String>();
        audio.setAudioProfile(profile);
        audio.setRequestObserver((kind, id) -> effects.add("observe"));
        try (var binding = bind(effects);
             var replay = audio.beginRewindReplay(0, 0, AudioReplayReason.SEEK)) {
            assertFalse(audio.playSfx(0x81));
            assertEquals(List.of("sfx:129"), effects);
            assertEquals(List.of(), profile.services);
            try (var entry = acquire(audio, true)) {
                audio.playMusic(0x81); audio.stopMusic(); audio.fadeOutMusic(2, 3);
                assertEquals(List.of("sfx:129"), effects);
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rawPreparationCanAcquireAdmissionAndPreparationFailurePrecedesObservation(boolean failPreparation) {
        var profile = new Profile(true);
        var effects = new ArrayList<String>();
        var failure = new IllegalStateException("request-service preparation");
        profile.prepare = () -> {
            effects.add("prepare"); acquire(audio, true);
            if (failPreparation) throw failure;
        };
        audio.setAudioProfile(profile);
        audio.setRequestObserver((kind, id) -> effects.add("observe"));
        profile.classify = () -> effects.add("classify");
        try (var binding = bind(effects)) {
            if (failPreparation) {
                assertSame(failure, assertThrows(IllegalStateException.class, () -> audio.playSfx(0x81)));
                assertEquals(List.of("sfx:129", "prepare"), effects);
                assertEquals(List.of(), profile.services);
            } else {
                assertTrue(audio.playSfx(0x81));
                assertEquals(List.of("sfx:129", "prepare", "observe", "classify"), effects);
                assertEquals(0, profile.latest().snapshot().pipeline().sfx0().requestByte());
                assertEquals(List.of(), profile.latest().snapshot().pendingSubmissionEvents());
            }
            admission(audio, false, false);
        }
    }

    @Test void enumResolutionAndNoArgumentFadeEvaluateTheCurrentProfileBeforeTheInnerGate() {
        var profile = install(new Profile(true));
        var effects = new ArrayList<String>();
        profile.resolve = () -> effects.add("resolve");
        profile.fade = () -> effects.add("fade-profile");
        var before = profile.latest().snapshot();
        try (var entry = acquire(audio, true)) {
            assertTrue(audio.playMusic(GameMusic.SPECIAL_STAGE), "resolution can succeed while the int request is blocked");
            assertFalse(audio.playMusic((GameMusic) null));
            audio.fadeOutMusic();
            assertEquals(List.of("resolve", "fade-profile"), effects);
            assertEquals(before, profile.latest().snapshot());
            assertEquals(0, audio.musicFadeOutCount());
            var failure = new IllegalStateException("profile evaluation");
            profile.resolve = () -> { throw failure; };
            profile.fade = () -> { throw failure; };
            assertSame(failure, assertThrows(IllegalStateException.class, () -> audio.playMusic(GameMusic.SPECIAL_STAGE)));
            assertSame(failure, assertThrows(IllegalStateException.class, audio::fadeOutMusic));
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @Test void originalNonSilentRootDispatchesAPrequeuedRawS2FadeDuringEntrySuppression() throws Exception {
        var rom = new Rom(); roms.add(rom);
        assertTrue(rom.open(RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath()));
        audio.setRom(rom);
        var events = new ArrayList<Sonic2SoundRequestService.Event>();
        audio.setAudioProfile(new Sonic2AudioProfile(events::add));
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(0x81, audio.captureLogicalSnapshot().presentation().activeMusic().musicId(),
                "actual Root publication, not a createSilent admission-only control");
        assertTrue(audio.playSfx(0xF9));
        var queued = audio.captureLogicalSnapshot().forwardServiceSnapshot();
        int before = audio.commandTimeline().entryCount();
        try (var entry = acquire(audio, true)) {
            assertTrue(audio.playSfx(0xFE));
            assertEquals(queued, audio.captureLogicalSnapshot().forwardServiceSnapshot());
            // The submitted song may still own a deferred load. Admission does not
            // bypass that service boundary; advance until its queued consequence commits.
            for (int frame = 0; frame < 60 && commands().subList(before, commands().size()).stream()
                    .noneMatch(AudioCommand.FadeOutMusic.class::isInstance); frame++)
                audio.presentFrame(PresentationMode.FORWARD);
            assertTrue(commands().subList(before, commands().size()).stream()
                    .anyMatch(AudioCommand.FadeOutMusic.class::isInstance), "there is no dispatch-time admission filter");
            assertTrue(events.stream().anyMatch(event -> event instanceof Sonic2SoundRequestService.Submission submission
                    && submission.rawRequestId() == 0xF9
                    && submission.sourceMailbox() == com.openggf.game.sonic2.audio.Sonic2SoundRequestPipeline.SourceSlot.SFX0));
            assertFalse(events.stream().anyMatch(event -> event instanceof Sonic2SoundRequestService.Submission submission
                    && submission.rawRequestId() == 0xFE));
            assertTrue(audio.captureLogicalSnapshot().presentation().smpsLogical().sequencers().stream()
                    .filter(sequencer -> !sequencer.sfx())
                    .anyMatch(sequencer -> sequencer.snapshot().fade().active() && sequencer.snapshot().fade().fadeOut()),
                    "the committed S2 fade belongs to the music sequencer");
        }
    }

    @Test void musicMailboxNamedSemanticSecondaryAndRestoreSpeedIngressRemainOutsideTheEntryGate() {
        var profile = install(new Profile(true));
        audio.setSoundMap(Map.of(GameSound.JUMP, 0xA0));
        try (var entry = acquire(audio, true)) {
            audio.playMusicMailboxNativeRequest(0x81);
            assertEquals(0x81, profile.latest().snapshot().pipeline().music0().requestByte());
            audio.playSfx(GameSound.JUMP);
            assertEquals(0xA0, profile.latest().snapshot().pipeline().sfx0().requestByte());
            audio.playSecondarySfx(GameSound.JUMP);
            assertEquals(0xA0, profile.latest().snapshot().pipeline().sfx1().requestByte());
            audio.playSfx("named");
            audio.restoreMusic(); audio.setSpeedShoes(true); audio.setSpeedMultiplier(2); audio.endMusicOverride(1);
            assertTrue(commands().stream().anyMatch(AudioCommand.PlaySfx.class::isInstance));
            assertTrue(commands().stream().anyMatch(AudioCommand.RestoreMusic.class::isInstance));
            assertTrue(commands().stream().anyMatch(AudioCommand.SetSpeedShoes.class::isInstance));
            assertTrue(commands().stream().anyMatch(AudioCommand.SetSpeedMultiplier.class::isInstance));
            assertTrue(commands().stream().anyMatch(AudioCommand.EndMusicOverride.class::isInstance));
        }
    }

    @Test void donorAndNamespacedIngressRemainOutsideEntryAdmission() {
        var loader = new AudioTestFixtures.StubSmpsLoader();
        loader.musicResults.put(1, new AudioTestFixtures.StubSmpsData("donor-music"));
        loader.sfxResults.put(0xA0, new AudioTestFixtures.StubSmpsData("donor-sfx"));
        var profile = new Sonic2AudioProfile();
        audio.registerDonorLoader("s2", loader, AudioTestFixtures.EMPTY_DAC, profile.getSequencerConfig(), profile);
        var port = mock(StreamedMusicPort.class);
        var track = new StreamedMusicPort.TrackRef("owner", "track");
        var sfx = new StreamedMusicPort.SfxRef("owner", "sfx");
        when(port.outputRate()).thenReturn(48000);
        when(port.hasTrack(track)).thenReturn(true);
        when(port.hasSfx(sfx)).thenReturn(true);
        audio.installStreamedMusicPort(port);
        try (var entry = acquire(audio, true)) {
            audio.playDonorMusic("s2", 1); audio.playDonorSfx("s2", 0xA0);
            assertTrue(audio.playNamespacedMusic(track));
            assertTrue(audio.playNamespacedSfx(sfx));
            assertTrue(commands().stream().anyMatch(command -> command instanceof AudioCommand.PlayMusic music
                    && music.route() == AudioCommand.MusicRoute.DONOR_SMPS && "s2".equals(music.donorGameId())));
            assertTrue(commands().stream().anyMatch(command -> command instanceof AudioCommand.PlaySfx sound
                    && sound.route() == AudioCommand.SfxRoute.DONOR_SMPS && "s2".equals(sound.donorGameId())));
            assertTrue(commands().contains(new AudioCommand.PlayNamespacedMusic(track)));
            assertTrue(commands().contains(new AudioCommand.PlayNamespacedSfx(sfx)));
        } finally { audio.resetStreamedMusicPort(); }
    }

    private List<AudioCommand> commands() {
        return audio.commandTimeline().entries().stream().map(AudioTimelineEntry::command).toList();
    }

    private Profile install(Profile profile) {
        audio.setRom(new Rom());
        audio.setAudioProfile(profile);
        audio.presentationFrameRate();
        assertEquals(1, profile.services.size());
        return profile;
    }

    private AudioMutatorAccess.Binding bind(List<String> effects) {
        return AudioMutatorAccess.bind(audio, new AudioMutatorAccess.Listener() {
            @Override public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { effects.add("music:" + intent.kind()); }
            @Override public void onNativeSfx(int id) { effects.add("sfx:" + id); }
        });
    }

    private static class Profile extends Sonic2AudioProfile {
        final List<Sonic2SoundRequestService> services = new ArrayList<>();
        final SmpsLoader loader;
        Runnable classify = () -> { }, prepare = () -> { }, resolve = () -> { }, fade = () -> { };
        boolean override;
        Profile(boolean hasLoader) { loader = hasLoader ? new AudioTestFixtures.StubSmpsLoader() : null; }
        @Override public SmpsLoader createSmpsLoader(Rom rom) { return loader; }
        @Override public AudioRequestService createAudioRequestService() {
            prepare.run();
            var service = new Sonic2SoundRequestService(); services.add(service); return service;
        }
        @Override public boolean isMusicOverride(int id) { classify.run(); return override; }
        @Override public Map<GameMusic, Integer> getMusicMap() { resolve.run(); return Map.of(GameMusic.SPECIAL_STAGE, 0x81); }
        @Override public void fadeOutMusic(AudioManager manager) { fade.run(); manager.fadeOutMusic(2, 3); }
        Sonic2SoundRequestService latest() { return services.getLast(); }
    }

    private static void blockedBy(AudioManager monitor, Runnable action) throws Exception {
        var entered = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var finished = new CountDownLatch(1);
        var worker = new Thread(() -> {
            entered.countDown();
            try { action.run(); } catch (Throwable thrown) { failure.set(thrown); }
            finally { finished.countDown(); }
        }, "level-music-monitor-contract");
        worker.setDaemon(true);
        try {
            synchronized (monitor) {
                worker.start();
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (worker.getState() != Thread.State.BLOCKED && finished.getCount() != 0 && System.nanoTime() < deadline)
                    Thread.onSpinWait();
                assertEquals(Thread.State.BLOCKED, worker.getState(), "operation must block on the supplied manager monitor");
                assertEquals(1, finished.getCount());
            }
        } finally { worker.join(3000); }
        assertFalse(worker.isAlive(), "finite worker cleanup");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void onWorker(Runnable action) throws Exception {
        var failure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> { try { action.run(); } catch (Throwable thrown) { failure.set(thrown); } },
                "level-music-off-thread-contract");
        worker.setDaemon(true);
        worker.start(); worker.join(3000);
        assertFalse(worker.isAlive(), "operation must finish without thread-affinity restrictions");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static boolean retains(Object object, AudioManager manager, int depth) throws Exception {
        // Observe strong token references without prescribing counter placement or a field name.
        for (Field field : object.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            field.setAccessible(true);
            Object value = field.get(object);
            if (value == manager) return true;
            if (depth > 0 && value != null && value.getClass().getName().startsWith(LevelMusicAccess.class.getName())
                    && retains(value, manager, depth - 1)) return true;
        }
        return false;
    }
}
