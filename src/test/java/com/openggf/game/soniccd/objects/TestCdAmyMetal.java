package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.openggf.game.soniccd.objects.TestCdIntroFamily.*;
import static com.openggf.game.soniccd.objects.CdIntroSpikes.Read.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCdAmyMetal {
    @Test void amyInitPlcFaultPrecedesFlagGraphicsAndPaletteStores() throws Exception {
        var p=pool(32,0x32,0);b(p,32,1,0x80);var before=p.capture().slots();var h=new Host();
        h.faultAt="plc:11";h.fault=new IllegalStateException("plc");
        assertSame(h.fault,assertThrows(IllegalStateException.class,()->new CdAmyRose(assets()).run(p,32,h)));
        assertEquals(before,p.capture().slots());assertEquals(List.of("read:TIME_ATTACK","read:AMY_CAPTURED","plc:11"),h.events);
    }
    @Test void amyInitPaletteFailureKeepsPlcFlagAndGraphicPrefix() throws Exception {
        var p=pool(32,0x32,0);var h=new Host();h.faultAt="palette";h.fault=new IllegalStateException("palette");
        assertSame(h.fault,assertThrows(IllegalStateException.class,()->new CdAmyRose(assets()).run(p,32,h)));
        assertEquals(255,p.state(32).unsignedByte(P5Call.bf(0x3E)));assertEquals(0x235E,p.state(32).word(CdObjectState.WordField.TILE));
        assertEquals(0x23BDB2,p.state(32).longBits(CdObjectState.LongField.MAPPINGS));assertEquals(0,p.state(32).routine());
        assertFalse(h.events.contains("read:PLAYER_X"));
    }
    @Test void amyInitRetainsRoutineUntilPlayerReachesXAndDoesNotMakeHeart() throws Exception {
        var p=pool(32,0x32,0);w(p,32,8,0x100);b(p,32,0x3B,255);var h=new Host();h.values.put(PLAYER_X,0xFF);
        new CdAmyRose(assets()).run(p,32,h);assertEquals(0,p.state(32).routine());assertEquals(255,p.state(32).unsignedByte(P5Call.bf(0x3B)));
        assertEquals(0x32,p.state(32).id());assertEquals(1,p.state(32).flags()&1);
        h.values.put(PLAYER_X,0x100);h.events.clear();new CdAmyRose(assets()).run(p,32,h);
        assertEquals(2,p.state(32).routine());assertFalse(h.events.contains("plc:11"));assertTrue(h.events.contains("palette"));
    }
    @Test void amySpawnsMetalAndHeartUsingActualFirstFreeOrderAndOnlyDeclaredDirtyStores() throws Exception {
        var p=pool(40,0x32,2);w(p,40,8,0x3C0);w(p,40,0xC,0x3E0);b(p,40,0x3B,0xF8);
        l(p,32,8,0xAAAA1234);l(p,32,0xC,0xBBBB5678);b(p,32,0x28,0xFF);
        l(p,33,8,0xCCCC9ABC);l(p,33,0xC,0xDDDDEF01);b(p,33,0x3C,0x88);
        var before=p.capture().slots();var h=new Host();h.values.put(PLAYER_X,0x400);
        new CdAmyRose(assets()).run(p,40,h);
        var metal=before.getFirst().copyBytes();bytes(metal,0,0x31);bytes(metal,8,5,0);bytes(metal,0xC,3,0xE8);bytes(metal,0x34,0xDA,0);
        assertArrayEquals(metal,p.state(32).copyBytes());assertEquals(0xD800,p.state(40).word(P5Call.wf(0x34)));
        var heart=before.get(1).copyBytes();bytes(heart,0,0x33);bytes(heart,8,3,0xCC);bytes(heart,0xC,3,0xD4);
        assertArrayEquals(heart,p.state(33).copyBytes());assertEquals(0,p.state(40).unsignedByte(P5Call.bf(0x3B)));
    }
    @Test void amyRawRoutine2LinkReadsByte3dWithoutIdGuardAndSubCpuFaultRetainsRoutine4() throws Exception {
        var p=pool(40,0x32,2);w(p,40,0x34,0xD800);b(p,32,0,0x29);b(p,32,0x3D,0x80);
        var h=new Host();h.faultAt="sub:125";h.fault=new IllegalStateException("subcpu");
        assertSame(h.fault,assertThrows(IllegalStateException.class,()->new CdAmyRose(assets()).run(p,40,h)));
        assertEquals(4,p.state(40).routine());assertFalse(h.events.contains("read:PLAYER_X"));
    }
    @Test void amyInvalidRawLinkStopsAtReachedUseWithoutInventingParent() throws Exception {
        var p=pool(40,0x32,2);w(p,40,0x34,0x1234);var before=p.capture().slots();var h=new Host();
        assertThrows(UnsupportedOperationException.class,()->new CdAmyRose(assets()).run(p,40,h));assertEquals(before,p.capture().slots());
        assertEquals(List.of("read:TIME_ATTACK","read:AMY_CAPTURED"),h.events);
    }
    @ParameterizedTest @ValueSource(ints={0,1})
    void amyHeldMetalCopiesIntegerPositionsAndPreservesFractionsAndFacing(int flipped) throws Exception {
        var p=pool(40,0x32,4);w(p,40,0x34,0xD800);l(p,40,8,0xAAAA1234);l(p,40,0xC,0xBBBB5678);
        b(p,32,0,0x31);b(p,32,0x22,flipped);w(p,32,8,0x400);w(p,32,0xC,0x300);
        new CdAmyRose(assets()).run(p,40,new Host());
        assertEquals(flipped==0?0x4081234:0x3F81234,p.state(40).longBits(CdObjectState.LongField.X_POSITION));
        assertEquals(0x3045678,p.state(40).longBits(CdObjectState.LongField.Y_POSITION));assertEquals(flipped^1,p.state(40).flags()&1);
    }
    @Test void amyMissingParentDeletesThenStillRunsWrapperQueryAndDraw() throws Exception {
        var p=pool(40,0x32,4);w(p,40,0x34,0xD800);var h=new Host();new CdAmyRose(assets()).run(p,40,h);
        assertArrayEquals(new byte[64],p.state(40).copyBytes());assertEquals("draw",h.events.getLast());assertTrue(h.events.contains("read:PLAYER_X"));
    }
    @Test void captureFlagComesBeforePaletteAndDeleteAndPaletteFaultRetainsFlagAndActor() throws Exception {
        var p=pool(40,0x32,255);var h=new Host();h.values.put(TIME_ATTACK,1);h.faultAt="palette";h.fault=new IllegalStateException("palette");
        assertSame(h.fault,assertThrows(IllegalStateException.class,()->new CdAmyRose(assets()).run(p,40,h)));
        assertEquals(255,h.values.get(AMY_CAPTURED));assertEquals(0x32,p.state(40).id());assertEquals(List.of("read:TIME_ATTACK","capture","palette"),h.events);
    }
    @Test void metalInitFallsIntoTimerDecrementWithoutClearingDirtyPlayerTimerOrLink() throws Exception {
        var p=pool(32,0x31,0);b(p,32,0x30,0x87);w(p,32,0x34,0x1234);var h=new Host();new CdMetalSonic(assets()).run(p,32,h);
        assertEquals(0x3B,p.state(32).unsignedByte(P5Call.bf(0x3A)));assertEquals(0x87,Byte.toUnsignedInt(p.state(32).copyBytes()[0x30]));
        assertEquals(0x1234,p.state(32).word(P5Call.wf(0x34)));assertEquals(2,p.state(32).routine());
    }
    @Test void metalTimerZeroStartsAndSoundFaultKeepsFlipsAccelerationRoutine() throws Exception {
        var p=pool(32,0x31,2);b(p,32,0x3A,1);b(p,32,1,0x80);var h=new Host();h.faultAt="fm:ca";h.fault=new IllegalStateException("fm");
        assertSame(h.fault,assertThrows(IllegalStateException.class,()->new CdMetalSonic(assets()).run(p,32,h)));
        assertEquals(4,p.state(32).routine());assertEquals(0xFFF0,p.state(32).word(P5Call.wf(0x3E)));assertEquals(0x81,p.state(32).spriteFlags());
        assertFalse(h.events.contains("read:PLAYER_X"));
    }
    @Test void metalRoutine4SpawnsDirtyExhaustBeforeAnimationAndBaseYOnlyWritesWord() throws Exception {
        var p=pool(40,0x31,4);w(p,40,8,0x3E0);w(p,40,0xC,0x345);w(p,40,0x34,0xD800);b(p,32,0,0x32);w(p,32,8,0x3C0);
        l(p,33,8,0x12345678);b(p,33,0x24,2);var expected=p.state(33).copyBytes();bytes(expected,0,0x34);bytes(expected,0x34,0xDA,0);
        new CdMetalSonic(assets()).run(p,40,new Host());assertArrayEquals(expected,p.state(33).copyBytes());
        assertEquals(6,p.state(40).routine());assertEquals(0x345D800,p.state(40).longBits(P5Call.lf(0x32)));assertEquals(255,p.state(40).unsignedByte(P5Call.bf(0x3D)));
    }
    @Test void metalBobIncludesRawLowLinkWordAndCarryTransitionsToRise() throws Exception {
        var p=pool(40,0x31,6);w(p,40,0x32,0x345);w(p,40,0x34,0xD800);b(p,40,0x3A,0xFC);
        new CdMetalSonic(assets()).run(p,40,new Host());
        assertEquals(0x3457400,p.state(40).longBits(CdObjectState.LongField.Y_POSITION)); // sineFC=-25, *4=-100, <<8
        assertEquals(8,p.state(40).routine());assertEquals(0xFD40,p.state(40).word(CdObjectState.WordField.Y_VELOCITY));
        assertEquals(0x40,p.state(40).unsignedByte(P5Call.bf(0x3B)));assertEquals(0,p.state(40).unsignedByte(P5Call.bf(0x3A)));assertEquals(0x50,Byte.toUnsignedInt(p.state(40).copyBytes()[0x30]));
    }
    @Test void metalRiseLastStepMovesBeforeClearingVelocityAndRecordsFullBaseWord() throws Exception {
        var p=pool(40,0x31,8);l(p,40,8,0x4001234);l(p,40,0xC,0x3005678);w(p,40,0x10,0x100);w(p,40,0x12,-0x200);
        w(p,40,0x3E,11);b(p,40,0x3B,1);b(p,40,0x30,2);var h=new Host();h.values.put(PLAYER_X,0x100);
        new CdMetalSonic(assets()).run(p,40,h);assertEquals(0x4011234,p.state(40).longBits(CdObjectState.LongField.X_POSITION));
        assertEquals(0,p.state(40).word(CdObjectState.WordField.Y_VELOCITY));assertEquals(0x2FE,p.state(40).word(P5Call.wf(0x32)));
    }
    @Test void metalNearPlayerUsesLowDistanceByteNotWordAndDoesNotInitializeDirtyPlayerTimer() throws Exception {
        var p=pool(40,0x31,8);w(p,40,8,0x410);b(p,40,0x30,1);var h=new Host();h.values.put(PLAYER_X,0x100);
        new CdMetalSonic(assets()).run(p,40,h);assertEquals(0xA,p.state(40).routine());assertEquals(0x60,p.state(40).word(P5Call.wf(0x3E)));
        assertEquals(0,Byte.toUnsignedInt(p.state(40).copyBytes()[0x30]));
    }
    @Test void metalHighUnsignedAccelerationIsCappedAndFinalCaptureDeleteStillReturnsToWrapper() throws Exception {
        var p=pool(40,0x31,0xA);w(p,40,8,0x528);w(p,40,0x3E,0xFFFF);var h=new Host();
        new CdMetalSonic(assets()).run(p,40,h);assertArrayEquals(new byte[64],p.state(40).copyBytes());
        assertEquals(List.of("capture","palette","read:PLAYER_X","draw"),h.events);
    }
    @ParameterizedTest @ValueSource(ints={0,1})
    void exhaustChecksCurrentRawParentIdCopiesPositionsAndAnimatesWithoutCopyingParentFlags(int flip) throws Exception {
        var p=pool(40,0x34,0);w(p,40,0x34,0xD800);l(p,40,8,0xAAAA1234);l(p,40,0xC,0xBBBB5678);
        b(p,32,0,0x31);w(p,32,8,0x400);w(p,32,0xC,0x300);b(p,32,0x22,flip);
        new CdMetalSonic(assets()).runExhaust(p,40,new Host());
        assertEquals(flip==0?0x3F01234:0x4101234,p.state(40).longBits(CdObjectState.LongField.X_POSITION));
        assertEquals(0x3005678,p.state(40).longBits(CdObjectState.LongField.Y_POSITION));assertEquals(0,p.state(40).flags());
        b(p,32,0,0x32);new CdMetalSonic(assets()).runExhaust(p,40,new Host());assertArrayEquals(new byte[64],p.state(40).copyBytes());
    }
    @Test void invalidExhaustParentFaultIsAfterInitButBeforeAnyDraw() throws Exception {
        var p=pool(40,0x34,0);w(p,40,0x34,0);var h=new Host();
        assertThrows(UnsupportedOperationException.class,()->new CdMetalSonic(assets()).runExhaust(p,40,h));
        assertEquals(2,p.state(40).routine());assertEquals(0x23BC54,p.state(40).longBits(CdObjectState.LongField.MAPPINGS));assertTrue(h.events.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={0x13,0x6D,0x77,0xFF})
    void heartLiteralTimerEdgesAndDirtyStoppedByte(int initial) throws Exception {
        var p=pool(40,0x33,2);b(p,40,0x3A,initial);b(p,40,0x3C,0xFF);b(p,40,0x1A,10);w(p,40,0x10,0x100);w(p,40,0x12,-0x60);
        l(p,40,8,0x10001234);l(p,40,0xC,0x20005678);var h=new Host();new CdAmyRose(assets()).runHeart(p,40,h);
        if(initial==0x77)assertArrayEquals(new byte[64],p.state(40).copyBytes());
        else {
            assertEquals(0x10011234,p.state(40).longBits(CdObjectState.LongField.X_POSITION));assertEquals(0x1FFFF678,p.state(40).longBits(CdObjectState.LongField.Y_POSITION));
            assertEquals(initial==0x13||initial==0x6D?11:10,p.state(40).unsignedByte(CdObjectState.ByteField.MAP_FRAME));
            if(initial==0x6D){assertEquals(0,p.state(40).word(CdObjectState.WordField.X_VELOCITY));assertEquals(0,p.state(40).word(CdObjectState.WordField.Y_VELOCITY));}
        }
        assertEquals(List.of("draw","despawn"),h.events);
    }
    @Test void higherChildCanRunSamePhysicalPassAndLowerChildWaitsWithRestoreForwardEquality() throws Exception {
        for(int parent:List.of(32,40)) {
            var p=pool(parent,0x32,2);w(p,parent,8,0x3C0);var h=new Host();h.values.put(PLAYER_X,0x400);var amy=new CdAmyRose(assets());var metal=new CdMetalSonic(assets());
            var saved=p.capture();
            for(int repeat=0;repeat<2;repeat++) {
                p.restore(saved);for(int n=32;n<128;n++){if(p.state(n).id()==0x32)amy.run(p,n,h);else if(p.state(n).id()==0x31)metal.run(p,n,h);}
                int child=parent==32?33:32;assertEquals(parent==32?2:0,p.state(child).routine());
                assertEquals(parent==32?0x3B:0,p.state(child).unsignedByte(P5Call.bf(0x3A)));
            }
        }
    }
    @Test void heldAnimatorDoesNotReadMissingDirectoryUntilDueAndFaultKeepsNativeTimerPrefix() throws Exception {
        var p=pool(40,0x31,4);w(p,40,8,0x500);b(p,40,0x1C,0xFF);b(p,40,0x1D,0xFF);b(p,40,0x1E,2);
        var metal=new CdMetalSonic(assets());metal.run(p,40,new Host());assertEquals(1,p.state(40).unsignedByte(CdObjectState.ByteField.ANIMATION_TIME));
        metal.run(p,40,new Host());var h=new Host();assertThrows(UnsupportedOperationException.class,()->metal.run(p,40,h));
        assertEquals(255,p.state(40).unsignedByte(CdObjectState.ByteField.ANIMATION_TIME));assertTrue(h.events.isEmpty());
    }
    @Test void historicalIntroAnimatorConsumesFrameDurationPairsAndAdvancesTwoBytes() throws Exception {
        var p=pool(40,0x31,4);w(p,40,8,0x500);var metal=new CdMetalSonic(assets());
        int[] frames={0,1,0},indices={2,4,2};
        for(int n=0;n<3;n++) {
            metal.run(p,40,new Host());assertEquals(frames[n],p.state(40).unsignedByte(CdObjectState.ByteField.MAP_FRAME));
            assertEquals(indices[n],p.state(40).unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME));
            assertEquals(0,p.state(40).unsignedByte(CdObjectState.ByteField.ANIMATION_TIME));
        }
    }
    @Test void constructedMissingIntroDurationRetainsNativeFrameAndFlipPrefix() throws Exception {
        var p=pool(40,0x31,4);b(p,40,1,0xFF);b(p,40,0x22,1);
        var call=new P5Call(assets(),p,40,new Host(),0x31);
        // Invalid finite input is solely a negative format vector; no media or World authority.
        var shortTable=new CdObjectAnimation.AnimationTable(com.openggf.game.soniccd.level.CdAssetBytes.copyOf(new byte[]{0,2,3}),0,1);
        assertThrows(IndexOutOfBoundsException.class,()->call.animateIntro(shortTable));
        assertEquals(3,p.state(40).unsignedByte(CdObjectState.ByteField.MAP_FRAME));assertEquals(0xFD,p.state(40).spriteFlags());
        assertEquals(255,p.state(40).unsignedByte(CdObjectState.ByteField.ANIMATION_TIME));assertEquals(0,p.state(40).unsignedByte(CdObjectState.ByteField.ANIMATION_FRAME));
    }

    @Test void dirtyAmyLinkReadsInteriorCaptureByteWithoutAlignmentOrIdNormalization() throws Exception {
        var p=pool(40,0x32,2);w(p,40,0x34,0xD801);b(p,32,0,6);b(p,32,0x3E,1);var h=new Host();
        new CdAmyRose(assets()).run(p,40,h);assertEquals(4,p.state(40).routine());assertTrue(h.events.contains("sub:125"));
    }
    @Test void rawCaptureByteMayCrossIntoNextOwnedPhysicalSlot() throws Exception {
        var p=pool(40,0x32,2);w(p,40,0x34,0xD83F);b(p,33,0x3C,1);var h=new Host();
        new CdAmyRose(assets()).run(p,40,h);assertEquals(4,p.state(40).routine());assertTrue(h.events.contains("sub:125"));
    }
    @Test void exhaustReadsActualInteriorIdPositionAndFlagsBytesInOrder() throws Exception {
        var p=pool(40,0x34,2);w(p,40,0x34,0xD801);l(p,40,8,0xAAAA1234);l(p,40,0xC,0xBBBB5678);
        b(p,32,1,0x31);l(p,32,8,0x12034567);l(p,32,0xC,0x120689AB);b(p,32,0x23,1);
        new CdMetalSonic(assets()).runExhaust(p,40,new Host());
        assertEquals(0x03551234,p.state(40).longBits(CdObjectState.LongField.X_POSITION));assertEquals(0x06895678,p.state(40).longBits(CdObjectState.LongField.Y_POSITION));
    }
    @Test void approachReadsInteriorXWithoutParentIdAndUnownedReservedReadKeepsReachedPrefix() throws Exception {
        var p=pool(40,0x31,4);w(p,40,0x34,0xD801);w(p,40,8,0x360);l(p,32,8,0x12034567);
        new CdMetalSonic(assets()).run(p,40,new Host());assertEquals(6,p.state(40).routine());
        var bad=pool(40,0x31,4);w(bad,40,0x34,0xD001);w(bad,40,0x10,-0x100);l(bad,40,8,0x5001234);
        assertThrows(UnsupportedOperationException.class,()->new CdMetalSonic(assets()).run(bad,40,new Host()));
        assertEquals(0x4FF1234,bad.state(40).longBits(CdObjectState.LongField.X_POSITION));assertEquals(0xFF00,bad.state(40).word(CdObjectState.WordField.X_VELOCITY));
    }

}
