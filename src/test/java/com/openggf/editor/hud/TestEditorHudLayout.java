package com.openggf.editor.hud;

import com.openggf.control.PhysicalGamepad;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEditorHudLayout {
    @Test
    void uiScaleKeepsAtLeast640By360Units() {
        assertEquals(2, EditorHudLayout.uiScale(1600, 900));
        assertEquals(3, EditorHudLayout.uiScale(1920, 1080));
        assertEquals(2, EditorHudLayout.uiScale(1280, 720));
        assertEquals(6, EditorHudLayout.uiScale(3840, 2160));
        assertEquals(1, EditorHudLayout.uiScale(1024, 600));
    }

    @Test
    void worldLayoutCentresTheGameAtTheLargestIntegerScaleBetweenTheBars() {
        EditorHudLayout layout = EditorHudLayout.of(1600, 900, 426, 224, false);
        int[] game = layout.gameViewport();
        assertEquals(3, game[4]);
        assertEquals(1278, game[2]);
        assertEquals(672, game[3]);
        assertNull(layout.workbench());
        // The game never sits under the top bar or the hotbar dock.
        assertTrue(layout.game().y() >= layout.topBar().bottom());
        assertTrue(layout.game().bottom() <= layout.dock().y());
    }

    @Test
    void workbenchLayoutShrinksTheGameToMakeRoomBesideIt() {
        EditorHudLayout layout = EditorHudLayout.of(1600, 900, 426, 224, true);
        assertEquals(2, layout.gameViewport()[4]);
        var bench = layout.workbench();
        assertTrue(bench.w() >= EditorHudLayout.MIN_WORKBENCH);
        assertTrue(bench.x() >= layout.game().right(), "The workbench sits beside the game, not over it");
        assertTrue(bench.right() <= layout.width());
    }

    @Test
    void hotbarSelectsExistingEntriesAndReplacesOnlyTheHighlightedSlot() {
        var hotbar = new EditorHudHotbar();
        hotbar.seed(EditorHud.LibraryKind.BLOCK, new int[] {5, 9, 2});
        assertEquals(5, hotbar.value(EditorHud.LibraryKind.BLOCK));
        hotbar.put(EditorHud.LibraryKind.BLOCK, 2);
        assertEquals(2, hotbar.selected(EditorHud.LibraryKind.BLOCK), "An entry already on the bar is selected, not duplicated");
        hotbar.put(EditorHud.LibraryKind.BLOCK, 40);
        assertArrayEquals(new int[] {5, 9, 40, -1, -1, -1, -1, -1, -1, -1}, hotbar.slots(EditorHud.LibraryKind.BLOCK));
        hotbar.cycle(EditorHud.LibraryKind.BLOCK, 1);
        assertEquals(0, hotbar.selected(EditorHud.LibraryKind.BLOCK), "Cycling skips empty slots");
        hotbar.cycle(EditorHud.LibraryKind.BLOCK, -1);
        assertEquals(2, hotbar.selected(EditorHud.LibraryKind.BLOCK));
    }

    @Test
    void hotbarSeedsWithTheMostUsedEntriesThenFillsFromOne() {
        int[] use = new int[16];
        use[0] = 99;
        use[7] = 4;
        use[3] = 9;
        assertArrayEquals(new int[] {3, 7, 1, 2, 4, 5, 6, 8, 9, 10}, EditorHud.ranked(use),
                "Index 0 is the usual empty block and is skipped even when common");
    }

    @Test
    void padReportsEdgesTriggersAndStickNavigation() {
        var pad = new EditorHudPad();
        boolean[] buttons = new boolean[15];
        float[] axes = {0, 0, 0, 0, -1, -1};
        pad.sample(List.of(new PhysicalGamepad(0, "Xbox", buttons, axes)));
        assertFalse(pad.pressed(EditorHudPad.Button.SOUTH));
        buttons[PhysicalGamepad.BUTTON_A] = true;
        axes[PhysicalGamepad.AXIS_RIGHT_TRIGGER] = .5f;
        axes[PhysicalGamepad.AXIS_LEFT_X] = .9f;
        pad.sample(List.of(new PhysicalGamepad(0, "Xbox", buttons, axes)));
        assertTrue(pad.pressed(EditorHudPad.Button.SOUTH));
        assertTrue(pad.pressed(EditorHudPad.Button.RT), "Three-quarter trigger pull counts as a press");
        assertEquals(1, pad.navX(), "The left stick navigates like the D-pad");
        pad.sample(List.of(new PhysicalGamepad(0, "Xbox", buttons, axes)));
        assertFalse(pad.pressed(EditorHudPad.Button.SOUTH), "Held is not a new press");
        assertEquals(0, pad.navX(), "Navigation repeats only after the menu repeat delay");
        buttons[PhysicalGamepad.BUTTON_A] = false;
        pad.sample(List.of(new PhysicalGamepad(0, "Xbox", buttons, axes)));
        assertTrue(pad.released(EditorHudPad.Button.SOUTH));
    }

    @Test
    void glyphMasksAreDrawn() {
        byte[] atlas = EditorHudGlyphs.atlasRgba();
        for (var mask : List.of(EditorHudGlyphs.DISC, EditorHudGlyphs.CROSS, EditorHudGlyphs.TRIANGLE,
                EditorHudGlyphs.PLUS, EditorHudGlyphs.MENU, EditorHudGlyphs.BUMPER, EditorHudGlyphs.TRIGGER)) {
            int covered = 0;
            for (int y = 0; y < mask.h(); y++)
                for (int x = 0; x < mask.w(); x++)
                    if (atlas[((mask.y() + y) * EditorHudGlyphs.ATLAS_WIDTH + mask.x() + x) * 4 + 3] != 0) covered++;
            assertTrue(covered > mask.w(), "mask " + mask + " has artwork");
        }
    }

    @Test
    void canvasMeasuresAndFitsTheStringItDraws() {
        // Case belongs to the caller: the canvas neither capitalises nor measures a different string.
        for (var size : EditorHudCanvas.Size.values()) {
            assertEquals(com.openggf.graphics.MenuPixelFont.textWidth("Level palette", size.scale),
                    EditorHudCanvas.textWidth("Level palette", size));
            assertTrue(EditorHudCanvas.textWidth("Level palette", size) < EditorHudCanvas.textWidth("LEVEL PALETTE", size),
                    "lowercase is narrower in the proportional faces");
            String fitted = EditorHudCanvas.fit("Clear region / cancel paste", 60, size);
            assertTrue(fitted.startsWith("Cl") && fitted.endsWith("..."), fitted);
            assertTrue(EditorHudCanvas.textWidth(fitted, size) <= 60, fitted);
        }
    }
}
