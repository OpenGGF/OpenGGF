package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.level.Level;
import com.openggf.level.LevelContinuationCarry;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native loc_7F310/StartNewLevel/LoadLevelLoadBlock2, independent of route movies. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kNativeContinuationDefinitionResidency {
    private static final int DEZ_DEFINITIONS = 0x1D5068;
    private static final int FINAL_DEFINITIONS = 0x1DC24E;

    @AfterEach
    void reset() {
        TestEnvironment.resetAll();
    }

    @Test
    void actualFreshContinuationOverwritesOnlyItsRomDestinationPrefix() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(11, 1).build();
        var manager = GameServices.level();
        byte[] source = decode(DEZ_DEFINITIONS);
        byte[] destination = decode(FINAL_DEFINITIONS);
        assertEquals(0x8000, source.length, "DEZ ROM producer, not padded capacity");
        assertEquals(0x0E80, destination.length, "final ROM producer's actual write extent");
        assertDefinitions(source, manager.getCurrentLevel());
        // Declared continuation inlet: the same public production request used by
        // loc_7F310, followed by GameLoop.doZoneAct's actual fresh-load boundary.
        // No boss phase, timer, health, player position or slot is injected.
        LevelContinuationCarry.request(manager, 23, 0, 0, 0, null);
        assertTrue(manager.consumeZoneActRequest());
        manager.loadZoneAndActAtFreshTitleCardBoundary(manager.getRequestedZone(), manager.getRequestedAct());
        assertFalse(manager.hasPendingFreshLevelTransitionBoundary(), "native Act3 bypasses the title loop");
        assertEquals(23, manager.getCurrentZone());
        assertEquals(0, manager.getCurrentAct());
        var level = manager.getCurrentLevel();
        assertTrue(level.getBlockCount() > 0xB8, "short destination write must preserve real DEZ tail definitions");
        for (int offset = 0; offset < source.length; offset += 2) {
            int expected = offset < destination.length ? word(destination, offset) : word(source, offset);
            assertEquals(expected, descriptor(level, offset), "native definition byte offset " + offset);
        }
        assertEquals(word(source, 0x4850), manager.getChunkDescAt((byte) 0, 0x2E82, 0x00D5).get());
        assertEquals(word(source, 0x5C02), manager.getChunkDescAt((byte) 0, 0x299C, 0x0109).get());
        assertEquals(word(source, 0x4850), manager.getChunkDescAt((byte) 0, 0x12E82, 0x100D5).get(),
                "native word coordinates, without visual-width wrapping");
    }

    @Test
    void actualFreshContinuationRetainsKnownPatternTailWithTargetCollision() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(11, 1).build();
        var manager = GameServices.level();
        var rom = TestEnvironment.currentRom();
        byte[] primary = decode(0x1D1DD6);
        byte[] secondary = decode(0x1D7E3A);
        byte[] previous = new byte[primary.length + secondary.length];
        System.arraycopy(primary, 0, previous, 0, primary.length);
        System.arraycopy(secondary, 0, previous, primary.length, secondary.length);
        byte[] target = decode(0x1D899C);
        assertEquals(0x15E0, primary.length);
        assertEquals(0x0210, secondary.length);
        assertEquals(766 * 8, previous.length, "actual DEZ2 writers, never padded capacity");
        assertEquals(414 * 8, target.length, "actual final-scene Block_table overwrite");
        for (int i = 0; i < previous.length / 8; i++)
            assertArrayEquals(patternWords(previous, i), patterns(manager.getCurrentLevel(), i),
                    "known predecessor group " + i);
        assertNull(knownPatternBytes(manager.getCurrentLevel(), 766),
                "no native writer is known beyond the766-group source, even if capacity exists");
        int targetCollision = rom.read32BitAddr(
                com.openggf.game.sonic3k.constants.Sonic3kConstants.SOLID_INDEXES_ADDR + 46 * 4) & 0xFFFFFF;
        assertEquals(0x09CFC0, targetCollision, "LoadSolids owns current interleaved indices");
        assertArrayEquals(new int[]{0xD08E, 0xD08F, 0xC882, 0xC881}, patternWords(previous, 0x1B0));
        assertEquals(0xFF, manager.getCurrentLevel().getChunk(0x1B0).getSolidTileIndex());
        assertEquals(0x68, rom.readByte(targetCollision + 0x1B0 * 2) & 0xFF);
        assertEquals(0x68, rom.readByte(targetCollision + 0x1B0 * 2 + 1) & 0xFF);

        // Same public native continuation/fresh-load inlet as the128-byte test.
        LevelContinuationCarry.request(manager, 23, 0, 0, 0, null);
        assertTrue(manager.consumeZoneActRequest());
        manager.loadZoneAndActAtFreshTitleCardBoundary(23, 0);
        var level = manager.getCurrentLevel();
        for (int i = 0; i < target.length / 8; i++) {
            assertArrayEquals(patternWords(target, i), patterns(level, i), "target-written group " + i);
            assertTargetCollision(level, i, targetCollision);
        }
        assertArrayEquals(patternWords(previous, 0x1B0), patterns(level, 0x1B0),
                "retained128 descriptors need the known native16 tail, not zero capacity placeholders");
        assertTargetCollision(level, 0x1B0, targetCollision);
        for (int i = target.length / 8; i < previous.length / 8; i++) {
            assertArrayEquals(patternWords(previous, i), patterns(level, i), "retained known group " + i);
            assertArrayEquals(java.util.Arrays.copyOfRange(previous, i * 8, i * 8 + 8),
                    knownPatternBytes(level, i), "retained known provenance " + i);
            assertTargetCollision(level, i, targetCollision);
        }
        assertNull(knownPatternBytes(level, 766), "unknown tail is not a native zero write");
        assertNull(knownPatternBytes(level, 767), "fixed buffer capacity cannot invent provenance");
    }

    @Test
    void coldManualAndMismatchedLoadsDoNotBorrowAnArbitraryLiveLevel() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
        assertEquals(0x0E80 / 128, GameServices.level().getCurrentLevel().getBlockCount());
        HeadlessTestFixture.builder().withZoneAndAct(11, 1).build();
        var manager = GameServices.level();
        manager.loadZoneAndAct(23, 0);
        assertEquals(0x0E80 / 128, manager.getCurrentLevel().getBlockCount(), "ordinary manual load is not a continuation");
        HeadlessTestFixture.builder().withZoneAndAct(11, 1).build();
        manager = GameServices.level();
        // REQUESTED alone cannot authorize either an ordinary build or captured
        // preparation; only the matched synchronous LOADING path may borrow RAM.
        LevelContinuationCarry.request(manager, 23, 0, 0, 0, null);
        var prepared = ((com.openggf.level.resources.PreparableLevelLoader) manager.getGame())
                .prepareLevelBuildTask(23 * 2, null).call();
        assertEquals(0x0E80 / 128, prepared.level().getBlockCount());
        assertEquals(11, manager.getCurrentZone(), "preparation does not install its target");
        LevelContinuationCarry.request(manager, 22, 0, 0, 0, null);
        assertTrue(manager.consumeZoneActRequest());
        manager.loadZoneAndAct(23, 0);
        assertEquals(0x0E80 / 128, manager.getCurrentLevel().getBlockCount(), "nonmatching request cannot authorize residency");
        assertFalse(LevelContinuationCarry.bypassesInitialPresentation(manager));
    }

    @Test
    void retainedDefinitionsSurviveTwoRecreatedWorldForwardContinuations() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(11, 1).build();
        var manager = GameServices.level();
        byte[] source = decode(DEZ_DEFINITIONS);
        byte[] previous16 = decode(0x1D1DD6);
        int targetCollision = TestEnvironment.currentRom().read32BitAddr(
                com.openggf.game.sonic3k.constants.Sonic3kConstants.SOLID_INDEXES_ADDR + 46 * 4) & 0xFFFFFF;
        LevelContinuationCarry.request(manager, 23, 0, 0, 0, null);
        assertTrue(manager.consumeZoneActRequest());
        manager.loadZoneAndActAtFreshTitleCardBoundary(23, 0);
        fixture.stepIdleFrames(1);
        // Capture only after the intentional fresh-load history reset. This is
        // an isolated destination continuation, not a replay across that reset.
        assertFalse(manager.hasPendingFreshLevelTransitionBoundary());
        manager.getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        assertArrayEquals(patternWords(previous16, 0x1B0), patterns(manager.getCurrentLevel(), 0x1B0));
        assertTargetCollision(manager.getCurrentLevel(), 0x1B0, targetCollision);
        assertNull(knownPatternBytes(manager.getCurrentLevel(), 766));
        var before = registry.capture();
        fixture.stepIdleFrames(3);
        var after = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(before);
            sameWorld(before, registry.capture());
            assertEquals(word(source, 0x4850), descriptor(manager.getCurrentLevel(), 0x4850));
            assertEquals(word(source, 0x5C02), descriptor(manager.getCurrentLevel(), 0x5C02));
            assertArrayEquals(patternWords(previous16, 0x1B0), patterns(manager.getCurrentLevel(), 0x1B0));
            assertTargetCollision(manager.getCurrentLevel(), 0x1B0, targetCollision);
            assertNull(knownPatternBytes(manager.getCurrentLevel(), 766));
            fixture.stepIdleFrames(3);
            sameWorld(after, registry.capture());
            assertArrayEquals(patternWords(previous16, 0x1B0), patterns(manager.getCurrentLevel(), 0x1B0));
            assertTargetCollision(manager.getCurrentLevel(), 0x1B0, targetCollision);
            assertNull(knownPatternBytes(manager.getCurrentLevel(), 766));
        }
    }

    private static int[] patternWords(byte[] bytes, int index) {
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) result[i] = word(bytes, index * 8 + i * 2);
        return result;
    }

    private static int[] patterns(Level level, int index) {
        int[] result = new int[4];
        for (int i = 0; i < 4; i++)
            result[i] = level.getChunk(index).getPatternDesc(i & 1, i >>> 1).get();
        return result;
    }

    private static void assertTargetCollision(Level level, int index, int address) throws Exception {
        var rom = TestEnvironment.currentRom();
        assertEquals(rom.readByte(address + index * 2) & 0xFF,
                level.getChunk(index).getSolidTileIndex(), "target primary " + index);
        assertEquals(rom.readByte(address + index * 2 + 1) & 0xFF,
                level.getChunk(index).getSolidTileAltIndex(), "target secondary " + index);
    }

    /** Read-only access to the existing private immutable provenance value; no new runtime API. */
    private static byte[] knownPatternBytes(Level level, int index) throws Exception {
        var capture = level.getClass().getDeclaredMethod("residentChunkPatterns");
        capture.setAccessible(true);
        Object seed = capture.invoke(level);
        var entry = seed.getClass().getDeclaredMethod("entry", int.class);
        entry.setAccessible(true);
        return (byte[]) entry.invoke(seed, index);
    }

    private static byte[] decode(int address) throws Exception {
        return new ResourceLoader(TestEnvironment.currentRom()).loadWithOverlays(
                List.of(LoadOp.kosinskiBase(address)), 0x8000);
    }

    private static int word(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static int descriptor(Level level, int offset) {
        int within = offset & 127;
        return level.getBlock(offset >>> 7).getChunkDesc((within >>> 1) & 7, within >>> 4).get();
    }

    private static void assertDefinitions(byte[] expected, Level level) {
        assertEquals(expected.length / 128, level.getBlockCount());
        for (int offset = 0; offset < expected.length; offset += 2)
            assertEquals(word(expected, offset), descriptor(level, offset), "ROM source byte offset " + offset);
    }

    private static void sameWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet())
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
    }
}
