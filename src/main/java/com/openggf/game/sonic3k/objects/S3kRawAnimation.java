package com.openggf.game.sonic3k.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindStateful;

import java.io.IOException;

/**
 * ROM raw animation interpreters (sonic3k.asm:177341-177720) over a window of script bytes
 * read from the ROM. A script is addressed by its ROM address, as the ROM keeps the script
 * pointer in {@code $30(a0)}; the jump commands rewrite that address.
 *
 * <p>Two script layouts exist. Single-delay scripts ({@link #animateNoSst},
 * {@link #animateCheckResult}) start with a delay byte followed by frames; multi-delay
 * scripts ({@link #animateMultiDelay}, {@link #animateRaw2MultiDelay}) are
 * {@code frame, delay} pairs. Commands are {@code $FC} restart, {@code $F8} jump and
 * {@code $F4} run the {@code $34(a0)} callback. {@code Animate_RawMultiDelay} and
 * {@code Animate_RawNoSST} recognise any negative frame byte as a command;
 * {@code Animate_RawCheckResult} and {@code Animate_Raw2MultiDelay} need a {@code $FF}
 * frame byte followed by the command byte.
 */
public final class S3kRawAnimation {
    /** d2 after a normal frame advance. */
    public static final int ADVANCED = 1;
    /** d2 while the delay has not expired. */
    public static final int WAITING = 0;
    /** d2 after a command byte (or {@code Animate_Raw2MultiDelay}'s restart/jump). */
    public static final int COMMAND = -1;

    private static final int CMD_RESTART = 0xFC;
    private static final int CMD_JUMP = 0xF8;
    private static final int CMD_CALLBACK = 0xF4;

    /** Mutable SST fields the interpreters read and write. */
    public static final class State implements RewindStateful<State.Value> {
        public record Value(int script, int animFrame, int animFrameTimer, int mappingFrame) {}

        /** {@code $30(a0)}: ROM address of the current script. */
        public int script;
        /** {@code anim_frame(a0)}. */
        public int animFrame;
        /** {@code anim_frame_timer(a0)}. */
        public int animFrameTimer;
        /** {@code mapping_frame(a0)}. */
        public int mappingFrame;

        @Override
        public Value captureRewindStateValue() {
            return new Value(script, animFrame, animFrameTimer, mappingFrame);
        }

        @Override
        public void restoreRewindStateValue(Value value) {
            script = value.script();
            animFrame = value.animFrame();
            animFrameTimer = value.animFrameTimer();
            mappingFrame = value.mappingFrame();
        }

        public State copy() {
            State copy = new State();
            copy.script = script;
            copy.animFrame = animFrame;
            copy.animFrameTimer = animFrameTimer;
            copy.mappingFrame = mappingFrame;
            return copy;
        }
    }

    private final int base;
    private final byte[] bytes;

    private S3kRawAnimation(int base, byte[] bytes) {
        this.base = base;
        this.bytes = bytes;
    }

    public static S3kRawAnimation load(RomByteReader reader, int base, int size) throws IOException {
        return new S3kRawAnimation(base, reader.slice(base, size));
    }

    private int u8(int address) {
        return bytes[address - base] & 0xFF;
    }

    /** {@code Set_Raw_Animation}. */
    public static void set(State state, int script) {
        state.script = script;
        state.animFrame = 0;
        state.animFrameTimer = 0;
    }

    /** {@code Animate_RawNoSST} with {@code a1} = {@code script}; the jump command stores {@code $30}. */
    public void animateNoSst(State s, int script, Runnable callback) {
        s.animFrameTimer = (s.animFrameTimer - 1) & 0xFF;
        if ((byte) s.animFrameTimer >= 0) {
            return;
        }
        int d0 = (s.animFrame + 1) & 0xFF;
        s.animFrame = d0;
        int frame = u8(script + 1 + d0);
        if ((byte) frame < 0) {
            switch (frame) {
                case CMD_JUMP -> {
                    int target = script + (byte) u8(script + 2 + d0);
                    s.script = target;
                    s.mappingFrame = u8(target + 1);
                    s.animFrameTimer = u8(target);
                }
                case CMD_CALLBACK -> {
                    s.animFrameTimer = 0;
                    callback.run();
                }
                default -> {
                    s.mappingFrame = u8(script + 1);
                    s.animFrameTimer = u8(script);
                }
            }
            s.animFrame = 0;
            return;
        }
        s.animFrameTimer = u8(script);
        s.mappingFrame = frame;
    }

    /** {@code Animate_RawCheckResult}. */
    public int animateCheckResult(State s, Runnable callback) {
        int script = s.script;
        s.animFrameTimer = (s.animFrameTimer - 1) & 0xFF;
        if ((byte) s.animFrameTimer >= 0) {
            return WAITING;
        }
        int d0 = (s.animFrame + 1) & 0xFF;
        s.animFrame = d0;
        int frame = u8(script + 1 + d0);
        if (frame != 0xFF) {
            s.animFrameTimer = u8(script);
            s.mappingFrame = frame;
            return ADVANCED;
        }
        int command = u8(script + 2 + d0);
        switch (command) {
            case CMD_JUMP -> {
                int target = script + (byte) u8(script + 3 + d0);
                s.script = target;
                s.mappingFrame = u8(target + 1);
                s.animFrameTimer = u8(target);
            }
            case CMD_CALLBACK -> {
                s.animFrameTimer = 0;
                callback.run();
            }
            default -> {
                s.mappingFrame = u8(script + 1);
                s.animFrameTimer = u8(script);
            }
        }
        s.animFrame = 0;
        return COMMAND;
    }

    /** {@code Animate_RawMultiDelay}. */
    public int animateMultiDelay(State s, Runnable callback) {
        int script = s.script;
        s.animFrameTimer = (s.animFrameTimer - 1) & 0xFF;
        if ((byte) s.animFrameTimer >= 0) {
            return WAITING;
        }
        int d0 = (s.animFrame + 2) & 0xFF;
        s.animFrame = d0;
        int frame = u8(script + d0);
        if ((byte) frame >= 0) {
            s.mappingFrame = frame;
            s.animFrameTimer = u8(script + 1 + d0);
            return ADVANCED;
        }
        int result;
        switch (frame) {
            case CMD_JUMP -> {
                int target = script + (byte) u8(script + 1 + d0);
                s.script = target;
                s.mappingFrame = u8(target);
                s.animFrameTimer = u8(target + 1);
                result = ADVANCED;
            }
            case CMD_CALLBACK -> {
                s.animFrameTimer = 0;
                callback.run();
                result = COMMAND;
            }
            default -> {
                s.mappingFrame = u8(script);
                s.animFrameTimer = u8(script + 1);
                result = ADVANCED;
            }
        }
        s.animFrame = 0;
        return result;
    }

    /**
     * {@code Animate_RawNoSSTMultiDelayFlipX} (sonic3k.asm:177628-177651). Same script layout as
     * {@link #animateMultiDelay}, with the script passed in {@code a1} rather than taken from
     * {@code $30(a0)}, and with bit 6 of a frame byte meaning "toggle {@code render_flags} bit 0"
     * rather than being part of the frame index ({@code bclr #6,d1 / bne / bchg #0,render_flags}).
     * The command paths are shared with {@code Animate_RawMultiDelay}, so a jump still stores its
     * target in {@code $30(a0)}.
     *
     * @param toggleFlipX run when a frame byte carries bit 6
     */
    public int animateNoSstMultiDelayFlipX(State s, int script, Runnable callback,
            Runnable toggleFlipX) {
        s.animFrameTimer = (s.animFrameTimer - 1) & 0xFF;
        if ((byte) s.animFrameTimer >= 0) {
            return WAITING;
        }
        int d0 = (s.animFrame + 2) & 0xFF;
        s.animFrame = d0;
        int frame = u8(script + d0);
        if ((byte) frame >= 0) {
            if ((frame & 0x40) != 0) {
                toggleFlipX.run();
            }
            s.mappingFrame = frame & ~0x40;
            s.animFrameTimer = u8(script + 1 + d0);
            return ADVANCED;
        }
        int result;
        switch (frame) {
            case CMD_JUMP -> {
                int target = script + (byte) u8(script + 1 + d0);
                s.script = target;
                s.mappingFrame = u8(target);
                s.animFrameTimer = u8(target + 1);
                result = ADVANCED;
            }
            case CMD_CALLBACK -> {
                s.animFrameTimer = 0;
                callback.run();
                result = COMMAND;
            }
            default -> {
                s.mappingFrame = u8(script);
                s.animFrameTimer = u8(script + 1);
                result = ADVANCED;
            }
        }
        s.animFrame = 0;
        return result;
    }

    /**
     * {@code Animate_Raw2MultiDelay}. Every command path returns {@link #COMMAND} and ends
     * with {@code clr.b anim_frame(a0)}, so the condition codes a caller tests afterwards
     * are Z set and N clear.
     */
    public int animateRaw2MultiDelay(State s, Runnable callback) {
        int script = s.script;
        s.animFrameTimer = (s.animFrameTimer - 1) & 0xFF;
        if ((byte) s.animFrameTimer >= 0) {
            return WAITING;
        }
        int d0 = (s.animFrame + 2) & 0xFF;
        s.animFrame = d0;
        int frame = u8(script + d0);
        if (frame != 0xFF) {
            s.mappingFrame = frame;
            s.animFrameTimer = u8(script + 1 + d0);
            return ADVANCED;
        }
        int command = u8(script + 1 + d0);
        switch (command) {
            case CMD_JUMP -> {
                int target = script + (byte) u8(script + 2 + d0);
                s.script = target;
                s.mappingFrame = u8(target);
                s.animFrameTimer = u8(target + 1);
            }
            case CMD_CALLBACK -> {
                s.animFrameTimer = 0;
                callback.run();
            }
            default -> {
                s.mappingFrame = u8(script);
                s.animFrameTimer = u8(script + 1);
            }
        }
        s.animFrame = 0;
        return COMMAND;
    }
}
