package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SOURCE ONLY, written before the owned production mirror. Compilation/JUnit UNRUN.
 * Actual retail source bindings plus explicitly CONSTRUCTED finite Frame/images are component inputs.
 * Mockito supplies only the admission EDGE: accepting it is not a real World/Primary/custody proof.
 * Actual private staged/completed save authority is tested by the separately owned World packet.
 * No private snapshot clone, gameplay hydration, route/13487/graphics/parity claim or new service.
 * Reentry/lifetime flags and direct retained-field changes below are labelled fault injection only.
 * N1ecc5f3 Sonic/Main.asm:618 high scrollLock byte; Act2 Scroll:184-205 locked early return.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@Isolated
class TestSonicCdAct2TimeWarpPresentation {
    private static CdR11aAssets present;
    private static CdR12aTerrainAssets terrain, foreignTerrain;
    private static CdR12aPlayerAssets playerAssets, foreignPlayer;

    @BeforeAll static void actualRetailBindings() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            present=CdR11aAssetLoader.load(p,CdReleaseVariant.USA,CdStagePayload.R11A.stage());terrain=CdR12aTerrainAssets.fromPrograms(p);playerAssets=CdR12aPlayerAssets.fromPrograms(p);
        }
        try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){foreignTerrain=CdR12aTerrainAssets.fromPrograms(p);foreignPlayer=CdR12aPlayerAssets.fromPrograms(p);}
    }

    @Test void packageOnlySurfaceUsesTheTwoExistingSemanticCarriers() throws Exception {
        for(String name:List.of("borrowAct2TimeWarpWorld","validateAct2TimeWarpLock","applyAct2TimeWarpLock")){
            var method=java.util.Arrays.stream(SonicCdPlayerPresentation.class.getDeclaredMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();
            assertFalse(Modifier.isPublic(method.getModifiers()));assertFalse(Modifier.isProtected(method.getModifiers()));assertFalse(Modifier.isPrivate(method.getModifiers()));
            assertEquals(name.startsWith("borrow")?1:2,method.getParameterCount());
        }
        assertEquals(2,CdOpeningObjectRuntime.TimeWarpWorldCut.class.getRecordComponents().length);assertEquals(2,CdOpeningObjectRuntime.TimeWarpLockPublication.class.getRecordComponents().length);
        assertEquals(CdOpeningObjectRuntime.TimeWarpLockPublication.class,SonicCdGameModule.class.getDeclaredMethod("requireAct2TimeWarpAdmission",CdPlayerFrameRuntime.class).getReturnType());
    }

    @Test void rawOneBorrowReturnsTheActualSourcePairAndChangesNoPartition() throws Exception {
        var h=new H(1,0xA55A);var before=h.retained();var cut=h.p.borrowAct2TimeWarpWorld(h.f);
        assertSame(h.fg,cut.foreground());assertSame(h.bg,cut.background());assertEquals(before,h.retained());verify(h.module).requireAct2TimeWarpAdmission(h.f);
    }

    @Test void constructedInOperationFaultProvesBorrowDoesNotCaptureTheFrame() throws Exception {
        var h=new H(1,0x5A);set(h.f,"inOperation",true); // Explicit fault flag, not a real private Primary invocation.
        try{assertThrows(IllegalStateException.class,h.f::captureShared);var cut=h.p.borrowAct2TimeWarpWorld(h.f);assertSame(h.fg,cut.foreground());assertSame(h.bg,cut.background());}
        finally{set(h.f,"inOperation",false);}
        assertEquals(0,h.f.vIntClock().deltaSinceAdmittedCut());
    }

    @Test void originalAdmissionEdgeAllowsOnlyTheHighByteStoreWithAllLowByteVectorsAndNonwrites() throws Exception {
        for(int high:new int[]{0,1,0xA5,0xFF})for(int low:new int[]{0,1,0x5A,0x80,0xFF}){
            var h=new H(1,(high<<8)|low);var before=h.retained();var ready=h.ready();h.admit=()->ready;
            h.p.validateAct2TimeWarpLock(h.f,ready);assertEquals(before,h.retained());h.p.applyAct2TimeWarpLock(h.f,ready);
            assertSame(ready.finalForeground(),field(h.p,"scrollState"));assertNotSame(before.fg(),field(h.p,"scrollState"));assertEquals(0x100|low,h.p.act2ForegroundImage().scrollLock());
            assertSame(before.bg(),field(h.p,"backgroundState"));assertSame(before.plane(),field(h.p,"planeState"));assertSame(before.copies(),field(h.p,"drawCopies"));
            assertSame(before.publishedPlane(),field(h.p,"publishedPlane"));assertSame(before.owner(),field(h.p,"scrollCaptureOwner"));assertEquals(before.actor(),h.f.captureActor());assertEquals(before.shared(),h.f.captureShared());
            assertEquals(before.load(),field(h.p,"loadScrollPending"));assertEquals(before.setup(),field(h.p,"checkpointSetupPending"));
        }
    }

    @Test void validationHasNoStoreAndDoesNotConsumeTheOriginalPublication() throws Exception {
        var h=new H(1,0xA5);var before=h.retained();var ready=h.ready();h.admit=()->ready;
        h.p.validateAct2TimeWarpLock(h.f,ready);h.p.validateAct2TimeWarpLock(h.f,ready);assertEquals(before,h.retained());h.p.applyAct2TimeWarpLock(h.f,ready);
        assertSame(ready.finalForeground(),h.p.act2ForegroundImage());assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,ready));
    }

    @Test void equalClonePublicationCannotAcquireTheMockedOriginalReferenceAuthority() throws Exception {
        var h=new H(1,0x5A);var ready=h.ready();var clone=new CdOpeningObjectRuntime.TimeWarpLockPublication(ready.expected(),ready.finalForeground());assertEquals(ready,clone);assertNotSame(ready,clone);h.admit=()->ready;var before=h.retained();
        assertThrows(IllegalStateException.class,()->h.p.validateAct2TimeWarpLock(h.f,clone));assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,clone));assertEquals(before,h.retained());
    }

    @Test void publicValueEqualityAndNormalRawOneAdmissionDoNotGrantLockAuthority() throws Exception {
        var h=new H(1,0x5A);var ready=h.ready();var before=h.retained(); // Ordinary mock admission returns null.
        assertThrows(IllegalStateException.class,()->h.p.validateAct2TimeWarpLock(h.f,ready));assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,ready));assertEquals(before,h.retained());
        assertThrows(NullPointerException.class,()->h.p.validateAct2TimeWarpLock(h.f,null));
    }

    @Test void aNoopReferenceDoesNotReplaceTheOriginalNativeSameValueWrite() throws Exception {
        var h=new H(1,0x15A);var cut=h.p.borrowAct2TimeWarpWorld(h.f);var bad=new CdOpeningObjectRuntime.TimeWarpLockPublication(cut,cut.foreground());h.admit=()->bad;var before=h.retained();
        assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,bad));assertEquals(before,h.retained());
        var real=h.ready();assertEquals(cut.foreground(),real.finalForeground());assertNotSame(cut.foreground(),real.finalForeground());h.admit=()->real;h.p.applyAct2TimeWarpLock(h.f,real);assertSame(real.finalForeground(),h.p.act2ForegroundImage());
    }

    @Test void everyOtherForegroundFieldAndWrongLockByteIsRejectedBeforeAdoption() throws Exception {
        for(int index=0;index<10;index++){var h=new H(1,0x5A);var cut=h.p.borrowAct2TimeWarpWorld(h.f);var next=replace(lock(cut.foreground()),index,value(lock(cut.foreground()),index)+1);var bad=new CdOpeningObjectRuntime.TimeWarpLockPublication(cut,next);h.admit=()->bad;var before=h.retained();
            assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,bad));assertEquals(before,h.retained());}
        for(int word:new int[]{0x005A,0x025A,0x015B}){var h=new H(1,0x5A);var cut=h.p.borrowAct2TimeWarpWorld(h.f);var bad=new CdOpeningObjectRuntime.TimeWarpLockPublication(cut,replace(lock(cut.foreground()),10,word));h.admit=()->bad;var before=h.retained();assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,bad));assertEquals(before,h.retained());}
    }

    @Test void equalForegroundPreimageCloneIsNotTheActualBorrowedImage() throws Exception {
        var h=new H(1,0x5A);var clone=replace(h.fg,0,h.fg.cameraX());assertEquals(h.fg,clone);assertNotSame(h.fg,clone);
        var bad=new CdOpeningObjectRuntime.TimeWarpLockPublication(new CdOpeningObjectRuntime.TimeWarpWorldCut(clone,h.bg),lock(clone));h.admit=()->bad;var before=h.retained();assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,bad));assertEquals(before,h.retained());
    }

    @Test void equalBackgroundPreimageCloneIsNotTheActualBorrowedImage() throws Exception {
        var h=new H(1,0x5A);var clone=background();assertBackgroundValueEquals(h.bg,clone);assertNotSame(h.bg,clone);
        assertNotSame(h.bg.crossingRam(),clone.crossingRam());assertNotSame(h.bg.cloudRam(),clone.cloudRam());
        assertNotSame(h.bg.deformationRam(),clone.deformationRam());assertNotSame(h.bg.hscrollRam(),clone.hscrollRam());
        var bad=new CdOpeningObjectRuntime.TimeWarpLockPublication(new CdOpeningObjectRuntime.TimeWarpWorldCut(h.fg,clone),lock(h.fg));h.admit=()->bad;
        var before=h.retained();assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,bad));assertEquals(before,h.retained());
    }

    @Test void staleActualForegroundCasDoesNotOverwriteTheNewImage() throws Exception {
        var h=new H(1,0x5A);var ready=h.ready();h.admit=()->ready;var changed=replace(h.fg,0,h.fg.cameraX()+1);set(h.p,"scrollState",changed);var before=h.retained();
        assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,ready));assertEquals(before,h.retained());assertSame(changed,h.p.act2ForegroundImage());
    }

    @Test void staleActualBackgroundCasDoesNotOverwriteAnyForegroundField() throws Exception {
        var h=new H(1,0x5A);var ready=h.ready();h.admit=()->ready;var b=h.bg;var changed=new CdR11aBackgroundScroll.State(b.cameraBgX()+1,b.cameraBgY(),b.cameraBg2X(),b.cameraBg2Y(),b.cameraBg3X(),b.cameraBg3Y(),b.crossingRam(),b.scrollFlagsBg(),b.scrollFlagsBg2(),b.scrollFlagsBg3(),b.cloudRam(),b.deformationRam(),b.hscrollRam(),b.vscroll());set(h.p,"backgroundState",changed);var before=h.retained();
        assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,ready));assertEquals(before,h.retained());
    }

    @Test void foreignFrameAndForeignRealAssetRelationFailBeforeTheAdmissionEdge() throws Exception {
        var h=new H(1,0x5A);var other=frame(terrain,playerAssets,1,1);var before=h.retained();
        assertThrows(IllegalArgumentException.class,()->h.p.borrowAct2TimeWarpWorld(other));verify(h.module,never()).requireAct2TimeWarpAdmission(other);assertEquals(before,h.retained());
        var p=new SonicCdPlayerPresentation(h.module);var foreign=frame(foreignTerrain,foreignPlayer,1,1);
        assertThrows(IllegalArgumentException.class,()->p.bindAct2Source(terrain,playerAssets,foreign,h.fg,h.bg,h.plane,h.copies));
        var old=CdPlayerFrameRuntime.externalTouchRequiredDevelopment(present);assertThrows(IllegalArgumentException.class,()->p.bindAct2Source(terrain,playerAssets,old,h.fg,h.bg,h.plane,h.copies));
    }

    @Test void rawOneOrdinaryAndObservationPathsHonorRejectedCustodyWithoutMutating() throws Exception {
        var h=new H(1,0x15A);var captured=h.p.captureAct2Scroll();var before=h.retained();
        var failure=new IllegalStateException("constructed raw-one custody rejection");h.admit=()->{throw failure;};
        assertSame(failure,assertThrows(IllegalStateException.class,h.p::captureAct2Scroll));
        assertSame(failure,assertThrows(IllegalStateException.class,h.p::validateAct2LevelLoadPlane));
        assertSame(failure,assertThrows(IllegalStateException.class,()->h.p.restoreAct2Scroll(captured)));
        assertEquals(before,h.retained());assertFalse(h.f.closed());h.admit=()->null;
        h.p.validateAct2LevelLoadPlane();h.p.restoreAct2Scroll(captured);assertTrue(h.p.sameAct2Scroll(captured));
    }

    @Test void arbitraryMarkedFramesDoNotGainOrdinaryCaptureRestoreOrLockAdmission() throws Exception {
        for(int period:new int[]{0x80,0x81,0x82}){var h=new H(period,0x15A);var before=h.retained();
            assertThrows(IllegalStateException.class,()->h.p.borrowAct2TimeWarpWorld(h.f));assertThrows(IllegalStateException.class,h.p::validateAct2LevelLoadPlane);
            assertThrows(IllegalStateException.class,h.p::captureAct2Scroll);assertEquals(before,h.retained());}
    }

    @Test void onlyTheMockedOriginalPublicationWindowPermitsMarkedPreCustodyAdoption() throws Exception {
        var h=new H(0x80,0x5A);var cut=new CdOpeningObjectRuntime.TimeWarpWorldCut(h.fg,h.bg);var ready=new CdOpeningObjectRuntime.TimeWarpLockPublication(cut,lock(h.fg));h.admit=()->ready;var before=h.retained();
        h.p.validateAct2TimeWarpLock(h.f,ready);h.p.applyAct2TimeWarpLock(h.f,ready);assertSame(ready.finalForeground(),field(h.p,"scrollState"));assertSame(before.bg(),field(h.p,"backgroundState"));
        assertEquals(0x80,h.f.environment().movement().gates().timeZone());assertEquals(before.actor(),h.f.captureActor());assertEquals(before.shared(),h.f.captureShared());
        // The mock does not establish saved42/spawn2/live Primary ownership; that is World's test obligation.
    }

    @Test void mockCompletedMarkedCustodyAllowsCapturedValuesButNeverAnotherLockPublication() throws Exception {
        for(int period:new int[]{0x80,0x81,0x82}){var h=new H(period,0x15A);h.admit=()->null;h.p.validateAct2LevelLoadPlane();var cut=h.p.captureAct2Scroll();h.p.restoreAct2Scroll(cut);assertTrue(h.p.sameAct2Scroll(cut));
            var publicValue=new CdOpeningObjectRuntime.TimeWarpLockPublication(new CdOpeningObjectRuntime.TimeWarpWorldCut(h.fg,h.bg),lock(h.fg));assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,publicValue));}
    }

    @Test void rawPeriodRangeAndZoneStayNarrowWhileUnmarkedObservationKeepsItsLegacyShape() throws Exception {
        for(int period:new int[]{0,2,3,0x7F,0x83,0xFF}){var h=new H(period,0x5A);h.admit=()->null;assertThrows(IllegalArgumentException.class,()->h.p.borrowAct2TimeWarpWorld(h.f));assertThrows(IllegalArgumentException.class,h.p::validateAct2LevelLoadPlane);
            if((period&0x80)!=0)assertThrows(IllegalArgumentException.class,h.p::captureAct2Scroll);else assertNotNull(h.p.captureAct2Scroll());}
        var h=new H(1,0x5A,2);h.admit=()->null;assertThrows(IllegalArgumentException.class,()->h.p.borrowAct2TimeWarpWorld(h.f));assertThrows(IllegalArgumentException.class,h.p::validateAct2LevelLoadPlane);
    }

    @Test void retiredFrameAndReboundSourceCannotReuseOldCapturesOrPublicationAuthority() throws Exception {
        var h=new H(1,0x5A);var ready=h.ready();var capture=h.p.captureAct2Scroll();h.f.close();assertThrows(IllegalStateException.class,()->h.p.borrowAct2TimeWarpWorld(h.f));assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,ready));
        h.p.unbind();var fresh=frame(terrain,playerAssets,1,1);h.p.bindAct2Source(terrain,playerAssets,fresh,h.fg,h.bg,h.plane,h.copies);when(h.module.requireAct2TimeWarpAdmission(fresh)).thenReturn(null);
        assertThrows(IllegalArgumentException.class,()->h.p.restoreAct2Scroll(capture));assertThrows(IllegalArgumentException.class,()->h.p.borrowAct2TimeWarpWorld(h.f));assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(fresh,ready));
    }

    @Test void renderReentryIsRejectedWithoutAnyAdmissionCallbackOrStore() throws Exception {
        for(String field:List.of("drawingPast","drawingAct2")){var h=new H(1,0x5A);var ready=h.ready();h.admit=()->ready;set(h.p,field,true);clearInvocations(h.module);
            try{assertThrows(IllegalStateException.class,()->h.p.borrowAct2TimeWarpWorld(h.f));assertThrows(IllegalStateException.class,()->h.p.applyAct2TimeWarpLock(h.f,ready));verify(h.module,never()).requireAct2TimeWarpAdmission(h.f);assertSame(h.fg,field(h.p,"scrollState"));}
            finally{set(h.p,field,false);}}
    }

    @Test void modulePredicateCannotReenterPortsCaptureRestoreOrTitleMutation() throws Exception {
        var h=new H(1,0x5A);var captured=h.p.captureAct2Scroll();var before=h.retained();h.admit=()->{
            assertThrows(IllegalStateException.class,()->h.p.borrowAct2TimeWarpWorld(h.f));assertThrows(IllegalStateException.class,h.p::captureAct2Scroll);assertThrows(IllegalStateException.class,()->h.p.restoreAct2Scroll(captured));
            assertThrows(IllegalStateException.class,()->h.p.prepareAct2TitleScrollLock(h.f,captured,0));return null;};
        h.p.borrowAct2TimeWarpWorld(h.f);assertEquals(before,h.retained());
    }

    @Test void readonlyAdmissionMutationIsDetectedOnSuccessWithoutRepairingItsPrefix() throws Exception {
        var h=new H(1,0x5A);var changed=copies();h.admit=()->{try{set(h.p,"drawCopies",changed);}catch(Exception e){throw new AssertionError(e);}return null;};
        assertThrows(IllegalStateException.class,()->h.p.borrowAct2TimeWarpWorld(h.f));assertSame(changed,field(h.p,"drawCopies"));assertSame(h.fg,field(h.p,"scrollState"));assertSame(h.bg,field(h.p,"backgroundState"));
        h.admit=()->null;assertSame(h.fg,h.p.borrowAct2TimeWarpWorld(h.f).foreground()); // Guard retires even after a fault.
    }

    @Test void readonlyAdmissionOriginalThrowKeepsIdentityAndSuppressesVerificationFault() throws Exception {
        var h=new H(1,0x5A);var original=new IllegalArgumentException("actual admission throw");var changed=plane();h.admit=()->{try{set(h.p,"planeState",changed);}catch(Exception e){throw new AssertionError(e);}throw original;};
        assertSame(original,assertThrows(IllegalArgumentException.class,()->h.p.borrowAct2TimeWarpWorld(h.f)));assertEquals(1,original.getSuppressed().length);assertSame(changed,field(h.p,"planeState"));assertSame(h.fg,field(h.p,"scrollState"));
    }

    @Test void actualOldR11AOrdinaryControlKeepsItsLegacyAdmissionAndRejectsTheNewAct2Port() throws Exception {
        var fixture=new TestSonicCdPastLoadingWorld();fixture.setup();
        try{var h=TestSonicCdPastLoadingWorld.opening();for(int call=1;call<=64;call++)TestSonicCdPastLoadingWorld.step(h,TestSonicCdPastLoadingWorld.route(call));
            var f=h.module().requirePlayerFrame();var source=f.stageSource();var p=(SonicCdPlayerPresentation)TestSonicCdPastLoadingWorld.actualField(h.module(),"playerPresentation");var a=f.captureActor();var shared=f.captureShared();
            assertThrows(IllegalStateException.class,()->p.borrowAct2TimeWarpWorld(f));assertThrows(IllegalStateException.class,()->p.borrowTimeWarpWorld(f));assertEquals(a,f.captureActor());assertEquals(shared,f.captureShared());
            TestSonicCdPastLoadingWorld.step(h,TestSonicCdPastLoadingWorld.route(65));assertSame(f,h.module().requirePlayerFrame());assertSame(source,f.stageSource());assertFalse(f.closed());
        }finally{fixture.cleanup();}
    }

    /** Component-only module boundary; it never installs a service, World, Primary or saved custody. */
    private static final class H {
        final SonicCdGameModule module=mock(SonicCdGameModule.class);final SonicCdPlayerPresentation p=new SonicCdPlayerPresentation(module);
        final CdPlayerFrameRuntime f;final CdR11aForegroundScroll.State fg;final CdR11aBackgroundScroll.State bg=background();final CdR11aBackgroundPlane.State plane=plane();final SonicCdPlayerPresentation.DrawCopies copies=copies();
        Supplier<CdOpeningObjectRuntime.TimeWarpLockPublication> admit;
        H(int period,int lock){this(period,lock,1);}
        H(int period,int lock,int zone){f=frame(terrain,playerAssets,period,zone);fg=foreground(lock);when(module.openingObjectsAdmission()).thenReturn(true);p.bindAct2Source(terrain,playerAssets,f,fg,bg,plane,copies);
            admit=()->{if(period!=1)throw new IllegalStateException("constructed marked cut lacks World custody");return null;};when(module.requireAct2TimeWarpAdmission(f)).thenAnswer(call->admit.get());}
        CdOpeningObjectRuntime.TimeWarpLockPublication ready(){return new CdOpeningObjectRuntime.TimeWarpLockPublication(p.borrowAct2TimeWarpWorld(f),lock((CdR11aForegroundScroll.State)fieldUnchecked(p,"scrollState")));}
        Retained retained()throws Exception{return new Retained(field(p,"act2Source"),field(p,"scrollState"),field(p,"backgroundState"),field(p,"planeState"),field(p,"drawCopies"),field(p,"publishedPlane"),field(p,"scrollCaptureOwner"),field(p,"loadScrollPending"),field(p,"checkpointSetupPending"),field(p,"preparedScroll"),field(p,"publishedScroll"),field(p,"heldPhase"),f.captureActor(),f.captureShared());}
    }
    private record Retained(Object source,Object fg,Object bg,Object plane,Object copies,Object publishedPlane,Object owner,Object load,Object setup,Object prepared,Object published,Object held,CdPlayerFrameRuntime.ActorState actor,CdPlayerFrameRuntime.SharedState shared){}
    private static CdPlayerFrameRuntime frame(CdR12aTerrainAssets t,CdR12aPlayerAssets p,int period,int zone){
        var motion=new CdPlayerMotionState(0x19001234,0x01005678,0x123,0x234,0x345,2,0x12,6,0x84,7,14,2,2,0,0,0,0,0,0x34,0x56);
        var state=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(5,6,3),new CdPlayerBody.Header(1,0x12345678,0x2345,3,0x21),new CdPlayerBody.Timers(0x78,0x1234,0x2345,0x3456),new CdPlayerBody.History(bytes(256,13),0x42),0x67,new CdPlayerBody.ObjectLinks(0x20,0x7F),0x89);
        var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,12,0x80),new CdPlayerMovement.Bounds(0,10391,784,0),new CdPlayerMovement.Gates(zone,period,0,0,0,0,0xFF),new CdPlayerMovement.Globals(0,96,0x34,0x56,0x1234,0x4567,0x78,0x89,0x9A,0xAB)),new CdPlayerBody.Controls(8,0,8,0,0),new CdPlayerBody.Globals(3042,96,0,0,0,209,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        return CdPlayerFrameRuntime.fromAct2Entry(t,p,state,env,new CdPlayerArtRuntime.State(bytes(0x300,7).copy(),bytes(0x300,11).copy(),7,true),new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),0));
    }
    private static CdR11aForegroundScroll.State foreground(int lock){return new CdR11aForegroundScroll.State(0x1956,0x009155AA,0x10,0x10,0x1234,0x5678,0xA55A,0x345,0x12,0x789A,lock);}
    private static CdR11aForegroundScroll.State lock(CdR11aForegroundScroll.State f){return replace(f,10,(f.scrollLock()&0xFF)|0x100);}
    private static int value(CdR11aForegroundScroll.State f,int index){return new int[]{f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),f.scrollLock()}[index];}
    private static CdR11aForegroundScroll.State replace(CdR11aForegroundScroll.State f,int index,int value){int[] a=new int[11];for(int i=0;i<a.length;i++)a[i]=i==index?value:value(f,i);return new CdR11aForegroundScroll.State(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],a[9],a[10]);}
    private static CdR11aBackgroundScroll.State background(){return new CdR11aBackgroundScroll.State(0x01232345,0x00454567,0x00676789,0x008989AB,0x00ABABCD,0x00CDCDEF,bytes(8,19),0xA55A,0x1234,0x5678,bytes(512,23),bytes(512,29),bytes(1024,31),0x2468ACE0);}
    private static void assertBackgroundValueEquals(CdR11aBackgroundScroll.State expected,CdR11aBackgroundScroll.State actual){
        // State record equality retains CdAssetBytes identity; compare every semantic field independently.
        assertArrayEquals(new int[]{expected.cameraBgX(),expected.cameraBgY(),expected.cameraBg2X(),expected.cameraBg2Y(),expected.cameraBg3X(),expected.cameraBg3Y(),expected.scrollFlagsBg(),expected.scrollFlagsBg2(),expected.scrollFlagsBg3(),expected.vscroll()},
                new int[]{actual.cameraBgX(),actual.cameraBgY(),actual.cameraBg2X(),actual.cameraBg2Y(),actual.cameraBg3X(),actual.cameraBg3Y(),actual.scrollFlagsBg(),actual.scrollFlagsBg2(),actual.scrollFlagsBg3(),actual.vscroll()});
        assertArrayEquals(expected.crossingRam().copy(),actual.crossingRam().copy());
        assertArrayEquals(expected.cloudRam().copy(),actual.cloudRam().copy());
        assertArrayEquals(expected.deformationRam().copy(),actual.deformationRam().copy());
        assertArrayEquals(expected.hscrollRam().copy(),actual.hscrollRam().copy());
    }
    private static CdR11aBackgroundPlane.State plane(){return new CdR11aBackgroundPlane.State(bytes(4096,37));}
    private static SonicCdPlayerPresentation.DrawCopies copies(){var c=new CdR11aBackgroundPlane.Camera(0x02345678,0x03456789);return new SonicCdPlayerPresentation.DrawCopies(0x12345678,0x23456789,new CdR11aBackgroundPlane.CopiedCameras(c,c,c),0x3456,new CdR11aBackgroundPlane.FlagWords(0x4567,0x5678,0x6789));}
    private static CdAssetBytes bytes(int size,int multiplier){byte[] b=new byte[size];for(int i=0;i<size;i++)b[i]=(byte)(i*multiplier+3);return CdAssetBytes.copyOf(b);}
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Object fieldUnchecked(Object object,String name){try{return field(object,name);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
}
