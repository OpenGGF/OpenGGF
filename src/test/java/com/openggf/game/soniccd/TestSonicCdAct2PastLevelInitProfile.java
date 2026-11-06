package com.openggf.game.soniccd;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Constructed host-pipeline controls, ALL JAVA UNRUN. Module admission is an explicit mock
 * caller-flow oracle, never a real private staged source/World/Frame or native-entry claim.
 * Real LevelLoadContext values and existing service APIs are used; no GL/audio/media execution.
 */
class TestSonicCdAct2PastLevelInitProfile {
    @Test void profileIsPackagePrivateFinalAndRetainsOnlyItsModuleAndAdmissionCleanupBit() {
        var type=SonicCdAct2PastLevelInitProfile.class;assertTrue(Modifier.isFinal(type.getModifiers()));
        assertFalse(Modifier.isPublic(type.getModifiers()));assertTrue(LevelInitProfile.class.isAssignableFrom(type));
        var constructors=type.getDeclaredConstructors();assertEquals(1,constructors.length);assertEquals(1,constructors[0].getParameterCount());
        assertFalse(Modifier.isPublic(constructors[0].getModifiers()));
        assertEquals(List.of("admitted:boolean","module:SonicCdGameModule"),Arrays.stream(type.getDeclaredFields())
                .map(f->f.getName()+":"+f.getType().getSimpleName()).sorted().toList());
        assertEquals(List.of("cancelPendingLevelLoadWork/0","isLevelMusicPublicationPending/0","levelLoadSteps/1","levelTeardownSteps/0","perTestResetSteps/0","postTeardownFixups/0"),
                Arrays.stream(type.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());
        assertThrows(NullPointerException.class,()->new SonicCdAct2PastLevelInitProfile(null));
    }

    @Test void declaringTheImmutableNineStepPlanOnlyValidatesAndDoesNotRetireOrTouchHostServices() throws Exception {
        try(var h=new Host()) {
            var profile=new SonicCdAct2PastLevelInitProfile(h.module);var context=request();h.admitExactly(context);
            var steps=profile.levelLoadSteps(context);
            assertEquals(List.of("AdmitActualAct2PastLoad","ResetAct2PastHost","InitAct2PastModule","PrepareAct2PastGeometry","InstallAct2PastLoadingVisit","OmitDuplicateHostHud","InitAct2PastFeatures","AllocateAct2PastBackgroundRenderer","RegisterAct2PastAdapters"),steps.stream().map(InitStep::name).toList());
            assertThrows(UnsupportedOperationException.class,()->steps.clear());
            verify(h.module).validateAct2PastLoadRequest(same(context));verifyNoMoreInteractions(h.module);
            h.services.verifyNoInteractions();h.session.verifyNoInteractions();h.noHostInteractions();
            assertEquals(-1,context.getZone());assertEquals(-1,context.getAct());assertNull(context.getGameModule());assertNull(context.getLevel());
            assertEquals(1,context.getLevelIndex());assertEquals(LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY,context.getLoadCause());
        }
    }

    @Test void nullForeignAndDefaultContextsFailThePrivateValidatorBeforeAnyRetirement() throws Exception {
        try(var h=new Host()) {
            var profile=new SonicCdAct2PastLevelInitProfile(h.module);var actual=request();h.admitExactly(actual);
            assertThrows(NullPointerException.class,()->profile.levelLoadSteps(null));
            assertThrows(IllegalArgumentException.class,()->profile.levelLoadSteps(request()));
            assertThrows(IllegalArgumentException.class,()->profile.levelLoadSteps(new LevelLoadContext()));
            verify(h.module,never()).closeTerrainPlayer();h.services.verifyNoInteractions();h.noHostInteractions();
            assertNull(actual.getGameModule());assertEquals(-1,actual.getZone());assertEquals(-1,actual.getAct());
        }
    }

    @Test void constructedPrivateContextOracleRejectsWrongIndexModesCausesAndCheckpointWithoutFallback() throws Exception {
        try(var h=new Host()) {
            var profile=new SonicCdAct2PastLevelInitProfile(h.module);
            List<Consumer<LevelLoadContext>> malformed=List.of(c->c.setLevelIndex(0),c->c.setLevelIndex(2),
                    c->c.setLoadMode(LevelLoadMode.PREVIEW_CAPTURE),c->c.setAssemblyKind(LevelAssemblyKind.DECODE_ONLY),
                    c->c.setAssemblyKind(LevelAssemblyKind.STATE_RESTORATION),c->c.setIncludePostLoadAssembly(false),
                    c->c.setShowTitleCard(true),c->c.setTitleCardRequiredInHeadlessMode(true),c->c.setQueueFreshLevelRuntimeArt(true),c->c.setRom(mock(Rom.class)));
            for(var mutate:malformed){var context=request();mutate.accept(context);h.admitExactly(context);assertThrows(IllegalArgumentException.class,()->profile.levelLoadSteps(context));assertNull(context.getGameModule());assertEquals(-1,context.getZone());assertEquals(-1,context.getAct());}
            for(var cause:LevelLoadCause.values())if(cause!=LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY){var context=request();context.setLoadCause(cause);h.admitExactly(context);assertThrows(IllegalArgumentException.class,()->profile.levelLoadSteps(context));}
            var checkpoint=request();var saved=mock(RespawnState.class);when(saved.isActive()).thenReturn(true);checkpoint.snapshotCheckpoint(saved);assertTrue(checkpoint.hasCheckpoint());
            h.admitExactly(checkpoint);assertThrows(IllegalArgumentException.class,()->profile.levelLoadSteps(checkpoint));
            verify(h.module,never()).closeTerrainPlayer();h.services.verifyNoInteractions();h.noHostInteractions();
        }
    }

    @Test void firstStepRevalidatesTheSameMutableContextBeforeRetirementOrNormalization() throws Exception {
        try(var h=new Host()) {
            var profile=new SonicCdAct2PastLevelInitProfile(h.module);var context=request();h.admitExactly(context);
            var steps=profile.levelLoadSteps(context);context.setLoadCause(LevelLoadCause.FULL_RESTART);
            assertThrows(IllegalArgumentException.class,()->steps.getFirst().execute());
            verify(h.module,times(2)).validateAct2PastLoadRequest(same(context));verify(h.module,never()).closeTerrainPlayer();
            assertEquals(LevelLoadCause.FULL_RESTART,context.getLoadCause());assertNull(context.getGameModule());
            assertEquals(-1,context.getZone());assertEquals(-1,context.getAct());h.services.verifyNoInteractions();h.noHostInteractions();
            profile.cancelPendingLevelLoadWork();verify(h.module,never()).closeTerrainPlayer();
        }
    }

    @Test void revokedPrivateContextIsRecheckedAtExecutionBeforeRetiringTheOldOwner() throws Exception {
        try(var h=new Host()) {
            var context=request();h.admitExactly(context);var profile=new SonicCdAct2PastLevelInitProfile(h.module);var steps=profile.levelLoadSteps(context);
            h.admitExactly(request()); // Explicit mock staging change; no real programme transition is claimed.
            assertThrows(IllegalArgumentException.class,()->steps.getFirst().execute());
            verify(h.module,times(2)).validateAct2PastLoadRequest(same(context));verify(h.module,never()).closeTerrainPlayer();
            assertNull(context.getGameModule());assertEquals(-1,context.getZone());assertEquals(-1,context.getAct());h.services.verifyNoInteractions();
        }
    }

    @Test void admittedFullPipelineUsesAct2IndexOneAndExactHostResetLoadInstallAndAdapterOrder() throws Exception {
        try(var h=new Host()) {
            var profile=new SonicCdAct2PastLevelInitProfile(h.module);var context=request();h.admitExactly(context);
            profile.levelLoadSteps(context).forEach(InitStep::execute);
            var order=inOrder(h.module,h.level,h.cross,h.sprites,h.collision,h.water,h.fade,h.timers,h.state,h.camera,h.parallax,h.graphics,h.mode);
            order.verify(h.module,times(2)).validateAct2PastLoadRequest(same(context));order.verify(h.module).closeTerrainPlayer();
            order.verify(h.level).resetState();order.verify(h.cross).resetState();order.verify(h.sprites).resetState();order.verify(h.collision).resetState();
            order.verify(h.water).reset();order.verify(h.fade).resetState();order.verify(h.timers).resetState();order.verify(h.state).resetForLevel();
            order.verify(h.camera).resetState();order.verify(h.parallax).resetState();order.verify(h.graphics).isHeadlessMode();order.verify(h.graphics).resetState();
            order.verify(h.graphics).bindRuntimeManagedReferences(same(h.camera),same(h.fade));
            order.verify(h.level).initGameModule(1);order.verify(h.level).loadLevelData(1);order.verify(h.module).installAct2PastLoadingVisit();
            order.verify(h.module).createLevelState();order.verify(h.level).resetLevelGamestate(same(h.levelState));
            order.verify(h.level).initZoneFeatures();order.verify(h.level).initBackgroundRenderer();order.verify(h.mode).getLevelManager();
            order.verify(h.mode).registerLevelAdapters(same(h.level));order.verify(h.mode).registerGameModuleRewindAdapters();
            order.verify(h.module).registerAct2PastRestoreProjection();order.verifyNoMoreInteractions();
            assertSame(h.module,context.getGameModule());assertSame(h.geometry,context.getLevel());assertEquals(0,context.getZone());assertEquals(1,context.getAct());
            assertEquals(1,context.getLevelIndex());assertEquals(LevelLoadMode.FULL,context.getLoadMode());assertEquals(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY,context.getAssemblyKind());
            assertEquals(LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY,context.getLoadCause());assertFalse(context.hasCheckpoint());assertNull(context.getRom());
            assertFalse(context.isShowTitleCard());assertFalse(context.isQueueFreshLevelRuntimeArt());assertFalse(context.isTitleCardRequiredInHeadlessMode());
            verify(h.level,never()).initGameModule(0);verify(h.level,never()).loadLevelData(0);verify(h.level,never()).configureAudio();verify(h.level,never()).initAudio(anyInt());
            verify(h.level,never()).initPlayerAndCheckpoint();h.services.verify(GameServices::audio,never());
            verify(h.module,never()).installPastLoadingVisit();verify(h.module,never()).installAct2LoadingVisit();
        }
    }

    @Test void graphicsResetKeepsExistingHeadlessUninitializedAndLiveGlBranchesAndRebindsReferences() throws Exception {
        for(boolean[] input:new boolean[][]{{true,false},{true,true},{false,false},{false,true}})try(var h=new Host()) {
            when(h.graphics.isHeadlessMode()).thenReturn(input[0]);when(h.graphics.isGlInitialized()).thenReturn(input[1]);
            var context=request();h.admitExactly(context);new SonicCdAct2PastLevelInitProfile(h.module).levelLoadSteps(context).get(1).execute();
            if(input[0]||!input[1]){verify(h.graphics).resetState();verify(h.graphics,never()).resetPatternAndPaletteState();}
            else{verify(h.graphics).resetPatternAndPaletteState();verify(h.graphics,never()).resetState();}
            verify(h.graphics).bindRuntimeManagedReferences(same(h.camera),same(h.fade));h.services.verify(GameServices::audio,never());
        }
    }

    @Test void checkedIoFailuresKeepTheirCauseAndDoNotNormalizeAResultOrRunLaterSteps() throws Exception {
        for(int step:new int[]{2,3,6})try(var h=new Host()) {
            var context=request();h.admitExactly(context);var failure=new IOException("constructed owner failure"+step);
            if(step==2)doThrow(failure).when(h.level).initGameModule(1);
            else if(step==3)when(h.level.loadLevelData(1)).thenThrow(failure);
            else doThrow(failure).when(h.level).initZoneFeatures();
            var profile=new SonicCdAct2PastLevelInitProfile(h.module);var steps=profile.levelLoadSteps(context);
            var thrown=assertThrows(UncheckedIOException.class,()->steps.forEach(InitStep::execute));assertSame(failure,thrown.getCause());
            if(step==6)assertSame(h.geometry,context.getLevel());else assertNull(context.getLevel());
            verify(h.module,times(step==6?1:0)).installAct2PastLoadingVisit();verify(h.module,never()).registerAct2PastRestoreProjection();
            verify(h.level,never()).initBackgroundRenderer();profile.cancelPendingLevelLoadWork();verify(h.module,times(2)).closeTerrainPlayer();
        }
    }

    @Test void cancellationRetiresOnlyAfterAdmissionAndIsOnceOnlyEvenAfterInstallFailure() throws Exception {
        try(var h=new Host()) {
            var context=request();h.admitExactly(context);var profile=new SonicCdAct2PastLevelInitProfile(h.module);var steps=profile.levelLoadSteps(context);
            profile.cancelPendingLevelLoadWork();verify(h.module,never()).closeTerrainPlayer();steps.getFirst().execute();
            var failure=new IllegalStateException("constructed visit failure");doThrow(failure).when(h.module).installAct2PastLoadingVisit();
            assertSame(failure,assertThrows(IllegalStateException.class,()->steps.get(4).execute()));
            profile.cancelPendingLevelLoadWork();profile.cancelPendingLevelLoadWork();verify(h.module,times(2)).closeTerrainPlayer();
            verify(h.module,never()).registerAct2PastRestoreProjection();h.services.verifyNoInteractions();
        }
    }

    @Test void failedInitialRetirementRetainsCleanupDutyAndLeavesContextUntouched() throws Exception {
        try(var h=new Host()) {
            var context=request();h.admitExactly(context);var profile=new SonicCdAct2PastLevelInitProfile(h.module);var steps=profile.levelLoadSteps(context);
            var failure=new IllegalStateException("constructed close failure");doThrow(failure).doNothing().when(h.module).closeTerrainPlayer();
            assertSame(failure,assertThrows(IllegalStateException.class,()->steps.getFirst().execute()));
            assertNull(context.getGameModule());assertEquals(-1,context.getZone());assertEquals(-1,context.getAct());
            profile.cancelPendingLevelLoadWork();profile.cancelPendingLevelLoadWork();verify(h.module,times(2)).closeTerrainPlayer();h.services.verifyNoInteractions();
        }
    }

    @Test void adapterFailureEscapesAfterExistingRegistrationsAndLeavesRetirementToCancellation() throws Exception {
        try(var h=new Host()) {
            var context=request();h.admitExactly(context);var profile=new SonicCdAct2PastLevelInitProfile(h.module);var steps=profile.levelLoadSteps(context);steps.getFirst().execute();
            var failure=new IllegalStateException("constructed projection failure");doThrow(failure).when(h.module).registerAct2PastRestoreProjection();
            assertSame(failure,assertThrows(IllegalStateException.class,()->steps.get(8).execute()));
            var order=inOrder(h.mode,h.module);order.verify(h.module,times(2)).validateAct2PastLoadRequest(context);order.verify(h.module).closeTerrainPlayer();
            order.verify(h.mode).getLevelManager();order.verify(h.mode).registerLevelAdapters(h.level);order.verify(h.mode).registerGameModuleRewindAdapters();order.verify(h.module).registerAct2PastRestoreProjection();
            profile.cancelPendingLevelLoadWork();verify(h.module,times(2)).closeTerrainPlayer();
        }
    }

    @Test void teardownUsesExistingRetirementWhileResetAndDuplicatePreludeFallbacksStayAbsent() throws Exception {
        try(var h=new Host()) {
            var profile=new SonicCdAct2PastLevelInitProfile(h.module);assertTrue(profile.isLevelMusicPublicationPending());
            assertEquals(InitialProcessSpritesLifecycle.NONE,profile.initialProcessSpritesLifecycle());assertEquals(0,profile.freshMainPlayablePreludeFrames());
            assertEquals(0,profile.preLevelFadeOutFrames());assertEquals(0,profile.preLevelMainLoopDelayFrames());assertEquals(0,profile.skippedPresentationPlayableFrames());
            assertThrows(UnsupportedOperationException.class,profile::perTestResetSteps);assertTrue(profile.postTeardownFixups().isEmpty());
            var teardown=profile.levelTeardownSteps();assertEquals(List.of("RetireAct2PastLoad"),teardown.stream().map(InitStep::name).toList());
            assertThrows(UnsupportedOperationException.class,()->teardown.clear());teardown.getFirst().execute();verify(h.module).closeTerrainPlayer();
            h.services.verifyNoInteractions();h.session.verifyNoInteractions();h.noHostInteractions();
        }
    }

    private static LevelLoadContext request(){var c=new LevelLoadContext();c.setLevelIndex(1);c.setLoadMode(LevelLoadMode.FULL);c.setLoadCause(LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY);c.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);c.setIncludePostLoadAssembly(true);c.setShowTitleCard(false);c.setTitleCardRequiredInHeadlessMode(false);c.setQueueFreshLevelRuntimeArt(false);return c;}
    private static final class Host implements AutoCloseable {
        final SonicCdGameModule module=mock(SonicCdGameModule.class);
        final LevelManager level=mock(LevelManager.class);final CrossGameFeatureProvider cross=mock(CrossGameFeatureProvider.class);
        final SpriteManager sprites=mock(SpriteManager.class);final CollisionSystem collision=mock(CollisionSystem.class);final WaterSystem water=mock(WaterSystem.class);
        final FadeManager fade=mock(FadeManager.class);final TimerManager timers=mock(TimerManager.class);final GameStateManager state=mock(GameStateManager.class);
        final Camera camera=mock(Camera.class);final ParallaxManager parallax=mock(ParallaxManager.class);final GraphicsManager graphics=mock(GraphicsManager.class);
        final GameplayModeContext mode=mock(GameplayModeContext.class);final Level geometry=mock(Level.class);final LevelState levelState=mock(LevelState.class);
        final MockedStatic<GameServices> services=mockStatic(GameServices.class);final MockedStatic<SessionManager> session=mockStatic(SessionManager.class);
        Host()throws IOException{
            services.when(GameServices::level).thenReturn(level);services.when(GameServices::crossGameFeatures).thenReturn(cross);services.when(GameServices::sprites).thenReturn(sprites);
            services.when(GameServices::collision).thenReturn(collision);services.when(GameServices::water).thenReturn(water);services.when(GameServices::fade).thenReturn(fade);
            services.when(GameServices::timers).thenReturn(timers);services.when(GameServices::gameState).thenReturn(state);services.when(GameServices::camera).thenReturn(camera);
            services.when(GameServices::parallax).thenReturn(parallax);services.when(GameServices::graphics).thenReturn(graphics);session.when(SessionManager::getCurrentGameplayMode).thenReturn(mode);
            when(graphics.isHeadlessMode()).thenReturn(true);when(level.loadLevelData(1)).thenReturn(geometry);when(module.createLevelState()).thenReturn(levelState);when(mode.getLevelManager()).thenReturn(level);
        }
        /** Explicit caller-flow oracle only: real source/World/token validation belongs to Module. */
        void admitExactly(LevelLoadContext privateContext){doAnswer(call->{var c=call.getArgument(0,LevelLoadContext.class);if(c!=privateContext||c.getLevelIndex()!=1||c.getLoadMode()!=LevelLoadMode.FULL||c.getLoadCause()!=LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY||c.getAssemblyKind()!=LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY||!c.isIncludePostLoadAssembly()||c.hasCheckpoint()||c.getRom()!=null||c.isShowTitleCard()||c.isTitleCardRequiredInHeadlessMode()||c.isQueueFreshLevelRuntimeArt())throw new IllegalArgumentException("constructed private R12B request rejected");return null;}).when(module).validateAct2PastLoadRequest(any());}
        void noHostInteractions(){verifyNoInteractions(level,cross,sprites,collision,water,fade,timers,state,camera,parallax,graphics,mode);}
        @Override public void close(){session.close();services.close();}
    }
}
