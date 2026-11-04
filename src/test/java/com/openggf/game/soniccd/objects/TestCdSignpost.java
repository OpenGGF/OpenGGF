package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
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
 * Source-derived constructed Signpost cases; all Java including compilation/discovery is UNRUN.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Level End/Main.asm:708-907;
 * Object Animate.asm:14-95 and Object Spawner.asm:329-340. Full64/all96 expected images
 * pin native writes/nonwrites. Harness external values are neither World nor media authority.
 * No naturally reached Signpost, Results body, completed act, route, rendering or parity claim.
 */
class TestCdSignpost {
    private static final int WHOLE=40, X0=1000, Y0=740, MAP=0x21D9B4;
    private static final byte[] ANI=hex("000201000102040303010204ff00");
    private static final byte[] BONUSES=hex("c350c350271013880fa00fa00bb80bb807d007d007d007d003e803e803e803e801f401f401f401f40000");

    @Test void rawFiniteViewsDoNotReadUnreachedAnimationOrBonuses() {
        var h=new Harness(assets(new byte[0],new byte[0]),false,false,false);
        h.edit(s -> s.moveByte(ROUTINE,8)); h.run(); assertEquals(List.of("draw"),h.order);
        assertThrows(NullPointerException.class,() -> CdSignpost.Assets.fromPastAssets(null));
    }

    @Test void invalidRoutineIsFencedBeforeAnyReadStoreOrDraw() {
        for(int routine:new int[]{1,3,5,7,9,10,128,254,255}) {
            var h=new Harness(); final int raw=routine; h.edit(s -> s.moveByte(ROUTINE,raw)); var before=images(h);
            var e=assertThrows(CdSignpost.UnsupportedSignpost.class,h::run);
            assertEquals(CdSignpost.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary()); assertEquals(raw,e.value());
            assertImages(before,h); assertTrue(h.order.isEmpty());
        }
    }

    @Test void initPreservesEveryDirtyNonwriteAndUsesHighTileByteExceptQuartz() {
        for(int zone:new int[]{0,3,255}) {
            var h=new Harness(); h.values.put(CdSignpost.ReadField.ZONE_BYTE,zone);
            h.edit(s -> dirty(s).moveByte(ID,0x17).moveByte(ROUTINE,0).moveByte(SPRITE_FLAGS,0xC1).moveWord(X,X0).moveWord(Y,Y0));
            h.pool().write(dirty(h.pool().state(127)).moveByte(ID,0x55)); var expected=images(h); var b=expected.get(WHOLE-32);
            b[0x24]=2; b[1]=0x45; b[0x17]=b[0x19]=24; b[0x16]=32; b[0x18]=4; w(b,2,zone==3?0x043C:0x843C); l(b,4,MAP);
            h.run(); assertImages(expected,h); assertEquals(List.of("read:ZONE_BYTE","read:PLAYER_Y_WORD","draw"),h.order);
        }
    }

    @Test void initZoneReadFailureRetainsOnlyItsAlreadyReachedHeaderPrefix() {
        var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,0).moveLong(MAPPINGS,0x89ABCDEF)); var expected=images(h); var b=expected.get(WHOLE-32);
        b[0x24]=2; b[1]|=4; b[0x17]=b[0x19]=24; b[0x16]=32; b[0x18]=4; w(b,2,0x043C);
        var original=new IllegalArgumentException("zone owner"); h.beforeRead=c -> { throw original; };
        assertSame(original,assertThrows(IllegalArgumentException.class,h::run)); assertImages(expected,h); assertTrue(h.draws.isEmpty());
    }

    @Test void mainYUsesSignedThenUnsignedWordWindowAndReadsXOnlyInsideIt() {
        int[][] vectors={{Y0-129,0},{Y0-128,1},{Y0+127,1},{Y0+128,0},{Y0+0x7F80,0},{Y0+0xFF80,1}};
        for(var v:vectors) {
            var h=new Harness(); h.values.put(CdSignpost.ReadField.PLAYER_Y_WORD,v[0]&65535);
            h.values.put(CdSignpost.ReadField.PLAYER_X_WORD,X0);
            h.run(); assertEquals(v[1]==0?List.of("read:PLAYER_Y_WORD","draw"):List.of("read:PLAYER_Y_WORD","read:PLAYER_X_WORD","draw"),h.order);
            assertEquals(2,h.slot().routine());
        }
    }

    @Test void mainXIsUnsignedAndRequiresStrictlyPastTheSignpost() {
        for(int x:new int[]{0,0x7FFF,0x8000,0x8001,0xFFFF}) {
            var h=new Harness(); h.edit(s -> s.moveWord(X,0x8000)); h.values.put(CdSignpost.ReadField.PLAYER_Y_WORD,Y0);
            h.values.put(CdSignpost.ReadField.PLAYER_X_WORD,x); h.run(); assertEquals(x>0x8000?4:2,h.slot().routine());
            assertEquals(x>0x8000?1:0,h.sounds.size());
        }
    }

    @Test void reachedMainKeepsStoreSoundAndObjectPrefixOrderExactly() {
        var h=new Harness(); h.reachedMain(); h.edit(s -> s.moveByte(LEVEL_END_TIMER,0xA5).moveByte(MAP_FRAME,0xE7));
        h.beforeStore=c -> {
            boolean later=c.field()==CdSignpost.StoreField.SPEED_SHOES_BYTE || c.field()==CdSignpost.StoreField.INVINCIBLE_BYTE;
            assertEquals(later?4:2,c.expectedSource().routine()); assertEquals(later?120:0xA5,c.expectedSource().unsignedByte(LEVEL_END_TIMER));
            assertEquals(later?0:0xE7,c.expectedSource().unsignedByte(MAP_FRAME));
        };
        var expected=images(h); var b=expected.get(WHOLE-32); b[0x24]=4; b[0x2A]=120; b[0x1A]=0; b[1]&=0x7F;
        h.run(); assertImages(expected,h);
        assertEquals(List.of("read:PLAYER_Y_WORD","read:PLAYER_X_WORD","read:CAMERA_X_WORD","store:LEFT_BOUND_WORD:902",
                "read:CAMERA_X_WORD","store:DEST_LEFT_BOUND_WORD:902","store:UPDATE_HUD_TIME_BYTE:0",
                "store:SPEED_SHOES_BYTE:0","store:INVINCIBLE_BYTE:0","sound:FM_SIGNPOST","draw"),h.order);
    }

    @Test void absentExternalOwnersFenceOnlyTheirFirstReachedAction() {
        var read=new Harness(assets(ANI,BONUSES),false,true,true);
        assertEquals(CdSignpost.UnsupportedBoundary.READ_OWNER,assertThrows(CdSignpost.UnsupportedSignpost.class,read::run).boundary());
        var store=new Harness(assets(ANI,BONUSES),true,false,true); store.reachedMain();
        assertEquals(CdSignpost.UnsupportedBoundary.STORE_OWNER,assertThrows(CdSignpost.UnsupportedSignpost.class,store::run).boundary());
        assertEquals(2,store.slot().routine()); assertEquals(0,store.slot().unsignedByte(LEVEL_END_TIMER));
        var sound=new Harness(assets(ANI,BONUSES),true,true,false); sound.reachedMain();
        assertEquals(CdSignpost.UnsupportedBoundary.SOUND_OWNER,assertThrows(CdSignpost.UnsupportedSignpost.class,sound::run).boundary());
        assertEquals(4,sound.slot().routine()); assertEquals(120,sound.slot().unsignedByte(LEVEL_END_TIMER)); assertTrue(sound.draws.isEmpty());
    }

    @Test void soundFailureRetainsCompletedMainStoresAndTheIdenticalException() {
        var h=new Harness(); h.reachedMain(); var original=new AssertionError("FM owner"); h.beforeSound=c -> { throw original; };
        assertSame(original,assertThrows(AssertionError.class,h::run)); assertEquals(0,original.getSuppressed().length);
        assertEquals(4,h.slot().routine()); assertEquals(120,h.slot().unsignedByte(LEVEL_END_TIMER));
        assertEquals(5,h.stores.size()); assertTrue(h.draws.isEmpty());
    }

    @Test void spinTimerIsAByteAndOneToZeroFinishesAfterAnimation() {
        int[][] vectors={{0,255,4,0x55},{1,60,6,3},{2,1,4,0x55},{128,127,4,0x55},{255,254,4,0x55}};
        for(var v:vectors) {
            var h=new Harness(); h.edit(s -> dirty(s).moveByte(ID,0x17).moveByte(ROUTINE,4).moveByte(SPRITE_FLAGS,0x84)
                    .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_TIME,2).moveByte(MAP_FRAME,0x55).moveByte(LEVEL_END_TIMER,v[0]));
            var expected=images(h); var b=expected.get(WHOLE-32); b[0x1E]=1; b[0x2A]=(byte)v[1]; b[0x24]=(byte)v[2]; b[0x1A]=(byte)v[3]; b[1]&=0x7F;
            h.run(); assertImages(expected,h); assertEquals(List.of("draw"),h.order);
        }
    }

    @Test void sharedAnimationSelectsTheActualFiniteScriptAndPreservesOtherBytes() {
        var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,4).moveByte(ANIMATION_TIME,0).moveByte(LEVEL_END_TIMER,2)
                .moveByte(ANIMATION_FRAME,0).moveByte(MAP_FRAME,5));
        var expected=images(h); var b=expected.get(WHOLE-32); b[0x1E]=1; b[0x1B]=1; b[0x1A]=0; b[0x2A]=1; b[1]&=0x7F;
        h.run(); assertImages(expected,h);
    }

    @Test void heldAnimationDoesNotReadADirectoryOrAnyUnreachedBonus() {
        var h=new Harness(assets(new byte[0],new byte[0]));
        h.edit(s -> s.moveByte(ROUTINE,4).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255)
                .moveByte(ANIMATION_TIME,1).moveByte(LEVEL_END_TIMER,2));
        h.run(); assertEquals(0,h.slot().unsignedByte(ANIMATION_TIME)); assertEquals(1,h.slot().unsignedByte(LEVEL_END_TIMER));
    }

    @Test void animatorRoutineAdvanceIsNotRedispatchedAndSpinStillExecutesItsOwnTail() {
        var h=new Harness(assets(hex("000201fc"),BONUSES));
        h.edit(s -> s.moveByte(ROUTINE,4).moveByte(ANIMATION_TIME,0).moveByte(LEVEL_END_TIMER,1));
        h.run(); assertEquals(8,h.slot().routine()); assertEquals(3,h.slot().unsignedByte(MAP_FRAME));
        assertEquals(60,h.slot().unsignedByte(LEVEL_END_TIMER)); assertTrue(h.reads.isEmpty());
    }

    @Test void selectedAnimationFaultsRetainOnlyTheNativeReachedPrefix() {
        byte[][] raw={new byte[0],hex("0002"),hex("000207"),hex("000207fd")};
        var boundaries=new CdSignpost.UnsupportedBoundary[]{CdSignpost.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,
                CdSignpost.UnsupportedBoundary.ANIMATION_SPEED_READ,CdSignpost.UnsupportedBoundary.ANIMATION_FRAME_READ,
                CdSignpost.UnsupportedBoundary.ANIMATION_OPERAND_READ};
        for(int i=0;i<raw.length;i++) {
            var h=new Harness(assets(raw[i],BONUSES)); h.edit(s -> s.moveByte(ROUTINE,4).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,9)
                    .moveByte(ANIMATION_FRAME,7).moveByte(ANIMATION_TIME,5).moveByte(LEVEL_END_TIMER,5));
            var expected=images(h); var b=expected.get(WHOLE-32); b[0x1D]=0; b[0x1B]=0; b[0x1E]=(byte)(i<2?255:7);
            var e=assertThrows(CdSignpost.UnsupportedSignpost.class,h::run); assertEquals(boundaries[i],e.boundary());
            assertImages(expected,h); assertTrue(h.draws.isEmpty());
        }
        var wrongId=new Harness(); wrongId.edit(s -> s.moveByte(ROUTINE,4).moveByte(ANIMATION,1).moveByte(PREVIOUS_ANIMATION,1).moveByte(ANIMATION_TIME,0));
        var e=assertThrows(CdSignpost.UnsupportedSignpost.class,wrongId::run); assertEquals(CdSignpost.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,e.boundary()); assertEquals(2,e.value());
    }

    @Test void failedFeSelectedFrameReadPreservesTheWrappedIndexPrefix() {
        var h=new Harness(assets(hex("000207000000fe05"),BONUSES));
        h.edit(s -> s.moveByte(ROUTINE,4).moveByte(ANIMATION_FRAME,3).moveByte(ANIMATION_TIME,0).moveByte(LEVEL_END_TIMER,1));
        var expected=images(h); var b=expected.get(WHOLE-32); b[0x1E]=7; b[0x1B]=(byte)254;
        var e=assertThrows(CdSignpost.UnsupportedSignpost.class,h::run); assertEquals(CdSignpost.UnsupportedBoundary.ANIMATION_FRAME_READ,e.boundary());
        assertEquals(257,e.value()); assertImages(expected,h);
    }

    @Test void startResultsDelayIsUnsignedByteCountdownWithNoEarlyExternalReads() {
        for(int timer:new int[]{0,2,128,255}) {
            var h=new Harness(assets(new byte[0],new byte[0]),false,false,false); h.edit(s -> s.moveByte(ROUTINE,6).moveByte(LEVEL_END_TIMER,timer));
            h.run(); assertEquals((timer-1)&255,h.slot().unsignedByte(LEVEL_END_TIMER)); assertEquals(6,h.slot().routine()); assertEquals(List.of("draw"),h.order);
        }
    }

    @Test void startResultsOrdersActualCommandsStoresChildAndBonusesWithoutRunningTheChild() {
        var h=new Harness(); h.results(); h.values.put(CdSignpost.ReadField.ZONE_ACT_WORD,0x502);
        h.values.put(CdSignpost.ReadField.TIME_MINUTES_BYTE,1); h.values.put(CdSignpost.ReadField.TIME_SECONDS_BYTE,30); h.values.put(CdSignpost.ReadField.RINGS_WORD,65535);
        h.pool().write(dirty(h.pool().state(32)).moveByte(ID,0)); var expected=images(h); var source=expected.get(WHOLE-32);
        source[0x24]=8; source[0x2A]=(byte)180; source[1]&=0x7F; var child=expected.get(0); child[0]=0x3A; child[0x32]=16;
        h.run(); assertImages(expected,h);
        assertEquals(List.of("read:TIME_ZONE_BYTE","sound:SCMD_FADEPCM","sound:SCMD_RESULTMUS","store:CONTROL_LOCK_BIT0:1",
                "store:PLAYER_CONTROL_HOLD_WORD:2056","read:ZONE_ACT_WORD","store:PLAYER_CONTROL_HOLD_WORD:0",
                "store:UPDATE_HUD_BONUS_BYTE:1","read:TIME_MINUTES_BYTE","read:TIME_SECONDS_BYTE","store:TIME_BONUS_WORD:3000",
                "read:RINGS_WORD","store:RING_BONUS_WORD:65436","draw"),h.order);
        assertEquals(1,h.children()); assertEquals(0x3A,h.pool().state(32).id());
    }

    @Test void onlyExactPastFadesPcmAndOnlyExactSszThreeOverridesTheNativeControlWord() {
        for(int period:new int[]{0,1,2,0x80,255}) {
            var h=new Harness(); h.results(); h.values.put(CdSignpost.ReadField.TIME_ZONE_BYTE,period);
            h.values.put(CdSignpost.ReadField.ZONE_ACT_WORD,0x8502); h.run();
            assertEquals(period==0?List.of(CdSignpost.Sound.SCMD_FADEPCM,CdSignpost.Sound.SCMD_RESULTMUS):List.of(CdSignpost.Sound.SCMD_RESULTMUS),h.sounds);
            assertEquals(2056,h.external.get(CdSignpost.StoreField.PLAYER_CONTROL_HOLD_WORD));
        }
    }

    @Test void timeBonusUsesRawMinuteAndSecondBytesDivuFifteenThenNativeTwentyClamp() {
        int[][] vectors={{0,0,50000},{0,14,50000},{0,15,50000},{0,30,10000},{0,45,5000},{0,255,500},{5,0,0},{255,255,0}};
        for(var v:vectors) {
            var h=new Harness(); h.results(); h.values.put(CdSignpost.ReadField.TIME_MINUTES_BYTE,v[0]); h.values.put(CdSignpost.ReadField.TIME_SECONDS_BYTE,v[1]);
            h.run(); assertEquals(v[2],h.external.get(CdSignpost.StoreField.TIME_BONUS_WORD));
        }
    }

    @Test void malformedTimeBonusStopsAfterRealChildHudFlagAndReachedClockReads() {
        var h=new Harness(assets(ANI,new byte[0])); h.results();
        var e=assertThrows(CdSignpost.UnsupportedSignpost.class,h::run); assertEquals(CdSignpost.UnsupportedBoundary.TIME_BONUS_READ,e.boundary());
        assertEquals(0,e.value()); assertEquals(8,h.slot().routine()); assertEquals(180,h.slot().unsignedByte(LEVEL_END_TIMER));
        assertEquals(0x3A,h.pool().state(32).id()); assertEquals(16,h.pool().state(32).unsignedByte(RESULTS_TIMER));
        assertEquals(1,h.external.get(CdSignpost.StoreField.UPDATE_HUD_BONUS_BYTE)); assertFalse(h.external.containsKey(CdSignpost.StoreField.TIME_BONUS_WORD));
        assertFalse(h.order.contains("read:RINGS_WORD")); assertTrue(h.draws.isEmpty());
    }

    @Test void exhaustedNativeAllocationStopsBeforeTheUnownedSlotWriteAndPreservesItsPrefix() {
        var h=new Harness(); h.results(); for(int whole=32;whole<128;whole++) if(whole!=WHOLE) h.pool().write(h.pool().state(whole).moveByte(ID,1));
        var expected=images(h); expected.get(WHOLE-32)[0x24]=8; expected.get(WHOLE-32)[0x2A]=(byte)180;
        var e=assertThrows(CdSignpost.UnsupportedSignpost.class,h::run); assertEquals(CdSignpost.UnsupportedBoundary.RESULTS_ALLOCATION_ADDRESS,e.boundary());
        assertImages(expected,h); assertFalse(h.external.containsKey(CdSignpost.StoreField.UPDATE_HUD_BONUS_BYTE)); assertTrue(h.draws.isEmpty());
    }

    @Test void resultsActiveOnlyDrawsAndNeverDespawnsOrTouchesSavedFlagsEvenFarOutsideCamera() {
        var h=new Harness(assets(new byte[0],new byte[0]),false,false,false);
        h.edit(s -> s.moveByte(ROUTINE,8).moveWord(X,65535).moveByte(SAVED_FLAGS_ID,255));
        h.placement.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.LOADED); var before=h.placement.capture();
        h.run(); assertEquals(0x17,h.slot().id()); assertEquals(8,h.slot().routine()); assertEquals(65535,h.slot().centreX());
        assertEquals(before.status(),h.placement.status()); assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy()); assertEquals(List.of("draw"),h.order);
    }

    @Test void outOfWidthReadFailsAtThatReadBeforeAnyFollowingNativeWork() {
        for(int raw:new int[]{-1,65536}) {
            var h=new Harness(); h.values.put(CdSignpost.ReadField.PLAYER_Y_WORD,raw); var before=images(h);
            assertEquals(CdSignpost.UnsupportedBoundary.READ_VALUE_WIDTH,assertThrows(CdSignpost.UnsupportedSignpost.class,h::run).boundary());
            assertImages(before,h); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void callbackMutationsAreRejectedAndOriginalFailureIdentitySurvivesVerification() {
        for(String port:List.of("read","store","sound")) for(boolean throwsOriginal:new boolean[]{false,true}) {
            var h=new Harness(); h.reachedMain(); var original=new IllegalArgumentException(port);
            Runnable corrupt=() -> { h.pool().write(h.pool().state(127).moveByte(ID,0x55));
                h.placement.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.LOADED); if(throwsOriginal) throw original; };
            switch(port) { case "read" -> h.beforeRead=c -> corrupt.run(); case "store" -> h.beforeStore=c -> corrupt.run(); case "sound" -> h.beforeSound=c -> corrupt.run(); }
            if(throwsOriginal) { assertSame(original,assertThrows(IllegalArgumentException.class,h::run)); assertEquals(1,original.getSuppressed().length); }
            else assertThrows(IllegalStateException.class,h::run);
            assertEquals(0x55,h.pool().state(127).id()); assertEquals(0x80,h.placement.savedFlag(1,0)); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void drawAllowsOnlyItsOwnBitSevenClearReloadsItAndPreservesFailureIdentity() {
        var h=new Harness(); h.edit(s -> s.moveByte(ROUTINE,8)); h.run(); assertEquals(4,h.slot().spriteFlags());
        var bad=new Harness(); bad.edit(s -> s.moveByte(ROUTINE,8)); bad.afterDraw=c -> bad.edit(s -> s.moveByte(ROUTINE,2));
        assertThrows(IllegalStateException.class,bad::run); assertEquals(2,bad.slot().routine());
        var failed=new Harness(); failed.edit(s -> s.moveByte(ROUTINE,8)); var original=new AssertionError("draw"); failed.afterDraw=c -> { throw original; };
        assertSame(original,assertThrows(AssertionError.class,failed::run)); assertEquals(0,original.getSuppressed().length); assertEquals(4,failed.slot().spriteFlags());
    }

    @Test void everyExternalPortRejectsSynchronousReentryAndEffectsAreOnlyCompletedObservations() {
        var h=new Harness(); h.reachedMain(); Runnable reenter=() -> assertThrows(IllegalStateException.class,h::run);
        h.beforeRead=c -> reenter.run(); h.beforeStore=c -> reenter.run(); h.beforeSound=c -> reenter.run(); h.afterDraw=c -> reenter.run();
        var result=h.run(); assertEquals(5,h.stores.size()); assertEquals(1,h.sounds.size()); assertEquals(1,h.draws.size());
        assertThrows(UnsupportedOperationException.class,() -> result.effects().clear());
        assertEquals(1,result.effects().stream().filter(CdSignpost.SoundRequested.class::isInstance).count());
    }

    private static CdSignpost.Assets assets(byte[] animations,byte[] bonuses) {
        byte[] map=new byte[13]; for(int i=0;i<6;i++) w(map,2*i,12);
        return new CdSignpost.Assets(CdObjectVisualAssets.decodeStatic(map,6,76),MAP,bytes(animations),bytes(bonuses));
    }
    private static CdAssetBytes bytes(byte[] b) { return CdAssetBytes.copyOf(b); }
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }
    private static void w(byte[] b,int at,int value) { b[at]=(byte)(value>>>8); b[at+1]=(byte)value; }
    private static void l(byte[] b,int at,int value) { w(b,at,value>>>16); w(b,at+2,value); }
    private static List<byte[]> images(Harness h) { var out=new ArrayList<byte[]>(); for(int whole=32;whole<128;whole++) out.add(h.pool().state(whole).copyBytes()); return out; }
    private static void assertImages(List<byte[]> expected,Harness h) { for(int whole=32;whole<128;whole++) assertArrayEquals(expected.get(whole-32),h.pool().state(whole).copyBytes(),"whole "+whole); }
    private static CdObjectState dirty(CdObjectState s) {
        for(var f:CdObjectState.ByteField.values()) s=s.moveByte(f,0x51+7*f.byteOffset());
        for(var f:CdObjectState.WordField.values()) s=s.moveWord(f,0xA157+0x103*f.byteOffset());
        for(var f:CdObjectState.LongField.values()) s=s.moveLong(f,0xA1536729+0x01030507*f.byteOffset()); return s;
    }
    private static CdObjectPlacements layout() {
        var b=ByteBuffer.allocate(34); b.putShort((short)12).putShort((short)28).putShort((short)65535).putInt(0).putShort((short)0);
        b.putShort((short)X0).putShort((short)Y0).put((byte)0x97).put((byte)0xC0).put((byte)0x40).put((byte)0);
        b.putShort((short)65535).putInt(0).putShort((short)0); b.putShort((short)65535).putInt(0); return CdObjectPlacements.decodeLayout(b.array());
    }
    private static final class Harness {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(layout()); final CdSignpost body;
        final Map<CdSignpost.ReadField,Integer> values=new EnumMap<>(CdSignpost.ReadField.class);
        final Map<CdSignpost.StoreField,Integer> external=new EnumMap<>(CdSignpost.StoreField.class);
        final List<String> order=new ArrayList<>(); final List<CdSignpost.ReadCall> reads=new ArrayList<>();
        final List<CdSignpost.StoreCall> stores=new ArrayList<>(); final List<CdSignpost.Sound> sounds=new ArrayList<>(); final List<CdSignpost.DrawCall> draws=new ArrayList<>();
        Consumer<CdSignpost.ReadCall> beforeRead=c -> {}; Consumer<CdSignpost.StoreCall> beforeStore=c -> {};
        Consumer<CdSignpost.SoundCall> beforeSound=c -> {}; Consumer<CdSignpost.DrawCall> afterDraw=c -> {};
        Harness() { this(assets(ANI,BONUSES)); } Harness(CdSignpost.Assets a) { this(a,true,true,true); }
        Harness(CdSignpost.Assets a,boolean hasRead,boolean hasStore,boolean hasSound) {
            for(var field:CdSignpost.ReadField.values()) values.put(field,0);
            values.put(CdSignpost.ReadField.PLAYER_Y_WORD,Y0+128); values.put(CdSignpost.ReadField.PLAYER_X_WORD,X0+1); values.put(CdSignpost.ReadField.CAMERA_X_WORD,902);
            edit(s -> s.moveByte(ID,0x17).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveWord(X,X0).moveWord(Y,Y0).moveLong(MAPPINGS,MAP));
            body=new CdSignpost(placement,a,hasRead?c -> { assertCut(c.actualCut(),c.expectedSource()); reads.add(c); order.add("read:"+c.field()); beforeRead.accept(c); return values.get(c.field()); }:null,
                    hasStore?c -> { assertCut(c.actualCut(),c.expectedSource()); beforeStore.accept(c); stores.add(c); order.add("store:"+c.field()+":"+c.value()); external.put(c.field(),c.value()); }:null,
                    hasSound?c -> { assertCut(c.actualCut(),c.expectedSource()); beforeSound.accept(c); sounds.add(c.sound()); order.add("sound:"+c.sound()); }:null,
                    c -> { assertCut(c.actualCut(),c.expectedSource()); draws.add(c); order.add("draw"); pool().write(slot().moveByte(SPRITE_FLAGS,slot().spriteFlags()&0x7F)); afterDraw.accept(c); });
        }
        void reachedMain() { values.put(CdSignpost.ReadField.PLAYER_Y_WORD,Y0); }
        void results() { edit(s -> s.moveByte(ROUTINE,6).moveByte(LEVEL_END_TIMER,1)); }
        CdObjectPool pool() { return placement.pool(); } CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> op) { pool().write(op.apply(slot())); }
        CdSignpost.Result run() { return body.run(WHOLE); }
        long children() { return pool().capture().slots().stream().filter(s -> s.id()==0x3A).count(); }
        void assertCut(CdObjectPlacementRuntime.Snapshot cut,CdObjectState source) {
            placement.validateRestore(cut); assertEquals(source,slot()); assertEquals(placement.status(),cut.status());
            assertArrayEquals(placement.capture().savedFlags().copy(),cut.savedFlags().copy());
            for(var image:cut.pool().slots()) assertEquals(image,pool().state(image.identity().wholeIndex()));
        }
    }
}
