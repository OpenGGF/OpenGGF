package com.openggf.game.soniccd.audio;

import com.openggf.audio.smps.CoordFlagContext;
import com.openggf.audio.smps.CoordFlagHandler;
import com.openggf.audio.smps.SmpsProgramView;
import com.openggf.audio.smps.SmpsSequencer;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Track commands of the Sonic CD FM sound-effect driver.
 *
 * <p>The command table {@code TrackCommand.Commands} (FM/_Driver.asm:1146-1178)
 * numbers its commands differently from the Sonic 1, 2 and 3&amp;K drivers, so every byte from
 * E0 to FF is dispatched here. Operands are counted the way the driver advances {@code de}:
 * {@code TrackCommand} loads the first operand and its {@code .Done} return steps past one byte
 * (FM/_Driver.asm:1130-1143), so a handler that ends with {@code dec de} takes none and one that leaves
 * {@code de} on its last operand takes that many.
 *
 * <p>The commands the 80 shipped USA effects reach are modelled from their handlers:
 * E0, E1, E2, E6, E7, EB, EF, F0, F1, F2, F3, F6, F7, F8, F9 and FB
 * ({@code TestCdFmSoundEffects} walks every effect from the disc bank). The others only keep
 * the stream aligned and stop the track: no shipped effect reaches them, so they are never
 * presented as native behaviour.
 */
public final class CdFmCoordFlagHandler implements CoordFlagHandler {
    private static final Logger LOGGER = Logger.getLogger(CdFmCoordFlagHandler.class.getName());
    /** {@code FM_RINGL} (FM/_Sound IDs.inc:34). */
    static final int RING_LEFT = 0xA8;

    private final CdFmDriverState driver;

    public CdFmCoordFlagHandler(CdFmDriverState driver) {
        this.driver = Objects.requireNonNull(driver, "driver");
    }

    @Override
    public boolean handleFlag(CoordFlagContext ctx, SmpsSequencer.Track t, int cmd) {
        SmpsProgramView program = ctx.programView();
        switch (cmd) {
            case 0xE0 -> panning(ctx, t, program.dataByteAt(t.pos++) & 0xFF);
            // TrkCmd_PortaSpeed (FM/_Driver.asm:1322-1324). With portamento off UpdatePortamento adds the
            // speed to every frequency it returns without storing it (FM/_Driver.asm:567-616), which is a
            // detune. Its block-boundary carry needs a frequency outside 283h..508h; the one
            // shipped use (B7, +0Ah) stays inside FreqTable's 284h..4C0h.
            case 0xE1 -> t.detune = program.dataByteAt(t.pos++);
            // TrkCmd_CommFlag stores commFlag (FM/_Driver.asm:1335-1337); nothing reads it.
            case 0xE2 -> ctx.setCommData(program.dataByteAt(t.pos++) & 0xFF);
            case 0xE6 -> {
                // TrkCmd_Volume: 8-bit add, then UpdateVolume writes (TL + volume) & 7Fh to
                // the bit-7-marked operators (FM/_Driver.asm:1597-1633). No clamp.
                t.volumeOffset = (t.volumeOffset + program.dataByteAt(t.pos++)) & 0xFF;
                ctx.refreshVolume(t);
            }
            // TrkCmd_Legato sets the legato flag that ParseTrackData clears at its next pass
            // (FM/_Driver.asm:1450-1454, :261-265): the next note neither keys off nor on and keeps its
            // vibrato and staccato (FM/_Driver.asm:377-391, :545-548), the shared no-attack tie.
            case 0xE7 -> t.tieNext = true;
            // TrkCmd_CondJump jumps while jumpCondition is zero (FM/_Driver.asm:1266-1272). Nothing writes
            // jumpCondition (FM/_Variables.inc:96) after StopSound clears it, so it always jumps.
            case 0xEB, 0xF6 -> jump(ctx, t);
            case 0xEF -> {
                // TrkCmd_Instrument: SetInstrumentMaxRel writes FFh to 80h+op first
                // (FM/_Driver.asm:1525-1535, :1066-1088), then UpdateInstrument sends the
                // 19h-byte voice (FM/_Driver.asm:732-756).
                int port = t.channelId < 3 ? 0 : 1;
                int channel = t.channelId % 3;
                for (int operator = 0; operator < 4; operator++) {
                    ctx.writeFm(port, 0x80 + operator * 4 + channel, 0xFF);
                }
                ctx.loadVoice(t, program.dataByteAt(t.pos++) & 0xFF);
            }
            case 0xF0 -> vibrato(t, program);
            // TrkCmd_VibDisable clears bit 7 of the vibrato mode (FM/_Driver.asm:1437-1441); UpdateVibrato
            // then returns on the zero mode (FM/_Driver.asm:470-473).
            case 0xF1 -> ctx.clearModulation(t);
            // TrkCmd_Stop (FM/_Driver.asm:1720-1734): the shared stop keys the channel off and reports the
            // stop, which clears curPriority in CdFmSoundRequestService.
            case 0xF2 -> {
                return false;
            }
            case 0xF3 -> swapRingChannel(ctx, t);
            case 0xF7 -> repeat(ctx, t, program);
            case 0xF8 -> call(ctx, t);
            case 0xF9 -> {
                // TrkCmd_Return pops the address of the call operand's high byte; .Done steps
                // past it (TrkCmd_Return, FM/_Driver.asm:1799-1811).
                if (t.returnSp > 0) {
                    t.pos = t.returnStack[--t.returnSp];
                } else {
                    stopUnreached(ctx, t, cmd);
                }
            }
            case 0xFB -> t.keyOffset = (byte) (t.keyOffset + program.dataByteAt(t.pos++));
            case 0xFF -> {
                int sub = program.dataByteAt(t.pos++) & 0xFF;
                t.pos += switch (sub) {
                    case 0x01, 0x02 -> 1;
                    case 0x06 -> 4;
                    default -> 0;
                };
                stopUnreached(ctx, t, 0xFF00 | sub);
            }
            default -> {
                t.pos += unreachedOperandBytes(cmd);
                stopUnreached(ctx, t, cmd);
            }
        }
        return true;
    }

    @Override
    public int flagParamLength(int cmd) {
        return switch (cmd) {
            case 0xE0, 0xE1, 0xE2, 0xE6, 0xE8, 0xEF, 0xFA, 0xFB, 0xFC, 0xFD -> 1;
            case 0xE9, 0xEB, 0xED, 0xEE, 0xF6, 0xF8 -> 2;
            case 0xE5 -> 3;
            case 0xF0, 0xF7, 0xFE -> 4;
            case 0xFF -> 1;
            default -> 0;
        };
    }

    /** UpdatePanAMSFMS keeps the old AMS/FMS bits and ORs the operand over them (FM/_Driver.asm:1557-1570). */
    private static void panning(CoordFlagContext ctx, SmpsSequencer.Track t, int value) {
        int combined = ((t.ams & 3) << 4 | (t.fms & 7)) | value;
        t.pan = combined & 0xC0;
        t.ams = (combined >> 4) & 3;
        t.fms = combined & 7;
        ctx.writeFm(t.channelId < 3 ? 0 : 1, 0xB4 + t.channelId % 3, combined);
    }

    /**
     * TrkCmd_Vibrato stores the parameter pointer and arms the mode (FM/_Driver.asm:1420-1428); InitVibrato
     * copies delay, speed and intensity and halves the step count at the next attacked note
     * (FM/_Driver.asm:433-461), the Z80 modulation the shared sequencer models.
     */
    private static void vibrato(SmpsSequencer.Track t, SmpsProgramView program) {
        t.modPendingDelayInit = program.dataByteAt(t.pos++) & 0xFF;
        int rate = program.dataByteAt(t.pos++) & 0xFF;
        t.modPendingRate = rate == 0 ? 256 : rate;
        t.modPendingDelta = program.dataByteAt(t.pos++);
        int steps = program.dataByteAt(t.pos++) & 0xFF;
        t.modPendingStepsFull = steps;
        t.modPendingSteps = steps / 2;
        t.customModEnabled = true;
        t.modEnvId = 0;
        t.modEnvData = null;
        t.modEnabled = true;
    }

    /** TrkCmd_Jump: absolute Z80 pointer (FM/_Driver.asm:1668-1675). */
    private static void jump(CoordFlagContext ctx, SmpsSequencer.Track t) {
        int target = ctx.readJumpPointer(t);
        if (target < 0) {
            throw new IllegalStateException("FM jump outside the driver bank");
        }
        t.pos = target;
    }

    /**
     * TrkCmd_Repeat: a zero counter loads the count, the counter is decremented, and the
     * pointer is taken while it is nonzero (FM/_Driver.asm:1820-1844). The counter is a byte, so a zero
     * count runs 256 times.
     */
    private static void repeat(CoordFlagContext ctx, SmpsSequencer.Track t, SmpsProgramView program) {
        int index = program.dataByteAt(t.pos++) & 0xFF;
        int count = program.dataByteAt(t.pos++) & 0xFF;
        if (index >= 2) {
            // ftrkRepeatCnts holds two counters (FM/_Variables.inc:59); shipped data uses 0 and 1.
            throw new IllegalStateException("FM repeat counter " + index + " outside the track's two");
        }
        if (t.loopCounters[index] == 0) {
            t.loopCounters[index] = count;
        }
        t.loopCounters[index] = (t.loopCounters[index] - 1) & 0xFF;
        if (t.loopCounters[index] != 0) {
            jump(ctx, t);
        } else {
            t.pos += 2;
        }
    }

    /** TrkCmd_Call pushes the operand position and jumps; the stack holds two (FM/_Driver.asm:1769-1792). */
    private static void call(CoordFlagContext ctx, SmpsSequencer.Track t) {
        int returnPos = t.pos;
        int target = ctx.readJumpPointer(t);
        if (target < 0 || t.returnSp >= 2) {
            throw new IllegalStateException("FM call outside the driver bank or its two-entry stack");
        }
        t.returnStack[t.returnSp++] = returnPos + 2;
        t.pos = target;
    }

    /**
     * TrkCmd_SwapRingChn (FM/_Driver.asm:1741-1760). A zero {@code ringChannel} becomes 80h and the right-hand
     * ring continues. Otherwise it becomes zero and {@code FM_RINGL} is written to the first
     * sound queue slot; this effect plays its tick, and {@link CdFmSoundRequestService} admits
     * FM_RINGL through that slot's priority check at the next one, replacing it on FM4.
     */
    private void swapRingChannel(CoordFlagContext ctx, SmpsSequencer.Track t) {
        if (driver.ringChannel() == 0) {
            driver.setRingChannel(0x80);
            return;
        }
        driver.setRingChannel(0);
        driver.setTrackQueued(RING_LEFT);
    }

    private static void stopUnreached(CoordFlagContext ctx, SmpsSequencer.Track t, int cmd) {
        LOGGER.warning(() -> String.format("Sonic CD FM command %02X is not reached by shipped data; track stopped", cmd));
        t.active = false;
        ctx.stopNote(t);
    }

    private static int unreachedOperandBytes(int cmd) {
        return switch (cmd) {
            // E3, E4, EA, F4 and F5 stop the track; EC takes no operand.
            case 0xE8, 0xFA, 0xFC, 0xFD -> 1;
            case 0xE9, 0xED, 0xEE -> 2;
            case 0xE5 -> 3;
            case 0xFE -> 4;
            default -> 0;
        };
    }
}
