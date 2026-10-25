package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.ResultsScreen;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.StockGameDataSources;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.WorldPlcLifecycleAccess;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.specialstage.S3kSpecialStageWorldAccess;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.game.sonic3k.titlecard.S3kTitleCardWorldAccess;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.runtime.OwnerBoundGamePatch;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual owned-provider forwarding and refusal of contextual lookalikes.
 * Origin: 2026-10-18 world-owned Nemesis migration. Public component entry only;
 * no native stage route, service timing or pattern publication is asserted.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kNativeWorldCallbackDispatch {
    private enum Binding { EXPLICIT_PATCH, STANDALONE, SERVICE_THEN_PATCH }
    private record Fixture(ModFaultBoundary boundary, ModRuntimeFindingStore findings) { }

    @BeforeEach void setUp() { TestEnvironment.resetAll(); }
    @AfterEach void tearDown() { TestEnvironment.resetAll(); }

    private static Fixture fixture() {
        var findings = new ModRuntimeFindingStore();
        return new Fixture(new ModFaultBoundary(Map.of(), findings,
                ignored -> new ModStateSaveResult.Saved(), ignored -> { }), findings);
    }

    @Test
    void nativeResultsDispatchThroughTheOriginalOwnedProxyAndKeepItsFailureBoundary() throws Exception {
        var rom = TestEnvironment.currentRom();
        var root = new Sonic3kGameModule();
        assertNotNull(root.createGame(rom));
        for (Binding binding : Binding.values()) {
            var context = new GameplayModeContext(new WorldSession(root, root,
                    StockGameDataSources.pinned(rom, root), null));
            GameplaySessionFactory.attachManagers(context, EngineServices.current());
            try {
                var plc = WorldPlcLifecycleAccess.require(context.getWorldSession(), Sonic3kLevelTitlePlcService.class);
                var fixture = fixture();
                var nativeProvider = new Sonic3kSpecialStageProvider();
                var provider = bind(binding, SpecialStageProvider.class, nativeProvider, root, fixture);
                assertNotSame(nativeProvider, provider);
                assertTrue(Proxy.isProxyClass(provider.getClass()));
                assertTrue(OwnerBoundGamePatch.hasNativeSpecialResultsWorldCreation(provider));
                assertNotNull(S3kSpecialStageWorldAccess.createResults(provider, context, 0, false, 0, 0));
                assertEquals(1, plc.capture().queuedEntries().size());
                var before = plc.capture();
                assertEquals(before, context.getRewindRegistry().capture().get(plc.key()));

                var lookups = new AtomicInteger();
                var absent = new com.openggf.game.GameDataSource() {
                    @Override public java.util.Optional<com.openggf.data.Rom> rom() {
                        lookups.incrementAndGet();
                        return java.util.Optional.empty();
                    }
                    @Override public java.io.InputStream openAsset(String path) throws IOException {
                        throw new IOException("No fixture assets");
                    }
                    @Override public String identity() { return "fixture:missing-results-rom"; }
                };
                var missing = new GameplayModeContext(new WorldSession(root, root, absent, null));
                GameplaySessionFactory.attachManagers(missing, EngineServices.current());
                try {
                    var otherPlc = WorldPlcLifecycleAccess.require(missing.getWorldSession(), Sonic3kLevelTitlePlcService.class);
                    var empty = otherPlc.capture();
                    var failure = assertThrows(ModFaultBoundary.CallbackAborted.class,
                            () -> S3kSpecialStageWorldAccess.createResults(provider, missing, 0, false, 0, 0));
                    assertEquals("registered-owner", failure.owner());
                    assertInstanceOf(IllegalStateException.class, failure.getCause());
                    assertInstanceOf(IOException.class, failure.getCause().getCause());
                    assertEquals(1, lookups.get(), "one actual native invocation through all selected wrappers");
                    assertEquals(1, fixture.findings().findingsFor("registered-owner").size());
                    assertEquals(empty, otherPlc.capture());
                    assertEquals(before, plc.capture(), "missing explicit data cannot select the available other world");
                } finally {
                    missing.destroy();
                }
            } finally {
                context.destroy();
            }
        }
    }

    @Test
    void nativeTitleDispatchUsesExplicitContextWithoutBypassingItsOwnedBoundary() {
        var root = new Sonic3kGameModule();
        var world = new WorldSession(root);
        var wrongWorld = new WorldSession(new Sonic2GameModule());
        for (Binding binding : Binding.values()) {
            var fixture = fixture();
            var nativeTitle = new Sonic3kTitleCardManager();
            var provider = bind(binding, TitleCardProvider.class, nativeTitle, root, fixture);
            assertTrue(Proxy.isProxyClass(provider.getClass()));
            assertTrue(OwnerBoundGamePatch.hasNativeTitleWorldUpdate(provider));
            assertDoesNotThrow(() -> S3kTitleCardWorldAccess.update(provider, world));
            var failure = assertThrows(ModFaultBoundary.CallbackAborted.class,
                    () -> S3kTitleCardWorldAccess.update(provider, wrongWorld));
            assertEquals("registered-owner", failure.owner());
            assertInstanceOf(NullPointerException.class, failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("explicit world PLC owner"));
            assertEquals(1, fixture.findings().findingsFor("registered-owner").size());
        }
    }

    @Test
    void sharedTitleOperationComesFromTheNativeRootAndKeepsTheSelectedFaultBoundary() {
        var root = new Sonic3kGameModule();
        var contributedOperationReads = new AtomicInteger();
        var resolved = new DelegatingGameModule(root, "claimed-other-owner:title-operation") {
            @Override public <T> T getGameService(Class<T> type) {
                if (type == com.openggf.game.internal.WorldTitleCardUpdate.class) {
                    contributedOperationReads.incrementAndGet();
                    throw new AssertionError("a contributed service must not receive world context");
                }
                return super.getGameService(type);
            }
        };
        var world = new WorldSession(root, resolved, null);
        var missingOwnerRoot = new Sonic3kGameModule() {
            @Override public <T> T getGameService(Class<T> type) {
                return type == com.openggf.game.session.WorldPlcLifecycleFactory.class
                        ? null : super.getGameService(type);
            }
        };
        // Declared missing-capability component world, not a gameplay boot or native scene.
        var missingOwnerWorld = new WorldSession(missingOwnerRoot);
        assertNull(WorldPlcLifecycleAccess.owned(missingOwnerWorld));
        for (Binding binding : Binding.values()) {
            var fixture = fixture();
            var provider = bind(binding, TitleCardProvider.class, new Sonic3kTitleCardManager(), root, fixture);
            assertDoesNotThrow(() -> com.openggf.game.internal.WorldTitleCardUpdate.dispatch(provider, world));
            assertEquals(0, contributedOperationReads.get());
            assertTrue(fixture.findings().snapshot().isEmpty());
            var failure = assertThrows(ModFaultBoundary.CallbackAborted.class,
                    () -> com.openggf.game.internal.WorldTitleCardUpdate.dispatch(provider, missingOwnerWorld));
            assertEquals("registered-owner", failure.owner());
            assertInstanceOf(NullPointerException.class, failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("explicit world PLC owner"));
            assertEquals(1, fixture.findings().findingsFor("registered-owner").size());
        }
    }

    @Test
    void customSubclassAndUnknownProxyNeverReceiveTheNativeWorldCapability() {
        var root = new Sonic3kGameModule();
        var title = new LookalikeTitle();
        var results = new LookalikeResults();
        assertFalse(OwnerBoundGamePatch.hasNativeTitleWorldUpdate(title));
        assertFalse(OwnerBoundGamePatch.hasNativeSpecialResultsWorldCreation(results));
        S3kTitleCardWorldAccess.update(title, null);
        assertNull(S3kSpecialStageWorldAccess.createResults(results, null, 1, true, 2, 3));
        for (Binding binding : Binding.values()) {
            var fixture = fixture();
            var ownedTitle = bind(binding, TitleCardProvider.class, title, root, fixture);
            var ownedResults = bind(binding, SpecialStageProvider.class, results, root, fixture);
            assertFalse(OwnerBoundGamePatch.hasNativeTitleWorldUpdate(ownedTitle));
            assertFalse(OwnerBoundGamePatch.hasNativeSpecialResultsWorldCreation(ownedResults));
            S3kTitleCardWorldAccess.update(ownedTitle, null);
            assertNull(S3kSpecialStageWorldAccess.createResults(ownedResults, null, 1, true, 2, 3));
            assertTrue(fixture.findings().snapshot().isEmpty());
        }
        var publicCalls = new AtomicInteger();
        var unknown = (TitleCardProvider)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {TitleCardProvider.class, S3kTitleCardWorldAccess.ContextualUpdate.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("updateInWorld")) throw new AssertionError("context leaked to unknown proxy");
                    if (method.getName().equals("update")) publicCalls.incrementAndGet();
                    return null;
                });
        assertFalse(OwnerBoundGamePatch.hasNativeTitleWorldUpdate(unknown));
        S3kTitleCardWorldAccess.update(unknown, null);
        assertEquals(1, publicCalls.get());
        assertEquals(1 + Binding.values().length, title.publicCalls);
        assertEquals(1 + Binding.values().length, results.publicCalls);
        int beforeOverlay = title.publicCalls;
        com.openggf.game.internal.BonusStageFrameLifecycle.updateOverlay(title, new WorldSession(root));
        assertEquals(beforeOverlay + 1, title.publicCalls,
                "the actual shared overlay inlet keeps a custom provider on its public default operation");
    }

    public static final class LookalikeTitle extends Sonic3kTitleCardManager {
        int publicCalls;
        @Override public boolean isOverlayActive() { return true; }
        @Override public void update() { publicCalls++; }
        @Override public void updateInWorld(WorldSession world) { throw new AssertionError("custom title received world"); }
    }
    public static final class LookalikeResults extends Sonic3kSpecialStageProvider {
        int publicCalls;
        @Override public ResultsScreen createResultsScreen(int rings, boolean emerald, int stage, int total) {
            publicCalls++;
            return null;
        }
        @Override public ResultsScreen createResultsScreenInWorld(int rings, boolean emerald, int stage, int total,
                GameplayModeContext context) { throw new AssertionError("custom results received context"); }
    }

    private static <T> T bind(Binding binding, Class<T> contract, T nativeValue, GameModule root, Fixture fixture) {
        if (binding == Binding.SERVICE_THEN_PATCH) {
            var registration = new ModContext("registered-owner", "s3k", ModAssetRoot.forTests("registered-owner"));
            registration.registerService("provider", contract, nativeValue);
            var services = new ModBackedGamePatch(registration.freeze(), fixture.boundary()).apply(root, null);
            nativeValue = services.getGameService(contract);
        }
        T selected = nativeValue;
        var module = new DelegatingGameModule(root, "claimed-other-owner:providers") {
            @Override public TitleCardProvider getTitleCardProvider() {
                return contract == TitleCardProvider.class ? (TitleCardProvider)selected : super.getTitleCardProvider();
            }
            @Override public SpecialStageProvider getSpecialStageProvider() {
                return contract == SpecialStageProvider.class ? (SpecialStageProvider)selected : super.getSpecialStageProvider();
            }
        };
        GameModule owned = binding == Binding.STANDALONE
                ? OwnerAwareStandaloneModule.wrap("registered-owner", module, fixture.boundary(), Map.of())
                : OwnerBoundGamePatch.wrap("registered-owner", patch(module), fixture.boundary()).apply(root, null);
        return contract.cast(contract == TitleCardProvider.class ? owned.getTitleCardProvider() : owned.getSpecialStageProvider());
    }

    private static GamePatch patch(GameModule result) {
        return new GamePatch() {
            @Override public String id() { return "registered-owner:providers"; }
            @Override public String displayName() { return "providers"; }
            @Override public String baseGameId() { return "s3k"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of(); }
            @Override public GameModule apply(GameModule base, PatchContext context) { return result; }
        };
    }
}
