package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdMonitor;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue.FrameMapping;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue.RawPiece;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST; author Java UNRUN. Original R31 metadata only, no handler-PC/World/art/residency grant. */
class TestCdR31aObjectAssets {
    @Test void wrapperIsFinitePrivateSourceBindingWithExactlyTenPublicMethods() {
        assertEquals(0,CdR31aObjectAssets.class.getConstructors().length);
        assertEquals(1,CdR31aObjectAssets.class.getDeclaredConstructors().length);
        assertEquals(List.of("explosionAssets/0","flowerAssets/0","fromAssets/1","hudVisuals/0","identity/0","kamaSickleAssets/0","monitorAssets/0","monitorTime/0",
                        "ownsAssets/1","pointsAssets/0","reservedHudAssets/0","ringData/0","springAssets/0","titleCard/0","titleCardAssets/0"),
                Arrays.stream(CdR31aObjectAssets.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers()))
                        .map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());
        assertThrows(NullPointerException.class,()->CdR31aObjectAssets.fromAssets(null));
        assertThrows(NullPointerException.class,()->CdObjectMappings.fromR31aAssets(null,null));
        assertEquals(67,CdR31aAssets.Asset.values().length);
    }

    @Nested class ActualUsa {
        static CdR31aAssets assets, equalButForeign;
        static CdR31aObjectAssets common;

        @BeforeAll static void originalViewsOnly() throws Exception {
            String root=System.getProperty("soniccd.media.root");
            Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Explicit original-USA media opt-in");
            Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
            try(var media=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
                assets=CdR31aAssets.fromPrograms(media,CdStagePayload.R31A.stage());
                equalButForeign=CdR31aAssets.fromPrograms(media,CdStagePayload.R31A.stage());
            }
            common=CdR31aObjectAssets.fromAssets(assets); // Genuine detached custody after same source closes.
        }

        @Test void actualRootExtentsAndCachedOrdinaryAssetsKeepThisSameAggregate() {
            assertSame(assets.identity(),common.identity());assertTrue(common.ownsAssets(assets));
            assertFalse(common.ownsAssets(null));assertFalse(common.ownsAssets(equalButForeign));
            assertSame(common.hudVisuals(),common.hudVisuals());assertSame(common.monitorTime(),common.monitorTime());
            assertSame(common.titleCard(),common.titleCard());
            var hud=common.reservedHudAssets();assertSame(hud,common.reservedHudAssets());
            assertEquals(0x20A850,hud.hudMappingAddressBits());assertEquals(0x20A538,hud.monitorMappingAddressBits());
            var monitor=common.monitorAssets();assertSame(monitor,common.monitorAssets());
            assertSame(common.monitorTime(),monitor.visual());assertEquals(0x20A538,monitor.mappingsAddress());
            var title=common.titleCardAssets();assertSame(title,common.titleCardAssets());
            assertSame(assets.view(CdR31aAssets.Asset.TITLE_CHARACTERS),title.initData());
            assertEquals(0x20F9A6,title.mappingsAddress());assertEquals(64,title.initData().length());
            assertEquals(0x20A850,assets.range(CdR31aAssets.Asset.HUD_MAPPINGS).nativeAddress());
            assertEquals(0x20A4B0,assets.range(CdR31aAssets.Asset.MONITOR_ANIMATIONS).nativeAddress());
            assertEquals(0x20F966,assets.range(CdR31aAssets.Asset.TITLE_CHARACTERS).nativeAddress());
        }

        @Test void allFourHudFramesRetainComplete168SourceBytesAndLiteralNativePieces() {
            var v=common.hudVisuals();assertEquals(168,v.mappings().length());assertTrue(v.animations().isEmpty());
            assertArrayEquals(assets.view(CdR31aAssets.Asset.HUD_MAPPINGS).copy(),v.mappings().copy());
            assertArrayEquals(new int[]{8,80,96,152},v.mappingFrames().stream().mapToInt(CdObjectVisualAssets.MappingFrame::relativeOffset).toArray());
            assertArrayEquals(new int[]{14,3,11,3},v.mappingFrames().stream().mapToInt(f->f.pieces().size()).toArray());
            assertEquals(new CdObjectVisualAssets.Piece(4,1,0x3D,24),v.mappingFrames().get(1).pieces().get(2));
            for(var f:v.mappingFrames())assertArrayEquals(assets.view(CdR31aAssets.Asset.HUD_MAPPINGS).slice(f.relativeOffset(),1+5*f.pieces().size()).copy(),f.bytes().copy());
        }

        @Test void completeMonitor450AndTenScripts136KeepAllLiteralIndicesAndTerminals() {
            var v=common.monitorTime();assertEquals(450,v.mappings().length());assertEquals(136,v.animationTable().length());
            assertArrayEquals(assets.view(CdR31aAssets.Asset.MONITOR_MAPPINGS).copy(),v.mappings().copy());
            assertArrayEquals(assets.view(CdR31aAssets.Asset.MONITOR_ANIMATIONS).copy(),v.animationTable().copy());
            assertArrayEquals(new int[]{40,62,84,106,128,150,172,194,216,238,260,286,312,338,364,390,416,432,438,444},
                    v.mappingFrames().stream().mapToInt(CdObjectVisualAssets.MappingFrame::relativeOffset).toArray());
            assertArrayEquals(new int[]{4,4,4,4,4,4,4,4,4,4,5,5,5,5,5,5,3,1,1,1},
                    v.mappingFrames().stream().mapToInt(f->f.pieces().size()).toArray());
            assertArrayEquals(new int[]{20,32,44,56,68,80,92,104,116,126},v.animations().stream().mapToInt(CdObjectVisualAssets.AnimationScript::relativeOffset).toArray());
            for(int i=0;i<8;i++)assertEquals(List.of(16,i,i,8,i,i,9,i,i),v.animations().get(i).frameBytes());
            assertEquals(List.of(10,14,15,14,11,14,15,14),v.animations().get(8).frameBytes());
            assertEquals(List.of(12,14,15,14,13,14,15,14),v.animations().get(9).frameBytes());
            assertTrue(v.animations().stream().allMatch(s->s.delay()==1&&s.terminal()==CdObjectVisualAssets.Terminal.LOOP_FF));
            assertThrows(IllegalArgumentException.class,()->new CdMonitor.Assets(common.hudVisuals(),0x20A538));
            assertThrows(IllegalArgumentException.class,()->new CdMonitor.Assets(v,0x20A539));
        }

        @Test void genuineR31TitleReader276RetainsCanonical275AndNative168MetadataHull() {
            var v=common.titleCard();var raw=assets.view(CdR31aAssets.Asset.TITLE_MAPPINGS);var read=assets.view(CdR31aAssets.Asset.TITLE_MAPPING_READ_VIEW);
            assertEquals(275,raw.length());assertEquals(276,read.length());assertEquals(0,read.unsignedByte(275));
            assertArrayEquals(raw.copy(),read.slice(0,275).copy());assertArrayEquals(read.copy(),v.mappings().copy());
            assertArrayEquals(new int[]{18,50,62,74,100,136,172,208,244},v.mappingFrames().stream().mapToInt(CdObjectVisualAssets.MappingFrame::relativeOffset).toArray());
            assertArrayEquals(new int[]{6,2,2,5,7,7,7,7,6},v.mappingFrames().stream().mapToInt(f->f.pieces().size()).toArray());
            var last=v.mappingFrames().get(8).pieces().get(1);assertEquals(162,last.tileOffset());assertEquals(6,last.tileCount());
            assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(read.copy(),9,164));
            assertEquals(9,CdObjectVisualAssets.decodeStatic(read.copy(),9,168).mappingFrames().size());
            assertSame(read,assets.view(CdR31aAssets.Asset.TITLE_MAPPING_READ_VIEW));
        }

        @Test void allEightCharacterRowsAreExactR3WordsWithNoActOrChildDefaults() {
            int[] words={0x130,0x228,0x168,0x15A,0x100,0x238,0x178,0x25A,
                    0x100,0x240,0x180,0x25A,0x100,0x248,0x188,0x25A,
                    0x120,0x230,0x170,0x35A,0x140,0x248,0x188,0x45A,
                    0x100,0x1D0,0x110,0x75A,0x100,0x1D0,0x110,0x85A};
            var bytes=common.titleCardAssets().initData();assertEquals(words.length*2,bytes.length());
            for(int i=0;i<words.length;i++)assertEquals(words[i],bytes.unsignedWord(i*2));
        }

        @Test void originalSonic167MappingReader2444IsGenuineCanonicalPrefixPlusRealEven() {
            var visual=assets.sonic();var raw=assets.view(CdR31aAssets.Asset.MAP_SPR_SONIC);var read=assets.view(CdR31aAssets.Asset.SONIC_MAPPING_READ_VIEW);
            assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_167,visual.sourceFormat());assertEquals(167,visual.mappingFrames().size());
            assertEquals(2443,raw.length());assertEquals(2444,read.length());assertEquals(0,read.unsignedByte(2443));
            assertArrayEquals(raw.copy(),read.slice(0,2443).copy());assertArrayEquals(raw.copy(),visual.mappings().copy());
            assertEquals(0x22DF20,assets.range(CdR31aAssets.Asset.SONIC_MAPPING_READ_VIEW).nativeAddress());
        }

        @Test void fourCountedRootsResolveAllTwoHundredFramesFromTheirExactActualSourceViews() {
            var mapper=CdObjectMappings.fromR31aAssets(assets,common);
            var tables=List.of(assets.view(CdR31aAssets.Asset.SONIC_MAPPING_READ_VIEW),assets.view(CdR31aAssets.Asset.HUD_MAPPINGS),
                    assets.view(CdR31aAssets.Asset.MONITOR_MAPPINGS),assets.view(CdR31aAssets.Asset.TITLE_MAPPING_READ_VIEW));
            int[] roots={0x22DF20,0x20A850,0x20A538,0x20F9A6},counts={167,4,20,9};int total=0;
            for(int t=0;t<roots.length;t++)for(int frame=0;frame<counts[t];frame++){
                var expected=body(tables.get(t),frame);assertEquals(expected,mapper.resolve(roots[t],false,frame));total++;
            }
            assertEquals(200,total);
        }

        @Test void registrationPreservesFullAddressAndFrameBoundsWithoutStaticOrPointsFallback() {
            var mapper=CdObjectMappings.fromR31aAssets(assets,common);
            int[] roots={0x22DF20,0x20A850,0x20A538,0x20F9A6},counts={167,4,20,9};
            for(int i=0;i<roots.length;i++){int root=roots[i],limit=counts[i];
                assertThrows(UnsupportedOperationException.class,()->mapper.resolve(root,false,limit));
                assertThrows(UnsupportedOperationException.class,()->mapper.resolve(root,false,255));
                assertThrows(UnsupportedOperationException.class,()->mapper.resolve(root,true,0));
                assertThrows(UnsupportedOperationException.class,()->mapper.resolve(root|0xFF000000,false,0));
                assertThrows(UnsupportedOperationException.class,()->mapper.resolve(root+1,false,0));}
            // The original six-frame Points table is explicitly registered; it is never a fallback.
            var points=assets.view(CdR31aAssets.Asset.POINTS_MAPPINGS);
            for(int frame=0;frame<6;frame++)assertEquals(body(points,frame),mapper.resolve(0x20A74E,false,frame));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20A74E,false,6));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20A74E,false,255));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20A74E,true,0));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0xFF20A74E,false,0));
            assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20A74F,false,0));
            assertThrows(IllegalArgumentException.class,()->mapper.resolve(0x20A850,false,-1));
            assertThrows(IllegalArgumentException.class,()->mapper.resolve(0x20A850,false,256));
        }

        @Test void equalPublicIdentityCannotJoinAForeignActualAggregateOrNullWrapper() {
            assertEquals(assets.identity(),equalButForeign.identity());
            assertThrows(IllegalArgumentException.class,()->CdObjectMappings.fromR31aAssets(equalButForeign,common));
            assertThrows(NullPointerException.class,()->CdObjectMappings.fromR31aAssets(assets,null));
            var other=CdR31aObjectAssets.fromAssets(equalButForeign);assertFalse(other.ownsAssets(assets));
            assertEquals(common.identity(),other.identity());assertNotSame(common,other);
        }

        @Test void immutableCachedMappingsScriptsAndCharactersNeverExposeArtOrMutableOwnerState() {
            for(var visual:List.of(common.hudVisuals(),common.monitorTime(),common.titleCard())){
                byte[] before=visual.mappings().copy(),copy=visual.mappings().copy();copy[0]^=0x55;
                assertArrayEquals(before,visual.mappings().copy());assertThrows(ReadOnlyBufferException.class,()->visual.mappings().buffer().put(0,(byte)0));
                assertThrows(UnsupportedOperationException.class,()->visual.mappingFrames().clear());}
            assertThrows(UnsupportedOperationException.class,()->common.monitorTime().animations().clear());
            assertThrows(ReadOnlyBufferException.class,()->common.titleCardAssets().initData().buffer().put(0,(byte)0));
            assertTrue(Arrays.stream(CdR31aObjectAssets.class.getDeclaredMethods()).noneMatch(m->List.of("view","read","art","decoded","originalImage").contains(m.getName())));
        }
    }

    /** Independent native counted-byte oracle; never calls production resolver/visual output. */
    private static FrameMapping body(CdAssetBytes bytes,int frame) {
        int at=(short)bytes.unsignedWord(frame*2),count=bytes.unsignedByte(at);var pieces=new ArrayList<RawPiece>();
        for(int i=0;i<count;i++){int p=at+1+i*5;pieces.add(new RawPiece(bytes.signedByte(p),bytes.unsignedByte(p+1),bytes.unsignedWord(p+2),bytes.signedByte(p+4)));}
        return new FrameMapping(count,pieces);
    }
}
