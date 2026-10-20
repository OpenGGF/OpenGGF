package com.openggf.game.session;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.Pattern;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.RomTestUtils;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent physical-publication oracle for the 2026-10-18 world Nemesis task.
 * The raw capsule and following Robotnik-ship PLC appends are explicitly
 * SYNTHETIC public component inlets in an ordinary ROM-loaded solo-Knuckles CNZ1 scene;
 * this does not exercise CNZ2 admission.
 * Expected pixels are decoded separately from the original ROM archive and
 * expanded here, without the PLC producer or pattern-publication implementation.
 *
 * Native044 sonic3k.asm:2140..2172 Init arms only an idle head, with no pixel
 * write; 2200..2260 Process_Nem_Queue_2 writes at most three complete patterns,
 * decrements after each eight-row write and shifts a completed head without
 * initializing its successor. Ordinary queued DMA precedes that service
 * (789..810); the ordinary tail is AnimateTiles -> Init -> KosM (7938..7940).
 * Real GameLoop frames exercise opening service and the ordinary loop tail.
 * An actual claimed lifecycle token and public game-VBlank dispatch then expose
 * the gap between final head completion and the next Init, which a whole frame
 * does not expose. Claim-time PLC readiness is not assumed to publish pixels.
 *
 * This checks level-memory pixels, stable Pattern references, logical progress
 * and the actual registered pattern journal. It does not certify native VRAM,
 * DMA hardware, water/HInt timing, a natural producer/caller or route, renderer
 * refresh, rewind/reopen/level replacement, or the relative animation-tail order.
 * The distinct successor is native PLC_RobotnikShip immediately after the
 * eight-byte PLC_EggCapsule (176736..176742); native constants place it at
 * tile 052E, and ArtNem_RobotnikShip includes Ship.nem (201103..201105),
 * whose original header 8052 names 82 patterns in XOR mode. Runtime data is
 * read exclusively from the actual world's ROM.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kWorldNemesisOrdinaryPublication {
    private static final int RAW_PLC = 0x083D74;
    private static final int SOURCE = 0x0DD990;
    private static final int DESTINATION_BYTES = 0x9280;
    private static final int DESTINATION_TILE = 0x494;
    private static final int PATTERNS = 70;
    private static final int SUCCESSOR_RAW_PLC = 0x083D7C;
    private static final int SUCCESSOR_SOURCE = 0x0D771E;
    private static final int SUCCESSOR_TILE = 0x52E;
    private static final int SUCCESSOR_PATTERNS = 82;
    private static final int FIRST_OBSERVED_TILE = DESTINATION_TILE - 1;
    // Includes both destinations, each adjacent guard, and the intervening pixels.
    private static final int OBSERVED_PATTERNS = SUCCESSOR_TILE + SUCCESSOR_PATTERNS + 1 - FIRST_OBSERVED_TILE;
    private static final PlcLifecyclePhase ORDINARY = PlcLifecyclePhase.ORDINARY_LEVEL;

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void ordinaryFramesPublishConfirmedThreePatternPrefixesAndDoNotArmTheSuccessor() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        // One actual capture boot owns the load and its pending title request.
        // No positioned/ring/clock/history seed or second title entry is used.
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),
                    Sonic3kZoneIds.ZONE_CNZ, 0, settings);
            var context = SessionManager.getCurrentGameplayMode();
            assertNotNull(context, "the actual capture boot must own a gameplay context");
            var world = context.getWorldSession();
            var configuration = GameServices.configuration();
            assertAll("actual declared solo Knuckles capture team and viewport",
                    () -> assertSame(configuration, EngineServices.current().configuration()),
                    () -> assertEquals(settings.mainCharacter(), configuration.getString(
                            SonicConfiguration.MAIN_CHARACTER_CODE)),
                    () -> assertEquals(settings.sidekickCharacter(), configuration.getString(
                            SonicConfiguration.SIDEKICK_CHARACTER_CODE)),
                    () -> assertNull(world.getSaveSessionContext(), "no save-selected team overrides this entry"),
                    () -> assertEquals("knuckles", ActiveGameplayTeamResolver.resolveMainCharacterCode(
                            world, configuration)),
                    () -> assertEquals(List.of(), ActiveGameplayTeamResolver.resolveSidekicks(configuration)),
                    () -> assertInstanceOf(Knuckles.class, session.player()),
                    () -> assertEquals(320, GameServices.camera().getWidth()),
                    () -> assertEquals(224, GameServices.camera().getHeight()));
            var level = assertInstanceOf(Sonic3kLevel.class, world.getCurrentLevel());
            var plc = WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class);
            var journal = context.getKosinskiModuleQueue();
            assertNotNull(journal, "the actual context's pattern journal is required");
            var titleInlet = new PublicationInlet(context, null, session.player());
            var titleLevelManager = context.getLevelManager();
            var titleObjects = titleLevelManager.getObjectManager();
            requireBindings(titleInlet, context, world, level, plc, journal);
            assertSame(titleLevelManager, GameServices.level());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertTrue(titleLevelManager.isTitleCardRequested(),
                    "showTitle=true must retain this load's actual pending title request");
            assertTrue(plc.isBusy(), "fresh-zone descriptors precede the production title loop");

            // The fresh load's one-shot initial Load_Sprites/Process_Sprites
            // admission precedes the pending title request. Let production
            // consume that setup token once; it is not a title or queue warmup.
            assertTrue(titleLevelManager.hasPendingInitialProcessSpritesPass());
            session.step(null);
            assertFalse(titleLevelManager.hasPendingInitialProcessSpritesPass());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertTrue(titleLevelManager.isTitleCardRequested());
            requireBindings(titleInlet, context, world, level, plc, journal);
            assertSame(titleLevelManager, GameServices.level());
            assertSame(titleObjects, titleLevelManager.getObjectManager());
            assertSame(titleInlet.sprite(), session.player());
            // The next normal step reaches that same pending title request;
            // no manual enter/reset/rebind or additional setup retry is used.
            session.step(null);
            assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode());
            requireBindings(titleInlet, context, world, level, plc, journal);
            assertSame(titleObjects, titleLevelManager.getObjectManager());
            int titleSteps = 1;
            // Only actual TITLE iterations are admitted. 600 is a safety cap,
            // not an accepted title duration or a queue-empty warmup target.
            while (session.loop().getCurrentGameMode() == GameMode.TITLE_CARD && titleSteps < 600) {
                assertSame(titleInlet.sprite(), session.player());
                requireBindings(titleInlet, context, world, level, plc, journal);
                assertSame(titleLevelManager, GameServices.level());
                assertSame(titleObjects, titleLevelManager.getObjectManager());
                session.step(null);
                titleSteps++;
                requireBindings(titleInlet, context, world, level, plc, journal);
                assertSame(titleObjects, titleLevelManager.getObjectManager());
            }
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(),
                    "the production title must naturally release within the safety bound");
            assertFalse(titleLevelManager.isTitleCardRequested());
            assertSame(titleLevelManager, GameServices.level());
            assertSame(titleInlet.sprite(), session.player());
            // Release is SETUP_ONLY before ordinary work. A newly constructed
            // public runner captures THIS same current LevelManager/player;
            // only it steps the ordinary component window below, never both.
            var fixture = new PublicationInlet(context, new HeadlessTestRunner(session.player()), session.player());
            requireBindings(fixture, context, world, level, plc, journal);
            assertTrue(level.getPatternCount() > SUCCESSOR_TILE + SUCCESSOR_PATTERNS,
                    "the real loaded level must expose both complete destinations and all guards");
            assertEquals(new NemesisPlcQueueSnapshot(null, List.of()), plc.capture(),
                    "declared empty-FIFO prerequisite; do not clear, drain or warm up the owner");
            var registered = context.getRewindRegistry().capture();
            assertTrue(registered.entries().containsKey(plc.key()));
            assertTrue(registered.entries().containsKey(journal.key()));
            assertEquals(plc.capture(), registered.get(plc.key()));
            assertEquals(journal.capture(), registered.get(journal.key()));

            // Independent data admission from this actual world's original ROM.
            var rom = world.getDataSource().rom().orElseThrow(
                    () -> new AssertionError("the actual loaded world's ROM is required"));
            assertSame(TestEnvironment.currentRom(), rom);
            assertEquals(0, rom.read16BitAddr(RAW_PLC) & 0xFFFF);
            assertEquals(SOURCE, rom.read32BitAddr(RAW_PLC + 2));
            assertEquals(DESTINATION_BYTES, rom.read16BitAddr(RAW_PLC + 6) & 0xFFFF);
            assertEquals(0x0046, rom.read16BitAddr(SOURCE) & 0xFFFF);
            assertEquals(DESTINATION_BYTES / 32, DESTINATION_TILE);
            assertTrue(rom.getSize() >= SUCCESSOR_RAW_PLC + 8L);
            assertEquals(RAW_PLC + 2 + 6, SUCCESSOR_RAW_PLC, "following one-entry native raw definition");
            assertEquals(0, rom.read16BitAddr(SUCCESSOR_RAW_PLC) & 0xFFFF);
            assertEquals(SUCCESSOR_SOURCE, rom.read32BitAddr(SUCCESSOR_RAW_PLC + 2));
            assertEquals(SUCCESSOR_TILE * 32, rom.read16BitAddr(SUCCESSOR_RAW_PLC + 6) & 0xFFFF);
            assertEquals(0x8052, rom.read16BitAddr(SUCCESSOR_SOURCE) & 0xFFFF,
                    "independent original XOR-mode successor header");
            byte[] decoded;
            try (var channel = Channels.newChannel(new ByteArrayInputStream(
                    rom.readBytes(SOURCE, Math.toIntExact(rom.getSize() - SOURCE))))) {
                decoded = NemesisReader.decompress(channel);
            }
            assertEquals(PATTERNS * 32, decoded.length);
            byte[] successorDecoded;
            try (var channel = Channels.newChannel(new ByteArrayInputStream(
                    rom.readBytes(SUCCESSOR_SOURCE, Math.toIntExact(rom.getSize() - SUCCESSOR_SOURCE))))) {
                successorDecoded = NemesisReader.decompress(channel);
            }
            assertEquals(SUCCESSOR_PATTERNS * 32, successorDecoded.length);
            byte[] expectedPixels = expandNibbles(decoded);
            byte[] successorPixels = expandNibbles(successorDecoded);
            var baseline = observe(level, plc, journal);
            // Historical journal ranges reread current pixels on capture. Both
            // complete destinations must start uncovered to prove new journaling.
            for (int index = 0; index < PATTERNS * 32; index++) {
                assertFalse(baseline.journalPresent()[32 + index],
                        "hard fixture prerequisite: no baseline capsule journal coverage, byte=" + index);
            }
            int successorJournalOffset = (SUCCESSOR_TILE - FIRST_OBSERVED_TILE) * 32;
            for (int index = 0; index < SUCCESSOR_PATTERNS * 32; index++) {
                assertFalse(baseline.journalPresent()[successorJournalOffset + index],
                        "hard fixture prerequisite: no baseline successor journal coverage, byte=" + index);
            }
            assertFalse(Arrays.equals(Arrays.copyOfRange(baseline.pixels(), 64, 64 + 3 * 64),
                            Arrays.copyOfRange(expectedPixels, 0, 3 * 64)),
                    "the first physical prefix must differ from the actual loaded baseline");
            int successorOffset = (SUCCESSOR_TILE - FIRST_OBSERVED_TILE) * 64;
            assertFalse(Arrays.equals(Arrays.copyOfRange(baseline.pixels(), successorOffset, successorOffset + 2 * 64),
                            Arrays.copyOfRange(successorPixels, 0, 2 * 64)),
                    "an illicit two-pattern successor write must be physically distinguishable");

            // No restoration, active-head seed or direct pattern write establishes these states.
            // Retain every observation before asserting semantics: a missing first Init must
            // not hide the independent append, physical-write and successor questions.
            plc.appendRaw(RAW_PLC);
            var appended = observe(level, plc, journal);
            stepOrdinary(fixture, context, world, level, plc, journal);
            var firstFrame = observe(level, plc, journal);
            var publications = new ArrayList<Observation>();
            for (int completed = 3; completed <= 69; completed += 3) {
                stepOrdinary(fixture, context, world, level, plc, journal);
                publications.add(observe(level, plc, journal));
            }

            // At the intended native boundary the first head has one pattern left.
            // This append is another declared synthetic producer, not a seeded head.
            plc.appendRaw(SUCCESSOR_RAW_PLC);
            var successorAppended = observe(level, plc, journal);
            // Existing public component inlets expose the actual physical game
            // dispatch before the loop-tail Init. The real token supplies ownership
            // and queued-DMA ordering; a bare PLC readiness claim is not the writer.
            var finalBoundary = context.plcFrameLifecycle().runLogicalIteration(() -> { }, frame -> {
                assertTrue(frame.claim(ORDINARY), "the declared component boundary must own its actual token");
                var frameContext = LevelFrameContext.from(context);
                assertSame(level, frameContext.levelManager().getCurrentLevel());
                assertSame(journal, frameContext.kosinskiModuleQueue());
                LevelFrameStep.dispatchGameVBlank(frameContext, frame);
                var completed = observe(level, plc, journal);
                // Re-dispatching the SAME token cannot create a second service or
                // prepare the waiting successor. This is not another VBlank/frame.
                LevelFrameStep.dispatchGameVBlank(frameContext, frame);
                var duplicateDispatch = observe(level, plc, journal);
                frame.prepareAfterLoop(ORDINARY);
                return List.of(completed, duplicateDispatch, observe(level, plc, journal));
            });
            var lastPattern = finalBoundary.get(0);
            var duplicateDispatch = finalBoundary.get(1);
            var successorPrepared = finalBoundary.get(2);
            requireBindings(fixture, context, world, level, plc, journal);

            var full = entry(PATTERNS);
            var successor = new NemesisPlcQueueSnapshot.Entry(
                    SUCCESSOR_SOURCE, SUCCESSOR_TILE, SUCCESSOR_PATTERNS, SUCCESSOR_PATTERNS);
            var checks = new ArrayList<Executable>();
            checks.add(() -> assertAll("append changes only the waiting FIFO",
                    () -> assertEquals(new NemesisPlcQueueSnapshot(null, List.of(full)), appended.queue()),
                    () -> assertImage(baseline, appended, expectedPixels, 0),
                    () -> assertEquals(baseline.wholeJournal(), appended.wholeJournal())));
            checks.add(() -> assertAll("first ordinary opening cannot consume an unprepared head",
                    () -> assertEquals(new NemesisPlcQueueSnapshot(full, List.of()), firstFrame.queue()),
                    () -> assertImage(baseline, firstFrame, expectedPixels, 0),
                    () -> assertJournalPrefix(baseline, firstFrame, decoded, 0)));
            for (int index = 0; index < publications.size(); index++) {
                int completed = (index + 1) * 3;
                var observed = publications.get(index);
                checks.add(() -> assertAll("ordinary confirmed prefix=" + completed,
                        () -> assertImage(baseline, observed, expectedPixels, completed),
                        () -> assertJournalPrefix(baseline, observed, decoded, completed),
                        () -> assertEquals(new NemesisPlcQueueSnapshot(entry(PATTERNS - completed), List.of()),
                                observed.queue(), "remaining progress follows the independently checked physical prefix")));
            }
            var beforeSuccessor = publications.getLast();
            checks.add(() -> assertAll("successor append preserves the last-pattern head",
                    () -> assertEquals(new NemesisPlcQueueSnapshot(entry(1), List.of(successor)), successorAppended.queue()),
                    () -> assertImage(baseline, successorAppended, expectedPixels, 69),
                    () -> assertEquals(beforeSuccessor.wholeJournal(), successorAppended.wholeJournal())));
            checks.add(() -> assertAll("final service writes one pattern without spending spare budget on the successor",
                    () -> assertImage(baseline, lastPattern, expectedPixels, PATTERNS),
                    () -> assertJournalPrefix(baseline, lastPattern, decoded, PATTERNS),
                    () -> assertEquals(new NemesisPlcQueueSnapshot(null, List.of(successor)), lastPattern.queue())));
            checks.add(() -> assertAll("the same physical token cannot dispatch a second service or prepare its successor",
                    () -> assertEquals(lastPattern.queue(), duplicateDispatch.queue()),
                    () -> assertImage(baseline, duplicateDispatch, expectedPixels, PATTERNS),
                    () -> assertEquals(lastPattern.wholeJournal(), duplicateDispatch.wholeJournal())));
            checks.add(() -> assertAll("only the next explicit Init prepares the successor, without pixels",
                    () -> assertEquals(new NemesisPlcQueueSnapshot(successor, List.of()), successorPrepared.queue()),
                    () -> assertImage(baseline, successorPrepared, expectedPixels, PATTERNS),
                    () -> assertEquals(duplicateDispatch.wholeJournal(), successorPrepared.wholeJournal())));
            assertAll("ordinary Nemesis preparation, physical publication and independent FIFO progress", checks);
        }
    }

    private static NemesisPlcQueueSnapshot.Entry entry(int remaining) {
        return new NemesisPlcQueueSnapshot.Entry(SOURCE, DESTINATION_TILE, PATTERNS, remaining);
    }

    /** Test-local carrier only; the actual session owns world/load/title lifetime. */
    private record PublicationInlet(GameplayModeContext gameplayMode, HeadlessTestRunner runner,
                                    AbstractPlayableSprite sprite) { }

    private static void stepOrdinary(PublicationInlet fixture, GameplayModeContext context,
                                     WorldSession world, Sonic3kLevel level,
                                     Sonic3kLevelTitlePlcService plc, KosinskiModuleQueue journal) {
        requireBindings(fixture, context, world, level, plc, journal);
        int before = GameServices.level().getFrameCounter() & 0xFFFF;
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false),
                "the real ordinary GameLoop must own every declared publication frame");
        assertEquals((before + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF);
        requireBindings(fixture, context, world, level, plc, journal);
    }

    private static void requireBindings(PublicationInlet fixture, GameplayModeContext context,
                                        WorldSession world, Sonic3kLevel level,
                                        Sonic3kLevelTitlePlcService plc, KosinskiModuleQueue journal) {
        assertSame(context, fixture.gameplayMode());
        assertSame(context, SessionManager.getCurrentGameplayMode());
        assertSame(world, SessionManager.getCurrentWorldSession());
        assertSame(world, GameServices.level().getWorldSession());
        assertSame(level, world.getCurrentLevel());
        assertSame(level, GameServices.level().getCurrentLevel());
        assertEquals(Sonic3kZoneIds.ZONE_CNZ, GameServices.level().getCurrentZone());
        assertEquals(0, GameServices.level().getCurrentAct());
        assertSame(fixture.sprite(), GameServices.sprites().getMainPlayable());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        assertFalse(fixture.sprite().getDead());
        assertSame(plc, WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class));
        var objects = GameServices.level().getObjectManager();
        assertNotNull(objects, "the actual loaded world's ObjectManager is required");
        var services = objects.getObjectServices();
        assertNotNull(services, "the actual manager's injected services are required");
        assertSame(level, services.currentLevel());
        assertSame(plc, services.gameService(Sonic3kLevelTitlePlcService.class));
        assertSame(journal, context.getKosinskiModuleQueue());
        assertSame(journal, services.kosinskiModuleQueue());
    }

    private static Observation observe(Sonic3kLevel level, Sonic3kLevelTitlePlcService plc,
                                       KosinskiModuleQueue journal) {
        var refs = new Pattern[OBSERVED_PATTERNS];
        var pixels = new byte[OBSERVED_PATTERNS * 64];
        for (int tile = 0; tile < refs.length; tile++) {
            refs[tile] = level.getPattern(FIRST_OBSERVED_TILE + tile);
            assertNotNull(refs[tile], "every destination/guard Pattern must actually exist");
            refs[tile].copyInto(pixels, tile * 64);
        }
        var snapshot = journal.capture();
        var present = new boolean[OBSERVED_PATTERNS * 32];
        var bytes = new byte[present.length];
        int firstAddress = FIRST_OBSERVED_TILE * 32;
        for (var write : snapshot.appliedWrites()) {
            byte[] data = write.data();
            for (int index = 0; index < data.length; index++) {
                long local = (long) write.destinationVramBytes() + index - firstAddress;
                if (local >= 0 && local < bytes.length) {
                    assertFalse(present[(int) local], "journal ranges must not overlap");
                    present[(int) local] = true;
                    bytes[(int) local] = data[index];
                }
            }
        }
        return new Observation(plc.capture(), refs, pixels, snapshot, present, bytes);
    }

    private static byte[] expandNibbles(byte[] decoded) {
        var pixels = new byte[decoded.length * 2];
        for (int index = 0; index < decoded.length; index++) {
            pixels[index * 2] = (byte) ((decoded[index] & 0xFF) >>> 4);
            pixels[index * 2 + 1] = (byte) (decoded[index] & 0x0F);
        }
        return pixels;
    }

    private static void assertImage(Observation baseline, Observation observed,
                                    byte[] expectedPixels, int publishedPatterns) {
        int replacedRefs = 0;
        int differingPixels = 0;
        int firstMismatch = -1;
        for (int tile = 0; tile < OBSERVED_PATTERNS; tile++) {
            if (baseline.refs()[tile] != observed.refs()[tile]) replacedRefs++;
        }
        for (int index = 0; index < observed.pixels().length; index++) {
            int archiveIndex = index - 64;
            byte expected = archiveIndex >= 0 && archiveIndex < publishedPatterns * 64
                    ? expectedPixels[archiveIndex] : baseline.pixels()[index];
            if (expected != observed.pixels()[index]) {
                differingPixels++;
                if (firstMismatch < 0) firstMismatch = index;
            }
        }
        assertEquals(0, replacedRefs, "publication must mutate the original destination/guard Pattern objects");
        assertEquals(0, differingPixels, "all prefix, suffix and adjacent-guard pixels; first mismatch local pixel="
                + firstMismatch + ", published patterns=" + publishedPatterns);
    }

    private static void assertJournalPrefix(Observation baseline, Observation observed,
                                            byte[] decoded, int publishedPatterns) {
        int mismatches = 0;
        int firstMismatch = -1;
        for (int index = 0; index < observed.journalBytes().length; index++) {
            int archiveIndex = index - 32;
            boolean published = archiveIndex >= 0 && archiveIndex < publishedPatterns * 32;
            boolean expectedPresence = published || baseline.journalPresent()[index];
            byte expected = published ? decoded[archiveIndex] : baseline.journalBytes()[index];
            if (expectedPresence != observed.journalPresent()[index]
                    || expectedPresence && expected != observed.journalBytes()[index]) {
                mismatches++;
                if (firstMismatch < 0) firstMismatch = index;
            }
        }
        assertEquals(0, mismatches, "the registered physical journal must confirm the exact prefix and preserve"
                + " the suffix/guards; first mismatch local byte=" + firstMismatch);
    }

    private record Observation(NemesisPlcQueueSnapshot queue, Pattern[] refs, byte[] pixels,
                               KosinskiModuleQueue.Snapshot wholeJournal,
                               boolean[] journalPresent, byte[] journalBytes) { }
}
