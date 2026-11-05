package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aProfile;
import com.openggf.game.soniccd.level.CdShieldArt;
import com.openggf.game.soniccd.media.CdMmdImage;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static org.junit.jupiter.api.Assertions.*;

/** ROM-free constructed pure-value vectors, not source authentication or World/current ingress.
 * N1ecc5f3a LoadShieldArt157-186; old algorithm/service is reused, no new lifecycle is tested. ALL JAVA UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
class TestCdPowerupArtRuntimeRawSources {
    @Test void allClearedBytesKeepExactInputAndDoNotReadTheSupplier() {
        var count=new AtomicInteger();var kernel=CdPowerupArtRuntime.fromRawSources(slot->{count.incrementAndGet();throw new AssertionError("idle queried source");});
        var state=state();for(int raw=0;raw<128;raw++){var out=kernel.service(state,raw);assertSame(state,out.state());assertEquals(raw,out.powerup());assertTrue(out.dma().isEmpty());}assertEquals(0,count.get());
    }

    @Test void fiveActiveSelectorsReadExactlyOneFiniteCopyAndPreserveTheEntireDirtyCpuTail() {
        var slots=new ArrayList<Integer>();var kernel=CdPowerupArtRuntime.fromRawSources(slot->{slots.add(slot);return source(slot);});assertTrue(slots.isEmpty());
        var state=state();var cpu=state.cpuBuffer().copy();var resident=state.residentBank().copy();
        for(int slot=0;slot<5;slot++) {
            var out=kernel.service(state,0x82+2*slot);assertEquals(slot+1,slots.size());assertEquals(slot,slots.getLast());byte[] expected=cpu.clone();System.arraycopy(source(slot).copy(),0,expected,0,1024);
            assertEquals(2+2*slot,out.powerup());assertArrayEquals(expected,out.state().cpuBuffer().copy());assertArrayEquals(expected,out.state().residentBank().copy());
            assertSame(out.state().cpuBuffer(),out.state().residentBank());assertSame(out.state().cpuBuffer(),out.dma().orElseThrow().bytes());assertEquals(0xA880,out.dma().orElseThrow().vramByteAddress());
            assertArrayEquals(Arrays.copyOfRange(cpu,1024,1152),out.dma().orElseThrow().bytes().slice(1024,128).copy());assertArrayEquals(cpu,state.cpuBuffer().copy());assertArrayEquals(resident,state.residentBank().copy());
        }
        assertEquals(List.of(0,1,2,3,4),slots);
    }

    @Test void everyUnboundActiveSelectorStopsBeforeTheRawSupplierAndLeavesInputUntouched() {
        var count=new AtomicInteger();var kernel=CdPowerupArtRuntime.fromRawSources(slot->{count.incrementAndGet();return source(slot);});var state=state();var cpu=state.cpuBuffer().copy();var resident=state.residentBank().copy();
        for(int raw=128;raw<256;raw++) {
            if(raw==0x82 || raw==0x84 || raw==0x86 || raw==0x88 || raw==0x8A)continue;int selected=raw;
            var error=assertThrows(CdPowerupArtRuntime.UnsupportedArt.class,()->kernel.service(state,selected));assertEquals((((raw&127)-2)&255)*2,error.pointerOffset());
        }
        assertEquals(0,count.get());assertArrayEquals(cpu,state.cpuBuffer().copy());assertArrayEquals(resident,state.residentBank().copy());
    }

    @Test void replayIsPureAndClearedRequestCannotQueryOrPublishAgain() {
        var count=new AtomicInteger();var kernel=CdPowerupArtRuntime.fromRawSources(slot->{count.incrementAndGet();return source(slot);});var state=state();
        var first=kernel.service(state,0x86);var replay=kernel.service(state,0x86);assertEquals(first.state(),replay.state());assertEquals(2,count.get());
        var held=kernel.service(first.state(),first.powerup());assertSame(first.state(),held.state());assertTrue(held.dma().isEmpty());assertEquals(2,count.get());
    }

    @Test void sourceFailuresNullsAndWrongWidthsNeverProduceAResultOrAlterInput() {
        var state=state();var cpu=state.cpuBuffer().copy();var resident=state.residentBank().copy();
        for(int width:new int[]{0,1023,1025,1152}) {
            var count=new AtomicInteger();var kernel=CdPowerupArtRuntime.fromRawSources(slot->{count.incrementAndGet();return CdAssetBytes.copyOf(new byte[width]);});
            assertThrows(IllegalArgumentException.class,()->kernel.service(state,0x82));assertEquals(1,count.get());
        }
        var sentinel=new IllegalStateException("actual-raw-source-fault");var count=new AtomicInteger();var fault=CdPowerupArtRuntime.fromRawSources(slot->{count.incrementAndGet();throw sentinel;});
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->fault.service(state,0x82)));assertEquals(1,count.get());assertEquals(0,sentinel.getSuppressed().length);
        assertThrows(NullPointerException.class,()->CdPowerupArtRuntime.fromRawSources(slot->null).service(state,0x82));assertArrayEquals(cpu,state.cpuBuffer().copy());assertArrayEquals(resident,state.residentBank().copy());
    }

    @Test void missingOwnersWidthsAndNonByteRequestsRetainLegacyConstructorContract() {
        assertThrows(NullPointerException.class,()->CdPowerupArtRuntime.fromRawSources(null));
        assertThrows(NullPointerException.class,()->new CdPowerupArtRuntime(null)); // Existing public CdShieldArt constructor; no public overload.
        var kernel=CdPowerupArtRuntime.fromRawSources(TestCdPowerupArtRuntimeRawSources::source);var state=state();
        assertThrows(NullPointerException.class,()->kernel.service(null,0));assertThrows(IllegalArgumentException.class,()->kernel.service(state,-1));assertThrows(IllegalArgumentException.class,()->kernel.service(state,256));
        assertThrows(IllegalArgumentException.class,()->new CdPowerupArtRuntime.State(new byte[1151],new byte[1152]));assertThrows(IllegalArgumentException.class,()->new CdPowerupArtRuntime.State(new byte[1152],new byte[1153]));
    }

    @Test void oldCdShieldArtPathAndNamedRawSeamHaveIdenticalServiceResultsAndStopsForAllBytes() throws Exception {
        var oldSources=legacyConstructedSources();var old=new CdPowerupArtRuntime(oldSources);var raw=CdPowerupArtRuntime.fromRawSources(slot->oldSources.copy(slot).bytes());var state=state();
        for(int value=0;value<256;value++) {
            int request=value;if((value&128)!=0 && value!=0x82 && value!=0x84 && value!=0x86 && value!=0x88 && value!=0x8A) {
                var a=assertThrows(CdPowerupArtRuntime.UnsupportedArt.class,()->old.service(state,request));var b=assertThrows(CdPowerupArtRuntime.UnsupportedArt.class,()->raw.service(state,request));assertEquals(a.pointerOffset(),b.pointerOffset());
            } else {
                var a=old.service(state,value);var b=raw.service(state,value);assertEquals(a.powerup(),b.powerup());assertEquals(a.state(),b.state());assertEquals(a.dma().isPresent(),b.dma().isPresent());
                if(a.dma().isPresent())assertArrayEquals(a.dma().orElseThrow().bytes().copy(),b.dma().orElseThrow().bytes().copy());
            }
        }
    }

    private static CdAssetBytes source(int slot){byte[] b=new byte[1024];for(int i=0;i<b.length;i++)b[i]=(byte)(slot*17+i);return CdAssetBytes.copyOf(b);}
    private static CdPowerupArtRuntime.State state(){byte[] cpu=new byte[1152],resident=new byte[1152];for(int i=0;i<1152;i++){cpu[i]=(byte)(11*i+7);resident[i]=(byte)(3*i+5);}return new CdPowerupArtRuntime.State(cpu,resident);}
    private static CdShieldArt legacyConstructedSources() throws Exception {
        // Same labelled component-only native-pointer fixture as the old tests; no authenticated media admission.
        var profile=CdR11aProfile.forStage(CdReleaseVariant.USA,CdStageKey.parse("R11A"));byte[] file=new byte[0x40000];
        CdR11aAsset[] order={ART_SHIELD,ART_INV_STARS,ART_TIME_STARS,ART_GAME_OVER,ART_TIME_OVER};
        for(int slot=0;slot<order.length;slot++){var range=profile.range(order[slot]);for(int i=0;i<range.length();i++)file[range.fileOffset()+i]=(byte)(17*slot+i);}
        var data=ByteBuffer.wrap(file);int table=profile.range(SHIELD_ART_INDEX).fileOffset();for(int slot=0;slot<order.length;slot++)data.putInt(table+4*slot,profile.range(order[slot]).nativeAddress());
        return CdShieldArt.decode(profile,CdMmdImage.parse(file));
    }
}
