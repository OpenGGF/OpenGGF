package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.CdIpxProgress.*;

/** Tests authored first; labelled complete/partial semantic component values, no live IPX/save authority. Java UNRUN. */
class TestCdIpxProgress {
    @Test void retainedUnknownStateInventsNoProgressSaveOrUnlockBytes() {
        var s=unknown();assertTrue(s.savedLevel().zoneByte().isEmpty());assertTrue(s.savedLevel().lowByte().isEmpty());
        assertTrue(s.savedLevel().word().isEmpty());for(var b:bytes(s))assertTrue(b.isEmpty());
    }
    @Test void savedWordKeepsIndependentHighAndLowKnowledgeAndStrictByteWidths() {
        var hi=new SavedLevel(k(0xA7),u());var lo=new SavedLevel(u(),k(0xDC));
        assertTrue(hi.word().isEmpty());assertTrue(lo.word().isEmpty());assertEquals(0xA7DC,new SavedLevel(k(0xA7),k(0xDC)).word().orElseThrow());
        assertThrows(IllegalArgumentException.class,()->new SavedLevel(k(256),u()));assertThrows(IllegalArgumentException.class,()->new SavedLevel(u(),k(-1)));
        assertThrows(NullPointerException.class,()->new SavedLevel(null,u()));
    }
    @Test void everyProgressByteRejectsUnknownWidthWithoutMaskingOrNormalization() {
        var a=known();for(int bad:new int[]{-1,256,65535})for(int field=0;field<7;field++){
            final int f=field;assertThrows(IllegalArgumentException.class,()->replace(a,f,k(bad)));
        }
        assertThrows(NullPointerException.class,()->new State(null,u(),u(),u(),u(),u(),u(),u()));
        assertThrows(NullPointerException.class,()->replace(a,0,null));
    }
    @Test void newGameEstablishesOnlyActualSixOrderedStoresFromUnknownHistory() {
        var out=newGameStores(unknown());var a=out.progress();assertEquals(0,a.savedLevel().word().orElseThrow());
        assertEquals(k(0),a.goodFuturesByte());assertEquals(k(0),a.currentSpecialStageByte());assertEquals(k(0),a.timeStonesByte());
        assertTrue(Arrays.stream(State.class.getRecordComponents()).noneMatch(c->c.getName().equals("goodFutureFlags")),"IPX achieved goodFutures is distinct from the level flags byte");
        assertTrue(a.titleFlagsByte().isEmpty());assertTrue(a.timeAttackUnlockByte().isEmpty());assertTrue(a.unknownBackupByte().isEmpty());assertTrue(a.saveDisabledByte().isEmpty());
        assertEquals(k(0),out.writes().plcLoadFlagsByte());assertEquals(k(0),out.writes().zoneActWord());
        assertEquals(List.of(Store.NEW_GAME_PLC_CLEAR,Store.NEW_GAME_ZONE_CLEAR,Store.NEW_GAME_SAVED_LEVEL_CLEAR,Store.NEW_GAME_GOOD_FUTURES_CLEAR,Store.NEW_GAME_SPECIAL_STAGE_CLEAR,Store.NEW_GAME_TIME_STONES_CLEAR),out.stores());
        assertEquals(Cut.NEW_GAME_WRITE_SAVE_DATA,out.cut());assertTrue(out.missingRead().isEmpty());
    }
    @Test void newGamePreservesHistoricalTitleUnlockUnknownSavePolicyAndUnrelatedBits() {
        var before=known();var out=newGameStores(before);var a=out.progress();
        assertSame(before.titleFlagsByte(),a.titleFlagsByte());assertSame(before.timeAttackUnlockByte(),a.timeAttackUnlockByte());
        assertSame(before.unknownBackupByte(),a.unknownBackupByte());assertSame(before.saveDisabledByte(),a.saveDisabledByte());
        assertEquals(known(),before);assertTrue(out.writes().checkpointByte().isEmpty());assertTrue(out.writes().projectorByte().isEmpty());
        assertTrue(out.writes().goodFutureByte().isEmpty());assertTrue(out.writes().amyCapturedByte().isEmpty());
    }
    @Test void all256SavedHighBytesFollowAddByteUnsignedCapThenCheckpointWithoutTouchingLow() {
        for(int old=0;old<256;old++){
            var before=withSaved(known(),new SavedLevel(k(old),k(0xD3)));var out=bossReturned(before,3);int added=(old+1)&255;int expected=added<7?added:(added-1)&255;
            assertEquals(k(expected),out.progress().savedLevel().zoneByte(),"old"+old);assertSame(before.savedLevel().lowByte(),out.progress().savedLevel().lowByte());
            assertEquals(before,withSaved(out.progress(),before.savedLevel()));assertEquals(k(0),out.writes().checkpointByte());
            assertEquals(added<7?List.of(Store.SAVED_ZONE_INCREMENT,Store.CHECKPOINT_CLEAR):List.of(Store.SAVED_ZONE_INCREMENT,Store.SAVED_ZONE_CAP_DECREMENT,Store.CHECKPOINT_CLEAR),out.stores());
            assertEquals(Cut.RETURN_TO_BOSS_CALLER,out.cut());assertTrue(out.missingRead().isEmpty());
        }
    }
    @Test void capRevertsIncrementRatherThanClampingArbitraryHistoricalBytesToSix() {
        for(int[] r:new int[][]{{5,6},{6,6},{7,7},{0xFE,0xFE},{0xFF,0}})assertEquals(k(r[1]),bossReturned(withSaved(known(),new SavedLevel(k(r[0]),u())),1).progress().savedLevel().zoneByte());
    }
    @Test void bossReturnNeedsOnlySavedHighAndPreservesUnknownLowByte() {
        var a=withSaved(known(),new SavedLevel(k(0),u()));var out=bossReturned(a,255);
        assertEquals(k(1),out.progress().savedLevel().zoneByte());assertTrue(out.progress().savedLevel().lowByte().isEmpty());assertTrue(out.progress().savedLevel().word().isEmpty());
        assertEquals(0,prepareWriteSaveAfterRead(out.progress(),SaveSite.RUN_R3).patch().fieldCount());
    }
    @Test void zeroLivesBranchesBeforeAnySavedReadOrCheckpointWrite() {
        var a=unknown();var out=bossReturned(a,0);assertSame(a,out.progress());assertEquals(Cut.RUN_BOSS_GAME_OVER,out.cut());
        assertTrue(out.stores().isEmpty());assertTrue(out.missingRead().isEmpty());assertTrue(out.writes().checkpointByte().isEmpty());
    }
    @Test void unknownSavedHighStopsBeforeAddAndRetainsActualKnownLow() {
        var a=withSaved(known(),new SavedLevel(u(),k(0x91)));var out=bossReturned(a,1);
        assertSame(a,out.progress());assertEquals(Cut.RUN_BOSS_SAVED_ZONE_READ,out.cut());assertEquals(Optional.of(Read.SAVED_LEVEL_ZONE),out.missingRead());
        assertTrue(out.stores().isEmpty());assertTrue(out.writes().checkpointByte().isEmpty());
    }
    @Test void invalidLivesWidthsAreRejectedBeforeValueComputation() {
        for(int bad:new int[]{-1,256,65535})assertThrows(IllegalArgumentException.class,()->bossReturned(known(),bad));
        assertThrows(NullPointerException.class,()->bossReturned(null,1));
    }
    @Test void unlockUnknownIsTheFirstRunR1TailReadBeforeTitleAndLiveWrites() {
        var a=replace(known(),2,u());var out=runR1AfterR13(a,u());assertSame(a,out.progress());
        assertEquals(Cut.RUN_R1_UNLOCK_READ,out.cut());assertEquals(Optional.of(Read.TIME_ATTACK_UNLOCK),out.missingRead());
        assertTrue(out.stores().isEmpty());assertTrue(out.writes().checkpointByte().isEmpty());assertTrue(out.writes().goodFutureByte().isEmpty());
    }
    @Test void unlockComparisonIsUnsignedAndWritesThreeOnlyWhenGreaterThanOld() {
        for(int old=0;old<256;old++){
            var a=replace(known(),2,k(old));var out=runR1AfterR13(a,k(0));assertEquals(k(Math.max(3,old)),out.progress().timeAttackUnlockByte());
            assertEquals(old<3,out.stores().contains(Store.TIME_ATTACK_UNLOCK_WRITE));
        }
    }
    @Test void titleUnknownStopsAfterRealUnlockPrefixAndBeforeFirstBset() {
        var a=replace(replace(known(),1,u()),2,k(0));var out=runR1AfterR13(a,k(1));
        assertEquals(k(3),out.progress().timeAttackUnlockByte());assertTrue(out.progress().titleFlagsByte().isEmpty());
        assertEquals(List.of(Store.TIME_ATTACK_UNLOCK_WRITE),out.stores());assertEquals(Optional.of(Read.TITLE_FLAGS),out.missingRead());
        assertEquals(Cut.RUN_R1_TITLE_FLAGS_READ,out.cut());assertTrue(out.writes().checkpointByte().isEmpty());
    }
    @Test void knownTitleBsetsRetainAllOtherBitsAndSourceOrderedStores() {
        var a=replace(replace(known(),1,k(0x89)),2,k(0));var out=runR1AfterR13(a,k(0xA5));
        assertEquals(k(0xE9),out.progress().titleFlagsByte());assertEquals(k(0xA4),out.writes().goodFutureByte());
        assertEquals(k(0),out.writes().checkpointByte());assertEquals(k(0),out.writes().projectorByte());
        assertEquals(k(0xA5),out.progress().goodFuturesByte());assertEquals(Cut.RUN_R3_WRITE_SAVE_DATA,out.cut());
        assertEquals(List.of(Store.TIME_ATTACK_UNLOCK_WRITE,Store.TITLE_BIT6_SET,Store.TITLE_BIT5_SET,Store.CHECKPOINT_CLEAR,Store.PROJECTOR_CLEAR,Store.GOOD_FUTURE_BIT0_CLEAR,Store.GOOD_FUTURES_BIT0_SET),out.stores());
    }
    @Test void unknownGoodFutureStopsAfterTitleCheckpointProjectorPrefixBeforeBclr() {
        var out=runR1AfterR13(known(),u());assertEquals(Cut.RUN_R1_GOOD_FUTURE_READ,out.cut());
        assertEquals(Optional.of(Read.GOOD_FUTURE),out.missingRead());assertEquals(k(0),out.writes().checkpointByte());assertEquals(k(0),out.writes().projectorByte());
        assertTrue(out.writes().goodFutureByte().isEmpty());assertEquals(List.of(Store.TITLE_BIT6_SET,Store.TITLE_BIT5_SET,Store.CHECKPOINT_CLEAR,Store.PROJECTOR_CLEAR),out.stores());
    }
    @Test void all256GoodFutureBytesUseOldBitZeroAndClearOnlyThatBit() {
        for(int old=0;old<256;old++){
            var out=runR1AfterR13(known(),k(old));assertEquals(k(old&0xFE),out.writes().goodFutureByte());
            assertEquals(k((old&1)==0?0xA4:0xA5),out.progress().goodFuturesByte());assertEquals((old&1)!=0,out.stores().contains(Store.GOOD_FUTURES_BIT0_SET));
        }
    }
    @Test void clearOldGoodFutureBitSkipsUnknownHistoricalAchievedFlagsRead() {
        var a=replace(known(),0,u());var out=runR1AfterR13(a,k(0x82));
        assertTrue(out.progress().goodFuturesByte().isEmpty());assertTrue(out.missingRead().isEmpty());assertEquals(Cut.RUN_R3_WRITE_SAVE_DATA,out.cut());assertEquals(k(0x82),out.writes().goodFutureByte());
    }
    @Test void setOldGoodFutureBitStopsAtUnknownAchievedFlagsAfterClearPrefix() {
        var a=replace(known(),0,u());var out=runR1AfterR13(a,k(0x83));
        assertEquals(Optional.of(Read.GOOD_FUTURES),out.missingRead());assertEquals(Cut.RUN_R1_GOOD_FUTURES_READ,out.cut());
        assertEquals(k(0x82),out.writes().goodFutureByte());assertEquals(Store.GOOD_FUTURE_BIT0_CLEAR,out.stores().getLast());assertTrue(out.progress().goodFuturesByte().isEmpty());
    }
    @Test void explicitPostNewGameKnowledgeDoesNotFillUnknownUnlockOrBorrowCurrentZoneAct() {
        var fresh=newGameStores(unknown()).progress();var boss=bossReturned(fresh,3);assertEquals(k(1),boss.progress().savedLevel().zoneByte());assertEquals(k(0),boss.progress().savedLevel().lowByte());
        var tail=runR1AfterR13(boss.progress(),k(0));assertEquals(Optional.of(Read.TIME_ATTACK_UNLOCK),tail.missingRead());
        assertEquals(0x100,boss.progress().savedLevel().word().orElseThrow());
        // The API accepts no Frame/zoneAct input; known0 follows only the explicit source-backed NewGame projection.
        var method=Arrays.stream(CdIpxProgress.class.getDeclaredMethods()).filter(m->m.getName().equals("bossReturned")).findFirst().orElseThrow();
        assertEquals(List.of(State.class,int.class),Arrays.asList(method.getParameterTypes()));
    }
    @Test void saveReadRequestStopsAtUnknownPolicyBeforePayloadReads() {
        for(var site:SaveSite.values()){
            var out=requestSaveRead(unknown(),site);assertTrue(out.request().isEmpty());assertEquals(Optional.of(Read.SAVE_DISABLED_READ),out.missingRead());
        }
    }
    @Test void all256SavePolicyBytesChooseModeByBitZeroOnlyWithoutMutatingProgress() {
        for(int raw=0;raw<256;raw++){
            var a=replace(known(),6,k(raw));var out=requestSaveRead(a,SaveSite.NEW_GAME);var request=out.request().orElseThrow();
            assertEquals((raw&1)==0?SaveMode.BACKUP:SaveMode.TEMPORARY,request.mode());assertEquals((raw&1)==0?CdCommand.READSAVE:CdCommand.RDTEMPSAVE,request.command());assertEquals(SaveSite.NEW_GAME,request.site());assertTrue(out.missingRead().isEmpty());assertEquals(k(raw),a.saveDisabledByte());
        }
    }
    @Test void saveWordRequiresBothSavedBytesAndCannotUseProgressHighAlone() {
        for(var saved:List.of(new SavedLevel(k(1),u()),new SavedLevel(u(),k(0)),new SavedLevel(u(),u()))){
            var out=prepareWriteSaveAfterRead(withSaved(known(),saved),SaveSite.RUN_R3);assertEquals(Optional.of(Read.SAVED_LEVEL_WORD),out.missingRead());assertEquals(0,out.patch().fieldCount());assertFalse(out.patch().complete());
        }
    }
    @Test void eachSaveFieldUnknownRetainsOnlyTheEarlierNativeSemanticPatchPrefix() {
        Read[] fields={Read.GOOD_FUTURES,Read.TITLE_FLAGS,Read.TIME_ATTACK_UNLOCK,Read.UNKNOWN_BACKUP,Read.CURRENT_SPECIAL_STAGE,Read.TIME_STONES};
        for(int i=0;i<fields.length;i++){
            var a=replace(known(),i,u());var out=prepareWriteSaveAfterRead(a,SaveSite.RUN_R3);assertEquals(Optional.of(fields[i]),out.missingRead());
            assertEquals(i+1,out.patch().fieldCount());assertFalse(out.patch().complete());assertEquals(k(0x04D3),out.patch().savedLevelWord());
            assertEquals(k(i<3?0x04D3:0x0429),out.patch().zoneAndUnlockWord());
        }
    }
    @Test void saveWriteReReadsPolicyAfterAllSevenFieldStoresRatherThanCachingReadRequestMode() {
        var before=replace(known(),6,k(0));assertEquals(SaveMode.BACKUP,requestSaveRead(before,SaveSite.RUN_R3).request().orElseThrow().mode());
        var prepared=prepareWriteSaveAfterRead(before,SaveSite.RUN_R3);assertTrue(prepared.patch().complete());assertTrue(prepared.missingRead().isEmpty());
        assertEquals(CdCommand.WRITESAVE,requestSaveWriteAfterFields(before,SaveSite.RUN_R3,prepared.patch()).request().orElseThrow().command());
        var unknownPolicy=requestSaveWriteAfterFields(replace(before,6,u()),SaveSite.RUN_R3,prepared.patch());assertTrue(unknownPolicy.patch().complete());assertEquals(7,unknownPolicy.patch().fieldCount());assertEquals(Optional.of(Read.SAVE_DISABLED_WRITE),unknownPolicy.missingRead());assertTrue(unknownPolicy.request().isEmpty());
        var out=requestSaveWriteAfterFields(replace(before,6,k(1)),SaveSite.RUN_R3,prepared.patch());var request=out.request().orElseThrow();assertEquals(SaveMode.TEMPORARY,request.mode());assertEquals(CdCommand.WRTEMPSAVE,request.command());assertEquals(SaveSite.RUN_R3,request.site());assertTrue(out.missingRead().isEmpty());
        assertEquals(new SavePatch(k(0x04D3),k(0xA4),k(0x89),k(0x29),k(0x73),k(5),k(0x55)),request.patch());
    }
    @Test void runR3AmyStoresAreSeparatePureReturnCutsWithoutInventedSaveCompletion() {
        var a=unknown();var saved=runR3AfterSaveReturned(a);var act1=runR3AfterR31Returned(a);
        assertSame(a,saved.progress());assertSame(a,act1.progress());assertEquals(Cut.RUN_R31,saved.cut());assertEquals(Cut.RUN_R32,act1.cut());
        for(var out:List.of(saved,act1)){assertEquals(k(0),out.writes().amyCapturedByte());assertEquals(List.of(Store.AMY_CAPTURED_CLEAR),out.stores());assertTrue(out.writes().checkpointByte().isEmpty());}
        assertTrue(Arrays.stream(CdIpxProgress.class.getDeclaredMethods()).noneMatch(m->m.getName().equals("completeSave")||m.getName().equals("acknowledgeSave")));
    }
    @Test void immutableFiniteValuesRejectHolesWidthsAndNullsAndExposeNoOwnerOrCapture() {
        var out=runR1AfterR13(known(),k(0));assertThrows(UnsupportedOperationException.class,()->out.stores().clear());
        assertThrows(IllegalArgumentException.class,()->new SavePatch(u(),k(1),u(),u(),u(),u(),u()));
        assertThrows(IllegalArgumentException.class,()->new SavePatch(k(65536),u(),u(),u(),u(),u(),u()));
        assertThrows(IllegalArgumentException.class,()->new WriteSaveRequest(SaveSite.RUN_R3,SaveMode.BACKUP,SavePatch.empty()));
        assertThrows(IllegalArgumentException.class,()->new SaveWrite(SavePatch.empty(),Optional.empty(),Optional.of(Read.GOOD_FUTURES)));
        assertThrows(IllegalArgumentException.class,()->new SaveWrite(SavePatch.empty(),Optional.empty(),Optional.of(Read.SAVE_DISABLED_READ)));
        assertThrows(IllegalArgumentException.class,()->requestSaveWriteAfterFields(known(),SaveSite.RUN_R3,SavePatch.empty()));
        assertThrows(NullPointerException.class,()->requestSaveRead(known(),null));assertThrows(NullPointerException.class,()->prepareWriteSaveAfterRead(null,SaveSite.RUN_R3));
        assertThrows(IllegalArgumentException.class,()->runR1AfterR13(known(),k(256)));assertThrows(NullPointerException.class,()->runR1AfterR13(known(),null));
        assertFalse(Modifier.isPublic(CdIpxProgress.class.getModifiers()));assertEquals(1,CdIpxProgress.class.getDeclaredConstructors().length);assertTrue(Modifier.isPrivate(CdIpxProgress.class.getDeclaredConstructors()[0].getModifiers()));
        assertTrue(Arrays.stream(CdIpxProgress.class.getDeclaredMethods()).noneMatch(m->m.getName().contains("restore")||m.getName().contains("capture")||m.getName().contains("adopt")));
    }
    @Test void nativeSaveZoneWordOverlapsUnlockAndLaterByteStoreWinsWithoutSkippingEarlierRead() {
        var a=known();var out=prepareWriteSaveAfterRead(a,SaveSite.RUN_R3);var patch=out.patch();
        assertEquals(k(0x04D3),patch.savedLevelWord(),"The original MOVE.W operand retains saved lowD3");
        assertEquals(k(0x0429),patch.zoneAndUnlockWord(),"svTmAtkUnlock immediately follows svZone and is overwritten later");
        var fields=patch.fields().orElseThrow();assertEquals(new MainSaveFields(4,0x29,0x73,0xA4,0x89,5,0x55),fields);
        assertEquals(0x0429,fields.levelPairWord());assertEquals(k(0x04D3),a.savedLevel().word(),"Writing a save does not mutate the live savedLevel");
        assertTrue(SavePatch.empty().fields().isEmpty());assertTrue(SavePatch.empty().zoneAndUnlockWord().isEmpty());
    }
    @Test void sourceReadSaveDataProjectionUsesZoneUnlockPairAndKeepsSavePolicy() {
        var before=replace(unknown(),6,k(0xA0));var fields=new MainSaveFields(6,0x15,0x81,0x3F,0xE0,4,0x7F);
        var after=readSaveDataFieldsAfterRead(before,fields);assertEquals(k(0x0615),after.savedLevel().word());
        assertEquals(k(0x15),after.timeAttackUnlockByte());assertEquals(k(0x81),after.unknownBackupByte());assertEquals(k(0x3F),after.goodFuturesByte());
        assertEquals(k(0xE0),after.titleFlagsByte());assertEquals(k(4),after.currentSpecialStageByte());assertEquals(k(0x7F),after.timeStonesByte());assertSame(before.saveDisabledByte(),after.saveDisabledByte());
        assertThrows(IllegalArgumentException.class,()->new MainSaveFields(256,0,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new MainSaveFields(0,-1,0,0,0,0,0));
        assertThrows(NullPointerException.class,()->readSaveDataFieldsAfterRead(before,null));
    }
    private static OptionalInt k(int n){return OptionalInt.of(n);}private static OptionalInt u(){return OptionalInt.empty();}
    private static State known(){return new State(new SavedLevel(k(4),k(0xD3)),k(0xA4),k(0x89),k(0x29),k(0x73),k(5),k(0x55),k(0xA0));}
    private static State withSaved(State a,SavedLevel level){return new State(level,a.goodFuturesByte(),a.titleFlagsByte(),a.timeAttackUnlockByte(),a.unknownBackupByte(),a.currentSpecialStageByte(),a.timeStonesByte(),a.saveDisabledByte());}
    private static List<OptionalInt> bytes(State a){return List.of(a.goodFuturesByte(),a.titleFlagsByte(),a.timeAttackUnlockByte(),a.unknownBackupByte(),a.currentSpecialStageByte(),a.timeStonesByte(),a.saveDisabledByte());}
    private static State replace(State a,int field,OptionalInt x){var b=bytes(a).toArray(OptionalInt[]::new);b[field]=x;return new State(a.savedLevel(),b[0],b[1],b[2],b[3],b[4],b[5],b[6]);}
}
