package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static com.openggf.game.soniccd.level.CdStageTerrain.*;
import static com.openggf.game.soniccd.level.CdStageTerrain.Plane.*;

/** Original-media immutable terrain DATA only, not World/actor/staging-bank admission. */
class TestCdR5TerrainData {
    private static final CdR5DataLayout.Programme A=CdR5DataLayout.Programme.R51A,B=CdR5DataLayout.Programme.R51B;
    private static CdProgrammeReader reader(CdR5DataLayout.Programme p)throws Exception {return TestCdR5DataLayout.actual().readers().get(p);}
    private static CdProgramAssets source()throws Exception {return CdProgramAssets.open(TestCdR5DataLayout.actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty());}
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,names={"R51A","R51B"})
    void authenticInitialTerrainUsesActualReadViewsAndKnownDecodedProducts(CdR5DataLayout.Programme p)throws Exception {
        var r=reader(p);var t=r.terrain();
        assertEquals(44,t.layout().columns(FOREGROUND));assertEquals(5,t.layout().rows(FOREGROUND));
        assertEquals(p==A?780:836,t.blockCount());assertEquals(p==A?716:690,t.patternCount());assertEquals(p==A?112:108,t.chunkCount());
        assertEquals(r.view(p.range("StageMaps0Read")).unsignedByte(178),t.layout().cell(FOREGROUND,0,4).rawByte());assertSame(t,r.terrain());
        assertEquals(LayoutEncoding.EXACT_READ_VIEW,t.profile().layoutEncoding());assertEquals(ReferencePolicy.SELECTED_KNOWN_SOURCE,t.profile().referencePolicy());
        assertArrayEquals(r.decoded(p.range("StageBlocks")).bytes().copy(),t.blocks().copy());assertArrayEquals(r.decoded(p.range("StageGfx")).bytes().copy(),t.patterns().copy());
        for(var role:List.of(CdProgrammeReader.Role.FG,CdProgrammeReader.Role.BG,CdProgrammeReader.Role.CHUNKS))assertSame(r.role(role),r.role(role));
        assertThrows(UnsupportedOperationException.class,t::shape);assertThrows(UnsupportedOperationException.class,r::terrainShape);
        assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);assertThrows(UnsupportedOperationException.class,()->CdStagePlayerSource.fromProgramme(r));
        assertThrows(UnsupportedOperationException.class,()->r.role(CdProgrammeReader.Role.ANI));
    }
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,names={"R51A","R51B"})
    void publicImmutableIngressQueriesActualRawWordsCollisionSamplesAndPatternNibbles(CdR5DataLayout.Programme p)throws Exception {
        try(var actual=source()) {
            var t=CdStageTerrain.fromProgramme(actual,p.payload().stage());var again=CdStageTerrain.fromProgramme(actual,p.payload().stage());
            assertNotSame(t,again);assertArrayEquals(t.blocks().copy(),again.blocks().copy());assertEquals(t.profile(),again.profile());
            assertEquals(1,t.layout().cell(FOREGROUND,0,0).chunkId());var block=t.chunkWord(1,0,0);
            assertEquals(p==A?16492:16972,block.rawWord());assertEquals(p==A?108:588,block.renderBlockIndex());
            assertEquals(81,t.collision().idForBlock(block.collisionBlockIndex()));assertEquals(255,t.collision().angle(81));assertEquals(16,t.collision().height(81,0));
            var pattern=t.blockWord(block.renderBlockIndex(),0,0);assertEquals(p==A?16423:49320,pattern.rawWord());
            int expected=t.patterns().unsignedByte(pattern.patternIndex()*32)>>>4;assertEquals(expected,t.pixel(pattern.patternIndex(),0,0));
            assertEquals(0,t.layout().nativeCell(FOREGROUND,63,15).rawByte());
        }
    }
    @Test void nativeForegroundOverreadIsRealAdjacentDataAndOddBackgroundViewIsNotPadded()throws Exception {
        var r=reader(A);var t=r.terrain();assertEquals(178,r.view(A.range("StageMapFg")).length());assertEquals(222,r.role(CdProgrammeReader.Role.FG).length());
        for(int x=0;x<44;x++)assertEquals(r.role(CdProgrammeReader.Role.FG).unsignedByte(178+x),t.layout().cell(FOREGROUND,x,4).rawByte());
        assertEquals(54,t.layout().cell(FOREGROUND,0,4).rawByte());assertEquals(2,t.layout().cell(FOREGROUND,1,4).rawByte());
        assertEquals(168,r.view(A.range("StageMapBg")).length());assertEquals(167,r.role(CdProgrammeReader.Role.BG).length());
        assertEquals(55,t.layout().columns(BACKGROUND));assertEquals(3,t.layout().rows(BACKGROUND));
    }
    @Test void pastCollisionHullAndMissingPatternReadsHavePreciseSourceOwners()throws Exception {
        var r=reader(B);var t=r.terrain();assertEquals(800,r.view(B.range("StageCollision")).length());assertEquals(830,t.collision().ids().length());
        for(int i=800;i<830;i++)assertEquals(r.view(B.range("Map0Collision")).unsignedByte(i),t.collision().idForBlock(i));
        assertEquals(0,t.collision().idForBlock(829));unavailable(ReadOwner.COLLISION_SOURCE,830,830,()->t.collision().idForBlock(830));
        var raw=t.blockWord(821,1,0);assertEquals(25266,raw.rawWord());assertEquals(690,raw.patternIndex());
        unavailable(ReadOwner.PATTERN_SOURCE,690,690,()->t.pixel(raw.patternIndex(),0,0));assertTrue(t.pixel(689,7,7)<16);
        unavailable(ReadOwner.BLOCK_STAGING,836,836,()->t.blockWord(836,0,0));
        unavailable(ReadOwner.CHUNK_SOURCE,109,108,()->t.chunkWord(109,0,0));
        var present=reader(A).terrain();unavailable(ReadOwner.COLLISION_SOURCE,800,800,()->present.collision().idForBlock(800));
    }
    private static void unavailable(ReadOwner owner,int index,int count,org.junit.jupiter.api.function.Executable read) {
        var e=assertThrows(UnavailableRead.class,read);assertEquals(owner,e.owner());assertEquals(index,e.index());assertEquals(count,e.availableCount());
    }
    private static CdStageTerrain constructed(Profile p,CdStageTerrain t,byte[] blocks,byte[] chunks,byte[] fg) {
        return CdStageTerrain.decodeData(p,Optional.empty(),blocks,t.patterns().copy(),chunks,fg,TestBytes.bg(t),t.collision().ids().copy(),t.collision().angles().copy(),t.collision().heights().copy(),t.collision().widths().copy());
    }
    // Constructed witnesses copy only the immutable decoded layout product, never a runtime bank/World.
    private static class TestBytes {
        static byte[] plane(CdStageTerrain t,Plane plane) {int w=t.layout().columns(plane),h=t.layout().rows(plane);byte[] a=new byte[2+w*h];a[0]=(byte)(w-1);a[1]=(byte)(h-1);for(int y=0;y<h;y++)for(int x=0;x<w;x++)a[2+y*w+x]=(byte)t.layout().cell(plane,x,y).rawByte();return a;}
        static byte[] fg(CdStageTerrain t){return plane(t,FOREGROUND);}static byte[] bg(CdStageTerrain t){return plane(t,BACKGROUND);}
    }
    private static Profile strict(Profile p) {return new Profile(p.blockBytes(),p.patternBytes(),p.chunkBytes(),p.foregroundColumns(),p.foregroundRows(),p.backgroundColumns(),p.backgroundRows(),p.collisionIndexBytes(),p.layoutEncoding(),ReferencePolicy.EAGER_VALIDATED);}
    @Test void selectedUnknownSubcellsRemainRawAndOldExhaustivePolicyStillRejectsThem()throws Exception {
        var t=reader(A).terrain();var chunks=t.chunks().copy();ByteBuffer.wrap(chunks).putShort(0,(short)1023);
        var c=constructed(t.profile(),t,t.blocks().copy(),chunks,TestBytes.fg(t));assertEquals(1023,c.chunkWord(1,0,0).rawWord());
        unavailable(ReadOwner.BLOCK_STAGING,1023,780,()->c.blockWord(c.chunkWord(1,0,0).renderBlockIndex(),0,0));
        assertEquals("Referenced chunk requires unloaded block/collision data",assertThrows(IllegalArgumentException.class,()->constructed(strict(t.profile()),t,t.blocks().copy(),chunks,TestBytes.fg(t))).getMessage());
        var blocks=t.blocks().copy();ByteBuffer.wrap(blocks).putShort(0,(short)0x7FF);var p=constructed(t.profile(),t,blocks,t.chunks().copy(),TestBytes.fg(t));
        assertEquals(0x7FF,p.blockWord(0,0,0).rawWord());unavailable(ReadOwner.PATTERN_SOURCE,2047,716,()->p.pixel(2047,0,0));
        assertThrows(IllegalArgumentException.class,()->constructed(strict(t.profile()),t,blocks,t.chunks().copy(),TestBytes.fg(t)));
    }
    @Test void structuralBoundsAndLegacyStrictProfilesCannotBeBypassed()throws Exception {
        var t=reader(A).terrain();var fg=TestBytes.fg(t);
        assertThrows(IndexOutOfBoundsException.class,()->t.blockWord(-1,0,0));
        assertThrows(IndexOutOfBoundsException.class,()->t.pixel(-1,0,0));
        assertThrows(IndexOutOfBoundsException.class,()->t.collision().idForBlock(-1));
        assertThrows(IndexOutOfBoundsException.class,()->t.chunkWord(0,0,0));
        assertThrows(IndexOutOfBoundsException.class,()->t.blockWord(0,2,0));
        assertThrows(IndexOutOfBoundsException.class,()->t.pixel(0,8,0));
        assertThrows(IllegalArgumentException.class,()->constructed(t.profile(),t,t.blocks().copy(),t.chunks().copy(),Arrays.copyOf(fg,178)));
        assertThrows(IllegalArgumentException.class,()->new Profile(7,32,512,1,1,1,1,1,LayoutEncoding.EXACT_READ_VIEW,ReferencePolicy.SELECTED_KNOWN_SOURCE));
        assertThrows(IllegalArgumentException.class,()->new Profile(8,32,512,65,1,1,1,1,LayoutEncoding.EXACT_READ_VIEW,ReferencePolicy.SELECTED_KNOWN_SOURCE));
        var legacy=CdStageTerrain.profileFor(Shape.CCZ1_PRESENT);var weak=new Profile(legacy.blockBytes(),legacy.patternBytes(),legacy.chunkBytes(),legacy.foregroundColumns(),legacy.foregroundRows(),legacy.backgroundColumns(),legacy.backgroundRows(),legacy.collisionIndexBytes(),legacy.layoutEncoding(),ReferencePolicy.SELECTED_KNOWN_SOURCE);
        assertEquals("Legacy terrain profile differs",assertThrows(IllegalArgumentException.class,()->CdStageTerrain.decodeData(weak,Optional.of(Shape.CCZ1_PRESENT),new byte[0],new byte[0],new byte[0],new byte[0],new byte[0],new byte[0],new byte[0],new byte[0],new byte[0])).getMessage());
    }
    @Test void failedDecodePreservesOriginalExceptionEarlierProductAndTerrainCacheRetry()throws Exception {
        try(var actual=source()) {
            var r=spy(CdProgrammeReader.open(actual,A.payload().stage()));var blocks=A.range("StageBlocks");var gfx=A.range("StageGfx");var fault=new IOException("constructed gfx read failure");
            doThrow(fault).when(r).decoded(gfx);clearInvocations(r);assertSame(fault,assertThrows(IOException.class,r::terrain));
            var before=r.decoded(blocks);doCallRealMethod().when(r).decoded(gfx);var t=r.terrain();assertSame(t,r.terrain());
            assertSame(before,r.decoded(blocks));assertArrayEquals(before.bytes().copy(),t.blocks().copy());
        }
    }
    @Test void terrainBorrowsOnlySelectedInitialRolesAndUnsupportedProfilesStopBeforeDecode()throws Exception {
        for(var p:CdR5DataLayout.Programme.values()) {
            // A fresh owner prevents an existing terrain cache from masking the read sequence.
            try(var actual=source()) {var r=spy(CdProgrammeReader.open(actual,p.payload().stage()));r.terrain();
                for(var role:CdProgrammeReader.Role.values())if(!Set.of(CdProgrammeReader.Role.CHUNKS,CdProgrammeReader.Role.FG,CdProgrammeReader.Role.BG,CdProgrammeReader.Role.COLLISION,CdProgrammeReader.Role.ANGLE,CdProgrammeReader.Role.HEIGHT,CdProgrammeReader.Role.WIDTH).contains(role))verify(r,never()).role(role);
                verify(r,never()).view(p.range("StageMaps2Read"));}
        }
        // Purposeful migration: all ten R5 bindings now exist. Keep the missing-Terrain
        // control post-authentication, independent of another family's evolving admission.
        for(var p:CdR5DataLayout.Programme.values()) {
            var r=TestCdR5RemainingTerrainData.withoutTerrain(p);
            assertEquals(p.name()+" has no reviewed terrain DATA binding",assertThrows(UnsupportedOperationException.class,r::terrain).getMessage());
            verify(r,never()).decoded(any());verify(r,never()).role(any());
        }
    }

    @Test void act3ActualUnknownBlockPreimageRemainsAnUnadmittedBoundary()throws Exception {
        var p=CdR5DataLayout.Programme.R53C;var r=reader(p);var fg=r.view(p.range("StageMaps0Read"));assertEquals(7,fg.unsignedByte(2+16+2));
        var word=new BlockReference(r.view(p.range("StageChunks")).unsignedWord(6*512+15*32));assertEquals(1019,word.renderBlockIndex());
        assertEquals(760,r.decoded(p.range("StageBlocks")).bytes().length()/8);var t=r.terrain();assertEquals(1019,t.chunkWord(7,0,15).rawWord());
        unavailable(ReadOwner.BLOCK_STAGING,1019,760,()->t.blockWord(1019,0,0));
        unavailable(ReadOwner.COLLISION_SOURCE,1019,800,()->t.collision().idForBlock(1019));
    }
    @Test void publicIngressRejectsForeignOriginalImagesAndCannotMintAPlayerSource()throws Exception {
        try(var actual=source()) {
            var wrong=mock(CdProgramAssets.class,delegatesTo(actual));doReturn(actual.mmd(CdNativeFile.R51B)).when(wrong).mmd(CdNativeFile.R51A);
            assertThrows(IOException.class,()->CdStageTerrain.fromProgramme(wrong,A.payload().stage()));
            assertThrows(NullPointerException.class,()->CdStageTerrain.fromProgramme(null,A.payload().stage()));
        }
        for(var p:List.of(A,B))assertThrows(UnsupportedOperationException.class,()->CdStagePlayerSource.fromProgramme(reader(p)));
    }
}
