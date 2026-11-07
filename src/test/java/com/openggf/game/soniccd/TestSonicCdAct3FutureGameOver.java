package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.scroll.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.graphics.RetainedPlaneSceneRenderer;
import com.openggf.graphics.SpritePresentation;
import static com.openggf.game.soniccd.SonicCdAct3FutureLoadingVisit.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Tests FIRST, Java UNRUN. Actual original C/D metadata; every actor/global/isolated-pool
 * ingress below is CONSTRUCTED component input, not natural death, media/art readiness,
 * Module/FULL authority or a successful restart. No algorithm or trace output is replayed.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestSonicCdAct3FutureGameOver {
    @Test void actualNativeDirectoriesOperandsAndCompleteSharedMappingJoin() throws Exception {
        for(var a:TestCdR13PlaneSources.actual()) {
            var o=CdR13ObjectAssets.fromAssets(a);var data=CdGameOver.Assets.fromAct3FutureAssets(a,o);boolean d=a.identity().file()==CdNativeFile.R13D;
            assertEquals(d?0x20DA50:0x20DA38,data.gameOverMappingsAddress());assertEquals(data.gameOverMappingsAddress()+4,data.timeOverMappingsAddress());
            var code=o.view(CdR13ObjectAssets.View.GAME_OVER_CODE);assertEquals(228,code.length());
            assertEquals(d?0x20D96C:0x20D954,o.view(CdR13ObjectAssets.View.OBJECT_INDEX).unsignedLong((0x3B-1)*4));
            assertEquals(4,code.unsignedWord(14));assertEquals(0xBC,code.unsignedWord(16));
            assertEquals(0x217C,code.unsignedWord(0x38));assertEquals(data.gameOverMappingsAddress(),code.unsignedLong(0x3A));assertEquals(4,code.unsignedWord(0x3E));
            assertEquals(0x217C,code.unsignedWord(0x5A));assertEquals(data.timeOverMappingsAddress(),code.unsignedLong(0x5C));assertEquals(4,code.unsignedWord(0x60));
            assertEquals("ab185aa60f83919e611dd47457d7fb9b5b87e99f2b9210d0dc0508c71dcea44b",hash(data.mappings()));
            assertArrayEquals(new byte[]{0,8,0,20,0,28,0,40},data.mappings().slice(0,8).copy());assertEquals(56,data.mappings().length());
            var old=CdObjectMappings.fromAct3FutureAssets(a,o);assertThrows(UnsupportedOperationException.class,()->old.resolve(data.gameOverMappingsAddress(),false,0));
            var mappings=CdObjectMappings.withAct3FutureGameOver(old,a,o);
            for(int root:new int[]{data.gameOverMappingsAddress(),data.timeOverMappingsAddress()})for(int frame=0;frame<2;frame++)assertNotNull(mappings.resolve(root,false,frame));
            assertThrows(UnsupportedOperationException.class,()->mappings.resolve(data.gameOverMappingsAddress(),false,2));
            assertThrows(UnsupportedOperationException.class,()->mappings.resolve(data.gameOverMappingsAddress()+8,false,0));
            assertThrows(UnsupportedOperationException.class,()->mappings.resolve(data.gameOverMappingsAddress()|0x80000000,false,0));
            assertThrows(UnsupportedOperationException.class,()->mappings.resolve(data.gameOverMappingsAddress(),true,0));
        }
    }
    @Test void normalLivesTwoTimeOverZeroDeletesAfterTwoStoresAndKeepsPowerupEight() throws Exception {
        for(var a:TestCdR13PlaneSources.actual()) {
            var h=new Component(a,new CdGameOver.Globals(2,0x55,0xA0));h.seed(32,0);var before=h.pool().capture();var result=h.body.run(32,h.port);
            assertTrue(result.deleted());assertArrayEquals(new byte[64],h.pool().state(32).copyBytes());assertEquals(new CdGameOver.Globals(2,8,0xA0),h.globals);
            assertEquals(List.of("cpu:SCMD_FADEPCM","powerup:8","time:160"),h.order);assertEquals(0,h.draws);assertSame(result,h.completed);
            for(int i=1;i<96;i++)assertEquals(before.slots().get(i),h.pool().capture().slots().get(i));
        }
    }
    @Test void timeOverPreservesUpperBitsCreatesNativeDirtyChildAndFallsIntoMain() throws Exception {
        var h=new Component(TestCdR13PlaneSources.actual().getFirst(),new CdGameOver.Globals(2,0x55,0xA3));h.seed(32,0);
        h.pool().write(h.pool().state(33).moveByte(SUBTYPE,0xA5).moveByte(SPRITE_FLAGS,0x34).moveWord(Y,0xBEEF));var childBefore=h.pool().state(33);
        h.body.run(32,h.port);var data=h.body.assets();assertEquals(new CdGameOver.Globals(2,0x8A,0xA2),h.globals);
        assertEquals(data.timeOverMappingsAddress(),h.pool().state(32).longBits(MAPPINGS));assertEquals(136,h.pool().state(32).centreX());assertEquals(1,h.draws);
        var expected=childBefore.moveByte(ID,0x3B).moveByte(ROUTINE,2).moveWord(TILE,0x8544).moveLong(MAPPINGS,data.timeOverMappingsAddress()).moveByte(MAP_FRAME,1).moveWord(X_FRACTION,224).moveWord(X,448).moveWord(GAME_OVER_DEST_X,288);
        assertEquals(expected,h.pool().state(33));assertEquals(List.of("cpu:SCMD_FADEPCM","powerup:8","time:162","powerup:10","powerup:138","draw:32"),h.order);
        h.body.run(33,h.port);assertEquals(440,h.pool().state(33).centreX());assertEquals(2,h.draws);
    }
    @Test void zeroLivesRetainsGameMappingAndOrdersMusicAfterChildWithoutParentDraw() throws Exception {
        var h=new Component(TestCdR13PlaneSources.actual().getFirst(),new CdGameOver.Globals(0,0x55,3));h.seed(32,0);h.body.run(32,h.port);
        assertEquals(new CdGameOver.Globals(0,0x88,2),h.globals);assertEquals(h.body.assets().gameOverMappingsAddress(),h.pool().state(32).longBits(MAPPINGS));assertEquals(128,h.pool().state(32).centreX());
        assertEquals(List.of("cpu:SCMD_FADEPCM","powerup:8","time:2","powerup:136","cpu:SCMD_GMOVERMUS"),h.order);assertEquals(0,h.draws);assertEquals(0x3B,h.pool().state(33).id());
    }
    @Test void fullPoolKeepsNativePowerupPrefixAndDeletesWithoutMusicOrDraw() throws Exception {
        var h=new Component(TestCdR13PlaneSources.actual().getFirst(),new CdGameOver.Globals(0,0,0));for(int i=32;i<128;i++)h.pool().write(h.pool().state(i).moveByte(ID,3));h.seed(32,0);var before=h.pool().capture();
        var result=h.body.run(32,h.port);assertTrue(result.deleted());assertEquals(0x88,h.globals.powerup());assertEquals(List.of("cpu:SCMD_FADEPCM","powerup:8","time:0","powerup:136"),h.order);
        assertArrayEquals(new byte[64],h.pool().state(32).copyBytes());for(int i=1;i<96;i++)assertEquals(before.slots().get(i),h.pool().capture().slots().get(i));
    }
    @Test void signedMainMovesExactlyEightWithoutClampingOrExtraGlobalsRead() throws Exception {
        var h=new Component(TestCdR13PlaneSources.actual().getFirst(),new CdGameOver.Globals(2,0,0));h.seed(32,2);h.pool().write(h.pool().state(32).moveWord(X,0x7FF8).moveWord(GAME_OVER_DEST_X,0x8000));h.body.run(32,h.port);assertEquals(0x7FF0,h.pool().state(32).centreX());assertEquals(List.of("draw:32"),h.order);assertEquals(0,h.reads);
    }
    @Test void synchronousFadeFailurePrecedesHeaderAndRetiresOnlyTheOptedInBody() throws Exception {
        var h=new Component(TestCdR13PlaneSources.actual().getFirst(),new CdGameOver.Globals(2,0,0));h.seed(32,0);var before=h.pool().capture();var original=new AssertionError("original FADEPCM failure");h.soundFault=original;
        assertSame(original,assertThrows(AssertionError.class,()->h.body.run(32,h.port)));assertEquals(before.slots(),h.pool().capture().slots());assertEquals(List.of(),h.order);assertEquals(0,h.globals.powerup());assertThrows(IllegalStateException.class,()->h.body.run(32,h.port));
    }
    @Test void headerThenGlobalFaultRetainsOriginalSourcePrefixWithoutLaterStores() throws Exception {
        var h=new Component(TestCdR13PlaneSources.actual().getFirst(),new CdGameOver.Globals(2,0,3));h.seed(32,0);var original=new AssertionError("original time-over store failure");h.timeFault=original;
        assertSame(original,assertThrows(AssertionError.class,()->h.body.run(32,h.port)));assertEquals(2,h.pool().state(32).routine());assertEquals(128,h.pool().state(32).centreX());assertEquals(8,h.globals.powerup());assertEquals(3,h.globals.timeOver());assertEquals(List.of("cpu:SCMD_FADEPCM","powerup:8"),h.order);assertEquals(0,h.pool().state(33).id());
    }
    @Test void completedChildPublicationFaultKeepsChildAndDoesNotEmitLaterMusic() throws Exception {
        var h=new Component(TestCdR13PlaneSources.actual().getFirst(),new CdGameOver.Globals(0,0,0));h.seed(32,0);var original=new AssertionError("original child completion failure");h.after=c->{if(c instanceof CdGameOver.Publication p&&p.output()!=null&&p.output().identity().wholeIndex()!=p.source().identity().wholeIndex())throw original;};
        assertSame(original,assertThrows(AssertionError.class,()->h.body.run(32,h.port)));assertEquals(0x3B,h.pool().state(33).id());assertEquals(0x88,h.globals.powerup());assertTrue(h.order.stream().noneMatch(x->x.contains("GMOVERMUS")));assertEquals(0,h.draws);assertThrows(IllegalStateException.class,()->h.body.run(32,h.port));
    }
    @Test void staleFullNinetySixWriteAndRetainedOrClonedOperationCannotComplete() throws Exception {
        var h=new Component(TestCdR13PlaneSources.actual().getFirst(),new CdGameOver.Globals(2,0,0));h.seed(32,0);Object[] retained={null};h.before=c->{retained[0]=c;if(c instanceof CdGameOver.Publication v){var clone=new CdGameOver.Publication(v.cut(),v.source(),v.before(),v.output());assertFalse(h.body.isCurrentCall(clone));assertThrows(IllegalStateException.class,()->h.body.currentOrdinal(clone));}if(c instanceof CdGameOver.Publication)h.pool().write(h.pool().state(127).moveByte(SUBTYPE,0x77));};
        assertThrows(IllegalStateException.class,()->h.body.run(32,h.port));assertEquals(0,h.pool().state(32).routine());assertEquals(0x77,h.pool().state(127).subtype());assertFalse(h.body.isCurrentCall(retained[0]));assertThrows(IllegalStateException.class,()->h.body.currentResult(retained[0]));
    }
    @Test void actualWorldDeathAllocatesThenNormalGameOverDeletesInTheSamePhysicalWalk() throws Exception {
        for(var a:TestCdR13PlaneSources.actual())try(var f=world(a)) {
            // Explicit isolated pool ingress AFTER native initial spawn. Placement cursors are retained.
            f.host.placement().pool().clearDynamicSlots();dead(f.frame);f.core.service(0);
            assertEquals(8,f.frame.state().motion().routine());assertEquals(60,f.frame.state().timers().reset());assertEquals(2,f.host.globals().ring().lives());assertEquals(8,f.core.state().level().powerup());assertEquals(0,f.core.state().level().timeOver());
            assertTrue(f.host.placement().pool().capture().slots().stream().allMatch(s->s.id()==0));assertEquals(List.of(CdGameOver.SubCpuCommand.SCMD_FADEPCM),sounds(f));assertFalse(f.core.failed());
            f.core.service(0);assertEquals(59,f.frame.state().timers().reset());assertEquals(8,f.core.state().level().powerup());assertFalse(f.core.failed());
        }
    }
    @Test void actualWorldTimeOverStoresAreSourceOrderedAndForwardChildRunsThisWalk() throws Exception {
        try(var f=world(TestCdR13PlaneSources.actual().getFirst())) {
            var p=f.host.placement().pool();p.clearDynamicSlots();time(f.core,3);p.write(p.state(32).moveByte(ID,0x3B));f.core.service(0);
            assertEquals(0x8A,f.core.state().level().powerup());assertEquals(2,f.core.state().level().timeOver());assertEquals(136,p.state(32).centreX());assertEquals(440,p.state(33).centreX());
            assertEquals(CdGameOver.Assets.fromAct3FutureAssets(f.assets,CdR13ObjectAssets.fromAssets(f.assets)).timeOverMappingsAddress(),p.state(33).longBits(MAPPINGS));assertEquals(List.of(CdGameOver.SubCpuCommand.SCMD_FADEPCM),sounds(f));assertFalse(f.core.failed());
        }
    }
    @Test void actualWorldEarlierAllocatedChildWaitsForItsOwnNextPhysicalVisit() throws Exception {
        try(var f=world(TestCdR13PlaneSources.actual().getFirst())) {
            var p=f.host.placement().pool();p.clearDynamicSlots();time(f.core,1);p.write(p.state(127).moveByte(ID,0x3B));f.core.service(0);
            assertEquals(136,p.state(127).centreX());assertEquals(0x3B,p.state(32).id());assertEquals(448,p.state(32).centreX());assertEquals(0x8A,f.core.state().level().powerup());assertFalse(f.core.failed());
        }
    }
    @Test void unsupportedOriginalRoutineStopsBeforeAnyNativeRequestReadOrWrite() throws Exception {
        for(int routine:new int[]{1,4,6,255}) {
            var h=new Component(TestCdR13PlaneSources.actual().getFirst(),new CdGameOver.Globals(2,0x55,3));h.seed(32,routine);var before=h.pool().capture();var stopped=assertThrows(CdGameOver.UnsupportedGameOver.class,()->h.body.run(32,h.port));assertEquals(CdGameOver.UnsupportedBoundary.ROUTINE_DISPATCH,stopped.boundary());assertEquals(before.slots(),h.pool().capture().slots());assertEquals(List.of(),h.order);assertEquals(0,h.reads);assertEquals(new CdGameOver.Globals(2,0x55,3),h.globals);
        }
    }
    @Test void genuineComponentCaptureReplaysNormalDeathWithoutReapplyingMetadata() throws Exception {
        try(var f=world(TestCdR13PlaneSources.actual().getFirst())) {
            f.host.placement().pool().clearDynamicSlots();dead(f.frame);var before=f.core.capture();f.core.service(0);var actor=f.frame.captureActor();var host=f.host.capture();var level=f.core.state().level();f.core.restore(before);f.core.service(0);assertEquals(actor,f.frame.captureActor());assertEquals(host,f.host.capture());assertEquals(level,f.core.state().level());assertEquals(1,sounds(f).size());
        }
    }
    @Test void foreignFactoriesAndIdleOwnerCannotMintActualGameOverInvocation() throws Exception {
        var all=TestCdR13PlaneSources.actual();var a=all.getFirst();var b=all.getLast();var placement=CdObjectPlacementRuntime.fromAct3FutureAssets(a,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));var o=CdR13ObjectAssets.fromAssets(a);
        assertThrows(NullPointerException.class,()->CdGameOver.Assets.fromAct3FutureAssets(null,o));assertThrows(IllegalArgumentException.class,()->CdGameOver.Assets.fromAct3FutureAssets(b,o));
        assertThrows(IllegalArgumentException.class,()->CdGameOver.fromAct3FutureAssets(placement,b,CdR13ObjectAssets.fromAssets(b),(p,id)->{},command->{},new NoObserver(),new NoGuard()));
        try(var f=world(a)){assertThrows(IllegalStateException.class,()->f.host.readGameOverOwners(null));assertThrows(IllegalStateException.class,()->f.world.requireObjectPass(null));assertFalse(f.core.failed());}
    }
    @Test void actualScalarCallbackFaultRetainsFadeAndHeaderButRejectsForeignFullPoolOrFrame() throws Exception {
        for(int kind=0;kind<3;kind++)try(var f=new FaultFixture(TestCdR13PlaneSources.actual().getFirst(),3)) {
            f.ready();f.core.service(0);var pool=f.host.placement().pool();pool.clearDynamicSlots();pool.write(pool.state(32).moveByte(ID,0x3B));
            var passField=SonicCdAct3FutureOrdinaryWorld.class.getDeclaredField("objects");passField.setAccessible(true);var pass=passField.get(f.world);var bodyField=SonicCdAct3FutureObjectPass.class.getDeclaredField("gameOver");bodyField.setAccessible(true);var body=(CdGameOver)bodyField.get(pass);
            var original=new AssertionError("original actual scalar callback");int mode=kind;boolean[] fired={false};f.auth.hook=()->{var c=pending(body);if(!fired[0]&&c instanceof CdGameOver.ScalarCall v&&body.isCurrentPrimitive(c)&&v.field()==CdGameOver.Scalar.POWERUP){fired[0]=true;if(mode==0)throw original;if(mode==1)pool.write(pool.state(127).moveByte(SUBTYPE,0x77));else{var e=f.frame.environment();var b=e.movement().bounds();f.frame.applyScrollFeedback(new CdPlayerMovement.Bounds(b.left(),b.right(),0x456,b.bossFight()),e.movement().globals().cameraYCentre());}}};
            if(kind==0)assertSame(original,assertThrows(AssertionError.class,()->f.core.service(0)));else assertThrows(RuntimeException.class,()->f.core.service(0));
            f.auth.hook=()->{};assertTrue(fired[0]);assertTrue(f.core.failed());assertTrue(f.frame.closed());assertEquals(2,pool.state(32).routine());assertEquals(0x3B,pool.state(32).id());assertEquals(0,f.core.state().level().powerup());
            assertEquals(1,rawSounds(f.host).stream().filter(SonicCdGameModule.PastR13GameOverSubCpu.class::isInstance).count());assertEquals(0,pool.state(33).id());assertThrows(IllegalStateException.class,()->f.core.service(0));
        }
    }
    @Test void caughtActualScalarReentryCannotReplaceTheOriginalPrivateInvocation() throws Exception {
        try(var f=new FaultFixture(TestCdR13PlaneSources.actual().getFirst(),3)) {
            f.ready();f.core.service(0);var pool=f.host.placement().pool();pool.clearDynamicSlots();pool.write(pool.state(32).moveByte(ID,0x3B));
            var passField=SonicCdAct3FutureOrdinaryWorld.class.getDeclaredField("objects");passField.setAccessible(true);var pass=passField.get(f.world);var bodyField=SonicCdAct3FutureObjectPass.class.getDeclaredField("gameOver");bodyField.setAccessible(true);var body=(CdGameOver)bodyField.get(pass);boolean[] tried={false};
            f.auth.hook=()->{var c=pending(body);if(!tried[0]&&c instanceof CdGameOver.ScalarCall&&body.isCurrentPrimitive(c)){tried[0]=true;assertThrows(IllegalStateException.class,()->f.core.service(0));assertThrows(IllegalStateException.class,f.core::capture);}};
            f.core.service(0);assertTrue(tried[0]);assertFalse(f.core.failed());assertEquals(8,f.core.state().level().powerup());assertEquals(0,pool.state(32).id());
        }
    }
    private static Object pending(CdGameOver body){try{var f=CdGameOver.class.getDeclaredField("pending");f.setAccessible(true);return f.get(body);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.PastSound> rawSounds(SonicCdAct3FutureWorldHost host)throws Exception{var f=SonicCdAct3FutureWorldHost.class.getDeclaredField("image");f.setAccessible(true);var image=f.get(host);var read=image.getClass().getDeclaredMethod("sounds");read.setAccessible(true);return (List<SonicCdGameModule.PastSound>)read.invoke(image);}
    static class NoObserver implements CdGameOver.DispatchObserver {public void beforeOperation(Object c){}public void afterOperation(Object c){}public void failedOperation(Object c,Throwable f){}}
    static class NoGuard implements CdGameOver.ReceiptGuard {public void prepared(Object c){}public void completed(Object c){}}
    private static final class Component {
        final CdObjectPlacementRuntime placement;final CdGameOver body;CdGameOver.Globals globals;final List<String> order=new ArrayList<>();int draws,reads;CdGameOver.Result completed;AssertionError soundFault,timeFault;java.util.function.Consumer<Object> before=c->{},after=c->{};
        Component(CdR13FutureAssets a,CdGameOver.Globals g){globals=g;placement=CdObjectPlacementRuntime.fromAct3FutureAssets(a,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));
            body=CdGameOver.fromAct3FutureAssets(placement,a,CdR13ObjectAssets.fromAssets(a),(p,id)->{draws++;order.add("draw:"+id.wholeIndex());var s=p.state(id.wholeIndex());p.write(s.moveByte(SPRITE_FLAGS,s.spriteFlags()&127));},command->{if(soundFault!=null)throw soundFault;order.add("cpu:"+command);},new CdGameOver.DispatchObserver(){public void beforeOperation(Object c){assertTrue(Component.this.body.isCurrentCall(c));before.accept(c);}public void afterOperation(Object c){assertTrue(Component.this.body.isCurrentCall(c));if(c instanceof CdGameOver.CompletionCall)completed=(CdGameOver.Result)Component.this.body.currentResult(c);after.accept(c);}public void failedOperation(Object c,Throwable f){}},new CdGameOver.ReceiptGuard(){public void prepared(Object c){assertTrue(Component.this.body.isCurrentCall(c));}public void completed(Object c){assertTrue(Component.this.body.isCurrentCall(c));}});
        }
        final CdGameOver.GlobalsPort port=new CdGameOver.GlobalsPort(){public CdGameOver.Globals read(){reads++;return globals;}public void writePowerup(int v){globals=new CdGameOver.Globals(globals.lives(),v,globals.timeOver());order.add("powerup:"+v);}public void writeTimeOver(int v){if(timeFault!=null)throw timeFault;globals=new CdGameOver.Globals(globals.lives(),globals.powerup(),v);order.add("time:"+v);}};
        CdObjectPool pool(){return placement.pool();}void seed(int whole,int routine){pool().write(pool().state(whole).moveByte(ID,0x3B).moveByte(ROUTINE,routine).moveLong(MAPPINGS,body.assets().gameOverMappingsAddress()).moveWord(TILE,0x8544));}
    }
    private static TestSonicCdAct3FutureWorldHost.Fixture world(CdR13FutureAssets a){var f=new TestSonicCdAct3FutureWorldHost.Fixture(a,3);f.ready();f.core.service(0);return f;}
    private static List<CdGameOver.SubCpuCommand> sounds(TestSonicCdAct3FutureWorldHost.Fixture f){return f.host.sounds().stream().filter(SonicCdGameModule.PastR13GameOverSubCpu.class::isInstance).map(SonicCdGameModule.PastR13GameOverSubCpu.class::cast).map(SonicCdGameModule.PastR13GameOverSubCpu::command).toList();}
    private static void dead(CdPlayerFrameRuntime frame){var cut=frame.captureActor();var s=cut.state();var m=s.motion();var n=new CdPlayerMotionState(m.centreX16_16(),0x05000000,0,0,0,6,0,0,m.spriteFlags(),m.xRadius(),m.yRadius(),0x18,0x18,0,0,0,0,0,m.previousPrimaryAngle(),m.previousSecondaryAngle());frame.restoreActor(new CdPlayerFrameRuntime.ActorState(cut.owner(),new CdPlayerBody.State(n,s.animation(),s.header(),new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(s.history().bytes(),0),s.rotationDistance(),s.objectLinks(),s.rotationAngle()),Optional.empty()));}
    private static void time(SonicCdAct3FutureLoadingVisit core,int time)throws Exception{var s=core.state();var l=s.level();var next=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),time);var field=SonicCdAct3FutureLoadingVisit.class.getDeclaredField("values");field.setAccessible(true);field.set(core,new SonicCdAct3FutureLoadingVisit.State(s.phase(),s.section(),s.program(),next,s.normalPalette(),s.fadePalette(),s.waterPalette(),s.displayedPalette(),s.hudSlots(),s.blocksRam(),s.layoutRam(),s.initialFadeSteps(),s.fromFadeSteps(),s.displayEnabled(),s.calls()));}
    private static String hash(CdAssetBytes b)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b.copy()));}
    /** Explicit test-only callback-fault Authority over the exact immutable fixture construction. */
    static final class FaultAuthority implements SonicCdAct3FutureWorldHost.Authority {
        FaultFixture f;Runnable hook=()->{};boolean invoking;
        public void require(SonicCdAct3FutureWorldHost h,SonicCdAct3FutureLoadingVisit c,SonicCdAct3FutureOrdinaryWorld w,CdPlayerFrameRuntime frame,CdR13FutureAssets a,CdR13SectionArt art){if(f!=null&&(f.host!=h||f.core!=null&&f.core!=c||w!=null&&f.world!=null&&f.world!=w||f.frame!=frame||f.assets!=a||f.art!=art))throw new IllegalStateException("Foreign declared fault owner");if(!invoking){invoking=true;try{hook.run();}finally{invoking=false;}}}
        public int selectedGoodFutureByte(SonicCdAct3FutureWorldHost h){return f.assets.identity().file()==CdNativeFile.R13C?1:0;}
        public SonicCdGameModule.PastSound levelMusic(SonicCdAct3FutureWorldHost h,Program p,int period,int attack){return new SonicCdGameModule.PastProgrammeSound(SonicCdGameModule.ProgramSound.STOP_FM);}
        public void serviceVInt(SonicCdAct3FutureWorldHost h){}public void resetGoodFutureFlags(SonicCdAct3FutureWorldHost h){}
    }
 static final class FaultFixture implements AutoCloseable {
  final CdR13FutureAssets assets;final CdR13SectionArt art;final CdPlayerFrameRuntime frame;final SonicCdAct3FutureWorldHost host;final SonicCdAct3FutureLoadingVisit core;final SonicCdAct3FutureOrdinaryWorld world;final FaultAuthority auth=new FaultAuthority();
  FaultFixture(CdR13FutureAssets a,int flags){this(a,flags,OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),true,0,OptionalInt.empty());}
  FaultFixture(CdR13FutureAssets a,int flags,int palFlags,OptionalInt water){this(a,flags,OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),true,palFlags,water);}
  FaultFixture(CdR13FutureAssets a,int flags,boolean bind){this(a,flags,OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),bind,0,OptionalInt.empty());}
  FaultFixture(CdR13FutureAssets a,int flags,OptionalInt projector,OptionalInt layer,OptionalInt boss,OptionalInt event){this(a,flags,projector,layer,boss,event,true,0,OptionalInt.empty());}
  FaultFixture(CdR13FutureAssets a,int flags,OptionalInt projector,OptionalInt layer,OptionalInt boss,OptionalInt event,boolean bind,int palFlags,OptionalInt water){assets=a;art=CdR13SectionArt.fromAssets(a);
   var motion=new CdPlayerMotionState(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);var actor=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(0,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(fill(256,0x71),0x2345),0,new CdPlayerBody.ObjectLinks(0,0),0);
   var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x345,0x23,0x45),new CdPlayerMovement.Bounds(3,0x1234,0x456,0),new CdPlayerMovement.Gates(2,2,1,0,0,1,0xFF),new CdPlayerMovement.Globals(3,0x78,4,5,0x6789,9,10,11,12,13)),new CdPlayerBody.Controls(0x20,0x20,0x10,0x10,0),new CdPlayerBody.Globals(0x4567,0x89,0,0x2345,0,0x3456,1,1),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
   frame=CdPlayerFrameRuntime.fromAct3FutureEntry(a,actor,env,new CdPlayerArtRuntime.State(fill(768,0x55),fill(768,0x66),0xA1,true),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0x1234),7));
   var section=art.fromValues(fill(512,0x5A),CdR13ArtResidency.unknownForNativeHud(a),0xA55A,CdR11aPlcBuffer.cleared(),Optional.empty(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.XOR,0x1234,10,0x89ABCDEF,0x5678,0x9ABC),0,0x2345,0x3456,0x4567);
   var entry=new Entry(section,new Program(0,0,a.identity().file()==CdNativeFile.R13C?1:0,0,0,palFlags,CdProgramInitialization.KnownBit.CLEAR,128,0,0),new CdLevelGlobals(0,0,0,0,flags,0,0,0,0),fill(128,0x22),fill(128,0x44),fill(128,0x66),fill(128,0x88),fill(1984,0xA5),false);
   var fg=new CdR11aForegroundScroll.State(0x1234,0x56789ABC,2,3,4,5,6,7,8,9,10);var bg=new CdR11aBackgroundScroll.State(1,2,3,4,5,6,fill(8,0x71),7,8,9,fill(512,0x72),fill(512,0x73),fill(1024,0x74),0x12345678);var camera=new CdR11aBackgroundPlane.Camera(1,2);var scroll=new Scroll(fg,bg,new Copies(1,2,new CdR11aBackgroundPlane.CopiedCameras(camera,camera,camera),3,new CdR11aBackgroundPlane.FlagWords(4,5,6)),0x3456,0x78);
   var globals=new SonicCdAct3FutureWorldHost.Globals(new CdRingGlobals(16,3,0,0,0,0,0,0),water.isPresent()?CdCheckpointLiveAux.writeWaterFullscreen(CdCheckpointLiveAux.unknown(),water.getAsInt()):CdCheckpointLiveAux.unknown(),new CdPlayerBody.ContactGlobals(0,0,0),new CdOpeningObjectRuntime.ScoreGlobals(0,5000,0),projector,CdCheckpointSaveData.State.unknown(),OptionalInt.empty(),OptionalInt.empty(),OptionalInt.empty(),Optional.empty(),Optional.empty(),Optional.empty(),fill(7,0x77),fill(7,0x88),layer,boss,event);
   var scene=new RetainedPlaneSceneRenderer.Scene(null,null,new int[256],0,new byte[128],false,SpritePresentation.Frame.empty(),Map.of(),32);
   var video=new SonicCdAct3FutureWorldHost.Video(fill(640,0xA5),Optional.empty(),frame.artState(),section.residency(),new SonicCdAct3FutureWorldHost.Carried(scene));
   host=new SonicCdAct3FutureWorldHost(auth,a,frame,art,new SonicCdAct3FutureWorldHost.Initial(globals,scroll,new CdR11aBackgroundPlane.State(fill(4096,0xA5)),new CdR11aBackgroundPlane.State(fill(4096,0xB6)),video,fill(CdObjectPlacementRuntime.SAVED_BANK_BYTES,0)));
   core=new SonicCdAct3FutureLoadingVisit(host,a,frame,art,entry);auth.f=this;host.install(core);world=bind?new SonicCdAct3FutureOrdinaryWorld(auth,core,host,frame,a,art,CdR13ObjectAssets.fromAssets(a),CdR13PlayerCollisionAssets.fromAssets(a)):null;if(bind){host.bindWorld(world);core.bindOrdinaryOwner(host,world);}
  }
  void ready(){core.begin();for(int i=0;i<256;i++){if(core.state().phase()==Phase.READY)return;core.service(0);}fail("Bounded actual loading component did not return Ready");}
  public void close(){host.close();frame.close();}
 }
 static CdAssetBytes fill(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
}
