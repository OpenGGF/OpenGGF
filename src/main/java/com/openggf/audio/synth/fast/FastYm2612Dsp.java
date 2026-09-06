package com.openggf.audio.synth.fast;

import java.util.Arrays;

/**
 * Register-level YM2612 model: registers in, six channel outputs per internal
 * frame out. Built clean-room from the techniques specification
 * ({@code docs/architecture/designs/2026-09-06-fast-fm-core-design.md}) and
 * public hardware documentation (Yamaha YM2608/YM2612 manuals: register map,
 * detune and LFO tables; Nemesis's SpritesMind envelope/phase research:
 * attenuation scale, rate formula, counter-shift and increment tables, attack
 * formula). No emulator source was consulted; the author read only prose
 * summaries of ymfm and fmgen, which are reproduced as techniques 1–9 in the
 * design document.
 *
 * <p>Not modelled: operator pipelining within a frame, bus timing, the busy
 * flag, LSI test registers, and the analog output stage. Everything else the
 * SMPS drivers touch — key on/off, DT/MUL, TL, RS/AR, AM/D1R, D2R, SL/RR,
 * SSG-EG, F-number/block including channel-3 special mode, feedback and the
 * eight algorithms, L/R/AMS/PMS, the LFO, timers A/B with CSM, and the DAC —
 * is.
 */
public final class FastYm2612Dsp implements FmDsp {
    // ------------------------------------------------------------ tables

    /** Quarter-wave log2-sine, 4.8 fixed point (12 bits), indexed by 8-bit phase. */
    private static final int[] LOG_SIN = new int[256];
    /** 2^(x/256) - 1 scaled to 10 bits, indexed by the fractional attenuation. */
    private static final int[] EXP = new int[256];
    /** Yamaha detune table: units of phase-increment LSB by key code and |DT|. */
    private static final int[][] DETUNE = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7},
        {1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 13, 14, 16, 16, 16, 16, 16},
        {2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 20, 22, 22, 22, 22, 22},
    };
    /** Key-code low bits from the top four F-number bits (manual, "note" bits). */
    private static final int[] FNUM_NOTE = {0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 3, 3};
    /** Envelope increment per rate (64) and counter phase (8); rates 0..47 repeat by rate & 3. */
    private static final int[][] EG_INCREMENT = new int[64][];
    /** Envelope counter shift per rate: how many counter LSBs must be zero before a step. */
    private static final int[] EG_SHIFT = new int[64];
    /**
     * Internal frames per LFO step (128 steps per period): the nearest integer
     * intervals to the manual's 3.98 .. 72.2 Hz table at its 8 MHz clock
     * (approximate: an arithmetic inference, not a measured divider table).
     */
    private static final int[] LFO_FRAMES_PER_STEP = {109, 78, 72, 68, 63, 45, 9, 6};
    /** AM depth per AMS in envelope units (0.046875 dB): 0, 1.4, 5.9, 11.8 dB. */
    private static final int[] AM_DEPTH = {0, 15, 63, 126};
    /** PM depth per PMS in cents (manual). */
    private static final double[] PM_CENTS = {0, 3.4, 6.7, 10, 14, 20, 40, 80};
    /** F-number multiplier per PMS and LFO step, Q16, from the cents depth on a 32-step triangle. */
    private static final int[][] PM_MULTIPLIER_Q16 = new int[8][32];
    private static final int LFO_STEPS = 128;
    private static final int PM_STEPS = 32;
    /** The envelope clock is master/432: one tick every three internal frames (Nemesis, later digital measurements). */
    private static final int EG_TICK_MASTER_CYCLES = 432;
    private static final int MASTER_CYCLES_PER_FRAME = 144;
    /** SSG-EG multiplies the decay-side increments by four. */
    private static final int SSG_STEP_MULTIPLIER = 4;
    /** Sustain level 15 is the full 5-bit code 31 in 3 dB steps (992), not the attenuation ceiling. */
    private static final int SUSTAIN_LEVEL_15 = 31 << 5;
    private static final int TIMER_A_FRAMES_PER_UNIT = 3;
    private static final int TIMER_B_FRAMES_PER_UNIT = 48;
    /** Register-slot order S1, S3, S2, S4 → operator index 0..3 (OP1, OP2, OP3, OP4). */
    private static final int[] SLOT_TO_OPERATOR = {0, 2, 1, 3};
    /** Key-on register bits 4..7 are S1, S3, S2, S4. */
    private static final int[] KEY_BIT_TO_OPERATOR = {0, 2, 1, 3};
    private static final int EG_ATTACK = 0;
    private static final int EG_DECAY = 1;
    private static final int EG_SUSTAIN = 2;
    private static final int EG_RELEASE = 3;
    private static final int MAX_ATTENUATION = 0x3FF;
    private static final int OUTPUT_MAX = 8191;
    private static final int OUTPUT_MIN = -8192;

    static {
        for (int i = 0; i < 256; i++) {
            double sine = Math.sin((i + 0.5) * Math.PI / 512.0);
            LOG_SIN[i] = (int) Math.round(-Math.log(sine) / Math.log(2.0) * 256.0);
            EXP[i] = (int) Math.round((Math.pow(2.0, i / 256.0) - 1.0) * 1024.0);
        }
        int[][] slow = {
            {0, 1, 0, 1, 0, 1, 0, 1},
            {0, 1, 0, 1, 1, 1, 0, 1},
            {0, 1, 1, 1, 0, 1, 1, 1},
            {0, 1, 1, 1, 1, 1, 1, 1},
        };
        for (int rate = 0; rate < 64; rate++) {
            if (rate < 48) {
                EG_INCREMENT[rate] = slow[rate & 3];
            } else {
                int base = 1 << ((rate - 48) >> 2);
                int[] pattern = new int[8];
                for (int i = 0; i < 8; i++) {
                    int form = rate & 3;
                    boolean doubled = form == 3 ? (i & 3) != 0
                            : form == 2 ? (i & 1) != 0
                            : form == 1 && (i & 3) == 3;
                    pattern[i] = rate >= 60 ? 8 : (doubled ? base * 2 : base);
                }
                EG_INCREMENT[rate] = pattern;
            }
            EG_SHIFT[rate] = Math.max(0, 11 - (rate >> 2));
        }
        for (int pms = 0; pms < 8; pms++) {
            for (int step = 0; step < PM_STEPS; step++) {
                // Triangle: 0 → +1 → 0 → -1 → 0 over 32 steps.
                int tri = step < 8 ? step : step < 24 ? 16 - step : step - 32;
                double cents = PM_CENTS[pms] * tri / 8.0;
                PM_MULTIPLIER_Q16[pms][step] = (int) Math.round(Math.pow(2.0, cents / 1200.0) * 65536.0);
            }
        }
    }

    // ------------------------------------------------------------- state
    // Per-operator arrays are indexed channel * 4 + operator (24 entries).

    private final int[] registers = new int[512];
    private final int[] phase = new int[24];
    private final int[] phaseIncrement = new int[24];
    private final int[] attenuation = new int[24];
    private final int[] egState = new int[24];
    private final int[] keyOn = new int[24];
    private final int[] ssgInvert = new int[24];
    private final int[] ssgHeld = new int[24];
    /** SSG-EG restart effects deferred until the restarted attack completes: bit 0 phase reset, bit 1 ALT toggle. */
    private final int[] ssgPendingRestart = new int[24];
    private final int[] output = new int[24];
    private final int[] detune = new int[24];
    private final int[] multiple = new int[24];
    private final int[] totalLevel = new int[24];
    private final int[] rateScaling = new int[24];
    private final int[] attackRate = new int[24];
    private final int[] decayRate = new int[24];
    private final int[] sustainRate = new int[24];
    private final int[] releaseRate = new int[24];
    private final int[] sustainLevel = new int[24];
    private final int[] amEnabled = new int[24];
    private final int[] ssgMode = new int[24];
    private final int[] keyCode = new int[24];
    /** Effective rates (2R + key scaling, capped) cached per slot; refreshed on register writes and key-code changes. */
    private final int[] rateAttack = new int[24];
    private final int[] rateDecay = new int[24];
    private final int[] rateSustain = new int[24];
    private final int[] rateRelease = new int[24];
    private final int[] operatorFnum = new int[24];
    private final int[] operatorBlock = new int[24];
    private final int[] channelFnum = new int[6];
    private final int[] channelBlock = new int[6];
    private final int[] latchedFnumHigh = new int[2];
    private final int[] latchedCh3FnumHigh = new int[3];
    private final int[] ch3Fnum = new int[3];
    private final int[] ch3Block = new int[3];
    private final int[] feedback = new int[6];
    private final int[] algorithm = new int[6];
    private final int[] amSensitivity = new int[6];
    private final int[] pmSensitivity = new int[6];
    private final int[] feedbackHistory = new int[12];
    private final int[] operatorOut = new int[4];
    private final int[] csmKeyed = new int[1];
    private final int[] scalar = new int[18];
    private static final int S_EG_COUNTER = 0;
    private static final int S_EG_FRAME = 1;
    private static final int S_LFO_ENABLED = 2;
    private static final int S_LFO_RATE = 3;
    private static final int S_LFO_COUNTER_Q8 = 4;
    private static final int S_LFO_STEP = 5;
    private static final int S_TIMER_A_LOAD = 6;
    private static final int S_TIMER_B_LOAD = 7;
    private static final int S_TIMER_A_COUNT = 8;
    private static final int S_TIMER_B_COUNT = 9;
    private static final int S_TIMER_CONTROL = 10;
    private static final int S_STATUS = 11;
    private static final int S_CH3_MODE = 12;
    private static final int S_DAC_ENABLED = 13;
    private static final int S_DAC_VALUE = 14;
    private static final int S_SSG_ENABLED_MASK = 15;
    private static final int S_TIMER_A_RELOAD = 16;
    private static final int S_TIMER_B_RELOAD = 17;

    public FastYm2612Dsp() {
        reset();
    }

    @Override
    public void reset() {
        Arrays.fill(registers, 0);
        for (int[] array : new int[][] {phase, phaseIncrement, egState, keyOn, ssgInvert, ssgHeld, ssgPendingRestart, output, detune,
                multiple, totalLevel, rateScaling, attackRate, decayRate, sustainRate, releaseRate,
                sustainLevel, amEnabled, ssgMode, keyCode, rateAttack, rateDecay, rateSustain, rateRelease,
                operatorFnum, operatorBlock, channelFnum,
                channelBlock, latchedFnumHigh, latchedCh3FnumHigh, ch3Fnum, ch3Block, feedback, algorithm,
                amSensitivity, pmSensitivity, feedbackHistory, operatorOut, csmKeyed, scalar}) {
            Arrays.fill(array, 0);
        }
        Arrays.fill(attenuation, MAX_ATTENUATION);
        Arrays.fill(egState, EG_RELEASE);
        scalar[S_DAC_VALUE] = 0x80;
        scalar[S_TIMER_A_RELOAD] = 1024 * TIMER_A_FRAMES_PER_UNIT;
        scalar[S_TIMER_B_RELOAD] = 256 * TIMER_B_FRAMES_PER_UNIT;
        for (int slot = 0; slot < 24; slot++) {
            refreshRates(slot);
            refreshPhaseIncrement(slot);
        }
    }

    // ---------------------------------------------------------- registers

    @Override
    public void writeRegister(int port, int register, int value) {
        port &= 1;
        register &= 0xff;
        value &= 0xff;
        registers[(port << 8) | register] = value;
        if (port == 0 && register < 0x30) {
            writeGlobal(register, value);
            return;
        }
        int channelInPort = register & 3;
        if (channelInPort == 3) {
            return;
        }
        int channel = port * 3 + channelInPort;
        int group = register & 0xF0;
        if (group >= 0x30 && group <= 0x90) {
            int slot = channel * 4 + SLOT_TO_OPERATOR[(register >> 2) & 3];
            writeOperator(slot, group, value);
            return;
        }
        switch (group) {
            case 0xA0 -> writeFrequency(port, channel, register, value);
            case 0xB0 -> {
                if ((register & 0xC) == 0) {
                    feedback[channel] = (value >> 3) & 7;
                    algorithm[channel] = value & 7;
                } else if ((register & 0xC) == 4) {
                    amSensitivity[channel] = (value >> 4) & 3;
                    pmSensitivity[channel] = value & 7;
                    for (int op = 0; op < 4; op++) {
                        refreshPhaseIncrement(channel * 4 + op);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void writeGlobal(int register, int value) {
        switch (register) {
            case 0x22 -> {
                boolean enable = (value & 8) != 0;
                scalar[S_LFO_RATE] = value & 7;
                if (!enable && scalar[S_LFO_ENABLED] != 0) {
                    scalar[S_LFO_STEP] = 0;
                    scalar[S_LFO_COUNTER_Q8] = 0;
                    refreshAllPhaseIncrements();
                }
                scalar[S_LFO_ENABLED] = enable ? 1 : 0;
            }
            case 0x24 -> {
                scalar[S_TIMER_A_LOAD] = (scalar[S_TIMER_A_LOAD] & 3) | (value << 2);
                scalar[S_TIMER_A_RELOAD] = (1024 - scalar[S_TIMER_A_LOAD]) * TIMER_A_FRAMES_PER_UNIT;
            }
            case 0x25 -> {
                scalar[S_TIMER_A_LOAD] = (scalar[S_TIMER_A_LOAD] & 0x3FC) | (value & 3);
                scalar[S_TIMER_A_RELOAD] = (1024 - scalar[S_TIMER_A_LOAD]) * TIMER_A_FRAMES_PER_UNIT;
            }
            case 0x26 -> {
                scalar[S_TIMER_B_LOAD] = value;
                scalar[S_TIMER_B_RELOAD] = (256 - value) * TIMER_B_FRAMES_PER_UNIT;
            }
            case 0x27 -> {
                int previous = scalar[S_TIMER_CONTROL];
                scalar[S_TIMER_CONTROL] = value;
                scalar[S_CH3_MODE] = (value >> 6) & 3;
                if ((value & 1) != 0 && (previous & 1) == 0) {
                    scalar[S_TIMER_A_COUNT] = scalar[S_TIMER_A_RELOAD];
                }
                if ((value & 2) != 0 && (previous & 2) == 0) {
                    scalar[S_TIMER_B_COUNT] = scalar[S_TIMER_B_RELOAD];
                }
                if ((value & 0x10) != 0) {
                    scalar[S_STATUS] &= ~1;
                }
                if ((value & 0x20) != 0) {
                    scalar[S_STATUS] &= ~2;
                }
                for (int op = 0; op < 4; op++) {
                    refreshPhaseIncrement(8 + op);
                }
            }
            case 0x28 -> {
                int channelBits = value & 3;
                if (channelBits == 3) {
                    return;
                }
                int channel = channelBits + ((value >> 2) & 1) * 3;
                for (int bit = 0; bit < 4; bit++) {
                    int slot = channel * 4 + KEY_BIT_TO_OPERATOR[bit];
                    if ((value & (0x10 << bit)) != 0) {
                        keyOnSlot(slot);
                    } else {
                        keyOffSlot(slot);
                    }
                }
            }
            case 0x2A -> scalar[S_DAC_VALUE] = value;
            case 0x2B -> scalar[S_DAC_ENABLED] = (value >> 7) & 1;
            default -> {
            }
        }
    }

    private void writeOperator(int slot, int group, int value) {
        switch (group) {
            case 0x30 -> {
                detune[slot] = (value >> 4) & 7;
                multiple[slot] = value & 15;
                refreshPhaseIncrement(slot);
            }
            case 0x40 -> totalLevel[slot] = (value & 0x7F) << 3;
            case 0x50 -> {
                rateScaling[slot] = (value >> 6) & 3;
                attackRate[slot] = value & 0x1F;
                refreshRates(slot);
            }
            case 0x60 -> {
                amEnabled[slot] = (value >> 7) & 1;
                decayRate[slot] = value & 0x1F;
                refreshRates(slot);
            }
            case 0x70 -> {
                sustainRate[slot] = value & 0x1F;
                refreshRates(slot);
            }
            case 0x80 -> {
                int sl = (value >> 4) & 15;
                sustainLevel[slot] = sl == 15 ? SUSTAIN_LEVEL_15 : sl << 5;
                releaseRate[slot] = value & 15;
                refreshRates(slot);
            }
            case 0x90 -> {
                ssgMode[slot] = value & 15;
                if ((value & 8) != 0) {
                    scalar[S_SSG_ENABLED_MASK] |= 1 << slot;
                } else {
                    scalar[S_SSG_ENABLED_MASK] &= ~(1 << slot);
                }
            }
            default -> {
            }
        }
    }

    private void writeFrequency(int port, int channel, int register, int value) {
        int sub = register & 0xC;
        int channelInPort = register & 3;
        if (sub == 0) {
            channelFnum[channel] = ((latchedFnumHigh[port] & 7) << 8) | value;
            channelBlock[channel] = (latchedFnumHigh[port] >> 3) & 7;
            for (int op = 0; op < 4; op++) {
                refreshPhaseIncrement(channel * 4 + op);
            }
        } else if (sub == 4) {
            latchedFnumHigh[port] = value & 0x3F;
        } else if (port == 0 && sub == 8) {
            // Channel 3 special-mode operator frequencies: A8..AA (S3, S1, S2 order).
            ch3Fnum[channelInPort] = ((latchedCh3FnumHigh[channelInPort] & 7) << 8) | value;
            ch3Block[channelInPort] = (latchedCh3FnumHigh[channelInPort] >> 3) & 7;
            for (int op = 0; op < 4; op++) {
                refreshPhaseIncrement(8 + op);
            }
        } else if (port == 0 && sub == 0xC) {
            latchedCh3FnumHigh[channelInPort] = value & 0x3F;
        }
    }

    // ------------------------------------------------------ derived state

    private void refreshAllPhaseIncrements() {
        for (int slot = 0; slot < 24; slot++) {
            refreshPhaseIncrement(slot);
        }
    }

    /** Channel-3 special mode: OP1..OP3 read their own F-number (A9, AA, A8 map to OP1, OP2, OP3); OP4 uses A2/A6. */
    private void resolveFrequency(int slot) {
        int channel = slot >> 2;
        int op = slot & 3;
        if (channel == 2 && scalar[S_CH3_MODE] != 0 && op < 3) {
            int index = op == 0 ? 1 : op == 1 ? 2 : 0;
            operatorFnum[slot] = ch3Fnum[index];
            operatorBlock[slot] = ch3Block[index];
        } else {
            operatorFnum[slot] = channelFnum[channel];
            operatorBlock[slot] = channelBlock[channel];
        }
    }

    private void refreshPhaseIncrement(int slot) {
        resolveFrequency(slot);
        int fnum = operatorFnum[slot];
        int block = operatorBlock[slot];
        int channel = slot >> 2;
        int pms = pmSensitivity[channel];
        // Key code comes from the raw F-number before LFO modulation (Sauraen's die tracing).
        int newKeyCode = (block << 2) | FNUM_NOTE[(fnum >> 7) & 15];
        if (scalar[S_LFO_ENABLED] != 0 && pms != 0) {
            int step = (scalar[S_LFO_STEP] >> 2) & (PM_STEPS - 1);
            fnum = (int) (((long) fnum * PM_MULTIPLIER_Q16[pms][step]) >> 16);
            fnum = Math.min(fnum, 0xFFF);
        }
        if (newKeyCode != keyCode[slot]) {
            keyCode[slot] = newKeyCode;
            refreshRates(slot);
        }
        int base = (fnum << block) >> 1;
        int dt = detune[slot];
        int detuneAmount = DETUNE[dt & 3][newKeyCode & 31];
        if ((dt & 4) != 0) {
            base -= detuneAmount;
            if (base < 0) {
                base += 1 << 17;
            }
        } else {
            base += detuneAmount;
        }
        base &= 0x1FFFF;
        int mul = multiple[slot];
        phaseIncrement[slot] = (mul == 0 ? base >> 1 : base * mul) & 0xFFFFF;
    }

    private void refreshRates(int slot) {
        rateAttack[slot] = effectiveRate(slot, attackRate[slot]);
        rateDecay[slot] = effectiveRate(slot, decayRate[slot]);
        rateSustain[slot] = effectiveRate(slot, sustainRate[slot]);
        rateRelease[slot] = effectiveRate(slot, releaseRate[slot] * 2 + 1);
    }

    private int effectiveRate(int slot, int rate) {
        if (rate == 0) {
            return 0;
        }
        int scaled = 2 * rate + (keyCode[slot] >> (3 - rateScaling[slot]));
        return Math.min(63, scaled);
    }

    private void keyOnSlot(int slot) {
        if (keyOn[slot] != 0) {
            return;
        }
        keyOn[slot] = 1;
        phase[slot] = 0;
        ssgInvert[slot] = 0;
        ssgHeld[slot] = 0;
        // A key-on with a real attack behaves like an SSG-EG restart: its
        // ALT toggle or non-ALT phase reset lands when that attack completes
        // (oracle-established; see handleSsgBoundary).
        ssgPendingRestart[slot] = (ssgMode[slot] & 8) != 0 && rateAttack[slot] < 62
                ? ((ssgMode[slot] & 2) != 0 ? 2 : (ssgMode[slot] & 1) != 0 ? 0 : 1) : 0;
        if (rateAttack[slot] >= 62) {
            attenuation[slot] = 0;
            egState[slot] = EG_DECAY;
        } else {
            egState[slot] = EG_ATTACK;
        }
    }

    private void keyOffSlot(int slot) {
        if (keyOn[slot] == 0) {
            return;
        }
        if ((ssgMode[slot] & 8) != 0 && ssgOutputInverted(slot)) {
            // Leaving an inverted SSG-EG pass: release continues from the level heard.
            attenuation[slot] = (0x200 - attenuation[slot]) & MAX_ATTENUATION;
        }
        ssgInvert[slot] = 0;
        ssgHeld[slot] = 0;
        ssgPendingRestart[slot] = 0;
        keyOn[slot] = 0;
        egState[slot] = EG_RELEASE;
    }

    // ------------------------------------------------------------- render

    @Override
    public void renderFrame(int[] out) {
        advanceTimers();
        advanceLfo();
        // SSG-EG's half-way boundary is tested every FM frame, before a coincident envelope update.
        int ssgMask = scalar[S_SSG_ENABLED_MASK];
        while (ssgMask != 0) {
            int slot = Integer.numberOfTrailingZeros(ssgMask);
            ssgMask &= ssgMask - 1;
            if (keyOn[slot] != 0 && egState[slot] != EG_ATTACK && attenuation[slot] >= 0x200) {
                handleSsgBoundary(slot);
            }
        }
        scalar[S_EG_FRAME] += MASTER_CYCLES_PER_FRAME;
        while (scalar[S_EG_FRAME] >= EG_TICK_MASTER_CYCLES) {
            scalar[S_EG_FRAME] -= EG_TICK_MASTER_CYCLES;
            advanceEnvelopes();
        }
        int amOffset = 0;
        if (scalar[S_LFO_ENABLED] != 0) {
            int step = scalar[S_LFO_STEP];
            int triangle = step < 64 ? 64 - step : step - 64; // 64 at rest, 0 at peak volume
            amOffset = triangle; // scaled per channel below
        }
        for (int channel = 0; channel < 6; channel++) {
            out[channel] = renderChannel(channel, amOffset);
        }
        if (scalar[S_DAC_ENABLED] != 0) {
            out[5] = (scalar[S_DAC_VALUE] - 0x80) << 6;
        }
    }

    private int renderChannel(int channel, int lfoTriangle) {
        int base = channel * 4;
        if (attenuation[base] == MAX_ATTENUATION && attenuation[base + 1] == MAX_ATTENUATION
                && attenuation[base + 2] == MAX_ATTENUATION && attenuation[base + 3] == MAX_ATTENUATION
                && egState[base] != EG_ATTACK && egState[base + 1] != EG_ATTACK
                && egState[base + 2] != EG_ATTACK && egState[base + 3] != EG_ATTACK) {
            // Silent channel: key-on resets the phase, so it need not advance here.
            feedbackHistory[channel * 2] = 0;
            feedbackHistory[channel * 2 + 1] = 0;
            output[base] = 0;
            output[base + 1] = 0;
            output[base + 2] = 0;
            output[base + 3] = 0;
            return 0;
        }
        int amDepth = AM_DEPTH[amSensitivity[channel]];
        int am = amDepth == 0 ? 0 : (lfoTriangle * amDepth) >> 6;
        int fb = feedback[channel];
        int feedbackInput = fb == 0 ? 0
                : (feedbackHistory[channel * 2] + feedbackHistory[channel * 2 + 1]) >> (10 - fb);
        // Hardware slot order S1, S3, S2, S4: OP3 sees OP2's previous-frame output.
        int previousOp2 = output[base + 1];
        int op1 = operatorSample(base, feedbackInput, am);
        feedbackHistory[channel * 2 + 1] = feedbackHistory[channel * 2];
        feedbackHistory[channel * 2] = op1;
        int op2;
        int op3;
        int op4;
        int sum;
        switch (algorithm[channel]) {
            case 0 -> {
                op3 = operatorSample(base + 2, previousOp2 >> 1, am);
                op2 = operatorSample(base + 1, op1 >> 1, am);
                op4 = operatorSample(base + 3, op3 >> 1, am);
                sum = op4;
            }
            case 1 -> {
                op3 = operatorSample(base + 2, (op1 + previousOp2) >> 1, am);
                op2 = operatorSample(base + 1, 0, am);
                op4 = operatorSample(base + 3, op3 >> 1, am);
                sum = op4;
            }
            case 2 -> {
                op3 = operatorSample(base + 2, previousOp2 >> 1, am);
                op2 = operatorSample(base + 1, 0, am);
                op4 = operatorSample(base + 3, (op1 + op3) >> 1, am);
                sum = op4;
            }
            case 3 -> {
                op3 = operatorSample(base + 2, 0, am);
                op2 = operatorSample(base + 1, op1 >> 1, am);
                op4 = operatorSample(base + 3, (op2 + op3) >> 1, am);
                sum = op4;
            }
            case 4 -> {
                op3 = operatorSample(base + 2, 0, am);
                op2 = operatorSample(base + 1, op1 >> 1, am);
                op4 = operatorSample(base + 3, op3 >> 1, am);
                sum = op2 + op4;
            }
            case 5 -> {
                op3 = operatorSample(base + 2, op1 >> 1, am);
                op2 = operatorSample(base + 1, op1 >> 1, am);
                op4 = operatorSample(base + 3, op1 >> 1, am);
                sum = op2 + op3 + op4;
            }
            case 6 -> {
                op3 = operatorSample(base + 2, 0, am);
                op2 = operatorSample(base + 1, op1 >> 1, am);
                op4 = operatorSample(base + 3, 0, am);
                sum = op2 + op3 + op4;
            }
            default -> {
                op3 = operatorSample(base + 2, 0, am);
                op2 = operatorSample(base + 1, 0, am);
                op4 = operatorSample(base + 3, 0, am);
                sum = op1 + op2 + op3 + op4;
            }
        }
        output[base] = op1;
        output[base + 1] = op2;
        output[base + 2] = op3;
        output[base + 3] = op4;
        return sum > OUTPUT_MAX ? OUTPUT_MAX : Math.max(sum, OUTPUT_MIN);
    }

    /** One operator sample: phase (plus modulation) through log-sine, envelope + TL + AM, exp. */
    private int operatorSample(int slot, int modulation, int am) {
        int phaseIndex = ((phase[slot] >> 10) + modulation) & 0x3FF;
        phase[slot] = (phase[slot] + phaseIncrement[slot]) & 0xFFFFF;
        int level = egOutput(slot) + totalLevel[slot];
        if (amEnabled[slot] != 0) {
            level += am;
        }
        if (level >= MAX_ATTENUATION) {
            return 0;
        }
        int quarter = phaseIndex & 0xFF;
        if ((phaseIndex & 0x100) != 0) {
            quarter = 0xFF - quarter;
        }
        int attenuation12 = LOG_SIN[quarter] + (level << 2);
        if (attenuation12 >= 0x1FFF) {
            return 0;
        }
        // 2^(-a/256) = 2^-(I+1) * 2^((256-F)/256): the fraction indexes the table complemented.
        int linear = ((EXP[(~attenuation12) & 0xFF] + 1024) << 2) >> (attenuation12 >> 8);
        return (phaseIndex & 0x200) != 0 ? -linear : linear;
    }

    /** SSG-EG output inversion: the ATT bit XOR the alternate toggle, only while keyed on. */
    private boolean ssgOutputInverted(int slot) {
        return keyOn[slot] != 0 && (((ssgMode[slot] >> 2) & 1) ^ ssgInvert[slot]) != 0;
    }

    /** The 10-bit attenuation the operator sees, honouring SSG-EG inversion. */
    private int egOutput(int slot) {
        int level = attenuation[slot];
        if ((ssgMode[slot] & 8) != 0 && ssgOutputInverted(slot)) {
            level = (0x200 - level) & MAX_ATTENUATION;
        }
        return level;
    }

    private void advanceEnvelopes() {
        int counter = (scalar[S_EG_COUNTER] + 1) & 0xFFF;
        if (counter == 0) {
            counter = 1;
        }
        scalar[S_EG_COUNTER] = counter;
        for (int slot = 0; slot < 24; slot++) {
            advanceEnvelope(slot, counter);
        }
    }

    private void advanceEnvelope(int slot, int counter) {
        int state = egState[slot];
        if (state == EG_DECAY && attenuation[slot] >= sustainLevel[slot]) {
            // The sustain level is a level comparison, not a step event: with a
            // zero decay rate (or a sustain level already reached) the envelope
            // still moves on to the sustain rate. Without this a D1R=0, SL=0
            // voice held at full level for ever (S3K effects were 2-4x too loud).
            state = EG_SUSTAIN;
            egState[slot] = state;
        }
        boolean ssg = (ssgMode[slot] & 8) != 0;
        if (ssg && ssgHeld[slot] != 0) {
            return;
        }
        if (state == EG_RELEASE && attenuation[slot] == MAX_ATTENUATION) {
            return; // fully released: nothing left to step
        }
        int rate;
        switch (state) {
            case EG_ATTACK -> rate = rateAttack[slot];
            case EG_DECAY -> rate = rateDecay[slot];
            case EG_SUSTAIN -> rate = rateSustain[slot];
            default -> rate = rateRelease[slot];
        }
        if (rate == 0) {
            return;
        }
        int shift = EG_SHIFT[rate];
        if ((counter & ((1 << shift) - 1)) != 0) {
            return;
        }
        int increment = EG_INCREMENT[rate][(counter >> shift) & 7];
        if (increment == 0) {
            return;
        }
        if (state == EG_ATTACK) {
            int level = attenuation[slot] + (((~attenuation[slot]) * increment) >> 4);
            if (level <= 0) {
                level = 0;
                egState[slot] = EG_DECAY;
                applySsgRestartEffects(slot);
            }
            attenuation[slot] = level;
            return;
        }
        if (ssg && ssgHeld[slot] != 0) {
            return;
        }
        int step = ssg ? increment * SSG_STEP_MULTIPLIER : increment;
        int level = Math.min(MAX_ATTENUATION, attenuation[slot] + step);
        if (state == EG_DECAY && level >= sustainLevel[slot]) {
            egState[slot] = EG_SUSTAIN;
        }
        attenuation[slot] = level;
    }

    /**
     * SSG-EG at the half-way boundary (attenuation 0x200 and beyond, keyed on).
     * Hold modes hold: an inverted output freezes at the boundary (heard loud),
     * a plain one goes to full attenuation (silent). Repeat modes toggle the
     * inversion if ALT is set and restart the attack from the attained
     * attenuation; when that attack is real (rate below 62) the restart's
     * side effect lands again when it completes, an ALT toggle or, for the
     * non-ALT modes, the phase reset, so the attack itself is heard inverted
     * and the phase restarts with the decay. With an instantaneous attack the
     * boundary toggle or phase reset is the whole effect. This timing was
     * established against the cycle-exact oracle (modes 8/10/12/14 with a
     * real attack moved from about 0.5 to above 0.9 correlation); the
     * published notes only place the toggle at the boundary, which is the same
     * thing when the attack is instantaneous.
     */
    private void handleSsgBoundary(int slot) {
        if (keyOn[slot] == 0) {
            return;
        }
        int mode = ssgMode[slot];
        boolean hold = (mode & 1) != 0;
        boolean alternate = (mode & 2) != 0;
        boolean instantAttack = rateAttack[slot] >= 62;
        if (hold) {
            if (ssgHeld[slot] == 0) {
                ssgHeld[slot] = 1;
                if (alternate && instantAttack) {
                    ssgInvert[slot] ^= 1;
                }
                attenuation[slot] = ssgOutputInverted(slot) ? 0x200 : MAX_ATTENUATION;
                egState[slot] = EG_SUSTAIN;
            }
            return;
        }
        if (alternate) {
            ssgInvert[slot] ^= 1;
        } else if (instantAttack) {
            phase[slot] = 0;
        }
        if (instantAttack) {
            attenuation[slot] = 0;
            egState[slot] = EG_DECAY;
            ssgPendingRestart[slot] = 0;
        } else {
            ssgPendingRestart[slot] = alternate ? 2 : 1;
            egState[slot] = EG_ATTACK;
        }
    }

    /** Lands a restart's deferred effects: phase reset (non-ALT) or inversion toggle (ALT). */
    private void applySsgRestartEffects(int slot) {
        int effects = ssgPendingRestart[slot];
        if (effects == 0 || (ssgMode[slot] & 8) == 0) {
            ssgPendingRestart[slot] = 0;
            return;
        }
        if ((effects & 1) != 0) {
            phase[slot] = 0;
        }
        if ((effects & 2) != 0) {
            ssgInvert[slot] ^= 1;
        }
        ssgPendingRestart[slot] = 0;
    }

    private void advanceLfo() {
        if (scalar[S_LFO_ENABLED] == 0) {
            return;
        }
        int counter = scalar[S_LFO_COUNTER_Q8] + 1;
        int period = LFO_FRAMES_PER_STEP[scalar[S_LFO_RATE]];
        if (counter >= period) {
            counter -= period;
            int step = (scalar[S_LFO_STEP] + 1) & (LFO_STEPS - 1);
            scalar[S_LFO_STEP] = step;
            if ((step & 3) == 0) {
                for (int channel = 0; channel < 6; channel++) {
                    if (pmSensitivity[channel] != 0) {
                        for (int op = 0; op < 4; op++) {
                            refreshPhaseIncrement(channel * 4 + op);
                        }
                    }
                }
            }
        }
        scalar[S_LFO_COUNTER_Q8] = counter;
    }

    private void advanceTimers() {
        int control = scalar[S_TIMER_CONTROL];
        if ((control & 3) == 0 && csmKeyed[0] == 0) {
            return;
        }
        if ((control & 1) != 0) {
            if (--scalar[S_TIMER_A_COUNT] <= 0) {
                scalar[S_TIMER_A_COUNT] = scalar[S_TIMER_A_RELOAD];
                if ((control & 4) != 0) {
                    scalar[S_STATUS] |= 1;
                }
                if (scalar[S_CH3_MODE] == 2) {
                    // CSM: key channel 3 on for this frame.
                    for (int op = 0; op < 4; op++) {
                        int slot = 8 + op;
                        if (keyOn[slot] == 0) {
                            keyOnSlot(slot);
                            csmKeyed[0] |= 1 << op;
                        }
                    }
                }
            }
        }
        if (csmKeyed[0] != 0 && scalar[S_TIMER_A_COUNT] != scalar[S_TIMER_A_RELOAD]) {
            for (int op = 0; op < 4; op++) {
                if ((csmKeyed[0] & (1 << op)) != 0) {
                    keyOffSlot(8 + op);
                }
            }
            csmKeyed[0] = 0;
        }
        if ((control & 2) != 0) {
            if (--scalar[S_TIMER_B_COUNT] <= 0) {
                scalar[S_TIMER_B_COUNT] = scalar[S_TIMER_B_RELOAD];
                if ((control & 8) != 0) {
                    scalar[S_STATUS] |= 2;
                }
            }
        }
    }

    @Override
    public int readStatus() {
        return scalar[S_STATUS] & 3;
    }

    // ---------------------------------------------------------- snapshots

    private int[][] arrays() {
        return new int[][] {registers, phase, phaseIncrement, attenuation, egState, keyOn, ssgInvert, ssgHeld, ssgPendingRestart, output,
                detune, multiple, totalLevel, rateScaling, attackRate, decayRate, sustainRate, releaseRate,
                sustainLevel, amEnabled, ssgMode, keyCode, rateAttack, rateDecay, rateSustain, rateRelease,
                operatorFnum, operatorBlock, channelFnum,
                channelBlock, latchedFnumHigh, latchedCh3FnumHigh, ch3Fnum, ch3Block, feedback, algorithm,
                amSensitivity, pmSensitivity, feedbackHistory, operatorOut, csmKeyed, scalar};
    }

    @Override
    public void copyStateTo(FmDsp target) {
        if (!(target instanceof FastYm2612Dsp other)) {
            throw new IllegalArgumentException("target is not a FastYm2612Dsp");
        }
        int[][] from = arrays();
        int[][] to = other.arrays();
        for (int i = 0; i < from.length; i++) {
            System.arraycopy(from[i], 0, to[i], 0, from[i].length);
        }
    }

    @Override
    public FmDsp newInstance() {
        return new FastYm2612Dsp();
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof FastYm2612Dsp other)) {
            return false;
        }
        int[][] mine = arrays();
        int[][] theirs = other.arrays();
        for (int i = 0; i < mine.length; i++) {
            if (!Arrays.equals(mine[i], theirs[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        for (int[] array : arrays()) {
            result = 31 * result + Arrays.hashCode(array);
        }
        return result;
    }
}
