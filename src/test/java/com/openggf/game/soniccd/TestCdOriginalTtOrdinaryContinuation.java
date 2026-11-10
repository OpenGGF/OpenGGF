package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.save.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static com.openggf.game.soniccd.CdCapturedValueAssertions.assertCapturedValueEquals;
import static org.junit.jupiter.api.Assertions.*;

/** Actual USA TT public LOAD_GAME/FULL continuation requirements; all Java UNRUN at handoff.
 * Uses only native held Right (8) after genuine Ready. Observation never satisfies strict16. */
@Isolated class TestCdOriginalTtOrdinaryContinuation extends TestCdOriginalR11LoadingWorld {
    private static final int RIGHT=8;
    private static final int REQUIRED_ORDINARY_SERVICES=16;
    private static final int OBSERVATION_CONTINUATION_LIMIT=300;

    @Test void actualTtMustReturnSixteenLivingRightOrdinaryServicesAndReplayCurrentValues() throws Exception {
        var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var module=attempt.module().orElseThrow();
        try(var boot=boot()) {
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
            var mode=SessionManager.getCurrentGameplayMode();var manager=mode.getLevelManager();
            var level=assertInstanceOf(SonicCdLevel.class,manager.getCurrentLevel());
            var loading=source.programmeData(CdStagePayload.R41A.stage()).orElseThrow().levelLoadingSource();
            var frame=module.originalR11EntryFrame();var playerSource=frame.stageSource();
            assertSame(loading,level.loadingSource());assertSame(loading.retainedPlayerSource(),playerSource);
            var loads=module.programServicesState().loads();var generation=manager.getCompletedProductionLoadGeneration();
            var registry=mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            var fullKeys=Set.copyOf(full.entries().keySet());var courseKeys=Set.copyOf(course.entries().keySet());
            var expected=new ArrayList<Object>();
            for(int service=1;service<=REQUIRED_ORDINARY_SERVICES;service++) {
                step(loop,RIGHT);livingOrdinary(module,frame,service);
                sameOwners(module,frame,source,playerSource,level,loads,generation);sameRegistryKeys(registry,fullKeys,courseKeys);
                expected.add(image(module));
            }
            registry.restore(full);
            for(int service=1;service<=REQUIRED_ORDINARY_SERVICES;service++) {
                step(loop,RIGHT);livingOrdinary(module,frame,service);
                sameOwners(module,frame,source,playerSource,level,loads,generation);sameRegistryKeys(registry,fullKeys,courseKeys);
                assertCapturedValueEquals(expected.get(service-1),image(module),"FULL.ordinary["+service+"]");
            }
            registry.restoreCourse(course);
            for(int service=1;service<=REQUIRED_ORDINARY_SERVICES;service++) {
                step(loop,RIGHT);livingOrdinary(module,frame,service);
                sameOwners(module,frame,source,playerSource,level,loads,generation);sameRegistryKeys(registry,fullKeys,courseKeys);
                assertCapturedValueEquals(expected.get(service-1),image(module),"course.ordinary["+service+"]");
            }
        }
    }

    @Test void observeAtMostThreeHundredFurtherRightServicesAfterThreeGenuineOrdinaryReturns() throws Exception {
        var source=TestCdOriginalBootPrefix.source();var attempt=tt(source);var module=attempt.module().orElseThrow();
        try(var boot=boot()) {
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);ready(loop,module);
            var mode=SessionManager.getCurrentGameplayMode();var manager=mode.getLevelManager();
            var frame=module.originalR11EntryFrame();var playerSource=frame.stageSource();
            var level=manager.getCurrentLevel();var loads=module.programServicesState().loads();
            var generation=manager.getCompletedProductionLoadGeneration();
            int completed=0,attempted=0;
            for(int service=1;service<=3;service++) {
                attempted++;step(loop,RIGHT);completed++;
                livingOrdinary(module,frame,service);sameOwners(module,frame,source,playerSource,level,loads,generation);
            }
            for(int continuation=0;continuation<OBSERVATION_CONTINUATION_LIMIT;continuation++) {
                attempted++;
                try {step(loop,RIGHT);}
                catch(UnsupportedOperationException boundary) {
                    observation(frame,completed,attempted,"UNSUPPORTED "+boundary.getClass().getName()+": "+boundary.getMessage());
                    return;
                }
                completed++;sameOwners(module,frame,source,playerSource,level,loads,generation);
                int routine=frame.state().motion().routine();
                if(routine==6||routine==8) {
                    observation(frame,completed,attempted,"RETURNED_NATIVE_DEATH_STATE routine="+routine);
                    return;
                }
                assertFalse(frame.closed(),"A returned observation service must retain its Frame");
            }
            observation(frame,completed,attempted,"CONTINUATION_LIMIT_RETURNED_WITHOUT_OBSERVED_DEATH_OR_UNSUPPORTED");
        }
    }

    /** Existing TT selection helpers are private; retain only this narrow public save ingress. */
    private SonicCdGameModule.SelectedProfileAttempt tt(CdGameDataSource source) throws Exception {
        var saves=new SaveManager(root);
        var profile=CdSaveProfile.fromTemplate(source.initialSaveAssets().orElseThrow()).withPatch(new CdIpxProgress.SavePatch(
                OptionalInt.of(0x200),OptionalInt.of(4),OptionalInt.of(0x90),OptionalInt.of(0),OptionalInt.of(0x35),OptionalInt.of(2),OptionalInt.of(3)));
        saves.writeSlot("scd",1,profile.encode());
        return SonicCdGameModule.prepareSelectedProfile(source,saves,1,SonicCdGameModule.ProfileEntry.LOAD_GAME,0);
    }

    private static void ready(GameLoop loop,SonicCdGameModule module) {
        for(int service=0;service<256&&module.originalR11LoadingPrefix().main().levelStartedByte()!=1;service++)step(loop,0);
        assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());
        assertEquals(SonicCdR31aLoadingVisit.Phase.READY,module.originalR11LoadingImage().phase());
        var frame=module.originalR11EntryFrame();assertFalse(frame.closed());
        assertEquals(CdStagePayload.R41A.stage(),frame.stageSource().stage());
        assertEquals(1,frame.environment().movement().gates().timeZone(),"Actual native TIME_PRESENT");
        assertEquals(0,frame.environment().globals().levelFrames(),"Ready precedes the first ordinary service");
    }

    private static void livingOrdinary(SonicCdGameModule module,CdPlayerFrameRuntime frame,int completed) {
        assertFalse(frame.closed(),"strict ordinary service "+completed+" must leave its Frame alive");
        int routine=frame.state().motion().routine();
        assertTrue(routine==2||routine==4,"ordinary service "+completed+" must retain a living Main/Hurt routine, actual="+routine);
        assertEquals(completed,frame.environment().globals().levelFrames());
        assertEquals(RIGHT,frame.environment().controls().rawHeld(),"Only actual native held Right is supplied");
        assertEquals(SonicCdR31aLoadingVisit.Phase.READY,module.originalR11LoadingImage().phase());
    }

    private static void sameOwners(SonicCdGameModule module,CdPlayerFrameRuntime frame,CdGameDataSource source,
            CdStagePlayerSource playerSource,Object level,Object loads,long generation) {
        var mode=SessionManager.getCurrentGameplayMode();var manager=mode.getLevelManager();
        assertSame(source,SessionManager.getCurrentWorldSession().getDataSource());
        assertSame(frame,module.originalR11EntryFrame());assertSame(playerSource,frame.stageSource());
        assertSame(level,manager.getCurrentLevel());assertSame(loads,module.programServicesState().loads());
        assertEquals(generation,manager.getCompletedProductionLoadGeneration());
    }

    private static void sameRegistryKeys(RewindRegistry registry,Set<String> fullKeys,Set<String> courseKeys) {
        assertSame(registry,SessionManager.getCurrentGameplayMode().getRewindRegistry());
        assertEquals(fullKeys,registry.capture().entries().keySet());
        assertEquals(courseKeys,registry.captureCourse().entries().keySet());
    }

    /** Complete existing exposed TT image fields; private registry authority is not serialized. */
    private static Object image(SonicCdGameModule module) {
        var frame=module.originalR11EntryFrame();var placement=module.originalR11PlacementImage();
        return List.of(frame.captureActor(),frame.captureShared(),placement.status(),placement.pool().slots(),placement.savedFlags(),
                module.originalR11LoadingPrefix(),module.originalR11LoadingImage(),module.originalR11FirstLoad(),
                module.originalLoadingWaterImage(),module.originalLoadingWaterPalette(),module.originalR11WorldImage(),
                module.programServicesState(),module.selectedProfileState());
    }

    private static void observation(CdPlayerFrameRuntime frame,int completed,int attempted,String boundary) {
        var motion=frame.state().motion();
        System.out.println("TT_CONTINUATION_OBSERVATION firstRequiredReturns=3 continuationLimit="+OBSERVATION_CONTINUATION_LIMIT
                +" completedServicesFromReady="+completed+" attemptedServicesFromReady="+attempted
                +" completedContinuation="+(completed-3)+" attemptedContinuation="+(attempted-3)+" nativeHeld="+RIGHT
                +" sourceStage="+frame.stageSource().stage()+" period="+frame.environment().movement().gates().timeZone()
                +" centreX16_16="+motion.centreX16_16()+" centreY16_16="+motion.centreY16_16()
                +" centreXWord="+(short)(motion.centreX16_16()>>>16)+" centreYWord="+(short)(motion.centreY16_16()>>>16)
                +" routine="+motion.routine()+" frameClosed="+frame.closed()+" boundary="+boundary.replace('\n',' '));
    }
}
