package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.openggf.game.soniccd.level.CdStageTerrain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Original media/common consumer proofs. Constructed faults never authenticate a source. */
class TestCdR8TerrainData {
    @ParameterizedTest @EnumSource(value=CdR8DataLayout.Programme.class,names={"R81A","R81B"})
    void authenticInitialTerrainUsesTheSharedReaderAndOriginalKnownProducts(CdR8DataLayout.Programme p)throws Exception {
        var r=TestCdR8DataLayout.reader(p);var t=r.terrain();
        assertSame(t,r.terrain());assertEquals(32,t.layout().columns(Plane.FOREGROUND));assertEquals(8,t.layout().rows(Plane.FOREGROUND));
        assertEquals(24,t.layout().columns(Plane.BACKGROUND));assertEquals(3,t.layout().rows(Plane.BACKGROUND));
        assertEquals(p==CdR8DataLayout.Programme.R81A?484:480,t.blockCount());
        assertEquals(p==CdR8DataLayout.Programme.R81A?545:544,t.patternCount());assertEquals(111,t.chunkCount());
        assertEquals(LayoutEncoding.EXACT_READ_VIEW,t.profile().layoutEncoding());assertEquals(ReferencePolicy.SELECTED_KNOWN_SOURCE,t.profile().referencePolicy());
        assertEquals(600,t.collision().ids().length());assertEquals(256,t.collision().angles().length());
        assertEquals(4096,t.collision().heights().length());assertEquals(4096,t.collision().widths().length());
        assertArrayEquals(r.decoded(p.range("StageBlocks")).bytes().copy(),t.blocks().copy());
        assertArrayEquals(r.decoded(p.range("StageGfx")).bytes().copy(),t.patterns().copy());
        assertEquals(p==CdR8DataLayout.Programme.R81A?"bd5e1ab7cea1c662ce0fa730e8879d8e38d5d456f1647766025c3577ada9f348":"8e48c1a68d351560a8d628e65f7f26bb101bbd61c620863559e3eeac9ffe668b",hash(t.blocks()));
        assertEquals(p==CdR8DataLayout.Programme.R81A?"6cc3832af89291906e8d3b2774a66ba5e3936535534f893c0e42347f46381de1":"69c01965536e39d3d8133984dff391e09f199f8e8b8280113879082fa0ad9791",hash(t.patterns()));
        assertArrayEquals(r.view(p.range("StageChunks")).copy(),t.chunks().copy());
        assertThrows(UnsupportedOperationException.class,t::shape);assertThrows(UnsupportedOperationException.class,r::terrainShape);
        assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);assertEquals("R3 requires retained 167-frame visuals",assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromProgramme(r)).getMessage());
        assertEquals(188,r.sonic().mappingFrames().size());assertEquals(59232,r.sonic().rawArt().length());
    }
    @ParameterizedTest @EnumSource(value=CdR8DataLayout.Programme.class,names={"R81A","R81B"})
    void publicIngressAndQueriesUseOriginalWordsSamplesAndNibbleOrder(CdR8DataLayout.Programme p)throws Exception {
        try(var source=open()) {
            var t=CdStageTerrain.fromProgramme(source,p.payload().stage());var retained=TestCdR8DataLayout.reader(p).terrain();
            assertNotSame(retained,t);assertNotSame(retained.blocks(),t.blocks());assertArrayEquals(retained.blocks().copy(),t.blocks().copy());
            var image=TestCdR8DataLayout.actual().images().get(p);
            for(var pair:Map.of("StageCollision",t.collision().ids(),"StageCollisionAngles",t.collision().angles(),
                    "StageCollisionColumns",t.collision().heights(),"StageCollisionRows",t.collision().widths()).entrySet()) {
                var range=p.range(pair.getKey());assertArrayEquals(image.readFile(range.fileOffset(),range.length()),pair.getValue().copy());assertEquals(range.sha256(),hash(pair.getValue()));
            }
            assertEquals(25,t.layout().cell(Plane.FOREGROUND,23,0).rawByte());var ref=t.chunkWord(25,13,10);
            assertEquals(0x6004,ref.rawWord());assertEquals(4,ref.renderBlockIndex());assertEquals(4,ref.collisionBlockIndex());assertEquals(3,ref.solidity());
            assertEquals(64,t.collision().idForBlock(4));assertEquals(226,t.collision().angle(64));
            assertEquals(1,t.collision().height(64,0));assertEquals(1,t.collision().width(64,0));
            assertEquals(-15,t.collision().height(57,8));assertEquals(-14,t.collision().width(57,14));
            assertEquals(0,t.blockWord(4,0,0).rawWord());assertEquals(15,t.pixel(1,6,1));assertEquals(14,t.pixel(1,7,1));
            for(int chunk=1;chunk<=111;chunk++)for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
                int at=(chunk-1)*512+(y*16+x)*2;assertEquals(t.chunks().unsignedWord(at),t.chunkWord(chunk,x,y).rawWord());
            }
            for(int block=0;block<t.blockCount();block++)for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
                var word=t.blockWord(block,x,y);assertEquals(t.blocks().unsignedWord(block*8+(y*2+x)*2),word.rawWord());assertTrue(word.patternIndex()<t.patternCount());
            }
        }
    }
    @ParameterizedTest @EnumSource(value=CdR8DataLayout.Programme.class,names={"R81A","R81B"})
    void nativeDirectoryCopyClearAndSelectedOperandsAreExact(CdR8DataLayout.Programme p)throws Exception {
        var r=TestCdR8DataLayout.reader(p);var t=r.terrain();var image=TestCdR8DataLayout.actual().images().get(p);
        var directory=p.range("StageMaps");var raw=r.view(directory);assertEquals(72,raw.length());
        assertEquals(72,(short)raw.unsignedWord(0));assertEquals(330,(short)raw.unsignedWord(2));
        var expected=new byte[0x800];
        for(var plane:Plane.values()) {
            int slot=plane==Plane.FOREGROUND?0:1;var range=p.range(slot==0?"StageMapFg":"StageMapBg");var view=r.view(range);
            assertEquals(range.fileOffset(),directory.fileOffset()+(short)raw.unsignedWord(slot*2));
            int width=view.unsignedByte(0)+1,height=view.unsignedByte(1)+1;assertEquals(2+width*height,view.length());
            for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                int cell=view.unsignedByte(2+y*width+x);expected[(slot==0?0:0x40)+y*0x80+x]=(byte)cell;
                assertEquals(cell,t.layout().cell(plane,x,y).rawByte());
                int chunk=cell&127;
                if(chunk!=0)for(int cy=0;cy<16;cy++)for(int cx=0;cx<16;cx++) {
                    var ref=t.chunkWord(chunk,cx,cy);assertTrue(ref.renderBlockIndex()<t.blockCount());assertTrue(ref.collisionBlockIndex()<600);
                    t.blockWord(ref.renderBlockIndex(),0,0);t.collision().idForBlock(ref.collisionBlockIndex());
                }
            }
            assertEquals(0,t.layout().nativeCell(plane,63,15).rawByte());
            assertThrows(IndexOutOfBoundsException.class,()->t.layout().cell(plane,width,0));
        }
        assertArrayEquals(expected,t.layout().ram().copy());
        // Original full-image authentication plus literal selected LOAD/LEA/STORE facts.
        for(var op:r.definition().operands())assertEquals(op.hex(),HexFormat.of().formatHex(image.readFile(op.offset(),op.hex().length()/2)));
        int pointers=p==CdR8DataLayout.Programme.R81A?0x1A82:0x1A0A;
        assertEquals("7000103900ff1506e54821fb0006f7964e75",HexFormat.of().formatHex(image.readFile(pointers-18,18)));
        for(int i=0;i<8;i++)assertEquals(0x23844A,CdAssetBytes.copyOf(image.readFile(pointers+i*4,4)).unsignedLong(0));
        assertEquals(6,r.definition().operands().size()-p.operands().size());
    }
    @ParameterizedTest @EnumSource(value=CdR8DataLayout.Programme.class,names={"R81A","R81B"})
    void genuineDirtyWordsStayRawAndUnknownReadsNameTheirOwnerAndKnownCount(CdR8DataLayout.Programme p)throws Exception {
        var t=TestCdR8DataLayout.reader(p).terrain();var dirty=t.chunkWord(89,13,8);
        assertEquals(550,dirty.rawWord());assertEquals(550,dirty.renderBlockIndex());assertEquals(550,dirty.collisionBlockIndex());
        unavailable(ReadOwner.BLOCK_STAGING,550,t.blockCount(),()->t.blockWord(dirty.renderBlockIndex(),0,0));
        assertEquals(29,t.collision().idForBlock(dirty.collisionBlockIndex()));assertEquals(0,t.collision().idForBlock(599));
        unavailable(ReadOwner.BLOCK_STAGING,t.blockCount(),t.blockCount(),()->t.blockWord(t.blockCount(),0,0));
        unavailable(ReadOwner.PATTERN_SOURCE,t.patternCount(),t.patternCount(),()->t.pixel(t.patternCount(),0,0));
        unavailable(ReadOwner.CHUNK_SOURCE,112,111,()->t.chunkWord(112,0,0));
        unavailable(ReadOwner.COLLISION_SOURCE,600,600,()->t.collision().idForBlock(600));
        assertThrows(IndexOutOfBoundsException.class,()->t.chunkWord(0,0,0));assertThrows(IndexOutOfBoundsException.class,()->t.blockWord(-1,0,0));
        assertThrows(IndexOutOfBoundsException.class,()->t.pixel(-1,0,0));assertThrows(IndexOutOfBoundsException.class,()->t.collision().idForBlock(-1));
        assertThrows(IndexOutOfBoundsException.class,()->t.collision().height(256,0));assertThrows(IndexOutOfBoundsException.class,()->t.collision().width(0,16));
        assertThrows(IndexOutOfBoundsException.class,()->t.layout().nativeCell(Plane.FOREGROUND,64,0));
    }
    @Test void constructedUnknownReferencesPreserveMasksFlipsAndStrictAlternative()throws Exception {
        var r=TestCdR8DataLayout.reader(CdR8DataLayout.Programme.R81A);var t=r.terrain();
        var chunks=t.chunks().copy();word(chunks,0,0xFC01);var fg=r.role(CdProgrammeReader.Role.FG).copy();fg[2]=1;
        var selected=decode(t.profile(),Optional.empty(),r,t.blocks().copy(),t.patterns().copy(),chunks,fg);
        var ref=selected.chunkWord(1,0,0);assertEquals(0xFC01,ref.rawWord());assertEquals(1,ref.renderBlockIndex());assertEquals(1025,ref.collisionBlockIndex());
        assertTrue(ref.flipX());assertTrue(ref.flipY());assertEquals(3,ref.solidity());
        unavailable(ReadOwner.COLLISION_SOURCE,1025,600,()->selected.collision().idForBlock(ref.collisionBlockIndex()));
        assertThrows(IllegalArgumentException.class,()->decode(policy(t.profile(),ReferencePolicy.EAGER_VALIDATED),Optional.empty(),r,t.blocks().copy(),t.patterns().copy(),chunks,fg));
        var blocks=t.blocks().copy();word(blocks,0,0xFFFF);
        var unknown=decode(t.profile(),Optional.empty(),r,blocks,t.patterns().copy(),t.chunks().copy(),r.role(CdProgrammeReader.Role.FG).copy());
        var pattern=unknown.blockWord(0,0,0);assertEquals(0xFFFF,pattern.rawWord());assertEquals(2047,pattern.patternIndex());assertTrue(pattern.flipX());assertTrue(pattern.flipY());assertTrue(pattern.priority());assertEquals(3,pattern.paletteLine());
        unavailable(ReadOwner.PATTERN_SOURCE,2047,t.patternCount(),()->unknown.pixel(pattern.patternIndex(),0,0));
        assertThrows(IllegalArgumentException.class,()->decode(policy(t.profile(),ReferencePolicy.EAGER_VALIDATED),Optional.empty(),r,blocks,t.patterns().copy(),t.chunks().copy(),r.role(CdProgrammeReader.Role.FG).copy()));
    }
    @Test void malformedTruncatedAndLegacyMarkerControlsCannotWeakenAReadContract()throws Exception {
        var r=TestCdR8DataLayout.reader(CdR8DataLayout.Programme.R81A);var t=r.terrain();var fg=r.role(CdProgrammeReader.Role.FG).copy();
        assertThrows(IllegalArgumentException.class,()->decode(t.profile(),Optional.empty(),r,Arrays.copyOf(t.blocks().copy(),t.blocks().length()-1),t.patterns().copy(),t.chunks().copy(),fg));
        assertThrows(IllegalArgumentException.class,()->decode(t.profile(),Optional.empty(),r,t.blocks().copy(),Arrays.copyOf(t.patterns().copy(),t.patterns().length()-1),t.chunks().copy(),fg));
        assertThrows(IllegalArgumentException.class,()->decode(t.profile(),Optional.empty(),r,t.blocks().copy(),t.patterns().copy(),Arrays.copyOf(t.chunks().copy(),t.chunks().length()-1),fg));
        assertThrows(IllegalArgumentException.class,()->decode(t.profile(),Optional.empty(),r,t.blocks().copy(),t.patterns().copy(),t.chunks().copy(),Arrays.copyOf(fg,fg.length-1)));
        assertThrows(IllegalArgumentException.class,()->decode(t.profile(),Optional.empty(),r,t.blocks().copy(),t.patterns().copy(),t.chunks().copy(),Arrays.copyOf(fg,fg.length+1)));
        var bad=fg.clone();bad[0]--;assertThrows(IllegalArgumentException.class,()->decode(t.profile(),Optional.empty(),r,t.blocks().copy(),t.patterns().copy(),t.chunks().copy(),bad));
        var ids=t.collision().ids().copy();var angles=t.collision().angles().copy();var heights=t.collision().heights().copy();var widths=t.collision().widths().copy();
        for(int role=0;role<4;role++) {
            byte[][] arrays={ids,angles,heights,widths};arrays[role]=Arrays.copyOf(arrays[role],arrays[role].length-1);
            assertThrows(IllegalArgumentException.class,()->CdStageTerrain.decodeData(t.profile(),Optional.empty(),t.blocks().copy(),t.patterns().copy(),t.chunks().copy(),fg,r.role(CdProgrammeReader.Role.BG).copy(),arrays[0],arrays[1],arrays[2],arrays[3]));
        }
        var legacy=CdStageTerrain.profileFor(Shape.PPZ1_PRESENT);var weak=policy(legacy,ReferencePolicy.SELECTED_KNOWN_SOURCE);
        assertEquals("Legacy terrain profile differs",assertThrows(IllegalArgumentException.class,()->decode(weak,Optional.of(Shape.PPZ1_PRESENT),r,t.blocks().copy(),t.patterns().copy(),t.chunks().copy(),fg)).getMessage());
        assertThrows(IllegalArgumentException.class,()->new Profile(7,32,512,1,1,1,1,1,LayoutEncoding.EXACT_READ_VIEW,ReferencePolicy.SELECTED_KNOWN_SOURCE));
        var binding=r.definition().terrain().orElseThrow();var roles=new EnumMap<CdProgrammeReader.Role,Object>(CdProgrammeReader.Role.class);roles.putAll(binding.roles());roles.remove(CdProgrammeReader.Role.ANGLE);
        assertThrows(IllegalArgumentException.class,()->new CdProgrammeDefinition.Terrain(binding.profile(),Optional.empty(),roles,binding.blocks(),binding.graphics()));
    }
    @Test void ioAndMalformedRoleFaultsPublishNoTerrainAndRetryTheSameCache()throws Exception {
        var p=CdR8DataLayout.Programme.R81A;
        try(var source=open()) {
            var r=spy(CdProgrammeReader.open(source,p.payload().stage()));var key=p.range("StageGfx");var fault=new IOException("Selected original product fault");
            doThrow(fault).when(r).decoded(key);assertSame(fault,assertThrows(IOException.class,r::terrain));
            var blocks=r.decoded(p.range("StageBlocks"));doCallRealMethod().when(r).decoded(key);var terrain=r.terrain();
            assertSame(blocks,r.decoded(p.range("StageBlocks")));assertSame(terrain,r.terrain());verify(r,times(2)).decoded(key);
            var other=spy(CdProgrammeReader.open(source,p.payload().stage()));var fg=other.role(CdProgrammeReader.Role.FG);
            doReturn(fg.slice(0,fg.length()-1)).when(other).role(CdProgrammeReader.Role.FG);assertThrows(IllegalArgumentException.class,other::terrain);
            doReturn(fg).when(other).role(CdProgrammeReader.Role.FG);var retried=other.terrain();assertSame(retried,other.terrain());
        }
    }
    @Test void allInitialBindingsBorrowExactlySevenTerrainRoles()throws Exception {
        try(var source=open()) {
            for(var p:CdR8DataLayout.Programme.values()) {
                var r=spy(CdProgrammeReader.open(source,p.payload().stage()));clearInvocations(r);
                var t=r.terrain();assertSame(t,r.terrain());
                for(var role:CdProgrammeReader.Role.values())verify(r,contains(role)?times(1):never()).role(role);
                verify(r).decoded(p.range("StageBlocks"));verify(r).decoded(p.range("StageGfx"));
            }
        }
    }
    @Test void actualForeignImageAndForeignKeysNeverMintTerrainData()throws Exception {
        var p=CdR8DataLayout.Programme.R81A;
        try(var actual=open()) {
            var source=mock(CdProgramAssets.class,delegatesTo(actual));doReturn(actual.mmd(CdR8DataLayout.Programme.R81B.file())).when(source).mmd(p.file());
            assertThrows(IOException.class,()->CdStageTerrain.fromProgramme(source,p.payload().stage()));
            var r=CdProgrammeReader.open(actual,p.payload().stage());var key=p.range("StageChunks");
            var equal=new CdR8DataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256(),key.nativePath(),key.line());
            assertEquals(key,equal);assertNotSame(key,equal);assertThrows(IllegalArgumentException.class,()->r.view(equal));
            assertThrows(IllegalArgumentException.class,()->r.view(CdR8DataLayout.Programme.R81B.range("StageChunks")));
            assertThrows(NullPointerException.class,()->CdStageTerrain.fromProgramme(null,p.payload().stage()));
        }
    }
    private static boolean contains(CdProgrammeReader.Role role) {return Set.of(CdProgrammeReader.Role.CHUNKS,CdProgrammeReader.Role.FG,CdProgrammeReader.Role.BG,CdProgrammeReader.Role.COLLISION,CdProgrammeReader.Role.ANGLE,CdProgrammeReader.Role.HEIGHT,CdProgrammeReader.Role.WIDTH).contains(role);}
    private static CdProgramAssets open()throws Exception {return CdProgramAssets.open(TestCdR8DataLayout.actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty());}
    private static String hash(CdAssetBytes bytes) {return CdR11aProfile.sha256(bytes.copy());}
    private static void word(byte[] bytes,int at,int value) {bytes[at]=(byte)(value>>>8);bytes[at+1]=(byte)value;}
    private static Profile policy(Profile p,ReferencePolicy policy) {return new Profile(p.blockBytes(),p.patternBytes(),p.chunkBytes(),p.foregroundColumns(),p.foregroundRows(),p.backgroundColumns(),p.backgroundRows(),p.collisionIndexBytes(),p.layoutEncoding(),policy);}
    private static CdStageTerrain decode(Profile p,Optional<Shape> legacy,CdProgrammeReader r,byte[] blocks,byte[] gfx,byte[] chunks,byte[] fg) {
        return CdStageTerrain.decodeData(p,legacy,blocks,gfx,chunks,fg,r.role(CdProgrammeReader.Role.BG).copy(),r.role(CdProgrammeReader.Role.COLLISION).copy(),r.role(CdProgrammeReader.Role.ANGLE).copy(),r.role(CdProgrammeReader.Role.HEIGHT).copy(),r.role(CdProgrammeReader.Role.WIDTH).copy());
    }
    private static void unavailable(ReadOwner owner,int index,int count,org.junit.jupiter.api.function.Executable call) {
        var fault=assertThrows(UnavailableRead.class,call);assertEquals(owner,fault.owner());assertEquals(index,fault.index());assertEquals(count,fault.availableCount());assertTrue(fault.getMessage().contains("known DATA prefix "+count));
    }
}
