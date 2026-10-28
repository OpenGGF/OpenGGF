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
    private String capturePrefix;

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
            assertEquals(0x41F9, reader.readU16BE(0xB0B2));
            assertEquals(0xFFFF0EE0, reader.readU32BE(0xB0B4)); // Original TimeAttack_Records art slice.
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
            boolean wordControlReached = false;
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
                if (original) {
                    var reader = RomByteReader.fromRom(GameServices.rom().getRom());
                    var level = host.world().context().getLevelManager().getCurrentLevel();
                    assertEquals(512, level.getSolidTileCount());
                    int bank = reader.readU32BE(0x746A0 + arena.zoneId() * 8);
                    boolean discriminating = false;
                    for (int chunk = 0; chunk < level.getChunkCount(); chunk++) {
                        int nativeIndex = reader.readU16BE(bank + chunk * 2);
                        assertEquals(nativeIndex, level.getChunk(chunk).getSolidTileIndex(), arena + " primary index " + chunk);
                        assertEquals(reader.readU16BE(bank + 0x600 + chunk * 2),
                                level.getChunk(chunk).getSolidTileAltIndex(), arena + " secondary index " + chunk);
                        if (chunk > 255 && nativeIndex != reader.readU8(bank + chunk * 2)) discriminating = true;
                    }
                    wordControlReached |= discriminating;
                }
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
            if (original) assertTrue(wordControlReached, "native word records beyond entry 255 distinguish byte decoding");
        });
    }

    @Test void originalSoloMatchMovementContactsResultsAndRestart() throws Exception {
        // Independently authored boundary poses, not a traversed course or a TAS
        // synchronization oracle. Each contact starts eight pixels left of the
        // actual ROM goal with a one-pixel/frame launch; RIGHT owns the crossing.
        withProduct(true, visit -> {
            capturePrefix = "solo-match-goal-";
            selectMode(visit, 1);
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            for (int i = 0; i < 12; i++) step(visit, LogicalInputSnapshot.neutral());
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            var host = visit.host();
            assertEquals(1, host.players().size(), "P2 remains absent in solo Match Race");
            assertEquals(S3kCompetitionSession.Mode.MATCH_RACE, host.session().config().mode());
            waitForGo(visit);
            var retiredWorld = host.world();
            var goal = retiredWorld.context().getLevelManager().getObjectManager().getActiveObjects().stream()
                    .filter(com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance.class::isInstance)
                    .map(com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance.class::cast)
                    .findFirst().orElseThrow();
            var right = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8, 0, 0, 0, false, false),
                    PlayerInputState.neutral());
            for (int contact = 0; contact < 6; contact++) {
                var player = host.players().getFirst();
                // Reset the side latch outside the native +/-$20 contact window.
                // Ordinary neutral frames advance clocks; no lap/timer is written.
                player.setCentre(goal.getX() - 0x40, goal.getY());
                player.launch(0, 0);
                for (int i = 0; i < 60; i++) step(visit, LogicalInputSnapshot.neutral());
                assertEquals(contact, host.session().lap(0), "distant left pose does not consume a goal contact");
                player.setCentre(goal.getX() - 8, goal.getY());
                player.setObjectControl(0);
                player.setAngle(0);
                player.setGroundVelocity(0x100);
                player.setSolidBits(0xC, 0xD);
                player.launch(0x100, 0);
                int beforeX = player.centreX(), clock = host.session().nativeLapTimerFrames(0);
                step(visit, right);
                assertEquals(contact, host.session().lap(0), "the authored pose still requires forward movement");
                assertEquals(clock + 1, host.session().nativeLapTimerFrames(0), "one ordinary body advances the native clock once");
                int frames = 1;
                while (host.session().lap(0) == contact && frames++ < 24) step(visit, right);
                assertEquals(contact + 1, host.session().lap(0), "RIGHT reaches each actual goal contact");
                assertTrue(player.centreX() > beforeX && player.centreX() >= goal.getX());
                assertTrue(player.xVelocity() > 0, "native movement, rather than a post-goal position write");
            }
            assertTrue(host.session().finished(0));
            for (int i = 0; i < 1_200 && host.world() != null; i++) step(visit, LogicalInputSnapshot.neutral());
            // Native Match Race returns its finished result to arena selection.
            assertEquals(S3kCompetitionScreen.Phase.ARENA_SELECT, host.screen().view().phase());
            assertNull(host.world(), "the finished arena is retired before selection");
            var result = host.screen().view().result();
            assertEquals(S3kCompetitionSession.Mode.MATCH_RACE, result.mode());
            assertEquals(Arena.AZURE_LAKE, result.arena());
            assertEquals(1, result.players().size());
            var finisher = result.players().getFirst();
            assertTrue(finisher.finished());
            assertEquals(6, finisher.laps());
            assertEquals(5, finisher.lapCentiseconds().size());
            assertTrue(finisher.lapCentiseconds().stream().allMatch(time -> time > 0));
            assertTrue(finisher.totalCentiseconds() > 0);
            assertEquals(0, result.winner());
            draw(visit);
            // sub_369C2 (s3.asm:64682-64685) saves only negative Competition_type (Time Attack).
            assertEquals(com.openggf.game.save.SaveSlotState.EMPTY,
                    new com.openggf.game.save.SaveManager(saves).readSlotSummary("s3-competition", 0).state());
            assertEquals(-1, host.newRecordIndex());
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            for (int i = 0; i < 12; i++) step(visit, LogicalInputSnapshot.neutral());
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            assertEquals(S3kCompetitionScreen.Phase.RACE, host.screen().view().phase());
            assertNotSame(retiredWorld, host.world());
            assertEquals(1, host.players().size());
            assertEquals(0, host.session().lap(0));
            assertEquals(0, host.session().nativeLapTimerFrames(0));
            waitForGo(visit);
            int restartedX = host.players().getFirst().centreX();
            for (int i = 0; i < 17; i++) step(visit, right);
            assertTrue(host.players().getFirst().centreX() > restartedX, "fresh arena accepts ordinary P1 control");
        });
    }

    @Test void originalPositionedTimeAttackGoalRewindResultsSaveRestartAndBack() throws Exception {
        // Positioned goal-boundary qualification, separately from full controller routes.
        // Only the actor centre is positioned; actual GO, clocks, lap/finish owners,
        // record insertion, output, teardown and restart run normally.
        withProduct(true, visit -> {
            capturePrefix = "positioned-goal-";
            selectMode(visit, 2);
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            for (int i = 0; i < 12; i++) step(visit, LogicalInputSnapshot.neutral());
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            var host = visit.host();
            assertEquals(1, host.players().size());
            waitForGo(visit);
            var retiredWorld = host.world();
            var context = retiredWorld.context();
            var goal = context.getLevelManager().getObjectManager().getActiveObjects().stream()
                    .filter(com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance.class::isInstance)
                    .map(com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance.class::cast)
                    .findFirst().orElseThrow();
            for (int crossing = 0; crossing < 6; crossing++) {
                var player = host.players().getFirst();
                player.setObjectControl(1);
                player.setCentreNative(goal.getX() - 0x40, goal.getY());
                for (int i = 0; i < 60; i++) step(visit, LogicalInputSnapshot.neutral());
                var checkpoint = crossing == 5 ? context.getRewindRegistry().capture() : null;
                player.setCentreNative(goal.getX() + 1, goal.getY());
                step(visit, LogicalInputSnapshot.neutral());
                if (checkpoint != null) {
                    var finished = host.session().capture();
                    assertTrue(host.session().finished(0));
                    context.getRewindRegistry().restore(checkpoint);
                    assertFalse(host.session().finished(0));
                    host.players().getFirst().setCentreNative(goal.getX() + 1, goal.getY());
                    step(visit, LogicalInputSnapshot.neutral());
                    assertEquals(finished, host.session().capture(), "native final goal and record replay");
                }
            }
            for (int i = 0; i < 1_200 && host.world() != null; i++) step(visit, LogicalInputSnapshot.neutral());
            assertEquals(S3kCompetitionScreen.Phase.TIME_ATTACK_RESULTS, host.screen().view().phase());
            assertNull(host.world());
            draw(visit);
            var result = host.screen().view().result().players().getFirst();
            assertTrue(result.finished());
            var save = new com.openggf.game.save.SaveManager(saves).readSlotSummary("s3-competition", 0);
            assertTrue(save.isLoadable());
            var records = new S3kCompetitionRecords(java.util.HexFormat.of().parseHex((String) save.payload().get("nativeRecords")));
            assertEquals(S3kCompetitionRecords.packCentiseconds(result.totalCentiseconds()),
                    records.entries(Arena.AZURE_LAKE).getFirst().packedTime());
            for (int i = 0; i < 31; i++) step(visit, LogicalInputSnapshot.neutral());
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            for (int i = 0; i < 12; i++) step(visit, LogicalInputSnapshot.neutral());
            press(visit, 0, 0, InputActionMasks.ACTION_A);
            assertNotSame(retiredWorld, host.world());
            assertEquals(0, host.session().nativeLapTimerFrames(0));
            var start = PlayerInputState.of(0, 0, 0, 0, true, true);
            step(visit, LogicalInputSnapshot.ofPlayers(start, PlayerInputState.neutral()));
            step(visit, LogicalInputSnapshot.neutral());
            assertTrue(host.pause().gamePaused());
            press(visit, 0, 0, InputActionMasks.ACTION_B);
            step(visit, LogicalInputSnapshot.neutral());
            assertEquals(S3kCompetitionScreen.Phase.ARENA_SELECT, host.screen().view().phase());
            assertNull(host.world());
            press(visit, 0, 0, InputActionMasks.ACTION_B);
            assertEquals(S3kCompetitionScreen.Phase.MODE, host.screen().view().phase());
            press(visit, 0, 0, InputActionMasks.ACTION_B);
            assertEquals(NativeMenuScreen.Destination.TITLE_SCREEN, visit.consumeExit().destination());
        });
    }

    private void withProduct(boolean original, CheckedConsumer body) throws Exception {
        cartridge = original ? "original-s3" : "locked-on";
        capturePrefix = "";
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
        ScreenshotCapture.savePNG(pixels, pictures.resolve(capturePrefix + phase + "-" + arena + ".png"));
    }
    @FunctionalInterface private interface CheckedConsumer { void accept(S3kNativeTitleModeProvider.Visit visit) throws Exception; }
}
