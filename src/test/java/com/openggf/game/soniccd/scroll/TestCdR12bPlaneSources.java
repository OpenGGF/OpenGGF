package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdStageTerrain;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.level.CdR12bProfile.Asset.*;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ALL JAVA UNRUN. N1ecc5f3a Scroll (Act 2 Past):847-1077,1083-1201 and Block Draw.asm.
 * Existing pure formats only. Source table/current plane values do not confer destination admission.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12bPlaneSources {
    private static final FlagWords FLAGS = new FlagWords(0x005A,0xA55A,0x5AA5);

    @Test void genuineClosedOriginalSourceBindsExactR12bTablesAndLeavesStageIdentityIntact() throws Exception {
        var f=retail();var a=f.assets();assertEquals(CdStagePayload.R12B.stage(),a.identity().stage());
        assertEquals(CdNativeFile.R12B,a.identity().file());assertSame(CdStageTerrain.Shape.PPZ2_PAST,a.terrain().shape());
        assertEquals(21,BG_CAMERA_SECT_IDS.ordinal());assertEquals(22,BG_CAMERA_SECTS.ordinal());assertEquals(24,CLOUD_LINE_COUNTS.ordinal());
        assertEquals(0x2033AA,a.profile().range(BG_CAMERA_SECT_IDS).nativeAddress());
        assertEquals(0x2033CC,a.profile().range(BG_CAMERA_SECTS).nativeAddress());assertEquals(0x202B6C,a.profile().range(CLOUD_LINE_COUNTS).nativeAddress());
        assertArrayEquals(ids(),a.view(BG_CAMERA_SECT_IDS).copy());
        assertEquals("0c92ec374afc09f10b4348843a553bf8bf67c18641b7b88d3dd107d3f23d22f0",sha(a.view(BG_CAMERA_SECT_IDS).copy()));
        assertEquals("00fff70800fff70800fff71000fff718",HexFormat.of().formatHex(a.view(BG_CAMERA_SECTS).copy()));
        assertEquals("04abc8821a06e5a30937967d11ad10221cb5ac3b5273e434f1284ee87129a061",sha(a.view(CLOUD_LINE_COUNTS).copy()));
        assertEquals("47f900202b6c",HexFormat.of().formatHex(f.original().readFile(0x2B0A,6)));
        assertEquals("41f9002033aa",HexFormat.of().formatHex(f.original().readFile(0x2FE0,6)));
        assertEquals("41f9002033aa",HexFormat.of().formatHex(f.original().readFile(0x3384,6)));
        assertArrayEquals(new byte[]{1,1,1,1,1,1,1,1},a.view(CLOUD_LINE_COUNTS).copy());
        assertNotNull(CdR12bBackgroundScroll.bind(a));
        var result=bindAct2Past(a).initialize(seed(),cameras(0,0,0,0,0,0),FLAGS);
        assertEquals(442,result.blocks().size());assertEquals(FLAGS,result.flags());
        assertArrayEquals(new int[]{32,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},result.sections().stream().mapToInt(SectionRead::tableIndex).toArray());
        // Actual R12B data; caller-flow format control supplies inherited0xA5 explicitly, not a live native register claim.
        var foreground=CdR11bForegroundPlane.fromTables(a.terrain().blocks(),a.terrain().chunks(),a.terrain().layout().ram());
        var drawn=foreground.initialize(seed(),camera(0,0),0xA55A,new CdR11bForegroundPlane.CallerMetadata(OptionalInt.of(0xA5)));
        assertEquals(512,drawn.blocks().size());assertEquals(0xA55A,drawn.flagsWord());
        assertSame(CdStageTerrain.Shape.PPZ2_PAST,a.terrain().shape());
    }

    @Test void r12bInitialSelectorsUseFourMountainAndFourWaterfallRowsWithTheExisting512Mask() {
        var f=fixture();var r=f.owner().initialize(seed(),cameras(16,96,80,32,48,64),FLAGS);
        assertArrayEquals(new int[]{0,0,0,6,6,6,6,4,4,4,4,0,0,0,0,0},r.sections().stream().mapToInt(SectionRead::selector).toArray());
        assertEquals(432,r.blocks().size());assertEquals(CameraSource.LIVE_BG3,r.blocks().get(96).camera());
        assertEquals(CameraSource.LIVE_BG2,r.blocks().get(184).camera());assertEquals(FLAGS,r.flags());
        var a=f.owner().initialize(seed(),cameras(0,0,0,0,0,0),FLAGS);
        var b=f.owner().initialize(seed(),cameras(0,512,0,0,0,0),FLAGS);assertEquals(a.sections(),b.sections());
        byte[] expected=seedBytes();for(int y=80;y<=112;y+=16)paintRow(expected,0,y,32);
        for(int y=96;y<=144;y+=16)paintRow(expected,32,y,22);
        for(int y=128;y<=176;y+=16)paintRow(expected,64,y,22);
        for(int y=256;y<=320;y+=16)paintRow(expected,0,y,32);
        assertArrayEquals(expected,r.state().nameTable().copy());
    }

    @Test void liveIdYAndDistinctCopiedCamerasRemainSeparateAndOnlyCopiedFlagsAreConsumed() {
        var live=cameras(0,160,0,0,0,0);var copied=new CopiedCameras(camera(16,48),camera(80,32),new Camera(0x01F0FFFF,0x01100001));
        var r=fixture().owner().incremental(seed(),live,copied,new FlagWords(0x015A,FLAGS.bg2(),FLAGS.bg3()));
        assertEquals(10,r.sections().getFirst().tableIndex());assertEquals(6,r.sections().getFirst().selector());assertEquals(22,r.blocks().size());
        var first=r.blocks().getFirst();assertEquals(CameraSource.COPIED_BG3,first.camera());assertEquals(480,first.worldXWord());assertEquals(256,first.worldYWord());
        assertEquals(FLAGS,r.flags());assertEquals(0x01F0FFFF,copied.bg3().xLong());assertEquals(0x01100001,copied.bg3().yLong());
        byte[] expected=seedBytes();paintRow(expected,480,256,22);assertArrayEquals(expected,r.state().nameTable().copy());
    }

    @Test void rightBg3DropsLeftBg2AndScansSixteenExactBandRows() {
        var r=fixture().owner().incremental(seed(),cameras(0,128,0,0,0,0),
                new CopiedCameras(camera(0,0),camera(80,32),camera(496,256)),new FlagWords(0x905A,FLAGS.bg2(),FLAGS.bg3()));
        assertEquals(16,r.sections().size());assertEquals(4,r.blocks().size());
        assertEquals(8,r.sections().getFirst().tableIndex());assertFalse(r.sections().getFirst().drawn());
        assertTrue(r.sections().get(1).drawn());assertFalse(r.sections().get(5).drawn());assertEquals(FLAGS,r.flags());
        byte[] expected=seedBytes();for(int y=256;y<=304;y+=16)paintBlock(expected,816,y);assertArrayEquals(expected,r.state().nameTable().copy());
    }

    @Test void explicitPastAct2BindingRejectsEveryOtherTerrainWithoutWideningOldBindings() {
        var f=fixture();var ids=CdAssetBytes.copyOf(f.ids());var pointers=CdAssetBytes.copyOf(f.pointers());
        assertThrows(IllegalArgumentException.class,()->fromAct2Tables(f.terrain(),ids,pointers));
        assertThrows(IllegalArgumentException.class,()->forPastTables(f.terrain(),ids,pointers));
        assertThrows(IllegalArgumentException.class,()->CdR11bForegroundPlane.fromTerrain(f.terrain()));
        assertThrows(IllegalArgumentException.class,()->fromAct2PastTables(terrain(CdStageTerrain.Shape.PPZ2_PRESENT),ids,pointers));
        assertThrows(IllegalArgumentException.class,()->fromAct2PastTables(terrain(CdStageTerrain.Shape.PPZ1_PAST),ids,pointers));
        assertThrows(NullPointerException.class,()->bindAct2Past(null));
        for(int length:new int[]{33,35,130})assertBoundary(Boundary.TABLE_SHAPE,length,()->fromAct2PastTables(f.terrain(),CdAssetBytes.copyOf(new byte[length]),pointers));
        assertBoundary(Boundary.TABLE_SHAPE,15,()->fromAct2PastTables(f.terrain(),ids,CdAssetBytes.copyOf(new byte[15])));
    }

    @Test void reachedSelectorAndPointerFaultsKeepInputWholeAndUnusedDataUnread() {
        var f=fixture();f.ids()[32]=8;State before=seed();
        assertBoundary(Boundary.CAMERA_POINTER_READ,16,()->f.owner().initialize(before,cameras(0,0,0,0,0,0),FLAGS));
        f.ids()[32]=0;f.pointers()[1]=0;
        assertBoundary(Boundary.CAMERA_ADDRESS,0,()->f.owner().initialize(before,cameras(0,0,0,0,0,0),FLAGS));
        Arrays.fill(f.ids(),(byte)0xFF);Arrays.fill(f.pointers(),(byte)0);
        var r=f.owner().incremental(before,cameras(0,0xFFFF,0,0,0,0),new CopiedCameras(camera(0,0),camera(0,0),camera(0,0)),FLAGS);
        assertSame(before,r.state());assertTrue(r.sections().isEmpty());assertTrue(r.blocks().isEmpty());assertArrayEquals(seedBytes(),before.nameTable().copy());
    }

    @Test void existingForegroundFormatSeamKeepsNativeRowColumnOrderFullFlagsAndInheritedMetadata() {
        var f=fixture();var t=f.terrain();var owner=CdR11bForegroundPlane.fromTables(t.blocks(),t.chunks(),t.layout().ram());
        var inherited=new CdR11bForegroundPlane.CallerMetadata(OptionalInt.empty());
        State before=seed();var r=owner.incremental(before,camera(16,16),0xAF5A,inherited);
        assertEquals(76,r.blocks().size());assertEquals(0xA05A,r.flagsWord());
        assertEquals(CdR11bForegroundPlane.Phase.TOP_ROW,r.blocks().get(0).phase());
        assertEquals(CdR11bForegroundPlane.Phase.BOTTOM_ROW,r.blocks().get(22).phase());
        assertEquals(CdR11bForegroundPlane.Phase.LEFT_COLUMN,r.blocks().get(44).phase());
        assertEquals(CdR11bForegroundPlane.Phase.RIGHT_COLUMN,r.blocks().get(60).phase());
        assertInstanceOf(CdR11bForegroundPlane.ChunkMetadata.class,r.metadata());assertArrayEquals(seedBytes(),before.nameTable().copy());
    }

    private record Fixture(CdStageTerrain terrain,byte[] ids,byte[] pointers){CdR11aBackgroundPlane owner(){return fromAct2PastTables(terrain,CdAssetBytes.copyOf(ids),CdAssetBytes.copyOf(pointers));}}
    private static Fixture fixture(){return new Fixture(terrain(CdStageTerrain.Shape.PPZ2_PAST),ids(),HexFormat.of().parseHex("00fff70800fff70800fff71000fff718"));}
    private static byte[] ids(){byte[] a=new byte[34];Arrays.fill(a,9,13,(byte)6);Arrays.fill(a,13,17,(byte)4);return a;}
    private static CdStageTerrain terrain(CdStageTerrain.Shape shape){
        boolean act1=shape==CdStageTerrain.Shape.PPZ1_PAST,present=shape==CdStageTerrain.Shape.PPZ2_PRESENT;
        byte[] blocks=new byte[act1?5280:present?5824:5664];word(blocks,0,0xA001);word(blocks,2,0x4802);word(blocks,4,0x1003);word(blocks,6,0xF004);
        byte[] fg=new byte[170],bg=new byte[22];fg[0]=41;fg[1]=3;bg[0]=9;bg[1]=1;Arrays.fill(fg,2,fg.length,(byte)1);Arrays.fill(bg,2,bg.length,(byte)1);
        return CdStageTerrain.decode(shape,blocks,new byte[act1?23872:present?25952:25504],new byte[54272],fg,bg,new byte[act1?800:900],new byte[256],new byte[4096],new byte[4096]);
    }
    private static Camera camera(int x,int y){return new Camera(x<<16,y<<16);}
    private static LiveCameras cameras(int x,int y,int x2,int y2,int x3,int y3){return new LiveCameras(camera(x,y),camera(x2,y2),camera(x3,y3));}
    private static State seed(){return new State(CdAssetBytes.copyOf(seedBytes()));}
    private static byte[] seedBytes(){byte[] a=new byte[4096];Arrays.fill(a,(byte)0x55);return a;}
    private static void paintRow(byte[] a,int x,int y,int n){for(int i=0;i<n;i++)paintBlock(a,x+i*16,y);}
    private static void paintBlock(byte[] a,int x,int y){int tx=(x&511)/8,ty=(y&255)/8;word(a,(ty*64+tx)*2,0xA001);word(a,(ty*64+tx+1)*2,0x4802);word(a,((ty+1)*64+tx)*2,0x1003);word(a,((ty+1)*64+tx+1)*2,0xF004);}
    private static void word(byte[] a,int at,int value){a[at]=(byte)(value>>>8);a[at+1]=(byte)value;}
    private static String sha(byte[] a)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(a));}
    private static void assertBoundary(Boundary b,int offset,org.junit.jupiter.api.function.Executable work){var x=assertThrows(InvalidPlane.class,work);assertEquals(b,x.boundary());assertEquals(offset,x.byteOffset());}
    private record Retail(CdR12bTerrainAssets assets,CdMmdImage original){}
    private Retail retained;
    private Retail retail()throws Exception{
        if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Configure original CD media root");assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=new Retail(CdR12bTerrainAssets.fromPrograms(source),source.mmd(CdNativeFile.R12B));}
        return retained;
    }
}
