package com.openggf.graphics.shaderlib;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestDisplayShaderSelectionModel {

    @TempDir
    Path tempDir;

    @Test
    public void emptyQueryReturnsOffThenSortedEntries() throws IOException {
        DisplayShaderSelectionModel model = new DisplayShaderSelectionModel(libraryWith(
                "scanlines/zebra.glslp",
                "crt/crt-easymode.glslp",
                "BizHawk/BizScanlines.cgp"));

        List<DisplayShaderSelectionModel.SelectionItem> items = model.filter("");

        assertEquals(List.of(
                "Off",
                "BizHawk/BizScanlines.cgp",
                "crt/crt-easymode.glslp",
                "scanlines/zebra.glslp"), displayPaths(items));
        assertEquals(List.of(
                "System",
                "BizHawk",
                "crt",
                "scanlines"), categories(items));
    }

    @Test
    public void textQueryMatchesRootRelativePathCaseInsensitively() throws IOException {
        DisplayShaderSelectionModel model = new DisplayShaderSelectionModel(libraryWith(
                "libretro-glsl/crt/crt-easymode.glslp",
                "scanlines/zebra.glslp"));

        List<DisplayShaderSelectionModel.SelectionItem> items = model.filter("EASYMODE");

        assertEquals(List.of("Off", "libretro-glsl/crt/crt-easymode.glslp"), displayPaths(items));
    }

    @Test
    public void categoryQueryMatchesPathSegment() throws IOException {
        DisplayShaderSelectionModel model = new DisplayShaderSelectionModel(libraryWith(
                "RetroArch/shaders_glsl/scanlines/scanline.glslp",
                "crt/crt-easymode.glslp"));

        List<DisplayShaderSelectionModel.SelectionItem> items = model.filter("scanlines");

        assertEquals(List.of("Off", "RetroArch/shaders_glsl/scanlines/scanline.glslp"), displayPaths(items));
        assertEquals("scanlines", items.get(1).category());
    }

    @Test
    public void categoryListExcludesImplementationSegmentsShadersAndResources() throws IOException {
        DisplayShaderSelectionModel model = new DisplayShaderSelectionModel(libraryWith(
                "libretro-glsl/shaders/crt/crt-pass.glslp",
                "libretro-glsl/resources/crt/crt-lut.glslp",
                "RetroArch/shaders_glsl/scanlines/scanline.glslp"));

        assertEquals(List.of("crt", "scanlines"), model.categories());
    }

    @Test
    public void selectByVisibleIndexReturnsTheUnderlyingPresetRef() throws IOException {
        DisplayShaderLibrary library = libraryWith(
                "crt/crt-easymode.glslp",
                "scanlines/zebra.glslp");
        DisplayShaderSelectionModel model = new DisplayShaderSelectionModel(library);
        List<DisplayShaderSelectionModel.SelectionItem> visibleItems = model.filter("zebra");

        DisplayShaderPresetRef selected = model.select(visibleItems, 1);

        assertSame(library.at(2), selected);
        assertEquals("scanlines/zebra.glslp", selected.relativePath());
    }

    private DisplayShaderLibrary libraryWith(String... paths) throws IOException {
        Path root = tempDir.resolve("display-shaders");
        for (String path : paths) {
            write(root.resolve(path), "# shader preset\n");
        }
        return DisplayShaderLibrary.scan(root);
    }

    private static List<String> displayPaths(List<DisplayShaderSelectionModel.SelectionItem> items) {
        return items.stream()
                .map(DisplayShaderSelectionModel.SelectionItem::displayPath)
                .toList();
    }

    private static List<String> categories(List<DisplayShaderSelectionModel.SelectionItem> items) {
        return items.stream()
                .map(DisplayShaderSelectionModel.SelectionItem::category)
                .toList();
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
