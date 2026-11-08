package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Genuine original media/common reader contracts. Fault/unsupported-selection controls are explicit. */
class TestCdR8SonicVisualData {
    @ParameterizedTest @EnumSource(CdR8DataLayout.Programme.class)
    void original188VisualProductUsesActualCommonReader(CdR8DataLayout.Programme p)throws Exception {
        var reader=TestCdR8DataLayout.reader(p);
        var visual=assertDoesNotThrow(reader::sonic,"Authenticated original R8 visual DATA must use the common decoder");
        assertSame(visual,reader.sonic());assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_188_COMPACT_ART,visual.sourceFormat());
        assertEquals(188,visual.mappingFrames().size());assertEquals(188,visual.dplcFrames().size());
        assertEquals(2639,visual.mappings().length());assertEquals(1454,visual.dplcs().length());
        assertEquals(59232,visual.rawArt().length());assertEquals(59232,visual.artReadView().length());
        assertArrayEquals(reader.view(p.range("PlayerSprites")).copy(),visual.mappings().copy());
        assertArrayEquals(reader.view(p.range("PlayerGfxScript")).copy(),visual.dplcs().copy());
        assertArrayEquals(reader.view(p.range("PlayerGfx")).copy(),visual.rawArt().copy());
        assertThrows(IllegalStateException.class,visual::alignmentByte);
        var signatures=new ArrayList<String>();int maps=0,art=0,mapEnd=0,dplcEnd=0,artEnd=0,maxOutput=0;
        for(int f=0;f<188;f++) {
            var map=visual.mappingFrames().get(f);var dplc=visual.dplcFrames().get(f);
            assertEquals((short)visual.mappings().unsignedWord(f*2),map.relativeOffset());
            assertEquals((short)visual.dplcs().unsignedWord(f*2),dplc.relativeOffset());
            assertEquals(0,map.relativeOffset()&1);assertEquals(0,dplc.relativeOffset()&1);
            assertEquals(1+5*map.pieceCount(),map.bytes().length());assertEquals(2+2*dplc.pieces().size(),dplc.bytes().length());
            assertArrayEquals(visual.mappings().slice(map.relativeOffset(),map.bytes().length()).copy(),map.bytes().copy());
            assertArrayEquals(visual.dplcs().slice(dplc.relativeOffset(),dplc.bytes().length()).copy(),dplc.bytes().copy());
            int sum=0;StringBuilder line=new StringBuilder().append(f).append(' ').append(map.relativeOffset()).append(' ').append(map.pieceCount()).append(' ').append(hash(map.bytes())).append(' ').append(dplc.relativeOffset()).append(' ').append(dplc.pieces().size()).append(' ').append(hash(dplc.bytes())).append(' ').append(dplc.outputBytes());
            for(int i=0;i<dplc.pieces().size();i++) {
                var piece=dplc.pieces().get(i);var bytes=visual.artPiece(f,i);assertEquals(piece.byteLength(),bytes.length());
                assertArrayEquals(TestCdR8DataLayout.actual().images().get(p).readFile(p.range("PlayerGfx").fileOffset()+piece.artByteOffset(),piece.byteLength()),bytes.copy());
                assertEquals(((piece.rawWord()>>>12)+1)*32,piece.byteLength());assertEquals((piece.rawWord()&4095)*32,piece.artByteOffset());
                line.append(' ').append(piece.artByteOffset()).append(':').append(piece.byteLength()).append(':').append(hash(bytes));
                sum+=piece.byteLength();artEnd=Math.max(artEnd,piece.artByteOffset()+piece.byteLength());
            }
            assertEquals(sum,dplc.outputBytes());assertTrue(sum<=768);
            for(int i=0;i<map.pieceCount();i++) {
                int at=1+i*5;int size=map.bytes().unsignedByte(at+1);assertEquals(0,size&~15);
                int end=(map.bytes().unsignedWord(at+2)&2047)+((size>>>2)+1)*((size&3)+1);
                assertTrue(end<=dplc.outputBytes()/32,"Every selected mapping tile has this frame's real DPLC bytes");
            }
            signatures.add(line.toString());maps+=map.pieceCount();art+=dplc.pieces().size();mapEnd=Math.max(mapEnd,map.relativeOffset()+map.bytes().length());dplcEnd=Math.max(dplcEnd,dplc.relativeOffset()+dplc.bytes().length());maxOutput=Math.max(maxOutput,sum);
        }
        assertEquals(424,maps);assertEquals(424,art);assertEquals(2639,mapEnd);assertEquals(1454,dplcEnd);assertEquals(59232,artEnd);assertEquals(736,maxOutput);
        assertEquals("e59d10080b501a512a805996f738515516ab233a92a6605b88b9f6758e8957b9",CdR11aProfile.sha256((String.join("\n",signatures)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(59232,visual.dplcFrames().get(184).pieces().get(1).artByteOffset()+visual.dplcFrames().get(184).pieces().get(1).byteLength());
        assertThrows(UnsupportedOperationException.class,()->visual.mappingFrames().clear());assertThrows(UnsupportedOperationException.class,()->visual.dplcFrames().get(184).pieces().clear());
    }
    @ParameterizedTest @EnumSource(CdR8DataLayout.Programme.class)
    void latePointersPiecesAndExactReadViewsRejectMalformedOrForeignFormats(CdR8DataLayout.Programme p)throws Exception {
        var v=TestCdR8DataLayout.reader(p).sonic();byte[] m=v.mappings().copy(),d=v.dplcs().copy(),a=v.rawArt().copy();
        assertThrows(IllegalArgumentException.class,()->decode(Arrays.copyOf(m,2638),d,a));
        assertThrows(IllegalArgumentException.class,()->decode(m,Arrays.copyOf(d,1453),a));
        assertThrows(IllegalArgumentException.class,()->decode(m,d,Arrays.copyOf(a,59231)));
        assertThrows(IllegalArgumentException.class,()->decode(m,d,Arrays.copyOf(a,59233)));
        for(var format:List.of(CdSonicVisualAssets.SourceFormat.FRAMES_167,CdSonicVisualAssets.SourceFormat.FRAMES_188,CdSonicVisualAssets.SourceFormat.FRAMES_198_WITH_EVEN_BYTE))assertThrows(IllegalArgumentException.class,()->CdSonicVisualAssets.decode(format,m,d,a));
        var bad=m.clone();word(bad,187*2,0x8000);byte[] mapPointer=bad;assertThrows(IllegalArgumentException.class,()->decode(mapPointer,d,a));
        bad=d.clone();word(bad,184*2,1449);byte[] dplcPointer=bad;assertThrows(IllegalArgumentException.class,()->decode(m,dplcPointer,a));
        bad=d.clone();word(bad,1448,128);byte[] count=bad;assertThrows(IllegalArgumentException.class,()->decode(m,count,a));
        bad=d.clone();word(bad,1452,0x073B);byte[] tile=bad;assertThrows(IllegalArgumentException.class,()->decode(m,tile,a));
        bad=m.clone();word(bad,2618+3,2047);byte[] mapTile=bad;assertThrows(IllegalArgumentException.class,()->decode(mapTile,d,a));
        bad=m.clone();bad[2618+2]=16;byte[] size=bad;assertThrows(IllegalArgumentException.class,()->decode(size,d,a));
        // Native frame184 has15 selected tiles; starting its12-tile piece at12 requires retained, unqualified staging.
        bad=m.clone();word(bad,2606+3,12);byte[] retained=bad;
        assertEquals("Sonic mapping requires unqualified retained staging tiles",assertThrows(UnsupportedOperationException.class,()->decode(retained,d,a)).getMessage());
        var detached=decode(m,d,a);Arrays.fill(m,(byte)0);Arrays.fill(d,(byte)0);Arrays.fill(a,(byte)0);
        assertArrayEquals(v.mappings().copy(),detached.mappings().copy());assertArrayEquals(v.rawArt().copy(),detached.rawArt().copy());
    }
    @Test void independentActualOwnersRetainIndependentVisualCaches()throws Exception {
        try(var source=CdProgramAssets.open(TestCdR8DataLayout.actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            for(var p:CdR8DataLayout.Programme.values()) {
                var first=TestCdR8DataLayout.reader(p).sonic();var second=CdProgrammeReader.open(source,p.payload().stage()).sonic();
                assertNotSame(first,second);assertNotSame(first.mappings(),second.mappings());assertArrayEquals(first.mappings().copy(),second.mappings().copy());assertArrayEquals(first.artReadView().copy(),second.artReadView().copy());
            }
        }
    }
    /** Conservative unsupported-selection fault on genuine authenticated bytes; no production definition ingress. */
    @Test void authenticDataWithUnsupportedSelectedFormatStillRejectsOnlyAtSonicAccess()throws Exception {
        var p=CdR8DataLayout.Programme.R83D;var original=TestCdR8DataLayout.reader(p);var definition=spy(original.definition());
        doReturn(new CdProgrammeDefinition.UnsupportedVisual("Unqualified selected visual format control")).when(definition).visual();
        var constructor=CdProgrammeReader.class.getDeclaredConstructor(CdProgrammeDefinition.class,CdMmdImage.class,String.class);constructor.setAccessible(true);
        var reader=constructor.newInstance(definition,TestCdR8DataLayout.actual().images().get(p),original.discSha());
        assertEquals(59232,reader.view(p.range("PlayerGfx")).length());assertTrue(reader.decoded(p.range("StageGfx")).bytes().length()>0);
        assertEquals("Unqualified selected visual format control",assertThrows(UnsupportedOperationException.class,reader::sonic).getMessage());
        assertEquals("Unqualified selected visual format control",assertThrows(UnsupportedOperationException.class,reader::sonic).getMessage());
        assertEquals(1454,reader.view(p.range("PlayerGfxScript")).length());
    }
    /** Post-authentication raw-span fault; an original full image does not authorize substituted mapping bytes. */
    @Test void wrongOrTruncatedSelectedMappingSpanNeverAdoptsVisualData()throws Exception {
        var p=CdR8DataLayout.Programme.R81A;var key=p.range("PlayerSprites");
        try(var actual=CdProgramAssets.open(TestCdR8DataLayout.actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            var image=spy(actual.mmd(p.file()));var source=mock(CdProgramAssets.class,delegatesTo(actual));doReturn(image).when(source).mmd(p.file());
            var raw=image.readFile(key.fileOffset(),key.length());var changed=raw.clone();changed[0]^=1;
            for(var bad:List.of(changed,Arrays.copyOf(raw,raw.length-1))) {
                doReturn(bad).when(image).readFile(key.fileOffset(),key.length());assertThrows(IOException.class,()->CdProgrammeReader.open(source,p.payload().stage()));
            }
            doReturn(raw).when(image).readFile(key.fileOffset(),key.length());assertEquals(188,CdProgrammeReader.open(source,p.payload().stage()).sonic().mappingFrames().size());
        }
    }
    @Test void actualLegacy167AndTt188FormatsKeepTheirContractsAndCannotSelectCompactArt()throws Exception {
        assertEquals(0,CdSonicVisualAssets.SourceFormat.FRAMES_198_WITH_EVEN_BYTE.ordinal());assertEquals(1,CdSonicVisualAssets.SourceFormat.FRAMES_167.ordinal());assertEquals(2,CdSonicVisualAssets.SourceFormat.FRAMES_188.ordinal());
        try(var actual=CdProgramAssets.open(TestCdR8DataLayout.actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            var small=CdProgrammeReader.open(actual,CdStagePayload.R32A.stage()).sonic();var tt=CdProgrammeReader.open(actual,CdStagePayload.R41A.stage()).sonic();
            assertEquals(167,small.mappingFrames().size());assertEquals(57120,small.rawArt().length());assertEquals(57120,small.artReadView().length());
            assertEquals(188,tt.mappingFrames().size());assertEquals(76928,tt.rawArt().length());assertEquals(82944,tt.artReadView().length());
            for(var v:List.of(small,tt))assertThrows(IllegalArgumentException.class,()->decode(v.mappings().copy(),v.dplcs().copy(),v.artReadView().copy()));
        }
    }
    private static String hash(CdAssetBytes bytes) { return CdR11aProfile.sha256(bytes.copy()); }
    private static CdSonicVisualAssets decode(byte[] m,byte[] d,byte[] a) { return CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_188_COMPACT_ART,m,d,a); }
    private static void word(byte[] bytes,int at,int value) {bytes[at]=(byte)(value>>>8);bytes[at+1]=(byte)value;}
}
