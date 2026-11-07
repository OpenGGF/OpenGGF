package com.openggf.game.soniccd;

import java.util.*;

/** Finite pure IPX rules at N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/CD Initial Program/IPX.asm:407-437,542-629. Results and these values carry NO
 * original RTS/RunMMD invocation authority. Root must call from its original typed return port,
 * authenticate current operation/source/full State+Environment, prepare before adopting once.
 * Warp and special-stage returns are separate original calls, never inferred from retained values. */
public final class CdR4ProgrammeTransitions {
    private CdR4ProgrammeTransitions() { }
    public enum Cut { MISSING_LIVES, MISSING_MARKED_ERA, MISSING_SPECIAL_STAGE, MISSING_GOOD_FUTURE,
        MISSING_SAVED_LEVEL, MISSING_TIME_STONES, GAME_OVER, RUN_WARP, RUN_SPECIAL_STAGE,
        RUN_STAGE, RETURN_TO_IPX, R4_UNLOCK_TIME_ATTACK }
    public record State(OptionalInt livesByte,OptionalInt timeZoneByte,OptionalInt goodFutureByte,
                        OptionalInt specialStageByte,OptionalInt savedLevelByte,OptionalInt timeStonesByte) {
        public State {for(var v:List.of(livesByte,timeZoneByte,goodFutureByte,specialStageByte,savedLevelByte,timeStonesByte)){
            Objects.requireNonNull(v);if(v.isPresent()&&(v.getAsInt()&~255)!=0)throw new IllegalArgumentException("IPX byte");}}
    }
    public enum Store { SAVED_LEVEL_INCREMENT, SAVED_LEVEL_CAP_DECREMENT, CHECKPOINT_ZERO,
        PALETTE_CLEAR_ONE, GOOD_FUTURE_ONE }
    /** Store census is metadata, never a replayable list of commands. Empty fields are no writes. */
    public record Result(Cut cut,Optional<CdNativeFile> nextFile,OptionalInt savedLevelAfter,
                         List<Store> completedStores) {
        public Result {Objects.requireNonNull(cut);Objects.requireNonNull(nextFile);Objects.requireNonNull(savedLevelAfter);completedStores=List.copyOf(completedStores);}
    }
    public static Result ordinaryReturned(State s) {
        if(s.livesByte().isEmpty())return result(Cut.MISSING_LIVES);
        if(s.livesByte().getAsInt()==0)return result(Cut.GAME_OVER);
        if(s.timeZoneByte().isEmpty())return result(Cut.MISSING_MARKED_ERA);
        if((s.timeZoneByte().getAsInt()&128)!=0)return result(Cut.RUN_WARP);
        if(s.specialStageByte().isEmpty())return result(Cut.MISSING_SPECIAL_STAGE);
        if(s.specialStageByte().getAsInt()!=0)return result(Cut.RUN_SPECIAL_STAGE); // first original special-entry continuation; no operand copies have run here
        return result(Cut.RETURN_TO_IPX);
    }
    /** Call only after the actual WARP.MMD return; act is the original RunLevel's table, not changed Results act. */
    public static Result warpReturned(int originalAct,State s) {
        if(originalAct<0||originalAct>1)throw new IllegalArgumentException("TT RunLevel supports acts1/2");
        if(s.timeZoneByte().isEmpty())return result(Cut.MISSING_MARKED_ERA);
        int era=s.timeZoneByte().getAsInt()&127;String suffix;
        if(era==0)suffix="B";else if(era==1)suffix="A";else {
            if(s.goodFutureByte().isEmpty())return result(Cut.MISSING_GOOD_FUTURE);
            suffix=s.goodFutureByte().getAsInt()==0?"D":"C";
        }
        return new Result(Cut.RUN_STAGE,Optional.of(CdNativeFile.valueOf("R4"+(originalAct+1)+suffix)),OptionalInt.empty(),List.of());
    }
    public static Result bossEntry(State s) {
        if(s.goodFutureByte().isEmpty())return result(Cut.MISSING_GOOD_FUTURE);
        return new Result(Cut.RUN_STAGE,Optional.of(s.goodFutureByte().getAsInt()==0?CdNativeFile.R43D:CdNativeFile.R43C),OptionalInt.empty(),List.of());
    }
    /** Original RunBossLevel return, not ResultsNextStage completion. ADDQ/SUBQ are BYTE-width. */
    public static Result bossReturned(State s) {
        if(s.livesByte().isEmpty())return result(Cut.MISSING_LIVES);
        var unknown=OptionalInt.empty();
        // Only project operands the existing shared RunBossLevel kernel reads; unrelated facts
        // remain explicitly unknown. This is not a fabricated Frame, World or loaded save.
        var before=new CdIpxProgress.State(new CdIpxProgress.SavedLevel(s.savedLevelByte(),unknown),
            unknown,unknown,unknown,unknown,unknown,unknown,unknown);
        var nativeResult=CdIpxProgress.bossReturned(before,s.livesByte().getAsInt());
        if(nativeResult.cut()==CdIpxProgress.Cut.RUN_BOSS_GAME_OVER)return result(Cut.GAME_OVER);
        if(nativeResult.cut()==CdIpxProgress.Cut.RUN_BOSS_SAVED_ZONE_READ)return result(Cut.MISSING_SAVED_LEVEL);
        if(nativeResult.cut()!=CdIpxProgress.Cut.RETURN_TO_BOSS_CALLER)throw new IllegalStateException("Unexpected shared IPX cut");
        var stores=nativeResult.stores().stream().map(store->switch(store){
            case SAVED_ZONE_INCREMENT -> Store.SAVED_LEVEL_INCREMENT;
            case SAVED_ZONE_CAP_DECREMENT -> Store.SAVED_LEVEL_CAP_DECREMENT;
            case CHECKPOINT_CLEAR -> Store.CHECKPOINT_ZERO;
            default -> throw new IllegalStateException("Different shared boss operation");
        }).toList();
        // Return to RunR4's ORIGINAL unlock call first (IPX:218), not directly to RunR51.
        return new Result(Cut.R4_UNLOCK_TIME_ATTACK,Optional.empty(),nativeResult.progress().savedLevel().zoneByte(),stores);
    }

    public static Result specialReturned(State s) {
        // palClearFlags is reached before the possibly unknown stones read.
        if(s.timeStonesByte().isEmpty())return new Result(Cut.MISSING_TIME_STONES,Optional.empty(),OptionalInt.empty(),List.of(Store.PALETTE_CLEAR_ONE));
        return new Result(Cut.RETURN_TO_IPX,Optional.empty(),OptionalInt.empty(),s.timeStonesByte().getAsInt()==127?
            List.of(Store.PALETTE_CLEAR_ONE,Store.GOOD_FUTURE_ONE):List.of(Store.PALETTE_CLEAR_ONE));
    }
    private static Result result(Cut cut) {return new Result(cut,Optional.empty(),OptionalInt.empty(),List.of());}
}
