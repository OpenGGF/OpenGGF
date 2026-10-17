package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import com.openggf.physics.Direction;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ROM destination-write and unsigned row-pointer contract; independent of replay data. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kNativeWordLayoutResidency {
    @Test
    void productionActTransitionRetainsTheNativeWordLookupOwners() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0).build();
        var levelManager = GameServices.level();
        var before = levelManager.getCurrentLevel();
        assertEquals(0x0179, before.getBlock(0xE0).getChunkDesc(7, 3).get(),
                "HCZ1 ROM producer owns the native resident tail before the shorter write");
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .getHczEventsForTest();
        // Native HCZ1BGE_Normal consumes the results owner's Events_fg_5 signal.
        // Exercise the real queue/service/request/build/commit, not a cold target load.
        events.setEventsFg5(true);
        for (int dispatch = 0; dispatch < 2000 && !events.isTransitionRequested(); dispatch++) {
            HardwareBoundaryPump.service(HardwareServiceBoundary.VINT_SERVICE);
            HardwareBoundaryPump.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            events.update(0, dispatch);
            HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
        }
        assertTrue(events.isTransitionRequested(), "owned ROM jobs must finish normally");
        // HCZ executes Load_Level inside this BG dispatch when its level exists;
        // the no-level event seam may instead publish a queued request.
        if (levelManager.getCurrentAct() == 0) {
            var request = levelManager.consumeSeamlessTransitionRequest();
            assertNotNull(request);
            levelManager.applySeamlessTransition(request);
        }
        assertEquals(1, levelManager.getCurrentAct());
        var level = levelManager.getCurrentLevel();
        assertNotSame(before, level, "actual target build must publish a new level");
        assertEquals(0xE094, level.getChunk(15).getPatternDesc(1, 1).get(),
                "HCZ common 16x16 ROM stream owns Block_table byte907F=94");
        assertEquals(0xA216, level.getBlock(0x94).getChunkDesc(0, 3).get(),
                "current HCZ2 128x128 ROM write owns the selected descriptor");
        var provider = GameServices.backgroundPlaneCollisionOrNull();
        assertNotNull(provider);
        var declared = new BackgroundPlaneCollisionProvider.State(true, 0x200, 0x500);
        int x = (short) provider.backgroundX(declared, 0x180, Direction.RIGHT);
        int y = (short) provider.backgroundY(declared, 0x838);
        assertEquals(-128, x);
        assertEquals(0x338, y);
        int positive = level.getBlock(level.getMap().getValue(1, 1, 6) & 0xFF)
                .getChunkDesc(0, 3).get();
        assertAll("Find_Tile_BG row8E80 + unsigned word column and current resident tables",
                () -> assertTrue(level.getBlockCount() > 0xE0,
                        "a shorter destination write must preserve the HCZ1 resident tail"),
                () -> assertEquals(0xA216, levelManager.getChunkDescAt((byte) 1, x, y).get(),
                        "FF80 selects layout byte907F from live 16x16 definitions"),
                () -> assertEquals(0x0179, levelManager.getChunkDescAt((byte) 1, x - 16, y).get(),
                        "FF70 selects907E=E0 and the retained HCZ1 descriptor"),
                () -> assertEquals(0xA216, levelManager.getChunkDescAt((byte) 1, x & 0xFFFF, y).get(),
                        "unsigned and signed representations of the same word agree"),
                () -> assertEquals(0xA216, levelManager.getChunkDescAt((byte) 1, x + 0x10000, y).get(),
                        "word addition wraps before the native lookup"),
                () -> assertEquals(positive, levelManager.getChunkDescAt((byte) 1, 0x80, y).get(),
                        "ordinary positive in-layout control"));
    }

    @Test
    void zeroRowPointerUsesTheRomAddressSpaceRatherThanResidentDefinitions() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1).build();
        var manager = GameServices.level();
        // Load_Level copies the production layout unchanged. FG row28 has no
        // row source: Find_Tile_FG's MOVEA.W turns pointer0 + unsigned column1
        // into ROM000001, not resident definition RAM FF0001.
        var rom = TestEnvironment.currentRom();
        int layout = rom.read32BitAddr(com.openggf.game.sonic3k.constants.Sonic3kConstants.LEVEL_PTRS_ADDR + 3 * 4);
        assertEquals(0, rom.read16BitAddr(layout + 8 + 28 * 4));
        assertProductionRomAliases();
        // An ordinary installed row still resolves the current ROM-produced block.
        var level = manager.getCurrentLevel();
        int block = level.getMap().getValue(0, 1, 0) & 0xFF;
        assertEquals(level.getBlock(block).getChunkDesc(0, 0).get(),
                manager.getChunkDescAt((byte) 0, 0x80, 0).get());
    }

    @Test
    void residentOwnersAndLiveMutationsReplayAfterTwoWholeWorldRecreations() throws Exception {
        var fixture = transitionForReplay();
        // Finish the ordinary transition boundary before capturing; do not revive retired act1 history.
        fixture.stepIdleFrames(1);
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        assertProductionRomAliases();
        var before = registry.capture();
        assertNotNull(before.get("s3k-collision-layout"));
        assertSame(before.get("s3k-collision-layout"), registry.capture().get("s3k-collision-layout"),
                "unchanged encoded bytes must reuse their captured value");
        mutateResidentAssetsAndAdvance(fixture);
        var after = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(before);
            assertWorld(before, registry.capture());
            assertProductionRomAliases();
            assertEquals(0xA216, GameServices.level().getChunkDescAt((byte) 1, -128, 0x338).get());
            assertEquals(0x0179, GameServices.level().getChunkDescAt((byte) 1, -144, 0x338).get());
            mutateResidentAssetsAndAdvance(fixture);
            assertWorld(after, registry.capture());
            assertProductionRomAliases();
        }
    }

    @Test
    void sceneLayoutEncodingAndMapMutationRestoreWithoutChangingPublicSnapshots() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1).build();
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        applySceneAndMapMutation();
        var after = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(before);
            assertWorld(before, registry.capture());
            assertEquals(20, GameServices.level().getCurrentLevel().getLayerWidthBlocks(1));
            applySceneAndMapMutation();
            assertWorld(after, registry.capture());
        }
    }

    private static void assertProductionRomAliases() throws Exception {
        var manager = GameServices.level();var level = manager.getCurrentLevel();
        var bytes = TestEnvironment.currentRom().readBytes(0, 0x200);
        int nonzeroDiscriminatingControls = 0;
        int zeroDescriptor = level.getBlock(0).getChunkDesc(0, 0).get();
        // All unsigned word columns of the real zero row address ROM000000..0001FF.
        // Compare actual ROM bytes, including nonzero bytes whose descriptor
        // differs from block0; no zero fallback or fitted address can satisfy this.
        for (int column = 0; column < bytes.length; column++) {
            int selected = bytes[column] & 0xFF;
            if (selected >= level.getBlockCount()) continue; // Unwritten definition RAM remains outside this control.
            int expected = level.getBlock(selected).getChunkDesc(0, 0).get();
            assertEquals(expected, manager.getChunkDescAt((byte) 0, column * 128, 28 * 128).get(),
                    "MOVEA.W selects the actual ROM byte at column " + column);
            if (selected != 0 && expected != zeroDescriptor) nonzeroDiscriminatingControls++;
        }
        assertTrue(nonzeroDiscriminatingControls > 0, "actual nonzero ROM bytes must discriminate from fabricated block0");
    }

    private static HeadlessTestFixture transitionForReplay() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0).build();
        // Declared native arena lock and handoff geometry; no movie/trace state.
        fixture.sprite().setCentreX((short) 0x3680);
        fixture.sprite().setCentreY((short) 0x800);
        GameServices.camera().setX((short) 0x3680);
        GameServices.camera().setMinX((short) 0x3680);
        GameServices.camera().setMaxX((short) 0x3680);
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getHczEventsForTest();
        events.setEventsFg5(true);
        for (int dispatch = 0; dispatch < 2000 && !events.isTransitionRequested(); dispatch++) {
            HardwareBoundaryPump.service(HardwareServiceBoundary.VINT_SERVICE);
            HardwareBoundaryPump.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
            events.update(0, dispatch);
            HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
        }
        assertTrue(events.isTransitionRequested());
        assertEquals(1, GameServices.level().getCurrentAct());
        return fixture;
    }

    private static void mutateResidentAssetsAndAdvance(HeadlessTestFixture fixture) {
        var manager = GameServices.level();
        var level = manager.getCurrentLevel();
        int[] tile = level.getChunk(15).saveState();tile[3] = 0xE0E0;
        int[] resident = level.getBlock(0xE0).saveState();resident[3 * 8 + 7] = 0xA216;
        var context = new com.openggf.game.mutation.LayoutMutationContext(
                com.openggf.game.mutation.LevelMutationSurface.forLevel(level), manager::applyMutationEffects);
        GameServices.zoneLayoutMutationPipeline().applyImmediately(c -> {
            c.surface().restoreChunkState(15, tile);
            c.surface().restoreBlockState(0xE0, resident);
            c.surface().setBlockInMap(1, 1, 6, 0x94);
            return com.openggf.game.mutation.MutationEffects.redrawAllTilemaps();
        }, context);
        assertEquals(level.getBlock(0xE0).getChunkDesc(0, 3).get(),
                manager.getChunkDescAt((byte) 1, -128, 0x338).get(), "layout alias reads live16x16 bytes");
        assertEquals(0xA216, manager.getChunkDescAt((byte) 1, -144, 0x338).get(),
                "adjacent descriptor reads the live retained128x128 table");
        assertEquals(0xA216, manager.getChunkDescAt((byte) 1, 0x80, 0x338).get(),
                "logical map mutations update encoded RAM row bytes");
        fixture.stepIdleFrames(2);
    }

    private static void applySceneAndMapMutation() throws Exception {
        var manager = GameServices.level();
        var level = (com.openggf.game.sonic3k.Sonic3kLevel) manager.getCurrentLevel();
        var rom = TestEnvironment.currentRom();
        int layout = rom.read32BitAddr(com.openggf.game.sonic3k.constants.Sonic3kConstants.LEVEL_PTRS_ADDR + 2 * 4);
        level.applySceneLayout(rom, layout);manager.setLevel(level);
        assertEquals(4, level.getLayerWidthBlocks(1));
        var context = new com.openggf.game.mutation.LayoutMutationContext(
                com.openggf.game.mutation.LevelMutationSurface.forLevel(level), manager::applyMutationEffects);
        GameServices.zoneLayoutMutationPipeline().applyImmediately(c -> c.surface().setBlockInMap(1, 0, 0, 0x94), context);
        assertEquals(level.getBlock(0x94).getChunkDesc(0, 0).get(),
                manager.getChunkDescAt((byte) 1, 0, 0).get());
    }

    private static void assertWorld(com.openggf.game.rewind.CompositeSnapshot expected,
                                    com.openggf.game.rewind.CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet())
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
    }

}
