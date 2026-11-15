package com.openggf.game.soniccd.audio;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerInternalAccess;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.AudioPresentationMixer;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player's music and sound-effect volumes on Sonic CD's own sample sources, through the
 * production path: config, {@code AudioManager}'s mix push, the shared producer and the capture
 * lease. No native reference has a player volume, so these are engine properties: a source at
 * gain 0 leaves exactly the render with that source disconnected, the music-only and effects-only
 * renders sum to the full render, and at unity the gained paths are the ungained ones exactly.
 */
class TestCdAudioMixGains {
    private static final int FM_JUMP = 0x92;
    private static final int FUTURE_VOICE = CdSubCpuAudio.commandId("SCMD_FUTURESFX");
    private static final int PRESENT_MUSIC = CdSubCpuAudio.commandId("SCMD_R1AMUS");
    private static final int PAST_MUSIC = CdSubCpuAudio.commandId("SCMD_PASTMUS");
    /** Frames before the effects are requested, then frames after. */
    private static final int LEAD = 60, TAIL = 90;

    private CdDiscDataSource disc;
    private AudioManager audio;
    private SonicCdAudioProfile profile;
    private LiveCaptureAudioHandle capture;

    @BeforeEach
    void open() throws Exception {
        TestEnvironment.resetAll();
        disc = CdDiscDataSource.open(CdAudioTestMedia.usaCue());
        audio = AudioManager.getInstance();
    }

    @AfterEach
    void close() throws Exception {
        if (capture != null) capture.close();
        GameServices.configuration().clearSessionOverrides();
        if (audio != null) audio.resetState();
        if (disc != null) disc.close();
    }

    /** Present stage: CD-DA music, an FM effect and the PCM chip's "Future!" voice. */
    @Test
    void presentStageSplitsDiscMusicFromFmAndPcmEffects() {
        short[] full = render(Stage.PRESENT, 100, 100, true, true, false);
        short[] musicOnly = render(Stage.PRESENT, 100, 0, true, true, false);
        short[] effectsOnly = render(Stage.PRESENT, 0, 100, true, true, false);
        assertTrue(energy(musicOnly) > 0 && energy(effectsOnly) > 0, "both halves are audible");

        assertArrayEquals(render(Stage.PRESENT, 100, 100, false, true, false), effectsOnly,
                "music at 0 is exactly the render with the disc track never started");
        assertArrayEquals(render(Stage.PRESENT, 100, 100, true, false, false), musicOnly,
                "effects at 0 is exactly the render with no effect requested");
        assertSums(full, musicOnly, effectsOnly);
    }

    /**
     * Past stage: the PCM driver's song and a PCM effect on the same chip, plus an FM effect.
     * Every PCM effect header names chip channel 6 ({@code cPCM7}, _smps2asm_inc.inc:185;
     * SFX/Future.asm:5), and the Past songs leave it free: Stardust Speedway Past has nine tracks
     * ({@code smpsHeaderChan $09}, Music/Stardust Speedway Past.asm:3), the ninth on channel 6
     * ({@code ChannelIDs}, PCM/_Driver.asm:674-683), and that track is a bare {@code smpsStop}
     * (Music/Stardust Speedway Past.asm:580-581). So the effect never shares a song channel here
     * (asserted); the shared case, which the driver allows, is the device partition test's.
     */
    @Test
    void pastStageSplitsTheChipBetweenSongAndEffectChannels() {
        short[] full = render(Stage.PAST, 100, 100, true, true, false);
        short[] musicOnly = render(Stage.PAST, 100, 0, true, true, false);
        short[] effectsOnly = render(Stage.PAST, 0, 100, true, true, false);
        int lead = leadSamples;

        assertTrue(energy(Arrays.copyOf(full, lead)) > 0, "the Past song plays before the effects");
        assertArrayEquals(new short[lead], Arrays.copyOf(effectsOnly, lead),
                "music at 0 silences the song's channels exactly");
        assertArrayEquals(Arrays.copyOf(full, lead), Arrays.copyOf(musicOnly, lead),
                "effects at 0 leaves the song untouched before any effect");
        assertTrue(energy(Arrays.copyOfRange(effectsOnly, lead, effectsOnly.length)) > 0,
                "the PCM and FM effects play over silenced music");
        assertTrue(effectChannelSeen, "the PCM effect played");
        assertTrue(!sharedChannelSeen, "the song leaves the effect channel free");

        // With no song every sounding channel is an effect's: the music gain changes nothing.
        assertArrayEquals(render(Stage.PAST, 100, 100, false, true, false),
                render(Stage.PAST, 0, 100, false, true, false),
                "music at 0 is exactly the render with the song never started");
        assertSums(full, musicOnly, effectsOnly);
    }

    /** At unity the gained arithmetic is the ungained render exactly, for disc audio and the chip. */
    @Test
    void gainedPathsReduceToTheUngainedRenderAtUnity() {
        for (Stage stage : Stage.values()) {
            assertArrayEquals(render(stage, 100, 100, true, true, false),
                    render(stage, 100, 100, true, true, true), stage + " at unity");
        }
    }

    /** A video program's streamed PCM song is music. */
    @Test
    void programPcmFollowsTheMusicGain() {
        short[] unity = renderProgram(AudioPresentationMixer.UNITY_GAIN, false);
        assertTrue(energy(unity) > 0);
        assertArrayEquals(unity, renderProgram(AudioPresentationMixer.UNITY_GAIN, true), "unity path");
        assertArrayEquals(new short[unity.length], renderProgram(0, false), "music at 0");
    }

    private enum Stage { PRESENT, PAST }

    private boolean sharedChannelSeen;
    private boolean effectChannelSeen;
    private int leadSamples;

    private short[] render(Stage stage, int music, int sfx, boolean withMusic, boolean withEffects,
                           boolean gainedPathAtUnity) {
        if (capture != null) capture.close();
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_MUSIC_VOLUME, music);
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_SFX_VOLUME, sfx);
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        profile = new SonicCdAudioProfile(() -> disc);
        AudioManagerInternalAccess.installMatchedSource(audio, null, profile);
        audio.setSoundMap(profile.getSoundMap());
        capture = audio.beginLiveCaptureAudio(60);
        CdSubCpuAudio subCpu = profile.subCpuAudio();
        subCpu.gainedPathAtUnity(gainedPathAtUnity);
        subCpu.loadStage(stage == Stage.PRESENT ? CdStagePayload.R11A : CdStagePayload.R71B);
        if (withMusic) audio.playMusic(stage == Stage.PRESENT ? PRESENT_MUSIC : PAST_MUSIC);
        short[] lead = present(LEAD);
        leadSamples = lead.length;
        if (withEffects) {
            audio.playSfx(FM_JUMP);
            audio.playMusic(FUTURE_VOICE);
        }
        short[] tail = present(TAIL, stage == Stage.PAST && withMusic && withEffects);
        short[] all = Arrays.copyOf(lead, lead.length + tail.length);
        System.arraycopy(tail, 0, all, lead.length, tail.length);
        return all;
    }

    private short[] present(int frames) {
        return present(frames, false);
    }

    private short[] present(int frames, boolean watchSharing) {
        short[] all = new short[0];
        short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
        for (int i = 0; i < frames; i++) {
            audio.presentFrame(PresentationMode.FORWARD);
            int n = capture.drainPresentationFrame(packet);
            all = Arrays.copyOf(all, all.length + n * 2);
            System.arraycopy(packet, 0, all, all.length - n * 2, n * 2);
            if (watchSharing) {
                int effects = profile.subCpuAudio().pcmDriver().sfxChannelMask();
                effectChannelSeen |= effects == 1 << 6;
                sharedChannelSeen |= (songChannels() & effects) != 0;
            }
        }
        return all;
    }

    private int songChannels() {
        CdPcmDriver driver = profile.subCpuAudio().pcmDriver();
        int mask = 0;
        for (int i = 1; i < 9; i++) {
            CdPcmDriver.Track t = driver.songTrack(i);
            if ((t.flags & 0x80) != 0) mask |= 1 << (t.channel & 7);
        }
        return mask;
    }

    private static short[] renderProgram(int musicGain, boolean gainedPathAtUnity) {
        CdSubCpuAudio subCpu = new CdSubCpuAudio(() -> { throw new AssertionError("no disc track"); });
        subCpu.bindOutputRate(48_000);
        subCpu.setMixGains(musicGain, AudioPresentationMixer.UNITY_GAIN);
        subCpu.gainedPathAtUnity(gainedPathAtUnity);
        CdSubCpuAudio.ProgramPcm program = subCpu.beginProgramPcm();
        short[] out = new short[800 * 2];
        for (int i = 0; i < 543; i++) {
            int left = (int) (9000 * Math.sin(i / 7.0)), right = (int) (-7000 * Math.sin(i / 5.0));
            program.sample((left << 16) | (right & 0xFFFF));
        }
        subCpu.render(out, 800);
        return out;
    }

    /** Music-only plus effects-only is the full render, within one step of rounding per source. */
    private static void assertSums(short[] full, short[] musicOnly, short[] effectsOnly) {
        assertEquals(full.length, musicOnly.length);
        int worst = 0;
        for (int i = 0; i < full.length; i++) {
            worst = Math.max(worst, Math.abs(full[i] - (musicOnly[i] + effectsOnly[i])));
        }
        System.out.println("max |full - (music + effects)| = " + worst);
        assertTrue(worst <= 2, "music-only plus effects-only differs from full by " + worst);
    }

    private static double energy(short[] pcm) {
        double e = 0;
        for (short s : pcm) e += (double) s * s;
        return e;
    }

}
