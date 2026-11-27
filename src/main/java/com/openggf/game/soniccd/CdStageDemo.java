package com.openggf.game.soniccd;

import com.openggf.control.PlayerInputState;

/** The three ordinary stage programs dispatched by Demo (CD Initial Program/IPX.asm:929-955). */
public enum CdStageDemo {
    R11A(CdStagePayload.R11A, CdNativeProgram.R11ADEMO, true),
    R43C(CdStagePayload.R43C, CdNativeProgram.R43CDEMO, false),
    R82A(CdStagePayload.R82A, CdNativeProgram.R82ADEMO, false);

    // ReadControllers (Level/Functions (General).asm:53-75) reads one held/tap word per V-INT.
    // Level_MainLoop (Level/Main.asm:397-408) returns when that sample count is reached.
    public static final int INPUT_WORDS = 0x800;

    // FadeFromBlack (Level/Palette Fade.asm:11-34) reads 7*3+1 samples.
    private static final int FADE_INPUT_READS = 7 * 3 + 1;

    private final CdStagePayload payload;
    private final CdNativeProgram program;
    private final boolean titleCard;

    CdStageDemo(CdStagePayload payload, CdNativeProgram program, boolean titleCard) {
        this.payload = payload;
        this.program = program;
        this.titleCard = titleCard;
    }

    public CdStagePayload payload() { return payload; }
    public CdNativeFile file() { return program.loadFiles().getFirst(); }

    // InitStageResetRuntime (r4/main.asm:10-16, common/main.asm:25-33) sets the
    // title-card-loaded bit for the R43C and R82A demos.
    public boolean showsTitleCard() { return titleCard; }

    int entryInputReads(com.openggf.game.soniccd.level.CdStageImage image) {
        // The first wait reads one sample and primes the queue. Each fast
        // decompression batch then costs one more read.
        // InitStageWaitForGfx (r4/main.asm:226-232, common/main.asm:255-261).
        var entry = com.openggf.game.soniccd.level.CdStageCatalogue.entry(this);
        int cue = image.u8(entry.levelDataIndex() + 4);
        int drain = titleCard || cue == 0 ? 0
                : com.openggf.game.soniccd.objects.common.CdPlcQueue.framesToDrain(
                        image, entry.plcLists(), cue,
                        com.openggf.game.soniccd.objects.common.CdPlcQueue.FAST_BATCH);
        return 1 + drain + FADE_INPUT_READS;
    }

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
