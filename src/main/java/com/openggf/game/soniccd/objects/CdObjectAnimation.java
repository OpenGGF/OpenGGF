package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Objects;

/**
 * Finite native object-animation transition, independent of slots and player animation.
 * Source pin: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Object Animate.asm:14-95 (AnimateObject through .End2).
 * The inspected routine has no FixBugs alternative; no repaired behavior is introduced.
 *
 * <p>The caller supplies asset bytes and native object fields, then commits the returned
 * value only after success. Construction and waiting do not prime frames. No object
 * dispatch, deletion, drawing, art service or external state mutation happens here.</p>
 */
public final class CdObjectAnimation {
    private CdObjectAnimation() { }

    /**
     * Canonical unsigned bytes for oAnim, oPrevAnim, oAnimFrame, oAnimTime, oMapFrame,
     * oSprFlags, oFlags, oRoutine and oRoutine2. Held map frames may exceed 31;
     * only a frame publication applies the source's five-bit mask.
     */
    public record State(int animationId, int previousId, int scriptIndex, int timer,
                        int mapFrame, int spriteFlags, int statusFlags, int routine, int routine2) {
        public State {
            requireByte(animationId, "animationId");
            requireByte(previousId, "previousId");
            requireByte(scriptIndex, "scriptIndex");
            requireByte(timer, "timer");
            requireByte(mapFrame, "mapFrame");
            requireByte(spriteFlags, "spriteFlags");
            requireByte(statusFlags, "statusFlags");
            requireByte(routine, "routine");
            requireByte(routine2, "routine2");
        }
    }

    /**
     * One explicit immutable read extent, with the native a1 table base inside it.
     * Offsets are signed big-endian words relative to tableOffset, including aliases
     * and negative offsets. scriptCount bounds the pointer directory, not script
     * length; every actually selected byte is separately bounded by bytes.length().
     *
     * <p>This is a format view, not media authentication. Production must obtain the
     * view/count from its authenticated object-asset owner. Do not pass an arbitrary
     * enlarged resident image to make an otherwise out-of-bounds read succeed.</p>
     */
    public record AnimationTable(CdAssetBytes bytes, int tableOffset, int scriptCount) {
        public AnimationTable {
            Objects.requireNonNull(bytes, "bytes");
            if (scriptCount < 1 || scriptCount > 256) {
                throw new IllegalArgumentException("Object animation scriptCount must be 1..256");
            }
            if (tableOffset < 0 || tableOffset > bytes.length() - 2 * scriptCount) {
                throw new IllegalArgumentException("Object animation pointer directory exceeds supplied bytes");
            }
        }

        private long scriptAddress(int animationId) {
            if (animationId >= scriptCount) {
                throw new UnsupportedOperationException("CD object animation " + animationId
                        + " exceeds supplied script count " + scriptCount);
            }
            int relative = (short) bytes.unsignedWord(tableOffset + 2 * animationId);
            return (long) tableOffset + relative;
        }

        private int read(int animationId, long scriptAddress, int scriptByte) {
            long address = scriptAddress + scriptByte;
            if (address < 0 || address >= bytes.length()) {
                throw new UnsupportedOperationException("CD object animation " + animationId
                        + " script byte " + scriptByte + " resolves to view byte " + address
                        + " outside supplied extent [0," + bytes.length() + ")");
            }
            return bytes.unsignedByte((int) address);
        }
    }

    /** One AnimateObject call; missing selected bytes fail without publishing any state. */
    public static State step(State state, AnimationTable table) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(table, "table");
        int animationId = state.animationId();
        int previousId = state.previousId();
        int index = state.scriptIndex();
        int timer = state.timer();

        // AnimateObject/.Do (Object Animate.asm:14-29): compare original IDs before
        // lookup, reset index/timer on change, then SUBQ.B and test N with BPL.
        if (animationId != previousId) {
            previousId = animationId;
            index = 0;
            timer = 0;
        }
        timer = (timer - 1) & 0xFF;
        if (timer < 0x80) {
            return new State(animationId, previousId, index, timer, state.mapFrame(),
                    state.spriteFlags(), state.statusFlags(), state.routine(), state.routine2());
        }

        long script = table.scriptAddress(animationId);
        timer = table.read(animationId, script, 0);
        int frame = table.read(animationId, script, 1 + index);
        if (frame < 0x80) {
            return publishFrame(state, previousId, index, timer, frame);
        }

        // .AniFF/.AniFE (Object Animate.asm:55-71): selected bytes go straight to
        // .AniNext, even when negative. There is no recursive command dispatch.
        int commandCheck = (frame + 1) & 0xFF;
        if (commandCheck == 0) {
            frame = table.read(animationId, script, 1);
            return publishFrame(state, previousId, 0, timer, frame);
        }
        commandCheck = (commandCheck + 1) & 0xFF;
        if (commandCheck == 0) {
            int back = table.read(animationId, script, 2 + index);
            index = (index - back) & 0xFF;
            frame = table.read(animationId, script, 1 + index);
            return publishFrame(state, previousId, index, timer, frame);
        }

        // .AniFD through .End2 (Object Animate.asm:73-95) fall through, adding one
        // to the SAME byte each time. FD's memory-to-memory animation write does
        // not load its operand into that byte; FC/FB writes do not change it either.
        // Keep those increments literal rather than reinterpreting an operand.
        commandCheck = (commandCheck + 1) & 0xFF;
        if (commandCheck == 0) {
            animationId = table.read(animationId, script, 2 + index);
        }
        int routine = state.routine();
        int routine2 = state.routine2();
        commandCheck = (commandCheck + 1) & 0xFF;
        if (commandCheck == 0) {
            routine = (routine + 2) & 0xFF;
        }
        commandCheck = (commandCheck + 1) & 0xFF;
        if (commandCheck == 0) {
            index = 0;
            routine2 = 0;
        }
        commandCheck = (commandCheck + 1) & 0xFF;
        if (commandCheck == 0) {
            routine2 = (routine2 + 2) & 0xFF;
        }
        // Other negative bytes reach the native RTS without a frame/index write.
        return new State(animationId, previousId, index, timer, state.mapFrame(),
                state.spriteFlags(), state.statusFlags(), routine, routine2);
    }

    private static State publishFrame(State state, int previousId, int index, int timer, int frame) {
        // .AniNext (Object Animate.asm:36-48): ROL.B #3, XOR oFlags, keep low two
        // sprite bits; the original byte, not the masked map, supplies both flips.
        int rotated = ((frame << 3) | (frame >>> 5)) & 0xFF;
        int sprite = (state.spriteFlags() & 0xFC) | ((rotated ^ state.statusFlags()) & 3);
        return new State(state.animationId(), previousId, (index + 1) & 0xFF, timer,
                frame & 0x1F, sprite, state.statusFlags(), state.routine(), state.routine2());
    }

    private static void requireByte(int value, String name) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " must be an unsigned native byte");
        }
    }
}
