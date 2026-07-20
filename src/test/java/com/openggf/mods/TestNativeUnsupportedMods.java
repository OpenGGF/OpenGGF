package com.openggf.mods;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestNativeUnsupportedMods {

    private static ModDescriptor descriptor(String id, boolean code) {
        ModManifest manifest = new ModManifest(1, id, id + "-name",
                new SemanticVersion(1, 0, 0), List.of("a"), "d",
                VersionRange.parse(">=2.0.0 <3.0.0"),
                ModType.PATCH, "s2", code ? "example." + id + ".Entry" : null,
                List.of(), Map.of(), Map.of(), null, OptionalInt.empty());
        return new ModDescriptor(Path.of("mods", id + ".jar"), manifest,
                "0".repeat(64), code, List.of());
    }

    private static ModState state(ModState.Entry... entries) {
        return new ModState(ModState.CURRENT_FORMAT_VERSION, List.of(entries));
    }

    @Test
    void includesEnabledCodeModExcludesDataAndDisabled() {
        List<ModCatalogEntry> scanned = List.of(
                descriptor("codeon", true), descriptor("codeoff", true),
                descriptor("dataon", false));
        ModState st = state(
                new ModState.Entry("codeon", true, 0),
                new ModState.Entry("codeoff", false, 1),
                new ModState.Entry("dataon", true, 2));

        List<ModDescriptor> result = NativeUnsupportedMods.compute(scanned, st, false);

        assertEquals(List.of("codeon"),
                result.stream().map(d -> d.manifest().id()).toList());
    }

    @Test
    void enabledUntrustedCodeModStillIncluded() {
        List<ModCatalogEntry> scanned = List.of(descriptor("codeon", true));
        ModState st = state(new ModState.Entry("codeon", true, 0));
        assertEquals(1, NativeUnsupportedMods.compute(scanned, st, false).size());
    }

    @Test
    void disabledUntrustedCodeModExcluded() {
        List<ModCatalogEntry> scanned = List.of(descriptor("codeoff", true));
        ModState st = state(new ModState.Entry("codeoff", false, 0));
        assertTrue(NativeUnsupportedMods.compute(scanned, st, false).isEmpty());
    }

    @Test
    void alwaysEmptyWhenSupported() {
        List<ModCatalogEntry> scanned = List.of(descriptor("codeon", true));
        ModState st = state(new ModState.Entry("codeon", true, 0));
        assertTrue(NativeUnsupportedMods.compute(scanned, st, true).isEmpty());
    }

    @Test
    void noticeLinesTruncatesWithAndNMore() {
        List<String> names = List.of("a", "b", "c", "d", "e");
        List<String> lines = NativeUnsupportedMods.noticeLines(names, 3);
        assertEquals(NativeUnsupportedMods.NOTICE_HEADER, lines.get(0));
        assertEquals(List.of("a", "b"), lines.subList(1, 3));
        assertEquals("…and 3 more", lines.get(3));
        assertEquals(4, lines.size());
    }

    @Test
    void noticeLinesNoTruncationAtBoundary() {
        List<String> names = List.of("a", "b", "c");
        List<String> lines = NativeUnsupportedMods.noticeLines(names, 3);
        assertEquals(NativeUnsupportedMods.NOTICE_HEADER, lines.get(0));
        assertEquals(List.of("a", "b", "c"), lines.subList(1, 4));
        assertEquals(4, lines.size());
    }

    @Test
    void blocksStandaloneOnlyWhenCodeAndUnsupported() {
        assertTrue(NativeUnsupportedMods.blocksStandalone(descriptor("s", true), false));
        assertFalse(NativeUnsupportedMods.blocksStandalone(descriptor("s", true), true));
        assertFalse(NativeUnsupportedMods.blocksStandalone(descriptor("s", false), false));
    }

    @Test
    void freshDropInWithoutStateEntryExcluded() {
        List<ModCatalogEntry> scanned = List.of(descriptor("codenew", true));
        assertTrue(NativeUnsupportedMods.compute(scanned, ModState.EMPTY, false).isEmpty());
    }

    @Test
    void enabledStandaloneCodeModIncluded() {
        List<ModCatalogEntry> scanned = List.of(descriptor("standalone", true));
        ModState st = state(new ModState.Entry("standalone", true, 0));
        assertEquals(List.of("standalone"),
                NativeUnsupportedMods.compute(scanned, st, false).stream()
                        .map(d -> d.manifest().id()).toList());
    }
}
