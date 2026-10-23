package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Native pattern provenance follows the existing geometry COW/recreation state. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kResidentChunkPatterns {
    @Test
    void immutableKnownBytesExcludePlaceholdersAndSurviveValueAndSceneRestores() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0).build();
        var level = current();
        var original = level.residentChunkPatterns();
        assertEquals(688, original.extent());
        byte[] original129 = original.entry(129);
        original.entry(129)[0] = 0; // The returned value cannot mutate the captured seed.
        assertArrayEquals(original129, original.entry(129));
        level.applyChunkOverlay(new byte[8], 717 * 8, false);
        assertNull(level.residentChunkPatterns().entry(688), "capacity has no native pattern writer");
        assertArrayEquals(new byte[8], level.residentChunkPatterns().entry(717),
                "an actual zero write is known, unlike a zero-valued placeholder");
        int[][] opaqueSnapshot = level.snapshotChunks();
        level.applyChunkOverlay(original129, 688 * 8, false);
        assertNotNull(level.residentChunkPatterns().entry(688));
        level.restoreChunks(opaqueSnapshot);
        assertNull(level.residentChunkPatterns().entry(688), "opaque value restore retains unknown provenance");
        applyCurrentRomSceneCollision();
        assertNull(level.residentChunkPatterns().entry(688), "LoadSolids does not produce pattern words");
        assertArrayEquals(original129, level.residentChunkPatterns().entry(129));
        level.applyChunkOverlay(original129, 768 * 8, false);
        assertEquals(718, level.residentChunkPatterns().extent(),
                "Block_table's1800-byte bound applies to actual pattern writes, not expanded capacity");
        assertNull(level.residentChunkPatterns().entry(768));
        assertEquals(688, original.extent(), "later writes cannot change the immutable predecessor");
    }

    @Test
    void opaqueRestoreRetiresTemporaryTailWithoutChangingExternalPrefixWrites() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0).build();
        fixture.stepIdleFrames(1);
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        var level = current();
        int[][] saved = level.snapshotChunks();
        assertEquals(688, saved.length);
        var registry = fixture.gameplayMode().getRewindRegistry();
        level.applyChunkOverlay(new byte[8], 717 * 8, false);
        var temporaryChunks = level.chunksReference();
        var temporaryWorld = registry.capture();
        assertNotNull(level.residentChunkPatterns().entry(717));
        assertNull(level.residentChunkPatterns().entry(688));
        level.restoreChunks(saved);
        assertEquals(688, level.getChunkCount(), "an owned opaque snapshot restores its actual saved extent");
        assertEquals(688, level.residentChunkPatterns().extent());
        assertNull(level.residentChunkPatterns().entry(717), "temporary tail was absent in the saved state");
        assertEquals(718, temporaryChunks.length, "restore cannot mutate the captured temporary geometry");
        var restoredWorld = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(temporaryWorld);
            assertWorld(temporaryWorld, registry.capture());
            assertNull(current().residentChunkPatterns().entry(688), "saved unknown holes stay unknown");
            assertNotNull(current().residentChunkPatterns().entry(717));
            current().restoreChunks(saved);
            assertEquals(688, current().getChunkCount());
            assertNull(current().residentChunkPatterns().entry(717));
            assertWorld(restoredWorld, registry.capture());
        }
        registry.restore(temporaryWorld);
        // A supplied external full-six-word prefix is a different existing inlet:
        // it writes those words, without claiming to restore the opaque geometry extent.
        current().restoreChunks(java.util.Arrays.copyOf(saved, saved.length));
        assertEquals(718, current().getChunkCount());
        assertNotNull(current().residentChunkPatterns().entry(717));
        assertNull(current().residentChunkPatterns().entry(688));
    }

    @Test
    void sparseKnownPatternsReplayAfterTwoForcedWholeWorldRecreations() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0).build();
        fixture.stepIdleFrames(1);
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        var level = current();
        level.applyChunkOverlay(new byte[8], 717 * 8, false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        var beforeSeed = level.residentChunkPatterns();
        var beforeChunks = level.chunksReference();
        mutateAndAdvance(fixture);
        var after = registry.capture();
        assertNull(beforeSeed.entry(688));
        assertArrayEquals(new byte[8], beforeSeed.entry(717));
        assertArrayEquals(new int[6], beforeChunks[688].saveState(),
                "forward full-state writes cannot alter the captured placeholder");
        assertNotNull(current().residentChunkPatterns().entry(688));
        assertNotNull(current().residentChunkPatterns().entry(689));
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(before);
            assertWorld(before, registry.capture());
            assertSeedEquals(beforeSeed, current().residentChunkPatterns());
            assertNull(current().residentChunkPatterns().entry(688));
            assertNull(current().residentChunkPatterns().entry(689));
            mutateAndAdvance(fixture);
            assertWorld(after, registry.capture());
            assertArrayEquals(beforeSeed.entry(129), current().residentChunkPatterns().entry(688));
            assertArrayEquals(new byte[8], current().residentChunkPatterns().entry(689));
        }
    }

    private static void mutateAndAdvance(HeadlessTestFixture fixture) throws Exception {
        var level = current();
        level.applyChunkOverlay(level.residentChunkPatterns().entry(129), 688 * 8, false);
        var manager = GameServices.level();
        var context = new LayoutMutationContext(LevelMutationSurface.forLevel(level), manager::applyMutationEffects);
        // A complete mutation state is a real four-word producer, even when all zero.
        GameServices.zoneLayoutMutationPipeline().applyImmediately(c -> {
            c.surface().restoreChunkState(689, new int[6]);
            return MutationEffects.redrawAllTilemaps();
        }, context);
        applyCurrentRomSceneCollision();
        fixture.stepIdleFrames(2);
    }

    private static void applyCurrentRomSceneCollision() throws Exception {
        var rom = GameServices.rom().getRom();
        // A separate LoadSolids selection deliberately differs from construction;
        // it must not turn unwritten pattern placeholders into known bytes.
        current().applySceneCollisionIndices(rom.readBytes(0x27A761, 0x600),
                rom.readBytes(0x27A761 + 0x600, 0x600));
        assertEquals(8, current().getChunk(129).getSolidTileIndex());
    }

    private static Sonic3kLevel current() {
        return (Sonic3kLevel) GameServices.level().getCurrentLevel();
    }

    private static void assertSeedEquals(Sonic3kLevel.ResidentChunkPatterns expected,
                                          Sonic3kLevel.ResidentChunkPatterns actual) {
        assertEquals(expected.extent(), actual.extent());
        for (int i = 0; i < expected.extent(); i++) assertArrayEquals(expected.entry(i), actual.entry(i));
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
