package com.openggf.game.soniccd.audio;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerInternalAccess;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sonic CD profile on the production presentation path: the shared producer, its
 * no-device sink and its capture lease, with no ROM installed.
 */
class TestCdAudioLivePath {
    private AudioManager audio;
    private CdDiscDataSource disc;
    private SonicCdAudioProfile profile;
    private LiveCaptureAudioHandle capture;

    @BeforeEach
    void install() throws Exception {
        TestEnvironment.resetAll();
        disc = CdDiscDataSource.open(CdAudioTestMedia.usaCue());
        profile = new SonicCdAudioProfile(() -> disc);
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        AudioManagerInternalAccess.installMatchedSource(audio, null, profile);
        audio.setSoundMap(profile.getSoundMap());
        capture = audio.beginLiveCaptureAudio(60);
    }

    @AfterEach
    void close() throws Exception {
        if (capture != null) capture.close();
        if (audio != null) audio.resetState();
        if (disc != null) disc.close();
    }

    @Test
    void anFmEffectRequestedByIdIsSynthesisedThroughTheSharedProducer() {
        assertTrue(audio.playSfx(0x92));
        assertTrue(audible(present(30)), "FM_JUMP produced sound");
    }

    @Test
    void theGameSoundMapReachesTheDriverIds() {
        audio.playSfx(com.openggf.audio.GameSound.SPRING);
        assertTrue(audible(present(20)), "FM_SPRING produced sound");
    }

    /**
     * Two rings on the production path: the first plays on the right, the second's
     * TrkCmd_SwapRingChn continues as FM_RINGL, the left-hand ring (SFX/A8.asm).
     */
    @Test
    void consecutiveRingsAlternateSpeakersThroughTheDriverSwap() {
        audio.playSfx(com.openggf.audio.GameSound.RING);
        short[] right = present(20);
        audio.playSfx(com.openggf.audio.GameSound.RING);
        short[] left = present(20);
        assertTrue(louder(right, 1, 0), "the first ring is panned right");
        assertTrue(louder(left, 0, 1), "the second ring is panned left");
    }

    private static boolean louder(short[] pcm, int channel, int other) {
        long a = 0, b = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            a += Math.abs(pcm[i + channel]);
            b += Math.abs(pcm[i + other]);
        }
        return a > 4 * Math.max(1, b);
    }

    /** A Past stage's bank and SCMD_PASTMUS: the PCM driver's music through the producer. */
    @Test
    void pastMusicPlaysThroughThePcmDriverAndFades() {
        profile.subCpuAudio().loadStage(com.openggf.game.soniccd.CdStagePayload.R11B);
        audio.playMusic(CdSubCpuAudio.commandId("SCMD_PASTMUS"));
        short[] music = present(90);
        assertTrue(audible(Arrays.copyOfRange(music, music.length / 2, music.length)), "Past music is mixed in");
        var before = profile.subCpuAudio().state();
        audio.playMusic(CdSubCpuAudio.commandId("SCMD_FADEPCM"));
        present(200);
        assertTrue(!audible(present(10)), "Cmd_FadeOut reaches silence");
        profile.subCpuAudio().rewindAdapter().restore(before);
        assertTrue(audible(present(30)), "rewind restores the playing driver, chip and wave RAM");
    }

    @Test
    void stageMusicCommandMixesTheDiscTrackAndFadeSilencesIt() {
        audio.playMusic(0x0F); // SCMD_R1AMUS
        short[] music = present(120);
        assertTrue(audible(Arrays.copyOfRange(music, music.length / 2, music.length)), "track 3 is mixed in");
        assertEquals(3, profile.subCpuAudio().state().cdda().track());
        audio.playMusic(0x0E); // SCMD_FADECDA
        present(25);
        short[] after = present(10);
        assertTrue(!audible(after), "19 frames of fade reach silence");
    }

    @Test
    void restoringTheRewindSnapshotReplaysTheSameCdAudio() {
        audio.playMusic(0x0F);
        present(30);
        var adapter = profile.subCpuAudio().rewindAdapter();
        CdSubCpuAudio.State saved = adapter.capture();
        short[] first = present(10);
        adapter.restore(saved);
        short[] replay = present(10);
        assertArrayEquals(first, replay);
    }

    @Test
    void sameDiscProfileReinstallKeepsTheProducer() {
        audio.playMusic(0x0F);
        present(5);
        AudioManagerInternalAccess.installMatchedSource(audio, null, profile);
        present(5);
        assertEquals(3, profile.subCpuAudio().state().cdda().track(), "level re-entry keeps the source");
    }

    private short[] present(int frames) {
        short[] all = new short[0];
        short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
        for (int i = 0; i < frames; i++) {
            audio.presentFrame(PresentationMode.FORWARD);
            int n = capture.drainPresentationFrame(packet);
            all = Arrays.copyOf(all, all.length + n * 2);
            System.arraycopy(packet, 0, all, all.length - n * 2, n * 2);
        }
        return all;
    }

    private static boolean audible(short[] pcm) {
        for (short s : pcm) if (Math.abs(s) > 64) return true;
        return false;
    }
}
