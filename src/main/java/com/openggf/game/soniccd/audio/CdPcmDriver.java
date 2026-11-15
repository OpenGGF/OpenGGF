package com.openggf.game.soniccd.audio;

import com.openggf.game.soniccd.device.pcm.CdPcmDevice;

import java.util.Arrays;
import java.util.Objects;

/**
 * The SMPS-PCM driver the sub CPU runs from a per-zone {@code SNCBNKxB.BIN} bank
 * (src/Sound Drivers/PCM/_Driver.asm), reimplemented over {@link CdPcmDevice}.
 *
 * <p>Its track bytes are SMPS (notes, durations, tick multiplier, E0-FF commands with 68k
 * PC-relative jumps), but it is not hosted on the shared SMPS sequencer: that sequencer's tracks
 * are FM, PSG or DAC and every note ends in YM2612 block/F-number or PSG divider writes, while
 * this driver's notes are RF5C164 frequency steps from its own table ({@code FreqTable},
 * PCM/_Driver.asm:1089-1111), its channels are keyed through the chip's on/off byte
 * ({@code NoteOn}/{@code NoteOff}, :891-922), and a streamed sample is refilled from the chip's
 * read-back play address ({@code UpdateSample}/{@code StreamSample}, :309-435). None of those
 * has a counterpart in the shared sequencer, and its control flow exits differ
 * ({@code HandleStaccato}, :235-259; {@code UpdateFreq}, :261-289).
 *
 * <p>Data is read from the bank at its load address: {@code PCMDriver} is PRG-RAM 40000h
 * (_Include/Sound.inc:22), the bank is assembled there ({@code org PCMDriver}, PCM/Palmtree
 * Panic.asm), so every {@code dc.l} pointer is an absolute 4xxxxh address and
 * {@code pdrvPtrOffset} is zero ({@code InitDriver}, :1058-1077).
 *
 * <p>One call to {@link #tick()} is one {@code UpdateDriver} (:40-57). The caller owns the
 * clock: the level-3 timer fires every 255 timer clocks of 384 sub-CPU cycles
 * ({@code GAIRQ3TIME} 255, CD System Program/SPX.asm:306-308; one timer clock is 30.72 us,
 * 384 cycles of the 12.5 MHz sub-CPU: MAME 9cf98d88 src/mame/sega/megacd.cpp:10,19
 * {@code SEGACD_CLOCK}, {@code SEGACD_IRQ3_TIMER_SPEED}), {@code RunPCMDriver} runs the
 * driver on every other interrupt (SPX.asm:2481-2491), and the RF5C164, clocked from the same
 * 12.5 MHz, makes one sample per 384 clocks (MAME megacd.cpp:304, src/devices/sound/rf5c68.cpp:84)
 * — one tick per {@link #CHIP_SAMPLES_PER_TICK} chip samples.
 *
 * <p>Shipped {@code FixBugs = 0} behaviour is kept: a track whose staccato runs out ends the
 * whole track walk for the tick ({@code HandleStaccato}, :241-252); the sample table's count is
 * one short, so the last sample is never preloaded ({@code SAMPTBLSTART}, PCM/_Macros.inc;
 * {@code LoadSamples}, :437-501); calls push into the repeat counters ({@code TrkCmd_Call},
 * :1423-1437, over {@code ptrkRepeatCnts}, PCM/_Variables.inc); an effect shares its channel
 * with the song track on it ({@code PlaySFX}, :695-770).
 */
public final class CdPcmDriver {
    /** 255 timer clocks per interrupt, two interrupts per run, 384 cycles per clock and per sample. */
    public static final int CHIP_SAMPLES_PER_TICK = 255 * 2;
    /** Sub-CPU clock / 384: 12.5 MHz chip clock. */
    public static final int CHIP_RATE_NUMERATOR = 12_500_000;
    public static final int CHIP_RATE_DENOMINATOR = 384;

    static final int BANK_ADDRESS = 0x40000;
    private static final int ORIGIN = 0x40010;
    private static final int TRACK_COUNT = 9;          // rhythm + PCM1..8 (RHY_TRACK_CNT + PCM_TRACK_CNT)
    private static final int SFX_TRACK_COUNT = 8;
    private static final int[] SONG_CHANNELS = {7, 0, 1, 2, 3, 4, 5, 7, 6};   // ChannelIDs, :674-686
    private static final int FLAG_REST = 1 << 1, FLAG_LEGATO = 1 << 4, FLAG_PLAY = 1 << 7;
    private static final int SONG_START = 0x81, SONG_END = 0x8F;               // PCM/_Sound IDs.inc
    private static final int SFX_START = 0xB0, SFX_END = 0xBF;
    private static final int CMD_START = 0xE0, CMD_END = 0xE4;
    private static final int REG_ENV = 0, REG_PAN = 1, REG_FDL = 2, REG_FDH = 3, REG_LSL = 4,
            REG_LSH = 5, REG_ST = 6, REG_CTRL = 7, REG_ONOFF = 8, WAVE = 0x1000;

    /** One {@code ptrk} structure (PCM/_Variables.inc); 40h-7Fh holds repeat counts and the call stack. */
    static final class Track {
        int flags, channel, tickMult, panning, dataPtr, transpose, volume, stackPtr, durCnt, duration;
        int stacCnt, staccato, detune, freq, sampleBank, sampleBlks, prevSampPos, sampRamOff;
        int sampleRam, sampleSize, sampRemain, samplePtr, sampleStart, sampleLoop;
        int sampStac, sampStacCnt, sampleMode;
        final byte[] high = new byte[0x40];

        void clear() {
            flags = channel = tickMult = panning = dataPtr = transpose = volume = stackPtr = durCnt = duration = 0;
            stacCnt = staccato = detune = freq = sampleBank = sampleBlks = prevSampPos = sampRamOff = 0;
            sampleRam = sampleSize = sampRemain = samplePtr = sampleStart = sampleLoop = 0;
            sampStac = sampStacCnt = sampleMode = 0;
            Arrays.fill(high, (byte) 0);
        }

        Track copy() {
            Track t = new Track();
            t.flags = flags; t.channel = channel; t.tickMult = tickMult; t.panning = panning;
            t.dataPtr = dataPtr; t.transpose = transpose; t.volume = volume; t.stackPtr = stackPtr;
            t.durCnt = durCnt; t.duration = duration; t.stacCnt = stacCnt; t.staccato = staccato;
            t.detune = detune; t.freq = freq; t.sampleBank = sampleBank; t.sampleBlks = sampleBlks;
            t.prevSampPos = prevSampPos; t.sampRamOff = sampRamOff; t.sampleRam = sampleRam;
            t.sampleSize = sampleSize; t.sampRemain = sampRemain; t.samplePtr = samplePtr;
            t.sampleStart = sampleStart; t.sampleLoop = sampleLoop; t.sampStac = sampStac;
            t.sampStacCnt = sampStacCnt; t.sampleMode = sampleMode;
            System.arraycopy(high, 0, t.high, 0, high.length);
            return t;
        }

        /** Repeat counters and pushed return addresses share 40h-7Fh of the structure. */
        int highByte(int offset) { return high[offset - 0x40] & 0xFF; }
        void setHighByte(int offset, int value) { high[offset - 0x40] = (byte) value; }
    }

    /** Immutable rewind copy of the driver variables ({@code pdrv*}) and every track. */
    public record State(int[] vars, Track[] song, Track[] sfx) { }

    private final byte[] bank;
    private final CdPcmDevice chip;
    private final int songPriorities, songIndex, sfxIndex, sfxPriorities, cmdPriorities, sampleIndex;
    private final Track[] song = new Track[TRACK_COUNT];
    private final Track[] sfx = new Track[SFX_TRACK_COUNT];
    private int tempo, tempoCnt, on, sfxPrio, commFlag, cddaLoop, sndPlay, sfxMode, pauseMode;
    private int fadeSteps, fadeSpeed, fadeDelay, fadeCnt, unkFadeVol;
    private final int[] queue = new int[4];

    public CdPcmDriver(byte[] bank, CdPcmDevice chip) {
        this.bank = Objects.requireNonNull(bank, "bank").clone();
        this.chip = Objects.requireNonNull(chip, "chip");
        for (int i = 0; i < TRACK_COUNT; i++) song[i] = new Track();
        for (int i = 0; i < SFX_TRACK_COUNT; i++) sfx[i] = new Track();
        int info = findDriverInfo();
        songPriorities = longAt(info);
        songIndex = longAt(info + 8);
        sfxIndex = longAt(info + 12);
        sfxPriorities = longAt(info + 32);
        cmdPriorities = longAt(info + 36);
        sampleIndex = findSampleIndex();
    }

    /** {@code PCMDrvQueue}: the system program writes one byte to the first slot (SPX.asm:1524-1650). */
    public void queue(int id) {
        queue[0] = id & 0xFF;
    }

    /** {@code pdrvCommFlag}, written by command E2 and read by nothing in the stage programs. */
    public int commFlag() {
        return commFlag;
    }

    /** {@code UpdateDriver} (:40-57). */
    public void tick() {
        procSoundQueue();
        playSoundId();
        // HandlePause: while paused the driver returns before tempo, fade and tracks, and
        // before the closing on/off write (:821-846).
        if (pauseMode != 0) {
            if ((pauseMode & 0x80) != 0) {
                pauseMode = 0;
            } else {
                if (pauseMode != 2) {
                    reg(REG_ONOFF, 0xFF);
                    pauseMode = 2;
                }
                return;
            }
        }
        handleTempo();
        handleFadeOut();
        updateTracks();
        reg(REG_ONOFF, on);
    }

    // ---- queue and dispatch ------------------------------------------------------------------

    /** {@code ProcSoundQueue} (:503-575). */
    private void procSoundQueue() {
        if ((queue[0] | queue[1] | queue[2] | queue[3]) == 0) return;
        int d3 = sfxPrio;
        for (int slot = 0; slot < 4; slot++) {
            int id = queue[slot];
            queue[slot] = 0;
            int table;
            int index;
            if (id >= SONG_START && id <= SONG_END) { table = songPriorities; index = id - SONG_START; }
            else if (id >= SFX_START && id <= SFX_END) { table = sfxPriorities; index = id - SFX_START; }
            else if (id >= CMD_START && id <= CMD_END) { table = cmdPriorities; index = id - CMD_START; }
            else continue;
            int priority = u8(table + index);
            if (priority < d3) continue;
            d3 = priority;
            sndPlay = id;
        }
        if ((d3 & 0x80) == 0) sfxPrio = d3;
    }

    /** {@code PlaySoundID} (:577-613). */
    private void playSoundId() {
        int id = sndPlay;
        if (id == 0) {
            initDriver();
            return;
        }
        if ((id & 0x80) == 0) {
            stopCommand();
            return;
        }
        sndPlay = 0x80;
        if (id >= SONG_START && id <= SONG_END) playSong(id);
        else if (id >= SFX_START && id <= SFX_END) playSfx(id);
        else if (id >= CMD_START && id <= CMD_END) runCommand(id);
    }

    /** {@code InitDriver} (:1058-1077): mute, bank 0, then {@code Cmd_Stop}. */
    private void initDriver() {
        reg(REG_ONOFF, 0xFF);
        reg(REG_CTRL, 0x80);
        stopCommand();
    }

    /** {@code Cmd_Stop} (:1023-1033): reset, clear wave RAM to loop markers, preload samples. */
    private void stopCommand() {
        resetDriver();
        for (int bankId = 0; bankId < 16; bankId++) {
            reg(REG_CTRL, 0x80 | bankId);
            chip.fillBankAtBoundary(bankId, 0xFF);
        }
        loadSamples();
    }

    /** {@code ResetDriver} (:993-1021). */
    private void resetDriver() {
        reg(REG_ONOFF, 0xFF);
        tempo = tempoCnt = sfxPrio = commFlag = cddaLoop = sfxMode = pauseMode = 0;
        fadeSteps = fadeSpeed = fadeDelay = fadeCnt = unkFadeVol = 0;
        Arrays.fill(queue, 0);
        for (Track t : song) t.clear();
        for (Track t : sfx) t.clear();
        on = 0xFF;
        sndPlay = 0x80;
    }

    /**
     * {@code LoadSamples} (:437-501): static samples (mode non-zero) are copied to their fixed
     * wave-RAM address, bank by bank, with the routine's word arithmetic: the control byte is
     * the destination rotated left by four with bit 7 set, so destination bits 8-11 land in it.
     */
    private void loadSamples() {
        int count = longAt(sampleIndex);
        if (count <= 0) return;
        for (int i = 0; i < count; i++) {
            int meta = longAt(sampleIndex + 4 + i * 4);
            if (u8(meta + 13) == 0) continue;
            int src = longAt(meta);
            int dest = word(meta + 14);
            int control = ((((dest << 4) | (dest >>> 12)) & 0xFF) | 0x80);
            int offset = dest & 0xF00;
            int size = longAt(meta + 4) & 0xFFFF;
            int banks = (((size << 4) | (size >>> 12)) & 0xF);
            for (int b = 0; b <= banks; b++) {
                reg(REG_CTRL, control);
                int length = Integer.compareUnsigned(size, 0x1000) <= 0 ? size : 0x1000;
                size = (size + offset) & 0xFFFF;
                int copies = ((length - offset - 1) & 0xFFFF) + 1;
                for (int k = 0; k < copies; k++) {
                    int chipOffset = offset + k;
                    if (chipOffset < 0x1000) chip.writeAtBoundary(WAVE + chipOffset, u8(src));
                    src++;
                }
                size = (size - 0x1000) & 0xFFFF;
                control = (control + 1) & 0xFF;
                offset = 0;
            }
        }
    }

    /** {@code PlaySong} (:615-672). */
    private void playSong(int id) {
        resetDriver();
        int header = longAt(songIndex + ((id - SONG_START) & 0x7F) * 4);
        int tracks = u8(header + 2);
        int tickMult = u8(header + 4);
        tempo = tempoCnt = u8(header + 5);
        int entry = header + 6;
        for (int i = 0; i < tracks; i++, entry += 4) {
            Track t = song[i];
            t.dataPtr = header + word(entry);
            t.transpose = u8(entry + 2);
            t.volume = u8(entry + 3);
            t.channel = SONG_CHANNELS[i];
            initChannel(t);
            t.tickMult = tickMult;
            t.stackPtr = 0x80;
            t.flags = FLAG_PLAY;
            t.durCnt = 1;
        }
        song[0].flags = 0;                                 // rhythm track disabled
        on = 0xFF;
    }

    /** {@code PlaySFX} (:695-770); the track slot is the header's channel. */
    private void playSfx(int id) {
        int header = longAt(sfxIndex + ((id - SFX_START) & 0x7F) * 4);
        int tracks = u8(header + 3);
        int tickMult = u8(header + 2);
        int entry = header + 4;
        for (int i = 0; i < tracks; i++, entry += 6) {
            Track t = sfx[u8(entry + 1) & 7];
            t.clear();
            t.flags = u8(entry);
            t.channel = u8(entry + 1);
            t.dataPtr = header + word(entry + 2);
            t.transpose = u8(entry + 4);
            t.volume = u8(entry + 5);
            initChannel(t);
            t.tickMult = tickMult;
            t.stackPtr = 0x80;
            t.durCnt = 1;
            t.staccato = 0;
            t.detune = 0;
        }
    }

    /** The channel set-up both loaders share: own 8 KiB region, loop to its start, centre pan. */
    private void initChannel(Track t) {
        reg(REG_CTRL, 0xC0 | (t.channel & 7));
        int page = ((0xC0 | t.channel) << 5) & 0xFF;
        reg(REG_ST, page);
        reg(REG_LSH, page);
        reg(REG_LSL, 0);
        reg(REG_PAN, 0xFF);
        reg(REG_ENV, t.volume);
    }

    /** {@code RunCommand} (:924-991). */
    private void runCommand(int id) {
        switch (id - CMD_START) {
            case 0 -> { fadeSteps = 0x60; fadeDelay = 1; fadeSpeed = 2; }
            case 1 -> stopCommand();
            case 2 -> pauseMode = 1;
            case 3 -> pauseMode = 0x80;
            default -> reg(REG_ONOFF, 0xFF);
        }
    }

    /** {@code HandleFadeOut} (:772-819). */
    private void handleFadeOut() {
        if (fadeSteps == 0) return;
        if (fadeCnt != 0) {
            fadeCnt--;
            return;
        }
        fadeSteps--;
        if (fadeSteps == 0) {
            resetDriver();
            return;
        }
        fadeCnt = fadeDelay;
        unkFadeVol = (unkFadeVol + fadeSpeed) & 0xFF;
        for (Track t : song) {
            if ((t.flags & FLAG_PLAY) == 0) continue;
            int volume = t.volume - fadeSpeed;
            if (volume < 0) {
                volume = 0;
                t.flags &= ~FLAG_PLAY;
            }
            t.volume = volume;
            reg(REG_CTRL, 0xC0 | (t.channel & 7));
            reg(REG_ENV, t.volume);
        }
    }

    /** {@code HandleTempo} (:848-874): every tempo-th tick delays the song tracks by one. */
    private void handleTempo() {
        if (tempo == 0) return;
        tempoCnt = (tempoCnt - 1) & 0xFF;
        if (tempoCnt != 0) return;
        tempoCnt = tempo;
        for (Track t : song) t.durCnt = (t.durCnt + 1) & 0xFF;
    }

    // ---- tracks ------------------------------------------------------------------------------

    /** {@code UpdateTracks} (:62-87): PCM1..8, then the effect tracks; the rhythm slot is skipped. */
    private void updateTracks() {
        sfxMode = 0;
        for (int i = 1; i < TRACK_COUNT; i++) {
            if ((song[i].flags & FLAG_PLAY) != 0 && !updateTrack(song[i])) return;
        }
        sfxMode = 0x80;
        for (Track t : sfx) {
            if ((t.flags & FLAG_PLAY) != 0 && !updateTrack(t)) return;
        }
    }

    /**
     * {@code UpdateTrack} (:101-127). Returns false when {@code HandleStaccato}'s stack drop has
     * left the walk (the shipped bug, :241-252).
     */
    private boolean updateTrack(Track t) {
        t.durCnt = (t.durCnt - 1) & 0xFF;
        if (t.durCnt != 0) {
            updateSample(t);
            return handleStaccato(t);
        }
        t.flags &= ~FLAG_LEGATO;
        if (!parseTrackData(t)) return true;               // TrkCmd_Stop: next track
        startStream(t);
        if ((t.flags & FLAG_REST) != 0) return true;       // UpdateFreq skips NoteOn
        updateFreq(t);
        if ((t.flags & FLAG_LEGATO) == 0) on &= ~(1 << t.channel);
        return true;
    }

    /** {@code HandleStaccato} (:235-259). */
    private boolean handleStaccato(Track t) {
        if (t.stacCnt == 0) return true;
        t.stacCnt = (t.stacCnt - 1) & 0xFF;
        if (t.stacCnt != 0) return true;
        restTrack(t);
        return false;
    }

    /** {@code ParseTrackData} (:129-161); false when the track stopped. */
    private boolean parseTrackData(Track t) {
        int p = t.dataPtr;
        t.flags &= ~FLAG_REST;
        while (true) {
            int b = u8(p++);
            if (b >= 0xE0) {
                int[] cursor = {p};
                if (!trackCommand(t, b, cursor)) return false;
                p = cursor[0];
                continue;
            }
            if (b >= 0x80) {
                getFreq(t, b);
                int next = u8(p);
                if (next < 0x80) {
                    p++;
                    calcDuration(t, next);
                }
            } else {
                calcDuration(t, b);
            }
            finishTrackParse(t, p);
            return true;
        }
    }

    /** {@code GetFreq} (:163-181). */
    private void getFreq(Track t, int note) {
        int n = (note - 0x80) & 0xFF;
        if (n == 0) {
            restTrack(t);
            return;
        }
        n = ((n + t.transpose) & 0xFF) & 0x7F;
        t.freq = word(freqTable() + n * 2);
    }

    /** {@code CalcDuration} (:212-233): byte multiply by repeated addition. */
    private static void calcDuration(Track t, int base) {
        int d = base;
        for (int m = t.tickMult - 1; m > 0; m--) d = (d + base) & 0xFF;
        if (t.tickMult == 0) for (int m = 255; m > 0; m--) d = (d + base) & 0xFF;
        t.duration = d;
        t.durCnt = d;
    }

    /** {@code FinishTrackParse} (:183-210). */
    private void finishTrackParse(Track t, int p) {
        t.dataPtr = p;
        t.durCnt = t.duration;
        if ((t.flags & FLAG_LEGATO) != 0) return;
        on |= 1 << t.channel;                              // NoteOff writes the byte at once
        reg(REG_ONOFF, on);
        t.stacCnt = t.staccato;
        t.samplePtr = t.sampleStart;
        t.sampRemain = t.sampleSize;
        t.sampStacCnt = t.sampStac;
        t.sampleRam = 0;
        t.prevSampPos = 0;
        t.sampRamOff = 0;
        t.sampleBank = 0;
        t.sampleBlks = 7;
    }

    private void restTrack(Track t) {
        on |= 1 << t.channel;
        t.flags |= FLAG_REST;
    }

    /** {@code UpdateFreq} (:261-289). */
    private void updateFreq(Track t) {
        int f = (t.freq + (byte) t.detune) & 0xFFFF;
        reg(REG_CTRL, 0xC0 | (t.channel & 7));
        reg(REG_FDL, f & 0xFF);
        reg(REG_FDH, f >>> 8);
    }

    /** {@code StartStream} (:291-307). */
    private void startStream(Track t) {
        if (t.sampleMode != 0 || (t.flags & FLAG_REST) != 0) return;
        streamSample(t);
    }

    /** {@code UpdateSample} (:309-354): refill when the play address passes the filled block. */
    private void updateSample(Track t) {
        if (t.sampStacCnt != 0) {
            t.sampStacCnt = (t.sampStacCnt - 1) & 0xFF;
            if (t.sampStacCnt == 0) {
                restTrack(t);
                return;
            }
        }
        if (t.sampleMode != 0 || (t.flags & FLAG_REST) != 0) return;
        int ch = t.channel & 7;
        int position = chip.readAtBoundary(0x10 + ch * 2) | (chip.readAtBoundary(0x11 + ch * 2) << 8);
        int previous = t.prevSampPos;
        t.prevSampPos = position;
        if (position < previous) t.sampRamOff = (t.sampRamOff - 0x1E00) & 0xFFFF;
        int playing = ((position & 0x1FFF) + 0x1000) & 0xFFFF;
        if (playing > t.sampRamOff) streamSample(t);
    }

    /** {@code StreamSample} (:356-435): one 200h-byte block, looping the sample inside it. */
    private void streamSample(Track t) {
        t.sampRamOff = (t.sampRamOff + 0x200) & 0xFFFF;
        int remaining = t.sampRemain;
        int src = t.samplePtr;
        int ram = t.sampleRam;
        reg(REG_CTRL, 0x80 | ((((t.channel & 0x7F) << 1) + t.sampleBank) & 0x7F));
        int block = 0x200;
        int left = 0x200;
        int loopLength = t.sampleSize - t.sampleLoop;
        while (true) {
            if (remaining < block) block = remaining;
            remaining -= block;
            left -= block;
            for (int k = 0; k < block; k++) {
                chip.writeAtBoundary(WAVE + (ram & 0xFFF), u8(src++));
                ram++;
            }
            if (left == 0) break;
            src -= loopLength;
            remaining += loopLength;
            block = left;
        }
        if (remaining == 0) {
            src -= loopLength;
            remaining = loopLength;
        }
        t.sampRemain = remaining;
        t.samplePtr = src;
        t.sampleBlks = (t.sampleBlks - 1) & 0xFF;
        if ((t.sampleBlks & 0x80) == 0) {
            t.sampleRam = ram;
            return;
        }
        t.sampleRam = 0;
        if (t.sampleBank == 0) {
            t.sampleBlks = 7 - 1;
            t.sampleBank = 1;
        } else {
            t.sampleBlks = 8 - 1;
            t.sampleBank = 0;
        }
    }

    // ---- commands ----------------------------------------------------------------------------

    /** {@code TrackCommand} (:1112-1153); false for the stop commands. */
    private boolean trackCommand(Track t, int cmd, int[] cursor) {
        int p = cursor[0];
        switch (cmd) {
            case 0xE0 -> {                                 // TrkCmd_Panning
                reg(REG_CTRL, 0xC0 | (t.channel & 7));
                t.panning = u8(p);
                reg(REG_PAN, u8(p++));
            }
            case 0xE1 -> t.detune = u8(p++);               // TrkCmd_Detune
            case 0xE2 -> commFlag = u8(p++);               // TrkCmd_CommFlag
            case 0xE3 -> cddaLoop = 1;                     // TrkCmd_SetCDDALoop
            case 0xE6 -> p = volume(t, p);                 // TrkCmd_Volume
            case 0xE7 -> t.flags |= FLAG_LEGATO;           // TrkCmd_Legato
            case 0xE8 -> { t.stacCnt = u8(p); t.staccato = u8(p++); }
            case 0xEA -> { tempoCnt = u8(p); tempo = u8(p++); }
            case 0xEB -> queue[0] = u8(p++);               // TrkCmd_PlaySound
            case 0xEF -> instrument(t, u8(p++));
            case 0xF0, 0xF1, 0xF2 -> {                     // TrkCmd_Stop
                t.flags &= ~(FLAG_PLAY | FLAG_LEGATO);
                restTrack(t);
                if (sfxMode != 0) sfxPrio = 0;
                return false;
            }
            case 0xF4, 0xF6 -> p = jump(p);
            case 0xF7 -> {                                 // TrkCmd_Repeat
                int index = u8(p++);
                int count = u8(p++);
                int offset = 0x40 + index;
                if (t.highByte(offset) == 0) t.setHighByte(offset, count);
                t.setHighByte(offset, (t.highByte(offset) - 1) & 0xFF);
                p = t.highByte(offset) != 0 ? jump(p) : p + 2;
            }
            case 0xF8 -> {                                 // TrkCmd_Call
                t.stackPtr = (t.stackPtr - 4) & 0xFF;
                for (int k = 0; k < 4; k++) t.setHighByte(t.stackPtr + k, p >>> (24 - 8 * k));
                p = jump(p);
            }
            case 0xF9 -> {                                 // TrkCmd_Return
                int ret = 0;
                for (int k = 0; k < 4; k++) ret = (ret << 8) | t.highByte(t.stackPtr + k);
                p = ret + 2;
                t.stackPtr = (t.stackPtr + 4) & 0xFF;
            }
            case 0xFA -> t.tickMult = u8(p++);
            case 0xFB -> t.transpose = (t.transpose + u8(p++)) & 0xFF;
            case 0xFC -> {                                 // TrkCmd_GlobalTickMult
                int m = u8(p++);
                for (Track s : song) s.tickMult = m;
            }
            case 0xFE, 0xFF -> throw new IllegalStateException(
                    String.format("PCM command %02X at %05X runs into DriverInfo (:1497-1501)", cmd, p - 1));
            default -> { }                                 // TrkCmd_Null: no operand
        }
        cursor[0] = p;
        return true;
    }

    /** {@code TrkCmd_Jump}: signed big-endian offset from its own first byte plus one (:1384-1398). */
    private int jump(int p) {
        return p + 1 + (short) word(p);
    }

    /** {@code TrkCmd_Volume} (:1225-1260): byte add; a wrap only stops the track while fading. */
    private int volume(Track t, int p) {
        reg(REG_CTRL, 0xC0 | (t.channel & 7));
        int delta = (byte) u8(p++);
        int sum = t.volume + (delta & 0xFF);
        boolean carry = sum > 0xFF;
        t.volume = sum & 0xFF;
        boolean cap = delta >= 0 ? carry : !carry;
        if (!cap) {
            reg(REG_ENV, t.volume);
        } else if (fadeSteps != 0) {
            t.flags &= ~FLAG_PLAY;
            reg(REG_ENV, 0);
        }
        return p;
    }

    /** {@code TrkCmd_Instrument} (:1313-1362). */
    private void instrument(Track t, int id) {
        int meta = longAt(sampleIndex + 4 + id * 4);
        t.sampStac = u8(meta + 12);
        t.sampStacCnt = u8(meta + 12);
        t.sampleMode = u8(meta + 13);
        if (t.sampleMode == 0) {
            int src = longAt(meta);
            t.sampleStart = src;
            t.samplePtr = src;
            t.sampleSize = longAt(meta + 4);
            t.sampRemain = t.sampleSize;
            t.sampleLoop = longAt(meta + 8);
            t.sampleRam = 0;
            t.sampleBank = 0;
            t.sampleBlks = 8 - 1;
            return;
        }
        reg(REG_CTRL, 0xC0 | (t.channel & 7));
        int dest = word(meta + 14);
        reg(REG_ST, dest >>> 8);
        int loop = (longAt(meta + 8) + dest) & 0xFFFF;
        reg(REG_LSL, loop & 0xFF);
        reg(REG_LSH, loop >>> 8);
    }

    // ---- state -------------------------------------------------------------------------------

    public State capture() {
        int[] vars = {tempo, tempoCnt, on, sfxPrio, commFlag, cddaLoop, sndPlay, sfxMode, pauseMode,
                fadeSteps, fadeSpeed, fadeDelay, fadeCnt, unkFadeVol, queue[0], queue[1], queue[2], queue[3]};
        Track[] s = new Track[TRACK_COUNT];
        Track[] f = new Track[SFX_TRACK_COUNT];
        for (int i = 0; i < TRACK_COUNT; i++) s[i] = song[i].copy();
        for (int i = 0; i < SFX_TRACK_COUNT; i++) f[i] = sfx[i].copy();
        return new State(vars, s, f);
    }

    public void restore(State state) {
        int[] v = state.vars();
        tempo = v[0]; tempoCnt = v[1]; on = v[2]; sfxPrio = v[3]; commFlag = v[4]; cddaLoop = v[5];
        sndPlay = v[6]; sfxMode = v[7]; pauseMode = v[8]; fadeSteps = v[9]; fadeSpeed = v[10];
        fadeDelay = v[11]; fadeCnt = v[12]; unkFadeVol = v[13];
        System.arraycopy(v, 14, queue, 0, 4);
        for (int i = 0; i < TRACK_COUNT; i++) song[i] = state.song()[i].copy();
        for (int i = 0; i < SFX_TRACK_COUNT; i++) sfx[i] = state.sfx()[i].copy();
    }

    /**
     * Chip channels an effect owns now: bit c is set while an effect track that is playing sits on
     * channel c. {@code PlaySFX} (PCM/_Driver.asm:695-770) sets an effect track up on the channel its
     * header names, the one a song track may be using, without silencing that song track, and
     * {@code UpdateTracks} (:62-87) runs every effect track after the song tracks, so the effect's
     * register writes are the last each tick; the effect gives the channel back at its
     * {@code TrkCmd_Stop}, which clears its play flag and rests the track, keying the channel off (:1364-1375).
     * A presentation split, derived from driver state: it holds no state of its own.
     */
    public int sfxChannelMask() {
        int mask = 0;
        for (Track t : sfx) {
            if ((t.flags & FLAG_PLAY) != 0) mask |= 1 << (t.channel & 7);
        }
        return mask;
    }

    /** Read-only views for native comparison. */
    Track songTrack(int index) { return song[index]; }
    Track sfxTrack(int index) { return sfx[index]; }
    int sndPlay() { return sndPlay; }
    int onMask() { return on; }

    // ---- bank access -------------------------------------------------------------------------

    private void reg(int register, int value) {
        chip.writeAtBoundary(register, value & 0xFF);
    }

    int u8(int address) {
        return bank[address - BANK_ADDRESS] & 0xFF;
    }

    int word(int address) {
        return (u8(address) << 8) | u8(address + 1);
    }

    int longAt(int address) {
        return (word(address) << 16) | word(address + 2);
    }

    private int freqTable = -1;

    /** {@code FreqTable} (:1089-1111) follows {@code GetPointers}' closing RTS: 0000 (rest), 0104, 0113, 0124. */
    private int freqTable() {
        if (freqTable < 0) {
            for (int a = BANK_ADDRESS + 0x10; a + 10 <= BANK_ADDRESS + bank.length; a += 2) {
                if (word(a) == 0x4E75 && word(a + 2) == 0x0000 && word(a + 4) == 0x0104
                        && word(a + 6) == 0x0113 && word(a + 8) == 0x0124) {
                    freqTable = a + 2;
                    break;
                }
            }
            if (freqTable < 0) throw new IllegalStateException("PCM bank has no FreqTable");
        }
        return freqTable;
    }

    /** {@code DriverInfo} (:1503-1514): two of its entries are {@code dc.l *} and one is the origin. */
    private int findDriverInfo() {
        for (int a = BANK_ADDRESS + 0x10; a + 40 <= BANK_ADDRESS + bank.length; a += 2) {
            if (longAt(a + 4) == a + 4 && longAt(a + 16) == a + 16 && longAt(a + 20) == a + 20
                    && longAt(a + 24) == SFX_START && longAt(a + 28) == ORIGIN) {
                return a;
            }
        }
        throw new IllegalStateException("PCM bank has no DriverInfo");
    }

    /** {@code SampleIndex}: the operand of TrkCmd_Instrument's {@code lea SampleIndex(pc),a0; addq.w #4,a0}. */
    private int findSampleIndex() {
        for (int a = BANK_ADDRESS + 0x10; a + 6 <= BANK_ADDRESS + bank.length; a += 2) {
            if (word(a) == 0x41FA && word(a + 4) == 0x5848) {
                return a + 2 + (short) word(a + 2);
            }
        }
        throw new IllegalStateException("PCM bank has no SampleIndex reference");
    }
}
