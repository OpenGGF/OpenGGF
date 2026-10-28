package com.openggf.game.sonic3k.competition;

import com.openggf.audio.NullAudioBackend;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.nativemenu.NativeTitleModeProvider;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import static org.junit.jupiter.api.Assertions.*;

/** Actual contextual product with original cartridge views. No trace state or terrain surrogate. */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3CompetitionProduct {
    @TempDir Path saves;
    private InputHandler input;
    private String cartridge;

    @Test void originalCartridgeHasItsOwnMenuOperandsAndRejectsUnknownRevision() throws Exception {
        var reader = RomByteReader.fromRom(GameServices.rom().getRom()).window(0x200000, 0x200000);
        try (var rom = Rom.fromReader(reader, "original S3 cartridge")) {
            assertEquals(S3kCompetitionResources.ORIGINAL_S3, S3kCompetitionResources.identify(rom));
            // Competition_Menu's actual LEA operands, independent of the engine's resource map.
            assertEquals(0x41F9, reader.readU16BE(0x95AE));
            assertEquals(0x19D2A2, reader.readU32BE(0x95B0));
            assertEquals(0x41F9, reader.readU16BE(0x9616));
            assertEquals(0x19F46E, reader.readU32BE(0x9618));
            assertEquals(0x600, reader.readU16BE(0x5E000));
            assertEquals(0x217C, reader.readU16BE(0x354F8));
            assertEquals(0x35668, reader.readU32BE(0x354FA)); // Obj_DPZDisolvingSandBar subtype branch.
            var game = new S3CompetitionGame(rom);
            for (int index = 28; index < 38; index++) {
                assertEquals(0x120 + (index - 28) / 2, game.getMusicId(index), "original native arena playlist");
                assertEquals(game.getMusicId(index), game.getMusicId(0xC0 + index), "stock level descriptor namespace");
            }
            assertThrows(IllegalArgumentException.class, () -> game.loadLevel(0), "this loader cannot substitute for a one-player campaign");
            assertThrows(java.io.IOException.class, () -> new S3CompetitionGame(
                    Rom.fromReader(RomByteReader.fromBytes(new byte[0x200000]), "unknown cartridge")));
            assertThrows(java.io.IOException.class, () -> new com.openggf.game.sonic3k.Sonic3kLevel.CollisionProfile(
                    0x200000, 0x726A0, 0x704A0).validate(rom));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void contextualMenusAllFiveArenasBothHumansAndDestructiveRestore(boolean original) throws Exception {
        withProduct(original, visit -> {
            var host = visit.host();
            draw(visit); // A real mode-menu scene before any arena exists.
            press(visit, 0, 0, InputActionMasks.ACTION_B);
            assertEquals(NativeMenuScreen.Destination.TITLE_SCREEN, visit.consumeExit().destination());
            visit.initialize();
            for (Arena arena : Arena.values()) {
                // Native Match Race -> P2 joins -> both confirm -> native track cursor.
                selectMode(visit, 1);
                press(visit, 1, 0, InputActionMasks.ACTION_A);
                int selectedCharacter = arena == Arena.DESERT_PALACE ? 2 : 0;
                for (int rotations = 0; host.screen().view().characters().getFirst() != selectedCharacter
                        && rotations < 3; rotations++) {
                    press(visit, 0, 8, 0);
                    for (int i = 0; i < 17; i++) step(visit, LogicalInputSnapshot.neutral());
                }
                assertEquals(selectedCharacter, host.screen().view().characters().getFirst());
                press(visit, 0, 0, InputActionMasks.ACTION_A);
                press(visit, 1, 0, InputActionMasks.ACTION_A);
                for (int i = 0; i < 10; i++) step(visit, LogicalInputSnapshot.neutral());
                int row = S3kCompetitionSession.GRAND_PRIX_ORDER.indexOf(arena);
                for (int moves = 0; host.screen().view().arenaRow() != row && moves < 5; moves++) {
                    press(visit, 0, host.screen().view().arenaRow() < row ? 2 : 1, 0);
                    for (int wait = 0; wait < 9; wait++) step(visit, LogicalInputSnapshot.neutral());
                }
                assertEquals(row, host.screen().view().arenaRow(), () -> "visible track cursor: " + host.screen().view());
                draw(visit);
                press(visit, 0, 0, InputActionMasks.ACTION_A);
                assertEquals(S3kCompetitionScreen.Phase.RACE, host.screen().view().phase());
                assertEquals(arena, host.session().arena());
                assertEquals(original ? S3kCompetitionResources.ORIGINAL_S3 : S3kCompetitionResources.LOCKED_ON,
                        host.resources());
                assertEquals(2, host.players().size());
                waitForGo(visit);
                int start = host.players().getFirst().centreX();
                var moving = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8, 0, 0, 0, false, false),
                        PlayerInputState.neutral());
                var context = host.world().context();
                var checkpoint = context.getRewindRegistry().capture();
                int stationaryClock = host.session().nativeLapTimerFrames(1);
                for (int i = 0; i < 17; i++) step(visit, moving);
                assertEquals(stationaryClock + 17, host.session().nativeLapTimerFrames(1), "neutral P2 clock advances once per shared world frame");
                assertEquals(host.session().nativeLapTimerFrames(1), host.levelState(1).getTimerFrames());
                var expected = host.session().capture();
                var scroll = host.scroll().capture();
                var pages = host.spritePages().capture();
                draw(visit);
                var pixels = ScreenshotCapture.captureFramebuffer(320, 224);
                assertNotEquals(start, host.players().getFirst().centreX(), arena + " real P1 RIGHT reached movement");
                assertEquals(0, host.players().get(1).xVelocity(), "P2 retains its independent neutral input");
                var retired = host.player(1);
                context.getSpriteManager().removeSprite(host.player(0).getCode());
                context.getSpriteManager().removeSprite(host.player(1).getCode());
                context.getRewindRegistry().restore(checkpoint);
                assertNotSame(retired, host.player(1));
                for (int i = 0; i < 17; i++) step(visit, moving);
                assertEquals(expected, host.session().capture(), arena + " both participant clocks/state replay");
                assertEquals(scroll, host.scroll().capture(), arena + " both cameras replay");
                assertEquals(pages, host.spritePages().capture(), arena + " published sprite pages replay");
                draw(visit);
                var replay = ScreenshotCapture.captureFramebuffer(320, 224);
                for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++)
                    assertEquals(pixels.argb(x, y), replay.argb(x, y), arena + " replay pixels " + x + "," + y);
                host.closeArena();
                visit.initialize();
            }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void grandPrixRealControllerRouteReachesEveryTrackAndResults(boolean original) throws Exception {
        withProduct(original, visit -> {
            press(visit, 1, 0, InputActionMasks.ACTION_A);
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            press(visit, 1, 0, InputActionMasks.ACTION_A);
            var reached = new ArrayList<Arena>();
            var independentCameras = new java.util.HashSet<Arena>();
            int frames = 0;
            while (visit.host().screen().view().phase() == S3kCompetitionScreen.Phase.RACE && frames < 24_000) {
                var host = visit.host();
                if (!reached.contains(host.session().arena())) reached.add(host.session().arena());
                if (host.scroll().view(0).camera().x() != host.scroll().view(1).camera().x()
                        || host.scroll().view(0).camera().y() != host.scroll().view(1).camera().y())
                    independentCameras.add(host.session().arena());
                if (!host.session().started()) step(visit, LogicalInputSnapshot.neutral());
                else step(visit, runningInput(host, frames));
                frames++;
            }
            assertEquals(S3kCompetitionSession.GRAND_PRIX_ORDER, reached, "real input must reach all five tracks; frames="
                    + frames + ", session=" + (visit.host().session() == null ? "results" : visit.host().session().capture()));
            assertEquals(S3kCompetitionScreen.Phase.RESULTS, visit.host().screen().view().phase(),
                    "route did not finish: " + visit.host().screen().view());
            assertEquals(new java.util.HashSet<>(S3kCompetitionSession.GRAND_PRIX_ORDER), independentCameras,
                    "both human camera owners must actually diverge on every track");
            var result = visit.host().screen().view().result();
            assertTrue(result.players().stream().allMatch(S3kCompetitionSession.PlayerResult::finished));
            assertTrue(result.grandPrixTimes().stream().flatMap(List::stream).allMatch(time -> time > 0));
            draw(visit);
            for (int i = 0; i < 30; i++) step(visit, LogicalInputSnapshot.neutral());
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            for (int i = 0; i < 16; i++) step(visit, LogicalInputSnapshot.neutral());
            press(visit, 1, 0, InputActionMasks.ACTION_A);
            assertEquals(S3kCompetitionScreen.Phase.MODE, visit.host().screen().view().phase());
            assertNull(visit.host().world());
        });
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void singleEventRoutesFinishRestartAndPersistOneHumanRecord(boolean original) throws Exception {
        withProduct(original, visit -> {
            for (int modeRow : new int[]{1, 2}) {
                selectMode(visit, modeRow);
                press(visit, modeRow == 1 ? 1 : 0, 0, InputActionMasks.ACTION_A);
                press(visit, 0, 0, InputActionMasks.ACTION_A);
                press(visit, 1, 0, InputActionMasks.ACTION_A);
                for (int i = 0; i < 12; i++) step(visit, LogicalInputSnapshot.neutral());
                press(visit, 0, 0, InputActionMasks.ACTION_A);
                var host = visit.host();
                assertEquals(modeRow == 1 ? 2 : 1, host.players().size(), "Time Attack owns exactly one human");
                int frames = 0;
                while (host.screen().view().phase() == S3kCompetitionScreen.Phase.RACE && frames < 8_000) {
                    step(visit, host.session().started() ? runningInput(host, frames) : LogicalInputSnapshot.neutral());
                    frames++;
                }
                assertNotEquals(S3kCompetitionScreen.Phase.RACE, host.screen().view().phase(),
                        () -> "single-event controller route: " + host.session().capture());
                var result = host.screen().view().result();
                assertEquals(Arena.AZURE_LAKE, result.arena());
                assertTrue(result.players().stream().allMatch(S3kCompetitionSession.PlayerResult::finished));
                assertNull(host.world(), "goal retires the arena world before selection/results");
                draw(visit);
                if (modeRow == 2) {
                    assertEquals(S3kCompetitionScreen.Phase.TIME_ATTACK_RESULTS, host.screen().view().phase());
                    var save = new com.openggf.game.save.SaveManager(saves).readSlotSummary(
                            original ? "s3-competition" : "s3k-competition", 0);
                    assertTrue(save.isLoadable(), "a real qualifying one-human finish persists native records");
                    var records = new S3kCompetitionRecords(java.util.HexFormat.of().parseHex(
                            (String) save.payload().get("nativeRecords")));
                    long time = S3kCompetitionRecords.packCentiseconds(result.players().getFirst().totalCentiseconds());
                    assertTrue(records.entries(Arena.AZURE_LAKE).stream().anyMatch(entry -> entry.packedTime() == time));
                    for (int i = 0; i < 31; i++) step(visit, LogicalInputSnapshot.neutral());
                    press(visit, 0, 0, InputActionMasks.ACTION_A);
                }
                assertEquals(S3kCompetitionScreen.Phase.ARENA_SELECT, host.screen().view().phase());
                // Re-enter through ordinary confirmation, rather than retaining the retired world.
                press(visit, 0, 0, InputActionMasks.ACTION_A);
                if (modeRow == 1) press(visit, 1, 0, InputActionMasks.ACTION_A);
                for (int i = 0; i < 12; i++) step(visit, LogicalInputSnapshot.neutral());
                press(visit, 0, 0, InputActionMasks.ACTION_A);
                assertEquals(S3kCompetitionScreen.Phase.RACE, host.screen().view().phase());
                assertEquals(0, host.session().nativeLapTimerFrames(0), "new arena clock starts from its native countdown");
                assertEquals(0, host.session().lap(0));
                host.closeArena();
                visit.initialize();
            }
        });
    }

    private LogicalInputSnapshot runningInput(S3kCompetitionNativeHost host, int frame) {
        // Ordinary continuous run: jumping at a fixed cadence can repeatedly
        // detach a miniature from Azure Lake's loop before it builds momentum.
        // All terrain, obstacles, wrapping and lap progress remain engine-owned.
        var p1 = PlayerInputState.of(8, 0, 0, 0, false, false);
        var p2 = host.players().size() == 2
                ? p1
                : PlayerInputState.neutral();
        return LogicalInputSnapshot.ofPlayers(p1, p2);
    }

    private void withProduct(boolean original, CheckedConsumer body) throws Exception {
        cartridge = original ? "original-s3" : "locked-on";
        var source = RomByteReader.fromRom(GameServices.rom().getRom());
        var selected = original ? source.window(0x200000, 0x200000) : source;
        String previous = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", saves.toString());
        try (var rom = Rom.fromReader(selected, original ? "original S3 cartridge" : "locked-on S3K cartridge")) {
            TestEnvironment.configureRomFixture(rom);
            try (var boot = new HeadlessGameBoot(320, 224)) {
                var engine = EngineServices.current();
                engine.audio().setBackend(new NullAudioBackend());
                var module = new Sonic3kGameModule();
                var provider = module.getGameService(NativeTitleModeProvider.class);
                assertEquals(NativeMenuScreen.Exit.NONE, provider.singlePlayerTitleExit(), "ordinary one-player policy remains independent");
                var published = new AtomicReference<GameplayModeContext>();
                input = new InputHandler();
                var visit = assertInstanceOf(S3kNativeTitleModeProvider.Visit.class,
                        provider.competitionScreen(new NativeMenuHostContext(engine, module, input, published::set)));
                visit.initialize();
                try { body.accept(visit); }
                finally { visit.reset(); engine.graphics().cleanup(); RenderContext.reset(); SessionManager.clear(); }
                assertThrows(IllegalStateException.class, visit::initialize, "retired visit cannot reuse GPU/world ownership");
            }
        } finally {
            if (previous == null) System.clearProperty("openggf.saveRoot"); else System.setProperty("openggf.saveRoot", previous);
        }
    }

    private void waitForGo(S3kNativeTitleModeProvider.Visit visit) {
        int frames = 0;
        while (!visit.host().session().started() && frames++ < 1_200) step(visit, LogicalInputSnapshot.neutral());
        assertTrue(visit.host().session().started(), "actual neon countdown starts both clocks");
    }
    private void selectMode(S3kNativeTitleModeProvider.Visit visit, int row) {
        assertEquals(S3kCompetitionScreen.Phase.MODE, visit.host().screen().view().phase());
        for (int moves = 0; visit.host().screen().view().modeRow() != row && moves < 4; moves++)
            press(visit, 0, 2, 0);
        assertEquals(row, visit.host().screen().view().modeRow());
    }
    private void press(S3kNativeTitleModeProvider.Visit visit, int slot, int direction, int action) {
        var buttons = PlayerInputState.of(direction, direction, action, action, false, false);
        step(visit, LogicalInputSnapshot.ofPlayers(slot == 0 ? buttons : PlayerInputState.neutral(),
                slot == 1 ? buttons : PlayerInputState.neutral()));
    }
    private void step(S3kNativeTitleModeProvider.Visit visit, LogicalInputSnapshot logical) {
        input.setLogicalOverride(logical);
        if (visit.host().world() == null) visit.update(input);
        else visit.host().world().context().plcFrameLifecycle().runLogicalIteration(() -> {}, frame -> {
            NativeCompetitionIterationAccess.runAdmitted(frame, () -> visit.update(input)); return null;
        });
        visit.afterFrame();
    }
    private void draw(S3kNativeTitleModeProvider.Visit visit) throws java.io.IOException {
        visit.draw();
        if (visit.host().world() == null) GameServices.graphics().flushScreenSpace();
        var pixels = ScreenshotCapture.captureFramebuffer(320, 224);
        var colors = new java.util.HashSet<Integer>();
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) colors.add(pixels.argb(x, y));
        assertTrue(colors.size() > 8, "actual ROM-native scene has nonblank pixels");
        Path pictures = Path.of("target/capture/s3-competition-product", cartridge);
        java.nio.file.Files.createDirectories(pictures);
        String phase = visit.host().screen().view().phase().name().toLowerCase(java.util.Locale.ROOT);
        String arena = visit.host().world() == null ? "menu" : visit.host().session().arena().name().toLowerCase(java.util.Locale.ROOT);
        ScreenshotCapture.savePNG(pixels, pictures.resolve(phase + "-" + arena + ".png"));
    }
    @FunctionalInterface private interface CheckedConsumer { void accept(S3kNativeTitleModeProvider.Visit visit) throws Exception; }
}
