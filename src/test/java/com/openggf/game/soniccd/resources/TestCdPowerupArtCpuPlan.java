package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed semantic controls only; dirty CPU tails are labelled inputs, never runtime ingress. ALL JAVA UNRUN. */
class TestCdPowerupArtCpuPlan {
    @Test void fiveConstructedCopiesReplaceOnly1024HeadBytesAndRetainEveryDirtyTailByteWithoutResidentIngress() {
        byte[] dirty = bytes(1152, 39); var actual = CdAssetBytes.copyOf(dirty);
        var calls = new AtomicInteger();
        var kernel = CdPowerupArtRuntime.fromRawSources(slot -> { calls.incrementAndGet(); return CdAssetBytes.copyOf(bytes(1024,slot)); });
        for (int slot=0;slot<5;slot++) {
            var request = kernel.prepareActiveCopy(actual,2+slot*2);
            assertEquals(0xA880, request.vramByteAddress()); assertEquals(1152,request.bytes().length());
            assertArrayEquals(bytes(1024,slot),request.bytes().slice(0,1024).copy());
            assertArrayEquals(Arrays.copyOfRange(dirty,1024,1152),request.bytes().slice(1024,128).copy());
            assertArrayEquals(dirty,actual.copy());
            byte[] exported=request.bytes().copy(); exported[1024]^=1;
            assertArrayEquals(Arrays.copyOfRange(dirty,1024,1152),request.bytes().slice(1024,128).copy());
        }
        assertEquals(5,calls.get());
    }
    @Test void sharedActivePlanAndLegacyServiceHaveExactFullByteEquivalenceForAllNativeBytes() {
        var kernel=CdPowerupArtRuntime.fromRawSources(slot->CdAssetBytes.copyOf(bytes(1024,slot)));
        var cpu=CdAssetBytes.copyOf(bytes(1152,91)); var resident=CdAssetBytes.copyOf(bytes(1152,107));
        var before=new CdPowerupArtRuntime.State(cpu,resident);
        for(int value=0;value<256;value++) {
            final int raw=value; var gate=CdPowerupArtRuntime.gate(raw);
            if(!gate.copyRequired()) {
                var result=kernel.service(before,raw);assertSame(before,result.state());assertTrue(result.dma().isEmpty());continue;
            }
            int offset=((gate.powerup()-2)&255)*2;
            if((offset&3)!=0 || offset>=20) {
                assertEquals(offset,assertThrows(CdPowerupArtRuntime.UnsupportedArt.class,()->kernel.service(before,raw)).pointerOffset());
                assertEquals(offset,assertThrows(CdPowerupArtRuntime.UnsupportedArt.class,()->kernel.prepareActiveCopy(cpu,gate.powerup())).pointerOffset());
            } else {
                var request=kernel.prepareActiveCopy(cpu,gate.powerup());var result=kernel.service(before,raw);
                assertArrayEquals(request.bytes().copy(),result.state().cpuBuffer().copy());
                assertArrayEquals(request.bytes().copy(),result.state().residentBank().copy());
                assertArrayEquals(request.bytes().copy(),result.dma().orElseThrow().bytes().copy());
            }
        }
    }
    @Test void inactiveLegacyServiceNeverQueriesSourcesAndPreservesBothConstructedWindows() {
        var kernel=CdPowerupArtRuntime.fromRawSources(slot->{throw new AssertionError("inactive source read");});
        var before=new CdPowerupArtRuntime.State(bytes(1152,1),bytes(1152,2));
        for(int raw=0;raw<128;raw++) {var result=kernel.service(before,raw);assertSame(before,result.state());assertEquals(raw,result.powerup());}
    }
    @Test void invalidCpuOrUnclearedSelectorsStopBeforeSources() {
        var kernel=CdPowerupArtRuntime.fromRawSources(slot->{throw new AssertionError("invalid source read");});
        assertThrows(NullPointerException.class,()->kernel.prepareActiveCopy(null,6));
        for(int size:new int[]{0,1024,1151,1153})assertThrows(IllegalArgumentException.class,
                ()->kernel.prepareActiveCopy(CdAssetBytes.copyOf(new byte[size]),6));
        for(int value:new int[]{-1,128,134,256})assertThrows(IllegalArgumentException.class,
                ()->kernel.prepareActiveCopy(CdAssetBytes.copyOf(new byte[1152]),value));
    }
    @Test void malformedOrThrowingSourcesCannotPublishAPlanAndOriginalFaultIdentityIsRetained() {
        var cpu=CdAssetBytes.copyOf(bytes(1152,8));var sentinel=new AssertionError("actual source failure");
        var kernel=CdPowerupArtRuntime.fromRawSources(slot->{throw sentinel;});
        assertSame(sentinel,assertThrows(AssertionError.class,()->kernel.prepareActiveCopy(cpu,6)));
        for(int size:new int[]{0,992,1023,1025,1152})assertThrows(IllegalArgumentException.class,
                ()->CdPowerupArtRuntime.fromRawSources(slot->CdAssetBytes.copyOf(new byte[size])).prepareActiveCopy(cpu,6));
        assertThrows(NullPointerException.class,()->CdPowerupArtRuntime.fromRawSources(slot->null).prepareActiveCopy(cpu,6));
        assertArrayEquals(bytes(1152,8),cpu.copy());
    }
    private static byte[] bytes(int size,int seed) {byte[] out=new byte[size];for(int i=0;i<size;i++)out[i]=(byte)(seed+i*37+(i>>>8));return out;}
}
