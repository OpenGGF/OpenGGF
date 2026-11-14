package com.openggf.game.soniccd.fmv;

import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.device.pcm.CdPcmDevice;

import java.util.Objects;

/**
 * The stream side of a CD video, as the rules the game's code implements, stepped once per V-int. What each part is:
 *
 * <ul>
 *   <li><b>Data rate (algorithm input).</b> Sectors arrive at the single-speed rate, 75 a second (the rate the FMV
 *       read counts in, wrapping {@code feFMVSectFrame} at 75, CD System Program/SP.asm:862-865); a V-int is 896,040 of
 *       the NTSC master clock's 53,693,175 Hz, so a packet takes 59.92 V-ints. {@link #advanceVint()} delivers what has
 *       arrived. Before the first sector the drive seeks: {@link #SEEK_VINTS}, an unsupported hardware timing.</li>
 *   <li><b>Packet layout (game algorithm).</b> Sectors 0-15 of each 75 fill the PCM buffer, 16-74 the next video bank
 *       ({@code ReadFMVSectors}, SP.asm:823-858).
 *       The mute load fills five-sector banks, all graphics (SP.asm:1100-1130).</li>
 *   <li><b>Bank rule (game algorithm).</b> A full bank is shown once the main program has taken the one before it: the
 *       engine holds a full bank until the main program asks, then goes on with the next packet ({@code .GfxDone},
 *       SP.asm:836-858; the main programs' {@code WaitWordRAMSwap}, FMV/Main (Opening).asm:572-596). So bank n is ready
 *       when the main program has taken bank n-1 and bank n's sectors have arrived ({@link #bankReady()},
 *       {@link #takeBank()}).</li>
 *   <li><b>PCM rule (game algorithm).</b> A packet's PCM is copied after its bank is taken, into the half of wave RAM
 *       the chip is not playing: {@code SyncWithPCM} (Sub (Opening).asm:220-237) reads the chip's play address
 *       ({@code PCMADDR1H}) and waits until it has left that half. That is the game's own rule, so it is evaluated where
 *       the play address changes: between chip samples. The copy takes {@code FMVPCMBUF} as it then is (by then the
 *       next packet's PCM), $FFF bytes per 4 KiB bank, leaving each bank's last byte and the packet's last 8 bytes
 *       unused: the shipped gaps (Sub (Opening).asm:46-75).</li>
 *   <li><b>End (game algorithm).</b> The video ends when the whole file has been read and no copy is pending, or when
 *       the main program asks it to stop; the chip is muted and stopped (Sub (Opening).asm:77-108).</li>
 *   <li><b>Chip (sound-chip synthesis).</b> The existing {@link CdPcmDevice}, stepped by this owner at its own clock
 *       (12.5 MHz / 384 per sample, 543.24 samples per V-int) through {@link CdSubCpuAudio.ProgramPcm}.</li>
 * </ul>
 * Not modelled: interrupts and the two CPUs' flag handshake (only the rules above), drive seek variation, CDC
 * buffering and read retries, and the copy loop's own duration.
 */
public final class CdFmvStreamer {
    /** Mega Drive NTSC master clock and master clocks per V-int (3420 x 262). */
    public static final long MASTER_CLOCK = 53_693_175L, MASTER_PER_VINT = 896_040L;
    public static final int SECTORS_PER_SECOND = 75;
    /**
     * <b>The drive's seek</b>: V-ints before sectors start to arrive; the first whole sector lands one V-int later
     * ({@link #arrived()}: 75 sectors a second, so the bank of a 75-sector packet is shown 60 V-ints after the seek).
     * No code on the disc sets it: the BIOS read ({@code ROMREADN}) and the drive decide. In normal play with a
     * load-time manifest it is the emulator-measured read readiness ({@link #seekFor}); this constant, 20, is the
     * documented fallback for {@code NONE} and for a stream with no manifest entry. Native, from {@code ROMREADN}
     * to the first sector: 16 V-ints for all three streams (load_time_census.py), so the first bank comes 75
     * after the request; from the program's start natively 77 (opening) and 81 (endings), the difference being
     * {@code FileEngine_LoadFMV}'s {@code .GetSectors} loop before the request (SP.asm:691-699), CPU time the
     * engine does not model. Tolerance: any seek of 12-46 V-ints keeps every copy whole on every stream (47 tears
     * the first copies, 11 loses the 248-packet ending's last copy) and leaves every picture unchanged relative to
     * its bank. What moves with it, one frame for one: when the first bank is shown, where in the sound each bank
     * starts (native 17-21), and when the video ends after its last bank (the end waits for the last copy).
     */
    public static final int SEEK_VINTS = 20;
    private static final long CHIP_PER_VINT_NUM = 12_500_000L * MASTER_PER_VINT, CHIP_PER_VINT_DEN = 384L * MASTER_CLOCK;
    private static final long SECTOR_NUM = SECTORS_PER_SECOND * MASTER_PER_VINT;

    /**
     * The seek for a stream read in normal play: the read submitted to {@code timing} as {@code CD_READ_READY}
     * work ({@link com.openggf.game.soniccd.CdLoadTiming#videoRead}) is ready after the V-INTs the profile measured
     * from {@code ROMREADN} to the first sector; the first sector lands one V-int after the seek, so the seek is one
     * less. Immediate readiness ({@code NONE}, no manifest entry) or no timing service: {@link #SEEK_VINTS}.
     */
    public static int seekFor(com.openggf.game.timing.HardwareTimingService timing,
                              com.openggf.data.cd.Iso9660FileSystem.Entry stream) {
        if (timing == null || stream == null) return SEEK_VINTS;
        int vints = com.openggf.game.soniccd.CdLoadTiming.vintsUntilReady(timing,
                com.openggf.game.soniccd.CdLoadTiming.videoRead(stream));
        return vints == 0 ? SEEK_VINTS : vints - 1;
    }

    /** One PCM copy: the V-int it happened in, the half it filled, and whose PCM it took (see {@link #copies()}). */
    public record Copy(long vint, int half, int packet, int sectorsOfPacket) { }

    private final CdFmvStream stream;
    private final CdFmvStream.Layout layout;
    private final CdSubCpuAudio.ProgramPcm pcm;
    private final int seek;
    private final byte[] pcmBuffer = new byte[0x8000];   // FMVPCMBUF
    private byte[] filling = new byte[0x20000];          // the bank the engine fills
    private byte[] shown = new byte[0x20000];            // the bank the main program reads
    private long vints;
    private int sectorsRead;
    private boolean bankFull;
    private boolean copyPending;
    private boolean secondHalf;                           // FMVF_PBUF: the half the next copy fills
    private boolean readDone;
    private boolean finished;
    private boolean stopRequested;
    private long chipAccumulator;
    private int banksTaken;
    private final java.util.List<Copy> copyLog = new java.util.ArrayList<>();

    /**
     * Start: {@code InitPCMWave}, {@code InitPCMRegs} with the file's 14 register bytes ({@code .RegValues} and the 7
     * bytes after it, which become PCM2's), {@code PCMONOFF %11111100}, then the stream's load (Sub (Opening).asm:17-33).
     * A mute stream ({@code pcm} null) touches no PCM.
     */
    public CdFmvStreamer(CdFmvStream stream, CdFmvStream.Layout layout, CdSubCpuAudio.ProgramPcm pcm, byte[] regValues) {
        this(stream, layout, pcm, regValues, SEEK_VINTS);
    }

    /** As above with another seek (tests of the tolerance stated at {@link #SEEK_VINTS}). */
    public CdFmvStreamer(CdFmvStream stream, CdFmvStream.Layout layout, CdSubCpuAudio.ProgramPcm pcm, byte[] regValues, int seek) {
        this.stream = Objects.requireNonNull(stream, "stream");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.seek = seek;
        this.pcm = layout == CdFmvStream.Layout.SOUND ? Objects.requireNonNull(pcm, "pcm") : null;
        if (this.pcm != null) initPcm(this.pcm.chip(), Objects.requireNonNull(regValues, "regValues"));
    }

    private static void initPcm(CdPcmDevice chip, byte[] values) {
        // InitPCMWave (Sub (Opening).asm:133-165): mute, clear all 16 banks, loop flag at the last byte.
        chip.writeAtBoundary(8, 0xFF);
        chip.writeAtBoundary(7, 0x80);
        for (int bank = 0; bank < 16; bank++) {
            chip.writeAtBoundary(7, 0x80 | bank);
            chip.fillBankAtBoundary(bank, 0);
        }
        chip.writeAtBoundary(0x1000 + 0xFFF, 0xFF);        // move.b #$FF,PCMWAVE+($FFF*2) with bank 15 selected
        chip.writeAtBoundary(7, 0x80);                     // move.b #$80,PCMCTRL
        // InitPCMRegs (:171-205): PCM1 then PCM2, ENV, PAN, FD low/high, LS low/high, ST.
        for (int channel = 0; channel < 2; channel++) {
            chip.writeAtBoundary(7, 0xC0 | channel);
            for (int r = 0; r < 7; r++) chip.writeAtBoundary(r, values[channel * 7 + r] & 0xFF);
        }
        chip.writeAtBoundary(8, 0xFC);                     // move.b #%11111100,PCMONOFF
    }

    /** One V-int of disc time: the sectors that have arrived fill the PCM buffer and the bank being filled. */
    public void advanceVint() {
        vints++;
        if (!finished && !bankFull) fill();
    }

    private long arrived() {
        long elapsed = vints - 1 - seek;
        return elapsed < 0 ? 0 : elapsed * SECTOR_NUM / MASTER_CLOCK;
    }

    private void fill() {
        long arrived = Math.min(arrived(), stream.sectors());
        while (sectorsRead < arrived && !bankFull) {
            int s = sectorsRead % layout.packetSectors;
            byte[] data = stream.sector(sectorsRead);
            if (s < layout.pcmSectors) System.arraycopy(data, 0, pcmBuffer, s * CdFmvStream.SECTOR_BYTES, CdFmvStream.SECTOR_BYTES);
            else System.arraycopy(data, 0, filling, (s - layout.pcmSectors) * CdFmvStream.SECTOR_BYTES, CdFmvStream.SECTOR_BYTES);
            sectorsRead++;
            if (s == layout.packetSectors - 1) bankFull = true;                   // the bank is held for the main program
            else if (sectorsRead == stream.sectors()) readDone = true;            // the load is over (FSTAT_OK)
        }
    }

    /** A full bank is ready for the main program. */
    public boolean bankReady() { return bankFull && !finished; }

    /** The main program takes the ready bank; its packet's PCM is now due, and the next packet's sectors fill on. */
    public void takeBank() {
        if (!bankFull) throw new IllegalStateException("No bank is ready");
        byte[] t = shown;
        shown = filling;
        filling = t;
        bankFull = false;
        banksTaken++;
        if (layout == CdFmvStream.Layout.SOUND) copyPending = true;
        if (sectorsRead == stream.sectors()) readDone = true;
        else fill();
    }

    /** The main program asks the video to stop (FMV/Main (Opening).asm:488-492). */
    public void requestStop() { stopRequested = true; }

    public boolean stopRequested() { return stopRequested; }

    /** The video has ended (read complete and no copy pending, or stopped). */
    public boolean finished() { return finished; }

    /** The bank the main program reads. */
    public byte[] shownBank() { return shown; }

    public int banksTaken() { return banksTaken; }

    public java.util.List<Copy> copies() { return java.util.List.copyOf(copyLog); }

    /** The rest of the V-int: the chip plays its samples; a pending copy happens at the sample its rule allows. */
    public void playVint() {
        if (pcm == null) {
            settle();
            return;
        }
        chipAccumulator += CHIP_PER_VINT_NUM;
        long samples = chipAccumulator / CHIP_PER_VINT_DEN;
        chipAccumulator %= CHIP_PER_VINT_DEN;
        CdPcmDevice chip = pcm.chip();
        for (long i = 0; i < samples; i++) {
            if (!finished) settle();
            pcm.sample(chip.step());
        }
    }

    /** The PCM rule, then the end rule, at this instant. */
    private void settle() {
        if (finished) return;
        if (stopRequested) { end(); return; }
        if (copyPending) {
            int high = pcm.chip().readAtBoundary(0x11);    // PCMADDR1H, as SyncWithPCM reads it
            boolean free = secondHalf ? high <= 0x7F : high > 0x7F;
            if (!free) return;
            int inPacket = sectorsRead % layout.packetSectors;
            boolean between = inPacket == 0 && sectorsRead > 0;
            copyLog.add(new Copy(vints, secondHalf ? 2 : 1, sectorsRead / layout.packetSectors - (between ? 1 : 0),
                    between ? layout.pcmSectors : Math.min(inPacket, layout.pcmSectors)));
            copyPcm(pcm.chip(), secondHalf ? 0x88 : 0x80);
            secondHalf = !secondHalf;
            copyPending = false;
        }
        if (readDone && !bankFull) end();
    }

    /** {@code .StreamPCM}: eight banks of $FFF bytes from {@code FMVPCMBUF} (Sub (Opening).asm:46-75). */
    private void copyPcm(CdPcmDevice chip, int firstBank) {
        int from = 0;
        for (int bank = 0; bank < 8; bank++) {
            chip.writeAtBoundary(7, firstBank + bank);
            for (int i = 0; i < 0xFFF; i++) chip.writeAtBoundary(0x1000 + i, pcmBuffer[from++] & 0xFF);
        }
    }

    /** {@code .Exit}: mute all channels and stop the chip (Sub (Opening).asm:104-106). */
    private void end() {
        if (pcm != null) {
            pcm.chip().writeAtBoundary(8, 0xFF);
            pcm.chip().writeAtBoundary(7, 0x00);
        }
        finished = true;
    }

    /** V-ints since the program started. */
    public long vints() { return vints; }
}
