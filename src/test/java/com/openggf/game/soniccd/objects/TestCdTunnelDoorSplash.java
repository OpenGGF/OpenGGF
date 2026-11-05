package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdTunnelDoorSplash.UnsupportedBoundary.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Written before the owned production file; all Java execution is UNRUN by this source-only author.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Spin Tunnel/Main (Door).asm:135-177;
 * literal complete Data/Animations (Door Splash).asm:1-9 and Data/Mappings (Door Splash).asm:1-94.
 * Constructed values authenticate no media. Actual factory/receipt/World tests belong to the integrating owner.
 */
class TestCdTunnelDoorSplash {
    private static final int WHOLE = 41, MAP = 0x21D652;
    private static final byte[] ANI = bytes(0,6, 0,14, 0,20,
            3,0,4,3,1,2,0xFC,0, 3,0,1,2,0xFC,0, 3,6,5,0xFC);
    private static final byte[] MAPPINGS = bytes(
            0,14, 0,26, 0,38, 0,44, 0,66, 0,88, 0,94,
            2,0xF0,5,0,0,0xFC,0xF8,0,0,4,0xF4,0,
            2,0xE0,0,0,5,0xF8,0xE8,0xE,0,6,0xF0,0,
            1,0xE0,0xF,0,0x12,0xF0,
            4,0xD0,6,0,0x22,0xF8,0xD8,0,0,0x28,0xF0,
            0xE0,0,0,0x29,8,0xE8,0xE,0,0x2A,0xF0,0,
            4,0xC0,0,0,0x36,0xF8,0xC8,6,0,0x37,0xF8,
            0xD8,0,0,0x3D,0xF0,0xE0,0xF,0,0x3E,0xF0,0,
            1,0xF0,9,0,0x4E,0xF4, 1,0xF8,4,0,0x54,0xF8);

    @ParameterizedTest
    @CsvSource({"0,0,FM_A2", "1,0,FM_A2", "2,6,FM_A1"})
    void initUsesLiteralNativeStoresAndRetainsEveryOtherByte(int subtype, int firstFrame, String sound) {
        var h = new Harness();
        var before = h.dirty(subtype).moveByte(PREVIOUS_ANIMATION, 0xF7)
                .moveByte(ANIMATION_FRAME, 0xA9).moveByte(ANIMATION_TIME, 0x38).moveByte(FLAGS, 3);
        h.write(before);
        var result = h.body.run(WHOLE);
        byte[] expected = before.copyBytes();
        expected[0x24]=2; expected[1]=7; expected[0x18]=1;
        expected[4]=0; expected[5]=0x21; expected[6]=(byte)0xD6; expected[7]=0x52;
        expected[0x1C]=(byte)subtype; expected[0x1D]=(byte)subtype;
        expected[2]=3; expected[3]=(byte)0xA7;
        expected[0x1B]=1; expected[0x1E]=3; expected[0x1A]=(byte)firstFrame;
        assertArrayEquals(expected, result.state().copyBytes());
        assertEquals(List.of(CdTunnelDoorSplash.FmSound.valueOf(sound)), h.sounds);
        assertEquals(1, h.drawn.size());
        assertArrayEquals(expected, h.drawn.getFirst().copyBytes());
        assertTrue(result.effects().contains(new CdTunnelDoorSplash.PlayedFm(CdTunnelDoorSplash.FmSound.valueOf(sound))));
        assertFalse(result.deleted());
        assertSame(h.body.assets(), h.assets);
    }

    @Test
    void unchangedDirtyAnimationAndPositiveTimerDoNotPrimeOrReadInvalidSelector() {
        var h = new Harness();
        var before = h.dirty(0xFF).moveByte(PREVIOUS_ANIMATION,0xFF)
                .moveByte(ANIMATION_FRAME,0xE2).moveByte(ANIMATION_TIME,5).moveByte(MAP_FRAME,0xDB);
        h.write(before);
        var result = h.body.run(WHOLE);
        byte[] expected = before.copyBytes();
        expected[0x24]=2; expected[1]=4; expected[0x18]=1;
        expected[4]=0; expected[5]=0x21; expected[6]=(byte)0xD6; expected[7]=0x52;
        expected[2]=3; expected[3]=(byte)0xA7; expected[0x1C]=(byte)0xFF; expected[0x1E]=4;
        assertArrayEquals(expected,result.state().copyBytes());
        assertEquals(List.of(CdTunnelDoorSplash.FmSound.FM_A1),h.sounds);
    }

    @Test
    void soundSeesTheHeaderAndTileBeforeAnyAnimationResetAndNoAllocationOrOtherImageChangesOccur() {
        var h=new Harness();
        h.runtime.pool().write(h.runtime.pool().state(32).moveWord(X_FRACTION,0xBA98).moveByte(ANGLE,0x47));
        h.runtime.pool().write(h.runtime.pool().state(127).moveByte(ID,0x25).moveWord(Y_FRACTION,0x7654));
        var before=h.dirty(1).moveByte(PREVIOUS_ANIMATION,0x7E).moveByte(ANIMATION_TIME,0x3A)
                .moveByte(ANIMATION_FRAME,0xA5);h.write(before);var cut=h.runtime.capture();
        var body=new CdTunnelDoorSplash(h.runtime,h.assets,h::draw,sound->{
            assertEquals(CdTunnelDoorSplash.FmSound.FM_A2,sound);
            var live=h.slot();assertEquals(4,live.spriteFlags());assertEquals(2,live.routine());
            assertEquals(1,live.unsignedByte(ANIMATION));assertEquals(0x03A7,live.word(TILE));
            assertEquals(0x7E,live.unsignedByte(PREVIOUS_ANIMATION));
            assertEquals(0x3A,live.unsignedByte(ANIMATION_TIME));assertEquals(0xA5,live.unsignedByte(ANIMATION_FRAME));
        });
        var result=body.run(WHOLE);assertEquals(0,result.state().unsignedByte(MAP_FRAME));
        for(var old:cut.pool().slots())if(old.identity().wholeIndex()!=WHOLE)
            assertArrayEquals(old.copyBytes(),h.runtime.pool().state(old.identity().wholeIndex()).copyBytes());
        assertEquals(cut.status(),h.runtime.capture().status());
        assertArrayEquals(cut.savedFlags().copy(),h.runtime.capture().savedFlags().copy());
    }

    @Test
    void changedSelectorThreePublishesInitSoundAndResetTimerPrefixBeforeDirectoryFailure() {
        var h = new Harness();
        var before = h.dirty(3).moveByte(PREVIOUS_ANIMATION,0)
                .moveByte(ANIMATION_TIME,70).moveByte(ANIMATION_FRAME,91);
        h.write(before);
        var failure=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->h.body.run(WHOLE));
        assertEquals(ANIMATION_DIRECTORY_READ,failure.boundary());
        assertEquals(WHOLE,failure.wholeIndex()); assertEquals(6,failure.value());
        assertInstanceOf(UnsupportedOperationException.class,failure.getCause());
        var actual=h.slot();
        assertEquals(2,actual.routine()); assertEquals(4,actual.spriteFlags());
        assertEquals(3,actual.unsignedByte(ANIMATION)); assertEquals(3,actual.unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(0,actual.unsignedByte(ANIMATION_FRAME)); assertEquals(255,actual.unsignedByte(ANIMATION_TIME));
        assertEquals(0x03A7,actual.word(TILE)); assertEquals(MAP,actual.longBits(CdObjectState.LongField.MAPPINGS));
        assertEquals(List.of(CdTunnelDoorSplash.FmSound.FM_A1),h.sounds); assertTrue(h.drawn.isEmpty());
        assertEquals(before.longBits(CdObjectState.LongField.X_POSITION),actual.longBits(CdObjectState.LongField.X_POSITION));
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "255"})
    void subtypeTwoTileReadCannotBorrowTheFollowingDeclaredBody(int subtype2) {
        var h=new Harness(); var before=h.dirty(0).moveByte(SUBTYPE2,subtype2);h.write(before);
        var failure=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->h.body.run(WHOLE));
        assertEquals(TILE_ID_SUBTYPE2,failure.boundary());assertEquals(subtype2,failure.value());
        byte[] expected=before.copyBytes();expected[0x24]=2;expected[1]=4;expected[0x18]=1;
        expected[4]=0;expected[5]=0x21;expected[6]=(byte)0xD6;expected[7]=0x52;expected[0x1C]=0;
        assertArrayEquals(expected,h.slot().copyBytes()); assertTrue(h.sounds.isEmpty());assertTrue(h.drawn.isEmpty());
    }

    @Test
    void dirtyExpiredIndexPublishesOnlyTimerThenSpeedBeforeFrameReadFailure() {
        var h=new Harness();var before=h.dirty(0).moveByte(ROUTINE,2).moveByte(ANIMATION,2)
                .moveByte(PREVIOUS_ANIMATION,2).moveByte(ANIMATION_TIME,0).moveByte(ANIMATION_FRAME,250);h.write(before);
        var failure=assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,()->h.body.run(WHOLE));
        assertEquals(ANIMATION_FRAME_READ,failure.boundary());assertEquals(271,failure.value());
        assertArrayEquals(before.moveByte(ANIMATION_TIME,3).copyBytes(),h.slot().copyBytes());
        assertTrue(h.sounds.isEmpty());assertTrue(h.drawn.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0,0:4:3:1:2", "1,0:1:2", "2,6:5"})
    void allThreeLiteralScriptsDrawTheirFcDispatchThenDeleteNextDispatch(int subtype,String encodedFrames) {
        var h=new Harness();var before=h.dirty(subtype).moveByte(PREVIOUS_ANIMATION,0xFE)
                .moveByte(ANIMATION_TIME,0).moveByte(ANIMATION_FRAME,0);h.write(before);
        String[] frames=encodedFrames.split(":");
        for(int call=0;call<frames.length*4;call++) {
            var result=h.body.run(WHOLE);
            assertEquals(Integer.parseInt(frames[call/4]),result.state().unsignedByte(MAP_FRAME),"call "+call);
            assertEquals(3-call%4,result.state().unsignedByte(ANIMATION_TIME));assertEquals(2,result.state().routine());
            assertEquals(before.word(X_FRACTION),result.state().word(X_FRACTION));
            assertEquals(before.word(Y_FRACTION),result.state().word(Y_FRACTION));
        }
        var fc=h.body.run(WHOLE);assertEquals(4,fc.state().routine());assertFalse(fc.deleted());
        assertEquals(frames.length*4+1,h.drawn.size());assertEquals(1,h.sounds.size());
        var cut=h.runtime.capture();var deleted=h.body.run(WHOLE);
        assertTrue(deleted.deleted());assertArrayEquals(new byte[64],deleted.state().copyBytes());
        assertEquals(frames.length*4+1,h.drawn.size());assertEquals(1,h.sounds.size());
        assertArrayEquals(cut.savedFlags().copy(),h.runtime.capture().savedFlags().copy());
    }

    @Test
    void invalidRoutineAndWrongIdFailBeforeAnyCallbackOrWrite() {
        var h=new Harness();var before=h.dirty(0).moveByte(ROUTINE,3);h.write(before);
        assertEquals(ROUTINE_DISPATCH,assertThrows(CdTunnelDoorSplash.UnsupportedTunnelDoorSplash.class,
                ()->h.body.run(WHOLE)).boundary());assertEquals(before,h.slot());
        h.write(before.moveByte(ID,0x0D));assertThrows(IllegalArgumentException.class,()->h.body.run(WHOLE));
        assertTrue(h.sounds.isEmpty());assertTrue(h.drawn.isEmpty());
    }

    @Test
    void throwingSoundPreservesOriginalAndCommittedInitPrefix() {
        var h=new Harness();var before=h.dirty(0);h.write(before);var original=new IllegalStateException("actual FM");
        var body=new CdTunnelDoorSplash(h.runtime,h.assets,h::draw,sound->{throw original;});
        assertSame(original,assertThrows(IllegalStateException.class,()->body.run(WHOLE)));
        assertEquals(2,h.slot().routine());assertEquals(0x03A7,h.slot().word(TILE));
        assertEquals(before.unsignedByte(ANIMATION_TIME),h.slot().unsignedByte(ANIMATION_TIME));
        assertTrue(h.drawn.isEmpty());assertFalse(body.isCurrentCall(new Object()));
    }

    @Test
    void soundCannotMutateAnyOtherDynamicByteEvenWhenItThrows() {
        var h=new Harness();h.write(h.dirty(0));var original=new IllegalArgumentException("sound failure");
        var body=new CdTunnelDoorSplash(h.runtime,h.assets,h::draw,sound->{
            h.runtime.pool().write(h.runtime.pool().state(127).moveByte(RESULTS_TIMER,1));throw original;
        });
        assertSame(original,assertThrows(IllegalArgumentException.class,()->body.run(WHOLE)));
        assertEquals(1,original.getSuppressed().length);assertInstanceOf(IllegalStateException.class,original.getSuppressed()[0]);
        assertTrue(h.drawn.isEmpty());
    }

    @Test
    void soundCannotMutateSavedBankOnSuccessfulReturn() {
        var h=new Harness();h.write(h.dirty(0));
        var body=new CdTunnelDoorSplash(h.runtime,h.assets,h::draw,sound->h.runtime.bsetSavedFlag(
                1,0,CdObjectPlacementRuntime.SavedBit.LOADED));
        assertThrows(IllegalStateException.class,()->body.run(WHOLE));assertTrue(h.drawn.isEmpty());
    }

    @Test
    void soundCannotMutateSourceUnwrittenByte() {
        var h=new Harness();h.write(h.dirty(0));
        var body=new CdTunnelDoorSplash(h.runtime,h.assets,h::draw,sound->h.write(h.slot().moveWord(X_FRACTION,0)));
        assertThrows(IllegalStateException.class,()->body.run(WHOLE));assertTrue(h.drawn.isEmpty());
    }

    @Test
    void drawAllowsOnlyNativeSourceBitSevenClear() {
        var h=new Harness();h.write(h.dirty(0));
        var body=new CdTunnelDoorSplash(h.runtime,h.assets,(pool,identity)->
                pool.write(pool.state(identity.wholeIndex()).moveByte(WIDTH,0)),h.sounds::add);
        assertThrows(IllegalStateException.class,()->body.run(WHOLE));assertEquals(1,h.sounds.size());
    }

    @Test
    void throwingDrawPreservesOriginalAndChecksNonwritesAfterTheNativeClearPrefix() {
        var h=new Harness();h.write(h.dirty(0));var original=new UnsupportedOperationException("actual draw");
        var body=new CdTunnelDoorSplash(h.runtime,h.assets,(pool,identity)->{
            var state=pool.state(identity.wholeIndex());pool.write(state.moveByte(SPRITE_FLAGS,state.spriteFlags()&0x7F));
            pool.write(pool.state(32).moveByte(ANGLE,7));throw original;
        },h.sounds::add);
        assertSame(original,assertThrows(UnsupportedOperationException.class,()->body.run(WHOLE)));
        assertEquals(1,original.getSuppressed().length);assertEquals(3,h.slot().unsignedByte(ANIMATION_TIME));
    }

    @Test
    void reentryFailsBeforeNestedMutationAndFinallyAllowsAnotherDispatch() {
        var h=new Harness();h.write(h.dirty(0));var holder=new CdTunnelDoorSplash[1];
        holder[0]=new CdTunnelDoorSplash(h.runtime,h.assets,h::draw,sound->holder[0].run(WHOLE));
        assertThrows(IllegalStateException.class,()->holder[0].run(WHOLE));
        assertEquals(2,h.slot().routine());assertEquals(0,h.drawn.size());
        var result=holder[0].run(WHOLE);assertEquals(2,result.state().routine());assertEquals(1,h.drawn.size());
    }

    private static CdTunnelDoorSplash.Assets assets() {
        // A constructed complete eighteen-entry directory, with selector D's declared body exactly one WORD.
        byte[] table=new byte[74];for(int selector=0;selector<18;selector++)putWord(table,selector*2,36+selector*2);
        putWord(table,26,64);putWord(table,28,66);putWord(table,64,0x03A7);putWord(table,66,0x7FFF);
        return new CdTunnelDoorSplash.Assets(CdAssetBytes.copyOf(table),CdObjectVisualAssets.decode(MAPPINGS,7,ANI,3,86),MAP);
    }
    private static byte[] bytes(int... values) {byte[] out=new byte[values.length];for(int i=0;i<values.length;i++)out[i]=(byte)values[i];return out;}
    private static void putWord(byte[] bytes,int at,int word) {bytes[at]=(byte)(word>>>8);bytes[at+1]=(byte)word;}
    /** Storage-only fixture, following pinned TestCdBoulder: no production alias or raw-import seam is added. */
    private static CdObjectState rawDirty(CdObjectState base) {
        try {
            var owner=CdObjectState.class.getDeclaredField("owner");owner.setAccessible(true);
            var constructor=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);
            constructor.setAccessible(true);byte[] bytes=new byte[64];
            for(int offset=0;offset<bytes.length;offset++)bytes[offset]=(byte)(0x31+17*offset);
            return constructor.newInstance(owner.get(base),base.identity(),bytes);
        } catch(ReflectiveOperationException failure) {throw new AssertionError("Cannot prepare storage-only full64 fixture",failure);}
    }
    private static CdObjectPlacements layout() {
        var b=ByteBuffer.allocate(26);b.putShort((short)12).putShort((short)20).putShort((short)0xFFFF).putInt(0).putShort((short)0);
        b.putShort((short)0xFFFF).putInt(0).putShort((short)0);b.putShort((short)0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(b.array());
    }
    private static final class Harness {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(layout());
        final CdTunnelDoorSplash.Assets assets=assets();
        final List<CdObjectState> drawn=new ArrayList<>();final List<CdTunnelDoorSplash.FmSound> sounds=new ArrayList<>();
        final CdTunnelDoorSplash body=new CdTunnelDoorSplash(runtime,assets,this::draw,sounds::add);
        CdObjectState slot(){return runtime.pool().state(WHOLE);}void write(CdObjectState state){runtime.pool().write(state);}
        void draw(CdObjectPool pool,CdObjectState.Identity identity){var state=pool.state(identity.wholeIndex());drawn.add(state);pool.write(state.moveByte(SPRITE_FLAGS,state.spriteFlags()&0x7F));}
        CdObjectState dirty(int subtype) {
            return rawDirty(slot()).moveByte(ID,0x0B).moveByte(ROUTINE,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0)
                    .moveByte(SPRITE_FLAGS,0xFF).moveByte(FLAGS,0).moveByte(ANIMATION,0)
                    .moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_TIME,0).moveByte(ANIMATION_FRAME,0);
        }
    }
}
