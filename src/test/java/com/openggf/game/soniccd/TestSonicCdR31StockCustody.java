package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdR31aAssets;
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

/** Actual original-disc data custody only: no loading visit, Frame, World or gameplay is created. */
class TestSonicCdR31StockCustody {
    private static final EnumMap<CdReleaseVariant,CdGameDataSource> SOURCES=new EnumMap<>(CdReleaseVariant.class);

    @Test void preparedUsaRetainsOriginalCollisionChaosAfterTheCueCloses() throws Exception {
        var s=source(CdReleaseVariant.USA);var next=s.r31aAssets().orElseThrow();
        assertEquals(CdNativeFile.R31A,next.identity().file());assertEquals(CdStagePayload.R31A.stage(),next.identity().stage());
        assertEquals("30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e",next.identity().mmdSha256());
        assertEquals(s.release().normalizedSha256(),next.identity().observedWholeDiscSha256());
        assertSame(next.placements(),CdObjectPlacements.fromR31aAssets(next));assertEquals(517,next.placements().records().size());
        assertEquals(63,next.ranges().size());assertEquals(167,next.sonic().mappingFrames().size());
        var player=CdStagePlayerSource.fromR31aAssets(next);assertTrue(player.ownsR31aAssets(next));assertEquals(0x100,player.ordinaryZoneActWord());
        assertTrue(s.rom().isEmpty());assertFalse(s.identity().contains(".cue"));
    }

    @Test void allLegacyConstructorsOmitTheNextZoneWithoutInventingCustody() throws Exception {
        var s=source(CdReleaseVariant.USA);
        for(var old:new CdGameDataSource[]{new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets()),
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms()),
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets()),
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets()),
                new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets())}) {
            assertTrue(old.r31aAssets().isEmpty());assertSame(s.assets(),old.assets());assertEquals(s.identity(),old.identity());
        }
    }

    @Test void explicitRetentionKeepsExactOwnersAndAllEarlierDataIdentity() throws Exception {
        var s=source(CdReleaseVariant.USA);var full=retain(s,s.r31aAssets());
        assertSame(s.r31aAssets().orElseThrow(),full.r31aAssets().orElseThrow());assertEquals(s.identity(),full.identity());
        assertSame(s.assets(),full.assets());assertSame(s.fmSoundAssets().orElseThrow(),full.fmSoundAssets().orElseThrow());
        assertSame(s.timeWarpPrograms().orElseThrow(),full.timeWarpPrograms().orElseThrow());
        assertSame(s.act2Assets().orElseThrow(),full.act2Assets().orElseThrow());
        assertSame(s.act2PastAssets().orElseThrow(),full.act2PastAssets().orElseThrow());
        assertSame(s.act3FutureAssets().orElseThrow(),full.act3FutureAssets().orElseThrow());
        assertTrue(retain(s,Optional.empty()).r31aAssets().isEmpty());
    }

    @Test void nullOptionalAndContradictoryOriginalOwnerReject() throws Exception {
        var s=source(CdReleaseVariant.USA);assertThrows(NullPointerException.class,()->retain(s,null));
        var wrong=org.mockito.Mockito.mock(CdR31aAssets.class);var original=s.r31aAssets().orElseThrow();
        org.mockito.Mockito.when(wrong.identity()).thenReturn(original.identity());
        org.mockito.Mockito.when(wrong.placements()).thenReturn(original.placements());
        assertThrows(IllegalArgumentException.class,()->retain(s,Optional.of(wrong)),"Matching public identity/cache cannot supply the private original image");
    }

    @Test void regionalPreparationRemainsAbsentAndRejectsUsaNextZoneCustody() throws Exception {
        var usa=source(CdReleaseVariant.USA).r31aAssets();
        for(var region:new CdReleaseVariant[]{CdReleaseVariant.JAPAN,CdReleaseVariant.EUROPE}) {
            var s=source(region);assertTrue(s.r31aAssets().isEmpty());assertThrows(IllegalArgumentException.class,()->retain(s,usa));
        }
    }

    @Test void retainedNextZoneAddsNoHostAssetPathOrCartridgeFallback() throws Exception {
        var s=source(CdReleaseVariant.USA);
        for(String path:new String[]{"R31A__.MMD;1","stage/R31A/tiles.bin","stage/R31A/blocks.bin","../R31A__.MMD;1"})
            assertThrows(FileNotFoundException.class,()->s.openAsset(path));
        try(var first=s.openAsset(CdGameDataSource.DECODED_TILES);var second=s.openAsset(CdGameDataSource.DECODED_TILES)) {
            assertArrayEquals(first.readAllBytes(),second.readAllBytes());
        }
    }

    @Test void retainedViewBytesAreImmutableAndNoDiscHandleIsNeededForLaterDecode() throws Exception {
        var next=source(CdReleaseVariant.USA).r31aAssets().orElseThrow();
        var bytes=next.view(CdR31aAssets.Asset.ANI_SONIC);var copy=bytes.copy();int first=bytes.unsignedByte(0);copy[0]^=127;
        assertEquals(first,bytes.unsignedByte(0));assertEquals(656,next.terrain().blocks().length()/8);
    }

    @Test void dataRetentionDoesNotAdmitDirectR31Boot() {
        assertThrows(UnsupportedOperationException.class,()->CdStockLaunch.prepare(Path.of("missing.cue"),
                new CdStockLaunch.Request(CdStagePayload.R31A.stage(),Optional.of(CdReleaseVariant.USA),Optional.empty())));
    }

    private static CdGameDataSource retain(CdGameDataSource s,Optional<CdR31aAssets> next) {
        return new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),s.timeWarpPrograms(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),next);
    }
    private static synchronized CdGameDataSource source(CdReleaseVariant variant) throws Exception {
        String root=System.getProperty("soniccd.media.root");Assumptions.assumeTrue(root!=null&&!root.isBlank(),"optional original retail media");
        var cached=SOURCES.get(variant);if(cached!=null)return cached;
        String directory=switch(variant){case USA->"USA";case JAPAN->"Japan";case EUROPE->"Europe";};
        try(var files=Files.list(Path.of(root).resolve(directory))) {
            var cues=files.filter(v->v.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size(),"Configured original-media directory requires exactly one CUE");
            var result=CdStockLaunch.prepare(cues.getFirst(),CdStockLaunch.Request.r11a());SOURCES.put(variant,result);return result;
        }
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
