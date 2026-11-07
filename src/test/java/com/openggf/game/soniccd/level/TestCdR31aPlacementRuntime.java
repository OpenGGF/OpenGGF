package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.openggf.game.soniccd.level.TestCdR31aAssets.*;
import static com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.SavedBit.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/**
 * Tests authored first. Real original-media DATA binding, with explicitly constructed complete bank/pool inputs.
 * Calls the existing public component only; no World/Frame/native invocation, actor or restore authority is forged.
 * No natural R31A route claim. Java UNRUN.
 */
class TestCdR31aPlacementRuntime {
    @Test void factoryRetainsAll517RowsEveryCarriedByteAndAll96ZeroSlotsWithoutInit() throws Exception {
        var a=actual().assets();byte[] raw=dirtyBank();var input=CdAssetBytes.copyOf(raw);var p=CdObjectPlacementRuntime.fromR31aAssets(a,input);
        assertSame(a.placements(),p.placements());assertTrue(p.ownsR31aAssets(a));assertTrue(p.sourceIdentity().isEmpty());
        assertEquals(new CdObjectPlacementRuntime.Status(0,0,0,0,0xC7,0x9E,-1,-1),p.status());
        assertEquals(517,p.placements().records().size());assertEquals(766,CdObjectPlacementRuntime.SAVED_BANK_BYTES);
        assertArrayEquals(raw,p.capture().savedFlags().copy());assertEquals(96,p.pool().capture().slots().size());
        for(int whole=32;whole<128;whole++)assertArrayEquals(new byte[64],p.pool().state(whole).copyBytes());
        p.bsetSavedFlag(1,1,LOADED);assertArrayEquals(raw,input.copy());assertEquals(0xC7,input.unsignedByte(0));
        assertThrows(IndexOutOfBoundsException.class,()->p.pool().state(31));assertThrows(IndexOutOfBoundsException.class,()->p.pool().state(128));
    }

    @Test void bankChecksPrecedeAssetAdmissionAndDoNotNormalizeOrResize() throws Exception {
        assertThrows(NullPointerException.class,()->CdObjectPlacementRuntime.fromR31aAssets(null,null));
        for(int n:new int[]{0,765,767,770})assertThrows(IllegalArgumentException.class,()->CdObjectPlacementRuntime.fromR31aAssets(null,CdAssetBytes.copyOf(new byte[n])));
        assertThrows(NullPointerException.class,()->CdObjectPlacementRuntime.fromR31aAssets(null,CdAssetBytes.copyOf(new byte[766])));
    }

    @Test void relationRequiresTheRetainedActualAggregateAndCacheNotEqualPublicValues() throws Exception {
        var f=actual();var a=f.assets();var independent=CdR31aAssets.fromPrograms(source(f,f.image()),CdStagePayload.R31A.stage());var p=placement();
        assertEquals(a.identity(),independent.identity());assertEquals(a.placements().records(),independent.placements().records());
        assertFalse(p.ownsR31aAssets(independent));assertFalse(p.ownsR31aAssets(null));assertFalse(new CdObjectPlacementRuntime(a.placements()).ownsR31aAssets(a));
        var fake=mock(CdR31aAssets.class);when(fake.identity()).thenReturn(a.identity());when(fake.placements()).thenReturn(a.placements());
        assertFalse(p.ownsR31aAssets(fake));assertThrows(IllegalArgumentException.class,()->CdObjectPlacementRuntime.fromR31aAssets(fake,CdAssetBytes.copyOf(scanBank())));
        assertTrue(p.ownsR31aAssets(a));
    }

    @Test void firstNativeWindowStopsAtEqualityAndWritesOnlyPlacementFields() throws Exception {
        var p=placement();dirtyFree(p);var before=p.capture();p.spawnObjects(0,1);
        assertEquals(new CdObjectPlacementRuntime.Status(2,0,1,0,2,1,0,0),p.status());
        assertSlots(before,p,new int[][]{{0x1A4,0x3B0,0x19,2,0,1}});
        byte[] bank=scanBank();bank[0]=2;bank[1]=1;bank[6]|=(byte)0x80;assertArrayEquals(bank,p.capture().savedFlags().copy());
        assertEquals(0x5C,p.pool().state(32).routine());assertEquals(0x1234,p.pool().state(32).word(X_FRACTION));
        assertEquals(0xADEF,p.pool().state(32).word(Y_FRACTION));
        assertEquals(0x280,p.placements().records().get(1).centreX(),"Equal forward bound is not consumed");
    }

    @Test void nextChunkProcessesBothEqualXRowsInNativeOrderAndMarkerOnlyMasksPeriod() throws Exception {
        var p=placement();dirtyFree(p);var before=p.capture();p.spawnObjects(0x80,0x81);
        assertEquals(new CdObjectPlacementRuntime.Status(2,0x80,3,0,4,1,0,0),p.status());
        assertSlots(before,p,new int[][]{{0x1A4,0x3B0,0x19,2,0,1},{0x280,0x1C5,0x10,0x52,0,2},{0x280,0x3EC,0x32,0,0,3}});
        byte[] bank=scanBank();bank[0]=4;bank[1]=1;for(int id:new int[]{1,2,3})bank[2+3*id+1]|=(byte)0x80;assertArrayEquals(bank,p.capture().savedFlags().copy());
        var plain=placement();dirtyFree(plain);plain.spawnObjects(0x80,1);assertEquals(p.status(),plain.status());
        for(int i=0;i<96;i++)assertArrayEquals(p.pool().state(32+i).copyBytes(),plain.pool().state(32+i).copyBytes());
        assertArrayEquals(p.capture().savedFlags().copy(),plain.capture().savedFlags().copy());
    }

    @Test void wrongPeriodSkipAdvancesSourceSavedCounterWithoutAllocatingOrWritingItsColumn() throws Exception {
        var p=placement();var before=p.capture();p.spawnObjects(0x80,0);
        assertEquals(new CdObjectPlacementRuntime.Status(2,0x80,3,0,4,1,0,0),p.status());
        assertSlots(before,p,new int[][]{{0x1A4,0x3B0,0x19,2,0,1},{0x280,0x1C5,0x10,0x52,0,2}});
        byte[] bank=scanBank();bank[0]=4;bank[1]=1;for(int id:new int[]{1,2})bank[2+3*id]|=(byte)0x80;assertArrayEquals(bank,p.capture().savedFlags().copy());
        assertEquals(scanBank()[11]&255,p.savedFlag(3,0));
    }

    @Test void nativeColdSeekReachesTrueRows515And516WithSavedIds223And224() throws Exception {
        var p=placement();dirtyFree(p);var before=p.capture();p.spawnObjects(0x2F80,1);
        assertEquals(new CdObjectPlacementRuntime.Status(2,0x2F80,517,515,225,223,0,0),p.status());
        assertSlots(before,p,new int[][]{{0x2F38,0x594,0x17,0,0,223},{0x2F90,0x530,0x14,0,0,224}});
        byte[] bank=scanBank();bank[0]=(byte)225;bank[1]=(byte)223;bank[672]|=(byte)0x80;bank[675]|=(byte)0x80;
        assertArrayEquals(bank,p.capture().savedFlags().copy());assertEquals(223,p.pool().state(32).unsignedByte(SAVED_FLAGS_ID));
        assertEquals(224,p.pool().state(33).unsignedByte(SAVED_FLAGS_ID));assertTrue(p.ownsR31aAssets(actual().assets()));
    }

    @Test void alreadyLoadedLateParentSkipsAllocationAndRetainsMemberBitsAndOtherPeriods() throws Exception {
        byte[] bank=scanBank();bank[672]=(byte)0x85;bank[674]=0x47;var p=CdObjectPlacementRuntime.fromR31aAssets(actual().assets(),CdAssetBytes.copyOf(bank));var before=p.capture();
        p.spawnObjects(0x2F80,1);assertSlots(before,p,new int[][]{{0x2F90,0x530,0x14,0,0,224}});
        bank[0]=(byte)225;bank[1]=(byte)223;bank[675]|=(byte)0x80;assertArrayEquals(bank,p.capture().savedFlags().copy());
        assertEquals(0x85,p.savedFlag(223,1));assertEquals(0x47,p.savedFlag(224,0));
    }

    @Test void fullPoolFailureRestoresTentativeSavedBitAndSameChunkNeverRetries() throws Exception {
        var p=placement();dirtyFree(p);for(int whole=32;whole<128;whole++)p.pool().write(p.pool().state(whole).moveByte(ID,1));var full=p.capture();
        p.spawnObjects(0x2F80,1);assertEquals(new CdObjectPlacementRuntime.Status(2,0x2F80,515,515,223,223,0,0),p.status());
        assertEquals(full.pool().slots(),p.pool().capture().slots());byte[] bank=scanBank();bank[0]=bank[1]=(byte)223;assertArrayEquals(bank,p.capture().savedFlags().copy());
        p.pool().deleteObject(32);var free=p.capture();p.spawnObjects(0x2FFF,1);assertCut(free,p);
        p.spawnObjects(0x3000,1);assertEquals(new CdObjectPlacementRuntime.Status(2,0x3000,516,516,224,224,0,0),p.status());
        assertArrayEquals(literal(free.pool().slots().getFirst(),new int[]{0x2F38,0x594,0x17,0,0,223}).copyBytes(),p.pool().state(32).copyBytes());
        bank[0]=bank[1]=(byte)224;bank[672]|=(byte)0x80;assertArrayEquals(bank,p.capture().savedFlags().copy());
        for(int whole=33;whole<128;whole++)assertEquals(full.pool().slots().get(whole-32),p.pool().state(whole));
    }

    @Test void backwardPassUsesNativeReverseOrderAndUnflaggedRowDoesNotReplaceSavedByte() throws Exception {
        var p=placement();p.spawnObjects(0x2F80,1);p.pool().deleteObject(32);p.pool().deleteObject(33);
        p.bclrSavedFlag(223,1,LOADED);p.bclrSavedFlag(224,1,LOADED);var before=p.capture();p.spawnObjects(0x2B80,1);
        assertEquals(new CdObjectPlacementRuntime.Status(2,0x2B80,515,513,223,222,0,0),p.status());
        assertSlots(before,p,new int[][]{{0x2BC0,0x584,0x16,0,0,222},{0x2B80,0x488,0x2F,255,0,-1}});
        byte[] bank=before.savedFlags().copy();bank[0]=(byte)223;bank[1]=(byte)222;bank[669]|=(byte)0x80;assertArrayEquals(bank,p.capture().savedFlags().copy());
        assertEquals(0,p.pool().state(33).unsignedByte(SAVED_FLAGS_ID));
    }

    @Test void ownCaptureRestoresEveryPartitionAndReplaysWithoutNewSourceOrInit() throws Exception {
        var p=placement();dirtyFree(p);var cold=p.capture();p.spawnObjects(0,1);var early=p.capture();p.spawnObjects(0x2F80,1);var late=p.capture();
        p.pool().deleteObject(32);p.bsetSavedFlag(253,2,MEMBER_6);p.restore(early);assertCut(early,p);
        p.spawnObjects(0x2F80,1);assertCut(late,p);p.restore(cold);assertCut(cold,p);p.spawnObjects(0,1);assertCut(early,p);
        assertTrue(p.ownsR31aAssets(actual().assets()));assertSame(actual().assets().placements(),p.placements());
    }

    @Test void foreignRuntimeIndependentSourceAndConstructedCapturesAreRejectedBeforeAnyWrite() throws Exception {
        var f=actual();var p=placement();p.spawnObjects(0x2F80,1);var before=p.capture();
        var same=CdObjectPlacementRuntime.fromR31aAssets(f.assets(),before.savedFlags());
        var independent=CdObjectPlacementRuntime.fromR31aAssets(CdR31aAssets.fromPrograms(source(f,f.image()),CdStagePayload.R31A.stage()),before.savedFlags());
        var constructed=new CdObjectPlacementRuntime(f.assets().placements());
        for(var other:List.of(same,independent,constructed)){
            assertThrows(IllegalArgumentException.class,()->p.validateRestore(other.capture()));assertThrows(IllegalArgumentException.class,()->p.restore(other.capture()));assertCut(before,p);
        }
        assertThrows(NullPointerException.class,()->p.restore(null));assertCut(before,p);
    }

    @Test void exactNativeBankResetAndFiniteAddressesStayIndependentOf517SourceIndices() throws Exception {
        var p=CdObjectPlacementRuntime.fromR31aAssets(actual().assets(),CdAssetBytes.copyOf(dirtyBank()));
        p.resetSavedObjectFlags();byte[] clean=new byte[766];clean[0]=clean[1]=1;assertArrayEquals(clean,p.capture().savedFlags().copy());
        assertEquals(new CdObjectPlacementRuntime.Status(0,0,0,0,1,1,-1,-1),p.status());
        p.bsetSavedFlag(224,1,LOADED);clean[675]=(byte)0x80;assertArrayEquals(clean,p.capture().savedFlags().copy());
        p.bsetSavedFlag(254,1,MEMBER_7);clean[765]=0x40;assertArrayEquals(clean,p.capture().savedFlags().copy());var before=p.capture();
        assertThrows(IndexOutOfBoundsException.class,()->p.savedFlag(254,2));assertThrows(IndexOutOfBoundsException.class,()->p.savedFlag(255,0));
        assertThrows(IllegalArgumentException.class,()->p.savedFlag(256,0));assertThrows(IllegalArgumentException.class,()->p.savedFlag(516,1));assertCut(before,p);
    }

    @Test void invalidNativeWidthsPeriodsAndPublicSnapshotShapeDoNotBroadenAuthority() throws Exception {
        var p=placement();var before=p.capture();
        for(int[] in:new int[][]{{-1,1},{65536,1},{0,-1},{0,256},{0,3},{0,0x83}})assertThrows(IllegalArgumentException.class,()->p.spawnObjects(in[0],in[1]));
        assertCut(before,p);var snapshot=CdObjectPlacementRuntime.Snapshot.class;assertFalse(snapshot.isRecord());assertEquals(0,snapshot.getConstructors().length);
        assertEquals(1,snapshot.getDeclaredConstructors().length);assertEquals(10,snapshot.getDeclaredConstructors()[0].getParameterCount());
        var field=snapshot.getDeclaredField("r31aSource");assertTrue(Modifier.isPrivate(field.getModifiers()));assertTrue(Modifier.isFinal(field.getModifiers()));assertEquals(CdR31aAssets.class,field.getType());
        assertEquals(List.of("pool/0","savedFlags/0","status/0"),Arrays.stream(snapshot.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());
    }

    private static CdObjectPlacementRuntime placement() throws Exception{return CdObjectPlacementRuntime.fromR31aAssets(actual().assets(),CdAssetBytes.copyOf(scanBank()));}
    private static byte[] dirtyBank(){byte[] b=new byte[766];for(int i=0;i<b.length;i++)b[i]=(byte)(0x5B+i*0x35);b[0]=(byte)0xC7;b[1]=(byte)0x9E;return b;}
    private static byte[] scanBank(){var b=dirtyBank();for(int i=2;i<b.length;i++)b[i]&=0x7F;return b;}
    /** Constructed complete public-pool component preimages. No private owner/invocation or raw-state constructor. */
    private static void dirtyFree(CdObjectPlacementRuntime p){for(int whole=32;whole<128;whole++)p.pool().write(p.pool().state(whole).moveByte(ROUTINE,0x5C).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0xADEF).moveWord(TILE,0x9876).moveLong(MAPPINGS,0x00234567).moveWord(X_VELOCITY,0xFEDC).moveWord(Y_VELOCITY,0x7654).moveByte(COLLISION_TYPE,0xD3).moveByte(SAVED_FLAGS_ID,0x42));}
    private static CdObjectState literal(CdObjectState s,int[] r){int flips=(r[1]>>>14)&3;s=s.moveWord(X,r[0]).moveWord(Y,r[1]&0xFFF).moveByte(SPRITE_FLAGS,flips).moveByte(FLAGS,flips).moveByte(ID,r[2]).moveByte(SUBTYPE,r[3]).moveByte(SUBTYPE2,r[4]);return r[5]<0?s:s.moveByte(SAVED_FLAGS_ID,r[5]);}
    private static void assertSlots(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p,int[][] rows){for(int i=0;i<96;i++){var expected=before.pool().slots().get(i);if(i<rows.length)expected=literal(expected,rows[i]);assertArrayEquals(expected.copyBytes(),p.pool().state(32+i).copyBytes(),"all64 bytes whole"+(32+i));}}
    private static void assertCut(CdObjectPlacementRuntime.Snapshot cut,CdObjectPlacementRuntime p){assertEquals(cut.status(),p.status());assertEquals(cut.pool().slots(),p.pool().capture().slots());assertArrayEquals(cut.savedFlags().copy(),p.capture().savedFlags().copy());}
}
