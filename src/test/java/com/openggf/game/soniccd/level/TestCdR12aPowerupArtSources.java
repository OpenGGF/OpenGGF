package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR12aPowerupArtSources.Source.*;
import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original-disc source values only. Kernel-state vectors below are explicitly constructed;
 * no actual CPU tail/current/World ingress, art publication or native execution is claimed. ALL JAVA UNRUN.
 * N1ecc5f3a Powerup157-186/Data(Act2)142-162. Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aPowerupArtSources {
    @Test void actualIndexPcOperandFivePointersAndOwnerCacheRetainOriginalIdentity() throws Exception {
        var r=retail();var o=r.assets().objects();var a=CdR12aPowerupArtSources.fromAssets(o);var hex=java.util.HexFormat.of();
        assertSame(a,o.powerupArtSources());assertSame(a,CdR12aPowerupArtSources.fromAssets(o));assertSame(o.identity(),a.identity());
        assertEquals(134,SHIELD_ART_INDEX.ordinal());assertEquals(20,a.index().length());assertSame(o.view(SHIELD_ART_INDEX),a.index());
        assertEquals("227b0038",hex.formatHex(r.original().readFile(0x6418,4)));assertEquals(0x6452,0x641A+(byte)0x38);
        assertEquals("002344e80023496800234de800233d3c0023397c",hex.formatHex(a.index().copy()));
        assertEquals("e5f83ef559b1e3eaa1e15d9c73a6d771b41634426b2dce4ecabf6a6ebe222834",CdR11aProfile.sha256(a.index().copy()));
        assertSame(a.copies(),a.copies());assertEquals(5,a.copies().size());assertThrows(UnsupportedOperationException.class,()->a.copies().clear());
        assertEquals(35,o.readerProfiles().size());assertEquals(19,o.plcLists().size());assertThrows(IllegalArgumentException.class,()->o.readerProfile(SHIELD_ART_INDEX));
    }

    @Test void allFiveRawCopiesMatchActualFiniteNativeSpansHashesAndRealAdjacentSegments() throws Exception {
        var r=retail();var o=r.assets().objects();var a=o.powerupArtSources();
        int[] starts={0x344E8,0x34968,0x34DE8,0x33D3C,0x3397C};var sources=List.of(SHIELD,INV_STARS,TIME_STARS,GAME_OVER,TIME_OVER);
        String[] hashes={"cbca98fc186fec63ea75c2c0dd8da06ec651c02c37232c15e38cecb749d5b7d0","706736889a2cced446522a69a86853de41d4dfc9204c83ab0a7d2690d3036fcc","2094d9156ba35fdb865feb01bee3e64b809ad0297e3d46d7defcf79d94fb5919","911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd","8830d700eed4f092a9f4329993caf68940ac65bae7656e54a823031711e371f3"};
        for(int slot=0;slot<5;slot++) {
            var c=a.descriptor(slot);assertSame(c,a.copies().get(slot));assertSame(c.bytes(),a.copy(slot));assertSame(a.copy(slot),a.copy(slot));
            assertEquals(slot,c.slot());assertEquals(sources.get(slot),c.source());assertEquals(starts[slot],c.consumedRange().fileOffset());assertEquals(1024,c.consumedRange().length());
            assertEquals(0x200000+starts[slot],a.index().unsignedLong(slot*4));assertEquals(hashes[slot],CdR11aProfile.sha256(a.copy(slot).copy()));
            assertArrayEquals(r.original().readFile(starts[slot],1024),a.copy(slot).copy());
        }
        assertEquals(List.of(new CdR12aPowerupArtSources.SourceSegment(TIME_STARS,0,992),new CdR12aPowerupArtSources.SourceSegment(DIAGONAL_SPRING,0,32)),a.descriptor(2).segments());
        assertEquals(List.of(new CdR12aPowerupArtSources.SourceSegment(TIME_OVER,0,960),new CdR12aPowerupArtSources.SourceSegment(GAME_OVER,0,64)),a.descriptor(4).segments());
        assertArrayEquals(o.view(ART_DIAGONAL_SPRING).slice(0,32).copy(),a.copy(2).slice(992,32).copy());
        assertArrayEquals(a.sourceView(GAME_OVER).slice(0,64).copy(),a.copy(4).slice(960,64).copy());
        assertEquals(992,a.sourceView(TIME_STARS).length());assertEquals(960,a.sourceView(TIME_OVER).length());
    }

    @Test void canonicalSourceViewsBorrowExistingCachesWithoutNewReaderPlcOrMutableAlias() throws Exception {
        var o=retail().assets().objects();var readers=o.readerProfiles();var lists=o.plcLists();var a=o.powerupArtSources();
        assertSame(o.view(ART_SHIELD),a.sourceView(SHIELD));assertSame(o.view(ART_INV_STARS),a.sourceView(INV_STARS));
        assertSame(o.view(ART_TIME_STARS),a.sourceView(TIME_STARS));assertSame(o.view(ART_DIAGONAL_SPRING),a.sourceView(DIAGONAL_SPRING));
        assertEquals(1024,a.sourceView(GAME_OVER).length());assertEquals(960,a.sourceView(TIME_OVER).length());
        for(var source:CdR12aPowerupArtSources.Source.values()) {
            var b=a.sourceView(source);var old=b.copy();var changed=b.copy();Arrays.fill(changed,(byte)0);
            assertSame(b,a.sourceView(source));assertArrayEquals(old,b.copy());assertThrows(IndexOutOfBoundsException.class,()->b.unsignedByte(b.length()));
        }
        assertSame(readers,o.readerProfiles());assertSame(lists,o.plcLists());assertEquals(35,readers.size());assertEquals(19,lists.size());
        for(var source:new CdR12aProfile.Asset[]{ART_SHIELD,ART_INV_STARS,ART_TIME_STARS,SHIELD_ART_INDEX}) {
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(source));assertTrue(lists.stream().flatMap(l->l.entries().stream()).noneMatch(e->e.source()==source));
        }
    }

    @Test void constructedFormatNegativesRejectPointerTagsOrderWidthsOverreadsAndNonadjacentSegments() throws Exception {
        var a=retail().assets().objects().powerupArtSources();CdR12aPowerupArtSources.requireIndex(a.index());
        for(int slot=0;slot<5;slot++) {
            byte[] tagged=a.index().copy();tagged[slot*4]^=(byte)0x80;assertThrows(IllegalArgumentException.class,()->CdR12aPowerupArtSources.requireIndex(CdAssetBytes.copyOf(tagged)));
            byte[] odd=a.index().copy();odd[slot*4+3]^=1;assertThrows(IllegalArgumentException.class,()->CdR12aPowerupArtSources.requireIndex(CdAssetBytes.copyOf(odd)));
        }
        byte[] swapped=a.index().copy();System.arraycopy(a.index().copy(),4,swapped,0,4);assertThrows(IllegalArgumentException.class,()->CdR12aPowerupArtSources.requireIndex(CdAssetBytes.copyOf(swapped)));
        assertThrows(IllegalArgumentException.class,()->CdR12aPowerupArtSources.requireIndex(CdAssetBytes.copyOf(new byte[19])));assertThrows(IllegalArgumentException.class,()->CdR12aPowerupArtSources.requireIndex(CdAssetBytes.copyOf(new byte[21])));
        assertThrows(NullPointerException.class,()->CdR12aPowerupArtSources.requireIndex(null));assertThrows(IndexOutOfBoundsException.class,()->a.copy(-1));assertThrows(IndexOutOfBoundsException.class,()->a.copy(5));
        assertThrows(IndexOutOfBoundsException.class,()->a.index().unsignedLong(17));assertThrows(NullPointerException.class,()->a.sourceView(null));
        assertThrows(IllegalArgumentException.class,()->new CdR12aPowerupArtSources.SourceSegment(TIME_STARS,0,1024));assertThrows(IllegalArgumentException.class,()->new CdR12aPowerupArtSources.SourceSegment(SHIELD,-1,1));
        var c=a.descriptor(0);assertThrows(IllegalArgumentException.class,()->new CdR12aPowerupArtSources.RawCopy(0,SHIELD,c.consumedRange(),CdAssetBytes.copyOf(new byte[1023]),c.segments()));
        assertThrows(IllegalArgumentException.class,()->new CdR12aPowerupArtSources.RawCopy(0,INV_STARS,a.descriptor(1).consumedRange(),a.copy(1),a.descriptor(1).segments()));
        assertThrows(IllegalArgumentException.class,()->new CdR12aPowerupArtSources.RawCopy(2,TIME_STARS,a.descriptor(2).consumedRange(),a.copy(2),List.of(new CdR12aPowerupArtSources.SourceSegment(TIME_STARS,0,992),new CdR12aPowerupArtSources.SourceSegment(DIAGONAL_SPRING,1,31))));
    }

    @Test void foreignIdentityAndChangedOriginalCannotReplaceActualCachedSourceOwner() throws Exception {
        var r=retail();var o=r.assets().objects();var a=o.powerupArtSources();assertThrows(NullPointerException.class,()->CdR12aPowerupArtSources.fromAssets(null));
        for(int kind=0;kind<4;kind++) {
            var foreign=org.mockito.Mockito.mock(CdR12aObjectAssets.class);var id=org.mockito.Mockito.mock(CdR12aTerrainAssets.Identity.class);org.mockito.Mockito.when(foreign.identity()).thenReturn(id);
            org.mockito.Mockito.when(id.variant()).thenReturn(kind==0?CdReleaseVariant.EUROPE:CdReleaseVariant.USA);org.mockito.Mockito.when(id.file()).thenReturn(kind==1?CdNativeFile.R11A:CdNativeFile.R12A);
            org.mockito.Mockito.when(id.domain()).thenReturn(kind==2?CdMmdImage.Domain.RELOCATED_COPY:CdMmdImage.Domain.ORIGINAL_WORD_RAM);org.mockito.Mockito.when(id.stage()).thenReturn(kind==3?CdStagePayload.R11A.stage():CdStagePayload.R12A.stage());
            assertThrows(IllegalArgumentException.class,()->CdR12aPowerupArtSources.fromAssets(foreign));org.mockito.Mockito.verify(foreign,org.mockito.Mockito.never()).powerupArtSources();
        }
        byte[] changed=r.original().readFile(0,262144);changed[0x6452]^=1;
        assertThrows(IllegalArgumentException.class,()->CdR12aPowerupArtSources.fromOwner(o,CdMmdImage.parse(changed)));
        assertSame(a,o.powerupArtSources());assertSame(a.identity(),o.identity());
    }

    @Test void actualSourcesUseSharedKernelWithLabelledDirtyTailValuesWithoutCreatingCurrentKnowledge() throws Exception {
        var a=retail().assets().objects().powerupArtSources();var kernel=CdPowerupArtRuntime.fromRawSources(a);
        byte[] cpu=new byte[1152],resident=new byte[1152];for(int i=0;i<1152;i++){cpu[i]=(byte)(7+11*i);resident[i]=(byte)(5+3*i);}
        // Constructed pure component input only. These bytes are not an admitted runtime CPU/current image.
        var state=new CdPowerupArtRuntime.State(cpu,resident);
        for(int slot=0;slot<5;slot++) {
            var out=kernel.service(state,0x82+2*slot);byte[] expected=cpu.clone();System.arraycopy(a.copy(slot).copy(),0,expected,0,1024);
            assertEquals(2+2*slot,out.powerup());assertArrayEquals(expected,out.state().cpuBuffer().copy());assertArrayEquals(expected,out.state().residentBank().copy());
            assertArrayEquals(expected,out.dma().orElseThrow().bytes().copy());assertEquals(0xA880,out.dma().orElseThrow().vramByteAddress());
            assertArrayEquals(Arrays.copyOfRange(cpu,1024,1152),out.state().cpuBuffer().slice(1024,128).copy());
            assertArrayEquals(cpu,state.cpuBuffer().copy());assertArrayEquals(resident,state.residentBank().copy());
            var held=kernel.service(out.state(),out.powerup());assertSame(out.state(),held.state());assertTrue(held.dma().isEmpty());
        }
    }

    private record Retail(CdR12aTerrainAssets assets,CdMmdImage original) { }
    private Retail retained;
    private Retail retail() throws Exception {
        if(retained!=null)return retained;
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var entries=Files.list(Path.of(root).resolve("USA"))){var cues=entries.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=new Retail(CdR12aTerrainAssets.fromPrograms(programs),programs.mmd(CdNativeFile.R12A));}
        return retained;
    }
}
