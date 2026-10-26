package com.openggf.game.soniccd.audio;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure native ProcSoundQueue/.ProcSlot transition planner, with explicit caller-owned bytes.
 * Every result is an UNCOMMITTED plan, never completed native, physical or gameplay effects.
 *
 * <p>Native pin N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, under
 * src/Sound Drivers/FM/: _Driver.asm ProcSoundQueue/.ProcSlot:1001-1041,
 * GetList:41-49, ReadHLPtr:75-81, DriverInfo:1874-1886;
 * _Variables.inc Variables/sndPlay/sndQueues/rValue/curPriority:70-83;
 * _Sound IDs.inc FM_START:8-10; Data.asm SFXIndex/SFXPriorities:17-182.
 * Integration API pin J=33dc1d9d4129a09d32793cb190ebf4ca18c08c90.
 *
 * <p>No scheduling, native phase, R-register emulation, installation, bus ownership,
 * track service, synthesis or playback is supplied. There is no hidden RAM or default
 * initialization. The direct pending byte is distinct from the three arbitration bytes.
 */
public final class CdFmSoundArbitration {
    private static final int FIRST_SFX = 0x90;
    private static final int INDEXED_BYTES = 0x100 - FIRST_SFX;
    private static final int DECLARED_SFX_POINTERS = 80;
    private static final int DECLARED_PRIORITIES = 79;
    private static final int VARIABLES = 0x1C00;
    private static final int SND_PLAY = 0x1C09;
    private static final int QUEUES = 0x1C0A;
    private static final int R_VALUE = 0x1C17;
    private static final int CUR_PRIORITY = 0x1C18;
    private static final int Z80_BYTES = 0x2000;

    private CdFmSoundArbitration() {
    }

    /**
     * Plans exactly one ProcSoundQueue body. rawR is the caller-supplied raw byte
     * corresponding to its single LD A,R, before all three .ProcSlot calls.
     * It is never inferred from V-int, frames, queue polls, or a synthetic clock.
     *
     * <p>An invalid current cell does not clear or advance DE; the remaining calls
     * can reread it. Every valid ID $90..$FF reads its raw indexed bank byte.
     * Unsigned curPriority is compared BEFORE masking a winning priority with $7F.
     * Equality wins. Losing valid cells still clear and advance. Every reached
     * store is retained, including same-value writes, in native source order.
     */
    public static Result procSoundQueue(State state, PriorityView priorities, int rawR) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(priorities, "priorities");
        requireByte(rawR, "rawR");
        int pending = state.sndPlay();
        int priority = state.curPriority();
        int[] cells = {state.arbitration1(), state.arbitration2(), state.arbitration3()};
        var writes = new ArrayList<PlannedByteWrite>(10);
        writes.add(new PlannedByteWrite(R_VALUE, rawR));
        int cell = 0;
        for (int call = 0; call < 3; call++) {
            int sound = cells[cell];
            if ((sound & 0x80) == 0 || sound < FIRST_SFX) {
                continue;
            }
            int rawPriority = priorities.rawPriorityByte(sound);
            if (priority <= rawPriority) {
                pending = sound;
                writes.add(new PlannedByteWrite(SND_PLAY, sound));
                priority = rawPriority & 0x7F;
                writes.add(new PlannedByteWrite(CUR_PRIORITY, priority));
            }
            cells[cell] = 0;
            writes.add(new PlannedByteWrite(QUEUES + cell, 0));
            cell++;
        }
        return new Result(new State(pending, cells[0], cells[1], cells[2], priority, rawR), writes);
    }

    /**
     * Uses ONLY the existing authenticated asset owner and its actual installed bytes.
     * GetList's operand derives DriverInfo, whose first LE pointer derives the priority
     * base. No copied priority literals, nativePriorityByte range clamp, or synthetic
     * padding is used. Authentication remains the existing owner's responsibility;
     * this factory adds native format/domain checks, not an independent media audit.
     */
    public static PriorityView fromAssets(CdFmSoundAssets assets) {
        Objects.requireNonNull(assets, "actual FM asset owner");
        return fromBankFormat(assets.readZ80(0, assets.bank().length()));
    }

    /**
     * Resolves the native origin-zero FORMAT from immutable caller-supplied bytes.
     * This permits synthetic fixtures and CERTIFIES NO AUTHENTIC SOURCE or execution.
     * Only the installed program/data image is admitted; variables remain outside it.
     *
     * <p>At origin zero DriverStart occupies six bytes; ALIGN 8 puts GetList at $08.
     * Its LD HL,DriverInfo operand is the little-endian word at $09. The fixed code
     * bytes below verify that pointer-reading format, not source identity. DriverInfo
     * occupies 20 bytes; SongIndex aliases the following 80-word SFXIndex, followed
     * by exactly 79 declared priorities. All 112 possible $90..$FF priority accesses
     * must fit the supplied bank. Byte79 is the first header, not a declared row80.
     * Malformed/truncated formats throw IllegalArgumentException; null is rejected.
     */
    public static PriorityView fromBankFormat(CdAssetBytes bank) {
        Objects.requireNonNull(bank, "origin-zero bank");
        int length = bank.length();
        if (length < 0x12 || length > VARIABLES) {
            throw malformed("installed image must end before variable RAM and contain GetList");
        }
        if (bank.unsignedByte(8) != 0x21
                || bank.unsignedByte(11) != 0x06 || bank.unsignedByte(12) != 0
                || bank.unsignedByte(13) != 0x09 || bank.unsignedByte(14) != 0x08
                || bank.unsignedByte(15) != 0xE7 || bank.unsignedByte(16) != 0x08
                || bank.unsignedByte(17) != 0xC9) {
            throw malformed("GetList pointer-reading code does not match the native format");
        }
        int info = wordLE(bank, 9);
        if (info < 0x12 || info > length - 20) {
            throw malformed("DriverInfo escapes the installed bank");
        }
        int base = wordLE(bank, info);
        int index = wordLE(bank, info + 6);
        if (index != info + 20 || wordLE(bank, info + 4) != index
                || base != index + 2 * DECLARED_SFX_POINTERS
                || base > length - INDEXED_BYTES) {
            throw malformed("index, priority pointer or full unchecked read window escapes its domain");
        }
        if (wordLE(bank, info + 2) != 0 || wordLE(bank, info + 8) != 0
                || wordLE(bank, info + 10) != 0 || wordLE(bank, info + 12) != FIRST_SFX
                || wordLE(bank, info + 14) != 0 || wordLE(bank, info + 16) != VARIABLES
                || wordLE(bank, info + 18) != 0) {
            throw malformed("DriverInfo fields do not match the native queue format");
        }
        if (wordLE(bank, index) != base + DECLARED_PRIORITIES) {
            throw malformed("first SFX header does not follow the 79 declared priority bytes");
        }
        return new PriorityView(base, bank.slice(base, INDEXED_BYTES));
    }

    /**
     * Six explicit unsigned bytes. sndPlay=$1C09, arbitration1/2/3=$1C0A/B/C,
     * curPriority=$1C18, rValue=$1C17. These values assert no cold/resident RAM state.
     */
    public record State(int sndPlay, int arbitration1, int arbitration2, int arbitration3,
                        int curPriority, int rValue) {
        public State {
            requireByte(sndPlay, "sndPlay");
            requireByte(arbitration1, "arbitration1");
            requireByte(arbitration2, "arbitration2");
            requireByte(arbitration3, "arbitration3");
            requireByte(curPriority, "curPriority");
            requireByte(rValue, "rValue");
        }
    }

    /**
     * Bounded immutable data view, with NO authentication claim. The public constructor
     * requires the entire 112-byte unchecked priority window, not just 79 declarations
     * or 80 SFX entries. Addresses are Z80-relative, not 68000 bus/file coordinates.
     */
    public static final class PriorityView {
        private final int baseAddress;
        private final CdAssetBytes indexedBytes;

        public PriorityView(int baseAddress, CdAssetBytes indexedBytes) {
            Objects.requireNonNull(indexedBytes, "indexedBytes");
            if (baseAddress < 0 || baseAddress > Z80_BYTES - INDEXED_BYTES
                    || indexedBytes.length() != INDEXED_BYTES) {
                throw malformed("priority view requires exactly 112 bytes within Z80 RAM coordinates");
            }
            this.baseAddress = baseAddress;
            this.indexedBytes = indexedBytes;
        }

        public int baseAddress() {
            return baseAddress;
        }

        /** CdAssetBytes already owns immutable storage; writable copies never alias it. */
        public CdAssetBytes indexedBytes() {
            return indexedBytes;
        }

        /** Preserves every raw $90..$FF lookup, including neighboring header/data bytes. */
        public int rawPriorityByte(int soundId) {
            if (soundId < FIRST_SFX || soundId > 0xFF) {
                throw new IllegalArgumentException("Unchecked native lookup requires a raw ID $90..$FF");
            }
            return indexedBytes.unsignedByte(soundId - FIRST_SFX);
        }
    }

    /** One UNPERFORMED byte store to one of this planner's six owned Z80-relative cells. */
    public record PlannedByteWrite(int address, int value) {
        public PlannedByteWrite {
            if (address != SND_PLAY && address != QUEUES && address != QUEUES + 1
                    && address != QUEUES + 2 && address != R_VALUE && address != CUR_PRIORITY) {
                throw new IllegalArgumentException("Not a ProcSoundQueue byte destination: " + address);
            }
            requireByte(value, "value");
        }
    }

    /**
     * UNCOMMITTED projected state and defensively copied ordered stores, including
     * unchanged-value stores. This value neither commits RAM nor records completed
     * gameplay metadata. Replanning is deterministic; external commit/retry, native
     * call placement and any phase/clock enforcement belong to the future owner.
     */
    public record Result(State plannedState, List<PlannedByteWrite> plannedWrites) {
        public Result {
            Objects.requireNonNull(plannedState, "plannedState");
            plannedWrites = List.copyOf(plannedWrites);
        }
    }

    private static int wordLE(CdAssetBytes bytes, int address) {
        return bytes.unsignedByte(address) | (bytes.unsignedByte(address + 1) << 8);
    }

    private static IllegalArgumentException malformed(String reason) {
        return new IllegalArgumentException("Native FM bank format: " + reason);
    }

    private static void requireByte(int value, String name) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " must be an unsigned byte: " + value);
        }
    }
}
