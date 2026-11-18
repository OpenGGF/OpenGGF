package com.openggf.game.soniccd;

import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.editor.project.*;
import com.openggf.game.GameId;
import com.openggf.game.soniccd.level.CdStageLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestCdEditorProjectSource {
    @TempDir Path directory;

    @Test void contentImportRoundTripRetainsNativeSourceAndRejectsFlatExport() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var game = new SonicCdGame(disc);
            var source = (com.openggf.game.soniccd.level.CdLevel) game.loadLevel(CdStagePayload.R11B.ordinal());
            var start = source.nativeProjectStart();
            var metadata = new EditorProjectMetadata(GameId.SCD, "Imported past", 0, 1, disc.identity(),
                    start[0], start[1], source.getMinX(), source.getMaxX(), source.getMinY(), source.getMaxY(),
                    new FullLevelExporter.ExportMusic.Stock(1), null);
            var document = EditorProjectDocument.create(metadata, source);
            document.level().getMap().setValue(0, 8, 1, (byte) 0);
            var store = new EditorProjectStore();
            var exported = directory.resolve("source.ogfproject"); store.save(exported, document);
            var importer = new com.openggf.ContentImportService(directory.resolve("mods"));
            try (var imported = importer.inspect(exported)) {
                assertEquals(com.openggf.ContentImportService.Kind.PROJECT, imported.kind());
                assertEquals(document.nativeSource(), imported.project().nativeSource());
                assertEquals(metadata, imported.project().metadata());
                assertEquals(0, imported.project().level().getMap().getValue(0, 8, 1));
                var second = directory.resolve("roundtrip.ogfproject"); store.save(second, imported.project());
                var reopened = store.open(second);
                assertEquals(source.nativeProjectSource(), reopened.nativeSource());
                assertEquals(CdStagePayload.R11B, game.loadProject(reopened.nativeSource(), reopened.level()).payload());
                var failure = assertThrows(IllegalArgumentException.class, () -> EditorProjectRuntimeModule.request(
                        reopened, directory.resolve("flat"), start[0], start[1]));
                assertTrue(failure.getMessage().contains(".ogfproject"));
                assertFalse(java.nio.file.Files.exists(directory.resolve("flat")));
            }
        }
    }

    @Test void portableNativeFolderAndZipRestoreEditsThroughContentImport() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var loader = new CdStageLoader(disc);
            var source = loader.load(CdStagePayload.R11B, 0, false);
            var start = source.nativeProjectStart();
            var metadata = new EditorProjectMetadata(GameId.SCD, "Portable edits", 0, 1, disc.identity(),
                    start[0], start[1], source.getMinX(), source.getMaxX(), source.getMinY(), source.getMaxY(),
                    new FullLevelExporter.ExportMusic.Stock(1), null);
            var document = EditorProjectDocument.create(metadata, source);
            var edited = document.level();
            int mapValue = Byte.toUnsignedInt(source.getMap().getValue(0, 0, 0));
            int newCell = (mapValue + 1) % source.getBlockCount();
            edited.getMap().setValue(0, 0, 0, (byte) newCell);
            int newPixel = (source.getPattern(1).getPixel(0, 0) + 1) & 15;
            edited.getPattern(1).setPixel(0, 0, (byte) newPixel);
            var unchanged = edited.getObjects().stream().filter(o -> o.objectId() != 0x10).findFirst().orElseThrow();
            var moved = edited.getObjects().stream().filter(o -> o.objectId() != 0x10 && !o.equals(unchanged)).findFirst().orElseThrow();
            var replacement = moved.withPosition(moved.x() + 8, moved.y());
            edited.removeObjectSpawn(unchanged);
            edited.moveObjectSpawn(moved, replacement);
            var ring = new com.openggf.level.rings.RingSpawn(1024, 128, 10000);
            edited.addRingSpawn(ring);

            var importer = new com.openggf.ContentImportService(directory.resolve("mods"));
            Path folder = directory.resolve("portable");
            Path zip = directory.resolve("portable.zip");
            for (Path destination : java.util.List.of(folder, zip)) {
                assertEquals(destination, NativeEditorProjectPackage.export(destination, document, source));
                assertThrows(java.io.IOException.class, () -> NativeEditorProjectPackage.export(destination, document, source));
                try (var prepared = importer.inspect(destination)) {
                    assertEquals(com.openggf.ContentImportService.Kind.PROJECT, prepared.kind());
                    var portable = prepared.nativeProject().orElseThrow();
                    assertEquals(source.nativeProjectSource(), portable.source());
                    var wrong = loader.load(CdStagePayload.R11A, 0, false);
                    assertThrows(java.io.IOException.class, () -> portable.materialize(wrong));
                    var project = portable.materialize(loader.load(CdStagePayload.R11B, 0, false));
                    assertEquals(metadata, project.metadata());
                    assertNull(project.path(), "import creates a new editable project");
                    assertEquals(newCell, Byte.toUnsignedInt(project.level().getMap().getValue(0, 0, 0)));
                    assertEquals(newPixel, project.level().getPattern(1).getPixel(0, 0));
                    assertEquals(edited.getObjects(), project.level().getObjects());
                    assertEquals(edited.getRings(), project.level().getRings());
                    assertNotNull(project.level().getRingSpriteSheet());
                    var replay = new SonicCdGame(disc).loadProject(project.nativeSource(), project.level());
                    assertEquals(source.subtype2(moved), replay.subtype2(replacement));
                    assertTrue(replay.getRings().contains(ring));
                }
            }
            try (var entries = Files.list(folder)) {
                assertEquals(java.util.List.of(NativeEditorProjectPackage.ENTRY),
                        entries.map(p -> p.getFileName().toString()).toList());
            }
            assertTrue(Files.size(folder.resolve(NativeEditorProjectPackage.ENTRY)) < 16_384,
                    "small edits carry no unchanged terrain or art banks");
            assertNull(document.path());
            assertTrue(document.isModified());
        }
    }

    @Test void creatorSelectsCdZoneActAndPeriod() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var module = new SonicCdGameModule();
            var game = (SonicCdGame) module.createGame(disc);
            var shown = new java.util.concurrent.atomic.AtomicReference<EditorProjectDocument>();
            var host = new EditorProjectWorkspace.Host() {
                public com.openggf.game.GameModule selectGame(GameId id) { assertEquals(GameId.SCD, id); return module; }
                public com.openggf.game.GameModule currentModule() { return module; }
                public com.openggf.level.Level loadLibrary(int z, int a) throws java.io.IOException {
                    return game.loadLevel(module.getZoneRegistry().getLevelDataForZone(z).get(a).levelIndex());
                }
                public String sourceFingerprint() { return disc.identity(); }
                public com.openggf.game.session.EditorCursorState cursor() { return new com.openggf.game.session.EditorCursorState(0,0); }
                public void finishPendingEdits() { }
                public void showDocument(EditorProjectDocument d) { shown.set(d); }
                public void playtest(EditorProjectDocument d, boolean cursor) { }
                public void closeDocument() { }
                public void menuClosed(EditorProjectDocument d) { }
                public void execute(com.openggf.editor.EditorCommand c) { c.apply(); }
                public void status(String text) { }
            };
            var workspace = new EditorProjectWorkspace(host);
            workspace.menu().onShow(menu -> {});
            workspace.openMenu(); workspace.menu().runAction(0);
            var games = workspace.menu().actionView().rows();
            int cd = java.util.stream.IntStream.range(0, games.size()).filter(i -> games.get(i).label().contains("CD")).findFirst().orElseThrow();
            workspace.menu().runAction(cd); workspace.menu().runAction(0);
            assertNull(workspace.menu().error());
            assertTrue(workspace.menu().actionView().rows().get(1).label().toLowerCase().contains("past"));
            workspace.menu().runAction(1);
            workspace.menu().setValue("name", "Past project"); workspace.menu().submit();
            assertNull(workspace.menu().error());
            var document = shown.get(); assertNotNull(document);
            assertEquals("R11B", document.nativeSource().stage());
            assertEquals("PAST", document.nativeSource().period());
            assertEquals(1, document.metadata().sourceAct());
            assertEquals(game.currentLevel().startX(), document.metadata().startX());
            assertEquals(game.currentLevel().startY(), document.metadata().startY());

            var activeLevel = game.currentLevel();
            Path archive = directory.resolve("workspace.zip");
            workspace.export();
            workspace.menu().setValue("path", archive.toString()); workspace.menu().submit();
            assertNull(workspace.menu().error());
            assertSame(activeLevel, game.currentLevel(), "export does not load into gameplay");
            assertSame(document, workspace.document());
            assertTrue(document.isModified());
            var importer = new com.openggf.ContentImportService(directory.resolve("mods"));
            try (var prepared = importer.inspect(archive)) {
                workspace.openNativeProject(prepared.nativeProject().orElseThrow());
                assertSame(document, workspace.document(), "opening asks before replacing dirty work");
                workspace.menu().runAction(2);
                assertNull(workspace.menu().error());
                assertNotSame(document, workspace.document());
                assertEquals(document.metadata(), workspace.document().metadata());
                assertEquals(document.nativeSource(), workspace.document().nativeSource());
                assertNull(workspace.document().path());
            }
            var imported = workspace.document();
            int width = imported.level().getMap().getWidth();
            workspace.resize();
            workspace.menu().setValue("width", "65");
            workspace.menu().submit();
            assertNotNull(workspace.menu().error());
            assertSame(imported, workspace.document(), "an invalid resize leaves the current project open");

            workspace.resize();
            workspace.menu().setValue("width", Integer.toString(width - 1));
            workspace.menu().setValue("crop", "yes");
            workspace.menu().submit();
            assertNull(workspace.menu().error());
            assertEquals(width - 1, workspace.document().level().getMap().getWidth());
            assertEquals(imported.nativeSource(), workspace.document().nativeSource());
        }
    }

    @Test void cropKeepsSurvivingNativeRemapsAndRejectsOutOfWindowReplay() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var loader = new CdStageLoader(disc);
            var source = loader.load(CdStagePayload.R11B, 0, false);
            int width = source.getMap().getWidth();
            int height = source.getMap().getHeight();
            int croppedWidth = width - 1;
            var metadata = new EditorProjectMetadata(GameId.SCD, "Cropped stage", 0, 1, disc.identity(),
                    source.startX(), source.startY(), source.getMinX(), source.getMaxX(), source.getMinY(),
                    source.getMaxY(), new FullLevelExporter.ExportMusic.Stock(1), null);
            var project = EditorProjectDocument.create(metadata, source);
            int croppedX = croppedWidth * source.getBlockPixelSize() + 32;
            var croppedObject = new com.openggf.level.objects.ObjectSpawn(croppedX, 128, 3, 0, 0, true, 128, 10_000);
            var croppedRing = new com.openggf.level.rings.RingSpawn(croppedX, 128, 10_000);
            project.level().addObjectSpawn(croppedObject);
            project.level().addRingSpawn(croppedRing);
            assertThrows(IllegalArgumentException.class, () -> project.resize(croppedWidth, height, false));
            assertEquals(width, project.level().getMap().getWidth());
            project.resize(croppedWidth, height, true);
            var replay = new SonicCdGame(disc).loadProject(project.nativeSource(), project.level());
            assertEquals(croppedWidth, replay.getMap().getWidth());
            assertEquals(-1, replay.collisionLayoutColumn(croppedWidth * 256), "collision never reads a cropped column");
            assertEquals(source.verticalScrollWrapRange(), replay.verticalScrollWrapRange());
            assertEquals(source.collisionLayoutYMask(), replay.collisionLayoutYMask());
            assertTrue(replay.getObjects().stream().allMatch(o -> o.x() < croppedWidth * 256));
            assertTrue(replay.getRings().stream().allMatch(r -> r.x() < croppedWidth * 256));
            boolean comparedLoop = false;
            for (int y = 0; y < height; y++) {
                assertEquals(source.backgroundLayoutX(896, y * 256 + 16), replay.backgroundLayoutX(896, y * 256 + 16));
                for (int x = 0; x < croppedWidth; x++) {
                    assertEquals(source.getMap().getValue(0, x, y), replay.getMap().getValue(0, x, y));
                    assertEquals(source.hasLoopFlag(x, y), replay.hasLoopFlag(x, y));
                    comparedLoop |= source.hasLoopFlag(x, y);
                }
            }
            assertTrue(comparedLoop, "the overlap includes native loop cells");
            assertFalse(replay.getObjects().contains(croppedObject));
            assertFalse(replay.getRings().contains(croppedRing));
            assertTrue(source.hasLoopFlag(7, 2));
            project.level().getMap().setValue(0, 7, 2, (byte) 0);
            var clearedLoop = new SonicCdGame(disc).loadProject(project.nativeSource(), project.level());
            assertFalse(clearedLoop.hasLoopFlag(7, 2), "replacing an original loop cell clears its flag");
            assertTrue(clearedLoop.hasLoopFlag(33, 3), "the other loop retains its native path");

            project.resize(65, height, false);
            assertThrows(java.io.IOException.class,
                    () -> new SonicCdGame(disc).loadProject(project.nativeSource(), project.level()));
            var ttz = loader.load(CdStagePayload.R41A, 2, false);
            assertThrows(java.io.IOException.class, () -> ttz.validateProjectDimensions(32, 9));
        }
    }

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
