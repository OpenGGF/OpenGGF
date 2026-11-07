package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.resources.CdNemesisPlcDecoder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Genuine actual-media DATA products; native-style semantics are existing code, never native execution. */
class TestCdTtDecoderProducts {
    private record NativeResult(byte[] output,int prepared,int consumed) { }
    private record Fixture(Path cue,Map<CdR4DataLayout.Programme,CdProgrammeReader> readers,
                           Map<CdR4DataLayout.Programme,CdMmdImage> images) { }
    private static Fixture retained;
    private static synchronized Fixture actual()throws Exception {
        if(retained!=null)return retained;
        var root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Original CD media absent");
        Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))) {
            var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();
        }
        var readers=new EnumMap<CdR4DataLayout.Programme,CdProgrammeReader>(CdR4DataLayout.Programme.class);
        var images=new EnumMap<CdR4DataLayout.Programme,CdMmdImage>(CdR4DataLayout.Programme.class);
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            for(var p:CdR4DataLayout.Programme.values()) {readers.put(p,CdProgrammeReader.open(source,p.payload().stage()));images.put(p,source.mmd(p.file()));}
        }
        retained=new Fixture(cue,Map.copyOf(readers),Map.copyOf(images));return retained;
    }
    private static NativeResult nativeProduct(byte[] input,long address,byte seed) {
        byte[] table=new byte[512];Arrays.fill(table,seed);
        var state=CdNemesisPlcDecoder.prepare(CdAssetBytes.copyOf(input),address,CdAssetBytes.copyOf(table));
        int prepared=state.inputOffset();var output=new ByteArrayOutputStream();
        while(state.remainingTiles()>0) {var tile=CdNemesisPlcDecoder.decodeTile(state);output.writeBytes(tile.bytes().copy());state=tile.state();}
        return new NativeResult(output.toByteArray(),prepared,state.inputOffset());
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void allOriginalProductsMatchIndependentJavaAndNativeReadContracts(CdR4DataLayout.Programme p)throws Exception {
        var reader=actual().readers().get(p);var data=CdProgrammeData.fromReader(reader);var image=actual().images().get(p);
        for(var key:data.compressedRanges()) {
            var f=data.productContract(key);var include=data.canonicalInput(key);var java=data.javaInput(key);var nativeBytes=data.nativeInput(key);
            assertEquals(f.javaProduct().input().length(),java.length());assertEquals(f.nativeInput().length(),nativeBytes.length());
            assertEquals(f.javaProduct().input().sha(),CdR11aProfile.sha256(java.copy()));assertEquals(f.nativeInput().sha(),CdR11aProfile.sha256(nativeBytes.copy()));
            assertArrayEquals(image.readFile(key.fileOffset(),java.length()),java.copy());assertArrayEquals(image.readFile(key.fileOffset(),nativeBytes.length()),nativeBytes.copy());
            assertArrayEquals(include.copy(),java.slice(0,key.length()).copy());assertArrayEquals(include.copy(),nativeBytes.slice(0,key.length()).copy());
            if(java.length()==include.length())assertSame(include,java);else assertNotSame(include,java);
            if(nativeBytes.length()==include.length())assertSame(include,nativeBytes);else if(nativeBytes.length()==java.length())assertSame(java,nativeBytes);
            var nativeResult=nativeProduct(nativeBytes.copy(),0x200000L+key.fileOffset(),(byte)0);
            var poison=nativeProduct(nativeBytes.copy(),0x200000L+key.fileOffset(),(byte)0xFF);
            assertArrayEquals(nativeResult.output(),poison.output());assertEquals(nativeResult.prepared(),poison.prepared());assertEquals(nativeResult.consumed(),poison.consumed());
            assertEquals(f.nativePrepared(),nativeResult.prepared());assertEquals(f.nativeConsumed(),nativeResult.consumed());
            assertEquals(f.javaProduct().tableBytes()+2,nativeResult.prepared());
            var direct=CdNemesisAsset.decode(java.copy(),reader.loadOutputBytes(key),key.key().equals("StageBlocks")?8192:65536);
            var shared=data.decoded(key);assertSame(shared,data.decoded(key));assertEquals(f.javaProduct().consumed(),shared.consumedInputBytes());
            assertEquals(f.javaProduct().outputSha(),CdR11aProfile.sha256(shared.bytes().copy()));
            assertArrayEquals(nativeResult.output(),shared.bytes().copy());assertArrayEquals(direct.bytes().copy(),shared.bytes().copy());
            assertEquals(direct.consumedInputBytes(),shared.consumedInputBytes());assertEquals(reader.loadOutputBytes(key),shared.bytes().length());
        }
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void everyReachedEndpointNeedsItsActualLastByteAndKeepsUnreadIncludeTails(CdR4DataLayout.Programme p)throws Exception {
        var data=CdProgrammeData.fromReader(actual().readers().get(p));
        for(var key:data.compressedRanges()) {
            var f=data.productContract(key);var java=data.javaInput(key).copy();var nativeBytes=data.nativeInput(key).copy();
            var fault=assertThrows(CdNemesisPlcDecoder.DecodeException.class,()->nativeProduct(Arrays.copyOf(nativeBytes,f.nativeConsumed()-1),0x200000L+key.fileOffset(),(byte)0));
            assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED,fault.reason());assertEquals(f.nativeConsumed()-1,fault.diagnostic().inputOffset());
            assertEquals(0x200000L+key.fileOffset()+f.nativeConsumed()-1,fault.diagnostic().sourcePointer());
            assertThrows(IOException.class,()->CdNemesisAsset.decode(Arrays.copyOf(java,f.javaProduct().consumed()-1),actual().readers().get(p).loadOutputBytes(key),65536));
            assertTrue(f.javaProduct().input().length()>=key.length());assertEquals(Math.max(key.length(),f.nativeConsumed()),nativeBytes.length);
        }
    }
    @Test void literalNativeReadAheadIsNotInferredFromJavaCursorOrIncludeLength()throws Exception {
        int[] larger={14,16,14,15,12,16,14,16,6,6};int total=0;
        for(var p:CdR4DataLayout.Programme.values()) {
            var data=CdProgrammeData.fromReader(actual().readers().get(p));int count=0;
            for(var key:data.compressedRanges()) {total++;if(data.nativeInput(key).length()>data.javaInput(key).length())count++;}
            assertEquals(larger[p.ordinal()],count,p.name());
        }
        assertEquals(419,total);
        for(var p:List.of(CdR4DataLayout.Programme.R41D,CdR4DataLayout.Programme.R42D)) {
            var data=CdProgrammeData.fromReader(actual().readers().get(p));var slope=data.productContract(p.range("SlopeElevatorGfxD"));
            assertEquals(180,slope.javaProduct().input().length());assertEquals(178,slope.javaProduct().consumed());assertEquals(180,slope.nativeConsumed());
            var floating=data.productContract(p.range("FloatElevatorGfxD"));assertEquals(460,floating.javaProduct().input().length());
            assertEquals(459,floating.javaProduct().consumed());assertEquals(461,floating.nativeConsumed());assertEquals(461,floating.nativeInput().length());
        }
    }
    @Test void equalAndForeignKeysNeverGainProductOrEndpointAuthority()throws Exception {
        for(var p:CdR4DataLayout.Programme.values()) {
            var r=actual().readers().get(p);var data=CdProgrammeData.fromReader(r);var key=p.range("StageGfx");
            var equal=new CdR4DataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256(),key.nativePath(),key.line());assertEquals(key,equal);
            var foreign=CdR4DataLayout.Programme.values()[(p.ordinal()+1)%10].range("StageGfx");
            for(var bad:List.of(equal,foreign,p.range("PlayerGfx"))) {
                assertThrows(IllegalArgumentException.class,()->data.productContract(bad));assertThrows(IllegalArgumentException.class,()->data.javaInput(bad));
                assertThrows(IllegalArgumentException.class,()->data.nativeInput(bad));assertThrows(IllegalArgumentException.class,()->data.decoded(bad));
            }
        }
    }
    @Test void independentActualOwnersKeepTheirInputAndProductCachesSeparate()throws Exception {
        try(var source=CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            for(var p:CdR4DataLayout.Programme.values()) {
                var first=CdProgrammeData.fromReader(actual().readers().get(p));var reader=CdProgrammeReader.open(source,p.payload().stage());var second=CdProgrammeData.fromReader(reader);var key=p.range("StageGfx");
                assertFalse(first.ownsReader(reader));assertTrue(second.ownsReader(reader));
                assertNotSame(first.canonicalInput(key),second.canonicalInput(key));assertNotSame(first.javaInput(key),second.javaInput(key));assertNotSame(first.nativeInput(key),second.nativeInput(key));
                assertNotSame(first.decoded(key),second.decoded(key));assertArrayEquals(first.decoded(key).bytes().copy(),second.decoded(key).bytes().copy());
            }
        }
    }
    /** Synthetic post-open read fault on a genuine original; never an alternative source ingress. */
    @Test void reachedExtentAndHeaderMutationFailBeforeInputAdoptionAndCanRetry()throws Exception {
        var p=CdR4DataLayout.Programme.R41D;var key=p.range("FloatElevatorGfxD");
        try(var actualSource=CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            var image=spy(actualSource.mmd(p.file()));var source=mock(CdProgramAssets.class,delegatesTo(actualSource));doReturn(image).when(source).mmd(p.file());
            var r=CdProgrammeReader.open(source,p.payload().stage());var original=image.readFile(key.fileOffset(),461);clearInvocations(image);
            for(var changed:List.of(Arrays.copyOf(original,460),mutate(original,460),mutate(original,0))) {
                doReturn(changed).when(image).readFile(key.fileOffset(),461);
                assertThrows(IllegalArgumentException.class,()->r.nativeInput(key));
            }
            doReturn(original).when(image).readFile(key.fileOffset(),461);var accepted=r.nativeInput(key);assertSame(accepted,r.nativeInput(key));
            verify(image,times(4)).readFile(key.fileOffset(),461);
            assertArrayEquals(original,accepted.copy());
        }
    }
    private static byte[] mutate(byte[] input,int offset) {var result=input.clone();result[offset]^=(byte)0x80;return result;}
    /** Synthetic decoder-input fault port; production still enters through authenticated originals. */
    @Test void failedDecodeAndWrongProductDoNotPublishAndRetainEarlierSuccessfulInput()throws Exception {
        var p=CdR4DataLayout.Programme.R41A;var key=p.range("StageGfx");
        try(var source=CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            var r=spy(CdProgrammeReader.open(source,p.payload().stage()));var include=r.canonicalInput(key);var java=r.javaInput(key);clearInvocations(r);
            var f=CdR4ProgrammeProfiles.contract(p,key);
            doReturn(CdAssetBytes.copyOf(Arrays.copyOf(java.copy(),f.javaProduct().consumed()-1))).when(r).javaInput(key);
            assertThrows(IOException.class,()->r.decoded(key));assertSame(include,r.canonicalInput(key));
            doReturn(CdAssetBytes.copyOf(mutate(java.copy(),0))).when(r).javaInput(key);
            assertEquals(p+" existing Java source product/cursor differs",assertThrows(IOException.class,()->r.decoded(key)).getMessage());
            doReturn(java).when(r).javaInput(key);var success=r.decoded(key);assertSame(success,r.decoded(key));
            verify(r,times(3)).javaInput(key);assertSame(include,r.canonicalInput(key));
        }
    }
}
