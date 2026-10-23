package com.openggf.game.sonic2.audio;

import com.openggf.audio.AudioManager;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.data.RomManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/** Exercises the retail mailbox ingress through the real ROM loader and presentation producer. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2NativeSoundTestRequests {
    private final AudioManager audio = AudioManager.getInstance();
    private final List<Sonic2SoundRequestService.Event> events = new ArrayList<>();
    private LiveCaptureAudioHandle capture;

    @BeforeEach
    void setUp() throws Exception {
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setRom(RomManager.getInstance().getRom());
        Sonic2AudioProfile profile = new Sonic2AudioProfile(events::add);
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
    }

    @AfterEach
    void tearDown() {
        if (capture != null) {
            capture.close();
        }
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
    }

    @Test
    void nativeOptionsRequestPreservesRawMailboxIdentityAndPlaysOptions() {
        assertMusicRequest(0x91, Sonic2Music.OPTIONS.id, true);
    }

    @Test
    void native89RequestPlaysCasinoNightRatherThanEngineOptions89() {
        assertMusicRequest(0x89, Sonic2Music.CASINO_NIGHT.id, true);
    }

    @Test
    void ordinaryEngineOptions89RequestKeepsItsExistingMusicIdentity() {
        assertMusicRequest(Sonic2Music.OPTIONS.id, Sonic2Music.OPTIONS.id, false);
    }

    @Test
    void nativeSfxAndStopCommandKeepTheirDriverClassification() {
        audio.playMusicMailboxNativeRequest(0xA0);
        assertEquals(0, audio.commandTimeline().entryCount(),
                "Native requests must wait for the production request service");
        audio.presentFrame(PresentationMode.FORWARD);
        AudioCommand.PlaySfx jump = assertInstanceOf(AudioCommand.PlaySfx.class,
                audio.commandTimeline().entries().getFirst().command());
        assertEquals(0xA0, jump.sfxId());
        assertMailboxDispatch(0xA0);

        events.clear();
        audio.playMusicMailboxNativeRequest(0xF8);
        audio.presentFrame(PresentationMode.FORWARD);
        assertInstanceOf(AudioCommand.StopAllSfx.class,
                audio.commandTimeline().entries().getLast().command());
        assertMailboxDispatch(0xF8);
    }

    private void assertMusicRequest(int request, int expectedEngineId, boolean nativeIngress) {
        capture = audio.beginLiveCaptureAudio(audio.presentationFrameRate());
        if (nativeIngress) {
            audio.playMusicMailboxNativeRequest(request);
        } else {
            audio.playMusic(request);
        }
        assertEquals(0, audio.commandTimeline().entryCount(),
                "Ingress must write the source mailbox without immediate playback");
        audio.presentFrame(PresentationMode.FORWARD);
        assertMailboxDispatch(request);
        assertEquals(1, audio.commandTimeline().entryCount());
        AudioCommand.PlayMusic played = assertInstanceOf(AudioCommand.PlayMusic.class,
                audio.commandTimeline().entries().getFirst().command());
        assertEquals(expectedEngineId, played.musicId(),
                "The presentation command must carry the engine id of the retail track");
        assertEquals(AudioCommand.MusicRoute.BASE_SMPS, played.route(),
                "The real ROM loader must resolve the translated music");

        short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
        for (int frame = 0; frame < 120; frame++) {
            int stereoFrames = capture.drainPresentationFrame(packet);
            var active = audio.captureLogicalSnapshot().presentation().activeMusic();
            if (active != null && active.musicId() == expectedEngineId
                    && containsNonZeroSample(packet, stereoFrames * 2)) {
                return;
            }
            if (frame + 1 < 120) {
                audio.presentFrame(PresentationMode.FORWARD);
            }
        }
        fail("Requested ROM track $" + Integer.toHexString(expectedEngineId)
                + " must become active and produce non-zero final PCM within 120 frames");
    }

    private void assertMailboxDispatch(int expectedRawRequest) {
        Sonic2SoundRequestService.Submission submitted = events.stream()
                .filter(Sonic2SoundRequestService.Submission.class::isInstance)
                .map(Sonic2SoundRequestService.Submission.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Sonic2SoundRequestPipeline.SourceSlot.MUSIC0, submitted.sourceMailbox());
        assertEquals(expectedRawRequest, submitted.rawRequestId(),
                "Track translation must preserve the retail request byte");
        Sonic2SoundRequestService.Dispatch dispatch = events.stream()
                .filter(Sonic2SoundRequestService.Dispatch.class::isInstance)
                .map(Sonic2SoundRequestService.Dispatch.class::cast)
                .findFirst().orElseThrow();
        assertEquals(expectedRawRequest, dispatch.originalRequestId());
    }

    private static boolean containsNonZeroSample(short[] packet, int sampleCount) {
        for (int index = 0; index < sampleCount; index++) {
            if (packet[index] != 0) {
                return true;
            }
        }
        return false;
    }
}
