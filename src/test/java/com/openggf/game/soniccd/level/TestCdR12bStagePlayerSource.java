package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual USA R12B relation plus constructed metadata negatives, not Frame/World/phase authority.
 * Native pin1ecc5f3 Act 2 Past.asm:20-30,91; Sonic/Main.asm:146,2645,3066,3088.
 * Source-only tests written before the production mirror. Java/JUnit UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12bStagePlayerSource {
    private CdR12bTerrainAssets terrain,otherTerrain;
    private CdR12bPlayerAssets player,otherPlayer;
    private CdR12aPlayerAssets foreignPlayer;
    @BeforeAll void actualSources()throws Exception{
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(programs);player=CdR12bPlayerAssets.fromPrograms(programs);
            otherTerrain=CdR12bTerrainAssets.fromPrograms(programs);otherPlayer=CdR12bPlayerAssets.fromPrograms(programs);foreignPlayer=CdR12aPlayerAssets.fromPrograms(programs);
        }
    }
    @Test void authenticPastPairBorrowsItsOwnStageShapeAndOrdinaryPeriodWithoutRelabellingAct2Present(){
        var source=CdStagePlayerSource.fromAct2Past(terrain,player);assertEquals(terrain.identity(),player.identity());assertNotSame(terrain.profile(),player.profile());
        assertSame(terrain.terrain(),source.terrain());assertSame(player.player(),source.player());assertSame(player.sonic(),source.sonic());assertSame(source.sonic(),source.player().sonic());
        assertEquals(CdStagePayload.R12B.stage(),source.stage());assertEquals(CdReleaseVariant.USA,source.variant());assertEquals(0,source.ordinaryPeriod());
        assertEquals(CdPeriod.PAST,source.stage().period());assertEquals(2,source.stage().actNumber());assertEquals(CdStageTerrain.Shape.PPZ2_PAST,source.terrain().shape());
        assertEquals(0x230F60,source.mappingAddress());assertFalse(source.ownsPresent(null));assertFalse(source.ownsPast(null));assertFalse(source.ownsAct2(null,null));
    }
    @Test void referenceOwnershipRequiresTheExactSelectedPairAndIndependentSameDiscProductsMayBePaired(){
        var source=CdStagePlayerSource.fromAct2Past(terrain,player);assertTrue(source.ownsAct2Past(terrain,player));
        assertNotSame(terrain,otherTerrain);assertNotSame(player,otherPlayer);assertEquals(terrain.identity(),otherTerrain.identity());assertEquals(player.identity(),otherPlayer.identity());
        assertFalse(source.ownsAct2Past(otherTerrain,player));assertFalse(source.ownsAct2Past(terrain,otherPlayer));assertFalse(source.ownsAct2Past(otherTerrain,otherPlayer));
        assertFalse(source.ownsAct2Past(null,player));assertFalse(source.ownsAct2Past(terrain,null));assertFalse(source.ownsAct2Past(null,null));
        var independent=CdStagePlayerSource.fromAct2Past(terrain,otherPlayer);assertSame(otherPlayer.sonic(),independent.sonic());assertTrue(independent.ownsAct2Past(terrain,otherPlayer));
        var repeated=CdStagePlayerSource.fromAct2Past(terrain,player);assertNotSame(source,repeated);assertSame(source.player(),repeated.player());assertSame(source.terrain(),repeated.terrain());
    }
    @Test void nullArgumentsCannotSelectAnotherSourceFamily(){
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct2Past(null,null));
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct2Past(null,player));assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct2Past(terrain,null));
    }
    @Test void wrongMmdOrDiscLineageRejectsEvenWhenTheTwoConstructedMetadataRecordsAgree(){
        var id=player.identity();
        for(var bad:new CdR12bTerrainAssets.Identity[]{new CdR12bTerrainAssets.Identity(id.variant(),id.stage(),id.file(),id.domain(),"0".repeat(64),id.observedWholeDiscSha256()),
                new CdR12bTerrainAssets.Identity(id.variant(),id.stage(),id.file(),id.domain(),id.mmdSha256(),"0".repeat(64))}){
            var p=declaredPlayer(bad,player.profile(),player.player(),player.sonic());assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct2Past(terrain,p));
        }
        var wrong=new CdR12bTerrainAssets.Identity(id.variant(),id.stage(),id.file(),id.domain(),"0".repeat(64),id.observedWholeDiscSha256());
        assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct2Past(declaredTerrain(wrong,terrain.profile(),terrain.terrain()),declaredPlayer(wrong,player.profile(),player.player(),player.sonic())));
    }
    @Test void foreignProfileVariantStageFileDomainOrHashNeverLaundersTheR12bPair(){
        for(int kind=0;kind<6;kind++){
            var p=profile();switch(kind){case 0->when(p.variant()).thenReturn(CdReleaseVariant.EUROPE);case 1->when(p.stage()).thenReturn(CdStagePayload.R12A.stage());
                case 2->when(p.file()).thenReturn(CdNativeFile.R12A);case 3->when(p.domain()).thenReturn(CdMmdImage.Domain.RELOCATED_COPY);case 4->when(p.expectedMmdSha256()).thenReturn("0".repeat(64));case 5->when(p.range(CdR12bProfile.Asset.SPEC_CHUNKS)).thenReturn(new CdR12bProfile.FileRange(CdNativeFile.R12B,CdMmdImage.Domain.ORIGINAL_WORD_RAM,0x29D4,4));}
            assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct2Past(declaredTerrain(terrain.identity(),p,terrain.terrain()),player));
            assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct2Past(terrain,declaredPlayer(player.identity(),p,player.player(),player.sonic())));
        }
    }
    @Test void foreignIdentityFieldsAndWrongTerrainShapeRejectBeforeSourceSelection(){
        for(int kind=0;kind<4;kind++){
            var id=mock(CdR12bTerrainAssets.Identity.class);var real=terrain.identity();when(id.variant()).thenReturn(real.variant());when(id.stage()).thenReturn(real.stage());
            when(id.file()).thenReturn(real.file());when(id.domain()).thenReturn(real.domain());when(id.mmdSha256()).thenReturn(real.mmdSha256());when(id.observedWholeDiscSha256()).thenReturn(real.observedWholeDiscSha256());
            switch(kind){case 0->when(id.variant()).thenReturn(CdReleaseVariant.JAPAN);case 1->when(id.stage()).thenReturn(CdStagePayload.R11B.stage());case 2->when(id.file()).thenReturn(CdNativeFile.R12A);case 3->when(id.domain()).thenReturn(CdMmdImage.Domain.RELOCATED_COPY);}
            assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct2Past(declaredTerrain(id,terrain.profile(),terrain.terrain()),declaredPlayer(id,player.profile(),player.player(),player.sonic())));
        }
        var shape=mock(CdStageTerrain.class);when(shape.shape()).thenReturn(CdStageTerrain.Shape.PPZ2_PRESENT);
        assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct2Past(declaredTerrain(terrain.identity(),terrain.profile(),shape),player));
    }
    @Test void missingMetadataOrBorrowedProductsNeverFallsBack(){
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct2Past(declaredTerrain(null,terrain.profile(),terrain.terrain()),player));
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct2Past(declaredTerrain(terrain.identity(),null,terrain.terrain()),player));
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct2Past(declaredTerrain(terrain.identity(),terrain.profile(),null),player));
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct2Past(terrain,declaredPlayer(null,player.profile(),player.player(),player.sonic())));
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct2Past(terrain,declaredPlayer(player.identity(),null,player.player(),player.sonic())));
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct2Past(terrain,declaredPlayer(player.identity(),player.profile(),null,player.sonic())));
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct2Past(terrain,declaredPlayer(player.identity(),player.profile(),player.player(),null)));
    }
    @Test void equalVisualBytesDoNotReplaceTheExactSonicBorrowedByThePlayerFormat(){
        assertNotSame(player.sonic(),otherPlayer.sonic());assertArrayEquals(player.sonic().mappings().copy(),otherPlayer.sonic().mappings().copy());
        assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct2Past(terrain,declaredPlayer(player.identity(),player.profile(),player.player(),otherPlayer.sonic())));
        assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct2Past(terrain,declaredPlayer(player.identity(),player.profile(),foreignPlayer.player(),player.sonic())));
    }
    @Test void consistentConstructedRelationsReadOnlyMetadataAndProductsAndGrantNoProgrammeLease(){
        var t=declaredTerrain(terrain.identity(),terrain.profile(),terrain.terrain());var p=declaredPlayer(player.identity(),player.profile(),player.player(),player.sonic());
        var source=CdStagePlayerSource.fromAct2Past(t,p);assertTrue(source.ownsAct2Past(t,p));assertFalse(source.ownsAct2Past(terrain,player));
        assertSame(terrain.terrain(),source.terrain());assertSame(player.player(),source.player());assertSame(player.sonic(),source.sonic());
        verify(t,never()).view(any());verify(t,never()).loadPlan();verify(t,never()).blocksDecode();verify(t,never()).levelTilesDecode();verify(t,never()).placements();verify(t,never()).sectionSources();
        verify(p,never()).view(any());verify(p,never()).sonicArtReadRange();verify(p,never()).touchDimensions();
    }
    private static CdR12bTerrainAssets declaredTerrain(CdR12bTerrainAssets.Identity id,CdR12bProfile p,CdStageTerrain t){var a=mock(CdR12bTerrainAssets.class);when(a.identity()).thenReturn(id);when(a.profile()).thenReturn(p);when(a.terrain()).thenReturn(t);return a;}
    private static CdR12bPlayerAssets declaredPlayer(CdR12bTerrainAssets.Identity id,CdR12bProfile p,CdR11aPlayerAssets f,CdSonicVisualAssets s){var a=mock(CdR12bPlayerAssets.class);when(a.identity()).thenReturn(id);when(a.profile()).thenReturn(p);when(a.player()).thenReturn(f);when(a.sonic()).thenReturn(s);return a;}
    private CdR12bProfile profile(){var a=terrain.profile();var p=mock(CdR12bProfile.class);when(p.variant()).thenReturn(a.variant());when(p.stage()).thenReturn(a.stage());when(p.file()).thenReturn(a.file());when(p.domain()).thenReturn(a.domain());when(p.expectedMmdSha256()).thenReturn(a.expectedMmdSha256());when(p.range(CdR12bProfile.Asset.SPEC_CHUNKS)).thenReturn(a.range(CdR12bProfile.Asset.SPEC_CHUNKS));return p;}
}
