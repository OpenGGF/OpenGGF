package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Positive actual public ingress and controller rows, written before the central joins.
 * All Java/EGL/runtime UNRUN. No private Frame, actor, clock, pool or bank injection. */
@Isolated
class TestCdOriginalR11SameReaderRuntime {
    @TempDir Path root;
    private final TestCdOriginalR11LoadingWorld fixture=new TestCdOriginalR11LoadingWorld();
    @BeforeEach void setup(){fixture.root=root;fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private static void step(com.openggf.GameLoop loop,int held){TestCdOriginalR11LoadingWorld.step(loop,held);}
    private static void ready(com.openggf.GameLoop loop,SonicCdGameModule module) {
        for(int row=0;row<256&&module.originalR11LoadingPrefix().main().levelStartedByte()!=1;row++)step(loop,0);
        assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte(),"actual loading must reach BeginLevel");
        assertTrue(module.originalR11FirstLoad().selection().inputs().displayEnabled());
    }
    private static AutoCloseable retireBeforeContextCloses(){return ()->{
        Throwable fault=null;try{SessionManager.clear();}catch(RuntimeException|Error e){fault=e;}
        try{GameServices.graphics().cleanup();}catch(RuntimeException|Error e){if(fault==null)fault=e;else if(fault!=e)fault.addSuppressed(e);}
        if(fault instanceof RuntimeException e)throw e;if(fault instanceof Error e)throw e;
    };}
    @Test void genuineLoadingReadyAndOrdinaryUseOneReaderWithoutChangingTheOriginalFrameBirth()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
        var loading=source.assets().retainedStandardLoadingSource();
        try(var boot=TestCdOriginalR11LoadingWorld.boot();var retire=retireBeforeContextCloses()) {
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);var frame=module.originalR11EntryFrame();
            assertSame(source,SessionManager.getCurrentWorldSession().getDataSource());assertSame(loading.retainedPlayerSource(),frame.stageSource());assertTrue(frame.stageSource().ownsPresent(source.assets()));
            var level=assertInstanceOf(SonicCdLevel.class,SessionManager.getCurrentGameplayMode().getLevelManager().getCurrentLevel());assertSame(loading,level.loadingSource());assertFalse(loading.hasOriginalR11Aggregate());
            assertTrue(frame.vIntClock().absoluteValue().isEmpty());assertTrue(module.originalR11WorldImage().isPresent());
            step(loop,8);assertEquals(0,frame.vIntClock().absoluteValue().orElseThrow());ready(loop,module);assertEquals(0,frame.state().motion().routine());
            var services=module.programServicesState();var clock=frame.vIntClock().absoluteValue().orElseThrow();step(loop,8);assertEquals(2,frame.state().motion().routine());
            assertEquals(8,frame.environment().controls().rawHeld());assertEquals(clock+1,frame.vIntClock().absoluteValue().orElseThrow());assertFalse(frame.closed());assertSame(services,module.programServicesState());
        }
    }
    @Test void actualClearAndDecoderRowsRemainDistinctFromTheInitialDataPrefix()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
        try(var boot=TestCdOriginalR11LoadingWorld.boot();var retire=retireBeforeContextCloses()) {
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);var loading=source.assets().retainedStandardLoadingSource();var bank=module.originalR11LoadingPrefix().blockDefinitions();var rows=module.originalR11LoadingImage().blocks().orElseThrow();
            assertEquals(6432,rows.length());for(int offset=0;offset<rows.length();offset++)assertEquals(rows.unsignedByte(offset),bank.unsignedByte(offset));
            assertEquals(0,bank.blockWord(804,0),"known tail must come from the actual original clear, never initial DATA padding");
            assertThrows(CdStageTerrain.UnavailableRead.class,()->loading.terrain().blockWord(804,0,0));
            step(loop,0);assertFalse(module.originalR11EntryFrame().closed());
        }
    }
    @Test void fullAndCourseReplayKeepTheSameFacadeFrameAndOriginalSaveStartHistory()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
        try(var boot=TestCdOriginalR11LoadingWorld.boot();var retire=retireBeforeContextCloses()) {
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);var frame=module.originalR11EntryFrame();var loading=source.assets().retainedStandardLoadingSource();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            var services=module.programServicesState();var save=Files.readAllBytes(root.resolve("scd/slot1.json"));step(loop,8);var actor=frame.captureActor();var shared=frame.captureShared();var ring=module.originalR11LoadingPrefix().ring();var score=module.originalR11LoadingPrefix().score();
            registry.restore(full);step(loop,8);assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());assertEquals(ring,module.originalR11LoadingPrefix().ring());assertEquals(score,module.originalR11LoadingPrefix().score());
            registry.restoreCourse(course);step(loop,8);assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());assertSame(frame,module.originalR11EntryFrame());assertSame(loading,source.assets().retainedStandardLoadingSource());assertTrue(loading.ownsPlayerSource(frame.stageSource()));
            assertSame(services,module.programServicesState());assertArrayEquals(save,Files.readAllBytes(root.resolve("scd/slot1.json")));
        }
    }
    @Test void invalidRowForeignThreadAndRetirementRemainNonwritesDespiteRetainedData()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var module=attempt.module().orElseThrow();
        try(var boot=TestCdOriginalR11LoadingWorld.boot();var retire=retireBeforeContextCloses()) {
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);var frame=module.originalR11EntryFrame();var actor=frame.captureActor();var shared=frame.captureShared();var prefix=module.originalR11LoadingPrefix();var services=module.programServicesState();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            assertThrows(IllegalArgumentException.class,()->module.serviceOriginalR11Loading(256));
            var failure=new AtomicReference<Throwable>();var wrongThread=new Thread(()->{try{module.serviceOriginalR11Loading(0);}catch(Throwable e){failure.set(e);}},"r11-source-negative-owner");wrongThread.start();wrongThread.join(5000);assertFalse(wrongThread.isAlive());assertInstanceOf(IllegalStateException.class,failure.get());
            assertEquals(actor,frame.captureActor());assertEquals(shared,frame.captureShared());assertSame(prefix,module.originalR11LoadingPrefix());assertSame(services,module.programServicesState());
            var loading=source.assets().retainedStandardLoadingSource();SessionManager.clear();assertTrue(frame.closed());assertSame(loading,source.assets().retainedStandardLoadingSource());
            assertThrows(IllegalStateException.class,()->module.serviceOriginalR11Loading(0));assertThrows(IllegalStateException.class,()->registry.restore(full));assertThrows(IllegalArgumentException.class,()->registry.restoreCourse(course));
        }
    }
}
