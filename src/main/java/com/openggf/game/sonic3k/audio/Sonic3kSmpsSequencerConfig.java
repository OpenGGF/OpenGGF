package com.openggf.game.sonic3k.audio;

import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.CoordFlagHandler;
import com.openggf.game.sonic3k.audio.smps.Sonic3kCoordFlagHandler;

import java.util.Collections;

/**
 * SMPS sequencer configuration for Sonic 3 &amp; Knuckles.
 *
 * <p>From DefDrv.txt (S&amp;K final):
 * <ul>
 *   <li>PtrFmt = Z80 (relativePointers=false)</li>
 *   <li>TempoMode = OVERFLOW</li>
 *   <li>Tempo1Tick = DOTEMPO (tempoOnFirstTick=true)</li>
 *   <li>ModAlgo = Z80 (applyModOnNote=true, halveModSteps=true)</li>
 *   <li>VolMode = BIT7</li>
 *   <li>NoteOnPrevent = HOLD</li>
 *   <li>DelayFreq = KEEP</li>
 *   <li>PSG envelope 80 = RESET</li>
 *   <li>FadeOutSteps = 0x28, FadeOutDelay = 6</li>
 *   <li>FadeInSteps = 0x40, FadeInDelay = 2</li>
 *   <li>FMChnOrder = 16 0 1 2 4 5 6 (same as S2)</li>
 * </ul>
 *
 * <p>S3K has no speed-up tempo table; speed shoes use frame multiplier instead
 * (Z80 RAM 0x1C08). The speed-up tempos map is empty.
 */
public final class Sonic3kSmpsSequencerConfig {

    /** Tempo modulo base — same across all games, references shared default. */
    public static final int TEMPO_MOD_BASE = SmpsSequencerConfig.DEFAULT_TEMPO_MOD_BASE;

    /** FM channel order — same across all games, references shared default. */
    public static final int[] FM_CHANNEL_ORDER = SmpsSequencerConfig.DEFAULT_FM_CHANNEL_ORDER;

    /** PSG channel order — same across all games, references shared default. */
    public static final int[] PSG_CHANNEL_ORDER = SmpsSequencerConfig.DEFAULT_PSG_CHANNEL_ORDER;

    /** Pre-built sequencer config instance for S3K. */
    public static final SmpsSequencerConfig CONFIG;

    static {
        CONFIG = create(new Sonic3kCoordFlagHandler());
    }

    public static SmpsSequencerConfig create(CoordFlagHandler coordFlagHandler) {
        return new SmpsSequencerConfig.Builder()
                .speedUpTempos(Collections.emptyMap())
                .tempoModBase(TEMPO_MOD_BASE)
                .fmChannelOrder(FM_CHANNEL_ORDER)
                .psgChannelOrder(PSG_CHANNEL_ORDER)
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                .palUpdateMode(SmpsSequencerConfig.PalUpdateMode.EXTRA_FULL)
                .applyModOnNote(true)       // ModAlgo = Z80
                .halveModSteps(true)        // Z80 driver halves mod steps (srl a)
                .relativePointers(false)    // PtrFmt = Z80 (absolute addresses)
                .tempoOnFirstTick(true)     // Tempo1Tick = DOTEMPO
                // Preserve the existing S3K Z80 rest path; the S2 oracle's
                // post-rest envelope step is selected independently.
                .advancePsgEnvelopeOnRest(false)
                .writeFmPanOnNote(false)
                .dacNoteKeysOffFm6AndRestoresFm3(true)
                .enableDacOnSequencerStart(false)
                .psgFrequencyHighByteNibbleSwap(true)
                .fmVoiceWriteProfile(SmpsSequencerConfig.FmVoiceWriteProfile.S3K_Z80)
                .volMode(SmpsSequencerConfig.VolMode.BIT7)
                .psgEnvCmd80(SmpsSequencerConfig.PsgEnvCmd80.RESET)
                .noteOnPrevent(SmpsSequencerConfig.NoteOnPrevent.HOLD)
                .delayFreq(SmpsSequencerConfig.DelayFreq.KEEP)
                .coordFlagHandler(coordFlagHandler)
                .modAlgo(SmpsSequencerConfig.ModAlgo.MOD_Z80)
                // zDoModulation is an ordinary subroutine here, so every one of
                // its returns falls through to the frequency send
                // (Sound/Z80 Sound Driver.asm:791-799, :4077-4090). S1 and S2
                // discard the return address instead and skip it.
                .noteGoingFreqSend(
                        SmpsSequencerConfig.NoteGoingFreqSend.EVERY_PASS)
                // zUpdatePSGTrack latches the frequency before it reads the
                // volume envelope (Sound/Z80 Sound Driver.asm:4077-4110); S1
                // and S2 run their volume effects first instead.
                .psgNoteGoingOrder(
                        SmpsSequencerConfig.PsgNoteGoingOrder.FREQUENCY_THEN_VOLUME)
                // zDoVolEnv rests the track on 81h and 83h without silencing
                // it (Sound/Z80 Sound Driver.asm:4169-4175, :4187-4208).
                .psgEnvRestCmd(
                        SmpsSequencerConfig.PsgEnvRestCmd.Z80_81_AND_83)
                // zDoModulation tests only ModulationCtrl, never the rest bit
                // (Sound/Z80 Sound Driver.asm:1277-1283), so a resting track
                // keeps stepping. Only S2 returns early at rest.
                .stepModulationAtRest(true)
                // zFinishTrackUpdate zeroes ModEnvIndex and ModEnvSens, which
                // alias ModulationSpeed and ModulationValLow in this layout
                // (Sound/Z80 Sound Driver.asm:1055-1069, :76-92).
                .noteResetAliasesModulationState(true)
                // zUpdateFMorPSGTrack .note_going returns on the rest bit
                // before it does anything (Sound/Z80 Sound Driver.asm:781-783).
                .fmNoteGoingReturnsAtRest(true)
                // zFadeOutMusic falls through zHaltDACPSG, which halts the
                // PSG tracks as well as FM6/DAC (Sound/Z80 Sound
                // Driver.asm:2307-2325).
                .fadeOutHalt(SmpsSequencerConfig.FadeOutHalt.DAC_AND_PSG)
                // zDoMusicFadeOut decrements the delay before testing it
                // (Sound/Z80 Sound Driver.asm:2337-2343).
                .fadeDelayCadence(
                        SmpsSequencerConfig.FadeDelayCadence.DECREMENT_THEN_TEST)
                // TempoWait sits in zUpdateEverything, ahead of zUpdateMusic's
                // zFillSoundQueue (Sound/Z80 Sound Driver.asm:653-701,
                // :2607-2621), so a load service does not accumulate for the
                // song it loads.
                .tempoWaitPrecedesRequest(true)
                // zSilencePSGChannel writes the track's own tone channel
                // first and adds the noise byte only for a noise track
                // (Sound/Z80 Sound Driver.asm:4226-4245).
                .psgSilenceShape(
                        SmpsSequencerConfig.PsgSilenceShape.TONE_THEN_NOISE)
                .noteFillTail(SmpsSequencerConfig.NoteFillTail.S3K_SPLIT)
                .fadeOutDelay(6)            // FadeOutDelay = 6
                .fadeOutSteps(0x28)         // FadeOutSteps = 28h
                .fadeInSteps(0x40)          // FadeInSteps = 40h
                .fadeInDelay(2)             // FadeInDelay = 2
                .build();
    }

    private Sonic3kSmpsSequencerConfig() {
    }
}
