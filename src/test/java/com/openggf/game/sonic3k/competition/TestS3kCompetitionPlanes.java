package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3k;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.competition.S3kCompetitionPlanes.Cell;
import com.openggf.game.sonic3k.competition.S3kCompetitionScroll.CameraPair;
import com.openggf.game.sonic3k.competition.S3kCompetitionScroll.CameraPosition;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.PatternDesc;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Native VRAM addressing oracles; no ordinary playable/controller/title boot. */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionPlanes {
    private Rom rom;
    private static final int[] LAYOUTS={0x28B626,0x28B7F2,0x28B946,0x28BA3E,0x28BB36};
    @BeforeEach void rom() throws Exception {rom=RomManager.getInstance().getRom();}
    private static Cell cell(int flags,int... words) {
        var chunk=new Chunk();
        for(int y=0;y<2;y++) for(int x=0;x<2;x++) chunk.setPatternDesc(x,y,new PatternDesc(words[y*2+x]));
        return new Cell(new ChunkDesc(flags),chunk);
    }
    private static Cell quad(){return cell(0,0x2001,0xA802,0x1003,0xF804);}
    private S3kCompetitionPlanes fixed(Arena arena) throws Exception {
        return new S3kCompetitionPlanes(rom,arena,(layer,x,y)->quad());
    }

    @Test void verifiedBootRegistersAndRomProfilesDefineTwoEightKilobytePlanes() throws Exception {
        var reader=RomByteReader.fromRom(rom);
        assertEquals(0x9003,reader.readU16BE(0x61B0));assertEquals(0x9011,reader.readU16BE(0x61BC));
        assertEquals(0x8220,reader.readU16BE(0x61A2));assertEquals(0x8405,reader.readU16BE(0x61A6));
        for(Arena arena:Arena.values()) {
            var p=fixed(arena).profile();boolean bpz=arena==Arena.BALLOON_PARK;
            assertEquals(bpz?0x9011:0x9003,p.screenSizeRegister());
            assertEquals(bpz?64:128,p.widthTiles());assertEquals(bpz?64:32,p.heightTiles());
            assertEquals(4096,p.widthTiles()*p.heightTiles());assertEquals(bpz?0x80:0x100,p.rowStrideBytes());
            assertEquals(bpz?0x200:0x100,p.initialForegroundY());assertEquals(LAYOUTS[arena.ordinal()],p.layoutAddress());
            var owner=fixed(arena);
            assertSame(owner.foregroundPlane(),owner.foregroundPlane());
            assertSame(owner.backgroundPlane(),owner.backgroundPlane());
            assertEquals(p.widthTiles(),owner.foregroundPlane().widthTiles());
            assertEquals(p.heightTiles(),owner.backgroundPlane().heightTiles());
            assertEquals(0,owner.foregroundPlane().revision());
            owner.initialize();
            assertEquals(owner.foregroundDescriptorAt(-8,520),owner.foregroundPlane().descriptorAt(-8,520));
            assertEquals(owner.backgroundDescriptorAt(1040,-8),owner.backgroundPlane().descriptorAt(1040,-8));
            assertEquals(1,owner.backgroundPlane().revision());
        }
    }
    @Test void fullRefreshReadsNativeSourceCoordinatesAndDistinctTopThenBottomPayloads() throws Exception {
        record Query(int layer,int x,int y) { }
        for(Arena arena:List.of(Arena.AZURE_LAKE,Arena.BALLOON_PARK)) {
            var calls=new ArrayList<Query>();
            var planes=new S3kCompetitionPlanes(rom,arena,(layer,x,y)->{calls.add(new Query(layer,x,y));return quad();});
            assertEquals(0,planes.foregroundDescriptorRevision());assertEquals(0,planes.backgroundDescriptorRevision());
            assertThrows(IllegalStateException.class,()->planes.foregroundDescriptorAt(0,0));planes.initialize();
            int columns=arena==Arena.BALLOON_PARK?32:64,firstY=arena==Arena.BALLOON_PARK?0x200:0x100;
            assertEquals(new Query(0,0,firstY),calls.getFirst());
            assertEquals(new Query(0,(columns-1)*16,firstY),calls.get(columns-1));
            assertEquals(new Query(1,0,0),calls.get(1024));assertEquals(2048,calls.size());
            assertEquals(0x2001,planes.physicalVramWord(0x8000));assertEquals(0xA802,planes.physicalVramWord(0x8002));
            int nextRow=0x8000+planes.profile().rowStrideBytes();
            assertEquals(0x1003,planes.physicalVramWord(nextRow));assertEquals(0xF804,planes.physicalVramWord(nextRow+2));
            assertNotEquals(planes.physicalVramWord(0x8000),planes.physicalVramWord(nextRow),"second VInt write consumes bottom payload");
            assertEquals(1,planes.foregroundDescriptorRevision());assertEquals(1,planes.backgroundDescriptorRevision());
        }
    }
    @Test void nativeChunkFlipBranchesSwapAxesAndXorOnlyTheirDescriptorBits() throws Exception {
        int[] flags={0,0x400,0x800,0xC00};
        int[][] expected={{0x2001,0xA802,0x1003,0xF804},{0xA002,0x2801,0xF004,0x1803},
                {0x0003,0xE804,0x3001,0xB802},{0xE004,0x0803,0xB002,0x3801}};
        for(int i=0;i<flags.length;i++) {
            int flag=flags[i];var planes=new S3kCompetitionPlanes(rom,Arena.AZURE_LAKE,
                    (layer,x,y)->cell(flag,0x2001,0xA802,0x1003,0xF804));planes.initialize();
            int row=planes.profile().rowStrideBytes();
            assertArrayEquals(expected[i],new int[]{planes.physicalVramWord(0x8000),planes.physicalVramWord(0x8002),
                    planes.physicalVramWord(0x8000+row),planes.physicalVramWord(0x8002+row)});
        }
    }
    @Test void physicalRingQueriesWrapBothAxesAtNativeGeometryIncludingNegativePixels() throws Exception {
        var planes=new S3kCompetitionPlanes(rom,Arena.BALLOON_PARK,(layer,x,y)-> {
            int base=(layer*0x400+(y>>4)*8+(x>>4))&0x7FC;
            return cell(0,base,base+1,base+2,base+3);
        });planes.initialize();
        assertEquals(64,planes.foregroundDescriptorRingRows());
        assertEquals(planes.foregroundDescriptorAt(504,504),planes.foregroundDescriptorAt(-1,-1));
        assertEquals(planes.foregroundDescriptorAt(0,0),planes.foregroundDescriptorAt(512,512));
        assertEquals(planes.backgroundDescriptorAt(0,0),planes.backgroundDescriptorAt(1024,-512));
        assertEquals(planes.foregroundDescriptorAt(504,504),planes.physicalVramWord(0x9FFE));
        assertEquals(planes.backgroundDescriptorAt(504,504),planes.physicalVramWord(0xBFFE));
        assertThrows(IllegalArgumentException.class,()->planes.physicalVramWord(0x8001));
        assertThrows(IllegalArgumentException.class,()->planes.physicalVramWord(0xC000));
    }
    @Test void descriptorsStayRetainedAcrossBothCameraViewsUntilTheNativeRefreshEntryRuns() throws Exception {
        var generation=new AtomicInteger(0);
        var planes=new S3kCompetitionPlanes(rom,Arena.AZURE_LAKE,(layer,x,y)-> {
            int word=1+generation.get()*4;return cell(0,word,word+1,word+2,word+3);
        });planes.initialize();var before=planes.capture();generation.set(10);
        var scroll=new S3kCompetitionScroll(rom,Arena.AZURE_LAKE,5);
        scroll.initialize(new CameraPair(new CameraPosition(0x7F0,0x148),new CameraPosition(0x410,0x168)));
        for(int frame=0;frame<8;frame++) {
            scroll.advance(new CameraPair(new CameraPosition(0x410+frame*16,0x148),new CameraPosition(0x7F0-frame*16,0x168)));
            for(int slot=0;slot<2;slot++) {
                var view=scroll.view(slot);
                for(int line=0;line<view.horizontalScroll().size();line+=13) {
                    int packed=view.horizontalScroll().get(line),nativeY=view.nativeRasterStart()+line;
                    planes.foregroundDescriptorAt(-(short)(packed>>>16),nativeY+view.foregroundVScroll());
                    planes.backgroundDescriptorAt(-(short)packed,nativeY+view.backgroundVScroll());
                }
            }
        }
        assertSame(before,planes.capture(),"camera/deformation and render reads do not regenerate descriptors");
        planes.backgroundInit();assertEquals(1,planes.foregroundDescriptorAt(0,0));assertEquals(41,planes.backgroundDescriptorAt(0,0));
        assertEquals(1,planes.foregroundDescriptorRevision());assertEquals(2,planes.backgroundDescriptorRevision());
        planes.screenInit();assertEquals(41,planes.foregroundDescriptorAt(0,0));assertEquals(2,planes.foregroundDescriptorRevision());
    }
    @Test void immutableNativePlaneSnapshotsRestoreRecreateAndReplayBothRefreshes() throws Exception {
        var bank=new AtomicInteger(0);
        S3kCompetitionPlanes.ChunkSource source=(layer,x,y)->cell(0,bank.get()+1,bank.get()+2,bank.get()+3,bank.get()+4);
        var planes=new S3kCompetitionPlanes(rom,Arena.ENDLESS_MINE,source);planes.initialize();var before=planes.capture();
        bank.set(100);planes.backgroundInit();planes.screenInit();var expected=planes.capture();
        assertThrows(UnsupportedOperationException.class,()->before.foreground().set(0,99));
        var restored=new S3kCompetitionPlanes(rom,Arena.ENDLESS_MINE,source);restored.restore(before);
        assertEquals(before,restored.capture());assertEquals(1,restored.foregroundDescriptorAt(0,0));
        restored.backgroundInit();restored.screenInit();assertEquals(expected,restored.capture());
        assertThrows(IllegalArgumentException.class,()->fixed(Arena.AZURE_LAKE).restore(before));
    }
    @Test void missingNativeGeometryFailsInsteadOfSynthesizingEmptyDescriptors() throws Exception {
        var planes=new S3kCompetitionPlanes(rom,Arena.AZURE_LAKE,(layer,x,y)->null);
        assertThrows(NullPointerException.class,planes::initialize);assertEquals(0,planes.foregroundDescriptorRevision());
    }

    @ParameterizedTest @EnumSource(Arena.class)
    void actualRomPlaneWordsMatchIndependentNativeRowPointersAndPatternAddressing(Arena arena) throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        try {
            var prepared=new Sonic3k(rom).prepareLevelBuildTask(arena.zoneId()*2,null).call();
            var manager=GameServices.level();manager.setLevel(prepared.level());
            var planes=new S3kCompetitionPlanes(rom,arena,manager);planes.initialize();
            int width=arena==Arena.BALLOON_PARK?64:128,height=arena==Arena.BALLOON_PARK?64:32;
            int sourceBaseY=arena==Arena.BALLOON_PARK?0x200:0x100;
            int layout=LAYOUTS[arena.ordinal()];var reader=RomByteReader.fromRom(rom);
            assertEquals(layout,planes.profile().layoutAddress());
            for(int layer=0;layer<2;layer++) for(int tileY=0;tileY<height;tileY++) for(int tileX=0;tileX<width;tileX++) {
                int sourceY=tileY*8+(layer==0?sourceBaseY:0);
                int expected=referenceDescriptor(reader,prepared.level(),arena,layout,layer,tileX*8,sourceY);
                int address=(layer==0?0x8000:0xA000)+2*(tileY*width+tileX);
                assertEquals(expected,planes.physicalVramWord(address),arena+" plane"+layer+" VRAM$"+Integer.toHexString(address));
            }
            if(arena==Arena.BALLOON_PARK) {
                assertEquals(3,prepared.level().getLayerHeightBlocks(1));assertEquals(0,reader.readU16BE(layout+8+3*4+2));
                for(int column=0;column<4;column++) assertEquals(0,reader.readU8(column),"zero row pointer reads actual reset-vector bytes");
                assertEquals(referenceDescriptor(reader,prepared.level(),arena,layout,1,0,0x180),planes.backgroundDescriptorAt(0,0x180));
            }
            var before=planes.capture();
            var definition=S3kCompetitionArenaDefinition.load(rom,arena);
            var scroll=new S3kCompetitionScroll(rom,arena,5);
            var bodies=definition.createPlayers(rom,List.of(S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS));
            scroll.initializeFromPlayers(bodies.get(0),bodies.get(1));scroll.trackPlayers(bodies.get(0),bodies.get(1));
            for(int slot=0;slot<2;slot++) {
                var view=scroll.view(slot);int line=view.nativeRasterStart();int packed=view.horizontalScroll().getFirst();
                int fgX=-(short)(packed>>>16),fgY=line+view.foregroundVScroll();
                int bgX=-(short)packed,bgY=line+view.backgroundVScroll();
                int fgAddress=0x8000+2*(Math.floorMod(Math.floorDiv(fgY,8),height)*width+Math.floorMod(Math.floorDiv(fgX,8),width));
                int bgAddress=0xA000+2*(Math.floorMod(Math.floorDiv(bgY,8),height)*width+Math.floorMod(Math.floorDiv(bgX,8),width));
                assertEquals(planes.physicalVramWord(fgAddress),planes.foregroundDescriptorAt(fgX,fgY));
                assertEquals(planes.physicalVramWord(bgAddress),planes.backgroundDescriptorAt(bgX,bgY));
            }
            assertSame(before,planes.capture());
            var restored=S3kCompetitionPlanes.recreate(rom,arena,manager,before);assertEquals(before,restored.capture());
            planes.backgroundInit();planes.screenInit();restored.backgroundInit();restored.screenInit();
            assertEquals(planes.capture(),restored.capture(),"actual ROM geometry replays after native descriptor recreation");
        } finally {SessionManager.clear();GameModuleRegistry.reset();}
    }
    /** Independent hardware address oracle, intentionally bypassing LevelManager layout wrapping. */
    private static int referenceDescriptor(RomByteReader rom,Level level,Arena arena,int layout,int layer,int x,int y) throws Exception {
        int rowCount=arena==Arena.BALLOON_PARK?8:arena==Arena.CHROME_GADGET?2:4;
        int row=y/128&(rowCount-1),pointer=rom.readU16BE(layout+8+row*4+layer*2);
        int blockAddress=(pointer>=0x8000?layout+pointer-0x8000:pointer)+x/128;
        int blockId=rom.readU8(blockAddress);
        var chunkDescriptor=level.getBlock(blockId).getChunkDesc(x/16%8,y/16%8);
        int tileX=x/8%2,tileY=y/8%2,flags=chunkDescriptor.get();
        if((flags&0x400)!=0) tileX^=1;if((flags&0x800)!=0) tileY^=1;
        int pattern=level.getChunk(flags&0x3FF).getPatternDesc(tileX,tileY).get();
        return pattern^((flags&0x400)!=0?0x800:0)^((flags&0x800)!=0?0x1000:0);
    }
}
