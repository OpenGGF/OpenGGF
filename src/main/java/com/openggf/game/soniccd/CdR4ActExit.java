package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.*;
import java.util.Objects;

/** Finite TT stage-end profile. Existing BigRing/Goal/Signpost/Results/GameOver/Title algorithms
 * are reused through their finite asset records, not copied into R4 objects or a per-act loader.
 * These borrowed values do not authenticate media, select C/D, establish current art or grant
 * programme-return authority. Root binds them from FINAL source metadata and original calls.
 * N1ecc5f3: objects/stage_end.asm:284-644; results.asm:229-287; R4 historical entry graphs.
 */
public final class CdR4ActExit {
    private CdR4ActExit() { }
    public record Assets(CdBigRing.Assets bigRing,CdGoalPost.Assets goal,CdSignpost.Assets signpost,
                         CdResults.Assets results,CdGameOver.Assets gameOver,CdTitleCard.Assets title) {
        public Assets {Objects.requireNonNull(bigRing);Objects.requireNonNull(goal);Objects.requireNonNull(signpost);
            Objects.requireNonNull(results);Objects.requireNonNull(gameOver);Objects.requireNonNull(title);}
    }
    /** Goal sub_20A140: TT rows are all $4F2; actual mapping must still come from the chosen programme. */
    public static int goalTileWord() {return 0x84F2;}
    public static int signpostTileWord() {return 0x843C;}
    public static int resultsPlcId() {return 16;}
    public static int stageEndPlcId() {return 18;}
    public static int sectionPlcId() {return 2;}
    public static int chaseBubblePlcId() {return 4;}
    /** For TT, USA below-fifty BigRing uses CheckObjectDespawn; retained art never selects handler Null. */
    public static boolean goalDraws(int rawActByte) {return (rawActByte&255)!=2;}
    /** Stage-end/capsule palette selection is StageDataIndex+$E, not a fabricated fixed palette. */
    public static int capsulePaletteOperandOffset() {return 0xE;}
    /** IPX RunR4:218-232 operands. Original unlock and RunR5 WriteSaveData precede RunR51. */
    public static int campaignUnlockLevelId() {return 9;}
    public static int campaignGoodFutureBit() {return 2;}
    public static int followingCampaignZoneAct() {return 0x300;}
    public static CdNativeFile followingCampaignProgramme() {return CdNativeFile.R51A;}
}
