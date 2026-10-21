package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.dataselect.AbstractDataSelectProvider;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.save.SavePaths;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.graphics.FadeManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every S3K {@code Level:} entry installs {@code Obj_TitleCard} at loc_62B6, waits in
 * loc_62CC while the card slides in, loads the level, and only then arms the card's
 * LevelLoop hold at loc_64DC: {@code Palette_fade_timer} and the card's {@code $2E}
 * both become {@code $16} (docs/skdisasm/sonic3k.asm:7762-7910).
 * {@code Obj_TitleCardWait2} (62249) counts {@code $2E} down on LevelLoop frames while
 * the player already has control, and the card starts leaving when it reaches zero.
 * A death's {@code Restart_level_flag}, a data-select launch, a bonus-stage entry and
 * the bonus return all run that one routine.
 *
 * <p>Native Knuckles HPZ entry (campaign-visual {@code hpz-entry} RAM dumps,
 * inputs 411495-411529): {@code Level_frame_counter} 0 holds {@code $2E = 22}, counter
 * k holds {@code 22 - k}, the hold ends at counter 22 and the children leave from 23.
 * Before LevelLoop the counter stays 0 while {@code V_int_run_count} advances.
 *
 * <p>Each route asserts the locked title loop (counter 0, the clock advancing, the
 * player locked), the release into gameplay with the card still holding 22 frames,
 * the hold counting with the counter while the player is free, and the exit.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kTitleCardHoldRoutes {
    private static final int HOLD = 0x16;
    @TempDir Path saves;

    @AfterEach
    void tearDown() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
        SessionManager.clear();
    }

    @Test
    void directBootWithTheCardReleasesLevelLoopIntoTheHold() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 22, 1, settings);
            List<Row> rows = new ArrayList<>();
            rows.add(row(session.loop()));
            for (int step = 0; step < 300; step++) {
                session.step(null);
                rows.add(row(session.loop()));
            }
            // HPZ's own entry event holds Ctrl_1_locked for Knuckles' walk-in (native
            // hpz-entry: set from counter 1), so only the title lock is checked here.
            assertNativeHold(rows, GameMode.LEVEL, false);
        }
    }

    @Test
    void rewindInsideTheHoldRestoresTheCardAndReplaysForward() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 22, 1, settings);
            boolean seen = false;
            for (int step = 0; step < 300 && !(seen && session.loop().getCurrentGameMode() == GameMode.LEVEL
                    && GameServices.level().getFrameCounter() == 5); step++) {
                session.step(null);
                seen |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
            }
            var card = (Sonic3kTitleCardManager) session.loop().getTitleCardProvider();
            assertEquals("DISPLAY/5", card.getStateName() + "/" + card.getStateTimer(), "counter 5 of the hold");
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var saved = registry.capture();
            // Forward through the end of the hold into the exit, stopping at counter 34:
            // the native card leaves from counter 23 and is deleted at 35 (hpz-entry).
            int forwardSteps = 29;
            for (int n = 0; n < forwardSteps; n++) session.step(null);
            assertEquals(34, GameServices.level().getFrameCounter());
            assertEquals("EXIT", card.getStateName(), "the card is leaving at the end of the forward run");
            var forward = registry.capture();

            registry.restore(saved);
            assertEquals("DISPLAY/5", card.getStateName() + "/" + card.getStateTimer(),
                    "restore returns the card to its counter-5 hold");
            for (int n = 0; n < forwardSteps; n++) session.step(null);
            var replayed = registry.capture();
            assertEquals(forward.entries().keySet(), replayed.entries().keySet());
            for (var key : forward.entries().keySet()) {
                var difference = RewindSnapshotDiff.diffKey(key, forward.get(key), replayed.get(key));
                assertTrue(difference.isEmpty(), "forward replay " + key + ": " + difference);
            }
        }
    }

    @Test
    void dataSelectNewGameReleasesLevelLoopIntoTheHold() throws Exception {
        String previous = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, true, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.startAtTitle();
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            var menu = (AbstractDataSelectProvider) session.loop().getDataSelectProvider();
            menu.initialize();
            session.loop().setGameMode(GameMode.DATA_SELECT);
            List<Row> rows = new ArrayList<>();
            try (var input = MutatorGameplayCaptureTool.PresentationInput.install(session.loop())) {
                for (int frame = 0; frame < 60; frame++) session.stepPresentation(null, input);
                session.stepPresentation(press(AbstractPlayableSprite.INPUT_DOWN, 0), input);
                session.stepPresentation(null, input);
                session.stepPresentation(press(0, InputActionMasks.ACTION_A), input);
                for (int step = 0; step < 400; step++) {
                    session.stepPresentation(null, input);
                    rows.add(row(session.loop()));
                }
            }
            assertNativeHold(rows, GameMode.LEVEL);
        } finally {
            if (previous == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
            else System.setProperty(SavePaths.ROOT_PROPERTY, previous);
        }
    }

    @Test
    void deathRestartReleasesLevelLoopIntoTheHoldWithoutAHostReveal() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        HeadlessTestFixture.builder().withZoneAndAct(9, 0).build();
        GameLoop loop = new GameLoop(new InputHandler());
        Method level = GameLoop.class.getDeclaredMethod("changeGameModeWithoutRewindBoundary", GameMode.class);
        level.setAccessible(true);
        level.invoke(loop, GameMode.LEVEL);
        for (int frame = 0; frame < 40; frame++) loop.step();
        GameServices.level().requestRespawn();
        List<Row> rows = new ArrayList<>();
        for (int step = 0; step < 400; step++) {
            loop.step();
            rows.add(row(loop));
        }
        int start = firstTitleCard(rows);
        for (int i = start; i < rows.size() && rows.get(i).mode() == GameMode.TITLE_CARD; i++) {
            // Level's Pal_FadeToBlack has left the screen black and LoadPalette_Immediate
            // (sonic3k.asm:7698) put line 0 up at full colour: the card slides in unfaded.
            assertEquals(FadeManager.FadeState.NONE, rows.get(i).host(), "restart title step " + i);
        }
        // Knuckles' LRZ1 start walks him in under the zone's own control lock.
        assertNativeHold(rows, GameMode.LEVEL, false);
    }

    @Test
    void bonusEntryAndReturnBothReleaseIntoTheHold() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            for (int frame = 0; frame < 60; frame++) step(session);
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            GameServices.level().requestBonusStageEntry(BonusStageType.GLOWING_SPHERE);
            List<Row> entry = steps(session, 500,
                    () -> session.loop().getCurrentGameMode() == GameMode.BONUS_STAGE
                            && GameServices.level().getFrameCounter() > 40);
            assertNativeHold(entry, GameMode.BONUS_STAGE);

            var coordinator = assertInstanceOf(Sonic3kBonusStageCoordinator.class, GameServices.bonusStage());
            var interior = GameServices.level().getObjectManager();
            coordinator.requestExit();
            List<Row> back = steps(session, 800,
                    () -> GameServices.level().getObjectManager() != interior
                            && session.loop().getCurrentGameMode() == GameMode.LEVEL
                            && GameServices.level().getFrameCounter() > 40);
            for (int i = firstTitleCard(back); back.get(i).mode() == GameMode.TITLE_CARD; i++) {
                assertEquals(FadeManager.FadeState.NONE, back.get(i).host(), "return title step " + i);
            }
            assertNativeHold(back, GameMode.LEVEL);
        }
    }

    private static List<Row> steps(GameplayCaptureSession session, int limit, Supplier<Boolean> done) {
        List<Row> rows = new ArrayList<>();
        for (int step = 0; step < limit && !done.get(); step++) {
            step(session);
            rows.add(row(session.loop()));
        }
        assertTrue(done.get(), "route reached its released gameplay: " + rows);
        return rows;
    }

    private static void step(GameplayCaptureSession session) {
        session.step(new Bk2FrameInput(0, 0, 0, false, "declared neutral title-card route"));
    }

    private static Bk2FrameInput press(int direction, int action) {
        return new Bk2FrameInput(0, direction, action, false, "S3K title-card hold data select route");
    }

    private record Row(GameMode mode, String card, int timer, int counter, int vint,
                       boolean locked, FadeManager.FadeState host) { }

    private static Row row(GameLoop loop) {
        var card = (Sonic3kTitleCardManager) loop.getTitleCardProvider();
        var level = GameServices.levelOrNull();
        int counter = level != null ? level.getFrameCounter() : -1;
        int vint = level != null && level.getObjectManager() != null ? level.getObjectManager().getVblaCounter() : -1;
        var sprites = GameServices.spritesOrNull();
        boolean locked = sprites != null && sprites.getMainPlayable() != null
                && sprites.getMainPlayable().isControlLocked();
        return new Row(loop.getCurrentGameMode(), card.getStateName(), card.getStateTimer(), counter, vint,
                locked, GameServices.fade().getState());
    }

    private static int firstTitleCard(List<Row> rows) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).mode() == GameMode.TITLE_CARD) return i;
        }
        throw new AssertionError("no title card was presented: " + rows);
    }

    private static void assertNativeHold(List<Row> rows, GameMode gameplay) {
        assertNativeHold(rows, gameplay, true);
    }

    private static void assertNativeHold(List<Row> rows, GameMode gameplay, boolean playerFree) {
        int start = firstTitleCard(rows);
        int release = start;
        while (release < rows.size() && rows.get(release).mode() == GameMode.TITLE_CARD) {
            Row locked = rows.get(release);
            // loc_62CC and LoadLevelLoadBlock wait on VBlank without LevelLoop's addq.
            assertEquals(0, locked.counter(), "locked title step " + release + " of " + rows);
            assertTrue(locked.locked(), "player locked at title step " + release);
            if (release > start) {
                assertEquals(rows.get(release - 1).vint() + 1, locked.vint(),
                        "V_int_run_count advances on title step " + release);
            }
            release++;
        }
        assertTrue(release < rows.size(), "the title loop released: " + rows);
        Row first = rows.get(release);
        assertEquals(gameplay, first.mode());
        assertEquals("DISPLAY/0", first.card() + "/" + first.timer(),
                "loc_64DC hands LevelLoop a card still holding $16 frames: " + first);
        assertEquals(0, first.counter());

        // One observation per Level_frame_counter, the first frame that reaches it.
        Row[] byCounter = new Row[HOLD + 2];
        int previousVint = Integer.MIN_VALUE;
        int previousCounter = 0;
        for (int i = release; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.mode() != gameplay || r.counter() > HOLD + 1) break;
            if (r.counter() != previousCounter) {
                assertEquals(previousCounter + 1, r.counter(), "LevelLoop counts every frame: " + r);
                if (previousVint != Integer.MIN_VALUE) {
                    assertEquals(previousVint + 1, r.vint(), "one V-int per LevelLoop frame: " + r);
                }
                previousCounter = r.counter();
            }
            previousVint = r.vint();
            if (byCounter[r.counter()] == null) byCounter[r.counter()] = r;
        }
        for (int counter = 0; counter < HOLD; counter++) {
            Row r = byCounter[counter];
            assertEquals("DISPLAY/" + counter, r == null ? "missing" : r.card() + "/" + r.timer(),
                    "Obj_TitleCardWait2 $2E = " + (HOLD - counter) + " at Level_frame_counter " + counter);
            if (playerFree && counter >= 1) {
                assertTrue(!r.locked(), "the player has control during the hold, counter " + counter);
            }
        }
        Row ended = byCounter[HOLD];
        assertEquals("EXIT", ended == null ? "missing" : ended.card(),
                "the hold reaches zero at Level_frame_counter 22");
    }
}
