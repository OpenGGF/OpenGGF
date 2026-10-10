package com.openggf.mods.scene.host;

import com.openggf.audio.AudioManager;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomManager;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.host.music.ManagedSceneMusic;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Sonic 1 songs as cross-game donor music in a Sonic 3 &amp; Knuckles scene: the same route
 * that plays S3K Super music in Sonic 2, borrowed for the scene's lifetime.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSceneDonorMusic {
    private static final int GREEN_HILL = 0x81;
    private static final int MARBLE = 0x83;
    /**
     * Green Hill's first pass is an 864-frame intro and the 2304-frame body it loops back
     * to (examples/sitar-hero Sonic1Catalogue; TestSitarHeroS1SongCatalogue checks those
     * forms against the song's native loop jumps).
     */
    private static final int GHZ_INTRO = 864;
    private static final int GHZ_FIRST_PASS = 3_168;

    @TempDir Path temp;
    @Test
    void sceneMusicPacingFollowsRetainedActsAndEndsWithTheOwner() throws Exception {
        freshS3k();
        try (var capture = audio.beginLiveCaptureAudio(60)) {
            SceneContext ctx = openScene();
            ctx.audio().setMusicTempoPercent(70);
            assertTrue(ctx.audio().playMusic("s1", GREEN_HILL));
            present(capture, 30);
            assertEquals(70, audio.captureLogicalSnapshot().presentation().smpsSession().musicTempoPercent());
            host.suspend();
            audio.setRom(GameServices.rom().getRom());
            audio.setAudioProfile(new Sonic3kAudioProfile()); // Real level entry rebuilds the base source.
            audio.playMusic(0x20); // Native retained act owns the S3K base track.
            present(capture, 30);
            var nativeState = audio.captureLogicalSnapshot().presentation();
            assertEquals(70, nativeState.smpsSession().musicTempoPercent());
            assertEquals(70, nativeState.smpsLogical().sequencers().stream()
                    .filter(e -> !e.sfx()).findFirst().orElseThrow().snapshot().musicTempoPercent());
            closeScene();
            present(capture, 2);
            assertEquals(100, audio.captureLogicalSnapshot().presentation().smpsSession().musicTempoPercent());
            ctx.audio().setMusicTempoPercent(25); // Retired contexts cannot reclaim the music clock.
            present(capture, 2);
            assertEquals(100, audio.captureLogicalSnapshot().presentation().smpsSession().musicTempoPercent());
        }
    }
    private AudioManager audio;
    private ModSceneHost host;

    @AfterEach
    void restoreSharedAudio() {
        if (host != null) host.close();
        if (audio != null) {
            audio.resetState();
            audio.setBackend(new NullAudioBackend());
        }
    }

    @Test
    void sonic1GreenHillPlaysInAnS3kSessionWithS3kEffectsOnTopAndLoops() throws Exception {
        int rate = freshS3k().outputSampleRate();
        int frames = GHZ_FIRST_PASS + 600;
        short[] music;
        try (var capture = audio.beginLiveCaptureAudio(60)) {
            SceneContext ctx = openScene();
            assertTrue(ctx.audio().playMusic("s1", GREEN_HILL));
            music = present(capture, frames);
        }
        closeScene();

        // The donor route plays the song as Sonic 1's own driver does.
        short[] nativeSong = nativeSonic1(GREEN_HILL, frames);
        int frame = rate / 60;
        double best = -1;
        int bestLag = 0;
        for (int lag = -6 * frame; lag <= 6 * frame; lag += frame / 4) {
            double correlation = envelopeCorrelation(nativeSong, Math.max(0, -lag), music, Math.max(0, lag), rate,
                    (frames - 7) * frame);
            if (correlation > best) { best = correlation; bestLag = lag; }
        }
        // Control: another Sonic 1 song must not pass the same comparison.
        short[] marble = nativeSonic1(MARBLE, frames);
        double control = -1;
        for (int lag = -6 * frame; lag <= 6 * frame; lag += frame / 4) {
            control = Math.max(control, envelopeCorrelation(marble, Math.max(0, -lag), music, Math.max(0, lag),
                    rate, (frames - 7) * frame));
        }
        // Past the loop jump the song repeats its body, not its intro, and never falls silent.
        double loop = -1;
        for (int lag = -6 * frame; lag <= 6 * frame; lag += frame / 4) {
            loop = Math.max(loop, envelopeCorrelation(music, GHZ_INTRO * frame, music,
                    GHZ_FIRST_PASS * frame + lag, rate, 590 * frame));
        }
        double shifted = envelopeCorrelation(music, GHZ_INTRO * frame, music,
                (GHZ_INTRO + 97) * frame, rate, 590 * frame);
        double quietest = Double.MAX_VALUE;
        for (int start = 60 * frame; start + rate / 10 <= music.length / 2; start += rate / 10)
            quietest = Math.min(quietest, rms(music, start, rate / 10));
        System.out.printf("S1 Green Hill as S3K donor music: native envelope correlation %.4f at lag %d"
                + " (Marble control %.4f), loop-body correlation %.4f (97-frame shift control %.4f),"
                + " quietest 100 ms RMS %.1f%n", best, bestLag, control, loop, shifted, quietest);
        assertTrue(acVariance(Arrays.copyOf(music, 120 * frame * 2)) > 1_000, "Green Hill is audible");
        assertTrue(best > 0.9, "Green Hill in the S3K session follows Sonic 1's own driver; " + best);
        assertTrue(best > control + 0.3, "the comparison tells Green Hill from Marble; " + control);
        assertTrue(loop > 0.9, "the song loops to its body at its own loop point; " + loop);
        assertTrue(loop > shifted + 0.05, "the repeat lines up with the body, not any phrase; " + shifted);
        assertTrue(quietest > 50, "no silence or stop across the loop jump; " + quietest);

        // An S3K effect is heard over it.
        int warm = 120, window = 50;
        short[] both = runWithRing(true, warm, warm + window);
        short[] ringOnly = runWithRing(false, warm, warm + window);
        int split = warm * frame * 2;
        assertArrayEquals(Arrays.copyOf(music, split), Arrays.copyOf(both, split), "deterministic before the ring");
        short[] residual = new short[both.length - split];
        short[] ring = Arrays.copyOfRange(ringOnly, split, ringOnly.length);
        for (int index = split; index < both.length; index++) residual[index - split] = (short) (both[index] - music[index]);
        double agreement = correlation(residual, ring);
        for (int channel = 0; channel < 2; channel++) {
            double variance = channelVariance(residual, channel);
            System.out.printf("S3K ring over S1 Green Hill: channel %d residual variance %.1f%n", channel, variance);
            assertTrue(variance > 1_000, "the ring changes channel " + channel + " audibly: " + variance);
        }
        System.out.printf("S3K ring over S1 Green Hill: residual/ring-alone correlation %.3f%n", agreement);
        assertTrue(acVariance(ring) > 1_000, "the ring alone is audible");
        assertTrue(agreement > 0.5, "what the ring adds is the ring itself; correlation " + agreement);
    }

    @Test
    void sceneRoutesEachGameAndExitRestoresTheBorrowedDonorKey() throws Exception {
        freshS3k();
        Sonic1AudioProfile s1Profile = new Sonic1AudioProfile();
        SmpsLoader crossGame = mock(SmpsLoader.class);
        audio.registerDonorLoader("s1", crossGame, new DacData(Map.of(), Map.of()),
                s1Profile.getSequencerConfig(), s1Profile);
        try (var capture = audio.beginLiveCaptureAudio(60)) {
            SceneContext ctx = openScene();
            assertFalse(ctx.audio().playMusic("s2", 0x82), "Sonic 2 was not supplied to this scene");
            assertFalse(ctx.audio().playMusic("s1", 0x80), "Sonic 1 has no song 0x80");
            assertThrows(IllegalArgumentException.class, () -> ctx.audio().playMusic("s4", 1));
            assertThrows(IllegalArgumentException.class, () -> ctx.audio().playMusic("s1", -1));
            assertThrows(NullPointerException.class, () -> ctx.audio().playMusic(null, 1));
            assertSilent(present(capture, 5), "failed requests start nothing");

            assertTrue(ctx.audio().playMusic("S3K", Sonic3kMusic.AIZ1.id), "the base game's own route");
            assertTrue(acVariance(present(capture, 60)) > 1_000, "Angel Island plays");
            assertTrue(ctx.audio().playMusic("s1", GREEN_HILL));
            assertTrue(acVariance(present(capture, 60)) > 1_000, "Green Hill replaces it");
            verify(crossGame, never()).loadMusic(anyInt());
            ctx.audio().stopMusic();
            present(capture, 2);
            assertSilent(present(capture, 10), "stopMusic stops a donor song like any song");

            assertTrue(ctx.audio().playMusic("s1", GREEN_HILL));
            present(capture, 30);
            closeScene();
            present(capture, 2);
            assertSilent(present(capture, 20), "leaving the scene stops another game's song");
        }
        audio.playDonorMusic("s1", GREEN_HILL);
        verify(crossGame).loadMusic(GREEN_HILL);

        // The S3K 1-up takes over from a donor song and then gives it back, as for a stock song.
        short[][] plain = jingleRun(false);
        short[][] jingle = jingleRun(true);
        double replaced = rmsDifference(plain[0], jingle[0]) / rms(plain[0], 0, plain[0].length / 2);
        System.out.printf("S3K 1-up over S1 Green Hill: relative change while it plays %.2f%n", replaced);
        assertTrue(replaced > 0.5, "the jingle replaces Green Hill while it plays; " + replaced);
        assertTrue(acVariance(jingle[1]) > 1_000, "Green Hill returns after the 1-up jingle");

        try (var capture = audio.beginLiveCaptureAudio(60)) {
            SceneContext ctx = openScene();
            assertTrue(ctx.audio().playMusic("s1", GREEN_HILL));
            present(capture, 30);
            ctx.audio().playMusic(Sonic3kMusic.AIZ1.id);
            present(capture, 30);
            closeScene();
            assertTrue(acVariance(present(capture, 60)) > 1_000,
                    "the base game's own music is left playing at exit, as before");
            assertFalse(ctx.audio().playMusic("s1", GREEN_HILL), "a closed scene starts nothing");
        }
    }

    /** Green Hill for 30 frames, optionally the S3K 1-up, then 120 frames during and 60 well after it. */
    private short[][] jingleRun(boolean jingle) throws Exception {
        freshS3k();
        try (var capture = audio.beginLiveCaptureAudio(60)) {
            SceneContext ctx = openScene();
            assertTrue(ctx.audio().playMusic("s1", GREEN_HILL));
            present(capture, 30);
            if (jingle) ctx.audio().playMusic(Sonic3kMusic.EXTRA_LIFE.id);
            short[] during = present(capture, 120);
            present(capture, 480);
            short[] after = present(capture, 60);
            closeScene();
            present(capture, 2);
            assertSilent(present(capture, 20), "the returned donor song still stops when the scene leaves");
            return new short[][] {during, after};
        }
    }

    private short[] runWithRing(boolean music, int ringFrame, int frames) throws Exception {
        freshS3k();
        try (var capture = audio.beginLiveCaptureAudio(60)) {
            SceneContext ctx = openScene();
            if (music) assertTrue(ctx.audio().playMusic("s1", GREEN_HILL));
            short[] result = new short[0];
            for (int frame = 0; frame < frames; frame++) {
                if (frame == ringFrame) ctx.audio().playSfx(Sonic3kSfx.RING_RIGHT.id);
                result = concat(result, present(capture, 1));
            }
            closeScene();
            return result;
        }
    }

    private AudioManager freshS3k() throws java.io.IOException {
        Rom rom = GameServices.rom().getRom();
        Sonic3kAudioProfile profile = new Sonic3kAudioProfile();
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setRom(rom);
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
        return audio;
    }

    /** Opens a scene on the S3K base with Sonic 1 supplied as a second ROM. */
    private SceneContext openScene() throws Exception {
        File s1 = RomTestUtils.ensureSonic1RomAvailable();
        assumeTrue(s1 != null && s1.exists(), "Sonic 1 ROM not available");
        RomManager roms = mock(RomManager.class);
        when(roms.isLogicalRomAvailable(RomIdentity.S1)).thenReturn(true);
        when(roms.openLogicalRom(RomIdentity.S1)).thenReturn(new RomByteReader(Files.readAllBytes(s1.toPath())));
        GameModule module = mock(GameModule.class);
        when(module.getGameId()).thenReturn(GameId.S3K);
        SceneRomLibrary library = new SceneRomLibrary(module, GameServices.rom().getRom(), roms);
        SceneContext[] retained = new SceneContext[1];
        host = new ModSceneHost();
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        host.open(ModContextTestAccess.ownedScene("valley", () -> new ModScene() {
            @Override public void enter(SceneContext ctx) { retained[0] = ctx; }
            @Override public void update(SceneContext ctx) { }
            @Override public void draw(SceneContext ctx, SceneCanvas canvas) { }
        }, boundary), new SceneServices(audio, null, temp, null, () -> { }, () -> { }, library), 320, 224);
        return retained[0];
    }

    private void closeScene() {
        host.close();
        host = null;
    }

    /** Sonic 1's own driver rendering of a song, at this session's output rate. */
    private short[] nativeSonic1(int musicId, int frames) throws Exception {
        File s1 = RomTestUtils.ensureSonic1RomAvailable();
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(s1.getAbsolutePath()));
            try (var music = new ManagedSceneMusic(audio, game -> rom)) {
                Object song = music.prepare("s1", musicId, frames);
                var field = song.getClass().getDeclaredField("full");
                field.setAccessible(true);
                return ((short[]) field.get(song)).clone();
            }
        }
    }

    private short[] present(LiveCaptureAudioHandle capture, int frames) {
        short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
        short[] result = new short[frames * packet.length];
        int length = 0;
        for (int frame = 0; frame < frames; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
            int count = capture.drainPresentationFrame(packet) * 2;
            System.arraycopy(packet, 0, result, length, count);
            length += count;
        }
        return Arrays.copyOf(result, length);
    }

    private static void assertSilent(short[] pcm, String message) {
        assertTrue(pcm.length > 0, message);
        assertArrayEquals(new short[pcm.length], pcm, message);
    }

    private static short[] concat(short[] first, short[] second) {
        short[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static double rmsDifference(short[] first, short[] second) {
        double sum = 0;
        for (int index = 0; index < first.length; index++) {
            double difference = first[index] - second[index];
            sum += difference * difference;
        }
        return Math.sqrt(sum / first.length);
    }

    private static double rms(short[] pcm, int firstFrame, int frames) {
        double sum = 0;
        for (int index = firstFrame * 2; index < (firstFrame + frames) * 2; index++) sum += (double) pcm[index] * pcm[index];
        return Math.sqrt(sum / (frames * 2.0));
    }

    /** Pearson correlation of 100 ms RMS envelopes of two equal stereo-frame spans. */
    private static double envelopeCorrelation(short[] first, int firstFrame, short[] second, int secondFrame,
                                              int rate, int frames) {
        int block = rate / 10, blocks = frames / block;
        double[] a = new double[blocks], b = new double[blocks];
        for (int index = 0; index < blocks; index++) {
            a[index] = rms(first, firstFrame + index * block, block);
            b[index] = rms(second, secondFrame + index * block, block);
        }
        return pearson(a, b);
    }

    private static double correlation(short[] first, short[] second) {
        double[] a = new double[first.length], b = new double[first.length];
        for (int index = 0; index < first.length; index++) { a[index] = first[index]; b[index] = second[index]; }
        return pearson(a, b);
    }

    private static double pearson(double[] a, double[] b) {
        double meanA = Arrays.stream(a).average().orElse(0), meanB = Arrays.stream(b).average().orElse(0);
        double covariance = 0, varianceA = 0, varianceB = 0;
        for (int index = 0; index < a.length; index++) {
            covariance += (a[index] - meanA) * (b[index] - meanB);
            varianceA += (a[index] - meanA) * (a[index] - meanA);
            varianceB += (b[index] - meanB) * (b[index] - meanB);
        }
        return covariance / Math.sqrt(varianceA * varianceB);
    }

    private static double acVariance(short[] samples) {
        double mean = 0;
        for (short sample : samples) mean += sample;
        mean /= samples.length;
        double sum = 0;
        for (short sample : samples) sum += (sample - mean) * (sample - mean);
        return sum / samples.length;
    }

    private static double channelVariance(short[] stereo, int channel) {
        double mean = 0;
        int count = stereo.length / 2;
        for (int index = channel; index < stereo.length; index += 2) mean += stereo[index];
        mean /= count;
        double sum = 0;
        for (int index = channel; index < stereo.length; index += 2) sum += (stereo[index] - mean) * (stereo[index] - mean);
        return sum / count;
    }
}
