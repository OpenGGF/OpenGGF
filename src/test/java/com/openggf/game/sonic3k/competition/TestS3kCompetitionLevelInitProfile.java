package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.InitialProcessSpritesLifecycle;
import com.openggf.game.InitStep;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.RomDataSource;
import com.openggf.game.RomOffsetProvider;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.competition.S3kCompetitionLevelInitProfile.Host;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real core load actions with ROM-native arena records and independently supplied miniature bodies. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionLevelInitProfile {
    private Rom rom;

    @BeforeEach void readRom() throws Exception { rom = RomManager.getInstance().getRom(); }

    @Test
    void everyArenaExecutesCoreRomLoadingAndNativeAssemblyInTheOwningOrder() throws Exception {
        int[] counts = {8, 15, 17, 15, 15};
        int ordinal = 0;
        for (Arena arena : Arena.values()) {
            var definition = S3kCompetitionArenaDefinition.load(rom, arena);
            var players = definition.createPlayers(rom, List.of(S3kCompetitionPlayer.Character.SONIC,
                    S3kCompetitionPlayer.Character.KNUCKLES));
            var originalBodies = players.stream().map(S3kCompetitionPlayer::capture).toList();
            var host = mock(Host.class);
            var profile = new S3kCompetitionLevelInitProfile(rom, arena, () -> players, host);
            var context = fresh(arena);
            var level = mock(Level.class);
            when(level.getZoneIndex()).thenReturn(arena.zoneId());
            var manager = mock(LevelManager.class);
            when(manager.loadLevelData(context.getLevelIndex())).thenReturn(level);
            try (var services = mockStatic(GameServices.class)) {
                services.when(GameServices::level).thenReturn(manager);
                profile.levelLoadSteps(context).forEach(InitStep::execute);
            }
            var order = inOrder(host, manager);
            order.verify(host).prepareLoad(same(context), eq(definition), eq(players));
            order.verify(manager).initGameModule(context.getLevelIndex());
            order.verify(manager).initAudio(context.getLevelIndex());
            order.verify(manager).loadLevelData(context.getLevelIndex());
            order.verify(manager).initAnimatedContent();
            order.verify(manager).initObjectSystem();
            order.verify(manager).initGameplayState();
            order.verify(manager).initRings();
            order.verify(manager).initZoneFeatures();
            order.verify(host).installArtAndPalette(same(context), eq(definition), eq(players));
            order.verify(host).bindSuppliedParticipants(same(context), eq(definition), eq(players));
            order.verify(manager).initWater();
            order.verify(manager).initBackgroundRenderer();
            order.verify(host).initializeCamerasAndScroll(same(context), eq(definition), eq(players));
            order.verify(host).allocateInitialPlacements(same(context), eq(definition));
            order.verify(host).processInitialNativeSprites(same(context), eq(definition), eq(players));
            order.verify(host).prepareInitialViews(same(context), eq(definition), eq(players));
            order.verifyNoMoreInteractions();
            verify(manager, never()).initArt();
            verify(manager, never()).initPlayerAndCheckpoint();
            verify(manager, never()).spawnPlayerAtStartPosition(any());
            verify(manager, never()).spawnSidekicks(anyInt(), anyInt());
            verify(manager, never()).requestTitleCardIfNeeded(any());
            assertSame(level, context.getLevel());
            assertEquals(originalBodies, players.stream().map(S3kCompetitionPlayer::capture).toList());
            assertEquals(counts[ordinal++], definition.placements().size());
            assertTrue(definition.placements().stream().allMatch(spawn -> spawn.x() < 0x800));
            assertFalse(context.isShowTitleCard()); assertFalse(context.isQueueFreshLevelRuntimeArt());
            assertFalse(context.isTitleCardRequiredInHeadlessMode());
            assertEquals(InitialProcessSpritesLifecycle.NONE, context.requestedInitialProcessSpritesLifecycle());
            assertEquals(InitialProcessSpritesLifecycle.NONE, profile.initialProcessSpritesLifecycle());
        }
    }

    @Test
    void rosterRecreatedByRewindIsResolvedOncePerActualLoadRatherThanAtProfileConstruction() throws Exception {
        var selected = new AtomicReference<>(Arena.AZURE_LAKE);
        var first = S3kCompetitionArenaDefinition.load(rom, selected.get()).createPlayers(rom,
                List.of(S3kCompetitionPlayer.Character.SONIC, S3kCompetitionPlayer.Character.TAILS));
        var recreated = S3kCompetitionArenaDefinition.load(rom, selected.get()).createPlayers(rom,
                List.of(S3kCompetitionPlayer.Character.SONIC, S3kCompetitionPlayer.Character.TAILS));
        var supplied = new AtomicReference<>(first);
        var reads = new AtomicInteger();
        var host = mock(Host.class);
        var profile = new S3kCompetitionLevelInitProfile(rom, selected::get,
                () -> { reads.incrementAndGet(); return supplied.get(); }, host);
        var firstContext = fresh(selected.get());
        var firstSteps = profile.levelLoadSteps(firstContext);
        assertEquals(0, reads.get(), "merely planning a load must not retain outgoing player models");
        supplied.set(recreated);
        var manager = mock(LevelManager.class);
        var azure = mock(Level.class); when(azure.getZoneIndex()).thenReturn(Arena.AZURE_LAKE.zoneId());
        var balloon = mock(Level.class); when(balloon.getZoneIndex()).thenReturn(Arena.BALLOON_PARK.zoneId());
        when(manager.loadLevelData(firstContext.getLevelIndex())).thenReturn(azure);
        when(manager.loadLevelData(fresh(Arena.BALLOON_PARK).getLevelIndex())).thenReturn(balloon);
        try (var services = mockStatic(GameServices.class)) {
            services.when(GameServices::level).thenReturn(manager);
            firstSteps.getFirst().execute();
            supplied.set(first); // Subsequent callbacks must retain this load's selected body identities.
            firstSteps.stream().skip(1).forEach(InitStep::execute);
            verify(host).processInitialNativeSprites(same(firstContext), any(), argThat(list ->
                    list.get(0) == recreated.get(0) && list.get(1) == recreated.get(1)));
            assertEquals(1, reads.get());
            selected.set(Arena.BALLOON_PARK);
            var secondContext = fresh(selected.get());
            profile.levelLoadSteps(secondContext).forEach(InitStep::execute);
            verify(host).processInitialNativeSprites(same(secondContext),
                    argThat(arena -> arena.arena() == Arena.BALLOON_PARK), argThat(list ->
                            list.get(0) == first.get(0) && list.get(1) == first.get(1)));
            assertEquals(2, reads.get());
        }
    }

    @Test
    void decodeAndPreviewRetainResourcesWithoutDispatchingNativeOrCampaignPlayers() throws Exception {
        var players = S3kCompetitionArenaDefinition.load(rom, Arena.AZURE_LAKE).createPlayers(rom,
                List.of(S3kCompetitionPlayer.Character.TAILS));
        for (LevelLoadMode mode : LevelLoadMode.values()) {
            var host = mock(Host.class);
            var profile = new S3kCompetitionLevelInitProfile(rom, Arena.AZURE_LAKE, () -> players, host);
            var context = fresh(Arena.AZURE_LAKE);
            context.setLoadMode(mode);
            if (mode == LevelLoadMode.FULL) context.setAssemblyKind(LevelAssemblyKind.DECODE_ONLY);
            var level = mock(Level.class); when(level.getZoneIndex()).thenReturn(0xE);
            var manager = mock(LevelManager.class); when(manager.loadLevelData(anyInt())).thenReturn(level);
            try (var services = mockStatic(GameServices.class)) {
                services.when(GameServices::level).thenReturn(manager);
                profile.levelLoadSteps(context).forEach(InitStep::execute);
            }
            verify(host).installArtAndPalette(same(context), any(), eq(players));
            verifyNoMoreInteractions(host);
            verify(manager, never()).initArt(); verify(manager, never()).initPlayerAndCheckpoint();
            if (mode == LevelLoadMode.PREVIEW_CAPTURE) verify(manager, never()).initAudio(anyInt());
            assertEquals(InitialProcessSpritesLifecycle.NONE, context.requestedInitialProcessSpritesLifecycle());
        }
    }

    @Test
    void mismatchedDecodedArenaStopsBeforeNativeObjectsOrArtAreInstalled() throws Exception {
        var players = S3kCompetitionArenaDefinition.load(rom, Arena.AZURE_LAKE).createPlayers(rom,
                List.of(S3kCompetitionPlayer.Character.SONIC));
        var host = mock(Host.class);
        var profile = new S3kCompetitionLevelInitProfile(rom, Arena.AZURE_LAKE, () -> players, host);
        var level = mock(Level.class); when(level.getZoneIndex()).thenReturn(0);
        var manager = mock(LevelManager.class); when(manager.loadLevelData(anyInt())).thenReturn(level);
        try (var services = mockStatic(GameServices.class)) {
            services.when(GameServices::level).thenReturn(manager);
            assertThrows(IllegalStateException.class,
                    () -> profile.levelLoadSteps(fresh(Arena.AZURE_LAKE)).forEach(InitStep::execute));
        }
        verify(host).prepareLoad(any(), any(), eq(players)); verifyNoMoreInteractions(host);
        verify(manager, never()).initObjectSystem(); verify(manager, never()).initArt();
    }

    @Test
    void invalidSuppliedRosterCannotStartTheSharedLoadPipeline() {
        var host = mock(Host.class);
        var wrongSlot = new S3kCompetitionPlayer(1, S3kCompetitionPlayer.Character.SONIC, 0, 0);
        for (List<S3kCompetitionPlayer> players : List.of(List.<S3kCompetitionPlayer>of(), List.of(wrongSlot))) {
            var profile = new S3kCompetitionLevelInitProfile(rom, Arena.AZURE_LAKE, () -> players, host);
            assertThrows(IllegalArgumentException.class,
                    () -> profile.levelLoadSteps(fresh(Arena.AZURE_LAKE)).getFirst().execute());
        }
        verifyNoInteractions(host);
    }

    @Test
    void nativePublicationUsesTheVintPhaseAndLeavesGoalHudClocksInTheirSstOwners() {
        var host = mock(Host.class);
        var body = new S3kCompetitionPlayer(0, S3kCompetitionPlayer.Character.SONIC, 0, 0);
        var profile = new S3kCompetitionLevelInitProfile(rom, Arena.AZURE_LAKE, () -> List.of(body), host);
        var art = mock(S3kRuntimeArtCoordinator.class);
        var manager = mock(LevelManager.class);
        var animation = mock(S3kCompetitionPatternAnimator.class);
        when(manager.getAnimatedPatternManager()).thenReturn(animation);
        for (PlcLifecyclePhase phase : PlcLifecyclePhase.values()) {
            clearInvocations(host, art, animation);
            profile.serviceQueuedPatternDma(phase, false, art, manager);
            boolean level = phase == PlcLifecyclePhase.ORDINARY_LEVEL || phase == PlcLifecyclePhase.NORMAL_PAUSE
                    || phase == PlcLifecyclePhase.LEVEL_TITLE_CARD;
            boolean dma = level || phase == PlcLifecyclePhase.PALETTE_FADE;
            boolean page = dma || phase == PlcLifecyclePhase.LAG;
            if (page) verify(host).publishNativeSpritePages(phase, manager);
            else verifyNoInteractions(host);
            if (dma) {
                var order = inOrder(host, animation, art);
                if (page) order.verify(host).publishNativeSpritePages(phase, manager);
                order.verify(animation).publishAtVBlank();
                order.verify(art).publishPatternModuleDma();
            } else { verifyNoInteractions(animation, art); }
            assertFalse(profile.publishesSpriteTable(phase), "shared one-camera SAT must not replace native pages");
            assertFalse(profile.updatesHudCounters(phase)); assertFalse(profile.advancesHudTimer(phase));
        }
        clearInvocations(host, art, animation);
        profile.serviceQueuedPatternDma(PlcLifecyclePhase.LAG, true, art, manager);
        verify(host).publishNativeSpritePages(PlcLifecyclePhase.LAG, manager);
        verify(animation).publishAtVBlank(); verify(art).publishPatternModuleDma();
        assertFalse(profile.publishesSpriteTable(null));
    }

    @Test
    void runtimeModuleKeepsRomDecodeServicesAndReplacesCampaignPresentation() {
        var base = mock(GameModule.class); when(base.getGameId()).thenReturn(GameId.S3K);
        var offsets = mock(RomOffsetProvider.class); when(base.getRomOffsetProvider()).thenReturn(offsets);
        var body = new S3kCompetitionPlayer(0, S3kCompetitionPlayer.Character.SONIC, 0, 0);
        var profile = new S3kCompetitionLevelInitProfile(rom, Arena.AZURE_LAKE, () -> List.of(body), mock(Host.class));
        var art = mock(ObjectArtProvider.class); var scroll = mock(ScrollHandlerProvider.class);
        var titlePlc = mock(Sonic3kLevelTitlePlcService.class);
        RewindSnapshottable<Integer> nativeState = new RewindSnapshottable<>() {
            @Override public String key() { return "test-native-state"; }
            @Override public Integer capture() { return 0; }
            @Override public void restore(Integer value) { }
        };
        when(base.rewindAdapters()).thenReturn(List.of(titlePlc, nativeState));
        var module = new S3kCompetitionRuntimeModule(base, profile, art, scroll);
        assertSame(profile, module.getLevelInitProfile()); assertSame(art, module.getObjectArtProvider());
        assertSame(scroll, module.getScrollHandlerProvider()); assertSame(offsets, module.getRomOffsetProvider());
        assertInstanceOf(S3kCompetitionZoneFeatures.class,module.getZoneFeatureProvider());
        var retained=assertInstanceOf(com.openggf.game.internal.RetainedPlaneDescriptorSource.class,module.getZoneFeatureProvider());
        assertNull(retained.foregroundPlane());assertNull(retained.backgroundPlane());
        var source = new RomDataSource(rom, "rom:s3k-competition-level-init-test");
        module.createGame(source); verify(base).createGame(source);
        module.onLevelLoad(); verify(base).onLevelLoad();
        assertNull(module.getTitleCardProvider()); assertFalse(module.supportsSidekick());
        assertNull(module.getGameService(PlcLifecycleService.class));
        assertNull(module.getGameService(Sonic3kLevelTitlePlcService.class));
        assertNull(module.getGameService(Sonic3kTitleCardManager.class));
        assertEquals(List.of(nativeState), module.rewindAdapters());
        verify(base, never()).getLevelInitProfile(); verify(base, never()).getTitleCardProvider();
    }

    @Test
    void runtimeModuleRejectsForeignRomIdentityBeforeBindingConsumers() {
        var profile = mock(S3kCompetitionLevelInitProfile.class);
        var art = mock(ObjectArtProvider.class);
        var scroll = mock(ScrollHandlerProvider.class);
        for (var foreign : List.of(GameId.S1, GameId.S2, GameId.STANDALONE)) {
            var base = mock(GameModule.class);
            when(base.getGameId()).thenReturn(foreign);
            assertThrows(IllegalArgumentException.class,
                    () -> new S3kCompetitionRuntimeModule(base, profile, art, scroll), foreign.name());
            verify(base).getGameId();
            verifyNoMoreInteractions(base);
        }
        verifyNoInteractions(profile, art, scroll);
    }

    @Test
    void competitionTeardownResetsItsOwnerWithoutInvokingCampaignZoneOrIntroState() {
        var host = mock(Host.class);
        var body = new S3kCompetitionPlayer(0, S3kCompetitionPlayer.Character.SONIC, 0, 0);
        var profile = new S3kCompetitionLevelInitProfile(rom, Arena.AZURE_LAKE, () -> List.of(body), host);
        profile.perTestResetSteps().getFirst().execute();
        profile.levelTeardownSteps().stream().filter(step -> step.name().equals("ResetCompetitionLoadState"))
                .findFirst().orElseThrow().execute();
        verify(host, times(2)).resetNativeLoadState(); verifyNoMoreInteractions(host);
        assertTrue(profile.postTeardownFixups().isEmpty());
    }

    private static LevelLoadContext fresh(Arena arena) {
        var context = new LevelLoadContext();
        context.setLevelIndex(0xC0 + arena.zoneId() * 2);
        context.setIncludePostLoadAssembly(true);
        context.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        context.setShowTitleCard(true); context.setTitleCardRequiredInHeadlessMode(true);
        context.setQueueFreshLevelRuntimeArt(true);
        return context;
    }
}
