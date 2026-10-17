package com.openggf.tools;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kKosDecompressionQueue;
import com.openggf.game.sonic3k.resources.S3kKosModuleDescriptor;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R31 independent production-entry oracle, source base 8b1e965.
 * Native044 Level/loc_6310 calls LoadLevelLoadBlock after the title/Nemesis gates.
 * Gumball's equal primary/secondary ROM pointers produce ONE terrain parent at zero.
 * No recorded ordinal/fingerprint, timing capture for observation, or gameplay seed.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kGumballTerrainSubmission {
    private static final int GUMBALL = 0x13;
    private static final int HCZ = 1;
    private static final int ENTRY_LIMIT = 600;

    @AfterEach
    void cleanup() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
        SessionManager.clear();
    }

    @Test
    void ordinaryKnucklesGumballEntrySubmitsNativeTerrainBeforeBonusRelease() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootAndSettleOrigin(session, settings);
            Terrain nativeTerrain = terrain(GUMBALL, 0);
            assertEquals(nativeTerrain.primary(), nativeTerrain.secondary(),
                    "native Gumball LevelLoadBlock repeats the same archive; no duplicate enqueue");
            assertEquals(Sonic3kConstants.GUMBALL_8X8_KOSM_ADDR, nativeTerrain.primary());
            assertEquals(1, nativeTerrain.descriptors().size());
            var observed = new FreshSubmissions();

            GameServices.level().requestBonusStageEntry(BonusStageType.GUMBALL);
            boolean sawBonusTitle = false;
            boolean sawPendingTitleBatch = false;
            boolean reachedTerrainOrRelease = false;
            for (int step = 0; step < ENTRY_LIMIT; step++) {
                session.step(null); // Existing ordinary GameLoop driver, exactly once.
                observed.readFreshPending();
                if (session.loop().getCurrentGameMode() == GameMode.TITLE_CARD
                        && GameServices.level().getCurrentZone() == GUMBALL) {
                    sawBonusTitle = true;
                    assertDestination(GUMBALL);
                    var title = card(session).capture();
                    if (title.artLoading()) {
                        assertEquals(4, title.artHandles().size(), "actual native bonus title batch");
                        sawPendingTitleBatch = true;
                    }
                    assertInstanceOf(Sonic3kBonusStageCoordinator.class, GameServices.bonusStage());
                    if (observed.hasTerrain(nativeTerrain)) {
                        assertGatesOpen(session);
                        reachedTerrainOrRelease = true;
                        break;
                    }
                }
                if (session.loop().getCurrentGameMode() == GameMode.BONUS_STAGE) {
                    // exitTitleCard retains these actual owner flags; this is an
                    // outside-step gate check, not a submission callback witness.
                    assertGatesOpen(session);
                    reachedTerrainOrRelease = true;
                    break;
                }
            }
            assertTrue(sawBonusTitle, "ordinary public entry must reach its real bonus title owner");
            assertTrue(sawPendingTitleBatch, "the real pending title batch must have been observed");
            assertTrue(reachedTerrainOrRelease,
                    "actual terrain or natural bonus release must be reached before the descriptor oracle");
            assertDestination(GUMBALL);
            // The complete module-parent order is retained; direct entries remain in the FIFO view.
            assertEquals(titleAndTerrain(Sonic3kConstants.ART_KOSM_BONUS_TITLE_CARD_ADDR, nativeTerrain),
                    observed.moduleParents(),
                    () -> "LoadLevelLoadBlock must submit its ROM terrain before bonus release: "
                            + observed + "; actual title=" + card(session).capture());
            assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode(),
                    "the real pending terrain parent must be observed before released bonus gameplay");
        }
    }

    @Test
    void normalFreshHczEntryRetainsItsNativeTerrainBatch() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootAndSettleOrigin(session, settings);
            Terrain nativeTerrain = terrain(HCZ, 0);
            var observed = new FreshSubmissions();

            // Existing production whole-level entry, not a handoff/index or queue write.
            GameServices.level().loadZoneAndActWithTitleCard(HCZ, 0);
            observed.readFreshPending();
            boolean sawTitle = false;
            for (int step = 0; step < ENTRY_LIMIT; step++) {
                session.step(null);
                observed.readFreshPending();
                if (session.loop().getCurrentGameMode() == GameMode.TITLE_CARD) {
                    sawTitle = true;
                    assertDestination(HCZ);
                    if (observed.hasTerrain(nativeTerrain)) {
                        assertGatesOpen(session);
                        break;
                    }
                } else if (sawTitle && session.loop().getCurrentGameMode() == GameMode.LEVEL) break;
            }
            assertTrue(sawTitle, "normal fresh control must actually present its production title");
            assertEquals(titleAndTerrain(Sonic3kConstants.TITLE_CARD_ZONE_ART_ADDRS[HCZ], nativeTerrain),
                    observed.moduleParents(), () -> "normal native terrain control: " + observed);
            assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode());
        }
    }

    @Test
    void pendingBonusTitleResourcePartitionRestoresAndForwardsTwiceWithoutCrossingLoad() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootAndSettleOrigin(session, settings);
            GameServices.level().requestBonusStageEntry(BonusStageType.GUMBALL);
            for (int step = 0; step < ENTRY_LIMIT
                    && !(session.loop().getCurrentGameMode() == GameMode.TITLE_CARD
                    && GameServices.level().getCurrentZone() == GUMBALL); step++) session.step(null);
            assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode());
            assertDestination(GUMBALL);
            var gameplay = SessionManager.getCurrentGameplayMode();
            var level = GameServices.level().getCurrentLevel();
            var objects = GameServices.level().getObjectManager();
            var title = card(session);
            assertTrue(title.capture().artLoading());
            assertEquals(4, title.capture().artHandles().size(), "real native bonus title batch");
            var registry = gameplay.getRewindRegistry();

            // Legitimate registered rewind capture, not a timing-capture observation shortcut.
            CompositeSnapshot pending = registry.capture();
            var retained = assertInstanceOf(Sonic3kTitleCardManager.Snapshot.class,
                    pending.get(Sonic3kTitleCardManager.REWIND_KEY));
            assertEquals(title.capture().artHandles(), retained.artHandles());
            for (int step = 0; step < 2; step++) session.step(null);
            assertSameTitleScene(session, gameplay, level, objects);
            assertTrue(title.capture().artLoading(), "this control stays inside the pending batch");
            CompositeSnapshot forward = registry.capture();
            assertFalse(RewindSnapshotDiff.keyEquals(HardwareTimingService.REWIND_KEY,
                    pending.get(HardwareTimingService.REWIND_KEY), forward.get(HardwareTimingService.REWIND_KEY)),
                    "ordinary forward steps must actually advance resource state");

            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(pending);
                assertSameTitleScene(session, gameplay, level, objects);
                assertPartition(pending, registry.capture(), "restore " + cycle);
                for (int step = 0; step < 2; step++) session.step(null);
                assertSameTitleScene(session, gameplay, level, objects);
                assertPartition(forward, registry.capture(), "forward " + cycle);
            }
            // No restore across entry/load or mode release; complete five-key partition only.
        }
    }

    @Test
    void ordinaryPachinkoEntrySubmitsItsNativeTerrainBeforeBonusRelease() throws Exception {
        assertOtherBonusTerrainEntry(BonusStageType.GLOWING_SPHERE, 0x14);
    }

    @Test
    void ordinarySlotEntrySubmitsItsNativeTerrainBeforeBonusRelease() throws Exception {
        assertOtherBonusTerrainEntry(BonusStageType.SLOT_MACHINE, 0x15);
    }

    private static void assertOtherBonusTerrainEntry(BonusStageType type, int zone) throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootAndSettleOrigin(session, settings);
            Terrain nativeTerrain = terrain(zone, 0);
            assertEquals(nativeTerrain.primary(), nativeTerrain.secondary(),
                    "native bonus LevelLoadBlock deduplicates equal terrain sources");
            assertEquals(1, nativeTerrain.descriptors().size());
            var observed = new FreshSubmissions();
            GameServices.level().requestBonusStageEntry(type);
            boolean sawTitle = false;
            boolean sawPendingBatch = false;
            boolean reachedTerrainOrRelease = false;
            for (int step = 0; step < ENTRY_LIMIT; step++) {
                session.step(null);
                observed.readFreshPending();
                if (session.loop().getCurrentGameMode() == GameMode.TITLE_CARD
                        && GameServices.level().getCurrentZone() == zone) {
                    sawTitle = true;
                    assertDestination(zone);
                    var provider = assertInstanceOf(Sonic3kBonusStageCoordinator.class, GameServices.bonusStage());
                    assertEquals(type, provider.getActiveType());
                    var title = card(session).capture();
                    assertTrue(title.bonusMode(), "the actual bonus title owns this entry");
                    if (title.artLoading()) {
                        assertEquals(4, title.artHandles().size(), "native bonus title archives");
                        sawPendingBatch = true;
                    }
                    if (observed.hasTerrain(nativeTerrain)) {
                        assertGatesOpen(session);
                        reachedTerrainOrRelease = true;
                        break;
                    }
                }
                if (session.loop().getCurrentGameMode() == GameMode.BONUS_STAGE) {
                    assertGatesOpen(session);
                    reachedTerrainOrRelease = true;
                    break;
                }
            }
            assertTrue(sawTitle && sawPendingBatch, "actual bonus title/pending archives must be observed");
            assertTrue(reachedTerrainOrRelease, "natural native gates must be reached before the oracle");
            assertEquals(titleAndTerrain(Sonic3kConstants.ART_KOSM_BONUS_TITLE_CARD_ADDR, nativeTerrain),
                    observed.moduleParents(), () -> type + " native terrain control: " + observed);
            assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode(),
                    "native terrain must be pending before bonus gameplay release");
            assertDestination(zone);
        }
    }

    private static GameplayCaptureSession.Settings settings() {
        return new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
    }

    private static void bootAndSettleOrigin(GameplayCaptureSession session,
                                            GameplayCaptureSession.Settings settings) throws Exception {
        session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
        // Boot can expose LEVEL/complete/empty before the ordinary loop consumes
        // its initial title request. Observe the real origin title and its return.
        boolean sawOriginTitle = session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        for (int step = 0; step < ENTRY_LIMIT && !(sawOriginTitle
                && session.loop().getCurrentGameMode() == GameMode.LEVEL
                && !GameServices.level().isTitleCardRequested()
                && card(session).isComplete() && GameServices.hardwareTiming().pendingHandles().isEmpty()); step++) {
            session.step(null);
            sawOriginTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        }
        assertTrue(sawOriginTitle, "ordinary origin TITLE_CARD phase must actually be observed");
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
        assertFalse(GameServices.level().isTitleCardRequested(),
                "the original loop must consume the origin title request before the bonus request");
        assertTrue(card(session).isComplete(), "ordinary origin title must retire naturally");
        assertTrue(GameServices.hardwareTiming().pendingHandles().isEmpty(),
                "entry prerequisite is actual quiescence, never a queue reset");
        assertInstanceOf(Knuckles.class, GameServices.sprites().getMainPlayable());
    }

    private static Sonic3kTitleCardManager card(GameplayCaptureSession session) {
        return assertInstanceOf(Sonic3kTitleCardManager.class, session.loop().getTitleCardProvider());
    }

    private static void assertDestination(int zone) {
        assertEquals(zone, GameServices.level().getCurrentZone());
        assertEquals(0, GameServices.level().getCurrentAct());
        assertEquals(0, GameServices.level().getApparentAct());
        assertInstanceOf(Knuckles.class, GameServices.sprites().getMainPlayable());
    }

    private static void assertGatesOpen(GameplayCaptureSession session) {
        var title = card(session).capture();
        assertFalse(title.artLoading(), "title archives must drain before native terrain");
        assertTrue(title.freshLevelTitleReady(), "actual title movement gate must have opened");
        assertTrue(title.freshLevelTerrainStarted());
        var plc = GameServices.module().getGameService(Sonic3kLevelTitlePlcService.class);
        assertNotNull(plc);
        assertFalse(plc.isBusy(), "native Nemesis gate must also have opened");
    }

    private static Terrain terrain(int zone, int act) throws IOException {
        var rom = TestEnvironment.currentRom();
        int entry = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + (zone * 2 + act) * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;
        int primary = rom.read32BitAddr(entry) & 0xFFFFFF;
        int secondary = rom.read32BitAddr(entry + 4) & 0xFFFFFF;
        List<S3kKosModuleDescriptor> descriptors = new ArrayList<>();
        descriptors.add(archive(primary, 0));
        // Native LoadLevelLoadBlock cmp.l d0,d7 skips an identical secondary pointer.
        if (secondary != primary) descriptors.add(archive(secondary, descriptors.getFirst().destinationLength()));
        return new Terrain(primary, secondary, List.copyOf(descriptors));
    }

    private static S3kKosModuleDescriptor archive(int source, int destinationBytes) throws IOException {
        var rom = TestEnvironment.currentRom();
        byte[] image = rom.readBytes(0, Math.toIntExact(rom.getSize()));
        var shape = KosinskiReader.inspectModuled(image, source);
        return new S3kKosModuleDescriptor(source, shape.compressedLength(), destinationBytes,
                shape.decompressedLength(), shape.moduleCount());
    }

    private static List<S3kKosModuleDescriptor> titleAndTerrain(int graphic, Terrain terrain) throws IOException {
        List<S3kKosModuleDescriptor> expected = new ArrayList<>();
        expected.add(archive(Sonic3kConstants.ART_KOSM_TITLE_CARD_RED_ACT_ADDR, 0xA000));
        expected.add(archive(Sonic3kConstants.ART_KOSM_TITLE_CARD_S3K_ZONE_ADDR, 0xA200));
        expected.add(archive(Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM1_ADDR, 0xA7A0));
        expected.add(archive(graphic, 0xA9A0));
        expected.addAll(terrain.descriptors());
        return List.copyOf(expected);
    }

    private static void assertSameTitleScene(GameplayCaptureSession session, Object gameplay,
                                             Object level, Object objects) {
        assertSame(gameplay, SessionManager.getCurrentGameplayMode());
        assertSame(level, GameServices.level().getCurrentLevel());
        assertSame(objects, GameServices.level().getObjectManager());
        assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode());
    }

    private static void assertPartition(CompositeSnapshot expected, CompositeSnapshot actual, String phase) {
        var provider = assertInstanceOf(Sonic3kObjectArtProvider.class, GameServices.module().getObjectArtProvider());
        for (String key : List.of(HardwareTimingService.REWIND_KEY, S3kKosDecompressionQueue.REWIND_KEY,
                S3kRuntimeArtCoordinator.REWIND_KEY, Sonic3kTitleCardManager.REWIND_KEY, provider.key())) {
            assertNotNull(expected.get(key), phase + " expected " + key);
            assertNotNull(actual.get(key), phase + " actual " + key);
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> phase + " " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    private record Terrain(int primary, int secondary, List<S3kKosModuleDescriptor> descriptors) { }
    private record Pending(HardwareWorkHandle handle, Object descriptor) { }

    /** Fresh live submissions only: submitPrepared caches descriptors before returning.
     * Claims remove both the descriptor and the unclaimed pending handle. The module
     * getter can rebind after rewind; this helper is NEVER used in the restore control.
     * Keep the whole FIFO (including direct jobs) and only project module parents for
     * the independent module-order oracle. No prepare/claim/payload/capture operation.
     */
    private static final class FreshSubmissions {
        private final HardwareTimingService timing = GameServices.hardwareTiming();
        private final S3kRuntimeArtCoordinator art = S3kRuntimeArtCoordinator.current();
        private final LinkedHashMap<HardwareWorkHandle, Pending> seen = new LinkedHashMap<>();
        private List<Pending> fifo = List.of();

        private void readFreshPending() {
            assertSame(timing, GameServices.hardwareTiming());
            assertSame(art, S3kRuntimeArtCoordinator.current());
            var handles = timing.pendingHandles();
            List<Pending> current = new ArrayList<>();
            for (var handle : handles) {
                assertTrue(handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE
                        || handle.kind() == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE,
                        "unexpected actual pending kind " + handle.kind());
                Object descriptor = handle.kind() == HardwareWorkKind.KOS_MODULE_QUEUE
                        ? art.moduleQueue().descriptor(handle) : art.directQueue().descriptor(handle);
                current.add(new Pending(handle, descriptor));
            }
            assertEquals(handles, timing.pendingHandles(), "metadata reads must preserve the known pending projection");
            fifo = List.copyOf(current);
            for (var item : fifo) seen.putIfAbsent(item.handle(), item);
        }

        private List<S3kKosModuleDescriptor> moduleParents() {
            return seen.values().stream().filter(item -> item.handle().kind() == HardwareWorkKind.KOS_MODULE_QUEUE)
                    .map(item -> (S3kKosModuleDescriptor) item.descriptor()).toList();
        }

        private boolean hasTerrain(Terrain expected) {
            return moduleParents().containsAll(expected.descriptors());
        }

        @Override public String toString() { return "all observed=" + seen.values() + "; actual FIFO=" + fifo; }
    }
}
