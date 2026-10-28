package com.openggf;

import com.openggf.graphics.shaderlib.*;

import com.openggf.game.MenuChrome;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestDisplayShaderPickerRenderer {
    @Test void rowWindowContainsFocusAtBothEndsAndEveryInteriorPosition() {
        for (int total = 1; total <= 100; total++) {
            for (int selected = 0; selected < total; selected++) {
                int first = DisplayShaderPickerRenderer.firstRow(selected, total);
                assertTrue(first >= 0 && first <= Math.max(0, total - 5));
                assertTrue(first <= selected && selected < Math.min(total, first + 5));
            }
        }
        assertEquals(0, DisplayShaderPickerRenderer.firstRow(0, 23));
        assertEquals(18, DisplayShaderPickerRenderer.firstRow(22, 23));
    }

    @Test void breadcrumbRetainsAnchorAndNearestFolderWithinNativeCellBudget() {
        assertEquals("shaders / crt", DisplayShaderPickerRenderer.breadcrumb("shaders", "crt", 270));
        String folder = "libretro-glsl/crt/handheld/deeply-nested/shaders";
        // Proportional compact text: the whole 329 px path fits the wide panel.
        assertEquals("shaders / libretro-glsl / crt / handheld / deeply-nested / shaders",
                DisplayShaderPickerRenderer.breadcrumb("/tmp/library/shaders/", folder, 338));
        for (int width : new int[]{180, 258}) {
            String path = DisplayShaderPickerRenderer.breadcrumb("/tmp/library/shaders/", folder, width);
            assertTrue(path.startsWith("shaders / ... / "), path);
            assertTrue(path.endsWith("shaders"), path);
            assertTrue(MenuChrome.compactWidth(path) <= width, path);
        }
        assertEquals("shaders", DisplayShaderPickerRenderer.breadcrumb(null, "", 260));
        assertEquals("/", DisplayShaderPickerRenderer.breadcrumb("/", "", 260));
        for (int width = 0; width < 160; width++)
            assertTrue(MenuChrome.compactWidth(DisplayShaderPickerRenderer.breadcrumb("shaders", folder, width)) <= width);
    }

    @Test void labelsKeepDistinguishingNamesWhileRemovingFormatAndSeparators() {
        var model = new DisplayShaderSelectionModel(List.of(preset("crt/crt-easymode.glslp")));
        var folder = model.filter("").get(1);
        assertEquals("CRT", DisplayShaderPickerRenderer.rowLabel(folder), "Vowel-less folder names read as initialisms");
        assertEquals("OPEN", DisplayShaderPickerRenderer.confirmLabel(folder));
        model.enterFolder("crt");
        var rows = model.filter("");
        assertEquals("Parent folder", DisplayShaderPickerRenderer.rowLabel(rows.getFirst()));
        assertEquals("UP", DisplayShaderPickerRenderer.confirmLabel(rows.getFirst()));
        assertEquals("CRT easymode", DisplayShaderPickerRenderer.rowLabel(rows.get(1)));
        assertEquals("APPLY", DisplayShaderPickerRenderer.confirmLabel(rows.get(1)));
    }

    @Test void activeMarkFollowsAppliedPresetRatherThanFocusAndMatchesStoredPathCase() {
        var active = preset("crt/easymode.glslp");
        var model = new DisplayShaderSelectionModel(List.of(active, preset("crt/scanlines.glsl")));
        model.enterFolder("crt");
        var rows = model.filter("");
        assertFalse(DisplayShaderPickerRenderer.isActive(rows.getFirst(), active));
        assertTrue(DisplayShaderPickerRenderer.isActive(rows.get(1), preset("CRT/EASYMODE.GLSLP")));
        assertFalse(DisplayShaderPickerRenderer.isActive(rows.get(2), active));
        var off = new DisplayShaderSelectionModel(List.of()).filter("").getFirst();
        assertTrue(DisplayShaderPickerRenderer.isActive(off, null));
        assertFalse(DisplayShaderPickerRenderer.isActive(off, active));
    }

    @Test void closedPickerAndRepeatedCloseDoNotAllocateGlResources() {
        var picker = new DisplayShaderPickerController(new DisplayShaderSelectionModel(List.of()), GLFW_KEY_F8);
        try (var renderer = new DisplayShaderPickerRenderer()) {
            renderer.render(null, null, null, null, false, null, 320);
            renderer.render(picker, null, null, null, false, null, 320);
            renderer.close();
        }
    }

    private static DisplayShaderPresetRef preset(String path) {
        return new DisplayShaderPresetRef(DisplayShaderPresetRef.Kind.GLSLP, path, Path.of("shaders").resolve(path));
    }
}
