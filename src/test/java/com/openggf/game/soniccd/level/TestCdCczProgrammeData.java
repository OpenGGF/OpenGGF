package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import java.nio.file.Path;
import java.util.*;
import java.security.MessageDigest;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
/** Original-media CCZ DATA only; no source-family/Frame/World/terrain admission. */
class TestCdCczProgrammeData {
    static CdProgramAssets open()throws Exception {var root=System.getProperty("soniccd.media.root");assertNotNull(root);return CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty());}
    private static String expectedSha(CdStagePayload p) {return switch(p) {
        case R31B -> "2a7dbe5463233d4cfb69b34dfcf480f8bec93407918d53883ee1d96771247862";
        case R31C -> "cdb48963c2661c537c19986f2f360ce89bc39d8dab0974d05ebca6557fefa987";
        case R31D -> "6980ad899736183083261cd57e05d897f76e01d9da517e9a07d82145b3af229d";
        case R32B -> "932883b324210ab8445ba1faf205edcf13d6429e9a83df678e7644816a5016b4";
        case R32C -> "adbec88d9f1f0c0baad3d007200be83e9ae40625a074646c5280e2749bedbe25";
        case R32D -> "dee68dbf40c7de6324a2736b2214fc5a2633533db64491ac4453144edbf69347";
        case R33C -> "5ac6cf2d7270d563b6f06ffa4b4915df34e3db2e2ad3a386f505fc1b140be402";
        case R33D -> "badd72ff19023c0f4e7f993a45f2b8ec220c4782a33fbe4555af5c0cb3a9c469";
        default -> throw new IllegalArgumentException("Missing CCZ programme only");
    };}
    private static CdCczDataLayout.Programme declaration(CdStagePayload p){return CdCczDataLayout.Programme.valueOf(p.name());}
    record Golden(int ranges,int products,String raw,String product,String input,String tables,String visual,int plcs,int bodies,int palettes,int cycles,int placements,int active,boolean parser,int nulls,int dispatch,int fragments) {}
    private static Golden golden(CdStagePayload p){return switch(p){
        case R31B -> new Golden(90,43,"71cddb6b61cd6bf838513bf5e2918d6f282723a4605da7377e126ff5781b59eb","bc4e71543698398ab0e0e4cdf990b78860a7860b952294a478767487ddbdaee5","c2bdf63b34db927af13566fd51303f64bb6d4348fec8d4af83176e8069db792e","b771a6e916c32f6cc549d68afbbf26dcc9e11d8262c2380555f1cc4e9af8937d","db3673f45361d231035a239a6779b27b697f8bf8545a8d85c78b49ff2236e2c1",19,14,5,0,517,376,false,12,60,0);
        case R31C -> new Golden(97,45,"776bdf0937667e47eb4e78ec04a0570648d56da62db8e29acc1d16f667d11b2a","9c3853ece90f43991759b4757a8a972189d31fc63e8989498956084b5b1ccfdc","be0ecc063a8f5e3ac45734915b0461f1192e2ab457fbc4491bf3504815b5b3d6","c690078773a0c3025b8f7152a6b931746345c5a016a4ffc02761c699037715e7","db3673f45361d231035a239a6779b27b697f8bf8545a8d85c78b49ff2236e2c1",19,5,8,2,517,382,false,12,60,0);
        case R31D -> new Golden(98,45,"a89038cfcf65c2755f08bdc73b5f564d12309e198c520e9a1e0112cc0ba78a8f","e2265529b9774129197f4c5fcbbedea9fd722a795b4e3762b4888ad19b619e12","68b4de028dac3f56ff2e0870f1b94cba7149d40472a49cee14a48ff6458744bf","dabae7cf492b383923613d741b22479254afe8777e4144b1a7446f5ab7e002e7","db3673f45361d231035a239a6779b27b697f8bf8545a8d85c78b49ff2236e2c1",19,5,7,3,517,382,false,12,60,0);
        case R32B -> new Golden(115,55,"19408cdcdc0afcabe449da67136587508d0606f64b6a1226d34aa770314e2ff9","ed2074bdea09de8354a7be77c328bea7a9a3861b87ac7d7898f6802a75acc245","3f4c5709c20f8c67f5dc49f15ca5c1199d9c81caa7945a242d242c9862ea7ffd","19199922f8a8dd096bf01c277bf4361ddab5db3e0b159c9fe92bb553b9c102c0","db3673f45361d231035a239a6779b27b697f8bf8545a8d85c78b49ff2236e2c1",19,12,5,0,454,294,true,11,60,1);
        case R32C -> new Golden(112,45,"63c780586c67a51cd737f4eb04ad5e90cd9647413298666c7160969e012f6f46","9c3853ece90f43991759b4757a8a972189d31fc63e8989498956084b5b1ccfdc","be0ecc063a8f5e3ac45734915b0461f1192e2ab457fbc4491bf3504815b5b3d6","b0f2906d59b35dd2acb4ccad658b8a8c4c6b1ca79255483cc8f4fdbe63ff33a6","db3673f45361d231035a239a6779b27b697f8bf8545a8d85c78b49ff2236e2c1",19,5,8,2,454,329,true,11,60,0);
        case R32D -> new Golden(112,45,"03587a088d25d3169af9192585da45f67215aceac20e7c62434b809c1b559206","dd1a1b751a10a6d80080edc09bb298d31b143e16c875028b03c525cb7544f913","d61b73fc0f83afffcfbe729f09f9bef279eddbc6fea595806dc2fe35b2589cfc","51f4afead5666749f4151d737e86308454721a1540e31b23fea20a4d9199bf36","db3673f45361d231035a239a6779b27b697f8bf8545a8d85c78b49ff2236e2c1",19,5,7,3,454,329,true,11,60,0);
        case R33C -> new Golden(98,45,"39f420dfc6df86c69ac3e6ab2cba302b5c000f0e2ee9f1a9931849d80eff22e4","f33163992c2780899da00ae0724720a4e46e6719736ca37c51d5c8667e916075","f0a887158491fbb50cf345b2249ff64202f22939534a9b1128e40f862c0b3610","30b8d0fcfea77ffcc036bdec4b5b4a731d5eddd37fba2292ab6d0a61a7973c8a","db3673f45361d231035a239a6779b27b697f8bf8545a8d85c78b49ff2236e2c1",17,5,8,2,61,61,true,7,63,0);
        case R33D -> new Golden(99,45,"e7b6b04978fd0781991e277264e01e6e8f68af4ee4170df7683db53978fe4a06","c953872eb7c5b1d91fa9e0616abb0c17821ac37c374bb245b3260a809f0df74e","5189a94297240d9ba7d3cc66bdf2d61909495858f3bec55178183169e4d3da3b","f09d53aef63cd02edd32caa429d99c98b41a06e7889290dcedbb6f8bfb473756","db3673f45361d231035a239a6779b27b697f8bf8545a8d85c78b49ff2236e2c1",17,5,7,3,61,61,true,7,63,0);
        default -> throw new IllegalArgumentException("Missing CCZ fixture only");
    };}
    @ParameterizedTest @EnumSource(value=CdStagePayload.class,names={"R31B","R31C","R31D","R32B","R32C","R32D","R33C","R33D"})
    void actualOriginalMissingProgrammeOpensCommonData(CdStagePayload p)throws Exception {
        try(var actual=open()) {var image=actual.mmd(p.file());assertEquals(expectedSha(p),CdR11aProfile.sha256(image.readFile(0,image.fileBytes())));assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,image.originalWordRam().domain());var r=CdProgrammeReader.open(actual,p.stage());assertEquals(p,r.definition().payload());assertTrue(r.definition().r3().isEmpty());assertTrue(r.definition().tt().isEmpty());assertSame(r.sonic(),r.sonic());assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_167,r.sonic().sourceFormat());}
    }
    @ParameterizedTest @EnumSource(value=CdStagePayload.class,names={"R31B","R31C","R31D","R32B","R32C","R32D","R33C","R33D"})
    void originalRawRangesAndRealOperandsRetainCanonicalKeyIdentity(CdStagePayload p)throws Exception {
        try(var actual=open()) {var r=CdProgrammeReader.open(actual,p.stage());var d=declaration(p);var g=golden(p);var h=MessageDigest.getInstance("SHA-256");assertEquals(g.ranges,d.ranges().size());
            for(var k:d.ranges()) {var v=r.view(k);assertSame(v,r.view(k));assertArrayEquals(actual.mmd(p.file()).readFile(k.fileOffset(),k.length()),v.copy());h.update(v.copy());}
            assertEquals(g.raw,hex(h));for(var o:d.operands())assertEquals(o.hex(),HexFormat.of().formatHex(actual.mmd(p.file()).readFile(o.fileOffset(),o.hex().length()/2)));
            var k=d.range("PlayerSprites");var forgery=new CdCczDataLayout.Range(k.key(),k.fileOffset(),k.length(),k.sha256());assertEquals(k,forgery);assertThrows(IllegalArgumentException.class,()->r.view(forgery));
            var other=CdCczDataLayout.Programme.values()[d.ordinal()==0?1:0];assertThrows(IllegalArgumentException.class,()->r.view(other.range("PlayerSprites")));
        }
    }
    @ParameterizedTest @EnumSource(value=CdStagePayload.class,names={"R31B","R31C","R31D","R32B","R32C","R32D","R33C","R33D"})
    void allCanonicalJavaAndIndependentNativeReadContractsUseSharedDecoder(CdStagePayload p)throws Exception {
        try(var actual=open()) {var r=CdProgrammeReader.open(actual,p.stage());var d=declaration(p);var h=MessageDigest.getInstance("SHA-256");var input=MessageDigest.getInstance("SHA-256");var sources=r.definition().sources();assertEquals(golden(p).products,sources.size());
            for(var source:sources) {var key=source.key();var decoded=r.decoded(key);assertSame(decoded,r.decoded(key));h.update(decoded.bytes().copy());ints(h,decoded.consumedInputBytes());input.update(r.canonicalInput(key).copy());input.update(r.javaInput(key).copy());input.update(r.nativeInput(key).copy());assertSame(r.javaInput(key),r.javaInput(key));assertSame(r.nativeInput(key),r.nativeInput(key));}
            assertEquals(golden(p).product,hex(h));assertEquals(golden(p).input,hex(input));
            if(p==CdStagePayload.R32B) {var fragment=d.range("R32BRetainedR12CGfxNemesisTail");assertEquals(4614,r.view(fragment).length());assertThrows(IllegalArgumentException.class,()->r.decoded(fragment));assertThrows(IllegalArgumentException.class,()->r.nativeInput(fragment));}
        }
    }
    @ParameterizedTest @EnumSource(value=CdStagePayload.class,names={"R31B","R31C","R31D","R32B","R32C","R32D","R33C","R33D"})
    void actualPlcAliasesPalettesSectionsCyclesAndBackgroundUseSameParser(CdStagePayload p)throws Exception {
        try(var actual=open()) {var r=CdProgrammeReader.open(actual,p.stage());var t=r.loadTables();assertSame(t,r.loadTables());var g=golden(p);var h=MessageDigest.getInstance("SHA-256");assertEquals(g.plcs,t.plcs().size());assertEquals(g.palettes,t.palettes().size());assertEquals(g.cycles,t.cycles().size());var bodies=new HashMap<Integer,CdProgrammeLoadTables.Body>();
            for(var plc:t.plcs()) {var b=plc.body();var previous=bodies.putIfAbsent(b.fileOffset(),b);if(previous!=null)assertSame(previous,b);ints(h,plc.nativeId(),b.fileOffset(),b.entries().size());for(var row:b.entries())ints(h,(int)row.sourceNativeAddress(),row.vramAddress(),row.outputBytes());}assertEquals(g.bodies,bodies.size());
            for(var pal:t.palettes()){ints(h,pal.nativeId(),(int)pal.sourceNativeAddress(),pal.destinationWord(),pal.longwordCounter());h.update(pal.bytes().copy());}
            for(var cycle:t.cycles()){assertEquals(CdProgrammeDefinition.CycleKind.STANDARD,cycle.kind());h.update(cycle.script().copy());h.update(cycle.colors().copy());}assertEquals(g.tables,hex(h));
            if(p.name().endsWith("B")){assertInstanceOf(CdProgrammeDefinition.CameraSections.class,t.sectionProgramme());assertEquals(CdProgrammeDefinition.CycleProgramme.SOURCE_RTS,t.cycleProgramme());}
            else if(p==CdStagePayload.R33C||p==CdStagePayload.R33D){assertSame(CdProgrammeDefinition.NoSections.INSTANCE,t.sectionProgramme());assertThrows(UnsupportedOperationException.class,()->r.loadView(CdProgrammeReader.LoadRole.SECTIONS));}
            else assertEquals(new CdProgrammeDefinition.InitQueueUpdateRts(2),t.sectionProgramme());
            var bg=assertInstanceOf(CdProgrammeLoadTables.BackgroundTables.class,t.backgroundProgramme());assertEquals(List.of(0xFFF708,0xFFF708,0xFFF710,0xFFF718),bg.tables().getFirst().values());assertTrue(bg.rowSelectors().isPresent());
        }
    }
    @ParameterizedTest @EnumSource(value=CdStagePayload.class,names={"R31B","R31C","R31D","R32B","R32C","R32D","R33C","R33D"})
    void realVisualAndPlacementBytesRetainEveryFramePhysicalRowMaskAndNull(CdStagePayload p)throws Exception {
        try(var actual=open()) {var r=CdProgrammeReader.open(actual,p.stage());var d=declaration(p);var g=golden(p);var v=r.sonic();var h=MessageDigest.getInstance("SHA-256");assertEquals(167,v.mappingFrames().size());assertEquals(167,v.dplcFrames().size());assertEquals(57120,v.rawArt().length());
            for(int i=0;i<167;i++){var mp=v.mappingFrames().get(i);var dp=v.dplcFrames().get(i);ints(h,i,mp.relativeOffset(),mp.pieceCount(),dp.relativeOffset(),dp.pieces().size(),dp.outputBytes());}assertEquals(g.visual,hex(h));
            var raw=r.view(d.range("Placement0"));assertEquals(g.placements*8+8,raw.length());int active=0;for(int i=0;i<g.placements;i++){int at=i*8;int id=raw.unsignedByte(at+4);assertTrue((id&127)>=1&&(id&127)<=g.dispatch);if((raw.unsignedByte(at+6)&(p.name().endsWith("B")?32:128))!=0)active++;}assertEquals(g.active,active);assertEquals(65535,raw.unsignedWord(g.placements*8));
            // StageObjectsNull is three WORDs; the following GetOff instruction is not placement DATA.
            var nullKey=d.range("Placement1");var nullList=r.view(nullKey);
            assertEquals(6,nullKey.length(),"native six-byte StageObjectsNull extent");
            assertEquals(d.range("ObjectLayouts").fileOffset()+d.range("ObjectLayouts").length(),nullKey.fileOffset()+nullKey.length());
            assertArrayEquals(HexFormat.of().parseHex("ffff00000000"),nullList.copy());
            assertEquals("c5d0356d6e6e9d0e6a0fdc00a0357d822cbbf76d257e5d702f8ccf13f70dc8fa",CdR11aProfile.sha256(nullList.copy()));
            assertThrows(IndexOutOfBoundsException.class,()->nullList.unsignedWord(6));
            assertEquals(d.range("PlacementDirectory").fileOffset()+12,d.range("Placement0").fileOffset());
            assertArrayEquals(HexFormat.of().parseHex("ffff000000000000"),actual.mmd(p.file()).readFile(d.range("Placement0").fileOffset()-8,8));
            var layouts=r.view(d.range("ObjectLayouts"));if(g.parser){var decoded=CdObjectPlacements.decodeLayout(layouts.copy());assertEquals(g.placements,decoded.records().size());for(var row:decoded.records()){int at=row.index()*8;assertEquals(raw.unsignedWord(at),row.centreX());assertEquals(raw.unsignedWord(at+2),row.packedY());assertEquals(raw.unsignedByte(at+4),row.encodedId());assertEquals(raw.unsignedByte(at+6),row.periodFlags());}}
            else assertThrows(IllegalArgumentException.class,()->CdObjectPlacements.decodeLayout(layouts.copy())); // Preserve owner/order boundary, never sort/relabel rows.
            var dispatch=r.view(d.range("ObjectIndex"));assertEquals(g.dispatch*4,dispatch.length());int nullCount=0;for(int i=0;i<g.dispatch;i++)if(dispatch.unsignedLong(i*4)==0x200000L+d.range("NullObject").fileOffset())nullCount++;assertEquals(g.nulls,nullCount);assertEquals("10bc00004e75",HexFormat.of().formatHex(r.view(d.range("NullObject")).copy()));
        }
    }
    @ParameterizedTest @EnumSource(value=CdStagePayload.class,names={"R31B","R31C","R31D","R32B","R32C","R32D","R33C","R33D"})
    void originalFailuresRetryCachesAndUnavailableRolesNeverGrantOtherOwners(CdStagePayload p)throws Exception {
        try(var actual=open()) {var r=spy(CdProgrammeReader.open(actual,p.stage()));var d=declaration(p);var key=d.range("StageBlocks");var fault=new IOException("selected original decoder failure");doThrow(fault).when(r).decoded(key);assertSame(fault,assertThrows(IOException.class,()->r.decoded(key)));doCallRealMethod().when(r).decoded(key);assertSame(r.decoded(key),r.decoded(key));
            assertThrows(UnsupportedOperationException.class,r::terrain);assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);assertThrows(UnsupportedOperationException.class,()->r.role(CdProgrammeReader.Role.ANI));assertThrows(UnsupportedOperationException.class,()->CdStagePlayerSource.fromProgramme(r));
            var wrong=mock(CdProgramAssets.class,delegatesTo(actual));doReturn(actual.mmd(CdNativeFile.R31A)).when(wrong).mmd(p.file());assertEquals("Original USA "+p.name()+" image/header differs",assertThrows(IOException.class,()->CdProgrammeReader.open(wrong,p.stage())).getMessage());
            var input=r.javaInput(key);var contract=r.definition().sources().stream().filter(s->s.key()==key).findFirst().orElseThrow();assertThrows(IOException.class,()->CdNemesisAsset.decode(input.slice(0,contract.javaProduct().orElseThrow().consumed()-1).copy(),contract.output(),contract.capacity()));
        }
    }
    @ParameterizedTest @EnumSource(value=CdStagePayload.class,names={"R31A","R32A"})
    void oldTwoProducersKeepExactEnumKeysRolesCachesAndSourceFamily(CdStagePayload p)throws Exception {
        try(var actual=open()) {var r=CdProgrammeReader.open(actual,p.stage());assertTrue(r.definition().r3().isPresent());Object key=p==CdStagePayload.R31A?CdR31aAssets.Asset.LEVEL_CHUNKS:CdR32aDataLayout.Asset.LEVEL_CHUNKS;assertSame(key,r.definition().r3().orElseThrow().roles().get(CdProgrammeReader.Role.CHUNKS));assertSame(r.view(key),r.role(CdProgrammeReader.Role.CHUNKS));assertSame(r.terrain(),r.terrain());assertSame(r.loadTables(),r.loadTables());assertEquals(p==CdStagePayload.R31A?0x100:0x101,r.ordinaryZoneActWord());assertEquals(19,r.loadTables().plcs().size());}
    }
    private static void ints(MessageDigest h,int...v){for(int i:v)h.update(new byte[]{(byte)(i>>>24),(byte)(i>>>16),(byte)(i>>>8),(byte)i});}
    private static String hex(MessageDigest h){return HexFormat.of().formatHex(h.digest());}
}
