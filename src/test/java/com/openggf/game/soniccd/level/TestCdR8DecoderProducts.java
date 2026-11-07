package com.openggf.game.soniccd.level;

import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.level.TestCdR8DataLayout.reader;
import static org.mockito.Mockito.*;

/** Canonical Java products and independently source-derived native read hulls, not native execution. */
class TestCdR8DecoderProducts {
    @ParameterizedTest @EnumSource(CdR8DataLayout.Programme.class)
    void all420CompressedProductsRetainSeparateRealInputExtents(CdR8DataLayout.Programme p)throws Exception {
        var r=reader(p);int expected=switch(p) {case R81A,R81B,R81C,R81D->41;case R82A->38;case R82B,R82C,R82D->44;case R83C,R83D->43;};
        assertEquals(expected,r.definition().sources().size());
        for(var source:r.definition().sources()) {
            var key=(CdR8DataLayout.Range)source.key();var contract=source.javaProduct().orElseThrow();
            var include=r.canonicalInput(key);var java=r.javaInput(key);var nativeInput=r.nativeInput(key);
            assertEquals(key.length(),include.length());assertEquals(key.sha256(),CdR11aProfile.sha256(include.copy()));
            assertArrayEquals(include.copy(),java.slice(0,include.length()).copy());assertArrayEquals(include.copy(),nativeInput.slice(0,include.length()).copy());
            assertSame(include,r.canonicalInput(key));assertSame(java,r.javaInput(key));assertSame(nativeInput,r.nativeInput(key));
            var product=r.decoded(key);assertSame(product,r.decoded(key));assertEquals(source.output(),product.bytes().length());
            assertEquals(contract.consumed(),product.consumedInputBytes());assertEquals(contract.outputSha(),CdR11aProfile.sha256(product.bytes().copy()));
            var direct=CdNemesisAsset.decode(java.copy(),source.output(),source.capacity());assertArrayEquals(product.bytes().copy(),direct.bytes().copy());assertEquals(product.consumedInputBytes(),direct.consumedInputBytes());
            if(java.length()==include.length())assertArrayEquals(product.bytes().copy(),CdNemesisAsset.decode(include.copy(),source.output(),source.capacity()).bytes().copy());
            else assertThrows(IOException.class,()->CdNemesisAsset.decode(include.copy(),source.output(),source.capacity()));
            // Every extra byte is in the actual original, not generated EOF padding.
            assertArrayEquals(TestCdR8DataLayout.actual().images().get(p).readFile(key.fileOffset(),nativeInput.length()),nativeInput.copy());
            assertEquals(source.nativeInput().orElseThrow().sha(),CdR11aProfile.sha256(nativeInput.copy()));
        }
    }
    @Test void malformedAndForeignInputsNeverAdoptAProductAndValidRetrySucceeds()throws Exception {
        var p=CdR8DataLayout.Programme.R81A;var key=p.range("StageGfx");var original=reader(p);var control=spy(original);
        var java=original.javaInput(key);var profile=original.definition().sources().stream().filter(s->s.key()==key).findFirst().orElseThrow();
        // Spy starts with a fresh reader so no previously cached product masks the fault.
        try(var actual=com.openggf.game.soniccd.media.CdProgramAssets.open(TestCdR8DataLayout.actual().cue(),Optional.of(com.openggf.game.soniccd.CdReleaseVariant.USA),Optional.empty())) {
            control=spy(CdProgrammeReader.open(actual,p.payload().stage()));var r=control;
            doReturn(CdAssetBytes.copyOf(Arrays.copyOf(java.copy(),profile.javaProduct().orElseThrow().consumed()-1))).when(r).javaInput(key);
            assertThrows(IOException.class,()->r.decoded(key));
            byte[] wrong=java.copy();wrong[0]^=(byte)128;doReturn(CdAssetBytes.copyOf(wrong)).when(r).javaInput(key);
            assertThrows(IOException.class,()->r.decoded(key));
            doReturn(java).when(r).javaInput(key);var accepted=r.decoded(key);assertSame(accepted,r.decoded(key));
        }
        assertThrows(IllegalArgumentException.class,()->original.javaInput(p.range("PlayerGfx")));
        assertThrows(IllegalArgumentException.class,()->original.nativeInput(CdR8DataLayout.Programme.R81B.range("StageGfx")));
    }
    @Test void independentOwnersDoNotShareProductsOrReachedInputs()throws Exception {
        var p=CdR8DataLayout.Programme.R83D;var first=reader(p);var key=p.range("StageGfx");
        try(var actual=com.openggf.game.soniccd.media.CdProgramAssets.open(TestCdR8DataLayout.actual().cue(),Optional.of(com.openggf.game.soniccd.CdReleaseVariant.USA),Optional.empty())) {
            var second=CdProgrammeReader.open(actual,p.payload().stage());assertNotSame(first.canonicalInput(key),second.canonicalInput(key));assertNotSame(first.decoded(key),second.decoded(key));
            assertArrayEquals(first.decoded(key).bytes().copy(),second.decoded(key).bytes().copy());
        }
    }
}
