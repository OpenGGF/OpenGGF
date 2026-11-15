package com.openggf.game.soniccd.special;

/**
 * {@code specStageFlags} (_Global Variables.inc:11): bit 0 temporary mode (stage-select and
 * attract demos, Special Stage 8), bit 1 Time Attack, bit 2 secret mode (Special Stage 8).
 * Callers set them as IPX.asm does: {@code SpecStage1Demo} (IPX.asm:111-118) temporary,
 * {@code .SpecialStage} (IPX.asm:586-591) none, {@code TimeAttack_SS} (IPX.asm:1287-1297)
 * Time Attack, {@code SoundTest_SpecStg8} (IPX.asm:1064-1078) temporary and secret.
 */
public record CdSpecialStageFlags(boolean temporary, boolean timeAttack, boolean secret) {
    public static final CdSpecialStageFlags NORMAL = new CdSpecialStageFlags(false, false, false);
    public static final CdSpecialStageFlags DEMO = new CdSpecialStageFlags(true, false, false);
    public static final CdSpecialStageFlags TIME_ATTACK = new CdSpecialStageFlags(false, true, false);
    public static final CdSpecialStageFlags SECRET = new CdSpecialStageFlags(true, false, true);

    /** {@code ReadController} replaces the pad with demo input in temporary, non-secret mode (scddisasm/src/Special Stage/Main.asm:3580-3585). */
    public boolean demoInput() { return temporary && !secret; }
}
