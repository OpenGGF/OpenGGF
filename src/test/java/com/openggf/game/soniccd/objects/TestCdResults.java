package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Constructed source vectors; all Java, compilation and discovery remain UNRUN here.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Results/Main.asm:42-366;
 * Object Spawner.asm:11-20,329-340; shared AddPoints HUD and Points/Main.asm:157-180.
 * Full64/all96 comparisons describe the selected component, never World/Frame/programme authority.
 * No media, clock, act completion, save execution, pixels, audio playback or next-stage claim.
 */
class TestCdResults {
    private static final int WHOLE=40, MAP=0x20E280;
    private static final byte[] INIT=hex("00cc0000012000000110020000f0000100cc000001200002");

    @Test void finiteUnreadAssetsAndMissingOwnersDoNotPreventARealDelayReturn() {
        var h=new Harness(new CdResults.Assets(bytes(new byte[0]),bytes(new byte[0]),MAP),EnumSet.allOf(Port.class));
        h.edit(s -> s.moveByte(ROUTINE,0).moveWord(RESULTS_TIMER_WORD,0x02A7)); h.run();
        assertEquals(0x01A7,h.slot().word(RESULTS_TIMER_WORD)); assertTrue(h.order.isEmpty());
        assertThrows(NullPointerException.class,() -> CdResults.Assets.fromPastAssets(null));
    }

    @Test void routineDispatchRejectsEveryOutOfTableFormBeforeReadingOrWritingAnything() {
        for(int raw:new int[]{1,3,5,7,9,10,128,254,255}) {
            var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,raw)); var expected=images(h);
            var e=assertThrows(CdResults.UnsupportedResults.class,h::run);
            assertEquals(CdResults.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary()); assertImages(expected,h); assertTrue(h.order.isEmpty());
        }
    }

    @Test void initUsesOnlyTheHighTimerByteAndLoadsCue16BeforeRoutineAdvanceAndWaitHeadRead() {
        int[][] vectors={{0,255,0},{1,0,2},{2,1,0},{128,127,0},{255,254,0}};
        for(var v:vectors) {
            var h=new Harness(); h.values.put(CdResults.ReadField.PLC_HEAD_LONG,1);
            h.edit(s -> dirty(s).moveByte(ID,0x3A).moveByte(ROUTINE,0).moveWord(RESULTS_TIMER_WORD,(v[0]<<8)|0xB7));
            var expected=images(h); var b=expected.get(WHOLE-32); b[0x32]=(byte)v[1]; b[0x24]=(byte)v[2];
            h.beforePlc=c -> { assertEquals(16,c.nativeId()); assertEquals(0,c.expectedSource().routine()); assertEquals(0x00B7,c.expectedSource().word(RESULTS_TIMER_WORD)); };
            h.run(); assertImages(expected,h);
            assertEquals(v[0]==1?List.of("plc:16","read:PLC_HEAD_LONG"):List.of(),h.order);
        }
    }

    @Test void cueFailureRetainsTheByteDecrementButNotTheFollowingRoutineAdvance() {
        var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,0).moveWord(RESULTS_TIMER_WORD,0x0173)); var expected=images(h);
        expected.get(WHOLE-32)[0x32]=0; var original=new AssertionError("queue"); h.beforePlc=c -> { throw original; };
        assertSame(original,assertThrows(AssertionError.class,h::run)); assertImages(expected,h); assertEquals(0,original.getSuppressed().length);
    }

    @Test void waitReadsActualRawHeadFirstAndDoesNotInferQueueReadiness() {
        for(int head:new int[]{1,-1,Integer.MIN_VALUE}) {
            var h=new Harness(); h.values.put(CdResults.ReadField.PLC_HEAD_LONG,head); var before=images(h);
            h.run(); assertImages(before,h); assertEquals(List.of("read:PLC_HEAD_LONG"),h.order);
        }
    }

    @Test void waitOffscreenGateWrapsCameraWordAndUsesStrictUnsignedComparison() {
        int[][] vectors={{1000,1336,0},{1000,1337,1},{65520,320,0},{65520,321,1},{65520,0,0},{0x7F00,0x8051,1}};
        for(var v:vectors) {
            var h=new Harness(); h.loadRows(); h.values.put(CdResults.ReadField.CAMERA_X_WORD,v[0]); h.values.put(CdResults.ReadField.PLAYER_X_WORD,v[1]);
            h.run(); assertEquals(v[2]==1?4:2,h.slot().routine());
            assertEquals(v[2]==1?3:0,h.rows()); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void exactSszWordBypassesPlayerReadsAndEachRawFutureByteChoosesTheNativeRoot() {
        for(int zone:new int[]{0,0x502,0x8502}) for(int good:new int[]{0,1,255}) {
            var h=new Harness(); h.loadRows(); h.values.put(CdResults.ReadField.ZONE_ACT_WORD,zone); h.values.put(CdResults.ReadField.GOOD_FUTURE_BYTE,good);
            h.values.put(CdResults.ReadField.ACT_BYTE,255); h.run();
            assertEquals(zone==0x502?0:1,h.reads.stream().filter(c -> c.field()==CdResults.ReadField.CAMERA_X_WORD).count());
            assertEquals(MAP+(zone==0x502?10:0)+(good==0?0:20),h.slot().longBits(MAPPINGS));
            assertEquals(zone==0x502?0x82F2:0x83C4,h.slot().word(TILE)); assertEquals(1,h.pool().state(33).unsignedByte(MAP_FRAME));
        }
    }

    @Test void rowInitializationPreservesEveryDirtyNonwriteAndRunsNoAllocatedChildBody() {
        var h=new Harness(); h.loadRows(); h.edit(s -> dirty(s).moveByte(ID,0x3A).moveByte(ROUTINE,2));
        for(int whole:new int[]{32,33}) h.pool().write(dirty(h.pool().state(whole)).moveByte(ID,0));
        var expected=images(h); int[][] rows={{WHOLE,204,0,288,0},{32,272,512,240,1},{33,204,0,288,2}};
        for(var r:rows) { var b=expected.get(r[0]-32); w(b,0x32,480); b[0]=0x3A; b[0x24]=4; w(b,2,0x83C4); l(b,4,MAP);
            w(b,0x0A,r[1]); w(b,8,r[2]); w(b,0x2A,r[3]); b[0x1A]=(byte)r[4]; }
        h.run(); assertImages(expected,h); assertTrue(h.draws.isEmpty());
        for(int whole:new int[]{WHOLE,32,33}) assertEquals(480,h.pool().state(whole).word(RESULTS_TIMER_WORD));
        assertEquals(2,h.slotSearches()); assertEquals(3,h.rows());
    }

    @Test void actualIdOnlySearchMayAllocateLaterPhysicalRowsWithoutDispatchingThem() {
        var h=new Harness(); h.loadRows();
        for(int whole=32;whole<WHOLE;whole++) h.pool().write(h.pool().state(whole).moveByte(ID,1));
        h.run(); assertEquals(0x3A,h.pool().state(41).id()); assertEquals(0x3A,h.pool().state(42).id());
        assertEquals(480,h.pool().state(41).word(RESULTS_TIMER_WORD)); assertEquals(480,h.pool().state(42).word(RESULTS_TIMER_WORD));
    }

    @Test void finiteInitReadsKeepExactlyTheirReachedHeaderAndEarlierWordStores() {
        int[] lengths={0,2,4,7}; var boundaries=new CdResults.UnsupportedBoundary[]{CdResults.UnsupportedBoundary.INIT_Y_READ,
                CdResults.UnsupportedBoundary.INIT_X_READ,CdResults.UnsupportedBoundary.INIT_DEST_X_READ,CdResults.UnsupportedBoundary.INIT_FRAME_READ};
        for(int i=0;i<lengths.length;i++) {
            var h=new Harness(new CdResults.Assets(bytes(java.util.Arrays.copyOf(INIT,lengths[i])),bytes(new byte[0]),MAP)); h.loadRows();
            var expected=images(h); var b=expected.get(WHOLE-32); w(b,0x32,480); b[0x24]=4; w(b,2,0x83C4); l(b,4,MAP);
            if(i>=1) w(b,0x0A,204); if(i>=2) w(b,8,0); if(i>=3) w(b,0x2A,288);
            var e=assertThrows(CdResults.UnsupportedResults.class,h::run); assertEquals(boundaries[i],e.boundary()); assertImages(expected,h); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void missingAllocationStopsBeforeUnownedAddressAndKeepsEveryInitializedPrefix() {
        for(boolean oneFree:new boolean[]{false,true}) {
            var h=new Harness(); h.loadRows(); for(int whole=32;whole<128;whole++) if(whole!=WHOLE) h.pool().write(h.pool().state(whole).moveByte(ID,1));
            if(oneFree) h.pool().write(dirty(h.pool().state(32)).moveByte(ID,0));
            var e=assertThrows(CdResults.UnsupportedResults.class,h::run); assertEquals(CdResults.UnsupportedBoundary.ALLOCATION_ADDRESS,e.boundary());
            assertEquals(4,h.slot().routine()); assertEquals(480,h.slot().word(RESULTS_TIMER_WORD)); assertEquals(MAP,h.slot().longBits(MAPPINGS));
            if(oneFree) { assertEquals(0x3A,h.pool().state(32).id()); assertEquals(1,h.pool().state(32).unsignedByte(MAP_FRAME)); }
            assertTrue(h.draws.isEmpty());
        }
    }

    @Test void moveUsesWordTimerAndUnsigned472DrawAdmissionWithoutAnyExternalReads() {
        int[][] vectors={{0,0,1},{1,0,1},{471,470,1},{472,471,1},{473,472,0},{0x8000,0x7FFF,0},{0xFFFF,0xFFFE,0}};
        for(var v:vectors) {
            var h=new Harness(); h.edit(s -> dirty(s).moveByte(ID,0x3A).moveByte(ROUTINE,4).moveByte(SPRITE_FLAGS,0x80)
                    .moveByte(MAP_FRAME,1).moveWord(X,100).moveWord(RESULTS_DEST_X,116).moveWord(RESULTS_TIMER_WORD,v[0]));
            var expected=images(h); var b=expected.get(WHOLE-32); w(b,0x32,v[1]); w(b,8,108); if(v[2]==1)b[1]&=0x7F;
            h.run(); assertImages(expected,h); assertTrue(h.reads.isEmpty()); assertEquals(v[2],h.draws.size());
        }
    }

    @Test void moveSignedComparisonAndWordOverflowUseLiteralEightPixelVectors() {
        int[][] vectors={{0x7FFC,0x8004,0x7FF4},{0x8004,0x7FFC,0x800C},{0xFFFC,4,4},{4,0xFFFC,0xFFFC}};
        for(var v:vectors) { var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,4).moveByte(MAP_FRAME,1).moveWord(X,v[0])
                .moveWord(RESULTS_DEST_X,v[1]).moveWord(RESULTS_TIMER_WORD,0)); h.run(); assertEquals(v[2],h.slot().centreX()); }
    }

    @Test void onlyFrameZeroAtDestinationAdvancesBeforeIndependentDrawAdmission() {
        for(int frame:new int[]{0,1,255}) { var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,4).moveByte(MAP_FRAME,frame)
                .moveWord(X,288).moveWord(RESULTS_DEST_X,288).moveWord(RESULTS_TIMER_WORD,480));
            h.run(); assertEquals(frame==0?6:4,h.slot().routine()); assertEquals(479,h.slot().word(RESULTS_TIMER_WORD)); assertTrue(h.draws.isEmpty()); }
    }

    @Test void zeroBonusesUseSignedWordExpiryAndNeverRedispatchNextLevelInTheSameCall() {
        int[][] vectors={{1,0,6},{0,0xFFFF,8},{0x8000,0x7FFF,6},{0x8001,0x8000,8},{0xFFFF,0xFFFE,8}};
        for(var v:vectors) { var h=new Harness(); h.bonus(v[0],0,0); h.run(); assertEquals(v[1],h.slot().word(RESULTS_TIMER_WORD));
            assertEquals(v[2],h.slot().routine()); assertTrue(h.programmeStores.isEmpty()); assertTrue(h.points.isEmpty()); assertEquals(1,h.draws.size()); }
    }

    @Test void warpSoundReadsSpecialStageOnlyAtTheActualPostDecrementWordThirty() {
        for(int timer:new int[]{30,31,32}) for(int special:new int[]{0,255}) { var h=new Harness(); h.bonus(timer,0,0);
            h.values.put(CdResults.ReadField.SPECIAL_STAGE_BYTE,special); h.run();
            assertEquals(timer==31?1:0,h.reads.stream().filter(c -> c.field()==CdResults.ReadField.SPECIAL_STAGE_BYTE).count());
            assertEquals(timer==31 && special!=0?List.of(CdResults.Sound.FM_SSWARP):List.of(),h.sounds); }
    }

    @Test void bonusCompletionRequestsDriverKachingThenSets45ThenUsesSharedScoreOnce() {
        var h=new Harness(); h.bonus(2,100,100); h.world=new CdPlayerTouchResponse.WorldGlobals(0xA55A,3,0x5A,4990,5000,0xA5);
        h.beforePoints=c -> { assertEquals(45,c.expectedSource().word(RESULTS_TIMER_WORD)); assertEquals(20,c.points()); };
        h.run(); assertEquals(new CdPlayerTouchResponse.WorldGlobals(0xA55A,4,0x5B,5010,10000,1),h.world);
        assertEquals(List.of("bonus:UPDATE_HUD_BONUS_BYTE:1","read:TIME_BONUS_WORD","bonus:TIME_BONUS_WORD:0",
                "read:RING_BONUS_WORD","bonus:RING_BONUS_WORD:0","read:TIME_BONUS_WORD","read:RING_BONUS_WORD",
                "sound:DRIVER_KACHING","points:20","subcpu:SCMD_YESSFX","draw"),h.order);
        assertEquals(1,h.points.size()); assertEquals(List.of(CdResults.Sound.DRIVER_KACHING),h.sounds);
    }

    @Test void bonusWordSubtractionWrapsWithoutClampAndOnlyNativeNonzeroSidesAddTen() {
        int[][] vectors={{100,0,0,0,10},{0,100,0,0,10},{50,1,0xFFCE,0xFF9D,20}};
        for(var v:vectors) { var h=new Harness(); h.bonus(480,v[0],v[1]); h.run();
            assertEquals(v[2],h.values.get(CdResults.ReadField.TIME_BONUS_WORD).intValue()); assertEquals(v[3],h.values.get(CdResults.ReadField.RING_BONUS_WORD).intValue());
            assertEquals(List.of(v[4]),h.points); assertEquals(v[2]==0 && v[3]==0?480:479,h.slot().word(RESULTS_TIMER_WORD)); }
    }

    @Test void tallyTestsHighTimerByteBitZeroAndNotTheLowWordParity() {
        int[][] vectors={{0x0101,0x0100,0},{2,1,1},{0x0201,0x0200,1},{0,0,1},{0x0200,0x01FF,0}};
        for(var v:vectors) { var h=new Harness(); h.bonus(v[0],1000,0); h.run(); assertEquals(v[1],h.slot().word(RESULTS_TIMER_WORD));
            assertEquals(v[2]==0?List.of():List.of(CdResults.Sound.FM_TALLY),h.sounds); assertEquals(List.of(10),h.points); }
    }

    @Test void driverOrPointsFailuresKeepTheCompletedBonusAndTimerPrefixesAndIdenticalFailure() {
        for(boolean atPoints:new boolean[]{false,true}) { var h=new Harness(); h.bonus(2,100,0); var original=new IllegalArgumentException("port");
            if(atPoints) h.beforePoints=c -> { throw original; }; else h.beforeSound=c -> { throw original; };
            assertSame(original,assertThrows(IllegalArgumentException.class,h::run)); assertEquals(0,h.values.get(CdResults.ReadField.TIME_BONUS_WORD).intValue());
            assertEquals(atPoints?45:2,h.slot().word(RESULTS_TIMER_WORD)); assertTrue(h.draws.isEmpty()); assertEquals(0,original.getSuppressed().length); }
    }

    @Test void absentProgrammeOwnerFencesBeforeTheFirstRestartWordStoreWithoutInventingAVisit() {
        var h=new Harness(assets(INIT),EnumSet.of(Port.PROGRAMME_STORE)); h.edit(s -> s.moveByte(ROUTINE,8)); var before=images(h);
        var e=assertThrows(CdResults.UnsupportedResults.class,h::run); assertEquals(CdResults.UnsupportedBoundary.PROGRAMME_STORE_OWNER,e.boundary());
        assertImages(before,h); assertTrue(h.order.isEmpty());
    }

    @Test void nextLevelOrdersProgrammeStoresExactSavedResetFadeDrawAndFreshTailReads() {
        var h=new Harness(); h.edit(s -> dirty(s).moveByte(ID,0x3A).moveByte(ROUTINE,8).moveByte(SPRITE_FLAGS,0x80));
        h.placement.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.LOADED); var expected=images(h); expected.get(WHOLE-32)[1]=0;
        h.run(); assertImages(expected,h); assertEquals(1,h.placement.status().rightSavedId()); assertEquals(1,h.placement.status().leftSavedId());
        byte[] saved=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]; saved[0]=saved[1]=1; assertArrayEquals(saved,h.placement.capture().savedFlags().copy());
        assertEquals(List.of("programme:LEVEL_RESTART_WORD:2","programme:SPAWN_MODE_BYTE:0","programme:SECTION_ID_WORD:0",
                "programme:FLOWER_COUNT_LONG:0","programme:UNKNOWN_LEVEL_BYTE:0","programme:PROJ_DESTROYED_BYTE:0","programme:CHECKPOINT_BYTE:0",
                "read:TIME_ATTACK_MODE_BYTE","programme:PLC_LOAD_FLAGS_CLEAR_BIT1:1","programme:TIME_ZONE_BYTE:1","read:ZONE_ACT_WORD","programme:ZONE_ACT_WORD:1",
                "action:RESET_SAVED_OBJECT_FLAGS","action:FADE_OUT_MUSIC","draw","read:ACT_BYTE","read:TIME_ATTACK_MODE_BYTE","read:TIME_STONES_BYTE","read:GOOD_FUTURE_BYTE"),h.order);
    }

    @Test void nextLevelUsesNativeByteActWrapWordZoneWrapAndConditionalFuturePeriod() {
        int[][] vectors={{0,1,1},{1,2,2},{2,0x100,1},{0xFF02,0,1},{0x12FF,0x1200,1},{0x1280,0x1281,1}};
        for(var v:vectors) { var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,8)); h.values.put(CdResults.ReadField.ZONE_ACT_WORD,v[0]);
            h.run(); assertEquals(v[1],h.values.get(CdResults.ReadField.ZONE_ACT_WORD).intValue()); assertEquals(v[2],h.programme.get(CdResults.ProgrammeField.TIME_ZONE_BYTE).intValue()); }
    }

    @Test void futureTailPreservesByteBsetAndItsExactKnowledgeDependentBranches() {
        var complete=new Harness(); complete.edit(s -> s.moveByte(ROUTINE,8)); complete.values.put(CdResults.ReadField.ZONE_ACT_WORD,1);
        complete.values.put(CdResults.ReadField.GOOD_FUTURE_BYTE,0x80); complete.values.put(CdResults.ReadField.GOOD_FUTURE_FLAGS_BYTE,1);
        complete.run(); assertEquals(3,complete.values.get(CdResults.ReadField.GOOD_FUTURE_FLAGS_BYTE).intValue()); assertEquals(1,complete.values.get(CdResults.ReadField.GOOD_FUTURE_BYTE).intValue());
        var stones=new Harness(); stones.edit(s -> s.moveByte(ROUTINE,8)); stones.values.put(CdResults.ReadField.TIME_STONES_BYTE,0x7F); stones.run();
        assertEquals(1,stones.values.get(CdResults.ReadField.GOOD_FUTURE_BYTE).intValue()); assertFalse(stones.order.contains("read:GOOD_FUTURE_BYTE"));
        var attack=new Harness(); attack.edit(s -> s.moveByte(ROUTINE,8)); attack.values.put(CdResults.ReadField.TIME_ATTACK_MODE_BYTE,255); attack.run();
        assertTrue(attack.order.contains("programme:PLC_LOAD_FLAGS_CLEAR_BIT0:0")); assertFalse(attack.order.contains("read:TIME_STONES_BYTE"));
        var wrapped=new Harness(); wrapped.edit(s -> s.moveByte(ROUTINE,8)); wrapped.values.put(CdResults.ReadField.ZONE_ACT_WORD,2); wrapped.run();
        assertEquals(0,wrapped.values.get(CdResults.ReadField.GOOD_FUTURE_FLAGS_BYTE).intValue());
        assertTrue(wrapped.order.contains("programme:GOOD_FUTURE_FLAGS_CLEAR_BYTE:0"));
    }

    @Test void fadeFailureRetainsProgrammeStoresAndTheCompletedNativeSavedBankReset() {
        var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,8)); h.placement.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.LOADED);
        var original=new AssertionError("fade"); h.beforeAction=c -> { if(c.action()==CdResults.ProgrammeAction.FADE_OUT_MUSIC) throw original; };
        assertSame(original,assertThrows(AssertionError.class,h::run)); assertEquals(0,h.placement.savedFlag(1,0));
        assertEquals(1,h.placement.status().leftSavedId()); assertEquals(2,h.programme.get(CdResults.ProgrammeField.LEVEL_RESTART_WORD).intValue()); assertTrue(h.draws.isEmpty());
    }

    @Test void resetActionRequiresItsEntireNativeBankAndHeaderPostimageAndPreservesThrownIdentity() {
        var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,8)); h.skipReset=true;
        assertThrows(IllegalStateException.class,h::run); assertTrue(h.draws.isEmpty());
        var failed=new Harness(); failed.edit(s -> s.moveByte(ROUTINE,8)); var original=new IllegalArgumentException("reset");
        failed.afterAction=c -> { if(c.action()==CdResults.ProgrammeAction.RESET_SAVED_OBJECT_FLAGS) throw original; };
        assertSame(original,assertThrows(IllegalArgumentException.class,failed::run)); assertEquals(0,original.getSuppressed().length); assertEquals(1,failed.placement.status().leftSavedId());
    }

    @Test void missingOwnersAndOutOfWidthOperandsStopAtTheirActualReachedOperation() {
        var missing=new Harness(assets(INIT),EnumSet.of(Port.BONUS)); missing.bonus(1,100,0);
        assertEquals(CdResults.UnsupportedBoundary.BONUS_STORE_OWNER,assertThrows(CdResults.UnsupportedResults.class,missing::run).boundary());
        var score=new Harness(assets(INIT),EnumSet.of(Port.POINTS)); score.bonus(1,100,0);
        assertEquals(CdResults.UnsupportedBoundary.POINTS_OWNER,assertThrows(CdResults.UnsupportedResults.class,score::run).boundary()); assertEquals(45,score.slot().word(RESULTS_TIMER_WORD));
        for(int bad:new int[]{-1,65536}) { var h=new Harness(); h.loadRows(); h.values.put(CdResults.ReadField.ZONE_ACT_WORD,bad); var before=images(h);
            assertEquals(CdResults.UnsupportedBoundary.READ_VALUE_WIDTH,assertThrows(CdResults.UnsupportedResults.class,h::run).boundary()); assertImages(before,h); }
    }

    @Test void everyCallbackRejectsPoolCorruptionRetainsOriginalFailureAndDoesNotRepair() {
        for(Port port:Port.values()) for(boolean throwsOriginal:new boolean[]{false,true}) {
            var h=new Harness(); var original=new IllegalArgumentException(port.name());
            Runnable corrupt=() -> { h.pool().write(h.pool().state(127).moveByte(ID,0x55)); if(throwsOriginal) throw original; };
            switch(port) {
                case READ -> h.beforeRead=c -> corrupt.run();
                case BONUS -> { h.bonus(2,100,0); h.beforeBonus=c -> corrupt.run(); }
                case POINTS -> { h.bonus(2,100,0); h.beforePoints=c -> corrupt.run(); }
                case PLC -> { h.edit(s -> s.moveByte(ROUTINE,0).moveWord(RESULTS_TIMER_WORD,0x0100)); h.beforePlc=c -> corrupt.run(); }
                case SOUND -> { h.bonus(2,100,0); h.beforeSound=c -> corrupt.run(); }
                case DRAW -> { h.edit(s -> s.moveByte(ROUTINE,4).moveWord(RESULTS_TIMER_WORD,0)); h.afterDraw=c -> corrupt.run(); }
                case PROGRAMME_STORE -> { h.edit(s -> s.moveByte(ROUTINE,8)); h.beforeProgramme=c -> corrupt.run(); }
                case PROGRAMME_ACTION -> { h.edit(s -> s.moveByte(ROUTINE,8)); h.beforeAction=c -> corrupt.run(); }
            }
            if(throwsOriginal) { assertSame(original,assertThrows(IllegalArgumentException.class,h::run)); assertEquals(1,original.getSuppressed().length); }
            else assertThrows(IllegalStateException.class,h::run);
            assertEquals(0x55,h.pool().state(127).id());
        }
    }

    @Test void drawAllowsOnlyOwnBitSevenClearAndEveryPortRejectsSynchronousReentry() {
        var bad=new Harness(); bad.edit(s -> s.moveByte(ROUTINE,4)); bad.afterDraw=c -> bad.edit(s -> s.moveByte(ROUTINE,0));
        assertThrows(IllegalStateException.class,bad::run); assertEquals(0,bad.slot().routine());
        for(boolean programme:new boolean[]{false,true}) { var h=new Harness(); Runnable enter=() -> assertThrows(IllegalStateException.class,h::run);
            h.beforeRead=c -> enter.run(); h.beforeBonus=c -> enter.run(); h.beforePoints=c -> enter.run(); h.beforeSound=c -> enter.run();
            h.beforePlc=c -> enter.run(); h.beforeProgramme=c -> enter.run(); h.beforeAction=c -> enter.run(); h.afterDraw=c -> enter.run();
            if(programme) h.edit(s -> s.moveByte(ROUTINE,8)); else h.bonus(2,100,0);
            var result=h.run(); assertThrows(UnsupportedOperationException.class,() -> result.effects().clear()); }
        var init=new Harness(); init.edit(s -> s.moveByte(ROUTINE,0).moveWord(RESULTS_TIMER_WORD,0x0100));
        init.beforePlc=c -> assertThrows(IllegalStateException.class,init::run); init.run();
    }

    @Test void failedDrawKeepsItsCompletedBitClearWithoutSuppressingTheOriginalFailure() {
        var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,4)); var original=new AssertionError("draw");
        h.afterDraw=c -> { throw original; };
        assertSame(original,assertThrows(AssertionError.class,h::run)); assertEquals(0,h.slot().spriteFlags());
        assertEquals(0,original.getSuppressed().length);
    }

    @Test void savedBankIsPartOfEveryReadOnlyCallbackCutAndCorruptionIsNeverRepaired() {
        for(boolean throwsOriginal:new boolean[]{false,true}) {
            var h=new Harness(); var original=new AssertionError("saved bank");
            h.beforeRead=c -> { h.placement.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.LOADED); if(throwsOriginal)throw original; };
            if(throwsOriginal) { assertSame(original,assertThrows(AssertionError.class,h::run)); assertEquals(1,original.getSuppressed().length); }
            else assertThrows(IllegalStateException.class,h::run);
            assertNotEquals(0,h.placement.savedFlag(1,0));
        }
    }

    @Test void everyOtherMissingOwnerIsFencedOnlyWhenItsOperationIsReached() {
        var read=new Harness(assets(INIT),EnumSet.of(Port.READ));
        assertEquals(CdResults.UnsupportedBoundary.READ_OWNER,assertThrows(CdResults.UnsupportedResults.class,read::run).boundary());
        var plc=new Harness(assets(INIT),EnumSet.of(Port.PLC)); plc.edit(s -> s.moveByte(ROUTINE,0).moveWord(RESULTS_TIMER_WORD,0x01AB));
        assertEquals(CdResults.UnsupportedBoundary.PLC_OWNER,assertThrows(CdResults.UnsupportedResults.class,plc::run).boundary());
        assertEquals(0x00AB,plc.slot().word(RESULTS_TIMER_WORD)); assertEquals(0,plc.slot().routine());
        var sound=new Harness(assets(INIT),EnumSet.of(Port.SOUND)); sound.bonus(2,100,0);
        assertEquals(CdResults.UnsupportedBoundary.SOUND_OWNER,assertThrows(CdResults.UnsupportedResults.class,sound::run).boundary());
        assertEquals(0,sound.values.get(CdResults.ReadField.TIME_BONUS_WORD).intValue()); assertEquals(2,sound.slot().word(RESULTS_TIMER_WORD));
        var draw=new Harness(assets(INIT),EnumSet.of(Port.DRAW)); draw.edit(s -> s.moveByte(ROUTINE,4));
        assertEquals(CdResults.UnsupportedBoundary.DRAW_OWNER,assertThrows(CdResults.UnsupportedResults.class,draw::run).boundary());
        var action=new Harness(assets(INIT),EnumSet.of(Port.PROGRAMME_ACTION)); action.edit(s -> s.moveByte(ROUTINE,8));
        assertEquals(CdResults.UnsupportedBoundary.PROGRAMME_ACTION_OWNER,assertThrows(CdResults.UnsupportedResults.class,action::run).boundary());
        assertEquals(2,action.programme.get(CdResults.ProgrammeField.LEVEL_RESTART_WORD).intValue()); assertTrue(action.draws.isEmpty());
    }

    private enum Port { READ, BONUS, POINTS, PLC, SOUND, DRAW, PROGRAMME_STORE, PROGRAMME_ACTION }
    private static CdResults.Assets assets(byte[] init) { return new CdResults.Assets(bytes(init),bytes(new byte[0]),MAP); }
    private static CdAssetBytes bytes(byte[] b) { return CdAssetBytes.copyOf(b); }
    private static byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    private static void w(byte[] b,int at,int v) { b[at]=(byte)(v>>>8); b[at+1]=(byte)v; }
    private static void l(byte[] b,int at,int v) { w(b,at,v>>>16); w(b,at+2,v); }
    private static List<byte[]> images(Harness h) { var out=new ArrayList<byte[]>(); for(int whole=32;whole<128;whole++) out.add(h.pool().state(whole).copyBytes()); return out; }
    private static void assertImages(List<byte[]> expected,Harness h) { for(int whole=32;whole<128;whole++) assertArrayEquals(expected.get(whole-32),h.pool().state(whole).copyBytes(),"whole "+whole); }
    private static CdObjectState dirty(CdObjectState s) { for(var f:CdObjectState.ByteField.values())s=s.moveByte(f,0x51+7*f.byteOffset());
        for(var f:CdObjectState.WordField.values())s=s.moveWord(f,0xA157+0x103*f.byteOffset());
        for(var f:CdObjectState.LongField.values())s=s.moveLong(f,0xA1536729+0x01030507*f.byteOffset());return s; }
    private static CdObjectPlacements layout() { var b=ByteBuffer.allocate(34); b.putShort((short)12).putShort((short)28).putShort((short)65535).putInt(0).putShort((short)0);
        b.putShort((short)1000).putShort((short)740).put((byte)0xBA).put((byte)0xC0).put((byte)0x40).put((byte)0);
        b.putShort((short)65535).putInt(0).putShort((short)0);b.putShort((short)65535).putInt(0);return CdObjectPlacements.decodeLayout(b.array()); }
    private static final class Harness {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(layout()); final CdResults body;
        final Map<CdResults.ReadField,Integer> values=new EnumMap<>(CdResults.ReadField.class);
        final Map<CdResults.ProgrammeField,Integer> programme=new EnumMap<>(CdResults.ProgrammeField.class);
        final List<String> order=new ArrayList<>(); final List<CdResults.ReadCall> reads=new ArrayList<>();
        final List<CdResults.ProgrammeStoreCall> programmeStores=new ArrayList<>(); final List<CdResults.Sound> sounds=new ArrayList<>();
        final List<CdResults.DrawCall> draws=new ArrayList<>(); final List<Integer> points=new ArrayList<>();
        CdPlayerTouchResponse.WorldGlobals world=new CdPlayerTouchResponse.WorldGlobals(7,3,0,0,1000000,0);
        CdResults.Result last; boolean skipReset;
        Consumer<CdResults.ReadCall> beforeRead=c -> {}; Consumer<CdResults.BonusStoreCall> beforeBonus=c -> {};
        Consumer<CdResults.PointsCall> beforePoints=c -> {}; Consumer<CdResults.PlcCall> beforePlc=c -> {};
        Consumer<CdResults.SoundCall> beforeSound=c -> {}; Consumer<CdResults.DrawCall> afterDraw=c -> {};
        Consumer<CdResults.ProgrammeStoreCall> beforeProgramme=c -> {}; Consumer<CdResults.ProgrammeActionCall> beforeAction=c -> {},afterAction=c -> {};
        Harness() { this(assets(INIT)); } Harness(CdResults.Assets a) { this(a,EnumSet.noneOf(Port.class)); }
        Harness(CdResults.Assets a,EnumSet<Port> absent) {
            for(var field:CdResults.ReadField.values()) values.put(field,0);
            values.put(CdResults.ReadField.PLC_HEAD_LONG,1); values.put(CdResults.ReadField.CAMERA_X_WORD,1000); values.put(CdResults.ReadField.PLAYER_X_WORD,1337);
            edit(s -> s.moveByte(ID,0x3A).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x80).moveWord(X,100).moveWord(RESULTS_DEST_X,100).moveLong(MAPPINGS,MAP));
            body=new CdResults(placement,a,absent.contains(Port.READ)?null:c -> { cut(c.actualCut(),c.expectedSource()); reads.add(c);order.add("read:"+c.field());beforeRead.accept(c);return values.get(c.field()); },
                    absent.contains(Port.BONUS)?null:c -> { cut(c.actualCut(),c.expectedSource());beforeBonus.accept(c);order.add("bonus:"+c.field()+":"+c.value());
                        switch(c.field()) { case TIME_BONUS_WORD -> values.put(CdResults.ReadField.TIME_BONUS_WORD,c.value()); case RING_BONUS_WORD -> values.put(CdResults.ReadField.RING_BONUS_WORD,c.value()); default -> {} } },
                    absent.contains(Port.POINTS)?null:c -> { cut(c.actualCut(),c.expectedSource());beforePoints.accept(c);points.add(c.points());order.add("points:"+c.points());
                        var result=CdPlayerTouchResponse.addPoints(world,c.points());world=result.world();result.command().ifPresent(command -> order.add("subcpu:"+command)); },
                    absent.contains(Port.PLC)?null:c -> { cut(c.actualCut(),c.expectedSource());beforePlc.accept(c);order.add("plc:"+c.nativeId()); },
                    absent.contains(Port.SOUND)?null:c -> { cut(c.actualCut(),c.expectedSource());beforeSound.accept(c);sounds.add(c.sound());order.add("sound:"+c.sound()); },
                    absent.contains(Port.DRAW)?null:c -> { cut(c.actualCut(),c.expectedSource());draws.add(c);order.add("draw");edit(s -> s.moveByte(SPRITE_FLAGS,s.spriteFlags()&0x7F));afterDraw.accept(c); },
                    absent.contains(Port.PROGRAMME_STORE)?null:c -> { cut(c.actualCut(),c.expectedSource());beforeProgramme.accept(c);programmeStores.add(c);programme.put(c.field(),c.value());order.add("programme:"+c.field()+":"+c.value());
                        switch(c.field()) { case ZONE_ACT_WORD -> { values.put(CdResults.ReadField.ZONE_ACT_WORD,c.value());values.put(CdResults.ReadField.ACT_BYTE,c.value()&255); }
                            case GOOD_FUTURE_BYTE -> values.put(CdResults.ReadField.GOOD_FUTURE_BYTE,c.value());
                            case GOOD_FUTURE_FLAGS_CLEAR_BYTE -> values.put(CdResults.ReadField.GOOD_FUTURE_FLAGS_BYTE,0);
                            case GOOD_FUTURE_FLAGS_SET_BIT -> values.put(CdResults.ReadField.GOOD_FUTURE_FLAGS_BYTE,values.get(CdResults.ReadField.GOOD_FUTURE_FLAGS_BYTE)|(1<<c.value())); default -> {} } },
                    absent.contains(Port.PROGRAMME_ACTION)?null:c -> { cut(c.actualCut(),c.expectedSource());beforeAction.accept(c);order.add("action:"+c.action());
                        if(c.action()==CdResults.ProgrammeAction.RESET_SAVED_OBJECT_FLAGS && !skipReset)placement.resetSavedObjectFlags();afterAction.accept(c); });
        }
        CdObjectPool pool(){return placement.pool();} CdObjectState slot(){return pool().state(WHOLE);} void edit(UnaryOperator<CdObjectState> f){pool().write(f.apply(slot()));}
        void loadRows(){values.put(CdResults.ReadField.PLC_HEAD_LONG,0);} void bonus(int timer,int time,int ring){edit(s -> s.moveByte(ROUTINE,6).moveWord(RESULTS_TIMER_WORD,timer));values.put(CdResults.ReadField.TIME_BONUS_WORD,time);values.put(CdResults.ReadField.RING_BONUS_WORD,ring);}
        CdResults.Result run(){last=body.run(WHOLE);return last;}
        long rows(){return last.effects().stream().filter(CdResults.RowInitialized.class::isInstance).count();}
        long slotSearches(){return last.effects().stream().filter(CdResults.SlotSearchReturned.class::isInstance).count();}
        void cut(CdObjectPlacementRuntime.Snapshot c,CdObjectState s){placement.validateRestore(c);assertEquals(s,slot());assertEquals(placement.status(),c.status());assertArrayEquals(placement.capture().savedFlags().copy(),c.savedFlags().copy());for(var image:c.pool().slots())assertEquals(image,pool().state(image.identity().wholeIndex()));}
    }
}
