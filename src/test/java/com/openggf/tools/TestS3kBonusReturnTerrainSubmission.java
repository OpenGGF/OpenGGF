package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kKosDecompressionDescriptor;
import com.openggf.game.sonic3k.resources.S3kKosDecompressionQueue;
import com.openggf.game.sonic3k.resources.S3kKosModuleDescriptor;
import com.openggf.game.sonic3k.resources.S3kKosModuleQueue;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R52 independent saved-origin return question, source base 14bdcedc6.
 * Native044 loc_62CC/loc_6310 -> LoadLevelLoadBlock: after title/Nemesis gates,
 * Knuckles AIZ1 uses entry26, primary at zero then unequal secondary after primary.
 * Public completion input only; no recorded clock/ordinal/FP, queue or gameplay writes.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBonusReturnTerrainSubmission {
    private static final int ENTRY_LIMIT = 600;
    private static final int RETURN_LIMIT = 800;
    private static final int HCZ = 1;

    @AfterEach
    void cleanup() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
        SessionManager.clear();
    }

    @Test
    void publicGumballReturnSubmitsNativeSavedOriginTerrainAfterTitleGates() throws Exception {
        assertSavedOriginReturn(BonusStageType.GUMBALL, 0x13);
    }

    @Test
    void publicPachinkoReturnSubmitsNativeSavedOriginTerrainAfterTitleGates() throws Exception {
        assertSavedOriginReturn(BonusStageType.GLOWING_SPHERE, 0x14);
    }

    @Test
    void publicSlotReturnSubmitsNativeSavedOriginTerrainAfterTitleGates() throws Exception {
        assertSavedOriginReturn(BonusStageType.SLOT_MACHINE, 0x15);
    }

    @Test
    void ordinaryNonFreshHostLoadDoesNotRequestNativeFreshTerrain() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootOrigin(session, settings);
            var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
            var origin = currentReceipt(tracker);
            var outgoingHistory = history();
            int outgoingFrame = outgoingHistory.currentFrame();
            var nativeTerrain = terrainAt(HCZ * 2);
            var observed = new FreshSubmissions();
            GameServices.level().loadZoneAndAct(HCZ, 0); // Ordinary host load, not fresh-runtime entry.
            var ordinary = currentReceipt(tracker);
            assertNewLoad(origin, ordinary);
            assertHistoryRetired(outgoingHistory, outgoingFrame);
            assertEquals(RunLevelLoadCause.ORDINARY, ordinary.cause());
            assertEquals(HCZ, ordinary.identity().progressionZone());
            observed.read();
            boolean released = false;
            for (int step = 0; step < ENTRY_LIMIT; step++) {
                assertSame(ordinary, currentReceipt(tracker));
                if (settled(session, GameMode.LEVEL) && card(session).isComplete()) {
                    released = true;
                    break;
                }
                advance(session);
                observed.read();
            }
            assertTrue(released, "ordinary pending presentation must finish without a forced fresh handoff");
            assertActualReceipt(tracker, ordinary);
            assertEquals(BonusStageType.NONE, GameServices.bonusStage().getActiveType());
            assertEquals(-1, card(session).capture().freshLevelRuntimeArtHandoffLevelIndex());
            for (var descriptor : nativeTerrain) {
                assertFalse(observed.moduleParents().contains(descriptor),
                        () -> "ordinary non-fresh load must not acquire native fresh terrain: " + observed);
            }
            // Other title/enemy/direct work is retained, not required to be globally empty.
        }
    }

    private static void assertSavedOriginReturn(BonusStageType type, int bonusZone) throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootOrigin(session, settings);
            var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
            var origin = currentReceipt(tracker);
            var originObjects = GameServices.level().getObjectManager();
            var originHistory = history();
            int originFrame = originHistory.currentFrame();
            GameServices.level().requestBonusStageEntry(type);
            var entry = awaitBonusEntry(session, tracker, origin, bonusZone, originHistory, originFrame);
            assertNotSame(originObjects, GameServices.level().getObjectManager());
            var provider = assertInstanceOf(Sonic3kBonusStageCoordinator.class, GameServices.bonusStage());
            assertEquals(type, provider.getActiveType());
            var saved = provider.getSavedState();
            assertNotNull(saved, "actual saved-origin return only; null fallback is not this oracle");
            assertEquals(0, saved.savedZoneAndAct());
            assertEquals(0, saved.savedApparentZoneAndAct());
            var terrain = nativeKnucklesAizTerrain();
            var expected = returnTitleAndTerrain(terrain);
            var interiorObjects = GameServices.level().getObjectManager();
            var interiorHistory = history();
            int interiorFrame = interiorHistory.currentFrame();
            provider.requestExit(); // Once; ordinary GameLoop owns fade, reload and title.
            RunLevelLoadTracker.Receipt returned = null;
            FreshSubmissions observed = null;
            boolean sawTitle = false;
            boolean sawPendingTitleBatch = false;
            boolean released = false;
            for (int step = 0; step < RETURN_LIMIT; step++) {
                advance(session);
                var receipt = currentReceipt(tracker);
                if (receipt.identity().loadGeneration() == entry.identity().loadGeneration()) continue;
                if (returned == null) {
                    returned = receipt;
                    assertNewLoad(entry, returned);
                    assertHistoryRetired(interiorHistory, interiorFrame);
                    assertNotSame(interiorObjects, GameServices.level().getObjectManager());
                    observed = new FreshSubmissions(); // Arm on actual saved-return load, not a fitted row.
                }
                assertSame(returned, receipt, "title progress must not invent another load");
                assertActualReceipt(tracker, returned);
                observed.read();
                if (session.loop().getCurrentGameMode() == GameMode.TITLE_CARD) {
                    sawTitle = true;
                    var title = card(session).capture();
                    if (title.artLoading()) {
                        assertEquals(4, title.artHandles().size(), "actual ordinary return-title batch");
                        sawPendingTitleBatch = true;
                    }
                }
                if (sawTitle && settled(session, GameMode.LEVEL)) {
                    released = true;
                    break;
                }
            }
            assertNotNull(returned, "the ordinary fade must publish an actual new saved-origin load");
            assertTrue(sawTitle && sawPendingTitleBatch, "observe the real return title and pending four archives");
            assertTrue(released, "natural title/fade/control release precedes the final semantic oracle");
            assertEquals(BonusStageType.NONE, provider.getActiveType());
            assertEquals(origin.identity().progressionZone(), returned.identity().progressionZone());
            assertEquals(origin.identity().romZone(), returned.identity().romZone());
            assertEquals(origin.identity().act(), returned.identity().act());
            assertEquals(RunLevelLoadCause.INTERIOR_RETURN, returned.cause());
            assertActualReceipt(tracker, returned);
            var title = card(session).capture();
            assertFalse(title.artLoading());
            assertTrue(title.freshLevelTitleReady(), "actual title movement gate opened");
            assertTrue(title.freshLevelTerrainStarted(), "owner reached its terrain publication decision");
            var plc = GameServices.module().getGameService(Sonic3kLevelTitlePlcService.class);
            assertNotNull(plc);
            assertFalse(plc.isBusy(), "actual Nemesis gate opened, not a forced release");
            assertNotNull(observed);
            assertEquals(expected, observed.moduleParents(),
                    type + " native saved-origin title+terrain order; " + observed + "; title=" + title);
            // No cross-load receipt/history snapshot or whole-world rewind claim.
        }
    }

    private static GameplayCaptureSession.Settings settings() {
        return new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
    }

    private static void bootOrigin(GameplayCaptureSession session,
                                   GameplayCaptureSession.Settings settings) throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
        boolean sawTitle = session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        boolean ready = false;
        for (int step = 0; step < ENTRY_LIMIT; step++) {
            if (sawTitle && settled(session, GameMode.LEVEL) && card(session).isComplete()
                    && GameServices.hardwareTiming().pendingHandles().isEmpty()) {
                ready = true;
                break;
            }
            advance(session);
            sawTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        }
        assertTrue(ready && sawTitle, "actual origin title/no request/LEVEL/complete/FIFO quiescence required");
        assertInstanceOf(Knuckles.class, GameServices.sprites().getMainPlayable());
        awaitHistory(session, GameMode.LEVEL);
        var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
        var receipt = currentReceipt(tracker);
        assertActualReceipt(tracker, receipt);
        assertEquals(RunLevelLoadCause.ORDINARY, receipt.cause());
        assertEquals(0, receipt.identity().progressionZone());
        assertEquals(0, receipt.identity().act());
        assertEquals(0, GameServices.level().getApparentAct());
    }

    private static RunLevelLoadTracker.Receipt awaitBonusEntry(GameplayCaptureSession session,
            RunLevelLoadTracker tracker, RunLevelLoadTracker.Receipt origin, int zone,
            RewindController outgoingHistory, int outgoingFrame) {
        RunLevelLoadTracker.Receipt entry = null;
        boolean sawTitle = false;
        for (int step = 0; step < ENTRY_LIMIT; step++) {
            advance(session);
            var receipt = currentReceipt(tracker);
            if (receipt.identity().loadGeneration() == origin.identity().loadGeneration()) continue;
            if (entry == null) {
                entry = receipt;
                assertNewLoad(origin, entry);
                assertHistoryRetired(outgoingHistory, outgoingFrame);
            }
            assertSame(entry, receipt);
            sawTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
            if (sawTitle && settled(session, GameMode.BONUS_STAGE)) {
                assertEquals(zone, entry.identity().progressionZone());
                assertEquals(0, entry.identity().act());
                assertEquals(RunLevelLoadCause.ORDINARY, entry.cause());
                assertActualReceipt(tracker, entry);
                awaitHistory(session, GameMode.BONUS_STAGE);
                assertSame(entry, currentReceipt(tracker));
                return entry;
            }
        }
        throw new AssertionError("ordinary public bonus entry/title/history must naturally complete");
    }

    private static List<S3kKosModuleDescriptor> nativeKnucklesAizTerrain() throws IOException {
        // Native044 LoadLevelLoadBlock: AIZ1 Player_mode>=2 -> $0D00 -> table entry26.
        var descriptors = terrainAt(26);
        assertEquals(2, descriptors.size(), "native unequal primary/secondary branch");
        assertEquals(0x3A566A, descriptors.get(0).sourceAddress());
        assertEquals(0, descriptors.get(0).destinationAddress());
        assertEquals(6080, descriptors.get(0).destinationLength());
        assertEquals(2, descriptors.get(0).moduleCount());
        assertEquals(0x3A944E, descriptors.get(1).sourceAddress());
        assertEquals(6080, descriptors.get(1).destinationAddress());
        assertEquals(18720, descriptors.get(1).destinationLength());
        assertEquals(5, descriptors.get(1).moduleCount());
        return descriptors;
    }

    private static List<S3kKosModuleDescriptor> terrainAt(int index) throws IOException {
        var rom = TestEnvironment.currentRom();
        int entry = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR + index * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;
        int primary = rom.read32BitAddr(entry) & 0xFFFFFF;
        int secondary = rom.read32BitAddr(entry + 4) & 0xFFFFFF;
        List<S3kKosModuleDescriptor> result = new ArrayList<>();
        result.add(archive(primary, 0));
        if (secondary != primary) result.add(archive(secondary, result.getFirst().destinationLength()));
        return List.copyOf(result);
    }

    private static S3kKosModuleDescriptor archive(int source, int destination) throws IOException {
        var rom = TestEnvironment.currentRom();
        var shape = KosinskiReader.inspectModuled(rom.readBytes(0, Math.toIntExact(rom.getSize())), source);
        return new S3kKosModuleDescriptor(source, shape.compressedLength(), destination,
                shape.decompressedLength(), shape.moduleCount());
    }

    private static List<S3kKosModuleDescriptor> returnTitleAndTerrain(List<S3kKosModuleDescriptor> terrain)
            throws IOException {
        // Obj_TitleCardInit native act0: RedAct, ZONE, Num1, AIZ, then Level's terrain.
        List<S3kKosModuleDescriptor> expected = new ArrayList<>();
        expected.add(archive(Sonic3kConstants.ART_KOSM_TITLE_CARD_RED_ACT_ADDR, 0xA000));
        expected.add(archive(Sonic3kConstants.ART_KOSM_TITLE_CARD_S3K_ZONE_ADDR, 0xA200));
        expected.add(archive(Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM1_ADDR, 0xA7A0));
        expected.add(archive(Sonic3kConstants.TITLE_CARD_ZONE_ART_ADDRS[0], 0xA9A0));
        expected.addAll(terrain);
        return List.copyOf(expected);
    }

    private static boolean settled(GameplayCaptureSession session, GameMode mode) {
        var context = SessionManager.getCurrentGameplayMode();
        return session.loop().getCurrentGameMode() == mode
                && !GameServices.level().isTitleCardRequested()
                && !context.getFadeManager().isActive() && !context.getFadeManager().hasPendingCompletion()
                && !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                && !GameServices.sprites().getMainPlayable().isControlLocked();
    }

    private static RunLevelLoadTracker.Receipt currentReceipt(RunLevelLoadTracker tracker) {
        assertSame(tracker, SessionManager.getCurrentGameplayMode().runLevelLoads());
        return tracker.latest().orElseThrow(() -> new AssertionError("actual production load receipt required"));
    }

    private static void assertActualReceipt(RunLevelLoadTracker tracker, RunLevelLoadTracker.Receipt receipt) {
        assertSame(receipt, currentReceipt(tracker));
        var level = GameServices.level();
        assertEquals(level.getCompletedProductionLoadGeneration(), tracker.generation());
        assertEquals(tracker.generation(), receipt.identity().loadGeneration());
        assertEquals(level.getCurrentZone(), receipt.identity().progressionZone());
        assertEquals(level.getRomZoneId(), receipt.identity().romZone());
        assertEquals(level.getCurrentAct(), receipt.identity().act());
    }

    private static void assertNewLoad(RunLevelLoadTracker.Receipt before, RunLevelLoadTracker.Receipt after) {
        assertNotSame(before, after);
        assertTrue(after.identity().loadGeneration() > before.identity().loadGeneration());
    }

    private static RewindController history() {
        var history = SessionManager.getCurrentGameplayMode().getRewindController();
        assertNotNull(history);
        assertTrue(history.currentFrame() > 1, "actual outgoing history required, never seeded");
        return history;
    }

    private static void awaitHistory(GameplayCaptureSession session, GameMode mode) {
        for (int step = 0; step < ENTRY_LIMIT; step++) {
            var history = SessionManager.getCurrentGameplayMode().getRewindController();
            if (history != null && history.currentFrame() > 1) return;
            assertEquals(mode, session.loop().getCurrentGameMode());
            advance(session);
        }
        fail("ordinary stable world must record history");
    }

    private static void assertHistoryRetired(RewindController outgoing, int outgoingFrame) {
        var incoming = SessionManager.getCurrentGameplayMode().getRewindController();
        assertTrue(incoming == null || incoming != outgoing || incoming.currentFrame() < outgoingFrame,
                "load must retire the outgoing timeline; no cross-load restore");
    }

    private static Sonic3kTitleCardManager card(GameplayCaptureSession session) {
        return assertInstanceOf(Sonic3kTitleCardManager.class, session.loop().getTitleCardProvider());
    }

    private static void advance(GameplayCaptureSession session) {
        session.step(null); // Once, ordinary public neutral input.
        session.renderFrame(); // Original renderer; no readback, provider replacement or publication callback.
    }

    private record Pending(HardwareWorkHandle handle, Object descriptor) { }

    /** No rewind: raw existing maps avoid descriptor(handle)'s restored-state memoization. */
    private static final class FreshSubmissions {
        private final HardwareTimingService timing = GameServices.hardwareTiming();
        private final S3kRuntimeArtCoordinator art = S3kRuntimeArtCoordinator.current();
        private final Map<?, ?> modules;
        private final Map<?, ?> direct;
        private final LinkedHashMap<HardwareWorkHandle, Pending> seen = new LinkedHashMap<>();
        private List<Pending> fifo = List.of();

        private FreshSubmissions() throws ReflectiveOperationException {
            modules = rawDescriptors(S3kKosModuleQueue.class, art.moduleQueue());
            direct = rawDescriptors(S3kKosDecompressionQueue.class, art.directQueue());
        }

        private void read() throws ReflectiveOperationException {
            assertSame(timing, GameServices.hardwareTiming());
            assertSame(art, S3kRuntimeArtCoordinator.current());
            assertSame(modules, rawDescriptors(S3kKosModuleQueue.class, art.moduleQueue()));
            assertSame(direct, rawDescriptors(S3kKosDecompressionQueue.class, art.directQueue()));
            var handles = timing.pendingHandles();
            List<Pending> current = new ArrayList<>();
            for (var handle : handles) {
                Object descriptor;
                if (handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE) {
                    descriptor = assertInstanceOf(S3kKosModuleDescriptor.class, modules.get(handle));
                } else {
                    assertEquals(HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, handle.kind(), "unfiltered pending kind");
                    descriptor = assertInstanceOf(S3kKosDecompressionDescriptor.class, direct.get(handle));
                }
                current.add(new Pending(handle, descriptor));
            }
            fifo = List.copyOf(current);
            for (var item : fifo) seen.putIfAbsent(item.handle(), item);
            assertEquals(handles, timing.pendingHandles(), "metadata must preserve the known pending projection");
            for (var item : fifo) {
                var map = item.handle().kind() == HardwareWorkKind.KOS_MODULE_QUEUE ? modules : direct;
                assertSame(item.descriptor(), map.get(item.handle()), "raw cached descriptor identity unchanged");
            }
        }

        private static Map<?, ?> rawDescriptors(Class<?> owner, Object queue) throws ReflectiveOperationException {
            Field field = owner.getDeclaredField("descriptors");
            field.setAccessible(true); // Read-only field access, never a queue mutation/getter/capture.
            return assertInstanceOf(Map.class, field.get(queue));
        }

        private List<S3kKosModuleDescriptor> moduleParents() {
            return seen.values().stream().filter(item -> item.handle().kind() == HardwareWorkKind.KOS_MODULE_QUEUE)
                    .map(item -> (S3kKosModuleDescriptor) item.descriptor()).toList();
        }

        @Override public String toString() { return "all observed=" + seen.values() + "; actual FIFO=" + fifo; }
    }
}
