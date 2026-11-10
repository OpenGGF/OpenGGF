package com.openggf.game.soniccd.level;

// Tests authored FIRST. allJavaUNRUN; strict constructed DATA contexts are not Frame/Ready evidence.
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestCdTtPlayerSource {
    private static CdProgrammeReader reader(CdR4DataLayout.Programme p)throws Exception {
        return TestCdProgrammeTtDefinitions.reader(p);
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void allTenBindActualTables188AndNativeZoneTimeWithoutFilenameArithmetic(CdR4DataLayout.Programme p)throws Exception {
        var r=reader(p);var binding=r.playerBinding();var source=CdStagePlayerSource.fromProgramme(r);
        int word=switch(p){case R41A,R41B,R41C,R41D->0x200;case R42A,R42B,R42C,R42D->0x201;case R43C,R43D->0x202;};
        int period=switch(p){case R41A,R42A->1;case R41B,R42B->0;case R41C,R41D,R42C,R42D,R43C,R43D->2;};
        assertEquals(word,r.ordinaryZoneActWord());assertEquals(word,source.ordinaryZoneActWord());
        assertEquals(period,binding.ordinaryPeriod());assertEquals(period,source.ordinaryPeriod());
        assertEquals(CdR11aPlayerAssets.SourceGraph.R4,source.player().sourceGraph());
        assertEquals(p.payload().stage(),source.stage());assertTrue(source.player().ownsProgramme(r));
        assertFalse(source.player().ownsProgramme(null));assertFalse(source.player().ownsR31aAssets(null));
        assertSame(r.terrain(),source.terrain());assertSame(r.sonic(),source.sonic());assertSame(source.sonic(),source.player().sonic());
        assertSame(p.range("PlayerSprites"),binding.visual().mapping());assertEquals(0x232C80,source.mappingAddress());
        assertEquals(188,source.sonic().mappingFrames().size());assertEquals(82944,source.sonic().artReadView().length());
        assertArrayEquals(r.view(p.range("PlayerAnims")).copy(),source.player().animationTable().copy());
        assertArrayEquals(r.view(p.range("PlayerShrunkAnimationMap")).slice(0,60).copy(),source.player().miniAnimations().copy());
        assertArrayEquals(r.view(p.range("ATan2Table")).slice(0,257).copy(),source.player().arcTanTable().copy());
        assertArrayEquals(r.view(p.range("SineTable")).copy(),source.player().sineTable().copy());
        assertArrayEquals(r.playerRole(CdProgrammeReader.Role.SPECIAL).copy(),source.player().specialChunks().copy());
        assertThrows(CdR11aPlayerAssets.UnownedPpzRamp.class,source.player()::rampChunks);
        assertThrows(UnsupportedOperationException.class,source.terrain()::shape);
        assertThrows(UnsupportedOperationException.class,r::terrainShape);
    }
    @Test void equalOriginalsEqualVisualsAndCopiedViewsCannotMintThePrivateRelation()throws Exception {
        var r=reader(CdR4DataLayout.Programme.R41A);var other=TestCdProgrammeTtDefinitions.fresh(CdR4DataLayout.Programme.R41A);
        var source=CdStagePlayerSource.fromProgramme(r);assertFalse(source.player().ownsProgramme(other));
        assertNotSame(source.sonic(),other.sonic());assertEquals(r.mmdSha(),other.mmdSha());
        var foreignVisual=spy(r);doReturn(other.sonic()).when(foreignVisual).sonic();
        assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromProgramme(foreignVisual));
        var copied=spy(r);var role=CdProgrammeReader.Role.ANI;
        doReturn(CdAssetBytes.copyOf(r.role(role).copy())).when(copied).role(role);
        assertThrows(IllegalArgumentException.class,()->CdR11aPlayerAssets.fromProgramme(copied));
        var wrongMap=spy(r);doReturn(0x22DF20).when(wrongMap).playerMappingAddress();
        assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromProgramme(wrongMap));
        var wrong=spy(r);doReturn(reader(CdR4DataLayout.Programme.R42A).definition()).when(wrong).definition();
        assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromProgramme(wrong));
        var foreignTerrain=spy(r);doReturn(other.terrain()).when(foreignTerrain).terrain();
        assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromProgramme(foreignTerrain));
    }
    @Test void wrongProgrammeViewsAreRejectedEvenWhenTheirBytesAreEqual()throws Exception {
        var r=reader(CdR4DataLayout.Programme.R41A);var other=reader(CdR4DataLayout.Programme.R42A);
        var bad=spy(r);doReturn(other.role(CdProgrammeReader.Role.SINE)).when(bad).role(CdProgrammeReader.Role.SINE);
        assertThrows(IllegalArgumentException.class,()->CdR11aPlayerAssets.fromProgramme(bad));
        var foreign=new CdR4DataLayout.Range("PlayerAnims",0,578,"0".repeat(64),"constructed",1);
        assertThrows(IllegalArgumentException.class,()->r.view(foreign));
        assertThrows(IllegalArgumentException.class,()->r.view(other.playerBinding().roles().get(CdProgrammeReader.Role.ANI)));
    }
    @Test void retainedFailureAndUnsupportedFamilyStopBeforeTableOrTerrainReads()throws Exception {
        var r=spy(reader(CdR4DataLayout.Programme.R41A));doThrow(new IllegalStateException("retained failure")).when(r).requireRetained();clearInvocations(r);
        assertThrows(IllegalStateException.class,()->CdStagePlayerSource.fromProgramme(r));
        verify(r,never()).sonic();verify(r,never()).role(any());verify(r,never()).terrain();
        try(var actual=TestCdCczProgrammeData.open()) {
            var unowned=spy(CdProgrammeReader.open(actual,CdStagePayload.R31B.stage()));clearInvocations(unowned);
            assertThrows(UnsupportedOperationException.class,()->CdStagePlayerSource.fromProgramme(unowned));
            verify(unowned,never()).terrain();
        }
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromProgramme(null));
        assertThrows(NullPointerException.class,()->CdR11aPlayerAssets.fromProgramme(null));
    }
    @Test void suppressedTablesAndUnknownBlocksNeverBecomeZeroStagingOrEagerDefaults()throws Exception {
        var r=reader(CdR4DataLayout.Programme.R41A);var bad=spy(r);
        doThrow(new UnsupportedOperationException("suppressed sine")).when(bad).role(CdProgrammeReader.Role.SINE);clearInvocations(bad);
        assertEquals("suppressed sine",assertThrows(UnsupportedOperationException.class,()->CdStagePlayerSource.fromProgramme(bad)).getMessage());
        verify(bad,never()).terrain();verify(bad,never()).decoded(any());
        var source=CdStagePlayerSource.fromProgramme(r);int unknown=source.terrain().blocks().length()/8;
        assertThrows(CdStageTerrain.UnavailableRead.class,()->source.terrain().blockWord(unknown,0,0));
        assertThrows(CdStageTerrain.UnavailableRead.class,()->source.terrain().blockWord(1019,0,0));
        var gate=spy(r);clearInvocations(gate);assertEquals(0x200,gate.ordinaryZoneActWord());
        verify(gate,never()).view(any());verify(gate,never()).decoded(any());verify(gate,never()).terrain();
    }
    @Test void unchangedR3AndLegacyKeep167And198TablesAndValidationOrder()throws Exception {
        var actual=TestCdR31aPlayerCoreAssets.actual();var assets=actual.first();
        var old=CdR11aPlayerAssets.fromR31aAssets(assets);var source=CdStagePlayerSource.fromProgramme(assets.internalReader());
        assertEquals(CdR11aPlayerAssets.SourceGraph.R3,source.player().sourceGraph());assertEquals(167,source.sonic().mappingFrames().size());
        assertEquals(0x100,source.ordinaryZoneActWord());assertEquals(0x22DF20,source.mappingAddress());
        assertArrayEquals(old.animationTable().copy(),source.player().animationTable().copy());assertSame(assets.sonic(),source.sonic());
        assertEquals(198,actual.present().sonic().mappingFrames().size());assertEquals(6,actual.present().player().rampChunks().length());
        var r=spy(assets.internalReader());doReturn(reader(CdR4DataLayout.Programme.R41A).sonic()).when(r).sonic();clearInvocations(r);
        assertEquals("R3 requires retained 167-frame visuals",assertThrows(IllegalArgumentException.class,()->CdR11aPlayerAssets.fromR3Programme(r)).getMessage());
        verify(r,never()).role(any());
    }
    @Test void existingMathReadsCosineFirstThenSineFromActualR4Table()throws Exception {
        var player=CdStagePlayerSource.fromProgramme(reader(CdR4DataLayout.Programme.R41A)).player();var observed=spy(player);clearInvocations(observed);
        var result=new CdPlayerMath(observed).calcSine(0x10A3);var order=inOrder(observed);
        order.verify(observed).sineWord(0xE3);order.verify(observed).sineWord(0xA3);
        assertEquals(player.sineWord(0xA3),result.sine());assertEquals(player.sineWord(0xE3),result.cosine());
        assertEquals(-0xC1,result.sine()); // Native SineTable WORD index$A3, not the anomalous$93 entry.
        assertEquals(-0x75,new CdPlayerMath(player).calcSine(0x1093).sine(),"Native anomalous WORD index$93 stays literal");
        assertEquals(0x20,new CdPlayerMath(player).calcAngle(1,1));
    }
    @Test void publicIngressUsesExistingReaderAndPreservesNullOwnerValidationOrder()throws Exception {
        var unused=mock(com.openggf.game.soniccd.media.CdProgramAssets.class);
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromProgramme(unused,null));
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromProgramme(null,CdStagePayload.R41A.stage()));
        verifyNoInteractions(unused);
        // R11A DATA is registered: actual header validation precedes reader/Player construction.
        // R265 observed this exact missing-header rejection before the new PPZ Player DATA import.
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromProgramme(unused,CdStagePayload.R11A.stage()));
        verify(unused).header();verifyNoMoreInteractions(unused);
        var fixture=TestCdR4TerrainData.actual();
        try(var actual=com.openggf.game.soniccd.media.CdProgramAssets.open(fixture.cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            var source=CdStagePlayerSource.fromProgramme(actual,CdStagePayload.R41A.stage());
            assertEquals(0x200,source.ordinaryZoneActWord());assertEquals(1,source.ordinaryPeriod());
            assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_188,source.sonic().sourceFormat());
            assertNotSame(reader(CdR4DataLayout.Programme.R41A).sonic(),source.sonic());
        }
    }
    @Test void strictConstructedSourceCannotFallThroughToPpzBodyOrMovement()throws Exception {
        var source=CdStagePlayerSource.fromProgramme(reader(CdR4DataLayout.Programme.R41A));
        assertEquals(CdPlayerMovement.UnsupportedBoundary.SOURCE_FAMILY,
            assertThrows(CdPlayerMovement.UnsupportedMotion.class,()->CdPlayerMovement.fromStageSource(source)).boundary());
        assertEquals(CdPlayerBody.UnsupportedBoundary.SOURCE_FAMILY,
            assertThrows(CdPlayerBody.UnsupportedBody.class,()->CdPlayerBody.fromStageSource(source)).boundary());
        // DATA relation only: no state, pool, Snapshot, Frame, Ready, pixel or fake service is constructed.
    }
}
