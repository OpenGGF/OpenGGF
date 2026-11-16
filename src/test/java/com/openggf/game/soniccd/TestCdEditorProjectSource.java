package com.openggf.game.soniccd;

import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.editor.project.*;
import com.openggf.game.GameId;
import com.openggf.game.soniccd.level.CdStageLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestCdEditorProjectSource {
    @TempDir Path directory;

    @Test void sourceProjectPlaytestUsesNativeRuntime() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var module = new SonicCdGameModule();
            var game = (SonicCdGame) module.createGame(disc);
            var source = (com.openggf.game.soniccd.level.CdLevel) game.loadLevel(CdStagePayload.R11A.ordinal());
            var metadata = new EditorProjectMetadata(GameId.SCD, "Playtest", 0, 0, disc.identity(),
                    128, 512, 0, source.getMaxX(), 0, source.getMaxY(),
                    new FullLevelExporter.ExportMusic.Stock(1), null);
            var document = EditorProjectDocument.create(metadata, source);
            // Empty this column to establish a safe authored spawn, away from level start.
            document.level().getMap().setValue(0, 0, 1, (byte) 0);
            document.level().getMap().setValue(0, 0, 2, (byte) 0);
            var runtime = EditorProjectRuntimeModule.prepare(module, document, 128, 512);
            var loaded = runtime.loadLevelOverride(CdStagePayload.R11A.ordinal());
            assertInstanceOf(com.openggf.game.soniccd.level.CdLevel.class, loaded);
            assertSame(loaded, game.currentLevel());
            assertNotNull(game.currentImage());
            assertArrayEquals(new int[]{128, 512}, game.getStartPosition(0, 0));
            assertNotSame(loaded, runtime.loadLevelOverride(CdStagePayload.R11A.ordinal()));
            assertThrows(java.io.IOException.class, () -> runtime.loadLevelOverride(CdStagePayload.R12A.ordinal()));
            assertThrows(IllegalArgumentException.class, () -> EditorProjectRuntimeModule.request(document, directory, 128, 512));
        }
    }

    @Test void authoredEditsReplayOnFreshNativeOwners() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var source = new CdStageLoader(disc).load(CdStagePayload.R11A, 0, false);
            var metadata = new EditorProjectMetadata(GameId.SCD, "Replay", 0, 0, disc.identity(),
                    source.getMinX(), source.getMinY(), source.getMinX(), source.getMaxX(),
                    source.getMinY(), source.getMaxY(), new FullLevelExporter.ExportMusic.Stock(1), null);
            var document = EditorProjectDocument.create(metadata, source);
            var edited = document.level();
            var original = edited.getObjects().stream().filter(o -> o.objectId() != 0x10).findFirst().orElseThrow();
            var moved = original.withPosition(original.x() + 32, original.y());
            edited.moveObjectSpawn(original, moved);
            var deleted = edited.getObjects().stream().filter(o -> o.objectId() != 0x10 && !o.equals(moved)).findFirst().orElseThrow();
            edited.removeObjectSpawn(deleted);
            var added = new com.openggf.level.objects.ObjectSpawn(1024, 512, original.objectId(), 3, 0, true, 512, 10000);
            edited.addObjectSpawn(added);
            edited.getMap().setValue(0, 4, 1, (byte) 0);
            int pixel = (source.getPattern(1).getPixel(0, 0) + 1) & 15;
            edited.getPattern(1).setPixel(0, 0, (byte) pixel);
            var ring = new com.openggf.level.rings.RingSpawn(1100, 500, 10000);
            edited.addRingSpawn(ring);
            var store = new EditorProjectStore();
            var file = directory.resolve("edited.ogfproject");
            store.save(file, document);
            var reopened = store.open(file);
            var game = new SonicCdGame(disc);
            var replay = game.loadProject(reopened.nativeSource(), reopened.level());
            assertSame(replay, game.currentLevel());
            assertNotNull(game.currentImage());
            assertNotSame(source, replay);
            assertEquals(source.payload(), replay.payload());
            assertEquals(0, replay.getMap().getValue(0, 4, 1));
            assertEquals(pixel, replay.getPattern(1).getPixel(0, 0));
            assertTrue(replay.getObjects().contains(moved));
            assertTrue(replay.getObjects().contains(added));
            assertFalse(replay.getObjects().contains(deleted));
            assertTrue(replay.getRings().contains(ring));
            assertEquals(source.subtype2(original), replay.subtype2(moved));
            assertEquals(source.placements().get(original.layoutIndex()).timeZones(),
                    replay.placements().stream().filter(p -> p.layoutIndex() == moved.layoutIndex()).findFirst().orElseThrow().timeZones());
            assertEquals(0, replay.subtype2(added));
            assertEquals(0x40, replay.placements().stream().filter(p -> p.layoutIndex() == added.layoutIndex()).findFirst().orElseThrow().timeZones());
            assertEquals(source.ringObjectPlacementMapping(), replay.ringObjectPlacementMapping());
            assertEquals(source.hasLoopFlag(4, 1), replay.hasLoopFlag(4, 1));
            assertNotEquals(pixel, source.getPattern(1).getPixel(0, 0));
        }
    }

    @Test void nativeProjectCreateSaveReopenRetainsSourceWithoutWeakeningFlatGuard() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var nativeLevel = new CdStageLoader(disc).load(CdStagePayload.R11A, 0, false);
            var metadata = new EditorProjectMetadata(GameId.SCD, "Edited R11A", 0, 0, disc.identity(),
                    nativeLevel.getMinX(), nativeLevel.getMinY(), nativeLevel.getMinX(), nativeLevel.getMaxX(),
                    nativeLevel.getMinY(), nativeLevel.getMaxY(), new FullLevelExporter.ExportMusic.Stock(1), null);
            assertThrows(IllegalArgumentException.class, () -> EditorProjectLevel.copyOf(nativeLevel));
            var document = EditorProjectDocument.create(metadata, nativeLevel);
            var file = directory.resolve("r11a.ogfproject");
            var store = new EditorProjectStore();
            store.save(file, document);
            var reopened = store.open(file);
            assertEquals(metadata, reopened.metadata());
            assertNotNull(reopened.nativeSource());
            assertEquals(nativeLevel.nativeProjectSource(), reopened.nativeSource());
            assertEquals(disc.identity(), reopened.nativeSource().mediaIdentity());
            assertEquals(CdStagePayload.R11A, new CdStageLoader(disc).load(reopened.nativeSource(), 0, false).payload());
            var reference = reopened.nativeSource();
            var wrongDisc = new com.openggf.game.internal.NativeProjectSource("foreign-disc", reference.stage(),
                    reference.zone(), reference.act(), reference.period(), reference.program(), reference.blockGridSide());
            assertThrows(java.io.IOException.class, () -> new CdStageLoader(disc).load(wrongDisc, 0, false));
            var wrongProgram = new com.openggf.game.internal.NativeProjectSource(reference.mediaIdentity(), reference.stage(),
                    reference.zone(), reference.act(), reference.period(), "R12A__.MMD;1", reference.blockGridSide());
            assertThrows(java.io.IOException.class, () -> new CdStageLoader(disc).load(wrongProgram, 0, false));
            assertEquals(16, reopened.level().getChunksPerBlockSide());
            assertFalse(reopened.isModified());
        }
    }
}
