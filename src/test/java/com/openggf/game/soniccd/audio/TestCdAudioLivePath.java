package com.openggf.game.soniccd.audio;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerInternalAccess;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.media.CdDiscHeader;
import com.openggf.game.soniccd.release.CdRetailReleases;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void installUsa() throws IOException {
        install(CdAudioTestMedia.usaCue());
    }

    private void install(Path image) throws IOException {
        TestEnvironment.resetAll();
        var openedDisc = CdDiscDataSource.open(image);
        disc = openedDisc;
        profile = new SonicCdAudioProfile(() -> openedDisc);
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        AudioManagerInternalAccess.installMatchedSource(audio, null, profile);
        audio.setSoundMap(profile.getSoundMap());
        capture = audio.beginLiveCaptureAudio(60);
    }

    @AfterEach
    void close() throws IOException {
        if (capture != null) {
            capture.close();
            capture = null;
        }
        if (audio != null) {
            audio.resetState();
            audio = null;
        }
        if (disc != null) {
            disc.close();
            disc = null;
        }
    }

    @Test
    void anFmEffectRequestedByIdIsSynthesisedThroughTheSharedProducer() {
        assertTrue(audio.playSfx(0x92));
        assertTrue(audible(present(30)), "FM_JUMP produced sound");
    }

    @Test
    void dustReplacesTheJumpFm1TrackWithoutChangingItsKey() throws IOException {
        // BizHawk 2.11 USA Sound Test, u63-dust-fm1-KeIh0GZH/fm1-keys-and-banked-reads.csv.
        // Compare writes between requests to allow for the video-frame/Timer B phase.
        for (boolean requestDust : List.of(false, true)) {
            if (requestDust) {
                close();
                install(CdAudioTestMedia.usaCue());
            }
            List<Integer> fm1Keys = new ArrayList<>();
            List<Integer> fm1SsgEg = new ArrayList<>();
            audio.setChipWriteObserver(new ChipWriteObserver() {
                @Override
                public void onYm2612Write(int port, int register, int value) {
                    if (port == 0 && register == 0x28 && (value & 7) == 0) {
                        fm1Keys.add(value);
                    }
                    if (port == 0 && register >= 0x90 && register <= 0x9C && register % 4 == 0) {
                        fm1SsgEg.add(register);
                    }
                }

                @Override
                public void onPsgWrite(int value) { }
            });
            audio.playSfx(0x96); // Frame 2400 in the native recording.
            present(10);
            audio.playSfx(0x92);
            present(15);
            List<Integer> beforeDust = List.copyOf(fm1Keys);
            fm1Keys.clear();
            fm1SsgEg.clear();

            if (requestDust) audio.playSfx(0xD6); // Frame 2425.
            present(360);
            List<Integer> beforeReplacement = List.copyOf(fm1Keys);
            List<Integer> dustSsgEg = List.copyOf(fm1SsgEg);
            fm1Keys.clear();

            audio.playSfx(0x92); // Frame 2785.
            present(36);
            assertEquals(
                    List.of(List.of(0, 0, 0xF0, 0, 0xF0),
                            requestDust ? List.of() : List.of(0), // Control key-off at frame 2435.
                            List.of(0, 0, 0xF0, 0, 0xF0, 0),
                            List.of()),
                    List.of(beforeDust, beforeReplacement, fm1Keys, dustSsgEg),
                    "FM1 key writes before dust, before replacement and after replacement, then SSG-EG writes "
                            + "during the dust window. Dust requested=" + requestDust);
        }
    }

    @Test
    void dustHoldsFm1UntilAReplacementAfter7200Frames() {
        List<String> fm1Writes = new ArrayList<>();
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                boolean fm1Key = register == 0x28 && (value & 7) == 0;
                boolean fm1Operator = register >= 0x30 && register <= 0x9C && register % 4 == 0;
                boolean fm1Channel = register == 0xA0 || register == 0xA4
                        || register == 0xB0 || register == 0xB4;
                if (port == 0 && (fm1Key || fm1Operator || fm1Channel)) {
                    fm1Writes.add("$%02X=$%02X".formatted(register, value));
                }
            }

            @Override
            public void onPsgWrite(int value) { }
        });

        assertTrue(audio.playSfx(0x92));
        present(15);
        assertTrue(fm1Writes.contains("$28=$F0"), "Jump keys FM1 on before dust");
        fm1Writes.clear();

        assertTrue(audio.playSfx(0xD6));
        short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
        for (int frame = 1; frame <= 7200; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
            capture.drainPresentationFrame(packet);
            assertEquals(List.of(), fm1Writes, "FM1 writes at dust-relative frame " + frame);
        }

        assertTrue(audio.playSfx(0x92));
        present(36);
        assertTrue(fm1Writes.contains("$28=$F0"), "The replacement jump keys FM1 on");
    }

    @ParameterizedTest
    @ValueSource(ints = {0x96, 0xA0, 0xA2, 0xA7})
    void badnikEffectsProduceAudibleSamples(int id) {
        assertTrue(audio.playSfx(id));
        assertTrue(audible(present(60)), "FM $%02X produced sound".formatted(id));
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

    @Test
    void firstRingAfterStopSoundPansRight() {
        audio.playSfx(com.openggf.audio.GameSound.RING);
        assertTrue(louder(present(20), 1, 0), "the first ring is panned right");

        // FMC_STOP (FM/_Sound IDs.inc:93-94) reaches StopSound through the driver queue.
        assertTrue(audio.playSfx(0xE0));
        present(1);
        audio.playSfx(com.openggf.audio.GameSound.RING);
        assertTrue(louder(present(20), 1, 0), "the first ring after StopSound is panned right");
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
        playPastMusic();
        var before = profile.subCpuAudio().state();
        assertPastMusicFades();
        profile.subCpuAudio().rewindAdapter().restore(before);
        assertTrue(audible(present(30)), "rewind restores the playing driver, chip and wave RAM");
    }

    @ParameterizedTest
    @EnumSource(CdReleaseVariant.class)
    void authenticatedRegionalDiscPlaysTheSamePastPcmAsUsa(CdReleaseVariant variant) throws IOException {
        Path regionalImage = CdTestMedia.disc(variant);
        assertAuthenticatedDisc(CdReleaseVariant.USA);
        int sampleRate = capture.sampleRate();
        short[] usa = playPastMusic();
        close();

        // A fresh disc, profile and capture lease also make USA a repeatability control.
        install(regionalImage);
        assertAuthenticatedDisc(variant);
        assertEquals(sampleRate, capture.sampleRate(), "compare at the same output sample rate");
        short[] regional = playPastMusic();
        assertArrayEquals(usa, regional, variant + " Past PCM samples");
        assertPastMusicFades();
    }

    private void assertAuthenticatedDisc(CdReleaseVariant expected) {
        var programs = disc.assets();
        var release = CdRetailReleases.find(programs.identity())
                .orElseThrow(() -> new AssertionError("Expected authenticated retail disc for " + expected));
        assertEquals(expected, release.variant());
        assertEquals(CdDiscHeader.Match.MATCH, programs.header().match(expected));
    }

    private short[] playPastMusic() {
        var subCpu = profile.subCpuAudio();
        subCpu.loadStage(CdStagePayload.R11B);
        audio.playMusic(CdSubCpuAudio.commandId("SCMD_PASTMUS"));
        short[] music = present(90);
        var state = subCpu.state();
        assertNotNull(state.pcm(), "Past stage loads the sub-CPU PCM driver");
        assertEquals(CdNativeFile.R1PCM.isoName(), state.pcm().bank());
        assertEquals(CdCddaPlayer.State.IDLE, state.cdda(), "Past music leaves CDDA idle");
        assertTrue(audible(Arrays.copyOfRange(music, music.length / 2, music.length)), "Past music is mixed in");
        return music;
    }

    private void assertPastMusicFades() {
        audio.playMusic(CdSubCpuAudio.commandId("SCMD_FADEPCM"));
        present(200);
        assertFalse(audible(present(10)), "Cmd_FadeOut reaches silence");
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

    @Test
    void resultsTallyProducesAudibleFm() {
        audio.playSfx(0xBD);
        assertTrue(audible(present(30)), "FM_TALLY produced sound");
    }

    @Test
    void resultsChimeProducesAudibleFm() {
        AudioManagerInternalAccess.playSecondaryNativeRequest(audio, 0x9A);
        assertTrue(audible(present(30)), "FM_KACHING produced sound");
    }

    @Test
    void specialStageWarpProducesAudibleFm() {
        audio.playSfx(0xC8);
        assertTrue(audible(present(30)), "FM_SSWARP produced sound");
    }

    @Test
    void workbenchBounceProducesAudibleFm() {
        audio.playSfx(0xDA);
        assertTrue(audible(present(30)), "FM_DA produced sound");
    }

    @Test
    void ringExtraLifeProducesAudiblePcm() {
        profile.subCpuAudio().loadStage(com.openggf.game.soniccd.CdStagePayload.R11A);
        AudioManagerInternalAccess.playRingExtraLife(audio);
        assertTrue(audible(present(30)), "SCMD_YESSFX produced sound");
    }

    private short[] present(int frames) {
        short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
        short[] all = new short[packet.length * frames];
        int offset = 0;
        for (int i = 0; i < frames; i++) {
            audio.presentFrame(PresentationMode.FORWARD);
            int samples = capture.drainPresentationFrame(packet) * 2;
            System.arraycopy(packet, 0, all, offset, samples);
            offset += samples;
        }
        return Arrays.copyOf(all, offset);
    }

    private static boolean audible(short[] pcm) {
        for (short s : pcm) if (Math.abs(s) > 64) return true;
        return false;
    }
}
