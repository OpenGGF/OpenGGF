package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Original-USA C/D immutable data relation only. No Frame/FULL/World or invocation authority.
 * Actual positive owners come solely from CdR13FutureAssets.fromPrograms; spies/mocks below are invalid controls only.
 * Source-only tests first; Java UNRUN. Native N1ecc5f3a Sonic/Main146; actual map operands C3DB4/D3DCC.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR13StagePlayerSource {
    @Test void actualGoodFutureBorrowsItsExactImmutableProductsAndNativeRange()throws Exception {
        var f=actual();var a=f.good();var s=CdStagePlayerSource.fromAct3Future(a);
        assertSame(a.terrain(),s.terrain());assertSame(a.player(),s.player());assertSame(a.sonic(),s.sonic());assertSame(s.sonic(),s.player().sonic());
        assertEquals(CdStagePayload.R13C.stage(),s.stage());assertEquals(CdReleaseVariant.USA,s.variant());assertEquals(CdPeriod.GOOD_FUTURE,s.stage().period());assertEquals(3,s.stage().actNumber());
        assertEquals(CdStageTerrain.Shape.PPZ3_GOOD_FUTURE,s.terrain().shape());assertEquals(a.range(CdR13FutureAssets.Asset.MAP_SPR_SONIC).nativeAddress(),s.mappingAddress());assertEquals(0x22DF20,s.mappingAddress());assertEquals(2,s.ordinaryPeriod());assertEquals(167,s.sonic().mappingFrames().size());assertTrue(s.ownsAct3Future(a));
    }
    @Test void actualBadFutureKeepsItsOwnStageShapeAndBorrowedPairDespiteEqualVisualBytes()throws Exception {
        var f=actual();var a=f.bad();var s=CdStagePlayerSource.fromAct3Future(a);
        assertEquals(CdStagePayload.R13D.stage(),s.stage());assertEquals(CdPeriod.BAD_FUTURE,s.stage().period());assertEquals(CdStageTerrain.Shape.PPZ3_BAD_FUTURE,s.terrain().shape());assertEquals(2,s.ordinaryPeriod());
        assertSame(a.terrain(),s.terrain());assertSame(a.player(),s.player());assertSame(a.sonic(),s.sonic());assertEquals(0x22DF20,s.mappingAddress());
        assertNotSame(a.sonic(),f.good().sonic());assertArrayEquals(a.sonic().mappings().copy(),f.good().sonic().mappings().copy());assertTrue(s.ownsAct3Future(a));assertFalse(s.ownsAct3Future(f.good()));
    }
    @Test void referenceRelationRequiresTheActualSelectedAggregateAndIsNotAnInvocationToken()throws Exception {
        var f=actual();assertNotSame(f.good(),f.sameGood());assertEquals(f.good().identity(),f.sameGood().identity());
        var first=CdStagePlayerSource.fromAct3Future(f.good());var again=CdStagePlayerSource.fromAct3Future(f.good());var independent=CdStagePlayerSource.fromAct3Future(f.sameGood());
        assertNotSame(first,again);assertSame(first.player(),again.player());assertSame(first.terrain(),again.terrain());assertTrue(first.ownsAct3Future(f.good()));assertTrue(again.ownsAct3Future(f.good()));
        assertFalse(first.ownsAct3Future(f.sameGood()));assertFalse(first.ownsAct3Future(f.bad()));assertFalse(first.ownsAct3Future(null));assertTrue(independent.ownsAct3Future(f.sameGood()));assertFalse(independent.ownsAct3Future(f.good()));
        assertFalse(first.ownsPresent(null));assertFalse(first.ownsPast(null));assertFalse(first.ownsAct2(null,null));assertFalse(first.ownsAct2Past(f.oldTerrain(),f.oldPlayer()));
        var old=CdStagePlayerSource.fromAct2Past(f.oldTerrain(),f.oldPlayer());assertTrue(old.ownsAct2Past(f.oldTerrain(),f.oldPlayer()));assertFalse(old.ownsAct3Future(f.good()));assertFalse(old.ownsAct3Future(f.bad()));assertFalse(old.ownsAct3Future(null));assertEquals(0,old.ordinaryPeriod());assertEquals(0x230F60,old.mappingAddress());
    }
    @Test void nullFactoryHasNoDefaultSource() {
        assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct3Future(null));
    }
    @Test void invalidIdentityOrShapeCannotRelabelARealFutureOwner()throws Exception {
        var a=actual().good();var real=a.identity();
        for(int kind=0;kind<6;kind++) {
            var bad=spy(a);var id=mock(CdR13FutureAssets.Identity.class);
            when(id.variant()).thenReturn(real.variant());when(id.stage()).thenReturn(real.stage());when(id.file()).thenReturn(real.file());when(id.domain()).thenReturn(real.domain());when(id.mmdSha256()).thenReturn(real.mmdSha256());when(id.observedWholeDiscSha256()).thenReturn(real.observedWholeDiscSha256());
            switch(kind){case 0->when(id.variant()).thenReturn(CdReleaseVariant.EUROPE);case 1->when(id.stage()).thenReturn(CdStagePayload.R12B.stage());case 2->when(id.file()).thenReturn(CdNativeFile.R13D);case 3->when(id.domain()).thenReturn(CdMmdImage.Domain.RELOCATED_COPY);case 4->when(id.mmdSha256()).thenReturn("0".repeat(64));case 5->when(id.observedWholeDiscSha256()).thenReturn("0".repeat(64));}
            doReturn(id).when(bad).identity();assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct3Future(bad));verify(bad,never()).view(any());verify(bad,never()).placements();verify(bad,never()).plcs();
        }
        var wrongShape=spy(a);var terrain=spy(a.terrain());doReturn(CdStageTerrain.Shape.PPZ3_BAD_FUTURE).when(terrain).shape();doReturn(terrain).when(wrongShape).terrain();assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct3Future(wrongShape));
    }
    @Test void missingProductsWrongRangeOrUnequalBorrowedPairNeverFallsBack()throws Exception {
        var f=actual();var a=f.good();
        var noId=spy(a);doReturn(null).when(noId).identity();assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct3Future(noId));
        var noTerrain=spy(a);doReturn(null).when(noTerrain).terrain();assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct3Future(noTerrain));
        var noPlayer=spy(a);doReturn(null).when(noPlayer).player();assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct3Future(noPlayer));
        var noSonic=spy(a);doReturn(null).when(noSonic).sonic();assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct3Future(noSonic));
        var noRange=spy(a);doReturn(null).when(noRange).range(CdR13FutureAssets.Asset.MAP_SPR_SONIC);assertThrows(NullPointerException.class,()->CdStagePlayerSource.fromAct3Future(noRange));
        var shifted=spy(a);var r=a.range(CdR13FutureAssets.Asset.MAP_SPR_SONIC);doReturn(new CdR13FutureAssets.FileRange(r.file(),r.domain(),r.fileOffset()+2,r.length())).when(shifted).range(CdR13FutureAssets.Asset.MAP_SPR_SONIC);assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct3Future(shifted));
        var foreignRange=spy(a);doReturn(f.bad().range(CdR13FutureAssets.Asset.MAP_SPR_SONIC)).when(foreignRange).range(CdR13FutureAssets.Asset.MAP_SPR_SONIC);assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct3Future(foreignRange));
        var equalBytes=spy(a);doReturn(f.sameGood().sonic()).when(equalBytes).sonic();assertArrayEquals(a.sonic().mappings().copy(),f.sameGood().sonic().mappings().copy());assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct3Future(equalBytes));
        var foreignPair=spy(a);doReturn(f.oldPlayer().player()).when(foreignPair).player();doReturn(f.oldPlayer().sonic()).when(foreignPair).sonic();assertThrows(IllegalArgumentException.class,()->CdStagePlayerSource.fromAct3Future(foreignPair));
    }
    @Test void closedDiscLifetimeAndDetachedCopiesCannotMutateTheRelation()throws Exception {
        for(var a:List.of(actual().good(),actual().bad())) {
            var s=CdStagePlayerSource.fromAct3Future(a);byte[] before=s.sonic().rawArt().copy(),copy=s.sonic().rawArt().copy();Arrays.fill(copy,(byte)99);assertArrayEquals(before,s.sonic().rawArt().copy());
            assertThrows(ReadOnlyBufferException.class,()->s.terrain().patterns().buffer().put(0,(byte)1));assertThrows(UnsupportedOperationException.class,()->s.player().animations().clear());assertThrows(UnsupportedOperationException.class,()->s.sonic().mappingFrames().clear());
            assertSame(a.terrain(),s.terrain());assertSame(a.player(),s.player());assertSame(a.sonic(),s.sonic());assertEquals(2,s.ordinaryPeriod());assertTrue(s.ownsAct3Future(a));
        }
    }
    @Test void newSurfaceAddsOnlyTheNamedDataFactoryRelationAndOneFinalOwnerField() {
        assertTrue(Modifier.isFinal(CdStagePlayerSource.class.getModifiers()));var constructors=CdStagePlayerSource.class.getDeclaredConstructors();assertEquals(5,constructors.length);for(var c:constructors)assertTrue(Modifier.isPrivate(c.getModifiers()));
        assertEquals(Set.of(List.of(CdR11aAssets.class,CdR11bAssets.class),List.of(CdR12aTerrainAssets.class,CdR12aPlayerAssets.class),List.of(CdR12bTerrainAssets.class,CdR12bPlayerAssets.class),List.of(CdR13FutureAssets.class),List.of(CdR31aAssets.class)),Arrays.stream(constructors).map(c->Arrays.asList(c.getParameterTypes())).collect(Collectors.toSet()));
        var names=Arrays.stream(CdStagePlayerSource.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).collect(Collectors.toSet());
        assertEquals(Set.of("fromPresent/1","fromPast/1","fromAct2/2","fromAct2Past/2","fromAct3Future/1","terrain/0","player/0","sonic/0","mappingAddress/0","variant/0","stage/0","ordinaryPeriod/0","ownsPresent/1","ownsPast/1","ownsAct2/2","ownsAct2Past/2","ownsAct3Future/1","fromR31aAssets/1","ownsR31aAssets/1","ordinaryZoneActWord/0"),names);
        var fields=CdStagePlayerSource.class.getDeclaredFields();assertEquals(15,fields.length);assertEquals(1,Arrays.stream(fields).filter(f->f.getType()==CdR13FutureAssets.class).count());assertEquals(1,Arrays.stream(fields).filter(f->f.getName().equals("r31a")&&f.getType()==CdR31aAssets.class).count());for(var f:fields){assertTrue(Modifier.isPrivate(f.getModifiers()));assertTrue(Modifier.isFinal(f.getModifiers()));assertFalse(f.getType().isArray());}
    }

    private record Fixture(CdR13FutureAssets good,CdR13FutureAssets bad,CdR13FutureAssets sameGood,CdR12bTerrainAssets oldTerrain,CdR12bPlayerAssets oldPlayer){}
    private Fixture retained;
    private Fixture actual()throws Exception {
        if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original CD media is absent");Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            retained=new Fixture(CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13D.stage()),CdR13FutureAssets.fromPrograms(p,CdStagePayload.R13C.stage()),CdR12bTerrainAssets.fromPrograms(p),CdR12bPlayerAssets.fromPrograms(p));
        }return retained;
    }
}
