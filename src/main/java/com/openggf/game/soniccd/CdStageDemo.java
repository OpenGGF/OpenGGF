package com.openggf.game.soniccd;

import com.openggf.control.PlayerInputState;

/** The three ordinary stage programs dispatched by Demo (CD Initial Program/IPX.asm:929-955). */
public enum CdStageDemo {
    R11A(CdStagePayload.R11A, CdNativeProgram.R11ADEMO),
    R43C(CdStagePayload.R43C, CdNativeProgram.R43CDEMO),
    R82A(CdStagePayload.R82A, CdNativeProgram.R82ADEMO);

    // ReadControllers (Level/Functions (General).asm:53-75) reads one held/tap word per V-INT.
    // Level_MainLoop (Level/Main.asm:397-408) returns when that sample count is reached.
    public static final int INPUT_WORDS = 0x800;

    // LevelStart (Level/Main.asm:219-259) reads one .WaitPLC2 sample on fresh entry.
    // FadeFromBlack (Level/Palette Fade.asm:11-34) then reads 7*3+1 samples before the main loop.
    public static final int ENTRY_INPUT_WORDS = 1 + (7 * 3 + 1);

    private final CdStagePayload payload;
    private final CdNativeProgram program;

    CdStageDemo(CdStagePayload payload, CdNativeProgram program) {
        this.payload = payload;
        this.program = program;
    }

    public CdStagePayload payload() { return payload; }
    public CdNativeProgram program() { return program; }

    static PlayerInputState decodeInput(int word) {
        int held = word >>> 8;
        int tapped = word & 0xFF;
        // Native action bits are B, C, A, in ascending order.
        return PlayerInputState.of(held, tapped, actions(held), actions(tapped), false, (tapped & 0x80) != 0);
    }

    private static int actions(int pad) {
        return ((pad >>> 4) & 3) << 1 | ((pad >>> 6) & 1);
    }
}
