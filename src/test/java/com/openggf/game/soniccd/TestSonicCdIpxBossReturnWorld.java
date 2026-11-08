package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.soniccd.level.CdR13CapsuleAssets;
import com.openggf.game.soniccd.level.CdR13ResultsAssets;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.ready;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * Tests FIRST, ALL Java/EGL UNRUN. ONE real Module/controller bootstrap to R13D17142.
 * Each healthy case restores that actual FULL cut; sole later CONSTRUCTED ingress is Results127.
 * No gameplay/clock/camera/progress seeding, fake private invocation, save completion or natural defeat claim.
 * Existing configured Egl/fixture setup and cleanup own the actual Session/graphics/module lifetimes.
 */
@Isolated @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestSonicCdIpxBossReturnWorld {
    private TestSonicCdPastLoadingWorld fixture;
    private TestSonicCdPastResultsRoute.Egl egl;
    private Harness h;
    private SonicCdGameModule module;
    private SonicCdAct3FutureLoadingVisit core;
    private SonicCdAct3FutureWorldHost host;
    private CdPlayerFrameRuntime frame;
    private CompositeSnapshot readyFull,readyCourse;
    private Method same;
    private final LogicalInputSnapshot right=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),PlayerInputState.neutral());

    @BeforeAll void actualBootstrap() throws Exception {
        fixture=new TestSonicCdPastLoadingWorld();fixture.setup();
        try {
            egl=TestSonicCdPastResultsRoute.Egl.open();var prepared=ready();h=prepared.harness();module=h.module();
            var early=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("controller",int.class);early.setAccessible(true);
            var goal=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("naturalR12bGoalController",int.class);goal.setAccessible(true);
            same=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("assertNaturalR13SnapshotValue",Object.class,Object.class,String.class);same.setAccessible(true);
            for(int call=prepared.returned()+1;call<=17142;call++)step(h,call<=14516?(LogicalInputSnapshot)early.invoke(null,call):call<=16187?(LogicalInputSnapshot)goal.invoke(null,call):right);
            core=module.act3FutureLoadingVisit();host=module.act3FutureWorldHost();frame=module.requirePlayerFrame();
            assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,core.state().phase());
            readyFull=h.mode().getRewindRegistry().capture();readyCourse=h.mode().getRewindRegistry().captureCourse();
        } catch(Exception|Error failure){try{cleanup();}catch(Exception|Error close){if(close!=failure)failure.addSuppressed(close);}throw failure;}
    }
    @AfterAll void cleanup() throws Exception {
        try{if(egl!=null){var e=egl;egl=null;e.close();}}
        finally{if(fixture!=null){var f=fixture;fixture=null;f.cleanup();}}
    }
    @BeforeEach void restoreActualReady() throws Exception {
        h.mode().getRewindRegistry().restore(readyFull);assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,core.state().phase());
        assertEquals(0,module.ipxProgressState().savedLevel().word().orElseThrow());assertFalse(frame.closed());
    }

    @Test void earlierSourceClearOwnsHistoryAndLaterNewGameDoesNotRecreateIt() throws Exception {
        var p=module.ipxProgressState();assertEquals(0,p.savedLevel().word().orElseThrow());assertEquals(OptionalInt.of(0),p.goodFuturesByte());
        assertEquals(OptionalInt.of(0),p.currentSpecialStageByte());assertEquals(OptionalInt.of(core.state().program().timeStonesByte()),p.timeStonesByte());
        assertEquals(OptionalInt.of(0),p.titleFlagsByte());assertEquals(OptionalInt.of(0),p.timeAttackUnlockByte());assertEquals(OptionalInt.of(0),p.unknownBackupByte());assertEquals(OptionalInt.of(0),p.saveDisabledByte());
        assertTrue(module.levelProgramState().goodFutureFlags().isEmpty());
        var facts=actualField(module,"ipxProgress");assertTrue(facts.getClass().isRecord());
        assertFalse(Arrays.stream(facts.getClass().getRecordComponents()).anyMatch(c->c.getName().equals("timeStonesByte")),"Core is the live timeStones owner");
        same.invoke(null,readyFull,h.mode().getRewindRegistry().capture(),"fresh progress observation FULL nonwrite");
        same.invoke(null,readyCourse,h.mode().getRewindRegistry().captureCourse(),"fresh progress observation course nonwrite");
    }
    @Test void originalBossAndRunR1PublishSourceProgressWithoutAnotherFrame() throws Exception {
        toIpx();var state=core.state();var actor=frame.captureActor();var shared=frame.captureShared();var hostImage=actualField(host,"image");
        var actorValue=frame.state();var environment=frame.environment();var artValue=frame.artState();var clock=frame.vIntClock();
        var pool=host.placement().capture();var selection=host.selected();var source=frame.stageSource();var beforeProgram=module.levelProgramState();
        var epoch=actualField(module,"ipxProgressLifetime");var world=h.mode().getWorldSession();long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        expectUnlock();assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RUN_R3_SAVE,core.state().phase());
        assertEquals(0x100,module.ipxProgressState().savedLevel().word().orElseThrow());assertEquals(OptionalInt.of(3),module.ipxProgressState().timeAttackUnlockByte());assertEquals(OptionalInt.of(0x60),module.ipxProgressState().titleFlagsByte());assertEquals(1,Collections.frequency(core.state().calls(),SonicCdAct3FutureLoadingVisit.Call.IPX_RUN_R1_PREFIX));assertEquals(0,core.state().level().checkpoint());
        assertEquals(1,Collections.frequency(core.state().calls(),SonicCdAct3FutureLoadingVisit.Call.IPX_BOSS_RETURN));
        assertEquals(state.program(),core.state().program());assertEquals(0,core.state().program().goodFutureByte()&1);assertSame(state.section(),core.state().section());assertSame(state.normalPalette(),core.state().normalPalette());
        assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());assertSame(actorValue,frame.state());assertSame(environment,frame.environment());assertSame(artValue,frame.artState());assertSame(clock,frame.vIntClock());
        assertNotSame(hostImage,actualField(host,"image"));assertEquals(OptionalInt.of(0),host.globals().projectorByte());assertSame(selection,host.selected());
        assertEquals(pool.status(),host.placement().status());assertEquals(pool.pool().slots(),host.placement().pool().capture().slots());assertArrayEquals(pool.savedFlags().copy(),host.placement().capture().savedFlags().copy());
        assertEquals(beforeProgram,module.levelProgramState());assertSame(epoch,actualField(module,"ipxProgressLifetime"));assertSame(source,frame.stageSource());assertSame(world,h.mode().getWorldSession());
        assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());assertFalse(core.failed());assertFalse(frame.closed());
    }
    @Test void repeatedSaveOwnerStopsNeverReplayRunR1OrIncrementSavedLevelTwice() throws Exception {
        toIpx();expectUnlock();var p=module.ipxProgressState();var full=h.mode().getRewindRegistry().capture();var course=h.mode().getRewindRegistry().captureCourse();
        for(int n=0;n<4;n++){expectUnlock();assertEquals(p,module.ipxProgressState());assertEquals(1,Collections.frequency(core.state().calls(),SonicCdAct3FutureLoadingVisit.Call.IPX_BOSS_RETURN));
            same.invoke(null,full,h.mode().getRewindRegistry().capture(),"repeated actual save-owner FULL nonwrite");same.invoke(null,course,h.mode().getRewindRegistry().captureCourse(),"repeated actual save-owner course nonwrite");}
        assertFalse(core.failed());assertFalse(frame.closed());
    }
    @Test void fullRestoreRewindsProgressAndOriginalReturnedStackThenReplaysExactlyOnce() throws Exception {
        toIpx();var registry=h.mode().getRewindRegistry();var before=registry.capture();var beforeCourse=registry.captureCourse();expectUnlock();var after=registry.capture();var afterCourse=registry.captureCourse();
        registry.restore(before);assertEquals(0,module.ipxProgressState().savedLevel().word().orElseThrow());assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RETURNED,core.state().phase());
        same.invoke(null,before,registry.capture(),"pre-RTS FULL restored");same.invoke(null,beforeCourse,registry.captureCourse(),"pre-RTS course restored");expectUnlock();
        assertEquals(0x100,module.ipxProgressState().savedLevel().word().orElseThrow());same.invoke(null,after,registry.capture(),"original boss RTS FULL replay");same.invoke(null,afterCourse,registry.captureCourse(),"original boss RTS course replay");
    }
    @Test void courseRestoreRewindsProgressAndBothCompleteOwnerCutsWithoutMintingAnotherLifetime() throws Exception {
        toIpx();var registry=h.mode().getRewindRegistry();var before=registry.capture();var course=registry.captureCourse();var epoch=actualField(module,"ipxProgressLifetime");
        expectUnlock();var after=registry.capture();var afterCourse=registry.captureCourse();registry.restoreCourse(course);
        assertSame(epoch,actualField(module,"ipxProgressLifetime"));assertEquals(0,module.ipxProgressState().savedLevel().word().orElseThrow());
        same.invoke(null,before,registry.capture(),"pre-RTS FULL after course restore");expectUnlock();same.invoke(null,after,registry.capture(),"course boss RTS FULL replay");same.invoke(null,afterCourse,registry.captureCourse(),"course boss RTS course replay");
    }
    @Test void restoringReadyAfterReturnedBossDoesNotRunNewGameAndRepeatsActualResultsWalk() throws Exception {
        toIpx();expectUnlock();var registry=h.mode().getRewindRegistry();var after=registry.capture();var afterCourse=registry.captureCourse();var epoch=actualField(module,"ipxProgressLifetime");
        for(boolean course:new boolean[]{false,true}){
            if(course)registry.restoreCourse(readyCourse);else registry.restore(readyFull);
            assertSame(epoch,actualField(module,"ipxProgressLifetime"));assertEquals(0,module.ipxProgressState().savedLevel().word().orElseThrow());
            assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,core.state().phase());toIpx();expectUnlock();
            same.invoke(null,after,registry.capture(),"Ready-to-original-Results-to-boss FULL replay");same.invoke(null,afterCourse,registry.captureCourse(),"Ready-to-original-Results-to-boss course replay");
        }
    }
    @Test void idlePreparedAndMalformedServiceCallsCannotMintProgressAuthorityOrRetireHealthyFrame() throws Exception {
        var full=h.mode().getRewindRegistry().capture();var course=h.mode().getRewindRegistry().captureCourse();
        var authority=(SonicCdAct3FutureWorldHost.Authority)actualField(module,"act3Authority");
        assertThrows(IllegalStateException.class,()->host.prepareIpxBossReturn(null));assertThrows(IllegalStateException.class,()->authority.prepareIpxBossReturn(host,null));
        assertThrows(IllegalStateException.class,()->core.requireIpxBossReturnPreparation(host,null));
        assertThrows(IllegalArgumentException.class,()->module.serviceAct3FutureLoading(-1));assertThrows(IllegalArgumentException.class,()->module.serviceAct3FutureLoading(256));
        same.invoke(null,full,h.mode().getRewindRegistry().capture(),"idle/native-width FULL nonwrite");same.invoke(null,course,h.mode().getRewindRegistry().captureCourse(),"idle/native-width course nonwrite");
        assertFalse(core.failed());assertFalse(frame.closed());
    }
    @Test void issuedCallHasNoPublicConstructorAndIdleUnlockReadHasNoStackAuthority() throws Exception {
        var type=SonicCdAct3FutureLoadingVisit.IpxBossReturnCall.class;assertEquals(0,type.getConstructors().length);assertEquals(1,type.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(type.getDeclaredConstructors()[0].getModifiers()));assertFalse(type.isRecord());
        assertThrows(IllegalStateException.class,()->core.requireRunR1UnlockRead(host));
        var authority=(SonicCdAct3FutureWorldHost.Authority)actualField(module,"act3Authority");assertThrows(IllegalStateException.class,()->authority.runR1UnlockByte(host));
        assertEquals(0,module.ipxProgressState().savedLevel().word().orElseThrow());assertFalse(frame.closed());
    }

    @Test void originalRunR1CallHasNoPublicConstructorAndIdleForeignOrThreadCallsCannotAdopt() throws Exception {
        var type=SonicCdAct3FutureLoadingVisit.IpxRunR1Call.class;
        assertEquals(0,type.getConstructors().length);assertEquals(1,type.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(type.getDeclaredConstructors()[0].getModifiers()));assertFalse(type.isRecord());
        var full=h.mode().getRewindRegistry().capture();var course=h.mode().getRewindRegistry().captureCourse();
        var authority=(SonicCdAct3FutureWorldHost.Authority)actualField(module,"act3Authority");
        assertThrows(IllegalStateException.class,()->host.prepareRunR1(null));
        assertThrows(IllegalStateException.class,()->authority.prepareRunR1(host,null));
        assertThrows(IllegalStateException.class,()->authority.prepareRunR1(null,null));
        assertThrows(IllegalStateException.class,()->core.requireRunR1Preparation(host,null));
        assertThrows(IllegalStateException.class,()->core.requireRunR1Commit(host,null));
        var observed=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var thread=new Thread(()->{try{core.requireRunR1Preparation(host,null);}catch(Throwable failure){observed.set(failure);}});
        thread.start();thread.join();assertInstanceOf(IllegalStateException.class,observed.get());
        // Passive wrong-thread/absent-call control only: no private issued receiver is fabricated.
        same.invoke(null,full,h.mode().getRewindRegistry().capture(),"idle original RunR1 FULL nonwrite");
        same.invoke(null,course,h.mode().getRewindRegistry().captureCourse(),"idle original RunR1 course nonwrite");
    }
    @Test void originalRunR1PrefixRetiresItsPrivateScopeAndBothCaptureCutsRemainStable() throws Exception {
        toIpx();expectUnlock();assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RUN_R3_SAVE,core.state().phase());
        assertNull(actualField(core,"ipxRunR1Call"));assertNull(actualField(core,"ipxRunR1Prepared"));assertEquals(false,actualField(core,"ipxRunR1Committing"));
        assertThrows(IllegalStateException.class,()->core.requireRunR1Commit(host,null));
        assertThrows(IllegalStateException.class,()->host.prepareRunR1(null));
        var full=h.mode().getRewindRegistry().capture();var course=h.mode().getRewindRegistry().captureCourse();
        expectUnlock();assertEquals(1,Collections.frequency(core.state().calls(),SonicCdAct3FutureLoadingVisit.Call.IPX_RUN_R1_PREFIX));
        same.invoke(null,full,h.mode().getRewindRegistry().capture(),"closed RunR1 FULL nonwrite");
        same.invoke(null,course,h.mode().getRewindRegistry().captureCourse(),"closed RunR1 course nonwrite");
    }

    /** Sole constructed public-pool component ingress; genuine cached Results emits programme stores/exit. */
    private void toIpx() throws Exception {
        assertEquals(SonicCdAct3FutureLoadingVisit.Phase.READY,core.state().phase());
        var data=CdR13ResultsAssets.bind(core.assets(),CdR13CapsuleAssets.bind(core.assets()));var pool=host.placement().pool();
        pool.write(pool.state(127).moveByte(ID,0x3A).moveByte(ROUTINE,8).moveByte(SPRITE_FLAGS,0).moveByte(PRIORITY,0)
                .moveWord(TILE,0x83C4).moveLong(MAPPINGS,data.mappingAddress()).moveByte(MAP_FRAME,0)
                .moveWord(X,0).moveWord(RESULTS_SCREEN_Y_WORD,0xCC).moveWord(RESULTS_DEST_X,0x120));
        step(h,right);assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_BLACK_FADE,core.state().phase());
        for(int n=0;n<24;n++)module.serviceAct3FutureLoading(8);
        assertEquals(SonicCdAct3FutureLoadingVisit.Phase.OLD_IPX_RETURNED,core.state().phase());assertEquals(0,module.ipxProgressState().savedLevel().word().orElseThrow());
    }
    /** Current known-history route stops at genuine missing save ingress, not an unlock default. */
    private void expectUnlock(){var stop=assertThrows(SonicCdAct3FutureLoadingVisit.UnsupportedLoading.class,()->module.serviceAct3FutureLoading(8));
        assertEquals(SonicCdAct3FutureLoadingVisit.Boundary.IPX_RUN_R3_WRITE_SAVE_OWNER,stop.boundary());}
}
