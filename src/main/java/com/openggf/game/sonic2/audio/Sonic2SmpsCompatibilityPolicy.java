package com.openggf.game.sonic2.audio;

import com.openggf.audio.session.LegacyCompatibilitySmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsChipWrite;
import com.openggf.audio.session.SmpsMusicActivation;
import com.openggf.audio.session.SmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsWriteProgram;

import java.util.ArrayList;
import java.util.List;

/** Named S2 host policy retaining the verified pre-migration 202-write program. */
public final class Sonic2SmpsCompatibilityPolicy
        implements SmpsPhysicalPolicy {
    public static final Sonic2SmpsCompatibilityPolicy INSTANCE =
            new Sonic2SmpsCompatibilityPolicy();

    private static final Identity IDENTITY =
            new Identity("sonic2-compatibility-v1");
    private static final LegacyCompatibilitySmpsPhysicalPolicy DELEGATE =
            LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;

    private Sonic2SmpsCompatibilityPolicy() {
    }

    @Override
    public Identity identity() {
        return IDENTITY;
    }

    @Override
    public SmpsWriteProgram boot() {
        return DELEGATE.boot();
    }

    @Override
    public SmpsWriteProgram stopAll() {
        return DELEGATE.stopAll();
    }

    @Override
    public SmpsWriteProgram beginMusicLoad() {
        // s2.sounddriver.asm zBGMLoad enters zInitMusicPlayback before
        // OptimiseDriver=0 zSaxmanDec: 6 key-offs, 192 register clears and
        // four PSG silences. FixDriverBugs=0 is the shipped path.
        return DELEGATE.stopAll();
    }

    @Override
    public SmpsWriteProgram activateMusic(SmpsMusicActivation activation) {
        return activationProgram(activation.fmDacTrackCount(),
                activation.psgTrackCount());
    }

    /**
     * The register writes zBGMLoad performs, in the shipped driver's own
     * order. The file assembles with {@code FixDriverBugs = fixBugs = 0} and
     * {@code OptimiseDriver = 0} (s2.sounddriver.asm:8-9, s2.asm:27), so every
     * conditional block reproduced here is present in the shipped ROM.
     */
    private static SmpsWriteProgram activationProgram(int fmDacTrackCount,
            int psgTrackCount) {
        List<SmpsChipWrite> writes = new ArrayList<>(16);
        appendFm6Disposition(writes, fmDacTrackCount);
        appendLoadNoteOffs(writes, fmDacTrackCount, psgTrackCount);
        return new SmpsWriteProgram(writes);
    }

    /**
     * zBGMLoad's FM6 disposition (s2.sounddriver.asm:1893-1938).
     *
     * <p>A song declaring seven FM+DAC tracks is using FM6 as a real FM
     * channel, so the load only tells the chip the DAC is off. Any other count
     * leaves FM6 to the DAC track: {@code .silencefm6} keys the channel off,
     * silences its four operator total-level registers, resets its panning
     * (the DAC track never runs zSetVoice, so nothing else would), and only
     * then enables the DAC at {@code .writesilence}.
     *
     * <p>With {@code FixDriverBugs = 1} the key-off and the total-level loop
     * would both be gone, deferred to a later zFMNoteOff and
     * zFMSilenceChannel; the engine takes the shipped {@code = 0} path.
     */
    private static void appendFm6Disposition(List<SmpsChipWrite> writes,
            int fmDacTrackCount) {
        if (fmDacTrackCount == 7) {
            // :1893-1898 - FM6 is in use, so the DAC stays disabled.
            writes.add(new SmpsChipWrite.Ym2612(0, 0x2B, 0x00));
            return;
        }
        // .silencefm6 :1900-1904 - key off all four operators of FM6.
        writes.add(new SmpsChipWrite.Ym2612(0, 0x28, 0x06));
        // :1906-1916 - total silence on FM6's four total level registers.
        for (int register = 0x42; register <= 0x4E; register += 4) {
            writes.add(new SmpsChipWrite.Ym2612(1, register, 0xFF));
        }
        // :1918-1935 - default stereo panning for the DAC track's channel.
        writes.add(new SmpsChipWrite.Ym2612(1, 0xB6, 0xC0));
        // .writesilence :1936-1938 - FM6 is free, so the DAC is enabled.
        writes.add(new SmpsChipWrite.Ym2612(0, 0x2B, 0x80));
    }

    /**
     * The load's closing note-off sweep (s2.sounddriver.asm:2051-2075):
     * {@code zFMNoteOff} over all six music FM slots, then
     * {@code zPSGNoteOff} over all three music PSG slots. Both loops walk the
     * fixed track region rather than the song's track list, so a song that
     * declares fewer tracks still sweeps every slot.
     *
     * <p>Each call sends the slot's own {@code VoiceControl} byte: 28h for FM
     * (:2814-2827) and {@code VoiceControl | 1Fh} to the PSG port
     * (:1357-1367). A slot the song loaded took its byte from
     * {@code zFMDACInitBytes} or {@code zPSGInitBytes} (:1838, :1952,
     * :2107-2113); a slot it did not is still zero, because {@code zBGMLoad} calls
     * {@code zInitMusicPlayback} first and that clears the whole music track
     * region (:1739, :2580-2612).
     *
     * <p>Neither early return can fire here. Bit 4 is clear across the region
     * the clear just wiped, and {@code zInitSFX}'s {@code .trackstore}
     * clears bit 2 rather than setting it under {@code FixDriverBugs = 0}
     * (:2036-2043), which the listing itself flags as a bug against S1's
     * driver.
     */
    private static void appendLoadNoteOffs(List<SmpsChipWrite> writes,
            int fmDacTrackCount, int psgTrackCount) {
        for (int slot = 0; slot < MUSIC_FM_TRACK_COUNT; slot++) {
            // zFMDACInitBytes' first byte belongs to the DAC track, so FM slot
            // n is loaded only while n + 1 is inside the song's count.
            int voiceControl = slot + 1 < fmDacTrackCount
                    ? FM_DAC_INIT_BYTES[slot + 1] : 0x00;
            writes.add(new SmpsChipWrite.Ym2612(0, 0x28, voiceControl));
        }
        for (int slot = 0; slot < MUSIC_PSG_TRACK_COUNT; slot++) {
            int voiceControl = slot < psgTrackCount
                    ? PSG_INIT_BYTES[slot] : 0x00;
            writes.add(new SmpsChipWrite.Psg(voiceControl | 0x1F));
        }
    }

    /** MUSIC_FM_TRACK_COUNT (s2.sounddriver.asm:237, :2052). */
    private static final int MUSIC_FM_TRACK_COUNT = 6;

    /** MUSIC_PSG_TRACK_COUNT (s2.sounddriver.asm:238, :2069). */
    private static final int MUSIC_PSG_TRACK_COUNT = 3;

    /** zFMDACInitBytes: the DAC track, then FM1-FM3 and FM4-FM6 across the
     * YM2612's part gap (s2.sounddriver.asm:2107-2108). */
    private static final int[] FM_DAC_INIT_BYTES = { 6, 0, 1, 2, 4, 5, 6 };

    /** zPSGInitBytes: the PSG port latch for each music PSG channel
     * (s2.sounddriver.asm:2112-2113). */
    private static final int[] PSG_INIT_BYTES = { 0x80, 0xA0, 0xC0 };
}
