package com.openggf.game.dataselect;

import com.openggf.graphics.RgbaImage;
import com.openggf.version.AppVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TestPreviewCacheFiles {
    @TempDir Path root;

    @Test
    void versionFailuresDoNotEvaluateRomHashButZoneFailureDoes() {
        AtomicInteger calls = new AtomicInteger();
        var hash = (java.util.function.Supplier<String>) () -> { calls.incrementAndGet(); return "rom"; };
        assertFalse(PreviewCacheFiles.valid(root,
                new PreviewCacheFiles.Manifest("old", 5, "rom", null), 5, hash, Set.of("zone"), 2, 2));
        assertFalse(PreviewCacheFiles.valid(root,
                new PreviewCacheFiles.Manifest(AppVersion.get(), 4, "rom", null), 5, hash, Set.of("zone"), 2, 2));
        assertEquals(0, calls.get());
        assertFalse(PreviewCacheFiles.valid(root,
                new PreviewCacheFiles.Manifest(AppVersion.get(), 5, "rom", null), 5, hash, Set.of("zone"), 2, 2));
        assertEquals(1, calls.get());
    }

    @Test
    void loadingIsImmutableAndDiscardsPartialResultsWhenLaterImageDisappears() throws Exception {
        PreviewImageFiles.writePng(new RgbaImage(1, 1, new int[]{0xff123456}), root, "first", "first.png");
        var zones = Map.of("first", "first.png", "second", "missing.png");
        var loaded = PreviewCacheFiles.load(root, zones, List.of(1), id -> "first");
        assertEquals(0xff123456, loaded.get(1).argb(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> loaded.clear());
        assertTrue(PreviewCacheFiles.load(root, zones, List.of(1, 2),
                id -> id == 1 ? "first" : "second").isEmpty());
        assertTrue(PreviewCacheFiles.load(root, zones, List.of(1), id -> null).isEmpty());
    }
}
