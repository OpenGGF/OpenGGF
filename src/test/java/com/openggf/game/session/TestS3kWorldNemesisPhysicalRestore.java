package com.openggf.game.session;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.game.GameMode;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.level.Pattern;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.level.resources.KosModuleTransferAccess;
import com.openggf.level.resources.PlcParser;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Component composition controls for the ordinary Nemesis publication owner.
 * Origin: 2026-10-18 world-owned ordinary Nemesis source candidate.
 * One real CNZ1 solo-Knuckles load completes its real title before synthetic
 * raw-descriptor and one explicit DMA-callback ingress. No scene/player/history
 * seeds, queue clearing or readiness warmup establish the inlet. Full registered
 * snapshots are checked, not a claim of a native route, GPU VRAM or H-int timing.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kWorldNemesisPhysicalRestore {
    @AfterEach void tearDown() { TestEnvironment.resetAll(); }

    @Test
    void fullRegistryReplaysPixelsAndRecipesTwiceAndReopenCannotBorrowTheOldTarget() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 3, 0, settings);
            var context = SessionManager.getCurrentGameplayMode();
            var world = context.getWorldSession();
            var manager = context.getLevelManager();
            var objects = manager.getObjectManager();
            var player = session.player();
            var level = assertInstanceOf(Sonic3kLevel.class, world.getCurrentLevel());
            var plc = WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class);
            var journal = context.getKosinskiModuleQueue();
            assertTrue(manager.hasPendingInitialProcessSpritesPass());
            assertTrue(manager.isTitleCardRequested());
            session.step(null);
            assertFalse(manager.hasPendingInitialProcessSpritesPass());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertTrue(manager.isTitleCardRequested());
            session.step(null);
            assertEquals(GameMode.TITLE_CARD, session.loop().getCurrentGameMode());
            int safety = 0;
            while (session.loop().getCurrentGameMode() == GameMode.TITLE_CARD && safety++ < 600) {
                assertSame(context, SessionManager.getCurrentGameplayMode());
                assertSame(objects, manager.getObjectManager());
                assertSame(player, session.player());
                session.step(null);
            }
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertSame(world, manager.getWorldSession());
            assertSame(level, manager.getCurrentLevel());
            assertSame(plc, WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class));
            assertTrue(context.getSpriteManager().getRegisteredSidekicks().isEmpty());
            assertFalse(plc.isBusy(), "only actual production title release establishes the empty FIFO");

            // Independent descriptor/header operands; no prepared-head or queue state is installed.
            var rom = world.getDataSource().rom().orElseThrow();
            int source = rom.read32BitAddr(0x083D76) & 0xFFFFFF;
            int destination = rom.read16BitAddr(0x083D7A) & 0xFFFF;
            int total = rom.read16BitAddr(source) & 0x7FFF;
            assertTrue(total > 6);
            Pattern[] identities = new Pattern[total];
            for (int index = 0; index < total; index++)
                identities[index] = level.getPattern(destination / 32 + index);
            plc.appendRaw(0x083D74);
            var registry = context.getRewindRegistry();
            var before = registry.capture();
            byte[] beforePixels = pixels(level, destination / 32, total);
            assertEquals(plc.capture(), before.get(plc.key()));
            assertEquals(plc.recipeState().capture(), before.get(plc.recipeState().key()));
            assertEquals(journal.capture(), before.get(journal.key()));

            componentIteration(context);
            assertEquals(total, plc.capture().activeEntry().remainingPatterns(), "Init writes no pixels");
            assertArrayEquals(beforePixels, pixels(level, destination / 32, total));
            componentIteration(context);
            assertEquals(total - 3, plc.capture().activeEntry().remainingPatterns());
            var after = registry.capture();
            byte[] afterPixels = pixels(level, destination / 32, total);
            assertFalse(Arrays.equals(beforePixels, afterPixels), "actual publication must be distinguishable");
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(before);
                same(before, registry.capture());
                assertArrayEquals(beforePixels, pixels(level, destination / 32, total));
                componentIteration(context);
                componentIteration(context);
                same(after, registry.capture());
                assertArrayEquals(afterPixels, pixels(level, destination / 32, total));
                for (int index = 0; index < total; index++)
                    assertSame(identities[index], level.getPattern(destination / 32 + index));
            }

            var progress = plc.capture();
            var recipes = plc.recipeState().capture();
            var physical = journal.capture();
            plc.publishOrdinaryPatterns(null, journal);
            assertEquals(progress, plc.capture());
            assertEquals(recipes, plc.recipeState().capture());
            assertEquals(physical, journal.capture());
            var foreignJournal = new KosinskiModuleQueue();
            var foreignBefore = foreignJournal.capture();
            assertThrows(IllegalArgumentException.class,
                    () -> plc.publishOrdinaryPatterns(manager, foreignJournal));
            assertEquals(progress, plc.capture());
            assertEquals(physical, journal.capture());
            assertEquals(foreignBefore, foreignJournal.capture());

            // Explicit synthetic queued-DMA callback in the real coordinator:
            // it writes the exact NEXT Nemesis pattern range before the physical
            // owner. This isolates ordering without inventing an AniPLC producer.
            byte[] decoded = PlcParser.decompressEntryRaw(rom,
                    new PlcParser.PlcEntry(source, destination / 32));
            byte[] nextThree = Arrays.copyOfRange(decoded, 3 * 32, 6 * 32);
            byte[] dma = new byte[nextThree.length];
            Arrays.fill(dma, (byte) 0x55);
            assertFalse(Arrays.equals(dma, nextThree));
            context.plcFrameLifecycle().runLogicalIteration(() -> { }, frame -> {
                assertTrue(frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
                frame.dispatchGameVBlank(() -> assertTrue(journal.applyImmediateDma(destination + 3 * 32, dma)),
                        manager, journal);
                var completed = registry.capture();
                byte[] completedPixels = pixels(level, destination / 32, total);
                assertFalse(frame.dispatchGameVBlank(() -> fail("duplicate token cannot run DMA"),
                        manager, journal));
                same(completed, registry.capture());
                assertArrayEquals(completedPixels, pixels(level, destination / 32, total));
                frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
                return null;
            });
            byte[] published = new byte[6 * 32];
            for (int index = 0; index < 6; index++)
                pack(level.getPattern(destination / 32 + index), published, index * 32);
            assertArrayEquals(Arrays.copyOf(decoded, published.length), published,
                    "post-DMA Nemesis, not the overlapping callback, owns the final complete prefix");
            assertEquals(total - 6, plc.capture().activeEntry().remainingPatterns());

            var retained = plc.capture();
            var retainedRecipes = plc.recipeState().capture();
            var oldJournal = journal;
            byte[] oldLevelPixels = pixels(level, destination / 32, total);
            var reopened = SessionManager.reopenGameplaySession(
                    com.openggf.game.timing.HardwareReadinessAdmissionPolicy.LIVE);
            GameplaySessionFactory.attachManagers(reopened, EngineServices.current());
            assertSame(world, reopened.getWorldSession());
            assertNotSame(context, reopened);
            assertSame(plc, WorldPlcLifecycleAccess.require(world, Sonic3kLevelTitlePlcService.class));
            assertEquals(retained, plc.capture());
            assertEquals(retainedRecipes, plc.recipeState().capture());
            var newJournal = reopened.getKosinskiModuleQueue();
            assertNotSame(oldJournal, newJournal);
            assertTrue(newJournal.capture().appliedWrites().isEmpty());
            // Reopened managers have no materialized object/pattern capability
            // yet. They cannot borrow the preceding context's journal.
            plc.publishOrdinaryPatterns(reopened.getLevelManager(), oldJournal);
            assertEquals(retained, plc.capture());
            assertTrue(newJournal.capture().appliedWrites().isEmpty());
            // Public resource-load component only: the reopened sprite manager
            // has no playable roster and we execute no gameplay/title frames on
            // it. This tests current level/journal binding, not player respawn.
            reopened.getLevelManager().loadZoneAndActWithTitleCard(3, 0);
            var replacement = assertInstanceOf(Sonic3kLevel.class, reopened.getLevelManager().getCurrentLevel());
            assertNotSame(level, replacement);
            assertArrayEquals(oldLevelPixels, pixels(level, destination / 32, total),
                    "loading a replacement cannot repaint the prior level's retained patterns");
            var services = reopened.getLevelManager().getObjectManager().getObjectServices();
            Sonic3kPlcLoader.bindRuntimePatternDmaTarget(newJournal, services);
            Pattern replacementIdentity = replacement.getPattern(destination / 32);
            byte[] one = Arrays.copyOf(decoded, 32);
            assertTrue(KosModuleTransferAccess.publishPattern(newJournal, destination, one),
                    "declared physical-writer component ingress targets the new manager only");
            assertSame(replacementIdentity, replacement.getPattern(destination / 32));
            byte[] actual = new byte[32]; pack(replacementIdentity, actual, 0);
            assertArrayEquals(one, actual);
            assertArrayEquals(oldLevelPixels, pixels(level, destination / 32, total));
            assertEquals(1, reopened.getRewindRegistry().capture().entries().keySet().stream()
                    .filter(plc.recipeState().key()::equals).count());
        }
    }

    private static void componentIteration(GameplayModeContext context) {
        context.plcFrameLifecycle().runLogicalIteration(() -> { }, frame -> {
            assertTrue(frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
            LevelFrameStep.dispatchGameVBlank(LevelFrameContext.from(context), frame);
            frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
            return null;
        });
    }

    private static byte[] pixels(Sonic3kLevel level, int first, int count) {
        byte[] result = new byte[count * 32];
        for (int index = 0; index < count; index++) pack(level.getPattern(first + index), result, index * 32);
        return result;
    }

    private static void pack(Pattern pattern, byte[] output, int offset) {
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x += 2)
            output[offset++] = (byte) ((pattern.getPixel(x, y) << 4) | pattern.getPixel(x + 1, y));
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet())
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
    }
}
