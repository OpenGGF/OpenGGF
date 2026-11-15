package com.openggf.game.soniccd.special;

/**
 * Animation scripts of a 3D-section object ({@code SprMap3DSection}, scddisasm/src/Special Stage/Sub.asm:2691-2716): a table
 * of long pointers, one per animation, each to {@code count, speed} bytes followed by long frame
 * pointers whose top byte carries the flip flags. Addresses are sub-CPU program addresses; the
 * program is loaded at $10000 (Sub.asm:198).
 */
final class CdSpecialAnimationScripts {
    private static final int ORIGIN = 0x10000;

    record Script(int count, int speed, int[] frames) { }

    private final byte[] program;
    private final int table;

    CdSpecialAnimationScripts(byte[] program, int tableOffset) {
        this.program = program;
        this.table = tableOffset;
    }

    Script script(int id) {
        int at = (int) (long32(table + id * 4) & 0xFFFFFF) - ORIGIN;
        int count = program[at] & 0xFF;
        int speed = program[at + 1] & 0xFF;
        int[] frames = new int[count];
        for (int i = 0; i < count; i++) frames[i] = (int) long32(at + 2 + i * 4);
        return new Script(count, speed, frames);
    }

    private long long32(int at) {
        return ((program[at] & 0xFFL) << 24) | ((program[at + 1] & 0xFFL) << 16)
                | ((program[at + 2] & 0xFFL) << 8) | (program[at + 3] & 0xFFL);
    }

    /** Animation state of one object: {@code SetObjAnim}, {@code ChgObjAnim}, {@code DrawObject}'s step. */
    static final class State {
        int anim;
        int frame;
        int time;
        private Script script;

        /** {@code SetObjAnim} (scddisasm/src/Special Stage/Sub.asm:802-817). */
        void set(CdSpecialAnimationScripts scripts, int id) {
            anim = id & 0xFF;
            frame = 0;
            script = scripts.script(anim);
            time = script.speed();
        }

        /** {@code ChgObjAnim} (scddisasm/src/Special Stage/Sub.asm:827-840): keeps the frame. */
        void change(CdSpecialAnimationScripts scripts, int id) {
            anim = id & 0xFF;
            script = scripts.script(anim);
            time = script.speed();
        }

        /** {@code DrawObject}'s animation step (scddisasm/src/Special Stage/Sub.asm:857-870). */
        void step() {
            if (script != null) step(script);
        }

        /** The step with the script {@code DrawObject} reads from the object's mappings and animation. */
        void step(Script current) {
            time = (time - 1) & 0xFF;
            if ((byte) time >= 0) return;
            time = current.speed();
            frame = (frame + 1) & 0xFF;
            if (Integer.compareUnsigned(current.count(), frame) <= 0) frame = 0;
        }
    }
}
