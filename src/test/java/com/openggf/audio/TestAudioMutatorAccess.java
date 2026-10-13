package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestAudioMutatorAccess {
    private AudioManager audio;
    @BeforeEach void setup() {
        audio = AudioManager.getInstance();
        audio.setAudioProfile(new Sonic3kAudioProfile());
        audio.setRom(TestEnvironment.currentRom());
    }
    @AfterEach void cleanup() { audio.resetState(); }

    @Test void internalLeasePreservesExternalObserverAndJournalsNativeRomRequests() {
        var nativeIntents = new ArrayList<AudioMutatorAccess.MusicIntent>();
        var diagnostics = new ArrayList<Integer>();
        audio.setRequestObserver((kind, id) -> diagnostics.add(id));
        try (var binding = AudioMutatorAccess.bind(audio, new AudioMutatorAccess.Listener() {
            public void onMusicIntent(AudioMutatorAccess.MusicIntent intent) { nativeIntents.add(intent); }
        })) {
            audio.playMusic(1);
            binding.playTemporaryMusic(3);
            binding.playLandingCue(0x49);
            audio.playMusic(0x31);
            assertEquals(List.of(1, 3, 0x49, 0x31), diagnostics);
            assertEquals(List.of(1, 0x31), nativeIntents.stream().map(AudioMutatorAccess.MusicIntent::value).toList());
            assertEquals(4, audio.commandTimeline().entryCount());
            assertEquals(AudioCommand.MusicRoute.BASE_SMPS,
                    ((AudioCommand.PlayMusic) audio.commandTimeline().entryAt(1).command()).route());
            assertEquals(AudioCommand.SfxRoute.BASE_SMPS_ID,
                    ((AudioCommand.PlaySfx) audio.commandTimeline().entryAt(2).command()).route());
        }
    }

    @Test void rejectsUnsupportedOrUnpreparedCuesBeforeAnySubmission() {
        try (var binding = AudioMutatorAccess.bind(audio, intent -> { })) {
            assertThrows(IllegalArgumentException.class, () -> binding.playTemporaryMusic(0x31));
            assertThrows(IllegalArgumentException.class, () -> binding.playTemporaryMusic(0xE0));
            assertThrows(IllegalArgumentException.class, () -> binding.playLandingCue(0xBC));
            assertThrows(IllegalArgumentException.class, () -> binding.playLandingCue(0xA9));
            assertEquals(0, audio.commandTimeline().entryCount());
            audio.setRom(null);
            assertThrows(IllegalStateException.class, () -> binding.playTemporaryMusic(3));
            assertThrows(IllegalStateException.class, () -> binding.playLandingCue(0x49));
            assertEquals(0, audio.commandTimeline().entryCount());
        }
    }

    @Test void ownerReplacementAndExactUnbindProtectAnotherWorld() {
        var firstEvents = new ArrayList<Integer>();
        var secondEvents = new ArrayList<Integer>();
        var first = AudioMutatorAccess.bind(audio, intent -> firstEvents.add(intent.value()));
        var second = AudioMutatorAccess.bind(audio, intent -> secondEvents.add(intent.value()));
        assertFalse(first.active());
        first.close();
        audio.playMusic(1);
        assertTrue(second.active());
        assertTrue(firstEvents.isEmpty());
        assertEquals(List.of(1), secondEvents);
        assertThrows(IllegalStateException.class, () -> first.playTemporaryMusic(3));
        second.close();
        assertFalse(second.active());
    }

    @Test void replayRetainsLogicalNativeIntentWithoutDoublePhysicalSubmissions() {
        var events = new ArrayList<Integer>();
        try (var binding = AudioMutatorAccess.bind(audio, intent -> events.add(intent.value()));
             var replay = audio.beginRewindReplay(3, 1, AudioReplayReason.SEEK)) {
            audio.playMusic(1);
            binding.playTemporaryMusic(3);
            binding.playLandingCue(0x49);
            audio.setSpeedMultiplier(8);
            assertEquals(List.of(1, 8), events);
            assertEquals(0, audio.commandTimeline().entryCount());
        }
    }

    @Test void noBindingLeavesNativeCommandAndOverridePathsUnchanged() {
        audio.playMusic(1);
        audio.playMusic(0x2A);
        audio.playMusic(0xE0);
        assertEquals(3, audio.commandTimeline().entryCount());
        assertFalse(((AudioCommand.PlayMusic) audio.commandTimeline().entryAt(0).command()).override());
        assertTrue(((AudioCommand.PlayMusic) audio.commandTimeline().entryAt(1).command()).override());
        assertInstanceOf(AudioCommand.FadeOutMusic.class, audio.commandTimeline().entryAt(2).command());
    }

    @Test void safeMusicPoolAndThumpPrepareFromTheRealNativeRom() {
        try (var binding = AudioMutatorAccess.bind(audio, intent -> { })) {
            for (int id = 1; id <= 23; id++) {
                int cue = id;
                assertDoesNotThrow(() -> binding.prepareMusic(cue));
            }
            assertDoesNotThrow(() -> binding.prepareLandingCue(0x49));
            assertEquals(0, audio.commandTimeline().entryCount(), "preparation must not manufacture playback");
        }
    }

    @Test void preparedNativeCuesProducePcmThroughTheAuthoritativeProducer() {
        try (var binding = AudioMutatorAccess.bind(audio, intent -> { });
             var capture = audio.beginLiveCaptureAudio(60)) {
            binding.playTemporaryMusic(3);
            binding.playLandingCue(0x49);
            short[] samples = new short[capture.maxStereoFramesPerPacket() * 2];
            boolean nonzero = false;
            for (int frame = 0; frame < 12; frame++) {
                audio.presentFrame(PresentationMode.FORWARD);
                int frames = capture.drainPresentationFrame(samples);
                assertTrue(frames > 0);
                for (int i = 0; i < frames * 2; i++) nonzero |= samples[i] != 0;
            }
            assertTrue(nonzero, "real ROM music/SFX must reach final producer PCM");
            var state = audio.captureLogicalSnapshot().presentation();
            assertEquals(3, state.activeMusic().musicId());
            assertNotNull(state.smpsLogical());
        }
    }
}
