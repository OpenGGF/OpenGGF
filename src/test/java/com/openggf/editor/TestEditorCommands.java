package com.openggf.editor;

import com.openggf.editor.commands.DeriveBlockFromChunksCommand;
import com.openggf.editor.commands.DeriveChunkFromPatternsCommand;
import com.openggf.editor.commands.CycleCellCollisionModeCommand;
import com.openggf.editor.commands.PlaceBlockCommand;
import com.openggf.editor.commands.DeleteObjectSpawnCommand;
import com.openggf.editor.commands.DeleteRingSpawnCommand;
import com.openggf.editor.commands.MoveObjectSpawnCommand;
import com.openggf.editor.commands.MoveRingSpawnCommand;
import com.openggf.editor.commands.PlaceObjectSpawnCommand;
import com.openggf.editor.commands.PlaceRingSpawnCommand;
import com.openggf.editor.commands.SetChunkSolidTileIndexCommand;
import com.openggf.editor.persistence.EditorSaveManager;
import com.openggf.game.GameId;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Map;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestEditorCommands {

    private static final class TestRegistry implements ObjectRegistry {
        @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int objectId) { return "Object-%02X".formatted(objectId); }
    }

    @Test
    void objectCommandsAreUndoableAndKeepStableIdentity() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        ObjectSpawn spawn = new CommonObjectPlacementEncoding()
                .create(0x180, 0x100, 0x26, 3, 2, true, 41);
        PlaceObjectSpawnCommand place = new PlaceObjectSpawnCommand(level, spawn);
        place.apply();
        assertEquals(List.of(spawn), level.getObjects());
        assertEquals(true, level.consumeObjectsDirty());
        assertEquals(true, level.isModifiedSinceLastSave());

        MoveObjectSpawnCommand move = new MoveObjectSpawnCommand(
                level, spawn, new CommonObjectPlacementEncoding().move(spawn, 0x140, 0x110));
        move.apply();
        ObjectSpawn moved = level.getObjects().get(0);
        assertEquals(41, moved.layoutIndex());
        assertEquals(0x140, moved.x());
        assertEquals(spawn.subtype(), moved.subtype());
        move.undo();
        assertEquals(spawn, level.getObjects().get(0));

        DeleteObjectSpawnCommand delete = new DeleteObjectSpawnCommand(level, spawn);
        delete.apply();
        assertEquals(List.of(), level.getObjects());
        delete.undo();
        assertEquals(List.of(spawn), level.getObjects());
        place.undo();
        assertEquals(List.of(), level.getObjects());
    }

    @Test
    void phase0HeadlessAuthoringSmokePersistsAndRaisesPlayResyncSignals(@TempDir Path saves) throws Exception {
        CommonObjectPlacementEncoding encoding = new CommonObjectPlacementEncoding();
        MutableLevel authored = MutableLevel.snapshot(new SyntheticLevel());

        ObjectSpawn badnik = encoding.create(0x180, 0x100, 0x1C, 3, 0, false, 41);
        new PlaceObjectSpawnCommand(authored, badnik).apply();
        ObjectSpawn movedBadnik = encoding.move(badnik, 0x1C0, 0x120);
        new MoveObjectSpawnCommand(authored, badnik, movedBadnik).apply();

        RingSpawn ring = new RingSpawn(0x200, 0x100, 51);
        new PlaceRingSpawnCommand(authored, ring).apply();
        RingSpawn movedRing = new RingSpawn(0x240, 0x120, ring.placementId());
        new MoveRingSpawnCommand(authored, ring, movedRing).apply();
        new DeleteRingSpawnCommand(authored, movedRing).apply();

        int baselineCell = authored.getBlock(0).getChunkDesc(0, 0).get();
        new CycleCellCollisionModeCommand(authored, 0, 0, EditorCollisionPath.PRIMARY).apply();
        new SetChunkSolidTileIndexCommand(authored, 0, EditorCollisionPath.PRIMARY, 1).apply();

        EditorSaveManager savesManager = new EditorSaveManager(saves);
        savesManager.save(GameId.S2, encoding, 0, 0, authored);
        MutableLevel reloaded = MutableLevel.snapshot(new SyntheticLevel());
        assertEquals(EditorSaveManager.ApplyResult.APPLIED,
                savesManager.tryApplyEdits(GameId.S2, encoding, 0, 0, reloaded));

        assertEquals(List.of(movedBadnik), reloaded.getObjects());
        assertEquals(List.of(), reloaded.getRings());
        assertEquals((baselineCell & ~0x3000) | 0x1000,
                reloaded.getBlock(0).getChunkDesc(0, 0).get());
        assertEquals(1, reloaded.getChunk(0).getSolidTileIndex());
        assertEquals(true, reloaded.consumeObjectsDirty(),
                "persisted object apply must request runtime placement resync");
        assertEquals(true, reloaded.consumeRingsDirty(),
                "persisted empty ring table must request runtime ring resync");
        assertEquals(true, reloaded.consumeDirtyBlocks().get(0),
                "persisted collision-mode apply must request tilemap redraw");
        assertEquals(true, reloaded.consumeDirtyChunks().get(0),
                "persisted collision-index apply must request collision/tilemap refresh");
        assertFalse(reloaded.isModifiedSinceLastSave(),
                "reloaded sidecar state is saved baseline, not an unsaved user edit");
    }

    @Test
    void objectInsertionUsesStablePlacementColumnsAndDuplicateBytesKeepDistinctIds() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        CommonObjectPlacementEncoding encoding = new CommonObjectPlacementEncoding();
        ObjectSpawn first = encoding.create(0x1C0, 0x100, 1, 0, 0, false, 1);
        ObjectSpawn second = encoding.create(0x180, 0x100, 1, 0, 0, false, 2);
        ObjectSpawn duplicateA = encoding.create(0x200, 0x100, 1, 0, 0, false, 3);
        ObjectSpawn duplicateB = encoding.create(0x200, 0x100, 1, 0, 0, false, 4);
        level.addObjectSpawn(first);
        level.addObjectSpawn(second);
        level.addObjectSpawn(duplicateA);
        level.addObjectSpawn(duplicateB);

        assertEquals(List.of(0x1C0, 0x180, 0x200, 0x200),
                level.getObjects().stream().map(ObjectSpawn::x).toList());
        assertEquals(List.of(1, 2, 3, 4),
                level.getObjects().stream().map(ObjectSpawn::layoutIndex).toList());
    }

    @Test
    void ringCommandsAreUndoableAndUseStableColumnOrdering() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        RingSpawn first = new RingSpawn(0x1C0, 0x100, 7);
        RingSpawn second = new RingSpawn(0x180, 0x100, 8);
        PlaceRingSpawnCommand placeFirst = new PlaceRingSpawnCommand(level, first);
        PlaceRingSpawnCommand placeSecond = new PlaceRingSpawnCommand(level, second);
        placeFirst.apply();
        placeSecond.apply();
        assertEquals(List.of(0x1C0, 0x180), level.getRings().stream().map(RingSpawn::x).toList());

        RingSpawn moved = new RingSpawn(0x280, 0x120, 7);
        MoveRingSpawnCommand move = new MoveRingSpawnCommand(level, first, moved);
        move.apply();
        assertEquals(7, level.getRings().get(1).placementId());
        move.undo();
        assertEquals(first, level.getRings().get(0));

        DeleteRingSpawnCommand delete = new DeleteRingSpawnCommand(level, second);
        delete.apply();
        delete.undo();
        assertEquals(8, level.getRings().get(1).placementId());
    }

    @Test
    void persistedSpawnReplacementDirtiesRuntimeWithoutMarkingUserModified() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        level.markSaved();
        ObjectSpawn spawn = new CommonObjectPlacementEncoding()
                .create(0x200, 0x100, 1, 0, 0, false, 9);

        level.replaceObjectSpawnsPersisted(List.of(spawn));
        level.replaceRingSpawnsPersisted(List.of(new RingSpawn(0x200, 0x100, 10)));

        assertEquals(true, level.consumeObjectsDirty());
        assertEquals(true, level.consumeRingsDirty());
        assertEquals(false, level.isModifiedSinceLastSave());
    }

    @Test
    void persistedReplacementIsAliasSafeAndRejectsNegativeIdsAtomically() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        ObjectSpawn object = new CommonObjectPlacementEncoding().create(32, 32, 1, 0, 0, false, 1);
        RingSpawn ring = new RingSpawn(32, 32, 1);
        level.replaceObjectSpawnsPersisted(List.of(object));
        level.replaceRingSpawnsPersisted(List.of(ring));
        level.replaceObjectSpawnsPersisted(level.getObjects());
        level.replaceRingSpawnsPersisted(level.getRings());
        assertEquals(List.of(object), level.getObjects());
        assertEquals(List.of(ring), level.getRings());

        assertThrows(IllegalArgumentException.class,
                () -> level.replaceObjectSpawnsPersisted(List.of(
                        new ObjectSpawn(1, 2, 3, 0, 0, false, 2, -1))));
        assertThrows(IllegalArgumentException.class,
                () -> level.replaceRingSpawnsPersisted(List.of(new RingSpawn(1, 2))));
        assertEquals(List.of(object), level.getObjects());
        assertEquals(List.of(ring), level.getRings());
    }

    @Test
    void invalidRestoreIndexDoesNotRemoveCurrentSpawn() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        ObjectSpawn before = new CommonObjectPlacementEncoding().create(32, 32, 1, 0, 0, false, 1);
        ObjectSpawn after = new CommonObjectPlacementEncoding().move(before, 48, 48);
        level.addObjectSpawn(before);
        level.moveObjectSpawn(before, after);
        assertThrows(IllegalArgumentException.class,
                () -> level.restoreMovedObjectSpawn(after, before, 99));
        assertEquals(List.of(after), level.getObjects());
    }

    @Test
    void controllerObjectVerbsSharePaletteFactoryAndHistory() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.configureSpawnEditing(new TestRegistry(), new CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        controller.setWorldCursor(new com.openggf.game.session.EditorCursorState(32, 32));

        controller.objectPalette().nextObject();
        controller.objectPalette().setSubtype(3);
        controller.placeObjectSpawnAtCursor();
        assertEquals(1, level.getObjects().size());
        assertEquals(1, level.getObjects().get(0).objectId());
        assertEquals(3, level.getObjects().get(0).subtype());

        controller.eyedropSpawnAtCursor();
        controller.moveSelectedSpawn(48, 48);
        assertEquals(48, level.getObjects().get(0).x());
        controller.undo();
        assertEquals(32, level.getObjects().get(0).x());
        controller.setWorldCursor(new com.openggf.game.session.EditorCursorState(32, 32));
        controller.deleteSpawnAtCursor();
        assertEquals(0, level.getObjects().size());
        controller.undo();
        assertEquals(1, level.getObjects().size());
    }

    @Test
    void controllerRingVerbsUseSameHistoryPath() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.configureSpawnEditing(new TestRegistry(), new CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(EditorSpawnEditMode.RINGS);
        controller.setWorldCursor(new com.openggf.game.session.EditorCursorState(32, 32));
        controller.placeRingSpawnAtCursor();
        controller.eyedropSpawnAtCursor();
        controller.moveSelectedSpawn(48, 48);
        assertEquals(48, level.getRings().get(0).x());
        controller.undo();
        assertEquals(32, level.getRings().get(0).x());
    }

    @Test
    void logicalInputActionsDriveSpawnModePaletteAndCommands() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.configureSpawnEditing(new TestRegistry(), new CommonObjectPlacementEncoding());
        EditorInputHandler input = new EditorInputHandler(controller);
        input.handleAction(EditorInputHandler.Action.CYCLE_SPAWN_EDIT_MODE);
        input.handleAction(EditorInputHandler.Action.NEXT_OBJECT);
        input.handleAction(EditorInputHandler.Action.INCREMENT_SUBTYPE);
        input.handleAction(EditorInputHandler.Action.APPLY_PRIMARY_ACTION);
        assertEquals(EditorSpawnEditMode.OBJECTS, controller.spawnEditMode());
        assertEquals(1, level.getObjects().get(0).objectId());
        assertEquals(1, level.getObjects().get(0).subtype());
        input.handleAction(EditorInputHandler.Action.PERFORM_EYEDROP);
        controller.setWorldCursor(new com.openggf.game.session.EditorCursorState(16, 16));
        input.handleAction(EditorInputHandler.Action.MOVE_SELECTED_SPAWN_TO_CURSOR);
        assertEquals(16, level.getObjects().get(0).x());
        input.handleAction(EditorInputHandler.Action.DELETE_SPAWN);
        assertEquals(0, level.getObjects().size());
    }

    @Test
    void sonic1ObjectBackedRingMappingSurvivesSnapshotAndEditorPlacement() {
        SyntheticLevel source = new SyntheticLevel();
        ObjectSpawn originalObject = new com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding()
                .create(16, 16, 0x25, 0, 0, false, 0);
        RingSpawn originalRing = new RingSpawn(16, 16, 0);
        source.setSourceRingGroup(originalObject, originalRing);
        MutableLevel level = MutableLevel.snapshot(source);
        assertEquals(List.of(originalRing), level.ringObjectPlacementMapping().get(originalObject));

        EditorPlacementIdAllocator ids = new EditorPlacementIdAllocator(level);
        EditorSpawnFactory factory = new EditorSpawnFactory(
                new com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding(), ids);
        RingSpawn addedRing = factory.createRingSpawn(32, 32);
        ObjectSpawn backingObject = factory.createRingBackingObject(addedRing);
        PlaceRingSpawnCommand command = new PlaceRingSpawnCommand(level, addedRing, backingObject);
        command.apply();
        assertEquals(List.of(addedRing), level.ringObjectPlacementMapping().get(backingObject));
        assertEquals(backingObject, level.ringBackingObject(addedRing));
        command.undo();
        assertEquals(null, level.ringBackingObject(addedRing));
    }

    @Test
    void editorMoveCodecPreservesNamespacedObjectOwnerAndKey() {
        ObjectSpawn tagged = new ObjectSpawn(16, 32, 0xFE, 3, 1, false, 0x2020, 9,
                "example", "example:objects/buzzer");
        EditorSpawnFactory factory = new EditorSpawnFactory(
                new CommonObjectPlacementEncoding(),
                new EditorPlacementIdAllocator(MutableLevel.snapshot(new SyntheticLevel())));

        ObjectSpawn moved = factory.moveObjectSpawn(tagged, 48, 64);

        assertEquals("example", moved.ownerModId());
        assertEquals("example:objects/buzzer", moved.objectKey());
        assertEquals(tagged.layoutIndex(), moved.layoutIndex());
    }

    @Test
    void multiRingSonic1GroupDeleteAndMoveUndoRestoreExactObjectAndRingOrder() {
        SyntheticLevel source = new SyntheticLevel();
        var encoding = new com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding();
        ObjectSpawn backing = encoding.create(0x1C0, 0x100, 0x25, 1, 0, false, 0);
        RingSpawn first = new RingSpawn(0x1C0, 0x100, 0);
        RingSpawn second = new RingSpawn(0x1D0, 0x100, 1);
        source.setSourceRingGroup(backing, List.of(first, second));
        MutableLevel level = MutableLevel.snapshot(source);
        ObjectSpawn neighborObject = encoding.create(0x180, 0x120, 1, 0, 0, false, 2);
        RingSpawn neighborRing = new RingSpawn(0x180, 0x120, 2);
        level.addObjectSpawn(neighborObject);
        level.addRingSpawn(neighborRing);
        List<ObjectSpawn> originalObjects = List.copyOf(level.getObjects());
        List<RingSpawn> originalRings = List.copyOf(level.getRings());

        DeleteRingSpawnCommand delete = new DeleteRingSpawnCommand(level, second, backing);
        delete.apply();
        assertEquals(List.of(neighborObject), level.getObjects());
        assertEquals(List.of(neighborRing), level.getRings());
        delete.undo();
        assertEquals(originalObjects, level.getObjects());
        assertEquals(originalRings, level.getRings());

        RingSpawn movedSelected = new RingSpawn(0x280, 0x180, second.placementId());
        ObjectSpawn movedBacking = encoding.move(backing,
                backing.x() + movedSelected.x() - second.x(),
                backing.y() + movedSelected.y() - second.y());
        MoveRingSpawnCommand move = new MoveRingSpawnCommand(
                level, second, movedSelected, backing, movedBacking);
        move.apply();
        assertEquals(List.of(0x270, 0x280),
                level.ringObjectPlacementMapping().get(movedBacking).stream().map(RingSpawn::x).toList());
        move.undo();
        assertEquals(originalObjects, level.getObjects());
        assertEquals(originalRings, level.getRings());
        assertEquals(List.of(first, second), level.ringObjectPlacementMapping().get(backing));
    }

    @Test
    void controllerMovesSonic1BackingAnchorBySelectedChildDeltaAndUndoRedoExactly() {
        SyntheticLevel source = new SyntheticLevel();
        var encoding = new com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding();
        ObjectSpawn backing = encoding.create(0x100, 0x100, 0x25, 1, 0, false, 0);
        RingSpawn first = new RingSpawn(0x100, 0x100, 0);
        RingSpawn second = new RingSpawn(0x110, 0x100, 1);
        source.setSourceRingGroup(backing, List.of(first, second));
        MutableLevel level = MutableLevel.snapshot(source);
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.configureSpawnEditing(new TestRegistry(), encoding);
        controller.setSpawnEditMode(EditorSpawnEditMode.RINGS);
        controller.setWorldCursor(new com.openggf.game.session.EditorCursorState(second.x(), second.y()));
        controller.eyedropSpawnAtCursor();
        controller.moveSelectedSpawn(0x210, 0x180);

        ObjectSpawn movedBacking = level.getObjects().get(0);
        assertEquals(0x200, movedBacking.x());
        assertEquals(0x180, movedBacking.y());
        assertEquals(List.of(0x200, 0x210),
                level.ringObjectPlacementMapping().get(movedBacking).stream().map(RingSpawn::x).toList());
        controller.undo();
        assertEquals(List.of(backing), level.getObjects());
        assertEquals(List.of(first, second), level.getRings());
        controller.moveSelectedSpawn(0x310, 0x200);
        assertEquals(0x300, level.getObjects().get(0).x());
        controller.undo();
        assertEquals(List.of(backing), level.getObjects());
        controller.redo();
        assertEquals(0x300, level.getObjects().get(0).x());
    }

    @Test
    void undoingPlacedSelectionsClearsThemBeforeFurtherMove() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.configureSpawnEditing(new TestRegistry(), new CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        controller.placeObjectSpawnAtCursor();
        controller.undo();
        controller.moveSelectedSpawn(32, 32);
        assertEquals(List.of(), level.getObjects());
        controller.setSpawnEditMode(EditorSpawnEditMode.RINGS);
        controller.placeRingSpawnAtCursor();
        controller.undo();
        controller.moveSelectedSpawn(32, 32);
        assertEquals(List.of(), level.getRings());
    }

    @Test
    void genericObjectMutationRejectsSonic1RingBackingAndReplacementDropsStaleMapping() {
        SyntheticLevel source = new SyntheticLevel();
        var encoding = new com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding();
        ObjectSpawn backing = encoding.create(16, 16, 0x25, 1, 0, false, 0);
        RingSpawn ring = new RingSpawn(16, 16, 0);
        RingSpawn child = new RingSpawn(32, 16, 1);
        source.setSourceRingGroup(backing, List.of(ring, child));
        MutableLevel level = MutableLevel.snapshot(source);
        assertThrows(IllegalArgumentException.class, () -> level.removeObjectSpawn(backing));
        assertThrows(IllegalArgumentException.class,
                () -> level.moveObjectSpawn(backing, encoding.move(backing, 32, 32)));
        assertEquals(List.of(backing), level.getObjects());
        assertEquals(List.of(ring, child), level.getRings());
        level.replaceObjectSpawnsPersisted(List.of());
        assertEquals(java.util.Map.of(), level.ringObjectPlacementMapping());
    }

    @Test
    void persistedReplacementRebindsSonic1MappingToCanonicalMovedRecords() {
        SyntheticLevel source = new SyntheticLevel();
        var encoding = new com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding();
        ObjectSpawn backing = encoding.create(16, 16, 0x25, 1, 0, false, 0);
        RingSpawn ring = new RingSpawn(16, 16, 0);
        RingSpawn child = new RingSpawn(32, 16, 1);
        source.setSourceRingGroup(backing, List.of(ring, child));
        MutableLevel level = MutableLevel.snapshot(source);
        ObjectSpawn movedBacking = encoding.move(backing, 48, 48);
        RingSpawn movedRing = new RingSpawn(48, 48, ring.placementId());
        RingSpawn movedChild = new RingSpawn(64, 48, child.placementId());
        level.replaceSpawnsPersisted(List.of(movedBacking), List.of(movedRing, movedChild),
                java.util.Map.of(backing, List.of(ring, child)));
        var entry = level.ringObjectPlacementMapping().entrySet().iterator().next();
        assertSame(movedBacking, entry.getKey());
        assertSame(movedRing, entry.getValue().get(0));
        assertSame(movedChild, entry.getValue().get(1));
        assertEquals(List.of(movedRing, movedChild), level.ringObjectPlacementMapping().get(movedBacking));
        com.openggf.game.sonic1.objects.Sonic1ObjectRegistry registry =
                new com.openggf.game.sonic1.objects.Sonic1ObjectRegistry();
        registry.setRingSpawnMapping(level.ringObjectPlacementMapping());
        var instance = registry.create(movedBacking);
        assertEquals(1, instance.getReservedChildSlotCount());
    }

    @Test
    void placementAllocatorRejectsExhaustedIntIdentitySpace() {
        SyntheticLevel source = new SyntheticLevel();
        source.setSourcePlacements(
                new ObjectSpawn(1, 1, 1, 0, 0, false, 1, Integer.MAX_VALUE),
                new RingSpawn(1, 1, Integer.MAX_VALUE));
        EditorPlacementIdAllocator allocator = new EditorPlacementIdAllocator(source);
        assertThrows(IllegalStateException.class, allocator::nextObjectId);
        assertThrows(IllegalStateException.class, allocator::nextRingId);
    }

    @Test
    void logicalGamepadEdgesNavigatePaletteAndPlaceObject() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.configureSpawnEditing(new TestRegistry(), new CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        controller.cycleFocusRegion();
        EditorInputHandler editorInput = new EditorInputHandler(controller);
        InputHandler input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT,
                        0, 0, false, false), PlayerInputState.neutral()));
        editorInput.update(input);
        assertEquals(1, controller.objectPalette().selectedObjectId());

        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, InputActionMasks.ACTION_A, false, false),
                PlayerInputState.neutral()));
        editorInput.update(input);
        assertEquals(1, level.getObjects().size());
        assertEquals(1, level.getObjects().get(0).objectId());
    }

    @Test
    void rawSpaceAndLogicalActionAProduceOneSpawnAndOneHistoryEntry() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.configureSpawnEditing(new TestRegistry(), new CommonObjectPlacementEncoding());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        EditorInputHandler editorInput = new EditorInputHandler(controller);
        InputHandler input = new InputHandler();
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, InputActionMasks.ACTION_A,
                        InputActionMasks.ACTION_A, false, false), PlayerInputState.neutral()));
        editorInput.update(input);
        assertEquals(1, level.getObjects().size());
        controller.undo();
        assertEquals(List.of(), level.getObjects());
        assertEquals(false, controller.hasUndoHistory());
    }

    @Test
    void sonic1UnsupportedAndRingReservedObjectSelectionsAreRefusedWithoutThrowing() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(level);
        controller.configureSpawnEditing(new TestRegistry(),
                new com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding());
        controller.setSpawnEditMode(EditorSpawnEditMode.OBJECTS);
        controller.objectPalette().setObjectId(0x80);
        controller.placeObjectSpawnAtCursor();
        controller.objectPalette().setObjectId(0x25);
        controller.placeObjectSpawnAtCursor();
        assertEquals(List.of(), level.getObjects());
        assertEquals(List.of(), level.getRings());
        assertEquals(false, controller.hasUndoHistory());
    }

    @Test
    void rawAndGamepadModeCycleAdvanceExactlyOneModePerUpdate() {
        LevelEditorController controller = new LevelEditorController();
        controller.attachLevel(MutableLevel.snapshot(new SyntheticLevel()));
        controller.configureSpawnEditing(new TestRegistry(), new CommonObjectPlacementEncoding());
        EditorInputHandler editorInput = new EditorInputHandler(controller);
        InputHandler input = new InputHandler();
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_O, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        editorInput.update(input);
        assertEquals(EditorSpawnEditMode.OBJECTS, controller.spawnEditMode());

        input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, false, true), PlayerInputState.neutral()));
        editorInput.update(input);
        assertEquals(EditorSpawnEditMode.RINGS, controller.spawnEditMode());

        input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, false, true), PlayerInputState.neutral()));
        editorInput.update(input);
        assertEquals(EditorSpawnEditMode.TILES, controller.spawnEditMode());
    }

    @Test
    void placeBlockCommand_mutatesMapAndUndoRestoresPreviousValue() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        int before = level.getMap().getValue(0, 1, 1) & 0xFF;
        PlaceBlockCommand command = new PlaceBlockCommand(level, 0, 1, 1, before, 2);

        command.apply();
        assertEquals(2, level.getMap().getValue(0, 1, 1) & 0xFF);

        command.undo();
        assertEquals(before, level.getMap().getValue(0, 1, 1) & 0xFF);
    }

    @Test
    void placeBlockCommand_keepsReverseLookupLiveForLaterBlockDirtying() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        int before = level.getMap().getValue(0, 1, 1) & 0xFF;
        PlaceBlockCommand command = new PlaceBlockCommand(level, 0, 1, 1, before, 2);

        command.apply();
        level.consumeDirtyMapCells();

        level.setChunkInBlock(2, 0, 0, new ChunkDesc(1));

        BitSet dirtyMapCells = level.consumeDirtyMapCells();
        assertEquals(2, level.getMap().getValue(0, 1, 1) & 0xFF);
        assertEquals(true, dirtyMapCells.get(3));
    }

    @Test
    void levelEditorController_placeBlockUndoRedoMutatesAttachedLevel() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        LevelEditorController controller = new LevelEditorController();

        controller.attachLevel(level);

        int before = level.getMap().getValue(0, 1, 1) & 0xFF;

        controller.placeBlock(0, 1, 1, 2);
        assertEquals(2, level.getMap().getValue(0, 1, 1) & 0xFF);

        controller.undo();
        assertEquals(before, level.getMap().getValue(0, 1, 1) & 0xFF);

        controller.redo();
        assertEquals(2, level.getMap().getValue(0, 1, 1) & 0xFF);
    }

    @Test
    void levelEditorController_attachLevelClearsHistoryForPreviousMutableLevel() {
        MutableLevel firstLevel = MutableLevel.snapshot(new SyntheticLevel());
        MutableLevel secondLevel = MutableLevel.snapshot(new SyntheticLevel());
        LevelEditorController controller = new LevelEditorController();

        controller.attachLevel(firstLevel);
        int firstBefore = firstLevel.getMap().getValue(0, 1, 1) & 0xFF;
        controller.placeBlock(0, 1, 1, 2);

        controller.attachLevel(secondLevel);
        controller.undo();

        assertEquals(2, firstLevel.getMap().getValue(0, 1, 1) & 0xFF);
        assertEquals(firstBefore, secondLevel.getMap().getValue(0, 1, 1) & 0xFF);
    }

    @Test
    void levelEditorController_placeBlockRequiresAttachedLevel() {
        LevelEditorController controller = new LevelEditorController();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> controller.placeBlock(0, 1, 1, 2));

        assertEquals("No MutableLevel is attached to the editor controller", error.getMessage());
    }

    @Test
    void deriveBlockFromChunksCommand_clonesSourceBlockAndUndoRestoresDerivedBlockStateAndMapCell() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        int mapLayer = 0;
        int mapX = 1;
        int mapY = 0;
        int sourceBlockIndex = level.getMap().getValue(mapLayer, mapX, mapY) & 0xFF;
        int derivedBlockIndex = 2;
        int[] derivedBlockBeforeState = level.getBlock(derivedBlockIndex).saveState();
        int baselineChunkAt00 = level.getBlock(derivedBlockIndex).getChunkDesc(0, 0).get();
        int baselineChunkAt11 = level.getBlock(derivedBlockIndex).getChunkDesc(1, 1).get();
        ChunkDesc replacementChunk = new ChunkDesc(9);
        DeriveBlockFromChunksCommand command = new DeriveBlockFromChunksCommand(
                level,
                mapLayer,
                mapX,
                mapY,
                sourceBlockIndex,
                derivedBlockIndex,
                derivedBlockBeforeState,
                replacementChunk,
                1,
                1
        );

        command.apply();
        assertEquals(derivedBlockIndex, level.getMap().getValue(mapLayer, mapX, mapY) & 0xFF);
        assertEquals(9, level.getBlock(derivedBlockIndex).getChunkDesc(1, 1).getChunkIndex());
        assertEquals(1, level.getBlock(derivedBlockIndex).getChunkDesc(0, 0).getChunkIndex());

        command.undo();
        assertEquals(sourceBlockIndex, level.getMap().getValue(mapLayer, mapX, mapY) & 0xFF);
        assertEquals(baselineChunkAt00, level.getBlock(derivedBlockIndex).getChunkDesc(0, 0).get());
        assertEquals(baselineChunkAt11, level.getBlock(derivedBlockIndex).getChunkDesc(1, 1).get());
    }

    @Test
    void deriveBlockFromChunksCommand_undoRefreshesChunkReverseLookupForRestoredBlockState() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        DeriveBlockFromChunksCommand command = new DeriveBlockFromChunksCommand(
                level,
                0,
                1,
                0,
                level.getMap().getValue(0, 1, 0) & 0xFF,
                2,
                level.getBlock(2).saveState(),
                new ChunkDesc(4),
                0,
                0
        );

        command.apply();
        command.undo();
        level.consumeDirtyBlocks();
        level.consumeDirtyMapCells();

        level.setPatternDescInChunk(4, 0, 0, new PatternDesc(444));

        BitSet dirtyBlocks = level.consumeDirtyBlocks();
        assertFalse(dirtyBlocks.get(2));
    }

    @Test
    void deriveBlockFromChunksCommand_rejectsDerivedBlockSlotAlreadyUsedInMap() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        int sourceBlockIndex = level.getMap().getValue(0, 1, 0) & 0xFF;

        assertThrows(IllegalArgumentException.class, () -> new DeriveBlockFromChunksCommand(
                level,
                0,
                1,
                0,
                sourceBlockIndex,
                0,
                level.getBlock(0).saveState(),
                new ChunkDesc(9),
                1,
                1
        ));
    }

    @Test
    void deriveChunkFromPatternsCommand_clonesSourceChunkAndUndoRestoresDerivedChunkStateAndBlockReference() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        int blockIndex = 1;
        int blockX = 0;
        int blockY = 1;
        int sourceChunkIndex = level.getBlock(blockIndex).getChunkDesc(blockX, blockY).getChunkIndex();
        int derivedChunkIndex = 4;
        int[] derivedChunkBeforeState = level.getChunk(derivedChunkIndex).saveState();
        int baselinePatternAt00 = level.getChunk(derivedChunkIndex).getPatternDesc(0, 0).get();
        int baselinePatternAt10 = level.getChunk(derivedChunkIndex).getPatternDesc(1, 0).get();
        PatternDesc replacementPattern = new PatternDesc(77);
        DeriveChunkFromPatternsCommand command = new DeriveChunkFromPatternsCommand(
                level,
                blockIndex,
                blockX,
                blockY,
                sourceChunkIndex,
                derivedChunkIndex,
                derivedChunkBeforeState,
                replacementPattern,
                1,
                0
        );

        command.apply();
        assertEquals(derivedChunkIndex, level.getBlock(blockIndex).getChunkDesc(blockX, blockY).getChunkIndex());
        assertEquals(77, level.getChunk(derivedChunkIndex).getPatternDesc(1, 0).get());
        assertEquals(30, level.getChunk(derivedChunkIndex).getPatternDesc(0, 0).get());
        level.consumeDirtyChunks();

        command.undo();
        BitSet dirtyChunks = level.consumeDirtyChunks();
        assertEquals(sourceChunkIndex, level.getBlock(blockIndex).getChunkDesc(blockX, blockY).getChunkIndex());
        assertEquals(baselinePatternAt00, level.getChunk(derivedChunkIndex).getPatternDesc(0, 0).get());
        assertEquals(baselinePatternAt10, level.getChunk(derivedChunkIndex).getPatternDesc(1, 0).get());
        assertEquals(true, dirtyChunks.get(derivedChunkIndex));
    }

    @Test
    void deriveChunkFromPatternsCommand_preservesBlockCellDescriptorHighBitsWhenReplacingChunkIndex() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        int blockIndex = 1;
        int blockX = 0;
        int blockY = 1;
        int sourceChunkRaw = 0xFC03;
        int derivedChunkRaw = 0xFC04;
        level.setChunkInBlock(blockIndex, blockX, blockY, new ChunkDesc(sourceChunkRaw));
        DeriveChunkFromPatternsCommand command = new DeriveChunkFromPatternsCommand(
                level,
                blockIndex,
                blockX,
                blockY,
                3,
                4,
                level.getChunk(4).saveState(),
                new PatternDesc(77),
                1,
                0
        );

        command.apply();
        assertEquals(derivedChunkRaw, level.getBlock(blockIndex).getChunkDesc(blockX, blockY).get());

        command.undo();
        assertEquals(sourceChunkRaw, level.getBlock(blockIndex).getChunkDesc(blockX, blockY).get());
    }

    @Test
    void deriveChunkFromPatternsCommand_keepsReverseLookupLiveForLaterChunkDirtying() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());
        DeriveChunkFromPatternsCommand command = new DeriveChunkFromPatternsCommand(
                level,
                1,
                0,
                1,
                level.getBlock(1).getChunkDesc(0, 1).getChunkIndex(),
                4,
                level.getChunk(4).saveState(),
                new PatternDesc(77),
                1,
                0
        );

        command.apply();
        level.consumeDirtyBlocks();
        level.consumeDirtyMapCells();

        level.setPatternDescInChunk(4, 0, 0, new PatternDesc(123));

        BitSet dirtyBlocks = level.consumeDirtyBlocks();
        BitSet dirtyMapCells = level.consumeDirtyMapCells();
        assertEquals(true, dirtyBlocks.get(1));
        assertEquals(true, dirtyMapCells.get(1));
    }

    @Test
    void deriveChunkFromPatternsCommand_rejectsDerivedChunkSlotAlreadyReferencedByBlocks() {
        MutableLevel level = MutableLevel.snapshot(new SyntheticLevel());

        assertThrows(IllegalArgumentException.class, () -> new DeriveChunkFromPatternsCommand(
                level,
                1,
                0,
                1,
                level.getBlock(1).getChunkDesc(0, 1).getChunkIndex(),
                3,
                level.getChunk(3).saveState(),
                new PatternDesc(77),
                1,
                0
        ));
    }

    private static final class SyntheticLevel extends AbstractLevel
            implements com.openggf.level.objects.RingObjectPlacementMapping {
        private final java.util.LinkedHashMap<ObjectSpawn, List<RingSpawn>> sourceRingMapping = new java.util.LinkedHashMap<>();
        private SyntheticLevel() {
            super(0);
            patternCount = 4;
            patterns = new Pattern[patternCount];
            for (int i = 0; i < patternCount; i++) {
                patterns[i] = new Pattern();
                patterns[i].setPixel(0, 0, (byte) (i + 1));
            }

            chunkCount = 5;
            chunks = new Chunk[chunkCount];
            for (int i = 0; i < chunkCount; i++) {
                chunks[i] = new Chunk();
                int[] state = new int[Chunk.PATTERNS_PER_CHUNK + 2];
                state[0] = i * 10;
                state[1] = i * 10 + 1;
                state[2] = i * 10 + 2;
                state[3] = i * 10 + 3;
                chunks[i].restoreState(state);
            }

            blockCount = 3;
            blocks = new Block[blockCount];
            for (int i = 0; i < blockCount; i++) {
                blocks[i] = new Block(2);
                blocks[i].setChunkDesc(0, 0, new ChunkDesc(i));
                blocks[i].setChunkDesc(1, 0, new ChunkDesc((i + 1) % 4));
                blocks[i].setChunkDesc(0, 1, new ChunkDesc((i + 2) % 4));
                blocks[i].setChunkDesc(1, 1, new ChunkDesc((i + 3) % 4));
            }

            solidTileCount = 2;
            solidTiles = new SolidTile[] {
                    new SolidTile(0, new byte[SolidTile.TILE_SIZE_IN_ROM], new byte[SolidTile.TILE_SIZE_IN_ROM], (byte) 0),
                    new SolidTile(1, new byte[SolidTile.TILE_SIZE_IN_ROM], new byte[SolidTile.TILE_SIZE_IN_ROM], (byte) 0)
            };

            map = new Map(1, 2, 2);
            map.setValue(0, 0, 0, (byte) 0);
            map.setValue(0, 1, 0, (byte) 1);
            map.setValue(0, 0, 1, (byte) 1);
            map.setValue(0, 1, 1, (byte) 0);

            palettes = new Palette[PALETTE_COUNT];
            for (int i = 0; i < PALETTE_COUNT; i++) {
                palettes[i] = new Palette();
            }

            objects = List.of();
            rings = List.of();
            minX = 0;
            maxX = 0x0FFF;
            minY = 0;
            maxY = 0x0FFF;
        }

        @Override
        public int getChunksPerBlockSide() {
            return 2;
        }

        @Override
        public int getBlockPixelSize() {
            return 32;
        }

        private void setSourceRingGroup(ObjectSpawn object, RingSpawn ring) {
            setSourceRingGroup(object, List.of(ring));
        }

        private void setSourceRingGroup(ObjectSpawn object, List<RingSpawn> group) {
            objects = List.of(object);
            rings = List.copyOf(group);
            sourceRingMapping.put(object, List.copyOf(group));
        }

        private void setSourcePlacements(ObjectSpawn object, RingSpawn ring) {
            objects = List.of(object);
            rings = List.of(ring);
        }

        @Override
        public java.util.Map<ObjectSpawn, List<RingSpawn>> ringObjectPlacementMapping() {
            return sourceRingMapping;
        }
    }
}
