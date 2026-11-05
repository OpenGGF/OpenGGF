package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source-only metadata tests, authored before both owned production mirrors existed; all Java UNRUN.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff PP Swing/Main.asm:71-79,83-135,161-197 and Data/Mappings.asm:5-24.
 * Exact literals/consumer operands reuse authenticated original-USA research; no motion/Solid/child/current/pixel claim.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aSwingAssets {
    private static final HexFormat HEX=HexFormat.of();
    private static final CdR12aProfile.Asset[] KEYS={SWING_MOTION,MAP_SPR_SWING};
    private static final String MOTION="c00000080200fe00c00000020100ff00c000fffeff000100a00000030180fe808000fffeff000100800000020100ff008000ffffff0000008000000101000000";
    private static final String MAPPINGS="0006000c001801f8050000f802f8090008e8f8090808000001f8050004f8";
    private static final String[] HASHES={"70b18bc828edc56e0a4157ccb2b124aa274914d0e898a9a852239d5cade58eb8","22d182c8037a55534a03b0f9cf2d90691c3161180ddd68d54eae822a3cfab68a"};

    @Test void appendOnlyDescriptorsPreserveTheCompleteMovingSpringPrefixAndOriginalDomain() throws Exception {
        var o=retail().terrain().objects();assertEquals(146,CdR12aProfile.Asset.values().length);
        assertEquals(142,ANI_MOVING_SPRING.ordinal());assertEquals(143,MAP_SPR_MOVING_SPRING.ordinal());
        assertEquals(List.of(SWING_MOTION,MAP_SPR_SWING),Arrays.asList(CdR12aProfile.Asset.values()).subList(144,146));
        int[] offsets={0xEBC2,0xED60},lengths={64,30};
        for(int n=0;n<2;n++){var key=KEYS[n];var range=o.profile().range(key);assertEquals(144+n,key.ordinal());
            assertEquals(offsets[n],range.fileOffset());assertEquals(lengths[n],range.length());assertEquals(0x200000+offsets[n],range.nativeAddress());
            assertEquals(offsets[n]+lengths[n],range.endExclusive());assertEquals(CdNativeFile.R12A,range.file());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,range.domain());}
        assertEquals(0xEC02,o.profile().range(SWING_MOTION).endExclusive());
    }

    @Test void completeNativeLiteralsHashesAndCachedReadIdentitySurviveTheCatalogueCueClose() throws Exception {
        var r=retail();var o=r.terrain().objects();String[] literals={MOTION,MAPPINGS};
        for(int n=0;n<2;n++){var key=KEYS[n];var view=o.view(key);assertSame(view,o.view(key));assertArrayEquals(HEX.parseHex(literals[n]),view.copy());
            assertEquals(HASHES[n],CdR11aProfile.sha256(view.copy()));assertArrayEquals(r.original().readFile(key.fileOffset(),key.length()),view.copy());
            assertSame(o.profile(),r.terrain().profile());assertSame(o.identity(),r.terrain().identity());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,o.identity().domain());}
        assertEquals("6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7",o.identity().mmdSha256());
    }

    @Test void allEightMotionRowsRetainEveryRawPhaseStepMaximumAndMinimumWord() throws Exception {
        var raw=retail().terrain().objects().view(SWING_MOTION);
        int[][] words={{0xC000,8,0x200,0xFE00},{0xC000,2,0x100,0xFF00},{0xC000,0xFFFE,0xFF00,0x100},{0xA000,3,0x180,0xFE80},
                {0x8000,0xFFFE,0xFF00,0x100},{0x8000,2,0x100,0xFF00},{0x8000,0xFFFF,0xFF00,0},{0x8000,1,0x100,0}};
        assertEquals(64,raw.length());for(int row=0;row<8;row++)for(int word=0;word<4;word++)assertEquals(words[row][word],raw.unsignedWord(row*8+word*2));
        for(int subtype=0;subtype<128;subtype++){int selected=(subtype&0xF0)>>>1;assertEquals(words[subtype>>>4][0],raw.unsignedWord(selected));}
        for(int high=8;high<16;high++){int reached=high*8;assertThrows(IndexOutOfBoundsException.class,()->raw.unsignedWord(reached));}
        assertEquals(-2,(short)raw.unsignedWord(2*8+2));assertEquals(-1,(short)raw.unsignedWord(6*8+2));
        assertThrows(IndexOutOfBoundsException.class,()->raw.unsignedByte(64));assertThrows(IndexOutOfBoundsException.class,()->raw.unsignedWord(63));
    }

    @Test void actualNativeDispatchMapTileAllocatorMotionAndHighPhaseConsumersRemainLiteral() throws Exception {
        var file=retail().original();assertEquals("0020eb48",HEX.formatHex(file.readFile(0x3D68,4)));
        assertEquals("700010280024303b00164ebb00124eb900203986302800364ef9002076f2",HEX.formatHex(file.readFile(0xEB48,30)));
        assertEquals("009c0154",HEX.formatHex(file.readFile(0xEB66,4)));
        assertEquals("217c0020ed600004",HEX.formatHex(file.readFile(0xEC12,8)));assertEquals("70114eb90020cc50",HEX.formatHex(file.readFile(0xEC1A,8)));
        assertEquals("4eb9002076b8",HEX.formatHex(file.readFile(0xEC54,6)));assertEquals("43faff1e",HEX.formatHex(file.readFile(0xECA2,4)));
        assertEquals(0xEBC2,0xECA4+(short)CdAssetBytes.copyOf(file.readFile(0xECA4,2)).unsignedWord(0));
        assertEquals("6100002e",HEX.formatHex(file.readFile(0xECC6,4)));assertEquals("6100fe78",HEX.formatHex(file.readFile(0xECF0,4)));
        assertEquals("1028003a",HEX.formatHex(file.readFile(0xED30,4)));assertEquals("4eb90020085a",HEX.formatHex(file.readFile(0xED34,6)));
        assertEquals("43f8d000",HEX.formatHex(file.readFile(0xEB6A,4)));assertEquals("43f8d040",HEX.formatHex(file.readFile(0xEB70,4)));
    }

    @Test void allThreeStaticFramesKeepAllFourRawPiecesAndExactUnpaddedFrameBodies() throws Exception {
        var o=retail().terrain().objects();var visual=o.swing();var literal=HEX.parseHex(MAPPINGS);int[] roots={6,12,24},counts={1,2,1};
        assertEquals(0,visual.mappingTableOffset());assertEquals(3,visual.mappingFrames().size());assertArrayEquals(literal,visual.mappings().copy());
        int pieces=0,maximum=0;for(int n=0;n<3;n++){var frame=visual.mappingFrames().get(n);assertEquals(roots[n],frame.relativeOffset());assertEquals(counts[n],frame.pieces().size());
            assertArrayEquals(Arrays.copyOfRange(literal,roots[n],roots[n]+1+5*counts[n]),frame.bytes().copy());
            for(int j=0;j<counts[n];j++){int at=roots[n]+1+5*j;var p=frame.pieces().get(j);pieces++;
                assertEquals((byte)literal[at],p.y());assertEquals(literal[at+1]&255,p.sizeByte());assertEquals(((literal[at+2]&255)<<8)|(literal[at+3]&255),p.rawTileWord());assertEquals((byte)literal[at+4],p.x());
                maximum=Math.max(maximum,p.tileOffset()+p.tileCount());}}
        assertEquals(4,pieces);assertEquals(14,maximum);assertEquals(0,literal[23]);assertEquals(11,visual.mappingFrames().get(1).bytes().length());
        assertEquals(30,roots[2]+visual.mappingFrames().get(2).bytes().length());
    }

    @Test void nativeSizesOffsetsFlipsAndPalettePriorityBitsRequireTheSameFourteenTiles() throws Exception {
        var v=retail().terrain().objects().swing();var chain0=v.mappingFrames().get(0).pieces().getFirst();var platform=v.mappingFrames().get(1).pieces();var chain2=v.mappingFrames().get(2).pieces().getFirst();
        assertEquals(2,chain0.widthTiles());assertEquals(2,chain0.heightTiles());assertEquals(0,chain0.tileOffset());assertEquals(4,chain2.tileOffset());
        assertEquals(List.of(-24,0),platform.stream().map(CdObjectVisualAssets.Piece::x).toList());assertEquals(3,platform.getFirst().widthTiles());assertEquals(2,platform.getFirst().heightTiles());
        assertEquals(0x0008,platform.get(0).rawTileWord());assertEquals(0x0808,platform.get(1).rawTileWord());
        for(var f:v.mappingFrames())for(var p:f.pieces()){assertEquals(-8,p.y());assertEquals(0,p.rawTileWord()&0xF000);assertTrue(p.tileOffset()+p.tileCount()<=14);}
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(HEX.parseHex(MAPPINGS),3,13));
    }

    @Test void staticVisualHasNoAnimationTableOrScriptAndRemainsAnImmutableCachedValue() throws Exception {
        var o=retail().terrain().objects();var v=o.swing();assertSame(v,o.swing());assertEquals(0,v.animationTable().length());assertTrue(v.animations().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->v.mappingFrames().clear());assertThrows(UnsupportedOperationException.class,()->v.mappingFrames().get(1).pieces().clear());
        assertThrows(UnsupportedOperationException.class,()->v.animations().clear());var copy=v.mappings().copy();copy[6]=0;assertArrayEquals(HEX.parseHex(MAPPINGS),v.mappings().copy());
        assertThrows(ReadOnlyBufferException.class,()->v.mappings().buffer().put(0,(byte)0));
    }

    @Test void lazySwingCacheReadsOnlyMapMetadataAndLeavesRawMotionAndEveryArtCacheUncreated() throws Exception {
        var r=retail();var o=new CdR12aObjectAssets(r.terrain(),r.original());assertNull(field(o,"swing"));assertEquals(Map.of(),field(o,"views"));
        var v=o.swing();assertSame(v,field(o,"swing"));assertSame(v,o.swing());assertEquals(Set.of(MAP_SPR_SWING),((Map<?,?>)field(o,"views")).keySet());
        for(String name:List.of("decoded","javaInputs","nativeInputs"))assertEquals(Map.of(),field(o,name));assertNull(field(o,"plcs"));
        var motion=o.view(SWING_MOTION);assertSame(motion,o.view(SWING_MOTION));assertEquals(Set.of(SWING_MOTION,MAP_SPR_SWING),((Map<?,?>)field(o,"views")).keySet());
        assertSame(v,o.swing());for(String name:List.of("decoded","javaInputs","nativeInputs"))assertEquals(Map.of(),field(o,name));assertNull(field(o,"plcs"));
    }

    @Test void rawViewsAreFiniteReadOnlyAndCannotBecomeReaderSources() throws Exception {
        var o=retail().terrain().objects();for(var key:KEYS){var view=o.view(key);var before=view.copy();var copy=view.copy();copy[0]^=1;assertArrayEquals(before,view.copy());assertSame(view,o.view(key));
            assertThrows(ReadOnlyBufferException.class,()->view.buffer().put(0,(byte)0));assertThrows(IndexOutOfBoundsException.class,()->view.unsignedByte(view.length()));
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(key));for(var kind:CdR12aObjectAssets.ReaderKind.values())assertThrows(IllegalArgumentException.class,()->o.readerView(key,kind));
            assertTrue(o.plcLists().stream().flatMap(l->l.entries().stream()).noneMatch(e->e.source()==key));}
    }

    @Test void existingSwingReaderAndEveryNativePlcSourceRemainUnchangedAcrossMetadataAccess() throws Exception {
        var o=retail().terrain().objects();var readers=o.readerProfiles();var plcs=o.plcLists();var readerValues=readers.toString();var plcValues=plcs.toString();var art=o.view(ART_SWING);
        o.swing();o.view(SWING_MOTION);assertSame(readers,o.readerProfiles());assertSame(plcs,o.plcLists());assertSame(art,o.view(ART_SWING));assertEquals(readerValues,o.readerProfiles().toString());assertEquals(plcValues,o.plcLists().toString());
        assertEquals(35,readers.size());assertEquals(19,plcs.size());var profile=o.readerProfile(ART_SWING);assertEquals(338,profile.sourceBlobRange().length());assertEquals(0x238C2A,profile.sourceBlobRange().nativeAddress());
        assertEquals(448,profile.outputBytes());assertEquals(338,profile.javaInputRange().length());assertEquals(338,profile.nativeInputRange().length());assertEquals(338,profile.javaConsumedInputBytes());
        assertEquals("1a519480f29f88b56ddec7c1e881281a098705e194f9ca4e0f451fe7f85ca621",CdR11aProfile.sha256(art.copy()));
        int[] ids={2,13,14,15,17,4,5,6,7},rows={8,8,8,8,8,7,6,7,6},operands={0xF16A,0xF16A,0xF16A,0xF16A,0xF16A,0xF1A8,0xF1E0,0xF21E,0xF256};
        for(int n=0;n<ids.length;n++){var e=plcs.get(ids[n]).entries().get(rows[n]);assertEquals(ART_SWING,e.source());assertEquals(0x8300,e.destinationWord());assertEquals(profile.sourceBlobRange(),e.sourceRange());assertEquals("00238c2a8300",HEX.formatHex(retail().original().readFile(operands[n],6)));}
    }

    @Test void metadataDoesNotBroadenLegacySelectorsOrAddASwingMotionGetter() throws Exception {
        var o=retail().terrain().objects();var raw=o.view(OBJECT_TILE_IDS);assertEquals(72,(short)raw.unsignedWord(0x11*2));assertEquals(74,CdObjectTileIds.endOfBody(raw,0x11));assertEquals(0x418,raw.unsignedWord(72));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(raw.copy(),0x11,0));
        assertThrows(IndexOutOfBoundsException.class,()->raw.unsignedWord(74));
        var methods=Arrays.stream(CdR12aObjectAssets.class.getDeclaredMethods()).filter(m->m.getName().startsWith("swing")).toList();assertEquals(1,methods.size());var method=methods.getFirst();
        assertEquals("swing",method.getName());assertEquals(0,method.getParameterCount());assertEquals(CdObjectVisualAssets.class,method.getReturnType());assertTrue(Modifier.isPublic(method.getModifiers()));assertTrue(Modifier.isSynchronized(method.getModifiers()));
    }

    @Test void actualProgramChecksumStillRejectsCorruptionInsideEitherNewFiniteSpan() throws Exception {
        var r=retail();for(var key:KEYS){byte[] changed=r.original().readFile(0,262144);changed[key.fileOffset()]^=1;assertThrows(IOException.class,()->r.terrain().profile().verifyProgram(CdNativeFile.R12A,CdMmdImage.Domain.ORIGINAL_WORD_RAM,CdMmdImage.parse(changed)));}
    }

    private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private record Retail(CdR12aTerrainAssets terrain,CdMmdImage original) { }
    private Retail retained;
    private Retail retail()throws Exception {
        if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Configure original CD media root");assertFalse(root.isBlank());Path cue;
        try(var entries=Files.list(Path.of(root).resolve("USA"))){var cues=entries.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=new Retail(CdR12aTerrainAssets.fromPrograms(source),source.mmd(CdNativeFile.R12A));}return retained;
    }
}
