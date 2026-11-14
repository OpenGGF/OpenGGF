package com.openggf.game.soniccd.special;

/**
 * What the special stage hands back to the program-flow owner when it ends.
 *
 * @param timeStoneWon   the stage's Time Stone was won ({@code .StageBeaten}, Sub.asm:332-336)
 * @param ufosLeft       UFOs remaining; IPX reads it as {@code specStageLost} (Main.asm:234)
 * @param timeStones     all stones held after the stage ({@code timeStonesSub})
 * @param nextStageId    the next stage ({@code .GetNextStage}, Sub.asm:341-356); normal play saves
 *                       it as {@code curSpecStage} (Main.asm:295)
 * @param rings          {@code specStageRings}; the results ring bonus is 20 each (Main.asm:282-284)
 * @param timer          {@code specStageTimer}: seconds left, or Time Attack minutes/seconds/frames
 *                       bytes; the results time bonus is 20 per second (Main.asm:285-287)
 * @param manualExit     left by Start in temporary mode, not by the stage ending
 * @param showsResults   normal mode goes to the results screen (Main.asm:239-241)
 */
public record CdSpecialStageResult(int stageId, boolean timeStoneWon, int ufosLeft, int timeStones, int nextStageId,
                                   int rings, int timer, boolean manualExit, boolean showsResults) {
    static CdSpecialStageResult of(CdSpecialStage stage) {
        CdSpecialStageFlags flags = stage.flags();
        boolean won = stage.gotTimeStone;
        return new CdSpecialStageResult(stage.stageId(), won, stage.ufoCount(), stage.timeStones(), stage.nextStageId(),
                stage.rings(), stage.timer(), stage.manualExit,
                !flags.temporary() && !flags.timeAttack());
    }

    /** {@code Results} (scddisasm/src/Special Stage/Main.asm:282-287). */
    public int ringBonus() { return rings * 20; }

    public int timeBonus() { return (timer & 0xFFFF) * 20; }
}
