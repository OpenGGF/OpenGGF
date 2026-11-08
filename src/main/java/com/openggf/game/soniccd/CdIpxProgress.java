package com.openggf.game.soniccd;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Inactive immutable IPX progress rules; no programme, save-service, capture or invocation owner.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, CD Initial Program/IPX.asm:
 * NewGame/RunR1:162-187; RunR3:191-197; RunBossLevel:616-629;
 * UnlockTimeAttackLevel:638-644; GetBuRAMData/WriteSaveData:1438-1475.
 * Values/cuts/requests are descriptions, never proof that a live operation or save returned.
 * The actual Module must authenticate its private stack, capture and complete owner preimages,
 * prepare every owner aggregate before publication, and adopt only the original result once.
 */
final class CdIpxProgress {
    private CdIpxProgress() { }

    /** The high BYTE is saved zone; the untouched low BYTE can remain independently unknown. */
    record SavedLevel(OptionalInt zoneByte, OptionalInt lowByte) {
        SavedLevel { octet(zoneByte); octet(lowByte); }
        OptionalInt word() {
            return zoneByte.isPresent() && lowByte.isPresent()
                    ? OptionalInt.of((zoneByte.getAsInt()<<8)|lowByte.getAsInt()) : OptionalInt.empty();
        }
    }

    /**
     * Named semantic save/progress facts, not an addressable RAM image or an actual save receipt.
     * goodFutures (Main CPU Variables.inc:32) is DISTINCT from goodFuture (:119) and goodFutureFlags (:143).
     */
    record State(SavedLevel savedLevel, OptionalInt goodFuturesByte, OptionalInt titleFlagsByte,
                 OptionalInt timeAttackUnlockByte, OptionalInt unknownBackupByte,
                 OptionalInt currentSpecialStageByte, OptionalInt timeStonesByte, OptionalInt saveDisabledByte) {
        State {
            Objects.requireNonNull(savedLevel,"saved level knowledge");
            octet(goodFuturesByte);octet(titleFlagsByte);octet(timeAttackUnlockByte);octet(unknownBackupByte);
            octet(currentSpecialStageByte);octet(timeStonesByte);octet(saveDisabledByte);
        }
    }

    enum Cut {
        NEW_GAME_WRITE_SAVE_DATA, RUN_BOSS_GAME_OVER, RUN_BOSS_SAVED_ZONE_READ, RETURN_TO_BOSS_CALLER,
        RUN_R1_UNLOCK_READ, RUN_R1_TITLE_FLAGS_READ, RUN_R1_GOOD_FUTURE_READ, RUN_R1_GOOD_FUTURES_READ,
        RUN_R3_WRITE_SAVE_DATA, RUN_R31, RUN_R32
    }
    enum Read {
        SAVED_LEVEL_ZONE, SAVED_LEVEL_WORD, TIME_ATTACK_UNLOCK, TITLE_FLAGS, GOOD_FUTURE, GOOD_FUTURES,
        SAVE_DISABLED_READ, SAVE_DISABLED_WRITE, UNKNOWN_BACKUP, CURRENT_SPECIAL_STAGE, TIME_STONES
    }
    /** Source-store census only; this list must never be replayed as gameplay/service effects. */
    enum Store {
        NEW_GAME_PLC_CLEAR, NEW_GAME_ZONE_CLEAR, NEW_GAME_SAVED_LEVEL_CLEAR, NEW_GAME_GOOD_FUTURES_CLEAR,
        NEW_GAME_SPECIAL_STAGE_CLEAR, NEW_GAME_TIME_STONES_CLEAR,
        SAVED_ZONE_INCREMENT, SAVED_ZONE_CAP_DECREMENT, CHECKPOINT_CLEAR, TIME_ATTACK_UNLOCK_WRITE,
        TITLE_BIT6_SET, TITLE_BIT5_SET, PROJECTOR_CLEAR, GOOD_FUTURE_BIT0_CLEAR, GOOD_FUTURES_BIT0_SET,
        AMY_CAPTURED_CLEAR
    }

    /** Empty is NO WRITE, unlike an empty State fact which means UNKNOWN current value. */
    record Writes(OptionalInt plcLoadFlagsByte, OptionalInt zoneActWord, OptionalInt checkpointByte,
                  OptionalInt projectorByte, OptionalInt goodFutureByte, OptionalInt amyCapturedByte) {
        Writes { octet(plcLoadFlagsByte);word(zoneActWord);octet(checkpointByte);octet(projectorByte);octet(goodFutureByte);octet(amyCapturedByte); }
        private static Writes none(){return new Writes(u(),u(),u(),u(),u(),u());}
    }
    /** Complete result or exact first-read prefix; neither is a live owner/publication certificate. */
    record Result(State progress, Writes writes, List<Store> stores, Cut cut, Optional<Read> missingRead) {
        Result {
            Objects.requireNonNull(progress);Objects.requireNonNull(writes);stores=List.copyOf(stores);
            Objects.requireNonNull(cut);Objects.requireNonNull(missingRead);
        }
    }

    enum SaveSite { NEW_GAME, RUN_R3 }
    enum SaveMode { BACKUP, TEMPORARY }
    /** Semantic request for the existing save record; its unrelated fields must be retained by that owner. */
    record ReadSaveRequest(SaveSite site, SaveMode mode) {
        ReadSaveRequest { Objects.requireNonNull(site);Objects.requireNonNull(mode); }
        CdCommand command(){return mode==SaveMode.BACKUP?CdCommand.READSAVE:CdCommand.RDTEMPSAVE;}
    }
    record SaveRead(Optional<ReadSaveRequest> request, Optional<Read> missingRead) {
        SaveRead {
            Objects.requireNonNull(request);Objects.requireNonNull(missingRead);
            if(request.isPresent()==missingRead.isPresent() || missingRead.isPresent()&&missingRead.orElseThrow()!=Read.SAVE_DISABLED_READ)
                throw new IllegalArgumentException("Save read must request data or stop at its policy read");
        }
    }
    /**
     * Explicit complete projection from an actual save-data producer; it owns no read completion.
     * Backup RAM.inc:44-51: svZone is ONE BYTE followed immediately by svTmAtkUnlock.
     * ReadSaveData's MOVE.W therefore loads both into savedLevel; the low byte is not assumed act0.
     * Time-attack records, padding and every other save field remain with the actual save owner.
     */
    record MainSaveFields(int zoneByte,int timeAttackUnlockByte,int unknownBackupByte,int goodFuturesByte,
                          int titleFlagsByte,int currentSpecialStageByte,int timeStonesByte) {
        MainSaveFields {
            octet(zoneByte);octet(timeAttackUnlockByte);octet(unknownBackupByte);octet(goodFuturesByte);
            octet(titleFlagsByte);octet(currentSpecialStageByte);octet(timeStonesByte);
        }
        int levelPairWord(){return (zoneByte<<8)|timeAttackUnlockByte;}
    }

    /** Ordered seven-field patch to an ACTUAL existing save record, never a fresh padded save image. */
    record SavePatch(OptionalInt savedLevelWord, OptionalInt goodFuturesByte, OptionalInt titleFlagsByte,
                     OptionalInt timeAttackUnlockByte, OptionalInt unknownBackupByte,
                     OptionalInt currentSpecialStageByte, OptionalInt timeStonesByte) {
        SavePatch {
            word(savedLevelWord);octet(goodFuturesByte);octet(titleFlagsByte);octet(timeAttackUnlockByte);
            octet(unknownBackupByte);octet(currentSpecialStageByte);octet(timeStonesByte);
            boolean gap=false;
            for(var field:List.of(savedLevelWord,goodFuturesByte,titleFlagsByte,timeAttackUnlockByte,unknownBackupByte,currentSpecialStageByte,timeStonesByte)) {
                if(field.isEmpty())gap=true;else if(gap)throw new IllegalArgumentException("Save patch must be a native field prefix");
            }
        }
        static SavePatch empty(){return new SavePatch(u(),u(),u(),u(),u(),u(),u());}
        /** Native1457 writes the low save byte first;1460 later replaces it with svTmAtkUnlock. */
        OptionalInt zoneAndUnlockWord(){
            if(savedLevelWord.isEmpty())return u();
            int raw=savedLevelWord.getAsInt();
            return k(timeAttackUnlockByte.isPresent()?(raw&0xFF00)|timeAttackUnlockByte.getAsInt():raw);
        }
        Optional<MainSaveFields> fields(){
            return complete()?Optional.of(new MainSaveFields(savedLevelWord.getAsInt()>>>8,timeAttackUnlockByte.getAsInt(),
                    unknownBackupByte.getAsInt(),goodFuturesByte.getAsInt(),titleFlagsByte.getAsInt(),
                    currentSpecialStageByte.getAsInt(),timeStonesByte.getAsInt())):Optional.empty();
        }
        boolean complete(){return timeStonesByte.isPresent();}
        int fieldCount(){int n=0;for(var f:List.of(savedLevelWord,goodFuturesByte,titleFlagsByte,timeAttackUnlockByte,unknownBackupByte,currentSpecialStageByte,timeStonesByte))if(f.isPresent())n++;return n;}
    }
    /** Request only, not a successful temporary/Backup RAM save or an acknowledgement token. */
    record WriteSaveRequest(SaveSite site, SaveMode mode, SavePatch patch) {
        WriteSaveRequest {
            Objects.requireNonNull(site);Objects.requireNonNull(mode);Objects.requireNonNull(patch);
            if(!patch.complete())throw new IllegalArgumentException("A write request needs all seven source fields");
        }
        CdCommand command(){return mode==SaveMode.BACKUP?CdCommand.WRITESAVE:CdCommand.WRTEMPSAVE;}
    }
    /** Source-field prefix before the actual GiveWordRAMAccess call at1465. */
    record SaveFields(SaveSite site,SavePatch patch,Optional<Read> missingRead) {
        SaveFields {
            Objects.requireNonNull(site);Objects.requireNonNull(patch);Objects.requireNonNull(missingRead);
            if(missingRead.isEmpty()){
                if(!patch.complete())throw new IllegalArgumentException("Prepared fields must be complete");
            }else{
                int expected=switch(missingRead.orElseThrow()){
                    case SAVED_LEVEL_WORD -> 0;case GOOD_FUTURES -> 1;case TITLE_FLAGS -> 2;
                    case TIME_ATTACK_UNLOCK -> 3;case UNKNOWN_BACKUP -> 4;case CURRENT_SPECIAL_STAGE -> 5;
                    case TIME_STONES -> 6;default -> -1;
                };
                if(patch.fieldCount()!=expected)throw new IllegalArgumentException("Save stop differs from its native field prefix");
            }
        }
    }
    /** Fresh post-transfer policy read; no save command or return is completed here. */
    record SaveWrite(SavePatch patch, Optional<WriteSaveRequest> request, Optional<Read> missingRead) {
        SaveWrite {
            Objects.requireNonNull(patch);Objects.requireNonNull(request);Objects.requireNonNull(missingRead);
            if(!patch.complete() || request.isPresent()==missingRead.isPresent()
                    || request.isPresent()&&!request.orElseThrow().patch().equals(patch)
                    || missingRead.isPresent()&&missingRead.orElseThrow()!=Read.SAVE_DISABLED_WRITE)
                throw new IllegalArgumentException("Save write needs its complete patch and request or reached policy stop");
        }
    }

    /** Explicit unknown retained/save-loaded history. This executes no clear or source operation. */
    static State unknown(){return new State(new SavedLevel(u(),u()),u(),u(),u(),u(),u(),u(),u());}

    /** Data projection of the actually selected IPX/MDINIT MAINVARS clear over
     * [FF0F00,FF2000),1088 LONGs. Its caller must own the original semantic ingress.
     * This does not run MDINIT's later title90 store, a save read, Title or NewGame. */
    static State mainVariablesCleared(){
        var z=k(0);return new State(new SavedLevel(z,z),z,z,z,z,z,z,z);
    }

    /**
     * NewGame/RunR1:165-170 postconditions only. The live caller owns selecting this semantic entry.
     * Title/unlock/unknown Backup byte/save policy survive; no previous Read/WriteSaveData success is inferred.
     */
    static Result newGameStores(State before) {
        Objects.requireNonNull(before);
        var after=new State(new SavedLevel(k(0),k(0)),k(0),before.titleFlagsByte(),before.timeAttackUnlockByte(),
                before.unknownBackupByte(),k(0),k(0),before.saveDisabledByte());
        return result(after,new Writes(k(0),k(0),u(),u(),u(),u()),List.of(Store.NEW_GAME_PLC_CLEAR,
                Store.NEW_GAME_ZONE_CLEAR,Store.NEW_GAME_SAVED_LEVEL_CLEAR,Store.NEW_GAME_GOOD_FUTURES_CLEAR,
                Store.NEW_GAME_SPECIAL_STAGE_CLEAR,Store.NEW_GAME_TIME_STONES_CLEAR),Cut.NEW_GAME_WRITE_SAVE_DATA);
    }

    /** RunBossLevel:616-629. Never reads Frame.zoneAct, low saved BYTE, time zone or goodFuture. */
    static Result bossReturned(State before,int livesByte) {
        Objects.requireNonNull(before);octet(livesByte);
        if(livesByte==0)return result(before,Writes.none(),List.of(),Cut.RUN_BOSS_GAME_OVER);
        if(before.savedLevel().zoneByte().isEmpty())return stopped(before,Writes.none(),List.of(),Cut.RUN_BOSS_SAVED_ZONE_READ,Read.SAVED_LEVEL_ZONE);
        int next=(before.savedLevel().zoneByte().getAsInt()+1)&0xFF;
        var stores=new ArrayList<Store>();stores.add(Store.SAVED_ZONE_INCREMENT);
        // CMP.B #7/BCS then SUBQ.B: revert the increment for every result>=7, NOT a universal clamp to6.
        if(next>=7){next=(next-1)&0xFF;stores.add(Store.SAVED_ZONE_CAP_DECREMENT);}
        var after=savedZone(before,next);stores.add(Store.CHECKPOINT_CLEAR);
        return result(after,new Writes(u(),u(),k(0),u(),u(),u()),stores,Cut.RETURN_TO_BOSS_CALLER);
    }

    /**
     * RunR1:178-187 after its actual RunR13 returns. Each RMW needs its real BYTE preimage.
     * Unknowns stop before the reached operand; already completed stores remain in the returned prefix.
     */
    static Result runR1AfterR13(State before,OptionalInt goodFutureByte) {
        Objects.requireNonNull(before);octet(goodFutureByte);
        State current=before;var writes=Writes.none();var stores=new ArrayList<Store>();
        if(current.timeAttackUnlockByte().isEmpty())return stopped(current,writes,stores,Cut.RUN_R1_UNLOCK_READ,Read.TIME_ATTACK_UNLOCK);
        if(3>current.timeAttackUnlockByte().getAsInt()){
            current=unlock(current,3);stores.add(Store.TIME_ATTACK_UNLOCK_WRITE);
        }
        if(current.titleFlagsByte().isEmpty())return stopped(current,writes,stores,Cut.RUN_R1_TITLE_FLAGS_READ,Read.TITLE_FLAGS);
        current=title(current,current.titleFlagsByte().getAsInt()|0x40);stores.add(Store.TITLE_BIT6_SET);
        current=title(current,current.titleFlagsByte().getAsInt()|0x20);stores.add(Store.TITLE_BIT5_SET);
        writes=new Writes(u(),u(),k(0),u(),u(),u());stores.add(Store.CHECKPOINT_CLEAR);
        writes=new Writes(u(),u(),k(0),k(0),u(),u());stores.add(Store.PROJECTOR_CLEAR);
        if(goodFutureByte.isEmpty())return stopped(current,writes,stores,Cut.RUN_R1_GOOD_FUTURE_READ,Read.GOOD_FUTURE);
        int old=goodFutureByte.getAsInt();writes=new Writes(u(),u(),k(0),k(0),k(old&0xFE),u());stores.add(Store.GOOD_FUTURE_BIT0_CLEAR);
        if((old&1)!=0){
            if(current.goodFuturesByte().isEmpty())return stopped(current,writes,stores,Cut.RUN_R1_GOOD_FUTURES_READ,Read.GOOD_FUTURES);
            current=goodFutures(current,current.goodFuturesByte().getAsInt()|1);stores.add(Store.GOOD_FUTURES_BIT0_SET);
        }
        return result(current,writes,stores,Cut.RUN_R3_WRITE_SAVE_DATA);
    }

    /** Pure projection of RunR3:194; caller must independently prove the actual WriteSaveData returned. */
    static Result runR3AfterSaveReturned(State before){Objects.requireNonNull(before);return result(before,new Writes(u(),u(),u(),u(),u(),k(0)),List.of(Store.AMY_CAPTURED_CLEAR),Cut.RUN_R31);}
    /** Pure projection of RunR3:196; caller must independently prove the actual RunR31 returned. */
    static Result runR3AfterR31Returned(State before){Objects.requireNonNull(before);return result(before,new Writes(u(),u(),u(),u(),u(),k(0)),List.of(Store.AMY_CAPTURED_CLEAR),Cut.RUN_R32);}

    /**
     * ReadSaveData:1423-1429 pure value projection, only after its caller's actual GetBuRAMData return.
     * An explicit MainSaveFields value is NOT proof that any read, command or ownership transfer occurred.
     */
    static State readSaveDataFieldsAfterRead(State current,MainSaveFields fields){
        Objects.requireNonNull(current);Objects.requireNonNull(fields);
        return new State(new SavedLevel(k(fields.zoneByte()),k(fields.timeAttackUnlockByte())),k(fields.goodFuturesByte()),
                k(fields.titleFlagsByte()),k(fields.timeAttackUnlockByte()),k(fields.unknownBackupByte()),
                k(fields.currentSpecialStageByte()),k(fields.timeStonesByte()),current.saveDisabledByte());
    }

    /**
     * GetBuRAMData:1441-1448, at the caller's actual1439 GiveWordRAMAccess return, FIRST in WriteSaveData.
     * No save field is sampled here. This request executes no transfer, SubCPU command or wait.
     */
    static SaveRead requestSaveRead(State current,SaveSite site) {
        Objects.requireNonNull(current);Objects.requireNonNull(site);
        if(current.saveDisabledByte().isEmpty())return new SaveRead(Optional.empty(),Optional.of(Read.SAVE_DISABLED_READ));
        return new SaveRead(Optional.of(new ReadSaveRequest(site,saveMode(current.saveDisabledByte().getAsInt()))),Optional.empty());
    }

    /**
     * WriteSaveData:1457-1475, for the caller's actual GetBuRAMData-return cut only.
     * This returns BEFORE GiveWordRAMAccess:1465; no policy is sampled here. The actual owner must
     * perform that semantic transfer before requestSaveWriteAfterFields samples FRESH policy:1468.
     * No read/write service is completed or acknowledged by these values.
     */
    static SaveFields prepareWriteSaveAfterRead(State current,SaveSite site) {
        Objects.requireNonNull(current);Objects.requireNonNull(site);
        var word=current.savedLevel().word();var patch=SavePatch.empty();
        if(word.isEmpty())return fieldsStopped(site,patch,Read.SAVED_LEVEL_WORD);
        patch=new SavePatch(word,u(),u(),u(),u(),u(),u());
        if(current.goodFuturesByte().isEmpty())return fieldsStopped(site,patch,Read.GOOD_FUTURES);
        patch=new SavePatch(word,current.goodFuturesByte(),u(),u(),u(),u(),u());
        if(current.titleFlagsByte().isEmpty())return fieldsStopped(site,patch,Read.TITLE_FLAGS);
        patch=new SavePatch(word,current.goodFuturesByte(),current.titleFlagsByte(),u(),u(),u(),u());
        if(current.timeAttackUnlockByte().isEmpty())return fieldsStopped(site,patch,Read.TIME_ATTACK_UNLOCK);
        patch=new SavePatch(word,current.goodFuturesByte(),current.titleFlagsByte(),current.timeAttackUnlockByte(),u(),u(),u());
        if(current.unknownBackupByte().isEmpty())return fieldsStopped(site,patch,Read.UNKNOWN_BACKUP);
        patch=new SavePatch(word,current.goodFuturesByte(),current.titleFlagsByte(),current.timeAttackUnlockByte(),current.unknownBackupByte(),u(),u());
        if(current.currentSpecialStageByte().isEmpty())return fieldsStopped(site,patch,Read.CURRENT_SPECIAL_STAGE);
        patch=new SavePatch(word,current.goodFuturesByte(),current.titleFlagsByte(),current.timeAttackUnlockByte(),current.unknownBackupByte(),current.currentSpecialStageByte(),u());
        if(current.timeStonesByte().isEmpty())return fieldsStopped(site,patch,Read.TIME_STONES);
        patch=new SavePatch(word,current.goodFuturesByte(),current.titleFlagsByte(),current.timeAttackUnlockByte(),current.unknownBackupByte(),current.currentSpecialStageByte(),current.timeStonesByte());
        return new SaveFields(site,patch,Optional.empty());
    }

    /** WriteSaveData:1467-1475, only at the actual1465 transfer return; never reuse earlier read mode. */
    static SaveWrite requestSaveWriteAfterFields(State current,SaveSite site,SavePatch patch){
        Objects.requireNonNull(current);Objects.requireNonNull(site);Objects.requireNonNull(patch);
        if(!patch.complete())throw new IllegalArgumentException("Write policy follows all seven original field copies");
        if(current.saveDisabledByte().isEmpty())return new SaveWrite(patch,Optional.empty(),Optional.of(Read.SAVE_DISABLED_WRITE));
        return new SaveWrite(patch,Optional.of(new WriteSaveRequest(site,saveMode(current.saveDisabledByte().getAsInt()),patch)),Optional.empty());
    }

    private static State savedZone(State s,int zone){return new State(new SavedLevel(k(zone),s.savedLevel().lowByte()),s.goodFuturesByte(),s.titleFlagsByte(),s.timeAttackUnlockByte(),s.unknownBackupByte(),s.currentSpecialStageByte(),s.timeStonesByte(),s.saveDisabledByte());}
    private static State unlock(State s,int value){return new State(s.savedLevel(),s.goodFuturesByte(),s.titleFlagsByte(),k(value),s.unknownBackupByte(),s.currentSpecialStageByte(),s.timeStonesByte(),s.saveDisabledByte());}
    private static State title(State s,int value){return new State(s.savedLevel(),s.goodFuturesByte(),k(value),s.timeAttackUnlockByte(),s.unknownBackupByte(),s.currentSpecialStageByte(),s.timeStonesByte(),s.saveDisabledByte());}
    private static State goodFutures(State s,int value){return new State(s.savedLevel(),k(value),s.titleFlagsByte(),s.timeAttackUnlockByte(),s.unknownBackupByte(),s.currentSpecialStageByte(),s.timeStonesByte(),s.saveDisabledByte());}
    private static Result result(State s,Writes w,List<Store> stores,Cut c){return new Result(s,w,stores,c,Optional.empty());}
    private static Result stopped(State s,Writes w,List<Store> stores,Cut c,Read r){return new Result(s,w,stores,c,Optional.of(r));}
    private static SaveFields fieldsStopped(SaveSite site,SavePatch patch,Read r){return new SaveFields(site,patch,Optional.of(r));}
    private static SaveMode saveMode(int raw){return (raw&1)==0?SaveMode.BACKUP:SaveMode.TEMPORARY;}
    private static OptionalInt k(int n){return OptionalInt.of(n);}
    private static OptionalInt u(){return OptionalInt.empty();}
    private static void octet(int n){if((n&~0xFF)!=0)throw new IllegalArgumentException("Expected a native BYTE");}
    private static void octet(OptionalInt n){Objects.requireNonNull(n,"explicit BYTE knowledge/no-write");if(n.isPresent())octet(n.getAsInt());}
    private static void word(OptionalInt n){Objects.requireNonNull(n,"explicit WORD knowledge/no-write");if(n.isPresent()&&(n.getAsInt()&~0xFFFF)!=0)throw new IllegalArgumentException("Expected a native WORD");}
}
