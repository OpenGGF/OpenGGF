package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeArtAdmissionLease;
import com.openggf.game.RuntimeArtAdmissionOwnerKind;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kFreshTitleCardElementObjectInstance;
import com.openggf.game.sonic3k.objects.S3kFreshTitleCardOwnerObjectInstance;
import com.openggf.game.sonic3k.resources.S3kKosDecompressionDescriptor;
import com.openggf.game.sonic3k.resources.S3kKosDecompressionQueue;
import com.openggf.game.sonic3k.resources.S3kKosModuleDescriptor;
import com.openggf.game.sonic3k.resources.S3kKosModuleQueue;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.runs.RunLevelLoadCause;
import com.openggf.trace.replay.runs.RunLevelLoadTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R58 independent public saved-return question, source base 28390f91.
 * Native044 loc_62B6 writes SST8; Obj_TitleCardCreate links four higher-slot
 * children; Wait2 drains them before LoadEnemyArt. No trace or timing oracle.
 * Observations bracket ordinary steps, not the inside of an owner callback.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBonusReturnTitleOwner {
    private static final int ENTRY_LIMIT = 600;
    private static final int RETURN_LIMIT = 800;

    @AfterEach
    void cleanup() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
        SessionManager.clear();
    }

    @Test
    void publicGumballReturnRetainsNativeTitleGraphAndEnemyHandoff() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootOrigin(session, settings);
            var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
            var origin = receipt(tracker);
            var originObjects = GameServices.level().getObjectManager();
            var outgoing = history();
            int outgoingFrame = outgoing.currentFrame();
            GameServices.level().requestBonusStageEntry(BonusStageType.GUMBALL);
            RunLevelLoadTracker.Receipt entry = null;
            boolean sawEntryTitle = false;
            for (int step = 0; step < ENTRY_LIMIT; step++) {
                advance(session);
                var current = receipt(tracker);
                if (current.identity().loadGeneration() == origin.identity().loadGeneration()) continue;
                if (entry == null) {
                    entry = current;
                    newLoad(origin, entry, outgoing, outgoingFrame);
                    assertNotSame(originObjects, GameServices.level().getObjectManager());
                }
                assertTrue(entry == current, "entry receipt identity must remain stable");
                actualReceipt(tracker, current);
                sawEntryTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
                if (sawEntryTitle && settled(session, GameMode.BONUS_STAGE)) break;
            }
            assertNotNull(entry, "ordinary public entry must load Gumball");
            assertTrue(sawEntryTitle && settled(session, GameMode.BONUS_STAGE));
            assertEquals(0x13, entry.identity().progressionZone());
            assertEquals(0, entry.identity().act());
            assertEquals(RunLevelLoadCause.ORDINARY, entry.cause());
            awaitHistory(session, GameMode.BONUS_STAGE);
            assertTrue(entry == receipt(tracker));
            var bonus = assertInstanceOf(Sonic3kBonusStageCoordinator.class, GameServices.bonusStage());
            assertEquals(BonusStageType.GUMBALL, bonus.getActiveType());
            var saved = bonus.getSavedState();
            assertNotNull(saved, "actual saved-origin return; null fallback is not this inlet");
            assertEquals(0, saved.savedZoneAndAct());
            assertEquals(0, saved.savedApparentZoneAndAct());
            var expected = nativeReturnParents(); // Independent ROM decode, never a runtime queue descriptor.
            var interiorObjects = GameServices.level().getObjectManager();
            var interiorHistory = history();
            int interiorFrame = interiorHistory.currentFrame();
            bonus.requestExit(); // ONCE. The real GameLoop owns fade/load/title and release.
            RunLevelLoadTracker.Receipt returned = null;
            TitleRun run = null;
            for (int step = 0; step < RETURN_LIMIT; step++) {
                advance(session);
                var current = receipt(tracker);
                if (current.identity().loadGeneration() == entry.identity().loadGeneration()) continue;
                if (returned == null) {
                    returned = current;
                    newLoad(entry, returned, interiorHistory, interiorFrame);
                    assertNotSame(interiorObjects, GameServices.level().getObjectManager());
                    run = new TitleRun(session);
                }
                assertTrue(returned == current, "title progress cannot manufacture another receipt");
                actualReceipt(tracker, current);
                run.observe(session);
                if (run.naturallyComplete(session)) break;
            }
            assertNotNull(returned, "the normal fade must publish a new saved-origin load");
            assertEquals(RunLevelLoadCause.INTERIOR_RETURN, returned.cause());
            assertEquals(origin.identity().progressionZone(), returned.identity().progressionZone());
            assertEquals(origin.identity().romZone(), returned.identity().romZone());
            assertEquals(origin.identity().act(), returned.identity().act());
            assertEquals(BonusStageType.NONE, bonus.getActiveType());
            assertNotNull(run);
            run.assertPrerequisites(session); // Does NOT require the expected SST graph to exist.
            run.assertNativeGraphAndRetirement(); // Absent parent/children is a FINAL semantic oracle.
            assertEquals(expected, run.submittedParents(), "complete unfiltered return parent order");
        }
    }

    @Test
    void genuineFreshTitleRetainsAndNaturallyRetiresItsNativeGraph() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootOrigin(session, settings);
            var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
            var origin = receipt(tracker);
            var outgoing = history();
            int outgoingFrame = outgoing.currentFrame();
            // Declared positive control, not a substitute for the saved-return caller above.
            GameServices.level().loadZoneAndActWithTitleCard(0, 1);
            var fresh = receipt(tracker);
            newLoad(origin, fresh, outgoing, outgoingFrame);
            assertEquals(RunLevelLoadCause.ORDINARY, fresh.cause());
            var run = new TitleRun(session);
            for (int step = 0; step < ENTRY_LIMIT; step++) {
                run.observe(session);
                if (run.naturallyComplete(session)) break;
                advance(session);
                assertTrue(fresh == receipt(tracker));
                actualReceipt(tracker, fresh);
            }
            run.assertPrerequisites(session);
            run.assertNativeGraphAndRetirement();
        }
    }

    @Test
    void ordinaryNonFreshHostLoadDoesNotManufactureFixedTitleOwners() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootOrigin(session, settings);
            var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
            var origin = receipt(tracker);
            var outgoing = history();
            int outgoingFrame = outgoing.currentFrame();
            GameServices.level().loadZoneAndAct(1, 0); // Ordinary host load, no fresh/title flag writes.
            var ordinary = receipt(tracker);
            newLoad(origin, ordinary, outgoing, outgoingFrame);
            assertEquals(RunLevelLoadCause.ORDINARY, ordinary.cause());
            assertEquals(1, ordinary.identity().progressionZone());
            boolean complete = false;
            for (int step = 0; step < ENTRY_LIMIT; step++) {
                var graph = graph();
                assertNull(graph.parent(), "host load must not invent a native title SST");
                assertTrue(graph.children().isEmpty());
                assertTrue(ordinary == receipt(tracker));
                actualReceipt(tracker, ordinary);
                if (settled(session, GameMode.LEVEL) && title(session).isComplete()
                        && GameServices.hardwareTiming().pendingHandles().isEmpty()) {
                    complete = true;
                    break;
                }
                advance(session);
            }
            assertTrue(complete, "host presentation must finish through ordinary neutral steps");
        }
    }

    private static GameplayCaptureSession.Settings settings() {
        return new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
    }

    private static void bootOrigin(GameplayCaptureSession session, GameplayCaptureSession.Settings settings)
            throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
        boolean sawTitle = session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        boolean ready = false;
        for (int step = 0; step < ENTRY_LIMIT; step++) {
            if (sawTitle && settled(session, GameMode.LEVEL) && title(session).isComplete()
                    && GameServices.hardwareTiming().pendingHandles().isEmpty()) {
                ready = true;
                break;
            }
            advance(session);
            sawTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        }
        assertTrue(ready, "origin TITLE_CARD must be seen, its request consumed, queues naturally quiescent");
        assertEquals(320, GameServices.camera().getWidth(), "actual native viewport, not settings alone");
        assertInstanceOf(Knuckles.class, GameServices.sprites().getMainPlayable());
        awaitHistory(session, GameMode.LEVEL);
        var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
        var origin = receipt(tracker);
        actualReceipt(tracker, origin);
        assertEquals(RunLevelLoadCause.ORDINARY, origin.cause());
        assertEquals(0, origin.identity().progressionZone());
        assertEquals(0, origin.identity().act());
        assertEquals(0, GameServices.level().getApparentAct());
    }

    private static void advance(GameplayCaptureSession session) {
        session.step(null); // Exactly one ordinary neutral input step.
        session.renderFrame(); // Normal renderer; no readback or callback replacement.
    }

    private static boolean settled(GameplayCaptureSession session, GameMode mode) {
        var context = SessionManager.getCurrentGameplayMode();
        return session.loop().getCurrentGameMode() == mode && !GameServices.level().isTitleCardRequested()
                && !context.getFadeManager().isActive() && !context.getFadeManager().hasPendingCompletion()
                && !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                && !GameServices.sprites().getMainPlayable().isControlLocked();
    }

    private static RunLevelLoadTracker.Receipt receipt(RunLevelLoadTracker tracker) {
        assertTrue(tracker == SessionManager.getCurrentGameplayMode().runLevelLoads());
        return tracker.latest().orElseThrow(() -> new AssertionError("actual production receipt required"));
    }

    private static void actualReceipt(RunLevelLoadTracker tracker, RunLevelLoadTracker.Receipt receipt) {
        assertTrue(receipt == receipt(tracker));
        var level = GameServices.level();
        assertEquals(level.getCompletedProductionLoadGeneration(), tracker.generation());
        assertEquals(tracker.generation(), receipt.identity().loadGeneration());
        assertEquals(level.getCurrentZone(), receipt.identity().progressionZone());
        assertEquals(level.getRomZoneId(), receipt.identity().romZone());
        assertEquals(level.getCurrentAct(), receipt.identity().act());
    }

    private static RewindController history() {
        var history = SessionManager.getCurrentGameplayMode().getRewindController();
        assertNotNull(history);
        assertTrue(history.currentFrame() > 1, "real history, never a seeded frame index");
        return history;
    }

    private static void awaitHistory(GameplayCaptureSession session, GameMode mode) {
        for (int step = 0; step < ENTRY_LIMIT; step++) {
            var history = SessionManager.getCurrentGameplayMode().getRewindController();
            if (history != null && history.currentFrame() > 1) return;
            assertEquals(mode, session.loop().getCurrentGameMode());
            advance(session);
        }
        fail("the actual stable world must record history");
    }

    private static void newLoad(RunLevelLoadTracker.Receipt before, RunLevelLoadTracker.Receipt after,
                                RewindController outgoing, int outgoingFrame) {
        assertTrue(before != after && after.identity().loadGeneration() > before.identity().loadGeneration());
        var incoming = SessionManager.getCurrentGameplayMode().getRewindController();
        assertTrue(incoming == null || incoming != outgoing || incoming.currentFrame() < outgoingFrame,
                "load retires outgoing history; no cross-load snapshot/receipt restoration");
    }

    private static Object raw(Class<?> owner, Object instance, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true); // Read-only; no field writes or producer calls.
        Object before = field.get(instance), after = field.get(instance);
        if (field.getType().isPrimitive()) assertEquals(before, after, "raw scalar read passivity");
        else assertTrue(before == after, "raw reference read passivity");
        return before;
    }

    private static Sonic3kTitleCardManager title(GameplayCaptureSession session) throws Exception {
        Object before = raw(GameLoop.class, session.loop(), "titleCardProvider");
        var title = assertInstanceOf(Sonic3kTitleCardManager.class, before);
        assertTrue(before == raw(GameLoop.class, session.loop(), "titleCardProvider"));
        return title; // Never GameLoop.getTitleCardProvider(): it delegates to a lazy getter.
    }

    private record Child(S3kFreshTitleCardElementObjectInstance object,
                         S3kFreshTitleCardOwnerObjectInstance parent, int slot) { }
    private record Graph(S3kFreshTitleCardOwnerObjectInstance parent, int slot, int childrenLeft,
                         List<Child> children) { }

    private static Graph readGraph(ObjectManager objects) throws Exception {
        var parents = objects.activeObjectsOfType(S3kFreshTitleCardOwnerObjectInstance.class).stream()
                .filter(owner -> !owner.isDestroyed()).toList();
        assertTrue(parents.size() <= 1, "an extra actual title owner is an unexpected-owner STOP");
        var parent = parents.isEmpty() ? null : parents.getFirst();
        List<Child> children = new ArrayList<>();
        for (var child : objects.activeObjectsOfType(S3kFreshTitleCardElementObjectInstance.class)) {
            if (child.isDestroyed()) continue;
            var link = assertInstanceOf(S3kFreshTitleCardOwnerObjectInstance.class,
                    raw(S3kFreshTitleCardElementObjectInstance.class, child, "parent"));
            children.add(new Child(child, link, child.getSlotIndex()));
        }
        return new Graph(parent, parent == null ? -1 : parent.getSlotIndex(), parent == null ? -1
                : (Integer) raw(S3kFreshTitleCardOwnerObjectInstance.class, parent, "childrenLeft"), List.copyOf(children));
    }

    private static Graph graph() throws Exception {
        var objects = GameServices.level().getObjectManager();
        var pending = GameServices.hardwareTiming().pendingHandles();
        Graph a = readGraph(objects), b = readGraph(objects);
        assertTrue(objects == GameServices.level().getObjectManager());
        assertTrue(a.parent() == b.parent());
        assertEquals(a.slot(), b.slot());
        assertEquals(a.childrenLeft(), b.childrenLeft());
        assertEquals(a.children().size(), b.children().size());
        for (int i = 0; i < a.children().size(); i++) {
            Child x = a.children().get(i), y = b.children().get(i);
            assertTrue(x.object() == y.object() && x.parent() == y.parent());
            assertEquals(x.slot(), y.slot());
        }
        assertEquals(pending, GameServices.hardwareTiming().pendingHandles());
        return a; // Passivity is this explicit projection, not whole-world purity.
    }

    private record TitleFlags(boolean artLoading, int handles, boolean ready, boolean terrain,
                              boolean consumed, long leaseId) { }

    private static TitleFlags flags(Sonic3kTitleCardManager title) throws Exception {
        var a = readFlags(title);
        assertEquals(a, readFlags(title), "raw title projection must be unchanged by observation");
        return a;
    }

    private static TitleFlags readFlags(Sonic3kTitleCardManager title) throws Exception {
        return new TitleFlags((Boolean) raw(Sonic3kTitleCardManager.class, title, "artLoading"),
                assertInstanceOf(List.class, raw(Sonic3kTitleCardManager.class, title, "artHandles")).size(),
                (Boolean) raw(Sonic3kTitleCardManager.class, title, "freshLevelTitleReady"),
                (Boolean) raw(Sonic3kTitleCardManager.class, title, "freshLevelTerrainStarted"),
                (Boolean) raw(Sonic3kTitleCardManager.class, title, "runtimeArtAdmissionConsumed"),
                (Long) raw(Sonic3kTitleCardManager.class, title, "runtimeArtAdmissionLeaseId"));
    }

    /** Fresh continuous observation only: no restore, descriptor getter, preparation or claim. */
    private static final class TitleRun {
        private final ObjectManager objects = GameServices.level().getObjectManager();
        private final Sonic3kTitleCardManager title;
        private final Sonic3kGameModule module = assertInstanceOf(Sonic3kGameModule.class, GameServices.module());
        private final Sonic3kObjectArtProvider provider;
        private final RuntimeArtAdmissionLease lease;
        private final S3kRuntimeArtCoordinator art = S3kRuntimeArtCoordinator.current();
        private final Map<?, ?> modules, direct;
        private final LinkedHashMap<HardwareWorkHandle, Object> seen = new LinkedHashMap<>();
        private Graph firstGraph;
        private boolean sawTitle, sawFourArtHandles, sawDrainedOwner, sawRetired, consumedWithLiveGraph;

        private TitleRun(GameplayCaptureSession session) throws Exception {
            title = title(session);
            Object p = raw(Sonic3kGameModule.class, module, "objectArtProvider");
            provider = assertInstanceOf(Sonic3kObjectArtProvider.class, p);
            assertTrue(p == raw(Sonic3kGameModule.class, module, "objectArtProvider"));
            Object l = raw(Sonic3kObjectArtProvider.class, provider, "runtimeArtAdmissionLease");
            lease = assertInstanceOf(RuntimeArtAdmissionLease.class, l);
            assertTrue(l == raw(Sonic3kObjectArtProvider.class, provider, "runtimeArtAdmissionLease"));
            modules = assertInstanceOf(Map.class, raw(S3kKosModuleQueue.class, art.moduleQueue(), "descriptors"));
            direct = assertInstanceOf(Map.class, raw(S3kKosDecompressionQueue.class, art.directQueue(), "descriptors"));
        }

        private void observe(GameplayCaptureSession session) throws Exception {
            assertTrue(objects == GameServices.level().getObjectManager() && title == title(session));
            assertTrue(module == GameServices.module() && art == S3kRuntimeArtCoordinator.current());
            assertTrue(provider == raw(Sonic3kGameModule.class, module, "objectArtProvider"));
            assertTrue(lease == raw(Sonic3kObjectArtProvider.class, provider, "runtimeArtAdmissionLease"));
            assertTrue(modules == raw(S3kKosModuleQueue.class, art.moduleQueue(), "descriptors"));
            assertTrue(direct == raw(S3kKosDecompressionQueue.class, art.directQueue(), "descriptors"));
            var before = GameServices.hardwareTiming().pendingHandles();
            for (var handle : before) { // ALL unclaimed ledger kinds, not a physical FIFO snapshot.
                assertTrue(handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE
                        || handle.kind() == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, "unexpected pending kind STOP");
                Object descriptor = handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE
                        ? assertInstanceOf(S3kKosModuleDescriptor.class, modules.get(handle))
                        : assertInstanceOf(S3kKosDecompressionDescriptor.class, direct.get(handle));
                Object prior = seen.putIfAbsent(handle, descriptor);
                assertTrue(prior == null || prior == descriptor, "continuous cache descriptor identity");
                assertTrue(descriptor == (handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE
                        ? modules : direct).get(handle));
            }
            assertEquals(before, GameServices.hardwareTiming().pendingHandles());
            Graph graph = graph();
            TitleFlags state = flags(title);
            if (session.loop().getCurrentGameMode() == GameMode.TITLE_CARD) {
                sawTitle = true;
                assertEquals(lease.id(), state.leaseId(), "actual new title must bind the actual load lease");
                if (state.artLoading()) {
                    assertEquals(4, state.handles(), "actual ordinary title batch prerequisite");
                    sawFourArtHandles = true;
                }
            }
            if (graph.parent() != null) {
                assertTrue(firstGraph == null || firstGraph.parent() == graph.parent(), "one actual title owner");
                // Native children appear after the archive gate, not necessarily on the first parent sample.
                if (firstGraph == null || graph.children().size() > firstGraph.children().size()) firstGraph = graph;
            }
            if (state.leaseId() == lease.id()) { // Actual owner identity; pre-initialization title flags belong to the old lease.
                assertTrue((Boolean) raw(Sonic3kObjectArtProvider.class, provider, "runtimeArtAdmissionBound"));
                if (firstGraph != null && !firstGraph.children().isEmpty() && graph.parent() != null
                        && graph.childrenLeft() == 0 && graph.children().isEmpty()
                        && !state.consumed()) sawDrainedOwner = true;
                consumedWithLiveGraph |= state.consumed() && (graph.parent() != null || !graph.children().isEmpty());
            }
            if (firstGraph != null && graph.parent() == null && graph.children().isEmpty()
                    && firstGraph.parent().isDestroyed()) sawRetired = true;
            assertTrue(lease == raw(Sonic3kObjectArtProvider.class, provider, "runtimeArtAdmissionLease"));
        }

        private boolean naturallyComplete(GameplayCaptureSession session) {
            return sawTitle && settled(session, GameMode.LEVEL) && title.isComplete()
                    && (firstGraph == null || sawRetired)
                    && GameServices.hardwareTiming().pendingHandles().isEmpty();
        }

        private void assertPrerequisites(GameplayCaptureSession session) throws Exception {
            assertTrue(sawTitle && sawFourArtHandles && naturallyComplete(session),
                    "real title, pending four archives, natural LEVEL/complete and queue drain required");
            var state = flags(title);
            assertFalse(state.artLoading());
            assertTrue(state.ready() && state.terrain(), "actual movement and terrain/Nemesis gate decisions");
            Object plc = raw(Sonic3kGameModule.class, module, "levelTitlePlcService");
            assertFalse(assertInstanceOf(Sonic3kLevelTitlePlcService.class, plc).isBusy());
            assertTrue(plc == raw(Sonic3kGameModule.class, module, "levelTitlePlcService"));
            assertEquals(RuntimeArtAdmissionOwnerKind.TITLE_OWNER, lease.ownerKind());
            assertEquals(lease.id(), state.leaseId());
        }

        private void assertNativeGraphAndRetirement() throws Exception {
            assertNotNull(firstGraph, "native loc_62B6 must install the actual saved-return title SST8 parent");
            assertEquals(8, firstGraph.slot());
            assertEquals(4, firstGraph.childrenLeft());
            assertEquals(List.of(9, 10, 11, 12), firstGraph.children().stream().map(Child::slot).toList());
            for (var child : firstGraph.children()) {
                assertTrue(child.parent() == firstGraph.parent(), "genuine parent2 link, not a seeded/nearest parent");
                assertTrue(child.object().isDestroyed(), "retained reference is retired, not physically live SST");
            }
            assertTrue(firstGraph.parent().isDestroyed());
            assertTrue(sawDrainedOwner && sawRetired, "ordinary samples must see children drain before owner deletion");
            assertFalse(consumedWithLiveGraph, "outside samples cannot show admission ahead of native retirement");
            assertTrue(flags(title).consumed(), "the same actual title lease eventually consumed");
            assertTrue((Boolean) raw(Sonic3kObjectArtProvider.class, provider, "runtimeArtAdmissionConsumed"));
            // These outside observations do not prove which callback consumed the lease or its in-service clock.
        }

        private List<S3kKosModuleDescriptor> submittedParents() {
            return seen.entrySet().stream().filter(entry -> entry.getKey().kind() == HardwareWorkKind.KOS_MODULE_QUEUE)
                    .map(entry -> (S3kKosModuleDescriptor) entry.getValue()).toList();
        }
    }

    private static List<S3kKosModuleDescriptor> nativeReturnParents() throws Exception {
        var rom = TestEnvironment.currentRom();
        byte[] bytes = rom.readBytes(0, Math.toIntExact(rom.getSize())); // Independent ROM oracle, not prepared payload.
        List<S3kKosModuleDescriptor> expected = new ArrayList<>();
        // Obj_TitleCardInit native044:62156..62196; apparent act0 and locked-on S3K.
        expected.add(archive(bytes, 0x0D6F28, 0xA000));
        expected.add(archive(bytes, 0x15C3A2, 0xA200));
        expected.add(archive(bytes, 0x0D6D84, 0xA7A0));
        expected.add(archive(bytes, 0x39BDC8, 0xA9A0));
        // LoadLevelLoadBlock Knuckles AIZ1 saved table26; native primary then secondary.
        int table = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR + 26 * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;
        assertEquals(0x3A566A, rom.read32BitAddr(table) & 0xFFFFFF);
        assertEquals(0x3A944E, rom.read32BitAddr(table + 4) & 0xFFFFFF);
        var primary = archive(bytes, 0x3A566A, 0);
        expected.add(primary);
        expected.add(archive(bytes, 0x3A944E, primary.destinationLength()));
        // Native PLCKosM_AIZ:64383..64387; byte destinations = literal native tiles *32.
        expected.add(archive(bytes, 0x36800C, 0x548 * 32));
        expected.add(archive(bytes, 0x367DCA, 0x52A * 32));
        expected.add(archive(bytes, 0x3681FE, 0x55F * 32));
        return List.copyOf(expected); // Each enemy archive remains its own production parent.
    }

    private static S3kKosModuleDescriptor archive(byte[] rom, int source, int destination) throws Exception {
        var shape = KosinskiReader.inspectModuled(rom, source);
        return new S3kKosModuleDescriptor(source, shape.compressedLength(), destination,
                shape.decompressedLength(), shape.moduleCount());
    }
}
