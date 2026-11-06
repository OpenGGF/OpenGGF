package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomFingerprint;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomIdentityTable;
import com.openggf.game.*;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesSkIntroInstance;
import com.openggf.game.sonic3k.titlescreen.Sonic3kTitleScreenManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Actual ROM title, GameLoop, world, objects and draw owners; no injected exit action or position. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kStandaloneNativeLaunch {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeKnucklesTitleLaunchClearsProgressReplaysIntroAndAcceptsEitherStart(boolean playerTwo) throws Exception {
        try (var boot = new HeadlessGameBoot(320, 224)) {
            configure();
            boot.setBootToTitle(true);
            var loop = boot.boot(cartridge(RomIdentity.SK), "original SK window", 7, 0);
            var driver = new Driver(loop);
            waitForMenu(driver);
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            var title = (Sonic3kTitleScreenManager) loop.getTitleScreenProvider();
            var sonicTitle = render(loop, true);
            assertNonblank(sonicTitle);
            driver.press(0, false, true);
            assertEquals(GameMode.TITLE_SCREEN, loop.getCurrentGameMode(), "SK title polls P1 Start only");
            driver.press(2, false, false);
            driver.idle(16);
            assertEquals(1, title.nativeTitleState().option());
            var knucklesTitle = render(loop, true);
            assertTrue(differentPixels(sonicTitle, knucklesTitle) > 50, "native selected portrait/text must change");
            save(knucklesTitle, "sk-title-knuckles.png");

            var oldWorld = SessionManager.getCurrentWorldSession();
            GameServices.gameState().addScore(1230);
            GameServices.gameState().addLife();
            GameServices.gameState().addContinue();
            GameServices.gameState().markEmeraldCollected(2);
            GameServices.gameState().consumeCurrentSpecialStageIndexAndAdvance();
            GameServices.gameState().markSpecialRingCollected(3);
            long titleClock = EngineTiming.vIntRunCounter().value();
            assertTrue(titleClock > 0, "the real title must service the semantic V-int clock");
            driver.step(0, true, false);
            for (int i = 0; i < 500 && loop.getCurrentGameMode() != GameMode.LEVEL; i++) driver.neutral();
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
            long launchClock = EngineTiming.vIntRunCounter().value();
            assertTrue(launchClock >= titleClock && launchClock <= titleClock + 501,
                    "native launch must carry the nonzero visible clock into the new world");
            assertEquals(launchClock, Integer.toUnsignedLong(GameServices.level().getObjectManager().getVblaCounter()));
            for (int i = 0; i < 32 && !intro().ownsNativeIntroInput(); i++) driver.neutral();
            assertTrue(intro().ownsNativeIntroInput(), "the native actor must actually enter its initialization routine");
            assertEquals(EngineTiming.vIntRunCounter().value(), GameServices.rng().getSeed(),
                    "loc_634CA seeds RNG from the intro's admitted V_int_run_count");
            assertNotSame(oldWorld, SessionManager.getCurrentWorldSession());
            assertEquals(7, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals("knuckles", GameServices.sprites().getMainPlayable().getCode());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(3, GameServices.gameState().getLives());
            assertEquals(0, GameServices.gameState().getScore());
            assertEquals(0, GameServices.gameState().getContinues());
            assertEquals(0, GameServices.gameState().getEmeraldCount());
            assertEquals(0, GameServices.gameState().getCurrentSpecialStageIndex());
            assertFalse(GameServices.gameState().isSpecialRingCollected(3));
            assertEquals("sonic", GameServices.configuration().getString(SonicConfiguration.MAIN_CHARACTER_CODE),
                    "the native title selection applies to this launch without overwriting configuration");
            assertEquals(com.openggf.game.sonic3k.audio.Sonic3kMusic.KNUCKLES.id,
                    GameServices.level().getCurrentLevelMusicId());
            assertTrue(SessionManager.getCurrentWorldSession().getSaveSessionContext().activeSlot().isEmpty());
            assertEquals(0x200000, GameServices.rom().getRom().getSize());
            driver.idle(32);
            assertTrue(intro().ownsNativeIntroInput());
            assertTrue(GameServices.camera().getFrozen());
            assertEquals(0x560, GameServices.camera().getX() & 0xFFFF);
            assertEquals(0x948, GameServices.camera().getY() & 0xFFFF);
            assertEquals(0x680, GameServices.camera().getMinX() & 0xFFFF);
            assertTrue(GameServices.sprites().getMainPlayable().isControlLocked());
            assertTrue(GameServices.sprites().getMainPlayable().isObjectControlled());
            assertTrue(GameServices.sprites().getMainPlayable().isObjectMappingFrameControl());
            var introPixels = render(loop, true);
            assertNonblank(introPixels);
            assertTrue(differentPixels(introPixels, render(loop, false)) > 30,
                    "the admitted native intro actor must draw ROM pixels");
            save(introPixels, "sk-mhz-knuckles-intro.png");

            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var before = registry.capture();
            driver.idle(16);
            var after = registry.capture();
            registry.restore(before);
            sameWorld(before, registry.capture());
            assertEquals(0, differentPixels(introPixels, render(loop, true)), "restored actor/palette pixels");
            driver.idle(16);
            sameWorld(after, registry.capture());

            var introLevel = GameServices.level().getCurrentLevel();
            driver.press(0, !playerTwo, playerTwo);
            for (int i = 0; i < 120 && GameServices.level().getCheckpointState().getLastCheckpointIndex() <= 0; i++) driver.neutral();
            assertEquals(1, GameServices.level().getCheckpointState().getLastCheckpointIndex());
            for (int i = 0; i < 300 && (GameServices.camera().getFrozen()
                    || GameServices.sprites().getMainPlayable().isControlLocked()); i++) driver.neutral();
            assertFalse(GameServices.camera().getFrozen());
            assertFalse(GameServices.sprites().getMainPlayable().isControlLocked());
            assertNotSame(introLevel, GameServices.level().getCurrentLevel(), "the checkpoint must pass through a real level reload");
            assertEquals(3, GameServices.gameState().getLives(), "native restart consumes no life");
            assertFalse(GameServices.sprites().getMainPlayable().isHidden());
            assertEquals(com.openggf.game.sonic3k.audio.Sonic3kMusic.MHZ1.id,
                    GameServices.level().getCurrentLevelMusicId(), "checkpoint-one reload uses the ordinary MHZ playlist");
            assertTrue(GameServices.level().getObjectManager().objectQuery()
                    .activeObjectsOfType(CutsceneKnucklesSkIntroInstance.class).stream().noneMatch(o -> !o.isDestroyed()));
            var player = GameServices.sprites().getMainPlayable();
            int x = player.getCentreX();
            driver.step(8, false, false);
            for (int i = 0; i < 31; i++) driver.step(8, false, false);
            assertTrue(player.getCentreX() > x, "normal gameplay input must advance the loaded Knuckles route");
            var playable = render(loop, true);
            assertTrue(differentPixels(playable, render(loop, false)) > 40);
            save(playable, "sk-mhz-knuckles-playable.png");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void movieStartReachesTheNativeIntroOwnerInsteadOfPausing(boolean playerTwo) throws Exception {
        try (var boot = new HeadlessGameBoot(320, 224)) {
            configure();
            boot.setBootToTitle(true);
            var loop = boot.boot(cartridge(RomIdentity.SK), "original SK movie input", 7, 0);
            var driver = new Driver(loop);
            waitForMenu(driver);
            driver.press(2, false, false);
            driver.idle(16);
            driver.press(0, true, false);
            for (int i = 0; i < 500 && loop.getCurrentGameMode() != GameMode.LEVEL; i++) driver.neutral();
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
            for (int i = 0; i < 32 && !intro().ownsNativeIntroInput(); i++) driver.neutral();
            assertTrue(intro().ownsNativeIntroInput());
            var frames = new java.util.ArrayList<com.openggf.debug.playback.Bk2FrameInput>();
            for (int i = 0; i < 160; i++) {
                frames.add(new com.openggf.debug.playback.Bk2FrameInput(i, 0, 0,
                        i == 0 && !playerTwo, 0, 0, i == 0 && playerTwo, "authored native Start"));
            }
            var movie = new com.openggf.debug.playback.Bk2Movie(Path.of("native-intro-start.bk2"),
                    "native pad input", java.util.Map.of(), frames, 1);
            var playback = GameServices.playbackDebug();
            try {
                playback.startSession(movie, 0);
                loop.step();
                assertFalse(GameServices.gameState().isGamePaused(), "native intro owns recorded Start");
                for (int i = 0; i < 120 && GameServices.level().getCheckpointState().getLastCheckpointIndex() <= 0; i++)
                    loop.step();
                assertEquals(1, GameServices.level().getCheckpointState().getLastCheckpointIndex());
                for (int i = 0; i < 300 && GameServices.sprites().getMainPlayable().isControlLocked(); i++)
                    loop.step();
                assertFalse(GameServices.sprites().getMainPlayable().isControlLocked());
                assertFalse(GameServices.gameState().isGamePaused());
                assertEquals(3, GameServices.gameState().getLives());
            } finally {
                playback.endSession();
            }
        }
    }

    @Test void neutralNativeIntroCompletesItsRomLandingCallbackWithoutFixtureSignals() throws Exception {
        try (var boot = new HeadlessGameBoot(320, 224)) {
            configure();
            boot.setBootToTitle(true);
            var loop = boot.boot(cartridge(RomIdentity.SK), "original SK window", 7, 0);
            var driver = new Driver(loop);
            waitForMenu(driver);
            driver.press(2, false, false);
            driver.idle(16);
            driver.press(0, true, false);
            for (int i = 0; i < 500 && loop.getCurrentGameMode() != GameMode.LEVEL; i++) driver.neutral();
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
            assertTrue(intro().ownsNativeIntroInput());
            int startY = intro().getY();
            boolean moved = false;
            boolean bombSeen = false;
            boolean bombReplayed = false;
            for (int i = 0; i < 2000 && GameServices.level().getCheckpointState().getLastCheckpointIndex() <= 0; i++) {
                driver.neutral();
                var objects = GameServices.level().getObjectManager().getActiveObjects();
                bombSeen |= objects.stream().anyMatch(o -> "CutsceneKnuxSKIntroBomb".equals(o.getName()));
                moved |= objects.stream().filter(CutsceneKnucklesSkIntroInstance.class::isInstance)
                        .anyMatch(o -> o.getY() != startY);
                if (bombSeen && !bombReplayed) {
                    var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                    var before = registry.capture();
                    driver.idle(16);
                    var after = registry.capture();
                    for (int retirement = 0; retirement < 400 && liveBomb(); retirement++) driver.neutral();
                    assertFalse(liveBomb(), "the reached bomb must retire before restore recreates it");
                    registry.restore(before);
                    assertTrue(liveBomb(), "restore must recreate the retired bomb and its parent link");
                    sameWorld(before, registry.capture());
                    driver.idle(16);
                    sameWorld(after, registry.capture());
                    bombReplayed = true;
                }
            }
            assertTrue(bombSeen, "native timer and allocation must produce the bomb");
            assertTrue(bombReplayed, "the reached live parent/bomb graph must restore and replay");
            assertTrue(moved, "native falling movement must run");
            assertEquals(1, GameServices.level().getCheckpointState().getLastCheckpointIndex(),
                    "ROM landing animation must naturally invoke the run/restart callback");
            for (int i = 0; i < 300 && (GameServices.camera().getFrozen()
                    || GameServices.sprites().getMainPlayable().isControlLocked()); i++) driver.neutral();
            assertFalse(GameServices.camera().getFrozen());
            assertFalse(GameServices.sprites().getMainPlayable().isControlLocked());
        }
    }

    @Test void nativeSonicTitleSelectionUsesStandaloneMhzStartWithoutKnucklesIntro() throws Exception {
        try (var boot = new HeadlessGameBoot(320, 224)) {
            configure();
            boot.setBootToTitle(true);
            var loop = boot.boot(cartridge(RomIdentity.SK), "original SK window", 7, 0);
            var driver = new Driver(loop);
            waitForMenu(driver);
            driver.press(0, true, false);
            for (int i = 0; i < 500 && loop.getCurrentGameMode() != GameMode.LEVEL; i++) driver.neutral();
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
            assertEquals("sonic", GameServices.sprites().getMainPlayable().getCode());
            assertEquals(7, GameServices.level().getCurrentZone());
            assertEquals(0x680, GameServices.camera().getMinX() & 0xFFFF);
            driver.idle(32);
            assertFalse(GameServices.camera().getFrozen());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty(), "native SK Sonic never installs the locked-on carry-in Tails");
            int sonicX = GameServices.sprites().getMainPlayable().getCentreX();
            for (int i = 0; i < 32; i++) driver.step(8, false, false);
            assertTrue(GameServices.sprites().getMainPlayable().getCentreX() > sonicX);
            assertTrue(GameServices.level().getObjectManager().objectQuery()
                    .activeObjectsOfType(CutsceneKnucklesSkIntroInstance.class).stream().noneMatch(o -> !o.isDestroyed()));
            assertNonblank(render(loop, true));
        }
    }

    @Test void originalSonic3TitleRendersItsOwnMenuAndReachesNativeTwoPlayerExit() throws Exception {
        try (var boot = new HeadlessGameBoot(320, 224)) {
            configure();
            boot.setBootToTitle(true);
            var loop = boot.boot(cartridge(RomIdentity.S3), "original S3 window", 0, 0);
            var driver = new Driver(loop);
            waitForMenu(driver);
            assertNonblank(render(loop, true));
            driver.press(2, false, false);
            var title = (Sonic3kTitleScreenManager) loop.getTitleScreenProvider();
            var menu = render(loop, true);
            assertNonblank(menu);
            save(menu, "s3-title-two-player.png");
            // Observe only the consumer seam: actual title input, fade and callback run.
            // The stock competition host is qualified by the parent/competition stream.
            var requested = new java.util.concurrent.atomic.AtomicReference<TitleScreenProvider.TitleScreenAction>();
            title.setExitToLevelHandler(() -> requested.set(title.consumeExitAction()));
            try (var ownership = RecordedInputOwnership.acquire(loop.getInputHandler())) {
                loop.getInputHandler().setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                        new PlayerInputState(0, 0, 0, 0, true, true), PlayerInputState.neutral()));
                title.update(loop.getInputHandler());
                loop.getInputHandler().setLogicalOverride(LogicalInputSnapshot.neutral());
                for (int i = 0; i < 120 && requested.get() == null; i++) title.update(loop.getInputHandler());
            }
            assertEquals(TitleScreenProvider.TitleScreenAction.TWO_PLAYER, requested.get(),
                    "native Start must complete the original-S3 title fade and deliver TWO_PLAYER");
        }
    }

    private static void configure() {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "none");
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_VHS_EFFECT, false);
    }

    private static RomByteReader cartridge(RomIdentity identity) throws Exception {
        try (var image = new Rom()) {
            assertTrue(image.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            var reader = RomByteReader.fromRom(image).window(identity == RomIdentity.S3 ? 0x200000 : 0, 0x200000);
            assertTrue(RomIdentityTable.verify(identity, RomFingerprint.of(reader.slice(0, reader.size()))).isPresent());
            return reader;
        }
    }

    private static boolean liveBomb() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(o -> !o.isDestroyed() && "CutsceneKnuxSKIntroBomb".equals(o.getName()));
    }

    private static CutsceneKnucklesSkIntroInstance intro() {
        return GameServices.level().getObjectManager().objectQuery()
                .activeObjectsOfType(CutsceneKnucklesSkIntroInstance.class).stream()
                .filter(o -> !o.isDestroyed()).findFirst().orElseThrow();
    }

    private static void waitForMenu(Driver driver) {
        for (int i = 0; i < 1200 && driver.loop.getTitleScreenProvider().getState() != TitleScreenProvider.State.ACTIVE; i++) driver.neutral();
        assertEquals(TitleScreenProvider.State.ACTIVE, driver.loop.getTitleScreenProvider().getState());
        driver.idle(32); // reached menu control; let the independent title background settle
    }

    private static RgbaImage render(GameLoop loop, boolean sprites) {
        var graphics = GameServices.graphics();
        graphics.runPendingRenderThreadTasks();
        if (loop.getCurrentGameMode() == GameMode.TITLE_SCREEN) {
            var title = loop.getTitleScreenProvider();
            title.setClearColor(); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); title.draw();
        } else {
            GameServices.level().setClearColor(); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            GameServices.level().drawWithSpritePriority(GameServices.sprites(), sprites);
        }
        graphics.flush(); graphics.flushScreenSpace(); glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static void save(RgbaImage frame, String name) throws Exception {
        String directory = System.getProperty("tas.standalone.capture.dir");
        if (directory != null) ScreenshotCapture.savePNG(frame, Path.of(directory).resolve(name));
    }

    private static int differentPixels(RgbaImage a, RgbaImage b) {
        int count = 0;
        for (int y = 0; y < a.height(); y++) for (int x = 0; x < a.width(); x++) if (a.argb(x, y) != b.argb(x, y)) count++;
        return count;
    }

    private static void assertNonblank(RgbaImage frame) {
        int corner = frame.argb(0, 0), different = 0;
        for (int y = 0; y < frame.height(); y++) for (int x = 0; x < frame.width(); x++) if (frame.argb(x, y) != corner) different++;
        assertTrue(different > 1000, "the native title/level must draw ROM pixels");
    }

    private static void sameWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (var key : expected.entries().keySet()) assertTrue(
                RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)).isEmpty(), "rewind/replay " + key);
    }

    private static final class Driver {
        private final GameLoop loop;
        private int previousDirections;
        private boolean previousP1Start;
        private boolean previousP2Start;
        Driver(GameLoop loop) { this.loop = loop; }
        void neutral() { step(0, false, false); }
        void idle(int frames) { for (int i = 0; i < frames; i++) neutral(); }
        void press(int directions, boolean p1, boolean p2) { step(directions, p1, p2); neutral(); }
        void step(int directions, boolean p1, boolean p2) {
            try (var ownership = RecordedInputOwnership.acquire(loop.getInputHandler())) {
                loop.getInputHandler().setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                        new PlayerInputState(directions, directions & ~previousDirections, 0, 0,
                                p1, p1 && !previousP1Start),
                        new PlayerInputState(0, 0, 0, 0, p2, p2 && !previousP2Start)));
                if (GameServices.level().getCurrentLevel() != null) GameServices.level().skipPendingInitialTitleCardPresentation();
                loop.step();
                previousDirections = directions;
                previousP1Start = p1;
                previousP2Start = p2;
            }
        }
    }
}
