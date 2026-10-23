package com.openggf.editor.project;

import com.openggf.editor.EditorPlacementIdAllocator;
import com.openggf.editor.EditorSpawnFactory;
import com.openggf.editor.persistence.EditorSaveManager;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.editor.selection.CellRectangle;
import com.openggf.editor.selection.EditorRegionOperations;
import com.openggf.game.GameId;
import com.openggf.game.MusicReference;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.authored.AuthoredGizmos;
import com.openggf.level.objects.authored.AuthoredObjectProperties;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinitionParser;
import com.openggf.mods.code.ModZoneLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestAuthoredGizmoPersistence {
    @TempDir Path directory;

    @Test void completeProjectCloneSaveReopenRetainEveryTypedPlacement() throws Exception {
        var level = level();
        var document = EditorProjectDocument.create(TestEditorProjects.metadata(GameId.S2, level), level);
        assertEquals(level.getObjects(), document.level().getObjects());
        assertEquals(level.getObjects(), EditorProjectLevel.copyOf(level).getObjects());
        var store = new EditorProjectStore();
        var path = directory.resolve("gizmos.ogfproject");
        store.save(path, document);
        var reopened = store.open(path);
        assertEquals(level.getObjects(), reopened.level().getObjects());
        assertFalse(reopened.isModified());
        assertEquals(5, reopened.level().getObjects().stream().filter(spawn -> spawn.authoredProperties() != null).count());
    }

    @Test void bakedExportStrictParserAndRuntimeLoaderRetainEveryTypedPlacement() throws Exception {
        var level = level();
        Path output = directory.resolve("baked");
        new FullLevelExporter(path -> {}).export(level, new FullLevelExporter.ExportRequest(output,
                "Typed gizmos", 0x40, 0x400, 64, 64, new FullLevelExporter.ExportMusic.Stock(0x81), GameId.S2, null));
        try (var assets = ModAssetRoot.directory(directory, output, ModInputLimits.production(), DirectoryAccess.TEST)) {
            var parsed = ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
            assertEquals(level.getObjects(), ModZoneLoader.decodeObjects(parsed, true));
            assertEquals(level.getObjects().stream().map(ObjectSpawn::authoredProperties).toList(),
                    parsed.objects().stream().map(entry -> entry.authoredProperties()).toList());
        }
    }

    @Test void keyedOverrideSaveAndLoadRetainPropertiesWithNoFormatVersionChange() throws Exception {
        var source = level();
        var encoding = new CommonObjectPlacementEncoding();
        var manager = new EditorSaveManager(directory.resolve("overrides"), AuthoredGizmos::handlesKey,
                finding -> fail(finding.message()));
        manager.save(GameId.S2, encoding, 2, 1, source);
        var target = MutableLevel.snapshot(new TestEditorProjects.Fixture(GameId.S2));
        assertEquals(EditorSaveManager.ApplyResult.APPLIED, manager.tryApplyEdits(GameId.S2, encoding, 2, 1, target));
        assertEquals(source.getObjects(), target.getObjects());
    }

    @Test void exportRejectsCanvasOverflowBeforeCreatingAnyOutput() {
        var level = level();
        level.replaceSpawnsPersisted(List.of(spawn(500, 10, 20,
                new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.stock(7), true))), List.of(), Map.of());
        Path output = directory.resolve("outside-canvas");
        var error = assertThrows(IllegalArgumentException.class, () -> new FullLevelExporter(path -> {}).export(level,
                new FullLevelExporter.ExportRequest(output, "Outside canvas", 0x40, 0x400, 64, 64,
                        new FullLevelExporter.ExportMusic.Stock(0x81), GameId.S2, null)));
        assertTrue(error.getMessage().contains("level canvas"));
        assertFalse(java.nio.file.Files.exists(output));
    }

    @Test void runtimeDecodeRejectsRegionsOutsideCanvasEvenWhenNativeCoordinatesAreValid() throws Exception {
        var level = level();
        Path output = directory.resolve("baked-outside");
        new FullLevelExporter(path -> {}).export(level, new FullLevelExporter.ExportRequest(output,
                "Typed gizmos", 0x40, 0x400, 64, 64, new FullLevelExporter.ExportMusic.Stock(0x81), GameId.S2, null));
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var metadata = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(output.resolve("level.json").toFile());
        var first = (com.fasterxml.jackson.databind.node.ObjectNode) metadata.get("objects").get(0);
        first.put("x", 500);
        json.writeValue(output.resolve("level.json").toFile(), metadata);
        try (var assets = ModAssetRoot.directory(directory, output, ModInputLimits.production(), DirectoryAccess.TEST)) {
            var parsed = ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
            assertThrows(java.io.IOException.class, () -> ModZoneLoader.decodeObjects(parsed, true));
        }
    }

    @Test void nativePropertyChangesMovesAndClipboardCopiesRetainTypedProperties() {
        var level = level();
        var encoding = new CommonObjectPlacementEncoding();
        var factory = new EditorSpawnFactory(encoding, new EditorPlacementIdAllocator(level));
        ObjectSpawn source = level.getObjects().getFirst();
        assertEquals(source.authoredProperties(), factory.moveObjectSpawn(source, 20, 20).authoredProperties());
        assertEquals(source.authoredProperties(), factory.withProperties(source, 7, 1, true).authoredProperties());
        var regions = new EditorRegionOperations(level, factory);
        var clipboard = regions.copy(0, new CellRectangle(0, 0, 1, 1));
        var preview = regions.preview(clipboard, 0, 1, 0);
        assertEquals(source.authoredProperties(), preview.objects().getFirst().authoredProperties());
        var command = regions.paste(clipboard, 0, 1, 0); command.apply();
        var pasted = level.getObjects().stream().filter(spawn -> spawn.layoutIndex() > 14).findFirst().orElseThrow();
        assertEquals(source.authoredProperties(), pasted.authoredProperties());
        assertEquals(138, pasted.x());
        command.undo(); assertEquals(5, level.getObjects().size());
        command.apply(); assertEquals(6, level.getObjects().size());
    }

    @Test void copyingACompleteWarpPairAssignsFreshUniqueLinksInOneUndoStep() {
        var level = level();
        var regions = new EditorRegionOperations(level, new EditorSpawnFactory(new CommonObjectPlacementEncoding(), new EditorPlacementIdAllocator(level)));
        var clipboard = regions.copy(0, new CellRectangle(0, 1, 2, 1));
        var first = regions.paste(clipboard, 0, 0, 2); first.apply();
        var second = regions.paste(clipboard, 0, 0, 3); second.apply();
        var links = level.getObjects().stream().map(ObjectSpawn::authoredProperties)
                .filter(p -> p instanceof AuthoredObjectProperties.Warp).map(p -> ((AuthoredObjectProperties.Warp) p).link()).toList();
        assertEquals(2, java.util.Collections.frequency(links, "pair"));
        assertEquals(2, java.util.Collections.frequency(links, "pair-copy"));
        assertEquals(2, java.util.Collections.frequency(links, "pair-copy-2"));
        assertDoesNotThrow(() -> AuthoredGizmos.validatePlacements(level.getObjects()));
        second.undo(); first.undo(); assertEquals(5, level.getObjects().size());
    }

    @Test void pastingOneEndpointCannotCreateAnAmbiguousThirdEndpoint() {
        var level = level();
        var regions = new EditorRegionOperations(level, new EditorSpawnFactory(new CommonObjectPlacementEncoding(), new EditorPlacementIdAllocator(level)));
        var clipboard = regions.copy(0, new CellRectangle(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> regions.paste(clipboard, 0, 0, 2));
        assertEquals(5, level.getObjects().size(), "rejected paste must not modify placements");
        var source = level.getObjects().stream().filter(object -> object.layoutIndex() == 13).findFirst().orElseThrow();
        level.removeObjectSpawn(source);
        var paste = regions.paste(clipboard, 0, 0, 2); paste.apply();
        assertDoesNotThrow(() -> AuthoredGizmos.validatePlacements(level.getObjects()), "one copied endpoint may finish an incomplete pair");
    }

    private static MutableLevel level() {
        var level = MutableLevel.snapshot(new TestEditorProjects.Fixture(GameId.S2));
        for (int line = 0; line < 4; line++) level.setPalette(line, new Palette());
        level.replaceSpawnsPersisted(List.of(
                spawn(10, 10, 10, new AuthoredObjectProperties.CameraZone(64, 64, 0, 400, 0, 300,
                        AuthoredObjectProperties.ReleaseCondition.CROSS_X, 350)),
                spawn(150, 10, 11, new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.stock(0x81), true)),
                spawn(10, 150, 12, new AuthoredObjectProperties.Warp(32, 64, "pair", true)),
                spawn(150, 150, 13, new AuthoredObjectProperties.Warp(32, 64, "pair", true)),
                spawn(250, 10, 14, new AuthoredObjectProperties.StageplayTrigger(32, 64, "intro.stageplay",
                        AuthoredObjectProperties.Trigger.PROXIMITY, true, true, false))), List.of(), Map.of());
        return level;
    }

    private static ObjectSpawn spawn(int x, int y, int id, AuthoredObjectProperties properties) {
        var encoded = new CommonObjectPlacementEncoding().createKeyed(x, y, properties.objectKey(), 0, 0, true, id);
        return new ObjectSpawn(x, y, encoded.objectId(), 0, 0, true, encoded.rawYWord(), id,
                encoded.ownerModId(), encoded.objectKey(), properties);
    }
}
