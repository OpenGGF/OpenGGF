package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Detached actual-disc custody only. No programme selection, Frame, World, load or service is invoked. */
class TestSonicCdAct3StockCustody {
    private static final EnumMap<CdReleaseVariant,CdGameDataSource> SOURCES=new EnumMap<>(CdReleaseVariant.class);

    @Test void nullPairCannotInventAnAuthenticatedFuture() {
        assertThrows(NullPointerException.class,()->new CdGameDataSource.Act3FutureAssets(null,null));
    }

    @Test void preparedUsaRetainsBothOriginalFutureProgrammesAfterTheCueCloses() throws Exception {
        var source=source(CdReleaseVariant.USA);var pair=source.act3FutureAssets().orElseThrow();
        assertEquals(CdStagePayload.R13C.stage(),pair.goodFuture().identity().stage());
        assertEquals(CdStagePayload.R13D.stage(),pair.badFuture().identity().stage());
        assertEquals("3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f",pair.goodFuture().identity().mmdSha256());
        assertEquals("27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356",pair.badFuture().identity().mmdSha256());
        for(var future:new CdR13FutureAssets[]{pair.goodFuture(),pair.badFuture()}) {
            assertEquals(source.release().normalizedSha256(),future.identity().observedWholeDiscSha256());
            assertTrue(CdStagePlayerSource.fromAct3Future(future).ownsAct3Future(future));
            assertEquals(167,future.sonic().mappingFrames().size());assertEquals(61,future.placements().records().size());
            var palette=future.initialPalette();var changed=palette.copy();changed[0]^=127;
            assertNotEquals(changed[0],palette.copy()[0]);
        }
        assertTrue(source.rom().isEmpty());assertFalse(source.identity().contains(".cue"));
    }

    @Test void dataSelectorUsesNativeTstByteWithoutNormalizingTheCarriedValue() throws Exception {
        var pair=source(CdReleaseVariant.USA).act3FutureAssets().orElseThrow();
        assertSame(pair.badFuture(),pair.select(0));
        for(int raw=1;raw<=255;raw++)assertSame(pair.goodFuture(),pair.select(raw));
        for(int bad:new int[]{-1,256,Integer.MIN_VALUE,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->pair.select(bad));
    }

    @Test void nullReversedAndAliasedProgrammeRolesRejectBeforePublication() throws Exception {
        var pair=source(CdReleaseVariant.USA).act3FutureAssets().orElseThrow();
        assertThrows(NullPointerException.class,()->new CdGameDataSource.Act3FutureAssets(pair.goodFuture(),null));
        assertThrows(NullPointerException.class,()->new CdGameDataSource.Act3FutureAssets(null,pair.badFuture()));
        assertThrows(IllegalArgumentException.class,()->new CdGameDataSource.Act3FutureAssets(pair.badFuture(),pair.goodFuture()));
        assertThrows(IllegalArgumentException.class,()->new CdGameDataSource.Act3FutureAssets(pair.goodFuture(),pair.goodFuture()));
        assertThrows(IllegalArgumentException.class,()->new CdGameDataSource.Act3FutureAssets(pair.badFuture(),pair.badFuture()));
    }

    @Test void legacyConstructorsOmitTheFutureAndNewRetentionKeepsEveryExistingIdentity() throws Exception {
        var s=source(CdReleaseVariant.USA);var pair=s.act3FutureAssets().orElseThrow();
        var legacy=new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets());
        assertTrue(legacy.act3FutureAssets().isEmpty());assertSame(s.assets(),legacy.assets());assertEquals(s.identity(),legacy.identity());
        var full=new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),Optional.of(pair));
        assertSame(pair,full.act3FutureAssets().orElseThrow());assertEquals(s.identity(),full.identity());
        assertSame(s.timeWarpPrograms().orElseThrow(),full.timeWarpPrograms().orElseThrow());
        assertSame(s.act2Assets().orElseThrow(),full.act2Assets().orElseThrow());
        assertSame(s.act2PastAssets().orElseThrow(),full.act2PastAssets().orElseThrow());
        assertThrows(NullPointerException.class,()->new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),null));
    }

    @Test void regionalSourcesRemainExplicitlyAbsentAndRejectTheUsaFuturePair() throws Exception {
        var pair=source(CdReleaseVariant.USA).act3FutureAssets();
        for(var region:new CdReleaseVariant[]{CdReleaseVariant.JAPAN,CdReleaseVariant.EUROPE}) {
            var s=source(region);assertTrue(s.act3FutureAssets().isEmpty());assertTrue(s.act2PastAssets().isEmpty());
            assertThrows(IllegalArgumentException.class,()->new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),pair));
        }
    }

    @Test void retainedFutureAddsNoHostAssetPathOrCartridgeFallback() throws Exception {
        var s=source(CdReleaseVariant.USA);
        for(String path:new String[]{"R13C__.MMD;1","R13D__.MMD;1","stage/R13C/tiles.bin","stage/R13D/blocks.bin","../R13C__.MMD;1"})
            assertThrows(FileNotFoundException.class,()->s.openAsset(path));
        try(var first=s.openAsset(CdGameDataSource.DECODED_TILES);var second=s.openAsset(CdGameDataSource.DECODED_TILES)) {
            assertArrayEquals(first.readAllBytes(),second.readAllBytes());
        }
    }

    @Test void retainingDataDoesNotAdmitDirectFutureBootOrPrivateWorldExecution() {
        for(var stage:new CdStageKey[]{CdStagePayload.R13C.stage(),CdStagePayload.R13D.stage()})
            assertThrows(UnsupportedOperationException.class,()->CdStockLaunch.prepare(Path.of("missing.cue"),new CdStockLaunch.Request(stage,Optional.of(CdReleaseVariant.USA),Optional.empty())));
    }

    private static synchronized CdGameDataSource source(CdReleaseVariant variant) throws Exception {
        String root=System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root!=null&&!root.isBlank(),"optional original retail media");
        var cached=SOURCES.get(variant);if(cached!=null)return cached;
        String directory=switch(variant){case USA->"USA";case JAPAN->"Japan";case EUROPE->"Europe";};
        try(var files=Files.list(Path.of(root).resolve(directory))) {
            var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size(),"Configured original-media directory requires exactly one CUE");
            var result=CdStockLaunch.prepare(cues.getFirst(),CdStockLaunch.Request.r11a());SOURCES.put(variant,result);return result;
        }
    }
}
