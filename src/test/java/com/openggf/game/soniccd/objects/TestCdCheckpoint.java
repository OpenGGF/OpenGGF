package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests-first source candidate, ALL UNRUN, including compilation and RED.
 * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Checkpoint/Main.asm:7-189,
 * Object Spawner.asm:329-340,385-446; Object Animate.asm:14-95;
 * Functions (Misc).asm:69-103; Player Object Collision.asm:7-98,302-395.
 * J ea8dabcf67df7d02f7a2a39496ff7cea93b7e8bd supplies the real component seams.
 * Constructed format fixtures authenticate no media. No Frame/Runtime/World is built.
 * Root owns ordinary-loop/phase/CAS, actual inputs, native sound and route proof.
 * Whole-image comparisons cover all 64 bytes; opaque bytes without a writer remain
 * at their actual fresh-pool values rather than using reflection to forge RAM.
 * OpenAI Codex GPT-6.1 Sol <noreply@openai.com>.
 */
class TestCdCheckpoint {
    static final int WHOLE = 40, MAP = 0x0023F120;
    static final String MAP_HEX = "0008001e002a003604e8030000f8e80308000008010004f8080108040000"
            + "02f8010006f8f80108060000" + "02f8010008f8f80108080000" + "02f801000af8f801080a0000";
    static final String ANI_HEX = "0002030203ff";
    // Source words at angles 0,8,...248; the constructor copies the actual cosine extension.
    static final int[] SINE_8 = {0,49,97,142,181,212,236,251,256,251,236,212,181,142,97,49,
            0,-49,-97,-142,-181,-212,-236,-251,-256,-251,-236,-212,-181,-142,-97,-49};

    @Test void aliasesShareNativeBytesAndDoNotInventObjectStorage() {
        var h = prepared(2);
        assertEquals(64, h.slot().copyBytes().length);
        assertEquals(0x2A, CHECKPOINT_BALL_X.byteOffset());
        assertEquals(0x2C, CHECKPOINT_BALL_Y.byteOffset());
        assertEquals(0x2E, CHECKPOINT_ACTIVE.byteOffset());
        assertEquals(0x30, CHECKPOINT_PARENT.byteOffset());
        assertEquals(0x34, CHECKPOINT_BALL_ANGLE.byteOffset());
        h.edit(s -> s.moveLong(MOSQUI_X_DELTA, 0x12345678).moveLong(CHECKPOINT_PARENT, 0xFFFFDA00)
                .moveByte(CHECKPOINT_BALL_ANGLE, 0xAB));
        assertEquals(0x1234, h.slot().word(CHECKPOINT_BALL_Y));
        assertEquals(0x56, h.slot().unsignedByte(CHECKPOINT_ACTIVE));
        assertEquals(0xFFFFDA00, h.slot().longBits(MOSQUI_ANIMATION_SCRIPT));
        assertEquals(0xAB, h.slot().unsignedByte(RING_MEMBER));
        assertEquals(0xAE, CdCheckpoint.FmSound.FM_CHECKPOINT_AE.nativeId());
    }

    @ParameterizedTest @CsvSource({"0,2,0,227,4", "2,2,1,102,6", "255,255,1,102,6", "0,0,1,102,6"})
    void initReturnsWithoutMainAndPreservesEveryUnwrittenPoleByte(int checkpoint, int subtype,
                                                                 int active, int collision, int childRoutine) {
        var h = prepared(0); h.level = level(checkpoint, 0);
        h.edit(s -> dirty(s).moveByte(ID, 0x13).moveByte(ROUTINE, 0).moveByte(SUBTYPE, subtype)
                .moveByte(CHECKPOINT_ACTIVE, 0).moveByte(SAVED_FLAGS_ID, 0));
        var before = h.slot(); var r = h.run();
        var expected = polePrefix(before).moveByte(COLLISION_TYPE, collision);
        if (active != 0) expected = expected.moveByte(CHECKPOINT_ACTIVE, 1);
        assertArrayEquals(expected.copyBytes(), r.state().copyBytes());
        assertEquals(0x55, r.state().unsignedByte(COLLISION_STATUS));
        assertEquals(7, r.state().unsignedByte(MAP_FRAME));
        assertEquals(List.of("DRAW"), h.order); assertEquals(0, h.saveCalls);
        assertEquals(childRoutine, h.pool().state(32).routine());
        assertEquals(0xFFFFDA00, h.pool().state(32).longBits(CHECKPOINT_PARENT));
        assertEquals(2760, h.pool().state(32).centreX()); assertEquals(712, h.pool().state(32).centreY());
        assertEquals(2760, h.pool().state(32).word(CHECKPOINT_BALL_X));
        assertEquals(720, h.pool().state(32).word(CHECKPOINT_BALL_Y));
        assertEquals(1, r.effects().stream().filter(CdCheckpoint.AllocationAttempt.class::isInstance).count());
        assertEquals(0, count(r, CdCheckpoint.NativeCall.ANIMATE_OBJECT));
        assertEquals(0, h.parentReads.size());
    }

    @ParameterizedTest @CsvSource({"0,250,254", "1,250,0", "0,0,4", "255,253,3"})
    void dirtyFreeChildAddsRoutineAndRetainsAllOtherBytes(int poleActive, int dirtyRoutine, int resultRoutine) {
        var h = prepared(0); h.level = level(0, 0);
        h.edit(s -> s.moveByte(CHECKPOINT_ACTIVE, poleActive).moveByte(SUBTYPE, 2));
        h.pool().write(dirty(h.pool().state(32)).moveByte(ID, 0).moveByte(ROUTINE, dirtyRoutine));
        var before = h.pool().state(32); var r = h.run();
        var expected = before.moveByte(ID, 0x13).moveByte(ROUTINE, resultRoutine)
                .moveLong(MAPPINGS, MAP).moveWord(TILE, 0x6CB).moveByte(SPRITE_FLAGS, 4)
                .moveByte(WIDTH, 8).moveByte(Y_RADIUS, 8).moveByte(PRIORITY, 3).moveByte(MAP_FRAME, 1)
                .moveLong(CHECKPOINT_PARENT, 0xFFFFDA00).moveWord(X, 2760).moveWord(Y, 712)
                .moveWord(CHECKPOINT_BALL_X, 2760).moveWord(CHECKPOINT_BALL_Y, 720);
        assertArrayEquals(expected.copyBytes(), h.pool().state(32).copyBytes());
        assertEquals(7, h.pool().state(32).unsignedByte(CHECKPOINT_ACTIVE));
        assertEquals(1, h.pool().state(32).unsignedByte(CHECKPOINT_BALL_ANGLE));
        assertEquals(0x4321, h.pool().state(32).word(X_FRACTION));
        assertEquals(0x8765, h.pool().state(32).word(Y_FRACTION));
        assertEquals(9, h.pool().state(32).savedFlagsId());
        assertEquals(1, r.effects().stream().filter(CdCheckpoint.ChildCreated.class::isInstance).count());
    }

    @ParameterizedTest @ValueSource(ints = {32,41,127})
    void allocatorUsesFirstIdZeroAndBodyDoesNotRecursivelyVisitItsChild(int free) {
        var h = prepared(0); h.level = level(2, 0);
        for (int i = 32; i < 128; i++) if (i != WHOLE && i != free)
            h.pool().write(h.pool().state(i).moveByte(ID, 0x7F));
        var r = h.run(); assertEquals(6, h.pool().state(free).routine());
        assertEquals(1, h.pool().state(free).unsignedByte(MAP_FRAME));
        assertEquals(1, h.draws.size());
        assertEquals(free, r.effects().stream().filter(CdCheckpoint.AllocationAttempt.class::isInstance)
                .map(CdCheckpoint.AllocationAttempt.class::cast).findFirst().orElseThrow().wholeIndex().orElseThrow());
        // Explicit caller visit, not a recursive body call. Earlier slots would wait for another pass.
        h.body.run(free, h.globals()); assertEquals(2, h.pool().state(free).unsignedByte(MAP_FRAME));
        assertEquals(2, h.draws.size());
    }

    @Test void fullPoolDeletesAll64PoleBytesThenStillCallsActualDrawQueueOnIdZero() {
        var h = prepared(0); h.edit(s -> dirty(s).moveByte(ID, 0x13).moveByte(ROUTINE, 0));
        for (int i = 32; i < 128; i++) if (i != WHOLE)
            h.pool().write(h.pool().state(i).moveByte(ID, 0x7F));
        var bank = h.placement.capture().savedFlags().copy(); var r = h.run();
        assertTrue(r.deleted()); assertArrayEquals(new byte[64], r.state().copyBytes());
        assertEquals(0, h.draws.getFirst().id()); assertEquals(List.of("DRAW"), h.order);
        assertArrayEquals(bank, h.placement.capture().savedFlags().copy());
        assertEquals(0, h.saveCalls); assertEquals(0, h.finishQueue().spriteCount());
    }

    @Test void everyUnownedRawRoutineStopsAtDirectoryReadBeforeEntryOrWrapperWrites() {
        for (int routine = 0; routine < 256; routine++) {
            if (routine == 0 || routine == 2 || routine == 4 || routine == 6) continue;
            var h = prepared(routine); var before = h.slot();
            var e = assertThrows(CdCheckpoint.UnsupportedCheckpoint.class, h::run);
            assertEquals(CdCheckpoint.UnsupportedBoundary.ROUTINE_DISPATCH, e.boundary());
            assertEquals(routine, e.value()); assertEquals(WHOLE, e.wholeIndex());
            assertEquals(before, h.slot()); assertTrue(h.order.isEmpty());
        }
    }

    @ParameterizedTest @CsvSource({"0,0", "1,255", "255,1"})
    void mainInactiveWithoutContactOrAlreadyActiveOnlyRunsWrapper(int active, int status) {
        var h = prepared(2); h.edit(s -> dirty(s).moveByte(ID, 0x13).moveByte(ROUTINE, 2).moveByte(CHECKPOINT_ACTIVE, active)
                .moveByte(COLLISION_STATUS, status).moveByte(SAVED_FLAGS_ID, 0));
        var before = h.slot(); var r = h.run();
        assertEquals(before.moveByte(SPRITE_FLAGS, before.spriteFlags() & 127), r.state());
        assertEquals(List.of("DRAW"), h.order); assertEquals(0, h.saveCalls);
    }

    @Test void contactPublishesTwoLevelStoresThenReal18StoreSaveThenAeThenDrawExactlyOnce() {
        var h = contacted(); h.edit(s -> dirty(s).moveByte(ID, 0x13).moveByte(ROUTINE, 2)
                .moveByte(CHECKPOINT_ACTIVE, 0).moveByte(SUBTYPE, 2).moveByte(SAVED_FLAGS_ID, 0));
        var before = h.slot(); var r = h.run(); var prefix = before.moveByte(COLLISION_TYPE, 0).moveByte(CHECKPOINT_ACTIVE, 1);
        assertArrayEquals(prefix.moveByte(SPRITE_FLAGS, prefix.spriteFlags() & 127).copyBytes(), r.state().copyBytes());
        assertEquals(List.of("LEVEL:CHECKPOINT", "LEVEL:SPAWN_MODE", "SAVE", "FM:174", "DRAW"), h.order);
        assertEquals(prefix, h.globalCalls.getFirst().expectedSourceObject());
        assertEquals(level(2,0), h.globalCalls.getFirst().finalLevel());
        assertEquals(h.globalCalls.getFirst().finalLevel(), h.globalCalls.get(1).expectedLevel());
        assertEquals(level(2,1), h.level); assertEquals(1,h.saveCalls);
        assertEquals(List.of(CdCheckpointSaveData.Store.values()), h.savedStores);
        assertEquals(0x1256, h.saved.read(CdCheckpointSaveData.SavedField.WATER_HEIGHT_WORD));
        assertEquals(0x56, h.saved.read(CdCheckpointSaveData.SavedField.WATER_ROUTINE));
        assertEquals(0x0004FFFE, h.saved.read(CdCheckpointSaveData.SavedField.TIME_LONG));
        assertEquals(0x55, h.slot().unsignedByte(COLLISION_STATUS));
        assertEquals(2, h.slot().routine());
        assertEquals(2,r.effects().stream().filter(CdCheckpoint.GlobalStored.class::isInstance).count());
        assertEquals(1,r.effects().stream().filter(CdCheckpoint.SaveCompleted.class::isInstance).count());
        assertEquals(1,r.effects().stream().filter(CdCheckpoint.FmRequested.class::isInstance).count());
        assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
        h.run(); assertEquals(1,h.saveCalls); assertEquals(1,h.fmCalls);
    }

    @ParameterizedTest @CsvSource({"2,1", "255,0", "0,255"})
    void storesIncludeSameValueWritesAndCanLowerContrivedCheckpoint(int checkpoint, int spawn) {
        var h = contacted(); h.level = level(checkpoint,spawn); h.run();
        assertEquals(2,h.globalCalls.size()); assertEquals(level(2,1),h.level);
        assertEquals(checkpoint,h.globalCalls.getFirst().expectedLevel().checkpoint());
        assertEquals(spawn,h.globalCalls.getFirst().finalLevel().spawnMode());
    }

    @ParameterizedTest @ValueSource(ints = {1,2})
    void thrownGlobalStoreRetainsEarlierPoolAndLevelPrefixWithoutSaveFmOrDraw(int ordinal) {
        var h=contacted(); h.rejectGlobal=ordinal; var e=assertThrows(Fault.class,h::run);
        assertSame(h.fault,e); assertEquals(1,h.slot().unsignedByte(CHECKPOINT_ACTIVE));
        assertEquals(0,h.slot().unsignedByte(COLLISION_TYPE));
        assertEquals(ordinal==1?0:2,h.level.checkpoint()); assertEquals(0,h.level.spawnMode());
        assertEquals(0,h.saveCalls); assertEquals(0,h.fmCalls); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1,2})
    void noOpGlobalStoreCannotClaimChangedActualLevel(int ordinal) {
        var h=contacted(); h.noOpGlobal=ordinal; assertThrows(IllegalStateException.class,h::run);
        assertEquals(ordinal==1?0:2,h.level.checkpoint()); assertEquals(0,h.level.spawnMode());
        assertEquals(0,h.saveCalls); assertTrue(h.draws.isEmpty());
    }

    @Test void globalCallbackChangingSourceCannotContinueThroughSave() {
        var h=contacted(); h.mutateGlobalSource=true; assertThrows(IllegalStateException.class,h::run);
        assertEquals(0xA5,h.slot().flags()); assertEquals(2,h.level.checkpoint());
        assertEquals(0,h.saveCalls); assertTrue(h.draws.isEmpty());
    }

    @Test void foreignPoolImageCannotBeAdoptedAtTheGlobalCallback() {
        var h=contacted(); h.foreignGlobalSource=true; assertThrows(IllegalArgumentException.class,h::run);
        assertEquals(1,h.slot().unsignedByte(CHECKPOINT_ACTIVE)); assertEquals(0,h.saveCalls);
        assertTrue(h.draws.isEmpty());
    }

    @Test void eachReachedSaveReadFailureRetainsActualEarlierSavedPrefixAndNoFmDraw() {
        for(int read=1;read<=18;read++) {
            var h=contacted(); h.rejectSaveRead=read;
            assertSame(h.fault,assertThrows(Fault.class,h::run));
            assertEquals(read-1,h.savedStores.size()); assertEquals(read,h.saveReads.size());
            assertEquals(level(2,1),h.level); assertEquals(1,h.saveCalls);
            assertEquals(List.of(CdCheckpointSaveData.Store.values()).subList(0,read-1),h.savedStores);
            assertEquals(0,h.fmCalls); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void eachSavePublicationFailureRetainsActualEarlierStoresRatherThanDetachedAllOrNothing() {
        for(int store=1;store<=18;store++) {
            var h=contacted(); h.rejectSaveStore=store;
            assertSame(h.fault,assertThrows(Fault.class,h::run));
            assertEquals(store-1,h.savedStores.size()); assertEquals(store,h.saveReads.size());
            assertEquals(level(2,1),h.level); assertEquals(0,h.fmCalls); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void realSaveOwnerRejectsNoOpSavedPublicationBeforeSound() {
        var h=contacted(); h.noOpSaveStore=true; assertThrows(IllegalStateException.class,h::run);
        assertEquals(CdCheckpointSaveData.State.unknown(),h.saved);
        assertEquals(0,h.fmCalls); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1,2,3,4})
    void missingStaleTruncatedOrReorderedReturnedSaveCannotPassCompleted18Check(int variant) {
        var h=contacted(); h.badSaveResult=variant; assertThrows(IllegalStateException.class,h::run);
        assertEquals(0,h.fmCalls); assertTrue(h.draws.isEmpty());
        if(variant!=1) assertEquals(18,h.savedStores.size());
    }

    @ParameterizedTest @ValueSource(ints = {1,2})
    void saveCallbackCannotMutateSourceOrActualLevel(int variant) {
        var h=contacted(); h.mutateSave=variant; assertThrows(IllegalStateException.class,h::run);
        assertEquals(18,h.savedStores.size()); assertEquals(0,h.fmCalls); assertTrue(h.draws.isEmpty());
    }

    @Test void fmThrowPreservesCompletedSaveAndBothGlobalsWithoutWrapperOrRetry() {
        var h=contacted(); h.rejectFm=true; assertSame(h.fault,assertThrows(Fault.class,h::run));
        assertEquals(18,h.savedStores.size()); assertEquals(level(2,1),h.level); assertEquals(1,h.fmCalls);
        assertTrue(h.draws.isEmpty()); assertEquals(1,h.slot().unsignedByte(CHECKPOINT_ACTIVE));
    }

    @ParameterizedTest @ValueSource(ints = {1,2,3})
    void returnedFmCannotRewriteSourceLevelOrSavedState(int variant) {
        var h=contacted(); h.mutateFm=variant; assertThrows(IllegalStateException.class,h::run);
        assertEquals(1,h.fmCalls); assertTrue(h.draws.isEmpty());
    }

    @Test void drawThrowComesAfterSaveAndFmAndBeforeAnyDespawnBankOrDeletion() {
        var h=contacted(); h.rejectDraw=true; h.time=0xFF;
        h.edit(s->s.moveByte(SAVED_FLAGS_ID,255));
        assertSame(h.fault,assertThrows(Fault.class,h::run));
        assertEquals(18,h.savedStores.size()); assertEquals(1,h.fmCalls); assertEquals(1,h.draws.size());
        assertEquals(0x13,h.slot().id());
    }

    @Test void instanceGuardRejectsCallbackReentryWithoutAnotherNativeCall() {
        var h=contacted(); h.reenterGlobal=true; assertThrows(IllegalStateException.class,h::run);
        assertEquals(1,h.globalCalls.size()); assertEquals(0,h.saveCalls); assertTrue(h.draws.isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0xFFFFD880,0x00FFD880,0x12FFD880})
    void inactiveBallReadsOnlyActualParentByteAcross24BitAliases(int pointer) {
        var h=ball(); h.edit(s->s.moveLong(CHECKPOINT_PARENT,pointer));
        h.pool().write(h.pool().state(34).moveByte(ID,0).moveByte(ROUTINE,255).moveByte(CHECKPOINT_ACTIVE,0));
        var before=h.slot(); var r=h.run();
        assertEquals(List.of(pointer+0x2E),h.parentReads);
        assertEquals(before.moveByte(SPRITE_FLAGS,before.spriteFlags()&127),r.state());
        assertEquals(pointer,h.slot().longBits(CHECKPOINT_PARENT));
    }

    @Test void oddByteAddressIsValidAndDoesNotDemandParentBaseAlignmentOrId() {
        var h=ball(); int raw=0xFFFFD880-3;
        h.pool().write(h.pool().state(34).moveByte(ID,0).moveWord(CHECKPOINT_BALL_X,0x007F));
        h.edit(s->s.moveLong(CHECKPOINT_PARENT,raw)); h.run();
        assertEquals(List.of(raw+0x2E),h.parentReads); assertEquals(1,h.slot().unsignedByte(CHECKPOINT_ACTIVE));
        assertEquals(8,h.slot().unsignedByte(CHECKPOINT_BALL_ANGLE));
    }

    @Test void parentAddressAdditionWrapsFull32BitsAndPortOwnsPrimaryOrReservedBytesExplicitly() {
        var h=ball(); h.extraParentBytes.put(1,0x80);
        h.edit(s->s.moveLong(CHECKPOINT_PARENT,0xFFFFFFD3)); h.run();
        assertEquals(List.of(1),h.parentReads); assertEquals(1,h.slot().unsignedByte(CHECKPOINT_ACTIVE));
        var p=ball(); p.extraParentBytes.put(0xFFD02E,1);
        p.edit(s->s.moveLong(CHECKPOINT_PARENT,0xFFFFD000)); p.run();
        assertEquals(List.of(0xFFFFD02E),p.parentReads); assertEquals(1,p.slot().unsignedByte(CHECKPOINT_ACTIVE));
    }

    @Test void unownedParentByteStopsAtReachedReadWithoutFabricatedInactiveValueOrWrapper() {
        var h=ball(); h.edit(s->s.moveLong(CHECKPOINT_PARENT,0x12345678)); var before=h.slot();
        var e=assertThrows(CdCheckpoint.UnownedParentByte.class,h::run);
        assertEquals(0x123456A6,e.rawAddressBits()); assertEquals(before,h.slot());
        assertTrue(h.order.isEmpty()); assertEquals(List.of(0x123456A6),h.parentReads);
    }

    @Test void deletedThenReusedParentActualByteGovernsInactiveBallWithoutLifetimeCheck() {
        var h=ball(); h.edit(s->s.moveLong(CHECKPOINT_PARENT,0xFFFFD880));
        h.pool().deleteObject(34); h.run(); assertEquals(0,h.slot().unsignedByte(CHECKPOINT_ACTIVE));
        h.pool().write(h.pool().state(34).moveByte(ID,0x7F).moveByte(ROUTINE,255).moveByte(CHECKPOINT_ACTIVE,0x80));
        h.run(); assertEquals(1,h.slot().unsignedByte(CHECKPOINT_ACTIVE)); assertEquals(8,h.slot().unsignedByte(CHECKPOINT_BALL_ANGLE));
    }

    @Test void activeBallSkipsUnownedParentAndExecutesExactly32NativeSpinCalls() {
        var h=ball(); h.edit(s->s.moveByte(CHECKPOINT_ACTIVE,1).moveLong(CHECKPOINT_PARENT,0x12345678));
        for(int step=1;step<=32;step++) {
            int angle=(8*step)&255; var r=h.run();
            int sine=SINE_8[angle/8], cosine=SINE_8[((angle+64)&255)/8];
            assertEquals((2760+((sine*8)>>>8))&0xFFFF,r.state().centreX());
            assertEquals((720+((cosine*-8)>>>8))&0xFFFF,r.state().centreY());
            assertEquals(angle,r.state().unsignedByte(CHECKPOINT_BALL_ANGLE));
            assertEquals(step==32?6:4,r.state().routine());
            assertEquals(0x4321,r.state().word(X_FRACTION)); assertEquals(0x8765,r.state().word(Y_FRACTION));
            assertEquals(1,count(r,CdCheckpoint.NativeCall.CALC_SINE));
            assertEquals(0,count(r,CdCheckpoint.NativeCall.ANIMATE_OBJECT));
        }
        assertTrue(h.parentReads.isEmpty()); h.run(); assertEquals(2,h.slot().unsignedByte(MAP_FRAME));
    }

    @Test void malformedNonmultipleOfEightAngleNeverGetsInvented32CallTimeout() {
        var h=ball(); h.edit(s->s.moveByte(CHECKPOINT_ACTIVE,7).moveByte(CHECKPOINT_BALL_ANGLE,1));
        for(int i=0;i<96;i++) h.run();
        assertEquals(4,h.slot().routine()); assertEquals(1,h.slot().unsignedByte(CHECKPOINT_BALL_ANGLE));
        assertTrue(h.parentReads.isEmpty());
    }

    @Test void orbitReadsSuppliedSignedMathWordsAndPreservesFractionsAndAnchors() {
        var h=ball(); h.body=new CdCheckpoint(h.placement,assets(math(8,-117,72,228)),h,h,h::fm,h::draw);
        h.edit(s->s.moveByte(CHECKPOINT_ACTIVE,1).moveWord(CHECKPOINT_BALL_X,0xFFFE)
                .moveWord(CHECKPOINT_BALL_Y,1)); h.camera=0xFF00; h.keepDrawSign=true;
        h.run(); assertEquals((0xFFFE+(((-117*8)>>>8)))&0xFFFF,h.slot().centreX());
        assertEquals((1+(((228*-8)>>>8)))&0xFFFF,h.slot().centreY());
        assertEquals(0xFFFE,h.slot().word(CHECKPOINT_BALL_X)); assertEquals(1,h.slot().word(CHECKPOINT_BALL_Y));
        assertEquals(0x4321,h.slot().word(X_FRACTION)); assertEquals(0x8765,h.slot().word(Y_FRACTION));
    }

    @Test void animateUsesSharedDelayThreeSequenceAndNativeFlags() {
        var h=prepared(6); h.edit(s->s.moveByte(FLAGS,3).moveByte(MAP_FRAME,1));
        int[] frames={2,2,2,2,3,3,3,3,2};
        for(int frame:frames) { var r=h.run(); assertEquals(frame,r.state().unsignedByte(MAP_FRAME));
            assertEquals(7,r.state().spriteFlags()); assertEquals(1,count(r,CdCheckpoint.NativeCall.ANIMATE_OBJECT)); }
        assertEquals(0,h.saveCalls); assertTrue(h.parentReads.isEmpty());
    }

    @Test void animationDirectoryFaultPublishesPreviousResetAndTimerPrefix() {
        var h=prepared(6); h.edit(s->s.moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,3)
                .moveByte(ANIMATION_FRAME,19).moveByte(ANIMATION_TIME,5));
        var e=assertThrows(CdCheckpoint.UnsupportedCheckpoint.class,h::run);
        assertEquals(CdCheckpoint.UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY,e.boundary()); assertEquals(510,e.value());
        assertEquals(255,h.slot().unsignedByte(PREVIOUS_ANIMATION)); assertEquals(0,h.slot().unsignedByte(ANIMATION_FRAME));
        assertEquals(255,h.slot().unsignedByte(ANIMATION_TIME)); assertTrue(h.order.isEmpty());
    }

    @Test void heldTimerDoesNotPrematurelyReadInvalidAnimationDirectory() {
        var h=prepared(6); h.edit(s->s.moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255)
                .moveByte(ANIMATION_FRAME,99).moveByte(ANIMATION_TIME,1));
        h.run(); assertEquals(0,h.slot().unsignedByte(ANIMATION_TIME)); assertEquals(99,h.slot().unsignedByte(ANIMATION_FRAME));
        h.order.clear(); assertThrows(CdCheckpoint.UnsupportedCheckpoint.class,h::run); assertTrue(h.order.isEmpty());
    }

    @Test void outOfExtentFrameKeepsActualSpeedAndIndexAndHasNoFallbackFrame() {
        var h=prepared(6); h.edit(s->s.moveByte(ANIMATION_FRAME,255).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,1));
        var e=assertThrows(CdCheckpoint.UnsupportedCheckpoint.class,h::run);
        assertEquals(CdCheckpoint.UnsupportedBoundary.ANIMATION_FRAME_ADDRESS,e.boundary()); assertEquals(258,e.value());
        assertEquals(3,h.slot().unsignedByte(ANIMATION_TIME)); assertEquals(255,h.slot().unsignedByte(ANIMATION_FRAME));
        assertEquals(1,h.slot().unsignedByte(MAP_FRAME)); assertTrue(h.order.isEmpty());
    }

    @Test void drawReloadsActualCurrentXAndSpriteSignBeforeDespawn() {
        var h=prepared(2); h.drawRewrite=s->s.moveWord(X,0x4000); var r=h.run(); assertTrue(r.deleted());
        assertEquals(0x13,h.draws.getFirst().id()); assertArrayEquals(new byte[64],r.state().copyBytes());
        var kept=prepared(2); kept.drawRewrite=s->s.moveWord(X,0x4000).moveByte(SPRITE_FLAGS,0x84);
        assertFalse(kept.run().deleted()); assertEquals(0x4000,kept.slot().centreX());
    }

    @Test void savedIdZeroSkipsAllRawPeriodAndDirectionBankAddressing() {
        for(int raw=0;raw<256;raw++) {
            var h=prepared(2); h.time=raw; h.direction=0x80; h.edit(s->s.moveWord(X,0x4000).moveByte(SAVED_FLAGS_ID,0));
            var bank=h.placement.capture().savedFlags().copy(); assertTrue(h.run().deleted());
            assertArrayEquals(bank,h.placement.capture().savedFlags().copy());
        }
    }

    @Test void everyNonwarpPeriodUsesActualLinearSavedBankAliasOrReachedBoundary() {
        int[] ids={1,7,212,213,254,255};
        for(int id:ids) for(int raw=0;raw<128;raw++) {
            var h=prepared(2); h.time=raw; h.edit(s->s.moveWord(X,0x4000).moveByte(SAVED_FLAGS_ID,id));
            int index=2+3*id+raw;
            if(index<CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
                int canonical=(index-2)/3, period=(index-2)%3;
                h.placement.bsetSavedFlag(canonical,period,CdObjectPlacementRuntime.SavedBit.LOADED);
                h.placement.bsetSavedFlag(canonical,period,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
                var r=h.run(); assertTrue(r.deleted()); assertEquals(1,h.placement.savedFlag(canonical,period));
                assertTrue(r.effects().stream().anyMatch(e->e instanceof CdCheckpoint.SavedFlagChanged f
                        && f.byteIndex()==index && f.previousValue()==129 && f.value()==1));
            } else {
                var e=assertThrows(CdCheckpoint.UnsupportedCheckpoint.class,h::run);
                assertEquals(CdCheckpoint.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,e.boundary()); assertEquals(index,e.value());
                assertEquals(0x13,h.slot().id()); assertEquals(1,h.draws.size());
            }
        }
    }

    @Test void warpSubtractsSignedDirectionAndClampsOnlyAtNativeReachedPath() {
        int[] directions={0,1,2,127,128,129,254,255};
        for(int period=0;period<128;period++) for(int direction:directions) {
            var h=prepared(2); h.time=period|128; h.direction=direction;
            h.edit(s->s.moveByte(SAVED_FLAGS_ID,7));
            for(int p=0;p<3;p++) h.placement.bsetSavedFlag(7,p,CdObjectPlacementRuntime.SavedBit.LOADED);
            int selected=period-(byte)direction; selected=selected<0?0:selected>=3?2:selected;
            assertTrue(h.run().deleted());
            for(int p=0;p<3;p++) assertEquals(p==selected?0:128,h.placement.savedFlag(7,p));
        }
    }

    @ParameterizedTest @CsvSource({"2432,false", "3072,false", "3200,true", "2304,true"})
    void despawnUsesUnsignedWordChunkDistanceAndNativeInclusiveBoundary(int x,boolean deleted) {
        var h=prepared(2); h.camera=2560; h.edit(s->s.moveWord(X,x)); assertEquals(deleted,h.run().deleted());
    }

    @Test void actualSharedSpecialE3ScanAndResponseIncrementContactWithoutHurtOrRollingFacade() {
        for(int status:new int[]{0,254,255}) {
            var h=prepared(2); h.edit(s->s.moveByte(COLLISION_TYPE,0xE3).moveByte(COLLISION_STATUS,status)
                    .moveByte(SPRITE_FLAGS,0x84));
            var sizes=new ArrayList<CdR11aObjectAssets.TouchSize>();
            for(int i=0;i<63;i++) sizes.add(new CdR11aObjectAssets.TouchSize(1,1));
            sizes.set(0x23-1,new CdR11aObjectAssets.TouchSize(12,24));
            var cut=h.pool().capture(); var selected=new CdPlayerObjectTouch(sizes)
                    .scan(new CdPlayerObjectTouch.PlayerCut(2760,744,19,0,0xFFFF),cut);
            assertInstanceOf(CdPlayerObjectTouch.HandlerBoundary.class,selected);
            assertEquals(CdPlayerObjectTouch.Kind.SPECIAL,selected.kind());
            var motion=new CdPlayerMotionState(2760<<16,744<<16,0,0,0,2,0,0,4,9,19,0,0,0,0,0,0,0,0,0);
            var actor=new CdPlayerTouchResponse.Actor(motion,0,2,0xFFFF,0);
            var pg=new CdPlayerTouchResponse.PlayerGlobals(0,1,1,0,0,0,0,0);
            var wg=new CdPlayerTouchResponse.WorldGlobals(99,3,0,0,5000,0);
            var response=new CdPlayerTouchResponse(CdAssetBytes.copyOf(new byte[8]))
                    .respond((CdPlayerObjectTouch.HandlerBoundary)selected,actor,pg,wg,cut);
            assertEquals(CdPlayerTouchResponse.Exit.SPECIAL_FLAG_INCREMENT,response.exit());
            assertEquals(actor,response.actor()); assertEquals(pg,response.player()); assertEquals(wg,response.world());
            assertTrue(response.calls().isEmpty()); assertEquals(1,response.changedSlots().size());
            h.pool().write(response.changedSlots().getFirst()); h.run();
            assertEquals((status+1)&255,h.slot().unsignedByte(COLLISION_STATUS));
            assertEquals(status==255?0:1,h.saveCalls);
        }
    }

    @Test void sameOwnerSnapshotRestoreRetainsOldResultAndForwardReplayWithoutEffectReplay() {
        var h=contacted(); var before=h.placement.capture(); var levelBefore=h.level; var savedBefore=h.saved;
        var first=h.run(); var retained=first.state().copyBytes(); var after=h.placement.capture();
        var resultSaved=h.saved; int calls=h.saveCalls;
        h.placement.restore(before); h.level=levelBefore; h.saved=savedBefore;
        assertEquals(calls,h.saveCalls); assertArrayEquals(retained,first.state().copyBytes());
        h.savedStores.clear(); h.saveReads.clear(); h.globalCalls.clear(); h.order.clear();
        var replay=h.run(); assertEquals(first,replay); assertEquals(resultSaved,h.saved);
        assertEquals(after.pool().slots(),h.placement.capture().pool().slots());
        assertEquals(calls+1,h.saveCalls); assertArrayEquals(retained,first.state().copyBytes());
        assertThrows(IllegalArgumentException.class,()->new CdObjectPlacementRuntime(layout()).restore(before));
    }

    static CdObjectState polePrefix(CdObjectState s) {
        return s.addByte(ROUTINE,2).moveLong(MAPPINGS,MAP).moveWord(TILE,0x6CB).moveByte(SPRITE_FLAGS,4)
                .moveByte(WIDTH,8).moveByte(Y_RADIUS,0x18).moveByte(PRIORITY,4);
    }
    static long count(CdCheckpoint.Result r,CdCheckpoint.NativeCall call) {
        return r.effects().stream().filter(e->e instanceof CdCheckpoint.CallReturned c && c.call()==call).count();
    }
    static CdLevelGlobals level(int checkpoint,int spawn) {
        return new CdLevelGlobals(0x1234,0x56,checkpoint,spawn,0x78,0x123456,0x9A,0xBC,0xDE);
    }
    static Harness prepared(int routine) {
        var h=new Harness(); h.edit(s->s.moveByte(ID,0x13).moveByte(ROUTINE,routine).moveWord(X,2760).moveWord(Y,744)
                .moveByte(SUBTYPE,2).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,8).moveByte(Y_RADIUS,24)
                .moveByte(PRIORITY,4).moveLong(MAPPINGS,MAP)); return h;
    }
    static Harness contacted() { var h=prepared(2); h.edit(s->s.moveByte(COLLISION_TYPE,0xE3).moveByte(COLLISION_STATUS,1)); return h; }
    static Harness ball() {
        var h=prepared(4); h.edit(s->s.moveByte(MAP_FRAME,1).moveByte(PRIORITY,3).moveByte(Y_RADIUS,8)
                .moveWord(CHECKPOINT_BALL_X,2760).moveWord(CHECKPOINT_BALL_Y,720)
                .moveWord(X_FRACTION,0x4321).moveWord(Y_FRACTION,0x8765)); return h;
    }
    static CdCheckpoint.Assets assets(CdPlayerMath math) {
        return new CdCheckpoint.Assets(MAP,CdObjectVisualAssets.decode(HexFormat.of().parseHex(MAP_HEX),4,
                HexFormat.of().parseHex(ANI_HEX),1,12),math);
    }
    static CdObjectPlacements layout() {
        var b=ByteBuffer.allocate(26);
        b.putShort((short)12).putShort((short)20).putShort((short)0xFFFF).putInt(0).putShort((short)0);
        b.putShort((short)0xFFFF).putInt(0).putShort((short)0); b.putShort((short)0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(b.array());
    }
    static CdObjectState dirty(CdObjectState s) {
        for(var f:CdObjectState.ByteField.values()) s=s.moveByte(f,0xA5);
        for(var f:CdObjectState.WordField.values()) s=s.moveWord(f,0xB6C7);
        for(var f:CdObjectState.LongField.values()) s=s.moveLong(f,0xD8E9FA0B);
        return s.moveWord(X,2760).moveWord(Y,744).moveWord(X_FRACTION,0x4321).moveWord(Y_FRACTION,0x8765)
                .moveByte(COLLISION_TYPE,0x66).moveByte(COLLISION_STATUS,0x55).moveByte(MAP_FRAME,7)
                .moveByte(ANIMATION,3).moveByte(PREVIOUS_ANIMATION,9).moveByte(ANIMATION_FRAME,5)
                .moveByte(ANIMATION_TIME,0x12).moveByte(CHECKPOINT_ACTIVE,7).moveByte(CHECKPOINT_BALL_ANGLE,1)
                .moveByte(SAVED_FLAGS_ID,9).moveByte(FLAGS,0x35);
    }
    static CdPlayerMath math(int... overrides) {
        byte[] sine=new byte[640];
        for(int i=0;i<32;i++) word(sine,16*i,SINE_8[i]);
        for(int i=0;i<64;i++) word(sine,2*(256+i),((sine[2*i]&255)<<8)|(sine[2*i+1]&255));
        for(int i=0;i<overrides.length;i+=2) {
            word(sine,2*overrides[i],overrides[i+1]);
            if(overrides[i]<64) word(sine,2*(overrides[i]+256),overrides[i+1]);
        }
        byte[] animations=new byte[578], mappings=new byte[398],dplcs=new byte[398];
        for(int i=0;i<60;i++) word(animations,2*i,120); animations[122]=(byte)0xFF;
        for(int i=0;i<198;i++) { word(mappings,2*i,396); word(dplcs,2*i,396); }
        var visuals=CdSonicVisualAssets.decode(mappings,dplcs,new byte[69472]);
        byte[] atan=new byte[257]; for(int i=0;i<=256;i++) atan[i]=(byte)(i/8);
        return new CdPlayerMath(CdR11aPlayerAssets.decode(animations,new byte[60],
                new byte[]{1,2,3,4,5,(byte)0xFF},new byte[4],sine,atan,visuals));
    }
    static void word(byte[] b,int at,int value) { b[at]=(byte)(value>>>8); b[at+1]=(byte)value; }
    static final class Fault extends RuntimeException { }

    static final class Harness implements CdCheckpoint.GlobalPort,CdCheckpoint.SaveDataPort {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(layout());
        CdLevelGlobals level=level(0,0);
        CdCheckpointSaveData.State saved=CdCheckpointSaveData.State.unknown();
        final List<String> order=new ArrayList<>();
        final List<CdCheckpoint.GlobalCall> globalCalls=new ArrayList<>();
        final List<CdCheckpointSaveData.Store> savedStores=new ArrayList<>();
        final List<CdCheckpointSaveData.Source> saveReads=new ArrayList<>();
        final List<Integer> parentReads=new ArrayList<>();
        final List<CdObjectState> draws=new ArrayList<>();
        final Map<Integer,Integer> extraParentBytes=new HashMap<>();
        final Fault fault=new Fault();
        final CdObjectDrawQueue queue;
        CdCheckpoint body;
        int time=1,direction=0,camera=2560,saveCalls,fmCalls,rejectGlobal,noOpGlobal,rejectSaveRead,rejectSaveStore;
        int badSaveResult,mutateSave,mutateFm;
        boolean mutateGlobalSource,foreignGlobalSource,noOpSaveStore,rejectFm,rejectDraw,reenterGlobal,keepDrawSign;
        UnaryOperator<CdObjectState> drawRewrite=UnaryOperator.identity();
        Harness() {
            placement.resetSavedObjectFlags();
            queue=new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots() {
                @Override public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref) {
                    var s=pool().state(ref.wholeIndex());
                    return new CdObjectDrawQueue.LiveDrawSlot(s.id(),s.spriteFlags(),s.centreX(),s.centreY(),
                            s.word(X_FRACTION),s.longBits(MAPPINGS),s.unsignedByte(MAP_FRAME),s.word(TILE));
                }
                @Override public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value) {
                    var s=pool().state(ref.wholeIndex()); pool().write(s.moveByte(SPRITE_FLAGS,
                            value?s.spriteFlags()|128:s.spriteFlags()&127));
                }
            },(address,staticPiece,frame)-> {
                assertEquals(MAP,address); assertFalse(staticPiece);
                var f=assets(math()).visual().mappingFrames().get(frame);
                var pieces=f.pieces().stream().map(p->new CdObjectDrawQueue.RawPiece(p.y(),p.sizeByte(),p.rawTileWord(),p.x())).toList();
                return new CdObjectDrawQueue.FrameMapping(pieces.size(),pieces);
            },new byte[640]);
            queue.beginPass(); body=new CdCheckpoint(placement,assets(math()),this,this,this::fm,this::draw);
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> edit) { pool().write(edit.apply(slot())); }
        CdCheckpoint.DespawnGlobals globals() { return new CdCheckpoint.DespawnGlobals(time,direction,camera); }
        CdCheckpoint.Result run() { return body.run(WHOLE,globals()); }
        @Override public CdLevelGlobals current() { return level; }
        @Override public void commit(CdCheckpoint.GlobalCall call) {
            assertEquals(slot(),call.expectedSourceObject()); assertEquals(level,call.expectedLevel());
            globalCalls.add(call); order.add("LEVEL:"+call.field());
            if(reenterGlobal) run();
            if(globalCalls.size()==rejectGlobal) throw fault;
            if(globalCalls.size()!=noOpGlobal) level=call.finalLevel();
            if(mutateGlobalSource) edit(s->s.moveByte(FLAGS,0xA5));
            if(foreignGlobalSource) pool().write(new CdObjectPool().state(WHOLE));
        }
        @Override public int readParentByte(int rawAddressBits) {
            parentReads.add(rawAddressBits); int address=rawAddressBits&0xFFFFFF;
            if(address>=0xFFD800 && address<0xFFF000) {
                int offset=address-0xFFD000; return pool().state(offset/64).copyBytes()[offset%64]&255;
            }
            Integer value=extraParentBytes.get(address);
            if(value==null) throw new CdCheckpoint.UnownedParentByte(rawAddressBits);
            return value;
        }
        @Override public CdCheckpointSaveData.State currentState() { return saved; }
        @Override public CdCheckpointSaveData.Result save(CdCheckpoint.SaveCall call) {
            assertEquals(slot(),call.expectedSourceObject()); assertEquals(level,call.expectedLevel());
            assertEquals(saved,call.expectedSaved()); order.add("SAVE"); saveCalls++;
            if(badSaveResult==1) return new CdCheckpointSaveData.Result(saved,List.of());
            var before=saved;
            var result=CdCheckpointSaveData.save(saved,new CdCheckpointSaveData.Port() {
                @Override public CdCheckpointSaveData.State currentState() { return saved; }
                @Override public int read(CdCheckpointSaveData.Source source) {
                    saveReads.add(source); if(saveReads.size()==rejectSaveRead) throw fault;
                    return switch(source) {
                        case SPAWN_MODE -> level.spawnMode(); case PRIMARY_X -> 2760; case PRIMARY_Y -> 744;
                        case WATER_ROUTINE -> 0x56; case WATER_HEIGHT2 -> 0x1234; case WATER_FULLSCREEN -> 0x9A;
                        case TIME_LONG -> 0x0004FFFE; case MINI_SONIC -> 0xBC;
                        default -> 0x4567;
                    };
                }
                @Override public void commit(CdCheckpointSaveData.StoreCall store) {
                    assertEquals(saved,store.expectedState());
                    if(savedStores.size()+1==rejectSaveStore) throw fault;
                    if(!noOpSaveStore) saved=store.finalState(); savedStores.add(store.store());
                }
            });
            if(mutateSave==1) edit(s->s.moveByte(FLAGS,0xA5));
            if(mutateSave==2) level=level(0xFE,0xFD);
            if(badSaveResult==2) return new CdCheckpointSaveData.Result(before,result.completedStores());
            if(badSaveResult==3) return new CdCheckpointSaveData.Result(result.state(),result.completedStores().subList(0,17));
            if(badSaveResult==4) { var reversed=new ArrayList<>(result.completedStores()); java.util.Collections.reverse(reversed);
                return new CdCheckpointSaveData.Result(result.state(),reversed); }
            return result;
        }
        void fm(CdCheckpoint.FmSound sound) {
            assertEquals(CdCheckpoint.FmSound.FM_CHECKPOINT_AE,sound); order.add("FM:"+sound.nativeId()); fmCalls++;
            assertEquals(18,savedStores.size()); assertEquals(level(2,1),level);
            if(rejectFm) throw fault;
            if(mutateFm==1) edit(s->s.moveByte(FLAGS,0xA5));
            if(mutateFm==2) level=level(0xFE,0xFD);
            if(mutateFm==3) saved=CdCheckpointSaveData.State.unknown();
        }
        void draw(CdObjectPool pool,CdObjectState.Identity identity) {
            assertSame(pool(),pool); var s=pool.state(identity.wholeIndex()); order.add("DRAW"); draws.add(s);
            if(rejectDraw) throw fault;
            queue.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(identity.wholeIndex()),
                    s.spriteFlags(),s.centreX(),s.centreY(),s.unsignedByte(Y_RADIUS),s.unsignedByte(WIDTH),
                    s.unsignedByte(PRIORITY)),new CdObjectDrawQueue.CameraWords(camera,640));
            s=pool.state(identity.wholeIndex()); if(keepDrawSign) s=s.moveByte(SPRITE_FLAGS,s.spriteFlags()|128);
            pool.write(drawRewrite.apply(s));
        }
        CdObjectDrawQueue.CpuSat finishQueue() {
            var c=new CdObjectDrawQueue.CameraWords(camera,640); return queue.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(c,c,c));
        }
    }
}
