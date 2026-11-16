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
