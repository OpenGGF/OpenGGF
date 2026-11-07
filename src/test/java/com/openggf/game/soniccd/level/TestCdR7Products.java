package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.resources.CdNemesisPlcDecoder;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Shared decoders on original R7 sources; independent native-style hulls are not native execution. */
class TestCdR7Products {
    private record NativeProduct(byte[] bytes,int prepared,int consumed) { }
    private static NativeProduct nativeProduct(byte[] input,long address,byte seed) {
        byte[] table=new byte[512];Arrays.fill(table,seed);
        var state=CdNemesisPlcDecoder.prepare(CdAssetBytes.copyOf(input),address,CdAssetBytes.copyOf(table));int prepared=state.inputOffset();
        var out=new ByteArrayOutputStream();while(state.remainingTiles()>0) {var tile=CdNemesisPlcDecoder.decodeTile(state);out.writeBytes(tile.bytes().copy());state=tile.state();}
        return new NativeProduct(out.toByteArray(),prepared,state.inputOffset());
    }
    @ParameterizedTest @EnumSource(CdR7DataLayout.Programme.class)
    void allActualProductsMatchIndependentJavaAndSourceNativeReadContracts(CdR7DataLayout.Programme p)throws Exception {
        var fixture=TestCdR7DataLayout.actual();var r=fixture.readers().get(p);var image=fixture.images().get(p);int count=0;
        for(var source:r.definition().sources()) {
            count++;var key=(CdR7DataLayout.Range)source.key();var fact=CdR7ProgrammeProfiles.contract(p,key);
            var canonical=r.canonicalInput(key);var java=r.javaInput(key);var nativeBytes=r.nativeInput(key);
            assertEquals(key.length(),canonical.length());assertEquals(fact.javaProduct().input().length(),java.length());assertEquals(fact.nativeInput().length(),nativeBytes.length());
            assertEquals(fact.javaProduct().input().sha(),CdR11aProfile.sha256(java.copy()));assertEquals(fact.nativeInput().sha(),CdR11aProfile.sha256(nativeBytes.copy()));
            assertArrayEquals(image.readFile(key.fileOffset(),java.length()),java.copy());assertArrayEquals(image.readFile(key.fileOffset(),nativeBytes.length()),nativeBytes.copy());
            assertArrayEquals(canonical.copy(),java.slice(0,canonical.length()).copy());assertArrayEquals(canonical.copy(),nativeBytes.slice(0,canonical.length()).copy());
            var nativeResult=nativeProduct(nativeBytes.copy(),0x200000L+key.fileOffset(),(byte)0);var poison=nativeProduct(nativeBytes.copy(),0x200000L+key.fileOffset(),(byte)255);
            assertEquals(fact.nativePrepared(),nativeResult.prepared());assertEquals(fact.nativeConsumed(),nativeResult.consumed());assertEquals(fact.javaProduct().tableBytes()+2,nativeResult.prepared());
            assertArrayEquals(nativeResult.bytes(),poison.bytes());assertEquals(nativeResult.consumed(),poison.consumed());
            var result=r.decoded(key);assertSame(result,r.decoded(key));assertEquals(fact.javaProduct().consumed(),result.consumedInputBytes());
            assertArrayEquals(nativeResult.bytes(),result.bytes().copy());assertEquals(fact.javaProduct().outputSha(),CdR11aProfile.sha256(result.bytes().copy()));assertEquals(source.output(),result.bytes().length());
        }
        assertEquals(productCount(p),count);
    }
    @ParameterizedTest @EnumSource(CdR7DataLayout.Programme.class)
    void everyReachedLastByteAndForeignSourceIsRejectedAtItsActualBoundary(CdR7DataLayout.Programme p)throws Exception {
        var r=TestCdR7DataLayout.actual().readers().get(p);
        for(var source:r.definition().sources()) {
            var key=(CdR7DataLayout.Range)source.key();var fact=CdR7ProgrammeProfiles.contract(p,key);var java=r.javaInput(key).copy();var nativeBytes=r.nativeInput(key).copy();
            assertThrows(IOException.class,()->CdNemesisAsset.decode(Arrays.copyOf(java,fact.javaProduct().consumed()-1),source.output(),source.capacity()));
            var fault=assertThrows(CdNemesisPlcDecoder.DecodeException.class,()->nativeProduct(Arrays.copyOf(nativeBytes,fact.nativeConsumed()-1),0x200000L+key.fileOffset(),(byte)0));
            assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED,fault.reason());assertEquals(fact.nativeConsumed()-1,fault.diagnostic().inputOffset());assertEquals(0x200000L+key.fileOffset()+fact.nativeConsumed()-1,fault.diagnostic().sourcePointer());
            var equal=new CdR7DataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256());
            assertThrows(IllegalArgumentException.class,()->r.decoded(equal));assertThrows(IllegalArgumentException.class,()->CdR7ProgrammeProfiles.contract(p,equal));
        }
        var foreign=CdR7DataLayout.Programme.values()[(p.ordinal()+1)%10].range("StageGfx");
        assertThrows(IllegalArgumentException.class,()->r.decoded(foreign));assertThrows(IllegalArgumentException.class,()->r.nativeInput(foreign));
    }
    @Test void catalogueLookaheadCursorsNeverSelectActualJavaOrNativeHulls()throws Exception {
        int[] logical={26,25,26,26,26,25,26,26,31,29},look={27,28,26,26,26,27,26,26,31,32};int total=0;
        for(var p:CdR7DataLayout.Programme.values()) {
            var r=TestCdR7DataLayout.actual().readers().get(p);int j=0,n=0;
            for(var source:r.definition().sources()) {
                total++;var f=CdR7ProgrammeProfiles.contract(p,(CdR7DataLayout.Range)source.key());
                if(f.javaProduct().consumed()!=f.catalogueLogical())j++;if(f.nativeConsumed()!=f.catalogueLookahead())n++;
            }
            assertEquals(logical[p.ordinal()],j);assertEquals(look[p.ordinal()],n);
        }
        assertEquals(466,total);
    }
    @Test void independentActualOwnersNeverShareTheirInputOrProductCaches()throws Exception {
        var fixture=TestCdR7DataLayout.actual();
        try(var actual=CdProgramAssets.open(fixture.cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            for(var p:CdR7DataLayout.Programme.values()) {
                var first=fixture.readers().get(p);var second=CdProgrammeReader.open(actual,p.payload().stage());var key=p.range("StageGfx");
                assertNotSame(first.canonicalInput(key),second.canonicalInput(key));assertNotSame(first.javaInput(key),second.javaInput(key));assertNotSame(first.nativeInput(key),second.nativeInput(key));
                assertNotSame(first.decoded(key),second.decoded(key));assertArrayEquals(first.decoded(key).bytes().copy(),second.decoded(key).bytes().copy());
            }
        }
    }
    private static int productCount(CdR7DataLayout.Programme p) { return switch(p) {
        case R71A -> 46;
        case R71B -> 46;
        case R71C -> 45;
        case R71D -> 45;
        case R72A -> 45;
        case R72B -> 45;
        case R72C -> 45;
        case R72D -> 45;
        case R73C -> 52;
        case R73D -> 52;
    }; }
}
