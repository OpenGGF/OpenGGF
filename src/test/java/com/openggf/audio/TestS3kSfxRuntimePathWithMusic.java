package com.openggf.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsTrackSnapshot;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The reported Sonic 3&amp;K effects, issued the way the game issues them:
 * through {@link AudioManager} with AIZ1 music loaded and presenting.
 *
 * <p>The driver-level fixture
 * {@code com.openggf.game.sonic3k.audio.TestS3kSfxNoiseTailWriteStream} pins
 * these effects' write streams against their own ROM scripts with nothing else
 * playing. This class covers the other half of the reported symptom, which was
 * always observed in play: the effects arriving over the runtime request path
 * while music holds the channels they need, where an effect can be cut by the
 * music's channel restore or by a second request.
 *
 * <p>Channel numbering: the PSG noise channel latches its volume at
 * {@code F0h + attenuation}, so {@code FFh} is its silence.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSfxRuntimePathWithMusic {
    /** Latched noise-channel volume: {@code F0h} plus a 4-bit attenuation. */
    private static final int NOISE_VOLUME_LATCH = 0xF0;

    /** The noise channel's silence level. */
    private static final int NOISE_SILENCE = 0x0F;

    private final List<Integer> psgWrites = new ArrayList<>();
    private Rom rom;

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
        if (rom != null) {
            rom.close();
        }
    }

    /**
     * Collapse's audible part is a PSG3 noise track that outlives its FM
     * tracks and fades over six repeats
     * (skdisasm Sound/SFX/59 - Collapse.asm:24-36). With music playing, the
     * effect must take the noise channel, ramp through its own attenuation
     * ladder and silence the channel once, at the end.
     */
    @Test
    void collapseRampsAndSilencesOnceWithMusicPlaying() {
        AudioManager audio = installWithAiz1();
        List<Integer> levels = noiseLevelsForRequest(
                audio, Sonic3kSfx.COLLAPSE.id, 200);

        assertEquals(List.of(0x00, 0x03, 0x06, 0x09, 0x0C, 0x0F), levels,
                "Collapse must ring out through its ROM attenuation ladder over"
                + " AIZ1 music, not stop dead; noise levels were " + levels);
        assertEquals(1, countSilences(levels),
                "Collapse's tail must reach the noise channel's silence once,"
                + " at the end; noise levels were " + levels);
    }

    /**
     * Dash is the spindash release
     * (skdisasm sonic3k.asm:21824's {@code Play_SFX} call from the release
     * routine). Its FM5 track stops at {@code $0F} ticks while its PSG3 noise
     * track sounds for {@code $06 + $4F}
     * (Sound/SFX/B6 - Dash.asm:14-23), so the release must not end when the FM
     * part does.
     */
    @Test
    void spindashRevLadderThenReleasePlaysItsWholeTail() {
        AudioManager audio = installWithAiz1();
        observe(audio);

        // The rev ladder as the game issues it: one request per charge tap,
        // each transposing the effect through cfSpindashRev
        // (Sound/SFX/AB - Spin Dash.asm:11-24).
        for (int rev = 0; rev < 4; rev++) {
            assertTrue(audio.playSfx(Sonic3kSfx.SPINDASH.id),
                    "the spindash rev must be accepted on charge tap " + rev);
            for (int frame = 0; frame < 6; frame++) {
                service(audio);
            }
        }

        List<Integer> levels = noiseLevelsForRequest(
                audio, Sonic3kSfx.DASH.id, 160);

        assertFalse(levels.isEmpty(),
                "the spindash release must reach the noise channel after the"
                + " rev ladder");
        assertTrue(levels.get(0) < NOISE_SILENCE, () ->
                "the spindash release must sound before it is silenced;"
                + " noise levels were " + levels);
        assertEquals(1, countSilences(levels), () ->
                "the spindash release must be silenced once, at the end of its"
                + " tail. Re-silencing it every pass is what made the release"
                + " buzz rather than sweep; noise levels were " + levels);
        assertEquals(NOISE_SILENCE, levels.get(levels.size() - 1).intValue(),
                "the spindash release ends on the noise channel's silence");
    }

    /**
     * The insta-shield arrives while the jump's roll effect is still sounding,
     * so it takes FM channels from a live SFX rather than from music.
     * {@code zSFXTrackInitLoop} keys off each incoming track and clears its
     * SSG-EG operators without asking who holds the channel
     * (Sound/Z80 Sound Driver.asm:2092-2103), so the incoming effect's own
     * writes must still reach the chip.
     */
    @Test
    void instaShieldOverTheJumpEffectStillReachesTheChip() {
        AudioManager audio = installWithAiz1();
        observe(audio);

        assertTrue(audio.playSfx(Sonic3kSfx.ROLL.id),
                "the jump's roll effect must be accepted over music");
        for (int frame = 0; frame < 3; frame++) {
            service(audio);
        }

        List<String> instaWrites = new ArrayList<>();
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                instaWrites.add(String.format(
                        "ym%d[%02X]=%02X", port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
                instaWrites.add(String.format("psg[%02X]", value & 0xFF));
            }
        });

        assertTrue(audio.playSfx(Sonic3kSfx.INSTA_SHIELD.id),
                "the insta-shield must be accepted while the roll still sounds");
        for (int frame = 0; frame < 8; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }

        assertFalse(instaWrites.isEmpty(),
                "the insta-shield must put its own writes on the bus while"
                + " taking channels from the live roll effect"
                + " (Sound/Z80 Sound Driver.asm:2092-2103)");
        assertTrue(instaWrites.stream().anyMatch(w -> w.startsWith("psg[")),
                () -> "the insta-shield is a noise-form effect and must reach"
                + " the PSG; writes were " + instaWrites);
    }

    /**
     * Issues one effect and returns the noise-channel attenuation levels it
     * puts on the bus, run-length reduced, with the setup silence dropped.
     *
     * <p>Every noise-form effect opens by silencing the noise channel during
     * setup (Sound/Z80 Sound Driver.asm:3541-3571), so that leading {@code $0F}
     * is the ROM's and the ladder proper begins after it.
     */
    private List<Integer> noiseLevelsForRequest(
            AudioManager audio, int sfxId, int frames) {
        psgWrites.clear();
        observe(audio);
        assertTrue(audio.playSfx(sfxId),
                () -> String.format("SFX 0x%02X must be accepted over music", sfxId));
        // AIZ1's own music drums use the noise channel, so collecting past the
        // effect's life would mix the music's writes into the measurement. The
        // effect owns the channel while its PSG track is alive, and the music
        // track's override is what keeps the music off it in the meantime.
        for (int frame = 0; frame < frames; frame++) {
            audio.presentFrame(PresentationMode.FORWARD);
            if (frame > 0 && !sfxPsgTrackAlive(audio)) {
                break;
            }
        }

        List<Integer> levels = new ArrayList<>();
        for (int write : psgWrites) {
            if ((write & 0xF0) != NOISE_VOLUME_LATCH) {
                continue;
            }
            int level = write & 0x0F;
            if (levels.isEmpty() || levels.get(levels.size() - 1) != level) {
                levels.add(level);
            }
        }
        if (!levels.isEmpty() && levels.get(0) == NOISE_SILENCE) {
            levels.remove(0);
        }
        return levels;
    }

    /** Whether any live SFX still holds a PSG track, and so the noise channel. */
    private boolean sfxPsgTrackAlive(AudioManager audio) {
        for (SmpsDriverSnapshot.SequencerEntry entry
                : audio.shadowSmpsDriverSnapshotForTesting().sequencers()) {
            if (!entry.sfx()) {
                continue;
            }
            for (SmpsTrackSnapshot track : entry.snapshot().tracks()) {
                if (track.type() == SmpsSequencer.TrackType.PSG && track.active()) {
                    return true;
                }
            }
        }
        return false;
    }

    private int countSilences(List<Integer> levels) {
        int silences = 0;
        for (int level : levels) {
            if (level == NOISE_SILENCE) {
                silences++;
            }
        }
        return silences;
    }

    private void service(AudioManager audio) {
        audio.presentFrame(PresentationMode.FORWARD);
    }

    private void observe(AudioManager audio) {
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
            }

            @Override
            public void onPsgWrite(int value) {
                psgWrites.add(value & 0xFF);
            }
        });
    }

    private AudioManager installWithAiz1() {
        File file = RomTestUtils.ensureSonic3kRomAvailable();
        rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        Sonic3kAudioProfile profile = new Sonic3kAudioProfile();
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setRom(rom);
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
        audio.playMusic(Sonic3kMusic.AIZ1.id);
        for (int warm = 0; warm < 4; warm++) {
            audio.presentFrame(PresentationMode.FORWARD);
        }
        return audio;
    }
}
