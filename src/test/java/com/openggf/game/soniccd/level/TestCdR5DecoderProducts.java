package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.resources.CdNemesisPlcDecoder;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Independent existing semantic decoders on real file bytes; never native execution. */
class TestCdR5DecoderProducts {
    private record NativeResult(byte[] bytes,int prepared,int consumed) { }
    private static NativeResult nativeProduct(byte[] input,int address,byte seed) {
        byte[] table=new byte[512];Arrays.fill(table,seed);
        var state=CdNemesisPlcDecoder.prepare(CdAssetBytes.copyOf(input),address,CdAssetBytes.copyOf(table));
        int prepared=state.inputOffset();var out=new ByteArrayOutputStream();
        while(state.remainingTiles()!=0) {var tile=CdNemesisPlcDecoder.decodeTile(state);out.writeBytes(tile.bytes().copy());state=tile.state();}
        return new NativeResult(out.toByteArray(),prepared,state.inputOffset());
    }
    @ParameterizedTest @EnumSource(CdR5DataLayout.Programme.class)
    void everyCompressedProductMatchesIndependentOutputAndBothExactReadContracts(CdR5DataLayout.Programme p)throws Exception {
        var r=TestCdR5DataLayout.actual().readers().get(p);var image=TestCdR5DataLayout.actual().images().get(p);
        for(var s:CdR5ProgrammeProfiles.facts(p).sources()) {
            var key=p.range(s.key());var f=CdR5ProgrammeProfiles.contract(p,key);
            var include=r.canonicalInput(key);var java=r.javaInput(key);var nativeBytes=r.nativeInput(key);
            assertEquals(key.length(),include.length());assertEquals(f.javaProduct().input().length(),java.length());assertEquals(f.nativeInput().length(),nativeBytes.length());
            assertArrayEquals(image.readFile(key.fileOffset(),java.length()),java.copy());assertArrayEquals(image.readFile(key.fileOffset(),nativeBytes.length()),nativeBytes.copy());
            assertArrayEquals(include.copy(),nativeBytes.slice(0,key.length()).copy());assertArrayEquals(include.copy(),java.slice(0,key.length()).copy());
            var product=r.decoded(key);assertSame(product,r.decoded(key));assertEquals(s.output(),product.bytes().length());
            assertEquals(f.javaProduct().consumed(),product.consumedInputBytes());assertEquals(f.javaProduct().outputSha(),CdR11aProfile.sha256(product.bytes().copy()));
            var nativeResult=nativeProduct(nativeBytes.copy(),0x200000+key.fileOffset(),(byte)0);
            var poison=nativeProduct(nativeBytes.copy(),0x200000+key.fileOffset(),(byte)0xFF);
            assertArrayEquals(product.bytes().copy(),nativeResult.bytes());assertArrayEquals(nativeResult.bytes(),poison.bytes());
            assertEquals(f.nativePrepared(),nativeResult.prepared());assertEquals(f.nativeConsumed(),nativeResult.consumed());
            assertEquals(nativeResult.prepared(),poison.prepared());assertEquals(nativeResult.consumed(),poison.consumed());
        }
    }
    @ParameterizedTest @EnumSource(CdR5DataLayout.Programme.class)
    void everyReachedLastByteIsRequiredIncludingRealAdjacentIncludes(CdR5DataLayout.Programme p)throws Exception {
        var r=TestCdR5DataLayout.actual().readers().get(p);
        for(var s:CdR5ProgrammeProfiles.facts(p).sources()) {
            var key=p.range(s.key());var f=CdR5ProgrammeProfiles.contract(p,key);var java=r.javaInput(key).copy();var nativeBytes=r.nativeInput(key).copy();
            assertThrows(IOException.class,()->CdNemesisAsset.decode(Arrays.copyOf(java,f.javaProduct().consumed()-1),s.output(),65536),s.key());
            var fault=assertThrows(CdNemesisPlcDecoder.DecodeException.class,()->nativeProduct(Arrays.copyOf(nativeBytes,f.nativeConsumed()-1),0x200000+key.fileOffset(),(byte)0),s.key());
            assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED,fault.reason());assertEquals(f.nativeConsumed()-1,fault.diagnostic().inputOffset());
            assertEquals(0x200000L+key.fileOffset()+f.nativeConsumed()-1,fault.diagnostic().sourcePointer());
        }
    }
    @ParameterizedTest @EnumSource(CdR5DataLayout.Programme.class)
    void all167PlayerFramesIncludingLastDirectoryEntriesUseExistingExplicitFormat(CdR5DataLayout.Programme p)throws Exception {
        var r=TestCdR5DataLayout.actual().readers().get(p);var sonic=r.sonic();
        assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_167,sonic.sourceFormat());assertEquals(57120,sonic.rawArt().length());assertEquals(57120,sonic.artReadView().length());
        assertThrows(IllegalStateException.class,sonic::alignmentByte);assertEquals(2443,sonic.mappings().length());assertEquals(1338,sonic.dplcs().length());
        assertEquals(167,sonic.mappingFrames().size());assertEquals(167,sonic.dplcFrames().size());int maximum=0;
        for(int i=0;i<167;i++) {
            var map=sonic.mappingFrames().get(i);assertEquals((short)sonic.mappings().unsignedWord(i*2),map.relativeOffset());assertEquals(1+5*map.pieceCount(),map.bytes().length());
            var dplc=sonic.dplcFrames().get(i);assertEquals((short)sonic.dplcs().unsignedWord(i*2),dplc.relativeOffset());assertEquals(2+2*dplc.pieces().size(),dplc.bytes().length());
            int dst=0;for(int j=0;j<dplc.pieces().size();j++) {var entry=dplc.pieces().get(j);assertArrayEquals(sonic.rawArt().slice(entry.artByteOffset(),entry.byteLength()).copy(),sonic.artPiece(i,j).copy());dst+=entry.byteLength();}
            assertEquals(dst,dplc.outputBytes());assertTrue(dst<=768);maximum=Math.max(maximum,dst);
        }
        assertEquals(736,maximum);assertEquals(0,r.view(p.range("PlayerMappingEven")).unsignedByte(0));
        assertThrows(IllegalArgumentException.class,()->CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_167,sonic.mappings().copy(),Arrays.copyOf(sonic.dplcs().copy(),1337),sonic.rawArt().copy()));
    }
    @Test void foreignProductKeysAndIndependentReaderCachesRemainDistinct()throws Exception {
        var p=CdR5DataLayout.Programme.R51A;var r=TestCdR5DataLayout.actual().readers().get(p);var key=p.range("StageGfx");
        assertThrows(IllegalArgumentException.class,()->CdR5ProgrammeProfiles.contract(p,CdR5DataLayout.Programme.R51B.range("StageGfx")));
        assertThrows(IllegalArgumentException.class,()->r.decoded(p.range("PlayerGfx")));
        try(var source=com.openggf.game.soniccd.media.CdProgramAssets.open(TestCdR5DataLayout.actual().cue(),Optional.of(com.openggf.game.soniccd.CdReleaseVariant.USA),Optional.empty())) {
            var other=CdProgrammeReader.open(source,p.payload().stage());assertNotSame(r.view(key),other.view(key));assertNotSame(r.decoded(key),other.decoded(key));
            assertArrayEquals(r.decoded(key).bytes().copy(),other.decoded(key).bytes().copy());
        }
    }
}
