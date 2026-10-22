package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R87 independent ROM ring-status question, source HEAD54f3de67.
 * Native044 sonic3k.asm:61959..61966 keeps status at starpost bonus entry;
 * Clear_SpriteRingMem:37983..38013 finishes consumption, sub_EB1A:18599..18616
 * skips the kept table wipe. Gumball:127803..127814 returns without clearing keep;
 * ordinary death:24624 clears it. HUD ring totals are a separate state contract.
 * Real input acquisition and public bonus requests; no trace, ring writes, pose
 * writes, capture/restore, private reads, synthetic placements or queue controls.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBonusReturnRingRetention {
    private static final int ENTRY_LIMIT = 600;
    private static final int RETURN_LIMIT = 800;
    private static final int ACQUIRE_LIMIT = 600;
    // Spatial exclusion, not a wait: exceeds both normal touch and +/-64 attraction.
    private static final int CLEARANCE = 0x80;

    @AfterEach
    void cleanup() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
        SessionManager.clear();
    }

    @Test
    void publicGumballReturnPreservesNaturallyCollectedOriginRing() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            var acquired = bootAndAcquire(session, settings);
            var origin = acquired.world();
            var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
            GameServices.level().requestBonusStageEntry(BonusStageType.GUMBALL); // Once, declared ingress.
            var interior = awaitNewWorld(session, tracker, origin, GameMode.BONUS_STAGE,
                    0x13, RunLevelLoadCause.ORDINARY, null, ENTRY_LIMIT);
            var provider = assertInstanceOf(Sonic3kBonusStageCoordinator.class, GameServices.bonusStage());
            assertEquals(BonusStageType.GUMBALL, provider.getActiveType());
            var saved = provider.getSavedState();
            assertNotNull(saved, "actual saved origin required, not the null-state fallback");
            assertEquals(0, saved.savedZoneAndAct());
            assertEquals(0, saved.savedApparentZoneAndAct());
            assertTrue(clearOf(acquired.ring(), saved.playerX(), saved.playerY()),
                    "saved return position must not recollect the selected ring during setup");

            provider.requestExit(); // Once; ordinary GameLoop owns fade, load and title.
            var returned = awaitNewWorld(session, tracker, interior, GameMode.LEVEL,
                    0, RunLevelLoadCause.INTERIOR_RETURN, acquired.ring(), RETURN_LIMIT);
            assertEquals(BonusStageType.NONE, provider.getActiveType());
            assertEquals(origin.receipt().identity().progressionZone(), returned.receipt().identity().progressionZone());
            assertEquals(origin.receipt().identity().romZone(), returned.receipt().identity().romZone());
            assertEquals(origin.receipt().identity().act(), returned.receipt().identity().act());
            assertNotSame(origin.level(), returned.level());
            assertNotSame(origin.rings(), returned.rings());
            assertNotSame(origin.objects(), returned.objects());
            var canonical = uniquePlacement(returned.level(), acquired.ring());
            assertEquals(acquired.ring().placementId(), canonical.placementId());
            // Final semantic old-red only after all public return/title/history gates.
            assertTrue(returned.rings().isRingCollected(canonical.x(), canonical.y()),
                    "native kept Ring_status_table must retain the naturally acquired origin placement: " + acquired.ring());
        }
    }

    @Test
    void ordinaryOriginReloadClearsNaturallyCollectedRing() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            var acquired = bootAndAcquire(session, settings);
            var origin = acquired.world();
            var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
            assertFalse(GameServices.level().getTransitions().hasBigRingReturn());
            assertFalse(GameServices.level().isBonusStageReturn());
            GameServices.level().loadZoneAndAct(0, 0); // Independent ordinary, non-kept public reload.
            var ordinary = awaitNewWorld(session, tracker, origin, GameMode.LEVEL,
                    0, RunLevelLoadCause.ORDINARY, acquired.ring(), ENTRY_LIMIT);
            assertFalse(GameServices.level().getTransitions().hasBigRingReturn());
            assertFalse(GameServices.level().isBonusStageReturn());
            assertEquals(origin.receipt().identity().romZone(), ordinary.receipt().identity().romZone());
            var canonical = uniquePlacement(ordinary.level(), acquired.ring());
            assertEquals(acquired.ring().placementId(), canonical.placementId());
            assertFalse(ordinary.rings().isRingCollected(canonical.x(), canonical.y()),
                    "an ordinary non-kept reload clears collection, independently of the HUD ring total");
        }
    }

    private record World(RunLevelLoadTracker.Receipt receipt, Level level, RingManager rings,
                         ObjectManager objects, RewindController history, int historyFrame) { }
    private record Acquired(World world, RingSpawn ring) { }

    private static GameplayCaptureSession.Settings settings() {
        // Actual default AIZ Knuckles start. All pose/shield/ring/timer/clock overrides absent.
        return new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
    }

    private static Acquired bootAndAcquire(GameplayCaptureSession session,
                                           GameplayCaptureSession.Settings settings) throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
        var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
        boolean sawTitle = session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        boolean ready = false;
        for (int step = 0; step < ENTRY_LIMIT; step++) {
            if (sawTitle && released(session, GameMode.LEVEL) && historyReady()
                    && GameServices.hardwareTiming().pendingHandles().isEmpty()) {
                ready = true;
                break;
            }
            advance(session, null);
            sawTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        }
        assertTrue(ready, "origin title/request/Nemesis/control release and real history must complete naturally");
        assertEquals(320, GameServices.camera().getWidth());
        var origin = world(tracker);
        assertEquals(RunLevelLoadCause.ORDINARY, origin.receipt().cause());
        assertEquals(0, origin.receipt().identity().progressionZone());
        assertEquals(0, origin.receipt().identity().act());
        assertEquals(0, GameServices.level().getApparentAct());
        assertFalse(GameServices.level().getTransitions().hasBigRingReturn());
        int startX = player().getCentreX() & 0xFFFF;
        int startY = player().getCentreY() & 0xFFFF;

        List<RingSpawn> layout = origin.level().getRings(); // Already-decoded original ROM placements.
        assertFalse(layout.isEmpty(), "actual ROM placement table required");
        var counts = new HashMap<Long, Integer>();
        for (var ring : layout) counts.merge(key(ring), 1, Integer::sum);
        var candidates = layout.stream().filter(ring -> counts.get(key(ring)) == 1).toList();
        assertFalse(candidates.isEmpty(), "at least one uniquely addressable canonical ROM placement required");
        boolean[] before = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) before[i] = collected(origin, candidates.get(i));
        for (int step = 0; step < ACQUIRE_LIMIT; step++) {
            sameOrigin(session, tracker, origin);
            int ringsBefore = player().getRingCount();
            advance(session, right(step));
            sameOrigin(session, tracker, origin);
            var acquired = new ArrayList<RingSpawn>();
            for (int i = 0; i < candidates.size(); i++) {
                boolean after = collected(origin, candidates.get(i));
                assertFalse(before[i] && !after, "stable origin must not reset a collected bit");
                if (!before[i] && after) acquired.add(candidates.get(i));
                before[i] = after;
            }
            int delta = player().getRingCount() - ringsBefore;
            assertEquals(acquired.size(), delta,
                    "ordinary placed-ring transitions must explain the actual acquisition count; no extra owner assumed");
            for (var ring : acquired) {
                // A ring away from both reload start and saved position prevents reacquisition masking loss.
                if (!clearOf(ring, startX, startY)) continue;
                assertTrue(delta > 0);
                assertSame(ring, uniquePlacement(origin.level(), ring));
                assertTrue(collected(origin, ring), "actual observed false->true before any stage entry");
                for (int leave = 0; leave < ACQUIRE_LIMIT && !clearOfPlayer(ring); leave++) {
                    advance(session, right(leave));
                    sameOrigin(session, tracker, origin);
                    assertTrue(collected(origin, ring), "naturally acquired source bit remains set before entry");
                }
                assertTrue(clearOfPlayer(ring), "ordinary movement must leave the selected pickup neighbourhood");
                return new Acquired(world(tracker), ring);
            }
        }
        throw new AssertionError("bounded ordinary right input did not naturally acquire a unique, separated origin ring");
    }

    private static World awaitNewWorld(GameplayCaptureSession session, RunLevelLoadTracker tracker,
                                      World before, GameMode mode, int zone, RunLevelLoadCause cause,
                                      RingSpawn excludedPickup, int limit) {
        RunLevelLoadTracker.Receipt loaded = null;
        boolean sawTitle = false;
        for (int step = 0; step < limit; step++) {
            advance(session, null);
            var current = receipt(tracker);
            if (current.identity().loadGeneration() == before.receipt().identity().loadGeneration()) continue;
            if (loaded == null) {
                loaded = current;
                assertTrue(current.identity().loadGeneration() > before.receipt().identity().loadGeneration());
                assertNotSame(before.receipt(), current);
                assertNotSame(before.level(), GameServices.level().getCurrentLevel());
                assertNotSame(before.rings(), GameServices.level().getRingManager());
                assertNotSame(before.objects(), GameServices.level().getObjectManager());
                var incoming = SessionManager.getCurrentGameplayMode().getRewindController();
                assertTrue(incoming == null || incoming != before.history() || incoming.currentFrame() < before.historyFrame(),
                        "ordinary new load must retire outgoing history, never restore its snapshot/receipt");
            }
            assertSame(loaded, current, "title processing must not create another physical load");
            actualReceipt(tracker, current);
            assertEquals(cause, current.cause());
            assertEquals(zone, current.identity().progressionZone());
            assertEquals(0, current.identity().act());
            if (excludedPickup != null) {
                assertTrue(clearOfPlayer(excludedPickup), "new-world setup/title must not reacquire the selected placement");
            }
            sawTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
            if (sawTitle && released(session, mode) && historyReady()) return world(tracker);
        }
        throw new AssertionError("actual new load/title/Nemesis/history/natural release prerequisites were not reached");
    }

    private static void sameOrigin(GameplayCaptureSession session, RunLevelLoadTracker tracker, World origin) {
        assertTrue(released(session, GameMode.LEVEL), "acquisition uses ordinary unlocked gameplay only");
        assertSame(origin.receipt(), receipt(tracker));
        actualReceipt(tracker, origin.receipt());
        assertSame(origin.level(), GameServices.level().getCurrentLevel());
        assertSame(origin.rings(), GameServices.level().getRingManager());
        assertSame(origin.objects(), GameServices.level().getObjectManager());
        assertNull(GameServices.level().getTransitions().peekBonusStageRequest());
        assertFalse(player().getDead());
        assertNotEquals(ShieldType.LIGHTNING, player().getShieldType(), "no attraction marking instead of actual acquisition");
    }

    private static boolean collected(World origin, RingSpawn ring) {
        return origin.rings().isRingCollected(ring.x(), ring.y()); // Pure coordinate table/BitSet read.
    }

    private static RingSpawn uniquePlacement(Level level, RingSpawn original) {
        var matches = level.getRings().stream()
                .filter(ring -> ring.x() == original.x() && ring.y() == original.y()).toList();
        assertEquals(1, matches.size(), "selected coordinate must exist uniquely in each actual ROM placement table");
        return matches.getFirst();
    }

    private static long key(RingSpawn ring) {
        return ((long) ring.x() << 32) | (ring.y() & 0xFFFF_FFFFL);
    }

    private static boolean clearOf(RingSpawn ring, int x, int y) {
        return Math.abs((x & 0xFFFF) - ring.x()) > CLEARANCE
                || Math.abs((y & 0xFFFF) - ring.y()) > CLEARANCE;
    }

    private static boolean clearOfPlayer(RingSpawn ring) {
        assertTrue(player().getYRadius() > 0 && player().getYRadius() <= 0x40);
        return clearOf(ring, player().getCentreX(), player().getCentreY());
    }

    private static Knuckles player() {
        return assertInstanceOf(Knuckles.class, GameServices.sprites().getMainPlayable());
    }

    private static boolean released(GameplayCaptureSession session, GameMode mode) {
        var context = SessionManager.getCurrentGameplayMode();
        var plc = com.openggf.game.session.WorldPlcLifecycleAccess.require(GameServices.level().getWorldSession(), Sonic3kLevelTitlePlcService.class);
        assertNotNull(plc, "actual production title Nemesis owner required");
        return session.loop().getCurrentGameMode() == mode && !GameServices.level().isTitleCardRequested()
                && !context.getFadeManager().isActive() && !context.getFadeManager().hasPendingCompletion()
                && !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                && !player().isControlLocked() && !plc.isBusy();
    }

    private static boolean historyReady() {
        var history = SessionManager.getCurrentGameplayMode().getRewindController();
        return history != null && history.currentFrame() > 1;
    }

    private static World world(RunLevelLoadTracker tracker) {
        var current = receipt(tracker);
        actualReceipt(tracker, current);
        assertTrue(historyReady());
        var history = SessionManager.getCurrentGameplayMode().getRewindController();
        var level = GameServices.level();
        assertNotNull(level.getCurrentLevel());
        assertNotNull(level.getRingManager());
        assertNotNull(level.getObjectManager());
        return new World(current, level.getCurrentLevel(), level.getRingManager(), level.getObjectManager(),
                history, history.currentFrame());
    }

    private static RunLevelLoadTracker.Receipt receipt(RunLevelLoadTracker tracker) {
        assertSame(tracker, SessionManager.getCurrentGameplayMode().runLevelLoads());
        return tracker.latest().orElseThrow(() -> new AssertionError("actual production load receipt required"));
    }

    private static void actualReceipt(RunLevelLoadTracker tracker, RunLevelLoadTracker.Receipt receipt) {
        var level = GameServices.level();
        assertSame(receipt, receipt(tracker));
        assertEquals(level.getCompletedProductionLoadGeneration(), tracker.generation());
        assertEquals(tracker.generation(), receipt.identity().loadGeneration());
        assertEquals(level.getCurrentZone(), receipt.identity().progressionZone());
        assertEquals(level.getRomZoneId(), receipt.identity().romZone());
        assertEquals(level.getCurrentAct(), receipt.identity().act());
    }

    private static Bk2FrameInput right(int index) {
        return new Bk2FrameInput(index, AbstractPlayableSprite.INPUT_RIGHT, 0, false, "declared ordinary right");
    }

    private static void advance(GameplayCaptureSession session, Bk2FrameInput input) {
        session.step(input); // Exactly one ordinary step, null means neutral; no recording/trace input.
        session.renderFrame(); // Ordinary render, no readback, file or replacement callback.
    }
}
