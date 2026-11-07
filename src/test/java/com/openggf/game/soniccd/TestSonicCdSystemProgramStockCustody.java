package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.FileNotFoundException;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests FIRST. Source custody only: no SPX/boot/clear/selector/driver/Module execution. Author Java was UNRUN; Root qualification is recorded in the implementation plan. */
class TestSonicCdSystemProgramStockCustody {
    private static final String SHA = "f0e9805a060fdd201e9f9e0043bd1d89d61ea69bd3de429bd4c2a83dd498f0d5";
    private record Key(String root,CdReleaseVariant variant) { }
    private static final Map<Key,CdGameDataSource> SOURCES = new HashMap<>();

    @Test void originalUsaStockRetainsExactSystemFileAfterItsCueCloses() throws Exception {
        var s=source(CdReleaseVariant.USA);var data=s.systemProgramSource().orElseThrow();var stamp=data.source();
        assertSame(CdRetailReleases.USA,s.release());assertEquals(CdReleaseVariant.USA,stamp.variant());
        assertEquals(s.assets().identity().observedWholeDiscSha256(),stamp.wholeDiscSha256());
        assertEquals(s.release().normalizedSha256(),stamp.wholeDiscSha256());assertEquals("/SPX___.BIN;1",stamp.filePath());
        assertEquals(10237,stamp.isoLba());assertEquals(17408,stamp.fileBytes());assertEquals(SHA,stamp.fileSha256());
        assertEquals(0xB800,stamp.loadAddress());assertEquals(0xC000,stamp.entryAddress());
        assertEquals(17408,data.bytes().length());assertEquals(SHA,sha(data.bytes().copy()));
        assertEquals("41f870003e3c00ff20fc0000000051cffff8",HexFormat.of().formatHex(data.bytes().slice(0x800,18).copy()));
        assertEquals(new CdSystemProgramSource.InitializationClear(0x800,0x7000,0x400,0,4),data.initializationClear());
        assertSame(data,s.systemProgramSource().orElseThrow());assertTrue(s.rom().isEmpty());
        assertFalse(Arrays.stream(CdNativeFile.values()).anyMatch(v->v.isoName().equals("SPX___.BIN;1")));
    }

    @Test void independentSameSourceFactoryMatchesRetainedDataAndClosedOwnerCannotReopen() throws Exception {
        var s=source(CdReleaseVariant.USA);var data=s.systemProgramSource().orElseThrow();CdSystemProgramSource independent;
        var p=CdProgramAssets.open(cue(CdReleaseVariant.USA),Optional.of(CdReleaseVariant.USA),Optional.empty());
        try(p){assertEquals(s.release().normalizedSha256(),p.identity().normalizedSha256());independent=CdSystemProgramSource.fromPrograms(p);}
        assertThrows(java.io.IOException.class,()->CdSystemProgramSource.fromPrograms(p));
        assertNotSame(data,independent);assertNotSame(data.bytes(),independent.bytes());assertEquals(data.source(),independent.source());
        assertArrayEquals(data.bytes().copy(),independent.bytes().copy());
        byte[] original=data.bytes().copy();byte[] copy=data.bytes().copy();copy[0]^=0x55;assertArrayEquals(original,data.bytes().copy());
        var first=data.bytes().buffer();var second=data.bytes().buffer();first.get();assertEquals(0,second.position());
        assertThrows(ReadOnlyBufferException.class,()->second.put(0,(byte)1));
        assertEquals(0,data.bytes().slice(17408,0).length());assertThrows(IndexOutOfBoundsException.class,()->data.bytes().unsignedByte(17408));
    }

    @Test void allLegacyConstructorsThreeThroughTenOmitOnlyTheNewSystemCustody() throws Exception {
        var s=source(CdReleaseVariant.USA);
        var old=List.of(new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets()),
            new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms()),
            new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets()),
            new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets()),
            new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets()),
            new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets()),
            new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets()),
            oldTen(s));
        for(var v:old){assertTrue(v.systemProgramSource().isEmpty());assertEquals(s.identity(),v.identity());assertSame(s.release(),v.release());assertSame(s.assets(),v.assets());assertSame(s.fmSoundAssets().orElseThrow(),v.fmSoundAssets().orElseThrow());}
        var ten=old.getLast();assertSame(s.timeWarpPrograms().orElseThrow(),ten.timeWarpPrograms().orElseThrow());
        assertSame(s.act2Assets().orElseThrow(),ten.act2Assets().orElseThrow());assertSame(s.act2PastAssets().orElseThrow(),ten.act2PastAssets().orElseThrow());
        assertSame(s.act3FutureAssets().orElseThrow(),ten.act3FutureAssets().orElseThrow());assertSame(s.r31aAssets().orElseThrow(),ten.r31aAssets().orElseThrow());
        assertSame(s.initialSaveAssets().orElseThrow(),ten.initialSaveAssets().orElseThrow());assertSame(s.r3PcmSource().orElseThrow(),ten.r3PcmSource().orElseThrow());
        assertTrue(old.subList(0,7).stream().allMatch(v->v.r3PcmSource().isEmpty()));
        assertTrue(old.subList(0,6).stream().allMatch(v->v.initialSaveAssets().isEmpty()));
    }

    @Test void canonicalElevenRetainsExactEarlierOwnersAndAllowsExplicitAbsenceWithoutNormalization() throws Exception {
        var s=source(CdReleaseVariant.USA);var full=retain(s,s.systemProgramSource());
        assertSame(s.systemProgramSource().orElseThrow(),full.systemProgramSource().orElseThrow());assertEquals(s.identity(),full.identity());
        assertSame(s.assets(),full.assets());assertSame(s.fmSoundAssets().orElseThrow(),full.fmSoundAssets().orElseThrow());
        assertSame(s.timeWarpPrograms().orElseThrow(),full.timeWarpPrograms().orElseThrow());assertSame(s.act2Assets().orElseThrow(),full.act2Assets().orElseThrow());
        assertSame(s.act2PastAssets().orElseThrow(),full.act2PastAssets().orElseThrow());assertSame(s.act3FutureAssets().orElseThrow(),full.act3FutureAssets().orElseThrow());
        assertSame(s.r31aAssets().orElseThrow(),full.r31aAssets().orElseThrow());assertSame(s.initialSaveAssets().orElseThrow(),full.initialSaveAssets().orElseThrow());
        assertSame(s.r3PcmSource().orElseThrow(),full.r3PcmSource().orElseThrow());
        var absent=retain(s,Optional.empty());assertTrue(absent.systemProgramSource().isEmpty());assertEquals(s.identity(),absent.identity());
        assertSame(s.r3PcmSource().orElseThrow(),absent.r3PcmSource().orElseThrow());
        var independent=minimal(s,s.assets(),s.systemProgramSource());assertTrue(independent.r3PcmSource().isEmpty());assertTrue(independent.initialSaveAssets().isEmpty());
        assertSame(s.systemProgramSource().orElseThrow(),independent.systemProgramSource().orElseThrow());assertEquals(s.identity(),independent.identity());
    }

    @ParameterizedTest(name="{0}: system custody absent and genuine foreign USA data rejects")
    @EnumSource(value=CdReleaseVariant.class,names={"JAPAN","EUROPE"})
    void regionalAbsenceAndForeignOriginalSourceRemainStrict(CdReleaseVariant region) throws Exception {
        var s=source(region);assertTrue(s.systemProgramSource().isEmpty());assertTrue(retain(s,Optional.empty()).systemProgramSource().isEmpty());
        assertThrows(IllegalArgumentException.class,()->retain(s,source(CdReleaseVariant.USA).systemProgramSource()));
        assertTrue(s.fmSoundAssets().isEmpty());assertTrue(s.timeWarpPrograms().isEmpty());assertTrue(s.initialSaveAssets().isEmpty());assertTrue(s.r3PcmSource().isEmpty());assertTrue(s.r31aAssets().isEmpty());
    }

    @Test void nullsKeepAllOldValidationPrecedenceAndRejectOnlyTheNewNullAfterEarlierChecks() throws Exception {
        var first=assertThrows(NullPointerException.class,()->new CdGameDataSource(null,null,null,null,null,null,null,null,null,null,null));assertEquals("release",first.getMessage());
        var s=source(CdReleaseVariant.USA);assertEquals("systemProgramSource",assertThrows(NullPointerException.class,()->retain(s,null)).getMessage());
        assertEquals("r3PcmSource",assertThrows(NullPointerException.class,()->new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets(),null,null)).getMessage());
        var wrong=new com.openggf.game.soniccd.release.CdRetailRelease("constructed-foreign",CdReleaseVariant.USA,"0".repeat(64),s.release().reference());
        assertThrows(IllegalArgumentException.class,()->new CdGameDataSource(wrong,s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets(),s.r3PcmSource(),null));
    }

    @Test void foreignBaseStageFileDomainAndImageCannotBorrowTheActualSystemSource() throws Exception {
        var s=source(CdReleaseVariant.USA);var id=s.assets().identity();
        for(var wrong:List.of(new CdR11aAssets.Identity(id.variant(),CdStagePayload.R31A.stage(),id.file(),id.domain(),id.mmdSha256(),id.observedWholeDiscSha256()),
            new CdR11aAssets.Identity(id.variant(),id.stage(),CdNativeFile.R31A,id.domain(),id.mmdSha256(),id.observedWholeDiscSha256()),
            new CdR11aAssets.Identity(id.variant(),id.stage(),id.file(),CdMmdImage.Domain.RELOCATED_COPY,id.mmdSha256(),id.observedWholeDiscSha256()),
            new CdR11aAssets.Identity(id.variant(),id.stage(),id.file(),id.domain(),"0".repeat(64),id.observedWholeDiscSha256()))){
            var stage=mock(CdR11aAssets.class);when(stage.identity()).thenReturn(wrong);when(stage.profile()).thenReturn(s.assets().profile());
            assertThrows(IllegalArgumentException.class,()->minimal(s,stage,s.systemProgramSource()));
        }
    }

    @Test void constructedWrongSystemMetadataIsRejectedWithoutAUsablePositiveSpy() throws Exception {
        var s=source(CdReleaseVariant.USA);var actual=s.systemProgramSource().orElseThrow().source();
        for(String field:List.of("variant","disc","path","lba","size","sha","load","entry")){
            var system=mock(CdSystemProgramSource.class);var stamp=mock(CdSystemProgramSource.Source.class);when(system.source()).thenReturn(stamp);
            when(stamp.variant()).thenReturn(actual.variant());when(stamp.wholeDiscSha256()).thenReturn(actual.wholeDiscSha256());when(stamp.filePath()).thenReturn(actual.filePath());
            when(stamp.isoLba()).thenReturn(actual.isoLba());when(stamp.fileBytes()).thenReturn(actual.fileBytes());when(stamp.fileSha256()).thenReturn(actual.fileSha256());
            when(stamp.loadAddress()).thenReturn(actual.loadAddress());when(stamp.entryAddress()).thenReturn(actual.entryAddress());
            switch(field){case "variant"->when(stamp.variant()).thenReturn(CdReleaseVariant.JAPAN);case "disc"->when(stamp.wholeDiscSha256()).thenReturn("0".repeat(64));
                case "path"->when(stamp.filePath()).thenReturn("/IPX___.MMD;1");case "lba"->when(stamp.isoLba()).thenReturn(10238L);case "size"->when(stamp.fileBytes()).thenReturn(17409);
                case "sha"->when(stamp.fileSha256()).thenReturn("0".repeat(64));case "load"->when(stamp.loadAddress()).thenReturn(0xB802);case "entry"->when(stamp.entryAddress()).thenReturn(0xC002);default->throw new AssertionError();}
            assertThrows(IllegalArgumentException.class,()->retain(s,Optional.of(system)));
        }
    }

    @Test void privateTypedCustodyLeavesAllElevenPublicMethodsAndOldConstructorsUnpublished() throws Exception {
        var getter=CdGameDataSource.class.getDeclaredMethod("systemProgramSource");assertFalse(Modifier.isPublic(getter.getModifiers()));assertFalse(Modifier.isProtected(getter.getModifiers()));assertFalse(Modifier.isPrivate(getter.getModifiers()));
        assertEquals("java.util.Optional<com.openggf.game.soniccd.media.CdSystemProgramSource>",getter.getGenericReturnType().getTypeName());
        var field=CdGameDataSource.class.getDeclaredField("systemProgramSource");assertTrue(Modifier.isPrivate(field.getModifiers()));assertTrue(Modifier.isFinal(field.getModifiers()));assertEquals(getter.getGenericReturnType(),field.getGenericType());
        assertEquals(List.of(3,4,5,6,7,8,9,10,11),Arrays.stream(CdGameDataSource.class.getDeclaredConstructors()).map(v->v.getParameterCount()).sorted().toList());
        assertTrue(Arrays.stream(CdGameDataSource.class.getDeclaredConstructors()).noneMatch(v->Modifier.isPublic(v.getModifiers())));
        var canonical=Arrays.stream(CdGameDataSource.class.getDeclaredConstructors()).filter(v->v.getParameterCount()==11).findFirst().orElseThrow();
        assertEquals("java.util.Optional<com.openggf.game.soniccd.media.CdR3PcmSource>",canonical.getGenericParameterTypes()[9].getTypeName());assertEquals(getter.getGenericReturnType(),canonical.getGenericParameterTypes()[10]);
        assertEquals(List.of("act2Assets/0","act2PastAssets/0","act3FutureAssets/0","assets/0","fmSoundAssets/0","identity/0","openAsset/1","r31aAssets/0","release/0","rom/0","timeWarpPrograms/0"),Arrays.stream(CdGameDataSource.class.getDeclaredMethods()).filter(v->Modifier.isPublic(v.getModifiers())).map(v->v.getName()+"/"+v.getParameterCount()).sorted().toList());
        assertTrue(Arrays.stream(CdGameDataSource.class.getDeclaredFields()).noneMatch(v->v.getType()==CdProgramAssets.class||v.getType()==Path.class));
    }

    @Test void systemSourceCreatesNoNewLogicalFilePathAndOldStreamsRemainIndependent() throws Exception {
        var s=source(CdReleaseVariant.USA);for(String path:List.of("SPX___.BIN;1","program/SPX___.BIN;1","boot/SPX.bin","../SPX___.BIN;1","/SPX___.BIN;1"))assertThrows(FileNotFoundException.class,()->s.openAsset(path));
        for(String path:List.of(CdGameDataSource.DECODED_BLOCKS,CdGameDataSource.DECODED_TILES,CdGameDataSource.INITIAL_PALETTE)){
            try(var a=s.openAsset(path);var b=s.openAsset(path)){int first=a.read();assertEquals(first,b.read());byte[] rest=a.readAllBytes();assertArrayEquals(rest,b.readAllBytes());rest[0]^=1;try(var c=s.openAsset(path)){assertEquals(first,c.read());assertNotEquals(rest[0],c.readAllBytes()[0]);}}
        }
    }

    private static CdGameDataSource oldTen(CdGameDataSource s){return new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets(),s.r3PcmSource());}
    private static CdGameDataSource retain(CdGameDataSource s,Optional<CdSystemProgramSource> system){return new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets(),s.r3PcmSource(),system);}
    private static CdGameDataSource minimal(CdGameDataSource s,CdR11aAssets stage,Optional<CdSystemProgramSource> system){return new CdGameDataSource(s.release(),stage,s.fmSoundAssets(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),system);}
    private static synchronized CdGameDataSource source(CdReleaseVariant variant)throws Exception{var key=new Key(root(),variant);var prior=SOURCES.get(key);if(prior!=null)return prior;var s=CdStockLaunch.prepare(cue(variant),CdStockLaunch.Request.r11a());SOURCES.put(key,s);return s;}
    private static String root(){String r=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(r!=null&&!r.isBlank(),"Optional original media absent");return r;}
    private static Path cue(CdReleaseVariant variant)throws java.io.IOException{String dir=switch(variant){case USA->"USA";case JAPAN->"Japan";case EUROPE->"Europe";};try(var paths=Files.list(Path.of(root()).resolve(dir))){var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());return cues.getFirst();}}
    private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
}
