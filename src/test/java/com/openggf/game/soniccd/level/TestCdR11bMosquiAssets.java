package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual USA B raw bytes/operands, N1ecc5f3a Mosqui/Main.asm:26-54,154-165; no gameplay or art service. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bMosquiAssets {
    private static final List<CdR11bAssets.Asset> KEYS=List.of(ANI_MOSQUI_NORMAL,ANI_MOSQUI_DAMAGED,MAP_SPR_MOSQUI_NORMAL,MAP_SPR_MOSQUI_DAMAGED);
    private static final String[] RAW={"00040008010001ff06020304fc00","00040008040001ff0e020304fc",
            "000a001a00250030003603f4080000f8fc050003f0fc0500070002fc050003f0fc05000b0002f00a000ff808040018f001f00b001af801f00b0026f4",
            "000a001a00250030003603f4080000f8fc050032f0fc0500070002fc050032f0fc05000b0002f00a000ff808040036f001f00b0038f80200010044fcf00b0026f400"};
    private static final String[] HASH={"24b00d5597a3482b4bf9f5e692be5ea38534c1f5265408b8c7273d672949c1a2","6718cb74ab9b1ce59a3a122b0dee40b38ece57b7fdacd1f4baf7b76bc38c1a6e",
            "5a10487e5e92e1c60e5885d832b90239c22d45f3c84bb49cd8886c590d8aba2e","23f8799c277fc89e4f94ccf75e0b488de9fca30a05a658cde5eaf0be8fec7313"};
    private Fixture retained;
    private record Fixture(CdR11bAssets assets,CdMmdImage past,CdMmdImage present){}

    @Test void fourRawViewsAppendAfterAll110WithoutChangingSpikesAntonOrAnyReader()throws Exception{
        var a=retail().assets();int[] offsets={0xB522,0xB530,0xB53E,0xB57A};int[] lengths={14,13,60,66};
        assertEquals(125,CdR11bAssets.Asset.values().length);assertEquals(KEYS,Arrays.asList(CdR11bAssets.Asset.values()).subList(110,114));
        assertEquals(107,MAP_SPR_SPIKES.ordinal());assertEquals(108,ANI_ANTON.ordinal());assertEquals(109,MAP_SPR_ANTON.ordinal());
        for(int i=0;i<4;i++){var key=KEYS.get(i);var view=a.view(key);assertEquals(110+i,key.ordinal());assertEquals(offsets[i],key.fileOffset());assertEquals(lengths[i],key.length());
            assertEquals(HASH[i],sha(view.bytes().copy()));assertArrayEquals(hex(RAW[i]),view.bytes().copy());assertArrayEquals(retail().past().readFile(offsets[i],lengths[i]),view.bytes().copy());
            assertEquals(0x200000+offsets[i],view.range().nativeAddress());assertFalse(key.isPlcSource());assertTrue(key.readerProfile().isEmpty());
            for(var kind:CdR11bAssets.ReaderKind.values())assertThrows(IllegalArgumentException.class,()->a.readerView(key,kind));}
        assertEquals(36,Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37,Arrays.stream(CdR11bAssets.Asset.values()).filter(k->k.readerProfile().isPresent()).count());
    }
    @Test void actualDispatchAndAllFourLeasAuthenticateOriginalBAddressesRatherThanAOffsets()throws Exception{
        var image=retail().past();assertArrayEquals(hex("0020b3d0"),image.readFile(0x3D28,4));
        assertArrayEquals(hex("43fa0102"),image.readFile(0xB43A,4));assertEquals(0x20B53E,0x20B43C+(short)0x0102);
        assertArrayEquals(hex("45fa00e2"),image.readFile(0xB43E,4));assertEquals(0x20B522,0x20B440+(short)0x00E2);
        assertArrayEquals(hex("43fa012e"),image.readFile(0xB44A,4));assertEquals(0x20B57A,0x20B44C+(short)0x012E);
        assertArrayEquals(hex("45fa00e0"),image.readFile(0xB44E,4));assertEquals(0x20B530,0x20B450+(short)0x00E0);
        assertArrayEquals(hex("70004eb90020cf72"),image.readFile(0xB42C,8));assertEquals(0x2396,retail().assets().view(OBJECT_TILE_IDS).bytes().unsignedWord(0x24));
        for(var key:KEYS)assertNotEquals(sha(retail().assets().view(key).bytes().copy()),sha(retail().present().readFile(key.fileOffset(),key.length())));
    }
    @Test void completeFiveFrameTwoScriptFormatsKeepTheGenuineOddBodiesAndEvenBytes()throws Exception{
        var a=retail().assets();var normal=CdObjectVisualAssets.decode(a.view(MAP_SPR_MOSQUI_NORMAL).bytes().copy(),5,a.view(ANI_MOSQUI_NORMAL).bytes().copy(),2,70);
        var damaged=CdObjectVisualAssets.decode(a.view(MAP_SPR_MOSQUI_DAMAGED).bytes().copy(),5,a.view(ANI_MOSQUI_DAMAGED).bytes().copy(),2,70);
        int[] offsets={10,26,37,48,54};int[][] counts={{3,2,2,1,1},{3,2,2,1,2}};
        for(int model=0;model<2;model++){var visual=List.of(normal,damaged).get(model);int maximum=0;
            for(int frame=0;frame<5;frame++){var f=visual.mappingFrames().get(frame);assertEquals(offsets[frame],f.relativeOffset());assertEquals(counts[model][frame],f.pieces().size());
                assertEquals(1+5*counts[model][frame],f.bytes().length());for(var piece:f.pieces())maximum=Math.max(maximum,piece.tileOffset()+piece.tileCount());}
            assertEquals(model==0?50:70,maximum);assertEquals(List.of(0,1),visual.animations().getFirst().frameBytes());assertEquals(List.of(2,3,4),visual.animations().get(1).frameBytes());
            assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF,visual.animations().getFirst().terminal());assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,visual.animations().get(1).terminal());}
        assertEquals(13,damaged.animationTable().length());assertEquals(0xFC,damaged.animationTable().unsignedByte(12));
        assertArrayEquals(new byte[]{0},retail().past().readFile(0xB53D,1));assertEquals(0,damaged.mappings().unsignedByte(65));
    }
    @Test void detachedViewsLeaveAllExistingBorrowedProductsAndArtProfilesUntouched()throws Exception{
        var a=retail().assets();var terrain=a.terrain();var plan=a.loadPlan();var player=a.player();var placements=a.placements();
        for(var key:KEYS){var view=a.view(key);byte[] copy=view.bytes().copy();copy[0]^=0x55;assertArrayEquals(hex(RAW[KEYS.indexOf(key)]),a.view(key).bytes().copy());
            assertThrows(java.nio.ReadOnlyBufferException.class,()->view.bytes().buffer().put(0,(byte)1));}
        assertSame(terrain,a.terrain());assertSame(plan,a.loadPlan());assertSame(player,a.player());assertSame(placements,a.placements());
        var art=ART_MOSQUI.readerProfile().orElseThrow();assertEquals(846,art.sourceBlobRange().length());assertEquals(847,art.nativeInputRange().length());assertEquals(2240,art.outputBytes());
    }
    private Fixture retail()throws Exception{
        if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){var p=CdTimeWarpPrograms.fromPrograms(source);retained=new Fixture(CdR11bAssets.fromPrograms(p),p.r11b(),source.mmd(CdNativeFile.R11A));}return retained;
    }
    private static byte[] hex(String s){return HexFormat.of().parseHex(s);}
    private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
}
