package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.runs.RunLevelLoadCause;
import com.openggf.trace.replay.runs.RunLevelLoadTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R40 independent semantic receipt question, source base d84f602d8.
 * Public bonus request/completion inputs; the real GameLoop owns fade/load/title.
 * No trace input, manual tracker publication, cross-load snapshot or ordinal oracle.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBonusReturnLoadReceipt {
    private static final int ORIGIN = 0;
    private static final int HCZ = 1;
    private static final int GUMBALL = 0x13;
    private static final int ENTRY_LIMIT = 600;
    private static final int RETURN_LIMIT = 800;

    @AfterEach
    void cleanup() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
        SessionManager.clear();
    }

    @Test
    void actualGumballReturnPublishesInteriorReturnReceiptAfterNaturalTitleRelease() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootOrigin(session, settings);
            var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
            var origin = currentReceipt(tracker);
            var originObjects = GameServices.level().getObjectManager();
            var originHistory = history();
            int originFrame = originHistory.currentFrame();

            GameServices.level().requestBonusStageEntry(BonusStageType.GUMBALL);
            var entry = awaitBonusEntry(session, tracker, origin, originHistory, originFrame);
            assertNotSame(originObjects, GameServices.level().getObjectManager());
            assertEquals(RunLevelLoadCause.ORDINARY, entry.cause(),
                    "public bonus entry is an ordinary production load, not an interior return");
            var provider = assertInstanceOf(Sonic3kBonusStageCoordinator.class, GameServices.bonusStage());
            assertEquals(BonusStageType.GUMBALL, provider.getActiveType());
            var saved = provider.getSavedState();
            assertNotNull(saved, "this oracle covers a real saved-origin return, not the null fallback");
            assertEquals(origin.identity().progressionZone(), saved.savedZoneAndAct() >>> 8);
            assertEquals(origin.identity().act(), saved.savedZoneAndAct() & 0xFF);
            var interiorObjects = GameServices.level().getObjectManager();
            var interiorHistory = history();
            int interiorFrame = interiorHistory.currentFrame();

            provider.requestExit(); // The same semantic completion stimulus used by actual objects.
            RunLevelLoadTracker.Receipt returned = null;
            boolean sawReturnTitle = false;
            boolean released = false;
            for (int step = 0; step < RETURN_LIMIT; step++) {
                step(session);
                var receipt = currentReceipt(tracker);
                if (receipt.identity().loadGeneration() != entry.identity().loadGeneration()) {
                    if (returned == null) {
                        returned = receipt;
                        assertNewLoad(entry, returned);
                        assertHistoryRetired(interiorHistory, interiorFrame);
                        assertNotSame(interiorObjects, GameServices.level().getObjectManager());
                    }
                    assertSame(returned, receipt, "title progress must not invent another load receipt");
                    sawReturnTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
                    if (sawReturnTitle && settled(session, GameMode.LEVEL)) {
                        released = true;
                        break;
                    }
                }
            }
            assertNotNull(returned, "the ordinary fade must actually publish a new return load");
            assertTrue(sawReturnTitle, "the real origin title must be observed, not inferred from completion");
            assertTrue(released, "natural return title/fade/control release must precede the semantic oracle");
            assertEquals(BonusStageType.NONE, provider.getActiveType());
            assertEquals(origin.identity().progressionZone(), returned.identity().progressionZone());
            assertEquals(origin.identity().romZone(), returned.identity().romZone());
            assertEquals(origin.identity().act(), returned.identity().act());
            assertActualReceipt(tracker, returned);
            assertEquals(RunLevelLoadCause.INTERIOR_RETURN, returned.cause(),
                    "a saved-origin bonus return must publish its actual semantic load cause: " + returned);
        }
    }

    @Test
    void ordinarySubsequentLoadAndBonusEntryKeepOrdinaryReceipts() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootOrigin(session, settings);
            var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
            var origin = currentReceipt(tracker);
            var outgoingObjects = GameServices.level().getObjectManager();
            var outgoingHistory = history();
            int outgoingFrame = outgoingHistory.currentFrame();

            GameServices.level().loadZoneAndAct(HCZ, 0); // A declared ordinary public subsequent load.
            var ordinary = currentReceipt(tracker);
            assertNewLoad(origin, ordinary);
            assertActualReceipt(tracker, ordinary);
            assertEquals(RunLevelLoadCause.ORDINARY, ordinary.cause());
            assertEquals(HCZ, ordinary.identity().progressionZone());
            assertEquals(0, ordinary.identity().act());
            assertNotSame(outgoingObjects, GameServices.level().getObjectManager());
            assertHistoryRetired(outgoingHistory, outgoingFrame);
            awaitLevelTitle(session);
            awaitHistory(session, GameMode.LEVEL);
            assertSame(ordinary, currentReceipt(tracker));

            var hczHistory = history();
            int hczFrame = hczHistory.currentFrame();
            GameServices.level().requestBonusStageEntry(BonusStageType.GUMBALL);
            var entry = awaitBonusEntry(session, tracker, ordinary, hczHistory, hczFrame);
            assertEquals(RunLevelLoadCause.ORDINARY, entry.cause(),
                    "a later ordinary bonus entry must not be blanket-classified as an interior return");
            assertActualReceipt(tracker, entry);
        }
    }

    @Test
    void ordinaryLoadAfterSavedOriginReturnDoesNotInheritItsCause() throws Exception {
        var settings = settings();
        try (var session = new GameplayCaptureSession(settings)) {
            bootOrigin(session, settings);
            var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
            var origin = currentReceipt(tracker);
            var outgoingHistory = history();
            int outgoingFrame = outgoingHistory.currentFrame();
            GameServices.level().requestBonusStageEntry(BonusStageType.GUMBALL);
            var entry = awaitBonusEntry(session, tracker, origin, outgoingHistory, outgoingFrame);
            var provider = assertInstanceOf(Sonic3kBonusStageCoordinator.class, GameServices.bonusStage());
            assertNotNull(provider.getSavedState(), "only the actual saved-origin return owns this cause");

            provider.requestExit(); // One real completion input; the ordinary loop owns the return.
            RunLevelLoadTracker.Receipt returned = null;
            boolean sawReturnTitle = false;
            boolean released = false;
            for (int step = 0; step < RETURN_LIMIT; step++) {
                step(session);
                var receipt = currentReceipt(tracker);
                if (receipt.identity().loadGeneration() != entry.identity().loadGeneration()) {
                    if (returned == null) {
                        returned = receipt;
                        assertNewLoad(entry, returned);
                    }
                    assertSame(returned, receipt);
                    sawReturnTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
                    if (sawReturnTitle && settled(session, GameMode.LEVEL)) {
                        released = true;
                        break;
                    }
                }
            }
            assertNotNull(returned, "the real saved-origin load must publish before the ordinary-load control");
            assertTrue(sawReturnTitle && released, "the return title/fade must release naturally");
            assertEquals(BonusStageType.NONE, provider.getActiveType());
            assertEquals(origin.identity().progressionZone(), returned.identity().progressionZone());
            assertEquals(origin.identity().romZone(), returned.identity().romZone());
            assertEquals(origin.identity().act(), returned.identity().act());
            assertActualReceipt(tracker, returned);
            assertEquals(RunLevelLoadCause.INTERIOR_RETURN, returned.cause());

            GameServices.level().loadZoneAndAct(HCZ, 0); // A distinct ordinary production load.
            var ordinary = currentReceipt(tracker);
            assertNewLoad(returned, ordinary);
            assertActualReceipt(tracker, ordinary);
            assertEquals(HCZ, ordinary.identity().progressionZone());
            assertEquals(0, ordinary.identity().act());
            assertEquals(RunLevelLoadCause.ORDINARY, ordinary.cause(),
                    "the prior interior-return cause must be consumed once, not leak into the next load");
            awaitLevelTitle(session);
            assertSame(ordinary, currentReceipt(tracker));
            assertActualReceipt(tracker, ordinary);
        }
    }

    private static GameplayCaptureSession.Settings settings() {
        return new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
    }

    private static void bootOrigin(GameplayCaptureSession session,
                                   GameplayCaptureSession.Settings settings) throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), ORIGIN, 0, settings);
        awaitLevelTitle(session);
        awaitHistory(session, GameMode.LEVEL);
        var tracker = SessionManager.getCurrentGameplayMode().runLevelLoads();
        var origin = currentReceipt(tracker);
        assertActualReceipt(tracker, origin);
        assertEquals(RunLevelLoadCause.ORDINARY, origin.cause());
        assertEquals(ORIGIN, origin.identity().progressionZone());
        assertEquals(0, origin.identity().act());
    }

    private static void awaitLevelTitle(GameplayCaptureSession session) {
        boolean sawTitle = session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        for (int step = 0; step < ENTRY_LIMIT; step++) {
            if (sawTitle && settled(session, GameMode.LEVEL)
                    && GameServices.hardwareTiming().pendingHandles().isEmpty()) {
                return;
            }
            step(session);
            sawTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        }
        fail("ordinary pending title request must enter TITLE_CARD and naturally release to LEVEL");
    }

    private static RunLevelLoadTracker.Receipt awaitBonusEntry(GameplayCaptureSession session,
            RunLevelLoadTracker tracker, RunLevelLoadTracker.Receipt origin,
            RewindController outgoingHistory, int outgoingFrame) {
        RunLevelLoadTracker.Receipt entry = null;
        boolean sawTitle = false;
        for (int step = 0; step < ENTRY_LIMIT; step++) {
            step(session);
            var receipt = currentReceipt(tracker);
            if (receipt.identity().loadGeneration() != origin.identity().loadGeneration()) {
                if (entry == null) {
                    entry = receipt;
                    assertNewLoad(origin, entry);
                    assertHistoryRetired(outgoingHistory, outgoingFrame);
                }
                assertSame(entry, receipt);
                sawTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
                if (sawTitle && settled(session, GameMode.BONUS_STAGE)) {
                    assertEquals(GUMBALL, entry.identity().progressionZone());
                    assertEquals(0, entry.identity().act());
                    assertEquals(RunLevelLoadCause.ORDINARY, entry.cause());
                    assertActualReceipt(tracker, entry);
                    awaitHistory(session, GameMode.BONUS_STAGE);
                    assertSame(entry, currentReceipt(tracker));
                    return entry;
                }
            }
        }
        throw new AssertionError("real bonus entry/title/fade and receipt must complete naturally");
    }

    private static boolean settled(GameplayCaptureSession session, GameMode mode) {
        var context = SessionManager.getCurrentGameplayMode();
        return session.loop().getCurrentGameMode() == mode
                && !GameServices.level().isTitleCardRequested()
                && !context.getFadeManager().isActive()
                && !context.getFadeManager().hasPendingCompletion()
                && !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                && !GameServices.sprites().getMainPlayable().isControlLocked();
    }

    private static RunLevelLoadTracker.Receipt currentReceipt(RunLevelLoadTracker tracker) {
        assertSame(tracker, SessionManager.getCurrentGameplayMode().runLevelLoads());
        return tracker.latest().orElseThrow(() -> new AssertionError("production must publish the load receipt"));
    }

    private static void assertActualReceipt(RunLevelLoadTracker tracker, RunLevelLoadTracker.Receipt receipt) {
        var level = GameServices.level();
        assertSame(receipt, currentReceipt(tracker));
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
        assertNotNull(history, "ordinary configured history must actually be installed");
        assertTrue(history.currentFrame() > 1, "the outgoing world must have real history to retire");
        return history;
    }

    private static void awaitHistory(GameplayCaptureSession session, GameMode mode) {
        for (int step = 0; step < ENTRY_LIMIT; step++) {
            var history = SessionManager.getCurrentGameplayMode().getRewindController();
            if (history != null && history.currentFrame() > 1) {
                return;
            }
            assertEquals(mode, session.loop().getCurrentGameMode());
            step(session);
        }
        fail("the stable world must record real history without a manual reset or snapshot seed");
    }

    private static void assertHistoryRetired(RewindController outgoing, int outgoingFrame) {
        var incoming = SessionManager.getCurrentGameplayMode().getRewindController();
        assertTrue(incoming == null || incoming != outgoing || incoming.currentFrame() < outgoingFrame,
                "a full load retires the previous timeline instead of restoring an outgoing receipt/history");
    }

    private static void step(GameplayCaptureSession session) {
        session.step(null); // Exactly one ordinary step; null is the public neutral-input contract.
        session.renderFrame(); // Existing normal renderer, no screenshot/readback or replacement callback.
    }
}
