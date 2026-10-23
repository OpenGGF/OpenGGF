package com.openggf.audio;

import com.openggf.audio.presentation.AudioRequestService;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.data.RomManager;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestPipeline;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native operand/mailbox ingress only; no player, bank or ring-award composition is modelled here. */
@RequiresRom(SonicGame.SONIC_2)
class TestAudioSecondaryNativeRequest {
    private AudioManager audio;
    private final List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
    private final List<String> observations = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        audio = AudioManager.getInstance();
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setRom(RomManager.getInstance().getRom());
        installProfile(new Sonic2AudioProfile(events::add));
        audio.setRequestObserver((kind, id) -> observations.add("request:" + kind + ":" + id));
    }

    @AfterEach
    void tearDown() {
        audio.setRequestObserver(null);
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
    }

    @Test
    void laterRingOperandOverwritesLifeOperandInTheSameSecondaryMailboxPass() {
        assertSamePassOverwrite(0x98, 0xB5);
        assertEquals(0xCE, assertInstanceOf(AudioCommand.PlaySfx.class, lastCommand()).sfxId());
        assertFalse(audio.captureLogicalSnapshot().ringLeft());
        assertEquals(Sonic2SoundRequestService.DecisionReason.ACCEPTED_PRIORITY,
                observed(Sonic2SoundRequestService.Decision.class).getFirst().reason());
    }

    @Test
    void laterLifeOperandOverwritesRingAndDispatchesMusicRatherThanSfx98() {
        assertSamePassOverwrite(0xB5, 0x98);
        var music = assertInstanceOf(AudioCommand.PlayMusic.class, lastCommand());
        assertEquals(Sonic2Music.EXTRA_LIFE.id, music.musicId());
        assertTrue(music.override());
        assertTrue(audio.captureLogicalSnapshot().ringLeft(), "the overwritten ring must not toggle a speaker");
        assertEquals(Sonic2SoundRequestService.DecisionReason.PROMOTED_MUSIC,
                observed(Sonic2SoundRequestService.Decision.class).getFirst().reason());
    }

    private void assertSamePassOverwrite(int first, int last) {
        try (var binding = observeIntents(audio)) {
            request(first);
            request(last);
        }
        assertEquals(List.of("intent:SFX:" + first, "request:SFX:" + first,
                "intent:SFX:" + last, "request:SFX:" + last), observations);
        assertEquals(0, audio.commandTimeline().entryCount());
        assertTrue(events.isEmpty(), "source writes must not publish committed driver diagnostics");
        var pending = requestSnapshot();
        assertEquals(0, pending.pipeline().sfx0().requestByte());
        assertEquals(0, pending.pipeline().music0().requestByte());
        assertEquals(0, pending.pipeline().music1().requestByte());
        assertEquals(last, pending.pipeline().sfx1().requestByte());
        assertEquals(2, pending.pendingSubmissionEvents().size());

        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(1, audio.commandTimeline().entryCount());
        var submissions = observed(Sonic2SoundRequestService.Submission.class);
        assertEquals(List.of(first, last), submissions.stream()
                .map(Sonic2SoundRequestService.Submission::rawRequestId).toList());
        assertTrue(submissions.stream().allMatch(event ->
                event.sourceMailbox() == Sonic2SoundRequestPipeline.SourceSlot.SFX1));
        assertSecondaryTransfer(last);
        assertEquals(last, observed(Sonic2SoundRequestService.Dispatch.class).getFirst().originalRequestId());
        assertEquals(4, observations.size(), "driver service must not repeat caller observations");
    }

    @ParameterizedTest
    @ValueSource(ints = {0x98, 0xB5})
    void pendingSecondarySnapshotRestoresAndReplaysExactlyOnce(int nativeId) {
        try (var binding = observeIntents(audio)) {
            request(nativeId);
        }
        var pending = audio.captureLogicalSnapshot();
        var service = requestSnapshot();
        audio.presentFrame(PresentationMode.FORWARD);
        AudioCommand first = lastCommand();
        boolean ringAfter = audio.captureLogicalSnapshot().ringLeft();

        audio.restoreLogicalSnapshot(pending);
        assertEquals(service, requestSnapshot());
        events.clear();
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(2, audio.commandTimeline().entryCount(), "historical timeline entries remain retained");
        assertEquals(first, lastCommand());
        assertEquals(ringAfter, audio.captureLogicalSnapshot().ringLeft());
        assertSecondaryTransfer(nativeId);
        assertEquals(List.of("intent:SFX:" + nativeId, "request:SFX:" + nativeId), observations);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(2, audio.commandTimeline().entryCount(), "the restored request must not dispatch twice");
        assertEquals(1, observed(Sonic2SoundRequestService.Dispatch.class).size());
    }

    @Test
    void mutatorSeesRawIntentBeforeReplaySuppressionWithoutRequestQueueOrRingMutation() {
        var before = audio.captureLogicalSnapshot();
        long frames = audio.shadowParitySnapshot().presentedFrames();
        try (var binding = observeIntents(audio);
             var replay = audio.beginRewindReplay(3, 1, AudioReplayReason.SEEK)) {
            request(0x98);
            request(0xB5);
            request(0xFA);
        }
        assertEquals(List.of("intent:SFX:152", "intent:SFX:181", "intent:COMMAND:250"), observations);
        var after = audio.captureLogicalSnapshot();
        assertEquals(before.forwardServiceSnapshot(), after.forwardServiceSnapshot());
        assertEquals(before.ringLeft(), after.ringLeft());
        assertEquals(0, after.commandEntryCount());
        assertEquals(frames, audio.shadowParitySnapshot().presentedFrames());
        assertTrue(events.isEmpty());
        request(0xB5);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(0xCE, assertInstanceOf(AudioCommand.PlaySfx.class, lastCommand()).sfxId());
    }

    @ParameterizedTest
    @CsvSource({"0x98, 0x98", "0x91, 0x89"})
    void noServiceFallbackUsesTheClassifiedMusicIncludingNativeAliases(int nativeId, int musicId) {
        var profile = installNoServiceProfile();
        try (var binding = observeIntents(audio)) {
            request(nativeId);
        }
        assertEquals(1, profile.classifications, "the native classifier must run once");
        assertEquals(List.of("intent:SFX:" + nativeId, "request:SFX:" + nativeId), observations);
        assertNull(audio.captureLogicalSnapshot().forwardServiceSnapshot());
        assertEquals(1, audio.commandTimeline().entryCount());
        var music = assertInstanceOf(AudioCommand.PlayMusic.class, lastCommand());
        assertEquals(musicId, music.musicId());
        assertEquals(AudioCommand.MusicRoute.BASE_SMPS, music.route());
        assertEquals(musicId == Sonic2Music.EXTRA_LIFE.id, music.override());
        assertEquals(List.of(musicId), profile.systemRequests,
                "music admission must receive the actual classified command's id");
        assertEquals(0, audio.shadowParitySnapshot().presentedFrames());
    }

    @Test
    void noServiceRingFallbackObservesOnceAndSharesExactlyOneSpeakerToggle() {
        var profile = installNoServiceProfile();
        try (var binding = observeIntents(audio)) {
            request(0xB5);
            assertFalse(audio.captureLogicalSnapshot().ringLeft());
            request(0xB5);
            assertTrue(audio.captureLogicalSnapshot().ringLeft());
            request(0xCE);
            assertTrue(audio.captureLogicalSnapshot().ringLeft(), "explicit left id must not toggle");
        }
        assertEquals(List.of(0xCE, 0xB5, 0xCE), audio.commandTimeline().entries().stream()
                .map(entry -> assertInstanceOf(AudioCommand.PlaySfx.class, entry.command()).sfxId()).toList());
        assertEquals(List.of("intent:SFX:181", "request:SFX:181", "intent:SFX:181", "request:SFX:181",
                "intent:SFX:206", "request:SFX:206"), observations);
        assertEquals(3, profile.classifications);
        assertEquals(0, audio.shadowParitySnapshot().presentedFrames());
    }

    @Test
    void noServiceRawSfxFallbackKeepsItsNativeIdAndOneObservation() {
        var profile = installNoServiceProfile();
        try (var binding = observeIntents(audio)) {
            request(0xA0);
        }
        assertEquals(1, profile.classifications);
        assertEquals(List.of("intent:SFX:160", "request:SFX:160"), observations);
        assertEquals(1, audio.commandTimeline().entryCount());
        assertEquals(0xA0, assertInstanceOf(AudioCommand.PlaySfx.class, lastCommand()).sfxId());
    }

    @Test
    void actualS2SystemFallbackDoesNotRepeatItsNestedMutatorOrRawObservation() {
        var profile = installNoServiceProfile();
        try (var binding = observeIntents(audio)) {
            request(0xFA);
        }
        assertEquals(List.of("intent:COMMAND:250", "request:COMMAND:250"), observations);
        assertEquals(List.of(0xFA), profile.systemRequests);
        assertEquals(1, profile.classifications);
        assertEquals(1, audio.commandTimeline().entryCount());
        assertInstanceOf(AudioCommand.PlaySegaPcm.class, lastCommand());
        assertEquals(0, audio.mutatorSubmissionDepth);
        assertTrue(audio.captureLogicalSnapshot().ringLeft());
        assertEquals(0, audio.shadowParitySnapshot().presentedFrames());
    }

    @Test
    void failingSystemFallbackRestoresDepthAndPreservesTheOriginalThrownIdentity() {
        var profile = installNoServiceProfile();
        var failure = new IllegalStateException("profile system failure");
        profile.systemFailure = failure;
        try (var binding = observeIntents(audio)) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> request(0xFA)));
            assertEquals(0, audio.mutatorSubmissionDepth);
            assertEquals(List.of("intent:COMMAND:250"), observations,
                    "failed profile dispatch precedes raw observation, as in the existing fallback");
            assertEquals(0, audio.commandTimeline().entryCount());
            profile.systemFailure = null;
            request(0xFA);
            audio.setSpeedShoes(true);
        }
        assertEquals(List.of("intent:COMMAND:250", "intent:COMMAND:250", "request:COMMAND:250",
                "intent:SPEED_SHOES:1"), observations);
        assertEquals(2, audio.commandTimeline().entryCount());
    }

    @Test
    void bridgeRequiresAndUsesTheInjectedManager() {
        assertEquals("manager", assertThrows(NullPointerException.class,
                () -> AudioManagerInternalAccess.playSecondaryNativeRequest(null, 0x98)).getMessage());
        AudioManager injected = AudioManager.createSilent();
        injected.setRequestObserver((kind, id) -> fail("silent injected owner must suppress raw requests"));
        try (var binding = observeIntents(injected)) {
            AudioManagerInternalAccess.playSecondaryNativeRequest(injected, 0x98);
        } finally {
            injected.destroy();
        }
        assertEquals(List.of("intent:SFX:152"), observations);
        assertEquals(0, audio.commandTimeline().entryCount());
        assertEquals(0, requestSnapshot().pipeline().sfx1().requestByte());
    }

    private void request(int nativeId) {
        AudioManagerInternalAccess.playSecondaryNativeRequest(audio, nativeId);
    }

    private void installProfile(Sonic2AudioProfile profile) {
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
    }

    private NoServiceSonic2Profile installNoServiceProfile() {
        var profile = new NoServiceSonic2Profile();
        installProfile(profile);
        return profile;
    }

    private AudioMutatorAccess.Binding observeIntents(AudioManager owner) {
        return AudioMutatorAccess.bind(owner, new AudioMutatorAccess.Listener() {
            @Override public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) {
                observations.add("intent:" + intent.kind() + ":" + intent.value());
            }
            @Override public void onNativeSfx(int id) {
                observations.add("intent:SFX:" + id);
            }
        });
    }

    private Sonic2SoundRequestService.Snapshot requestSnapshot() {
        return assertInstanceOf(Sonic2SoundRequestService.Snapshot.class,
                audio.captureLogicalSnapshot().forwardServiceSnapshot());
    }

    private AudioCommand lastCommand() {
        return audio.commandTimeline().entries().getLast().command();
    }

    private <T extends Sonic2SoundRequestService.Event> List<T> observed(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    private void assertSecondaryTransfer(int nativeId) {
        var transfers = observed(Sonic2SoundRequestService.Transfer.class);
        assertEquals(1, transfers.size());
        assertEquals(Sonic2SoundRequestPipeline.SourceSlot.SFX1, transfers.getFirst().sourceMailbox());
        assertEquals(1, transfers.getFirst().physicalSlot());
        assertEquals(nativeId, transfers.getFirst().rawRequestId());
        assertFalse(transfers.getFirst().slot3Alias());
    }

    /** Only removes the request service; content, aliases and system dispatch remain production S2. */
    private static final class NoServiceSonic2Profile extends Sonic2AudioProfile {
        private int classifications;
        private final List<Integer> systemRequests = new ArrayList<>();
        private RuntimeException systemFailure;

        @Override public AudioRequestService createAudioRequestService() { return null; }

        @Override public int musicForNativeRequest(int nativeId) {
            classifications++;
            return super.musicForNativeRequest(nativeId);
        }

        @Override public boolean handleSystemCommand(int nativeId, AudioManager manager) {
            systemRequests.add(nativeId);
            if (systemFailure != null) throw systemFailure;
            return super.handleSystemCommand(nativeId, manager);
        }
    }
}
