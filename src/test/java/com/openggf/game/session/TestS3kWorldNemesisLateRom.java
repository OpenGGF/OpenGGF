package com.openggf.game.session;

import com.openggf.game.GameDataSource;
import com.openggf.game.StockGameDataSources;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.specialstage.S3kSpecialStageWorldAccess;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit-world resource-availability oracle for the actual S3K results provider.
 * Origin: 2026-10-18 world-owned Nemesis migration. Only the fixture data source's
 * availability changes. No native stage route, scheduling, rendering or publication
 * is claimed; the ambient other world deliberately keeps its real original ROM.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kWorldNemesisLateRom {
    private GameplayModeContext target;

    @BeforeEach void setUp() { TestEnvironment.resetAll(); }

    @AfterEach void tearDown() {
        try {
            SessionManager.clear();
        } finally {
            try {
                if (target != null) target.destroy();
            } finally {
                TestEnvironment.resetAll();
            }
        }
    }

    @Test
    void boundLoadRecipeUsesItsOwnSavedTeamWhileAnotherWorldIsCurrent() throws Exception {
        var rom = TestEnvironment.currentRom();
        var module = new Sonic3kGameModule();
        assertNotNull(module.createGame(rom));
        var source = StockGameDataSources.pinned(rom, module);
        var ambient = SessionManager.openGameplaySession(module, module, source,
                com.openggf.game.save.SaveSessionContext.noSave("s3k",
                        new com.openggf.game.save.SelectedTeam("tails", List.of()), 1, 0),
                HardwareReadinessAdmissionPolicy.LIVE);
        GameplaySessionFactory.attachManagers(ambient, EngineServices.current());
        var ambientPlc = WorldPlcLifecycleAccess.require(ambient.getWorldSession(), Sonic3kLevelTitlePlcService.class);
        ambientPlc.appendRaw(0x083D74); // Declared unrelated component ingress; its full FIFO must stay untouched.
        var ambientBefore = ambientPlc.capture();
        var config = com.openggf.game.GameServices.configuration();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        try {
            var world = new WorldSession(module, module, source,
                    com.openggf.game.save.SaveSessionContext.noSave("s3k",
                            new com.openggf.game.save.SelectedTeam("knuckles", List.of()), 1, 0));
            // Declared public load-component context, not a complete level-load or route claim.
            world.setCurrentZone(1);
            world.setCurrentAct(0);
            var load = new com.openggf.game.LevelLoadContext();
            load.setRom(rom);
            load.setIncludePostLoadAssembly(true);
            load.setAssemblyKind(com.openggf.game.LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
            com.openggf.game.LevelLoadRuntimeAccess.bindWorld(load, world);
            var steps = module.getLevelInitProfile().levelLoadSteps(load).stream()
                    .filter(step -> step.name().equals("SubmitFreshLevelTitlePlcs")).toList();
            assertEquals(1, steps.size());
            assertSame(ambient.getWorldSession(), SessionManager.getCurrentWorldSession());
            assertEquals("tails", ActiveGameplayTeamResolver.resolveMainCharacterCode(config),
                    "the current world deliberately disagrees with both target and configured default");
            var expected = new Sonic3kLevelTitlePlcService(rom);
            expected.beginFreshZoneTitle(1, 0, "knuckles");
            var currentRecipe = new Sonic3kLevelTitlePlcService(rom);
            currentRecipe.beginFreshZoneTitle(1, 0, "tails");
            var defaultRecipe = new Sonic3kLevelTitlePlcService(rom);
            defaultRecipe.beginFreshZoneTitle(1, 0, "sonic");
            assertNotEquals(expected.capture(), currentRecipe.capture());
            assertNotEquals(expected.capture(), defaultRecipe.capture());
            steps.getFirst().execute();
            assertEquals(expected.capture(), WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class).capture());
            assertEquals(ambientBefore, ambientPlc.capture());
            assertSame(ambient.getWorldSession(), SessionManager.getCurrentWorldSession());
        } finally {
            config.clearSessionOverrides();
        }
    }

    @Test
    void nativeWorldQueueWithNullResolvedRulesKeepsTheNeutralDmaPolicy() throws Exception {
        var root = new Sonic3kGameModule();
        var wrapper = new com.openggf.game.patch.DelegatingGameModule(root, "fixture:null-rules") {
            @Override public com.openggf.game.rules.GameRules getRules() { return null; }
        };
        var mock = org.mockito.Mockito.mock(com.openggf.game.GameModule.class);
        for (var resolved : List.of(wrapper, mock)) {
            var world = new WorldSession(root, resolved,
                    StockGameDataSources.pinned(TestEnvironment.currentRom(), root), null);
            target = new GameplayModeContext(world);
            try {
                assertNull(resolved.getRules());
                assertNotNull(WorldPlcLifecycleAccess.owned(world));
                var dynamicArt = target.dynamicArtLifecycle();
                dynamicArt.beginRun();
                // Declared typed DPLC ingress discriminates EVERY_CLAIM from per-game DMA filtering.
                dynamicArt.observePlayerDplc(com.openggf.game.GameId.S2, "sonic", 1,
                        new com.openggf.level.render.SpriteDplcFrame(List.of(
                                new com.openggf.level.render.TileLoadRequest(0, 1))));
                dynamicArt.openComparisonSegment();
                target.plcFrameLifecycle().runLogicalIteration(() -> { }, frame -> {
                    frame.claim(PlcLifecyclePhase.TITLE_SCREEN);
                    return null;
                });
                assertTrue(dynamicArt.latestSnapshot().outstandingTransferIds().isEmpty());
                assertEquals("completed", dynamicArt.latestSnapshot().edges().getFirst().phase());
            } finally {
                target.destroy();
                target = null;
            }
        }
    }

    @Test
    void resultsProviderRequiresItsWorldRomAndEmptyLifecycleDoesNotResolveIt() throws Exception {
        var rom = TestEnvironment.currentRom();
        var module = new Sonic3kGameModule();
        assertNotNull(module.createGame(rom));
        var engine = EngineServices.current();
        var ambient = SessionManager.openGameplaySession(module, module,
                StockGameDataSources.pinned(rom, module), null, HardwareReadinessAdmissionPolicy.LIVE);
        GameplaySessionFactory.attachManagers(ambient, engine);
        var ambientServices = new DefaultObjectServices(ambient, engine);
        var ambientPlc = ambientServices.gameService(Sonic3kLevelTitlePlcService.class);
        assertNotNull(ambientPlc);
        ambientPlc.appendRaw(0x083D74); // Declared synthetic descriptor ingress, not a CNZ route.
        var ambientBefore = ambientPlc.capture();

        var available = new AtomicReference<com.openggf.data.Rom>();
        var lookups = new AtomicInteger();
        GameDataSource source = new GameDataSource() {
            @Override public Optional<com.openggf.data.Rom> rom() {
                lookups.incrementAndGet();
                return Optional.ofNullable(available.get());
            }
            @Override public java.io.InputStream openAsset(String path) throws IOException {
                throw new IOException("No named fixture assets");
            }
            @Override public String identity() { return "fixture:late-original-s3k-rom"; }
        };
        var world = new WorldSession(module, module, source, null);
        target = new GameplayModeContext(world);
        GameplaySessionFactory.attachManagers(target, engine);
        var services = new DefaultObjectServices(target, engine);
        var plc = services.gameService(Sonic3kLevelTitlePlcService.class);
        assertNotNull(plc);
        assertNotSame(ambientPlc, plc);
        var empty = new NemesisPlcQueueSnapshot(null, List.of());
        assertEquals(empty, plc.capture());
        plc.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        plc.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        assertEquals(empty, plc.capture());
        assertEquals(empty, target.getRewindRegistry().capture().get(plc.key()));
        assertEquals(0, lookups.get(), "world construction, empty lifecycle and capture do not resolve ROM");

        var provider = module.getSpecialStageProvider();
        var failure = assertThrows(IllegalStateException.class,
                () -> S3kSpecialStageWorldAccess.createResults(provider, target, 0, false, 0, 0));
        assertInstanceOf(IOException.class, failure.getCause());
        assertEquals(1, lookups.get(), "the actual provider consults its explicit world's unavailable source");
        assertEquals(empty, plc.capture());
        assertEquals(ambientBefore, ambientPlc.capture(), "available ambient ROM/queue cannot satisfy another world");

        available.set(rom); // Declared component data availability, never engine gameplay hydration.
        assertNotNull(S3kSpecialStageWorldAccess.createResults(provider, target, 0, false, 0, 0));
        int art = Sonic3kConstants.ART_NEM_RING_HUD_TEXT_ADDR;
        int patterns = rom.read16BitAddr(art) & 0x7FFF;
        var entry = new NemesisPlcQueueSnapshot.Entry(art,
                Sonic3kConstants.VRAM_SS_RESULTS_HUD_TEXT, patterns, patterns);
        var queued = new NemesisPlcQueueSnapshot(null, List.of(entry));
        assertEquals(queued, plc.capture(), "only the explicit world's original results recipe is submitted");
        assertEquals(queued, target.getRewindRegistry().capture().get(plc.key()));
        assertEquals(ambientBefore, ambientPlc.capture());
    }
}
