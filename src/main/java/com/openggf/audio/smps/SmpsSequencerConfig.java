package com.openggf.audio.smps;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SmpsSequencerConfig {

    public enum TempoMode {
        /** S3K: carry extends music durations before the mandatory per-VInt track service. */
        OVERFLOW,
        /** S2: no carry extends music durations before the mandatory per-VInt track service. */
        OVERFLOW2,
        /** S1: countdown from tempo; when 0, extend all track durations by 1. Always tick. */
        TIMEOUT
    }

    public enum PalServicePolicy {
        /** No driver-side PAL compensation (Sonic 1). */
        NONE,
        /** Sonic 2: one extra music-only service every fifth PAL VInt. */
        EXTRA_MUSIC_EVERY_FIFTH,
        /** Locked-on S&K: repeat the complete driver update every sixth PAL VInt. */
        FULL_DRIVER_REPEAT_EVERY_SIXTH,
        /** Legacy generic 1.2 tempo scaling for non-production/custom profiles. */
        LEGACY_TEMPO_SCALE
    }

    public enum TempoPhasePolicy {
        /** S1: tempo and speed changes reload the live timeout. */
        RESET_TO_EFFECTIVE_TEMPO,
        /** S2/S3K: tempo and speed changes preserve accumulator phase. */
        PRESERVE
    }

    /** Request-wide SFX priority behavior owned by the original sound driver. */
    public enum SfxPriorityPolicy {
        /** Sonic 1/2: one global stored priority gates the complete request. */
        GLOBAL_LATCH,
        /** Sonic 3 & Knuckles: no SFX priority table or global latch. */
        NONE
    }

    /** Order of the shared driver's music and SFX track services per VInt. */
    public enum DriverServiceOrder {
        MUSIC_THEN_SFX,
        SFX_THEN_MUSIC
    }

    /** How carrier operators are determined for volume scaling. */
    public enum VolMode {
        /** S1/S2: carrier mask derived from algorithm number via ALGO_OUT_MASK table. */
        ALGO,
        /** S3K: carrier operators identified by bit 7 set in the TL byte of the voice data. */
        BIT7
    }

    /** Behavior of PSG envelope command byte 0x80. */
    public enum PsgEnvCmd80 {
        /** S1/S2: hold the envelope at current level (stop advancing). */
        HOLD,
        /** S3K: reset the envelope index to 0 (loop from start). */
        RESET
    }

    /** How note-on is prevented during ties/holds. */
    public enum NoteOnPrevent {
        /** S1/S2: prevented when note is REST (0x80). */
        REST,
        /** S3K: prevented when HOLD flag is set. */
        HOLD
    }

    /** What happens to frequency during rests/delays. */
    public enum DelayFreq {
        /** S1/S2: frequency is reset on rest. */
        RESET,
        /** S3K: frequency persists through rests. */
        KEEP
    }

    /** Modulation stepping algorithm. */
    public enum ModAlgo {
        /** S1/S2 (MODALGO_68K): pre-check step counter, then decrement. Reload from raw data. */
        MOD_68K,
        /** S3K (MODALGO_Z80): post-decrement with 8-bit wrap, then check. Reload from raw data. */
        MOD_Z80
    }

    /** How an FM channel is prepared when an SFX first takes it from music. */
    public enum FmSfxTakeoverMode {
        /** Legacy engine behavior: clear internal chip state and inject a key-off. */
        FORCE_RESET,
        /** S3K fix_sndbugs=0: key off and clear all four SSG-EG registers. */
        KEY_OFF_CLEAR_SSG_EG,
        /** Shipped-driver behavior: let the SFX bytecode perform all visible writes. */
        REGISTER_SEQUENCE
    }

    /** Hardware writes performed when an ordinary FM SFX track ends. */
    public enum FmSfxReleaseMode {
        /** Legacy behavior: force maximum release/TL before restoring music. */
        FORCE_SILENCE_THEN_RESTORE,
        /** S3K fix_sndbugs=0 cfStopTrack: key off, then restore music directly. */
        RESTORE_MUSIC_DIRECTLY
    }

    public enum PsgSfxReleaseMode {
        /** S1/S2: restored music track stays at rest until its next note. */
        REST_UNTIL_NEXT_NOTE,
        /** S3K/custom: restore the live music PSG state immediately. */
        RESTORE_LIVE_STATE
    }

    public enum FadeOutChannelPolicy {
        /** S1/S2: halt DAC immediately, then fade FM and PSG. */
        FADE_FM_AND_PSG,
        /** S3K: halt DAC and every PSG track immediately, then fade FM only. */
        HALT_DAC_AND_PSG_FADE_FM
    }

    public enum MusicOverrideSpeedPolicy {
        /** S1/S2: the 1-up load observes the live speed-tempo flag. */
        INHERIT_CURRENT,
        /** S3K: save the speed timeout and run the 1-up at normal speed. */
        NORMAL_DURING_OVERRIDE
    }

    public enum MusicOverrideRestorePolicy {
        IMMEDIATE,
        /** Restore saved tracks through the driver's fade-in routine. */
        DRIVER_FADE_IN
    }

    public enum MusicOverridePriorityPolicy {
        /** S1 clears the global SFX priority before backing up music state. */
        CLEAR_BEFORE_SAVE,
        /** S2 FixDriverBugs=0 restores the stale priority saved before clear. */
        PRESERVE_SAVED_LATCH
    }

    public enum MusicOverrideSfxReleasePolicy {
        /** S1/S2 block new SFX until the restore fade completes. */
        AFTER_FADE_IN,
        /** S3K permits SFX on the driver cycle after restore begins. */
        ON_RESTORE
    }

    public enum MusicOverrideDacRestorePolicy {
        /** Restore the displaced driver's preserved YM2612 DAC mode. */
        RESTORE_SAVED_CHIP,
        /** S1 FixBugs=0 omits $2B and leaves the jingle's DAC mode active. */
        PRESERVE_OVERRIDE_DAC_MODE
    }

    public enum FadeInChannelPolicy {
        ALL_NON_DAC,
        /** S3K zDoMusicFadeIn changes FM volume only. */
        FM_ONLY
    }

    /** Hardware mute/restore sequence selected by the retail sound driver. */
    public enum PausePolicy {
        /** Compatibility default for custom data without a driver contract. */
        NONE,
        /** S1 68k PauseMusic: pan/key-off FM1-6 and restore pan only. */
        S1_PAN_KEYOFF,
        /** S2 zPauseMusic: destructive FM silence and voice reload. */
        S2_SILENCE_RELOAD,
        /** S3K zPauseAudio: preserve FM6/DAC and use the shipped resume loop. */
        S3K_FM1_TO_5
    }

    /** Driver-owned request transform applied before an SFX starts. */
    public enum SfxRequestTransformPolicy {
        NONE,
        /** Sonic 2's shipped E0 spindash-rev semitone ladder and timeout. */
        SONIC2_SPINDASH_REV
    }

    /** Exact shipped-driver sequence used to upload a 25-byte FM voice. */
    public enum FmVoiceWriteProfile {
        /** Sonic 1's 68k SetVoice routine. */
        S1_68K,
        /** Sonic 2's Z80 zSetVoice routine. */
        S2_Z80,
        /** Sonic 3 & Knuckles' Z80 zSendFMInstrument routine. */
        S3K_Z80
    }

    // -----------------------------------------------------------------------
    // Default constants shared across all three games (S1, S2, S3K)
    // -----------------------------------------------------------------------

    /** Default tempo modulation base (0x100). Same for S1, S2, and S3K. */
    public static final int DEFAULT_TEMPO_MOD_BASE = 0x100;

    /** Default FM channel order: DAC(0x16), FM1-FM6. Same for S1, S2, and S3K. */
    public static final int[] DEFAULT_FM_CHANNEL_ORDER = { 0x16, 0, 1, 2, 4, 5, 6 };

    /** Default PSG channel order: PSG1(0x80), PSG2(0xA0), PSG3(0xC0). Same for S1, S2, and S3K. */
    public static final int[] DEFAULT_PSG_CHANNEL_ORDER = { 0x80, 0xA0, 0xC0 };

    private final Map<Integer, Integer> speedUpTempos;
    private final int tempoModBase;
    private final int[] fmChannelOrder;
    private final int[] psgChannelOrder;
    private final TempoMode tempoMode;
    private final PalServicePolicy palServicePolicy;
    private final TempoPhasePolicy tempoPhasePolicy;
    private final SfxPriorityPolicy sfxPriorityPolicy;
    private final DriverServiceOrder driverServiceOrder;
    private final Map<Integer, Integer> coordFlagParamOverrides;
    private final boolean applyModOnNote;
    private final boolean halveModSteps;
    private final Set<Integer> extraTrkEndFlags;
    private final boolean relativePointers; // S1: true (68k PC-relative), S2: false (Z80 absolute)
    private final boolean direct68kDriver;
    private final FmSfxTakeoverMode fmSfxTakeoverMode;
    private final FmSfxReleaseMode fmSfxReleaseMode;
    private final PsgSfxReleaseMode psgSfxReleaseMode;
    private final FadeOutChannelPolicy fadeOutChannelPolicy;
    private final MusicOverrideSpeedPolicy musicOverrideSpeedPolicy;
    private final MusicOverrideRestorePolicy musicOverrideRestorePolicy;
    private final MusicOverridePriorityPolicy musicOverridePriorityPolicy;
    private final MusicOverrideSfxReleasePolicy musicOverrideSfxReleasePolicy;
    private final MusicOverrideDacRestorePolicy musicOverrideDacRestorePolicy;
    private final FadeInChannelPolicy fadeInChannelPolicy;
    private final PausePolicy pausePolicy;
    private final SfxRequestTransformPolicy sfxRequestTransformPolicy;
    private final boolean fadeOutClearsSpeedShoes;
    private final boolean fadeOutStopsSfxImmediately;
    private final FmVoiceWriteProfile fmVoiceWriteProfile;

    // --- S3K-specific config fields ---
    private final VolMode volMode;
    private final PsgEnvCmd80 psgEnvCmd80;
    private final NoteOnPrevent noteOnPrevent;
    private final DelayFreq delayFreq;
    private final CoordFlagHandler coordFlagHandler;
    private final ModAlgo modAlgo;
    private final int fadeOutDelay;
    private final int fadeOutSteps;
    private final int fadeInSteps;
    private final int fadeInDelay;

    /**
     * Private constructor used by the Builder. All fields are set here.
     */
    private SmpsSequencerConfig(Builder b) {
        this.speedUpTempos = Collections.unmodifiableMap(new HashMap<>(b.speedUpTempos));
        this.tempoModBase = b.tempoModBase;
        this.fmChannelOrder = Arrays.copyOf(b.fmChannelOrder, b.fmChannelOrder.length);
        this.psgChannelOrder = Arrays.copyOf(b.psgChannelOrder, b.psgChannelOrder.length);
        this.tempoMode = b.tempoMode;
        this.palServicePolicy = b.palServicePolicy;
        this.tempoPhasePolicy = b.tempoPhasePolicy;
        this.sfxPriorityPolicy = b.sfxPriorityPolicy;
        this.driverServiceOrder = b.driverServiceOrder;
        this.coordFlagParamOverrides = (b.coordFlagParamOverrides != null)
                ? Collections.unmodifiableMap(new HashMap<>(b.coordFlagParamOverrides))
                : Collections.emptyMap();
        this.applyModOnNote = b.applyModOnNote;
        this.halveModSteps = b.halveModSteps;
        this.extraTrkEndFlags = (b.extraTrkEndFlags != null)
                ? Collections.unmodifiableSet(b.extraTrkEndFlags)
                : Collections.emptySet();
        this.relativePointers = b.relativePointers;
        this.direct68kDriver = b.direct68kDriver;
        this.fmSfxTakeoverMode = b.fmSfxTakeoverMode;
        this.fmSfxReleaseMode = b.fmSfxReleaseMode;
        this.psgSfxReleaseMode = b.psgSfxReleaseMode;
        this.fadeOutChannelPolicy = b.fadeOutChannelPolicy;
        this.musicOverrideSpeedPolicy = b.musicOverrideSpeedPolicy;
        this.musicOverrideRestorePolicy = b.musicOverrideRestorePolicy;
        this.musicOverridePriorityPolicy = b.musicOverridePriorityPolicy;
        this.musicOverrideSfxReleasePolicy = b.musicOverrideSfxReleasePolicy;
        this.musicOverrideDacRestorePolicy = b.musicOverrideDacRestorePolicy;
        this.fadeInChannelPolicy = b.fadeInChannelPolicy;
        this.pausePolicy = b.pausePolicy;
        this.sfxRequestTransformPolicy = b.sfxRequestTransformPolicy;
        this.fadeOutClearsSpeedShoes = b.fadeOutClearsSpeedShoes;
        this.fadeOutStopsSfxImmediately = b.fadeOutStopsSfxImmediately;
        this.fmVoiceWriteProfile = b.fmVoiceWriteProfile;
        this.volMode = b.volMode;
        this.psgEnvCmd80 = b.psgEnvCmd80;
        this.noteOnPrevent = b.noteOnPrevent;
        this.delayFreq = b.delayFreq;
        this.coordFlagHandler = b.coordFlagHandler;
        this.modAlgo = b.modAlgo;
        this.fadeOutDelay = b.fadeOutDelay;
        this.fadeOutSteps = b.fadeOutSteps;
        this.fadeInSteps = b.fadeInSteps;
        this.fadeInDelay = b.fadeInDelay;
    }

    public Map<Integer, Integer> getSpeedUpTempos() {
        return speedUpTempos;
    }

    public int getTempoModBase() {
        return tempoModBase;
    }

    public int[] getFmChannelOrder() {
        return Arrays.copyOf(fmChannelOrder, fmChannelOrder.length);
    }

    public int[] getPsgChannelOrder() {
        return Arrays.copyOf(psgChannelOrder, psgChannelOrder.length);
    }

    int fmChannelCount() {
        return fmChannelOrder.length;
    }

    int fmChannelAt(int index) {
        return fmChannelOrder[index];
    }

    int psgChannelCount() {
        return psgChannelOrder.length;
    }

    int psgChannelAt(int index) {
        return psgChannelOrder[index];
    }

    public TempoMode getTempoMode() {
        return tempoMode;
    }

    public PalServicePolicy getPalServicePolicy() {
        return palServicePolicy;
    }

    public TempoPhasePolicy getTempoPhasePolicy() {
        return tempoPhasePolicy;
    }

    public SfxPriorityPolicy getSfxPriorityPolicy() {
        return sfxPriorityPolicy;
    }

    public DriverServiceOrder getDriverServiceOrder() {
        return driverServiceOrder;
    }

    /**
     * Returns overrides for coordination flag parameter lengths.
     * Keys are flag commands (0xE0-0xFF), values are the param length for that flag.
     * Only flags that differ from the default S2 table need to be present.
     */
    public Map<Integer, Integer> getCoordFlagParamOverrides() {
        return coordFlagParamOverrides;
    }

    /**
     * Whether to apply modulation during note start (playNote).
     * S2 (ModAlgo 68k_a): true. S1 (ModAlgo 68k): false.
     */
    public boolean isApplyModOnNote() {
        return applyModOnNote;
    }

    /**
     * Whether to halve the modulation step count on load.
     * Both the S1 68k and S2 Z80 drivers shift the raw count once.
     */
    public boolean isHalveModSteps() {
        return halveModSteps;
    }

    /**
     * Returns coordination flag commands that should stop the track (TRK_END).
     * S1: includes 0xEE. S2: empty (0xEE is IGNORE/no-op).
     */
    public Set<Integer> getExtraTrkEndFlags() {
        return extraTrkEndFlags;
    }

    /**
     * Whether in-stream pointers (F6 Jump, F7 Loop, F8 Call) use PC-relative addressing.
     * S1 (68k): true — pointer value is signed offset from (ptrAddr + 1).
     * S2 (Z80): false — pointer value is absolute Z80 address, resolved via relocate().
     */
    public boolean isRelativePointers() {
        return relativePointers;
    }

    /** Whether playback follows the direct 68k chip-write/update contract. */
    public boolean isDirect68kDriver() {
        return direct68kDriver;
    }

    public FmSfxTakeoverMode getFmSfxTakeoverMode() {
        return fmSfxTakeoverMode;
    }

    public FmSfxReleaseMode getFmSfxReleaseMode() {
        return fmSfxReleaseMode;
    }

    public PsgSfxReleaseMode getPsgSfxReleaseMode() {
        return psgSfxReleaseMode;
    }

    public FadeOutChannelPolicy getFadeOutChannelPolicy() {
        return fadeOutChannelPolicy;
    }

    public boolean isFadeOutClearsSpeedShoes() {
        return fadeOutClearsSpeedShoes;
    }

    public boolean isFadeOutStopsSfxImmediately() {
        return fadeOutStopsSfxImmediately;
    }

    public FmVoiceWriteProfile getFmVoiceWriteProfile() {
        return fmVoiceWriteProfile;
    }

    /** Volume mode: ALGO (S1/S2) or BIT7 (S3K). */
    public VolMode getVolMode() {
        return volMode;
    }

    /** PSG envelope 0x80 command behavior: HOLD (S1/S2) or RESET (S3K). */
    public PsgEnvCmd80 getPsgEnvCmd80() {
        return psgEnvCmd80;
    }

    /** Note-on prevention mode: REST (S1/S2) or HOLD (S3K). */
    public NoteOnPrevent getNoteOnPrevent() {
        return noteOnPrevent;
    }

    /** Delay frequency behavior: RESET (S1/S2) or KEEP (S3K). */
    public DelayFreq getDelayFreq() {
        return delayFreq;
    }

    /** Game-specific coordination flag handler, or null for default S2 handling. */
    public CoordFlagHandler getCoordFlagHandler() {
        return coordFlagHandler;
    }

    /** Modulation stepping algorithm: MOD_68K (S1/S2) or MOD_Z80 (S3K). */
    public ModAlgo getModAlgo() {
        return modAlgo;
    }

    /** Fade-out inter-step delay in frames. S1/S2: 3, S3K: 6. */
    public int getFadeOutDelay() {
        return fadeOutDelay;
    }

    /** Fade-out total step count. S1/S2: 0x28, S3K: 0x28. */
    public int getFadeOutSteps() {
        return fadeOutSteps;
    }

    /** Fade-in total step count. S1/S2: 0x28, S3K: 0x40. */
    public int getFadeInSteps() {
        return fadeInSteps;
    }

    /** Fade-in inter-step delay in frames. S1/S2: 2, S3K: 2. */
    public int getFadeInDelay() {
        return fadeInDelay;
    }

    public MusicOverrideSpeedPolicy getMusicOverrideSpeedPolicy() {
        return musicOverrideSpeedPolicy;
    }

    public MusicOverrideRestorePolicy getMusicOverrideRestorePolicy() {
        return musicOverrideRestorePolicy;
    }

    public MusicOverridePriorityPolicy getMusicOverridePriorityPolicy() {
        return musicOverridePriorityPolicy;
    }

    public MusicOverrideSfxReleasePolicy getMusicOverrideSfxReleasePolicy() {
        return musicOverrideSfxReleasePolicy;
    }

    public MusicOverrideDacRestorePolicy getMusicOverrideDacRestorePolicy() {
        return musicOverrideDacRestorePolicy;
    }

    public FadeInChannelPolicy getFadeInChannelPolicy() {
        return fadeInChannelPolicy;
    }

    public PausePolicy getPausePolicy() {
        return pausePolicy;
    }

    public SfxRequestTransformPolicy getSfxRequestTransformPolicy() {
        return sfxRequestTransformPolicy;
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    /**
     * Builder with legacy-compatible defaults. Production game configs must
     * select their explicit scheduler, PAL-service, and tempo-phase policies.
     */
    public static final class Builder {
        // Required (defaults reference shared constants)
        private Map<Integer, Integer> speedUpTempos = Collections.emptyMap();
        private int tempoModBase = DEFAULT_TEMPO_MOD_BASE;
        private int[] fmChannelOrder = DEFAULT_FM_CHANNEL_ORDER;
        private int[] psgChannelOrder = DEFAULT_PSG_CHANNEL_ORDER;

        // Legacy-compatible defaults; production profiles override policies.
        private TempoMode tempoMode = TempoMode.OVERFLOW2;
        private PalServicePolicy palServicePolicy = PalServicePolicy.LEGACY_TEMPO_SCALE;
        private TempoPhasePolicy tempoPhasePolicy = TempoPhasePolicy.PRESERVE;
        private SfxPriorityPolicy sfxPriorityPolicy = SfxPriorityPolicy.NONE;
        private DriverServiceOrder driverServiceOrder =
                DriverServiceOrder.MUSIC_THEN_SFX;
        private Map<Integer, Integer> coordFlagParamOverrides = null;
        private boolean applyModOnNote = true;
        private boolean halveModSteps = true;
        private Set<Integer> extraTrkEndFlags = null;
        private boolean relativePointers = false;
        private boolean direct68kDriver = false;
        private FmSfxTakeoverMode fmSfxTakeoverMode = FmSfxTakeoverMode.FORCE_RESET;
        private FmSfxReleaseMode fmSfxReleaseMode =
                FmSfxReleaseMode.FORCE_SILENCE_THEN_RESTORE;
        private PsgSfxReleaseMode psgSfxReleaseMode =
                PsgSfxReleaseMode.RESTORE_LIVE_STATE;
        private FadeOutChannelPolicy fadeOutChannelPolicy =
                FadeOutChannelPolicy.FADE_FM_AND_PSG;
        private MusicOverrideSpeedPolicy musicOverrideSpeedPolicy =
                MusicOverrideSpeedPolicy.INHERIT_CURRENT;
        private MusicOverrideRestorePolicy musicOverrideRestorePolicy =
                MusicOverrideRestorePolicy.IMMEDIATE;
        private MusicOverridePriorityPolicy musicOverridePriorityPolicy =
                MusicOverridePriorityPolicy.CLEAR_BEFORE_SAVE;
        private MusicOverrideSfxReleasePolicy musicOverrideSfxReleasePolicy =
                MusicOverrideSfxReleasePolicy.AFTER_FADE_IN;
        private MusicOverrideDacRestorePolicy musicOverrideDacRestorePolicy =
                MusicOverrideDacRestorePolicy.RESTORE_SAVED_CHIP;
        private FadeInChannelPolicy fadeInChannelPolicy =
                FadeInChannelPolicy.ALL_NON_DAC;
        private PausePolicy pausePolicy = PausePolicy.NONE;
        private SfxRequestTransformPolicy sfxRequestTransformPolicy =
                SfxRequestTransformPolicy.NONE;
        private boolean fadeOutClearsSpeedShoes;
        private boolean fadeOutStopsSfxImmediately;
        private FmVoiceWriteProfile fmVoiceWriteProfile = FmVoiceWriteProfile.S2_Z80;

        // S3K-specific defaults (S2 compatible)
        private VolMode volMode = VolMode.ALGO;
        private PsgEnvCmd80 psgEnvCmd80 = PsgEnvCmd80.HOLD;
        private NoteOnPrevent noteOnPrevent = NoteOnPrevent.REST;
        private DelayFreq delayFreq = DelayFreq.RESET;
        private CoordFlagHandler coordFlagHandler = null;
        private ModAlgo modAlgo = ModAlgo.MOD_68K;
        private int fadeOutDelay = 3;
        private int fadeOutSteps = 0x28;
        private int fadeInSteps = 0x28;
        private int fadeInDelay = 2;

        public Builder speedUpTempos(Map<Integer, Integer> val) { speedUpTempos = val; return this; }
        public Builder tempoModBase(int val) { tempoModBase = val; return this; }
        public Builder fmChannelOrder(int[] val) { fmChannelOrder = val; return this; }
        public Builder psgChannelOrder(int[] val) { psgChannelOrder = val; return this; }
        public Builder tempoMode(TempoMode val) { tempoMode = val; return this; }
        public Builder palServicePolicy(PalServicePolicy val) { palServicePolicy = val; return this; }
        public Builder tempoPhasePolicy(TempoPhasePolicy val) { tempoPhasePolicy = val; return this; }
        public Builder sfxPriorityPolicy(SfxPriorityPolicy val) { sfxPriorityPolicy = val; return this; }
        public Builder driverServiceOrder(DriverServiceOrder val) { driverServiceOrder = val; return this; }
        public Builder coordFlagParamOverrides(Map<Integer, Integer> val) { coordFlagParamOverrides = val; return this; }
        public Builder applyModOnNote(boolean val) { applyModOnNote = val; return this; }
        public Builder halveModSteps(boolean val) { halveModSteps = val; return this; }
        public Builder extraTrkEndFlags(Set<Integer> val) { extraTrkEndFlags = val; return this; }
        public Builder relativePointers(boolean val) { relativePointers = val; return this; }
        public Builder direct68kDriver(boolean val) { direct68kDriver = val; return this; }
        public Builder fmSfxTakeoverMode(FmSfxTakeoverMode val) { fmSfxTakeoverMode = val; return this; }
        public Builder fmSfxReleaseMode(FmSfxReleaseMode val) { fmSfxReleaseMode = val; return this; }
        public Builder psgSfxReleaseMode(PsgSfxReleaseMode val) { psgSfxReleaseMode = val; return this; }
        public Builder fadeOutChannelPolicy(FadeOutChannelPolicy val) { fadeOutChannelPolicy = val; return this; }
        public Builder musicOverrideSpeedPolicy(MusicOverrideSpeedPolicy val) { musicOverrideSpeedPolicy = val; return this; }
        public Builder musicOverrideRestorePolicy(MusicOverrideRestorePolicy val) { musicOverrideRestorePolicy = val; return this; }
        public Builder musicOverridePriorityPolicy(MusicOverridePriorityPolicy val) { musicOverridePriorityPolicy = val; return this; }
        public Builder musicOverrideSfxReleasePolicy(MusicOverrideSfxReleasePolicy val) { musicOverrideSfxReleasePolicy = val; return this; }
        public Builder musicOverrideDacRestorePolicy(MusicOverrideDacRestorePolicy val) { musicOverrideDacRestorePolicy = val; return this; }
        public Builder fadeInChannelPolicy(FadeInChannelPolicy val) { fadeInChannelPolicy = val; return this; }
        public Builder pausePolicy(PausePolicy val) { pausePolicy = val; return this; }
        public Builder sfxRequestTransformPolicy(SfxRequestTransformPolicy val) { sfxRequestTransformPolicy = val; return this; }
        public Builder fadeOutClearsSpeedShoes(boolean val) { fadeOutClearsSpeedShoes = val; return this; }
        public Builder fadeOutStopsSfxImmediately(boolean val) { fadeOutStopsSfxImmediately = val; return this; }
        public Builder fmVoiceWriteProfile(FmVoiceWriteProfile val) { fmVoiceWriteProfile = val; return this; }
        public Builder volMode(VolMode val) { volMode = val; return this; }
        public Builder psgEnvCmd80(PsgEnvCmd80 val) { psgEnvCmd80 = val; return this; }
        public Builder noteOnPrevent(NoteOnPrevent val) { noteOnPrevent = val; return this; }
        public Builder delayFreq(DelayFreq val) { delayFreq = val; return this; }
        public Builder coordFlagHandler(CoordFlagHandler val) { coordFlagHandler = val; return this; }
        public Builder modAlgo(ModAlgo val) { modAlgo = val; return this; }
        public Builder fadeOutDelay(int val) { fadeOutDelay = val; return this; }
        public Builder fadeOutSteps(int val) { fadeOutSteps = val; return this; }
        public Builder fadeInSteps(int val) { fadeInSteps = val; return this; }
        public Builder fadeInDelay(int val) { fadeInDelay = val; return this; }

        public SmpsSequencerConfig build() {
            Objects.requireNonNull(speedUpTempos, "speedUpTempos");
            Objects.requireNonNull(fmChannelOrder, "fmChannelOrder");
            Objects.requireNonNull(psgChannelOrder, "psgChannelOrder");
            Objects.requireNonNull(tempoMode, "tempoMode");
            Objects.requireNonNull(palServicePolicy, "palServicePolicy");
            Objects.requireNonNull(tempoPhasePolicy, "tempoPhasePolicy");
            Objects.requireNonNull(sfxPriorityPolicy, "sfxPriorityPolicy");
            Objects.requireNonNull(driverServiceOrder, "driverServiceOrder");
            Objects.requireNonNull(fmSfxTakeoverMode, "fmSfxTakeoverMode");
            Objects.requireNonNull(fmSfxReleaseMode, "fmSfxReleaseMode");
            Objects.requireNonNull(psgSfxReleaseMode, "psgSfxReleaseMode");
            Objects.requireNonNull(fadeOutChannelPolicy, "fadeOutChannelPolicy");
            Objects.requireNonNull(musicOverrideSpeedPolicy, "musicOverrideSpeedPolicy");
            Objects.requireNonNull(musicOverrideRestorePolicy, "musicOverrideRestorePolicy");
            Objects.requireNonNull(musicOverridePriorityPolicy, "musicOverridePriorityPolicy");
            Objects.requireNonNull(musicOverrideSfxReleasePolicy, "musicOverrideSfxReleasePolicy");
            Objects.requireNonNull(musicOverrideDacRestorePolicy, "musicOverrideDacRestorePolicy");
            Objects.requireNonNull(fadeInChannelPolicy, "fadeInChannelPolicy");
            Objects.requireNonNull(pausePolicy, "pausePolicy");
            Objects.requireNonNull(sfxRequestTransformPolicy, "sfxRequestTransformPolicy");
            Objects.requireNonNull(fmVoiceWriteProfile, "fmVoiceWriteProfile");
            return new SmpsSequencerConfig(this);
        }
    }
}
