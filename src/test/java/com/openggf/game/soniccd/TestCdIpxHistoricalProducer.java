package com.openggf.game.soniccd;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** SOURCE ONLY / UNRUN. Literal source vectors; records are not original-operation authority. */
class TestCdIpxHistoricalProducer {
    @Test void actualMainVariableClearProjectionEstablishesAllCoveredFactsWithoutOldReads(){
        var p=CdIpxProgress.mainVariablesCleared();assertEquals(OptionalInt.of(0),p.savedLevel().word());
        for(var v:List.of(p.timeAttackUnlockByte(),p.titleFlagsByte(),p.unknownBackupByte(),p.goodFuturesByte(),p.currentSpecialStageByte(),p.timeStonesByte(),p.saveDisabledByte()))assertEquals(OptionalInt.of(0),v);
    }
    @Test void laterNewGameNeverManufacturesHistoryAbsentTheEarlierClearOrRead(){
        var p=CdIpxProgress.newGameStores(CdIpxProgress.unknown()).progress();assertTrue(p.timeAttackUnlockByte().isEmpty());assertTrue(p.titleFlagsByte().isEmpty());assertTrue(p.unknownBackupByte().isEmpty());assertTrue(p.saveDisabledByte().isEmpty());assertEquals(OptionalInt.of(0),p.savedLevel().word());
    }
    @Test void readOverwritesEarlierClearThenNewGamePreservesSeparateUnlockAndSaveAlias(){
        var loaded=CdIpxProgress.readSaveDataFieldsAfterRead(CdIpxProgress.mainVariablesCleared(),new CdIpxProgress.MainSaveFields(2,0xFE,0xF1,0xA5,0x90,5,0x3F));assertEquals(OptionalInt.of(0x2FE),loaded.savedLevel().word());
        var fresh=CdIpxProgress.newGameStores(loaded).progress();assertEquals(OptionalInt.of(0),fresh.savedLevel().word());assertEquals(OptionalInt.of(0xFE),fresh.timeAttackUnlockByte());assertEquals(OptionalInt.of(0x90),fresh.titleFlagsByte());assertEquals(OptionalInt.of(0xF1),fresh.unknownBackupByte());
        var fields=CdIpxProgress.prepareWriteSaveAfterRead(fresh,CdIpxProgress.SaveSite.NEW_GAME);assertEquals(0xFE,fields.patch().fields().orElseThrow().levelPairWord());
    }
    @Test void unknownUnlockRemainsFirstReadAndProducesNoCallerStores(){
        var p=CdIpxProgress.newGameStores(CdIpxProgress.unknown()).progress();var r=CdIpxProgress.runR1AfterR13(p,OptionalInt.empty());assertEquals(Optional.of(CdIpxProgress.Read.TIME_ATTACK_UNLOCK),r.missingRead());assertTrue(r.stores().isEmpty());assertSame(p,r.progress());
    }
    @Test void unsignedUnlockAndPartialTitleReadKeepOnlyReachedNativePrefix(){
        var u=OptionalInt.empty();var p=new CdIpxProgress.State(new CdIpxProgress.SavedLevel(u,u),u,u,OptionalInt.of(2),u,u,u,u);var r=CdIpxProgress.runR1AfterR13(p,u);assertEquals(Optional.of(CdIpxProgress.Read.TITLE_FLAGS),r.missingRead());assertEquals(OptionalInt.of(3),r.progress().timeAttackUnlockByte());assertEquals(List.of(CdIpxProgress.Store.TIME_ATTACK_UNLOCK_WRITE),r.stores());
        p=new CdIpxProgress.State(p.savedLevel(),u,u,OptionalInt.of(0xFE),u,u,u,u);r=CdIpxProgress.runR1AfterR13(p,u);assertEquals(OptionalInt.of(0xFE),r.progress().timeAttackUnlockByte());assertTrue(r.stores().isEmpty());
    }
    @Test void clearBitGoodFutureDoesNotReadUnknownGoodFuturesButSetBitDoes(){
        var z=OptionalInt.of(0);var u=OptionalInt.empty();var p=new CdIpxProgress.State(new CdIpxProgress.SavedLevel(z,z),u,z,z,u,u,z,u);
        var r=CdIpxProgress.runR1AfterR13(p,OptionalInt.of(0xFE));assertTrue(r.missingRead().isEmpty());assertTrue(r.progress().goodFuturesByte().isEmpty());assertEquals(OptionalInt.of(0xFE),r.writes().goodFutureByte());
        r=CdIpxProgress.runR1AfterR13(p,OptionalInt.of(0xFF));assertEquals(Optional.of(CdIpxProgress.Read.GOOD_FUTURES),r.missingRead());assertEquals(OptionalInt.of(0xFE),r.writes().goodFutureByte());assertTrue(r.progress().goodFuturesByte().isEmpty());
    }
    @Test void finiteKnownCallerDoesNotClaimItsFollowingSaveReturned(){
        var r=CdIpxProgress.runR1AfterR13(CdIpxProgress.mainVariablesCleared(),OptionalInt.of(0));assertEquals(CdIpxProgress.Cut.RUN_R3_WRITE_SAVE_DATA,r.cut());assertEquals(OptionalInt.of(3),r.progress().timeAttackUnlockByte());assertEquals(OptionalInt.of(0x60),r.progress().titleFlagsByte());assertEquals(OptionalInt.of(0),r.writes().checkpointByte());assertEquals(OptionalInt.of(0),r.writes().projectorByte());assertTrue(r.writes().amyCapturedByte().isEmpty());
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
