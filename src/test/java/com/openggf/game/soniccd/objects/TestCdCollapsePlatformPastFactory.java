package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authenticated USA B views and shared component execution, not a natural World/contact proof.
 * Native pin1ecc5f3a: Act1Past.asm:67-68 includes the same Collapse Main and Data(Past).
 * Main.asm:31-69,134-308 supplies Init/breakup/physical-order rules and literal tile44BE.
 * Full data below is independently expanded from the four Past mapping includes and Data(Past).
 * Breakup uses existing public-format terrain with the genuine shared Solid owner, so no expected
 * oracle invokes/replays a Body, native helper or callback, and no synthetic d0 is supplied.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdCollapsePlatformPastFactory {
    private static final int WHOLE = 40, X0 = 3248, Y0 = 784;
    private static final int[] ADDRESSES = {0x20C378, 0x20C4A8, 0x20C46A, 0x20C7F0};
    private static final int[][] OFFSETS = {{4,120}, {12,12,103,329,430,531},
            {14,20,26,32,38,44,50}, {14,30,56,82,108,129,150}};
    private static final int[][] PIECES = {{23,16}, {18,18,45,20,20,50},
            {1,1,1,1,1,1,2}, {3,5,5,5,4,4,4}};
    private static final int[] TILE_ENDS = {41,47,41,47};
    private static final byte[] SIZE1 = HexFormat.of().parseHex(
            "0004001a0403ffff0000000102030304000505050506060606060302010203030505050506060606");
    private static final byte[] SIZE2 = HexFormat.of().parseHex(
            "000c000c0014001f0026002d05010000000000000803010303030303030302040204060606060402060606060509030103030303030303030200");
    private static final String[] MAPPING_HEX = {
            "0004007817d005080dd8d005080de8d005080df8d005080d08d005080d18e0050811d8e0050811e8e0050811f8e005081108e005081118f0"
            + "050019d8f0050815e8f0050815f8f005081508f00508151800050825d80005001de800050021f800050021080005081d1810050019f81005"
            + "001908100500191810d805080de0d805080df0d805080d00d805080d10e8050811e0e8050811f0e805081100e805081110f8050815e0f805"
            + "0815f0f805081500f80508151008050825e00805001df00805002100080500211000",

            "000c000c0067014901ae021312e005000dd0e005000de0e005000df0e005000d00e005000d10e005000d20f0050011d0f0050011e0f00500"
            + "11f0f005001100f005001110f00500112000050015d000050015e000050015f00005001500000500151000050015202dd005000db8d00500"
            + "0dc8d005000dd8d005000de8d005000df8d005000d08d005000d18d005000d28d005000d38e0050011b8e0050011c8e0050011d8e0050011"
            + "e8e0050011f8e005001108e005001118e005001128e005001138f0050015b8f0050015c8f0050015d8f0050015e8f0050015f8f005001508"
            + "f005001518f005001528f0050015380005002bb80005002bc80005002bd80005002be80005002bf80005002b080005002b180005002b2800"
            + "05002b381005001db810050021c810050021d810050021e810050021f81005002108100500211810050021281005081d3814d805000dd8d8"
            + "05000de8d805000df8d805000d08d805000d18e8050011d8e8050011e8e8050011f8e805001108e805001118f8050015d8f8050015e8f805"
            + "0015f8f805001508f8050015180805001dd808050021e808050021f80805002108080500211814d805000dd8d805000de8d805000df8d805"
            + "000d08d805000d18e8050011d8e8050011e8e8050011f8e805001108e805001118f8050015d8f8050015e8f8050015f8f805001508f80500"
            + "151808050021d808050021e808050021f808050021080805081d1832d005000db0d005000dc0d005000dd0d005000de0d005000df0d00500"
            + "0d00d005000d10d005000d20d005000d30d005000d40e0050011b0e0050011c0e0050011d0e0050011e0e0050011f0e005001100e0050011"
            + "10e005001120e005001130e005001140f0050015b0f0050015c0f0050015d0f0050015e0f0050015f0f005001500f005001510f005001520"
            + "f005001530f0050015400005002bb00005002bc00005002bd00005002be00005002bf00005002b000005002b100005002b200005002b3000"
            + "05002b401005001db010050021c010050021d010050021e010050021f010050021001005002110100500212010050021301005081d40",

            "000e0014001a00200026002c003201f8050019f801f8050825f801f805001df801f8050021f801f805081df801f8050815f802e805080df8"
            + "f8050811f800",

            "000e001e00380052006c0081009603e005000df8f0050011f800050015f805d005000df8e0050011f8f0050015f80005002bf81005001df8"
            + "05d005000df8e0050011f8f0050015f80005002bf81005081df805d005000df8e0050011f8f0050015f80005002bf810050021f804d80500"
            + "0df8e8050011f8f8050015f80805001df804d805000df8e8050011f8f8050015f80805081df804d805000df8e8050011f8f8050015f80805"
            + "0021f800"
    };
    private static final int[][] MULTI = {
            {-1,-1,0,0,0,1,2,3,3,4,0,5,5,5,5,6,6,6,6,6},
            {1,2,3,3,5,5,5,5,6,6,6,6}};
    private static final int[][] SINGLE = {{0,0,0,0,0,0}, {0,0,0,0,0,0},
            {1,3,3,3,3,3,3,3,2}, {4,6,6,6,6}, {6,6,6,6,5}, {1,3,3,3,3,3,3,3,3,2}};

    @Test void namedPastFactoryRetainsAllSixCompleteOriginalViewsAndCorrectNativeRoots() throws Exception {
        var actual = retail(); var data = CdCollapsePlatform.Assets.fromPastAssets(actual);
        assertEquals("b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265", actual.identity().mmdSha256());
        var keys = List.of(CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_1,
                CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_2,
                CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_3,
                CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_4);
        var visuals = visuals(data); int[] addresses = {data.mappings1(),data.mappings2(),data.mappings3(),data.mappings4()};
        for (int root = 0; root < 4; root++) {
            var view = actual.view(keys.get(root)); var visual = visuals.get(root);
            assertEquals(ADDRESSES[root], addresses[root]); assertEquals(ADDRESSES[root], view.range().nativeAddress());
            assertArrayEquals(HexFormat.of().parseHex(MAPPING_HEX[root]), view.bytes().copy());
            assertArrayEquals(view.bytes().copy(), visual.mappings().copy());
            assertEquals(OFFSETS[root].length, visual.mappingFrames().size());
            assertEquals(0, visual.mappingTableOffset()); assertTrue(visual.animations().isEmpty());
            assertEquals(0, visual.animationTable().length());
            assertThrows(IndexOutOfBoundsException.class, () -> visual.mappings().unsignedByte(view.bytes().length()));
        }
        assertEquals(0x20C442, actual.view(CdR11bAssets.Asset.COLLAPSE_PLATFORM_SIZES_1).range().nativeAddress());
        assertEquals(0x20C7B6, actual.view(CdR11bAssets.Asset.COLLAPSE_PLATFORM_SIZES_2).range().nativeAddress());
        assertArrayEquals(SIZE1, data.sizes1().copy()); assertArrayEquals(SIZE2, data.sizes2().copy());
        assertArrayEquals(actual.view(CdR11bAssets.Asset.COLLAPSE_PLATFORM_SIZES_1).bytes().copy(), data.sizes1().copy());
        assertArrayEquals(actual.view(CdR11bAssets.Asset.COLLAPSE_PLATFORM_SIZES_2).bytes().copy(), data.sizes2().copy());
        assertEquals(40, data.sizes1().length()); assertEquals(58, data.sizes2().length());
    }

    @Test void everyPastCountedFrameKeepsLiteralPiecesAliasesAlignmentAndExistingTileBound() throws Exception {
        var visuals = visuals(CdCollapsePlatform.Assets.fromPastAssets(retail()));
        for (int root = 0; root < 4; root++) {
            byte[] source = HexFormat.of().parseHex(MAPPING_HEX[root]); var visual = visuals.get(root); int maximum = 0;
            for (int frame = 0; frame < OFFSETS[root].length; frame++) {
                var decoded = visual.mappingFrames().get(frame); int offset = OFFSETS[root][frame];
                assertEquals(offset, decoded.relativeOffset()); assertEquals(PIECES[root][frame], decoded.pieces().size());
                assertArrayEquals(Arrays.copyOfRange(source, offset, offset + 1 + 5 * PIECES[root][frame]), decoded.bytes().copy());
                for (int p = 0; p < PIECES[root][frame]; p++) {
                    int at = offset + 1 + p * 5; var piece = decoded.pieces().get(p);
                    assertEquals(source[at], piece.y()); assertEquals(source[at+1] & 255, piece.sizeByte());
                    assertEquals(word(source,at+2), piece.rawTileWord()); assertEquals(source[at+4], piece.x());
                    maximum = Math.max(maximum, piece.tileOffset() + piece.tileCount());
                }
            }
            assertEquals(TILE_ENDS[root], maximum); assertTrue(maximum <= 57);
        }
        assertEquals(visuals.get(1).mappingFrames().get(0).relativeOffset(), visuals.get(1).mappingFrames().get(1).relativeOffset());
        assertEquals(0x21, visuals.get(3).mappingFrames().get(6).pieces().getLast().rawTileWord(),
                "The genuine seventh child frame ends with native tile21, not the six-frame A shape");
    }

    @Test void factoryAddsOnlyTheNamedPastEntryAndPreservesExistingOrdinaryComponentSurface() throws Exception {
        assertEquals(CdCollapsePlatform.Assets.class,
                CdCollapsePlatform.Assets.class.getMethod("fromPastAssets", CdR11bAssets.class).getReturnType());
        assertNotNull(CdCollapsePlatform.Assets.class.getMethod("fromObjectAssets", CdR11aObjectAssets.class));
        assertEquals(10, CdCollapsePlatform.Assets.class.getRecordComponents().length);
        assertEquals(1, CdCollapsePlatform.class.getConstructors().length);
        assertEquals(6, CdCollapsePlatform.class.getConstructors()[0].getParameterCount());
        assertNotNull(CdCollapsePlatform.class.getMethod("run", int.class, CdCollapsePlatform.DespawnGlobals.class, CdCollapsePlatform.Inputs.class));
        assertEquals(0x20, CdCollapsePlatform.OBJECT_ID); assertEquals(0x44BE, CdCollapsePlatform.TILE_WORD);
        assertThrows(NullPointerException.class, () -> CdCollapsePlatform.Assets.fromPastAssets(null));
        assertThrows(NullPointerException.class, () -> CdCollapsePlatform.Assets.fromObjectAssets(null));
    }

    @Test void initEveryLegalPastByteSubtypeKeepsTheFullNativeImageAndLiteralTile() throws Exception {
        var actual = retail(); var data = CdCollapsePlatform.Assets.fromPastAssets(actual);
        for (int subtype = 0; subtype < 256; subtype++) {
            int index = subtype & 15; if (index >= (subtype < 128 ? 2 : 6)) continue;
            var h = new Harness(actual, data); h.seed(subtype, 0); var before = h.slot(); var poolBefore = h.pool().capture();
            byte[] expected = before.copyBytes(); expected[0x24] = 2; expected[1] |= 4; expected[0x18] = 3;
            word(expected, 2, 0x44BE); putLong(expected,4,subtype < 128 ? ADDRESSES[0] : ADDRESSES[1]);
            if ((subtype & 16) != 0) { expected[1] |= 1; expected[0x22] |= 1; }
            expected[0x1A] = (byte) index; byte[] sizes = subtype < 128 ? SIZE1 : SIZE2; int at = word(sizes,index*2);
            expected[0x17] = expected[0x19] = (byte) (((sizes[at] & 255) + 1) * 8);
            expected[0x16] = (byte) ((Math.abs(sizes[at+1]) + 1) * 8 + 2);
            var result = h.run(); assertEquals(1,h.calls.size());
            assertArrayEquals(expected,h.calls.getFirst().actualCut().slots().get(WHOLE-32).copyBytes(),"Init subtype " + subtype);
            expected[0x25]=0; assertArrayEquals(expected,result.state().copyBytes()); assertArrayEquals(expected,h.draws.getFirst().copyBytes());
            assertEquals(List.of(CdCollapsePlatform.Site.MAIN_TOP),h.sites()); assertTrue(h.fm.isEmpty());
            assertPool(poolBefore,h.pool(),Set.of(WHOLE)); assertEquals(0xD7,result.state().subtype2());
        }
    }

    @Test void bothPastMultiRowShapesPreserveEveryDirtyChildByteAndSourceCellOrder() throws Exception {
        var actual=retail(); var data=CdCollapsePlatform.Assets.fromPastAssets(actual);
        for(int index=0;index<2;index++) for(int flip:new int[]{0,16}) {
            var h=new Harness(actual,data); h.dirtyFree(); h.main(index|flip); h.contact(0,0,0);
            var before=h.pool().capture(); h.run(); int ordinal=0, columns=index==0?5:4, rows=index==0?4:3;
            var expectedChanged=new java.util.HashSet<Integer>();expectedChanged.add(WHOLE);
            for(int cell=0;cell<MULTI[index].length;cell++) {
                int frame=MULTI[index][cell]; if(frame<0)continue;
                int physical=32+ordinal++; if(physical>=WHOLE)physical++; expectedChanged.add(physical);
                byte[] expected=before.slots().get(physical-32).copyBytes(); int row=cell/columns,col=cell%columns;
                expected[0]=0x20;expected[0x24]=4;expected[0x1A]=(byte)frame;expected[1]|=4;expected[0x18]=3;
                word(expected,2,0x44BE);putLong(expected,4,ADDRESSES[2]);putLong(expected,0x2C,0x20000);
                if(flip!=0){expected[1]|=1;expected[0x22]|=1;}
                if(row==rows-1){expected[0x3E]=(byte)255;expected[0x17]=expected[0x19]=8;expected[0x16]=9;}
                word(expected,8,X0+(flip!=0?1:-1)*(columns-1)*8+(flip!=0?-16:16)*col);
                word(expected,0x0C,Y0+(rows-1)*8-16*row);word(expected,0x2A,9+5*row+12*col);
                assertArrayEquals(expected,h.pool().state(physical).copyBytes(),"Past multi index/flip/cell "+index+"/"+flip+"/"+cell);
            }
            assertEquals(index==0?18:12,ordinal);assertEquals(MULTI[index].length,h.result.effects().stream().filter(CdCollapsePlatform.SlotFound.class::isInstance).count());
            assertArrayEquals(new byte[64],h.slot().copyBytes()); assertArrayEquals(new byte[64],h.draws.getFirst().copyBytes());
            assertEquals(List.of(0xA3),h.fm);assertEquals(List.of(CdCollapsePlatform.Site.MAIN_TOP,CdCollapsePlatform.Site.MAIN_GET_OFF),h.sites());
            assertEquals(List.of("solid:MAIN_TOP","solid:MAIN_GET_OFF","fm:163","draw"),h.order);
            assertPool(before,h.pool(),expectedChanged);
        }
    }

    @Test void everyPastSingleRowUsesRealScriptFramesBothDirectionsAndFullDirtyImages() throws Exception {
        var actual=retail();var data=CdCollapsePlatform.Assets.fromPastAssets(actual);
        for(int index=0;index<6;index++)for(int flags:new int[]{0x80,0xA0,0xC0,0xE0})for(int speed:new int[]{-256,0,256}) {
            var h=new Harness(actual,data);h.dirtyFree();h.main(flags|index);h.contact(speed,0,0);var before=h.pool().capture();h.run();
            boolean reverse=(flags&64)==0?(flags&32)!=0:(short)((flags&32)==0?speed:-speed)<0;
            int n=SINGLE[index].length;var changed=new java.util.HashSet<Integer>();changed.add(WHOLE);
            for(int ordinal=0;ordinal<n;ordinal++) {
                int physical=32+ordinal;if(physical>=WHOLE)physical++;changed.add(physical);int at=reverse?n-1-ordinal:ordinal;
                byte[] expected=before.slots().get(physical-32).copyBytes();expected[0]=0x20;expected[0x24]=4;expected[1]|=4;
                expected[0x18]=3;word(expected,2,0x44BE);putLong(expected,4,ADDRESSES[3]);putLong(expected,0x2C,0x20000);
                expected[0x3E]=(byte)255;expected[0x17]=expected[0x19]=8;expected[0x16]=(byte)(index<2?18:index<3||index==5?34:26);
                expected[0x1A]=(byte)SINGLE[index][at];word(expected,8,X0-8*(n-1)+16*at);word(expected,0x0C,Y0);word(expected,0x2A,9+12*ordinal);
                assertArrayEquals(expected,h.pool().state(physical).copyBytes(),"Past single "+(flags|index)+" speed "+speed+" child "+ordinal);
            }
            assertEquals(n,h.result.effects().stream().filter(CdCollapsePlatform.ChildInitialized.class::isInstance).count());
            assertArrayEquals(new byte[64],h.slot().copyBytes());assertEquals(List.of(0xA3),h.fm);assertPool(before,h.pool(),changed);
        }
    }

    @Test void ascendingPhysicalWalkLeavesEarlierChildUntouchedAndVisitsLaterChildInSamePass() throws Exception {
        var h=new Harness(retail(),CdCollapsePlatform.Assets.fromPastAssets(retail()));h.main(0x80);h.contact(0,0,0);
        for(int i=32;i<128;i++)if(i!=WHOLE)h.pool().write(h.pool().state(i).moveByte(ID,0x31));
        h.pool().objNull(32);h.pool().objNull(41);
        for(int i=32;i<128;i++)if(h.pool().state(i).id()==0x20)h.body.run(i,world(),h.inputs());
        assertEquals(9,h.pool().state(32).word(COLLAPSE_DELAY));assertEquals(20,h.pool().state(41).word(COLLAPSE_DELAY));
        assertEquals(4,h.pool().state(32).routine());assertEquals(4,h.pool().state(41).routine());assertEquals(0,h.slot().id());
        assertEquals(ADDRESSES[3],h.pool().state(32).longBits(MAPPINGS));assertEquals(ADDRESSES[3],h.pool().state(41).longBits(MAPPINGS));
    }

    @Test void realStoppedSolidAdoptsOnlyReachedPrefixAndRethrowsTheIdenticalFaultBeforeSoundOrDraw() throws Exception {
        var actual=retail();var data=CdCollapsePlatform.Assets.fromPastAssets(actual);
        for(int stand:new int[]{1,31,128,255}) {
            var h=new Harness(actual,data);h.main(0x80);h.contact(0,8,stand);var before=h.slot();
            var failure=assertThrows(RuntimeException.class,h::run);assertEquals(1,h.calls.size());
            var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,h.calls.getFirst().outcome());
            assertSame(stopped.stop().originalException(),failure);assertEquals(stopped.prefix().player(),h.player);
            assertEquals(stopped.prefix().globals(),h.globals);assertEquals(stopped.prefix().object(),h.slot());
            byte[] expected=before.copyBytes();expected[0x25]=0;expected[0x22]|=8;assertArrayEquals(expected,h.slot().copyBytes());
            assertEquals(stand,h.player.standObject());assertEquals(Set.of(SOLID_TYPE,FLAGS),stopped.prefix().objectWrites());
            assertTrue(h.fm.isEmpty());assertTrue(h.draws.isEmpty());
        }
    }

    @Test void pastDelayThenFallKeepsNativeTransitionAndFullLongFractionsWithoutExtraSolid() throws Exception {
        var h=new Harness(retail(),CdCollapsePlatform.Assets.fromPastAssets(retail()));h.seed(0x80,4);
        h.pool().write(h.slot().moveWord(COLLAPSE_DELAY,1).moveByte(COLLAPSE_SOLID_MARKER,0).moveLong(COLLAPSE_FALL_DELTA,0x20000));
        var before=h.slot();byte[] expected=before.copyBytes();expected[0x24]=6;word(expected,0x2A,0);
        h.run();assertArrayEquals(expected,h.slot().copyBytes());assertTrue(h.calls.isEmpty());assertEquals(1,h.draws.size());
        putLong(expected,0x0C,before.longBits(Y_POSITION)+0x20000);putLong(expected,0x2C,0x24000);
        h.player=player((Y0<<16)|0x1234,0,0,0);h.run();assertArrayEquals(expected,h.slot().copyBytes());
        assertTrue(h.calls.isEmpty());assertTrue(h.fm.isEmpty());assertEquals(2,h.draws.size());
    }

    private static List<CdObjectVisualAssets> visuals(CdCollapsePlatform.Assets a){return List.of(a.collapsePlatform1(),a.collapsePlatform2(),a.collapsePlatform3(),a.collapsePlatform4());}
    private static CdCollapsePlatform.DespawnGlobals world(){return new CdCollapsePlatform.DespawnGlobals(0,0,X0);}
    private static int word(byte[] b,int at){return ((b[at]&255)<<8)|(b[at+1]&255);}
    private static void word(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}
    private static void putLong(byte[] b,int at,int v){word(b,at,v>>>16);word(b,at+2,v);}
    private static CdObjectState dirty(CdObjectState s){
        for(var f:CdObjectState.ByteField.values())s=s.moveByte(f,0x53+7*f.byteOffset());
        for(var f:CdObjectState.WordField.values())s=s.moveWord(f,0xA153+0x103*f.byteOffset());
        for(var f:CdObjectState.LongField.values())s=s.moveLong(f,0xA1536729+0x01030507*f.byteOffset());return s;
    }
    private static void assertPool(CdObjectPool.Snapshot before,CdObjectPool pool,Set<Integer> changed){
        for(var s:before.slots())if(!changed.contains(s.identity().wholeIndex()))assertEquals(s,pool.state(s.identity().wholeIndex()));
    }
    private static CdSolidObject.Player player(int y,int speed,int flags,int stand){
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState((X0<<16)|0x8000,y,speed,0x100,0,
                2,0x10,flags,4,9,19,0,0,1,0,0,0,0,0x56,0x78),0x780,2,0,0),1,0,stand);
    }
    private static final class Harness {
        final CdObjectPlacementRuntime placement;final CdCollapsePlatform body;
        final List<CdCollapsePlatform.SolidCall> calls=new ArrayList<>();final List<CdObjectState> draws=new ArrayList<>();
        final List<Integer> fm=new ArrayList<>();final List<String> order=new ArrayList<>();
        CdSolidObject.Player player=player(0x01001234,0,2,0);CdSolidObject.Globals globals=new CdSolidObject.Globals(0,4,0x12,0x34);
        CdCollapsePlatform.Result result;
        Harness(CdR11bAssets actual,CdCollapsePlatform.Assets data){
            byte[] saved=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];for(int i=0;i<saved.length;i++)saved[i]=(byte)(17*i+3);
            placement=CdObjectPlacementRuntime.fromAssets(actual,CdAssetBytes.copyOf(saved));seed(0x80,0);
            body=new CdCollapsePlatform(placement,data,solid(),this::commit,(pool,identity)->{
                order.add("draw");var s=pool.state(identity.wholeIndex());draws.add(s);pool.write(s.moveByte(SPRITE_FLAGS,s.spriteFlags()&0x7F));
            },sound->{order.add("fm:"+sound);fm.add(sound);});
        }
        CdObjectPool pool(){return placement.pool();}CdObjectState slot(){return pool().state(WHOLE);}
        void seed(int subtype,int routine){pool().write(dirty(slot()).moveByte(ID,0x20).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype)
                .moveByte(SUBTYPE2,0xD7).moveByte(SPRITE_FLAGS,0x20).moveByte(FLAGS,0x50).moveByte(SAVED_FLAGS_ID,0)
                .moveWord(X,X0).moveWord(Y,Y0).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678));}
        void main(int subtype){seed(subtype,2);int index=subtype&15;byte[] sizes=subtype<128?SIZE1:SIZE2;int at=word(sizes,index*2);
            pool().write(slot().moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(PRIORITY,3).moveWord(TILE,0x44BE)
                    .moveLong(MAPPINGS,subtype<128?ADDRESSES[0]:ADDRESSES[1]).moveByte(WIDTH,((sizes[at]&255)+1)*8)
                    .moveByte(X_RADIUS,((sizes[at]&255)+1)*8).moveByte(Y_RADIUS,(Math.abs(sizes[at+1])+1)*8+2)
                    .moveWord(X_VELOCITY,0).moveWord(Y_VELOCITY,0));}
        void dirtyFree(){for(int i=32;i<128;i++)if(i!=WHOLE)pool().write(dirty(pool().state(i)).moveByte(ID,0).moveByte(COLLAPSE_SOLID_MARKER,1));}
        void contact(int speed,int flags,int stand){player=player(((Y0-slot().unsignedByte(Y_RADIUS)-19)<<16)|0x1234,speed,flags,stand);}
        CdCollapsePlatform.Inputs inputs(){return new CdCollapsePlatform.Inputs(player,new CdSolidObject.Gates(0,0),globals);}
        CdCollapsePlatform.Result run(){return result=body.run(WHOLE,world(),inputs());}
        List<CdCollapsePlatform.Site> sites(){return calls.stream().map(CdCollapsePlatform.SolidCall::site).toList();}
        void commit(CdCollapsePlatform.SolidCall call){
            pool().validateRestore(call.actualCut());assertPool(call.actualCut(),pool(),Set.of());
            assertEquals(player,call.expectedPlayer());assertEquals(globals,call.expectedGlobals());
            CdObjectState source;List<CdObjectState> others;CdSolidObject.Player next;CdSolidObject.Globals nextGlobals;
            if(call.outcome()instanceof CdSolidObject.Completed done){var r=done.completedResult();source=r.object();others=r.otherSlots();next=r.player();nextGlobals=r.globals();}
            else{var p=((CdSolidObject.NativeStopped)call.outcome()).prefix();source=p.object();others=p.otherSlots();next=p.player();nextGlobals=p.globals();}
            pool().validateWrite(source);others.forEach(pool()::validateWrite);calls.add(call);order.add("solid:"+call.site());
            pool().write(source);others.forEach(pool()::write);player=next;globals=nextGlobals;
        }
    }
    /** Existing public-format Solid fixture: empty floor at the fixed component ingress, no probe fake. */
    private static CdSolidObject solid(){
        byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50];fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)2);
        var terrain=CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,new byte[900],new byte[256],new byte[4096],new byte[4096]);
        byte[] animations=new byte[578];for(int i=0;i<60;i++)word(animations,i*2,120);animations[122]=(byte)255;
        byte[] maps=new byte[398],dplcs=new byte[398];for(int i=0;i<198;i++){word(maps,i*2,396);word(dplcs,i*2,396);}
        var visual=CdSonicVisualAssets.decode(maps,dplcs,new byte[69472]);byte[] atan=new byte[257];for(int i=0;i<=256;i++)atan[i]=(byte)(i/8);
        var assets=CdR11aPlayerAssets.decode(animations,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)255},new byte[4],new byte[640],atan,visual);
        return new CdSolidObject(new CdPlayerMovement(terrain,assets));
    }
    private String retainedRoot;private CdR11bAssets retained;
    private CdR11bAssets retail()throws Exception{
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Original USA cases require soniccd.media.root");assertFalse(root.isBlank());
        if(retained!=null&&root.equals(retainedRoot))return retained;Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));}
        retainedRoot=root;return retained;
    }
}
