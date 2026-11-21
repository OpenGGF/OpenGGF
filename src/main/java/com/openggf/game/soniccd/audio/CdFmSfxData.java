package com.openggf.game.soniccd.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSfxData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One Sonic CD FM sound effect, read from the driver bank the disc's MDINIT copies to Z80 RAM.
 *
 * <p>The program bytes are the whole installed bank at Z80 origin 0, so every pointer in a
 * header or track is an absolute Z80 address and equally an index into {@link #data}.
 * Header layout, {@code PlaySFX} (FM/_Driver.asm:803-860): word instrument
 * table pointer, byte tick multiplier, byte track count, then per track flags, channel, data
 * pointer (little-endian word), transposition and volume (the six bytes {@code ldi} copies
 * at FM/_Driver.asm:826-845). The sound's index entry is {@code SFXIndex} (FM/Data.asm:17-97), read through
 * {@code DrvInfo_SFXIndex} (FM/_Driver.asm:1874-1886).
 *
 * <p>Instruments are 19h bytes ({@code GetInstrumentPtr}, FM/_Driver.asm:644-660) in the register
 * order of {@code InstrumentRegs} (FM/_Driver.asm:1094-1103): feedback/algorithm, then detune/multiply, rate
 * scale/attack, AM/decay 1, decay 2, sustain/release and total level for four operators, the
 * same layout the shared sequencer's Z80 voice profile reads.
 */
public final class CdFmSfxData extends AbstractSmpsData implements SmpsSfxData {
    /** One {@code smpsHeaderSFXChannel} entry. */
    public record TrackEntry(int playbackFlags, int channelMask, int pointer,
                             int transpose, int volume, boolean holdsFmState) implements SmpsSfxTrack { }

    private static final int DUST = 0xD6;
    private static final int SOUND_DISABLED = 0x04;
    private static final int BANK_WINDOW_START = 0x8000;
    private static final int VOICE_BYTES = 0x19;

    private final int headerAddress;
    private final List<TrackEntry> tracks = new ArrayList<>();
    private int tickMultiplier = 1;

    CdFmSfxData(byte[] bank, int soundId, int headerAddress) {
        super(bank, 0, true);
        this.headerAddress = headerAddress;
        setId(soundId);
        parseHeader();
    }

    /** Z80 address of this sound's header in the installed bank. */
    public int headerAddress() {
        return headerAddress;
    }

    @Override
    protected void parseHeader() {
        tracks.clear();
        int base = headerAddress;
        voicePtr = read16(base);
        // PlaySFX copies (iy+2) to tickMult and from it every track's ftrkTickMult (FM/_Driver.asm:818-838).
        tickMultiplier = data[base + 2] & 0xFF;
        dividingTiming = tickMultiplier;
        tempo = 0;
        int count = data[base + 3] & 0xFF;
        int pos = base + 4;
        int trackDataStart = data.length;
        for (int i = 0; i < count && pos + 6 <= data.length; i++, pos += 6) {
            int flags = data[pos] & 0xFF;
            int channel = data[pos + 1] & 0xFF;
            int pointer = read16(pos + 2);
            boolean overlapsTrackData = pos + 6 > trackDataStart;
            // FixBugs=0 uses the declared count. SonicCD_D6_Header (Sound Drivers/FM/SFX/D6.asm:1-11)
            // has a second entry at the start of the FM6 stream. The corrected count would be one.
            // PlaySFX (Sound Drivers/FM/_Driver.asm:826-861) takes over FM1 with sound disabled.
            // SetKeyOff (Sound Drivers/FM/_Driver.asm:545-556) and WriteYM1Trk
            // (Sound Drivers/FM/_Driver.asm:674-677) suppress the admission writes.
            // The pointer reaches the boot ROM, so keep the chip state until FM1 is replaced.
            boolean holdsFmState = getId() == DUST && overlapsTrackData && channel == 0
                    && (flags & SOUND_DISABLED) != 0 && pointer >= BANK_WINDOW_START;
            if (overlapsTrackData && !holdsFmState) break;
            if (pointer >= pos + 6 && pointer < trackDataStart) trackDataStart = pointer;
            tracks.add(new TrackEntry(flags, channel, pointer, data[pos + 4], data[pos + 5], holdsFmState));
        }
        // The driver has six FM track slots and no PSG or DAC tracks (FM/_Variables.inc:11, :98-103).
        channels = tracks.size();
        psgChannels = 0;
    }

    @Override
    public int getTickMultiplier() {
        return tickMultiplier;
    }

    @Override
    public List<TrackEntry> getTrackEntries() {
        return Collections.unmodifiableList(tracks);
    }

    @Override
    public byte[] getVoice(int voiceId) {
        int offset = voicePtr + voiceId * VOICE_BYTES;
        if (voicePtr == 0 || offset < 0 || offset + VOICE_BYTES > data.length) {
            return null;
        }
        byte[] voice = new byte[VOICE_BYTES];
        System.arraycopy(data, offset, voice, 0, VOICE_BYTES);
        return voice;
    }

    /** The FM-only driver has no PSG envelopes (no PSG tracks, FM/_Variables.inc:98-103). */
    @Override
    public byte[] getPsgEnvelope(int id) {
        return null;
    }

    /** {@code ReadHLPtr}: little-endian Z80 words (FM/_Driver.asm:76-81). */
    @Override
    public int read16(int offset) {
        if (offset < 0 || offset + 1 >= data.length) {
            return 0;
        }
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * {@code .GetNote}: the note byte minus 81h plus transposition, split into octave and
     * semitone ({@code .GetNote}, FM/_Driver.asm:287-312) with {@code FreqTable} starting at C (FM/_Driver.asm:1117-1119).
     */
    @Override
    public int getBaseNoteOffset() {
        return 0;
    }
}
