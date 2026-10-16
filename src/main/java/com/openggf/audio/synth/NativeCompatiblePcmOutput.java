package com.openggf.audio.synth;

import com.openggf.audio.synth.blip.BlipBuf;

import java.util.Arrays;
import java.util.Objects;

/**
 * Opt-in, capture/diagnostic-only output stage that turns the production
 * {@link Ym2612Chip}'s own pin sums into PCM the way the BizHawk Genesis Plus
 * GX core does for a movie configured with the Nuked YM2612 FM core. It never
 * feeds the ordinary presentation mix; ordinary sound and the default 48 kHz
 * presentation are unchanged whether or not it is enabled.
 *
 * <p><b>What it models.</b> The behaviour below is written from the
 * documented arithmetic of the Genesis Plus GX sources inside BizHawk
 * {@code 427556b5ef3a} (GPGX {@code 051d430d}); no text of {@code sound.c} or
 * {@code system.c} is copied (they carry a different licence). Citations are
 * to those files:
 * <ul>
 *   <li>FM sample: {@code YM3438_Update} holds the sum of the 24 pin values of
 *       the last complete slot sweep and outputs it times 11 on every
 *       internal clock ({@code sound.c:184-204}); {@code sound_update}
 *       multiplies by {@code fm_preamp / 100} (BizHawk sets 100,
 *       {@code cinterface.c:845}) and adds the change from the previous clock
 *       as a band-limited delta at the clock's master-clock time
 *       ({@code hq_fm = 1}, {@code sound.c:405-418}). One internal clock is 42
 *       master clocks ({@code YM2612_CLOCK_RATIO}, {@code sound.c:44,306}).
 *       The engine chip's pin sum is the same 24-cycle sum (its frame value
 *       is that sum shifted by {@code Ym2612Chip.OUTPUT_SHIFT}).</li>
 *   <li>Frame: an NTSC frame is {@code 3420 * 262} master clocks. Clocks whose
 *       time falls at or beyond the frame end belong to the next frame with
 *       the frame length subtracted ({@code sound.c:447}); the buffer frame
 *       is then ended ({@code blip_end_frame}, {@code sound.c:459}) and every
 *       available sample is read ({@code audio_update}, {@code system.c:201-246}).</li>
 *   <li>Rates: {@code audio_init(44100, 0)} ({@code cinterface.c:987}) makes the
 *       buffer {@code 44100 / 10} samples and sets rates from the NTSC master
 *       clock 53,693,175 Hz to 44,100 Hz ({@code system.c:63-132}).</li>
 *   <li>Filter ({@code config.filter}, {@code system.c:249-307}): none; bit 0
 *       single-pole low-pass in 16.16 fixed point with {@code lp_range} and
 *       its 16-bit carry between frames; bit 1 (only when bit 0 is clear) the
 *       three-band equaliser ({@link ThreeBandEq}) followed by 16-bit
 *       clipping. Its gains are BizHawk's {@code (short)(gain * 100)}
 *       integers ({@code GPGX.ISettable.cs:329-331}) divided by 100.0
 *       ({@code system.c:184-186}).</li>
 * </ul>
 *
 * <p>PSG: the session's PSG data-bus bytes enter {@link NativeCompatiblePsgOutput}
 * at the master-clock time of the chip's next YM clock and add their deltas to
 * the same buffer, as GPGX's {@code psg.c} does ({@code psg_preamp 150},
 * {@code hq_psg 1}). PSG write times are the production session's own.
 *
 * <p><b>What it does not model (explicit exclusions).</b> The MAME YM2612 core (BizHawk
 * {@code GenesisFMSoundChip 0}, the Knuckles complete movie) is not
 * available: this stage is Nuked-only and never stands in for it. PAL timing
 * and mono mixing are not modelled. Write scheduling and the DAC are the
 * production chip's own; this stage only observes its pins.
 *
 * <p><b>Clock origin.</b> Master-clock time zero is the chip clock at which the
 * stage was enabled, which must be a slot-sweep boundary. Enabled at device
 * construction that is the engine chip's power-on, the same origin BizHawk
 * uses at {@code system_reset}. The stage never adopts observed native state.
 *
 * <p><b>Rewind.</b> Every mutable field (buffer offset and factor, both
 * integrators, both sample buffers, held FM level and previous delta level,
 * master-clock frame position, PSG generator state, low-pass carries,
 * equaliser histories and the
 * pending output queue) is carried by {@link Snapshot} and by
 * {@link #copyFrom(NativeCompatiblePcmOutput)}; {@link Ym2612Chip} includes
 * the stage in both its snapshot and its mutation-backup paths.
 */
public final class NativeCompatiblePcmOutput {

    /** NTSC master clock, Hz ({@code system_clock}, BizHawk {@code audio_init(44100, 0)}). */
    public static final double MASTER_CLOCK_NTSC = 53_693_175.0;
    /** Output sample rate BizHawk passes to {@code audio_init}. */
    public static final int SAMPLE_RATE = 44_100;
    /** Master clocks per NTSC frame: 3420 per line, 262 lines. */
    public static final int FRAME_MCYCLES = 3420 * 262;
    /** Master clocks per YM internal clock: input clock / 6 = (master / 7) / 6. */
    public static final int YM_CLOCK_RATIO = 7 * 6;
    /** Nuked pin-sum output scale in {@code YM3438_Update}. */
    public static final int NUKED_OUTPUT_SCALE = 11;
    /** BizHawk {@code config.fm_preamp}. */
    public static final int FM_PREAMP = 100;
    private static final int BUFFER_SIZE = SAMPLE_RATE / 10;

    /** {@code config.filter}: 0 none, 1 low-pass, 2 three-band EQ. */
    public enum Filter {
        NONE(0), LOW_PASS(1), THREE_BAND(2);

        private final int configValue;

        Filter(int configValue) {
            this.configValue = configValue;
        }

        /** BizHawk {@code GPGXSyncSettings.Filter} value. */
        public int configValue() {
            return configValue;
        }

        public static Filter fromConfigValue(int value) {
            for (Filter filter : values()) {
                if (filter.configValue == value) {
                    return filter;
                }
            }
            throw new IllegalArgumentException("unsupported GPGX audio filter " + value);
        }
    }

    /**
     * The movie's audio SyncSettings. Gains are BizHawk's integer percent
     * ({@code (short)(gain * 100)}); {@code lowPassRange} is 16.16 fixed point.
     */
    public record Config(Filter filter, int lowPassRange, int lowFreq, int highFreq,
            int lowGain, int midGain, int highGain) {
        public Config {
            Objects.requireNonNull(filter, "filter");
            if (lowPassRange < 0 || lowPassRange > 0xFFFF) {
                throw new IllegalArgumentException("lowPassRange must be an unsigned 16-bit value");
            }
            if (lowFreq != (short) lowFreq || highFreq != (short) highFreq) {
                throw new IllegalArgumentException("equaliser frequencies are BizHawk 16-bit values");
            }
        }

        /** BizHawk GPGX defaults with the given filter (LowPassRange 0x6666, 880/5000 Hz, unity gains). */
        public static Config withFilter(Filter filter) {
            return new Config(filter, 0x6666, 880, 5000, 100, 100, 100);
        }
    }

    private final Config config;
    private final BlipBuf blip = new BlipBuf(BUFFER_SIZE);
    /** GPGX-equivalent PSG contribution into the same buffer ({@link NativeCompatiblePsgOutput}). */
    private final NativeCompatiblePsgOutput psg = new NativeCompatiblePsgOutput();
    /** Master-clock time of the next YM internal clock inside the open frame ({@code fm_cycles_start}). */
    private int fmTime;
    /** Pin sum of the last complete slot sweep, per side ({@code ym3438_sample}). */
    private int heldLeft;
    private int heldRight;
    /** Scaled level of the previous clock ({@code fm_last}). */
    private int lastLeft;
    private int lastRight;
    private long framesEnded;
    /** Low-pass carries ({@code llp}, {@code rrp}): 16-bit between frames. */
    private short lowPassLeft;
    private short lowPassRight;
    private final ThreeBandEq eqLeft;
    private final ThreeBandEq eqRight;
    /** Interleaved stereo samples read at frame ends and not yet drained. */
    private short[] pending = new short[2 * 2048];
    private int pendingFrames;
    private final short[] frameScratch = new short[2 * (BUFFER_SIZE + 64)];

    public NativeCompatiblePcmOutput(Config config) {
        this.config = Objects.requireNonNull(config, "config");
        blip.setRates(MASTER_CLOCK_NTSC, SAMPLE_RATE);
        blip.clear();
        eqLeft = new ThreeBandEq(config.lowFreq(), config.highFreq(), SAMPLE_RATE,
                config.lowGain(), config.midGain(), config.highGain());
        eqRight = new ThreeBandEq(config.lowFreq(), config.highFreq(), SAMPLE_RATE,
                config.lowGain(), config.midGain(), config.highGain());
    }

    public Config config() {
        return config;
    }

    /** Latches the pin sum of a completed 24-cycle slot sweep; call before that sweep's last {@link #clockFm()}. */
    void latchFmFrame(int pinSumLeft, int pinSumRight) {
        heldLeft = pinSumLeft;
        heldRight = pinSumRight;
    }

    /** One YM internal clock at the next master-clock position. */
    void clockFm() {
        if (fmTime >= FRAME_MCYCLES) {
            endFrame();
            fmTime -= FRAME_MCYCLES;
        }
        int l = heldLeft * NUKED_OUTPUT_SCALE * FM_PREAMP / 100;
        int r = heldRight * NUKED_OUTPUT_SCALE * FM_PREAMP / 100;
        blip.addDelta(fmTime, l - lastLeft, r - lastRight);
        lastLeft = l;
        lastRight = r;
        fmTime += YM_CLOCK_RATIO;
    }

    /**
     * One PSG data-bus byte at the master-clock time of the next YM clock: the
     * session presents PSG bytes between chip clocks, where GPGX's
     * {@code psg_write} receives the CPU's cycle count.
     */
    void psgWrite(int value) {
        if (fmTime >= FRAME_MCYCLES) {
            endFrame();
            fmTime -= FRAME_MCYCLES;
        }
        psg.write(blip, fmTime, value & 0xFF);
    }

    private void endFrame() {
        // sound_update: the PSG runs to the frame end before the buffer frame ends (sound.c:377-381).
        psg.endFrame(blip, FRAME_MCYCLES);
        blip.endFrame(FRAME_MCYCLES);
        int size = blip.samplesAvail();
        blip.readSamples(frameScratch, 0, size);
        applyFilter(frameScratch, size);
        ensurePending(size);
        System.arraycopy(frameScratch, 0, pending, pendingFrames * 2, size * 2);
        pendingFrames += size;
        framesEnded++;
    }

    private void applyFilter(short[] out, int samples) {
        if (samples <= 0) {
            return;
        }
        switch (config.filter()) {
            case NONE -> { }
            case LOW_PASS -> {
                long factorA = config.lowPassRange();
                long factorB = 0x10000 - factorA;
                int l = lowPassLeft;
                int r = lowPassRight;
                for (int i = 0; i < samples; i++) {
                    // C uint32 arithmetic, assigned to int32: mod 2^32, then an arithmetic shift.
                    l = (int) (l * factorA + out[2 * i] * factorB) >> 16;
                    r = (int) (r * factorA + out[2 * i + 1] * factorB) >> 16;
                    out[2 * i] = (short) l;
                    out[2 * i + 1] = (short) r;
                }
                lowPassLeft = (short) l;
                lowPassRight = (short) r;
            }
            case THREE_BAND -> {
                for (int i = 0; i < samples; i++) {
                    out[2 * i] = (short) clamp16(eqLeft.process(out[2 * i]));
                    out[2 * i + 1] = (short) clamp16(eqRight.process(out[2 * i + 1]));
                }
            }
        }
    }

    private static int clamp16(int value) {
        return value > 32767 ? 32767 : Math.max(value, -32768);
    }

    private void ensurePending(int additionalFrames) {
        int needed = (pendingFrames + additionalFrames) * 2;
        if (needed > pending.length) {
            pending = Arrays.copyOf(pending, Math.max(needed, pending.length * 2));
        }
    }

    /** Absolute master-clock time of the next YM clock since the stage's origin (vector generation). */
    long masterClock() {
        return framesEnded * FRAME_MCYCLES + fmTime;
    }

    /** Completed NTSC frames whose samples have been read into the queue. */
    public long framesEnded() {
        return framesEnded;
    }

    /** Stereo frames waiting in the queue. */
    public int pendingStereoFrames() {
        return pendingFrames;
    }

    /** Moves up to {@code maxFrames} queued stereo frames into {@code target}; returns the count moved. */
    public int drain(short[] target, int offsetSamples, int maxFrames) {
        Objects.requireNonNull(target, "target");
        int frames = Math.min(Math.min(maxFrames, pendingFrames), (target.length - offsetSamples) / 2);
        if (frames <= 0) {
            return 0;
        }
        System.arraycopy(pending, 0, target, offsetSamples, frames * 2);
        System.arraycopy(pending, frames * 2, pending, 0, (pendingFrames - frames) * 2);
        pendingFrames -= frames;
        return frames;
    }

    // ---------------------------------------------------------------- state

    /** Complete immutable stage state. */
    public record Snapshot(Config config, BlipBuf.State blip, int fmTime, int heldLeft, int heldRight,
            int lastLeft, int lastRight, long framesEnded, short lowPassLeft, short lowPassRight,
            ThreeBandEq.State eqLeft, ThreeBandEq.State eqRight, short[] pending,
            NativeCompatiblePsgOutput.State psg) {
        public Snapshot {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(blip, "blip");
            Objects.requireNonNull(eqLeft, "eqLeft");
            Objects.requireNonNull(eqRight, "eqRight");
            Objects.requireNonNull(psg, "psg");
            pending = pending.clone();
        }

        @Override
        public short[] pending() {
            return pending.clone();
        }

        @Override
        public boolean equals(Object candidate) {
            return candidate instanceof Snapshot other
                    && config.equals(other.config)
                    && blip.equals(other.blip)
                    && fmTime == other.fmTime
                    && heldLeft == other.heldLeft
                    && heldRight == other.heldRight
                    && lastLeft == other.lastLeft
                    && lastRight == other.lastRight
                    && framesEnded == other.framesEnded
                    && lowPassLeft == other.lowPassLeft
                    && lowPassRight == other.lowPassRight
                    && eqLeft.equals(other.eqLeft)
                    && eqRight.equals(other.eqRight)
                    && Arrays.equals(pending, other.pending)
                    && psg.equals(other.psg);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(config, blip, fmTime, heldLeft, heldRight, lastLeft, lastRight,
                    framesEnded, lowPassLeft, lowPassRight, eqLeft, eqRight, psg);
            return 31 * result + Arrays.hashCode(pending);
        }
    }

    public Snapshot captureSnapshot() {
        return new Snapshot(config, blip.captureState(), fmTime, heldLeft, heldRight, lastLeft, lastRight,
                framesEnded, lowPassLeft, lowPassRight, eqLeft.captureState(), eqRight.captureState(),
                Arrays.copyOf(pending, pendingFrames * 2), psg.captureState());
    }

    public void restoreSnapshot(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!config.equals(snapshot.config())) {
            throw new IllegalArgumentException("native-compatible output snapshot has a different configuration");
        }
        blip.restoreState(snapshot.blip());
        fmTime = snapshot.fmTime();
        heldLeft = snapshot.heldLeft();
        heldRight = snapshot.heldRight();
        lastLeft = snapshot.lastLeft();
        lastRight = snapshot.lastRight();
        framesEnded = snapshot.framesEnded();
        lowPassLeft = snapshot.lowPassLeft();
        lowPassRight = snapshot.lowPassRight();
        eqLeft.restoreState(snapshot.eqLeft());
        eqRight.restoreState(snapshot.eqRight());
        psg.restoreState(snapshot.psg());
        short[] queued = snapshot.pending;
        ensureCapacity(queued.length);
        System.arraycopy(queued, 0, pending, 0, queued.length);
        pendingFrames = queued.length / 2;
    }

    private void ensureCapacity(int samples) {
        if (samples > pending.length) {
            pending = new short[Math.max(samples, pending.length * 2)];
        }
    }

    /** Allocation-free copy of every mutable field, for mutation backups; configurations must match. */
    public void copyFrom(NativeCompatiblePcmOutput source) {
        if (!config.equals(source.config)) {
            throw new IllegalArgumentException("native-compatible output configurations differ");
        }
        blip.copyFrom(source.blip);
        fmTime = source.fmTime;
        heldLeft = source.heldLeft;
        heldRight = source.heldRight;
        lastLeft = source.lastLeft;
        lastRight = source.lastRight;
        framesEnded = source.framesEnded;
        lowPassLeft = source.lowPassLeft;
        lowPassRight = source.lowPassRight;
        eqLeft.copyFrom(source.eqLeft);
        eqRight.copyFrom(source.eqRight);
        psg.copyFrom(source.psg);
        ensureCapacity(source.pendingFrames * 2);
        System.arraycopy(source.pending, 0, pending, 0, source.pendingFrames * 2);
        pendingFrames = source.pendingFrames;
    }

    /**
     * Three-band equaliser of Genesis Plus GX {@code core/sound/eq.c} (sha256
     * {@code 44567e07...d885d3}): "3 Band EQ" (c) Neil C / Etanza Systems 2K6,
     * placed in the public domain by its author, after Paul Kellet's filter
     * (musicdsp). Ported line for line in IEEE double arithmetic (Java's
     * strict semantics). The cutoffs use {@link MuslSin}, the {@code sin} the
     * stock BizHawk 2.11 core links, because the libm matters: glibc's
     * {@code sin} gives a cutoff one ulp away for 880 Hz at 44.1 kHz, which
     * changed 916 of 11,774 unity-gain output samples in the vector harness.
     * The stock core inlines this filter (no {@code do_3band} symbol); its
     * per-sample machine code is not audited here, only its source.
     */
    public static final class ThreeBandEq {
        /** {@code vsa}: very small amount (denormal fix), eq.c:52. */
        private static final double VSA = 1.0 / 4294967295.0;

        private final double lf;
        private final double hf;
        private final double lg;
        private final double mg;
        private final double hg;
        private double f1p0, f1p1, f1p2, f1p3;
        private double f2p0, f2p1, f2p2, f2p3;
        private double sdm1, sdm2, sdm3;

        /** {@code init_3band_state} (eq.c:68-87) then the gains of {@code audio_set_equalizer}. */
        public ThreeBandEq(int lowFreq, int highFreq, int mixFreq, int lowGain, int midGain, int highGain) {
            lf = 2 * nativeSin(Math.PI * ((double) lowFreq / (double) mixFreq));
            hf = 2 * nativeSin(Math.PI * ((double) highFreq / (double) mixFreq));
            lg = (double) lowGain / 100.0;
            mg = (double) midGain / 100.0;
            hg = (double) highGain / 100.0;
        }

        /**
         * The cutoff {@code sin}: musl's, as linked into the stock BizHawk 2.11
         * core ({@link MuslSin}), never the host or JDK libm.
         */
        static double nativeSin(double x) {
            return MuslSin.sin(x);
        }

        /** {@code do_3band} (eq.c:96-131); the C function returns {@code (int)(l + m + h)}. */
        public int process(int sample) {
            f1p0 += (lf * ((double) sample - f1p0)) + VSA;
            f1p1 += (lf * (f1p0 - f1p1));
            f1p2 += (lf * (f1p1 - f1p2));
            f1p3 += (lf * (f1p2 - f1p3));
            double l = f1p3;

            f2p0 += (hf * ((double) sample - f2p0)) + VSA;
            f2p1 += (hf * (f2p0 - f2p1));
            f2p2 += (hf * (f2p1 - f2p2));
            f2p3 += (hf * (f2p2 - f2p3));
            double h = sdm3 - f2p3;

            double m = sample - (h + l);
            l *= lg;
            m *= mg;
            h *= hg;

            sdm3 = sdm2;
            sdm2 = sdm1;
            sdm1 = sample;
            return (int) (l + m + h);
        }

        public record State(double f1p0, double f1p1, double f1p2, double f1p3,
                double f2p0, double f2p1, double f2p2, double f2p3,
                double sdm1, double sdm2, double sdm3) {
        }

        public State captureState() {
            return new State(f1p0, f1p1, f1p2, f1p3, f2p0, f2p1, f2p2, f2p3, sdm1, sdm2, sdm3);
        }

        public void restoreState(State state) {
            f1p0 = state.f1p0();
            f1p1 = state.f1p1();
            f1p2 = state.f1p2();
            f1p3 = state.f1p3();
            f2p0 = state.f2p0();
            f2p1 = state.f2p1();
            f2p2 = state.f2p2();
            f2p3 = state.f2p3();
            sdm1 = state.sdm1();
            sdm2 = state.sdm2();
            sdm3 = state.sdm3();
        }

        void copyFrom(ThreeBandEq source) {
            f1p0 = source.f1p0;
            f1p1 = source.f1p1;
            f1p2 = source.f1p2;
            f1p3 = source.f1p3;
            f2p0 = source.f2p0;
            f2p1 = source.f2p1;
            f2p2 = source.f2p2;
            f2p3 = source.f2p3;
            sdm1 = source.sdm1;
            sdm2 = source.sdm2;
            sdm3 = source.sdm3;
        }
    }
}
